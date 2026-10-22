package services;

import com.google.gson.*;
import java.util.*;

/** Store folders are views over stable server revisions; sharing never duplicates artwork. */
public final class StudioTemplateLibraryRouter {
    interface Store { Object execute(int storageStore,JsonObject request)throws Exception; }
    private final Map<Integer,String> stores;
    private final Set<Integer> allowed;
    private final int current;
    private final Store storage;
    StudioTemplateLibraryRouter(Map<Integer,String> stores,Set<Integer> allowed,int current,Store storage){this.stores=Map.copyOf(stores);this.allowed=Set.copyOf(allowed);this.current=current;this.storage=storage;}
    private int owner(JsonObject row,int fallback){return row.has("ownerStoreId")?row.get("ownerStoreId").getAsInt():fallback;}
    private boolean visible(JsonObject row,int source,int view){
        if(owner(row,source)==view)return true;
        if(row.has("sharedStoreIds"))for(var value:row.getAsJsonArray("sharedStoreIds"))if(value.getAsInt()==view)return true;
        return false;
    }
    private void permitted(boolean condition){if(!condition)throw new SecurityException("This staff account cannot access that store's templates.");}
    private JsonObject read(int source,JsonObject request)throws Exception{
        JsonObject get=new JsonObject();get.addProperty("action","GET");get.add("id",request.get("id"));
        return (JsonObject)storage.execute(source,get);
    }
    private void decorate(JsonObject row,int source){
        int owner=owner(row,source);row.addProperty("storageStoreId",source);row.addProperty("ownerStoreId",owner);
        row.addProperty("ownerStoreName",stores.getOrDefault(owner,"Store "+owner));
        row.addProperty("createdStoreId",row.has("createdStoreId")?row.get("createdStoreId").getAsInt():source);
        if(!row.has("sharedStoreIds"))row.add("sharedStoreIds",new JsonArray());
        if(!row.has("assignmentVersion"))row.addProperty("assignmentVersion","");
    }
    public Object execute(JsonObject request)throws Exception{
        String action=request.get("action").getAsString();
        if(action.equals("LIST")){
            int view=request.has("viewStoreId")?request.get("viewStoreId").getAsInt():current;
            permitted(view==0||allowed.contains(view));JsonArray rows=new JsonArray(),folders=new JsonArray();
            stores.entrySet().stream().filter(e->allowed.contains(e.getKey())).sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER)).forEach(e->{JsonObject f=new JsonObject();f.addProperty("id",e.getKey());f.addProperty("name",e.getValue());folders.add(f);});
            JsonObject result=new JsonObject();boolean pending=false,error=false;
            for(int source:stores.keySet()){
                JsonObject list=new JsonObject();list.addProperty("action","LIST");JsonObject catalog=(JsonObject)storage.execute(source,list);
                boolean relevant=false;
                for(var value:catalog.getAsJsonArray("templates")){
                    JsonObject row=value.getAsJsonObject();boolean show=view==0?allowed.stream().anyMatch(id->visible(row,source,id)):visible(row,source,view);
                    if(show&&(request.has("viewStoreId")||request.has("storeFoldersSupported")||source==current)){decorate(row,source);rows.add(row);relevant=true;}
                }
                if(relevant){pending|=catalog.has("backupCatalogPending")&&catalog.get("backupCatalogPending").getAsBoolean();error|=catalog.has("backupError")&&catalog.get("backupError").getAsBoolean();}
                if(source==current){result=catalog.deepCopy();}
            }
            result.add("templates",rows);result.add("stores",folders);result.addProperty("defaultStoreId",current);result.addProperty("storeFoldersSupported",true);
            result.addProperty("backupCatalogPending",pending);result.addProperty("backupError",error);return result;
        }
        int source=request.has("storageStoreId")?request.get("storageStoreId").getAsInt():current;
        permitted(stores.containsKey(source));
        if(action.equals("GET")||action.equals("READ")){
            JsonObject row=read(source,request);permitted(allowed.stream().anyMatch(id->visible(row,source,id)));
        }else if(action.equals("DELETE")){
            JsonObject row=read(source,request);permitted(allowed.contains(owner(row,source)));
        }else if(action.equals("ASSIGN")){
            JsonObject row=read(source,request);permitted(allowed.contains(owner(row,source)));
            permitted(allowed.contains(request.get("ownerStoreId").getAsInt()));
            for(var value:request.getAsJsonArray("sharedStoreIds"))permitted(allowed.contains(value.getAsInt()));
        }else if(action.equals("CHUNK")||action.equals("FINISH")){
            JsonObject info=request.deepCopy();info.addProperty("action","UPLOAD_META");info=(JsonObject)storage.execute(source,info);
            if(info.get("expected").getAsString().isEmpty())permitted(allowed.contains(source));
            else {JsonObject row=read(source,info);permitted(allowed.stream().anyMatch(id->visible(row,source,id)));}
        }else if(action.equals("ABORT")){
            // Only the authenticated upload owner can discard their staging files.
        }else if((action.equals("SAVE")||action.equals("BEGIN"))&&request.has("expected")&&!request.get("expected").getAsString().isEmpty()){
            JsonObject row=read(source,request);permitted(allowed.stream().anyMatch(id->visible(row,source,id)));
        }else permitted(allowed.contains(source));
        Object result=storage.execute(source,request);if(result instanceof JsonObject row&&row.has("id"))decorate(row,source);return result;
    }
}
