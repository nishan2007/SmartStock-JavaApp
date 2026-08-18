package ui.screens.customorders;

import services.CustomOrderDataService.CustomItemOption;
import services.CustomOrderDataService.PrintMaterialOption;
import services.CustomOrderDataService.PrintSizePresetOption;
import services.CustomOrderDataService.VariantOption;

import javax.swing.*;
import ui.helpers.ThemeManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

class CustomOrdersNewOrderTabPanel extends JPanel {
    private static final Color BG = new Color(13, 17, 23);
    private static final Color PANEL = new Color(22, 27, 34);
    private static final Color PANEL_ALT = new Color(30, 36, 46);
    private static final Color FIELD_BG = new Color(9, 13, 19);
    private static final Color BORDER = new Color(54, 65, 82);
    private static final Color TEXT = new Color(238, 242, 247);
    private static final Color MUTED = new Color(166, 176, 190);
    private static final Color ACCENT = new Color(37, 99, 235);
    private static final Color ACCENT_DARK = new Color(29, 78, 216);
    private static final Color TEAL = new Color(20, 184, 166);
    private static final Color AMBER = new Color(245, 158, 11);
    private static final Color DANGER = new Color(185, 28, 28);

    final CustomerInfoPanel customerInfoPanel;
    final JCheckBox dueDateEnabledBox;
    final DatePickerField dueDateField;
    final JTextArea orderNotesArea;
    final JTextField itemLookupField;
    final JComboBox<CustomItemOption> orderItemBox;
    final JComboBox<VariantOption> variantBox;
    final JTextField linePriceField;
    final JLabel priceRateUnitLabel;
    final JComboBox<PrintMaterialOption> printMaterialBox;
    final JComboBox<PrintSizePresetOption> printSizePresetBox;
    final JTextField printChargeField;
    final JTextField printLineCountField;
    final JTextField printDescriptionField;
    final JLabel printAddonSummaryLabel;
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
    final JLabel lineCountLabel;
    final JLabel orderTotalLabel;
    final JLabel minimumDepositLabel;
    final JLabel reviewLineCountLabel;
    final JLabel reviewOrderTotalLabel;
    final JLabel reviewMinimumDepositLabel;
    final DefaultListModel<String> reviewLineModel;
    final JLabel customerSummaryLabel;
    final JLabel paymentMinimumDepositLabel;
    final JLabel depositOverrideNoticeLabel;
    final JTextField depositOverrideReasonField;
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

    private final CardLayout stepLayout = new CardLayout();
    private final JPanel stepCards = new JPanel(stepLayout);
    private final List<JToggleButton> stepButtons = new ArrayList<>();
    private JButton saveOrderButton;
    private JButton saveAndPrintOrderButton;
    private boolean alwaysPrintOrderSlip;
    private int currentStep;

