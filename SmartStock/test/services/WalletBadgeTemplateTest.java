package services;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WalletBadgeTemplateTest {
    private WalletBadgeTemplate template(List<WalletBadgeTemplate.Field> fields) {
        var t=WalletBadgeTemplate.defaults();
        return new WalletBadgeTemplate(t.company(),t.title(),t.background(),t.foreground(),t.labelColor(),fields,"","",100,75,false);
    }
    @Test void roundTripAndSelectedFieldsUseOnlyAllowedEmployeeInformation() {
        var t=template(List.of(new WalletBadgeTemplate.Field(true,"primaryFields","NAME","EMPLOYEE",""),
                new WalletBadgeTemplate.Field(false,"secondaryFields","EMAIL","EMAIL",""),
                new WalletBadgeTemplate.Field(true,"backFields","TEXT","NOTE","Please return to reception")));
        assertEquals(t,WalletBadgeTemplate.parse(t.json()));
        String fields=new com.google.gson.Gson().toJson(t.passFields(Map.of("NAME","Alice","EMAIL","private@example.com","PIN","1234","BADGE_ID","physical-secret")));
        assertTrue(fields.contains("Alice"));assertTrue(fields.contains("Please return to reception"));
        assertFalse(fields.contains("private@example.com"));assertFalse(fields.contains("1234"));assertFalse(fields.contains("physical-secret"));
    }
    @Test void serverRejectsUnsafeSourcesAndExcessFrontFields() {
        for(String source:List.of("PIN","BADGE_ID","USER_ID","FILE","URL"))assertThrows(IllegalArgumentException.class,
                ()->template(List.of(new WalletBadgeTemplate.Field(true,"primaryFields",source,"LABEL",""))));
        var primary=new WalletBadgeTemplate.Field(true,"primaryFields","NAME","EMPLOYEE","");
        assertThrows(IllegalArgumentException.class,()->template(List.of(primary,primary)));
        assertThrows(IllegalArgumentException.class,()->template(Collections.nCopies(5,new WalletBadgeTemplate.Field(true,"secondaryFields","ROLE","ROLE",""))));
        assertThrows(RuntimeException.class,()->WalletBadgeTemplate.parse("null"));
        assertThrows(RuntimeException.class,()->WalletBadgeTemplate.parse("x".repeat(600001)));
        assertThrows(RuntimeException.class,()->WalletBadgeTemplate.parse(WalletBadgeTemplate.defaults().json().replace("#003760","red")));
    }
    @Test void imagesAreNormalizedBoundedAndRetainAspectRatio() throws Exception {
        BufferedImage source=new BufferedImage(200,100,BufferedImage.TYPE_INT_RGB);
        byte[] bytes=WalletBadgeTemplate.image(source,480,150,50);
        var normalized=WalletBadgeTemplate.decodeImage(Base64.getEncoder().encodeToString(bytes));
        assertEquals(480,normalized.getWidth());assertEquals(150,normalized.getHeight());
        assertEquals(0,normalized.getRGB(0,0));
        assertThrows(IllegalArgumentException.class,()->WalletBadgeTemplate.decodeImage("https://example.com/image.png"));
        assertThrows(IllegalArgumentException.class,()->WalletBadgeTemplate.decodeImage("A".repeat(250001)));
        byte[] large=WalletBadgeTemplate.image(source,481,150,100);
        assertThrows(IllegalArgumentException.class,()->WalletBadgeTemplate.decodeImage(Base64.getEncoder().encodeToString(large)));
    }
    @Test void previewRendersWithoutARealCredential() throws Exception {
        var t=WalletBadgeTemplate.defaults();
        BufferedImage preview=ui.screens.companyprefs.WalletTemplatePreview.render(t,100);
        assertEquals(360,preview.getWidth());assertEquals(470,preview.getHeight());
        java.nio.file.Path output=java.nio.file.Path.of("target/wallet-template-preview.png");
        javax.imageio.ImageIO.write(preview,"png",output.toFile());
        assertThrows(IllegalArgumentException.class,()->ui.screens.companyprefs.WalletTemplatePreview.render(t,0));
    }
}
