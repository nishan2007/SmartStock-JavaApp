package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import services.EmployeeScheduleService.Assignment;
import services.EmployeeScheduleService.Holiday;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScheduleExportServiceTest {
    @TempDir Path temp;
    @Test void exportsWeeklyRosterAndCompleteHalfMonth() throws Exception {
        LocalDate start = LocalDate.of(2026,11,16), end = start.withDayOfMonth(30);
        Map<LocalDate,List<Assignment>> assignments = new LinkedHashMap<>();
        for (LocalDate d=start; !d.isAfter(end); d=d.plusDays(1)) {
            if (d.getDayOfWeek()==DayOfWeek.SUNDAY) continue;
            assignments.put(d,List.of(entry(6,"Alicia",d,8),entry(7,"Brian",d,9),entry(8,"Priya",d,8)));
        }
        // Manual Sunday assignments must survive export; off-period rows must not leak.
        assignments.put(start.plusDays(6),List.of(entry(6,"Alicia",start.plusDays(6),9)));
        assignments.put(start.minusDays(1),List.of(entry(99,"Outside selected period",start.minusDays(1),8)));
        Map<LocalDate,Holiday> holidays=Map.of(start.plusDays(7),new Holiday(UUID.randomUUID(),start.plusDays(7),"Special opening hours"));
        for (boolean weekly : new boolean[]{true,false}) {
            String name=weekly?"weekly":"semi-monthly";
            LocalDate until=weekly?start.plusDays(6):end;
            Path png=temp.resolve(name+".png"),pdf=temp.resolve(name+".pdf");
            ScheduleExportService.writePng(png.toFile(),"Main Store",start,until,weekly?7:8,false,assignments,holidays);
            ScheduleExportService.writePdf(pdf.toFile(),"Main Store",start,until,weekly?7:8,false,assignments,holidays);
            assertTrue(ImageIO.read(png.toFile()).getWidth()>2000);
            try(PDDocument document=PDDocument.load(pdf.toFile())) {
                assertEquals(1,document.getNumberOfPages());
                assertTrue(new PDFRenderer(document).renderImage(0).getWidth()>700);
            }
            String review=System.getProperty("scheduleExport.reviewDirectory");
            if(review!=null) {
                Files.createDirectories(Path.of(review));
                Files.copy(png,Path.of(review,name+".png"),StandardCopyOption.REPLACE_EXISTING);
                Files.copy(pdf,Path.of(review,name+".pdf"),StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
    @Test void handlesEmptyPeriodsAndLongNamesWithMissingTimes() throws Exception {
        LocalDate d=LocalDate.of(2026,11,23);
        for(int columns:new int[]{7,8}) {
            Path output=temp.resolve("empty-"+columns+".png");
            ScheduleExportService.writePng(output.toFile(),"Store",d,d.plusDays(columns==7?6:14),columns,true,Map.of(),Map.of());
            assertNotNull(ImageIO.read(output.toFile()));
            Assignment a=new Assignment(1,"An employee with a very long display name that must wrap completely","",d,null,null,null,null,null);
            ScheduleExportService.writePng(output.toFile(),"Store",d,d.plusDays(columns==7?6:14),columns,true,Map.of(d,List.of(a)),Map.of());
            assertNotNull(ImageIO.read(output.toFile()));
        }
    }
    private static Assignment entry(int id,String name,LocalDate d,int hour) {
        return new Assignment(id,name,name.toLowerCase(),d,LocalTime.of(hour==8?12:13,0),UUID.randomUUID(),"Day",LocalTime.of(hour,0),LocalTime.of(hour+8,0));
    }
}
