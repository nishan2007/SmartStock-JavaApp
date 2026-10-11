package ui.screens.customorders;

import ui.helpers.UiDebouncer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

class CustomOrdersLookupTabPanel extends JPanel {
    final JTextField searchField;
    final DefaultTableModel model;
    final JTable table;
    final JTextArea detailsArea;
    final CustomOrderDetailsPanel orderDetails;
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
                CustomOrderListColumns.headings(),
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        CustomOrderListColumns.installSorting(table, new javax.swing.table.TableRowSorter<>(model));
        CustomOrderListColumns.installDateRenderers(table);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setMaxWidth(70);

        detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setLineWrap(true);
        detailsArea.setWrapStyleWord(true);

        methodBox = new JComboBox<>(new String[]{"Cash", "Card", "Cheque", "MMG", "Account"});
        amountField = new JTextField(8);
        referenceField = new JTextField(14);
        JButton payButton = new JButton("Apply Payment");
        JButton cancelButton = new JButton("Cancel Order");
        styleDialogButton(cancelButton);
        cancelButton.setEnabled(handler.canCancelOrders());
        JButton finalizePriceButton = new JButton("Finalize price");
        styleDialogButton(finalizePriceButton);finalizePriceButton.setEnabled(handler.canFinalizePrice());
        finalizePriceButton.addActionListener(e->{Long id=handler.selectedLookupOrderId(table,model);if(id==null){JOptionPane.showMessageDialog(this,"Select an order first.");return;}handler.finalizePrice(id,success->{if(success)refreshAfterMutation(handler,id);});});
        JButton refundButton = new JButton("Refund");
        JButton productionButton = new JButton("Production");
        JButton mediaButton = new JButton("Files & Design Approval");
        JButton deliveredButton = new JButton("Deliver Lines");
        JButton previewSlipButton = new JButton("Preview Slip");
        JButton printSlipButton = new JButton("Print Slip");
        JButton reprintLabelButton = new JButton("Reprint Order Label(s)");
        JButton closeButton = new JButton("Close");
        styleDialogButton(searchButton);
        styleDialogButton(payButton);
        styleDialogButton(refundButton);
        styleDialogButton(productionButton);
        styleDialogButton(mediaButton);
        styleDialogButton(deliveredButton);
        styleDialogButton(previewSlipButton);
        styleDialogButton(printSlipButton);
        styleDialogButton(reprintLabelButton);
        styleDialogButton(closeButton);
        refundButton.setEnabled(handler.canRefundPayments());
        productionButton.setEnabled(handler.canUpdateProduction());
        deliveredButton.setEnabled(handler.canDeliverOrderLines());
        previewSlipButton.setEnabled(false);
        printSlipButton.setEnabled(false);
        reprintLabelButton.setEnabled(false);

        orderDetails = new CustomOrderDetailsPanel(enabled -> payButton.setEnabled(enabled));
        if(handler.canUpdateProduction())orderDetails.setProductionUpdater((orderId,lineId,status,completion)->
                handler.updateProductionLines(orderId,List.of(lineId),status,"Updated from line details",this,success->{
                    completion.accept(success);
                    if(success)refreshAfterMutation(handler,orderId);
                }));
        JPanel printControls = new JPanel(new OrderDetailsWrapLayout());
        printControls.add(mediaButton);
        printControls.add(previewSlipButton);
        JButton printMenu = new JButton("Print ▾");
        JPopupMenu printOptions = new JPopupMenu();
        JMenuItem slipOption = new JMenuItem("Print Slip"); slipOption.addActionListener(e -> printSlipButton.doClick()); printOptions.add(slipOption);
        JMenuItem labelsOption = new JMenuItem("Reprint Order Labels"); labelsOption.addActionListener(e -> reprintLabelButton.doClick()); printOptions.add(labelsOption);
        printMenu.addActionListener(e -> printOptions.show(printMenu, 0, printMenu.getHeight())); printControls.add(printMenu);
        JPanel detailsHeader = detailsHeader(printControls);
        JPanel detailsPanel = new JPanel(new BorderLayout(0, 8));
        detailsPanel.add(detailsHeader, BorderLayout.NORTH);
        detailsPanel.add(orderDetails, BorderLayout.CENTER);
        JPanel center = new JPanel(new GridLayout(1, 2, 10, 0));
        center.add(new JScrollPane(table));
        center.add(detailsPanel);

