package ui.screens;

import Receipt.ReceiptBuilder;
import Receipt.ReceiptData;
import managers.CompanyCustomizationManager;
import managers.PermissionManager;
import managers.ReceiptNumberManager;
import managers.SessionManager;
import data.DB;
import models.CashDrawerContext;
import services.CashDrawerService;
import services.CustomerAccountLedgerService;
import services.DeviceContextService;
import services.ManagerApprovalService;
import services.SaleAuditService;
import services.SyncOutboxService;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;
import ui.components.AppMenuBar;
import ui.design.DeckersLogoManager;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;


import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;


public class MakeASale extends JFrame {
    private static final int CART_COL_ID = 0;
    private static final int CART_COL_NAME = 1;
    private static final int CART_COL_DESCRIPTION = 2;
    private static final int CART_COL_SKU = 3;
    private static final int CART_COL_PRICE = 4;
    private static final int CART_COL_QTY = 5;
    private static final int CART_COL_ITEM_DISCOUNT = 6;
    private static final int CART_COL_LINE_TOTAL = 7;
    private static final int CART_COL_ORIGINAL_PRICE = 8;
    private static final int CART_COL_PRODUCT_TYPE = 9;
    private static final int CART_COL_DEPARTMENT_ID = 10;
    private static final String APPLY_SALE_DISCOUNT_PERMISSION = "APPLY_SALE_DISCOUNT";
    private static final String CHANGE_SALE_ITEM_PRICE_PERMISSION = "CHANGE_SALE_ITEM_PRICE";
    private static final String SALE_DISCOUNT_OVERRIDE_PERMISSION = "SALE_DISCOUNT_OVERRIDE";
    private static final String MAKE_SALE_PERMISSION = "MAKE_SALE";
    private static final String HOLD_CART_PERMISSION = "MAKE_SALE";
    private static final String RESUME_HOLD_PERMISSION = "MAKE_SALE";
    private static final int SEARCH_CONTROL_HEIGHT = 28;

    private JTextField searchField;
    private JTable cartTable;
    private DefaultTableModel cartModel;
    private boolean updatingCart = false;
    private JLabel totalLabel;
    private JLabel subtotalLabel;
    private JLabel discountAmountLabel;
    private JLabel vatAmountLabel;
    private JTextField discountPercentField;
    private ButtonGroup paymentMethodGroup;
    private JToggleButton cashPaymentButton;
    private JToggleButton cardPaymentButton;
    private JToggleButton chequePaymentButton;
    private JToggleButton mmgPaymentButton;
    private JToggleButton accountPaymentButton;
    private JTextField paymentReferenceField;
    private String selectedPaymentMethod;
    private JComboBox<CustomerAccountOption> customerAccountBox;
    private JButton addCustomerAccountButton;
    private JButton checkoutBtn;
    private JButton checkoutPrintBtn;
    private JButton holdCartBtn;
    private JButton resumeHeldCartBtn;
    private JLabel overrideStatusLabel;
    private JLabel selectedStoreLabel;
    private JLabel currentUserLabel;
    private JLabel companyNameLabel;
    private JLabel screenTitleLabel;
    private JLabel companyLogoLabel;
    private JLabel appLogoLabel;
    private JButton productDropdownButton;
    private JButton editItemBtn;
    private JButton newItemBtn;
    private JLabel currentDateLabel;
    private JLabel currentTimeLabel;
    private String lastShownDate;
    private JPopupMenu searchPopup;
    private JTable searchResultsTable;
    private JScrollPane searchResultsScrollPane;
    private javax.swing.Timer searchDebounceTimer;
    private SwingWorker<java.util.List<Object[]>, Void> searchWorker;
    private SwingWorker<java.util.List<Object[]>, Void> productCacheWorker;
    private long latestSearchRequestId = 0L;
    private volatile java.util.List<Object[]> productSearchCache = java.util.Collections.emptyList();
    private int cachedProductLocationId = -1;
    private boolean suppressDiscountFieldEvents = false;
    private record PendingPriceApproval(BigDecimal approvedPrice, ManagerApprovalService.ApprovalResult approval) {}
    private record PendingDiscountApproval(BigDecimal approvedDiscountPercent, ManagerApprovalService.ApprovalResult approval) {}
    private record VatCalculation(BigDecimal amount, BigDecimal ratePercent, String mode) {
        private VatCalculation {
            amount = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
            ratePercent = ratePercent == null ? BigDecimal.ZERO : ratePercent.setScale(2, RoundingMode.HALF_UP);
            mode = mode == null ? "" : mode;
        }
    }
    private final java.util.Map<Integer, PendingPriceApproval> pendingPriceOverrideApprovals = new java.util.HashMap<>();
    private final java.util.Map<Integer, PendingDiscountApproval> pendingItemDiscountApprovals = new java.util.HashMap<>();
    private java.util.List<CustomerAccountOption> customerAccountOptions = new java.util.ArrayList<>();
    private boolean updatingCustomerAccountFilter = false;

