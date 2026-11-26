package services;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.*;
import java.time.LocalDate;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class CustomOrderDueDateTest {
  @Test void rejectsMissingAndInvalidDates()throws Exception{
    for(String date:new String[]{null,"","2026-02-30","03/11/2026"})
      assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.parseDueDate(date));
    assertEquals(LocalDate.of(2028,2,29),LanCustomOrderWorkflowService.parseDueDate("2028-02-29"));
  }
  @Test void unavailableOrPreviouslyDatedOrderCannotWriteAudit()throws Exception{
    var statement=(PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)->m.getName().equals("executeUpdate")?0:null);
    var connection=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(p,m,a)->{
      if(m.getName().equals("prepareStatement")){
        String sql=(String)a[0];assertTrue(sql.startsWith("UPDATE custom_orders"));
        assertTrue(sql.contains("due_date IS NULL"));assertTrue(sql.contains("location_id=?"));assertTrue(sql.contains("'CANCELLED','DELIVERED'"));
        return statement;
      }return null;
    });
    assertThrows(SQLException.class,()->LanCustomOrderWorkflowService.setMissingDueDate(connection,42,"2026-11-03",1,7,"Employee","device","register"));
  }
}
