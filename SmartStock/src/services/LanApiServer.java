package services;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import data.DB;
import data.DatabaseConfig;
import data.EnvironmentProfile;
import data.DatabaseMode;
import managers.ServerTimeClockManager;
import managers.ServerCompanyCustomizationRepository;
import managers.SupabaseSessionManager;
import ui.helpers.PerformanceDiagnostics;

import javax.crypto.Cipher;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Authenticated HTTPS boundary between registers and the store database.
 * This server deliberately exposes named operations only; it has no SQL or
 * generic table endpoint.
 */
public final class LanApiServer implements AutoCloseable {
    public static final int DEFAULT_PORT = 8443;
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;
    private static final int MAX_IMAGE_BODY_BYTES = 16 * 1024 * 1024;
    private static final int MAX_CLOUD_FILE_BODY_BYTES = 36 * 1024 * 1024;
    private static final Set<String> DEVICE_HEADER_EXEMPT_ROUTES = Set.of(
            "/v1/devices/enroll", "/v1/devices/claim", "/v1/devices/local-claim");
    private static final Duration SESSION_LIFETIME = Duration.ofMinutes(15);
    private static final Duration SESSION_ABSOLUTE_LIFETIME = Duration.ofHours(12);
    private static final Gson GSON = LanJson.create();
    private static final HttpClient CLOUD_HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)).build();

    private final HttpsServer server;
    private final ExecutorService executor;
    private final LanTlsIdentity tlsIdentity;
    private final LanDiscoveryService discoveryService;

    private LanApiServer(HttpsServer server, ExecutorService executor, LanTlsIdentity tlsIdentity,
                         LanDiscoveryService discoveryService) {
        this.server = server;
        this.executor = executor;
        this.tlsIdentity = tlsIdentity;
        this.discoveryService = discoveryService;
    }

    public static LanApiServer start() throws Exception {
        int port = Integer.getInteger("smartstock.lan.api.port", DEFAULT_PORT);
        LanTlsIdentity identity = LanTlsIdentity.loadOrCreate();
        HttpsServer https = HttpsServer.create(new InetSocketAddress(port), 50);
        https.setHttpsConfigurator(new HttpsConfigurator(identity.sslContext()));
        ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(4, Math.min(16, Runtime.getRuntime().availableProcessors() * 2)),
                runnable -> {
                    Thread thread = new Thread(runnable, "smartstock-lan-api");
                    thread.setDaemon(true);
                    return thread;
                });
        https.setExecutor(executor);
        try (Connection connection = DB.getConnection()) {
            DeviceCredentialSchemaInstaller.ensureSchema(connection);
            LanApiSchemaInstaller.ensureSchema(connection);
        }
        LanDiscoveryService discovery = Boolean.getBoolean("smartstock.remote.gateway")
                ? null : LanDiscoveryService.start(port, identity, discoveryIdentity(identity));
        LanApiServer api = new LanApiServer(https, executor, identity, discovery);
        api.installRoutes();
        https.start();
        System.out.println("SmartStock LAN service listening on HTTPS port " + port
                + "; certificate " + identity.fingerprint());
        return api;
    }

    private static LanDiscoveryService.DiscoveryIdentity discoveryIdentity(
            LanTlsIdentity identity) throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        String storeName = "Unassigned Store";
        String storeCode = "";
        if (config.locationId() != null) {
            try (Connection connection = DB.getConnection();
                 PreparedStatement statement = connection.prepareStatement("""
                         SELECT name, COALESCE(receipt_store_code, '')
                         FROM locations WHERE location_id = ?
                         """)) {
                statement.setInt(1, config.locationId());
                try (ResultSet rows = statement.executeQuery()) {
                    if (rows.next()) {
                        storeName = rows.getString(1);
                        storeCode = rows.getString(2);
                    }
                }
            }
        }
        String computerName;
        try {
            computerName = java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            computerName = LanTlsIdentity.tlsHostName();
        }
        String fingerprint = identity.fingerprint();
        String serverId = fingerprint == null ? "UNKNOWN"
                : fingerprint.substring(0, Math.min(8, fingerprint.length())).toUpperCase();
        return new LanDiscoveryService.DiscoveryIdentity(
                EnvironmentProfile.active().id(), storeName, storeCode, computerName, serverId);
    }

    public String pairingPhrase() {
        return tlsIdentity.currentPairingPhrase();
    }

    public String certificateFingerprint() {
        return tlsIdentity.fingerprint();
    }

    private void installRoutes() {
        server.createContext("/v1/health", this::health);
        server.createContext("/v1/devices/enroll", exchange -> handle(exchange, this::enroll));
        server.createContext("/v1/devices/claim", exchange -> handle(exchange, this::claim));
        server.createContext("/v1/devices/local-claim", exchange -> handle(exchange, this::localServerClaim));
        server.createContext("/v1/devices/rotate", exchange -> handle(exchange, this::rotate));
        server.createContext("/v1/sessions/login", exchange -> handle(exchange, this::login));
        server.createContext("/v1/sessions/badge-status", exchange -> handle(exchange, this::badgeStatus));
        server.createContext("/v1/sessions/badge-pin-setup", exchange -> handle(exchange, this::badgePinSetup));
        server.createContext("/v1/sessions/refresh", exchange -> handle(exchange, this::refresh));
        server.createContext("/v1/sessions/policy", exchange -> handle(exchange, this::sessionPolicy));
        server.createContext("/v1/sessions/logout", exchange -> handle(exchange, this::logout));
        server.createContext("/v1/sessions/stores", exchange -> handle(exchange, this::sessionStores));
        server.createContext("/v1/sessions/switch-store", exchange -> handle(exchange, this::switchSessionStore));
        server.createContext("/v1/remote/commands", exchange -> handle(exchange, this::remoteCommands));
        server.createContext("/v1/session/permissions", exchange -> handle(exchange, this::permissions));
        server.createContext("/v1/approvals", exchange -> handle(exchange, this::approve));
        server.createContext("/v1/catalog/search", exchange -> handle(exchange, this::searchCatalog));
        server.createContext("/v1/customers/accounts", exchange -> handle(exchange, this::customerAccounts));
        server.createContext("/v1/cash-drawers/current", exchange -> handle(exchange, this::currentCashDrawer));
        server.createContext("/v1/sales/settings", exchange -> handle(exchange, this::salesSettings));
        server.createContext("/v1/sales/checkout", exchange -> handle(exchange, this::checkout));
        server.createContext("/v1/sales/search", exchange -> handle(exchange, this::searchSales));
        server.createContext("/v1/sales/return-stores", exchange -> handle(exchange, this::returnStores));
        server.createContext("/v1/sales/return-details", exchange -> handle(exchange, this::returnDetails));
        server.createContext("/v1/sales/refund", exchange -> handle(exchange, this::refund));
        server.createContext("/v1/sales/receipt", exchange -> handle(exchange, this::saleReceipt));
        server.createContext("/v1/sales/history", exchange -> handle(exchange, this::salesHistory));
        server.createContext("/v1/sales/details", exchange -> handle(exchange, this::salesDetails));
        server.createContext("/v1/held-carts/create", exchange -> handle(exchange, this::createHeldCart));
        server.createContext("/v1/held-carts/list", exchange -> handle(exchange, this::listHeldCarts));
        server.createContext("/v1/held-carts/resume", exchange -> handle(exchange, this::resumeHeldCart));
        server.createContext("/v1/inventory/lookups", exchange -> handle(exchange, this::inventoryLookups));
        server.createContext("/v1/inventory/cross-store-search", exchange -> handle(exchange, this::crossStoreInventorySearch));
        server.createContext("/v1/inventory/receiving-search", exchange -> handle(exchange, this::receivingSearch));
        server.createContext("/v1/inventory/list", exchange -> handle(exchange, this::inventoryList));
        server.createContext("/v1/inventory/details", exchange -> handle(exchange, this::inventoryDetails));
        server.createContext("/v1/inventory/receiving-history", exchange -> handle(exchange, this::receivingHistory));
        server.createContext("/v1/inventory/receive", exchange -> handle(exchange, this::receiveInventory));
        server.createContext("/v1/transfers/destinations", exchange -> handle(exchange, this::transferDestinations));
        server.createContext("/v1/transfers/products", exchange -> handle(exchange, this::transferProducts));
        server.createContext("/v1/transfers/incoming", exchange -> handle(exchange, this::incomingTransfers));
        server.createContext("/v1/transfers/outgoing", exchange -> handle(exchange, this::outgoingTransfers));
        server.createContext("/v1/transfers/items", exchange -> handle(exchange, this::transferItems));
        server.createContext("/v1/transfers/create", exchange -> handle(exchange, this::createTransfer));
        server.createContext("/v1/transfers/receive", exchange -> handle(exchange, this::receiveTransfer));
        server.createContext("/v1/catalog/departments/list", exchange -> handle(exchange, this::catalogDepartments));
        server.createContext("/v1/catalog/departments/save", exchange -> handle(exchange, this::saveCatalogDepartment));
        server.createContext("/v1/catalog/vendors/list", exchange -> handle(exchange, this::catalogVendors));
        server.createContext("/v1/catalog/vendors/save", exchange -> handle(exchange, this::saveCatalogVendor));
        server.createContext("/v1/catalog/customer-types/list", exchange -> handle(exchange, this::catalogCustomerTypes));
        server.createContext("/v1/catalog/customer-types/save", exchange -> handle(exchange, this::saveCatalogCustomerType));
        server.createContext("/v1/products/edit-search", exchange -> handle(exchange, this::editableProductSearch));
        server.createContext("/v1/products/price-tags", exchange -> handle(exchange, this::priceTagProductSearch));
        server.createContext("/v1/products/price-tag-settings", exchange -> handle(exchange, this::priceTagSettings));
        server.createContext("/v1/products/create", exchange -> handle(exchange, this::createProduct));
        server.createContext("/v1/products/update", exchange -> handle(exchange, this::updateProduct));
        server.createContext("/v1/sync/status", exchange -> handle(exchange, this::syncStatus));
        server.createContext("/v1/sync/run", exchange -> handle(exchange, this::runSync));
        server.createContext("/v1/sync/resolve", exchange -> handle(exchange, this::resolveSyncConflict));
        server.createContext("/v1/workstation/settings", exchange -> handle(exchange, this::workstationSettings));
        server.createContext("/v1/workstation/device-code", exchange -> handle(exchange, this::updateWorkstationDeviceCode));
        server.createContext("/v1/workstation/timezone", exchange -> handle(exchange, this::updateWorkstationTimezone));
        server.createContext("/v1/customer-accounts/list", exchange -> handle(exchange, this::customerAccountList));
        server.createContext("/v1/customer-accounts/details", exchange -> handle(exchange, this::customerAccountDetails));
        server.createContext("/v1/customer-accounts/save", exchange -> handle(exchange, this::saveCustomerAccount));
        server.createContext("/v1/customer-accounts/adjust", exchange -> handle(exchange, this::adjustCustomerAccount));
        server.createContext("/v1/customer-accounts/transactions", exchange -> handle(exchange, this::customerAccountTransactions));
        server.createContext("/v1/customer-accounts/payments", exchange -> handle(exchange, this::customerAccountPayments));
        server.createContext("/v1/customer-accounts/payment-receipt", exchange -> handle(exchange, this::customerAccountPaymentReceipt));
        server.createContext("/v1/employees/change-pin", exchange -> handle(exchange, this::changeEmployeePin));
        server.createContext("/v1/cash/change-basket/state", exchange -> handle(exchange, this::changeBasketState));
        server.createContext("/v1/cash/change-basket/update", exchange -> handle(exchange, this::updateChangeBasket));
        server.createContext("/v1/cash/drawer/state", exchange -> handle(exchange, this::cashDrawerState));
        server.createContext("/v1/cash/drawer/open", exchange -> handle(exchange, this::openCashDrawer));
        server.createContext("/v1/cash/drawer/handover", exchange -> handle(exchange, this::handoverCashDrawer));
        server.createContext("/v1/cash/drawer/close", exchange -> handle(exchange, this::closeCashDrawer));
        server.createContext("/v1/cash/drawer/recent", exchange -> handle(exchange, this::recentCashDrawers));
        server.createContext("/v1/cash/drawer/revise", exchange -> handle(exchange, this::reviseCashDrawer));
        server.createContext("/v1/cash/drawer/admin-state", exchange -> handle(exchange, this::cashDrawerAdminState));
        server.createContext("/v1/cash/drawer/save", exchange -> handle(exchange, this::saveCashDrawer));
        server.createContext("/v1/cash/drawer/assign", exchange -> handle(exchange, this::assignCashDrawer));
        server.createContext("/v1/cash/drawer/unassign", exchange -> handle(exchange, this::unassignCashDrawer));
        server.createContext("/v1/cash/drawer/change-target", exchange -> handle(exchange, this::saveCashDrawerChangeTarget));
        server.createContext("/v1/security/devices/list", exchange -> handle(exchange, this::deviceAdminList));
        server.createContext("/v1/security/devices/sessions", exchange -> handle(exchange, this::deviceAdminSessions));
        server.createContext("/v1/security/devices/update", exchange -> handle(exchange, this::deviceAdminUpdate));
        server.createContext("/v1/security/servers/list", exchange -> handle(exchange, this::serverAdminList));
        server.createContext("/v1/security/servers/update", exchange -> handle(exchange, this::serverAdminUpdate));
        server.createContext("/v1/security/servers/prepare-standby", exchange -> handle(exchange, x->serverAdminAction(x,"PREPARE_STANDBY")));
        server.createContext("/v1/security/servers/begin-handoff", exchange -> handle(exchange, x->serverAdminAction(x,"BEGIN_HANDOFF")));
        server.createContext("/v1/security/servers/handoff-status", exchange -> handle(exchange, this::serverAdminHandoffStatus));
        server.createContext("/v1/security/servers/emergency-takeover", exchange -> handle(exchange, x->serverAdminAction(x,"EMERGENCY_TAKEOVER")));
        server.createContext("/v1/security/servers/retire", exchange -> handle(exchange, x->serverAdminAction(x,"RETIRE")));
        server.createContext("/v1/security/status", exchange -> handle(exchange, this::deviceSecurityStatus));
        server.createContext("/v1/locations/list", exchange -> handle(exchange, this::locationList));
        server.createContext("/v1/locations/save", exchange -> handle(exchange, this::saveLocation));
        server.createContext("/v1/locations/process-email", exchange -> handle(exchange, this::processLocationEmail));
        server.createContext("/v1/reports/options", exchange -> handle(exchange, this::reportOptions));
        server.createContext("/v1/reports/load", exchange -> handle(exchange, this::loadReports));
        server.createContext("/v1/reports/orders", exchange -> handle(exchange, this::orderReport));
        server.createContext("/v1/reports/invoices", exchange -> handle(exchange, this::invoiceReport));
        server.createContext("/v1/maintenance/parts/list", exchange -> handle(exchange, this::maintenancePartsList));
        server.createContext("/v1/maintenance/parts/save", exchange -> handle(exchange, this::maintenancePartSave));
        server.createContext("/v1/maintenance/parts/delete", exchange -> handle(exchange, this::maintenancePartDelete));
        server.createContext("/v1/maintenance/machines/state", exchange -> handle(exchange, this::machineState));
        server.createContext("/v1/maintenance/machines/detail", exchange -> handle(exchange, this::machineDetail));
        server.createContext("/v1/maintenance/machines/update", exchange -> handle(exchange, this::machineMutation));
        server.createContext("/v1/maintenance/workflow/state", exchange -> handle(exchange, this::maintenanceWorkflowState));
        server.createContext("/v1/maintenance/workflow/detail", exchange -> handle(exchange, this::maintenanceWorkflowDetail));
        server.createContext("/v1/maintenance/workflow/update", exchange -> handle(exchange, this::maintenanceWorkflowMutation));
        server.createContext("/v1/custom-orders/catalog", exchange -> handle(exchange, this::customOrderCatalog));
        server.createContext("/v1/custom-orders/create", exchange -> handle(exchange, this::createCustomOrder));
        server.createContext("/v1/custom-orders/dashboard", exchange -> handle(exchange, this::customOrderDashboard));
        server.createContext("/v1/custom-orders/assign", exchange -> handle(exchange, this::assignCustomOrder));
        server.createContext("/v1/custom-orders/admin/state", exchange -> handle(exchange, this::customCatalogAdminState));
        server.createContext("/v1/custom-orders/admin/update", exchange -> handle(exchange, this::customCatalogAdminMutation));
        server.createContext("/v1/custom-orders/workflow/read", exchange -> handle(exchange, this::customOrderWorkflowRead));
        server.createContext("/v1/custom-orders/workflow/update", exchange -> handle(exchange, this::customOrderWorkflowMutation));
        server.createContext("/v1/configuration/read", exchange -> handle(exchange, this::companyCustomizationRead));
        server.createContext("/v1/configuration/update", exchange -> handle(exchange, this::companyCustomizationMutation));
        server.createContext("/v1/cloud/update/latest", exchange -> handle(exchange, this::latestAppRelease));
        server.createContext("/v1/cloud/update/sign", exchange -> handle(exchange, this::signAppRelease));
        server.createContext("/v1/cloud/storage/upload", exchange -> handle(exchange, this::uploadCloudFile));
        server.createContext("/v1/cloud/storage/download", exchange -> handle(exchange, this::downloadCloudFile));
        server.createContext("/v1/images/fetch", exchange -> handle(exchange, this::fetchImageAsset));
        server.createContext("/v1/images/list", exchange -> handle(exchange, this::listImageAssets));
        server.createContext("/v1/images/reconcile", exchange -> handle(exchange, this::reconcileImageAssets));
        server.createContext("/v1/images/retain", exchange -> handle(exchange, this::retainImageAsset));
        server.createContext("/v1/images/purge", exchange -> handle(exchange, this::purgeImageAsset));
        server.createContext("/v1/accounting/balance-sheet/read", exchange -> handle(exchange, this::balanceSheetRead));
        server.createContext("/v1/accounting/balance-sheet/update", exchange -> handle(exchange, this::balanceSheetMutation));
        server.createContext("/v1/email/queue", exchange -> handle(exchange, this::queueEmail));
        server.createContext("/v1/documents/custom-order-slip", exchange -> handle(exchange, this::customOrderSlip));
        server.createContext("/v1/time-clock/auto-close/settings", exchange -> handle(exchange, this::timeClockAutoCloseSettings));
        server.createContext("/v1/time-clock/auto-close/save-settings", exchange -> handle(exchange, this::saveTimeClockAutoCloseSettings));
        server.createContext("/v1/time-clock/auto-close/reviews", exchange -> handle(exchange, this::timeClockAutoCloseReviews));
        server.createContext("/v1/time-clock/auto-close/notice", exchange -> handle(exchange, this::timeClockAutoCloseNotice));
        server.createContext("/v1/time-clock/auto-close/confirm", exchange -> handle(exchange, this::confirmTimeClockAutoClose));
        server.createContext("/v1/time-clock/auto-close/correct", exchange -> handle(exchange, this::correctTimeClockAutoClose));
        server.createContext("/v1/time-clock/dashboard", exchange -> handle(exchange, this::timeClockDashboard));
        server.createContext("/v1/time-clock/punch-state", exchange -> handle(exchange, this::timeClockPunchState));
        server.createContext("/v1/time-clock/punch", exchange -> handle(exchange, this::timeClockPunch));
        server.createContext("/v1/payroll/dashboard", exchange -> handle(exchange, this::payrollDashboard));
        server.createContext("/v1/payroll/bonus", exchange -> handle(exchange, this::payrollBonus));
        server.createContext("/v1/payroll/pay", exchange -> handle(exchange, this::payrollPay));
        server.createContext("/v1/employees/badge-data", exchange -> handle(exchange, this::employeeBadgeData));
        server.createContext("/v1/employees/badge-printed", exchange -> handle(exchange, this::employeeBadgePrinted));
        server.createContext("/v1/employees/admin/state", exchange -> handle(exchange, this::employeeAdminState));
        server.createContext("/v1/employees/admin/update", exchange -> handle(exchange, this::employeeAdminMutation));
        server.createContext("/v1/quotations/read", exchange -> handle(exchange, this::quotationRead));
        server.createContext("/v1/quotations/update", exchange -> handle(exchange, this::quotationMutation));
        server.createContext("/v1/documents/quotation-invoice", exchange -> handle(exchange, this::quotationDocument));
        server.createContext("/v1/notifications/list", exchange -> handle(exchange, this::notificationList));
        server.createContext("/v1/notifications/update", exchange -> handle(exchange, this::notificationUpdate));
        server.createContext("/v1/schedule/locations", exchange -> handle(exchange, this::scheduleLocations));
        server.createContext("/v1/schedule/employees", exchange -> handle(exchange, this::scheduleEmployees));
        server.createContext("/v1/schedule/shifts", exchange -> handle(exchange, this::scheduleShifts));
        server.createContext("/v1/schedule/range", exchange -> handle(exchange, this::scheduleRange));
        server.createContext("/v1/schedule/holidays", exchange -> handle(exchange, this::scheduleHolidays));
        server.createContext("/v1/schedule/snapshot", exchange -> handle(exchange, this::scheduleSnapshot));
        server.createContext("/v1/schedule/update", exchange -> handle(exchange, this::scheduleUpdate));
        server.createContext("/v1/schedule/auto-generate", exchange -> handle(exchange, this::autoScheduleGenerate));
        server.createContext("/v1/schedule/auto-apply", exchange -> handle(exchange, this::autoScheduleApply));
        server.createContext("/v1/security/roles/state", exchange -> handle(exchange, this::roleAdminState));
        server.createContext("/v1/security/roles/selected", exchange -> handle(exchange, this::roleAdminSelected));
        server.createContext("/v1/security/roles/save", exchange -> handle(exchange, this::roleAdminSave));
        server.createContext("/v1/security/roles/add", exchange -> handle(exchange, this::roleAdminAdd));
    }

    private ApiResult latestAppRelease(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        authenticateSession(context.exchange(), device, true);
        String platform = required(context.body(), "platform", 40).toLowerCase(java.util.Locale.ROOT);
        if (!platform.matches("[a-z0-9._-]+")) throw new ApiException(400, "VALIDATION_ERROR", "The update platform is invalid.", false);
        String query = "select=release_id,version,build_number,platform,artifact_bucket,artifact_path,sha256,file_size_bytes,release_notes,required,minimum_supported_version"
                + "&published=eq.true&platform=in.(" + cloudEncode(platform) + ",all)&order=build_number.desc&limit=1";
        HttpResponse<String> response = cloudRequest(HttpRequest.newBuilder()
                .uri(URI.create(SupabaseSessionManager.getSupabaseUrl() + "/rest/v1/app_releases?" + query))
                .timeout(Duration.ofSeconds(20)).header("Accept", "application/json").GET());
        com.google.gson.JsonArray rows = JsonParser.parseString(response.body()).getAsJsonArray();
        if (rows.isEmpty()) { Map<String,Object> empty=new LinkedHashMap<>();empty.put("release",null);return ApiResult.ok(empty); }
        JsonObject row = rows.get(0).getAsJsonObject();
        AppUpdateService.AppRelease release = new AppUpdateService.AppRelease(
                row.get("release_id").getAsLong(), row.get("version").getAsString(), row.get("build_number").getAsInt(),
                row.get("platform").getAsString(), row.get("artifact_bucket").getAsString(), row.get("artifact_path").getAsString(),
                row.get("sha256").getAsString(), row.get("file_size_bytes").getAsLong(),
                row.has("release_notes") && !row.get("release_notes").isJsonNull() ? row.get("release_notes").getAsString() : "",
                row.has("required") && row.get("required").getAsBoolean(),
                row.has("minimum_supported_version") && !row.get("minimum_supported_version").isJsonNull()
                        ? row.get("minimum_supported_version").getAsString() : "");
        return ApiResult.ok(Map.of("release", release));
    }

    private ApiResult signAppRelease(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        authenticateSession(context.exchange(), device, true);
        String bucket = required(context.body(), "bucket", 100);
        String path = required(context.body(), "path", 1000);
        if (R2UpdateUrlSigner.handles(bucket)) {
            if (!R2UpdateUrlSigner.R2_BUCKET_REFERENCE.equals(bucket)
                    || path.startsWith("/") || path.contains("..") || path.contains("\\")) {
                throw new ApiException(403, "UPDATE_ARTIFACT_DENIED",
                        "The requested update artifact is not allowed.", false);
            }
            requirePublishedUpdateArtifact(bucket, path);
            return ApiResult.ok(Map.of("url", R2UpdateUrlSigner.createDownloadUrl(bucket, path)));
        }
        if (!"smartstock-releases".equals(bucket) || path.startsWith("/")
                || path.contains("..") || path.contains("\\")) {
            throw new ApiException(403, "UPDATE_ARTIFACT_DENIED", "The requested update artifact is not allowed.", false);
        }
        requirePublishedUpdateArtifact(bucket, path);
        String encodedPath = java.util.Arrays.stream(path.split("/"))
                .filter(part -> !part.isBlank()).map(LanApiServer::cloudEncode)
                .collect(java.util.stream.Collectors.joining("/"));
        HttpResponse<String> response = cloudRequest(HttpRequest.newBuilder()
                .uri(URI.create(SupabaseSessionManager.getSupabaseUrl() + "/storage/v1/object/sign/"
                        + cloudEncode(bucket) + "/" + encodedPath))
                .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"expiresIn\":600}", StandardCharsets.UTF_8)));
        JsonObject body = JsonParser.parseString(response.body()).getAsJsonObject();
        String signed = body.has("signedURL") ? body.get("signedURL").getAsString() : body.get("signedUrl").getAsString();
        return ApiResult.ok(Map.of("url", AppUpdateService.resolveSignedDownloadUrl(SupabaseSessionManager.getSupabaseUrl(), signed)));
    }

    private static void requirePublishedUpdateArtifact(String bucket, String path) throws Exception {
        String query = "select=release_id"
                + "&published=eq.true"
                + "&artifact_bucket=eq." + cloudEncode(bucket)
                + "&artifact_path=eq." + cloudEncode(path)
                + "&limit=1";
        HttpResponse<String> response = cloudRequest(HttpRequest.newBuilder()
                .uri(URI.create(SupabaseSessionManager.getSupabaseUrl() + "/rest/v1/app_releases?" + query))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .GET());
        com.google.gson.JsonArray rows = JsonParser.parseString(response.body()).getAsJsonArray();
        if (rows.isEmpty()) {
            throw new ApiException(403, "UPDATE_ARTIFACT_DENIED",
                    "The requested update artifact is not published.", false);
        }
    }

    private ApiResult uploadCloudFile(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String bucket = required(context.body(), "bucket", 100);
        String path = required(context.body(), "path", 1000);
        String contentType = required(context.body(), "contentType", 200);
        boolean employeeFile = "employee files".equals(bucket)
                && (path.startsWith("employee photos/") || path.startsWith("ID cards/"));
        boolean productImage = "Product Images".equals(bucket) && path.startsWith("products/");
        if ((!employeeFile && !productImage) || path.startsWith("/") || path.contains("..")) {
            throw new ApiException(403, "CLOUD_FILE_DENIED", "The requested employee file location is not allowed.", false);
        }
        if (employeeFile) try (Connection connection = DB.getConnection()) {
            requireAnyPermission(connection, session.userId(), "EMPLOYEE_MANAGEMENT");
        }
        byte[] bytes;
        try { bytes = Base64.getDecoder().decode(required(context.body(), "bytesBase64", MAX_CLOUD_FILE_BODY_BYTES)); }
        catch (IllegalArgumentException ex) { throw new ApiException(400, "INVALID_FILE", "The uploaded employee file is invalid.", false); }
        String category = employeeFile ? "EMPLOYEE_PHOTO" : "PRODUCT";
        if (path.startsWith("ID cards/")) {
            String encodedPath = java.util.Arrays.stream(path.split("/"))
                    .filter(part -> !part.isBlank()).map(LanApiServer::cloudEncode)
                    .collect(java.util.stream.Collectors.joining("/"));
            cloudRequest(HttpRequest.newBuilder().uri(URI.create(SupabaseSessionManager.getSupabaseUrl()
                            + "/storage/v1/object/" + cloudEncode(bucket) + "/" + encodedPath))
                    .timeout(Duration.ofSeconds(60)).header("Content-Type", contentType).header("x-upsert", "true")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bytes)));
            String url = SupabaseSessionManager.getSupabaseUrl() + "/storage/v1/object/authenticated/"
                    + cloudEncode(bucket) + "/" + encodedPath;
            return ApiResult.ok(Map.of("url", url));
        }
        String reference;
        try (Connection connection = DB.getConnection()) {
            reference = ServerImageAssetService.storeUpload(connection, category, bucket, path, contentType,
                    path.substring(path.lastIndexOf('/') + 1),
                    productImage ? "PUBLIC" : "AUTHENTICATED", bytes);
            try {
                ServerImageAssetService.synchronize(connection);
            } catch (Exception ignored) {
                // Offline-first: the sync worker will retry without blocking the save.
            }
        }
        return ApiResult.ok(Map.of("url", reference, "reference", reference));
    }

    private ApiResult fetchImageAsset(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String reference = required(context.body(), "reference", 100);
        try (Connection connection = DB.getConnection()) {
            if (ServerImageAssetService.isEmployeePhoto(connection, reference)) {
                requireAnyPermission(connection, session.userId(), "EMPLOYEE_MANAGEMENT");
            }
        }
        ServerImageAssetService.AssetBytes asset = ServerImageAssetService.load(reference);
        return ApiResult.ok(Map.of(
                "bytesBase64", Base64.getEncoder().encodeToString(asset.bytes()),
                "contentType", asset.contentType(),
                "sha256", asset.sha256()));
    }

    private ApiResult listImageAssets(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            requireAnyPermission(connection, session.userId(), "COMPANY_PREFERENCES", "COMPANY_CUSTOMIZATION");
            return ApiResult.ok(Map.of("assets", ServerImageAssetService.list(connection),
                    "counts", ServerImageAssetService.counts(connection)));
        }
    }

    private ApiResult reconcileImageAssets(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            requireAnyPermission(connection, session.userId(), "COMPANY_PREFERENCES", "COMPANY_CUSTOMIZATION");
            ServerImageAssetService.SyncResult result = ServerImageAssetService.synchronize(connection);
            return ApiResult.ok(Map.of("result", result));
        }
    }

    private ApiResult retainImageAsset(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        UUID assetId = UUID.fromString(required(context.body(), "assetId", 80));
        try (Connection connection = DB.getConnection()) {
            requireAnyPermission(connection, session.userId(), "COMPANY_PREFERENCES", "COMPANY_CUSTOMIZATION");
            ServerImageAssetService.retain(connection, assetId);
            return ApiResult.ok(Map.of("updated", true));
        }
    }

    private ApiResult purgeImageAsset(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        UUID assetId = UUID.fromString(required(context.body(), "assetId", 80));
        try (Connection connection = DB.getConnection()) {
            requireAnyPermission(connection, session.userId(), "COMPANY_PREFERENCES", "COMPANY_CUSTOMIZATION");
            AuthenticatedUser actor = loadUser(connection, session.userId(), session.locationId());
            ServerImageAssetService.purge(connection, assetId, session.userId(), displayName(actor));
            return ApiResult.ok(Map.of("deleted", true));
        }
    }

    private ApiResult downloadCloudFile(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            requireAnyPermission(connection, session.userId(), "EMPLOYEE_MANAGEMENT");
        }
        String url = required(context.body(), "url", 4000);
        String prefix = SupabaseSessionManager.getSupabaseUrl() + "/storage/v1/object/authenticated/" + cloudEncode("employee files") + "/";
        if (!url.startsWith(prefix) || url.contains("..")) {
            throw new ApiException(403, "CLOUD_FILE_DENIED", "The requested employee file is not allowed.", false);
        }
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create(url))
                        .timeout(Duration.ofSeconds(60)).GET();
        HttpResponse<byte[]> response = CLOUD_HTTP.send(
                ServerSupabaseCredentials.applyTo(request).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(502, "CLOUD_REQUEST_FAILED", "The SmartStock Server could not download the employee file.", response.statusCode() >= 500);
        }
        if (response.body().length > 25L * 1024L * 1024L) throw new ApiException(413, "FILE_TOO_LARGE", "The employee file is too large.", false);
        return ApiResult.ok(Map.of("bytesBase64", Base64.getEncoder().encodeToString(response.body())));
    }

    private static HttpResponse<String> cloudRequest(HttpRequest.Builder builder) throws Exception {
        HttpResponse<String> response = CLOUD_HTTP.send(
                ServerSupabaseCredentials.applyTo(builder).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(502, "CLOUD_REQUEST_FAILED", "The SmartStock Server cloud request failed with HTTP " + response.statusCode() + ".", response.statusCode() >= 500);
        }
        return response;
    }

    private static String cloudEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void health(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            send(exchange, 405, failure("METHOD_NOT_ALLOWED", "Use GET for this endpoint.", false));
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", "SmartStock LAN Service");
        data.put("status", "ok");
        data.put("apiVersion", "v1");
        try (Connection connection = DB.getConnection()) {
            SchemaContractService.Readiness local =
                    SchemaContractService.validateLocal(connection);
            data.put("localSchemaVersion", local.version());
            data.put("localSchemaReady", local.ready());
        } catch (Exception ex) {
            data.put("localSchemaVersion", null);
            data.put("localSchemaReady", false);
        }
        CloudSyncManifest.SchemaReadiness cloud =
                CloudSyncManifest.latestSchemaReadiness();
        data.put("cloudSchemaVersion", cloud.version());
        data.put("cloudSchemaReady", cloud.ready());
        data.put("certificateFingerprint", tlsIdentity.fingerprint());
        List<String> pairingProofs = tlsIdentity.pairingProofs();
        data.put("pairingProof", pairingProofs.get(0));
        data.put("previousPairingProof", pairingProofs.get(1));
        send(exchange, 200, success(data));
    }

    private ApiResult enroll(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        JsonObject body = context.body();
        requirePairingPhrase(body);
        String installationId = required(body, "installationId", 160);
        String publicKey = required(body, "publicKey", 8192);
        String fingerprint = optional(body, "deviceFingerprint", 512);
        String deviceName = optional(body, "deviceName", 200);
        String hostname = optional(body, "hostname", 255);
        String appVersion = optional(body, "appVersion", 100);
        String accessMode = "REMOTE_ADMIN".equals(optional(body, "accessMode", 30))
                ? "REMOTE_ADMIN" : "CLIENT";
        String pairingChallenge = LanSecurity.randomToken();
        String pairingChallengeEnvelope = encryptForDevice(publicKey, pairingChallenge);

        try (Connection connection = DB.getConnection()) {
            UUID deviceId;
            boolean approved;
            boolean blocked;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO devices (
                        installation_id, device_fingerprint, device_name, hostname,
                        app_version, pairing_public_key, api_pairing_challenge_hash,
                        api_pairing_challenge_expires_at, first_seen, last_seen,
                        is_approved, is_blocked, credential_status, access_mode
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '24 hours',
                              CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE, FALSE, 'PENDING', ?)
                    ON CONFLICT (installation_id) DO UPDATE SET
                        device_fingerprint = EXCLUDED.device_fingerprint,
                        device_name = COALESCE(NULLIF(devices.device_name, ''), EXCLUDED.device_name),
                        hostname = EXCLUDED.hostname,
                        app_version = EXCLUDED.app_version,
                        access_mode = CASE WHEN devices.is_approved THEN devices.access_mode ELSE EXCLUDED.access_mode END,
                        last_seen = CURRENT_TIMESTAMP,
                        api_pairing_challenge_hash = EXCLUDED.api_pairing_challenge_hash,
                        api_pairing_challenge_expires_at = EXCLUDED.api_pairing_challenge_expires_at,
                        pairing_public_key = CASE
                            WHEN devices.is_approved AND devices.pairing_public_key IS NOT NULL
                                THEN devices.pairing_public_key
                            ELSE EXCLUDED.pairing_public_key
                        END
                    RETURNING device_id, is_approved, is_blocked
                    """)) {
                statement.setString(1, installationId);
                statement.setString(2, fingerprint);
                statement.setString(3, deviceName);
                statement.setString(4, hostname);
                statement.setString(5, appVersion);
                statement.setString(6, publicKey);
                statement.setString(7, LanSecurity.sha256(pairingChallenge));
                statement.setString(8, accessMode);
                try (ResultSet rs = statement.executeQuery()) {
                    rs.next();
                    deviceId = (UUID) rs.getObject("device_id");
                    approved = rs.getBoolean("is_approved");
                    blocked = rs.getBoolean("is_blocked");
                }
            }
            auditSecurity(connection, "LAN_DEVICE_ENROLLMENT", deviceId, null,
                    approved ? "Existing approved device requested LAN enrollment" : "Device is pending administrator approval");
            if (blocked) throw new ApiException(403, "DEVICE_REVOKED", "This device has been revoked.", false);
            return ApiResult.ok(Map.of(
                    "deviceId", deviceId.toString(),
                    "status", approved ? "APPROVED" : "PENDING_APPROVAL",
                    "pairingChallengeEnvelope", pairingChallengeEnvelope,
                    "employeeActionRequired", false));
        }
    }

    private ApiResult claim(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        JsonObject body = context.body();
        String installationId = required(body, "installationId", 160);
        String publicKeyText = required(body, "publicKey", 8192);
        String pairingChallenge = required(body, "pairingChallenge", 256);

        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                UUID deviceId;
                boolean approved;
                boolean blocked;
                String registeredKey;
                String challengeHash;
                Timestamp challengeExpiresAt;
                try (PreparedStatement ps = connection.prepareStatement("""
                        SELECT device_id, is_approved, is_blocked, pairing_public_key,
                               api_pairing_challenge_hash, api_pairing_challenge_expires_at
                        FROM devices WHERE installation_id = ? FOR UPDATE
                        """)) {
                    ps.setString(1, installationId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new ApiException(404, "DEVICE_NOT_ENROLLED", "This device has not been enrolled.", false);
                        deviceId = (UUID) rs.getObject(1);
                        approved = rs.getBoolean(2);
                        blocked = rs.getBoolean(3);
                        registeredKey = rs.getString(4);
                        challengeHash = rs.getString(5);
                        challengeExpiresAt = rs.getTimestamp(6);
                    }
                }
                if (blocked) throw new ApiException(403, "DEVICE_REVOKED", "This device has been revoked.", false);
                if (!approved) throw new ApiException(409, "PAIRING_PENDING", "An administrator must approve this register once.", true);
                if (!LanSecurity.constantTimeEquals(publicKeyText, registeredKey)) {
                    throw new ApiException(409, "DEVICE_IDENTITY_MISMATCH", "The device identity changed; administrator recovery is required.", false);
                }
                if (challengeExpiresAt == null || challengeExpiresAt.toInstant().isBefore(Instant.now())
                        || !LanSecurity.constantTimeEquals(LanSecurity.sha256(pairingChallenge), challengeHash)) {
                    throw new ApiException(403, "PAIRING_CHALLENGE_INVALID", "The one-time pairing approval expired; an administrator must restart setup.", false);
                }

                String token = LanSecurity.randomToken();
                String tokenHash = LanSecurity.sha256(token);
                String envelope = encryptForDevice(publicKeyText, token);
                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE devices SET
                            api_previous_credential_hash = api_credential_hash,
                            api_previous_expires_at = LEAST(
                                COALESCE(api_credential_expires_at, CURRENT_TIMESTAMP),
                                CURRENT_TIMESTAMP + INTERVAL '14 days'),
                            api_credential_hash = ?,
                            api_credential_issued_at = CURRENT_TIMESTAMP,
                            api_credential_expires_at = CURRENT_TIMESTAMP + INTERVAL '90 days',
                            api_server_fingerprint = ?,
                            api_pairing_challenge_hash = NULL,
                            api_pairing_challenge_expires_at = NULL,
                            credential_status = 'ISSUED', credential_issued_at = CURRENT_TIMESTAMP
                        WHERE device_id = ?
                        """)) {
                    ps.setString(1, tokenHash);
                    ps.setString(2, tlsIdentity.fingerprint());
                    ps.setObject(3, deviceId);
                    ps.executeUpdate();
                }
                auditSecurity(connection, "LAN_API_CREDENTIAL_ISSUED", deviceId, null,
                        "Issued a 90-day device API credential with a 14-day rotation overlap");
                connection.commit();
                return ApiResult.ok(Map.of(
                        "deviceId", deviceId.toString(),
                        "credentialEnvelope", envelope,
                        "certificateFingerprint", tlsIdentity.fingerprint(),
                        "expiresAt", Instant.now().plus(Duration.ofDays(90)).toString()));
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private ApiResult login(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        JsonObject body = context.body();
        int locationId = requiredInt(body, "locationId");
        String accessToken = optional(body, "supabaseAccessToken", 16384);
        String identifier = optional(body, "identifier", 512);
        String secret = optional(body, "secret", 2048);
        if (secret == null) secret = "";

        AuthenticatedUser user;
        String source;
        SupabasePasswordResult supabaseTokens = null;
        try (Connection connection = DB.getConnection()) {
            if (!device.remoteAdmin() && device.locationId() != null && device.locationId() != locationId) {
                throw new ApiException(403, "DEVICE_STORE_MISMATCH", "This register is assigned to a different store.", false);
            }
            if (accessToken != null) {
                user = authenticateSupabase(connection, accessToken, locationId);
                source = "SUPABASE";
            } else {
                if (identifier == null) {
                    throw new ApiException(400, "LOGIN_REQUIRED", "Login credentials are required.", false);
                }
                LoginSecurityService.requireAllowed(connection, identifier);
                ResolvedLoginUser resolved = resolveLoginUser(connection, identifier, locationId);
                if (resolved == null) {
                    LoginSecurityService.recordFailure(connection, device.deviceId(), identifier, "Unknown LAN API login identifier");
                    throw new ApiException(401, "LOGIN_FAILED", "The login was not accepted.", false);
                }
                boolean badgeLogin = BadgeCredentialService.normalizeBadge(identifier)
                        .equals(BadgeCredentialService.normalizeBadge(resolved.badgeId()));
                char[] enteredSecret = secret.toCharArray();
                try {
                    if (badgeLogin) {
                        boolean pinRequired = ServerCompanyCustomizationRepository.isBadgePinRequired(connection, locationId);
                        if (pinRequired && !LocalAuthCacheService.verifyEmployeePin(connection, resolved.userId(), enteredSecret)) {
                            LoginSecurityService.recordFailure(connection, device.deviceId(), identifier, "Badge PIN failed");
                            throw new ApiException(401, "LOGIN_FAILED", "The login was not accepted.", false);
                        }
                        source = pinRequired ? "BADGE_PIN" : "BADGE_ONLY";
                        if (!pinRequired) {
                            auditSecurity(connection, "BADGE_ONLY_LOGIN", device.deviceId(), resolved.userId(),
                                    "Badge-only login accepted by company preference");
                        }
                    } else {
                        supabaseTokens = signInSupabasePassword(resolved.email(), secret);
                        if (supabaseTokens.status() == SupabasePasswordStatus.SUCCESS) {
                            LocalAuthCacheService.savePasswordVerifier(connection, resolved.cachedUser(), enteredSecret);
                            source = "SUPABASE_PASSWORD";
                        } else if (LocalAuthCacheService.verifyEmployeePin(connection, resolved.userId(), enteredSecret)) {
                            source = "EMPLOYEE_PIN";
                            supabaseTokens = null;
                        } else if (supabaseTokens.status() == SupabasePasswordStatus.UNAVAILABLE) {
                            LocalAuthCacheService.CachedUser cached = LocalAuthCacheService.verify(
                                    connection, identifier, enteredSecret, locationId);
                            if (cached == null) {
                                LoginSecurityService.recordFailure(connection, device.deviceId(), identifier, "Offline password verification failed");
                                throw new ApiException(401, "OFFLINE_LOGIN_UNAVAILABLE",
                                        "This login is not available offline yet. Connect to the internet once and sign in normally.", false);
                            }
                            source = "LOCAL_PASSWORD_CACHE";
                            supabaseTokens = null;
                        } else {
                            LoginSecurityService.recordFailure(connection, device.deviceId(), identifier, "Password or employee PIN failed");
                            throw new ApiException(401, "LOGIN_FAILED", "The login was not accepted.", false);
                        }
                    }
                } finally {
                    java.util.Arrays.fill(enteredSecret, '\0');
                }
                LoginSecurityService.recordSuccess(connection, identifier);
                user = resolved.authenticatedUser();
            }
            String sessionToken = issueSession(connection, device, user, source);
            List<String> permissions = loadPermissions(connection, user.userId());
            return ApiResult.ok(sessionResponse(sessionToken, user, permissions, device.deviceId(), supabaseTokens,
                    deviceSessionPolicy(connection, device.deviceId())));
        }
    }

    private ApiResult badgeStatus(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        int locationId = requiredInt(context.body(), "locationId");
        String badgeId = required(context.body(), "badgeId", 512);
        if (device.locationId() != null && device.locationId() != locationId) {
            throw new ApiException(403, "DEVICE_STORE_MISMATCH", "This register is assigned to a different store.", false);
        }
        try (Connection connection = DB.getConnection()) {
            ResolvedLoginUser user = resolveLoginUser(connection, badgeId, locationId);
            if (user == null || !BadgeCredentialService.normalizeBadge(badgeId)
                    .equals(BadgeCredentialService.normalizeBadge(user.badgeId()))) {
                throw new ApiException(404, "BADGE_NOT_FOUND", "This employee badge is not active at this store.", false);
            }
            return ApiResult.ok(Map.of(
                    "pinConfigured", LocalAuthCacheService.hasEmployeePin(connection, user.userId()),
                    "pinRequired", ServerCompanyCustomizationRepository.isBadgePinRequired(connection, locationId)));
        }
    }

    private ApiResult sessionStores(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        if (!device.remoteAdmin()) {
            throw new ApiException(403, "REMOTE_ADMIN_REQUIRED", "Store switching is available only on an enrolled Remote Admin device.", false);
        }
        List<Map<String,Object>> stores = new ArrayList<>();
        try (Connection connection = DB.getConnection(); PreparedStatement ps = connection.prepareStatement("""
                SELECT l.location_id, l.name, COALESCE(l.timezone, ''),
                       CASE WHEN ss.last_seen_at >= CURRENT_TIMESTAMP - INTERVAL '3 minutes' THEN 'Online'
                            WHEN ss.last_seen_at IS NULL THEN 'Never synchronized' ELSE 'Offline' END,
                       ss.last_success_at
                FROM user_locations ul
                JOIN locations l ON l.location_id = ul.location_id
                LEFT JOIN store_sync_status ss ON ss.location_id = l.location_id
                WHERE ul.user_id = ? ORDER BY l.name
                """)) {
            ps.setInt(1, session.userId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> row = new LinkedHashMap<>();
                    row.put("locationId", rs.getInt(1)); row.put("name", rs.getString(2));
                    row.put("timezone", rs.getString(3)); row.put("status", rs.getString(4));
                    Timestamp seen = rs.getTimestamp(5); row.put("lastSyncEpochMillis", seen == null ? 0L : seen.getTime());
                    stores.add(row);
                }
            }
        }
        return ApiResult.ok(Map.of("stores", stores, "currentLocationId", session.locationId()));
    }

    private ApiResult switchSessionStore(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, false);
        if (!device.remoteAdmin()) {
            throw new ApiException(403, "REMOTE_ADMIN_REQUIRED", "Store switching is available only on an enrolled Remote Admin device.", false);
        }
        int locationId = requiredInt(context.body(), "locationId");
        try (Connection connection = DB.getConnection()) {
            AuthenticatedUser user = loadUser(connection, session.userId(), locationId);
            String token = issueSession(connection, device, user, "REMOTE_STORE_SWITCH");
            List<String> permissions = loadPermissions(connection, user.userId());
            auditSecurity(connection, "REMOTE_ADMIN_STORE_SWITCHED", device.deviceId(), user.userId(),
                    "Remote Admin session switched to location " + locationId);
            return ApiResult.ok(sessionResponse(token, user, permissions, device.deviceId(), null,
                    deviceSessionPolicy(connection, device.deviceId())));
        }
    }

    private ApiResult remoteCommands(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        if (!device.remoteAdmin()) throw new ApiException(403, "REMOTE_ADMIN_REQUIRED", "Remote command status requires Remote Admin.", false);
        List<Map<String,Object>> commands = new ArrayList<>();
        try (Connection c = DB.getConnection(); PreparedStatement ps = c.prepareStatement("""
                SELECT command_id,operation,status,details,created_at,applied_at
                FROM remote_admin_commands WHERE location_id=? ORDER BY created_at DESC LIMIT 100
                """)) {
            ps.setInt(1, session.locationId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String,Object> row = new LinkedHashMap<>();
                    row.put("commandId", rs.getObject(1).toString()); row.put("operation", rs.getString(2));
                    row.put("status", rs.getString(3)); row.put("details", rs.getString(4));
                    row.put("createdAtEpochMillis", rs.getTimestamp(5).getTime());
                    Timestamp applied = rs.getTimestamp(6); row.put("appliedAtEpochMillis", applied == null ? 0L : applied.getTime());
                    commands.add(row);
                }
            }
        }
        return ApiResult.ok(Map.of("commands", commands));
    }

    private ApiResult badgePinSetup(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        JsonObject body = context.body();
        int locationId = requiredInt(body, "locationId");
        String badgeId = required(body, "badgeId", 512);
        String accountPassword = required(body, "accountPassword", 2048);
        String pinText = required(body, "pin", 16);
        char[] pin = pinText.toCharArray();
        if (device.locationId() != null && device.locationId() != locationId) {
            java.util.Arrays.fill(pin, '\0');
            throw new ApiException(403, "DEVICE_STORE_MISMATCH", "This register is assigned to a different store.", false);
        }
        if (!EmployeePinService.validPin(pin)) {
            java.util.Arrays.fill(pin, '\0');
            throw new ApiException(400, "PIN_INVALID", "Use exactly 4–8 digits for the employee PIN.", false);
        }
        try (Connection connection = DB.getConnection()) {
            LoginSecurityService.requireAllowed(connection, badgeId);
            ResolvedLoginUser user = resolveLoginUser(connection, badgeId, locationId);
            if (user == null || !BadgeCredentialService.normalizeBadge(badgeId)
                    .equals(BadgeCredentialService.normalizeBadge(user.badgeId()))) {
                LoginSecurityService.recordFailure(connection, device.deviceId(), badgeId, "Unknown badge PIN setup identifier");
                throw new ApiException(401, "BADGE_SETUP_FAILED", "Badge PIN setup was not accepted.", false);
            }
            if (LocalAuthCacheService.hasEmployeePin(connection, user.userId())) {
                throw new ApiException(409, "PIN_ALREADY_CONFIGURED", "This employee already has a badge PIN.", false);
            }
            SupabasePasswordResult passwordResult = signInSupabasePassword(user.email(), accountPassword);
            if (passwordResult.status() != SupabasePasswordStatus.SUCCESS) {
                LoginSecurityService.recordFailure(connection, device.deviceId(), badgeId, "Badge PIN setup password failed");
                throw new ApiException(401, "BADGE_SETUP_FAILED",
                        passwordResult.status() == SupabasePasswordStatus.UNAVAILABLE
                                ? "Internet access is required for first-time badge PIN setup."
                                : "Badge PIN setup was not accepted.", false);
            }
            LocalAuthCacheService.saveEmployeePin(connection, user.cachedUser(), pin);
            LoginSecurityService.recordSuccess(connection, badgeId);
            String sessionToken = issueSession(connection, device, user.authenticatedUser(), "BADGE_PIN_SETUP");
            List<String> permissions = loadPermissions(connection, user.userId());
            auditSecurity(connection, "EMPLOYEE_BADGE_PIN_CREATED", device.deviceId(), user.userId(),
                    "Employee created a PIN after first badge tap");
            return ApiResult.ok(sessionResponse(sessionToken, user.authenticatedUser(), permissions,
                    device.deviceId(), passwordResult, deviceSessionPolicy(connection, device.deviceId())));
        } finally {
            java.util.Arrays.fill(pin, '\0');
        }
    }

    private ApiResult localServerClaim(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        if (DatabaseConfig.load().mode() != DatabaseMode.SERVER
                || context.exchange().getRemoteAddress() == null
                || !context.exchange().getRemoteAddress().getAddress().isLoopbackAddress()) {
            throw new ApiException(403,"LOCAL_ACCESS_REQUIRED",
                    "Local server recovery is available only from the physical SmartStock server.",false);
        }
        JsonObject body=context.body();
        String installationId=required(body,"installationId",160);
        String publicKey=required(body,"publicKey",8192);
        String token=LanSecurity.randomToken();
        DatabaseConfig config=DatabaseConfig.load();
        try(Connection connection=DB.getConnection()) {
            UUID deviceId;
            try(PreparedStatement ps=connection.prepareStatement("""
                    INSERT INTO devices (installation_id,device_fingerprint,device_name,hostname,app_version,access_mode,
                      pairing_public_key,first_seen,last_seen,last_store_id,is_approved,is_blocked,approved_at,
                      credential_status,api_credential_hash,api_credential_issued_at,api_credential_expires_at,
                      api_server_fingerprint)
                    VALUES (?,?,?,?,?,'SERVER',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,TRUE,FALSE,CURRENT_TIMESTAMP,
                      'CLAIMED',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP+INTERVAL '90 days',?)
                    ON CONFLICT (installation_id) DO UPDATE SET
                      device_fingerprint=EXCLUDED.device_fingerprint,device_name=EXCLUDED.device_name,
                      hostname=EXCLUDED.hostname,app_version=EXCLUDED.app_version,pairing_public_key=EXCLUDED.pairing_public_key,
                      access_mode='SERVER',
                      last_seen=CURRENT_TIMESTAMP,last_store_id=EXCLUDED.last_store_id,is_approved=TRUE,is_blocked=FALSE,
                      approved_at=COALESCE(devices.approved_at,CURRENT_TIMESTAMP),credential_status='CLAIMED',
                      api_credential_hash=EXCLUDED.api_credential_hash,api_credential_issued_at=CURRENT_TIMESTAMP,
                      api_credential_expires_at=EXCLUDED.api_credential_expires_at,
                      api_server_fingerprint=EXCLUDED.api_server_fingerprint
                    RETURNING device_id
                    """)) {
                ps.setString(1,installationId); ps.setString(2,optional(body,"deviceFingerprint",512));
                ps.setString(3,optional(body,"deviceName",200)); ps.setString(4,optional(body,"hostname",255));
                ps.setString(5,optional(body,"appVersion",100)); ps.setString(6,publicKey);
                if(config.locationId()==null) ps.setNull(7,java.sql.Types.INTEGER); else ps.setInt(7,config.locationId());
                ps.setString(8,LanSecurity.sha256(token)); ps.setString(9,tlsIdentity.fingerprint());
                try(ResultSet rs=ps.executeQuery()){rs.next();deviceId=(UUID)rs.getObject(1);}
            }
            auditSecurity(connection,"LOCAL_SERVER_DEVICE_CLAIMED",deviceId,null,
                    "Physical-server loopback recovery issued a server UI credential");
            return ApiResult.ok(Map.of("deviceId",deviceId.toString(),"credentialEnvelope",encryptForDevice(publicKey,token),
                    "certificateFingerprint",tlsIdentity.fingerprint(),"expiresAt",Instant.now().plus(Duration.ofDays(90)).toString()));
        }
    }

    private ApiResult rotate(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String publicKey;
                Timestamp issuedAt;
                String status;
                try (PreparedStatement ps = connection.prepareStatement("""
                        SELECT pairing_public_key, api_credential_issued_at, credential_status
                        FROM devices WHERE device_id = ? FOR UPDATE
                        """)) {
                    ps.setObject(1, device.deviceId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new ApiException(404, "DEVICE_NOT_FOUND", "This register is no longer enrolled.", false);
                        publicKey = rs.getString(1);
                        issuedAt = rs.getTimestamp(2);
                        status = rs.getString(3);
                    }
                }
                boolean due = issuedAt == null || issuedAt.toInstant().plus(Duration.ofDays(60)).isBefore(Instant.now());
                if (!due && !"ROTATION_PENDING".equals(status)) {
                    throw new ApiException(409, "ROTATION_NOT_DUE", "The device credential does not need renewal yet.", false);
                }
                String token = LanSecurity.randomToken();
                String envelope = encryptForDevice(publicKey, token);
                try (PreparedStatement ps = connection.prepareStatement("""
                        UPDATE devices SET
                            api_previous_credential_hash = api_credential_hash,
                            api_previous_expires_at = CURRENT_TIMESTAMP + INTERVAL '14 days',
                            api_credential_hash = ?, api_credential_issued_at = CURRENT_TIMESTAMP,
                            api_credential_expires_at = CURRENT_TIMESTAMP + INTERVAL '90 days',
                            credential_status = 'CLAIMED', credential_claimed_at = CURRENT_TIMESTAMP
                        WHERE device_id = ?
                        """)) {
                    ps.setString(1, LanSecurity.sha256(token));
                    ps.setObject(2, device.deviceId());
                    ps.executeUpdate();
                }
                auditSecurity(connection, "LAN_API_CREDENTIAL_ROTATED", device.deviceId(), null,
                        "Rotated the device API credential with a 14-day overlap");
                connection.commit();
                return ApiResult.ok(Map.of(
                        "credentialEnvelope", envelope,
                        "expiresAt", Instant.now().plus(Duration.ofDays(90)).toString()));
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private ApiResult refresh(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            List<String> permissions = loadPermissions(connection, session.userId());
            AuthenticatedUser user = loadUser(connection, session.userId(), session.locationId());
            return ApiResult.ok(sessionResponse(session.plainToken(), user, permissions, device.deviceId(), null,
                    deviceSessionPolicy(connection, device.deviceId())));
        }
    }

    private ApiResult logout(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, false);
        try (Connection connection = DB.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     UPDATE lan_api_sessions SET revoked_at = CURRENT_TIMESTAMP
                     WHERE session_id = ? AND device_id = ?
                     """)) {
            ps.setObject(1, session.sessionId());
            ps.setObject(2, device.deviceId());
            ps.executeUpdate();
            try (PreparedStatement legacy = connection.prepareStatement("""
                    UPDATE device_sessions SET logout_time = CURRENT_TIMESTAMP, session_status = 'ENDED'
                    WHERE device_id = ? AND user_id = ? AND session_status = 'ACTIVE' AND logout_time IS NULL
                    """)) {
                legacy.setObject(1, device.deviceId());
                legacy.setInt(2, session.userId());
                legacy.executeUpdate();
            }
        }
        return ApiResult.ok(Map.of("loggedOut", true));
    }

    private ApiResult sessionPolicy(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            DeviceSessionPolicy policy = deviceSessionPolicy(connection, device.deviceId());
            return ApiResult.ok(Map.of(
                    "persistentLoginAllowed", policy.persistentLoginAllowed(),
                    "autoLogoutEnabled", policy.autoLogoutEnabled(),
                    "autoLogoutMinutes", policy.autoLogoutMinutes()
            ));
        }
    }

    private ApiResult permissions(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "GET");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            return ApiResult.ok(Map.of("permissions", loadPermissions(connection, session.userId())));
        }
    }

    private ApiResult approve(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal requester = authenticateSession(context.exchange(), device, true);
        JsonObject body = context.body();
        String managerIdentifier = required(body, "managerIdentifier", 512);
        String password = optional(body, "password", 2048);
        if (password == null) password = "";
        String permissionKey = required(body, "permissionKey", 160).toUpperCase();
        String actionKey = required(body, "actionKey", 200);
        String resourceHash = required(body, "resourceHash", 128);

        try (Connection connection = DB.getConnection()) {
            int managerUserId;
            String managerEmail;
            String managerName;
            String managerBadgeId;
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT u.user_id, u.email, COALESCE(NULLIF(TRIM(u.full_name), ''), u.username, 'Manager'),
                           COALESCE(u.badge_id, '')
                FROM users u
                JOIN user_locations ul ON ul.user_id = u.user_id AND ul.location_id = ?
                WHERE u.is_active = TRUE
                      AND (LOWER(u.username) = LOWER(?) OR LOWER(u.email) = LOWER(?)
                        OR UPPER(REGEXP_REPLACE(COALESCE(u.badge_id, ''), '[^a-zA-Z0-9]', '', 'g'))
                           = UPPER(REGEXP_REPLACE(?, '[^a-zA-Z0-9]', '', 'g')))
                    """)) {
                ps.setInt(1, requester.locationId());
                ps.setString(2, managerIdentifier);
                ps.setString(3, managerIdentifier);
                ps.setString(4, managerIdentifier);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new ApiException(401, "MANAGER_LOGIN_FAILED", "Manager confirmation was not accepted.", false);
                    managerUserId = rs.getInt(1);
                    managerEmail = rs.getString(2);
                    managerName = rs.getString(3);
                    managerBadgeId = rs.getString(4);
                }
            }
            LoginSecurityService.requireAllowed(connection, managerIdentifier);
            boolean badgeLogin = BadgeCredentialService.normalizeBadge(managerIdentifier)
                    .equals(BadgeCredentialService.normalizeBadge(managerBadgeId))
                    && BadgeCredentialService.looksLikeGeneratedBadge(
                            BadgeCredentialService.normalizeBadge(managerIdentifier));
            char[] managerSecret = password.toCharArray();
            boolean managerAccepted;
            boolean managerBadgePinRequired = !badgeLogin
                    || ServerCompanyCustomizationRepository.isBadgePinRequired(
                            connection, requester.locationId());
            try {
                if (badgeLogin) {
                    managerAccepted = !managerBadgePinRequired
                            || LocalAuthCacheService.verifyEmployeePin(connection, managerUserId, managerSecret);
                } else {
                    SupabasePasswordResult managerAuth = signInSupabasePassword(managerEmail, password);
                    managerAccepted = managerAuth.status() == SupabasePasswordStatus.SUCCESS;
                    if (!managerAccepted && managerAuth.status() == SupabasePasswordStatus.UNAVAILABLE) {
                        LocalAuthCacheService.CachedUser cachedManager = LocalAuthCacheService.verify(
                                connection, managerIdentifier, managerSecret, requester.locationId());
                        managerAccepted = cachedManager != null && cachedManager.userId() == managerUserId;
                    }
                }
            } finally {
                java.util.Arrays.fill(managerSecret, '\0');
            }
            if (!managerAccepted) {
                LoginSecurityService.recordFailure(connection, device.deviceId(), managerIdentifier, "LAN API manager confirmation failed");
                throw new ApiException(401, "MANAGER_LOGIN_FAILED", "Manager confirmation was not accepted.", false);
            }
            LoginSecurityService.recordSuccess(connection, managerIdentifier);
            if (badgeLogin && !managerBadgePinRequired) {
                auditSecurity(connection, "BADGE_ONLY_MANAGER_APPROVAL", device.deviceId(), managerUserId,
                        "Badge-only manager approval accepted by company preference");
            }
            if (!hasPermission(connection, managerUserId, permissionKey)) {
                throw new ApiException(403, "MANAGER_PERMISSION_DENIED", "That manager does not have permission for this action.", false);
            }
            String approval = LanSecurity.randomToken();
            try (PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO lan_api_approvals (
                        approval_hash, device_id, requester_user_id, approver_user_id,
                        location_id, permission_key, action_key, resource_hash, expires_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP + INTERVAL '5 minutes')
                    """)) {
                ps.setString(1, LanSecurity.sha256(approval));
                ps.setObject(2, device.deviceId());
                ps.setInt(3, requester.userId());
                ps.setInt(4, managerUserId);
                ps.setInt(5, requester.locationId());
                ps.setString(6, permissionKey);
                ps.setString(7, actionKey);
                ps.setString(8, resourceHash);
                ps.executeUpdate();
            }
            auditSecurity(connection, "LAN_MANAGER_APPROVAL_ISSUED", device.deviceId(), managerUserId,
                    actionKey + " approved for requester " + requester.userId());
            return ApiResult.ok(Map.of(
                    "approvalToken", approval,
                    "expiresAt", Instant.now().plus(Duration.ofMinutes(5)).toString(),
                    "approverUserId", managerUserId,
                    "approverName", managerName));
        }
    }

    private ApiResult searchCatalog(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String searchText = optional(context.body(), "query", 300);
        if (searchText == null) searchText = "";
        try (Connection connection = DB.getConnection()) {
            requireAnyPermission(connection, session.userId(), "MAKE_SALE", "VIEW_INVENTORY",
                    "RECEIVING_INVENTORY", "EDIT_ITEM");
            String sql = """
                    SELECT p.product_id, p.name, COALESCE(p.size, '') AS size,
                           COALESCE(p.description, '') AS description, COALESCE(p.sku, '') AS sku,
                           p.price, COALESCE(p.product_type, 'INVENTORY') AS product_type,
                           p.category_id, COALESCE(i.quantity_on_hand, 0) AS quantity_on_hand,
                           %s AS searchable_text
                    FROM products p
                    LEFT JOIN inventory i ON i.product_id = p.product_id AND i.location_id = ?
                    WHERE %s
                    ORDER BY p.name
                    LIMIT 250
                    """.formatted(ProductSearchHelper.searchableTextExpression("p", session.locationId()),
                    ProductSearchHelper.predicate("p", session.locationId(), searchText));
            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setInt(1, session.locationId());
                ProductSearchHelper.bindTokens(ps, 2, searchText);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("productId", rs.getInt("product_id"));
                        row.put("name", rs.getString("name"));
                        row.put("size", rs.getString("size"));
                        row.put("description", rs.getString("description"));
                        row.put("sku", rs.getString("sku"));
                        row.put("price", rs.getBigDecimal("price"));
                        row.put("productType", rs.getString("product_type"));
                        row.put("categoryId", rs.getObject("category_id"));
                        row.put("quantityOnHand", rs.getInt("quantity_on_hand"));
                        row.put("searchableText", rs.getString("searchable_text"));
                        rows.add(row);
                    }
                }
            }
            return ApiResult.ok(Map.of("products", rows));
        }
    }

    private ApiResult customerAccounts(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            requireAnyPermission(connection, session.userId(), "MAKE_SALE", "CUSTOMER_ACCOUNTS");
            CustomerAccountLedgerService.repairAllBalances(connection);
            List<Map<String, Object>> rows = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT ca.customer_id, ca.account_number, ca.name AS customer_name,
                           ca.credit_limit, ca.current_balance,
                           (ca.credit_limit - ca.current_balance) AS available_credit,
                           COALESCE(ca.is_business, FALSE) AS is_business,
                           COALESCE(ct.name, '') AS customer_type_name, COALESCE(ca.phone, '') AS phone
                    FROM customer_accounts ca
                    LEFT JOIN customer_types ct ON ct.customer_type_id = ca.customer_type_id
                    WHERE ca.is_active = TRUE
                    ORDER BY ca.name
                    """)) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("customerId", rs.getInt("customer_id"));
                        row.put("accountNumber", rs.getString("account_number"));
                        row.put("customerName", rs.getString("customer_name"));
                        row.put("creditLimit", rs.getBigDecimal("credit_limit"));
                        row.put("currentBalance", rs.getBigDecimal("current_balance"));
                        row.put("availableCredit", rs.getBigDecimal("available_credit"));
                        row.put("business", rs.getBoolean("is_business"));
                        row.put("customerTypeName", rs.getString("customer_type_name"));
                        row.put("phone", rs.getString("phone"));
                        rows.add(row);
                    }
                }
            }
            return ApiResult.ok(Map.of("accounts", rows));
        }
    }

    private ApiResult currentCashDrawer(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            requireAnyPermission(connection, session.userId(), "MAKE_SALE", "BALANCE_DRAWER",
                    "CASH_DRAWER_MANAGEMENT");
            models.CashDrawerContext drawer = CashDrawerService.resolveDrawerForDevice(
                    connection, session.locationId(), device.deviceId().toString());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("cashDrawerId", drawer.cashDrawerId());
            result.put("drawerName", drawer.drawerName());
            result.put("sessionId", drawer.sessionId());
            result.put("assigned", drawer.isAssigned());
            result.put("activeSession", drawer.hasActiveSession());
            return ApiResult.ok(result);
        }
    }

    private ApiResult salesSettings(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            try {
                return ApiResult.ok(LanHeldCartService.settings(
                        connection, session.userId(), session.locationId()));
            } catch (LanHeldCartService.RuleViolation ex) {
                throw apiException(ex);
            }
        }
    }

    private ApiResult listHeldCarts(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            try {
                return ApiResult.ok(Map.of("heldCarts", LanHeldCartService.list(
                        connection, session.userId(), session.locationId())));
            } catch (LanHeldCartService.RuleViolation ex) {
                throw apiException(ex);
            }
        }
    }

    private ApiResult createHeldCart(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String idempotencyKey = requireIdempotencyKey(context, "A valid idempotency key is required to hold a cart.");
        String operationKey = "held-carts.create.v1";
        String requestHash = LanSecurity.sha256(GSON.toJson(context.body()));
        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> previous = loadIdempotentResult(connection, device.deviceId(),
                        idempotencyKey, operationKey, requestHash);
                if (previous != null) { connection.commit(); return ApiResult.ok(previous); }
                AuthenticatedUser user = loadUser(connection, session.userId(), session.locationId());
                Map<String, Object> result = LanHeldCartService.create(connection, context.body(), device.deviceId(),
                        session.userId(), displayName(user), session.locationId(),
                        (token, permission, action, reason) -> consumeApproval(
                                connection, device, session, token, permission, action, reason));
                completeIdempotency(connection, device.deviceId(), idempotencyKey, result);
                connection.commit(); return ApiResult.ok(result);
            } catch (LanHeldCartService.RuleViolation ex) {
                connection.rollback(); throw apiException(ex);
            } catch (Exception ex) {
                connection.rollback(); throw ex;
            } finally { connection.setAutoCommit(true); }
        }
    }

    private ApiResult resumeHeldCart(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String idempotencyKey = requireIdempotencyKey(context, "A valid idempotency key is required to resume a cart.");
        String operationKey = "held-carts.resume.v1";
        String requestHash = LanSecurity.sha256(GSON.toJson(context.body()));
        int heldCartId = requiredInt(context.body(), "heldCartId");
        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> previous = loadIdempotentResult(connection, device.deviceId(),
                        idempotencyKey, operationKey, requestHash);
                if (previous != null) { connection.commit(); return ApiResult.ok(previous); }
                AuthenticatedUser user = loadUser(connection, session.userId(), session.locationId());
                Map<String, Object> result = LanHeldCartService.resume(connection, heldCartId, device.deviceId(),
                        session.userId(), displayName(user), session.locationId());
                completeIdempotency(connection, device.deviceId(), idempotencyKey, result);
                connection.commit(); return ApiResult.ok(result);
            } catch (LanHeldCartService.RuleViolation ex) {
                connection.rollback(); throw apiException(ex);
            } catch (Exception ex) {
                connection.rollback(); throw ex;
            } finally { connection.setAutoCommit(true); }
        }
    }

    private ApiResult salesHistory(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            Integer requested=context.body().has("locationId")&&!context.body().get("locationId").isJsonNull()
                    ?context.body().get("locationId").getAsInt():session.locationId();
            boolean all=context.body().has("allStores")&&context.body().get("allStores").getAsBoolean();
            try {
                List<Map<String,Object>> transactions=new ArrayList<>();
                List<?> stores=List.of();
                if(all||requested==session.locationId())transactions.addAll(LanSalesHistoryService.history(
                        connection,context.body(),session.userId(),session.locationId()));
                if(all||requested!=session.locationId()){
                    CrossStoreSalesService.HistoryResult remote=CrossStoreSalesService.history(connection,
                            session.userId(),session.locationId(),optional(context.body(),"search",300),
                            optional(context.body(),"fromDate",20),optional(context.body(),"toDate",20),
                            all?null:requested);
                    transactions.addAll(remote.transactions());stores=remote.stores();
                }
                transactions.sort((a,b)->Long.compare(((Number)b.get("createdAtEpochMillis")).longValue(),((Number)a.get("createdAtEpochMillis")).longValue()));
                Map<String,Object> result=new LinkedHashMap<>();result.put("transactions",transactions);result.put("stores",stores);
                result.put("currentLocationId",session.locationId());return ApiResult.ok(result);
            } catch (LanSalesHistoryService.RuleViolation ex) {
                throw apiException(ex);
            } catch(CrossStoreSalesService.RuleViolation ex){
                throw new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);
            }
        }
    }

    private ApiResult salesDetails(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        int saleId = requiredInt(context.body(), "saleId");
        Integer sourceLocationId=context.body().has("sourceLocationId")&&!context.body().get("sourceLocationId").isJsonNull()
                ?context.body().get("sourceLocationId").getAsInt():session.locationId();
        try (Connection connection = DB.getConnection()) {
            try {
                return ApiResult.ok(sourceLocationId==session.locationId()
                        ?LanSalesHistoryService.details(connection,saleId,session.userId(),session.locationId())
                        :CrossStoreSalesService.details(connection,session.userId(),session.locationId(),sourceLocationId,saleId));
            } catch (LanSalesHistoryService.RuleViolation ex) {
                throw apiException(ex);
            } catch(CrossStoreSalesService.RuleViolation ex){throw new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);
            }
        }
    }

    private ApiResult inventoryLookups(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        Integer categoryId=context.body().has("categoryId")&&!context.body().get("categoryId").isJsonNull()
                ? context.body().get("categoryId").getAsInt():null;
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(LanInventoryService.lookups(connection,session.userId(),session.locationId(),categoryId));}
            catch(LanInventoryService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult receivingSearch(RequestContext context) throws Exception {
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        String query=optional(context.body(),"query",300);
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(Map.of("items",LanInventoryService.receivingSearch(connection,query,session.userId(),session.locationId())));}
            catch(LanInventoryService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult inventoryList(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(LanInventoryService.inventory(connection,context.body(),session.userId(),session.locationId()));}
            catch(LanInventoryService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult crossStoreInventorySearch(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");
        DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        String query=optional(context.body(),"query",300);
        Integer locationId=context.body().has("locationId")&&!context.body().get("locationId").isJsonNull()
                ? context.body().get("locationId").getAsInt():null;
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(CrossStoreInventoryService.search(
                    connection,session.userId(),session.locationId(),query,locationId));}
            catch(CrossStoreInventoryService.RuleViolation ex){
                throw new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);
            }
        }
    }

    private ApiResult inventoryDetails(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);int productId=requiredInt(context.body(),"productId");
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(LanInventoryService.details(connection,productId,session.userId(),session.locationId()));}
            catch(LanInventoryService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult receivingHistory(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(Map.of("records",LanInventoryService.receivingHistory(connection,context.body(),session.userId(),session.locationId())));}
            catch(LanInventoryService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult receiveInventory(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        String key=requireIdempotencyKey(context,"A valid idempotency key is required for receiving inventory.");
        String operation="inventory.receive.v1";String hash=LanSecurity.sha256(GSON.toJson(context.body()));
        try(Connection connection=DB.getConnection()){
            connection.setAutoCommit(false);try{
                Map<String,Object>previous=loadIdempotentResult(connection,device.deviceId(),key,operation,hash);
                if(previous!=null){connection.commit();return ApiResult.ok(previous);}
                AuthenticatedUser user=loadUser(connection,session.userId(),session.locationId());
                Map<String,Object>result=LanInventoryService.receive(connection,context.body(),device.deviceId(),session.userId(),
                        displayName(user),session.locationId(),(token,permission,action,reason)->consumeApproval(
                                connection,device,session,token,permission,action,reason));
                completeIdempotency(connection,device.deviceId(),key,result);connection.commit();return ApiResult.ok(result);
            }catch(LanInventoryService.RuleViolation ex){connection.rollback();throw apiException(ex);}
            catch(Exception ex){connection.rollback();throw ex;}finally{connection.setAutoCommit(true);}
        }
    }

    private ApiResult transferDestinations(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(Map.of("locations",LanTransferService.destinations(connection,session.userId(),session.locationId())));}
            catch(LanTransferService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult transferProducts(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);String query=optional(context.body(),"query",300);
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(Map.of("products",LanTransferService.products(connection,query,session.userId(),session.locationId())));}
            catch(LanTransferService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult incomingTransfers(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(Map.of("transfers",LanTransferService.incoming(connection,session.userId(),session.locationId())));}
            catch(LanTransferService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult outgoingTransfers(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(Map.of("transfers",LanTransferService.outgoing(connection,session.userId(),session.locationId())));}
            catch(LanTransferService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult transferItems(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);long transferId=requiredLong(context.body(),"transferId");
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(Map.of("items",LanTransferService.items(connection,transferId,session.userId(),session.locationId())));}
            catch(LanTransferService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult createTransfer(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        String key=requireIdempotencyKey(context,"A valid idempotency key is required to create a transfer.");
        String operation="transfers.create.v1";String hash=LanSecurity.sha256(GSON.toJson(context.body()));
        try(Connection connection=DB.getConnection()){
            connection.setAutoCommit(false);try{
                Map<String,Object>previous=loadIdempotentResult(connection,device.deviceId(),key,operation,hash);
                if(previous!=null){connection.commit();return ApiResult.ok(previous);}
                AuthenticatedUser user=loadUser(connection,session.userId(),session.locationId());
                Map<String,Object>result=LanTransferService.create(connection,context.body(),device.deviceId(),session.userId(),displayName(user),session.locationId());
                completeIdempotency(connection,device.deviceId(),key,result);connection.commit();return ApiResult.ok(result);
            }catch(LanTransferService.RuleViolation ex){connection.rollback();throw apiException(ex);}
            catch(Exception ex){connection.rollback();throw ex;}finally{connection.setAutoCommit(true);}
        }
    }

    private ApiResult receiveTransfer(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);long transferId=requiredLong(context.body(),"transferId");
        String key=requireIdempotencyKey(context,"A valid idempotency key is required to receive a transfer.");
        String operation="transfers.receive.v1";String hash=LanSecurity.sha256(GSON.toJson(context.body()));
        try(Connection connection=DB.getConnection()){
            connection.setAutoCommit(false);try{
                Map<String,Object>previous=loadIdempotentResult(connection,device.deviceId(),key,operation,hash);
                if(previous!=null){connection.commit();return ApiResult.ok(previous);}
                AuthenticatedUser user=loadUser(connection,session.userId(),session.locationId());
                Map<String,Object>result=LanTransferService.receive(connection,transferId,device.deviceId(),session.userId(),displayName(user),session.locationId());
                completeIdempotency(connection,device.deviceId(),key,result);connection.commit();return ApiResult.ok(result);
            }catch(LanTransferService.RuleViolation ex){connection.rollback();throw apiException(ex);}
            catch(Exception ex){connection.rollback();throw ex;}finally{connection.setAutoCommit(true);}
        }
    }

    private ApiResult catalogDepartments(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String search = optional(context.body(), "search", 300);
        try (Connection connection = DB.getConnection()) {
            try {
                return ApiResult.ok(LanCatalogAdminService.departments(
                        connection, search, session.userId(), session.locationId()));
            } catch (LanCatalogAdminService.RuleViolation ex) { throw apiException(ex); }
        }
    }

    private ApiResult saveCatalogDepartment(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String key = requireIdempotencyKey(context, "A valid idempotency key is required to save a department.");
        String operation = "catalog.departments.save.v1";
        String hash = LanSecurity.sha256(GSON.toJson(context.body()));
        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> previous = loadIdempotentResult(connection, device.deviceId(), key, operation, hash);
                if (previous != null) { connection.commit(); return ApiResult.ok(previous); }
                Map<String, Object> result = LanCatalogAdminService.saveDepartment(connection, context.body(),
                        device.deviceId(), session.userId(), session.locationId());
                completeIdempotency(connection, device.deviceId(), key, result);
                connection.commit(); return ApiResult.ok(result);
            } catch (LanCatalogAdminService.RuleViolation ex) { connection.rollback(); throw apiException(ex); }
            catch (Exception ex) { connection.rollback(); throw ex; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private ApiResult catalogVendors(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String search = optional(context.body(), "search", 300);
        try (Connection connection = DB.getConnection()) {
            try {
                return ApiResult.ok(Map.of("vendors", LanCatalogAdminService.vendors(
                        connection, search, session.userId())));
            } catch (LanCatalogAdminService.RuleViolation ex) { throw apiException(ex); }
        }
    }

    private ApiResult saveCatalogVendor(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String key = requireIdempotencyKey(context, "A valid idempotency key is required to save a vendor.");
        String operation = "catalog.vendors.save.v1";
        String hash = LanSecurity.sha256(GSON.toJson(context.body()));
        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> previous = loadIdempotentResult(connection, device.deviceId(), key, operation, hash);
                if (previous != null) { connection.commit(); return ApiResult.ok(previous); }
                Map<String, Object> result = LanCatalogAdminService.saveVendor(
                        connection, context.body(), device.deviceId(), session.userId());
                completeIdempotency(connection, device.deviceId(), key, result);
                connection.commit(); return ApiResult.ok(result);
            } catch (LanCatalogAdminService.RuleViolation ex) { connection.rollback(); throw apiException(ex); }
            catch (Exception ex) { connection.rollback(); throw ex; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private ApiResult catalogCustomerTypes(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String search = optional(context.body(), "search", 300);
        boolean activeOnly = context.body().has("activeOnly") && context.body().get("activeOnly").getAsBoolean();
        try (Connection connection = DB.getConnection()) {
            try { return ApiResult.ok(Map.of("customerTypes", LanCatalogAdminService.customerTypes(
                    connection, search, activeOnly, session.userId()))); }
            catch (LanCatalogAdminService.RuleViolation ex) { throw apiException(ex); }
        }
    }

    private ApiResult saveCatalogCustomerType(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String key = requireIdempotencyKey(context, "A valid idempotency key is required to save a customer type.");
        String operation = "catalog.customer-types.save.v1";
        String hash = LanSecurity.sha256(GSON.toJson(context.body()));
        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> previous = loadIdempotentResult(connection, device.deviceId(), key, operation, hash);
                if (previous != null) { connection.commit(); return ApiResult.ok(previous); }
                Map<String, Object> result = LanCatalogAdminService.saveCustomerType(
                        connection, context.body(), device.deviceId(), session.userId());
                completeIdempotency(connection, device.deviceId(), key, result);
                connection.commit(); return ApiResult.ok(result);
            } catch (LanCatalogAdminService.RuleViolation ex) { connection.rollback(); throw apiException(ex); }
            catch (Exception ex) { connection.rollback(); throw ex; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private ApiResult editableProductSearch(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String search = optional(context.body(), "search", 300);
        try (Connection connection = DB.getConnection()) {
            try { return ApiResult.ok(Map.of("products", LanProductAdminService.searchEditable(
                    connection, search, session.userId(), session.locationId()))); }
            catch (LanProductAdminService.RuleViolation ex) { throw apiException(ex); }
        }
    }

    private ApiResult priceTagProductSearch(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String search = optional(context.body(), "search", 300);
        try (Connection connection = DB.getConnection()) {
            try { return ApiResult.ok(Map.of("items", LanProductAdminService.priceTagItems(
                    connection, search, session.userId(), session.locationId()))); }
            catch (LanProductAdminService.RuleViolation ex) { throw apiException(ex); }
        }
    }

    private ApiResult priceTagSettings(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            try { return ApiResult.ok(LanProductAdminService.priceTagSettings(
                    connection, session.userId(), session.locationId())); }
            catch (LanProductAdminService.RuleViolation ex) { throw apiException(ex); }
        }
    }

    private ApiResult changeBasketState(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        try(Connection c=DB.getConnection()){try{return ApiResult.ok(LanCashOperationsService.changeBasketState(c,session.userId(),session.locationId()));}catch(LanCashOperationsService.RuleViolation ex){throw apiException(ex);}}
    }
    private ApiResult updateChangeBasket(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        String key=requireIdempotencyKey(context,"A valid idempotency key is required to update the change basket."),operation="cash.change-basket.update.v1",hash=LanSecurity.sha256(GSON.toJson(context.body()));
        try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{Map<String,Object>previous=loadIdempotentResult(c,device.deviceId(),key,operation,hash);if(previous!=null){c.commit();return ApiResult.ok(previous);}
            AuthenticatedUser user=loadUser(c,session.userId(),session.locationId());Map<String,Object>result=LanCashOperationsService.recordChangeBasket(c,context.body(),device.deviceId(),session.userId(),displayName(user),session.locationId());
            completeIdempotency(c,device.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(LanCashOperationsService.RuleViolation ex){c.rollback();throw apiException(ex);}catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}}
    }

    private ApiResult changeEmployeePin(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);String pin=required(context.body(),"pin",8);
        char[]secret=pin.toCharArray();try(Connection c=DB.getConnection()){
            try{LanEmployeeSelfService.changePin(c,session.userId(),session.locationId(),secret);return ApiResult.ok(Map.of("changed",true));}
            catch(LanEmployeeSelfService.RuleViolation ex){throw apiException(ex);}
        }finally{java.util.Arrays.fill(secret,'\0');}
    }

    private ApiResult cashDrawerState(RequestContext x)throws Exception{return cashDrawerRead(x,(c,d,s,u)->LanCashDrawerService.registerState(c,d.deviceId(),s.userId(),s.locationId()));}
    private ApiResult recentCashDrawers(RequestContext x)throws Exception{return cashDrawerRead(x,(c,d,s,u)->LanCashDrawerService.recent(c,s.userId(),s.locationId()));}
    private ApiResult cashDrawerAdminState(RequestContext x)throws Exception{return cashDrawerRead(x,(c,d,s,u)->LanCashDrawerService.adminState(c,x.body(),s.userId()));}
    private ApiResult openCashDrawer(RequestContext x)throws Exception{return cashDrawerMutation(x,"cash.drawer.open.v1",(c,d,s,u)->LanCashDrawerService.open(c,d.deviceId(),s.userId(),displayName(u),s.locationId()));}
    private ApiResult handoverCashDrawer(RequestContext x)throws Exception{return cashDrawerMutation(x,"cash.drawer.handover.v1",(c,d,s,u)->LanCashDrawerService.handover(c,x.body(),s.userId(),displayName(u)));}
    private ApiResult closeCashDrawer(RequestContext x)throws Exception{return cashDrawerMutation(x,"cash.drawer.close.v1",(c,d,s,u)->LanCashDrawerService.close(c,x.body(),s.userId(),displayName(u)));}
    private ApiResult reviseCashDrawer(RequestContext x)throws Exception{return cashDrawerMutation(x,"cash.drawer.revise.v1",(c,d,s,u)->LanCashDrawerService.revise(c,x.body(),s.userId(),displayName(u)));}
    private ApiResult saveCashDrawer(RequestContext x)throws Exception{return cashDrawerMutation(x,"cash.drawer.save.v1",(c,d,s,u)->LanCashDrawerService.saveDrawer(c,x.body(),s.userId()));}
    private ApiResult assignCashDrawer(RequestContext x)throws Exception{return cashDrawerMutation(x,"cash.drawer.assign.v1",(c,d,s,u)->LanCashDrawerService.assign(c,x.body(),s.userId()));}
    private ApiResult unassignCashDrawer(RequestContext x)throws Exception{return cashDrawerMutation(x,"cash.drawer.unassign.v1",(c,d,s,u)->LanCashDrawerService.unassign(c,x.body(),s.userId()));}
    private ApiResult saveCashDrawerChangeTarget(RequestContext x)throws Exception{return cashDrawerMutation(x,"cash.drawer.change-target.v1",(c,d,s,u)->LanCashDrawerService.saveTarget(c,x.body(),s.userId()));}

    private ApiResult cashDrawerRead(RequestContext x,CashDrawerOperation operation)throws Exception{
        requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        try(Connection c=DB.getConnection()){try{return ApiResult.ok(operation.run(c,d,s,loadUser(c,s.userId(),s.locationId())));}catch(LanCashDrawerService.RuleViolation e){throw apiException(e);}}
    }
    private ApiResult cashDrawerMutation(RequestContext x,String operationName,CashDrawerOperation operation)throws Exception{
        requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        String key=requireIdempotencyKey(x,"A valid idempotency key is required for this cash drawer change."),hash=LanSecurity.sha256(GSON.toJson(x.body()));
        try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operationName,hash);if(old!=null){c.commit();return ApiResult.ok(old);}
            Map<String,Object>result=operation.run(c,d,s,loadUser(c,s.userId(),s.locationId()));completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);
        }catch(LanCashDrawerService.RuleViolation e){c.rollback();throw apiException(e);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}
    }

    private ApiResult deviceAdminList(RequestContext x)throws Exception{return deviceAdminRead(x,(c,s)->LanDeviceAdminService.list(c,s.userId()));}
    private ApiResult deviceAdminSessions(RequestContext x)throws Exception{return deviceAdminRead(x,(c,s)->LanDeviceAdminService.sessions(c,x.body(),s.userId()));}
    private ApiResult deviceSecurityStatus(RequestContext x)throws Exception{return deviceAdminRead(x,(c,s)->LanDeviceAdminService.security(c,s.userId()));}
    private ApiResult deviceAdminUpdate(RequestContext x)throws Exception{
        requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        String key=requireIdempotencyKey(x,"A valid idempotency key is required for a device security change."),operation="security.devices.update.v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));
        try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}
            Map<String,Object>result=LanDeviceAdminService.update(c,x.body(),s.userId());completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);
        }catch(LanDeviceAdminService.RuleViolation e){c.rollback();throw apiException(e);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}
    }
    private ApiResult deviceAdminRead(RequestContext x,DeviceAdminOperation operation)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        try(Connection c=DB.getConnection()){try{return ApiResult.ok(operation.run(c,s));}catch(LanDeviceAdminService.RuleViolation e){throw apiException(e);}}}

    private ApiResult serverAdminList(RequestContext x)throws Exception{
        requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        try(Connection c=DB.getConnection()){try{return ApiResult.ok(LanServerAdminService.list(c,s.userId(),s.locationId()));}
        catch(LanServerAdminService.RuleViolation e){throw apiException(e);}
        catch(CloudServerRegistryService.RegistryException e){throw apiException(e);}
        catch(java.io.IOException e){throw new ApiException(503,"SERVER_REGISTRY_UNAVAILABLE","Server inventory is temporarily unavailable.",true);}}
    }
    private ApiResult serverAdminUpdate(RequestContext x)throws Exception{
        return serverAdminMutation(x,null);
    }
    private ApiResult serverAdminAction(RequestContext x,String action)throws Exception{
        return serverAdminMutation(x,action);
    }
    private ApiResult serverAdminMutation(RequestContext x,String forcedAction)throws Exception{
        requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        if(forcedAction!=null)x.body().addProperty("action",forcedAction);
        String key=requireIdempotencyKey(x,"A valid idempotency key is required for a server operation."),operation="security.servers.update.v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));
        try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{
            Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}
            AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());
            Map<String,Object>result=LanServerAdminService.mutate(c,x.body(),s.userId(),displayName(u),s.locationId());
            completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);
        }catch(LanServerAdminService.RuleViolation e){c.rollback();throw apiException(e);}
        catch(CloudServerRegistryService.RegistryException e){c.rollback();throw apiException(e);}
        catch(java.io.IOException e){c.rollback();throw new ApiException(503,"SERVER_REGISTRY_UNAVAILABLE","Server coordination is temporarily unavailable.",true);}
        catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}
    }
    private ApiResult serverAdminHandoffStatus(RequestContext x)throws Exception{
        requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        try(Connection c=DB.getConnection()){try{return ApiResult.ok(LanServerAdminService.handoffStatus(c,x.body(),s.userId(),s.locationId()));}
        catch(LanServerAdminService.RuleViolation e){throw apiException(e);}
        catch(CloudServerRegistryService.RegistryException e){throw apiException(e);}
        catch(java.io.IOException e){throw new ApiException(503,"SERVER_REGISTRY_UNAVAILABLE","Handoff status is temporarily unavailable.",true);}}
    }

    private ApiResult locationList(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){try{return ApiResult.ok(LanLocationService.list(c,x.body(),s.userId()));}catch(LanLocationService.RuleViolation e){throw apiException(e);}}}
    private ApiResult processLocationEmail(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());bindServerIdentity(c,d,s,u);ServerRequestIdentity.bindSupabaseAccessToken(optional(x.body(),"supabaseAccessToken",16384));try{return ApiResult.ok(LanLocationService.processEmail(c,s.userId()));}catch(LanLocationService.RuleViolation e){throw apiException(e);}finally{ServerRequestIdentity.clear();}}}
    private ApiResult saveLocation(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String key=requireIdempotencyKey(x,"A valid idempotency key is required to save a location."),operation="locations.save.v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));
        try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}Map<String,Object>result=LanLocationService.save(c,x.body(),s.userId());completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);
        }catch(LanLocationService.RuleViolation e){c.rollback();throw apiException(e);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}}

    private ApiResult reportOptions(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"VIEW_REPORTS");return ApiResult.ok(Map.of("options",ReportDataService.loadOptions(c,s.locationId())));}}
    private ApiResult loadReports(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        ReportDataService.Filters requested=GSON.fromJson(x.body().get("filters"),ReportDataService.Filters.class);if(requested==null)throw new ApiException(400,"VALIDATION_ERROR","Report filters are required.",false);
        try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"VIEW_REPORTS");AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());java.time.ZoneId zone=java.time.ZoneId.of(u.locationTimezone());
            ReportDataService.Filters scoped=new ReportDataService.Filters(requested.from(),requested.to(),zone,s.locationId(),requested.products(),requested.brands(),requested.departments(),requested.itemTypes(),requested.employees(),requested.paymentMethods());
            boolean allRevenue=x.body().has("allRevenue")&&x.body().get("allRevenue").getAsBoolean(),accounting=hasPermission(c,s.userId(),"BALANCE_SHEET");return ApiResult.ok(Map.of("snapshot",ReportDataService.load(c,scoped,allRevenue,accounting)));}}
    private ApiResult orderReport(RequestContext x)throws Exception{return detailedReport(x,true);}
    private ApiResult invoiceReport(RequestContext x)throws Exception{return detailedReport(x,false);}
    private ApiResult detailedReport(RequestContext x,boolean orders)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);java.time.ZonedDateTime from=java.time.ZonedDateTime.parse(required(x.body(),"from",80)),to=java.time.ZonedDateTime.parse(required(x.body(),"to",80));try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"VIEW_REPORTS");return ApiResult.ok(orders?LanReportDetailService.orders(c,from,to,s.locationId()):LanReportDetailService.invoices(c,from,to,s.locationId()));}}
    private ApiResult maintenancePartsList(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){try{return ApiResult.ok(LanMaintenancePartsService.list(c,x.body(),s.userId()));}catch(LanMaintenancePartsService.RuleViolation e){throw apiException(e);}}}
    private ApiResult maintenancePartSave(RequestContext x)throws Exception{return maintenancePartMutation(x,"maintenance.parts.save.v1",false);}
    private ApiResult maintenancePartDelete(RequestContext x)throws Exception{return maintenancePartMutation(x,"maintenance.parts.delete.v1",true);}
    private ApiResult maintenancePartMutation(RequestContext x,String operation,boolean delete)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String key=requireIdempotencyKey(x,"A valid idempotency key is required for this parts change."),hash=LanSecurity.sha256(GSON.toJson(x.body()));try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}Map<String,Object>result=delete?LanMaintenancePartsService.delete(c,x.body(),s.userId()):LanMaintenancePartsService.save(c,x.body(),s.userId());completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(LanMaintenancePartsService.RuleViolation e){c.rollback();throw apiException(e);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}}
    private ApiResult machineState(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"MACHINE_MANAGEMENT","MAINTENANCE_MANAGEMENT");return ApiResult.ok(Map.of("state",LanMachineService.state(c,optional(x.body(),"search",300))));}}
    private ApiResult machineDetail(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"MACHINE_MANAGEMENT","MAINTENANCE_MANAGEMENT");return ApiResult.ok(Map.of("detail",LanMachineService.detail(c,requiredInt(x.body(),"machineId"))));}}
    private ApiResult machineMutation(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String key=requireIdempotencyKey(x,"A valid idempotency key is required for this machine change."),operation="maintenance.machine.update.v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{requireAnyPermission(c,s.userId(),"MACHINE_MANAGEMENT");Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}String action=required(x.body(),"action",30);Map<String,Object>result=new LinkedHashMap<>();switch(action){case "SAVE"->result.put("machineId",LanMachineService.save(c,GSON.fromJson(x.body().get("machine"),LanMachineService.Machine.class)));case "DELETE"->{LanMachineService.delete(c,requiredInt(x.body(),"machineId"));result.put("deleted",true);}case "LINK"->{LanMachineService.link(c,requiredInt(x.body(),"machineId"),requiredInt(x.body(),"partId"),optional(x.body(),"notes",2000));result.put("linked",true);}case "UNLINK"->{LanMachineService.unlink(c,requiredLong(x.body(),"linkId"));result.put("unlinked",true);}default->throw new ApiException(400,"VALIDATION_ERROR","The machine action is invalid.",false);}completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}}
    private ApiResult maintenanceWorkflowState(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"MAINTENANCE_MANAGEMENT");return ApiResult.ok(Map.of("state",LanMaintenanceWorkflowService.state(c,optional(x.body(),"search",300),optional(x.body(),"filter",40))));}}
    private ApiResult maintenanceWorkflowDetail(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"MAINTENANCE_MANAGEMENT");return ApiResult.ok(Map.of("detail",LanMaintenanceWorkflowService.detail(c,required(x.body(),"type",20),requiredInt(x.body(),"id"))));}}
    private ApiResult maintenanceWorkflowMutation(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String key=requireIdempotencyKey(x,"A valid idempotency key is required for this maintenance change."),operation="maintenance.workflow.update.v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{requireAnyPermission(c,s.userId(),"MAINTENANCE_MANAGEMENT");Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}String action=required(x.body(),"action",30);Map<String,Object>result=new LinkedHashMap<>();switch(action){case "SAVE_LOG"->result.put("id",LanMaintenanceWorkflowService.saveLog(c,GSON.fromJson(x.body().get("log"),LanMaintenanceWorkflowService.Log.class),s.userId()));case "SAVE_TICKET"->result.put("id",LanMaintenanceWorkflowService.saveTicket(c,GSON.fromJson(x.body().get("ticket"),LanMaintenanceWorkflowService.Ticket.class),s.userId()));case "CLOSE_TICKET"->{LanMaintenanceWorkflowService.closeTicket(c,requiredInt(x.body(),"id"));result.put("closed",true);}default->throw new ApiException(400,"VALIDATION_ERROR","The maintenance action is invalid.",false);}completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}}
    private ApiResult customOrderCatalog(RequestContext x)throws Exception {
        requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        try(Connection c=DB.getConnection()) {
            requireAnyPermission(c,s.userId(),"CREATE_CUSTOM_ORDER","MANAGE_CUSTOM_ORDERS","VIEW_ASSIGNED_CUSTOM_ORDERS","ORDERS_MANAGER_DASHBOARD");
            String action=required(x.body(),"action",40).toUpperCase(java.util.Locale.ROOT);
            return ApiResult.ok(switch(action) {
                case "ITEMS" -> Map.of("items",ServerCustomOrderDataService.listActiveItems(c));
                case "VARIANTS" -> Map.of("variants",ServerCustomOrderDataService.listActiveVariants(c,requiredLong(x.body(),"customItemId")));
                case "PRINT_MATERIALS" -> Map.of("materials",ServerCustomOrderDataService.listActivePrintMaterials(c));
                case "PRINT_PRESETS" -> Map.of("presets",ServerCustomOrderDataService.listActivePrintSizePresets(c,requiredLong(x.body(),"printMaterialId")));
                case "PLACEMENTS" -> Map.of("placements",ServerCustomOrderDataService.listActiveDesignPlacements(c));
                case "CUSTOMERS" -> Map.of("customers",ServerCustomOrderDataService.searchCustomers(c,optional(x.body(),"search",300)));
                case "EMPLOYEES" -> Map.of("employees",ServerCustomOrderDataService.listActiveEmployees(c,s.locationId()));
                case "LOOKUP" -> {Map<String,Object>r=new LinkedHashMap<>();r.put("match",ServerCustomOrderDataService.lookupCustomItem(c,optional(x.body(),"search",300)));yield r;}
                default -> throw new ApiException(400,"VALIDATION_ERROR","The custom-order catalog action is invalid.",false);
            });
        }
    }
    private ApiResult createCustomOrder(RequestContext x)throws Exception {
        requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        String key=requireIdempotencyKey(x,"A valid idempotency key is required to create a custom order."),operation="custom-orders.create.v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));
        try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{
            requireAnyPermission(c,s.userId(),"CREATE_CUSTOM_ORDER");Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}
            AuthenticatedUser user=loadUser(c,s.userId(),s.locationId());bindServerIdentity(c,d,s,user);ServerCustomOrderDataService.OrderSaveRequest supplied=GSON.fromJson(x.body(),ServerCustomOrderDataService.OrderSaveRequest.class);
            String number=ServerCustomOrderDataService.saveCustomOrder(c,trustedCustomOrderRequest(c,d,s,user,supplied));Map<String,Object>result=Map.of("orderNumber",number);completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);
        }catch(ApiException e){c.rollback();throw e;}catch(SQLException e){c.rollback();throw new ApiException(409,"CUSTOM_ORDER_REJECTED","The custom order could not be created. Review the order and try again.",false);}catch(Exception e){c.rollback();throw e;}finally{ServerRequestIdentity.clear();c.setAutoCommit(true);}}}
    private ServerCustomOrderDataService.OrderSaveRequest trustedCustomOrderRequest(
            Connection c,DevicePrincipal d,SessionPrincipal s,AuthenticatedUser user,
            ServerCustomOrderDataService.OrderSaveRequest request)throws Exception {
        if(request==null||request.lines()==null||request.lines().isEmpty())
            throw new ApiException(400,"VALIDATION_ERROR","Add at least one item to the custom order.",false);
        if(request.lines().size()>250)
            throw new ApiException(400,"VALIDATION_ERROR","A custom order cannot contain more than 250 lines.",false);

        BigDecimal total=BigDecimal.ZERO;
        List<ServerCustomOrderDataService.OrderLineRequest> lines=new ArrayList<>();
        Map<String,LanSalesService.Approval> approvals=new LinkedHashMap<>();
        for(ServerCustomOrderDataService.OrderLineRequest line:request.lines()) {
            if(line==null||line.unitPrice()==null||line.unitPrice().compareTo(BigDecimal.ZERO)<0)
                throw new ApiException(400,"VALIDATION_ERROR","Every custom-order line must have a valid non-negative total.",false);
            boolean discounted=line.lineDiscountPercent()!=null&&line.lineDiscountPercent().signum()>0;
            Integer discountBy=discounted?s.userId():null;
            String discountName=discounted?displayName(user):null;
            if(discounted&&!hasAnyPermission(c,s.userId(),"CUSTOM_ORDER_LINE_DISCOUNT","CUSTOM_ORDER_OVERRIDES")) {
                String reason=line.lineDiscountReason()==null?"":line.lineDiscountReason().trim();
                if(reason.isBlank())throw new ApiException(400,"VALIDATION_ERROR","A line discount reason is required.",false);
                String token=line.lineDiscountApprovalToken();
                String cacheKey="DISCOUNT|"+token+"|"+reason;
                LanSalesService.Approval approval=approvals.get(cacheKey);
                if(approval==null){approval=consumeApproval(c,d,s,token,"CUSTOM_ORDER_LINE_DISCOUNT","Custom Order Line Discount Override",reason);approvals.put(cacheKey,approval);}
                discountBy=approval.approverUserId();discountName=approval.approverName();
            }

            boolean overridden=line.priceOverridePrice()!=null||(line.priceOverrideReason()!=null&&!line.priceOverrideReason().isBlank());
            Integer priceBy=overridden?s.userId():null;
            String priceName=overridden?displayName(user):null;
            if(overridden&&!hasAnyPermission(c,s.userId(),"CUSTOM_ORDER_PRICE_OVERRIDE","CUSTOM_ORDER_OVERRIDES")) {
                String reason=line.priceOverrideReason()==null?"":line.priceOverrideReason().trim();
                if(reason.isBlank())throw new ApiException(400,"VALIDATION_ERROR","A price override reason is required.",false);
                String token=line.priceOverrideApprovalToken();
                String cacheKey="PRICE|"+token+"|"+reason;
                LanSalesService.Approval approval=approvals.get(cacheKey);
                if(approval==null){approval=consumeApproval(c,d,s,token,"CUSTOM_ORDER_PRICE_OVERRIDE","Custom Order Price Override",reason);approvals.put(cacheKey,approval);}
                priceBy=approval.approverUserId();priceName=approval.approverName();
            }
            total=total.add(line.unitPrice());
            lines.add(new ServerCustomOrderDataService.OrderLineRequest(
                    line.customItemId(),line.customVariantId(),line.itemName(),line.variantName(),line.pricingType(),
                    line.unitPrice(),line.customizationDetails(),line.orderInstructions(),line.widthValue(),line.lengthValue(),
                    line.dimensionUnit(),line.areaValue(),line.areaUnit(),line.areaPrice(),line.baseItemPrice(),
                    line.printMaterialId(),line.printMaterialName(),line.printSizePresetId(),line.printSizeName(),
                    line.printCharge(),line.printLineCount(),line.originalLineTotal(),line.lineDiscountPercent(),
                    line.lineDiscountAmount(),discountBy,discountName,line.lineDiscountReason(),line.minimumDepositPercent(),
                    line.originalBasePrice(),line.priceOverridePrice(),line.priceOverrideReason(),priceBy,priceName,
                    line.printAddons()==null?List.of():line.printAddons(),null,null));
        }

        total=utils.CurrencyFormatter.normalize(total);
        BigDecimal paid=utils.CurrencyFormatter.normalize(request.amountPaid());
        if(paid.signum()<0||paid.compareTo(total)>0)
            throw new ApiException(400,"VALIDATION_ERROR","The upfront payment must be between zero and the order total.",false);
        BigDecimal balance=utils.CurrencyFormatter.normalize(total.subtract(paid));
        BigDecimal requiredDeposit=loadRequiredCustomOrderDeposit(c,s.locationId(),total);
        boolean depositOverride=paid.compareTo(requiredDeposit)<0;
        Integer depositBy=depositOverride?s.userId():null;
        String depositName=depositOverride?displayName(user):null;
        if(depositOverride) {
            String reason=request.depositOverrideReason()==null?"":request.depositOverrideReason().trim();
            if(reason.isBlank())throw new ApiException(400,"VALIDATION_ERROR","A deposit override reason is required.",false);
            if(!hasAnyPermission(c,s.userId(),"CUSTOM_ORDER_DEPOSIT_OVERRIDE","CUSTOM_ORDER_OVERRIDES")) {
                LanSalesService.Approval approval=consumeApproval(c,d,s,request.depositApprovalToken(),
                        "CUSTOM_ORDER_DEPOSIT_OVERRIDE","Custom Order Deposit Override",reason);
                depositBy=approval.approverUserId();depositName=approval.approverName();
            }
        }
        String method=paid.signum()==0?null:(request.paymentMethod()==null?"":request.paymentMethod().trim().toUpperCase(java.util.Locale.ROOT));
        if(method!=null&&!List.of("CASH","CARD","CHEQUE","MMG").contains(method))
            throw new ApiException(400,"VALIDATION_ERROR","The upfront payment method is invalid.",false);
        String status=paid.signum()==0?"UNPAID":balance.signum()==0?"PAID":"PARTIAL";
        ServerCustomOrderDataService.CustomerOption customer=trustedCustomOrderCustomer(c,request.selectedCustomer(),request.customerName(),request.customerPhone());
        return new ServerCustomOrderDataService.OrderSaveRequest(customer,customer.name(),customer.phone(),request.dueDate(),
                total,paid,balance,method,request.paymentReference(),status,s.userId(),displayName(user),s.locationId(),
                user.locationName(),d.deviceId().toString(),loadDeviceDisplayName(c,d.deviceId()),requiredDeposit,
                depositOverride?request.depositOverrideReason():null,depositBy,depositName,request.orderNotes(),lines,null);
    }
    private ServerCustomOrderDataService.CustomerOption trustedCustomOrderCustomer(Connection c,ServerCustomOrderDataService.CustomerOption selected,String name,String phone)throws Exception {
        if(selected==null||selected.customerId()==null){String clean=name==null?"":name.trim();if(clean.isBlank())throw new ApiException(400,"VALIDATION_ERROR","Customer name is required.",false);return new ServerCustomOrderDataService.CustomerOption(null,clean,phone==null?"":phone.trim());}
        try(PreparedStatement ps=c.prepareStatement("SELECT customer_id,name,COALESCE(phone,'') FROM customer_accounts WHERE customer_id=? AND is_active=TRUE")){ps.setInt(1,selected.customerId());try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new ApiException(404,"CUSTOMER_NOT_FOUND","The selected customer account is not active.",false);return new ServerCustomOrderDataService.CustomerOption(rs.getInt(1),rs.getString(2),rs.getString(3));}}
    }
    private BigDecimal loadRequiredCustomOrderDeposit(Connection c,int locationId,BigDecimal total)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(custom_order_minimum_deposit_percent,0) FROM company_customization WHERE location_id=?")){ps.setInt(1,locationId);try(ResultSet rs=ps.executeQuery()){BigDecimal percent=rs.next()?rs.getBigDecimal(1):BigDecimal.ZERO;return utils.CurrencyFormatter.normalize(total.multiply(percent==null?BigDecimal.ZERO:percent).divide(BigDecimal.valueOf(100),6,java.math.RoundingMode.HALF_UP));}}
    }
    private String loadDeviceDisplayName(Connection c,UUID deviceId)throws SQLException {
        try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(NULLIF(device_name,''),NULLIF(hostname,''),device_id::text) FROM devices WHERE device_id=?")){ps.setObject(1,deviceId);try(ResultSet rs=ps.executeQuery()){return rs.next()?rs.getString(1):deviceId.toString();}}
    }
    private ApiResult customOrderDashboard(RequestContext x)throws Exception {
        requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"ORDERS_MANAGER_DASHBOARD","MANAGE_CUSTOM_ORDERS");AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());return ApiResult.ok(Map.of("dashboard",LanOrdersDashboardService.load(c,s.locationId(),java.time.ZoneId.of(u.locationTimezone()))));}
    }
    private ApiResult assignCustomOrder(RequestContext x)throws Exception {
        requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String key=requireIdempotencyKey(x,"A valid idempotency key is required for this order assignment."),operation="custom-orders.assign.v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));
        try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{requireAnyPermission(c,s.userId(),"MANAGE_CUSTOM_ORDERS","CUSTOM_ORDER_OVERRIDES");Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());Integer employee=x.body().has("employeeId")&&!x.body().get("employeeId").isJsonNull()?x.body().get("employeeId").getAsInt():null;LanOrdersDashboardService.assign(c,requiredLong(x.body(),"orderId"),employee,required(x.body(),"status",30),s.userId(),displayName(u),d.deviceId(),loadDeviceDisplayName(c,d.deviceId()),s.locationId());Map<String,Object>result=Map.of("saved",true);completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}}
    private ApiResult customOrderSlip(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String number=required(x.body(),"orderNumber",100);try(Connection c=DB.getConnection()){try{return ApiResult.ok(Map.of("slip",LanDocumentDataService.customOrderSlip(c,number,s.userId(),s.locationId())));}catch(LanDocumentDataService.RuleViolation e){throw apiException(e);}}}

    private ApiResult timeClockAutoCloseSettings(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"COMPANY_PREFERENCES");return ApiResult.ok(Map.of("settings",TimeClockAutoCloseService.loadSettings(c)));}}
    private ApiResult timeClockAutoCloseReviews(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"TIME_CLOCK_MANAGEMENT");return ApiResult.ok(Map.of("reviews",TimeClockAutoCloseService.loadPendingReviews(c)));}}
    private ApiResult timeClockAutoCloseNotice(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){TimeClockAutoCloseService.EmployeeAutoCloseNotice notice=TimeClockAutoCloseService.latestPendingNotice(c,s.userId());Map<String,Object>result=new LinkedHashMap<>();result.put("notice",notice);return ApiResult.ok(result);}}
    private ApiResult saveTimeClockAutoCloseSettings(RequestContext x)throws Exception{return timeClockAutoCloseMutation(x,"time-clock.auto-close.settings.v1",(c,s,u)->{requireAnyPermission(c,s.userId(),"COMPANY_PREFERENCES");TimeClockAutoCloseService.AutoCloseSettings settings=GSON.fromJson(x.body().get("settings"),TimeClockAutoCloseService.AutoCloseSettings.class);if(settings==null)throw new ApiException(400,"VALIDATION_ERROR","Automatic clock-out settings are required.",false);TimeClockAutoCloseService.saveSettings(c,settings,s.userId(),displayName(u));return Map.of("saved",true);});}
    private ApiResult confirmTimeClockAutoClose(RequestContext x)throws Exception{return timeClockAutoCloseMutation(x,"time-clock.auto-close.confirm.v1",(c,s,u)->{requireAnyPermission(c,s.userId(),"TIME_CLOCK_MANAGEMENT");TimeClockAutoCloseService.confirm(c,requiredLong(x.body(),"clockId"),optional(x.body(),"reason",2000),s.userId(),displayName(u));return Map.of("confirmed",true);});}
    private ApiResult correctTimeClockAutoClose(RequestContext x)throws Exception{return timeClockAutoCloseMutation(x,"time-clock.auto-close.correct.v1",(c,s,u)->{requireAnyPermission(c,s.userId(),"TIME_CLOCK_MANAGEMENT");long clockId=requiredLong(x.body(),"clockId");java.time.ZoneId zone;try{zone=java.time.ZoneId.of(required(x.body(),"zoneId",100));}catch(Exception e){throw new ApiException(400,"VALIDATION_ERROR","The location timezone is invalid.",false);}TimeClockAutoCloseService.Correction correction=GSON.fromJson(x.body().get("correction"),TimeClockAutoCloseService.Correction.class);if(correction==null)throw new ApiException(400,"VALIDATION_ERROR","Clock correction details are required.",false);TimeClockAutoCloseService.correct(c,clockId,zone,correction,s.userId(),displayName(u));return Map.of("corrected",true);});}
    private ApiResult timeClockAutoCloseMutation(RequestContext x,String operation,TimeClockAutoCloseMutation action)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String key=requireIdempotencyKey(x,"A valid idempotency key is required for this time-clock change."),hash=LanSecurity.sha256(GSON.toJson(x.body()));try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());Map<String,Object>result=action.run(c,s,u);completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(SQLException e){c.rollback();throw new ApiException(409,"TIME_CLOCK_CHANGE_REJECTED","The time-clock change could not be completed.",false);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}}

    private ApiResult timeClockDashboard(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());boolean all=hasPermission(c,s.userId(),"TIME_CLOCK_MANAGEMENT")||hasPermission(c,s.userId(),"EMPLOYEE_MANAGEMENT")||hasPermission(c,s.userId(),"ROLE_MANAGEMENT");bindTimeClock(s,u);try{return ApiResult.ok(Map.of("dashboard",ServerTimeClockManager.loadDashboard(c,all)));}finally{ServerTimeClockManager.clearRequest();}}}
    private ApiResult timeClockPunchState(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());bindTimeClock(s,u);try{return ApiResult.ok(Map.of("requiresOverride",ServerTimeClockManager.requiresMultipleSessionOverride(c),"requesterCanOverride",ServerTimeClockManager.currentUserCanApproveMultipleSessionOverride(c)));}finally{ServerTimeClockManager.clearRequest();}}}
    private ApiResult timeClockPunch(RequestContext x)throws Exception{return timeClockCoreMutation(x,"time-clock.punch.v1",(c,d,s,u)->{String action=required(x.body(),"action",30).toUpperCase(java.util.Locale.ROOT);bindTimeClock(s,u);try{switch(action){case "CLOCK_IN"->{services.ManagerApprovalService.ApprovalResult approval=null;boolean needs=ServerTimeClockManager.requiresMultipleSessionOverride(c),self=ServerTimeClockManager.currentUserCanApproveMultipleSessionOverride(c);if(needs&&!self){String reason=required(x.body(),"approvalReason",2000);LanSalesService.Approval trusted=consumeApproval(c,d,s,optional(x.body(),"approvalToken",512),ServerTimeClockManager.MULTIPLE_SESSION_OVERRIDE_PERMISSION,"Time Clock Multiple Session Override",reason);approval=new services.ManagerApprovalService.ApprovalResult(trusted.approverUserId(),trusted.approverName(),trusted.reason(),null);}ServerTimeClockManager.clockIn(c,approval);}case "LUNCH_START"->ServerTimeClockManager.lunchStart(c);case "LUNCH_END"->ServerTimeClockManager.lunchEnd(c);case "BREAK_START"->ServerTimeClockManager.breakStart(c);case "BREAK_END"->ServerTimeClockManager.breakEnd(c);case "CLOCK_OUT"->ServerTimeClockManager.clockOut(c);default->throw new ApiException(400,"VALIDATION_ERROR","The time-clock action is invalid.",false);}return Map.of("recorded",true);}catch(ServerTimeClockManager.TimeClockException e){throw new ApiException(409,"TIME_CLOCK_REJECTED",e.getMessage(),false);}finally{ServerTimeClockManager.clearRequest();}});}
    private ApiResult payrollDashboard(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"PAYROLL_DASHBOARD");AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());bindTimeClock(s,u);try{return ApiResult.ok(Map.of("dashboard",ServerTimeClockManager.loadPayrollDashboard(c)));}finally{ServerTimeClockManager.clearRequest();}}}
    private ApiResult payrollBonus(RequestContext x)throws Exception{return timeClockCoreMutation(x,"payroll.bonus.v1",(c,d,s,u)->{requireAnyPermission(c,s.userId(),"PAYROLL_DASHBOARD");ServerTimeClockManager.PayrollSummary[] requested=GSON.fromJson(x.body().get("summaries"),ServerTimeClockManager.PayrollSummary[].class);if(requested==null||requested.length==0)throw new ApiException(400,"VALIDATION_ERROR","Select at least one payroll row.",false);java.math.BigDecimal amount=x.body().has("amount")?x.body().get("amount").getAsBigDecimal():null;String reason=optional(x.body(),"reason",2000);bindTimeClock(s,u);try{ServerTimeClockManager.PayrollDashboard dashboard=ServerTimeClockManager.loadPayrollDashboard(c);List<ServerTimeClockManager.PayrollSummary> trusted=new ArrayList<>();for(ServerTimeClockManager.PayrollSummary wanted:requested){ServerTimeClockManager.PayrollSummary found=findPayroll(dashboard,wanted);if(found==null)throw new ApiException(409,"PAYROLL_CHANGED","Payroll changed; refresh and try again.",true);trusted.add(found);}ServerTimeClockManager.addPayrollBonuses(c,trusted,amount,reason);return Map.of("created",trusted.size());}finally{ServerTimeClockManager.clearRequest();}});}
    private ApiResult payrollPay(RequestContext x)throws Exception{return timeClockCoreMutation(x,"payroll.pay.v1",(c,d,s,u)->{requireAnyPermission(c,s.userId(),"PAYROLL_DASHBOARD");ServerTimeClockManager.PayrollSummary wanted=GSON.fromJson(x.body().get("summary"),ServerTimeClockManager.PayrollSummary.class);if(wanted==null)throw new ApiException(400,"VALIDATION_ERROR","Payroll row is required.",false);String method=required(x.body(),"paymentMethod",20),reference=optional(x.body(),"paymentReference",500);bindTimeClock(s,u);try{ServerTimeClockManager.PayrollSummary trusted=findPayroll(ServerTimeClockManager.loadPayrollDashboard(c),wanted);if(trusted==null)throw new ApiException(409,"PAYROLL_CHANGED","Payroll changed; refresh and try again.",true);ServerTimeClockManager.markPayrollPaid(c,trusted,method,reference);return Map.of("paid",true);}finally{ServerTimeClockManager.clearRequest();}});}
    private ApiResult timeClockCoreMutation(RequestContext x,String operation,TimeClockCoreMutation action)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String key=requireIdempotencyKey(x,"A valid idempotency key is required for this time-clock or payroll change."),hash=LanSecurity.sha256(GSON.toJson(x.body()));try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());Map<String,Object>result=action.run(c,d,s,u);completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(ApiException e){c.rollback();throw e;}catch(SQLException e){c.rollback();throw new ApiException(409,"TIME_CLOCK_CHANGE_REJECTED","The change could not be completed.",false);}catch(Exception e){c.rollback();throw e;}finally{ServerTimeClockManager.clearRequest();c.setAutoCommit(true);}}}
    private static ServerTimeClockManager.PayrollSummary findPayroll(ServerTimeClockManager.PayrollDashboard d,ServerTimeClockManager.PayrollSummary w){if(d==null||d.summaries()==null||w==null)return null;for(ServerTimeClockManager.PayrollSummary p:d.summaries())if(p.userId()==w.userId()&&java.util.Objects.equals(p.payPeriodStart(),w.payPeriodStart())&&java.util.Objects.equals(p.payPeriodEnd(),w.payPeriodEnd()))return p;return null;}
    private static void bindTimeClock(SessionPrincipal s,AuthenticatedUser u){ServerTimeClockManager.bindRequest(s.userId(),s.locationId(),u.locationName(),u.locationTimezone(),displayName(u));}
    private ApiResult employeeBadgeData(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);int userId=requiredInt(x.body(),"userId");try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"EMPLOYEE_MANAGEMENT");try{return ApiResult.ok(Map.of("employee",BadgePrintService.loadEmployeeBadgeData(c,userId,s.locationId())));}catch(IllegalArgumentException e){throw new ApiException(404,"EMPLOYEE_NOT_FOUND",e.getMessage(),false);}}}
    private ApiResult employeeBadgePrinted(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String key=requireIdempotencyKey(x,"A valid idempotency key is required to record badge printing."),operation="employees.badge-printed.v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));int userId=requiredInt(x.body(),"userId");try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{requireAnyPermission(c,s.userId(),"EMPLOYEE_MANAGEMENT");Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}BadgePrintService.incrementBadgePrintCount(c,userId,s.locationId());Map<String,Object>result=Map.of("recorded",true);completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(IllegalArgumentException e){c.rollback();throw new ApiException(404,"EMPLOYEE_NOT_FOUND",e.getMessage(),false);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}}
    private ApiResult employeeAdminState(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);Integer userId=x.body().has("userId")&&!x.body().get("userId").isJsonNull()?x.body().get("userId").getAsInt():null;try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"EMPLOYEE_MANAGEMENT");AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());return ApiResult.ok(Map.of("state",LanEmployeeAdminService.state(c,userId,LocalDate.now(java.time.ZoneId.of(u.locationTimezone())))));}}
    private ApiResult employeeAdminMutation(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String action=required(x.body(),"action",30),key=requireIdempotencyKey(x,"A valid idempotency key is required for this employee change."),operation="employees.admin."+action.toLowerCase(java.util.Locale.ROOT)+".v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{requireAnyPermission(c,s.userId(),"EMPLOYEE_MANAGEMENT");Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}AuthenticatedUser actor=loadUser(c,s.userId(),s.locationId());Map<String,Object>result=new LinkedHashMap<>();switch(action){case"ROTATE_BADGE"->result.put("badgeId",LanEmployeeAdminService.rotateBadge(c,requiredInt(x.body(),"userId"),s.userId(),displayName(actor)));case"SAVE_STORES"->{Integer[]ids=GSON.fromJson(x.body().get("locationIds"),Integer[].class);LanEmployeeAdminService.saveStores(c,requiredInt(x.body(),"userId"),ids==null?List.of():List.of(ids));result.put("saved",true);}case"CREATE"->{LanEmployeeAdminService.SaveRequest request=GSON.fromJson(x.body().get("employee"),LanEmployeeAdminService.SaveRequest.class);String authId=employeeAuthCreate(request);try{result.put("userId",LanEmployeeAdminService.create(c,request,authId,s.userId(),displayName(actor)));}catch(Exception e){try{employeeAuthDelete(authId,request.supabaseAccessToken());}catch(Exception ignored){}throw e;}}case"UPDATE"->{int id=requiredInt(x.body(),"userId");LanEmployeeAdminService.SaveRequest request=GSON.fromJson(x.body().get("employee"),LanEmployeeAdminService.SaveRequest.class);String authId=LanEmployeeAdminService.authUserId(c,id);if(authId==null||authId.isBlank()){if(request.password()==null||request.password().isBlank())throw new ApiException(400,"PASSWORD_REQUIRED","Enter a password to create the missing employee Auth account.",false);authId=employeeAuthCreate(request);}else employeeAuthUpdate(authId,request);LanEmployeeAdminService.update(c,id,request,authId,request.password()!=null&&!request.password().isBlank(),s.userId(),displayName(actor),LocalDate.now(java.time.ZoneId.of(actor.locationTimezone())));result.put("userId",id);}case"DEACTIVATE"->{int id=requiredInt(x.body(),"userId");String authId=LanEmployeeAdminService.authUserId(c,id);String token=required(x.body(),"supabaseAccessToken",10000);if(authId!=null&&!authId.isBlank())employeeAuthDelete(authId,token);LanEmployeeAdminService.deactivate(c,id,s.userId(),displayName(actor));result.put("deactivated",true);}default->throw new ApiException(400,"VALIDATION_ERROR","The employee administration action is invalid.",false);}completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}}
    private String employeeAuthCreate(LanEmployeeAdminService.SaveRequest r)throws Exception{if(r==null)throw new ApiException(400,"VALIDATION_ERROR","Employee details are required.",false);JsonObject b=new JsonObject();b.addProperty("email",r.email());b.addProperty("password",r.password());b.addProperty("full_name",r.fullName());b.addProperty("is_active",r.active());JsonObject result=employeeAuthCall("create-employee-auth-user",b,r.supabaseAccessToken());for(String k:List.of("auth_user_id","user_id","id"))if(result.has(k)&&!result.get(k).isJsonNull())return result.get(k).getAsString();throw new ApiException(502,"AUTH_SYNC_FAILED","Supabase created the employee but returned no Auth user ID.",true);}
    private void employeeAuthUpdate(String id,LanEmployeeAdminService.SaveRequest r)throws Exception{JsonObject b=new JsonObject();b.addProperty("auth_user_id",id);b.addProperty("email",r.email());b.addProperty("full_name",r.fullName());b.addProperty("is_active",r.active());if(r.password()!=null&&!r.password().isBlank())b.addProperty("password",r.password());employeeAuthCall("update-employee-auth-user",b,r.supabaseAccessToken());}
    private void employeeAuthDelete(String id,String token)throws Exception{JsonObject b=new JsonObject();b.addProperty("auth_user_id",id);employeeAuthCall("delete-employee-auth-user",b,token);}
    private JsonObject employeeAuthCall(String function,JsonObject body,String token)throws Exception{
        boolean serverCredential=token==null||token.isBlank()||"SERVER_CREDENTIAL".equals(token);
        HttpRequest.Builder request=HttpRequest.newBuilder()
                .uri(URI.create(SupabaseSessionManager.getSupabaseUrl()+"/functions/v1/"+function))
                .timeout(Duration.ofSeconds(20)).header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body),StandardCharsets.UTF_8));
        if(serverCredential)ServerSupabaseCredentials.applyTo(request);
        else request.header("apikey",SupabaseSessionManager.getSupabasePublishableKey())
                .header("Authorization","Bearer "+token);
        HttpResponse<String>response=CLOUD_HTTP.send(request.build(),HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(response.statusCode()<200||response.statusCode()>=300){String message="Employee Auth synchronization failed.";try{JsonObject e=JsonParser.parseString(response.body()).getAsJsonObject();for(String k:List.of("error","message","msg"))if(e.has(k)){message=e.get(k).getAsString();break;}}catch(Exception ignored){}throw new ApiException(502,"AUTH_SYNC_FAILED",message,response.statusCode()>=500);}
        if(response.body()==null||response.body().isBlank())return new JsonObject();
        try{return JsonParser.parseString(response.body()).getAsJsonObject();}catch(Exception e){return new JsonObject();}
    }
    private ApiResult quotationRead(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"QUOTATIONS_ORDERS","CREATE_QUOTATION");AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());bindServerIdentity(c,d,s,u);try{String a=required(x.body(),"action",40);Map<String,Object>r=new LinkedHashMap<>();switch(a){case"LIST_QUOTES"->r.put("rows",ServerQuotationInvoiceViewService.listQuotations());case"LIST_INVOICES"->r.put("rows",ServerQuotationInvoiceViewService.listInvoices());case"LIST_DELIVERIES"->r.put("rows",ServerQuotationInvoiceViewService.listDeliveries());case"LIST_AUDIT"->r.put("rows",ServerQuotationInvoiceViewService.listAudit());case"SEARCH_CUSTOMERS"->r.put("rows",ServerQuotationInvoiceViewService.searchCustomers(optional(x.body(),"search",500)));case"QUOTE_EDIT"->r.put("quotation",ServerQuotationInvoiceViewService.loadQuotationForEdit(requiredLong(x.body(),"quotationId")));case"SEARCH_PRODUCTS"->r.put("rows",ServerQuotationInvoiceViewService.searchProducts(optional(x.body(),"search",500)));case"DELIVERABLE_LINES"->r.put("rows",ServerQuotationInvoiceViewService.listDeliverableLines(requiredLong(x.body(),"invoiceId")));case"INVOICE_FINANCIALS"->r.put("financials",ServerQuotationInvoiceViewService.loadInvoiceFinancials(requiredLong(x.body(),"invoiceId")));default->throw new ApiException(400,"VALIDATION_ERROR","The quotation query is invalid.",false);}return ApiResult.ok(r);}finally{ServerRequestIdentity.clear();}}}
    private ApiResult quotationMutation(RequestContext x)throws Exception{
        requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);
        String a=required(x.body(),"action",40),key=requireIdempotencyKey(x,"A valid idempotency key is required for this quotation or invoice change."),op="quotations."+a.toLowerCase(java.util.Locale.ROOT)+".v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));
        try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{
            requireAnyPermission(c,s.userId(),"QUOTATIONS_ORDERS","CREATE_QUOTATION");Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,op,hash);if(old!=null){c.commit();return ApiResult.ok(old);}
            AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());bindServerIdentity(c,d,s,u);Map<String,Object>r=new LinkedHashMap<>();
            try{switch(a){
                case"CREATE"->{ServerQuotationInvoiceService.QuotationLineInput[]lines=GSON.fromJson(x.body().get("lines"),ServerQuotationInvoiceService.QuotationLineInput[].class);r.put("quotation",ServerQuotationInvoiceService.createQuotation(c,requiredInt(x.body(),"customerId"),optionalDate(x.body(),"validUntil"),optional(x.body(),"notes",5000),trustedQuotationLines(c,d,s,u,null,lines)));}
                case"UPDATE"->{long quotationId=requiredLong(x.body(),"quotationId");ServerQuotationInvoiceService.QuotationLineInput[]lines=GSON.fromJson(x.body().get("lines"),ServerQuotationInvoiceService.QuotationLineInput[].class);r.put("quotation",ServerQuotationInvoiceService.updateDraftQuotation(c,quotationId,requiredInt(x.body(),"customerId"),optionalDate(x.body(),"validUntil"),optional(x.body(),"notes",5000),trustedQuotationLines(c,d,s,u,quotationId,lines)));}
                case"ISSUE"->{ServerQuotationInvoiceService.issueQuotation(c,requiredLong(x.body(),"quotationId"));r.put("updated",true);}
                case"CANCEL"->{ServerQuotationInvoiceService.cancelQuotation(c,requiredLong(x.body(),"quotationId"),optional(x.body(),"reason",2000));r.put("updated",true);}
                case"ACCEPT"->r.put("invoice",ServerQuotationInvoiceService.acceptQuotation(c,requiredLong(x.body(),"quotationId")));
                case"PAYMENT"->r.put("receipt",ServerQuotationInvoiceService.recordPayment(c,requiredLong(x.body(),"invoiceId"),x.body().get("amount").getAsBigDecimal(),required(x.body(),"method",30),optional(x.body(),"reference",500)));
                case"ACCOUNT"->{ServerQuotationInvoiceService.chargeInvoiceToAccount(c,requiredLong(x.body(),"invoiceId"),optional(x.body(),"reason",2000));r.put("updated",true);}
                case"DELIVERY"->{ServerQuotationInvoiceService.DeliveryLineInput[]lines=GSON.fromJson(x.body().get("lines"),ServerQuotationInvoiceService.DeliveryLineInput[].class);r.put("delivery",ServerQuotationInvoiceService.postDelivery(c,requiredLong(x.body(),"invoiceId"),required(x.body(),"deliveryMethod",40),optional(x.body(),"receiverName",500),optional(x.body(),"notes",5000),lines==null?List.of():List.of(lines)));}
                default->throw new ApiException(400,"VALIDATION_ERROR","The quotation change is invalid.",false);
            }}finally{ServerRequestIdentity.clear();}
            completeIdempotency(c,d.deviceId(),key,r);c.commit();return ApiResult.ok(r);
        }catch(Exception e){c.rollback();throw e;}finally{ServerRequestIdentity.clear();c.setAutoCommit(true);}}}
    private List<ServerQuotationInvoiceService.QuotationLineInput> trustedQuotationLines(
            Connection c,DevicePrincipal d,SessionPrincipal s,AuthenticatedUser u,Long quotationId,
            ServerQuotationInvoiceService.QuotationLineInput[] supplied)throws Exception{
        if(supplied==null)return List.of();
        List<ServerQuotationInvoiceService.QuotationLineInput> trusted=new ArrayList<>();
        boolean canChangePrice=hasPermission(c,s.userId(),"CHANGE_SALE_ITEM_PRICE");
        for(ServerQuotationInvoiceService.QuotationLineInput line:supplied){
            if(line==null)continue;
            BigDecimal original=line.unitPrice();
            String reason=null;
            Integer approvedBy=null;
            String approvedName=null;
            if(line.productId()!=null){
                try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(price,0) FROM products WHERE product_id=?")){
                    ps.setInt(1,line.productId());
                    try(ResultSet rs=ps.executeQuery()){
                        if(!rs.next())throw new ApiException(400,"PRODUCT_NOT_FOUND","A quotation product no longer exists.",false);
                        original=utils.CurrencyFormatter.normalize(rs.getBigDecimal(1));
                    }
                }
                BigDecimal entered=utils.CurrencyFormatter.normalize(line.unitPrice());
                if(entered.compareTo(original)!=0&&!canChangePrice){
                    reason=line.priceOverrideReason()==null?"":line.priceOverrideReason().trim();
                    if(reason.isBlank())throw new ApiException(400,"VALIDATION_ERROR","A quotation price override reason is required.",false);
                    LanSalesService.Approval approval=existingQuotationPriceApproval(
                            c,quotationId,line.productId(),entered,original,reason);
                    if(approval==null){
                        approval=consumeApproval(c,d,s,line.priceOverrideApprovalToken(),
                                "CHANGE_SALE_ITEM_PRICE","Quotation Price Override",reason);
                    }
                    approvedBy=approval.approverUserId();
                    approvedName=approval.approverName();
                }
            }
            trusted.add(new ServerQuotationInvoiceService.QuotationLineInput(
                    line.productId(),line.itemName(),line.sku(),line.quantity(),line.unitPrice(),original,
                    line.discountPercent(),line.deliveryMethod(),line.notes(),reason,approvedBy,approvedName,null));
        }
        return trusted;
    }
    private LanSalesService.Approval existingQuotationPriceApproval(
            Connection c,Long quotationId,Integer productId,BigDecimal entered,BigDecimal original,
            String reason)throws SQLException{
        if(quotationId==null)return null;
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT price_override_by_user_id,price_override_by_name
                FROM quotation_lines
                WHERE quotation_id=? AND product_id=? AND unit_price=? AND original_unit_price=?
                  AND COALESCE(price_override_reason,'')=?
                  AND price_override_by_user_id IS NOT NULL
                LIMIT 1
                """)){
            ps.setLong(1,quotationId);ps.setInt(2,productId);ps.setBigDecimal(3,entered);
            ps.setBigDecimal(4,original);ps.setString(5,reason);
            try(ResultSet rs=ps.executeQuery()){
                return rs.next()?new LanSalesService.Approval(rs.getInt(1),rs.getString(2),reason):null;
            }
        }
    }
    private void bindServerIdentity(Connection c,DevicePrincipal d,SessionPrincipal s,AuthenticatedUser u)throws SQLException{ServerRequestIdentity.bind(s.userId(),s.locationId(),u.locationName(),displayName(u),d.deviceId().toString(),loadDeviceDisplayName(c,d.deviceId()));}
    private static LocalDate optionalDate(JsonObject b,String key)throws ApiException{String v=optional(b,key,40);if(v==null||v.isBlank())return null;try{return LocalDate.parse(v);}catch(Exception e){throw new ApiException(400,"VALIDATION_ERROR",key+" is invalid.",false);}}
    private ApiResult quotationDocument(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"QUOTATIONS_ORDERS","CREATE_QUOTATION");String type=required(x.body(),"type",30);long id=requiredLong(x.body(),"documentId");String value=switch(type){case"QUOTATION"->Receipt.ServerQuotationInvoiceDocumentBuilder.buildQuotation(id);case"INVOICE"->Receipt.ServerQuotationInvoiceDocumentBuilder.buildInvoice(id);case"DELIVERY"->Receipt.ServerQuotationInvoiceDocumentBuilder.buildDelivery(id);default->throw new ApiException(400,"VALIDATION_ERROR","The document type is invalid.",false);};return ApiResult.ok(Map.of("text",value));}}
    private ApiResult notificationList(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){bindNotifications(c,d,s);try{return ApiResult.ok(Map.of("notifications",ServerNotificationService.loadNotifications(c)));}finally{ServerNotificationService.clearRequest();}}}
    private ApiResult notificationUpdate(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String key=requireIdempotencyKey(x,"A valid idempotency key is required for this notification change."),operation="notifications.update.v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));String action=required(x.body(),"action",20).toUpperCase(java.util.Locale.ROOT),notificationKey=required(x.body(),"notificationKey",500);try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}bindNotifications(c,d,s);try{switch(action){case "READ"->ServerNotificationService.markRead(c,notificationKey);case "SNOOZE"->ServerNotificationService.snooze(c,notificationKey,x.body().has("minutes")?x.body().get("minutes").getAsInt():60);case "CLEAR"->ServerNotificationService.clear(c,notificationKey);case "SEEN"->{models.AppNotification n=GSON.fromJson(x.body().get("notification"),models.AppNotification.class);if(n==null||!notificationKey.equals(n.notificationKey()))throw new ApiException(400,"VALIDATION_ERROR","Notification details are invalid.",false);ServerNotificationService.markSeen(c,n);}default->throw new ApiException(400,"VALIDATION_ERROR","The notification action is invalid.",false);}}finally{ServerNotificationService.clearRequest();}Map<String,Object>result=Map.of("updated",true);completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(Exception e){c.rollback();throw e;}finally{ServerNotificationService.clearRequest();c.setAutoCommit(true);}}}
    private void bindNotifications(Connection c,DevicePrincipal d,SessionPrincipal s)throws Exception{ServerNotificationService.bindRequest(s.userId(),s.locationId(),d.deviceId().toString(),Set.copyOf(loadPermissions(c,s.userId())));}
    private ApiResult scheduleLocations(RequestContext x)throws Exception{return scheduleRead(x,(c,d,s)->Map.of("locations",ServerEmployeeScheduleService.loadAccessibleLocations()));}
    private ApiResult scheduleEmployees(RequestContext x)throws Exception{return scheduleRead(x,(c,d,s)->Map.of("employees",ServerEmployeeScheduleService.loadActiveEmployees(requiredInt(x.body(),"locationId"))));}
    private ApiResult scheduleShifts(RequestContext x)throws Exception{return scheduleRead(x,(c,d,s)->Map.of("shifts",ServerEmployeeScheduleService.loadShifts(requiredInt(x.body(),"locationId"),x.body().has("includeInactive")&&x.body().get("includeInactive").getAsBoolean())));}
    private ApiResult scheduleRange(RequestContext x)throws Exception{return scheduleRead(x,(c,d,s)->{int location=requiredInt(x.body(),"locationId");java.time.LocalDate start=date(x.body(),"start"),end=date(x.body(),"end");Map<java.time.LocalDate,List<ServerEmployeeScheduleService.Assignment>>m=ServerEmployeeScheduleService.loadRange(location,start,end);List<Map<String,Object>>days=new ArrayList<>();for(var e:m.entrySet())days.add(Map.of("date",e.getKey(),"assignments",e.getValue()));return Map.of("days",days);});}
    private ApiResult scheduleHolidays(RequestContext x)throws Exception{return scheduleRead(x,(c,d,s)->{java.time.LocalDate start=date(x.body(),"start"),end=date(x.body(),"end");boolean clock=x.body().has("timeClock")&&x.body().get("timeClock").getAsBoolean();Map<java.time.LocalDate,ServerEmployeeScheduleService.Holiday>m=clock?ServerEmployeeScheduleService.loadCurrentStoreHolidaysForTimeClock(start,end):ServerEmployeeScheduleService.loadHolidays(start,end);return Map.of("holidays",m.values());});}
    private ApiResult scheduleSnapshot(RequestContext x)throws Exception{return scheduleRead(x,(c,d,s)->{int location=requiredInt(x.body(),"locationId");java.time.LocalDate start=date(x.body(),"start"),end=date(x.body(),"end");Map<java.time.LocalDate,List<ServerEmployeeScheduleService.Assignment>>assignments=ServerEmployeeScheduleService.loadRange(location,start,end);Map<java.time.LocalDate,ServerEmployeeScheduleService.Holiday>holidays=ServerEmployeeScheduleService.loadHolidays(start,end);List<Map<String,Object>>days=new ArrayList<>();for(var entry:assignments.entrySet())days.add(Map.of("date",entry.getKey(),"assignments",entry.getValue()));return Map.of("days",days,"holidays",holidays.values());});}
    private ApiResult scheduleRead(RequestContext x,ScheduleOperation action)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){bindSchedule(c,s);try{return ApiResult.ok(action.run(c,d,s));}finally{clearScheduleContext();}}}
    private ApiResult scheduleUpdate(RequestContext x)throws Exception {
        return scheduleMutation(x,"schedule.update.v1",(c,d,s)-> {
            String action=required(x.body(),"action",40).toUpperCase(java.util.Locale.ROOT);
            Map<String,Object>result=new LinkedHashMap<>();
            switch(action) {
                case "SAVE_HOLIDAY" -> ServerEmployeeScheduleService.saveHoliday(c,date(x.body(),"date"),optional(x.body(),"name",300));
                case "REMOVE_HOLIDAY" -> ServerEmployeeScheduleService.removeHoliday(c,date(x.body(),"date"));
                case "ADD_EMPLOYEES" -> {
                    int location=requiredInt(x.body(),"locationId");
                    ServerEmployeeScheduleService.Employee[] employees=GSON.fromJson(x.body().get("employees"),ServerEmployeeScheduleService.Employee[].class);
                    ServerEmployeeScheduleService.addEmployees(c,location,date(x.body(),"start"),employees==null?List.of():List.of(employees),uuid(x.body(),"shiftId",true),time(x.body(),"lunchStart"));
                }
                case "UPDATE_ASSIGNMENT" -> ServerEmployeeScheduleService.updateAssignment(c,requiredInt(x.body(),"locationId"),requiredInt(x.body(),"userId"),date(x.body(),"date"),uuid(x.body(),"shiftId",true),time(x.body(),"lunchStart"));
                case "SAVE_SHIFT" -> {
                    ServerEmployeeScheduleService.Shift shift=ServerEmployeeScheduleService.saveShift(c,
                            requiredInt(x.body(),"locationId"),uuid(x.body(),"shiftId",false),required(x.body(),"name",300),
                            time(x.body(),"startTime"),time(x.body(),"endTime"),
                            !x.body().has("active")||x.body().get("active").getAsBoolean(),
                            x.body().has("displayOrder")?x.body().get("displayOrder").getAsInt():0,
                            x.body().has("propagate")&&x.body().get("propagate").getAsBoolean());
                    result.put("shift",shift);
                }
                case "SHIFT_ORDER" -> {
                    UUID[] ids=GSON.fromJson(x.body().get("shiftIds"),UUID[].class);
                    ServerEmployeeScheduleService.updateShiftOrder(c,requiredInt(x.body(),"locationId"),ids==null?List.of():List.of(ids));
                }
                case "REMOVE_EMPLOYEE" -> ServerEmployeeScheduleService.removeEmployee(c,requiredInt(x.body(),"locationId"),requiredInt(x.body(),"userId"),date(x.body(),"date"));
                case "CLEAR" -> result.put("removed",ServerEmployeeScheduleService.clearSchedule(c,requiredInt(x.body(),"locationId"),date(x.body(),"start"),date(x.body(),"end")));
                default -> throw new ApiException(400,"VALIDATION_ERROR","The schedule action is invalid.",false);
            }
            result.putIfAbsent("updated",true);
            return result;
        });
    }
    private ApiResult customCatalogAdminState(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){requireAnyPermission(c,s.userId(),"MANAGE_CUSTOM_ORDER_ITEMS","CUSTOM_ORDER_ITEMS","MANAGE_CUSTOM_ORDERS");return ApiResult.ok(Map.of("state",LanCustomOrderCatalogAdminService.load(c)));}}
    private ApiResult customCatalogAdminMutation(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String action=required(x.body(),"action",40),key=requireIdempotencyKey(x,"A valid idempotency key is required for this custom catalog change."),op="custom-orders.admin."+action.toLowerCase(java.util.Locale.ROOT)+".v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{requireAnyPermission(c,s.userId(),"MANAGE_CUSTOM_ORDER_ITEMS","CUSTOM_ORDER_ITEMS","MANAGE_CUSTOM_ORDERS");Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,op,hash);if(old!=null){c.commit();return ApiResult.ok(old);}long id=LanCustomOrderCatalogAdminService.mutate(c,action,x.body());Map<String,Object>result=Map.of("recordId",id);completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}}
    private ApiResult customOrderWorkflowRead(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){String action=required(x.body(),"action",40);requireAnyPermission(c,s.userId(),"CREATE_CUSTOM_ORDER","MANAGE_CUSTOM_ORDERS","CUSTOM_ORDER_LOOKUP","CUSTOM_ORDER_OVERRIDES");Map<String,Object>r=new LinkedHashMap<>();switch(action){case"ALL"->r.put("orders",LanCustomOrderWorkflowService.orders(c,s.locationId(),null,"",500));case"MINE"->r.put("orders",LanCustomOrderWorkflowService.orders(c,s.locationId(),s.userId(),"",500));case"LOOKUP"->r.put("orders",LanCustomOrderWorkflowService.orders(c,s.locationId(),null,optional(x.body(),"search",500),100));case"RETURNS"->r.put("lines",LanCustomOrderWorkflowService.returnLines(c,requiredLong(x.body(),"orderId"),s.locationId()));case"DELIVERIES"->r.put("lines",LanCustomOrderWorkflowService.deliveryLines(c,requiredLong(x.body(),"orderId"),s.locationId()));case"PRODUCTION"->r.put("lines",LanCustomOrderWorkflowService.productionLines(c,requiredLong(x.body(),"orderId"),s.locationId()));case"DETAILS"->r.put("details",LanCustomOrderWorkflowService.details(c,requiredLong(x.body(),"orderId"),s.locationId()));default->throw new ApiException(400,"VALIDATION_ERROR","The custom order lookup is invalid.",false);}return ApiResult.ok(r);}}
    private ApiResult customOrderWorkflowMutation(RequestContext x)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String action=required(x.body(),"action",40),key=requireIdempotencyKey(x,"A valid idempotency key is required for this custom order change."),op="custom-orders.workflow."+action.toLowerCase(java.util.Locale.ROOT)+".v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,op,hash);if(old!=null){c.commit();return ApiResult.ok(old);}AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());String deviceName=loadDeviceDisplayName(c,d.deviceId());long orderId=requiredLong(x.body(),"orderId");switch(action){case"PAYMENT"->{requireAnyPermission(c,s.userId(),"CREATE_CUSTOM_ORDER","MANAGE_CUSTOM_ORDERS","CUSTOM_ORDER_PAYMENTS","CUSTOM_ORDER_OVERRIDES");LanCustomOrderWorkflowService.payment(c,orderId,x.body().get("amount").getAsBigDecimal(),required(x.body(),"method",30),optional(x.body(),"reference",500),s.locationId(),s.userId(),displayName(u),d.deviceId().toString(),deviceName);}case"PRODUCTION"->{requireAnyPermission(c,s.userId(),"CUSTOM_ORDER_PRODUCTION_STEPS","CUSTOM_ORDER_OVERRIDES");Long[]ids=GSON.fromJson(x.body().get("lineIds"),Long[].class);LanCustomOrderWorkflowService.production(c,orderId,ids==null?List.of():List.of(ids),required(x.body(),"status",40),optional(x.body(),"notes",3000),s.locationId(),s.userId(),displayName(u),d.deviceId().toString(),deviceName);}case"DELIVER_LINES"->{requireAnyPermission(c,s.userId(),"CUSTOM_ORDER_LINE_DELIVERY","CUSTOM_ORDER_OVERRIDES");Long[]ids=GSON.fromJson(x.body().get("lineIds"),Long[].class);LanCustomOrderWorkflowService.deliver(c,orderId,ids==null?List.of():List.of(ids),optional(x.body(),"notes",3000),s.locationId(),s.userId(),displayName(u),d.deviceId().toString(),deviceName);}case"DELIVER_ORDER"->{requireAnyPermission(c,s.userId(),"CUSTOM_ORDER_LINE_DELIVERY","MANAGE_CUSTOM_ORDERS","CUSTOM_ORDER_OVERRIDES");LanCustomOrderWorkflowService.markOrderDelivered(c,orderId,s.locationId(),s.userId(),displayName(u),d.deviceId().toString(),deviceName);}case"LINE_RETURN"->{requireAnyPermission(c,s.userId(),"CUSTOM_ORDER_LINE_RETURNS","CUSTOM_ORDER_REFUNDS","CUSTOM_ORDER_OVERRIDES");LanCustomOrderWorkflowService.ReturnRequest[]requests=GSON.fromJson(x.body().get("returns"),LanCustomOrderWorkflowService.ReturnRequest[].class);BigDecimal total=BigDecimal.ZERO;if(requests!=null)for(var r:requests)if(r.amount()!=null)total=total.add(r.amount());BigDecimal limit=BigDecimal.ZERO;try(PreparedStatement ps=c.prepareStatement("SELECT COALESCE(custom_order_refund_approval_limit,0) FROM company_customization WHERE location_id=?")){ps.setInt(1,s.locationId());try(ResultSet rs=ps.executeQuery()){if(rs.next())limit=rs.getBigDecimal(1);}}if(limit!=null&&limit.signum()>0&&total.compareTo(limit)>0&&!hasAnyPermission(c,s.userId(),"CUSTOM_ORDER_REFUND_APPROVAL","CUSTOM_ORDER_OVERRIDES")){String reason=required(x.body(),"approvalReason",2000);consumeApproval(c,d,s,optional(x.body(),"approvalToken",512),"CUSTOM_ORDER_REFUND_APPROVAL","Custom Order Refund Approval",reason);}LanCustomOrderWorkflowService.lineReturn(c,orderId,requests==null?List.of():List.of(requests),required(x.body(),"method",30),optional(x.body(),"reference",500),required(x.body(),"reason",3000),s.locationId(),s.userId(),displayName(u),d.deviceId().toString(),deviceName);}default->throw new ApiException(400,"VALIDATION_ERROR","The custom order change is invalid.",false);}Map<String,Object>result=Map.of("updated",true);completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}}

    private ApiResult companyCustomizationRead(RequestContext x) throws Exception {
        requireMethod(x.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(x.exchange());
        SessionPrincipal session = authenticateSession(x.exchange(), device, true);
        String action = required(x.body(), "action", 40);
        int locationId = x.body().has("locationId") ? requiredInt(x.body(), "locationId") : session.locationId();
        if (locationId != session.locationId()) {
            throw new ApiException(403, "STORE_SCOPE_DENIED", "Configuration can only be read for the selected store.", false);
        }
        try (Connection connection = DB.getConnection()) {
            AuthenticatedUser user = loadUser(connection, session.userId(), session.locationId());
            bindServerIdentity(connection, device, session, user);
            ServerRequestIdentity.bindSupabaseAccessToken(optional(x.body(), "supabaseAccessToken", 16384));
            try {
                Object settings = switch (action) {
                    case "ALL_SETTINGS" -> {
                        Map<String, Object> all = new LinkedHashMap<>();
                        all.put("receipt", ServerCompanyCustomizationRepository.loadReceiptSettings());
                        all.put("saleSafety", ServerCompanyCustomizationRepository.loadSaleSafetySettings());
                        all.put("customOrder", ServerCompanyCustomizationRepository.loadCustomOrderSettings());
                        all.put("customOrderSlip", ServerCompanyCustomizationRepository.loadCustomOrderSlipSettings());
                        all.put("quotationInvoice", ServerCompanyCustomizationRepository.loadQuotationInvoicePrintSettings());
                        all.put("badgeTemplate", ServerCompanyCustomizationRepository.loadBadgeTemplateSettings());
                        all.put("badgeSecurity", ServerCompanyCustomizationRepository.loadBadgeSecuritySettings());
                        all.put("priceTags", ServerCompanyCustomizationRepository.loadPriceTagTemplateSettings());
                        yield all;
                    }
                    case "RECEIPT" -> ServerCompanyCustomizationRepository.loadReceiptSettings();
                    case "CUSTOM_ORDER" -> ServerCompanyCustomizationRepository.loadCustomOrderSettings();
                    case "SALE_SAFETY" -> ServerCompanyCustomizationRepository.loadSaleSafetySettings();
                    case "CUSTOM_ORDER_SLIP" -> ServerCompanyCustomizationRepository.loadCustomOrderSlipSettings();
                    case "QUOTATION_INVOICE" -> ServerCompanyCustomizationRepository.loadQuotationInvoicePrintSettings();
                    case "BADGE_TEMPLATE" -> ServerCompanyCustomizationRepository.loadBadgeTemplateSettings();
                    case "BADGE_SECURITY" -> ServerCompanyCustomizationRepository.loadBadgeSecuritySettings();
                    case "PRICE_TAGS" -> ServerCompanyCustomizationRepository.loadPriceTagTemplateSettings();
                    case "CHANGE_BASKET_TARGET" -> ServerCompanyCustomizationRepository.loadChangeBasketTargetAmount(locationId);
                    case "UPLOADED_IMAGES" -> ServerCompanyCustomizationRepository.listUploadedCompanyLogos();
                    default -> throw new ApiException(400, "VALIDATION_ERROR", "The configuration query is invalid.", false);
                };
                return ApiResult.ok(Map.of("settings", settings));
            } finally {
                ServerRequestIdentity.clear();
            }
        }
    }

    private ApiResult companyCustomizationMutation(RequestContext x) throws Exception {
        requireMethod(x.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(x.exchange());
        SessionPrincipal session = authenticateSession(x.exchange(), device, true);
        String action = required(x.body(), "action", 40);
        String key = requireIdempotencyKey(x, "A valid idempotency key is required for this configuration change.");
        String operation = "configuration." + action.toLowerCase(java.util.Locale.ROOT) + ".v1";
        String hash = LanSecurity.sha256(GSON.toJson(x.body()));
        int locationId = x.body().has("locationId") ? requiredInt(x.body(), "locationId") : session.locationId();
        if (locationId != session.locationId()) {
            throw new ApiException(403, "STORE_SCOPE_DENIED", "Configuration can only be changed for the selected store.", false);
        }
        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAnyPermission(connection, session.userId(), "COMPANY_PREFERENCES", "COMPANY_CUSTOMIZATION");
                Map<String,Object> prior = loadIdempotentResult(connection, device.deviceId(), key, operation, hash);
                if (prior != null) {
                    connection.commit();
                    return ApiResult.ok(prior);
                }
                AuthenticatedUser user = loadUser(connection, session.userId(), session.locationId());
                bindServerIdentity(connection, device, session, user);
                ServerRequestIdentity.bindSupabaseAccessToken(optional(x.body(), "supabaseAccessToken", 16384));
                Map<String,Object> result = new LinkedHashMap<>();
                try {
                    if (!x.body().has("settings") || x.body().get("settings").isJsonNull()) {
                        throw new ApiException(400, "VALIDATION_ERROR", "Configuration settings are required.", false);
                    }
                    switch (action) {
                        case "RECEIPT" -> ServerCompanyCustomizationRepository.saveReceiptSettings(
                                GSON.fromJson(x.body().get("settings"), ServerCompanyCustomizationRepository.ReceiptSettings.class));
                        case "CUSTOM_ORDER" -> ServerCompanyCustomizationRepository.saveCustomOrderSettings(
                                GSON.fromJson(x.body().get("settings"), ServerCompanyCustomizationRepository.CustomOrderSettings.class));
                        case "SALE_SAFETY" -> ServerCompanyCustomizationRepository.saveSaleSafetySettings(
                                GSON.fromJson(x.body().get("settings"), ServerCompanyCustomizationRepository.SaleSafetySettings.class));
                        case "CUSTOM_ORDER_SLIP" -> ServerCompanyCustomizationRepository.saveCustomOrderSlipSettings(
                                GSON.fromJson(x.body().get("settings"), ServerCompanyCustomizationRepository.CustomOrderSlipSettings.class));
                        case "QUOTATION_INVOICE" -> ServerCompanyCustomizationRepository.saveQuotationInvoicePrintSettings(
                                GSON.fromJson(x.body().get("settings"), ServerCompanyCustomizationRepository.QuotationInvoicePrintSettings.class));
                        case "BADGE_TEMPLATE" -> ServerCompanyCustomizationRepository.saveBadgeTemplateSettings(
                                GSON.fromJson(x.body().get("settings"), ServerCompanyCustomizationRepository.BadgeTemplateSettings.class));
                        case "BADGE_SECURITY" -> ServerCompanyCustomizationRepository.saveBadgeSecuritySettings(
                                GSON.fromJson(x.body().get("settings"), ServerCompanyCustomizationRepository.BadgeSecuritySettings.class));
                        case "PRICE_TAGS" -> {
                            ServerCompanyCustomizationRepository.PriceTagTemplateSettings[] values = GSON.fromJson(
                                    x.body().get("settings"), ServerCompanyCustomizationRepository.PriceTagTemplateSettings[].class);
                            ServerCompanyCustomizationRepository.savePriceTagTemplateSettings(values == null ? List.of() : List.of(values));
                        }
                        case "CHANGE_BASKET_TARGET" -> ServerCompanyCustomizationRepository.saveChangeBasketTargetAmount(
                                locationId, x.body().get("settings").getAsBigDecimal());
                        case "COMPANY_LOGO", "BADGE_TEMPLATE_IMAGE" -> result.put("path", saveCustomizationImage(action, x.body().getAsJsonObject("settings")));
                        default -> throw new ApiException(400, "VALIDATION_ERROR", "The configuration change is invalid.", false);
                    }
                    result.putIfAbsent("saved", true);
                } finally {
                    ServerRequestIdentity.clear();
                }
                completeIdempotency(connection, device.deviceId(), key, result);
                connection.commit();
                return ApiResult.ok(result);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                ServerRequestIdentity.clear();
                connection.setAutoCommit(true);
            }
        }
    }

    private String saveCustomizationImage(String action, JsonObject settings) throws Exception {
        String fileName = required(settings, "fileName", 255);
        String encoded = required(settings, "contentBase64", MAX_IMAGE_BODY_BYTES);
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(400, "VALIDATION_ERROR", "The uploaded image is invalid.", false);
        }
        String suffix = ".bin";
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0 && dot < fileName.length() - 1) {
            String candidate = fileName.substring(dot).toLowerCase(java.util.Locale.ROOT);
            if (candidate.matches("\\.[a-z0-9]{1,8}")) suffix = candidate;
        }
        Path temporary = Files.createTempFile("smartstock-company-image-", suffix);
        try {
            Files.write(temporary, bytes);
            return "COMPANY_LOGO".equals(action)
                    ? ServerCompanyCustomizationRepository.uploadCompanyLogo(temporary)
                    : ServerCompanyCustomizationRepository.uploadBadgeTemplateImage(temporary);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private ApiResult balanceSheetRead(RequestContext x) throws Exception {
        requireMethod(x.exchange(), "POST");
        DevicePrincipal device=authenticateDevice(x.exchange());
        SessionPrincipal session=authenticateSession(x.exchange(),device,true);
        String action=required(x.body(),"action",40);
        try(Connection connection=DB.getConnection()) {
            requireAnyPermission(connection,session.userId(),"BALANCE_SHEET");
            AuthenticatedUser user=loadUser(connection,session.userId(),session.locationId());
            bindServerIdentity(connection,device,session,user);
            try {
                Map<String,Object> result=new LinkedHashMap<>();
                LocalDate from=x.body().has("from")?date(x.body(),"from"):null;
                LocalDate to=x.body().has("to")?date(x.body(),"to"):null;
                switch(action) {
                    case "LOAD" -> result.put("sheet",ServerBalanceSheetService.loadBalanceSheet(from,to,
                            required(x.body(),"storeZoneId",100),longList(x.body(),"cashDrawerSessionIds")));
                    case "SUBMISSIONS" -> result.put("rows",ServerBalanceSheetService.listSubmissions());
                    case "SUBMISSION" -> result.put("sheet",ServerBalanceSheetService.loadSubmission(requiredLong(x.body(),"submissionId")));
                    case "EDIT_CONTEXT" -> {
                        requireAnyPermission(connection,session.userId(),"EDIT_BALANCE_SHEET");
                        result.put("editContext",ServerBalanceSheetService.loadEditContext(connection,requiredLong(x.body(),"submissionId")));
                    }
                    case "REVISION_HISTORY" -> result.put("rows",ServerBalanceSheetService.loadRevisionHistory(connection,requiredLong(x.body(),"submissionId")));
                    case "DRAW_RANGES" -> result.put("rows",ServerBalanceSheetService.findDrawSessionRanges(
                            required(x.body(),"storeZoneId",100),from,to));
                    case "DELETABLE_EXPENSES" -> result.put("rows",ServerBalanceSheetService.listDeletableExpenses(from,to,optional(x.body(),"status",40)));
                    case "DELETABLE_OTHER_INCOME" -> result.put("rows",ServerBalanceSheetService.listDeletableOtherIncome(from,to));
                    case "PENDING_CHEQUES" -> result.put("rows",ServerBalanceSheetService.listPendingChequeDeposits());
                    case "UNPAID_PAYABLES" -> result.put("rows",ServerBalanceSheetService.listUnpaidPayables(from,to));
                    default -> throw new ApiException(400,"VALIDATION_ERROR","The balance-sheet query is invalid.",false);
                }
                return ApiResult.ok(result);
            } finally { ServerRequestIdentity.clear(); }
        }
    }

    private ApiResult balanceSheetMutation(RequestContext x) throws Exception {
        requireMethod(x.exchange(),"POST");
        DevicePrincipal device=authenticateDevice(x.exchange());
        SessionPrincipal session=authenticateSession(x.exchange(),device,true);
        String action=required(x.body(),"action",40),key=requireIdempotencyKey(x,"A valid idempotency key is required for this accounting change."),
                operation="accounting.balance-sheet."+action.toLowerCase(java.util.Locale.ROOT)+".v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));
        try(Connection connection=DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireAnyPermission(connection,session.userId(),"BALANCE_SHEET");
                Map<String,Object> prior=loadIdempotentResult(connection,device.deviceId(),key,operation,hash);
                if(prior!=null){connection.commit();return ApiResult.ok(prior);}
                AuthenticatedUser user=loadUser(connection,session.userId(),session.locationId());
                bindServerIdentity(connection,device,session,user);
                Map<String,Object> result=new LinkedHashMap<>();
                try {
                    switch(action) {
                        case "ADD_EXPENSE" -> ServerBalanceSheetService.addManualExpense(connection,GSON.fromJson(x.body().get("expense"),ServerBalanceSheetService.ExpenseEntry.class));
                        case "ADD_OTHER_INCOME" -> ServerBalanceSheetService.addOtherIncome(connection,GSON.fromJson(x.body().get("income"),ServerBalanceSheetService.OtherIncomeEntry.class));
                        case "DELETE_OTHER_INCOME" -> ServerBalanceSheetService.deleteOtherIncome(connection,requiredLong(x.body(),"otherIncomeId"),date(x.body(),"from"),date(x.body(),"to"));
                        case "DELETE_EXPENSE" -> ServerBalanceSheetService.deleteManualExpense(connection,requiredLong(x.body(),"expenseId"),date(x.body(),"from"),date(x.body(),"to"),optional(x.body(),"status",40));
                        case "DEPOSIT_CHEQUE" -> ServerBalanceSheetService.markChequeDeposited(connection,GSON.fromJson(x.body().get("cheque"),ServerBalanceSheetService.ChequeDepositOption.class),optional(x.body(),"notes",3000));
                        case "PAY_PAYABLE" -> ServerBalanceSheetService.recordPayablePayment(connection,requiredLong(x.body(),"expenseId"),date(x.body(),"paymentDate"),
                                x.body().get("paymentAmount").getAsBigDecimal(),required(x.body(),"paymentMethod",40),optional(x.body(),"paymentReference",500));
                        case "SUBMIT" -> result.put("submissionId",ServerBalanceSheetService.submitBalanceSheet(connection,date(x.body(),"from"),date(x.body(),"to"),
                                required(x.body(),"storeZoneId",100),optional(x.body(),"notes",5000),longList(x.body(),"cashDrawerSessionIds")));
                        case "REVISE" -> {
                            requireAnyPermission(connection,session.userId(),"EDIT_BALANCE_SHEET");
                            ServerBalanceSheetService.EditResult revised=ServerBalanceSheetService.reviseSubmission(connection,
                                    GSON.fromJson(x.body().get("edit"),ServerBalanceSheetService.EditRequest.class));
                            result.put("revisionNo",revised.revisionNo());
                            result.put("sheet",revised.sheet());
                        }
                        case "SET_BALANCE_BF" -> {
                            if (!"ADMIN".equalsIgnoreCase(user.role())) {
                                throw new ApiException(403,"ADMIN_REQUIRED","Only an administrator can set or edit Balance B/F.",false);
                            }
                            BigDecimal amount;
                            try {
                                amount=x.body().get("amount").getAsBigDecimal();
                            } catch(Exception ex) {
                                throw new ApiException(400,"VALIDATION_ERROR","A valid Balance B/F amount is required.",false);
                            }
                            ServerBalanceSheetService.setBalanceBf(connection,date(x.body(),"periodStart"),amount);
                        }
                        default -> throw new ApiException(400,"VALIDATION_ERROR","The balance-sheet change is invalid.",false);
                    }
                    result.putIfAbsent("updated",true);
                } finally { ServerRequestIdentity.clear(); }
                completeIdempotency(connection,device.deviceId(),key,result);
                connection.commit();
                return ApiResult.ok(result);
            } catch(Exception ex){connection.rollback();throw ex;}
            finally{ServerRequestIdentity.clear();connection.setAutoCommit(true);}
        }
    }

    private ApiResult queueEmail(RequestContext x) throws Exception {
        requireMethod(x.exchange(),"POST");
        DevicePrincipal device=authenticateDevice(x.exchange());
        SessionPrincipal session=authenticateSession(x.exchange(),device,true);
        String action=required(x.body(),"action",50),key=requireIdempotencyKey(x,"A valid idempotency key is required to queue email."),
                operation="email.queue."+action.toLowerCase(java.util.Locale.ROOT)+".v1",hash=LanSecurity.sha256(GSON.toJson(x.body()));
        try(Connection connection=DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireEmailPermission(connection,session.userId(),action);
                Map<String,Object> prior=loadIdempotentResult(connection,device.deviceId(),key,operation,hash);
                if(prior!=null){connection.commit();return ApiResult.ok(prior);}
                AuthenticatedUser user=loadUser(connection,session.userId(),session.locationId());
                bindServerIdentity(connection,device,session,user);
                String emailAccessToken=optional(x.body(),"supabaseAccessToken",16384);
                ServerRequestIdentity.bindSupabaseAccessToken(emailAccessToken);
                ServerEmailOutboxService.bindRequestConnection(connection);
                ServerEmailOutboxService.QueueResult queued;
                try {
                    String recipient=optional(x.body(),"recipient",500);
                    boolean requireEnabled=x.body().has("requireEnabled")&&x.body().get("requireEnabled").getAsBoolean();
                    queued=switch(action) {
                        case "SALE_RECEIPT" -> ServerEmailOutboxService.queueSaleReceipt(requiredInt(x.body(),"saleId"),recipient,requireEnabled);
                        case "CUSTOM_ORDER_CONFIRMATION" -> ServerEmailOutboxService.queueCustomOrderConfirmation(required(x.body(),"orderNumber",100),requireEnabled);
                        case "ACCOUNT_PAYMENT_RECEIPT" -> ServerEmailOutboxService.queueAccountPaymentReceipt(
                                GSON.fromJson(x.body().get("receipt"),Receipt.AccountPaymentReceiptData.class),recipient,requireEnabled);
                        case "QUOTATION" -> ServerEmailOutboxService.queueQuotation(requiredLong(x.body(),"documentId"),recipient,requireEnabled);
                        case "INVOICE" -> ServerEmailOutboxService.queueInvoice(requiredLong(x.body(),"documentId"),recipient,requireEnabled);
                        case "DELIVERY_BILL" -> ServerEmailOutboxService.queueDeliveryBill(requiredLong(x.body(),"documentId"),recipient,requireEnabled);
                        case "BALANCE_SHEET" -> ServerEmailOutboxService.queueBalanceSheetSubmission(requiredLong(x.body(),"submissionId"),
                                x.body().has("revisionNo")?x.body().get("revisionNo").getAsInt():0);
                        default -> throw new ApiException(400,"VALIDATION_ERROR","The email document type is invalid.",false);
                    };
                } finally { ServerEmailOutboxService.clearRequestConnection(); ServerRequestIdentity.clear(); }
                Map<String,Object> result=Map.of("result",queued);
                completeIdempotency(connection,device.deviceId(),key,result);
                connection.commit();
                if(queued.queued())ServerEmailOutboxService.processOneAsync(queued.outboxId(),emailAccessToken);
                return ApiResult.ok(result);
            } catch(Exception ex){connection.rollback();throw ex;}
            finally{ServerEmailOutboxService.clearRequestConnection();ServerRequestIdentity.clear();connection.setAutoCommit(true);}
        }
    }

    private void requireEmailPermission(Connection connection,int userId,String action)throws Exception{
        switch(action){
            case "SALE_RECEIPT" -> requireAnyPermission(connection,userId,"MAKE_SALE","VIEW_SALES","PROCESS_RETURNS");
            case "CUSTOM_ORDER_CONFIRMATION" -> requireAnyPermission(connection,userId,"CREATE_CUSTOM_ORDER","MANAGE_CUSTOM_ORDERS");
            case "ACCOUNT_PAYMENT_RECEIPT" -> requireAnyPermission(connection,userId,"CUSTOMER_ACCOUNTS");
            case "QUOTATION","INVOICE","DELIVERY_BILL" -> requireAnyPermission(connection,userId,"QUOTATIONS_ORDERS","CREATE_QUOTATION");
            case "BALANCE_SHEET" -> requireAnyPermission(connection,userId,"BALANCE_SHEET");
            default -> throw new ApiException(400,"VALIDATION_ERROR","The email document type is invalid.",false);
        }
    }

    private static List<Long> longList(JsonObject body,String key){
        if(!body.has(key)||body.get(key).isJsonNull())return List.of();Long[]values=GSON.fromJson(body.get(key),Long[].class);return values==null?List.of():List.of(values);
    }
    private ApiResult autoScheduleGenerate(RequestContext x)throws Exception{return scheduleRead(x,(c,d,s)->{Integer[]ids=x.body().has("employeeIds")?GSON.fromJson(x.body().get("employeeIds"),Integer[].class):null;java.util.Set<Integer>selected=ids==null?null:new java.util.LinkedHashSet<>(List.of(ids));ServerEmployeeAutoScheduleService.AutoScheduleProposal p=ServerEmployeeAutoScheduleService.generateRange(requiredInt(x.body(),"locationId"),date(x.body(),"start"),date(x.body(),"end"),selected);return Map.of("proposal",LanAutoScheduleProposalService.store(c,d.deviceId(),s.userId(),s.locationId(),p));});}
    private ApiResult autoScheduleApply(RequestContext x)throws Exception{return scheduleMutation(x,"schedule.auto-apply.v1",(c,d,s)->{ServerEmployeeAutoScheduleService.AutoScheduleProposal requested=GSON.fromJson(x.body().get("proposal"),ServerEmployeeAutoScheduleService.AutoScheduleProposal.class);if(requested==null||requested.proposalId()==null)throw new ApiException(400,"VALIDATION_ERROR","An automatic schedule proposal is required.",false);ServerEmployeeAutoScheduleService.AutoScheduleProposal trusted;try{trusted=LanAutoScheduleProposalService.consume(c,d.deviceId(),s.userId(),s.locationId(),requested.proposalId());}catch(IllegalStateException e){throw new ApiException(409,"SCHEDULE_PROPOSAL_INVALID",e.getMessage(),false);}return Map.of("applied",ServerEmployeeAutoScheduleService.apply(c,trusted));});}
    private ApiResult scheduleMutation(RequestContext x,String operation,ScheduleOperation action)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String key=requireIdempotencyKey(x,"A valid idempotency key is required for this schedule change."),hash=LanSecurity.sha256(GSON.toJson(x.body()));try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}bindSchedule(c,s);Map<String,Object>result;try{result=action.run(c,d,s);}finally{clearScheduleContext();}completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(SQLException e){c.rollback();throw new ApiException(409,"SCHEDULE_CHANGE_REJECTED","The schedule change could not be completed.",false);}catch(Exception e){c.rollback();throw e;}finally{clearScheduleContext();c.setAutoCommit(true);}}}
    private void bindSchedule(Connection c,SessionPrincipal s)throws Exception{Set<String>p=Set.copyOf(loadPermissions(c,s.userId()));AuthenticatedUser u=loadUser(c,s.userId(),s.locationId());String name=displayName(u);ServerEmployeeScheduleService.bindRequest(s.userId(),s.locationId(),name,p);ServerEmployeeAutoScheduleService.bindRequest(s.userId(),s.locationId(),name,p);}
    private static void clearScheduleContext(){ServerEmployeeAutoScheduleService.clearRequest();ServerEmployeeScheduleService.clearRequest();}
    private static java.time.LocalDate date(JsonObject b,String k)throws ApiException{try{return java.time.LocalDate.parse(required(b,k,40));}catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(400,"VALIDATION_ERROR",k+" is invalid.",false);}}
    private static java.time.LocalTime time(JsonObject b,String k)throws ApiException{try{return java.time.LocalTime.parse(required(b,k,40));}catch(ApiException e){throw e;}catch(Exception e){throw new ApiException(400,"VALIDATION_ERROR",k+" is invalid.",false);}}
    private static UUID uuid(JsonObject b,String k,boolean required)throws ApiException{String v=required?required(b,k,80):optional(b,k,80);if(v==null)return null;try{return UUID.fromString(v);}catch(Exception e){throw new ApiException(400,"VALIDATION_ERROR",k+" is invalid.",false);}}
    private ApiResult roleAdminState(RequestContext x)throws Exception{return roleAdminRead(x,(c,s)->LanRoleAdminService.state(c,s.userId()));}
    private ApiResult roleAdminSelected(RequestContext x)throws Exception{return roleAdminRead(x,(c,s)->LanRoleAdminService.selected(c,x.body(),s.userId()));}
    private ApiResult roleAdminSave(RequestContext x)throws Exception{return roleAdminMutation(x,"security.roles.save.v1",false);}
    private ApiResult roleAdminAdd(RequestContext x)throws Exception{return roleAdminMutation(x,"security.roles.add.v1",true);}
    private ApiResult roleAdminRead(RequestContext x,RoleAdminOperation op)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);try(Connection c=DB.getConnection()){try{return ApiResult.ok(op.run(c,s));}catch(LanRoleAdminService.RuleViolation e){throw new ApiException(e.status,e.code,e.getMessage(),false);}}}
    private ApiResult roleAdminMutation(RequestContext x,String operation,boolean add)throws Exception{requireMethod(x.exchange(),"POST");DevicePrincipal d=authenticateDevice(x.exchange());SessionPrincipal s=authenticateSession(x.exchange(),d,true);String key=requireIdempotencyKey(x,"A valid idempotency key is required for this role change."),hash=LanSecurity.sha256(GSON.toJson(x.body()));try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{Map<String,Object>old=loadIdempotentResult(c,d.deviceId(),key,operation,hash);if(old!=null){c.commit();return ApiResult.ok(old);}Map<String,Object>result=add?LanRoleAdminService.add(c,x.body(),s.userId(),s.locationId(),d.deviceId()):LanRoleAdminService.save(c,x.body(),s.userId(),s.locationId(),d.deviceId());completeIdempotency(c,d.deviceId(),key,result);c.commit();return ApiResult.ok(result);}catch(LanRoleAdminService.RuleViolation e){c.rollback();throw new ApiException(e.status,e.code,e.getMessage(),false);}catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}}}

    private ApiResult customerAccountList(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        try(Connection c=DB.getConnection()){try{return ApiResult.ok(Map.of("accounts",LanCustomerAccountService.list(c,session.userId())));}
            catch(LanCustomerAccountService.RuleViolation ex){throw apiException(ex);}}
    }
    private ApiResult customerAccountDetails(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);int id=requiredInt(context.body(),"customerId");
        try(Connection c=DB.getConnection()){try{return ApiResult.ok(LanCustomerAccountService.details(c,id,session.userId()));}
            catch(LanCustomerAccountService.RuleViolation ex){throw apiException(ex);}}
    }
    private ApiResult customerAccountTransactions(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);int id=requiredInt(context.body(),"customerId");
        try(Connection c=DB.getConnection()){try{return ApiResult.ok(LanCustomerAccountService.transactions(c,id,session.userId()));}
            catch(LanCustomerAccountService.RuleViolation ex){throw apiException(ex);}}
    }
    private ApiResult customerAccountPayments(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);int id=requiredInt(context.body(),"customerId");
        try(Connection c=DB.getConnection()){try{return ApiResult.ok(LanCustomerAccountService.payments(c,id,session.userId()));}
            catch(LanCustomerAccountService.RuleViolation ex){throw apiException(ex);}}
    }
    private ApiResult customerAccountPaymentReceipt(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);int customerId=requiredInt(context.body(),"customerId");
        long transactionId=requiredLong(context.body(),"transactionId");
        try(Connection c=DB.getConnection()){try{return ApiResult.ok(LanCustomerAccountService.receipt(c,customerId,transactionId,session.userId()));}
            catch(LanCustomerAccountService.RuleViolation ex){throw apiException(ex);}}
    }
    private ApiResult saveCustomerAccount(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        String key=requireIdempotencyKey(context,"A valid idempotency key is required to save a customer account.");
        String hash=LanSecurity.sha256(GSON.toJson(context.body())),operation="customer-accounts.save.v1";
        try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{
            Map<String,Object>previous=loadIdempotentResult(c,device.deviceId(),key,operation,hash);
            if(previous!=null){c.commit();return ApiResult.ok(previous);}
            Map<String,Object>result=LanCustomerAccountService.save(c,context.body(),device.deviceId(),session.userId());
            completeIdempotency(c,device.deviceId(),key,result);c.commit();return ApiResult.ok(result);
        }catch(LanCustomerAccountService.RuleViolation ex){c.rollback();throw apiException(ex);}catch(Exception ex){c.rollback();throw ex;}
        finally{c.setAutoCommit(true);}}
    }

    private ApiResult adjustCustomerAccount(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        String key=requireIdempotencyKey(context,"A valid idempotency key is required to record an account adjustment.");
        String hash=LanSecurity.sha256(GSON.toJson(context.body())),operation="customer-accounts.adjust.v1";
        try(Connection c=DB.getConnection()){c.setAutoCommit(false);try{
            Map<String,Object>previous=loadIdempotentResult(c,device.deviceId(),key,operation,hash);
            if(previous!=null){c.commit();return ApiResult.ok(previous);}
            AuthenticatedUser user=loadUser(c,session.userId(),session.locationId());
            Map<String,Object>result=LanCustomerAccountService.adjust(c,context.body(),device.deviceId(),session.userId(),displayName(user),session.locationId());
            completeIdempotency(c,device.deviceId(),key,result);c.commit();return ApiResult.ok(result);
        }catch(LanCustomerAccountService.RuleViolation ex){c.rollback();throw apiException(ex);}catch(Exception ex){c.rollback();throw ex;}
        finally{c.setAutoCommit(true);}}
    }

    private ApiResult workstationSettings(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(LanWorkstationSettingsService.load(connection,device.deviceId(),session.userId(),session.locationId()));}
            catch(LanWorkstationSettingsService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult updateWorkstationDeviceCode(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        String code=required(context.body(),"deviceCode",40);
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(LanWorkstationSettingsService.updateDeviceCode(connection,device.deviceId(),session.userId(),code));}
            catch(LanWorkstationSettingsService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult updateWorkstationTimezone(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        String timezone=required(context.body(),"timezone",100);
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(LanWorkstationSettingsService.updateTimezone(connection,session.userId(),session.locationId(),timezone));}
            catch(LanWorkstationSettingsService.RuleViolation ex){throw apiException(ex);}
        }
    }

    private ApiResult syncStatus(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            try { return ApiResult.ok(LanSyncAdminService.status(connection, session.userId())); }
            catch (LanSyncAdminService.RuleViolation ex) { throw apiException(ex); }
        }
    }

    private ApiResult runSync(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        try (Connection connection = DB.getConnection()) {
            try { return ApiResult.ok(LanSyncAdminService.runNow(connection, session.userId())); }
            catch (LanSyncAdminService.RuleViolation ex) { throw apiException(ex); }
        }
    }

    private ApiResult resolveSyncConflict(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        long conflictId = requiredLong(context.body(), "conflictId");
        String key = requireIdempotencyKey(context, "A valid idempotency key is required to resolve a sync conflict.");
        String operation = "sync.resolve.v1";
        String hash = LanSecurity.sha256(GSON.toJson(context.body()));
        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String,Object> previous=loadIdempotentResult(connection,device.deviceId(),key,operation,hash);
                if(previous!=null){connection.commit();return ApiResult.ok(previous);}
                Map<String,Object> result = LanSyncAdminService.resolve(connection, conflictId, session.userId());
                completeIdempotency(connection,device.deviceId(),key,result);
                connection.commit(); return ApiResult.ok(result);
            } catch (LanSyncAdminService.RuleViolation ex) { connection.rollback(); throw apiException(ex); }
            catch (Exception ex) { connection.rollback(); throw ex; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private ApiResult createProduct(RequestContext context) throws Exception {
        return mutateProduct(context, true);
    }

    private ApiResult updateProduct(RequestContext context) throws Exception {
        return mutateProduct(context, false);
    }

    private ApiResult mutateProduct(RequestContext context, boolean create) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String key = requireIdempotencyKey(context, "A valid idempotency key is required to save an item.");
        String operation = create ? "products.create.v1" : "products.update.v1";
        String hash = LanSecurity.sha256(GSON.toJson(context.body()));
        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> previous = loadIdempotentResult(connection, device.deviceId(), key, operation, hash);
                if (previous != null) { connection.commit(); return ApiResult.ok(previous); }
                AuthenticatedUser user = loadUser(connection, session.userId(), session.locationId());
                Map<String, Object> result = create
                        ? LanProductAdminService.create(connection, context.body(), device.deviceId(),
                            session.userId(), displayName(user), session.locationId())
                        : LanProductAdminService.update(connection, context.body(), device.deviceId(),
                            session.userId(), displayName(user), session.locationId());
                completeIdempotency(connection, device.deviceId(), key, result);
                connection.commit(); return ApiResult.ok(result);
            } catch (LanProductAdminService.RuleViolation ex) { connection.rollback(); throw apiException(ex); }
            catch (Exception ex) { connection.rollback(); throw ex; }
            finally { connection.setAutoCommit(true); }
        }
    }

    private ApiResult checkout(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String idempotencyKey = context.exchange().getRequestHeaders().getFirst("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            throw new ApiException(400, "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid idempotency key is required for checkout.", false);
        }
        String operationKey = "sales.checkout.v1";
        String requestHash = LanSecurity.sha256(GSON.toJson(context.body()));
        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> previous = loadIdempotentResult(connection, device.deviceId(),
                        idempotencyKey.trim(), operationKey, requestHash);
                if (previous != null) {
                    connection.commit();
                    return ApiResult.ok(previous);
                }
                AuthenticatedUser user = loadUser(connection, session.userId(), session.locationId());
                Map<String, Object> result;
                try {
                    result = LanSalesService.checkout(connection, context.body(), device.deviceId(),
                            session.userId(), user.fullName() == null || user.fullName().isBlank()
                                    ? user.username() : user.fullName(), session.locationId(),
                            (token, permission, action, reason) -> consumeApproval(connection, device, session,
                                    token, permission, action, reason));
                } catch (LanSalesService.RuleViolation ex) {
                    throw new ApiException(ex.status(), ex.code(), ex.safeMessage(), ex.retryable());
                }
                completeIdempotency(connection, device.deviceId(), idempotencyKey.trim(), result);
                connection.commit();
                return ApiResult.ok(result);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    private ApiResult saleReceipt(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        int saleId = requiredInt(context.body(), "saleId");
        try (Connection connection = DB.getConnection()) {
            requireAnyPermission(connection, session.userId(), "MAKE_SALE", "VIEW_SALES", "PROCESS_RETURNS");
            Map<String, Object> receipt = new LinkedHashMap<>();
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT s.sale_id,COALESCE(s.receipt_number,''),s.created_at,
                           COALESCE(l.name,'Unknown Store'),
                           COALESCE(s.user_name,u.full_name,u.username,'Unknown'),
                           COALESCE(ca.name,''),COALESCE(ca.account_number,''),
                           COALESCE(s.payment_method,''),COALESCE(s.payment_status,'PAID'),
                           COALESCE(s.receipt_device_id,''),COALESCE(s.subtotal_amount,s.total_amount,0),
                           COALESCE(s.discount_percent,0),COALESCE(s.discount_amount,0),
                           COALESCE(s.vat_amount,0),COALESCE(s.vat_rate_percent,0),COALESCE(s.vat_mode,''),
                           COALESCE(s.total_amount,0),COALESCE(s.amount_paid,0),COALESCE(s.returned_amount,0)
                    FROM sales s LEFT JOIN users u ON u.user_id=s.user_id
                    LEFT JOIN locations l ON l.location_id=s.location_id
                    LEFT JOIN customer_accounts ca ON ca.customer_id=s.customer_id
                    WHERE s.sale_id=? AND s.location_id=?
                    """)) {
                ps.setInt(1,saleId); ps.setInt(2,session.locationId());
                try (ResultSet rs=ps.executeQuery()) {
                    if(!rs.next()) throw new ApiException(404,"SALE_NOT_FOUND","Sale was not found for this store.",false);
                    receipt.put("saleId",rs.getInt(1)); receipt.put("receiptNumber",rs.getString(2));
                    receipt.put("saleTimeEpochMillis",rs.getTimestamp(3).getTime()); receipt.put("storeName",rs.getString(4));
                    receipt.put("cashierName",rs.getString(5)); receipt.put("customerName",rs.getString(6));
                    receipt.put("accountNumber",rs.getString(7)); receipt.put("paymentMethod",rs.getString(8));
                    receipt.put("paymentStatus",rs.getString(9)); receipt.put("deviceId",rs.getString(10));
                    receipt.put("subtotalAmount",rs.getBigDecimal(11)); receipt.put("discountPercent",rs.getBigDecimal(12));
                    receipt.put("discountAmount",rs.getBigDecimal(13)); receipt.put("vatAmount",rs.getBigDecimal(14));
                    receipt.put("vatRatePercent",rs.getBigDecimal(15)); receipt.put("vatMode",rs.getString(16));
                    receipt.put("totalAmount",rs.getBigDecimal(17)); receipt.put("amountPaid",rs.getBigDecimal(18));
                    receipt.put("returnedAmount",rs.getBigDecimal(19));
                }
            }
            List<Map<String,Object>> items=new ArrayList<>();
            try(PreparedStatement ps=connection.prepareStatement("""
                    SELECT COALESCE(p.name,'Deleted Item') || CASE WHEN COALESCE(p.size,'')='' THEN '' ELSE ' ('||p.size||')' END,
                           COALESCE(p.sku,''),COALESCE(si.quantity,0),COALESCE(si.original_unit_price,si.unit_price,0),
                           COALESCE(si.unit_price,0),COALESCE(si.discount_percent,0),
                           COALESCE(si.quantity,0)*COALESCE(si.unit_price,0)
                    FROM sale_items si LEFT JOIN products p ON p.product_id=si.product_id
                    WHERE si.sale_id=? ORDER BY si.sale_item_id
                    """)) {
                ps.setInt(1,saleId); try(ResultSet rs=ps.executeQuery()){while(rs.next()){
                    Map<String,Object> item=new LinkedHashMap<>(); item.put("name",rs.getString(1)); item.put("sku",rs.getString(2));
                    item.put("quantity",rs.getInt(3)); item.put("originalUnitPrice",rs.getBigDecimal(4));
                    item.put("finalUnitPrice",rs.getBigDecimal(5)); item.put("discountPercent",rs.getBigDecimal(6));
                    item.put("lineTotal",rs.getBigDecimal(7)); items.add(item);
                }}
            }
            receipt.put("items",items);
            return ApiResult.ok(receipt);
        }
    }

    private ApiResult searchSales(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String query = optional(context.body(), "query", 300);
        Integer source=context.body().has("sourceLocationId")&&!context.body().get("sourceLocationId").isJsonNull()
                ?context.body().get("sourceLocationId").getAsInt():session.locationId();
        try (Connection connection = DB.getConnection()) {
            try {
                return ApiResult.ok(Map.of("sales",source==session.locationId()
                        ?LanRefundService.search(connection,query,session.userId(),session.locationId())
                        :CrossStoreSalesService.searchForReturn(connection,session.userId(),session.locationId(),query,source)));
            } catch (LanRefundService.RuleViolation ex) {
                throw apiException(ex);
            } catch(CrossStoreSalesService.RuleViolation ex){throw new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);
            }
        }
    }

    private ApiResult returnStores(RequestContext context)throws Exception{
        requireMethod(context.exchange(),"POST");
        DevicePrincipal device=authenticateDevice(context.exchange());
        SessionPrincipal session=authenticateSession(context.exchange(),device,true);
        try(Connection connection=DB.getConnection()){
            try{return ApiResult.ok(Map.of("stores",CrossStoreSalesService.returnStoreOptions(connection,session.userId(),session.locationId())));}
            catch(CrossStoreSalesService.RuleViolation ex){throw new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);}
        }
    }

    private ApiResult returnDetails(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        int saleId = requiredInt(context.body(), "saleId");
        Integer source=context.body().has("sourceLocationId")&&!context.body().get("sourceLocationId").isJsonNull()
                ?context.body().get("sourceLocationId").getAsInt():session.locationId();
        try (Connection connection = DB.getConnection()) {
            try {
                return ApiResult.ok(source==session.locationId()
                        ?LanRefundService.details(connection,saleId,session.userId(),session.locationId())
                        :CrossStoreSalesService.returnDetails(connection,session.userId(),session.locationId(),source,saleId));
            } catch (LanRefundService.RuleViolation ex) {
                throw apiException(ex);
            } catch(CrossStoreSalesService.RuleViolation ex){throw new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);
            }
        }
    }

    private ApiResult refund(RequestContext context) throws Exception {
        requireMethod(context.exchange(), "POST");
        DevicePrincipal device = authenticateDevice(context.exchange());
        SessionPrincipal session = authenticateSession(context.exchange(), device, true);
        String idempotencyKey = context.exchange().getRequestHeaders().getFirst("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 160) {
            throw new ApiException(400, "IDEMPOTENCY_KEY_REQUIRED",
                    "A valid idempotency key is required for this return.", false);
        }
        String operationKey = "sales.refund.v1";
        String requestHash = LanSecurity.sha256(GSON.toJson(context.body()));
        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> previous = loadIdempotentResult(connection, device.deviceId(),
                        idempotencyKey.trim(), operationKey, requestHash);
                if (previous != null) {
                    connection.commit();
                    return ApiResult.ok(previous);
                }
                AuthenticatedUser user = loadUser(connection, session.userId(), session.locationId());
                Map<String, Object> result;
                try {
                    boolean cross=context.body().has("sourceLocationId")&&!context.body().get("sourceLocationId").isJsonNull()
                            &&context.body().get("sourceLocationId").getAsInt()!=session.locationId();
                    result = cross?CrossStoreRefundService.refund(connection,context.body(),device.deviceId(),
                            session.userId(),user.fullName()==null||user.fullName().isBlank()?user.username():user.fullName(),
                            session.locationId(),(token,permission,action,reason,resourceIdentity)->consumeResourceApproval(
                                    connection,device,session,token,permission,action,reason,resourceIdentity))
                            :LanRefundService.refund(connection, context.body(), device.deviceId(),
                            session.userId(), user.fullName() == null || user.fullName().isBlank()
                                    ? user.username() : user.fullName(), session.locationId(),
                            (token, permission, action, reason, resourceIdentity) ->
                                    consumeResourceApproval(connection, device, session, token,
                                            permission, action, reason, resourceIdentity));
                } catch (LanRefundService.RuleViolation ex) {
                    throw apiException(ex);
                } catch(CrossStoreRefundService.RuleViolation ex){
                    throw new ApiException(ex.status(),ex.code(),ex.safeMessage(),ex.retryable());
                }
                completeIdempotency(connection, device.deviceId(), idempotencyKey.trim(), result);
                connection.commit();
                return ApiResult.ok(result);
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> loadIdempotentResult(Connection connection, UUID deviceId,
                                                     String key, String operation, String requestHash) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO lan_api_idempotency (device_id,idempotency_key,operation_key,request_hash)
                VALUES (?,?,?,?) ON CONFLICT (device_id,idempotency_key) DO NOTHING
                """)) {
            insert.setObject(1, deviceId); insert.setString(2, key); insert.setString(3, operation);
            insert.setString(4, requestHash);
            if (insert.executeUpdate() == 1) return null;
        }
        try (PreparedStatement select = connection.prepareStatement("""
                SELECT operation_key,request_hash,response_body,completed_at
                FROM lan_api_idempotency WHERE device_id=? AND idempotency_key=? FOR UPDATE
                """)) {
            select.setObject(1, deviceId); select.setString(2, key);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) throw new ApiException(409,"IDEMPOTENCY_CONFLICT","Retry state was not found.",true);
                if (!operation.equals(rs.getString(1)) || !LanSecurity.constantTimeEquals(requestHash, rs.getString(2))) {
                    throw new ApiException(409,"IDEMPOTENCY_CONFLICT",
                            "That retry key was already used for a different request.",false);
                }
                if (rs.getTimestamp(4) == null || rs.getString(3) == null) {
                    throw new ApiException(409,"IDEMPOTENCY_IN_PROGRESS","That request is still being processed.",true);
                }
                return GSON.fromJson(rs.getString(3), Map.class);
            }
        }
    }

    static void completeIdempotency(Connection connection, UUID deviceId, String key,
                                    Map<String, Object> result) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE lan_api_idempotency SET response_status=200,response_body=?,completed_at=CURRENT_TIMESTAMP
                WHERE device_id=? AND idempotency_key=?
                """)) {
            ps.setString(1, GSON.toJson(result)); ps.setObject(2, deviceId); ps.setString(3, key);
            if (ps.executeUpdate() != 1) throw new SQLException("Idempotency result could not be saved.");
        }
    }

    private LanRefundService.Approval consumeResourceApproval(Connection connection,
                                                               DevicePrincipal device,
                                                               SessionPrincipal session,
                                                               String token, String permission,
                                                               String action, String reason,
                                                               String resourceIdentity) throws Exception {
        if (token == null || token.isBlank() || reason == null || reason.isBlank()) {
            throw new ApiException(403, "APPROVAL_REQUIRED",
                    "Manager approval is required for this return.", false);
        }
        String resourceHash = LanSecurity.sha256(
                RefundApprovalIdentity.withReason(resourceIdentity, reason));
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT a.approval_id,a.approver_user_id,
                       COALESCE(NULLIF(TRIM(u.full_name),''),u.username,'Manager') AS approver_name
                FROM lan_api_approvals a JOIN users u ON u.user_id=a.approver_user_id
                WHERE a.approval_hash=? AND a.device_id=? AND a.requester_user_id=? AND a.location_id=?
                  AND UPPER(a.permission_key)=? AND a.action_key=? AND a.resource_hash=?
                  AND a.consumed_at IS NULL AND a.expires_at>CURRENT_TIMESTAMP
                FOR UPDATE
                """)) {
            ps.setString(1, LanSecurity.sha256(token));
            ps.setObject(2, device.deviceId());
            ps.setInt(3, session.userId());
            ps.setInt(4, session.locationId());
            ps.setString(5, permission.toUpperCase());
            ps.setString(6, action);
            ps.setString(7, resourceHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ApiException(403, "APPROVAL_INVALID",
                            "Manager approval is expired, already used, or does not match this return.", false);
                }
                UUID approvalId = (UUID) rs.getObject(1);
                int approver = rs.getInt(2);
                String name = rs.getString(3);
                try (PreparedStatement consume = connection.prepareStatement("""
                        UPDATE lan_api_approvals SET consumed_at=CURRENT_TIMESTAMP
                        WHERE approval_id=? AND consumed_at IS NULL
                        """)) {
                    consume.setObject(1, approvalId);
                    if (consume.executeUpdate() != 1) {
                        throw new ApiException(403, "APPROVAL_INVALID",
                                "Manager approval was already used.", false);
                    }
                }
                return new LanRefundService.Approval(approver, name, reason.trim());
            }
        }
    }

    private static ApiException apiException(LanRefundService.RuleViolation ex) {
        return new ApiException(ex.status(), ex.code(), ex.safeMessage(), ex.retryable());
    }

    private static ApiException apiException(LanHeldCartService.RuleViolation ex) {
        return new ApiException(ex.status(), ex.code(), ex.safeMessage(), false);
    }

    private static ApiException apiException(LanSalesHistoryService.RuleViolation ex) {
        return new ApiException(ex.status(), ex.code(), ex.safeMessage(), false);
    }

    private static ApiException apiException(LanInventoryService.RuleViolation ex) {
        return new ApiException(ex.status(), ex.code(), ex.safeMessage(), false);
    }

    private static ApiException apiException(LanTransferService.RuleViolation ex) {
        return new ApiException(ex.status(), ex.code(), ex.safeMessage(), false);
    }

    private static ApiException apiException(LanCatalogAdminService.RuleViolation ex) {
        return new ApiException(ex.status(), ex.code(), ex.safeMessage(), false);
    }

    private static ApiException apiException(LanProductAdminService.RuleViolation ex) {
        return new ApiException(ex.status(), ex.code(), ex.safeMessage(), false);
    }

    private static ApiException apiException(LanSyncAdminService.RuleViolation ex) {
        return new ApiException(ex.status(), ex.code(), ex.safeMessage(), false);
    }

    private static ApiException apiException(LanWorkstationSettingsService.RuleViolation ex) {
        return new ApiException(ex.status(), ex.code(), ex.safeMessage(), false);
    }

    private static ApiException apiException(LanCustomerAccountService.RuleViolation ex) {
        return new ApiException(ex.status(), ex.code(), ex.safeMessage(), false);
    }
    private static ApiException apiException(LanEmployeeSelfService.RuleViolation ex){
        return new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);
    }
    private static ApiException apiException(LanCashOperationsService.RuleViolation ex){return new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);}
    private static ApiException apiException(LanCashDrawerService.RuleViolation ex){return new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);}
    private static ApiException apiException(LanDeviceAdminService.RuleViolation ex){return new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);}
    private static ApiException apiException(LanServerAdminService.RuleViolation ex){return new ApiException(ex.status,ex.code,ex.getMessage(),false);}
    private static ApiException apiException(CloudServerRegistryService.RegistryException ex){
        String code=switch(ex.code()){case "23505"->"PRIMARY_SERVER_EXISTS";case "40001"->"GENERATION_CONFLICT";case "55000"->"SERVER_READINESS_FAILED";case "P0002"->"SERVER_NOT_FOUND";default->"SERVER_REGISTRY_REJECTED";};
        return new ApiException(409,code,ex.getMessage(),false);
    }
    private static ApiException apiException(LanLocationService.RuleViolation ex){return new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);}
    private static ApiException apiException(LanMaintenancePartsService.RuleViolation ex){return new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);}
    private static ApiException apiException(LanDocumentDataService.RuleViolation ex){return new ApiException(ex.status(),ex.code(),ex.safeMessage(),false);}

    private static String displayName(AuthenticatedUser user) {
        return user.fullName() == null || user.fullName().isBlank() ? user.username() : user.fullName();
    }

    private static String requireIdempotencyKey(RequestContext context, String message) throws ApiException {
        String value = context.exchange().getRequestHeaders().getFirst("Idempotency-Key");
        if (value == null || value.isBlank() || value.length() > 160) {
            throw new ApiException(400, "IDEMPOTENCY_KEY_REQUIRED", message, false);
        }
        return value.trim();
    }

    private LanSalesService.Approval consumeApproval(Connection connection, DevicePrincipal device,
                                                       SessionPrincipal session, String token,
                                                       String permission, String action, String reason) throws Exception {
        if (token == null || token.isBlank() || reason == null || reason.isBlank()) {
            throw new ApiException(403,"APPROVAL_REQUIRED","Manager approval is required for this action.",false);
        }
        String resourceHash = LanSecurity.sha256(action + "|" + reason.trim());
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT a.approval_id,a.approver_user_id,
                       COALESCE(NULLIF(TRIM(u.full_name),''),u.username,'Manager') AS approver_name
                FROM lan_api_approvals a JOIN users u ON u.user_id=a.approver_user_id
                WHERE a.approval_hash=? AND a.device_id=? AND a.requester_user_id=? AND a.location_id=?
                  AND UPPER(a.permission_key)=? AND a.action_key=? AND a.resource_hash=?
                  AND a.consumed_at IS NULL AND a.expires_at>CURRENT_TIMESTAMP
                FOR UPDATE
                """)) {
            ps.setString(1,LanSecurity.sha256(token)); ps.setObject(2,device.deviceId());
            ps.setInt(3,session.userId()); ps.setInt(4,session.locationId());
            ps.setString(5,permission.toUpperCase()); ps.setString(6,action); ps.setString(7,resourceHash);
            try (ResultSet rs=ps.executeQuery()) {
                if (!rs.next()) throw new ApiException(403,"APPROVAL_INVALID",
                        "Manager approval is expired, already used, or does not match this action.",false);
                UUID approvalId=(UUID)rs.getObject(1); int approver=rs.getInt(2); String name=rs.getString(3);
                try (PreparedStatement consume=connection.prepareStatement(
                        "UPDATE lan_api_approvals SET consumed_at=CURRENT_TIMESTAMP WHERE approval_id=?")) {
                    consume.setObject(1,approvalId); consume.executeUpdate();
                }
                return new LanSalesService.Approval(approver,name,reason.trim());
            }
        }
    }

    private void requireAnyPermission(Connection connection, int userId, String... permissionKeys) throws Exception {
        for (String permissionKey : permissionKeys) {
            if (hasPermission(connection, userId, permissionKey)) return;
        }
        throw new ApiException(403, "PERMISSION_DENIED", "You do not have permission for this operation.", false);
    }

    private boolean hasAnyPermission(Connection connection,int userId,String...permissionKeys)throws SQLException{
        for(String permissionKey:permissionKeys)if(hasPermission(connection,userId,permissionKey))return true;
        return false;
    }

    private DevicePrincipal authenticateDevice(HttpExchange exchange) throws Exception {
        String token = bearer(exchange.getRequestHeaders(), "X-SmartStock-Device");
        if (token == null) throw new ApiException(401, "DEVICE_CREDENTIAL_REQUIRED", "This register is not paired.", false);
        String hash = LanSecurity.sha256(token);
        try (Connection connection = DB.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT device_id, installation_id, last_store_id, COALESCE(access_mode, 'CLIENT') access_mode,
                            api_credential_hash, api_previous_credential_hash,
                            api_credential_expires_at, api_previous_expires_at
                     FROM devices
                     WHERE is_approved = TRUE AND is_blocked = FALSE
                       AND (api_credential_hash = ? OR api_previous_credential_hash = ?)
                     """)) {
            ps.setString(1, hash);
            ps.setString(2, hash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new ApiException(401, "DEVICE_CREDENTIAL_INVALID", "This register credential is invalid or revoked.", false);
                String currentHash = rs.getString("api_credential_hash");
                Timestamp expiry = LanSecurity.constantTimeEquals(hash, currentHash)
                        ? rs.getTimestamp("api_credential_expires_at") : rs.getTimestamp("api_previous_expires_at");
                if (expiry == null || expiry.toInstant().isBefore(Instant.now())) {
                    throw new ApiException(401, "DEVICE_CREDENTIAL_EXPIRED", "This register credential needs administrator recovery.", false);
                }
                UUID deviceId = (UUID) rs.getObject("device_id");
                boolean remoteAdmin = "REMOTE_ADMIN".equals(rs.getString("access_mode"));
                if (remoteAdmin && RemoteAdminPolicy.isPhysicalOperation(exchange.getRequestURI().getPath())) {
                    throw new ApiException(403, "PHYSICAL_STORE_ACCESS_REQUIRED",
                            "This action requires physical access to the selected store and is unavailable through Remote Admin.", false);
                }
                try (PreparedStatement touch = connection.prepareStatement("""
                        UPDATE devices SET last_seen = CURRENT_TIMESTAMP,
                            api_credential_last_used_at = CURRENT_TIMESTAMP WHERE device_id = ?
                        """)) {
                    touch.setObject(1, deviceId);
                    touch.executeUpdate();
                }
                exchange.setAttribute("smartstock.deviceId", deviceId);
                exchange.setAttribute("smartstock.remoteAdmin", remoteAdmin);
                return new DevicePrincipal(deviceId, rs.getString("installation_id"),
                        (Integer) rs.getObject("last_store_id"), remoteAdmin);
            }
        }
    }

    private SessionPrincipal authenticateSession(HttpExchange exchange, DevicePrincipal device, boolean refresh) throws Exception {
        String token = bearer(exchange.getRequestHeaders(), "Authorization");
        if (token == null) throw new ApiException(401, "SESSION_REQUIRED", "Employee login is required.", false);
        String hash = LanSecurity.sha256(token);
        try (Connection connection = DB.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT s.session_id, s.user_id, s.location_id, s.expires_at, s.absolute_expires_at,
                           u.is_active, d.is_approved, d.is_blocked
                    FROM lan_api_sessions s
                    JOIN users u ON u.user_id = s.user_id
                    JOIN devices d ON d.device_id = s.device_id
                    WHERE s.session_hash = ? AND s.device_id = ? AND s.revoked_at IS NULL
                    FOR UPDATE OF s
                    """)) {
                ps.setString(1, hash);
                ps.setObject(2, device.deviceId());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new ApiException(401, "SESSION_INVALID", "The employee session is no longer valid.", false);
                    if (!rs.getBoolean("is_active") || !rs.getBoolean("is_approved") || rs.getBoolean("is_blocked")) {
                        throw new ApiException(403, "SESSION_REVOKED", "Access has been revoked.", false);
                    }
                    Instant now = Instant.now();
                    Instant expires = rs.getTimestamp("expires_at").toInstant();
                    Instant absolute = rs.getTimestamp("absolute_expires_at").toInstant();
                    if (expires.isBefore(now) || absolute.isBefore(now)) {
                        throw new ApiException(401, "SESSION_EXPIRED", "Please log in again.", false);
                    }
                    UUID sessionId = (UUID) rs.getObject("session_id");
                    if (refresh) {
                        Instant next = now.plus(SESSION_LIFETIME).isBefore(absolute) ? now.plus(SESSION_LIFETIME) : absolute;
                        try (PreparedStatement update = connection.prepareStatement("""
                                UPDATE lan_api_sessions SET expires_at = ?, last_seen_at = CURRENT_TIMESTAMP
                                WHERE session_id = ?
                                """)) {
                            update.setTimestamp(1, Timestamp.from(next));
                            update.setObject(2, sessionId);
                            update.executeUpdate();
                        }
                    }
                    exchange.setAttribute("smartstock.userId", rs.getInt("user_id"));
                    exchange.setAttribute("smartstock.locationId", rs.getInt("location_id"));
                    enforceRemoteStoreAvailability(connection, exchange, rs.getInt("location_id"));
                    connection.commit();
                    return new SessionPrincipal(sessionId, rs.getInt("user_id"), rs.getInt("location_id"), token);
                }
            } catch (Exception ex) {
                connection.rollback();
                throw ex;
            }
        }
    }

    private void enforceRemoteStoreAvailability(Connection connection, HttpExchange exchange, int locationId) throws SQLException, ApiException {
        if (!Boolean.TRUE.equals(exchange.getAttribute("smartstock.remoteAdmin"))) return;
        String path = exchange.getRequestURI().getPath();
        if (!RemoteAdminPolicy.isMutation(path) || RemoteAdminPolicy.isOfflineSafeMutation(path)) return;
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT last_seen_at >= CURRENT_TIMESTAMP - INTERVAL '3 minutes'
                FROM store_sync_status WHERE location_id=?
                """)) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !rs.getBoolean(1)) {
                    throw new ApiException(409, "STORE_OFFLINE_CURRENT_STATE_REQUIRED",
                            "The selected store is offline. This change requires current store state and has not been applied.", true);
                }
            }
        }
    }

    private AuthenticatedUser authenticateSupabase(Connection connection, String accessToken, int locationId) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(SupabaseSessionManager.getSupabaseUrl() + "/auth/v1/user"))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", SupabaseSessionManager.getSupabasePublishableKey())
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json").GET().build();
        HttpResponse<String> response = CLOUD_HTTP.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(401, "SUPABASE_SESSION_INVALID", "The online login session was not accepted.", false);
        }
        JsonObject user = JsonParser.parseString(response.body()).getAsJsonObject();
        String authUserId = required(user, "id", 100);
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT u.user_id, u.username, u.full_name, u.email, COALESCE(r.role_name, 'USER') role_name,
                       l.location_id, l.name location_name, COALESCE(l.timezone, '') location_timezone
                FROM users u
                LEFT JOIN roles r ON r.role_id = u.role_id
                JOIN user_locations ul ON ul.user_id = u.user_id
                JOIN locations l ON l.location_id = ul.location_id
                WHERE u.auth_user_id::text = ? AND u.is_active = TRUE AND l.location_id = ?
                """)) {
            ps.setString(1, authUserId);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new ApiException(403, "STORE_ACCESS_DENIED", "This employee is not assigned to this store.", false);
                return userFrom(rs);
            }
        }
    }

    private AuthenticatedUser loadUser(Connection connection, int userId, int locationId) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT u.user_id, u.username, u.full_name, u.email, COALESCE(r.role_name, 'USER') role_name,
                       l.location_id, l.name location_name, COALESCE(l.timezone, '') location_timezone
                FROM users u LEFT JOIN roles r ON r.role_id = u.role_id
                JOIN user_locations ul ON ul.user_id = u.user_id
                JOIN locations l ON l.location_id = ul.location_id
                WHERE u.user_id = ? AND u.is_active = TRUE AND l.location_id = ?
                """)) {
            ps.setInt(1, userId);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new ApiException(403, "STORE_ACCESS_DENIED", "This employee no longer has store access.", false);
                return userFrom(rs);
            }
        }
    }

    private String issueSession(Connection connection, DevicePrincipal device, AuthenticatedUser user, String source) throws SQLException {
        String token = LanSecurity.randomToken();
        Instant now = Instant.now();
        try (PreparedStatement revoke = connection.prepareStatement("""
                UPDATE lan_api_sessions SET revoked_at = CURRENT_TIMESTAMP
                WHERE device_id = ? AND user_id = ? AND revoked_at IS NULL
                """)) {
            revoke.setObject(1, device.deviceId());
            revoke.setInt(2, user.userId());
            revoke.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO lan_api_sessions (
                    session_hash, device_id, user_id, location_id, expires_at,
                    absolute_expires_at, auth_source
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, LanSecurity.sha256(token));
            ps.setObject(2, device.deviceId());
            ps.setInt(3, user.userId());
            ps.setInt(4, user.locationId());
            ps.setTimestamp(5, Timestamp.from(now.plus(SESSION_LIFETIME)));
            ps.setTimestamp(6, Timestamp.from(now.plus(SESSION_ABSOLUTE_LIFETIME)));
            ps.setString(7, source);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                UPDATE devices SET last_store_id = ?, last_login_user_id = ?, last_seen = CURRENT_TIMESTAMP
                WHERE device_id = ?
                """)) {
            ps.setInt(1, user.locationId());
            ps.setInt(2, user.userId());
            ps.setObject(3, device.deviceId());
            ps.executeUpdate();
        }
        return token;
    }

    private List<String> loadPermissions(Connection connection, int userId) throws SQLException {
        List<String> permissions = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT DISTINCT UPPER(p.permission_key)
                FROM users u JOIN role_permissions rp ON rp.role_id = u.role_id
                JOIN permissions p ON p.permission_id = rp.permission_id
                WHERE u.user_id = ? ORDER BY 1
                """)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) permissions.add(rs.getString(1));
            }
        }
        return permissions;
    }

    private boolean hasPermission(Connection connection, int userId, String permissionKey) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT 1 FROM users u
                JOIN role_permissions rp ON rp.role_id = u.role_id
                JOIN permissions p ON p.permission_id = rp.permission_id
                WHERE u.user_id = ? AND UPPER(p.permission_key) = ? LIMIT 1
                """)) {
            ps.setInt(1, userId);
            ps.setString(2, permissionKey);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private SupabasePasswordResult signInSupabasePassword(String email, String password) {
        if (email == null || email.isBlank()) return new SupabasePasswordResult(SupabasePasswordStatus.REJECTED, null, null);
        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SupabaseSessionManager.getSupabaseUrl() + "/auth/v1/token?grant_type=password"))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", SupabaseSessionManager.getSupabasePublishableKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = CLOUD_HTTP.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
                return new SupabasePasswordResult(SupabasePasswordStatus.SUCCESS,
                        optionalJsonString(json, "access_token"), optionalJsonString(json, "refresh_token"));
            }
            return new SupabasePasswordResult(SupabasePasswordStatus.REJECTED, null, null);
        } catch (Exception ex) {
            return new SupabasePasswordResult(SupabasePasswordStatus.UNAVAILABLE, null, null);
        }
    }

    private ResolvedLoginUser resolveLoginUser(Connection connection, String identifier, int locationId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT u.user_id, u.username, u.full_name, u.email, u.badge_id,
                       COALESCE(r.role_name, 'USER') AS role_name,
                       l.location_id, l.name AS location_name, COALESCE(l.timezone, '') AS location_timezone
                FROM users u
                LEFT JOIN roles r ON r.role_id = u.role_id
                JOIN user_locations ul ON ul.user_id = u.user_id AND ul.location_id = ?
                JOIN locations l ON l.location_id = ul.location_id
                WHERE COALESCE(u.is_active, TRUE) = TRUE
                  AND (LOWER(u.username) = LOWER(?) OR LOWER(u.email) = LOWER(?)
                       OR UPPER(REGEXP_REPLACE(COALESCE(u.badge_id, ''), '[^a-zA-Z0-9]', '', 'g')) = ?)
                LIMIT 1
                """)) {
            ps.setInt(1, locationId);
            ps.setString(2, identifier);
            ps.setString(3, identifier);
            ps.setString(4, BadgeCredentialService.normalizeBadge(identifier));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new ResolvedLoginUser(rs.getInt("user_id"), rs.getString("username"),
                        rs.getString("full_name"), rs.getString("email"), rs.getString("badge_id"),
                        rs.getString("role_name"), rs.getInt("location_id"), rs.getString("location_name"),
                        rs.getString("location_timezone"));
            }
        }
    }

    private Map<String, Object> sessionResponse(String token, AuthenticatedUser user, List<String> permissions,
                                                UUID deviceId, SupabasePasswordResult supabaseTokens,
                                                DeviceSessionPolicy policy) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionToken", token);
        result.put("expiresAt", Instant.now().plus(SESSION_LIFETIME).toString());
        result.put("user", user);
        result.put("permissions", permissions);
        result.put("deviceId", deviceId.toString());
        result.put("persistentLoginAllowed", policy.persistentLoginAllowed());
        result.put("autoLogoutEnabled", policy.autoLogoutEnabled());
        result.put("autoLogoutMinutes", policy.autoLogoutMinutes());
        if (supabaseTokens != null && supabaseTokens.status() == SupabasePasswordStatus.SUCCESS) {
            result.put("supabaseAccessToken", supabaseTokens.accessToken());
            result.put("supabaseRefreshToken", supabaseTokens.refreshToken());
        }
        return result;
    }

    private DeviceSessionPolicy deviceSessionPolicy(Connection connection, UUID deviceId) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT COALESCE(allow_persistent_login, FALSE),
                       COALESCE(auto_logout_enabled, FALSE),
                       COALESCE(auto_logout_minutes, 15)
                FROM devices WHERE device_id = ?
                """)) {
            ps.setObject(1, deviceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return new DeviceSessionPolicy(false, false, 15);
                int minutes = Math.max(1, Math.min(480, rs.getInt(3)));
                return new DeviceSessionPolicy(rs.getBoolean(1), rs.getBoolean(2), minutes);
            }
        }
    }

    private record DeviceSessionPolicy(boolean persistentLoginAllowed,
                                       boolean autoLogoutEnabled,
                                       int autoLogoutMinutes) { }

    private static String optionalJsonString(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }

    private void handle(HttpExchange exchange, Operation operation) throws IOException {
        long started = System.nanoTime();
        UUID requestId = UUID.randomUUID();
        exchange.getResponseHeaders().set("X-Request-Id", requestId.toString());
        int status = 500;
        int resultCount = -1;
        String outcome = "ERROR";
        ApiEnvelope envelope;
        try {
            if (ServerRoleGuard.blocks(exchange.getRequestURI().getPath())) {
                throw new ApiException(503, "SERVER_NOT_PRIMARY", ServerRoleGuard.safeMessage(), true);
            }
            requireDeviceHeaderBeforeBody(exchange);
            JsonObject body = readsBody(exchange.getRequestMethod()) ? readJson(exchange) : new JsonObject();
            ApiResult result = operation.run(new RequestContext(exchange, body, requestId));
            status = result.status();
            resultCount = PerformanceDiagnostics.resultCount(result.data());
            outcome = status < 400 ? "SUCCESS" : "DENIED";
            envelope = success(result.data());
            recordRemoteCommand(requestId, exchange);
        } catch (ApiException ex) {
            status = ex.status;
            outcome = status == 401 || status == 403 ? "DENIED" : "ERROR";
            envelope = failure(ex.code, ex.getMessage(), ex.retryable);
        } catch (Exception ex) {
            ex.printStackTrace();
            envelope = failure("INTERNAL_ERROR", "SmartStock could not complete this request.", true);
        }
        PerformanceDiagnostics.record("server", exchange.getRequestURI().getPath(), started,
                status < 400, resultCount);
        bestEffortRequestAudit(requestId, exchange, status, outcome);
        send(exchange, status, envelope);
    }

    private void recordRemoteCommand(UUID requestId, HttpExchange exchange) {
        if (!Boolean.TRUE.equals(exchange.getAttribute("smartstock.remoteAdmin"))) return;
        String path = exchange.getRequestURI().getPath();
        if (!RemoteAdminPolicy.isMutation(path)) return;
        Object location = exchange.getAttribute("smartstock.locationId");
        if (!(location instanceof Integer locationId)) return;
        try (Connection connection = DB.getConnection(); PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO remote_admin_commands(command_id,location_id,device_id,user_id,operation,status,details)
                VALUES (?,?,?,?,?,CASE WHEN EXISTS (
                    SELECT 1 FROM store_sync_status WHERE location_id=?
                      AND last_seen_at >= CURRENT_TIMESTAMP - INTERVAL '3 minutes'
                ) THEN 'APPLIED_CLOUD' ELSE 'PENDING_STORE' END,?)
                ON CONFLICT(command_id) DO NOTHING
                """)) {
            ps.setObject(1, requestId); ps.setInt(2, locationId);
            ps.setObject(3, exchange.getAttribute("smartstock.deviceId"));
            Object user = exchange.getAttribute("smartstock.userId");
            if (user instanceof Integer userId) ps.setInt(4, userId); else ps.setNull(4, java.sql.Types.INTEGER);
            ps.setString(5, path); ps.setInt(6, locationId);
            ps.setString(7, RemoteAdminPolicy.isOfflineSafeMutation(path)
                    ? "Conflict-safe cloud change" : "Applied while store was online");
            ps.executeUpdate();
        } catch (Exception ex) {
            System.err.println("Remote Admin command audit could not be recorded: " + ex.getMessage());
        }
    }

    private void bestEffortRequestAudit(UUID requestId, HttpExchange exchange, int status, String outcome) {
        try (Connection connection = DB.getConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO lan_api_request_audit (
                         request_id, device_id, user_id, location_id, method, route,
                         operation_key, outcome, status_code, source_address
                     ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::inet)
                     """)) {
            ps.setObject(1, requestId);
            ps.setObject(2, exchange.getAttribute("smartstock.deviceId"));
            Object userId = exchange.getAttribute("smartstock.userId");
            if (userId == null) ps.setNull(3, java.sql.Types.INTEGER); else ps.setInt(3, (Integer) userId);
            Object locationId = exchange.getAttribute("smartstock.locationId");
            if (locationId == null) ps.setNull(4, java.sql.Types.INTEGER); else ps.setInt(4, (Integer) locationId);
            ps.setString(5, exchange.getRequestMethod());
            ps.setString(6, exchange.getRequestURI().getPath());
            ps.setString(7, exchange.getRequestURI().getPath());
            ps.setString(8, outcome);
            ps.setInt(9, status);
            String address = exchange.getRemoteAddress() == null ? null
                    : exchange.getRemoteAddress().getAddress().getHostAddress();
            ps.setString(10, address);
            ps.executeUpdate();
        } catch (Exception ex) {
            System.err.println("LAN request audit failed for " + requestId + ": " + ex.getMessage());
        }
    }

    private void auditSecurity(Connection connection, String type, UUID deviceId, Integer actor, String details) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO security_audit_events (event_type, device_id, actor_user_id, details)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setString(1, type);
            ps.setObject(2, deviceId);
            if (actor == null) ps.setNull(3, java.sql.Types.INTEGER); else ps.setInt(3, actor);
            ps.setString(4, details);
            ps.executeUpdate();
        }
    }

    private void requirePairingPhrase(JsonObject body) throws ApiException {
        if (!tlsIdentity.acceptsPairingPhrase(optional(body, "pairingPhrase", 64))) {
            throw new ApiException(403, "PAIRING_PHRASE_INVALID", "The administrator pairing phrase is invalid or expired.", false);
        }
    }

    private static void requireDeviceHeaderBeforeBody(HttpExchange exchange) throws ApiException {
        if (DEVICE_HEADER_EXEMPT_ROUTES.contains(exchange.getRequestURI().getPath())) return;
        String credential = exchange.getRequestHeaders().getFirst("X-SmartStock-Device");
        if (credential == null || credential.isBlank()) {
            throw new ApiException(401, "DEVICE_CREDENTIAL_REQUIRED", "This register is not paired.", false);
        }
    }

    private static JsonObject readJson(HttpExchange exchange) throws Exception {
        String path = exchange.getRequestURI().getPath();
        int limit = "/v1/cloud/storage/upload".equals(path) ? MAX_CLOUD_FILE_BODY_BYTES
                : "/v1/configuration/update".equals(path) ? MAX_IMAGE_BODY_BYTES : MAX_BODY_BYTES;
        int declared = parseContentLength(exchange.getRequestHeaders().getFirst("Content-Length"));
        if (declared > limit) throw new ApiException(413, "BODY_TOO_LARGE", "The request is too large.", false);
        byte[] bytes = exchange.getRequestBody().readNBytes(limit + 1);
        if (bytes.length > limit) throw new ApiException(413, "BODY_TOO_LARGE", "The request is too large.", false);
        if (bytes.length == 0) return new JsonObject();
        try {
            return JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            throw new ApiException(400, "INVALID_JSON", "The request body is invalid.", false);
        }
    }

    private static int parseContentLength(String value) {
        try { return value == null ? -1 : Integer.parseInt(value); }
        catch (NumberFormatException ex) { return -1; }
    }

    private static boolean readsBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    private static String encryptForDevice(String publicKeyText, String secret) throws Exception {
        PublicKey key = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyText)));
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key);
        return Base64.getEncoder().encodeToString(cipher.doFinal(secret.getBytes(StandardCharsets.UTF_8)));
    }

    private static String bearer(Headers headers, String name) {
        String value = headers.getFirst(name);
        if (value == null || value.isBlank()) return null;
        if ("Authorization".equalsIgnoreCase(name)) {
            return value.regionMatches(true, 0, "Bearer ", 0, 7) ? value.substring(7).trim() : null;
        }
        return value.trim();
    }

    private static String required(JsonObject object, String key, int maxLength) throws ApiException {
        String value = optional(object, key, maxLength);
        if (value == null) throw new ApiException(400, "VALIDATION_ERROR", key + " is required.", false);
        return value;
    }

    private static int requiredInt(JsonObject object, String key) throws ApiException {
        try { return object.get(key).getAsInt(); }
        catch (Exception ex) { throw new ApiException(400, "VALIDATION_ERROR", key + " is required.", false); }
    }

    private static long requiredLong(JsonObject object, String key) throws ApiException {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            throw new ApiException(400, "VALIDATION_ERROR", "Missing required field: " + key, false);
        }
        try {
            long value = object.get(key).getAsLong();
            if (value <= 0) throw new NumberFormatException();
            return value;
        } catch (Exception ex) {
            throw new ApiException(400, "VALIDATION_ERROR", "Invalid value for: " + key, false);
        }
    }

    private static String optional(JsonObject object, String key, int maxLength) throws ApiException {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return null;
        String value = object.get(key).getAsString().trim();
        if (value.isEmpty()) return null;
        if (value.length() > maxLength) throw new ApiException(400, "VALIDATION_ERROR", key + " is too long.", false);
        return value;
    }

    private static void requireMethod(HttpExchange exchange, String method) throws ApiException {
        if (!method.equals(exchange.getRequestMethod())) {
            throw new ApiException(405, "METHOD_NOT_ALLOWED", "Use " + method + " for this endpoint.", false);
        }
    }

    private static AuthenticatedUser userFrom(ResultSet rs) throws SQLException {
        return new AuthenticatedUser(rs.getInt("user_id"), rs.getString("username"), rs.getString("full_name"),
                rs.getString("email"), rs.getString("role_name"), rs.getInt("location_id"),
                rs.getString("location_name"), rs.getString("location_timezone"));
    }

    private static ApiEnvelope success(Object data) {
        return new ApiEnvelope(true, data, null);
    }

    private static ApiEnvelope failure(String code, String message, boolean retryable) {
        return new ApiEnvelope(false, null, new ApiError(code, message, retryable, Map.of()));
    }

    private static void send(HttpExchange exchange, int status, ApiEnvelope envelope) throws IOException {
        byte[] bytes = GSON.toJson(envelope).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }

    @Override
    public void close() {
        server.stop(2);
        if (discoveryService != null) discoveryService.close();
        executor.shutdownNow();
    }

    private interface Operation { ApiResult run(RequestContext context) throws Exception; }
    @FunctionalInterface private interface CashDrawerOperation { Map<String,Object> run(Connection c,DevicePrincipal d,SessionPrincipal s,AuthenticatedUser u) throws Exception; }
    @FunctionalInterface private interface DeviceAdminOperation { Map<String,Object> run(Connection c,SessionPrincipal s) throws Exception; }
    @FunctionalInterface private interface TimeClockAutoCloseMutation { Map<String,Object> run(Connection c,SessionPrincipal s,AuthenticatedUser u) throws Exception; }
    @FunctionalInterface private interface TimeClockCoreMutation { Map<String,Object> run(Connection c,DevicePrincipal d,SessionPrincipal s,AuthenticatedUser u) throws Exception; }
    @FunctionalInterface private interface ScheduleOperation { Map<String,Object> run(Connection c,DevicePrincipal d,SessionPrincipal s) throws Exception; }
    @FunctionalInterface private interface RoleAdminOperation { Map<String,Object> run(Connection c,SessionPrincipal s) throws Exception; }
    private record RequestContext(HttpExchange exchange, JsonObject body, UUID requestId) { }
    private record ApiResult(int status, Object data) { static ApiResult ok(Object data) { return new ApiResult(200, data); } }
    private record ApiEnvelope(boolean success, Object data, ApiError error) { }
    private record ApiError(String code, String message, boolean retryable, Map<String, String> fields) { }
    private record DevicePrincipal(UUID deviceId, String installationId, Integer locationId, boolean remoteAdmin) { }
    private record SessionPrincipal(UUID sessionId, int userId, int locationId, String plainToken) { }
    private record AuthenticatedUser(int userId, String username, String fullName, String email, String role,
                                     int locationId, String locationName, String locationTimezone) { }
    private enum SupabasePasswordStatus { SUCCESS, REJECTED, UNAVAILABLE }
    private record SupabasePasswordResult(SupabasePasswordStatus status, String accessToken,
                                          String refreshToken) { }
    private record ResolvedLoginUser(int userId, String username, String fullName, String email,
                                     String badgeId, String roleName, int locationId,
                                     String locationName, String locationTimezone) {
        private AuthenticatedUser authenticatedUser() {
            return new AuthenticatedUser(userId, username, fullName, email, roleName,
                    locationId, locationName, locationTimezone);
        }

        private LocalAuthCacheService.CachedUser cachedUser() {
            return new LocalAuthCacheService.CachedUser(userId, username, fullName, email, badgeId,
                    roleName, locationId, locationName, locationTimezone);
        }
    }

    private static final class ApiException extends Exception {
        private final int status;
        private final String code;
        private final boolean retryable;
        private ApiException(int status, String code, String message, boolean retryable) {
            super(message); this.status = status; this.code = code; this.retryable = retryable;
        }
    }
}
