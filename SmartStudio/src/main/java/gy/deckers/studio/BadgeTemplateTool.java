package gy.deckers.studio;

import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.Map;

/** Local conversion assistant entry point: no store login, database or cloud credentials. */
public final class BadgeTemplateTool {
    public static void main(String[] args) {
        try { run(args); }
        catch(Exception ex){System.err.println(ex.getMessage());System.exit(1);}
    }
    static void run(String[] args)throws Exception {
        if(args.length==2&&args[0].equals("validate")) {
            BadgeTemplate t=BadgeTemplate.load(Path.of(args[1]));
            t.render(Map.of(),null,300);
            System.out.println("VALID: "+t.name+" • "+t.widthMm+" × "+t.heightMm+" mm • "+t.elements.size()+" layers");
        } else if(args.length==3&&args[0].equals("preview")) {
            BadgeTemplate t=BadgeTemplate.load(Path.of(args[1]));Path out=Path.of(args[2]).toAbsolutePath();
            if(Files.exists(out))throw new java.io.IOException("Preview already exists. Choose a new output name.");
            Files.createDirectories(out.getParent());ImageIO.write(t.render(Map.of(),null,300),"png",out.toFile());
            System.out.println("Preview written: "+out);
        } else if(args.length==2&&args[0].equals("check-library")) {
            var files=new BadgeTemplateLibrary(Path.of(args[1])).templates();int invalid=0;
            for(Path file:files){try{BadgeTemplate t=BadgeTemplate.load(file);t.render(Map.of(),null,300);System.out.println("VALID\t"+file.getFileName());}catch(Exception ex){invalid++;System.out.println("INVALID\t"+file.getFileName()+"\t"+ex.getMessage());}}
            System.out.println(files.size()+" template(s), "+invalid+" invalid. Visual review is still required.");
            if(invalid>0||files.isEmpty())throw new java.io.IOException("Library check incomplete: invalid or missing templates.");
        } else throw new IllegalArgumentException("Usage: validate <template.sstbadge> | preview <template.sstbadge> <new-preview.png> | check-library <folder>");
    }
}
