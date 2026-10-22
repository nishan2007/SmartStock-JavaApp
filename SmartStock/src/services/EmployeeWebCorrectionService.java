package services;

import com.google.gson.JsonObject;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.Set;

/** Browser boundary for the existing audited time-clock correction workflow. */
final class EmployeeWebCorrectionService {
    private EmployeeWebCorrectionService() { }

    static boolean canEdit(Set<String> permissions) {
        return permissions.contains("ACCESS_EMPLOYEE_WEB") && permissions.contains("TIME_CLOCK_MANAGEMENT");
    }

    static String revision(long clockId, Timestamp... timestamps) {
        StringBuilder value=new StringBuilder(Long.toString(clockId));
        for(Timestamp stamp:timestamps) value.append('|').append(stamp==null?"":stamp.toInstant());
        return LanSecurity.sha256(value.toString());
    }

    static TimeClockAutoCloseService.Correction correction(JsonObject body)throws EmployeeWebServer.WebError {
        try {
            String reason=text(body,"reason");
            if(reason==null||reason.isBlank()) reason="N/A";
            if(reason.length()>2000) throw new IllegalArgumentException();
            var result=new TimeClockAutoCloseService.Correction(time(body,"clockIn"),time(body,"lunchStart"),
                    time(body,"lunchEnd"),time(body,"breakStart"),time(body,"breakEnd"),time(body,"clockOut"),reason.trim());
            try {TimeClockAutoCloseService.validateCorrection(result);}
            catch(SQLException e){throw new EmployeeWebServer.WebError(400,"INVALID_CORRECTION",e.getMessage());}
            return result;
        } catch(java.time.DateTimeException | IllegalArgumentException e) {
            throw new EmployeeWebServer.WebError(400,"INVALID_CORRECTION","Enter valid store-local dates and times and a reason of at most 2000 characters.");
        }
    }

    static void correct(Connection c,int actor,String actorName,Set<String> permissions,JsonObject body)throws Exception {
        if(!canEdit(permissions)) throw new SecurityException("Time Clock Management permission is required to correct sessions.");
        if(!body.has("clockId")||body.get("clockId").isJsonNull())
            throw new EmployeeWebServer.WebError(400,"INVALID_CORRECTION","Select a time-clock session.");
        long clockId=body.get("clockId").getAsLong();
        int employee;
        try(PreparedStatement p=c.prepareStatement("SELECT user_id FROM employee_time_clock WHERE clock_id=?")) {
            p.setLong(1,clockId);try(ResultSet r=p.executeQuery()) {
                if(!r.next())throw new EmployeeWebServer.WebError(404,"SESSION_NOT_FOUND","This time-clock session no longer exists.");
                employee=r.getInt(1);
            }
        }
        EmployeeWebHistoryService.authorize(actor,employee,permissions);
        // Match the shared correction lock order: employee, then session.
        ManualTimeClockService.lockEmployee(c,employee);
        try(PreparedStatement p=c.prepareStatement("SELECT clock_in,lunch_start,lunch_end,break_start,break_end,clock_out,updated_at,user_id FROM employee_time_clock WHERE clock_id=? FOR UPDATE")) {
            p.setLong(1,clockId);try(ResultSet r=p.executeQuery()) {
                if(!r.next())throw new EmployeeWebServer.WebError(404,"SESSION_NOT_FOUND","This time-clock session no longer exists.");
                if(r.getInt(8)!=employee)throw new EmployeeWebServer.WebError(409,"SESSION_CHANGED","This session changed. Refresh the history before editing again.");
                Timestamp[] stamps=new Timestamp[7];for(int i=0;i<stamps.length;i++)stamps[i]=r.getTimestamp(i+1);
                if(!revision(clockId,stamps).equals(text(body,"revision")))
                    throw new EmployeeWebServer.WebError(409,"SESSION_CHANGED","This session changed. Refresh the history before editing again.");
            }
        }
        if(!body.has("correction")||!body.get("correction").isJsonObject())
            throw new EmployeeWebServer.WebError(400,"INVALID_CORRECTION","Correction details are required.");
        TimeClockAutoCloseService.correctSession(c,clockId,correction(body.getAsJsonObject("correction")),actor,actorName);
    }

    private static String text(JsonObject body,String key) {
        return body.has(key)&&!body.get(key).isJsonNull()?body.get(key).getAsString():null;
    }
    private static LocalDateTime time(JsonObject body,String key) {
        String value=text(body,key);return value==null||value.isBlank()?null:LocalDateTime.parse(value);
    }
}
