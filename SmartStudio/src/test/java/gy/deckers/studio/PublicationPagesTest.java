package gy.deckers.studio;
import publishing.*;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class PublicationPagesTest {
 @Test void showsAllPagesSelectsWithoutRecursionAndHidesForSinglePage()throws Exception{
  SwingUtilities.invokeAndWait(()->{
   AtomicInteger selected=new AtomicInteger(-1);var strip=new PublicationPages(selected::set);Publication p=new Publication();for(int i=1;i<120;i++){var page=new Publication.Page();page.name="Page "+(i+1);p.pages.add(page);}
   strip.update(p,0);assertTrue(strip.isVisible());assertEquals(120,strip.list.getModel().getSize());assertEquals(-1,selected.get());strip.list.setSelectedIndex(119);assertEquals(119,selected.get());strip.select(3);assertEquals(3,strip.list.getSelectedIndex());assertEquals(119,selected.get());strip.update(new Publication(),0);assertFalse(strip.isVisible());assertEquals(1,strip.list.getModel().getSize());
  });
 }
 @Test void linkedChildrenNavigateBySourcePageWithoutChangingDocumentPageCount()throws Exception{
  SwingUtilities.invokeAndWait(()->{var p=new Publication();var object=new Publication.Element();p.pages.get(0).objects.add(object);var sub=LinkedRiso.add(p,0);sub.name="Green";p.pages.add(new Publication.Page());var strip=new PublicationPages(i->{});var selected=new java.util.concurrent.atomic.AtomicReference<String>();strip.setRisoSelection((page,id)->selected.set(page+":"+id));strip.update(p,0);assertEquals(2,strip.list.getModel().getSize());assertEquals(2,strip.subPageList().getModel().getSize());strip.subPageList().setSelectedIndex(0);assertEquals("0:"+sub.id,selected.get());strip.select(1);assertEquals(1,strip.list.getSelectedIndex());assertEquals(0,strip.subPageList().getModel().getSize());strip.select(0);assertEquals(2,strip.subPageList().getModel().getSize());assertEquals(2,p.pages.size());});
 }
}
