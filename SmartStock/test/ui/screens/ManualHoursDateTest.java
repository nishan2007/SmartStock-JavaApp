package ui.screens;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import static org.junit.jupiter.api.Assertions.*;

class ManualHoursDateTest {
    @Test void acceptsExplicitDatesAndOptionalBlankValues() {
        assertNull(PayrollDashboard.parseManualHoursTime(" "));
        assertEquals(LocalDateTime.of(2026,9,30,8,0),PayrollDashboard.parseManualHoursTime(" 2026-09-30 08:00 "));
    }
    @Test void rejectsImpossibleDatesAndWrongFormats() {
        for(String value:new String[]{"2026-02-30 08:00","2026-09-30 24:00","09/30/2026 08:00","08:00"})
            assertThrows(DateTimeParseException.class,()->PayrollDashboard.parseManualHoursTime(value));
    }
}
