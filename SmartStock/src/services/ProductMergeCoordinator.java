package services;

import com.google.gson.*;
import data.DatabaseConfig;
import java.sql.*;
import java.util.*;
import static services.ProductMergeService.*;

/** A shared alias, with an independent atomic consolidation at each store. */
final class ProductMergeCoordinator {
    private static final Gson JSON=new Gson();
    private ProductMergeCoordinator() { }
    private record Ref(String schema,String table,String column) {
        String sql(){return quote(schema)+"."+quote(table);}
        String label(){return schema+"."+table+"."+column;}
    }
    static UUID uid(Item item) {
        return UUID.fromString(JsonParser.parseString(item.snapshot()).getAsJsonObject().getAsJsonObject("product").get("catalog_uid").getAsString());
    }
    static Review review(Connection c,int user,int location,int keepId,int sourceId)throws SQLException {
        ProductMergeService.requireAdmin(c,user);
        if(keepId<=0||sourceId<=0||keepId==sourceId)throw new SQLException("Choose two different products.","22023");
        Item keep=ProductMergeService.find(c,location,keepId),source=ProductMergeService.find(c,location,sourceId);
        List<String> blockers=new ArrayList<>(),warnings=new ArrayList<>();
        if(ProductMergeService.matchReason(keep,source).isBlank())blockers.add("The items no longer match the duplicate rules.");
        if(!ProductMergeService.normalize(keep.size()).equals(ProductMergeService.normalize(source.size()))
                ||!ProductMergeService.normalize(keep.color()).equals(ProductMergeService.normalize(source.color()))
                ||!ProductMergeService.normalize(keep.flavor()).equals(ProductMergeService.normalize(source.flavor()))
                ||!ProductMergeService.normalize(keep.brand()).equals(ProductMergeService.normalize(source.brand())))blockers.add("Size, color, flavor or brand differs.");
        if(!keep.groupId().isBlank()||!source.groupId().isBlank())blockers.add("Manage grouped variants through variant setup first.");
        List<StoreStock> stores=stocks(c,location,keepId,sourceId);
        for(StoreStock store:stores) {
            long a=store.keepQuantity()==null?0:store.keepQuantity(),b=store.sourceQuantity()==null?0:store.sourceQuantity();
            if(a<0||b<0)warnings.add("Signed stock at "+store.name()+" will be combined as "+a+" + "+b+" = "+(a+b)+"; negative quantities are subtracted, not discarded.");
            if(a+b>Integer.MAX_VALUE||a+b<Integer.MIN_VALUE)blockers.add("Combined stock at "+store.name()+" exceeds the supported quantity.");
        }
        warnings.add("Each store combines only its own stock. Cached quantities are a preview, not permission to overwrite remote stock.");
        warnings.add("The merge remains awaiting other stores until their updated servers acknowledge it. Existing sale and return amounts are preserved.");
        warnings.add("Keep the Skeldon item's price, cost and photos. Descriptions and barcode aliases are combined; source website listings are unpublished.");
        var references=new TreeMap<String,Long>();var hashes=new TreeMap<String,String>();
        for(Ref ref:refs(c))try(var p=c.prepareStatement("SELECT count(*),md5(COALESCE(string_agg(md5(to_jsonb(t)::text),'' ORDER BY md5(to_jsonb(t)::text)),'')) FROM "+ref.sql()+" t WHERE "+quote(ref.column())+" IN (?,?)")) {
            p.setInt(1,keepId);p.setInt(2,sourceId);try(var r=p.executeQuery()){r.next();if(r.getLong(1)>0){references.put(ref.label(),r.getLong(1));hashes.put(ref.label(),r.getString(2));}}
        }
        String stockSnapshot=rows(c,"SELECT to_jsonb(t) FROM inventory t WHERE product_id IN (?,?) ORDER BY product_id,location_id",keepId,sourceId).toString();
        String shelves=rows(c,"SELECT to_jsonb(t) FROM product_shelf_assignments t WHERE product_id IN (?,?) ORDER BY product_id,location_id",keepId,sourceId).toString();
        String extra=extraFingerprint(c,keepId,sourceId);
        String fingerprint=LanSecurity.sha256(JSON.toJson(List.of(location,keep,source,stores,hashes,stockSnapshot,shelves,extra,blockers)));
        return new Review(keep,source,List.copyOf(blockers),fingerprint,location,(long)keep.quantity()+source.quantity(),stores,references,warnings);
    }

