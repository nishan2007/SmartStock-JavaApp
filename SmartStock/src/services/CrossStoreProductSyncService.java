package services;

import com.google.gson.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Applies shared catalog notifications; never imports another store's stock or transactions. */
final class CrossStoreProductSyncService {
    private static final Map<String,String> KEYS = Map.of(
            "products", "product_id", "categories", "category_id", "item_types", "item_type_id",
            "item_brands", "brand_id", "vendors", "vendor_id", "product_groups", "group_id",
            "image_assets", "asset_id");
    private CrossStoreProductSyncService() { }

    interface CatalogSource {
        Instant completedAt();
        List<JsonObject> rows(String table) throws SQLException;
    }
    interface SourceLoader { CatalogSource load(int locationId) throws SQLException; }

    static int applyInbox(Connection c, int localLocationId) throws SQLException {
        return applyInbox(c, localLocationId, location -> {
            try {
                CloudSyncManifest manifest = CloudSyncManifest.fetchStoreSnapshot(location);
                if (!manifest.hasVerifiedSnapshot()) throw new SQLException("Product source snapshot is incomplete.");
                return new CatalogSource() {
                    final Map<String,List<JsonObject>> tables = new HashMap<>();
                    public Instant completedAt() { return manifest.completedAt(); }
                    public List<JsonObject> rows(String table) throws SQLException {
                        if (!manifest.hasTable(table)) throw new SQLException("Product source snapshot lacks " + table + ".");
                        if (!tables.containsKey(table)) {
                            var rows = CrossStoreInventoryService.fetchTable(location, manifest.snapshotGenerationId(), table);
                            if (rows.size() != manifest.rowCount(table)) throw new SQLException("Product source count did not verify: " + table + ".");
                            tables.put(table, rows);
                        }
                        return tables.get(table);
                    }
                };
            } catch (java.io.IOException ex) { throw new SQLException("Product source snapshot is unavailable; sync will retry.", ex); }
        });
    }

    static int applyInbox(Connection c, int localLocationId, SourceLoader loader) throws SQLException {
        if (!c.getAutoCommit()) throw new SQLException("Product inbox requires its own transactions.");
        List<Event> events = new ArrayList<>();
        try (var s = c.createStatement(); var r = s.executeQuery("""
                SELECT cloud_sequence,origin_location_id,origin_created_at,payload::text,received_at
                FROM sync_inbox WHERE event_type IN ('PRODUCT_CREATED','PRODUCT_UPDATED')
                  AND status IN ('RECEIVED','FAILED') ORDER BY cloud_sequence
                """)) {
            while (r.next()) events.add(new Event(r.getLong(1),(Integer)r.getObject(2),
                    r.getTimestamp(3) == null ? null : r.getTimestamp(3).toInstant(),r.getString(4),
                    r.getTimestamp(5) == null ? null : r.getTimestamp(5).toInstant()));
        }
        Map<Integer,CatalogSource> sources = new HashMap<>();
        Map<Integer,SQLException> failures = new HashMap<>();
        int applied = 0;
        for (Event event : events) {
            try {
                if (event.origin() == null || event.origin() <= 0 || event.origin() == localLocationId)
                    throw new SQLException("Product notification has an invalid source store.");
                JsonObject payload = JsonParser.parseString(event.payload()).getAsJsonObject();
                int id = payload.get("product_id").getAsInt();
                if (id <= 0) throw new SQLException("Product notification has an invalid item ID.");
                if (payload.has("location_id") && payload.get("location_id").getAsInt() != event.origin())
                    throw new SQLException("Product notification source does not match its store.");
                if (failures.containsKey(event.origin())) throw failures.get(event.origin());
                CatalogSource source = sources.get(event.origin());
                if (source == null) {
                    try { source = loader.load(event.origin()); sources.put(event.origin(),source); }
                    catch (SQLException ex) { failures.put(event.origin(),ex); throw ex; }
                }
                Instant expectedVersion = payload.has("catalog_updated_at")
                        ? timestamp(payload,"catalog_updated_at") : event.createdAt();
                if (event.createdAt() == null || source.completedAt() == null
                        || source.completedAt().isBefore(expectedVersion))
                    throw new SQLException("Product notification is waiting for its completed source snapshot.");
                // Download before taking product locks or starting the local transaction.
                ProductPlan plan = plan(source, id);
                // Old servers sent only the ID. A generation completed before event delivery
                // may have scanned this product before the edit. Never acknowledge that race.
                // Quantity-only edits do not change products.updated_at; a later completed
                // generation proves those legacy notifications have no catalog change to apply.
                if (timestamp(plan.product(),"updated_at").isBefore(expectedVersion)
                        && (payload.has("catalog_updated_at") || event.receivedAt() == null
                            || source.completedAt().isBefore(event.receivedAt())))
                    throw new SQLException("Product edit is waiting for its newer source snapshot.");
                c.setAutoCommit(false);
                try (var s = c.createStatement()) { s.execute("SET LOCAL lock_timeout='5s'"); }
                applyPlan(c, plan, event.origin());
                mark(c,event.sequence(),"APPLIED",null);
                c.commit(); applied++;
            } catch (Exception ex) {
                if (!c.getAutoCommit()) c.rollback();
                c.setAutoCommit(false);
                // Do not copy arbitrary HTTP responses or row contents into the sync status.
                String message = ex instanceof SQLException ? ex.getMessage() : "Product notification is invalid.";
                if (ex instanceof SQLException sql && sql.getSQLState() != null)
                    message = "Product catalog could not be applied (SQL " + sql.getSQLState() + "); sync will retry.";
                mark(c,event.sequence(),"FAILED",message == null ? "Product sync failed; sync will retry." : message);
                c.commit();
            } finally { c.setAutoCommit(true); }
        }
        return applied;
    }

