package services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CurrentPeriodPayRateIntegrationTest {
    @Test
    void lateSalaryCorrectionCoversWholeCurrentPeriodAndFutureSnapshots() throws Exception {
        String url = System.getProperty("smartstock.variants.test.jdbc", "");
        assumeTrue(!url.isBlank());
        try (Connection c = DriverManager.getConnection(url,
                System.getProperty("smartstock.variants.test.user", "variant_test"), "")) {
            SchemaContractService.requireLocalReady(c);
            c.setAutoCommit(false);
            try {
                int user;
                try (var ps = c.prepareStatement("INSERT INTO users(username,compensation_type,salary) VALUES (?,'SALARY',0) RETURNING user_id")) {
                    ps.setString(1, "salary-fix-" + UUID.randomUUID());
                    try (var rs = ps.executeQuery()) { rs.next(); user = rs.getInt(1); }
                }
                for (String day : new String[]{"2026-08-16", "2026-09-01", "2026-09-08", "2026-09-16"}) {
                    try (var ps = c.prepareStatement("""
                            INSERT INTO employee_payroll_settings(setting_id,user_id,period_type,
                                work_hour_limit,effective_from,compensation_type,pay_rate)
                            VALUES (?,?,'SEMI_MONTHLY',80,?,'SALARY',0)
                            """)) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setInt(2, user);
                        ps.setDate(3, Date.valueOf(day));
                        ps.executeUpdate();
                    }
                }
                assertEquals(true, EmployeePayrollSettingsService.currentPeriodRateNeedsRepair(c, user,
                        LocalDate.of(2026, 9, 12), "SALARY", "SALARY", new BigDecimal("120000")));
                EmployeePayrollSettingsService.saveCurrentPeriodPayRate(c, user,
                        LocalDate.of(2026, 9, 12), "SALARY", "SALARY", new BigDecimal("120000"));
                assertEquals(false, EmployeePayrollSettingsService.currentPeriodRateNeedsRepair(c, user,
                        LocalDate.of(2026, 9, 12), "SALARY", "SALARY", new BigDecimal("120000")));
                for (String day : new String[]{"2026-09-01", "2026-09-07", "2026-09-12", "2026-09-20"}) {
                    assertEquals(0, new BigDecimal("120000").compareTo(
                            EmployeePayrollSettingsService.payRateFor(c, user, LocalDate.parse(day)).rate()));
                }
                assertEquals(0, EmployeePayrollSettingsService.payRateFor(c, user,
                        LocalDate.of(2026, 8, 31)).rate().compareTo(BigDecimal.ZERO));
            } finally { c.rollback(); }
        }
    }
}
