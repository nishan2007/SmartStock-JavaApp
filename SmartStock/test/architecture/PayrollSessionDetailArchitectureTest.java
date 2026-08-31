package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PayrollSessionDetailArchitectureTest {
    @Test void summaryDoubleClickShowsEveryTimestampForTheEmployeePeriod()throws Exception{
        String source=Files.readString(Path.of("src/ui/screens/PayrollDashboard.java"));
        assertTrue(source.contains("e.getClickCount()==2"));
        assertTrue(source.contains("showSelectedEmployeeSessions()"));
        assertTrue(source.contains("row.userId()!=summary.userId()"));
        assertTrue(source.contains("row.payPeriodStart().equals(summary.payPeriodStart())"));
        for(String column:new String[]{"Clock In","Lunch Start","Lunch End","Break Start","Break End","Clock Out"})assertTrue(source.contains("\""+column+"\""));
    }
}
