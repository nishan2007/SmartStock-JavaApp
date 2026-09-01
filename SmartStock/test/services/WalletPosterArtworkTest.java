package services;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WalletPosterArtworkTest {
    private WalletBadgeTemplate sample() throws Exception {
        BufferedImage signature=new BufferedImage(60,20,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=signature.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,60,20);g.setColor(Color.BLACK);g.drawLine(0,15,60,5);g.dispose();
        String encoded=Base64.getEncoder().encodeToString(WalletBadgeTemplate.image(signature,396,168,100));
        var t=WalletBadgeTemplate.defaults();
        return new WalletBadgeTemplate(t.company(),t.title(),t.background(),t.foreground(),t.labelColor(),t.fields(),t.logoPng(),t.thumbnailPng(),t.logoScale(),t.thumbnailScale(),t.employeePhoto(),new WalletBadgeTemplate.Poster("",encoded,100));
    }
    @Test void legacyJsonLoadsWithoutPosterAndRoundTripPreservesArtwork() throws Exception {
        String legacy=WalletBadgeTemplate.defaults().json().replaceAll(",\"poster\":\\{[^}]*\\}","");
        assertFalse(WalletBadgeTemplate.parse(legacy).poster().enabled());
        var t=sample();assertEquals(t,WalletBadgeTemplate.parse(t.json()));
        var imported=WalletCompanyBranding.apply(t,new WalletCompanyBranding.Info("Company","","","","",""),p->null);
        assertEquals(t.poster(),imported.poster());
    }
    @Test void signatureIsInsideRightArtworkAboveReservedFooter() throws Exception {
        var t=sample();var image=WalletPosterArtwork.render(t,3);
        assertEquals(1074,image.getWidth());assertEquals(1344,image.getHeight());
        Rectangle r=WalletPosterArtwork.SIGNATURE_AREA;
        assertTrue(r.x>WalletPosterArtwork.WIDTH/2);assertTrue(r.y+r.height<310);
        assertEquals(Color.decode(t.background()).getRGB(),image.getRGB(500,1200),"QR/footer region must contain no signature");
        javax.imageio.ImageIO.write(ui.screens.companyprefs.WalletTemplatePreview.render(t,100),"png",java.nio.file.Path.of("target/wallet-poster-preview.png").toFile());
    }
    @Test void posterHasOneFooterAndRetainsSecondaryInformationInDetails() throws Exception {
        var result=WalletPosterArtwork.fields(sample(),Map.of("NAME","Alice","ROLE","Manager","LOCATION","Store"));
        assertEquals(1,((java.util.List<?>)result.get("footerFields")).size());
        assertTrue(result.get("backFields").toString().contains("Manager"));
        assertFalse(result.containsKey("secondaryFields"));
        assertThrows(IllegalArgumentException.class,()->new WalletBadgeTemplate.Poster("https://example.com/image.png","",100));
        assertThrows(IllegalArgumentException.class,()->new WalletBadgeTemplate.Poster("","",0));
    }
}