    static ProductPlan plan(CatalogSource source, int productId) throws SQLException {
        JsonObject product = find(source,"products","product_id",Integer.toString(productId));
        if ("SMARTSTOCK-MISC".equals(text(product,"sku")))
            return new ProductPlan(product.deepCopy(),List.of(),List.of(),List.of());
        Map<String,JsonObject> dependencies = new LinkedHashMap<>();
        addReference(source,dependencies,product,"categories","category_id");
        JsonObject type = addReference(source,dependencies,product,"item_types","item_type_id");
        if (type != null) addReference(source,dependencies,type,"categories","category_id");
        addReference(source,dependencies,product,"item_brands","brand_id");
        addReference(source,dependencies,product,"vendors","vendor_id");
        addReference(source,dependencies,product,"product_groups","group_id");
        List<JsonObject> assets = new ArrayList<>();
        Set<String> urls = new LinkedHashSet<>();
        urls.add(text(product,"image_url"));
        JsonElement gallery = product.get("additional_image_urls");
        if (gallery != null && !gallery.isJsonNull()) {
            JsonArray images = gallery.isJsonArray() ? gallery.getAsJsonArray() : JsonParser.parseString(gallery.getAsString()).getAsJsonArray();
            for (JsonElement image : images) urls.add(image.getAsString());
        }
        for (String url : urls) if (url != null && url.startsWith("smartstock-asset:")) {
            JsonObject asset = find(source,"image_assets","asset_id",url.substring("smartstock-asset:".length()));
            if (!"PRODUCT".equals(text(asset,"category"))) throw new SQLException("Product image has an invalid category.");
            asset = asset.deepCopy(); asset.addProperty("local_status","MISSING"); assets.add(asset);
        }
        List<JsonObject> barcodes = new ArrayList<>();
        for (JsonObject row : source.rows("product_barcodes"))
            if (Integer.toString(productId).equals(text(row,"product_id"))) barcodes.add(row.deepCopy());
        // Categories must precede item types even when the type carries a different category.
        List<Map.Entry<String,JsonObject>> ordered = new ArrayList<>(dependencies.entrySet());
        ordered.sort(Comparator.comparing(e -> !e.getKey().startsWith("categories:")));
        return new ProductPlan(product.deepCopy(),ordered,assets,barcodes);
    }

    private static JsonObject addReference(CatalogSource source, Map<String,JsonObject> deps,
            JsonObject row, String table, String key) throws SQLException {
        String id = text(row,key);
        if (id == null) return null;
        JsonObject reference = find(source,table,key,id);
        deps.put(table+":"+id,reference.deepCopy()); return reference;
    }

    private static JsonObject find(CatalogSource source,String table,String key,String id) throws SQLException {
        for (JsonObject row : source.rows(table)) if (id.equals(text(row,key))) return row;
        throw new SQLException("Product source is missing its " + table + " record; sync will retry.");
    }

