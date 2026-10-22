package gy.deckers.studio;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class PhotoFramingTest {
 private BufferedImage source(){BufferedImage image=new BufferedImage(200,100,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.RED);g.fillRect(0,0,100,100);g.setColor(Color.BLUE);g.fillRect(100,0,100,100);g.dispose();return image;}
 private BufferedImage paint(PhotoFraming framing){BufferedImage image=new BufferedImage(100,100,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,100,100);framing.paint(g,0,0,100,100);g.dispose();return image;}
 @Test void backgroundAndOutlineDefaultOffAndRespectTransparency(){
  var frame=PhotoFraming.initial(source()).fit(100,100);assertNull(frame.background());assertNull(frame.outline());
  BufferedImage transparent=new BufferedImage(100,100,BufferedImage.TYPE_INT_ARGB);var g=transparent.createGraphics();frame.paint(g,0,0,100,100);g.dispose();assertEquals(0,transparent.getRGB(50,5)>>>24);
  BufferedImage styled=paint(frame.styled(Color.GREEN,Color.MAGENTA));assertEquals(Color.GREEN.getRGB(),styled.getRGB(50,5));assertEquals(Color.MAGENTA.getRGB(),styled.getRGB(0,50));
  var moved=frame.styled(Color.GREEN,Color.MAGENTA).positioned(1,.2,.3,1);assertEquals(Color.GREEN,moved.background());assertEquals(Color.MAGENTA,moved.outline());
 }
 @Test void wholePhotoFitDoesNotCropAndFillUsesTheBox(){
  PhotoFraming frame=PhotoFraming.initial(source()).fit(100,100);BufferedImage fit=paint(frame);
  assertEquals(Color.WHITE.getRGB(),fit.getRGB(50,10));assertEquals(Color.RED.getRGB(),fit.getRGB(10,50));assertEquals(Color.BLUE.getRGB(),fit.getRGB(90,50));
  BufferedImage fill=paint(PhotoFraming.initial(frame.source()));assertNotEquals(Color.WHITE.getRGB(),fill.getRGB(50,10));
  BufferedImage moved=paint(new PhotoFraming(frame.source(),1,.5,0,0));assertEquals(Color.RED.getRGB(),moved.getRGB(75,50));
  PhotoFraming rotated=new PhotoFraming(frame.source(),1,0,0,1);assertEquals(100,rotated.width());assertEquals(200,rotated.height());
 }
 @Test void exportUsesSameFrameWithoutSavingPersonalFraming()throws Exception{
  BadgeTemplate model=new BadgeTemplate();model.widthMm=model.heightMm=50;
  BadgeTemplate.Element e=new BadgeTemplate.Element();e.type="PHOTO";e.label="Person";e.width=e.height=50;e.personalPhoto=source();e.photoFraming=PhotoFraming.initial(e.personalPhoto).fit(50,50);model.elements.add(e);
  BufferedImage result=model.render(Map.of(),null,254);assertEquals(Color.WHITE.getRGB(),result.getRGB(250,20));assertEquals(Color.RED.getRGB(),result.getRGB(25,250));
  String json=new com.google.gson.Gson().toJson(model);assertFalse(json.contains("photoFraming"));assertFalse(json.contains("personalPhoto"));assertEquals(50,e.width);assertEquals(50,e.height);
 }
 @Test void renderFramingPreview()throws Exception{
  BufferedImage source=source();SwingUtilities.invokeAndWait(()->{
   var panel=new PhotoFramingDialog.FramePreview(PhotoFraming.initial(source).fit(18,24),18./24);panel.setSize(580,390);
   BufferedImage output=new BufferedImage(580,390,BufferedImage.TYPE_INT_RGB);var g=output.createGraphics();panel.paint(g);g.dispose();
   assertEquals(18./24,panel.box.width/(double)panel.box.height,.01);
   try{javax.imageio.ImageIO.write(output,"png",Path.of("target","photo-framing-preview.png").toFile());}catch(Exception ex){throw new RuntimeException(ex);}
  });
 }
}