    CustomOrdersNewOrderTabPanel(Handler handler) {
        super(new BorderLayout(8, 8));
        // This workflow owns a complete high-contrast palette. The global theme
        // pass otherwise replaces only some of these colors, producing dark text
        // on dark tiles/buttons (most visibly with the Windows light theme).
        putClientProperty("SmartStock.preserveThemeColors", Boolean.TRUE);
        setBorder(new EmptyBorder(8, 8, 8, 8));
        setBackground(BG);
        stepCards.setBackground(BG);

        dueDateEnabledBox = new JCheckBox("Due date");
        dueDateField = new DatePickerField();
        dueDateField.setEnabled(false);
        dueDateEnabledBox.addActionListener(e -> dueDateField.setEnabled(dueDateEnabledBox.isSelected()));
        orderNotesArea = new JTextArea(4, 30);
        orderNotesArea.setLineWrap(true);
        orderNotesArea.setWrapStyleWord(true);

        orderItemBox = new JComboBox<>();
        itemLookupField = new JTextField();
        itemLookupField.setToolTipText(
                "Search by item name, department, item type, brand, variant, SKU, or barcode. Words can be entered in any order.");
        JButton itemLookupButton = new JButton("Lookup");
        variantBox = new JComboBox<>();
        linePriceField = new JTextField();
        priceRateUnitLabel = new JLabel(" ");
        printMaterialBox = new JComboBox<>();
        printSizePresetBox = new JComboBox<>();
        printChargeField = new JTextField("0");
        printLineCountField = new JTextField("1");
        printDescriptionField = new JTextField();
        printAddonSummaryLabel = new JLabel("No print add-ons added.");
        printAddonSummaryLabel.setForeground(MUTED);
        lineQuantityField = new JTextField("1", 6);
        lineDiscountPercentField = new JTextField("0", 6);
        lineDiscountReasonField = new JTextField();
        priceOverrideReasonField = new JTextField();
        widthField = new JTextField();
        lengthField = new JTextField();
        areaCalculationLabel = new JLabel(" ");
        designPlacementBox = new JComboBox<>();
        designPlacementField = new JTextField();
        lineNotesArea = new JTextArea(5, 24);
        lineNotesArea.setLineWrap(true);
        lineNotesArea.setWrapStyleWord(true);
        addLineButton = new JButton("Add Line");

        lineCountLabel = summaryTile("Lines", "0", TEAL);
        orderTotalLabel = summaryTile("Order Total", "$0", ACCENT);
        minimumDepositLabel = summaryTile("Minimum Deposit", "$0", AMBER);

        orderLineModel = new DefaultTableModel(new Object[]{"Item ID", "Variant ID", "Item", "Size / Variant", "Pricing", "Total", "Details", "Notes", "Width", "Length", "Dimension Unit", "Area", "Area Unit", "Area Price", "Print Material ID", "Print Material", "Print Preset ID", "Print Size", "Print Charge", "Base Price", "Print Lines", "Print Add Ons", "Print Add On Data", "Original Total", "Discount %", "Discount Amount", "Discount Reason", "Min Deposit %", "Original Base Price", "Override Price", "Override Reason"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        orderLineTable = new JTable(orderLineModel);
        orderLineTable.setRowHeight(30);
        orderLineTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hideColumn(orderLineTable, 0);
        hideColumn(orderLineTable, 1);
        for (int hiddenColumn = 8; hiddenColumn <= 20; hiddenColumn++) {
            hideColumn(orderLineTable, hiddenColumn);
        }
        hideColumn(orderLineTable, 22);
        hideColumn(orderLineTable, 23);
        for (int hiddenColumn = 24; hiddenColumn <= 30; hiddenColumn++) {
            hideColumn(orderLineTable, hiddenColumn);
        }
        orderLineTable.getColumn("Item").setPreferredWidth(220);
        orderLineTable.getColumn("Size / Variant").setPreferredWidth(160);
        orderLineTable.getColumn("Pricing").setPreferredWidth(90);
        orderLineTable.getColumn("Total").setPreferredWidth(90);
        orderLineTable.getColumn("Details").setPreferredWidth(360);
        orderLineTable.getColumn("Notes").setPreferredWidth(320);
        orderLineTable.getColumn("Print Add Ons").setPreferredWidth(260);
        styleTable(orderLineTable);

        reviewLineCountLabel = new JLabel("Lines: 0");
        reviewOrderTotalLabel = new JLabel("Order Total: $0");
        reviewMinimumDepositLabel = new JLabel("Minimum Deposit Required: $0");
        reviewLineModel = new DefaultListModel<>();
        customerInfoPanel = new CustomerInfoPanel();
        customerSummaryLabel = new JLabel("Customer details will appear here before payment.");
        paymentMinimumDepositLabel = new JLabel("Minimum Deposit Required: $0");
        depositOverrideNoticeLabel = new JLabel(" ");
        depositOverrideReasonField = new JTextField();
        paymentMethodGroup = new ButtonGroup();
        cashPaymentButton = createPaymentMethodButton("Cash", "CASH", handler);
        cardPaymentButton = createPaymentMethodButton("Card", "CARD", handler);
        chequePaymentButton = createPaymentMethodButton("Cheque", "CHEQUE", handler);
        mmgPaymentButton = createPaymentMethodButton("MMG", "MMG", handler);
        accountPaymentButton = createPaymentMethodButton("Account", "ACCOUNT", handler);
        paymentReferenceField = new JTextField();
        upfrontPaymentField = new JTextField("0", 10);
        balanceDueLabel = new JLabel("Balance Due: $0");
        styleFormControls();

        stepCards.add(buildLinesStep(handler, itemLookupButton), "Lines");
        stepCards.add(buildReviewStep(), "Review");
        stepCards.add(buildCustomerStep(), "Customer");
        stepCards.add(buildPaymentStep(handler), "Payment");

        add(buildStepper(handler), BorderLayout.NORTH);
        add(stepCards, BorderLayout.CENTER);
        add(buildNavigation(handler), BorderLayout.SOUTH);

        wire(handler, itemLookupButton);
        updateStepButtons();
    }

    private JPanel buildLinesStep(Handler handler, JButton itemLookupButton) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(BG);
        panel.add(buildSummaryTiles(), BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                new EmptyBorder(8, 8, 8, 8)
        ));
        GridBagConstraints gbc = formGbc();
        JButton addPlacementButton = new JButton("Add Placement");
        JButton printSheetButton = new JButton("Print Add-ons...");
        JButton discountButton = new JButton("Discount...");
        JButton removeLineButton = new JButton("Delete Line");
        styleButton(itemLookupButton, ACCENT_DARK);
        styleButton(addPlacementButton, ACCENT_DARK);
        styleButton(printSheetButton, PANEL_ALT);
        styleButton(addLineButton, TEAL);
        styleButton(discountButton, AMBER);
        styleButton(removeLineButton, DANGER);

        JPanel leftColumn = new JPanel(new GridBagLayout());
        leftColumn.setOpaque(false);
        JPanel rightColumn = new JPanel(new GridBagLayout());
        rightColumn.setOpaque(false);
        GridBagConstraints leftGbc = formGbc();
        GridBagConstraints rightGbc = formGbc();

        JPanel topFields = new JPanel(new GridBagLayout());
        topFields.setOpaque(false);
        GridBagConstraints topGbc = formGbc();
        addField(topFields, topGbc, 0, "Search / Scan:", buildLookupPanel(itemLookupButton));
        addField(topFields, topGbc, 1, "Item:", orderItemBox);
        addField(topFields, topGbc, 2, "Variant:", variantBox);

        addField(leftColumn, leftGbc, 0, "Price / Rate:", buildPriceRatePanel());
        addField(leftColumn, leftGbc, 1, "Price Reason:", priceOverrideReasonField);
        addTrackedField(areaLineComponents, leftColumn, leftGbc, 2, "Width:", widthField);
        addTrackedField(areaLineComponents, leftColumn, leftGbc, 3, "Length:", lengthField);
        leftGbc.gridx = 1;
        leftGbc.gridy = 4;
        leftColumn.add(areaCalculationLabel, leftGbc);
        areaLineComponents.add(areaCalculationLabel);
        addColumnBottomGlue(leftColumn, leftGbc, 5);

        addField(rightColumn, rightGbc, 0, "Design Placement:", buildDesignPlacementPanel(addPlacementButton));
        JScrollPane notesScroll = new JScrollPane(lineNotesArea);
        notesScroll.setPreferredSize(new Dimension(360, 96));
        addField(rightColumn, rightGbc, 1, "Line Notes:", notesScroll);
        addField(rightColumn, rightGbc, 2, "Quantity:", lineQuantityField);
        JPanel lineButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lineButtons.setOpaque(false);
        lineButtons.add(printSheetButton);
        lineButtons.add(addLineButton);
        lineButtons.add(discountButton);
        lineButtons.add(removeLineButton);
        JLabel quantityNote = new JLabel("Each quantity is added as a separate line.");
        quantityNote.setForeground(MUTED);
        lineButtons.add(quantityNote);
        rightGbc.gridx = 1;
        rightGbc.gridy = 3;
        rightColumn.add(lineButtons, rightGbc);
        rightGbc.gridy = 4;
        rightGbc.insets = new Insets(6, 4, 4, 4);
        rightColumn.add(printAddonSummaryLabel, rightGbc);
        addColumnBottomGlue(rightColumn, rightGbc, 5);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        form.add(topFields, gbc);
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.48;
        gbc.weighty = 0;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.fill = GridBagConstraints.BOTH;
        form.add(leftColumn, gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.52;
        form.add(rightColumn, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        form.add(Box.createVerticalGlue(), gbc);
        gbc.gridwidth = 1;
        gbc.weighty = 0;

        JPanel hiddenPrintPanel = buildPrintAddonsPanel(new JPanel());
        hiddenPrintPanel.setVisible(false);
        form.add(hiddenPrintPanel, gbc);

        JScrollPane cartScroll = new JScrollPane(orderLineTable);
        cartScroll.setPreferredSize(new Dimension(0, 210));
        cartScroll.getViewport().setBackground(PANEL);
        cartScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        JPanel cartPanel = new JPanel(new BorderLayout(4, 4));
        cartPanel.setBackground(BG);
        cartPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER),
                "Cart Lines",
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                getFont().deriveFont(Font.BOLD),
                TEXT
        ));
        cartPanel.add(cartScroll, BorderLayout.CENTER);

