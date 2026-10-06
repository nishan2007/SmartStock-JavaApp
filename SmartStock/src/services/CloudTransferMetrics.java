package services;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Stores byte counts only; cloud payloads and credentials are never logged. */
final class CloudTransferMetrics {
    private CloudTransferMetrics() {
    }

    static void record(Connection local, String operation, String requestBody,
                       String responseBody) throws SQLException {
        record(local,operation,null,requestBody,responseBody);
    }

    static void record(Connection local, String operation, Integer sourceLocationId,
                       String requestBody, String responseBody) throws SQLException {
        try (PreparedStatement cleanup = local.prepareStatement("""
                DELETE FROM sync_transfer_metrics
                WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '90 days'
                """)) {
            cleanup.executeUpdate();
        }
        try (PreparedStatement ps = local.prepareStatement("""
                INSERT INTO sync_transfer_metrics(operation,request_bytes,response_bytes,source_location_id)
                VALUES (?,?,?,?)
                """)) {
            ps.setString(1, operation);
            ps.setLong(2, bytes(requestBody));
            ps.setLong(3, bytes(responseBody));
            if(sourceLocationId==null)ps.setNull(4,java.sql.Types.INTEGER);else ps.setInt(4,sourceLocationId);
            ps.executeUpdate();
        }
    }

    static java.util.Map<String,Object> totals(Connection local) throws SQLException {
        java.time.ZoneId zone=java.time.ZoneId.of("UTC");
        try(PreparedStatement p=local.prepareStatement("SELECT timezone FROM locations WHERE location_id=?")){
            Integer location=data.DatabaseConfig.load().locationId();
            if(location==null)p.setNull(1,java.sql.Types.INTEGER);else p.setInt(1,location);
            try(var r=p.executeQuery()){if(r.next())try{zone=java.time.ZoneId.of(r.getString(1));}catch(Exception ignored){}}
        }
        java.time.Instant now=java.time.Instant.now(),today=now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant();
        java.time.Instant billing=null,earliest=null;
        try(var p=local.prepareStatement("SELECT billing_period_start FROM sync_transfer_settings WHERE settings_id=1");var r=p.executeQuery()){
            if(r.next()&&r.getTimestamp(1)!=null)billing=r.getTimestamp(1).toInstant();
        }
        java.util.Map<String,Object> result=new java.util.LinkedHashMap<>();
        result.put("transferMeasurementLabel","Application payload bytes; not exact Supabase billing");
        result.put("transferBillingStartEpochMillis",billing==null?0L:billing.toEpochMilli());
        try(var p=local.prepareStatement("""
            SELECT COALESCE(SUM(request_bytes) FILTER(WHERE created_at>=?),0),
                   COALESCE(SUM(response_bytes) FILTER(WHERE created_at>=?),0),
                   COALESCE(SUM(request_bytes) FILTER(WHERE created_at>=?),0),
                   COALESCE(SUM(response_bytes) FILTER(WHERE created_at>=?),0),MIN(created_at)
            FROM sync_transfer_metrics WHERE created_at<=?
            """)){
            p.setTimestamp(1,java.sql.Timestamp.from(today));p.setTimestamp(2,java.sql.Timestamp.from(today));
            p.setTimestamp(3,billing==null?null:java.sql.Timestamp.from(billing));p.setTimestamp(4,billing==null?null:java.sql.Timestamp.from(billing));
            p.setTimestamp(5,java.sql.Timestamp.from(now));
            try(var r=p.executeQuery()){r.next();result.put("transferTodayRequestBytes",r.getLong(1));result.put("transferTodayResponseBytes",r.getLong(2));
                result.put("transferBillingRequestBytes",r.getLong(3));result.put("transferBillingResponseBytes",r.getLong(4));
                if(r.getTimestamp(5)!=null)earliest=r.getTimestamp(5).toInstant();}
        }
        result.put("transferMeasuredSinceEpochMillis",earliest==null?0L:earliest.toEpochMilli());
        return result;
    }

    private static int bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }
}
