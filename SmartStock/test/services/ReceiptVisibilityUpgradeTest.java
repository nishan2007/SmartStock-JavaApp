package services;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ReceiptVisibilityUpgradeTest {
    @Test void upgradesExistingStoresWithoutChangingVisibilityDefaults()throws Exception {
        String url=System.getProperty("smartstock.variants.test.jdbc","");assumeTrue(!url.isBlank());
        try(Connection c=DriverManager.getConnection(url,System.getProperty("smartstock.variants.test.user","variant_test"),"")) {
            try(var ps=c.prepareStatement("SELECT to_regclass('public.products')");var rs=ps.executeQuery()){rs.next();if(rs.getString(1)==null)SchemaContractService.installLocalBaseline(c);}
            SchemaContractService.requireLocalReady(c);
            try(var ps=c.prepareStatement("ALTER TABLE company_customization DROP COLUMN sale_receipt_visibility")){ps.executeUpdate();}
            try(var ps=c.prepareStatement("UPDATE smartstock_schema_metadata SET catalog_fingerprint_sha256=? WHERE schema_scope='LOCAL'")){ps.setString(1,SchemaContractService.catalogFingerprint(c,List.of("public")));ps.executeUpdate();}
            SchemaContractService.ensureSaleReceiptVisibilityUpgrade(c);
            assertTrue(SchemaContractService.validateLocal(c).ready());
            try(var ps=c.prepareStatement("SELECT count(*) FROM company_customization WHERE sale_receipt_visibility IS NOT NULL");var rs=ps.executeQuery()){rs.next();assertEquals(0,rs.getInt(1));}
        }
    }
}
