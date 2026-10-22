package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class BadgeTemplatePickerTest {
    @Test void searchFiltersNamesWithoutChangingTheSelectedTemplateIdentity()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var current=new BadgeTemplatePicker.Choice("Continue current design",BadgeTemplatePicker.Kind.CURRENT,null);
            var saved=new BadgeTemplatePicker.Choice("Skeldon Primary Staff",BadgeTemplatePicker.Kind.FILE,Path.of("Staff.sstbadge"));
            var builtin=new BadgeTemplatePicker.Choice("Skeldon Primary",BadgeTemplatePicker.Kind.BUILTIN,null);
            var selected=new AtomicReference<BadgeTemplatePicker.Choice>();
            BadgeTemplatePicker picker=new BadgeTemplatePicker(List.of(current,builtin,saved),new JLabel("Preview"),selected::set);
            picker.search("PRIMARY staff");assertEquals(1,picker.resultCount());assertSame(saved,picker.selection());assertSame(saved,selected.get());
            assertEquals(BadgeTemplatePicker.Kind.FILE,picker.selection().kind());assertEquals(Path.of("Staff.sstbadge"),picker.selection().file());
            picker.search("nothing matches");assertEquals(0,picker.resultCount());assertNull(picker.selection());assertNull(selected.get());
            picker.search("");assertEquals(3,picker.resultCount());assertSame(current,picker.selection());
        });
    }
    @Test void renderSearchAndEmptyStates()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            var saved=new BadgeTemplatePicker.Choice("Skeldon Primary Staff",BadgeTemplatePicker.Kind.FILE,Path.of("Staff.sstbadge"));
            BadgeTemplatePicker picker=new BadgeTemplatePicker(List.of(saved),new JLabel("Template preview",SwingConstants.CENTER),choice->{});
            picker.setSize(660,380);layout(picker);picker.search("staff");
            try{
                var image=new java.awt.image.BufferedImage(660,380,java.awt.image.BufferedImage.TYPE_INT_RGB);
                var graphics=image.createGraphics();graphics.setColor(ui.design.DeckersPalette.background());graphics.fillRect(0,0,660,380);picker.printAll(graphics);graphics.dispose();
                javax.imageio.ImageIO.write(image,"png",Path.of("target","template-search-preview.png").toFile());
            }catch(Exception ex){throw new RuntimeException(ex);}
            picker.search("unavailable");layout(picker);assertNull(picker.selection());
        });
    }
    private static void layout(Container parent){parent.doLayout();for(Component child:parent.getComponents())if(child instanceof Container nested)layout(nested);}
}
