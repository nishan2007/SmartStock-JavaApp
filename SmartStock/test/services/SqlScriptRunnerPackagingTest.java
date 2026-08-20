package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqlScriptRunnerPackagingTest {
    @Test
    void requiredLocalV1ObjectsArePackaged() throws Exception {
        String schema = SqlScriptRunner.readResource("database/v1/local/001_schema.sql");

        assertTrue(schema.contains("CREATE TABLE public.devices"));
        assertTrue(schema.contains("CREATE TABLE public.custom_orders"));
        assertFalse(schema.contains("wifi_sessions"));
    }

    @Test
    void everyLocalProvisioningSchemaIsPackagedAndDependencyOrdered() {
        List<String> schemas = ServerProvisioningService.localWorkflowSchemaResources();
        schemas.forEach(resource ->
                assertTrue(assertPackaged(resource), "Missing packaged schema: " + resource));

        assertEquals(List.of(
                "database/v1/local/001_schema.sql",
                "database/v1/local/002_seed.sql",
                "database/v1/local/003_metadata.sql"), schemas);
    }

    @Test
    void localBaselineContainsNoWifiCompatibilityObject() throws Exception {
        for (String resource : ServerProvisioningService.localWorkflowSchemaResources()) {
            assertFalse(SqlScriptRunner.readResource(resource).contains("wifi_sessions"));
        }
    }

    @Test
    void printAddonForeignKeysAreDeferredUntilReferencedPrimaryKeysExist() throws Exception {
        String schema = SqlScriptRunner.readResource("database/v1/local/001_schema.sql");
        int quotationPrimaryKey = schema.indexOf("ADD CONSTRAINT quotation_lines_pkey PRIMARY KEY");
        int invoicePrimaryKey = schema.indexOf("ADD CONSTRAINT invoice_lines_pkey PRIMARY KEY");
        int quotationAddonForeignKey = schema.indexOf(
                "ADD CONSTRAINT quotation_line_print_addons_quotation_line_id_fkey FOREIGN KEY");
        int invoiceAddonForeignKey = schema.indexOf(
                "ADD CONSTRAINT invoice_line_print_addons_invoice_line_id_fkey FOREIGN KEY");

        assertTrue(quotationPrimaryKey >= 0);
        assertTrue(invoicePrimaryKey >= 0);
        assertTrue(quotationAddonForeignKey > quotationPrimaryKey);
        assertTrue(invoiceAddonForeignKey > invoicePrimaryKey);
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
