package services;

import data.DB;
import managers.ServerReceiptNumberManager;
import utils.DeviceUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Preflights and atomically moves this server's paired desktop registers to another store. */
public final class ServerStoreSwitchService {
    private ServerStoreSwitchService() { }

    public static Preflight preflight(int sourceLocationId,int destinationLocationId)throws SQLException{
        try(Connection connection=DB.getConnection()){
            return preflight(connection,sourceLocationId,destinationLocationId);
        }
    }

    public static int switchServerStore(int sourceLocationId,int destinationLocationId)throws Exception{
        try(Connection connection=DB.getConnection()){
            connection.setAutoCommit(false);
            try{
                int moved=switchPairedRegisters(connection,sourceLocationId,destinationLocationId);
                DeviceCredentialService.assignLocalInstallationToStore(connection,destinationLocationId);
                connection.commit();return moved;
            }catch(Exception ex){connection.rollback();throw ex;}
            finally{connection.setAutoCommit(true);}
        }
    }

    public static Preflight preflight(Connection connection, int sourceLocationId,
                                      int destinationLocationId) throws SQLException {
        List<String> blockers=new ArrayList<>();
        try(PreparedStatement ps=connection.prepareStatement("""
                SELECT drawer_name,COALESCE(device_name,'Unknown device'),opened_at
                FROM cash_drawer_sessions
                WHERE location_id=? AND UPPER(COALESCE(status,''))='OPEN'
                ORDER BY opened_at
                """)){
            ps.setInt(1,sourceLocationId);try(ResultSet rs=ps.executeQuery()){while(rs.next())blockers.add(
                    "Close and balance drawer '"+rs.getString(1)+"' on "+rs.getString(2)
                            +" (open since "+rs.getTimestamp(3)+").");}
        }
        try(PreparedStatement ps=connection.prepareStatement("""
                SELECT COUNT(*) FROM register_transfers
                WHERE status='PREPARED' AND (source_location_id=? OR destination_location_id=?)
                  AND expires_at>CURRENT_TIMESTAMP
                """)){
            ps.setInt(1,sourceLocationId);ps.setInt(2,sourceLocationId);
            try(ResultSet rs=ps.executeQuery()){rs.next();if(rs.getInt(1)>0)blockers.add(
                    "Complete or cancel "+rs.getInt(1)+" prepared register transfer(s) involving the current store.");}
        }
        int registers;
        try(PreparedStatement ps=connection.prepareStatement("""
                SELECT COUNT(*) FROM devices
                WHERE last_store_id=? AND is_approved=TRUE AND is_blocked=FALSE
                  AND UPPER(COALESCE(access_mode,'CLIENT'))='CLIENT'
                  AND NULLIF(TRIM(hostname),'') IS NOT NULL
                  AND UPPER(COALESCE(credential_status,'')) IN ('CLAIMED','ISSUED')
                  AND installation_id<>?
                """)){
            ps.setInt(1,sourceLocationId);ps.setString(2,DeviceUtils.collectDeviceInfo().getInstallationId());
            try(ResultSet rs=ps.executeQuery()){rs.next();registers=rs.getInt(1);}
        }
        return new Preflight(sourceLocationId,destinationLocationId,registers,List.copyOf(blockers));
    }

