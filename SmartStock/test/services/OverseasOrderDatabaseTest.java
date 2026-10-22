package services;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Explicit opt-in. Never connects to a store database or reads store credentials. */
class OverseasOrderDatabaseTest {
    private static final String URL="jdbc:postgresql://127.0.0.1:55443/postgres";
    private Connection c;
    private final OverseasOrderService.Actor actor=new OverseasOrderService.Actor(900001,"Order tester",900001,
            "00000000-0000-0000-0000-000000000001","Test register",Set.of("OVERSEAS_ORDERS_VIEW","OVERSEAS_ORDERS_QUOTE",
            "OVERSEAS_ORDERS_PAYMENT","OVERSEAS_ORDERS_SHIP","OVERSEAS_ORDERS_COLLECT","OVERSEAS_ORDERS_ADJUST","CUSTOM_ORDER_REFUND_APPROVAL"),false);

    @BeforeAll static void schema()throws Exception {
        String selected=System.getProperty("smartstock.overseas.test.jdbc","");assumeTrue(!selected.isBlank());
        if(!selected.equals(URL))throw new IllegalArgumentException("Use the isolated overseas test cluster on port 55443.");
        try(Connection c=DriverManager.getConnection(URL,"overseas_test","")) {
            try(var p=c.prepareStatement("SELECT to_regclass('public.overseas_orders') IS NOT NULL");var r=p.executeQuery()){r.next();if(r.getBoolean(1)){SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261021120000_overseas_orders.sql"));SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261021130000_overseas_sellers.sql"));SchemaContractService.ensureOverseasCashUpgrade(c);return;}}
            SchemaContractService.installLocalBaseline(c);
        }
    }
    @BeforeEach void open()throws Exception {
        c=DriverManager.getConnection(URL,"overseas_test","");c.setAutoCommit(false);
        try(var s=c.createStatement()) {
            s.execute("SET search_path=public");
            s.execute("INSERT INTO locations(location_id,name) VALUES(900001,'Overseas test store') ON CONFLICT DO NOTHING");
            s.execute("INSERT INTO users(user_id,username,full_name) VALUES(900001,'overseas_test','Order tester') ON CONFLICT DO NOTHING");
        }
        ServerRequestIdentity.bind(actor.userId(),actor.locationId(),"Overseas test store",actor.name(),actor.deviceId(),actor.deviceName());
    }
    @AfterEach void close()throws Exception {ServerRequestIdentity.clear();if(c!=null){c.rollback();c.close();}}
    private JsonObject execute(String action,JsonObject detail,JsonObject extra)throws Exception {
        JsonObject b=extra==null?new JsonObject():extra.deepCopy();b.addProperty("action",action);b.addProperty("requestId",UUID.randomUUID().toString());
        if(detail!=null){JsonObject o=detail.getAsJsonObject("order");b.add("id",o.get("overseas_order_id"));b.add("revision",o.get("revision"));}
        return OverseasOrderService.execute(c,actor,b);
    }
    private JsonObject draft()throws Exception {
        JsonObject line=new JsonObject();line.addProperty("description","Customer shoes");line.addProperty("quantity",2);line.addProperty("unit_price","1000");line.addProperty("usd_cost","0.01");line.addProperty("exchange_rate","1");
        line.addProperty("product_url","https://example.com/shoes");JsonArray lines=new JsonArray();lines.add(line);
        JsonObject o=new JsonObject();o.addProperty("customer_name","Overseas test customer");o.addProperty("customer_phone","6"+String.format("%06d",java.util.concurrent.ThreadLocalRandom.current().nextInt(1000000)));o.addProperty("shipping","100");o.add("lines",lines);
        JsonObject b=new JsonObject();b.add("order",o);return execute("SAVE",null,b);
    }
    private JsonObject issued()throws Exception{return execute("ISSUE",draft(),null);}
    private JsonObject quantities(JsonObject detail,int arrived,int collected,boolean collection)throws Exception {
        JsonObject l=new JsonObject();l.add("line_id",detail.getAsJsonObject("order").getAsJsonArray("lines").get(0).getAsJsonObject().get("line_id"));
        if(collection)l.addProperty("collected",collected);else{l.addProperty("purchased",2);l.addProperty("shipped",2);l.addProperty("arrived",arrived);}
        JsonArray lines=new JsonArray();lines.add(l);JsonObject b=new JsonObject();b.add("lines",lines);return execute(collection?"COLLECT":"SHIPMENT",detail,b);
    }
    private JsonObject payment(String amount){JsonObject p=new JsonObject();p.addProperty("amount",amount);p.addProperty("method","CARD");p.addProperty("reference","test terminal reference");return p;}

    @Test void draftAndIssuedQuoteUseManualGydPricesAndNoPayment()throws Exception {
        JsonObject d=issued(),o=d.getAsJsonObject("order");assertEquals(new BigDecimal("2100.00"),o.get("total").getAsBigDecimal());
        assertTrue(o.get("number").getAsString().startsWith("OS-"));assertEquals(0,d.getAsJsonArray("payments").size());
        try(var p=c.prepareStatement("SELECT COUNT(*) FROM custom_order_lines WHERE custom_order_id=?")){p.setLong(1,o.get("custom_order_id").getAsLong());try(var r=p.executeQuery()){r.next();assertEquals(0,r.getInt(1));}}
    }
    @Test void optionalExtraShippingDefaultsToZeroForMissingNullAndBlankAmounts()throws Exception {
        JsonObject d=draft();
        for(String mode:List.of("missing","null","blank")) {
            JsonObject order=d.getAsJsonObject("order").deepCopy();
            if(mode.equals("missing"))order.remove("shipping");else if(mode.equals("null"))order.add("shipping",JsonNull.INSTANCE);else order.addProperty("shipping","");
            JsonObject b=new JsonObject();b.add("order",order);d=execute("SAVE",d,b);
            assertEquals(new BigDecimal("2000.00"),d.getAsJsonObject("order").get("total").getAsBigDecimal());
            assertEquals(0,d.getAsJsonObject("order").get("shipping").getAsBigDecimal().signum());
        }
        d=execute("ISSUE",d,null);var slip=Receipt.OverseasOrderDocuments.build(d,null);
        assertTrue(slip.orderNotes().contains("Item prices include shipping."));
        assertTrue(slip.lines().stream().noneMatch(l->l.itemName().equals("Extra shipping")));
    }
    @Test void phoneLookupFindsExistingAccountAcrossFormatsAndBlocksDuplicateDraft()throws Exception {
        JsonObject issued=issued(),o=issued.getAsJsonObject("order");String phone=o.get("customer_phone").getAsString();int id=o.get("customer_id").getAsInt();
        for(String format:List.of(phone,phone.substring(4),"00"+phone.substring(1),phone.substring(0,4)+" "+phone.substring(4,7)+"-"+phone.substring(7))) {
            JsonObject match=OverseasOrderService.customerPhone(c,format);assertTrue(match.get("reserved").getAsBoolean());
            assertTrue(match.getAsJsonArray("customers").asList().stream().anyMatch(e->e.getAsJsonObject().get("customerId").getAsInt()==id));
        }
        JsonObject input=o.deepCopy();input.remove("customer_id");JsonObject b=new JsonObject();b.add("order",input);
        assertThrows(SQLException.class,()->execute("SAVE",null,b));
        assertFalse(OverseasOrderService.customerPhone(c,"705").get("validPhone").getAsBoolean());
        try(var p=c.prepareStatement("UPDATE customer_accounts SET is_active=false WHERE customer_id=?")){p.setInt(1,id);p.executeUpdate();}
        assertFalse(OverseasOrderService.customerPhone(c,phone).getAsJsonArray("customers").get(0).getAsJsonObject().get("active").getAsBoolean());
    }
    @Test void paymentOrderedAndArrivalProgressionKeepDestinationStoreAndSeller()throws Exception {
        JsonObject d=issued(),o=d.getAsJsonObject("order");
        assertEquals("WAITING_FOR_PAYMENT",o.getAsJsonArray("lines").get(0).getAsJsonObject().get("tracking").getAsString());
        assertEquals("Overseas test store",o.get("store_name").getAsString());
        d=execute("PAYMENT",d,payment("1"));assertEquals("PROCESSING",d.getAsJsonObject("order").getAsJsonArray("lines").get(0).getAsJsonObject().get("tracking").getAsString());
        JsonObject edit=new JsonObject(),input=d.getAsJsonObject("order").deepCopy();input.getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("seller","Amazon");edit.add("order",input);edit.addProperty("reason","Record seller");d=execute("SAVE",d,edit);
        JsonObject sellers=execute("SELLERS",null,null);assertTrue(sellers.getAsJsonArray("sellers").asList().stream().anyMatch(e->e.getAsString().equals("Amazon")));
        JsonObject update=new JsonObject();JsonArray ids=new JsonArray();ids.add(d.getAsJsonObject("order").getAsJsonArray("lines").get(0).getAsJsonObject().get("line_id"));update.add("lineIds",ids);update.addProperty("stage","ORDERED");
        d=execute("TRACK",d,update);assertEquals("ORDERED",d.getAsJsonObject("order").getAsJsonArray("lines").get(0).getAsJsonObject().get("tracking").getAsString());
        update.addProperty("stage","ARRIVED");d=execute("TRACK",d,update);assertEquals("ARRIVED",d.getAsJsonObject("order").getAsJsonArray("lines").get(0).getAsJsonObject().get("tracking").getAsString());
        var slip=Receipt.OverseasOrderDocuments.build(d,null);assertEquals("Overseas test store",slip.locationName());assertTrue(slip.lines().get(0).details().contains("Seller: Amazon"));
        JsonObject query=new JsonObject();query.addProperty("action","SELLERS");var otherStore=new OverseasOrderService.Actor(actor.userId(),actor.name(),900002,actor.deviceId(),actor.deviceName(),actor.permissions(),false);
        assertEquals(0,OverseasOrderService.execute(c,otherStore,query).getAsJsonArray("sellers").size());
    }
    @Test void partialPickupCanLeaveDebtButFinalPickupRequiresFullPayment()throws Exception {
        JsonObject d=quantities(issued(),2,0,false);d=execute("PAYMENT",d,payment("1"));d=quantities(d,2,1,true);
        assertEquals(new BigDecimal("2099.00"),d.getAsJsonObject("order").get("balance").getAsBigDecimal());
        JsonObject partial=d;assertThrows(IllegalArgumentException.class,()->quantities(partial,2,2,true));
        d=execute("PAYMENT",d,payment("2099"));d=quantities(d,2,2,true);assertEquals("COMPLETED",d.getAsJsonObject("order").get("state").getAsString());
    }
    @Test void rejectedCombinedPaymentAndCollectionRollBackTogether()throws Exception {
        JsonObject d=quantities(issued(),2,0,false);JsonObject b=payment("1");JsonObject l=new JsonObject();l.add("line_id",d.getAsJsonObject("order").getAsJsonArray("lines").get(0).getAsJsonObject().get("line_id"));l.addProperty("collected",2);JsonArray ls=new JsonArray();ls.add(l);b.add("lines",ls);
        Savepoint savepoint=c.setSavepoint();assertThrows(IllegalArgumentException.class,()->execute("COLLECT",d,b));c.rollback(savepoint);
        JsonObject fresh=execute("DETAIL",d,null);assertEquals(0,fresh.getAsJsonArray("payments").size());assertEquals(0,fresh.getAsJsonObject("order").get("paid").getAsBigDecimal().signum());
    }
    @Test void duplicatePaymentIsAppliedOnceAndStaleEditsAreRejected()throws Exception {
        JsonObject d=issued(),b=payment("100");JsonObject o=d.getAsJsonObject("order");b.addProperty("action","PAYMENT");b.addProperty("requestId",UUID.randomUUID().toString());b.add("id",o.get("overseas_order_id"));b.add("revision",o.get("revision"));
        JsonObject first=OverseasOrderService.execute(c,actor,b),second=OverseasOrderService.execute(c,actor,b);
        assertEquals(first,second);assertEquals(1,second.getAsJsonArray("payments").size());
        assertThrows(IllegalArgumentException.class,()->execute("PAYMENT",d,payment("100")));
        b.addProperty("amount","101");assertThrows(IllegalArgumentException.class,()->OverseasOrderService.execute(c,actor,b));
    }
    @Test void permissionStoreAndPaymentBoundariesAreServerEnforced()throws Exception {
        JsonObject d=issued();JsonObject b=payment("1");b.addProperty("action","PAYMENT");b.addProperty("requestId",UUID.randomUUID().toString());b.add("id",d.getAsJsonObject("order").get("overseas_order_id"));b.add("revision",d.getAsJsonObject("order").get("revision"));
        var viewer=new OverseasOrderService.Actor(actor.userId(),actor.name(),actor.locationId(),actor.deviceId(),actor.deviceName(),Set.of("OVERSEAS_ORDERS_VIEW"),false);
        assertThrows(SecurityException.class,()->OverseasOrderService.execute(c,viewer,b));
        var web=new OverseasOrderService.Actor(actor.userId(),actor.name(),actor.locationId(),actor.deviceId(),actor.deviceName(),actor.permissions(),true);
        assertThrows(SecurityException.class,()->OverseasOrderService.execute(c,web,b));
        var otherStore=new OverseasOrderService.Actor(actor.userId(),actor.name(),900002,actor.deviceId(),actor.deviceName(),actor.permissions(),false);
        assertThrows(IllegalArgumentException.class,()->OverseasOrderService.execute(c,otherStore,b));
        JsonObject read=b.deepCopy();read.addProperty("action","DETAIL");JsonObject redacted=OverseasOrderService.execute(c,viewer,read);assertFalse(redacted.getAsJsonObject("order").getAsJsonArray("lines").get(0).getAsJsonObject().has("usd_cost"));
    }
    @Test void overpaymentUnarrivedPickupAndUnapprovedPriceChangesAreRejected()throws Exception {
        JsonObject d=issued();assertThrows(SQLException.class,()->execute("PAYMENT",d,payment("2101")));
        assertThrows(IllegalArgumentException.class,()->quantities(d,0,1,true));
        JsonObject paid=execute("PAYMENT",d,payment("100")),edit=new JsonObject();edit.add("order",paid.get("order").deepCopy());
        var quoteOnly=new OverseasOrderService.Actor(actor.userId(),actor.name(),actor.locationId(),actor.deviceId(),actor.deviceName(),Set.of("OVERSEAS_ORDERS_QUOTE"),false);
        edit.addProperty("action","SAVE");edit.addProperty("requestId",UUID.randomUUID().toString());edit.add("id",paid.getAsJsonObject("order").get("overseas_order_id"));edit.add("revision",paid.getAsJsonObject("order").get("revision"));
        assertThrows(SecurityException.class,()->OverseasOrderService.execute(c,quoteOnly,edit));
    }
    @Test void refundPreservesLedgerAndCancellationNeedsAReasonAndRefund()throws Exception {
        JsonObject d=execute("PAYMENT",issued(),payment("100")),reason=new JsonObject();reason.addProperty("reason","Customer cancelled");JsonObject paid=d;
        assertThrows(IllegalArgumentException.class,()->execute("CANCEL",paid,reason));
        JsonObject refund=payment("100");refund.addProperty("reason","Customer cancelled");d=execute("REFUND",d,refund);
        assertEquals(2,d.getAsJsonArray("payments").size());d=execute("CANCEL",d,reason);assertEquals("CANCELLED",d.getAsJsonObject("order").get("state").getAsString());assertEquals(0,d.getAsJsonObject("order").get("balance").getAsBigDecimal().signum());
    }
    @Test void concurrentDuplicatePaymentsCommitExactlyOnce()throws Exception {
        JsonObject d=issued(),b=payment("100");b.addProperty("action","PAYMENT");b.addProperty("requestId",UUID.randomUUID().toString());b.add("id",d.getAsJsonObject("order").get("overseas_order_id"));b.add("revision",d.getAsJsonObject("order").get("revision"));c.commit();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<JsonObject> task=()->{start.await();try(Connection other=DriverManager.getConnection(URL,"overseas_test","")){
                other.setAutoCommit(false);ServerRequestIdentity.bind(actor.userId(),actor.locationId(),"Test store",actor.name(),actor.deviceId(),actor.deviceName());
                try{JsonObject result=OverseasOrderService.execute(other,actor,b);other.commit();return result;}catch(Exception e){other.rollback();throw e;}finally{ServerRequestIdentity.clear();}
            }};
            var first=pool.submit(task);var second=pool.submit(task);start.countDown();assertEquals(first.get(15,java.util.concurrent.TimeUnit.SECONDS),second.get(15,java.util.concurrent.TimeUnit.SECONDS));
            JsonObject fresh=execute("DETAIL",d,null);assertEquals(1,fresh.getAsJsonArray("payments").size());assertEquals(new BigDecimal("100.00"),fresh.getAsJsonObject("order").get("paid").getAsBigDecimal());
        }finally{pool.shutdownNow();}
    }
    @Test void oldReceiptRetainsOriginalTotalsAndScreenshotAccessIsScoped()throws Exception {
        JsonObject d=execute("PAYMENT",issued(),payment("100"));JsonObject first=d.getAsJsonArray("payments").get(0).getAsJsonObject();
        assertEquals(new BigDecimal("2000.00"),first.getAsJsonObject("overseas_receipt").get("balance").getAsBigDecimal());
        assertFalse(first.getAsJsonObject("overseas_receipt").getAsJsonArray("lines").get(0).getAsJsonObject().has("usd_cost"));
        d=execute("PAYMENT",d,payment("1000"));var document=Receipt.OverseasOrderDocuments.build(d,first);assertEquals(new BigDecimal("2000.00"),document.balanceDue());
        JsonObject b=new JsonObject();b.addProperty("reference","asset:"+UUID.randomUUID());JsonObject selected=d;
        assertThrows(SecurityException.class,()->execute("IMAGE",selected,b));
    }
    @Test void cloudMirrorMigrationRevokesBrowserRolesAndEnablesRls()throws Exception {
        try(var s=c.createStatement()) {
            s.execute("DO $$ BEGIN IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon; END IF; IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated; END IF; IF NOT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='service_role') THEN CREATE ROLE service_role BYPASSRLS; END IF; END $$");
        }
        SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261021120000_overseas_orders.sql"));
        for(String table:List.of("overseas_orders","overseas_order_lines","overseas_order_images","overseas_order_events","overseas_order_requests")) {
            try(var p=c.prepareStatement("SELECT relrowsecurity,has_table_privilege('anon',oid,'SELECT'),has_table_privilege('authenticated',oid,'SELECT'),has_table_privilege('service_role',oid,'SELECT') FROM pg_class WHERE oid=?::regclass")) {
                p.setString(1,"public."+table);try(var r=p.executeQuery()){assertTrue(r.next());assertTrue(r.getBoolean(1));assertFalse(r.getBoolean(2));assertFalse(r.getBoolean(3));assertTrue(r.getBoolean(4));}
            }
        }
        Savepoint savepoint=c.setSavepoint();try(var s=c.createStatement()){s.execute("SET LOCAL ROLE anon");assertThrows(SQLException.class,()->s.executeQuery("SELECT * FROM public.overseas_orders"));}finally{c.rollback(savepoint);}
    }
    @Test void customerAccountAllocatorCannotBypassOverseasWorkflow()throws Exception {
        JsonObject d=issued(),o=d.getAsJsonObject("order");
        Class<?> allocation=Class.forName("services.LanCustomerAccountService$PaymentAllocation");
        var constructor=allocation.getDeclaredConstructor(String.class,Long.class,BigDecimal.class);constructor.setAccessible(true);
        Object selected=constructor.newInstance("CUSTOM_ORDER",o.get("custom_order_id").getAsLong(),new BigDecimal("100"));
        var method=Arrays.stream(LanCustomerAccountService.class.getDeclaredMethods()).filter(m->m.getName().equals("applySelectedAllocations")).findFirst().orElseThrow();method.setAccessible(true);
        var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->method.invoke(null,c,o.get("customer_id").getAsInt(),List.of(selected),1L,"CARD","test",new models.CashDrawerContext(null,null),UUID.fromString(actor.deviceId()),actor.deviceName(),actor.userId(),actor.name()));
        assertTrue(failure.getCause().getMessage().contains("no longer available"));
        assertEquals(0,execute("DETAIL",d,null).getAsJsonArray("payments").size());
    }
    @Test void sharedImageEndpointAlsoEnforcesOverseasPermissionAndStoreOwnership()throws Exception {
        JsonObject d=draft();UUID asset=UUID.randomUUID();String reference=ImageAssetReference.format(asset);
        try(var p=c.prepareStatement("INSERT INTO image_assets(asset_id,category,bucket_name,object_path,access_level) VALUES(?,'OVERSEAS_ORDER','test-overseas',?,'AUTHENTICATED')")){p.setObject(1,asset);p.setString(2,asset+".png");p.executeUpdate();}
        try(var p=c.prepareStatement("INSERT INTO overseas_order_images(overseas_order_id,asset_reference) VALUES(?,?)")){p.setObject(1,UUID.fromString(d.getAsJsonObject("order").get("overseas_order_id").getAsString()));p.setString(2,reference);p.executeUpdate();}
        try(var p=c.prepareStatement("UPDATE users SET role_id=NULL WHERE user_id=?")){p.setInt(1,actor.userId());p.executeUpdate();}
        assertThrows(SecurityException.class,()->OverseasOrderService.authorizeImageRead(c,actor.userId(),actor.locationId(),reference));
        try(var p=c.prepareStatement("UPDATE users SET role_id=(SELECT role_id FROM roles WHERE upper(role_name)='ADMIN' LIMIT 1) WHERE user_id=?")){p.setInt(1,actor.userId());p.executeUpdate();}
        assertDoesNotThrow(()->OverseasOrderService.authorizeImageRead(c,actor.userId(),actor.locationId(),reference));
        assertThrows(SecurityException.class,()->OverseasOrderService.authorizeImageRead(c,actor.userId(),900002,reference));
    }
    private models.CashDrawerSession cashDraw(boolean enabled)throws Exception {
        try(var p=c.prepareStatement("INSERT INTO devices(device_id,installation_id,is_approved) VALUES(?::uuid,'overseas-cash-test',true) ON CONFLICT DO NOTHING")){p.setString(1,actor.deviceId());p.executeUpdate();}
        long id=CashDrawerService.saveDrawer(c,null,actor.locationId(),"Overseas cash test "+UUID.randomUUID(),"Test",new BigDecimal("20000"),CashDrawerService.DEFAULT_FLOAT_MIX,true,actor.userId());
        OverseasCashService.configure(c,id,enabled);
        CashDrawerService.assignDevice(c,id,actor.locationId(),actor.deviceId(),actor.userId(),"Test");
        return CashDrawerService.openSessionForDevice(c,actor.locationId(),actor.deviceId(),actor.deviceName(),actor.userId(),actor.name(),null);
    }
    private JsonObject cashPayment(String amount){JsonObject b=payment(amount);b.addProperty("method","CASH");return b;}
    @Test void overseasCashNeverEntersNormalFloatOrCarryForwardAndClosesIntoOwnFund()throws Exception {
        var session=cashDraw(true);JsonObject d=issued();java.time.LocalDate today=java.time.LocalDate.now(java.time.ZoneId.of("America/Guyana"));
        var baseline=ServerBalanceSheetService.loadBalanceSheet(c,today,today,"America/Guyana",List.of(session.sessionId()));
        JsonObject request=cashPayment("1000");request.addProperty("action","PAYMENT");request.addProperty("requestId",UUID.randomUUID().toString());request.add("id",d.getAsJsonObject("order").get("overseas_order_id"));request.add("revision",d.getAsJsonObject("order").get("revision"));
        d=OverseasOrderService.execute(c,actor,request);assertEquals(d,OverseasOrderService.execute(c,actor,request));assertEquals(1,d.getAsJsonArray("payments").size());
        assertEquals(0,new BigDecimal("20000").compareTo(CashDrawerService.calculateExpectedCash(c,session.sessionId())));
        assertEquals(0,new BigDecimal("1000").compareTo(OverseasCashService.drawer(c,session.sessionId()).expected()));
        var after=ServerBalanceSheetService.loadBalanceSheet(c,today,today,"America/Guyana",List.of(session.sessionId()));
        assertEquals(0,baseline.balanceCf().compareTo(after.balanceCf()));
        assertThrows(SQLException.class,()->CashDrawerService.closeSession(c,session.sessionId(),new BigDecimal("20000"),null,actor.userId(),actor.name()));
        JsonObject close=new JsonObject();close.addProperty("overseasCounted","980");
        assertThrows(SQLException.class,()->OverseasCashService.prepareClose(c,session.sessionId(),close));
        close.addProperty("overseasReason","Counted twice, short 20");OverseasCashService.prepareClose(c,session.sessionId(),close);
        var closed=CashDrawerService.closeSession(c,session.sessionId(),new BigDecimal("20000"),null,actor.userId(),actor.name());
        assertTrue(closed.closingReport().contains("Overseas variance: -20.00"));
        var report=OverseasCashService.report(c,actor.locationId(),today,today,"America/Guyana");assertEquals(0,new BigDecimal("980").compareTo(report.carriedForward()));
        assertThrows(SQLException.class,()->CashDrawerService.closeSession(c,session.sessionId(),new BigDecimal("20000"),null,actor.userId(),actor.name()));
        OverseasCashService.withdraw(c,new BigDecimal("80"),"Supplier payment");
        report=OverseasCashService.report(c,actor.locationId(),today,today,"America/Guyana");assertEquals(0,new BigDecimal("900").compareTo(report.carriedForward()));assertEquals(0,new BigDecimal("80").compareTo(report.expenses()));
        assertThrows(SQLException.class,()->OverseasCashService.withdraw(c,new BigDecimal("901"),"Too much"));
        assertThrows(SQLException.class,()->OverseasCashService.withdraw(c,new BigDecimal("1"),""));
        JsonObject cashCheck=new JsonObject(),notesCount=new JsonObject();for(int denomination:OverseasCashService.COUNT_DENOMINATIONS)notesCount.addProperty(Integer.toString(denomination),0);notesCount.addProperty("500",1);notesCount.addProperty("100",4);cashCheck.add("denominations",notesCount);cashCheck.addProperty("expected","900");
        long submission=ServerBalanceSheetService.submitBalanceSheet(c,today,today,"America/Guyana","Overseas cash snapshot",List.of(session.sessionId()),cashCheck);
        OverseasCashService.withdraw(c,new BigDecimal("10"),"Withdrawal after saved report");
        try(var p=c.prepareStatement("SELECT overseas_cash_snapshot::text FROM balance_sheet_submissions WHERE balance_sheet_submission_id=?")){p.setLong(1,submission);try(var r=p.executeQuery()){r.next();assertEquals(0,new BigDecimal("900").compareTo(JsonParser.parseString(r.getString(1)).getAsJsonObject().get("carriedForward").getAsBigDecimal()));}}
        try(var p=c.prepareStatement("SELECT overseas_cash_snapshot::text FROM balance_sheet_submissions WHERE balance_sheet_submission_id=?")){p.setLong(1,submission);try(var r=p.executeQuery()){r.next();var check=JsonParser.parseString(r.getString(1)).getAsJsonObject().getAsJsonObject("verification");assertEquals(0,new BigDecimal("900").compareTo(check.get("counted").getAsBigDecimal()));assertEquals(0,check.get("variance").getAsBigDecimal().signum());assertEquals(actor.name(),check.get("verifiedBy").getAsString());}}
        assertThrows(SQLException.class,()->ServerBalanceSheetService.submitBalanceSheet(c,today,today,"America/Guyana","Stale count",List.of(session.sessionId()),cashCheck));
        var next=CashDrawerService.openSessionForDevice(c,actor.locationId(),actor.deviceId(),actor.deviceName(),actor.userId(),actor.name(),null);
        assertEquals(0,OverseasCashService.drawer(c,next.sessionId()).expected().signum());
        assertEquals(0,new BigDecimal("20000").compareTo(CashDrawerService.calculateExpectedCash(c,next.sessionId())));
        var tomorrow=OverseasCashService.report(c,actor.locationId(),today.plusDays(1),today.plusDays(1),"America/Guyana");assertEquals(0,new BigDecimal("890").compareTo(tomorrow.broughtForward()));
    }
    @Test void overseasCashRequiresEnabledDrawerAndRefundsStaySeparate()throws Exception {
        var s=cashDraw(false);JsonObject d=issued();Savepoint save=c.setSavepoint();JsonObject unpaid=d;
        assertThrows(SQLException.class,()->execute("PAYMENT",unpaid,cashPayment("100")));c.rollback(save);
        long drawerId=s.cashDrawerId();assertThrows(SQLException.class,()->OverseasCashService.configure(c,drawerId,true));
        CashDrawerService.closeSession(c,s.sessionId(),new BigDecimal("20000"),null,actor.userId(),actor.name());
        OverseasCashService.configure(c,s.cashDrawerId(),true);
        s=CashDrawerService.openSessionForDevice(c,actor.locationId(),actor.deviceId(),actor.deviceName(),actor.userId(),actor.name(),null);
        d=execute("PAYMENT",d,cashPayment("100"));JsonObject refund=cashPayment("40");refund.addProperty("reason","Payment correction");d=execute("REFUND",d,refund);
        assertEquals(0,new BigDecimal("60").compareTo(OverseasCashService.drawer(c,s.sessionId()).expected()));
        assertEquals(0,CashDrawerService.calculateReturnedCash(c,s.sessionId()).signum());
        assertEquals(0,new BigDecimal("20000").compareTo(CashDrawerService.calculateExpectedCash(c,s.sessionId())));
    }

}
