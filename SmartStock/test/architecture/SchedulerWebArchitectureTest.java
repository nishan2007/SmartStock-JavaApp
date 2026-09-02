package architecture;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerWebArchitectureTest {
    private static final Path ROOT=Path.of("src");
    @Test void gatewayHostsOwnerSchedulerWithoutBrowserCredentials()throws Exception{
        String gateway=Files.readString(ROOT.resolve("app/RemoteGatewayMain.java"));
        String server=Files.readString(ROOT.resolve("services/SchedulerWebServer.java"));
        assertTrue(gateway.contains("SchedulerWebRuntimeController.start()"));
        assertTrue(server.contains("127.0.0.1"));
        assertTrue(server.contains("ACCESS_SCHEDULER_WEB"));
        assertTrue(server.contains("\"aal2\""));
        assertTrue(server.contains("HttpOnly; SameSite=Strict"));
        assertFalse(Files.readString(ROOT.resolve("scheduler-web/app.js")).contains("SUPABASE_"));
    }
    @Test void schedulerIsInstallableAndDoesNotCacheMutations()throws Exception{
        String html=Files.readString(ROOT.resolve("scheduler-web/index.html"));
        String sw=Files.readString(ROOT.resolve("scheduler-web/sw.js"));
        assertTrue(html.contains("manifest.webmanifest"));
        assertTrue(html.contains("viewport-fit=cover"));
        assertTrue(Files.readString(Path.of("pom.xml")).contains("<include>scheduler-web/**</include>"));
        assertTrue(sw.contains("e.request.method==='GET'"));
        assertFalse(sw.contains("method==='POST'"));
    }
    @Test void schedulerMirrorsDesktopPeriodsAndHasPurposeBuiltDeviceViews()throws Exception{
        String html=Files.readString(ROOT.resolve("scheduler-web/index.html"));
        String javascript=Files.readString(ROOT.resolve("scheduler-web/app.js"));
        String css=Files.readString(ROOT.resolve("scheduler-web/app.css"));
        assertTrue(html.contains("value=\"semi\">Semi-monthly"));
        assertTrue(html.contains("value=\"week\">Weekly"));
        assertTrue(html.contains("value=\"detailed\">Detailed"));
        assertTrue(html.contains("value=\"compact\">Compact"));
        assertTrue(html.contains("id=\"desktopMode\"") && html.contains("id=\"phoneMode\""));
        assertTrue(javascript.contains("state.periodMode==='week'"));
        assertTrue(javascript.contains("first?1:16"));
        assertTrue(javascript.contains("period()"));
        assertTrue(css.contains(".days.semi{grid-template-columns:repeat(8"));
        assertTrue(css.contains("body[data-device=\"phone\"]"));
        assertTrue(css.contains("env(safe-area-inset-bottom)"));
    }
    @Test void schedulerSupportsUsernameLoginAndServerEnforcedEmployeeSelfView()throws Exception{
        String html=Files.readString(ROOT.resolve("scheduler-web/index.html"));
        String javascript=Files.readString(ROOT.resolve("scheduler-web/app.js"));
        String server=Files.readString(ROOT.resolve("services/SchedulerWebServer.java"));
        assertTrue(html.contains("id=\"identifier\"") && html.contains("Username or email"));
        assertTrue(javascript.contains("identifier:$('#identifier').value"));
        assertTrue(javascript.contains("state.editable") && javascript.contains("state.selfOnly"));
        assertTrue(server.contains("resolveLoginEmail"));
        assertTrue(server.contains("row.userId()==s.userId"));
        assertTrue(server.contains("if(!granted.contains(\"EDIT_EMPLOYEE_SCHEDULE\"))"));
    }
    @Test void autoSchedulePromptsForEmployeesAndExcludesAdministratorsByDefault()throws Exception{
        String javascript=Files.readString(ROOT.resolve("scheduler-web/app.js"));
        String employees=Files.readString(ROOT.resolve("services/ServerEmployeeScheduleService.java"));
        assertTrue(javascript.contains("Choose employees"));
        assertTrue(javascript.contains("name=\"autoEmployee\""));
        assertTrue(javascript.contains("e.administrator?'':'checked'"));
        assertTrue(javascript.contains("employeeIds=[...document.querySelectorAll"));
        assertTrue(employees.contains("isAdministratorRole"));
        assertTrue(employees.contains("r.role_name"));
    }
    @Test void schemaAndMigrationCoverSchedulerSecurityState()throws Exception{
        String schema=Files.readString(Path.of("database/v1/local/001_schema.sql"));
        String migration=Files.readString(Path.of("database/migrations/v1_after/20260828190000_scheduler_web_app.sql"));
        for(String table:new String[]{"scheduler_web_runtime","scheduler_web_sessions","scheduler_web_auth_attempts","scheduler_web_idempotency"}){
            assertTrue(schema.contains(table));assertTrue(migration.contains(table));
        }
        assertTrue(migration.contains("ENABLE ROW LEVEL SECURITY"));
        assertTrue(migration.contains("REVOKE ALL"));
    }
    @Test void schedulerBrowsersRegisterAutomaticallyButRememberingRequiresDesktopToggle()throws Exception{
        String server=Files.readString(ROOT.resolve("services/SchedulerWebServer.java"));
        String admin=Files.readString(ROOT.resolve("services/SchedulerWebDeviceAdminService.java"));
        String dialog=Files.readString(ROOT.resolve("ui/screens/SchedulerBrowserDevicesDialog.java"));
        String migration=Files.readString(Path.of("database/migrations/v1_after/20260831210000_scheduler_web_devices.sql"));
        assertTrue(server.contains("registerBrowser"));
        assertTrue(server.contains("stay_signed_in=TRUE"));
        assertTrue(server.contains("ss_scheduler_browser"));
        assertTrue(admin.contains("Duration.ofDays(30)"));
        assertTrue(admin.contains("'DEVICE_MANAGEMENT'"));
        assertTrue(dialog.contains("Browsers appear here automatically"));
        assertTrue(dialog.contains("Enable Stay Signed In"));
        assertTrue(migration.contains("stay_signed_in boolean NOT NULL DEFAULT false"));
        assertTrue(migration.contains("ENABLE ROW LEVEL SECURITY"));
    }
    @Test void schedulerCanBeStoppedFromTheActiveServerConsole()throws Exception{
        String api=Files.readString(ROOT.resolve("services/LanApiServer.java"));
        String gateway=Files.readString(ROOT.resolve("services/SchedulerWebRuntimeController.java"));
        String quick=Files.readString(ROOT.resolve("services/CloudflareQuickTunnel.java"));
        String menu=Files.readString(ROOT.resolve("ui/components/AppMenuBar.java"));
        assertTrue(api.contains("/v1/scheduler-web/start"));
        assertTrue(api.contains("/v1/scheduler-web/stop"));
        assertTrue(api.contains("requireSchedulerQrAccess"));
        assertTrue(api.contains("revokeSchedulerWebSessions"));
        assertTrue(gateway.contains("SELECT enabled,generation FROM scheduler_web_runtime"));
        assertTrue(gateway.contains("CloudflareQuickTunnel.start"));
        assertTrue(quick.contains("trycloudflare"));
        assertTrue(Files.readString(ROOT.resolve("services/CloudflareBinary.java")).contains("SMARTSTOCK_CLOUDFLARED_PATH"));
        assertTrue(menu.contains("Scheduler Web App…"));
        assertTrue(menu.contains("canViewSchedulerWeb"));
        assertTrue(menu.contains("ACCESS_SCHEDULER_WEB"));
    }
}
