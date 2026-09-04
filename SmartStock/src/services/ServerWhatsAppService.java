package services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;

/** Server-only WhatsApp Cloud API sender and conservative monthly cost estimator. */
final class ServerWhatsAppService {
    private static final Gson GSON = LanJson.create();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private ServerWhatsAppService() { }

    static WhatsAppService.SendResult send(Connection c, String type, long id, int userId, int locationId) throws Exception {
        ensureSchema(c);
        String normalizedType = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("SALE_RECEIPT", "QUOTATION", "INVOICE", "DELIVERY_BILL", "ACCOUNT_PAYMENT_RECEIPT").contains(normalizedType))
            throw new Rule(400, "WHATSAPP_DOCUMENT_INVALID", "This document cannot be sent by WhatsApp.");
        Settings settings = settings(c, locationId);
        if (!settings.enabled()) throw new Rule(409, "WHATSAPP_DISABLED", "WhatsApp sending is not enabled for this store.");
        if (blank(settings.phoneNumberId()) || blank(settings.apiVersion())) throw new Rule(409,"WHATSAPP_NOT_CONFIGURED","Configure the Cloud API phone number ID and Graph API version.");
        String token = System.getenv("SMARTSTOCK_WHATSAPP_ACCESS_TOKEN");
        if (blank(token)) throw new Rule(409, "WHATSAPP_NOT_CONFIGURED", "The store server is missing SMARTSTOCK_WHATSAPP_ACCESS_TOKEN.");
        Document doc = loadDocument(c, normalizedType, id);
        if (doc.customerId() == null) throw new Rule(409, "WHATSAPP_CUSTOMER_REQUIRED", "This document is not linked to a customer account.");
        Recipient recipient = recipient(c, doc.customerId());
        String phone = normalizePhone(recipient.phone());
        if (!recipient.optedIn() || !phone.equals(normalizePhone(recipient.consentedPhone())))
            throw new Rule(409, "WHATSAPP_CONSENT_REQUIRED", "The customer must opt in to WhatsApp messages for this phone number.");
        String mode = settings.messageMode();
        String template = settings.template(normalizedType, mode);
        if (blank(template)) throw new Rule(409, "WHATSAPP_TEMPLATE_REQUIRED", "Configure an approved WhatsApp template for " + normalizedType + " (" + mode + ").");
        String text = render(c, normalizedType, id, doc, settings, mode);
        BigDecimal estimate = monthAccepted(c, locationId).multiply(settings.estimatedPrice());
        boolean warning = estimate.add(settings.estimatedPrice()).compareTo(settings.monthlyBudget().multiply(new BigDecimal("0.80"))) >= 0;
        long outboxId = insertOutbox(c, locationId, userId, doc, normalizedType, id, phone, mode, template, text, settings.estimatedPrice());
        JsonObject payload = payload(phone, template, settings.language(), text);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/" + settings.apiVersion() + "/" + settings.phoneNumberId() + "/messages"))
                    .timeout(Duration.ofSeconds(25)).header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String safe = "Meta rejected the message (HTTP " + response.statusCode() + "). Check the phone number, template, token, and WhatsApp Manager.";
                update(c, outboxId, "FAILED", null, safe);
                return new WhatsAppService.SendResult(false,outboxId,"",warning,safe);
            }
            JsonObject responseJson = GSON.fromJson(response.body(), JsonObject.class);
            String messageId = responseJson != null && responseJson.has("messages") && responseJson.getAsJsonArray("messages").size() > 0
                    ? responseJson.getAsJsonArray("messages").get(0).getAsJsonObject().get("id").getAsString() : "";
            update(c, outboxId, "ACCEPTED", messageId, null);
            String message = "WhatsApp accepted the message." + (warning ? " Estimated monthly messaging cost is near or above the configured warning level." : "");
            return new WhatsAppService.SendResult(true, outboxId, messageId, warning, message);
        } catch (Exception ex) { String safe="WhatsApp could not be reached. The message was not accepted.";update(c, outboxId, "FAILED", null, safe);return new WhatsAppService.SendResult(false,outboxId,"",warning,safe); }
    }

    private static Settings settings(Connection c, int locationId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT enabled,phone_number_id,api_version,message_mode,template_language,estimated_price_usd,monthly_budget_usd,template_names,contact_line FROM whatsapp_configuration WHERE location_id=?")) {
            ps.setInt(1, locationId); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return new Settings(rs.getBoolean(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getBigDecimal(6),rs.getBigDecimal(7),rs.getString(8),rs.getString(9)); }
        }
        return new Settings(false,"","v23.0","FULL","en_US",new BigDecimal("0.05"),new BigDecimal("5.00"),"{}","");
    }

    static java.util.Map<String,Object> loadConfiguration(Connection c,int locationId)throws SQLException{ensureSchema(c);Settings s=settings(c,locationId);return java.util.Map.of("enabled",s.enabled(),"phoneNumberId",s.phoneNumberId()==null?"":s.phoneNumberId(),"apiVersion",s.apiVersion(),"messageMode",s.messageMode(),"language",s.language(),"estimatedPrice",s.estimatedPrice(),"monthlyBudget",s.monthlyBudget(),"templates",s.templates(),"contactLine",s.contactLine(),"tokenConfigured",!blank(System.getenv("SMARTSTOCK_WHATSAPP_ACCESS_TOKEN")));}
    static java.util.Map<String,Object> saveConfiguration(Connection c,int locationId,JsonObject b)throws Exception{ensureSchema(c);String mode=b.has("messageMode")?b.get("messageMode").getAsString().toUpperCase(Locale.ROOT):"FULL";if(!java.util.Set.of("FULL","COMPACT","REFERENCE").contains(mode))throw new Rule(400,"VALIDATION_ERROR","Select a valid WhatsApp message detail mode.");BigDecimal price=b.get("estimatedPrice").getAsBigDecimal(),budget=b.get("monthlyBudget").getAsBigDecimal();if(price.signum()<0||budget.signum()<0)throw new Rule(400,"VALIDATION_ERROR","WhatsApp estimates cannot be negative.");String templates=b.has("templates")?b.get("templates").getAsString():"{}";try{GSON.fromJson(templates,JsonObject.class);}catch(Exception e){throw new Rule(400,"VALIDATION_ERROR","Template names must be a valid JSON object.");}try(PreparedStatement ps=c.prepareStatement("INSERT INTO whatsapp_configuration(location_id,enabled,phone_number_id,api_version,message_mode,template_language,estimated_price_usd,monthly_budget_usd,template_names,contact_line) VALUES(?,?,?,?,?,?,?,?,?::jsonb,?) ON CONFLICT(location_id) DO UPDATE SET enabled=EXCLUDED.enabled,phone_number_id=EXCLUDED.phone_number_id,api_version=EXCLUDED.api_version,message_mode=EXCLUDED.message_mode,template_language=EXCLUDED.template_language,estimated_price_usd=EXCLUDED.estimated_price_usd,monthly_budget_usd=EXCLUDED.monthly_budget_usd,template_names=EXCLUDED.template_names,contact_line=EXCLUDED.contact_line,updated_at=CURRENT_TIMESTAMP")){ps.setInt(1,locationId);ps.setBoolean(2,b.has("enabled")&&b.get("enabled").getAsBoolean());ps.setString(3,b.get("phoneNumberId").getAsString().trim());ps.setString(4,b.get("apiVersion").getAsString().trim());ps.setString(5,mode);ps.setString(6,b.get("language").getAsString().trim());ps.setBigDecimal(7,price);ps.setBigDecimal(8,budget);ps.setString(9,templates);ps.setString(10,b.get("contactLine").getAsString().trim());ps.executeUpdate();}return java.util.Map.of("saved",true);}

    private static Recipient recipient(Connection c, int customerId) throws SQLException, Rule {
        try (PreparedStatement ps=c.prepareStatement("SELECT COALESCE(phone,''),COALESCE(whatsapp_opt_in,FALSE),COALESCE(whatsapp_consent_phone,'') FROM customer_accounts WHERE customer_id=? AND COALESCE(is_active,TRUE)")) {
            ps.setInt(1,customerId);try(ResultSet rs=ps.executeQuery()){if(rs.next())return new Recipient(rs.getString(1),rs.getBoolean(2),rs.getString(3));}
        } throw new Rule(404,"CUSTOMER_NOT_FOUND","The customer account was not found or is inactive.");
    }

    private static Document loadDocument(Connection c,String type,long id)throws SQLException,Rule{
        String sql=switch(type){
            case "SALE_RECEIPT" -> "SELECT s.customer_id,COALESCE(s.receipt_number,'Sale #'||s.sale_id),COALESCE(ca.name,''),COALESCE(l.name,''),s.created_at,COALESCE(s.total_amount,0),COALESCE(s.amount_paid,0),COALESCE(s.payment_status,'') FROM sales s LEFT JOIN customer_accounts ca ON ca.customer_id=s.customer_id LEFT JOIN locations l ON l.location_id=s.location_id WHERE s.sale_id=?";
            case "QUOTATION" -> "SELECT q.customer_id,COALESCE(q.quotation_number,'Quotation '||q.quotation_id),COALESCE(q.customer_name,ca.name,''),COALESCE(l.name,''),q.created_at,COALESCE(q.total_amount,0),0,COALESCE(q.status,'') FROM quotations q LEFT JOIN customer_accounts ca ON ca.customer_id=q.customer_id LEFT JOIN locations l ON l.location_id=q.location_id WHERE q.quotation_id=?";
            case "INVOICE" -> "SELECT i.customer_id,COALESCE(i.invoice_number,'Invoice '||i.invoice_id),COALESCE(i.customer_name,ca.name,''),COALESCE(l.name,''),i.created_at,COALESCE(i.total_amount,0),COALESCE(i.amount_paid,0),COALESCE(i.payment_status,'') FROM invoices i LEFT JOIN customer_accounts ca ON ca.customer_id=i.customer_id LEFT JOIN locations l ON l.location_id=i.location_id WHERE i.invoice_id=?";
            case "DELIVERY_BILL" -> "SELECT i.customer_id,COALESCE(d.delivery_number,'Delivery '||d.invoice_delivery_event_id),COALESCE(i.customer_name,ca.name,''),COALESCE(l.name,''),d.created_at,COALESCE(i.total_amount,0),COALESCE(i.amount_paid,0),COALESCE(i.payment_status,'') FROM invoice_delivery_events d JOIN invoices i ON i.invoice_id=d.invoice_id LEFT JOIN customer_accounts ca ON ca.customer_id=i.customer_id LEFT JOIN locations l ON l.location_id=i.location_id WHERE d.invoice_delivery_event_id=?";
            default -> "SELECT t.customer_id,COALESCE(t.payment_id,'PAY-'||LPAD(t.transaction_id::text,6,'0')),COALESCE(ca.name,''),COALESCE(l.name,''),t.created_at,ABS(COALESCE(t.amount,0)),ABS(COALESCE(t.amount,0)),'PAID' FROM customer_account_transactions t JOIN customer_accounts ca ON ca.customer_id=t.customer_id LEFT JOIN locations l ON l.location_id=t.location_id WHERE t.transaction_id=?";};
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){if(rs.next())return new Document((Integer)rs.getObject(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant().toString(),rs.getBigDecimal(6),rs.getBigDecimal(7),rs.getString(8));}}
        throw new Rule(404,"DOCUMENT_NOT_FOUND","The sales document was not found.");
    }

    private static String render(Connection c,String type,long id,Document d,Settings s,String mode)throws SQLException{
        StringBuilder b=new StringBuilder();b.append(d.store()).append("\n").append(label(type)).append(" ").append(d.number()).append("\n");
        if("REFERENCE".equals(mode))return b.append("Thank you, ").append(d.customer()).append(".\n").append(s.contactLine()).toString();
        b.append("Customer: ").append(d.customer()).append("\nDate: ").append(d.date()).append("\n");
        if("FULL".equals(mode)){String linesSql=switch(type){case "SALE_RECEIPT"->"SELECT COALESCE(NULLIF(si.item_name,''),p.name,'Item'),si.quantity,COALESCE(si.unit_price,0) FROM sale_items si LEFT JOIN products p ON p.product_id=si.product_id WHERE si.sale_id=? ORDER BY si.sale_item_id";case "QUOTATION"->"SELECT item_name,quantity,unit_price FROM quotation_lines WHERE quotation_id=? ORDER BY sort_order,quotation_line_id";case "INVOICE"->"SELECT item_name,quantity_invoiced,unit_price FROM invoice_lines WHERE invoice_id=? ORDER BY sort_order,invoice_line_id";case "DELIVERY_BILL"->"SELECT il.item_name,dl.quantity_delivered,COALESCE(il.unit_price,0) FROM invoice_delivery_lines dl JOIN invoice_lines il ON il.invoice_line_id=dl.invoice_line_id WHERE dl.invoice_delivery_event_id=? ORDER BY dl.invoice_delivery_line_id";case "ACCOUNT_PAYMENT_RECEIPT"->"SELECT CASE WHEN a.sale_id IS NOT NULL THEN 'Sale #'||a.sale_id WHEN a.invoice_id IS NOT NULL THEN 'Invoice #'||a.invoice_id WHEN a.custom_order_id IS NOT NULL THEN 'Custom Order #'||a.custom_order_id ELSE 'Account credit' END,1,COALESCE(a.amount,0) FROM customer_account_payment_allocations a WHERE a.payment_transaction_id=? ORDER BY a.allocation_id";default->null;};if(linesSql!=null)try(PreparedStatement ps=c.prepareStatement(linesSql)){ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){while(rs.next())b.append("• ").append(rs.getString(1)).append(" x").append(rs.getInt(2)).append(" @ ").append(money(rs.getBigDecimal(3))).append("\n");}}}
        b.append("Total: ").append(money(d.total())).append("\nPaid: ").append(money(d.paid())).append("\nStatus: ").append(d.status()).append("\n").append(s.contactLine());return b.toString();
    }

    private static JsonObject payload(String phone,String template,String language,String text){JsonObject p=new JsonObject();p.addProperty("messaging_product","whatsapp");p.addProperty("to",phone);p.addProperty("type","template");JsonObject t=new JsonObject();t.addProperty("name",template);JsonObject lang=new JsonObject();lang.addProperty("code",language);t.add("language",lang);JsonArray components=new JsonArray();JsonObject body=new JsonObject();body.addProperty("type","body");JsonArray params=new JsonArray();JsonObject value=new JsonObject();value.addProperty("type","text");value.addProperty("text",text);params.add(value);body.add("parameters",params);components.add(body);t.add("components",components);p.add("template",t);return p;}
    private static long insertOutbox(Connection c,int loc,int user,Document d,String type,long id,String phone,String mode,String template,String text,BigDecimal estimate)throws SQLException{try(PreparedStatement ps=c.prepareStatement("INSERT INTO whatsapp_outbox(location_id,queued_by_user_id,customer_id,document_type,document_id,document_number,recipient_phone,message_mode,template_name,message_text,estimated_cost_usd,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,'SENDING') RETURNING whatsapp_outbox_id")){ps.setInt(1,loc);ps.setInt(2,user);ps.setObject(3,d.customerId());ps.setString(4,type);ps.setLong(5,id);ps.setString(6,d.number());ps.setString(7,phone);ps.setString(8,mode);ps.setString(9,template);ps.setString(10,text);ps.setBigDecimal(11,estimate);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getLong(1);}}}
    private static void update(Connection c,long id,String status,String meta,String error)throws SQLException{try(PreparedStatement ps=c.prepareStatement("UPDATE whatsapp_outbox SET status=?,meta_message_id=?,last_error=?,updated_at=CURRENT_TIMESTAMP WHERE whatsapp_outbox_id=?")){ps.setString(1,status);ps.setString(2,meta);ps.setString(3,error);ps.setLong(4,id);ps.executeUpdate();}}
    private static BigDecimal monthAccepted(Connection c,int loc)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(SUM(estimated_cost_usd),0) FROM whatsapp_outbox WHERE location_id=? AND status='ACCEPTED' AND created_at>=date_trunc('month',CURRENT_TIMESTAMP)")){ps.setInt(1,loc);try(ResultSet rs=ps.executeQuery()){rs.next();return rs.getBigDecimal(1);}}}
    private static void ensureSchema(Connection c)throws SQLException{try(var st=c.createStatement()){st.execute("ALTER TABLE customer_accounts ADD COLUMN IF NOT EXISTS whatsapp_opt_in BOOLEAN NOT NULL DEFAULT FALSE");st.execute("ALTER TABLE customer_accounts ADD COLUMN IF NOT EXISTS whatsapp_consent_phone TEXT");st.execute("ALTER TABLE customer_accounts ADD COLUMN IF NOT EXISTS whatsapp_consent_at TIMESTAMPTZ");st.execute("ALTER TABLE customer_accounts ADD COLUMN IF NOT EXISTS whatsapp_consent_by_user_id INTEGER REFERENCES users(user_id)");st.execute("CREATE TABLE IF NOT EXISTS whatsapp_configuration(location_id INTEGER PRIMARY KEY REFERENCES locations(location_id),enabled BOOLEAN NOT NULL DEFAULT FALSE,phone_number_id TEXT,api_version TEXT NOT NULL DEFAULT 'v23.0',message_mode TEXT NOT NULL DEFAULT 'FULL',template_language TEXT NOT NULL DEFAULT 'en_US',estimated_price_usd NUMERIC(10,4) NOT NULL DEFAULT 0.05,monthly_budget_usd NUMERIC(10,2) NOT NULL DEFAULT 5.00,template_names JSONB NOT NULL DEFAULT '{}'::jsonb,contact_line TEXT NOT NULL DEFAULT '',updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)");st.execute("CREATE TABLE IF NOT EXISTS whatsapp_outbox(whatsapp_outbox_id BIGSERIAL PRIMARY KEY,location_id INTEGER NOT NULL REFERENCES locations(location_id),queued_by_user_id INTEGER NOT NULL REFERENCES users(user_id),customer_id INTEGER REFERENCES customer_accounts(customer_id),document_type TEXT NOT NULL,document_id BIGINT NOT NULL,document_number TEXT,recipient_phone TEXT NOT NULL,message_mode TEXT NOT NULL,template_name TEXT NOT NULL,message_text TEXT NOT NULL,estimated_cost_usd NUMERIC(10,4) NOT NULL DEFAULT 0,status TEXT NOT NULL,meta_message_id TEXT,last_error TEXT,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)");}}
    static String normalizePhone(String v)throws Rule{String x=v==null?"":v.trim();if(x.startsWith("+"))x=x.substring(1);x=x.replaceAll("[^0-9]","");if(x.length()<8||x.length()>15)throw new Rule(409,"WHATSAPP_PHONE_INVALID","Enter the customer's phone in international format, for example +592...");return x;}
    private static String label(String t){return t.replace('_',' ');}
    private static String money(BigDecimal v){return (v==null?BigDecimal.ZERO:v).setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();}
    private static boolean blank(String v){return v==null||v.isBlank();}
    record Recipient(String phone,boolean optedIn,String consentedPhone){}
    record Document(Integer customerId,String number,String customer,String store,String date,BigDecimal total,BigDecimal paid,String status){}
    record Settings(boolean enabled,String phoneNumberId,String apiVersion,String messageMode,String language,BigDecimal estimatedPrice,BigDecimal monthlyBudget,String templates,String contactLine){String template(String type,String mode){try{JsonObject o=GSON.fromJson(templates,JsonObject.class);String key=type+"_"+mode;return o!=null&&o.has(key)?o.get(key).getAsString():"";}catch(Exception e){return "";}}public String contactLine(){return contactLine==null||contactLine.isBlank()?"Please contact us if you have any questions.":contactLine;}}
    static final class Rule extends Exception{final int status;final String code;Rule(int status,String code,String message){super(message);this.status=status;this.code=code;}}
}
