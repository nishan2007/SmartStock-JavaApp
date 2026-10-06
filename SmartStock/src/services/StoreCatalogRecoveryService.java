package services;

import com.google.gson.*;
import data.EnvironmentProfile;
import java.sql.*;
import java.util.*;

/** One-time, additive catalog bootstrap for Rosehall's incomplete enrollment. */
final class StoreCatalogRecoveryService {
    static final String REPAIR_ID="rosehall_catalog_bootstrap_v1";
    static final List<String> TABLES=List.of("categories","item_types","item_brands","vendors",
            "product_groups","image_assets","products","product_barcodes","custom_order_items",
            "custom_order_item_variants","custom_order_item_barcodes");
    static final Set<String> IMAGE_CATEGORIES=Set.of("PRODUCT","CUSTOM_ITEM","CUSTOM_VARIANT");
    private StoreCatalogRecoveryService() { }

    static void repairIfNeeded(Connection c,int location) throws SQLException {
        if(EnvironmentProfile.active()!=EnvironmentProfile.PRODUCTION || completed(c))return;
        try(var p=c.prepareStatement("SELECT name FROM locations WHERE location_id=?")) {
            p.setInt(1,location);try(var r=p.executeQuery()) {
                if(!r.next() || !"Rosehall".equalsIgnoreCase(r.getString(1)))return;
            }
        }
        int source;
        try(var p=c.prepareStatement("SELECT location_id FROM locations WHERE lower(name)='skeldon'");var r=p.executeQuery()) {
            if(!r.next())throw new SQLException("Skeldon's catalog source is unavailable.");
            source=r.getInt(1);if(r.next() || source==location)throw new SQLException("Catalog source is ambiguous.");
        }
        Map<String,JsonArray> plan=new LinkedHashMap<>();
        try {
            CloudSyncManifest manifest=CloudSyncManifest.fetchStoreSnapshot(source,c);
            if(!manifest.hasVerifiedSnapshot())throw new SQLException("Skeldon's completed catalog snapshot is unavailable.");
            for(String table:TABLES) {
                if(!manifest.hasTable(table))throw new SQLException("Catalog snapshot is incomplete: "+table);
                JsonArray rows=new JsonArray();long cursor=0;
                while(true) {
                    JsonObject page=CloudRecoveryService.fetchMirrorPage(source,manifest.snapshotGenerationId(),table,cursor);
                    JsonArray envelopes=page.getAsJsonArray("rows");
                    if(envelopes==null)throw new SQLException("Catalog snapshot has invalid rows: "+table);
                    long next=cursor;
                    for(JsonElement element:envelopes) {
                        JsonObject envelope=element.getAsJsonObject();long sequence=envelope.get("sequence").getAsLong();
                        if(sequence<=next)throw new SQLException("Catalog snapshot is unordered: "+table);
                        next=sequence;
                        if(!envelope.get("is_deleted").getAsBoolean())rows.add(envelope.getAsJsonObject("row_data"));
                    }
                    cursor=next;if(envelopes.size()<1000)break;
                }
                if(rows.size()!=manifest.rowCount(table))throw new SQLException("Catalog snapshot count did not verify: "+table);
                plan.put(table,"image_assets".equals(table)?catalogImages(rows):rows);
            }
        }catch(java.io.IOException|RuntimeException ex) {
            throw new SQLException("Catalog recovery download failed; sync will retry without changing local data.",ex);
        }
        if(plan.get("products").isEmpty())throw new SQLException("Skeldon's catalog snapshot is empty.");
        applyPlan(c,plan);
    }

    static JsonArray catalogImages(JsonArray rows) {
        JsonArray result=new JsonArray();
        for(JsonElement element:rows) {
            JsonObject row=element.getAsJsonObject();
            if(!IMAGE_CATEGORIES.contains(row.get("category").getAsString())
                    || "DELETED".equals(row.get("lifecycle_status").getAsString()))continue;
            JsonObject imported=row.deepCopy();imported.addProperty("local_status","MISSING");
            imported.add("last_verified_at",JsonNull.INSTANCE);imported.add("last_error",JsonNull.INSTANCE);
            result.add(imported);
        }
        return result;
    }

