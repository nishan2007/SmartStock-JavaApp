package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
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
    void mainMenuNavigationProvidesImmediateFeedbackAndRestoresTiles() throws Exception {
        String navigation = Files.readString(SOURCE_ROOT.resolve("managers/NavigationManager.java"));
        String mainMenu = Files.readString(SOURCE_ROOT.resolve("ui/screens/MainMenu.java"));
        int openScreen = navigation.indexOf("private static void openScreen");
        int createScreen = navigation.indexOf("createScreen(screenType)", openScreen);
        int busyFeedback = navigation.indexOf("sourceMenu.setNavigationInProgress(true)", openScreen);

        assertTrue(busyFeedback > openScreen && busyFeedback < createScreen,
                "The first accepted tile click must show feedback before screen creation");
        assertTrue(navigation.contains("sourceMenu.setNavigationInProgress(false)"),
                "Failed navigation must restore the menu tiles");
        assertTrue(navigation.contains("mainMenu.setNavigationInProgress(false)"),
                "Returning to the main menu must restore permission-based tile states");
        assertTrue(mainMenu.contains("Cursor.WAIT_CURSOR"));
        assertTrue(mainMenu.contains("for (JButton button : menuButtons())"));
        assertTrue(mainMenu.contains("forwardTileClicks(textPanel, button)"),
                "Every nested text component must forward its mouse clicks to the containing tile");
        assertTrue(mainMenu.contains("if (activate) button.doClick(0)"));
        assertTrue(mainMenu.contains("titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT)"));
        assertTrue(mainMenu.contains("descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT)"));
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
        String base = Files.readString(Path.of("database/v1/local/001_schema.sql"));
        String runtime = Files.readString(SOURCE_ROOT.resolve("services/BaseSchemaInstaller.java"));
        assertTrue(base.contains("sale_items_sale_idx"));
        assertTrue(base.contains("sale_items_product_sale_idx"));
        assertTrue(runtime.contains("SchemaContractService.requireLocalReady(connection)"));
        assertFalse(runtime.contains("CREATE INDEX"));
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
    void everyNavigationDestinationUsesTheVisibleFirstFactory() throws Exception {
        String source = Files.readString(SOURCE_ROOT.resolve("managers/NavigationManager.java"));
        String factory = source.substring(source.indexOf("private static JFrame createScreen"),
                source.indexOf("private static ScreenType parseScreenType"));
        for (NavigationManager.ScreenType type : NavigationManager.ScreenType.values()) {
            Pattern visibleFirst = Pattern.compile("case\\s+" + type.name()
                    + "\\s+->\\s+(?:deferred\\(|new DeferredScreenFrame\\()", Pattern.DOTALL);
            assertTrue(visibleFirst.matcher(factory).find(),
                    () -> "Screen bypasses visible-first factory: " + type.name());
        }
        String login = Files.readString(SOURCE_ROOT.resolve("ui/screens/Login.java"));
        assertTrue(login.contains("NavigationManager.showMainMenuAfterLogin(this)"));
        assertFalse(login.contains("new MainMenu()"));
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
        assertTrue(screen.contains("UiTaskRunner.supplyAsync(()->EmployeeScheduleService.loadActiveEmployees"));
        assertTrue(screen.contains("UiTaskRunner.supplyAsync(()->EmployeeScheduleService.loadShifts"));
        assertTrue(client.contains("INVALID_SERVER_RESPONSE"));
        String scheduleService = Files.readString(SOURCE_ROOT.resolve("services/ServerEmployeeScheduleService.java"));
        assertTrue(scheduleService.contains("synchronized (SCHEMA_LOCK)"));
        assertTrue(scheduleService.contains("schemaReady = true"));
    }

    @Test
    void coldHeavyScreensExposeVisibleFirstShells() throws Exception {
        String navigation = Files.readString(SOURCE_ROOT.resolve("managers/NavigationManager.java"));
        String mainMenu = Files.readString(SOURCE_ROOT.resolve("ui/screens/MainMenu.java"));
        String sale = Files.readString(SOURCE_ROOT.resolve("ui/screens/MakeASale.java"));
        String preferences = Files.readString(SOURCE_ROOT.resolve("ui/screens/CompanyCustomization.java"));

        assertTrue(navigation.contains("case COMPANY_CUSTOMIZATION -> new DeferredScreenFrame"));
        assertTrue(navigation.contains("UiTaskRunner.submit(this, taskKey, screenFactory::get"));
        assertTrue(mainMenu.contains("new Timer(75"));
        assertTrue(mainMenu.contains("Preparing menu..."));
        assertFalse(mainMenu.contains("createMenuButton(\"Make a Sale\""));
        assertTrue(sale.contains("Preparing point of sale..."));
        assertTrue(sale.contains("new javax.swing.Timer(50"));
        assertTrue(preferences.contains("addPermittedCardPlaceholders()"));
        assertFalse(preferences.contains("addPermittedCards();"));
    }

    @Test
    void coldLocalAssetsAndFontEnumerationAreDeferred() throws Exception {
        String mainMenu = Files.readString(SOURCE_ROOT.resolve("ui/screens/MainMenu.java"));
        String sale = Files.readString(SOURCE_ROOT.resolve("ui/screens/MakeASale.java"));
        String preferences = Files.readString(SOURCE_ROOT.resolve("ui/screens/CompanyCustomization.java"));
        String theme = Files.readString(SOURCE_ROOT.resolve("ui/helpers/ThemeManager.java"));

        assertTrue(mainMenu.contains("main-menu.local-assets"));
        assertTrue(sale.contains("make-sale.branding"));
        assertFalse(sale.contains("setSmartStockAppLogo();"));
        assertTrue(preferences.contains("company-preferences.badge-fonts"));
        assertFalse(preferences.contains("new JComboBox<>(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())"));
        assertTrue(theme.contains("cachedDarkMode"));
    }

    @Test
    void deferredScreensDoNotStartJobsUntilTheirRealWindowIsDisplayable() throws Exception {
        String runner = Files.readString(SOURCE_ROOT.resolve("ui/helpers/UiTaskRunner.java"));
        String loader = Files.readString(SOURCE_ROOT.resolve("ui/helpers/CachedUiLoader.java"));
        String mainMenu = Files.readString(SOURCE_ROOT.resolve("ui/screens/MainMenu.java"));

        assertTrue(runner.contains("if (!owner.isDisplayable())"));
        assertTrue(runner.contains("windowOpened(WindowEvent event)"));
        assertTrue(loader.contains("if (!owner.isDisplayable())"));
        assertTrue(mainMenu.contains("menuBuildTimer.start();"));
        assertTrue(mainMenu.contains("@Override public void windowOpened(WindowEvent event)"));
        assertFalse(mainMenu.contains("SwingUtilities.invokeLater(() -> {\n            if (!isDisplayable()) return;\n            loadLocalMenuAssets();"));
    }

    @Test
    void managerApprovalVerificationRunsAwayFromTheEdt() throws Exception {
        String approval = Files.readString(SOURCE_ROOT.resolve("services/ManagerApprovalService.java"));
        assertTrue(approval.contains("SwingWorker<ApprovalResult, Void>"));
        assertTrue(approval.contains("doInBackground()"));
        assertTrue(approval.contains("verifyAwayFromEdt"));
    }

    @Test
    void customOrderAndNotificationMutationsUseBackgroundJobs() throws Exception {
        String orders = Files.readString(SOURCE_ROOT.resolve("ui/screens/customorders/CustomOrders.java"));
        String catalog = Files.readString(SOURCE_ROOT.resolve("ui/screens/customorders/CustomOrderItems.java"));
        String notifications = Files.readString(SOURCE_ROOT.resolve("ui/screens/NotificationsDialog.java"));
        assertTrue(orders.contains("custom-orders.mutation."));
        assertTrue(orders.contains("custom-orders.save"));
        assertTrue(catalog.contains("custom-catalog.save-item"));
        assertTrue(catalog.contains("custom-catalog.save-variant"));
        assertTrue(notifications.contains("UiTaskRunner.submit(this, \"notifications.\" + action"));
    }

    @Test
    void macReleaseArchiveExcludesMetadataThatBreaksUpdateSignatures() throws Exception {
        String packaging = Files.readString(Path.of("tools/package-macos-release.sh"));
        assertTrue(packaging.contains("ditto -c -k --keepParent --norsrc --noextattr --noqtn --noacl"));
    }
}
