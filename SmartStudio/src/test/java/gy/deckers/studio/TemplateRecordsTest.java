package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import publishing.*;
import java.util.*;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.*;

class TemplateRecordsTest {
    @Test void deletingFieldsPreservesOtherValuesAndPersistsWithoutResurrectingKeys()throws Exception{
        SwingUtilities.invokeAndWait(()->{
            try{
                var panel=new BusinessCardsPanel();
                panel.loadRows(List.of("number","notes","remove_me"),List.of(Map.of("number","501","notes","Keep","remove_me","Gone"),Map.of("number","502","notes","","remove_me","Gone too")));
                panel.deleteField("remove_me");
                var saved=PublicationFiles.read(PublicationFiles.archive(panel.output()));
                assertEquals(List.of("number","notes"),saved.mergeColumns);
                assertEquals(List.of(Map.of("number","501","notes","Keep"),Map.of("number","502","notes","")),saved.mergeRows);
                panel.loadRows(saved.mergeColumns,saved.mergeRows);assertEquals(saved.mergeColumns,panel.output().mergeColumns);
                panel.deleteField("number");panel.deleteField("notes");
                assertTrue(panel.output().mergeColumns.isEmpty());
                assertTrue(panel.output().mergeRows.stream().allMatch(Map::isEmpty));
                assertThrows(IllegalArgumentException.class,()->panel.deleteField("missing"));
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    @Test void emptySchemaAndNumberRunsSurviveTemplateRoundTrips() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                var panel = new BusinessCardsPanel();
                assertTrue(panel.output().mergeColumns.isEmpty());
                assertTrue(panel.output().mergeRows.isEmpty());
                panel.loadRows(List.of("ticket", "notes"), List.of());
                var headers = PublicationFiles.read(PublicationFiles.archive(panel.output()));
                assertEquals(List.of("ticket", "notes"), headers.mergeColumns);
                for (int start : List.of(1, 501)) {
                    var rows = new ArrayList<Map<String,String>>();
                    for (String number : BusinessCardSeries.paddedValues("" + start, "1", 500, 4))
                        rows.add(Map.of("ticket", number, "notes", ""));
                    panel.loadRows(headers.mergeColumns, rows);
                    var saved = PublicationTemplates.clean(panel.output());
                    var restored = PublicationFiles.read(PublicationFiles.archive(saved));
                    var reopened = new BusinessCardsPanel();
                    reopened.loadRows(restored.mergeColumns, restored.mergeRows);
                    assertEquals(rows, reopened.output().mergeRows);
                    assertEquals(String.format("%04d",start + 499), reopened.output().mergeRows.get(499).get("ticket"));
                }
                panel.loadRows(List.of("custom"), List.of(Map.of("custom", "")));
                assertEquals(List.of(Map.of("custom", "")), panel.output().mergeRows);
                panel.loadRows(List.of(), List.of());
                assertTrue(panel.output().mergeColumns.isEmpty());
                assertTrue(panel.output().mergeRows.isEmpty());
                // Older archives have row keys but no explicit schema.
                panel.loadRows(List.of(), List.of(Map.of("legacy", "42")));
                assertEquals(List.of("legacy"), panel.output().mergeColumns);
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
    }
}
