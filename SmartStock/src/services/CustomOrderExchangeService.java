package services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.util.*;
import models.CashDrawerContext;
import utils.CurrencyFormatter;

/** Server-owned item exchanges. The caller owns the transaction and idempotency key. */
public final class CustomOrderExchangeService {
    private CustomOrderExchangeService() { }

    public record Authorization(String representativeName,String signaturePngBase64,String unsignedReason) { }
    public record Request(long orderId, long lineId, long itemId, Long variantId,
                          boolean restock, String fingerprint, String reason,
                          String refundMethod, String refundReference, Authorization authorization) {
        public Request(long orderId,long lineId,long itemId,Long variantId,boolean restock,String fingerprint,String reason,String method,String reference) {
            this(orderId,lineId,itemId,variantId,restock,fingerprint,reason,method,reference,null);
        }
    }
    public record Price(BigDecimal lineTotal, BigDecimal discount, BigDecimal orderTotal,
                        BigDecimal paid, BigDecimal balance, BigDecimal refund) { }

    static Price price(BigDecimal oldLine, BigDecimal base, BigDecimal addons, BigDecimal percent,
                       BigDecimal total, BigDecimal paid) {
        if (base.signum()<0 || addons.signum()<0 || percent.signum()<0 || percent.compareTo(new BigDecimal("100"))>0)
            throw new IllegalArgumentException("The item has invalid pricing. Review its price first.");
        BigDecimal original=CurrencyFormatter.normalize(base.add(addons));
        BigDecimal discount=original.multiply(percent).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);
        BigDecimal line=CurrencyFormatter.normalize(original.subtract(discount));
        BigDecimal next=total.add(line.subtract(oldLine));
        if(next.signum()<0)throw new IllegalArgumentException("The replacement would make the order total negative.");
        BigDecimal refund=paid.subtract(next).max(BigDecimal.ZERO);
        BigDecimal nextPaid=paid.subtract(refund);
        return new Price(line,discount,next,nextPaid,next.subtract(nextPaid),refund);
    }

    static Price roundedOrderPrice(Price linePrice,BigDecimal oldSubtotal,BigDecimal oldLine,BigDecimal oldTotal,BigDecimal paid,boolean round) {
        BigDecimal previousRounded=CurrencyFormatter.customOrderTotal(oldSubtotal,round);
        BigDecimal nextRounded=CurrencyFormatter.customOrderTotal(oldSubtotal.add(linePrice.lineTotal().subtract(oldLine)),round);
        BigDecimal total=oldTotal.add(nextRounded.subtract(previousRounded));
        if(total.signum()<0)throw new IllegalArgumentException("The replacement would make the order total negative.");
        BigDecimal refund=paid.subtract(total).max(BigDecimal.ZERO),remainingPaid=paid.subtract(refund);
        return new Price(linePrice.lineTotal(),linePrice.discount(),total,remainingPaid,total.subtract(remainingPaid),refund);
    }

    public static void requireAdmin(Connection c,int actor)throws SQLException {
        try(var p=c.prepareStatement("SELECT 1 FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.user_id=? AND u.is_active AND upper(r.role_name)='ADMIN'")) {
            p.setInt(1,actor);try(var r=p.executeQuery()) {
                if(!r.next())throw new SQLException("Only an administrator can exchange order items.","42501");
            }
        }
    }

    private record State(Map<String,Object> order,Map<String,Object> line,Map<String,Object> replacement,
                         Price price,String fingerprint) { }

    public static Map<String,Object> preview(Connection c,int location,int actor,Request request)throws SQLException {
        requireAdmin(c,actor);
        State s=state(c,location,request,false);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("fingerprint",s.fingerprint());result.put("oldItem",s.line().get("variant_name")==null?s.line().get("item_name"):s.line().get("variant_name"));
        result.put("newItem",s.replacement().get("variant_name")==null?s.replacement().get("item_name"):s.replacement().get("variant_name"));
        result.put("oldLineTotal",s.line().get("line_total"));result.put("newLineTotal",s.price().lineTotal());
        result.put("difference",s.price().lineTotal().subtract(money(s.line(),"line_total")));
        result.put("orderTotal",s.price().orderTotal());result.put("balance",s.price().balance());
        result.put("refund",s.price().refund());result.put("stock",s.replacement().get("quantity_on_hand"));
        result.put("customerName",s.order().get("customer_name"));
        result.put("requireAuthorization",requiresAuthorization(c,s));
        return result;
    }

    private static State state(Connection c,int location,Request q,boolean lock)throws SQLException {
        String suffix=lock?" FOR UPDATE":"";
        Map<String,Object> o=row(c,"SELECT * FROM custom_orders WHERE custom_order_id=? AND location_id=?"+suffix,q.orderId(),location);
        if(o.isEmpty())throw invalid("Order was not found for this store.");
        if(List.of("CANCELLED","DELIVERED").contains(o.get("status")))throw invalid("Exchange items before delivery. Use the return workflow for delivered orders.");
        if(!"FINAL".equals(o.get("pricing_state")) || Boolean.TRUE.equals(o.get("invoice_billed")) || "ACCOUNT".equals(o.get("payment_method")))
            throw invalid("Exchanges require a final-priced order that is not billed to an invoice or customer account.");
        if(money(o,"loyalty_discount_amount").signum()>0)throw invalid("Reverse the loyalty redemption before exchanging this order's items.");
        if(money(o,"total_amount").subtract(money(o,"amount_paid")).compareTo(money(o,"balance_due"))!=0)
            throw invalid("The order has a manual balance adjustment. Reconcile it before exchanging items.");
        if(!row(c,"SELECT allocation_id FROM customer_account_payment_allocations WHERE custom_order_id=? AND amount>0 LIMIT 1",q.orderId()).isEmpty())
            throw invalid("This order has allocated customer-account payments. Reconcile those allocations before exchanging items.");
        Map<String,Object> l=row(c,"SELECT * FROM custom_order_lines WHERE custom_order_id=? AND custom_order_line_id=?"+suffix,q.orderId(),q.lineId());
        if(l.isEmpty())throw invalid("The selected order item was not found.");
        if(!"FIXED".equals(l.get("pricing_type")) || l.get("custom_item_id")==null || l.get("inventory_product_id")!=null)
            throw invalid("Choose a fixed-price custom-catalog inventory item to exchange.");
        if(!"NONE".equals(l.get("return_status")) || money(l,"returned_amount").signum()>0 || "DELIVERED".equals(l.get("delivery_status")))
            throw invalid("Delivered or returned items cannot be exchanged here.");
        Long oldVariant=number(l.get("custom_variant_id"));long oldItem=number(l.get("custom_item_id"));
        // Lock parents and variants in a stable order, including the item being returned.
        if(lock) {
            rows(c,"SELECT custom_item_id FROM custom_order_items WHERE custom_item_id IN (?,?) ORDER BY custom_item_id FOR UPDATE",oldItem,q.itemId());
            rows(c,"SELECT custom_variant_id FROM custom_order_item_variants WHERE custom_variant_id IN (?,?) ORDER BY custom_variant_id FOR UPDATE",oldVariant,q.variantId());
        }
        Map<String,Object> old=row(c,"SELECT product_type FROM custom_order_items WHERE custom_item_id=?",oldItem);
        if(!"INVENTORY".equals(old.get("product_type")))throw invalid("The original item is not an inventory item.");
        Map<String,Object> replacement=row(c,"SELECT i.custom_item_id,i.item_name,i.has_variants,i.product_type,i.pricing_type,i.is_active,i.is_archived,"+
                "i.fixed_price,i.size,i.color FROM custom_order_items i WHERE i.custom_item_id=?",q.itemId());
        if(replacement.isEmpty() || !Boolean.TRUE.equals(replacement.get("is_active")) || Boolean.TRUE.equals(replacement.get("is_archived")) ||
                !"INVENTORY".equals(replacement.get("product_type")) || !"FIXED".equals(replacement.get("pricing_type")))
            throw invalid("The replacement must be an active, fixed-price inventory item.");
        if(Boolean.TRUE.equals(replacement.get("has_variants"))!=(q.variantId()!=null))throw invalid("Select a specific replacement variant.");
        if(q.variantId()!=null) {
            Map<String,Object> v=row(c,"SELECT custom_variant_id,variant_name,fixed_price,COALESCE(size,?) AS size,COALESCE(color,?) AS color,template_width,template_height,template_unit FROM custom_order_item_variants WHERE custom_variant_id=? AND custom_item_id=? AND is_active",replacement.get("size"),replacement.get("color"),q.variantId(),q.itemId());
            if(v.isEmpty())throw invalid("The replacement variant is no longer available.");
            replacement.putAll(v);
        }
        if(oldItem==q.itemId() && Objects.equals(oldVariant,q.variantId()))throw invalid("Select a different replacement item.");
        replacement.put("quantity_on_hand",CustomItemStockService.quantity(c,q.itemId(),q.variantId(),location));
        if(money(replacement,"quantity_on_hand").compareTo(BigDecimal.ONE)<0)throw invalid("The replacement item is out of stock.");
        if(replacement.get("fixed_price")==null)throw invalid("The replacement needs a configured selling price.");
        Map<String,Object> reserved=row(c,"SELECT custom_order_inventory_reservation_id FROM custom_order_inventory_reservations WHERE custom_order_id=? AND custom_order_line_id=? AND custom_item_id=? AND custom_variant_id IS NOT DISTINCT FROM ? AND status='RESERVED' AND reserved_qty-released_qty=1"+suffix,q.orderId(),q.lineId(),oldItem,oldVariant);
        if(reserved.isEmpty())throw invalid("The original inventory reservation needs reconciliation before exchange.");
        Map<String,Object> subtotal=row(c,"SELECT COALESCE(SUM(line_total),0) AS subtotal FROM custom_order_lines WHERE custom_order_id=?",q.orderId());
        Map<String,Object> settings=row(c,"SELECT round_custom_orders_to_nearest_twenty FROM company_customization WHERE location_id=?",location);
        boolean round=!Boolean.FALSE.equals(settings.get("round_custom_orders_to_nearest_twenty"));
        Price p;
        try{
            p=price(money(l,"line_total"),money(replacement,"fixed_price"),money(l,"print_charge"),money(l,"line_discount_percent"),money(o,"total_amount"),money(o,"amount_paid"));
            p=roundedOrderPrice(p,money(subtotal,"subtotal"),money(l,"line_total"),money(o,"total_amount"),money(o,"amount_paid"),round);
        }
        catch(IllegalArgumentException e){throw invalid(e.getMessage());}
        // Include all source snapshots. Concurrent payments, production, pricing and stock changes require a new preview.
        String fingerprint=LanSecurity.sha256(o.toString()+l+replacement+reserved+subtotal+settings);
        return new State(o,l,replacement,p,fingerprint);
    }

    public static Map<String,Object> exchange(Connection c,int location,int actor,String actorName,String device,String deviceName,Request q)throws SQLException {
        requireAdmin(c,actor);
        if(c.getAutoCommit())throw new SQLException("An exchange requires a transaction.","25000");
        if(q.reason()==null || q.reason().isBlank() || q.reason().length()>2000)throw invalid("Enter a reason for the exchange (up to 2000 characters).");
        State s=state(c,location,q,true);
        if(q.fingerprint()==null || !q.fingerprint().equals(s.fingerprint()))throw invalid("The order or stock changed. Preview the exchange again.");
        var o=s.order();var l=s.line();var n=s.replacement();var price=s.price();
        String reason=q.reason().trim();
        var auth=q.authorization()==null?null:new CustomerChargeAuthorizationService.Authorization(q.authorization().representativeName(),q.authorization().signaturePngBase64(),q.authorization().unsignedReason());
        if(requiresAuthorization(c,s))CustomerChargeAuthorizationService.validateRequired(c,((Number)o.get("customer_id")).intValue(),auth);
        String method=q.refundMethod();
        CashDrawerContext drawer=new CashDrawerContext(null,null);
        if(price.refund().signum()>0) {
            if(!List.of("CASH","CARD","CHEQUE","MMG").contains(method==null?"":method))throw invalid("Choose how to refund the overpayment before exchanging.");
            if(!"CASH".equals(method) && (q.refundReference()==null || q.refundReference().isBlank()))throw invalid("Enter the refund transaction reference.");
            if(q.refundReference()!=null && q.refundReference().length()>500)throw invalid("The refund reference is too long.");
            drawer=LanCustomOrderWorkflowService.cashDrawer(c,method,location,device);
            if("CASH".equals(method)) {
                Map<String,Object> session=row(c,"SELECT status FROM cash_drawer_sessions WHERE cash_drawer_session_id=? FOR UPDATE",drawer.sessionId());
                if(!"OPEN".equals(session.get("status")))throw invalid("The refund drawer was closed. Open a drawer and preview again.");
            }
        }
        long oldItem=number(l.get("custom_item_id"));Long oldVariant=number(l.get("custom_variant_id"));
        if(q.restock())stock(c,oldItem,oldVariant,location,1,-1);
        stock(c,q.itemId(),q.variantId(),location,-1,1);
        String explanation="Order item exchange: "+itemLabel(l)+" -> "+itemLabel(n)+"; original "+(q.restock()?"returned to stock":"not returned to stock")+"; "+reason;
        if(q.restock())movement(c,oldItem,oldVariant,location,1,"RETURN_RESTOCK",explanation,actor,actorName,device,deviceName,q);
        movement(c,q.itemId(),q.variantId(),location,-1,"CUSTOM_ORDER",explanation,actor,actorName,device,deviceName,q);
        execute(c,"UPDATE custom_order_inventory_reservations SET status=?,released_qty=CASE WHEN ? THEN reserved_qty ELSE released_qty END,released_at=now(),release_reason=? WHERE custom_order_id=? AND custom_order_line_id=? AND status='RESERVED'",q.restock()?"RELEASED":"CONSUMED",q.restock(),explanation,q.orderId(),q.lineId());
        execute(c,"INSERT INTO custom_order_inventory_reservations(custom_order_id,custom_order_line_id,custom_item_id,custom_variant_id,item_name,variant_name,reserved_qty,status) VALUES(?,?,?,?,?,?,1,'RESERVED')",q.orderId(),q.lineId(),q.itemId(),q.variantId(),n.get("item_name"),n.get("variant_name"));
        execute(c,"UPDATE custom_order_lines SET custom_item_id=?,custom_variant_id=?,item_name=?,variant_name=?,item_size=?,item_color=?,template_width=?,template_height=?,template_unit=?,base_item_price=?,area_price=?,original_base_price=?,unit_price=?,original_line_total=?,line_total=?,line_discount_amount=?,price_override_price=NULL,price_override_reason=NULL,price_override_by_user_id=NULL,price_override_by_name=NULL,production_status='NOT_STARTED',production_updated_at=now(),production_updated_by_user_id=?,production_updated_by_name=? WHERE custom_order_line_id=? AND custom_order_id=?",
                q.itemId(),q.variantId(),n.get("item_name"),n.get("variant_name"),n.get("size"),n.get("color"),n.get("template_width"),n.get("template_height"),n.get("template_unit"),n.get("fixed_price"),n.get("fixed_price"),n.get("fixed_price"),price.lineTotal(),CurrencyFormatter.normalize(money(n,"fixed_price").add(money(l,"print_charge"))),price.lineTotal(),price.discount(),actor,actorName,q.lineId(),q.orderId());
        execute(c,"INSERT INTO custom_order_line_production_history(custom_order_id,custom_order_line_id,item_name,old_status,new_status,notes,updated_by_user_id,updated_by_name,device_id,device_name) VALUES(?,?,?,?,'NOT_STARTED',?,?,?,?,?)",q.orderId(),q.lineId(),n.get("item_name"),l.get("production_status"),explanation,actor,actorName,device,deviceName);
        BigDecimal required=money(o,"total_amount").signum()==0?BigDecimal.ZERO:CurrencyFormatter.normalize(money(o,"minimum_deposit_required").multiply(price.orderTotal()).divide(money(o,"total_amount"),6,RoundingMode.HALF_UP));
        execute(c,"UPDATE custom_orders SET total_amount=?,amount_paid=?,balance_due=?,payment_status=?,minimum_deposit_required=?,status='NEW',completed_at=NULL,updated_at=now() WHERE custom_order_id=? AND location_id=?",price.orderTotal(),price.paid(),price.balance(),price.balance().signum()==0?"PAID":price.paid().signum()==0?"UNPAID":"PARTIAL",required,q.orderId(),location);
        // Restore the aggregate production stage without changing the other lines.
        List<String> stages=new ArrayList<>();for(var stage:rows(c,"SELECT production_status FROM custom_order_lines WHERE custom_order_id=? AND COALESCE(return_status,'NONE')<>'FULL' AND COALESCE(delivery_status,'PENDING')<>'DELIVERED'",q.orderId()))stages.add(String.valueOf(stage.get("production_status")));
        String status=LanCustomOrderWorkflowService.aggregateProductionStatus(o.get("assigned_to_user_id")==null?"NEW":"ASSIGNED",stages);
        execute(c,"UPDATE custom_orders SET status=? WHERE custom_order_id=?",status,q.orderId());
        if(!status.equals(o.get("status")))execute(c,"INSERT INTO custom_order_status_history(custom_order_id,old_status,new_status,reason,user_id,user_name,device_id,device_name) VALUES(?,?,?,?,?,?,?,?)",q.orderId(),o.get("status"),status,explanation,actor,actorName,device,deviceName);
        Integer customer=o.get("customer_id")==null?null:((Number)o.get("customer_id")).intValue();
        BigDecimal delta=price.orderTotal().subtract(money(o,"total_amount"));
        if(delta.signum()!=0 && customer!=null) {
            try(var p=c.prepareStatement("INSERT INTO customer_account_transactions(customer_id,custom_order_id,location_id,amount,transaction_type,note,user_name,device_id,device_name) VALUES(?,?,?,?,'CUSTOM_ORDER_BALANCE',?,?,?,?) RETURNING transaction_id")) {
                p.setInt(1,customer);p.setLong(2,q.orderId());p.setInt(3,location);p.setBigDecimal(4,delta);p.setString(5,explanation);p.setString(6,actorName);p.setString(7,device);p.setString(8,deviceName);
                try(var r=p.executeQuery()){r.next();if(requiresAuthorization(c,s))CustomerChargeAuthorizationService.insert(c,r.getLong(1),customer,location,"CUSTOM_ORDER",q.orderId(),auth,actor,actorName,device==null?null:UUID.fromString(device),deviceName);}
            }
        }
        if(price.refund().signum()>0) {
            LanCustomOrderWorkflowService.insertPayment(c,q.orderId(),price.refund(),method,q.refundReference(),"REFUND",explanation,actor,actorName,device,deviceName,drawer);
            LanCustomOrderWorkflowService.accountTransaction(c,customer,q.orderId(),price.refund(),"CUSTOM_ORDER_PAYMENT",explanation,actorName,device,deviceName,method,q.refundReference(),drawer);
        }
        execute(c,"INSERT INTO custom_order_audit_log(custom_order_id,action_type,field_name,old_value,new_value,reason,user_id,user_name,device_id,device_name) VALUES(?,'ITEM_EXCHANGE',?,?,?,?,?,?,?,?)",q.orderId(),"custom_order_line_id="+q.lineId(),oldItem+"/"+oldVariant+"; total="+o.get("total_amount"),q.itemId()+"/"+q.variantId()+"; total="+price.orderTotal()+"; refund="+price.refund(),explanation,actor,actorName,device,deviceName);
        SyncOutboxService.recordEvent(c,"CUSTOM_ORDER_ITEM_EXCHANGED",Map.of("custom_order_id",q.orderId(),"custom_order_line_id",q.lineId(),"custom_item_id",q.itemId(),"restocked",q.restock(),"total_amount",price.orderTotal(),"balance_due",price.balance(),"refund",price.refund()),location,device,actor);
        return Map.of("updated",true,"balance",price.balance(),"refund",price.refund());
    }

    private static void stock(Connection c,Long item,Long variant,int location,int quantity,int sold)throws SQLException {
        if(item==null)return;
        CustomItemStockService.move(c,item,variant,location,BigDecimal.valueOf(quantity),BigDecimal.valueOf(sold),quantity<0);
    }

    private static void movement(Connection c,long item,Long variant,int location,int qty,String reason,String note,int actor,String name,String device,String deviceName,Request q)throws SQLException {
        execute(c,"INSERT INTO custom_order_item_movements(custom_item_id,custom_variant_id,variant_name,location_id,change_qty,reason,note,user_id,user_name,device_id,device_name,custom_order_id,custom_order_line_id) VALUES(?,?,(SELECT variant_name FROM custom_order_item_variants WHERE custom_variant_id=?),?,?,?,?,?,?,?,?,?,?)",item,variant,variant,location,qty,reason,note,actor,name,device,deviceName,q.orderId(),q.lineId());
    }
    private static BigDecimal money(Map<String,Object> row,String field){Object value=row.get(field);return value==null?BigDecimal.ZERO:new BigDecimal(value.toString());}
    private static String itemLabel(Map<String,Object> row){return row.get("item_name")+(row.get("variant_name")==null?"":" / "+row.get("variant_name"));}
    private static boolean requiresAuthorization(Connection c,State s)throws SQLException {
        Object customer=s.order().get("customer_id");
        return customer!=null && s.price().balance().compareTo(money(s.order(),"balance_due"))>0 && CustomerChargeAuthorizationService.required(c,((Number)customer).intValue());
    }
    private static Long number(Object value){return value==null?null:((Number)value).longValue();}
    private static SQLException invalid(String message){return new SQLException(message,"22023");}
    private static Map<String,Object> row(Connection c,String sql,Object...args)throws SQLException {var rows=rows(c,sql,args);if(rows.size()>1)throw invalid("The item has multiple active inventory reservations. Reconcile inventory first.");return rows.isEmpty()?new LinkedHashMap<>():rows.get(0);}
    private static List<Map<String,Object>> rows(Connection c,String sql,Object...args)throws SQLException {
        var rows=new ArrayList<Map<String,Object>>();try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var r=p.executeQuery()){while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));rows.add(row);}}}return rows;
    }
    private static int execute(Connection c,String sql,Object...args)throws SQLException {try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);return p.executeUpdate();}}
}
