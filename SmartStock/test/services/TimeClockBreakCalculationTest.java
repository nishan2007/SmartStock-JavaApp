package services;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeClockBreakCalculationTest {
    private static final Instant CLOCK_IN = Instant.parse("2026-08-08T13:00:00Z");
    private static final Instant CLOCK_OUT = Instant.parse("2026-08-08T21:00:00Z");

    @Test
    void deductsTheEntireRecordedBreakIncludingTimeBeyondTheAllowance() {
        assertHours("7.92", 5);
        assertHours("7.83", 10);
        assertHours("7.75", 15);
    }

    @Test
    void automaticBreakEndAtFifteenMinutesLeavesSevenPointSevenFivePaidHours() {
        Instant breakStart = CLOCK_IN.plusSeconds(3 * 60 * 60);
        Instant automaticEnd = breakStart.plusSeconds(15 * 60);

        assertEquals(new BigDecimal("7.75"), TimeClockAutoCloseService.workedHours(
                CLOCK_IN, null, null, breakStart, automaticEnd, CLOCK_OUT));
    }

    private static void assertHours(String expected, int breakMinutes) {
        Instant breakStart = CLOCK_IN.plusSeconds(3 * 60 * 60);
        assertEquals(new BigDecimal(expected), TimeClockAutoCloseService.workedHours(
                CLOCK_IN, null, null, breakStart, breakStart.plusSeconds(breakMinutes * 60L), CLOCK_OUT));
    }
}
