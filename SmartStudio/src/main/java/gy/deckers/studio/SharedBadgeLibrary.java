package gy.deckers.studio;

import com.google.gson.*;
import services.LanApiClient;
import data.DatabaseConfig;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Read-through local cache. Offline edits never write or enqueue templates. */
final class SharedBadgeLibrary {
    private final Path folder;
    final StoreTemplateFolders stores;
    interface Transport{JsonObject send(JsonObject request)throws Exception;}
    private final Transport transport;
    volatile boolean online;
    volatile String status="OFFLINE — cached templates only; changes cannot be saved.";
    SharedBadgeLibrary(){
        DatabaseConfig config=DatabaseConfig.load();String scope=LanApiClient.baseUri()+"/"+config.locationId();
        folder=Path.of(System.getProperty("user.home"),"shared-badge-cache",UUID.nameUUIDFromBytes(scope.getBytes(StandardCharsets.UTF_8)).toString());
        transport=LanApiClient::studioTemplates;stores=new StoreTemplateFolders(folder);
    }
    SharedBadgeLibrary(Path folder,Transport transport){this.folder=folder;this.transport=transport;this.stores=new StoreTemplateFolders(folder);}
    Path folder(){return folder;}
    private JsonObject request(String action){JsonObject request=new JsonObject();request.addProperty("action",action);return request;}
    private Path meta(Path file){return file.resolveSibling(file.getFileName()+".meta.json");}
    private void atomic(Path path,byte[] data)throws Exception{Files.createDirectories(folder);Path temp=Files.createTempFile(folder,".cache-",".tmp");try{Files.write(temp,data);Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temp);}}
    void refresh()throws Exception{
        try{
            JsonObject catalog=transport.send(allStoresRequest());stores.accept(catalog);JsonArray rows=catalog.getAsJsonArray("templates");
            if(rows.size()>10000)throw new java.io.IOException("Template catalog is too large");
            ExecutorService pool=Executors.newFixedThreadPool(4);List<Future<?>> jobs=new ArrayList<>();int pending=0;Set<Path> retained=new HashSet<>();
            try{for(JsonElement item:rows){JsonObject row=item.getAsJsonObject();String id=UUID.fromString(row.get("id").getAsString()).toString();String digest=row.get("sha256").getAsString();
                if(!digest.matches("[a-f0-9]{64}"))throw new java.io.IOException("Invalid template digest");
                if(!row.get("backedUp").getAsBoolean())pending++;
                Path file=stores.file(row,".sstbadge");retained.add(meta(file));
                if(Files.isRegularFile(file)&&Files.exists(meta(file))&&BadgeTemplateLibrary.fingerprint(file).equals(digest)){atomic(meta(file),row.toString().getBytes(StandardCharsets.UTF_8));continue;}
                jobs.add(pool.submit(()->{try{JsonObject get=request("GET");stores.target(get,row);get.addProperty("id",id);get.addProperty("sha256",digest);JsonObject data=transport.send(get);byte[] content=data.get("content").getAsString().getBytes(StandardCharsets.UTF_8);
                    BadgeTemplate.read(new java.io.ByteArrayInputStream(content));if(!java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content)).equals(digest))throw new java.io.IOException("Template hash mismatch");atomic(file,content);atomic(meta(file),row.toString().getBytes(StandardCharsets.UTF_8));
                }catch(Exception ex){throw new CompletionException(ex);}}));
            }for(Future<?> job:jobs)job.get();}finally{pool.shutdownNow();}
            if(Files.isDirectory(folder))try(var cached=Files.list(folder)){for(Path metadata:cached.filter(p->p.toString().endsWith(".sstbadge.meta.json")).toList())if(!retained.contains(metadata))Files.deleteIfExists(metadata);}
            online=true;status="ONLINE — shared library • "+rows.size()+" templates • "+pending+" backups pending"+(catalog.has("backupError")&&catalog.get("backupError").getAsBoolean()?" • OneDrive needs attention":catalog.has("backupCatalogPending")&&catalog.get("backupCatalogPending").getAsBoolean()?" • backup index pending":"");
        }catch(Exception ex){online=false;status="OFFLINE / SERVER UNAVAILABLE — cached templates only; changes cannot be saved.";throw ex;}
    }
    private JsonObject allStoresRequest(){JsonObject r=request("LIST");r.addProperty("viewStoreId",0);return r;}
    void assign(JsonObject body)throws Exception{if(!online)throw new java.io.IOException("Reconnect before changing template stores.");transport.send(body);refresh();}
    LegacyBadgeMigration.Result migrateLegacy(Path source,java.util.function.BooleanSupplier active)throws Exception{
        if(!online)throw new java.io.IOException("Reconnect before migrating existing badge templates.");
        return new LegacyBadgeMigration(folder,transport).run(source,active);
    }
    String label(Path file){try{JsonObject row=JsonParser.parseString(Files.readString(meta(file))).getAsJsonObject();return stores.label(file,row.get("name").getAsString());}catch(Exception ex){return file.getFileName().toString().replaceFirst("(?i)\\.sstbadge$","");}}
    void delete(Path file)throws Exception {
        if(!online||file==null||!Files.exists(meta(file)))throw new java.io.IOException("Reconnect and select a shared template before deleting.");
        JsonObject row=stores.metadata(file),body=request("DELETE");stores.target(body,row);
        body.add("id",row.get("id"));body.add("expected",row.get("sha256"));
        body.addProperty("expectedAssignment",row.has("assignmentVersion")?row.get("assignmentVersion").getAsString():"");
        JsonObject result=transport.send(body);
        if(!result.has("deleted")||!result.get("deleted").getAsBoolean())throw new java.io.IOException("Server did not confirm deletion.");
        Files.deleteIfExists(meta(file));refresh();
    }
    JsonObject record(Path file,String action,JsonObject payload)throws Exception {
        if(file==null||!Files.exists(meta(file)))throw new java.io.IOException("Save this template to the shared library first.");
        JsonObject row=stores.metadata(file),body=payload.deepCopy();stores.target(body,row);body.addProperty("id",row.get("id").getAsString());body.addProperty("action",action);
        return transport.send(body);
    }
    Path save(BadgeTemplate template,Path current,String expected)throws Exception{
        if(!online)throw new java.io.IOException("Offline mode: changes cannot be saved. Reconnect to the store server and refresh first.");
        JsonObject metadata=current!=null&&Files.exists(meta(current))?stores.metadata(current):new JsonObject();String id=metadata.has("id")?metadata.get("id").getAsString():UUID.randomUUID().toString();
        JsonObject save=request("SAVE");stores.target(save,metadata);save.addProperty("id",id);save.addProperty("name",template.name);save.addProperty("expected",current!=null&&Files.exists(meta(current))?expected:"");
        // Match the portable format while keeping session photos and framing out.
        save.addProperty("content",BadgeTemplate.portableJson(template));JsonObject result;
        try{result=transport.send(save);}catch(Exception ex){if(ex instanceof services.LanApiClient.LanApiException api && (api.code().equals("PERMISSION_DENIED")||api.code().equals("TEMPLATE_CONFLICT")))throw ex;online=false;status="OFFLINE / SAVE FAILED — changes were not confirmed saved.";throw ex;}
        Path file=stores.file(result,".sstbadge");atomic(file,save.get("content").getAsString().getBytes(StandardCharsets.UTF_8));atomic(meta(file),result.toString().getBytes(StandardCharsets.UTF_8));return file;
    }
}
