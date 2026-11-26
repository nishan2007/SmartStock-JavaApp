package services;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StoreKeysSchemaTest {
    @Test void newInstallAndExistingStoreUpgradeValidate()throws Exception {
        assumeTrue(!System.getProperty("smartstock.keys.test.jdbc","").isBlank());
        try(Connection c=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:55447/smartstock_store_keys_schema_test","store_keys_test","")) {
            SchemaContractService.installLocalBaseline(c);assertTrue(SchemaContractService.validateLocal(c).ready());
            try(var s=c.createStatement()) {s.execute("DROP TABLE store_key_events,store_key_custody,store_keys CASCADE; DROP FUNCTION store_key_events_immutable()");}
            // Recreate the recorded catalog of a pre-feature store, then exercise its guarded upgrade.
            try(var p=c.prepareStatement("UPDATE smartstock_schema_metadata SET catalog_fingerprint_sha256=?,resource_fingerprint_sha256='0000000000000000000000000000000000000000000000000000000000000000' WHERE schema_scope='LOCAL'")) {
                p.setString(1,SchemaContractService.catalogFingerprint(c,List.of("public")));p.executeUpdate();
            }
            try(var s=c.createStatement()){s.execute("ALTER TABLE locations ADD COLUMN unexpected_key_test_drift boolean");}
            assertThrows(SQLException.class,()->SchemaContractService.ensureStoreKeysUpgrade(c));
            try(var s=c.createStatement()){s.execute("ALTER TABLE locations DROP COLUMN unexpected_key_test_drift");}
            SchemaContractService.ensureStoreKeysUpgrade(c);SchemaContractService.ensureStoreKeysUpgrade(c);
            assertTrue(SchemaContractService.validateLocal(c).ready());
            assertTrue(ReferenceDataSyncService.cloudMirrorTablesForApi().containsAll(List.of("store_keys","store_key_custody","store_key_events")));
        }
    }
}
