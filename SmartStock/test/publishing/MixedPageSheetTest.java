package publishing;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MixedPageSheetTest {
    private Publication cards(){
        Publication p=new Publication();p.width=88.9;p.height=50.8;
        p.print.width=215.9;p.print.height=279.4;p.print.left=p.print.right=p.print.top=p.print.bottom=6.35;
        p.print.gapX=p.print.gapY=0;p.print.quantity=1;p.pages.clear();
        for(String color:List.of("#ff0000","#0000ff")){
            Publication.Page page=new Publication.Page();Publication.Element e=new Publication.Element();
            e.type="RECT";e.fill=color;e.x=e.y=0;e.width=p.width;e.height=p.height;page.objects.add(e);p.pages.add(page);
        }
        return p;
    }
    @Test void mixedPagesUseSuccessiveSlotsAndKeepSeparateMode(){
        Publication p=cards();assertEquals(2,new PublicationRenderer(p).sheets());
        p.print.mixPages=true;PublicationRenderer renderer=new PublicationRenderer(p);assertEquals(1,renderer.sheets());
        var image=renderer.raster(0,254);var places=SheetLayout.pack(p.width,p.height,p.print).placements();
        assertEquals(0xff0000,image.getRGB((int)((places.get(0).x()+5)*10),(int)((places.get(0).y()+5)*10))&0xffffff);
        assertEquals(0x0000ff,image.getRGB((int)((places.get(1).x()+5)*10),(int)((places.get(1).y()+5)*10))&0xffffff);
        assertEquals(0xffffff,image.getRGB((int)((places.get(2).x()+5)*10),(int)((places.get(2).y()+5)*10))&0xffffff);
    }
    @Test void mixedMergeCountsAllPagesAcrossSheetBoundariesAndPersists()throws Exception{
        Publication p=cards();p.mergeRows=List.of(Map.of("page1_number","101","page2_number","501","quantity","13"));
        assertEquals(4,new PublicationRenderer(p).sheets());p.print.mixPages=true;
        assertEquals(3,new PublicationRenderer(p).sheets());
        assertTrue(PublicationFiles.read(PublicationFiles.archive(p)).print.mixPages);
        p.print.duplex=true;assertThrows(IllegalArgumentException.class,()->new PublicationRenderer(p).sheets());
    }
    @Test void eachPageUsesItsColumnAndKeepsTheRecordAcrossSlots(){
        Publication p=cards();p.print.width=177.8;p.print.height=50.8;
        p.print.left=p.print.right=p.print.top=p.print.bottom=0;p.print.rotate=false;p.print.mixPages=true;
        for(int i=0;i<2;i++){
            Publication.Element text=new Publication.Element();text.text="{{page"+(i+1)+"_number}}";text.width=75;text.height=25;text.fontSize=18;
            p.pages.get(i).objects.add(text);
        }
        p.mergeRows=List.of(Map.of("page1_number","111","page2_number","222","quantity","2"),Map.of("page1_number","333","page2_number","444"));
        PublicationRenderer renderer=new PublicationRenderer(p);assertEquals(3,renderer.sheets());
        var first=renderer.raster(0,254);var repeat=renderer.raster(1,254);var next=renderer.raster(2,254);
        assertArrayEquals(first.getRGB(0,0,first.getWidth(),first.getHeight(),null,0,first.getWidth()),repeat.getRGB(0,0,repeat.getWidth(),repeat.getHeight(),null,0,repeat.getWidth()));
        assertFalse(Arrays.equals(first.getRGB(0,0,first.getWidth(),first.getHeight(),null,0,first.getWidth()),next.getRGB(0,0,next.getWidth(),next.getHeight(),null,0,next.getWidth())));
        for(int i=0;i<2;i++){
            var image=new java.awt.image.BufferedImage(889,508,java.awt.image.BufferedImage.TYPE_INT_RGB);
            var g=image.createGraphics();g.scale(10,10);renderer.page(g,i,p.mergeRows.get(0));g.dispose();
            assertArrayEquals(image.getRGB(0,0,889,508,null,0,889),first.getRGB(i*889,0,889,508,null,0,889));
        }
    }
}
