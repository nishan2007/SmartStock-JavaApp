package ui.screens;

import com.google.gson.*;
import services.LanApiClient;
import ui.design.DeckersPalette;
import ui.design.DeckersLogoManager;
import ui.design.DeckersSwing;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Native main-screen overseas workspace. Authorization and financial writes remain server-owned. */
public final class OverseasOrders extends JFrame {
    @FunctionalInterface interface Transport { JsonObject send(JsonObject request) throws Exception; }
    private enum Page { LIST, DETAIL, QUOTE, ITEM }
    private final Transport transport;
    private final JPanel root = new JPanel(new BorderLayout()){@Override public void paint(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);super.paint(g);g.dispose();}}, body = new JPanel(new BorderLayout());
    private final JTextField search = field(""), name = field(""), phone = field(""), expected = field(""), shipping = field("0.00");
    private final JTextArea notes = new JTextArea(3, 30);
    private final JComboBox<String> filter = new JComboBox<>(new String[]{"Active orders", "All orders", "Unpaid", "Arrived", "Completed"});
    private final DefaultTableModel ordersModel = model("Order", "Customer", "Status", "Total (GYD)", "Paid (GYD)", "Balance (GYD)", "");
    private final DefaultTableModel itemsModel = model("Item", "Seller", "Options", "Qty", "Amount (GYD)", "Status");
    private final JTable orders = new JTable(ordersModel), items = new JTable(itemsModel);
    private final List<JsonObject> rows = new ArrayList<>(), lineValues = new ArrayList<>();
    private final List<JButton> buttons = new ArrayList<>();
    private final Map<Integer, byte[]> stagedImages = new HashMap<>();
    private final JPanel phoneMatches = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JLabel status = label(" ", 14, false), listCount = label(" ", 14, false), totals = label("GYD 0.00", 22, true);
    private final JLabel quoteItems = label("0.00", 17, true), selectedItem = label("Select an item.", 15, false);
    private final JLabel customerHint=label("",14,false);
    private JsonObject detail, order, savedDraft;
    private Set<String> permissions = Set.of();
    private Integer customerId;
    private long phoneVersion;
    private boolean working, loading, editingIssued, disposed;
    private Page page = Page.LIST;
    private String selectedOrderId;
    private int detailTab;
    private final Map<String, JTextField> itemFields = new LinkedHashMap<>();
    private byte[] itemImage;
    private JsonObject itemOriginal, itemBaseline;
    private int itemIndex = -1;
    private JLabel itemTotal, imagePreview;
    private JComboBox<String> sellerChoices;
    private JCheckBox digitalItem;
    private JPanel headerTitles;

    public OverseasOrders() { this(LanApiClient::overseasOrders); }
    OverseasOrders(Transport transport) {
        super("Overseas Orders · SmartStock");
        this.transport = transport;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setSize(1440, 1024); setMinimumSize(new Dimension(960, 700)); setLocationRelativeTo(null);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { if (!working && confirmDiscard()) dispose(); }
            @Override public void windowClosed(java.awt.event.WindowEvent e) { disposed=true;phoneVersion++; }
        });
        root.setBackground(DeckersPalette.background()); root.putClientProperty("SmartStock.preserveBackground", true);root.putClientProperty("SmartStock.preserveThemeColors",true);
        body.setOpaque(false); root.add(buildBrandHeader(), BorderLayout.NORTH); root.add(body, BorderLayout.CENTER);
        status.setForeground(DeckersPalette.muted()); status.setBorder(new EmptyBorder(0, 28, 6, 28));status.setVisible(false); root.add(status, BorderLayout.SOUTH);
        setContentPane(root);
        notes.setFont(font(16, false)); notes.setLineWrap(true); notes.setWrapStyleWord(true);
        notes.setBackground(DeckersPalette.fieldBackground()); notes.setForeground(DeckersPalette.text());
        styleTable(orders, 57); styleTable(items, 44);
        int[] widths = {150,190,245,170,170,170,75};
        for (int i=0;i<widths.length;i++) orders.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        orders.setName("overseas.orders"); items.setName("overseas.items");
        orders.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 || orders.columnAtPoint(e.getPoint()) == 6) openSelectedOrder();
            }
        });
        orders.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "openOrder");
        orders.getActionMap().put("openOrder", new AbstractAction() { public void actionPerformed(java.awt.event.ActionEvent e) { openSelectedOrder(); }});
        orders.getSelectionModel().addListSelectionListener(e -> {
            int i = orders.getSelectedRow(); if (i >= 0 && i < rows.size()) selectedOrderId = s(rows.get(i), "id");
        });
        items.getSelectionModel().addListSelectionListener(e -> updateSelectedItem());
        ui.helpers.UiDebouncer.bind(phone, 350, this::lookupPhone);
        onChange(phone, () -> { phoneVersion++; phoneMatches.removeAll(); phoneMatches.revalidate(); phoneMatches.repaint(); });
        onChange(shipping, this::updateTotals);
        search.addActionListener(e -> refresh());
        filter.addActionListener(e -> { if (!working && page == Page.LIST) refresh(); });
        showList(false); refresh();
    }

    private JPanel buildBrandHeader() {
        JPanel header = panel(new BorderLayout(20, 0));
        header.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DeckersPalette.border()), new EmptyBorder(17, 28, 17, 28)));
        JPanel brand = row(24);
        ImageIcon logo = DeckersLogoManager.loadDeckersLogoIcon(OverseasOrders.class);
        if (logo != null) brand.add(new JLabel(new ImageIcon(DeckersLogoManager.scaleToFit(logo.getImage(), 190, 70))));
        brand.add(brandDivider());brand.add(label("SmartStock", 30, true));brand.add(brandDivider()); brand.add(label("Deckers " + storeName(), 23, false)); header.add(brand, BorderLayout.WEST);
        JLabel date = label(ui.helpers.StoreTimeZoneHelper.today().format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)), 16, false);
        date.setIcon(SalesTaskVisuals.icon("calendar", DeckersPalette.muted(), 18)); date.setIconTextGap(10); header.add(date, BorderLayout.EAST);
        return header;
    }
    private String storeName() {
        String store = order == null ? managers.SessionManager.getCurrentLocationName() : s(order, "store_name");
        if (store == null || store.isBlank()) return "Store";
        return store.startsWith("Deckers ") ? store.substring(8) : store;
    }
    private static Font font(int size, boolean bold) { return new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, Math.round(size * 1.16f)); }
    private static JSeparator brandDivider(){JSeparator s=new JSeparator(SwingConstants.VERTICAL);s.setPreferredSize(new Dimension(1,42));s.setForeground(DeckersPalette.border());return s;}
    private static JLabel label(String text, int size, boolean bold) { JLabel l = new JLabel(text); l.setFont(font(size, bold)); l.setForeground(DeckersPalette.text()); return l; }
    private static SalesTaskVisuals.Badge badge(String text,Color accent){SalesTaskVisuals.Badge badge=new SalesTaskVisuals.Badge(text,accent);badge.setFont(font(16,true));return badge;}
    private static JPanel panel(LayoutManager layout) { JPanel p = new JPanel(layout); p.setOpaque(false); return p; }
    private static JPanel row(int gap) { return panel(new FlowLayout(FlowLayout.LEFT, gap, 0)); }
    private static JPanel stack(int gap, Component... components) {
        // GridBagLayout switches both axes to minimum sizes when a wide field's
        // preferred width does not fit. A vertical form must retain row heights.
        JPanel p=new JPanel(null){
            @Override public void doLayout(){Insets in=getInsets();int y=in.top,width=Math.max(0,getWidth()-in.left-in.right);boolean first=true;for(Component c:getComponents()){if(!c.isVisible())continue;if(!first)y+=gap;c.setSize(width,c.getPreferredSize().height);int height=c.getPreferredSize().height;c.setBounds(in.left,y,width,height);y+=height;first=false;}}
            @Override public Dimension getPreferredSize(){Insets in=getInsets();int width=0,height=0,count=0;for(Component c:getComponents()){if(!c.isVisible())continue;Dimension d=c.getPreferredSize();width=Math.max(width,d.width);height+=d.height;count++;}return new Dimension(width+in.left+in.right,height+Math.max(0,count-1)*gap+in.top+in.bottom);}
            @Override public Dimension getMinimumSize(){return new Dimension(0,getPreferredSize().height);}
        };p.setOpaque(false);for(Component c:components)p.add(c);return p;
    }
    private static JTextField field(String value) {
        JTextField f = new JTextField(value){@Override protected void paintComponent(Graphics g){super.paintComponent(g);if(Boolean.TRUE.equals(getClientProperty("overseas.searchIcon")))SalesTaskVisuals.icon("search",DeckersPalette.muted(),19).paintIcon(this,g,12,(getHeight()-19)/2);Object placeholder=getClientProperty("JTextField.placeholderText");if(getText().isEmpty()&&placeholder!=null&&!hasFocus()){Graphics2D copy=(Graphics2D)g.create();copy.setColor(DeckersPalette.muted());copy.setFont(getFont());copy.drawString(placeholder.toString(),getInsets().left,(getHeight()-copy.getFontMetrics().getHeight())/2+copy.getFontMetrics().getAscent());copy.dispose();}}}; DeckersSwing.styleField(f); f.setFont(font(16, false));
        f.setBorder(BorderFactory.createCompoundBorder(new javax.swing.border.LineBorder(DeckersPalette.border(),1,true),new EmptyBorder(2,10,2,10)));
        f.setPreferredSize(new Dimension(180, 43)); f.setMinimumSize(new Dimension(70, 43)); return f;
    }
    private static JPanel labeled(String title, JComponent component) {
        component.getAccessibleContext().setAccessibleName(title);
        JLabel l = label(title, 16, false); l.setLabelFor(component); return stack(7, l, component);
    }
    private static JPanel columns(Component left, Component right, double leftWeight, int gap) {
        JPanel p = new JPanel(null) {
            private boolean stacked(){return leftWeight>.65 && getWidth()>0 && getWidth()<1100;}
            @Override public void setBounds(int x,int y,int w,int h){boolean changed=w!=getWidth();super.setBounds(x,y,w,h);if(changed)SwingUtilities.invokeLater(this::revalidate);}
            @Override public void doLayout() {if(stacked()){int first=left.getPreferredSize().height;left.setBounds(0,0,getWidth(),first);right.setBounds(0,first+gap,getWidth(),right.getPreferredSize().height);}else{int width=Math.max(0,getWidth()-gap), first=(int)Math.round(width*leftWeight);left.setBounds(0,0,first,getHeight());right.setBounds(first+gap,0,width-first,getHeight());}}
            @Override public Dimension getPreferredSize() {return new Dimension(1100,stacked()?left.getPreferredSize().height+gap+right.getPreferredSize().height:Math.max(left.getPreferredSize().height,right.getPreferredSize().height));}
            @Override public Dimension getMinimumSize() {return new Dimension(300,Math.max(left.getMinimumSize().height,right.getMinimumSize().height));}
        };p.setOpaque(false);p.add(left);p.add(right);return p;
    }
    private static JPanel section(String title, JComponent content) {
        return section(title,content,null);
    }
    private static JPanel section(String title, JComponent content,JComponent trailing) {
        JPanel p = SalesTaskVisuals.card(new BorderLayout());p.setBorder(new EmptyBorder(1,1,1,1));
        JLabel heading = label(title, 21, true); heading.setOpaque(true); heading.setBackground(DeckersPalette.background()); heading.setBorder(new EmptyBorder(11, 18, 11, 18));
        JPanel headingRow=panel(new BorderLayout());headingRow.setOpaque(true);headingRow.setBackground(DeckersPalette.background());headingRow.add(heading);
        if(trailing!=null){JPanel actions=panel(new BorderLayout());actions.setBorder(new EmptyBorder(8,8,8,12));actions.add(trailing);headingRow.add(actions,BorderLayout.EAST);}
        p.add(headingRow, BorderLayout.NORTH); content.setBorder(Boolean.TRUE.equals(content.getClientProperty("overseas.dashed"))?BorderFactory.createCompoundBorder(new EmptyBorder(16,18,16,18),BorderFactory.createDashedBorder(DeckersPalette.border(),1,4,3,true)):new EmptyBorder(16,18,16,18)); p.add(content); return p;
    }
    private static JScrollPane scroll(JComponent view) {
        JScrollPane s = new JScrollPane(view); s.setBorder(null); s.getViewport().setBackground(DeckersPalette.surface()); s.getVerticalScrollBar().setUnitIncrement(20); return s;
    }
    private JButton button(String text, Runnable run) { return makeButton(text, null, false, null, run); }
    private JButton action(String text, String permission, Runnable run) { return makeButton(text, null, false, permission, run); }
    private JButton actionLink(String text,String icon,String permission,Runnable run){JButton b=makeButton(text,icon,false,permission,run);b.putClientProperty("taskLink",true);b.setBorder(new EmptyBorder(5,0,5,0));return b;}
    private JButton primary(String text, String icon, String permission, Runnable run) { return makeButton(text, icon, true, permission, run); }
    private JButton link(String text, String icon, Runnable run) {
        JButton b = makeButton(text, icon, false, null, run); b.putClientProperty("taskLink", true); b.setBorder(new EmptyBorder(5, 0, 5, 0)); return b;
    }
    private JButton makeButton(String text, String icon, boolean primary, String permission, Runnable run) {
        JButton b = SalesTaskVisuals.button(text, icon, primary); b.setFont(font(primary?18:16, primary));if(primary)b.setBorder(new EmptyBorder(12,18,12,18)); b.setName("overseas." + text);
        if (permission != null) b.putClientProperty("permission", permission);
        b.addActionListener(e -> { if (!working) try { run.run(); } catch (Exception ex) { error(ex); }}); buttons.add(b); return b;
    }
    private void showPage(Page next, JComponent content) {
        page = next; body.removeAll();if(!working)setStatus(" "); content.setBorder(new EmptyBorder(15, 26, 25, 26));
        class PageViewport extends JPanel implements Scrollable {
            PageViewport(){super(new BorderLayout());setOpaque(false);add(content,BorderLayout.NORTH);}
            public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
            public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 24;}
            public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(24,visible.height-48);}
            public boolean getScrollableTracksViewportWidth(){return true;}
            public boolean getScrollableTracksViewportHeight(){return false;}
        }
        JScrollPane viewport = scroll(new PageViewport()); viewport.getViewport().setBackground(DeckersPalette.background());
        body.add(viewport); body.revalidate(); body.repaint(); controls();
    }
    private JPanel pageHeader(String back, Runnable backAction, String title, String subtitle, JComponent right) {
        JPanel top = panel(new BorderLayout(12, 8)); JPanel backRow=panel(new BorderLayout());backRow.add(link(back,"back",backAction),BorderLayout.WEST);top.add(backRow,BorderLayout.NORTH);
        JPanel titleRow=row(0);titleRow.add(label(title,38,true));if(right instanceof SalesTaskVisuals.Badge){titleRow.add(Box.createHorizontalStrut(16));titleRow.add(right);}headerTitles=titleRow;
        JPanel headings = stack(6, titleRow, label(subtitle, 17, false));
        JPanel line = panel(new BorderLayout(20, 0)); line.add(headings); if (right != null && !(right instanceof SalesTaskVisuals.Badge)) {JPanel aligned=panel(new BorderLayout());aligned.setBorder(new EmptyBorder(14,0,0,0));aligned.add(right,BorderLayout.NORTH);line.add(aligned,BorderLayout.EAST);}
        line.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, DeckersPalette.PURPLE), new EmptyBorder(0, 0, 22, 0)));
        top.putClientProperty("overseas.headerLine",line);top.add(line); return top;
    }
    private void showList(boolean reload) {
        buttons.clear(); page = Page.LIST;
        JPanel content = panel(new BorderLayout(0, 34));JButton create=primary("New quote","plus","QUOTE",this::fresh);create.setPreferredSize(new Dimension(256,56));
        content.add(stack(33, pageHeader("Back to main menu", this::leaveWorkspace, "Overseas Orders", "Find a quote or follow an order.", create), buildSearch()), BorderLayout.NORTH);
        JPanel tableArea = panel(new BorderLayout(0, 18));
        JScrollPane tableScroll = scroll(orders); tableScroll.setBorder(BorderFactory.createLineBorder(DeckersPalette.border())); tableScroll.setPreferredSize(new Dimension(1200, 400));orders.getTableHeader().setPreferredSize(new Dimension(500,56));
        JPanel foot = panel(new BorderLayout()); foot.add(listCount, BorderLayout.WEST);
        foot.add(label("Open an order to view items, record payments and update shipment progress.", 14, false), BorderLayout.EAST);
        tableArea.add(stack(18,tableScroll,foot), BorderLayout.NORTH); content.add(tableArea);
        showPage(Page.LIST, content); if (reload) refresh();
    }
    private JPanel buildSearch() {
        JPanel bar = panel(new GridBagLayout()); GridBagConstraints g = new GridBagConstraints();
        g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1; bar.add(search, g);
        search.putClientProperty("JTextField.placeholderText", "Search order, customer or phone"); search.setToolTipText("Search order, customer or phone");
        search.putClientProperty("overseas.searchIcon",true);search.setBorder(BorderFactory.createCompoundBorder(new javax.swing.border.LineBorder(DeckersPalette.border(),1,true),new EmptyBorder(2,40,2,10)));
        g.weightx = 0; g.insets = new Insets(0, 18, 0, 0); filter.setFont(font(16, false)); filter.setPreferredSize(new Dimension(260, 49)); bar.add(filter, g);
        JButton find=primary("Search",null,null,this::refresh);find.putClientProperty("taskAccent",DeckersPalette.PURPLE);find.setPreferredSize(new Dimension(144,49));bar.add(find,g);
        JPanel limited=new JPanel(null){@Override public Dimension getPreferredSize(){return new Dimension(1090,49);}@Override public void doLayout(){bar.setBounds(0,0,Math.min(getWidth(),1090),49);}};limited.setOpaque(false);limited.add(bar);return limited;
    }
    private void refresh() {
        if (working) return;
        JsonObject b = request("LIST"); b.addProperty("search", search.getText());
        b.addProperty("filter", new String[]{"ACTIVE", "ALL", "UNPAID", "ARRIVED", "COMPLETED"}[filter.getSelectedIndex()]);
        call(b, data -> {
            permissions = permissions(data); rows.clear(); ordersModel.setRowCount(0);
            for (JsonElement e : data.getAsJsonArray("rows")) {
                JsonObject r = e.getAsJsonObject(); rows.add(r);
                String tracking = s(r, "tracking"); if (tracking.isBlank()) tracking = s(r, "state");
                ordersModel.addRow(new Object[]{s(r, "number"), s(r, "customerName"), trackingLabel(tracking), money(r, "total"), money(r, "paid"), money(r, "balance"), "Open"});
                if (s(r, "id").equals(selectedOrderId)) orders.setRowSelectionInterval(rows.size() - 1, rows.size() - 1);
            }
            listCount.setText(rows.size() + " orders shown");
            setStatus(rows.isEmpty() ? "No matching orders. Try another search or create a new quote." : " ");
        });
    }
    private void openSelectedOrder() {
        if (working || orders.getSelectedRow() < 0) return;
        JsonObject b = request("DETAIL"); b.add("id", rows.get(orders.getSelectedRow()).get("id")); detailTab = 0;
        call(b, this::showDetail);
    }
    private void leaveWorkspace() { if (confirmDiscard()) managers.NavigationManager.showMainMenu(this); }
    private void backToList() {
        if (confirmDiscard()) { itemIndex = -1; itemBaseline = null; stagedImages.clear(); savedDraft = null; showList(true); }
    }
    private void fresh() {
        if (!confirmDiscard()) return;
        loading = true; order = null; detail = null; customerId = null; editingIssued = false; itemBaseline = null;
        name.setText(""); phone.setText(""); expected.setText(""); shipping.setText("0.00"); notes.setText("");
        lineValues.clear(); stagedImages.clear(); savedDraft = draft().deepCopy(); loading = false; showQuote();
    }
    private void showDetail(JsonObject data) {
        loading = true; detail = data; order = data.getAsJsonObject("order"); permissions = permissions(data);
        selectedOrderId=s(order,"overseas_order_id");
        customerId = !order.has("customer_id") || order.get("customer_id").isJsonNull() ? null : order.get("customer_id").getAsInt();
        name.setText(s(order, "customer_name")); phone.setText(s(order, "customer_phone")); expected.setText(s(order, "expected_date"));
        shipping.setText(s(order, "shipping")); notes.setText(s(order, "notes"));
        lineValues.clear(); for (JsonElement e : order.getAsJsonArray("lines")) lineValues.add(e.getAsJsonObject().deepCopy());
        savedDraft = draft().deepCopy(); editingIssued = false; itemBaseline = null; loading = false;
        if ("DRAFT".equals(s(order, "state"))) showQuote(); else showOrder();
    }
    private void showOrder() {
        buttons.clear(); page = Page.DETAIL; renderLines();
        JPanel content = panel(new BorderLayout(0, 24));
        JPanel top = pageHeader("Back to overseas orders", this::backToList, s(order, "number"), "Customer:  " + s(order, "customer_name") + "     |     " + s(order, "customer_phone"), moreButton());
        ((JComponent)top.getClientProperty("overseas.headerLine")).setBorder(new EmptyBorder(0,0,22,0));
        content.add(top, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane(){@Override public void updateUI(){super.updateUI();installOrderTabs(this);}}; tabs.setFont(font(18, true)); tabs.setForeground(DeckersPalette.PURPLE);tabs.setBackground(DeckersPalette.surface());tabs.setOpaque(false);
        tabs.addTab("Items", buildOrderItems()); tabs.addTab("Payments", buildPayments()); tabs.addTab("Activity", buildActivity());
        tabs.setSelectedIndex(Math.min(detailTab, 2)); tabs.addChangeListener(e -> detailTab = tabs.getSelectedIndex());
        headerTitles.add(Box.createHorizontalStrut(18));headerTitles.add(badge(overallTracking(),DeckersPalette.PURPLE));
        content.add(columns(tabs, paymentSummary(), .74, 22)); showPage(Page.DETAIL, content);
    }
    private static void installOrderTabs(JTabbedPane tabs){
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI(){
            @Override protected void installDefaults(){super.installDefaults();tabInsets=new Insets(12,44,12,44);contentBorderInsets=new Insets(20,0,0,0);}
            @Override protected void paintContentBorder(Graphics g,int placement,int selected){if(rects.length>0){Rectangle tab=rects[Math.max(0,selected)];g.setColor(DeckersPalette.PURPLE);g.fillRect(0,tab.y+tab.height-1,tabs.getWidth(),2);}}
            @Override protected void paintTabBorder(Graphics g,int placement,int index,int x,int y,int w,int h,boolean selected){g.setColor(selected?DeckersPalette.PURPLE:DeckersPalette.border());g.drawRoundRect(x,y,w-1,h+4,6,6);}
            @Override protected void paintTabBackground(Graphics g,int placement,int index,int x,int y,int w,int h,boolean selected){g.setColor(selected?DeckersPalette.PURPLE:DeckersPalette.surface());g.fillRoundRect(x,y,w,h+4,6,6);tabs.setForegroundAt(index,selected?Color.WHITE:DeckersPalette.text());}
        });
    }
    private JComponent buildOrderItems() {
        JPanel customer = stack(10, label(s(order, "customer_name"), 17, true), label(s(order, "customer_phone"), 16, false));
        JPanel delivery = stack(8, label((lineValues.stream().allMatch(l->l.has("digital")&&l.get("digital").getAsBoolean())?"Digital codes; no physical delivery. Reference date: ":"Expected arrival: ") + (s(order, "expected_date").isBlank() ? "Not set" : s(order, "expected_date")), 16, false), wrap(s(order, "notes"), 15));
        JPanel table = panel(new BorderLayout(0, 14)); JScrollPane itemScroll = scroll(items); itemScroll.setPreferredSize(new Dimension(850, 198)); table.add(itemScroll);
        JPanel links = panel(new BorderLayout(12, 0)); links.setBorder(new EmptyBorder(12, 14, 12, 14)); links.setOpaque(true); links.setBackground(DeckersPalette.tileFill(DeckersPalette.PURPLE));
        links.add(selectedItem); JPanel actions = row(18); actions.add(link("View screenshot", "image", this::preview)); actions.add(link("Open product link", "external-link", this::openProductLink)); links.add(actions, BorderLayout.EAST); table.add(links, BorderLayout.SOUTH);
        JPanel shipment = panel(new BorderLayout(12, 8)); shipment.setBorder(new EmptyBorder(16, 12, 16, 12)); shipment.add(label("Shipment progress", 18, true), BorderLayout.WEST);
        JPanel progress=panel(new GridLayout(1,4,10,0));String[] stages={"Ordered","Purchased","Shipped","Arrived"},keys={"purchased","purchased","shipped","arrived"};
        for(int i=0;i<stages.length;i++){final String key=keys[i];boolean complete=!lineValues.isEmpty()&&lineValues.stream().allMatch(l->(l.has("digital")&&l.get("digital").getAsBoolean()&&!key.equals("purchased"))||integer(l,key)==integer(l,"quantity"));JLabel step=label(stages[i],14,false);step.setHorizontalAlignment(SwingConstants.CENTER);step.setVerticalTextPosition(SwingConstants.BOTTOM);step.setHorizontalTextPosition(SwingConstants.CENTER);step.setIcon(SalesTaskVisuals.icon(complete?"check-circle":"circle",complete?DeckersPalette.PURPLE:DeckersPalette.border(),20));step.setIconTextGap(8);step.setToolTipText(shipmentSummary());progress.add(step);}
        if(lineValues.stream().allMatch(l->l.has("digital")&&l.get("digital").getAsBoolean())){progress.removeAll();progress.add(label("Digital codes: no physical delivery. Fulfilled once ordered and paid.",14,false));} shipment.add(progress); shipment.add(action("Mark ordered", "SHIP", () -> track("ORDERED")), BorderLayout.EAST);
        return stack(22, disclosure("Customer details", customer), disclosure("Delivery details", delivery), table, shipment);
    }
    private String overallTracking() {
        if (lineValues.isEmpty()) return trackingLabel(s(order, "state"));
        String first = s(lineValues.get(0), "tracking");
        return lineValues.stream().allMatch(l -> s(l, "tracking").equals(first)) ? trackingLabel(first) : "In progress";
    }
    private String shipmentSummary() {
        int purchased = 0, shipped = 0, arrived = 0, collected = 0;
        for (JsonObject l : lineValues) { if(l.has("digital")&&l.get("digital").getAsBoolean())continue; purchased += integer(l, "purchased"); shipped += integer(l, "shipped"); arrived += integer(l, "arrived"); collected += integer(l, "collected"); }
        return "Purchased: " + purchased + "     Shipped: " + shipped + "     Arrived: " + arrived + "     Collected: " + collected;
    }
    private JComponent paymentSummary() {
        JPanel summary = stack(22, amountRow("Total", "GYD "+money(order, "total"), false), amountRow("Paid", "GYD "+money(order, "paid"), false), amountRow("Balance due", "GYD "+money(order, "balance"), true), primary("Take payment", "payment", "PAYMENT", () -> payment(false, false)), link("Print receipt", "print", this::print));
        JPanel balance=(JPanel)summary.getComponent(2);balance.setOpaque(true);balance.setBackground(DeckersPalette.tileFill(DeckersPalette.PURPLE));
        JPanel section = section("Payment summary", summary);
        JPanel aligned = panel(new BorderLayout());aligned.setBorder(new EmptyBorder(78,0,0,0)); aligned.add(section, BorderLayout.NORTH); return aligned;
    }
    private JComponent buildPayments() {
        DefaultTableModel m = model("Date / time", "Action", "Amount (GYD)", "Method", "Taken by");
        for (JsonElement e : detail.getAsJsonArray("payments")) { JsonObject p = e.getAsJsonObject(); m.addRow(new Object[]{s(p, "created_at"), s(p, "payment_action"), money(p, "payment_amount"), s(p, "payment_method"), s(p, "taken_by_name")}); }
        JTable t = new JTable(m); styleTable(t, 46); JScrollPane s = scroll(t); s.setPreferredSize(new Dimension(850, 250));
        return stack(18, label("Payment history", 22, true), m.getRowCount() == 0 ? label("No payments have been recorded.", 16, false) : s, link("Print slip / receipt", "print", this::print));
    }
    private JComponent buildActivity() {
        DefaultTableModel m = model("Date / time", "Action", "By", "Details");
        for (JsonElement e : detail.getAsJsonArray("events")) { JsonObject v = e.getAsJsonObject(); m.addRow(new Object[]{s(v, "at"), s(v, "action"), s(v, "actor"), s(v, "reason")}); }
        JTable t = new JTable(m); styleTable(t, 46); JScrollPane s = scroll(t); s.setPreferredSize(new Dimension(850, 320)); return s;
    }
    private JButton moreButton() {
        JButton b = button("More actions", () -> {}); b.setIcon(SalesTaskVisuals.icon("down", DeckersPalette.muted(), 12));
        b.addActionListener(e -> { if (working) return; JPopupMenu menu = new JPopupMenu();
            menuItem(menu, "Edit quote", "QUOTE", () -> { editingIssued = true; showQuote(); });
            menuItem(menu, "Arrived at Deckers", "SHIP", () -> track("ARRIVED"));
            menuItem(menu, "Shipment quantities", "SHIP", () -> quantities(false));
            menuItem(menu, "Collect items", "COLLECT", () -> quantities(true));
            menuItem(menu, "Print slip / receipt", null, this::print);
            menu.addSeparator(); menuItem(menu, "Refund payment", "ADJUST", () -> payment(true, false));
            menuItem(menu, "Cancel order", "ADJUST", () -> change("CANCEL", reason(), false)); menu.show(b, 0, b.getHeight()); }); return b;
    }
    private void menuItem(JPopupMenu menu, String title, String permission, Runnable action) {
        JMenuItem item = new JMenuItem(title); item.setFont(font(16, false)); item.setEnabled(permission == null || permissions.contains("OVERSEAS_ORDERS_" + permission));
        item.addActionListener(e -> { try { action.run(); } catch (Exception ex) { error(ex); }}); menu.add(item);
    }
    private void showQuote() {
        buttons.clear(); page = Page.QUOTE; renderLines();
        JPanel content = panel(new BorderLayout(0, 24));
        content.add(pageHeader("Back to overseas orders", this::backToList, editingIssued ? "Edit overseas quote" : "New Overseas Quote", "Add the customer and items, then review the total.", badge(editingIssued ? "Issued quote" : "Draft", DeckersPalette.PURPLE)), BorderLayout.NORTH);
        JPanel customerFields = columns(labeled("Customer name", name), labeled("Phone / WhatsApp", phone), .5, 24);
        JPanel customer = panel(new BorderLayout(18, 12)); customer.add(customerFields);JPanel find=panel(new BorderLayout());find.setBorder(new EmptyBorder(29,0,0,0));JButton findButton=action("Find customer","QUOTE",this::findCustomer);findButton.setIcon(SalesTaskVisuals.icon("search",DeckersPalette.muted(),18));find.add(findButton,BorderLayout.NORTH);customer.add(find,BorderLayout.EAST);
        updateCustomerHint();customer.add(stack(8, customerHint, phoneMatches), BorderLayout.SOUTH);
        JPanel delivery = stack(14, labeled("Expected arrival (YYYY-MM-DD)", expected), labeled("Notes", scroll(notes)));
        JPanel itemPanel = panel(new BorderLayout(0, 12));
        JScrollPane itemScroll = scroll(items); itemScroll.setPreferredSize(new Dimension(850, 185)); itemPanel.add(itemScroll);
        JPanel itemActions = row(20);itemActions.setBorder(new EmptyBorder(8,0,8,0));itemActions.setOpaque(true);itemActions.setBackground(DeckersPalette.tileFill(DeckersPalette.PURPLE)); itemActions.add(actionLink("Edit item","edit","QUOTE", () -> editLine(selectedLine()))); itemActions.add(actionLink("Remove","trash","QUOTE",this::removeLine)); itemActions.add(link("View screenshot", "image", this::preview)); itemActions.add(link("Product link", "external-link", this::openProductLink)); itemPanel.add(itemActions, BorderLayout.SOUTH);
        JPanel left = stack(22, section("Customer", customer), disclosure("Delivery & notes (optional)", delivery), section("Items", itemPanel,action("Add item","QUOTE",()->editLine(-1))));
        JPanel shippingRow = columns(label("Extra shipping (optional)", 15, false), shipping, .65, 8);
        JPanel summary = stack(20, amountRow("Items", quoteItems, false), shippingRow, wrap("Item prices already include shipping.", 15), amountRow("Total", totals, true), wrap(editingIssued ? "Save your changes to update this quote." : "Issue this quote when ready. Payment is recorded separately at the register.", 16), primary(editingIssued ? "Save changes" : "Issue quote", "save", "QUOTE", editingIssued ? this::save : this::issue), action("Save draft", "QUOTE", this::save));
        if (editingIssued) summary.getComponent(summary.getComponentCount() - 1).setVisible(false);
        JPanel right = panel(new BorderLayout()); right.add(section("Quote summary", summary), BorderLayout.CENTER);
        content.add(columns(left, right, .725, 22)); showPage(Page.QUOTE, content); updateTotals();
    }
    private static JPanel amountRow(String title, String value, boolean prominent) { return amountRow(title, label(value, prominent ? 22 : 17, prominent), prominent); }
    private static JPanel amountRow(String title, JLabel value, boolean prominent) {
        JPanel p = panel(new BorderLayout(12, 0)); JLabel l = label(title, prominent ? 21 : 17, prominent);
        if (prominent) { l.setForeground(DeckersPalette.PURPLE); value.setForeground(DeckersPalette.PURPLE); p.setBorder(new EmptyBorder(10, 0, 10, 0)); }
        p.add(l, BorderLayout.WEST); p.add(value, BorderLayout.EAST); return p;
    }
    private JPanel disclosure(String title, JComponent content) {
        JPanel p = panel(new BorderLayout()); JButton toggle = button(title, () -> {}); toggle.setHorizontalAlignment(SwingConstants.LEFT); toggle.setIcon(SalesTaskVisuals.icon("down", DeckersPalette.muted(), 12)); toggle.setFont(font(17, true));
        content.setVisible(false); toggle.addActionListener(e -> { content.setVisible(!content.isVisible()); p.revalidate(); }); p.add(toggle, BorderLayout.NORTH); p.add(content); return p;
    }
    private static JTextArea wrap(String text, int size) {
        JTextArea area = new JTextArea(text); area.setFont(font(size, false)); area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); area.setOpaque(false); area.setForeground(DeckersPalette.muted()); area.setRows(Math.max(1, text.length() / 42 + 1)); return area;
    }
    private void renderLines() {
        boolean quote = page == Page.QUOTE;
        itemsModel.setColumnIdentifiers(quote ? new String[]{"Item", "Seller", "Options", "Qty", "Unit price (GYD)", "Amount (GYD)"} : new String[]{"Item", "Seller", "Options", "Qty", "Amount (GYD)", "Status"}); itemsModel.setRowCount(0);
        for (JsonObject l : lineValues) {
            BigDecimal subtotal = decimal(l, "unit_price").multiply(decimal(l, "quantity"));
            itemsModel.addRow(new Object[]{s(l, "description") + (l.has("digital") && l.get("digital").getAsBoolean() ? " [Digital]" : ""), s(l, "seller"), s(l, "options"), s(l, "quantity"), quote ? money(l, "unit_price") : format(subtotal), quote ? format(subtotal) : trackingLabel(s(l, "tracking"))});
        }
        int[] widths={220,120,110,60,175,165};for(int i=0;i<widths.length;i++)items.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        if (!lineValues.isEmpty()) items.setRowSelectionInterval(0, 0); updateTotals(); updateSelectedItem();
    }
    private void updateSelectedItem() { int i = items.getSelectedRow(); selectedItem.setText(i >= 0 && i < lineValues.size() ? "Selected item: " + s(lineValues.get(i), "description") + " — " + s(lineValues.get(i), "options") : "Select an item to view its screenshot or product link."); }
    private void updateTotals() {
        BigDecimal total = BigDecimal.ZERO; for (JsonObject l : lineValues) total = total.add(decimal(l, "unit_price").multiply(decimal(l, "quantity"))); quoteItems.setText(format(total));
        try { total = total.add(new BigDecimal(shipping.getText().isBlank() ? "0" : shipping.getText().trim())); totals.setText("GYD " + format(total)); } catch (NumberFormatException e) { totals.setText("Check shipping"); }
    }
    private JsonObject draft() {
        JsonObject o = new JsonObject(); o.addProperty("customer_name", name.getText()); o.addProperty("customer_phone", phone.getText()); o.addProperty("customer_id", customerId);
        o.addProperty("expected_date", expected.getText()); o.addProperty("shipping", new BigDecimal(shipping.getText().isBlank() ? "0" : shipping.getText().trim())); o.addProperty("notes", notes.getText());
        JsonArray l = new JsonArray(); lineValues.forEach(v -> l.add(v.deepCopy())); o.add("lines", l); return o;
    }
    private static DefaultTableModel model(String... columns) { return new DefaultTableModel(columns, 0) { @Override public boolean isCellEditable(int r, int c) { return false; }}; }
    private static void styleTable(JTable table, int height) {
        DeckersSwing.styleTable(table, DeckersPalette.PURPLE); table.setFont(font(16, false)); table.setRowHeight(height); table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setShowHorizontalLines(true); table.setIntercellSpacing(new Dimension(0, 1)); table.setFillsViewportHeight(false); table.getTableHeader().setFont(font(16, true)); table.getTableHeader().setPreferredSize(new Dimension(500, 50)); table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable t,Object v,boolean selected,boolean focus,int r,int c){super.getTableCellRendererComponent(t,v,false,false,r,c);setFont(font(17,true));setBackground(DeckersPalette.background());setForeground(DeckersPalette.text());setBorder(new EmptyBorder(0,14,0,14));setHorizontalAlignment(t.getColumnName(c).contains("GYD")?RIGHT:LEFT);return this;}});
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean selected, boolean focus, int r, int c) {
                super.getTableCellRendererComponent(t, v, selected, focus, r, c); setFont(font(c==0&&t.getColumnCount()==7?18:16, c == 0 && t.getColumnCount() == 7)); setBorder(new EmptyBorder(0, 16, 0, 16));
                setBackground(selected ? DeckersPalette.tilePressed(DeckersPalette.PURPLE) : r % 2 == 0 ? DeckersPalette.surface() : DeckersPalette.tableStripe()); setForeground("Open".equals(v) ? DeckersPalette.PURPLE : DeckersPalette.text());
                setHorizontalAlignment(t.getColumnName(c).contains("GYD") ? RIGHT : LEFT);
                if(t.getColumnName(c).equals("Status")){
                    String text=Objects.toString(v,"");String shortText=text.startsWith("Arrived at")?"Arrived":text.startsWith("Partially arrived")?"Partially arrived":text;
                    JPanel cell=panel(new FlowLayout(FlowLayout.LEFT,14,Math.max(2,(t.getRowHeight()-34)/2)));cell.setOpaque(true);cell.setBackground(getBackground());
                    Color accent=shortText.equals("Arrived")||shortText.equals("Collected")?new Color(22,135,60):shortText.equals("Waiting for payment")?DeckersPalette.ORANGE:shortText.equals("Draft")?DeckersPalette.muted():DeckersPalette.PURPLE;
                    JLabel badge=new SalesTaskVisuals.Badge(shortText,accent);badge.setFont(font(14,true));badge.setToolTipText(text);cell.add(badge);return cell;
                }
                setToolTipText(Objects.toString(v,""));return this;
            }
        });
    }
    private static int integer(JsonObject o, String key) { return decimal(o, key).intValue(); }
    private static BigDecimal decimal(JsonObject o, String key) { return !o.has(key) || o.get(key).isJsonNull() ? BigDecimal.ZERO : o.get(key).getAsBigDecimal(); }
    private static String format(BigDecimal value) { return String.format(Locale.US, "%,.2f", value); }
    private static String money(JsonObject o, String key) { return format(decimal(o, key)); }
    private static void onChange(JTextField field, Runnable run) { field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() { public void insertUpdate(javax.swing.event.DocumentEvent e) { run.run(); } public void removeUpdate(javax.swing.event.DocumentEvent e) { run.run(); } public void changedUpdate(javax.swing.event.DocumentEvent e) { run.run(); }}); }
    private static JsonObject request(String action) { JsonObject b = new JsonObject(); b.addProperty("action", action); return b; }
    private int selectedLine() { int i = items.getSelectedRow(); if (i < 0) throw new IllegalArgumentException("Select an item first."); return i; }
    private void editLine(int index) {
        itemIndex = index; itemOriginal = index < 0 ? new JsonObject() : lineValues.get(index).deepCopy();
        itemImage = index < 0 ? null : stagedImages.get(index); itemFields.clear(); buttons.clear();
        String[] keys = {"description", "seller", "options", "product_url", "quantity", "unit_price", "usd_cost", "exchange_rate"};
        for (String key : keys) itemFields.put(key, field(s(itemOriginal, key)));
        if(!s(itemOriginal,"unit_price").isBlank())itemFields.get("unit_price").setText(money(itemOriginal,"unit_price"));
        if (index < 0) { itemFields.get("quantity").setText("1"); itemFields.get("unit_price").setText("0.00"); }
        JPanel content = panel(new BorderLayout(0, 24));
        content.add(pageHeader("Back to quote", this::cancelItem, index < 0 ? "Add item" : "Edit item", "Describe the item and set its quoted price.", badge("New overseas quote · " + name.getText(), DeckersPalette.PURPLE)), BorderLayout.NORTH);
        sellerChoices = new JComboBox<>(); sellerChoices.setEditable(true); sellerChoices.setFont(font(16, false)); sellerChoices.setPreferredSize(new Dimension(180, 43));
        sellerChoices.setEditor(new javax.swing.plaf.basic.BasicComboBoxEditor() {
            @Override public Component getEditorComponent() { return itemFields.get("seller"); }
            @Override public Object getItem() { return itemFields.get("seller").getText(); }
            @Override public void setItem(Object value) { itemFields.get("seller").setText(value == null ? "" : value.toString()); }
        });
        sellerChoices.setSelectedItem(s(itemOriginal, "seller"));
        JPanel options = stack(6, labeled("Options", itemFields.get("options")), wrap("Colour, size or other details.", 14));
        digitalItem = new JCheckBox("Digital item (code; no physical delivery)", itemOriginal.has("digital") && itemOriginal.get("digital").getAsBoolean());
        JPanel fields = stack(22, digitalItem, labeled("Item description", itemFields.get("description")), columns(labeled("Seller", sellerChoices), options, .46, 26), labeled("Product link (optional)", itemFields.get("product_url")));
        JPanel price = stack(20, columns(labeled("Quantity", itemFields.get("quantity")), labeled("Unit price (GYD)", itemFields.get("unit_price")), .3, 26), wrap("Customer quote price. Include shipping in this price.", 14), disclosure("Purchase cost (optional)", stack(14, labeled("USD cost each (optional)", itemFields.get("usd_cost")), labeled("Exchange rate (reference only)", itemFields.get("exchange_rate")))));
        JPanel left = stack(22, section("Item details", fields), section("Pricing", price));
        imagePreview = label("No screenshot selected", 15, false); imagePreview.setHorizontalAlignment(SwingConstants.CENTER); imagePreview.setPreferredSize(new Dimension(230, 55));
        JButton choose = action("Choose screenshot", "QUOTE", () -> chooseItemImage(false));
        JButton paste = action("Paste clipboard image", "QUOTE", () -> chooseItemImage(true));paste.putClientProperty("taskLink",true);paste.setBorder(new EmptyBorder(2,0,2,0));
        JTextArea screenshotHint=wrap("Attach the exact item, colour and size.",14);screenshotHint.setRows(1);
        JPanel chooseRow=panel(new FlowLayout(FlowLayout.CENTER,0,0));choose.setPreferredSize(new Dimension(235,43));chooseRow.add(choose);
        JPanel screenshot = stack(10, imagePreview, chooseRow, paste, screenshotHint);screenshot.putClientProperty("overseas.dashed",true);
        itemTotal = label("GYD 0.00", 22, true);
        JLabel quantitySummary=label(itemFields.get("quantity").getText(),17,false),unitSummary=label(formatInputMoney(itemFields.get("unit_price").getText()),17,true);
        JPanel summary = stack(18, amountRow("Quantity",quantitySummary,false),amountRow("Unit price",unitSummary,false), amountRow("Item total", itemTotal, true), primary(index < 0 ? "Add to quote" : "Save item", "plus", "QUOTE", this::acceptItem), button("Cancel", this::cancelItem));
        JPanel right = stack(22, section("Screenshot (optional)", screenshot), section("Item summary", summary));
        JPanel aligned = panel(new BorderLayout()); aligned.add(right, BorderLayout.NORTH);
        content.add(columns(left, aligned, .69, 22));
        itemBaseline = itemInput();
        onChange(itemFields.get("quantity"),()->{quantitySummary.setText(itemFields.get("quantity").getText());updateItemTotal();}); onChange(itemFields.get("unit_price"),()->{unitSummary.setText(formatInputMoney(itemFields.get("unit_price").getText()));updateItemTotal();});
        updateItemTotal(); updateItemImage(); showPage(Page.ITEM, content);
        JComboBox<String> target = sellerChoices;
        new SwingWorker<JsonObject, Void>() {
            @Override protected JsonObject doInBackground() throws Exception { return transport.send(request("SELLERS")); }
            @Override protected void done() {
                if (page != Page.ITEM || target != sellerChoices || !isDisplayable()) return;
                try { String typed = itemFields.get("seller").getText(); for (JsonElement seller : get().getAsJsonArray("sellers")) target.addItem(seller.getAsString()); itemFields.get("seller").setText(typed); }
                catch (Exception ignored) { target.setToolTipText("Saved sellers unavailable. You can still type a seller name."); }
            }
        }.execute();
    }
    private JsonObject itemInput() { JsonObject o = new JsonObject(); itemFields.forEach((key, f) -> o.addProperty(key, f.getText())); return o; }
    private boolean itemDirty() { return page == Page.ITEM && itemBaseline != null && (!itemBaseline.equals(itemInput()) || !Arrays.equals(itemImage, itemIndex < 0 ? null : stagedImages.get(itemIndex))); }
    private void cancelItem() {
        if (itemDirty() && JOptionPane.showConfirmDialog(this, "Discard changes to this item?", "Unsaved item", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        itemBaseline = null; showQuote();
    }
    private void acceptItem() {
        JsonObject l = itemOriginal.deepCopy();
        String description = itemFields.get("description").getText().trim();
        if (description.isBlank()) throw new IllegalArgumentException("Enter an item description.");
        int quantity;
        BigDecimal price;
        try { quantity = Integer.parseInt(itemFields.get("quantity").getText().trim()); price = parseAmount(itemFields.get("unit_price").getText()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a whole quantity and a valid unit price."); }
        if (quantity < 1 || quantity > 100000) throw new IllegalArgumentException("Quantity must be between 1 and 100,000.");
        if (price.signum() < 0 || price.scale() > 2) throw new IllegalArgumentException("Enter a non-negative unit price with at most two decimal places.");
        for (String key : List.of("description", "seller", "options", "product_url")) l.addProperty(key, itemFields.get(key).getText().trim());
        String url = s(l, "product_url");
        if (!url.isBlank()) { java.net.URI uri; try { uri = java.net.URI.create(url); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Enter a valid product link."); } if (!Set.of("https", "http").contains(Objects.toString(uri.getScheme(),"")) || uri.getHost() == null || uri.getUserInfo()!=null) throw new IllegalArgumentException("Product links must start with https:// or http:// and cannot include login details."); }
        l.addProperty("digital", digitalItem.isSelected());
        l.addProperty("quantity", quantity); l.addProperty("unit_price", price);
        for (String key : List.of("usd_cost", "exchange_rate")) {
            String text = itemFields.get(key).getText().trim();
            if (text.isBlank()) l.add(key, JsonNull.INSTANCE);
            else { BigDecimal value; try { value = new BigDecimal(text); } catch (NumberFormatException e) { throw new IllegalArgumentException("Enter a valid purchase cost and exchange rate."); } if (value.signum() < 0 || (key.equals("exchange_rate")&&value.signum()==0) || value.compareTo(new BigDecimal("99999999"))>0) throw new IllegalArgumentException("Enter a valid purchase cost and a positive exchange rate."); l.addProperty(key, value); }
        }
        for (String key : List.of("purchased", "shipped", "arrived", "collected")) if (!l.has(key)) l.addProperty(key, 0);
        if (itemIndex < 0) { itemIndex = lineValues.size(); lineValues.add(l); } else lineValues.set(itemIndex, l);
        if (itemImage != null) stagedImages.put(itemIndex, itemImage);
        int selected = itemIndex; itemBaseline = null; showQuote(); items.setRowSelectionInterval(selected, selected);
    }
    private void updateItemTotal() {
        if (itemTotal == null) return;
        try { itemTotal.setText("GYD " + format(parseAmount(itemFields.get("unit_price").getText()).multiply(new BigDecimal(itemFields.get("quantity").getText().trim())))); }
        catch (NumberFormatException e) { itemTotal.setText("Check quantity / price"); }
    }
    private static BigDecimal parseAmount(String text){String value=text.trim();if(value.contains(",")&&!value.matches("[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]+)?"))throw new NumberFormatException("Invalid grouping");return new BigDecimal(value.replace(",",""));}
    private static String formatInputMoney(String text){try{return "GYD "+format(parseAmount(text));}catch(NumberFormatException e){return "Check price";}}
    private void updateCustomerHint(){customerHint.setText(customerId==null?"Find an existing customer or enter a new customer's details.":"Existing customer linked.");customerHint.setIcon(customerId==null?null:SalesTaskVisuals.icon("check-circle",new Color(22,135,60),20));customerHint.setIconTextGap(10);}
    private void chooseItemImage(boolean clipboard) {
        try {
            byte[] bytes;
            if (clipboard) {
                if (!Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(DataFlavor.imageFlavor)) throw new IllegalArgumentException("Copy an image to the clipboard first.");
                Image image = (Image) Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.imageFlavor);
                BufferedImage out = new BufferedImage(image.getWidth(null), image.getHeight(null), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = out.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, out.getWidth(), out.getHeight()); g.drawImage(image, 0, 0, null); g.dispose();
                ByteArrayOutputStream bytesOut = new ByteArrayOutputStream(); javax.imageio.ImageIO.write(out, "png", bytesOut); bytes = bytesOut.toByteArray();
            } else {
                JFileChooser chooser = new JFileChooser(); chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG or JPEG screenshot", "png", "jpg", "jpeg"));
                if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
                if (Files.size(chooser.getSelectedFile().toPath()) > 2000000) throw new IllegalArgumentException("Screenshot must be under 2 MB."); bytes = Files.readAllBytes(chooser.getSelectedFile().toPath());
            }
            if (bytes.length > 2000000) throw new IllegalArgumentException("Screenshot must be under 2 MB.");
            try (var input = javax.imageio.ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = javax.imageio.ImageIO.getImageReaders(input); if (!readers.hasNext()) throw new IllegalArgumentException("Choose a PNG or JPEG screenshot.");
                var reader = readers.next();
                try { reader.setInput(input); if (!Set.of("png", "jpeg", "jpg").contains(reader.getFormatName().toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Choose a PNG or JPEG screenshot."); if (reader.getWidth(0) > 6000 || reader.getHeight(0) > 6000 || (long) reader.getWidth(0) * reader.getHeight(0) > 20000000) throw new IllegalArgumentException("Screenshot dimensions are too large."); } finally { reader.dispose(); }
            }
            itemImage = bytes; updateItemImage();
        } catch (Exception e) { error(e); }
    }
    private void updateItemImage() {
        imagePreview.setIcon(null);
        if (itemImage != null) { ImageIcon icon = new ImageIcon(itemImage); imagePreview.setText(""); imagePreview.setIcon(new ImageIcon(DeckersLogoManager.scaleToFit(icon.getImage(), 280, 110))); }
        else {imagePreview.setText(s(itemOriginal,"screenshot").isBlank()?"":"Screenshot already attached");imagePreview.setIcon(SalesTaskVisuals.icon("image",DeckersPalette.muted(),44));}
    }
    private void removeLine() {
        int index = selectedLine();
        if (integer(lineValues.get(index), "purchased") > 0) throw new IllegalArgumentException("Purchased items cannot be removed.");
        if (JOptionPane.showConfirmDialog(this, "Remove this item from the quote?", "Remove item", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        lineValues.remove(index); Map<Integer, byte[]> shifted = new HashMap<>(); stagedImages.forEach((i, bytes) -> { if (i != index) shifted.put(i > index ? i - 1 : i, bytes); }); stagedImages.clear(); stagedImages.putAll(shifted); renderLines();
    }
    private void preview() {
        int index = selectedLine(); byte[] local = stagedImages.get(index);
        if (local != null) { displayImage(local); return; }
        String ref = s(lineValues.get(index), "screenshot"); if (ref.isBlank()) throw new IllegalArgumentException("This item has no screenshot.");
        JsonObject b = request("IMAGE"); b.add("id", order.get("overseas_order_id")); b.addProperty("reference", ref);
        call(b, result -> displayImage(Base64.getDecoder().decode(result.get("data").getAsString().split(",", 2)[1])));
    }
    private void displayImage(byte[] bytes) {
        ImageIcon icon = new ImageIcon(bytes);
        JOptionPane.showMessageDialog(this, new JScrollPane(new JLabel(new ImageIcon(DeckersLogoManager.scaleToFit(icon.getImage(), 850, 650)))), "Item screenshot", JOptionPane.PLAIN_MESSAGE);
    }
    private void openProductLink() {
        String url = s(lineValues.get(selectedLine()), "product_url");
        if (url.isBlank()) throw new IllegalArgumentException("This item has no product link.");
        java.net.URI uri = java.net.URI.create(url);
        if (!Set.of("https", "http").contains(Objects.toString(uri.getScheme(),"")) || uri.getHost() == null || uri.getUserInfo()!=null) throw new IllegalArgumentException("Invalid product link.");
        try { Desktop.getDesktop().browse(uri); } catch (Exception e) { error(e); }
    }
    private void issue() { persist(true); }
    private void save() { persist(false); }
    private void persist(boolean issueAfterSave) {
        if (name.getText().isBlank() || phone.getText().isBlank()) throw new IllegalArgumentException("Enter the customer name and phone number.");
        if (lineValues.isEmpty()) throw new IllegalArgumentException("Add at least one item to the quote.");
        JsonObject payload = new JsonObject(); payload.add("order", draft());
        if (order != null && !s(order, "custom_order_id").isBlank() && (decimal(order, "paid").signum() > 0 || lineValues.stream().anyMatch(l -> integer(l, "purchased") > 0))) {
            JsonObject reason = reason(); if (reason == null) return; with(payload, reason);
        }
        JsonObject saveRequest = mutation("SAVE", payload);
        Map<Integer, byte[]> pending = new TreeMap<>(stagedImages);
        call(saveRequest, data -> {
            showDetail(data);
            uploadPending(new ArrayList<>(pending.entrySet()), 0, payload, issueAfterSave);
        });
    }
    private void uploadPending(List<Map.Entry<Integer, byte[]>> pending, int index, JsonObject original, boolean issueAfterSave) {
        if (index >= pending.size()) {
            if (pending.isEmpty()) { finishSave(issueAfterSave); return; }
            JsonObject payload = new JsonObject(); payload.add("order", draft()); if (original.has("reason")) payload.add("reason", original.get("reason"));
            call(mutation("SAVE", payload), data -> { stagedImages.clear(); showDetail(data); finishSave(issueAfterSave); }); return;
        }
        Map.Entry<Integer, byte[]> image = pending.get(index);
        JsonObject payload = new JsonObject(); payload.addProperty("base64", Base64.getEncoder().encodeToString(image.getValue()));
        call(mutation("UPLOAD", payload), result -> {
            lineValues.get(image.getKey()).add("screenshot", result.get("reference"));
            uploadPending(pending, index + 1, original, issueAfterSave);
        });
    }
    private void finishSave(boolean issueAfterSave) {
        if (issueAfterSave) call(mutation("ISSUE", new JsonObject()), data -> { showDetail(data); setStatus("Quote issued. Payment can now be recorded at the register."); });
        else setStatus("Quote saved.");
    }
    private JsonObject mutation(String action, JsonObject payload) {
        JsonObject b = payload.deepCopy(); b.addProperty("action", action); b.addProperty("requestId", UUID.randomUUID().toString());
        if (order != null) { b.add("id", order.get("overseas_order_id")); b.add("revision", order.get("revision")); } return b;
    }
    private void change(String action, JsonObject b, boolean printPayment) {
        if (b == null) return; requireSaved();
        call(mutation(action, b), data -> { showDetail(data); if (printPayment) printLatest(); });
    }
    private static JsonObject with(JsonObject b, JsonObject extra) { for (var e : extra.entrySet()) b.add(e.getKey(), e.getValue()); return b; }
    private JsonObject reason() {
        String value = JOptionPane.showInputDialog(this, "Reason for this adjustment / cancellation:"); if (value == null) return null;
        if (value.isBlank()) throw new IllegalArgumentException("A reason is required."); JsonObject b = new JsonObject(); b.addProperty("reason", value); return b;
    }
    private void call(JsonObject b, Consumer<JsonObject> complete) {
        if (working) return;
        working = true; controls(); setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR)); setStatus("Working…");
        new SwingWorker<JsonObject, Void>() {
            @Override protected JsonObject doInBackground() throws Exception { return transport.send(b); }
            @Override protected void done() {
                if (disposed) { working = false; return; }
                working = false; setCursor(Cursor.getDefaultCursor()); setStatus(" ");
                try { complete.accept(get()); } catch (Exception e) { error(e); } finally { controls(); }
            }
        }.execute();
    }
    private void controls() {
        for (JButton b : buttons) { Object permission = b.getClientProperty("permission");boolean eligible=!(editingIssued&&b.getText().equals("Find customer"));if(b.getText().equals("Take payment"))eligible=order!=null&&s(order,"state").equals("ISSUED")&&decimal(order,"balance").signum()>0;if(b.getText().equals("Mark ordered"))eligible=order!=null&&s(order,"state").equals("ISSUED");b.setEnabled(!working && eligible && (permission == null || permissions.contains("OVERSEAS_ORDERS_" + permission))); }
        boolean edit = !working && permissions.contains("OVERSEAS_ORDERS_QUOTE");
        boolean identity = edit && !editingIssued;
        name.setEditable(identity); phone.setEditable(identity); expected.setEditable(edit); shipping.setEditable(edit); notes.setEditable(edit);
        itemFields.values().forEach(f -> f.setEditable(edit)); if(digitalItem!=null)digitalItem.setEnabled(edit); if (sellerChoices != null) sellerChoices.setEnabled(edit);
        search.setEnabled(!working); filter.setEnabled(!working); orders.setEnabled(!working); items.setEnabled(!working);
    }
    private boolean dirty() {
        if (page == Page.LIST || loading) return false;
        try { return itemDirty() || !stagedImages.isEmpty() || savedDraft != null && !savedDraft.equals(draft()); } catch (Exception e) { return true; }
    }
    private boolean confirmDiscard() { return !dirty() || JOptionPane.showConfirmDialog(this, "Discard unsaved quote changes?", "Unsaved quote", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION; }
    private void requireSaved() { if (order == null) throw new IllegalArgumentException("Save and issue the quote first."); if (dirty()) throw new IllegalArgumentException("Save the quote changes before recording payments, shipment, collection or printing."); }
    private void error(Exception e) {
        Throwable root = e; while (root.getCause() != null) root = root.getCause(); setStatus("Action was not completed. " + Objects.toString(root.getMessage(), "Please retry."));
        JOptionPane.showMessageDialog(this, Objects.toString(root.getMessage(), "The action could not be completed."), "Overseas Orders", JOptionPane.WARNING_MESSAGE);
    }
    private void setStatus(String text){status.setText(text);status.setVisible(text!=null&&!text.isBlank());root.revalidate();}
    private static String s(JsonObject o, String key) { return o == null || !o.has(key) || o.get(key).isJsonNull() ? "" : o.get(key).getAsString(); }
    private static Set<String> permissions(JsonObject data) { Set<String> out = new HashSet<>(); for (JsonElement p : data.getAsJsonArray("permissions")) out.add(p.getAsString()); return Set.copyOf(out); }
    private void lookupPhone(){
        String query=phone.getText().trim();long version=++phoneVersion;
        if(loading||working||page!=Page.QUOTE||!permissions.contains("OVERSEAS_ORDERS_QUOTE")||query.isBlank())return;
        try{utils.CustomerPhoneNumber.normalize(query);}catch(IllegalArgumentException e){return;}
        JsonObject request=request("CUSTOMER_PHONE");request.addProperty("phone",query);
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{return transport.send(request);}
            protected void done(){if(version!=phoneVersion||!query.equals(phone.getText().trim()))return;
                try{JsonObject result=get();phoneMatches.removeAll();for(JsonElement element:result.getAsJsonArray("customers")){JsonObject c=element.getAsJsonObject();int id=c.get("customerId").getAsInt();if(customerId!=null&&customerId==id)continue;
                    if(!c.get("active").getAsBoolean()){phoneMatches.add(new JLabel(c.get("name").getAsString()+" — inactive account; ask an administrator."));continue;}
                    JButton choose=new JButton("Select existing customer: "+c.get("name").getAsString()+" · "+c.get("accountNumber").getAsString());choose.addActionListener(e->{if(version!=phoneVersion)return;customerId=id;name.setText(s(c,"name"));phone.setText(s(c,"phone"));updateCustomerHint();phoneMatches.removeAll();phoneMatches.revalidate();phoneMatches.repaint();});phoneMatches.add(choose);
                }if(result.get("reserved").getAsBoolean()&&result.getAsJsonArray("customers").isEmpty())phoneMatches.add(new JLabel("Number reserved. Ask an administrator to check the customer account."));phoneMatches.revalidate();phoneMatches.repaint();
                }catch(Exception e){phoneMatches.add(new JLabel("Phone lookup unavailable. Use Find customer or retry."));phoneMatches.revalidate();}
            }
        }.execute();
    }    private void findCustomer(){String q=JOptionPane.showInputDialog(this,"Customer name, phone or account number:",phone.getText());if(q==null)return;JsonObject b=request("CUSTOMERS");b.addProperty("search",q);call(b,data->{List<JsonObject> customers=new ArrayList<>();List<String> labels=new ArrayList<>();for(JsonElement e:data.getAsJsonArray("customers")){JsonObject c=e.getAsJsonObject();customers.add(c);labels.add(s(c,"name")+" · "+s(c,"phone")+" · "+s(c,"accountNumber"));}if(labels.isEmpty()){JOptionPane.showMessageDialog(this,"No customers found. Leave the customer unselected to create a new account on issue.");return;}Object choice=JOptionPane.showInputDialog(this,"Select customer","Customer",JOptionPane.PLAIN_MESSAGE,null,labels.toArray(),labels.get(0));if(choice!=null){JsonObject c=customers.get(labels.indexOf(choice));customerId=c.get("customerId").getAsInt();name.setText(s(c,"name"));phone.setText(s(c,"phone"));updateCustomerHint();}});}
    private JsonObject paymentFields(boolean refund){JPanel p=new JPanel(new GridLayout(0,2,8,8));JTextField amount=new JTextField(),reference=new JTextField();JComboBox<String> method=new JComboBox<>(new String[]{"CASH","CARD","CHEQUE","MMG"});p.add(new JLabel("Amount GYD"));p.add(amount);p.add(new JLabel("Payment method"));p.add(method);p.add(new JLabel("Reference (card / cheque / MMG)"));p.add(reference);if(JOptionPane.showConfirmDialog(this,p,refund?"Refund payment":"Collect payment",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return null;JsonObject b=new JsonObject();b.addProperty("amount",new BigDecimal(amount.getText()));b.addProperty("method",method.getSelectedItem().toString());b.addProperty("reference",reference.getText());return b;}
    private void payment(boolean refund,boolean unused){requireSaved();JsonObject b=paymentFields(refund);if(b==null)return;if(refund){JsonObject r=reason();if(r==null)return;with(b,r);}change(refund?"REFUND":"PAYMENT",b,true);}
    private String trackingLabel(String stage){return switch(stage){case "DIGITAL_FULFILLED"->"Digital item fulfilled";case "DIGITAL_ORDERED"->"Digital item ordered";case "DIGITAL_PROCESSING"->"Digital item processing";case "DRAFT"->"Draft";case "CANCELLED"->"Cancelled";case "COMPLETED"->"Collected";case "ISSUED"->"Issued";case "WAITING_FOR_PAYMENT"->"Waiting for payment";case "PROCESSING"->"Processing";case "ORDERED"->"Ordered";case "PARTIALLY_ORDERED"->"Partially ordered";case "ARRIVED"->"Arrived at Deckers "+(order==null?managers.SessionManager.getCurrentLocationName():s(order,"store_name"));case "PARTIALLY_ARRIVED"->"Partially arrived";case "COLLECTED"->"Collected";default->stage;};}
    private void track(String stage){requireSaved();int index=items.getSelectedRow();if(index<0)throw new IllegalArgumentException("Select an item first.");JsonObject line=lineValues.get(index);JsonObject b=new JsonObject();b.addProperty("stage",stage);JsonArray ids=new JsonArray();ids.add(line.get("line_id"));b.add("lineIds",ids);change("TRACK",b,false);}    private void quantities(boolean collect){requireSaved();if(order==null)throw new IllegalArgumentException("Select an issued order.");DefaultTableModel m=new DefaultTableModel(collect?new String[]{"Item","Ordered","Arrived","Collected total"}:new String[]{"Item","Ordered","Purchased total","Shipped total","Arrived total"},0){@Override public boolean isCellEditable(int r,int col){return col>=(collect?3:2);}};for(JsonObject l:lineValues)m.addRow(collect?new Object[]{s(l,"description"),s(l,"quantity"),s(l,"arrived"),s(l,"collected")}:new Object[]{s(l,"description"),s(l,"quantity"),s(l,"purchased"),s(l,"shipped"),s(l,"arrived")});JTable table=new JTable(m);JPanel panel=new JPanel(new BorderLayout());panel.add(new JLabel(collect?"Enter cumulative collected quantities. Final collection requires full payment.":"Enter cumulative quantities for purchased, shipped and arrived."),BorderLayout.NORTH);panel.add(new JScrollPane(table));if(JOptionPane.showConfirmDialog(this,panel,collect?"Customer collection":"Shipment progress",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;if(table.isEditing())table.getCellEditor().stopCellEditing();JsonArray updates=new JsonArray();for(int i=0;i<m.getRowCount();i++){JsonObject l=new JsonObject();l.add("line_id",lineValues.get(i).get("line_id"));if(!l.has("line_id"))throw new IllegalArgumentException("Save the quote before updating quantities.");String[] keys=collect?new String[]{"collected"}:new String[]{"purchased","shipped","arrived"};for(int j=0;j<keys.length;j++)l.addProperty(keys[j],Integer.parseInt(m.getValueAt(i,(collect?3:2)+j).toString()));updates.add(l);}JsonObject b=new JsonObject();b.add("lines",updates);if(collect&&permissions.contains("OVERSEAS_ORDERS_PAYMENT")&&JOptionPane.showConfirmDialog(this,"Record a payment together with this collection?","Collection payment",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION){JsonObject payment=paymentFields(false);if(payment==null)return;with(b,payment);}if(permissions.contains("OVERSEAS_ORDERS_ADJUST")){String reason=JOptionPane.showInputDialog(this,"Reason (required if reducing quantities):","");if(reason==null)return;b.addProperty("reason",reason);}change(collect?"COLLECT":"SHIPMENT",b,collect&&b.has("amount"));}
    private void print(){requireSaved();if(detail==null)throw new IllegalArgumentException("Select a saved order.");List<String> labels=new ArrayList<>();List<JsonObject> receipts=new ArrayList<>();labels.add("Order slip");receipts.add(null);for(JsonElement e:detail.getAsJsonArray("payments")){JsonObject p=e.getAsJsonObject();labels.add(s(p,"payment_action")+" #"+s(p,"custom_order_payment_id")+" · GYD "+s(p,"payment_amount"));receipts.add(p);}Object choice=JOptionPane.showInputDialog(this,"Print document","Overseas order",JOptionPane.PLAIN_MESSAGE,null,labels.toArray(),labels.get(0));if(choice!=null)printDocument(receipts.get(labels.indexOf(choice)));}
    private void printLatest(){JsonArray p=detail.getAsJsonArray("payments");if(p.isEmpty())return;JsonObject receipt=p.get(p.size()-1).getAsJsonObject();boolean first=p.size()==1&&s(receipt,"payment_action").equals("PAYMENT");JsonObject snapshot=detail.deepCopy();new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{if(first)Receipt.CustomOrderSlipPrinter.print(Receipt.OverseasOrderDocuments.build(snapshot,null));Receipt.CustomOrderSlipPrinter.print(Receipt.OverseasOrderDocuments.build(snapshot,receipt));return null;}protected void done(){try{get();}catch(Exception e){JOptionPane.showMessageDialog(OverseasOrders.this,"Payment saved. Printing failed; use Print slip / receipt to retry.\n"+e.getMessage());}}}.execute();}
    private void printDocument(JsonObject receipt){JsonObject snapshot=detail.deepCopy();new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{Receipt.CustomOrderSlipPrinter.print(Receipt.OverseasOrderDocuments.build(snapshot,receipt));return null;}protected void done(){try{get();}catch(Exception e){error(e);}}}.execute();}

}
