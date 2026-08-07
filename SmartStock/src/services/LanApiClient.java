package services;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import Receipt.ReceiptData;
import Receipt.ReceiptItem;
import Receipt.CustomOrderSlipData;
import data.DatabaseConfig;
import data.EnvironmentProfile;
import models.DeviceInfo;
import models.CashDrawer;
import models.CashDrawerAssignment;
import models.CashDrawerHandover;
import models.CashDrawerSession;
import models.ManagedDevice;
import models.DeviceSessionRecord;
import utils.DeviceUtils;
import utils.SecureCredentialStore;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.math.BigDecimal;
import java.sql.Timestamp;
import ui.helpers.BlockingCallGuard;
import ui.helpers.PerformanceDiagnostics;
import ui.helpers.SessionDataCache;
import ui.helpers.UiTaskRunner;

/** Credential-free desktop client for the named SmartStock LAN and remote-gateway operations. */
public final class LanApiClient {
    private static final Gson GSON = LanJson.create();
    private static final String API_SESSION_SECRET = EnvironmentProfile.active().secretKey("lan-api-employee-session");
    private static final String API_TOKEN_EXPIRES_SECRET = EnvironmentProfile.active().secretKey("lan-api-device-token-expires");
    private static final String API_HOST_SECRET = EnvironmentProfile.active().secretKey("lan-api-server-host");
    private static final String API_PORT_SECRET = EnvironmentProfile.active().secretKey("lan-api-server-port");
    private static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final Object TRANSPORT_LOCK = new Object();
    private static volatile URI cachedBaseUri;
    private static volatile String cachedFingerprint;
    private static volatile String cachedDeviceToken;
    private static volatile String cachedEmployeeSession;
    private static volatile HttpClient cachedPinnedClient;
    private static volatile HttpClient cachedBootstrapClient;
    private static final AtomicBoolean CONNECTION_LOSS_REPORTED = new AtomicBoolean();
    private static volatile Runnable connectionLossHandler = () -> { };

    private LanApiClient() {
    }

    /** Installs the register-shell response to a genuine LAN transport outage. */
    public static void setConnectionLossHandler(Runnable handler) {
        connectionLossHandler = handler == null ? () -> { } : handler;
    }

    public static URI baseUri() {
        URI existing = cachedBaseUri;
        if (existing != null) return existing;
        DatabaseConfig config = DatabaseConfig.load();
        String savedHost = SecureCredentialStore.read(API_HOST_SECRET);
        String host = System.getProperty("smartstock.lan.api.host",
                savedHost == null ? config.serverHost() : savedHost);
        int savedPort = parsePort(SecureCredentialStore.read(API_PORT_SECRET), LanApiServer.DEFAULT_PORT);
        int port = Integer.getInteger("smartstock.lan.api.port", savedPort);
        URI resolved = URI.create("https://" + host + ":" + port);
        cachedBaseUri = resolved;
        SessionDataCache.setEndpoint(resolved.toString());
        return resolved;
    }

