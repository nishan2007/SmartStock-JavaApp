package gy.deckers.studio;

import publishing.*;
import java.nio.file.*;
import java.util.*;
import java.security.MessageDigest;

/** Reviewable local cleanup: removed variants and prior archives are retained outside the picker. */
public final class BillBookLibraryCleanup {
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("Usage: BillBookLibraryCleanup library-root evidence-root original-source-root");
        Path library=Path.of(args[0]).toRealPath(),evidence=Path.of(args[1]).toAbsolutePath().normalize(),source=Path.of(args[2]).toRealPath();
        Path output=evidence.resolve("library-cleanup-"+java.time.LocalDateTime.now().toString().replace(':','-')).normalize();
        if(output.startsWith(library))throw new IllegalArgumentException("Backups must be outside the active library.");
        Files.createDirectories(output);
        Map<Path,String> expected=new HashMap<>();Map<Path,String[]> checkpoint=new HashMap<>();Map<Path,Path> reports=new HashMap<>(),references=new HashMap<>();
        try(var paths=Files.walk(evidence)){
            for(Path report:paths.filter(p->p.getFileName().toString().equals("transfer.tsv")).toList())
                for(String line:Files.readAllLines(report).stream().skip(1).toList()){
                    String[] row=line.split("\t",-1);if(row.length!=8||row[3].isBlank())continue;
                    Path file=Path.of(row[2]).toAbsolutePath().normalize();expected.put(file,row[3]);checkpoint.put(file,row);reports.put(file,report);
                }
        }
        try(var paths=Files.walk(evidence)){
            for(Path file:paths.filter(p->p.getFileName().toString().equals("source.txt")).toList()){
                Path original=Path.of(Files.readString(file).strip()).toAbsolutePath().normalize(),relative=null;
                if(original.startsWith(source))relative=source.relativize(original);
                else if(original.startsWith(evidence.resolve("Original sources")))relative=evidence.resolve("Original sources").relativize(original);
                if(relative!=null)references.put(library.resolve(relative+".sspub"),file.resolveSibling("source-reference.pdf"));
            }
        }
        List<Path> archives;try(var paths=Files.walk(library)){archives=paths.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".sspub")).sorted().toList();}
        Map<String,Path> referenceBySourceHash=new HashMap<>();
        for(var entry:references.entrySet()){
            Path receipt=entry.getKey().resolveSibling(entry.getKey().getFileName()+".source-sha256");
            String sourceHash=Files.isRegularFile(receipt)?Files.readString(receipt).strip():checkpoint.containsKey(entry.getKey())?checkpoint.get(entry.getKey())[1]:null;
            if(sourceHash!=null&&Files.isRegularFile(entry.getValue()))referenceBySourceHash.put(sourceHash,entry.getValue());
        }
        for(Path file:archives)if(!references.containsKey(file)){
            Path receipt=file.resolveSibling(file.getFileName()+".source-sha256");
            if(Files.isRegularFile(receipt)){
                Path reference=referenceBySourceHash.get(Files.readString(receipt).strip());if(reference!=null)references.put(file,reference);
            }
        }
        StringBuilder manifest=new StringBuilder("Archive\tBefore SHA-256\tAfter SHA-256\tAction\n");int removed=0,fixed=0,objects=0,skipped=0;
        for(Path file:archives){
            Path real=file.toRealPath();if(!real.startsWith(library))throw new java.io.IOException("Archive leaves active library.");
            byte[] bytes=Files.readAllBytes(file);String before=hash(bytes);Path relative=library.relativize(file);
            if(BillBookDocuments.numberingName(relative.toString())){
                Path archived=output.resolve("removed-numbering").resolve(relative).normalize();
                if(!archived.startsWith(output))throw new java.io.IOException("Invalid backup path.");
                Files.createDirectories(archived.getParent());Files.move(file,archived);
                Path receipt=file.resolveSibling(file.getFileName()+".source-sha256");if(Files.isRegularFile(receipt))Files.move(receipt,archived.resolveSibling(archived.getFileName()+".source-sha256"));
                manifest.append(relative).append('\t').append(before).append("\t\tArchived numbering variant\n");removed++;continue;
            }
            if(!before.equals(expected.get(file))){manifest.append(relative).append('\t').append(before).append("\t\tSKIPPED: changed after transfer\n");skipped++;continue;}
            Path reference=references.get(file);if(reference==null){manifest.append(relative).append('\t').append(before).append("\t\tREVIEW: missing reference\n");continue;}
            try{
                var p=PublicationFiles.read(bytes);int repairs=PublicationTableArtworkRepair.repair(p,reference);
                Path nativeSource=Path.of(Files.readString(reference.resolveSibling("source.txt")).strip());
                if(Files.isRegularFile(nativeSource)&&nativeSource.toString().toLowerCase(Locale.ROOT).endsWith(".pub")&&hash(Files.readAllBytes(nativeSource)).equals(checkpoint.get(file)[1]))repairs+=PublisherNativeTables.repair(p,nativeSource);
                repairs+=PublicationTableArtworkRepair.refreshNativeCrops(p,reference);
                if(Files.isRegularFile(nativeSource)&&nativeSource.toString().toLowerCase(Locale.ROOT).endsWith(".pub")&&hash(Files.readAllBytes(nativeSource)).equals(checkpoint.get(file)[1]))repairs+=PublisherNativeTables.repair(p,nativeSource,PublicationTableArtworkRepair.clippedHeadings(p,reference));
                if(Files.isRegularFile(nativeSource)&&nativeSource.toString().toLowerCase(Locale.ROOT).endsWith(".pub")&&hash(Files.readAllBytes(nativeSource)).equals(checkpoint.get(file)[1]))repairs+=PublisherNativeArtwork.repair(p,nativeSource);
                if(repairs==0)continue;
                Path backup=output.resolve("before-repair").resolve(relative);Files.createDirectories(backup.getParent());Files.write(backup,bytes,StandardOpenOption.CREATE_NEW);
                byte[] updated=PublicationFiles.archive(p);PublicationFiles.read(updated).validate();
                if(!before.equals(hash(Files.readAllBytes(file))))throw new java.io.IOException("Template changed during repair.");
                PublicationFiles.writeAtomic(file,updated);String after=hash(updated);
                Path previews=output.resolve("previews").resolve(relative+".png");Files.createDirectories(previews.getParent());
                javax.imageio.ImageIO.write(PublicationTemplateLibrary.preview(p,0),"png",previews.toFile());
                String[] row=checkpoint.get(file).clone();row[3]=after;row[7]="Damaged exports repaired from source PDF; visual review pending";
                Files.writeString(reports.get(file),String.join("\t",row)+"\n",StandardOpenOption.APPEND);
                manifest.append(relative).append('\t').append(before).append('\t').append(after).append("\tRepaired ").append(repairs).append(" exports; editable cells retained\n");fixed++;objects+=repairs;
            }catch(Exception ex){manifest.append(relative).append('\t').append(before).append("\t\tREVIEW: ").append(String.valueOf(ex.getMessage()).replace('\n',' ').replace('\t',' ')).append('\n');}
        }
        Files.writeString(output.resolve("cleanup.tsv"),manifest);
        System.out.println("Removed numbering variants: "+removed+"; repaired templates: "+fixed+"; repaired exports: "+objects+"; modified designs retained: "+skipped);
        System.out.println("Review and backups: "+output);
    }
    private static String hash(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
