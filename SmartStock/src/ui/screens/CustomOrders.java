package ui.screens;

import data.DB;
import managers.PermissionManager;
import managers.SessionManager;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class CustomOrders extends JFrame {
    private final boolean canCreateOrders = PermissionManager.hasPermission("CREATE_CUSTOM_ORDER");
    private final boolean canManageOrders = PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS");
    private final boolean canViewAssignedOrders = PermissionManager.hasPermission("VIEW_ASSIGNED_CUSTOM_ORDERS");

    private JComboBox<CustomerOption> customerBox;
    private JTextField customerSearchField;
    private JTextField dueDateField;
    private JTextArea orderNotesArea;
    private JComboBox<CustomItemOption> orderItemBox;
    private JComboBox<VariantOption> variantBox;
    private JTextField linePriceField;
    private JTextField lineQuantityField;
    private JTextField widthField;
    private JTextField lengthField;
    private JLabel areaCalculationLabel;
    private JTextArea customizationArea;
    private JTextArea lineNotesArea;
    private DefaultTableModel orderLineModel;
    private JTable orderLineTable;
    private JButton addLineButton;
    private int selectedOrderLineModelRow = -1;
    private JLabel orderTotalLabel;
    private ButtonGroup paymentMethodGroup;
    private JToggleButton cashPaymentButton;
    private JToggleButton cardPaymentButton;
    private JToggleButton chequePaymentButton;
    private JToggleButton accountPaymentButton;
    private String selectedPaymentMethod;
    private JTextField paymentReferenceField;
    private JTextField upfrontPaymentField;
    private JLabel balanceDueLabel;
    private final List<JComponent> areaLineComponents = new ArrayList<>();

    private DefaultTableModel ordersModel;
    private JTable ordersTable;
    private TableRowSorter<DefaultTableModel> ordersSorter;
    private JTextField orderSearchField;
    private JComboBox<String> statusFilterBox;
    private JComboBox<EmployeeOption> assignEmployeeBox;
    private JComboBox<String> manageStatusBox;
    private JTextArea selectedOrderDetailsArea;
    private Long selectedOrderId;
    private DefaultTableModel myOrdersModel;
    private JTable myOrdersTable;
    private TableRowSorter<DefaultTableModel> myOrdersSorter;
    private JTextField myOrderSearchField;
    private JComboBox<String> myStatusFilterBox;
    private JTextArea myOrderDetailsArea;

    public CustomOrders() {
        this(false);
    }

    protected CustomOrders(boolean orderManagementMode) {
        setTitle(orderManagementMode ? "Orders" : "Custom Orders");
        setSize(1180, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setJMenuBar(AppMenuBar.create(this, orderManagementMode ? "Orders" : "CustomOrders"));

        JTabbedPane tabs = new JTabbedPane();
        if (!orderManagementMode && canCreateOrders) {
            tabs.addTab("New Order", buildOrderEntryPanel());
        }
        if (!orderManagementMode && canCreateOrders) {
            tabs.addTab("Order Lookup", buildOrderLookupPanel());
        }
        if (orderManagementMode && (canViewAssignedOrders || canManageOrders)) {
            tabs.addTab("My Orders", buildMyOrdersPanel());
        }
        if (orderManagementMode && canManageOrders) {
            tabs.addTab("Manage Orders", buildManageOrdersPanel());
        }
        if (tabs.getTabCount() == 0) {
            add(new JLabel("You do not have permission to access this screen.", SwingConstants.CENTER), BorderLayout.CENTER);
        } else {
            add(tabs, BorderLayout.CENTER);
        }

        loadItems();
        loadCustomers("");
        loadEmployees();
        loadOrders();
        loadMyOrders();
        WindowHelper.showPosWindow(this);
    }

    private JPanel buildOrderEntryPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createTitledBorder("Customer"));
        GridBagConstraints gbc = formGbc();
        customerSearchField = new JTextField();
        customerBox = new JComboBox<>();
        dueDateField = new JTextField();
        orderNotesArea = new JTextArea(3, 20);
        orderNotesArea.setLineWrap(true);
        orderNotesArea.setWrapStyleWord(true);
        JButton searchCustomerButton = new JButton("Search");
        JButton newCustomerButton = new JButton("New Customer");

        addField(top, gbc, 0, "Search:", customerSearchField);
        gbc.gridx = 2;
        top.add(searchCustomerButton, gbc);
        addField(top, gbc, 1, "Customer:", customerBox);
        gbc.gridx = 2;
        top.add(newCustomerButton, gbc);
        addField(top, gbc, 2, "Due Date:", dueDateField);
        addField(top, gbc, 3, "Notes:", new JScrollPane(orderNotesArea));

        searchCustomerButton.addActionListener(e -> loadCustomers(customerSearchField.getText().trim()));
        newCustomerButton.addActionListener(e -> {
            QuickCustomerAccount frame = new QuickCustomerAccount(() -> loadCustomers(customerSearchField.getText().trim()));
            frame.setLocationRelativeTo(this);
            frame.setVisible(true);
        });

        JPanel linePanel = new JPanel(new GridBagLayout());
        linePanel.setBorder(BorderFactory.createTitledBorder("Line Item"));
        GridBagConstraints lineGbc = formGbc();
        orderItemBox = new JComboBox<>();
        variantBox = new JComboBox<>();
        linePriceField = new JTextField();
        lineQuantityField = new JTextField("1");
        widthField = new JTextField();
        lengthField = new JTextField();
        areaCalculationLabel = new JLabel(" ");
        customizationArea = new JTextArea(4, 20);
        customizationArea.setLineWrap(true);
        customizationArea.setWrapStyleWord(true);
        lineNotesArea = new JTextArea(2, 20);
        lineNotesArea.setLineWrap(true);
        lineNotesArea.setWrapStyleWord(true);
        addLineButton = new JButton("Add Line");
        JButton removeLineButton = new JButton("Remove Selected");

        addField(linePanel, lineGbc, 0, "Item:", orderItemBox);
        addField(linePanel, lineGbc, 1, "Size / Variant:", variantBox);
        addField(linePanel, lineGbc, 2, "Price:", linePriceField);
        addField(linePanel, lineGbc, 3, "Quantity:", lineQuantityField);
        addTrackedField(areaLineComponents, linePanel, lineGbc, 4, "Width:", widthField);
        addTrackedField(areaLineComponents, linePanel, lineGbc, 5, "Length:", lengthField);
        lineGbc.gridx = 1;
        lineGbc.gridy = 6;
        linePanel.add(areaCalculationLabel, lineGbc);
        areaLineComponents.add(areaCalculationLabel);
        addField(linePanel, lineGbc, 7, "Customization:", new JScrollPane(customizationArea));
        addField(linePanel, lineGbc, 8, "Line Notes:", new JScrollPane(lineNotesArea));
        lineGbc.gridx = 1;
        lineGbc.gridy = 9;
        JPanel lineButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lineButtons.add(addLineButton);
        lineButtons.add(removeLineButton);
        linePanel.add(lineButtons, lineGbc);

        orderItemBox.addActionListener(e -> {
            loadVariantsForSelectedItem();
            applySelectedOrderItemPrice();
        });
        variantBox.addActionListener(e -> applySelectedOrderItemPrice());
        widthField.getDocument().addDocumentListener(simpleDocumentListener(this::updateAreaCalculationPreview));
        lengthField.getDocument().addDocumentListener(simpleDocumentListener(this::updateAreaCalculationPreview));
        addLineButton.addActionListener(e -> addOrderLine());
        removeLineButton.addActionListener(e -> removeSelectedOrderLine());

        orderLineModel = new DefaultTableModel(new Object[]{"Item ID", "Variant ID", "Item", "Size / Variant", "Pricing", "Price", "Details", "Notes", "Width", "Length", "Dimension Unit", "Area", "Area Unit", "Area Price"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        orderLineTable = new JTable(orderLineModel);
        orderLineTable.setRowHeight(28);
        orderLineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        orderLineTable.getColumnModel().getColumn(0).setMinWidth(0);
        orderLineTable.getColumnModel().getColumn(0).setMaxWidth(0);
        orderLineTable.getColumnModel().getColumn(0).setPreferredWidth(0);
        orderLineTable.getColumnModel().getColumn(1).setMinWidth(0);
        orderLineTable.getColumnModel().getColumn(1).setMaxWidth(0);
        orderLineTable.getColumnModel().getColumn(1).setPreferredWidth(0);
        for (int hiddenColumn = 8; hiddenColumn <= 13; hiddenColumn++) {
            orderLineTable.getColumnModel().getColumn(hiddenColumn).setMinWidth(0);
            orderLineTable.getColumnModel().getColumn(hiddenColumn).setMaxWidth(0);
            orderLineTable.getColumnModel().getColumn(hiddenColumn).setPreferredWidth(0);
        }
        orderLineTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedCartLineIntoEditor();
            }
        });
        orderTotalLabel = new JLabel("Total: $0.00");
        orderTotalLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        paymentMethodGroup = new ButtonGroup();
        cashPaymentButton = createPaymentMethodButton("Cash", "CASH");
        cardPaymentButton = createPaymentMethodButton("Card", "CARD");
        chequePaymentButton = createPaymentMethodButton("Cheque", "CHEQUE");
        accountPaymentButton = createPaymentMethodButton("Account", "ACCOUNT");
        paymentReferenceField = new JTextField();
        upfrontPaymentField = new JTextField("0.00", 8);
        balanceDueLabel = new JLabel("Balance Due: $0.00");
        balanceDueLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        upfrontPaymentField.getDocument().addDocumentListener(simpleDocumentListener(this::updatePaymentPreview));
        updatePaymentButtonStyles();
        updatePaymentReferenceState();

        JPanel center = new JPanel(new GridLayout(1, 2, 12, 0));
        center.add(linePanel);
        center.add(new JScrollPane(orderLineTable));

        JButton saveOrderButton = new JButton("Save Custom Order");
        JButton clearOrderButton = new JButton("Clear Order");
        saveOrderButton.addActionListener(e -> saveCustomOrder());
        clearOrderButton.addActionListener(e -> clearOrderEntry());

        JPanel footer = new JPanel(new BorderLayout());
        JPanel paymentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        paymentPanel.setBorder(BorderFactory.createTitledBorder("Payment Method"));
        paymentPanel.add(cashPaymentButton);
        paymentPanel.add(cardPaymentButton);
        paymentPanel.add(chequePaymentButton);
        paymentPanel.add(accountPaymentButton);
        paymentPanel.add(new JLabel("Upfront:"));
        paymentPanel.add(upfrontPaymentField);
        paymentPanel.add(new JLabel("Reference:"));
        paymentReferenceField.setPreferredSize(new Dimension(150, 30));
        paymentPanel.add(paymentReferenceField);
        JPanel footerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footerButtons.add(clearOrderButton);
        footerButtons.add(saveOrderButton);
        footer.add(orderTotalLabel, BorderLayout.WEST);
        footer.add(paymentPanel, BorderLayout.CENTER);
        footerButtons.add(balanceDueLabel);
        footer.add(footerButtons, BorderLayout.EAST);

        panel.add(top, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildManageOrdersPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel filterPanel = new JPanel(new BorderLayout(8, 0));
        orderSearchField = new JTextField();
        statusFilterBox = new JComboBox<>(new String[]{"All", "NEW", "ASSIGNED", "IN_PROGRESS", "READY", "COMPLETED", "DELIVERED", "CANCELLED"});
        filterPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        filterPanel.add(orderSearchField, BorderLayout.CENTER);
        filterPanel.add(statusFilterBox, BorderLayout.EAST);

        ordersModel = new DefaultTableModel(new Object[]{"ID", "Order #", "Status", "Customer", "Phone", "Due", "Total", "Paid", "Balance", "Payment", "Payment Reference", "Assigned To", "Taken By", "Created"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        ordersSorter = new TableRowSorter<>(ordersModel);
        ordersTable = new JTable(ordersModel);
        ordersTable.setRowSorter(ordersSorter);
        ordersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        ordersTable.setRowHeight(28);
        ordersTable.getColumnModel().getColumn(0).setMaxWidth(70);
        ordersTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedOrder();
            }
        });
        orderSearchField.getDocument().addDocumentListener(simpleDocumentListener(this::applyOrderFilter));
        statusFilterBox.addActionListener(e -> applyOrderFilter());

        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.add(filterPanel, BorderLayout.NORTH);
        left.add(new JScrollPane(ordersTable), BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setPreferredSize(new Dimension(420, 0));
        right.setBorder(BorderFactory.createTitledBorder("Assignment"));
        JPanel assignPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = formGbc();
        assignEmployeeBox = new JComboBox<>();
        manageStatusBox = new JComboBox<>(new String[]{"NEW", "ASSIGNED", "IN_PROGRESS", "READY", "COMPLETED", "DELIVERED", "CANCELLED"});
        selectedOrderDetailsArea = new JTextArea();
        selectedOrderDetailsArea.setEditable(false);
        selectedOrderDetailsArea.setLineWrap(true);
        selectedOrderDetailsArea.setWrapStyleWord(true);

        addField(assignPanel, gbc, 0, "Assign To:", assignEmployeeBox);
        addField(assignPanel, gbc, 1, "Status:", manageStatusBox);
        JButton assignButton = new JButton("Save Assignment");
        JButton refreshButton = new JButton("Refresh");
        gbc.gridx = 1;
        gbc.gridy = 2;
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(assignButton);
        buttons.add(refreshButton);
        assignPanel.add(buttons, gbc);
        assignButton.addActionListener(e -> saveAssignment());
        refreshButton.addActionListener(e -> loadOrders());

        right.add(assignPanel, BorderLayout.NORTH);
        right.add(new JScrollPane(selectedOrderDetailsArea), BorderLayout.CENTER);

        panel.add(left, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private JToggleButton createPaymentMethodButton(String label, String method) {
        JToggleButton button = new JToggleButton(label);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension("ACCOUNT".equals(method) ? 120 : 96, 42));
        button.addActionListener(e -> selectPaymentMethod(method));
        paymentMethodGroup.add(button);
        return button;
    }

    private void selectPaymentMethod(String method) {
        selectedPaymentMethod = method;
        if ("CASH".equals(method) && cashPaymentButton != null) {
            cashPaymentButton.setSelected(true);
        } else if ("CARD".equals(method) && cardPaymentButton != null) {
            cardPaymentButton.setSelected(true);
        } else if ("CHEQUE".equals(method) && chequePaymentButton != null) {
            chequePaymentButton.setSelected(true);
        } else if ("ACCOUNT".equals(method) && accountPaymentButton != null) {
            accountPaymentButton.setSelected(true);
        }
        updatePaymentButtonStyles();
        updatePaymentReferenceState();
    }

    private void updatePaymentButtonStyles() {
        stylePaymentButton(cashPaymentButton, "CASH".equals(selectedPaymentMethod));
        stylePaymentButton(cardPaymentButton, "CARD".equals(selectedPaymentMethod));
        stylePaymentButton(chequePaymentButton, "CHEQUE".equals(selectedPaymentMethod));
        stylePaymentButton(accountPaymentButton, "ACCOUNT".equals(selectedPaymentMethod));
    }

    private void stylePaymentButton(JToggleButton button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setBackground(selected ? new Color(30, 64, 175) : new Color(64, 64, 64));
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createLineBorder(selected ? new Color(147, 197, 253) : new Color(120, 120, 120), selected ? 2 : 1));
        button.setOpaque(true);
    }

    private void updatePaymentReferenceState() {
        if (paymentReferenceField == null) {
            return;
        }
        boolean needsReference = selectedPaymentMethod != null && !"CASH".equals(selectedPaymentMethod);
        paymentReferenceField.setEnabled(needsReference);
        if (!needsReference) {
            paymentReferenceField.setText("");
        }
        paymentReferenceField.setToolTipText(needsReference ? "Enter check number, card transaction ID, or account reference." : "Reference is only used for non-cash payments.");
    }

    private void styleDialogButton(JButton button) {
        button.setBackground(new Color(64, 64, 64));
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 120), 1));
        button.setFocusPainted(false);
        button.setOpaque(true);
    }

    private JPanel buildMyOrdersPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel filterPanel = new JPanel(new BorderLayout(8, 0));
        myOrderSearchField = new JTextField();
        myStatusFilterBox = new JComboBox<>(new String[]{"All", "ASSIGNED", "IN_PROGRESS", "READY", "COMPLETED", "DELIVERED", "CANCELLED"});
        JButton refreshButton = new JButton("Refresh");
        filterPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        filterPanel.add(myOrderSearchField, BorderLayout.CENTER);
        JPanel filterRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterRight.add(myStatusFilterBox);
        filterRight.add(refreshButton);
        filterPanel.add(filterRight, BorderLayout.EAST);

        myOrdersModel = new DefaultTableModel(new Object[]{"ID", "Order #", "Status", "Customer", "Phone", "Due", "Total", "Paid", "Balance", "Payment", "Payment Reference", "Created"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        myOrdersSorter = new TableRowSorter<>(myOrdersModel);
        myOrdersTable = new JTable(myOrdersModel);
        myOrdersTable.setRowSorter(myOrdersSorter);
        myOrdersTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        myOrdersTable.setRowHeight(28);
        myOrdersTable.getColumnModel().getColumn(0).setMaxWidth(70);
        myOrdersTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedMyOrder();
            }
        });
        myOrderSearchField.getDocument().addDocumentListener(simpleDocumentListener(this::applyMyOrderFilter));
        myStatusFilterBox.addActionListener(e -> applyMyOrderFilter());
        refreshButton.addActionListener(e -> loadMyOrders());

        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.add(filterPanel, BorderLayout.NORTH);
        left.add(new JScrollPane(myOrdersTable), BorderLayout.CENTER);

        myOrderDetailsArea = new JTextArea();
        myOrderDetailsArea.setEditable(false);
        myOrderDetailsArea.setLineWrap(true);
        myOrderDetailsArea.setWrapStyleWord(true);

        JPanel right = new JPanel(new BorderLayout());
        right.setPreferredSize(new Dimension(420, 0));
        right.setBorder(BorderFactory.createTitledBorder("Order Details"));
        right.add(new JScrollPane(myOrderDetailsArea), BorderLayout.CENTER);

        panel.add(left, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private void loadItems() {
        List<CustomItemOption> options = new ArrayList<>();
        String sql = """
                SELECT custom_item_id, item_name, description, product_type, pricing_type, fixed_price,
                       area_price, area_price_unit, dimension_unit, max_width, max_length,
                       COALESCE(has_variants, FALSE) AS has_variants,
                       is_active
                FROM custom_order_items
                ORDER BY is_active DESC, item_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Long id = rs.getLong("custom_item_id");
                String name = rs.getString("item_name");
                String productType = rs.getString("product_type");
                String pricingType = rs.getString("pricing_type");
                BigDecimal fixedPrice = rs.getBigDecimal("fixed_price");
                BigDecimal areaPrice = fixedPrice;
                BigDecimal legacyAreaPrice = rs.getBigDecimal("area_price");
                if (areaPrice == null) {
                    areaPrice = legacyAreaPrice;
                }
                if (fixedPrice == null && "AREA".equals(pricingType)) {
                    fixedPrice = areaPrice;
                }
                String areaPriceUnit = rs.getString("area_price_unit");
                String dimensionUnit = rs.getString("dimension_unit");
                BigDecimal maxWidth = rs.getBigDecimal("max_width");
                BigDecimal maxLength = rs.getBigDecimal("max_length");
                boolean hasVariants = rs.getBoolean("has_variants");
                boolean active = rs.getBoolean("is_active");
                String description = rs.getString("description");
                if (active) {
                    options.add(new CustomItemOption(id, name, productType, pricingType, fixedPrice, hasVariants, areaPrice, areaPriceUnit, dimensionUnit, maxWidth, maxLength));
                }
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }

        if (orderItemBox != null) {
            orderItemBox.removeAllItems();
            for (CustomItemOption option : options) {
                orderItemBox.addItem(option);
            }
            loadVariantsForSelectedItem();
            applySelectedOrderItemPrice();
        }
    }

    private void loadVariantsForSelectedItem() {
        if (variantBox == null) {
            return;
        }
        variantBox.removeAllItems();
        variantBox.addItem(new VariantOption(null, "No Variant", null));
        CustomItemOption item = orderItemBox == null ? null : (CustomItemOption) orderItemBox.getSelectedItem();
        if (item == null || item.customItemId() == null) {
            return;
        }

        String sql = """
                SELECT custom_variant_id, variant_name, fixed_price
                FROM custom_order_item_variants
                WHERE custom_item_id = ?
                  AND is_active = TRUE
                ORDER BY variant_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, item.customItemId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    variantBox.addItem(new VariantOption(rs.getLong("custom_variant_id"), rs.getString("variant_name"), rs.getBigDecimal("fixed_price")));
                }
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private void loadCustomers(String search) {
        if (customerBox == null) {
            return;
        }
        customerBox.removeAllItems();
        String sql = """
                SELECT customer_id, name, phone
                FROM customer_accounts
                WHERE is_active = TRUE
                  AND (? = '' OR LOWER(name) LIKE LOWER(?) OR COALESCE(phone, '') LIKE ?)
                ORDER BY name
                LIMIT 100
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + search + "%";
            ps.setString(1, search);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    customerBox.addItem(new CustomerOption(
                            rs.getInt("customer_id"),
                            rs.getString("name"),
                            rs.getString("phone")
                    ));
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load customers: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadEmployees() {
        if (assignEmployeeBox == null) {
            return;
        }
        assignEmployeeBox.removeAllItems();
        assignEmployeeBox.addItem(new EmployeeOption(null, "Unassigned"));
        String sql = """
                SELECT u.user_id, COALESCE(NULLIF(u.full_name, ''), u.username) AS employee_name
                FROM users u
                WHERE COALESCE(u.is_active, TRUE) = TRUE
                ORDER BY employee_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                assignEmployeeBox.addItem(new EmployeeOption(rs.getInt("user_id"), rs.getString("employee_name")));
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load employees: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadOrders() {
        if (ordersModel == null) {
            return;
        }
        ordersModel.setRowCount(0);
        String sql = """
                SELECT custom_order_id, order_number, status, customer_name, customer_phone, due_date,
                       total_amount, amount_paid, balance_due, payment_method, payment_reference,
                       payment_status, assigned_to_name, taken_by_name, created_at
                FROM custom_orders
                ORDER BY created_at DESC
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ordersModel.addRow(new Object[]{
                        rs.getLong("custom_order_id"),
                        rs.getString("order_number"),
                        rs.getString("status"),
                        rs.getString("customer_name"),
                        rs.getString("customer_phone"),
                        rs.getDate("due_date"),
                        formatMoney(rs.getBigDecimal("total_amount")),
                        formatMoney(rs.getBigDecimal("amount_paid")),
                        formatMoney(rs.getBigDecimal("balance_due")),
                        formatPayment(rs.getString("payment_method"), rs.getString("payment_status")),
                        rs.getString("payment_reference"),
                        rs.getString("assigned_to_name"),
                        rs.getString("taken_by_name"),
                        rs.getTimestamp("created_at")
                });
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
        applyOrderFilter();
    }

    private void loadMyOrders() {
        if (myOrdersModel == null) {
            return;
        }
        myOrdersModel.setRowCount(0);
        myOrderDetailsArea.setText("");

        if (SessionManager.getCurrentUserId() == null) {
            return;
        }

        String sql = """
                SELECT custom_order_id, order_number, status, customer_name, customer_phone, due_date,
                       total_amount, amount_paid, balance_due, payment_method, payment_reference,
                       payment_status, created_at
                FROM custom_orders
                WHERE assigned_to_user_id = ?
                ORDER BY
                    CASE status
                        WHEN 'ASSIGNED' THEN 1
                        WHEN 'IN_PROGRESS' THEN 2
                        WHEN 'READY' THEN 3
                        WHEN 'COMPLETED' THEN 4
                        WHEN 'DELIVERED' THEN 5
                        WHEN 'CANCELLED' THEN 6
                        ELSE 6
                    END,
                    due_date NULLS LAST,
                    created_at DESC
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, SessionManager.getCurrentUserId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    myOrdersModel.addRow(new Object[]{
                            rs.getLong("custom_order_id"),
                            rs.getString("order_number"),
                            rs.getString("status"),
                            rs.getString("customer_name"),
                            rs.getString("customer_phone"),
                            rs.getDate("due_date"),
                            formatMoney(rs.getBigDecimal("total_amount")),
                            formatMoney(rs.getBigDecimal("amount_paid")),
                            formatMoney(rs.getBigDecimal("balance_due")),
                            formatPayment(rs.getString("payment_method"), rs.getString("payment_status")),
                            rs.getString("payment_reference"),
                            rs.getTimestamp("created_at")
                    });
                }
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
        applyMyOrderFilter();
    }

    private void saveCustomOrder() {
        CustomerOption customer = (CustomerOption) customerBox.getSelectedItem();
        if (customer == null) {
            JOptionPane.showMessageDialog(this, "Select a customer account before saving the order.");
            return;
        }
        if (customer.phone() == null || customer.phone().isBlank()) {
            JOptionPane.showMessageDialog(this, "The customer account needs a phone number for custom orders.");
            return;
        }
        if (orderLineModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Add at least one customized line item.");
            return;
        }
        LocalDate dueDate = null;
        String dueDateText = dueDateField.getText().trim();
        if (!dueDateText.isEmpty()) {
            try {
                dueDate = LocalDate.parse(dueDateText);
            } catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(this, "Due date must use YYYY-MM-DD.");
                return;
            }
        }

        BigDecimal total = calculateOrderTotal();
        BigDecimal upfrontPaid = parseMoney(upfrontPaymentField.getText().trim().isEmpty() ? "0" : upfrontPaymentField.getText().trim(), "Upfront payment");
        if (upfrontPaid == null) {
            return;
        }
        if (upfrontPaid.compareTo(total) > 0) {
            JOptionPane.showMessageDialog(this, "Upfront payment cannot be more than the order total.");
            return;
        }
        if (upfrontPaid.compareTo(BigDecimal.ZERO) > 0 && (selectedPaymentMethod == null || selectedPaymentMethod.isBlank())) {
            JOptionPane.showMessageDialog(this, "Select a payment method for the upfront payment.");
            return;
        }
        String paymentReference = paymentReferenceField.getText().trim();
        if (upfrontPaid.compareTo(BigDecimal.ZERO) > 0
                && ("CARD".equals(selectedPaymentMethod) || "CHEQUE".equals(selectedPaymentMethod))
                && paymentReference.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter a payment reference for card or cheque payments.");
            return;
        }
        BigDecimal balanceDue = total.subtract(upfrontPaid);
        String paymentStatus = upfrontPaid.compareTo(BigDecimal.ZERO) == 0
                ? "UNPAID"
                : balanceDue.compareTo(BigDecimal.ZERO) == 0 ? "PAID" : "PARTIAL";
        String orderNumber = generateOrderNumber();
        String orderSql = """
                INSERT INTO custom_orders (
                    order_number, customer_id, customer_name, customer_phone, status, due_date,
                    order_notes, total_amount, amount_paid, balance_due, payment_method,
                    payment_reference, payment_status, taken_by_user_id, taken_by_name
                )
                VALUES (?, ?, ?, ?, 'NEW', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String lineSql = """
                INSERT INTO custom_order_lines (
                    custom_order_id, custom_item_id, item_name, pricing_type, unit_price,
                    line_total, customization_details, line_notes, sort_order,
                    custom_variant_id, variant_name,
                    width_value, length_value, dimension_unit, area_value, area_unit, area_price
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        String updateCustomItemSoldSql = """
                UPDATE custom_order_items
                SET sold_quantity = COALESCE(sold_quantity, 0) + 1,
                    quantity_on_hand = CASE WHEN COALESCE(product_type, 'INVENTORY') = 'INVENTORY' AND COALESCE(has_variants, FALSE) = FALSE THEN quantity_on_hand - 1 ELSE quantity_on_hand END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_item_id = ?
                """;
        String updateCustomVariantSoldSql = """
                UPDATE custom_order_item_variants v
                SET sold_quantity = COALESCE(v.sold_quantity, 0) + 1,
                    quantity_on_hand = CASE WHEN COALESCE(i.product_type, 'INVENTORY') = 'INVENTORY' THEN v.quantity_on_hand - 1 ELSE v.quantity_on_hand END,
                    updated_at = CURRENT_TIMESTAMP
                FROM custom_order_items i
                WHERE v.custom_item_id = i.custom_item_id
                  AND v.custom_variant_id = ?
                """;
        String refreshVariantParentSql = """
                UPDATE custom_order_items i
                SET quantity_on_hand = COALESCE((SELECT SUM(quantity_on_hand) FROM custom_order_item_variants WHERE custom_item_id = i.custom_item_id AND is_active = TRUE), 0),
                    sold_quantity = COALESCE((SELECT SUM(sold_quantity) FROM custom_order_item_variants WHERE custom_item_id = i.custom_item_id), 0),
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_item_id = ?
                  AND has_variants = TRUE
                """;
        String paymentSql = """
                INSERT INTO custom_order_payments (
                    custom_order_id, payment_amount, payment_method, payment_reference,
                    taken_by_user_id, taken_by_name
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement orderPs = conn.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement linePs = conn.prepareStatement(lineSql);
                 PreparedStatement itemSoldPs = conn.prepareStatement(updateCustomItemSoldSql);
                 PreparedStatement variantSoldPs = conn.prepareStatement(updateCustomVariantSoldSql);
                 PreparedStatement refreshParentPs = conn.prepareStatement(refreshVariantParentSql);
                 PreparedStatement paymentPs = conn.prepareStatement(paymentSql)) {
                orderPs.setString(1, orderNumber);
                orderPs.setInt(2, customer.customerId());
                orderPs.setString(3, customer.name());
                orderPs.setString(4, customer.phone());
                if (dueDate == null) {
                    orderPs.setNull(5, Types.DATE);
                } else {
                    orderPs.setDate(5, Date.valueOf(dueDate));
                }
                String notes = orderNotesArea.getText().trim();
                orderPs.setString(6, notes.isEmpty() ? null : notes);
                orderPs.setBigDecimal(7, total);
                orderPs.setBigDecimal(8, upfrontPaid);
                orderPs.setBigDecimal(9, balanceDue);
                orderPs.setString(10, selectedPaymentMethod);
                orderPs.setString(11, paymentReference.isBlank() ? null : paymentReference);
                orderPs.setString(12, paymentStatus);
                setNullableInteger(orderPs, 13, SessionManager.getCurrentUserId());
                orderPs.setString(14, SessionManager.getCurrentUserDisplayName());
                orderPs.executeUpdate();

                long orderId;
                try (ResultSet rs = orderPs.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new SQLException("Failed to get custom order ID.");
                    }
                    orderId = rs.getLong(1);
                }

                if (upfrontPaid.compareTo(BigDecimal.ZERO) > 0) {
                    paymentPs.setLong(1, orderId);
                    paymentPs.setBigDecimal(2, upfrontPaid);
                    paymentPs.setString(3, selectedPaymentMethod);
                    paymentPs.setString(4, paymentReference.isBlank() ? null : paymentReference);
                    setNullableInteger(paymentPs, 5, SessionManager.getCurrentUserId());
                    paymentPs.setString(6, SessionManager.getCurrentUserDisplayName());
                    paymentPs.executeUpdate();
                }

                for (int i = 0; i < orderLineModel.getRowCount(); i++) {
                    Object itemIdValue = orderLineModel.getValueAt(i, 0);
                    Object variantIdValue = orderLineModel.getValueAt(i, 1);
                    if (itemIdValue == null) {
                        linePs.setNull(2, Types.BIGINT);
                    } else {
                        linePs.setLong(2, Long.parseLong(itemIdValue.toString()));
                    }
                    BigDecimal unitPrice = parseMoneyValue(orderLineModel.getValueAt(i, 5).toString());
                    linePs.setLong(1, orderId);
                    linePs.setString(3, orderLineModel.getValueAt(i, 2).toString());
                    linePs.setString(4, orderLineModel.getValueAt(i, 4).toString());
                    linePs.setBigDecimal(5, unitPrice);
                    linePs.setBigDecimal(6, unitPrice);
                    linePs.setString(7, orderLineModel.getValueAt(i, 6).toString());
                    Object lineNotes = orderLineModel.getValueAt(i, 7);
                    linePs.setString(8, lineNotes == null || lineNotes.toString().isBlank() ? null : lineNotes.toString());
                    linePs.setInt(9, i + 1);
                    if (variantIdValue == null || variantIdValue.toString().isBlank()) {
                        linePs.setNull(10, Types.BIGINT);
                    } else {
                        linePs.setLong(10, Long.parseLong(variantIdValue.toString()));
                    }
                    Object variantName = orderLineModel.getValueAt(i, 3);
                    linePs.setString(11, variantName == null || variantName.toString().isBlank() ? null : variantName.toString());
                    setNullableBigDecimal(linePs, 12, parseNullableMoneyValue(orderLineModel.getValueAt(i, 8)));
                    setNullableBigDecimal(linePs, 13, parseNullableMoneyValue(orderLineModel.getValueAt(i, 9)));
                    linePs.setString(14, blankToNull(orderLineModel.getValueAt(i, 10)));
                    setNullableBigDecimal(linePs, 15, parseNullableMoneyValue(orderLineModel.getValueAt(i, 11)));
                    linePs.setString(16, blankToNull(orderLineModel.getValueAt(i, 12)));
                    setNullableBigDecimal(linePs, 17, parseNullableMoneyValue(orderLineModel.getValueAt(i, 13)));
                    linePs.addBatch();
                    if (itemIdValue != null) {
                        long itemId = Long.parseLong(itemIdValue.toString());
                        if (variantIdValue == null || variantIdValue.toString().isBlank()) {
                            itemSoldPs.setLong(1, itemId);
                            itemSoldPs.addBatch();
                        } else {
                            variantSoldPs.setLong(1, Long.parseLong(variantIdValue.toString()));
                            variantSoldPs.addBatch();
                            refreshParentPs.setLong(1, itemId);
                            refreshParentPs.addBatch();
                        }
                    }
                }
                linePs.executeBatch();
                itemSoldPs.executeBatch();
                variantSoldPs.executeBatch();
                refreshParentPs.executeBatch();
                conn.commit();
                JOptionPane.showMessageDialog(this, "Custom order " + orderNumber + " saved.");
                clearOrderEntry();
                loadOrders();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private void saveAssignment() {
        if (selectedOrderId == null) {
            JOptionPane.showMessageDialog(this, "Select an order first.");
            return;
        }
        EmployeeOption employee = (EmployeeOption) assignEmployeeBox.getSelectedItem();
        String status = manageStatusBox.getSelectedItem() == null ? "NEW" : manageStatusBox.getSelectedItem().toString();
        boolean assigned = employee != null && employee.userId() != null;
        if (assigned && "NEW".equals(status)) {
            status = "ASSIGNED";
        }

        String sql = """
                UPDATE custom_orders
                SET assigned_to_user_id = ?, assigned_to_name = ?,
                    assigned_by_user_id = ?, assigned_by_name = ?,
                    assigned_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE assigned_at END,
                    status = ?,
                    completed_at = CASE WHEN ? = 'COMPLETED' THEN CURRENT_TIMESTAMP ELSE completed_at END,
                    delivered_at = CASE WHEN ? = 'DELIVERED' THEN CURRENT_TIMESTAMP ELSE delivered_at END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (assigned) {
                ps.setInt(1, employee.userId());
                ps.setString(2, employee.name());
            } else {
                ps.setNull(1, Types.INTEGER);
                ps.setNull(2, Types.VARCHAR);
            }
            setNullableInteger(ps, 3, SessionManager.getCurrentUserId());
            ps.setString(4, SessionManager.getCurrentUserDisplayName());
            ps.setBoolean(5, assigned);
            ps.setString(6, status);
            ps.setString(7, status);
            ps.setString(8, status);
            ps.setLong(9, selectedOrderId);
            ps.executeUpdate();
            loadOrders();
            loadMyOrders();
            JOptionPane.showMessageDialog(this, "Order updated.");
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private void openOrderLookupDialog() {
        JDialog dialog = new JDialog(this, "Order Lookup", true);
        dialog.setSize(980, 620);
        dialog.setLocationRelativeTo(this);
        dialog.add(buildOrderLookupPanel(), BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private JPanel buildOrderLookupPanel() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JTextField searchField = new JTextField();
        JButton searchButton = new JButton("Search");
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"ID", "Order #", "Status", "Customer", "Phone", "Total", "Paid", "Balance", "Payment", "Reference", "Created"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setMaxWidth(70);

        JTextArea detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);

        JPanel center = new JPanel(new GridLayout(1, 2, 10, 0));
        center.add(new JScrollPane(table));
        center.add(new JScrollPane(detailsArea));

        JComboBox<String> methodBox = new JComboBox<>(new String[]{"Cash", "Card", "Cheque", "Account"});
        JTextField amountField = new JTextField(8);
        JTextField referenceField = new JTextField(14);
        JButton payButton = new JButton("Apply Payment");
        JButton deliveredButton = new JButton("Mark Delivered");
        JButton closeButton = new JButton("Close");
        styleDialogButton(searchButton);
        styleDialogButton(payButton);
        styleDialogButton(deliveredButton);
        styleDialogButton(closeButton);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.add(new JLabel("Amount:"));
        actionPanel.add(amountField);
        actionPanel.add(new JLabel("Method:"));
        actionPanel.add(methodBox);
        actionPanel.add(new JLabel("Reference:"));
        actionPanel.add(referenceField);
        actionPanel.add(payButton);
        actionPanel.add(deliveredButton);
        actionPanel.add(closeButton);

        Runnable loadLookupOrders = () -> loadLookupOrders(model, searchField.getText().trim());
        searchButton.addActionListener(e -> loadLookupOrders.run());
        searchField.addActionListener(e -> loadLookupOrders.run());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Long orderId = selectedLookupOrderId(table, model);
                if (orderId != null) {
                    loadOrderDetails(orderId, detailsArea);
                    BigDecimal balance = parseNullableMoneyValue(model.getValueAt(table.convertRowIndexToModel(table.getSelectedRow()), 7));
                    amountField.setText(balance == null ? "" : balance.toPlainString());
                }
            }
        });
        methodBox.addActionListener(e -> {
            boolean needsReference = !"Cash".equals(methodBox.getSelectedItem());
            referenceField.setEnabled(needsReference);
            if (!needsReference) {
                referenceField.setText("");
            }
        });
        payButton.addActionListener(e -> {
            Long orderId = selectedLookupOrderId(table, model);
            if (orderId == null) {
                JOptionPane.showMessageDialog(root, "Select an order first.");
                return;
            }
            if (applyLookupPayment(orderId, amountField.getText().trim(), methodBox.getSelectedItem().toString(), referenceField.getText().trim(), root)) {
                loadLookupOrders.run();
                loadOrders();
                loadMyOrders();
                loadOrderDetails(orderId, detailsArea);
            }
        });
        deliveredButton.addActionListener(e -> {
            Long orderId = selectedLookupOrderId(table, model);
            if (orderId == null) {
                JOptionPane.showMessageDialog(root, "Select an order first.");
                return;
            }
            if (markLookupOrderDelivered(orderId, root)) {
                loadLookupOrders.run();
                loadOrders();
                loadMyOrders();
                loadOrderDetails(orderId, detailsArea);
            }
        });
        closeButton.addActionListener(e -> {
            Window window = SwingUtilities.getWindowAncestor(root);
            if (window != null) {
                window.dispose();
            }
        });
        methodBox.setSelectedItem("Cash");
        referenceField.setEnabled(false);

        root.add(searchPanel, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(actionPanel, BorderLayout.SOUTH);
        loadLookupOrders.run();
        return root;
    }

    private void loadLookupOrders(DefaultTableModel model, String search) {
        model.setRowCount(0);
        String sql = """
                SELECT custom_order_id, order_number, status, customer_name, customer_phone,
                       total_amount, amount_paid, balance_due, payment_method, payment_reference,
                       payment_status, created_at
                FROM custom_orders
                WHERE (? = ''
                   OR LOWER(order_number) LIKE LOWER(?)
                   OR LOWER(customer_name) LIKE LOWER(?)
                   OR COALESCE(customer_phone, '') LIKE ?)
                ORDER BY created_at DESC
                LIMIT 100
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String pattern = "%" + search + "%";
            ps.setString(1, search);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                            rs.getLong("custom_order_id"),
                            rs.getString("order_number"),
                            rs.getString("status"),
                            rs.getString("customer_name"),
                            rs.getString("customer_phone"),
                            formatMoney(rs.getBigDecimal("total_amount")),
                            formatMoney(rs.getBigDecimal("amount_paid")),
                            formatMoney(rs.getBigDecimal("balance_due")),
                            formatPayment(rs.getString("payment_method"), rs.getString("payment_status")),
                            rs.getString("payment_reference"),
                            rs.getTimestamp("created_at")
                    });
                }
            }
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private Long selectedLookupOrderId(JTable table, DefaultTableModel model) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        return Long.parseLong(model.getValueAt(table.convertRowIndexToModel(row), 0).toString());
    }

    private boolean applyLookupPayment(Long orderId, String amountText, String methodLabel, String reference, Component parent) {
        BigDecimal amount = parseMoney(amountText, "Payment amount");
        if (amount == null) {
            return false;
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            JOptionPane.showMessageDialog(parent, "Payment amount must be greater than zero.");
            return false;
        }
        String method = methodLabel.toUpperCase().replace(" ", "_");
        if (("CARD".equals(method) || "CHEQUE".equals(method)) && reference.isBlank()) {
            JOptionPane.showMessageDialog(parent, "Enter a payment reference for card or cheque payments.");
            return false;
        }
        String lockSql = "SELECT COALESCE(balance_due, total_amount) AS balance_due FROM custom_orders WHERE custom_order_id = ? FOR UPDATE";
        String updateSql = """
                UPDATE custom_orders
                SET amount_paid = COALESCE(amount_paid, 0) + ?,
                    balance_due = GREATEST(COALESCE(balance_due, total_amount) - ?, 0),
                    payment_method = ?,
                    payment_reference = ?,
                    payment_status = CASE WHEN GREATEST(COALESCE(balance_due, total_amount) - ?, 0) <= 0 THEN 'PAID' ELSE 'PARTIAL' END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                """;
        String paymentSql = """
                INSERT INTO custom_order_payments (
                    custom_order_id, payment_amount, payment_method, payment_reference,
                    taken_by_user_id, taken_by_name
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement lockPs = conn.prepareStatement(lockSql);
                 PreparedStatement updatePs = conn.prepareStatement(updateSql);
                 PreparedStatement paymentPs = conn.prepareStatement(paymentSql)) {
                lockPs.setLong(1, orderId);
                BigDecimal balanceDue;
                try (ResultSet rs = lockPs.executeQuery()) {
                    if (!rs.next()) {
                        JOptionPane.showMessageDialog(parent, "Order was not found.");
                        conn.rollback();
                        return false;
                    }
                    balanceDue = rs.getBigDecimal("balance_due");
                }
                if (amount.compareTo(balanceDue) > 0) {
                    JOptionPane.showMessageDialog(parent, "Payment cannot be more than the balance due.");
                    conn.rollback();
                    return false;
                }
                updatePs.setBigDecimal(1, amount);
                updatePs.setBigDecimal(2, amount);
                updatePs.setString(3, method);
                updatePs.setString(4, reference.isBlank() ? null : reference);
                updatePs.setBigDecimal(5, amount);
                updatePs.setLong(6, orderId);
                updatePs.executeUpdate();

                paymentPs.setLong(1, orderId);
                paymentPs.setBigDecimal(2, amount);
                paymentPs.setString(3, method);
                paymentPs.setString(4, reference.isBlank() ? null : reference);
                setNullableInteger(paymentPs, 5, SessionManager.getCurrentUserId());
                paymentPs.setString(6, SessionManager.getCurrentUserDisplayName());
                paymentPs.executeUpdate();
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
            JOptionPane.showMessageDialog(parent, "Payment applied.");
            return true;
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
            return false;
        }
    }

    private boolean markLookupOrderDelivered(Long orderId, Component parent) {
        String sql = """
                UPDATE custom_orders
                SET status = 'DELIVERED',
                    delivered_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE custom_order_id = ?
                  AND COALESCE(balance_due, 0) <= 0
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            int updated = ps.executeUpdate();
            if (updated == 0) {
                JOptionPane.showMessageDialog(parent, "This order still has a balance due. Complete payment before marking it delivered.");
                return false;
            }
            JOptionPane.showMessageDialog(parent, "Order marked delivered.");
            return true;
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
            return false;
        }
    }

    private void addOrderLine() {
        CustomItemOption item = (CustomItemOption) orderItemBox.getSelectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "Select a custom item.");
            return;
        }
        VariantOption variant = (VariantOption) variantBox.getSelectedItem();
        if (item.hasVariants() && (variant == null || variant.variantId() == null)) {
            JOptionPane.showMessageDialog(this, "Select a size or variant for this item.");
            return;
        }
        int quantity = parseLineQuantity();
        if (quantity <= 0) {
            return;
        }
        AreaCalculation areaCalculation = null;
        BigDecimal configuredPrice = configuredLinePrice(item, variant);
        BigDecimal price;
        if ("AREA".equals(item.pricingType())) {
            areaCalculation = calculateAreaPrice(item, configuredPrice, true);
            if (areaCalculation == null) {
                return;
            }
            price = areaCalculation.totalPrice();
            linePriceField.setText(formatMoney(price));
        } else {
            price = parseMoney(linePriceField.getText().trim(), "Price");
            if (price == null) {
                return;
            }
        }
        if ("FIXED".equals(item.pricingType()) && configuredPrice != null && price.compareTo(configuredPrice) != 0) {
            JOptionPane.showMessageDialog(this, "This selection has a fixed price of " + formatMoney(configuredPrice) + ".");
            linePriceField.setText(formatMoney(configuredPrice));
            return;
        }
        String details = customizationArea.getText().trim();
        if (areaCalculation != null) {
            String areaDetails = "Size: " + stripTrailingZeros(areaCalculation.width()) + " x " + stripTrailingZeros(areaCalculation.length()) + " " + displayDimensionUnit(areaCalculation.dimensionUnit())
                    + "\nArea: " + stripTrailingZeros(areaCalculation.area()) + " " + displayAreaUnit(areaCalculation.areaUnit())
                    + "\nRate: " + formatMoney(areaCalculation.areaPrice()) + " / " + displayAreaUnit(areaCalculation.areaUnit());
            details = details.isBlank() ? areaDetails : areaDetails + "\n" + details;
        }
        if (details.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Customization details are required for each item.");
            return;
        }
        String notes = lineNotesArea.getText().trim();
        if (selectedOrderLineModelRow >= 0) {
            Object[] updatedRow = buildOrderLineRow(item, variant, item.pricingType(), price, details, notes, areaCalculation);
            for (int column = 0; column < updatedRow.length; column++) {
                orderLineModel.setValueAt(updatedRow[column], selectedOrderLineModelRow, column);
            }
            orderLineTable.clearSelection();
        } else {
            for (int i = 0; i < quantity; i++) {
                String lineDetails = quantity == 1 ? details : details + "\nCopy " + (i + 1) + " of " + quantity;
                orderLineModel.addRow(buildOrderLineRow(item, variant, item.pricingType(), price, lineDetails, notes, areaCalculation));
            }
        }
        clearLineEditor();
        updateAreaCalculationPreview();
        updateOrderTotal();
    }

    private Object[] buildOrderLineRow(CustomItemOption item, VariantOption variant, String pricingType, BigDecimal price, String details, String notes, AreaCalculation areaCalculation) {
        return new Object[]{
                item.customItemId(),
                variant == null ? null : variant.variantId(),
                item.name(),
                variant == null || variant.variantId() == null ? "" : variant.name(),
                pricingType,
                formatMoney(price),
                details,
                notes,
                areaCalculation == null ? "" : areaCalculation.width(),
                areaCalculation == null ? "" : areaCalculation.length(),
                    areaCalculation == null ? "" : areaCalculation.dimensionUnit(),
                areaCalculation == null ? "" : areaCalculation.area(),
                areaCalculation == null ? "" : areaCalculation.areaUnit(),
                areaCalculation == null ? "" : areaCalculation.areaPrice()
        };
    }

    private void clearLineEditor() {
        selectedOrderLineModelRow = -1;
        if (addLineButton != null) {
            addLineButton.setText("Add Line");
        }
        customizationArea.setText("");
        lineNotesArea.setText("");
        lineQuantityField.setText("1");
        lineQuantityField.setEnabled(true);
        widthField.setText("");
        lengthField.setText("");
        CustomItemOption item = orderItemBox == null ? null : (CustomItemOption) orderItemBox.getSelectedItem();
        if (item == null || (!"FIXED".equals(item.pricingType()) && !"AREA".equals(item.pricingType()))) {
            linePriceField.setText("");
        }
    }

    private int parseLineQuantity() {
        String quantityText = lineQuantityField == null ? "1" : lineQuantityField.getText().trim();
        if (quantityText.isEmpty()) {
            quantityText = "1";
        }
        try {
            int quantity = Integer.parseInt(quantityText);
            if (quantity <= 0) {
                JOptionPane.showMessageDialog(this, "Quantity must be at least 1.");
                return -1;
            }
            if (quantity > 100) {
                JOptionPane.showMessageDialog(this, "Quantity cannot be more than 100 lines at once.");
                return -1;
            }
            return quantity;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Quantity must be a whole number.");
            return -1;
        }
    }

    private void removeSelectedOrderLine() {
        int selectedRow = orderLineTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        orderLineModel.removeRow(orderLineTable.convertRowIndexToModel(selectedRow));
        clearLineEditor();
        updateOrderTotal();
    }

    private void loadSelectedCartLineIntoEditor() {
        int viewRow = orderLineTable.getSelectedRow();
        if (viewRow < 0) {
            return;
        }
        selectedOrderLineModelRow = orderLineTable.convertRowIndexToModel(viewRow);
        selectOrderItemById(parseLongValue(orderLineModel.getValueAt(selectedOrderLineModelRow, 0)));
        selectVariantById(parseLongValue(orderLineModel.getValueAt(selectedOrderLineModelRow, 1)));
        linePriceField.setText(valueAt(orderLineModel, selectedOrderLineModelRow, 5));
        lineQuantityField.setText("1");
        lineQuantityField.setEnabled(false);
        customizationArea.setText(stripGeneratedAreaDetails(
                valueAt(orderLineModel, selectedOrderLineModelRow, 4),
                valueAt(orderLineModel, selectedOrderLineModelRow, 6)
        ));
        lineNotesArea.setText(valueAt(orderLineModel, selectedOrderLineModelRow, 7));
        widthField.setText(valueAt(orderLineModel, selectedOrderLineModelRow, 8));
        lengthField.setText(valueAt(orderLineModel, selectedOrderLineModelRow, 9));
        if (addLineButton != null) {
            addLineButton.setText("Update Item");
        }
        updateAreaCalculationPreview();
    }

    private void selectOrderItemById(Long itemId) {
        if (itemId == null || orderItemBox == null) {
            return;
        }
        for (int i = 0; i < orderItemBox.getItemCount(); i++) {
            CustomItemOption option = orderItemBox.getItemAt(i);
            if (itemId.equals(option.customItemId())) {
                orderItemBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private void selectVariantById(Long variantId) {
        if (variantBox == null) {
            return;
        }
        for (int i = 0; i < variantBox.getItemCount(); i++) {
            VariantOption option = variantBox.getItemAt(i);
            if ((variantId == null && option.variantId() == null) || (variantId != null && variantId.equals(option.variantId()))) {
                variantBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private Long parseLongValue(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return Long.parseLong(value.toString());
    }

    private String stripGeneratedAreaDetails(String pricingType, String details) {
        if (!"AREA".equals(pricingType) || details == null) {
            return details == null ? "" : details;
        }
        String[] lines = details.split("\\R", -1);
        if (lines.length >= 3 && lines[0].startsWith("Size:") && lines[1].startsWith("Area:") && lines[2].startsWith("Rate:")) {
            StringBuilder remaining = new StringBuilder();
            for (int i = 3; i < lines.length; i++) {
                if (remaining.length() > 0) {
                    remaining.append("\n");
                }
                remaining.append(lines[i]);
            }
            return remaining.toString().trim();
        }
        return details;
    }

    private void loadSelectedOrder() {
        int row = ordersTable.getSelectedRow();
        if (row < 0) {
            selectedOrderId = null;
            selectedOrderDetailsArea.setText("");
            return;
        }
        int modelRow = ordersTable.convertRowIndexToModel(row);
        selectedOrderId = Long.parseLong(ordersModel.getValueAt(modelRow, 0).toString());
        manageStatusBox.setSelectedItem(ordersModel.getValueAt(modelRow, 2).toString());
        selectEmployeeByName(valueAt(ordersModel, modelRow, 11));
        loadOrderDetails(selectedOrderId, selectedOrderDetailsArea);
    }

    private void loadSelectedMyOrder() {
        int row = myOrdersTable.getSelectedRow();
        if (row < 0) {
            myOrderDetailsArea.setText("");
            return;
        }
        int modelRow = myOrdersTable.convertRowIndexToModel(row);
        long orderId = Long.parseLong(myOrdersModel.getValueAt(modelRow, 0).toString());
        loadOrderDetails(orderId, myOrderDetailsArea);
    }

    private void loadOrderDetails(Long orderId, JTextArea detailsArea) {
        String sql = """
                SELECT co.order_number, co.customer_name, co.customer_phone, co.status, co.due_date,
                       co.order_notes, co.total_amount, co.amount_paid, co.balance_due,
                       co.payment_method, co.payment_reference, co.payment_status, co.assigned_to_name,
                       col.item_name, col.variant_name, col.unit_price, col.customization_details, col.line_notes
                FROM custom_orders co
                LEFT JOIN custom_order_lines col ON col.custom_order_id = co.custom_order_id
                WHERE co.custom_order_id = ?
                ORDER BY col.sort_order
                """;
        StringBuilder details = new StringBuilder();
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean headerWritten = false;
                int lineNumber = 1;
                while (rs.next()) {
                    if (!headerWritten) {
                        details.append(rs.getString("order_number")).append("\n");
                        details.append(rs.getString("customer_name")).append(" - ").append(rs.getString("customer_phone")).append("\n");
                        details.append("Status: ").append(rs.getString("status")).append("\n");
                        details.append("Due: ").append(rs.getDate("due_date") == null ? "" : rs.getDate("due_date")).append("\n");
                        details.append("Assigned: ").append(rs.getString("assigned_to_name") == null ? "Unassigned" : rs.getString("assigned_to_name")).append("\n");
                        details.append("Payment: ").append(formatPayment(rs.getString("payment_method"), rs.getString("payment_status"))).append("\n");
                        String paymentReference = rs.getString("payment_reference");
                        if (paymentReference != null && !paymentReference.isBlank()) {
                            details.append("Payment Reference: ").append(paymentReference).append("\n");
                        }
                        details.append("Total: ").append(formatMoney(rs.getBigDecimal("total_amount"))).append("\n");
                        details.append("Paid: ").append(formatMoney(rs.getBigDecimal("amount_paid"))).append("\n");
                        details.append("Balance Due: ").append(formatMoney(rs.getBigDecimal("balance_due"))).append("\n");
                        String notes = rs.getString("order_notes");
                        if (notes != null && !notes.isBlank()) {
                            details.append("Notes: ").append(notes).append("\n");
                        }
                        details.append("\nItems\n");
                        headerWritten = true;
                    }
                    String itemName = rs.getString("item_name");
                    if (itemName != null) {
                        String variantName = rs.getString("variant_name");
                        details.append(lineNumber++).append(". ").append(itemName)
                                .append(variantName == null || variantName.isBlank() ? "" : " / " + variantName)
                                .append(" - ").append(formatMoney(rs.getBigDecimal("unit_price"))).append("\n")
                                .append(rs.getString("customization_details")).append("\n");
                        String lineNotes = rs.getString("line_notes");
                        if (lineNotes != null && !lineNotes.isBlank()) {
                            details.append("Notes: ").append(lineNotes).append("\n");
                        }
                        details.append("\n");
                    }
                }
            }
            detailsArea.setText(details.toString());
            appendPaymentHistory(orderId, detailsArea);
            detailsArea.setCaretPosition(0);
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private void appendPaymentHistory(Long orderId, JTextArea detailsArea) {
        String sql = """
                SELECT payment_amount, payment_method, payment_reference, taken_by_name, created_at
                FROM custom_order_payments
                WHERE custom_order_id = ?
                ORDER BY created_at
                """;
        StringBuilder payments = new StringBuilder(detailsArea.getText());
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                boolean headerWritten = false;
                while (rs.next()) {
                    if (!headerWritten) {
                        payments.append("\nPayments\n");
                        headerWritten = true;
                    }
                    payments.append(formatMoney(rs.getBigDecimal("payment_amount")))
                            .append(" - ")
                            .append(formatPayment(rs.getString("payment_method"), null));
                    String reference = rs.getString("payment_reference");
                    if (reference != null && !reference.isBlank()) {
                        payments.append(" Ref: ").append(reference);
                    }
                    String takenBy = rs.getString("taken_by_name");
                    if (takenBy != null && !takenBy.isBlank()) {
                        payments.append(" By: ").append(takenBy);
                    }
                    payments.append(" At: ").append(rs.getTimestamp("created_at")).append("\n");
                }
            }
            detailsArea.setText(payments.toString());
        } catch (SQLException ex) {
            if (!"42P01".equals(ex.getSQLState())) {
                showDatabaseSetupMessage(ex);
            }
        }
    }

    private void applySelectedOrderItemPrice() {
        if (orderItemBox == null || linePriceField == null) {
            return;
        }
        CustomItemOption item = (CustomItemOption) orderItemBox.getSelectedItem();
        if (item == null) {
            linePriceField.setText("");
            linePriceField.setEditable(true);
            setAreaLineVisible(false);
            return;
        }
        boolean fixed = "FIXED".equals(item.pricingType());
        boolean area = "AREA".equals(item.pricingType());
        VariantOption variant = variantBox == null ? null : (VariantOption) variantBox.getSelectedItem();
        BigDecimal configuredPrice = configuredLinePrice(item, variant);
        linePriceField.setEditable(!fixed && !area);
        widthField.setEnabled(area);
        lengthField.setEnabled(area);
        setAreaLineVisible(area);
        if (fixed && configuredPrice != null) {
            linePriceField.setText(formatMoney(configuredPrice));
        } else if (!area) {
            linePriceField.setText("");
        }
        updateAreaCalculationPreview();
    }

    private BigDecimal configuredLinePrice(CustomItemOption item, VariantOption variant) {
        if (item == null) {
            return null;
        }
        if (item.hasVariants() && variant != null && variant.variantId() != null) {
            return variant.fixedPrice();
        }
        if ("AREA".equals(item.pricingType())) {
            return item.areaPrice();
        }
        return item.fixedPrice();
    }

    private void setAreaLineVisible(boolean visible) {
        for (JComponent component : areaLineComponents) {
            component.setVisible(visible);
        }
        revalidate();
        repaint();
    }

    private void updateAreaCalculationPreview() {
        if (areaCalculationLabel == null || linePriceField == null) {
            return;
        }
        CustomItemOption item = orderItemBox == null ? null : (CustomItemOption) orderItemBox.getSelectedItem();
        if (item == null || !"AREA".equals(item.pricingType())) {
            areaCalculationLabel.setText(" ");
            return;
        }
        VariantOption variant = variantBox == null ? null : (VariantOption) variantBox.getSelectedItem();
        BigDecimal configuredPrice = configuredLinePrice(item, variant);
        AreaCalculation calculation = calculateAreaPrice(item, configuredPrice, false);
        if (calculation == null) {
            areaCalculationLabel.setText("Rate: " + formatMoney(configuredPrice) + " / " + displayAreaUnit(item.areaPriceUnit()));
            return;
        }
        linePriceField.setText(formatMoney(calculation.totalPrice()));
        areaCalculationLabel.setText(stripTrailingZeros(calculation.area()) + " " + displayAreaUnit(calculation.areaUnit())
                + " x " + formatMoney(calculation.areaPrice()) + " = " + formatMoney(calculation.totalPrice()));
    }

    private AreaCalculation calculateAreaPrice(CustomItemOption item, BigDecimal areaPrice, boolean showErrors) {
        if (areaPrice == null) {
            if (showErrors) {
                JOptionPane.showMessageDialog(this, "Area price is not configured for this selection.");
            }
            return null;
        }
        BigDecimal width = parsePositiveDimension(widthField.getText().trim(), "Width", showErrors);
        BigDecimal length = parsePositiveDimension(lengthField.getText().trim(), "Length", showErrors);
        if (width == null || length == null) {
            return null;
        }
        if (item.maxWidth() != null && width.compareTo(item.maxWidth()) > 0) {
            if (showErrors) {
                JOptionPane.showMessageDialog(this, "Width exceeds the max of " + stripTrailingZeros(item.maxWidth()) + " " + displayDimensionUnit(item.dimensionUnit()) + ".");
            }
            return null;
        }
        if (item.maxLength() != null && length.compareTo(item.maxLength()) > 0) {
            if (showErrors) {
                JOptionPane.showMessageDialog(this, "Length exceeds the max of " + stripTrailingZeros(item.maxLength()) + " " + displayDimensionUnit(item.dimensionUnit()) + ".");
            }
            return null;
        }
        BigDecimal areaInSquareMeters = toMeters(width, item.dimensionUnit()).multiply(toMeters(length, item.dimensionUnit()));
        BigDecimal area = fromSquareMeters(areaInSquareMeters, item.areaPriceUnit());
        BigDecimal total = area.multiply(areaPrice).setScale(2, java.math.RoundingMode.HALF_UP);
        return new AreaCalculation(width, length, item.dimensionUnit(), area, item.areaPriceUnit(), areaPrice, total);
    }

    private BigDecimal parsePositiveDimension(String value, String fieldName, boolean showErrors) {
        try {
            BigDecimal amount = new BigDecimal(value.replace(",", "").trim());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException();
            }
            return amount;
        } catch (Exception ex) {
            if (showErrors) {
                JOptionPane.showMessageDialog(this, fieldName + " must be greater than zero.");
            }
            return null;
        }
    }

    private BigDecimal toMeters(BigDecimal value, String unit) {
        return switch (unit == null ? "IN" : unit) {
            case "FT" -> value.multiply(new BigDecimal("0.3048"));
            case "YD" -> value.multiply(new BigDecimal("0.9144"));
            case "M" -> value;
            case "CM" -> value.multiply(new BigDecimal("0.01"));
            default -> value.multiply(new BigDecimal("0.0254"));
        };
    }

    private BigDecimal fromSquareMeters(BigDecimal value, String areaUnit) {
        return switch (areaUnit == null ? "SQ_FT" : areaUnit) {
            case "SQ_IN" -> value.multiply(new BigDecimal("1550.0031000062"));
            case "SQ_YD" -> value.multiply(new BigDecimal("1.1959900463"));
            case "SQ_M" -> value;
            case "SQ_CM" -> value.multiply(new BigDecimal("10000"));
            default -> value.multiply(new BigDecimal("10.7639104167"));
        };
    }

    private void applyOrderFilter() {
        if (ordersSorter == null) {
            return;
        }
        List<RowFilter<Object, Object>> filters = new ArrayList<>();
        String search = orderSearchField == null ? "" : orderSearchField.getText().trim();
        if (!search.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(search)));
        }
        Object status = statusFilterBox == null ? "All" : statusFilterBox.getSelectedItem();
        if (status != null && !"All".equals(status.toString())) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(status.toString()) + "$", 2));
        }
        ordersSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    private void applyMyOrderFilter() {
        if (myOrdersSorter == null) {
            return;
        }
        List<RowFilter<Object, Object>> filters = new ArrayList<>();
        String search = myOrderSearchField == null ? "" : myOrderSearchField.getText().trim();
        if (!search.isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(search)));
        }
        Object status = myStatusFilterBox == null ? "All" : myStatusFilterBox.getSelectedItem();
        if (status != null && !"All".equals(status.toString())) {
            filters.add(RowFilter.regexFilter("^" + Pattern.quote(status.toString()) + "$", 2));
        }
        myOrdersSorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
    }

    private void clearOrderEntry() {
        if (orderLineModel != null) {
            orderLineModel.setRowCount(0);
        }
        selectedOrderLineModelRow = -1;
        if (addLineButton != null) {
            addLineButton.setText("Add Line");
        }
        dueDateField.setText("");
        orderNotesArea.setText("");
        customizationArea.setText("");
        lineNotesArea.setText("");
        lineQuantityField.setText("1");
        widthField.setText("");
        lengthField.setText("");
        upfrontPaymentField.setText("0.00");
        paymentReferenceField.setText("");
        selectedPaymentMethod = null;
        if (paymentMethodGroup != null) {
            paymentMethodGroup.clearSelection();
        }
        updatePaymentButtonStyles();
        updatePaymentReferenceState();
        applySelectedOrderItemPrice();
        updateOrderTotal();
    }

    private void updateOrderTotal() {
        orderTotalLabel.setText("Total: " + formatMoney(calculateOrderTotal()));
        updatePaymentPreview();
    }

    private void updatePaymentPreview() {
        if (balanceDueLabel == null) {
            return;
        }
        BigDecimal total = calculateOrderTotal();
        BigDecimal paid = BigDecimal.ZERO;
        try {
            String paidText = upfrontPaymentField == null ? "" : upfrontPaymentField.getText().trim();
            if (!paidText.isEmpty()) {
                paid = parseMoneyValue(paidText);
            }
        } catch (Exception ignored) {
            balanceDueLabel.setText("Balance Due: --");
            return;
        }
        balanceDueLabel.setText("Balance Due: " + formatMoney(total.subtract(paid)));
    }

    private BigDecimal calculateOrderTotal() {
        BigDecimal total = BigDecimal.ZERO;
        if (orderLineModel == null) {
            return total;
        }
        for (int i = 0; i < orderLineModel.getRowCount(); i++) {
            total = total.add(parseMoneyValue(orderLineModel.getValueAt(i, 5).toString()));
        }
        return total;
    }

    private String generateOrderNumber() {
        return "CO-" + System.currentTimeMillis();
    }

    private void selectEmployeeByName(String name) {
        if (assignEmployeeBox == null) {
            return;
        }
        for (int i = 0; i < assignEmployeeBox.getItemCount(); i++) {
            EmployeeOption option = assignEmployeeBox.getItemAt(i);
            if ((name == null || name.isBlank()) && option.userId() == null) {
                assignEmployeeBox.setSelectedIndex(i);
                return;
            }
            if (name != null && name.equals(option.name())) {
                assignEmployeeBox.setSelectedIndex(i);
                return;
            }
        }
    }

    private GridBagConstraints formGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private JLabel addField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        JLabel labelComponent = new JLabel(label);
        panel.add(labelComponent, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
        return labelComponent;
    }

    private void addTrackedField(List<JComponent> trackedComponents, JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        trackedComponents.add(addField(panel, gbc, row, label, field));
        trackedComponents.add(field);
    }

    private javax.swing.event.DocumentListener simpleDocumentListener(Runnable callback) {
        return new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                callback.run();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                callback.run();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                callback.run();
            }
        };
    }

    private BigDecimal parseMoney(String value, String fieldName) {
        try {
            BigDecimal amount = parseMoneyValue(value);
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, fieldName + " cannot be negative.");
                return null;
            }
            return amount;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, fieldName + " must be a valid amount.");
            return null;
        }
    }

    private BigDecimal parseMoneyValue(String value) {
        return new BigDecimal(value.replace("$", "").replace(",", "").trim());
    }

    private BigDecimal parseNullableMoneyValue(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return parseMoneyValue(value.toString());
    }

    private void setNullableBigDecimal(PreparedStatement ps, int index, BigDecimal value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.NUMERIC);
        } else {
            ps.setBigDecimal(index, value);
        }
    }

    private String blankToNull(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return "$" + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String formatPayment(String method, String status) {
        if (method == null || method.isBlank()) {
            return status == null ? "" : status;
        }
        String normalizedMethod = method.substring(0, 1).toUpperCase() + method.substring(1).toLowerCase();
        if (status == null || status.isBlank()) {
            return normalizedMethod;
        }
        return normalizedMethod + " / " + status;
    }

    private String stripTrailingZeros(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String displayDimensionUnit(String unit) {
        return switch (unit == null ? "IN" : unit) {
            case "FT" -> "ft";
            case "YD" -> "yd";
            case "M" -> "m";
            case "CM" -> "cm";
            default -> "in";
        };
    }

    private String displayAreaUnit(String areaUnit) {
        return switch (areaUnit == null ? "SQ_FT" : areaUnit) {
            case "SQ_IN" -> "sq in";
            case "SQ_YD" -> "sq yd";
            case "SQ_M" -> "sq m";
            case "SQ_CM" -> "sq cm";
            default -> "sq ft";
        };
    }

    private String valueAt(DefaultTableModel model, int row, int column) {
        Object value = model.getValueAt(row, column);
        return value == null ? "" : value.toString();
    }

    private void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private void showDatabaseSetupMessage(SQLException ex) {
        JOptionPane.showMessageDialog(
                this,
                "Custom orders are not ready yet. Run database/custom_orders_setup.sql, then reopen this screen.\n\n" + ex.getMessage(),
                "Database Setup Needed",
                JOptionPane.ERROR_MESSAGE
        );
    }

    private record AreaCalculation(BigDecimal width, BigDecimal length, String dimensionUnit, BigDecimal area, String areaUnit, BigDecimal areaPrice, BigDecimal totalPrice) {
    }

    private record CustomItemOption(Long customItemId, String name, String productType, String pricingType, BigDecimal fixedPrice, boolean hasVariants, BigDecimal areaPrice, String areaPriceUnit, String dimensionUnit, BigDecimal maxWidth, BigDecimal maxLength) {
        @Override
        public String toString() {
            if (hasVariants) {
                return name + " (variants)";
            }
            if ("FIXED".equals(pricingType)) {
                return name + " ($" + fixedPrice + ")";
            }
            if ("AREA".equals(pricingType)) {
                return name + " (area)";
            }
            return name + " (variable)";
        }
    }

    private record VariantOption(Long variantId, String name, BigDecimal fixedPrice) {
        @Override
        public String toString() {
            return fixedPrice == null ? name : name + " ($" + fixedPrice + ")";
        }
    }

    private record CustomerOption(int customerId, String name, String phone) {
        @Override
        public String toString() {
            return name + (phone == null || phone.isBlank() ? " (no phone)" : " - " + phone);
        }
    }

    private record EmployeeOption(Integer userId, String name) {
        @Override
        public String toString() {
            return name;
        }
    }
}
