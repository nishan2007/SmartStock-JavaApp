package ui.screens.customorders;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;

class CustomOrdersLookupTabPanel extends JPanel {
    final JTextField searchField;
    final DefaultTableModel model;
    final JTable table;
    final JTextArea detailsArea;
    final JComboBox<String> methodBox;
    final JTextField amountField;
    final JTextField referenceField;

    CustomOrdersLookupTabPanel(Handler handler) {
        super(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        searchField = new JTextField();
        JButton searchButton = new JButton("Search");
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.add(new JLabel("Search:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchButton, BorderLayout.EAST);

        model = new DefaultTableModel(
                new Object[]{"ID", "Order #", "Status", "Customer", "Phone", "Total", "Paid", "Balance", "Payment", "Reference", "Created"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setMaxWidth(70);

        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);

        JPanel center = new JPanel(new GridLayout(1, 2, 10, 0));
        center.add(new JScrollPane(table));
        center.add(new JScrollPane(detailsArea));

        methodBox = new JComboBox<>(new String[]{"Cash", "Card", "Cheque", "Account"});
        amountField = new JTextField(8);
        referenceField = new JTextField(14);
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

        searchButton.addActionListener(e -> handler.loadLookupOrders(model, searchField.getText().trim()));
        searchField.addActionListener(e -> handler.loadLookupOrders(model, searchField.getText().trim()));
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Long orderId = handler.selectedLookupOrderId(table, model);
                if (orderId != null) {
                    handler.loadOrderDetails(orderId, detailsArea);
                    BigDecimal balance = handler.parseNullableMoneyValue(model.getValueAt(table.convertRowIndexToModel(table.getSelectedRow()), 7));
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
            Long orderId = handler.selectedLookupOrderId(table, model);
            if (orderId == null) {
                JOptionPane.showMessageDialog(this, "Select an order first.");
                return;
            }
            if (handler.applyLookupPayment(orderId, amountField.getText().trim(), methodBox.getSelectedItem().toString(), referenceField.getText().trim(), this)) {
                handler.loadLookupOrders(model, searchField.getText().trim());
                handler.refreshRelatedOrders();
                handler.loadOrderDetails(orderId, detailsArea);
            }
        });
        deliveredButton.addActionListener(e -> {
            Long orderId = handler.selectedLookupOrderId(table, model);
            if (orderId == null) {
                JOptionPane.showMessageDialog(this, "Select an order first.");
                return;
            }
            if (handler.markLookupOrderDelivered(orderId, this)) {
                handler.loadLookupOrders(model, searchField.getText().trim());
                handler.refreshRelatedOrders();
                handler.loadOrderDetails(orderId, detailsArea);
            }
        });
        closeButton.addActionListener(e -> {
            Window window = SwingUtilities.getWindowAncestor(this);
            if (window != null) {
                window.dispose();
            }
        });
        methodBox.setSelectedItem("Cash");
        referenceField.setEnabled(false);

        add(searchPanel, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(actionPanel, BorderLayout.SOUTH);
    }

    void load(Handler handler) {
        handler.loadLookupOrders(model, searchField.getText().trim());
    }

    private void styleDialogButton(JButton button) {
        button.setBackground(new Color(64, 64, 64));
        button.setForeground(Color.WHITE);
        button.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 120), 1));
        button.setFocusPainted(false);
        button.setOpaque(true);
    }

    interface Handler {
        void loadLookupOrders(DefaultTableModel model, String search);
        Long selectedLookupOrderId(JTable table, DefaultTableModel model);
        void loadOrderDetails(Long orderId, JTextArea detailsArea);
        BigDecimal parseNullableMoneyValue(Object value);
        boolean applyLookupPayment(Long orderId, String amountText, String method, String reference, Component parent);
        boolean markLookupOrderDelivered(Long orderId, Component parent);
        void refreshRelatedOrders();
    }
}
