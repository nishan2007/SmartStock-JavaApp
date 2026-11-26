package gy.deckers.studio;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StudioRecordBrowserTest {
    @Test void showsAllFieldsAndLoadsTheSelectedIdentityAfterSortingAndFiltering() throws Exception {
        var browserRef=new AtomicReference<StudioRecordBrowser<String>>();
        var ready=new LinkedBlockingQueue<Boolean>();
        var loaded=new AtomicReference<String>();
        SwingUtilities.invokeAndWait(()->{
            var columns=new LinkedHashMap<String,String>();
            columns.put("name","Name");
            columns.put("dateOfBirth","Date of birth");
            columns.put("permitNumber","Permit number");
            var browser=new StudioRecordBrowser<String>("Permit",columns,record->
                    new StudioRecordBrowser.Details(null,()->loaded.set(record.id())),()->{});
            browserRef.set(browser);
            browser.open.addPropertyChangeListener("enabled",e->{if(browser.open.isEnabled())ready.add(true);});
            browser.load(List.of(
                    new StudioRecordBrowser.Row<>("permit-a",Map.of("name","Alex Rider","dateOfBirth","8 FEB 2002","village","Siparuta","permitNumber","OSV-10000")),
                    new StudioRecordBrowser.Row<>("permit-b",Map.of("name","Robin Example","dateOfBirth","23 NOV 1999","village","Orealla","permitNumber","OSV-9999","legacyField","Older value"))));
            assertEquals(5,browser.table.getColumnCount());
            assertEquals("OSV-10000",browser.table.getValueAt(0,2));
            browser.table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(1,SortOrder.ASCENDING)));
            assertEquals("Robin Example",browser.table.getValueAt(0,0));
            assertEquals("Alex Rider",browser.table.getValueAt(browser.table.getSelectedRow(),0));
            browser.search.setText("older orealla");
            assertEquals(1,browser.table.getRowCount());
            browser.table.setRowSelectionInterval(0,0);
        });
        assertNotNull(ready.poll(5,TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(()->{
            var browser=browserRef.get();
            browser.open.doClick(0);
            assertEquals("permit-b",loaded.get());
            browser.search.setText(".*");
            assertEquals(0,browser.table.getRowCount());
            assertFalse(browser.open.isEnabled());
            browser.close();
        });
    }
}
