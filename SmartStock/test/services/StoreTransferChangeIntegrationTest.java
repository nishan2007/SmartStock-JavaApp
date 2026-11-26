package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.sql.*;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StoreTransferChangeIntegrationTest {
    @TempDir Path temp;

    @Test void changesRequireDestinationAcknowledgementAndNeverRestoreReceivedStock() throws Exception {
        Path bin=Path.of(System.getenv().getOrDefault("SMARTSTOCK_TEST_POSTGRES_BIN","C:/Program Files/PostgreSQL/17/bin"));
        String suffix=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")?".exe":"";
        assumeTrue(Files.isRegularFile(bin.resolve("initdb"+suffix)));
        Process init=new ProcessBuilder(bin.resolve("initdb"+suffix).toString(),"-D",temp.resolve("cluster").toString(),"-A","trust","-U","transfer_test","--no-locale","--encoding=UTF8")
                .redirectErrorStream(true).redirectOutput(temp.resolve("init.log").toFile()).start();
        assertTrue(init.waitFor(30,TimeUnit.SECONDS));assertEquals(0,init.exitValue(),Files.readString(temp.resolve("init.log")));
        int port;try(ServerSocket socket=new ServerSocket(0)){port=socket.getLocalPort();}
        Process server=new ProcessBuilder(bin.resolve("postgres"+suffix).toString(),"-D",temp.resolve("cluster").toString(),"-h","127.0.0.1","-p",String.valueOf(port),"-F")
                .redirectErrorStream(true).redirectOutput(temp.resolve("postgres.log").toFile()).start();
        try {
            String url="jdbc:postgresql://127.0.0.1:"+port+"/postgres?connectTimeout=1";
            Connection ready=null;
            for(int i=0;i<100&&ready==null;i++){try{ready=DriverManager.getConnection(url,"transfer_test","");}catch(SQLException ex){Thread.sleep(100);}}
            assertNotNull(ready);
            try(Connection source=ready;Connection destination=DriverManager.getConnection(url,"transfer_test","")) {
                schema(source,"source_store");schema(destination,"destination_store");
                source.setAutoCommit(false);destination.setAutoCommit(false);
                UUID device=UUID.randomUUID();
                long id=((Number)LanTransferService.create(source,edit(4,"Initial"),device,1,"Sender",1).get("transferId")).longValue();source.commit();
                assertEquals(16,stock(source));relay(source,destination,2);
                assertEquals(1,LanTransferService.incoming(destination,1,2).size());
                assertEquals(1,LanTransferService.outgoing(source,1,1).size());
                JsonObject stale=change(id,"EDIT",6,"Stale edit");stale.add("expectedLines",JsonParser.parseString("[{\"productId\":1,\"quantity\":3}]"));
                assertEquals(409,assertThrows(LanTransferService.RuleViolation.class,()->LanTransferService.change(source,stale,device,1,"Sender",1)).status());
                stale.add("expectedLines",JsonParser.parseString("[{\"productId\":1,\"quantity\":4}]"));stale.addProperty("expectedNote","Old note");
                assertEquals(409,assertThrows(LanTransferService.RuleViolation.class,()->LanTransferService.change(source,stale,device,1,"Sender",1)).status());
                assertEquals("PENDING",status(source));
                assertEquals(403,assertThrows(LanTransferService.RuleViolation.class,()->LanTransferService.change(source,change(id,"CANCEL",0,""),device,99,"Other",1)).status());
                assertEquals(404,assertThrows(LanTransferService.RuleViolation.class,()->LanTransferService.change(source,change(id,"CANCEL",0,""),device,1,"Sender",2)).status());
                assertEquals(400,assertThrows(LanTransferService.RuleViolation.class,()->LanTransferService.change(source,change(id,"EDIT",0,""),device,1,"Sender",1)).status());
                source.rollback();
                LanTransferService.change(source,change(id,"EDIT",6,"Edited"),device,1,"Sender",1);source.commit();
                assertEquals(16,stock(source));assertTrue(status(source).startsWith("CHANGE_PENDING:"));
                assertEquals(409,assertThrows(LanTransferService.RuleViolation.class,()->LanTransferService.change(source,change(id,"CANCEL",0,""),device,1,"Sender",1)).status());source.rollback();
                relay(source,destination,2);assertEquals(6,quantity(destination));assertEquals(16,stock(source));
                relay(destination,source,1);assertEquals(14,stock(source));assertEquals(6,quantity(source));assertEquals("PENDING",status(source));
                relay(destination,source,1);assertEquals(14,stock(source));
                LanTransferService.change(source,change(id,"EDIT",2,"Reduced"),device,1,"Sender",1);source.commit();
                relay(source,destination,2);relay(destination,source,1);assertEquals(18,stock(source));assertEquals(2,quantity(destination));
                // Replay the earlier edit under a different transport sequence after a newer edit.
                try(var st=destination.createStatement()) {st.execute("INSERT INTO sync_inbox(cloud_sequence,event_type,payload,origin_location_id) SELECT cloud_sequence+100,event_type,payload,origin_location_id FROM sync_inbox WHERE cloud_sequence=2");}destination.commit();
                CrossStoreTransferSyncService.applyInbox(destination,2);assertEquals(2,quantity(destination));
                LanTransferService.change(source,change(id,"CANCEL",0,""),device,1,"Sender",1);source.commit();
                assertEquals(18,stock(source));relay(source,destination,2);assertEquals("CANCELLED",status(destination));
                relay(destination,source,1);assertEquals(20,stock(source));assertEquals("CANCELLED",status(source));
                assertTrue(LanTransferService.outgoing(source,1,1).isEmpty());
                assertEquals(3,scalar(source,"SELECT count(*) FROM inventory_movements WHERE note LIKE '%action=%'"));
                // A destination that has already received wins over a cancellation in flight.
                long receivedId=((Number)LanTransferService.create(source,edit(3,"Race"),device,1,"Sender",1).get("transferId")).longValue();source.commit();relay(source,destination,2);
                try(var st=destination.createStatement()){st.execute("UPDATE store_transfers SET status='RECEIVED' WHERE status='PENDING'");}destination.commit();
                LanTransferService.change(source,change(receivedId,"CANCEL",0,""),device,1,"Sender",1);source.commit();
                relay(source,destination,2);relay(destination,source,1);assertEquals(17,stock(source));
                assertEquals("RECEIVED",string(source,"SELECT status FROM store_transfers WHERE transfer_id="+receivedId));
                // Replacing a product restores its old quantity and deducts the added product.
                long replacementId=((Number)LanTransferService.create(source,edit(1,"Replace product"),device,1,"Sender",1).get("transferId")).longValue();source.commit();relay(source,destination,2);
                JsonObject replacement=change(replacementId,"EDIT",5,"New product");
                replacement.getAsJsonArray("lines").get(0).getAsJsonObject().addProperty("productId",2);
                LanTransferService.change(source,replacement,device,1,"Sender",1);source.commit();
                relay(source,destination,2);relay(destination,source,1);
                assertEquals(17,stock(source));assertEquals(5,scalar(source,"SELECT quantity_on_hand FROM inventory WHERE product_id=2"));
                assertEquals(2,scalar(destination,"SELECT product_id FROM store_transfer_items ORDER BY transfer_id DESC LIMIT 1"));
                // Receipt delivery may overtake the edit acknowledgement; never lose its stock delta.
                long reorderedId=((Number)LanTransferService.create(source,edit(1,"Reordered"),device,1,"Sender",1).get("transferId")).longValue();source.commit();relay(source,destination,2);
                LanTransferService.change(source,change(reorderedId,"EDIT",4,"Reordered edit"),device,1,"Sender",1);source.commit();relay(source,destination,2);
                JsonObject receipt=new JsonObject();receipt.addProperty("transfer_uuid",string(source,"SELECT transfer_uuid FROM store_transfers WHERE transfer_id="+reorderedId));
                receipt.addProperty("source_location_id",1);receipt.addProperty("destination_location_id",2);receipt.addProperty("received_at","2026-10-30T12:00:00Z");receipt.addProperty("received_by_name","Receiver");
                try(var insert=source.prepareStatement("INSERT INTO sync_inbox(cloud_sequence,event_type,payload,origin_location_id) VALUES(-1,'STORE_TRANSFER_RECEIVED',?::jsonb,2)")){insert.setString(1,receipt.toString());insert.executeUpdate();}source.commit();
                relay(destination,source,1);assertEquals(13,stock(source));
                assertEquals(1,scalar(source,"SELECT count(*) FROM sync_inbox WHERE status='FAILED'"));
                CrossStoreTransferSyncService.applyInbox(source,1);source.commit();
                assertEquals("RECEIVED",string(source,"SELECT status FROM store_transfers WHERE transfer_id="+reorderedId));assertEquals(13,stock(source));
                // A legacy transfer can be changed before its first creation envelope is announced.
                long legacyId=((Number)LanTransferService.create(source,edit(1,"Legacy"),device,1,"Sender",1).get("transferId")).longValue();
                try(var st=source.createStatement()){st.execute("DELETE FROM sync_outbox WHERE event_type='STORE_TRANSFER_CREATED' AND (payload->>'source_transfer_id')::bigint="+legacyId);}source.commit();
                LanTransferService.change(source,change(legacyId,"CANCEL",0,""),device,1,"Sender",1);source.commit();
                relay(source,destination,2);relay(destination,source,1);assertEquals(13,stock(source));
                assertEquals("CANCELLED",string(source,"SELECT status FROM store_transfers WHERE transfer_id="+legacyId));
                assertEquals(0,scalar(source,"SELECT count(*) FROM sync_inbox WHERE status='FAILED'"));
                assertEquals(0,scalar(destination,"SELECT count(*) FROM sync_inbox WHERE status='FAILED'"));
                try(Connection full=DriverManager.getConnection(url,"transfer_test","")){verifyRealReceiving(full,device);}
            }
        } finally {server.destroy();assertTrue(server.waitFor(15,TimeUnit.SECONDS));}
    }

    private static void verifyRealReceiving(Connection c,UUID device)throws Exception {
        // Full disposable application schema: exercise receipt allocation and actual receiving SQL.
        SchemaContractService.installLocalBaseline(c);c.setAutoCommit(false);
        try(var st=c.createStatement()) {
            st.execute("INSERT INTO locations(location_id,name,receipt_store_code) VALUES(994,'Source fixture','0994'),(995,'Destination fixture','0995')");
            st.execute("INSERT INTO roles(role_id,role_name) VALUES(994,'Transfer fixture')");
            st.execute("INSERT INTO role_permissions(role_id,permission_id) SELECT 994,permission_id FROM permissions WHERE permission_key='STORE_TRANSFER'");
            st.execute("INSERT INTO users(user_id,username,full_name,role_id,is_active) VALUES(994,'transfer-fixture','Transfer Tester',994,true)");
            st.execute("INSERT INTO user_locations(user_id,location_id) VALUES(994,994),(994,995)");
            st.execute("INSERT INTO products(product_id,name,sku,barcode,price,is_active,size) VALUES(994,'Polo','POLO','T-994',100,true,'M')");
            st.execute("INSERT INTO inventory(product_id,location_id,quantity_on_hand,reorder_level) VALUES(994,994,20,0)");
        }
        try(var ps=c.prepareStatement("INSERT INTO devices(device_id,installation_id,last_store_id,is_approved,receipt_device_code) VALUES(?, 'transfer-fixture',995,true,'0001')")){ps.setObject(1,device);ps.executeUpdate();}
        assertEquals(994,LanTransferService.products(c,"T-994",994,994).get(0).get("productId"));
        assertEquals("Polo (M)",LanTransferService.products(c,"polo m",994,994).get(0).get("name"));
        assertTrue(LanTransferService.products(c,"T-994",994,995).isEmpty());
        assertTrue(LanTransferService.products(c,"not-a-real-product",994,994).isEmpty());
        assertEquals(403,assertThrows(LanTransferService.RuleViolation.class,()->LanTransferService.products(c,"T-994",999999,994)).status());
        try(var st=c.createStatement()){st.execute("INSERT INTO product_barcodes(product_id,barcode) VALUES(994,'0123456789012')");}
        assertEquals(994,LanTransferService.products(c,"0123456789012",994,994).get(0).get("productId"));
        JsonObject body=JsonParser.parseString("{\"destinationLocationId\":995,\"note\":\"Actual receiving\",\"lines\":[{\"productId\":994,\"quantity\":4}]}").getAsJsonObject();
        long id=((Number)LanTransferService.create(c,body,device,994,"Sender",994).get("transferId")).longValue();
        var row=LanTransferService.outgoing(c,994,994).get(0);assertEquals(995,row.get("destinationLocationId"));
        var detail=LanTransferService.items(c,id,994,994).get(0);assertEquals("Polo (M)",detail.get("name"));assertEquals(16,detail.get("availableQuantity"));
        JsonObject reviewed=JsonParser.parseString("{\"expectedLines\":[{\"productId\":994,\"quantity\":3}]}").getAsJsonObject();
        assertEquals(409,assertThrows(LanTransferService.RuleViolation.class,()->LanTransferService.receive(c,id,device,994,"Receiver",995,reviewed)).status());
        assertEquals(0,scalar(c,"SELECT count(*) FROM receiving_batches WHERE location_id=995"));
        assertEquals(0,scalar(c,"SELECT count(*) FROM inventory WHERE product_id=994 AND location_id=995"));
        reviewed.getAsJsonArray("expectedLines").get(0).getAsJsonObject().addProperty("quantity",4);
        var receive=LanTransferService.receive(c,id,device,994,"Receiver",995,reviewed);assertNotNull(receive.get("receiveId"));
        assertEquals(4,scalar(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=994 AND location_id=995"));
        assertEquals(16,scalar(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=994 AND location_id=994"));
        assertEquals(1,scalar(c,"SELECT count(*) FROM receiving_batches WHERE location_id=995"));
        assertEquals(409,assertThrows(LanTransferService.RuleViolation.class,()->LanTransferService.receive(c,id,device,994,"Receiver",995)).status());
        JsonObject cancel=change(id,"CANCEL",0,"");assertEquals(409,assertThrows(LanTransferService.RuleViolation.class,()->LanTransferService.change(c,cancel,device,994,"Sender",994)).status());
        assertEquals(4,scalar(c,"SELECT quantity_on_hand FROM inventory WHERE product_id=994 AND location_id=995"));c.rollback();
    }

    private static JsonObject edit(int quantity,String note) {
        JsonObject body=new JsonObject();body.addProperty("destinationLocationId",2);body.addProperty("note",note);
        body.add("lines",JsonParser.parseString("[{\"productId\":1,\"quantity\":"+quantity+"}]"));return body;
    }
    private static JsonObject change(long id,String action,int quantity,String note) {JsonObject body=edit(quantity,note);body.addProperty("transferId",id);body.addProperty("action",action);return body;}
    private static int scalar(Connection c,String sql)throws Exception {try(var st=c.createStatement();var rs=st.executeQuery(sql)){assertTrue(rs.next());return rs.getInt(1);}}
    private static String string(Connection c,String sql)throws Exception {try(var st=c.createStatement();var rs=st.executeQuery(sql)){assertTrue(rs.next());return rs.getString(1);}}
    private static int stock(Connection c)throws Exception{return scalar(c,"SELECT quantity_on_hand FROM inventory WHERE location_id=1 AND product_id=1");}
    private static int quantity(Connection c)throws Exception{return scalar(c,"SELECT quantity FROM store_transfer_items ORDER BY transfer_id LIMIT 1");}
    private static String status(Connection c)throws Exception{return string(c,"SELECT status FROM store_transfers ORDER BY transfer_id LIMIT 1");}
    private static void relay(Connection from,Connection to,int location)throws Exception {
        from.commit();to.commit();
        try(var st=from.createStatement();var rs=st.executeQuery("SELECT cloud_sequence,event_type,payload,origin_location_id FROM sync_outbox WHERE event_type IN ('STORE_TRANSFER_CREATED','STORE_TRANSFER_RECEIVED') ORDER BY cloud_sequence");
            var insert=to.prepareStatement("INSERT INTO sync_inbox(cloud_sequence,event_type,payload,origin_location_id) VALUES(?,?,?::jsonb,?) ON CONFLICT DO NOTHING")) {
            while(rs.next()){insert.setLong(1,rs.getLong(1));insert.setString(2,rs.getString(2));insert.setString(3,rs.getString(3));insert.setInt(4,rs.getInt(4));insert.executeUpdate();}
        }
        to.commit();CrossStoreTransferSyncService.applyInbox(to,location);to.commit();
    }
    private static void schema(Connection c,String schema)throws Exception {
        try(var st=c.createStatement()) {
            st.execute("CREATE SCHEMA "+schema);st.execute("SET search_path TO "+schema);
            st.execute("CREATE TABLE locations(location_id int PRIMARY KEY,name text); INSERT INTO locations VALUES(1,'Source'),(2,'Destination')");
            st.execute("CREATE TABLE users(user_id int PRIMARY KEY,role_id int);INSERT INTO users VALUES(1,1)");
            st.execute("CREATE TABLE permissions(permission_id int,permission_key text);INSERT INTO permissions VALUES(1,'STORE_TRANSFER')");
            st.execute("CREATE TABLE role_permissions(role_id int,permission_id int);INSERT INTO role_permissions VALUES(1,1)");
            st.execute("CREATE TABLE products(product_id int PRIMARY KEY,sku text,name text,size text,product_type text,is_active boolean);INSERT INTO products VALUES(1,'SKU','Product','','INVENTORY',true),(2,'SKU2','Other product','','INVENTORY',true)");
            st.execute("CREATE TABLE inventory(product_id int,location_id int,quantity_on_hand int,PRIMARY KEY(product_id,location_id));INSERT INTO inventory VALUES(1,1,20),(2,1,10)");
            st.execute("CREATE TABLE store_transfers(transfer_id bigserial PRIMARY KEY,transfer_uuid uuid UNIQUE DEFAULT gen_random_uuid(),from_location_id int,to_location_id int,user_id int,user_name text,note text,status text DEFAULT 'PENDING',created_at timestamptz DEFAULT now(),received_at timestamptz,received_by_user_id int,received_by_name text,receive_id text)");
            st.execute("CREATE TABLE store_transfer_items(transfer_item_id bigserial PRIMARY KEY,transfer_id bigint REFERENCES store_transfers,product_id int REFERENCES products,quantity int)");
            st.execute("CREATE TABLE inventory_movements(product_id int,location_id int,change_qty int,reason text,note text,user_name text,user_id int,device_id text)");
            st.execute("CREATE TABLE sync_outbox(cloud_sequence bigserial PRIMARY KEY,event_type text,location_id int,device_id text,user_id int,payload jsonb,origin_location_id int,origin_device_id text)");
            st.execute("CREATE TABLE sync_inbox(cloud_sequence bigint PRIMARY KEY,event_type text,payload jsonb,origin_location_id int,status text DEFAULT 'RECEIVED',applied_at timestamptz,last_error text)");
        }
    }
}
