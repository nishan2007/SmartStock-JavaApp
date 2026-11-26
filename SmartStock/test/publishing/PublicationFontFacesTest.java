package publishing;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class PublicationFontFacesTest {
    @Test void styleChoicesAreRealFacesAndResolveWithoutLosingWeight(){
        for(String family:PublicationFonts.families()){
            var faces=PublicationFonts.faces(family);
            for(var face:faces){
                assertEquals(family,PublicationFonts.fontFamily(face.name()));assertTrue(PublicationFonts.available(face.name()));
                Font actual=PublicationFonts.create(face.name(),(face.bold()?Font.BOLD:0)|(face.italic()?Font.ITALIC:0),24);
                assertEquals(family,actual.getFamily(Locale.ROOT));assertEquals(24,actual.getSize2D());
                assertEquals(face.name(),actual.getFontName(Locale.ROOT),"Selecting a native style must retain that exact face");
            }
        }
        assertTrue(PublicationFonts.faces("Definitely missing font abc123").isEmpty());
    }
}
