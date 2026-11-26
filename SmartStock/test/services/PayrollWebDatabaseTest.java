package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PayrollWebDatabaseTest {
    @Test void realJournalSupportsPartialFullRetryDateAndPermissionChecks()throws Exception {
        String url=System.getProperty("smartstock.payroll.web.jdbc","");assumeTrue(!url.isBlank());
        if(!url.equals("jdbc:postgresql://127.0.0.1:56564/postgres"))throw new IllegalArgumentException("Use disposable payroll web cluster.");
        try(var c=DriverManager.getConnection(url,"postgres","");var st=c.createStatement()) {
            st.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
            try{SchemaContractService.installLocalBaseline(c);}catch(SQLException existingStorefrontMismatch){
                // The baseline installer also validates an unrelated Storefront v17
                // catalogue. Its pre-existing mismatch must not hide payroll checks.
                if(!existingStorefrontMismatch.getMessage().startsWith("Storefront schema differs from the supported version 17 catalog"))throw existingStorefrontMismatch;
            }
            assertTrue(SchemaContractService.validateLocal(c).ready());
            st.execute("DROP TABLE payroll_web_requests");
            try(var p=c.prepareStatement("UPDATE smartstock_schema_metadata SET catalog_fingerprint_sha256=?,resource_fingerprint_sha256=repeat('0',64) WHERE schema_scope='LOCAL'")){p.setString(1,SchemaContractService.catalogFingerprint(c,List.of("public")));p.executeUpdate();}
            st.execute("CREATE TABLE unexpected_payroll_drift(value text)");assertThrows(SQLException.class,()->SchemaContractService.ensurePayrollWebUpgrade(c));st.execute("DROP TABLE unexpected_payroll_drift");
            SchemaContractService.ensurePayrollWebUpgrade(c);SchemaContractService.ensurePayrollWebUpgrade(c);assertTrue(SchemaContractService.validateLocal(c).ready());
            c.setAutoCommit(false);
            st.execute("INSERT INTO locations(location_id,name,timezone,receipt_store_code) VALUES(901,'Test Main','America/Guyana','0901'),(902,'Test Second','America/Guyana','0902')");
            st.execute("INSERT INTO users(user_id,username,full_name,role_id,salary) VALUES(901,'payroll_manager_test','Test Manager',1,0),(902,'payroll_employee_test','Test Employee',3,100)");
            st.execute("INSERT INTO employee_time_clock(user_id,user_name,location_id,location_name,work_date,clock_in,clock_out,total_hours_worked,total_earned) VALUES(902,'Test Employee',901,'Test Main','2026-11-20','2026-11-20 08:00-04','2026-11-20 16:00-04',8,800)");
            st.execute("INSERT INTO employee_time_clock(user_id,user_name,location_id,location_name,work_date,clock_in,clock_out,total_hours_worked,total_earned) VALUES(902,'Test Employee',902,'Test Second','2026-11-19','2026-11-19 08:00-04','2026-11-19 12:00-04',4,400)");
            UUID browser=UUID.randomUUID();
            assertThrows(SecurityException.class,()->PayrollWebService.execute(c,902,"Employee",browser,"payroll/dashboard",new JsonObject(),901));
            var dashboard=(Map<?,?>)PayrollWebService.execute(c,901,"Manager",browser,"payroll/dashboard",new JsonObject(),901);
            JsonObject row=LanJson.create().toJsonTree(((List<?>)dashboard.get("rows")).get(0)).getAsJsonObject();
            assertEquals(1200,row.get("total").getAsInt());assertEquals(12,row.get("hours").getAsInt());assertEquals(2,row.getAsJsonArray("stores").size());assertEquals(0,((Number)dashboard.get("defaultStore")).intValue());
            JsonObject payment=new JsonObject();payment.addProperty("userId",902);payment.add("start",row.get("start"));payment.add("end",row.get("end"));payment.addProperty("amount",200);payment.addProperty("date","2026-11-20");payment.addProperty("expectedPaid",0);payment.addProperty("method","CASH");payment.addProperty("reference","");payment.addProperty("idempotencyKey",UUID.randomUUID().toString());
            PayrollWebService.execute(c,901,"Manager",browser,"payroll/pay",payment,901);
            PayrollWebService.execute(c,901,"Manager",browser,"payroll/pay",payment,901);
            try(var r=st.executeQuery("SELECT count(*),sum(total_pay),min((paid_at AT TIME ZONE 'America/Guyana')::date) FROM payroll_payments WHERE user_id=902")){assertTrue(r.next());assertEquals(1,r.getInt(1));assertEquals(200,r.getInt(2));assertEquals("2026-11-20",r.getString(3));}
            try(var r=st.executeQuery("SELECT count(*),sum(amount),min(expense_date) FROM expenses WHERE source_type='PAYROLL'")){assertTrue(r.next());assertEquals(1,r.getInt(1));assertEquals(200,r.getInt(2));assertEquals("2026-11-20",r.getString(3));}
            JsonObject changed=payment.deepCopy();changed.addProperty("amount",201);assertThrows(IllegalArgumentException.class,()->PayrollWebService.execute(c,901,"Manager",browser,"payroll/pay",changed,901));
            JsonObject stale=payment.deepCopy();stale.addProperty("idempotencyKey",UUID.randomUUID().toString());assertThrows(IllegalArgumentException.class,()->PayrollWebService.execute(c,901,"Manager",browser,"payroll/pay",stale,901));
            for(int invalid:new int[]{0,-1,1001}){var bad=payment.deepCopy();bad.addProperty("amount",invalid);bad.addProperty("expectedPaid",200);bad.addProperty("idempotencyKey",UUID.randomUUID().toString());assertThrows(IllegalArgumentException.class,()->PayrollWebService.execute(c,901,"Manager",browser,"payroll/pay",bad,901));}
            payment.addProperty("idempotencyKey",UUID.randomUUID().toString());payment.addProperty("amount",1000);payment.addProperty("expectedPaid",200);payment.addProperty("method","BANK");payment.addProperty("reference","TEST-BANK-PAYMENT");
            PayrollWebService.execute(c,901,"Manager",browser,"payroll/pay",payment,901);
            dashboard=(Map<?,?>)PayrollWebService.execute(c,901,"Manager",browser,"payroll/dashboard",new JsonObject(),901);row=LanJson.create().toJsonTree(((List<?>)dashboard.get("rows")).get(0)).getAsJsonObject();
            assertEquals(1200,row.get("paid").getAsInt());assertEquals(0,row.get("balance").getAsInt());
            try(var r=st.executeQuery("SELECT count(*),sum(amount),min(transaction_date) FROM bank_transactions WHERE source_type='PAYROLL'")){assertTrue(r.next());assertEquals(1,r.getInt(1));assertEquals(1000,r.getInt(2));assertEquals("2026-11-20",r.getString(3));}
            c.rollback();
        }
    }
}
