package services;

import Receipt.AccountPaymentReceiptData;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.sql.SQLException;
import java.util.UUID;

/** Register-side gateway for server-owned document rendering and email queuing. */
public final class EmailOutboxService {
    private static final Gson GSON=LanJson.create();
    private EmailOutboxService(){}

    public static QueueResult queueSaleReceipt(int saleId,String recipient,boolean requireEnabled)throws SQLException{
        JsonObject b=base(recipient,requireEnabled);b.addProperty("saleId",saleId);return queue("SALE_RECEIPT",b);
    }
    public static QueueResult queueCustomOrderConfirmation(String orderNumber,boolean requireEnabled)throws SQLException{
        JsonObject b=base(null,requireEnabled);b.addProperty("orderNumber",orderNumber);return queue("CUSTOM_ORDER_CONFIRMATION",b);
    }
    public static QueueResult queueAccountPaymentReceipt(AccountPaymentReceiptData receipt,String recipient,boolean requireEnabled)throws SQLException{
        JsonObject b=base(recipient,requireEnabled);b.add("receipt",GSON.toJsonTree(receipt));return queue("ACCOUNT_PAYMENT_RECEIPT",b);
    }
    public static QueueResult queueQuotation(long id,String recipient,boolean requireEnabled)throws SQLException{return document("QUOTATION",id,recipient,requireEnabled);}
    public static QueueResult queueInvoice(long id,String recipient,boolean requireEnabled)throws SQLException{return document("INVOICE",id,recipient,requireEnabled);}
    public static QueueResult queueDeliveryBill(long id,String recipient,boolean requireEnabled)throws SQLException{return document("DELIVERY_BILL",id,recipient,requireEnabled);}
    public static QueueResult queueBalanceSheetSubmission(long id)throws SQLException{JsonObject b=new JsonObject();b.addProperty("submissionId",id);return queue("BALANCE_SHEET",b);}

    private static QueueResult document(String action,long id,String recipient,boolean enabled)throws SQLException{JsonObject b=base(recipient,enabled);b.addProperty("documentId",id);return queue(action,b);}
    private static JsonObject base(String recipient,boolean enabled){JsonObject b=new JsonObject();if(recipient!=null)b.addProperty("recipient",recipient);b.addProperty("requireEnabled",enabled);return b;}
    private static QueueResult queue(String action,JsonObject body)throws SQLException{try{return GSON.fromJson(LanApiClient.queueEmail(action,body,UUID.randomUUID().toString()).get("result"),QueueResult.class);}catch(Exception e){throw e instanceof SQLException s?s:new SQLException(e.getMessage(),e);}}

    public record QueueResult(boolean queued,boolean skipped,long outboxId,String message){public static QueueResult queued(long id){return new QueueResult(true,false,id,"Email queued.");}public static QueueResult skipped(String message){return new QueueResult(false,true,0,message);}}
    public record SendResult(long outboxId,String status,String message){public static SendResult sent(long id){return new SendResult(id,"SENT","Email sent.");}public static SendResult failed(long id,String message){return new SendResult(id,"FAILED",message);}public static SendResult skipped(long id,String message){return new SendResult(id,"SKIPPED",message);}}
}
