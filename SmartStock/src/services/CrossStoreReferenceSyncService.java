package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Replicates shared store details and employee schedules between live store databases. */
final class CrossStoreReferenceSyncService {
    static final String EVENT = "REFERENCE_ROW_CHANGED";
    private static final List<TableSnapshot> TABLES = List.of(
            new TableSnapshot("locations", "location_id", "location_id::text", "updated_at"),
            new TableSnapshot("employee_schedule_shifts", "shift_id", "shift_id::text", "updated_at"),
            new TableSnapshot("employee_schedule_holidays", "holiday_date", "holiday_date::text", "updated_at"),
            new TableSnapshot("employee_schedule_assignments", "location_id,user_id,work_date",
                    "location_id::text||':'||user_id::text||':'||work_date::text", "updated_at"),
            new TableSnapshot("employee_time_clock", "clock_uuid", "clock_uuid::text", "updated_at"),
            new TableSnapshot("employee_payroll_bonuses", "sync_uuid", "sync_uuid::text", "created_at"),
            new TableSnapshot("payroll_payments", "sync_uuid", "sync_uuid::text", "created_at")
    );

    private CrossStoreReferenceSyncService() { }

    static int announceChanges(Connection connection, int locationId) throws SQLException {
        int announced = 0;
        for (TableSnapshot table : TABLES) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT " + table.keySql()
                    + ",to_jsonb(t)::text,"+table.timestampSql()+" FROM " + table.name() + " t ORDER BY "
                    + table.orderSql())) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String rowKey=rs.getString(1),rowData=rs.getString(2);
                        Timestamp updated=rs.getTimestamp(3);
                        String hash=LanSecurity.sha256(table.name()+"|UPSERT|"+rowKey+"|"+rowData);
                        if (alreadyKnown(connection,table.name(),rowKey,hash)) continue;
                        JsonObject payload=new JsonObject();
                        payload.addProperty("table_name",table.name());
                        payload.addProperty("operation","UPSERT");
                        payload.addProperty("row_key",rowKey);
                        payload.addProperty("row_hash",hash);
                        payload.add("row_data",JsonParser.parseString(rowData));
                        payload.addProperty("source_updated_at",updated.toInstant().toString());
                        SyncOutboxService.recordJsonEvent(connection,EVENT,payload,locationId,null,null);
                        announced++;
                    }
                }
            }
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT table_name,key_data::text,deleted_at
                FROM sync_tombstones
                WHERE table_name IN ('employee_schedule_assignments','employee_schedule_holidays')
                ORDER BY deleted_at,table_name,key_data::text
                """)) {
            try (ResultSet rs=ps.executeQuery()) {
                while(rs.next()) {
                    String table=rs.getString(1),keyData=rs.getString(2);
                    Instant deleted=rs.getTimestamp(3).toInstant();
                    JsonObject key=JsonParser.parseString(keyData).getAsJsonObject();
                    String rowKey=deleteKey(table,key);
                    String hash=LanSecurity.sha256(table+"|DELETE|"+rowKey+"|"+deleted);
                    if(alreadyKnown(connection,table,rowKey,hash))continue;
                    JsonObject payload=new JsonObject();payload.addProperty("table_name",table);
                    payload.addProperty("operation","DELETE");payload.addProperty("row_key",rowKey);
                    payload.addProperty("row_hash",hash);payload.add("key_data",key);
                    payload.addProperty("deleted_at",deleted.toString());
                    SyncOutboxService.recordJsonEvent(connection,EVENT,payload,locationId,null,null);
                    announced++;
                }
            }
        }
        return announced;
    }

    static int applyInbox(Connection connection) throws SQLException {
        List<InboxEvent> events=new ArrayList<>();
        try(PreparedStatement ps=connection.prepareStatement("""
                SELECT cloud_sequence,payload FROM sync_inbox
                WHERE event_type='REFERENCE_ROW_CHANGED' AND status IN ('RECEIVED','FAILED')
                ORDER BY cloud_sequence
                """ );ResultSet rs=ps.executeQuery()){
            while(rs.next())events.add(new InboxEvent(rs.getLong(1),rs.getString(2)));
        }
        boolean oldAuto=connection.getAutoCommit();int applied=0;
        for(InboxEvent event:events){connection.setAutoCommit(false);try{
            JsonObject payload=JsonParser.parseString(event.payload()).getAsJsonObject();
            applyPayload(connection,payload);
            mark(connection,event.sequence(),"APPLIED",null);connection.commit();applied++;
        }catch(Exception ex){connection.rollback();mark(connection,event.sequence(),"FAILED",safeError(ex));connection.commit();}
        finally{connection.setAutoCommit(oldAuto);}}
        return applied;
    }

    static void applyPayload(Connection connection,JsonObject payload)throws SQLException{
        String table=required(payload,"table_name"),operation=required(payload,"operation");
        if("UPSERT".equals(operation))upsert(connection,table,payload.getAsJsonObject("row_data"));
        else if("DELETE".equals(operation))delete(connection,table,payload.getAsJsonObject("key_data"),
                Instant.parse(required(payload,"deleted_at")));
        else throw new SQLException("Shared reference event has an invalid operation.");
    }

    private static boolean alreadyKnown(Connection c,String table,String key,String hash)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT 1 FROM sync_outbox
                WHERE event_type='REFERENCE_ROW_CHANGED' AND payload->>'table_name'=?
                  AND payload->>'row_key'=? AND payload->>'row_hash'=?
                UNION ALL
                SELECT 1 FROM sync_inbox
                WHERE event_type='REFERENCE_ROW_CHANGED' AND payload->>'table_name'=?
                  AND payload->>'row_key'=? AND payload->>'row_hash'=?
                LIMIT 1
                """)){ps.setString(1,table);ps.setString(2,key);ps.setString(3,hash);
            ps.setString(4,table);ps.setString(5,key);ps.setString(6,hash);
            try(ResultSet rs=ps.executeQuery()){return rs.next();}}
    }

    private static void upsert(Connection c,String table,JsonObject row)throws SQLException{
        JsonObject key=keyForRow(table,row);
        String timestampColumn=("employee_payroll_bonuses".equals(table)||"payroll_payments".equals(table))?"created_at":"updated_at";
        Timestamp updated=timestamp(row,timestampColumn);
        try(PreparedStatement ps=c.prepareStatement("SELECT deleted_at FROM sync_tombstones WHERE table_name=? AND key_data=?::jsonb")){
            ps.setString(1,table);ps.setString(2,key.toString());try(ResultSet rs=ps.executeQuery()){
                if(rs.next()&&!rs.getTimestamp(1).before(updated))return;
            }}
        switch(table){
            case "locations"->upsertLocation(c,row);
            case "employee_schedule_shifts"->upsertShift(c,row);
            case "employee_schedule_holidays"->upsertHoliday(c,row);
            case "employee_schedule_assignments"->upsertAssignment(c,row);
            case "employee_time_clock"->upsertTimeClock(c,row);
            case "employee_payroll_bonuses"->insertPayrollBonus(c,row);
            case "payroll_payments"->insertPayrollPayment(c,row);
            default->throw new SQLException("Shared reference event targets an unapproved table.");
        }
        try(PreparedStatement ps=c.prepareStatement("DELETE FROM sync_tombstones WHERE table_name=? AND key_data=?::jsonb AND deleted_at<?")){
            ps.setString(1,table);ps.setString(2,key.toString());ps.setTimestamp(3,updated);ps.executeUpdate();}
    }

    private static void upsertLocation(Connection c,JsonObject r)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO locations(location_id,name,address,company_address_line1,company_address_line2,
                  company_address_line3,company_phone_line1,company_phone_line2,company_email_line1,
                  company_email_line2,receipt_store_code,timezone,created_at,balance_sheet_recipient_email,
                  updated_at,email_sender_address,email_sender_name,email_bcc_address,email_receipts_enabled,
                  email_order_confirmations_enabled,email_quotes_enabled,email_invoices_enabled,
                  email_delivery_bills_enabled,email_connected_at,email_last_tested_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(location_id) DO UPDATE SET name=EXCLUDED.name,address=EXCLUDED.address,
                  company_address_line1=EXCLUDED.company_address_line1,company_address_line2=EXCLUDED.company_address_line2,
                  company_address_line3=EXCLUDED.company_address_line3,company_phone_line1=EXCLUDED.company_phone_line1,
                  company_phone_line2=EXCLUDED.company_phone_line2,company_email_line1=EXCLUDED.company_email_line1,
                  company_email_line2=EXCLUDED.company_email_line2,receipt_store_code=EXCLUDED.receipt_store_code,
                  timezone=EXCLUDED.timezone,balance_sheet_recipient_email=EXCLUDED.balance_sheet_recipient_email,
                  updated_at=EXCLUDED.updated_at,email_sender_address=EXCLUDED.email_sender_address,
                  email_sender_name=EXCLUDED.email_sender_name,email_bcc_address=EXCLUDED.email_bcc_address,
                  email_receipts_enabled=EXCLUDED.email_receipts_enabled,
                  email_order_confirmations_enabled=EXCLUDED.email_order_confirmations_enabled,
                  email_quotes_enabled=EXCLUDED.email_quotes_enabled,email_invoices_enabled=EXCLUDED.email_invoices_enabled,
                  email_delivery_bills_enabled=EXCLUDED.email_delivery_bills_enabled,
                  email_connected_at=EXCLUDED.email_connected_at,email_last_tested_at=EXCLUDED.email_last_tested_at
                WHERE locations.updated_at<EXCLUDED.updated_at
                """)){int i=1;ps.setInt(i++,integer(r,"location_id"));ps.setString(i++,text(r,"name"));
            nullableText(ps,i++,r,"address");ps.setString(i++,text(r,"company_address_line1"));
            ps.setString(i++,text(r,"company_address_line2"));ps.setString(i++,text(r,"company_address_line3"));
            ps.setString(i++,text(r,"company_phone_line1"));ps.setString(i++,text(r,"company_phone_line2"));
            ps.setString(i++,text(r,"company_email_line1"));ps.setString(i++,text(r,"company_email_line2"));
            ps.setString(i++,text(r,"receipt_store_code"));ps.setString(i++,text(r,"timezone"));
            ps.setTimestamp(i++,timestamp(r,"created_at"));ps.setString(i++,text(r,"balance_sheet_recipient_email"));
            ps.setTimestamp(i++,timestamp(r,"updated_at"));ps.setString(i++,text(r,"email_sender_address"));
            ps.setString(i++,text(r,"email_sender_name"));ps.setString(i++,text(r,"email_bcc_address"));
            ps.setBoolean(i++,bool(r,"email_receipts_enabled"));ps.setBoolean(i++,bool(r,"email_order_confirmations_enabled"));
            ps.setBoolean(i++,bool(r,"email_quotes_enabled"));ps.setBoolean(i++,bool(r,"email_invoices_enabled"));
            ps.setBoolean(i++,bool(r,"email_delivery_bills_enabled"));nullableTimestamp(ps,i++,r,"email_connected_at");
            nullableTimestamp(ps,i,r,"email_last_tested_at");ps.executeUpdate();}
    }

    private static void upsertShift(Connection c,JsonObject r)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO employee_schedule_shifts(shift_id,location_id,shift_name,start_time,end_time,is_active,
                  display_order,created_by_user_id,created_by_name,updated_by_user_id,updated_by_name,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(shift_id) DO UPDATE SET location_id=EXCLUDED.location_id,
                  shift_name=EXCLUDED.shift_name,start_time=EXCLUDED.start_time,end_time=EXCLUDED.end_time,
                  is_active=EXCLUDED.is_active,display_order=EXCLUDED.display_order,
                  updated_by_user_id=EXCLUDED.updated_by_user_id,updated_by_name=EXCLUDED.updated_by_name,
                  updated_at=EXCLUDED.updated_at WHERE employee_schedule_shifts.updated_at<EXCLUDED.updated_at
                """)){int i=1;ps.setObject(i++,uuid(r,"shift_id"));ps.setInt(i++,integer(r,"location_id"));
            ps.setString(i++,text(r,"shift_name"));ps.setTime(i++,time(r,"start_time"));ps.setTime(i++,time(r,"end_time"));
            ps.setBoolean(i++,bool(r,"is_active"));ps.setInt(i++,integer(r,"display_order"));nullableInt(ps,i++,r,"created_by_user_id");
            nullableText(ps,i++,r,"created_by_name");nullableInt(ps,i++,r,"updated_by_user_id");nullableText(ps,i++,r,"updated_by_name");
            ps.setTimestamp(i++,timestamp(r,"created_at"));ps.setTimestamp(i,timestamp(r,"updated_at"));ps.executeUpdate();}
    }

    private static void upsertHoliday(Connection c,JsonObject r)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO employee_schedule_holidays(holiday_id,holiday_date,holiday_name,created_by_user_id,
                  created_by_name,updated_by_user_id,updated_by_name,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)
                ON CONFLICT(holiday_date) DO UPDATE SET holiday_id=EXCLUDED.holiday_id,holiday_name=EXCLUDED.holiday_name,
                  updated_by_user_id=EXCLUDED.updated_by_user_id,updated_by_name=EXCLUDED.updated_by_name,
                  updated_at=EXCLUDED.updated_at WHERE employee_schedule_holidays.updated_at<EXCLUDED.updated_at
                """)){int i=1;ps.setObject(i++,uuid(r,"holiday_id"));ps.setDate(i++,date(r,"holiday_date"));
            ps.setString(i++,text(r,"holiday_name"));nullableInt(ps,i++,r,"created_by_user_id");nullableText(ps,i++,r,"created_by_name");
            nullableInt(ps,i++,r,"updated_by_user_id");nullableText(ps,i++,r,"updated_by_name");
            ps.setTimestamp(i++,timestamp(r,"created_at"));ps.setTimestamp(i,timestamp(r,"updated_at"));ps.executeUpdate();}
    }

    private static void upsertAssignment(Connection c,JsonObject r)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO employee_schedule_assignments(location_id,user_id,work_date,lunch_start_time,shift_id,
                  shift_name_snapshot,shift_start_time,shift_end_time,created_by_user_id,created_by_name,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(location_id,user_id,work_date) DO UPDATE SET
                  lunch_start_time=EXCLUDED.lunch_start_time,shift_id=EXCLUDED.shift_id,
                  shift_name_snapshot=EXCLUDED.shift_name_snapshot,shift_start_time=EXCLUDED.shift_start_time,
                  shift_end_time=EXCLUDED.shift_end_time,created_by_user_id=EXCLUDED.created_by_user_id,
                  created_by_name=EXCLUDED.created_by_name,updated_at=EXCLUDED.updated_at
                WHERE employee_schedule_assignments.updated_at<EXCLUDED.updated_at
                """)){int i=1;ps.setInt(i++,integer(r,"location_id"));ps.setInt(i++,integer(r,"user_id"));
            ps.setDate(i++,date(r,"work_date"));nullableTime(ps,i++,r,"lunch_start_time");nullableUuid(ps,i++,r,"shift_id");
            nullableText(ps,i++,r,"shift_name_snapshot");nullableTime(ps,i++,r,"shift_start_time");nullableTime(ps,i++,r,"shift_end_time");
            nullableInt(ps,i++,r,"created_by_user_id");nullableText(ps,i++,r,"created_by_name");
            ps.setTimestamp(i++,timestamp(r,"created_at"));ps.setTimestamp(i,timestamp(r,"updated_at"));ps.executeUpdate();}
    }

    private static void upsertTimeClock(Connection c,JsonObject r)throws SQLException{
        String columns="clock_uuid,user_id,user_name,location_id,location_name,work_date,clock_in,lunch_start,lunch_end,break_start,break_end,clock_out,total_hours_worked,total_earned,created_at,updated_at,auto_break_end,auto_break_end_detected_at,auto_break_end_review_status,multiple_session_override_required,multiple_session_override_reason,multiple_session_override_by_user_id,multiple_session_override_by_name,auto_close_enabled_snapshot,auto_close_rule_snapshot,auto_close_detection_at,auto_close_max_work_hours,scheduled_shift_id_snapshot,scheduled_shift_name_snapshot,scheduled_shift_end_at_snapshot,auto_clock_out,auto_clock_out_detected_at,auto_clock_out_review_status,auto_clock_out_reviewed_at,auto_clock_out_reviewed_by_user_id,auto_clock_out_reviewed_by_name,auto_clock_out_review_reason";
        String sql="WITH incoming AS (SELECT (jsonb_populate_record(NULL::employee_time_clock,?::jsonb)).*) INSERT INTO employee_time_clock("+columns+") SELECT "+columns+" FROM incoming ON CONFLICT(clock_uuid) DO UPDATE SET user_id=EXCLUDED.user_id,user_name=EXCLUDED.user_name,location_id=EXCLUDED.location_id,location_name=EXCLUDED.location_name,work_date=EXCLUDED.work_date,clock_in=EXCLUDED.clock_in,lunch_start=EXCLUDED.lunch_start,lunch_end=EXCLUDED.lunch_end,break_start=EXCLUDED.break_start,break_end=EXCLUDED.break_end,clock_out=EXCLUDED.clock_out,total_hours_worked=EXCLUDED.total_hours_worked,total_earned=EXCLUDED.total_earned,updated_at=EXCLUDED.updated_at,auto_break_end=EXCLUDED.auto_break_end,auto_break_end_detected_at=EXCLUDED.auto_break_end_detected_at,auto_break_end_review_status=EXCLUDED.auto_break_end_review_status,multiple_session_override_required=EXCLUDED.multiple_session_override_required,multiple_session_override_reason=EXCLUDED.multiple_session_override_reason,multiple_session_override_by_user_id=EXCLUDED.multiple_session_override_by_user_id,multiple_session_override_by_name=EXCLUDED.multiple_session_override_by_name,auto_close_enabled_snapshot=EXCLUDED.auto_close_enabled_snapshot,auto_close_rule_snapshot=EXCLUDED.auto_close_rule_snapshot,auto_close_detection_at=EXCLUDED.auto_close_detection_at,auto_close_max_work_hours=EXCLUDED.auto_close_max_work_hours,scheduled_shift_id_snapshot=EXCLUDED.scheduled_shift_id_snapshot,scheduled_shift_name_snapshot=EXCLUDED.scheduled_shift_name_snapshot,scheduled_shift_end_at_snapshot=EXCLUDED.scheduled_shift_end_at_snapshot,auto_clock_out=EXCLUDED.auto_clock_out,auto_clock_out_detected_at=EXCLUDED.auto_clock_out_detected_at,auto_clock_out_review_status=EXCLUDED.auto_clock_out_review_status,auto_clock_out_reviewed_at=EXCLUDED.auto_clock_out_reviewed_at,auto_clock_out_reviewed_by_user_id=EXCLUDED.auto_clock_out_reviewed_by_user_id,auto_clock_out_reviewed_by_name=EXCLUDED.auto_clock_out_reviewed_by_name,auto_clock_out_review_reason=EXCLUDED.auto_clock_out_review_reason WHERE employee_time_clock.updated_at<EXCLUDED.updated_at";
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,r.toString());ps.executeUpdate();}
    }

    private static void insertPayrollBonus(Connection c,JsonObject r)throws SQLException{
        String cols="sync_uuid,user_id,location_id,employee_name,pay_period_start,pay_period_end,amount,reason,created_by_user_id,created_by_name,created_at";
        try(PreparedStatement ps=c.prepareStatement("WITH incoming AS (SELECT (jsonb_populate_record(NULL::employee_payroll_bonuses,?::jsonb)).*) INSERT INTO employee_payroll_bonuses("+cols+") SELECT "+cols+" FROM incoming ON CONFLICT(sync_uuid) DO NOTHING")){ps.setString(1,r.toString());ps.executeUpdate();}
    }

    private static void insertPayrollPayment(Connection c,JsonObject r)throws SQLException{
        String cols="sync_uuid,user_id,employee_name,employee_role,location_id,pay_period_start,pay_period_end,payment_number,pay_date,days_worked,total_hours,pay_period_type,work_hour_limit,regular_hours,overtime_hours,regular_pay,overtime_pay,total_pay,record_count,compensation_type,location_name,payment_method,payment_reference,paid_at,paid_by_user_id,paid_by_name,created_at";
        try(PreparedStatement ps=c.prepareStatement("WITH incoming AS (SELECT (jsonb_populate_record(NULL::payroll_payments,?::jsonb)).*) INSERT INTO payroll_payments("+cols+") SELECT "+cols+" FROM incoming ON CONFLICT(sync_uuid) DO NOTHING")){ps.setString(1,r.toString());ps.executeUpdate();}
    }

    private static void delete(Connection c,String table,JsonObject key,Instant deleted)throws SQLException{
        String sql=switch(table){
            case "employee_schedule_holidays"->"DELETE FROM employee_schedule_holidays WHERE holiday_date=? AND updated_at<=?";
            case "employee_schedule_assignments"->"DELETE FROM employee_schedule_assignments WHERE location_id=? AND user_id=? AND work_date=? AND updated_at<=?";
            default->throw new SQLException("Shared reference deletion targets an unapproved table.");};
        try(PreparedStatement ps=c.prepareStatement(sql)){if("employee_schedule_holidays".equals(table)){
            ps.setDate(1,date(key,"holiday_date"));ps.setTimestamp(2,Timestamp.from(deleted));
        }else{ps.setInt(1,integer(key,"location_id"));ps.setInt(2,integer(key,"user_id"));
            ps.setDate(3,date(key,"work_date"));ps.setTimestamp(4,Timestamp.from(deleted));}ps.executeUpdate();}
        try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO sync_tombstones(table_name,key_data,deleted_at)
                VALUES(?,?::jsonb,?) ON CONFLICT(table_name,key_data)
                DO UPDATE SET deleted_at=GREATEST(sync_tombstones.deleted_at,EXCLUDED.deleted_at)
                """)){ps.setString(1,table);ps.setString(2,key.toString());ps.setTimestamp(3,Timestamp.from(deleted));ps.executeUpdate();}
    }

    private static JsonObject keyForRow(String table,JsonObject row)throws SQLException{
        JsonObject key=new JsonObject();switch(table){
            case "locations"->key.addProperty("location_id",integer(row,"location_id"));
            case "employee_schedule_shifts"->key.addProperty("shift_id",required(row,"shift_id"));
            case "employee_schedule_holidays"->key.addProperty("holiday_date",required(row,"holiday_date"));
            case "employee_schedule_assignments"->{key.addProperty("location_id",integer(row,"location_id"));key.addProperty("user_id",integer(row,"user_id"));key.addProperty("work_date",required(row,"work_date"));}
            case "employee_time_clock"->key.addProperty("clock_uuid",required(row,"clock_uuid"));
            case "employee_payroll_bonuses","payroll_payments"->key.addProperty("sync_uuid",required(row,"sync_uuid"));
            default->throw new SQLException("Shared reference event targets an unapproved table.");}
        return key;
    }

    private static String deleteKey(String table,JsonObject key)throws SQLException{return switch(table){
        case "employee_schedule_holidays"->required(key,"holiday_date");
        case "employee_schedule_assignments"->integer(key,"location_id")+":"+integer(key,"user_id")+":"+required(key,"work_date");
        default->throw new SQLException("Unsupported shared reference tombstone.");};}
    private static void mark(Connection c,long seq,String status,String error)throws SQLException{try(PreparedStatement ps=c.prepareStatement("UPDATE sync_inbox SET status=?,applied_at=CASE WHEN ?='APPLIED' THEN CURRENT_TIMESTAMP ELSE applied_at END,last_error=? WHERE cloud_sequence=?")){ps.setString(1,status);ps.setString(2,status);ps.setString(3,error);ps.setLong(4,seq);ps.executeUpdate();}}
    private static String required(JsonObject r,String k)throws SQLException{if(r==null||!r.has(k)||r.get(k).isJsonNull())throw new SQLException("Shared reference row is missing "+k+".");return r.get(k).getAsString();}
    private static String text(JsonObject r,String k)throws SQLException{return required(r,k);}
    private static int integer(JsonObject r,String k)throws SQLException{try{return r.get(k).getAsInt();}catch(Exception e){throw new SQLException("Shared reference row has invalid "+k+".",e);}}
    private static boolean bool(JsonObject r,String k)throws SQLException{try{return r.get(k).getAsBoolean();}catch(Exception e){throw new SQLException("Shared reference row has invalid "+k+".",e);}}
    private static UUID uuid(JsonObject r,String k)throws SQLException{try{return UUID.fromString(required(r,k));}catch(Exception e){throw new SQLException("Shared reference row has invalid "+k+".",e);}}
    private static Timestamp timestamp(JsonObject r,String k)throws SQLException{try{return Timestamp.from(Instant.parse(required(r,k)));}catch(Exception e){throw new SQLException("Shared reference row has invalid "+k+".",e);}}
    private static Date date(JsonObject r,String k)throws SQLException{try{return Date.valueOf(LocalDate.parse(required(r,k)));}catch(Exception e){throw new SQLException("Shared reference row has invalid "+k+".",e);}}
    private static Time time(JsonObject r,String k)throws SQLException{try{return Time.valueOf(LocalTime.parse(required(r,k)));}catch(Exception e){throw new SQLException("Shared reference row has invalid "+k+".",e);}}
    private static void nullableText(PreparedStatement p,int i,JsonObject r,String k)throws SQLException{if(!r.has(k)||r.get(k).isJsonNull())p.setNull(i,java.sql.Types.VARCHAR);else p.setString(i,r.get(k).getAsString());}
    private static void nullableInt(PreparedStatement p,int i,JsonObject r,String k)throws SQLException{if(!r.has(k)||r.get(k).isJsonNull())p.setNull(i,java.sql.Types.INTEGER);else p.setInt(i,r.get(k).getAsInt());}
    private static void nullableUuid(PreparedStatement p,int i,JsonObject r,String k)throws SQLException{if(!r.has(k)||r.get(k).isJsonNull())p.setNull(i,java.sql.Types.OTHER);else p.setObject(i,UUID.fromString(r.get(k).getAsString()));}
    private static void nullableTimestamp(PreparedStatement p,int i,JsonObject r,String k)throws SQLException{if(!r.has(k)||r.get(k).isJsonNull())p.setNull(i,java.sql.Types.TIMESTAMP_WITH_TIMEZONE);else p.setTimestamp(i,timestamp(r,k));}
    private static void nullableTime(PreparedStatement p,int i,JsonObject r,String k)throws SQLException{if(!r.has(k)||r.get(k).isJsonNull())p.setNull(i,java.sql.Types.TIME);else p.setTime(i,time(r,k));}
    private static String safeError(Exception e){String v=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();return v.substring(0,Math.min(v.length(),1000));}
    private record TableSnapshot(String name,String orderSql,String keySql,String timestampSql) { }
    private record InboxEvent(long sequence,String payload) { }
}
