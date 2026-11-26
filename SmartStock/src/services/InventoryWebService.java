package services;

import com.google.gson.JsonObject;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only catalogue shared by the LAN app and the approved employee gateway. */
final class InventoryWebService {
    static final String PUBLIC_URL="https://inventory.deckers.gy/employees/inventory.html";
    private InventoryWebService() { }

    static Object search(Connection c, int user, int location, JsonObject body) throws Exception {
        var result=page(c,user,location,body);
        result.put("branding",OrdersWebBranding.load(location));
        return result;
    }
    static Map<String,Object> page(Connection c,int user,int location,JsonObject body)throws Exception {
        if (!LanInventoryService.hasPermission(c,user,"VIEW_INVENTORY"))
            throw new SecurityException("Your account needs View Inventory permission.");
        try(var p=c.prepareStatement("SELECT 1 FROM user_locations WHERE user_id=? AND location_id=?")) {
            p.setInt(1,user);p.setInt(2,location);
            try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("This store is not assigned to your account.");}
        }
        String query=value(body,"query"),kind=value(body,"kind"),stock=value(body,"stock");
        if(query.length()>300 || !java.util.Set.of("","product","custom").contains(kind)
                || !java.util.Set.of("","in","low","out").contains(stock))
            throw new IllegalArgumentException("Invalid inventory search.");
        int offset=body.has("offset")?body.get("offset").getAsInt():0;
        if(offset<0||offset>1000000)throw new IllegalArgumentException("Invalid page.");
        String sql=("""
            SELECT * FROM (
              SELECT 'product' kind,p.product_id id,p.name,COALESCE(p.description,'') description,
                COALESCE(p.sku,'') sku,COALESCE(p.barcode,'') barcode,COALESCE(p.size,'') size,
                COALESCE(p.color,'') color,COALESCE(p.price,0) price,
                COALESCE(i.quantity_on_hand,0) quantity,COALESCE(i.reorder_level,0) reorder,
                COALESCE(p.product_type,'INVENTORY') product_type,'FIXED' pricing_type,'' area_price_unit,COALESCE(item_classification.name,'') item_type_name
              FROM products p LEFT JOIN inventory i ON i.product_id=p.product_id AND i.location_id=?
              LEFT JOIN item_types item_classification ON item_classification.item_type_id=p.item_type_id
              WHERE %s
              UNION ALL
              SELECT 'custom',coi.custom_item_id,coi.item_name,COALESCE(coi.description,''),
                COALESCE(coi.sku,''),COALESCE(coi.barcode,''),COALESCE(coi.size,''),COALESCE(coi.color,''),
                COALESCE(coi.fixed_price,coi.area_price,0),COALESCE(custom_stock_value(coi.custom_item_id,NULL,__CUSTOM_STORE__,'quantity_on_hand'),0),
                COALESCE(custom_stock_value(coi.custom_item_id,NULL,__CUSTOM_STORE__,'reorder_level'),0),COALESCE(coi.product_type,'INVENTORY'),coi.pricing_type,COALESCE(coi.area_price_unit,''),COALESCE(item_classification.name,'')
              FROM custom_order_items coi LEFT JOIN item_types item_classification ON item_classification.item_type_id=coi.item_type_id WHERE coi.is_active=TRUE AND coi.is_archived=FALSE
                AND COALESCE(coi.has_variants,FALSE)=FALSE AND %s
              UNION ALL
              SELECT 'variant',v.custom_variant_id,CONCAT_WS(' · ',coi.item_name,v.variant_name),
                COALESCE(coi.description,''),COALESCE(v.sku,''),COALESCE(v.barcode,''),
                COALESCE(NULLIF(v.size,''),coi.size,''),COALESCE(NULLIF(v.color,''),coi.color,''),
                COALESCE(v.fixed_price,coi.fixed_price,coi.area_price,0),COALESCE(custom_stock_value(v.custom_item_id,v.custom_variant_id,__CUSTOM_STORE__,'quantity_on_hand'),0),
                COALESCE(custom_stock_value(v.custom_item_id,v.custom_variant_id,__CUSTOM_STORE__,'reorder_level'),0),COALESCE(coi.product_type,'INVENTORY'),CASE WHEN v.fixed_price IS NOT NULL THEN 'FIXED' ELSE coi.pricing_type END,COALESCE(coi.area_price_unit,''),COALESCE(item_classification.name,'')
              FROM custom_order_item_variants v JOIN custom_order_items coi ON coi.custom_item_id=v.custom_item_id
              LEFT JOIN item_types item_classification ON item_classification.item_type_id=coi.item_type_id
              WHERE v.is_active=TRUE AND coi.is_active=TRUE AND coi.is_archived=FALSE AND %s
            ) catalogue
            WHERE (?='' OR (?='product' AND kind='product') OR (?='custom' AND kind IN ('custom','variant')))
              AND (?='' OR (product_type='INVENTORY' AND
                ((?='in' AND quantity>0) OR (?='low' AND quantity>0 AND quantity<=reorder) OR (?='out' AND quantity<=0))))
            ORDER BY lower(name),kind,id LIMIT 61 OFFSET ?
            """).replace("__CUSTOM_STORE__",Integer.toString(location)).formatted(ProductSearchHelper.predicate("p",location,query,LanInventoryService.hasPermission(c,user,"VIEW_VENDOR")),
                ProductSearchHelper.customItemPredicate("coi",query),ProductSearchHelper.customVariantPredicate("coi","v",query));
        var rows=new ArrayList<Map<String,Object>>();
        try(var p=c.prepareStatement(sql)) {
            p.setInt(1,location);int n=ProductSearchHelper.bindTokens(p,2,query);
            n=ProductSearchHelper.bindTokens(p,n,query);n=ProductSearchHelper.bindTokens(p,n,query);
            for(String s:new String[]{kind,kind,kind,stock,stock,stock,stock})p.setString(n++,s);
            p.setInt(n,offset);
            try(var r=p.executeQuery()) {
                while(r.next()) {
                    var row=new LinkedHashMap<String,Object>();
                    for(String key:new String[]{"kind","id","name","description","sku","barcode","size","color","price","quantity","reorder","product_type","pricing_type","area_price_unit","item_type_name"})
                        row.put(key,r.getObject(key));
                    rows.add(row);
                }
            }
        }
        boolean more=rows.size()>60;if(more)rows.remove(60);
        return new LinkedHashMap<>(Map.of("items",rows,"hasMore",more,"nextOffset",offset+rows.size(),"locationId",location));
    }
    private static String value(JsonObject b,String key){return b.has(key)&&!b.get(key).isJsonNull()?b.get(key).getAsString().trim():"";}
}
