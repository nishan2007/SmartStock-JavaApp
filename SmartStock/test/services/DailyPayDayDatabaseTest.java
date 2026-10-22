package services;

import managers.ServerTimeClockManager;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Never reads application connection settings; uses a dedicated disposable cluster. */
class DailyPayDayDatabaseTest {
    @Test void dateClassificationPersistsRecalculatesAndEnforcesOwnership() throws Exception {
        String url=System.getProperty("halfday.test.jdbc", "");
        assumeTrue(!url.isBlank());
        if(!url.equals("jdbc:postgresql://127.0.0.1:55457/postgres"))
            throw new IllegalArgumentException("Use the disposable half-day cluster.");
        String db="halfday_"+UUID.randomUUID().toString().replace("-", "");
        try(Connection admin=DriverManager.getConnection(url,"half_day_test","")) {
            execute(admin,"CREATE DATABASE "+db);
            try(Connection c=DriverManager.getConnection(url.replace("/postgres","/"+db),"half_day_test","")) {
                SchemaContractService.installLocalBaseline(c);
                execute(c,"DROP TABLE employee_daily_pay_days");
                SchemaContractService.ensureDailyHalfDaysUpgrade(c);
                assertTrue(SchemaContractService.validateLocal(c).ready());
                for(int i=0;i<2;i++) SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20261015120000_daily_half_days.sql"));
                execute(c,"INSERT INTO locations(location_id,name,timezone) VALUES(990,'Half day test','America/Guyana')");
                execute(c,"INSERT INTO users(user_id,username,full_name,compensation_type,salary) VALUES(990,'halfday','Daily test','DAILY',4000),(991,'hourlytest','Hourly test','HOURLY',500)");
                ServerTimeClockManager.bindRequest(990,990,"Half day test","America/Guyana","Daily test");
                LocalDate today=LocalDate.now(ZoneId.of("America/Guyana"));
                add(c,990,today,8,10);add(c,990,today,11,12);
                assertPay(c,today,4000,false);
                DailyPayDayService.set(c,990,today,true,990,990,false);
                assertPay(c,today,2000,true);
                DailyPayDayService.set(c,990,today,true,990,990,false); // Replays never halve twice.
                assertPay(c,today,2000,true);
                assertThrows(SQLException.class,()->DailyPayDayService.set(c,990,today,false,990,990,false));
                assertThrows(SQLException.class,()->DailyPayDayService.set(c,990,today,true,991,990,false));
                assertThrows(SQLException.class,()->DailyPayDayService.set(c,990,today.minusDays(1),true,990,990,false));
                DailyPayDayService.set(c,990,today,false,990,990,true);
                assertPay(c,today,4000,false);
                add(c,991,today,8,12);
                assertThrows(SQLException.class,()->DailyPayDayService.set(c,991,today,true,990,990,true));
                execute(c,"INSERT INTO employee_time_clock(user_id,location_id,work_date,clock_in) VALUES(990,990,'"+today+"','"+today+" 13:00:00-04')");
                assertThrows(SQLException.class,()->DailyPayDayService.set(c,990,today,true,990,990,false));
                execute(c,"DELETE FROM employee_time_clock WHERE clock_out IS NULL AND user_id=990");
                var summary=ServerTimeClockManager.loadPayrollDashboard(c).summaries().stream().filter(x->x.userId()==990).findFirst().orElseThrow();
                ServerTimeClockManager.markPayrollPaid(c,summary,new BigDecimal("4000"),"CASH",null);
                assertThrows(SQLException.class,()->DailyPayDayService.set(c,990,today,true,990,990,true));
            } finally {
                ServerTimeClockManager.clearRequest();
                execute(admin,"DROP DATABASE "+db+" WITH (FORCE)");
            }
        }
    }
    private static void add(Connection c,int user,LocalDate date,int start,int end)throws Exception {
        ManualTimeClockService.create(c,user,990,new TimeClockAutoCloseService.Correction(date.atTime(start,0),null,null,null,null,date.atTime(end,0),"Test shift"));
    }
    private static void assertPay(Connection c,LocalDate date,int expected,boolean half)throws Exception {
        var rows=ServerTimeClockManager.loadDashboard(c,true).rows().stream().filter(x->x.userId()==990&&x.workDate().equals(date)).toList();
        assertEquals(expected,rows.stream().map(x->x.totalPay()).reduce(BigDecimal.ZERO,BigDecimal::add).intValueExact());
        assertTrue(rows.stream().allMatch(x->x.halfDay()==half));
        try(var p=c.prepareStatement("SELECT SUM(total_earned) FROM employee_time_clock WHERE user_id=990 AND work_date=?")) {
            p.setDate(1,Date.valueOf(date));try(var rs=p.executeQuery()){assertTrue(rs.next());assertEquals(expected,rs.getBigDecimal(1).intValueExact());}
        }
    }
    private static void execute(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
}
