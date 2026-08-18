package ui.screens;

import Receipt.ReceiptBuilder;
import Receipt.ReceiptData;
import Receipt.ReceiptPrinter;
import managers.CompanyCustomizationManager;
import managers.HardwareSettingsManager;
import managers.PermissionManager;
import managers.SessionManager;
import services.ManagerApprovalService;
import services.LanApiClient;
import services.ProductSearchHelper;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.UiTaskRunner;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EventObject;
import java.util.Map;
import java.util.UUID;


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
    private JButton quickPickItemsBtn;
    private JButton removeCartItemBtn;
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
    private JButton storeStockBtn;
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
    private long productSearchGeneration;
    private long identifierLookupGeneration;
    private String searchResultsQuery = "";
    private boolean resettingProductSearch;
    private int cachedProductLocationId = -1;
    private boolean suppressDiscountFieldEvents = false;
    private record PendingPriceApproval(BigDecimal approvedPrice, ManagerApprovalService.ApprovalResult approval) {}
    private record PendingDiscountApproval(BigDecimal approvedDiscountPercent, ManagerApprovalService.ApprovalResult approval) {}
    private record PendingSaleDiscountApproval(String permission,
                                               ManagerApprovalService.ApprovalResult approval) {}
    private record VatCalculation(BigDecimal amount, BigDecimal ratePercent, String mode) {
        private VatCalculation {
            amount = utils.CurrencyFormatter.normalize(amount);
            ratePercent = ratePercent == null ? BigDecimal.ZERO : ratePercent.setScale(2, RoundingMode.HALF_UP);
            mode = mode == null ? "" : mode;
        }
    }
    private final java.util.Map<Integer, PendingPriceApproval> pendingPriceOverrideApprovals = new java.util.HashMap<>();
    private final java.util.Map<Integer, PendingDiscountApproval> pendingItemDiscountApprovals = new java.util.HashMap<>();
    private PendingSaleDiscountApproval pendingSaleDiscountApproval;
    private String lastAcceptedSaleDiscountText = "0";
    private java.util.List<CustomerAccountOption> customerAccountOptions = new java.util.ArrayList<>();
    private boolean updatingCustomerAccountFilter = false;
    private LanApiClient.SalesSettings salesSettings = new LanApiClient.SalesSettings(
            false, false, BigDecimal.ZERO, BigDecimal.valueOf(5), java.util.List.of());
    private boolean salesSettingsLoaded;
    private boolean alwaysPrintSaleReceipt;
    private String pendingCheckoutKey;
    private String pendingCheckoutFingerprint;
    private String pendingHoldKey;
    private String pendingHoldFingerprint;
    private Integer pendingResumeHeldCartId;
    private String pendingResumeKey;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();
    private boolean saleUiInitialized;

   public MakeASale() {

       //Window Setup
       setTitle("Make a Sale");
      // setDefaultCloseOperation(DISPOSE_ON_CLOSE);
      // setExtendedState(JFrame.MAXIMIZED_BOTH);
       setSize(1000, 600);
       setLocationRelativeTo(null);
       setDefaultCloseOperation(DISPOSE_ON_CLOSE);
       setJMenuBar(AppMenuBar.create(this, "MakeASale", loadingState));

       JPanel initialShell = new JPanel(new BorderLayout());
       JLabel initialStatus = new JLabel("Preparing point of sale...", SwingConstants.CENTER);
       initialStatus.setFont(new Font("SansSerif", Font.PLAIN, 16));
       initialShell.add(initialStatus, BorderLayout.CENTER);
       add(initialShell);
       WindowHelper.configurePosWindow(this);
       addWindowListener(new java.awt.event.WindowAdapter() {
           @Override
           public void windowOpened(java.awt.event.WindowEvent event) {
               if (saleUiInitialized) return;
               javax.swing.Timer buildTimer = new javax.swing.Timer(50, ignored -> {
                   if (!isDisplayable() || saleUiInitialized) return;
                   saleUiInitialized = true;
                   remove(initialShell);

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

       newItemBtn = createUtilityButton("New Item", DeckersPalette.LIME);
       searchField = new PromptTextField("Scan or enter item information");
       DeckersSwing.styleField(searchField);
       setFixedControlHeight(searchField, 0);
       searchField.putClientProperty("JTextField.placeholderText", "Scan or enter item information");
       productDropdownButton = createProductDropdownButton();
       selectedStoreLabel = createMetaLabel("Store: Not selected");
       currentUserLabel = createMetaLabel("No User currently logged in");
       editItemBtn = createUtilityButton("Edit Item", DeckersPalette.PURPLE);
       storeStockBtn = createUtilityButton("Store Stock", DeckersPalette.MAGENTA);
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
       leftSidePanel.add(storeStockBtn);

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
	       cartTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
	       cartTable.getColumnModel().getColumn(CART_COL_PRICE).setCellEditor(
                   createApprovalAwareCartEditor(CART_COL_PRICE));
	       cartTable.getColumnModel().getColumn(CART_COL_QTY).setCellEditor(new DefaultCellEditor(new JTextField()));
	       cartTable.getColumnModel().getColumn(CART_COL_ITEM_DISCOUNT).setCellEditor(
                   createApprovalAwareCartEditor(CART_COL_ITEM_DISCOUNT));
       cartTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
               .put(KeyStroke.getKeyStroke("DELETE"), "remove-selected-cart-items");
       cartTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
               .put(KeyStroke.getKeyStroke("BACK_SPACE"), "remove-selected-cart-items");
       cartTable.getActionMap().put("remove-selected-cart-items", new AbstractAction() {
           @Override
           public void actionPerformed(ActionEvent event) {
               removeSelectedCartItems();
           }
       });
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
       paymentReferenceField.setToolTipText("Required for card, cheque, and MMG payments.");
       paymentReferenceField.setEnabled(false);
       DeckersSwing.styleField(paymentReferenceField);
       setFixedControlHeight(paymentReferenceField, 170);
	       discountPercentField = new JTextField("0", 5);
       DeckersSwing.styleField(discountPercentField);
	       if (!canApplySaleDiscount()) {
	           discountPercentField.setToolTipText("A manager approval is required to apply a sale discount.");
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
	       subtotalLabel = createTotalLabel("Subtotal: $0", false);
	       totalsPanel.add(subtotalLabel);
	       discountAmountLabel = createTotalLabel("Discount: $0", false);
	       totalsPanel.add(discountAmountLabel);
	       vatAmountLabel = createTotalLabel("VAT: $0", false);
	       totalsPanel.add(vatAmountLabel);
	       totalLabel = createTotalLabel("Overall Total: $0", true);
	       totalsPanel.add(totalLabel);

       JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
       actionPanel.setOpaque(false);
       checkoutBtn = createCheckoutButton("Checkout");
       checkoutPrintBtn = createCheckoutButton("Checkout & Print");
       holdCartBtn = createActionUtilityButton("Hold Cart");
       resumeHeldCartBtn = createActionUtilityButton("Resume Hold");
       quickPickItemsBtn = createActionUtilityButton("Quick Pick Items");
       removeCartItemBtn = createActionUtilityButton("Remove Item");
       removeCartItemBtn.setToolTipText("Remove the selected cart item. You can also press Delete.");
       removeCartItemBtn.setEnabled(false);
       actionPanel.add(quickPickItemsBtn);
       actionPanel.add(removeCartItemBtn);
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
       quickPickItemsBtn.addActionListener(e -> showServiceQuickPick());
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
       storeStockBtn.addActionListener(e -> {
           if (!PermissionManager.hasPermission("VIEW_INVENTORY")
                   || !PermissionManager.hasPermission("VIEW_MULTI_STORE_STOCK")) {
               JOptionPane.showMessageDialog(MakeASale.this,
                       "You do not have permission to view store inventory.");
               refreshPermissionButtons();
               return;
           }
           if (WindowHelper.focusIfAlreadyOpen(CrossStoreInventory.class)) return;
           WindowHelper.showPosWindow(new CrossStoreInventory(), MakeASale.this);
       });
       searchField.addActionListener(new ActionListener() {
           public void actionPerformed(ActionEvent e) {
               handleProductSearchEnter();
           }
       });
       searchField.getDocument().addDocumentListener(new DocumentListener() {
           private void restartSearchDebounce() {
               if (resettingProductSearch) {
                   return;
               }
               if (searchDebounceTimer == null) {
                   searchDebounceTimer = new javax.swing.Timer(300, e -> searchProducts(false));
                   searchDebounceTimer.setRepeats(false);
               }

               searchDebounceTimer.restart();
           }

           @Override
           public void insertUpdate(DocumentEvent e) {
               if (!resettingProductSearch) SwingUtilities.invokeLater(this::restartSearchDebounce);
           }

           @Override
           public void removeUpdate(DocumentEvent e) {
               if (!resettingProductSearch) SwingUtilities.invokeLater(this::restartSearchDebounce);
           }

           @Override
           public void changedUpdate(DocumentEvent e) {
               if (!resettingProductSearch) SwingUtilities.invokeLater(this::restartSearchDebounce);
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
       removeCartItemBtn.addActionListener(e -> removeSelectedCartItems());
       cartTable.getSelectionModel().addListSelectionListener(e -> {
           if (!e.getValueIsAdjusting()) {
               removeCartItemBtn.setEnabled(cartTable.getSelectedRowCount() > 0);
           }
       });
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
                       handleSaleDiscountEditOverride();
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
	       loadStartupData();
	       updateCustomerAccountEnabled();
       loadCompanyBranding();
	       revalidate();
	       repaint();
	       javax.swing.Timer themeTimer = new javax.swing.Timer(100, themeEvent -> {
	           if (isShowing()) WindowHelper.configurePosWindow(MakeASale.this);
	       });
	       themeTimer.setRepeats(false);
	       themeTimer.start();
	       });
	       buildTimer.setRepeats(false);
	       buildTimer.start();
	   }
	   });
	   }

    private void loadStartupData() {
        CachedUiLoader.load(this,"make-sale:startup",SalesStartupSnapshot.class,SessionDataCache.SCREEN_TTL,
                loadingState,()->{var settings=UiTaskRunner.supplyAsync(LanApiClient::loadSalesSettings);var customers=UiTaskRunner.supplyAsync(LanApiClient::loadCustomerAccounts);var receipt=UiTaskRunner.supplyAsync(CompanyCustomizationManager::loadReceiptSettings);return new SalesStartupSnapshot(settings.join(),customers.join(),receipt.join().alwaysPrintSaleReceipt());},snapshot->{salesSettings=snapshot.settings();salesSettingsLoaded=true;alwaysPrintSaleReceipt=snapshot.alwaysPrintSaleReceipt();applyRequiredPrintPreference();applyCustomerAccounts(snapshot.customers());updateOverallTotal();updateCustomerAccountEnabled();});
    }

    private void applyRequiredPrintPreference() {
        if (checkoutBtn != null) {
            checkoutBtn.setVisible(!alwaysPrintSaleReceipt);
        }
    }

    private void applyCustomerAccounts(java.util.List<LanApiClient.CustomerAccount> accounts) {
        CustomerAccountOption selectedBeforeReload = getSelectedCustomerAccount();
        customerAccountOptions = new java.util.ArrayList<>();
        customerAccountBox.removeAllItems();
        for (LanApiClient.CustomerAccount account : accounts) customerAccountOptions.add(new CustomerAccountOption(account.customerId(), account.accountNumber(),account.customerName(), account.creditLimit(), account.currentBalance(),account.availableCredit(), account.business(), account.customerTypeName()));
        applyCustomerAccountFilter("", false);
        if (selectedBeforeReload != null) selectCustomerById(selectedBeforeReload.customerId);
    }

    private record SalesStartupSnapshot(LanApiClient.SalesSettings settings,
                                        java.util.List<LanApiClient.CustomerAccount> customers,
                                        boolean alwaysPrintSaleReceipt) { }
    private record CheckoutSnapshot(LanApiClient.CheckoutResult result, ReceiptData receipt,
                                    String receiptError, String printError) { }

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
        boolean enabled = requiresPaymentReference(selectedPaymentMethod);
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

    static boolean requiresPaymentReference(String paymentMethod) {
        return "CARD".equals(paymentMethod)
                || "CHEQUE".equals(paymentMethod)
                || "MMG".equals(paymentMethod);
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

    private void showServiceQuickPick() {
        if (SessionManager.getCurrentLocationId() == null) {
            JOptionPane.showMessageDialog(this, "No store is selected for this session.");
            return;
        }

        JDialog dialog = new JDialog(this, "Quick Pick Service Items", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setMinimumSize(new Dimension(560, 420));
        dialog.setSize(820, 620);
        dialog.setLocationRelativeTo(this);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBackground(DeckersPalette.background());
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JLabel heading = new JLabel("Quick Pick Service Items");
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(DeckersPalette.text());
        heading.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        content.add(heading, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        content.add(center, BorderLayout.CENTER);

        JButton doneButton = createActionUtilityButton("Done");
        doneButton.addActionListener(e -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(false);
        footer.add(doneButton);
        content.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        loadServiceQuickPickItems(dialog, center);
        dialog.setVisible(true);
    }

    private void loadServiceQuickPickItems(JDialog dialog, JPanel center) {
        center.removeAll();
        JLabel loading = new JLabel("Loading service items...", SwingConstants.CENTER);
        loading.setFont(new Font("SansSerif", Font.PLAIN, 16));
        loading.setForeground(DeckersPalette.muted());
        center.add(loading, BorderLayout.CENTER);
        center.revalidate();
        center.repaint();

        UiTaskRunner.submit(dialog, "make-sale.quick-pick-services",
                () -> LanApiClient.searchCatalog("", "SERVICE"),
                products -> {
                    if (!dialog.isDisplayable()) return;
                    showServiceQuickPickItems(center, products);
                },
                failure -> {
                    if (!dialog.isDisplayable()) return;
                    showServiceQuickPickError(dialog, center, failure);
                });
    }

    private void showServiceQuickPickItems(JPanel center,
                                           java.util.List<LanApiClient.CatalogProduct> products) {
        center.removeAll();
        if (products.isEmpty()) {
            JLabel empty = new JLabel("No service items are available for this store.", SwingConstants.CENTER);
            empty.setFont(new Font("SansSerif", Font.PLAIN, 16));
            empty.setForeground(DeckersPalette.muted());
            center.add(empty, BorderLayout.CENTER);
        } else {
            JPanel grid = new JPanel(new GridLayout(0, 3, 12, 12));
            grid.setBackground(DeckersPalette.background());
            grid.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
            for (LanApiClient.CatalogProduct product : products) {
                grid.add(createServiceQuickPickButton(product));
            }

            JScrollPane scrollPane = new JScrollPane(grid);
            scrollPane.setBorder(BorderFactory.createLineBorder(DeckersPalette.border()));
            scrollPane.getVerticalScrollBar().setUnitIncrement(24);
            scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scrollPane.getViewport().setBackground(DeckersPalette.background());
            scrollPane.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentResized(java.awt.event.ComponentEvent event) {
                    int columns = Math.max(1, scrollPane.getViewport().getWidth() / 230);
                    GridLayout layout = (GridLayout) grid.getLayout();
                    if (layout.getColumns() != columns) {
                        layout.setColumns(columns);
                        grid.revalidate();
                    }
                }
            });
            center.add(scrollPane, BorderLayout.CENTER);
        }
        center.revalidate();
        center.repaint();
    }

    private JButton createServiceQuickPickButton(LanApiClient.CatalogProduct product) {
        String displayName = displayNameWithSize(product.name(), product.size());
        String normalText = quickPickButtonText(displayName, product.price(), false);
        JButton button = new RoundedFillButton(normalText);
        button.setFont(new Font("SansSerif", Font.BOLD, 16));
        button.setForeground(DeckersPalette.text());
        button.setBackground(DeckersPalette.tileFill(DeckersPalette.LIME));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setBorder(new OutsideRoundedBorder(DeckersPalette.sectionBorder(DeckersPalette.LIME),
                4, 14, new Insets(16, 12, 16, 12)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(210, 104));
        button.setToolTipText("Add one " + displayName);
        button.addActionListener(e -> {
            button.setEnabled(false);
            addCatalogProductToCart(product, 1);
            button.setText(quickPickButtonText(displayName, product.price(), true));
            javax.swing.Timer confirmationTimer = new javax.swing.Timer(450, ignored -> {
                button.setText(normalText);
                button.setEnabled(true);
            });
            confirmationTimer.setRepeats(false);
            confirmationTimer.start();
        });
        return button;
    }

    private String quickPickButtonText(String name, BigDecimal price, boolean added) {
        String status = added ? "<br><font size='3'>Added</font>" : "";
        return "<html><center>" + escapeHtml(name) + "<br><font size='4'>"
                + escapeHtml(utils.CurrencyFormatter.format(price)) + "</font>" + status + "</center></html>";
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void showServiceQuickPickError(JDialog dialog, JPanel center, Throwable failure) {
        center.removeAll();
        JPanel errorPanel = new JPanel();
        errorPanel.setOpaque(false);
        errorPanel.setLayout(new BoxLayout(errorPanel, BoxLayout.Y_AXIS));
        JLabel message = new JLabel("Unable to load service items: "
                + (failure.getMessage() == null ? "Unknown error" : failure.getMessage()),
                SwingConstants.CENTER);
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        message.setForeground(DeckersPalette.text());
        JButton retry = createActionUtilityButton("Retry");
        retry.setAlignmentX(Component.CENTER_ALIGNMENT);
        retry.addActionListener(e -> loadServiceQuickPickItems(dialog, center));
        errorPanel.add(Box.createVerticalGlue());
        errorPanel.add(message);
        errorPanel.add(Box.createVerticalStrut(14));
        errorPanel.add(retry);
        errorPanel.add(Box.createVerticalGlue());
        center.add(errorPanel, BorderLayout.CENTER);
        center.revalidate();
        center.repaint();
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
        loadBrandingAssets();
    }

    private void loadBrandingAssets() {
        UiTaskRunner.submit(this, "make-sale.branding", () -> {
            ImageIcon deckers = DeckersLogoManager.loadDeckersLogoIcon(getClass());
            ImageIcon smartStock = DeckersLogoManager.loadSmartStockLogoIcon(getClass());
            ImageIcon scaledDeckers = deckers == null || deckers.getIconWidth() <= 0 ? null
                    : new ImageIcon(DeckersLogoManager.scaleToFit(deckers.getImage(), 300, 96));
            ImageIcon scaledSmartStock = smartStock == null || smartStock.getIconWidth() <= 0 ? null
                    : new ImageIcon(DeckersLogoManager.scaleToFit(smartStock.getImage(), 196, 88));
            return new BrandingAssets(scaledDeckers, scaledSmartStock);
        }, assets -> {
            applyDeckersCompanyLogo(assets.deckers());
            applySmartStockAppLogo(assets.smartStock());
        }, ignored -> {
            applyDeckersCompanyLogo(null);
            applySmartStockAppLogo(null);
        });
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

    private void applyDeckersCompanyLogo(ImageIcon deckersLogoIcon) {
        if (deckersLogoIcon != null && deckersLogoIcon.getIconWidth() > 0) {
            companyLogoLabel.setText("");
            companyLogoLabel.setIcon(deckersLogoIcon);
            return;
        }

        companyLogoLabel.setIcon(null);
        companyLogoLabel.setText("Deckers");
        companyLogoLabel.setFont(new Font("SansSerif", Font.BOLD, 26));
    }

    private void applySmartStockAppLogo(ImageIcon centerLogoIcon) {
        if (appLogoLabel == null) {
            return;
        }
        if (centerLogoIcon != null && centerLogoIcon.getIconWidth() > 0) {
            appLogoLabel.setText("");
            appLogoLabel.setIcon(centerLogoIcon);
            return;
        }

        appLogoLabel.setIcon(null);
        appLogoLabel.setText("SmartStock");
    }

    private record BrandingAssets(ImageIcon deckers, ImageIcon smartStock) { }

    private void refreshPermissionButtons() {
        if (newItemBtn != null) {
            newItemBtn.setEnabled(PermissionManager.hasPermission("NEW_ITEM"));
        }
        if (editItemBtn != null) {
            editItemBtn.setEnabled(PermissionManager.hasPermission("EDIT_ITEM"));
        }
        if (storeStockBtn != null) {
            storeStockBtn.setEnabled(PermissionManager.hasPermission("VIEW_INVENTORY")
                    && PermissionManager.hasPermission("VIEW_MULTI_STORE_STOCK"));
        }
	        if (discountPercentField != null) {
                discountPercentField.setEnabled(true);
                discountPercentField.setToolTipText(canApplySaleDiscount()
                        ? null
                        : "A manager approval is required to apply a sale discount.");
	        }
	    }

    private boolean canApplySaleDiscount() {
        return PermissionManager.hasPermission(APPLY_SALE_DISCOUNT_PERMISSION);
    }

    private boolean canChangeSaleItemPrice() {
        return PermissionManager.hasPermission(CHANGE_SALE_ITEM_PRICE_PERMISSION);
    }

    private boolean canOverrideSaleDiscount() {
        return PermissionManager.hasPermission(SALE_DISCOUNT_OVERRIDE_PERMISSION);
    }

    private DefaultCellEditor createApprovalAwareCartEditor(int column) {
        return new DefaultCellEditor(new JTextField()) {
            @Override
            public boolean isCellEditable(EventObject event) {
                if (!super.isCellEditable(event)) {
                    return false;
                }
                int row = cartTable == null ? -1 : cartTable.getSelectedRow();
                if (event instanceof java.awt.event.MouseEvent mouseEvent
                        && mouseEvent.getSource() == cartTable) {
                    row = cartTable.rowAtPoint(mouseEvent.getPoint());
                }
                if (row >= 0 && cartTable != null) {
                    row = cartTable.convertRowIndexToModel(row);
                }
                return authorizeCartValueEdit(row, column);
            }
        };
    }

    private boolean authorizeCartValueEdit(int row, int column) {
        if (row < 0 || row >= cartModel.getRowCount()) {
            return false;
        }
        if (column == CART_COL_PRICE && canChangeSaleItemPrice()) {
            return true;
        }
        if (column == CART_COL_ITEM_DISCOUNT && canApplySaleDiscount()) {
            return true;
        }

        int productId = parseIntOrDefault(cartModel.getValueAt(row, CART_COL_ID), -1);
        if (productId <= 0) {
            return false;
        }
        String productName = String.valueOf(cartModel.getValueAt(row, CART_COL_NAME));
        String permission = column == CART_COL_PRICE
                ? CHANGE_SALE_ITEM_PRICE_PERMISSION : APPLY_SALE_DISCOUNT_PERMISSION;
        String action = column == CART_COL_PRICE ? "Price Override" : "Item Discount Override";
        String prompt = column == CART_COL_PRICE
                ? "Reason for price override on " + productName + ":"
                : "Reason for item discount on " + productName + ":";
        ManagerApprovalService.ApprovalResult approval = requestManagerApproval(
                permission, action, prompt);
        if (approval == null) {
            return false;
        }
        if (column == CART_COL_PRICE) {
            pendingPriceOverrideApprovals.put(productId, new PendingPriceApproval(null, approval));
            showOverrideStatus("Price override approved by: " + approval.approvedByName());
        } else {
            pendingItemDiscountApprovals.put(productId, new PendingDiscountApproval(null, approval));
            showOverrideStatus("Item discount approved by: " + approval.approvedByName());
        }
        return true;
    }

    private ManagerApprovalService.ApprovalResult requestManagerApproval(
            String permission, String action, String prompt) {
        try {
            return ManagerApprovalService.requestApproval(this, permission, action, prompt);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Manager Approval", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    private void showOverrideStatus(String message) {
        if (overrideStatusLabel != null) {
            overrideStatusLabel.setText(message);
        }
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
        final long searchGeneration = ++productSearchGeneration;

        if (SessionManager.getCurrentLocationId() == null) {
            JOptionPane.showMessageDialog(this, "No store is selected for this session.");
            return;
        }

        final int locationId = SessionManager.getCurrentLocationId();
        warmProductSearchCacheInBackground();
        UiTaskRunner.submit(this,"make-sale.search",()->{
                java.util.List<Object[]> cachedRows = tryFilterCachedProducts(locationId, searchText);
                if (cachedRows != null) {
                    return cachedRows;
                }
                java.util.List<Object[]> rows = new java.util.ArrayList<>();
                for (LanApiClient.CatalogProduct product : LanApiClient.searchCatalog(searchText)) {
                    rows.add(catalogRow(product));
                }
                return rows;
            },rows->{
                    if (searchGeneration != productSearchGeneration
                            || !searchText.equals(searchField.getText().trim())) {
                        return;
                    }
                    if (rows.isEmpty()) {
                        closeSearchPopup();
                        if (showMessages) {
                            JOptionPane.showMessageDialog(MakeASale.this,
                                    searchText.isEmpty() ? "No products found for this store." : "No matching products found.");
                        }
                        return;
                    }
                    showSearchResultsPopup(rows, searchText);
            },failure->{if(showMessages)JOptionPane.showMessageDialog(MakeASale.this,"Database error: "+failure.getMessage());});
    }

    private java.util.List<Object[]> tryFilterCachedProducts(int locationId, String searchText) {
        if (cachedProductLocationId != locationId || productSearchCache.isEmpty()) {
            return null;
        }
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (Object[] row : productSearchCache) {
            if (rows.size() >= 250) {
                break;
            }
            if (ProductSearchHelper.textMatches(rowValue(row, 9), searchText)) {
                rows.add(row);
            }
        }
        return rows;
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
                java.util.List<Object[]> rows = new java.util.ArrayList<>();
                for (LanApiClient.CatalogProduct product : LanApiClient.searchCatalog("")) {
                    if (isCancelled()) return rows;
                    rows.add(catalogRow(product));
                }
                return rows;
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

    private static Object[] catalogRow(LanApiClient.CatalogProduct product) {
        return new Object[]{product.productId(), product.name(), product.size(), product.description(),
                product.sku(), product.price(), product.productType(), product.categoryId(),
                product.quantityOnHand(), product.searchableText()};
    }


    private void showSearchResultsPopup(java.util.List<Object[]> rows, String searchText) {
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
        searchResultsQuery = searchText == null ? "" : searchText.trim();

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

    private void handleProductSearchEnter() {
        String identifier = searchField.getText().trim();
        if (identifier.isEmpty()) {
            return;
        }
        if (searchDebounceTimer != null) {
            searchDebounceTimer.stop();
        }
        productSearchGeneration++;
        final long lookupGeneration = ++identifierLookupGeneration;
        UiTaskRunner.submit(this, "make-sale.identifier-lookup",
                () -> LanApiClient.lookupCatalogIdentifier(identifier),
                lookup -> {
                    if (lookupGeneration != identifierLookupGeneration
                            || !identifier.equals(searchField.getText().trim())) {
                        return;
                    }
                    if ("MATCH".equals(lookup.status()) && lookup.products().size() == 1) {
                        addCatalogProductToCart(lookup.products().get(0), 1);
                        resetProductSearchAfterAdd();
                    } else if ("AMBIGUOUS".equals(lookup.status())) {
                        closeSearchPopup();
                        JOptionPane.showMessageDialog(this,
                                "This barcode matches more than one item. Review the item barcodes before continuing.");
                    } else if (identifier.equals(searchResultsQuery)
                            && searchPopup != null && searchPopup.isVisible()
                            && searchResultsTable != null && searchResultsTable.getSelectedRow() >= 0) {
                        addSelectedSearchResultToCart();
                    } else {
                        searchProducts(false);
                    }
                },
                failure -> JOptionPane.showMessageDialog(this,
                        "Unable to look up item barcode: " + failure.getMessage()));
    }

    private void addCatalogProductToCart(LanApiClient.CatalogProduct product, int quantity) {
        addToCart(product.productId(), displayNameWithSize(product.name(), product.size()),
                product.description(), product.sku(), product.price().doubleValue(), quantity,
                normalizeProductType(product.productType()), product.categoryId());
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
        resetProductSearchAfterAdd();
    }

    private void resetProductSearchAfterAdd() {
        productSearchGeneration++;
        identifierLookupGeneration++;
        if (searchDebounceTimer != null) {
            searchDebounceTimer.stop();
        }
        resettingProductSearch = true;
        try {
            searchField.setText("");
        } finally {
            resettingProductSearch = false;
        }
        closeSearchPopup();
        if (searchResultsTable != null) {
            ((DefaultTableModel) searchResultsTable.getModel()).setRowCount(0);
        }
        searchResultsQuery = "";
        SwingUtilities.invokeLater(searchField::requestFocusInWindow);
    }

    private void closeSearchPopup() {
        if (searchPopup != null) {
            searchPopup.setVisible(false);
        }
        searchResultsQuery = "";
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
                utils.CurrencyFormatter.normalize(BigDecimal.valueOf(price)),
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
        if (existingApproval != null && (existingApproval.approvedPrice() == null
                || existingApproval.approvedPrice().compareTo(enteredPrice) == 0)) {
            if (existingApproval.approvedPrice() == null) {
                pendingPriceOverrideApprovals.put(productId,
                        new PendingPriceApproval(enteredPrice, existingApproval.approval()));
            }
            return;
        }

        String productName = String.valueOf(cartModel.getValueAt(row, CART_COL_NAME));
        ManagerApprovalService.ApprovalResult approval = requestManagerApproval(
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
        if (existingApproval != null && (existingApproval.approvedDiscountPercent() == null
                || existingApproval.approvedDiscountPercent().compareTo(discountPercent) == 0)) {
            if (existingApproval.approvedDiscountPercent() == null) {
                pendingItemDiscountApprovals.put(productId,
                        new PendingDiscountApproval(discountPercent, existingApproval.approval()));
            }
            return;
        }

        String productName = String.valueOf(cartModel.getValueAt(row, CART_COL_NAME));
        ManagerApprovalService.ApprovalResult approval = requestManagerApproval(
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

    private void handleSaleDiscountEditOverride() {
        if (discountPercentField == null || suppressDiscountFieldEvents) {
            return;
        }
        String currentText = discountPercentField.getText() == null
                ? "" : discountPercentField.getText().trim();
        if (currentText.isBlank()) {
            return;
        }

        BigDecimal discountPercent;
        try {
            discountPercent = new BigDecimal(currentText);
        } catch (NumberFormatException ignored) {
            return;
        }
        if (discountPercent.signum() <= 0) {
            pendingSaleDiscountApproval = null;
            lastAcceptedSaleDiscountText = currentText;
            return;
        }

        BigDecimal discountLimit = salesSettings == null || salesSettings.discountLimit() == null
                ? BigDecimal.valueOf(5) : salesSettings.discountLimit();
        String requiredPermission = null;
        String action = null;
        String prompt = null;
        if (discountPercent.compareTo(discountLimit) > 0) {
            if (!canOverrideSaleDiscount()) {
                requiredPermission = SALE_DISCOUNT_OVERRIDE_PERMISSION;
                action = "Sale Discount Override";
                prompt = "Reason for discount override:";
            }
        } else if (!canApplySaleDiscount()) {
            requiredPermission = APPLY_SALE_DISCOUNT_PERMISSION;
            action = "Sale Discount Approval";
            prompt = "Reason for applying this sale discount:";
        }

        if (requiredPermission == null) {
            pendingSaleDiscountApproval = null;
            lastAcceptedSaleDiscountText = currentText;
            return;
        }
        if (pendingSaleDiscountApproval != null
                && requiredPermission.equals(pendingSaleDiscountApproval.permission())) {
            lastAcceptedSaleDiscountText = currentText;
            return;
        }

        ManagerApprovalService.ApprovalResult approval = requestManagerApproval(
                requiredPermission, action, prompt);
        if (approval == null) {
            setDiscountFieldValue(lastAcceptedSaleDiscountText);
            return;
        }
        pendingSaleDiscountApproval = new PendingSaleDiscountApproval(requiredPermission, approval);
        lastAcceptedSaleDiscountText = currentText;
        showOverrideStatus("Sale discount approved by: " + approval.approvedByName());
    }

    private String requiredSaleDiscountApprovalPermission(BigDecimal discountPercent,
                                                           BigDecimal discountLimit) {
        if (discountPercent == null || discountPercent.signum() <= 0) {
            return null;
        }
        BigDecimal limit = discountLimit == null ? BigDecimal.valueOf(5) : discountLimit;
        if (discountPercent.compareTo(limit) > 0) {
            return canOverrideSaleDiscount() ? null : SALE_DISCOUNT_OVERRIDE_PERMISSION;
        }
        if (!canApplySaleDiscount()) {
            return APPLY_SALE_DISCOUNT_PERMISSION;
        }
        return null;
    }

    private boolean commitCurrentCartEdit() {
        if (cartTable == null || !cartTable.isEditing()) {
            return true;
        }
        if (cartTable.getCellEditor() != null && cartTable.getCellEditor().stopCellEditing()) {
            return true;
        }
        JOptionPane.showMessageDialog(this,
                "Finish or cancel the current cart edit before continuing.");
        return false;
    }

    private void removeSelectedCartItems() {
        if (cartTable == null || cartModel == null) return;
        if (!commitCurrentCartEdit()) return;

        int[] selectedViewRows = cartTable.getSelectedRows();
        if (selectedViewRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Select an item in the cart to remove.");
            return;
        }

        int[] selectedModelRows = new int[selectedViewRows.length];
        for (int i = 0; i < selectedViewRows.length; i++) {
            selectedModelRows[i] = cartTable.convertRowIndexToModel(selectedViewRows[i]);
        }
        java.util.Arrays.sort(selectedModelRows);

        updatingCart = true;
        try {
            for (int i = selectedModelRows.length - 1; i >= 0; i--) {
                int row = selectedModelRows[i];
                int productId = parseIntOrDefault(cartModel.getValueAt(row, CART_COL_ID), -1);
                pendingPriceOverrideApprovals.remove(productId);
                pendingItemDiscountApprovals.remove(productId);
                cartModel.removeRow(row);
            }
        } finally {
            updatingCart = false;
        }

        updateLineTotals();
        if (cartModel.getRowCount() > 0) {
            int nextModelRow = Math.min(selectedModelRows[0], cartModel.getRowCount() - 1);
            int nextViewRow = cartTable.convertRowIndexToView(nextModelRow);
            if (nextViewRow >= 0) {
                cartTable.setRowSelectionInterval(nextViewRow, nextViewRow);
            }
        } else {
            removeCartItemBtn.setEnabled(false);
        }
    }

    private void clearPendingManagerApprovals() {
        pendingPriceOverrideApprovals.clear();
        pendingItemDiscountApprovals.clear();
        pendingSaleDiscountApproval = null;
        lastAcceptedSaleDiscountText = "0";
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
                    price = utils.CurrencyFormatter.normalize(new BigDecimal(priceValue.toString()));
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

                BigDecimal lineGross = utils.CurrencyFormatter.normalize(price.multiply(BigDecimal.valueOf(qty)));
                BigDecimal lineDiscount = utils.CurrencyFormatter.normalize(lineGross.multiply(itemDiscountPercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
                BigDecimal lineTotal = utils.CurrencyFormatter.normalize(lineGross.subtract(lineDiscount).max(BigDecimal.ZERO));

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
        return utils.CurrencyFormatter.normalize(total);
    }

    private BigDecimal getItemDiscountTotal() {
        return utils.CurrencyFormatter.normalize(getCartGrossSubtotal()
                .subtract(BigDecimal.valueOf(getCartSubtotal()))
                .max(BigDecimal.ZERO));
    }

    private double getOverallTotal() {
        return getFinalTotalAmount().doubleValue();
    }

    private BigDecimal getFinalTotalAmount() {
        BigDecimal discountPercent = getDiscountPercent();
        BigDecimal preVatTotal = getPreVatSaleTotal(discountPercent);
        return utils.CurrencyFormatter.normalize(preVatTotal.add(calculateVat(discountPercent).amount()));
    }

    private BigDecimal getPreVatSaleTotal(BigDecimal discountPercent) {
        BigDecimal subtotal = BigDecimal.valueOf(getCartSubtotal()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cleanDiscountPercent = discountPercent == null ? BigDecimal.ZERO : discountPercent;
        BigDecimal discountAmount = subtotal.multiply(cleanDiscountPercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        return utils.CurrencyFormatter.normalize(subtotal.subtract(discountAmount).max(BigDecimal.ZERO));
    }

    private VatCalculation calculateVat(BigDecimal saleDiscountPercent) {
        LanApiClient.SalesSettings settings = salesSettings;
        if (settings == null || !settings.vatEnabled()) {
            return new VatCalculation(BigDecimal.ZERO, BigDecimal.ZERO, "");
        }
        BigDecimal preVatTotal = getPreVatSaleTotal(saleDiscountPercent);
        if (preVatTotal.compareTo(BigDecimal.ZERO) <= 0) {
            return new VatCalculation(BigDecimal.ZERO, BigDecimal.ZERO,
                    settings.departmentVat() ? "DEPARTMENT" : "FIXED");
        }
        if (!settings.departmentVat()) {
            BigDecimal rate = settings.fixedVatRate() == null ? BigDecimal.ZERO : settings.fixedVatRate();
            BigDecimal amount = preVatTotal.multiply(rate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            return new VatCalculation(amount, rate, "FIXED");
        }

        Map<Integer, BigDecimal> departmentRates = new java.util.HashMap<>();
        if (settings.departmentRates() != null) {
            for (LanApiClient.DepartmentVatRate rate : settings.departmentRates()) {
                departmentRates.put(rate.categoryId(),
                        rate.ratePercent() == null ? BigDecimal.ZERO : rate.ratePercent());
            }
        }
        BigDecimal saleDiscountMultiplier = BigDecimal.ONE.subtract(
                (saleDiscountPercent == null ? BigDecimal.ZERO : saleDiscountPercent)
                        .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
        BigDecimal vatAmount = BigDecimal.ZERO;
        for (int i = 0; i < cartModel.getRowCount(); i++) {
            Integer departmentId = parseNullableInt(cartModel.getValueAt(i, CART_COL_DEPARTMENT_ID));
            BigDecimal rate = departmentId == null ? BigDecimal.ZERO
                    : departmentRates.getOrDefault(departmentId, BigDecimal.ZERO);
            if (rate.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal taxableLine = utils.CurrencyFormatter.normalize(
                    parseMoneyOrZero(cartModel.getValueAt(i, CART_COL_LINE_TOTAL))
                            .multiply(saleDiscountMultiplier).max(BigDecimal.ZERO));
            vatAmount = vatAmount.add(taxableLine.multiply(rate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP));
        }
        BigDecimal effectiveRate = vatAmount.multiply(BigDecimal.valueOf(100))
                .divide(preVatTotal, 2, RoundingMode.HALF_UP);
        return new VatCalculation(vatAmount, effectiveRate, "DEPARTMENT");
    }

    private BigDecimal getDiscountPercent() {
        if (discountPercentField == null) {
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
            return utils.CurrencyFormatter.normalize(new BigDecimal(String.valueOf(value).trim()));
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
        UiTaskRunner.submit(this, "sale.customer-accounts", LanApiClient::loadCustomerAccounts,
                this::applyCustomerAccounts,
                failure -> loadingState.failed(failure.getMessage(), true, this::loadCustomerAccounts));
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
        editorField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent event) {
                if (event.getKeyCode() == java.awt.event.KeyEvent.VK_DOWN) {
                    navigateCustomerAccountResults(editorField, 1);
                    event.consume();
                } else if (event.getKeyCode() == java.awt.event.KeyEvent.VK_UP) {
                    navigateCustomerAccountResults(editorField, -1);
                    event.consume();
                } else if (event.getKeyCode() == java.awt.event.KeyEvent.VK_ENTER) {
                    if (customerAccountBox.getSelectedItem() instanceof CustomerAccountOption option) {
                        updatingCustomerAccountFilter = true;
                        try {
                            customerAccountBox.setSelectedItem(option);
                            customerAccountBox.getEditor().setItem(option);
                            customerAccountBox.setPopupVisible(false);
                        } finally {
                            updatingCustomerAccountFilter = false;
                        }
                        event.consume();
                    }
                } else if (event.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE
                        && customerAccountBox.isPopupVisible()) {
                    customerAccountBox.setPopupVisible(false);
                    event.consume();
                }
            }
        });
    }

    private void navigateCustomerAccountResults(JTextField editorField, int direction) {
        if (customerAccountBox == null) return;
        String filterText = editorField.getText();
        if (!customerAccountBox.isPopupVisible()) {
            applyCustomerAccountFilter(filterText, true);
        }
        if (customerAccountBox.getItemCount() <= 1) return;

        int currentIndex = customerAccountBox.getSelectedIndex();
        int nextIndex;
        if (direction > 0) {
            nextIndex = currentIndex < 1 ? 1
                    : Math.min(currentIndex + 1, customerAccountBox.getItemCount() - 1);
        } else {
            nextIndex = currentIndex <= 1 ? 1 : currentIndex - 1;
        }

        updatingCustomerAccountFilter = true;
        try {
            customerAccountBox.setSelectedIndex(nextIndex);
            editorField.setText(filterText);
            editorField.setCaretPosition(editorField.getText().length());
            customerAccountBox.setPopupVisible(true);
        } finally {
            updatingCustomerAccountFilter = false;
        }
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
        if (!commitCurrentCartEdit()) {
            return;
        }
        if (!PermissionManager.requirePermission(MAKE_SALE_PERMISSION, this, "Checkout")) {
            refreshPermissionButtons();
            return;
        }
        if (cartModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Cart is empty.");
            return;
        }
        if (!salesSettingsLoaded) {
            loadingState.loading(false, java.time.Instant.now());
            UiTaskRunner.submit(this, "sale.settings-retry", LanApiClient::loadSalesSettings, settings -> {
                salesSettings = settings;
                salesSettingsLoaded = true;
                loadingState.ready(java.time.Instant.now());
                checkout(showReceiptPreview);
            }, failure -> loadingState.failed(failure.getMessage(), false,
                    () -> checkout(showReceiptPreview)));
            return;
        }

        handleSaleDiscountEditOverride();
        BigDecimal discountPercent = parseDiscountPercentOrShowError();
        if (discountPercent == null) {
            return;
        }
        BigDecimal discountLimit = salesSettings == null || salesSettings.discountLimit() == null
                ? BigDecimal.valueOf(5)
                : salesSettings.discountLimit();
        String saleDiscountOverrideReason = null;
        String saleDiscountApprovalToken = null;
        String requiredSaleApproval = requiredSaleDiscountApprovalPermission(
                discountPercent, discountLimit);
        if (requiredSaleApproval != null) {
            if (pendingSaleDiscountApproval == null
                    || !requiredSaleApproval.equals(pendingSaleDiscountApproval.permission())) {
                JOptionPane.showMessageDialog(this,
                        "Manager approval is required before this discount can be checked out.",
                        "Manager Approval", JOptionPane.WARNING_MESSAGE);
                return;
            }
            saleDiscountOverrideReason = pendingSaleDiscountApproval.approval().reason();
            saleDiscountApprovalToken = pendingSaleDiscountApproval.approval().lanApprovalToken();
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
        if (requiresPaymentReference(paymentMethod) && paymentReference.isBlank()) {
            JOptionPane.showMessageDialog(this,
                    "Enter the card, cheque, or MMG reference number.");
            return;
        }

        BigDecimal cashCollected = BigDecimal.ZERO;
        if (cashPayment) {
            cashCollected = promptForCashCollected(getFinalTotalAmount());
            if (cashCollected == null) {
                return;
            }
        }

        checkoutThroughLanApi(showReceiptPreview, paymentMethod, paymentReference, selectedCustomer,
                discountPercent, cashCollected, saleDiscountApprovalToken, saleDiscountOverrideReason);
    }

    private void checkoutThroughLanApi(boolean showReceiptPreview, String paymentMethod,
                                       String paymentReference, CustomerAccountOption customer,
                                       BigDecimal saleDiscountPercent, BigDecimal cashCollected,
                                       String saleApprovalToken, String saleApprovalReason) {
        java.util.List<LanApiClient.CheckoutLine> lines = new java.util.ArrayList<>();
        for (int row = 0; row < cartModel.getRowCount(); row++) {
            int productId = parseIntOrDefault(cartModel.getValueAt(row, CART_COL_ID), -1);
            PendingPriceApproval priceApproval = pendingPriceOverrideApprovals.get(productId);
            PendingDiscountApproval discountApproval = pendingItemDiscountApprovals.get(productId);
            lines.add(new LanApiClient.CheckoutLine(
                    productId,
                    parseIntOrDefault(cartModel.getValueAt(row, CART_COL_QTY), 0),
                    parseMoneyOrZero(cartModel.getValueAt(row, CART_COL_PRICE)),
                    parsePercentOrZero(cartModel.getValueAt(row, CART_COL_ITEM_DISCOUNT)),
                    priceApproval == null ? null : priceApproval.approval().lanApprovalToken(),
                    priceApproval == null ? null : priceApproval.approval().reason(),
                    discountApproval == null ? null : discountApproval.approval().lanApprovalToken(),
                    discountApproval == null ? null : discountApproval.approval().reason()
            ));
        }
        LanApiClient.CheckoutRequest request = new LanApiClient.CheckoutRequest(
                paymentMethod, paymentReference, customer == null ? null : customer.customerId,
                saleDiscountPercent, cashCollected, saleApprovalToken, saleApprovalReason, lines);
        String requestFingerprint = request.toString();
        if (pendingCheckoutKey == null || !requestFingerprint.equals(pendingCheckoutFingerprint)) {
            pendingCheckoutKey = UUID.randomUUID().toString();
            pendingCheckoutFingerprint = requestFingerprint;
        }
        String checkoutKey = pendingCheckoutKey;
        checkoutBtn.setEnabled(false);
        checkoutPrintBtn.setEnabled(false);
        loadingState.loading(false, java.time.Instant.now());
        UiTaskRunner.submit(this, "sale.checkout", () -> {
            LanApiClient.CheckoutResult result = LanApiClient.checkout(request, checkoutKey);
            ReceiptData receipt = null;
            String receiptError = null;
            String printError = null;
            if (showReceiptPreview) {
                try {
                    receipt = ReceiptBuilder.loadSaleReceipt(result.saleId(),
                            "CASH".equals(paymentMethod) ? result.cashCollected() : null,
                            "CASH".equals(paymentMethod) ? result.changeDue() : null);
                    HardwareSettingsManager.PosPrinter printer = HardwareSettingsManager.getDefaultReceiptPrinter();
                    ReceiptPrinter.printToPosPrinter(receipt, printer);
                } catch (Exception ex) {
                    if (receipt == null) {
                        receiptError = ex.getMessage();
                    } else {
                        printError = ex.getMessage();
                    }
                }
            }
            return new CheckoutSnapshot(result, receipt, receiptError, printError);
        }, snapshot -> {
            LanApiClient.CheckoutResult result = snapshot.result();
            pendingCheckoutKey = null;
            pendingCheckoutFingerprint = null;
            String message = "Sale completed successfully.\nReceipt #: " + result.receiptNumber()
                    + "\nSale ID: " + result.saleId();
            if (result.cashDrawerName() != null && !result.cashDrawerName().isBlank()) {
                message += "\nCash Drawer: " + result.cashDrawerName();
            }
            if ("CASH".equals(paymentMethod)) {
                message += "\nCash Collected: " + utils.CurrencyFormatter.format(result.cashCollected())
                        + "\nChange Due: " + utils.CurrencyFormatter.format(result.changeDue());
            }
            if (snapshot.receipt() != null) {
                WindowHelper.showPosWindow(new ReceiptPreview(snapshot.receipt()), this);
            } else if (snapshot.receiptError() != null) {
                message += "\n\nReceipt preview failed: " + snapshot.receiptError();
            }
            if (snapshot.printError() != null) {
                message += "\n\nReceipt printing failed: " + snapshot.printError();
            } else if (showReceiptPreview) {
                message += "\n\nReceipt sent to the printer.";
            }
            JOptionPane.showMessageDialog(this, message);
            cartModel.setRowCount(0);
            clearPendingManagerApprovals();
            setDiscountFieldValue("0");
            clearHeldCartSelection();
            if (paymentReferenceField != null) paymentReferenceField.setText("");
            configureCartTableColumns();
            searchField.setText("");
            loadCustomerAccounts();
            updateOverallTotal();
            checkoutBtn.setEnabled(true);
            checkoutPrintBtn.setEnabled(true);
            loadingState.ready(java.time.Instant.now());
        }, failure -> {
            checkoutBtn.setEnabled(true);
            checkoutPrintBtn.setEnabled(true);
            loadingState.actionFailed("Sale", failure.getMessage(),
                    () -> checkoutThroughLanApi(showReceiptPreview, paymentMethod, paymentReference,
                            customer, saleDiscountPercent, cashCollected, saleApprovalToken, saleApprovalReason));
        });
    }

    private void holdCurrentCart() {
        if (!commitCurrentCartEdit()) {
            return;
        }
        if (!PermissionManager.requirePermission(HOLD_CART_PERMISSION, this, "Hold Cart")) {
            refreshPermissionButtons();
            return;
        }
        if (cartModel.getRowCount() == 0) {
            JOptionPane.showMessageDialog(this, "Cart is empty.");
            return;
        }
        String holdName = JOptionPane.showInputDialog(this, "Hold name / note:", "Hold Cart",
                JOptionPane.PLAIN_MESSAGE);
        if (holdName == null) return;
        CustomerAccountOption customer = getSelectedCustomerAccount();
        handleSaleDiscountEditOverride();
        BigDecimal saleDiscount = parseDiscountPercentOrShowError();
        if (saleDiscount == null) return;
        BigDecimal discountLimit = salesSettings == null || salesSettings.discountLimit() == null
                ? BigDecimal.valueOf(5) : salesSettings.discountLimit();
        String requiredSaleApproval = requiredSaleDiscountApprovalPermission(
                saleDiscount, discountLimit);
        ManagerApprovalService.ApprovalResult saleApproval = null;
        if (requiredSaleApproval != null) {
            if (pendingSaleDiscountApproval == null
                    || !requiredSaleApproval.equals(pendingSaleDiscountApproval.permission())) {
                JOptionPane.showMessageDialog(this,
                        "Manager approval is required before this discounted cart can be held.",
                        "Manager Approval", JOptionPane.WARNING_MESSAGE);
                return;
            }
            saleApproval = pendingSaleDiscountApproval.approval();
        }

        java.util.List<LanApiClient.HeldCartCreateLine> lines = new java.util.ArrayList<>();
        for (int row = 0; row < cartModel.getRowCount(); row++) {
            int productId = parseIntOrDefault(cartModel.getValueAt(row, CART_COL_ID), -1);
            PendingPriceApproval priceApproval = pendingPriceOverrideApprovals.get(productId);
            PendingDiscountApproval discountApproval = pendingItemDiscountApprovals.get(productId);
            lines.add(new LanApiClient.HeldCartCreateLine(
                    productId,
                    parseIntOrDefault(cartModel.getValueAt(row, CART_COL_QTY), 0),
                    parseMoneyOrZero(cartModel.getValueAt(row, CART_COL_PRICE)),
                    parsePercentOrZero(cartModel.getValueAt(row, CART_COL_ITEM_DISCOUNT)),
                    priceApproval == null ? null : priceApproval.approval().lanApprovalToken(),
                    priceApproval == null ? null : priceApproval.approval().reason(),
                    discountApproval == null ? null : discountApproval.approval().lanApprovalToken(),
                    discountApproval == null ? null : discountApproval.approval().reason()));
        }
        LanApiClient.HeldCartCreateRequest request = new LanApiClient.HeldCartCreateRequest(
                holdName.trim(), selectedPaymentMethod, customer == null ? null : customer.customerId,
                saleDiscount,
                saleApproval == null ? null : saleApproval.lanApprovalToken(),
                saleApproval == null ? null : saleApproval.reason(),
                lines);
        String fingerprint = request.toString();
        if (pendingHoldKey == null || !fingerprint.equals(pendingHoldFingerprint)) {
            pendingHoldKey = UUID.randomUUID().toString();
            pendingHoldFingerprint = fingerprint;
        }
        try {
            LanApiClient.HeldCartCreated result = LanApiClient.createHeldCart(request, pendingHoldKey);
            pendingHoldKey = null;
            pendingHoldFingerprint = null;
            JOptionPane.showMessageDialog(this,
                    "Cart held successfully. Hold ID: " + result.heldCartId());
            cartModel.setRowCount(0);
            clearPendingManagerApprovals();
            clearHeldCartSelection();
            configureCartTableColumns();
            updateOverallTotal();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to hold cart: " + ex.getMessage(),
                    "Hold Cart", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resumeHeldCart() {
        if (!PermissionManager.requirePermission(RESUME_HOLD_PERMISSION, this, "Resume Held Cart")) {
            refreshPermissionButtons();
            return;
        }
        if (cartModel.getRowCount() > 0) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Replace the current cart with a held cart?", "Resume Held Cart",
                    JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) return;
        }
        HeldCartOption selected = pendingResumeHeldCartId == null
                ? selectHeldCart() : new HeldCartOption(pendingResumeHeldCartId);
        if (selected == null) return;
        if (pendingResumeHeldCartId == null || pendingResumeHeldCartId != selected.heldCartId()) {
            pendingResumeHeldCartId = selected.heldCartId();
            pendingResumeKey = UUID.randomUUID().toString();
        }
        try {
            LanApiClient.HeldCartPayload held = LanApiClient.resumeHeldCart(
                    selected.heldCartId(), pendingResumeKey);
            loadHeldCartIntoCurrentCart(held);
            pendingResumeHeldCartId = null;
            pendingResumeKey = null;
            JOptionPane.showMessageDialog(this, "Held cart resumed.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to resume held cart: " + ex.getMessage(),
                    "Resume Held Cart", JOptionPane.ERROR_MESSAGE);
        }
    }

    private HeldCartOption selectHeldCart() {
        DefaultTableModel model = new DefaultTableModel(
                new Object[]{"Hold ID", "Held At", "Hold Name", "Cashier", "Customer", "Items", "Total"}, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        try {
            for (LanApiClient.HeldCartSummary held : LanApiClient.listHeldCarts()) {
                String heldAt = Instant.ofEpochMilli(held.createdAtEpochMillis())
                        .atZone(StoreTimeZoneHelper.getStoreZone())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a"));
                model.addRow(new Object[]{held.heldCartId(), heldAt, held.holdName(), held.userName(),
                        held.customerName(), held.itemCount(), utils.CurrencyFormatter.format(held.total())});
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to load held carts: " + ex.getMessage(),
                    "LAN Service", JOptionPane.ERROR_MESSAGE);
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
        int result = JOptionPane.showConfirmDialog(this, scrollPane, "Select Held Cart",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION || table.getSelectedRow() < 0) return null;
        int row = table.convertRowIndexToModel(table.getSelectedRow());
        return new HeldCartOption(Integer.parseInt(String.valueOf(model.getValueAt(row, 0))));
    }

    private void loadHeldCartIntoCurrentCart(LanApiClient.HeldCartPayload held) {
        clearPendingManagerApprovals();
        updatingCart = true;
        try {
            cartModel.setRowCount(0);
            for (LanApiClient.HeldCartItem item : held.items()) {
                BigDecimal price = utils.CurrencyFormatter.normalize(item.unitPrice());
                BigDecimal catalogPrice = utils.CurrencyFormatter.normalize(
                        item.catalogPrice() == null ? item.unitPrice() : item.catalogPrice());
                BigDecimal discount = item.discountPercent() == null
                        ? BigDecimal.ZERO : item.discountPercent();
                BigDecimal gross = price.multiply(BigDecimal.valueOf(item.quantity()));
                BigDecimal lineTotal = gross.subtract(gross.multiply(discount)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)).max(BigDecimal.ZERO);
                cartModel.addRow(new Object[]{item.productId(), item.productName(), item.description(), item.sku(),
                        price, item.quantity(), discount, lineTotal, catalogPrice,
                        normalizeProductType(item.productType()), item.categoryId()});
            }
        } finally {
            updatingCart = false;
        }
        if (held.paymentMethod() != null && !held.paymentMethod().isBlank()) {
            selectPaymentMethod(held.paymentMethod());
        }
        setDiscountFieldValue(held.saleDiscountPercent() != null
                ? held.saleDiscountPercent().stripTrailingZeros().toPlainString() : "0");
        lastAcceptedSaleDiscountText = "0";
        for (int row = 0; row < cartModel.getRowCount(); row++) {
            handlePriceEditOverrideAtCart(row);
            handleItemDiscountEditOverrideAtCart(row);
        }
        handleSaleDiscountEditOverride();
        selectCustomerById(held.customerId());
        configureCartTableColumns();
        updateLineTotals();
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
        clearPendingManagerApprovals();
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
            setDiscountFieldValue("0");
        }
    }

    private void updateOverallTotal() {
        BigDecimal subtotal = getCartGrossSubtotal();
        BigDecimal afterItemDiscounts = utils.CurrencyFormatter.normalize(BigDecimal.valueOf(getCartSubtotal()));
        BigDecimal itemDiscountAmount = getItemDiscountTotal();
        BigDecimal saleDiscountAmount = getDiscountAmount(afterItemDiscounts);
        BigDecimal discountAmount = utils.CurrencyFormatter.normalize(itemDiscountAmount.add(saleDiscountAmount));
        VatCalculation vat = calculateVat(getDiscountPercent());
        BigDecimal vatAmount = vat.amount();
        BigDecimal total = utils.CurrencyFormatter.normalize(
                subtotal.subtract(discountAmount).max(BigDecimal.ZERO).add(vatAmount));

        if (subtotalLabel != null) {
            subtotalLabel.setText("Subtotal: " + utils.CurrencyFormatter.format(subtotal));
        }
        if (discountAmountLabel != null) {
            discountAmountLabel.setText("Discount: " + utils.CurrencyFormatter.format(discountAmount));
        }
        if (vatAmountLabel != null) {
            vatAmountLabel.setText("VAT: " + utils.CurrencyFormatter.format(vatAmount));
        }
        totalLabel.setText("Overall Total: " + utils.CurrencyFormatter.format(total));
    }

    private BigDecimal promptForCashCollected(BigDecimal amountDue) {
        BigDecimal due = utils.CurrencyFormatter.normalize(amountDue);

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
        JLabel changeLabel = new JLabel("Change: $0");
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
                        : utils.CurrencyFormatter.normalize(new BigDecimal(text));
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
                result[0] = utils.CurrencyFormatter.normalize(new BigDecimal(collectedField.getText().trim()));
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
