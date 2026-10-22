package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class IndependentPhotoFieldsTest {
 @Test void slotsRenderIndependentlyAndPersonalImagesAreNeverSerialized()throws Exception{
  BadgeTemplate template=new BadgeTemplate();template.widthMm=80;template.heightMm=50;
  for(int i=0;i<2;i++){var e=new BadgeTemplate.Element();e.label="Photo "+i;e.type="PHOTO";e.x=5+i*35;e.y=5;e.width=25;e.height=30;e.personalPhoto=solid(i==0?Color.RED:Color.BLUE);template.elements.add(e);}
  BufferedImage image=template.render(Map.of(),null,254);
  assertEquals(Color.RED.getRGB(),image.getRGB(100,100));assertEquals(Color.BLUE.getRGB(),image.getRGB(450,100));
  template.elements.get(0).personalPhoto=solid(Color.GREEN);image=template.render(Map.of(),null,254);
  assertEquals(Color.GREEN.getRGB(),image.getRGB(100,100));assertEquals(Color.BLUE.getRGB(),image.getRGB(450,100));
  String json=new com.google.gson.Gson().toJson(template);assertFalse(json.contains("personalPhoto"));
  BadgeTemplate copy=new com.google.gson.Gson().fromJson(json,BadgeTemplate.class);
  assertNull(copy.elements.get(0).personalPhoto);assertNull(copy.elements.get(1).personalPhoto);
  assertEquals(template.elements.get(0).photoId,copy.elements.get(0).photoId);
 }
 private static BufferedImage solid(Color color){BufferedImage image=new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(color);g.fillRect(0,0,10,10);g.dispose();return image;}
}
