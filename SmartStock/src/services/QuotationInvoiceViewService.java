package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.util.List;

/** Register-side quotation and invoice query gateway. */
public final class QuotationInvoiceViewService {
    private static final Gson GSON = LanJson.create();
    private QuotationInvoiceViewService() { }

    public static List<QuotationSummary> listQuotations() throws SQLException { return list("LIST_QUOTES", "rows", new TypeToken<List<QuotationSummary>>(){}.getType()); }
    public static List<InvoiceSummary> listInvoices() throws SQLException { return list("LIST_INVOICES", "rows", new TypeToken<List<InvoiceSummary>>(){}.getType()); }
    public static List<DeliverySummary> listDeliveries() throws SQLException { return list("LIST_DELIVERIES", "rows", new TypeToken<List<DeliverySummary>>(){}.getType()); }
    public static List<AuditEntry> listAudit() throws SQLException { return list("LIST_AUDIT", "rows", new TypeToken<List<AuditEntry>>(){}.getType()); }
    public static List<CustomerOption> listCustomers() throws SQLException { return searchCustomers(""); }
    public static List<CustomerOption> searchCustomers(String text) throws SQLException { JsonObject b=new JsonObject();b.addProperty("search",text);return list("SEARCH_CUSTOMERS",b,"rows",new TypeToken<List<CustomerOption>>(){}.getType()); }
    public static QuotationEditData loadQuotationForEdit(long id) throws SQLException { JsonObject b=new JsonObject();b.addProperty("quotationId",id);return one("QUOTE_EDIT",b,"quotation",QuotationEditData.class); }
    public static List<ProductOption> listProducts() throws SQLException { return searchProducts(""); }
    public static List<ProductOption> searchProducts(String text) throws SQLException { JsonObject b=new JsonObject();b.addProperty("search",text);return list("SEARCH_PRODUCTS",b,"rows",new TypeToken<List<ProductOption>>(){}.getType()); }
    public static List<DeliverableLine> listDeliverableLines(long id) throws SQLException { JsonObject b=new JsonObject();b.addProperty("invoiceId",id);return list("DELIVERABLE_LINES",b,"rows",new TypeToken<List<DeliverableLine>>(){}.getType()); }
    public static InvoiceFinancials loadInvoiceFinancials(long id) throws SQLException { JsonObject b=new JsonObject();b.addProperty("invoiceId",id);return one("INVOICE_FINANCIALS",b,"financials",InvoiceFinancials.class); }

    private static <T>T one(String action,JsonObject b,String field,Class<T>type)throws SQLException{try{return GSON.fromJson(LanApiClient.quotationRead(action,b).get(field),type);}catch(Exception e){throw sql(e);}}
    private static <T>List<T> list(String action,String field,java.lang.reflect.Type type)throws SQLException{return list(action,new JsonObject(),field,type);}
    private static <T>List<T> list(String action,JsonObject b,String field,java.lang.reflect.Type type)throws SQLException{try{return GSON.fromJson(LanApiClient.quotationRead(action,b).get(field),type);}catch(Exception e){throw sql(e);}}
    private static SQLException sql(Exception e){return e instanceof SQLException s?s:new SQLException(e.getMessage(),e);}

    public record QuotationSummary(long quotationId,String quotationNumber,String customerName,String status,Date validUntil,BigDecimal totalAmount){}
    public record QuotationEditData(long quotationId,String quotationNumber,int customerId,String customerName,String status,Date validUntil,Date productionDueDate,String notes,List<QuotationEditLine>lines){}
    public record QuotationEditLine(Integer productId,String itemName,String sku,int quantity,BigDecimal unitPrice,BigDecimal originalUnitPrice,BigDecimal discountPercent,String deliveryMethod,String notes,String priceOverrideReason,Integer priceOverrideByUserId,String priceOverrideByName,QuotationInvoiceService.CustomLineInput custom){}
    public record InvoiceSummary(long invoiceId,String invoiceNumber,String customerName,String status,String paymentStatus,BigDecimal balanceDue,String quotationNumber){}
    public record DeliverySummary(long deliveryEventId,String deliveryNumber,String invoiceNumber,String customerName,String deliveryMethod,BigDecimal balanceDue,String createdAt){}
    public record InvoiceFinancials(long invoiceId,String invoiceNumber,BigDecimal totalAmount,BigDecimal amountPaid,BigDecimal balanceDue,BigDecimal customerBalance,BigDecimal creditLimit,BigDecimal availableCredit){}
    public record AuditEntry(String createdAt,String document,String actionType,String fieldName,String oldValue,String newValue,String userName,String reason){}
    public record CustomerOption(int customerId,String accountNumber,String name,boolean business){@Override public String toString(){return(business?"[Business] ":"")+name+(accountNumber==null||accountNumber.isBlank()?"":" ("+accountNumber+")");}}
    public record ProductOption(Integer productId,String name,String sku,String barcode,String description,BigDecimal price){@Override public String toString(){if(productId==null)return name;String code=sku==null||sku.isBlank()?barcode:sku;return name+(code==null||code.isBlank()?"":" ("+code+")");}}
    public record DeliverableLine(long invoiceLineId,Integer productId,String itemName,int quantityInvoiceed,int quantityDelivered,int remaining,int availableStock){}
}
