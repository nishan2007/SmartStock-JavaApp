package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static services.StorefrontService.*;

/** Only disposable databases on the explicitly selected loopback cluster. */
class CustomerWebDatabaseTest {
    @Test void privateSchemaDirectoryAndEmployeePermissions()throws Exception {
        String admin=System.getProperty("storefront.test.admin","");assumeTrue(!admin.isBlank());
        if(!admin.equals("jdbc:postgresql://127.0.0.1:55439/postgres"))throw new IllegalArgumentException("Use the isolated test cluster.");
        String database="smartstock_auth_test_"+UUID.randomUUID().toString().replace("-","");
        try(var root=DriverManager.getConnection(admin,"storefront_test","")){
            execute(root,"CREATE DATABASE "+database);
            try(var c=DriverManager.getConnection(admin.replace("/postgres","/"+database),"storefront_test","")){
                SchemaContractService.installLocalBaseline(c);StorefrontSchema.ensure(c);CustomerWebSchema.ensure(c);
                assertFalse(rows(c,"SELECT 1 FROM information_schema.role_table_grants WHERE table_schema='customer_auth' AND grantee='PUBLIC'").size()>0);
                UUID customer=UUID.randomUUID();
                execute(c,"INSERT INTO customer_accounts(sync_uuid,name,email,phone) VALUES(?,?,?,?)",customer,"Existing customer","saved@example.test","6123456");
                JsonObject lookup=CustomerWebAuth.lookup(c,"+5926123456","saved@example.test",1);
                assertEquals(1,lookup.getAsJsonArray("customers").size());
                assertEquals(customer.toString(),text(lookup.getAsJsonArray("customers").get(0).getAsJsonObject(),"uuid"));
                assertThrows(SecurityException.class,()->CustomerWebAuth.relink(c,UUID.randomUUID(),customer,"saved@example.test","test",-1,1));
                for(String action:java.util.List.of("ACCOUNT_REVIEW_STATE","ACCOUNT_REVIEW","ACCOUNT_LAUNCH_SAVE","ACCOUNT_LAUNCH_STATE")){var denied=new JsonObject();denied.addProperty("action",action);assertThrows(SecurityException.class,()->CustomerWebReview.run(c,denied,-1));}
                execute(c,"INSERT INTO locations(location_id,name) VALUES(1,'Authentication test') ON CONFLICT DO NOTHING");
                UUID launchCustomer=UUID.randomUUID(),auth=UUID.randomUUID();
                execute(c,"INSERT INTO customer_accounts(sync_uuid,name,email,phone) VALUES(?,?,NULL,?)",launchCustomer,"Phone-only customer","6223456");
                c.setAutoCommit(false);
                StorefrontAdminService.linkCustomer(c,auth,launchCustomer,"new@example.test",1,true);
                assertEquals("",text(one(rows(c,"SELECT COALESCE(email,'') AS email FROM customer_accounts WHERE sync_uuid=?",launchCustomer)),"email"));
                assertEquals(launchCustomer.toString(),text(one(rows(c,"SELECT customer_uuid FROM storefront.customer_links WHERE auth_id=?",auth)),"customer_uuid"));
                assertThrows(IllegalArgumentException.class,()->StorefrontAdminService.linkCustomer(c,UUID.randomUUID(),launchCustomer,"other@example.test",1,true));
                c.commit();c.setAutoCommit(true);
                execute(c,"INSERT INTO customer_auth.identities(email_key,revision,identity_data) VALUES(?,1,'{}')","a".repeat(64));
                assertThrows(SQLException.class,()->execute(c,"INSERT INTO customer_auth.identities(email_key,revision,identity_data) VALUES(?,1,'{}')","a".repeat(64)));
                assertThrows(SQLException.class,()->execute(c,"INSERT INTO customer_auth.identities(email_key,revision,identity_data) VALUES('invalid',0,'{}')"));
            }finally{execute(root,"DROP DATABASE "+database+" WITH (FORCE)");}
        }
    }
}
