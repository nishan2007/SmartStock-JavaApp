package services;

import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EmployeeRememberDeviceUpgradeTest {
    @Test void existingStoreUpgradeAddsColumnPreservesSessionsAndUpdatesContract()throws Exception {
        String url=System.getProperty("smartstock.remember.upgrade.jdbc","");assumeTrue(!url.isBlank());
        if(!url.equals("jdbc:postgresql://127.0.0.1:56562/postgres"))throw new IllegalArgumentException("Use the disposable remembered-login cluster.");
        try(Connection c=DriverManager.getConnection(url,"postgres","");Statement st=c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS public.unexpected_drift,public.employee_web_sessions,public.mobile_managed_devices,public.smartstock_schema_metadata CASCADE");
            st.execute("CREATE TABLE public.mobile_managed_devices(device_id integer PRIMARY KEY)");
            st.execute("INSERT INTO public.mobile_managed_devices VALUES(1)");
            st.execute("CREATE TABLE public.employee_web_sessions(session_id integer PRIMARY KEY,session_hash text)");
            st.execute("INSERT INTO public.employee_web_sessions VALUES(1,'existing-session')");
            st.execute("CREATE TABLE public.smartstock_schema_metadata(schema_scope text PRIMARY KEY,baseline_version integer,resource_fingerprint_sha256 text,catalog_fingerprint_sha256 text)");
            try(var p=c.prepareStatement("INSERT INTO public.smartstock_schema_metadata VALUES('LOCAL',?,'previous',?)")) {
                p.setInt(1,SchemaContractService.BASELINE_VERSION);p.setString(2,SchemaContractService.catalogFingerprint(c,List.of("public")));p.executeUpdate();
            }
            // Drift must never silently be incorporated into the contract.
            st.execute("CREATE TABLE public.unexpected_drift(value text)");
            SQLException drift=assertThrows(SQLException.class,()->SchemaContractService.ensureEmployeeWebRememberDeviceUpgrade(c));
            assertEquals("55000",drift.getSQLState());
            try(var r=c.getMetaData().getColumns(null,"public","employee_web_sessions","remember_device")){assertFalse(r.next());}
            st.execute("DROP TABLE public.unexpected_drift");
            SchemaContractService.ensureEmployeeWebRememberDeviceUpgrade(c);
            SchemaContractService.ensureMobileDeviceRememberLoginUpgrade(c);
            SchemaContractService.ensureMobileDeviceRememberLoginUpgrade(c);
            try(var r=st.executeQuery("SELECT remember_login FROM public.mobile_managed_devices WHERE device_id=1")){assertTrue(r.next());assertFalse(r.getBoolean(1));assertFalse(r.wasNull());}
            SchemaContractService.ensureMobileDeviceInfoUpgrade(c);
            SchemaContractService.ensureMobileDeviceInfoUpgrade(c);
            try(var r=st.executeQuery("SELECT device_info::text,device_info_updated_at FROM public.mobile_managed_devices WHERE device_id=1")){assertTrue(r.next());assertEquals("{}",r.getString(1));assertNull(r.getTimestamp(2));}
            assertTrue(c.getAutoCommit());
            try(var r=st.executeQuery("SELECT session_hash,remember_device FROM public.employee_web_sessions WHERE session_id=1")) {
                assertTrue(r.next());assertEquals("existing-session",r.getString(1));assertFalse(r.getBoolean(2));assertFalse(r.wasNull());
            }
            String expected=SchemaContractService.resourceFingerprint(SchemaContractService.localContractResources());
            String catalog=SchemaContractService.catalogFingerprint(c,List.of("public"));
            try(var r=st.executeQuery("SELECT resource_fingerprint_sha256,catalog_fingerprint_sha256 FROM public.smartstock_schema_metadata")) {
                assertTrue(r.next());assertEquals(expected,r.getString(1));assertEquals(catalog,r.getString(2));
            }
            SchemaContractService.ensureEmployeeWebRememberDeviceUpgrade(c);
            try(var r=st.executeQuery("SELECT count(*) FROM public.employee_web_sessions")){assertTrue(r.next());assertEquals(1,r.getInt(1));}
        }
    }
}