    private static void alignUnusedMiscItem(Connection c,Map<String,JsonArray> plan)throws SQLException {
        Integer sourceId=null;
        for(JsonElement e:plan.get("products")) {
            JsonObject r=e.getAsJsonObject();
            if("SMARTSTOCK-MISC".equals(r.get("sku").getAsString()))sourceId=r.get("product_id").getAsInt();
        }
        if(sourceId==null)throw new SQLException("Source catalog has no system miscellaneous item.");
        JsonObject local;
        try(var s=c.createStatement();var r=s.executeQuery("SELECT to_jsonb(p)::text FROM products p WHERE sku='SMARTSTOCK-MISC' FOR UPDATE")) {
            if(!r.next())return;local=JsonParser.parseString(r.getString(1)).getAsJsonObject();
        }
        int old=local.get("product_id").getAsInt();if(old==sourceId)return;
        try(var p=c.prepareStatement("SELECT 1 FROM products WHERE product_id=?")){p.setInt(1,sourceId);try(var r=p.executeQuery()){if(r.next())throw new SQLException("System miscellaneous item identity is occupied; existing catalog was preserved.");}}
        // Never renumber an item that has been used in stock, carts or transactions.
        try(var p=c.prepareStatement("SELECT n.nspname,t.relname,a.attname FROM pg_constraint f JOIN pg_class t ON t.oid=f.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace JOIN pg_attribute a ON a.attrelid=f.conrelid AND a.attnum=f.conkey[1] WHERE f.contype='f' AND f.confrelid='products'::regclass AND n.nspname='public'" );var r=p.executeQuery()) {
            while(r.next()) {
                String table=r.getString(2),col=r.getString(3);
                try(var q=c.prepareStatement("SELECT 1 FROM public.\""+table+"\" WHERE \""+col+"\"=? LIMIT 1")){q.setInt(1,old);try(var used=q.executeQuery()){if(used.next())throw new SQLException("Rosehall's miscellaneous item has existing stock or transaction references; identity repair requires review.");}}
            }
        }
        // Both the source and destination identify the placeholder by its reserved SKU.
        local.addProperty("product_id",sourceId);
        try(var p=c.prepareStatement("DELETE FROM products WHERE product_id=? AND sku='SMARTSTOCK-MISC'")){p.setInt(1,old);p.executeUpdate();}
        try(var p=c.prepareStatement("INSERT INTO products OVERRIDING SYSTEM VALUE SELECT * FROM jsonb_populate_record(NULL::products,?::jsonb)")){p.setString(1,local.toString());p.executeUpdate();}
    }
    static JsonArray decodeJsonColumns(JsonArray rows,Set<String> jsonColumns)throws SQLException {
        JsonArray decoded=new JsonArray();
        for(JsonElement element:rows) {
            JsonObject row=element.getAsJsonObject().deepCopy();
            for(String column:jsonColumns) {
                JsonElement value=row.get(column);
                if(value!=null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
                    try{row.add(column,JsonParser.parseString(value.getAsString()));}
                    catch(JsonParseException ex){throw new SQLException("Catalog mirror contains invalid JSON in "+column,ex);}
                }
            }
            decoded.add(row);
        }
        return decoded;
    }
    static boolean completed(Connection c)throws SQLException {
        try(var p=c.prepareStatement("SELECT 1 FROM sync_cloud_state WHERE state_id=?")) {
            p.setString(1,REPAIR_ID);try(var r=p.executeQuery()){return r.next();}
        }
    }

