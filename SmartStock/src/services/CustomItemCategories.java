package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;
import static services.StorefrontService.*;

/** Shared custom-item categories owned by the local store, also used by its website. */
public final class CustomItemCategories {
    private CustomItemCategories() { }
    public static JsonArray list(Connection c)throws SQLException {
        return rows(c,"SELECT category_id AS id,name,revision FROM storefront.custom_item_categories ORDER BY lower(name),category_id");
    }
    static void save(Connection c,JsonObject body)throws SQLException {
        String name=text(body,"name").trim();
        if(name.isEmpty()||name.length()>80||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Enter a category name of 1 to 80 characters.");
        if(Set.of("custom t-shirts","laser engraving").contains(name.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("That custom item category already exists.");
        for(var builtIn:CustomItemCategory.values())if(builtIn.toString().equalsIgnoreCase(name))throw new IllegalArgumentException("That custom item category already exists.");
        if(!rows(c,"SELECT 1 FROM storefront.custom_item_categories WHERE lower(btrim(name))=lower(?) AND category_id<>?",name,UUID.fromString(text(body,"id"))).isEmpty())throw new IllegalArgumentException("That custom item category already exists.");
        UUID id=UUID.fromString(text(body,"id"));long revision=body.get("revision").getAsLong();
        if(revision==0)execute(c,"INSERT INTO storefront.custom_item_categories(category_id,name) VALUES(?,?)",id,name);
        else if(execute(c,"UPDATE storefront.custom_item_categories SET name=?,revision=revision+1 WHERE category_id=? AND revision=?",name,id,revision)!=1)throw new IllegalArgumentException("This category changed. Refresh and try again.");
    }
    static void validateAssignment(Connection c,String id,Boolean...flags)throws SQLException {
        if(id==null||id.isBlank())return;
        for(Boolean flag:flags)if(Boolean.TRUE.equals(flag))throw new IllegalArgumentException("Choose only one custom item category.");
        if(rows(c,"SELECT 1 FROM storefront.custom_item_categories WHERE category_id=?",UUID.fromString(id)).isEmpty())throw new IllegalArgumentException("Choose an existing custom item category.");
    }
    static void assign(Connection c,long item,String id)throws SQLException {
        execute(c,"DELETE FROM storefront.custom_item_category_assignments WHERE custom_item_id=?",item);
        if(id!=null&&!id.isBlank())execute(c,"INSERT INTO storefront.custom_item_category_assignments(custom_item_id,category_id) VALUES(?,?)",item,UUID.fromString(id));
    }
    static JsonArray catalog(Connection c)throws SQLException {
        JsonArray categories=list(c);
        for(var entry:categories){var category=entry.getAsJsonObject();category.remove("revision");category.add("items",rows(c,"""
            SELECT i.custom_item_id AS id,i.item_name AS name,COALESCE(i.description,'') AS description,
                   COALESCE(i.size,'') AS size,COALESCE(i.color,'') AS color
            FROM storefront.custom_item_category_assignments a JOIN custom_order_items i ON i.custom_item_id=a.custom_item_id
            WHERE a.category_id=? AND i.is_active AND NOT i.is_archived
              AND NOT i.is_shirt AND NOT i.is_sublimation AND NOT i.is_business_cards AND NOT i.is_large_format_printing AND NOT i.is_laser
            ORDER BY i.item_name,i.custom_item_id
            """,UUID.fromString(text(category,"id"))));}
        return categories;
    }
    public record Choice(String id,String name){@Override public String toString(){return name;}}
    public static Object[] choices(JsonArray categories){
        var values=new ArrayList<Object>(Arrays.asList(CustomItemCategory.values()));
        if(categories!=null)for(var e:categories){var c=e.getAsJsonObject();values.add(new Choice(text(c,"id"),text(c,"name")));}
        return values.toArray();
    }
    public static Object choice(JsonObject item,JsonArray categories){
        String id=text(item,"customCategoryId");
        for(Object value:choices(categories))if(value instanceof Choice c&&c.id().equals(id))return value;
        return CustomItemCategory.from(item);
    }
    public static Object displayChoice(JsonObject item,JsonArray categories){Object value=choice(item,categories);return value instanceof Choice?value:value.toString();}
    public static void apply(Object choice,JsonObject item){
        if(choice instanceof Choice c){CustomItemCategory.NONE.apply(item);item.addProperty("customCategoryId",c.id());}
        else {CustomItemCategory.parse(choice).apply(item);item.addProperty("customCategoryId","");}
    }
}
