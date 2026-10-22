package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StudioBadgeRecordsTest {
    @Test void disabledAndLegacyTemplatesRejectWritesBeforeDatabaseAccess() {
        for(String design:new String[]{"{}","{\"savePrintRecords\":false}"}) {
            JsonObject head=new JsonObject();head.addProperty("id",UUID.randomUUID().toString());head.addProperty("content",design);
            JsonObject request=new JsonObject();request.addProperty("action","RECORD_SAVE");
            assertThrows(IllegalArgumentException.class,()->StudioBadgeRecords.execute(null,1,2,head,request));
        }
    }
    @Test void everyHistoryReadRejectsNonAdminBeforeQueryingRecords()throws Exception {
        ResultSet rows=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(p,m,a)->m.getName().equals("next")?false:null);
        PreparedStatement statement=(PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->m.getName().equals("executeQuery")?rows:null);
        Connection connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(p,m,a)->{
            if(m.getName().equals("prepareStatement")){assertTrue(a[0].toString().contains("UPPER(r.role_name)='ADMIN'"));return statement;}return null;
        });
        JsonObject head=new JsonObject();head.addProperty("id",UUID.randomUUID().toString());
        for(String action:new String[]{"RECORD_LIST","RECORD_GET"}){
            JsonObject request=new JsonObject();request.addProperty("action",action);
            assertThrows(SecurityException.class,()->StudioBadgeRecords.execute(connection,1,2,head,request));
        }
    }
}
