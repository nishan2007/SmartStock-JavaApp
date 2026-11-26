package gy.deckers.studio.pdfeditor;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;
import publishing.TextAlignment;

/** Serialized edit commands make mutations transactional and keep PDF object graphs intact. */
public final class PdfDocument implements AutoCloseable {
  public static final class PermissionException extends IOException {
    public PermissionException() {
      super("This PDF has restricted permissions. Enter its owner password.");
    }
  }

  private PDDocument document = new PDDocument();
  private final List<UUID> ids = new ArrayList<>();

  public record SourceReference(Path file, int page) {}

  private final Map<UUID, SourceReference> sources = new HashMap<>();

  public record Overlay(
      UUID id,
      UUID page,
      String kind,
      String text,
      BufferedImage image,
      float x,
      float y,
      float width,
      float height,
      float fontSize,
      Color color,
      TextAlignment alignment) {
    public Overlay(UUID id, UUID page, String kind, String text, BufferedImage image,
                   float x, float y, float width, float height, float fontSize, Color color) {
      this(id, page, kind, text, image, x, y, width, height, fontSize, color, null);
    }
  }

  private final List<Overlay> objects = new ArrayList<>();

  private record State(
      byte[] pdf, List<UUID> ids, List<Overlay> objects, Map<UUID, SourceReference> sources) {}

  private record Command(String name, State before, State after) {}

