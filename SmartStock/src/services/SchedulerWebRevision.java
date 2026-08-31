package services;

import java.sql.*;
import java.time.LocalDate;

/** Optimistic browser revision, including desktop changes and row replacement (xmin). */
final class SchedulerWebRevision {
    private SchedulerWebRevision() { }

    static String read(Connection c,int location,LocalDate start,LocalDate end)throws SQLException {
        StringBuilder result=new StringBuilder();
        for(String table:new String[]{"employee_schedule_assignments","employee_schedule_holidays","employee_schedule_shifts"}) {
            boolean holiday=table.endsWith("holidays"),shift=table.endsWith("shifts");
            String filter=holiday?"holiday_date BETWEEN ? AND ?":shift?"location_id=?":"location_id=? AND work_date BETWEEN ? AND ?";
            String sql="SELECT md5(COALESCE(string_agg(v,E'\\n' ORDER BY v),'')) FROM (SELECT row_to_json(t)::text||':'||t.xmin::text v FROM "+table+" t WHERE "+filter+") q";
            try(PreparedStatement p=c.prepareStatement(sql)) {
                int i=1;if(!holiday)p.setInt(i++,location);
                if(!shift){p.setDate(i++,Date.valueOf(start));p.setDate(i,Date.valueOf(end));}
                try(ResultSet r=p.executeQuery()){r.next();result.append(r.getString(1)).append(':');}
            }
        }
        return LanSecurity.sha256(location+":"+start+":"+end+":"+result);
    }

    static void lockForWrite(Connection c)throws SQLException {
        if(c.getAutoCommit())throw new SQLException("Revision checking requires a transaction");
        try(Statement s=c.createStatement()) {
            s.execute("SET LOCAL lock_timeout='3s'");
            s.execute("SET LOCAL statement_timeout='10s'");
            // Desktop writers do not share the browser's advisory locks. PostgreSQL
            // table locks protect the compare-and-write against those writers too.
            s.execute("LOCK TABLE employee_schedule_assignments,employee_schedule_holidays,employee_schedule_shifts IN SHARE ROW EXCLUSIVE MODE");
        }
    }
}
