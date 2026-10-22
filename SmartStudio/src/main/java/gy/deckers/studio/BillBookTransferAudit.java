package gy.deckers.studio;

import publishing.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Checks transferred archives and repairs failed solid-black text exports with backups. */
public final class BillBookTransferAudit {
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Usage: BillBookTransferAudit evidence-folder");
        Path evidence=Path.of(args[0]).toAbsolutePath();
        List<Path> reports;try(var files=Files.walk(evidence)){reports=files.filter(p->p.getFileName().toString().equals("transfer.tsv")).toList();}
        StringBuilder review=new StringBuilder("Archive\tPages\tText fields\tMissing fonts\tBlack exports repaired\tStatus\n");
        int checked=0,repaired=0,changed=0;
        for(Path report:reports){
            Map<Path,String> expected=new LinkedHashMap<>();
            for(String line:Files.readAllLines(report).stream().skip(1).toList()){
                String[] cells=line.split("\t",-1);
                if(cells.length==8&&!cells[3].isBlank())expected.put(Path.of(cells[2]),cells[3]);
            }
            for(var entry:expected.entrySet()){
                Path file=entry.getKey();if(!Files.isRegularFile(file)){review.append(file).append("\t\t\t\t\tRemoved from active library\n");continue;}byte[] bytes=Files.readAllBytes(file);String before=hash(bytes);
                if(!before.equals(entry.getValue())){changed++;review.append(file).append("\t\t\t\t\tSKIPPED: changed after transfer; edits retained\n");continue;}
                Publication p=PublicationFiles.read(bytes);int fixes=0;
                for(var page:p.pages)for(var e:page.objects)if(e.type.equals("TEXT")&&!e.text.isBlank()&&!e.asset.isBlank()){
                    var image=PublicationImport.decodeImage(p.assetBytes(e.asset));
                    if(image!=null&&EditablePublicationImport.solidBlack(image)){e.asset="";fixes++;}
                }
                if(fixes>0){
                    Path backup=report.getParent().resolve("before-black-text-repair").resolve(before+".sspub");
                    Files.createDirectories(backup.getParent());if(!Files.exists(backup))Files.write(backup,bytes,StandardOpenOption.CREATE_NEW);
                    byte[] archive=PublicationFiles.archive(p);PublicationFiles.read(archive).validate();
                    if(!hash(Files.readAllBytes(file)).equals(before))throw new java.io.IOException("Design changed during audit: "+file);
                    PublicationFiles.writeAtomic(file,archive);
                    Path previews=report.getParent().resolve("repaired-previews").resolve(before);Files.createDirectories(previews);
                    for(int i=0;i<p.pages.size();i++)javax.imageio.ImageIO.write(PublicationTemplateLibrary.preview(p,i),"png",previews.resolve("page-"+(i+1)+".png").toFile());
                    // Preserve source receipt and checkpoint the repaired hash for safe subsequent audits.
                    String prior=Files.readAllLines(report).stream().filter(l->l.contains("\t"+file+"\t"+before+"\t")).reduce((a,b)->b).orElseThrow();
                    String[] cells=prior.split("\t",-1);cells[3]=hash(archive);cells[7]="Black text export repaired; visual review pending";
                    Files.writeString(report,String.join("\t",cells)+"\n",StandardOpenOption.APPEND);repaired+=fixes;
                }
                long text=p.pages.stream().flatMap(pg->pg.objects.stream()).filter(e->e.type.equals("TEXT")).count();
                String fonts=PublicationTemplateLibrary.missingFonts(p);
                review.append(file).append('\t').append(p.pages.size()).append('\t').append(text).append('\t').append(fonts).append('\t').append(fixes).append('\t').append(text==0?"REVIEW: no editable text":!fonts.isBlank()?"REVIEW: missing fonts":"Visual review pending").append('\n');checked++;
            }
        }
        Files.writeString(evidence.resolve("archive-review.tsv"),review);
        System.out.println("Checked "+checked+" archives; repaired "+repaired+" black text exports; preserved "+changed+" changed designs. Visual review remains required.");
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
