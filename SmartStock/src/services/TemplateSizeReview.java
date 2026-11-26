package services;

import com.google.gson.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Missing printable-area work, with template-only optimistic writes. */
public final class TemplateSizeReview {
    private TemplateSizeReview(){}
    public record Entry(long itemId,Long variantId,String parent,String name,String type,String size,
                        String photo,BigDecimal width,BigDecimal height,String unit,boolean sublimation) {
        @Override public String toString(){return name+(variantId==null?"":" · variant #"+variantId);}
    }
    public static List<Entry> pending(JsonObject state,boolean sublimationOnly){
        List<Entry> out=new ArrayList<>();
        if(!state.has("items"))return out;
        for(var value:state.getAsJsonArray("items")){
            var item=value.getAsJsonObject();
            if(!flag(item,"active")||flag(item,"archived")||flag(item,"isShirt")||!"INVENTORY".equals(text(item,"productType"))||sublimationOnly&&!flag(item,"isSublimation"))continue;
            if(flag(item,"hasVariants")){
                if(item.has("variants"))for(var child:item.getAsJsonArray("variants")){
                    var variant=child.getAsJsonObject();if(flag(variant,"active"))add(out,item,variant);
                }
            }else add(out,item,item);
        }
        out.sort(Comparator.comparing(Entry::type,String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::parent,String.CASE_INSENSITIVE_ORDER).thenComparing(Entry::name,String.CASE_INSENSITIVE_ORDER));
        return out;
    }
    private static void add(List<Entry> out,JsonObject parent,JsonObject row){
        BigDecimal width=number(row,"templateWidth"),height=number(row,"templateHeight");String unit=row.has("templateUnit")&&!row.get("templateUnit").isJsonNull()?row.get("templateUnit").getAsString():null;
        if(complete(width,height,unit))return;
        String photo=text(row,"imageUrl");if(photo.isBlank())photo=text(parent,"imageUrl");
        out.add(new Entry(parent.get("itemId").getAsLong(),row==parent?null:row.get("variantId").getAsLong(),text(parent,"name"),text(row,"name"),text(parent,"itemType"),text(row,"size"),photo,width,height,unit,flag(parent,"isSublimation")));
    }
    public static boolean complete(BigDecimal width,BigDecimal height,String unit){
        return width!=null&&height!=null&&width.signum()>0&&height.signum()>0&&Set.of("IN","CM","MM").contains(Objects.toString(unit,""));
    }
    public static long save(Connection c,JsonObject request)throws SQLException {
        BigDecimal width=number(request,"templateWidth"),height=number(request,"templateHeight");String unit=text(request,"templateUnit");
        if(!complete(width,height,unit)||width.compareTo(new BigDecimal("10000"))>0||height.compareTo(new BigDecimal("10000"))>0||width.scale()>3||height.scale()>3)throw new IllegalArgumentException("Enter positive printable width and height (up to 10000, with at most 3 decimals), and IN, CM or MM.");
        long itemId=request.get("itemId").getAsLong();boolean variant=request.has("variantId")&&!request.get("variantId").isJsonNull();
        String sql=variant?"""
            UPDATE custom_order_item_variants v SET template_width=?,template_height=?,template_unit=?
            FROM custom_order_items i WHERE v.custom_item_id=i.custom_item_id AND i.custom_item_id=? AND v.custom_variant_id=?
              AND v.is_active AND i.is_active AND NOT i.is_archived AND i.has_variants AND NOT i.is_shirt AND i.product_type='INVENTORY'
              AND v.template_width IS NOT DISTINCT FROM ? AND v.template_height IS NOT DISTINCT FROM ? AND v.template_unit IS NOT DISTINCT FROM ?
            """:"""
            UPDATE custom_order_items SET template_width=?,template_height=?,template_unit=?,updated_at=CURRENT_TIMESTAMP
            WHERE custom_item_id=? AND is_active AND NOT is_archived AND NOT has_variants AND NOT is_shirt AND product_type='INVENTORY'
              AND template_width IS NOT DISTINCT FROM ? AND template_height IS NOT DISTINCT FROM ? AND template_unit IS NOT DISTINCT FROM ?
            """;
        try(var ps=c.prepareStatement(sql)){
            int p=1;ps.setBigDecimal(p++,width);ps.setBigDecimal(p++,height);ps.setString(p++,unit);ps.setLong(p++,itemId);
            if(variant)ps.setLong(p++,request.get("variantId").getAsLong());
            ps.setBigDecimal(p++,number(request,"expectedWidth"));ps.setBigDecimal(p++,number(request,"expectedHeight"));
            ps.setString(p,request.has("expectedUnit")&&!request.get("expectedUnit").isJsonNull()?request.get("expectedUnit").getAsString():null);
            if(ps.executeUpdate()!=1)throw new IllegalArgumentException("This item changed or is no longer available. Close the review and refresh before trying again.");
        }
        return variant?request.get("variantId").getAsLong():itemId;
    }
    public static JsonObject request(Entry entry,BigDecimal width,BigDecimal height,String unit){
        var out=new JsonObject();out.addProperty("itemId",entry.itemId());if(entry.variantId()!=null)out.addProperty("variantId",entry.variantId());
        out.addProperty("templateWidth",width);out.addProperty("templateHeight",height);out.addProperty("templateUnit",unit);
        out.addProperty("expectedWidth",entry.width());out.addProperty("expectedHeight",entry.height());out.addProperty("expectedUnit",entry.unit());return out;
    }
    private static String text(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    private static boolean flag(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()&&o.get(key).getAsBoolean();}
    private static BigDecimal number(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsBigDecimal():null;}
}
