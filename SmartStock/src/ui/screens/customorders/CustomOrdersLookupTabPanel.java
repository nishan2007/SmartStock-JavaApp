package ui.screens.customorders;

import ui.helpers.UiDebouncer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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

        methodBox = new JComboBox<>(new String[]{"Cash", "Card", "Cheque", "MMG", "Account"});
        amountField = new JTextField(8);
        referenceField = new JTextField(14);
        JButton payButton = new JButton("Apply Payment");
        JButton refundButton = new JButton("Refund");
        JButton productionButton = new JButton("Production");
        JButton deliveredButton = new JButton("Deliver Lines");
        JButton closeButton = new JButton("Close");
        styleDialogButton(searchButton);
        styleDialogButton(payButton);
        styleDialogButton(refundButton);
        styleDialogButton(productionButton);
        styleDialogButton(deliveredButton);
        styleDialogButton(closeButton);
        refundButton.setEnabled(handler.canRefundPayments());
        productionButton.setEnabled(handler.canUpdateProduction());
        deliveredButton.setEnabled(handler.canDeliverOrderLines());

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actionPanel.add(new JLabel("Amount:"));
        actionPanel.add(amountField);
        actionPanel.add(new JLabel("Method:"));
        actionPanel.add(methodBox);
        actionPanel.add(new JLabel("Reference:"));
        actionPanel.add(referenceField);
        actionPanel.add(payButton);
        actionPanel.add(refundButton);
        actionPanel.add(productionButton);
        actionPanel.add(deliveredButton);
        actionPanel.add(closeButton);

        searchButton.addActionListener(e -> handler.loadLookupOrders(model, searchField.getText().trim()));
        searchField.addActionListener(e -> handler.loadLookupOrders(model, searchField.getText().trim()));
        UiDebouncer.bind(searchField, 300,
                () -> handler.loadLookupOrders(model, searchField.getText().trim()));
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
        refundButton.addActionListener(e -> {
            Long orderId = handler.selectedLookupOrderId(table, model);
            if (orderId == null) {
                JOptionPane.showMessageDialog(this, "Select an order first.");
                return;
            }
            List<LineReturnOption> lines = handler.loadReturnableLines(orderId);
            if (lines.isEmpty()) {
                JOptionPane.showMessageDialog(this, "There are no refundable order lines left on this order.");
                return;
            }
            RefundRequest refundRequest = promptLineRefund(lines);
            if (refundRequest == null) {
                return;
            }
            if (handler.applyLookupLineRefund(
                    orderId,
                    refundRequest.lines(),
                    methodBox.getSelectedItem().toString(),
                    referenceField.getText().trim(),
                    refundRequest.reason(),
                    this
            )) {
                handler.loadLookupOrders(model, searchField.getText().trim());
                handler.refreshRelatedOrders();
                handler.loadOrderDetails(orderId, detailsArea);
            }
        });
        productionButton.addActionListener(e -> {
            Long orderId = handler.selectedLookupOrderId(table, model);
            if (orderId == null) {
                JOptionPane.showMessageDialog(this, "Select an order first.");
                return;
            }
            List<ProductionLineOption> lines = handler.loadProductionLines(orderId);
            if (lines.isEmpty()) {
                JOptionPane.showMessageDialog(this, "There are no order lines to update.");
                return;
            }
            ProductionUpdateRequest request = promptProductionUpdate(lines);
            if (request == null) {
                return;
            }
            if (handler.updateProductionLines(orderId, request.lineIds(), request.status(), request.notes(), this)) {
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
            List<LineDeliveryOption> lines = handler.loadDeliverableLines(orderId);
            if (lines.isEmpty()) {
                JOptionPane.showMessageDialog(this, "There are no order lines left to deliver.");
                return;
            }
            LineDeliveryRequest deliveryRequest = promptLineDelivery(lines);
            if (deliveryRequest == null) {
                return;
            }
            if (handler.markLookupLinesDelivered(orderId, deliveryRequest.lineIds(), deliveryRequest.notes(), this)) {
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

    private RefundRequest promptLineRefund(List<LineReturnOption> lines) {
        JComboBox<String> reasonBox = new JComboBox<>(new String[]{
                "Returned Order",
                "Customer Cancelled",
                "Quality Issue",
                "Payment Mistake",
                "Other"
        });
        JTextArea notesArea = new JTextArea(3, 38);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

        DefaultTableModel refundModel = new DefaultTableModel(
                new Object[]{"Return", "Line ID", "Item", "Variant", "Line Total", "Returned", "Remaining", "Partial", "Refund Amount", "Restock"},
                0
        ) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return switch (columnIndex) {
                    case 0, 7 -> Boolean.class;
                    default -> Object.class;
                };
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 || column == 7 || column == 8 || column == 9;
            }
        };
        for (LineReturnOption line : lines) {
            refundModel.addRow(new Object[]{
                    Boolean.FALSE,
                    line.lineId(),
                    line.itemName(),
                    line.variantName() == null ? "" : line.variantName(),
                    line.lineTotal().toPlainString(),
                    line.returnedAmount().toPlainString(),
                    line.remainingAmount().toPlainString(),
                    Boolean.FALSE,
                    line.remainingAmount().toPlainString(),
                    "No Restock"
            });
        }

        JTable refundTable = new JTable(refundModel);
        refundTable.setRowHeight(26);
        refundTable.getColumnModel().getColumn(1).setMinWidth(0);
        refundTable.getColumnModel().getColumn(1).setMaxWidth(0);
        refundTable.getColumnModel().getColumn(1).setPreferredWidth(0);
        JComboBox<String> restockBox = new JComboBox<>(new String[]{"No Restock", "Restock", "Damaged", "Customer Kept", "Waste"});
        refundTable.getColumnModel().getColumn(9).setCellEditor(new DefaultCellEditor(restockBox));

        JPanel reasonPanel = new JPanel(new BorderLayout(8, 8));
        JPanel reasonRow = new JPanel(new BorderLayout(8, 0));
        reasonRow.add(new JLabel("Reason:"), BorderLayout.WEST);
        reasonRow.add(reasonBox, BorderLayout.CENTER);
        reasonPanel.add(reasonRow, BorderLayout.NORTH);
        reasonPanel.add(new JScrollPane(notesArea), BorderLayout.CENTER);
        JLabel hint = new JLabel("Check each returned line. Use Partial when the customer is only refunded part of that line.");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        reasonPanel.add(hint, BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JScrollPane(refundTable), BorderLayout.CENTER);
        panel.add(reasonPanel, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(900, 420));

        int choice = JOptionPane.showConfirmDialog(this, panel, "Refund Order Lines", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return null;
        }
        if (refundTable.isEditing()) {
            refundTable.getCellEditor().stopCellEditing();
        }
        String reason = reasonBox.getSelectedItem() == null ? "" : reasonBox.getSelectedItem().toString();
        String notes = notesArea.getText() == null ? "" : notesArea.getText().trim();
        if (reason.isBlank()) {
            JOptionPane.showMessageDialog(this, "A refund reason is required.");
            return null;
        }
        List<LineReturnRequest> requests = new ArrayList<>();
        for (int row = 0; row < refundModel.getRowCount(); row++) {
            if (!Boolean.TRUE.equals(refundModel.getValueAt(row, 0))) {
                continue;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(refundModel.getValueAt(row, 8).toString().replace("$", "").replace(",", "").trim());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Refund amount must be valid for every selected line.");
                return null;
            }
            BigDecimal remaining = new BigDecimal(refundModel.getValueAt(row, 6).toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                JOptionPane.showMessageDialog(this, "Refund amount must be greater than zero for every selected line.");
                return null;
            }
            if (amount.compareTo(remaining) > 0) {
                JOptionPane.showMessageDialog(this, "Refund amount cannot be more than the remaining refundable amount for a line.");
                return null;
            }
            requests.add(new LineReturnRequest(
                    Long.parseLong(refundModel.getValueAt(row, 1).toString()),
                    amount,
                    Boolean.TRUE.equals(refundModel.getValueAt(row, 7)),
                    restockCode(refundModel.getValueAt(row, 9))
            ));
        }
        if (requests.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one order line to refund.");
            return null;
        }
        return new RefundRequest(requests, notes.isBlank() ? reason : reason + ": " + notes);
    }

    private LineDeliveryRequest promptLineDelivery(List<LineDeliveryOption> lines) {
        DefaultTableModel deliveryModel = new DefaultTableModel(
                new Object[]{"Deliver", "Line ID", "Item", "Variant", "Status", "Return"},
                0
        ) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : Object.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
        for (LineDeliveryOption line : lines) {
            deliveryModel.addRow(new Object[]{
                    Boolean.FALSE,
                    line.lineId(),
                    line.itemName(),
                    line.variantName() == null ? "" : line.variantName(),
                    line.deliveryStatus(),
                    line.returnStatus()
            });
        }
        JTable deliveryTable = new JTable(deliveryModel);
        deliveryTable.setRowHeight(26);
        deliveryTable.getColumnModel().getColumn(1).setMinWidth(0);
        deliveryTable.getColumnModel().getColumn(1).setMaxWidth(0);
        deliveryTable.getColumnModel().getColumn(1).setPreferredWidth(0);

        JTextArea notesArea = new JTextArea(3, 38);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JScrollPane(deliveryTable), BorderLayout.CENTER);
        JPanel notesPanel = new JPanel(new BorderLayout(8, 0));
        notesPanel.add(new JLabel("Notes:"), BorderLayout.WEST);
        notesPanel.add(new JScrollPane(notesArea), BorderLayout.CENTER);
        panel.add(notesPanel, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(780, 360));

        int choice = JOptionPane.showConfirmDialog(this, panel, "Deliver Order Lines", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return null;
        }
        List<Long> lineIds = new ArrayList<>();
        for (int row = 0; row < deliveryModel.getRowCount(); row++) {
            if (Boolean.TRUE.equals(deliveryModel.getValueAt(row, 0))) {
                lineIds.add(Long.parseLong(deliveryModel.getValueAt(row, 1).toString()));
            }
        }
        if (lineIds.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one order line to deliver.");
            return null;
        }
        return new LineDeliveryRequest(lineIds, notesArea.getText() == null ? "" : notesArea.getText().trim());
    }

    private ProductionUpdateRequest promptProductionUpdate(List<ProductionLineOption> lines) {
        DefaultTableModel productionModel = new DefaultTableModel(
                new Object[]{"Update", "Line ID", "Item", "Variant", "Production", "Delivery"},
                0
        ) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : Object.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
        for (ProductionLineOption line : lines) {
            productionModel.addRow(new Object[]{
                    Boolean.FALSE,
                    line.lineId(),
                    line.itemName(),
                    line.variantName() == null ? "" : line.variantName(),
                    line.productionStatus(),
                    line.deliveryStatus()
            });
        }
        JTable productionTable = new JTable(productionModel);
        productionTable.setRowHeight(26);
        productionTable.getColumnModel().getColumn(1).setMinWidth(0);
        productionTable.getColumnModel().getColumn(1).setMaxWidth(0);
        productionTable.getColumnModel().getColumn(1).setPreferredWidth(0);

        JComboBox<String> statusBox = new JComboBox<>(new String[]{
                "Design Approved",
                "Printed",
                "Finished",
                "Quality Checked",
                "Ready"
        });
        JTextArea notesArea = new JTextArea(3, 38);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

        JPanel controls = new JPanel(new BorderLayout(8, 8));
        JPanel statusRow = new JPanel(new BorderLayout(8, 0));
        statusRow.add(new JLabel("Set Status:"), BorderLayout.WEST);
        statusRow.add(statusBox, BorderLayout.CENTER);
        controls.add(statusRow, BorderLayout.NORTH);
        controls.add(new JScrollPane(notesArea), BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JScrollPane(productionTable), BorderLayout.CENTER);
        panel.add(controls, BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(820, 380));

        int choice = JOptionPane.showConfirmDialog(this, panel, "Update Production Checklist", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return null;
        }
        List<Long> lineIds = new ArrayList<>();
        for (int row = 0; row < productionModel.getRowCount(); row++) {
            if (Boolean.TRUE.equals(productionModel.getValueAt(row, 0))) {
                lineIds.add(Long.parseLong(productionModel.getValueAt(row, 1).toString()));
            }
        }
        if (lineIds.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Select at least one order line to update.");
            return null;
        }
        return new ProductionUpdateRequest(lineIds, statusCode(statusBox.getSelectedItem()), notesArea.getText() == null ? "" : notesArea.getText().trim());
    }

    private String statusCode(Object value) {
        String text = value == null ? "" : value.toString().trim().toUpperCase().replace(" ", "_");
        return text.isBlank() ? "DESIGN_APPROVED" : text;
    }

    private String restockCode(Object value) {
        String text = value == null ? "" : value.toString();
        return switch (text) {
            case "Restock" -> "RESTOCK";
            case "Damaged" -> "DAMAGED";
            case "Customer Kept" -> "CUSTOMER_KEPT";
            case "Waste" -> "WASTE";
            default -> "NO_RESTOCK";
        };
    }

    void load(Handler handler) {
        handler.loadLookupOrders(model, searchField.getText().trim());
    }

    private String promptRefundReason() {
        JComboBox<String> reasonBox = new JComboBox<>(new String[]{
                "Returned Order",
                "Customer Cancelled",
                "Quality Issue",
                "Payment Mistake",
                "Other"
        });
        JTextArea notesArea = new JTextArea(4, 26);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel reasonRow = new JPanel(new BorderLayout(8, 0));
        reasonRow.add(new JLabel("Reason:"), BorderLayout.WEST);
        reasonRow.add(reasonBox, BorderLayout.CENTER);
        panel.add(reasonRow, BorderLayout.NORTH);
        panel.add(new JScrollPane(notesArea), BorderLayout.CENTER);

        JLabel hint = new JLabel("Payment Mistake means the customer should still owe the refunded amount.");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        panel.add(hint, BorderLayout.SOUTH);

        int choice = JOptionPane.showConfirmDialog(this, panel, "Refund Payment", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return null;
        }
        String reason = reasonBox.getSelectedItem() == null ? "" : reasonBox.getSelectedItem().toString();
        String notes = notesArea.getText() == null ? "" : notesArea.getText().trim();
        if (reason.isBlank()) {
            JOptionPane.showMessageDialog(this, "A refund reason is required.");
            return null;
        }
        return notes.isBlank() ? reason : reason + ": " + notes;
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
        List<LineReturnOption> loadReturnableLines(Long orderId);
        List<LineDeliveryOption> loadDeliverableLines(Long orderId);
        BigDecimal parseNullableMoneyValue(Object value);
        boolean applyLookupPayment(Long orderId, String amountText, String method, String reference, Component parent);
        boolean applyLookupRefund(Long orderId, String amountText, String method, String reference, String reason, Component parent);
        boolean applyLookupLineRefund(Long orderId, List<LineReturnRequest> lines, String method, String reference, String reason, Component parent);
        boolean markLookupOrderDelivered(Long orderId, Component parent);
        boolean markLookupLinesDelivered(Long orderId, List<Long> lineIds, String notes, Component parent);
        List<ProductionLineOption> loadProductionLines(Long orderId);
        boolean updateProductionLines(Long orderId, List<Long> lineIds, String productionStatus, String notes, Component parent);
        boolean canRefundPayments();
        boolean canDeliverOrderLines();
        boolean canUpdateProduction();
        void refreshRelatedOrders();
    }

    record LineReturnOption(
            Long lineId,
            String itemName,
            String variantName,
            BigDecimal lineTotal,
            BigDecimal returnedAmount,
            BigDecimal remainingAmount
    ) {
    }

    record LineReturnRequest(
            Long lineId,
            BigDecimal refundAmount,
            boolean partial,
            String restockAction
    ) {
    }

    private record RefundRequest(List<LineReturnRequest> lines, String reason) {
    }

    record LineDeliveryOption(
            Long lineId,
            String itemName,
            String variantName,
            String deliveryStatus,
            String returnStatus
    ) {
    }

    record ProductionLineOption(
            Long lineId,
            String itemName,
            String variantName,
            String productionStatus,
            String deliveryStatus
    ) {
    }

    private record LineDeliveryRequest(List<Long> lineIds, String notes) {
    }

    private record ProductionUpdateRequest(List<Long> lineIds, String status, String notes) {
    }
}
