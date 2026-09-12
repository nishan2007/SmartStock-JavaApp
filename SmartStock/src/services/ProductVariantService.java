package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;

/** Group metadata never replaces a sellable product or changes its inventory. */
final class ProductVariantService {
    private static final Gson JSON = new Gson();
    private ProductVariantService() { }

    static void validateColorEdit(Connection c,int productId,String color)throws Exception {
        try(PreparedStatement ps=c.prepareStatement("SELECT variant_options FROM products WHERE product_id=? AND group_id IS NOT NULL")) {
            ps.setInt(1,productId);try(ResultSet rs=ps.executeQuery()){if(rs.next()) {
                for(var option:JsonParser.parseString(rs.getString(1)).getAsJsonObject().entrySet())
                    if(option.getKey().equalsIgnoreCase("Color")&&!option.getValue().getAsString().equals(color))
                        throw invalid("Change Color through Manage variants so option combinations stay consistent.");
            }}
        }
    }

    static void validateFlavorEdit(Connection c,int productId,String flavor)throws Exception {
        try(PreparedStatement ps=c.prepareStatement("SELECT variant_options FROM products WHERE product_id=? AND group_id IS NOT NULL")) {
            ps.setInt(1,productId);try(ResultSet rs=ps.executeQuery()){if(rs.next()) {
                for(var option:JsonParser.parseString(rs.getString(1)).getAsJsonObject().entrySet())
                    if(option.getKey().equalsIgnoreCase("Flavor")&&!option.getValue().getAsString().equals(flavor))
                        throw invalid("Change Flavor through Manage variants so option combinations stay consistent.");
            }}
        }
    }

    static void validateItemEdit(Connection c,int productId,String size,String productType) throws Exception {
        try(PreparedStatement ps=c.prepareStatement("SELECT p.variant_options,g.option_names FROM products p JOIN product_groups g ON g.group_id=p.group_id WHERE p.product_id=?")) {
            ps.setInt(1,productId);try(ResultSet rs=ps.executeQuery()) {if(!rs.next())return;
                if(productType!=null&&!"INVENTORY".equals(productType))throw invalid("Detach this variant before changing its product type.");
                JsonObject options=JsonParser.parseString(rs.getString(1)).getAsJsonObject();
                if(size!=null)for(JsonElement element:JsonParser.parseString(rs.getString(2)).getAsJsonArray()) {
                    String key=element.getAsString();if(key.equalsIgnoreCase("Size")&&!size.equals(options.get(key).getAsString()))throw invalid("Change the Size option through Manage variants so combinations stay consistent.");
                }
            }
        }
    }

