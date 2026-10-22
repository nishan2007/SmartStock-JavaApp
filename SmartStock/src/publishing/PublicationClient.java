package publishing;

import com.google.gson.*;
import java.io.*;
import java.util.*;
import services.LanApiClient;

public final class PublicationClient {
  private PublicationClient() {}

  public record Saved(UUID id, int revision) {}

  public record Loaded(Publication publication, int revision) {}

  public static PublicationImport.Result copyOrder(JsonObject legacy, long order) throws Exception {
    Publication p = PublicationImport.legacy(legacy);
    p.orderId = order;
    p.lineId = legacy.has("lineId") ? legacy.get("lineId").getAsLong() : null;
    p.title = "Order " + order + " publication";
    List<String> warnings = new ArrayList<>();
    Set<String> assets = new LinkedHashSet<>();
    for (var page : p.pages)
      for (var e : page.objects) if (e.type.equals("IMAGE")) assets.add(e.asset);
    for (String id : assets)
      try {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int size = 1;
        while (bytes.size() < size) {
          if (Thread.currentThread().isInterrupted()) throw new IOException("Import cancelled.");
          JsonObject b = new JsonObject();
          b.addProperty("action", "CHUNK");
          b.addProperty("orderId", order);
          b.addProperty("fileId", id);
          b.addProperty("offset", bytes.size());
          b.addProperty("length", 524288);
          JsonObject part = LanApiClient.customOrderMediaRead(b);
          size = part.get("totalBytes").getAsInt();
          if (size > 20 * 1024 * 1024) throw new IOException("Order image exceeds 20 MiB.");
          byte[] chunk = Base64.getDecoder().decode(part.get("bytesBase64").getAsString());
          if (chunk.length == 0) throw new IOException("Empty image chunk.");
          bytes.write(chunk);
        }
        PublicationImport.decodeImage(bytes.toByteArray());
        p.assets.put(id, Base64.getEncoder().encodeToString(bytes.toByteArray()));
      } catch (Exception ex) {
        warnings.add("Order image " + id + " could not be copied: " + ex.getMessage());
      }
    warnings.add(
        "Copied into a separate publication. Existing order designs and approved proofs are"
            + " unchanged.");
    return new PublicationImport.Result(p, List.copyOf(warnings));
  }

  public static Saved save(Publication p, UUID id, int revision) throws Exception {
    byte[] archive = PublicationFiles.archive(p);
    JsonObject body = new JsonObject();
    body.addProperty("projectId", id.toString());
    body.addProperty("revision", revision);
    String upload =
        LanApiClient.publicationRequest("BEGIN", body)
            .getAsJsonObject("result")
            .get("uploadId")
            .getAsString();
    for (int offset = 0; offset < archive.length; offset += 524288) {
      if (Thread.currentThread().isInterrupted()) throw new IOException("Save cancelled.");
      body = new JsonObject();
      body.addProperty("uploadId", upload);
      body.addProperty("offset", offset);
      body.addProperty(
          "bytes",
          Base64.getEncoder()
              .encodeToString(
                  Arrays.copyOfRange(archive, offset, Math.min(archive.length, offset + 524288))));
      LanApiClient.publicationRequest("CHUNK", body);
    }
    body = new JsonObject();
    body.addProperty("uploadId", upload);
    JsonObject result = LanApiClient.publicationRequest("FINISH", body).getAsJsonObject("result");
    return new Saved(
        UUID.fromString(result.get("projectId").getAsString()), result.get("revision").getAsInt());
  }

  public static Loaded load(UUID id) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    int size = 1, revision = -1;
    while (bytes.size() < size) {
      JsonObject body = new JsonObject();
      body.addProperty("projectId", id.toString());
      body.addProperty("offset", bytes.size());
      JsonObject part = LanApiClient.publicationRequest("READ", body).getAsJsonObject("result");
      size = part.get("size").getAsInt();
      if (size > PublicationFiles.MAX_BYTES)
        throw new IOException("Shared project exceeds size limit.");
      int current = part.get("revision").getAsInt();
      if (revision != -1 && revision != current)
        throw new IOException("Project changed during download; reload it.");
      revision = current;
      byte[] chunk = Base64.getDecoder().decode(part.get("bytes").getAsString());
      if (chunk.length == 0) throw new IOException("Empty download chunk.");
      bytes.write(chunk);
    }
    return new Loaded(PublicationFiles.read(bytes.toByteArray()), revision);
  }
}
