package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import managers.NavigationManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsiveLoadingArchitectureTest {
    private static final Path SOURCE_ROOT = Path.of("src");

    @Test
    void menuConstructionDoesNotLoadNotificationsSynchronously() throws Exception {
        String source = Files.readString(SOURCE_ROOT.resolve("ui/components/AppMenuBar.java"));
        assertFalse(source.contains("NotificationService.loadSummary()"));
        assertTrue(source.contains("NotificationService.cachedSummary()"));
    }

    @Test
    void macRuntimeIncludesGsonUnsafeSupport() throws Exception {
        String packaging = Files.readString(Path.of("tools/package-macos-release.sh"));
        assertTrue(packaging.contains("jdk.unsupported"),
                "The packaged runtime must support Gson model construction used by devices and pairing");
    }

    @Test
    void lanAndDatabaseBoundariesHaveEdtGuards() throws Exception {
        String lan = Files.readString(SOURCE_ROOT.resolve("services/LanApiClient.java"));
        String database = Files.readString(SOURCE_ROOT.resolve("data/DB.java"));
        assertTrue(lan.contains("BlockingCallGuard.check(\"LAN "));
        assertTrue(database.contains("BlockingCallGuard.check(\"primary database connection\")"));
    }

    @Test
    void navigationCreatesScreensOnlyAfterTransitionGuard() throws Exception {
        String source = Files.readString(SOURCE_ROOT.resolve("managers/NavigationManager.java"));
        int guard = source.indexOf("transitionInProgress = true;", source.indexOf("private static void openScreen"));
        int creation = source.indexOf("createScreen(screenType)", source.indexOf("private static void openScreen"));
        assertTrue(guard > 0 && creation > guard);
    }

    @Test
    void screensDoNotShowThemselvesFromConstructors() throws Exception {
        try (var files = Files.walk(SOURCE_ROOT.resolve("ui"))) {
            assertTrue(files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().endsWith(" 2.java"))
                    .map(path -> {
                        try { return Files.readString(path); }
                        catch (Exception ex) { throw new RuntimeException(ex); }
                    })
                    .noneMatch(source -> source.contains("WindowHelper.showPosWindow(this)")));
        }
    }

    @Test
    void companyPreferencesUsesCompositeSettingsRead() throws Exception {
        String manager = Files.readString(SOURCE_ROOT.resolve("managers/CompanyCustomizationManager.java"));
        String server = Files.readString(SOURCE_ROOT.resolve("services/LanApiServer.java"));
        String screen = Files.readString(SOURCE_ROOT.resolve("ui/screens/CompanyCustomization.java"));
        assertTrue(manager.contains("companyCustomizationRead(\"ALL_SETTINGS\""));
        assertTrue(server.contains("case \"ALL_SETTINGS\""));
        assertTrue(screen.contains("CompanyCustomizationManager::loadAllSettings"));
    }

    @Test
    void lanTransportAndReportIndexesAreReusedAndTracked() throws Exception {
        String client = Files.readString(SOURCE_ROOT.resolve("services/LanApiClient.java"));
        assertTrue(client.contains("cachedPinnedClient"));
        assertTrue(client.contains("if (existing != null) return existing"));
        assertTrue(client.contains("saveEmployeeSession(String session, boolean persistent)"));
        assertTrue(client.contains("else SecureCredentialStore.delete(API_SESSION_SECRET)"));
        assertTrue(client.contains("resetTransport(false, false)"));
        String base = Files.readString(Path.of("database/base_schema_setup.sql"));
        String runtime = Files.readString(SOURCE_ROOT.resolve("services/BaseSchemaInstaller.java"));
        assertTrue(base.contains("sale_items_sale_idx"));
        assertTrue(base.contains("sale_items_product_sale_idx"));
        assertTrue(runtime.contains("sale_items_sale_idx"));
        assertTrue(runtime.contains("sale_items_product_sale_idx"));
    }

    @Test
    void everyNavigationDestinationHasAScreenFactoryCase() throws Exception {
        String source = Files.readString(SOURCE_ROOT.resolve("managers/NavigationManager.java"));
        for (NavigationManager.ScreenType type : NavigationManager.ScreenType.values()) {
            assertTrue(source.contains("case " + type.name() + " ->"),
                    () -> "Missing screen factory for " + type.name());
        }
    }

    @Test
    void sharedLoaderIsBoundedAndFilteredScreensUseStableCancellationKeys() throws Exception {
        String runner = Files.readString(SOURCE_ROOT.resolve("ui/helpers/UiTaskRunner.java"));
        String reports = Files.readString(SOURCE_ROOT.resolve("ui/screens/Reports.java"));
        String timeClock = Files.readString(SOURCE_ROOT.resolve("ui/screens/TimeClock.java"));
        assertTrue(runner.contains("new ArrayBlockingQueue<>(256)"));
        assertTrue(reports.contains("\"reports.load\", cacheKey"));
        assertTrue(timeClock.contains("\"time-clock.load\", cacheKey"));
    }

    @Test
    void receiptAndSlipPreviewConstructorsDoNotFetchLanData() throws Exception {
        String receipt = Files.readString(SOURCE_ROOT.resolve("ui/screens/ReceiptPreview.java"));
        String accountReceipt = Files.readString(SOURCE_ROOT.resolve("ui/screens/AccountPaymentReceiptPreview.java"));
        String slip = Files.readString(SOURCE_ROOT.resolve("ui/screens/CustomOrderSlipPreview.java"));
        assertFalse(receipt.contains("this.receiptSettings = CompanyCustomizationManager.loadReceiptSettings()"));
        assertFalse(accountReceipt.contains("this.receiptSettings = CompanyCustomizationManager.loadReceiptSettings()"));
        assertFalse(slip.contains("this(CustomOrderSlipBuilder.buildFromOrderNumber"));
        assertTrue(slip.contains("CachedUiLoader.load(this, \"custom-order-slip-preview.load\""));
    }

    @Test
    void employeeScheduleUsesOneCompositePeriodRequest() throws Exception {
        String client = Files.readString(SOURCE_ROOT.resolve("services/LanApiClient.java"));
        String server = Files.readString(SOURCE_ROOT.resolve("services/LanApiServer.java"));
        String screen = Files.readString(SOURCE_ROOT.resolve("ui/screens/WeeklySchedule.java"));
        assertTrue(client.contains("post(\"/v1/schedule/snapshot\""));
        assertTrue(server.contains("/v1/schedule/snapshot"));
        assertTrue(screen.contains("EmployeeScheduleService.loadPeriod"));
        assertFalse(screen.contains("UiTaskRunner.supplyAsync"));
        assertTrue(client.contains("INVALID_SERVER_RESPONSE"));
        String scheduleService = Files.readString(SOURCE_ROOT.resolve("services/ServerEmployeeScheduleService.java"));
        assertTrue(scheduleService.contains("synchronized (SCHEMA_LOCK)"));
        assertTrue(scheduleService.contains("schemaReady = true"));
    }
}