    static void annotate(Connection c,List<Map<String,Object>> rows) throws SQLException {
        if(rows.isEmpty())return;
        Map<Integer,Map<String,Object>> byId=new HashMap<>();
        for(var row:rows)byId.put(((Number)row.get("productId")).intValue(),row);
        String placeholders=String.join(",",Collections.nCopies(byId.size(),"?"));
        try(PreparedStatement ps=c.prepareStatement("SELECT p.product_id,g.group_id,g.name,g.option_names,g.revision,p.variant_options FROM products p JOIN product_groups g ON g.group_id=p.group_id WHERE p.product_id IN ("+placeholders+")")) {
            int index=1;for(int id:byId.keySet())ps.setInt(index++,id);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())byId.get(rs.getInt(1)).put("variant",Map.of("groupId",rs.getString(2),"groupName",rs.getString(3),"optionNames",JsonParser.parseString(rs.getString(4)),"revision",rs.getLong(5),"options",JsonParser.parseString(rs.getString(6))));}
        }
    }

    static Map<String,Object> list(Connection c, int userId,int locationId) throws Exception {
        LanProductAdminService.requireAnyPermission(c,userId,"VIEW_INVENTORY","EDIT_ITEM","NEW_ITEM","MAKE_SALE","RECEIVING_INVENTORY");
        List<Map<String,Object>> groups = new ArrayList<>();
        try (PreparedStatement ps=c.prepareStatement("SELECT group_id,name,option_names,revision,barcode,additional_barcodes FROM product_groups ORDER BY name,group_id"); ResultSet rs=ps.executeQuery()) {
            while(rs.next()) {
                Map<String,Object> group=new LinkedHashMap<>();
                group.put("groupId",rs.getString(1)); group.put("name",rs.getString(2));
                group.put("optionNames",JsonParser.parseString(rs.getString(3))); group.put("revision",rs.getLong(4));
                group.put("barcode",rs.getString(5));group.put("additionalBarcodes",JsonParser.parseString(rs.getString(6)));
                groups.add(group);
            }
        }
        Map<String,List<Map<String,Object>>> members=new HashMap<>();
        try(PreparedStatement ps=c.prepareStatement("SELECT p.group_id,p.product_id,p.variant_options,p.is_active,p.image_url,p.name,COALESCE(i.quantity_on_hand,0),p.price FROM products p LEFT JOIN inventory i ON i.product_id=p.product_id AND i.location_id=? WHERE p.group_id IS NOT NULL ORDER BY p.product_id")) {
            ps.setInt(1,locationId);try(ResultSet rs=ps.executeQuery()) {
            while(rs.next()) {
                Map<String,Object> row=new LinkedHashMap<>();row.put("productId",rs.getInt(2));
                row.put("options",JsonParser.parseString(rs.getString(3)));row.put("active",rs.getBoolean(4));
                row.put("imageUrl",rs.getString(5));row.put("name",rs.getString(6));row.put("quantityOnHand",rs.getInt(7));row.put("price",rs.getBigDecimal(8));
                members.computeIfAbsent(rs.getString(1),key->new ArrayList<>()).add(row);
            }
        }
        }
        for(Map<String,Object> group:groups) group.put("members",members.getOrDefault(group.get("groupId"),List.of()));
        return Map.of("groups",groups);
    }

    static List<String> validateOptionNames(List<String> names) throws LanProductAdminService.RuleViolation {
        if(names==null||names.isEmpty()||names.size()>6)throw invalid("Define between one and six options.");
        List<String> result=new ArrayList<>(); Set<String> seen=new HashSet<>();
        for(String name:names) {
            String value=clean(name,60);
            if(!seen.add(value.toLowerCase(Locale.ROOT)))throw invalid("Option names must be unique.");
            result.add(value);
        }
        return List.copyOf(result);
    }

    static Map<String,String> normalizeOptions(List<String> names,Map<String,String> options) throws LanProductAdminService.RuleViolation {
        if(options==null||options.size()!=names.size())throw invalid("Every variant needs a value for each option.");
        Map<String,String> result=new LinkedHashMap<>();
        boolean hasSeparator=false;
        for(String name:names) {
            String value=standardSeparator(name)?cleanOptional(options.get(name),200):clean(options.get(name),200);
            result.put(name,value);if(standardSeparator(name)&&!value.isBlank())hasSeparator=true;
        }
        if(names.stream().anyMatch(ProductVariantService::standardSeparator)&&!hasSeparator)
            throw invalid("Each variant needs at least one Size, Color, or Flavor value.");
        return result;
    }

    static String combinationKey(Map<String,String> options) {
        List<String> values=new ArrayList<>();
        options.forEach((k,v)->values.add(v.toLowerCase(Locale.ROOT)));
        return JSON.toJson(values);
    }

    static Map<String,Object> save(Connection c,JsonObject body,UUID device,int user,String userName,int location) throws Exception {
        LanProductAdminService.requireDeviceId(device);
        Request request=JSON.fromJson(body,Request.class);
        if(request==null)throw invalid("Supply a product group.");
        List<String> names=validateOptionNames(request.optionNames());String name=clean(request.name(),200);
        if(request.members()==null||(request.members().isEmpty()&&request.groupId()==null)||request.members().size()>500)throw invalid("A new group needs between one and 500 variants.");
        boolean existing=request.groupId()!=null;
        boolean attaches=request.members().stream().anyMatch(m->m!=null&&m.productId()!=null);
        if(existing||attaches)LanProductAdminService.requirePermission(c,user,"EDIT_ITEM");
        if(request.members().stream().anyMatch(m->m!=null&&m.productId()==null))LanProductAdminService.requirePermission(c,user,"NEW_ITEM");
        UUID group=existing?request.groupId():UUID.randomUUID();
        long revision=1;
        if(existing)try(PreparedStatement ps=c.prepareStatement("SELECT revision FROM product_groups WHERE group_id=? FOR UPDATE")) {
            ps.setObject(1,group);try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next())throw conflict("This group no longer exists. Reload the inventory.");
                revision=rs.getLong(1)+1;
                if(request.expectedRevision()==null||request.expectedRevision()!=revision-1)throw conflict("This group changed on another register. Reload before saving.");
            }
        }
        Set<String> combinations=new HashSet<>();Set<Integer> ids=new TreeSet<>();
        List<Map<String,String>> options=new ArrayList<>();
        for(Member member:request.members()) {
            if(member==null)throw invalid("Invalid variant.");
            Map<String,String> normalized=normalizeOptions(names,member.options());options.add(normalized);
            if(!combinations.add(combinationKey(normalized)))throw invalid("Two variants have the same option combination.");
            if(member.productId()!=null&&(member.productId()<=0||!ids.add(member.productId())))throw invalid("An item was selected more than once.");
        }
        // Omitted fields from older registers preserve the current group barcodes.
        String barcode="";List<String> additional=new ArrayList<>();
        if(existing)try(PreparedStatement ps=c.prepareStatement("SELECT barcode,additional_barcodes FROM product_groups WHERE group_id=?")) {
            ps.setObject(1,group);try(ResultSet rs=ps.executeQuery()){if(rs.next()){
                barcode=rs.getString(1);for(JsonElement value:JsonParser.parseString(rs.getString(2)).getAsJsonArray())additional.add(value.getAsString());
            }}
        }
        if(body.has("barcode"))barcode=normalizeBarcode(request.barcode());
        if(body.has("additionalBarcodes")) {
            additional=new ArrayList<>();
            if(request.additionalBarcodes()!=null)for(String value:request.additionalBarcodes()) {
                String normalized=normalizeBarcode(value);if(!normalized.isEmpty())additional.add(normalized);
            }
        }
        if(additional.size()>100)throw invalid("Use at most 100 additional group barcodes.");
        List<String> allBarcodes=new ArrayList<>(additional);allBarcodes.add(barcode);
        CatalogBarcodeService.requireAvailable(c,allBarcodes,null,null,null,group);
        // Lock in stable product order before attaching or detaching; racing groups cannot steal members.
        Set<Integer> affected=new TreeSet<>(ids);
        if(existing)try(PreparedStatement ps=c.prepareStatement("SELECT product_id FROM products WHERE group_id=?")) {
            ps.setObject(1,group);try(ResultSet rs=ps.executeQuery()){while(rs.next())affected.add(rs.getInt(1));}
        }
        for(int id:affected)try(PreparedStatement ps=c.prepareStatement("SELECT group_id,product_type,is_active FROM products WHERE product_id=? FOR UPDATE")) {
            ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next())throw conflict("An item no longer exists.");
                UUID previous=rs.getObject(1,UUID.class);
                if(previous!=null&&!previous.equals(group))throw conflict("An item already belongs to another group. Detach it there first.");
                if(ids.contains(id)&&!"INVENTORY".equals(rs.getString(2)))throw invalid("Only inventory products can be grouped.");
                if(ids.contains(id)&&!rs.getBoolean(3)&&previous==null)throw invalid("Restore archived items before attaching them.");
            }
        }
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO product_groups(group_id,name,option_names,revision) VALUES (?,?,?::jsonb,?) ON CONFLICT(group_id) DO UPDATE SET name=EXCLUDED.name,option_names=EXCLUDED.option_names,revision=EXCLUDED.revision,updated_at=CURRENT_TIMESTAMP")) {
            ps.setObject(1,group);ps.setString(2,name);ps.setString(3,JSON.toJson(names));ps.setLong(4,revision);ps.executeUpdate();
        }
        try(PreparedStatement ps=c.prepareStatement("UPDATE product_groups SET barcode=?,additional_barcodes=?::jsonb WHERE group_id=?")) {
            ps.setString(1,barcode);ps.setString(2,JSON.toJson(additional));ps.setObject(3,group);ps.executeUpdate();
        }
        // Clear membership inside this transaction to permit swapping option combinations.
        try(PreparedStatement ps=c.prepareStatement("UPDATE products SET group_id=NULL,variant_options='{}'::jsonb,updated_at=CURRENT_TIMESTAMP WHERE group_id=?")) {ps.setObject(1,group);ps.executeUpdate();}
        int index=0;
        for(Member member:request.members()) {
            Map<String,String> values=options.get(index++);Integer id=member.productId();
            if(id==null) {
                if(member.product()==null)throw invalid("New variants need item details.");
                JsonObject product=member.product().deepCopy();product.addProperty("productType","INVENTORY");
                if(!product.has("barcode")||product.get("barcode").getAsString().isBlank())product.addProperty("barcode",CatalogBarcodeService.generateAvailable(c));
                product.addProperty("name",name+" — "+values.values().stream().filter(value->!value.isBlank()).collect(java.util.stream.Collectors.joining(" · ")));
                for(String option:names)if(option.equalsIgnoreCase("Size"))product.addProperty("size",values.get(option));
                for(String option:names)if(option.equalsIgnoreCase("Color"))product.addProperty("color",values.get(option));
                for(String option:names)if(option.equalsIgnoreCase("Flavor"))product.addProperty("flavor",values.get(option));
                id=((Number)LanProductAdminService.create(c,product,device,user,userName,location).get("productId")).intValue();
                affected.add(id);
            }
            String size=null;for(String option:names)if(option.equalsIgnoreCase("Size"))size=values.get(option);
            for(String option:names)if(option.equalsIgnoreCase("Color")) {
                try(PreparedStatement ps=c.prepareStatement("UPDATE products SET color=? WHERE product_id=?")) {
                    ps.setString(1,values.get(option));ps.setInt(2,id);ps.executeUpdate();
                }
            }
            for(String option:names)if(option.equalsIgnoreCase("Flavor")) {
                try(PreparedStatement ps=c.prepareStatement("UPDATE products SET flavor=? WHERE product_id=?")) {
                    ps.setString(1,values.get(option));ps.setInt(2,id);ps.executeUpdate();
                }
            }
            try(PreparedStatement ps=c.prepareStatement("UPDATE products SET group_id=?,variant_options=?::jsonb,size=CASE WHEN ? IS NULL THEN size ELSE ? END,updated_at=CURRENT_TIMESTAMP WHERE product_id=?")) {
                ps.setObject(1,group);ps.setString(2,JSON.toJson(values));ps.setString(3,size);ps.setString(4,size);ps.setInt(5,id);ps.executeUpdate();
            }
        }
        for(int id:affected)SyncOutboxService.recordEvent(c,"PRODUCT_UPDATED",Map.of("product_id",id,"location_id",location,"user_id",user),location,device.toString(),user);
        LanProductAdminService.audit(c,"LAN_PRODUCT_GROUP_SAVED",device,user,"group_id="+group+"; revision="+revision+"; products="+affected);
        return Map.of("groupId",group.toString(),"revision",revision);
    }

    private static String clean(String value,int max) throws LanProductAdminService.RuleViolation {if(value==null||value.isBlank()||value.trim().length()>max)throw invalid("Option values and names must be nonempty and within the allowed length.");return value.trim();}
    private static String cleanOptional(String value,int max) throws LanProductAdminService.RuleViolation {String cleaned=value==null?"":value.trim();if(cleaned.length()>max)throw invalid("Option values and names must be within the allowed length.");return cleaned;}
    private static boolean standardSeparator(String name){return name!=null&&(name.equalsIgnoreCase("Size")||name.equalsIgnoreCase("Color")||name.equalsIgnoreCase("Flavor"));}
    private static LanProductAdminService.RuleViolation invalid(String text){return new LanProductAdminService.RuleViolation(400,"VALIDATION_ERROR",text);}
    private static LanProductAdminService.RuleViolation conflict(String text){return new LanProductAdminService.RuleViolation(409,"GROUP_CHANGED",text);}
    static String normalizeBarcode(String value)throws LanProductAdminService.RuleViolation {
        String normalized=BarcodeNormalizer.normalize(value);
        if(normalized.length()>200)throw invalid("A group barcode must be at most 200 characters.");
        return normalized;
    }
    record Request(UUID groupId,Long expectedRevision,String name,List<String> optionNames,List<Member> members,String barcode,List<String> additionalBarcodes) { }
    record Member(Integer productId,Map<String,String> options,JsonObject product) { }
}
