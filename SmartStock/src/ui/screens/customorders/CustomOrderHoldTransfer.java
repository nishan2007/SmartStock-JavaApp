package ui.screens.customorders;

import com.google.gson.*;
import services.*;
import java.nio.file.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Chunked transfer keeps register memory bounded and verifies every restored file. */
final class CustomOrderHoldTransfer {
    private static final Gson JSON=LanJson.create();
    static final int CHUNK=524288;
    record Prepared(String id,String name,CustomOrderHeldDraft draft,Map<String,Path> files){}
    record Restored(CustomOrderHeldDraft draft,Map<String,Path> files){}
    static CustomOrderDataService.OrderLineRequest withoutApprovals(CustomOrderDataService.OrderLineRequest line){
        var data=JSON.toJsonTree(line).getAsJsonObject();data.remove("lineDiscountApprovalToken");data.remove("priceOverrideApprovalToken");
        return JSON.fromJson(data,CustomOrderDataService.OrderLineRequest.class);
    }
    static JsonObject request(String action,String id){var b=new JsonObject();b.addProperty("action",action);if(id!=null)b.addProperty("id",id);return b;}
    static JsonObject call(String action,String id)throws Exception{return LanApiClient.customOrderHold(request(action,id),null);}
    static Prepared prepare(String id,String name,CustomOrderHeldDraft draft,Map<String,Path> paths)throws Exception{
        if(paths.size()>100||draft.lines().size()>5000)throw new IOException("A held cart may contain up to 5,000 items and 100 attachments.");
        var attachments=new ArrayList<CustomOrderHeldDraft.Attachment>();
        long total=0;
        for(var entry:paths.entrySet()){
            var path=entry.getValue();long size=Files.size(path);if(size>104857600)throw new IOException("Held-cart attachments must be 100 MB or smaller.");
            total+=size;if(total>536870912)throw new IOException("A held cart may contain up to 512 MB of attachments.");
            attachments.add(new CustomOrderHeldDraft.Attachment(entry.getKey(),path.getFileName().toString(),size,hash(path)));
        }
        var safeLines=draft.lines().stream().map(line->new CustomOrderHeldDraft.Line(withoutApprovals(line.request()),line.item(),line.variant(),line.files())).toList();
        var complete=new CustomOrderHeldDraft(draft.customer(),draft.pricePending(),draft.dueEnabled(),draft.dueDate(),draft.notes(),draft.paymentMethod(),draft.paymentReference(),draft.deposit(),draft.depositOverrideReason(),safeLines,attachments);
        if(JSON.toJson(complete).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>1_500_000)throw new IOException("This cart contains too much design detail to hold. Split it into smaller carts.");
        return new Prepared(id,name,complete,Map.copyOf(paths));
    }
    static void hold(Prepared p)throws Exception{
        var b=request("CREATE",p.id());b.addProperty("name",p.name());b.add("draft",JSON.toJsonTree(p.draft()));
        if("HELD".equals(LanApiClient.customOrderHold(b,null).get("status").getAsString()))return;
        for(var attachment:p.draft().attachments()){
            Path path=p.files().get(attachment.id());if(Files.size(path)!=attachment.size()||!hash(path).equals(attachment.sha256()))throw new IOException("An attachment changed. Restore the original file before retrying Hold Cart.");
            try(var in=Files.newInputStream(path)){
                int chunk=0;byte[] bytes;while((bytes=in.readNBytes(CHUNK)).length>0){var upload=request("UPLOAD",p.id());upload.addProperty("fileId",attachment.id());upload.addProperty("chunk",chunk++);upload.addProperty("bytes",Base64.getEncoder().encodeToString(bytes));LanApiClient.customOrderHold(upload,null);}
            }
        }
        call("FINISH",p.id());
    }
    static Restored download(String id)throws Exception{
        var draft=JSON.fromJson(call("LOAD",id).get("draft"),CustomOrderHeldDraft.class);
        var paths=new HashMap<String,Path>();Path directory=Files.createTempDirectory("smartstock-custom-hold-");utils.SecureFilePermissions.restrictDirectoryToOwner(directory);
        try{
            for(var f:draft.attachments()){
                if(f.name().contains("/")||f.name().contains("\\")||f.name().equals(".")||f.name().equals(".."))throw new IOException("Invalid held attachment name.");
                Path folder=directory.resolve(UUID.fromString(f.id()).toString());Files.createDirectory(folder);utils.SecureFilePermissions.restrictDirectoryToOwner(folder);Path path=folder.resolve(f.name()).normalize();
                if(!path.startsWith(folder)||path.equals(folder))throw new IOException("Invalid held attachment path.");
                try(var out=Files.newOutputStream(path)){
                    for(int chunk=0;(long)chunk*CHUNK<f.size();chunk++){var b=request("DOWNLOAD",id);b.addProperty("fileId",f.id());b.addProperty("chunk",chunk);byte[] bytes=Base64.getDecoder().decode(LanApiClient.customOrderHold(b,null).get("bytes").getAsString());if(bytes.length!=Math.min(CHUNK,f.size()-(long)chunk*CHUNK))throw new IOException("Incomplete held attachment.");out.write(bytes);}
                }
                utils.SecureFilePermissions.restrictFileToOwner(path);if(Files.size(path)!=f.size()||!hash(path).equals(f.sha256()))throw new IOException("Held attachment failed its checksum check.");paths.put(f.id(),path);
            }
            return new Restored(draft,Map.copyOf(paths));
        }catch(Exception e){try(var walk=Files.walk(directory)){for(Path path:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}throw e;}
    }
    static String hash(Path path)throws Exception{
        var hash=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)hash.update(buffer,0,n);}return HexFormat.of().formatHex(hash.digest());
    }
}
