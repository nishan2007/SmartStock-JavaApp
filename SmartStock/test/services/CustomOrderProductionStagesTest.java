package services;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomOrderProductionStagesTest {
    @Test void everyDisplayedStageRoundTripsToItsExistingStoredCode() {
        assertEquals(List.of("NOT_STARTED","STARTED","WAITING_FOR_APPROVAL","DESIGN_APPROVED","PRINTED","FINISHED","QUALITY_CHECKED","READY"),CustomOrderProductionStages.CODES);
        for(String code:CustomOrderProductionStages.CODES)
            assertEquals(code,CustomOrderProductionStages.code(CustomOrderProductionStages.label(code)));
        assertEquals("In progress",CustomOrderProductionStages.label("STARTED"));
        assertEquals("STARTED",LanCustomOrderWorkflowService.productionStatus("In progress"));
        assertEquals("STARTED",LanCustomOrderWorkflowService.productionStatus("Started"));
    }
    @Test void dashboardSummaryPreservesMixedStagesInsteadOfPretendingAllLinesMatch() {
        assertEquals("MIXED",CustomOrderProductionStages.summarize("READY,STARTED"));
        assertEquals("STARTED",CustomOrderProductionStages.summarize("STARTED,STARTED"));
        assertEquals("NOT_STARTED",CustomOrderProductionStages.summarize(""));
    }
}