    /** Saves the administrator-selected LAN service endpoint without storing any database credential. */
    public static void configureEndpoint(String host, int port) throws Exception {
        String cleanHost = host == null ? "" : host.trim();
        if (cleanHost.isBlank() || cleanHost.contains("://") || cleanHost.contains("/") || cleanHost.contains("@")) {
            throw new IllegalArgumentException("Enter only the SmartStock server hostname or IP address.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("The SmartStock service port must be between 1 and 65535.");
        }
        SecureCredentialStore.write(API_HOST_SECRET, cleanHost);
        SecureCredentialStore.write(API_PORT_SECRET, String.valueOf(port));
        resetTransport(true, false);
    }

    public static List<DiscoveredServer> discoverServers() throws Exception {
        List<DiscoveredServer> results = new ArrayList<>();
        byte[] request = LanDiscoveryService.REQUEST.getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(800);
            socket.send(new DatagramPacket(request, request.length,
                    InetAddress.getByName("255.255.255.255"), LanDiscoveryService.DISCOVERY_PORT));
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                network.getInterfaceAddresses().forEach(address -> {
                    if (address.getBroadcast() == null) return;
                    try {
                        socket.send(new DatagramPacket(request, request.length,
                                address.getBroadcast(), LanDiscoveryService.DISCOVERY_PORT));
                    } catch (Exception ignored) { }
                });
            }
            long deadline = System.nanoTime() + Duration.ofMillis(750).toNanos();
            while (System.nanoTime() < deadline) {
                try {
                    byte[] buffer = new byte[2048];
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    JsonObject json = JsonParser.parseString(new String(packet.getData(), packet.getOffset(),
                            packet.getLength(), StandardCharsets.UTF_8)).getAsJsonObject();
                    DiscoveredServer advertised = GSON.fromJson(json, DiscoveredServer.class);
                    DiscoveredServer result = discoveredServerAtSource(
                            advertised, packet.getAddress().getHostAddress());
                    if (matchesActiveEnvironment(result)
                            && results.stream().noneMatch(item -> item.host().equals(result.host())
                            && item.port() == result.port())) {
                        results.add(result);
                    }
                } catch (SocketTimeoutException ex) {
                    break;
                }
            }
        }
        return results;
    }

    static DiscoveredServer discoveredServerAtSource(DiscoveredServer advertised, String sourceHost) {
        if (advertised == null) return null;
        // Keep the certificate hostname. The packet address is only a fallback for an
        // older response that omitted its host; replacing DNS with an IP breaks TLS SAN checks.
        String reachableHost = advertised.host() == null || advertised.host().isBlank()
                ? sourceHost : advertised.host();
        return new DiscoveredServer(advertised.service(), reachableHost, advertised.port(),
                advertised.environment(), advertised.storeName(), advertised.storeCode(),
                advertised.computerName(), advertised.serverId(),
                advertised.certificateFingerprint(), advertised.pairingProof(), advertised.previousPairingProof());
    }

    static boolean matchesActiveEnvironment(DiscoveredServer server) {
        if (server == null) return false;
        String environment = server.environment();
        if (environment == null || environment.isBlank()) {
            return EnvironmentProfile.active() == EnvironmentProfile.DEVELOPMENT;
        }
        return EnvironmentProfile.active().id().equalsIgnoreCase(environment.trim());
    }

    public static ServiceHealth checkHealth() throws Exception {
        Probe probe = probeUntrusted(baseUri());
        return new ServiceHealth(true, probe.certificateFingerprint());
    }

    public static boolean isServerReachable(String host,int port,String expectedFingerprint){
        try{
            Probe probe=probeUntrusted(URI.create("https://"+host+":"+port));
            return expectedFingerprint!=null&&LanSecurity.constantTimeEquals(expectedFingerprint,probe.certificateFingerprint());
        }catch(Exception ex){return false;}
    }

    /**
     * Administrator-only, one-time setup. The phrase must match the server
     * screen before the server certificate is pinned or any secret is sent.
     */
    public static PairingResult pairOnce(String administratorPhrase) throws Exception {
        String expected = normalizePhrase(administratorPhrase);
        URI endpoint = baseUri();
        Probe probe;
        try {
            probe = probeUntrusted(endpoint);
            if (!matchesPairingProof(expected, probe.certificateFingerprint(), probe.pairingProof(), probe.previousPairingProof())) {
                throw new IllegalStateException("Configured endpoint did not match the pairing phrase.");
            }
        } catch (Exception initialFailure) {
            DiscoveredServer match = discoverServers().stream()
                    .filter(server -> matchesPairingProof(expected, server.certificateFingerprint(),
                            server.pairingProof(), server.previousPairingProof()))
                    .findFirst()
                    .orElseThrow(() -> initialFailure);
            endpoint = URI.create("https://" + match.host() + ":" + match.port());
            probe = probeUntrusted(endpoint);
            SecureCredentialStore.write(API_HOST_SECRET, match.host());
            SecureCredentialStore.write(API_PORT_SECRET, String.valueOf(match.port()));
            resetTransport(true, false);
        }
        if (!matchesPairingProof(expected, probe.certificateFingerprint(), probe.pairingProof(), probe.previousPairingProof())) {
            throw new IllegalStateException("The pairing phrase does not match the SmartStock server.");
        }
        SecureCredentialStore.write(DeviceCredentialService.LAN_API_FINGERPRINT_SECRET,
                probe.certificateFingerprint());
        resetTransport(false, false);

        DeviceInfo device = DeviceUtils.collectDeviceInfo();
        String publicKey = DeviceCredentialService.pairingPublicKey();
        JsonObject request = new JsonObject();
        request.addProperty("pairingPhrase", expected);
        request.addProperty("installationId", device.getInstallationId());
        request.addProperty("deviceFingerprint", device.getFingerprint());
        request.addProperty("deviceName", device.getDeviceName());
        request.addProperty("hostname", device.getHostname());
        request.addProperty("appVersion", device.getAppVersion());
        request.addProperty("accessMode", DatabaseConfig.load().mode().name());
        request.addProperty("publicKey", publicKey);
        JsonObject data = post("/v1/devices/enroll", request, false, false);
        String challenge = DeviceCredentialService.decryptLanEnvelope(
                data.get("pairingChallengeEnvelope").getAsString());
        SecureCredentialStore.write(DeviceCredentialService.LAN_API_PAIRING_CHALLENGE_SECRET, challenge);
        String status = data.get("status").getAsString();
        if ("APPROVED".equals(status)) {
            claimApprovedCredential();
            return new PairingResult("PAIRED", false);
        }
        return new PairingResult(status, false);
    }

    /** Physical-server bootstrap; the endpoint accepts only loopback traffic in SERVER mode. */
    public static boolean ensureLocalServerCredential() throws Exception {
        if (DatabaseConfig.load().mode() != data.DatabaseMode.SERVER) return false;
        if (isPaired()) return true;
        SecureCredentialStore.write(API_HOST_SECRET, "127.0.0.1");
        resetTransport(true, false);
        Probe probe=probeUntrusted(baseUri());
        SecureCredentialStore.write(DeviceCredentialService.LAN_API_FINGERPRINT_SECRET,
                probe.certificateFingerprint());
        resetTransport(false, false);
        DeviceInfo device=DeviceUtils.collectDeviceInfo();
        JsonObject request=new JsonObject();
        request.addProperty("installationId",device.getInstallationId());
        request.addProperty("deviceFingerprint",device.getFingerprint());
        request.addProperty("deviceName",device.getDeviceName());
        request.addProperty("hostname",device.getHostname());
        request.addProperty("appVersion",device.getAppVersion());
        request.addProperty("publicKey",DeviceCredentialService.pairingPublicKey());
        JsonObject data=post("/v1/devices/local-claim",request,false,false);
        String token=DeviceCredentialService.decryptLanEnvelope(data.get("credentialEnvelope").getAsString());
        SecureCredentialStore.write(DeviceCredentialService.LAN_API_TOKEN_SECRET,token);
        cachedDeviceToken = token;
        SecureCredentialStore.write(API_TOKEN_EXPIRES_SECRET,data.get("expiresAt").getAsString());
        resetTransport(false, false);
        return true;
    }

    /** Called silently at startup; pending approval never becomes an employee prompt. */
    public static boolean claimApprovedCredential() throws Exception {
        String challenge = SecureCredentialStore.read(DeviceCredentialService.LAN_API_PAIRING_CHALLENGE_SECRET);
        String fingerprint = SecureCredentialStore.read(DeviceCredentialService.LAN_API_FINGERPRINT_SECRET);
        if (challenge == null || fingerprint == null) return false;
        DeviceInfo device = DeviceUtils.collectDeviceInfo();
        JsonObject request = new JsonObject();
        request.addProperty("installationId", device.getInstallationId());
        request.addProperty("publicKey", DeviceCredentialService.pairingPublicKey());
        request.addProperty("pairingChallenge", challenge);
        JsonObject data;
        try {
            data = post("/v1/devices/claim", request, false, false);
        } catch (LanApiException ex) {
            if ("PAIRING_PENDING".equals(ex.code())) return false;
            throw ex;
        }
        String token = DeviceCredentialService.decryptLanEnvelope(
                data.get("credentialEnvelope").getAsString());
        SecureCredentialStore.write(DeviceCredentialService.LAN_API_TOKEN_SECRET, token);
        cachedDeviceToken = token;
        SecureCredentialStore.write(API_TOKEN_EXPIRES_SECRET, data.get("expiresAt").getAsString());
        SecureCredentialStore.delete(DeviceCredentialService.LAN_API_PAIRING_CHALLENGE_SECRET);
        resetTransport(false, false);
        return true;
    }

    public static LoginResult loginWithSupabase(String accessToken, int locationId) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("supabaseAccessToken", accessToken);
        request.addProperty("locationId", locationId);
        JsonObject data = post("/v1/sessions/login", request, true, false);
        return saveSession(data);
    }

    public static LoginResult loginOffline(String identifier, char[] secret, int locationId) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("identifier", identifier);
        request.addProperty("secret", new String(secret));
        request.addProperty("locationId", locationId);
        try {
            return saveSession(post("/v1/sessions/login", request, true, false));
        } finally {
            java.util.Arrays.fill(secret, '\0');
        }
    }

    /**
     * Normal employee login. The server chooses Supabase Auth when reachable
     * and the server-held password/PIN verifier when internet is unavailable.
     */
    public static LoginResult loginWithCredentials(String identifier, char[] secret, int locationId) throws Exception {
        return loginOffline(identifier, secret, locationId);
    }

    public static List<RemoteStore> loadRemoteStores() throws Exception {
        JsonObject data = post("/v1/sessions/stores", new JsonObject(), true, true);
        RemoteStore[] stores = GSON.fromJson(data.getAsJsonArray("stores"), RemoteStore[].class);
        return stores == null ? List.of() : List.of(stores);
    }

    public static LoginResult switchRemoteStore(int locationId) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("locationId", locationId);
        return saveSession(post("/v1/sessions/switch-store", request, true, true));
    }

    public static List<RemoteCommand> loadRemoteCommands() throws Exception {
        JsonObject data = post("/v1/remote/commands", new JsonObject(), true, true);
        RemoteCommand[] commands = GSON.fromJson(data.getAsJsonArray("commands"), RemoteCommand[].class);
        return commands == null ? List.of() : List.of(commands);
    }

    public static BadgeStatus badgeStatus(String badgeId, int locationId) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("badgeId", badgeId);
        request.addProperty("locationId", locationId);
        return GSON.fromJson(post("/v1/sessions/badge-status", request, true, false), BadgeStatus.class);
    }

    public static boolean isBadgePinConfigured(String badgeId, int locationId) throws Exception {
        return badgeStatus(badgeId, locationId).pinConfigured();
    }

    public static LoginResult setupBadgePin(String badgeId, char[] accountPassword,
                                            char[] pin, int locationId) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("badgeId", badgeId);
        request.addProperty("accountPassword", new String(accountPassword));
        request.addProperty("pin", new String(pin));
        request.addProperty("locationId", locationId);
        try {
            return saveSession(post("/v1/sessions/badge-pin-setup", request, true, false));
        } finally {
            java.util.Arrays.fill(accountPassword, '\0');
            java.util.Arrays.fill(pin, '\0');
        }
    }

    public static JsonObject refreshSession() throws Exception {
        JsonObject data = post("/v1/sessions/refresh", new JsonObject(), true, true);
        if (data.has("sessionToken")) {
            saveEmployeeSession(data.get("sessionToken").getAsString(),
                    data.has("persistentLoginAllowed") && data.get("persistentLoginAllowed").getAsBoolean());
        }
        return data;
    }

    public static LoginResult refreshLoginSession() throws Exception {
        JsonObject data = post("/v1/sessions/refresh", new JsonObject(), true, true);
        if (data.has("sessionToken")) {
            saveEmployeeSession(data.get("sessionToken").getAsString(),
                    data.has("persistentLoginAllowed") && data.get("persistentLoginAllowed").getAsBoolean());
        }
        return GSON.fromJson(data, LoginResult.class);
    }

    public static SessionPolicy loadSessionPolicy() throws Exception {
        return GSON.fromJson(post("/v1/sessions/policy", new JsonObject(), true, true),
                SessionPolicy.class);
    }

    public static boolean hasEmployeeSession() {
        return employeeSession() != null;
    }

    public static void clearEmployeeSession() {
        SecureCredentialStore.delete(API_SESSION_SECRET);
        cachedEmployeeSession = null;
        resetTransport(false, false);
    }

    public static ApprovalResult requestManagerApproval(String managerIdentifier, char[] password,
                                                        String permissionKey, String actionKey,
                                                        String resourceIdentity) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("managerIdentifier", managerIdentifier);
        request.addProperty("password", new String(password));
        request.addProperty("permissionKey", permissionKey);
        request.addProperty("actionKey", actionKey);
        request.addProperty("resourceHash", LanSecurity.sha256(resourceIdentity == null ? "" : resourceIdentity));
        try {
            JsonObject data = post("/v1/approvals", request, true, true);
            return GSON.fromJson(data, ApprovalResult.class);
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    public static List<CatalogProduct> searchCatalog(String query) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("query", query == null ? "" : query);
        JsonObject data = post("/v1/catalog/search", request, true, true);
        CatalogProduct[] products = GSON.fromJson(data.getAsJsonArray("products"), CatalogProduct[].class);
        return products == null ? List.of() : List.of(products);
    }

    public static List<CustomerAccount> loadCustomerAccounts() throws Exception {
        JsonObject data = post("/v1/customers/accounts", new JsonObject(), true, true);
        CustomerAccount[] accounts = GSON.fromJson(data.getAsJsonArray("accounts"), CustomerAccount[].class);
        return accounts == null ? List.of() : List.of(accounts);
    }

    public static CashDrawerStatus currentCashDrawer() throws Exception {
        JsonObject data = post("/v1/cash-drawers/current", new JsonObject(), true, true);
        return GSON.fromJson(data, CashDrawerStatus.class);
    }

    public static SalesSettings loadSalesSettings() throws Exception {
        return GSON.fromJson(post("/v1/sales/settings", new JsonObject(), true, true), SalesSettings.class);
    }

    public static HeldCartCreated createHeldCart(HeldCartCreateRequest request, String idempotencyKey) throws Exception {
        requireIdempotencyKey(idempotencyKey, "Held-cart idempotency key is required.");
        return GSON.fromJson(post("/v1/held-carts/create", GSON.toJsonTree(request).getAsJsonObject(),
                true, true, Map.of("Idempotency-Key", idempotencyKey)), HeldCartCreated.class);
    }

    public static List<HeldCartSummary> listHeldCarts() throws Exception {
        JsonObject data = post("/v1/held-carts/list", new JsonObject(), true, true);
        HeldCartSummary[] rows = GSON.fromJson(data.getAsJsonArray("heldCarts"), HeldCartSummary[].class);
        return rows == null ? List.of() : List.of(rows);
    }

    public static HeldCartPayload resumeHeldCart(int heldCartId, String idempotencyKey) throws Exception {
        requireIdempotencyKey(idempotencyKey, "Held-cart resume idempotency key is required.");
        JsonObject request = new JsonObject(); request.addProperty("heldCartId", heldCartId);
        return GSON.fromJson(post("/v1/held-carts/resume", request, true, true,
                Map.of("Idempotency-Key", idempotencyKey)), HeldCartPayload.class);
    }

    public static List<SalesHistoryRow> loadSalesHistory(String search, String fromDate, String toDate) throws Exception {
        return loadSalesHistory(search,fromDate,toDate,null,false).transactions();
    }

    public static SalesHistoryResult loadSalesHistory(String search,String fromDate,String toDate,
                                                       Integer locationId,boolean allStores)throws Exception{
        JsonObject request = new JsonObject(); request.addProperty("search", search == null ? "" : search);
        request.addProperty("fromDate", fromDate == null ? "" : fromDate);
        request.addProperty("toDate", toDate == null ? "" : toDate);
        if(locationId!=null)request.addProperty("locationId",locationId);request.addProperty("allStores",allStores);
        JsonObject data = post("/v1/sales/history", request, true, true);
        SalesHistoryRow[] rows = GSON.fromJson(data.getAsJsonArray("transactions"), SalesHistoryRow[].class);
        CrossStoreStoreOption[]stores=GSON.fromJson(data.getAsJsonArray("stores"),CrossStoreStoreOption[].class);
        return new SalesHistoryResult(rows==null?List.of():List.of(rows),stores==null?List.of():List.of(stores),
                data.has("currentLocationId")?data.get("currentLocationId").getAsInt():0);
    }

    public static SaleHistoryDetails loadSaleHistoryDetails(int saleId) throws Exception {
        return loadSaleHistoryDetails(saleId,null);
    }
    public static SaleHistoryDetails loadSaleHistoryDetails(int saleId,Integer sourceLocationId)throws Exception{
        JsonObject request = new JsonObject(); request.addProperty("saleId", saleId);
        if(sourceLocationId!=null)request.addProperty("sourceLocationId",sourceLocationId);
        return GSON.fromJson(post("/v1/sales/details", request, true, true), SaleHistoryDetails.class);
    }

    public static InventoryLookups loadInventoryLookups(Integer categoryId) throws Exception {
        JsonObject request=new JsonObject(); if(categoryId!=null)request.addProperty("categoryId",categoryId);
        return GSON.fromJson(post("/v1/inventory/lookups",request,true,true),InventoryLookups.class);
    }

    public static List<LookupItem> searchReceivingItems(String query)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("query",query==null?"":query);
        JsonObject data=post("/v1/inventory/receiving-search",request,true,true);
        LookupItem[]rows=GSON.fromJson(data.getAsJsonArray("items"),LookupItem[].class);
        return rows==null?List.of():List.of(rows);
    }

    public static InventoryResult loadInventory(InventoryRequest request)throws Exception{
        return GSON.fromJson(post("/v1/inventory/list",GSON.toJsonTree(request).getAsJsonObject(),true,true),InventoryResult.class);
    }

    public static CrossStoreInventoryResult loadCrossStoreInventory(String query,Integer locationId)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("query",query==null?"":query);
        if(locationId!=null)request.addProperty("locationId",locationId);
        return GSON.fromJson(post("/v1/inventory/cross-store-search",request,true,true),CrossStoreInventoryResult.class);
    }

    public static InventoryDetails loadInventoryDetails(int productId)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("productId",productId);
        return GSON.fromJson(post("/v1/inventory/details",request,true,true),InventoryDetails.class);
    }

    public static List<ReceivingHistoryRow> loadReceivingHistory(String search,String fromDate,String toDate)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("search",search==null?"":search);
        request.addProperty("fromDate",fromDate==null?"":fromDate);request.addProperty("toDate",toDate==null?"":toDate);
        JsonObject data=post("/v1/inventory/receiving-history",request,true,true);
        ReceivingHistoryRow[]rows=GSON.fromJson(data.getAsJsonArray("records"),ReceivingHistoryRow[].class);
        return rows==null?List.of():List.of(rows);
    }

    public static ReceiveInventoryResult receiveInventory(ReceiveInventoryRequest request,String idempotencyKey)throws Exception{
        requireIdempotencyKey(idempotencyKey,"Inventory receiving idempotency key is required.");
        return GSON.fromJson(post("/v1/inventory/receive",GSON.toJsonTree(request).getAsJsonObject(),true,true,
                Map.of("Idempotency-Key",idempotencyKey)),ReceiveInventoryResult.class);
    }

    public static List<TransferLocation> loadTransferDestinations()throws Exception{
        JsonObject data=post("/v1/transfers/destinations",new JsonObject(),true,true);
        TransferLocation[]rows=GSON.fromJson(data.getAsJsonArray("locations"),TransferLocation[].class);
        return rows==null?List.of():List.of(rows);
    }

    public static List<TransferProduct> searchTransferProducts(String query)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("query",query==null?"":query);
        JsonObject data=post("/v1/transfers/products",request,true,true);
        TransferProduct[]rows=GSON.fromJson(data.getAsJsonArray("products"),TransferProduct[].class);
        return rows==null?List.of():List.of(rows);
    }

    public static List<IncomingTransfer> loadIncomingTransfers()throws Exception{
        JsonObject data=post("/v1/transfers/incoming",new JsonObject(),true,true);
        IncomingTransfer[]rows=GSON.fromJson(data.getAsJsonArray("transfers"),IncomingTransfer[].class);
        return rows==null?List.of():List.of(rows);
    }

    public static List<OutgoingTransfer> loadOutgoingTransfers()throws Exception{
        JsonObject data=post("/v1/transfers/outgoing",new JsonObject(),true,true);
        OutgoingTransfer[]rows=GSON.fromJson(data.getAsJsonArray("transfers"),OutgoingTransfer[].class);
        return rows==null?List.of():List.of(rows);
    }

    public static List<TransferDetailItem> loadTransferItems(long transferId)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("transferId",transferId);
        JsonObject data=post("/v1/transfers/items",request,true,true);
        TransferDetailItem[]rows=GSON.fromJson(data.getAsJsonArray("items"),TransferDetailItem[].class);
        return rows==null?List.of():List.of(rows);
    }

    public static CreateTransferResult createTransfer(CreateTransferRequest request,String idempotencyKey)throws Exception{
        requireIdempotencyKey(idempotencyKey,"Transfer idempotency key is required.");
        return GSON.fromJson(post("/v1/transfers/create",GSON.toJsonTree(request).getAsJsonObject(),true,true,
                Map.of("Idempotency-Key",idempotencyKey)),CreateTransferResult.class);
    }

    public static ReceiveTransferResult receiveTransfer(long transferId,String idempotencyKey)throws Exception{
        requireIdempotencyKey(idempotencyKey,"Transfer receiving idempotency key is required.");
        JsonObject request=new JsonObject();request.addProperty("transferId",transferId);
        return GSON.fromJson(post("/v1/transfers/receive",request,true,true,
                Map.of("Idempotency-Key",idempotencyKey)),ReceiveTransferResult.class);
    }

    public static DepartmentListResult loadDepartments(String search) throws Exception {
        JsonObject request = new JsonObject(); request.addProperty("search", search == null ? "" : search);
        return GSON.fromJson(post("/v1/catalog/departments/list", request, true, true), DepartmentListResult.class);
    }

    public static SavedDepartment saveDepartment(DepartmentSaveRequest request, String idempotencyKey) throws Exception {
        requireIdempotencyKey(idempotencyKey, "Department idempotency key is required.");
        return GSON.fromJson(post("/v1/catalog/departments/save", GSON.toJsonTree(request).getAsJsonObject(),
                true, true, Map.of("Idempotency-Key", idempotencyKey)), SavedDepartment.class);
    }

    public static List<VendorRecord> loadVendors(String search) throws Exception {
        JsonObject request = new JsonObject(); request.addProperty("search", search == null ? "" : search);
        JsonObject data = post("/v1/catalog/vendors/list", request, true, true);
        VendorRecord[] rows = GSON.fromJson(data.getAsJsonArray("vendors"), VendorRecord[].class);
        return rows == null ? List.of() : List.of(rows);
    }

    public static SavedVendor saveVendor(VendorSaveRequest request, String idempotencyKey) throws Exception {
        requireIdempotencyKey(idempotencyKey, "Vendor idempotency key is required.");
        return GSON.fromJson(post("/v1/catalog/vendors/save", GSON.toJsonTree(request).getAsJsonObject(),
                true, true, Map.of("Idempotency-Key", idempotencyKey)), SavedVendor.class);
    }

    public static List<CustomerTypeRecord> loadCustomerTypes(String search, boolean activeOnly) throws Exception {
        JsonObject request = new JsonObject(); request.addProperty("search", search == null ? "" : search);
        request.addProperty("activeOnly", activeOnly);
        JsonObject data = post("/v1/catalog/customer-types/list", request, true, true);
        CustomerTypeRecord[] rows = GSON.fromJson(data.getAsJsonArray("customerTypes"), CustomerTypeRecord[].class);
        return rows == null ? List.of() : List.of(rows);
    }

    public static SavedCustomerType saveCustomerType(CustomerTypeSaveRequest request,
                                                      String idempotencyKey) throws Exception {
        requireIdempotencyKey(idempotencyKey, "Customer type idempotency key is required.");
        return GSON.fromJson(post("/v1/catalog/customer-types/save", GSON.toJsonTree(request).getAsJsonObject(),
                true, true, Map.of("Idempotency-Key", idempotencyKey)), SavedCustomerType.class);
    }

    public static List<EditableProduct> searchEditableProducts(String search) throws Exception {
        JsonObject request = new JsonObject(); request.addProperty("search", search == null ? "" : search);
        JsonObject data = post("/v1/products/edit-search", request, true, true);
        EditableProduct[] rows = GSON.fromJson(data.getAsJsonArray("products"), EditableProduct[].class);
        return rows == null ? List.of() : List.of(rows);
    }

    public static List<PriceTagCatalogItem> searchPriceTagItems(String search) throws Exception {
        JsonObject request = new JsonObject(); request.addProperty("search", search == null ? "" : search);
        JsonObject data = post("/v1/products/price-tags", request, true, true);
        PriceTagCatalogItem[] rows = GSON.fromJson(data.getAsJsonArray("items"), PriceTagCatalogItem[].class);
        return rows == null ? List.of() : List.of(rows);
    }

    public static PriceTagSettings loadPriceTagSettings() throws Exception {
        return GSON.fromJson(post("/v1/products/price-tag-settings", new JsonObject(), true, true),
                PriceTagSettings.class);
    }

    public static SavedProduct createProduct(ProductSaveRequest request, String idempotencyKey) throws Exception {
        return saveProduct("/v1/products/create", request, idempotencyKey);
    }

    public static SavedProduct updateProduct(ProductSaveRequest request, String idempotencyKey) throws Exception {
        return saveProduct("/v1/products/update", request, idempotencyKey);
    }

    public static SyncStatusSnapshot loadSyncStatus() throws Exception {
        return GSON.fromJson(post("/v1/sync/status", new JsonObject(), true, true), SyncStatusSnapshot.class);
    }

    public static SyncStatusSnapshot runSyncNow() throws Exception {
        return GSON.fromJson(post("/v1/sync/run", new JsonObject(), true, true), SyncStatusSnapshot.class);
    }

    public static void resolveSyncConflict(long conflictId) throws Exception {
        JsonObject request = new JsonObject(); request.addProperty("conflictId", conflictId);
        post("/v1/sync/resolve", request, true, true,
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
    }

    public static WorkstationSettings loadWorkstationSettings()throws Exception{
        return GSON.fromJson(post("/v1/workstation/settings",new JsonObject(),true,true),WorkstationSettings.class);
    }
    public static String updateWorkstationDeviceCode(String code)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("deviceCode",code);
        return post("/v1/workstation/device-code",request,true,true).get("deviceCode").getAsString();
    }
    public static String updateWorkstationTimezone(String timezone)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("timezone",timezone);
        return post("/v1/workstation/timezone",request,true,true).get("timezone").getAsString();
    }

    public static List<CustomerAccountRecord> loadCustomerAccountRecords()throws Exception{
        JsonObject data=post("/v1/customer-accounts/list",new JsonObject(),true,true);
        CustomerAccountRecord[] rows=GSON.fromJson(data.getAsJsonArray("accounts"),CustomerAccountRecord[].class);
        return rows==null?List.of():List.of(rows);
    }
    public static CustomerAccountRecord loadCustomerAccountDetails(int customerId)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("customerId",customerId);
        return GSON.fromJson(post("/v1/customer-accounts/details",request,true,true),CustomerAccountRecord.class);
    }
    public static SavedCustomerAccount saveCustomerAccount(CustomerAccountSaveRequest request,String key)throws Exception{
        requireIdempotencyKey(key,"Customer account idempotency key is required.");
        return GSON.fromJson(post("/v1/customer-accounts/save",GSON.toJsonTree(request).getAsJsonObject(),true,true,
                Map.of("Idempotency-Key",key)),SavedCustomerAccount.class);
    }
    public static CustomerAccountAdjustmentResult adjustCustomerAccount(CustomerAccountAdjustmentRequest request,String key)throws Exception{
        requireIdempotencyKey(key,"Customer account adjustment idempotency key is required.");
        return GSON.fromJson(post("/v1/customer-accounts/adjust",GSON.toJsonTree(request).getAsJsonObject(),true,true,
                Map.of("Idempotency-Key",key)),CustomerAccountAdjustmentResult.class);
    }
    public static CustomerTransactionResult loadCustomerTransactions(int customerId)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("customerId",customerId);
        return GSON.fromJson(post("/v1/customer-accounts/transactions",request,true,true),CustomerTransactionResult.class);
    }
    public static CustomerPaymentResult loadCustomerPayments(int customerId)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("customerId",customerId);
        return GSON.fromJson(post("/v1/customer-accounts/payments",request,true,true),CustomerPaymentResult.class);
    }
    public static AccountPaymentReceiptPayload loadAccountPaymentReceipt(int customerId,long transactionId)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("customerId",customerId);request.addProperty("transactionId",transactionId);
        return GSON.fromJson(post("/v1/customer-accounts/payment-receipt",request,true,true),AccountPaymentReceiptPayload.class);
    }
    public static void changeEmployeePin(char[]pin)throws Exception{
        JsonObject request=new JsonObject();request.addProperty("pin",new String(pin));post("/v1/employees/change-pin",request,true,true);
    }
    public static ChangeBasketState loadChangeBasketState()throws Exception{return GSON.fromJson(post("/v1/cash/change-basket/state",new JsonObject(),true,true),ChangeBasketState.class);}
    public static long updateChangeBasket(Map<Integer,Integer>counts,String key)throws Exception{requireIdempotencyKey(key,"Change basket idempotency key is required.");
        JsonObject request=new JsonObject();request.add("denominationCounts",GSON.toJsonTree(counts));request.addProperty("notes","");
        return post("/v1/cash/change-basket/update",request,true,true,Map.of("Idempotency-Key",key)).get("updateId").getAsLong();}
    public static CashDrawerRegisterState loadCashDrawerRegisterState()throws Exception{return GSON.fromJson(post("/v1/cash/drawer/state",new JsonObject(),true,true),CashDrawerRegisterState.class);}
    public static CashDrawerSession openCashDrawer(String key)throws Exception{return cashDrawerSessionMutation("/v1/cash/drawer/open",new JsonObject(),key);}
    public static CashDrawerHandover handoverCashDrawer(long sessionId,BigDecimal count,String notes,String key)throws Exception{
        JsonObject r=new JsonObject();r.addProperty("sessionId",sessionId);r.addProperty("countedCash",count);r.addProperty("notes",notes);
        return GSON.fromJson(post("/v1/cash/drawer/handover",r,true,true,Map.of("Idempotency-Key",key)).get("handover"),CashDrawerHandover.class);}
    public static CashDrawerCloseResult closeCashDrawer(long sessionId,BigDecimal count,String notes,String key)throws Exception{
        JsonObject r=new JsonObject();r.addProperty("sessionId",sessionId);r.addProperty("countedCash",count);r.addProperty("notes",notes);
        return GSON.fromJson(post("/v1/cash/drawer/close",r,true,true,Map.of("Idempotency-Key",key)),CashDrawerCloseResult.class);}
    public static List<CashDrawerSession> loadRecentCashDrawers()throws Exception{JsonObject d=post("/v1/cash/drawer/recent",new JsonObject(),true,true);
        CashDrawerSession[] rows=GSON.fromJson(d.getAsJsonArray("sessions"),CashDrawerSession[].class);return rows==null?List.of():List.of(rows);}
    public static CashDrawerSession reviseCashDrawer(long sessionId,BigDecimal count,String notes,String key)throws Exception{
        JsonObject r=new JsonObject();r.addProperty("sessionId",sessionId);r.addProperty("countedCash",count);r.addProperty("notes",notes);return cashDrawerSessionMutation("/v1/cash/drawer/revise",r,key);}
    public static CashDrawerAdminState loadCashDrawerAdminState(Integer locationId,boolean includeInactive)throws Exception{JsonObject r=new JsonObject();
        if(locationId!=null)r.addProperty("locationId",locationId);r.addProperty("includeInactive",includeInactive);return GSON.fromJson(post("/v1/cash/drawer/admin-state",r,true,true),CashDrawerAdminState.class);}
    public static long saveCashDrawer(CashDrawerSaveRequest request,String key)throws Exception{return post("/v1/cash/drawer/save",GSON.toJsonTree(request).getAsJsonObject(),true,true,Map.of("Idempotency-Key",key)).get("drawerId").getAsLong();}
    public static void assignCashDrawer(long drawerId,int locationId,String deviceId,String notes,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("drawerId",drawerId);r.addProperty("locationId",locationId);r.addProperty("deviceId",deviceId);r.addProperty("notes",notes);post("/v1/cash/drawer/assign",r,true,true,Map.of("Idempotency-Key",key));}
    public static void unassignCashDrawer(long assignmentId,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("assignmentId",assignmentId);post("/v1/cash/drawer/unassign",r,true,true,Map.of("Idempotency-Key",key));}
    public static BigDecimal saveCashDrawerChangeTarget(int locationId,BigDecimal target,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("locationId",locationId);r.addProperty("targetAmount",target);return post("/v1/cash/drawer/change-target",r,true,true,Map.of("Idempotency-Key",key)).get("targetAmount").getAsBigDecimal();}
    private static CashDrawerSession cashDrawerSessionMutation(String path,JsonObject request,String key)throws Exception{return GSON.fromJson(post(path,request,true,true,Map.of("Idempotency-Key",key)).get("session"),CashDrawerSession.class);}
    public static List<ManagedDevice> loadManagedDevices()throws Exception{JsonObject d=post("/v1/security/devices/list",new JsonObject(),true,true);ManagedDevice[]a=GSON.fromJson(d.getAsJsonArray("devices"),ManagedDevice[].class);return a==null?List.of():List.of(a);}
    public static List<DeviceSessionRecord> loadDeviceSessions(String deviceId)throws Exception{JsonObject r=new JsonObject();r.addProperty("deviceId",deviceId);JsonObject d=post("/v1/security/devices/sessions",r,true,true);DeviceSessionRecord[]a=GSON.fromJson(d.getAsJsonArray("sessions"),DeviceSessionRecord[].class);return a==null?List.of():List.of(a);}
    public static void updateManagedDevice(DeviceAdminUpdate request,String key)throws Exception{post("/v1/security/devices/update",GSON.toJsonTree(request).getAsJsonObject(),true,true,Map.of("Idempotency-Key",key));}
    public static ServerAdminState loadServerAdminState()throws Exception{
        JsonObject d=post("/v1/security/servers/list",new JsonObject(),true,true);
        ServerRecord[]a=GSON.fromJson(d.getAsJsonArray("servers"),ServerRecord[].class);
        ServerEvent[]events=GSON.fromJson(d.getAsJsonArray("events"),ServerEvent[].class);
        return new ServerAdminState(a==null?List.of():List.of(a),events==null?List.of():List.of(events),optionalString(d,"currentServerInstanceId"),optionalString(d,"localRole"));
    }
    public static JsonObject updateManagedServer(ServerAdminUpdate request,String key)throws Exception{
        return post("/v1/security/servers/update",GSON.toJsonTree(request).getAsJsonObject(),true,true,Map.of("Idempotency-Key",key));
    }
    public static JsonObject prepareStandby(String serverInstanceId,String key)throws Exception{return serverLifecycle("/v1/security/servers/prepare-standby",serverInstanceId,null,false,key);}
    public static JsonObject beginServerHandoff(String sourceId,String targetId,String key)throws Exception{return serverLifecycle("/v1/security/servers/begin-handoff",sourceId,targetId,false,key);}
    public static JsonObject loadServerHandoffStatus(String serverInstanceId,String handoffId)throws Exception{JsonObject r=new JsonObject();if(serverInstanceId!=null)r.addProperty("serverInstanceId",serverInstanceId);if(handoffId!=null)r.addProperty("handoffId",handoffId);return post("/v1/security/servers/handoff-status",r,true,true);}
    public static JsonObject emergencyServerTakeover(String standbyId,String key)throws Exception{return serverLifecycle("/v1/security/servers/emergency-takeover",standbyId,null,true,key);}
    public static JsonObject retireServer(String serverInstanceId,String key)throws Exception{return serverLifecycle("/v1/security/servers/retire",serverInstanceId,null,false,key);}
    private static JsonObject serverLifecycle(String path,String serverId,String targetId,boolean warning,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("serverInstanceId",serverId);if(targetId!=null)r.addProperty("targetServerInstanceId",targetId);r.addProperty("idempotencyKey",key);r.addProperty("warningAcknowledged",warning);return post(path,r,true,true,Map.of("Idempotency-Key",key));}
    public static DeviceSecurityStatus loadDeviceSecurityStatus()throws Exception{return GSON.fromJson(post("/v1/security/status",new JsonObject(),true,true),DeviceSecurityStatus.class);}
    public static List<LocationRecord> loadLocationRecords(String search)throws Exception{JsonObject r=new JsonObject();r.addProperty("search",search);JsonObject d=post("/v1/locations/list",r,true,true);LocationRecord[]a=GSON.fromJson(d.getAsJsonArray("locations"),LocationRecord[].class);return a==null?List.of():List.of(a);}
    public static int saveLocationRecord(LocationRecord r,String key)throws Exception{return post("/v1/locations/save",GSON.toJsonTree(r).getAsJsonObject(),true,true,Map.of("Idempotency-Key",key)).get("locationId").getAsInt();}
    public static EmailProcessingResult processLocationEmailOutbox()throws Exception{return GSON.fromJson(post("/v1/locations/process-email",new JsonObject(),true,true),EmailProcessingResult.class);}
    public static ReportDataService.FilterOptions loadReportOptions()throws Exception{return GSON.fromJson(post("/v1/reports/options",new JsonObject(),true,true).get("options"),ReportDataService.FilterOptions.class);}
    public static ReportDataService.Snapshot loadReportSnapshot(ReportDataService.Filters filters,boolean allRevenue)throws Exception{JsonObject r=new JsonObject();r.add("filters",GSON.toJsonTree(filters));r.addProperty("allRevenue",allRevenue);return GSON.fromJson(post("/v1/reports/load",r,true,true).get("snapshot"),ReportDataService.Snapshot.class);}
    public static OrderReport loadOrderReport(java.time.ZonedDateTime from,java.time.ZonedDateTime to)throws Exception{return GSON.fromJson(post("/v1/reports/orders",range(from,to),true,true),OrderReport.class);}
    public static InvoiceReport loadInvoiceReport(java.time.ZonedDateTime from,java.time.ZonedDateTime to)throws Exception{return GSON.fromJson(post("/v1/reports/invoices",range(from,to),true,true),InvoiceReport.class);}
    private static JsonObject range(java.time.ZonedDateTime from,java.time.ZonedDateTime to){JsonObject r=new JsonObject();r.addProperty("from",from.toString());r.addProperty("to",to.toString());return r;}
    public static List<MaintenancePart> loadMaintenanceParts(String search)throws Exception{JsonObject r=new JsonObject();r.addProperty("search",search);JsonObject d=post("/v1/maintenance/parts/list",r,true,true);MaintenancePart[]a=GSON.fromJson(d.getAsJsonArray("parts"),MaintenancePart[].class);return a==null?List.of():List.of(a);}
    public static int saveMaintenancePart(MaintenancePart r,String key)throws Exception{return post("/v1/maintenance/parts/save",GSON.toJsonTree(r).getAsJsonObject(),true,true,Map.of("Idempotency-Key",key)).get("partId").getAsInt();}
    public static void deleteMaintenancePart(int id,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("partId",id);post("/v1/maintenance/parts/delete",r,true,true,Map.of("Idempotency-Key",key));}
    public static LanMachineService.State loadMachineState(String search)throws Exception{JsonObject r=new JsonObject();r.addProperty("search",search==null?"":search);return GSON.fromJson(post("/v1/maintenance/machines/state",r,true,true).get("state"),LanMachineService.State.class);}
    public static LanMachineService.Detail loadMachineDetail(int id)throws Exception{JsonObject r=new JsonObject();r.addProperty("machineId",id);return GSON.fromJson(post("/v1/maintenance/machines/detail",r,true,true).get("detail"),LanMachineService.Detail.class);}
    public static int saveMachine(LanMachineService.Machine machine,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("action","SAVE");r.add("machine",GSON.toJsonTree(machine));return post("/v1/maintenance/machines/update",r,true,true,Map.of("Idempotency-Key",key)).get("machineId").getAsInt();}
    public static void updateMachineLink(String action,Integer machineId,Integer partId,Long linkId,String notes,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("action",action);if(machineId!=null)r.addProperty("machineId",machineId);if(partId!=null)r.addProperty("partId",partId);if(linkId!=null)r.addProperty("linkId",linkId);if(notes!=null)r.addProperty("notes",notes);post("/v1/maintenance/machines/update",r,true,true,Map.of("Idempotency-Key",key));}
    public static LanMaintenanceWorkflowService.State loadMaintenanceWorkflow(String search,String filter)throws Exception{JsonObject r=new JsonObject();r.addProperty("search",search==null?"":search);r.addProperty("filter",filter==null?"Active":filter);return GSON.fromJson(post("/v1/maintenance/workflow/state",r,true,true).get("state"),LanMaintenanceWorkflowService.State.class);}
    public static LanMaintenanceWorkflowService.Detail loadMaintenanceDetail(String type,int id)throws Exception{JsonObject r=new JsonObject();r.addProperty("type",type);r.addProperty("id",id);return GSON.fromJson(post("/v1/maintenance/workflow/detail",r,true,true).get("detail"),LanMaintenanceWorkflowService.Detail.class);}
    public static int saveMaintenanceWorkflow(String action,Object value,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("action",action);r.add("SAVE_LOG".equals(action)?"log":"ticket",GSON.toJsonTree(value));return post("/v1/maintenance/workflow/update",r,true,true,Map.of("Idempotency-Key",key)).get("id").getAsInt();}
    public static void closeMaintenanceTicket(int id,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("action","CLOSE_TICKET");r.addProperty("id",id);post("/v1/maintenance/workflow/update",r,true,true,Map.of("Idempotency-Key",key));}
    public static List<CustomOrderDataService.CustomItemOption> loadCustomOrderItems()throws Exception{JsonObject d=customOrderQuery("ITEMS",new JsonObject());CustomOrderDataService.CustomItemOption[]a=GSON.fromJson(d.getAsJsonArray("items"),CustomOrderDataService.CustomItemOption[].class);return a==null?List.of():List.of(a);}
    public static List<CustomOrderDataService.VariantOption> loadCustomOrderVariants(long itemId)throws Exception{JsonObject r=new JsonObject();r.addProperty("customItemId",itemId);JsonObject d=customOrderQuery("VARIANTS",r);CustomOrderDataService.VariantOption[]a=GSON.fromJson(d.getAsJsonArray("variants"),CustomOrderDataService.VariantOption[].class);return a==null?List.of():List.of(a);}
    public static List<CustomOrderDataService.PrintMaterialOption> loadCustomOrderPrintMaterials()throws Exception{JsonObject d=customOrderQuery("PRINT_MATERIALS",new JsonObject());CustomOrderDataService.PrintMaterialOption[]a=GSON.fromJson(d.getAsJsonArray("materials"),CustomOrderDataService.PrintMaterialOption[].class);return a==null?List.of():List.of(a);}
    public static List<CustomOrderDataService.PrintSizePresetOption> loadCustomOrderPrintSizePresets(long materialId)throws Exception{JsonObject r=new JsonObject();r.addProperty("printMaterialId",materialId);JsonObject d=customOrderQuery("PRINT_PRESETS",r);CustomOrderDataService.PrintSizePresetOption[]a=GSON.fromJson(d.getAsJsonArray("presets"),CustomOrderDataService.PrintSizePresetOption[].class);return a==null?List.of():List.of(a);}
    public static List<String> loadCustomOrderDesignPlacements()throws Exception{JsonObject d=customOrderQuery("PLACEMENTS",new JsonObject());String[]a=GSON.fromJson(d.getAsJsonArray("placements"),String[].class);return a==null?List.of():List.of(a);}
    public static List<CustomOrderDataService.CustomerOption> searchCustomOrderCustomers(String search)throws Exception{JsonObject r=new JsonObject();r.addProperty("search",search==null?"":search);JsonObject d=customOrderQuery("CUSTOMERS",r);CustomOrderDataService.CustomerOption[]a=GSON.fromJson(d.getAsJsonArray("customers"),CustomOrderDataService.CustomerOption[].class);return a==null?List.of():List.of(a);}
    public static List<CustomOrderDataService.EmployeeOption> loadCustomOrderEmployees()throws Exception{JsonObject d=customOrderQuery("EMPLOYEES",new JsonObject());CustomOrderDataService.EmployeeOption[]a=GSON.fromJson(d.getAsJsonArray("employees"),CustomOrderDataService.EmployeeOption[].class);return a==null?List.of():List.of(a);}
    public static CustomOrderDataService.LookupResult lookupCustomOrderItem(String search)throws Exception{JsonObject r=new JsonObject();r.addProperty("search",search==null?"":search);JsonObject d=customOrderQuery("LOOKUP",r);return d.has("match")&&!d.get("match").isJsonNull()?GSON.fromJson(d.get("match"),CustomOrderDataService.LookupResult.class):null;}
    private static JsonObject customOrderQuery(String action,JsonObject request)throws Exception{request.addProperty("action",action);return post("/v1/custom-orders/catalog",request,true,true);}
    public static String saveCustomOrder(CustomOrderDataService.OrderSaveRequest request,String key)throws Exception{JsonObject body=GSON.toJsonTree(request).getAsJsonObject();return post("/v1/custom-orders/create",body,true,true,Map.of("Idempotency-Key",key)).get("orderNumber").getAsString();}
    public static LanOrdersDashboardService.Dashboard loadCustomOrderDashboard()throws Exception{return GSON.fromJson(post("/v1/custom-orders/dashboard",new JsonObject(),true,true).get("dashboard"),LanOrdersDashboardService.Dashboard.class);}
    public static void assignCustomOrder(long orderId,Integer employeeId,String status,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("orderId",orderId);if(employeeId!=null)r.addProperty("employeeId",employeeId);r.addProperty("status",status);post("/v1/custom-orders/assign",r,true,true,Map.of("Idempotency-Key",key));}
    public static CustomOrderSlipData loadCustomOrderSlip(String number)throws Exception{JsonObject r=new JsonObject();r.addProperty("orderNumber",number);return GSON.fromJson(post("/v1/documents/custom-order-slip",r,true,true).get("slip"),CustomOrderSlipData.class);}
    public static TimeClockAutoCloseService.AutoCloseSettings loadTimeClockAutoCloseSettings()throws Exception{return GSON.fromJson(post("/v1/time-clock/auto-close/settings",new JsonObject(),true,true).get("settings"),TimeClockAutoCloseService.AutoCloseSettings.class);}
    public static void saveTimeClockAutoCloseSettings(TimeClockAutoCloseService.AutoCloseSettings settings,String key)throws Exception{JsonObject r=new JsonObject();r.add("settings",GSON.toJsonTree(settings));post("/v1/time-clock/auto-close/save-settings",r,true,true,Map.of("Idempotency-Key",key));}
    public static List<TimeClockAutoCloseService.PendingReview> loadPendingTimeClockReviews()throws Exception{JsonObject d=post("/v1/time-clock/auto-close/reviews",new JsonObject(),true,true);TimeClockAutoCloseService.PendingReview[] rows=GSON.fromJson(d.getAsJsonArray("reviews"),TimeClockAutoCloseService.PendingReview[].class);return rows==null?List.of():List.of(rows);}
    public static TimeClockAutoCloseService.EmployeeAutoCloseNotice loadLatestTimeClockNotice()throws Exception{JsonObject d=post("/v1/time-clock/auto-close/notice",new JsonObject(),true,true);return d.has("notice")&&!d.get("notice").isJsonNull()?GSON.fromJson(d.get("notice"),TimeClockAutoCloseService.EmployeeAutoCloseNotice.class):null;}
    public static void confirmTimeClockAutoClose(long clockId,String reason,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("clockId",clockId);r.addProperty("reason",reason);post("/v1/time-clock/auto-close/confirm",r,true,true,Map.of("Idempotency-Key",key));}
    public static void correctTimeClockAutoClose(long clockId,java.time.ZoneId zone,TimeClockAutoCloseService.Correction correction,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("clockId",clockId);r.addProperty("zoneId",zone.getId());r.add("correction",GSON.toJsonTree(correction));post("/v1/time-clock/auto-close/correct",r,true,true,Map.of("Idempotency-Key",key));}
    public static managers.TimeClockManager.TimeClockDashboard loadTimeClockDashboard()throws Exception{return GSON.fromJson(post("/v1/time-clock/dashboard",new JsonObject(),true,true).get("dashboard"),managers.TimeClockManager.TimeClockDashboard.class);}
    public static TimeClockPunchState loadTimeClockPunchState()throws Exception{return GSON.fromJson(post("/v1/time-clock/punch-state",new JsonObject(),true,true),TimeClockPunchState.class);}
    public static void timeClockPunch(String action,String approvalToken,String approvalReason,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("action",action);if(approvalToken!=null)r.addProperty("approvalToken",approvalToken);if(approvalReason!=null)r.addProperty("approvalReason",approvalReason);post("/v1/time-clock/punch",r,true,true,Map.of("Idempotency-Key",key));}
    public static managers.TimeClockManager.PayrollDashboard loadPayrollDashboard()throws Exception{return GSON.fromJson(post("/v1/payroll/dashboard",new JsonObject(),true,true).get("dashboard"),managers.TimeClockManager.PayrollDashboard.class);}
    public static void addPayrollBonuses(List<managers.TimeClockManager.PayrollSummary> summaries,BigDecimal amount,String reason,String key)throws Exception{JsonObject r=new JsonObject();r.add("summaries",GSON.toJsonTree(summaries));r.addProperty("amount",amount);r.addProperty("reason",reason);post("/v1/payroll/bonus",r,true,true,Map.of("Idempotency-Key",key));}
    public static void markPayrollPaid(managers.TimeClockManager.PayrollSummary summary,String method,String reference,String key)throws Exception{JsonObject r=new JsonObject();r.add("summary",GSON.toJsonTree(summary));r.addProperty("paymentMethod",method);if(reference!=null)r.addProperty("paymentReference",reference);post("/v1/payroll/pay",r,true,true,Map.of("Idempotency-Key",key));}
    public static BadgePrintService.EmployeeBadgeData loadEmployeeBadgeData(int userId)throws Exception{JsonObject r=new JsonObject();r.addProperty("userId",userId);return GSON.fromJson(post("/v1/employees/badge-data",r,true,true).get("employee"),BadgePrintService.EmployeeBadgeData.class);}
    public static void incrementEmployeeBadgePrintCount(int userId,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("userId",userId);post("/v1/employees/badge-printed",r,true,true,Map.of("Idempotency-Key",key));}
    public static LanEmployeeAdminService.State loadEmployeeAdminState(Integer userId)throws Exception{JsonObject r=new JsonObject();if(userId!=null)r.addProperty("userId",userId);return GSON.fromJson(post("/v1/employees/admin/state",r,true,true).get("state"),LanEmployeeAdminService.State.class);}
    public static JsonObject updateEmployeeAdmin(String action,Integer userId,LanEmployeeAdminService.SaveRequest employee,List<Integer>locations,String token,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("action",action);if(userId!=null)r.addProperty("userId",userId);if(employee!=null)r.add("employee",GSON.toJsonTree(employee));if(locations!=null)r.add("locationIds",GSON.toJsonTree(locations));if(token!=null)r.addProperty("supabaseAccessToken",token);return post("/v1/employees/admin/update",r,true,true,Map.of("Idempotency-Key",key));}
    public static JsonObject quotationRead(String action,JsonObject body)throws Exception{JsonObject r=copy(body);r.addProperty("action",action);return post("/v1/quotations/read",r,true,true);}
    public static JsonObject quotationMutation(String action,JsonObject body,String key)throws Exception{JsonObject r=copy(body);r.addProperty("action",action);return post("/v1/quotations/update",r,true,true,Map.of("Idempotency-Key",key));}
    public static String loadQuotationDocument(String type,long id)throws Exception{JsonObject r=new JsonObject();r.addProperty("type",type);r.addProperty("documentId",id);return post("/v1/documents/quotation-invoice",r,true,true).get("text").getAsString();}
    public static LanCustomOrderCatalogAdminService.State loadCustomCatalogAdmin()throws Exception{return GSON.fromJson(post("/v1/custom-orders/admin/state",new JsonObject(),true,true).get("state"),LanCustomOrderCatalogAdminService.State.class);}
    public static long updateCustomCatalogAdmin(String action,JsonObject body,String key)throws Exception{JsonObject r=copy(body);r.addProperty("action",action);return post("/v1/custom-orders/admin/update",r,true,true,Map.of("Idempotency-Key",key)).get("recordId").getAsLong();}
    public static JsonObject customOrderWorkflowRead(String action,Long orderId,String search)throws Exception{JsonObject r=new JsonObject();r.addProperty("action",action);if(orderId!=null)r.addProperty("orderId",orderId);if(search!=null)r.addProperty("search",search);return post("/v1/custom-orders/workflow/read",r,true,true);}
    public static JsonObject customOrderWorkflowMutation(JsonObject request,String key)throws Exception{return post("/v1/custom-orders/workflow/update",request,true,true,Map.of("Idempotency-Key",key));}
    public static JsonObject companyCustomizationRead(String action,Integer locationId)throws Exception{
        JsonObject r=new JsonObject();r.addProperty("action",action);if(locationId!=null)r.addProperty("locationId",locationId);
        return post("/v1/configuration/read",r,true,true);
    }
    public static AppUpdateService.AppRelease loadLatestAppRelease(String platform)throws Exception{JsonObject r=new JsonObject();r.addProperty("platform",platform);JsonObject d=post("/v1/cloud/update/latest",r,true,true);return !d.has("release")||d.get("release").isJsonNull()?null:GSON.fromJson(d.get("release"),AppUpdateService.AppRelease.class);}
    public static String createUpdateDownloadUrl(String bucket,String path)throws Exception{JsonObject r=new JsonObject();r.addProperty("bucket",bucket);r.addProperty("path",path);return post("/v1/cloud/update/sign",r,true,true).get("url").getAsString();}
    public static String uploadCloudFile(String bucket,String path,String contentType,byte[]bytes)throws Exception{JsonObject r=new JsonObject();r.addProperty("bucket",bucket);r.addProperty("path",path);r.addProperty("contentType",contentType);r.addProperty("bytesBase64",java.util.Base64.getEncoder().encodeToString(bytes));return post("/v1/cloud/storage/upload",r,true,true).get("url").getAsString();}
    public static byte[] downloadEmployeeCloudFile(String url)throws Exception{JsonObject r=new JsonObject();r.addProperty("url",url);return java.util.Base64.getDecoder().decode(post("/v1/cloud/storage/download",r,true,true).get("bytesBase64").getAsString());}
    public static byte[] downloadImageAsset(String reference)throws Exception{JsonObject r=new JsonObject();r.addProperty("reference",reference);return java.util.Base64.getDecoder().decode(post("/v1/images/fetch",r,true,true).get("bytesBase64").getAsString());}
    public static ImageAssetState imageAssets()throws Exception{
        JsonObject data=post("/v1/images/list",new JsonObject(),true,true);
        ImageAssetRecord[] rows=GSON.fromJson(data.get("assets"),ImageAssetRecord[].class);
        ImageAssetCounts counts=GSON.fromJson(data.get("counts"),ImageAssetCounts.class);
        return new ImageAssetState(rows==null?List.of():List.of(rows),counts);
    }
    public static void reconcileImageAssets()throws Exception{post("/v1/images/reconcile",new JsonObject(),true,true);}
    public static void retainImageAsset(String assetId)throws Exception{JsonObject r=new JsonObject();r.addProperty("assetId",assetId);post("/v1/images/retain",r,true,true);}
    public static void purgeImageAsset(String assetId)throws Exception{JsonObject r=new JsonObject();r.addProperty("assetId",assetId);post("/v1/images/purge",r,true,true);}
    public static JsonObject companyCustomizationSave(String action,JsonElement settings,Integer locationId,String key)throws Exception{
        requireIdempotencyKey(key,"Configuration idempotency key is required.");JsonObject r=new JsonObject();r.addProperty("action",action);
        if(settings!=null)r.add("settings",settings);if(locationId!=null)r.addProperty("locationId",locationId);
        return post("/v1/configuration/update",r,true,true,Map.of("Idempotency-Key",key));
    }
    public static JsonObject balanceSheetRead(String action,JsonObject body)throws Exception{JsonObject r=copy(body);r.addProperty("action",action);return post("/v1/accounting/balance-sheet/read",r,true,true);}
    public static JsonObject balanceSheetMutation(String action,JsonObject body,String key)throws Exception{requireIdempotencyKey(key,"Balance-sheet idempotency key is required.");JsonObject r=copy(body);r.addProperty("action",action);return post("/v1/accounting/balance-sheet/update",r,true,true,Map.of("Idempotency-Key",key));}
    public static JsonObject queueEmail(String action,JsonObject body,String key)throws Exception{requireIdempotencyKey(key,"Email queue idempotency key is required.");JsonObject r=copy(body);r.addProperty("action",action);return post("/v1/email/queue",r,true,true,Map.of("Idempotency-Key",key));}
    private static JsonObject copy(JsonObject source){JsonObject out=new JsonObject();if(source!=null)for(var e:source.entrySet())out.add(e.getKey(),e.getValue());return out;}
    public static List<models.AppNotification> loadNotifications()throws Exception{JsonObject d=post("/v1/notifications/list",new JsonObject(),true,true);models.AppNotification[] rows=GSON.fromJson(d.getAsJsonArray("notifications"),models.AppNotification[].class);return rows==null?List.of():List.of(rows);}
    public static void updateNotification(String action,String notificationKey,int minutes,models.AppNotification notification,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("action",action);r.addProperty("notificationKey",notificationKey);r.addProperty("minutes",minutes);if(notification!=null)r.add("notification",GSON.toJsonTree(notification));post("/v1/notifications/update",r,true,true,Map.of("Idempotency-Key",key));}
    public static List<EmployeeScheduleService.StoreLocation> loadScheduleLocations()throws Exception{JsonObject d=post("/v1/schedule/locations",new JsonObject(),true,true);EmployeeScheduleService.StoreLocation[]a=GSON.fromJson(d.getAsJsonArray("locations"),EmployeeScheduleService.StoreLocation[].class);return a==null?List.of():List.of(a);}
    public static List<EmployeeScheduleService.Employee> loadScheduleEmployees(int locationId)throws Exception{JsonObject r=location(locationId),d=post("/v1/schedule/employees",r,true,true);EmployeeScheduleService.Employee[]a=GSON.fromJson(d.getAsJsonArray("employees"),EmployeeScheduleService.Employee[].class);return a==null?List.of():List.of(a);}
    public static List<EmployeeScheduleService.Shift> loadScheduleShifts(int locationId,boolean inactive)throws Exception{JsonObject r=location(locationId);r.addProperty("includeInactive",inactive);JsonObject d=post("/v1/schedule/shifts",r,true,true);EmployeeScheduleService.Shift[]a=GSON.fromJson(d.getAsJsonArray("shifts"),EmployeeScheduleService.Shift[].class);return a==null?List.of():List.of(a);}
    public static Map<java.time.LocalDate,List<EmployeeScheduleService.Assignment>> loadScheduleRange(int locationId,java.time.LocalDate start,java.time.LocalDate end)throws Exception{JsonObject r=range(locationId,start,end),d=post("/v1/schedule/range",r,true,true);ScheduleDay[]days=GSON.fromJson(d.getAsJsonArray("days"),ScheduleDay[].class);Map<java.time.LocalDate,List<EmployeeScheduleService.Assignment>>m=new java.util.LinkedHashMap<>();if(days!=null)for(ScheduleDay day:days)m.put(day.date(),day.assignments()==null?List.of():day.assignments());return m;}
    public static Map<java.time.LocalDate,EmployeeScheduleService.Holiday> loadScheduleHolidays(java.time.LocalDate start,java.time.LocalDate end,boolean timeClock)throws Exception{JsonObject r=new JsonObject();r.addProperty("start",start.toString());r.addProperty("end",end.toString());r.addProperty("timeClock",timeClock);JsonObject d=post("/v1/schedule/holidays",r,true,true);EmployeeScheduleService.Holiday[]a=GSON.fromJson(d.getAsJsonArray("holidays"),EmployeeScheduleService.Holiday[].class);Map<java.time.LocalDate,EmployeeScheduleService.Holiday>m=new java.util.LinkedHashMap<>();if(a!=null)for(var h:a)m.put(h.holidayDate(),h);return m;}
    public static EmployeeScheduleService.PeriodSnapshot loadScheduleSnapshot(
            int locationId, java.time.LocalDate start, java.time.LocalDate end) throws Exception {
        JsonObject data;
        try {
            data = post("/v1/schedule/snapshot", range(locationId, start, end), true, true);
        } catch (LanApiException ex) {
            if (!"INVALID_SERVER_RESPONSE".equals(ex.code()) && !"NOT_FOUND".equals(ex.code())) throw ex;
            return new EmployeeScheduleService.PeriodSnapshot(
                    loadScheduleRange(locationId, start, end),
                    loadScheduleHolidays(start, end, false));
        }
        ScheduleDay[] days = GSON.fromJson(data.getAsJsonArray("days"), ScheduleDay[].class);
        Map<java.time.LocalDate, List<EmployeeScheduleService.Assignment>> assignments = new java.util.LinkedHashMap<>();
        if (days != null) for (ScheduleDay day : days) assignments.put(day.date(),
                day.assignments() == null ? List.of() : day.assignments());
        EmployeeScheduleService.Holiday[] rows = GSON.fromJson(
                data.getAsJsonArray("holidays"), EmployeeScheduleService.Holiday[].class);
        Map<java.time.LocalDate, EmployeeScheduleService.Holiday> holidays = new java.util.LinkedHashMap<>();
        if (rows != null) for (var holiday : rows) holidays.put(holiday.holidayDate(), holiday);
        return new EmployeeScheduleService.PeriodSnapshot(assignments, holidays);
    }
    public static void updateSchedule(String action,ScheduleMutation mutation,String key)throws Exception{JsonObject r=GSON.toJsonTree(mutation).getAsJsonObject();r.addProperty("action",action);post("/v1/schedule/update",r,true,true,Map.of("Idempotency-Key",key));}
    public static void addScheduleEmployees(int locationId,java.time.LocalDate date,List<EmployeeScheduleService.Employee>employees,UUID shiftId,java.time.LocalTime lunch,String key)throws Exception{JsonObject r=range(locationId,date,date);r.add("employees",GSON.toJsonTree(employees));r.addProperty("shiftId",shiftId.toString());r.addProperty("lunchStart",lunch.toString());r.addProperty("action","ADD_EMPLOYEES");post("/v1/schedule/update",r,true,true,Map.of("Idempotency-Key",key));}
    public static EmployeeScheduleService.Shift saveScheduleShift(int locationId,UUID id,String name,java.time.LocalTime start,java.time.LocalTime end,boolean active,int displayOrder,boolean propagate,String key)throws Exception{JsonObject r=location(locationId);if(id!=null)r.addProperty("shiftId",id.toString());r.addProperty("name",name);r.addProperty("startTime",start.toString());r.addProperty("endTime",end.toString());r.addProperty("active",active);r.addProperty("displayOrder",displayOrder);r.addProperty("propagate",propagate);r.addProperty("action","SAVE_SHIFT");return GSON.fromJson(post("/v1/schedule/update",r,true,true,Map.of("Idempotency-Key",key)).get("shift"),EmployeeScheduleService.Shift.class);}
    public static int clearSchedule(int locationId,java.time.LocalDate start,java.time.LocalDate end,String key)throws Exception{JsonObject r=range(locationId,start,end);r.addProperty("action","CLEAR");return post("/v1/schedule/update",r,true,true,Map.of("Idempotency-Key",key)).get("removed").getAsInt();}
    public static EmployeeAutoScheduleService.AutoScheduleProposal generateAutoSchedule(int locationId,java.time.LocalDate start,java.time.LocalDate end,java.util.Set<Integer>employees)throws Exception{JsonObject r=range(locationId,start,end);if(employees!=null)r.add("employeeIds",GSON.toJsonTree(employees));return GSON.fromJson(post("/v1/schedule/auto-generate",r,true,true).get("proposal"),EmployeeAutoScheduleService.AutoScheduleProposal.class);}
    public static int applyAutoSchedule(EmployeeAutoScheduleService.AutoScheduleProposal proposal,String key)throws Exception{JsonObject r=new JsonObject();r.add("proposal",GSON.toJsonTree(proposal));return post("/v1/schedule/auto-apply",r,true,true,Map.of("Idempotency-Key",key)).get("applied").getAsInt();}
    public static RoleAdminState loadRoleAdminState()throws Exception{return GSON.fromJson(post("/v1/security/roles/state",new JsonObject(),true,true),RoleAdminState.class);}
    public static RolePermissionSelection loadRolePermissionSelection(int roleId)throws Exception{JsonObject r=new JsonObject();r.addProperty("roleId",roleId);return GSON.fromJson(post("/v1/security/roles/selected",r,true,true),RolePermissionSelection.class);}
    public static void saveRolePermissions(int roleId,java.util.Set<String>desktop,java.util.Set<String>mobile,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("roleId",roleId);r.add("permissionKeys",GSON.toJsonTree(desktop));r.add("mobilePermissionKeys",GSON.toJsonTree(mobile));post("/v1/security/roles/save",r,true,true,Map.of("Idempotency-Key",key));}
    public static RoleRecord addRole(String name,String key)throws Exception{JsonObject r=new JsonObject();r.addProperty("name",name);return GSON.fromJson(post("/v1/security/roles/add",r,true,true,Map.of("Idempotency-Key",key)),RoleRecord.class);}
    private static JsonObject location(int id){JsonObject r=new JsonObject();r.addProperty("locationId",id);return r;}
    private static JsonObject range(int id,java.time.LocalDate start,java.time.LocalDate end){JsonObject r=location(id);r.addProperty("start",start.toString());r.addProperty("end",end.toString());return r;}

    private static SavedProduct saveProduct(String path, ProductSaveRequest request,
                                             String idempotencyKey) throws Exception {
        requireIdempotencyKey(idempotencyKey, "Item idempotency key is required.");
        return GSON.fromJson(post(path, GSON.toJsonTree(request).getAsJsonObject(), true, true,
                Map.of("Idempotency-Key", idempotencyKey)), SavedProduct.class);
    }

    public static CheckoutResult checkout(CheckoutRequest checkout, String idempotencyKey) throws Exception {
        requireIdempotencyKey(idempotencyKey, "Checkout idempotency key is required.");
        JsonObject data = post("/v1/sales/checkout", GSON.toJsonTree(checkout).getAsJsonObject(),
                true, true, Map.of("Idempotency-Key", idempotencyKey));
        return GSON.fromJson(data, CheckoutResult.class);
    }

    public static List<SaleSearchResult> searchSalesForReturn(String query) throws Exception {
        return searchSalesForReturn(query,null);
    }
    public static List<SaleSearchResult> searchSalesForReturn(String query,Integer sourceLocationId)throws Exception{
        JsonObject request = new JsonObject();
        request.addProperty("query", query == null ? "" : query);
        if(sourceLocationId!=null)request.addProperty("sourceLocationId",sourceLocationId);
        JsonObject data = post("/v1/sales/search", request, true, true);
        SaleSearchResult[] sales = GSON.fromJson(data.getAsJsonArray("sales"), SaleSearchResult[].class);
        return sales == null ? List.of() : List.of(sales);
    }

    public static List<CrossStoreStoreOption> loadReturnStores()throws Exception{
        JsonObject data=post("/v1/sales/return-stores",new JsonObject(),true,true);
        CrossStoreStoreOption[] stores=GSON.fromJson(data.getAsJsonArray("stores"),CrossStoreStoreOption[].class);
        return stores==null?List.of():List.of(stores);
    }

    public static ReturnSaleDetails loadReturnSaleDetails(int saleId) throws Exception {
        return loadReturnSaleDetails(saleId,null);
    }
    public static ReturnSaleDetails loadReturnSaleDetails(int saleId,Integer sourceLocationId)throws Exception{
        JsonObject request = new JsonObject();
        request.addProperty("saleId", saleId);
        if(sourceLocationId!=null)request.addProperty("sourceLocationId",sourceLocationId);
        return GSON.fromJson(post("/v1/sales/return-details", request, true, true),
                ReturnSaleDetails.class);
    }

    public static RefundResult refund(RefundRequest refund, String idempotencyKey) throws Exception {
        requireIdempotencyKey(idempotencyKey, "Return idempotency key is required.");
        JsonObject data = post("/v1/sales/refund", GSON.toJsonTree(refund).getAsJsonObject(),
                true, true, Map.of("Idempotency-Key", idempotencyKey));
        return GSON.fromJson(data, RefundResult.class);
    }

    public static ReceiptData loadSaleReceipt(int saleId, BigDecimal cashCollected,
                                              BigDecimal changeDue) throws Exception {
        JsonObject request = new JsonObject(); request.addProperty("saleId", saleId);
        ReceiptPayload payload = GSON.fromJson(post("/v1/sales/receipt", request, true, true), ReceiptPayload.class);
        List<ReceiptItem> items = new ArrayList<>();
        if (payload.items() != null) {
            for (ReceiptItemPayload item : payload.items()) {
                items.add(new ReceiptItem(item.name(), item.sku(), item.quantity(), item.originalUnitPrice(),
                        item.finalUnitPrice(), item.discountPercent(), item.lineTotal()));
            }
        }
        return new ReceiptData(payload.saleId(), payload.receiptNumber(),
                new Timestamp(payload.saleTimeEpochMillis()), payload.storeName(), payload.cashierName(),
                payload.customerName(), payload.accountNumber(), payload.paymentMethod(), payload.paymentStatus(),
                payload.deviceId(), payload.subtotalAmount(), payload.discountPercent(), payload.discountAmount(),
                payload.vatAmount(), payload.vatRatePercent(), payload.vatMode(), payload.totalAmount(),
                payload.amountPaid(), payload.returnedAmount(), cashCollected, changeDue, items);
    }

    public static void logout() {
        try {
            post("/v1/sessions/logout", new JsonObject(), true, true);
        } catch (Exception ignored) {
            // Local logout still clears the employee session during a LAN outage.
        } finally {
            SecureCredentialStore.delete(API_SESSION_SECRET);
            cachedEmployeeSession = null;
            resetTransport(false, false);
        }
    }

    /** Clears local credentials immediately and invalidates the captured server session without delaying Swing. */
    public static void logoutWithoutWaiting() {
        HttpClient client = null;
        HttpRequest request = null;
        try {
            String device = cachedDeviceToken;
            String session = cachedEmployeeSession;
            URI endpoint = cachedBaseUri;
            client = cachedPinnedClient;
            if (device != null && session != null && endpoint != null && client != null) {
                request = HttpRequest.newBuilder(endpoint.resolve("/v1/sessions/logout"))
                        .timeout(TIMEOUT)
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .header("X-SmartStock-Device", device)
                        .header("Authorization", "Bearer " + session)
                        .POST(HttpRequest.BodyPublishers.ofString("{}", StandardCharsets.UTF_8))
                        .build();
            }
        } catch (Exception ignored) {
            // Local logout remains authoritative for this workstation.
        } finally {
            SecureCredentialStore.delete(API_SESSION_SECRET);
            cachedEmployeeSession = null;
            resetTransport(false, false);
        }
        HttpClient capturedClient = client;
        HttpRequest capturedRequest = request;
        if (capturedClient != null && capturedRequest != null) {
            UiTaskRunner.supplyAsync(() -> {
                long started = System.nanoTime();
                try {
                    capturedClient.send(capturedRequest, HttpResponse.BodyHandlers.discarding());
                    PerformanceDiagnostics.record("lan", "/v1/sessions/logout", started, true, 0);
                } catch (Exception ex) {
                    PerformanceDiagnostics.record("lan", "/v1/sessions/logout", started, false, -1);
                }
                return null;
            });
        }
    }

    public static boolean isPaired() {
        return deviceToken() != null && fingerprint() != null;
    }

    private static void requireIdempotencyKey(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    public static boolean shouldRenewDeviceCredential() {
        try {
            String value = SecureCredentialStore.read(API_TOKEN_EXPIRES_SECRET);
            return value != null && Instant.parse(value).minus(Duration.ofDays(30)).isBefore(Instant.now());
        } catch (Exception ex) {
            return false;
        }
    }

    /** Silently renews at day 60; the old token remains valid during the grace window. */
    public static boolean renewDeviceCredentialIfDue() throws Exception {
        if (!isPaired() || !shouldRenewDeviceCredential()) return false;
        JsonObject data;
        try {
            data = post("/v1/devices/rotate", new JsonObject(), true, false);
        } catch (LanApiException ex) {
            if ("ROTATION_NOT_DUE".equals(ex.code())) return false;
            throw ex;
        }
        String replacement = DeviceCredentialService.decryptLanEnvelope(
                data.get("credentialEnvelope").getAsString());
        SecureCredentialStore.write(DeviceCredentialService.LAN_API_TOKEN_SECRET, replacement);
        cachedDeviceToken = replacement;
        SecureCredentialStore.write(API_TOKEN_EXPIRES_SECRET, data.get("expiresAt").getAsString());
        resetTransport(false, false);
        return true;
    }

    private static LoginResult saveSession(JsonObject data) throws Exception {
        String session = data.get("sessionToken").getAsString();
        saveEmployeeSession(session,
                data.has("persistentLoginAllowed") && data.get("persistentLoginAllowed").getAsBoolean());
        return GSON.fromJson(data, LoginResult.class);
    }

    private static Probe probeUntrusted(URI endpoint) throws Exception {
        BlockingCallGuard.check("LAN health probe");
        long started = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(endpoint.resolve("/v1/health"))
                .timeout(TIMEOUT).GET().build();
        HttpResponse<String> response;
        try {
            response = (RemoteAdminPolicy.isRemoteAdminClient()
                    ? HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
                    : untrustedBootstrapClient()).send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            PerformanceDiagnostics.record("lan", "/v1/health", started, true, -1);
        } catch (Exception ex) {
            PerformanceDiagnostics.record("lan", "/v1/health", started, false, -1);
            throw ex;
        }
        JsonObject data = responseData(response);
        String advertised = data.get("certificateFingerprint").getAsString();
        if (!RemoteAdminPolicy.isRemoteAdminClient()) {
            String presented = peerFingerprint(response.sslSession().orElseThrow()
                    .getPeerCertificates()[0].getEncoded());
            if (!LanSecurity.constantTimeEquals(presented, advertised)) {
                throw new IllegalStateException("The server certificate fingerprint did not match its health response.");
            }
        }
        return new Probe(advertised, optionalString(data, "pairingProof"),
                optionalString(data, "previousPairingProof"));
    }

    private static JsonObject post(String path, JsonObject body, boolean deviceAuth, boolean employeeAuth) throws Exception {
        return post(path, body, deviceAuth, employeeAuth, Map.of());
    }

    private static JsonObject post(String path, JsonObject body, boolean deviceAuth, boolean employeeAuth,
                                   Map<String, String> extraHeaders) throws Exception {
        RemoteAdminPolicy.requireClientOperationAllowed(path);
        BlockingCallGuard.check("LAN " + path);
        long started = System.nanoTime();
        HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri().resolve(path))
                .timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8));
        extraHeaders.forEach(builder::header);
        if (deviceAuth) {
            String token = deviceToken();
            if (token == null) throw new LanApiException("DEVICE_CREDENTIAL_REQUIRED", "This register is not paired.", false);
            builder.header("X-SmartStock-Device", token);
        }
        if (employeeAuth) {
            String session = employeeSession();
            if (session == null) throw new LanApiException("SESSION_REQUIRED", "Employee login is required.", false);
            builder.header("Authorization", "Bearer " + session);
        }
        try {
            HttpResponse<String> response = pinnedClient().send(builder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            CONNECTION_LOSS_REPORTED.set(false);
            JsonObject data = responseData(response);
            invalidateCachesAfterMutation(path);
            PerformanceDiagnostics.record("lan", path, started, true,
                    PerformanceDiagnostics.resultCount(data));
            return data;
        } catch (Exception ex) {
            PerformanceDiagnostics.record("lan", path, started, false, -1);
            if (isConnectionFailure(ex)) {
                reportConnectionLoss();
                throw new LanApiException("SERVER_UNREACHABLE",
                        "Connection to the SmartStock server was lost. Return to the welcome screen and wait for it to reconnect.",
                        true);
            }
            throw ex;
        }
    }

    static boolean isConnectionFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof IOException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void reportConnectionLoss() {
        data.DatabaseMode mode = DatabaseConfig.load().mode();
        if ((mode != data.DatabaseMode.CLIENT && mode != data.DatabaseMode.REMOTE_ADMIN)
                || !CONNECTION_LOSS_REPORTED.compareAndSet(false, true)) {
            return;
        }
        try {
            connectionLossHandler.run();
        } catch (RuntimeException ignored) {
            // The original transport failure must still reach the calling workflow.
        }
    }

    static void invalidateCachesAfterMutation(String path) {
        if (path == null) return;
        boolean mutation = path.endsWith("/update") || path.endsWith("/save")
                || path.endsWith("/create") || path.endsWith("/receive")
                || path.endsWith("/assign") || path.endsWith("/unassign")
                || path.endsWith("/open") || path.endsWith("/close")
                || path.endsWith("/handover") || path.endsWith("/revise")
                || path.endsWith("/adjust") || path.endsWith("/checkout")
                || path.endsWith("/refund") || path.endsWith("/pay")
                || path.endsWith("/bonus") || path.endsWith("/punch")
                || path.endsWith("/confirm") || path.endsWith("/correct")
                || path.endsWith("/auto-apply") || path.endsWith("/resolve")
                || path.endsWith("/device-code") || path.endsWith("/timezone")
                || path.endsWith("/badge-printed") || path.endsWith("/change-pin")
                || path.endsWith("/process-email") || path.endsWith("/add");
        mutation = mutation || path.endsWith("/resume") || path.endsWith("/change-target")
                || path.endsWith("/save-settings") || path.endsWith("/complete")
                || path.endsWith("/delete") || path.endsWith("/deactivate")
                || path.endsWith("/return") || path.endsWith("/void")
                || path.endsWith("/approve") || path.endsWith("/deny")
                || path.endsWith("/deliver") || path.endsWith("/production")
                || path.endsWith("/clock-in") || path.endsWith("/clock-out")
                || path.endsWith("/start") || path.endsWith("/end")
                || path.endsWith("/deposit") || path.endsWith("/withdrawal")
                || path.contains("/mutation");
        if (mutation) SessionDataCache.clear();
    }

    private static JsonObject responseData(HttpResponse<String> response) throws LanApiException {
        try {
            JsonObject envelope = JsonParser.parseString(response.body()).getAsJsonObject();
            if (response.statusCode() >= 200 && response.statusCode() < 300 && envelope.get("success").getAsBoolean()) {
                return envelope.getAsJsonObject("data");
            }
            JsonObject error = envelope.getAsJsonObject("error");
            throw new LanApiException(error.get("code").getAsString(), error.get("message").getAsString(),
                    error.has("retryable") && error.get("retryable").getAsBoolean());
        } catch (LanApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LanApiException("INVALID_SERVER_RESPONSE", "The SmartStock server returned an invalid response.", true);
        }
    }

    private static HttpClient pinnedClient() throws Exception {
        HttpClient existing = cachedPinnedClient;
        if (existing != null) return existing;
        if (RemoteAdminPolicy.isRemoteAdminClient()) {
            synchronized (TRANSPORT_LOCK) {
                if (cachedPinnedClient == null) {
                    cachedPinnedClient = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
                }
                return cachedPinnedClient;
            }
        }
        String expected = fingerprint();
        if (expected == null) throw new LanApiException("SERVER_NOT_VERIFIED", "An administrator must pair this register once.", false);
        synchronized (TRANSPORT_LOCK) {
            if (cachedPinnedClient == null) {
                cachedPinnedClient = clientForTrustManager(new X509TrustManager() {
                    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) throws java.security.cert.CertificateException {
                        if (chain == null || chain.length == 0) throw new java.security.cert.CertificateException("Missing server certificate.");
                        try {
                            String actual = peerFingerprint(chain[0].getEncoded());
                            if (!LanSecurity.constantTimeEquals(expected, actual)) {
                                throw new java.security.cert.CertificateException("SmartStock server certificate changed.");
                            }
                        } catch (java.security.cert.CertificateException ex) { throw ex; }
                        catch (Exception ex) { throw new java.security.cert.CertificateException(ex); }
                    }
                    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                });
            }
            return cachedPinnedClient;
        }
    }

    private static HttpClient untrustedBootstrapClient() throws Exception {
        HttpClient existing = cachedBootstrapClient;
        if (existing != null) return existing;
        synchronized (TRANSPORT_LOCK) {
            if (cachedBootstrapClient == null) {
                cachedBootstrapClient = clientForTrustManager(new X509TrustManager() {
                    @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                    @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                    @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                });
            }
            return cachedBootstrapClient;
        }
    }

    static void resetTransportForTests() {
        resetTransport(true, true);
        CONNECTION_LOSS_REPORTED.set(false);
        connectionLossHandler = () -> { };
    }

    static HttpClient bootstrapClientForTests() throws Exception {
        return untrustedBootstrapClient();
    }

    private static void resetTransport(boolean endpoint, boolean tokens) {
        synchronized (TRANSPORT_LOCK) {
            if (endpoint) cachedBaseUri = null;
            cachedPinnedClient = null;
            cachedBootstrapClient = null;
            cachedFingerprint = null;
            if (tokens) {
                cachedDeviceToken = null;
                cachedEmployeeSession = null;
            }
        }
        SessionDataCache.clear();
    }

    private static String fingerprint() {
        String value = cachedFingerprint;
        if (value == null) {
            value = SecureCredentialStore.read(DeviceCredentialService.LAN_API_FINGERPRINT_SECRET);
            cachedFingerprint = value;
        }
        return value;
    }

    private static String deviceToken() {
        String value = cachedDeviceToken;
        if (value == null) {
            value = SecureCredentialStore.read(DeviceCredentialService.LAN_API_TOKEN_SECRET);
            cachedDeviceToken = value;
        }
        return value;
    }

    private static String employeeSession() {
        String value = cachedEmployeeSession;
        if (value == null) {
            value = SecureCredentialStore.read(API_SESSION_SECRET);
            cachedEmployeeSession = value;
        }
        return value;
    }

    private static void saveEmployeeSession(String session, boolean persistent) throws Exception {
        if (persistent) SecureCredentialStore.write(API_SESSION_SECRET, session);
        else SecureCredentialStore.delete(API_SESSION_SECRET);
        cachedEmployeeSession = session;
        resetTransport(false, false);
    }

    private static HttpClient clientForTrustManager(X509TrustManager trustManager) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{trustManager}, new SecureRandom());
        SSLParameters parameters = new SSLParameters();
        // Certificate pinning owns server identity; generated certificates may not
        // contain every DHCP address by design.
        parameters.setEndpointIdentificationAlgorithm("");
        return HttpClient.newBuilder().sslContext(context).sslParameters(parameters)
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    private static String peerFingerprint(byte[] certificate) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(certificate));
    }

    private static String normalizePhrase(String phrase) {
        return phrase == null ? "" : phrase.trim().toUpperCase(Locale.ROOT);
    }

    private static int parsePort(String value, int fallback) {
        try {
            int port = Integer.parseInt(value);
            return port > 0 && port <= 65535 ? port : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static boolean matchesPairingProof(String phrase, String fingerprint, String currentProof, String previousProof) {
        String expected = LanSecurity.hmacSha256(normalizePhrase(phrase), fingerprint == null ? "" : fingerprint);
        return LanSecurity.constantTimeEquals(expected, currentProof)
                || LanSecurity.constantTimeEquals(expected, previousProof);
    }

    private static String optionalString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private record Probe(String certificateFingerprint, String pairingProof, String previousPairingProof) { }
    public record PairingResult(String status, boolean employeeActionRequired) { }
    public record DiscoveredServer(String service, String host, int port,
                                   String environment, String storeName, String storeCode,
                                   String computerName, String serverId,
                                   String certificateFingerprint, String pairingProof,
                                   String previousPairingProof) {
        public DiscoveredServer(String service, String host, int port,
                                String certificateFingerprint, String pairingProof,
                                String previousPairingProof) {
            this(service, host, port, null, null, null, null, null,
                    certificateFingerprint, pairingProof, previousPairingProof);
        }

        @Override
        public String toString() {
            String store = storeName == null || storeName.isBlank() ? "Unassigned Store" : storeName;
            String code = storeCode == null || storeCode.isBlank() ? "no code" : storeCode;
            String computer = computerName == null || computerName.isBlank() ? host : computerName;
            String id = serverId == null || serverId.isBlank() ? "unknown" : serverId;
            String profile = "production".equalsIgnoreCase(environment)
                    ? "Production" : "Developer/Test";
            return store + " (" + code + ") — " + computer + " — "
                    + host + ":" + port + " — " + profile + " — ID " + id;
        }
    }
    public record ServiceHealth(boolean online, String certificateFingerprint) { }
    public record LoginResult(String sessionToken, String expiresAt, User user, String[] permissions,
                              String deviceId, String supabaseAccessToken, String supabaseRefreshToken,
                              boolean persistentLoginAllowed, boolean autoLogoutEnabled,
                              int autoLogoutMinutes) { }
    public record SessionPolicy(boolean persistentLoginAllowed, boolean autoLogoutEnabled,
                                int autoLogoutMinutes) { }
    public record User(int userId, String username, String fullName, String email, String role,
                       int locationId, String locationName, String locationTimezone) { }
    public record ApprovalResult(String approvalToken, String expiresAt, int approverUserId, String approverName) { }
    public record BadgeStatus(boolean pinConfigured, boolean pinRequired) { }
    public record CatalogProduct(int productId, String name, String size, String description, String sku,
                                 BigDecimal price, String productType, Integer categoryId,
                                 int quantityOnHand, String searchableText) { }
    public record CustomerAccount(int customerId, String accountNumber, String customerName,
                                  BigDecimal creditLimit, BigDecimal currentBalance,
                                  BigDecimal availableCredit, boolean business,
                                  String customerTypeName, String phone) { }
    public record CashDrawerStatus(Long cashDrawerId, String drawerName, Long sessionId,
                                   boolean assigned, boolean activeSession) { }
    public record SalesSettings(boolean vatEnabled, boolean departmentVat, BigDecimal fixedVatRate,
                                BigDecimal discountLimit, List<DepartmentVatRate> departmentRates) { }
    public record DepartmentVatRate(int categoryId, BigDecimal ratePercent) { }
    public record HeldCartCreateRequest(String holdName, String paymentMethod, Integer customerId,
                                        BigDecimal saleDiscountPercent,
                                        String saleDiscountApprovalToken,
                                        String saleDiscountOverrideReason,
                                        List<HeldCartCreateLine> lines) { }
    public record HeldCartCreateLine(int productId, int quantity, BigDecimal unitPrice,
                                     BigDecimal discountPercent,
                                     String priceApprovalToken, String priceOverrideReason,
                                     String discountApprovalToken,
                                     String discountOverrideReason) { }
    public record HeldCartCreated(int heldCartId, BigDecimal total, int itemCount) { }
    public record HeldCartSummary(int heldCartId, long createdAtEpochMillis, String holdName,
                                  String userName, String customerName, int itemCount, BigDecimal total) { }
    public record HeldCartPayload(int heldCartId, Integer customerId, String paymentMethod,
                                  BigDecimal saleDiscountPercent, List<HeldCartItem> items) { }
    public record HeldCartItem(int productId, String productName, String description, String sku,
                               BigDecimal unitPrice, BigDecimal catalogPrice,
                               int quantity, BigDecimal discountPercent,
                               String productType, Integer categoryId) { }
    public record SalesHistoryRow(String transactionType, int saleId, Long returnId, String receiptNumber,
                                  long createdAtEpochMillis, String cashierName, String storeName, int itemCount,
                                  String paymentMethod, String paymentStatus, BigDecimal amountPaid,
                                  BigDecimal returnedAmount, BigDecimal discountAmount,
                                  BigDecimal totalAmount, BigDecimal netAmount,Integer sourceLocationId,
                                  long cacheRefreshedAtEpochMillis,String cacheStatus) { }
    public record SalesHistoryResult(List<SalesHistoryRow>transactions,List<CrossStoreStoreOption>stores,
                                     int currentLocationId) { }
    public record SaleHistoryDetails(int saleId, BigDecimal subtotalAmount, BigDecimal discountPercent,
                                     BigDecimal discountAmount, BigDecimal totalAmount,
                                     List<SaleHistoryItem> items, List<SaleHistoryReturn> returns,
                                     List<SaleHistoryReturnItem> returnItems,
                                     List<SaleHistoryAudit> overrideAudit,Integer sourceLocationId,String sourceStoreName,
                                     long cacheRefreshedAtEpochMillis,String cacheStatus) { }
    public record SaleHistoryItem(int productId, String productName, int quantity, int returnedQuantity,
                                  BigDecimal originalUnitPrice, BigDecimal discountPercent,
                                  BigDecimal discountAmount, BigDecimal unitPrice, BigDecimal lineTotal) { }
    public record SaleHistoryReturn(long returnId, long createdAtEpochMillis, String userName,
                                    String refundMethod, BigDecimal refundAmount, String reason) { }
    public record SaleHistoryReturnItem(long returnId, int productId, String productName, int quantity,
                                        BigDecimal unitPrice, BigDecimal lineTotal) { }
    public record SaleHistoryAudit(long createdAtEpochMillis, String actionType, String actionScope,
                                   String fieldName, String oldValue, String newValue, BigDecimal amount,
                                   Integer quantity, String reason, String note, String userName,
                                   String deviceName) { }
    public record InventoryLookups(List<NamedId> departments,List<NamedId> vendors,List<String> itemTypes,
                                   List<String> brands,List<String> shelves) { }
    public record NamedId(int id,String name) { }
    public record LookupItem(String itemType,int itemId,String name,String description,String code,int quantityOnHand) { }
    public record InventoryRequest(String search,String stockFilter,String department,String itemType,String brand,
                                   String shelf,String storageShelf) { }
    public record InventoryResult(List<InventoryProduct> products,int totalProducts,int totalUnits,
                                  boolean canViewVendor,boolean canViewCostPrice,boolean canViewCreatedBy) { }
    public record InventoryProduct(int productId,String sku,String name,String size,String description,String productType,
                                   String department,String itemType,String brand,String shelf,String storageShelf,
                                   String vendor,BigDecimal costPrice,BigDecimal price,int quantityOnHand,
                                   int reorderLevel,String createdBy) { }
    public record InventoryDetails(Map<String,String> fields,List<InventoryActivity> activities) { }
    public record CrossStoreInventoryResult(List<CrossStoreStoreOption> stores,List<CrossStoreInventoryItem> items) { }
    public record CrossStoreStoreOption(int locationId,String name,String status,long refreshedAtEpochMillis) { }
    public record CrossStoreInventoryItem(int locationId,String storeName,int productId,String sku,String barcode,
                                          String productName,String size,String description,int quantityOnHand,
                                          int reorderLevel,long sourceUpdatedAtEpochMillis,
                                          long cacheRefreshedAtEpochMillis,String cacheStatus,String cacheError) { }
    public record InventoryActivity(long createdAtEpochMillis,String activityType,int quantity,String amount,
                                    String reference,String userName,String note) { }
    public record ReceivingHistoryRow(long movementId,String receiveId,long createdAtEpochMillis,String productName,
                                      String sku,String storeName,int changeQuantity,String receivedBy,String note) { }
    public record ReceiveInventoryRequest(String overrideApprovalToken,String overrideReason,List<ReceiveInventoryLine>lines) { }
    public record ReceiveInventoryLine(String itemType,int itemId,int countedStock,int quantity) { }
    public record ReceiveInventoryResult(String receiveId,int lineCount) { }
    public record TransferLocation(int locationId,String name) { }
    public record TransferProduct(int productId,String sku,String name,int availableQuantity) { }
    public record IncomingTransfer(long transferId,String fromStore,long createdAtEpochMillis,String sentBy,String note,
                                   int itemCount,int unitCount) { }
    public record OutgoingTransfer(long transferId,String toStore,long createdAtEpochMillis,String sentBy,String note,
                                   int itemCount,int unitCount) { }
    public record TransferDetailItem(int productId,String sku,String name,int quantity) { }
    public record CreateTransferRequest(int destinationLocationId,String note,List<TransferLine>lines) { }
    public record TransferLine(int productId,int quantity) { }
    public record CreateTransferResult(long transferId,int lineCount) { }
    public record ReceiveTransferResult(long transferId,String receiveId,int lineCount) { }
    public record DepartmentListResult(List<DepartmentRecord> departments,boolean vatEditable) { }
    public record DepartmentRecord(int categoryId,String name,BigDecimal vatRatePercent,String description) { }
    public record DepartmentSaveRequest(Integer categoryId,String name,BigDecimal vatRatePercent,String description) { }
    public record SavedDepartment(int categoryId,String name) { }
    public record VendorRecord(int vendorId,String name,String contactName,String phone,String email,
                               String address,String notes,boolean active) { }
    public record VendorSaveRequest(Integer vendorId,String name,String contactName,String phone,String email,
                                    String address,String notes,boolean active) { }
    public record SavedVendor(int vendorId,String name) { }
    public record CustomerTypeRecord(int customerTypeId,String name,String description,boolean active) { }
    public record CustomerTypeSaveRequest(Integer customerTypeId,String name,String description,boolean active) { }
    public record SavedCustomerType(int customerTypeId,String name) { }
    public record EditableProduct(int productId,String name,String size,String sku,String barcode,String description,
                                  BigDecimal costPrice,BigDecimal price,String productType,int quantity,int reorderLevel,
                                  Integer categoryId,String categoryName,Integer vendorId,String vendorName,String imageUrl,
                                  String itemTypeName,String brandName,String shelfName,String storageShelfName,
                                  List<String> additionalBarcodes) { }
    public record PriceTagCatalogItem(String itemType,String name,String size,String description,String code,
                                      BigDecimal price,long itemId) { }
    public record PriceTagSettings(String encodedTemplates,boolean showCompany,boolean showSku,boolean showBarcode,
                                   double widthInches,double heightInches) { }
    public record ProductSaveRequest(Integer productId,String name,String size,String sku,String barcode,String description,
                                     BigDecimal costPrice,BigDecimal price,String productType,Integer categoryId,Integer vendorId,
                                     String imageUrl,String itemTypeName,String brandName,String shelfName,String storageShelfName,
                                     List<String> additionalBarcodes,int quantity,int reorderLevel,Integer expectedQuantity,
                                     boolean adjustQuantity) { }
    public record SavedProduct(int productId,String sku,int quantity) { }
    public record SyncStatusSnapshot(boolean cloudReachable,String message,long lastSuccessEpochMillis,
                                     int lastPushed,int pendingCount,int failedCount,int conflictCount,
                                     String lastError,boolean lockRunning,String lockOwner,
                                     long lockAcquiredEpochMillis,String serviceStatus,String serviceMessage,
                                     long serviceLastSeenEpochMillis,boolean serverWorkerStarted,
                                     int imagePendingUploads,int imageMissingLocal,int imageMissingCloud,
                                     int imageUnused,int imageFailedPurges,
                                     List<SyncConflict> conflicts,List<SyncAudit> audits) { }
    public record SyncConflict(long conflictId,String eventType,String conflictType,String status,
                               long createdAtEpochMillis) { }
    public record SyncAudit(long createdAtEpochMillis,String actionType,String tableName,
                            String localIdBefore,String localIdAfter,String cloudId,String matchKey,
                            String status,String details) { }
    public record WorkstationSettings(String deviceCode,String storeCode,int nextSequence,
                                      String nextReceiptPreview,int nextReceiveSequence,
                                      String nextReceivePreview,String timezone) { }
    public record CustomerAccountRecord(int customerId,String accountNumber,String name,String phone,String email,
                                        BigDecimal creditLimit,BigDecimal currentBalance,BigDecimal availableCredit,
                                        boolean business,boolean active,String accountNotes,Integer customerTypeId,
                                        String customerTypeName) { }
    public record CustomerAccountSaveRequest(Integer customerId,String accountNumber,String name,Integer customerTypeId,
                                             String phone,String email,BigDecimal creditLimit,boolean business,
                                             boolean active,String accountNotes) { }
    public record SavedCustomerAccount(int customerId,String accountNumber) { }
    public record CustomerAccountAdjustmentRequest(int customerId,BigDecimal amount,String action,String paymentMethod,String paymentReference) { }
    public record CustomerAccountAdjustmentResult(long transactionId,String paymentId,BigDecimal balanceAfter) { }
    public record CustomerTransactionResult(List<CustomerTransactionRecord> transactions,int count,
                                            BigDecimal totalCharges,BigDecimal totalPayments) { }
    public record CustomerTransactionRecord(long transactionId,String paymentId,long createdAtEpochMillis,String userName,
                                            String deviceName,String cashDrawerName,String transactionType,String paymentMethod,
                                            String paymentReference,Integer saleId,Long customOrderId,BigDecimal amount,
                                            String note,String paymentStatus,BigDecimal chargeTotal) { }
    public record CustomerPaymentResult(List<CustomerPaymentRecord> payments,int paymentCount,int rowCount,
                                        BigDecimal totalPayments,BigDecimal totalApplied) { }
    public record CustomerPaymentRecord(String paymentId,long transactionId,long paymentDateEpochMillis,String userName,
                                        String paymentMethod,String paymentReference,String deviceName,String cashDrawerName,
                                        BigDecimal paymentAmount,String target,BigDecimal appliedAmount,BigDecimal chargeTotal,
                                        BigDecimal chargePaid,String paymentStatus,long chargeDateEpochMillis) { }
    public record AccountPaymentReceiptPayload(long transactionId,Integer locationId,String paymentId,long paymentTimeEpochMillis,
                                               String storeName,String userName,String customerName,String accountNumber,
                                               String customerEmail,String paymentMethod,String paymentReference,String deviceName,
                                               String cashDrawerName,BigDecimal paymentAmount,BigDecimal accountBalanceAfter,
                                               List<AccountPaymentAllocation> allocations) { }
    public record AccountPaymentAllocation(String targetLabel,BigDecimal appliedAmount,BigDecimal chargeTotal,
                                           BigDecimal chargePaid,String paymentStatus,long chargeDateEpochMillis) { }
    public record ChangeBasketState(String storeName,BigDecimal targetAmount) { }
    public record CashDrawerRegisterState(Long drawerId,String drawerName,CashDrawerSession session,BigDecimal expectedCash,Map<Integer,Integer>floatMix) { }
    public record CashDrawerCloseResult(CashDrawerSession session,List<String>handlers) { }
    public record CashDrawerAdminState(List<CashDrawerService.StoreOption>stores,List<CashDrawerService.DeviceOption>devices,
                                       List<CashDrawer>drawers,List<CashDrawerAssignment>assignments,BigDecimal changeBasketTarget) { }
    public record CashDrawerSaveRequest(Long drawerId,int locationId,String drawerName,String description,BigDecimal startingCashAmount,
                                        Map<Integer,Integer>floatMix,boolean active) { }
    public record DeviceAdminUpdate(String action,String deviceId,boolean approved,
                                    boolean persistentLoginAllowed,boolean autoLogoutEnabled,
                                    int autoLogoutMinutes,boolean allowSales,boolean allowOrders,
                                    String notes,String deviceName,String receiptCode) { }
    public record ServerAdminState(List<ServerRecord> servers,List<ServerEvent> events,String currentServerInstanceId,String localRole) { }
    public record ServerRecord(String serverInstanceId,int locationId,String installationId,String displayName,
                               String hostname,String appVersion,String certificateFingerprint,String endpointHost,
                               int endpointPort,String role,long generation,String health,String lastHeartbeatAt,
                               String lastSyncAt,String lastMaterializationAt,Long materializedRowCount,
                               String recoveryValidatedAt,String recoveryMaterializationAt,
                               String recoveryNetworkCheckedAt,
                               String statusMessage,String replacedByServerInstanceId,String createdAt,String retiredAt) { }
    public record ServerAdminUpdate(String action,String serverInstanceId,String targetServerInstanceId,
                                    String displayName,String idempotencyKey,boolean warningAcknowledged) { }
    public record ServerEvent(String eventType,String serverInstanceId,String handoffId,
                              String actorName,String details,String createdAt) { }
    public record DeviceSecurityStatus(boolean healthy,boolean tls,String credentialStore,int pendingCredentials,int issuedCredentials,
                                       int claimedCredentials,int blockedDevices,int broadAuthenticatedPolicies,int exposedTablesWithoutRls,
                                       int publicSecurityDefiners,long latestAuditEpochMillis,long latestBackupEpochMillis,String pairingPhrase,
                                       String lanCertificateFingerprint,List<String>warnings) { }
    public record LocationRecord(Integer locationId,String name,String storeCode,String address,String addressLine1,String addressLine2,String addressLine3,
                                 String phoneLine1,String phoneLine2,String emailLine1,String emailLine2,String senderEmail,String senderName,String bccEmail,
                                 String balanceSheetEmail,boolean emailReceipts,boolean emailOrders,boolean emailQuotes,boolean emailInvoices,boolean emailDelivery,String timezone) { }
    public record EmailProcessingResult(int processed,long sent,long failed,long skipped) { }
    public record TimeClockPunchState(boolean requiresOverride,boolean requesterCanOverride) { }
    public record ScheduleMutation(Integer locationId,java.time.LocalDate date,Integer userId,UUID shiftId,java.time.LocalTime lunchStart,java.time.LocalDate endDate,String name,List<UUID>shiftIds,Boolean active) { }
    private record ScheduleDay(java.time.LocalDate date,List<EmployeeScheduleService.Assignment>assignments) { }
    public record PermissionRecord(String key,String label,String group,String subgroup,String description) { }
    public record RoleRecord(int roleId,String name) { }
    public record RoleAdminState(List<RoleRecord>roles,List<PermissionRecord>permissions,List<PermissionRecord>mobilePermissions,boolean mobileAvailable) { }
    public record RolePermissionSelection(List<String>permissionKeys,List<String>mobilePermissionKeys) { }
    public record MaintenancePart(Integer partId,String name,String partNumber,String category,BigDecimal quantity,BigDecimal reorderPoint,
                                  BigDecimal reorderQuantity,BigDecimal unitCost,String vendor,String binLocation,boolean active,String notes) { }
    public record OrderReport(List<OrderReportRow>rows,int payments,BigDecimal collected,BigDecimal total,BigDecimal balance,BigDecimal cash,BigDecimal card,BigDecimal cheque,BigDecimal mmg,BigDecimal account,BigDecimal returns) { }
    public record OrderReportRow(long paymentId,String orderNumber,java.time.LocalDateTime time,String customer,String employee,String device,String drawer,String method,BigDecimal amount,BigDecimal total,BigDecimal balance,String status) { }
    public record InvoiceReport(List<InvoiceReportRow>rows,int count,int open,int delivered,BigDecimal total,BigDecimal paid,BigDecimal balance,BigDecimal cash,BigDecimal card,BigDecimal cheque,BigDecimal mmg) { }
    public record InvoiceReportRow(long invoiceId,String invoiceNumber,java.time.LocalDate invoiceDate,String customer,String status,String paymentStatus,BigDecimal total,BigDecimal paid,BigDecimal balance,String creator,String device,String drawer) { }
    public record CheckoutRequest(String paymentMethod, String paymentReference, Integer customerId,
                                  BigDecimal saleDiscountPercent, BigDecimal cashCollected,
                                  String saleDiscountApprovalToken, String saleDiscountOverrideReason,
                                  List<CheckoutLine> lines) { }
    public record CheckoutLine(int productId, int quantity, BigDecimal unitPrice, BigDecimal discountPercent,
                               String priceApprovalToken, String priceOverrideReason,
                               String discountApprovalToken, String discountOverrideReason) { }
    public record CheckoutResult(int saleId, String receiptNumber, BigDecimal total,
                                 BigDecimal cashCollected, BigDecimal changeDue,
                                 String cashDrawerName) { }
    public record SaleSearchResult(int saleId, String receiptNumber, long createdAtEpochMillis,
                                   BigDecimal totalAmount, String cashierName, String deviceId,
                                   Integer sourceLocationId,String storeName,long cacheRefreshedAtEpochMillis,String cacheStatus) { }
    public record ReturnSaleDetails(int saleId, String receiptNumber, Integer customerId,
                                    String paymentMethod, String paymentStatus,
                                    BigDecimal totalAmount, BigDecimal returnedAmount,
                                    BigDecimal returnApprovalLimit, boolean requesterCanOverride,
                                    List<ReturnSaleLine> items,Integer sourceLocationId) { }
    public record ReturnSaleLine(int saleItemId, int productId, String sku, String productName,
                                 String productType, int soldQuantity, int returnedQuantity,
                                 int availableQuantity, BigDecimal unitPrice) { }
    public record RefundRequest(UUID requestId,Integer sourceLocationId,int saleId,String refundMethod,String reason,
                                String approvalToken,String approvalReason,List<RefundLine>lines) {
        public RefundRequest(int saleId,String refundMethod,String reason,String approvalToken,String approvalReason,List<RefundLine>lines){
            this(null,null,saleId,refundMethod,reason,approvalToken,approvalReason,lines);
        }
    }
    public record RefundLine(int saleItemId,int quantity,String disposition,Integer destinationLocationId,String dispositionReason) {
        public RefundLine(int saleItemId,int quantity){this(saleItemId,quantity,null,null,null);}
    }
    public record RefundResult(long returnId, int saleId, BigDecimal refundAmount,
                               String refundMethod, boolean approvalRequired,
                               String approvedByName,String requestId,Integer sourceLocationId,String status) { }
    private record ReceiptPayload(int saleId,String receiptNumber,long saleTimeEpochMillis,String storeName,
                                  String cashierName,String customerName,String accountNumber,String paymentMethod,
                                  String paymentStatus,String deviceId,BigDecimal subtotalAmount,BigDecimal discountPercent,
                                  BigDecimal discountAmount,BigDecimal vatAmount,BigDecimal vatRatePercent,String vatMode,
                                  BigDecimal totalAmount,BigDecimal amountPaid,BigDecimal returnedAmount,
                                  List<ReceiptItemPayload> items) { }
    private record ReceiptItemPayload(String name,String sku,int quantity,BigDecimal originalUnitPrice,
                                      BigDecimal finalUnitPrice,BigDecimal discountPercent,BigDecimal lineTotal) { }

    public record RemoteStore(int locationId, String name, String timezone, String status,
                              long lastSyncEpochMillis) {
        @Override public String toString() { return name; }
    }
    public record RemoteCommand(String commandId, String operation, String status, String details,
                                long createdAtEpochMillis, long appliedAtEpochMillis) { }

    public static final class LanApiException extends Exception {
        private final String code;
        private final boolean retryable;
        public LanApiException(String code, String message, boolean retryable) {
            super(message); this.code = code; this.retryable = retryable;
        }
        public String code() { return code; }
        public boolean retryable() { return retryable; }
    }
    public record ImageAssetState(List<ImageAssetRecord>assets,ImageAssetCounts counts) { }
    public record ImageAssetRecord(String assetId,String category,String filename,long byteSize,String lifecycleStatus,
                                   String localStatus,String cloudStatus,long createdAtEpochMillis,long updatedAtEpochMillis,
                                   long unusedSinceEpochMillis,String lastError,int referenceCount) { }
    public record ImageAssetCounts(int pendingUploads,int missingLocal,int missingCloud,int unused,int failedPurges,
                                   boolean cloudCredentialConfigured) { }
}
