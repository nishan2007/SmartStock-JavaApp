package architecture;

import data.DB;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LanApiBoundaryTest {
    private static final Path ROOT = Path.of(System.getProperty("user.dir"));
    private static final Path SRC = ROOT.resolve("src");
    private static final String JDBC_PATTERN =
            "(?s).*(import data\\.DB;|import java\\.sql\\.(Connection|PreparedStatement|ResultSet|Statement);"
                    + "|DB\\.getConnection\\(|DriverManager\\.getConnection\\().*";
    private static final Set<String> SERVER_MANAGERS = Set.of(
            "ServerTimeClockManager.java", "ServerCompanyCustomizationRepository.java", "ServerReceiptNumberManager.java");

    @Test
    void registerReachableSourcesHaveNoJdbc() throws Exception {
        List<Path> violations = new ArrayList<>();
        scan(SRC.resolve("ui"), violations, false);
        scan(SRC.resolve("Receipt"), violations, false);
        scan(SRC.resolve("managers"), violations, true);
        assertTrue(violations.isEmpty(), () -> "Register-callable JDBC remains: " + violations);
    }

    @Test
    void clientFacadesDoNotImportServerRepositories() throws Exception {
        String combined = readTree(SRC.resolve("ui")) + readTree(SRC.resolve("Receipt"));
        assertFalse(combined.matches("(?s).*(ServerTimeClockManager|ServerNotificationService|"
                + "ServerEmployeeScheduleService|ServerEmployeeAutoScheduleService|ServerCustomOrderDataService|"
                + "ServerQuotationInvoiceService|ServerQuotationInvoiceViewService|ServerBalanceSheetService|"
                + "ServerEmailOutboxService|ServerCompanyCustomizationRepository|ServerReceiptNumberManager).*"));
    }

    @Test
    void cleanCutHasNoCloudDirectOrClientDatabaseCredentialMode() throws Exception {
        String mode = Files.readString(SRC.resolve("data/DatabaseMode.java"));
        String setup = Files.readString(SRC.resolve("ui/screens/DatabaseSetup.java"));
        assertFalse(mode.contains("CLOUD_DIRECT"));
        assertFalse(setup.matches("(?s).*SMARTSTOCK_CLIENT_DB_(USER|PASSWORD).*"));
        assertFalse(setup.contains("clientJdbcUrlOrDefault"));
    }

    @Test
    void registerStoreAssignmentComesFromAuthenticatedServer() throws Exception {
        String client = Files.readString(SRC.resolve("services/LanApiClient.java"));
        String server = Files.readString(SRC.resolve("services/LanApiServer.java"));
        assertTrue(server.contains("data.put(\"locationId\", DatabaseConfig.load().locationId())"));
        assertTrue(server.contains("\"locationId\", destination"));
        assertTrue(client.contains("saveServerAssignedLocation(probe.locationId())"));
        assertTrue(client.contains("DatabaseConfig.load().withLocationId(locationId).save()"));
    }

    @Test
    void registerEnrollmentAllocatesStoreUniqueReceiptDeviceCode() throws Exception {
        String server = Files.readString(SRC.resolve("services/LanApiServer.java"));
        String receipts = Files.readString(SRC.resolve("managers/ServerReceiptNumberManager.java"));
        assertTrue(server.contains("assignCodeForEnrollment(connection, destination, deviceId)"));
        assertTrue(server.contains("connection.setAutoCommit(false)"));
        assertTrue(server.contains("connection.commit()"));
        assertTrue(server.contains("connection.rollback()"));
        assertTrue(receipts.contains("pg_advisory_xact_lock"));
        assertTrue(receipts.contains("last_store_id=? AND device_id<>?"));
        assertTrue(receipts.contains("MAX(receipt_device_code::integer),0)+1"));
        assertTrue(receipts.contains("assignCodeForEnrollment(conn, locationId, deviceId)"),
                "Existing duplicate device codes must self-repair before another receipt is allocated");
    }

    @Test
    void registerReportsHostnameAsDefaultNameAndRefreshesHardwareDetails() throws Exception {
        String deviceUtils = Files.readString(SRC.resolve("utils/DeviceUtils.java"));
        String client = Files.readString(SRC.resolve("services/LanApiClient.java"));
        String server = Files.readString(SRC.resolve("services/LanApiServer.java"));
        String main = Files.readString(SRC.resolve("app/Main.java"));
        assertTrue(deviceUtils.contains("getDeviceName(hostname)"));
        assertFalse(deviceUtils.contains("preferredDeviceName(localUsername, hostname)"));
        assertTrue(deviceUtils.contains("macSystemName(\"ComputerName\")"));
        assertTrue(deviceUtils.contains("macSystemName(\"LocalHostName\")"));
        assertTrue(deviceUtils.contains("System.getenv(\"COMPUTERNAME\")"));
        for (String field : List.of("osName", "osVersion", "osArch", "javaVersion",
                "localUsername", "macAddresses")) {
            assertTrue(client.contains("request.addProperty(\"" + field + "\""), field);
            assertTrue(server.contains("optional(body,\"" + field + "\""), field);
        }
        assertTrue(server.contains("/v1/devices/metadata"));
        assertTrue(main.contains("LanApiClient.syncDeviceMetadata()"));
        assertTrue(server.contains("device_name=? THEN ?"),
                "An old username-derived default must be replaced without overwriting custom names");
    }

    @Test
    void registerDatabaseAccessFailsBeforeAnyConnectionAttempt() {
        String old = System.getProperty("smartstock.db.mode");
        System.setProperty("smartstock.db.mode", "CLIENT");
        try {
            SQLException failure = assertThrows(SQLException.class, DB::getConnection);
            assertEquals("28000", failure.getSQLState());
            assertTrue(failure.getMessage().contains("Direct database access is disabled"));
        } finally {
            if (old == null) System.clearProperty("smartstock.db.mode");
            else System.setProperty("smartstock.db.mode", old);
        }
    }

    @Test
    void salesPreserveNegativeStockAndDeterministicConcurrencyLocks() throws Exception {
        String sales = Files.readString(SRC.resolve("services/LanSalesService.java"));
        assertTrue(sales.contains("productIds.sort(Integer::compareTo)"));
        assertTrue(sales.contains("quantity_on_hand=inventory.quantity_on_hand+EXCLUDED.quantity_on_hand"));
        assertTrue(sales.contains("statement.setInt(3, -line.quantity())"));
        assertFalse(sales.matches("(?s).*quantity_on_hand\\s*>=\\s*\\?.*"));
        assertTrue(sales.contains("customer_id,sale_id,location_id,transaction_type,amount"));
        assertFalse(sales.contains("description,user_id,user_name,device_id,created_at"));
    }

    private static void scan(Path root, List<Path> violations, boolean managers) throws IOException {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().endsWith(" 2.java"))
                    .filter(path -> !path.getFileName().toString().startsWith("Server"))
                    .filter(path -> !managers || !SERVER_MANAGERS.contains(path.getFileName().toString()))
                    .forEach(path -> {
                        try {
                            if (Files.readString(path).matches(JDBC_PATTERN)) violations.add(ROOT.relativize(path));
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
    }

    private static String readTree(Path root) throws IOException {
        StringBuilder result = new StringBuilder();
        if (!Files.isDirectory(root)) return "";
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !p.getFileName().toString().endsWith(" 2.java"))
                    .filter(p -> !p.getFileName().toString().startsWith("Server")).toList()) {
                result.append(Files.readString(path)).append('\n');
            }
        }
        return result.toString();
    }
}
