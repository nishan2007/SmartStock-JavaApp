package gy.deckers.studio;
import org.junit.jupiter.api.Test;import publishing.*;import java.util.*;import java.awt.*;import java.awt.image.*;import static org.junit.jupiter.api.Assertions.*;
class BarcodeCaptionTest{
 @Test void hiddenCaptionKeepsEncodedValueAndUsesFullBarHeightAfterSaving()throws Exception{
  var p=new Publication();p.width=60;p.height=30;p.pages.get(0).objects.clear();var e=new Publication.Element();e.type="BARCODE";e.text="123456789";e.width=60;e.height=30;e.x=e.y=0;e.barcodeText=false;p.pages.get(0).objects.add(e);
  var restored=PublicationFiles.read(PublicationFiles.archive(p));var code=restored.pages.get(0).objects.get(0);assertFalse(code.barcodeText);assertEquals("123456789",code.text);
  BufferedImage hidden=render(restored);code.barcodeText=true;BufferedImage shown=render(restored);
  int barColumn=-1;for(int x=0;x<600;x++)if((hidden.getRGB(x,20)&0xffffff)==0){barColumn=x;break;}
  assertTrue(barColumn>=0);assertEquals(0,hidden.getRGB(barColumn,290)&0xffffff,"Caption-free barcode must extend bars to bottom");
  assertEquals(0xffffff,shown.getRGB(barColumn,299)&0xffffff,"Caption mode leaves caption space below bars");
 }
 private BufferedImage render(Publication p){var image=new BufferedImage(600,300,1);var g=image.createGraphics();try{g.scale(10,10);new PublicationRenderer(p).page(g,0,Map.of());}finally{g.dispose();}return image;}
}
