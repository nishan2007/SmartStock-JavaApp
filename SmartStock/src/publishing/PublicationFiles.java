package publishing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

public final class PublicationFiles {
  public static final int MAX_BYTES = 48 * 1024 * 1024;

  private PublicationFiles() {}

  public static byte[] archive(Publication original) throws IOException {
    original.validate();
    Publication p = original.copy();
    p.assets.clear();
    p.assetFiles.clear();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] json = Publication.JSON.toJson(p).getBytes(StandardCharsets.UTF_8);
    long expanded = json.length;
    if (expanded > MAX_BYTES) throw new IOException("Project document exceeds 48 MiB.");
    try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
      entry(zip, "publication.json", json);
      for (String id : original.assetIds()) {
        if(original.assetSize(id)>MAX_BYTES-expanded)throw new IOException("Project exceeds 48 MiB; save large banner templates through the Banners workspace.");
        byte[] content = original.assetBytes(id);
        expanded += content.length;
        if (expanded > MAX_BYTES) throw new IOException("Expanded project exceeds 48 MiB.");
        entry(zip, "assets/" + id, content);
      }
    }
    if (bytes.size() > MAX_BYTES) throw new IOException("Project exceeds 48 MiB.");
    return bytes.toByteArray();
  }

  private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    zip.write(bytes);
    zip.closeEntry();
  }

  public static Publication read(byte[] bytes) throws IOException {
    if (bytes.length > MAX_BYTES) throw new IOException("Project exceeds 48 MiB.");
    Map<String, byte[]> entries = new LinkedHashMap<>();
    int total = 0;
    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry e;
      while ((e = zip.getNextEntry()) != null) {
        String name = e.getName();
        if ((!name.equals("publication.json") && !name.matches("assets/[A-Za-z0-9_-]{1,100}"))
            || entries.containsKey(name)
            || entries.size() > 10000)
          throw new IOException("Unsafe, duplicate, or unsupported archive entry.");
        byte[] content = zip.readNBytes(MAX_BYTES - total + 1);
        total += content.length;
        if (total > MAX_BYTES) throw new IOException("Expanded project exceeds 48 MiB.");
        entries.put(name, content);
      }
    }
    try {
      if (!entries.containsKey("publication.json"))
        throw new IOException("Project document is missing.");
      Publication p =
          Publication.JSON.fromJson(
              new String(entries.get("publication.json"), StandardCharsets.UTF_8),
              Publication.class);
      if (p == null) throw new IOException("Empty project.");
      p.assets.clear();
      for (var e : entries.entrySet())
        if (e.getKey().startsWith("assets/"))
          p.assets.put(e.getKey().substring(7), Base64.getEncoder().encodeToString(e.getValue()));
      p.validate();
      return p;
    } catch (RuntimeException ex) {
      throw new IOException("Invalid project: " + ex.getMessage(), ex);
    }
  }

  public static void writeAtomic(Path target, byte[] bytes) throws IOException {
    Path parent = target.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path temp = Files.createTempFile(parent, "publication-", ".partial");
    try {
      Files.write(temp, bytes);
      try {
        Files.move(
            temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public static void pdf(Publication p, Path target, int first, int last) throws IOException {
    PublicationPreflight.requireMergeData(p);
    PublicationRenderer renderer = new PublicationRenderer(p);
    if (first < 0 || last < first || last >= renderer.sheets() || last - first > 999)
      throw new IOException("Choose a valid export range of at most 1000 sheets.");
    Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), "publication-", ".pdf");
    try {
      try (PDDocument doc = new PDDocument()) {
        for (int i = first; i <= last; i++) {
          if (Thread.currentThread().isInterrupted()) throw new IOException("Export cancelled.");
          PDPage page =
              new PDPage(
                  new PDRectangle(
                      (float) (p.print.width * 72 / 25.4), (float) (p.print.height * 72 / 25.4)));
          doc.addPage(page);
          try (PDPageContentStream out = new PDPageContentStream(doc, page)) {
            out.drawImage(
                LosslessFactory.createFromImage(doc, renderer.raster(i, 300)),
                0,
                0,
                page.getMediaBox().getWidth(),
                page.getMediaBox().getHeight());
          }
        }
        doc.save(temp.toFile());
      }
      Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /** RFC4180 quoting including embedded line breaks; first row names the merge fields. */
  public static List<Map<String, String>> csv(String content) {
    if (content.length() > 5000000) throw new IllegalArgumentException("CSV exceeds 5 MB.");
    List<List<String>> rows = new ArrayList<>();
    List<String> row = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < content.length(); i++) {
      char ch = content.charAt(i);
      if (ch == '"') {
        if (quoted && i + 1 < content.length() && content.charAt(i + 1) == '"') {
          field.append('"');
          i++;
        } else quoted = !quoted;
      } else if (!quoted && (ch == ',' || ch == '\n' || ch == '\r')) {
        row.add(field.toString());
        field.setLength(0);
        if (ch != ',') {
          if (ch == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
          rows.add(row);
          row = new ArrayList<>();
        }
      } else field.append(ch);
      if (rows.size() > 10001) throw new IllegalArgumentException("CSV exceeds 10000 rows.");
    }
    if (quoted) throw new IllegalArgumentException("Unclosed CSV quote.");
    if (!row.isEmpty() || !field.isEmpty()) {
      row.add(field.toString());
      rows.add(row);
    }
    if (rows.size() < 2) throw new IllegalArgumentException("CSV needs headers and data.");
    List<String> headers = rows.remove(0);
    Set<String> unique = new HashSet<>();
    for (int i = 0; i < headers.size(); i++) {
      String h = headers.get(i).replace("\uFEFF", "").trim();
      if (!h.matches("[A-Za-z_][A-Za-z0-9_ ]{0,99}") || !unique.add(h))
        throw new IllegalArgumentException("CSV headers must be unique field names.");
      headers.set(i, h);
    }
    List<Map<String, String>> result = new ArrayList<>();
    for (List<String> values : rows) {
      if (values.size() != headers.size())
        throw new IllegalArgumentException("CSV row has a different column count.");
      Map<String, String> valuesMap = new LinkedHashMap<>();
      for (int i = 0; i < headers.size(); i++) valuesMap.put(headers.get(i), values.get(i));
      result.add(valuesMap);
    }
    return result;
  }
}
