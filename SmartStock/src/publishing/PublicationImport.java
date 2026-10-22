package publishing;

import com.google.gson.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.w3c.dom.*;

/** Local-only converters. External processes receive file paths as arguments, never shell text. */
public final class PublicationImport {
  private PublicationImport() {}

  public static BufferedImage decodeImage(byte[] bytes) throws IOException {
    return decodeImage(bytes,0);
  }
  public static BufferedImage decodeImage(byte[] bytes,int maximumSide) throws IOException {
    return decodeImage(new ByteArrayInputStream(bytes),maximumSide);
  }
  public static BufferedImage decodeImage(InputStream source,int maximumSide) throws IOException {
    try (source; var input = ImageIO.createImageInputStream(source)) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new IOException("Unsupported image.");
      var reader = readers.next();
      try {
        reader.setInput(input);
        int w = reader.getWidth(0), h = reader.getHeight(0);
        if (w <= 0 || h <= 0 || (long) w * h > 100000000)
          throw new IOException("Image exceeds 100 megapixels.");
        var param=reader.getDefaultReadParam();
        if(maximumSide>0){int sample=Math.max(1,(int)Math.ceil(Math.max(w,h)/(double)maximumSide));param.setSourceSubsampling(sample,sample,0,0);}
        return reader.read(0,param);
      } finally {
        reader.dispose();
      }
    }
  }

  public record Result(Publication publication, List<String> warnings) {}

  /** Preserve compressed artwork exactly; a preview must never replace the source asset. */
  public static void addEncodedImage(Publication p,Publication.Page page,byte[] bytes,double x,double y,double w,double h)throws IOException {
    if(bytes.length==0||bytes.length>PublicationLargeFiles.MAX_IMAGE_BYTES)throw new IOException("Artwork exceeds 100 MiB.");
    try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
      var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Unsupported image.");
      var reader=readers.next();try{reader.setInput(input);String format=reader.getFormatName();if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("jpeg"))throw new IOException("Choose PNG or JPEG artwork.");int width=reader.getWidth(0),height=reader.getHeight(0);if(width<=0||height<=0||(long)width*height>100000000)throw new IOException("Artwork exceeds 100 megapixels.");}finally{reader.dispose();}
    }
    long total=bytes.length;for(String id:p.assetIds())total+=p.assetSize(id);
    if(total>PublicationLargeFiles.MAX_BYTES-PublicationLargeFiles.MAX_JSON_BYTES)throw new IOException("Artwork would exceed the 5 GiB template budget.");
    String id=UUID.randomUUID().toString();p.assetFiles.put(id,PublicationLargeFiles.store(new ByteArrayInputStream(bytes),PublicationLargeFiles.cache()));
    Publication.Element element=new Publication.Element();element.type="IMAGE";element.asset=id;element.text="Image";element.x=x;element.y=y;element.width=w;element.height=h;page.objects.add(element);
  }

  public static void image(Publication p, Path file, Publication.Page page) throws IOException {
    if (Files.size(file) > 20 * 1024 * 1024) throw new IOException("Image exceeds 20 MiB.");
    BufferedImage image = decodeImage(Files.readAllBytes(file));
    addImage(p, page, image, 0, 0, p.width, p.height);
  }

  public static void addImage(
      Publication p,
      Publication.Page page,
      BufferedImage image,
      double x,
      double y,
      double w,
      double h)
      throws IOException {
    if ((long) image.getWidth() * image.getHeight() > 100000000)
      throw new IOException("Image exceeds 100 megapixels.");
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    ImageIO.write(image, "png", bytes);
    String id = UUID.randomUUID().toString();
    p.assets.put(id, Base64.getEncoder().encodeToString(bytes.toByteArray()));
    Publication.Element e = new Publication.Element();
    e.type = "IMAGE";
    e.asset = id;
    e.text = "Image";
    e.x = x;
    e.y = y;
    e.width = w;
    e.height = h;
    page.objects.add(e);
  }

  public static Publication pdf(Path file) throws IOException {
    if (Files.size(file) > PublicationFiles.MAX_BYTES) throw new IOException("PDF exceeds 48 MiB.");
    Publication p = new Publication();
    p.pages.clear();
    p.title = file.getFileName().toString();
    try (PDDocument doc = PDDocument.load(file.toFile())) {
      if (doc.getNumberOfPages() > 100)
        throw new IOException("PDF import is limited to 100 pages.");
      PDFRenderer renderer = new PDFRenderer(doc);
      for (int i = 0; i < doc.getNumberOfPages(); i++) {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Import cancelled.");
        var box = doc.getPage(i).getCropBox();
        double w = box.getWidth() * 25.4 / 72, h = box.getHeight() * 25.4 / 72;
        if (doc.getPage(i).getRotation() % 180 != 0) {
          double temp = w;
          w = h;
          h = temp;
        }
        if (i == 0) {
          p.width = w;
          p.height = h;
        }
        if ((long) Math.ceil(w / 25.4 * 200) * (long) Math.ceil(h / 25.4 * 200) > 50000000)
          throw new IOException("PDF page is too large to import.");
        Publication.Page page = new Publication.Page();
        page.name = "Page " + (i + 1);
        p.pages.add(page);
        addImage(p, page, renderer.renderImageWithDPI(i, 200), 0, 0, w, h);
      }
    }
    return p;
  }

  public static Result publisher(Path file, Path executable) throws Exception {
    if (executable == null || !Files.isRegularFile(executable))
      throw new IOException(
          "LibreOffice is required for .pub import. Install it and select its soffice executable in"
              + " the import dialog.");
    if (Files.size(file) > PublicationFiles.MAX_BYTES)
      throw new IOException("Publisher file exceeds 48 MiB.");
    Path temp = Files.createTempDirectory("smartstock-pub-");
    try {
      // A separate profile prevents an existing LibreOffice session from swallowing the conversion.
      convert(executable, file, temp, "odg");
      convert(executable, file, temp, "pdf");
      String base = file.getFileName().toString().replaceFirst("(?i)\\.pub$", "");
      Path odg = temp.resolve(base + ".odg"), pdf = temp.resolve(base + ".pdf");
      if (!Files.isRegularFile(odg) || !Files.isRegularFile(pdf))
        throw new IOException(
            "LibreOffice could not convert this Publisher file. Open it in LibreOffice to check its"
                + " format support.");
      return odg(odg, pdf);
    } finally {
      try (var paths = Files.walk(temp)) {
        for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
          Files.deleteIfExists(path);
      }
    }
  }

  private static void convert(Path exe, Path source, Path target, String format) throws Exception {
    Path log = target.resolve("convert-" + format + ".log");
    ProcessBuilder builder =
        new ProcessBuilder(
                exe.toString(),
                "-env:UserInstallation=" + target.resolve("profile").toUri(),
                "--headless",
                "--convert-to",
                format,
                "--outdir",
                target.toString(),
                source.toAbsolutePath().toString())
            .redirectErrorStream(true)
            .redirectOutput(log.toFile());
    // File conversion does not use physical printers. Offline LAN queues can otherwise
    // stall every headless startup while Windows waits for the print spooler.
    builder.environment().put("SAL_DISABLE_PRINTERLIST", "1");
    builder.environment().put("SAL_DISABLE_DEFAULTPRINTER", "1");
    Process process = builder.start();
    try {
      if (!process.waitFor(60, TimeUnit.SECONDS))
        throw new IOException("LibreOffice conversion timed out.");
      if (process.exitValue() != 0) throw new IOException("LibreOffice conversion failed.");
    } finally {
      if (process.isAlive()) process.destroyForcibly();
    }
  }

  static Result odg(Path file, Path pdf) throws Exception {
    Map<String, byte[]> entries = new HashMap<>();
    int total = 0;
    try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
      ZipEntry entry;
      while ((entry = zip.getNextEntry()) != null) {
        if (entry.isDirectory()) continue;
        if (entry.getName().contains("..")
            || entry.getName().startsWith("/")
            || entries.size() > 2000) throw new IOException("Unsafe ODG archive.");
        byte[] bytes = zip.readNBytes(PublicationFiles.MAX_BYTES - total + 1);
        total += bytes.length;
        if (total > PublicationFiles.MAX_BYTES) throw new IOException("ODG exceeds 48 MiB.");
        entries.put(entry.getName(), bytes);
      }
    }
    var factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
    factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    if (!entries.containsKey("content.xml")) throw new IOException("ODG document is missing.");
    Document xml =
        factory.newDocumentBuilder().parse(new ByteArrayInputStream(entries.get("content.xml")));
    Map<String, org.w3c.dom.Element> styles = new HashMap<>();
    collectStyles(xml, styles);
    if (entries.containsKey("styles.xml"))
      collectStyles(
          factory.newDocumentBuilder().parse(new ByteArrayInputStream(entries.get("styles.xml"))),
          styles);
    Publication p = new Publication();
    p.pages.clear();
    List<String> warnings = new ArrayList<>();
    warnings.add(
        "Publisher conversion is best effort. Review fonts, text flow, and object positions before"
            + " printing.");
    try (PDDocument rendered = PDDocument.load(pdf.toFile())) {
      PDFRenderer renderer = new PDFRenderer(rendered);
      NodeList pages =
          xml.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:drawing:1.0", "page");
      if (pages.getLength() == 0
          || pages.getLength() > 100
          || rendered.getNumberOfPages() != pages.getLength())
        throw new IOException("Converted page counts are unsupported.");
      p.width = rendered.getPage(0).getCropBox().getWidth() * 25.4 / 72;
      p.height = rendered.getPage(0).getCropBox().getHeight() * 25.4 / 72;
      for (int i = 0; i < pages.getLength(); i++) {
        Publication.Page page = new Publication.Page();
        page.name = "Page " + (i + 1);
        p.pages.add(page);
        NodeList objects = pages.item(i).getChildNodes();
        boolean fallback = false;
        for (int j = 0; j < objects.getLength(); j++)
          if (objects.item(j) instanceof org.w3c.dom.Element node) {
            String type = node.getLocalName();
            Publication.Element e = new Publication.Element();
            applyOdgStyle(e, node.getAttribute("draw:style-name"), styles, new HashSet<>());
            e.x = length(node, "x", 0);
            e.y = length(node, "y", 0);
            e.width = length(node, "width", 0);
            e.height = length(node, "height", 0);
            if (e.width <= 0 || e.height <= 0 || !node.getAttribute("draw:transform").isBlank()) {
              fallback = true;
              continue;
            }
            switch (type) {
              case "rect" -> e.type = "RECT";
              case "ellipse" -> e.type = "ELLIPSE";
              case "frame" -> {
                NodeList texts =
                    node.getElementsByTagNameNS(
                        "urn:oasis:names:tc:opendocument:xmlns:text:1.0", "p");
                NodeList images =
                    node.getElementsByTagNameNS(
                        "urn:oasis:names:tc:opendocument:xmlns:drawing:1.0", "image");
                if (images.getLength() > 0) {
                  String href = ((org.w3c.dom.Element) images.item(0)).getAttribute("xlink:href");
                  byte[] bytes = entries.get(href);
                  BufferedImage image = null;
                  if (bytes != null) {
                    try {
                      image = decodeImage(bytes);
                    } catch (IOException unsupported) {
                      // Publisher artwork can be WMF/EMF, which ImageIO cannot decode.
                      // The rendered PDF still preserves it on the complete reference page.
                      fallback = true;
                      continue;
                    }
                  }
                  if (image == null) {
                    fallback = true;
                    continue;
                  }
                  addImage(p, page, image, e.x, e.y, e.width, e.height);
                  continue;
                } else if (texts.getLength() > 0) {
                  applyOdgStyle(
                      e,
                      ((org.w3c.dom.Element) texts.item(0)).getAttribute("text:style-name"),
                      styles,
                      new HashSet<>());
                  StringJoiner text = new StringJoiner("\n");
                  for (int k = 0; k < texts.getLength(); k++)
                    text.add(texts.item(k).getTextContent());
                  e.text = text.toString();
                } else {
                  fallback = true;
                  continue;
                }
              }
              default -> {
                fallback = true;
                continue;
              }
            }
            page.objects.add(e);
          }
        if (fallback) {
          // Keep a complete visual reference on its own page: no duplicated artwork beneath
          // editable text.
          Publication.Page reference = new Publication.Page();
          reference.name = page.name + " · visual reference";
          reference.reference = true;
          float dpi = p.width <= 150 && p.height <= 150 ? 300 : 150;
          addImage(p, reference, renderer.renderImageWithDPI(i, dpi), 0, 0, p.width, p.height);
          p.pages.add(reference);
          warnings.add(
              page.name
                  + ": unsupported objects retained on a separate visual-reference page. Editable"
                  + " reconstruction may be incomplete.");
        }
      }
    }
    p.validate();
    return new Result(p, List.copyOf(warnings));
  }

  private static double length(org.w3c.dom.Element node, String name, double fallback) {
    String value = node.getAttribute("svg:" + name);
    if (value.isBlank()) return fallback;
    try {
      double n = Double.parseDouble(value.replaceAll("[a-zA-Z]", ""));
      if (value.endsWith("cm")) return n * 10;
      if (value.endsWith("in")) return n * 25.4;
      if (value.endsWith("pt")) return n * 25.4 / 72;
      return n;
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static void collectStyles(Document doc, Map<String, org.w3c.dom.Element> styles) {
    NodeList list =
        doc.getElementsByTagNameNS("urn:oasis:names:tc:opendocument:xmlns:style:1.0", "style");
    for (int i = 0; i < list.getLength(); i++) {
      var style = (org.w3c.dom.Element) list.item(i);
      styles.put(style.getAttribute("style:name"), style);
    }
  }

  private static void applyOdgStyle(
      Publication.Element e,
      String name,
      Map<String, org.w3c.dom.Element> styles,
      Set<String> visited) {
    if (name.isBlank() || !visited.add(name) || visited.size() > 30) return;
    var style = styles.get(name);
    if (style == null) return;
    applyOdgStyle(e, style.getAttribute("style:parent-style-name"), styles, visited);
    NodeList children = style.getChildNodes();
    for (int i = 0; i < children.getLength(); i++)
      if (children.item(i) instanceof org.w3c.dom.Element prop) {
        String value = prop.getAttribute("fo:font-size");
        if (!value.isBlank())
          try {
            e.fontSize = Double.parseDouble(value.replace("pt", ""));
          } catch (NumberFormatException ignored) {
          }
        value = prop.getAttribute("style:font-name");
        if (!value.isBlank()) e.font = value;
        value = prop.getAttribute("fo:font-weight");
        if (!value.isBlank()) e.bold = value.equals("bold");
        value = prop.getAttribute("fo:font-style");
        if (!value.isBlank()) e.italic = value.equals("italic");
        value = prop.getAttribute("fo:color");
        if (!value.isBlank()) e.color = value;
        value = prop.getAttribute("draw:fill-color");
        if (!value.isBlank()) e.fill = value;
        if (prop.getAttribute("draw:fill").equals("none")) e.fill = "none";
        value = prop.getAttribute("svg:stroke-color");
        if (!value.isBlank()) e.stroke = value;
        if (prop.getAttribute("draw:stroke").equals("none")) e.stroke = "none";
      }
  }

  /** Copies legacy editor data into a new project; never mutates an order document or proof. */
  public static Publication legacy(JsonObject document) {
    Publication p = new Publication();
    p.width =
        Publication.toMm(document.get("width").getAsDouble(), document.get("unit").getAsString());
    p.height =
        Publication.toMm(document.get("height").getAsDouble(), document.get("unit").getAsString());
    JsonObject content = document.getAsJsonObject("document");
    if (content.has("background")) {
      Publication.Element background = new Publication.Element();
      background.type = "RECT";
      background.x = background.y = 0;
      background.width = p.width;
      background.height = p.height;
      background.fill = content.get("background").getAsString();
      background.stroke = background.fill;
      background.text = "Background";
      background.locked = true;
      p.pages.get(0).objects.add(background);
    }
    double factor = p.width / 1000;
    for (JsonElement object : document.getAsJsonObject("document").getAsJsonArray("objects")) {
      JsonObject old = object.getAsJsonObject();
      Publication.Element e = new Publication.Element();
      String type = old.get("type").getAsString();
      e.type =
          switch (type) {
            case "text" -> "TEXT";
            case "rect" -> "RECT";
            case "ellipse" -> "ELLIPSE";
            case "image" -> "IMAGE";
            default -> "RECT";
          };
      e.x = old.get("x").getAsDouble() * factor;
      e.y = old.get("y").getAsDouble() * factor;
      e.width = old.get("w").getAsDouble() * factor;
      e.height = old.get("h").getAsDouble() * factor;
      if (old.has("text")) e.text = old.get("text").getAsString();
      if (old.has("size")) e.fontSize = old.get("size").getAsDouble() * factor * 72 / 25.4;
      if (old.has("font")) e.font = old.get("font").getAsString();
      if (old.has("bold")) e.bold = old.get("bold").getAsBoolean();
      if (old.has("fill")) {
        e.fill = old.get("fill").getAsString();
        e.color = e.fill;
      }
      if (old.has("fileId")) e.asset = old.get("fileId").getAsString();
      p.pages.get(0).objects.add(e);
    }
    return p;
  }
}
