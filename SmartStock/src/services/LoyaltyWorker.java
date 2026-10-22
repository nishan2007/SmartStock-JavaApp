package services;

import com.google.gson.*;
import data.DB;
import data.DatabaseConfig;
import java.math.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

/** Durable local document delivery, independent of cloud availability. */
public final class LoyaltyWorker {
    private static boolean started;
    private LoyaltyWorker() { }
    public static synchronized void start() {
        if(started)return;started=true;
        var executor=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"smartstock-loyalty");t.setDaemon(true);return t;});
        executor.scheduleWithFixedDelay(LoyaltyWorker::runSafely,3,10,TimeUnit.SECONDS);
    }
    public static void runSafely() {
        if(DatabaseConfig.load().mode()!=data.DatabaseMode.SERVER||ServerRoleGuard.blocksMutations())return;
        Integer location=DatabaseConfig.load().locationId();if(location==null)return;
        try(var c=DB.getConnection()) {
            c.setAutoCommit(false);
            try {
                var s=LoyaltyService.settings(c);
                if(s.authorityLocationId()!=null&&s.authorityLocationId()!=location) {
                    var response=LoyaltyPeer.call(s.authorityLocationId(),location,"SETTINGS",new JsonObject());
                    LoyaltyService.cacheSettings(c,LoyaltyService.JSON.fromJson(response.get("settings"),LoyaltyPolicy.Settings.class),null);
                }
                c.commit();
            }catch(Exception e){c.rollback();}
            reconcileIntents(c,location);
            synchronizeMerges(c,location);
            for(int i=0;i<25;i++) {
                UUID document=null;
                try {
                    try(var p=c.prepareStatement("SELECT document_uuid FROM loyalty_documents WHERE source_location_id=? AND revision>delivered_revision ORDER BY updated_at LIMIT 1 FOR UPDATE SKIP LOCKED")){p.setInt(1,location);try(var r=p.executeQuery()){if(r.next())document=(UUID)r.getObject(1);}}
                    if(document==null){c.rollback();break;}
                    var payload=payload(c,document);var settings=LoyaltyService.settings(c);
                    if(settings.authorityLocationId()==null)throw new SQLException("No loyalty authority configured.");
                    if(settings.authorityLocationId()==location)LoyaltyService.apply(c,payload,location);
                    else LoyaltyPeer.call(settings.authorityLocationId(),location,"APPLY",payload);
                    try(var p=c.prepareStatement("UPDATE loyalty_documents SET delivered_revision=?,last_error=NULL,attempts=0 WHERE document_uuid=?")){p.setLong(1,payload.get("revision").getAsLong());p.setObject(2,document);p.executeUpdate();}
                    c.commit();
                }catch(Exception failure) {
                    c.rollback();
                    if(document!=null)try(var p=c.prepareStatement("UPDATE loyalty_documents SET attempts=attempts+1,last_error='Loyalty synchronization pending; check authority connectivity.',updated_at=now() WHERE document_uuid=?")){p.setObject(1,document);p.executeUpdate();c.commit();}
                    break;
                }
            }
        }catch(Exception ignored){/* A retryable loyalty outage never interrupts register operations. */}
    }
    @FunctionalInterface interface PeerCall { JsonObject call(int authority,int origin,String action,JsonObject body)throws Exception; }
    static void reconcileIntents(Connection c,int location)throws Exception {reconcileIntents(c,location,LoyaltyPeer::call);}
    static void reconcileIntents(Connection c,int location,PeerCall peer)throws Exception {
        var ids=new ArrayList<UUID>();
        try(var p=c.prepareStatement("SELECT document_uuid FROM loyalty_remote_intents WHERE source_location_id=? ORDER BY created_at LIMIT 25")){p.setInt(1,location);try(var r=p.executeQuery()){while(r.next())ids.add((UUID)r.getObject(1));}}
        c.commit();
        for(UUID id:ids)try {
            boolean locked;
            try(var p=c.prepareStatement("SELECT pg_try_advisory_xact_lock(hashtextextended(?::text,1280262476))")){p.setString(1,id.toString());try(var r=p.executeQuery()){r.next();locked=r.getBoolean(1);}}
            if(!locked){c.rollback();continue;}
            int authority;
            try(var p=c.prepareStatement("SELECT authority_location_id FROM loyalty_remote_intents WHERE document_uuid=? FOR UPDATE")){p.setObject(1,id);try(var r=p.executeQuery()){if(!r.next()){c.rollback();continue;}authority=r.getInt(1);}}
            boolean committed;
            try(var p=c.prepareStatement("SELECT 1 FROM loyalty_documents WHERE document_uuid=? AND requested_points>0")){p.setObject(1,id);try(var r=p.executeQuery()){committed=r.next();}}
            if(!committed){var b=new JsonObject();b.addProperty("documentUuid",id.toString());peer.call(authority,location,"RELEASE",b);}
            try(var p=c.prepareStatement("DELETE FROM loyalty_remote_intents WHERE document_uuid=?")){p.setObject(1,id);p.executeUpdate();}c.commit();
        }catch(Exception e){c.rollback();}
    }
    static void synchronizeMerges(Connection c,int location)throws Exception {
        var policy=LoyaltyService.settings(c);if(policy.authorityLocationId()==null){c.rollback();return;}
        var merges=new ArrayList<JsonObject>();
        try(var p=c.prepareStatement("SELECT source_uuid,target_uuid FROM customer_account_merges ORDER BY source_customer_id DESC");var r=p.executeQuery()){while(r.next()){var b=new JsonObject();b.addProperty("sourceUuid",r.getObject(1).toString());b.addProperty("targetUuid",r.getObject(2).toString());merges.add(b);}}
        c.commit();
        for(var b:merges)try{
            if(policy.authorityLocationId()==location)LoyaltyService.merge(c,UUID.fromString(b.get("sourceUuid").getAsString()),UUID.fromString(b.get("targetUuid").getAsString()));
            else LoyaltyPeer.call(policy.authorityLocationId(),location,"MERGE",b);
            c.commit();
        }catch(Exception e){c.rollback();break;}
    }
    static LoyaltyPolicy.Settings snapshot(String json) {
        var o=JsonParser.parseString(json).getAsJsonObject();
        return new LoyaltyPolicy.Settings(o.get("version").getAsLong(),o.get("enabled").getAsBoolean(),o.get("authority_location_id").isJsonNull()?null:o.get("authority_location_id").getAsInt(),o.get("spend_per_award").getAsBigDecimal(),o.get("points_per_award").getAsLong(),o.get("point_value").getAsBigDecimal(),o.get("minimum_redemption").getAsLong(),o.get("redemption_cap_percent").getAsBigDecimal());
    }
    record Line(long id,BigDecimal eligible,BigDecimal remaining) { }
    static List<Line> lines(Connection c,String kind,long id)throws SQLException {
        String sql="SALE".equals(kind)?"""
            SELECT si.sale_item_id,CASE WHEN si.loyalty_eligible THEN COALESCE(si.loyalty_net_amount,si.unit_price*si.quantity) ELSE 0 END,
              CASE WHEN si.quantity>0 THEN GREATEST(0,si.quantity-COALESCE((SELECT sum(sri.quantity) FROM sale_return_items sri JOIN sale_returns sr ON sr.return_id=sri.return_id WHERE sri.sale_item_id=si.sale_item_id AND sr.sale_id=si.sale_id),0))::numeric/si.quantity ELSE 0 END
            FROM sale_items si LEFT JOIN products p ON p.product_id=si.product_id WHERE si.sale_id=? ORDER BY si.sale_item_id
            """:"""
            SELECT custom_order_line_id,CASE WHEN loyalty_eligible THEN line_total ELSE 0 END,
              CASE WHEN line_total>0 THEN GREATEST(0,line_total-COALESCE(returned_amount,0))/line_total ELSE 0 END
            FROM custom_order_lines WHERE custom_order_id=? ORDER BY sort_order,custom_order_line_id
            """;
        var result=new ArrayList<Line>();try(var p=c.prepareStatement(sql)){p.setLong(1,id);try(var r=p.executeQuery()){while(r.next())result.add(new Line(r.getLong(1),r.getBigDecimal(2).max(BigDecimal.ZERO),r.getBigDecimal(3)));}}
        return result;
    }
    static void captureLines(Connection c,UUID doc)throws SQLException {
        String kind;long id,redeemed;LoyaltyPolicy.Settings policy;
        try(var p=c.prepareStatement("SELECT document_type,local_id,requested_points,settings FROM loyalty_documents WHERE document_uuid=?")){p.setObject(1,doc);try(var r=p.executeQuery()){if(!r.next())return;kind=r.getString(1);id=r.getLong(2);redeemed=r.getLong(3);policy=snapshot(r.getString(4));}}
        var lines=lines(c,kind,id);BigDecimal sum=lines.stream().map(Line::eligible).reduce(BigDecimal.ZERO,BigDecimal::add);
        long earned=LoyaltyPolicy.earn(policy,sum);BigDecimal cumulative=BigDecimal.ZERO;long priorEarn=0,priorRedeem=0;
        for(var line:lines) {
            cumulative=cumulative.add(line.eligible());
            long uptoEarn=allocate(earned,cumulative,sum),uptoRedeem=allocate(redeemed,cumulative,sum);
            try(var p=c.prepareStatement("INSERT INTO loyalty_document_lines(document_uuid,line_id,eligible_amount,earned_points,redeemed_points) VALUES(?,?,?,?,?) ON CONFLICT(document_uuid,line_id) DO NOTHING")){p.setObject(1,doc);p.setLong(2,line.id());p.setBigDecimal(3,line.eligible());p.setLong(4,uptoEarn-priorEarn);p.setLong(5,uptoRedeem-priorRedeem);p.executeUpdate();}
            priorEarn=uptoEarn;priorRedeem=uptoRedeem;
        }
    }
    static long allocate(long points,BigDecimal part,BigDecimal total){return total.signum()==0?0:part.multiply(BigDecimal.valueOf(points)).divide(total,0,RoundingMode.DOWN).longValueExact();}
    static JsonObject payload(Connection c,UUID doc)throws SQLException {
        var b=new JsonObject();String kind;long id;boolean paid=false,cancelled=false,complete=false;
        try(var p=c.prepareStatement("SELECT customer_uuid,settings_version,revision,document_type,local_id FROM loyalty_documents WHERE document_uuid=?")){p.setObject(1,doc);try(var r=p.executeQuery()){if(!r.next())throw new SQLException("Unknown loyalty document.");b.addProperty("documentUuid",doc.toString());b.addProperty("customerUuid",r.getObject(1).toString());b.addProperty("settingsVersion",r.getLong(2));b.addProperty("revision",r.getLong(3));kind=r.getString(4);id=r.getLong(5);}}
        if("SALE".equals(kind))try(var p=c.prepareStatement("SELECT payment_status,status FROM sales WHERE sale_id=?")){p.setLong(1,id);try(var r=p.executeQuery()){if(r.next()){paid="PAID".equals(r.getString(1));cancelled=List.of("VOID","VOIDED","CANCELLED").contains(r.getString(2));complete=true;}}}
        else try(var p=c.prepareStatement("SELECT CASE WHEN co.invoice_billed THEN COALESCE((SELECT i.payment_status='PAID' AND i.status<>'CANCELLED' FROM invoices i WHERE i.invoice_id=co.source_invoice_id),false) ELSE co.balance_due=0 END,co.status,co.pricing_state,COALESCE((SELECT i.status='CANCELLED' FROM invoices i WHERE co.invoice_billed AND i.invoice_id=co.source_invoice_id),false) FROM custom_orders co WHERE co.custom_order_id=?")){p.setLong(1,id);try(var r=p.executeQuery()){if(r.next()){paid=r.getBoolean(1);cancelled="CANCELLED".equals(r.getString(2))||r.getBoolean(4);complete=List.of("COMPLETED","READY","DELIVERED").contains(r.getString(2))&&"FINAL".equals(r.getString(3));}}}
        // Pending-price custom orders are captured by finalization, not from provisional prices.
        boolean readyForCapture="SALE".equals(kind);
        if(!readyForCapture)try(var p=c.prepareStatement("SELECT pricing_state FROM custom_orders WHERE custom_order_id=?")){p.setLong(1,id);try(var r=p.executeQuery()){readyForCapture=r.next()&&"FINAL".equals(r.getString(1));}}
        if(readyForCapture)captureLines(c,doc);
        var remaining=new HashMap<Long,BigDecimal>();for(var line:lines(c,kind,id))remaining.put(line.id(),line.remaining());
        long surviving=0,restored=0;BigDecimal refundedEligible=BigDecimal.ZERO,totalEligible=BigDecimal.ZERO;
        try(var p=c.prepareStatement("SELECT line_id,earned_points,redeemed_points,eligible_amount FROM loyalty_document_lines WHERE document_uuid=? ORDER BY line_id")){p.setObject(1,doc);try(var r=p.executeQuery()){while(r.next()){
            BigDecimal fraction=cancelled?BigDecimal.ZERO:remaining.getOrDefault(r.getLong(1),BigDecimal.ZERO);
            totalEligible=totalEligible.add(r.getBigDecimal(4));refundedEligible=refundedEligible.add(r.getBigDecimal(4).multiply(BigDecimal.ONE.subtract(fraction)));
            surviving+=r.getLong(2)-BigDecimal.ONE.subtract(fraction).multiply(BigDecimal.valueOf(r.getLong(2))).setScale(0,RoundingMode.DOWN).longValueExact();
            restored+=BigDecimal.ONE.subtract(fraction).multiply(BigDecimal.valueOf(r.getLong(3))).setScale(0,RoundingMode.DOWN).longValueExact();
        }}}
        b.addProperty("earnedPoints",paid&&complete&&!cancelled?surviving:0);b.addProperty("pendingPoints",!cancelled&&!(paid&&complete)?surviving:0);b.addProperty("restoredPoints",restored);
        b.addProperty("restorationFraction",cancelled?BigDecimal.ONE:totalEligible.signum()==0?BigDecimal.ZERO:refundedEligible.divide(totalEligible,18,RoundingMode.DOWN));
        return b;
    }
}
