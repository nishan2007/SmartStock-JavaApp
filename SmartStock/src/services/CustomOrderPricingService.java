package services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import services.CustomOrderDataService.PrintAddonRequest;
import utils.CurrencyFormatter;

/** Pricing of existing orders: never creates lines or repeats inventory movements. */
public final class CustomOrderPricingService {
    private CustomOrderPricingService() {}
    public record PricingLine(long lineId, BigDecimal width, BigDecimal length, BigDecimal rate,
            List<PrintAddonRequest> addons, BigDecimal discountPercent, String discountReason,
            String discountApprovalToken, String priceOverrideReason, String priceOverrideApprovalToken) {}
    public record Snapshot(long lineId, Long itemId, Long variantId, String itemName, String variantName,
            String pricingType, String dimensionUnit, String areaUnit, BigDecimal maximumWidth,
            BigDecimal configuredRate, BigDecimal rate, BigDecimal width, BigDecimal length,
            boolean customerSupplied, List<PrintAddonRequest> addons) {}
    public record PricingDetails(BigDecimal paid, boolean applyCustomerDiscount, List<Snapshot> lines,
            String customerName, boolean requireChargeAuthorization, BigDecimal customerDiscountPercent, boolean roundToNearestTwenty,
            List<CustomOrderDataService.PrintMaterialOption> materials,List<CustomOrderDataService.PrintSizePresetOption> presets) {}
    public record AuthorizedLine(Snapshot saved,PricingLine input,Calculated price,Integer discountBy,
            String discountByName,Integer priceBy,String priceByName) {}
    public record Calculated(BigDecimal area, BigDecimal base, BigDecimal addons, BigDecimal original,
            BigDecimal discountPercent, BigDecimal discountAmount, BigDecimal total) {}

