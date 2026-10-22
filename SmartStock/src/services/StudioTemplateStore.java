package services;

import com.google.gson.*;
import data.EnvironmentProfile;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Store-local authority: immutable revisions, atomic heads, durable backup retry. */
public final class StudioTemplateStore {
    private static final Gson JSON=new Gson();
    private static final ScheduledExecutorService BACKUPS=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"studio-template-backup");t.setDaemon(true);return t;});
    private static final StudioTemplateStore CURRENT=new StudioTemplateStore(EnvironmentProfile.active().file("studio-templates"));
    static {BACKUPS.scheduleWithFixedDelay(()->{try{CURRENT.backupPending();}catch(Exception ignored){}},10,60,TimeUnit.SECONDS);}
    private final Path root;
    interface Cloud {void upload(String id,Path path)throws Exception;boolean download(String id,Path target)throws Exception;}
    private final Cloud cloud;
    private final boolean publication;
    private volatile Map<Integer,String> storeNames=Map.of();
    void configureStores(Map<Integer,String> names){storeNames=Map.copyOf(names);}
    StudioTemplateStore(Path root){this(root,new Cloud(){final OneDriveImageCloudProvider provider=new OneDriveImageCloudProvider();public void upload(String id,Path path)throws Exception{provider.uploadPrivateFile(id,path,"application/json");}public boolean download(String id,Path target)throws Exception{return provider.downloadPrivateFile(id,target);}});}
    StudioTemplateStore(Path root,Cloud cloud){this(root,cloud,false);}
    StudioTemplateStore(Path root,Cloud cloud,boolean publication){this.root=root.toAbsolutePath().normalize();this.cloud=cloud;this.publication=publication;}
    public static StudioTemplateStore current(){return CURRENT;}
    Path directory(int location)throws Exception{if(location<=0)throw new IllegalArgumentException("Invalid store");Path p=root.resolve("store-"+location);
        if(!Files.isDirectory(p)&&Files.isDirectory(root)){try(var paths=Files.list(root)){p=paths.filter(Files::isDirectory).filter(path->path.getFileName().toString().startsWith("store-"+location+"-")).sorted().findFirst().orElse(p);}}
        if(!Files.exists(p)&&storeNames.containsKey(location)){String name=storeNames.get(location).replaceAll("[^\\p{L}\\p{N} _-]","_").strip();if(name.length()>80)name=name.substring(0,80);p=root.resolve("store-"+location+"-"+(name.isEmpty()?"Store":name));}
        Files.createDirectories(p);return p;}
    private String id(String id){return UUID.fromString(id).toString();}
    private synchronized String identity()throws Exception{
        Files.createDirectories(root);Path file=root.resolve("library-id.txt");if(!Files.exists(file))atomic(file,UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));return UUID.fromString(Files.readString(file).strip()).toString();
    }
    String catalogBackupId(int location)throws Exception{return UUID.nameUUIDFromBytes(("SmartStudioTemplates/"+identity()+"/store-"+location).getBytes(StandardCharsets.UTF_8)).toString();}
    private String revisionBackupId(Path folder,String pending)throws Exception{return UUID.nameUUIDFromBytes((identity()+"/"+folder.getFileName()+"/"+pending).getBytes(StandardCharsets.UTF_8)).toString();}
    static String hash(byte[] data)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));}
    private static void atomic(Path path,byte[] bytes)throws Exception{
        Path temp=Files.createTempFile(path.getParent(),".write-",".tmp");
        try{Files.write(temp,bytes);Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    public synchronized JsonObject execute(int location,JsonObject request)throws Exception{
        Path folder=directory(location);String action=request.get("action").getAsString();
        if(action.equals("LIST")){
            JsonArray rows=new JsonArray();try(var files=Files.list(folder)){for(Path file:files.filter(p->p.getFileName().toString().endsWith(".head.json")).sorted().toList()){
                JsonObject row=JsonParser.parseString(Files.readString(file)).getAsJsonObject();row.addProperty("backedUp",Files.exists(folder.resolve(row.get("sha256").getAsString()+".backed-up")));rows.add(row);
            }}JsonObject response=new JsonObject();response.add("templates",rows);response.addProperty("recoveryCatalogId",catalogBackupId(location));response.addProperty("backupCatalogPending",Files.exists(folder.resolve("catalog.pending")));response.addProperty("backupError",Files.exists(folder.resolve("backup-error.txt")));response.addProperty("backupNote","OneDrive backups run in the background; pending revisions retry every minute.");return response;
        }
        String key=id(request.get("id").getAsString());Path head=folder.resolve(key+".head.json");
        if(action.equals("GET")){
            JsonObject row=JsonParser.parseString(Files.readString(head)).getAsJsonObject();
            if(request.has("sha256")&&!row.get("sha256").getAsString().equals(request.get("sha256").getAsString()))throw new Conflict("Template changed; refresh the library.");
            if(!publication)row.addProperty("content",Files.readString(folder.resolve(row.get("sha256").getAsString()+".sstbadge")));return row;
        }
        if(action.equals("DELETE")){
            JsonObject row=JsonParser.parseString(Files.readString(head)).getAsJsonObject();
            if(!request.has("expected")||!row.get("sha256").getAsString().equals(request.get("expected").getAsString()))throw new Conflict("Template changed. Refresh before deleting.");
            String assignment=row.has("assignmentVersion")?row.get("assignmentVersion").getAsString():"";
            if(!request.has("expectedAssignment")||!assignment.equals(request.get("expectedAssignment").getAsString()))throw new Conflict("Store assignment changed. Refresh before deleting.");
            Path deleted=folder.resolve("deleted");Files.createDirectories(deleted);
            JsonObject record=row.deepCopy();record.addProperty("deletedAt",java.time.Instant.now().toString());
            atomic(deleted.resolve(key+"-"+UUID.randomUUID()+".json"),JSON.toJson(record).getBytes(StandardCharsets.UTF_8));
            atomic(folder.resolve("catalog.pending"),UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            Files.delete(head);
            atomic(folder.resolve("catalog.pending"),UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            JsonObject result=new JsonObject();result.addProperty("deleted",true);return result;
        }
        if(action.equals("ASSIGN")){
            JsonObject row=JsonParser.parseString(Files.readString(head)).getAsJsonObject();
            String version=row.has("assignmentVersion")?row.get("assignmentVersion").getAsString():"";
            if(!version.equals(request.get("expectedAssignment").getAsString()))throw new Conflict("Store assignment changed. Refresh before assigning again.");
            int owner=request.get("ownerStoreId").getAsInt();if(owner<=0)throw new IllegalArgumentException("Invalid owner store.");
            JsonArray shares=request.getAsJsonArray("sharedStoreIds");if(shares.size()>1000)throw new IllegalArgumentException("Too many shared stores.");
            TreeSet<Integer> unique=new TreeSet<>();for(var entry:shares){int value=entry.getAsInt();if(value<=0)throw new IllegalArgumentException("Invalid shared store.");if(value!=owner)unique.add(value);}
            JsonArray normalized=new JsonArray();unique.forEach(normalized::add);
            row.addProperty("ownerStoreId",owner);row.add("sharedStoreIds",normalized);row.addProperty("assignmentVersion",UUID.randomUUID().toString());row.addProperty("updatedAt",java.time.Instant.now().toString());
            atomic(folder.resolve("catalog.pending"),UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
            atomic(head,JSON.toJson(row).getBytes(StandardCharsets.UTF_8));atomic(folder.resolve("catalog.pending"),UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));return row;
        }
        if(!action.equals("SAVE"))throw new IllegalArgumentException("Unknown template operation");
        String name=request.get("name").getAsString().strip();
        String content=request.get(publication?"contentBase64":"content").getAsString();
        byte[] bytes=publication?Base64.getDecoder().decode(content):content.getBytes(StandardCharsets.UTF_8);
        if(name.isBlank()||name.length()>(publication?200:160)||bytes.length>(publication?publishing.PublicationFiles.MAX_BYTES:8*1024*1024))throw new IllegalArgumentException("Template exceeds name or file size limits.");
        String contentDigest=null;
        if(publication){var design=publishing.PublicationFiles.read(bytes);publishing.PublicationTemplates.requireTemplate(design);if(!name.equals(design.title))throw new IllegalArgumentException("Template name does not match its archive.");contentDigest=publishing.PublicationTemplates.fingerprint(design);}
        else {
        JsonObject design=JsonParser.parseString(content).getAsJsonObject();
        if(!Set.of("formatVersion","name","widthMm","heightMm","elements","savePrintRecords").containsAll(design.keySet()))throw new IllegalArgumentException("Only portable template data may be shared.");
        double width=number(design,"widthMm",10,300),height=number(design,"heightMm",10,300);
        if(design.get("formatVersion").getAsInt()!=1||!design.has("elements")||design.getAsJsonArray("elements").size()>64)throw new IllegalArgumentException("Invalid badge template");
        // Only template data, never personal records or session photos, may be shared.
        for(JsonElement item:design.getAsJsonArray("elements")){
            JsonObject e=item.getAsJsonObject();
            if(!Set.of("photoId","label","type","text","field","font","color","image","x","y","width","height","fontMm","bold","italic","underline","strike","textOutline","textOutlineColor","textOutlineWidth","photoBackgroundEnabled","photoOutlineEnabled","photoBackgroundColor","photoOutlineColor").containsAll(e.keySet()))throw new IllegalArgumentException("Only reusable template fields may be shared.");
            String type=e.get("type").getAsString();if(!Set.of("TEXT","PHOTO","IMAGE","RECT","ELLIPSE","TRIANGLE","LINE").contains(type))throw new IllegalArgumentException("Invalid layer type");
            number(e,"x",-1000,1000);number(e,"y",-1000,1000);number(e,"width",.01,width);number(e,"height",.01,height);
            if(e.has("text")&&(e.get("text").isJsonNull()||e.get("text").getAsString().length()>1000))throw new IllegalArgumentException("Invalid layer text");
            if(e.has("textOutlineWidth"))number(e,"textOutlineWidth",0,5);
            if(e.has("textOutlineColor")&&!e.get("textOutlineColor").getAsString().matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Invalid text outline colour");
            if(e.has("image")&&!e.get("image").getAsString().isEmpty()&&!type.equals("IMAGE"))throw new IllegalArgumentException("Only artwork may embed image bytes");
        }
        }
        String expected=request.has("expected")?request.get("expected").getAsString():"";
        String actual=Files.exists(head)?JsonParser.parseString(Files.readString(head)).getAsJsonObject().get("sha256").getAsString():"";
        if(!expected.equals(actual))throw new Conflict("Another computer saved this template. Reopen the latest version before saving.");
        String digest=hash(bytes);Path revision=folder.resolve(digest+(publication?".sspub":".sstbadge"));if(!Files.exists(revision))atomic(revision,bytes);
        JsonObject row=new JsonObject();row.addProperty("id",key);row.addProperty("name",name);row.addProperty("sha256",digest);row.addProperty("kind",publication?(request.has("kind")?request.get("kind").getAsString():"PUBLICATION"):"BADGE");row.addProperty("size",bytes.length);if(publication)row.addProperty("contentSha256",contentDigest);row.addProperty("updatedAt",java.time.Instant.now().toString());
        JsonObject prior=Files.exists(head)?JsonParser.parseString(Files.readString(head)).getAsJsonObject():new JsonObject();
        row.addProperty("createdStoreId",prior.has("createdStoreId")?prior.get("createdStoreId").getAsInt():location);
        row.addProperty("ownerStoreId",prior.has("ownerStoreId")?prior.get("ownerStoreId").getAsInt():location);
        row.add("sharedStoreIds",prior.has("sharedStoreIds")?prior.getAsJsonArray("sharedStoreIds").deepCopy():new JsonArray());
        row.addProperty("assignmentVersion",prior.has("assignmentVersion")?prior.get("assignmentVersion").getAsString():"");
        // Queue the full revision envelope before advancing the head, so crashes cannot lose backup work.
        JsonObject backup=row.deepCopy();backup.addProperty(publication?"contentBase64":"content",content);atomic(folder.resolve(key+"-"+digest+".pending.json"),JSON.toJson(backup).getBytes(StandardCharsets.UTF_8));
        atomic(head,JSON.toJson(row).getBytes(StandardCharsets.UTF_8));atomic(folder.resolve("catalog.pending"),UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));return row;
    }
    synchronized Map<String,Object> readChunk(int location,String key,String expected,int offset)throws Exception{
        if(!publication)throw new IllegalArgumentException("Archive reads require a publication library.");
        Path folder=directory(location);JsonObject row=JsonParser.parseString(Files.readString(folder.resolve(id(key)+".head.json"))).getAsJsonObject();
        String digest=row.get("sha256").getAsString();if(!digest.equals(expected))throw new Conflict("Template changed; refresh before downloading.");
        Path file=folder.resolve(digest+".sspub");long size=Files.size(file);
        if(offset<0||offset>=size||size>publishing.PublicationFiles.MAX_BYTES)throw new IllegalArgumentException("Invalid template offset.");
        try(var input=java.nio.channels.FileChannel.open(file,StandardOpenOption.READ)){
            var bytes=java.nio.ByteBuffer.allocate((int)Math.min(524288,size-offset));input.position(offset);while(bytes.hasRemaining())if(input.read(bytes)<0)throw new java.io.IOException("Truncated template.");
            return Map.of("bytes",Base64.getEncoder().encodeToString(bytes.array()),"size",size,"sha256",digest);
        }
    }
    private static double number(JsonObject value,String name,double min,double max){
        if(!value.has(name))throw new IllegalArgumentException("Missing template dimension");double number=value.get(name).getAsDouble();if(!Double.isFinite(number)||number<min||number>max)throw new IllegalArgumentException("Invalid template dimension");return number;
    }
    void backupPending()throws Exception{
        if(!Files.isDirectory(root))return;
        try(var locations=Files.list(root)){for(Path folder:locations.filter(Files::isDirectory).toList()){
            try(var files=Files.list(folder)){for(Path pending:files.filter(p->p.getFileName().toString().endsWith(".pending.json")).toList()){
                try{
                    byte[] bytes=Files.readAllBytes(pending);String backupId=revisionBackupId(folder,pending.getFileName().toString());
                    cloud.upload(backupId,pending);
                    Path check=Files.createTempFile(folder,".verify-",".tmp");try{if(!cloud.download(backupId,check)||!hash(bytes).equals(hash(Files.readAllBytes(check))))throw new java.io.IOException("Backup verification failed");}finally{Files.deleteIfExists(check);}
                    JsonObject row=JsonParser.parseString(new String(bytes,StandardCharsets.UTF_8)).getAsJsonObject();atomic(folder.resolve(row.get("sha256").getAsString()+".backed-up"),backupId.getBytes(StandardCharsets.UTF_8));Files.deleteIfExists(pending);Files.deleteIfExists(folder.resolve("backup-error.txt"));
                }catch(Exception ex){atomic(folder.resolve("backup-error.txt"),"OneDrive backup pending. Check the store server's OneDrive configuration.".getBytes(StandardCharsets.UTF_8));break;}
            }}
            if(Files.exists(folder.resolve("catalog.pending"))){
                try{
                    String catalogGeneration=Files.readString(folder.resolve("catalog.pending"));
                    JsonArray entries=new JsonArray();try(var heads=Files.list(folder)){for(Path head:heads.filter(p->p.getFileName().toString().endsWith(".head.json")).toList()){
                        JsonObject row=JsonParser.parseString(Files.readString(head)).getAsJsonObject();if(!Files.exists(folder.resolve(row.get("sha256").getAsString()+".backed-up")))throw new java.io.IOException("Revisions still pending");
                        String pendingName=row.get("id").getAsString()+"-"+row.get("sha256").getAsString()+".pending.json";
                        row.addProperty("backupId",revisionBackupId(folder,pendingName));entries.add(row);
                    }}
                    String catalogId=catalogBackupId(Integer.parseInt(folder.getFileName().toString().substring(6).split("-",2)[0]));
                    Path catalog=folder.resolve("backup-catalog.json");atomic(catalog,entries.toString().getBytes(StandardCharsets.UTF_8));cloud.upload(catalogId,catalog);
                    Path check=Files.createTempFile(folder,".catalog-check-",".tmp");try{if(!cloud.download(catalogId,check)||!hash(Files.readAllBytes(catalog)).equals(hash(Files.readAllBytes(check))))throw new java.io.IOException("Catalog backup verification failed");}finally{Files.deleteIfExists(check);}
                    synchronized(this){if(Files.exists(folder.resolve("catalog.pending"))&&Files.readString(folder.resolve("catalog.pending")).equals(catalogGeneration))Files.deleteIfExists(folder.resolve("catalog.pending"));}
                }catch(Exception ignored){}
            }
        }}
    }
    public static final class Conflict extends java.io.IOException{public Conflict(String message){super(message);}}
}
