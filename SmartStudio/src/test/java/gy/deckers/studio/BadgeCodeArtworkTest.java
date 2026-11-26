package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.Publication;
import publishing.PublicationBarcode;
import javax.swing.*;
import java.nio.file.Path;
import java.awt.*;
import static org.junit.jupiter.api.Assertions.*;

class BadgeCodeArtworkTest {
    @TempDir Path folder;
    @Test void generatedBadgeCodesDecodeBeforeAndAfterPortableSave()throws Exception{
        for(boolean qr:new boolean[]{false,true}){
            var code=new Publication.Element();code.type=qr?"QR":"BARCODE";code.barcodeFormat=qr?"QR_CODE":"CODE_128";
            code.text=qr?"https://deckers.gy/badge/123":"BADGE-123";code.barcodeText=true;
            var artwork=BadgeEditableArtwork.code(code);
            assertEquals(code.text,PublicationBarcode.decode(artwork.image()).orElseThrow().text());
            var badge=new BadgeTemplate();badge.name="Generated code";badge.widthMm=qr?25:50;badge.heightMm=qr?25:20;
            var layer=new BadgeTemplate.Element();layer.type="IMAGE";layer.label=artwork.name();layer.image=java.util.Base64.getEncoder().encodeToString(artwork.bytes());layer.width=badge.widthMm;layer.height=badge.heightMm;badge.elements.add(layer);
            Path file=folder.resolve(qr?"qr.sstbadge":"barcode.sstbadge");badge.save(file);
            var loaded=BadgeTemplate.load(file);assertEquals("IMAGE",loaded.elements.get(0).type);
            var editable=BadgeEditableArtwork.read(loaded.elements.get(0));
            assertEquals(BadgeEditableArtwork.Kind.CODE,editable.kind());
            assertEquals(code.text,editable.objects().get(0).text);
            assertEquals(code.barcodeFormat,editable.objects().get(0).barcodeFormat);
            var edited=editable.objects().get(0);
            edited.text="UPDATED-123";
            assertEquals(edited.text,PublicationBarcode.decode(BadgeEditableArtwork.code(edited).image()).orElseThrow().text());
            assertEquals(code.text,PublicationBarcode.decode(loaded.render(java.util.Map.of(),null,600)).orElseThrow().text());
        }
    }
    @Test void badgeInsertRibbonExposesBothGeneratorsAtCompactAndNormalWidths()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var panel=new BadgeCardsPanel();var ribbon=find(panel,StudioRibbon.class);ribbon.select("Insert");ribbon.setCollapsed(false);
            for(int width:new int[]{820,1100}){
                panel.setSize(width,700);layout(panel);var tabs=find(ribbon,JTabbedPane.class);var insert=(Container)tabs.getSelectedComponent();
                assertNotNull(button(insert,"Add barcode…"));assertNotNull(button(insert,"Add QR code…"));
            }
        });
    }
    private static JButton button(Container parent,String name){for(Component c:parent.getComponents()){if(c instanceof JButton b&&name.equals(b.getText()))return b;if(c instanceof Container n){var found=button(n,name);if(found!=null)return found;}}return null;}
    private static <T>T find(Container parent,Class<T> type){for(Component c:parent.getComponents()){if(type.isInstance(c))return type.cast(c);if(c instanceof Container n){T found=find(n,type);if(found!=null)return found;}}return null;}
    private static void layout(Container parent){parent.doLayout();for(Component c:parent.getComponents())if(c instanceof Container n)layout(n);}
}
