package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Administrator-reviewed catalog aliases with independent per-store consolidation. */
public final class ProductMergeService {
    private static final Gson JSON = new Gson();
    private ProductMergeService() { }

    public record Item(int id, String name, String sku, String size, String color, String flavor,
                       String brand, String department, String description, BigDecimal price,
                       BigDecimal cost, int quantity, String createdBy, String createdAt,
                       List<String> barcodes, String snapshot, String groupId) { }
    public record Candidate(Item first, Item second, String reason) { }
    public record Candidates(List<Candidate> pairs, boolean truncated, int locationId) { }
    public record StoreStock(int locationId,String name,Integer keepQuantity,Integer sourceQuantity,
                             boolean live,String status,String capturedAt) { }
    public record Review(Item keep, Item source, List<String> blockers, String fingerprint,
                         int locationId, long combinedQuantity,List<StoreStock> stores,
                         Map<String,Long> references,List<String> warnings) { }

    static void requireAdmin(Connection c, int user) throws SQLException {
        try (var p=c.prepareStatement("SELECT 1 FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.user_id=? AND u.is_active AND UPPER(r.role_name)='ADMIN'")) {
            p.setInt(1,user);try(var r=p.executeQuery()) {
                if(!r.next())throw new SQLException("Only an administrator can review and merge inventory duplicates.","42501");
            }
        }
    }

    public static Candidates candidates(Connection c,int user,int location,String search) throws SQLException {
        requireAdmin(c,user);
        // Search is applied before the bound, so a large catalogue remains reviewable.
        var items=new ArrayList<Item>();
        try(var p=c.prepareStatement(itemSql()+" AND (?='' OR p.name ILIKE ? OR p.sku ILIKE ?) ORDER BY p.product_id LIMIT 5001")) {
            p.setInt(1,location);String term=search==null?"":search.trim();
            if(term.length()>200)throw new SQLException("Search must be at most 200 characters.","22023");
            p.setString(2,term);p.setString(3,"%"+term+"%");p.setString(4,"%"+term+"%");
            try(var r=p.executeQuery()){while(r.next())items.add(read(r));}
        }
        boolean truncated=items.size()>5000;if(truncated)items.remove(items.size()-1);
        var buckets=new LinkedHashMap<String,List<Item>>();
        for(Item item:items) {
            if(!normalize(item.name()).isBlank())buckets.computeIfAbsent("N:"+normalize(item.name()),k->new ArrayList<>()).add(item);
            for(String barcode:item.barcodes())for(String key:BarcodeNormalizer.lookupCandidates(barcode))
                if(!key.isBlank())buckets.computeIfAbsent("B:"+key,k->new ArrayList<>()).add(item);
        }
        var pairs=new LinkedHashMap<String,Candidate>();
        outer: for(var bucket:buckets.values())for(int i=0;i<bucket.size();i++)for(int j=i+1;j<bucket.size();j++) {
            Item a=bucket.get(i),b=bucket.get(j);if(a.id()==b.id())continue;
            String reason=matchReason(a,b);if(reason.isEmpty())continue;
            int lo=Math.min(a.id(),b.id()),hi=Math.max(a.id(),b.id());
            pairs.putIfAbsent(lo+":"+hi,new Candidate(a.id()==lo?a:b,a.id()==lo?b:a,reason));
            if(pairs.size()>=500){truncated=true;break outer;}
        }
        return new Candidates(List.copyOf(pairs.values()),truncated,location);
    }

    static String normalize(String text) {
        return text==null?"":java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").trim();
    }
    static String matchReason(Item a,Item b) {
        for(String x:a.barcodes())for(String y:b.barcodes())
            if(!Collections.disjoint(BarcodeNormalizer.lookupCandidates(x),BarcodeNormalizer.lookupCandidates(y)))return "Matching barcode (check variants)";
        if(!normalize(a.name()).isEmpty()&&normalize(a.name()).equals(normalize(b.name()))
                &&normalize(a.size()).equals(normalize(b.size()))&&normalize(a.color()).equals(normalize(b.color()))
                &&normalize(a.flavor()).equals(normalize(b.flavor()))&&normalize(a.brand()).equals(normalize(b.brand())))
            return "Matching name, brand, size, color and flavor";
        return "";
    }

    public static Review review(Connection c,int user,int location,int keepId,int sourceId) throws SQLException {
        return ProductMergeCoordinator.review(c,user,location,keepId,sourceId);
    }

    public static Map<String,Object> merge(Connection c,int user,int location,UUID device,int keep,int source,String fingerprint) throws Exception {
        return ProductMergeCoordinator.merge(c,user,location,device,keep,source,fingerprint);
    }

    static Item find(Connection c,int location,int id) throws SQLException {
        try(var p=c.prepareStatement(itemSql()+" AND p.product_id=?")) {
            p.setInt(1,location);p.setInt(2,id);try(var r=p.executeQuery()) {
                if(!r.next())throw new SQLException("An item is archived, unavailable or no longer an inventory product. Refresh the review.","40001");
                return read(r);
            }
        }
    }
    private static String itemSql() {
        return """
                SELECT p.product_id,p.name,p.sku,COALESCE(p.size,''),COALESCE(p.color,''),COALESCE(p.flavor,''),
                  COALESCE(b.name,''),COALESCE(d.name,''),COALESCE(p.description,''),p.price,p.cost_price,
                  COALESCE(i.quantity_on_hand,0),COALESCE(p.created_by_name,''),p.created_at::text,
                  COALESCE((SELECT jsonb_agg(code ORDER BY code)::text FROM
                    (SELECT p.barcode code WHERE COALESCE(btrim(p.barcode),'')<>'' UNION
                     SELECT barcode FROM product_barcodes WHERE product_id=p.product_id AND btrim(barcode)<>'') codes),'[]'),
                  jsonb_build_object('product',to_jsonb(p),'inventory',to_jsonb(i))::text,COALESCE(p.group_id::text,'')
                FROM products p LEFT JOIN inventory i ON i.product_id=p.product_id AND i.location_id=?
                LEFT JOIN item_brands b ON b.brand_id=p.brand_id LEFT JOIN categories d ON d.category_id=p.category_id
                WHERE p.is_active AND p.product_type='INVENTORY' AND p.sku<>'SMARTSTOCK-MISC'
                """;
    }
    private static Item read(ResultSet r) throws SQLException {
        List<String> codes=Arrays.asList(JSON.fromJson(r.getString(15),String[].class));
        return new Item(r.getInt(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),
                r.getString(7),r.getString(8),r.getString(9),r.getBigDecimal(10),r.getBigDecimal(11),r.getInt(12),
                r.getString(13),r.getString(14),codes,r.getString(16),r.getString(17));
    }
}
