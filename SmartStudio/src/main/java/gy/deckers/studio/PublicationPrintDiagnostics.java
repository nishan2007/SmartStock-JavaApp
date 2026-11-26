package gy.deckers.studio;

import publishing.Publication;
import java.awt.Graphics2D;
import java.awt.print.*;
import java.nio.file.*;
import java.io.IOException;
import javax.print.PrintService;
import javax.print.attribute.PrintRequestAttributeSet;

/** Local geometry-only evidence: no artwork, merge records or credentials. */
final class PublicationPrintDiagnostics {
    private final Path file;
    private static volatile Path latest;
    private int callbacks;
    PublicationPrintDiagnostics(Path file){this.file=file;latest=file;}
    Path path(){return file;}
    static String latestReport()throws IOException{
        Path report=latest;
        if(report==null){
            Path directory=Path.of(System.getProperty("user.home"),"print-diagnostics");
            if(Files.isDirectory(directory))try(var files=Files.list(directory)){
                report=files.filter(path->path.getFileName().toString().startsWith("direct-print-")&&path.toString().endsWith(".txt"))
                        .max(java.util.Comparator.comparingLong(path->path.toFile().lastModified())).orElse(null);
            }
        }
        if(report==null||!Files.isRegularFile(report))throw new IOException("Print one sheet first to collect printer details.");
        if(Files.size(report)>131072)throw new IOException("Print details exceed the copy limit: "+report);
        return Files.readString(report);
    }
    static PublicationPrintDiagnostics start(Publication source,PrintService printer,PrintRequestAttributeSet settings,PageFormat page)throws IOException{
        Path directory=Path.of(System.getProperty("user.home"),"print-diagnostics");Files.createDirectories(directory);
        var log=new PublicationPrintDiagnostics(Files.createTempFile(directory,"direct-print-",".txt"));
        log.note("Studio "+StudioUpdates.currentVersion()+"; Java "+System.getProperty("java.version")+"; OS "+System.getProperty("os.name"));
        log.note("Printer: "+(printer==null?"none":printer.getName()));
        log.note("Native print pipeline: "+System.getProperty("sun.java2d.print.pipeline","automatic"));
        log.note("Sheet mm: "+source.print.width+" x "+source.print.height+"; item mm: "+source.width+" x "+source.height);
        for(var attribute:settings.toArray())if(java.util.Set.of("media","orientation-requested","sides","media-printable-area","printer-resolution","copies","number-up").contains(attribute.getName()))log.note(attribute.getName()+"="+attribute);
        log.note("Selected "+geometry(page));return log;
    }
    synchronized void note(String line){try{Files.writeString(file,line+System.lineSeparator(),StandardOpenOption.APPEND);}catch(IOException ignored){}}
    Printable wrap(Printable artwork){return (graphics,page,index)->{
        synchronized(this){if(callbacks++<32){
            note("Callback sheet="+index+" "+geometry(page));
            if(graphics instanceof Graphics2D g)note("Graphics="+g.getClass().getName()+"; transform="+g.getTransform()+"; clip="+(g.getClip()==null?"none":g.getClip().getBounds2D()));
        }}
        return artwork.print(graphics,page,index);
    };}
    private static String geometry(PageFormat page){return "page points="+page.getWidth()+" x "+page.getHeight()+"; orientation="+page.getOrientation()+"; imageable="+page.getImageableX()+","+page.getImageableY()+","+page.getImageableWidth()+","+page.getImageableHeight();}
}
