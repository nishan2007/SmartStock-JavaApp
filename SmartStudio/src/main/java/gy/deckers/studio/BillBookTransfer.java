package gy.deckers.studio;

import publishing.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Resumable local transfer. Never replaces an existing editable design. */
public final class BillBookTransfer {
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("Usage: BillBookTransfer source-folder destination-folder evidence-folder");
        Path source=Path.of(args[0]).toAbsolutePath().normalize(),destination=Path.of(args[1]).toAbsolutePath().normalize(),evidence=Path.of(args[2]).toAbsolutePath().normalize();
        if(destination.startsWith(source)||evidence.startsWith(source))throw new IllegalArgumentException("Keep transferred files and evidence outside the source folder.");
        Files.createDirectories(destination);Files.createDirectories(evidence);
        System.setProperty("smartstudio.import.evidence",evidence.toString());
        Path converter=PublisherConverter.find();if(converter==null)throw new IllegalStateException("LibreOffice importer is required.");
        List<Path> sources;
        try(var paths=Files.walk(source)){sources=paths.filter(Files::isRegularFile).filter(p->!BillBookDocuments.numberingName(source.relativize(p).toString())).filter(p->p.toString().toLowerCase(Locale.ROOT).matches(".*\\.(pub|pptx|pptm|odg)$")).sorted().toList();}
        Path report=evidence.resolve("transfer.tsv");
        if(!Files.exists(report))Files.writeString(report,"Source\tSource SHA-256\tArchive\tArchive SHA-256\tPages\tEditable text fields\tMissing fonts\tStatus\n");
        int failed=0,done=0;
        for(Path file:sources){
            Path relative=source.relativize(file),target=destination.resolve(relative.toString()+".sspub");
            String sourceHash=hash(Files.readAllBytes(file));
            try{
                String status="Transferred; visual review pending";
                Publication p;
                Path receipt=target.resolveSibling(target.getFileName()+".source-sha256");
                if(Files.exists(target)){
                    if(!Files.exists(receipt)||!Files.readString(receipt).equals(sourceHash))throw new java.io.IOException("Existing design retained; source changed or receipt missing");
                    p=PublicationFiles.read(Files.readAllBytes(target));status="Existing design retained; visual review pending";
                }else{
                    p=EditablePublicationImport.read(file,converter);
                    if(!sourceHash.equals(hash(Files.readAllBytes(file))))throw new java.io.IOException("Source changed during conversion");
                    p.mergeRows.clear();p.orderId=null;p.lineId=null;p.template=true;
                    p.print=BillBookDocuments.create().print;
                    // Keep imported physical geometry; use a full-size sheet for large originals.
                    if(Math.min(p.width,p.height)>215.9||Math.max(p.width,p.height)>279.4){p.print.width=p.width;p.print.height=p.height;}
                    byte[] archive=PublicationFiles.archive(p);Publication reopened=PublicationFiles.read(archive);
                    if(reopened.pages.size()!=p.pages.size()||!reopened.assetIds().equals(p.assetIds()))throw new java.io.IOException("Archive round-trip changed pages or assets");
                    Files.createDirectories(target.getParent());
                    Files.write(target,archive,StandardOpenOption.CREATE_NEW);
                    Files.writeString(receipt,sourceHash,StandardOpenOption.CREATE_NEW);
                    p=reopened;
                }
                p.validate();long text=p.pages.stream().flatMap(pg->pg.objects.stream()).filter(e->e.type.equals("TEXT")).count();
                if(text==0)status="REVIEW: no separately editable text";
                String fonts=PublicationTemplateLibrary.missingFonts(p);
                if(!fonts.isBlank())status="REVIEW: missing fonts; "+status;
                append(report,List.of(relative.toString(),sourceHash,target.toString(),hash(Files.readAllBytes(target)),""+p.pages.size(),""+text,fonts,status));
                System.out.println(++done+"/"+sources.size()+" "+relative+" | "+status);
            }catch(Exception ex){failed++;append(report,List.of(relative.toString(),sourceHash,target.toString(),"","","","","FAILED: "+ex.getMessage()));System.out.println("FAILED "+relative+": "+ex.getMessage());}
        }
        System.out.println("Complete: "+done+" transferred/retained, "+failed+" failed, "+sources.size()+" source templates.");
        if(failed>0)System.exit(2);
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static void append(Path file,List<String> cells)throws Exception{Files.writeString(file,String.join("\t",cells.stream().map(s->s.replace('\t',' ').replace('\r',' ').replace('\n',' ')).toList())+"\n",StandardCharsets.UTF_8,StandardOpenOption.APPEND);}
}
