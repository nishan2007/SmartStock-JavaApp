package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Register-side quotation and invoice command gateway. */
public final class QuotationInvoiceService {
    private static final Gson GSON=LanJson.create();
    private QuotationInvoiceService(){}
    public static QuotationResult createQuotation(int customerId,LocalDate validUntil,String notes,List<QuotationLineInput>lines)throws SQLException{JsonObject b=base(customerId,validUntil,notes,lines);return result("CREATE",b,"quotation",QuotationResult.class);}
    public static void issueQuotation(long id)throws SQLException{mutate("ISSUE",id,null);}
    public static QuotationResult updateDraftQuotation(long id,int customerId,LocalDate validUntil,String notes,List<QuotationLineInput>lines)throws SQLException{JsonObject b=base(customerId,validUntil,notes,lines);b.addProperty("quotationId",id);return result("UPDATE",b,"quotation",QuotationResult.class);}
    public static void cancelQuotation(long id,String reason)throws SQLException{JsonObject b=new JsonObject();b.addProperty("quotationId",id);b.addProperty("reason",reason);mutate("CANCEL",null,b);}
    public static InvoiceResult acceptQuotation(long id)throws SQLException{JsonObject b=new JsonObject();b.addProperty("quotationId",id);return result("ACCEPT",b,"invoice",InvoiceResult.class);}
    public static PaymentReceiptRef recordPayment(long id,BigDecimal amount,String method,String reference)throws SQLException{return recordPayment(id,amount,method,reference,null,null);}
    public static PaymentReceiptRef recordPayment(long id,BigDecimal amount,String method,String reference,String approvalToken,String approvalReason)throws SQLException{JsonObject b=new JsonObject();b.addProperty("invoiceId",id);b.addProperty("amount",amount);b.addProperty("method",method);b.addProperty("reference",reference);b.addProperty("approvalToken",approvalToken);b.addProperty("approvalReason",approvalReason);return resultNullable("PAYMENT",b,"receipt",PaymentReceiptRef.class);}
    public static void chargeInvoiceToAccount(long id,String reason)throws SQLException{chargeInvoiceToAccount(id,reason,null,null);}
    public static void chargeInvoiceToAccount(long id,String reason,String approvalToken,String approvalReason)throws SQLException{JsonObject b=new JsonObject();b.addProperty("invoiceId",id);b.addProperty("reason",reason);b.addProperty("approvalToken",approvalToken);b.addProperty("approvalReason",approvalReason);mutate("ACCOUNT",null,b);}
    public static DeliveryResult postDelivery(long id,String method,String receiver,String notes,List<DeliveryLineInput>lines)throws SQLException{JsonObject b=new JsonObject();b.addProperty("invoiceId",id);b.addProperty("deliveryMethod",method);b.addProperty("receiverName",receiver);b.addProperty("notes",notes);b.add("lines",GSON.toJsonTree(lines));return result("DELIVERY",b,"delivery",DeliveryResult.class);}
    private static JsonObject base(int customerId,LocalDate until,String notes,List<QuotationLineInput>lines){JsonObject b=new JsonObject();b.addProperty("customerId",customerId);b.addProperty("validUntil",until==null?null:until.toString());b.addProperty("notes",notes);b.add("lines",GSON.toJsonTree(lines));return b;}
    private static void mutate(String action,Long id,JsonObject b)throws SQLException{if(b==null)b=new JsonObject();if(id!=null)b.addProperty("quotationId",id);try{LanApiClient.quotationMutation(action,b,UUID.randomUUID().toString());}catch(Exception e){throw sql(e);}}
    private static <T>T result(String action,JsonObject b,String field,Class<T>type)throws SQLException{T value=resultNullable(action,b,field,type);if(value==null)throw new SQLException("The server returned no "+field+" result.");return value;}
    private static <T>T resultNullable(String action,JsonObject b,String field,Class<T>type)throws SQLException{try{JsonObject r=LanApiClient.quotationMutation(action,b,UUID.randomUUID().toString());return !r.has(field)||r.get(field).isJsonNull()?null:GSON.fromJson(r.get(field),type);}catch(Exception e){throw sql(e);}}
    private static SQLException sql(Exception e){return e instanceof SQLException s?s:new SQLException(e.getMessage(),e);}
    public record QuotationLineInput(Integer productId,String itemName,String sku,int quantity,BigDecimal unitPrice,BigDecimal originalUnitPrice,BigDecimal discountPercent,String deliveryMethod,String notes,String priceOverrideReason,Integer priceOverrideByUserId,String priceOverrideByName,String priceOverrideApprovalToken){}
    public record DeliveryLineInput(long salesInvoiceLineId,int quantityDelivered){}
    public record QuotationResult(long quotationId,String quotationNumber){}
    public record InvoiceResult(long invoiceId,String invoiceNumber){}
    public record DeliveryResult(long deliveryEventId,String deliveryNumber){}
    public record PaymentReceiptRef(int customerId,long transactionId){}
}
