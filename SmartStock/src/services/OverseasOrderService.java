package services;

import com.google.gson.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.net.URI;
import java.io.*;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import models.CashDrawerContext;

/** Store-owned overseas quotes. Caller owns the transaction and supplies authenticated identity. */
public final class OverseasOrderService {
    private static final Gson JSON = LanJson.create();
    public record Actor(int userId, String name, int locationId, String deviceId, String deviceName,
                        Set<String> permissions, boolean web) { }
    private OverseasOrderService() { }

    public static Set<String> permissions(Connection c,int user)throws SQLException {
        Set<String> permissions=new HashSet<>();
        try(var p=c.prepareStatement("SELECT upper(p.permission_key) FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? UNION SELECT upper(rp.permission_key) FROM users u JOIN role_mobile_permissions rp ON rp.role_id=u.role_id WHERE u.user_id=?")) {
            p.setInt(1,user);p.setInt(2,user);try(var r=p.executeQuery()){while(r.next())permissions.add(r.getString(1));}
        }return Set.copyOf(permissions);
    }

    public static boolean canView(Set<String> permissions) {
        return permissions.stream().anyMatch(p -> p.startsWith("OVERSEAS_ORDERS_"));
    }
    /** Protect the shared image endpoint as well as the dedicated overseas endpoint. */
    public static void authorizeImageRead(Connection c,int user,int location,String reference)throws SQLException {
        boolean overseas;
        try(var p=c.prepareStatement("SELECT category='OVERSEAS_ORDER' FROM image_assets WHERE asset_id=?")) {
            p.setObject(1,ImageAssetReference.assetId(reference));try(var r=p.executeQuery()){overseas=r.next()&&r.getBoolean(1);}
        }
        if(!overseas)return;
        if(!canView(permissions(c,user)))throw new SecurityException("Overseas order access is required to view this screenshot.");
        try(var p=c.prepareStatement("SELECT 1 FROM overseas_order_images i JOIN overseas_orders o USING(overseas_order_id) WHERE i.asset_reference=? AND o.location_id=?")) {
            p.setString(1,reference);p.setInt(2,location);try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("Screenshot not found in this store.");}
        }
    }
    private static void permit(Actor a, String suffix) {
        if (!a.permissions.contains("OVERSEAS_ORDERS_" + suffix)) throw new SecurityException("Overseas order permission required: " + suffix);
    }
    public static JsonObject execute(Connection c, Actor actor, JsonObject b) throws Exception {
        if (c.getAutoCommit()) throw new IllegalStateException("Overseas operations require a transaction.");
        if (!canView(actor.permissions)) throw new SecurityException("You cannot access overseas orders.");
        String action = text(b,"action",30,true);
        if (action.equals("LIST")) return list(c,actor,b);
        if (action.equals("CUSTOMERS")) {
            permit(actor,"QUOTE"); JsonObject out = new JsonObject();
            out.add("customers",JSON.toJsonTree(ServerCustomOrderDataService.searchCustomers(c,text(b,"search",200,false)))); return out;
        }
        if (action.equals("CUSTOMER_PHONE")) {
            permit(actor,"QUOTE");return customerPhone(c,text(b,"phone",100,false));
        }
        if(action.equals("SELLERS")) {
            permit(actor,"QUOTE");JsonArray sellers=new JsonArray();
            try(var p=c.prepareStatement("SELECT min(l.seller) FROM overseas_order_lines l JOIN overseas_orders o USING(overseas_order_id) WHERE o.location_id=? AND l.seller<>'' GROUP BY lower(l.seller) ORDER BY lower(l.seller) LIMIT 500")) {
                p.setInt(1,actor.locationId);try(var r=p.executeQuery()){while(r.next())sellers.add(r.getString(1));}
            }JsonObject out=new JsonObject();out.add("sellers",sellers);return out;
        }
        if (action.equals("DETAIL")) return detail(c,actor,UUID.fromString(text(b,"id",36,true)));
        if (action.equals("IMAGE")) {
            UUID id=UUID.fromString(text(b,"id",36,true)); lock(c,actor,id,false);
            String ref=text(b,"reference",100,true); requireImage(c,id,ref);
            var image=ServerImageAssetService.load(c,ref); JsonObject out=new JsonObject();
            out.addProperty("data","data:"+image.contentType()+";base64,"+Base64.getEncoder().encodeToString(image.bytes()));return out;
        }
        String permission = switch(action) {
            case "SAVE","ISSUE","UPLOAD" -> "QUOTE";
            case "PAYMENT" -> "PAYMENT";
            case "SHIPMENT","TRACK" -> "SHIP";
            case "COLLECT" -> "COLLECT";
            case "REFUND","CANCEL" -> "ADJUST";
            default -> throw new IllegalArgumentException("Unknown overseas order action.");
        };
        permit(actor,permission);
        if (actor.web && Set.of("PAYMENT","COLLECT","REFUND","CANCEL").contains(action))
            throw new SecurityException("Payments, collection and refunds must be recorded at a store register.");
        UUID requestId=UUID.fromString(text(b,"requestId",36,true));
        String hash=LanSecurity.sha256(JSON.toJson(b));
        try(var p=c.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))")) {
            p.setString(1,"overseas:"+actor.userId+":"+actor.locationId+":"+requestId);p.execute();
        }
        try(var p=c.prepareStatement("SELECT request_hash,response::text FROM overseas_order_requests WHERE actor_id=? AND location_id=? AND request_id=?")) {
            p.setInt(1,actor.userId);p.setInt(2,actor.locationId);p.setObject(3,requestId);
            try(var r=p.executeQuery()) { if(r.next()) {
                if(!hash.equals(r.getString(1))) throw new IllegalArgumentException("This request identifier was already used for different values.");
                JsonObject cached=JsonParser.parseString(r.getString(2)).getAsJsonObject();
                redact(cached,actor);return cached;
            }}
        }
        UUID id=b.has("id")&&!b.get("id").isJsonNull()&&!b.get("id").getAsString().isBlank()?UUID.fromString(b.get("id").getAsString()):null;
        JsonObject before=id==null?null:lock(c,actor,id,true);
        if(before!=null)before.add("lines",lines(c,id));
        if(before!=null && before.get("revision").getAsLong()!=revision(b))
            throw new IllegalArgumentException("This order changed. Reload it before saving.");
        if(before!=null && Set.of("COMPLETED","CANCELLED").contains(before.get("state").getAsString())) {
            if(before.get("state").getAsString().equals("CANCELLED"))throw new IllegalArgumentException("This order is cancelled.");
            if(!Set.of("REFUND","COLLECT").contains(action))throw new IllegalArgumentException("This order is complete. Use an authorized collection correction or refund.");
            permit(actor,"ADJUST");text(b,"reason",2000,true);
        }
        if(action.equals("SAVE")) id=save(c,actor,id,b,before);
        else {
            if(id==null) throw new IllegalArgumentException("Save the draft first.");
            switch(action) {
                case "ISSUE" -> issue(c,actor,id,before);
                case "UPLOAD" -> { return uploadResult(c,actor,b,id,before,requestId,hash); }
                case "PAYMENT" -> pay(c,actor,b,before);
                case "SHIPMENT" -> shipment(c,actor,id,b,before,false);
                case "TRACK" -> {
                    String stage=text(b,"stage",30,true);if(!Set.of("ORDERED","ARRIVED").contains(stage))throw new IllegalArgumentException("Choose Ordered or Arrived.");
                    JsonArray patches=new JsonArray();Set<String> selected=new HashSet<>();for(JsonElement line:b.getAsJsonArray("lineIds"))selected.add(line.getAsString());
                    for(JsonElement element:lines(c,id)){JsonObject l=element.getAsJsonObject();if(!selected.remove(l.get("line_id").getAsString()))continue;
                        JsonObject patch=new JsonObject();patch.add("line_id",l.get("line_id"));patch.add("purchased",l.get("quantity"));
                        if(stage.equals("ARRIVED")){patch.add("shipped",l.get("quantity"));patch.add("arrived",l.get("quantity"));}patches.add(patch);
                    }
                    if(!selected.isEmpty()||patches.isEmpty())throw new IllegalArgumentException("Select items belonging to this order.");
                    JsonObject update=b.deepCopy();update.add("lines",patches);shipment(c,actor,id,update,before,false);
                }
                case "COLLECT" -> {
                    if(b.has("amount")&&b.get("amount").getAsBigDecimal().signum()<0)throw new IllegalArgumentException("Payment cannot be negative.");
                    if(b.has("amount")&&b.get("amount").getAsBigDecimal().signum()>0) {permit(actor,"PAYMENT");pay(c,actor,b,before);}
                    shipment(c,actor,id,b,lock(c,actor,id,true),true);
                }
                case "REFUND" -> refund(c,actor,b,before);
                case "CANCEL" -> cancel(c,actor,id,b,before);
                default -> throw new IllegalArgumentException("Unsupported action.");
            }
        }
        if(Set.of("PAYMENT","SAVE").contains(action)&&!lock(c,actor,id,false).get("custom_order_id").isJsonNull()) {
            JsonObject active=lock(c,actor,id,false);
            if(finance(c,active).get("balance_due").getAsBigDecimal().signum()==0&&quantities(lines(c,id)).stream().allMatch(q->q.collected()==q.quantity())) {
                try(var p=c.prepareStatement("UPDATE overseas_orders SET state='COMPLETED' WHERE overseas_order_id=?")){p.setObject(1,id);p.executeUpdate();}
                try(var p=c.prepareStatement("UPDATE custom_orders SET status='DELIVERED',delivered_at=COALESCE(delivered_at,now()) WHERE custom_order_id=?")){p.setLong(1,active.get("custom_order_id").getAsLong());p.executeUpdate();}
            }
        }
        try(var p=c.prepareStatement("UPDATE overseas_orders SET revision=revision+1,updated_at=now() WHERE overseas_order_id=?")) {p.setObject(1,id);p.executeUpdate();}
        JsonObject after=lock(c,actor,id,false);
        if(!after.get("custom_order_id").isJsonNull())after.add("financials",finance(c,after));
        event(c,actor,id,action,text(b,"reason",2000,false),before,after);
        if(action.equals("PAYMENT")||action.equals("REFUND")||(action.equals("COLLECT")&&b.has("amount")&&b.get("amount").getAsBigDecimal().signum()>0)) {
            JsonObject snapshot=detail(c,actor,id).getAsJsonObject("order");
            for(JsonElement l:snapshot.getAsJsonArray("lines")){l.getAsJsonObject().remove("usd_cost");l.getAsJsonObject().remove("exchange_rate");}
            try(var p=c.prepareStatement("UPDATE custom_order_payments SET overseas_receipt=?::jsonb WHERE custom_order_payment_id=(SELECT MAX(custom_order_payment_id) FROM custom_order_payments WHERE custom_order_id=?) AND overseas_receipt IS NULL")) {
                p.setString(1,JSON.toJson(snapshot));p.setLong(2,after.get("custom_order_id").getAsLong());p.executeUpdate();
            }
        }
        JsonObject response=detail(c,actor,id);remember(c,actor,requestId,hash,response);
        SyncOutboxService.recordEvent(c,"OVERSEAS_ORDER_CHANGED",Map.of("overseas_order_id",id.toString(),"location_id",actor.locationId));
        return response;
    }

    public static JsonObject customerPhone(Connection c,String supplied)throws SQLException {
        JsonObject out=new JsonObject();JsonArray customers=new JsonArray();out.add("customers",customers);
        String phone;
        try{phone=utils.CustomerPhoneNumber.normalize(supplied);if(phone.isBlank())throw new IllegalArgumentException();}
        catch(IllegalArgumentException e){out.addProperty("validPhone",false);out.addProperty("reserved",false);return out;}
        out.addProperty("validPhone",true);out.addProperty("phone",phone);
        try(var p=c.prepareStatement("SELECT EXISTS(SELECT 1 FROM customer_phone_ownership WHERE phone_key=public.customer_phone_key(?))")) {
            p.setString(1,phone);try(var r=p.executeQuery()){r.next();out.addProperty("reserved",r.getBoolean(1));}
        }
        try(var p=c.prepareStatement("""
            SELECT customer_id,name,COALESCE(phone,''),COALESCE(account_number,''),is_active,
              COALESCE(email,''),require_charge_authorization,custom_order_discount_enabled,custom_order_discount_percent,custom_order_deposit_exempt
            FROM customer_accounts WHERE public.customer_phone_key(phone)=public.customer_phone_key(?)
              OR customer_id=(SELECT customer_id FROM customer_phone_ownership WHERE phone_key=public.customer_phone_key(?))
            ORDER BY customer_id LIMIT 100
            """)) {
            p.setString(1,phone);p.setString(2,phone);try(var r=p.executeQuery()){while(r.next()){
                JsonObject customer=new JsonObject();customer.addProperty("customerId",r.getInt(1));customer.addProperty("name",r.getString(2));
                customer.addProperty("phone",r.getString(3));customer.addProperty("accountNumber",r.getString(4));customer.addProperty("active",r.getBoolean(5));
                customer.addProperty("email",r.getString(6));customer.addProperty("requireChargeAuthorization",r.getBoolean(7));
                customer.addProperty("customOrderDiscountEnabled",r.getBoolean(8));customer.addProperty("customOrderDiscountPercent",r.getBigDecimal(9));customer.addProperty("customOrderDepositExempt",r.getBoolean(10));customers.add(customer);
            }}
        }return out;
    }

    private static JsonObject list(Connection c,Actor a,JsonObject b)throws SQLException {
        String search=text(b,"search",200,false),filter=text(b,"filter",30,false);JsonArray rows=new JsonArray();
        try(var p=c.prepareStatement("""
            SELECT o.overseas_order_id,o.customer_name,o.customer_phone,o.state,o.total,o.revision,
                COALESCE(f.amount_paid,0),COALESCE(f.balance_due,o.total),COALESCE(f.order_number,'Draft'),o.expected_date,
                (SELECT name FROM locations WHERE location_id=o.location_id),
                CASE WHEN o.state='DRAFT' THEN 'DRAFT' WHEN o.state='CANCELLED' THEN 'CANCELLED' WHEN o.state='COMPLETED' THEN 'COLLECTED'
                  WHEN (SELECT bool_and(l.arrived=l.quantity) FROM overseas_order_lines l WHERE l.overseas_order_id=o.overseas_order_id) THEN 'ARRIVED'
                  WHEN EXISTS(SELECT 1 FROM overseas_order_lines l WHERE l.overseas_order_id=o.overseas_order_id AND l.arrived>0) THEN 'PARTIALLY_ARRIVED'
                  WHEN (SELECT bool_and(l.purchased=l.quantity) FROM overseas_order_lines l WHERE l.overseas_order_id=o.overseas_order_id) THEN 'ORDERED'
                  WHEN EXISTS(SELECT 1 FROM overseas_order_lines l WHERE l.overseas_order_id=o.overseas_order_id AND l.purchased>0) THEN 'PARTIALLY_ORDERED'
                  WHEN COALESCE(f.amount_paid,0)>0 OR o.total=0 THEN 'PROCESSING' ELSE 'WAITING_FOR_PAYMENT' END
            FROM overseas_orders o LEFT JOIN custom_orders f ON f.custom_order_id=o.custom_order_id
            WHERE o.location_id=? AND (?='' OR o.customer_name ILIKE ? OR o.customer_phone ILIKE ? OR f.order_number ILIKE ?)
            AND (?='' OR ?='ALL' OR (?='UNPAID' AND o.state='ISSUED' AND COALESCE(f.amount_paid,0)=0)
              OR (?='ACTIVE' AND o.state IN ('DRAFT','ISSUED')) OR (?='COMPLETED' AND o.state='COMPLETED')
              OR (?='ARRIVED' AND o.state='ISSUED' AND EXISTS(SELECT 1 FROM overseas_order_lines l WHERE l.overseas_order_id=o.overseas_order_id AND l.arrived>l.collected)))
            ORDER BY o.updated_at DESC LIMIT 250
            """)) {
            p.setInt(1,a.locationId);p.setString(2,search);for(int i=3;i<=5;i++)p.setString(i,"%"+search+"%");for(int i=6;i<=11;i++)p.setString(i,filter);
            try(var r=p.executeQuery()){while(r.next()){JsonObject row=new JsonObject();String[]keys={"id","customerName","customerPhone","state","total","revision","paid","balance","number","expectedDate","storeName","tracking"};for(int i=0;i<keys.length;i++)row.addProperty(keys[i],r.getString(i+1));rows.add(row);}}
        }
        JsonObject out=new JsonObject();out.add("rows",rows);out.add("permissions",JSON.toJsonTree(a.permissions));return out;
    }
    private static JsonObject lock(Connection c,Actor a,UUID id,boolean update)throws SQLException {
        try(var p=c.prepareStatement("SELECT row_to_json(o)::text FROM overseas_orders o WHERE overseas_order_id=? AND location_id=?"+(update?" FOR UPDATE":""))) {
            p.setObject(1,id);p.setInt(2,a.locationId);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Order not found in this store.");return JsonParser.parseString(r.getString(1)).getAsJsonObject();}
        }
    }
    private static JsonObject detail(Connection c,Actor a,UUID id)throws SQLException {
        JsonObject order=lock(c,a,id,true);order.add("lines",lines(c,id));
        try(var p=c.prepareStatement("SELECT name FROM locations WHERE location_id=?")){p.setInt(1,a.locationId);try(var r=p.executeQuery()){r.next();order.addProperty("store_name",r.getString(1));}}
        if(!order.get("custom_order_id").isJsonNull())try(var p=c.prepareStatement("SELECT order_number,total_amount,amount_paid,balance_due FROM custom_orders WHERE custom_order_id=?")) {
            p.setLong(1,order.get("custom_order_id").getAsLong());try(var r=p.executeQuery()){r.next();order.addProperty("number",r.getString(1));order.addProperty("paid",r.getBigDecimal(3));order.addProperty("balance",r.getBigDecimal(4));}
        } else {order.addProperty("number","Draft");order.addProperty("paid",BigDecimal.ZERO);order.add("balance",order.get("total"));}
        for(JsonElement element:order.getAsJsonArray("lines")){JsonObject l=element.getAsJsonObject();l.addProperty("tracking",OverseasOrderRules.tracking(order.get("state").getAsString(),order.get("paid").getAsBigDecimal(),order.get("total").getAsBigDecimal(),quantity(l)));}
        JsonArray payments=new JsonArray();
        if(!order.get("custom_order_id").isJsonNull())try(var p=c.prepareStatement("SELECT row_to_json(p)::text FROM custom_order_payments p WHERE custom_order_id=? ORDER BY created_at,custom_order_payment_id")) {
            p.setLong(1,order.get("custom_order_id").getAsLong());try(var r=p.executeQuery()){while(r.next())payments.add(JsonParser.parseString(r.getString(1)));}
        }
        JsonArray events=new JsonArray();try(var p=c.prepareStatement("SELECT action,actor_name,reason,created_at::text FROM overseas_order_events WHERE overseas_order_id=? ORDER BY created_at DESC LIMIT 100")) {
            p.setObject(1,id);try(var r=p.executeQuery()){while(r.next()){JsonObject e=new JsonObject();e.addProperty("action",r.getString(1));e.addProperty("actor",r.getString(2));e.addProperty("reason",r.getString(3));e.addProperty("at",r.getString(4));events.add(e);}}
        }
        if(!a.permissions.contains("OVERSEAS_ORDERS_QUOTE"))for(JsonElement line:order.getAsJsonArray("lines")){line.getAsJsonObject().remove("usd_cost");line.getAsJsonObject().remove("exchange_rate");}
        JsonObject out=new JsonObject();out.add("order",order);out.add("payments",payments);out.add("events",events);out.add("permissions",JSON.toJsonTree(a.permissions));return out;
    }
    private static JsonArray lines(Connection c,UUID id)throws SQLException {
        JsonArray lines=new JsonArray();try(var p=c.prepareStatement("SELECT row_to_json(l)::text FROM overseas_order_lines l WHERE overseas_order_id=? ORDER BY sort_order,line_id")) {
            p.setObject(1,id);try(var r=p.executeQuery()){while(r.next())lines.add(JsonParser.parseString(r.getString(1)));}
        }return lines;
    }
    private static UUID save(Connection c,Actor a,UUID id,JsonObject b,JsonObject before)throws Exception {
        JsonObject input=b.getAsJsonObject("order");String name=text(input,"customer_name",300,true),phone=utils.CustomerPhoneNumber.normalize(text(input,"customer_phone",100,true));
        if(phone.isBlank())throw new IllegalArgumentException("Customer phone is required.");
        String date=text(input,"expected_date",10,false);if(!date.isBlank())LocalDate.parse(date);
        BigDecimal extraShipping=optionalMoney(input,"shipping");
        BigDecimal shipping=OverseasOrderRules.money(extraShipping==null?BigDecimal.ZERO:extraShipping);
        JsonArray incoming=input.getAsJsonArray("lines");if(incoming==null||incoming.isEmpty()||incoming.size()>200)throw new IllegalArgumentException("Add between 1 and 200 items.");
        List<BigDecimal> totals=new ArrayList<>();Set<UUID> ids=new HashSet<>();
        for(JsonElement value:incoming) {
            JsonObject line=value.getAsJsonObject();text(line,"description",1000,true);text(line,"options",1000,false);text(line,"seller",200,false);
            int qty=count(line,"quantity");new OverseasOrderRules.Quantities(qty,0,0,0,0);
            totals.add(OverseasOrderRules.money(line.get("unit_price").getAsBigDecimal()).multiply(BigDecimal.valueOf(qty)));
            String url=text(line,"product_url",3000,false);if(!url.isBlank()) {URI uri=URI.create(url);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalArgumentException("Product links must be HTTP or HTTPS links.");}
            for(String key:List.of("usd_cost","exchange_rate"))if(line.has(key)&&!line.get(key).isJsonNull()&&!line.get(key).getAsString().isBlank()) {
                BigDecimal n=line.get(key).getAsBigDecimal();if(n.signum()<0||(key.equals("exchange_rate")&&n.signum()==0)||n.compareTo(new BigDecimal("99999999"))>0)throw new IllegalArgumentException("Invalid USD cost or exchange rate.");
            }
            if(line.has("line_id")&&!line.get("line_id").isJsonNull()&&!line.get("line_id").getAsString().isBlank()&&!ids.add(UUID.fromString(line.get("line_id").getAsString())))throw new IllegalArgumentException("Duplicate item identifier.");
        }
        BigDecimal total=OverseasOrderRules.total(totals,shipping);
        Integer customer=input.has("customer_id")&&!input.get("customer_id").isJsonNull()?input.get("customer_id").getAsInt():null;
        if(customer!=null)try(var p=c.prepareStatement("SELECT 1 FROM customer_accounts WHERE customer_id=? AND is_active")){p.setInt(1,customer);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Select an active customer.");}}
        CustomerPhoneOwnership.requireAvailable(c,phone,customer);
        if(id==null){id=UUID.randomUUID();try(var p=c.prepareStatement("INSERT INTO overseas_orders(overseas_order_id,location_id,customer_name,customer_phone,created_by) VALUES(?,?,?,?,?)")){p.setObject(1,id);p.setInt(2,a.locationId);p.setString(3,name);p.setString(4,phone);p.setInt(5,a.userId);p.executeUpdate();}}
        JsonArray old=lines(c,id);boolean issued=before!=null&&!before.get("custom_order_id").isJsonNull();
        if(issued) {
            JsonObject finance=finance(c,before);
            boolean paid=finance.get("amount_paid").getAsBigDecimal().signum()>0;
            boolean progress=false;for(JsonElement l:old)if(l.getAsJsonObject().get("purchased").getAsInt()>0)progress=true;
            if(paid||progress){permit(a,"ADJUST");text(b,"reason",2000,true);}
            if(total.compareTo(finance.get("amount_paid").getAsBigDecimal())<0)throw new IllegalArgumentException("The new total cannot be below payments received. Refund first.");
            if(!Objects.equals(customer,nullableInt(before,"customer_id"))||!name.equals(before.get("customer_name").getAsString())||!phone.equals(before.get("customer_phone").getAsString()))throw new IllegalArgumentException("Customer identity cannot change after quote issue.");
            updateFinanceTotal(c,a,before,total,text(b,"reason",2000,false));
        }
        Set<UUID> existing=new HashSet<>();for(JsonElement l:old){JsonObject row=l.getAsJsonObject();UUID lid=UUID.fromString(row.get("line_id").getAsString());existing.add(lid);if(!ids.contains(lid)&&row.get("purchased").getAsInt()>0)throw new IllegalArgumentException("Purchased items cannot be removed.");}
        for(UUID lid:ids)if(!existing.contains(lid))throw new IllegalArgumentException("Item identifier does not belong to this order.");
        try(var p=c.prepareStatement("UPDATE overseas_orders SET customer_id=?,customer_name=?,customer_phone=?,expected_date=?::date,notes=?,shipping=?,total=? WHERE overseas_order_id=?")) {
            p.setObject(1,customer);p.setString(2,name);p.setString(3,phone);p.setString(4,date.isBlank()?null:date);p.setString(5,text(input,"notes",5000,false));p.setBigDecimal(6,shipping);p.setBigDecimal(7,total);p.setObject(8,id);p.executeUpdate();
        }
        for(UUID lid:existing)if(!ids.contains(lid))try(var p=c.prepareStatement("DELETE FROM overseas_order_lines WHERE line_id=?")){p.setObject(1,lid);p.executeUpdate();}
        int sort=0;for(JsonElement value:incoming) {
            JsonObject l=value.getAsJsonObject();UUID lid=l.has("line_id")&&!l.get("line_id").isJsonNull()&&!l.get("line_id").getAsString().isBlank()?UUID.fromString(l.get("line_id").getAsString()):UUID.randomUUID();
            String screenshot=text(l,"screenshot",100,false);if(!screenshot.isBlank())requireImage(c,id,screenshot);
            try(var p=c.prepareStatement("""
                INSERT INTO overseas_order_lines(line_id,overseas_order_id,description,product_url,options,quantity,unit_price,usd_cost,exchange_rate,screenshot,sort_order,seller)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(line_id) DO UPDATE SET description=EXCLUDED.description,product_url=EXCLUDED.product_url,seller=EXCLUDED.seller,
                options=EXCLUDED.options,quantity=EXCLUDED.quantity,unit_price=EXCLUDED.unit_price,usd_cost=EXCLUDED.usd_cost,exchange_rate=EXCLUDED.exchange_rate,screenshot=EXCLUDED.screenshot,sort_order=EXCLUDED.sort_order
                """)) {
                p.setObject(1,lid);p.setObject(2,id);p.setString(3,text(l,"description",1000,true));p.setString(4,text(l,"product_url",3000,false));p.setString(5,text(l,"options",1000,false));p.setInt(6,l.get("quantity").getAsInt());p.setBigDecimal(7,OverseasOrderRules.money(l.get("unit_price").getAsBigDecimal()));p.setBigDecimal(8,optionalMoney(l,"usd_cost"));p.setBigDecimal(9,optionalMoney(l,"exchange_rate"));p.setString(10,screenshot);p.setInt(11,sort++);p.setString(12,text(l,"seller",200,false));p.executeUpdate();
            }
        }
        // Never permit a quote edit to manufacture a fully collected order with debt.
        if(issued){List<OverseasOrderRules.Quantities> q=quantities(lines(c,id));OverseasOrderRules.requireCollectionBalance(q,finance(c,before).get("balance_due").getAsBigDecimal());}
        return id;
    }

    private static void issue(Connection c,Actor a,UUID id,JsonObject o)throws Exception {
        if(!o.get("state").getAsString().equals("DRAFT"))throw new IllegalArgumentException("Only drafts can be issued.");
        var selected=nullableInt(o,"customer_id");
        ServerCustomOrderDataService.CustomerOption customer=selected==null?null:new ServerCustomOrderDataService.CustomerOption(selected,o.get("customer_name").getAsString(),o.get("customer_phone").getAsString(),"","");
        int cid=ServerCustomOrderDataService.resolveOrderCustomerId(c,customer,o.get("customer_name").getAsString(),o.get("customer_phone").getAsString());
        String number="OS-"+a.locationId+"-"+id.toString().toUpperCase(Locale.ROOT);
        long parent;try(var p=c.prepareStatement("""
            INSERT INTO custom_orders(order_number,customer_id,customer_name,customer_phone,total_amount,balance_due,location_id,taken_by_user_id,taken_by_name,order_notes,order_type)
            VALUES(?,?,?,?,?,?,?,?,?,?,'OVERSEAS') RETURNING custom_order_id
            """)) {p.setString(1,number);p.setInt(2,cid);p.setString(3,o.get("customer_name").getAsString());p.setString(4,o.get("customer_phone").getAsString());p.setBigDecimal(5,o.get("total").getAsBigDecimal());p.setBigDecimal(6,o.get("total").getAsBigDecimal());p.setInt(7,a.locationId);p.setInt(8,a.userId);p.setString(9,a.name);p.setString(10,"Overseas shopping order");try(var r=p.executeQuery()){r.next();parent=r.getLong(1);}}
        number="OS-"+a.locationId+"-"+parent;
        try(var p=c.prepareStatement("UPDATE custom_orders SET order_number=? WHERE custom_order_id=?")){p.setString(1,number);p.setLong(2,parent);p.executeUpdate();}
        try(var p=c.prepareStatement("UPDATE overseas_orders SET state='ISSUED',custom_order_id=?,customer_id=? WHERE overseas_order_id=?")){p.setLong(1,parent);p.setInt(2,cid);p.setObject(3,id);p.executeUpdate();}
        LanCustomOrderWorkflowService.accountTransaction(c,cid,parent,o.get("total").getAsBigDecimal(),"CUSTOM_ORDER_BALANCE","Overseas order quote "+number,a.name,a.deviceId,a.deviceName,null,null,new CashDrawerContext(null,null));
    }
    private static JsonObject finance(Connection c,JsonObject o)throws SQLException {
        if(o.get("custom_order_id").isJsonNull())throw new IllegalArgumentException("Issue the quote first.");
        try(var p=c.prepareStatement("SELECT row_to_json(f)::text FROM custom_orders f WHERE custom_order_id=? FOR UPDATE")){p.setLong(1,o.get("custom_order_id").getAsLong());try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Financial record not found.");return JsonParser.parseString(r.getString(1)).getAsJsonObject();}}
    }
    private static void updateFinanceTotal(Connection c,Actor a,JsonObject o,BigDecimal total,String reason)throws SQLException {
        JsonObject original=finance(c,o);BigDecimal delta=total.subtract(original.get("total_amount").getAsBigDecimal());
        try(var p=c.prepareStatement("UPDATE custom_orders SET total_amount=?,balance_due=?-amount_paid,payment_status=CASE WHEN ?=amount_paid THEN 'PAID' WHEN amount_paid=0 THEN 'UNPAID' ELSE 'PARTIAL' END,updated_at=now() WHERE custom_order_id=?")){p.setBigDecimal(1,total);p.setBigDecimal(2,total);p.setBigDecimal(3,total);p.setLong(4,o.get("custom_order_id").getAsLong());p.executeUpdate();}
        if(delta.signum()!=0)LanCustomOrderWorkflowService.accountTransaction(c,original.get("customer_id").getAsInt(),o.get("custom_order_id").getAsLong(),delta,"CUSTOM_ORDER_BALANCE","Overseas quote adjustment: "+reason,a.name,a.deviceId,a.deviceName,null,null,new CashDrawerContext(null,null));
    }
    private static void pay(Connection c,Actor a,JsonObject b,JsonObject o)throws Exception {
        finance(c,o);String method=paymentMethod(b);BigDecimal amount=OverseasOrderRules.money(b.get("amount").getAsBigDecimal());
        LanCustomOrderWorkflowService.payment(c,o.get("custom_order_id").getAsLong(),amount,method,text(b,"reference",500,false),a.locationId,a.userId,a.name,a.deviceId,a.deviceName);
    }
    private static void shipment(Connection c,Actor a,UUID id,JsonObject b,JsonObject o,boolean collection)throws Exception {
        if(!o.get("state").getAsString().equals("ISSUED")&&!(collection&&o.get("state").getAsString().equals("COMPLETED")))throw new IllegalArgumentException("Only issued orders can be updated.");
        JsonArray updates=b.getAsJsonArray("lines");if(updates==null||updates.isEmpty())throw new IllegalArgumentException("Select item quantities.");
        Map<UUID,JsonObject> byId=new HashMap<>();for(JsonElement e:lines(c,id)){JsonObject l=e.getAsJsonObject();byId.put(UUID.fromString(l.get("line_id").getAsString()),l);}
        Set<UUID> seen=new HashSet<>();for(JsonElement e:updates){JsonObject patch=e.getAsJsonObject();UUID lid=UUID.fromString(text(patch,"line_id",36,true));JsonObject l=byId.get(lid);if(l==null||!seen.add(lid))throw new IllegalArgumentException("Invalid or duplicate item.");
            for(String key:collection?List.of("collected"):List.of("purchased","shipped","arrived"))if(patch.has(key)) {
                int count=count(patch,key);
                if(count<l.get(key).getAsInt()){permit(a,"ADJUST");text(b,"reason",2000,true);}l.addProperty(key,count);
            }
        }
        List<OverseasOrderRules.Quantities> all=new ArrayList<>();for(JsonObject l:byId.values())all.add(quantity(l));
        BigDecimal balance=finance(c,o).get("balance_due").getAsBigDecimal();OverseasOrderRules.requireCollectionBalance(all,balance);
        for(UUID lid:seen){JsonObject l=byId.get(lid);try(var p=c.prepareStatement("UPDATE overseas_order_lines SET purchased=?,shipped=?,arrived=?,collected=? WHERE line_id=?")){p.setInt(1,l.get("purchased").getAsInt());p.setInt(2,l.get("shipped").getAsInt());p.setInt(3,l.get("arrived").getAsInt());p.setInt(4,l.get("collected").getAsInt());p.setObject(5,lid);p.executeUpdate();}}
        if(all.stream().allMatch(q->q.quantity()==q.collected())) {
            try(var p=c.prepareStatement("UPDATE overseas_orders SET state='COMPLETED' WHERE overseas_order_id=?")){p.setObject(1,id);p.executeUpdate();}
            try(var p=c.prepareStatement("UPDATE custom_orders SET status='DELIVERED',delivered_at=now(),updated_at=now() WHERE custom_order_id=?")){p.setLong(1,o.get("custom_order_id").getAsLong());p.executeUpdate();}
        } else if(o.get("state").getAsString().equals("COMPLETED")) {
            try(var p=c.prepareStatement("UPDATE overseas_orders SET state='ISSUED' WHERE overseas_order_id=?")){p.setObject(1,id);p.executeUpdate();}
            try(var p=c.prepareStatement("UPDATE custom_orders SET status='NEW',delivered_at=NULL,updated_at=now() WHERE custom_order_id=?")){p.setLong(1,o.get("custom_order_id").getAsLong());p.executeUpdate();}
        }
    }
    private static void refund(Connection c,Actor a,JsonObject b,JsonObject o)throws Exception {
        JsonObject f=finance(c,o);BigDecimal amount=OverseasOrderRules.money(b.get("amount").getAsBigDecimal());String reason=text(b,"reason",2000,true),method=paymentMethod(b),ref=text(b,"reference",500,false);
        if(amount.signum()<=0||amount.compareTo(f.get("amount_paid").getAsBigDecimal())>0)throw new IllegalArgumentException("Refund must be positive and cannot exceed net payments.");
        if(Set.of("CARD","CHEQUE","MMG").contains(method)&&ref.isBlank())throw new IllegalArgumentException("A refund reference is required.");
        try(var p=c.prepareStatement("SELECT COALESCE(custom_order_refund_approval_limit,0) FROM company_customization WHERE location_id=?")){p.setInt(1,a.locationId);try(var r=p.executeQuery()){if(r.next()&&r.getBigDecimal(1).signum()>0&&amount.compareTo(r.getBigDecimal(1))>0&&!a.permissions.contains("CUSTOM_ORDER_REFUND_APPROVAL")&&!a.permissions.contains("CUSTOM_ORDER_OVERRIDES"))throw new SecurityException("Refund exceeds your approval limit. An authorized refund approver must perform it.");}}
        CashDrawerContext drawer=LanCustomOrderWorkflowService.cashDrawer(c,method,a.locationId,a.deviceId);
        long parent=o.get("custom_order_id").getAsLong();LanCustomOrderWorkflowService.insertPayment(c,parent,amount,method,ref,"REFUND",reason,a.userId,a.name,a.deviceId,a.deviceName,drawer);
        // A payment correction reopens the balance; it does not silently discount the quote.
        LanCustomOrderWorkflowService.accountTransaction(c,f.get("customer_id").getAsInt(),parent,amount,"CUSTOM_ORDER_PAYMENT","Overseas payment refund: "+reason,a.name,a.deviceId,a.deviceName,method,ref,drawer);
        try(var p=c.prepareStatement("UPDATE custom_orders SET amount_paid=amount_paid-?,balance_due=balance_due+?,payment_status=CASE WHEN amount_paid-?=0 THEN 'UNPAID' ELSE 'PARTIAL' END,status=CASE WHEN status='DELIVERED' THEN 'NEW' ELSE status END,updated_at=now() WHERE custom_order_id=?")){p.setBigDecimal(1,amount);p.setBigDecimal(2,amount);p.setBigDecimal(3,amount);p.setLong(4,parent);p.executeUpdate();}
        try(var p=c.prepareStatement("UPDATE overseas_orders SET state='ISSUED' WHERE overseas_order_id=?")){p.setObject(1,UUID.fromString(o.get("overseas_order_id").getAsString()));p.executeUpdate();}
    }
    private static void cancel(Connection c,Actor a,UUID id,JsonObject b,JsonObject o)throws Exception {
        String reason=text(b,"reason",2000,true);for(JsonElement l:lines(c,id))if(l.getAsJsonObject().get("collected").getAsInt()>0)throw new IllegalArgumentException("Collected items must be corrected before cancelling this order.");
        if(!o.get("custom_order_id").isJsonNull()) {
            JsonObject f=finance(c,o);if(f.get("amount_paid").getAsBigDecimal().signum()>0)throw new IllegalArgumentException("Refund payments before cancelling.");
            updateFinanceTotal(c,a,o,BigDecimal.ZERO,reason);
            try(var p=c.prepareStatement("UPDATE custom_orders SET status='CANCELLED',cancellation_reason=?,cancelled_at=now(),cancelled_by_user_id=?,cancelled_by_name=? WHERE custom_order_id=?")){p.setString(1,reason);p.setInt(2,a.userId);p.setString(3,a.name);p.setLong(4,o.get("custom_order_id").getAsLong());p.executeUpdate();}
        }
        try(var p=c.prepareStatement("UPDATE overseas_orders SET state='CANCELLED',total=0 WHERE overseas_order_id=?")){p.setObject(1,id);p.executeUpdate();}
    }
    private static JsonObject uploadResult(Connection c,Actor a,JsonObject b,UUID id,JsonObject before,UUID requestId,String hash)throws Exception {
        byte[] bytes=Base64.getDecoder().decode(text(b,"base64",2800000,true));if(bytes.length==0||bytes.length>2000000)throw new IllegalArgumentException("Screenshot must be under 2 MB.");
        String type;
        try(var in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw new IllegalArgumentException("Upload a PNG or JPEG screenshot.");ImageReader reader=readers.next();
            try {reader.setInput(in);String format=reader.getFormatName().toLowerCase(Locale.ROOT);if(!Set.of("png","jpeg","jpg").contains(format))throw new IllegalArgumentException("Upload a PNG or JPEG screenshot.");if(reader.getWidth(0)>6000||reader.getHeight(0)>6000||(long)reader.getWidth(0)*reader.getHeight(0)>20000000)throw new IllegalArgumentException("Screenshot dimensions are too large.");type=format.equals("png")?"image/png":"image/jpeg";} finally{reader.dispose();}
        }
        String ref=ServerImageAssetService.storeUpload(c,"OVERSEAS_ORDER","custom-order-files",a.locationId+"/overseas/"+id+"/"+UUID.randomUUID()+(type.equals("image/png")?".png":".jpg"),type,"Screenshot","AUTHENTICATED",bytes);
        try(var p=c.prepareStatement("INSERT INTO overseas_order_images(overseas_order_id,asset_reference) VALUES(?,?)")){p.setObject(1,id);p.setString(2,ref);p.executeUpdate();}
        event(c,a,id,"UPLOAD","Screenshot attached",null,null);JsonObject out=new JsonObject();out.addProperty("reference",ref);remember(c,a,requestId,hash,out);return out;
    }
    private static void requireImage(Connection c,UUID id,String ref)throws SQLException {try(var p=c.prepareStatement("SELECT 1 FROM overseas_order_images WHERE overseas_order_id=? AND asset_reference=?")){p.setObject(1,id);p.setString(2,ref);try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("Screenshot does not belong to this order.");}}}
    private static void event(Connection c,Actor a,UUID id,String action,String reason,JsonObject before,JsonObject after)throws SQLException {
        if(after!=null)after.add("lines",lines(c,id));
        try(var p=c.prepareStatement("INSERT INTO overseas_order_events(overseas_order_id,actor_id,actor_name,action,reason,before_value,after_value) VALUES(?,?,?,?,?,?::jsonb,?::jsonb)")){p.setObject(1,id);p.setInt(2,a.userId);p.setString(3,a.name);p.setString(4,action);p.setString(5,reason);p.setString(6,before==null?null:JSON.toJson(before));p.setString(7,after==null?null:JSON.toJson(after));p.executeUpdate();}
    }
    private static void remember(Connection c,Actor a,UUID key,String hash,JsonObject out)throws SQLException {try(var p=c.prepareStatement("INSERT INTO overseas_order_requests(request_id,actor_id,location_id,request_hash,response) VALUES(?,?,?,?,?::jsonb)")){p.setObject(1,key);p.setInt(2,a.userId);p.setInt(3,a.locationId);p.setString(4,hash);p.setString(5,out.toString());p.executeUpdate();}}
    private static List<OverseasOrderRules.Quantities> quantities(JsonArray lines){List<OverseasOrderRules.Quantities> out=new ArrayList<>();for(JsonElement e:lines)out.add(quantity(e.getAsJsonObject()));return out;}
    private static OverseasOrderRules.Quantities quantity(JsonObject l){return new OverseasOrderRules.Quantities(l.get("quantity").getAsInt(),l.get("purchased").getAsInt(),l.get("shipped").getAsInt(),l.get("arrived").getAsInt(),l.get("collected").getAsInt());}
    private static Integer nullableInt(JsonObject o,String key){return !o.has(key)||o.get(key).isJsonNull()?null:o.get(key).getAsInt();}
    private static int count(JsonObject o,String key){try{return o.get(key).getAsBigDecimal().intValueExact();}catch(Exception e){throw new IllegalArgumentException("Enter a whole number for "+key+".");}}
    private static long revision(JsonObject o){try{return o.get("revision").getAsBigDecimal().longValueExact();}catch(Exception e){throw new IllegalArgumentException("Reload this order to obtain its current revision.");}}
    private static void redact(JsonObject response,Actor actor){response.add("permissions",JSON.toJsonTree(actor.permissions));if(response.has("order")&&!actor.permissions.contains("OVERSEAS_ORDERS_QUOTE"))for(JsonElement l:response.getAsJsonObject("order").getAsJsonArray("lines")){l.getAsJsonObject().remove("usd_cost");l.getAsJsonObject().remove("exchange_rate");}}
    private static BigDecimal optionalMoney(JsonObject o,String key){return !o.has(key)||o.get(key).isJsonNull()||o.get(key).getAsString().isBlank()?null:o.get(key).getAsBigDecimal();}
    private static String paymentMethod(JsonObject b){String method=text(b,"method",20,true);if(!Set.of("CASH","CARD","CHEQUE","MMG").contains(method))throw new IllegalArgumentException("Choose cash, card, cheque or MMG.");return method;}
    private static String text(JsonObject o,String key,int max,boolean required){String value=o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString().trim():"";if(value.length()>max||(required&&value.isBlank()))throw new IllegalArgumentException("Check "+key.replace('_',' ')+".");return value;}
}
