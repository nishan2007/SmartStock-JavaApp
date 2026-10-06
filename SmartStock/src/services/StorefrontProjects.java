package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import java.util.Base64;
import static services.StorefrontService.*;

/** Staff-curated portfolio. Never copies customer text or order pricing into public fields. */
final class StorefrontProjects {
    private StorefrontProjects() { }

    static JsonArray staffState(Connection c,int location)throws SQLException {
        JsonArray projects=rows(c,"""
            SELECT project_id AS id,source_custom_order_id AS "sourceOrderId",status,title,summary,
                   category,materials,production_method AS "productionMethod",service_slug AS "serviceSlug",customization,tags,
                   starting_price AS "startingPrice",product_ids AS "productIds",
                   cover_reference AS "coverReference",updated_at AS "updatedAt"
            FROM storefront.projects WHERE location_id=? ORDER BY updated_at DESC LIMIT 300
            """,location);
        java.util.Map<String,JsonObject> byId=new java.util.HashMap<>();
        for(var item:projects){var project=item.getAsJsonObject();project.add("gallery",new JsonArray());byId.put(text(project,"id"),project);}
        for(var item:rows(c,"""
            SELECT media_id AS id,project_id AS "projectId",role,caption,position
            FROM storefront.project_media WHERE location_id=? ORDER BY project_id,position
            """,location)){
            var media=item.getAsJsonObject();var project=byId.get(text(media,"projectId"));
            if(project!=null)project.getAsJsonArray("gallery").add(media);
        }
        return projects;
    }
    static JsonObject share(Connection c,int location,UUID project,String origin)throws SQLException{
        if(origin==null||origin.isBlank())throw new IllegalArgumentException("Configure the public website address on the store server first.");
        JsonObject row=one(rows(c,"""
            SELECT title FROM storefront.projects
            WHERE project_id=? AND location_id=? AND status IN ('PUBLISHED','FEATURED')
            """,project,location));
        if(row.entrySet().isEmpty())throw new IllegalArgumentException("Publish this project before preparing a social post.");
        String url=origin.replaceAll("/$","")+"/shop/projects/"+location+"/"+project;
        JsonObject result=new JsonObject();result.addProperty("url",url);
        result.addProperty("caption","Made at Deckers: "+text(row,"title")+"\nSee the project and make something like it: "+url);
        return result;
    }
    static UUID draft(Connection c,int location,long order)throws SQLException {
        if(order<=0)throw new IllegalArgumentException("Select a completed custom order.");
        if(rows(c,"SELECT 1 FROM custom_orders WHERE custom_order_id=? AND location_id=? AND status IN ('COMPLETED','DELIVERED') FOR UPDATE",order,location).isEmpty())
            throw new IllegalArgumentException("Select a completed custom order at this store.");
        UUID id=UUID.randomUUID();
        return UUID.fromString(text(one(rows(c,"""
            INSERT INTO storefront.projects(project_id,location_id,source_custom_order_id) VALUES(?,?,?)
            ON CONFLICT(location_id,source_custom_order_id) DO UPDATE SET updated_at=storefront.projects.updated_at
            RETURNING project_id AS id
            """,id,location,order)),"id"));
    }
    static void save(Connection c,int location,JsonObject body)throws SQLException {
        UUID id=UUID.fromString(text(body,"projectId"));
        String title=field(body,"title",150),summary=field(body,"summary",600),category=field(body,"category",80),
            materials=field(body,"materials",300),method=field(body,"productionMethod",300),customization=field(body,"customization",300),serviceSlug=field(body,"serviceSlug",80);
        if(!serviceSlug.isBlank()&&!StorefrontServicePage.has(serviceSlug))throw new IllegalArgumentException("Choose a supported related service.");
        JsonObject current=one(rows(c,"SELECT status FROM storefront.projects WHERE project_id=? AND location_id=? FOR UPDATE",id,location));
        if(current.entrySet().isEmpty()||"ARCHIVED".equals(text(current,"status")))throw new IllegalArgumentException("Project draft not found.");
        if(("PUBLISHED".equals(text(current,"status"))||"FEATURED".equals(text(current,"status")))
                &&(title.isBlank()||summary.isBlank()||category.isBlank()))
            throw new IllegalArgumentException("Published projects need a title, description, and category. Return this project to Draft before clearing them.");
        JsonArray tags=body.has("tags")&&body.get("tags").isJsonArray()?body.getAsJsonArray("tags"):new JsonArray();
        if(tags.size()>12)throw new IllegalArgumentException("Use at most 12 tags.");
        for(var tag:tags)if(!tag.isJsonPrimitive()||!tag.getAsJsonPrimitive().isString()||tag.getAsString().trim().length()>40)
            throw new IllegalArgumentException("Each tag must be short text.");
        JsonArray productIds=null;
        if(body.has("productIds")){
            if(!body.get("productIds").isJsonArray())throw new IllegalArgumentException("Choose valid related products.");
            productIds=body.getAsJsonArray("productIds");
            if(productIds.size()>12)throw new IllegalArgumentException("Choose at most 12 products.");
            java.util.Set<Integer> unique=new java.util.HashSet<>();
            for(var value:productIds){
                int product;
                try{product=value.getAsInt();if(!value.getAsString().equals(Integer.toString(product)))throw new Exception();}
                catch(Exception e){throw new IllegalArgumentException("Choose valid related products.");}
                if(product<=0||!unique.add(product))throw new IllegalArgumentException("Choose each product once.");
                if(rows(c,"""
                    SELECT 1 FROM storefront.products w JOIN products p ON p.product_id=w.product_id
                    WHERE w.location_id=? AND w.product_id=? AND w.published AND p.is_active AND p.product_type='INVENTORY'
                    """,location,product).isEmpty())throw new IllegalArgumentException("Choose products published at this store.");
            }
        }
        java.math.BigDecimal price=null;
        if(body.has("startingPrice")&&!body.get("startingPrice").isJsonNull()&&!body.get("startingPrice").getAsString().isBlank()){
            price=body.get("startingPrice").getAsBigDecimal();
            if(price.signum()<0||price.scale()>2||price.compareTo(new java.math.BigDecimal("9999999999.99"))>0)
                throw new IllegalArgumentException("Enter a valid starting price.");
        }
        if(execute(c,"""
            UPDATE storefront.projects SET title=?,summary=?,category=?,materials=?,production_method=?,service_slug=?,
                   customization=?,tags=?::jsonb,starting_price=?,product_ids=COALESCE(?::jsonb,product_ids),updated_at=now()
            WHERE project_id=? AND location_id=? AND status<>'ARCHIVED'
            """,title,summary,category,materials,method,serviceSlug,customization,tags.toString(),price,
            productIds==null?null:productIds.toString(),id,location)!=1)
            throw new IllegalArgumentException("Project draft not found.");
    }
    static void status(Connection c,int location,JsonObject body)throws SQLException {
        UUID id=UUID.fromString(text(body,"projectId"));String wanted=text(body,"status");
        if(!java.util.Set.of("DRAFT","PUBLISHED","FEATURED","ARCHIVED").contains(wanted))throw new IllegalArgumentException("Choose a project status.");
        JsonObject project=one(rows(c,"SELECT title,summary,category,cover_reference FROM storefront.projects WHERE project_id=? AND location_id=? FOR UPDATE",id,location));
        if(project.entrySet().isEmpty())throw new IllegalArgumentException("Project not found at this store.");
        if(wanted.equals("PUBLISHED")||wanted.equals("FEATURED")){
            if(text(project,"title").isBlank()||text(project,"summary").isBlank()||text(project,"category").isBlank())
                throw new IllegalArgumentException("Add a title, description, and category before publishing.");
            if(ServerImageAssetService.storefrontManifest(c,text(project,"cover_reference"),"PROJECT")==null)
                throw new IllegalArgumentException("Add an approved project cover photo before publishing.");
        }
        execute(c,"UPDATE storefront.projects SET status=?,updated_at=now() WHERE project_id=? AND location_id=?",wanted,id,location);
    }
    static void cover(Connection c,int location,JsonObject body)throws Exception {
        UUID id=UUID.fromString(text(body,"projectId"));
        if(rows(c,"SELECT 1 FROM storefront.projects WHERE project_id=? AND location_id=? AND status<>'ARCHIVED' FOR UPDATE",id,location).isEmpty())
            throw new IllegalArgumentException("Project draft not found.");
        String encoded=text(body,"bytesBase64");
        if(encoded.length()>1_800_000)throw new IllegalArgumentException("Use a photo smaller than 1.3 MB.");
        byte[] bytes;try{bytes=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException e){throw new IllegalArgumentException("The photo is invalid.");}
        if(bytes.length==0||bytes.length>1_300_000)throw new IllegalArgumentException("Use a photo smaller than 1.3 MB.");
        boolean jpeg=bytes.length>3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
        boolean png=bytes.length>8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71;
        if(!jpeg&&!png)throw new IllegalArgumentException("Upload a JPEG or PNG photo.");
        String extension=jpeg?"jpg":"png",type=jpeg?"image/jpeg":"image/png";
        String path="projects/"+id+"/"+UUID.randomUUID()+"."+extension;
        String reference=ServerImageAssetService.storeUpload(c,"PROJECT","deckers-creative",path,type,"project-cover."+extension,"AUTHENTICATED",bytes);
        execute(c,"UPDATE storefront.projects SET cover_reference=?,updated_at=now() WHERE project_id=? AND location_id=?",reference,id,location);
    }
    static UUID uploadMedia(Connection c,int location,JsonObject body)throws Exception {
        UUID project=UUID.fromString(text(body,"projectId"));
        if(rows(c,"SELECT 1 FROM storefront.projects WHERE project_id=? AND location_id=? AND status<>'ARCHIVED' FOR UPDATE",project,location).isEmpty())
            throw new IllegalArgumentException("Project draft not found.");
        String role=field(body,"role",12),caption=field(body,"caption",160);
        if(!java.util.Set.of("FINAL","DETAIL","PROCESS","BEFORE","AFTER").contains(role))
            throw new IllegalArgumentException("Choose a photo role.");
        java.util.Set<Integer> taken=new java.util.HashSet<>();
        for(var item:rows(c,"SELECT position FROM storefront.project_media WHERE project_id=?",project))taken.add(integer(item.getAsJsonObject(),"position"));
        int position=0;while(position<6&&taken.contains(position))position++;
        if(position==6)throw new IllegalArgumentException("A project can have at most six gallery photos.");
        String encoded=text(body,"bytesBase64");if(encoded.length()>1_800_000)throw new IllegalArgumentException("Use a photo smaller than 1.3 MB.");
        byte[] bytes;try{bytes=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException e){throw new IllegalArgumentException("The photo is invalid.");}
        if(bytes.length==0||bytes.length>1_300_000)throw new IllegalArgumentException("Use a photo smaller than 1.3 MB.");
        boolean jpeg=bytes.length>3&&(bytes[0]&255)==255&&(bytes[1]&255)==216&&(bytes[2]&255)==255;
        boolean png=bytes.length>8&&(bytes[0]&255)==137&&bytes[1]==80&&bytes[2]==78&&bytes[3]==71;
        if(!jpeg&&!png)throw new IllegalArgumentException("Upload a JPEG or PNG photo.");
        String extension=jpeg?"jpg":"png",type=jpeg?"image/jpeg":"image/png";
        UUID media=UUID.randomUUID();
        String path="projects/"+project+"/gallery/"+media+"."+extension;
        String reference=ServerImageAssetService.storeUpload(c,"PROJECT","deckers-creative",path,type,"project-gallery."+extension,"AUTHENTICATED",bytes);
        execute(c,"""
            INSERT INTO storefront.project_media(media_id,project_id,location_id,role,caption,asset_reference,position)
            VALUES(?,?,?,?,?,?,?)
            """,media,project,location,role,caption,reference,position);
        execute(c,"UPDATE storefront.projects SET updated_at=now() WHERE project_id=? AND location_id=?",project,location);
        return media;
    }
    static void deleteMedia(Connection c,int location,JsonObject body)throws SQLException {
        UUID project=UUID.fromString(text(body,"projectId")),media=UUID.fromString(text(body,"mediaId"));
        if(rows(c,"SELECT 1 FROM storefront.projects WHERE project_id=? AND location_id=? AND status<>'ARCHIVED' FOR UPDATE",project,location).isEmpty())
            throw new IllegalArgumentException("Project draft not found.");
        if(execute(c,"DELETE FROM storefront.project_media WHERE media_id=? AND project_id=? AND location_id=?",media,project,location)!=1)
            throw new IllegalArgumentException("Project photo not found.");
        execute(c,"UPDATE storefront.projects SET updated_at=now() WHERE project_id=? AND location_id=?",project,location);
    }
    private static String field(JsonObject body,String key,int limit){
        String value=text(body,key).trim();if(value.length()>limit)throw new IllegalArgumentException(key+" is too long.");return value;
    }
}