   public MakeASale() {

       //Window Setup
       setTitle("Make a Sale");
      // setDefaultCloseOperation(DISPOSE_ON_CLOSE);
      // setExtendedState(JFrame.MAXIMIZED_BOTH);
       setSize(1000, 600);
       setLocationRelativeTo(null);
       setDefaultCloseOperation(DISPOSE_ON_CLOSE);
       setJMenuBar(AppMenuBar.create(this, "MakeASale"));

       // Main container
       JPanel panel = new JPanel(new BorderLayout(16, 16));
       panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
       panel.setBackground(DeckersPalette.background());

       // Search and register header
       JPanel searchPanel = new JPanel(new BorderLayout(0, 14));
       searchPanel.setOpaque(false);

       companyLogoLabel = new JLabel("Logo", SwingConstants.CENTER);
       companyLogoLabel.setOpaque(false);
       companyLogoLabel.setForeground(DeckersPalette.muted());
       companyLogoLabel.setBorder(BorderFactory.createEmptyBorder());
       companyLogoLabel.setPreferredSize(new Dimension(320, 104));

       companyNameLabel = new JLabel("SmartStock");
       companyNameLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
       companyNameLabel.setForeground(DeckersPalette.text());
       companyNameLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

       screenTitleLabel = new JLabel("Point of Sale");
       screenTitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
       screenTitleLabel.setForeground(DeckersPalette.muted());
       screenTitleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

       JPanel brandTextPanel = new JPanel();
       brandTextPanel.setOpaque(false);
       brandTextPanel.setLayout(new BoxLayout(brandTextPanel, BoxLayout.Y_AXIS));
       brandTextPanel.add(companyNameLabel);
       brandTextPanel.add(Box.createVerticalStrut(4));
       brandTextPanel.add(screenTitleLabel);

       JPanel brandPanel = new JPanel(new BorderLayout(14, 0));
       brandPanel.setOpaque(false);
       brandPanel.add(companyLogoLabel, BorderLayout.WEST);
       brandPanel.add(brandTextPanel, BorderLayout.CENTER);

       appLogoLabel = new JLabel("SmartStock", SwingConstants.CENTER);
       appLogoLabel.setForeground(DeckersPalette.muted());
       appLogoLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
       appLogoLabel.setPreferredSize(new Dimension(210, 96));
       setSmartStockAppLogo();

       newItemBtn = createUtilityButton("New Item", DeckersPalette.LIME);
       searchField = new PromptTextField("Scan or enter item information");
       DeckersSwing.styleField(searchField);
       setFixedControlHeight(searchField, 0);
       searchField.putClientProperty("JTextField.placeholderText", "Scan or enter item information");
       productDropdownButton = createProductDropdownButton();
       selectedStoreLabel = createMetaLabel("Store: Not selected");
       currentUserLabel = createMetaLabel("No User currently logged in");
       editItemBtn = createUtilityButton("Edit Item", DeckersPalette.PURPLE);
       currentDateLabel = createMetaLabel("No date yet");
       currentTimeLabel = createMetaLabel("No time yet");

       JPanel rightSidePanel = new JPanel();
       rightSidePanel.setOpaque(false);
       rightSidePanel.setLayout(new BoxLayout(rightSidePanel, BoxLayout.Y_AXIS));
       selectedStoreLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
       currentUserLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
       currentDateLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
       currentTimeLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

       updateCurrentDateLabel();
       updateCurrentTimeLabel();
       updateSalesGreeting();
       startDateRefreshTimer();

       rightSidePanel.add(currentDateLabel);
       rightSidePanel.add(Box.createVerticalStrut(5));
       rightSidePanel.add(currentTimeLabel);
       rightSidePanel.add(Box.createVerticalStrut(8));
       rightSidePanel.add(selectedStoreLabel);
       rightSidePanel.add(Box.createVerticalStrut(8));
       rightSidePanel.add(currentUserLabel);


       JPanel leftSidePanel = new JPanel(new GridLayout(0, 1, 0, 8));
       leftSidePanel.setOpaque(false);
       leftSidePanel.add(newItemBtn);
       leftSidePanel.add(editItemBtn);

       JPanel centerSection = new JPanel(new BorderLayout(20, 0));
       centerSection.setOpaque(true);
       DeckersSwing.styleBand(centerSection, DeckersPalette.ORANGE, new Insets(16, 16, 16, 16));
       centerSection.add(brandPanel, BorderLayout.CENTER);
       centerSection.add(leftSidePanel, BorderLayout.WEST);
       JPanel rightHeaderPanel = new JPanel(new BorderLayout(14, 0));
       rightHeaderPanel.setOpaque(false);
       rightHeaderPanel.add(appLogoLabel, BorderLayout.WEST);
       rightHeaderPanel.add(rightSidePanel, BorderLayout.EAST);
       centerSection.add(rightHeaderPanel, BorderLayout.EAST);

        // Search row (THIS is the important part)
       JPanel searchRow = new JPanel(new BorderLayout(12, 0));
       searchRow.setOpaque(true);
       DeckersSwing.styleBand(searchRow, DeckersPalette.MAGENTA, new Insets(7, 14, 7, 14));
       JPanel productSearchPanel = new JPanel(new BorderLayout(0, 0));
       productSearchPanel.setOpaque(false);
       productSearchPanel.add(searchField, BorderLayout.CENTER);
       productSearchPanel.add(productDropdownButton, BorderLayout.EAST);
       searchRow.add(productSearchPanel, BorderLayout.CENTER);

       searchPanel.add(centerSection, BorderLayout.NORTH);
       searchPanel.add(searchRow, BorderLayout.SOUTH);

	       // Cart table
	       cartModel = new DefaultTableModel(
	               new Object[]{"ID", "Name", "Description", "SKU", "Price", "Qty", "Item Disc %", "Line Total", "Original Price", "Product Type", "Department ID"},
	               0
	       ) {
	           @Override
	           public boolean isCellEditable(int row, int column) {
	               return (column == CART_COL_PRICE)
	                       || column == CART_COL_QTY
	                       || (column == CART_COL_ITEM_DISCOUNT);
	           }
       };
       cartTable = new JTable(cartModel) {
           @Override
           protected void paintComponent(Graphics graphics) {
               graphics.setColor(getBackground());
               graphics.fillRect(0, 0, getWidth(), getHeight());
               super.paintComponent(graphics);
           }
       };
       cartTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
       cartTable.setFillsViewportHeight(true);
       DeckersSwing.styleTable(cartTable, DeckersPalette.LIME);
       JScrollPane cartScrollPane = new JScrollPane(cartTable);
       cartScrollPane.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
       cartScrollPane.setBorder(BorderFactory.createEmptyBorder());
       cartScrollPane.setBackground(DeckersPalette.tableBody(DeckersPalette.LIME));
       cartScrollPane.getViewport().putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
       cartScrollPane.getViewport().setBackground(DeckersPalette.tableBody(DeckersPalette.LIME));
	       cartTable.getColumnModel().getColumn(CART_COL_PRICE).setCellEditor(new DefaultCellEditor(new JTextField()));
	       cartTable.getColumnModel().getColumn(CART_COL_QTY).setCellEditor(new DefaultCellEditor(new JTextField()));
	       cartTable.getColumnModel().getColumn(CART_COL_ITEM_DISCOUNT).setCellEditor(new DefaultCellEditor(new JTextField()));
       configureCartTableColumns();

       JPanel cartSection = new JPanel(new BorderLayout());
       DeckersSwing.styleBand(cartSection, DeckersPalette.LIME, new Insets(6, 6, 6, 6));
       cartSection.add(cartScrollPane, BorderLayout.CENTER);

       panel.add(searchPanel, BorderLayout.NORTH);
       panel.add(cartSection, BorderLayout.CENTER);

       customerAccountBox = new JComboBox<>();
       customerAccountBox.setEditable(true);
       customerAccountBox.setEditor(new PromptComboBoxEditor("Enter customer name"));
       customerAccountBox.setBorder(BorderFactory.createLineBorder(DeckersPalette.border()));
       customerAccountBox.setBackground(DeckersPalette.fieldBackground());
       customerAccountBox.setForeground(DeckersPalette.text());
       customerAccountBox.setFont(new Font("SansSerif", Font.PLAIN, 14));
       customerAccountBox.setPrototypeDisplayValue(new CustomerAccountOption(0, "0000000000", "Enter customer name", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, ""));
       setFixedControlHeight(customerAccountBox, 360);
       customerAccountBox.setRenderer(new CustomerAccountRenderer());
       addCustomerAccountButton = createUtilityButton("New Customer", DeckersPalette.MAGENTA);
       paymentMethodGroup = new ButtonGroup();
       cashPaymentButton = createPaymentMethodButton("Cash", "CASH");
       cardPaymentButton = createPaymentMethodButton("Card", "CARD");
       chequePaymentButton = createPaymentMethodButton("Cheque", "CHEQUE");
       mmgPaymentButton = createPaymentMethodButton("MMG", "MMG");
       accountPaymentButton = createPaymentMethodButton("Account", "ACCOUNT");
       paymentReferenceField = new JTextField(14);
       paymentReferenceField.setToolTipText("Required for MMG transaction reference.");
       paymentReferenceField.setEnabled(false);
       DeckersSwing.styleField(paymentReferenceField);
       setFixedControlHeight(paymentReferenceField, 170);
	       discountPercentField = new JTextField("0", 5);
       DeckersSwing.styleField(discountPercentField);
	       discountPercentField.setEnabled(canApplySaleDiscount());
	       if (!canApplySaleDiscount()) {
	           discountPercentField.setToolTipText("Requires Apply Sale Discount permission.");
	       }
       setFixedControlHeight(discountPercentField, 70);

       JPanel customerControlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
       customerControlsPanel.setOpaque(false);
       customerControlsPanel.add(customerAccountBox);
       customerControlsPanel.add(addCustomerAccountButton);
       searchRow.add(customerControlsPanel, BorderLayout.EAST);

       JPanel bottomPanel = new JPanel(new BorderLayout(14, 10));
       bottomPanel.setOpaque(true);
       DeckersSwing.styleBand(bottomPanel, DeckersPalette.CORAL, new Insets(14, 14, 14, 14));

       JPanel totalsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 6));
       totalsPanel.setOpaque(false);
       JPanel transactionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
       transactionPanel.setOpaque(false);
       transactionPanel.add(cashPaymentButton);
       transactionPanel.add(cardPaymentButton);
       transactionPanel.add(chequePaymentButton);
       transactionPanel.add(mmgPaymentButton);
       transactionPanel.add(accountPaymentButton);
       transactionPanel.add(buildLabeledControl("Reference", paymentReferenceField));
       totalsPanel.add(buildLabeledControl("Discount %", discountPercentField));
	       subtotalLabel = createTotalLabel("Subtotal: $0.00", false);
	       totalsPanel.add(subtotalLabel);
	       discountAmountLabel = createTotalLabel("Discount: $0.00", false);
	       totalsPanel.add(discountAmountLabel);
	       vatAmountLabel = createTotalLabel("VAT: $0.00", false);
	       totalsPanel.add(vatAmountLabel);
	       totalLabel = createTotalLabel("Overall Total: $0.00", true);
	       totalsPanel.add(totalLabel);

       JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
       actionPanel.setOpaque(false);
       checkoutBtn = createCheckoutButton("Checkout");
       checkoutPrintBtn = createCheckoutButton("Checkout & Print");
       holdCartBtn = createActionUtilityButton("Hold Cart");
       resumeHeldCartBtn = createActionUtilityButton("Resume Hold");
       actionPanel.add(holdCartBtn);
       actionPanel.add(resumeHeldCartBtn);
       actionPanel.add(checkoutBtn);
       actionPanel.add(checkoutPrintBtn);

       overrideStatusLabel = new JLabel("No active override approvals");
       overrideStatusLabel.setForeground(DeckersPalette.muted());
       overrideStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

       JPanel bottomTopPanel = new JPanel(new BorderLayout(8, 4));
       bottomTopPanel.setOpaque(false);
       bottomTopPanel.add(transactionPanel, BorderLayout.WEST);
       bottomTopPanel.add(totalsPanel, BorderLayout.EAST);
       JPanel footerPanel = new JPanel(new BorderLayout(8, 6));
       footerPanel.setOpaque(false);
       footerPanel.add(overrideStatusLabel, BorderLayout.WEST);
       footerPanel.add(actionPanel, BorderLayout.EAST);
       bottomPanel.add(bottomTopPanel, BorderLayout.CENTER);
       bottomPanel.add(footerPanel, BorderLayout.SOUTH);

       panel.add(bottomPanel, BorderLayout.SOUTH);

       //Add panel to frame
       add(panel);
       refreshPermissionButtons();
       warmProductSearchCacheInBackground();

       //Action Listeners
       newItemBtn.addActionListener(new ActionListener() {
           public void actionPerformed(ActionEvent e) {
               if (!PermissionManager.requirePermission("NEW_ITEM", MakeASale.this, "New Item")) {
                   refreshPermissionButtons();
                   return;
               }
               if (SessionManager.getCurrentLocationId() == null) {
                   JOptionPane.showMessageDialog(MakeASale.this, "No store is selected for this session.");
                   return;
               }
               if (WindowHelper.focusIfAlreadyOpen(
                       NewItem.class)) {
                   return;
               }
               WindowHelper.showPosWindow(new NewItem(SessionManager.getCurrentLocationId()), MakeASale.this);
           }
       });
       editItemBtn.addActionListener(new ActionListener() {
           public void actionPerformed(ActionEvent e) {
               if (!PermissionManager.requirePermission("EDIT_ITEM", MakeASale.this, "Edit Item")) {
                   refreshPermissionButtons();
                   return;
               }
               if (WindowHelper.focusIfAlreadyOpen(EditItem.class)) {
                   return;
               }
               EditItem screen = new EditItem();
               WindowHelper.showPosWindow(screen, MakeASale.this);
           }
       });
       searchField.addActionListener(new ActionListener() {
           public void actionPerformed(ActionEvent e) {
               addSelectedSearchResultToCart();
           }
       });
       searchField.getDocument().addDocumentListener(new DocumentListener() {
           private void restartSearchDebounce() {
               if (searchDebounceTimer == null) {
                   searchDebounceTimer = new javax.swing.Timer(250, e -> searchProducts(false));
                   searchDebounceTimer.setRepeats(false);
               }

               searchDebounceTimer.restart();
           }

           @Override
           public void insertUpdate(DocumentEvent e) {
               SwingUtilities.invokeLater(this::restartSearchDebounce);
           }

           @Override
           public void removeUpdate(DocumentEvent e) {
               SwingUtilities.invokeLater(this::restartSearchDebounce);
           }

           @Override
           public void changedUpdate(DocumentEvent e) {
               SwingUtilities.invokeLater(this::restartSearchDebounce);
           }
       });
       searchField.addKeyListener(new java.awt.event.KeyAdapter() {
           @Override
           public void keyPressed(java.awt.event.KeyEvent e) {
               if (searchResultsTable == null || searchResultsTable.getRowCount() == 0) {
                   return;
               }

               int selectedRow = searchResultsTable.getSelectedRow();

               if (e.getKeyCode() == java.awt.event.KeyEvent.VK_DOWN) {
                   int nextRow = Math.min(selectedRow + 1, searchResultsTable.getRowCount() - 1);
                   if (nextRow >= 0) {
                       searchResultsTable.setRowSelectionInterval(nextRow, nextRow);
                       searchResultsTable.scrollRectToVisible(searchResultsTable.getCellRect(nextRow, 0, true));
                   }
                   e.consume();
               } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_UP) {
                   int nextRow = Math.max(selectedRow - 1, 0);
                   if (nextRow >= 0) {
                       searchResultsTable.setRowSelectionInterval(nextRow, nextRow);
                       searchResultsTable.scrollRectToVisible(searchResultsTable.getCellRect(nextRow, 0, true));
                   }
                   e.consume();
               } else if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE) {
                   closeSearchPopup();
               }
           }
       });
       searchField.addFocusListener(new java.awt.event.FocusAdapter() {
           @Override
           public void focusGained(java.awt.event.FocusEvent e) {
               searchProducts(false);
           }
       });
       searchField.addMouseListener(new java.awt.event.MouseAdapter() {
           @Override
           public void mouseClicked(java.awt.event.MouseEvent e) {
               searchProducts(false);
           }
       });
       productDropdownButton.addActionListener(e -> {
           searchField.requestFocusInWindow();
           searchProducts(false);
       });
       configureCustomerAccountSearch();
	       cartModel.addTableModelListener(e -> {
	           if (updatingCart) {
	               return;
	           }
               if (e.getColumn() == CART_COL_PRICE) {
                   handlePriceEditOverrideAtCart(e.getFirstRow());
               }
               if (e.getColumn() == CART_COL_ITEM_DISCOUNT) {
                   handleItemDiscountEditOverrideAtCart(e.getFirstRow());
               }
		           if (e.getColumn() == CART_COL_PRICE
		                   || e.getColumn() == CART_COL_QTY
		                   || e.getColumn() == CART_COL_ITEM_DISCOUNT
		                   || e.getColumn() == javax.swing.event.TableModelEvent.ALL_COLUMNS) {
		               updateLineTotals();
	           }
	       });
       checkoutBtn.addActionListener(new ActionListener() {
           public void actionPerformed(ActionEvent e) {
               checkout(false);
           }
       });
       checkoutPrintBtn.addActionListener(e -> checkout(true));
       holdCartBtn.addActionListener(e -> holdCurrentCart());
       resumeHeldCartBtn.addActionListener(e -> resumeHeldCart());
	       addCustomerAccountButton.addActionListener(e -> openQuickCustomerAccount());
	       discountPercentField.getDocument().addDocumentListener(new DocumentListener() {
	           private void refreshTotals() {
                   if (suppressDiscountFieldEvents) {
                       return;
                   }
	               SwingUtilities.invokeLater(() -> {
                       if (suppressDiscountFieldEvents) {
                           return;
                       }
	                   if (!canApplySaleDiscount()) {
                           String current = discountPercentField.getText() == null ? "" : discountPercentField.getText().trim();
                           if (!"0".equals(current)) {
                               setDiscountFieldValue("0");
                           }
	                   }
	                   updateOverallTotal();
	               });
	           }

	           @Override
	           public void insertUpdate(DocumentEvent e) {
	               refreshTotals();
	           }

	           @Override
	           public void removeUpdate(DocumentEvent e) {
	               refreshTotals();
	           }

	           @Override
	           public void changedUpdate(DocumentEvent e) {
	               refreshTotals();
	           }
	       });
	       addWindowFocusListener(new java.awt.event.WindowAdapter() {
           @Override
           public void windowGainedFocus(java.awt.event.WindowEvent e) {
               refreshPermissionButtons();
           }
       });
       updateSelectedStoreLabel(); //displays the current store
       updateCurrentUserLabel(); //displays the current user
	       loadCustomerAccounts();
	       updateCustomerAccountEnabled();
       loadCompanyBranding();
	       WindowHelper.showPosWindow(this); //runs last for the main UI to show
	   }

    private JButton createPrimaryButton(String text) {
        // Standard blue command button: text color, fill color, border color, and internal padding live here.
        JButton button = new RoundedFillButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setForeground(DeckersPalette.text());
        button.setBackground(DeckersPalette.tileFill(DeckersPalette.CORAL));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DeckersPalette.sectionBorder(DeckersPalette.CORAL)),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton createCheckoutButton(String text) {
        // Bottom-right checkout buttons: change red fill, text color, rounded border, padding, and size here.
        JButton button = new RoundedFillButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 16));
        button.setFocusPainted(false);
        button.setForeground(new Color(255, 255, 255));
        button.putClientProperty("Button.disabledText", DeckersPalette.muted());
        button.setBackground(DeckersPalette.CHECKOUT_RED);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(true);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setBorder(new OutsideRoundedBorder(DeckersPalette.background(), 4, 12, new Insets(12, 24, 12, 24)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setToolTipText("Select a payment method before checkout.");
        button.setPreferredSize(new Dimension("Checkout & Print".equals(text) ? 205 : 150, 56));
        return button;
    }

    private JToggleButton createPaymentMethodButton(String label, String method) {
        // Payment method buttons: base size, blue fill, text color, rounded border, and padding live here.
        JToggleButton button = new RoundedFillToggleButton(label);
        button.setFont(new Font("SansSerif", Font.BOLD, 16));
        button.setFocusPainted(false);
        button.setActionCommand(method);
        button.setForeground(DeckersPalette.text());
        button.setBackground(DeckersPalette.tileFill(paymentAccent(method)));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(new OutsideRoundedBorder(DeckersPalette.sectionBorder(paymentAccent(method)), 4, 12, new Insets(12, 22, 12, 22)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension("ACCOUNT".equals(method) ? 155 : 125, 56));
        button.setMinimumSize(button.getPreferredSize());
        button.setUI(new javax.swing.plaf.basic.BasicToggleButtonUI());
        button.addActionListener(e -> selectPaymentMethod(method));
        paymentMethodGroup.add(button);
        return button;
    }

    private void selectPaymentMethod(String method) {
        selectedPaymentMethod = method;
        if ("CASH".equals(method) && cashPaymentButton != null) {
            cashPaymentButton.setSelected(true);
        } else if ("CARD".equals(method) && cardPaymentButton != null) {
            cardPaymentButton.setSelected(true);
        } else if ("CHEQUE".equals(method) && chequePaymentButton != null) {
            chequePaymentButton.setSelected(true);
        } else if ("MMG".equals(method) && mmgPaymentButton != null) {
            mmgPaymentButton.setSelected(true);
        } else if ("ACCOUNT".equals(method) && accountPaymentButton != null) {
            accountPaymentButton.setSelected(true);
        }
        updatePaymentReferenceEnabled();
        updatePaymentButtonStyles();
        updateCheckoutAvailability();
        updateCustomerAccountEnabled();
    }

    private void updatePaymentButtonStyles() {
        stylePaymentButton(cashPaymentButton, "CASH".equals(selectedPaymentMethod));
        stylePaymentButton(cardPaymentButton, "CARD".equals(selectedPaymentMethod));
        stylePaymentButton(chequePaymentButton, "CHEQUE".equals(selectedPaymentMethod));
        stylePaymentButton(mmgPaymentButton, "MMG".equals(selectedPaymentMethod));
        stylePaymentButton(accountPaymentButton, "ACCOUNT".equals(selectedPaymentMethod));
    }

    private void updatePaymentReferenceEnabled() {
        if (paymentReferenceField == null) {
            return;
        }
        boolean enabled = "MMG".equals(selectedPaymentMethod);
        paymentReferenceField.setEnabled(enabled);
        if (!enabled) {
            paymentReferenceField.setText("");
        }
    }

    private void stylePaymentButton(JToggleButton button, boolean selected) {
        // Selected/unselected payment button colors and border thickness are controlled here.
        if (button == null) {
            return;
        }
        Color accent = selected ? DeckersPalette.LIME : DeckersPalette.ORANGE;
        button.setBackground(selected ? DeckersPalette.tilePressed(accent) : DeckersPalette.tileFill(accent));
        button.setBorder(new OutsideRoundedBorder(
                DeckersPalette.sectionBorder(accent),
                4,
                12,
                new Insets(12, 22, 12, 22)
        ));
    }

    private void updateCheckoutAvailability() {
        boolean hasPaymentMethod = selectedPaymentMethod != null && !selectedPaymentMethod.isBlank();
        if (checkoutBtn != null) {
            checkoutBtn.setToolTipText(hasPaymentMethod ? null : "Select a payment method before checkout.");
        }
        if (checkoutPrintBtn != null) {
            checkoutPrintBtn.setToolTipText(hasPaymentMethod ? null : "Select a payment method before checkout.");
        }
    }

    private Color paymentAccent(String method) {
        return DeckersPalette.ORANGE;
    }

    private JButton createUtilityButton(String text, Color accent) {
        // Small utility buttons like New Item/New Customer: text color, fill, border, and padding live here.
        JButton button = new JButton(text);
        DeckersSwing.styleUtilityButton(button, accent);
        return button;
    }

    private JButton createActionUtilityButton(String text) {
        // Bottom-right Hold/Resume buttons: larger size plus thick rounded border settings live here.
        JButton button = new RoundedFillButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 15));
        button.setFocusPainted(false);
        button.setForeground(DeckersPalette.text());
        button.setBackground(DeckersPalette.tileFill(DeckersPalette.YELLOW));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setBorder(new OutsideRoundedBorder(DeckersPalette.sectionBorder(DeckersPalette.YELLOW), 4, 12, new Insets(12, 22, 12, 22)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(150, 56));
        return button;
    }

    private JButton createProductDropdownButton() {
        // Green arrow section on the product search field: width, height, fill, text, and border live here.
        JButton button = new JButton("▼");
        button.setFont(new Font("SansSerif", Font.BOLD, 11));
        button.setForeground(DeckersPalette.text());
        button.setBackground(DeckersPalette.tilePressed(DeckersPalette.LIME));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(DeckersPalette.sectionBorder(DeckersPalette.LIME)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Dimension size = new Dimension(38, SEARCH_CONTROL_HEIGHT);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setToolTipText("Show product list");
        return button;
    }

    private JLabel createMetaLabel(String text) {
        return DeckersSwing.metaLabel(text);
    }

    private JLabel createTotalLabel(String text, boolean prominent) {
        // Totals row label sizes and colors are controlled here.
        return DeckersSwing.totalLabel(text, prominent);
    }

    private void setFixedControlHeight(JComponent component, int width) {
        // Shared control sizing for search fields, dropdowns, payment combo replacements, and discount input.
        int controlWidth = Math.max(width, 0);
        Dimension preferred = new Dimension(controlWidth, SEARCH_CONTROL_HEIGHT);
        Dimension minimum = new Dimension(controlWidth, SEARCH_CONTROL_HEIGHT);
        Dimension maximum = new Dimension(controlWidth == 0 ? Integer.MAX_VALUE : controlWidth, SEARCH_CONTROL_HEIGHT);
        component.setPreferredSize(preferred);
        component.setMinimumSize(minimum);
        component.setMaximumSize(maximum);
    }

    private static class CustomerAccountRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setFont(new Font("SansSerif", Font.PLAIN, 14));
            label.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
            label.setForeground(isSelected ? DeckersPalette.text() : DeckersPalette.text());
            label.setBackground(isSelected ? DeckersPalette.tileHover(DeckersPalette.MAGENTA) : DeckersPalette.fieldBackground());
            if (value == null) {
                label.setText("");
            }
            if (index == -1) {
                label.setPreferredSize(new Dimension(label.getPreferredSize().width, SEARCH_CONTROL_HEIGHT - 2));
            }
            return label;
        }
    }

    private static class RoundedFillButton extends JButton {
        private RoundedFillButton(String text) {
            super(text);
            putClientProperty("SmartStock.customPaintedButton", Boolean.TRUE);
            setOpaque(false);
            setContentAreaFilled(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            // Paints a rounded fill before Swing draws the button text.
            paintRoundedButtonFill(this, graphics);
            setContentAreaFilled(false);
            super.paintComponent(graphics);
        }
    }

    private static class RoundedFillToggleButton extends JToggleButton {
        private RoundedFillToggleButton(String text) {
            super(text);
            putClientProperty("SmartStock.customPaintedButton", Boolean.TRUE);
            setOpaque(false);
            setContentAreaFilled(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            // Paints a rounded fill before Swing draws the toggle button text.
            paintRoundedButtonFill(this, graphics);
            setContentAreaFilled(false);
            super.paintComponent(graphics);
        }
    }

    private static void paintRoundedButtonFill(AbstractButton button, Graphics graphics) {
        int strokeWidth = 0;
        int radius = 12;
        if (button.getBorder() instanceof OutsideRoundedBorder roundedBorder) {
            strokeWidth = roundedBorder.getThickness();
            radius = roundedBorder.getRadius();
        }

        int inset = Math.max(strokeWidth - 1, 1);
        int arc = Math.max(radius - strokeWidth, 4);
        int width = Math.max(0, button.getWidth() - (inset * 2) - 1);
        int height = Math.max(0, button.getHeight() - (inset * 2) - 1);

        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(button.getBackground());
        g2.fillRoundRect(inset, inset, width, height, arc, arc);
        g2.dispose();
    }

    private static class OutsideRoundedBorder extends AbstractBorder {
        // Reusable thick rounded border. Padding controls the content inset; thickness/radius control the outline.
        private final Color color;
        private final int thickness;
        private final int radius;
        private final Insets padding;

        private OutsideRoundedBorder(Color color, int thickness, int radius, Insets padding) {
            this.color = color;
            this.thickness = thickness;
            this.radius = radius;
            this.padding = padding;
        }

        private int getThickness() {
            return thickness;
        }

        private int getRadius() {
            return radius;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int inset = Math.max(thickness / 2, 1);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.drawRoundRect(
                    x + inset,
                    y + inset,
                    width - (inset * 2) - 1,
                    height - (inset * 2) - 1,
                    radius,
                    radius
            );
            g2.dispose();
        }

        @Override
        public Insets getBorderInsets(Component component) {
            return getBorderInsets(component, new Insets(0, 0, 0, 0));
        }

        @Override
        public Insets getBorderInsets(Component component, Insets insets) {
            insets.top = padding.top;
            insets.left = padding.left;
            insets.bottom = padding.bottom;
            insets.right = padding.right;
            return insets;
        }
    }

    private JPanel buildLabeledControl(String label, JComponent control) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(false);
        JLabel title = new JLabel(label);
        title.setFont(new Font("SansSerif", Font.BOLD, 11));
        title.setForeground(DeckersPalette.muted());
        title.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        panel.add(title, BorderLayout.NORTH);
        panel.add(control, BorderLayout.CENTER);
        return panel;
    }

    private void loadCompanyBranding() {
        if (companyNameLabel == null || companyLogoLabel == null) {
            return;
        }

        updateSalesGreeting();
        setDeckersCompanyLogo();
    }

    private void updateSalesGreeting() {
        if (companyNameLabel == null || screenTitleLabel == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(StoreTimeZoneHelper.getStoreZone());
        int hour = now.getHour();
        int variant = (now.getMinute() / 10) % 3;
        if (hour < 5) {
            companyNameLabel.setText("Late Shift Sales");
            screenTitleLabel.setText(variant == 0
                    ? "Keep checkout smooth and every order accurate."
                    : variant == 1
                    ? "Quiet hours, clean totals, steady sales."
                    : "One more careful sale at a time.");
        } else if (hour < 12) {
            companyNameLabel.setText(variant == 2 ? "Morning Momentum" : "Good Morning");
            screenTitleLabel.setText(variant == 0
                    ? "Start strong and make the first sales count."
                    : variant == 1
                    ? "Coffee loaded, scanner ready."
                    : "Fresh day, fresh carts, fresh wins.");
        } else if (hour == 12) {
            companyNameLabel.setText(variant == 0
                    ? "What's for Lunch? 🍽"
                    : variant == 1
                    ? "Lunch Time Already? 🥪"
                    : "Ready for Lunch? ☀");
            screenTitleLabel.setText(variant == 0
                    ? "Serve the lunch rush, then enjoy yours."
                    : variant == 1
                    ? "Great service first, good lunch after."
                    : "Smooth scans, happy customers, well-earned lunch.");
        } else if (hour < 15) {
            companyNameLabel.setText(variant == 1 ? "Midday Hustle" : "Midday Momentum");
            screenTitleLabel.setText(variant == 0
                    ? "Keep the line moving and the basket growing."
                    : variant == 1
                    ? "Halfway there, sales still count double in spirit."
                    : "A smooth checkout keeps the day on track.");
        } else if (hour == 16) {
            companyNameLabel.setText("Waiting for 5 PM?");
            screenTitleLabel.setText(variant == 0
                    ? "One strong final hour can still move the needle."
                    : variant == 1
                    ? "Close time is calling, but the register is too."
                    : "Finish clean, finish sharp, finish smiling.");
        } else if (hour < 18) {
            companyNameLabel.setText(variant == 2 ? "Final Stretch" : "Good Afternoon");
            screenTitleLabel.setText(variant == 0
                    ? "Finish the day strong with confident service."
                    : variant == 1
                    ? "Last stretch, best service."
                    : "Every checkout is one more good impression.");
        } else {
            companyNameLabel.setText(variant == 1 ? "Evening Sales" : "Good Evening");
            screenTitleLabel.setText(variant == 0
                    ? "Close out sales with care and a great final impression."
                    : variant == 1
                    ? "Evening pace, steady hands, clean totals."
                    : "Make the last carts feel like the first.");
        }
    }

    private void setDeckersCompanyLogo() {
        ImageIcon deckersLogoIcon = DeckersLogoManager.loadDeckersLogoIcon(getClass());
        if (deckersLogoIcon != null && deckersLogoIcon.getIconWidth() > 0) {
            Image scaled = DeckersLogoManager.scaleToFit(deckersLogoIcon.getImage(), 300, 96);
            companyLogoLabel.setText("");
            companyLogoLabel.setIcon(new ImageIcon(scaled));
            return;
        }

        companyLogoLabel.setIcon(null);
        companyLogoLabel.setText("Deckers");
        companyLogoLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
    }

    private void setSmartStockAppLogo() {
        if (appLogoLabel == null) {
            return;
        }
        ImageIcon centerLogoIcon = DeckersLogoManager.loadSmartStockLogoIcon(getClass());
        if (centerLogoIcon != null && centerLogoIcon.getIconWidth() > 0) {
            Image scaled = DeckersLogoManager.scaleToFit(centerLogoIcon.getImage(), 196, 88);
            appLogoLabel.setText("");
            appLogoLabel.setIcon(new ImageIcon(scaled));
            return;
        }

        appLogoLabel.setIcon(null);
        appLogoLabel.setText("SmartStock");
    }

    private void refreshPermissionButtons() {
        if (newItemBtn != null) {
            newItemBtn.setEnabled(PermissionManager.hasPermission("NEW_ITEM"));
        }
        if (editItemBtn != null) {
            editItemBtn.setEnabled(PermissionManager.hasPermission("EDIT_ITEM"));
        }
	        if (discountPercentField != null) {
	            discountPercentField.setEnabled(canApplySaleDiscount());
	            if (!canApplySaleDiscount()) {
                    setDiscountFieldValue("0");
	            }
	        }
	    }

    private boolean canApplySaleDiscount() {
        return PermissionManager.hasPermission(APPLY_SALE_DISCOUNT_PERMISSION);
    }

    private boolean canChangeSaleItemPrice() {
        return PermissionManager.hasPermission(CHANGE_SALE_ITEM_PRICE_PERMISSION);
    }

    private void configureCartTableColumns() {
        if (cartTable == null || cartTable.getColumnModel().getColumnCount() < 11) {
            return;
        }

        TableColumnModel columnModel = cartTable.getColumnModel();

        int idWidth = fitColumnWidth(cartTable, CART_COL_ID, 45);
        int nameWidth = fitColumnWidth(cartTable, CART_COL_NAME, 120);
        int skuWidth = fitColumnWidth(cartTable, CART_COL_SKU, 100);
        int priceWidth = fitColumnWidth(cartTable, CART_COL_PRICE, 75);
        int qtyWidth = fitColumnWidth(cartTable, CART_COL_QTY, 55);
        int itemDiscountWidth = fitColumnWidth(cartTable, CART_COL_ITEM_DISCOUNT, 90);
        int lineTotalWidth = fitColumnWidth(cartTable, CART_COL_LINE_TOTAL, 95);

        columnModel.getColumn(CART_COL_ID).setMinWidth(40);
        columnModel.getColumn(CART_COL_ID).setMaxWidth(70);
        columnModel.getColumn(CART_COL_ID).setPreferredWidth(idWidth);

        columnModel.getColumn(CART_COL_NAME).setMinWidth(90);
        columnModel.getColumn(CART_COL_NAME).setMaxWidth(200);
        columnModel.getColumn(CART_COL_NAME).setPreferredWidth(nameWidth);

        columnModel.getColumn(CART_COL_DESCRIPTION).setMinWidth(220);
        columnModel.getColumn(CART_COL_DESCRIPTION).setPreferredWidth(320);
        columnModel.getColumn(CART_COL_DESCRIPTION).setCellRenderer(new MultiLineTableCellRenderer());

        columnModel.getColumn(CART_COL_SKU).setMinWidth(90);
        columnModel.getColumn(CART_COL_SKU).setPreferredWidth(skuWidth);

        columnModel.getColumn(CART_COL_PRICE).setMinWidth(70);
        columnModel.getColumn(CART_COL_PRICE).setMaxWidth(95);
        columnModel.getColumn(CART_COL_PRICE).setPreferredWidth(priceWidth);

        columnModel.getColumn(CART_COL_QTY).setMinWidth(50);
        columnModel.getColumn(CART_COL_QTY).setMaxWidth(70);
        columnModel.getColumn(CART_COL_QTY).setPreferredWidth(qtyWidth);

        columnModel.getColumn(CART_COL_ITEM_DISCOUNT).setMinWidth(80);
        columnModel.getColumn(CART_COL_ITEM_DISCOUNT).setMaxWidth(115);
        columnModel.getColumn(CART_COL_ITEM_DISCOUNT).setPreferredWidth(itemDiscountWidth);

        columnModel.getColumn(CART_COL_LINE_TOTAL).setMinWidth(90);
        columnModel.getColumn(CART_COL_LINE_TOTAL).setMaxWidth(120);
        columnModel.getColumn(CART_COL_LINE_TOTAL).setPreferredWidth(lineTotalWidth);

        columnModel.getColumn(CART_COL_ORIGINAL_PRICE).setMinWidth(0);
        columnModel.getColumn(CART_COL_ORIGINAL_PRICE).setMaxWidth(0);
        columnModel.getColumn(CART_COL_ORIGINAL_PRICE).setPreferredWidth(0);

        columnModel.getColumn(CART_COL_PRODUCT_TYPE).setMinWidth(0);
        columnModel.getColumn(CART_COL_PRODUCT_TYPE).setMaxWidth(0);
        columnModel.getColumn(CART_COL_PRODUCT_TYPE).setPreferredWidth(0);

        columnModel.getColumn(CART_COL_DEPARTMENT_ID).setMinWidth(0);
        columnModel.getColumn(CART_COL_DEPARTMENT_ID).setMaxWidth(0);
        columnModel.getColumn(CART_COL_DEPARTMENT_ID).setPreferredWidth(0);

        updateDescriptionRowHeights();
    }

    private int fitColumnWidth(JTable table, int columnIndex, int minWidth) {
        int width = minWidth;
        TableColumnModel columnModel = table.getColumnModel();

        TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
        Component headerComponent = headerRenderer.getTableCellRendererComponent(
                table,
                columnModel.getColumn(columnIndex).getHeaderValue(),
                false,
                false,
                0,
                columnIndex
        );
        width = Math.max(width, headerComponent.getPreferredSize().width + 16);

        for (int row = 0; row < table.getRowCount(); row++) {
            TableCellRenderer renderer = table.getCellRenderer(row, columnIndex);
            Component component = table.prepareRenderer(renderer, row, columnIndex);
            width = Math.max(width, component.getPreferredSize().width + 16);
        }

        return width;
    }

    private void updateDescriptionRowHeights() {
        if (cartTable == null || cartTable.getRowCount() == 0) {
            return;
        }

        for (int row = 0; row < cartTable.getRowCount(); row++) {
            int rowHeight = 24;
            Object value = cartTable.getValueAt(row, 2);
            String text = value == null ? "" : value.toString();

            TableCellRenderer renderer = cartTable.getCellRenderer(row, 2);
            Component component = renderer.getTableCellRendererComponent(cartTable, text, false, false, row, 2);

            if (component instanceof JTextArea textArea) {
                int columnWidth = cartTable.getColumnModel().getColumn(CART_COL_DESCRIPTION).getWidth();
                textArea.setSize(columnWidth, Short.MAX_VALUE);
                rowHeight = Math.max(rowHeight, textArea.getPreferredSize().height + 4);
            }

            cartTable.setRowHeight(row, rowHeight);
        }
    }

    private static class MultiLineTableCellRenderer extends JTextArea implements TableCellRenderer {
        public MultiLineTableCellRenderer() {
            setLineWrap(true);
            setWrapStyleWord(true);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            setFont(table.getFont());

            if (isSelected) {
                setForeground(table.getSelectionForeground());
                setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }

            setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
            return this;
        }
    }

    private void searchProducts() {
        searchProducts(true);
    }

    private void searchProducts(boolean showMessages) {
        final String searchText = searchField.getText().trim();

        if (SessionManager.getCurrentLocationId() == null) {
            JOptionPane.showMessageDialog(this, "No store is selected for this session.");
            return;
        }

        final int locationId = SessionManager.getCurrentLocationId();
        warmProductSearchCacheInBackground();
        final long requestId = ++latestSearchRequestId;
        if (searchWorker != null && !searchWorker.isDone()) {
            searchWorker.cancel(true);
        }

        searchWorker = new SwingWorker<>() {
            @Override
            protected java.util.List<Object[]> doInBackground() throws Exception {
                java.util.List<Object[]> cachedRows = tryFilterCachedProducts(locationId, searchText);
                if (cachedRows != null) {
                    return cachedRows;
                }
                String sql = """
                    SELECT p.product_id, p.name, COALESCE(p.size, '') AS size, p.description, p.sku, p.price,
                           COALESCE(p.product_type, 'INVENTORY') AS product_type,
                           p.category_id,
                           COALESCE(i.quantity_on_hand, 0) AS quantity_on_hand
                    FROM products p
                    LEFT JOIN inventory i
                        ON p.product_id = i.product_id
                       AND i.location_id = ?
                    WHERE (? = '' OR p.name ILIKE ? OR COALESCE(p.size, '') ILIKE ? OR p.sku ILIKE ?)
                    ORDER BY p.name
                    LIMIT 250
                    """;

                try (Connection conn = DB.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, locationId);
                    ps.setString(2, searchText);
                    ps.setString(3, "%" + searchText + "%");
                    ps.setString(4, "%" + searchText + "%");
                    ps.setString(5, "%" + searchText + "%");

                    java.util.List<Object[]> rows = new java.util.ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if (isCancelled()) {
                                return rows;
                            }
                            rows.add(new Object[]{
                                    rs.getInt("product_id"),
                                    rs.getString("name"),
                                    rs.getString("size"),
                                    rs.getString("description"),
                                    rs.getString("sku"),
                                    rs.getDouble("price"),
                                    rs.getString("product_type"),
                                    rs.getObject("category_id"),
                                    rs.getInt("quantity_on_hand")
                            });
                        }
                    }
                    return rows;
                }
            }

            @Override
            protected void done() {
                if (isCancelled() || requestId != latestSearchRequestId || !isDisplayable()) {
                    return;
                }
                try {
                    java.util.List<Object[]> rows = get();
                    if (rows.isEmpty()) {
                        closeSearchPopup();
                        if (showMessages) {
                            JOptionPane.showMessageDialog(MakeASale.this,
                                    searchText.isEmpty() ? "No products found for this store." : "No matching products found.");
                        }
                        return;
                    }
                    showSearchResultsPopup(rows);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(MakeASale.this, "Database error: " + e.getMessage());
                }
            }
        };
        searchWorker.execute();
    }

    private java.util.List<Object[]> tryFilterCachedProducts(int locationId, String searchText) {
        if (cachedProductLocationId != locationId || productSearchCache.isEmpty()) {
            return null;
        }
        String normalized = searchText == null ? "" : searchText.trim().toLowerCase();
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (Object[] row : productSearchCache) {
            if (rows.size() >= 250) {
                break;
            }
            if (normalized.isEmpty() || cachedRowMatches(row, normalized)) {
                rows.add(row);
            }
        }
        return rows;
    }

    private boolean cachedRowMatches(Object[] row, String normalized) {
        String name = rowValue(row, 1);
        String size = rowValue(row, 2);
        String sku = rowValue(row, 4);
        return name.contains(normalized) || size.contains(normalized) || sku.contains(normalized);
    }

    private String rowValue(Object[] row, int index) {
        if (row == null || index < 0 || index >= row.length || row[index] == null) {
            return "";
        }
        return String.valueOf(row[index]).toLowerCase();
    }

    private void warmProductSearchCacheInBackground() {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            return;
        }
        if (cachedProductLocationId == locationId && !productSearchCache.isEmpty()) {
            return;
        }
        if (productCacheWorker != null && !productCacheWorker.isDone()) {
            return;
        }

        final int loadLocationId = locationId;
        productCacheWorker = new SwingWorker<>() {
            @Override
            protected java.util.List<Object[]> doInBackground() throws Exception {
                String sql = """
                    SELECT p.product_id, p.name, COALESCE(p.size, '') AS size, p.description, p.sku, p.price,
                           COALESCE(p.product_type, 'INVENTORY') AS product_type,
                           p.category_id,
                           COALESCE(i.quantity_on_hand, 0) AS quantity_on_hand
                    FROM products p
                    LEFT JOIN inventory i
                        ON p.product_id = i.product_id
                       AND i.location_id = ?
                    ORDER BY p.name
                    """;

                try (Connection conn = DB.getConnection();
                     PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, loadLocationId);
                    java.util.List<Object[]> rows = new java.util.ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if (isCancelled()) {
                                return rows;
                            }
                            rows.add(new Object[]{
                                    rs.getInt("product_id"),
                                    rs.getString("name"),
                                    rs.getString("size"),
                                    rs.getString("description"),
                                    rs.getString("sku"),
                                    rs.getDouble("price"),
                                    rs.getString("product_type"),
                                    rs.getObject("category_id"),
                                    rs.getInt("quantity_on_hand")
                            });
                        }
                    }
                    return rows;
                }
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    productSearchCache = get();
                    cachedProductLocationId = loadLocationId;
                } catch (Exception ignored) {
                    // Keep fallback search path active if cache warm-up fails.
                }
            }
        };
        productCacheWorker.execute();
    }


    private void showSearchResultsPopup(java.util.List<Object[]> rows) {
        if (searchPopup == null) {
            searchPopup = new JPopupMenu();
            searchPopup.setBorder(BorderFactory.createLineBorder(DeckersPalette.sectionBorder(DeckersPalette.MAGENTA)));
            searchPopup.setFocusable(false);

            String[] columns = {"ID", "Name", "Size", "Description", "SKU", "Price", "Type", "Department ID", "Stock"};
            DefaultTableModel resultsModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            searchResultsTable = new JTable(resultsModel);
            DeckersSwing.styleTable(searchResultsTable);
            searchResultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            searchResultsTable.setAutoCreateRowSorter(false);
            searchResultsTable.setRowHeight(24);
            JTableHeader header = searchResultsTable.getTableHeader();
            header.setReorderingAllowed(false);
            header.setPreferredSize(new Dimension(0, 0));
            header.setMinimumSize(new Dimension(0, 0));
            header.setMaximumSize(new Dimension(0, 0));
            header.setVisible(false);
            searchResultsTable.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        addSelectedSearchResultToCart();
                    }
                }
            });

            searchResultsScrollPane = new JScrollPane(searchResultsTable);
            searchResultsScrollPane.setBorder(BorderFactory.createEmptyBorder());
            searchResultsScrollPane.getViewport().setBackground(DeckersPalette.surface());
            searchResultsScrollPane.setColumnHeaderView(null);
            searchResultsScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

            searchPopup.setLayout(new BorderLayout());
            searchPopup.add(searchResultsScrollPane, BorderLayout.CENTER);
        }

        DefaultTableModel model = (DefaultTableModel) searchResultsTable.getModel();
        model.setRowCount(0);
        for (Object[] row : rows) {
            model.addRow(row);
        }

        if (searchResultsTable.getRowCount() > 0) {
            searchResultsTable.setRowSelectionInterval(0, 0);
        }

        searchResultsScrollPane.setPreferredSize(new Dimension(Math.max(searchField.getWidth(), 500), 220));

        searchResultsTable.getColumnModel().getColumn(0).setPreferredWidth(50);
        searchResultsTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        searchResultsTable.getColumnModel().getColumn(2).setPreferredWidth(220);
        searchResultsTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        searchResultsTable.getColumnModel().getColumn(4).setPreferredWidth(80);
        searchResultsTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        searchResultsTable.getColumnModel().getColumn(6).setPreferredWidth(70);
        searchResultsTable.getColumnModel().getColumn(7).setMinWidth(0);
        searchResultsTable.getColumnModel().getColumn(7).setMaxWidth(0);
        searchResultsTable.getColumnModel().getColumn(7).setPreferredWidth(0);

        if (searchPopup.isVisible()) {
            searchPopup.setVisible(false);
        }

        searchPopup.show(searchField, 0, searchField.getHeight());
    }

    private void addSelectedSearchResultToCart() {
        if (searchResultsTable == null || searchResultsTable.getSelectedRow() == -1) {
            if (searchPopup != null && searchPopup.isVisible()) {
                JOptionPane.showMessageDialog(this, "Please select a product.");
            }
            return;
        }

        int selectedRow = searchResultsTable.convertRowIndexToModel(searchResultsTable.getSelectedRow());

        int productId = ((Number) searchResultsTable.getModel().getValueAt(selectedRow, 0)).intValue();
        String name = String.valueOf(searchResultsTable.getModel().getValueAt(selectedRow, 1));
        String size = String.valueOf(searchResultsTable.getModel().getValueAt(selectedRow, 2));
        String description = String.valueOf(searchResultsTable.getModel().getValueAt(selectedRow, 3));
        String sku = String.valueOf(searchResultsTable.getModel().getValueAt(selectedRow, 4));
        double price = ((Number) searchResultsTable.getModel().getValueAt(selectedRow, 5)).doubleValue();
        String productType = normalizeProductType(String.valueOf(searchResultsTable.getModel().getValueAt(selectedRow, 6)));
        Integer departmentId = parseNullableInt(searchResultsTable.getModel().getValueAt(selectedRow, 7));

        String qtyText = JOptionPane.showInputDialog(this, "Enter quantity:", "1");
        if (qtyText == null) {
            return;
        }

        int qty;
        try {
            qty = Integer.parseInt(qtyText);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter a valid number.");
            return;
        }

        addToCart(productId, displayNameWithSize(name, size), description, sku, price, qty, productType, departmentId);
        closeSearchPopup();
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    private void closeSearchPopup() {
        if (searchPopup != null) {
            searchPopup.setVisible(false);
        }
    }

    private String displayNameWithSize(String name, String size) {
        if (size == null || size.isBlank()) {
            return name;
        }
        return name + " (" + size + ")";
    }

    private void addToCart(int productId, String name, String description, String sku, double price, int qty, String productType, Integer departmentId) {
        for (int i = 0; i < cartModel.getRowCount(); i++) {
            int existingProductId = Integer.parseInt(cartModel.getValueAt(i, CART_COL_ID).toString());

            if (existingProductId == productId) {
                int existingQty = Integer.parseInt(cartModel.getValueAt(i, CART_COL_QTY).toString());
                int newQty = existingQty + qty;

                cartModel.setValueAt(newQty, i, CART_COL_QTY);
                updateLineTotals();
                return;
            }
        }

        cartModel.addRow(new Object[]{
                productId,
                name,
                description,
                sku,
                price,
                qty,
                BigDecimal.ZERO,
                price * qty,
                BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP),
                normalizeProductType(productType),
                departmentId
        });
        updateLineTotals();
        configureCartTableColumns();
    }

    private Integer parseNullableInt(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String normalizeProductType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase().replace(' ', '_');
        if ("SERVICE".equals(normalized) || "NON_INVENTORY".equals(normalized)) {
            return normalized;
        }
        return "INVENTORY";
    }

    private boolean isInventoryProduct(String productType) {
        return "INVENTORY".equals(normalizeProductType(productType));
    }

    private void handlePriceEditOverrideAtCart(int row) {
        if (row < 0 || row >= cartModel.getRowCount()) {
            return;
        }
        int productId = parseIntOrDefault(cartModel.getValueAt(row, CART_COL_ID), -1);
        if (productId <= 0) {
            return;
        }

        BigDecimal enteredPrice = parseMoneyOrZero(cartModel.getValueAt(row, CART_COL_PRICE));
        BigDecimal catalogPrice = parseMoneyOrZero(cartModel.getValueAt(row, CART_COL_ORIGINAL_PRICE));
        if (enteredPrice.compareTo(catalogPrice) == 0) {
            pendingPriceOverrideApprovals.remove(productId);
            return;
        }
        if (canChangeSaleItemPrice()) {
            pendingPriceOverrideApprovals.remove(productId);
            return;
        }
        PendingPriceApproval existingApproval = pendingPriceOverrideApprovals.get(productId);
        if (existingApproval != null && existingApproval.approvedPrice().compareTo(enteredPrice) == 0) {
            return;
        }

        String productName = String.valueOf(cartModel.getValueAt(row, CART_COL_NAME));
        ManagerApprovalService.ApprovalResult approval = ManagerApprovalService.requestApproval(
                this,
                CHANGE_SALE_ITEM_PRICE_PERMISSION,
                "Price Override",
                "Reason for price override on " + productName + ":"
        );
        if (approval == null) {
            updatingCart = true;
            try {
                cartModel.setValueAt(catalogPrice, row, CART_COL_PRICE);
            } finally {
                updatingCart = false;
            }
            pendingPriceOverrideApprovals.remove(productId);
            updateLineTotals();
            return;
        }

        pendingPriceOverrideApprovals.put(productId, new PendingPriceApproval(enteredPrice, approval));
        if (overrideStatusLabel != null) {
            overrideStatusLabel.setText("Price override approved by: " + approval.approvedByName());
        }
    }

    private void handleItemDiscountEditOverrideAtCart(int row) {
        if (row < 0 || row >= cartModel.getRowCount()) {
            return;
        }
        int productId = parseIntOrDefault(cartModel.getValueAt(row, CART_COL_ID), -1);
        if (productId <= 0) {
            return;
        }
        BigDecimal discountPercent = parsePercentOrZero(cartModel.getValueAt(row, CART_COL_ITEM_DISCOUNT));
        if (discountPercent.compareTo(BigDecimal.ZERO) <= 0) {
            pendingItemDiscountApprovals.remove(productId);
            return;
        }
        if (canApplySaleDiscount()) {
            pendingItemDiscountApprovals.remove(productId);
            return;
        }
        PendingDiscountApproval existingApproval = pendingItemDiscountApprovals.get(productId);
        if (existingApproval != null && existingApproval.approvedDiscountPercent().compareTo(discountPercent) == 0) {
            return;
        }

        String productName = String.valueOf(cartModel.getValueAt(row, CART_COL_NAME));
        ManagerApprovalService.ApprovalResult approval = ManagerApprovalService.requestApproval(
                this,
                APPLY_SALE_DISCOUNT_PERMISSION,
                "Item Discount Override",
                "Reason for item discount on " + productName + ":"
        );
        if (approval == null) {
            updatingCart = true;
            try {
                cartModel.setValueAt(BigDecimal.ZERO, row, CART_COL_ITEM_DISCOUNT);
            } finally {
                updatingCart = false;
            }
            pendingItemDiscountApprovals.remove(productId);
            updateLineTotals();
            return;
        }

        pendingItemDiscountApprovals.put(productId, new PendingDiscountApproval(discountPercent, approval));
        if (overrideStatusLabel != null) {
            overrideStatusLabel.setText("Item discount approved by: " + approval.approvedByName());
        }
    }

    private String getCartProductType(int row) {
        if (cartModel.getColumnCount() <= CART_COL_PRODUCT_TYPE) {
            return "INVENTORY";
        }
        return normalizeProductType(String.valueOf(cartModel.getValueAt(row, CART_COL_PRODUCT_TYPE)));
    }

    private void updateLineTotals() {
        updatingCart = true;
        try {
            for (int i = 0; i < cartModel.getRowCount(); i++) {
                Object priceValue = cartModel.getValueAt(i, CART_COL_PRICE);
                Object qtyValue = cartModel.getValueAt(i, CART_COL_QTY);
                Object itemDiscountValue = cartModel.getValueAt(i, CART_COL_ITEM_DISCOUNT);

                int qty;
                BigDecimal price;
                BigDecimal itemDiscountPercent;

                try {
                    qty = Integer.parseInt(qtyValue.toString());
                } catch (NumberFormatException ex) {
                    qty = 1;
                }

                try {
                    price = new BigDecimal(priceValue.toString()).setScale(2, RoundingMode.HALF_UP);
                } catch (NumberFormatException ex) {
                    price = BigDecimal.ZERO;
                }

                itemDiscountPercent = parsePercentOrZero(itemDiscountValue);
                int productId = parseIntOrDefault(cartModel.getValueAt(i, CART_COL_ID), -1);
                PendingDiscountApproval pendingDiscountApproval = pendingItemDiscountApprovals.get(productId);
                if (!canApplySaleDiscount()
                        && itemDiscountPercent.compareTo(BigDecimal.ZERO) > 0
                        && (pendingDiscountApproval == null
                        || pendingDiscountApproval.approvedDiscountPercent().compareTo(itemDiscountPercent) != 0)) {
                    itemDiscountPercent = BigDecimal.ZERO;
                }

                BigDecimal lineGross = price.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
                BigDecimal lineDiscount = lineGross.multiply(itemDiscountPercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal lineTotal = lineGross.subtract(lineDiscount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

                cartModel.setValueAt(price, i, CART_COL_PRICE);
                cartModel.setValueAt(qty, i, CART_COL_QTY);
                cartModel.setValueAt(itemDiscountPercent, i, CART_COL_ITEM_DISCOUNT);
                cartModel.setValueAt(lineTotal, i, CART_COL_LINE_TOTAL);
            }
            updateOverallTotal();
            updateDescriptionRowHeights();
            configureCartTableColumns();
        } finally {
            updatingCart = false;
        }
    }

    private double getCartSubtotal() {
        double total = 0.0;

        for (int i = 0; i < cartModel.getRowCount(); i++) {
            Object lineTotalValue = cartModel.getValueAt(i, CART_COL_LINE_TOTAL);
            try {
                total += Double.parseDouble(lineTotalValue.toString());
            } catch (NumberFormatException ex) {
                // ignore invalid values
            }
        }

        return total;
    }

    private BigDecimal getCartGrossSubtotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 0; i < cartModel.getRowCount(); i++) {
            BigDecimal price = parseMoneyOrZero(cartModel.getValueAt(i, CART_COL_PRICE));
            int qty = parseIntOrDefault(cartModel.getValueAt(i, CART_COL_QTY), 0);
            total = total.add(price.multiply(BigDecimal.valueOf(qty)));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getItemDiscountTotal() {
        return getCartGrossSubtotal().subtract(BigDecimal.valueOf(getCartSubtotal()).setScale(2, RoundingMode.HALF_UP))
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private double getOverallTotal() {
        return getFinalTotalAmount().doubleValue();
    }

    private BigDecimal getFinalTotalAmount() {
        try (Connection conn = DB.getConnection()) {
            return getFinalTotalAmount(conn);
        } catch (SQLException ex) {
            return getPreVatSaleTotal(getDiscountPercent()).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private BigDecimal getFinalTotalAmount(Connection conn) throws SQLException {
        BigDecimal discountPercent = getDiscountPercent();
        BigDecimal preVatTotal = getPreVatSaleTotal(discountPercent);
        return preVatTotal.add(calculateVat(conn, discountPercent).amount()).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getPreVatSaleTotal(BigDecimal discountPercent) {
        BigDecimal subtotal = BigDecimal.valueOf(getCartSubtotal()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cleanDiscountPercent = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        BigDecimal discountAmount = subtotal.multiply(cleanDiscountPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return subtotal.subtract(discountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private VatCalculation calculateVat(Connection conn, BigDecimal saleDiscountPercent) throws SQLException {
        CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
        if (!settings.vatEnabled()) {
            return new VatCalculation(BigDecimal.ZERO, BigDecimal.ZERO, "");
        }
        BigDecimal preVatTotal = getPreVatSaleTotal(saleDiscountPercent);
        if (preVatTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new VatCalculation(BigDecimal.ZERO, BigDecimal.ZERO, settings.vatUseDepartmentRates() ? "DEPARTMENT" : "FIXED");
        }
        if (!settings.vatUseDepartmentRates()) {
            BigDecimal amount = preVatTotal.multiply(settings.vatFixedRatePercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return new VatCalculation(amount, settings.vatFixedRatePercent(), "FIXED");
        }

        Map<Integer, BigDecimal> departmentRates = loadDepartmentVatRates(conn);
        BigDecimal saleDiscountMultiplier = BigDecimal.ONE.subtract(
                (saleDiscountPercent == null ? BigDecimal.ZERO : saleDiscountPercent)
                        .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
        );
        BigDecimal vatAmount = BigDecimal.ZERO;
        for (int i = 0; i < cartModel.getRowCount(); i++) {
            Integer departmentId = parseNullableInt(cartModel.getValueAt(i, CART_COL_DEPARTMENT_ID));
            BigDecimal rate = departmentId == null ? BigDecimal.ZERO : departmentRates.getOrDefault(departmentId, BigDecimal.ZERO);
            if (rate.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal lineAfterItemDiscount = parseMoneyOrZero(cartModel.getValueAt(i, CART_COL_LINE_TOTAL));
            BigDecimal taxableLine = lineAfterItemDiscount.multiply(saleDiscountMultiplier).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            vatAmount = vatAmount.add(taxableLine.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        BigDecimal effectiveRate = preVatTotal.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : vatAmount.multiply(BigDecimal.valueOf(100)).divide(preVatTotal, 2, RoundingMode.HALF_UP);
        return new VatCalculation(vatAmount, effectiveRate, "DEPARTMENT");
    }

    private Map<Integer, BigDecimal> loadDepartmentVatRates(Connection conn) throws SQLException {
        java.util.Map<Integer, BigDecimal> rates = new java.util.HashMap<>();
        if (!hasColumn(conn, "categories", "vat_rate_percent")) {
            return rates;
        }
        try (PreparedStatement ps = conn.prepareStatement("SELECT category_id, COALESCE(vat_rate_percent, 0) AS vat_rate_percent FROM categories")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rates.put(rs.getInt("category_id"), rs.getBigDecimal("vat_rate_percent"));
                }
            }
        }
        return rates;
    }

    private boolean hasColumn(Connection conn, String tableName, String columnName) {
        String sql = """
                SELECT 1
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = ?
                  AND column_name = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            ps.setString(2, columnName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException ex) {
            return false;
        }
    }

    private BigDecimal getDiscountPercent() {
        if (!canApplySaleDiscount() || discountPercentField == null) {
            return BigDecimal.ZERO;
        }

        String text = discountPercentField.getText().trim();
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            BigDecimal percent = new BigDecimal(text);
            if (percent.compareTo(BigDecimal.ZERO) < 0) {
                return BigDecimal.ZERO;
            }
            if (percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                return BigDecimal.valueOf(100);
            }
            return percent;
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private void setDiscountFieldValue(String value) {
        if (discountPercentField == null) {
            return;
        }
        String next = value == null ? "" : value;
        String current = discountPercentField.getText();
        if (next.equals(current)) {
            return;
        }
        suppressDiscountFieldEvents = true;
        try {
            discountPercentField.setText(next);
        } finally {
            suppressDiscountFieldEvents = false;
        }
    }

    private BigDecimal parseDiscountPercentOrShowError() {
        if (!canApplySaleDiscount()) {
            return BigDecimal.ZERO;
        }

        String text = discountPercentField.getText().trim();
        if (text.isEmpty()) {
            return BigDecimal.ZERO;
        }

        try {
            BigDecimal percent = new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
            if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
                JOptionPane.showMessageDialog(this, "Discount percent must be between 0 and 100.");
                return null;
            }
            return percent;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Discount percent must be a valid number.");
            return null;
        }
    }

    private BigDecimal getDiscountAmount(BigDecimal subtotal) {
        BigDecimal discountPercent = getDiscountPercent();
        return subtotal.multiply(discountPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal parsePercentOrZero(Object value) {
        BigDecimal percent = parseMoneyOrZero(value);
        if (percent.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            return BigDecimal.valueOf(100);
        }
        return percent.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseMoneyOrZero(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value).trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private int parseIntOrDefault(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private void updateCurrentDateLabel() {
        if (currentDateLabel == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(StoreTimeZoneHelper.getStoreZone());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        lastShownDate = now.format(formatter);
        currentDateLabel.setText("Date: " + lastShownDate);
    }
    private void updateCurrentTimeLabel() {
        if (currentTimeLabel == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(StoreTimeZoneHelper.getStoreZone());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
        currentTimeLabel.setText("Time: " + now.format(formatter));
        updateSalesGreeting();
    }

    private void startDateRefreshTimer() {
        javax.swing.Timer dateTimer = new javax.swing.Timer(1000, e -> {
            updateCurrentTimeLabel();
            String today = LocalDateTime.now(StoreTimeZoneHelper.getStoreZone()).format(DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            if (!today.equals(lastShownDate)) {
                updateCurrentDateLabel();
            }
        });
        dateTimer.setInitialDelay(0);
        dateTimer.start();
    }
    private void updateCurrentUserLabel() {
       if(currentUserLabel == null){
           return;
       }
       if(SessionManager.getCurrentUserId() == null || SessionManager.getCurrentUsername() == null){
           currentUserLabel.setText("No User currently loged in");
       }
       else{
           currentUserLabel.setText("Current Cashier: " + SessionManager.getCurrentUserDisplayName());
       }
    }
    private void updateSelectedStoreLabel() {
        if (selectedStoreLabel == null) {
            return;
        }

        if (SessionManager.getCurrentLocationId() == null || SessionManager.getCurrentLocationName() == null) {
            selectedStoreLabel.setText("Store: Not selected");
        } else {
            selectedStoreLabel.setText("Store: " + SessionManager.getCurrentLocationName() + " (ID: " + SessionManager.getCurrentLocationId() + ")");
        }
    }

    private void loadCustomerAccounts() {
        if (customerAccountBox == null) {
            return;
        }

        CustomerAccountOption selectedBeforeReload = getSelectedCustomerAccount();
        customerAccountOptions = new java.util.ArrayList<>();
        customerAccountBox.removeAllItems();

        String sql = """
                SELECT ca.customer_id,
                       ca.account_number,
                       ca.name AS customer_name,
                       ca.credit_limit,
                       ca.current_balance,
                       (ca.credit_limit - ca.current_balance) AS available_credit,
                       COALESCE(ca.is_business, FALSE) AS is_business,
                       COALESCE(ct.name, '') AS customer_type_name
                FROM customer_accounts ca
                LEFT JOIN customer_types ct ON ct.customer_type_id = ca.customer_type_id
                WHERE ca.is_active = TRUE
                ORDER BY ca.name
                """;

        try (Connection conn = DB.getConnection()) {
            CustomerAccountLedgerService.repairAllBalances(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    customerAccountOptions.add(new CustomerAccountOption(
                            rs.getInt("customer_id"),
                            rs.getString("account_number"),
                            rs.getString("customer_name"),
                            rs.getBigDecimal("credit_limit"),
                            rs.getBigDecimal("current_balance"),
                            rs.getBigDecimal("available_credit"),
                            rs.getBoolean("is_business"),
                            rs.getString("customer_type_name")
                    ));
                }
            }
            applyCustomerAccountFilter("", false);
            if (selectedBeforeReload != null) {
                selectCustomerById(selectedBeforeReload.customerId);
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load customer accounts: " + ex.getMessage());
        }
    }

    private void openQuickCustomerAccount() {
        QuickCustomerAccount screen = new QuickCustomerAccount(this::loadCustomerAccounts);
        screen.setLocationRelativeTo(this);
        screen.setVisible(true);
    }

    private void updateCustomerAccountEnabled() {
        customerAccountBox.setEnabled(true);
    }

    private void configureCustomerAccountSearch() {
        if (customerAccountBox == null || customerAccountBox.getEditor() == null) {
            return;
        }

        Component editorComponent = customerAccountBox.getEditor().getEditorComponent();
        if (!(editorComponent instanceof JTextField editorField)) {
            return;
        }

        editorField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(),
                BorderFactory.createEmptyBorder(0, 8, 0, 8)
        ));
        editorField.setFont(new Font("SansSerif", Font.PLAIN, 14));
        editorField.setForeground(DeckersPalette.text());
        editorField.setCaretColor(DeckersPalette.text());
        editorField.setBackground(DeckersPalette.fieldBackground());
        setFixedControlHeight(editorField, 0);
        if (editorField instanceof PromptTextField promptTextField) {
            promptTextField.setPrompt("Enter customer name");
        }
        editorField.putClientProperty("JTextField.placeholderText", "Enter customer name");
        editorField.getDocument().addDocumentListener(new DocumentListener() {
            private void filter() {
                if (updatingCustomerAccountFilter) {
                    return;
                }
                SwingUtilities.invokeLater(() -> applyCustomerAccountFilter(editorField.getText(), true));
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                filter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                filter();
            }
        });
    }

    private void applyCustomerAccountFilter(String text, boolean showPopup) {
        if (customerAccountBox == null) {
            return;
        }

        String filter = text == null ? "" : text.trim();
        updatingCustomerAccountFilter = true;
        try {
            DefaultComboBoxModel<CustomerAccountOption> model = new DefaultComboBoxModel<>();
            model.addElement(null);
            for (CustomerAccountOption option : customerAccountOptions) {
                if (filter.isBlank() || option.matches(filter)) {
                    model.addElement(option);
                }
            }
            customerAccountBox.setModel(model);
            customerAccountBox.setSelectedItem(filter);
            customerAccountBox.getEditor().setItem(filter);
        } finally {
            updatingCustomerAccountFilter = false;
        }

        if (showPopup && customerAccountBox.isShowing()) {
            customerAccountBox.setPopupVisible(customerAccountBox.getItemCount() > 1);
        }
    }

    private CustomerAccountOption getSelectedCustomerAccount() {
        Object selected = customerAccountBox == null ? null : customerAccountBox.getSelectedItem();
        if (selected instanceof CustomerAccountOption option) {
            return option;
        }

        if (customerAccountBox != null && customerAccountBox.getEditor() != null) {
            Object editorItem = customerAccountBox.getEditor().getItem();
            String text = editorItem == null ? "" : editorItem.toString().trim();
            if (!text.isBlank()) {
                for (CustomerAccountOption option : customerAccountOptions) {
                    if (option.matchesExact(text)) {
                        return option;
                    }
                }
            }
        }
        return null;
    }

    private void checkout(boolean showReceiptPreview) {
        if (!PermissionManager.requirePermission(MAKE_SALE_PERMISSION, this, "Checkout")) {
            refreshPermissionButtons();
            return;
        }
        if (cartModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Cart is empty.");
            return;
        }

        BigDecimal discountPercent = parseDiscountPercentOrShowError();
        if (discountPercent == null) {
            return;
        }
        if (discountPercent.compareTo(BigDecimal.ZERO) > 0 && !canApplySaleDiscount()) {
            JOptionPane.showMessageDialog(this, "You do not have permission to apply sale discounts.");
            discountPercentField.setText("0");
            updateOverallTotal();
            return;
        }
        CompanyCustomizationManager.SaleSafetySettings saleSafetySettings = CompanyCustomizationManager.loadSaleSafetySettings();
        BigDecimal discountLimit = saleSafetySettings.discountLimitPercent() == null
                ? BigDecimal.valueOf(5)
                : saleSafetySettings.discountLimitPercent();
        String saleDiscountOverrideReason = null;
        Integer saleDiscountOverrideByUserId = null;
        String saleDiscountOverrideByName = null;
        if (discountPercent.compareTo(discountLimit) > 0) {
            ManagerApprovalService.ApprovalResult approval = ManagerApprovalService.requestApproval(
                    this,
                    SALE_DISCOUNT_OVERRIDE_PERMISSION,
                    "Sale Discount Override",
                    "Reason for discount override:"
            );
            if (approval == null) {
                return;
            }
            saleDiscountOverrideReason = approval.reason();
            saleDiscountOverrideByUserId = approval.approvedByUserId();
            saleDiscountOverrideByName = approval.approvedByName();
            if (overrideStatusLabel != null) {
                overrideStatusLabel.setText("Discount override approved by: " + saleDiscountOverrideByName);
            }
        }

        String paymentMethod = selectedPaymentMethod;
        if (paymentMethod == null || paymentMethod.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select a payment method before checkout.");
            updateCheckoutAvailability();
            return;
        }
        CustomerAccountOption selectedCustomer = getSelectedCustomerAccount();

        boolean chargeCustomerAccount = "ACCOUNT".equals(paymentMethod);
        boolean cashPayment = "CASH".equals(paymentMethod);
        String paymentReference = paymentReferenceField == null ? "" : paymentReferenceField.getText().trim();

        if (chargeCustomerAccount && selectedCustomer == null) {
            JOptionPane.showMessageDialog(this, "Select a customer account for account payment.");
            return;
        }
        if ("MMG".equals(paymentMethod) && paymentReference.isBlank()) {
            JOptionPane.showMessageDialog(this, "Enter the MMG transaction reference number.");
            return;
        }

        BigDecimal cashCollected = BigDecimal.ZERO;
        if (cashPayment) {
            cashCollected = promptForCashCollected(getFinalTotalAmount());
            if (cashCollected == null) {
                return;
            }
        }

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);

            if (SessionManager.getCurrentLocationId() == null) {
                conn.setAutoCommit(true);
                JOptionPane.showMessageDialog(this, "No store is selected for this session.");
                return;
            }
            if (SessionManager.getCurrentUserId() == null) {
                conn.setAutoCommit(true);
                JOptionPane.showMessageDialog(this, "No cashier is logged in for this session.");
                return;
            }

            int locationId = SessionManager.getCurrentLocationId();

            try {
                try {
                    DeviceContextService.requireSalesAllowed(conn);
                } catch (SQLException ex) {
                    conn.setAutoCommit(true);
                    JOptionPane.showMessageDialog(
                            this,
                            ex.getMessage(),
                            "Device Access Required",
                            JOptionPane.WARNING_MESSAGE
                    );
                    return;
                }

                CashDrawerContext cashDrawer = new CashDrawerContext(null, null);
                if (cashPayment) {
                    try {
                        cashDrawer = CashDrawerService.requireActiveCashSession(conn);
                    } catch (SQLException ex) {
                        conn.setAutoCommit(true);
                        JOptionPane.showMessageDialog(
                                this,
                                ex.getMessage(),
                                "Cash Drawer Required",
                                JOptionPane.WARNING_MESSAGE
                        );
                        return;
                    }
                }

                BigDecimal subtotalAmount = getCartGrossSubtotal();
                BigDecimal itemDiscountTotal = getItemDiscountTotal();
                BigDecimal lineSubtotalAfterItemDiscounts = BigDecimal.valueOf(getCartSubtotal()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal saleLevelDiscountAmount = lineSubtotalAfterItemDiscounts.multiply(discountPercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal preVatSaleTotal = lineSubtotalAfterItemDiscounts.subtract(saleLevelDiscountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                VatCalculation vat = calculateVat(conn, discountPercent);
                BigDecimal saleTotal = preVatSaleTotal.add(vat.amount()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal discountAmount = itemDiscountTotal.add(saleLevelDiscountAmount).setScale(2, RoundingMode.HALF_UP);
                if (saleTotal.compareTo(BigDecimal.ZERO) <= 0) {
                    JOptionPane.showMessageDialog(this, "Sale total must be greater than zero.");
                    return;
                }
                BigDecimal saleDiscountMultiplier = BigDecimal.ONE.subtract(
                        discountPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                );
                if (chargeCustomerAccount) {
                    validateAndChargeCustomerAccount(conn, selectedCustomer.customerId, saleTotal);
                }

                ReceiptNumberManager.ReceiptNumber receipt = ReceiptNumberManager.nextReceipt(locationId);
                String paymentStatus = chargeCustomerAccount ? "UNPAID" : "PAID";
                BigDecimal amountPaid = chargeCustomerAccount ? BigDecimal.ZERO : saleTotal;
                repairSalesSequence(conn);
                String insertSaleSql = """
                        INSERT INTO sales (
                            location_id,
                            user_id,
                            customer_id,
                            total_amount,
                            status,
                            payment_method,
	                            payment_status,
	                            amount_paid,
	                            user_name,
	                            receipt_number,
	                            receipt_device_id,
	                            receipt_sequence,
	                            subtotal_amount,
	                            discount_percent,
	                            discount_amount,
                                vat_amount,
                                vat_rate_percent,
                                vat_mode,
                                payment_reference,
	                            transaction_source,
                                device_id,
                                cash_drawer_id,
                                cash_drawer_name,
                                cash_drawer_session_id,
                                discount_override_reason,
                                discount_override_by_user_id,
                                discount_override_by_name,
                                completed_at
	                        )
	                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
	                        """;
                int saleId;

                try (PreparedStatement saleStmt = conn.prepareStatement(insertSaleSql, Statement.RETURN_GENERATED_KEYS)) {
                    saleStmt.setInt(1, locationId);
                    saleStmt.setInt(2, SessionManager.getCurrentUserId());
                    if (selectedCustomer == null) {
                        saleStmt.setNull(3, java.sql.Types.INTEGER);
                    } else {
                        saleStmt.setInt(3, selectedCustomer.customerId);
                    }
                    saleStmt.setBigDecimal(4, saleTotal);
                    saleStmt.setString(5, "COMPLETED");
                    saleStmt.setString(6, paymentMethod);
	                    saleStmt.setString(7, paymentStatus);
	                    saleStmt.setBigDecimal(8, amountPaid);
	                    saleStmt.setString(9, SessionManager.getCurrentUserDisplayName());
		                    saleStmt.setString(10, receipt.receiptNumber());
		                    saleStmt.setString(11, receipt.deviceId());
		                    saleStmt.setInt(12, receipt.sequence());
		                    saleStmt.setBigDecimal(13, subtotalAmount);
		                    saleStmt.setBigDecimal(14, discountPercent);
		                    saleStmt.setBigDecimal(15, discountAmount);
                            saleStmt.setBigDecimal(16, vat.amount());
                            saleStmt.setBigDecimal(17, vat.ratePercent());
                            saleStmt.setString(18, vat.mode());
                            saleStmt.setString(19, paymentReference.isBlank() ? null : paymentReference);
	                    saleStmt.setString(20, "Java_app");
                            saleStmt.setString(21, DeviceContextService.currentDeviceId());
                            setNullableLong(saleStmt, 22, cashDrawer.cashDrawerId());
                            saleStmt.setString(23, cashDrawer.drawerName());
                            setNullableLong(saleStmt, 24, cashDrawer.sessionId());
                            saleStmt.setString(25, saleDiscountOverrideReason);
                            setNullableInteger(saleStmt, 26, saleDiscountOverrideByUserId);
                            saleStmt.setString(27, saleDiscountOverrideByName);
	                    saleStmt.executeUpdate();

                    try (ResultSet generatedKeys = saleStmt.getGeneratedKeys()) {
                        if (!generatedKeys.next()) {
                            throw new SQLException("Failed to create sale.");
                        }
                        saleId = generatedKeys.getInt(1);
                    }
                }

                SaleAuditService.recordSale(
                        conn,
                        saleId,
                        selectedCustomer == null ? null : selectedCustomer.customerId,
                        locationId,
                        "SALE_CREATED",
                        saleTotal,
                        "receipt=" + receipt.receiptNumber()
                                + "; payment_method=" + paymentMethod
                                + "; payment_status=" + paymentStatus
                                + (cashDrawer.drawerName() == null ? "" : "; cash_drawer=" + cashDrawer.drawerName())
                                + "; subtotal=" + subtotalAmount
                                + "; discount=" + discountAmount
                                + "; vat=" + vat.amount()
                                + (paymentReference.isBlank() ? "" : "; reference=" + paymentReference)
                );
                if (discountPercent.compareTo(BigDecimal.ZERO) > 0 || discountAmount.compareTo(BigDecimal.ZERO) > 0) {
                    SaleAuditService.record(
                            conn, saleId, null, null, null,
                            selectedCustomer == null ? null : selectedCustomer.customerId,
                            null, locationId,
                            "SALE_DISCOUNT_APPLIED", "SALE", "discount_percent",
                            BigDecimal.ZERO, discountPercent, discountAmount, null,
                            null, "Sale-level discount applied during checkout."
                    );
                }

                if (selectedCustomer != null) {
                    insertCustomerAccountTransaction(
                            conn,
                            selectedCustomer.customerId,
                            saleId,
                            chargeCustomerAccount ? saleTotal : BigDecimal.ZERO,
                            chargeCustomerAccount ? "SALE_CREDIT" : "SALE_PAID",
                            chargeCustomerAccount
                                    ? "Charged to account. sale_id=" + saleId
                                    : "Paid by " + paymentMethod + ". sale_id=" + saleId
                    );
                }
                SaleAuditService.recordSale(
                        conn,
                        saleId,
                        selectedCustomer == null ? null : selectedCustomer.customerId,
                        locationId,
                        chargeCustomerAccount ? "ACCOUNT_CHARGE_RECORDED" : "PAYMENT_RECORDED",
                        saleTotal,
                        chargeCustomerAccount
                                ? "Customer account charged. customer_id=" + selectedCustomer.customerId
                                : "Payment method=" + paymentMethod + (paymentReference.isBlank() ? "" : "; reference=" + paymentReference)
                );

                String insertItemSql = """
                        INSERT INTO sale_items (
                            sale_id,
                            product_id,
                            quantity,
                            unit_price,
                            original_unit_price,
                            discount_percent,
                            discount_amount,
                            price_override_reason,
                            price_override_by_user_id,
                            price_override_by_name,
                            product_type
                        )
	                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
	                        """;
	                String insertMovementSql = """
                            INSERT INTO inventory_movements (
                                product_id, location_id, change_qty, reason, note, user_name,
                                sale_id, sale_item_id, device_id, device_name, user_id
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """;
                String ensureInventorySql = "INSERT INTO inventory (product_id, location_id, quantity_on_hand, reorder_level) VALUES (?, ?, 0, 0) ON CONFLICT (product_id, location_id) DO NOTHING";
                String updateInventorySql = "UPDATE inventory SET quantity_on_hand = quantity_on_hand - ? WHERE product_id = ? AND location_id = ?";

                try (PreparedStatement itemStmt = conn.prepareStatement(insertItemSql, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement movementStmt = conn.prepareStatement(insertMovementSql);
                     PreparedStatement ensureInventoryStmt = conn.prepareStatement(ensureInventorySql);
                     PreparedStatement updateInventoryStmt = conn.prepareStatement(updateInventorySql)) {
                    java.util.List<String> lineOverrideApprovals = new java.util.ArrayList<>();

	                    for (int i = 0; i < cartModel.getRowCount(); i++) {
                        int productId = Integer.parseInt(cartModel.getValueAt(i, CART_COL_ID).toString());
                        int qty = Integer.parseInt(cartModel.getValueAt(i, CART_COL_QTY).toString());
                        if (qty <= 0) {
                            throw new SQLException("Quantity must be greater than zero for product " + cartModel.getValueAt(i, CART_COL_NAME) + ".");
                        }
                        String productType = getCartProductType(i);
                            BigDecimal catalogPrice = parseMoneyOrZero(cartModel.getValueAt(i, CART_COL_ORIGINAL_PRICE));
	                        BigDecimal enteredPrice = parseMoneyOrZero(cartModel.getValueAt(i, CART_COL_PRICE));
		                        BigDecimal itemDiscountPercent = parsePercentOrZero(cartModel.getValueAt(i, CART_COL_ITEM_DISCOUNT));
		                        BigDecimal itemDiscountMultiplier = BigDecimal.ONE.subtract(
		                                itemDiscountPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
		                        );
		                        BigDecimal itemDiscountedPrice = enteredPrice.multiply(itemDiscountMultiplier).setScale(2, RoundingMode.HALF_UP);
		                        BigDecimal chargedPrice = itemDiscountedPrice.multiply(saleDiscountMultiplier).setScale(2, RoundingMode.HALF_UP);
                            BigDecimal itemDiscountAmount = enteredPrice
		                                .multiply(BigDecimal.valueOf(qty))
		                                .multiply(itemDiscountPercent)
		                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                            boolean priceOverridden = enteredPrice.compareTo(catalogPrice) != 0;
                            String priceOverrideReason = null;
                            Integer priceOverrideByUserId = null;
                            String priceOverrideByName = null;
                            boolean priceOverrideApproved = false;
                            if (priceOverridden) {
                                if (!canChangeSaleItemPrice()) {
                                    PendingPriceApproval pendingApproval = pendingPriceOverrideApprovals.get(productId);
                                    if (pendingApproval == null || pendingApproval.approvedPrice().compareTo(enteredPrice) != 0) {
                                        throw new SQLException("Price override approval is required in cart for " + cartModel.getValueAt(i, CART_COL_NAME) + ".");
                                    }
                                    ManagerApprovalService.ApprovalResult approval = pendingApproval.approval();
                                    priceOverrideReason = approval.reason();
                                    priceOverrideByUserId = approval.approvedByUserId();
                                    priceOverrideByName = approval.approvedByName();
                                    priceOverrideApproved = true;
                                    lineOverrideApprovals.add(cartModel.getValueAt(i, CART_COL_NAME) + " by " + priceOverrideByName);
                                }
                            }

		                        itemStmt.setInt(1, saleId);
		                        itemStmt.setInt(2, productId);
		                        itemStmt.setInt(3, qty);
		                        itemStmt.setBigDecimal(4, chargedPrice);
		                        itemStmt.setBigDecimal(5, catalogPrice);
		                        itemStmt.setBigDecimal(6, itemDiscountPercent);
		                        itemStmt.setBigDecimal(7, itemDiscountAmount);
		                        itemStmt.setString(8, priceOverrideReason);
                                setNullableInteger(itemStmt, 9, priceOverrideByUserId);
                                itemStmt.setString(10, priceOverrideByName);
		                        itemStmt.setString(11, productType);
		                        itemStmt.executeUpdate();
                            int saleItemId;
                            try (ResultSet itemKeys = itemStmt.getGeneratedKeys()) {
                                if (!itemKeys.next()) {
                                    throw new SQLException("Failed to create sale item audit reference.");
                                }
                                saleItemId = itemKeys.getInt(1);
                            }

                            BigDecimal lineAmount = chargedPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
                            SaleAuditService.recordLine(
                                    conn,
                                    saleId,
                                    saleItemId,
                                    productId,
                                    locationId,
                                    "SALE_ITEM_ADDED",
                                    lineAmount,
                                    qty,
                                    "product=" + cartModel.getValueAt(i, CART_COL_NAME)
                                            + "; sku=" + cartModel.getValueAt(i, CART_COL_SKU)
                                            + "; product_type=" + productType
                            );
                            if (enteredPrice.compareTo(catalogPrice) != 0) {
                                SaleAuditService.record(
                                        conn, saleId, saleItemId, null, null, null, productId, locationId,
                                        "PRICE_OVERRIDE", "SALE_ITEM", "unit_price",
                                        catalogPrice, enteredPrice,
                                        enteredPrice.subtract(catalogPrice).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP),
                                        qty,
                                        null,
                                        "Manual line price change during sale."
                                );
                                if (priceOverrideApproved) {
                                    SaleAuditService.record(
                                            conn, saleId, saleItemId, null, null, null, productId, locationId,
                                            "PRICE_OVERRIDE_APPROVED", "SALE_ITEM", "price_override_by",
                                            null, priceOverrideByName,
                                            enteredPrice.subtract(catalogPrice).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP),
                                            qty,
                                            priceOverrideReason,
                                            "Price override approval captured."
                                    );
                                }
                            }
                            if (itemDiscountPercent.compareTo(BigDecimal.ZERO) > 0 || itemDiscountAmount.compareTo(BigDecimal.ZERO) > 0) {
                                if (!canApplySaleDiscount()) {
                                    PendingDiscountApproval discountApproval = pendingItemDiscountApprovals.get(productId);
                                    if (discountApproval == null || discountApproval.approvedDiscountPercent().compareTo(itemDiscountPercent) != 0) {
                                        throw new SQLException("Item discount approval is required in cart for " + cartModel.getValueAt(i, CART_COL_NAME) + ".");
                                    }
                                }
                                SaleAuditService.record(
                                        conn, saleId, saleItemId, null, null, null, productId, locationId,
                                        "ITEM_DISCOUNT_APPLIED", "SALE_ITEM", "discount_percent",
                                        BigDecimal.ZERO, itemDiscountPercent,
                                        itemDiscountAmount, qty,
                                        null,
                                        "Line item discount applied during sale."
                                );
                            }

                        if (isInventoryProduct(productType)) {
                            ensureInventoryStmt.setInt(1, productId);
                            ensureInventoryStmt.setInt(2, locationId);
                            ensureInventoryStmt.executeUpdate();

                            updateInventoryStmt.setInt(1, qty);
                            updateInventoryStmt.setInt(2, productId);
                            updateInventoryStmt.setInt(3, locationId);
                            updateInventoryStmt.executeUpdate();

                            movementStmt.setInt(1, productId);
                            movementStmt.setInt(2, locationId);
                            movementStmt.setInt(3, -qty);
                            movementStmt.setString(4, "SALE");
                            movementStmt.setString(5, "sale_id=" + saleId);
                            movementStmt.setString(6, SessionManager.getCurrentUserDisplayName());
                            movementStmt.setInt(7, saleId);
                            movementStmt.setInt(8, saleItemId);
                            movementStmt.setString(9, DeviceContextService.currentDeviceId());
                            movementStmt.setString(10, DeviceContextService.currentDeviceName());
                            setNullableInteger(movementStmt, 11, SessionManager.getCurrentUserId());
                            movementStmt.executeUpdate();
                            SaleAuditService.recordLine(
                                    conn,
                                    saleId,
                                    saleItemId,
                                    productId,
                                    locationId,
                                    "INVENTORY_DEDUCTED",
                                    null,
                                    -qty,
                                    "Inventory deducted for sale."
                            );
                        }
                    }
                    if (!lineOverrideApprovals.isEmpty() && overrideStatusLabel != null) {
                        overrideStatusLabel.setText("Price override approvals: " + String.join("; ", lineOverrideApprovals));
                    }
                }

                if (saleDiscountOverrideReason != null) {
                    SaleAuditService.record(
                            conn, saleId, null, null, null,
                            selectedCustomer == null ? null : selectedCustomer.customerId,
                            null, locationId,
                            "SALE_DISCOUNT_OVERRIDE", "SALE", "discount_override_by",
                            null, saleDiscountOverrideByName,
                            discountAmount, null,
                            saleDiscountOverrideReason,
                            "Discount override approval captured."
                    );
                }

                SyncOutboxService.recordEvent(conn, "SALE_COMPLETED", Map.of(
                        "sale_id", saleId,
                        "receipt_number", receipt.receiptNumber(),
                        "location_id", locationId,
                        "user_id", SessionManager.getCurrentUserId(),
                        "device_id", String.valueOf(DeviceContextService.currentDeviceId()),
                        "payment_method", paymentMethod,
                        "payment_status", paymentStatus,
                        "total_amount", saleTotal,
                        "cash_drawer_session_id", cashDrawer.sessionId() == null ? "" : cashDrawer.sessionId()
                ));
                conn.commit();
	                String successMessage = "Sale completed successfully.\nReceipt #: " + receipt.receiptNumber() + "\nSale ID: " + saleId;
                    if (cashDrawer.drawerName() != null && !cashDrawer.drawerName().isBlank()) {
                        successMessage += "\nCash Drawer: " + cashDrawer.drawerName();
                    }
	                BigDecimal changeDue = BigDecimal.ZERO;
	                if (cashPayment) {
	                    changeDue = cashCollected.subtract(saleTotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
	                    successMessage += "\nCash Collected: $" + cashCollected.setScale(2, RoundingMode.HALF_UP)
	                            + "\nChange Due: $" + changeDue;
	                }
	                if (showReceiptPreview) {
	                    try {
	                        ReceiptData receiptData = ReceiptBuilder.loadSaleReceipt(
	                                saleId,
	                                cashPayment ? cashCollected : null,
	                                cashPayment ? changeDue : null
	                        );
	                        WindowHelper.showPosWindow(new ReceiptPreview(receiptData), this);
	                    } catch (SQLException receiptEx) {
	                        JOptionPane.showMessageDialog(
	                                this,
	                                successMessage + "\n\nReceipt preview failed: " + receiptEx.getMessage(),
	                                "Receipt Preview",
	                                JOptionPane.WARNING_MESSAGE
	                        );
	                    }
	                } else {
	                    JOptionPane.showMessageDialog(this, successMessage);
	                }
		                cartModel.setRowCount(0);
                        pendingPriceOverrideApprovals.clear();
                        pendingItemDiscountApprovals.clear();
		                discountPercentField.setText("0");
		                clearHeldCartSelection();
                    if (paymentReferenceField != null) {
                        paymentReferenceField.setText("");
                    }
                configureCartTableColumns();
                searchField.setText("");
                loadCustomerAccounts();
                updateOverallTotal();

            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Checkout failed: " + ex.getMessage());
        }
    }

    private void holdCurrentCart() {
        if (!PermissionManager.requirePermission(HOLD_CART_PERMISSION, this, "Hold Cart")) {
            refreshPermissionButtons();
            return;
        }
        if (cartModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Cart is empty.");
            return;
        }
        if (SessionManager.getCurrentLocationId() == null) {
            JOptionPane.showMessageDialog(this, "No store is selected for this session.");
            return;
        }

        String holdName = JOptionPane.showInputDialog(this, "Hold name / note:", "Held Cart");
        if (holdName == null) {
            return;
        }
        holdName = holdName.trim();
        if (holdName.isBlank()) {
            JOptionPane.showMessageDialog(this, "Hold name is required.");
            return;
        }

	        CustomerAccountOption selectedCustomer = getSelectedCustomerAccount();
        BigDecimal subtotalAmount = getCartGrossSubtotal();
        BigDecimal lineSubtotalAfterItemDiscounts = BigDecimal.valueOf(getCartSubtotal()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal itemDiscountTotal = getItemDiscountTotal();
        BigDecimal discountPercent = parseDiscountPercentOrShowError();
        if (discountPercent == null) {
            return;
        }
        BigDecimal saleLevelDiscountAmount = lineSubtotalAfterItemDiscounts.multiply(discountPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = itemDiscountTotal.add(saleLevelDiscountAmount).setScale(2, RoundingMode.HALF_UP);
	        String insertHoldSql = """
	                INSERT INTO held_carts (
	                    location_id,
                    user_id,
                    user_name,
	                    customer_id,
	                    hold_name,
	                    payment_method,
	                    subtotal_amount,
	                    discount_percent,
	                    discount_amount,
	                    total_amount,
	                    status
	                )
	                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN')
	                RETURNING held_cart_id
	                """;
        String insertItemSql = """
                INSERT INTO held_cart_items (
                    held_cart_id,
                    product_id,
                    product_name,
                    description,
	                    sku,
	                    unit_price,
	                    quantity,
	                    discount_percent,
	                    product_type
	                )
	                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
	                """;

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int heldCartId;
                try (PreparedStatement holdStmt = conn.prepareStatement(insertHoldSql)) {
                    holdStmt.setInt(1, SessionManager.getCurrentLocationId());
                    setNullableInteger(holdStmt, 2, SessionManager.getCurrentUserId());
                    holdStmt.setString(3, SessionManager.getCurrentUserDisplayName());
                    if (selectedCustomer == null) {
                        holdStmt.setNull(4, java.sql.Types.INTEGER);
                    } else {
                        holdStmt.setInt(4, selectedCustomer.customerId);
	                    }
                    holdStmt.setString(5, holdName);
	                    holdStmt.setString(6, selectedPaymentMethod == null ? "" : selectedPaymentMethod);
	                    holdStmt.setBigDecimal(7, subtotalAmount);
	                    holdStmt.setBigDecimal(8, discountPercent);
	                    holdStmt.setBigDecimal(9, discountAmount);
		                    holdStmt.setBigDecimal(10, lineSubtotalAfterItemDiscounts.subtract(saleLevelDiscountAmount).max(BigDecimal.ZERO));
	                    try (ResultSet rs = holdStmt.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Failed to create held cart.");
                        }
                        heldCartId = rs.getInt("held_cart_id");
                    }
                }

                try (PreparedStatement itemStmt = conn.prepareStatement(insertItemSql)) {
                    for (int i = 0; i < cartModel.getRowCount(); i++) {
                        itemStmt.setInt(1, heldCartId);
	                        itemStmt.setInt(2, Integer.parseInt(String.valueOf(cartModel.getValueAt(i, CART_COL_ID))));
	                        itemStmt.setString(3, String.valueOf(cartModel.getValueAt(i, CART_COL_NAME)));
	                        itemStmt.setString(4, String.valueOf(cartModel.getValueAt(i, CART_COL_DESCRIPTION)));
	                        itemStmt.setString(5, String.valueOf(cartModel.getValueAt(i, CART_COL_SKU)));
	                        BigDecimal heldUnitPrice = canChangeSaleItemPrice()
	                                ? parseMoneyOrZero(cartModel.getValueAt(i, CART_COL_PRICE))
	                                : parseMoneyOrZero(cartModel.getValueAt(i, CART_COL_ORIGINAL_PRICE));
	                        itemStmt.setBigDecimal(6, heldUnitPrice);
	                        itemStmt.setInt(7, Integer.parseInt(String.valueOf(cartModel.getValueAt(i, CART_COL_QTY))));
	                        itemStmt.setBigDecimal(8, parsePercentOrZero(cartModel.getValueAt(i, CART_COL_ITEM_DISCOUNT)));
	                        itemStmt.setString(9, getCartProductType(i));
	                        itemStmt.addBatch();
                    }
                    itemStmt.executeBatch();
                }
                SaleAuditService.recordHeldCart(
                        conn,
                        SessionManager.getCurrentLocationId(),
                        "HELD_CART_CREATED",
                        cartModel.getRowCount(),
                        lineSubtotalAfterItemDiscounts.subtract(saleLevelDiscountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                        "held_cart_id=" + heldCartId
                                + "; hold_name=" + holdName.trim()
                                + "; payment_method=" + (selectedPaymentMethod == null ? "" : selectedPaymentMethod)
                                + "; customer_id=" + (selectedCustomer == null ? "" : selectedCustomer.customerId)
                );

                conn.commit();
	                JOptionPane.showMessageDialog(this, "Cart held successfully. Hold ID: " + heldCartId);
		                cartModel.setRowCount(0);
                        pendingPriceOverrideApprovals.clear();
                        pendingItemDiscountApprovals.clear();
		                clearHeldCartSelection();
                configureCartTableColumns();
                updateOverallTotal();
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to hold cart: " + ex.getMessage(), "Hold Cart", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resumeHeldCart() {
        if (!PermissionManager.requirePermission(RESUME_HOLD_PERMISSION, this, "Resume Held Cart")) {
            refreshPermissionButtons();
            return;
        }
        if (SessionManager.getCurrentLocationId() == null) {
            JOptionPane.showMessageDialog(this, "No store is selected for this session.");
            return;
        }
        if (cartModel.getRowCount() > 0) {
            int result = JOptionPane.showConfirmDialog(
                    this,
                    "Replace the current cart with a held cart?",
                    "Resume Held Cart",
                    JOptionPane.YES_NO_OPTION
            );
            if (result != JOptionPane.YES_OPTION) {
                return;
            }
        }

        HeldCartOption selectedHold = selectHeldCart();
        if (selectedHold == null) {
            return;
        }

        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                loadHeldCartIntoCurrentCart(conn, selectedHold.heldCartId());
                SaleAuditService.recordHeldCart(
                        conn,
                        SessionManager.getCurrentLocationId(),
                        "HELD_CART_RESUMED",
                        cartModel.getRowCount(),
                        BigDecimal.valueOf(getOverallTotal()).setScale(2, RoundingMode.HALF_UP),
                        "held_cart_id=" + selectedHold.heldCartId()
                );
                deleteHeldCart(conn, selectedHold.heldCartId());
                conn.commit();
                JOptionPane.showMessageDialog(this, "Held cart resumed.");
            } catch (Exception ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to resume held cart: " + ex.getMessage(), "Resume Held Cart", JOptionPane.ERROR_MESSAGE);
        }
    }

    private HeldCartOption selectHeldCart() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"Hold ID", "Held At", "Hold Name", "Cashier", "Customer", "Items", "Total"},
                0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        String sql = """
                SELECT hc.held_cart_id,
                       (hc.created_at AT TIME ZONE ?) AS local_created_at,
                       COALESCE(hc.hold_name, '') AS hold_name,
                       COALESCE(hc.user_name, '') AS user_name,
                       COALESCE(ca.name, '') AS customer_name,
                       COUNT(hci.held_cart_item_id) AS item_count,
                       COALESCE(hc.total_amount, 0) AS total_amount
                FROM held_carts hc
                LEFT JOIN held_cart_items hci ON hci.held_cart_id = hc.held_cart_id
                LEFT JOIN customer_accounts ca ON ca.customer_id = hc.customer_id
                WHERE hc.location_id = ?
                  AND UPPER(COALESCE(hc.status, 'OPEN')) = 'OPEN'
                GROUP BY hc.held_cart_id, hc.created_at, hc.hold_name, hc.user_name, ca.name, hc.total_amount
                ORDER BY hc.created_at DESC
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, StoreTimeZoneHelper.getStoreZoneId());
            ps.setInt(2, SessionManager.getCurrentLocationId());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    model.addRow(new Object[]{
                            rs.getInt("held_cart_id"),
                            StoreTimeZoneHelper.formatLocalTimestamp(
                                    rs.getTimestamp("local_created_at"),
                                    DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a")
                            ),
                            rs.getString("hold_name"),
                            rs.getString("user_name"),
                            rs.getString("customer_name"),
                            rs.getInt("item_count"),
                            "$" + rs.getBigDecimal("total_amount")
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Failed to load held carts: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        if (model.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "There are no held carts for this store.");
            return null;
        }

        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowSelectionInterval(0, 0);
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(780, 280));

        int result = JOptionPane.showConfirmDialog(
                this,
                scrollPane,
                "Select Held Cart",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION || table.getSelectedRow() < 0) {
            return null;
        }

        int modelRow = table.convertRowIndexToModel(table.getSelectedRow());
        return new HeldCartOption(Integer.parseInt(String.valueOf(model.getValueAt(modelRow, 0))));
    }

    private void loadHeldCartIntoCurrentCart(Connection conn, int heldCartId) throws SQLException {
        String holdSql = "SELECT customer_id, payment_method, COALESCE(discount_percent, 0) AS discount_percent FROM held_carts WHERE held_cart_id = ? AND location_id = ? AND UPPER(COALESCE(status, 'OPEN')) = 'OPEN' FOR UPDATE";
        Integer customerId = null;
        String paymentMethod = null;
        BigDecimal discountPercent = BigDecimal.ZERO;
        try (PreparedStatement ps = conn.prepareStatement(holdSql)) {
            ps.setInt(1, heldCartId);
            ps.setInt(2, SessionManager.getCurrentLocationId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Held cart is no longer available.");
                }
                int loadedCustomerId = rs.getInt("customer_id");
                if (!rs.wasNull()) {
                    customerId = loadedCustomerId;
	                }
	                paymentMethod = rs.getString("payment_method");
	                discountPercent = rs.getBigDecimal("discount_percent");
	            }
	        }

        String itemsSql = """
                SELECT hci.product_id,
                       hci.product_name,
                       hci.description,
                       hci.sku,
                       hci.unit_price,
                       hci.quantity,
                       COALESCE(discount_percent, 0) AS discount_percent,
                       COALESCE(hci.product_type, 'INVENTORY') AS product_type,
                       p.category_id
                FROM held_cart_items hci
                LEFT JOIN products p ON p.product_id = hci.product_id
                WHERE hci.held_cart_id = ?
                ORDER BY hci.held_cart_item_id
                """;

        cartModel.setRowCount(0);
        try (PreparedStatement ps = conn.prepareStatement(itemsSql)) {
            ps.setInt(1, heldCartId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
	                    double price = rs.getBigDecimal("unit_price").doubleValue();
	                    int qty = rs.getInt("quantity");
	                    BigDecimal itemDiscountPercent = rs.getBigDecimal("discount_percent");
	                    BigDecimal lineGross = BigDecimal.valueOf(price).multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
	                    BigDecimal lineDiscount = lineGross.multiply(itemDiscountPercent == null ? BigDecimal.ZERO : itemDiscountPercent)
	                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
	                    cartModel.addRow(new Object[]{
	                            rs.getInt("product_id"),
	                            rs.getString("product_name"),
	                            rs.getString("description"),
	                            rs.getString("sku"),
	                            price,
	                            qty,
	                            itemDiscountPercent == null ? BigDecimal.ZERO : itemDiscountPercent,
	                            lineGross.subtract(lineDiscount).max(BigDecimal.ZERO),
	                            BigDecimal.valueOf(price).setScale(2, RoundingMode.HALF_UP),
	                            normalizeProductType(rs.getString("product_type")),
                                rs.getObject("category_id")
	                    });
                }
            }
        }

        if (cartModel.getRowCount() == 0) {
            throw new SQLException("Held cart has no items.");
        }

	        if (paymentMethod != null && !paymentMethod.isBlank()) {
	            selectPaymentMethod(paymentMethod);
	        }
	        if (discountPercentField != null) {
	            discountPercentField.setText(discountPercent == null ? "0" : discountPercent.stripTrailingZeros().toPlainString());
	        }
	        selectCustomerById(customerId);
        configureCartTableColumns();
        updateOverallTotal();
    }

    private void deleteHeldCart(Connection conn, int heldCartId) throws SQLException {
        String sql = """
                UPDATE held_carts
                SET status = 'RESUMED',
                    resumed_at = CURRENT_TIMESTAMP,
                    resumed_by_user_id = ?,
                    resumed_by_name = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE held_cart_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            Integer userId = SessionManager.getCurrentUserId();
            if (userId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
            } else {
                ps.setInt(1, userId);
            }
            ps.setString(2, SessionManager.getCurrentUserDisplayName());
            ps.setInt(3, heldCartId);
            ps.executeUpdate();
        }
    }

    private void selectCustomerById(Integer customerId) {
        if (customerId == null) {
            customerAccountBox.setSelectedItem("");
            if (customerAccountBox.getEditor() != null) {
                customerAccountBox.getEditor().setItem("");
            }
            return;
        }
        applyCustomerAccountFilter("", false);
        for (int i = 0; i < customerAccountOptions.size(); i++) {
            CustomerAccountOption option = customerAccountOptions.get(i);
            if (option.customerId == customerId) {
                customerAccountBox.setSelectedItem(option);
                return;
            }
        }
    }

    private void clearHeldCartSelection() {
        pendingPriceOverrideApprovals.clear();
        pendingItemDiscountApprovals.clear();
        applyCustomerAccountFilter("", false);
        customerAccountBox.setSelectedItem("");
        if (customerAccountBox.getEditor() != null) {
            customerAccountBox.getEditor().setItem("");
        }
        selectedPaymentMethod = null;
        if (paymentMethodGroup != null) {
            paymentMethodGroup.clearSelection();
        }
        updatePaymentReferenceEnabled();
        updatePaymentButtonStyles();
        updateCheckoutAvailability();
        if (discountPercentField != null) {
            discountPercentField.setText("0");
        }
    }

    private static void setNullableInteger(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.INTEGER);
        } else {
            ps.setInt(index, value);
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, java.sql.Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }

    private void validateAndChargeCustomerAccount(Connection conn, int customerId, BigDecimal saleTotal) throws SQLException {
        CustomerAccountLedgerService.repairCustomerBalance(conn, customerId);
        String lockSql = """
                SELECT current_balance, credit_limit, is_active
                FROM customer_accounts
                WHERE customer_id = ?
                FOR UPDATE
                """;

        BigDecimal currentBalance;
        BigDecimal creditLimit;
        boolean active;

        try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
            ps.setInt(1, customerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Customer account was not found.");
                }
                currentBalance = rs.getBigDecimal("current_balance");
                creditLimit = rs.getBigDecimal("credit_limit");
                active = rs.getBoolean("is_active");
            }
        }

        if (!active) {
            throw new SQLException("Customer account is inactive.");
        }

        BigDecimal newBalance = currentBalance.add(saleTotal);
        if (newBalance.compareTo(creditLimit) > 0) {
            throw new SQLException("Account payment exceeds customer credit limit. Available credit: $" + creditLimit.subtract(currentBalance));
        }

        try (PreparedStatement ps = conn.prepareStatement("UPDATE customer_accounts SET current_balance = ? WHERE customer_id = ?")) {
            ps.setBigDecimal(1, newBalance);
            ps.setInt(2, customerId);
            ps.executeUpdate();
        }
    }

    private void insertCustomerAccountTransaction(Connection conn, int customerId, int saleId, BigDecimal amount, String type, String note) throws SQLException {
        String sql = """
	                INSERT INTO customer_account_transactions (
	                    customer_id, sale_id, location_id, amount, transaction_type, note, user_name, device_id, device_name
	                )
	                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, saleId);
            setNullableInteger(ps, 3, SessionManager.getCurrentLocationId());
	            ps.setBigDecimal(4, amount);
	            ps.setString(5, type);
	            ps.setString(6, note);
	            ps.setString(7, SessionManager.getCurrentUserDisplayName());
                ps.setString(8, DeviceContextService.currentDeviceId());
                ps.setString(9, DeviceContextService.currentDeviceName());
	            ps.executeUpdate();
        }
        SaleAuditService.record(
                conn, saleId, null, null, null, customerId, null, SessionManager.getCurrentLocationId(),
                "CUSTOMER_ACCOUNT_TRANSACTION", "CUSTOMER_ACCOUNT", "amount",
                null, amount, amount, null, null,
                "type=" + type + "; " + note
        );
    }

    private void repairSalesSequence(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT setval(
                    pg_get_serial_sequence('sales', 'sale_id'),
                    COALESCE((SELECT MAX(sale_id) FROM sales), 1),
                    COALESCE((SELECT MAX(sale_id) FROM sales), 0) > 0
                )
                """)) {
            ps.executeQuery();
        }
    }

    private void updateOverallTotal() {
        BigDecimal subtotal = getCartGrossSubtotal();
        BigDecimal afterItemDiscounts = BigDecimal.valueOf(getCartSubtotal()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal itemDiscountAmount = getItemDiscountTotal();
        BigDecimal saleDiscountAmount = getDiscountAmount(afterItemDiscounts);
        BigDecimal discountAmount = itemDiscountAmount.add(saleDiscountAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal vatAmount = BigDecimal.ZERO;
        BigDecimal total = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);
        try (Connection conn = DB.getConnection()) {
            VatCalculation vat = calculateVat(conn, getDiscountPercent());
            vatAmount = vat.amount();
            total = total.add(vatAmount).setScale(2, RoundingMode.HALF_UP);
        } catch (SQLException ex) {
            total = total.setScale(2, RoundingMode.HALF_UP);
        }

        if (subtotalLabel != null) {
            subtotalLabel.setText(String.format("Subtotal: $%.2f", subtotal.doubleValue()));
        }
        if (discountAmountLabel != null) {
            discountAmountLabel.setText(String.format("Discount: $%.2f", discountAmount.doubleValue()));
        }
        if (vatAmountLabel != null) {
            vatAmountLabel.setText(String.format("VAT: $%.2f", vatAmount.doubleValue()));
        }
        totalLabel.setText(String.format("Overall Total: $%.2f", total.doubleValue()));
    }

    private BigDecimal promptForCashCollected(BigDecimal amountDue) {
        BigDecimal due = amountDue == null ? BigDecimal.ZERO : amountDue.setScale(2, RoundingMode.HALF_UP);

        JDialog dialog = new JDialog(this, "Cash Checkout", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new BorderLayout(14, 14));
        content.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel amountDueValue = new JLabel("$" + due.toPlainString());
        amountDueValue.setFont(amountDueValue.getFont().deriveFont(Font.BOLD, 18f));
        JTextField collectedField = new JTextField(due.toPlainString(), 12);
        JLabel changeLabel = new JLabel("Change: $0.00");
        changeLabel.setFont(changeLabel.getFont().deriveFont(Font.BOLD, 16f));

        gbc.gridx = 0;
        gbc.gridy = 0;
        fieldsPanel.add(new JLabel("Amount Due:"), gbc);
        gbc.gridx = 1;
        fieldsPanel.add(amountDueValue, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        fieldsPanel.add(new JLabel("Cash Collected:"), gbc);
        gbc.gridx = 1;
        fieldsPanel.add(collectedField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        fieldsPanel.add(new JLabel("Result:"), gbc);
        gbc.gridx = 1;
        fieldsPanel.add(changeLabel, gbc);

        JButton doneButton = new JButton("Done");
        JButton cancelButton = new JButton("Cancel");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(cancelButton);
        buttonPanel.add(doneButton);

        content.add(fieldsPanel, BorderLayout.CENTER);
        content.add(buttonPanel, BorderLayout.SOUTH);
        dialog.setContentPane(content);

        final BigDecimal[] result = new BigDecimal[1];

        Runnable updateChange = () -> {
            try {
                String text = collectedField.getText().trim();
                BigDecimal collected = text.isEmpty()
                        ? BigDecimal.ZERO
                        : new BigDecimal(text).setScale(2, RoundingMode.HALF_UP);
                if (collected.compareTo(BigDecimal.ZERO) < 0) {
                    changeLabel.setText("Cash collected cannot be negative.");
                    doneButton.setEnabled(false);
                    return;
                }

                BigDecimal difference = collected.subtract(due).setScale(2, RoundingMode.HALF_UP);
                if (difference.compareTo(BigDecimal.ZERO) < 0) {
                    changeLabel.setText("Short: $" + difference.abs().toPlainString());
                    doneButton.setEnabled(false);
                } else {
                    changeLabel.setText("Change: $" + difference.toPlainString());
                    doneButton.setEnabled(true);
                }
            } catch (NumberFormatException ex) {
                changeLabel.setText("Enter a valid cash amount.");
                doneButton.setEnabled(false);
            }
        };

        collectedField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(updateChange);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(updateChange);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                SwingUtilities.invokeLater(updateChange);
            }
        });

        doneButton.addActionListener(e -> {
            try {
                result[0] = new BigDecimal(collectedField.getText().trim()).setScale(2, RoundingMode.HALF_UP);
                dialog.dispose();
            } catch (NumberFormatException ex) {
                changeLabel.setText("Enter a valid cash amount.");
                doneButton.setEnabled(false);
            }
        });
        cancelButton.addActionListener(e -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(doneButton);

        updateChange.run();
        dialog.pack();
        dialog.setMinimumSize(new Dimension(360, dialog.getHeight()));
        dialog.setLocationRelativeTo(this);
        SwingUtilities.invokeLater(() -> {
            collectedField.requestFocusInWindow();
            collectedField.selectAll();
        });
        dialog.setVisible(true);
        return result[0];
    }

    private static class PromptTextField extends JTextField {
        private String prompt;

        private PromptTextField(String prompt) {
            this.prompt = prompt == null ? "" : prompt;
        }

        private void setPrompt(String prompt) {
            this.prompt = prompt == null ? "" : prompt;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (!getText().isEmpty() || prompt.isBlank()) {
                return;
            }

            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setColor(DeckersPalette.muted());
            g2.setFont(getFont());
            Insets insets = getInsets();
            FontMetrics metrics = g2.getFontMetrics();
            int y = ((getHeight() - metrics.getHeight()) / 2) + metrics.getAscent();
            g2.drawString(prompt, insets.left, y);
            g2.dispose();
        }
    }

    private static class PromptComboBoxEditor extends javax.swing.plaf.basic.BasicComboBoxEditor {
        private final PromptTextField promptField;

        private PromptComboBoxEditor(String prompt) {
            promptField = new PromptTextField(prompt);
            editor = promptField;
        }

        @Override
        public Component getEditorComponent() {
            return promptField;
        }
    }

    private static class CustomerAccountOption {
        private final int customerId;
        private final String accountNumber;
        private final String name;
        private final BigDecimal creditLimit;
        private final BigDecimal currentBalance;
        private final BigDecimal availableCredit;
        private final boolean businessAccount;
        private final String customerTypeName;

        private CustomerAccountOption(int customerId, String accountNumber, String name, BigDecimal creditLimit, BigDecimal currentBalance, BigDecimal availableCredit, boolean businessAccount, String customerTypeName) {
            this.customerId = customerId;
            this.accountNumber = accountNumber == null ? "" : accountNumber;
            this.name = name == null ? "" : name;
            this.creditLimit = creditLimit == null ? BigDecimal.ZERO : creditLimit;
            this.currentBalance = currentBalance == null ? BigDecimal.ZERO : currentBalance;
            this.availableCredit = availableCredit == null ? BigDecimal.ZERO : availableCredit;
            this.businessAccount = businessAccount;
            this.customerTypeName = customerTypeName == null ? "" : customerTypeName;
        }

        @Override
        public String toString() {
            String accountLabel = accountNumber.isBlank() ? "" : accountNumber + " - ";
            String typeLabel = businessAccount ? "Business" : "Personal";
            String customerTypeLabel = customerTypeName.isBlank() ? "" : " / " + customerTypeName;
            return accountLabel + name + " [" + typeLabel + customerTypeLabel + "] (Available: $" + availableCredit + ")";
        }

        private boolean matches(String filter) {
            String normalized = filter == null ? "" : filter.trim().toLowerCase();
            if (normalized.isBlank()) {
                return true;
            }
            return accountNumber.toLowerCase().contains(normalized)
                    || name.toLowerCase().contains(normalized)
                    || toString().toLowerCase().contains(normalized);
        }

        private boolean matchesExact(String value) {
            String normalized = value == null ? "" : value.trim();
            return normalized.equalsIgnoreCase(toString())
                    || normalized.equalsIgnoreCase(accountNumber)
                    || normalized.equalsIgnoreCase(name);
        }
    }

    private record HeldCartOption(int heldCartId) {
    }

}
