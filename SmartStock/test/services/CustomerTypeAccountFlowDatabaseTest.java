package services;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named="smartstock.test.customerTypeFlowDatabaseUrl",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/customer_type_fresh_setup_test")
class CustomerTypeAccountFlowDatabaseTest {
    @Test void savesReadsAndChangesClassificationUsingOnlyCustomerType() throws Exception {
        try(Connection c=DriverManager.getConnection(System.getProperty("smartstock.test.customerTypeFlowDatabaseUrl"),"postgres",""); Statement s=c.createStatement()) {
            c.setAutoCommit(false);
            try {
                int user;
                try(ResultSet rs=s.executeQuery("SELECT user_id FROM users WHERE role_id=(SELECT role_id FROM roles WHERE role_name='ADMIN') LIMIT 1")){assertTrue(rs.next());user=rs.getInt(1);}
                int business,individual;
                try(ResultSet rs=s.executeQuery("SELECT customer_type_id FROM customer_types WHERE name='Business' LIMIT 1")){assertTrue(rs.next());business=rs.getInt(1);}
                try(ResultSet rs=s.executeQuery("SELECT customer_type_id FROM customer_types WHERE LOWER(name)='individual' LIMIT 1")){assertTrue(rs.next());individual=rs.getInt(1);}
                var body=LanJson.create().fromJson("{name:'Disposable business fixture',active:true,creditLimit:20000,business:false}",com.google.gson.JsonObject.class);
                body.addProperty("customerTypeId",business);
                var saved=LanCustomerAccountService.save(c,body,null,user);
                int id=((Number)saved.get("customerId")).intValue();
                String number=saved.get("accountNumber").toString();
                assertTrue(number.startsWith("BA-"));
                assertEquals(true,LanCustomerAccountService.details(c,id,user).get("business"));
                body.addProperty("customerId",id);body.addProperty("accountNumber",number);
                body.addProperty("customerTypeId",individual);body.addProperty("business",true);
                LanCustomerAccountService.save(c,body,null,user);
                var details=LanCustomerAccountService.details(c,id,user);
                assertEquals(false,details.get("business"));assertEquals(number,details.get("accountNumber"));
                assertEquals(individual,((Number)details.get("customerTypeId")).intValue());
                assertTrue(LanCustomerAccountService.list(c,user).stream().anyMatch(row -> ((Number)row.get("customerId")).intValue()==id && Boolean.FALSE.equals(row.get("business"))));
            } finally { c.rollback(); }
        }
    }
}
