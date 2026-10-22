package services;

import managers.ServerTimeClockManager;
import managers.ServerTimeClockManager.TimeClockRow;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Read-only projections. Authorization is checked here as well as at the gateway boundary. */
final class EmployeeWebHistoryService {
    private EmployeeWebHistoryService() { }

    static boolean canBrowse(Set<String> permissions) {
        return permissions.contains("ACCESS_EMPLOYEE_WEB") && permissions.contains("VIEW_ALL_EMPLOYEE_WEB");
    }

    static void authorize(int actor, int target, Set<String> permissions) {
        if (!permissions.contains("ACCESS_EMPLOYEE_WEB") || (actor != target && !canBrowse(permissions)))
            throw new SecurityException("You cannot view this employee's history.");
    }

    static Map<String,Object> employees(Connection c, int actor, Set<String> permissions,
                                        String search, boolean includeInactive, int offset) throws SQLException {
        if (!canBrowse(permissions)) throw new SecurityException("Employee directory access is required.");
        List<Map<String,Object>> rows = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("""
                SELECT user_id,username,COALESCE(NULLIF(full_name,''),username),is_active
                FROM users WHERE (? OR is_active=TRUE)
                AND (strpos(lower(COALESCE(full_name,'')),lower(?))>0 OR strpos(lower(username),lower(?))>0)
                ORDER BY lower(COALESCE(NULLIF(full_name,''),username)),user_id LIMIT 51 OFFSET ?
                """)) {
            p.setBoolean(1,includeInactive); p.setString(2,search); p.setString(3,search); p.setInt(4,offset);
            try (ResultSet r=p.executeQuery()) { while(r.next()) rows.add(employee(r)); }
        }
        boolean more=rows.size()>50;
        return Map.of("employees",more?rows.subList(0,50):rows,"hasMore",more,"offset",offset);
    }

    static Map<String,Object> history(Connection c,int actor,int target,Set<String> permissions,
                                      LocalDate start,LocalDate end,int offset) throws SQLException {
        authorize(actor,target,permissions);
        Map<String,Object> employee;
        String zone;
        try (PreparedStatement p=c.prepareStatement("""
                SELECT u.user_id,u.username,COALESCE(NULLIF(u.full_name,''),u.username),u.is_active,
                COALESCE((SELECT NULLIF(l.timezone,'') FROM user_locations ul JOIN locations l USING(location_id)
                WHERE ul.user_id=u.user_id ORDER BY l.location_id LIMIT 1),'America/Guyana')
                FROM users u WHERE u.user_id=?
                """)) {
            p.setInt(1,target);
            try(ResultSet r=p.executeQuery()) {
                if(!r.next()) throw new IllegalArgumentException("Employee not found.");
                employee=employee(r); zone=r.getString(5);
            }
        }
        LocalDate today=LocalDate.now(ZoneId.of(zone));
        var rate=EmployeePayrollSettingsService.payRateFor(c,target,today);
        var period=EmployeePayrollSettingsService.periodFor(c,target,today,rate.compensationType());
        if(start==null && end==null) { start=period.start(); end=period.end(); }
        if(start==null || end==null || end.isBefore(start) || java.time.temporal.ChronoUnit.DAYS.between(start,end)>366)
            throw new IllegalArgumentException("Choose a date range of at most 367 days.");

        // Load complete payroll context before date filtering: overtime and daily allocations depend on other sessions.
        // Legacy sessions without a store timezone follow the existing correction service's fallback.
        List<TimeClockRow> calculations=ServerTimeClockManager.loadEmployeeHistory(c,target,"America/New_York");
        Map<Integer,Totals> totals=aggregate(calculations);
        List<Map<String,Object>> sessions=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("""
                SELECT tc.clock_id,tc.work_date,tc.location_id,COALESCE(tc.location_name,l.name,''),
                  COALESCE(NULLIF(l.timezone,''),?),
                  tc.clock_in,tc.lunch_start,tc.lunch_end,tc.break_start,tc.break_end,tc.clock_out,
                  COALESCE(tc.auto_clock_out,FALSE),tc.auto_clock_out_review_status,
                  COALESCE(tc.auto_break_end,FALSE),tc.auto_break_end_review_status,tc.updated_at
                FROM employee_time_clock tc LEFT JOIN locations l ON l.location_id=tc.location_id
                WHERE tc.user_id=? AND tc.work_date BETWEEN ? AND ?
                ORDER BY tc.clock_in DESC NULLS LAST,tc.clock_id DESC LIMIT 51 OFFSET ?
                """)) {
            p.setString(1,"America/New_York"); p.setInt(2,target); p.setDate(3,java.sql.Date.valueOf(start));
            p.setDate(4,java.sql.Date.valueOf(end)); p.setInt(5,offset);
            try(ResultSet r=p.executeQuery()) { while(r.next()) {
                Map<String,Object> row=new LinkedHashMap<>(); int id=r.getInt(1);
                row.put("clockId",id); row.put("workDate",r.getDate(2).toString());
                row.put("locationId",r.getObject(3)); row.put("store",r.getString(4));
                String sessionZone=r.getString(5); row.put("timezone",sessionZone);
                String[] keys={"clockIn","lunchStart","lunchEnd","breakStart","breakEnd","clockOut"};
                Timestamp[] stamps=new Timestamp[7];
                for(int i=0;i<keys.length;i++) {
                    Timestamp stamp=r.getTimestamp(6+i);
                    stamps[i]=stamp;
                    row.put(keys[i],stamp==null?null:stamp.toInstant().atZone(ZoneId.of(sessionZone)).toOffsetDateTime().toString());
                }
                stamps[6]=r.getTimestamp(16);
                row.put("revision",EmployeeWebCorrectionService.revision(id,stamps));
                Totals total=totals.get(id);
                row.put("hours",total==null?null:total.hours());
                row.put("calculatedPay",total==null||total.salary()?null:total.pay());
                row.put("payNote",total!=null&&total.salary()?"Salary is calculated per pay period, not per session.":null);
                row.put("provisional",r.getTimestamp(11)==null);
                row.put("autoClockOut",r.getBoolean(12)); row.put("clockOutReview",r.getString(13));
                row.put("autoBreakEnd",r.getBoolean(14)); row.put("breakEndReview",r.getString(15));
                sessions.add(row);
            }}
        }
        boolean more=sessions.size()>50;
        return Map.of("employee",employee,"start",start.toString(),"end",end.toString(),
                "sessions",more?sessions.subList(0,50):sessions,"hasMore",more,"offset",offset);
    }

    static Map<Integer,Totals> aggregate(List<TimeClockRow> rows) {
        Map<Integer,Totals> result=new HashMap<>();
        for(TimeClockRow row:rows) result.merge(row.clockId(),new Totals(row.dailyHours(),row.totalPay(),
                "SALARY".equalsIgnoreCase(row.compensationType())),(a,b)->new Totals(a.hours.add(b.hours),a.pay.add(b.pay),a.salary||b.salary));
        return result;
    }

    private static Map<String,Object> employee(ResultSet r)throws SQLException {
        return Map.of("userId",r.getInt(1),"username",r.getString(2),"name",r.getString(3),"active",r.getBoolean(4));
    }
    record Totals(BigDecimal hours,BigDecimal pay,boolean salary) { }
}
