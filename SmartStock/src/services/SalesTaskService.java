package services;

import com.google.gson.*;
import models.CashDrawerContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

/** Store-server operations. Caller owns one transaction and idempotency claim. */
final class SalesTaskService {
    private static final Gson JSON = new GsonBuilder().serializeNulls().create();
    record Actor(int location, int user, String name, UUID device, String deviceName, Set<String> permissions) { }
    private SalesTaskService() { }

    static Map<String,Object> read(Connection c, JsonObject b, Actor a) throws Exception {
        permit(a,"MANAGE_SALES_TASKS");
        String action=text(b,"action",30,true);
        if(action.equals("CUSTOMERS")) {
            String search="%"+text(b,"search",200,false)+"%";
            return Map.of("customers",rows(c,"""
                SELECT customer_id,name,COALESCE(phone,'') AS phone,COALESCE(account_number,'') AS account_number,
                    COALESCE(email,'') AS email FROM customer_accounts
                WHERE is_active=true AND (name ILIKE ? OR phone ILIKE ? OR account_number ILIKE ? OR email ILIKE ?)
                ORDER BY name,customer_id LIMIT 100
                """,search,search,search,search));
        }
        if(action.equals("LIST")) {
            String search="%"+text(b,"search",200,false).toLowerCase(Locale.ROOT)+"%";
            return Map.of("tasks", rows(c,"""
                SELECT t.*,u.full_name AS assigned_name,s.payment_method AS sale_payment_method,
                  COALESCE((SELECT sum(CASE WHEN p.payment_action='REFUND' THEN -p.amount ELSE p.amount END)
                    FROM sales_task_payments p WHERE p.task_id=t.task_id),0) AS paid,
                  (t.due_at < now() AND t.status NOT IN ('COLLECTED','CANCELLED')) AS overdue
                FROM sales_tasks t LEFT JOIN users u ON u.user_id=t.assigned_user_id
                LEFT JOIN sales s ON s.sale_id=t.sale_id
                WHERE t.location_id=? AND (lower(t.customer_name||' '||t.phone||' '||t.instructions||' ST-'||t.task_id) LIKE ?)
                ORDER BY CASE WHEN t.status IN ('COLLECTED','CANCELLED') THEN 1 ELSE 0 END,t.due_asap DESC,t.due_at NULLS LAST,t.task_id DESC LIMIT 500
                """,a.location,search));
        }
        if(action.equals("OPTIONS")) return Map.of(
            "timezone",rows(c,"SELECT COALESCE(NULLIF(timezone,''),'America/Guyana') AS timezone FROM locations WHERE location_id=?",a.location).get(0).get("timezone"),
            "discountLimit",rows(c,"SELECT COALESCE((SELECT sale_discount_limit_percent FROM company_customization WHERE location_id=?),5) AS value",a.location).get(0).get("value"),
            "customers",rows(c,"SELECT customer_id,name,phone FROM customer_accounts WHERE is_active=true ORDER BY name LIMIT 2000"),
            "employees",rows(c,"SELECT u.user_id,COALESCE(NULLIF(u.full_name,''),u.username) AS name FROM users u JOIN user_locations ul ON ul.user_id=u.user_id WHERE ul.location_id=? ORDER BY name",a.location),
            "products",rows(c,"SELECT product_id,name,price,sku FROM products WHERE is_active=true OR sku='SMARTSTOCK-MISC' ORDER BY name LIMIT 5000"));
        if(!Set.of("DETAILS","FILE").contains(action))throw new SQLException("Unknown sales task query.");
        long id=b.get("taskId").getAsLong();
        JsonObject task=task(c,id,a.location,false);
        if(action.equals("FILE")) {
            List<Map<String,Object>> files=rows(c,"SELECT filename,encode(content,'base64') AS content FROM sales_task_files WHERE task_id=? AND file_id=?",id,b.get("fileId").getAsLong());
            if(files.isEmpty())throw new SQLException("Attachment was not found.");
            return files.get(0);
        }
        String saleMethod="";
        if(optionalInt(task,"sale_id")!=null)saleMethod=(String)rows(c,"SELECT payment_method FROM sales WHERE sale_id=?",optionalInt(task,"sale_id")).get(0).get("payment_method");
        String accountNumber="";if(optionalInt(task,"customer_id")!=null){var account=rows(c,"SELECT COALESCE(account_number,'') AS account_number FROM customer_accounts WHERE customer_id=?",optionalInt(task,"customer_id"));if(!account.isEmpty())accountNumber=String.valueOf(account.get(0).get("account_number"));}
        return Map.of("task",task,"customerAccountNumber",accountNumber,"salePaymentMethod",saleMethod,"payments",rows(c,"SELECT payment_id,amount,payment_action,payment_method,payment_reference,reason,user_name,created_at FROM sales_task_payments WHERE task_id=? ORDER BY payment_id",id),
            "events",rows(c,"SELECT event_id,action,reason,user_name,created_at FROM sales_task_events WHERE task_id=? ORDER BY event_id",id),
            "files",rows(c,"SELECT file_id,filename,sha256,octet_length(content) AS size FROM sales_task_files WHERE task_id=? ORDER BY file_id",id),
            "paid",paid(c,id));
    }

