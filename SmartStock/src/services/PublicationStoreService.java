package services;

import com.google.gson.JsonObject;
import java.sql.*;
import java.util.*;
import publishing.*;

/**
 * Invoked only after device/session and permission checks in LanApiServer. Assets stay
 * server-owned.
 */
public final class PublicationStoreService {
  private PublicationStoreService() {}

  public static final class Conflict extends SQLException {
    public Conflict() {
      super(
          "This publication changed on another computer. Reload it or save your work as a new"
              + " project.");
    }
  }

  public static Object execute(
      Connection c, int location, int user, String action, JsonObject body, boolean templates)
      throws Exception {
    switch (action) {
      case "READ" -> {
        uuid(body, "projectId");
        integer(body, "offset");
      }
      case "BEGIN" -> {
        uuid(body, "projectId");
        integer(body, "revision");
      }
      case "CHUNK" -> {
        uuid(body, "uploadId");
        integer(body, "offset");
        string(body, "bytes", 750000);
      }
      case "FINISH" -> uuid(body, "uploadId");
      case "LIST" -> {}
      default -> throw new IllegalArgumentException("Unknown publication action.");
    }
    return switch (action) {
      case "LIST" -> list(c, location);
      case "READ" ->
          read(
              c,
              location,
              UUID.fromString(body.get("projectId").getAsString()),
              body.get("offset").getAsInt());
      case "BEGIN" -> begin(c, location, user, body);
      case "CHUNK" -> chunk(c, location, user, body);
      case "FINISH" ->
          finish(c, location, user, UUID.fromString(body.get("uploadId").getAsString()), templates);
      default -> throw new IllegalArgumentException("Unknown publication action.");
    };
  }

  private static String string(JsonObject body, String name, int limit) {
    if (!body.has(name)
        || !body.get(name).isJsonPrimitive()
        || !body.get(name).getAsJsonPrimitive().isString())
      throw new IllegalArgumentException("Missing " + name + ".");
    String value = body.get(name).getAsString();
    if (value.length() > limit) throw new IllegalArgumentException("Invalid " + name + ".");
    return value;
  }

  private static UUID uuid(JsonObject body, String name) {
    try {
      return UUID.fromString(string(body, name, 40));
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Invalid " + name + ".");
    }
  }

  private static int integer(JsonObject body, String name) {
    try {
      if (!body.has(name) || !body.get(name).isJsonPrimitive())
        throw new IllegalArgumentException();
      return new java.math.BigDecimal(body.get(name).getAsString()).intValueExact();
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("Invalid " + name + ".");
    }
  }

  private static List<Map<String, Object>> list(Connection c, int location) throws SQLException {
    List<Map<String, Object>> rows = new ArrayList<>();
    try (var p =
        c.prepareStatement(
            "SELECT project_id,title,is_template,revision,updated_at FROM publication_projects"
                + " WHERE location_id=? ORDER BY updated_at DESC LIMIT 1000")) {
      p.setInt(1, location);
      try (var r = p.executeQuery()) {
        while (r.next())
          rows.add(
              Map.of(
                  "id",
                  r.getObject(1).toString(),
                  "title",
                  r.getString(2),
                  "template",
                  r.getBoolean(3),
                  "revision",
                  r.getInt(4),
                  "updated",
                  r.getTimestamp(5).toInstant().toString()));
      }
    }
    return rows;
  }

  private static Map<String, Object> read(Connection c, int location, UUID id, int offset)
      throws SQLException {
    if (offset < 0 || offset >= PublicationFiles.MAX_BYTES)
      throw new IllegalArgumentException("Invalid archive offset.");
    try (var p =
        c.prepareStatement(
            "SELECT substring(archive FROM ? FOR 524288),octet_length(archive),revision FROM"
                + " publication_projects WHERE project_id=? AND location_id=?")) {
      p.setInt(1, offset + 1);
      p.setObject(2, id);
      p.setInt(3, location);
      try (var r = p.executeQuery()) {
        if (!r.next()) throw new IllegalArgumentException("Publication not found.");
        if (offset >= r.getInt(2)) throw new IllegalArgumentException("Invalid archive offset.");
        return Map.of(
            "bytes",
            Base64.getEncoder().encodeToString(r.getBytes(1)),
            "size",
            r.getInt(2),
            "revision",
            r.getInt(3));
      }
    }
  }

  private static Map<String, Object> begin(Connection c, int location, int user, JsonObject b)
      throws SQLException {
    UUID id = UUID.randomUUID();
    int revision = b.get("revision").getAsInt();
    if (revision < 0) throw new IllegalArgumentException("Invalid revision.");
    try (var p = c.prepareStatement("DELETE FROM publication_uploads WHERE expires_at<now()")) {
      p.executeUpdate();
    }
    try (var p =
        c.prepareStatement(
            "SELECT count(*) FROM publication_uploads WHERE user_id=? AND location_id=?")) {
      p.setInt(1, user);
      p.setInt(2, location);
      try (var r = p.executeQuery()) {
        r.next();
        if (r.getInt(1) >= 5)
          throw new IllegalArgumentException(
              "Too many pending uploads. Try again after they expire.");
      }
    }
    try (var p =
        c.prepareStatement(
            "INSERT INTO"
                + " publication_uploads(upload_id,location_id,user_id,project_id,expected_revision)"
                + " VALUES(?,?,?,?,?)")) {
      p.setObject(1, id);
      p.setInt(2, location);
      p.setInt(3, user);
      p.setObject(4, UUID.fromString(b.get("projectId").getAsString()));
      p.setInt(5, revision);
      p.executeUpdate();
    }
    return Map.of("uploadId", id.toString());
  }

