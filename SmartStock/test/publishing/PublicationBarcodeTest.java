package publishing;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.util.Map;
class PublicationBarcodeTest{
 @Test void generatedBarsDecodeAfterRenderingAndInvalidCheckDigitsStopOutput()throws Exception{
  for(String format:PublicationBarcode.FORMATS){
   var p=new Publication();p.width=format.equals("QR_CODE")?40:80;p.height=40;p.pages.get(0).objects.clear();var e=new Publication.Element();e.type=format.equals("QR_CODE")?"QR":"BARCODE";e.barcodeFormat=format;e.text=switch(format){case "EAN_13"->"5901234123457";case "EAN_8"->"96385074";case "UPC_A"->"036000291452";case "ITF"->"12345678";default->"SMARTSTOCK-123";};e.barcodeText=false;e.x=e.y=0;e.width=p.width;e.height=40;p.pages.get(0).objects.add(e);
   var image=new BufferedImage((int)p.width*12,480,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.scale(12,12);new PublicationRenderer(p).page(g,0,Map.of());g.dispose();
   var decoded=PublicationBarcode.decode(image);assertTrue(decoded.isPresent(),format);assertEquals(e.text,decoded.get().text());
  }
  assertThrows(Exception.class,()->PublicationBarcode.encode("5901234123458","EAN_13"));
  var p=new Publication();var bad=new Publication.Element();bad.type="BARCODE";bad.barcodeFormat="EAN_13";bad.text="invalid";p.pages.get(0).objects.add(bad);assertThrows(IllegalArgumentException.class,()->PublicationPreflight.requireMergeData(p));
 }
}
