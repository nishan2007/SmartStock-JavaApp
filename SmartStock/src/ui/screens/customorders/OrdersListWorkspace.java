package ui.screens.customorders;

import static ui.screens.customorders.OrdersDesign.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import ui.design.DeckersPalette;

/** Filtered, paginated view of the complete store-scoped order list. */
final class OrdersListWorkspace extends JPanel {
  final JButton newOrder = button("New Order", true);
  private final JTable table;
  private final DefaultTableModel model;
  private final TableRowSorter<DefaultTableModel> sorter;
  private final JComboBox<String> status =
      new JComboBox<>(
          new String[] {
            "Current orders",
            "All statuses",
            "NEW",
            "ASSIGNED",
            "IN_PROGRESS",
            "WAITING_FOR_APPROVAL",
            "READY",
            "COMPLETED",
            "DELIVERED",
            "CANCELLED"
          });
  private final JComboBox<String> production = new JComboBox<>(java.util.stream.Stream.concat(java.util.stream.Stream.of("All production"), services.CustomOrderProductionStages.CODES.stream()).toArray(String[]::new));
  private final JComboBox<String> staff = new JComboBox<>(new String[] {"Assigned to: All staff"});
  private final JComboBox<String> payment =
      new JComboBox<>(
          new String[] {
            "All payment", "Unpaid", "Part paid", "Paid", "Price pending", "Billed on invoice"
          });
  private final JComboBox<String> delivery =
      new JComboBox<>(
          new String[] {"All delivery", "PENDING", "CALLED", "CALLED_NO_ANSWER", "DELIVERED"});
  private final JComboBox<Integer> pageSize = new JComboBox<>(new Integer[] {25, 50, 100});
  private final JLabel count = label("0 orders", 13, false), pageLabel = label("1 / 1", 13, false);
  private final JButton previous = button("‹", false), next = button("›", false);
  private final JToggleButton all = new JToggleButton("All orders"),
      mine = new JToggleButton("My orders"),
      unassigned = new JToggleButton("Unassigned");
  private final java.util.Set<Integer> matching = new java.util.HashSet<>();
  private int page;
  private boolean filtering;
  private boolean refreshPending;