    public static boolean pending(Connection c,long id,int location)throws SQLException {
        try(var p=c.prepareStatement("SELECT pricing_state FROM custom_orders WHERE custom_order_id=? AND location_id=? FOR UPDATE")) {
            p.setLong(1,id);p.setInt(2,location);try(var r=p.executeQuery()) {
                if(!r.next())throw new SQLException("Order was not found for this store.");
                return "PENDING".equals(r.getString(1));
            }
        }
    }
    public static PricingDetails details(Connection c,long id,int location)throws SQLException {
        BigDecimal paid;boolean discount;
        try(var p=c.prepareStatement("SELECT amount_paid,pricing_apply_customer_discount,pricing_state,status,invoice_billed FROM custom_orders WHERE custom_order_id=? AND location_id=? FOR UPDATE")) {
            p.setLong(1,id);p.setInt(2,location);try(var r=p.executeQuery()) {
                if(!r.next())throw new SQLException("Order was not found for this store.");
                if(!"PENDING".equals(r.getString(3)))throw new SQLException("This order already has a final price.");
                if("CANCELLED".equals(r.getString(4))||r.getBoolean(5))throw new SQLException("This order cannot be repriced.");
                paid=r.getBigDecimal(1);discount=r.getBoolean(2);
            }
        }
        List<Snapshot> lines=new ArrayList<>();
        try(var p=c.prepareStatement("""
                SELECT l.custom_order_line_id,l.custom_item_id,l.custom_variant_id,l.item_name,l.variant_name,
                       l.pricing_type,COALESCE(i.dimension_unit,l.dimension_unit),COALESCE(i.area_price_unit,l.area_unit),
                       i.max_width,CASE WHEN l.pricing_type='AREA' THEN i.area_price ELSE COALESCE(v.fixed_price,i.fixed_price) END,
                       COALESCE(l.area_price,l.base_item_price,0),l.width_value,l.length_value,(l.custom_item_id IS NULL AND l.customization_details LIKE 'Customer-supplied item |%')
                FROM custom_order_lines l LEFT JOIN custom_order_items i ON i.custom_item_id=l.custom_item_id
                LEFT JOIN custom_order_item_variants v ON v.custom_variant_id=l.custom_variant_id
                WHERE l.custom_order_id=? ORDER BY l.sort_order,l.custom_order_line_id
                """)) {
            p.setLong(1,id);try(var r=p.executeQuery()) {while(r.next()) {
                long lineId=r.getLong(1);List<PrintAddonRequest> addons=new ArrayList<>();
                try(var a=c.prepareStatement("SELECT print_material_id,print_material_name,print_size_preset_id,print_size_name,pricing_mode,print_description,print_line_count,print_charge FROM custom_order_line_print_addons WHERE custom_order_line_id=? ORDER BY sort_order,custom_order_line_print_addon_id")) {
                    a.setLong(1,lineId);try(var ar=a.executeQuery()){while(ar.next())addons.add(new PrintAddonRequest(nullableLong(ar,1),ar.getString(2),nullableLong(ar,3),ar.getString(4),ar.getString(5),ar.getString(6),ar.getInt(7),ar.getBigDecimal(8)));}
                }
                lines.add(new Snapshot(lineId,nullableLong(r,2),nullableLong(r,3),r.getString(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getBigDecimal(9),r.getBigDecimal(10),r.getBigDecimal(11),r.getBigDecimal(12),r.getBigDecimal(13),r.getBoolean(14),addons));
            }}
        }
        List<CustomOrderDataService.PrintMaterialOption> materials=new ArrayList<>();List<CustomOrderDataService.PrintSizePresetOption> presets=new ArrayList<>();
        try(var p=c.prepareStatement("SELECT print_material_id,material_name FROM custom_order_print_materials WHERE is_active ORDER BY material_name");var r=p.executeQuery()){while(r.next())materials.add(new CustomOrderDataService.PrintMaterialOption(r.getLong(1),r.getString(2)));}
        try(var p=c.prepareStatement("SELECT print_size_preset_id,print_material_id,preset_name,pricing_mode,fixed_price FROM custom_order_print_size_presets WHERE is_active ORDER BY preset_name");var r=p.executeQuery()){while(r.next())presets.add(new CustomOrderDataService.PrintSizePresetOption(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getBigDecimal(5)));}
        try(var p=c.prepareStatement("SELECT o.customer_name,COALESCE(a.require_charge_authorization,FALSE),CASE WHEN a.custom_order_discount_enabled THEN a.custom_order_discount_percent ELSE 0 END,COALESCE(cc.round_custom_orders_to_nearest_twenty,TRUE) FROM custom_orders o JOIN customer_accounts a ON a.customer_id=o.customer_id LEFT JOIN company_customization cc ON cc.location_id=o.location_id WHERE o.custom_order_id=?")) {
            p.setLong(1,id);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Customer was not found.");return new PricingDetails(paid,discount,lines,r.getString(1),r.getBoolean(2),discount?r.getBigDecimal(3):BigDecimal.ZERO,r.getBoolean(4),materials,presets);}
        }
    }
    public static Calculated calculate(Snapshot saved,PricingLine line,BigDecimal customerRate,boolean round) {
        BigDecimal rate=nonNegative(line.rate(),"Enter a valid price for every line.");
        BigDecimal area=null,base=rate;
        if(saved.customerSupplied()) {
            if(rate.signum()!=0)throw new IllegalArgumentException("Customer-supplied items must have a zero base price.");
        } else if("AREA".equals(saved.pricingType())) {
            area=billableArea(line.width(),line.length(),saved.maximumWidth(),saved.dimensionUnit(),saved.areaUnit());
            base=area.multiply(rate).setScale(2,RoundingMode.HALF_UP);
        }
        BigDecimal addons=BigDecimal.ZERO;
        if(line.addons()!=null)for(var addon:line.addons()) {
            if(addon==null||addon.printLineCount()<1)throw new IllegalArgumentException("Enter a valid add-on line count.");
            addons=addons.add(nonNegative(addon.printCharge(),"Enter a valid add-on charge."));
        }
        if(saved.customerSupplied()&&(line.addons()==null||line.addons().isEmpty()))throw new IllegalArgumentException("Customer-supplied items require an add-on.");
        BigDecimal manual=line.discountPercent()==null?BigDecimal.ZERO:line.discountPercent();
        if(manual.signum()<0||manual.compareTo(BigDecimal.valueOf(100))>0)throw new IllegalArgumentException("Discount must be between 0 and 100%.");
        BigDecimal percent=manual.max(customerRate==null?BigDecimal.ZERO:customerRate),original=CurrencyFormatter.normalize(base.add(addons));
        BigDecimal reduction=original.multiply(percent).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP);
        BigDecimal total=CurrencyFormatter.normalize(original.subtract(reduction));
        return new Calculated(area,base,addons,original,percent,reduction,round?CurrencyFormatter.roundToNearestTwenty(total):total);
    }
    public static BigDecimal billableArea(BigDecimal width,BigDecimal length,BigDecimal maximumWidth,String dimensionUnit,String areaUnit) {
        if(width==null||length==null||width.signum()<=0||length.signum()<=0)throw new IllegalArgumentException("Enter positive width and length for area pricing.");
        BigDecimal chargedWidth=maximumWidth!=null&&maximumWidth.signum()>0?maximumWidth:width;
        String d=dimensionUnit==null?"":dimensionUnit.trim().toUpperCase(Locale.ROOT),a=areaUnit==null?"":areaUnit.trim().toUpperCase(Locale.ROOT);
        if(a.isBlank())return chargedWidth.multiply(length);
        BigDecimal metres=switch(d){case "IN","INCH","INCHES"->new BigDecimal("0.0254");case "FT","FOOT","FEET"->new BigDecimal("0.3048");case "CM"->new BigDecimal("0.01");case "MM"->new BigDecimal("0.001");case "M"->BigDecimal.ONE;default->throw new IllegalArgumentException("Unsupported dimension unit.");};
        BigDecimal squareUnit=switch(a){case "SQ_IN","SQUARE_INCH","SQUARE_INCHES"->new BigDecimal("0.00064516");case "SQ_FT","SQUARE_FOOT","SQUARE_FEET"->new BigDecimal("0.09290304");case "SQ_CM","SQUARE_CENTIMETRE","SQUARE_CENTIMETERS"->new BigDecimal("0.0001");case "SQ_MM"->new BigDecimal("0.000001");case "SQ_M"->BigDecimal.ONE;default->throw new IllegalArgumentException("Unsupported area price unit.");};
        return chargedWidth.multiply(metres).multiply(length.multiply(metres)).divide(squareUnit,6,RoundingMode.HALF_UP);
    }
    private static BigDecimal nonNegative(BigDecimal value,String message){if(value==null||value.signum()<0||value.precision()>16||value.scale()>8)throw new IllegalArgumentException(message);return value;}
    private static Long nullableLong(ResultSet r,int i)throws SQLException{long v=r.getLong(i);return r.wasNull()?null:v;}
    public static BigDecimal finalBalance(BigDecimal total,BigDecimal paid) {
        if(paid.compareTo(total)>0)throw new IllegalArgumentException("Deposits exceed the final total by $"+CurrencyFormatter.normalize(paid.subtract(total))+". Refund the excess deposit before finalizing.");
        return CurrencyFormatter.normalize(total.subtract(paid));
    }
    public static void finalizePrice(Connection c,long id,int location,PricingDetails details,List<AuthorizedLine> lines,
            CustomerChargeAuthorizationService.Authorization authorization,int actor,String actorName,String device,String deviceName)throws SQLException {
        if(!pending(c,id,location))throw new SQLException("This order already has a final price.");
        BigDecimal total=CurrencyFormatter.normalize(lines.stream().map(l->l.price().total()).reduce(BigDecimal.ZERO,BigDecimal::add));
        BigDecimal balance=finalBalance(total,details.paid());int customer;
        try(var p=c.prepareStatement("SELECT customer_id FROM custom_orders WHERE custom_order_id=? AND location_id=? FOR UPDATE")){p.setLong(1,id);p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Order was not found.");customer=r.getInt(1);}}
        if(balance.signum()>0)CustomerChargeAuthorizationService.validateRequired(c,customer,authorization);
        for(var l:lines) {
            var input=l.input();var price=l.price();
            try(var p=c.prepareStatement("""
                    UPDATE custom_order_lines SET width_value=?,length_value=?,dimension_unit=?,area_value=?,area_unit=?,area_price=?,
                        base_item_price=?,print_charge=?,unit_price=?,line_total=?,original_line_total=?,line_discount_percent=?,line_discount_amount=?,
                        line_discount_reason=?,line_discount_by_user_id=?,line_discount_by_name=?,original_base_price=?,price_override_price=?,
                        price_override_reason=?,price_override_by_user_id=?,price_override_by_name=?,print_line_count=?
                    WHERE custom_order_id=? AND custom_order_line_id=?
                    """)) {
                p.setBigDecimal(1,input.width());p.setBigDecimal(2,input.length());p.setString(3,l.saved().dimensionUnit());p.setBigDecimal(4,price.area());p.setString(5,l.saved().areaUnit());p.setBigDecimal(6,input.rate());
                p.setBigDecimal(7,price.base());p.setBigDecimal(8,price.addons());p.setBigDecimal(9,price.total());p.setBigDecimal(10,price.total());p.setBigDecimal(11,price.original());p.setBigDecimal(12,price.discountPercent());p.setBigDecimal(13,price.discountAmount());
                p.setString(14,details.customerDiscountPercent().compareTo(input.discountPercent()==null?BigDecimal.ZERO:input.discountPercent())>0?"Automatic customer discount":input.discountReason());p.setObject(15,l.discountBy());p.setString(16,l.discountByName());p.setBigDecimal(17,l.saved().configuredRate());p.setBigDecimal(18,l.priceBy()==null?null:input.rate());
                p.setString(19,input.priceOverrideReason());p.setObject(20,l.priceBy());p.setString(21,l.priceByName());p.setInt(22,input.addons().stream().mapToInt(PrintAddonRequest::printLineCount).sum());p.setLong(23,id);p.setLong(24,input.lineId());
                if(p.executeUpdate()!=1)throw new SQLException("An order line changed. Reload pricing.");
            }
            try(var p=c.prepareStatement("DELETE FROM custom_order_line_print_addons WHERE custom_order_line_id=?")){p.setLong(1,input.lineId());p.executeUpdate();}
            int sort=0;for(var a:input.addons())try(var p=c.prepareStatement("INSERT INTO custom_order_line_print_addons(custom_order_line_id,print_material_id,print_material_name,print_size_preset_id,print_size_name,pricing_mode,print_description,print_charge,print_line_count,sort_order) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
                p.setLong(1,input.lineId());p.setObject(2,a.printMaterialId());p.setString(3,a.materialName());p.setObject(4,a.printSizePresetId());p.setString(5,a.printSizeName());p.setString(6,a.pricingMode());p.setString(7,a.printDescription());p.setBigDecimal(8,a.printCharge());p.setInt(9,a.printLineCount());p.setInt(10,++sort);p.executeUpdate();
            }
            audit(c,id,"FINALIZE_LINE_PRICE","line_total",null,price.total(),"Line "+input.lineId()+"; price reason: "+Objects.toString(input.priceOverrideReason(),"")+"; price approved by: "+Objects.toString(l.priceByName(),"")+"; discount reason: "+Objects.toString(input.discountReason(),"")+"; discount approved by: "+Objects.toString(l.discountByName(),""),actor,actorName,device,deviceName);
        }
        BigDecimal required=BigDecimal.ZERO;
        try(var p=c.prepareStatement("SELECT COALESCE(custom_order_minimum_deposit_percent,0) FROM company_customization WHERE location_id=?")){p.setInt(1,location);try(var r=p.executeQuery()){if(r.next())required=CurrencyFormatter.normalize(total.multiply(r.getBigDecimal(1)).divide(BigDecimal.valueOf(100),6,RoundingMode.HALF_UP));}}
        try(var p=c.prepareStatement("UPDATE custom_orders SET pricing_state='FINAL',total_amount=?,balance_due=?,payment_status=?,pricing_finalized_at=now(),pricing_finalized_by_user_id=?,pricing_finalized_by_name=?,minimum_deposit_required=?,customer_discount_percent=?,customer_discount_applied=?,customer_discount_amount=?,updated_at=now() WHERE custom_order_id=? AND location_id=? AND pricing_state='PENDING'")) {
            p.setBigDecimal(1,total);p.setBigDecimal(2,balance);p.setString(3,balance.signum()==0?"PAID":details.paid().signum()==0?"UNPAID":"PARTIAL");p.setInt(4,actor);p.setString(5,actorName);p.setBigDecimal(6,required);p.setBigDecimal(7,details.customerDiscountPercent());p.setBoolean(8,details.customerDiscountPercent().signum()>0);p.setBigDecimal(9,lines.stream().map(l->l.price().discountAmount().subtract(calculate(l.saved(),l.input(),BigDecimal.ZERO,details.roundToNearestTwenty()).discountAmount()).max(BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add));p.setLong(10,id);p.setInt(11,location);if(p.executeUpdate()!=1)throw new SQLException("The price was already finalized. Reload the order.");
        }
        if(balance.signum()>0)try(var p=c.prepareStatement("INSERT INTO customer_account_transactions(customer_id,custom_order_id,location_id,amount,transaction_type,note,user_name,device_id,device_name) VALUES(?,?,?,?,'CUSTOM_ORDER_BALANCE','Custom order pricing finalized',?,?,?) RETURNING transaction_id")) {
            p.setInt(1,customer);p.setLong(2,id);p.setInt(3,location);p.setBigDecimal(4,balance);p.setString(5,actorName);p.setString(6,device);p.setString(7,deviceName);try(var r=p.executeQuery()){r.next();CustomerChargeAuthorizationService.insert(c,r.getLong(1),customer,location,"CUSTOM_ORDER",id,authorization,actor,actorName,UUID.fromString(device),deviceName);}
        }
        audit(c,id,"FINALIZE_PRICE","pricing_state","PENDING","FINAL","Final total: "+total+"; deposits: "+details.paid()+"; balance: "+balance,actor,actorName,device,deviceName);
    }
    public static void audit(Connection c,long id,String action,String field,Object oldValue,Object value,String reason,int actor,String actorName,String device,String deviceName)throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO custom_order_audit_log(custom_order_id,action_type,field_name,old_value,new_value,reason,user_id,user_name,device_id,device_name) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            p.setLong(1,id);p.setString(2,action);p.setString(3,field);p.setString(4,oldValue==null?null:oldValue.toString());p.setString(5,value==null?null:value.toString());p.setString(6,reason);p.setInt(7,actor);p.setString(8,actorName);p.setString(9,device);p.setString(10,deviceName);p.executeUpdate();
        }
    }
}
