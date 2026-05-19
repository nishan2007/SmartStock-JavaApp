package ui.screens.customorders;

import services.CustomOrderDataService.CustomItemOption;
import services.CustomOrderDataService.PrintMaterialOption;
import services.CustomOrderDataService.PrintSizePresetOption;
import services.CustomOrderDataService.VariantOption;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class CustomOrdersNewOrderTabPanel extends JPanel {
    final CustomerInfoPanel customerInfoPanel;
    final DatePickerField dueDateField;
    final JComboBox<CustomItemOption> orderItemBox;
    final JComboBox<VariantOption> variantBox;
    final JTextField linePriceField;
    final JComboBox<PrintMaterialOption> printMaterialBox;
    final JComboBox<PrintSizePresetOption> printSizePresetBox;
    final JTextField printChargeField;
    final JTextField printLineCountField;
    final JTextField printDescriptionField;
    final List<JComponent> printAddOnComponents = new ArrayList<>();
    final List<JComponent> printLineComponents = new ArrayList<>();
    DefaultTableModel printAddonModel;
    JTable printAddonTable;
    final JTextField lineQuantityField;
    final JTextField lineDiscountPercentField;
    final JTextField lineDiscountReasonField;
    final JTextField priceOverrideReasonField;
    final JTextField widthField;
    final JTextField lengthField;
    final JLabel areaCalculationLabel;
    final JComboBox<String> designPlacementBox;
    final JTextField designPlacementField;
    final JTextArea lineNotesArea;
    final DefaultTableModel orderLineModel;
    final JTable orderLineTable;
    final JButton addLineButton;
    final JLabel orderTotalLabel;
    final ButtonGroup paymentMethodGroup;
    final JToggleButton cashPaymentButton;
    final JToggleButton cardPaymentButton;
    final JToggleButton chequePaymentButton;
    final JToggleButton mmgPaymentButton;
    final JToggleButton accountPaymentButton;
    final JTextField paymentReferenceField;
    final JTextField upfrontPaymentField;
    final JLabel balanceDueLabel;
    final List<JComponent> areaLineComponents = new ArrayList<>();

    CustomOrdersNewOrderTabPanel(Handler handler) {
        super(new BorderLayout(12, 12));
        setBorder(new EmptyBorder(14, 14, 14, 14));

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createTitledBorder("Customer"));
        dueDateField = new DatePickerField();
        customerInfoPanel = new CustomerInfoPanel("Due Date:", dueDateField);
        top.add(customerInfoPanel, BorderLayout.CENTER);

        JPanel linePanel = new JPanel(new BorderLayout(12, 8));
        linePanel.setBorder(BorderFactory.createTitledBorder("Order Item"));
        JPanel leftLinePanel = new JPanel(new GridBagLayout());
        JPanel rightLinePanel = new JPanel(new GridBagLayout());
        GridBagConstraints leftLineGbc = formGbc();
        GridBagConstraints rightLineGbc = formGbc();
        orderItemBox = new JComboBox<>();
        variantBox = new JComboBox<>();
        linePriceField = new JTextField();
        printMaterialBox = new JComboBox<>();
        printSizePresetBox = new JComboBox<>();
        printChargeField = new JTextField("0.00");
        printLineCountField = new JTextField("1");
        printDescriptionField = new JTextField();
        lineQuantityField = new JTextField("1");
        lineDiscountPercentField = new JTextField("0", 6);
        lineDiscountReasonField = new JTextField();
        priceOverrideReasonField = new JTextField();
        widthField = new JTextField();
        lengthField = new JTextField();
        areaCalculationLabel = new JLabel(" ");
        designPlacementBox = new JComboBox<>();
        designPlacementField = new JTextField();
        lineNotesArea = new JTextArea(4, 20);
        lineNotesArea.setLineWrap(true);
        lineNotesArea.setWrapStyleWord(true);
        addLineButton = new JButton("Add to Order");
        JButton removeLineButton = new JButton("Remove Selected");
        JButton addPlacementButton = new JButton("Add Placement");
        JButton addPrintAddonButton = new JButton("Add Print Add On");
        JButton removePrintAddonButton = new JButton("Remove Print Add On");

        addField(leftLinePanel, leftLineGbc, 0, "Item:", orderItemBox);
        addField(leftLinePanel, leftLineGbc, 1, "Size / Variant:", variantBox);
        addField(leftLinePanel, leftLineGbc, 2, "Base Price:", linePriceField);
        addField(leftLinePanel, leftLineGbc, 3, "Price Reason:", priceOverrideReasonField);
        addTrackedField(areaLineComponents, leftLinePanel, leftLineGbc, 4, "Width:", widthField);
        addTrackedField(areaLineComponents, leftLinePanel, leftLineGbc, 5, "Length:", lengthField);
        leftLineGbc.gridx = 1;
        leftLineGbc.gridy = 6;
        leftLinePanel.add(areaCalculationLabel, leftLineGbc);
        areaLineComponents.add(areaCalculationLabel);
        JScrollPane lineNotesScroll = new JScrollPane(lineNotesArea);
        lineNotesScroll.setPreferredSize(new Dimension(300, 120));
        addField(leftLinePanel, leftLineGbc, 7, "Order Instructions:", lineNotesScroll);
        addVerticalFormSpacer(leftLinePanel, leftLineGbc, 8);

        addField(rightLinePanel, rightLineGbc, 0, "Print Add On:", printMaterialBox);
        addTrackedField(printAddOnComponents, rightLinePanel, rightLineGbc, 1, "Print Size:", printSizePresetBox);
        addTrackedField(printAddOnComponents, rightLinePanel, rightLineGbc, 2, "Print Price:", printChargeField);
        addTrackedField(printLineComponents, rightLinePanel, rightLineGbc, 3, "Print Lines:", printLineCountField);
        printAddOnComponents.addAll(printLineComponents);
        addTrackedField(printAddOnComponents, rightLinePanel, rightLineGbc, 4, "Print Description:", printDescriptionField);
        JPanel printAddonButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        printAddonButtons.add(addPrintAddonButton);
        printAddonButtons.add(removePrintAddonButton);
        addTrackedField(printAddOnComponents, rightLinePanel, rightLineGbc, 5, "Print Add Ons:", buildPrintAddonsPanel(printAddonButtons));
        addField(rightLinePanel, rightLineGbc, 6, "Design Placement:", buildDesignPlacementPanel(addPlacementButton));
        addField(rightLinePanel, rightLineGbc, 7, "Quantity:", lineQuantityField);
        addField(rightLinePanel, rightLineGbc, 8, "Line Discount %:", lineDiscountPercentField);
        addField(rightLinePanel, rightLineGbc, 9, "Discount Reason:", lineDiscountReasonField);
        rightLineGbc.gridx = 1;
        rightLineGbc.gridy = 10;
        JPanel lineButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lineButtons.add(addLineButton);
        lineButtons.add(removeLineButton);
        rightLinePanel.add(lineButtons, rightLineGbc);
        addVerticalFormSpacer(rightLinePanel, rightLineGbc, 11);

        JPanel lineColumns = new JPanel(new GridLayout(1, 2, 14, 0));
        lineColumns.add(leftLinePanel);
        lineColumns.add(rightLinePanel);
        linePanel.add(lineColumns, BorderLayout.CENTER);

        orderItemBox.addActionListener(e -> handler.orderItemChanged());
        variantBox.addActionListener(e -> handler.variantChanged());
        printMaterialBox.addActionListener(e -> handler.printMaterialChanged());
        printSizePresetBox.addActionListener(e -> handler.printPresetChanged());
        printLineCountField.getDocument().addDocumentListener(simpleDocumentListener(handler.printLineCountChanged()));
        addPrintAddonButton.addActionListener(e -> handler.addPrintAddon());
        removePrintAddonButton.addActionListener(e -> handler.removePrintAddon());
        widthField.getDocument().addDocumentListener(simpleDocumentListener(handler.areaChanged()));
        lengthField.getDocument().addDocumentListener(simpleDocumentListener(handler.areaChanged()));
        addPlacementButton.addActionListener(e -> handler.addPlacement());
        addLineButton.addActionListener(e -> handler.addOrderLine());
        removeLineButton.addActionListener(e -> handler.removeOrderLine());

        orderLineModel = new DefaultTableModel(new Object[]{"Item ID", "Variant ID", "Item", "Size / Variant", "Pricing", "Price", "Details", "Notes", "Width", "Length", "Dimension Unit", "Area", "Area Unit", "Area Price", "Print Material ID", "Print Material", "Print Preset ID", "Print Size", "Print Charge", "Base Price", "Print Lines", "Print Add Ons", "Print Add On Data", "Original Total", "Discount %", "Discount Amount", "Discount Reason", "Min Deposit %", "Original Base Price", "Override Price", "Override Reason"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        orderLineTable = new JTable(orderLineModel);
        orderLineTable.setRowHeight(28);
        orderLineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hideColumn(orderLineTable, 0);
        hideColumn(orderLineTable, 1);
        for (int hiddenColumn = 8; hiddenColumn <= 20; hiddenColumn++) {
            hideColumn(orderLineTable, hiddenColumn);
        }
        hideColumn(orderLineTable, 22);
        hideColumn(orderLineTable, 23);
        hideColumn(orderLineTable, 24);
        for (int hiddenColumn = 25; hiddenColumn <= 30; hiddenColumn++) {
            hideColumn(orderLineTable, hiddenColumn);
        }
        orderLineTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                handler.cartSelectionChanged();
            }
        });

        orderTotalLabel = new JLabel("Total: $0.00");
        orderTotalLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        paymentMethodGroup = new ButtonGroup();
        cashPaymentButton = createPaymentMethodButton("Cash", "CASH", handler);
        cardPaymentButton = createPaymentMethodButton("Card", "CARD", handler);
        chequePaymentButton = createPaymentMethodButton("Cheque", "CHEQUE", handler);
        mmgPaymentButton = createPaymentMethodButton("MMG", "MMG", handler);
        accountPaymentButton = createPaymentMethodButton("Account", "ACCOUNT", handler);
        paymentReferenceField = new JTextField();
        upfrontPaymentField = new JTextField("0.00", 8);
        balanceDueLabel = new JLabel("Balance Due: $0.00");
        balanceDueLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        upfrontPaymentField.getDocument().addDocumentListener(simpleDocumentListener(handler.upfrontChanged()));

        JPanel cartPanel = new JPanel(new BorderLayout());
        cartPanel.setBorder(BorderFactory.createTitledBorder("Cart"));
        JScrollPane cartScrollPane = new JScrollPane(orderLineTable);
        cartScrollPane.setPreferredSize(new Dimension(0, 170));
        cartPanel.add(cartScrollPane, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.add(linePanel, BorderLayout.CENTER);
        center.add(cartPanel, BorderLayout.SOUTH);

        JButton saveOrderButton = new JButton("Save Custom Order");
        JButton clearOrderButton = new JButton("Clear Order");
        saveOrderButton.addActionListener(e -> handler.saveOrder());
        clearOrderButton.addActionListener(e -> handler.clearOrder());

        JPanel footer = new JPanel(new BorderLayout());
        JPanel paymentPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        paymentPanel.setBorder(BorderFactory.createTitledBorder("Payment Method"));
        paymentPanel.add(cashPaymentButton);
        paymentPanel.add(cardPaymentButton);
        paymentPanel.add(chequePaymentButton);
        paymentPanel.add(mmgPaymentButton);
        paymentPanel.add(accountPaymentButton);
        paymentPanel.add(new JLabel("Upfront:"));
        paymentPanel.add(upfrontPaymentField);
        paymentPanel.add(new JLabel("Reference:"));
        paymentReferenceField.setPreferredSize(new Dimension(150, 30));
        paymentPanel.add(paymentReferenceField);

        JPanel totalsPanel = new JPanel(new GridLayout(2, 1, 0, 6));
        JPanel totalLine = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        totalLine.add(orderTotalLabel);
        totalLine.add(balanceDueLabel);
        JPanel footerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footerButtons.add(clearOrderButton);
        footerButtons.add(saveOrderButton);
        totalsPanel.add(totalLine);
        totalsPanel.add(footerButtons);

        footer.add(paymentPanel, BorderLayout.CENTER);
        footer.add(totalsPanel, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    private JToggleButton createPaymentMethodButton(String label, String method, Handler handler) {
        JToggleButton button = new JToggleButton(label);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension("ACCOUNT".equals(method) ? 120 : 96, 42));
        button.addActionListener(e -> handler.selectPaymentMethod(method));
        paymentMethodGroup.add(button);
        return button;
    }

    private JPanel buildDesignPlacementPanel(JButton addPlacementButton) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.add(designPlacementBox, BorderLayout.WEST);
        panel.add(designPlacementField, BorderLayout.CENTER);
        panel.add(addPlacementButton, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildPrintAddonsPanel(JPanel buttons) {
        printAddonModel = new DefaultTableModel(new Object[]{"Material ID", "Material", "Preset ID", "Size", "Pricing", "Description", "Lines", "Price"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        printAddonTable = new JTable(printAddonModel);
        printAddonTable.setRowHeight(24);
        printAddonTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hideColumn(printAddonTable, 0);
        hideColumn(printAddonTable, 2);
        hideColumn(printAddonTable, 4);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        JScrollPane scrollPane = new JScrollPane(printAddonTable);
        scrollPane.setPreferredSize(new Dimension(300, 88));
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void hideColumn(JTable table, int column) {
        table.getColumnModel().getColumn(column).setMinWidth(0);
        table.getColumnModel().getColumn(column).setMaxWidth(0);
        table.getColumnModel().getColumn(column).setPreferredWidth(0);
    }

    private JLabel addField(JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.weighty = 0;
        JLabel labelComponent = new JLabel(label);
        panel.add(labelComponent, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.weighty = 0;
        panel.add(field, gbc);
        return labelComponent;
    }

    private void addTrackedField(List<JComponent> trackedComponents, JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        trackedComponents.add(addField(panel, gbc, row, label, field));
        trackedComponents.add(field);
    }

    private void addVerticalFormSpacer(JPanel panel, GridBagConstraints gbc, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        panel.add(Box.createVerticalGlue(), gbc);
        gbc.gridwidth = 1;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
    }

    private GridBagConstraints formGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
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

    interface Handler {
        void orderItemChanged();
        void variantChanged();
        void printMaterialChanged();
        void printPresetChanged();
        Runnable printLineCountChanged();
        void addPrintAddon();
        void removePrintAddon();
        Runnable areaChanged();
        void addPlacement();
        void addOrderLine();
        void removeOrderLine();
        void cartSelectionChanged();
        void selectPaymentMethod(String method);
        Runnable upfrontChanged();
        void saveOrder();
        void clearOrder();
    }
}
