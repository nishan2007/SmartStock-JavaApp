package services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import services.CustomOrderDataService.PrintAddonRequest;
import utils.CurrencyFormatter;

/** Transactional final pricing; existing lines are preserved and only new units consume stock. */
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
            List<CustomOrderDataService.PrintMaterialOption> materials,List<CustomOrderDataService.PrintSizePresetOption> presets,
            String orderNotes,List<AdditionOption> additionOptions) {
        public PricingDetails(BigDecimal paid,boolean apply,List<Snapshot> lines,String name,boolean auth,BigDecimal discount,boolean round,List<CustomOrderDataService.PrintMaterialOption> materials,List<CustomOrderDataService.PrintSizePresetOption> presets){this(paid,apply,lines,name,auth,discount,round,materials,presets,"",List.of());}
    }
    public record AdditionOption(Long itemId,Long variantId,String name,String variant,String size,String color,String pricingType,BigDecimal rate,String dimensionUnit,String areaUnit,BigDecimal maximumWidth,Integer productId) {
        public AdditionOption(Long itemId,Long variantId,String name,String variant,String size,String color,String pricingType,BigDecimal rate,String dimensionUnit,String areaUnit,BigDecimal maximumWidth){this(itemId,variantId,name,variant,size,color,pricingType,rate,dimensionUnit,areaUnit,maximumWidth,null);}
        public String toString(){return name+(variant==null?"":" / "+variant)+(size==null||size.isBlank()?"":" / "+size)+(color==null||color.isBlank()?"":" / "+color)+(productId==null?"":" [Inventory]");}
        public Snapshot snapshot(long id){return new Snapshot(id,itemId,variantId,name,variant,pricingType,dimensionUnit,areaUnit,maximumWidth,rate,rate,null,null,false,List.of());}
    }
    public record AddedLine(long temporaryId,Long itemId,Long variantId,String itemName,String size,String color,String instructions,Integer productId) {}

    public static void updatePendingNotes(Connection c,long orderId,int location,String expected,String notes,int actor,String actorName,String device,String deviceName)throws SQLException {
        expected=Objects.toString(expected,"");notes=Objects.toString(notes,"");
        if(notes.length()>5000)throw new SQLException("Order notes must be 5000 characters or fewer.");
        try(var p=c.prepareStatement("UPDATE custom_orders SET order_notes=?,updated_at=now() WHERE custom_order_id=? AND location_id=? AND pricing_state='PENDING' AND COALESCE(order_notes,'')=?")){
            p.setString(1,notes);p.setLong(2,orderId);p.setInt(3,location);p.setString(4,expected);
            if(p.executeUpdate()!=1)throw new SQLException("Order notes changed. Reload before finalizing.");
        }
        if(!expected.equals(notes))audit(c,orderId,"UPDATE_NOTES","order_notes",expected,notes,"Notes updated during final pricing",actor,actorName,device,deviceName);
    }

    public static List<AdditionOption> additionOptions(Connection c)throws SQLException {
        List<AdditionOption> options=new ArrayList<>();
        for(var item:ServerCustomOrderDataService.listActiveItems(c)){
            if(item.hasVariants())for(var variant:ServerCustomOrderDataService.listActiveVariants(c,item.customItemId()))
                options.add(new AdditionOption(item.customItemId(),variant.variantId(),item.name(),variant.name(),variant.effectiveSize(),variant.effectiveColor(),item.pricingType(),"AREA".equals(item.pricingType())?item.areaPrice():variant.fixedPrice()==null?item.fixedPrice():variant.fixedPrice(),item.dimensionUnit(),item.areaPriceUnit(),item.maxWidth()));
            else options.add(new AdditionOption(item.customItemId(),null,item.name(),null,item.size(),item.color(),item.pricingType(),"AREA".equals(item.pricingType())?item.areaPrice():item.fixedPrice(),item.dimensionUnit(),item.areaPriceUnit(),item.maxWidth()));
        }
        try(var p=c.prepareStatement("SELECT product_id,name,size,color,price FROM products WHERE is_active AND COALESCE(product_type,'INVENTORY')='INVENTORY' ORDER BY name");var r=p.executeQuery()){while(r.next())options.add(new AdditionOption(null,null,r.getString(2),null,r.getString(3),r.getString(4),"FIXED",r.getBigDecimal(5),null,null,null,r.getInt(1)));}
        return options;
    }

    public static Map<Long,Long> appendPendingLines(Connection c,long orderId,int location,List<AddedLine> additions,int actor,String actorName,String device,String deviceName)throws SQLException {
        if(additions.size()>500)throw new SQLException("Add no more than 500 units at once.");
        if(!pending(c,orderId,location))throw new SQLException("This order already has a final price.");
        if(!additions.isEmpty())try(var p=c.prepareStatement("SELECT status,invoice_billed FROM custom_orders WHERE custom_order_id=? AND location_id=? FOR UPDATE")){
            p.setLong(1,orderId);p.setInt(2,location);try(var r=p.executeQuery()){
                if(!r.next()||List.of("CANCELLED","DELIVERED").contains(r.getString(1))||r.getBoolean(2))throw new SQLException("Items cannot be added to a cancelled, delivered or invoiced order.");
            }
        }
        List<AdditionOption> options=additions.isEmpty()?List.of():additionOptions(c);
        Map<Long,Long> mapped=new LinkedHashMap<>();
        Set<Long> ids=new HashSet<>();
        for(var added:additions){
            if(added==null||added.temporaryId()>=0||!ids.add(added.temporaryId()))throw new SQLException("Invalid new item identifier.");
            AdditionOption option;
            if(added.itemId()!=null||added.productId()!=null){option=options.stream().filter(o->Objects.equals(o.itemId(),added.itemId())&&Objects.equals(o.variantId(),added.variantId())&&Objects.equals(o.productId(),added.productId())).findFirst().orElseThrow(()->new SQLException("The selected catalogue item is no longer available."));}
            else {if(added.variantId()!=null||added.itemName()==null||added.itemName().isBlank()||added.itemName().length()>200)throw new SQLException("Enter a valid manual item description.");option=new AdditionOption(null,null,added.itemName().trim(),null,added.size(),added.color(),"VARIABLE",null,null,null,null);}
            if(added.instructions()!=null&&added.instructions().length()>5000)throw new SQLException("Item instructions are too long.");
            if(option.itemId()!=null)try(var p=c.prepareStatement("SELECT COALESCE(product_type,'INVENTORY'),0,is_active,is_archived FROM custom_order_items WHERE custom_item_id=? FOR UPDATE")){
                p.setLong(1,option.itemId());try(var r=p.executeQuery()){
                    if(!r.next()||!r.getBoolean(3)||r.getBoolean(4))throw new SQLException("The catalogue item is no longer available.");
                    if("INVENTORY".equals(r.getString(1))){
                        BigDecimal quantity=CustomItemStockService.quantity(c,option.itemId(),option.variantId(),location);
                        if(option.variantId()!=null)try(var v=c.prepareStatement("SELECT custom_variant_id FROM custom_order_item_variants WHERE custom_variant_id=? AND custom_item_id=? AND is_active FOR UPDATE")){v.setLong(1,option.variantId());v.setLong(2,option.itemId());try(var vr=v.executeQuery()){if(!vr.next())throw new SQLException("The selected variant is no longer available.");quantity=CustomItemStockService.quantity(c,option.itemId(),option.variantId(),location);}}
                        if(quantity==null||quantity.compareTo(BigDecimal.ONE)<0)throw new SQLException("Insufficient stock for the new order item.");
                    }
                }
            }
            long lineId;
            try(var p=c.prepareStatement("INSERT INTO custom_order_lines(custom_order_id,custom_item_id,custom_variant_id,item_name,variant_name,item_size,item_color,pricing_type,unit_price,line_total,base_item_price,area_price,dimension_unit,area_unit,customization_details,order_instructions,inventory_product_id,sort_order) VALUES(?,?,?,?,?,?,?,?,0,0,0,?,?,?,?,?,?,(SELECT COALESCE(MAX(sort_order),0)+1 FROM custom_order_lines WHERE custom_order_id=?)) RETURNING custom_order_line_id")){
                p.setLong(1,orderId);p.setObject(2,option.itemId());p.setObject(3,option.variantId());p.setString(4,option.name());p.setString(5,option.variant());p.setString(6,option.size());p.setString(7,option.color());p.setString(8,option.pricingType());p.setBigDecimal(9,option.rate());p.setString(10,option.dimensionUnit());p.setString(11,option.areaUnit());p.setString(12,option.productId()!=null?"Inventory item":option.itemId()==null?"Manual entry":"Added during price finalization");p.setString(13,added.instructions());p.setObject(14,option.productId());p.setLong(15,orderId);try(var r=p.executeQuery()){r.next();lineId=r.getLong(1);}
            }
            mapped.put(added.temporaryId(),lineId);
            CustomOrderInventoryService.consume(c,orderId,lineId,option.productId(),location,actor,actorName,device,deviceName);
            if(option.itemId()!=null){
                try(var p=c.prepareStatement("INSERT INTO custom_order_inventory_reservations(custom_order_id,custom_order_line_id,custom_item_id,custom_variant_id,item_name,variant_name,reserved_qty,status) SELECT ?,?,?,?, ?,?,1,'RESERVED' FROM custom_order_items WHERE custom_item_id=? AND COALESCE(product_type,'INVENTORY')='INVENTORY'")){p.setLong(1,orderId);p.setLong(2,lineId);p.setLong(3,option.itemId());p.setObject(4,option.variantId());p.setString(5,option.name());p.setString(6,option.variant());p.setLong(7,option.itemId());p.executeUpdate();}
                CustomItemStockService.consume(c,option.itemId(),option.variantId(),location,true);
            }
            audit(c,orderId,"ADD_LINE","item_name",null,option.name(),"Line "+lineId+" added during final pricing",actor,actorName,device,deviceName);
        }
        if(!additions.isEmpty()){
            CustomOrderDesignService.createForOrder(c,orderId);
            LanCustomOrderWorkflowService.refreshProductionOrderStatus(c,orderId,location,actor,actorName,device,deviceName);
        }
        return mapped;
    }
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
                       i.max_width,CASE WHEN l.pricing_type='AREA' THEN i.area_price ELSE COALESCE(p.price,v.fixed_price,i.fixed_price) END,
                       COALESCE(l.area_price,l.base_item_price,0),l.width_value,l.length_value,(l.custom_item_id IS NULL AND l.customization_details LIKE 'Customer-supplied item |%')
                FROM custom_order_lines l LEFT JOIN products p ON p.product_id=l.inventory_product_id LEFT JOIN custom_order_items i ON i.custom_item_id=l.custom_item_id
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
        try(var p=c.prepareStatement("SELECT o.customer_name,COALESCE(a.require_charge_authorization,FALSE),CASE WHEN a.custom_order_discount_enabled THEN a.custom_order_discount_percent ELSE 0 END,COALESCE(cc.round_custom_orders_to_nearest_twenty,TRUE),o.order_notes FROM custom_orders o LEFT JOIN customer_accounts a ON a.customer_id=o.customer_id LEFT JOIN company_customization cc ON cc.location_id=o.location_id WHERE o.custom_order_id=?")) {
            p.setLong(1,id);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Customer was not found.");return new PricingDetails(paid,discount,lines,r.getString(1),r.getBoolean(2),discount?r.getBigDecimal(3):BigDecimal.ZERO,r.getBoolean(4),materials,presets,r.getString(5),additionOptions(c));}
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
        return new Calculated(area,base,addons,original,percent,reduction,total);
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
        finalizePrice(c,id,location,details,lines,authorization,actor,actorName,device,deviceName,CustomOrderCredit::reject);
    }

    static <E extends Exception> void finalizePrice(Connection c,long id,int location,PricingDetails details,
            List<AuthorizedLine> lines,CustomerChargeAuthorizationService.Authorization authorization,
            int actor,String actorName,String device,String deviceName,CustomOrderCredit.Approver<E> approver)
            throws SQLException,E {
        if(!pending(c,id,location))throw new SQLException("This order already has a final price.");
        BigDecimal total=CurrencyFormatter.customOrderTotal(lines.stream().map(l->l.price().total()).reduce(BigDecimal.ZERO,BigDecimal::add),details.roundToNearestTwenty());
        BigDecimal balance=finalBalance(total,details.paid());Integer customer;
        try(var p=c.prepareStatement("SELECT customer_id FROM custom_orders WHERE custom_order_id=? AND location_id=? FOR UPDATE")){p.setLong(1,id);p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Order was not found.");customer=(Integer)r.getObject(1);}}
        CustomOrderCredit.Charge charge = CustomOrderCredit.check(c,customer,location,balance,false,approver);
        if(customer!=null&&balance.signum()>0)CustomerChargeAuthorizationService.validateRequired(c,customer,authorization);
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
        if(customer!=null)try(var p=c.prepareStatement("SELECT custom_order_deposit_exempt FROM customer_accounts WHERE customer_id=? AND is_active=TRUE")){p.setInt(1,customer);try(var r=p.executeQuery()){if(r.next()&&r.getBoolean(1))required=BigDecimal.ZERO;}}
        try(var p=c.prepareStatement("UPDATE custom_orders SET pricing_state='FINAL',total_amount=?,balance_due=?,payment_status=?,pricing_finalized_at=now(),pricing_finalized_by_user_id=?,pricing_finalized_by_name=?,minimum_deposit_required=?,customer_discount_percent=?,customer_discount_applied=?,customer_discount_amount=?,payment_method=CASE WHEN ? THEN 'ACCOUNT' ELSE payment_method END,updated_at=now() WHERE custom_order_id=? AND location_id=? AND pricing_state='PENDING'")) {
            p.setBigDecimal(1,total);p.setBigDecimal(2,balance);p.setString(3,balance.signum()==0?"PAID":details.paid().signum()==0?"UNPAID":"PARTIAL");p.setInt(4,actor);p.setString(5,actorName);p.setBigDecimal(6,required);p.setBigDecimal(7,details.customerDiscountPercent());p.setBoolean(8,details.customerDiscountPercent().signum()>0);p.setBigDecimal(9,lines.stream().map(l->l.price().discountAmount().subtract(calculate(l.saved(),l.input(),BigDecimal.ZERO,details.roundToNearestTwenty()).discountAmount()).max(BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add));p.setBoolean(10,charge!=null);p.setLong(11,id);p.setInt(12,location);if(p.executeUpdate()!=1)throw new SQLException("The price was already finalized. Reload the order.");
        }
        if(customer!=null&&balance.signum()>0)try(var p=c.prepareStatement("INSERT INTO customer_account_transactions(customer_id,custom_order_id,location_id,amount,transaction_type,note,user_name,device_id,device_name) VALUES(?,?,?,?,'CUSTOM_ORDER_BALANCE','Custom order pricing finalized',?,?,?) RETURNING transaction_id")) {
            p.setInt(1,customer);p.setLong(2,id);p.setInt(3,location);p.setBigDecimal(4,balance);p.setString(5,actorName);p.setString(6,device);p.setString(7,deviceName);try(var r=p.executeQuery()){r.next();CustomerChargeAuthorizationService.insert(c,r.getLong(1),customer,location,"CUSTOM_ORDER",id,authorization,actor,actorName,UUID.fromString(device),deviceName);}
        }
        if (charge != null) {
            CustomerAccountLedgerService.repairCustomerBalance(c, customer);
            charge.audit(c,id,balance,actor,actorName,device,deviceName);
        }
        audit(c,id,"FINALIZE_PRICE","pricing_state","PENDING","FINAL","Final total: "+total+"; deposits: "+details.paid()+"; balance: "+balance,actor,actorName,device,deviceName);
        LanCustomOrderWorkflowService.reconcileSettledDelivery(c,id,location,actor,actorName,device,deviceName);
    }
    public static void audit(Connection c,long id,String action,String field,Object oldValue,Object value,String reason,int actor,String actorName,String device,String deviceName)throws SQLException {
        try(var p=c.prepareStatement("INSERT INTO custom_order_audit_log(custom_order_id,action_type,field_name,old_value,new_value,reason,user_id,user_name,device_id,device_name) VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            p.setLong(1,id);p.setString(2,action);p.setString(3,field);p.setString(4,oldValue==null?null:oldValue.toString());p.setString(5,value==null?null:value.toString());p.setString(6,reason);p.setInt(7,actor);p.setString(8,actorName);p.setString(9,device);p.setString(10,deviceName);p.executeUpdate();
        }
    }
}
