package ui.screens;

import Receipt.SalesQuoteOrderDocumentBuilder;
import services.SalesQuoteOrderService;
import services.SalesQuoteOrderViewService;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SalesOrders extends JFrame {
    private final DefaultTableModel orderModel = SalesQuotes.readOnlyModel("ID", "Order #", "Customer", "Status", "Payment", "Balance", "Quote #");
    private final DefaultTableModel deliveryModel = SalesQuotes.readOnlyModel("ID", "Delivery #", "Order #", "Customer", "Method", "Balance", "Created");
    private final DefaultTableModel auditModel = SalesQuotes.readOnlyModel("Time", "Document", "Action", "Field", "Old", "New", "User", "Reason");
    private final JTable orderTable = new JTable(orderModel);
    private final JTable deliveryTable = new JTable(deliveryModel);
    private final JTable auditTable = new JTable(auditModel);

    public SalesOrders() {
        setTitle("Sales Orders");
        setSize(1280, 760);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "SalesOrders"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        JLabel title = new JLabel("Sales Orders");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        mainPanel.add(title, BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Orders", tablePanel(orderTable, orderButtons()));
        tabs.addTab("Deliveries", tablePanel(deliveryTable, deliveryButtons()));
        tabs.addTab("Audit", tablePanel(auditTable, auditButtons()));
        mainPanel.add(tabs, BorderLayout.CENTER);

        refreshAll();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel orderButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton payment = new JButton("Add Payment");
        JButton account = new JButton("Place on Account");
        JButton delivery = new JButton("Post Delivery");
        JButton preview = new JButton("Preview Order");
        JButton refresh = new JButton("Refresh");
        panel.add(payment);
        panel.add(account);
        panel.add(delivery);
        panel.add(preview);
        panel.add(refresh);
        payment.addActionListener(e -> addPayment());
        account.addActionListener(e -> chargeToAccount());
        delivery.addActionListener(e -> postDelivery());
        preview.addActionListener(e -> previewOrder());
        refresh.addActionListener(e -> refreshAll());
        return panel;
    }

    private JPanel deliveryButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton preview = new JButton("Preview Delivery Bill");
        JButton refresh = new JButton("Refresh");
        panel.add(preview);
        panel.add(refresh);
        preview.addActionListener(e -> previewDelivery());
        refresh.addActionListener(e -> refreshAll());
        return panel;
    }

    private JPanel auditButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton refresh = new JButton("Refresh");
        panel.add(refresh);
        refresh.addActionListener(e -> refreshAll());
        return panel;
    }

    private JPanel tablePanel(JTable table, JPanel buttons) {
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(buttons, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private void refreshAll() {
        loadOrders();
        loadDeliveries();
        loadAudit();
    }

    private void loadOrders() {
        orderModel.setRowCount(0);
        try {
            for (SalesQuoteOrderViewService.OrderSummary row : SalesQuoteOrderViewService.listOrders()) {
                orderModel.addRow(new Object[]{
                        row.orderId(),
                        row.orderNumber(),
                        row.customerName(),
                        row.status(),
                        row.paymentStatus(),
                        row.balanceDue(),
                        row.quoteNumber()
                });
            }
        } catch (SQLException ex) {
            showError("Failed to load orders", ex);
        }
    }

    private void loadDeliveries() {
        deliveryModel.setRowCount(0);
        try {
            for (SalesQuoteOrderViewService.DeliverySummary row : SalesQuoteOrderViewService.listDeliveries()) {
                deliveryModel.addRow(new Object[]{
                        row.deliveryEventId(),
                        row.deliveryNumber(),
                        row.orderNumber(),
                        row.customerName(),
                        row.deliveryMethod(),
                        row.balanceDue(),
                        row.createdAt()
                });
            }
        } catch (SQLException ex) {
            showError("Failed to load deliveries", ex);
        }
    }

    private void loadAudit() {
        auditModel.setRowCount(0);
        try {
            for (SalesQuoteOrderViewService.AuditEntry row : SalesQuoteOrderViewService.listAudit()) {
                auditModel.addRow(new Object[]{
                        row.createdAt(),
                        row.document(),
                        row.actionType(),
                        row.fieldName(),
                        row.oldValue(),
                        row.newValue(),
                        row.userName(),
                        row.reason()
                });
            }
        } catch (SQLException ex) {
            showError("Failed to load audit", ex);
        }
    }

    private void addPayment() {
        Long orderId = selectedId(orderTable);
        if (orderId == null) return;
        JTextField amount = new JTextField();
        JComboBox<String> method = new JComboBox<>(new String[]{"CASH", "CARD", "CHEQUE", "MMG"});
        JTextField reference = new JTextField();
        JPanel panel = SalesQuotes.formPanel(new String[]{"Amount", "Method", "Reference"}, new JComponent[]{amount, method, reference});
        if (JOptionPane.showConfirmDialog(this, panel, "Add Sales Order Payment", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        try {
            SalesQuoteOrderService.recordPayment(orderId, SalesQuotes.parseMoney(amount.getText()), (String) method.getSelectedItem(), reference.getText());
            refreshAll();
        } catch (Exception ex) {
            showError("Failed to add payment", ex);
        }
    }

    private void chargeToAccount() {
        Long orderId = selectedId(orderTable);
        if (orderId == null) return;
        int confirm = JOptionPane.showConfirmDialog(this, "Place this order's remaining balance on the customer account?", "Account Credit", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) return;
        try {
            SalesQuoteOrderService.chargeOrderToAccount(orderId, "Placed on customer account from Sales Orders.");
            refreshAll();
        } catch (SQLException ex) {
            showError("Failed to place order on account", ex);
        }
    }

    private void postDelivery() {
        Long orderId = selectedId(orderTable);
        if (orderId == null) return;
        DeliveryDialog dialog = new DeliveryDialog(this, orderId);
        dialog.setVisible(true);
        if (dialog.created) {
            refreshAll();
        }
    }

    private void previewOrder() {
        Long orderId = selectedId(orderTable);
        if (orderId == null) return;
        try {
            WindowHelper.showPosWindow(new SalesQuoteOrderDocumentPreview("Sales Order Preview", SalesQuoteOrderDocumentBuilder.buildOrder(orderId)), this);
        } catch (SQLException ex) {
            showError("Failed to preview order", ex);
        }
    }

    private void previewDelivery() {
        Long deliveryId = selectedId(deliveryTable);
        if (deliveryId == null) return;
        try {
            WindowHelper.showPosWindow(new SalesQuoteOrderDocumentPreview("Delivery Bill Preview", SalesQuoteOrderDocumentBuilder.buildDelivery(deliveryId)), this);
        } catch (SQLException ex) {
            showError("Failed to preview delivery bill", ex);
        }
    }

    private Long selectedId(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a row first.");
            return null;
        }
        Object value = table.getValueAt(row, 0);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private void showError(String title, Exception ex) {
        JOptionPane.showMessageDialog(this, title + ".\n\n" + ex.getMessage(), "Sales Orders", JOptionPane.ERROR_MESSAGE);
    }

    private static class DeliveryDialog extends JDialog {
        private final long orderId;
        private final JComboBox<String> methodBox = new JComboBox<>(new String[]{"PICKUP", "LOCAL_DELIVERY", "SHIP", "INSTALLATION"});
        private final JTextField receiverField = new JTextField();
        private final JTextField notesField = new JTextField();
        private final DefaultTableModel lineModel = new DefaultTableModel(new String[]{"Line ID", "Item", "Ordered", "Delivered", "Remaining", "Available", "Deliver Now"}, 0);
        private boolean created;

        DeliveryDialog(JFrame owner, long orderId) {
            super(owner, "Post Sales Order Delivery", true);
            this.orderId = orderId;
            setSize(820, 540);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));
            JPanel main = new JPanel(new BorderLayout(8, 8));
            main.setBorder(new EmptyBorder(12, 12, 12, 12));
            add(main, BorderLayout.CENTER);
            main.add(SalesQuotes.formPanel(new String[]{"Method", "Receiver", "Notes"}, new JComponent[]{methodBox, receiverField, notesField}), BorderLayout.NORTH);
            JTable table = new JTable(lineModel);
            main.add(new JScrollPane(table), BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton post = new JButton("Post Delivery");
            JButton cancel = new JButton("Cancel");
            buttons.add(post);
            buttons.add(cancel);
            main.add(buttons, BorderLayout.SOUTH);
            post.addActionListener(e -> post());
            cancel.addActionListener(e -> dispose());
            loadLines();
        }

        private void loadLines() {
            try {
                for (SalesQuoteOrderViewService.DeliverableLine line : SalesQuoteOrderViewService.listDeliverableLines(orderId)) {
                    lineModel.addRow(new Object[]{
                            line.orderLineId(),
                            line.itemName(),
                            line.quantityOrdered(),
                            line.quantityDelivered(),
                            line.remaining(),
                            line.productId() == null ? "Manual" : line.availableStock(),
                            0
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Failed to load order lines.\n\n" + ex.getMessage(), "Delivery", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void post() {
            List<SalesQuoteOrderService.DeliveryLineInput> lines = new ArrayList<>();
            for (int i = 0; i < lineModel.getRowCount(); i++) {
                int qty = Integer.parseInt(String.valueOf(lineModel.getValueAt(i, 6)));
                if (qty > 0) {
                    lines.add(new SalesQuoteOrderService.DeliveryLineInput(
                            Long.parseLong(String.valueOf(lineModel.getValueAt(i, 0))),
                            qty
                    ));
                }
            }
            try {
                SalesQuoteOrderService.DeliveryResult result = SalesQuoteOrderService.postDelivery(
                        orderId,
                        String.valueOf(methodBox.getSelectedItem()),
                        receiverField.getText(),
                        notesField.getText(),
                        lines
                );
                created = true;
                JOptionPane.showMessageDialog(this, "Posted delivery " + result.deliveryNumber() + ".");
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to post delivery.\n\n" + ex.getMessage(), "Delivery", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
