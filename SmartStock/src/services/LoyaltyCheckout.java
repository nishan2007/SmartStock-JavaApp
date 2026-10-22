package services;

import com.google.gson.JsonObject;
import java.math.*;
import java.sql.*;
import java.util.*;

/** Integration boundaries for server-calculated sales and custom-order discounts. */
final class LoyaltyCheckout {
    private LoyaltyCheckout() { }
    static boolean eligible(Connection c,int product)throws SQLException {
        String sql=product>=0?"SELECT loyalty_eligible FROM products WHERE product_id=?":product<=-1_000_000_000?"SELECT ci.loyalty_eligible FROM custom_order_item_variants v JOIN custom_order_items ci USING(custom_item_id) WHERE v.custom_variant_id=?":"SELECT loyalty_eligible FROM custom_order_items WHERE custom_item_id=?";
        long id=product>=0?product:product<=-1_000_000_000?-(long)product-1_000_000_000L:-(long)product;
        try(var p=c.prepareStatement(sql)){p.setLong(1,id);try(var r=p.executeQuery()){return r.next()&&r.getBoolean(1);}}
    }
    static BigDecimal refundAmount(BigDecimal original,int sold,int alreadyReturned,int quantity) {
        if(sold<=0||quantity<0||alreadyReturned<0||alreadyReturned+quantity>sold)throw new IllegalArgumentException("Invalid loyalty refund allocation.");
        return original.multiply(BigDecimal.valueOf(alreadyReturned+quantity)).divide(BigDecimal.valueOf(sold),2,RoundingMode.DOWN)
                .subtract(original.multiply(BigDecimal.valueOf(alreadyReturned)).divide(BigDecimal.valueOf(sold),2,RoundingMode.DOWN));
    }
    static void lockDocument(Connection c,UUID doc)throws SQLException {
        try(var p=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?::text,1280262476))")){p.setString(1,doc.toString());p.execute();}
    }
    static BigDecimal reserve(Connection c,UUID doc,Integer customer,long requested,BigDecimal eligible,int location)throws Exception {
        return reserve(c,doc,customer,requested,eligible,location,false);
    }
    static BigDecimal reserve(Connection c,UUID doc,Integer customer,long requested,BigDecimal eligible,int location,boolean preview)throws Exception {
        if(requested==0)return BigDecimal.ZERO;
        if(requested<0||customer==null)throw new SQLException("Select a customer and a positive whole number of loyalty points.","22023");
        lockDocument(c,doc);
        return LoyaltyService.reserve(c,doc,LoyaltyService.customerUuid(c,customer),requested,eligible,location,preview).discount();
    }
    static void record(Connection c,UUID doc,long points,BigDecimal discount)throws SQLException {
        if(points==0)return;
        try(var p=c.prepareStatement("UPDATE loyalty_documents SET requested_points=?,discount_amount=?,revision=revision+1 WHERE document_uuid=?")){p.setLong(1,points);p.setBigDecimal(2,discount);p.setObject(3,doc);if(p.executeUpdate()!=1)throw new SQLException("Loyalty policy changed; refresh the purchase before redeeming points.","22023");}
    }
    static void customOrder(Connection c,long id,long points,int location)throws Exception {
        UUID doc;int customer;BigDecimal paid,total;String state,status;
        try(var p=c.prepareStatement("SELECT sync_uuid,customer_id,amount_paid,total_amount,pricing_state,status FROM custom_orders WHERE custom_order_id=? AND location_id=? AND NOT invoice_billed FOR UPDATE")){p.setLong(1,id);p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Order was not found or is billed through an invoice.","22023");doc=(UUID)r.getObject(1);customer=r.getInt(2);paid=r.getBigDecimal(3);total=r.getBigDecimal(4);state=r.getString(5);status=r.getString(6);}}
        if(paid.signum()!=0||!"FINAL".equals(state)||"CANCELLED".equals(status))throw new SQLException("Redeem points after pricing and before the first payment.","22023");
        try(var p=c.prepareStatement("SELECT 1 FROM custom_order_payments WHERE custom_order_id=? UNION ALL SELECT 1 FROM loyalty_documents WHERE document_uuid=? AND requested_points>0")){p.setLong(1,id);p.setObject(2,doc);try(var r=p.executeQuery()){if(r.next())throw new SQLException("This order already has payment or loyalty history; its redemption is locked.","22023");}}
        var lines=LoyaltyWorker.lines(c,"CUSTOM_ORDER",id);BigDecimal eligible=lines.stream().map(LoyaltyWorker.Line::eligible).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal discount=reserve(c,doc,customer,points,eligible,location);
        if(discount.compareTo(total)>=0)throw new SQLException("Loyalty redemption must leave a positive order total.","22023");
        BigDecimal running=BigDecimal.ZERO,previous=BigDecimal.ZERO;
        for(var line:lines){running=running.add(line.eligible());BigDecimal upto=discount.multiply(running).divide(eligible,2,RoundingMode.DOWN),amount=upto.subtract(previous);previous=upto;
            try(var p=c.prepareStatement("UPDATE custom_order_lines SET line_total=line_total-? WHERE custom_order_line_id=?")){p.setBigDecimal(1,amount);p.setLong(2,line.id());p.executeUpdate();}}
        try(var p=c.prepareStatement("UPDATE custom_orders SET total_amount=total_amount-?,balance_due=balance_due-?,loyalty_discount_amount=?,updated_at=now() WHERE custom_order_id=?")){p.setBigDecimal(1,discount);p.setBigDecimal(2,discount);p.setBigDecimal(3,discount);p.setLong(4,id);p.executeUpdate();}
        // Custom-order balances are tracked separately from register credit.
        LanCustomOrderWorkflowService.accountTransaction(c,customer,id,discount.negate(),"CUSTOM_ORDER_BALANCE","Loyalty redemption", "Loyalty",null,null,"ACCOUNT",null,new models.CashDrawerContext(null,null));
        record(c,doc,points,discount);
        try(var p=c.prepareStatement("DELETE FROM loyalty_document_lines WHERE document_uuid=?")){p.setObject(1,doc);p.executeUpdate();}
        LoyaltyWorker.captureLines(c,doc);
    }
}