    static Map<String,Object> mutate(Connection c,JsonObject b,Actor a,LanSalesService.ApprovalConsumer approvals) throws Exception {
        permit(a,"MANAGE_SALES_TASKS");
        StorefrontService.lock(c,a.location);
        String action=text(b,"action",30,true);
        if(action.equals("CREATE")) {
            String customer=text(b,"customerName",300,true),instructions=text(b,"instructions",20000,true);
            Integer customerId=optionalInt(b,"customerId"),assigned=optionalInt(b,"assignedUserId");
            BigDecimal initialAdvance=b.has("advance")?money(b.get("advance").getAsBigDecimal()):BigDecimal.ZERO;
            if(initialAdvance.signum()<0)throw new SQLException("An advance cannot be negative.");
            validateCustomer(c,customerId);
            validateAssignee(c,assigned,a.location);
            long id;
            try(var p=c.prepareStatement("INSERT INTO sales_tasks(location_id,customer_id,customer_name,phone,instructions,due_at,due_asap,assigned_user_id,checkout_request) VALUES(?,?,?,?,?,?,?,?,?::jsonb) RETURNING task_id")) {
                bind(p,a.location,customerId,customer,text(b,"phone",100,false),instructions,due(b),asap(b),assigned,estimate(b).toString());
                try(var r=p.executeQuery()){r.next();id=r.getLong(1);}
            }
            if(initialAdvance.signum()>0) payment(c,id,b,a,false,initialAdvance);
            audit(c,id,action,"",a);
            SyncOutboxService.recordEvent(c,"SALES_TASK_CHANGED",Map.of("task_id",id,"action",action,"location_id",a.location),a.location,a.device.toString(),a.user);
            return Map.of("taskId",id);
        }
        long id=b.get("taskId").getAsLong();
        JsonObject t=task(c,id,a.location,true);
        if(!b.has("version") || b.get("version").getAsInt()!=t.get("version").getAsInt())throw new SQLException("This task changed. Refresh before trying again.");
        String status=t.get("status").getAsString(),reason=text(b,"reason",2000,false);
        if(Set.of("COLLECTED","CANCELLED").contains(status))throw new SQLException("This task is closed. Collected work must use sale returns/corrections.");
        Map<String,Object> response=new LinkedHashMap<>();response.put("taskId",id);
        switch(action) {
            case "EDIT" -> {
                if(t.get("billing_status").getAsString().equals("FINALIZED"))throw new SQLException("Reopen pricing before editing a finalized task.");
                Integer customerId=optionalInt(b,"customerId"),assigned=optionalInt(b,"assignedUserId");
                validateCustomer(c,customerId);validateAssignee(c,assigned,a.location);
                if(!Objects.equals(customerId,optionalInt(t,"customer_id")) && paid(c,id).signum()>0)throw new SQLException("Refund advances before changing the linked customer.");
                execute(c,"UPDATE sales_tasks SET customer_id=?,customer_name=?,phone=?,instructions=?,due_at=?,due_asap=?,assigned_user_id=?,checkout_request=?::jsonb WHERE task_id=?",
                    customerId,text(b,"customerName",300,true),text(b,"phone",100,false),text(b,"instructions",20000,true),due(b),asap(b),assigned,estimate(b).toString(),id);
            }
            case "START" -> {transition(status,"RECEIVED");execute(c,"UPDATE sales_tasks SET status='IN_PROGRESS' WHERE task_id=?",id);}
            case "COMPLETE" -> {if(!Set.of("RECEIVED","IN_PROGRESS").contains(status))throw new SQLException("Only received or in-progress work can be completed.");execute(c,"UPDATE sales_tasks SET status='COMPLETED' WHERE task_id=?",id);}
            case "REOPEN", "REOPEN_PRICING" -> {
                requireReason(reason);
                execute(c,"UPDATE sales_tasks SET status=?,billing_status='PROVISIONAL',final_total=NULL,finalized_quote=NULL WHERE task_id=?",action.equals("REOPEN")?"IN_PROGRESS":status,id);
            }
            case "FINALIZE" -> {
                transition(status,"COMPLETED");
                if(t.get("billing_status").getAsString().equals("FINALIZED"))throw new SQLException("Reopen pricing before revising this bill.");
                JsonObject request=checkout(t,b);
                Map<String,Object> quote=LanSalesService.preview(c,request,a.device,a.user,a.name,a.location,approvals);
                if(((BigDecimal)quote.get("total")).signum()<=0)throw new SQLException("The final bill must have a positive total.");
                request.remove("saleDiscountApprovalToken");
                for(JsonElement line:request.getAsJsonArray("lines")){line.getAsJsonObject().remove("priceApprovalToken");line.getAsJsonObject().remove("discountApprovalToken");}
                execute(c,"UPDATE sales_tasks SET checkout_request=?::jsonb,finalized_quote=?::jsonb,final_total=?,billing_status='FINALIZED' WHERE task_id=?",request.toString(),JSON.toJson(quote),quote.get("total"),id);
            }
            case "PAYMENT" -> payment(c,id,b,a,false,b.get("amount").getAsBigDecimal());
            case "REFUND" -> {requireReason(reason);payment(c,id,b,a,true,b.get("amount").getAsBigDecimal());}
            case "CANCEL" -> {
                requireReason(reason);
                if(paid(c,id).signum()!=0)throw new SQLException("Refund all advances before cancelling this task.");
                execute(c,"UPDATE sales_tasks SET status='CANCELLED' WHERE task_id=?",id);
            }
            case "ATTACH" -> {
                String filename=text(b,"filename",240,true);
                if(filename.contains("/")||filename.contains("\\")||filename.chars().anyMatch(Character::isISOControl))throw new SQLException("Invalid filename.");
                byte[] content=Base64.getMimeDecoder().decode(text(b,"content",7100000,true));
                if(content.length==0||content.length>5242880)throw new SQLException("Attachments must be between 1 byte and 5 MB.");
                var sizes=rows(c,"SELECT count(*) AS n,COALESCE(sum(octet_length(content)),0) AS bytes FROM sales_task_files WHERE task_id=?",id).get(0);
                if(((Number)sizes.get("n")).intValue()>=20||((Number)sizes.get("bytes")).longValue()+content.length>52428800)throw new SQLException("A task can hold at most 20 files and 50 MB.");
                execute(c,"INSERT INTO sales_task_files(task_id,filename,content,sha256,user_id) VALUES(?,?,?,?,?)",id,filename,content,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content)),a.user);
                reason=filename;
            }
            case "COLLECT" -> {
                transition(status,"COMPLETED");
                if(!t.get("billing_status").getAsString().equals("FINALIZED"))throw new SQLException("Finalize the bill before collection.");
                BigDecimal total=t.get("final_total").getAsBigDecimal(),advance=paid(c,id),remaining=total.subtract(advance);
                if(remaining.signum()<0)throw new SQLException("Refund the excess advance before collection.");
                JsonObject request=JsonParser.parseString(t.get("checkout_request").getAsString()).getAsJsonObject();
                String method=text(b,"method",30,true);
                request.addProperty("paymentMethod",method);request.addProperty("paymentReference",text(b,"reference",500,false));
                request.addProperty("cashCollected",b.has("cashCollected")?money(b.get("cashCollected").getAsBigDecimal()):remaining);
                if(b.has("authorization"))request.add("authorization",b.get("authorization"));
                JsonObject quote=JsonParser.parseString(t.get("finalized_quote").getAsString()).getAsJsonObject();
                Map<String,Object> sale=LanSalesService.checkoutSalesTask(c,request,a.device,a.user,a.name,a.location,approvals,quote,advance);
                response.put("sale",sale);
                if(remaining.signum()>0&&!method.equals("ACCOUNT"))payment(c,id,b,a,false,remaining);
                execute(c,"UPDATE sales_tasks SET status='COLLECTED',sale_id=? WHERE task_id=?",sale.get("saleId"),id);
            }
            default -> throw new SQLException("Unknown sales task action.");
        }
        execute(c,"UPDATE sales_tasks SET version=version+1,updated_at=now() WHERE task_id=?",id);
        audit(c,id,action,reason,a);
        SyncOutboxService.recordEvent(c,"SALES_TASK_CHANGED",Map.of("task_id",id,"action",action,"location_id",a.location),a.location,a.device.toString(),a.user);
        return response;
    }

    private static JsonObject checkout(JsonObject task,JsonObject body) throws SQLException {
        JsonObject r=body.has("checkout")?body.getAsJsonObject("checkout").deepCopy():JsonParser.parseString(task.get("checkout_request").getAsString()).getAsJsonObject();
        r.addProperty("paymentMethod","CARD");r.addProperty("paymentReference","SALES TASK PRICE PREVIEW");r.addProperty("cashCollected",0);
        if(!r.has("lines")||r.getAsJsonArray("lines").isEmpty())throw new SQLException("Add final charge lines before finalizing.");
        for(JsonElement line:r.getAsJsonArray("lines")) {
            JsonObject l=line.getAsJsonObject();
            if(!l.has("quantity")||l.get("quantity").isJsonNull()||!l.has("unitPrice")||l.get("unitPrice").isJsonNull())throw new SQLException("Every final charge needs a quantity and unit price.");
        }
        r.add("customerId",task.get("customer_id"));r.addProperty("loyaltyPoints",0);
        return r;
    }
    private static JsonObject estimate(JsonObject b) throws SQLException {
        JsonObject r=b.has("checkout")?b.getAsJsonObject("checkout").deepCopy():new JsonObject();
        if(!r.has("lines"))r.add("lines",new JsonArray());
        if(!r.get("lines").isJsonArray()||r.getAsJsonArray("lines").size()>200||r.toString().length()>200000)throw new SQLException("Too many provisional charge lines.");
        r.remove("saleDiscountApprovalToken");
        for(JsonElement line:r.getAsJsonArray("lines")){line.getAsJsonObject().remove("priceApprovalToken");line.getAsJsonObject().remove("discountApprovalToken");}
        return r;
    }
    private static void payment(Connection c,long id,JsonObject b,Actor a,boolean refund,BigDecimal raw) throws Exception {
        permit(a,refund?"PROCESS_RETURNS":"MAKE_SALE");
        BigDecimal amount=money(raw);
        if(amount.signum()<=0)throw new SQLException("Payment amount must be greater than zero.");
        if(refund&&amount.compareTo(paid(c,id))>0)throw new SQLException("Refund exceeds the advance held.");
        String method=text(b,"method",30,true);
        if(!Set.of("CASH","CARD","CHEQUE","MMG","BANK_TRANSFER").contains(method))throw new SQLException("Use a supported tender method for advances and refunds.");
        String reference=text(b,"reference",500,false);
        if(!method.equals("CASH")&&reference.isBlank())throw new SQLException("A payment reference is required.");
        CashDrawerContext drawer=LanCustomOrderWorkflowService.cashDrawer(c,method,a.location,a.device.toString());
        execute(c,"INSERT INTO sales_task_payments(task_id,location_id,customer_id,amount,payment_action,payment_method,payment_reference,reason,user_id,user_name,device_id,device_name,cash_drawer_id,cash_drawer_name,cash_drawer_session_id) SELECT task_id,location_id,customer_id,?,?,?,?,?,?,?,?,?,?,?,? FROM sales_tasks WHERE task_id=?",
            amount,refund?"REFUND":"PAYMENT",method,reference,text(b,"reason",2000,false),a.user,a.name,a.device.toString(),a.deviceName,drawer.cashDrawerId(),drawer.drawerName(),drawer.sessionId(),id);
    }
    static BigDecimal money(BigDecimal value) throws SQLException {
        if(value==null||value.scale()>2||value.abs().compareTo(new BigDecimal("9999999999.99"))>0)throw new SQLException("Enter a valid amount with at most two decimal places.");
        return value.setScale(2,RoundingMode.UNNECESSARY);
    }
    static Map<String,Object> customerPaymentReceipt(Connection c,int customer,long payment)throws SQLException {
        var result=rows(c,"""
            SELECT p.*,t.customer_name,t.final_total,t.billing_status,
                extract(epoch FROM t.created_at)*1000 AS task_epoch,extract(epoch FROM p.created_at)*1000 AS payment_epoch,
                ca.account_number,ca.current_balance,l.name AS store_name,
                (SELECT COALESCE(sum(CASE WHEN x.payment_action='REFUND' THEN -x.amount ELSE x.amount END),0)
                    FROM sales_task_payments x WHERE x.task_id=p.task_id) AS paid
            FROM sales_task_payments p JOIN sales_tasks t ON t.task_id=p.task_id
            JOIN customer_accounts ca ON ca.customer_id=p.customer_id JOIN locations l ON l.location_id=p.location_id
            WHERE p.payment_id=? AND p.customer_id=?
            """,payment,customer);
        if(result.isEmpty())throw new SQLException("Task payment was not found for this customer.");
        var p=result.get(0);BigDecimal amount=(BigDecimal)p.get("amount");if("REFUND".equals(p.get("payment_action")))amount=amount.negate();
        Map<String,Object> allocation=new LinkedHashMap<>();allocation.put("targetLabel","Sales Task ST-"+p.get("task_id"));allocation.put("appliedAmount",amount);allocation.put("chargeTotal",p.get("final_total"));allocation.put("chargePaid",p.get("paid"));allocation.put("paymentStatus","PROVISIONAL".equals(p.get("billing_status"))?"PRICE_PENDING":"FINALIZED");allocation.put("chargeDateEpochMillis",((Number)p.get("task_epoch")).longValue());
        Map<String,Object> receipt=new LinkedHashMap<>();receipt.put("transactionId",-payment);receipt.put("locationId",p.get("location_id"));receipt.put("paymentId","ST-"+p.get("task_id")+"/P-"+payment);
        receipt.put("paymentTimeEpochMillis",((Number)p.get("payment_epoch")).longValue());
        receipt.put("storeName",p.get("store_name"));receipt.put("userName",p.get("user_name"));receipt.put("customerName",p.get("customer_name"));receipt.put("accountNumber",p.get("account_number"));receipt.put("customerEmail","");receipt.put("paymentMethod",p.get("payment_method"));receipt.put("paymentReference",p.get("payment_reference"));receipt.put("deviceName",p.get("device_name"));receipt.put("cashDrawerName",p.get("cash_drawer_name"));receipt.put("paymentAmount",amount);receipt.put("accountBalanceAfter",p.get("current_balance"));receipt.put("allocations",List.of(allocation));return receipt;
    }
    static String salePaymentSummary(Connection c,int sale)throws SQLException {
        var tasks=rows(c,"SELECT task_id FROM sales_tasks WHERE sale_id=?",sale);if(tasks.isEmpty())return "";
        long id=((Number)tasks.get(0).get("task_id")).longValue();StringBuilder text=new StringBuilder("Sales Task ST-").append(id).append("\nNet tender including advances:\n");
        for(var row:rows(c,"SELECT payment_method,sum(CASE WHEN payment_action='REFUND' THEN -amount ELSE amount END) AS amount FROM sales_task_payments WHERE task_id=? GROUP BY payment_method ORDER BY payment_method",id))text.append(row.get("payment_method")).append(": ").append(row.get("amount")).append('\n');
        return text.toString();
    }
    static void transition(String actual,String expected) throws SQLException {if(!expected.equals(actual))throw new SQLException("This action requires "+expected.toLowerCase(Locale.ROOT).replace('_',' ')+" work.");}
    private static void requireReason(String reason)throws SQLException{if(reason.isBlank())throw new SQLException("A reason is required.");}
    private static void permit(Actor a,String permission)throws SQLException{if(!a.permissions.contains(permission))throw new SQLException("Permission required: "+permission);}
    private static void validateCustomer(Connection c,Integer id)throws SQLException{if(id!=null&&rows(c,"SELECT customer_id FROM customer_accounts WHERE customer_id=? AND is_active=true",id).isEmpty())throw new SQLException("Select an active customer account.");}
    private static void validateAssignee(Connection c,Integer id,int location)throws SQLException{if(id!=null&&rows(c,"SELECT user_id FROM user_locations WHERE user_id=? AND location_id=?",id,location).isEmpty())throw new SQLException("Select an employee assigned to this store.");}
    private static boolean asap(JsonObject b){return b.has("dueAsap")&&!b.get("dueAsap").isJsonNull()&&b.get("dueAsap").getAsBoolean();}
    private static Object due(JsonObject b)throws SQLException{if(asap(b))return null;String v=text(b,"dueAt",60,false);try{return v.isBlank()?null:OffsetDateTime.parse(v);}catch(Exception e){throw new SQLException("Enter a valid due date and time.");}}
    private static Integer optionalInt(JsonObject b,String key){return !b.has(key)||b.get(key).isJsonNull()?null:b.get(key).getAsInt();}
    private static String text(JsonObject b,String key,int max,boolean required)throws SQLException{String v=!b.has(key)||b.get(key).isJsonNull()?"":b.get(key).getAsString().trim();if(v.length()>max||(required&&v.isBlank()))throw new SQLException("Invalid "+key+".");return v;}
    private static BigDecimal paid(Connection c,long id)throws SQLException{return (BigDecimal)rows(c,"SELECT COALESCE(sum(CASE WHEN payment_action='REFUND' THEN -amount ELSE amount END),0) AS paid FROM sales_task_payments WHERE task_id=?",id).get(0).get("paid");}
    private static JsonObject task(Connection c,long id,int location,boolean lock)throws SQLException{var r=rows(c,"SELECT * FROM sales_tasks WHERE task_id=? AND location_id=?"+(lock?" FOR UPDATE":""),id,location);if(r.isEmpty())throw new SQLException("Task was not found at this store.");return JSON.toJsonTree(r.get(0)).getAsJsonObject();}
    private static void audit(Connection c,long id,String action,String reason,Actor a)throws SQLException{execute(c,"INSERT INTO sales_task_events(task_id,action,reason,snapshot,user_id,user_name,device_id) SELECT task_id,?,?,to_jsonb(t),?,?,? FROM sales_tasks t WHERE task_id=?",action,reason,a.user,a.name,a.device.toString(),id);}
    private static void bind(PreparedStatement p,Object... values)throws SQLException{for(int i=0;i<values.length;i++)p.setObject(i+1,values[i]);}
    private static void execute(Connection c,String sql,Object... values)throws SQLException{try(var p=c.prepareStatement(sql)){bind(p,values);p.executeUpdate();}}
    private static List<Map<String,Object>> rows(Connection c,String sql,Object... values)throws SQLException{
        List<Map<String,Object>> out=new ArrayList<>();try(var p=c.prepareStatement(sql)){bind(p,values);try(var r=p.executeQuery()){var md=r.getMetaData();while(r.next()){Map<String,Object> row=new LinkedHashMap<>();for(int i=1;i<=md.getColumnCount();i++){Object v=r.getObject(i);if(v instanceof Timestamp||v instanceof org.postgresql.util.PGobject)v=v.toString();row.put(md.getColumnLabel(i),v);}out.add(row);}}}return out;
    }
}
