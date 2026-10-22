package publishing;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SheetSequenceTest {
  private Publication document(int records){
    var publication=new Publication();publication.width=40;publication.height=30;
    var settings=publication.print;settings.width=80;settings.height=90;
    settings.left=settings.right=settings.top=settings.bottom=settings.gapX=settings.gapY=0;settings.rotate=false;
    var text=new Publication.Element();text.text="{{number}}";text.width=30;text.height=15;publication.pages.get(0).objects.add(text);
    for(int number=1;number<=records;number++)publication.mergeRows.add(Map.of("number",String.valueOf(number)));
    return publication;
  }
  @Test void requestedRowsColumnsAndCustomOrder(){
    var publication=document(6);var settings=publication.print;
    var canonical=SheetLayout.pack(40,30,settings).placements();
    for(String order:SheetSequence.ORDERS){
      if(order.equals("CUSTOM"))continue;
      var positions=SheetSequence.arrange(canonical,order,"");
      assertEquals(6,new HashSet<>(positions).size());
    }
    assertEquals(List.of(1,0,3,2,5,4),SheetSequence.arrange(canonical,"ROWS_RIGHT_TOP","").stream().map(canonical::indexOf).toList());
    assertEquals(List.of(1,3,5,0,2,4),SheetSequence.arrange(canonical,"COLUMNS_TOP_RIGHT","").stream().map(canonical::indexOf).toList());
    assertEquals(List.of(5,0,2,1,4,3),SheetSequence.arrange(canonical,"CUSTOM","6,1,3,2,5,4").stream().map(canonical::indexOf).toList());
    for(String invalid:List.of("1,2","1,2,3,4,5,5","0,1,2,3,4,5","1,2,3,4,5,no"))
      assertThrows(IllegalArgumentException.class,()->SheetSequence.arrange(canonical,"CUSTOM",invalid));
  }
  @Test void rendererUsesRightFirstRowsAndColumns(){
    var publication=document(6);publication.print.positionOrder="ROWS_RIGHT_TOP";
    var renderer=new PublicationRenderer(publication);var rows=renderer.raster(0,254);
    assertSlot(publication,rows,400,0,"1");assertSlot(publication,rows,0,0,"2");assertSlot(publication,rows,400,300,"3");
    publication.print.positionOrder="COLUMNS_TOP_RIGHT";var columns=renderer.raster(0,254);
    assertSlot(publication,columns,400,0,"1");assertSlot(publication,columns,400,300,"2");assertSlot(publication,columns,400,600,"3");assertSlot(publication,columns,0,0,"4");
  }
  @Test void thousandNumbersProduceTwoConsecutiveFiveHundredSets(){
    var publication=document(1000);publication.print.height=30;publication.print.positionOrder="COLUMNS_TOP_RIGHT";publication.print.cutAndStack=true;
    var renderer=new PublicationRenderer(publication);assertEquals(500,renderer.sheets());
    var first=renderer.raster(0,254);assertSlot(publication,first,400,0,"1");assertSlot(publication,first,0,0,"501");
    var last=renderer.raster(499,254);assertSlot(publication,last,400,0,"500");assertSlot(publication,last,0,0,"1000");
    var saved=Publication.JSON.fromJson(Publication.JSON.toJson(publication),Publication.class);
    assertTrue(saved.print.cutAndStack);assertEquals("COLUMNS_TOP_RIGHT",saved.print.positionOrder);
  }
  @Test void incompleteStackLeavesBlankAndDuplexKeepsMatchingRecords(){
    var publication=document(5);publication.print.height=30;publication.print.positionOrder="ROWS_RIGHT_TOP";publication.print.cutAndStack=true;
    var renderer=new PublicationRenderer(publication);assertEquals(3,renderer.sheets());
    var last=renderer.raster(2,254);assertSlot(publication,last,400,0,"3");assertSlot(publication,last,0,0,"");
    var back=Publication.JSON.fromJson(Publication.JSON.toJson(publication.pages.get(0)),Publication.Page.class);publication.pages.add(back);publication.print.duplex=true;
    assertEquals(6,renderer.sheets());var image=renderer.raster(1,254);
    assertSlot(publication,image,0,0,"1");assertSlot(publication,image,400,0,"4");
    publication.print.mixPages=true;assertThrows(IllegalArgumentException.class,()->renderer.sheets());
  }
  private void assertSlot(Publication publication,BufferedImage sheet,int x,int y,String number){
    var expected=new BufferedImage(400,300,BufferedImage.TYPE_INT_RGB);var graphics=expected.createGraphics();
    try{graphics.scale(10,10);new PublicationRenderer(publication).page(graphics,0,Map.of("number",number));}finally{graphics.dispose();}
    assertArrayEquals(expected.getRGB(0,0,400,300,null,0,400),sheet.getRGB(x,y,400,300,null,0,400));
  }
}
