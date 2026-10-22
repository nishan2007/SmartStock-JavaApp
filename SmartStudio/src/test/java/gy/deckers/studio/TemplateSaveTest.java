package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.*;
import javax.swing.*;
import java.nio.file.*;
import java.lang.reflect.Field;
import static org.junit.jupiter.api.Assertions.*;

class TemplateSaveTest {
    @TempDir Path folder;

    @Test void controlSUpdatesOpenedFileAndPreservesLinkedPages()throws Exception{
        for(boolean labels:new boolean[]{false,true}){
            Path file=folder.resolve(labels?"label.sspub":"card.sspub");
            BusinessCardsPanel[] holder=new BusinessCardsPanel[1];
            SwingUtilities.invokeAndWait(()->{try{
                var panel=new BusinessCardsPanel(labels);holder[0]=panel;
                Publication p=(Publication)get(panel,"document");
                if(p.pages.get(0).objects.isEmpty())p.pages.get(0).objects.add(new Publication.Element());
                Files.write(file,PublicationFiles.archive(p));
                set(panel,"currentLocalFile",file);set(panel,"currentLocalHash",BadgeTemplateLibrary.fingerprint(file));
                p.pages.get(0).objects.get(0).text="Saved edit";
                var sub=new Publication.RisoPage();sub.name="Black";sub.objectIds.add(p.pages.get(0).objects.get(0).id);p.pages.get(0).risoPages.add(sub);p.version=2;
                Object key=panel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(KeyStroke.getKeyStroke("control S"));
                assertNotNull(key);panel.getActionMap().get(key).actionPerformed(null);
            }catch(Exception ex){throw new RuntimeException(ex);}});
            long deadline=System.nanoTime()+10_000_000_000L;boolean[] busy={true};
            while(busy[0]&&System.nanoTime()<deadline){Thread.sleep(30);SwingUtilities.invokeAndWait(()->{try{busy[0]=(boolean)get(holder[0],"working");}catch(Exception ex){throw new RuntimeException(ex);}});}
            assertFalse(busy[0]);Publication saved=PublicationFiles.read(Files.readAllBytes(file));
            assertEquals("Saved edit",saved.pages.get(0).objects.get(0).text);
            assertEquals("Black",saved.pages.get(0).risoPages.get(0).name);
            SwingUtilities.invokeAndWait(()->{try{assertFalse((boolean)get(holder[0],"dirty"));((Timer)get(holder[0],"refresh")).stop();((Timer)get(holder[0],"thumbnailRefresh")).stop();}catch(Exception ex){throw new RuntimeException(ex);}});
        }
    }

    @Test void changedOrDeletedSourceIsNotOverwritten()throws Exception{
        Path file=folder.resolve("template.sspub");Publication p=BusinessCardsPanel.builtin();
        Files.write(file,PublicationFiles.archive(p));String original=BadgeTemplateLibrary.fingerprint(file);
        p.title="Other editor";Files.write(file,PublicationFiles.archive(p));byte[] newer=Files.readAllBytes(file);
        assertThrows(java.io.IOException.class,()->BusinessCardsPanel.writeTemplate(file,BusinessCardsPanel.builtin(),original));
        assertArrayEquals(newer,Files.readAllBytes(file));Files.delete(file);
        assertThrows(java.io.IOException.class,()->BusinessCardsPanel.writeTemplate(file,p,original));assertFalse(Files.exists(file));
    }
    private static Object get(Object object,String name)throws Exception{Field f=BusinessCardsPanel.class.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static void set(Object object,String name,Object value)throws Exception{Field f=BusinessCardsPanel.class.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
}