    static void applyPlan(Connection c,Map<String,JsonArray> plan)throws SQLException {
        if(!c.getAutoCommit())throw new SQLException("Catalog bootstrap requires its own transaction.");
        c.setAutoCommit(false);
        try {
            // Serialize concurrent app/service repair attempts without blocking checkout.
            try(var s=c.createStatement()){s.execute("SET LOCAL lock_timeout='5s'");s.execute("SELECT pg_advisory_xact_lock(728491,2)");}
            if(completed(c)){c.commit();return;}
            alignUnusedMiscItem(c,plan);
            for(String table:TABLES) {
                JsonArray rows=plan.get(table);if(rows==null)throw new SQLException("Incomplete catalog recovery plan: "+table);
                if("products".equals(table)) { JsonArray retail=new JsonArray(); for(JsonElement e:rows) if(!"SMARTSTOCK-MISC".equals(e.getAsJsonObject().get("sku").getAsString()))retail.add(e); rows=retail; }
                if(rows.isEmpty())continue;
                List<String> cols=new ArrayList<>(),keys=new ArrayList<>(); Set<String> jsonColumns=new HashSet<>();
                try(var p=c.prepareStatement("SELECT column_name,udt_name FROM information_schema.columns WHERE table_schema='public' AND table_name=? AND is_generated='NEVER' ORDER BY ordinal_position")) {
                    p.setString(1,table);try(var r=p.executeQuery()){while(r.next()){cols.add(r.getString(1));if(Set.of("json","jsonb").contains(r.getString(2)))jsonColumns.add(r.getString(1));}}
                }
                try(var p=c.prepareStatement("SELECT a.attname FROM pg_index i JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=ANY(i.indkey) WHERE i.indrelid=?::regclass AND i.indisprimary")) {
                    p.setString(1,table);try(var r=p.executeQuery()){while(r.next())keys.add(r.getString(1));}
                }
                if(cols.isEmpty() || keys.isEmpty())throw new SQLException("Catalog target schema is incomplete: "+table);
                rows=decodeJsonColumns(rows,jsonColumns);
                String join=String.join(" AND ",keys.stream().map(k->"t.\""+k+"\"=s.\""+k+"\"").toList());
                String ignored="ARRAY['created_at','updated_at','last_verified_at','last_error','local_status','cloud_verified_at','unused_since','retained']::text[]";
                String relation="jsonb_populate_recordset(NULL::"+table+",?::jsonb)";
                try(var p=c.prepareStatement("SELECT EXISTS(SELECT 1 FROM "+relation+" s JOIN "+table+" t ON "+join+" WHERE (to_jsonb(s)-"+ignored+") IS DISTINCT FROM (to_jsonb(t)-"+ignored+"))")) {
                    p.setString(1,rows.toString());try(var r=p.executeQuery()){r.next();if(r.getBoolean(1))throw new SQLException("Catalog ID collision in "+table+"; existing store data was preserved. Review before merging.");}
                }
                String columns=String.join(",",cols.stream().map(k->"\""+k+"\"").toList());
                try(var p=c.prepareStatement("INSERT INTO "+table+"("+columns+") OVERRIDING SYSTEM VALUE SELECT "+columns+" FROM "+relation+" ON CONFLICT DO NOTHING")) {
                    p.setString(1,rows.toString());p.executeUpdate();
                }
                try(var p=c.prepareStatement("SELECT count(*) FROM "+relation+" s WHERE NOT EXISTS(SELECT 1 FROM "+table+" t WHERE "+join+")")) {
                    p.setString(1,rows.toString());try(var r=p.executeQuery()){r.next();if(r.getLong(1)>0)throw new SQLException("Catalog uniqueness collision in "+table+"; repair rolled back.");}
                }
                for(String col:cols) {
                    String sequence;
                    try(var p=c.prepareStatement("SELECT pg_get_serial_sequence(?,?)")){p.setString(1,table);p.setString(2,col);try(var r=p.executeQuery()){r.next();sequence=r.getString(1);}}
                    if(sequence==null)continue;
                    // Never move sequences backwards. Advancing one on rollback is harmless.
                    try(var p=c.prepareStatement("SELECT setval(?::regclass,GREATEST(COALESCE((SELECT max(\""+col+"\") FROM "+table+"),1),(SELECT last_value FROM "+sequence+")),true)")){p.setString(1,sequence);p.execute();}
                }
            }
            try(var p=c.prepareStatement("INSERT INTO sync_cloud_state(state_id,cursor_value) VALUES(?,1)")){p.setString(1,REPAIR_ID);p.executeUpdate();}
            c.commit();
        }catch(Exception ex){c.rollback();if(ex instanceof SQLException sql)throw sql;throw new SQLException("Catalog repair rolled back.",ex);}
        finally{c.setAutoCommit(true);}
    }
}
