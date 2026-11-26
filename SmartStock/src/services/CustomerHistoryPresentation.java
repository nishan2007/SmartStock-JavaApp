package services;

import java.util.*;

/** Combines a document with its bookkeeping entry without changing the underlying ledger. */
final class CustomerHistoryPresentation {
    private CustomerHistoryPresentation() { }
    static List<Map<String,Object>> consolidate(List<Map<String,Object>> source) {
        Map<String,Map<String,Object>> documents=new LinkedHashMap<>();
        List<Map<String,Object>> output=new ArrayList<>();
        for(var input:source) {
            Map<String,Object> row=new LinkedHashMap<>(input);output.add(row);
            String type=text(row,"transactionType");
            if(Set.of("SALE","CUSTOM_ORDER","INVOICE","MINUTES").contains(type)) {
                String key=key(row,type);
                if(key!=null)documents.putIfAbsent(key,row);
            }
        }
        output.removeIf(row->{
            String type=text(row,"transactionType");
            String document=switch(type) {
                case "SALE_CREDIT","SALE_PAID" -> "SALE";
                case "CUSTOM_ORDER_CREDIT","CUSTOM_ORDER_PAID","CUSTOM_ORDER_BALANCE" -> "CUSTOM_ORDER";
                case "INVOICE_CREDIT","INVOICE_PAID" -> "INVOICE";
                case "MINUTES_CREDIT","MINUTES_PAID" -> "MINUTES";
                default -> null;
            };
            if(document==null)return false;
            String key=key(row,document);
            Map<String,Object> target=key==null?null:documents.get(key);
            if(target==null)return false; // Retain orphan ledger records and every separate payment/refund.
            for(String field:List.of("transactionId","authorization")) {
                if(row.get(field)!=null&&target.get(field)==null)target.put(field,row.get(field));
            }
            for(String field:List.of("paymentMethod","paymentReference","deviceName","cashDrawerName")) {
                if(text(target,field).isBlank()&&!text(row,field).isBlank())target.put(field,row.get(field));
            }
            return true;
        });
        output.sort(Comparator.comparingLong((Map<String,Object> r)->((Number)r.getOrDefault("createdAtEpochMillis",0L)).longValue()).reversed());
        return List.copyOf(output);
    }
    private static String key(Map<String,Object> row,String type) {
        String link=switch(type){case "SALE"->"saleId";case "CUSTOM_ORDER"->"customOrderId";case "INVOICE"->"invoiceId";default->"documentId";};
        Object id=row.get(link);
        if(id==null) {
            String declared=text(row,"documentType");
            if(!declared.isBlank()&&!declared.equals(type)&&!declared.equals("ACCOUNT"))return null;
            id=row.get("documentId");
        }
        if(id==null)return null;
        Object location=row.get("locationId");
        if(location==null)return null; // Never guess a store when matching local IDs.
        return location+"|"+type+"|"+id;
    }
    private static String text(Map<String,Object> row,String field){return Objects.toString(row.get(field),"");}
}
