package gy.deckers.studio;

import com.google.gson.*;
import data.DatabaseConfig;
import services.LanApiClient;
import publishing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Server-owned business-card/label templates with hash-verified local caches. */
final class SharedPublicationLibrary {
    interface Transport {JsonObject send(JsonObject body)throws Exception;}
    private final Path folder;
    private final String kind;
    private final Transport transport;
    volatile boolean online;
    volatile String status="OFFLINE — cached templates; server saves unavailable.";
    volatile String migrationProgress="";
    final StoreTemplateFolders stores;
    private JsonArray catalog=new JsonArray();
    SharedPublicationLibrary(boolean labels){this(labels,false);}
    SharedPublicationLibrary(boolean labels,boolean billBooks){
        this(Path.of(System.getProperty("user.home"),"shared-publication-cache",UUID.nameUUIDFromBytes(
                (LanApiClient.baseUri()+"/"+DatabaseConfig.load().locationId()).getBytes(StandardCharsets.UTF_8)).toString(),billBooks?"bill-books":labels?"labels":"business-cards"),
                billBooks?"BILL_BOOKS":labels?"LABELS":"BUSINESS_CARDS",LanApiClient::studioTemplates);
    }
    SharedPublicationLibrary(Path folder,String kind,Transport transport){this.folder=folder.toAbsolutePath().normalize();this.kind=kind;this.transport=transport;this.stores=new StoreTemplateFolders(this.folder);}
    Path folder(){return folder;}
    boolean contains(Path file){return file!=null&&folder.equals(file.toAbsolutePath().normalize().getParent());}
    private JsonObject request(String action){var r=new JsonObject();r.addProperty("action",action);r.addProperty("kind",kind);return r;}
    private JsonObject send(JsonObject r)throws Exception{return transport.send(r);}
    private Path meta(Path file){return file.resolveSibling(file.getFileName()+".meta.json");}
    private void atomic(Path file,byte[] data)throws Exception{Files.createDirectories(folder);Path temp=Files.createTempFile(folder,".template-",".tmp");try{Files.write(temp,data);Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
    List<Path> files()throws IOException{if(!Files.isDirectory(folder))return List.of();try(var files=Files.list(folder)){return files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".sspub")&&Files.isRegularFile(meta(p))).filter(stores::visible).sorted().toList();}}
    String label(Path file){try{return stores.label(file,JsonParser.parseString(Files.readString(meta(file))).getAsJsonObject().get("name").getAsString());}catch(Exception ex){return file.getFileName().toString();}}
    private static String digest(byte[] bytes)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}
    void refresh()throws Exception{
        try{
            JsonObject response=send(allStoresRequest());
            if(!response.has("kind")||!kind.equals(response.get("kind").getAsString()))throw new IOException("Update the SmartStock server to enable shared card and label templates.");
            stores.accept(response);JsonArray rows=response.getAsJsonArray("templates");if(rows.size()>10000)throw new IOException("Shared template list exceeds 10000 designs.");int pending=0;Set<Path> retained=new HashSet<>();
            for(var entry:rows){var row=entry.getAsJsonObject();String id=UUID.fromString(row.get("id").getAsString()).toString(),hash=row.get("sha256").getAsString();int size=row.get("size").getAsInt();
                if(!hash.matches("[a-f0-9]{64}")||size<1||size>PublicationFiles.MAX_BYTES)throw new IOException("Invalid shared template metadata.");
                if(!row.get("backedUp").getAsBoolean())pending++;
                Path file=stores.file(row,".sspub");retained.add(meta(file));
                if(!Files.isRegularFile(file)||!Files.isRegularFile(meta(file))||!BadgeTemplateLibrary.fingerprint(file).equals(hash)){
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                    while(bytes.size()<size){JsonObject read=request("READ");stores.target(read,row);read.addProperty("id",id);read.addProperty("sha256",hash);read.addProperty("offset",bytes.size());JsonObject chunk=send(read);
                        if(chunk.get("size").getAsInt()!=size||!hash.equals(chunk.get("sha256").getAsString()))throw new IOException("Template changed during download.");
                        byte[] block=Base64.getDecoder().decode(chunk.get("bytes").getAsString());if(block.length<1||block.length>524288||(long)bytes.size()+block.length>size)throw new IOException("Invalid template download chunk.");bytes.write(block);
                    }
                    byte[] archive=bytes.toByteArray();if(!digest(archive).equals(hash))throw new IOException("Shared template SHA-256 mismatch.");PublicationTemplates.requireTemplate(PublicationFiles.read(archive));atomic(file,archive);
                }
                atomic(meta(file),row.toString().getBytes(StandardCharsets.UTF_8));
            }
            if(Files.isDirectory(folder))try(var cached=Files.list(folder)){for(Path metadata:cached.filter(p->p.toString().endsWith(".sspub.meta.json")).toList())if(!retained.contains(metadata))Files.deleteIfExists(metadata);}
            catalog=rows.deepCopy();online=true;status="ONLINE — server template library • "+rows.size()+" templates • "+pending+" OneDrive backups pending"+(response.has("backupError")&&response.get("backupError").getAsBoolean()?" • OneDrive needs attention":response.has("backupCatalogPending")&&response.get("backupCatalogPending").getAsBoolean()?" • backup index pending":"");
        }catch(Exception ex){online=false;status="OFFLINE / SERVER UNAVAILABLE — "+(ex.getMessage()!=null&&ex.getMessage().startsWith("Update the SmartStock")?ex.getMessage():"cached templates; reconnect to save.");throw ex;}
    }
    record Saved(Path file,String sha256){}
    void delete(Path file)throws Exception{
        if(!online||!contains(file))throw new IOException("Reconnect and select a shared template before deleting.");
        JsonObject row=stores.metadata(file),body=request("DELETE");stores.target(body,row);
        body.add("id",row.get("id"));body.add("expected",row.get("sha256"));
        body.addProperty("expectedAssignment",row.has("assignmentVersion")?row.get("assignmentVersion").getAsString():"");
        JsonObject result=send(body);if(!result.has("deleted")||!result.get("deleted").getAsBoolean())throw new IOException("Server did not confirm deletion.");
        Files.deleteIfExists(meta(file));refresh();
    }
    Saved save(Publication source,Path current,String expected,BooleanSupplier active)throws Exception{
        JsonObject metadata=current!=null&&contains(current)?stores.metadata(current):new JsonObject();
        String id=metadata.has("id")?metadata.get("id").getAsString():UUID.randomUUID().toString();
        String savedHash=upload(PublicationTemplates.clean(source),id,current==null?"":expected,active,metadata);refresh();JsonObject row=metadata.deepCopy();row.addProperty("id",id);if(!row.has("storageStoreId")){for(var entry:catalog){var r=entry.getAsJsonObject();if(r.get("id").getAsString().equals(id)){row=r;break;}}}return new Saved(stores.file(row,".sspub"),savedHash);
    }
    private String upload(Publication p,String id,String expected,BooleanSupplier active)throws Exception{return upload(p,id,expected,active,new JsonObject());}
    private String upload(Publication p,String id,String expected,BooleanSupplier active,JsonObject metadata)throws Exception{
        if(!online||!active.getAsBoolean())throw new IOException("Reconnect and sign in before saving templates.");
        byte[] archive=PublicationFiles.archive(p);String contentHash=PublicationTemplates.fingerprint(p);
        JsonObject begin=request("BEGIN");stores.target(begin,metadata);begin.addProperty("id",id);begin.addProperty("expected",expected);begin.addProperty("name",p.title);begin.addProperty("size",archive.length);
        String upload=send(begin).get("uploadId").getAsString();boolean finished=false;
        try{
            for(int offset=0;offset<archive.length;offset+=524288){if(!active.getAsBoolean())throw new IOException("Template save cancelled after sign-out.");JsonObject chunk=request("CHUNK");stores.target(chunk,metadata);chunk.addProperty("uploadId",upload);chunk.addProperty("offset",offset);chunk.addProperty("bytes",Base64.getEncoder().encodeToString(Arrays.copyOfRange(archive,offset,Math.min(archive.length,offset+524288))));send(chunk);}
            if(!active.getAsBoolean())throw new IOException("Template save cancelled after sign-out.");JsonObject end=request("FINISH");stores.target(end,metadata);end.addProperty("uploadId",upload);JsonObject row=send(end);
            if(!id.equals(row.get("id").getAsString())||!digest(archive).equals(row.get("sha256").getAsString())||!contentHash.equals(row.get("contentSha256").getAsString()))throw new IOException("Server template acknowledgement differs from saved content.");finished=true;return row.get("sha256").getAsString();
        }finally{if(!finished&&active.getAsBoolean())try{JsonObject abort=request("ABORT");stores.target(abort,metadata);abort.addProperty("uploadId",upload);send(abort);}catch(Exception ignored){}}
    }
    private JsonObject allStoresRequest(){JsonObject r=request("LIST");r.addProperty("viewStoreId",0);return r;}
    void assign(JsonObject body)throws Exception{if(!online)throw new IOException("Reconnect before changing template stores.");body.addProperty("kind",kind);send(body);refresh();}
    String migrate(List<Path> localFiles,BooleanSupplier active)throws Exception{return migrate(localFiles,active,false);}
    String migrate(List<Path> localFiles,BooleanSupplier active,boolean includeNewFiles)throws Exception{
        if(!online||!active.getAsBoolean())return "Template migration pending until online sign-in.";
        Path marker=folder.resolve("local-template-migration-v1.json");JsonObject state;
        if(Files.exists(marker)){if(Files.size(marker)>4*1024*1024)throw new IOException("Template migration record is too large.");state=JsonParser.parseString(Files.readString(marker)).getAsJsonObject();if(state.get("finished").getAsBoolean()&&!includeNewFiles)return "Existing templates migrated to the server.";}
        else{if(localFiles.size()>10000)throw new IOException("Too many local templates.");state=new JsonObject();state.addProperty("finished",false);JsonArray files=new JsonArray();for(Path file:localFiles){JsonObject item=new JsonObject();item.addProperty("file",file.toAbsolutePath().toString());item.addProperty("done",false);files.add(item);}state.add("files",files);atomic(marker,state.toString().getBytes(StandardCharsets.UTF_8));}
        if(includeNewFiles){
            Set<String> known=new HashSet<>();for(var item:state.getAsJsonArray("files"))known.add(item.getAsJsonObject().get("file").getAsString());
            for(Path file:localFiles){String path=file.toAbsolutePath().toString();if(known.add(path)){
                if(known.size()>10000)throw new IOException("Too many local templates.");
                JsonObject item=new JsonObject();item.addProperty("file",path);item.addProperty("done",false);state.getAsJsonArray("files").add(item);state.addProperty("finished",false);
            }}
            atomic(marker,state.toString().getBytes(StandardCharsets.UTF_8));
        }
        Set<String> ids=new HashSet<>(),hashes=new HashSet<>();for(var entry:catalog){var row=entry.getAsJsonObject();if(!stores.own(row))continue;ids.add(row.get("id").getAsString());hashes.add(row.get("contentSha256").getAsString());}
        int pending=0,imported=0,processed=0,total=state.getAsJsonArray("files").size();
        migrationProgress="Checking local templates: "+total;
        for(var entry:state.getAsJsonArray("files")){var item=entry.getAsJsonObject();if(item.get("done").getAsBoolean())continue;if(!active.getAsBoolean()){pending++;continue;}
            migrationProgress="Uploading local templates: "+(++processed)+" • "+imported+" uploaded this pass";
            try{Path file=Path.of(item.get("file").getAsString());if(Files.size(file)>PublicationFiles.MAX_BYTES)throw new IOException("Template exceeds archive limit.");String before=BadgeTemplateLibrary.fingerprint(file);Publication p=PublicationTemplates.clean(PublicationFiles.read(Files.readAllBytes(file)));if(!before.equals(BadgeTemplateLibrary.fingerprint(file)))throw new IOException("Local template changed while reading.");String hash=PublicationTemplates.fingerprint(p),id=UUID.nameUUIDFromBytes(("smartstudio-template-v1/"+kind+"/"+hash).getBytes(StandardCharsets.UTF_8)).toString();
                if(!ids.contains(id)&&!hashes.contains(hash)){
                    try{upload(p,id,"",active);}
                    catch(Exception ex){if(!active.getAsBoolean())throw ex;JsonObject get=request("GET");get.addProperty("id",id);JsonObject existing=send(get);if(!id.equals(existing.get("id").getAsString()))throw ex;}
                    ids.add(id);hashes.add(hash);imported++;
                }
                item.addProperty("done",true);atomic(marker,state.toString().getBytes(StandardCharsets.UTF_8));
            }catch(Exception ex){pending++;item.addProperty("error",ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage()));atomic(marker,state.toString().getBytes(StandardCharsets.UTF_8));}
        }
        if(pending==0&&active.getAsBoolean()){state.addProperty("finished",true);atomic(marker,state.toString().getBytes(StandardCharsets.UTF_8));}
        if(imported>0)refresh();migrationProgress=pending==0?"Local template migration complete":"Template migration pending: "+pending+" file(s); retry is automatic";return pending==0?"Existing templates migrated to the server.":"Template migration pending: "+pending+" file(s). Check connection, save permission and local files; retry is automatic.";
    }
}
