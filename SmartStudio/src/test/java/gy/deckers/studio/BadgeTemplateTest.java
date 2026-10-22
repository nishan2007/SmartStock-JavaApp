package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BadgeTemplateTest {
    @TempDir Path temp;
    @Test void retentionIsOptInAndTemplateNeverContainsPersonalPhotos() throws Exception {
        BadgeTemplate template=BadgeTemplate.builtin();
        assertFalse(template.savePrintRecords);
        template.savePrintRecords=true;
        var slot=template.elements.stream().filter(e->e.type.equals("PHOTO")).findFirst().orElseThrow();
        slot.personalPhoto=new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB);
        slot.photoFraming=PhotoFraming.initial(slot.personalPhoto);
        Path file=temp.resolve("retention.sstbadge");template.save(file);
        BadgeTemplate loaded=BadgeTemplate.load(file);
        assertTrue(loaded.savePrintRecords);
        assertNull(loaded.elements.stream().filter(e->e.type.equals("PHOTO")).findFirst().orElseThrow().personalPhoto);
        assertFalse(Files.readString(file).contains("personalPhoto"));
        loaded.savePrintRecords=false;loaded.save(file);
        assertFalse(BadgeTemplate.load(file).savePrintRecords);
    }
    @Test void parkedLayersRoundTripAndOnlyPageIntersectionPrints() throws Exception {
        BadgeTemplate t=new BadgeTemplate();
        BadgeTemplate.Element e=new BadgeTemplate.Element();
        e.label="Parked photo";e.type="PHOTO";e.x=-20;e.y=2;e.width=10;e.height=10;t.elements.add(e);
        Path file=temp.resolve("parked.sstbadge");t.save(file);t=BadgeTemplate.load(file);
        assertEquals(-20,t.elements.get(0).x);
        BufferedImage blank=t.render(Map.of(),null,72);
        for(int y=0;y<blank.getHeight();y++)for(int x=0;x<blank.getWidth();x++)assertEquals(0xffffff,blank.getRGB(x,y)&0xffffff);
        t.elements.get(0).x=-5;
        BufferedImage partial=t.render(Map.of(),null,72);
        assertNotEquals(0xffffff,partial.getRGB(1,8)&0xffffff);
        assertEquals(0xffffff,partial.getRGB(20,8)&0xffffff);
        t.elements.get(0).x=5;
        assertNotEquals(0xffffff,t.render(Map.of(),null,72).getRGB(16,8)&0xffffff);
    }
    @Test void recoveredDesignRoundTripsWithoutPupilData() throws Exception {
        BadgeTemplate template=BadgeTemplate.builtin();
        assertEquals(8,template.elements.size());
        template.render(Map.of("fullName","Private test pupil","phone","Private test phone"),new BufferedImage(100,120,BufferedImage.TYPE_INT_RGB),300);
        Path file=temp.resolve("badge.sstbadge");template.save(file);
        String saved=Files.readString(file);
        assertFalse(saved.contains("Private test"));
        assertEquals("Full name",template.elements.get(4).text);
        assertEquals("Contact phone",template.elements.get(6).text);
        assertTrue(template.elements.stream().filter(e->"PHOTO".equals(e.type)).allMatch(e->e.image.isEmpty()));
        BadgeTemplate loaded=BadgeTemplate.load(file);
        assertEquals(template.name,loaded.name);
        BufferedImage output=loaded.render(Map.of(),null,300);
        assertEquals(1011,output.getWidth());assertEquals(638,output.getHeight());
        assertEquals(0x00ff00,output.getRGB(0,0)&0xffffff);
        assertEquals(0xffffff,output.getRGB(70,300)&0xffffff);
        assertEquals(template.elements.get(7).image,loaded.elements.get(7).image);
    }
    @Test void rejectsUnknownVersionsInvalidGeometryAndArtwork() throws Exception {
        BadgeTemplate t=BadgeTemplate.builtin();t.formatVersion=2;assertThrows(IllegalArgumentException.class,t::validate);
        t.formatVersion=1;t.elements.get(0).x=Double.NaN;assertThrows(IllegalArgumentException.class,t::validate);
        t.elements.get(0).x=0;t.elements.get(0).image="not an image";assertThrows(IllegalArgumentException.class,t::validate);
        assertThrows(IOException.class,()->BadgeTemplate.read(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8))));
    }
    @Test void longNamesFitAndFileLimitIsEnforced() throws Exception {
        BadgeTemplate t=BadgeTemplate.builtin();assertNotNull(t.render(Map.of("fullName","A very long pupil name that must fit inside the designated name area"),null,300));
        assertThrows(IOException.class,()->BadgeTemplate.read(new ByteArrayInputStream(new byte[BadgeTemplate.MAX_BYTES+1])));
    }
}
