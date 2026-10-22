package publishing;

import com.google.gson.Gson;
import java.util.*;

/** Portable, versioned publication. All geometry is millimetres, never screen pixels. */
public final class Publication {
  public static final Gson JSON = new Gson();
  public int version = 1;
  public String title = "Untitled publication", displayUnit = "MM";
  public double width = 90, height = 50;
  public boolean template;
  public Long orderId, lineId;
  public List<Page> pages = new ArrayList<>();
  public List<Page> masters = new ArrayList<>();
  public Map<String, String> stories = new LinkedHashMap<>();
  public Map<String, Element> styles = new LinkedHashMap<>();
  // Immutable encoded bytes live in archive entries, not document JSON or undo serialization.
  public transient Map<String, String> assets = new LinkedHashMap<>();
  /** Local immutable source files; never serialized as machine paths. */
  public transient Map<String, java.nio.file.Path> assetFiles = new LinkedHashMap<>();
  public Set<String> assetIds(){Set<String> ids=new LinkedHashSet<>(assets.keySet());ids.addAll(assetFiles.keySet());return ids;}
  public java.io.InputStream openAsset(String id)throws java.io.IOException {
    if(assetFiles.containsKey(id))return java.nio.file.Files.newInputStream(assetFiles.get(id));
    String encoded=assets.get(id);if(encoded==null)throw new java.io.IOException("Missing image asset: "+id);
    return Base64.getDecoder().wrap(new java.io.ByteArrayInputStream(encoded.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
  }
  public long assetSize(String id)throws java.io.IOException {
    if(assetFiles.containsKey(id))return java.nio.file.Files.size(assetFiles.get(id));
    String encoded=assets.get(id);if(encoded==null)throw new java.io.IOException("Missing asset: "+id);
    return (long)encoded.length()/4*3-(encoded.endsWith("==")?2:encoded.endsWith("=")?1:0);
  }
  public byte[] assetBytes(String id)throws java.io.IOException {
    try(var input=openAsset(id)){byte[] value=input.readNBytes(100*1024*1024+1);if(value.length>100*1024*1024)throw new java.io.IOException("Image exceeds 100 MiB.");return value;}
  }
  public List<Map<String, String>> mergeRows = new ArrayList<>();
  public SheetLayout.Settings print = new SheetLayout.Settings();

  public Publication() {
    pages.add(new Page());
  }

  public static final class Page {
    public String name = "Page", master = "";
    public boolean reference;
    public List<Element> objects = new ArrayList<>();
    /** Linked print views contain memberships only; geometry remains on the original objects. */
    public List<RisoPage> risoPages = new ArrayList<>();
    public Set<String> risoExcluded = new LinkedHashSet<>();
    public List<Double> horizontalGuides = new ArrayList<>(), verticalGuides = new ArrayList<>();

    @Override
    public String toString() {
      return name + (reference ? " (reference; not printed)" : "");
    }
  }

  public static final class RisoPage {
    public String id=UUID.randomUUID().toString(),name="Riso sub-page",color="#000000";
    public Set<String> objectIds=new LinkedHashSet<>();
  }

  public static final class Element {
    public int transparency;
    public String id = UUID.randomUUID().toString(),
        type = "TEXT",
        text = "Text",
        asset = "",
        story = "",
        group = "";
    public String font = "SansSerif", fill = "#ffffff", color = "#222222", stroke = "#222222";
    public double x = 5, y = 5, width = 60, height = 15, rotation, fontSize = 12, strokeWidth = .25;
    public double cropX, cropY, cropWidth = 1, cropHeight = 1;
    public boolean bold, italic, underline, strike, textOutline, locked;
    public String textOutlineColor="#000000";
    public double textOutlineWidth=.2;
    /** Geometry text stretches to its original Publisher field; imported text artwork is optional. */
    public boolean fitText;
    public String wordArt = "NONE", wordArtEndColor = "#8844cc", wordArtOutline = "#222222", wordArtShadow = "#777777";
    public double wordArtBend = .3, wordArtOutlineWidth = .2;
    public boolean wordArtGradient, wordArtHasShadow, wordArtOutsideBounds;
    public String originalText="",originalFont="",originalColor="";
    public double originalFontSize;
    public boolean originalBold;
    public double textRotation;
    public String sourceTable="";
    public int sourceRow,sourceColumn,rowSpan=1,columnSpan=1;
    public String barcodeFormat="CODE_128";
    public boolean barcodeText=true;
    public int columns = 1, tableColumns = 2;

    @Override
    public String toString() {
      return (locked ? "🔒 " : "") + type + " · " + text.substring(0, Math.min(30, text.length()));
    }
  }

  public Publication copy() {
    Publication p = JSON.fromJson(JSON.toJson(this), Publication.class);
    p.assets = new LinkedHashMap<>(assets);
    p.assetFiles = new LinkedHashMap<>(assetFiles);
    return p;
  }

  public static double toMm(double value, String unit) {
    return switch (unit) {
      case "IN" -> value * 25.4;
      case "CM" -> value * 10;
      case "FT" -> value * 304.8;
      case "YD" -> value * 914.4;
      case "M" -> value * 1000;
      default -> value;
    };
  }

  public static double fromMm(double value, String unit) {
    return value / toMm(1, unit);
  }

  public void resize(double w, double h, boolean scale) {
    positive(w);
    positive(h);
    if (scale)
      for (Page p : allPages()) {
        p.horizontalGuides.replaceAll(y -> y * h / height);
        p.verticalGuides.replaceAll(x -> x * w / width);
        for (Element e : p.objects) {
          e.x *= w / width;
          e.width *= w / width;
          e.y *= h / height;
          e.height *= h / height;
          e.fontSize *= Math.min(w / width, h / height);
        }
      }
    width = w;
    height = h;
  }

  public List<Page> allPages() {
    List<Page> all = new ArrayList<>(masters);
    all.addAll(pages);
    return all;
  }

  public void validate() {
    if (version != 1 && version != 2)
      throw new IllegalArgumentException("Unsupported publication version: " + version);
    positive(width);
    positive(height);
    if (!Set.of("MM", "CM", "IN", "FT").contains(displayUnit))
      throw new IllegalArgumentException("Invalid display unit.");
    if (title == null
        || title.length() > 200
        || pages == null
        || pages.isEmpty()
        || pages.size() > 500
        || masters == null
        || masters.size() > 100)
      throw new IllegalArgumentException("Invalid publication pages or title.");
    if (assets == null || assetFiles == null
        || assets.size()+assetFiles.size() > 10000
        || stories == null
        || styles == null
        || mergeRows == null
        || mergeRows.size() > 10000
        || print == null) throw new IllegalArgumentException("Invalid publication resources.");
    int count = 0, risoCount=0;
    Set<String> ids = new HashSet<>();
    for (Page p : allPages()) {
      if (p == null || p.objects == null || p.name == null || p.master == null)
        throw new IllegalArgumentException("Invalid page.");
      if(p.risoPages==null||p.risoExcluded==null||p.risoPages.size()>32)throw new IllegalArgumentException("Invalid Riso sub-pages.");
      if(version==1&&(!p.risoPages.isEmpty()||!p.risoExcluded.isEmpty()))throw new IllegalArgumentException("Linked Riso sub-pages require publication version 2.");
      Set<String> risoIds=new HashSet<>(),members=new HashSet<>();
      for(RisoPage sub:p.risoPages){
        if(sub==null||sub.id==null||!sub.id.matches("[A-Za-z0-9_-]{1,100}")||!risoIds.add(sub.id)||sub.name==null||sub.name.isBlank()||sub.name.length()>100||sub.color==null||!sub.color.matches("#[0-9a-fA-F]{6}")||sub.objectIds==null||sub.objectIds.size()>10000)throw new IllegalArgumentException("Invalid Riso sub-page.");
        for(String id:sub.objectIds)if(id==null||id.isBlank()||id.length()>200||!members.add(id))throw new IllegalArgumentException("An object may belong to only one Riso sub-page per main page.");
      }
      for(String id:p.risoExcluded)if(id==null||id.isBlank()||id.length()>200||!members.add(id))throw new IllegalArgumentException("Invalid Riso exclusion.");
      if(members.size()>10000||(risoCount+=members.size())>100000)throw new IllegalArgumentException("Too many Riso object memberships.");
      if (p.horizontalGuides == null
          || p.verticalGuides == null
          || p.horizontalGuides.size() > 1000
          || p.verticalGuides.size() > 1000)
        throw new IllegalArgumentException("Invalid page guides.");
      for (double guide : p.horizontalGuides)
        if (!Double.isFinite(guide)) throw new IllegalArgumentException("Invalid guide.");
      for (double guide : p.verticalGuides)
        if (!Double.isFinite(guide)) throw new IllegalArgumentException("Invalid guide.");
      for (Element e : p.objects) {
        if (++count > 10000
            || e == null
            || !Set.of("TEXT", "IMAGE", "RECT", "ELLIPSE", "LINE", "QR", "BARCODE", "TABLE", "CYLINDER")
                .contains(e.type))
          throw new IllegalArgumentException("Invalid or excessive publication objects.");
        positive(e.width);
        positive(e.height);
        positive(e.fontSize);
        if(e.fontSize>10000)throw new IllegalArgumentException("Font size must not exceed 10000 points.");
        if(e.textOutlineColor==null||!e.textOutlineColor.matches("#[0-9a-fA-F]{6}")||!Double.isFinite(e.textOutlineWidth)||e.textOutlineWidth<0||e.textOutlineWidth>5)throw new IllegalArgumentException("Invalid text outline.");
        if (e.wordArt == null || !Set.of("NONE","PLAIN","ARCH_UP","ARCH_DOWN","WAVE").contains(e.wordArt)
            || !Double.isFinite(e.wordArtBend) || Math.abs(e.wordArtBend)>1
            || !Double.isFinite(e.wordArtOutlineWidth) || e.wordArtOutlineWidth<0 || e.wordArtOutlineWidth>5
            || e.wordArtEndColor==null || e.wordArtOutline==null || e.wordArtShadow==null)
          throw new IllegalArgumentException("Invalid WordArt style.");
        if (e.id == null
            || e.font == null
            || e.asset == null
            || e.story == null
            || e.group == null
            || e.originalText == null || e.originalFont == null || e.originalColor == null
            || e.sourceTable == null || e.barcodeFormat == null
            || !Double.isFinite(e.textRotation+e.originalFontSize)
            || e.sourceRow<0 || e.sourceColumn<0 || e.rowSpan<1 || e.columnSpan<1
            || e.strokeWidth < 0
            || e.strokeWidth > 100
            || !Double.isFinite(e.cropX + e.cropY + e.cropWidth + e.cropHeight))
          throw new IllegalArgumentException("Invalid object style or crop.");
        if (!ids.add(e.id)
            || e.fill == null
            || e.stroke == null
            || e.color == null
            || (!e.story.isBlank() && !stories.containsKey(e.story)))
          throw new IllegalArgumentException("Duplicate object identifier or invalid style/story.");
        if (e.transparency<0||e.transparency>100)throw new IllegalArgumentException("Invalid image transparency.");
        if (!Double.isFinite(e.x + e.y + e.rotation + e.strokeWidth)
            || e.text == null
            || e.text.length() > 100000
            || e.columns < 1
            || e.columns > 20
            || e.tableColumns < 1
            || e.tableColumns > 100)
          throw new IllegalArgumentException("Invalid object properties.");
        if (e.cropX < 0
            || e.cropY < 0
            || e.cropWidth <= 0
            || e.cropHeight <= 0
            || e.cropX + e.cropWidth > 1.000001
            || e.cropY + e.cropHeight > 1.000001)
          throw new IllegalArgumentException("Image crop must be inside the image.");
      }
    }
    for (String key : assetIds())
      if (!key.matches("[A-Za-z0-9_-]{1,100}"))
        throw new IllegalArgumentException("Invalid asset identifier.");
    for(var file:assetFiles.entrySet())if(file.getValue()==null||assets.containsKey(file.getKey()))throw new IllegalArgumentException("Invalid or duplicate file-backed asset.");
    for (var story : stories.entrySet())
      if (story.getKey() == null || story.getValue() == null || story.getValue().length() > 1000000)
        throw new IllegalArgumentException("Invalid text story.");
    for (var row : mergeRows)
      if (row == null
          || row.size() > 200
          || row.entrySet().stream()
              .anyMatch(
                  e ->
                      e.getKey() == null || e.getValue() == null || e.getValue().length() > 100000))
        throw new IllegalArgumentException("Invalid merge row.");
    print.validate();
  }

  private static void positive(double n) {
    if (!Double.isFinite(n) || n <= 0 || n > 1000000)
      throw new IllegalArgumentException("Dimensions must be greater than 0 and at most 1000000 mm.");
  }

  public static Publication preset(String name) {
    Publication p = new Publication();
    p.title = name;
    switch (name) {
      case "Label" -> {
        p.width = 63.5;
        p.height = 38.1;
      }
      case "Card" -> {
        p.width = 90;
        p.height = 50;
      }
      case "Flyer" -> {
        p.width = 210;
        p.height = 297;
        p.print.left = p.print.right = p.print.top = p.print.bottom = 0;
      }
      case "Booklet" -> {
        p.width = 143.5;
        p.height = 200;
        p.print.width = 297;
        p.print.height = 210;
        p.print.booklet = true;
        p.print.backMapping = "SHORT_EDGE";
      }
      default -> throw new IllegalArgumentException("Unknown template.");
    }
    Element e = new Element();
    e.width = p.width - 10;
    e.text = name.equals("Label") ? "{{name}}\n{{price}}" : "Your " + name.toLowerCase();
    p.pages.get(0).objects.add(e);
    if (name.equals("Booklet"))
      for (int i = 2; i <= 4; i++) {
        Page page = new Page();
        page.name = "Page " + i;
        p.pages.add(page);
      }
    return p;
  }
}
