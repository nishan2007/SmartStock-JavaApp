package services;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CustomOrderProductionStatusTest {
    @Test void finishedLinesCompleteOrder() {
        assertEquals("COMPLETED", LanCustomOrderWorkflowService.aggregateProductionStatus("IN_PROGRESS", List.of("FINISHED","FINISHED")));
        assertEquals("COMPLETED", LanCustomOrderWorkflowService.aggregateProductionStatus("NEW", List.of("QUALITY_CHECKED","READY")));
    }
    @Test void unfinishedLineKeepsOrderInProgress() {
        assertEquals("IN_PROGRESS", LanCustomOrderWorkflowService.aggregateProductionStatus("NEW", List.of("FINISHED","NOT_STARTED")));
        assertEquals("IN_PROGRESS", LanCustomOrderWorkflowService.aggregateProductionStatus("COMPLETED", List.of("FINISHED","PRINTED")));
    }
    @Test void allReadyAndTerminalOrders() {
        assertEquals("READY", LanCustomOrderWorkflowService.aggregateProductionStatus("IN_PROGRESS", List.of("READY","READY")));
        for(String status:List.of("CANCELLED","DELIVERED"))
            assertEquals(status, LanCustomOrderWorkflowService.aggregateProductionStatus(status, List.of("FINISHED")));
        assertEquals("ASSIGNED", LanCustomOrderWorkflowService.aggregateProductionStatus("ASSIGNED", List.of()));
    }
}
