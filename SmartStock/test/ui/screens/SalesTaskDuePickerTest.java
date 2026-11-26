package ui.screens;

import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.time.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SalesTaskDuePickerTest {
    private final ZoneId zone=ZoneId.of("America/Guyana");
    private final Clock clock=Clock.fixed(Instant.parse("2026-10-25T16:30:00Z"),ZoneOffset.UTC);
    @Test void optionalDeadlineDoesNotCreateAnUnintendedDueTime()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var picker=new SalesTaskDuePicker(zone,null,clock);
            assertEquals("",picker.dueAt());assertFalse(picker.date.isEditable());
            picker.enabled.doClick();picker.hour.setSelectedItem(12);picker.minute.setSelectedItem("00");picker.period.setSelectedItem("AM");
            assertEquals(OffsetDateTime.parse("2026-10-25T00:00-04:00"),OffsetDateTime.parse(picker.dueAt()));
            picker.period.setSelectedItem("PM");
            assertEquals(OffsetDateTime.parse("2026-10-25T12:00-04:00"),OffsetDateTime.parse(picker.dueAt()));
            picker.enabled.doClick();assertEquals("",picker.dueAt());
        });
    }
    @Test void editingPreservesTheStoreLocalDeadlineAndClearingUnsetsIt()throws Exception {
        AtomicReference<SalesTaskDuePicker> ref=new AtomicReference<>();
        SwingUtilities.invokeAndWait(()->{
            var value=OffsetDateTime.parse("2026-11-05T21:42Z");
            var picker=new SalesTaskDuePicker(zone,value,clock);ref.set(picker);
            assertEquals(LocalDate.of(2026,11,5),picker.date.getSelectedDate());
            assertEquals(5,picker.hour.getSelectedItem());assertEquals("42",picker.minute.getSelectedItem());assertEquals("PM",picker.period.getSelectedItem());
            assertEquals(value.toInstant(),OffsetDateTime.parse(picker.dueAt()).toInstant());
            picker.date.clearDate();
        });
        SwingUtilities.invokeAndWait(()->assertEquals("",ref.get().dueAt()));
    }
    @Test void asapAndScheduledDeadlineAreMutuallyExclusive()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var picker=new SalesTaskDuePicker(zone,OffsetDateTime.parse("2026-11-05T21:42Z"),clock);
            picker.asap.doClick();assertTrue(picker.dueAsap());assertFalse(picker.enabled.isSelected());
            assertFalse(picker.hour.isEnabled());assertEquals("",picker.dueAt());
            picker.enabled.doClick();assertFalse(picker.dueAsap());assertTrue(picker.hour.isEnabled());
            assertFalse(picker.dueAt().isEmpty());
            picker.setAsap(true);assertTrue(picker.dueAsap());assertEquals("",picker.dueAt());
        });
    }
}
