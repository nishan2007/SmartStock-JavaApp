package services;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EmployeePhotoUpgradeTest {
    @Test void crossStoreUserUpsertCarriesOriginalAndFrame()throws Exception {
        String url=System.getProperty("smartstock.photo.test.jdbc","");assumeTrue(!url.isBlank());
        if(!url.equals("jdbc:postgresql://127.0.0.1:56563/postgres"))throw new IllegalArgumentException("Use disposable photo cluster.");
        try(var c=DriverManager.getConnection(url,"postgres","");var st=c.createStatement()) {
            st.execute("CREATE TEMP TABLE roles(role_id int,role_name text); INSERT INTO roles VALUES(1,'USER')");
            st.execute("""
                CREATE TEMP TABLE users(user_id serial PRIMARY KEY,username text,first_name text,middle_name text,last_name text,
                full_name text,nickname text,email text,phone text,employee_photo_url text,employee_photo_source_url text,
                employee_photo_frame text,employee_id_card_document_url text,date_of_birth date,hire_date date,badge_id text,
                badge_generated_at timestamptz,badge_print_count int,badge_rotated_at timestamptz,badge_rotated_by_user_id int,
                badge_rotated_by_name text,compensation_type text,salary numeric,role_id int,auth_user_id uuid,is_active boolean,
                deactivated_at timestamptz,deactivated_by_user_id int,deactivated_by_name text,created_at timestamptz,updated_at timestamptz)
                """);
            var row=new com.google.gson.JsonObject();row.addProperty("user_id",4);row.addProperty("username","example");
            row.addProperty("full_name","Example Employee");row.addProperty("role_name","USER");row.addProperty("role_policy_version",1);
            row.addProperty("salary",0);row.addProperty("compensation_type","HOURLY");row.addProperty("hire_date","2026-11-21");
            row.addProperty("badge_print_count",0);row.addProperty("is_active",true);row.addProperty("created_at","2026-11-21T12:00:00Z");row.addProperty("updated_at","2026-11-21T12:00:00Z");
            row.addProperty("employee_photo_url","display");row.addProperty("employee_photo_source_url","original");
            String frame=EmployeePhotoFraming.encode(EmployeePhotoFraming.fit());row.addProperty("employee_photo_frame",frame);
            var method=CrossStoreReferenceSyncService.class.getDeclaredMethod("upsertUser",Connection.class,com.google.gson.JsonObject.class);method.setAccessible(true);
            method.invoke(null,c,row);
            try(var r=st.executeQuery("SELECT employee_photo_url,employee_photo_source_url,employee_photo_frame FROM users WHERE user_id=4")){assertTrue(r.next());assertEquals("display",r.getString(1));assertEquals("original",r.getString(2));assertEquals(frame,r.getString(3));}
            row.addProperty("employee_photo_source_url","next-original");row.addProperty("updated_at","2026-11-21T13:00:00Z");method.invoke(null,c,row);
            try(var r=st.executeQuery("SELECT employee_photo_source_url FROM users WHERE user_id=4")){assertTrue(r.next());assertEquals("next-original",r.getString(1));}
        }
    }
    @Test void upgradePreservesPhotoEnforcesDriftGuardAndSavesFraming()throws Exception {
        String url=System.getProperty("smartstock.photo.test.jdbc","");assumeTrue(!url.isBlank());
        if(!url.equals("jdbc:postgresql://127.0.0.1:56563/postgres"))throw new IllegalArgumentException("Use disposable photo cluster.");
        try(var c=DriverManager.getConnection(url,"postgres","");var st=c.createStatement()) {
            st.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
            st.execute("CREATE TABLE users(user_id int PRIMARY KEY,employee_photo_url text)");st.execute("INSERT INTO users VALUES(1,'existing-photo')");
            st.execute("CREATE TABLE smartstock_schema_metadata(schema_scope text PRIMARY KEY,baseline_version integer,resource_fingerprint_sha256 text,catalog_fingerprint_sha256 text)");
            try(var p=c.prepareStatement("INSERT INTO smartstock_schema_metadata VALUES('LOCAL',?,'old',?)")){
                p.setInt(1,SchemaContractService.BASELINE_VERSION);p.setString(2,SchemaContractService.catalogFingerprint(c,List.of("public")));p.executeUpdate();
            }
            st.execute("CREATE TABLE drift(value text)");assertThrows(SQLException.class,()->SchemaContractService.ensureEmployeePhotoFramingUpgrade(c));st.execute("DROP TABLE drift");
            SchemaContractService.ensureEmployeePhotoFramingUpgrade(c);SchemaContractService.ensureEmployeePhotoFramingUpgrade(c);
            try(var r=st.executeQuery("SELECT employee_photo_url,employee_photo_source_url,employee_photo_frame FROM users")){assertTrue(r.next());assertEquals("existing-photo",r.getString(1));assertNull(r.getString(2));assertNull(r.getString(3));}
            String frame=EmployeePhotoFraming.encode(new EmployeePhotoFraming.Frame(1.2,0,-.05));
            LanEmployeeAdminService.SaveRequest request=request("original-photo",frame);
            LanEmployeeAdminService.savePhotoFrame(c,1,request);
            assertEquals("original-photo",request.withEmail("new@example.com").photoSourceUrl());assertEquals(frame,request.withEmail("new@example.com").photoFrame());
            LanEmployeeAdminService.savePhotoFrame(c,1,request(null,null)); // old client preserves metadata
            try(var r=st.executeQuery("SELECT employee_photo_source_url,employee_photo_frame FROM users")){assertTrue(r.next());assertEquals("original-photo",r.getString(1));assertEquals(frame,r.getString(2));}
            assertThrows(SQLException.class,()->LanEmployeeAdminService.savePhotoFrame(c,1,request("original-photo","{}")));
            assertThrows(SQLException.class,()->LanEmployeeAdminService.savePhotoFrame(c,1,request(null,frame)));
            LanEmployeeAdminService.savePhotoFrame(c,1,request("",""));
            try(var r=st.executeQuery("SELECT employee_photo_source_url,employee_photo_frame FROM users")){assertTrue(r.next());assertNull(r.getString(1));assertNull(r.getString(2));}
            try(var r=st.executeQuery("SELECT resource_fingerprint_sha256,catalog_fingerprint_sha256 FROM smartstock_schema_metadata")){assertTrue(r.next());assertEquals(SchemaContractService.resourceFingerprint(SchemaContractService.localContractResources()),r.getString(1));assertEquals(SchemaContractService.catalogFingerprint(c,List.of("public")),r.getString(2));}
        }
    }
    private static LanEmployeeAdminService.SaveRequest request(String original,String frame){return new LanEmployeeAdminService.SaveRequest("user",null,"First",null,"Last","First Last",null,"e@example.com","123","display-photo",null,null,java.time.LocalDate.now(),"HOURLY",java.math.BigDecimal.ONE,"USER",true,List.of(1),null,null,false,null,original,frame);}
}
