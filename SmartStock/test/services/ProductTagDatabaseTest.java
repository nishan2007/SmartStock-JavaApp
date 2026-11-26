package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Requires an explicitly provisioned disposable loopback cluster; never uses store config. */
class ProductTagDatabaseTest {
    @Test void defaultVisibilityBulkEditsRenameMergeAndRollback()throws Exception {
        String url=System.getProperty("tags.test.admin","");assumeTrue(!url.isEmpty());
        if(!url.equals("jdbc:postgresql://127.0.0.1:55443/postgres"))throw new IllegalArgumentException("Use disposable tags cluster on port 55443.");
        String db="tags_test_"+UUID.randomUUID().toString().replace("-","");
        try(Connection root=DriverManager.getConnection(url,"tags_test","")) {
            sql(root,"CREATE DATABASE "+db);
            try(Connection c=DriverManager.getConnection(url.replace("/postgres","/"+db),"tags_test","")) {
                sql(c,"CREATE TABLE products(product_id integer PRIMARY KEY,updated_at timestamptz DEFAULT now()); INSERT INTO products(product_id) VALUES(1),(2)");
                sql(c,"CREATE TABLE sync_outbox(event_type text,location_id integer,device_id text,user_id integer,payload jsonb,origin_location_id integer,origin_device_id text)");
                sql(c,"CREATE TABLE security_audit_events(event_type text,device_id uuid,actor_user_id integer,details text)");
                SqlScriptRunner.runResource(c,"database/migrations/v1_after/20261123120000_product_tags.sql");
                SqlScriptRunner.runResource(c,"database/migrations/v1_after/20261123120000_product_tags.sql");
                c.setAutoCommit(false);
                String tag=change(c,"{action:'CREATE',name:' School   Wear '}");
                assertEquals(tag,change(c,"{action:'CREATE',name:'school wear'}"));
                assertEquals(0,count(c,"SELECT count(*) FROM product_tag_definitions WHERE is_public"));
                change(c,"{action:'ADD',tagId:'"+tag+"',productIds:[1,2]}");
                change(c,"{action:'ADD',tagId:'"+tag+"',productIds:[1,2]}");
                assertEquals(2,count(c,"SELECT count(*) FROM product_tags"));
                change(c,"{action:'SAVE',tagId:'"+tag+"',name:'Uniforms',isPublic:true}");
                assertEquals(1,count(c,"SELECT count(*) FROM product_tag_definitions WHERE is_public AND name='Uniforms'"));
                assertEquals(2,ProductTagService.state(c).getAsJsonArray("assignments").size());
                String internal=change(c,"{action:'CREATE',name:'Needs Photo'}");
                change(c,"{action:'ADD',tagId:'"+internal+"',productIds:[1]}");
                try(var p=c.createStatement();var rows=p.executeQuery("SELECT "+ProductTagService.PUBLIC_TAGS_SQL+" FROM products p WHERE product_id=1")) {
                    rows.next();assertEquals("[\"Uniforms\"]",rows.getString(1));
                }
                change(c,"{action:'MERGE',tagId:'"+tag+"',targetId:'"+internal+"'}");
                assertEquals(2,count(c,"SELECT count(*) FROM product_tags"));
                assertEquals(0,count(c,"SELECT count(*) FROM product_tag_definitions WHERE is_public"));
                try(var p=c.createStatement();var rows=p.executeQuery("SELECT "+ProductTagService.PUBLIC_TAGS_SQL+" FROM products p WHERE product_id=1")) {
                    rows.next();assertEquals("[]",rows.getString(1));
                }
                change(c,"{action:'REMOVE',tagId:'"+internal+"',productIds:[1]}");
                assertEquals(1,count(c,"SELECT count(*) FROM product_tags"));
                c.commit();
                assertThrows(Exception.class,()->change(c,"{action:'ADD',tagId:'"+internal+"',productIds:[1,999]}"));c.rollback();
                assertEquals(1,count(c,"SELECT count(*) FROM product_tags"));
                assertTrue(count(c,"SELECT count(*) FROM sync_outbox")>0);
                c.rollback();
                c.setAutoCommit(true);
                sql(c,"DROP TABLE product_tags; DROP TABLE product_tag_definitions");
                sql(c,"CREATE TABLE smartstock_schema_metadata(schema_scope text,baseline_version integer,resource_fingerprint_sha256 text,catalog_fingerprint_sha256 text)");
                try(var p=c.prepareStatement("INSERT INTO smartstock_schema_metadata VALUES('LOCAL',1,'previous',?)")) {
                    p.setString(1,SchemaContractService.catalogFingerprint(c,List.of("public")));p.executeUpdate();
                }
                SchemaContractService.ensureProductTagsUpgrade(c);
                SchemaContractService.ensureProductTagsUpgrade(c);
                assertEquals(0,count(c,"SELECT count(*) FROM product_tags"));
                try(var p=c.createStatement();var rows=p.executeQuery("SELECT resource_fingerprint_sha256,catalog_fingerprint_sha256 FROM smartstock_schema_metadata")) {
                    rows.next();assertEquals(SchemaContractService.resourceFingerprint(SchemaContractService.localContractResources()),rows.getString(1));
                    assertEquals(SchemaContractService.catalogFingerprint(c,List.of("public")),rows.getString(2));
                }
                sql(c,"DROP TABLE product_tags; DROP TABLE product_tag_definitions; ALTER TABLE products ADD COLUMN unexpected text");
                assertThrows(SQLException.class,()->SchemaContractService.ensureProductTagsUpgrade(c));
            }finally{sql(root,"DROP DATABASE "+db+" WITH (FORCE)");}
        }
    }
    private static String change(Connection c,String json)throws Exception {
        Map<String,Object> result=ProductTagService.mutate(c,JsonParser.parseString(json).getAsJsonObject(),UUID.randomUUID(),1,1);
        return result.get("tagId").toString();
    }
    private static void sql(Connection c,String s)throws SQLException{try(var p=c.createStatement()){p.execute(s);}}
    private static int count(Connection c,String s)throws SQLException{try(var p=c.createStatement();var r=p.executeQuery(s)){r.next();return r.getInt(1);}}
}
