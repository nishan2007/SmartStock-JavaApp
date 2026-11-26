package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class BadgeTwoPagesTest {
    @TempDir Path folder;
    private BadgeTemplate card(String color) {
        var t=new BadgeTemplate();var e=new BadgeTemplate.Element();
        e.label="Background";e.type="RECT";e.width=t.widthMm;e.height=t.heightMm;e.color=color;
        t.elements.add(e);return t;
    }
    @Test void bothPagesPersistAndRenderInOrderWhileEditingBack()throws Exception {
        var front=card("#ff0000");front.mergeBack(card("#0000ff"));front.editingBack=true;
        assertEquals(0xff0000,front.renderSide(false,Map.of(),null,72).getRGB(1,1)&0xffffff);
        assertEquals(0x0000ff,front.renderSide(true,Map.of(),null,72).getRGB(1,1)&0xffffff);
        assertTrue(front.editingBack);
        var file=folder.resolve("both.sstbadge");front.save(file);
        var loaded=BadgeTemplate.load(file);assertFalse(loaded.editingBack);
        assertEquals("#ff0000",loaded.elements.get(0).color);
        assertEquals("#0000ff",loaded.backElements.get(0).color);
        var portable=BadgeTemplate.read(new java.io.ByteArrayInputStream(BadgeTemplate.portableJson(front).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("#0000ff",portable.backElements.get(0).color);
    }
    @Test void frontOnlyTemplateOpensWithABlankEditableBack()throws Exception {
        var fresh=new BadgeTemplate();
        assertTrue(fresh.backElements.isEmpty());

        var legacy=new com.google.gson.Gson().toJsonTree(card("#ff0000")).getAsJsonObject();
        legacy.remove("backElements");
        var file=folder.resolve("front-only.sstbadge");
        java.nio.file.Files.writeString(file,legacy.toString());
        var loaded=BadgeTemplate.load(file);
        assertTrue(loaded.backElements.isEmpty());
        assertEquals(0xffffff,loaded.renderSide(true,Map.of(),null,72).getRGB(1,1)&0xffffff);

        loaded.save(file);
        var saved=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
        assertTrue(saved.getAsJsonArray("backElements").isEmpty());

        loaded.editingBack=true;
        loaded.pageElements().add(card("#0000ff").elements.get(0));
        assertEquals(0xff0000,loaded.renderSide(false,Map.of(),null,72).getRGB(1,1)&0xffffff);
        assertEquals(0x0000ff,loaded.renderSide(true,Map.of(),null,72).getRGB(1,1)&0xffffff);
    }
    @Test void backEditingClipboardAndRotationPreserveFront() {
        var t=card("#ff0000");t.mergeBack(card("#0000ff"));t.editingBack=true;
        var copied=StudioLayerClipboard.badge(t,new int[]{0});
        var pasted=StudioLayerClipboard.paste(t,copied);
        assertEquals(1,pasted.elements.size());assertEquals(2,pasted.backElements.size());
        assertEquals("#0000ff",pasted.backElements.get(1).color);
        assertTrue(TemplateOrientation.turn(t,true));
        assertEquals(90,t.elements.get(0).rotation);assertEquals(90,t.backElements.get(0).rotation);
    }
    @Test void invalidBackAndMismatchedSizeAreRejected() {
        var t=card("#ff0000");var back=card("#0000ff");back.widthMm=54;
        assertThrows(IllegalArgumentException.class,()->t.mergeBack(back));assertTrue(t.backElements.isEmpty());
        t.backElements.add(new BadgeTemplate.Element());assertThrows(IllegalArgumentException.class,t::validate);
    }
}

