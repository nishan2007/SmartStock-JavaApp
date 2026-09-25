package services;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CurrentPayrollHourLimitIntegrationTest {
    @Test void currentLimitOverridesMidPeriodSnapshotWithoutChangingRatesOrPendingType() throws Exception {
        String url=System.getProperty("smartstock.variants.test.jdbc","");assumeTrue(!url.isBlank());
        try(Connection c=DriverManager.getConnection(url,System.getProperty("smartstock.variants.test.user","variant_test"),"")) {
            SchemaContractService.requireLocalReady(c);c.setAutoCommit(false);
            try {
                int user;
                try(var p=c.prepareStatement("INSERT INTO users(username,compensation_type,salary) VALUES (?,'HOURLY',200) RETURNING user_id")) {
                    p.setString(1,"limit-test-"+UUID.randomUUID());try(var r=p.executeQuery()){r.next();user=r.getInt(1);}
                }
                try(var p=c.prepareStatement("INSERT INTO employee_payroll_settings(setting_id,user_id,period_type,work_hour_limit,effective_from,compensation_type,pay_rate) VALUES (?,?,'SEMI_MONTHLY',80,?,'HOURLY',?)")) {
                    p.setObject(1,UUID.randomUUID());p.setInt(2,user);p.setDate(3,Date.valueOf("2026-09-01"));p.setBigDecimal(4,new BigDecimal("100"));p.executeUpdate();
                    p.setObject(1,UUID.randomUUID());p.setDate(3,Date.valueOf("2026-09-05"));p.setBigDecimal(4,new BigDecimal("200"));p.executeUpdate();
                }
                LocalDate today=LocalDate.of(2026,9,12);
                var pending=EmployeePayrollSettingsService.saveNextSetting(c,user,EmployeePayrollSettingsService.PeriodType.WEEKLY,new BigDecimal("40"),today);
                EmployeePayrollSettingsService.saveChangedSetting(c,user,EmployeePayrollSettingsService.PeriodType.WEEKLY,new BigDecimal("60"),today,"HOURLY",true);
                for(LocalDate day:new LocalDate[]{LocalDate.of(2026,9,2),LocalDate.of(2026,9,6),today}) {
                    var period=EmployeePayrollSettingsService.periodFor(c,user,day,"HOURLY");
                    assertEquals(EmployeePayrollSettingsService.PeriodType.SEMI_MONTHLY,period.periodType());
                    assertEquals(0,new BigDecimal("60").compareTo(period.workHourLimit()));
                }
                assertEquals(0,new BigDecimal("100").compareTo(EmployeePayrollSettingsService.payRateFor(c,user,LocalDate.of(2026,9,2)).rate()));
                assertEquals(0,new BigDecimal("200").compareTo(EmployeePayrollSettingsService.payRateFor(c,user,today).rate()));
                var view=EmployeePayrollSettingsService.loadCurrentAndPending(c,user,today,"HOURLY");
                assertNotNull(view.pending());assertEquals(EmployeePayrollSettingsService.PeriodType.WEEKLY,view.pending().periodType());
                assertEquals(pending.effectiveFrom(),view.pending().effectiveFrom());
            } finally {c.rollback();}
        }
    }
}
