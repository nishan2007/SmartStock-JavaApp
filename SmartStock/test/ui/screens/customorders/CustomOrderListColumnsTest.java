package ui.screens.customorders;
import org.junit.jupiter.api.Test;
import services.LanCustomOrderWorkflowService.OrderRow;
import javax.swing.table.DefaultTableModel;
import java.math.BigDecimal;
import java.time.*;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;
class CustomOrderListColumnsTest {
 @Test void everyHeadingMatchesItsValueIncludingCreatedAndFinancialFields() {
  Instant created=Instant.parse("2026-10-05T20:49:00Z");
  var order=new OrderRow(19,"CO-5002","NEW","Venita","6510368",LocalDate.of(2026,10,8),new BigDecimal("4600"),new BigDecimal("3000"),new BigDecimal("1600"),"CASH","REF-123","PARTIAL","Employee","Cashier",created.toEpochMilli());
  var columns=CustomOrderListColumns.headings();var values=CustomOrderListColumns.values(order,false);
  assertEquals(columns.length,values.length);
  var model=new DefaultTableModel(columns,0);model.addRow(values);
  Object[] expected={19L,"CO-5002","NEW","Venita","6510368",LocalDate.of(2026,10,8),"4600","3000","1600","CASH / PARTIAL","REF-123",Date.from(created)};
  for(int i=0;i<columns.length;i++)assertEquals(expected[i],model.getValueAt(0,model.findColumn(columns[i])),columns[i]);
  assertEquals("5 Oct 2026, 4:49 PM",CustomOrderListColumns.formatDate(model.getValueAt(0,model.findColumn("Created")),ZoneId.of("America/Guyana")));
  assertEquals("8 Oct 2026",CustomOrderListColumns.formatDate(order.dueDate(),ZoneId.of("America/Guyana")));
 }
 @Test void missingDatesAreReadable() {
  assertEquals("Not set",CustomOrderListColumns.formatDate("",ZoneId.of("America/Guyana")));
  assertEquals("Not set",CustomOrderListColumns.formatDate(new Date(0),ZoneId.of("America/Guyana")));
 }
 @Test void headerSortingUsesNumbersAndDatesAndKeepsSelectionOnTheSameOrder() throws Exception {
  javax.swing.SwingUtilities.invokeAndWait(() -> {
   var model=new DefaultTableModel(CustomOrderListColumns.headings(),0);
   model.addRow(new Object[]{10L,"CO-10","NEW","Zoe","",LocalDate.of(2026,10,2),"100","20","80","CASH","",new Date(2000)});
   model.addRow(new Object[]{2L,"CO-2","NEW","Amy","",LocalDate.of(2026,9,30),"9","1","8","CASH","",new Date(1000)});
   model.addRow(new Object[]{100L,"CO-100","NEW","Max","","","2000","500","1500","CASH","",new Date(3000)});
   var table=new javax.swing.JTable(model);
   var sorter=new javax.swing.table.TableRowSorter<DefaultTableModel>(model);
   CustomOrderListColumns.installSorting(table,sorter);
   for(String heading: new String[]{"ID","Order #","Due","Total","Paid","Balance","Created"}) {
    sorter.setSortKeys(java.util.List.of(new javax.swing.RowSorter.SortKey(model.findColumn(heading),javax.swing.SortOrder.ASCENDING)));
    assertEquals(2L,table.getValueAt(0,0),heading);
    sorter.toggleSortOrder(model.findColumn(heading));
    assertEquals(100L,table.getValueAt(0,0),heading);
   }
   table.setRowSelectionInterval(table.convertRowIndexToView(0),table.convertRowIndexToView(0));
   sorter.toggleSortOrder(model.findColumn("Customer"));
   assertEquals(10L,model.getValueAt(table.convertRowIndexToModel(table.getSelectedRow()),0));
   sorter.setRowFilter(javax.swing.RowFilter.regexFilter("Amy",model.findColumn("Customer")));
   assertEquals(1,table.getRowCount());
   assertEquals(2L,table.getValueAt(0,0));
  });
 }
}
