package services;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.Font;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ShirtAssetsTest {
    @Test void privateStaffRendererRetainsTheConfiguredColorAndBothViews()throws Exception{
        var shirt=ShirtDesignerServiceTest.item();shirt.add("config",ShirtDesignerService.defaults());var design=ShirtDesignerServiceTest.design();
        var blue=ShirtPreviewRenderer.render(shirt,design,"FRONT","#2266aa",Map.of());var red=ShirtPreviewRenderer.render(shirt,design,"FRONT","#aa2222",Map.of());var back=ShirtPreviewRenderer.render(shirt,design,"BACK","#2266aa",Map.of());
        assertEquals(900,blue.getWidth());assertNotEquals(blue.getRGB(450,700),red.getRGB(450,700));assertNotEquals(blue.getRGB(450,100),back.getRGB(450,100));assertTrue(blue.getColorModel().hasAlpha());
    }
    @Test void garmentModelsAreBundledAsBinaryGltf()throws Exception{
        for(String style:List.of("crew","vneck","polo")){String name="assets/shirts/"+style+".glb";assertEquals(name,StorefrontWebServer.bundledFile("/shop/"+name));assertEquals("model/gltf-binary",StorefrontWebServer.bundledContentType(name));
            try(var in=getClass().getClassLoader().getResourceAsStream("storefront-web/"+name)){assertNotNull(in);byte[] bytes=in.readAllBytes();assertTrue(bytes.length>500000);assertEquals("glTF",new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII));}}
        try(var in=getClass().getClassLoader().getResourceAsStream("storefront-web/assets/shirts/LICENSE.txt")){assertNotNull(in);assertTrue(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).contains("MIT License"));}
    }
    @Test void mockupsAndLicensedFontsAreBundledAndServedWithTheirActualTypes()throws Exception{
        for(String name:List.of("shirt-front.png","shirt-back.png","shirt-vneck-front.png","shirt-collared-front.png","shirt-collared-back.png")){
            assertEquals(name,StorefrontWebServer.bundledFile("/shop/"+name));assertEquals("image/png",StorefrontWebServer.bundledContentType(name));
            try(var in=getClass().getClassLoader().getResourceAsStream("storefront-web/"+name)){assertNotNull(in);var image=ImageIO.read(in);assertNotNull(image);assertTrue(image.getColorModel().hasAlpha());assertTrue(image.getWidth()>=900);}
        }
        for(String name:List.of("shirt-sans.ttf","shirt-serif.ttf","shirt-mono.ttf")){
            assertEquals("fonts/"+name,StorefrontWebServer.bundledFile("/shop/fonts/"+name));assertEquals("font/ttf",StorefrontWebServer.bundledContentType(name));
            try(var in=getClass().getClassLoader().getResourceAsStream("storefront-web/fonts/"+name)){assertNotNull(in);assertNotNull(Font.createFont(Font.TRUETYPE_FONT,in));}
        }
        for(String name:List.of("NotoSans-OFL.txt","NotoSerif-OFL.txt","RobotoMono-OFL.txt"))try(var in=getClass().getClassLoader().getResourceAsStream("storefront-web/fonts/"+name)){assertNotNull(in);}
    }
}
