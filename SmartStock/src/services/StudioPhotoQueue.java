package services;

import com.google.gson.*;
import data.EnvironmentProfile;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import utils.SecureFilePermissions;

/** Local store authority for personal photos. Never copied into template/cloud backups. */
final class StudioPhotoQueue {
    private static final class Runtime {
        static final StudioPhotoQueue CURRENT=new StudioPhotoQueue(EnvironmentProfile.active().file("studio-photo-queue"),Clock.systemUTC());
        static final ScheduledExecutorService CLEANER=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"studio-photo-queue-cleanup");t.setDaemon(true);return t;});
        static final ScheduledExecutorService PREPARE=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"studio-photo-queue-prepare");t.setDaemon(true);t.setPriority(Thread.MIN_PRIORITY);return t;});
        static {
            CLEANER.scheduleWithFixedDelay(()->{try{CURRENT.cleanup();}catch(Exception e){System.err.println("Photo queue cleanup will retry: "+e.getClass().getSimpleName());}},0,60,TimeUnit.SECONDS);
            PREPARE.scheduleWithFixedDelay(()->{try{
                if(StudioRemovalRuntime.canPrepare()){
                    if(!CURRENT.prepareNext(StudioBackgroundService::prepareQueuedPhoto))StudioBackgroundService.prewarmBest();
                }
            }catch(Exception e){System.err.println("Photo preparation will retry: "+e.getClass().getSimpleName());}},30,3,TimeUnit.SECONDS);
        }
    }
    private final Path root;
    private final Clock clock;
    private boolean preparing;
    StudioPhotoQueue(Path root,Clock clock){this.root=root.toAbsolutePath().normalize();this.clock=clock;}
    static StudioPhotoQueue current(){return Runtime.CURRENT;}
    static void prepareUploadedPhoto(){
        Runtime.PREPARE.execute(()->{try{
            if(StudioRemovalRuntime.canPrepare())Runtime.CURRENT.prepareNext(StudioBackgroundService::prepareQueuedPhoto);
        }catch(Exception e){System.err.println("Photo preparation will retry: "+e.getClass().getSimpleName());}});
    }
    private Path directory(int location)throws Exception{
        if(location<1)throw new IllegalArgumentException("Invalid store.");
        Path folder=root.resolve("store-"+location);Files.createDirectories(folder);SecureFilePermissions.restrictDirectoryToOwner(root);SecureFilePermissions.restrictDirectoryToOwner(folder);return folder;
    }
    private static String id(String value){if(value==null)throw new IllegalArgumentException("A photo ID is required.");return UUID.fromString(value).toString();}
    private static JsonObject read(Path file)throws Exception{return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}
    private static void atomic(Path file,byte[] bytes)throws Exception{
        Path temp=Files.createTempFile(file.getParent(),".pending-",".tmp");
        try{Files.write(temp,bytes);SecureFilePermissions.restrictFileToOwner(temp);Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    private static void write(Path file,JsonObject value)throws Exception{atomic(file,value.toString().getBytes(StandardCharsets.UTF_8));}
    synchronized JsonObject issue(int location,int user)throws Exception{
        cleanup();String token=LanSecurity.randomToken();Path folder=directory(location);
        JsonObject grant=new JsonObject();grant.addProperty("expiresAt",clock.millis()+Duration.ofHours(12).toMillis());grant.addProperty("issuedBy",user);
        write(folder.resolve("grant-"+LanSecurity.sha256(token)+".json"),grant);
        JsonObject result=new JsonObject();result.addProperty("token",token);result.addProperty("expiresAt",grant.get("expiresAt").getAsLong());return result;
    }
    synchronized JsonObject pair(int location,int user,String name)throws Exception{
        cleanup();if(name==null||name.isBlank()||name.strip().length()>80)throw new IllegalArgumentException("Enter a phone name of at most 80 characters.");
        Path folder=directory(location);int active=0;try(var files=Files.list(folder)){for(Path file:files.filter(f->f.getFileName().toString().startsWith("grant-")).toList())if(read(file).get("expiresAt").getAsLong()==0)active++;}
        if(active>=30)throw new IllegalStateException("Revoke an unused phone before pairing another (30 phones per store).");
        String token=LanSecurity.randomToken();JsonObject grant=new JsonObject();grant.addProperty("id",UUID.randomUUID().toString());grant.addProperty("name",name.strip());grant.addProperty("issuedAt",clock.millis());grant.addProperty("issuedBy",user);grant.addProperty("expiresAt",0L);
        write(folder.resolve("grant-"+LanSecurity.sha256(token)+".json"),grant);JsonObject result=grant.deepCopy();result.addProperty("token",token);result.addProperty("persistent",true);return result;
    }
    private JsonArray phones(Path folder)throws Exception{
        JsonArray result=new JsonArray();try(var files=Files.list(folder)){for(Path file:files.filter(f->f.getFileName().toString().startsWith("grant-")&&f.getFileName().toString().endsWith(".json")).sorted().toList()){
            JsonObject row=read(file);if(row.get("expiresAt").getAsLong()==0){JsonObject phone=new JsonObject();for(String key:List.of("id","name","issuedAt","issuedBy"))phone.add(key,row.get(key));result.add(phone);}
        }}return result;
    }
    private Path requireGrant(int location,String token)throws Exception{
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new SecurityException("This photo queue link is invalid.");
        Path folder=directory(location),grant=folder.resolve("grant-"+LanSecurity.sha256(token)+".json");
        if(!Files.isRegularFile(grant)||(read(grant).get("expiresAt").getAsLong()>0&&read(grant).get("expiresAt").getAsLong()<=clock.millis()))throw new SecurityException("This photo queue link expired. Scan a new link from SmartStudio.");
        return folder;
    }
    synchronized JsonObject upload(int location,String token,String uploadId,String label,byte[] bytes)throws Exception{
        return upload(location,token,uploadId,label,bytes,true);
    }
    synchronized JsonObject upload(int location,String token,String uploadId,String label,byte[] bytes,boolean removeBackground)throws Exception{
        Path folder=requireGrant(location,token);cleanup();String key=id(uploadId);
        if(Files.exists(folder.resolve(key+".tombstone")))throw new IllegalArgumentException("This upload was already removed. Take a new photo.");
        if(label==null)label="";label=label.strip();if(label.length()>80)throw new IllegalArgumentException("Use a name of at most 80 characters.");
        if(bytes.length>2*1024*1024)throw new IllegalArgumentException("Choose a photo of at most 2 MB.");
        StudioBackgroundService.validate(bytes);
        String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));Path meta=folder.resolve(key+".json");
        if(Files.exists(meta)){
            JsonObject previous=read(meta);
            if(!hash.equals(previous.get("sha256").getAsString())||!label.equals(previous.get("name").getAsString()))throw new IllegalArgumentException("That upload was already used for another photo. Take a new photo.");
            return previous;
        }
        long count;try(var paths=Files.list(folder)){count=paths.filter(p->p.getFileName().toString().matches("[a-f0-9-]{36}\\.json")).count();}
        if(count>=300)throw new IllegalStateException("The photo queue is full. Finish or remove older photos before uploading more.");
        JsonObject row=new JsonObject();row.addProperty("id",key);row.addProperty("name",label);row.addProperty("createdAt",clock.millis());row.addProperty("sha256",hash);row.addProperty("printedAt",0L);row.addProperty("deleteAfter",0L);
        row.addProperty("backgroundState",removeBackground?"pending":"disabled");
        atomic(folder.resolve(key+".photo"),bytes);write(meta,row);return row;
    }
    synchronized JsonObject execute(int location,JsonObject request)throws Exception{
        cleanup();Path folder=directory(location);String action=StorefrontService.text(request,"action");
        if(action.equals("LIST")){
            List<JsonObject> entries=new ArrayList<>();try(var paths=Files.list(folder)){
                for(Path file:paths.filter(p->p.getFileName().toString().matches("[a-f0-9-]{36}\\.json")).toList())entries.add(read(file));
            }
            entries.sort(Comparator.comparingLong(row->row.get("createdAt").getAsLong()));
            JsonArray photos=new JsonArray();entries.forEach(photos::add);JsonObject result=new JsonObject();result.add("photos",photos);return result;
        }
        if(action.equals("PHONES")){JsonObject result=new JsonObject();result.add("phones",phones(folder));return result;}
        if(action.equals("REVOKE_PHONE")){
            String phone=id(StorefrontService.text(request,"id"));boolean found=false;
            try(var files=Files.list(folder)){for(Path file:files.filter(f->f.getFileName().toString().startsWith("grant-")&&f.getFileName().toString().endsWith(".json")).toList()){
                JsonObject row=read(file);if(row.has("id")&&row.get("id").getAsString().equals(phone)){Files.delete(file);found=true;}
            }}
            if(!found)throw new IllegalArgumentException("This phone is no longer paired at this store.");JsonObject result=new JsonObject();result.addProperty("revoked",true);return result;
        }
        String key=id(StorefrontService.text(request,"id"));Path meta=folder.resolve(key+".json"),photo=folder.resolve(key+".photo");
        if(!Files.isRegularFile(meta))throw new IllegalArgumentException("This photo was removed or expired. Refresh the queue.");
        JsonObject row=read(meta);
        switch(action){
            case "GET" -> {
                boolean original=request.has("original")&&request.get("original").getAsBoolean();
                Path prepared=folder.resolve(key+".cutout.png");
                boolean ready=!original&&row.has("backgroundState")&&row.get("backgroundState").getAsString().equals("ready")&&Files.isRegularFile(prepared);
                row.addProperty("usingCutout",ready);row.addProperty("bytesBase64",Base64.getEncoder().encodeToString(Files.readAllBytes(ready?prepared:photo)));return row;
            }
            case "PREPARE" -> {row.addProperty("backgroundState","pending");write(meta,row);return row;}
            case "STATUS" -> {return row;}
            case "PRINTED" -> {
                // Reprints and retries must never extend the original deletion deadline.
                if(row.get("printedAt").getAsLong()==0){row.addProperty("printedAt",clock.millis());row.addProperty("deleteAfter",clock.millis()+Duration.ofHours(24).toMillis());write(meta,row);}return row;
            }
            case "DELETE" -> {remove(folder,key,meta);JsonObject result=new JsonObject();result.addProperty("deleted",true);return result;}
            default -> throw new IllegalArgumentException("Unknown photo queue action.");
        }
    }
    private void remove(Path folder,String key,Path metadata)throws Exception{
        JsonObject tombstone=new JsonObject();tombstone.addProperty("expiresAt",clock.millis()+Duration.ofHours(24).toMillis());write(folder.resolve(key+".tombstone"),tombstone);
        Files.deleteIfExists(folder.resolve(key+".photo"));Files.deleteIfExists(metadata);
        Files.deleteIfExists(folder.resolve(key+".cutout.png"));
    }
    interface BackgroundProcessor {byte[] remove(byte[] original)throws Exception;}
    private record Pending(Path folder,String id,String hash,byte[] original){}
    private synchronized Pending next()throws Exception{
        cleanup();if(!Files.isDirectory(root))return null;
        List<Path> candidates=new ArrayList<>();try(var stores=Files.list(root)){
            for(Path folder:stores.filter(Files::isDirectory).sorted().toList())try(var paths=Files.list(folder)){
                candidates.addAll(paths.filter(p->p.getFileName().toString().matches("[a-f0-9-]{36}\\.json")).toList());
            }
        }
        candidates.sort(Comparator.comparingLong(p->{try{return read(p).get("createdAt").getAsLong();}catch(Exception ex){return Long.MAX_VALUE;}}));
        for(Path metadata:candidates){JsonObject row=read(metadata);String state=row.has("backgroundState")?row.get("backgroundState").getAsString():"pending";
            if(row.get("printedAt").getAsLong()>0||!Set.of("pending","processing").contains(state))continue;
            String key=id(row.get("id").getAsString());byte[] bytes=Files.readAllBytes(metadata.getParent().resolve(key+".photo"));
            row.addProperty("backgroundState","processing");write(metadata,row);
            return new Pending(metadata.getParent(),key,row.get("sha256").getAsString(),bytes);
        }return null;
    }
    boolean prepareNext(BackgroundProcessor processor)throws Exception{
        synchronized(this){if(preparing)return false;preparing=true;}
        try{
        Pending job=next();if(job==null)return false;byte[] cutout=null;String state="ready";
        try{cutout=processor.remove(job.original());StudioBackgroundService.validate(cutout);}
        catch(StudioBackgroundService.BusyException ex){state="pending";}
        catch(CatalogStudioImageProcessor.ModelUnavailableException ex){state="pending";}
        catch(Exception|LinkageError ex){state="failed";System.err.println("Queued background removal failed: "+ex.getClass().getSimpleName());}
        synchronized(this){
            cleanup();Path metadata=job.folder().resolve(job.id()+".json");
            // A slow inference must never resurrect a deleted/expired photo.
            if(!Files.isRegularFile(metadata))return true;JsonObject row=read(metadata);
            if(!job.hash().equals(row.get("sha256").getAsString()))return true;
            if(state.equals("ready"))atomic(job.folder().resolve(job.id()+".cutout.png"),cutout);
            row.addProperty("backgroundState",state);if(state.equals("ready"))row.addProperty("backgroundQuality","BEST");write(metadata,row);
        }return true;
        }finally{synchronized(this){preparing=false;}}
    }
    synchronized void cleanup()throws Exception{
        if(!Files.isDirectory(root))return;
        try(var stores=Files.list(root)){
            for(Path folder:stores.filter(Files::isDirectory).toList())try(var files=Files.list(folder)){
                for(Path file:files.filter(p->p.getFileName().toString().endsWith(".json")).toList()){
                    JsonObject row=read(file);String filename=file.getFileName().toString();
                    if(filename.startsWith("grant-")){if(row.get("expiresAt").getAsLong()>0&&row.get("expiresAt").getAsLong()<=clock.millis())Files.deleteIfExists(file);continue;}
                    if(row.has("deleteAfter")&&row.get("deleteAfter").getAsLong()>0&&row.get("deleteAfter").getAsLong()<=clock.millis()){
                        remove(folder,id(row.get("id").getAsString()),file);
                    }
                }
                // Remove an image left behind by an interrupted upload before metadata committed.
                try(var photos=Files.list(folder)){for(Path photo:photos.filter(p->p.getFileName().toString().endsWith(".photo")).toList()){
                    String key=photo.getFileName().toString().replace(".photo","");
                    if(!Files.exists(folder.resolve(key+".json")))Files.deleteIfExists(photo);
                }}
                try(var temporary=Files.list(folder)){for(Path file:temporary.toList()){
                    String name=file.getFileName().toString();
                    if(name.startsWith(".pending-")&&name.endsWith(".tmp"))Files.deleteIfExists(file);
                    else if(name.endsWith(".cutout.png")&&!Files.exists(folder.resolve(name.replace(".cutout.png",".json"))))Files.deleteIfExists(file);
                    else if(name.endsWith(".tombstone")&&read(file).get("expiresAt").getAsLong()<=clock.millis())Files.deleteIfExists(file);
                }}
            }
        }
    }
}
