package ui.screens.customorders;

import static ui.screens.customorders.OrdersDesign.*;

import com.google.gson.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import ui.design.DeckersLogoManager;
import ui.design.DeckersPalette;
import ui.helpers.UiTaskRunner;

/** Presentation of a selected order; mutations stay in the existing workflow handler. */
final class OrdersDetailWorkspace extends JPanel implements Scrollable {
  private final JPanel heading = panel(new BorderLayout(14, 5));
  private final JPanel stages = panel(new BorderLayout()),
      paymentStatus = panel(new BorderLayout()),
      deliveryStatus = panel(new BorderLayout());
  private final DefaultTableModel rows =
      new DefaultTableModel(
          new String[] {"#", "Item / Description", "Size", "Quantity", "Unit Price", "Line Total"},
          0) {
        public boolean isCellEditable(int r, int c) {
          return false;
        }
      };
  final JTable items = new JTable(rows);
  final JTabbedPane tabs =
      new JTabbedPane() {
        @Override
        public void updateUI() {
          setUI(new OrdersTabsUI());
        }
      };
  final JPanel paymentForm = card(new BorderLayout(8, 8));
  private final JPanel totals = card(new GridLayout(0, 2, 8, 5));
  private final JLabel accountNotice = label("Charged to account", 14, true);
  private final JTextArea instructions = new JTextArea(3, 30);
  private final DefaultListModel<String> files = new DefaultListModel<>();
  private final JLabel preview = new JLabel("No product preview", SwingConstants.CENTER),
      approval = label("", 14, false);
  private final JPanel fileTab = panel(new BorderLayout(8, 8));
  private final DefaultListModel<String> proofs = new DefaultListModel<>();
  private final JButton change = button("Change", false);
  private JsonObject selected;
  private Runnable mediaAction = () -> {}, assignmentAction = () -> {}, detailsAction = () -> {};
  private long mediaGeneration;
  private boolean assignmentAllowed;
  private void copyAttachments(JList<String> list) {
    if(selected==null)return;
    JsonObject summary=selected.getAsJsonObject("summary");
    String id=text(summary,"custom_order_id");if(id.isBlank())return;
    JsonArray attachments=array(selected,"files");
    List<JsonObject> chosen=new java.util.ArrayList<>();
    for(int index:list.getSelectedIndices())if(index<attachments.size()){
      JsonObject file=attachments.get(index).getAsJsonObject();
      if(!text(file,"file_id").isBlank())chosen.add(file.deepCopy());
    }
    CustomOrderAttachmentClipboard.copy(this,Long.parseLong(id),chosen);
  }

