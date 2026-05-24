package ui.screens.customorders;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;

class CustomOrdersMyOrdersTabPanel extends JPanel {
    final DefaultTableModel myOrdersModel;
    final JTable myOrdersTable;
    final TableRowSorter<DefaultTableModel> myOrdersSorter;
    final JTextField myOrderSearchField;
    final JComboBox<String> myStatusFilterBox;
    final JTextArea myOrderDetailsArea;

    CustomOrdersMyOrdersTabPanel(Handler handler) {
        super(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(14, 14, 14, 14));

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
                handler.loadSelectedMyOrder();
            }
        });
        myOrderSearchField.getDocument().addDocumentListener(simpleDocumentListener(handler.applyFilter()));
        myStatusFilterBox.addActionListener(e -> handler.applyFilter().run());
        refreshButton.addActionListener(e -> handler.refreshMyOrders());

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
        JPanel slipActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton previewSlipButton = new JButton("Preview Slip");
        JButton printSlipButton = new JButton("Print Slip");
        slipActions.add(previewSlipButton);
        slipActions.add(printSlipButton);
        previewSlipButton.addActionListener(e -> handler.previewSelectedMyOrderSlip());
        printSlipButton.addActionListener(e -> handler.printSelectedMyOrderSlip());
        right.add(slipActions, BorderLayout.SOUTH);

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
        void loadSelectedMyOrder();
        Runnable applyFilter();
        void refreshMyOrders();
        void previewSelectedMyOrderSlip();
        void printSelectedMyOrderSlip();
    }
}
