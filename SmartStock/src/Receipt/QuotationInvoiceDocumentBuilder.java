package Receipt;

import services.LanApiClient;
import managers.CompanyCustomizationManager;
import java.sql.SQLException;

/** Register-side document gateway; document data is assembled only by the store server. */
public final class QuotationInvoiceDocumentBuilder {
    private QuotationInvoiceDocumentBuilder() { }
    public static String buildQuotation(long id)throws SQLException{return load("QUOTATION",id);}
    public static String buildInvoice(long id)throws SQLException{return load("INVOICE",id);}
    public static String buildDelivery(long id)throws SQLException{return load("DELIVERY",id);}
    public static String buildSampleQuotation(CompanyCustomizationManager.ReceiptSettings receipt,CompanyCustomizationManager.QuotationInvoicePrintSettings print){return ServerQuotationInvoiceDocumentBuilder.buildSampleQuotation(receipt,print);}
    public static String buildSampleInvoice(CompanyCustomizationManager.ReceiptSettings receipt,CompanyCustomizationManager.QuotationInvoicePrintSettings print){return ServerQuotationInvoiceDocumentBuilder.buildSampleInvoice(receipt,print);}
    public static String buildSampleDelivery(CompanyCustomizationManager.ReceiptSettings receipt,CompanyCustomizationManager.QuotationInvoicePrintSettings print){return ServerQuotationInvoiceDocumentBuilder.buildSampleDelivery(receipt,print);}
    private static String load(String type,long id)throws SQLException{try{return LanApiClient.loadQuotationDocument(type,id);}catch(Exception e){throw new SQLException(e.getMessage(),e);}}
}
