package services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Materializes collision-safe store transfers from durable cloud sync events. */
final class CrossStoreTransferSyncService {
    private static final String CREATED = "STORE_TRANSFER_CREATED";
    private static final String RECEIVED = "STORE_TRANSFER_RECEIVED";

    private CrossStoreTransferSyncService() { }

    static int announcePending(Connection connection, int locationId) throws SQLException {
        int announced = 0;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT st.transfer_id
                FROM store_transfers st
                WHERE st.from_location_id=?
                  AND UPPER(COALESCE(st.status,'PENDING'))='PENDING'
                  AND NOT EXISTS (
                    SELECT 1 FROM sync_outbox o
                    WHERE o.event_type='STORE_TRANSFER_CREATED'
                      AND o.payload->>'transfer_uuid'=st.transfer_uuid::text)
                ORDER BY st.created_at,st.transfer_id
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    announceTransfer(connection, rs.getLong(1), locationId);
                    announced++;
                }
            }
        }
        return announced;
    }

    static void announceTransfer(Connection connection, long transferId, int locationId)
            throws SQLException {
        JsonObject payload = transferPayload(connection, transferId, locationId);
        SyncOutboxService.recordJsonEvent(connection, CREATED, payload, locationId,
                null, nullableInt(payload, "user_id"));
    }

    static int applyInbox(Connection connection, int locationId) throws SQLException {
        List<InboxEvent> events = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT cloud_sequence,event_type,payload,origin_location_id
                FROM sync_inbox
                WHERE event_type IN ('STORE_TRANSFER_CREATED','STORE_TRANSFER_RECEIVED')
                  AND status IN ('RECEIVED','FAILED')
                ORDER BY cloud_sequence
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(new InboxEvent(rs.getLong(1), rs.getString(2), rs.getString(3),rs.getInt(4)));
                }
            }
        }
        int applied = 0;
        boolean oldAutoCommit = connection.getAutoCommit();
        for (InboxEvent event : events) {
            connection.setAutoCommit(false);
            try {
                JsonObject payload = JsonParser.parseString(event.payload()).getAsJsonObject();
                if(payload.has("change_action")) {
                    int owner=requiredPositive(payload,CREATED.equals(event.eventType())?"source_location_id":"destination_location_id");
                    if(owner!=event.originLocationId())throw new SQLException("Transfer change origin does not match its owning store.");
                }
                if (CREATED.equals(event.eventType())
                        && (!payload.has("transfer_uuid") || !payload.has("items"))) {
                    mark(connection,event.sequence(),"IGNORED_LEGACY",
                            "Superseded by the source store's complete UUID transfer envelope.");
                    connection.commit();
                    continue;
                }
                if (CREATED.equals(event.eventType())) applyCreated(connection, payload, locationId);
                else applyReceived(connection, payload, locationId);
                mark(connection, event.sequence(), "APPLIED", null);
                connection.commit();
                applied++;
            } catch (Exception ex) {
                connection.rollback();
                mark(connection, event.sequence(), "FAILED", safeError(ex));
                connection.commit();
            } finally {
                connection.setAutoCommit(oldAutoCommit);
            }
        }
        return applied;
    }

    static void recordReceived(Connection connection, long transferId, int locationId,
                               UUID deviceId, int userId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT transfer_uuid,from_location_id,to_location_id,received_at,
                       received_by_user_id,COALESCE(received_by_name,''),COALESCE(receive_id,'')
                FROM store_transfers
                WHERE transfer_id=? AND to_location_id=? AND status='RECEIVED'
                """)) {
            ps.setLong(1, transferId);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Received transfer acknowledgement is unavailable.");
                JsonObject payload = new JsonObject();
                payload.addProperty("transfer_uuid", rs.getObject(1, UUID.class).toString());
                payload.addProperty("source_location_id", rs.getInt(2));
                payload.addProperty("destination_location_id", rs.getInt(3));
                Timestamp receivedAt = rs.getTimestamp(4);
                payload.addProperty("received_at", receivedAt == null
                        ? Instant.now().toString() : receivedAt.toInstant().toString());
                if (rs.getObject(5) != null) payload.addProperty("received_by_user_id", rs.getInt(5));
                payload.addProperty("received_by_name", rs.getString(6));
                payload.addProperty("receive_id", rs.getString(7));
                SyncOutboxService.recordJsonEvent(connection, RECEIVED, payload, locationId,
                        deviceId.toString(), userId);
            }
        }
    }

    static JsonObject changeEnvelope(Connection c,long id,int locationId)throws SQLException {
        return transferPayload(c,id,locationId);
    }

    static void requestChange(Connection c,JsonObject payload,int locationId,UUID deviceId,int userId)throws SQLException {
        // Use the existing source-to-destination envelope and destination-to-source acknowledgement routes.
        SyncOutboxService.recordJsonEvent(c,CREATED,payload,locationId,deviceId.toString(),userId);
    }

    private static void applyChange(Connection c,JsonObject payload,int locationId)throws SQLException {
        UUID uuid=requiredUuid(payload,"transfer_uuid");
        UUID request=requiredUuid(payload,"change_request_id");
        // A retried envelope must never overwrite a later edit or recreate cancelled stock.
        try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM sync_outbox WHERE event_type='STORE_TRANSFER_RECEIVED' AND payload->>'change_request_id'=? LIMIT 1")) {
            ps.setString(1,request.toString());try(ResultSet rs=ps.executeQuery()){if(rs.next())return;}
        }
        String action=text(payload,"change_action",20);
        if(!java.util.Set.of("EDIT","CANCEL").contains(action))throw new SQLException("Invalid transfer change action.");
        long id;String status;
        try(PreparedStatement ps=c.prepareStatement("SELECT transfer_id,status,from_location_id FROM store_transfers WHERE transfer_uuid=? AND to_location_id=? FOR UPDATE")) {
            ps.setObject(1,uuid);ps.setInt(2,locationId);
            try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next())throw new SQLException("Incoming transfer must synchronize before its change request.");
                if(rs.getInt(3)!=requiredPositive(payload,"source_location_id"))throw new SQLException("Transfer source mismatch.");
                id=rs.getLong(1);status=rs.getString(2);
            }
        }
        boolean accepted="PENDING".equalsIgnoreCase(status);
        if(accepted) {
            if("EDIT".equals(action))replaceItems(c,id,payload);
            try(PreparedStatement ps=c.prepareStatement("UPDATE store_transfers SET status=?,note=? WHERE transfer_id=?")) {
                ps.setString(1,"CANCEL".equals(action)?"CANCELLED":"PENDING");
                ps.setString(2,text(payload,"note",1000));ps.setLong(3,id);ps.executeUpdate();
            }
        }
        JsonObject ack=payload.deepCopy();ack.addProperty("change_accepted",accepted);
        ack.addProperty("destination_status",status);
        SyncOutboxService.recordJsonEvent(c,RECEIVED,ack,locationId,null,null);
    }

    private static void applyChangeAcknowledgement(Connection c,JsonObject payload,int locationId)throws SQLException {
        UUID uuid=requiredUuid(payload,"transfer_uuid");
        String expected="CHANGE_PENDING:"+requiredUuid(payload,"change_request_id");
        long id;
        try(PreparedStatement ps=c.prepareStatement("SELECT transfer_id,status,to_location_id FROM store_transfers WHERE transfer_uuid=? AND from_location_id=? FOR UPDATE")) {
            ps.setObject(1,uuid);ps.setInt(2,locationId);
            try(ResultSet rs=ps.executeQuery()) {
                if(!rs.next())throw new SQLException("Source transfer is unavailable for change acknowledgement.");
                if(rs.getInt(3)!=requiredPositive(payload,"destination_location_id"))throw new SQLException("Transfer destination mismatch.");
                if(!expected.equals(rs.getString(2)))return; // Duplicate or superseded acknowledgement.
                id=rs.getLong(1);
            }
        }
        boolean accepted=payload.get("change_accepted").getAsBoolean();
        String action=text(payload,"change_action",20);
        if(!java.util.Set.of("EDIT","CANCEL").contains(action))throw new SQLException("Invalid transfer change acknowledgement.");
        if(accepted) {
            java.util.Map<Integer,Integer> deltas=new java.util.TreeMap<>();
            try(PreparedStatement ps=c.prepareStatement("SELECT product_id,quantity FROM store_transfer_items WHERE transfer_id=? ORDER BY product_id")) {
                ps.setLong(1,id);try(ResultSet rs=ps.executeQuery()){while(rs.next())deltas.merge(rs.getInt(1),rs.getInt(2),Integer::sum);}
            }
            if("EDIT".equals(action))for(JsonElement row:payload.getAsJsonArray("items")) {
                JsonObject item=row.getAsJsonObject();deltas.merge(requiredPositive(item,"product_id"),-requiredPositive(item,"quantity"),Integer::sum);
            }
            try(PreparedStatement stock=c.prepareStatement("UPDATE inventory SET quantity_on_hand=quantity_on_hand+? WHERE product_id=? AND location_id=?");
                PreparedStatement movement=c.prepareStatement("INSERT INTO inventory_movements(product_id,location_id,change_qty,reason,note,user_name,user_id,device_id) VALUES(?,?,?,?,?,?,?,?)")) {
                for(var entry:deltas.entrySet()) {
                    if(entry.getValue()==0)continue;
                    stock.setInt(1,entry.getValue());stock.setInt(2,entry.getKey());stock.setInt(3,locationId);
                    if(stock.executeUpdate()!=1)throw new SQLException("Source inventory is unavailable for transfer adjustment.");
                    movement.setInt(1,entry.getKey());movement.setInt(2,locationId);movement.setInt(3,entry.getValue());
                    movement.setString(4,"TRANSFER_OUT");movement.setString(5,"transfer_id="+id+"; action="+action+"; request="+requiredUuid(payload,"change_request_id"));
                    movement.setString(6,text(payload,"change_user_name",300));movement.setInt(7,requiredPositive(payload,"change_user_id"));
                    movement.setString(8,requiredUuid(payload,"change_device_id").toString());movement.executeUpdate();
                }
            }
            if("EDIT".equals(action))replaceItems(c,id,payload);
        }
        String status=accepted&&"CANCEL".equals(action)?"CANCELLED":
                !accepted&&"RECEIVED".equalsIgnoreCase(text(payload,"destination_status",100))?"RECEIVED":"PENDING";
        try(PreparedStatement ps=c.prepareStatement("UPDATE store_transfers SET status=?,note=CASE WHEN ? THEN ? ELSE note END WHERE transfer_id=?")) {
            ps.setString(1,status);ps.setBoolean(2,accepted);ps.setString(3,text(payload,"note",1000));ps.setLong(4,id);ps.executeUpdate();
        }
        SyncOutboxService.recordEvent(c,"INVENTORY_MOVEMENT_CREATED",java.util.Map.of("source","STORE_TRANSFER_CHANGE","transfer_id",id,"accepted",accepted),locationId,null,null);
    }

    private static void replaceItems(Connection c,long id,JsonObject payload)throws SQLException {
        JsonArray rows=payload.getAsJsonArray("items");
        if(rows==null||rows.isEmpty()||rows.size()>300)throw new SQLException("Invalid transfer change items.");
        java.util.Set<Integer> seen=new java.util.HashSet<>();
        try(PreparedStatement delete=c.prepareStatement("DELETE FROM store_transfer_items WHERE transfer_id=?")) {
            delete.setLong(1,id);delete.executeUpdate();
        }
        try(PreparedStatement insert=c.prepareStatement("INSERT INTO store_transfer_items(transfer_id,product_id,quantity) VALUES(?,?,?)")) {
            for(JsonElement element:rows) {
                JsonObject row=element.getAsJsonObject();int product=requiredPositive(row,"product_id"),quantity=requiredPositive(row,"quantity");
                if(!seen.add(product)||quantity>1_000_000)throw new SQLException("Invalid transfer change quantity.");
                insert.setLong(1,id);insert.setInt(2,product);insert.setInt(3,quantity);insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static JsonObject transferPayload(Connection c, long transferId, int locationId)
            throws SQLException {
        JsonObject payload = new JsonObject();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT transfer_uuid,transfer_id,from_location_id,to_location_id,user_id,
                       COALESCE(user_name,''),COALESCE(note,''),created_at
                FROM store_transfers
                WHERE transfer_id=? AND from_location_id=?
                  AND UPPER(COALESCE(status,'PENDING'))='PENDING'
                """)) {
            ps.setLong(1, transferId);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Pending transfer is unavailable for synchronization.");
                payload.addProperty("transfer_uuid", rs.getObject(1, UUID.class).toString());
                payload.addProperty("source_transfer_id", rs.getLong(2));
                payload.addProperty("source_location_id", rs.getInt(3));
                payload.addProperty("destination_location_id", rs.getInt(4));
                if (rs.getObject(5) != null) payload.addProperty("user_id", rs.getInt(5));
                payload.addProperty("user_name", rs.getString(6));
                payload.addProperty("note", rs.getString(7));
                payload.addProperty("created_at", rs.getTimestamp(8).toInstant().toString());
            }
        }
        JsonArray items = new JsonArray();
        try (PreparedStatement ps = c.prepareStatement("""
                SELECT product_id,quantity FROM store_transfer_items
                WHERE transfer_id=? ORDER BY product_id
                """)) {
            ps.setLong(1, transferId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    JsonObject item = new JsonObject();
                    item.addProperty("product_id", rs.getInt(1));
                    item.addProperty("quantity", rs.getInt(2));
                    items.add(item);
                }
            }
        }
        if (items.isEmpty()) throw new SQLException("Pending transfer has no items to synchronize.");
        payload.add("items", items);
        return payload;
    }

    private static void applyCreated(Connection c, JsonObject payload, int locationId)
            throws SQLException {
        UUID transferUuid = requiredUuid(payload, "transfer_uuid");
        int source = requiredPositive(payload, "source_location_id");
        int destination = requiredPositive(payload, "destination_location_id");
        if (destination != locationId || source == destination) {
            throw new SQLException("Transfer event is not addressed to this store.");
        }
        JsonArray items = payload.has("items") && payload.get("items").isJsonArray()
                ? payload.getAsJsonArray("items") : new JsonArray();
        if (items.isEmpty() || items.size() > 300) throw new SQLException("Transfer event has invalid items.");

        if(payload.has("change_action")) { applyChange(c,payload,locationId);return; }
        Long existing = localTransferId(c, transferUuid);
        if (existing != null) return;
        long localId;
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO store_transfers(transfer_uuid,from_location_id,to_location_id,user_name,note,created_at)
                VALUES(?,?,?,?,?,?) RETURNING transfer_id
                """)) {
            ps.setObject(1, transferUuid);
            ps.setInt(2, source);
            ps.setInt(3, destination);
            ps.setString(4, text(payload, "user_name", 300));
            ps.setString(5, text(payload, "note", 1000));
            ps.setTimestamp(6, Timestamp.from(requiredInstant(payload, "created_at")));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("Incoming transfer could not be created.");
                localId = rs.getLong(1);
            }
        }
        try (PreparedStatement product = c.prepareStatement("SELECT 1 FROM products WHERE product_id=?");
             PreparedStatement item = c.prepareStatement("""
                     INSERT INTO store_transfer_items(transfer_id,product_id,quantity) VALUES(?,?,?)
                     """)) {
            for (JsonElement element : items) {
                JsonObject row = element.getAsJsonObject();
                int productId = requiredPositive(row, "product_id");
                int quantity = requiredPositive(row, "quantity");
                product.setInt(1, productId);
                try (ResultSet found = product.executeQuery()) {
                    if (!found.next()) throw new SQLException("Transfer product " + productId + " is unavailable locally.");
                }
                item.setLong(1, localId);
                item.setInt(2, productId);
                item.setInt(3, quantity);
                item.addBatch();
            }
            item.executeBatch();
        }
    }

    private static void applyReceived(Connection c, JsonObject payload, int locationId)
            throws SQLException {
        UUID transferUuid = requiredUuid(payload, "transfer_uuid");
        int source = requiredPositive(payload, "source_location_id");
        if (source != locationId) throw new SQLException("Receipt event is not addressed to this store.");
        if(payload.has("change_action")) { applyChangeAcknowledgement(c,payload,locationId);return; }
        // Delivery can reorder a receipt ahead of an accepted edit acknowledgement.
        // Apply the stock delta first; retry this receipt on the next inbox pass.
        try(PreparedStatement ps=c.prepareStatement("SELECT status FROM store_transfers WHERE transfer_uuid=? AND from_location_id=? FOR UPDATE")) {
            ps.setObject(1,transferUuid);ps.setInt(2,locationId);
            try(ResultSet rs=ps.executeQuery()) {
                if(rs.next()&&rs.getString(1).startsWith("CHANGE_PENDING:"))throw new SQLException("Awaiting transfer change acknowledgement before applying receipt.");
            }
        }
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE store_transfers
                SET status='RECEIVED',received_at=?,received_by_user_id=?,received_by_name=?
                WHERE transfer_uuid=? AND from_location_id=?
                  AND UPPER(COALESCE(status,'PENDING')) IN ('PENDING','RECEIVED')
                """)) {
            ps.setTimestamp(1, Timestamp.from(requiredInstant(payload, "received_at")));
            if (payload.has("received_by_user_id") && !payload.get("received_by_user_id").isJsonNull())
                ps.setInt(2, payload.get("received_by_user_id").getAsInt());
            else ps.setNull(2, java.sql.Types.INTEGER);
            ps.setString(3, text(payload, "received_by_name", 300));
            // receive_id belongs to the destination store's receiving_batches
            // table. Persisting it on the source row violates the local FK and
            // incorrectly couples two independently authoritative databases.
            ps.setObject(4, transferUuid);
            ps.setInt(5, locationId);
            int changed = ps.executeUpdate();
            if (changed == 0 && localTransferId(c, transferUuid) == null)
                throw new SQLException("Source transfer is unavailable for receipt acknowledgement.");
        }
    }

    private static Long localTransferId(Connection c, UUID uuid) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT transfer_id FROM store_transfers WHERE transfer_uuid=?")) {
            ps.setObject(1, uuid);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : null; }
        }
    }

    private static void mark(Connection c, long sequence, String status, String error)
            throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                UPDATE sync_inbox SET status=?,applied_at=CASE WHEN ?='APPLIED' THEN CURRENT_TIMESTAMP ELSE applied_at END,
                    last_error=? WHERE cloud_sequence=?
                """)) {
            ps.setString(1, status);
            ps.setString(2, status);
            ps.setString(3, error);
            ps.setLong(4, sequence);
            ps.executeUpdate();
        }
    }

    private static UUID requiredUuid(JsonObject o, String name) throws SQLException {
        try { return UUID.fromString(o.get(name).getAsString()); }
        catch (Exception ex) { throw new SQLException("Transfer event has invalid " + name + ".", ex); }
    }
    private static int requiredPositive(JsonObject o, String name) throws SQLException {
        try { int value=o.get(name).getAsInt(); if(value<=0)throw new IllegalArgumentException(); return value; }
        catch (Exception ex) { throw new SQLException("Transfer event has invalid " + name + ".", ex); }
    }
    private static Instant requiredInstant(JsonObject o, String name) throws SQLException {
        try { return Instant.parse(o.get(name).getAsString()); }
        catch (Exception ex) { throw new SQLException("Transfer event has invalid " + name + ".", ex); }
    }
    private static String text(JsonObject o,String name,int max) {
        if(!o.has(name)||o.get(name).isJsonNull())return "";
        String value=o.get(name).getAsString();return value.substring(0,Math.min(value.length(),max));
    }
    private static Integer nullableInt(JsonObject o,String name) {
        return !o.has(name)||o.get(name).isJsonNull()?null:o.get(name).getAsInt();
    }
    private static String safeError(Exception ex) {
        String value=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
        return value.substring(0,Math.min(value.length(),1000));
    }
    private record InboxEvent(long sequence,String eventType,String payload,int originLocationId) { }
}
