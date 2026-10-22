package services;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

/** Store-owned price validation and stock movements for regular products customized on an order. */
public final class CustomOrderInventoryService {
    private CustomOrderInventoryService() {}

    public static ServerCustomOrderDataService.OrderLineRequest validate(Connection c,
            ServerCustomOrderDataService.OrderLineRequest line, boolean pending) throws SQLException {
        if (line == null || line.inventoryProductId() == null) return line;
        if (line.customerItem()!=null || line.customItemId()!=null || line.customVariantId()!=null || !"FIXED".equals(line.pricingType()))
            throw new SQLException("Inventory order lines must reference only a regular product.");
        try (var p=c.prepareStatement("SELECT name,price,size,color FROM products WHERE product_id=? AND is_active AND COALESCE(product_type,'INVENTORY')='INVENTORY' FOR SHARE")) {
            p.setInt(1,line.inventoryProductId());
            try (var r=p.executeQuery()) {
                if (!r.next()) throw new SQLException("This inventory item is no longer available.");
                BigDecimal price=r.getBigDecimal(2);
                if (price==null || price.signum()<0) throw new SQLException("This inventory item needs a selling price.");
                BigDecimal entered=line.baseItemPrice();
                if (!pending && (entered==null || entered.signum()<0)) throw new SQLException("Enter a non-negative item price.");
                if (!pending && (entered==null || entered.compareTo(price)!=0)) {
                    if (line.priceOverridePrice()==null || line.priceOverridePrice().compareTo(entered)!=0 || line.priceOverrideReason()==null || line.priceOverrideReason().isBlank())
                        throw new SQLException("The inventory selling price changed. Select the item again or approve a price override.");
                }
                BigDecimal addons=BigDecimal.ZERO;
                for (var addon:line.printAddons()==null?java.util.List.<ServerCustomOrderDataService.PrintAddonRequest>of():line.printAddons()) {
                    if (addon==null || addon.printCharge()==null || addon.printCharge().signum()<0) throw new SQLException("Enter a valid add-on charge.");
                    addons=addons.add(addon.printCharge());
                }
                if (!pending && (line.originalLineTotal()==null || entered.add(addons).compareTo(line.originalLineTotal())!=0
                        || line.printCharge()==null || line.printCharge().compareTo(addons)!=0))
                    throw new SQLException("Inventory item total must equal its selling price plus add-ons.");
                var json=LanJson.create().toJsonTree(line).getAsJsonObject();
                json.addProperty("itemName",r.getString(1));json.addProperty("itemSize",r.getString(3));json.addProperty("itemColor",r.getString(4));
                json.addProperty("originalBasePrice",price);
                if (pending) {json.addProperty("baseItemPrice",price);json.addProperty("areaPrice",price);}
                return LanJson.create().fromJson(json,ServerCustomOrderDataService.OrderLineRequest.class);
            }
        }
    }

    public static void consume(Connection c,long order,long line,Integer product,Integer location,Integer uid,String user,String device,String deviceName) throws SQLException {
        if(product==null)return;
        try(var p=c.prepareStatement("UPDATE inventory SET quantity_on_hand=quantity_on_hand-1,updated_at=now() WHERE product_id=? AND location_id=? AND quantity_on_hand>=1")) {
            p.setInt(1,product);p.setInt(2,location);
            if(p.executeUpdate()!=1)throw new SQLException("Insufficient inventory for this custom order. Refresh the item quantity.");
        }
        movement(c,product,location,-1,order,line,uid,user,device,deviceName,"Custom order item issued");
    }

    /** Returns true for product lines, including lines already restocked by an earlier refund. */
    public static boolean restock(Connection c,long order,long line,int location,int uid,String user,String device,String deviceName,String reason) throws SQLException {
        Integer product;
        try(var p=c.prepareStatement("SELECT inventory_product_id,inventory_restocked FROM custom_order_lines WHERE custom_order_id=? AND custom_order_line_id=? FOR UPDATE")) {
            p.setLong(1,order);p.setLong(2,line);
            try(var r=p.executeQuery()) {
                if(!r.next())return false;
                product=(Integer)r.getObject(1);if(product==null)return false;if(r.getBoolean(2))return true;
            }
        }
        try(var p=c.prepareStatement("INSERT INTO inventory(product_id,location_id,quantity_on_hand) VALUES(?,?,1) ON CONFLICT(product_id,location_id) DO UPDATE SET quantity_on_hand=inventory.quantity_on_hand+1,updated_at=now()")) {
            p.setInt(1,product);p.setInt(2,location);p.executeUpdate();
        }
        try(var p=c.prepareStatement("UPDATE custom_order_lines SET inventory_restocked=true WHERE custom_order_line_id=?")) {p.setLong(1,line);p.executeUpdate();}
        movement(c,product,location,1,order,line,uid,user,device,deviceName,reason);
        return true;
    }

    private static void movement(Connection c,int product,int location,int quantity,long order,long line,Integer uid,String user,String device,String deviceName,String reason) throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO inventory_movements(product_id,location_id,change_qty,reason,note,user_id,user_name,device_id,device_name) VALUES(?,?,?,?,?,?,?,?,?)")) {
            p.setInt(1,product);p.setInt(2,location);p.setInt(3,quantity);p.setString(4,quantity<0?"CUSTOM_ORDER":"RETURN_RESTOCK");
            p.setString(5,"custom_order_id="+order+"; custom_order_line_id="+line+"; "+reason);p.setObject(6,uid);p.setString(7,user);p.setString(8,device);p.setString(9,deviceName);p.executeUpdate();
        }
    }
}
