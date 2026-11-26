package gy.deckers.studio;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;import javax.swing.*;import java.awt.*;import java.nio.file.*;import static org.junit.jupiter.api.Assertions.*;
class StudioFileViewTest {
 @TempDir Path temp;
 @Test void renamePreservesBytesAndRejectsConflictsAndStaleFiles()throws Exception{
  Path source=temp.resolve("old.sspub");Files.writeString(source,"template bytes");String hash=BadgeTemplateLibrary.fingerprint(source);Path renamed=StudioFileNames.rename(source,"New name",".sspub",hash);assertFalse(Files.exists(source));assertEquals("template bytes",Files.readString(renamed));Files.writeString(temp.resolve("exists.sspub"),"other");assertThrows(FileAlreadyExistsException.class,()->StudioFileNames.rename(renamed,"exists",".sspub",hash));Files.writeString(renamed,"newer");assertThrows(java.io.IOException.class,()->StudioFileNames.rename(renamed,"Another",".sspub",hash));
  for(String name:new String[]{"../bad","CON","bad/name","","bad."})assertThrows(IllegalArgumentException.class,()->StudioFileNames.clean(name,".sspub"));
 }
 @Test void badgeSaveAsCreatesIndependentCopyAndRenamePreservesIt()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{
   var editor=new BadgeCardsPanel();editor.saveAsFile("First",temp,false);byte[] first=Files.readAllBytes(temp.resolve("First.sstbadge"));editor.saveAsFile("Second",temp,false);assertArrayEquals(first,Files.readAllBytes(temp.resolve("First.sstbadge")));assertTrue(Files.exists(temp.resolve("Second.sstbadge")));editor.renameFile("Renamed");assertFalse(Files.exists(temp.resolve("Second.sstbadge")));assertTrue(Files.exists(temp.resolve("Renamed.sstbadge")));assertEquals("Renamed",BadgeTemplate.load(temp.resolve("Renamed.sstbadge")).name);
  }catch(Exception ex){throw new RuntimeException(ex);}});
 }
 @Test void publicationSaveAsActivatesCopyAndKeepsSetupAndOriginal()throws Exception{
  var panel=new java.util.concurrent.atomic.AtomicReference<BusinessCardsPanel>();
  SwingUtilities.invokeAndWait(()->{try{var editor=new BusinessCardsPanel(true);panel.set(editor);editor.output().print.left=7;editor.saveAsFile("First publication",temp,false);}catch(Exception ex){throw new RuntimeException(ex);}});
  awaitIdle(panel.get());Path first=temp.resolve("First publication.sspub");assertTrue(Files.isRegularFile(first));byte[] original=Files.readAllBytes(first);
  SwingUtilities.invokeAndWait(()->{try{panel.get().saveAsFile("Second publication",temp,false);}catch(Exception ex){throw new RuntimeException(ex);}});awaitIdle(panel.get());assertArrayEquals(original,Files.readAllBytes(first));
  Path second=temp.resolve("Second publication.sspub");assertEquals("Second publication",publishing.PublicationFiles.read(Files.readAllBytes(second)).title);
  SwingUtilities.invokeAndWait(()->{try{panel.get().renameFile("Renamed publication");}catch(Exception ex){throw new RuntimeException(ex);}});awaitIdle(panel.get());assertFalse(Files.exists(second));assertTrue(Files.exists(temp.resolve("Renamed publication.sspub")));assertEquals("Renamed publication",publishing.PublicationFiles.read(Files.readAllBytes(temp.resolve("Renamed publication.sspub"))).title);
 }
 private static void awaitIdle(BusinessCardsPanel panel)throws Exception{long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);var busy=new java.util.concurrent.atomic.AtomicBoolean(true);while(busy.get()&&System.nanoTime()<until){SwingUtilities.invokeAndWait(()->{try{var f=BusinessCardsPanel.class.getDeclaredField("working");f.setAccessible(true);busy.set(f.getBoolean(panel));}catch(Exception ex){throw new RuntimeException(ex);}});if(busy.get())Thread.sleep(30);}assertFalse(busy.get());}
 @Test void filePrecedesHomeAndHomeStaysDefaultAndBackRestoresCanvas()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{
   for(JComponent editor:new JComponent[]{new BadgeCardsPanel(),new BusinessCardsPanel(),new BusinessCardsPanel(true),new BusinessCardsPanel(false,true),new BusinessCardsPanel(false,false,true)}){
    var shell=find(editor,StudioWorkspaceShell.class);var ribbon=shell.ribbon();var tabs=find(ribbon,JTabbedPane.class);assertEquals("File",tabs.getTitleAt(0));assertEquals("Home",tabs.getTitleAt(tabs.getSelectedIndex()));ribbon.select("File");var view=find(shell,StudioFileView.class);assertNotNull(view);assertTrue(view.isVisible());
    view.show("save-as");editor.setSize(1366,768);layout(editor);var image=new java.awt.image.BufferedImage(1366,768,1);var g=image.createGraphics();editor.printAll(g);g.dispose();if(editor instanceof BadgeCardsPanel)javax.imageio.ImageIO.write(image,"png",Path.of("target/file-view.png").toFile());for(int width:new int[]{1334,1888,878})for(String section:new String[]{"info","save-as","rename","export","print"}){view.show(section);editor.setSize(width,width==878?470:720);layout(editor);var f=StudioFileView.class.getDeclaredField("confirm");f.setAccessible(true);var action=(JButton)f.get(view);Rectangle bounds=SwingUtilities.convertRectangle(action.getParent(),action.getBounds(),view);assertTrue(bounds.x>=0&&bounds.y>=0&&bounds.x+bounds.width<=view.getWidth()&&bounds.y+bounds.height<=view.getHeight(),section+" at "+width+": "+bounds+" in "+view.getSize());}
    ribbon.select("Home");assertFalse(view.isVisible());
   }
  }catch(Exception e){throw new RuntimeException(e);}});
 }
 @Test void renameConfirmsTheNewNameAfterLocalAndSharedCompletion()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{
   for(boolean asynchronous:new boolean[]{false,true}){
    var name=new java.util.concurrent.atomic.AtomicReference<>("Original");var busy=new java.util.concurrent.atomic.AtomicBoolean();var requested=new java.util.concurrent.atomic.AtomicReference<String>();
    StudioFileView.Actions actions=(StudioFileView.Actions)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{StudioFileView.Actions.class},(proxy,method,args)->{
     if(method.getName().equals("info"))return new StudioFileView.Info("Business Cards",name.get(),".sspub","Library","3 x 2",1,false,busy.get(),true,true,null);
     if(method.getName().equals("rename")){requested.set((String)args[0]);if(asynchronous)busy.set(true);else name.set(requested.get());}return null;
    });
    var view=new StudioFileView(actions,()->{});view.show("rename");
    var input=StudioFileView.class.getDeclaredField("filename");input.setAccessible(true);((JTextField)input.get(view)).setText("New name");
    var button=StudioFileView.class.getDeclaredField("confirm");button.setAccessible(true);((JButton)button.get(view)).doClick();
    var message=StudioFileView.class.getDeclaredField("feedback");message.setAccessible(true);
    if(asynchronous){assertEquals("Original",name.get());assertEquals("Renaming template…",((JLabel)message.get(view)).getText());name.set(requested.get());busy.set(false);view.refreshStatus();}
    assertEquals("New name",name.get());assertEquals("New name",((JTextField)input.get(view)).getText());
    assertEquals("Renamed to New name.sspub",((JLabel)message.get(view)).getText());
   }
  }catch(Exception ex){throw new RuntimeException(ex);}});
 }
 @Test void failedRenamePreparationLeavesOriginalAndConflictingFilesUntouched()throws Exception{
  Path source=temp.resolve("Original.sspub");Files.writeString(source,"original");String hash=BadgeTemplateLibrary.fingerprint(source);
  assertThrows(java.io.IOException.class,()->StudioFileNames.renameWithContent(source,"New",".sspub",hash,path->{Files.writeString(path,"partial");throw new java.io.IOException("Disk failure");}));
  assertEquals("original",Files.readString(source));assertFalse(Files.exists(temp.resolve("New.sspub")));
  assertThrows(java.io.IOException.class,()->StudioFileNames.renameWithContent(source,"New",".sspub",hash,path->{Files.writeString(path,"renamed");Files.writeString(source,"external change");}));
  assertEquals("external change",Files.readString(source));assertFalse(Files.exists(temp.resolve("New.sspub")));
 }
 @Test void badgeRenameKeepsUnsavedEditsOutOfTheSavedTemplate()throws Exception{
  SwingUtilities.invokeAndWait(()->{try{
   var panel=new BadgeCardsPanel();panel.saveAsFile("Original badge",temp,false);
   var field=BadgeCardsPanel.class.getDeclaredField("template");field.setAccessible(true);BadgeTemplate draft=(BadgeTemplate)field.get(panel);
   var text=draft.elements.stream().filter(e->e.type.equals("TEXT")).findFirst().orElseThrow();String savedText=text.text;text.text="Unsaved draft text";
   panel.renameFile("Renamed badge");var saved=BadgeTemplate.load(temp.resolve("Renamed badge.sstbadge"));
   assertEquals("Renamed badge",saved.name);assertEquals("Renamed badge",draft.name);assertEquals("Unsaved draft text",text.text);
   assertEquals(savedText,saved.elements.stream().filter(e->e.type.equals("TEXT")).findFirst().orElseThrow().text);
   var dirty=BadgeCardsPanel.class.getDeclaredField("dirty");dirty.setAccessible(true);assertTrue(dirty.getBoolean(panel));
  }catch(Exception ex){throw new RuntimeException(ex);}});
 }
 @Test void sharedBadgeRenameKeepsDraftEditsUnsavedAndUsesExistingTemplateId()throws Exception{
  Path cache=temp.resolve("shared");Files.createDirectories(cache);String id=java.util.UUID.randomUUID().toString();Path file=cache.resolve(id+".sstbadge");
  BadgeTemplate saved=BadgeTemplate.builtin();saved.name="Original shared";saved.save(file);String originalText=saved.elements.stream().filter(e->e.type.equals("TEXT")).findFirst().orElseThrow().text;
  String hash=BadgeTemplateLibrary.fingerprint(file);
  var meta=new com.google.gson.JsonObject();meta.addProperty("id",id);meta.addProperty("name",saved.name);meta.addProperty("sha256",hash);Files.writeString(file.resolveSibling(file.getFileName()+".meta.json"),meta.toString());
  var library=new SharedBadgeLibrary(cache,request->{
   assertEquals("SAVE",request.get("action").getAsString());assertEquals(id,request.get("id").getAsString());assertEquals(hash,request.get("expected").getAsString());
   String content=request.get("content").getAsString();var result=meta.deepCopy();result.addProperty("name",request.get("name").getAsString());
   result.addProperty("sha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content.getBytes(java.nio.charset.StandardCharsets.UTF_8))));return result;
  });library.online=true;
  var editor=new java.util.concurrent.atomic.AtomicReference<BadgeCardsPanel>();
  SwingUtilities.invokeAndWait(()->{try{
   var panel=new BadgeCardsPanel();editor.set(panel);
   for(var entry:java.util.Map.of("shared",(Object)library,"currentFile",file,"currentFingerprint",hash,"template",saved).entrySet()){var field=BadgeCardsPanel.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(panel,entry.getValue());}
   saved.elements.stream().filter(e->e.type.equals("TEXT")).findFirst().orElseThrow().text="Unsaved shared draft";panel.renameFile("Renamed shared");
  }catch(Exception ex){throw new RuntimeException(ex);}});
  var busy=new java.util.concurrent.atomic.AtomicBoolean(true);long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
  while(busy.get()&&System.nanoTime()<deadline){SwingUtilities.invokeAndWait(()->{try{var field=BadgeCardsPanel.class.getDeclaredField("sharedSave");field.setAccessible(true);busy.set(field.getBoolean(editor.get()));}catch(Exception ex){throw new RuntimeException(ex);}});if(busy.get())Thread.sleep(20);}
  assertFalse(busy.get());assertEquals("Renamed shared",BadgeTemplate.load(file).name);
  assertEquals(originalText,BadgeTemplate.load(file).elements.stream().filter(e->e.type.equals("TEXT")).findFirst().orElseThrow().text);
  assertEquals("Unsaved shared draft",saved.elements.stream().filter(e->e.type.equals("TEXT")).findFirst().orElseThrow().text);
  SwingUtilities.invokeAndWait(()->{try{library.online=false;assertThrows(java.io.IOException.class,()->editor.get().renameFile("Offline rename"));assertEquals("Renamed shared",saved.name);}catch(Exception ex){throw new RuntimeException(ex);}});
 }
 private static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
 private static <T>T find(Container c,Class<T> type){for(Component child:c.getComponents()){if(type.isInstance(child))return type.cast(child);if(child instanceof Container n){T r=find(n,type);if(r!=null)return r;}}return null;}
}
