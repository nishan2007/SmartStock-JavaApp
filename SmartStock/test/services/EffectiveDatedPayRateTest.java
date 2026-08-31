package services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectiveDatedPayRateTest {
    @Test
    void currentSemiMonthlyRateStartsAtCurrentPeriodBoundary() {
        var period = EmployeePayrollSettingsService.periodFor(
                EmployeePayrollSettingsService.PeriodType.SEMI_MONTHLY,
                new BigDecimal("80.00"), LocalDate.of(2026, 8, 24));

        assertEquals(LocalDate.of(2026, 8, 16), period.start());
        assertEquals(LocalDate.of(2026, 8, 31), period.end());
    }

    @Test
    void payrollAndClockOutResolveRateByWorkDate() throws Exception {
        String reports = Files.readString(Path.of("src/managers/ServerTimeClockManager.java"));
        String autoClose = Files.readString(Path.of("src/services/TimeClockAutoCloseService.java"));
        String employeeAdmin = Files.readString(Path.of("src/services/LanEmployeeAdminService.java"));

        assertTrue(reports.contains("effective_from <= tc.work_date"));
        assertTrue(reports.contains("COALESCE(pay.pay_rate, u.salary, 0)"));
        assertTrue(autoClose.contains("effective_from <= tc.work_date"));
        assertTrue(autoClose.contains("COALESCE(pay.pay_rate, u.salary, 0)"));
        assertTrue(employeeAdmin.contains("saveCurrentPeriodPayRate"));
    }

    @Test
    void migrationBackfillsExistingEmployeesAndRejectsNegativeRates() throws Exception {
        String migration = Files.readString(Path.of(
                "database/migrations/v1_after/20260824160000_effective_dated_pay_rates.sql"));

        assertTrue(migration.contains("FROM public.users u"));
        assertTrue(migration.contains("pay_rate >= 0"));
        assertTrue(migration.contains("ALTER COLUMN pay_rate SET NOT NULL"));
    }

    @Test
    void serverStartupRunsPayRateMigrationBeforeSchemaValidation() throws Exception {
        String server = Files.readString(Path.of("src/services/LanApiServer.java"));
        String installer = Files.readString(Path.of("src/services/LanApiSchemaInstaller.java"));

        assertTrue(server.contains("LanApiSchemaInstaller.ensureSchema(connection)"));
        assertTrue(installer.indexOf("ensureEffectiveDatedPayRatesUpgrade")
                < installer.indexOf("requireLocalReady"));
        assertTrue(installer.indexOf("ensureMissingPayrollBaselinesUpgrade")
                < installer.indexOf("requireLocalReady"));
    }

    @Test
    void followUpMigrationCreatesOnlyMissingEmployeeBaselines() throws Exception {
        String migration = Files.readString(Path.of(
                "database/migrations/v1_after/20260824170000_backfill_missing_payroll_baselines.sql"));

        assertTrue(migration.contains("FROM public.users u"));
        assertTrue(migration.contains("effective_from = DATE '1900-01-01'"));
        assertTrue(migration.contains("ON CONFLICT (user_id, effective_from) DO NOTHING"));
    }
}
