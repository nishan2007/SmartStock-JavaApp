package services;

import data.DatabaseConfig;
import managers.ServerTimeClockManager;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PayrollPeriodRateIntegrationTest {
    private Connection open() throws Exception {
        String profile = System.getProperty("smartstock.payroll.test.profile", "");
        assumeTrue(!profile.isBlank());
        if (!profile.equals("development")) throw new IllegalArgumentException("Use the development payroll database.");
        String previous = System.getProperty("smartstock.environment");
        DatabaseConfig config;
        try {
            System.setProperty("smartstock.environment", profile);
            config = DatabaseConfig.load();
        } finally {
            if (previous == null) System.clearProperty("smartstock.environment");
            else System.setProperty("smartstock.environment", previous);
        }
        Connection c = DriverManager.getConnection(config.jdbcUrl(), config.dbUser(), config.dbPassword());
        c.setAutoCommit(false);
        return c;
    }

    private int employee(Connection c) throws SQLException {
        try (var ps = c.prepareStatement("INSERT INTO users(username,full_name,compensation_type,salary) VALUES (?,'Payroll test','HOURLY',385) RETURNING user_id")) {
            ps.setString(1, "period-rate-test-" + UUID.randomUUID());
            try (var rs = ps.executeQuery()) { rs.next(); return rs.getInt(1); }
        }
    }

    private void rate(Connection c, int user, String date, int amount, String updated) throws SQLException {
        try (var ps = c.prepareStatement("""
                INSERT INTO employee_payroll_settings(setting_id,user_id,period_type,work_hour_limit,
                    effective_from,compensation_type,pay_rate,updated_at)
                VALUES (?,?,'SEMI_MONTHLY',120,?,'HOURLY',?,?::timestamptz)
                """)) {
            ps.setObject(1, UUID.randomUUID()); ps.setInt(2, user); ps.setDate(3, Date.valueOf(date));
            ps.setInt(4, amount); ps.setString(5, updated); ps.executeUpdate();
        }
    }

    private void shift(Connection c, int user, String date, int earnings) throws SQLException {
        try (var ps = c.prepareStatement("""
                INSERT INTO employee_time_clock(user_id,work_date,clock_in,clock_out,total_hours_worked,total_earned)
                VALUES (?,?,?::timestamptz,?::timestamptz,8,?)
                """)) {
            ps.setInt(1, user); ps.setDate(2, Date.valueOf(date));
            ps.setString(3, date + " 08:00:00-04"); ps.setString(4, date + " 16:00:00-04");
            ps.setInt(5, earnings); ps.executeUpdate();
        }
    }

    @Test void mostRecentPeriodEditAppliesToEarlyAndLateSessionsWithoutChangingOtherPeriods() throws Exception {
        try (Connection c = open()) {
            int user = employee(c);
            rate(c, user, "2026-08-16", 300, "2026-08-16T12:00:00Z");
            rate(c, user, "2026-09-01", 380, "2026-09-01T12:00:00Z");
            rate(c, user, "2026-09-08", 385, "2026-09-08T12:00:00Z");
            rate(c, user, "2026-09-16", 400, "2026-09-16T12:00:00Z");
            assertEquals(385, EmployeePayrollSettingsService.payRateFor(c, user, LocalDate.of(2026,9,1)).rate().intValue());
            assertEquals(385, EmployeePayrollSettingsService.payRateFor(c, user, LocalDate.of(2026,9,15)).rate().intValue());
            assertEquals(300, EmployeePayrollSettingsService.payRateFor(c, user, LocalDate.of(2026,8,31)).rate().intValue());
            assertEquals(400, EmployeePayrollSettingsService.payRateFor(c, user, LocalDate.of(2026,9,16)).rate().intValue());
            // Updating an earlier snapshot later is still the most recent period edit.
            try (var ps = c.prepareStatement("UPDATE employee_payroll_settings SET pay_rate=390,updated_at='2026-09-12T12:00:00Z' WHERE user_id=? AND effective_from='2026-09-01'")) {
                ps.setInt(1,user); ps.executeUpdate();
            }
            assertEquals(390, EmployeePayrollSettingsService.payRateFor(c,user,LocalDate.of(2026,9,10)).rate().intValue());
            shift(c,user,"2026-09-02",0);
            shift(c,user,"2026-09-10",3080);
            ServerTimeClockManager.bindRequest(user,1,"Test","America/Guyana","Test");
            try {
                var dashboard=ServerTimeClockManager.loadDashboard(c,true);
                var rows=dashboard.rows().stream().filter(row -> row.userId()==user).toList();
                assertEquals(2,rows.size());
                for(var row:rows) { assertEquals(390,row.salary().intValue()); assertEquals(3120,row.totalPay().intValue()); }
            } finally { ServerTimeClockManager.clearRequest(); c.rollback(); }
        }
    }

    @Test void rateSaveRebuildsAllCompletedSessionsAndRepairsStaleEarnings() throws Exception {
        try(Connection c=open()) {
            int user=employee(c);
            rate(c,user,"2026-09-01",380,"2026-09-01T12:00:00Z");
            rate(c,user,"2026-09-16",385,"2026-09-16T12:00:00Z");
            rate(c,user,"2026-09-23",385,"2026-09-23T12:00:00Z");
            shift(c,user,"2026-09-02",3040);
            shift(c,user,"2026-09-17",3040);
            shift(c,user,"2026-09-24",3080);
            assertTrue(EmployeePayrollSettingsService.currentPeriodRateNeedsRepair(c,user,LocalDate.of(2026,9,30),"HOURLY","HOURLY",new BigDecimal("385")));
            EmployeePayrollSettingsService.saveCurrentPeriodPayRate(c,user,LocalDate.of(2026,9,30),"HOURLY","HOURLY",new BigDecimal("390"));
            assertFalse(EmployeePayrollSettingsService.currentPeriodRateNeedsRepair(c,user,LocalDate.of(2026,9,30),"HOURLY","HOURLY",new BigDecimal("390")));
            try(var ps=c.prepareStatement("SELECT work_date,total_earned,total_hours_worked FROM employee_time_clock WHERE user_id=? ORDER BY work_date")) {
                ps.setInt(1,user);try(var rs=ps.executeQuery()) {
                    rs.next();assertEquals(3040,rs.getBigDecimal(2).intValue());
                    while(rs.next()){ assertEquals(3120,rs.getBigDecimal(2).intValue()); assertEquals(8,rs.getBigDecimal(3).intValue()); }
                }
            }
            c.rollback();
        }
    }

    @Test void weeklyAndFourBlockRatesStayInsideTheirOwnPeriod() throws Exception {
        try(Connection c=open()) {
            int user=employee(c);
            rate(c,user,"2026-09-01",100,"2026-09-01T12:00:00Z");
            rate(c,user,"2026-09-03",200,"2026-09-03T12:00:00Z");
            rate(c,user,"2026-09-08",300,"2026-09-08T12:00:00Z");
            try(var ps=c.prepareStatement("UPDATE employee_payroll_settings SET period_type=? WHERE user_id=?")) {
                ps.setString(1,"WEEKLY");ps.setInt(2,user);ps.executeUpdate();
                assertEquals(200,EmployeePayrollSettingsService.payRateFor(c,user,LocalDate.of(2026,9,1)).rate().intValue());
                assertEquals(300,EmployeePayrollSettingsService.payRateFor(c,user,LocalDate.of(2026,9,7)).rate().intValue());
                ps.setString(1,"FOUR_MONTH_BLOCKS");ps.executeUpdate();
                assertEquals(200,EmployeePayrollSettingsService.payRateFor(c,user,LocalDate.of(2026,9,7)).rate().intValue());
                assertEquals(300,EmployeePayrollSettingsService.payRateFor(c,user,LocalDate.of(2026,9,8)).rate().intValue());
            }
            c.rollback();
        }
    }

    @Test void settledHistoricalSummaryRetainsPaymentSnapshotWhilePartialPaymentStaysLive() throws Exception {
        try(Connection c=open()) {
            int user=employee(c);
            rate(c,user,"2026-08-01",100,"2026-08-01T12:00:00Z");
            shift(c,user,"2026-08-02",800);
            try(var ps=c.prepareStatement("""
                    INSERT INTO payroll_payments(user_id,employee_name,pay_period_start,pay_period_end,pay_date,
                        total_hours,regular_hours,overtime_hours,regular_pay,overtime_pay,total_pay,
                        days_worked,record_count,compensation_type,pay_period_type,work_hour_limit,payment_method)
                    VALUES (?,'Test','2026-08-01','2026-08-15','2026-08-17',10,10,0,1000,0,1000,1,1,'HOURLY','SEMI_MONTHLY',120,'CASH')
                    """)) { ps.setInt(1,user); ps.executeUpdate(); }
            ServerTimeClockManager.bindRequest(user,1,"Test","America/Guyana","Test");
            try {
                var summary=ServerTimeClockManager.loadPayrollDashboard(c).summaries().stream().filter(s -> s.userId()==user).findFirst().orElseThrow();
                assertEquals(1000,summary.totalPay().intValue()); assertEquals(10,summary.totalHours().intValue()); assertTrue(summary.paid());
                try(var ps=c.prepareStatement("UPDATE payroll_payments SET total_pay=400 WHERE user_id=?")) { ps.setInt(1,user);ps.executeUpdate(); }
                summary=ServerTimeClockManager.loadPayrollDashboard(c).summaries().stream().filter(s -> s.userId()==user).findFirst().orElseThrow();
                assertEquals(800,summary.totalPay().intValue()); assertEquals(400,summary.amountDue().intValue()); assertFalse(summary.paid());
            } finally { ServerTimeClockManager.clearRequest(); c.rollback(); }
        }
    }

    @Test void remoteClockEchoCannotOverwriteLocallyOwnedSessionButOwnerCanUpdateRemoteCopy() throws Exception {
        try(Connection c=open()) {
            int user=employee(c);
            rate(c,user,"2026-09-16",385,"2026-09-16T12:00:00Z");
            shift(c,user,"2026-09-17",3080);
            int location;
            try(var s=c.createStatement();var r=s.executeQuery("SELECT location_id FROM locations ORDER BY location_id LIMIT 1")) { assertTrue(r.next());location=r.getInt(1); }
            try(var p=c.prepareStatement("UPDATE employee_time_clock SET location_id=? WHERE user_id=?")) {p.setInt(1,location);p.setInt(2,user);p.executeUpdate();}
            com.google.gson.JsonObject row;
            try(var p=c.prepareStatement("SELECT to_jsonb(tc)::text FROM employee_time_clock tc WHERE user_id=?")) {
                p.setInt(1,user);try(var r=p.executeQuery()){r.next();row=com.google.gson.JsonParser.parseString(r.getString(1)).getAsJsonObject();}
            }
            row.addProperty("total_hours_worked",5);row.addProperty("total_earned",1925);
            row.addProperty("updated_at","2099-01-01T00:00:00Z");
            var payload=new com.google.gson.JsonObject();payload.addProperty("table_name","employee_time_clock");
            payload.addProperty("operation","UPSERT");payload.add("row_data",row);
            CrossStoreReferenceSyncService.applyPayload(c,payload,location,location+1000);
            try(var p=c.prepareStatement("SELECT total_hours_worked FROM employee_time_clock WHERE user_id=?")) {
                p.setInt(1,user);try(var r=p.executeQuery()){r.next();assertEquals(8,r.getBigDecimal(1).intValue());}
            }
            CrossStoreReferenceSyncService.applyPayload(c,payload,location+1000,location+2000);
            try(var p=c.prepareStatement("SELECT total_hours_worked FROM employee_time_clock WHERE user_id=?")) {
                p.setInt(1,user);try(var r=p.executeQuery()){r.next();assertEquals(8,r.getBigDecimal(1).intValue());}
            }
            CrossStoreReferenceSyncService.applyPayload(c,payload,location+1000,location);
            try(var p=c.prepareStatement("SELECT total_hours_worked FROM employee_time_clock WHERE user_id=?")) {
                p.setInt(1,user);try(var r=p.executeQuery()){r.next();assertEquals(5,r.getBigDecimal(1).intValue());}
            }
            c.rollback();
        }
    }

    @Test void payrollRateSyncMergesDifferentIdsForSameEmployeeAndDateAndRejectsOlderZero() throws Exception {
        try(Connection c=open()) {
            int user=employee(c);
            rate(c,user,"2026-09-16",0,"2026-09-12T12:00:00Z");
            com.google.gson.JsonObject row;
            String originalId;
            try(var p=c.prepareStatement("SELECT to_jsonb(eps)::text,setting_id::text FROM employee_payroll_settings eps WHERE user_id=?")) {
                p.setInt(1,user);try(var r=p.executeQuery()){r.next();row=com.google.gson.JsonParser.parseString(r.getString(1)).getAsJsonObject();originalId=r.getString(2);}
            }
            row.addProperty("setting_id",UUID.randomUUID().toString());
            row.addProperty("pay_rate",380);row.addProperty("updated_at","2026-09-30T12:00:00Z");
            var payload=new com.google.gson.JsonObject();payload.addProperty("table_name","employee_payroll_settings");
            payload.addProperty("operation","UPSERT");payload.add("row_data",row);
            CrossStoreReferenceSyncService.applyPayload(c,payload);
            row.addProperty("pay_rate",0);row.addProperty("updated_at","2026-09-13T12:00:00Z");
            CrossStoreReferenceSyncService.applyPayload(c,payload);
            try(var p=c.prepareStatement("SELECT setting_id::text,pay_rate FROM employee_payroll_settings WHERE user_id=? AND effective_from='2026-09-16'")) {
                p.setInt(1,user);try(var r=p.executeQuery()){assertTrue(r.next());assertEquals(originalId,r.getString(1));assertEquals(380,r.getBigDecimal(2).intValue());assertFalse(r.next());}
            }
            var display=LanEmployeeAdminService.state(c,user,LocalDate.of(2026,9,30)).employees().stream().filter(e -> e.userId()==user).findFirst().orElseThrow();
            assertEquals(380,display.salary().intValue());
            c.rollback();
        }
    }
    @Test void automaticClockOutLeavesOtherStoresOpenPunchesUntouched() throws Exception {
        try(Connection c=open()) {
            int user=employee(c);
            int location;
            try(var st=c.createStatement();var r=st.executeQuery("SELECT location_id FROM locations ORDER BY location_id LIMIT 1")) {
                assertTrue(r.next()); location=r.getInt(1);
            }
            try(var p=c.prepareStatement("""
                    INSERT INTO employee_time_clock(user_id,location_id,work_date,clock_in,
                      auto_close_enabled_snapshot,auto_close_detection_at,auto_close_rule_snapshot,auto_close_max_work_hours)
                    VALUES (?,?,'2026-09-17','2026-09-17T08:00:00-04',true,'2026-09-17T20:00:00-04','UNSCHEDULED',8)
                    """)) { p.setInt(1,user);p.setInt(2,location);p.executeUpdate(); }
            var due=TimeClockAutoCloseService.class.getDeclaredMethod("processDuePunches",Connection.class,java.time.Instant.class,int.class);
            due.setAccessible(true);
            due.invoke(null,c,java.time.Instant.now(),location+1000);
            try(var p=c.prepareStatement("SELECT clock_out,auto_clock_out FROM employee_time_clock WHERE user_id=?")) {
                p.setInt(1,user);try(var r=p.executeQuery()){assertTrue(r.next());assertNull(r.getTimestamp(1));assertFalse(r.getBoolean(2));}
            }
            c.rollback();
        }
    }
}
