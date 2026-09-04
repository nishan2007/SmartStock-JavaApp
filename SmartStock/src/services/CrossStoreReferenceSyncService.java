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

/** Replicates approved shared store, employee, schedule, time-clock, and payroll rows. */
final class CrossStoreReferenceSyncService {
    static final String EVENT = "REFERENCE_ROW_CHANGED";
    private static final List<TableSnapshot> TABLES = List.of(
            new TableSnapshot("locations", "location_id", "location_id::text", "updated_at"),
            new TableSnapshot("users", "user_id", "user_id::text", "updated_at", """
                    to_jsonb(t)
                      - ARRAY['password_hash','password_cache_invalidated_at','employee_pin_salt',
                              'employee_pin_hash','employee_pin_updated_at','badge_secret_salt',
                              'badge_secret_hash']::text[]
                      || jsonb_build_object('role_name',
                           (SELECT r.role_name FROM roles r WHERE r.role_id=t.role_id))
                    """),
            new TableSnapshot("user_locations", "user_id,location_id",
                    "user_id::text||':'||location_id::text", "updated_at"),
            new TableSnapshot("employee_wallet_credentials", "wallet_credential_id",
                    "wallet_credential_id::text", "updated_at"),
            new TableSnapshot("employee_payroll_settings", "setting_id", "setting_id::text", "updated_at"),
            new TableSnapshot("employee_schedule_shifts", "shift_id", "shift_id::text", "updated_at"),
            new TableSnapshot("employee_schedule_holidays", "holiday_date", "holiday_date::text", "updated_at"),
            new TableSnapshot("employee_schedule_assignments", "location_id,user_id,work_date",
                    "location_id::text||':'||user_id::text||':'||work_date::text", "updated_at"),
            new TableSnapshot("employee_time_clock", "clock_uuid", "clock_uuid::text", "updated_at"),
            new TableSnapshot("employee_time_clock_adjustments", "adjustment_id", "adjustment_id::text", "created_at",
                    "to_jsonb(t) - 'clock_id' || jsonb_build_object('clock_uuid',"
                            + "(SELECT tc.clock_uuid FROM employee_time_clock tc WHERE tc.clock_id=t.clock_id))"),
            new TableSnapshot("employee_payroll_bonuses", "sync_uuid", "sync_uuid::text", "created_at"),
            new TableSnapshot("payroll_payments", "sync_uuid", "sync_uuid::text", "created_at")
    );

    private CrossStoreReferenceSyncService() { }

    static int announceChanges(Connection connection, int locationId) throws SQLException {
        int announced = 0;
        for (TableSnapshot table : TABLES) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT " + table.keySql()
                    + ",("+table.rowSql()+")::text,"+table.timestampSql()+" FROM " + table.name() + " t ORDER BY "
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
                        if("users".equals(table.name()))
                            payload.add("protected_credentials",protectedCredentials(connection,
                                    payload.getAsJsonObject("row_data").get("user_id").getAsInt()));
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
                WHERE table_name IN ('user_locations','employee_schedule_assignments','employee_schedule_holidays')
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
        if("UPSERT".equals(operation)){
            upsert(connection,table,payload.getAsJsonObject("row_data"));
            if("users".equals(table)&&payload.has("protected_credentials"))
                applyProtectedCredentials(connection,payload.getAsJsonObject("protected_credentials"));
        }
        else if("DELETE".equals(operation))delete(connection,table,payload.getAsJsonObject("key_data"),
                Instant.parse(required(payload,"deleted_at")));
        else throw new SQLException("Shared reference event has an invalid operation.");
    }

