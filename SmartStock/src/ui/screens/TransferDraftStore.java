package ui.screens;

import com.google.gson.Gson;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;
import utils.SecureFilePermissions;

/** Workstation-local preparation only. Never reserves or mutates store stock. */
final class TransferDraftStore {
    record Line(int productId,String sku,String name,int quantity) {}
    record Draft(String id,int destinationId,String destination,String note,List<Line> lines) {
        @Override public String toString(){return (destination==null||destination.isBlank()?"No destination":destination)+" · "+lines.size()+" items · "+id.substring(0,8);}
    }
    private static final Gson JSON=new Gson();
    private final Path directory;
    TransferDraftStore(Path directory){this.directory=directory;}
    List<Draft> load()throws IOException {
        if(!Files.exists(directory))return List.of();
        List<Draft> drafts=new ArrayList<>();
        try(var paths=Files.list(directory)){
            for(Path path:paths.filter(p->p.getFileName().toString().endsWith(".json")).sorted().toList()){
                if(Files.size(path)>2_000_000)throw new IOException("Saved transfer draft is too large.");
                try{Draft draft=JSON.fromJson(Files.readString(path),Draft.class);validate(draft);drafts.add(draft);}
                catch(RuntimeException error){throw new IOException("Could not read a saved transfer draft. Original file was preserved.",error);}
            }
        }
        return List.copyOf(drafts);
    }
    void save(Draft draft)throws IOException {
        validate(draft);Files.createDirectories(directory);SecureFilePermissions.restrictDirectoryToOwner(directory);
        Path temporary=Files.createTempFile(directory,"draft-",".tmp");
        try{
            SecureFilePermissions.restrictFileToOwner(temporary);Files.writeString(temporary,JSON.toJson(draft));
            try{Files.move(temporary,path(draft.id()),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException error){Files.move(temporary,path(draft.id()),StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }
    void delete(String id)throws IOException {Files.deleteIfExists(path(id));}
    private Path path(String id){UUID.fromString(id);return directory.resolve(id+".json");}
    private static void validate(Draft draft){
        Objects.requireNonNull(draft);UUID.fromString(draft.id());
        if(draft.destinationId()<0||draft.note()==null||draft.note().length()>1000||draft.lines()==null||draft.lines().size()>300)throw new IllegalArgumentException("Invalid transfer draft.");
        Set<Integer> ids=new HashSet<>();
        for(Line line:draft.lines())if(line==null||line.productId()<=0||line.quantity()<1||line.quantity()>1_000_000||!ids.add(line.productId())||line.sku()==null||line.name()==null)throw new IllegalArgumentException("Invalid transfer draft item.");
    }
}