    static boolean applyPlan(Connection c, ProductPlan plan, int sourceLocation) throws SQLException {
        JsonObject incoming = plan.product().deepCopy();
        if ("SMARTSTOCK-MISC".equals(text(incoming,"sku"))) return false;
        // Download is complete before this short, serialized catalog transaction.
        try (var s=c.createStatement()) { s.execute("SELECT pg_advisory_xact_lock(728493,0)"); }
        Map<String,String> remapped=new HashMap<>();
        Map<String,UUID> categories=new HashMap<>();
        for (var dependency : plan.dependencies()) {
            String table = dependency.getKey().substring(0,dependency.getKey().indexOf(':'));
            JsonObject row = dependency.getValue().deepCopy();
            if(CatalogIdentityService.KEYS.containsKey(table)) {
                String sourceId=text(row,KEYS.get(table));
                UUID sourceCategoryUid="categories".equals(table)
                        ? (text(row,"catalog_uid")==null ? CatalogIdentityService.legacyUid(table,row,null)
                            : UUID.fromString(text(row,"catalog_uid"))) : null;
                UUID parent=null;
                if("item_types".equals(table)) {
                    String sourceCategory=text(row,"category_id");
                    parent=categories.get(sourceCategory);
                    row.addProperty("category_id",remapped.get("categories:"+sourceCategory));
                }
                row=CatalogIdentityService.resolve(c,table,row,sourceLocation,parent);
                remapped.put(table+":"+sourceId,text(row,KEYS.get(table)));
                if("categories".equals(table))categories.put(sourceId,sourceCategoryUid);
            }
            JsonObject local = localRow(c,table,text(row,KEYS.get(table)));
            if (local == null) insertOrUpdate(c,table,row,false);
            else if(CatalogIdentityService.KEYS.containsKey(table)) {
                if(text(row,"updated_at")!=null && text(local,"updated_at")!=null
                        && timestamp(row,"updated_at").isAfter(timestamp(local,"updated_at")))
                    insertOrUpdate(c,table,row,true);
            } else if(!Objects.equals(text(local,"name"),text(row,"name")))
                throw new SQLException("Product group identity collision; existing catalog was preserved.");
        }
        for(String table:List.of("categories","item_types","item_brands","vendors")) {
            String key=KEYS.get(table),sourceId=text(incoming,key);
            if(sourceId!=null) {
                String target=remapped.get(table+":"+sourceId);
                if(target==null)throw new SQLException("Product reference mapping is incomplete.");
                incoming.addProperty(key,target);
            }
        }
        incoming=CatalogIdentityService.resolve(c,"products",incoming,sourceLocation,null);
        String id=text(incoming,"product_id");
        JsonObject existing=localRow(c,"products",id);
        if(existing!=null) {
            if("SMARTSTOCK-MISC".equals(text(existing,"sku")))throw new SQLException("Product identity belongs to this store's system item.");
            if(timestamp(existing,"updated_at").isAfter(timestamp(incoming,"updated_at")))return false;
        }
        for (JsonObject asset : plan.assets()) {
            // Existing local image presence and download state belong to this server.
            if (localRow(c,"image_assets",text(asset,"asset_id")) == null) insertOrUpdate(c,"image_assets",asset,false);
        }
        insertOrUpdate(c,"products",incoming,true);
        Set<String> desiredBarcodes = new HashSet<>();
        for (JsonObject row : plan.barcodes()) desiredBarcodes.add(text(row,"barcode"));
        List<String> removed = new ArrayList<>();
        try (var p=c.prepareStatement("SELECT barcode FROM product_barcodes WHERE product_id=?")) {
            p.setInt(1,Integer.parseInt(id));try(var r=p.executeQuery()){while(r.next())if(!desiredBarcodes.contains(r.getString(1)))removed.add(r.getString(1));}
        }
        for(String barcode:removed)try(var p=c.prepareStatement("DELETE FROM product_barcodes WHERE product_id=? AND barcode=?")) {
            p.setInt(1,Integer.parseInt(id));p.setString(2,barcode);p.executeUpdate();
        }
        for (JsonObject row : plan.barcodes()) {
            try (var p = c.prepareStatement("""
                    INSERT INTO product_barcodes(product_id,barcode,created_at,updated_at)
                    VALUES(?,?,?::timestamptz,?::timestamptz)
                    ON CONFLICT(barcode) DO UPDATE SET updated_at=EXCLUDED.updated_at
                      WHERE product_barcodes.product_id=EXCLUDED.product_id
                    """)) {
                p.setInt(1,Integer.parseInt(id)); p.setString(2,text(row,"barcode"));
                p.setString(3,text(row,"created_at")); p.setString(4,text(row,"updated_at"));
                if(p.executeUpdate()!=1)throw new SQLException("Product barcode belongs to another item; existing catalog was preserved.");
            }
        }
        return true;
    }

