package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;

/** Cross-store stock edits are commands executed by the owning store, never cache writes. */
final class ProductStockCommandService {
    private ProductStockCommandService() { }
    static Map<String,Object> request(Connection c,int user,int origin,int destination,int product,int expected,int quantity,UUID device)throws Exception {
        ProductMergeService.requireAdmin(c,user);LanProductAdminService.requirePermission(c,user,"EDIT_ITEM");
        if(quantity<0)throw new SQLException("Use non-negative stock quantities.","22023");
        if(c.getAutoCommit())throw new SQLException("Stock changes require a transaction.","25000");
        UUID uid;
        try(var p=c.prepareStatement("SELECT catalog_uid FROM products WHERE product_id=? AND is_active AND product_type='INVENTORY'")){p.setInt(1,product);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Choose an active inventory item.","22023");uid=r.getObject(1,UUID.class);}}
        try(var p=c.prepareStatement("SELECT 1 FROM locations WHERE location_id=?")){p.setInt(1,destination);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Store was not found.","22023");}}
        JsonObject command=new JsonObject();String id=UUID.randomUUID().toString();command.addProperty("command_id",id);
        command.addProperty("origin_location_id",origin);command.addProperty("location_id",destination);command.addProperty("product_uid",uid.toString());
        command.addProperty("expected_quantity",expected);command.addProperty("new_quantity",quantity);command.addProperty("requested_by_user_id",user);
        command.addProperty("device_id",device.toString());command.addProperty("updated_at",java.time.Instant.now().toString());
        insert(c,command);
        if(origin==destination)apply(c,command,origin,origin);
        return Map.of("commandId",id,"state",origin==destination?"APPLIED":"QUEUED","locationId",destination);
    }
    static void apply(Connection c,JsonObject command,int local,Integer origin)throws SQLException {
        if(origin==null||command.get("origin_location_id").getAsInt()!=origin)throw new SQLException("Stock command origin does not match its authenticated server.","42501");
        int owner=command.get("location_id").getAsInt();insert(c,command);if(owner!=local)return;
        String id=command.get("command_id").getAsString();
        try(var p=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")){p.setString(1,"stock-command:"+id);p.execute();}
        try(var p=c.prepareStatement("SELECT 1 FROM product_stock_command_results WHERE command_id=?::uuid")){p.setString(1,id);try(var r=p.executeQuery()){if(r.next())return;}}
        int user=command.get("requested_by_user_id").getAsInt();
        ProductMergeService.requireAdmin(c,user);
        try { LanProductAdminService.requirePermission(c,user,"EDIT_ITEM"); }
        catch(Exception ex){throw new SQLException("The administrator no longer has permission to edit stock.","42501");}
        int product;
        try(var p=c.prepareStatement("SELECT product_id FROM products WHERE catalog_uid=canonical_product_uid(?::uuid) AND is_active AND product_type='INVENTORY' FOR UPDATE")){p.setString(1,command.get("product_uid").getAsString());try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Stock command is waiting for its product catalog.","55000");product=r.getInt(1);}}
        int expected=command.get("expected_quantity").getAsInt(),desired=command.get("new_quantity").getAsInt();
        if(desired<0)throw new SQLException("Invalid stock command quantity.","22023");
        try(var p=c.prepareStatement("INSERT INTO inventory(product_id,location_id,quantity_on_hand) VALUES(?,?,0) ON CONFLICT DO NOTHING")){p.setInt(1,product);p.setInt(2,local);p.executeUpdate();}
        int actual;
        try(var p=c.prepareStatement("SELECT quantity_on_hand FROM inventory WHERE product_id=? AND location_id=? FOR UPDATE")){p.setInt(1,product);p.setInt(2,local);try(var r=p.executeQuery()){r.next();actual=r.getInt(1);}}
        String state=actual==expected?"APPLIED":"CONFLICT";
        if(actual==expected) {
            try(var p=c.prepareStatement("UPDATE inventory SET quantity_on_hand=?,updated_at=CURRENT_TIMESTAMP WHERE product_id=? AND location_id=?")){p.setInt(1,desired);p.setInt(2,product);p.setInt(3,local);p.executeUpdate();}
            try(var p=c.prepareStatement("INSERT INTO inventory_movements(product_id,location_id,change_qty,reason,note,user_id,device_id) VALUES(?,?,?,'ADJUSTMENT',?,?,?)")){p.setInt(1,product);p.setInt(2,local);p.setInt(3,desired-actual);p.setString(4,"Administrator stock edit from store #"+origin+"; command "+id);p.setInt(5,user);p.setString(6,command.get("device_id").getAsString());p.executeUpdate();}
            SyncOutboxService.recordEvent(c,"INVENTORY_MOVEMENT_CREATED",Map.of("product_id",product,"location_id",local,"command_id",id),local,command.get("device_id").getAsString(),user);
        }
        try(var p=c.prepareStatement("INSERT INTO product_stock_command_results(command_id,location_id,state,previous_quantity,requested_quantity) VALUES(?::uuid,?,?,?,?)")){p.setString(1,id);p.setInt(2,local);p.setString(3,state);p.setInt(4,actual);p.setInt(5,desired);p.executeUpdate();}
    }
    static void result(Connection c,JsonObject row,Integer origin)throws SQLException {
        if(origin==null||origin!=row.get("location_id").getAsInt())throw new SQLException("Stock result is not from the owning store.","42501");
        try(var p=c.prepareStatement("INSERT INTO product_stock_command_results(command_id,location_id,state,previous_quantity,requested_quantity,updated_at) VALUES(?::uuid,?,?,?,?,?::timestamptz) ON CONFLICT(command_id) DO NOTHING")){
            p.setString(1,row.get("command_id").getAsString());p.setInt(2,origin);p.setString(3,row.get("state").getAsString());p.setInt(4,row.get("previous_quantity").getAsInt());p.setInt(5,row.get("requested_quantity").getAsInt());p.setString(6,row.get("updated_at").getAsString());p.executeUpdate();
        }
    }
    static List<ProductMergeService.StoreStock> view(Connection c,int user,int local,int product)throws SQLException {
        ProductMergeService.requireAdmin(c,user);return ProductMergeCoordinator.stocks(c,local,product,product);
    }
    private static void insert(Connection c,JsonObject row)throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO product_stock_commands SELECT (jsonb_populate_record(NULL::product_stock_commands,?::jsonb)).* ON CONFLICT(command_id) DO NOTHING")){p.setString(1,row.toString());p.executeUpdate();}
        try(var p=c.prepareStatement("SELECT to_jsonb(t)::text FROM product_stock_commands t WHERE command_id=?::uuid")){
            p.setString(1,row.get("command_id").getAsString());try(var r=p.executeQuery()){r.next();var old=JsonParser.parseString(r.getString(1)).getAsJsonObject();for(String key:List.of("product_uid","expected_quantity","new_quantity","origin_location_id","location_id","requested_by_user_id","device_id"))if(!old.get(key).equals(row.get(key)))throw new SQLException("Conflicting stock command.","55000");}
        }
    }
}