    private static JsonObject protectedCredentials(Connection c,int userId)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("""
                SELECT jsonb_build_object('user_id',user_id,
                  'employee_pin_salt',employee_pin_salt,'employee_pin_hash',employee_pin_hash,
                  'employee_pin_updated_at',employee_pin_updated_at,
                  'badge_secret_salt',badge_secret_salt,'badge_secret_hash',badge_secret_hash,
                  'badge_generated_at',badge_generated_at,'badge_rotated_at',badge_rotated_at)::text
                FROM users WHERE user_id=?
                """)){p.setInt(1,userId);try(ResultSet rs=p.executeQuery()){
            if(!rs.next())throw new SQLException("Protected employee credentials are unavailable.");
            return JsonParser.parseString(rs.getString(1)).getAsJsonObject();
        }}
    }

    private static void applyProtectedCredentials(Connection c,JsonObject credentials)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("""
                WITH incoming AS (
                  SELECT (jsonb_populate_record(NULL::users,?::jsonb)).*
                )
                UPDATE users u SET
                  employee_pin_salt=CASE WHEN i.employee_pin_updated_at IS NOT NULL AND
                    i.employee_pin_updated_at>=COALESCE(u.employee_pin_updated_at,'epoch'::timestamptz)
                    THEN i.employee_pin_salt ELSE u.employee_pin_salt END,
                  employee_pin_hash=CASE WHEN i.employee_pin_updated_at IS NOT NULL AND
                    i.employee_pin_updated_at>=COALESCE(u.employee_pin_updated_at,'epoch'::timestamptz)
                    THEN i.employee_pin_hash ELSE u.employee_pin_hash END,
                  employee_pin_updated_at=GREATEST(u.employee_pin_updated_at,i.employee_pin_updated_at),
                  badge_secret_salt=CASE WHEN COALESCE(i.badge_rotated_at,i.badge_generated_at) IS NOT NULL AND
                    COALESCE(i.badge_rotated_at,i.badge_generated_at)>=
                    COALESCE(u.badge_rotated_at,u.badge_generated_at,'epoch'::timestamptz)
                    THEN i.badge_secret_salt ELSE u.badge_secret_salt END,
                  badge_secret_hash=CASE WHEN COALESCE(i.badge_rotated_at,i.badge_generated_at) IS NOT NULL AND
                    COALESCE(i.badge_rotated_at,i.badge_generated_at)>=
                    COALESCE(u.badge_rotated_at,u.badge_generated_at,'epoch'::timestamptz)
                    THEN i.badge_secret_hash ELSE u.badge_secret_hash END
                FROM incoming i WHERE u.user_id=i.user_id
                """)){p.setString(1,credentials.toString());p.executeUpdate();}
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
        String timestampColumn=("employee_time_clock_adjustments".equals(table)
                ||"employee_payroll_bonuses".equals(table)||"payroll_payments".equals(table))?"created_at":"updated_at";
        Timestamp updated=timestamp(row,timestampColumn);
        try(PreparedStatement ps=c.prepareStatement("SELECT deleted_at FROM sync_tombstones WHERE table_name=? AND key_data=?::jsonb")){
            ps.setString(1,table);ps.setString(2,key.toString());try(ResultSet rs=ps.executeQuery()){
                if(rs.next()&&!rs.getTimestamp(1).before(updated))return;
            }}
        switch(table){
            case "locations"->upsertLocation(c,row);
            case "users"->upsertUser(c,row);
            case "user_locations"->upsertUserLocation(c,row);
            case "employee_wallet_credentials"->upsertWalletCredential(c,row);
            case "employee_payroll_settings"->upsertPayrollSetting(c,row);
            case "employee_schedule_shifts"->upsertShift(c,row);
            case "employee_schedule_holidays"->upsertHoliday(c,row);
            case "employee_schedule_assignments"->upsertAssignment(c,row);
            case "employee_time_clock"->upsertTimeClock(c,row);
            case "employee_time_clock_adjustments"->insertTimeClockAdjustment(c,row);
            case "employee_payroll_bonuses"->insertPayrollBonus(c,row);
            case "payroll_payments"->insertPayrollPayment(c,row);
            default->throw new SQLException("Shared reference event targets an unapproved table.");
        }
        try(PreparedStatement ps=c.prepareStatement("DELETE FROM sync_tombstones WHERE table_name=? AND key_data=?::jsonb AND deleted_at<?")){
            ps.setString(1,table);ps.setString(2,key.toString());ps.setTimestamp(3,updated);ps.executeUpdate();}
    }

    private static void upsertUser(Connection c,JsonObject r)throws SQLException{
        String sql="""
                INSERT INTO users(user_id,username,first_name,middle_name,last_name,full_name,nickname,email,phone,
                  employee_photo_url,employee_id_card_document_url,date_of_birth,hire_date,badge_id,
                  badge_generated_at,badge_print_count,badge_rotated_at,badge_rotated_by_user_id,
                  badge_rotated_by_name,compensation_type,salary,role_id,auth_user_id,is_active,deactivated_at,
                  deactivated_by_user_id,deactivated_by_name,created_at,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,
                  (SELECT role_id FROM roles WHERE UPPER(role_name)=UPPER(?)),?::uuid,?,?,?,?,?,?)
                ON CONFLICT(user_id) DO UPDATE SET username=EXCLUDED.username,first_name=EXCLUDED.first_name,
                  middle_name=EXCLUDED.middle_name,last_name=EXCLUDED.last_name,full_name=EXCLUDED.full_name,
                  nickname=EXCLUDED.nickname,email=EXCLUDED.email,phone=EXCLUDED.phone,
                  employee_photo_url=EXCLUDED.employee_photo_url,
                  employee_id_card_document_url=EXCLUDED.employee_id_card_document_url,
                  date_of_birth=EXCLUDED.date_of_birth,hire_date=EXCLUDED.hire_date,
                  compensation_type=EXCLUDED.compensation_type,salary=EXCLUDED.salary,role_id=EXCLUDED.role_id,
                  auth_user_id=EXCLUDED.auth_user_id,is_active=EXCLUDED.is_active,
                  deactivated_at=EXCLUDED.deactivated_at,deactivated_by_user_id=EXCLUDED.deactivated_by_user_id,
                  deactivated_by_name=EXCLUDED.deactivated_by_name,updated_at=EXCLUDED.updated_at
                WHERE users.updated_at<EXCLUDED.updated_at
                  AND (users.auth_user_id IS NULL OR EXCLUDED.auth_user_id IS NULL
                       OR users.auth_user_id=EXCLUDED.auth_user_id)
                """;
        try(PreparedStatement p=c.prepareStatement(sql)){int i=1;
            p.setInt(i++,integer(r,"user_id"));p.setString(i++,text(r,"username"));nullableText(p,i++,r,"first_name");
            nullableText(p,i++,r,"middle_name");nullableText(p,i++,r,"last_name");p.setString(i++,text(r,"full_name"));
            nullableText(p,i++,r,"nickname");nullableText(p,i++,r,"email");nullableText(p,i++,r,"phone");
            nullableText(p,i++,r,"employee_photo_url");nullableText(p,i++,r,"employee_id_card_document_url");
            nullableDate(p,i++,r,"date_of_birth");p.setDate(i++,date(r,"hire_date"));nullableText(p,i++,r,"badge_id");
            nullableTimestamp(p,i++,r,"badge_generated_at");p.setInt(i++,integer(r,"badge_print_count"));
            nullableTimestamp(p,i++,r,"badge_rotated_at");nullableInt(p,i++,r,"badge_rotated_by_user_id");
            nullableText(p,i++,r,"badge_rotated_by_name");p.setObject(i++,text(r,"compensation_type"),java.sql.Types.OTHER);
            p.setBigDecimal(i++,r.get("salary").getAsBigDecimal());nullableText(p,i++,r,"role_name");
            nullableText(p,i++,r,"auth_user_id");p.setBoolean(i++,bool(r,"is_active"));nullableTimestamp(p,i++,r,"deactivated_at");
            nullableInt(p,i++,r,"deactivated_by_user_id");nullableText(p,i++,r,"deactivated_by_name");
            p.setTimestamp(i++,timestamp(r,"created_at"));p.setTimestamp(i,timestamp(r,"updated_at"));p.executeUpdate();}
        try(PreparedStatement p=c.prepareStatement("""
                SELECT setval(pg_get_serial_sequence('users','user_id'),
                  GREATEST((SELECT COALESCE(MAX(user_id),1) FROM users),
                           (SELECT last_value FROM users_user_id_seq)),true)
                """)){p.execute();}
    }

    private static void upsertUserLocation(Connection c,JsonObject r)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO user_locations(user_id,location_id,updated_at) VALUES(?,?,?)
                ON CONFLICT(user_id,location_id) DO UPDATE SET updated_at=EXCLUDED.updated_at
                WHERE user_locations.updated_at<EXCLUDED.updated_at
                """)){p.setInt(1,integer(r,"user_id"));p.setInt(2,integer(r,"location_id"));
            p.setTimestamp(3,timestamp(r,"updated_at"));p.executeUpdate();}
    }

    private static void upsertWalletCredential(Connection c,JsonObject r)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO employee_wallet_credentials(wallet_credential_id,user_id,credential_hash,serial_number,
                  status,issued_at,issued_by_user_id,revoked_at,revoked_by_user_id,last_used_at,updated_at)
                VALUES(?::uuid,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(wallet_credential_id) DO UPDATE SET credential_hash=EXCLUDED.credential_hash,
                  serial_number=EXCLUDED.serial_number,status=EXCLUDED.status,revoked_at=EXCLUDED.revoked_at,
                  revoked_by_user_id=EXCLUDED.revoked_by_user_id,last_used_at=EXCLUDED.last_used_at,
                  updated_at=EXCLUDED.updated_at
                WHERE employee_wallet_credentials.updated_at<EXCLUDED.updated_at
                """)){int i=1;p.setString(i++,required(r,"wallet_credential_id"));p.setInt(i++,integer(r,"user_id"));
            p.setString(i++,text(r,"credential_hash"));p.setString(i++,text(r,"serial_number"));p.setString(i++,text(r,"status"));
            p.setTimestamp(i++,timestamp(r,"issued_at"));nullableInt(p,i++,r,"issued_by_user_id");nullableTimestamp(p,i++,r,"revoked_at");
            nullableInt(p,i++,r,"revoked_by_user_id");nullableTimestamp(p,i++,r,"last_used_at");p.setTimestamp(i,timestamp(r,"updated_at"));p.executeUpdate();}
    }

    private static void upsertPayrollSetting(Connection c,JsonObject r)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO employee_payroll_settings(setting_id,user_id,period_type,work_hour_limit,effective_from,
                  compensation_type,pay_rate,created_by_user_id,created_by_name,created_at,updated_at)
                VALUES(?,?,?,?,?,?::compensation_type_enum,?,?,?,?,?)
                ON CONFLICT(setting_id) DO UPDATE SET period_type=EXCLUDED.period_type,
                  work_hour_limit=EXCLUDED.work_hour_limit,effective_from=EXCLUDED.effective_from,
                  compensation_type=EXCLUDED.compensation_type,pay_rate=EXCLUDED.pay_rate,
                  created_by_user_id=EXCLUDED.created_by_user_id,created_by_name=EXCLUDED.created_by_name,
                  updated_at=EXCLUDED.updated_at WHERE employee_payroll_settings.updated_at<EXCLUDED.updated_at
                """)){int i=1;p.setObject(i++,uuid(r,"setting_id"));p.setInt(i++,integer(r,"user_id"));
            p.setString(i++,text(r,"period_type"));p.setBigDecimal(i++,r.get("work_hour_limit").getAsBigDecimal());
            p.setDate(i++,date(r,"effective_from"));p.setString(i++,text(r,"compensation_type"));
            p.setBigDecimal(i++,r.get("pay_rate").getAsBigDecimal());nullableInt(p,i++,r,"created_by_user_id");
            nullableText(p,i++,r,"created_by_name");p.setTimestamp(i++,timestamp(r,"created_at"));
            p.setTimestamp(i,timestamp(r,"updated_at"));p.executeUpdate();}
    }

    private static void upsertLocation(Connection c,JsonObject r)throws SQLException{
        try(PreparedStatement ps=c.prepareStatement("""
                INSERT INTO locations(location_id,name,address,company_address_line1,company_address_line2,
                  company_address_line3,company_phone_line1,company_phone_line2,company_email_line1,
                  company_email_line2,receipt_store_code,timezone,created_at,balance_sheet_recipient_email,
                  updated_at,email_sender_address,email_sender_name,email_bcc_address,email_receipts_enabled,
                  email_order_confirmations_enabled,email_quotes_enabled,email_invoices_enabled,
                  email_delivery_bills_enabled,email_connected_at,email_last_tested_at,
                  wallet_relevance_latitude,wallet_relevance_longitude)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
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
                  email_connected_at=EXCLUDED.email_connected_at,email_last_tested_at=EXCLUDED.email_last_tested_at,
                  wallet_relevance_latitude=EXCLUDED.wallet_relevance_latitude,
                  wallet_relevance_longitude=EXCLUDED.wallet_relevance_longitude
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
            nullableTimestamp(ps,i++,r,"email_last_tested_at");nullableDouble(ps,i++,r,"wallet_relevance_latitude");
            nullableDouble(ps,i,r,"wallet_relevance_longitude");ps.executeUpdate();}
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

    private static void insertTimeClockAdjustment(Connection c,JsonObject r)throws SQLException{
        UUID clockUuid=uuid(r,"clock_uuid");
        Long clockId=null;
        try(PreparedStatement p=c.prepareStatement("SELECT clock_id FROM employee_time_clock WHERE clock_uuid=?")){
            p.setObject(1,clockUuid);try(ResultSet rs=p.executeQuery()){if(rs.next())clockId=rs.getLong(1);}}
        if(clockId==null)throw new SQLException("Time-clock adjustment parent is unavailable locally.");
        JsonObject local=r.deepCopy();local.remove("clock_uuid");local.addProperty("clock_id",clockId);
        try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO employee_time_clock_adjustments
                SELECT (jsonb_populate_record(NULL::employee_time_clock_adjustments,?::jsonb)).*
                ON CONFLICT(adjustment_id) DO NOTHING
                """)){p.setString(1,local.toString());p.executeUpdate();}
    }

    private static void insertPayrollPayment(Connection c,JsonObject r)throws SQLException{
        String cols="sync_uuid,user_id,employee_name,employee_role,location_id,pay_period_start,pay_period_end,payment_number,pay_date,days_worked,total_hours,pay_period_type,work_hour_limit,regular_hours,overtime_hours,regular_pay,overtime_pay,total_pay,record_count,compensation_type,location_name,payment_method,payment_reference,paid_at,paid_by_user_id,paid_by_name,created_at";
        // Older databases may already contain the same paycheck with a locally
        // generated UUID.  The employee/period/payment number tuple is the
        // durable business identity, so either unique key makes the replay
        // idempotent instead of leaving the inbox permanently failed.
        String sql="WITH incoming AS (SELECT (jsonb_populate_record(NULL::payroll_payments,?::jsonb)).*) "
                +"INSERT INTO payroll_payments("+cols+") SELECT "+cols+" FROM incoming "
                +"ON CONFLICT(user_id,pay_period_start,pay_period_end,payment_number) DO NOTHING";
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,r.toString());ps.executeUpdate();}
    }

    private static void delete(Connection c,String table,JsonObject key,Instant deleted)throws SQLException{
        String sql=switch(table){
            case "user_locations"->"DELETE FROM user_locations WHERE user_id=? AND location_id=? AND updated_at<=?";
            case "employee_schedule_holidays"->"DELETE FROM employee_schedule_holidays WHERE holiday_date=? AND updated_at<=?";
            case "employee_schedule_assignments"->"DELETE FROM employee_schedule_assignments WHERE location_id=? AND user_id=? AND work_date=? AND updated_at<=?";
            default->throw new SQLException("Shared reference deletion targets an unapproved table.");};
        try(PreparedStatement ps=c.prepareStatement(sql)){if("user_locations".equals(table)){
            ps.setInt(1,integer(key,"user_id"));ps.setInt(2,integer(key,"location_id"));ps.setTimestamp(3,Timestamp.from(deleted));
        }else if("employee_schedule_holidays".equals(table)){
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
            case "users"->key.addProperty("user_id",integer(row,"user_id"));
            case "user_locations"->{key.addProperty("user_id",integer(row,"user_id"));key.addProperty("location_id",integer(row,"location_id"));}
            case "employee_wallet_credentials"->key.addProperty("wallet_credential_id",required(row,"wallet_credential_id"));
            case "employee_payroll_settings"->key.addProperty("setting_id",required(row,"setting_id"));
            case "employee_schedule_shifts"->key.addProperty("shift_id",required(row,"shift_id"));
            case "employee_schedule_holidays"->key.addProperty("holiday_date",required(row,"holiday_date"));
            case "employee_schedule_assignments"->{key.addProperty("location_id",integer(row,"location_id"));key.addProperty("user_id",integer(row,"user_id"));key.addProperty("work_date",required(row,"work_date"));}
            case "employee_time_clock"->key.addProperty("clock_uuid",required(row,"clock_uuid"));
            case "employee_time_clock_adjustments"->key.addProperty("adjustment_id",required(row,"adjustment_id"));
            case "employee_payroll_bonuses","payroll_payments"->key.addProperty("sync_uuid",required(row,"sync_uuid"));
            default->throw new SQLException("Shared reference event targets an unapproved table.");}
        return key;
    }

    private static String deleteKey(String table,JsonObject key)throws SQLException{return switch(table){
        case "user_locations"->integer(key,"user_id")+":"+integer(key,"location_id");
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
    private static void nullableDouble(PreparedStatement p,int i,JsonObject r,String k)throws SQLException{if(!r.has(k)||r.get(k).isJsonNull())p.setNull(i,java.sql.Types.DOUBLE);else p.setDouble(i,r.get(k).getAsDouble());}
    private static void nullableUuid(PreparedStatement p,int i,JsonObject r,String k)throws SQLException{if(!r.has(k)||r.get(k).isJsonNull())p.setNull(i,java.sql.Types.OTHER);else p.setObject(i,UUID.fromString(r.get(k).getAsString()));}
    private static void nullableTimestamp(PreparedStatement p,int i,JsonObject r,String k)throws SQLException{if(!r.has(k)||r.get(k).isJsonNull())p.setNull(i,java.sql.Types.TIMESTAMP_WITH_TIMEZONE);else p.setTimestamp(i,timestamp(r,k));}
    private static void nullableDate(PreparedStatement p,int i,JsonObject r,String k)throws SQLException{if(!r.has(k)||r.get(k).isJsonNull())p.setNull(i,java.sql.Types.DATE);else p.setDate(i,date(r,k));}
    private static void nullableTime(PreparedStatement p,int i,JsonObject r,String k)throws SQLException{if(!r.has(k)||r.get(k).isJsonNull())p.setNull(i,java.sql.Types.TIME);else p.setTime(i,time(r,k));}
    private static String safeError(Exception e){String v=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();return v.substring(0,Math.min(v.length(),1000));}
    private record TableSnapshot(String name,String orderSql,String keySql,String timestampSql,String rowSql) {
        private TableSnapshot(String name,String orderSql,String keySql,String timestampSql){
            this(name,orderSql,keySql,timestampSql,"to_jsonb(t)");
        }
    }
    private record InboxEvent(long sequence,String payload) { }
}
