package services;

import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CustomOrderSettledDeliveryTest {
    @Test void reconcilesOnlyFullyDeliveredSettledOrdersAndAuditsOnce() throws Exception {
        String url=System.getenv("SMARTSTOCK_DELIVERY_TEST_JDBC");
        assumeTrue(url!=null);
        try(var c=DriverManager.getConnection(url,System.getenv("SMARTSTOCK_DELIVERY_TEST_USER"),System.getenv("SMARTSTOCK_DELIVERY_TEST_PASSWORD"))) {
            c.setAutoCommit(false);
            try(var s=c.createStatement()) {
                s.execute("CREATE TEMP TABLE custom_orders(custom_order_id bigint,location_id int,status text,balance_due numeric,pricing_state text,delivered_at timestamptz,updated_at timestamptz)");
                s.execute("CREATE TEMP TABLE custom_order_lines(custom_order_id bigint,delivery_status text,return_status text,delivered_at timestamptz)");
                s.execute("CREATE TEMP TABLE custom_order_status_history(custom_order_id bigint,old_status text,new_status text,reason text,user_id int,user_name text,device_id text,device_name text)");
                s.execute("CREATE TEMP TABLE custom_order_audit_log(custom_order_id bigint,action_type text,field_name text,old_value text,new_value text,reason text,user_id int,user_name text,device_id text,device_name text)");
                s.execute("INSERT INTO custom_orders VALUES(1,1,'COMPLETED',0,'FINAL',NULL,NULL),(2,1,'COMPLETED',10,'FINAL',NULL,NULL),(3,1,'COMPLETED',0,'FINAL',NULL,NULL),(4,1,'COMPLETED',0,'PENDING',NULL,NULL),(5,1,'CANCELLED',0,'FINAL',NULL,NULL),(6,1,'COMPLETED',0,'FINAL',NULL,NULL),(7,1,'NEW',0,'FINAL',NULL,NULL)");
                s.execute("INSERT INTO custom_order_lines SELECT n,'DELIVERED','NONE','2026-10-10 15:00:00-04'::timestamptz FROM generate_series(1,6) n");
                s.execute("INSERT INTO custom_order_lines VALUES(3,'PENDING','NONE',NULL),(6,'PENDING','FULL',NULL)");
                for(int id=1;id<=7;id++)LanCustomOrderWorkflowService.reconcileSettledDelivery(c,id,1,1,"Operator","test-device","Test");
                LanCustomOrderWorkflowService.reconcileSettledDelivery(c,1,1,1,"Operator","test-device","Test");
                try(var r=s.executeQuery("SELECT custom_order_id,status FROM custom_orders ORDER BY custom_order_id")) {
                    for(String expected:new String[]{"DELIVERED","COMPLETED","COMPLETED","COMPLETED","CANCELLED","DELIVERED","NEW"}) {
                        assertTrue(r.next());assertEquals(expected,r.getString(2));
                    }
                }
                try(var r=s.executeQuery("SELECT COUNT(*) FROM custom_order_audit_log")){r.next();assertEquals(2,r.getInt(1));}
                try(var r=s.executeQuery("SELECT COUNT(*) FROM custom_order_status_history")){r.next();assertEquals(2,r.getInt(1));}
                try(var r=s.executeQuery("SELECT delivered_at='2026-10-10 15:00:00-04'::timestamptz FROM custom_orders WHERE custom_order_id=1")){r.next();assertTrue(r.getBoolean(1));}
                s.execute("ALTER TABLE custom_orders ADD COLUMN assigned_to_name text");
                var blocked=assertThrows(SQLException.class,()->LanOrdersDashboardService.assign(c,1,null,"COMPLETED",1,"Operator",java.util.UUID.randomUUID(),"Test",1));
                assertTrue(blocked.getMessage().contains("Delivered orders cannot be reopened"));
                assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.reconcileSettledDelivery(c,1,2,1,"Operator","test-device","Test"));
            } finally {c.rollback();}
        }
    }
}
