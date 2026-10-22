package gy.deckers.studio;
import publishing.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PublicationTemplateLibraryTest{
 @TempDir Path root;
 @Test void localTemplatesRemainVisibleWithOldServerOfflineOrBeforeFirstMigration()throws Exception{
  Path file=root.resolve("BIKA.sspub");Files.write(file,PublicationFiles.archive(BusinessCardsPanel.labelTemplate()));
  var shared=new SharedPublicationLibrary(root.resolve("server-cache"),"LABELS",request->{throw new java.io.IOException("Old server");});
  assertThrows(Exception.class,shared::refresh);
  for(boolean labels:List.of(false,true)){
   var choices=PublicationTemplateLibrary.choices(labels,shared,List.of(file));var local=choices.stream().filter(c->file.equals(c.file())).findFirst().orElseThrow();assertTrue(local.label().contains("Local on this computer"));
  }
  shared.online=true;assertTrue(PublicationTemplateLibrary.choices(true,shared,List.of(file)).stream().anyMatch(c->file.equals(c.file())));
 }
 @Test void discoversNestedTransferredDesignsAndKeepsFilesSeparate()throws Exception{
  Path cards=Files.createDirectories(root.resolve("cards/nested")),labels=Files.createDirectories(root.resolve("labels"));
  Files.write(cards.resolve("Deckers.pub.sspub"),PublicationFiles.archive(BusinessCardsPanel.builtin()));
  Files.write(labels.resolve("Sauce.sspub"),PublicationFiles.archive(BusinessCardsPanel.labelTemplate()));
  Files.writeString(cards.resolve("proof.pdf"),"proof");
  var found=PublicationTemplateLibrary.files(List.of(root.resolve("cards"),root.resolve("cards")));
  assertEquals(1,found.size());assertEquals("Deckers",PublicationTemplateLibrary.name(found.get(0)));
  assertEquals("Sauce",PublicationTemplateLibrary.name(PublicationTemplateLibrary.files(List.of(labels)).get(0)));
 }
 @Test void nativeTextFieldsRemainSeparateAndEditableAfterSaving()throws Exception{
  Files.writeString(root.resolve("layers.json"),"{\"pages\":[{\"width\":90,\"height\":50,\"objects\":[]}]}");
  var a=new PublisherDrawingText.Field(0,1,"Company","Arial",12,true,false,0,0,-40,-20,60,7);
  var b=new PublisherDrawingText.Field(0,2,"555-1234","Arial",10,false,false,0,0,-40,-5,40,5);
  var p=EditablePublicationImport.assemble(Path.of("sample.pub"),root,List.of(a,b));
  var reopened=PublicationFiles.read(PublicationFiles.archive(p));assertEquals(2,reopened.pages.get(0).objects.size());
  var first=reopened.pages.get(0).objects.get(0);var second=reopened.pages.get(0).objects.get(1);
  first.text="Changed";first.x+=4;first.font="Serif";reopened.validate();
  assertEquals("555-1234",second.text);assertEquals(5,second.x,.001);assertTrue(first.fitText);assertFalse(first.locked);
  assertNotNull(PublicationTemplateLibrary.preview(reopened,0));
 }
 @Test void importedMergedCellsKeepSeparateContentAndPosition()throws Exception{
  var image=new java.awt.image.BufferedImage(100,60,java.awt.image.BufferedImage.TYPE_INT_RGB);javax.imageio.ImageIO.write(image,"png",root.resolve("table.png").toFile());
  Files.writeString(root.resolve("layers.json"),"""
   {"pages":[{"width":90,"height":50,"objects":[{"x":5,"y":5,"width":60,"height":30,"image":"table.png","cells":[
   {"row":0,"column":0,"rowSpan":1,"columnSpan":2,"text":"Heading","left":0,"top":0,"width":1,"height":0.5,"font":"Arial","fontSize":12,"bold":true,"color":0,"fill":16777215},
   {"row":1,"column":0,"rowSpan":1,"columnSpan":1,"text":"One","left":0,"top":0.5,"width":0.5,"height":0.5,"font":"Arial","fontSize":10,"bold":false,"color":0,"fill":16777215},
   {"row":1,"column":1,"rowSpan":1,"columnSpan":1,"text":"Two","left":0.5,"top":0.5,"width":0.5,"height":0.5,"font":"Arial","fontSize":10,"bold":false,"color":0,"fill":16777215}]}]}]}
   """);
  var p=EditablePublicationImport.assemble(Path.of("table.pptx"),root,List.of());var objects=p.pages.get(0).objects;
  assertEquals(3,objects.size());assertEquals(2,objects.get(0).columnSpan);assertEquals(1,objects.get(2).sourceRow);
  objects.get(1).text="Edited";objects.get(1).x+=10;p.validate();assertEquals("Two",objects.get(2).text);assertEquals(35,objects.get(2).x,.001);
  var reopened=PublicationFiles.read(PublicationFiles.archive(p));assertEquals(3,reopened.pages.get(0).objects.size());assertNotNull(PublicationTemplateLibrary.preview(reopened,0));
 }
}