  private final Deque<Command> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
  private long historyBytes;
  private boolean editing;
  private static final long PREVIEW_PIXELS = 24_000_000L;
  private static final long HISTORY_LIMIT = 96L * 1024 * 1024;
  private final Map<String, BufferedImage> previews =
      new LinkedHashMap<>(16, .75f, true) {
        protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> e) {
          return size() > 8;
        }
      };

  @FunctionalInterface
  public interface Edit {
    void run(PDDocument pdf) throws Exception;
  }

  public synchronized int size() {
    return document.getNumberOfPages();
  }

  public synchronized UUID pageId(int page) {
    return ids.get(page);
  }

  public synchronized SourceReference source(int page) {
    return sources.get(pageId(page));
  }

  public synchronized boolean canUndo() {
    return !undo.isEmpty();
  }

  public synchronized boolean canRedo() {
    return !redo.isEmpty();
  }

  private State state() throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    document.save(bytes);
    return new State(
        bytes.toByteArray(), List.copyOf(ids), List.copyOf(objects), Map.copyOf(sources));
  }

  private void restore(State state) throws IOException {
    PDDocument replacement =
        PDDocument.load(
            new ByteArrayInputStream(state.pdf), MemoryUsageSetting.setupTempFileOnly());
    document.close();
    document = replacement;
    ids.clear();
    ids.addAll(state.ids);
    objects.clear();
    objects.addAll(state.objects);
    sources.clear();
    sources.putAll(state.sources);
    previews.clear();
  }

  public synchronized void edit(String name, Edit edit) throws Exception {
    if (editing) {
      edit.run(document);
      return;
    }
    State before = state();
    editing = true;
    try {
      normalizeDestinations(
          document,
          document.getDocumentCatalog().getCOSObject(),
          Collections.newSetFromMap(new IdentityHashMap<>()));
      edit.run(document);
      cleanPageReferences(document);
      while (ids.size() < size()) ids.add(UUID.randomUUID());
      while (ids.size() > size()) ids.remove(ids.size() - 1);
      sources.keySet().retainAll(ids);
      objects.removeIf(object -> !ids.contains(object.page));
      State after = state();
      undo.addLast(new Command(name, before, after));
      redo.clear();
      historyBytes = 0;
      for (Command command : undo)
        historyBytes += command.before.pdf.length + command.after.pdf.length;
      while (historyBytes > HISTORY_LIMIT && undo.size() > 1) {
        Command removed = undo.removeFirst();
        historyBytes -= removed.before.pdf.length + removed.after.pdf.length;
      }
      previews.clear();
    } catch (Exception ex) {
      restore(before);
      throw ex;
    } finally {
      editing = false;
    }
  }

  /** Drop dangling navigation/widget references after delete or extraction. */
  private static void cleanPageReferences(PDDocument pdf) throws IOException {
    Set<org.apache.pdfbox.cos.COSDictionary> live =
        Collections.newSetFromMap(new IdentityHashMap<>());
    Set<org.apache.pdfbox.cos.COSDictionary> widgets =
        Collections.newSetFromMap(new IdentityHashMap<>());
    for (PDPage page : pdf.getPages()) {
      live.add(page.getCOSObject());
      for (var annotation : page.getAnnotations()) widgets.add(annotation.getCOSObject());
    }
    pruneDestinations(
        pdf.getDocumentCatalog().getCOSObject(),
        live,
        Collections.newSetFromMap(new IdentityHashMap<>()));
    var form = pdf.getDocumentCatalog().getAcroForm();
    if (form != null) {
      List<org.apache.pdfbox.pdmodel.interactive.form.PDField> roots =
          new ArrayList<>(form.getFields());
      roots.removeIf(field -> !keepField(field, widgets));
      form.setFields(roots);
    }
  }

  private static boolean keepField(
      org.apache.pdfbox.pdmodel.interactive.form.PDField field,
      Set<org.apache.pdfbox.cos.COSDictionary> liveWidgets) {
    if (field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField parent) {
      var children = new ArrayList<>(parent.getChildren());
      children.removeIf(child -> !keepField(child, liveWidgets));
      parent.setChildren(children);
      return !children.isEmpty();
    }
    var all = field.getWidgets();
    var kept = new ArrayList<>(all);
    kept.removeIf(widget -> !liveWidgets.contains(widget.getCOSObject()));
    if (kept.isEmpty()) return false;
    // A combined field/widget dictionary must not become its own child.
    if (kept.size() != all.size()
        && field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDTerminalField terminal)
      terminal.setWidgets(kept);
    return true;
  }

  private static void pruneDestinations(
      org.apache.pdfbox.cos.COSBase value,
      Set<org.apache.pdfbox.cos.COSDictionary> live,
      Set<org.apache.pdfbox.cos.COSBase> visited) {
    if (value instanceof org.apache.pdfbox.cos.COSObject ref) value = ref.getObject();
    if (value == null || !visited.add(value)) return;
    if (value instanceof org.apache.pdfbox.cos.COSDictionary dictionary) {
      for (var key : new ArrayList<>(dictionary.keySet())) {
        var child = dictionary.getDictionaryObject(key);
        if (danglingDestination(child, live)) dictionary.removeItem(key);
        else pruneDestinations(child, live, visited);
      }
    } else if (value instanceof org.apache.pdfbox.cos.COSArray array) {
      for (int i = 0; i < array.size(); i++) {
        if (i + 1 < array.size()
            && array.getObject(i) instanceof org.apache.pdfbox.cos.COSString
            && danglingDestination(array.getObject(i + 1), live)) {
          array.remove(i + 1);
          array.remove(i);
          i--;
        } else pruneDestinations(array.getObject(i), live, visited);
      }
    }
  }

  private static boolean danglingDestination(
      org.apache.pdfbox.cos.COSBase value, Set<org.apache.pdfbox.cos.COSDictionary> live) {
    if (value instanceof org.apache.pdfbox.cos.COSDictionary dictionary)
      value = dictionary.getDictionaryObject(org.apache.pdfbox.cos.COSName.D);
    return value instanceof org.apache.pdfbox.cos.COSArray array
        && array.size() > 1
        && array.getObject(1) instanceof org.apache.pdfbox.cos.COSName
        && array.getObject(0) instanceof org.apache.pdfbox.cos.COSDictionary page
        && "Page".equals(page.getNameAsString(org.apache.pdfbox.cos.COSName.TYPE))
        && !live.contains(page);
  }

  private static void normalizeDestinations(
      PDDocument pdf,
      org.apache.pdfbox.cos.COSBase value,
      Set<org.apache.pdfbox.cos.COSBase> visited) {
    if (value instanceof org.apache.pdfbox.cos.COSObject ref) value = ref.getObject();
    if (value == null || !visited.add(value)) return;
    if (value instanceof org.apache.pdfbox.cos.COSArray array) {
      if (array.size() >= 2
          && array.getObject(0) instanceof org.apache.pdfbox.cos.COSInteger number
          && array.getObject(1) instanceof org.apache.pdfbox.cos.COSName name
          && Set.of("XYZ", "Fit", "FitH", "FitV", "FitR", "FitB", "FitBH", "FitBV")
              .contains(name.getName())) {
        int index = number.intValue();
        if (index >= 0 && index < pdf.getNumberOfPages())
          array.set(0, pdf.getPage(index).getCOSObject());
      }
      for (int i = 0; i < array.size(); i++)
        normalizeDestinations(pdf, array.getObject(i), visited);
    } else if (value instanceof org.apache.pdfbox.cos.COSDictionary dictionary) {
      for (var key : dictionary.keySet())
        normalizeDestinations(pdf, dictionary.getDictionaryObject(key), visited);
    }
  }

  public synchronized void undo() throws IOException {
    if (!undo.isEmpty()) {
      Command c = undo.removeLast();
      restore(c.before);
      redo.addLast(c);
    }
  }

  public synchronized void redo() throws IOException {
    if (!redo.isEmpty()) {
      Command c = redo.removeLast();
      restore(c.after);
      undo.addLast(c);
    }
  }

  public static PDDocument load(Path file, String password) throws IOException {
    PDDocument pdf =
        PDDocument.load(file.toFile(), password, MemoryUsageSetting.setupTempFileOnly());
    var permissions = pdf.getCurrentAccessPermission();
    if (!permissions.canModify()
        || !permissions.canAssembleDocument()
        || !permissions.canExtractContent()
        || !permissions.canPrint()
        || !permissions.canFillInForm()
        || !permissions.canModifyAnnotations()) {
      pdf.close();
      throw new PermissionException();
    }
    return pdf;
  }

  public synchronized void append(Path file, String password, int insertion, boolean allowSigned)
      throws Exception {
    try (PDDocument source = load(file, password)) {
      normalizeDestinations(
          source,
          source.getDocumentCatalog().getCOSObject(),
          Collections.newSetFromMap(new IdentityHashMap<>()));
      if (!source.getSignatureDictionaries().isEmpty() && !allowSigned)
        throw new IOException("SIGNED_PDF");
      if (!source.getSignatureDictionaries().isEmpty()
          && source.getDocumentCatalog().getAcroForm() != null) {
        source.getDocumentCatalog().getAcroForm().flatten();
        source.getDocumentCatalog().getCOSObject().removeItem(org.apache.pdfbox.cos.COSName.PERMS);
      }
      edit(
          "Insert PDF",
          pdf -> {
            int oldSize = size();
            var form = source.getDocumentCatalog().getAcroForm();
            if (form != null) {
              String prefix = "import_" + UUID.randomUUID().toString().replace("-", "") + "_";
              for (var field : form.getFields())
                field.setPartialName(prefix + field.getPartialName());
            }
            new PDFMergerUtility().appendDocument(pdf, source);
            List<PDPage> added = new ArrayList<>();
            for (int i = oldSize; i < size(); i++) added.add(pdf.getPage(i));
            for (PDPage page : added) pdf.getPages().remove(page);
            int at = Math.max(0, Math.min(insertion, oldSize));
            int sourceIndex = 0;
            for (PDPage page : added) {
              if (at == pdf.getNumberOfPages()) pdf.addPage(page);
              else pdf.getPages().insertBefore(page, pdf.getPage(at));
              UUID id = UUID.randomUUID();
              ids.add(at++, id);
              sources.put(id, new SourceReference(file.toAbsolutePath(), sourceIndex++));
            }
          });
    }
  }

  public synchronized void move(int[] selected, int insertion) throws Exception {
    edit(
        "Move pages",
        pdf -> {
          List<PDPage> pages = new ArrayList<>();
          List<UUID> movedIds = new ArrayList<>();
          int at = insertion;
          for (int index : selected) {
            pages.add(pdf.getPage(index));
            movedIds.add(ids.get(index));
            if (index < insertion) at--;
          }
          for (PDPage page : pages) pdf.getPages().remove(page);
          for (int i = selected.length - 1; i >= 0; i--) ids.remove(selected[i]);
          at = Math.max(0, Math.min(at, size()));
          for (int i = 0; i < pages.size(); i++) {
            if (at == size()) pdf.addPage(pages.get(i));
            else pdf.getPages().insertBefore(pages.get(i), pdf.getPage(at));
            ids.add(at++, movedIds.get(i));
          }
        });
  }

  public synchronized void delete(int[] selected) throws Exception {
    if (selected.length == size()) throw new IOException("Keep at least one page in the document.");
    edit(
        "Delete pages",
        pdf -> {
          for (int i = selected.length - 1; i >= 0; i--) {
            pdf.removePage(selected[i]);
            ids.remove(selected[i]);
          }
        });
  }

  public synchronized void rotate(int[] selected, int degrees) throws Exception {
    edit(
        "Rotate pages",
        pdf -> {
          for (int i : selected) {
            PDPage p = pdf.getPage(i);
            p.setRotation(Math.floorMod(p.getRotation() + degrees, 360));
          }
        });
  }

  public synchronized void duplicate(int[] selected) throws Exception {
    edit(
        "Duplicate pages",
        pdf -> {
          for (int k = selected.length - 1; k >= 0; k--) {
            int i = selected[k];
            PDPage original = pdf.getPage(i);
            // A serialized copy isolates content, resources and annotation dictionaries.
            try (PDDocument copy = PDDocument.load(state().pdf)) {
              if (copy.getDocumentCatalog().getAcroForm() != null)
                copy.getDocumentCatalog().getAcroForm().flatten();
              PDPage page = pdf.importPage(copy.getPage(i));
              var cloner = new org.apache.pdfbox.multipdf.PDFCloneUtility(pdf);
              if (copy.getPage(i).getResources() != null)
                page.setResources(
                    new PDResources(
                        (org.apache.pdfbox.cos.COSDictionary)
                            cloner.cloneForNewDocument(copy.getPage(i).getResources())));
              var annotations =
                  new ArrayList<org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation>();
              for (var annotation : copy.getPage(i).getAnnotations()) {
                var cloned =
                    org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation.createAnnotation(
                        cloner.cloneForNewDocument(annotation.getCOSObject()));
                cloned.setPage(page);
                annotations.add(cloned);
              }
              page.setAnnotations(annotations);
              pdf.getPages().remove(page);
              pdf.getPages().insertAfter(page, original);
              UUID newId = UUID.randomUUID();
              ids.add(i + 1, newId);
              if (sources.containsKey(ids.get(i))) sources.put(newId, sources.get(ids.get(i)));
              for (Overlay object : new ArrayList<>(objects))
                if (object.page.equals(ids.get(i)))
                  objects.add(
                      new Overlay(
                          UUID.randomUUID(),
                          newId,
                          object.kind,
                          object.text,
                          object.image,
                          object.x,
                          object.y,
                          object.width,
                          object.height,
                          object.fontSize,
                          object.color,
                          object.alignment));
            }
          }
        });
  }

  public synchronized void blank(int at) throws Exception {
    edit(
        "Blank page",
        pdf -> {
          PDPage page = new PDPage(PDRectangle.LETTER);
          if (at == size()) pdf.addPage(page);
          else pdf.getPages().insertBefore(page, pdf.getPage(at));
          ids.add(at, UUID.randomUUID());
        });
  }

  public synchronized void crop(int page, float left, float bottom, float width, float height)
      throws Exception {
    edit(
        "Crop page",
        pdf -> {
          PDRectangle box = pdf.getPage(page).getMediaBox();
          if (width <= 0
              || height <= 0
              || left < box.getLowerLeftX()
              || bottom < box.getLowerLeftY()
              || left + width > box.getUpperRightX()
              || bottom + height > box.getUpperRightY())
            throw new IOException("Crop must fit inside the page (PDF points).");
          pdf.getPage(page).setCropBox(new PDRectangle(left, bottom, width, height));
        });
  }

  public synchronized PDRectangle bounds(int page) {
    return document.getPage(page).getCropBox();
  }

  public synchronized int rotation(int page) {
    return document.getPage(page).getRotation();
  }

  public synchronized BufferedImage render(int page, float scale) throws IOException {
    String key = pageId(page) + ":" + scale;
    BufferedImage image = previews.get(key);
    if (image == null) {
      PDRectangle box = bounds(page);
      double pixels = (double) box.getWidth() * box.getHeight() * scale * scale;
      if (!Float.isFinite(scale) || scale <= 0 || pixels > 16_000_000 || pixels <= 0)
        throw new IOException(
            "Page preview is too large. Reduce zoom or crop the page before OCR.");
      try (PDDocument composed = composed()) {
        image = new PDFRenderer(composed).renderImage(page, scale);
      }
      long total = (long) image.getWidth() * image.getHeight();
      for (BufferedImage cached : previews.values())
        total += (long) cached.getWidth() * cached.getHeight();
      var iterator = previews.entrySet().iterator();
      while (total > PREVIEW_PIXELS && iterator.hasNext()) {
        var cached = iterator.next().getValue();
        total -= (long) cached.getWidth() * cached.getHeight();
        iterator.remove();
      }
      previews.put(key, image);
    }
    return image;
  }

  public synchronized String text(int page) throws IOException {
    PDFTextStripper stripper = new PDFTextStripper();
    stripper.setStartPage(page + 1);
    stripper.setEndPage(page + 1);
    try (PDDocument copy = composed()) {
      return stripper.getText(copy);
    }
  }

  public synchronized void text(int page, String text, float x, float y, float size, Color color)
      throws Exception {
    text(page, text, x, y, Math.max(20, text.length() * size / 2), size, color, null);
  }

  public synchronized void text(int page, String text, float x, float y, float width,
                                float size, Color color, TextAlignment alignment) throws Exception {
    // Validate font encoding before accepting the edit.
    PDType1Font.HELVETICA.encode(text.replaceAll("\\R", ""));
    addObject(
        new Overlay(
            UUID.randomUUID(),
            pageId(page),
            "Text",
            text,
            null,
            x,
            y,
            width,
            size * 1.3f,
            size,
            color,
            alignment));
  }

  public static void writeText(
      PDDocument pdf,
      PDPage page,
      String text,
      float x,
      float y,
      float size,
      Color color,
      boolean invisible)
      throws IOException {
    writeText(pdf, page, text, x, y, size, color, invisible, 0, null);
  }

  private static void writeText(PDDocument pdf, PDPage page, String text, float x, float y,
                                float size, Color color, boolean invisible, float width,
                                TextAlignment alignment) throws IOException {
    try (PDPageContentStream out =
        new PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
      out.beginText();
      out.setFont(PDType1Font.HELVETICA, size);
      out.setNonStrokingColor(color);
      if (invisible)
        out.setRenderingMode(org.apache.pdfbox.pdmodel.graphics.state.RenderingMode.NEITHER);
      for (String line : text.split("\\R", -1)) {
        float offset = alignment == null ? 0 : (float)alignment.offset(width, PDType1Font.HELVETICA.getStringWidth(line) * size / 1000);
        out.setTextMatrix(Matrix.getTranslateInstance(x + offset, y));
        out.showText(line);
        y -= size * 1.2f;
      }
      out.endText();
    }
  }

  public synchronized void rectangle(
      int page, float x, float y, float width, float height, Color color, boolean fill)
      throws Exception {
    addObject(
        new Overlay(
            UUID.randomUUID(),
            pageId(page),
            fill ? "Highlight" : "Rectangle",
            "",
            null,
            x,
            y,
            width,
            height,
            12,
            color));
  }

  public synchronized void image(
      int page, BufferedImage image, float x, float y, float width, float height) throws Exception {
    image(page, image, x, y, width, height, "Image");
  }

  public synchronized void image(
      int page, BufferedImage image, float x, float y, float width, float height, String label)
      throws Exception {
    addObject(
        new Overlay(
            UUID.randomUUID(),
            pageId(page),
            label,
            "",
            image,
            x,
            y,
            width,
            height,
            12,
            Color.BLACK));
  }

  public synchronized List<Overlay> objects(int page) {
    UUID id = pageId(page);
    return objects.stream().filter(o -> o.page.equals(id)).toList();
  }

  public synchronized void addObject(Overlay object) throws Exception {
    validateObject(object);
    edit("Add " + object.kind, pdf -> objects.add(object));
  }

  public synchronized void updateObject(Overlay object) throws Exception {
    validateObject(object);
    edit(
        "Change object",
        pdf -> {
          objects.removeIf(o -> o.id.equals(object.id));
          objects.add(object);
        });
  }

  private void validateObject(Overlay object) throws IOException {
    if (!ids.contains(object.page)
        || !Float.isFinite(object.x)
        || !Float.isFinite(object.y)
        || !Float.isFinite(object.width)
        || !Float.isFinite(object.height)
        || !Float.isFinite(object.fontSize)
        || object.width <= 0
        || object.height <= 0
        || object.fontSize <= 0)
      throw new IOException("Object coordinates, dimensions and font size must be valid.");
    if (object.kind.equals("Text")) PDType1Font.HELVETICA.encode(object.text.replaceAll("\\R", ""));
  }

  public synchronized void removeObject(UUID id) throws Exception {
    edit("Delete object", pdf -> objects.removeIf(o -> o.id.equals(id)));
  }

  private PDDocument composed() throws IOException {
    PDDocument copy =
        PDDocument.load(
            new ByteArrayInputStream(state().pdf), MemoryUsageSetting.setupTempFileOnly());
    try {
      for (Overlay o : objects) {
        int index = ids.indexOf(o.page);
        if (index < 0) continue;
        PDPage page = copy.getPage(index);
        if (o.kind.equals("Text")) {
          writeText(copy, page, o.text, o.x, o.y, o.fontSize, o.color, false, o.width, o.alignment);
          continue;
        }
        try (var out =
            new PDPageContentStream(
                copy, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
          if (o.image != null)
            out.drawImage(
                LosslessFactory.createFromImage(copy, o.image), o.x, o.y, o.width, o.height);
          else {
            out.setStrokingColor(o.color);
            out.setNonStrokingColor(o.color);
            out.addRect(o.x, o.y, o.width, o.height);
            if (o.kind.equals("Highlight")) {
              var alpha = new org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState();
              alpha.setNonStrokingAlphaConstant(.3f);
              out.setGraphicsStateParameters(alpha);
              out.fill();
            } else out.stroke();
          }
        }
      }
      return copy;
    } catch (IOException | RuntimeException ex) {
      copy.close();
      throw ex;
    }
  }

  public synchronized Map<String, String> fields() {
    Map<String, String> values = new LinkedHashMap<>();
    var form = document.getDocumentCatalog().getAcroForm();
    if (form != null)
      for (var field : form.getFieldTree())
        if (!(field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField)
            && !(field instanceof org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField))
          values.put(field.getFullyQualifiedName(), field.getValueAsString());
    return values;
  }

  public synchronized void field(String name, String value) throws Exception {
    field(name, value, null);
  }

  public synchronized Optional<TextAlignment> fieldTextAlignment(String name) {
    var form = document.getDocumentCatalog().getAcroForm();
    if (form == null || !(form.getField(name) instanceof PDTextField field)) return Optional.empty();
    return Optional.of(switch (field.getQ()) {
      case 1 -> TextAlignment.CENTER;
      case 2 -> TextAlignment.RIGHT;
      default -> TextAlignment.LEFT;
    });
  }

  public synchronized void field(String name, String value, TextAlignment alignment) throws Exception {
    edit("Fill form", pdf -> {
      var field = pdf.getDocumentCatalog().getAcroForm().getField(name);
      if (alignment != null && field instanceof PDTextField text) {
        text.setQ(switch (alignment) {case LEFT -> 0;case CENTER -> 1;case RIGHT -> 2;});
      }
      field.setValue(value);
    });
  }

  public synchronized void save(Path target, int[] selection) throws Exception {
    if (size() == 0) throw new IOException("Open or insert a page first.");
    Path absolute = target.toAbsolutePath();
    Path temp = Files.createTempFile(absolute.getParent(), ".smartstock-pdf-", ".pdf");
    try {
      try (PDDocument copy = composed()) {
        if (selection != null) {
          Set<Integer> keep = new HashSet<>();
          for (int i : selection) keep.add(i);
          for (int i = copy.getNumberOfPages() - 1; i >= 0; i--)
            if (!keep.contains(i)) copy.removePage(i);
        }
        cleanPageReferences(copy);
        copy.save(temp.toFile());
      }
      try (PDDocument verified = PDDocument.load(temp.toFile())) {
        if (verified.getNumberOfPages() != (selection == null ? size() : selection.length))
          throw new IOException("Saved PDF failed page verification.");
      }
      try {
        Files.move(
            temp, absolute, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public synchronized byte[] bytes() throws IOException {
    try (PDDocument copy = composed()) {
      var out = new ByteArrayOutputStream();
      copy.save(out);
      return out.toByteArray();
    }
  }

  @Override
  public synchronized void close() throws IOException {
    previews.clear();
    undo.clear();
    redo.clear();
    document.close();
  }
}
