package services;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import static org.junit.jupiter.api.Assertions.*;

class AdDraftRendererTest {
    @Test void rendersAllCoverageFormatsWithoutCroppingTheProduct() {
        var source=new BufferedImage(200,400,BufferedImage.TYPE_INT_RGB);
        for(String kind:new String[]{"ad_square","ad_portrait","ad_story","ad_landscape"}) {
            var result=AdDraftRenderer.render(kind,source,"Product headline");
            var expected=AdDraftRenderer.size(kind);
            assertEquals(expected.width,result.getWidth());
            assertEquals(expected.height,result.getHeight());
            assertDoesNotThrow(() -> AdDraftRenderer.validate(kind,result));
            assertEquals(source.getRGB(100,200),result.getRGB(result.getWidth()/2,(result.getHeight()-200)/2));
        }
    }
    @Test void rejectsMissingWrongFormatAndUnsupportedImages() {
        assertThrows(IllegalArgumentException.class,()->AdDraftRenderer.validate("ad_square",null));
        assertThrows(IllegalArgumentException.class,()->AdDraftRenderer.validate("ad_story",new BufferedImage(100,100,1)));
        assertThrows(IllegalArgumentException.class,()->AdDraftRenderer.size("unknown"));
    }
}