    static Map<String,Object> merge(Connection c,int user,int location,UUID device,int keep,int source,String fingerprint)throws Exception {
        ProductMergeService.requireAdmin(c,user);LanProductAdminService.requirePermission(c,user,"EDIT_ITEM");
        LanProductAdminService.requirePermission(c,user,"PRODUCT_ARCHIVE");requireTransaction(c);Objects.requireNonNull(device);
        lock(c);
        Review review=review(c,user,location,keep,source);
        if(!Objects.equals(fingerprint,review.fingerprint()))throw new SQLException("Items, stock or linked records changed. Open a fresh merge review.","40001");
        if(!review.blockers().isEmpty())throw new SQLException(String.join("\n",review.blockers()),"55000");
        JsonObject mapping=new JsonObject();mapping.addProperty("merge_id",UUID.randomUUID().toString());
        mapping.addProperty("source_uid",uid(review.source()).toString());mapping.addProperty("target_uid",uid(review.keep()).toString());
        mapping.addProperty("origin_location_id",location);mapping.addProperty("merged_by_user_id",user);
        mapping.addProperty("updated_at",java.time.Instant.now().toString());
        JsonObject retained=JsonParser.parseString(review.keep().snapshot()).getAsJsonObject().getAsJsonObject("product").deepCopy();
        JsonObject fields=new JsonObject();for(String key:List.of("name","sku","barcode","price","cost_price","image_url","additional_image_urls"))fields.add(key,retained.get(key));
        fields.addProperty("description",combineDescriptions(review.keep().description(),review.source().description()));
        var allCodes=new LinkedHashSet<String>(review.keep().barcodes());allCodes.addAll(review.source().barcodes());allCodes.add(review.source().sku());
        fields.add("scan_aliases",JSON.toJsonTree(allCodes));mapping.add("retained_fields",fields);
        applyMapping(c,mapping,location,device);
        LanProductAdminService.audit(c,"LAN_PRODUCTS_MERGED",device,user,JSON.toJson(review));
        // Existing reference sync announces the mapping and per-store receipts.
        boolean pending;
        try(var p=c.prepareStatement("SELECT EXISTS(SELECT 1 FROM locations l WHERE l.location_id>0 AND NOT EXISTS(SELECT 1 FROM product_merge_applications a WHERE a.merge_id=?::uuid AND a.location_id=l.location_id))")) {
            p.setString(1,mapping.get("merge_id").getAsString());try(var r=p.executeQuery()){r.next();pending=r.getBoolean(1);}
        }
        return Map.of("keptProductId",keep,"archivedProductId",source,"quantityMoved",review.source().quantity(),
                "mergeId",mapping.get("merge_id").getAsString(),"state",pending?"AWAITING_STORES":"COMPLETE");
    }

    static void applyMapping(Connection c,JsonObject mapping)throws SQLException {
        Integer location=DatabaseConfig.load().locationId();
        if(location==null)throw new SQLException("The store location is not configured for product merges.","55000");
        try{applyMapping(c,mapping,location,null);}catch(SQLException ex){throw ex;}catch(Exception ex){throw new SQLException("Product merge could not be applied.",ex);}
    }

