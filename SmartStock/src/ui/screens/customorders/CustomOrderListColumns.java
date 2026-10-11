package ui.screens.customorders;

import managers.SessionManager;
import services.LanCustomOrderWorkflowService.OrderRow;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Shared order-list headings and values prevent column offsets drifting between screens. */
final class CustomOrderListColumns {
    static String[] headings() {
        return new String[]{"ID","Order #","Status","Customer","Phone","Due","Total","Paid","Balance","Payment","Reference","Created"};
    }
    static Object[] values(OrderRow order, boolean includeStaff) {
        List<Object> values=new ArrayList<>(List.of(order.orderId(),order.orderNumber(),order.status(),
                order.customer(),order.phone(),order.dueDate()==null?"":order.dueDate(),
                order.pricePending()?"Price pending":CustomOrders.money(order.total()),CustomOrders.money(order.paid()),order.pricePending()?"Price pending":CustomOrders.money(order.balance()),
                order.paymentMethod()+(order.paymentStatus().isBlank()?"":" / "+order.paymentStatus()),order.paymentReference()));
        if(includeStaff){values.add(order.assignedTo());values.add(order.takenBy());}
        values.add(new Date(order.createdAtEpochMillis()));
        return values.toArray();
    }
    static void installSorting(JTable table, javax.swing.table.TableRowSorter<javax.swing.table.DefaultTableModel> sorter) {
        var model = sorter.getModel();
        for (String heading : List.of("ID", "Total", "Paid", "Balance")) {
            int column = model.findColumn(heading);
            sorter.setComparator(column, (a,b) -> {boolean left="Price pending".equals(a),right="Price pending".equals(b);return left||right?Boolean.compare(left,right):new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString()));});
        }
        sorter.setComparator(model.findColumn("Due"), (a,b) -> {
            LocalDate left = a instanceof LocalDate date ? date : LocalDate.MAX;
            LocalDate right = b instanceof LocalDate date ? date : LocalDate.MAX;
            return left.compareTo(right);
        });
        sorter.setComparator(model.findColumn("Created"), (a,b) -> Long.compare(
                a instanceof Date date ? date.getTime() : 0,
                b instanceof Date date ? date.getTime() : 0));
        sorter.setComparator(model.findColumn("Order #"), (a,b) -> compareOrderNumbers(a.toString(),b.toString()));
        sorter.setSortsOnUpdates(true);
        table.setRowSorter(sorter);
        table.getTableHeader().setToolTipText("Click a heading to sort. Click again to reverse the order.");
    }
    static int compareOrderNumbers(String left, String right) {
        var pattern = java.util.regex.Pattern.compile("[0-9]+|[^0-9]+");
        var a=pattern.matcher(left); var b=pattern.matcher(right);
        while (a.find()) {
            if (!b.find()) return 1;
            String x=a.group(), y=b.group();
            int comparison = Character.isDigit(x.charAt(0)) && Character.isDigit(y.charAt(0))
                    ? new java.math.BigInteger(x).compareTo(new java.math.BigInteger(y)) : x.compareToIgnoreCase(y);
            if (comparison != 0) return comparison;
        }
        return b.find() ? -1 : 0;
    }
    static void installDateRenderers(JTable table) {
        DefaultTableCellRenderer renderer=new DefaultTableCellRenderer() {
            @Override protected void setValue(Object value) {
                String timezone=SessionManager.getCurrentLocationTimezone();
                ZoneId zone=timezone==null||timezone.isBlank()?ZoneId.systemDefault():ZoneId.of(timezone);
                super.setValue(formatDate(value,zone));
            }
        };
        for(String heading:List.of("Due","Created")) {
            int modelColumn=((javax.swing.table.DefaultTableModel)table.getModel()).findColumn(heading);
            table.getColumnModel().getColumn(table.convertColumnIndexToView(modelColumn)).setCellRenderer(renderer);
        }
    }
    static String formatDate(Object value,ZoneId zone) {
        if(value instanceof Date date) return date.getTime()<=0?"Not set":DateTimeFormatter.ofPattern("d MMM uuuu, h:mm a",Locale.ENGLISH).format(date.toInstant().atZone(zone));
        if(value instanceof LocalDate date) return DateTimeFormatter.ofPattern("d MMM uuuu",Locale.ENGLISH).format(date);
        return value==null||value.toString().isBlank()?"Not set":value.toString();
    }
}
