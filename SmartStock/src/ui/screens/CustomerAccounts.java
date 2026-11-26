package ui.screens;

import managers.PermissionManager;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.components.CustomerTypeSelector;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.WindowHelper;
import ui.helpers.UiTaskRunner;
import ui.helpers.ResponsiveTask;
import ui.helpers.CustomerCardActions;
import ui.design.DeckersPalette;
import managers.CompanyCustomizationManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class CustomerAccounts extends JFrame {
    private JTable customerTable;
    private DefaultTableModel customerModel;
    private TableRowSorter<DefaultTableModel> customerSorter;
    private JTextField searchField;
    private JTextField accountNumberField;
    private JTextField nameField;
    private JTextField phoneField;
    private final JTextField additionalPhone1Field = new JTextField();
    private final JTextField additionalPhone2Field = new JTextField();

    private final JTextField phoneLabelField = phoneLabelField("Main number type");
    private final JTextField additionalPhone1LabelField = phoneLabelField("Additional number 1 type");
    private final JTextField additionalPhone2LabelField = phoneLabelField("Additional number 2 type");

    private static JTextField phoneLabelField(String name) {
        JTextField field = new JTextField(8);
        field.putClientProperty("JTextField.placeholderText", "Type");
        field.setToolTipText("Optional type, e.g. Personal, Business, Home or Work");
        field.getAccessibleContext().setAccessibleName(name);
        return field;
    }

    private JPanel phoneNumbersPanel() {
        JPanel panel = new JPanel(new java.awt.GridBagLayout());
        panel.setOpaque(false);
        JTextField[] numbers = {phoneField, additionalPhone1Field, additionalPhone2Field};
        JTextField[] types = {phoneLabelField, additionalPhone1LabelField, additionalPhone2LabelField};
        String[] labels = {"Main:", "Additional 1:", "Additional 2:"};
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(2, 3, 2, 3);
        for (int row = 0; row < numbers.length; row++) {
            constraints.gridy = row;
            constraints.gridx = 0; constraints.weightx = 0;
            panel.add(new JLabel(labels[row]), constraints);
            constraints.gridx = 1; constraints.weightx = 0.65;
            panel.add(numbers[row], constraints);
            constraints.gridx = 2; constraints.weightx = 0.35;
            panel.add(types[row], constraints);
        }
        return panel;
    }
    private JTextField emailField;
    private JTextField customerSinceField;
    private CustomerTypeSelector customerTypeSelector;
    private JTextField creditLimitField;
    private JTextField balanceField;
    private JTextArea accountNotesArea;
    private ui.helpers.ProductImageHelper.ImageSelector customerPhotoSelector;
    private JCheckBox activeCheckBox;
    private JCheckBox whatsappOptInCheckBox;
    private JCheckBox requireChargeAuthorizationCheckBox;
    private JCheckBox customOrderDepositExemptBox;
    private JCheckBox salesDiscountEnabledBox,invoiceDiscountEnabledBox,customOrderDiscountEnabledBox;
    private JTextField salesDiscountPercentField,invoiceDiscountPercentField,customOrderDiscountPercentField;
    private JButton addChargeButton;
    private JButton recordPaymentButton;
    private JButton transactionHistoryButton;
    private JButton paymentHistoryButton;
    private JButton previewCardButton,printCardButton,pdfCardButton;
    private LanApiClient.CustomerAccountRecord selectedAccount;
    private final Map<Integer,LanApiClient.CustomerAccountRecord> customerRecords=new HashMap<>();
    private Integer selectedCustomerId;
    private String preparedPhotoKey;
    private LanApiClient.CustomerAccountSaveRequest preparedPhotoRequest;
    private String pendingSaveKey;
    private String pendingSaveFingerprint;
    private String pendingAdjustmentKey;
    private String pendingAdjustmentFingerprint;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();
    private JLabel customerHeading, customerSubheading, companyFooter;
    private CustomerAccountsDesign.Avatar customerAvatar;
    private final JLabel[] accountTotals = new JLabel[5];
    private JButton saveButton, allFilter, activeFilter, businessFilter;
    private SalesTaskVisuals.Badge statusBadge;
    private JTabbedPane accountTabs;
    private CustomerActivityPanel activityHistory;
    private long activityGeneration;
    private Integer activityCustomerId;
    private String customerFilter = "ALL";
    private JPanel workspace, directoryArea;
    private JPanel accountOverview, accountSummary, accountIdentity, saveActions;
    private final boolean connected;

    public CustomerAccounts() {
        this(true);
    }

    // Package access lets the visual harness exercise the actual screen without a live store.
    CustomerAccounts(boolean connected) {
        this.connected = connected;
        setTitle("Customer Accounts");
        setSize(1440, 1024);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        if (connected) setJMenuBar(AppMenuBar.create(this, "CustomerAccounts"));

        workspace = CustomerAccountsDesign.panel(new BorderLayout(0, 18));
        workspace.setBorder(new EmptyBorder(12, 8, 0, 8));
        JPanel mainPanel = CustomerAccountsDesign.panel(new BorderLayout(20, 0));

        mainPanel.add(buildTablePanel(), BorderLayout.CENTER);
        mainPanel.add(buildFormPanel(), BorderLayout.EAST);
        workspace.add(mainPanel, BorderLayout.CENTER);
        workspace.add(buildFooter(), BorderLayout.SOUTH);
        add(workspace, BorderLayout.CENTER);
        clearFields();
        updateFilterLabels();
        CustomerAccountsDesign.styleTree(workspace);
        refreshCustomerHeading();
        if (connected) {
            loadCustomers();
            loadCompanyIdentity();
            WindowHelper.configurePosWindow(this);
        }
        getContentPane().setBackground(DeckersPalette.background());
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                mainPanel.getComponent(1).setPreferredSize(new Dimension(Math.max(420, (int)(mainPanel.getWidth() * .34)), 0));
                applyWorkspaceDensity();
                mainPanel.revalidate();
            }
        });
    }

    private JPanel buildTablePanel() {
        JPanel panel = CustomerAccountsDesign.panel(new BorderLayout(0, 27));
        JPanel toolbar = CustomerAccountsDesign.panel(new BorderLayout(0, 14));
        JPanel headingRow = CustomerAccountsDesign.panel(new BorderLayout(12, 0));
        headingRow.setPreferredSize(new Dimension(100, 64));
        JPanel heading = CustomerAccountsDesign.panel(new GridLayout(2, 1, 0, 0));
        JLabel title = CustomerAccountsDesign.label("Customers", 32, true);
        title.setIcon(SalesTaskVisuals.icon("person", DeckersPalette.PURPLE, 28));
        title.setIconTextGap(12);
        heading.add(title);
        JLabel subtitle = CustomerAccountsDesign.label("Customer accounts and credit", 16, false);
        subtitle.setBorder(new EmptyBorder(0, 40, 0, 0));
        heading.add(subtitle);
        headingRow.add(heading);
        JPanel actions = CustomerAccountsDesign.panel(new FlowLayout(FlowLayout.RIGHT, 8, 14));
        JButton newCustomer = CustomerAccountsDesign.button("New Customer", "plus", true);
        newCustomer.addActionListener(e -> { clearFields(); accountTabs.setSelectedIndex(0); });
        actions.add(newCustomer);
        actions.add(moreButton());
        headingRow.add(actions, BorderLayout.EAST);
        toolbar.add(headingRow, BorderLayout.NORTH);

        JPanel searchPanel = CustomerAccountsDesign.panel(new BorderLayout(12, 0));
        searchField = new JTextField() {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (getText().isEmpty() && !hasFocus()) {
                    Graphics2D g = (Graphics2D)graphics.create();
                    g.setFont(CustomerAccountsDesign.font(Font.PLAIN, 14));
                    g.setColor(DeckersPalette.muted());
                    g.drawString("Find customer by name, account or phone...", 8, (getHeight()-g.getFontMetrics().getHeight())/2+g.getFontMetrics().getAscent());
                    g.dispose();
                }
            }
        };
        searchField.setToolTipText("Find customer by name, account or phone");
        searchField.getAccessibleContext().setAccessibleName("Find customer by name, account or phone");
        searchField.putClientProperty("JTextField.placeholderText", "Find customer by name, account or phone...");
        JPanel searchBox = CustomerAccountsDesign.surface(new BorderLayout(10, 0), DeckersPalette.PURPLE);
        searchBox.setBorder(new EmptyBorder(4, 10, 4, 4));
        JLabel searchIcon = new JLabel(SalesTaskVisuals.icon("search", DeckersPalette.muted(), 18));
        searchBox.add(searchIcon, BorderLayout.WEST);
        searchBox.add(searchField);
        searchPanel.add(searchBox);
        JPanel filters = CustomerAccountsDesign.panel(new GridLayout(1, 3, 10, 0));
        allFilter = filterButton("All customers", "ALL");
        activeFilter = filterButton("Active", "ACTIVE");
        businessFilter = filterButton("Business", "BUSINESS");
        filters.add(allFilter); filters.add(activeFilter); filters.add(businessFilter);
        searchPanel.add(filters, BorderLayout.EAST);
        toolbar.add(searchPanel, BorderLayout.SOUTH);

        customerModel = new DefaultTableModel(
                new Object[]{"ID", "Account #", "Name", "Customer Type ID", "Customer Type", "Phone", "Email", "Credit Limit", "Credit Balance", "Custom Order Due", "Total Due", "Available Credit", "Account Type", "Active", "Notes"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        customerSorter = new TableRowSorter<>(customerModel);
        customerTable = new JTable(customerModel) {
            @Override public Color getBackground() { return DeckersPalette.surface(); }
            @Override public Color getGridColor() { return DeckersPalette.border(); }
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (getRowCount() == 0) {
                    graphics.setColor(DeckersPalette.muted());
                    graphics.setFont(CustomerAccountsDesign.font(Font.PLAIN, 16));
                    String message = searchField.getText().isBlank() && "ALL".equals(customerFilter) ? "No customers to display" : "No customers match this search";
                    graphics.drawString(message, 24, 56);
                }
            }
        };
        customerTable.setRowSorter(customerSorter);
        customerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        CustomerAccountsDesign.configureTable(customerTable);
        for (int index : new int[]{7, 8, 9, 10, 11}) customerSorter.setComparator(index,
                (a,b) -> new BigDecimal(stripMoney(a.toString())).compareTo(new BigDecimal(stripMoney(b.toString()))));
        customerSorter.setSortKeys(List.of(new RowSorter.SortKey(10, SortOrder.DESCENDING),
                new RowSorter.SortKey(2, SortOrder.ASCENDING)));

        customerTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedCustomer();
            }
        });
        customerTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    openAccountDetails();
                }
            }
        });
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyCustomerFilter();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyCustomerFilter();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyCustomerFilter();
            }
        });

        panel.add(toolbar, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(customerTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(DeckersPalette.sectionBorder(DeckersPalette.PURPLE)));
        tableScroll.getViewport().setBackground(DeckersPalette.surface());
        directoryArea = new JPanel(null) {
            @Override public void doLayout() {
                int contentHeight = customerTable.getTableHeader().getPreferredSize().height + Math.max(1,customerTable.getRowCount()) * customerTable.getRowHeight() + 2;
                tableScroll.setBounds(0, 0, getWidth(), Math.min(getHeight(),contentHeight));
            }
        };
        directoryArea.setOpaque(false);
        directoryArea.add(tableScroll);
        panel.add(directoryArea, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFormPanel() {
        JPanel wrapper = CustomerAccountsDesign.surface(new BorderLayout(0, 14), DeckersPalette.PURPLE);
        wrapper.setPreferredSize(new Dimension(480, 0));
        wrapper.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(3, 0, 0, 0, DeckersPalette.PURPLE), new EmptyBorder(14, 16, 16, 16)));

        JPanel formPanel = CustomerAccountsDesign.panel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 4, 2, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        accountNumberField = new JTextField();
        accountNumberField.setEditable(false);
        nameField = new JTextField();
        phoneField = new JTextField();
        phoneField.setToolTipText("Seven-digit Guyana number or international number with country code, e.g. +5927058194. Saved with +country code and digits.");
        emailField = new JTextField();
        customerSinceField = new JTextField();
        customerTypeSelector = new CustomerTypeSelector(connected);
        creditLimitField = new JTextField("0");
        balanceField = new JTextField("0");
        balanceField.setEditable(false);
        accountNotesArea = new JTextArea(4, 20);
        accountNotesArea.setLineWrap(true);
        accountNotesArea.setWrapStyleWord(true);
        activeCheckBox = new JCheckBox("Active", true);
        whatsappOptInCheckBox = new JCheckBox("<html>Customer consents to sales documents<br>by WhatsApp</html>");
        requireChargeAuthorizationCheckBox=new JCheckBox("<html>Require representative name and<br>signature for account charges</html>");
        customOrderDepositExemptBox=new JCheckBox("<html>Allow custom orders without<br>a deposit</html>");
        customOrderDepositExemptBox.setEnabled(PermissionManager.hasPermission("CUSTOM_ORDER_DEPOSIT_OVERRIDE")||PermissionManager.hasPermission("CUSTOM_ORDER_OVERRIDES"));
        salesDiscountEnabledBox=new JCheckBox("Apply");salesDiscountPercentField=new JTextField("0",4);
        invoiceDiscountEnabledBox=new JCheckBox("Apply");invoiceDiscountPercentField=new JTextField("0",4);
        customOrderDiscountEnabledBox=new JCheckBox("Apply");customOrderDiscountPercentField=new JTextField("0",4);
        boolean canManageDiscounts=PermissionManager.hasPermission("MANAGE_CUSTOMER_DISCOUNTS");
        for(JComponent component:new JComponent[]{salesDiscountEnabledBox,salesDiscountPercentField,invoiceDiscountEnabledBox,invoiceDiscountPercentField,customOrderDiscountEnabledBox,customOrderDiscountPercentField})component.setEnabled(canManageDiscounts);

        addField(formPanel, gbc, 0, "Name:", nameField);
        addField(formPanel, gbc, 1, "Account #:", accountNumberField);
        addField(formPanel, gbc, 2, "Phone numbers:", phoneNumbersPanel());
        addField(formPanel, gbc, 3, "Email:", emailField);
        addField(formPanel, gbc, 4, "Customer Type:", customerTypeSelector);
        addField(formPanel, gbc, 5, "WhatsApp:", whatsappOptInCheckBox);
        JScrollPane notesScrollPane = new JScrollPane(accountNotesArea);
        notesScrollPane.setPreferredSize(new Dimension(240, 70));
        notesScrollPane.setBorder(BorderFactory.createLineBorder(DeckersPalette.border()));
        addField(formPanel, gbc, 6, "Notes:", notesScrollPane);
        customerPhotoSelector = ui.helpers.ProductImageHelper.createImageSelector(this);
        JPanel creditPanel = CustomerAccountsDesign.panel(new GridBagLayout());
        addField(creditPanel, gbc, 0, "Credit limit:", creditLimitField);
        addField(creditPanel, gbc, 1, "Credit balance:", balanceField);
        addField(creditPanel, gbc, 2, "Authorization:", requireChargeAuthorizationCheckBox);
        addField(creditPanel, gbc, 3, "Sales discount:", discountControl(salesDiscountEnabledBox, salesDiscountPercentField));
        addField(creditPanel, gbc, 4, "Invoice discount:", discountControl(invoiceDiscountEnabledBox, invoiceDiscountPercentField));
        addField(creditPanel, gbc, 5, "Order discount:", discountControl(customOrderDiscountEnabledBox, customOrderDiscountPercentField));
        addField(creditPanel, gbc, 6, "Order deposit:", customOrderDepositExemptBox);
        addField(creditPanel, gbc, 7, "Customer Since (year):", customerSinceField);
        addField(creditPanel, gbc, 8, "Status:", activeCheckBox);
        JButton photoButton = CustomerAccountsDesign.button("Manage photo", "person", false);
        photoButton.addActionListener(e -> JOptionPane.showMessageDialog(this, customerPhotoSelector, "Customer photo — saved with the account", JOptionPane.PLAIN_MESSAGE));
        addField(creditPanel, gbc, 9, "Photo:", photoButton);

        addChargeButton = CustomerAccountsDesign.button("Add Charge", "plus", false);
        recordPaymentButton = CustomerAccountsDesign.button("Record Payment", "payment", false);
        recordPaymentButton.putClientProperty("taskOutline", true);
        transactionHistoryButton = CustomerAccountsDesign.button("Details", "orders", false);
        paymentHistoryButton = CustomerAccountsDesign.button("Payments", "payment", false);
        previewCardButton=CustomerAccountsDesign.button("Preview Card","person",false);
        printCardButton=CustomerAccountsDesign.button("Print Card","print",false);
        pdfCardButton=CustomerAccountsDesign.button("Save Card PDF","pdf",false);

        addChargeButton.setEnabled(false);
        recordPaymentButton.setEnabled(false);
        transactionHistoryButton.setEnabled(false);
        paymentHistoryButton.setEnabled(false);
        previewCardButton.setEnabled(false);printCardButton.setEnabled(false);pdfCardButton.setEnabled(false);

        addChargeButton.addActionListener(e -> adjustBalance(true));
        recordPaymentButton.addActionListener(e -> adjustBalance(false));
        transactionHistoryButton.addActionListener(e -> openAccountDetails());
        paymentHistoryButton.addActionListener(e -> openPaymentHistory());
        previewCardButton.addActionListener(e->CustomerCardActions.run(this,selectedAccount,CustomerCardActions.Action.PREVIEW));
        printCardButton.addActionListener(e->CustomerCardActions.run(this,selectedAccount,CustomerCardActions.Action.PRINT));
        pdfCardButton.addActionListener(e->CustomerCardActions.run(this,selectedAccount,CustomerCardActions.Action.PDF));

        JPanel overview = CustomerAccountsDesign.panel(new BorderLayout(0, 14));
        accountOverview = overview;
        overview.add(buildCustomerIdentity(), BorderLayout.NORTH);
        overview.add(buildAccountSummary(), BorderLayout.CENTER);
        JPanel paymentActions = CustomerAccountsDesign.panel(new GridLayout(1, 2, 12, 0));
        paymentActions.add(recordPaymentButton); paymentActions.add(addChargeButton);
        overview.add(paymentActions, BorderLayout.SOUTH);
        wrapper.add(overview, BorderLayout.NORTH);

        accountTabs = new JTabbedPane() {
            @Override public void updateUI() { super.updateUI(); installAccountTabs(this); }
        };
        accountTabs.setFont(CustomerAccountsDesign.font(Font.PLAIN, 14));
        accountTabs.setOpaque(false);
        accountTabs.addTab("Profile", scrollForm(formPanel));
        accountTabs.addTab("Credit & terms", scrollForm(creditPanel));
        JPanel activity = CustomerAccountsDesign.panel(new GridLayout(0, 1, 0, 12));
        for (JButton button : new JButton[]{transactionHistoryButton, paymentHistoryButton, previewCardButton, printCardButton, pdfCardButton}) {
            activity.add(button);
        }
        activityHistory=new CustomerActivityPanel(()->loadActivity(true),this::openAccountDetails,this::openPaymentHistory);
        accountTabs.addTab("Activity",activityHistory);
        accountTabs.addTab("More", scrollForm(activity));
        accountTabs.addChangeListener(e->{if(accountTabs.getSelectedComponent()==activityHistory)loadActivity(false);});
        wrapper.add(accountTabs, BorderLayout.CENTER);

        saveActions = CustomerAccountsDesign.panel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        saveActions.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DeckersPalette.border()), new EmptyBorder(18, 0, 6, 0)));
        saveButton = CustomerAccountsDesign.button("Save Changes", "save", true);
        saveButton.setPreferredSize(new Dimension(210, 42));
        saveButton.addActionListener(e -> { if (selectedCustomerId == null) addCustomer(); else updateCustomer(); });
        JButton cancel = CustomerAccountsDesign.button("Cancel", null, false);
        cancel.setPreferredSize(new Dimension(110, 42));
        cancel.addActionListener(e -> { if (selectedCustomerId != null) loadSelectedCustomer(); else clearFields(); });
        saveActions.add(saveButton); saveActions.add(cancel);
        wrapper.add(saveActions, BorderLayout.SOUTH);
        return wrapper;
    }

    private void installAccountTabs(JTabbedPane tabs) {
        tabs.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override protected void installDefaults() { super.installDefaults(); tabInsets = new Insets(10, 18, 10, 18); contentBorderInsets = new Insets(14, 0, 0, 0); }
            @Override protected void paintTabBackground(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) { g.setColor(selected ? DeckersPalette.surface() : DeckersPalette.background()); g.fillRect(x, y, w, h); }
            @Override protected void paintTabBorder(Graphics g, int placement, int index, int x, int y, int w, int h, boolean selected) { g.setColor(DeckersPalette.border()); g.drawRect(x, y, w, h); if (selected) { g.setColor(DeckersPalette.ORANGE); g.fillRect(x, y+h-2, w, 3); } }
            @Override protected void paintContentBorder(Graphics g, int placement, int selectedIndex) { }
            @Override protected void paintFocusIndicator(Graphics g, int placement, Rectangle[] rects, int index, Rectangle iconRect, Rectangle textRect, boolean selected) { if (selected && tabs.hasFocus()) { g.setColor(DeckersPalette.ORANGE); g.drawRect(rects[index].x+3, rects[index].y+3, rects[index].width-6, rects[index].height-6); } }
        });
    }

    private JPanel buildFooter() {
        JPanel footer = CustomerAccountsDesign.panel(new BorderLayout(12, 0));
        footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DeckersPalette.border()), new EmptyBorder(4, 4, 4, 0)));
        JPanel identity = CustomerAccountsDesign.panel(new FlowLayout(FlowLayout.LEFT, 22, 0));
        JLabel theme = CustomerAccountsDesign.label("SmartStock theme: Orange", 14, false);
        theme.setIcon(SalesTaskVisuals.icon("box", DeckersPalette.ORANGE, 18));
        identity.add(theme);
        companyFooter = CustomerAccountsDesign.label("Company", 14, false);
        identity.add(companyFooter);
        identity.add(CustomerAccountsDesign.label("SmartStock", 14, false));
        footer.add(identity, BorderLayout.WEST);
        footer.add(loadingState, BorderLayout.CENTER);
        JButton more = moreButton(); more.setText("More");
        more.setBorder(new EmptyBorder(8,12,8,12));
        footer.add(more, BorderLayout.EAST);
        return footer;
    }

    private JButton moreButton() {
        JButton more = CustomerAccountsDesign.button("", "more", false);
        more.setToolTipText("More customer actions");
        more.getAccessibleContext().setAccessibleName("More customer actions");
        more.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            menuItem(menu, "Refresh", () -> { SessionDataCache.invalidate("customer-accounts:"); loadCustomers(); }, true);
            menuItem(menu, "Customer Types", this::openCustomerTypes, true);
            menuItem(menu, "Clear selection", this::clearFields, true);
            menu.addSeparator();
            menuItem(menu, "Details", this::openAccountDetails, selectedCustomerId != null);
            menuItem(menu, "Payments", this::openPaymentHistory, selectedCustomerId != null);
            menuItem(menu, "Preview Card", () -> previewCardButton.doClick(), selectedCustomerId != null);
            menuItem(menu, "Print Card", () -> printCardButton.doClick(), selectedCustomerId != null);
            menuItem(menu, "Save Card PDF", () -> pdfCardButton.doClick(), selectedCustomerId != null);
            menu.addSeparator();
            menuItem(menu, "Customer Management Web App", this::openCustomerWebApp, true);
            if ("ADMIN".equalsIgnoreCase(managers.SessionManager.getCurrentRole())) menuItem(menu, "Merge Duplicates", this::mergeDuplicateAccounts, true);
            menu.show(more, 0, more.getHeight());
        });
        return more;
    }

    private void openCustomerWebApp() {
        try { Desktop.getDesktop().browse(java.net.URI.create("https://customermanagement.deckers.gy")); }
        catch (Exception ex) { JOptionPane.showMessageDialog(this, "Open https://customermanagement.deckers.gy on your phone or browser."); }
    }

    private void menuItem(JPopupMenu menu, String title, Runnable action, boolean enabled) {
        JMenuItem item = new JMenuItem(title);
        item.setFont(CustomerAccountsDesign.font(Font.PLAIN, 14));
        item.setEnabled(enabled);
        item.addActionListener(e -> action.run());
        menu.add(item);
    }

    private JButton filterButton(String text, String filter) {
        JButton button = CustomerAccountsDesign.button(text, null, false);
        button.addActionListener(e -> { customerFilter = filter; applyCustomerFilter(); });
        return button;
    }

    private JScrollPane scrollForm(JPanel form) {
        JPanel content = CustomerAccountsDesign.panel(new BorderLayout());
        content.add(form, BorderLayout.NORTH);
        return CustomerAccountsDesign.scroll(content);
    }

    private JPanel buildCustomerIdentity() {
        JPanel identity = CustomerAccountsDesign.panel(new BorderLayout(16, 0));
        accountIdentity = identity;
        customerAvatar = new CustomerAccountsDesign.Avatar(66);
        identity.add(customerAvatar, BorderLayout.WEST);
        JPanel name = CustomerAccountsDesign.panel(new GridLayout(2, 1));
        customerHeading = CustomerAccountsDesign.label("New Customer", 26, true);
        customerSubheading = CustomerAccountsDesign.label("Account created on save", 16, false);
        name.add(customerHeading); name.add(customerSubheading);
        identity.add(name);
        statusBadge = new SalesTaskVisuals.Badge("Active", new Color(25, 155, 55));
        JPanel status = CustomerAccountsDesign.panel(new GridBagLayout());
        status.add(statusBadge);
        identity.add(status, BorderLayout.EAST);
        return identity;
    }

    private JPanel buildAccountSummary() {
        JPanel summary = CustomerAccountsDesign.surface(new BorderLayout(0, 10), DeckersPalette.PURPLE);
        accountSummary = summary;
        summary.putClientProperty("customerTint", true);
        summary.setBorder(new EmptyBorder(10, 16, 12, 16));
        JPanel upper = CustomerAccountsDesign.panel(new GridLayout(1, 3, 10, 0));
        JPanel lower = CustomerAccountsDesign.panel(new GridLayout(1, 2, 16, 0));
        lower.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, DeckersPalette.sectionBorder(DeckersPalette.PURPLE)), new EmptyBorder(8, 0, 0, 0)));
        String[] titles = {"Credit Limit", "Credit Balance", "Order Due", "Total Due", "Available Credit"};
        for (int i = 0; i < titles.length; i++) {
            JPanel amount = CustomerAccountsDesign.panel(new BorderLayout(0, 2));
            amount.add(CustomerAccountsDesign.label(titles[i], 15, false), BorderLayout.NORTH);
            accountTotals[i] = CustomerAccountsDesign.label(money(BigDecimal.ZERO), i < 3 ? 22 : 29, true);
            amount.add(accountTotals[i]);
            (i < 3 ? upper : lower).add(amount);
        }
        summary.add(upper, BorderLayout.NORTH);
        summary.add(lower, BorderLayout.CENTER);
        return summary;
    }

    private void applyWorkspaceDensity() {
        boolean compact = getHeight() < 880;
        ((BorderLayout)accountOverview.getLayout()).setVgap(compact ? 7 : 14);
        int avatarSize = compact ? 48 : 66;
        customerAvatar.setPreferredSize(new Dimension(avatarSize, avatarSize));
        accountIdentity.setPreferredSize(new Dimension(100, avatarSize));
        accountSummary.setBorder(compact ? new EmptyBorder(4,12,4,12) : new EmptyBorder(10,16,12,16));
        ((BorderLayout)accountSummary.getLayout()).setVgap(compact ? 6 : 10);
        customerHeading.setFont(CustomerAccountsDesign.font(Font.BOLD, compact ? 22 : 26));
        for (int i=0;i<accountTotals.length;i++) accountTotals[i].setFont(CustomerAccountsDesign.font(Font.BOLD, compact ? (i<3?18:21) : (i<3?22:29)));
        saveActions.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,DeckersPalette.border()),new EmptyBorder(compact?8:18,0,compact?2:6,0)));
        workspace.revalidate();
    }

    private void refreshCustomerHeading() {
        if (customerHeading == null) return;
        customerHeading.setText(selectedAccount == null ? "New Customer" : selectedAccount.name());
        customerSubheading.setText(selectedAccount == null ? "Account created on save" : selectedAccount.accountNumber());
        customerAvatar.setText(selectedAccount == null ? "" : CustomerAccountsDesign.initials(selectedAccount.name()));
        boolean active = selectedAccount == null || selectedAccount.active();
        statusBadge.setText(active ? "Active" : "Inactive");
        statusBadge.tone(active ? new Color(25, 155, 55) : DeckersPalette.muted());
        BigDecimal[] totals = selectedAccount == null ? new BigDecimal[]{BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO}
                : new BigDecimal[]{selectedAccount.creditLimit(),selectedAccount.currentBalance(),selectedAccount.customOrderDue(),selectedAccount.totalDue(),selectedAccount.availableCredit()};
        for (int i = 0; i < totals.length; i++) accountTotals[i].setText(money(totals[i]));
        accountTotals[3].setForeground(DeckersPalette.CORAL);
        accountTotals[3].putClientProperty("customerTone", "due");
        accountTotals[4].setForeground(DeckersPalette.dark() ? new Color(217,180,255) : DeckersPalette.PURPLE);
        accountTotals[4].putClientProperty("customerTone", "credit");
        saveButton.setText(selectedCustomerId == null ? "Add Account" : "Save Changes");
    }

    private void loadCompanyIdentity() {
        UiTaskRunner.submit(this, "customer-accounts.branding", () -> {
            var settings = CompanyCustomizationManager.loadReceiptSettings();
            return settings.companyName();
        }, identity -> {
            companyFooter.setText(identity);
            setTitle("SmartStock – " + identity + " – Customers");
        }, ignored -> companyFooter.setText("SmartStock"));
    }


    private String pendingMergeFingerprint;
    private String pendingMergeKey;

    private void mergeDuplicateAccounts() {
        try {
            var preview=ResponsiveTask.await(this,"Finding duplicate customer accounts...",LanApiClient::previewCustomerAccountMerges);
            if(preview==null)return;
            if(preview.groups().isEmpty()) {JOptionPane.showMessageDialog(this,"No unmerged accounts have the same name and phone number.");return;}
            DefaultTableModel model=new DefaultTableModel(new Object[]{"Name","Phone","Keep account","Merge accounts","References","Combined balance"},0) {
                @Override public boolean isCellEditable(int row,int column){return false;}
            };
            for(var group:preview.groups())model.addRow(new Object[]{group.name(),group.phone(),group.survivorId(),group.duplicateIds().toString(),
                    group.references().values().stream().mapToLong(Long::longValue).sum(),group.combinedBalance()});
            JPanel panel=new JPanel(new BorderLayout(8,8));
            panel.add(new JLabel("Keep the oldest account in each group and move its duplicates' orders, payments and history to it."),BorderLayout.NORTH);
            JScrollPane rows=new JScrollPane(new JTable(model));rows.setPreferredSize(new Dimension(850,280));panel.add(rows,BorderLayout.CENTER);
            panel.add(new JLabel("Old account IDs remain recorded. Existing account limits and discounts are kept; required charge authorization is preserved."),BorderLayout.SOUTH);
            if(JOptionPane.showConfirmDialog(this,panel,"Merge all matching customer accounts",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
            if(!preview.fingerprint().equals(pendingMergeFingerprint)) {pendingMergeFingerprint=preview.fingerprint();pendingMergeKey=UUID.randomUUID().toString();}
            String key=pendingMergeKey;
            UiTaskRunner.submit(this,"customer-accounts.merge",()->LanApiClient.mergeCustomerAccounts(preview.fingerprint(),key),result->{
                pendingMergeFingerprint=null;pendingMergeKey=null;SessionDataCache.invalidate("customer-accounts:");clearFields();loadCustomers();
                JOptionPane.showMessageDialog(this,"Merged "+result.accountsRetired()+" duplicate accounts into "+result.groupsMerged()+" customer accounts.");
            },error->JOptionPane.showMessageDialog(this,"Unable to merge customer accounts: "+error.getMessage()));
        }catch(Exception ex){JOptionPane.showMessageDialog(this,"Unable to preview customer duplicates: "+ex.getMessage());}
    }

    private void addField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(CustomerAccountsDesign.font(Font.PLAIN, 15));
        fieldLabel.setMinimumSize(fieldLabel.getPreferredSize());
        panel.add(fieldLabel, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private void loadCustomers() {
        CachedUiLoader.load(this, "customer-accounts:list", CustomerAccountsSnapshot.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                () -> new CustomerAccountsSnapshot(LanApiClient.loadCustomerAccountRecords()),
                this::applyCustomers);
    }

    private void applyCustomers(CustomerAccountsSnapshot snapshot) {
        Integer previousCustomerId = selectedCustomerId;
        customerModel.setRowCount(0);
        customerRecords.clear();
        for (LanApiClient.CustomerAccountRecord row : snapshot.rows()) {
                    customerRecords.put(row.customerId(),row);
                    customerModel.addRow(new Object[]{
                            row.customerId(),row.accountNumber(),row.name(),row.customerTypeId()==null?"":row.customerTypeId(),
                            row.customerTypeName(),row.phone(),row.email(),money(row.creditLimit()),money(row.currentBalance()),
                            money(row.customOrderDue()),money(row.totalDue()),money(row.availableCredit()),utils.CustomerAccountTypes.isBusiness(row.customerTypeName())?"Business":"Personal",row.active(),row.accountNotes()
                    });
        }
        updateFilterLabels();
        if (previousCustomerId != null) {
            for (int row = 0; row < customerModel.getRowCount(); row++) {
                if (previousCustomerId.equals(customerModel.getValueAt(row, 0))) {
                    int viewRow = customerTable.convertRowIndexToView(row);
                    if (viewRow >= 0) customerTable.setRowSelectionInterval(viewRow, viewRow);
                    else clearFields();
                    return;
                }
            }
            clearFields();
        }
    }

    private record CustomerAccountsSnapshot(List<LanApiClient.CustomerAccountRecord> rows) { }

    private void loadSelectedCustomer() {
        int row = customerTable.getSelectedRow();
        if (row == -1) {
            return;
        }
        int modelRow = customerTable.convertRowIndexToModel(row);
        selectedCustomerId = Integer.parseInt(String.valueOf(customerModel.getValueAt(modelRow, 0)));
        selectedAccount=customerRecords.get(selectedCustomerId);
        accountNumberField.setText(valueAt(modelRow, 1));
        accountNumberField.setEditable(PermissionManager.hasPermission("EDIT_ACCOUNT_NUMBER"));
        nameField.setText(valueAt(modelRow, 2));
        customerTypeSelector.setSelectedCustomerType(parseNullableInt(valueAt(modelRow, 3)), valueAt(modelRow, 4));
        phoneField.setText(valueAt(modelRow, 5));additionalPhone1Field.setText(selectedAccount.additionalPhone1());additionalPhone2Field.setText(selectedAccount.additionalPhone2());phoneLabelField.setText(selectedAccount.phoneLabel());additionalPhone1LabelField.setText(selectedAccount.additionalPhone1Label());additionalPhone2LabelField.setText(selectedAccount.additionalPhone2Label());
        emailField.setText(valueAt(modelRow, 6));
        creditLimitField.setText(stripMoney(valueAt(modelRow, 7)));
        balanceField.setText(stripMoney(valueAt(modelRow, 8)));
        activeCheckBox.setSelected(Boolean.TRUE.equals(customerModel.getValueAt(modelRow, 13)));
        accountNotesArea.setText(valueAt(modelRow, 14));
        customerPhotoSelector.setImageUrl(selectedAccount == null ? "" : selectedAccount.customerPhotoUrl());
        accountNotesArea.setCaretPosition(0);
        customerSinceField.setText(selectedAccount==null||selectedAccount.customerSince()==null?"":String.valueOf(selectedAccount.customerSince()));
        whatsappOptInCheckBox.setSelected(selectedAccount!=null&&selectedAccount.whatsappOptIn());
        customOrderDepositExemptBox.setSelected(selectedAccount!=null&&selectedAccount.customOrderDepositExempt());
        requireChargeAuthorizationCheckBox.setSelected(selectedAccount!=null&&selectedAccount.requireChargeAuthorization());
        salesDiscountEnabledBox.setSelected(selectedAccount!=null&&selectedAccount.salesDiscountEnabled());salesDiscountPercentField.setText(discountText(selectedAccount==null?null:selectedAccount.salesDiscountPercent()));
        invoiceDiscountEnabledBox.setSelected(selectedAccount!=null&&selectedAccount.invoiceDiscountEnabled());invoiceDiscountPercentField.setText(discountText(selectedAccount==null?null:selectedAccount.invoiceDiscountPercent()));
        customOrderDiscountEnabledBox.setSelected(selectedAccount!=null&&selectedAccount.customOrderDiscountEnabled());customOrderDiscountPercentField.setText(discountText(selectedAccount==null?null:selectedAccount.customOrderDiscountPercent()));
        addChargeButton.setEnabled(true);
        recordPaymentButton.setEnabled(true);
        transactionHistoryButton.setEnabled(true);
        paymentHistoryButton.setEnabled(true);
        previewCardButton.setEnabled(true);printCardButton.setEnabled(true);pdfCardButton.setEnabled(true);
        refreshCustomerHeading();
        activityCustomerId=null;
        activityGeneration++;
        activityHistory.message("Open Activity to view transactions and payments.");
        if(accountTabs.getSelectedComponent()==activityHistory)loadActivity(false);
    }

    private void loadActivity(boolean force) {
        Integer customerId=selectedCustomerId;
        if(customerId==null){activityHistory.message("Select a customer to view activity.");return;}
        if(!force&&customerId.equals(activityCustomerId))return;
        activityCustomerId=customerId;
        long generation=++activityGeneration;
        activityHistory.message("Loading transactions and payments…");
        if(!connected){activityHistory.message("No transactions or payments in this preview.");return;}
        UiTaskRunner.submit(this,"customer-accounts.activity",()->LanApiClient.loadCustomerTransactions(customerId),result->{
            if(generation==activityGeneration&&customerId.equals(selectedCustomerId))activityHistory.showHistory(result);
        },error->{
            if(generation==activityGeneration&&customerId.equals(selectedCustomerId)){
                activityCustomerId=null;activityHistory.message("Unable to load activity. Use Refresh to retry.");
            }
        });
    }

    private void openAccountDetails() {
        CustomerSelection selection = getSelectedCustomer();
        if (selection == null) {
            JOptionPane.showMessageDialog(this, "Select a customer first.");
            return;
        }
        CustomerAccountDetails details = new CustomerAccountDetails(selection.customerId(), this::loadCustomers, this);
        WindowHelper.showPosWindow(details, this);
    }

    private void openPaymentHistory() {
        CustomerSelection selection = getSelectedCustomer();
        if (selection == null) {
            JOptionPane.showMessageDialog(this, "Select a customer first.");
            return;
        }
        CustomerPaymentHistory history = new CustomerPaymentHistory(selection.customerId(), selection.accountLabel());
        WindowHelper.showPosWindow(history, this);
    }

    private void openPaymentReceipt(int customerId, int transactionId) {
        WindowHelper.showPosWindow(new AccountPaymentReceiptPreview(customerId, transactionId), this);
    }

    private void openCustomerTypes() {
        if (WindowHelper.focusIfAlreadyOpen(CustomerTypeList.class)) {
            return;
        }
        WindowHelper.showPosWindow(new CustomerTypeList(), this);
    }

    private CustomerSelection getSelectedCustomer() {
        int row = customerTable.getSelectedRow();
        if (row == -1) {
            return null;
        }
        int modelRow = customerTable.convertRowIndexToModel(row);
        int customerId = Integer.parseInt(String.valueOf(customerModel.getValueAt(modelRow, 0)));
        String accountNumber = valueAt(modelRow, 1);
        String name = valueAt(modelRow, 2);
        String accountLabel = accountNumber.isBlank() ? name : accountNumber + " - " + name;
        return new CustomerSelection(customerId, accountLabel);
    }

    private void addCustomer() {
        String name = nameField.getText().trim();
        String phone = normalizedPhone();
        if (phone == null) return;
        String email = emailField.getText().trim();
        String accountNotes = accountNotesArea.getText().trim();
        Integer customerSince=parseCustomerSince(customerSinceField.getText());if(customerSinceField.getText()!=null&&!customerSinceField.getText().trim().isEmpty()&&customerSince==null)return;
        Integer customerTypeId = customerTypeSelector.getSelectedCustomerTypeId();
        if (customerTypeId == null && !customerTypeSelector.getSelectedCustomerTypeName().isBlank()) {
            return;
        }

        BigDecimal creditLimit = parseMoney(creditLimitField.getText().trim(), "Credit limit");
        BigDecimal salesDiscount=parseDiscount(salesDiscountPercentField,"Sales"),invoiceDiscount=parseDiscount(invoiceDiscountPercentField,"Invoice"),customOrderDiscount=parseDiscount(customOrderDiscountPercentField,"Custom-order");
        if(salesDiscount==null||invoiceDiscount==null||customOrderDiscount==null)return;
        if (creditLimit == null) {
            return;
        }
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Customer name is required.");
            return;
        }

        LanApiClient.CustomerAccountSaveRequest request=new LanApiClient.CustomerAccountSaveRequest(null,null,name,customerTypeId,
                phone,email,creditLimit,activeCheckBox.isSelected(),accountNotes,customerSince,customerPhotoSelector.getImageUrl(),whatsappOptInCheckBox.isSelected(),requireChargeAuthorizationCheckBox.isSelected(),salesDiscountEnabledBox.isSelected(),salesDiscount,invoiceDiscountEnabledBox.isSelected(),invoiceDiscount,customOrderDiscountEnabledBox.isSelected(),customOrderDiscount,customOrderDepositExemptBox.isSelected()).withAdditionalPhones(additionalPhone1Field.getText(),additionalPhone2Field.getText()).withPhoneLabels(phoneLabelField.getText(),additionalPhone1LabelField.getText(),additionalPhone2LabelField.getText());
        String fingerprint=request.toString();
        try {
            if(pendingSaveKey==null||!fingerprint.equals(pendingSaveFingerprint)){pendingSaveKey=UUID.randomUUID().toString();pendingSaveFingerprint=fingerprint;}
            String mutationKey=pendingSaveKey;
            UiTaskRunner.submit(this,"customer-accounts.create",()->LanApiClient.saveCustomerAccount(uploadCustomerPhoto(request, mutationKey),mutationKey),saved->{SessionDataCache.invalidate("customer-accounts:");pendingSaveKey=null;pendingSaveFingerprint=null;accountNumberField.setText(saved.accountNumber());
            JOptionPane.showMessageDialog(this, "Customer account added.");
            clearFields();
            loadCustomers();
            },ex->JOptionPane.showMessageDialog(this,"Failed to add customer account: "+ex.getMessage()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to add customer account: " + ex.getMessage());
        }
    }

    private void updateCustomer() {
        if (selectedCustomerId == null) {
            JOptionPane.showMessageDialog(this, "Select a customer first.");
            return;
        }

        String accountNumber = accountNumberField.getText().trim();
        String name = nameField.getText().trim();
        String phone = normalizedPhone();
        if (phone == null) return;
        String email = emailField.getText().trim();
        String accountNotes = accountNotesArea.getText().trim();
        Integer customerSince=parseCustomerSince(customerSinceField.getText());if(customerSinceField.getText()!=null&&!customerSinceField.getText().trim().isEmpty()&&customerSince==null)return;
        Integer customerTypeId = customerTypeSelector.getSelectedCustomerTypeId();
        if (customerTypeId == null && !customerTypeSelector.getSelectedCustomerTypeName().isBlank()) {
            return;
        }

        BigDecimal creditLimit = parseMoney(creditLimitField.getText().trim(), "Credit limit");
        BigDecimal salesDiscount=parseDiscount(salesDiscountPercentField,"Sales"),invoiceDiscount=parseDiscount(invoiceDiscountPercentField,"Invoice"),customOrderDiscount=parseDiscount(customOrderDiscountPercentField,"Custom-order");
        if(salesDiscount==null||invoiceDiscount==null||customOrderDiscount==null)return;
        if (creditLimit == null) {
            return;
        }
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Customer name is required.");
            return;
        }

        if (!PermissionManager.hasPermission("EDIT_ACCOUNT_NUMBER")) {
            accountNumber = valueAt(customerTable.convertRowIndexToModel(customerTable.getSelectedRow()), 1);
        }
        if (accountNumber.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Account number is required.");
            return;
        }

        LanApiClient.CustomerAccountSaveRequest request=new LanApiClient.CustomerAccountSaveRequest(selectedCustomerId,accountNumber,name,customerTypeId,
                phone,email,creditLimit,activeCheckBox.isSelected(),accountNotes,customerSince,customerPhotoSelector.getImageUrl(),whatsappOptInCheckBox.isSelected(),requireChargeAuthorizationCheckBox.isSelected(),salesDiscountEnabledBox.isSelected(),salesDiscount,invoiceDiscountEnabledBox.isSelected(),invoiceDiscount,customOrderDiscountEnabledBox.isSelected(),customOrderDiscount,customOrderDepositExemptBox.isSelected()).withAdditionalPhones(additionalPhone1Field.getText(),additionalPhone2Field.getText()).withPhoneLabels(phoneLabelField.getText(),additionalPhone1LabelField.getText(),additionalPhone2LabelField.getText());
        String fingerprint=request.toString();
        try {
            if(pendingSaveKey==null||!fingerprint.equals(pendingSaveFingerprint)){pendingSaveKey=UUID.randomUUID().toString();pendingSaveFingerprint=fingerprint;}
            String mutationKey=pendingSaveKey;
            UiTaskRunner.submit(this,"customer-accounts.update",()->{LanApiClient.saveCustomerAccount(uploadCustomerPhoto(request, mutationKey),mutationKey);return Boolean.TRUE;},ignored->{SessionDataCache.invalidate("customer-accounts:");pendingSaveKey=null;pendingSaveFingerprint=null;
            JOptionPane.showMessageDialog(this, "Customer account updated.");
            loadCustomers();
            },ex->JOptionPane.showMessageDialog(this,"Failed to update customer account: "+ex.getMessage()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to update customer account: " + ex.getMessage());
        }
    }

    private String normalizedPhone() {
        try {
            String phone = utils.CustomerPhoneNumber.normalize(phoneField.getText());
            phoneField.setText(phone);
            return phone;
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Phone number", JOptionPane.WARNING_MESSAGE);
            phoneField.requestFocusInWindow();
            return null;
        }
    }

    private synchronized LanApiClient.CustomerAccountSaveRequest uploadCustomerPhoto(LanApiClient.CustomerAccountSaveRequest request, String mutationKey) throws Exception {
        // A retry must send the same uploaded reference with the same idempotency key.
        if (mutationKey.equals(preparedPhotoKey)) return preparedPhotoRequest;
        String photo = ui.helpers.CustomerPhotoHelper.uploadIfNeeded(request.customerPhotoUrl());
        preparedPhotoRequest = new LanApiClient.CustomerAccountSaveRequest(request.customerId(), request.accountNumber(),
                request.name(), request.customerTypeId(), request.phone(), request.email(), request.creditLimit(),
                request.active(), request.accountNotes(), request.customerSince(), photo,
                request.whatsappOptIn(), request.requireChargeAuthorization(),request.salesDiscountEnabled(),request.salesDiscountPercent(),request.invoiceDiscountEnabled(),request.invoiceDiscountPercent(),request.customOrderDiscountEnabled(),request.customOrderDiscountPercent(),request.customOrderDepositExempt()).withAdditionalPhones(request.additionalPhone1(),request.additionalPhone2()).withPhoneLabels(request.phoneLabel(),request.additionalPhone1Label(),request.additionalPhone2Label());
        preparedPhotoKey = mutationKey;
        return preparedPhotoRequest;
    }

    private void adjustBalance(boolean addCharge) {
        if (selectedCustomerId == null) {
            JOptionPane.showMessageDialog(this, "Select a customer first.");
            return;
        }

        String label = addCharge ? "charge amount" : "payment amount";
        String input = JOptionPane.showInputDialog(this, "Enter " + label + ":");
        if (input == null) {
            return;
        }

        BigDecimal amount = parseMoney(input.trim(), addCharge ? "Charge" : "Payment");
        if (amount == null) {
            return;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(this, "Amount must be greater than zero.");
            return;
        }
        if(amount.stripTrailingZeros().scale()>0){JOptionPane.showMessageDialog(this,"Customer account amounts must use whole GYD values.");return;}

        String description = null;
        if(addCharge){
            JTextArea purpose=new JTextArea(4,32);purpose.setLineWrap(true);purpose.setWrapStyleWord(true);
            purpose.setToolTipText("Describe the goods, service or reason for this charge (up to 1,000 characters).");
            JPanel details=new JPanel(new BorderLayout(0,8));details.add(new JLabel("What is this charge for? *"),BorderLayout.NORTH);details.add(new JScrollPane(purpose),BorderLayout.CENTER);
            while(true){
                if(JOptionPane.showConfirmDialog(this,details,"Charge description",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)!=JOptionPane.OK_OPTION)return;
                description=purpose.getText().trim();
                if(!description.isBlank()&&description.length()<=1000)break;
                JOptionPane.showMessageDialog(this,description.isBlank()?"Enter what the charge is for.":"Keep the charge description to 1,000 characters or fewer.");
                purpose.requestFocusInWindow();
            }
        }
        String paymentMethod = null;
        String paymentReference = null;
        if (!addCharge) {
            paymentMethod = promptForPaymentMethod();
            if (paymentMethod == null) {
                return;
            }
            if (requiresPaymentReference(paymentMethod)) {
                paymentReference = JOptionPane.showInputDialog(this, "Enter payment reference:");
                if (paymentReference == null) {
                    return;
                }
                paymentReference = paymentReference.trim();
                if (paymentReference.isBlank()) {
                    JOptionPane.showMessageDialog(this, "Payment reference is required for card, cheque, and MMG payments.");
                    return;
                }
            }
        }

        List<LanApiClient.CustomerPaymentAllocationRequest> allocations=addCharge?List.of():selectPaymentAllocations(amount);
        if(!addCharge&&allocations==null)return;
        LanApiClient.CustomerAccountAdjustmentRequest request=new LanApiClient.CustomerAccountAdjustmentRequest(selectedCustomerId,amount,
                addCharge?"CHARGE":"PAYMENT",paymentMethod,paymentReference,allocations,description);String fingerprint=request.toString();
        try {
            if(pendingAdjustmentKey==null||!fingerprint.equals(pendingAdjustmentFingerprint)){pendingAdjustmentKey=UUID.randomUUID().toString();pendingAdjustmentFingerprint=fingerprint;}
            String mutationKey=pendingAdjustmentKey;Integer customerId=selectedCustomerId;
            UiTaskRunner.submit(this,"customer-accounts.adjust",()->LanApiClient.adjustCustomerAccount(request,mutationKey),result->{SessionDataCache.invalidate("customer-accounts:");pendingAdjustmentKey=null;pendingAdjustmentFingerprint=null;
            if(addCharge)JOptionPane.showMessageDialog(this,"Charge added.");else{
                JOptionPane.showMessageDialog(this,"Payment recorded. Payment ID: "+result.paymentId());
                openPaymentReceipt(customerId,(int)result.transactionId());
            }
            loadCustomers();
            },ex->JOptionPane.showMessageDialog(this,"Failed to update account balance: "+ex.getMessage()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to update account balance: " + ex.getMessage());
        }
    }

    private List<LanApiClient.CustomerPaymentAllocationRequest> selectPaymentAllocations(BigDecimal paymentAmount){
        List<LanApiClient.CustomerOpenBalance> balances;try{balances=ResponsiveTask.await(this,"Loading open customer balances...",()->LanApiClient.loadCustomerOpenBalances(selectedCustomerId));if(balances==null)return null;}catch(Exception ex){JOptionPane.showMessageDialog(this,"Unable to load open balances: "+ex.getMessage());return null;}
        if(balances.isEmpty()){JOptionPane.showMessageDialog(this,"This customer has no open balances.");return null;}
        DefaultTableModel model=new DefaultTableModel(new Object[]{"Pay","Type","Document","Date","Total","Paid","Balance Due","Apply"},0){@Override public Class<?> getColumnClass(int c){return c==0?Boolean.class:Object.class;}@Override public boolean isCellEditable(int r,int c){return c==0||c==7;}};
        BigDecimal remaining=paymentAmount;for(var b:balances){BigDecimal apply=remaining.signum()>0?remaining.min(b.balanceDue()):BigDecimal.ZERO;model.addRow(new Object[]{apply.signum()>0,b.documentType(),b.documentNumber(),new java.util.Date(b.createdAtEpochMillis()),b.total(),b.paid(),b.balanceDue(),apply});remaining=remaining.subtract(apply).max(BigDecimal.ZERO);}
        JTable table=new JTable(model);table.setRowHeight(26);JScrollPane scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(850,260));
        int choice=JOptionPane.showConfirmDialog(this,scroll,"Allocate Customer Payment",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);if(choice!=JOptionPane.OK_OPTION)return null;
        List<LanApiClient.CustomerPaymentAllocationRequest> out=new java.util.ArrayList<>();BigDecimal sum=BigDecimal.ZERO;for(int i=0;i<model.getRowCount();i++)if(Boolean.TRUE.equals(model.getValueAt(i,0))){try{BigDecimal amount=new BigDecimal(String.valueOf(model.getValueAt(i,7)));if(amount.signum()<=0||amount.compareTo(balances.get(i).balanceDue())>0)throw new Exception();out.add(new LanApiClient.CustomerPaymentAllocationRequest(balances.get(i).documentType(),balances.get(i).documentId(),amount));sum=sum.add(amount);}catch(Exception ex){JOptionPane.showMessageDialog(this,"Every selected allocation must be greater than zero and no more than its balance due.");return null;}}
        if(sum.compareTo(paymentAmount)!=0){JOptionPane.showMessageDialog(this,"Selected allocations must equal the payment amount of "+money(paymentAmount)+".");return null;}return List.copyOf(out);
    }

    private void clearFields() {
        activityGeneration++;
        activityCustomerId=null;
        if(activityHistory!=null)activityHistory.message("Select a customer to view activity.");
        selectedCustomerId = null;
        selectedAccount = null;
        accountNumberField.setText("Generated on save");
        accountNumberField.setEditable(false);
        nameField.setText("");
        phoneField.setText("");additionalPhone1Field.setText("");additionalPhone2Field.setText("");phoneLabelField.setText("");additionalPhone1LabelField.setText("");additionalPhone2LabelField.setText("");
        emailField.setText("");
        customerSinceField.setText("");
        customerTypeSelector.clearSelection();
        creditLimitField.setText("0");
        balanceField.setText("0");
        accountNotesArea.setText("");
        customerPhotoSelector.setImageUrl("");
        activeCheckBox.setSelected(true);
        whatsappOptInCheckBox.setSelected(false);
        requireChargeAuthorizationCheckBox.setSelected(false);customOrderDepositExemptBox.setSelected(false);
        salesDiscountEnabledBox.setSelected(false);salesDiscountPercentField.setText("0");invoiceDiscountEnabledBox.setSelected(false);invoiceDiscountPercentField.setText("0");customOrderDiscountEnabledBox.setSelected(false);customOrderDiscountPercentField.setText("0");
        addChargeButton.setEnabled(false);
        recordPaymentButton.setEnabled(false);
        transactionHistoryButton.setEnabled(false);
        paymentHistoryButton.setEnabled(false);
        previewCardButton.setEnabled(false);printCardButton.setEnabled(false);pdfCardButton.setEnabled(false);
        customerTable.clearSelection();
        refreshCustomerHeading();
        nameField.requestFocusInWindow();
    }

    private Integer parseCustomerSince(String value){String text=value==null?"":value.trim();if(text.isEmpty())return null;try{int year=Integer.parseInt(text),current=java.time.Year.now().getValue();if(text.length()!=4||year<1900||year>current)throw new NumberFormatException();return year;}catch(NumberFormatException ex){JOptionPane.showMessageDialog(this,"Customer Since must be a four-digit year from 1900 through "+java.time.Year.now().getValue()+".");return null;}}

    private JPanel discountControl(JCheckBox enabled,JTextField percent){JPanel p=CustomerAccountsDesign.panel(new FlowLayout(FlowLayout.LEFT,6,0));p.add(enabled);p.add(percent);p.add(new JLabel("%"));return p;}
    private String discountText(BigDecimal value){return value==null?"0":value.stripTrailingZeros().toPlainString();}
    private BigDecimal parseDiscount(JTextField field,String label){try{BigDecimal value=new BigDecimal(field.getText().trim().isEmpty()?"0":field.getText().trim());if(value.signum()<0||value.compareTo(BigDecimal.valueOf(100))>0)throw new NumberFormatException();return value;}catch(NumberFormatException ex){JOptionPane.showMessageDialog(this,label+" discount must be between 0 and 100%.");return null;}}

    private void applyCustomerFilter() {
        if (customerSorter == null) {
            return;
        }
        String text = searchField == null ? "" : searchField.getText().trim();
        java.util.List<RowFilter<DefaultTableModel,Integer>> filters = new java.util.ArrayList<>();
        if (!text.isEmpty()) filters.add(new RowFilter<DefaultTableModel,Integer>() {
 public boolean include(Entry<? extends DefaultTableModel,? extends Integer> entry) {
 String query=text.toLowerCase(java.util.Locale.ROOT);
 for(int col:new int[]{1,2,5,6})if(entry.getStringValue(col).toLowerCase(java.util.Locale.ROOT).contains(query))return true;
 LanApiClient.CustomerAccountRecord account=customerRecords.get(entry.getValue(0));
 return account!=null && ((account.additionalPhone1()!=null&&account.additionalPhone1().contains(text)) || (account.additionalPhone2()!=null&&account.additionalPhone2().contains(text)));
 }
 });
        if ("ACTIVE".equals(customerFilter)) filters.add(RowFilter.regexFilter("^true$", 13));
        if ("BUSINESS".equals(customerFilter)) filters.add(RowFilter.regexFilter("^Business$", 12));
        customerSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        if (selectedCustomerId != null && customerTable.getSelectedRow() < 0) clearFields();
        updateFilterLabels();
    }

    private void updateFilterLabels() {
        if (allFilter == null) return;
        long active = customerRecords.values().stream().filter(LanApiClient.CustomerAccountRecord::active).count();
        long business = customerRecords.values().stream().filter(row -> utils.CustomerAccountTypes.isBusiness(row.customerTypeName())).count();
        allFilter.setText("All customers (" + customerRecords.size() + ")");
        activeFilter.setText("Active (" + active + ")");
        businessFilter.setText("Business (" + business + ")");
        JButton[] buttons = {allFilter,activeFilter,businessFilter};
        String[] values = {"ALL","ACTIVE","BUSINESS"};
        for (int i=0;i<buttons.length;i++) {
            boolean selected = values[i].equals(customerFilter);
            buttons[i].putClientProperty("taskOutline", selected);
            buttons[i].getAccessibleContext().setAccessibleDescription(selected ? "Selected filter" : "Filter customers");
            buttons[i].repaint();
        }
        if (directoryArea != null) { directoryArea.revalidate(); directoryArea.repaint(); }
    }

    private String valueAt(int row, int column) {
        Object value = customerModel.getValueAt(row, column);
        return value == null ? "" : value.toString();
    }

    private Integer parseNullableInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String promptForPaymentMethod() {
        String[] options = {"CASH", "CARD", "CHEQUE", "MMG", "BANK_TRANSFER", "APPLY_CREDIT"};
        Object selected = JOptionPane.showInputDialog(
                this,
                "Select payment method (APPLY_CREDIT uses released minutes credit):",
                "Payment Method",
                JOptionPane.PLAIN_MESSAGE,
                null,
                options,
                options[0]
        );
        if (selected == null) {
            return null;
        }
        return selected.toString();
    }

    private boolean requiresPaymentReference(String paymentMethod) {
        return "CARD".equalsIgnoreCase(paymentMethod)
                || "CHEQUE".equalsIgnoreCase(paymentMethod)
                || "MMG".equalsIgnoreCase(paymentMethod);
    }

    private String stripMoney(String value) {
        return value == null ? "" : value.replace("G$", "").replace("$", "").replace(",", "").trim();
    }

    private String money(BigDecimal value) {
        value = defaultZero(value);
        return "G$" + java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(utils.CurrencyFormatter.normalize(value));
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal parseMoney(String value, String fieldName) {
        try {
            return utils.CurrencyFormatter.normalize(new BigDecimal(stripMoney(value)));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, fieldName + " must be a valid amount.");
            return null;
        }
    }

    private record CustomerSelection(int customerId, String accountLabel) {
    }
}