    static void applyReceipt(Connection c,JsonObject row)throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO product_merge_applications(merge_id,location_id,source_product_id,target_product_id,quantity_moved,before_state,updated_at) VALUES(?::uuid,?,?,?,?, '{}'::jsonb,?::timestamptz) ON CONFLICT(merge_id,location_id) DO NOTHING")) {
            p.setString(1,row.get("merge_id").getAsString());p.setInt(2,row.get("location_id").getAsInt());
            p.setInt(3,row.get("source_product_id").getAsInt());p.setInt(4,row.get("target_product_id").getAsInt());p.setInt(5,row.get("quantity_moved").getAsInt());p.setString(6,row.get("updated_at").getAsString());p.executeUpdate();
        }
    }
    static JsonObject status(Connection c)throws SQLException {
        JsonObject result=new JsonObject();
        result.add("merges",rows(c,"SELECT to_jsonb(m)||jsonb_build_object('sourceProductId',(SELECT product_id FROM products WHERE catalog_uid=m.source_uid),'targetProductId',(SELECT product_id FROM products WHERE catalog_uid=m.target_uid),'stores',(SELECT COALESCE(jsonb_agg(jsonb_build_object('locationId',l.location_id,'name',l.name,'state',CASE WHEN a.merge_id IS NULL THEN 'AWAITING_STORE' ELSE 'APPLIED' END)),'[]') FROM locations l LEFT JOIN product_merge_applications a ON a.merge_id=m.merge_id AND a.location_id=l.location_id WHERE l.location_id>0)) FROM product_merges m ORDER BY m.updated_at DESC LIMIT 100"));
        result.add("stockCommands",rows(c,"SELECT to_jsonb(q)||jsonb_build_object('state',COALESCE(r.state,'QUEUED'),'actualQuantity',r.previous_quantity) FROM product_stock_commands q LEFT JOIN product_stock_command_results r USING(command_id) ORDER BY q.updated_at DESC LIMIT 100"));
        return result;
    }

    static void applyMapping(Connection c,JsonObject mapping,int location,UUID device)throws Exception {
        requireTransaction(c);lock(c);
        UUID mergeId=UUID.fromString(mapping.get("merge_id").getAsString()),sourceUid=UUID.fromString(mapping.get("source_uid").getAsString()),targetUid=UUID.fromString(mapping.get("target_uid").getAsString());
        try(var p=c.prepareStatement("SELECT to_jsonb(m)::text FROM product_merges m WHERE source_uid=?")) {
            p.setObject(1,sourceUid);try(var r=p.executeQuery()){if(r.next()) {
                JsonObject old=JsonParser.parseString(r.getString(1)).getAsJsonObject();
                if(!old.get("merge_id").getAsString().equals(mergeId.toString())||!old.get("target_uid").getAsString().equals(targetUid.toString())
                        ||!Objects.equals(old.get("retained_fields"),mapping.get("retained_fields")))
                    throw new SQLException("Conflicting product merge requires administrator review.","55000");
                // A mapping is acknowledged only by its local receipt. Recovery may
                // restore a mapping before replay; consolidation remains idempotent.
                if(hasReceipt(c,mergeId,location))return;
            }}
        }
        int source=id(c,sourceUid),target=id(c,canonicalUid(c,targetUid));
        if(source==target)throw new SQLException("A product merge would create a cycle.","55000");
        Item a=ProductMergeService.find(c,location,target);
        JsonObject original=product(c,source); // a replay may already have archived the source
        JsonObject retained=mapping.has("retained_fields")?mapping.getAsJsonObject("retained_fields"):new JsonObject();
        // Shared approvals retain the original fields; each store may already
        // know that exact storage object under a different local asset UUID.
        JsonObject localPhotos=ProductImageIdentity.photos(c,retained);
        for(String field:List.of("image_url","additional_image_urls"))if(retained.has(field)&&!retained.get(field).isJsonNull()) {
            var references=new ArrayList<String>();
            if(localPhotos.get(field).isJsonArray())for(var value:localPhotos.getAsJsonArray(field))references.add(value.getAsString());
            else references.add(localPhotos.get(field).getAsString());
            for(String reference:references)if(reference.startsWith("smartstock-asset:"))try(var p=c.prepareStatement("SELECT 1 FROM image_assets WHERE asset_id=?::uuid")) {
                p.setString(1,reference.substring("smartstock-asset:".length()));try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Product merge is waiting for the kept item's photo catalog.","55000");}
            }
        }
        if(!"INVENTORY".equals(original.get("product_type").getAsString())||!original.get("group_id").isJsonNull()||!a.groupId().isBlank())
            throw new SQLException("Only ungrouped inventory products can be consolidated.","55000");
        JsonArray before=rows(c,"SELECT to_jsonb(t) FROM inventory t WHERE product_id IN (?,?) ORDER BY location_id,product_id",source,target);
        // Signed counts preserve the net stock when sales were recorded against
        // the duplicate. Check the live sum before any mapping or inventory write.
        var totals=new HashMap<Integer,Long>();
        for(JsonElement row:before){var stock=row.getAsJsonObject();totals.merge(stock.get("location_id").getAsInt(),stock.get("quantity_on_hand").getAsLong(),Long::sum);}
        for(long total:totals.values())if(total<Integer.MIN_VALUE||total>Integer.MAX_VALUE)
            throw new SQLException("Combined stock exceeds the supported quantity.","22003");
        int moved=0;for(JsonElement row:before)if(row.getAsJsonObject().get("product_id").getAsInt()==source&&row.getAsJsonObject().get("location_id").getAsInt()==location)moved=row.getAsJsonObject().get("quantity_on_hand").getAsInt();
        // Persist the shared decision before local writes, activating retirement and
        // reference guards for concurrent/stale callers within the transaction.
        try(var p=c.prepareStatement("INSERT INTO product_merges(merge_id,source_uid,target_uid,origin_location_id,merged_by_user_id,updated_at,retained_fields) VALUES(?,?,?,?,?,?::timestamptz,?::jsonb) ON CONFLICT(source_uid) DO NOTHING")) {
            p.setObject(1,mergeId);p.setObject(2,sourceUid);p.setObject(3,targetUid);p.setInt(4,mapping.get("origin_location_id").getAsInt());p.setInt(5,mapping.get("merged_by_user_id").getAsInt());p.setString(6,mapping.get("updated_at").getAsString());p.setString(7,retained.toString());p.executeUpdate();
        }
        // One addition per local row, once. No cached stock ever enters inventory.
        try(var p=c.prepareStatement("INSERT INTO inventory(product_id,location_id,quantity_on_hand,reorder_level) SELECT ?,location_id,quantity_on_hand,reorder_level FROM inventory WHERE product_id=? ON CONFLICT(product_id,location_id) DO UPDATE SET quantity_on_hand=inventory.quantity_on_hand+EXCLUDED.quantity_on_hand,updated_at=CURRENT_TIMESTAMP")) {
            p.setInt(1,target);p.setInt(2,source);p.executeUpdate();
        }
        exec(c,"UPDATE inventory SET quantity_on_hand=0,updated_at=CURRENT_TIMESTAMP WHERE product_id=?",source);
        // Move the old primary barcode, additional barcodes and SKU scan alias.
        var codes=new LinkedHashSet<String>();String primary=original.has("barcode")&&!original.get("barcode").isJsonNull()?original.get("barcode").getAsString():"";
        if(!primary.isBlank())codes.add(primary);codes.add(original.get("sku").getAsString());
        codes.addAll(a.barcodes());if(!retained.has("sku")||!a.sku().equals(retained.get("sku").getAsString()))codes.add(a.sku());
        if(retained.has("scan_aliases"))for(var code:retained.getAsJsonArray("scan_aliases"))codes.add(code.getAsString());
        for(var row:rows(c,"SELECT to_jsonb(t) FROM product_barcodes t WHERE product_id=?",source))codes.add(row.getAsJsonObject().get("barcode").getAsString());
        exec(c,"UPDATE products SET barcode=NULL WHERE product_id=?",source);exec(c,"DELETE FROM product_barcodes WHERE product_id=?",source);
        String keptPrimary=retained.has("barcode")&&!retained.get("barcode").isJsonNull()?retained.get("barcode").getAsString():"";
        if(!keptPrimary.isBlank())CatalogBarcodeService.requireAvailable(c,List.of(keptPrimary),target,null,null);
        exec(c,"UPDATE products SET barcode=? WHERE product_id=?",keptPrimary.isBlank()?null:keptPrimary,target);
        var known=new HashSet<String>(BarcodeNormalizer.lookupCandidates(keptPrimary));
        for(var row:rows(c,"SELECT to_jsonb(t) FROM product_barcodes t WHERE product_id=?",target))known.addAll(BarcodeNormalizer.lookupCandidates(row.getAsJsonObject().get("barcode").getAsString()));
        for(String code:codes) {
            var normalized=BarcodeNormalizer.lookupCandidates(code);if(!Collections.disjoint(known,normalized))continue;
            CatalogBarcodeService.requireAvailable(c,List.of(code),target,null,null);
            try(var p=c.prepareStatement("INSERT INTO product_barcodes(product_id,barcode) VALUES(?,?) ON CONFLICT(barcode) DO NOTHING")){p.setInt(1,target);p.setString(2,code);p.executeUpdate();}
            known.addAll(normalized);
        }
        try(var p=c.prepareStatement("INSERT INTO product_shelf_assignments(product_id,location_id,shelf_location_id,storage_shelf_location_id) SELECT ?,location_id,shelf_location_id,storage_shelf_location_id FROM product_shelf_assignments WHERE product_id=? ON CONFLICT(product_id,location_id) DO NOTHING")){p.setInt(1,target);p.setInt(2,source);p.executeUpdate();}
        exec(c,"DELETE FROM product_shelf_assignments WHERE product_id=?",source);
        // Legacy profit reports use the current product cost. Freeze the source
        // cost before changing its product FK, so consolidation cannot alter
        // historical profit when the two catalog records had different costs.
        exec(c,"UPDATE sale_items si SET merge_cost_price_snapshot=COALESCE(si.merge_cost_price_snapshot,p.cost_price),sku_snapshot=COALESCE(si.sku_snapshot,p.sku) FROM products p WHERE p.product_id IN (?,?) AND si.product_id=p.product_id",source,target);
        for(Ref ref:refs(c)) {
            if(ref.schema().equals("public")&&ref.table().equals("product_tags")&&ref.column().equals("product_id")) {
                exec(c,"INSERT INTO product_tags(product_id,tag_id) SELECT ?,tag_id FROM product_tags WHERE product_id=? ON CONFLICT DO NOTHING",target,source);
                exec(c,"DELETE FROM product_tags WHERE product_id=?",source);
            } else try(var p=c.prepareStatement("UPDATE "+ref.sql()+" SET "+quote(ref.column())+"=? WHERE "+quote(ref.column())+"=?")){p.setInt(1,target);p.setInt(2,source);p.executeUpdate();}
        }
        exec(c,"UPDATE stock_ledger_entries SET item_id=canonical_product_id(item_id) WHERE item_type='PRODUCT' AND item_id=?",source);
        exec(c,"UPDATE sales_tasks SET checkout_request=canonical_product_lines(checkout_request,'productId') WHERE checkout_request::text LIKE ?","%"+source+"%");
        // Monetary snapshots remain intact; only the product identity changes.
        exec(c,"UPDATE storefront.orders SET quote=canonical_product_lines(canonical_product_lines(quote,'id'),'productId') WHERE quote::text LIKE ?","%"+source+"%");
        combineWebsiteKeys(c,source,target);
        exec(c,"UPDATE products SET is_active=FALSE,archived_at=COALESCE(archived_at,CURRENT_TIMESTAMP),archive_reason=?,updated_at=CURRENT_TIMESTAMP WHERE product_id=?","Merged into product #"+target,source);
        StorefrontAdminService.unpublishArchivedProduct(c,source);
        exec(c,"UPDATE products SET updated_at=CURRENT_TIMESTAMP WHERE product_id=?",target);
        String combinedDescription=combineDescriptions(retained.has("description")?retained.get("description").getAsString():a.description(),combineDescriptions(a.description(),original.has("description")&&!original.get("description").isJsonNull()?original.get("description").getAsString():""));
        exec(c,"UPDATE products SET description=?,updated_at=CURRENT_TIMESTAMP WHERE product_id=?",combinedDescription,target);
        if(!retained.isEmpty())exec(c,"UPDATE products SET name=?,sku=?,price=?,cost_price=?,image_url=?,additional_image_urls=?::jsonb WHERE product_id=?",retained.get("name").getAsString(),retained.get("sku").getAsString(),retained.get("price").getAsBigDecimal(),retained.get("cost_price").getAsBigDecimal(),localPhotos.get("image_url").isJsonNull()?null:localPhotos.get("image_url").getAsString(),localPhotos.get("additional_image_urls").toString(),target);
        JsonObject evidence=new JsonObject();evidence.add("inventory",before);evidence.add("source",original);
        try(var p=c.prepareStatement("INSERT INTO product_merge_applications(merge_id,location_id,source_product_id,target_product_id,quantity_moved,before_state) VALUES(?,?,?,?,?,?::jsonb) ON CONFLICT(merge_id,location_id) DO NOTHING")) {
            p.setObject(1,mergeId);p.setInt(2,location);p.setInt(3,source);p.setInt(4,target);p.setInt(5,moved);p.setString(6,evidence.toString());p.executeUpdate();
        }
        // Do not announce an ordinary source archive to older/offline servers:
        // they must keep serving their own source stock until the merge command
        // atomically consolidates it. The mapping itself retires the source there.
        if(device!=null)SyncOutboxService.recordEvent(c,"PRODUCT_UPDATED",Map.of("product_id",target,"location_id",location,"user_id",mapping.get("merged_by_user_id").getAsInt()),location,device.toString(),mapping.get("merged_by_user_id").getAsInt());
    }

    private static void combineWebsiteKeys(Connection c,int source,int target)throws SQLException {
        // These compound keys may already contain both IDs; combine quantities,
        // retaining the original order/customer keys and deleting only aliases.
        try(var p=c.prepareStatement("INSERT INTO storefront.reservations(order_id,product_id,location_id,quantity) SELECT order_id,?,location_id,quantity FROM storefront.reservations WHERE product_id=? ON CONFLICT(order_id,product_id) DO UPDATE SET quantity=storefront.reservations.quantity+EXCLUDED.quantity")){p.setInt(1,target);p.setInt(2,source);p.executeUpdate();}
        exec(c,"DELETE FROM storefront.reservations WHERE product_id=?",source);
        try(var p=c.prepareStatement("INSERT INTO storefront.favorites(location_id,auth_id,product_id,created_at) SELECT location_id,auth_id,?,created_at FROM storefront.favorites WHERE product_id=? ON CONFLICT(location_id,auth_id,product_id) DO NOTHING")){p.setInt(1,target);p.setInt(2,source);p.executeUpdate();}
        exec(c,"DELETE FROM storefront.favorites WHERE product_id=?",source);
    }
    private static boolean hasReceipt(Connection c,UUID merge,int location)throws SQLException {
        try(var p=c.prepareStatement("SELECT 1 FROM product_merge_applications WHERE merge_id=? AND location_id=?")){p.setObject(1,merge);p.setInt(2,location);try(var r=p.executeQuery()){return r.next();}}
    }
    private static int id(Connection c,UUID uid)throws SQLException {
        try(var p=c.prepareStatement("SELECT product_id FROM products WHERE catalog_uid=?")){p.setObject(1,uid);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Product merge is waiting for the verified product catalog.","55000");return r.getInt(1);}}
    }
    private static UUID canonicalUid(Connection c,UUID uid)throws SQLException {
        try(var p=c.prepareStatement("SELECT canonical_product_uid(?)")){p.setObject(1,uid);try(var r=p.executeQuery()){r.next();return r.getObject(1,UUID.class);}}
    }
    private static JsonObject product(Connection c,int id)throws SQLException {
        return rows(c,"SELECT to_jsonb(t) FROM products t WHERE product_id=?",id).get(0).getAsJsonObject();
    }
    private static void requireTransaction(Connection c)throws SQLException {if(c.getAutoCommit())throw new SQLException("Product merge requires a transaction.","25000");}
    private static List<Ref> refs(Connection c)throws SQLException {
        var refs=new ArrayList<Ref>();try(var s=c.createStatement();var r=s.executeQuery("SELECT * FROM product_merge_reference_columns() ORDER BY 1,2,3")){while(r.next())refs.add(new Ref(r.getString(1),r.getString(2),r.getString(3)));}return refs;
    }
    private static void lock(Connection c)throws SQLException {
        var tables=new TreeSet<String>(List.of("public.inventory","public.product_merges","public.product_merge_applications","public.product_barcodes","public.product_shelf_assignments","public.sales_tasks","public.stock_ledger_entries","storefront.orders","storefront.reservations","storefront.products","storefront.favorites"));
        for(Ref ref:refs(c))tables.add(ref.sql());
        try(var s=c.createStatement()){s.execute("SET LOCAL lock_timeout='5s'");s.execute("SET LOCAL statement_timeout='20s'");s.execute("SELECT pg_advisory_xact_lock(728493,0)");s.execute("LOCK TABLE products IN SHARE ROW EXCLUSIVE MODE");for(String table:tables)s.execute("LOCK TABLE "+table+" IN SHARE ROW EXCLUSIVE MODE");}
    }
    private static JsonArray rows(Connection c,String sql,Object... args)throws SQLException {
        JsonArray result=new JsonArray();try(var p=c.prepareStatement(sql)){for(int n=0;n<args.length;n++)p.setObject(n+1,args[n]);try(var r=p.executeQuery()){while(r.next())result.add(JsonParser.parseString(r.getString(1)));}}return result;
    }
    private static void exec(Connection c,String sql,Object... args)throws SQLException {try(var p=c.prepareStatement(sql)){for(int n=0;n<args.length;n++)p.setObject(n+1,args[n]);p.executeUpdate();}}
    private static String extraFingerprint(Connection c,int keep,int source)throws SQLException {
        // Include mutable checkout JSON and stock-ledger references in stale-review detection.
        return rows(c,"SELECT to_jsonb(t) FROM sales_tasks t WHERE checkout_request::text LIKE ? OR checkout_request::text LIKE ? ORDER BY task_id","%"+keep+"%","%"+source+"%")+"|"+
                rows(c,"SELECT to_jsonb(t) FROM storefront.orders t WHERE quote::text LIKE ? OR quote::text LIKE ? ORDER BY order_id","%"+keep+"%","%"+source+"%")+"|"+
                rows(c,"SELECT to_jsonb(t) FROM stock_ledger_entries t WHERE item_type='PRODUCT' AND item_id IN (?,?) ORDER BY entry_id",keep,source);
    }
    static List<StoreStock> stocks(Connection c,int local,int keep,int source)throws SQLException {
        var result=new ArrayList<StoreStock>();
        try(var p=c.prepareStatement("SELECT l.location_id,l.name,k.quantity_on_hand,s.quantity_on_hand FROM locations l LEFT JOIN inventory k ON k.location_id=l.location_id AND k.product_id=? LEFT JOIN inventory s ON s.location_id=l.location_id AND s.product_id=? WHERE l.location_id=? OR k.product_id IS NOT NULL OR s.product_id IS NOT NULL ORDER BY l.location_id")){
            p.setInt(1,keep);p.setInt(2,source);p.setInt(3,local);try(var r=p.executeQuery()){while(r.next())result.add(new StoreStock(r.getInt(1),r.getString(2),(Integer)r.getObject(3),(Integer)r.getObject(4),true,"LIVE",null));}
        }
        // Cache IDs are scoped to the remote store, mapped by catalog UUID, not
        // assumed to have the same local numeric IDs.
        try(var p=c.prepareStatement("""
                SELECT st.source_location_id,st.store_name,
                  sum(CASE WHEN m.local_uid=k.catalog_uid THEN ci.quantity_on_hand END)::integer,
                  sum(CASE WHEN m.local_uid=s.catalog_uid THEN ci.quantity_on_hand END)::integer,
                  st.status,st.refreshed_at::text
                FROM sync_cross_store_inventory_status st
                LEFT JOIN sync_cross_store_inventory_cache ci ON ci.source_location_id=st.source_location_id
                LEFT JOIN sync_cross_store_source_rows pr ON pr.source_location_id=ci.source_location_id AND pr.table_name='products' AND pr.row_key->>'product_id'=ci.product_id::text
                LEFT JOIN LATERAL (
                  SELECT CASE WHEN count(DISTINCT x.local_uid)=1 THEN min(x.local_uid::text)::uuid
                    WHEN count(DISTINCT x.local_uid)=0 AND pr.row_data IS NOT NULL THEN COALESCE((pr.row_data->>'catalog_uid')::uuid,catalog_legacy_uid('products',pr.row_data)) END local_uid
                  FROM catalog_source_ids x WHERE x.entity_table='products' AND x.source_location_id=ci.source_location_id AND x.source_id=ci.product_id
                  AND (pr.row_data IS NULL OR x.source_uid=COALESCE((pr.row_data->>'catalog_uid')::uuid,catalog_legacy_uid('products',pr.row_data)))
                ) m ON true
                CROSS JOIN products k CROSS JOIN products s
                WHERE k.product_id=? AND s.product_id=? AND st.source_location_id<>?
                GROUP BY st.source_location_id,st.store_name,st.status,st.refreshed_at ORDER BY st.source_location_id
                """)){
            p.setInt(1,keep);p.setInt(2,source);p.setInt(3,local);try(var r=p.executeQuery()){while(r.next())if(result.stream().noneMatch(v->v.locationId()==getInt(r,1)))result.add(new StoreStock(r.getInt(1),r.getString(2),(Integer)r.getObject(3),(Integer)r.getObject(4),false,r.getString(5),r.getString(6)));}
        }
        return List.copyOf(result);
    }
    private static int getInt(ResultSet r,int index){try{return r.getInt(index);}catch(SQLException ex){throw new IllegalStateException(ex);}}
    private static String quote(String s){return '"'+s.replace("\"","\"\"")+'"';}
    static String combineDescriptions(String kept,String duplicate) {
        var paragraphs=new LinkedHashMap<String,String>();
        for(String text:List.of(kept==null?"":kept,duplicate==null?"":duplicate))for(String paragraph:text.split("\\R+")) {
            String value=paragraph.trim();if(!value.isBlank())paragraphs.putIfAbsent(ProductMergeService.normalize(value),value);
        }
        return String.join("\n",paragraphs.values());
    }
}