        panel.add(new JScrollPane(form), BorderLayout.CENTER);
        panel.add(cartPanel, BorderLayout.SOUTH);

        addPlacementButton.addActionListener(e -> handler.addPlacement());
        printSheetButton.addActionListener(e -> openPrintAddonSheet(handler));
        addLineButton.addActionListener(e -> handler.addOrderLine());
        discountButton.addActionListener(e -> handler.editLineDiscount());
        removeLineButton.addActionListener(e -> handler.removeOrderLine());
        return panel;
    }

    private JPanel buildReviewStep() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(BG);
        JPanel header = new JPanel(new GridLayout(1, 3, 10, 0));
        header.setOpaque(false);
        styleInfoLabel(reviewLineCountLabel, TEAL);
        styleInfoLabel(reviewOrderTotalLabel, ACCENT);
        styleInfoLabel(reviewMinimumDepositLabel, AMBER);
        header.add(reviewLineCountLabel);
        header.add(reviewOrderTotalLabel);
        header.add(reviewMinimumDepositLabel);
        panel.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(10, 10));
        body.setBackground(PANEL);
        body.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(10, 10, 10, 10)));
        JPanel notes = new JPanel(new BorderLayout(8, 8));
        notes.setOpaque(false);
        JLabel reviewHint = new JLabel("Customer details come next. Confirm the order details first.");
        reviewHint.setForeground(MUTED);
        notes.add(reviewHint, BorderLayout.NORTH);
        JPanel dateLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        dateLine.setOpaque(false);
        dateLine.add(dueDateEnabledBox);
        dateLine.add(dueDateField);
        notes.add(dateLine, BorderLayout.CENTER);
        JPanel orderNotes = new JPanel(new BorderLayout());
        orderNotes.setOpaque(false);
        orderNotes.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER), "Order Notes", javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, getFont().deriveFont(Font.BOLD), TEXT));
        orderNotes.add(new JScrollPane(orderNotesArea), BorderLayout.CENTER);
        notes.add(orderNotes, BorderLayout.SOUTH);
        body.add(notes, BorderLayout.NORTH);
        JList<String> reviewList = new JList<>(reviewLineModel);
        reviewList.setBackground(FIELD_BG);
        reviewList.setForeground(TEXT);
        reviewList.setSelectionBackground(ACCENT_DARK);
        reviewList.setSelectionForeground(Color.WHITE);
        reviewList.setFixedCellHeight(-1);
        body.add(new JScrollPane(reviewList), BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCustomerStep() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(BG);
        JPanel text = new JPanel(new GridLayout(2, 1, 0, 4));
        text.setBackground(PANEL);
        text.setBorder(new EmptyBorder(10, 12, 10, 12));
        JLabel attachLabel = new JLabel("Attach an existing customer or enter a new customer now.");
        JLabel createLabel = new JLabel("New customer accounts are created automatically when the order is saved.");
        attachLabel.setForeground(TEXT);
        createLabel.setForeground(MUTED);
        text.add(attachLabel);
        text.add(createLabel);
        panel.add(text, BorderLayout.NORTH);
        JPanel framed = new JPanel(new BorderLayout());
        framed.setBackground(PANEL);
        framed.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER), "Customer", javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, getFont().deriveFont(Font.BOLD), TEXT));
        framed.add(customerInfoPanel, BorderLayout.NORTH);
        panel.add(framed, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildPaymentStep(Handler handler) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBackground(BG);
        JPanel summary = new JPanel(new GridLayout(4, 1, 0, 6));
        summary.setBackground(PANEL);
        summary.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(10, 12, 10, 12)));
        summary.add(customerSummaryLabel);
        summary.add(paymentMinimumDepositLabel);
        summary.add(depositOverrideNoticeLabel);
        summary.add(balanceDueLabel);

        JPanel payment = new JPanel(new GridBagLayout());
        payment.setBackground(PANEL);
        payment.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(10, 10, 10, 10)));
        GridBagConstraints gbc = formGbc();
        JPanel methods = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        methods.setOpaque(false);
        methods.add(cashPaymentButton);
        methods.add(cardPaymentButton);
        methods.add(chequePaymentButton);
        methods.add(mmgPaymentButton);
        methods.add(accountPaymentButton);
        addField(payment, gbc, 0, "Payment Method:", methods);
        addField(payment, gbc, 1, "Upfront Amount:", upfrontPaymentField);
        addField(payment, gbc, 2, "Reference:", paymentReferenceField);
        addField(payment, gbc, 3, "Deposit Override Reason:", depositOverrideReasonField);

        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);
        center.add(summary, BorderLayout.NORTH);
        center.add(payment, BorderLayout.CENTER);
        panel.add(center, BorderLayout.CENTER);

        upfrontPaymentField.getDocument().addDocumentListener(simpleDocumentListener(handler.upfrontChanged()));
        return panel;
    }

    private JPanel buildStepper(Handler handler) {
        JPanel panel = new JPanel(new GridLayout(1, 4, 8, 0));
        panel.setBackground(BG);
        String[] names = {"Lines", "Review", "Customer", "Payment"};
        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < names.length; i++) {
            int step = i;
            JToggleButton button = new JToggleButton((i + 1) + ". " + names[i]);
            button.setFocusPainted(false);
            button.setOpaque(true);
            button.setBorder(new EmptyBorder(8, 12, 8, 12));
            button.addActionListener(e -> moveToStep(step, handler));
            group.add(button);
            stepButtons.add(button);
            panel.add(button);
        }
        return panel;
    }

    private JPanel buildNavigation(Handler handler) {
        JButton previousButton = new JButton("Back");
        JButton nextButton = new JButton("Next");
        saveOrderButton = new JButton("Create Custom Order");
        saveAndPrintOrderButton = new JButton("Create & Print Order Slip");
        JButton clearOrderButton = new JButton("Clear Order");
        styleButton(previousButton, PANEL_ALT);
        styleButton(nextButton, ACCENT_DARK);
        styleButton(saveOrderButton, TEAL);
        styleButton(saveAndPrintOrderButton, TEAL);
        styleButton(clearOrderButton, PANEL_ALT);
        previousButton.addActionListener(e -> moveToStep(currentStep - 1, handler));
        nextButton.addActionListener(e -> moveToStep(currentStep + 1, handler));
        saveOrderButton.addActionListener(e -> handler.saveOrder(false));
        saveAndPrintOrderButton.addActionListener(e -> handler.saveOrder(true));
        saveOrderButton.setVisible(false);
        saveAndPrintOrderButton.setVisible(false);
        clearOrderButton.addActionListener(e -> handler.clearOrder());

        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(4, 0, 0, 0));
        panel.add(clearOrderButton);
        panel.add(previousButton);
        panel.add(nextButton);
        panel.add(saveOrderButton);
        panel.add(saveAndPrintOrderButton);
        return panel;
    }

    private void wire(Handler handler, JButton itemLookupButton) {
        orderItemBox.addActionListener(e -> handler.orderItemChanged());
        itemLookupField.addActionListener(e -> handler.orderLookup());
        itemLookupButton.addActionListener(e -> handler.orderLookup());
        variantBox.addActionListener(e -> handler.variantChanged());
        printMaterialBox.addActionListener(e -> handler.printMaterialChanged());
        printSizePresetBox.addActionListener(e -> handler.printPresetChanged());
        printLineCountField.getDocument().addDocumentListener(simpleDocumentListener(handler.printLineCountChanged()));
        linePriceField.getDocument().addDocumentListener(simpleDocumentListener(handler.areaChanged()));
        widthField.getDocument().addDocumentListener(simpleDocumentListener(handler.areaChanged()));
        lengthField.getDocument().addDocumentListener(simpleDocumentListener(handler.areaChanged()));
        orderLineTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                handler.cartSelectionChanged();
            }
        });
    }

    private void moveToStep(int targetStep, Handler handler) {
        if (targetStep < 0 || targetStep > 3 || targetStep == currentStep) {
            updateStepButtons();
            return;
        }
        if (handler != null && targetStep > currentStep && !handler.canLeaveStep(currentStep)) {
            updateStepButtons();
            return;
        }
        if (handler != null) {
            handler.enterStep(targetStep);
        }
        currentStep = targetStep;
        stepLayout.show(stepCards, switch (currentStep) {
            case 1 -> "Review";
            case 2 -> "Customer";
            case 3 -> "Payment";
            default -> "Lines";
        });
        updateStepButtons();
    }

    void showLinesStep() {
        currentStep = 0;
        stepLayout.show(stepCards, "Lines");
        updateStepButtons();
    }

    private void updateStepButtons() {
        for (int i = 0; i < stepButtons.size(); i++) {
            JToggleButton button = stepButtons.get(i);
            boolean selected = i == currentStep;
            button.setSelected(selected);
            button.setBackground(selected ? ACCENT : PANEL_ALT);
            button.setForeground(selected ? Color.WHITE : MUTED);
            button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(selected ? new Color(147, 197, 253) : BORDER, selected ? 2 : 1),
                    new EmptyBorder(selected ? 7 : 8, 12, selected ? 7 : 8, 12)
            ));
        }
        if (saveOrderButton != null) {
            saveOrderButton.setVisible(currentStep == 3 && !alwaysPrintOrderSlip);
        }
        if (saveAndPrintOrderButton != null) {
            saveAndPrintOrderButton.setVisible(currentStep == 3);
        }
    }

    void setAlwaysPrintOrderSlip(boolean required) {
        alwaysPrintOrderSlip = required;
        updateStepButtons();
    }

    private void openPrintAddonSheet(Handler handler) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = owner instanceof Frame frame
                ? new JDialog(frame, "Print Add-ons", true)
                : new JDialog((Frame) null, "Print Add-ons", true);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setSize(620, 430);
        dialog.setLocationRelativeTo(this);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(PANEL);
        form.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = formGbc();
        JButton addPrintAddonButton = new JButton("Add Print Add-on");
        JButton removePrintAddonButton = new JButton("Remove Selected");
        styleButton(addPrintAddonButton, TEAL);
        styleButton(removePrintAddonButton, DANGER);
        addField(form, gbc, 0, "Material:", printMaterialBox);
        addTrackedField(printAddOnComponents, form, gbc, 1, "Size Preset:", printSizePresetBox);
        addTrackedField(printAddOnComponents, form, gbc, 2, "Price:", printChargeField);
        addTrackedField(printLineComponents, form, gbc, 3, "Print Lines:", printLineCountField);
        printAddOnComponents.addAll(printLineComponents);
        addTrackedField(printAddOnComponents, form, gbc, 4, "Description:", printDescriptionField);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(addPrintAddonButton);
        buttons.add(removePrintAddonButton);
        JPanel addOnsPanel = buildPrintAddonsPanel(buttons);
        JLabel addOnsLabel = addField(form, gbc, 5, "Add-ons:", addOnsPanel);
        printAddOnComponents.add(addOnsLabel);
        printAddOnComponents.add(addOnsPanel);
        gbc.gridx = 1;
        gbc.gridy = 5;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        ((GridBagLayout) form.getLayout()).setConstraints(addOnsPanel, gbc);

        JButton doneButton = new JButton("Done");
        styleButton(doneButton, ACCENT_DARK);
        doneButton.addActionListener(e -> dialog.dispose());
        addPrintAddonButton.addActionListener(e -> handler.addPrintAddon());
        removePrintAddonButton.addActionListener(e -> handler.removePrintAddon());
        dialog.add(form, BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        footer.setBackground(BG);
        footer.add(doneButton);
        dialog.add(footer, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    private JLabel summaryTile(String title, String value, Color accent) {
        JLabel label = new JLabel("<html><b>" + title + "</b><br>" + value + "</html>", SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(PANEL);
        label.setForeground(TEXT);
        label.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, accent), new EmptyBorder(10, 10, 10, 10)));
        return label;
    }

    private JPanel buildSummaryTiles() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 10, 0));
        panel.setBackground(BG);
        panel.add(lineCountLabel);
        panel.add(orderTotalLabel);
        panel.add(minimumDepositLabel);
        return panel;
    }

    private JToggleButton createPaymentMethodButton(String label, String method, Handler handler) {
        JToggleButton button = new JToggleButton(label);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBackground(PANEL_ALT);
        button.setForeground(TEXT);
        button.setBorder(BorderFactory.createLineBorder(BORDER));
        button.setPreferredSize(new Dimension("ACCOUNT".equals(method) ? 110 : 92, 38));
        button.setActionCommand(method);
        button.addItemListener(e -> {
            boolean selected = button.isSelected();
            button.setBackground(selected ? ACCENT_DARK : PANEL_ALT);
            button.setForeground(selected ? Color.WHITE : TEXT);
            button.setBorder(BorderFactory.createLineBorder(selected ? ACCENT : BORDER, selected ? 2 : 1));
            if (selected) {
                handler.selectPaymentMethod(method);
            }
        });
        paymentMethodGroup.add(button);
        return button;
    }

    private JPanel buildDesignPlacementPanel(JButton addPlacementButton) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.add(designPlacementBox, BorderLayout.WEST);
        panel.add(designPlacementField, BorderLayout.CENTER);
        panel.add(addPlacementButton, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildLookupPanel(JButton lookupButton) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        panel.add(itemLookupField, BorderLayout.CENTER);
        panel.add(lookupButton, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildPriceRatePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setOpaque(false);
        panel.add(linePriceField);
        priceRateUnitLabel.setForeground(MUTED);
        panel.add(priceRateUnitLabel);
        return panel;
    }

    private JPanel buildPrintAddonsPanel(JPanel buttons) {
        if (printAddonModel == null) {
            printAddonModel = new DefaultTableModel(new Object[]{"Material ID", "Material", "Preset ID", "Size", "Pricing", "Description", "Lines", "Price"}, 0) {
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            printAddonTable = new JTable(printAddonModel);
            printAddonTable.setRowHeight(24);
            printAddonTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            printAddonTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
            styleTable(printAddonTable);
            hideColumn(printAddonTable, 0);
            hideColumn(printAddonTable, 2);
            hideColumn(printAddonTable, 4);
            printAddonTable.getColumn("Material").setPreferredWidth(125);
            printAddonTable.getColumn("Size").setPreferredWidth(105);
            printAddonTable.getColumn("Description").setPreferredWidth(185);
            printAddonTable.getColumn("Lines").setPreferredWidth(50);
            printAddonTable.getColumn("Price").setPreferredWidth(70);
        }

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(printAddonTable);
        scrollPane.setPreferredSize(new Dimension(440, 140));
        scrollPane.setMinimumSize(new Dimension(320, 110));
        scrollPane.getViewport().setBackground(PANEL);
        scrollPane.setBorder(BorderFactory.createLineBorder(BORDER));
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
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel labelComponent = new JLabel(label);
        labelComponent.setForeground(TEXT);
        panel.add(labelComponent, gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = field instanceof JScrollPane ? GridBagConstraints.BOTH : GridBagConstraints.HORIZONTAL;
        panel.add(field, gbc);
        return labelComponent;
    }

    private void styleFormControls() {
        styleInput(itemLookupField);
        styleInput(linePriceField);
        styleInput(printChargeField);
        styleInput(printLineCountField);
        styleInput(printDescriptionField);
        styleInput(lineQuantityField);
        styleInput(lineDiscountPercentField);
        styleInput(lineDiscountReasonField);
        styleInput(priceOverrideReasonField);
        styleInput(widthField);
        styleInput(lengthField);
        styleInput(designPlacementField);
        styleInput(orderNotesArea);
        styleInput(lineNotesArea);
        styleInput(paymentReferenceField);
        styleInput(upfrontPaymentField);
        styleInput(depositOverrideReasonField);
        setFixedWidth(linePriceField, 120);
        setFixedWidth(widthField, 110);
        setFixedWidth(lengthField, 110);
        setFixedWidth(lineQuantityField, 72);
        setFixedWidth(upfrontPaymentField, 120);
        setFixedWidth(printChargeField, 120);
        setFixedWidth(printLineCountField, 72);
        styleCombo(orderItemBox);
        styleCombo(variantBox);
        styleCombo(printMaterialBox);
        styleCombo(printSizePresetBox);
        styleCombo(designPlacementBox);
        dueDateEnabledBox.setForeground(TEXT);
        dueDateEnabledBox.setBackground(PANEL);
        areaCalculationLabel.setForeground(MUTED);
        priceRateUnitLabel.setForeground(MUTED);
        customerSummaryLabel.setForeground(TEXT);
        paymentMinimumDepositLabel.setForeground(MUTED);
        depositOverrideNoticeLabel.setForeground(AMBER);
        balanceDueLabel.setForeground(TEXT);
    }

    private void styleInput(JComponent component) {
        component.setBackground(FIELD_BG);
        component.setForeground(TEXT);
        component.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER), new EmptyBorder(3, 6, 3, 6)));
        if (component instanceof JTextArea textArea) {
            textArea.setCaretColor(TEXT);
        } else if (component instanceof JTextField textField) {
            textField.setCaretColor(TEXT);
        }
    }

    private void styleCombo(JComboBox<?> comboBox) {
        comboBox.setUI(new BasicComboBoxUI());
        comboBox.setBackground(FIELD_BG);
        comboBox.setForeground(TEXT);
        comboBox.setOpaque(true);
        comboBox.setBorder(BorderFactory.createLineBorder(BORDER));
        comboBox.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                Component component = super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                component.setBackground(isSelected ? ACCENT_DARK : FIELD_BG);
                component.setForeground(isSelected ? Color.WHITE : TEXT);
                if (component instanceof JComponent rendered) {
                    rendered.setBorder(new EmptyBorder(3, 6, 3, 6));
                    rendered.setOpaque(true);
                }
                list.setBackground(FIELD_BG);
                list.setForeground(TEXT);
                list.setSelectionBackground(ACCENT_DARK);
                list.setSelectionForeground(Color.WHITE);
                return component;
            }
        });
    }

    private void setFixedWidth(JComponent component, int width) {
        Dimension preferred = component.getPreferredSize();
        Dimension size = new Dimension(width, Math.max(preferred.height, 26));
        component.setPreferredSize(size);
        component.setMaximumSize(size);
    }

    private void styleButton(AbstractButton button, Color color) {
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBackground(color);
        button.setForeground(Color.WHITE);
        ThemeManager.ensureReadableButtonColors(button);
        button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(color.brighter()), new EmptyBorder(5, 14, 5, 14)));
    }

    private void styleTable(JTable table) {
        table.setBackground(PANEL);
        table.setForeground(TEXT);
        table.setGridColor(BORDER);
        table.setSelectionBackground(ACCENT_DARK);
        table.setSelectionForeground(Color.WHITE);
        table.getTableHeader().setBackground(PANEL_ALT);
        table.getTableHeader().setForeground(TEXT);
        table.getTableHeader().setBorder(BorderFactory.createLineBorder(BORDER));
    }

    private void styleInfoLabel(JLabel label, Color accent) {
        label.setOpaque(true);
        label.setBackground(PANEL);
        label.setForeground(TEXT);
        label.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 3, 0, accent), new EmptyBorder(10, 10, 10, 10)));
    }

    private void addTrackedField(List<JComponent> trackedComponents, JPanel panel, GridBagConstraints gbc, int row, String label, JComponent field) {
        trackedComponents.add(addField(panel, gbc, row, label, field));
        trackedComponents.add(field);
    }

    private void addColumnBottomGlue(JPanel panel, GridBagConstraints gbc, int row) {
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
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private javax.swing.event.DocumentListener simpleDocumentListener(Runnable callback) {
        return new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { callback.run(); }
        };
    }

    interface Handler {
        void orderItemChanged();
        void orderLookup();
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
        void editLineDiscount();
        void cartSelectionChanged();
        void selectPaymentMethod(String method);
        Runnable upfrontChanged();
        boolean canLeaveStep(int step);
        void enterStep(int step);
        void saveOrder(boolean printOrderSlip);
        void clearOrder();
    }
}
