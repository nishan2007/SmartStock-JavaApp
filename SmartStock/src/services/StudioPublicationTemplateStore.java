package services;

import com.google.gson.*;
import data.EnvironmentProfile;
import publishing.PublicationFiles;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Bounded archive uploads, scoped to a store/category and the authenticated staff member. */
public final class StudioPublicationTemplateStore {
    private static final class Production {
    private static final Map<String,StudioPublicationTemplateStore> STORES=new HashMap<>();
    static {
        var cloud=new StudioTemplateStore.Cloud(){final OneDriveImageCloudProvider p=new OneDriveImageCloudProvider();
            public void upload(String id,Path file)throws Exception{p.uploadPrivateFile(id,file,"application/json");}
            public boolean download(String id,Path file)throws Exception{return p.downloadPrivateFile(id,file);}};
        for(String kind:List.of("BUSINESS_CARDS","LABELS","BILL_BOOKS"))STORES.put(kind,new StudioPublicationTemplateStore(
                EnvironmentProfile.active().file("studio-templates").resolve(kind.toLowerCase(Locale.ROOT)),cloud,kind));
        var backups=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"studio-publication-backup");t.setDaemon(true);return t;});
        backups.scheduleWithFixedDelay(()->{for(var store:STORES.values())try{store.backupPending();}catch(Exception ignored){}},10,60,TimeUnit.SECONDS);
    }
    }
    public static void start(){Production.STORES.size();} // Initialize backup retry even before any client reconnects.
    public static StudioPublicationTemplateStore current(String kind){var store=Production.STORES.get(kind);if(store==null)throw new IllegalArgumentException("Unknown template category.");return store;}
    private final Path root;
    private final StudioTemplateStore store;
    private final String kind;
    StudioPublicationTemplateStore(Path root,StudioTemplateStore.Cloud cloud,String kind){this.root=root.toAbsolutePath().normalize();this.kind=kind;store=new StudioTemplateStore(root,cloud,true);}
    void configureStores(Map<Integer,String> names){store.configureStores(names);}
    void backupPending()throws Exception{store.backupPending();}
    public synchronized Object execute(int location,int user,JsonObject request)throws Exception{
        if(location<=0||user<=0)throw new IllegalArgumentException("Authenticated store and staff are required.");
        String action=text(request,"action",12);
        if(action.equals("LIST")){JsonObject catalog=store.execute(location,request);catalog.addProperty("kind",kind);return catalog;}
        if(action.equals("GET")||action.equals("ASSIGN")||action.equals("DELETE"))return store.execute(location,request);
        if(action.equals("READ"))return store.readChunk(location,uuid(request,"id"),text(request,"sha256",64),integer(request,"offset"));
        Path uploads=store.directory(location).resolve("uploads");Files.createDirectories(uploads);
        if(action.equals("BEGIN")){
            int pending=0;long now=System.currentTimeMillis();
            try(var files=Files.list(uploads)){for(Path file:files.filter(p->p.toString().endsWith(".json")).toList()){
                JsonObject meta=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                if(meta.get("expires").getAsLong()<now){Files.deleteIfExists(uploads.resolve(file.getFileName().toString().replace(".json",".part")));Files.deleteIfExists(file);}
                else if(meta.get("user").getAsInt()==user)pending++;
            }}
            if(pending>=5)throw new IllegalArgumentException("Too many pending template uploads. Wait for expiry or retry later.");
            String id=uuid(request,"id"),expected=text(request,"expected",64),name=text(request,"name",200);int size=integer(request,"size");
            if(name.isBlank()||(!expected.isEmpty()&&!expected.matches("[a-f0-9]{64}"))||size<1||size>PublicationFiles.MAX_BYTES)throw new IllegalArgumentException("Invalid template upload.");
            String upload=UUID.randomUUID().toString();JsonObject meta=new JsonObject();meta.addProperty("user",user);meta.addProperty("id",id);meta.addProperty("expected",expected);meta.addProperty("name",name);meta.addProperty("size",size);meta.addProperty("expires",now+3600000);
            Path staging=uploads.resolve(upload+".part"),manifest=uploads.resolve(upload+".json"),temp=Files.createTempFile(uploads,".upload-",".tmp");
            try{Files.write(staging,new byte[0],StandardOpenOption.CREATE_NEW);Files.writeString(temp,meta.toString());Files.move(temp,manifest,StandardCopyOption.ATOMIC_MOVE);}
            catch(Exception ex){Files.deleteIfExists(staging);throw ex;}finally{Files.deleteIfExists(temp);}return Map.of("uploadId",upload);
        }
        if(!Set.of("CHUNK","FINISH","ABORT","UPLOAD_META").contains(action))throw new IllegalArgumentException("Unknown template operation.");
        String upload=uuid(request,"uploadId");Path manifest=uploads.resolve(upload+".json"),part=uploads.resolve(upload+".part");
        if(action.equals("ABORT")&&!Files.exists(manifest))return Map.of("aborted",true);
        JsonObject meta=JsonParser.parseString(Files.readString(manifest)).getAsJsonObject();
        if(meta.get("user").getAsInt()!=user||meta.get("expires").getAsLong()<System.currentTimeMillis())throw new IllegalArgumentException("Upload expired or belongs to another staff member.");
        if(action.equals("UPLOAD_META")){JsonObject info=new JsonObject();info.add("id",meta.get("id"));info.add("expected",meta.get("expected"));return info;}
        if(action.equals("ABORT")){Files.deleteIfExists(part);Files.deleteIfExists(manifest);return Map.of("aborted",true);}
        if(action.equals("CHUNK")){
            byte[] bytes=Base64.getDecoder().decode(text(request,"bytes",710000));int offset=integer(request,"offset");
            if(bytes.length<1||bytes.length>524288||offset<0||Files.size(part)!=offset||(long)offset+bytes.length>meta.get("size").getAsInt())throw new IllegalArgumentException("Template chunk offset or size changed.");
            Files.write(part,bytes,StandardOpenOption.APPEND);return Map.of("offset",offset+bytes.length);
        }
        if(Files.size(part)!=meta.get("size").getAsInt())throw new IllegalArgumentException("Template upload is incomplete.");
        JsonObject save=new JsonObject();save.addProperty("kind",kind);save.addProperty("action","SAVE");save.addProperty("id",meta.get("id").getAsString());save.addProperty("name",meta.get("name").getAsString());save.addProperty("expected",meta.get("expected").getAsString());save.addProperty("contentBase64",Base64.getEncoder().encodeToString(Files.readAllBytes(part)));
        JsonObject result=store.execute(location,save);Files.deleteIfExists(part);Files.deleteIfExists(manifest);return result;
    }
    private static String text(JsonObject b,String key,int limit){if(!b.has(key)||!b.get(key).isJsonPrimitive()||!b.get(key).getAsJsonPrimitive().isString())throw new IllegalArgumentException("Missing "+key);String value=b.get(key).getAsString();if(value.length()>limit)throw new IllegalArgumentException("Invalid "+key);return value;}
    private static String uuid(JsonObject b,String key){return UUID.fromString(text(b,key,40)).toString();}
    private static int integer(JsonObject b,String key){try{return new java.math.BigDecimal(b.get(key).getAsString()).intValueExact();}catch(Exception ex){throw new IllegalArgumentException("Invalid "+key);}}
}
