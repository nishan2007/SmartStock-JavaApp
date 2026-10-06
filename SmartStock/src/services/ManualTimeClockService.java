package services;

import java.sql.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Completed, manager-entered shifts; all identity and pay decisions stay on the server. */
public final class ManualTimeClockService {
    private ManualTimeClockService() { }
    public record Employee(int userId, String name) {
        @Override public String toString() { return name; }
    }
    private static final String ELIGIBLE = """
        COALESCE(u.is_active, TRUE)
        AND (EXISTS (SELECT 1 FROM user_locations ul WHERE ul.user_id=u.user_id AND ul.location_id=?)
             OR NOT EXISTS (SELECT 1 FROM user_locations ul WHERE ul.user_id=u.user_id))
        AND NOT EXISTS (SELECT 1 FROM employee_registrations er WHERE er.employee_id=u.user_id AND er.status<>'APPROVED')
        """;

    public static List<Employee> employees(Connection c, int locationId) throws SQLException {
        List<Employee> result = new ArrayList<>();
        try (PreparedStatement p=c.prepareStatement("SELECT u.user_id,COALESCE(NULLIF(u.full_name,''),u.username) FROM users u WHERE "+ELIGIBLE+" ORDER BY 2,u.user_id")) {
            p.setInt(1,locationId);
            try(ResultSet r=p.executeQuery()) { while(r.next()) result.add(new Employee(r.getInt(1),r.getString(2))); }
        }
        return result;
    }

    /** Shared employee lock serializes manual entries with punches and corrections. */
    public static void lockEmployee(Connection c, int userId) throws SQLException {
        try(PreparedStatement p=c.prepareStatement("SELECT user_id FROM users WHERE user_id=? FOR UPDATE")) {
            p.setInt(1,userId);
            try(ResultSet r=p.executeQuery()) { if(!r.next()) throw new SQLException("Employee was not found."); }
        }
    }

    public static void validate(TimeClockAutoCloseService.Correction v) throws SQLException {
        TimeClockAutoCloseService.validateCorrection(v);
        if(!v.clockOut().isAfter(v.clockIn())) throw new SQLException("Clock-out must be after clock-in.");
        if(v.reason().trim().length()>2000) throw new SQLException("Reason must be 2,000 characters or fewer.");
        if(v.lunchStart()!=null && v.breakStart()!=null
                && v.lunchStart().isBefore(v.breakEnd()) && v.breakStart().isBefore(v.lunchEnd()))
            throw new SQLException("Lunch and break cannot overlap.");
    }

    static Instant timestamp(LocalDateTime value, ZoneId zone) throws SQLException {
        if(value==null) return null;
        var offsets=zone.getRules().getValidOffsets(value);
        if(offsets.size()!=1) throw new SQLException("An entered time is ambiguous or does not exist in the store timezone.");
        return value.toInstant(offsets.get(0));
    }

    public static long create(Connection c, int userId, int locationId,
                              TimeClockAutoCloseService.Correction v) throws SQLException {
        validate(v);
        lockEmployee(c,userId);
        String name;
        try(PreparedStatement p=c.prepareStatement("SELECT COALESCE(NULLIF(u.full_name,''),u.username) FROM users u WHERE u.user_id=? AND "+ELIGIBLE)) {
            p.setInt(1,userId); p.setInt(2,locationId);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new SQLException("Select an active employee eligible to work at this store.");
                name=r.getString(1);
            }
        }
        String store; ZoneId zone;
        try(PreparedStatement p=c.prepareStatement("SELECT name,timezone FROM locations WHERE location_id=?")) {
            p.setInt(1,locationId);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new SQLException("Store was not found.");
                store=r.getString(1);
                try { zone=ZoneId.of(r.getString(2)); }
                catch(Exception e) { throw new SQLException("Configure a valid store timezone before entering hours."); }
            }
        }
        Instant in=timestamp(v.clockIn(),zone), out=timestamp(v.clockOut(),zone);
        Instant ls=timestamp(v.lunchStart(),zone), le=timestamp(v.lunchEnd(),zone);
        Instant bs=timestamp(v.breakStart(),zone), be=timestamp(v.breakEnd(),zone);
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM employee_time_clock WHERE user_id=? AND clock_in < ? AND (clock_out IS NULL OR clock_out > ?) LIMIT 1")) {
            p.setInt(1,userId); p.setTimestamp(2,Timestamp.from(out)); p.setTimestamp(3,Timestamp.from(in));
            try(ResultSet r=p.executeQuery()) { if(r.next()) throw new SQLException("These hours overlap an existing employee session. Correct that session instead."); }
        }
        LocalDate date=v.clockIn().toLocalDate();
        String type; BigDecimal rate;
        try(PreparedStatement p=c.prepareStatement("""
            SELECT COALESCE(pay.compensation_type::text,u.compensation_type::text,'HOURLY'),COALESCE(pay.pay_rate,u.salary,0)
            FROM users u LEFT JOIN LATERAL (
                SELECT compensation_type,pay_rate FROM employee_payroll_settings
                WHERE user_id=u.user_id AND effective_from<=? ORDER BY effective_from DESC,updated_at DESC LIMIT 1
            ) pay ON TRUE WHERE u.user_id=?
            """)) {
            p.setDate(1,Date.valueOf(date)); p.setInt(2,userId);
            try(ResultSet r=p.executeQuery()) { r.next(); type=r.getString(1); rate=r.getBigDecimal(2); }
        }
        BigDecimal hours=TimeClockAutoCloseService.workedHours(in,ls,le,bs,be,out);
        BigDecimal earned=TimeClockAutoCloseService.earned(c,userId,date,type,rate,hours,-1);
        try(PreparedStatement p=c.prepareStatement("""
            INSERT INTO employee_time_clock(user_id,user_name,location_id,location_name,work_date,
                clock_in,lunch_start,lunch_end,break_start,break_end,clock_out,total_hours_worked,total_earned)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?) RETURNING clock_id
            """)) {
            p.setInt(1,userId); p.setString(2,name); p.setInt(3,locationId); p.setString(4,store); p.setDate(5,Date.valueOf(date));
            Instant[] times={in,ls,le,bs,be,out};
            for(int i=0;i<times.length;i++) p.setTimestamp(6+i,times[i]==null?null:Timestamp.from(times[i]));
            p.setBigDecimal(12,hours); p.setBigDecimal(13,earned);
            try(ResultSet r=p.executeQuery()) { r.next(); return r.getLong(1); }
        }
    }
}
