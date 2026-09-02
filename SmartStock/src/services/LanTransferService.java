package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import managers.ServerReceiptNumberManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-only, store-scoped transfer creation and receiving. */
final class LanTransferService {
    private static final Gson GSON = new Gson();

    private LanTransferService() { }

    static List<Map<String, Object>> destinations(Connection c, int userId, int sourceLocationId) throws Exception {
        requirePermission(c, userId);
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT location_id,name FROM locations WHERE location_id<>? ORDER BY name")) {
            ps.setInt(1, sourceLocationId); try (ResultSet rs=ps.executeQuery()) {
                while(rs.next()) rows.add(map("locationId",rs.getInt(1),"name",rs.getString(2)));
            }
        }
        return rows;
    }

    static List<Map<String, Object>> products(Connection c, String query,
                                              int userId, int locationId) throws Exception {
        requirePermission(c, userId);
        String search = query == null ? "" : query.trim();
        if (search.length() > 300) throw rule(400,"VALIDATION_ERROR","Search text is too long.");
        StringBuilder sql = new StringBuilder("""
                SELECT p.product_id,COALESCE(p.sku,''),p.name||
                  CASE WHEN COALESCE(p.size,'')='' THEN '' ELSE ' ('||p.size||')' END,
                  COALESCE(i.quantity_on_hand,0)
                FROM products p JOIN inventory i ON i.product_id=p.product_id
                WHERE i.location_id=? AND COALESCE(p.product_type,'INVENTORY')='INVENTORY' AND p.is_active=TRUE
                """);
        if (!search.isBlank()) sql.append(" AND ").append(ProductSearchHelper.predicate("p",locationId,search));
        sql.append(" ORDER BY p.name LIMIT 300");
        List<Map<String,Object>> rows=new ArrayList<>();
        try(PreparedStatement ps=c.prepareStatement(sql.toString())){
            ps.setInt(1,locationId); if(!search.isBlank())ProductSearchHelper.bindTokens(ps,2,search);
            try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(map(
                    "productId",rs.getInt(1),"sku",rs.getString(2),"name",rs.getString(3),
                    "availableQuantity",rs.getInt(4)));}
        }
        return rows;
    }

    static List<Map<String,Object>> incoming(Connection c,int userId,int locationId)throws Exception{
        requirePermission(c,userId); List<Map<String,Object>>rows=new ArrayList<>();
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT st.transfer_id,COALESCE(l.name,'Unknown'),st.created_at,COALESCE(st.user_name,''),
                  COALESCE(st.note,''),COUNT(i.transfer_item_id),COALESCE(SUM(i.quantity),0)
                FROM store_transfers st LEFT JOIN store_transfer_items i ON i.transfer_id=st.transfer_id
                LEFT JOIN locations l ON l.location_id=st.from_location_id
                WHERE st.to_location_id=? AND UPPER(COALESCE(st.status,'PENDING'))='PENDING'
                GROUP BY st.transfer_id,l.name,st.created_at,st.user_name,st.note
                ORDER BY st.created_at,st.transfer_id LIMIT 1000
                """)){ps.setInt(1,locationId);try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(map(
                        "transferId",rs.getLong(1),"fromStore",rs.getString(2),"createdAtEpochMillis",rs.getTimestamp(3).getTime(),
                        "sentBy",rs.getString(4),"note",rs.getString(5),"itemCount",rs.getInt(6),"unitCount",rs.getInt(7)));}}
        return rows;
    }

    static List<Map<String,Object>> outgoing(Connection c,int userId,int locationId)throws Exception{
        requirePermission(c,userId); List<Map<String,Object>>rows=new ArrayList<>();
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT st.transfer_id,COALESCE(l.name,'Unknown'),st.created_at,COALESCE(st.user_name,''),
                  COALESCE(st.note,''),COUNT(i.transfer_item_id),COALESCE(SUM(i.quantity),0)
                FROM store_transfers st LEFT JOIN store_transfer_items i ON i.transfer_id=st.transfer_id
                LEFT JOIN locations l ON l.location_id=st.to_location_id
                WHERE st.from_location_id=? AND UPPER(COALESCE(st.status,'PENDING'))='PENDING'
                GROUP BY st.transfer_id,l.name,st.created_at,st.user_name,st.note
                ORDER BY st.created_at,st.transfer_id LIMIT 1000
                """)){ps.setInt(1,locationId);try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(map(
                        "transferId",rs.getLong(1),"toStore",rs.getString(2),"createdAtEpochMillis",rs.getTimestamp(3).getTime(),
                        "sentBy",rs.getString(4),"note",rs.getString(5),"itemCount",rs.getInt(6),"unitCount",rs.getInt(7)));}}
        return rows;
    }

    static List<Map<String,Object>> items(Connection c,long transferId,int userId,int locationId)throws Exception{
        requirePermission(c,userId); List<Map<String,Object>>rows=new ArrayList<>();
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT i.product_id,COALESCE(p.sku,''),COALESCE(p.name,'Unknown'),i.quantity
                FROM store_transfer_items i JOIN store_transfers st ON st.transfer_id=i.transfer_id
                LEFT JOIN products p ON p.product_id=i.product_id
                WHERE i.transfer_id=? AND (st.to_location_id=? OR st.from_location_id=?)
                  AND UPPER(COALESCE(st.status,'PENDING'))='PENDING'
                ORDER BY p.name,i.product_id
                """)){ps.setLong(1,transferId);ps.setInt(2,locationId);ps.setInt(3,locationId);try(ResultSet rs=ps.executeQuery()){while(rs.next())rows.add(map(
                        "productId",rs.getInt(1),"sku",rs.getString(2),"name",rs.getString(3),"quantity",rs.getInt(4)));}}
        if(rows.isEmpty())throw rule(404,"TRANSFER_NOT_FOUND","Pending transfer was not found for this store.");
        return rows;
    }

    static Map<String,Object> create(Connection c,JsonObject body,UUID deviceId,int userId,String userName,
                                     int sourceLocationId)throws Exception{
        requirePermission(c,userId); CreateRequest request=GSON.fromJson(body,CreateRequest.class);
        if(request==null||request.lines()==null||request.lines().isEmpty())throw rule(400,"VALIDATION_ERROR","Add at least one transfer item.");
        if(request.lines().size()>300)throw rule(400,"VALIDATION_ERROR","Transfer has too many items.");
        if(request.destinationLocationId()<=0||request.destinationLocationId()==sourceLocationId)
            throw rule(400,"VALIDATION_ERROR","Source and destination stores must be different.");
        try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM locations WHERE location_id=?")){ps.setInt(1,request.destinationLocationId());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw rule(404,"LOCATION_NOT_FOUND","Destination store was not found.");}}
        List<TransferLine> lines=new ArrayList<>(request.lines()); lines.sort(Comparator.comparingInt(TransferLine::productId));
        Set<Integer> unique=new HashSet<>();
        for(TransferLine line:lines){if(line.productId()<=0||line.quantity()<=0||line.quantity()>1_000_000||!unique.add(line.productId()))throw rule(400,"VALIDATION_ERROR","Every product must appear once with a valid quantity.");}
        for(TransferLine line:lines){
            try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(i.quantity_on_hand,0) FROM inventory i JOIN products p ON p.product_id=i.product_id WHERE i.product_id=? AND i.location_id=? AND COALESCE(p.product_type,'INVENTORY')='INVENTORY' AND p.is_active=TRUE FOR UPDATE OF i")){
                ps.setInt(1,line.productId());ps.setInt(2,sourceLocationId);try(ResultSet rs=ps.executeQuery()){
                    if(!rs.next())throw rule(404,"PRODUCT_NOT_FOUND","A transfer product is unavailable at this store.");
                }
            }
        }
        long transferId;
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO store_transfers(from_location_id,to_location_id,user_id,user_name,note) VALUES (?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
            ps.setInt(1,sourceLocationId);ps.setInt(2,request.destinationLocationId());ps.setInt(3,userId);ps.setString(4,userName);ps.setString(5,clean(request.note(),1000));ps.executeUpdate();
            try(ResultSet keys=ps.getGeneratedKeys()){if(!keys.next())throw new SQLException("Transfer could not be created.");transferId=keys.getLong(1);}
        }
        try(PreparedStatement item=c.prepareStatement("INSERT INTO store_transfer_items(transfer_id,product_id,quantity) VALUES (?,?,?)");
            PreparedStatement stock=c.prepareStatement("UPDATE inventory SET quantity_on_hand=quantity_on_hand-? WHERE product_id=? AND location_id=?");
            PreparedStatement movement=c.prepareStatement("INSERT INTO inventory_movements(product_id,location_id,change_qty,reason,note,user_name,user_id,device_id) VALUES (?,?,?,?,?,?,?,?)")){
            for(TransferLine line:lines){
                stock.setInt(1,line.quantity());stock.setInt(2,line.productId());stock.setInt(3,sourceLocationId);
                if(stock.executeUpdate()!=1)throw rule(409,"STOCK_CHANGED","Transfer stock changed. Refresh and retry the transfer.");
                item.setLong(1,transferId);item.setInt(2,line.productId());item.setInt(3,line.quantity());item.addBatch();
                movement.setInt(1,line.productId());movement.setInt(2,sourceLocationId);movement.setInt(3,-line.quantity());movement.setString(4,"TRANSFER_OUT");
                movement.setString(5,"transfer_id="+transferId+"; from_location_id="+sourceLocationId+"; to_location_id="+request.destinationLocationId());
                movement.setString(6,userName);movement.setInt(7,userId);movement.setString(8,deviceId.toString());movement.addBatch();
            } item.executeBatch();movement.executeBatch();
        }
        CrossStoreTransferSyncService.announceTransfer(c,transferId,sourceLocationId);
        return map("transferId",transferId,"lineCount",lines.size());
    }

    static Map<String,Object> receive(Connection c,long transferId,UUID deviceId,int userId,String userName,
                                      int locationId)throws Exception{
        requirePermission(c,userId);int fromLocation;
        try(PreparedStatement ps=c.prepareStatement("SELECT from_location_id,to_location_id,COALESCE(status,'PENDING') FROM store_transfers WHERE transfer_id=? AND to_location_id=? FOR UPDATE")){
            ps.setLong(1,transferId);ps.setInt(2,locationId);try(ResultSet rs=ps.executeQuery()){
                if(!rs.next())throw rule(404,"TRANSFER_NOT_FOUND","Transfer was not found for this receiving store.");
                if(!"PENDING".equalsIgnoreCase(rs.getString(3)))throw rule(409,"TRANSFER_ALREADY_RECEIVED","Transfer has already been received.");
                fromLocation=rs.getInt(1);
            }
        }
        List<TransferLine>lines=new ArrayList<>();
        try(PreparedStatement ps=c.prepareStatement("SELECT product_id,quantity FROM store_transfer_items WHERE transfer_id=? ORDER BY product_id FOR UPDATE")){
            ps.setLong(1,transferId);try(ResultSet rs=ps.executeQuery()){while(rs.next())lines.add(new TransferLine(rs.getInt(1),rs.getInt(2)));}
        }
        if(lines.isEmpty())throw rule(409,"TRANSFER_EMPTY","Transfer has no items.");
        ServerReceiptNumberManager.ReceiveNumber receive=ServerReceiptNumberManager.nextReceive(c,locationId,deviceId);
        try(PreparedStatement ps=c.prepareStatement("INSERT INTO receiving_batches(receive_id,location_id,user_id,user_name,receive_device_id,receive_sequence) VALUES (?,?,?,?,?,?)")){
            ps.setString(1,receive.receiveId());ps.setInt(2,locationId);ps.setInt(3,userId);ps.setString(4,userName);ps.setString(5,receive.deviceId());ps.setInt(6,receive.sequence());ps.executeUpdate();
        }
        try(PreparedStatement ensure=c.prepareStatement("INSERT INTO inventory(product_id,location_id,quantity_on_hand,reorder_level) VALUES (?,?,0,0) ON CONFLICT(product_id,location_id) DO NOTHING");
            PreparedStatement add=c.prepareStatement("UPDATE inventory SET quantity_on_hand=quantity_on_hand+? WHERE product_id=? AND location_id=?");
            PreparedStatement movement=c.prepareStatement("INSERT INTO inventory_movements(product_id,location_id,change_qty,reason,note,user_name,user_id,device_id,receive_id,receive_device_id,receive_sequence) VALUES (?,?,?,?,?,?,?,?,?,?,?)")){
            for(TransferLine line:lines){
                ensure.setInt(1,line.productId());ensure.setInt(2,locationId);ensure.executeUpdate();
                add.setInt(1,line.quantity());add.setInt(2,line.productId());add.setInt(3,locationId);if(add.executeUpdate()!=1)throw new SQLException("Receiving inventory changed unexpectedly.");
                movement.setInt(1,line.productId());movement.setInt(2,locationId);movement.setInt(3,line.quantity());movement.setString(4,"INVENTORY_ENTRY");
                movement.setString(5,"transfer_id="+transferId+"; from_location_id="+fromLocation+"; received_by_user_id="+userId);
                movement.setString(6,userName);movement.setInt(7,userId);movement.setString(8,deviceId.toString());movement.setString(9,receive.receiveId());
                movement.setString(10,receive.deviceId());movement.setInt(11,receive.sequence());movement.executeUpdate();
            }
        }
        try(PreparedStatement ps=c.prepareStatement("UPDATE store_transfers SET status='RECEIVED',received_at=CURRENT_TIMESTAMP,received_by_user_id=?,received_by_name=?,receive_id=? WHERE transfer_id=? AND to_location_id=? AND UPPER(COALESCE(status,'PENDING'))='PENDING'")){
            ps.setInt(1,userId);ps.setString(2,userName);ps.setString(3,receive.receiveId());ps.setLong(4,transferId);ps.setInt(5,locationId);
            if(ps.executeUpdate()!=1)throw rule(409,"TRANSFER_CHANGED","Transfer changed before it could be received.");
        }
        SyncOutboxService.recordEvent(c,"INVENTORY_RECEIVED",map("source","STORE_TRANSFER","transfer_id",transferId,
                "receive_id",receive.receiveId(),"location_id",locationId,"user_id",userId),
                locationId,deviceId.toString(),userId);
        SyncOutboxService.recordEvent(c,"INVENTORY_MOVEMENT_CREATED",map("source","STORE_TRANSFER_RECEIVE",
                "transfer_id",transferId,"receive_id",receive.receiveId(),"location_id",locationId),
                locationId,deviceId.toString(),userId);
        CrossStoreTransferSyncService.recordReceived(c,transferId,locationId,deviceId,userId);
        return map("transferId",transferId,"receiveId",receive.receiveId(),"lineCount",lines.size());
    }

    private static String clean(String value,int max)throws RuleViolation{String v=value==null?"":value.trim();if(v.length()>max)throw rule(400,"VALIDATION_ERROR","Transfer note is too long.");return v;}
    private static boolean hasPermission(Connection c,int userId)throws SQLException{try(PreparedStatement ps=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions p ON p.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(p.permission_key)='STORE_TRANSFER' LIMIT 1")){ps.setInt(1,userId);try(ResultSet rs=ps.executeQuery()){return rs.next();}}}
    private static void requirePermission(Connection c,int userId)throws Exception{if(!hasPermission(c,userId))throw rule(403,"PERMISSION_DENIED","You do not have permission for store transfers.");}
    private static Map<String,Object>map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
    private static RuleViolation rule(int status,String code,String message){return new RuleViolation(status,code,message);}
    static final class RuleViolation extends Exception{private final int status;private final String code;private final String safeMessage;RuleViolation(int s,String c,String m){super(m);status=s;code=c;safeMessage=m;}int status(){return status;}String code(){return code;}String safeMessage(){return safeMessage;}}
    private record CreateRequest(int destinationLocationId,String note,List<TransferLine>lines){}
    private record TransferLine(int productId,int quantity){}
}
