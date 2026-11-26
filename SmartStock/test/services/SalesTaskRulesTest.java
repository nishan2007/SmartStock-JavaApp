package services;

import java.math.BigDecimal;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SalesTaskRulesTest {
    @Test void amountsFitTheExistingSaleAndReceiptPrecision()throws Exception {
        assertEquals(new BigDecimal("12.00"),SalesTaskService.money(new BigDecimal("12")));
        assertThrows(SQLException.class,()->SalesTaskService.money(new BigDecimal("1.001")));
        assertThrows(SQLException.class,()->SalesTaskService.money(new BigDecimal("10000000000")));
        assertThrows(SQLException.class,()->SalesTaskService.money(null));
    }
    @Test void workTransitionsAndPhysicalStorePolicyAreEnforced()throws Exception {
        SalesTaskService.transition("COMPLETED","COMPLETED");
        assertThrows(SQLException.class,()->SalesTaskService.transition("RECEIVED","COMPLETED"));
        assertTrue(RemoteAdminPolicy.isMutation("/v1/sales-tasks/update"));
        assertTrue(RemoteAdminPolicy.isPhysicalOperation("/v1/sales-tasks/update"));
        assertFalse(RemoteAdminPolicy.isMutation("/v1/sales-tasks/read"));
        assertTrue(CloudRowMirrorService.scopePredicate("sales_task_files",false).contains("p.location_id=?"));
        assertTrue(CloudRowMirrorService.scopePredicate("sales_task_events",false).contains("p.location_id=?"));
    }
}