  private static Map<String, Object> chunk(Connection c, int location, int user, JsonObject b)
      throws SQLException {
    byte[] bytes = Base64.getDecoder().decode(b.get("bytes").getAsString());
    int offset = b.get("offset").getAsInt();
    if (bytes.length < 1
        || bytes.length > 524288
        || offset < 0
        || (long) offset + bytes.length > PublicationFiles.MAX_BYTES)
      throw new IllegalArgumentException("Invalid upload chunk.");
    try (var p =
        c.prepareStatement(
            "UPDATE publication_uploads SET archive=archive || ?::bytea WHERE upload_id=? AND"
                + " location_id=? AND user_id=? AND expires_at>now() AND"
                + " octet_length(archive)=?")) {
      p.setBytes(1, bytes);
      p.setObject(2, UUID.fromString(b.get("uploadId").getAsString()));
      p.setInt(3, location);
      p.setInt(4, user);
      p.setInt(5, offset);
      if (p.executeUpdate() != 1)
        throw new IllegalArgumentException(
            "Upload expired or chunk offset changed; restart the save.");
    }
    return Map.of("offset", offset + bytes.length);
  }

  private static Map<String, Object> finish(
      Connection c, int location, int user, UUID upload, boolean templates) throws Exception {
    try (var p =
        c.prepareStatement(
            "SELECT project_id,expected_revision,archive FROM publication_uploads WHERE upload_id=?"
                + " AND location_id=? AND user_id=? AND expires_at>now() FOR UPDATE")) {
      p.setObject(1, upload);
      p.setInt(2, location);
      p.setInt(3, user);
      try (var r = p.executeQuery()) {
        if (!r.next()) throw new IllegalArgumentException("Upload expired; restart the save.");
        UUID id = (UUID) r.getObject(1);
        int revision = r.getInt(2);
        byte[] archive = r.getBytes(3);
        Publication doc = PublicationFiles.read(archive);
        boolean existingTemplate = false;
        try (var existing =
            c.prepareStatement(
                "SELECT is_template FROM publication_projects WHERE project_id=? AND location_id=?"
                    + " FOR UPDATE")) {
          existing.setObject(1, id);
          existing.setInt(2, location);
          try (var found = existing.executeQuery()) {
            if (found.next()) existingTemplate = found.getBoolean(1);
          }
        }
        if ((doc.template || existingTemplate) && !templates)
          throw new SecurityException("Template management permission is required.");
        if (doc.orderId != null) {
          try (var check =
              c.prepareStatement(
                  "SELECT 1 FROM custom_orders o WHERE o.custom_order_id=? AND o.location_id=? AND"
                      + " (?::bigint IS NULL OR EXISTS(SELECT 1 FROM custom_order_lines l WHERE"
                      + " l.custom_order_id=o.custom_order_id AND l.custom_order_line_id=?))")) {
            check.setLong(1, doc.orderId);
            check.setInt(2, location);
            check.setObject(3, doc.lineId);
            check.setObject(4, doc.lineId);
            try (var found = check.executeQuery()) {
              if (!found.next())
                throw new IllegalArgumentException("Order link is unavailable in this store.");
            }
          }
        } else if (doc.lineId != null)
          throw new IllegalArgumentException("Order line needs an order link.");
        String sql =
            revision == 0
                ? "INSERT INTO"
                      + " publication_projects(project_id,title,is_template,order_id,line_id,archive,updated_by,location_id)"
                      + " VALUES(?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING"
                : "UPDATE publication_projects SET"
                      + " title=?,is_template=?,order_id=?,line_id=?,archive=?,updated_by=?,updated_at=now(),revision=revision+1"
                      + " WHERE location_id=? AND project_id=? AND revision=?";
        try (var save = c.prepareStatement(sql)) {
          int i = 1;
          if (revision == 0) save.setObject(i++, id);
          save.setString(i++, doc.title);
          save.setBoolean(i++, doc.template);
          save.setObject(i++, doc.orderId);
          save.setObject(i++, doc.lineId);
          save.setBytes(i++, archive);
          save.setInt(i++, user);
          save.setInt(i++, location);
          if (revision > 0) {
            save.setObject(i++, id);
            save.setInt(i, revision);
          }
          if (save.executeUpdate() != 1) throw new Conflict();
        }
        try (var delete = c.prepareStatement("DELETE FROM publication_uploads WHERE upload_id=?")) {
          delete.setObject(1, upload);
          delete.executeUpdate();
        }
        return Map.of("projectId", id.toString(), "revision", revision + 1);
      }
    }
  }
}
