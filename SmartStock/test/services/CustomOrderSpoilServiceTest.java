package services;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
class CustomOrderSpoilServiceTest {
 @Test void excludesClosedAndDeliveredOrReturnedLines(){
  assertTrue(CustomOrderSpoilService.eligible("IN_PROGRESS","PENDING","NONE"));
  assertTrue(CustomOrderSpoilService.eligible("READY","PENDING","PARTIAL"));
  for(String status:new String[]{"DELIVERED","CANCELLED"})assertFalse(CustomOrderSpoilService.eligible(status,"PENDING","NONE"));
  assertFalse(CustomOrderSpoilService.eligible("IN_PROGRESS","DELIVERED","NONE"));
  assertFalse(CustomOrderSpoilService.eligible("IN_PROGRESS","PENDING","FULL"));
 }
 @Test void requiresReasonAndBoundedPhoto(){
  assertThrows(IllegalArgumentException.class,()->CustomOrderSpoilService.reason(" "));
  assertThrows(IllegalArgumentException.class,()->CustomOrderSpoilService.reason("x".repeat(2001)));
  assertEquals("Print smudged",CustomOrderSpoilService.reason(" Print smudged "));
  assertThrows(IllegalArgumentException.class,()->new CustomOrderSpoilService.Photo(new byte[0]));
  assertThrows(IllegalArgumentException.class,()->new CustomOrderSpoilService.Photo(new byte[2097153]));
 }
 @Test void validatesActualImageAndOptimizesToJpeg()throws Exception{
  assertThrows(IllegalArgumentException.class,()->MobileItemWebServer.optimizeSpoilPhoto("not an image".getBytes()));
  var source=new BufferedImage(1300,50,BufferedImage.TYPE_INT_RGB);var output=new ByteArrayOutputStream();ImageIO.write(source,"png",output);
  byte[] jpeg=MobileItemWebServer.optimizeSpoilPhoto(output.toByteArray());assertEquals(255,jpeg[0]&255);assertEquals(216,jpeg[1]&255);
  var result=ImageIO.read(new java.io.ByteArrayInputStream(jpeg));assertEquals(1200,result.getWidth());
 }
}
