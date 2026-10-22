package services;

import com.google.gson.JsonParser;
import data.DatabaseConfig;
import java.sql.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

/** Transactional DDL test restricted to the disposable catalog test database. */
@EnabledIfSystemProperty(named="smartstock.test.productSync",matches="true")
class CatalogIdentityMigrationDatabaseTest {
    @Test void migrationPreservesLegacyIdentityAndSupportsOlderRecoverySnapshots() throws Exception {
        var cfg=DatabaseConfig.load();
        try(var c=DriverManager.getConnection(cfg.jdbcUrl(),cfg.dbUser(),cfg.dbPassword())) {
            assertEquals("smartstock_catalog_identity_test",c.getCatalog(),"Requires the isolated test database");
            try(var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM information_schema.tables WHERE table_schema='public'")) {
                r.next();assertEquals(0,r.getInt(1),"Requires an empty public schema");
            }
            c.setAutoCommit(false);
            try {
                try(var s=c.createStatement()) {
                    s.execute("CREATE TABLE public.categories(category_id serial PRIMARY KEY,name text)");
                    s.execute("CREATE TABLE public.item_brands(brand_id serial PRIMARY KEY,name text)");
                    s.execute("CREATE TABLE public.vendors(vendor_id serial PRIMARY KEY,name text)");
                    s.execute("CREATE TABLE public.item_types(item_type_id serial PRIMARY KEY,category_id integer REFERENCES public.categories,name text)");
                    s.execute("CREATE TABLE public.products(product_id serial PRIMARY KEY,name text,created_at timestamptz)");
                    s.execute("INSERT INTO public.categories VALUES(5,'Paper')");
                    s.execute("INSERT INTO public.item_brands VALUES(9,'  Summer   Brand ')");
                    s.execute("INSERT INTO public.vendors VALUES(3,'Deckers')");
                    s.execute("INSERT INTO public.item_types VALUES(7,5,'  Note   Pad ')");
                    s.execute("INSERT INTO public.products VALUES(689,'Cardboard','2026-08-31 11:47:54.123456-04')");
                }
                String sql=SqlScriptRunner.readResource("/database/migrations/v1_after/20261018220000_catalog_sync_identity.sql");
                SqlScriptRunner.runSql(c,sql);
                UUID categoryUid=null;
                for(String table:List.of("categories","item_brands","vendors","products","item_types")) {
                    try(var s=c.createStatement();var r=s.executeQuery("SELECT to_jsonb(t)::text FROM public."+table+" t")) {
                        assertTrue(r.next());var row=JsonParser.parseString(r.getString(1)).getAsJsonObject();
                        UUID actual=UUID.fromString(row.get("catalog_uid").getAsString());
                        assertEquals(CatalogIdentityService.legacyUid(table,row,categoryUid),actual);
                        if(table.equals("categories"))categoryUid=actual;
                    }
                }
                SqlScriptRunner.runSql(c,sql); // upgrade can safely be retried
                try(var s=c.createStatement()) {
                    s.execute("INSERT INTO public.products(product_id,name,created_at,catalog_uid) VALUES(690,'Old snapshot','2026-09-01T00:00:00Z',NULL)");
                    try(var r=s.executeQuery("SELECT catalog_uid=public.catalog_legacy_uid('products',to_jsonb(t)) FROM public.products t WHERE product_id=690")) {
                        r.next();assertTrue(r.getBoolean(1));
                    }
                    s.execute("INSERT INTO public.products(product_id,name,created_at) VALUES(691,'New local','2026-09-01T00:00:00Z')");
                    try(var r=s.executeQuery("SELECT count(DISTINCT catalog_uid),min(product_id),max(product_id) FROM public.products")) {
                        r.next();assertEquals(3,r.getInt(1));assertEquals(689,r.getInt(2));assertEquals(691,r.getInt(3));
                    }
                    var savepoint=c.setSavepoint();
                    assertThrows(SQLException.class,()->s.execute("UPDATE public.products SET catalog_uid=gen_random_uuid() WHERE product_id=689"));
                    c.rollback(savepoint);
                }
            } finally {c.rollback();}
        }
    }
}
