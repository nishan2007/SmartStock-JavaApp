package services;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

/** All custom stock belongs to an explicit store; catalog rows never hold live stock. */
public final class CustomItemStockService {
    private CustomItemStockService() { }

    /** Upgrade once, into this server's configured store only, never every location. */
    static void migrateLegacy(Connection c,int location)throws SQLException {
        if(c.getAutoCommit())throw new SQLException("Custom stock migration requires a transaction.");
        try(var p=c.prepareStatement("SELECT pg_advisory_xact_lock(728493,1612)")){p.execute();}
        try(var p=c.prepareStatement("SELECT 1 FROM custom_inventory_legacy_migration");var r=p.executeQuery()){if(r.next())return;}
        try(var p=c.prepareStatement("INSERT INTO custom_item_inventory(custom_item_id,location_id,quantity_on_hand,reorder_level,sold_quantity) SELECT custom_item_id,?,COALESCE(quantity_on_hand,0),GREATEST(COALESCE(reorder_level,0),0),COALESCE(sold_quantity,0) FROM custom_order_items WHERE NOT COALESCE(has_variants,false) ON CONFLICT DO NOTHING")){p.setInt(1,location);p.executeUpdate();}
        try(var p=c.prepareStatement("INSERT INTO custom_variant_inventory(custom_variant_id,location_id,quantity_on_hand,reorder_level,sold_quantity) SELECT custom_variant_id,?,COALESCE(quantity_on_hand,0),GREATEST(COALESCE(reorder_level,0),0),COALESCE(sold_quantity,0) FROM custom_order_item_variants ON CONFLICT DO NOTHING")){p.setInt(1,location);p.executeUpdate();}
        try(var p=c.prepareStatement("INSERT INTO custom_inventory_legacy_migration(singleton,location_id) VALUES(true,?)")){p.setInt(1,location);p.executeUpdate();}
    }

    static String valueSql(String item,String variant,int location,String field) {
        if(location<=0 || !java.util.Set.of("quantity_on_hand","reorder_level","sold_quantity").contains(field))throw new IllegalArgumentException("Invalid stock scope.");
        return "custom_stock_value("+item+","+(variant==null?"NULL":variant)+","+location+",'"+field+"')";
    }

    static BigDecimal quantity(Connection c,long item,Long variant,int location)throws SQLException {
        try(var p=c.prepareStatement("SELECT "+valueSql(Long.toString(item),variant==null?null:variant.toString(),location,"quantity_on_hand"));var r=p.executeQuery()){r.next();return r.getBigDecimal(1);}
    }

    static void ensure(Connection c,long item,Long variant,int location)throws SQLException {
        if(location<=0)throw new SQLException("A store location is required.");
        String table=variant==null?"custom_item_inventory":"custom_variant_inventory",key=variant==null?"custom_item_id":"custom_variant_id";
        // Keep lock order consistent with order/spoil workflows: parent, then variant, then store balance.
        try(var p=c.prepareStatement("SELECT custom_item_id FROM custom_order_items WHERE custom_item_id=? FOR UPDATE")) {
            p.setLong(1,item);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Custom inventory item is unavailable.");}
        }
        if(variant!=null)try(var p=c.prepareStatement("SELECT custom_variant_id FROM custom_order_item_variants WHERE custom_item_id=? AND custom_variant_id=? FOR UPDATE")) {
            p.setLong(1,item);p.setLong(2,variant);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Custom inventory variant is unavailable.");}
        }
        try(var p=c.prepareStatement("INSERT INTO "+table+"("+key+",location_id) VALUES(?,?) ON CONFLICT DO NOTHING")){p.setLong(1,variant==null?item:variant);p.setInt(2,location);p.executeUpdate();}
    }

    /** Receiving must hold the counted store balance until its adjustment commits. */
    static BigDecimal lock(Connection c,long item,Long variant,int location)throws SQLException {
        ensure(c,item,variant,location);
        String table=variant==null?"custom_item_inventory":"custom_variant_inventory",key=variant==null?"custom_item_id":"custom_variant_id";
        try(var p=c.prepareStatement("SELECT quantity_on_hand FROM "+table+" WHERE "+key+"=? AND location_id=? FOR UPDATE")) {
            p.setLong(1,variant==null?item:variant);p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Custom stock is unavailable.");return r.getBigDecimal(1);}
        }
    }

    static boolean restock(Connection c,long item,Long variant,int location)throws SQLException {
        try(var p=c.prepareStatement("SELECT product_type FROM custom_order_items WHERE custom_item_id=?")) {
            p.setLong(1,item);try(var r=p.executeQuery()){if(!r.next()||!"INVENTORY".equals(r.getString(1)))return false;}
        }
        move(c,item,variant,location,BigDecimal.ONE,BigDecimal.ONE.negate(),false);
        return true;
    }

    static BigDecimal move(Connection c,long item,Long variant,int location,BigDecimal delta,BigDecimal sold,boolean requireStock)throws SQLException {
        ensure(c,item,variant,location);
        String table=variant==null?"custom_item_inventory":"custom_variant_inventory",key=variant==null?"custom_item_id":"custom_variant_id";
        try(var p=c.prepareStatement("UPDATE "+table+" SET quantity_on_hand=quantity_on_hand+?,sold_quantity=GREATEST(sold_quantity+?,0),updated_at=now() WHERE "+key+"=? AND location_id=?"+(requireStock?" AND quantity_on_hand+?>=0":"")+" RETURNING quantity_on_hand")){
            p.setBigDecimal(1,delta);p.setBigDecimal(2,sold);p.setLong(3,variant==null?item:variant);p.setInt(4,location);if(requireStock)p.setBigDecimal(5,delta);
            try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Insufficient custom inventory at this store.");return r.getBigDecimal(1);}
        }
    }

    static void set(Connection c,long item,Long variant,int location,BigDecimal quantity,BigDecimal reorder)throws SQLException {
        if(quantity==null||reorder==null||reorder.signum()<0)throw new SQLException("Enter a valid quantity and non-negative reorder level.");
        ensure(c,item,variant,location);
        String table=variant==null?"custom_item_inventory":"custom_variant_inventory",key=variant==null?"custom_item_id":"custom_variant_id";
        try(var p=c.prepareStatement("UPDATE "+table+" SET quantity_on_hand=?,reorder_level=?,updated_at=now() WHERE "+key+"=? AND location_id=?")){p.setBigDecimal(1,quantity);p.setBigDecimal(2,reorder);p.setLong(3,variant==null?item:variant);p.setInt(4,location);p.executeUpdate();}
    }

    static void consume(Connection c,long item,Long variant,int location)throws SQLException {consume(c,item,variant,location,false);}

    static void consume(Connection c,long item,Long variant,int location,boolean requireStock)throws SQLException {
        try(var p=c.prepareStatement("SELECT COALESCE(product_type,'INVENTORY') FROM custom_order_items WHERE custom_item_id=?")){p.setLong(1,item);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Custom item is unavailable.");if(!"INVENTORY".equals(r.getString(1))){move(c,item,variant,location,BigDecimal.ZERO,BigDecimal.ONE,false);return;}}}
        move(c,item,variant,location,BigDecimal.ONE.negate(),BigDecimal.ONE,requireStock);
    }
}