        JPanel actionPanel = new JPanel(new OrderDetailsWrapLayout());
        JPanel paymentControls = orderDetails.paymentActions;
        paymentControls.add(new JLabel("Amount:"));
        paymentControls.add(amountField);
        paymentControls.add(new JLabel("Method:"));
        paymentControls.add(methodBox);
        paymentControls.add(new JLabel("Reference:"));
        paymentControls.add(referenceField);
        paymentControls.add(payButton);
        paymentControls.add(refundButton);
        paymentControls.add(finalizePriceButton);
        orderDetails.itemActions.add(productionButton);

        orderDetails.itemActions.add(deliveredButton);
        actionPanel.add(cancelButton);
        actionPanel.add(closeButton);

        searchButton.addActionListener(e -> handler.loadLookupOrders(model, searchField.getText().trim()));
        mediaButton.addActionListener(e -> { Long orderId=handler.selectedLookupOrderId(table,model);if(orderId==null)JOptionPane.showMessageDialog(this,"Select an order first.");else handler.openMedia(orderId,this); });
        searchField.addActionListener(e -> handler.loadLookupOrders(model, searchField.getText().trim()));
        UiDebouncer.bind(searchField, 300,
                () -> handler.loadLookupOrders(model, searchField.getText().trim()));
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                Long orderId = handler.selectedLookupOrderId(table, model);
                if (orderId != null) {
                    previewSlipButton.setEnabled(true);
                    printSlipButton.setEnabled(true);
                    reprintLabelButton.setEnabled(true);
                    handler.loadStructuredOrderDetails(orderId, orderDetails);
                    BigDecimal balance = handler.parseNullableMoneyValue(model.getValueAt(table.convertRowIndexToModel(table.getSelectedRow()), model.findColumn("Balance")));
                    amountField.setText(balance == null ? "" : balance.toPlainString());
                } else {
                    orderDetails.empty();
                    previewSlipButton.setEnabled(false);
                    printSlipButton.setEnabled(false);
                    reprintLabelButton.setEnabled(false);
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
            payButton.setEnabled(false);
            handler.applyLookupPayment(orderId, amountField.getText().trim(),
                    methodBox.getSelectedItem().toString(), referenceField.getText().trim(), this,
                    success -> {
                        payButton.setEnabled(orderDetails.canPay());
                        if (success) refreshAfterMutation(handler, orderId);
                    });
        });
        cancelButton.addActionListener(e -> {
            Long id=handler.selectedLookupOrderId(table,model);
            if(id==null){JOptionPane.showMessageDialog(this,"Select an order first.");return;}
            int row=table.convertRowIndexToModel(table.getSelectedRow());
            BigDecimal paid=handler.parseNullableMoneyValue(model.getValueAt(row,model.findColumn("Paid")));
            cancelButton.setEnabled(false);
            handler.loadCancellationLines(id,lines->{
                if(lines==null){cancelButton.setEnabled(handler.canCancelOrders());return;}
                CancellationRequest request=promptCancellation(lines,paid==null?BigDecimal.ZERO:paid);
                if(request==null){cancelButton.setEnabled(handler.canCancelOrders());return;}
                handler.cancelOrder(id,request,success->{cancelButton.setEnabled(handler.canCancelOrders());if(success)refreshAfterMutation(handler,id);});
            });
        });
        refundButton.addActionListener(e -> {
            Long orderId = handler.selectedLookupOrderId(table, model);
            if (orderId == null) {
                JOptionPane.showMessageDialog(this, "Select an order first.");
                return;
            }
            int row=table.convertRowIndexToModel(table.getSelectedRow());
            if("Price pending".equals(model.getValueAt(row,model.findColumn("Total")))) {
                handler.refundPendingDeposit(orderId,methodBox.getSelectedItem().toString(),referenceField.getText().trim(),success->{if(success)refreshAfterMutation(handler,orderId);});return;
            }
            refundButton.setEnabled(false);
            handler.loadReturnableLines(orderId, lines -> {
                if (lines == null) {
                    refundButton.setEnabled(true);
                    return;
                }
                if (lines.isEmpty()) {
                    refundButton.setEnabled(true);
                    JOptionPane.showMessageDialog(this, "There are no refundable order lines left on this order.");
                    return;
                }
                RefundRequest refundRequest = promptLineRefund(lines);
                if (refundRequest == null) {
                    refundButton.setEnabled(true);
                    return;
                }
                handler.applyLookupLineRefund(orderId, refundRequest.lines(),
                        methodBox.getSelectedItem().toString(), referenceField.getText().trim(),
                        refundRequest.reason(), this, success -> {
                            refundButton.setEnabled(true);
                            if (success) refreshAfterMutation(handler, orderId);
                        });
            });
        });
        productionButton.addActionListener(e -> {
            Long orderId = handler.selectedLookupOrderId(table, model);
            if (orderId == null) {
                JOptionPane.showMessageDialog(this, "Select an order first.");
                return;
            }
            productionButton.setEnabled(false);
            handler.loadProductionLines(orderId, lines -> {
                if (lines == null) {
                    productionButton.setEnabled(true);
                    return;
                }
                if (lines.isEmpty()) {
                    productionButton.setEnabled(true);
                    JOptionPane.showMessageDialog(this, "There are no order lines to update.");
                    return;
                }
                ProductionUpdateRequest request = promptProductionUpdate(lines);
                if (request == null) {
                    productionButton.setEnabled(true);
                    return;
                }
                handler.updateProductionLines(orderId, request.lineIds(), request.status(), request.notes(), this,
                        success -> {
                            productionButton.setEnabled(true);
                            if (success) refreshAfterMutation(handler, orderId);
                        });
            });
        });
        deliveredButton.addActionListener(e -> {
            Long orderId = handler.selectedLookupOrderId(table, model);
            if (orderId == null) {
                JOptionPane.showMessageDialog(this, "Select an order first.");
                return;
            }
            deliveredButton.setEnabled(false);
            handler.loadDeliverableLines(orderId, lines -> {
                if (lines == null) {
                    deliveredButton.setEnabled(true);
                    return;
                }
                if (lines.isEmpty()) {
                    deliveredButton.setEnabled(true);
                    JOptionPane.showMessageDialog(this, "There are no order lines left to deliver.");
                    return;
                }
                LineDeliveryRequest deliveryRequest = promptLineDelivery(lines);
                if (deliveryRequest == null) {
                    deliveredButton.setEnabled(true);
                    return;
                }
                handler.markLookupLinesDelivered(orderId, deliveryRequest.lineIds(), deliveryRequest.notes(), this,
                        success -> {
                            deliveredButton.setEnabled(true);
                            if (success) refreshAfterMutation(handler, orderId);
                        });
            });
        });
        previewSlipButton.addActionListener(e -> {
            String orderNumber = selectedOrderNumber();
            if (orderNumber != null) handler.previewOrderSlip(orderNumber);
        });
        printSlipButton.addActionListener(e -> {
            String orderNumber = selectedOrderNumber();
            if (orderNumber != null) handler.printOrderSlip(orderNumber);
        });
        reprintLabelButton.addActionListener(e -> {
            String orderNumber = selectedOrderNumber();
            if (orderNumber != null) handler.reprintOrderLabels(orderNumber);
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

    static JPanel detailsHeader(JPanel controls) {
        JPanel header = new JPanel(new BorderLayout(0, 4));
        JLabel title = new JLabel("Order Details");
        title.setBorder(new EmptyBorder(0, 8, 0, 0));
        header.add(title, BorderLayout.NORTH);
        header.add(controls, BorderLayout.CENTER);
        controls.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                header.revalidate();
            }
        });
        return header;
    }

    private String selectedOrderNumber() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) return null;
        int modelRow = table.convertRowIndexToModel(selectedRow);
        return String.valueOf(model.getValueAt(modelRow, 1));
    }

    private void refreshAfterMutation(Handler handler, Long orderId) {
        handler.loadLookupOrders(model, searchField.getText().trim());
        handler.refreshRelatedOrders();
        if (orderId.equals(handler.selectedLookupOrderId(table, model))) handler.loadStructuredOrderDetails(orderId, orderDetails);
    }

    private CancellationRequest promptCancellation(List<LineReturnOption> lines,BigDecimal paid) {
        DefaultTableModel rows=new DefaultTableModel(new Object[]{"Item","Variant","Amount cancelled","Stock action"},0){
            public boolean isCellEditable(int row,int column){return column==3;}
        };
        for(LineReturnOption line:lines)rows.addRow(new Object[]{line.itemName(),line.variantName(),line.remainingAmount().toPlainString(),"No Restock"});
        JTable items=new JTable(rows);items.setRowHeight(28);
        items.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(new JComboBox<>(new String[]{"No Restock","Restock","Damaged","Customer Kept","Waste"})));
        JTextArea reason=new JTextArea(3,30);reason.setLineWrap(true);reason.setWrapStyleWord(true);
        JComboBox<String> method=new JComboBox<>(new String[]{"Cash","Card","Cheque","MMG","Account"});
        method.setSelectedItem(methodBox.getSelectedItem());
        JTextField reference=new JTextField(20);
        JPanel fields=new JPanel(new GridLayout(0,2,8,8));
        fields.add(new JLabel("Refund to customer: "+paid.toPlainString()));fields.add(new JLabel("All remaining lines will be returned."));
        fields.add(new JLabel("Refund method:"));fields.add(method);
        fields.add(new JLabel("Refund reference:"));fields.add(reference);
        method.setEnabled(paid.signum()>0);reference.setEnabled(paid.signum()>0);
        fields.add(new JLabel("Cancellation reason:"));fields.add(new JScrollPane(reason));
        JPanel panel=new JPanel(new BorderLayout(8,8));panel.add(new JScrollPane(items),BorderLayout.CENTER);panel.add(fields,BorderLayout.SOUTH);
        panel.setPreferredSize(new Dimension(780,380));
        while(JOptionPane.showConfirmDialog(this,panel,"Cancel Order — Return & Refund",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.OK_OPTION){
            if(items.isEditing()&&!items.getCellEditor().stopCellEditing())continue;
            if(reason.getText().isBlank()){JOptionPane.showMessageDialog(this,"A cancellation reason is required.");continue;}
            String selected=paid.signum()>0?method.getSelectedItem().toString():"Account";
            if(List.of("Card","Cheque","MMG").contains(selected)&&reference.getText().isBlank()){JOptionPane.showMessageDialog(this,"Enter a refund reference.");continue;}
            List<LineReturnRequest> returns=new ArrayList<>();
            for(int i=0;i<lines.size();i++)returns.add(new LineReturnRequest(lines.get(i).lineId(),lines.get(i).remainingAmount(),false,restockCode(rows.getValueAt(i,3))));
            return new CancellationRequest(returns,selected,reference.getText().trim(),reason.getText().trim());
        }
        return null;
    }

    record CancellationRequest(List<LineReturnRequest> lines,String method,String reference,String reason){}

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
                return column == 0 && !"DELIVERED".equals(getValueAt(row, 4))
                        && !"FULL".equals(getValueAt(row, 5));
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
        JButton deliverAllButton = new JButton("Deliver All");
        styleDialogButton(deliverAllButton);
        deliverAllButton.setToolTipText("Select all pending lines. Press OK to record delivery.");
        deliverAllButton.addActionListener(e -> {
            if(deliveryTable.isEditing())deliveryTable.getCellEditor().stopCellEditing();
            for(int row=0;row<deliveryModel.getRowCount();row++)
                deliveryModel.setValueAt(deliveryModel.isCellEditable(row,0),row,0);
        });
        JPanel deliveryActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        deliveryActions.add(deliverAllButton);
        panel.add(deliveryActions, BorderLayout.NORTH);
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
        if(deliveryTable.isEditing())deliveryTable.getCellEditor().stopCellEditing();
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
        default void loadStructuredOrderDetails(Long orderId, CustomOrderDetailsPanel panel) { }
        void loadReturnableLines(Long orderId, Consumer<List<LineReturnOption>> completion);
        void loadDeliverableLines(Long orderId, Consumer<List<LineDeliveryOption>> completion);
        BigDecimal parseNullableMoneyValue(Object value);
        void applyLookupPayment(Long orderId, String amountText, String method, String reference,
                                Component parent, Consumer<Boolean> completion);
        void applyLookupLineRefund(Long orderId, List<LineReturnRequest> lines, String method,
                                   String reference, String reason, Component parent,
                                   Consumer<Boolean> completion);
        void markLookupLinesDelivered(Long orderId, List<Long> lineIds, String notes,
                                      Component parent, Consumer<Boolean> completion);
        void loadProductionLines(Long orderId, Consumer<List<ProductionLineOption>> completion);
        void updateProductionLines(Long orderId, List<Long> lineIds, String productionStatus,
                                   String notes, Component parent, Consumer<Boolean> completion);
        default boolean canCancelOrders(){return false;}
        default void loadCancellationLines(Long id,Consumer<List<LineReturnOption>> completion){loadReturnableLines(id,completion);}
        default void cancelOrder(Long id,CancellationRequest request,Consumer<Boolean> completion){completion.accept(false);}
        default boolean canFinalizePrice(){return false;}
        default void finalizePrice(Long id,Consumer<Boolean> completion){completion.accept(false);}
        default void refundPendingDeposit(Long id,String method,String reference,Consumer<Boolean> completion){completion.accept(false);}
        boolean canRefundPayments();
        boolean canDeliverOrderLines();
        boolean canUpdateProduction();
        void previewOrderSlip(String orderNumber);
        void printOrderSlip(String orderNumber);
        default void openMedia(Long orderId, Component parent) { }
        void reprintOrderLabels(String orderNumber);
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