    static boolean sameIdentity(JsonObject local,JsonObject incoming) throws SQLException {
        // Stores inherited these IDs from the common catalog. A matching birth time allows
        // legitimate SKU/barcode edits, but independently-created colliding IDs must fail.
        return timestamp(local,"created_at").equals(timestamp(incoming,"created_at"));
    }

    private static JsonObject localRow(Connection c,String table,String id) throws SQLException {
        String key = KEYS.get(table);
        if (key == null) throw new SQLException("Unapproved product catalog table.");
        try (var p = c.prepareStatement("SELECT to_jsonb(t)::text FROM " + table + " t WHERE " + key + "::text=? FOR UPDATE")) {
            p.setString(1,id); try (var r=p.executeQuery()) { return r.next() ? JsonParser.parseString(r.getString(1)).getAsJsonObject() : null; }
        }
    }

    private static void insertOrUpdate(Connection c,String table,JsonObject row,boolean update) throws SQLException {
        String key=KEYS.get(table);
        if (key == null) throw new SQLException("Unapproved product catalog table.");
        List<String> columns=new ArrayList<>(); Set<String> json=new HashSet<>();
        try (var p=c.prepareStatement("""
                SELECT a.attname,t.typname FROM pg_attribute a JOIN pg_type t ON t.oid=a.atttypid
                WHERE a.attrelid=?::regclass AND a.attnum>0 AND NOT a.attisdropped AND a.attgenerated=''
                ORDER BY a.attnum
                """)) {
            p.setString(1,table); try(var r=p.executeQuery()) { while(r.next()) {
                if(row.has(r.getString(1))) columns.add(r.getString(1));
                if(Set.of("json","jsonb").contains(r.getString(2))) json.add(r.getString(1));
            } }
        }
        JsonArray array=new JsonArray(); array.add(row);
        JsonObject decoded=StoreCatalogRecoveryService.decodeJsonColumns(array,json).get(0).getAsJsonObject();
        if(!columns.contains(key)) throw new SQLException("Product catalog row has no key.");
        String names=String.join(",",columns.stream().map(v->"\""+v+"\"").toList());
        String assignments=String.join(",",columns.stream().filter(v->!v.equals(key)).map(v->"\""+v+"\"=EXCLUDED.\""+v+"\"").toList());
        try(var p=c.prepareStatement("INSERT INTO "+table+"("+names+") OVERRIDING SYSTEM VALUE SELECT "+names+
                " FROM jsonb_populate_record(NULL::"+table+",?::jsonb) ON CONFLICT("+key+") DO "+(update?"UPDATE SET "+assignments:"NOTHING"))) {
            p.setString(1,decoded.toString()); p.executeUpdate();
        }
        // Imported numeric IDs must not collide with the next locally-created catalog row.
        if (!update || "products".equals(table)) {
            String sequence;
            try(var p=c.prepareStatement("SELECT pg_get_serial_sequence(?,?)")) {
                p.setString(1,table);p.setString(2,key);try(var r=p.executeQuery()){r.next();sequence=r.getString(1);}
            }
            if(sequence!=null)try(var p=c.prepareStatement("SELECT setval(?::regclass,GREATEST((SELECT last_value FROM "+sequence+
                    "),COALESCE((SELECT max(\""+key+"\") FROM "+table+"),1)),true)")) {p.setString(1,sequence);p.execute();}
        }
    }

    private static void mark(Connection c,long sequence,String status,String error) throws SQLException {
        try(var p=c.prepareStatement("UPDATE sync_inbox SET status=?,last_error=?,applied_at=CASE WHEN ?='APPLIED' THEN CURRENT_TIMESTAMP ELSE NULL END WHERE cloud_sequence=?")) {
            p.setString(1,status);p.setString(2,error);p.setString(3,status);p.setLong(4,sequence);p.executeUpdate();
        }
    }
    static String text(JsonObject row,String field) { JsonElement v=row.get(field);return v==null||v.isJsonNull()?null:v.getAsString(); }
    static Instant timestamp(JsonObject row,String field) throws SQLException {
        String value=text(row,field);
        if(value==null)throw new SQLException("Product catalog is missing its "+field+" timestamp.");
        try { return java.time.OffsetDateTime.parse(value.replace(' ','T').replaceAll("([+-]\\d{2})$","$1:00")).toInstant(); }
        catch(RuntimeException ex){throw new SQLException("Product catalog has an invalid "+field+" timestamp.",ex);}
    }
    record ProductPlan(JsonObject product,List<Map.Entry<String,JsonObject>> dependencies,List<JsonObject> assets,List<JsonObject> barcodes) { }
    private record Event(long sequence,Integer origin,Instant createdAt,String payload,Instant receivedAt) { }
}
