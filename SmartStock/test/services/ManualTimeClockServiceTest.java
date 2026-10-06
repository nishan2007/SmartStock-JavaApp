package services;

import org.junit.jupiter.api.Test;
import java.sql.SQLException;
import java.time.*;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class ManualTimeClockServiceTest {
    private final LocalDateTime in=LocalDateTime.of(2026,9,30,8,0);
    private TimeClockAutoCloseService.Correction entry(LocalDateTime ls,LocalDateTime le,LocalDateTime bs,LocalDateTime be) {
        return new TimeClockAutoCloseService.Correction(in,ls,le,bs,be,in.plusHours(9),"Away from store");
    }
    @Test void requiresCompletedPositiveSessionAndReason() {
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(null));
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(new TimeClockAutoCloseService.Correction(null,null,null,null,null,in,"Reason")));
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(new TimeClockAutoCloseService.Correction(in,null,null,null,null,null,"Reason")));
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(new TimeClockAutoCloseService.Correction(in,null,null,null,null,in,"Reason")));
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(new TimeClockAutoCloseService.Correction(in,null,null,null,null,in.minusHours(1),"Reason")));
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(new TimeClockAutoCloseService.Correction(in,null,null,null,null,in.plusHours(1)," ")));
    }
    @Test void validatesPairedIntervalsInsideShiftAndWithoutOverlap() {
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(entry(in.plusHours(4),null,null,null)));
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(entry(null,in.plusHours(4),null,null)));
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(entry(null,null,in.plusHours(2),null)));
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(entry(null,null,null,in.plusHours(2))));
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(entry(in.minusMinutes(1),in.plusHours(1),null,null)));
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(entry(null,null,in.plusHours(3),in.plusHours(2))));
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(entry(null,null,in.plusHours(8),in.plusHours(10))));
        assertThrows(SQLException.class,()->ManualTimeClockService.validate(entry(in.plusHours(4),in.plusHours(5),in.plusHours(4).plusMinutes(30),in.plusHours(6))));
        assertDoesNotThrow(()->ManualTimeClockService.validate(entry(in.plusHours(4),in.plusHours(5),in.plusHours(5),in.plusHours(6))));
    }
    @Test void overnightHoursSubtractLunchAndBreak() throws Exception {
        LocalDateTime start=in.withHour(22),end=start.plusHours(9);
        var v=new TimeClockAutoCloseService.Correction(start,start.plusHours(3),start.plusHours(4),start.plusHours(5),start.plusHours(5).plusMinutes(15),end,"Remote shift");
        ManualTimeClockService.validate(v);
        ZoneId zone=ZoneId.of("America/Guyana");
        assertEquals(new BigDecimal("7.75"),TimeClockAutoCloseService.workedHours(
                ManualTimeClockService.timestamp(v.clockIn(),zone),ManualTimeClockService.timestamp(v.lunchStart(),zone),
                ManualTimeClockService.timestamp(v.lunchEnd(),zone),ManualTimeClockService.timestamp(v.breakStart(),zone),
                ManualTimeClockService.timestamp(v.breakEnd(),zone),ManualTimeClockService.timestamp(v.clockOut(),zone)));
    }
    @Test void rejectsInvalidAndAmbiguousStoreLocalTimes() {
        ZoneId zone=ZoneId.of("America/New_York");
        assertThrows(SQLException.class,()->ManualTimeClockService.timestamp(LocalDateTime.of(2026,3,8,2,30),zone));
        assertThrows(SQLException.class,()->ManualTimeClockService.timestamp(LocalDateTime.of(2026,11,1,1,30),zone));
    }
}
