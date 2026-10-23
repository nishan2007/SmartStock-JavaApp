package services;

import com.google.gson.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/** Converts existing unpaid account stock sales without changing inventory or drawer cash. */
final class CompanyExpenseTransferService {
    private static final Gson GSON=new Gson();
    private CompanyExpenseTransferService() { }

    static void require(Connection c,int user)throws Exception {
        for(String key:List.of("CUSTOMER_ACCOUNTS","RECORD_STOCK_EXPENSE","PROCESS_RETURNS","RETURN_OVERRIDE"))
            LanInventoryService.requirePermission(c,user,key);
    }

    static Map<String,Object> preview(Connection c,JsonObject body,int user,int location)throws Exception {
        require(c,user);
        return inspect(c,body.get("customerId").getAsInt(),location,false);
    }

    private static Map<String,Object> inspect(Connection c,int customer,int location,boolean lock)throws Exception {
        String accountName,accountNumber;
        try(var p=c.prepareStatement("SELECT name,COALESCE(account_number,'') FROM customer_accounts WHERE customer_id=?"+(lock?" FOR UPDATE":""))){
            p.setInt(1,customer);try(var r=p.executeQuery()){if(!r.next())throw invalid("Customer account was not found.");accountName=r.getString(1);accountNumber=r.getString(2);}
        }
        List<Map<String,Object>> eligible=new ArrayList<>(),skipped=new ArrayList<>();BigDecimal total=BigDecimal.ZERO;
        String query="SELECT sale_id,COALESCE(receipt_number,'Sale #'||sale_id),total_amount,COALESCE(amount_paid,0),COALESCE(returned_amount,0),created_at,COALESCE(user_name,''),payment_status,status FROM sales WHERE customer_id=? AND location_id=? AND payment_method='ACCOUNT' ORDER BY sale_id"+(lock?" FOR UPDATE":"");
        try(var p=c.prepareStatement(query)){
            p.setInt(1,customer);p.setInt(2,location);
            try(var r=p.executeQuery()){while(r.next()){
                int sale=r.getInt(1);BigDecimal amount=r.getBigDecimal(3);String why=null;
                if(r.getBigDecimal(4).signum()!=0 || "PAID".equalsIgnoreCase(r.getString(8)))why="Paid or partly paid receipt";
                else if(!"COMPLETED".equalsIgnoreCase(r.getString(9)))why="Receipt is not a completed sale";
                else if(r.getBigDecimal(5).signum()!=0)why="Already returned or transferred";
                List<Line> lines=lines(c,sale,lock);
                BigDecimal sum=lines.stream().map(l->l.price().multiply(BigDecimal.valueOf(l.quantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
                if(why==null && (amount==null || amount.signum()<=0 || sum.compareTo(amount)!=0))why="Discount or receipt total requires manual review";
                if(why==null && (lines.isEmpty() || lines.stream().anyMatch(l->l.quantity()<=0 || l.quantity()>100000 || l.price().signum()<0 || !l.stock() || l.itemId()<=0)))why="Contains non-stock, miscellaneous, or unsupported items";
                try(var q=c.prepareStatement("SELECT COALESCE(SUM(CASE WHEN transaction_type='SALE_CREDIT' THEN ABS(amount) ELSE 0 END),0),COUNT(*) FILTER(WHERE transaction_type IN ('RETURN','PAYMENT')) FROM customer_account_transactions WHERE customer_id=? AND sale_id=?")){
                    q.setInt(1,customer);q.setInt(2,sale);try(var credit=q.executeQuery()){credit.next();if(why==null && (credit.getBigDecimal(1).compareTo(amount)!=0 || credit.getLong(2)!=0))why="Account credit/payment history requires manual review";}
                }
                if(why==null && lines.size()>200)why="Receipt has more than 200 lines";
                if(why==null && hasLoyalty(c,sale))why="Receipt has loyalty activity; needs separate review";
                if(why!=null){skipped.add(Map.of("saleId",sale,"receipt",r.getString(2),"reason",why));continue;}
                Map<String,Object> row=new LinkedHashMap<>();row.put("saleId",sale);row.put("receipt",r.getString(2));row.put("amount",amount);
                row.put("purchasedAt",r.getTimestamp(6).getTime());row.put("cashier",r.getString(7));row.put("lines",lines);eligible.add(row);total=total.add(amount);
            }}
        }
        BigDecimal balance=CustomerAccountLedgerService.calculateCustomerBalance(c,customer);
        if(balance.compareTo(total)<0)throw invalid("Account payments or credits reduce the balance below these purchases. Review allocations before transferring.");
        Map<String,Object> result=new LinkedHashMap<>();result.put("customerId",customer);result.put("accountName",accountName);result.put("accountNumber",accountNumber);
        result.put("eligible",eligible);result.put("skipped",skipped);result.put("total",total);result.put("balanceBefore",balance);
        result.put("previewToken",LanSecurity.sha256(GSON.toJson(result)));return result;
    }

    private static boolean hasLoyalty(Connection c,int sale)throws SQLException {
        try(var p=c.prepareStatement("SELECT COALESCE(s.loyalty_discount_amount,0)<>0 OR EXISTS(SELECT 1 FROM loyalty_documents d WHERE d.document_type='SALE' AND d.local_id=s.sale_id AND d.source_location_id=s.location_id) FROM sales s WHERE s.sale_id=?")){
            p.setInt(1,sale);try(var r=p.executeQuery()){return r.next() && r.getBoolean(1);}
        }
    }

    private static List<Line> lines(Connection c,int sale,boolean lock)throws SQLException {
        List<Line> lines=new ArrayList<>();
        try(var p=c.prepareStatement("""
                SELECT si.sale_item_id,si.quantity,si.unit_price,COALESCE(si.item_name,p.name,'Historical stock item'),
                CASE WHEN si.custom_variant_id IS NOT NULL THEN 'CUSTOM_VARIANT' WHEN si.custom_item_id IS NOT NULL THEN 'CUSTOM_ITEM' ELSE 'PRODUCT' END,
                COALESCE(si.custom_variant_id,si.custom_item_id,si.product_id),
                COALESCE(si.product_type,p.product_type,'INVENTORY')='INVENTORY' AND NOT COALESCE(si.is_misc_item,FALSE),
                si.loyalty_net_amount
                FROM sale_items si LEFT JOIN products p ON p.product_id=si.product_id WHERE si.sale_id=? ORDER BY si.sale_item_id
                """+(lock?" FOR UPDATE OF si":""))){
            p.setInt(1,sale);try(var r=p.executeQuery()){while(r.next()){
                BigDecimal price=r.getBigDecimal(3);if(price==null)price=BigDecimal.ZERO;
                boolean stock=r.getBoolean(7);BigDecimal net=r.getBigDecimal(8);
                if(net!=null && net.compareTo(price.multiply(BigDecimal.valueOf(r.getInt(2))))!=0)stock=false;
                lines.add(new Line(r.getInt(1),r.getInt(2),price,r.getString(4),r.getString(5),r.getInt(6),stock));
            }}
        }return lines;
    }

    @SuppressWarnings("unchecked")
    static Map<String,Object> transfer(Connection c,JsonObject body,UUID device,int user,String name,int location)throws Exception {
        require(c,user);
        int customer=body.get("customerId").getAsInt();
        Map<String,Object> preview=inspect(c,customer,location,true);
        if(!body.has("previewToken") || !Objects.equals(preview.get("previewToken"),body.get("previewToken").getAsString()))throw invalid("The account changed. Review a fresh transfer preview.");
        List<Map<String,Object>> sales=(List<Map<String,Object>>)preview.get("eligible");
        if(sales.isEmpty())throw invalid("There are no eligible purchases to transfer.");
        if(!body.has("signaturePngBase64") || body.get("signaturePngBase64").isJsonNull())throw invalid("Sign the company expense transfer before posting.");
        byte[] signature=StockLedgerService.validateSignature(body.get("signaturePngBase64").getAsString());
        String category=body.has("category")?body.get("category").getAsString():"Company Use";
        if(!List.of("Company Use","Donation").contains(category))throw invalid("Choose Company Use or Donation.");
        // Schema readiness is established by DB.getConnection before this transaction.
        // Do not invoke schema-upgrade helpers from inside an accounting transfer.
        try(var p=c.prepareStatement("UPDATE customer_accounts SET current_balance=? WHERE customer_id=?")){
            p.setBigDecimal(1,(BigDecimal)preview.get("balanceBefore"));p.setInt(2,customer);p.executeUpdate();
        }
        int transferred=0;
        for(Map<String,Object> sale:sales){
            int saleId=(Integer)sale.get("saleId");String reason="Company expense transfer from "+preview.get("accountName")+" / receipt "+sale.get("receipt")+"; original cashier: "+sale.get("cashier");
            List<Line> lines=(List<Line>)sale.get("lines");JsonObject refund=new JsonObject();refund.addProperty("saleId",saleId);refund.addProperty("refundMethod","ACCOUNT");refund.addProperty("reason",reason);
            JsonArray quantities=new JsonArray();for(Line line:lines){JsonObject q=new JsonObject();q.addProperty("saleItemId",line.saleItemId());q.addProperty("quantity",line.quantity());quantities.add(q);}refund.add("lines",quantities);
            Map<String,Object> corrected=LanRefundService.refundForCompanyExpense(c,refund,device,user,name,location);
            if(((BigDecimal)corrected.get("refundAmount")).compareTo((BigDecimal)sale.get("amount"))!=0)throw invalid("Receipt amount changed. Transfer was rolled back.");
            for(Line line:lines){
                UUID id=UUID.randomUUID();
                try(var p=c.prepareStatement("""
                        INSERT INTO stock_ledger_entries(entry_id,location_id,ledger_type,item_type,item_id,item_name,quantity,
                        selling_price,category,description,reason,user_id,user_name,device_id,signature_png,created_at,
                        source_movement_type,source_movement_id,valuation_source)
                        VALUES(?,?,'EXPENSE',?,?,?,?,?,?,?,?,?,?,?,?,?,'ACCOUNT_SALE_ITEM',?,'ORIGINAL_RECEIPT_PRICE')
                        """)){
                    Object[] values={id,location,line.type(),line.itemId(),line.name(),line.quantity(),line.price(),category,
                            "Account #"+preview.get("accountNumber")+"; sale_id="+saleId+"; correction return_id="+corrected.get("returnId")+"; no stock movement; transfer signed at "+java.time.Instant.now(),reason,user,name,device,signature,
                            new Timestamp((Long)sale.get("purchasedAt")),line.saleItemId()};
                    for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);p.executeUpdate();
                }
                SyncOutboxService.recordEvent(c,"STOCK_LEDGER_ENTRY_CREATED",Map.of("entry_id",id.toString(),"source_sale_id",saleId,"source_sale_item_id",line.saleItemId()),location,device.toString(),user);
            }
            try(var p=c.prepareStatement("UPDATE customer_account_transactions SET note=? WHERE customer_id=? AND sale_id=? AND transaction_type='RETURN'")){
                p.setString(1,reason+"; correction return_id="+corrected.get("returnId")+"; no cash refund or stock change");p.setInt(2,customer);p.setInt(3,saleId);p.executeUpdate();
            }transferred++;
        }
        BigDecimal after=CustomerAccountLedgerService.calculateCustomerBalance(c,customer);
        try(var p=c.prepareStatement("UPDATE customer_accounts SET current_balance=?,updated_at=CURRENT_TIMESTAMP WHERE customer_id=?")){
            p.setBigDecimal(1,after);p.setInt(2,customer);p.executeUpdate();
        }
        return Map.of("transferred",transferred,"amount",preview.get("total"),"balanceAfter",after,"skipped",preview.get("skipped"));
    }

    private static LanInventoryService.RuleViolation invalid(String message){return new LanInventoryService.RuleViolation(409,"COMPANY_EXPENSE_TRANSFER_REJECTED",message);}
    private record Line(int saleItemId,int quantity,BigDecimal price,String name,String type,int itemId,boolean stock) { }
}
