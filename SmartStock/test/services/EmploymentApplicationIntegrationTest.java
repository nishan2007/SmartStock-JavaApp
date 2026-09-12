package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Explicit disposable database only; never opens the configured store database. */
class EmploymentApplicationIntegrationTest {
    private static Connection connect(String url)throws SQLException{return DriverManager.getConnection(url,"portal_test","");}
    @Test void migrationDraftOwnershipRevisionUploadAndReview()throws Exception {
        String url=System.getProperty("smartstock.portal.test.jdbc","");assumeTrue(url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/portal_test"));
        try(Connection c=connect(url)){
            try(var p=c.createStatement();var r=p.executeQuery("SELECT to_regclass('public.employee_registrations')")){r.next();if(r.getString(1)==null)SchemaContractService.installLocalBaseline(c);}
            SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20260909120000_employment_portal.sql"));
            SqlScriptRunner.runSql(c,SqlScriptRunner.readResource("database/migrations/v1_after/20260909120000_employment_portal.sql"));
            assertTrue(SchemaContractService.validateLocal(c).ready());
            String suffix=UUID.randomUUID().toString().substring(0,8),email=suffix+"@example.test";
            int location;try(var p=c.prepareStatement("INSERT INTO locations(name,receipt_store_code) VALUES('Portal test',?) RETURNING location_id")){p.setString(1,suffix);try(var r=p.executeQuery()){r.next();location=r.getInt(1);}}
            int manager;try(var p=c.prepareStatement("INSERT INTO users(username,full_name) VALUES(?,'Manager') RETURNING user_id")){p.setString(1,"manager-"+suffix);try(var r=p.executeQuery()){r.next();manager=r.getInt(1);}}
            UUID auth=UUID.randomUUID(),other=UUID.randomUUID(),id=EmploymentApplicationService.ensureApplicant(c,auth,email,location);
            assertEquals(id,EmploymentApplicationService.ensureApplicant(c,auth,email,location));
            assertThrows(IllegalArgumentException.class,()->EmploymentApplicationService.read(c,id,other,false));
            assertThrows(IllegalArgumentException.class,()->EmploymentApplicationService.save(c,id,other,0,EmploymentApplicationServiceTest.form(),false));
            var saved=EmploymentApplicationService.save(c,id,auth,0,EmploymentApplicationServiceTest.form(),false);assertEquals(1,saved.get("revision").getAsInt());
            assertThrows(IllegalArgumentException.class,()->EmploymentApplicationService.save(c,id,auth,0,EmploymentApplicationServiceTest.form(),false));
            var submitted=EmploymentApplicationService.save(c,id,auth,1,EmploymentApplicationServiceTest.form(),true);assertEquals("PENDING",submitted.get("status").getAsString());assertEquals(0,submitted.getAsJsonArray("attachments").size());
            assertEquals(2,EmploymentApplicationService.save(c,id,auth,1,EmploymentApplicationServiceTest.form(),true).get("revision").getAsInt());
            assertThrows(IllegalArgumentException.class,()->EmploymentApplicationService.save(c,id,auth,2,EmploymentApplicationServiceTest.form(),false));
            EmploymentApplicationService.transition(c,id,null,"INFORMATION_REQUESTED","Please add exam results.","Internal interview note",manager);
            assertFalse(EmploymentApplicationService.read(c,id,auth,false).toString().contains("Internal interview note"));assertTrue(EmploymentApplicationService.read(c,id,null,true).toString().contains("Internal interview note"));
            var objects=new ConcurrentHashMap<String,byte[]>();EmploymentPortalCloud cloud=new EmploymentPortalCloud(){@Override public void upload(String path,String type,byte[] bytes){objects.put(path,bytes.clone());}@Override public byte[] download(String path){return objects.get(path).clone();}};
            UUID file=UUID.randomUUID();EmploymentApplicationService.upload(c,id,auth,file,"exam.png","exam_results","Results",EmploymentApplicationServiceTest.image(),cloud);
            assertThrows(IllegalArgumentException.class,()->EmploymentApplicationService.download(c,id,other,file,false,cloud));
            assertEquals("image/png",EmploymentApplicationService.download(c,id,auth,file,false,cloud).type());
            EmploymentApplicationService.upload(c,id,auth,file,"exam.png","exam_results","Results",EmploymentApplicationServiceTest.image(),cloud);assertEquals(1,EmploymentApplicationService.attachments(c,id).size());
            for(int i=1;i<9;i++)EmploymentApplicationService.upload(c,id,auth,UUID.randomUUID(),"exam.png","exam_results","",EmploymentApplicationServiceTest.image(),cloud);
            ExecutorService pool=Executors.newFixedThreadPool(2);try{var start=new CountDownLatch(1);Callable<Boolean> task=()->{try(Connection conn=connect(url)){start.await();try{EmploymentApplicationService.upload(conn,id,auth,UUID.randomUUID(),"exam.png","exam_results","",EmploymentApplicationServiceTest.image(),cloud);return true;}catch(Exception expected){return false;}}};var a=pool.submit(task);var b=pool.submit(task);start.countDown();assertTrue(a.get()^b.get());}finally{pool.shutdownNow();}
            assertEquals(10,EmploymentApplicationService.attachments(c,id).size());
            assertThrows(IllegalArgumentException.class,()->EmploymentApplicationService.upload(c,id,auth,UUID.randomUUID(),"exam.png","exam_results","",EmploymentApplicationServiceTest.image(),cloud));
            EmploymentApplicationService.remove(c,id,auth,file);assertThrows(IllegalArgumentException.class,()->EmploymentApplicationService.download(c,id,auth,file,false,cloud));
            assertNotNull(EmploymentApplicationService.download(c,id,null,file,true,cloud));
            EmploymentApplicationService.transition(c,id,auth,"WITHDRAWN","","",null);assertThrows(IllegalArgumentException.class,()->EmploymentApplicationService.save(c,id,auth,4,EmploymentApplicationServiceTest.form(),true));
            EmploymentApplicationService.transition(c,id,null,"INFORMATION_REQUESTED","Please resubmit.","",manager);
            int revision=EmploymentApplicationService.read(c,id,auth,false).get("revision").getAsInt();
            EmploymentApplicationService.save(c,id,auth,revision,EmploymentApplicationServiceTest.form(),true);
            String role;try(var p=c.createStatement();var r=p.executeQuery("SELECT role_name FROM roles ORDER BY role_id LIMIT 1")){r.next();role=r.getString(1);}
            var employee=new LanEmployeeAdminService.SaveRequest("hire-"+suffix,null,"Alex","","Example","Alex Example","",email,"6001234",null,null,java.time.LocalDate.of(2000,1,1),java.time.LocalDate.now(),"HOURLY",java.math.BigDecimal.TEN,role,true,List.of(location),EmployeePayrollSettingsService.PeriodType.SEMI_MONTHLY,new java.math.BigDecimal("80"),true,null);
            var noCloud=new EmployeeRegistrationService.Cloud(){public String createBlocked(UUID i,String e,String p,String n){throw new AssertionError("Hiring must reuse Auth identity");}public String uploadId(UUID i,String t,byte[] b){throw new AssertionError("Exam results must not become ID documents");}public void activate(String a){throw new AssertionError("Portal hiring must not unban Auth identities");}};
            int hired=EmployeeRegistrationService.approve(c,id,employee,manager,"Manager",noCloud);
            assertEquals(hired,EmployeeRegistrationService.approve(c,id,employee,manager,"Manager",noCloud));
            try(var p=c.prepareStatement("SELECT auth_user_id,is_active,employee_id_card_document_url FROM users WHERE user_id=?")){p.setInt(1,hired);try(var r=p.executeQuery()){assertTrue(r.next());assertEquals(auth,r.getObject(1));assertTrue(r.getBoolean(2));assertNull(r.getString(3));}}
            assertEquals("APPROVED",EmploymentApplicationService.read(c,id,auth,false).get("status").getAsString());
        }
    }
}
