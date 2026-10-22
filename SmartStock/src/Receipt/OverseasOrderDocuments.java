package Receipt;

import com.google.gson.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Reuses the configured order-slip printer and layout without exposing overseas purchase costs. */
public final class OverseasOrderDocuments {
    private OverseasOrderDocuments() { }
    public static CustomOrderSlipData build(JsonObject detail, JsonObject payment) {
        JsonObject o=payment!=null&&payment.has("overseas_receipt")&&!payment.get("overseas_receipt").isJsonNull()
                ?payment.getAsJsonObject("overseas_receipt"):detail.getAsJsonObject("order");
        List<CustomOrderSlipData.Line> lines=new ArrayList<>();
        for(JsonElement e:o.getAsJsonArray("lines")) {
            JsonObject l=e.getAsJsonObject();int qty=l.get("quantity").getAsInt();BigDecimal unit=l.get("unit_price").getAsBigDecimal();
            lines.add(new CustomOrderSlipData.Line(l.get("description").getAsString()+" x "+qty,
                    string(l,"options"),"GYD "+unit.toPlainString()+" each"+(string(l,"seller").isBlank()?"":" · Seller: "+string(l,"seller")),null,unit.multiply(BigDecimal.valueOf(qty))));
        }
        if(o.get("shipping").getAsBigDecimal().signum()>0)
            lines.add(new CustomOrderSlipData.Line("Extra shipping",null,null,null,o.get("shipping").getAsBigDecimal()));
        if(string(o,"state").equals("CANCELLED")) {
            BigDecimal reversal=lines.stream().map(CustomOrderSlipData.Line::lineTotal).reduce(BigDecimal.ZERO,BigDecimal::add).negate();
            lines.add(new CustomOrderSlipData.Line("Cancellation adjustment",null,null,null,reversal));
        }
        String title="OVERSEAS ORDER SLIP · "+string(o,"state"),method="",reference="";BigDecimal paid=o.get("paid").getAsBigDecimal();
        if(payment!=null) {
            title="OVERSEAS "+string(payment,"payment_action")+" RECEIPT #"+string(payment,"custom_order_payment_id")
                    +"\nThis transaction: GYD "+string(payment,"payment_amount")+"\nReceived by: "+string(payment,"taken_by_name")
                    +"\nRecorded: "+string(payment,"created_at");method=string(payment,"payment_method");reference=string(payment,"payment_reference");
        }
        String date=string(o,"expected_date");BigDecimal balance=o.get("balance").getAsBigDecimal();
        return new CustomOrderSlipData(string(o,"number"),string(o,"customer_name"),string(o,"customer_phone"),"",
                date.isBlank()?null:LocalDate.parse(date),Timestamp.from(java.time.OffsetDateTime.parse(string(payment==null?o:payment,"created_at")).toInstant()),"",string(o,"store_name").isBlank()?managers.SessionManager.getCurrentLocationName():string(o,"store_name"),
                services.DeviceContextService.currentDeviceName(),method,reference,balance.signum()==0?"PAID":paid.signum()==0?"UNPAID":"PARTIAL",
                o.get("total").getAsBigDecimal(),paid,balance,title+"\nItem prices include shipping.\n"+string(o,"notes"),lines);
    }
    private static String string(JsonObject o,String key){return !o.has(key)||o.get(key).isJsonNull()?"":o.get(key).getAsString();}
}
