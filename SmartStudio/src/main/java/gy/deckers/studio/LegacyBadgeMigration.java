package gy.deckers.studio;

import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;

/** One-time, resumable import of the pre-shared-library folder; never overwrites server designs. */
final class LegacyBadgeMigration {
    private static final Gson JSON=new Gson();
    record Result(int imported,int completed,int pending,boolean finished) {
        String message(){return finished?"Existing badge templates migrated to the server ("+completed+")."
                :"Badge migration pending: "+pending+" file(s). Check server permission, connection and local templates; it will retry.";}
    }
    private final Path cache;
    private final SharedBadgeLibrary.Transport transport;
    LegacyBadgeMigration(Path cache,SharedBadgeLibrary.Transport transport){this.cache=cache;this.transport=transport;}
    private JsonObject request(String action){JsonObject r=new JsonObject();r.addProperty("action",action);return r;}
    private void persist(Path file,JsonObject state)throws Exception{
        Files.createDirectories(cache);Path temp=Files.createTempFile(cache,".migration-",".tmp");
        try{Files.writeString(temp,JSON.toJson(state));Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
    }
    Result run(Path configuredFolder,BooleanSupplier active)throws Exception{
        Path source=configuredFolder.toAbsolutePath().normalize();
        if(source.equals(cache.toAbsolutePath().normalize()))return new Result(0,0,0,true);
        Path marker=cache.resolve("legacy-badge-migration-v1.json");
        JsonObject state;
        if(Files.exists(marker)){
            if(Files.size(marker)>4*1024*1024)throw new IOException("Badge migration record exceeds limit.");
            state=JsonParser.parseString(Files.readString(marker)).getAsJsonObject();
            if(state.get("version").getAsInt()!=1)throw new IOException("Unsupported badge migration record.");
            if(state.get("finished").getAsBoolean())return result(state,0);
            source=Path.of(state.get("source").getAsString());
        }else{
            // Don't mark an unavailable/disconnected old folder as migrated.
            if(!Files.isDirectory(source))return new Result(0,0,1,false);
            if(!active.getAsBoolean())return new Result(0,0,1,false);
            List<Path> files;
            try(var entries=Files.list(source)){files=entries.filter(Files::isRegularFile)
                    .filter(p->p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sstbadge"))
                    .sorted().limit(10001).toList();}
            if(files.size()>10000)throw new IOException("Legacy badge folder exceeds 10000 templates.");
            state=new JsonObject();state.addProperty("version",1);state.addProperty("source",source.toString());state.addProperty("finished",false);
            JsonArray items=new JsonArray();for(Path file:files){JsonObject item=new JsonObject();item.addProperty("file",file.getFileName().toString());item.addProperty("done",false);items.add(item);}state.add("files",items);
            persist(marker,state);
        }
        if(!active.getAsBoolean())return result(state,0);
        JsonArray catalog=transport.send(request("LIST")).getAsJsonArray("templates");
        Set<String> ids=new HashSet<>(),digests=new HashSet<>();
        for(var item:catalog){var row=item.getAsJsonObject();ids.add(row.get("id").getAsString());digests.add(row.get("sha256").getAsString());}
        int imported=0;
        for(var entry:state.getAsJsonArray("files")){
            if(!active.getAsBoolean())break;
            var item=entry.getAsJsonObject();if(item.get("done").getAsBoolean())continue;
            try{
                String filename=item.get("file").getAsString();Path file=source.resolve(filename).normalize();
                if(!file.getParent().equals(source)||!filename.toLowerCase(Locale.ROOT).endsWith(".sstbadge"))throw new IOException("Invalid migration filename.");
                String before=BadgeTemplateLibrary.fingerprint(file);BadgeTemplate template=BadgeTemplate.load(file);
                if(!before.equals(BadgeTemplateLibrary.fingerprint(file)))throw new IOException("Template changed during migration.");
                String content=BadgeTemplate.portableJson(template),digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
                String id=UUID.nameUUIDFromBytes(("smartstudio-legacy-badge-v1/"+digest).getBytes(StandardCharsets.UTF_8)).toString();
                // A confirmed earlier upload or an already shared identical design needs no new save.
                // An edited server head under this identity is preserved, even after a lost response.
                if(!ids.contains(id)&&!digests.contains(digest)){
                    if(!active.getAsBoolean())break;
                    JsonObject save=request("SAVE");save.addProperty("id",id);save.addProperty("name",template.name);save.addProperty("expected","");save.addProperty("content",content);
                    try{JsonObject saved=transport.send(save);if(!id.equals(saved.get("id").getAsString())||!digest.equals(saved.get("sha256").getAsString()))throw new IOException("Migration save acknowledgement did not match the template.");}
                    catch(Exception ex){
                        // Concurrent imports, or an acknowledged request whose response was lost.
                        JsonObject get=request("GET");get.addProperty("id",id);
                        try{JsonObject existing=transport.send(get);if(!id.equals(existing.get("id").getAsString()))throw ex;}
                        catch(Exception absent){throw ex;}
                    }
                    imported++;ids.add(id);digests.add(digest);
                }
                item.addProperty("done",true);persist(marker,state);
            }catch(Exception ex){
                // Keep this file pending, but allow other valid designs to migrate.
            }
        }
        Result result=result(state,imported);
        if(result.pending()==0&&active.getAsBoolean()){state.addProperty("finished",true);persist(marker,state);}
        return result(state,imported);
    }
    private Result result(JsonObject state,int imported){int done=0;JsonArray files=state.getAsJsonArray("files");for(var file:files)if(file.getAsJsonObject().get("done").getAsBoolean())done++;return new Result(imported,done,files.size()-done,state.get("finished").getAsBoolean());}
}
