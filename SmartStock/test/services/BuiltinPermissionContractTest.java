package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinPermissionContractTest {
    @Test
    void paymentReversalPermissionIsProvisionedForNewAndExistingStores() throws Exception {
        String migration="database/migrations/v1_after/20261112120000_order_payment_reversal_permission.sql";
        assertTrue(SchemaContractService.localContractResources().contains(migration));
        String contract=Files.readString(Path.of("src/services/SchemaContractService.java"));
        assertTrue(contract.contains("SqlScriptRunner.readResource(\""+migration+"\")"));
        for(String source:java.util.List.of(migration,"database/v1/local/002_seed.sql","database/v1/cloud/003_metadata.sql")) {
            String sql=Files.readString(Path.of(source));
            assertTrue(sql.contains("'REVERSE_CUSTOM_ORDER_PAYMENTS'"),source);
            assertTrue(sql.contains("upper(r.role_name)='ADMIN'"),source);
        }
    }

    @Test
    void smartDriveManagementIsProvisionedForNewAndExistingStores() throws Exception {
        String migration = "database/migrations/v1_after/20261108120000_smartdrive_permission.sql";
        assertTrue(SchemaContractService.localContractResources().contains(migration));
        String contract = Files.readString(Path.of("src/services/SchemaContractService.java"));
        assertTrue(contract.contains("SqlScriptRunner.readResource(\"" + migration + "\")"));
        for (String source : java.util.List.of(migration, "database/v1/local/002_seed.sql",
                "database/v1/cloud/003_metadata.sql")) {
            String sql = Files.readString(Path.of(source));
            assertTrue(sql.contains("'MANAGE_SMARTDRIVE'"), source);
            assertTrue(sql.contains("ON CONFLICT"), source);
        }
    }

    @Test
    void enforcedDesktopPermissionsExistInThePackagedCatalog() throws Exception {
        StringBuilder java = new StringBuilder();
        try (var paths = Files.walk(Path.of("src"))) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                java.append(Files.readString(path));
            }
        }
        StringBuilder packagedCatalog = new StringBuilder();
        for (String resource : SchemaContractService.localContractResources()) {
            packagedCatalog.append(Files.readString(Path.of(resource)));
        }
        String catalog = packagedCatalog.toString();
        var call = Pattern.compile("(?:hasPermission|requirePermission|requireAnyPermission)\\s*\\((.{0,500}?)\\)",
                Pattern.DOTALL).matcher(java);
        Pattern key = Pattern.compile("\"([A-Z][A-Z0-9_]+)\"");
        while (call.find()) {
            var keys = key.matcher(call.group(1));
            while (keys.find()) {
                assertTrue(catalog.contains("'" + keys.group(1) + "'"),
                        "Missing packaged desktop permission: " + keys.group(1));
            }
        }
    }

    @Test
    void existingStoresRunPermissionRepairBeforeValidation() throws Exception {
        String contract = Files.readString(Path.of("src/services/SchemaContractService.java"));
        assertTrue(contract.indexOf("ensureBuiltinPermissionsUpgrade(connection)")
                < contract.indexOf("Readiness readiness = validateLocal(connection)"));
        assertTrue(contract.contains("builtinAdminPermissionsComplete(connection)"));
    }
}
