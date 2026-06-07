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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SalesQuotes extends JFrame {
    private final DefaultTableModel quoteModel = readOnlyModel("ID", "Quote #", "Customer", "Status", "Valid Until", "Total");
    private final JTable quoteTable = new JTable(quoteModel);

    public SalesQuotes() {
        setTitle("Sales Quotes");
        setSize(1120, 720);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setJMenuBar(AppMenuBar.create(this, "SalesQuotes"));

        JPanel mainPanel = new JPanel(new BorderLayout(12, 12));
        mainPanel.setBorder(new EmptyBorder(14, 14, 14, 14));
        add(mainPanel, BorderLayout.CENTER);

        JLabel title = new JLabel("Sales Quotes");
        title.setFont(new Font("SansSerif", Font.BOLD, 26));
        mainPanel.add(title, BorderLayout.NORTH);
        mainPanel.add(tablePanel(quoteTable, quoteButtons()), BorderLayout.CENTER);

        refreshQuotes();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel quoteButtons() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton newQuote = new JButton("New Quote");
        JButton issue = new JButton("Issue");
        JButton accept = new JButton("Accept Quote");
        JButton preview = new JButton("Preview Quote");
        JButton refresh = new JButton("Refresh");
        panel.add(newQuote);
        panel.add(issue);
        panel.add(accept);
        panel.add(preview);
        panel.add(refresh);
        newQuote.addActionListener(e -> openQuoteDialog());
        issue.addActionListener(e -> issueSelectedQuote());
        accept.addActionListener(e -> acceptSelectedQuote());
        preview.addActionListener(e -> previewQuote());
        refresh.addActionListener(e -> refreshQuotes());
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

    private void refreshQuotes() {
        quoteModel.setRowCount(0);
        try {
            for (SalesQuoteOrderViewService.QuoteSummary row : SalesQuoteOrderViewService.listQuotes()) {
                quoteModel.addRow(new Object[]{
                        row.quoteId(),
                        row.quoteNumber(),
                        row.customerName(),
                        row.status(),
                        row.validUntil(),
                        row.totalAmount()
                });
            }
        } catch (SQLException ex) {
            showError("Failed to load quotes", ex);
        }
    }

    private void openQuoteDialog() {
        QuoteEditor editor = new QuoteEditor(this);
        editor.setVisible(true);
        if (editor.created) {
            refreshQuotes();
        }
    }

    private void issueSelectedQuote() {
        Long quoteId = selectedId(quoteTable);
        if (quoteId == null) return;
        try {
            SalesQuoteOrderService.issueQuote(quoteId);
            refreshQuotes();
        } catch (SQLException ex) {
            showError("Failed to issue quote", ex);
        }
    }

    private void acceptSelectedQuote() {
        Long quoteId = selectedId(quoteTable);
        if (quoteId == null) return;
        try {
            SalesQuoteOrderService.OrderResult result = SalesQuoteOrderService.acceptQuote(quoteId);
            refreshQuotes();
            JOptionPane.showMessageDialog(this, "Created sales order " + result.orderNumber() + ".");
        } catch (SQLException ex) {
            showError("Failed to accept quote", ex);
        }
    }

    private void previewQuote() {
        Long quoteId = selectedId(quoteTable);
        if (quoteId == null) return;
        try {
            WindowHelper.showPosWindow(new SalesQuoteOrderDocumentPreview("Quote Preview", SalesQuoteOrderDocumentBuilder.buildQuote(quoteId)), this);
        } catch (SQLException ex) {
            showError("Failed to preview quote", ex);
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

    static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    static JPanel formPanel(String[] labels, JComponent[] fields) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            gbc.weightx = 0;
            panel.add(new JLabel(labels[i] + ":"), gbc);
            gbc.gridx = 1;
            gbc.weightx = 1;
            panel.add(fields[i], gbc);
        }
        return panel;
    }

    static BigDecimal parseMoney(String value) {
        String clean = value == null ? "" : value.replace("$", "").replace(",", "").trim();
        return clean.isBlank() ? BigDecimal.ZERO : new BigDecimal(clean);
    }

    private void showError(String title, Exception ex) {
        JOptionPane.showMessageDialog(this, title + ".\n\n" + ex.getMessage(), "Sales Quotes", JOptionPane.ERROR_MESSAGE);
    }

    private static class QuoteEditor extends JDialog {
        private final JComboBox<SalesQuoteOrderViewService.CustomerOption> customerBox = new JComboBox<>();
        private final JTextField validUntilField = new JTextField(LocalDate.now().plusDays(30).toString());
        private final JTextArea notesArea = new JTextArea(3, 40);
        private final DefaultTableModel lineModel = new DefaultTableModel(new String[]{"Product ID", "Item", "SKU", "Qty", "Unit", "Disc %", "Delivery", "Notes"}, 0);
        private boolean created;

        QuoteEditor(JFrame owner) {
            super(owner, "New Sales Quote", true);
            setSize(860, 620);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));
            JPanel main = new JPanel(new BorderLayout(8, 8));
            main.setBorder(new EmptyBorder(12, 12, 12, 12));
            add(main, BorderLayout.CENTER);
            loadCustomers();
            main.add(formPanel(new String[]{"Customer", "Valid Until", "Notes"}, new JComponent[]{customerBox, validUntilField, new JScrollPane(notesArea)}), BorderLayout.NORTH);
            JTable lineTable = new JTable(lineModel);
            main.add(new JScrollPane(lineTable), BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton addLine = new JButton("Add Line");
            JButton removeLine = new JButton("Remove Line");
            JButton create = new JButton("Create Quote");
            JButton cancel = new JButton("Cancel");
            buttons.add(addLine);
            buttons.add(removeLine);
            buttons.add(create);
            buttons.add(cancel);
            main.add(buttons, BorderLayout.SOUTH);
            addLine.addActionListener(e -> addLine());
            removeLine.addActionListener(e -> {
                int row = lineTable.getSelectedRow();
                if (row >= 0) lineModel.removeRow(lineTable.convertRowIndexToModel(row));
            });
            create.addActionListener(e -> createQuote());
            cancel.addActionListener(e -> dispose());
        }

        private void addLine() {
            LineEditor editor = new LineEditor(this);
            editor.setVisible(true);
            if (editor.line != null) {
                SalesQuoteOrderViewService.ProductOption product = editor.line.product();
                lineModel.addRow(new Object[]{
                        product == null ? null : product.productId(),
                        editor.line.itemName(),
                        editor.line.sku(),
                        editor.line.quantity(),
                        editor.line.unitPrice(),
                        editor.line.discountPercent(),
                        editor.line.deliveryMethod(),
                        editor.line.notes()
                });
            }
        }

        private void createQuote() {
            SalesQuoteOrderViewService.CustomerOption customer = (SalesQuoteOrderViewService.CustomerOption) customerBox.getSelectedItem();
            if (customer == null) {
                JOptionPane.showMessageDialog(this, "Select a customer.");
                return;
            }
            List<SalesQuoteOrderService.QuoteLineInput> lines = new ArrayList<>();
            for (int i = 0; i < lineModel.getRowCount(); i++) {
                Object productIdValue = lineModel.getValueAt(i, 0);
                Integer productId = productIdValue == null ? null : Integer.parseInt(productIdValue.toString());
                lines.add(new SalesQuoteOrderService.QuoteLineInput(
                        productId,
                        String.valueOf(lineModel.getValueAt(i, 1)),
                        String.valueOf(lineModel.getValueAt(i, 2)),
                        Integer.parseInt(String.valueOf(lineModel.getValueAt(i, 3))),
                        parseMoney(String.valueOf(lineModel.getValueAt(i, 4))),
                        parseMoney(String.valueOf(lineModel.getValueAt(i, 5))),
                        String.valueOf(lineModel.getValueAt(i, 6)),
                        String.valueOf(lineModel.getValueAt(i, 7))
                ));
            }
            try {
                SalesQuoteOrderService.QuoteResult result = SalesQuoteOrderService.createQuote(
                        customer.customerId(),
                        LocalDate.parse(validUntilField.getText().trim()),
                        notesArea.getText(),
                        lines
                );
                created = true;
                JOptionPane.showMessageDialog(this, "Created quote " + result.quoteNumber() + ".");
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to create quote.\n\n" + ex.getMessage(), "Sales Quote", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void loadCustomers() {
            try {
                for (SalesQuoteOrderViewService.CustomerOption customer : SalesQuoteOrderViewService.listCustomers()) {
                    customerBox.addItem(customer);
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Failed to load customers.\n\n" + ex.getMessage(), "Sales Quote", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static class LineEditor extends JDialog {
        private final JComboBox<SalesQuoteOrderViewService.ProductOption> productBox = new JComboBox<>();
        private final JTextField itemField = new JTextField();
        private final JTextField skuField = new JTextField();
        private final JTextField qtyField = new JTextField("1");
        private final JTextField unitField = new JTextField("0.00");
        private final JTextField discountField = new JTextField("0");
        private final JComboBox<String> deliveryBox = new JComboBox<>(new String[]{"PICKUP", "LOCAL_DELIVERY", "SHIP", "INSTALLATION"});
        private final JTextField notesField = new JTextField();
        private LineInput line;

        LineEditor(JDialog owner) {
            super(owner, "Add Quote Line", true);
            setSize(520, 360);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));
            loadProducts();
            productBox.addActionListener(e -> fillProduct());
            JPanel panel = formPanel(
                    new String[]{"Product", "Item", "SKU", "Qty", "Unit Price", "Discount %", "Delivery", "Notes"},
                    new JComponent[]{productBox, itemField, skuField, qtyField, unitField, discountField, deliveryBox, notesField}
            );
            panel.setBorder(new EmptyBorder(12, 12, 12, 12));
            add(panel, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            JButton add = new JButton("Add");
            JButton cancel = new JButton("Cancel");
            buttons.add(add);
            buttons.add(cancel);
            add(buttons, BorderLayout.SOUTH);
            add.addActionListener(e -> save());
            cancel.addActionListener(e -> dispose());
        }

        private void save() {
            try {
                SalesQuoteOrderViewService.ProductOption product = (SalesQuoteOrderViewService.ProductOption) productBox.getSelectedItem();
                String itemName = itemField.getText().trim();
                if (itemName.isBlank()) {
                    throw new IllegalArgumentException("Item name is required.");
                }
                line = new LineInput(product, itemName, skuField.getText(), Integer.parseInt(qtyField.getText().trim()),
                        parseMoney(unitField.getText()), parseMoney(discountField.getText()),
                        String.valueOf(deliveryBox.getSelectedItem()), notesField.getText());
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Line", JOptionPane.ERROR_MESSAGE);
            }
        }

        private void fillProduct() {
            SalesQuoteOrderViewService.ProductOption product = (SalesQuoteOrderViewService.ProductOption) productBox.getSelectedItem();
            if (product == null || product.productId() == null) return;
            itemField.setText(product.name());
            skuField.setText(product.sku());
            unitField.setText(product.price().toPlainString());
        }

        private void loadProducts() {
            try {
                for (SalesQuoteOrderViewService.ProductOption product : SalesQuoteOrderViewService.listProducts()) {
                    productBox.addItem(product);
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Failed to load products.\n\n" + ex.getMessage(), "Products", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private record LineInput(SalesQuoteOrderViewService.ProductOption product, String itemName, String sku, int quantity,
                             BigDecimal unitPrice, BigDecimal discountPercent,
                             String deliveryMethod, String notes) {
    }
}
