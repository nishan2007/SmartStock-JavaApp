package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import publishing.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PublisherImportAppearanceTest {
 @Test void brokenMergeFieldBitmapDoesNotCoverRecoveredEditableText()throws Exception{
  var black=new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB);
  javax.imageio.ImageIO.write(black,"png",folder.resolve("field.png").toFile());
  Files.writeString(folder.resolve("layers.json"),"""
   {"pages":[{"width":90,"height":50,"objects":[{"image":"field.png","x":5,"y":5,"width":20,"height":10,"text":"Invoice {{number}}","font":"SansSerif","fontSize":12,"bold":false,"color":0}]}]}
   """);
  var p=EditablePublicationImport.assemble(Path.of("source.pub"),folder,List.of());
  var reopened=PublicationFiles.read(PublicationFiles.archive(p));
  var text=reopened.pages.get(0).objects.get(0);
  assertEquals("TEXT",text.type);assertEquals("Invoice {{number}}",text.text);assertTrue(text.asset.isBlank());
  text.text="Receipt {{number}}";
  assertEquals("Receipt {{number}}",PublicationFiles.read(PublicationFiles.archive(reopened)).pages.get(0).objects.get(0).text);
 }
 @TempDir Path folder;
 @Test void laterBooleanFlagsDoNotEnableAnExplicitlyDisabledOutline(){
  assertEquals(0x280020,PublisherDrawingText.mergeBoolean(0x80000,0x200020));
  assertEquals(0x280028,PublisherDrawingText.mergeBoolean(0x80008,0x200020));
 }
 @Test void replacesCompleteWordArtGroupAtItsOriginalStackingPosition()throws Exception{
  var image=new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_ARGB);
  javax.imageio.ImageIO.write(image,"png",folder.resolve("shape.png").toFile());
  String field="{\"image\":\"shape.png\",\"x\":5,\"y\":5,\"width\":20,\"height\":5,\"sourceBounds\":[{\"x\":5,\"y\":5,\"width\":20,\"height\":5}]}";
  String cover="{\"image\":\"shape.png\",\"x\":0,\"y\":0,\"width\":90,\"height\":50}";
  Files.writeString(folder.resolve("layers.json"),"{\"pages\":[{\"width\":90,\"height\":50,\"objects\":["+field+","+field+","+cover+"]}]}");
  var f=new PublisherDrawingText.Field(0,1,"Covered title","Arial",12,true,false,0,0,-40,-20,20,5);
  var p=EditablePublicationImport.assemble(Path.of("source.pub"),folder,List.of(f));
  assertEquals(2,p.pages.get(0).objects.size());
  assertEquals("Covered title",p.pages.get(0).objects.get(0).text);
  assertEquals("IMAGE",p.pages.get(0).objects.get(1).type);
 }
 @Test void resolvesActualDocumentPaletteAndKeepsEditableCylinder()throws Exception{
  assertEquals(0x317ded,PublisherDrawingText.resolveColor(0x08000002,List.of(0,0xd59b5b,0x317ded)));
  Files.writeString(folder.resolve("layers.json"),"{\"pages\":[{\"width\":90,\"height\":50,\"objects\":[]}]}");
  var f=new PublisherDrawingText.Field(0,1,"","",12,false,false,0xffffff,0,-40,-20,40,35,22,0xffffff,.2);
  var p=EditablePublicationImport.assemble(Path.of("source.pub"),folder,List.of(f));
  var cylinder=PublicationFiles.read(PublicationFiles.archive(p)).pages.get(0).objects.get(0);
  assertEquals("CYLINDER",cylinder.type);assertEquals("#ffffff",cylinder.fill);
  var image=new java.awt.image.BufferedImage(900,500,java.awt.image.BufferedImage.TYPE_INT_RGB);
  var g=image.createGraphics();g.scale(10,10);new PublicationRenderer(p).page(g,0,Map.of());g.dispose();
  assertEquals(java.awt.Color.WHITE.getRGB(),image.getRGB(250,90),"Cylinder top must be filled, not a hole");
 }
 @Test void missingCylinderStaysBehindRecoveredProductTitle()throws Exception{
  var image=new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_ARGB);
  javax.imageio.ImageIO.write(image,"png",folder.resolve("shape.png").toFile());
  Files.writeString(folder.resolve("layers.json"),"{\"pages\":[{\"width\":90,\"height\":100,\"objects\":[{\"image\":\"shape.png\",\"x\":20,\"y\":30,\"width\":50,\"height\":6,\"sourceBounds\":[{\"x\":20,\"y\":30,\"width\":50,\"height\":6}]}]}]}");
  var body=new PublisherDrawingText.Field(0,1,"","",12,false,false,0xffffff,0,-38,-30,76,65,22,0xffffff,.2);
  var title=new PublisherDrawingText.Field(0,2,"SPLIT PEAS","Serif",12,true,false,0,0,-25,-20,50,6);
  var p=EditablePublicationImport.assemble(Path.of("source.pub"),folder,List.of(body,title));
  var restored=PublicationFiles.read(PublicationFiles.archive(p));
  assertEquals("CYLINDER",restored.pages.get(0).objects.get(0).type);
  assertEquals("SPLIT PEAS",restored.pages.get(0).objects.get(1).text);
 }
 @Test void recoversMissingCloudBehindSeparatelyEditableTitle()throws Exception{
  Files.writeString(folder.resolve("layers.json"),"{\"pages\":[{\"width\":90,\"height\":100,\"objects\":[]}]}");
  var cloud=new PublisherDrawingText.Field(0,8,"","",12,false,false,0,0,-25,-30,48,26,106,0,0,1627,27862);
  var title=new PublisherDrawingText.Field(0,9,"SPLIT PEAS\nPOWDER","Serif",12,true,false,0xffffff,0,-18,-24,34,15);
  var p=EditablePublicationImport.assemble(Path.of("source.pub"),folder,List.of(cloud,title));
  assertEquals(2,p.pages.get(0).objects.size());var shape=p.pages.get(0).objects.get(0);var text=p.pages.get(0).objects.get(1);
  assertEquals("IMAGE",shape.type);assertEquals("Publisher thought bubble",shape.text);assertTrue(shape.height>26,"Tail must not be clipped to the cloud body");
  assertEquals("TEXT",text.type);assertEquals("SPLIT PEAS\nPOWDER",text.text);assertEquals("#ffffff",text.color);
  var restored=PublicationFiles.read(PublicationFiles.archive(p));
  var image=PublicationImport.decodeImage(java.util.Base64.getDecoder().decode(restored.assets.get(shape.asset)));
  assertEquals(0,image.getRGB(0,0)>>>24,"Outside geometry stays transparent");
  assertEquals(0xff000000,image.getRGB(image.getWidth()/2,image.getHeight()/3));
  assertEquals(shape.asset,restored.pages.get(0).objects.get(0).asset);
 }
 @Test void missingCylinderDoesNotCoverSealsOrBarcodeArtwork()throws Exception{
  var seal=new java.awt.image.BufferedImage(80,80,java.awt.image.BufferedImage.TYPE_INT_ARGB);
  var g=seal.createGraphics();g.setColor(java.awt.Color.RED);g.fillOval(0,0,80,80);g.dispose();
  javax.imageio.ImageIO.write(seal,"png",folder.resolve("seal.png").toFile());
  var bars=new java.awt.image.BufferedImage(100,40,java.awt.image.BufferedImage.TYPE_INT_ARGB);
  g=bars.createGraphics();g.setColor(java.awt.Color.BLACK);for(int x=0;x<100;x+=4)g.fillRect(x,0,2,40);g.dispose();
  javax.imageio.ImageIO.write(bars,"png",folder.resolve("bars.png").toFile());
  var fragment=new java.awt.image.BufferedImage(100,20,java.awt.image.BufferedImage.TYPE_INT_ARGB);
  g=fragment.createGraphics();g.setColor(java.awt.Color.BLACK);g.fillRect(0,0,100,1);g.dispose();
  javax.imageio.ImageIO.write(fragment,"png",folder.resolve("fragment.png").toFile());
  Files.writeString(folder.resolve("layers.json"),"""
   {"pages":[{"width":90,"height":100,"objects":[
    {"image":"fragment.png","x":10,"y":40,"width":20,"height":5},
    {"image":"seal.png","x":35,"y":40,"width":20,"height":20},
    {"image":"bars.png","x":12,"y":65,"width":65,"height":15}
   ]}]}
   """);
  var body=new PublisherDrawingText.Field(0,1,"","",12,false,false,0xffffff,0,-38,-30,76,65,22,0xffffff,.2);
  var p=EditablePublicationImport.assemble(Path.of("source.pub"),folder,List.of(body));
  assertEquals("Artwork 1",p.pages.get(0).objects.get(0).text,"Broken glyph fragment stays under mask");
  assertEquals("CYLINDER",p.pages.get(0).objects.get(1).type);
  var rendered=new java.awt.image.BufferedImage(900,1000,java.awt.image.BufferedImage.TYPE_INT_RGB);
  g=rendered.createGraphics();g.scale(10,10);new PublicationRenderer(p).page(g,0,Map.of());g.dispose();
  assertEquals(java.awt.Color.RED.getRGB(),rendered.getRGB(450,500),"Seal stays visible");
  assertEquals(java.awt.Color.BLACK.getRGB(),rendered.getRGB(121,700),"Barcode bars stay visible");
  assertEquals(java.awt.Color.WHITE.getRGB(),rendered.getRGB(200,400),"Empty glyph fragment stays hidden");
 }
 @Test void publisherWindowsLineBreaksDoNotCreateBlankLines()throws Exception{
  var method=PublisherDrawingText.class.getDeclaredMethod("string",byte[].class);method.setAccessible(true);
  assertEquals("SPLIT PEAS\nPOWDER",method.invoke(null,(Object)"SPLIT PEAS\r\nPOWDER\0".getBytes(java.nio.charset.StandardCharsets.UTF_16LE)));
 }
 @Test void recognizesClosedFieldFramesButNotEmptyGlyphFragments(){
  var image=new java.awt.image.BufferedImage(200,60,java.awt.image.BufferedImage.TYPE_INT_ARGB);
  var g=image.createGraphics();g.setColor(java.awt.Color.BLACK);g.drawRect(2,2,195,55);g.dispose();
  assertTrue(EditablePublicationImport.rectangularFrame(image));
  image=new java.awt.image.BufferedImage(200,60,java.awt.image.BufferedImage.TYPE_INT_ARGB);
  g=image.createGraphics();g.setColor(java.awt.Color.BLACK);g.drawLine(2,2,197,2);g.dispose();
  assertFalse(EditablePublicationImport.rectangularFrame(image));
 }
}
