package services;

import com.google.gson.*;
import java.sql.*;
import java.util.*;
import static services.StorefrontService.*;

/** Called only behind employee/device authentication. No browser customer route invokes this class. */
final class StorefrontAdminService {
    private StorefrontAdminService() { }
    static Map<String,Object> orderState(Connection c,int location)throws SQLException{
        return Map.of("orders",rows(c,"SELECT * FROM storefront.orders WHERE location_id=? ORDER BY created_at DESC LIMIT 300",location),
            "notifications",notificationSummary(c,location));
    }
    static JsonObject notificationSummary(Connection c,int location)throws SQLException{
        return one(rows(c,"SELECT COUNT(*) FILTER (WHERE status='FAILED') AS failed,COUNT(*) FILTER (WHERE status IN ('QUEUED','SENDING')) AS pending FROM email_outbox WHERE location_id=? AND document_type IN ('STOREFRONT_ORDER','STOREFRONT_PROOF')",location));
    }
    static Map<String,Object> run(Connection c,JsonObject body,int user,int location,String name,UUID device,LanSalesService.ApprovalConsumer approvals)throws Exception{
        if("WEBSITE_STATUS".equals(text(body,"action"))){require(c,user,"VIEW_SALES");return websiteStatus(c,location);}
        if(ServerRoleGuard.state()!=ServerRoleGuard.State.PRIMARY)throw new IllegalArgumentException("Use the active store server.");
        String action=text(body,"action");require(c,user,switch(action){case "SETTINGS","CAMPAIGN","PUBLISH","PUBLISH_ALL_ACTIVE","SET_ENABLED","PROJECT_DRAFT","PROJECT_SAVE","PROJECT_COVER","PROJECT_MEDIA_UPLOAD","PROJECT_MEDIA_DELETE","PROJECT_STATUS","PROJECT_STATE","PROJECT_SHARE","SERVICE_STATE","SERVICE_AVAILABILITY"->"COMPANY_PREFERENCES";case "QUOTE_STATE","QUOTE_STATUS","QUOTE_FILES","QUOTE_PROOF","QUOTE_LINK_ORDER"->"MANAGE_CUSTOM_ORDERS";case "LINK","LINK_SEARCH"->"CUSTOMER_ACCOUNTS";case "STATE","WEBSITE_STATUS"->"VIEW_SALES";default->"MAKE_SALE";});
        if(action.equals("LINK_SEARCH")){
            String email=StorefrontIdentityDirectory.email(text(body,"email"));
            JsonObject identity=StorefrontIdentityDirectory.byEmail(email);
            return Map.of("identity",identity,"customers",linkCandidates(c,email,location));
        }
        if(action.equals("LINK"))StorefrontIdentityDirectory.byId(UUID.fromString(text(body,"authId")),text(body,"email"));
        StorefrontRuntime.requirePrimary();
        lock(c,location);
        if(action.equals("QUOTE_STATE"))return Map.of("quotes",StorefrontQuoteRequests.staff(c,location));
        if(action.equals("QUOTE_FILES"))return Map.of("files",StorefrontQuoteRequests.files(c,location,UUID.fromString(text(body,"requestId"))));
        if(action.equals("QUOTE_LINK_ORDER")){
            JsonObject result=StorefrontQuoteRequests.linkOrder(c,location,user,body);StorefrontRuntime.requirePrimary();
            audit(c,user,action,"request="+text(body,"requestId")+"; customOrderId="+text(body,"customOrderId"));return Map.of("link",result);
        }
        if(action.equals("QUOTE_PROOF")){
            JsonObject proof=StorefrontQuoteProofs.publish(c,location,user,body);StorefrontRuntime.requirePrimary();
            audit(c,user,action,"request="+text(body,"requestId")+"; proof="+text(body,"proofId"));return Map.of("proof",proof);
        }
        if(action.equals("QUOTE_STATUS")){
            StorefrontQuoteRequests.transition(c,location,body);StorefrontRuntime.requirePrimary();
            audit(c,user,action,"request="+text(body,"requestId")+"; status="+text(body,"status"));return Map.of("ok",true);
        }
        if(action.equals("PROJECT_STATE"))return Map.of("projects",StorefrontProjects.staffState(c,location));
        if(action.equals("SERVICE_STATE"))return Map.of("services",StorefrontServiceAvailability.staff(c,location));
        if(action.equals("SERVICE_AVAILABILITY")){
            if(!body.has("available")||!body.get("available").isJsonPrimitive()||!body.getAsJsonPrimitive("available").isBoolean())
                throw new IllegalArgumentException("Choose available or unavailable.");
            StorefrontServiceAvailability.set(c,location,text(body,"slug"),body.get("available").getAsBoolean());
            StorefrontRuntime.requirePrimary();audit(c,user,action,"service="+text(body,"slug")+"; available="+body.get("available"));
            return Map.of("ok",true);
        }
        if(action.equals("PROJECT_SHARE")){
            StorefrontConfig config=StorefrontConfig.load();
            return Map.of("share",StorefrontProjects.share(c,location,UUID.fromString(text(body,"projectId")),config==null?null:config.origin()));
        }
        if(action.equals("PROJECT_DRAFT")){
            long source=body.has("sourceOrderId")?body.get("sourceOrderId").getAsLong():0;
            UUID id=StorefrontProjects.draft(c,location,source);
            StorefrontRuntime.requirePrimary();audit(c,user,action,"project="+id+"; sourceOrder="+source);
            return Map.of("ok",true,"projectId",id.toString());
        }
        if(action.equals("PROJECT_SAVE")||action.equals("PROJECT_STATUS")||action.equals("PROJECT_COVER")||action.equals("PROJECT_MEDIA_UPLOAD")||action.equals("PROJECT_MEDIA_DELETE")){
            if(action.equals("PROJECT_SAVE"))StorefrontProjects.save(c,location,body);
            else if(action.equals("PROJECT_COVER"))StorefrontProjects.cover(c,location,body);
            else if(action.equals("PROJECT_MEDIA_UPLOAD"))StorefrontProjects.uploadMedia(c,location,body);
            else if(action.equals("PROJECT_MEDIA_DELETE"))StorefrontProjects.deleteMedia(c,location,body);
            else StorefrontProjects.status(c,location,body);
            StorefrontRuntime.requirePrimary();audit(c,user,action,"project="+text(body,"projectId"));return Map.of("ok",true);
        }
        switch(action){
            case "SET_ENABLED": {
                if(!body.has("enabled")||!body.get("enabled").isJsonPrimitive()||!body.getAsJsonPrimitive("enabled").isBoolean())throw new IllegalArgumentException("Choose enabled or disabled.");
                setEnabled(c,user,location,body.get("enabled").getAsBoolean());
                break;
            }
            case "ORDERS_STATE":return orderState(c,location);
            case "STATE":return Map.of("notifications",notificationSummary(c,location),"settings",one(rows(c,"SELECT * FROM storefront.settings WHERE location_id=?",location)),"products",rows(c,"SELECT p.product_id AS id,p.name,p.price,COALESCE(w.published,false) AS published,COALESCE(w.featured,false) AS featured,COALESCE(w.description,'') AS description,w.website_price,w.promotional_price FROM products p LEFT JOIN storefront.products w ON w.product_id=p.product_id AND w.location_id=? WHERE p.is_active AND p.product_type='INVENTORY' ORDER BY p.name",location),"orders",rows(c,"SELECT * FROM storefront.orders WHERE location_id=? ORDER BY created_at DESC LIMIT 300",location),"links",rows(c,"SELECT * FROM storefront.customer_links WHERE location_id=?",location),"enrollments",rows(c,"SELECT name,email,status,note FROM storefront.enrollments WHERE location_id=? AND status<>'LINKED' ORDER BY updated_at DESC LIMIT 300",location));
            case "SETTINGS": {
                int hours=integer(body,"pickupHours");StorefrontPolicy.deadline(java.time.Instant.now(),hours);
                String currency=text(body,"currency");if(!currency.matches("[A-Z]{3}"))throw new IllegalArgumentException("Enter a three-letter currency code.");
                execute(c,"INSERT INTO storefront.settings(location_id,enabled,pickup_hours,currency,welcome) VALUES(?,?,?,?,?) ON CONFLICT(location_id) DO UPDATE SET enabled=EXCLUDED.enabled,pickup_hours=EXCLUDED.pickup_hours,currency=EXCLUDED.currency,welcome=EXCLUDED.welcome,updated_at=now()",location,body.get("enabled").getAsBoolean(),hours,currency,text(body,"welcome"));break;
            }
            case "CAMPAIGN": {
                String topic=campaignField(body,"topic",100),eyebrow=campaignField(body,"eyebrow",120),headline=campaignField(body,"headline",150),
                    description=campaignField(body,"description",300),steps=campaignField(body,"steps",200),primary=campaignField(body,"primary",60),
                    secondary=campaignField(body,"secondary",60),visual=campaignField(body,"visual",20),
                    primaryAction=campaignField(body,"primaryAction",20),secondaryAction=campaignField(body,"secondaryAction",20);
                if(!visual.equals("3D_PRINT")&&!visual.equals("EDITORIAL"))throw new IllegalArgumentException("Choose a supported campaign visual.");
                if(!java.util.Set.of("START","EXPLORE","SHOP","MADE").contains(primaryAction)
                    ||!java.util.Set.of("START","EXPLORE","SHOP","MADE").contains(secondaryAction))
                    throw new IllegalArgumentException("Choose supported campaign button destinations.");
                execute(c,"""
                    INSERT INTO storefront.settings(location_id,campaign_topic,campaign_eyebrow,campaign_headline,campaign_description,
                        campaign_steps,campaign_primary,campaign_secondary,campaign_visual,campaign_primary_action,campaign_secondary_action)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(location_id) DO UPDATE SET
                        campaign_topic=EXCLUDED.campaign_topic,campaign_eyebrow=EXCLUDED.campaign_eyebrow,
                        campaign_headline=EXCLUDED.campaign_headline,campaign_description=EXCLUDED.campaign_description,
                        campaign_steps=EXCLUDED.campaign_steps,campaign_primary=EXCLUDED.campaign_primary,
                        campaign_secondary=EXCLUDED.campaign_secondary,campaign_visual=EXCLUDED.campaign_visual,
                        campaign_primary_action=EXCLUDED.campaign_primary_action,campaign_secondary_action=EXCLUDED.campaign_secondary_action,updated_at=now()
                    """,location,topic,eyebrow,headline,description,steps,primary,secondary,visual,primaryAction,secondaryAction);
                break;
            }
            case "PUBLISH": {
                int product=integer(body,"productId");lockPublishableProduct(c,product);
                JsonObject existing=one(rows(c,"SELECT website_price,promotional_price FROM storefront.products WHERE location_id=? AND product_id=?",location,product));
                java.math.BigDecimal websitePrice=priceField(body.has("websitePrice")?body.get("websitePrice"):existing.get("website_price"),"website price");
                java.math.BigDecimal promotionalPrice=priceField(body.has("promotionalPrice")?body.get("promotionalPrice"):existing.get("promotional_price"),"promotional price");
                if(promotionalPrice!=null&&(websitePrice==null||promotionalPrice.compareTo(websitePrice)>=0))
                    throw new IllegalArgumentException("Set a regular website price above the promotional price.");
                execute(c,"INSERT INTO storefront.products(location_id,product_id,published,featured,description,website_price,promotional_price) VALUES(?,?,?,?,?,?,?) ON CONFLICT(location_id,product_id) DO UPDATE SET published=EXCLUDED.published,featured=EXCLUDED.featured,description=EXCLUDED.description,website_price=EXCLUDED.website_price,promotional_price=EXCLUDED.promotional_price,updated_at=now()",location,product,body.get("published").getAsBoolean(),body.get("featured").getAsBoolean(),text(body,"description"),websitePrice,promotionalPrice);break;
            }
            case "PUBLISH_ALL_ACTIVE": {
                int count=publishAllActive(c,user,location);
                StorefrontRuntime.requirePrimary();audit(c,user,action,"location="+location+"; published="+count);
                return Map.of("ok",true,"publishedCount",count);
            }
            case "LINK": {
                UUID auth=UUID.fromString(text(body,"authId")),customer=UUID.fromString(text(body,"customerUuid"));String email=StorefrontIdentityDirectory.email(text(body,"email"));
                linkCustomer(c,auth,customer,email,location);break;
            }
            case "TRANSITION": {
                UUID id=UUID.fromString(text(body,"orderId"));transition(c,id,location,text(body,"status"),text(body,"note"),user);break;
            }
            case "COLLECT": {var result=collect(c,body,user,location,name,device,approvals);StorefrontRuntime.requirePrimary();return result;}
            default:throw new IllegalArgumentException("Unknown storefront action.");
        }
        StorefrontRuntime.requirePrimary();audit(c,user,action,body.toString());return Map.of("ok",true);
    }
    private static String campaignField(JsonObject body,String key,int limit){
        JsonElement value=body.get(key);
        if(value==null||!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Enter campaign "+key+".");
        String result=value.getAsString().trim();
        if(result.isEmpty()||result.length()>limit)throw new IllegalArgumentException("Campaign "+key+" must contain 1 to "+limit+" characters.");
        return result;
    }
    static java.math.BigDecimal priceField(JsonElement value,String label){
        if(value==null||value.isJsonNull())return null;
        if(!value.isJsonPrimitive())throw new IllegalArgumentException("Enter a valid "+label+".");
        String input=value.getAsString().trim();if(input.isEmpty())return null;
        try{
            java.math.BigDecimal price=new java.math.BigDecimal(input);
            if(price.signum()<=0||price.scale()>2||price.precision()-price.scale()>10)
                throw new IllegalArgumentException("Enter a positive "+label+" with at most two decimal places.");
            return price;
        }catch(NumberFormatException e){throw new IllegalArgumentException("Enter a valid "+label+".");}
    }
    static void lockPublishableProduct(Connection c,int product)throws SQLException {
        // Serialize publication with archive/restore; the caller owns the transaction.
        if(rows(c,"SELECT product_id FROM products WHERE product_id=? AND is_active AND product_type='INVENTORY' FOR UPDATE",product).isEmpty())
            throw new IllegalArgumentException("Select an active inventory product.");
    }
    static int publishAllActive(Connection c,int user,int location)throws SQLException {
        require(c,user,"COMPANY_PREFERENCES");
        lock(c,location);
        var active=rows(c,"SELECT product_id FROM products WHERE is_active AND product_type='INVENTORY' ORDER BY product_id FOR UPDATE");
        int count=0;
        try(var p=c.prepareStatement("INSERT INTO storefront.products(location_id,product_id,published) VALUES(?,?,true) ON CONFLICT(location_id,product_id) DO UPDATE SET published=true,updated_at=now() WHERE NOT storefront.products.published")){
            for(var e:active){p.setInt(1,location);p.setInt(2,integer(e.getAsJsonObject(),"product_id"));p.addBatch();}
            for(int changed:p.executeBatch())if(changed>0)count+=changed;
        }
        return count;
    }
    static void unpublishArchivedProduct(Connection c,int product)throws SQLException {
        // Clear every store's publication, including on restore of legacy archived records.
        execute(c,"UPDATE storefront.products SET published=false,featured=false,updated_at=now() WHERE product_id=? AND (published OR featured)",product);
    }
    static void setEnabled(Connection c,int user,int location,boolean enabled)throws SQLException {
        require(c,user,"COMPANY_PREFERENCES");
        lock(c,location);
        execute(c,"INSERT INTO storefront.settings(location_id,enabled) VALUES(?,?) ON CONFLICT(location_id) DO UPDATE SET enabled=EXCLUDED.enabled,updated_at=now()",location,enabled);
    }
    static Map<String,Object> websiteStatus(Connection c,int location)throws SQLException {
        return Map.of("runtime",StorefrontRuntime.status(),"settings",one(rows(c,"SELECT enabled,updated_at FROM storefront.settings WHERE location_id=?",location)),
            "notifications",notificationSummary(c,location),"queue",one(rows(c,"SELECT COUNT(*) AS pending FROM storefront.events WHERE delivered_at IS NULL")));
    }
    static JsonArray linkCandidates(Connection c,String email,int location)throws SQLException {
        return rows(c,"""
            SELECT ca.customer_id AS number,ca.sync_uuid AS uuid,ca.name,ca.email,COALESCE(ca.phone,'') AS phone,
              ca.current_balance AS balance,EXISTS(SELECT 1 FROM storefront.customer_links l
                WHERE l.location_id=? AND l.customer_uuid=ca.sync_uuid) AS linked
            FROM customer_accounts ca WHERE ca.is_active AND lower(btrim(ca.email))=? ORDER BY ca.name,ca.customer_id
            """,location,email);
    }
    static void linkCustomer(Connection c,UUID auth,UUID customer,String email,int location)throws SQLException {
        lock(c,location);
        if(rows(c,"SELECT customer_id FROM customer_accounts WHERE sync_uuid=? AND is_active AND lower(btrim(email))=? FOR UPDATE",customer,email).isEmpty())
            throw new IllegalArgumentException("The selected active customer must have this verified email.");
        if(!rows(c,"SELECT auth_id FROM storefront.customer_links WHERE location_id=? AND customer_uuid=? AND auth_id<>?",location,customer,auth).isEmpty())
            throw new IllegalArgumentException("This customer record is already linked to another website account. Ask the administrator to review it.");
        execute(c,"INSERT INTO storefront.customer_links(auth_id,location_id,customer_uuid,email) VALUES(?,?,?,?) ON CONFLICT(auth_id,location_id) DO UPDATE SET customer_uuid=EXCLUDED.customer_uuid,email=EXCLUDED.email",auth,location,customer,email);
        execute(c,"UPDATE storefront.enrollments SET status='LINKED',customer_uuid=?,note='',revision=revision+1,updated_at=now() WHERE auth_id=? AND location_id=? AND (status<>'LINKED' OR customer_uuid IS DISTINCT FROM ?)",customer,auth,location,customer);
    }
    static Map<String,Object> collect(Connection c,JsonObject body,int user,int location,String name,UUID device,LanSalesService.ApprovalConsumer approvals)throws Exception {
        lock(c,location);
                UUID id=UUID.fromString(text(body,"orderId"));JsonObject o=one(rows(c,"SELECT * FROM storefront.orders WHERE order_id=? AND location_id=? FOR UPDATE",id,location));
                if(o.entrySet().isEmpty())throw new IllegalArgumentException("Order not found.");
                if("COLLECTED".equals(text(o,"status")))return Map.of("saleId",integer(o,"sale_id"),"receiptNumber",text(o,"receipt_number"));
                StorefrontPolicy.transition(text(o,"status"),"COLLECTED");
                if(!text(o,"expires_at").isBlank()&&!java.time.Instant.parse(text(o,"expires_at")).isAfter(java.time.Instant.now()))throw new IllegalArgumentException("The pickup deadline has passed.");
                JsonObject q=o.getAsJsonObject("quote"),request=new JsonObject();JsonArray lines=new JsonArray();
                for(var e:q.getAsJsonArray("lines")){var line=e.getAsJsonObject();var r=new JsonObject();r.addProperty("productId",integer(line,"id"));r.addProperty("quantity",integer(line,"quantity"));r.add("unitPrice",line.get("price"));r.addProperty("discountPercent",0);lines.add(r);}
                request.add("lines",lines);request.addProperty("paymentMethod",text(body,"paymentMethod"));if("ACCOUNT".equals(text(body,"paymentMethod")))throw new IllegalArgumentException("Website orders must be paid in store.");
                request.add("cashCollected",body.get("cashCollected"));request.addProperty("paymentReference",text(body,"paymentReference"));request.addProperty("saleDiscountPercent",decimal(q,"discountPercent"));request.addProperty("applyCustomerDiscount",false);
                var customer=one(rows(c,"SELECT customer_id FROM customer_accounts WHERE sync_uuid=? AND is_active",UUID.fromString(text(o,"customer_uuid"))));if(customer.entrySet().isEmpty())throw new IllegalArgumentException("Customer account is unavailable.");request.addProperty("customerId",integer(customer,"customer_id"));
                for(var e:q.getAsJsonArray("lines")){
                    var line=e.getAsJsonObject();var stock=one(rows(c,"SELECT quantity_on_hand AS qty FROM inventory WHERE location_id=? AND product_id=? FOR UPDATE",location,integer(line,"id")));
                    if(integer(stock,"qty")<integer(line,"quantity"))throw new IllegalArgumentException("Resolve the stock shortage before collecting this order.");
                }
                execute(c,"DELETE FROM storefront.reservations WHERE order_id=?",id);
                var result=LanSalesService.checkoutLockedStorefront(c,request,device,user,name,location,approvals,q);
                execute(c,"UPDATE storefront.orders SET status='COLLECTED',sale_id=?,receipt_number=?,revision=revision+1,updated_at=now() WHERE order_id=?",result.get("saleId"),result.get("receiptNumber"),id);emit(c,id);queueEmail(c,id);audit(c,user,"ORDER_COLLECTED",id.toString());return result;
    }
    static void require(Connection c,int user,String permission)throws SQLException{
        if(rows(c,"SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND u.is_active AND p.permission_key=?",user,permission).isEmpty())throw new SecurityException("This action requires "+permission+".");
    }
    static void protectReservations(Connection c,int location,int product,int quantity)throws SQLException{
        var held=one(rows(c,"SELECT COALESCE(SUM(quantity),0) AS qty FROM storefront.reservations WHERE location_id=? AND product_id=?",location,product));
        if(integer(held,"qty")>0){var stock=one(rows(c,"SELECT quantity_on_hand AS qty FROM inventory WHERE location_id=? AND product_id=? FOR UPDATE",location,product));if(integer(stock,"qty")-integer(held,"qty")<quantity)throw new IllegalArgumentException("This product is reserved for online pickup orders.");}
    }
}