  @SuppressWarnings("unchecked")
  OrdersListWorkspace(JTable table, DefaultTableModel model, JTextField search) {
    super(new BorderLayout(0, 12));
    this.table = table;
    this.model = model;
    this.sorter = (TableRowSorter<DefaultTableModel>) table.getRowSorter();
    putClientProperty("SmartStock.preserveThemeColors", true);
    setBackground(background());
    setBorder(new EmptyBorder(18, 18, 14, 12));
    setMinimumSize(new Dimension(660, 300));
    setPreferredSize(new Dimension(760, 700));
    OrdersIcons.install(newOrder, "plus", true);
    newOrder.setFont(font(Font.BOLD, 18));
    JPanel top = panel(new BorderLayout(0, 16));
    JPanel searchRow = panel(new BorderLayout(10, 0));
    newOrder.setPreferredSize(new Dimension(197, 46));
    searchRow.add(newOrder, BorderLayout.WEST);
    field(search);
    search.setToolTipText("Search order #, customer or phone");
    search.putClientProperty("JTextField.placeholderText", "Search order #, customer or phone…");
    searchRow.add(search);
    top.add(searchRow, BorderLayout.NORTH);
    JPanel filters = panel(new BorderLayout(0, 18));
    JPanel scopes = panel(new GridLayout(1, 3));
    ButtonGroup group = new ButtonGroup();
    for (JToggleButton b : List.of(all, mine, unassigned)) {
      group.add(b);
      b.setFont(font(Font.PLAIN, 14));
      b.setForeground(ink());
      b.setBackground(background());
      b.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(line()), new EmptyBorder(12, 3, 12, 3)));
      b.addActionListener(
          e -> {
            page = 0;
            filter();
          });
      scopes.add(b);
    }
    all.setSelected(true);
    filters.add(scopes, BorderLayout.NORTH);
    JPanel filterRows = panel(new GridLayout(2, 1, 0, 8));
    JPanel row1 = panel(new GridLayout(1, 3, 8, 0)), row2 = panel(new GridLayout(1, 3, 8, 0));
    for (JComboBox<String> b : List.of(status, production, staff, payment, delivery)) {
      field(b);
      b.setRenderer(
          new DefaultListCellRenderer() {
            public Component getListCellRendererComponent(
                JList<?> l, Object v, int i, boolean s, boolean f) {
              Component cell =
                  super.getListCellRendererComponent(l, human(Objects.toString(v, "")), i, s, f);
              cell.setFont(font(Font.PLAIN, 15));
              return cell;
            }
          });
      b.addActionListener(
          e -> {
            if (!filtering) {
              page = 0;
              refreshModel();
            }
          });
    }
    row1.add(status);
    row1.add(production);
    row1.add(staff);
    row2.add(payment);
    row2.add(delivery);
    JButton refresh = button("Refresh", false);
    row2.add(refresh);
    filterRows.add(row1);
    filterRows.add(row2);
    filters.add(filterRows);
    top.add(filters);
    add(top, BorderLayout.NORTH);
    table(table);
    table.setRowHeight(79);
    table.setShowVerticalLines(false);
    table.setShowHorizontalLines(true);
    table.setDefaultRenderer(Object.class, new ListCell(model));
    for (int i = 0; i < table.getColumnCount(); i++)
      table.getColumnModel().getColumn(i).setCellRenderer(new ListCell(model));
    table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    rebuildColumns();
    JScrollPane list = new JScrollPane(table);
    list.getViewport()
        .addComponentListener(
            new java.awt.event.ComponentAdapter() {
              public void componentResized(java.awt.event.ComponentEvent e) {
                int mode =
                    list.getViewport().getWidth() < 680
                        ? JTable.AUTO_RESIZE_OFF
                        : JTable.AUTO_RESIZE_ALL_COLUMNS;
                if (table.getAutoResizeMode() != mode) {
                  table.setAutoResizeMode(mode);
                  table.revalidate();
                }
              }
            });
    list.setBorder(BorderFactory.createLineBorder(line()));
    list.getViewport().setBackground(background());
    add(list);
    table.getTableHeader().setPreferredSize(new Dimension(0, 52));
    table
        .getTableHeader()
        .setDefaultRenderer(
            (t, value, selected, focus, row, column) -> {
              JLabel heading =
                  label(
                      value.equals("Order #")
                          ? "<html>Order #<br>Assigned to</html>"
                          : value.equals("Due") ? "Due date" : value.toString(),
                      16,
                      false);
              heading.setOpaque(true);
              heading.setBackground(background());
              heading.setBorder(
                  BorderFactory.createCompoundBorder(
                      BorderFactory.createMatteBorder(0, 0, 1, 1, line()),
                      new EmptyBorder(6, 12, 6, 4)));
              return heading;
            });
    JPanel footer = panel(new BorderLayout(10, 0));
    footer.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, line()), new EmptyBorder(18, 4, 17, 4)));
    count.setFont(font(Font.PLAIN, 16));
    count.setPreferredSize(new Dimension(145, 38));
    footer.add(count, BorderLayout.WEST);
    JPanel paging = panel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    pageSize.setSelectedItem(50);
    field(pageSize);
    pageSize.setPreferredSize(new Dimension(128, 38));
    pageSize.setRenderer(
        new DefaultListCellRenderer() {
          public Component getListCellRendererComponent(
              JList<?> l, Object v, int i, boolean selected, boolean focus) {
            return super.getListCellRendererComponent(l, v + " per page", i, selected, focus);
          }
        });
    pageSize.setToolTipText("Orders per page");
    pageSize.addActionListener(
        e -> {
          page = 0;
          filter();
        });
    previous.addActionListener(
        e -> {
          page--;
          filter();
        });
    next.addActionListener(
        e -> {
          page++;
          filter();
        });
    JPanel sizeControls = panel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    sizeControls.add(pageSize);
    footer.add(sizeControls);
    previous.setPreferredSize(new Dimension(38, 38));
    next.setPreferredSize(new Dimension(38, 38));
    pageLabel.setHorizontalAlignment(SwingConstants.CENTER);
    pageLabel.setPreferredSize(new Dimension(58, 38));
    paging.add(previous);
    paging.add(pageLabel);
    paging.add(next);
    footer.add(paging, BorderLayout.EAST);
    add(footer, BorderLayout.SOUTH);
    model.addTableModelListener(
        e -> {
          if (!refreshPending) {
            refreshPending = true;
            SwingUtilities.invokeLater(
                () -> {
                  refreshPending = false;
                  refreshModel();
                });
          }
        });
    sorter.addRowSorterListener(
        e -> {
          if (e.getType() == javax.swing.event.RowSorterEvent.Type.SORT_ORDER_CHANGED)
            SwingUtilities.invokeLater(this::filter);
        });
    refresh.addActionListener(
        e -> {
          for (var l : search.getActionListeners())
            l.actionPerformed(new java.awt.event.ActionEvent(search, 0, "refresh"));
        });
    filter();
  }

  private void rebuildColumns() {
    for (int i = table.getColumnModel().getColumnCount() - 1; i >= 0; i--) {
      String name = table.getColumnModel().getColumn(i).getHeaderValue().toString();
      if (!List.of("Order #", "Customer", "Status", "Due", "Balance").contains(name))
        table.removeColumn(table.getColumnModel().getColumn(i));
    }
    String[] order = {"Order #", "Customer", "Status", "Due", "Balance"};
    for (int i = 0; i < order.length; i++)
      for (int j = i; j < table.getColumnCount(); j++)
        if (table.getColumnName(j).equals(order[i])) {
          table.moveColumn(j, i);
          break;
        }
    int[] widths = {225, 130, 110, 105, 110};
    for (int i = 0; i < widths.length; i++) {
      table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
      table.getColumnModel().getColumn(i).setMinWidth(widths[i]);
    }
  }

  void refreshModel() {
    filtering = true;
    Object selected = staff.getSelectedItem();
    java.util.TreeSet<String> names = new java.util.TreeSet<>();
    int assigned = 0, free = 0, total = 0;
    for (int r = 0; r < model.getRowCount(); r++) {
      String name = value(r, "Assigned To");
      if (!name.isBlank()) names.add(name);
      if (!inCurrentScope(r)) continue;
      total++;
      if (name.isBlank()) free++;
      else {
        names.add(name);
        if (isMine(r)) assigned++;
      }
    }
    staff.removeAllItems();
    staff.addItem("Assigned to: All staff");
    for (String name : names) staff.addItem(name);
    if (selected != null && names.contains(selected.toString())) staff.setSelectedItem(selected);
    all.setText("All orders (" + total + ")");
    mine.setText("My orders (" + assigned + ")");
    unassigned.setText("Unassigned (" + free + ")");
    filtering = false;
    filter();
  }

  private String value(int r, String column) {
    int c = model.findColumn(column);
    return c < 0 ? "" : Objects.toString(model.getValueAt(r, c), "");
  }

  private boolean isMine(int r) {
    String id = value(r, "Assigned User ID");
    return id.isBlank()
        ? value(r, "Assigned To").equals(managers.SessionManager.getCurrentUserDisplayName())
        : id.equals(Objects.toString(managers.SessionManager.getCurrentUserId(), ""));
  }

  boolean accepts(int r) {
    if (!inCurrentScope(r)) return false;
    String assignee = value(r, "Assigned To");
    if (mine.isSelected() && !isMine(r)) return false;
    if (unassigned.isSelected() && !assignee.isBlank()) return false;
    if (status.getSelectedIndex() > 1
        && !CustomOrderListColumns.displayStatus(value(r, "Status"), value(r, "Production"))
            .equals(status.getSelectedItem())) return false;
    if (staff.getSelectedIndex() > 0 && !assignee.equals(staff.getSelectedItem())) return false;
    if (production.getSelectedIndex() > 0
        && !contains(value(r, "Production"), production.getSelectedItem())) return false;
    if (delivery.getSelectedIndex() > 0
        && !contains(value(r, "Delivery"), delivery.getSelectedItem())) return false;
    String selected = Objects.toString(payment.getSelectedItem(), "");
    if (payment.getSelectedIndex() > 0) {
      boolean pending = "Price pending".equals(value(r, "Total")),
          billed = value(r, "Payment").contains("INVOICE");
      java.math.BigDecimal paid = decimal(value(r, "Paid")), balance = decimal(value(r, "Balance"));
      if (!switch (selected) {
        case "Price pending" -> pending;
        case "Billed on invoice" -> billed;
        case "Paid" -> !pending && !billed && balance.signum() == 0;
        case "Part paid" -> !pending && !billed && paid.signum() > 0 && balance.signum() > 0;
        default -> !pending && !billed && paid.signum() == 0 && balance.signum() > 0;
      }) return false;
    }
    return true;
  }

  private boolean inCurrentScope(int row) {
    if (status.getSelectedIndex() != 0) return true;
    if ("CANCELLED".equals(value(row,"Status"))) return false;
    if (!"DELIVERED".equals(value(row,"Status"))) return true;
    String balance=value(row,"Balance");
    if ("Price pending".equals(value(row,"Total")) || balance.isBlank()) return true;
    if ("ACCOUNT".equals(value(row,"Payment").split(" / ", 2)[0].trim())) return false;
    try { return new java.math.BigDecimal(balance.replace(",", "")).signum()>0; }
    catch(NumberFormatException unknownBalance) { return true; }
  }

  private static boolean contains(String states, Object selection) {
    return List.of(states.split(",")).contains(selection);
  }

  private static java.math.BigDecimal decimal(String s) {
    try {
      return new java.math.BigDecimal(s);
    } catch (Exception e) {
      return java.math.BigDecimal.ZERO;
    }
  }

  private void filter() {
    if (filtering) return;
    Object selected =
        table.getSelectedRow() < 0
            ? null
            : model.getValueAt(table.convertRowIndexToModel(table.getSelectedRow()), 0);
    matching.clear();
    for (int r = 0; r < model.getRowCount(); r++) if (accepts(r)) matching.add(r);
    int size = (Integer) pageSize.getSelectedItem(),
        pages = Math.max(1, (matching.size() + size - 1) / size);
    page = Math.max(0, Math.min(page, pages - 1));
    // Filter the sorted order, then paginate those view positions so sorting stays global.
    table.getSelectionModel().setValueIsAdjusting(true);
    try {
      sorter.setRowFilter(
          new RowFilter<DefaultTableModel, Integer>() {
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> e) {
              return matching.contains(e.getIdentifier());
            }
          });
      List<Integer> sorted = new ArrayList<>();
      for (int r = 0; r < sorter.getViewRowCount(); r++)
        sorted.add(sorter.convertRowIndexToModel(r));
      java.util.Set<Integer> visible =
          new java.util.HashSet<>(
              sorted.subList(
                  Math.min(page * size, sorted.size()),
                  Math.min((page + 1) * size, sorted.size())));
      sorter.setRowFilter(
          new RowFilter<DefaultTableModel, Integer>() {
            public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> e) {
              return visible.contains(e.getIdentifier());
            }
          });
      if (selected != null)
        for (int v = 0; v < table.getRowCount(); v++)
          if (Objects.equals(model.getValueAt(table.convertRowIndexToModel(v), 0), selected)) {
            table.setRowSelectionInterval(v, v);
            break;
          }
      if (table.getSelectedRow() < 0 && table.getRowCount() > 0)
        table.setRowSelectionInterval(0, 0);
    } finally {
      table.getSelectionModel().setValueIsAdjusting(false);
    }
    count.setText(
        (matching.isEmpty()
                ? "0"
                : (page * size + 1) + "–" + Math.min((page + 1) * size, matching.size()))
            + " of "
            + matching.size()
            + " orders");
    pageLabel.setText((page + 1) + " / " + pages);
    previous.setEnabled(page > 0);
    next.setEnabled(page + 1 < pages);
    for (JToggleButton b : List.of(all, mine, unassigned)) {
      b.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
      b.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createLineBorder(line()),
              BorderFactory.createCompoundBorder(
                  BorderFactory.createMatteBorder(
                      0, 0, 3, 0, b.isSelected() ? DeckersPalette.ORANGE : background()),
                  new EmptyBorder(10, 2, 8, 2))));
    }
  }
}
