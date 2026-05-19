package ui.screens.customorders;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;

class CustomOrdersManageTabPanel extends JPanel {
    final DefaultTableModel ordersModel;
    final JTable ordersTable;
    final TableRowSorter<DefaultTableModel> ordersSorter;
    final JTextField orderSearchField;
    final JComboBox<String> statusFilterBox;
    final JTextArea selectedOrderDetailsArea;

    CustomOrdersManageTabPanel(Handler handler) {
        super(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel filterPanel = new JPanel(new BorderLayout(8, 0));
        orderSearchField = new JTextField();
        statusFilterBox = new JComboBox<>(new String[]{"All", "NEW", "ASSIGNED", "IN_PROGRESS", "READY", "COMPLETED", "DELIVERED", "CANCELLED"});
        JButton refreshButton = new JButton("Refresh");
        filterPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        filterPanel.add(orderSearchField, BorderLayout.CENTER);
        JPanel filterActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filterActions.add(statusFilterBox);
        filterActions.add(refreshButton);
        filterPanel.add(filterActions, BorderLayout.EAST);

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
                handler.loadSelectedOrder();
            }
        });
        orderSearchField.getDocument().addDocumentListener(simpleDocumentListener(handler.applyFilter()));
        statusFilterBox.addActionListener(e -> handler.applyFilter().run());

        JPanel left = new JPanel(new BorderLayout(8, 8));
        left.add(filterPanel, BorderLayout.NORTH);
        left.add(new JScrollPane(ordersTable), BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(8, 8));
        right.setPreferredSize(new Dimension(420, 0));
        right.setBorder(BorderFactory.createTitledBorder("Order Details"));
        selectedOrderDetailsArea = new JTextArea();
        selectedOrderDetailsArea.setEditable(false);
        selectedOrderDetailsArea.setLineWrap(true);
        selectedOrderDetailsArea.setWrapStyleWord(true);
        refreshButton.addActionListener(e -> handler.refreshOrders());

        right.add(new JScrollPane(selectedOrderDetailsArea), BorderLayout.CENTER);

        add(left, BorderLayout.CENTER);
        add(right, BorderLayout.EAST);
    }

    private javax.swing.event.DocumentListener simpleDocumentListener(Runnable callback) {
        return new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
        };
    }

    interface Handler {
        void loadSelectedOrder();
        Runnable applyFilter();
        void refreshOrders();
    }
}