    public static int switchPairedRegisters(Connection connection, int sourceLocationId,
                                            int destinationLocationId) throws Exception {
        try(PreparedStatement lock=connection.prepareStatement("SELECT pg_advisory_xact_lock(?,?)")){
            lock.setInt(1,0x53534B53);lock.setInt(2,sourceLocationId);lock.execute();
        }
        try(PreparedStatement lockTables=connection.prepareStatement(
                "LOCK TABLE cash_drawer_sessions,register_transfers IN SHARE ROW EXCLUSIVE MODE")){
            lockTables.execute();
        }
        Preflight current=preflight(connection,sourceLocationId,destinationLocationId);
        if(!current.blockers().isEmpty())throw new IllegalStateException(current.blockerMessage());
        List<UUID> devices=new ArrayList<>();
        try(PreparedStatement ps=connection.prepareStatement("""
                SELECT device_id FROM devices
                WHERE last_store_id=? AND is_approved=TRUE AND is_blocked=FALSE
                  AND UPPER(COALESCE(access_mode,'CLIENT'))='CLIENT'
                  AND NULLIF(TRIM(hostname),'') IS NOT NULL
                  AND UPPER(COALESCE(credential_status,'')) IN ('CLAIMED','ISSUED')
                  AND installation_id<>?
                ORDER BY first_seen,device_id FOR UPDATE
                """)){
            ps.setInt(1,sourceLocationId);ps.setString(2,DeviceUtils.collectDeviceInfo().getInstallationId());
            try(ResultSet rs=ps.executeQuery()){while(rs.next())devices.add((UUID)rs.getObject(1));}
        }
        for(UUID deviceId:devices){
            try(PreparedStatement ps=connection.prepareStatement("""
                    UPDATE cash_drawer_device_assignments SET is_active=FALSE,
                      unassigned_at=CURRENT_TIMESTAMP,notes=CONCAT_WS(' | ',NULLIF(notes,''),?),
                      updated_at=CURRENT_TIMESTAMP
                    WHERE device_id=? AND location_id=? AND is_active=TRUE
                    """)){
                ps.setString(1,"Server store switch to location "+destinationLocationId);
                ps.setObject(2,deviceId);ps.setInt(3,sourceLocationId);ps.executeUpdate();
            }
            try(PreparedStatement ps=connection.prepareStatement("""
                    UPDATE device_sessions SET logout_time=CURRENT_TIMESTAMP,session_status='ENDED'
                    WHERE device_id=? AND logout_time IS NULL
                    """)){ps.setObject(1,deviceId);ps.executeUpdate();}
            try(PreparedStatement ps=connection.prepareStatement("""
                    UPDATE lan_api_sessions SET revoked_at=CURRENT_TIMESTAMP
                    WHERE device_id=? AND revoked_at IS NULL
                    """)){ps.setObject(1,deviceId);ps.executeUpdate();}
            try(PreparedStatement ps=connection.prepareStatement("""
                    UPDATE devices SET last_store_id=?,last_seen=CURRENT_TIMESTAMP WHERE device_id=?
                    """)){ps.setInt(1,destinationLocationId);ps.setObject(2,deviceId);ps.executeUpdate();}
            ServerReceiptNumberManager.assignCodeForEnrollment(connection,destinationLocationId,deviceId);
            try(PreparedStatement ps=connection.prepareStatement("""
                    INSERT INTO register_transfers(transfer_id,device_id,installation_id,source_location_id,
                      destination_location_id,status,emergency,reason,prepared_at,expires_at,completed_at)
                    SELECT gen_random_uuid(),device_id,installation_id,?,?,'COMPLETED',FALSE,?,
                      CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP FROM devices WHERE device_id=?
                    """)){
                ps.setInt(1,sourceLocationId);ps.setInt(2,destinationLocationId);
                ps.setString(3,"Automatic register migration during server store switch");ps.setObject(4,deviceId);
                ps.executeUpdate();
            }
            try(PreparedStatement ps=connection.prepareStatement("""
                    INSERT INTO security_audit_events(event_type,device_id,details)
                    VALUES('DEVICE_STORE_REASSIGNED',?,?)
                    """)){ps.setObject(1,deviceId);ps.setString(2,
                    "Register moved from store "+sourceLocationId+" to "+destinationLocationId
                            +" during server store switch");ps.executeUpdate();}
        }
        return devices.size();
    }

    public record Preflight(int sourceLocationId,int destinationLocationId,
                            int registerCount,List<String>blockers){
        public boolean ready(){return blockers.isEmpty();}
        public String blockerMessage(){return "The server cannot switch stores yet:\n\n- "+String.join("\n- ",blockers);}
    }
}
