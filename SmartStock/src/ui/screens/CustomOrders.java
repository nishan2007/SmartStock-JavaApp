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
    private JTextField linePriceField;
    private JTextArea customizationArea;
    private JTextArea lineNotesArea;
    private DefaultTableModel orderLineModel;
    private JTable orderLineTable;
    private JLabel orderTotalLabel;

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
        setTitle("Custom Orders");
        setSize(1180, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setJMenuBar(AppMenuBar.create(this, "CustomOrders"));

        JTabbedPane tabs = new JTabbedPane();
        if (canCreateOrders) {
            tabs.addTab("New Order", buildOrderEntryPanel());
        }
        if (canViewAssignedOrders || canManageOrders) {
            tabs.addTab("My Orders", buildMyOrdersPanel());
        }
        if (canManageOrders) {
            tabs.addTab("Manage Orders", buildManageOrdersPanel());
        }
        if (tabs.getTabCount() == 0) {
            add(new JLabel("You do not have permission to access custom orders.", SwingConstants.CENTER), BorderLayout.CENTER);
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
        linePriceField = new JTextField();
        customizationArea = new JTextArea(4, 20);
        customizationArea.setLineWrap(true);
        customizationArea.setWrapStyleWord(true);
        lineNotesArea = new JTextArea(2, 20);
        lineNotesArea.setLineWrap(true);
        lineNotesArea.setWrapStyleWord(true);
        JButton addLineButton = new JButton("Add Line");
        JButton removeLineButton = new JButton("Remove Selected");

        addField(linePanel, lineGbc, 0, "Item:", orderItemBox);
        addField(linePanel, lineGbc, 1, "Price:", linePriceField);
        addField(linePanel, lineGbc, 2, "Customization:", new JScrollPane(customizationArea));
        addField(linePanel, lineGbc, 3, "Line Notes:", new JScrollPane(lineNotesArea));
        lineGbc.gridx = 1;
        lineGbc.gridy = 4;
        JPanel lineButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lineButtons.add(addLineButton);
        lineButtons.add(removeLineButton);
        linePanel.add(lineButtons, lineGbc);

        orderItemBox.addActionListener(e -> applySelectedOrderItemPrice());
        addLineButton.addActionListener(e -> addOrderLine());
        removeLineButton.addActionListener(e -> removeSelectedOrderLine());

        orderLineModel = new DefaultTableModel(new Object[]{"Item ID", "Item", "Pricing", "Price", "Details", "Notes"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        orderLineTable = new JTable(orderLineModel);
        orderLineTable.setRowHeight(28);
        orderLineTable.getColumnModel().getColumn(0).setMinWidth(0);
        orderLineTable.getColumnModel().getColumn(0).setMaxWidth(0);
        orderLineTable.getColumnModel().getColumn(0).setPreferredWidth(0);
        orderTotalLabel = new JLabel("Total: $0.00");
        orderTotalLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

        JPanel center = new JPanel(new GridLayout(1, 2, 12, 0));
        center.add(linePanel);
        center.add(new JScrollPane(orderLineTable));

        JButton saveOrderButton = new JButton("Save Custom Order");
        JButton clearOrderButton = new JButton("Clear Order");
        saveOrderButton.addActionListener(e -> saveCustomOrder());
        clearOrderButton.addActionListener(e -> clearOrderEntry());

        JPanel footer = new JPanel(new BorderLayout());
        JPanel footerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footerButtons.add(clearOrderButton);
        footerButtons.add(saveOrderButton);
        footer.add(orderTotalLabel, BorderLayout.WEST);
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
        statusFilterBox = new JComboBox<>(new String[]{"All", "NEW", "ASSIGNED", "IN_PROGRESS", "READY", "COMPLETED", "CANCELLED"});
        filterPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        filterPanel.add(orderSearchField, BorderLayout.CENTER);
        filterPanel.add(statusFilterBox, BorderLayout.EAST);

        ordersModel = new DefaultTableModel(new Object[]{"ID", "Order #", "Status", "Customer", "Phone", "Due", "Total", "Assigned To", "Taken By", "Created"}, 0) {
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
        manageStatusBox = new JComboBox<>(new String[]{"NEW", "ASSIGNED", "IN_PROGRESS", "READY", "COMPLETED", "CANCELLED"});
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

    private JPanel buildMyOrdersPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel filterPanel = new JPanel(new BorderLayout(8, 0));
        myOrderSearchField = new JTextField();
        myStatusFilterBox = new JComboBox<>(new String[]{"All", "ASSIGNED", "IN_PROGRESS", "READY", "COMPLETED", "CANCELLED"});
        JButton refreshButton = new JButton("Refresh");
        filterPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        filterPanel.add(myOrderSearchField, BorderLayout.CENTER);
        JPanel filterRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterRight.add(myStatusFilterBox);
        filterRight.add(refreshButton);
        filterPanel.add(filterRight, BorderLayout.EAST);

        myOrdersModel = new DefaultTableModel(new Object[]{"ID", "Order #", "Status", "Customer", "Phone", "Due", "Total", "Created"}, 0) {
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
                SELECT custom_item_id, item_name, description, pricing_type, fixed_price, is_active
                FROM custom_order_items
                ORDER BY is_active DESC, item_name
                """;
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Long id = rs.getLong("custom_item_id");
                String name = rs.getString("item_name");
                String pricingType = rs.getString("pricing_type");
                BigDecimal fixedPrice = rs.getBigDecimal("fixed_price");
                boolean active = rs.getBoolean("is_active");
                String description = rs.getString("description");
                if (active) {
                    options.add(new CustomItemOption(id, name, pricingType, fixedPrice));
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
            applySelectedOrderItemPrice();
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
                       total_amount, assigned_to_name, taken_by_name, created_at
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
                       total_amount, created_at
                FROM custom_orders
                WHERE assigned_to_user_id = ?
                ORDER BY
                    CASE status
                        WHEN 'ASSIGNED' THEN 1
                        WHEN 'IN_PROGRESS' THEN 2
                        WHEN 'READY' THEN 3
                        WHEN 'COMPLETED' THEN 4
                        WHEN 'CANCELLED' THEN 5
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
        String orderNumber = generateOrderNumber();
        String orderSql = """
                INSERT INTO custom_orders (
                    order_number, customer_id, customer_name, customer_phone, status, due_date,
                    order_notes, total_amount, taken_by_user_id, taken_by_name
                )
                VALUES (?, ?, ?, ?, 'NEW', ?, ?, ?, ?, ?)
                """;
        String lineSql = """
                INSERT INTO custom_order_lines (
                    custom_order_id, custom_item_id, item_name, pricing_type, unit_price,
                    line_total, customization_details, line_notes, sort_order
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement orderPs = conn.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
                 PreparedStatement linePs = conn.prepareStatement(lineSql)) {
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
                setNullableInteger(orderPs, 8, SessionManager.getCurrentUserId());
                orderPs.setString(9, SessionManager.getCurrentUserDisplayName());
                orderPs.executeUpdate();

                long orderId;
                try (ResultSet rs = orderPs.getGeneratedKeys()) {
                    if (!rs.next()) {
                        throw new SQLException("Failed to get custom order ID.");
                    }
                    orderId = rs.getLong(1);
                }

                for (int i = 0; i < orderLineModel.getRowCount(); i++) {
                    Object itemIdValue = orderLineModel.getValueAt(i, 0);
                    if (itemIdValue == null) {
                        linePs.setNull(2, Types.BIGINT);
                    } else {
                        linePs.setLong(2, Long.parseLong(itemIdValue.toString()));
                    }
                    BigDecimal unitPrice = parseMoneyValue(orderLineModel.getValueAt(i, 3).toString());
                    linePs.setLong(1, orderId);
                    linePs.setString(3, orderLineModel.getValueAt(i, 1).toString());
                    linePs.setString(4, orderLineModel.getValueAt(i, 2).toString());
                    linePs.setBigDecimal(5, unitPrice);
                    linePs.setBigDecimal(6, unitPrice);
                    linePs.setString(7, orderLineModel.getValueAt(i, 4).toString());
                    Object lineNotes = orderLineModel.getValueAt(i, 5);
                    linePs.setString(8, lineNotes == null || lineNotes.toString().isBlank() ? null : lineNotes.toString());
                    linePs.setInt(9, i + 1);
                    linePs.addBatch();
                }
                linePs.executeBatch();
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
            ps.setLong(8, selectedOrderId);
            ps.executeUpdate();
            loadOrders();
            loadMyOrders();
            JOptionPane.showMessageDialog(this, "Order updated.");
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
        }
    }

    private void addOrderLine() {
        CustomItemOption item = (CustomItemOption) orderItemBox.getSelectedItem();
        if (item == null) {
            JOptionPane.showMessageDialog(this, "Select a custom item.");
            return;
        }
        BigDecimal price = parseMoney(linePriceField.getText().trim(), "Price");
        if (price == null) {
            return;
        }
        if ("FIXED".equals(item.pricingType()) && item.fixedPrice() != null && price.compareTo(item.fixedPrice()) != 0) {
            JOptionPane.showMessageDialog(this, "This item has a fixed price of " + formatMoney(item.fixedPrice()) + ".");
            linePriceField.setText(formatMoney(item.fixedPrice()));
            return;
        }
        String details = customizationArea.getText().trim();
        if (details.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Customization details are required for each item.");
            return;
        }
        String notes = lineNotesArea.getText().trim();
        orderLineModel.addRow(new Object[]{
                item.customItemId(),
                item.name(),
                item.pricingType(),
                formatMoney(price),
                details,
                notes
        });
        customizationArea.setText("");
        lineNotesArea.setText("");
        if (!"FIXED".equals(item.pricingType())) {
            linePriceField.setText("");
        }
        updateOrderTotal();
    }

    private void removeSelectedOrderLine() {
        int selectedRow = orderLineTable.getSelectedRow();
        if (selectedRow < 0) {
            return;
        }
        orderLineModel.removeRow(orderLineTable.convertRowIndexToModel(selectedRow));
        updateOrderTotal();
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
        selectEmployeeByName(valueAt(ordersModel, modelRow, 7));
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
                       co.order_notes, co.total_amount, co.assigned_to_name,
                       col.item_name, col.unit_price, col.customization_details, col.line_notes
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
                        details.append("Total: ").append(formatMoney(rs.getBigDecimal("total_amount"))).append("\n");
                        String notes = rs.getString("order_notes");
                        if (notes != null && !notes.isBlank()) {
                            details.append("Notes: ").append(notes).append("\n");
                        }
                        details.append("\nItems\n");
                        headerWritten = true;
                    }
                    String itemName = rs.getString("item_name");
                    if (itemName != null) {
                        details.append(lineNumber++).append(". ").append(itemName)
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
            detailsArea.setCaretPosition(0);
        } catch (SQLException ex) {
            showDatabaseSetupMessage(ex);
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
            return;
        }
        boolean fixed = "FIXED".equals(item.pricingType());
        linePriceField.setEditable(!fixed);
        linePriceField.setText(fixed && item.fixedPrice() != null ? formatMoney(item.fixedPrice()) : "");
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
        dueDateField.setText("");
        orderNotesArea.setText("");
        customizationArea.setText("");
        lineNotesArea.setText("");
        applySelectedOrderItemPrice();
        updateOrderTotal();
    }

    private void updateOrderTotal() {
        orderTotalLabel.setText("Total: " + formatMoney(calculateOrderTotal()));
    }

    private BigDecimal calculateOrderTotal() {
        BigDecimal total = BigDecimal.ZERO;
        if (orderLineModel == null) {
            return total;
        }
        for (int i = 0; i < orderLineModel.getRowCount(); i++) {
            total = total.add(parseMoneyValue(orderLineModel.getValueAt(i, 3).toString()));
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

    private void addField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(field, gbc);
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

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return "$" + amount.setScale(2, java.math.RoundingMode.HALF_UP);
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

    private record CustomItemOption(Long customItemId, String name, String pricingType, BigDecimal fixedPrice) {
        @Override
        public String toString() {
            return name + ("FIXED".equals(pricingType) ? " (" + "$" + fixedPrice + ")" : " (variable)");
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