  private void installAttachmentCopy(JList<String> list) {
    list.getInputMap().put(KeyStroke.getKeyStroke("control C"),"copyOrderAttachment");
    list.getActionMap().put("copyOrderAttachment",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){copyAttachments(list);}});
    JPopupMenu menu=new JPopupMenu();
    JMenuItem copy=new JMenuItem("Copy file(s)");copy.addActionListener(e->copyAttachments(list));menu.add(copy);
    JMenuItem manage=new JMenuItem("Manage files");manage.addActionListener(e->mediaAction.run());menu.add(manage);
    list.addMouseListener(new java.awt.event.MouseAdapter(){
      private void popup(java.awt.event.MouseEvent e){
        if(!e.isPopupTrigger()&&!(e.getID()==java.awt.event.MouseEvent.MOUSE_PRESSED&&e.getX()>list.getWidth()-35))return;
        int index=list.locationToIndex(e.getPoint());
        if(index<0||!list.getCellBounds(index,index).contains(e.getPoint()))return;
        if(!list.isSelectedIndex(index))list.setSelectedIndex(index);
        copy.setEnabled(selected!=null&&index<array(selected,"files").size()
            &&!text(array(selected,"files").get(index).getAsJsonObject(),"file_id").isBlank());
        menu.show(list,e.getX(),e.getY());
      }
      public void mousePressed(java.awt.event.MouseEvent e){popup(e);}
      public void mouseReleased(java.awt.event.MouseEvent e){popup(e);}
    });
  }
  interface DueDateUpdater {
    void save(long orderId, java.time.LocalDate date, java.util.function.Consumer<Boolean> completion);
  }
  private DueDateUpdater dueDateUpdater;
  void setDueDateUpdater(DueDateUpdater updater) { dueDateUpdater = updater; }

  private JComponent dueDateControl(JsonObject summary) {
    if (!text(summary, "due_date").isBlank() || dueDateUpdater == null
        || List.of("CANCELLED", "DELIVERED").contains(text(summary, "status")))
      return label(due(text(summary, "due_date")), 16, false);
    JPanel controls = panel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    DatePickerField date = new DatePickerField();
    date.setName("missingOrderDueDate");
    date.getAccessibleContext().setAccessibleName("Order due date");
    OrdersDesign.field(date);
    JButton save = button("Set date", false);
    save.setName("saveOrderDueDate");
    JLabel error = label("", 13, false);
    error.setForeground(DeckersPalette.ORANGE);
    controls.add(date); controls.add(save); controls.add(error);
    JsonObject order = selected;
    long generation = mediaGeneration;
    save.addActionListener(e -> {
      if (selected != order || generation != mediaGeneration) return;
      java.time.LocalDate value;
      try { value = date.getSelectedDate(); }
      catch (java.time.format.DateTimeParseException invalid) { error.setText("Use YYYY-MM-DD"); return; }
      if (value == null) { error.setText("Choose a date"); return; }
      String id = text(summary, "custom_order_id");
      if (id.isBlank()) return;
      error.setText(""); date.setEnabled(false); save.setEnabled(false);
      dueDateUpdater.save(Long.parseLong(id), value, success -> {
        if (selected != order || generation != mediaGeneration) return;
        if (success) { controls.removeAll(); controls.add(label(due(value.toString()),16,false)); controls.revalidate(); controls.repaint(); }
        else { date.setEnabled(true); save.setEnabled(true); }
      });
    });
    return controls;
  }
  interface DeliveryUpdater {
    void update(long orderId, String status, java.util.function.Consumer<Boolean> completion);
  }
  private DeliveryUpdater deliveryUpdater;

  void setDeliveryUpdater(DeliveryUpdater updater) { deliveryUpdater = updater; }

  private JComboBox<String> deliverySelector(JsonObject summary) {
    var selector = new JComboBox<>(new String[]{"Update delivery…", "Deliver all", "Called", "Called not answered"});
    selector.setName("orderDeliveryAction");
    selector.getAccessibleContext().setAccessibleName("Whole order delivery or call status");
    OrdersDesign.field(selector);
    selector.setEnabled(deliveryUpdater != null
        && !List.of("CANCELLED", "DELIVERED").contains(text(summary, "status")));
    JsonObject order = selected;
    long generation = mediaGeneration;
    boolean[] busy = {false};
    selector.addActionListener(e -> {
      if (busy[0] || !selector.isEnabled() || selector.getSelectedIndex() == 0
          || selected != order || generation != mediaGeneration) return;
      String id = text(summary, "custom_order_id");
      if (id.isBlank() || deliveryUpdater == null) return;
      String status = switch (selector.getSelectedIndex()) {
        case 1 -> "DELIVERED"; case 2 -> "CALLED"; default -> "CALLED_NO_ANSWER";
      };
      busy[0] = true;
      selector.setEnabled(false);
      deliveryUpdater.update(Long.parseLong(id), status, success -> {
        if (selected != order || generation != mediaGeneration) return;
        selector.setSelectedIndex(0);
        busy[0] = false;
        selector.setEnabled(true);
      });
    });
    return selector;
  }
  interface AssignmentHandler {
    void load(java.util.function.Consumer<List<services.CustomOrderDataService.EmployeeOption>> completion);
    void save(long orderId, Integer employeeId, String status, java.util.function.Consumer<Boolean> completion);
  }
  private AssignmentHandler assignmentHandler;

  void setAssignmentHandler(AssignmentHandler handler) {
    assignmentHandler = handler;
    change.setVisible(handler == null);
  }

  private JComponent assignmentSelector(JsonObject summary) {
    String name = fallback(text(summary, "assigned_to_name"), "Unassigned");
    if (!assignmentAllowed || assignmentHandler == null
        || List.of("DELIVERED", "CANCELLED").contains(text(summary, "status")))
      return label(name, 16, false);
    var selector = new JComboBox<services.CustomOrderDataService.EmployeeOption>();
    selector.setName("orderAssignedEmployee");
    selector.getAccessibleContext().setAccessibleName("Assigned employee");
    OrdersDesign.field(selector);
    selector.setPreferredSize(new Dimension(190, 30));
    Integer employeeId = text(summary, "assigned_to_user_id").isBlank() ? null
        : Integer.valueOf(text(summary, "assigned_to_user_id"));
    var current = new java.util.concurrent.atomic.AtomicReference<>(
        new services.CustomOrderDataService.EmployeeOption(employeeId, name));
    selector.addItem(current.get());
    selector.setEnabled(false);
    selector.setToolTipText("Loading employees…");
    JsonObject order = selected;
    long generation = mediaGeneration;
    boolean[] adjusting = {true};
    assignmentHandler.load(options -> {
      if (selected != order || generation != mediaGeneration) return;
      if (options == null) { selector.setToolTipText("Could not load employees. Refresh the order to retry."); return; }
      selector.removeAllItems();
      selector.addItem(new services.CustomOrderDataService.EmployeeOption(null, "Unassigned"));
      for (var option : options) selector.addItem(option);
      services.CustomOrderDataService.EmployeeOption match = null;
      for (int i = 0; i < selector.getItemCount(); i++) {
        var option = selector.getItemAt(i);
        if (employeeId != null ? employeeId.equals(option.userId()) : name.equals(option.name())) { match = option; break; }
      }
      if (match == null) { match = current.get(); selector.addItem(match); }
      current.set(match);
      selector.setSelectedItem(match);
      adjusting[0] = false;
      selector.setEnabled(true);
      selector.setToolTipText("Select an employee to assign this order");
    });
    selector.addActionListener(e -> {
      if (adjusting[0] || selected != order || generation != mediaGeneration) return;
      var chosen = (services.CustomOrderDataService.EmployeeOption) selector.getSelectedItem();
      if (chosen == null || chosen.equals(current.get())) return;
      String id = text(summary, "custom_order_id");
      if (id.isBlank()) return;
      adjusting[0] = true;
      selector.setEnabled(false);
      assignmentHandler.save(Long.parseLong(id), chosen.userId(), text(summary, "status"), success -> {
        if (selected != order || generation != mediaGeneration) return;
        if (success) current.set(chosen);
        selector.setSelectedItem(current.get());
        adjusting[0] = false;
        selector.setEnabled(true);
      });
    });
    return selector;
  }
  private ProductionUpdater productionUpdater;
  private boolean productionBusy;

  interface ProductionUpdater {
    void update(long orderId, List<Long> lineIds, String status, java.util.function.Consumer<Boolean> completion);
  }

  void setProductionUpdater(ProductionUpdater updater) {
    productionUpdater = updater;
    renderProduction();
  }

  private void renderProduction() {
    stages.removeAll();
    if (selected != null) {
      String status = aggregate(selected, "production_status", "NOT_STARTED");
      setStatus(stages, status);
      String orderStatus = text(selected.getAsJsonObject("summary"), "status");
      stages.add(new OrdersProductionSteps(status, this::updateWholeOrderProduction,
          productionUpdater != null && !productionBusy
              && !List.of("CANCELLED", "DELIVERED").contains(orderStatus)));
    }
    stages.revalidate();
    stages.repaint();
  }

  private void updateWholeOrderProduction(String status) {
    if (productionUpdater == null || productionBusy || selected == null) return;
    JsonObject summary = selected.getAsJsonObject("summary");
    if (List.of("CANCELLED", "DELIVERED").contains(text(summary, "status"))) return;
    String orderId = text(summary, "custom_order_id");
    if (orderId.isBlank()) return;
    List<Long> ids = new java.util.ArrayList<>();
    for (JsonElement element : array(selected, "lines")) {
      String id = text(element.getAsJsonObject(), "custom_order_line_id");
      if (!id.isBlank()) ids.add(Long.parseLong(id));
    }
    if (ids.isEmpty()) return;
    productionBusy = true;
    renderProduction();
    productionUpdater.update(Long.parseLong(orderId), List.copyOf(ids), status, success -> {
      productionBusy = false;
      renderProduction();
    });
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(850, 1000);
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return getParent() == null || getParent().getWidth() >= 700;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return getParent() != null && getParent().getHeight() >= 800;
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
    return 24;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
    return Math.max(24, r.height - 24);
  }

  OrdersDetailWorkspace(CustomOrderDetailsPanel legacy) {
    super(new BorderLayout(0, 12));
    setBackground(DeckersPalette.surface());
    setBorder(new EmptyBorder(18, 16, 65, 16));
    setMinimumSize(new Dimension(550, 400));
    putClientProperty("SmartStock.preserveThemeColors", true);
    change.setBorder(new EmptyBorder(0, 4, 0, 4));
    change.setContentAreaFilled(false);
    change.setForeground(DeckersPalette.ORANGE);
    JPanel north = panel(new BorderLayout(0, 14));
    north.add(heading, BorderLayout.NORTH);
    JPanel cards =
        new JPanel(new GridBagLayout()) {
          @Override
          public void doLayout() {
            GridBagLayout layout = (GridBagLayout) getLayout();
            double[] weights =
                getWidth() < 800 ? new double[] {.46, .27, .27} : new double[] {.5, .24, .26};
            for (int i = 0; i < getComponentCount(); i++) {
              GridBagConstraints constraints = layout.getConstraints(getComponent(i));
              constraints.weightx = weights[i];
              layout.setConstraints(getComponent(i), constraints);
              JPanel card = (JPanel) getComponent(i);
              JPanel header =
                  (JPanel) ((BorderLayout) card.getLayout()).getLayoutComponent(BorderLayout.NORTH);
              JLabel title =
                  (JLabel)
                      ((BorderLayout) header.getLayout()).getLayoutComponent(BorderLayout.CENTER);
              title.setFont(font(Font.BOLD, getWidth() < 800 ? 14 : 16));
              title.setIcon(
                  OrdersIcons.icon(
                      title.getText().toLowerCase(java.util.Locale.ROOT),
                      ink(),
                      getWidth() < 800 ? 18 : 22));
            }
            super.doLayout();
          }
        };
    cards.setOpaque(false);
    GridBagConstraints cg = new GridBagConstraints();
    cg.fill = GridBagConstraints.BOTH;
    cg.weighty = 1;
    cg.insets = new Insets(0, 0, 0, 4);
    cg.weightx = .5;
    cards.add(statusCard("Production", stages), cg);
    cg.weightx = .24;
    cards.add(statusCard("Payment", paymentStatus), cg);
    cg.weightx = .26;
    cg.insets = new Insets(0, 0, 0, 0);
    cards.add(statusCard("Delivery", deliveryStatus), cg);
    north.add(cards);
    add(north, BorderLayout.NORTH);
    JPanel itemTab = panel(new BorderLayout(0, 12));
    itemTab.setOpaque(true);
    itemTab.setBackground(DeckersPalette.surface());
    itemTab.setBorder(new EmptyBorder(17, 14, 10, 12));
    JPanel itemTop = panel(new BorderLayout(12, 0));
    JPanel itemTable = panel(new BorderLayout(0, 8));
    itemTable.add(label("Order items", 18, true), BorderLayout.NORTH);
    table(items);
    int[] widths = {43, 800, 80, 107, 105, 125};
    for (int i = 0; i < widths.length; i++) {
      items.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
      items.getColumnModel().getColumn(i).setMinWidth(i == 1 ? 120 : widths[i]);
      if (i != 1) items.getColumnModel().getColumn(i).setMaxWidth(widths[i]);
    }
    items.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
    items.setDefaultRenderer(
        Object.class,
        new javax.swing.table.DefaultTableCellRenderer() {
          public Component getTableCellRendererComponent(
              JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            setFont(font(Font.PLAIN, 16));
            setForeground(ink());
            setHorizontalAlignment(
                column == 2 || column == 3
                    ? SwingConstants.CENTER
                    : column >= 4 ? SwingConstants.RIGHT : SwingConstants.LEFT);
            setBorder(new EmptyBorder(4, 10, 4, 10));
            setToolTipText(java.util.Objects.toString(value, ""));
            return this;
          }
        });
    items.getColumnModel().getColumn(1).setCellRenderer(
        (table, value, selected, focus, row, column) -> {
          JTextArea description = new JTextArea(java.util.Objects.toString(value, ""));
          description.setFont(font(Font.PLAIN, 16));
          description.setForeground(ink());
          description.setBackground(selected ? table.getSelectionBackground() : table.getBackground());
          description.setLineWrap(true);
          description.setWrapStyleWord(true);
          description.setBorder(new EmptyBorder(8, 10, 8, 10));
          description.setSize(Math.max(120, table.getColumnModel().getColumn(column).getWidth()), 32767);
          return description;
        });
    items.getColumnModel().addColumnModelListener(new javax.swing.event.TableColumnModelListener() {
      public void columnMarginChanged(javax.swing.event.ChangeEvent e) { updateItemRowHeights(); }
      public void columnAdded(javax.swing.event.TableColumnModelEvent e) {}
      public void columnRemoved(javax.swing.event.TableColumnModelEvent e) {}
      public void columnMoved(javax.swing.event.TableColumnModelEvent e) {}
      public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) {}
    });
    JScrollPane itemScroll = new JScrollPane(items);
    itemScroll
        .getViewport()
        .addComponentListener(
            new java.awt.event.ComponentAdapter() {
              public void componentResized(java.awt.event.ComponentEvent e) {
                int mode =
                    itemScroll.getViewport().getWidth() < 570
                        ? JTable.AUTO_RESIZE_OFF
                        : JTable.AUTO_RESIZE_ALL_COLUMNS;
                if (items.getAutoResizeMode() != mode) {
                  items.setAutoResizeMode(mode);
                  items.revalidate();
                }
              }
            });
    itemScroll.setBorder(BorderFactory.createLineBorder(line()));
    itemScroll.setPreferredSize(new Dimension(500, 150));
    itemTable.add(itemScroll);
    itemTop.add(itemTable);
    JPanel product = panel(new BorderLayout(0, 8));
    product.setPreferredSize(new Dimension(186, 185));
    product.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, line()));
    JLabel productTitle = label("Product preview", 16, true);
    productTitle.setBorder(new EmptyBorder(0, 12, 0, 0));
    product.add(productTitle, BorderLayout.NORTH);
    preview.setFont(font(Font.PLAIN, 12));
    preview.setForeground(muted());
    preview.setBorder(BorderFactory.createLineBorder(line()));
    product.add(preview);
    itemTop.add(product, BorderLayout.EAST);
    itemTab.add(itemTop, BorderLayout.NORTH);
    JPanel lower = panel(new BorderLayout(20, 0));
    JPanel notes = panel(new BorderLayout(0, 6));
    notes.add(label("Instructions", 16, true), BorderLayout.NORTH);
    instructions.setEditable(false);
    instructions.setLineWrap(true);
    instructions.setWrapStyleWord(true);
    field(instructions);
    notes.add(new JScrollPane(instructions));
    lower.add(notes, BorderLayout.CENTER);
    JPanel attachments = panel(new BorderLayout(0, 6));
    JPanel filesHead = panel(new BorderLayout());
    filesHead.add(label("Attachments", 16, true));
    JButton add = button("Add file…", false);
    OrdersIcons.install(add, "clip", false);
    add.setBorder(new EmptyBorder(5, 10, 5, 10));
    add.addActionListener(e -> mediaAction.run());
    filesHead.add(add, BorderLayout.EAST);
    attachments.add(filesHead, BorderLayout.NORTH);
    JList<String> attachmentList = new JList<>(files);
    attachmentList.setFont(font(Font.PLAIN, 13));
    attachmentList.setForeground(ink());
    attachmentList.setFixedCellHeight(34);
    attachmentList.setBackground(background());
    attachmentList.setCellRenderer(
        (list, value, index, selected, focus) -> {
          JPanel row = panel(new BorderLayout(10, 0));
          row.setOpaque(true);
          row.setBackground(selected ? selection() : background());
          row.setBorder(new EmptyBorder(5, 10, 5, 10));
          JLabel name = label(value, 15, false);
          name.setIcon(
              OrdersIcons.icon(
                  value.toLowerCase(java.util.Locale.ROOT).contains(".pdf") ? "pdf" : "file",
                  value.toLowerCase(java.util.Locale.ROOT).contains(".pdf") ? Color.RED : ink(),
                  22));
          name.setIconTextGap(12);
          row.add(name);
          row.add(new JLabel(OrdersIcons.icon("more", ink(), 18)), BorderLayout.EAST);
          return row;
        });
    attachmentList.addMouseListener(
        new java.awt.event.MouseAdapter() {
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (e.getClickCount() == 2) mediaAction.run();
          }
        });
    JButton copyFile=button("Copy file",false);copyFile.addActionListener(e->copyAttachments(attachmentList));
    JPanel attachmentActions=panel(new FlowLayout(FlowLayout.RIGHT,4,0));attachmentActions.add(copyFile);attachmentActions.add(add);filesHead.add(attachmentActions,BorderLayout.EAST);
    attachments.add(new JScrollPane(attachmentList));
    installAttachmentCopy(attachmentList);
    attachments.add(approval, BorderLayout.SOUTH);
    attachments.setPreferredSize(new Dimension(368, 95));
    lower.add(attachments, BorderLayout.EAST);
    itemTab.add(lower);
    tabs.setUI(new OrdersTabsUI());
    tabs.setFont(font(Font.PLAIN, 16));
    tabs.setForeground(ink());
    tabs.setBackground(DeckersPalette.surface());
    tabs.addTab("Items", itemTab);
    JButton manageFiles = button("Manage files & design approval", false);
    manageFiles.addActionListener(e -> mediaAction.run());
    fileTab.add(manageFiles, BorderLayout.NORTH);
    JPanel fileSections = panel(new GridLayout(2, 1, 0, 14));
    JList<String> allFiles = new JList<>(files);
    allFiles.setFont(font(Font.PLAIN, 16));
    allFiles.setFixedCellHeight(40);
    installAttachmentCopy(allFiles);
    allFiles.addMouseListener(
        new java.awt.event.MouseAdapter() {
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (e.getClickCount() == 2) mediaAction.run();
          }
        });
    JPanel attachmentSection = panel(new BorderLayout(0, 6));
    attachmentSection.add(label("Attachments", 18, true), BorderLayout.NORTH);
    attachmentSection.add(new JScrollPane(allFiles));
    fileSections.add(attachmentSection);
    JList<String> proofList = new JList<>(proofs);
    proofList.setFont(font(Font.PLAIN, 16));
    proofList.setFixedCellHeight(40);
    proofList.addMouseListener(
        new java.awt.event.MouseAdapter() {
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (e.getClickCount() == 2) mediaAction.run();
          }
        });
    JPanel proofSection = panel(new BorderLayout(0, 6));
    proofSection.add(label("Design proofs & customer approval", 18, true), BorderLayout.NORTH);
    proofSection.add(new JScrollPane(proofList));
    fileSections.add(proofSection);
    fileTab.add(fileSections);
    tabs.addTab("Files & approval", fileTab);
    // Reuse the actual financial ledger and activity components, including void/refund history.
    Component ledger = legacy.tabs.getComponentAt(1), history = legacy.tabs.getComponentAt(2);
    tabs.addTab("Payments", ledger);
    tabs.addTab("History", history);
    JPanel middle = panel(new BorderLayout(0, 12));
    middle.add(tabs);
    JPanel checkout = panel(new BorderLayout(12, 0));
    paymentForm.add(label("Payment", 16, true), BorderLayout.NORTH);
    checkout.add(paymentForm);
    totals.setPreferredSize(new Dimension(265, 145));
    totals.setBackground(
        DeckersPalette.dark() ? DeckersPalette.surface() : new Color(255, 242, 231));
    JPanel totalsSummary = panel(new BorderLayout(0, 5));
    totalsSummary.add(totals);
    accountNotice.setHorizontalAlignment(SwingConstants.RIGHT);
    accountNotice.setVisible(false);
    totalsSummary.add(accountNotice, BorderLayout.SOUTH);
    checkout.add(totalsSummary, BorderLayout.EAST);
    middle.add(checkout, BorderLayout.SOUTH);
    add(middle);
    change.addActionListener(e -> assignmentAction.run());
    items.addMouseListener(
        new java.awt.event.MouseAdapter() {
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (e.getClickCount() == 2) detailsAction.run();
          }
        });
    legacy.observeWorkspace(this::showDetails, this::state);
    state("Select an order to view its details");
  }

  void actions(Runnable media, Runnable assign, Runnable details) {
    mediaAction = media;
    assignmentAction = assign;
    detailsAction = details;
  }

  void allowAssignment(boolean enabled) {
    assignmentAllowed = enabled;
    change.setEnabled(enabled && selected != null);
  }

  JsonObject selected() {
    return selected;
  }

  void state(String text) {
    accountNotice.setVisible(false);
    selected = null;
    ++mediaGeneration;
    change.setEnabled(false);
    heading.removeAll();
    heading.add(label(text, 18, true));
    rows.setRowCount(0);
    files.clear();
    proofs.clear();
    stages.removeAll();
    paymentStatus.removeAll();
    deliveryStatus.removeAll();
    totals.removeAll();
    instructions.setText("");
    approval.setText("");
    showApproval("");
    for (JPanel body : List.of(stages, paymentStatus, deliveryStatus)) {
      JPanel header = (JPanel) ((JPanel) body.getParent()).getComponent(0);
      Component old = ((BorderLayout) header.getLayout()).getLayoutComponent(BorderLayout.EAST);
      if (old != null) header.remove(old);
    }
    preview.setIcon(null);
    preview.setText("No product preview");
    revalidate();
    repaint();
  }

  private JPanel statusCard(String title, JPanel body) {
    JPanel p = card(new BorderLayout(0, 8));
    p.setPreferredSize(new Dimension(0, 128));
    p.setMinimumSize(new Dimension(0, 128));
    p.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(line()), new EmptyBorder(10, 10, 10, 10)));
    JPanel header = panel(new BorderLayout(4, 0));
    JLabel titleLabel = label(title, 16, true);
    titleLabel.setIcon(OrdersIcons.icon(title.toLowerCase(java.util.Locale.ROOT), ink(), 22));
    titleLabel.setIconTextGap(9);
    header.add(titleLabel);
    p.add(header, BorderLayout.NORTH);
    p.add(body);
    return p;
  }

  void showDetails(JsonObject data) {
    selected = data;
    long generation = ++mediaGeneration;
    JsonObject s = data.getAsJsonObject("summary");
    heading.removeAll();
    JPanel title = panel(new FlowLayout(FlowLayout.LEFT, 14, 0));
    JLabel number = label(text(s, "order_number"), 26, true);
    number.setFont(new Font("Segoe UI", Font.BOLD, 26));
    title.add(number);
    JPanel separator = panel(new BorderLayout());
    separator.setPreferredSize(new Dimension(16, 28));
    separator.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.EAST);
    title.add(separator);
    title.add(label(text(s, "customer_name"), 28, true));
    title.add(badge(text(s, "status")));
    heading.add(title, BorderLayout.NORTH);
    JPanel meta = panel(new BorderLayout(8, 0));
    JPanel metadata = panel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    metadata.add(
        label(
            "Phone: "
                + text(s, "customer_phone")
                + "    |    Due date: ",
            16,
            false));
    metadata.add(dueDateControl(s));
    metadata.add(label("    |    Assigned to: ",16,false));
    metadata.add(assignmentSelector(s));
    String createdBy = text(s, "taken_by_name").strip();
    meta.add(label("Created by: " + (createdBy.isBlank() ? "Not recorded" : createdBy),
        16, false), BorderLayout.SOUTH);
    meta.add(metadata);
    meta.add(change, BorderLayout.EAST);
    heading.add(meta);
    change.setEnabled(
        assignmentAllowed && !List.of("DELIVERED", "CANCELLED").contains(text(s, "status")));
    boolean pending = "PENDING".equals(text(s, "pricing_state"));
    BigDecimal total = decimal(s, "total_amount"),
        paid = decimal(s, "amount_paid"),
        balance = decimal(s, "balance_due");
    String production = aggregate(data, "production_status", "NOT_STARTED"),
        delivery = aggregate(data, "delivery_status", "PENDING");
    stages.removeAll();
    setStatus(stages, production);
    renderProduction();
    paymentStatus.removeAll();
    setStatus(
        paymentStatus,
        Boolean.parseBoolean(text(s, "invoice_billed"))
            ? "BILLED_ON_INVOICE"
            : pending
                ? "PRICE_PENDING"
                : balance.signum() == 0 ? "PAID" : paid.signum() > 0 ? "PART_PAID" : "UNPAID");
    JPanel pay = panel(new BorderLayout(0, 8));
    JProgressBar progress = new JProgressBar(0, 100);
    progress.setUI(
        new javax.swing.plaf.basic.BasicProgressBarUI() {
          protected int getCellLength() {
            return 1;
          }

          protected int getCellSpacing() {
            return 0;
          }
        });
    progress.setBorderPainted(false);
    progress.setValue(
        total.signum() > 0
            ? paid.multiply(BigDecimal.valueOf(100))
                .divide(total, 0, java.math.RoundingMode.DOWN)
                .min(BigDecimal.valueOf(100))
                .intValue()
            : 0);
    progress.setForeground(DeckersPalette.ORANGE);
    progress.setBackground(line());
    progress.setPreferredSize(new Dimension(100, 9));
    pay.add(progress, BorderLayout.NORTH);
    JPanel figures = panel(new GridLayout(2, 2, 5, 4));
    figures.add(label("Paid", 16, false));
    figures.add(rightLabel(money(paid)));
    figures.add(label("Balance", 16, false));
    figures.add(rightLabel(pending ? "Price pending" : money(balance)));
    pay.add(figures, BorderLayout.SOUTH);
    paymentStatus.add(pay);
    deliveryStatus.removeAll();
    setStatus(deliveryStatus, delivery.equals("PENDING") ? "NOT_COLLECTED" : delivery);
    JPanel collection = panel(new BorderLayout(6, 0));
    collection.add(label("Collection", 16, false));
    collection.add(
        rightLabel(human(delivery.equals("PENDING") ? "NOT_COLLECTED" : delivery)),
        BorderLayout.EAST);
    collection.add(deliverySelector(s), BorderLayout.SOUTH);
    deliveryStatus.add(collection);
    rows.setRowCount(0);
    int index = 1;
    for (var group : CustomOrderQuantityGroups.group(data)) {
      JsonObject first = group.get(0);
      int qty = group.size();
      String description = itemDescription(data, first);
      rows.addRow(
          new Object[] {
            index++,
            description,
            text(first, "item_size"),
            qty,
            pending
                ? "Pending"
                : money(decimal(first, first.has("unit_price") ? "unit_price" : "line_total")),
            pending
                ? "Pending"
                : money(decimal(first, "line_total").multiply(BigDecimal.valueOf(qty)))
          });
    }
    updateItemRowHeights();
    instructions.setText(instructionsText(data));
    instructions.setCaretPosition(0);
    proofs.clear();
    proofs.addElement("Loading design proofs…");
    files.clear();
    for (JsonElement element : array(data, "files")) {
      JsonObject f = element.getAsJsonObject();
      files.addElement(
          text(f, "filename")
              + "    "
              + Math.max(1, decimal(f, "byte_size").longValue() / 1024)
              + " KB");
    }
    if (files.isEmpty()) files.addElement("No attachments");
    approval.setText("");
    preview.setIcon(null);
    preview.setText("No product preview");
    totals.removeAll();
    BigDecimal loyaltyDiscount = decimal(s, "loyalty_discount_amount");
    metric("Subtotal", pending ? "Price pending" : money(total.add(loyaltyDiscount)), false);
    if (loyaltyDiscount.signum() > 0)
      metric("Loyalty discount", "−" + money(loyaltyDiscount), false);
    metric("Paid", money(paid), false);
    accountNotice.setVisible(!pending && "ACCOUNT".equals(text(s, "payment_method"))
        && !Boolean.parseBoolean(text(s, "invoice_billed")));
    metric("Balance", pending ? "Price pending" : money(balance), true);
    metric("Total", pending ? "Price pending" : money(total), true);
    revalidate();
    repaint();
    if (s.has("custom_order_id")) loadPreview(s.get("custom_order_id").getAsLong(), generation);
    else {
      proofs.clear();
      proofs.addElement("No design proofs submitted");
    }
  }

  private void updateItemRowHeights() {
    for (int row = 0; row < items.getRowCount(); row++) {
      Component renderer = items.prepareRenderer(items.getCellRenderer(row, 1), row, 1);
      int height = Math.max(37, renderer.getPreferredSize().height);
      if (items.getRowHeight(row) != height) items.setRowHeight(row, height);
    }
  }

  static String itemDescription(JsonObject data, JsonObject line) {
    StringBuilder result = new StringBuilder(text(line, "item_name"));
    if (!text(line, "variant_name").isBlank()) result.append(" / ").append(text(line, "variant_name"));
    String id = text(line, "custom_order_line_id");
    if (!id.isBlank()) for (JsonElement element : array(data, "addons")) {
      JsonObject addon = element.getAsJsonObject();
      if (!id.equals(text(addon, "custom_order_line_id"))) continue;
      java.util.List<String> parts = new java.util.ArrayList<>();
      String material = text(addon, "print_material_name");
      String normalized = material.trim().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
      if (normalized.equals("emb") || normalized.contains("embroider")) material = "EMB";
      else if (normalized.contains("vinyl")) material = "VINYL";
      else if (normalized.contains("transfer") && normalized.contains("paper")) material = "Transfer Paper";
      if (!material.isBlank()) parts.add(material);
      for (String field : List.of("print_size_name", "print_description")) {
        String value = text(addon, field);
        if (!value.isBlank()) parts.add(value);
      }
      if (!parts.isEmpty()) result.append('\n').append(String.join(" · ", parts));
    }
    return result.toString();
  }

  /** Includes production add-ons next to the same item groups displayed in the table. */
  static String instructionsText(JsonObject data) {
    StringBuilder notes=new StringBuilder(text(data.getAsJsonObject("summary"),"order_notes"));
    for(JsonElement element:array(data,"lines")){
      JsonObject line=element.getAsJsonObject();
      for(String field:List.of("customization_details","order_instructions")){
        String value=text(line,field);
        if(!value.isBlank()&&notes.indexOf(value)<0){if(notes.length()>0)notes.append('\n');notes.append(value);}
      }
    }
    int row=0;
    for(var group:CustomOrderQuantityGroups.group(data)){
      row++;JsonObject line=group.get(0);String id=text(line,"custom_order_line_id");
      if(id.isBlank())continue;
      List<String> addons=new java.util.ArrayList<>();
      for(JsonElement element:array(data,"addons")){
        JsonObject addon=element.getAsJsonObject();if(!id.equals(text(addon,"custom_order_line_id")))continue;
        List<String> parts=new java.util.ArrayList<>();
        for(String field:List.of("print_material_name","print_size_name","print_description")){String value=text(addon,field);if(!value.isBlank())parts.add(value);}
        if(!parts.isEmpty())addons.add("Add-on: "+String.join(" · ",parts));
      }
      if(addons.isEmpty())continue;
      if(notes.length()>0)notes.append("\n\n");
      notes.append("Item ").append(row).append(" — ").append(group.size()).append(" × ").append(text(line,"item_name"));
      for(String field:List.of("variant_name","item_size","item_color")){String value=text(line,field);if(!value.isBlank())notes.append(" / ").append(value);}
      for(String addon:addons)notes.append('\n').append(addon);
    }
    return notes.toString();
  }

  private void setStatus(JPanel body, String status) {
    JPanel header = (JPanel) ((JPanel) body.getParent()).getComponent(0);
    Component old = ((BorderLayout) header.getLayout()).getLayoutComponent(BorderLayout.EAST);
    if (old != null) header.remove(old);
    JLabel state = badge(status);
    state.setFont(font(Font.BOLD, 14));
    state.setBorder(new EmptyBorder(5, 7, 5, 7));
    header.add(state, BorderLayout.EAST);
  }

  private void metric(String title, String value, boolean bold) {
    totals.add(label(title, 16, bold));
    JLabel amount = label(value, title.equals("Total") ? 26 : bold ? 22 : 16, bold);
    amount.setHorizontalAlignment(SwingConstants.RIGHT);
    if (title.equals("Balance")) amount.setForeground(DeckersPalette.ORANGE);
    totals.add(amount);
  }

  private static JLabel rightLabel(String text) {
    JLabel label = label(text, 16, false);
    label.setHorizontalAlignment(SwingConstants.RIGHT);
    return label;
  }

  private void loadPreview(long orderId, long generation) {
    Window owner = SwingUtilities.getWindowAncestor(this);
    if (owner == null) return;
    UiTaskRunner.submit(
        owner,
        "orders.preview",
        () -> {
          JsonObject request = new JsonObject();
          request.addProperty("action", "LIST");
          request.addProperty("orderId", orderId);
          JsonObject media = services.LanApiClient.customOrderMediaRead(request);
          JsonObject image = null;
          boolean proof = false;
          String approved = "";
          var latest = latestProofs(media);
          boolean allApproved = !latest.isEmpty();
          for (JsonObject p : latest) {
            if (!"APPROVED".equals(text(p, "status"))) allApproved = false;
            if ("APPROVED".equals(text(p, "status")) && isImage(text(p, "filename"))) {
              image = p;
              proof = true;
            }
          }
          approved =
              allApproved ? "Design approved" : latest.isEmpty() ? "" : "Design approval pending";
          if (image == null)
            for (JsonElement e : array(media, "files")) {
              JsonObject f = e.getAsJsonObject();
              if (isImage(text(f, "filename"))) {
                image = f;
                break;
              }
            }
          ImageIcon icon = null;
          if (image != null) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
            long offset = 0, total = -1;
            while (total < 0 || offset < total) {
              JsonObject q = new JsonObject();
              q.addProperty("action", "CHUNK");
              q.addProperty("orderId", orderId);
              q.addProperty("fileId", text(image, "id"));
              q.addProperty("proof", proof);
              q.addProperty("offset", offset);
              JsonObject chunk = services.LanApiClient.customOrderMediaRead(q);
              total = chunk.get("totalBytes").getAsLong();
              if (total > 8 * 1024 * 1024) return new MediaPreview(null, approved, latest);
              byte[] part =
                  java.util.Base64.getDecoder().decode(chunk.get("bytesBase64").getAsString());
              if (part.length == 0) break;
              bytes.write(part);
              offset += part.length;
            }
            java.awt.image.BufferedImage decoded =
                javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes.toByteArray()));
            if (decoded != null)
              icon = new ImageIcon(DeckersLogoManager.scaleToFit(decoded, 178, 145));
          }
          return new MediaPreview(icon, approved, latest);
        },
        result -> {
          if (generation != mediaGeneration) return;
          preview.setIcon(result.icon());
          preview.setText(result.icon() == null ? "No product preview" : "");
          showApproval(result.approval());
          proofs.clear();
          for (JsonObject proof : result.proofs())
            proofs.addElement(
                "Rev "
                    + text(proof, "revision")
                    + "  ·  "
                    + text(proof, "filename")
                    + "  ·  "
                    + human(text(proof, "status")));
          if (proofs.isEmpty()) proofs.addElement("No design proofs submitted");
        },
        error -> {
          if (generation == mediaGeneration) {
            proofs.clear();
            proofs.addElement("Design proofs unavailable — use Files & Design Approval to retry");
            preview.setText("Preview unavailable");
            preview.setToolTipText(error.getMessage());
          }
        });
  }

  private void showApproval(String text) {
    approval.setText(text);
    approval.setIcon(text.isBlank() ? null : OrdersIcons.icon("check", new Color(0, 120, 36), 17));
    approval.setOpaque(!text.isBlank());
    approval.setBorder(new EmptyBorder(text.isBlank() ? 0 : 8, 12, text.isBlank() ? 0 : 8, 4));
    approval.setForeground(new Color(0, 120, 36));
    approval.setBackground(
        DeckersPalette.dark() ? DeckersPalette.surface() : new Color(232, 248, 237));
  }

  private record MediaPreview(ImageIcon icon, String approval, List<JsonObject> proofs) {}

  static List<JsonObject> latestProofs(JsonObject media) {
    java.util.Map<String, JsonObject> latest = new java.util.LinkedHashMap<>();
    for (JsonElement e : array(media, "proofs")) {
      JsonObject proof = e.getAsJsonObject();
      String id = text(proof, "lineId");
      JsonObject previous = latest.get(id);
      if (previous == null
          || decimal(proof, "revision").compareTo(decimal(previous, "revision")) > 0)
        latest.put(id, proof);
    }
    return new java.util.ArrayList<>(latest.values());
  }

  private static boolean isImage(String name) {
    return name.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(png|jpg|jpeg|gif|webp)$");
  }

  static String aggregate(JsonObject data, String field, String fallback) {
    String found = null;
    for (JsonElement e : array(data, "lines")) {
      JsonObject l = e.getAsJsonObject();
      if ("FULL".equals(text(l, "return_status"))) continue;
      String v = fallback(text(l, field), fallback);
      if (found != null && !found.equals(v)) return "MIXED";
      found = v;
    }
    return found == null ? fallback : found;
  }

  private static String text(JsonObject o, String key) {
    return CustomOrderDetailsPanel.text(o, key);
  }

  private static JsonArray array(JsonObject o, String key) {
    return o.has(key) && o.get(key).isJsonArray() ? o.getAsJsonArray(key) : new JsonArray();
  }

  private static BigDecimal decimal(JsonObject o, String key) {
    try {
      return new BigDecimal(text(o, key));
    } catch (Exception e) {
      return BigDecimal.ZERO;
    }
  }

  private static String fallback(String s, String fallback) {
    return s.isBlank() ? fallback : s;
  }

  private static String due(String s) {
    try {
      return java.time.LocalDate.parse(s)
          .format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy"));
    } catch (Exception e) {
      return fallback(s, "Not set");
    }
  }
}
