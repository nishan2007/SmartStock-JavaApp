package services;

import com.google.gson.*;
import data.DatabaseConfig;
import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import java.time.Instant;
import static services.StorefrontPolicy.*;

/** Store-owned storefront data. Call mutations inside the caller's transaction. */
public final class StorefrontService {
    static final Gson JSON=new Gson();
    private StorefrontService() { }
    static String text(JsonObject o,String k){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}
    static int integer(JsonObject o,String k){
        if(!o.has(k)||o.get(k).isJsonNull())return 0;
        try{return new BigDecimal(o.get(k).getAsString()).intValueExact();}
        catch(RuntimeException e){throw new IllegalArgumentException("Enter a whole number within the supported range for "+k+".");}
    }
    static BigDecimal decimal(JsonObject o,String k){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsBigDecimal():BigDecimal.ZERO;}
    static JsonArray rows(Connection c,String sql,Object...args)throws SQLException{
        JsonArray out=new JsonArray();try(var p=c.prepareStatement(sql)){bind(p,args);try(var r=p.executeQuery()){
            var meta=r.getMetaData();while(r.next()){JsonObject row=new JsonObject();for(int i=1;i<=meta.getColumnCount();i++){
                Object v=r.getObject(i);String key=meta.getColumnLabel(i);
                if(v==null)row.add(key,JsonNull.INSTANCE);else if(v instanceof Number n)row.addProperty(key,n);
                else if(v instanceof Boolean b)row.addProperty(key,b);else if(v instanceof Timestamp t)row.addProperty(key,t.toInstant().toString());
                else if("jsonb".equals(meta.getColumnTypeName(i)))row.add(key,JsonParser.parseString(v.toString()));
                else row.addProperty(key,v.toString());
            }out.add(row);}}
        }return out;
    }
    static void bind(PreparedStatement p,Object...args)throws SQLException{for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);}
    static int execute(Connection c,String sql,Object...args)throws SQLException{try(var p=c.prepareStatement(sql)){bind(p,args);return p.executeUpdate();}}
    static JsonObject one(JsonArray a){return a.isEmpty()?new JsonObject():a.get(0).getAsJsonObject();}
    static void lock(Connection c,int location)throws SQLException{try(var p=c.prepareStatement("SELECT pg_advisory_xact_lock(73489,?)")){p.setInt(1,location);p.execute();}}

    public static JsonObject snapshot(Connection c,int location)throws SQLException{
        execute(c,"INSERT INTO storefront.settings(location_id) VALUES(?) ON CONFLICT DO NOTHING",location);
        JsonObject s=new JsonObject();s.addProperty("locationId",location);s.addProperty("capturedAt",Instant.now().toString());
        s.add("settings",one(rows(c,"SELECT * FROM storefront.settings WHERE location_id=?",location)));
        s.add("unavailableServices",StorefrontServiceAvailability.unavailable(c,location));
        s.add("store",one(rows(c,"SELECT location_id AS id,name,address,timezone FROM locations WHERE location_id=?",location)));
        s.add("branding",one(rows(c,"""
            SELECT ci.company_name AS name,ci.company_motto_line1 AS "mottoLine1",
              ci.company_motto_line2 AS "mottoLine2",ci.company_logo_url AS "_logoReference",
              l.company_phone_line1 AS "phoneLine1",l.company_phone_line2 AS "phoneLine2",
              l.company_email_line1 AS "emailLine1",l.company_email_line2 AS "emailLine2",
              l.company_address_line1 AS "addressLine1",l.company_address_line2 AS "addressLine2"
            FROM company_info ci LEFT JOIN locations l ON l.location_id=? WHERE ci.company_info_id=1
            """,location)));
        var branding=s.getAsJsonObject("branding");
        var logo=ServerImageAssetService.storefrontManifest(c,text(branding,"_logoReference"),"COMPANY_LOGO");
        branding.remove("_logoReference");
        if(logo!=null)branding.add("_logoManifest",logo);
        s.add("tax",one(rows(c,"SELECT vat_enabled,vat_use_department_rates,vat_fixed_rate_percent,round_sales_to_nearest_twenty FROM company_customization WHERE location_id=?",location)));
        s.add("products",rows(c,"""
            SELECT p.product_id AS id,p.name,p.sku,COALESCE(p.size,'') AS size,COALESCE(p.color,'') AS color,
              p.group_id AS "groupId",p.variant_options AS "variantOptions",
              COALESCE(g.name,'') AS "groupName",COALESCE(g.option_names,'[]'::jsonb) AS "optionNames",
              COALESCE(NULLIF(w.description,''),p.description,'') AS description,
              COALESCE(w.promotional_price,w.website_price,p.price) AS price,
              CASE WHEN w.promotional_price IS NOT NULL THEN w.website_price END AS "regularPrice",
              COALESCE(p.image_url,'') AS image,COALESCE(cat.name,'Essentials') AS category,
              COALESCE(cat.vat_rate_percent,0) AS "vatRate",w.featured,
              GREATEST(0,COALESCE(i.quantity_on_hand,0)-COALESCE((SELECT SUM(r.quantity) FROM storefront.reservations r
                WHERE r.product_id=p.product_id AND r.location_id=w.location_id),0)) AS available
            FROM storefront.products w JOIN products p ON p.product_id=w.product_id
            LEFT JOIN product_groups g ON g.group_id=p.group_id
            LEFT JOIN inventory i ON i.product_id=p.product_id AND i.location_id=w.location_id
            LEFT JOIN categories cat ON cat.category_id=p.category_id
            WHERE w.location_id=? AND w.published AND p.is_active AND p.product_type='INVENTORY' ORDER BY p.name,p.product_id
            """,location));
        for(var e:s.getAsJsonArray("products")){
            var product=e.getAsJsonObject();var manifest=ServerImageAssetService.storefrontManifest(c,text(product,"image"));
            if(manifest!=null){product.add("_imageManifest",manifest);product.addProperty("image",ImageAssetReference.format(UUID.fromString(text(manifest,"id"))));}
        }
        s.add("projects",rows(c,"""
            SELECT project_id AS id,title,summary,category,materials,production_method AS "productionMethod",service_slug AS "serviceSlug",
                   customization,tags,starting_price AS "startingPrice",product_ids AS "productIds",cover_reference AS cover,
                   status='FEATURED' AS featured,updated_at AS "updatedAt"
            FROM storefront.projects WHERE location_id=? AND status IN ('PUBLISHED','FEATURED')
            ORDER BY (status='FEATURED') DESC,updated_at DESC,project_id LIMIT 200
            """,location));
        JsonArray approvedProjects=new JsonArray();
        Map<String,JsonObject> approvedById=new HashMap<>();
        for(var e:s.getAsJsonArray("projects")){
            var project=e.getAsJsonObject();var manifest=ServerImageAssetService.storefrontManifest(c,text(project,"cover"),"PROJECT");
            if(manifest!=null){project.add("_coverManifest",manifest);project.add("gallery",new JsonArray());approvedProjects.add(project);approvedById.put(text(project,"id"),project);}
        }
        for(var e:rows(c,"""
            SELECT m.media_id AS id,m.project_id AS "projectId",m.role,m.caption,m.asset_reference AS reference
            FROM storefront.project_media m JOIN storefront.projects p ON p.project_id=m.project_id
            WHERE m.location_id=? AND p.location_id=? AND p.status IN ('PUBLISHED','FEATURED')
            ORDER BY m.project_id,m.position
            """,location,location)){
            var media=e.getAsJsonObject();var project=approvedById.get(text(media,"projectId"));if(project==null)continue;
            var manifest=ServerImageAssetService.storefrontManifest(c,text(media,"reference"),"PROJECT");
            if(manifest==null)continue;
            media.remove("reference");media.remove("projectId");media.add("_manifest",manifest);
            project.getAsJsonArray("gallery").add(media);
        }
        s.add("projects",approvedProjects);
        // These fields remain on trusted server synchronization routes, never in public catalog responses.
        s.add("customers",rows(c,"""
            SELECT customer_id AS id,sync_uuid AS uuid,name,email,is_active AS active,current_balance AS balance,
              sales_discount_enabled AS "discountEnabled",sales_discount_percent AS discount
            FROM customer_accounts WHERE email IS NOT NULL AND btrim(email)<>''
            """));
        s.add("links",rows(c,"SELECT auth_id AS auth,customer_uuid AS customer,email FROM storefront.customer_links WHERE location_id=?",location));
        s.add("orders",rows(c,"SELECT order_id AS id,revision,status FROM storefront.orders WHERE location_id=?",location));
        s.add("receipts",rows(c,"""
            SELECT ca.sync_uuid AS customer,s.sale_id AS id,s.receipt_number AS number,s.created_at AS date,
              s.total_amount AS total,s.payment_status AS status,s.payment_method AS method,s.status AS "saleStatus",
              s.subtotal_amount AS subtotal,s.discount_amount AS discount,s.vat_amount AS vat,s.amount_paid AS paid,s.returned_amount AS returned,
              COALESCE((SELECT jsonb_agg(jsonb_build_object('name',COALESCE(NULLIF(si.item_name,''),p.name,'Item'),
                'quantity',si.quantity,'price',si.unit_price,'discount',si.discount_amount) ORDER BY si.sale_item_id)
                FROM sale_items si LEFT JOIN products p ON p.product_id=si.product_id WHERE si.sale_id=s.sale_id),'[]'::jsonb) AS lines
            FROM sales s JOIN customer_accounts ca ON ca.customer_id=s.customer_id WHERE s.location_id=?
            ORDER BY s.created_at DESC LIMIT 10000
            """,location));
        return s;
    }
    static JsonObject publicSyncSnapshot(JsonObject snapshot){
        JsonObject publicOnly=new JsonObject();
        for(String key:List.of("locationId","capturedAt","settings","unavailableServices",
                "store","branding","tax","products","projects")){
            if(snapshot.has(key))publicOnly.add(key,snapshot.get(key).deepCopy());
        }
        if(publicOnly.has("settings"))publicOnly.getAsJsonObject("settings").addProperty("enabled",false);
        return publicOnly;
    }
    static JsonObject cached(Connection c,int location)throws SQLException{
        JsonObject row=one(rows(c,"SELECT payload FROM storefront.snapshots WHERE location_id=?",location));
        if(!row.has("payload"))throw new IllegalArgumentException("This store's catalog is not available yet.");
        return row.getAsJsonObject("payload");
    }
    static JsonObject catalog(JsonObject s){
        JsonObject out=new JsonObject();for(String k:List.of("locationId","capturedAt","settings","store","products","projects","branding","unavailableServices"))out.add(k,s.has(k)?s.get(k).deepCopy():JsonNull.INSTANCE);
        JsonObject settings=out.get("settings").isJsonObject()?out.getAsJsonObject("settings"):new JsonObject();
        JsonObject campaign=new JsonObject();
        for(String[] field:new String[][]{{"topic","campaign_topic"},{"eyebrow","campaign_eyebrow"},{"headline","campaign_headline"},
                {"description","campaign_description"},{"steps","campaign_steps"},{"primary","campaign_primary"},
                {"secondary","campaign_secondary"},{"visual","campaign_visual"},
                {"primaryAction","campaign_primary_action"},{"secondaryAction","campaign_secondary_action"}}){
            if(settings.has(field[1]))campaign.addProperty(field[0],text(settings,field[1]));
            settings.remove(field[1]);
        }
        if(campaign.has("headline"))out.add("campaign",campaign);
        if(out.has("branding")&&!out.get("branding").isJsonNull()){
            var brand=out.getAsJsonObject("branding");
            var logo=brand.remove("_logoManifest");
            brand.addProperty("logo",logo==null?"":text(logo.getAsJsonObject(),"sha256"));
        }
        // The browser needs only an image presence indicator; storage references can contain private URLs.
        for(var e:out.getAsJsonArray("products")){
            var p=e.getAsJsonObject();p.remove("_imageManifest");p.addProperty("image",text(p,"image").isBlank()?"":"available");
            int available=integer(p,"available");p.remove("available");
            p.addProperty("availability",available<=0?"Out of Stock":available<=3?"Low Stock":"In Stock");
            p.addProperty("canOrder",available>0);
        }
        Set<Integer> publishedProductIds=new HashSet<>();
        for(var e:out.getAsJsonArray("products"))publishedProductIds.add(integer(e.getAsJsonObject(),"id"));
        if(out.get("projects").isJsonArray())for(var e:out.getAsJsonArray("projects")){
            var p=e.getAsJsonObject();boolean hasCover=p.has("_coverManifest");p.remove("_coverManifest");p.addProperty("cover",hasCover?"available":"");
            JsonArray visible=new JsonArray();
            if(p.has("productIds")&&p.get("productIds").isJsonArray())for(var id:p.getAsJsonArray("productIds")){
                try{int value=id.getAsInt();if(publishedProductIds.contains(value))visible.add(value);}
                catch(RuntimeException ignored){/* Invalid references cannot escape to the public catalog. */}
            }
            p.add("productIds",visible);
            if(p.has("gallery")&&p.get("gallery").isJsonArray())for(var item:p.getAsJsonArray("gallery"))item.getAsJsonObject().remove("_manifest");
        }
        return out;
    }
    static JsonObject customer(JsonObject s,UUID auth,String email){
        String linked=null;for(var e:s.getAsJsonArray("links")){var l=e.getAsJsonObject();if(auth.toString().equals(text(l,"auth")))linked=text(l,"customer");}
        List<JsonObject> matches=new ArrayList<>();for(var e:s.getAsJsonArray("customers")){var x=e.getAsJsonObject();
            if(linked!=null?linked.equals(text(x,"uuid")):email.equalsIgnoreCase(text(x,"email").trim()))matches.add(x);
        }
        if(matches.size()>1)throw new IllegalArgumentException("Several customer records use this email. Please ask the store to link your account.");
        if(matches.isEmpty()){
            if(linked!=null)throw new IllegalArgumentException("Your linked customer record is unavailable. Please contact the store.");
            JsonObject n=new JsonObject();n.addProperty("uuid",UUID.nameUUIDFromBytes(("deckers-customer:"+auth).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
            n.addProperty("name",email);n.addProperty("email",email);n.addProperty("active",true);return n;
        }
        JsonObject result=matches.get(0);if(!result.get("active").getAsBoolean())throw new IllegalArgumentException("This customer account is inactive. Contact the store.");
        for(var e:s.getAsJsonArray("links")){var link=e.getAsJsonObject();if(text(result,"uuid").equals(text(link,"customer"))&&!auth.toString().equals(text(link,"auth")))
            throw new IllegalArgumentException("This customer record is linked to another website identity. Please contact the store.");}
        return result;
    }
    static JsonObject quote(JsonObject s,JsonObject body,UUID auth,String email){
        if(!s.getAsJsonObject("settings").get("enabled").getAsBoolean())throw new IllegalArgumentException("Online ordering is unavailable at this store.");
        JsonObject customer=customer(s,auth,email),tax=s.getAsJsonObject("tax");
        BigDecimal discount=customer.has("discountEnabled")&&customer.get("discountEnabled").getAsBoolean()?decimal(customer,"discount"):BigDecimal.ZERO;
        CustomerDiscountPolicy.validated(discount);
        Map<Integer,JsonObject> products=new HashMap<>();for(var p:s.getAsJsonArray("products"))products.put(integer(p.getAsJsonObject(),"id"),p.getAsJsonObject());
        if(!body.has("lines")||!body.get("lines").isJsonArray()||body.getAsJsonArray("lines").isEmpty()||body.getAsJsonArray("lines").size()>100)throw new IllegalArgumentException("Add 1 to 100 products to your bag.");
        JsonArray lines=new JsonArray();Set<Integer> seen=new HashSet<>();BigDecimal subtotal=BigDecimal.ZERO,vat=BigDecimal.ZERO;
        JsonObject pending=body.has("_pending")?body.getAsJsonObject("_pending"):new JsonObject();
        for(var item:body.getAsJsonArray("lines")){
            var wanted=item.getAsJsonObject();int id=integer(wanted,"id"),qty=integer(wanted,"quantity");quantity(qty);
            if(!seen.add(id))throw new IllegalArgumentException("Duplicate cart product.");
            JsonObject p=products.get(id);if(p==null)throw new IllegalArgumentException("A product is no longer published.");
            int available=integer(p,"available")-(pending.has(""+id)?pending.get(""+id).getAsInt():0);
            if(qty>available)throw new IllegalArgumentException(text(p,"name")+" has insufficient available stock.");
            BigDecimal price=decimal(p,"price"),gross=money(price.multiply(BigDecimal.valueOf(qty)));
            BigDecimal rate=tax.has("vat_enabled")&&tax.get("vat_enabled").getAsBoolean()?
                tax.get("vat_use_department_rates").getAsBoolean()?decimal(p,"vatRate"):decimal(tax,"vat_fixed_rate_percent"):BigDecimal.ZERO;
            subtotal=subtotal.add(gross);vat=vat.add(CustomerDiscountPolicy.amount(gross,rate));
            JsonObject l=new JsonObject();l.addProperty("id",id);l.addProperty("quantity",qty);l.addProperty("name",text(p,"name"));l.addProperty("price",price);l.addProperty("vatRate",rate);lines.add(l);
        }
        BigDecimal reduction=CustomerDiscountPolicy.amount(subtotal,discount);
        vat=money(vat.multiply(BigDecimal.ONE.subtract(discount.movePointLeft(2))));
        BigDecimal total=LanSalesService.roundSaleTotal(money(subtotal.subtract(reduction).add(vat)),!tax.has("round_sales_to_nearest_twenty")||tax.get("round_sales_to_nearest_twenty").getAsBoolean());
        if(total.signum()<=0)throw new IllegalArgumentException("Order total must be greater than zero.");
        JsonObject q=new JsonObject();q.add("lines",lines);q.addProperty("subtotal",subtotal);q.addProperty("discount",reduction);q.addProperty("discountPercent",discount);q.addProperty("vat",vat);q.addProperty("total",total);
        q.addProperty("currency",text(s.getAsJsonObject("settings"),"currency"));q.addProperty("locationId",integer(s,"locationId"));q.addProperty("capturedAt",text(s,"capturedAt"));q.addProperty("customerUuid",text(customer,"uuid"));q.addProperty("customerName",text(customer,"name"));return q;
    }
    static JsonObject checkout(Connection c,JsonObject s,JsonObject body,UUID auth,String email,int serving)throws Exception{
        UUID id=UUID.fromString(text(body,"orderId"));int location=integer(s,"locationId");lock(c,location);
        String hash=LanSecurity.sha256(auth+"|"+location+"|"+body.get("lines")+"|"+text(body,"expectedTotal"));
        JsonObject old=one(rows(c,"SELECT * FROM storefront.orders WHERE order_id=? FOR UPDATE",id));
        if(!old.entrySet().isEmpty()){if(!auth.toString().equals(text(old,"auth_id"))||!hash.equals(text(old,"request_hash")))throw new IllegalArgumentException("This checkout identifier was already used for another order.");return old;}
        if(!rows(c,"SELECT order_id FROM storefront.rejected_commands WHERE order_id=?",id).isEmpty())
            throw new IllegalArgumentException("This interrupted checkout was closed without acceptance. Review your bag to start a new order.");
        // The live fulfillment store is authoritative when reachable. Snapshots are only
        // a fallback; do not silently apply a stale price or availability on the primary.
        if(location==serving){
            s=snapshot(c,location);
            JsonObject pending=body.has("_pending")?body.getAsJsonObject("_pending").deepCopy():new JsonObject();
            // Coordinator pending quantities exclude orders already in its source snapshot.
            // Locally imported orders are already deducted by snapshot(), so remove them here.
            if(body.has("_snapshot")){
                Set<String> known=new HashSet<>();for(var e:body.getAsJsonObject("_snapshot").getAsJsonArray("orders"))known.add(text(e.getAsJsonObject(),"id"));
                for(var e:rows(c,"SELECT order_id,quote,status FROM storefront.orders WHERE location_id=?",location)){
                    var order=e.getAsJsonObject();if(known.contains(text(order,"order_id"))||!reserves(text(order,"status")))continue;
                    for(var line:order.getAsJsonObject("quote").getAsJsonArray("lines")){var l=line.getAsJsonObject();String key=text(l,"id");if(pending.has(key))pending.addProperty(key,Math.max(0,pending.get(key).getAsInt()-integer(l,"quantity")));}
                }
            }
            body=body.deepCopy();body.add("_pending",pending);
        }
        JsonObject q=quote(s,body,auth,email);
        if(decimal(body,"expectedTotal").compareTo(decimal(q,"total"))!=0)throw new IllegalArgumentException("Your total changed. Review the bag and confirm again.");
        execute(c,"""
            INSERT INTO storefront.orders(order_id,auth_id,location_id,customer_uuid,email,name,request_hash,quote,total,source_server)
            VALUES(?,?,?,?,?,?,?,?::jsonb,?,?)
            """,id,auth,location,UUID.fromString(text(q,"customerUuid")),email,text(q,"customerName"),hash,q.toString(),decimal(q,"total"),serving);
        if(location==serving){ensureCustomer(c,id);reserve(c,id,location);}
        emit(c,id);queueEmail(c,id);return one(rows(c,"SELECT * FROM storefront.orders WHERE order_id=?",id));
    }
    static void ensureCustomer(Connection c,UUID order)throws SQLException{
        JsonObject o=one(rows(c,"SELECT * FROM storefront.orders WHERE order_id=?",order));UUID customer=UUID.fromString(text(o,"customer_uuid"));
        execute(c,"INSERT INTO customer_accounts(sync_uuid,name,email) SELECT ?,?,? WHERE NOT EXISTS(SELECT 1 FROM customer_accounts WHERE sync_uuid=?)",customer,text(o,"name"),text(o,"email"),customer);
        execute(c,"INSERT INTO storefront.customer_links(auth_id,location_id,customer_uuid,email) VALUES(?,?,?,?) ON CONFLICT(auth_id,location_id) DO NOTHING",UUID.fromString(text(o,"auth_id")),integer(o,"location_id"),customer,text(o,"email"));
    }
    static JsonObject resolveCommand(Connection c,UUID id,int location)throws SQLException{
        // Serialize with checkout before certifying absence. The persisted rejection also
        // fences requests that were queued in HTTP when the coordinator timed out.
        lock(c,location);
        JsonObject order=one(rows(c,"SELECT * FROM storefront.orders WHERE order_id=?",id));
        JsonObject result=new JsonObject();
        if(!order.entrySet().isEmpty()){
            if(integer(order,"location_id")!=location)throw new IllegalArgumentException("Order store mismatch.");
            result.add("order",order);
        }else{
            execute(c,"INSERT INTO storefront.rejected_commands(order_id,location_id) VALUES(?,?) ON CONFLICT DO NOTHING",id,location);
            result.addProperty("rejected",true);
        }
        return result;
    }
    static void reserve(Connection c,UUID id,int location)throws SQLException{
        JsonObject o=one(rows(c,"SELECT quote,status FROM storefront.orders WHERE order_id=?",id));
        execute(c,"DELETE FROM storefront.reservations WHERE order_id=?",id);
        if(!reserves(text(o,"status")))return;
        boolean shortage=false;
        for(var e:o.getAsJsonObject("quote").getAsJsonArray("lines")){
            var l=e.getAsJsonObject();int product=integer(l,"id"),qty=integer(l,"quantity");
            var stock=one(rows(c,"SELECT quantity_on_hand AS qty FROM inventory WHERE product_id=? AND location_id=? FOR UPDATE",product,location));
            var held=one(rows(c,"SELECT COALESCE(SUM(quantity),0) AS qty FROM storefront.reservations WHERE product_id=? AND location_id=?",product,location));
            if(integer(stock,"qty")-integer(held,"qty")<qty)shortage=true;
            execute(c,"INSERT INTO storefront.reservations(order_id,product_id,location_id,quantity) VALUES(?,?,?,?)",id,product,location,qty);
        }
        if(shortage)execute(c,"UPDATE storefront.orders SET status='NEEDS_ATTENTION',note='Stock shortage: contact customer before preparing.',revision=revision+1,updated_at=now() WHERE order_id=?",id);
    }
    static void emit(Connection c,UUID id)throws SQLException{
        JsonObject order=one(rows(c,"SELECT * FROM storefront.orders WHERE order_id=?",id));
        execute(c,"INSERT INTO storefront.events(event_id,order_id,revision,payload) VALUES(?,?,?,?::jsonb) ON CONFLICT(order_id,revision) DO NOTHING",UUID.randomUUID(),id,order.get("revision").getAsLong(),order.toString());
    }
    static void importOrder(Connection c,JsonObject o,int local)throws Exception{
        UUID id=UUID.fromString(text(o,"order_id"));int location=integer(o,"location_id");lock(c,location);
        JsonObject old=one(rows(c,"SELECT * FROM storefront.orders WHERE order_id=? FOR UPDATE",id));
        // A higher lifecycle revision cannot reassign an accepted order or its locked price.
        if(!old.entrySet().isEmpty()){
            for(String key:List.of("auth_id","location_id","customer_uuid","request_hash","source_server"))
                if(!text(old,key).equals(text(o,key)))throw new IllegalArgumentException("Order handoff changed immutable identity.");
            if(decimal(old,"total").compareTo(decimal(o,"total"))!=0||!old.get("quote").equals(o.get("quote")))
                throw new IllegalArgumentException("Order handoff changed the confirmed quote.");
        }
        if(!old.entrySet().isEmpty()&&old.get("revision").getAsLong()>=o.get("revision").getAsLong())return;
        if(old.entrySet().isEmpty()){
            execute(c,"""
                INSERT INTO storefront.orders(order_id,auth_id,location_id,customer_uuid,email,name,request_hash,quote,total,source_server,status,created_at,updated_at,revision,note,sale_id,receipt_number,ready_at,expires_at)
                VALUES(?,?,?,?,?,?,?,?::jsonb,?,?,?,?::timestamptz,?::timestamptz,?,?,?,?,?::timestamptz,?::timestamptz)
                """,id,UUID.fromString(text(o,"auth_id")),location,UUID.fromString(text(o,"customer_uuid")),text(o,"email"),text(o,"name"),text(o,"request_hash"),o.get("quote").toString(),decimal(o,"total"),integer(o,"source_server"),text(o,"status"),text(o,"created_at"),text(o,"updated_at"),o.get("revision").getAsLong(),text(o,"note"),o.has("sale_id")&&!o.get("sale_id").isJsonNull()?integer(o,"sale_id"):null,nullable(o,"receipt_number"),nullable(o,"ready_at"),nullable(o,"expires_at"));
        }else execute(c,"UPDATE storefront.orders SET status=?,revision=?,updated_at=?::timestamptz,note=?,sale_id=?,receipt_number=?,ready_at=?::timestamptz,expires_at=?::timestamptz WHERE order_id=?",
            text(o,"status"),o.get("revision").getAsLong(),text(o,"updated_at"),text(o,"note"),o.has("sale_id")&&!o.get("sale_id").isJsonNull()?integer(o,"sale_id"):null,nullable(o,"receipt_number"),nullable(o,"ready_at"),nullable(o,"expires_at"),id);
        if(location==local){ensureCustomer(c,id);reserve(c,id,local);emit(c,id);}
    }
    static String nullable(JsonObject o,String k){String v=text(o,k);return v.isBlank()?null:v;}
    static void transition(Connection c,UUID id,int location,String status,String note,int user)throws SQLException{
        lock(c,location);JsonObject o=one(rows(c,"SELECT * FROM storefront.orders WHERE order_id=? AND location_id=? FOR UPDATE",id,location));
        if(o.entrySet().isEmpty())throw new IllegalArgumentException("Order not found at this store.");
        StorefrontPolicy.transition(text(o,"status"),status);if("COLLECTED".equals(status))throw new IllegalArgumentException("Use Collect and pay to create the sale.");
        Instant ready=null,expires=null;if("READY".equals(status)&&text(o,"ready_at").isBlank()){ready=Instant.now();int hours=integer(one(rows(c,"SELECT pickup_hours FROM storefront.settings WHERE location_id=?",location)),"pickup_hours");expires=deadline(ready,hours);}
        execute(c,"UPDATE storefront.orders SET status=?,note=?,ready_at=COALESCE(?,ready_at),expires_at=COALESCE(?,expires_at),revision=revision+1,updated_at=now() WHERE order_id=?",status,note,ready==null?null:Timestamp.from(ready),expires==null?null:Timestamp.from(expires),id);
        if(!reserves(status))execute(c,"DELETE FROM storefront.reservations WHERE order_id=?",id);
        audit(c,user,"ORDER_"+status,id.toString());emit(c,id);queueEmail(c,id);
    }
    static void expire(Connection c,int location)throws SQLException{
        lock(c,location);for(var e:rows(c,"SELECT order_id FROM storefront.orders WHERE location_id=? AND status='READY' AND expires_at<=now() FOR UPDATE",location))
            transition(c,UUID.fromString(text(e.getAsJsonObject(),"order_id")),location,"EXPIRED","Pickup deadline passed.",0);
    }
    static void audit(Connection c,int user,String action,String detail)throws SQLException{execute(c,"INSERT INTO storefront.audit(user_id,action,detail) VALUES(?,?,?)",user==0?null:user,action,detail);}
    static void queueEmail(Connection c,UUID id)throws SQLException{
        JsonObject o=one(rows(c,"SELECT * FROM storefront.orders WHERE order_id=?",id));
        ServerEmailOutboxService.queueStorefrontOrder(c,integer(o,"location_id"),text(o,"email"),id.toString(),text(o,"status"),text(o,"expires_at"));
    }
    static JsonObject account(Connection c,JsonObject snapshot,UUID auth,String email)throws SQLException{
        JsonObject customer=customer(snapshot,auth,email),out=new JsonObject();out.add("customer",customer);out.addProperty("capturedAt",text(snapshot,"capturedAt"));
        out.add("orders",rows(c,"SELECT order_id,status,quote,total,created_at,expires_at,receipt_number,location_id FROM storefront.orders WHERE auth_id=? ORDER BY created_at DESC LIMIT 200",auth));
        out.add("quoteRequests",StorefrontQuoteRequests.customer(c,auth));
        out.add("designApprovals",StorefrontQuoteProofs.customer(c,auth));
        out.add("designApprovalHistory",StorefrontQuoteProofs.history(c,auth));
        if(integer(snapshot,"locationId")==DatabaseConfig.load().locationId())out.add("favorites",StorefrontFavorites.list(c,integer(snapshot,"locationId"),auth));
        JsonArray receipts=new JsonArray(),stores=new JsonArray();
        addAccountStore(snapshot,customer,stores,receipts);
        for(var row:rows(c,"SELECT payload FROM storefront.snapshots WHERE location_id<>?",integer(snapshot,"locationId"))){
            JsonObject other=row.getAsJsonObject().getAsJsonObject("payload");
            if(!other.has("settings"))continue;
            try{
                JsonObject linked=customer(other,auth,email);
                // A new-account placeholder is not an existing balance at another store.
                if(linked.has("balance"))addAccountStore(other,linked,stores,receipts);
            }catch(IllegalArgumentException ignored){
                JsonObject unresolved=accountStore(other);unresolved.addProperty("needsAttention",true);stores.add(unresolved);
            }
        }
        out.add("stores",stores);out.add("receipts",receipts);return out;
    }
    private static JsonObject accountStore(JsonObject snapshot){
        JsonObject store=new JsonObject();store.addProperty("locationId",integer(snapshot,"locationId"));store.addProperty("name",text(snapshot.getAsJsonObject("store"),"name"));
        store.addProperty("currency",text(snapshot.getAsJsonObject("settings"),"currency"));store.addProperty("capturedAt",text(snapshot,"capturedAt"));return store;
    }
    private static void addAccountStore(JsonObject snapshot,JsonObject customer,JsonArray stores,JsonArray receipts){
        JsonObject store=accountStore(snapshot);store.addProperty("balance",decimal(customer,"balance"));stores.add(store);
        for(var e:snapshot.getAsJsonArray("receipts"))if(text(customer,"uuid").equals(text(e.getAsJsonObject(),"customer"))){
            JsonObject receipt=e.getAsJsonObject().deepCopy();receipt.remove("customer");receipt.addProperty("locationId",integer(snapshot,"locationId"));receipt.addProperty("storeName",text(store,"name"));receipt.addProperty("currency",text(store,"currency"));receipt.addProperty("capturedAt",text(snapshot,"capturedAt"));receipts.add(receipt);
        }
    }
}
