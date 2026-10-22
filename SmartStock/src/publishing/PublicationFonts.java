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
        ||FAMILIES.contains(family(requested));
  }
  public static Font create(String requested,int style,float size){
    Font library=shared(requested);if(library!=null){
      if(library.getFamily(Locale.ROOT).equalsIgnoreCase(requested))
        for(Font candidate:shared)if(candidate.getFamily(Locale.ROOT).equalsIgnoreCase(requested)&&faceStyle(candidate)==style)
          return candidate.deriveFont(Font.PLAIN,size);
      return library.deriveFont(style&~faceStyle(library),size);
    }
    return new Font(family(requested),style,1).deriveFont(size);
  }
  private static int faceStyle(Font font){
    String name=font.getFontName(Locale.ROOT).toLowerCase(Locale.ROOT);
    return (name.matches(".*\\bbold\\b.*")?Font.BOLD:0)|(name.matches(".*\\b(italic|oblique)\\b.*")?Font.ITALIC:0);
  }
}
