package publishing;

import java.awt.*;
import java.util.*;
import java.util.List;

/** Resolves known naming variants without silently replacing a different typeface. */
public final class PublicationFonts {
  private PublicationFonts() {}
  private static final Set<String> FAMILIES=new HashSet<>(Arrays.asList(
      GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames(Locale.ROOT)));
  private static volatile List<Font> shared=List.of();
  private static final List<Font> INSTALLED=List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAllFonts());
  public record Face(String label,String name,boolean bold,boolean italic){public String toString(){return label;}}
  public static String fontFamily(String requested){
    for(Font font:shared)if(font.getFontName(Locale.ROOT).equalsIgnoreCase(requested))return font.getFamily(Locale.ROOT);
    for(Font font:INSTALLED)if(font.getFontName(Locale.ROOT).equalsIgnoreCase(requested))return font.getFamily(Locale.ROOT);
    return family(requested);
  }
  public static String[] families(){Set<String> names=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);names.addAll(FAMILIES);for(Font font:shared)names.add(font.getFamily(Locale.ROOT));return names.toArray(String[]::new);}
  public static List<Face> faces(String requested){
    String family=fontFamily(requested);Map<String,Face> result=new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    List<Font> fonts=new ArrayList<>(INSTALLED);fonts.addAll(shared);
    for(Font font:fonts)if(font.getFamily(Locale.ROOT).equalsIgnoreCase(family)){
      String name=font.getFontName(Locale.ROOT),label=name.replaceFirst("(?i)^"+java.util.regex.Pattern.quote(family)+"[ .,_-]*","");
      if(label.isBlank()||label.matches("(?i)plain|regular|roman|normal"))label="Regular";
      else if(label.matches("(?i)bold[ -]?(italic|oblique)"))label="Bold Italic";
      else if(label.equalsIgnoreCase("bold"))label="Bold";else if(label.matches("(?i)italic|oblique"))label="Italic";
      int style=faceStyle(font);result.put(name,new Face(label,name,(style&Font.BOLD)!=0,(style&Font.ITALIC)!=0));
    }
    var faces=new ArrayList<>(result.values());faces.sort(Comparator.comparingInt((Face face)->face.label.equalsIgnoreCase("Regular")||face.label.equalsIgnoreCase("Plain")?0:1).thenComparing(Face::label,String.CASE_INSENSITIVE_ORDER));return List.copyOf(faces);
  }
  public static void sharedFonts(Collection<Font> fonts){shared=List.copyOf(fonts);}
  public static String[] names(){
    Set<String> names=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);names.addAll(FAMILIES);
    for(Font font:shared){names.add(font.getFamily(Locale.ROOT));names.add(font.getFontName(Locale.ROOT));}
    return names.toArray(String[]::new);
  }
  private static Font shared(String requested){
    for(Font font:shared)if(font.getFontName(Locale.ROOT).equalsIgnoreCase(requested))return font;
    for(Font font:shared)if(font.getFamily(Locale.ROOT).equalsIgnoreCase(requested)&&font.getFontName(Locale.ROOT).equalsIgnoreCase(font.getFamily(Locale.ROOT)))return font;
    for(Font font:shared)if(font.getFamily(Locale.ROOT).equalsIgnoreCase(requested))return font;
    return null;
  }
  public static String family(String requested){return family(requested,FAMILIES);}
  static String family(String requested,Set<String> installed){
    for(String name:installed)if(name.equalsIgnoreCase(requested))return name;
    if(requested.equalsIgnoreCase("Monotype Corsiva"))
      for(String name:installed)if(name.equalsIgnoreCase("Monotype Corsiva Regular"))return name;
    return requested;
  }
  public static boolean available(String requested){
    return shared(requested)!=null||Set.of("SansSerif","Serif","Monospaced","Dialog","DialogInput").contains(requested)
        ||FAMILIES.contains(family(requested))||INSTALLED.stream().anyMatch(font->font.getFontName(Locale.ROOT).equalsIgnoreCase(requested));
  }
  public static Font create(String requested,int style,float size){
    Font library=shared(requested);if(library!=null){
      if(library.getFamily(Locale.ROOT).equalsIgnoreCase(requested))
        for(Font candidate:shared)if(candidate.getFamily(Locale.ROOT).equalsIgnoreCase(requested)&&faceStyle(candidate)==style)
          return candidate.deriveFont(Font.PLAIN,size);
      return library.deriveFont(style&~faceStyle(library),size);
    }
    for(Font font:INSTALLED)if(font.getFontName(Locale.ROOT).equalsIgnoreCase(requested))return font.deriveFont((style&~faceStyle(font))|font.getStyle(),size);
    return new Font(family(requested),style,1).deriveFont(size);
  }
  private static int faceStyle(Font font){
    String name=font.getFontName(Locale.ROOT).toLowerCase(Locale.ROOT);
    return (font.isBold()||name.matches(".*\\b(bold|semibold|demibold|black|heavy|extrabold)\\b.*")?Font.BOLD:0)|(font.isItalic()||name.matches(".*\\b(italic|oblique)\\b.*")?Font.ITALIC:0);
  }
}
