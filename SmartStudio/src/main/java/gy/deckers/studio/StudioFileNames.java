package gy.deckers.studio;
import java.nio.file.*;
import java.io.IOException;
import java.util.Locale;
final class StudioFileNames {
 static String clean(String name,String extension){
  String value=name==null?"":name.strip();if(value.toLowerCase(Locale.ROOT).endsWith(extension))value=value.substring(0,value.length()-extension.length());
  if(value.isBlank()||value.length()>120||value.matches(".*[<>:\"/\\\\|?*\\p{Cntrl}].*")||value.endsWith(".")||value.endsWith(" ")||value.equals("..")||value.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))throw new IllegalArgumentException("Use a name of 1–120 characters without path characters or reserved Windows names.");return value;
 }
 static Path target(Path folder,String name,String extension){Path base=folder.toAbsolutePath().normalize(),target=base.resolve(clean(name,extension)+extension).normalize();if(!target.getParent().equals(base))throw new IllegalArgumentException("Choose a file name, not a path.");return target;}
 @FunctionalInterface interface ContentWriter {void write(Path target)throws Exception;}
 static Path renameWithContent(Path source,String name,String extension,String expected,ContentWriter writer)throws Exception{
  if(source==null)throw new IOException("Save this draft with Save As before renaming it.");
  Path destination=target(source.toAbsolutePath().getParent(),name,extension);
  return StudioDocumentSave.lockedRename(source,destination,()->renameContentLocked(source,name,extension,expected,writer));
 }
 private static Path renameContentLocked(Path source,String name,String extension,String expected,ContentWriter writer)throws Exception{
  Path target=checkedTarget(source,name,extension,expected);
  Path staged=Files.createTempFile(target.getParent(),".rename-",".tmp");
  try{
   writer.write(staged);
   Path renamed=checkedTarget(source,name,extension,expected);
   if(!source.toAbsolutePath().normalize().equals(renamed))Files.move(source,renamed);
   try{
    try{Files.move(staged,renamed,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
    catch(AtomicMoveNotSupportedException unsupported){Files.move(staged,renamed,StandardCopyOption.REPLACE_EXISTING);}
   }catch(Exception failure){
    if(!source.toAbsolutePath().normalize().equals(renamed))try{Files.move(renamed,source);}catch(Exception restore){failure.addSuppressed(restore);}
    throw failure;
   }
   return renamed;
  }finally{Files.deleteIfExists(staged);}
 }
 private static Path checkedTarget(Path source,String name,String extension,String expected)throws Exception{
  if(source==null||!Files.isRegularFile(source))throw new IOException("Save this draft with Save As before renaming it.");
  Path target=target(source.toAbsolutePath().getParent(),name,extension);
  if(expected==null||!expected.equals(BadgeTemplateLibrary.fingerprint(source)))throw new IOException("This file changed on disk. Save As a new copy, then reopen it before renaming.");
  if(Files.exists(target)&&!Files.isSameFile(source,target))throw new FileAlreadyExistsException("A file with that name already exists. Choose a different name.");
  return target;
 }
 static Path rename(Path source,String name,String extension,String expected)throws Exception{
  if(source==null)throw new IOException("Save this draft with Save As before renaming it.");
  Path destination=target(source.toAbsolutePath().getParent(),name,extension);
  return StudioDocumentSave.lockedRename(source,destination,()->{
   Path target=checkedTarget(source,name,extension,expected);
   if(!source.toAbsolutePath().normalize().equals(target))Files.move(source,target);return target;
  });
 }
}
