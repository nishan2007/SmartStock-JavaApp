package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptRunnerPackagingTest {
    @Test
    void requiredLocalProvisioningSchemasArePackaged() throws Exception {
        String devices = SqlScriptRunner.readResource("database/device_management_setup.sql");
        String lanSecurity = SqlScriptRunner.readResource("database/lan_api_security_setup.sql");

        assertTrue(devices.contains("CREATE TABLE IF NOT EXISTS devices"));
        assertTrue(lanSecurity.contains("ALTER TABLE public.devices"));
    }

    @Test
    void locationSetupToleratesCompanyCustomizationNotExistingYet() throws Exception {
        String locations = SqlScriptRunner.readResource("database/location_management_setup.sql");

        assertTrue(locations.contains("ALTER TABLE IF EXISTS company_customization"));
    }

    @Test
    void customOrderPaymentVoidColumnsExistBeforeTheirIndex() throws Exception {
        String customOrders = SqlScriptRunner.readResource("database/custom_orders_setup.sql");

        assertTrue(customOrders.indexOf("ADD COLUMN IF NOT EXISTS voided_by_user_id")
                < customOrders.indexOf("custom_order_payments_voided_by_user_fk_idx"));
        assertTrue(customOrders.indexOf("ADD COLUMN IF NOT EXISTS cancelled_by_user_id")
                < customOrders.indexOf("custom_orders_cancelled_by_user_fk_idx"));
    }

    @Test
    void everyLocalProvisioningSchemaIsPackagedAndDependencyOrdered() {
        List<String> schemas = ServerProvisioningService.localWorkflowSchemaResources();
        schemas.forEach(resource ->
                assertTrue(assertPackaged(resource), "Missing packaged schema: " + resource));

        assertTrue(schemas.indexOf("database/device_management_setup.sql")
                < schemas.indexOf("database/cash_drawer_management_setup.sql"));
        assertTrue(schemas.indexOf("database/company_customization_setup.sql")
                < schemas.indexOf("database/sale_override_controls_setup.sql"));
        assertTrue(schemas.indexOf("database/custom_orders_setup.sql")
                < schemas.indexOf("database/cash_drawer_management_setup.sql"));
    }

    private static boolean assertPackaged(String resource) {
        try {
            return !SqlScriptRunner.readResource(resource).isBlank();
        } catch (Exception ex) {
            return false;
        }
    }

    @Test
    void multiScriptRunnerDoesNotSilentlySkipMissingFiles() throws Exception {
        String source = Files.readString(Path.of("src/services/SqlScriptRunner.java"));

        assertTrue(source.contains("executed += runResource(conn, relativePath)"));
        assertFalse(source.contains("if (!Files.isRegularFile(script)) {\n                continue;"));
    }
}
