package services;

import java.sql.Connection;
import java.sql.SQLException;

/** Additive extension outside the established public-schema fingerprint. */
public final class StorefrontSchema {
    // Captured from the canonical v17 schema on PostgreSQL 17; migration tests verify installation paths.
    private static final String VERSION_17_CATALOG = "79a4141d2c07cdb8f0aacfb9d7a3c35a4f3bcd51d3511ecca502eb4d722e6512";
    private StorefrontSchema() { }
    public static void ensure(Connection c) throws SQLException {
        CustomerWebSchema.ensure(c);
        int installed=0;
        try (var p=c.prepareStatement("SELECT to_regclass('storefront.schema_version') IS NOT NULL"); var r=p.executeQuery()) {
            r.next(); if(r.getBoolean(1)) {
                try(var version=c.prepareStatement("SELECT MAX(version) FROM storefront.schema_version");var v=version.executeQuery()){
                    v.next();installed=v.getInt(1);if(installed<1||installed>17)throw new SQLException("Unsupported storefront schema version.");
                }
                if(installed==17){validate(c);return;}
            }
        }
        if(!c.getAutoCommit())throw new SQLException("Storefront schema installation requires its own connection transaction.");
        boolean auto=c.getAutoCommit(); c.setAutoCommit(false);
        try {
            try(var p=c.prepareStatement("SELECT pg_advisory_xact_lock(734891231)")){p.execute();}
            if(installed==0)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/001_schema.sql"));
            else {
                if(installed==1)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/002_enrollment.sql"));
                if(installed<=2)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/003_campaign.sql"));
                if(installed<=3)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/004_projects.sql"));
                if(installed<=4)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/005_quote_requests.sql"));
                if(installed<=5)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/006_quote_files.sql"));
                if(installed<=6)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/007_quote_proofs.sql"));
                if(installed<=7)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/008_quote_order_links.sql"));
                if(installed<=8)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/009_project_products.sql"));
                if(installed<=9)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/010_project_media.sql"));
                if(installed<=10)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/011_service_availability.sql"));
                if(installed<=11)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/012_project_service.sql"));
                if(installed<=12)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/013_favorites.sql"));
                if(installed<=13)SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/014_campaign_actions.sql"));
                SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/015_website_prices.sql"));
            }
            SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/016_customer_merges.sql"));
            SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/storefront/017_custom_item_categories.sql"));
            validate(c);
            c.commit();
        } catch(Exception e){c.rollback();throw new SQLException("Storefront schema installation failed.",e);}
        finally {c.setAutoCommit(auto);}
    }
    static void validate(Connection c)throws SQLException{
        String catalog=SchemaContractService.catalogFingerprint(c,java.util.List.of("storefront"));
        var attributes=StorefrontService.rows(c,"""
            SELECT table_name,column_name,numeric_precision,numeric_scale,character_maximum_length,
                   datetime_precision,is_identity,identity_generation,is_generated,generation_expression
            FROM information_schema.columns WHERE table_schema='storefront' ORDER BY table_name,column_name
            """);
        var validity=StorefrontService.rows(c,"""
            SELECT 'constraint' AS kind,t.relname AS table_name,k.conname AS name,k.convalidated AS valid
            FROM pg_constraint k JOIN pg_class t ON t.oid=k.conrelid JOIN pg_namespace n ON n.oid=t.relnamespace
            WHERE n.nspname='storefront'
            UNION ALL
            SELECT 'index',t.relname,i.relname,x.indisvalid AND x.indisready
            FROM pg_index x JOIN pg_class t ON t.oid=x.indrelid JOIN pg_class i ON i.oid=x.indexrelid
            JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='storefront'
            ORDER BY kind,table_name,name
            """);
        String actual=LanSecurity.sha256(catalog+"\n"+attributes+"\n"+validity);
        if(!VERSION_17_CATALOG.equals(actual))throw new SQLException("Storefront schema differs from the supported version 17 catalog ("+actual+"). Restore or migrate the database before enabling checkout.");
        if(!StorefrontService.rows(c,"""
            SELECT 1 FROM pg_namespace n CROSS JOIN LATERAL aclexplode(COALESCE(n.nspacl,acldefault('n',n.nspowner))) a
            WHERE n.nspname='storefront' AND a.grantee=0
            """).isEmpty())throw new SQLException("The storefront schema must not be accessible to PUBLIC.");
    }
}
