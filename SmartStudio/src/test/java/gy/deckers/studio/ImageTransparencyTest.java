package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ImageTransparencyTest {
    private byte[] red()throws Exception {
        var image=new BufferedImage(40,40,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setColor(Color.RED);g.fillRect(0,0,40,40);g.dispose();var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
    @Test void badgeTransparencySavesAndBlendsWithBackground()throws Exception {
        var badge=new BadgeTemplate();badge.widthMm=badge.heightMm=20;
        var e=new BadgeTemplate.Element();e.label="Picture";e.type="IMAGE";e.width=e.height=20;
        e.image=Base64.getEncoder().encodeToString(red());e.transparency=50;badge.elements.add(e);
        var restored=BadgeTemplate.read(new java.io.ByteArrayInputStream(new com.google.gson.Gson().toJson(badge).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(50,restored.elements.get(0).transparency);
        int rgb=restored.render(Map.of(),null,254).getRGB(100,100);
        assertEquals(255,(rgb>>16)&255);assertTrue((rgb&255)>=126&&(rgb&255)<=129);
    }
    @Test void publicationTransparencySurvivesArchiveAndRendering()throws Exception {
        var p=new Publication();p.width=p.height=20;p.pages.get(0).objects.clear();
        p.assets.put("red",Base64.getEncoder().encodeToString(red()));
        var e=new Publication.Element();e.type="IMAGE";e.asset="red";e.x=e.y=0;e.width=e.height=20;e.transparency=100;p.pages.get(0).objects.add(e);
        var restored=PublicationFiles.read(PublicationFiles.archive(p));assertEquals(100,restored.pages.get(0).objects.get(0).transparency);
        var image=new BufferedImage(200,200,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.scale(10,10);new PublicationRenderer(restored).page(g,0,Map.of());g.dispose();
        assertEquals(0xffffff,image.getRGB(100,100)&0xffffff);
    }
}
