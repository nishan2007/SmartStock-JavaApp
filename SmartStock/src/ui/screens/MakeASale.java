package ui.screens;

import Receipt.ReceiptBuilder;
import Receipt.ReceiptData;
import managers.CompanyCustomizationManager;
import managers.PermissionManager;
import managers.ReceiptNumberManager;
import managers.SessionManager;
import data.DB;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.WindowHelper;
import ui.components.AppMenuBar;


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
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URL;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


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
    private static final String APPLY_SALE_DISCOUNT_PERMISSION = "APPLY_SALE_DISCOUNT";
    private static final String CHANGE_SALE_ITEM_PRICE_PERMISSION = "CHANGE_SALE_ITEM_PRICE";
    private static final int SEARCH_CONTROL_HEIGHT = 28;

    private JTextField searchField;
    private JTable cartTable;
    private DefaultTableModel cartModel;
    private boolean updatingCart = false;
    private JLabel totalLabel;
    private JLabel subtotalLabel;
    private JLabel discountAmountLabel;
    private JTextField discountPercentField;
    private ButtonGroup paymentMethodGroup;
    private JToggleButton cashPaymentButton;
    private JToggleButton cardPaymentButton;
    private JToggleButton chequePaymentButton;
    private JToggleButton accountPaymentButton;
    private String selectedPaymentMethod;
    private JComboBox<CustomerAccountOption> customerAccountBox;
    private JButton addCustomerAccountButton;
    private JButton checkoutBtn;
    private JButton checkoutPrintBtn;
    private JButton holdCartBtn;
    private JButton resumeHeldCartBtn;
    private JLabel selectedStoreLabel;
    private JLabel currentUserLabel;
    private JLabel companyNameLabel;
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
       panel.setBackground(new Color(245, 247, 250));

       // Search and register header
       JPanel searchPanel = new JPanel(new BorderLayout(0, 14));
       searchPanel.setOpaque(false);

       companyLogoLabel = new JLabel("Logo", SwingConstants.CENTER);
       companyLogoLabel.setOpaque(true);
       companyLogoLabel.setBackground(Color.WHITE);
       companyLogoLabel.setForeground(new Color(100, 116, 139));
       companyLogoLabel.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
       companyLogoLabel.setPreferredSize(new Dimension(300, 96));

       companyNameLabel = new JLabel("SmartStock");
       companyNameLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
       companyNameLabel.setForeground(new Color(17, 24, 39));

       JLabel screenTitleLabel = new JLabel("Point of Sale");
       screenTitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 15));
       screenTitleLabel.setForeground(new Color(100, 116, 139));

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
       appLogoLabel.setForeground(new Color(100, 116, 139));
       appLogoLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
       appLogoLabel.setPreferredSize(new Dimension(140, 72));
       setSmartStockAppLogo();

       newItemBtn = createUtilityButton("New Item");
       searchField = new PromptTextField("Scan or enter item information");
       searchField.setFont(new Font("SansSerif", Font.PLAIN, 14));
       searchField.setForeground(new Color(15, 23, 42));
       searchField.setCaretColor(new Color(15, 23, 42));
       searchField.setBackground(Color.WHITE);
       searchField.setBorder(BorderFactory.createCompoundBorder(
               BorderFactory.createLineBorder(new Color(203, 213, 225)),
               BorderFactory.createEmptyBorder(2, 10, 2, 10)
       ));
       setFixedControlHeight(searchField, 0);
       searchField.putClientProperty("JTextField.placeholderText", "Scan or enter item information");
       productDropdownButton = createProductDropdownButton();
       selectedStoreLabel = createMetaLabel("Store: Not selected");
       currentUserLabel = createMetaLabel("No User currently logged in");
       editItemBtn = createUtilityButton("Edit Item");
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
       centerSection.setBackground(Color.WHITE);
       centerSection.setBorder(BorderFactory.createCompoundBorder(
               BorderFactory.createLineBorder(new Color(226, 232, 240)),
               BorderFactory.createEmptyBorder(16, 16, 16, 16)
       ));
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
       searchRow.setBackground(Color.WHITE);
       searchRow.setBorder(BorderFactory.createCompoundBorder(
               BorderFactory.createLineBorder(new Color(226, 232, 240)),
               BorderFactory.createEmptyBorder(7, 14, 7, 14)
       ));
       JPanel productSearchPanel = new JPanel(new BorderLayout(0, 0));
       productSearchPanel.setOpaque(false);
       productSearchPanel.add(searchField, BorderLayout.CENTER);
       productSearchPanel.add(productDropdownButton, BorderLayout.EAST);
       searchRow.add(productSearchPanel, BorderLayout.CENTER);

       searchPanel.add(centerSection, BorderLayout.NORTH);
       searchPanel.add(searchRow, BorderLayout.SOUTH);

	       // Cart table
	       cartModel = new DefaultTableModel(
	               new Object[]{"ID", "Name", "Description", "SKU", "Price", "Qty", "Item Disc %", "Line Total", "Original Price", "Product Type"},
	               0
	       ) {
	           @Override
	           public boolean isCellEditable(int row, int column) {
	               return (column == CART_COL_PRICE && canChangeSaleItemPrice())
	                       || column == CART_COL_QTY
	                       || (column == CART_COL_ITEM_DISCOUNT && canApplySaleDiscount());
	           }
       };
       cartTable = new JTable(cartModel);
       cartTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
       cartTable.setFont(new Font("SansSerif", Font.PLAIN, 14));
       cartTable.setRowHeight(34);
       cartTable.setShowVerticalLines(false);
       cartTable.setGridColor(new Color(226, 232, 240));
       cartTable.setSelectionBackground(new Color(219, 234, 254));
       cartTable.setSelectionForeground(new Color(17, 24, 39));
       cartTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 13));
       cartTable.getTableHeader().setBackground(new Color(241, 245, 249));
       cartTable.getTableHeader().setForeground(new Color(51, 65, 85));
       JScrollPane cartScrollPane = new JScrollPane(cartTable);
       cartScrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
	       cartTable.getColumnModel().getColumn(CART_COL_PRICE).setCellEditor(new DefaultCellEditor(new JTextField()));
	       cartTable.getColumnModel().getColumn(CART_COL_QTY).setCellEditor(new DefaultCellEditor(new JTextField()));
	       cartTable.getColumnModel().getColumn(CART_COL_ITEM_DISCOUNT).setCellEditor(new DefaultCellEditor(new JTextField()));
       configureCartTableColumns();

       panel.add(searchPanel, BorderLayout.NORTH);
       panel.add(cartScrollPane, BorderLayout.CENTER);

       customerAccountBox = new JComboBox<>();
       customerAccountBox.setEditable(true);
       customerAccountBox.setEditor(new PromptComboBoxEditor("Enter customer name"));
       customerAccountBox.setBorder(BorderFactory.createLineBorder(new Color(203, 213, 225)));
       customerAccountBox.setBackground(Color.WHITE);
       customerAccountBox.setFont(new Font("SansSerif", Font.PLAIN, 14));
       customerAccountBox.setPrototypeDisplayValue(new CustomerAccountOption(0, "0000000000", "Enter customer name", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false, ""));
       setFixedControlHeight(customerAccountBox, 360);
       customerAccountBox.setRenderer(new CustomerAccountRenderer());
       addCustomerAccountButton = createUtilityButton("New Customer");
       paymentMethodGroup = new ButtonGroup();
       cashPaymentButton = createPaymentMethodButton("Cash", "CASH");
       cardPaymentButton = createPaymentMethodButton("Card", "CARD");
       chequePaymentButton = createPaymentMethodButton("Cheque", "CHEQUE");
       accountPaymentButton = createPaymentMethodButton("Account", "ACCOUNT");
	       discountPercentField = new JTextField("0", 5);
       discountPercentField.setForeground(new Color(15, 23, 42));
       discountPercentField.setCaretColor(new Color(15, 23, 42));
       discountPercentField.setBackground(Color.WHITE);
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
       bottomPanel.setBackground(Color.WHITE);
       bottomPanel.setBorder(BorderFactory.createCompoundBorder(
               BorderFactory.createLineBorder(new Color(226, 232, 240)),
               BorderFactory.createEmptyBorder(14, 14, 14, 14)
       ));

       JPanel totalsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 6));
       totalsPanel.setOpaque(false);
       JPanel transactionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
       transactionPanel.setOpaque(false);
       transactionPanel.add(cashPaymentButton);
       transactionPanel.add(cardPaymentButton);
       transactionPanel.add(chequePaymentButton);
       transactionPanel.add(accountPaymentButton);
       totalsPanel.add(buildLabeledControl("Discount %", discountPercentField));
	       subtotalLabel = createTotalLabel("Subtotal: $0.00", false);
	       totalsPanel.add(subtotalLabel);
	       discountAmountLabel = createTotalLabel("Discount: $0.00", false);
	       totalsPanel.add(discountAmountLabel);
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

       JPanel bottomTopPanel = new JPanel(new BorderLayout(8, 4));
       bottomTopPanel.setOpaque(false);
       bottomTopPanel.add(transactionPanel, BorderLayout.WEST);
       bottomTopPanel.add(totalsPanel, BorderLayout.EAST);
       bottomPanel.add(bottomTopPanel, BorderLayout.CENTER);
       bottomPanel.add(actionPanel, BorderLayout.SOUTH);

       panel.add(bottomPanel, BorderLayout.SOUTH);

       //Add panel to frame
       add(panel);
       refreshPermissionButtons();

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
               if (WindowHelper.focusIfAlreadyOpen(NewItem.class)) {
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
	               SwingUtilities.invokeLater(() -> {
	                   if (!canApplySaleDiscount()) {
	                       discountPercentField.setText("0");
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
        button.setForeground(new Color(15, 23, 42));
        button.setBackground(new Color(37, 99, 235));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(29, 78, 216)),
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
        button.putClientProperty("Button.disabledText", new Color(0, 0, 0));
        button.setBackground(new Color(220, 38, 38));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(true);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setBorder(new OutsideRoundedBorder(new Color(0, 0, 0), 4, 12, new Insets(12, 24, 12, 24)));
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
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(37, 99, 235));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(new OutsideRoundedBorder(new Color(0, 0, 0), 4, 12, new Insets(12, 22, 12, 22)));
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
        } else if ("ACCOUNT".equals(method) && accountPaymentButton != null) {
            accountPaymentButton.setSelected(true);
        }
        updatePaymentButtonStyles();
        updateCheckoutAvailability();
        updateCustomerAccountEnabled();
    }

    private void updatePaymentButtonStyles() {
        stylePaymentButton(cashPaymentButton, "CASH".equals(selectedPaymentMethod));
        stylePaymentButton(cardPaymentButton, "CARD".equals(selectedPaymentMethod));
        stylePaymentButton(chequePaymentButton, "CHEQUE".equals(selectedPaymentMethod));
        stylePaymentButton(accountPaymentButton, "ACCOUNT".equals(selectedPaymentMethod));
    }

    private void stylePaymentButton(JToggleButton button, boolean selected) {
        // Selected/unselected payment button colors and border thickness are controlled here.
        if (button == null) {
            return;
        }
        button.setBackground(selected ? new Color(30, 64, 175) : new Color(37, 99, 235));
        button.setBorder(new OutsideRoundedBorder(
                selected ? new Color(15, 23, 42) : new Color(29, 78, 216),
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

    private JButton createUtilityButton(String text) {
        // Small utility buttons like New Item/New Customer: text color, fill, border, and padding live here.
        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 13));
        button.setFocusPainted(false);
        button.setForeground(new Color(30, 41, 59));
        button.setBackground(new Color(248, 250, 252));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225)),
                BorderFactory.createEmptyBorder(7, 13, 7, 13)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JButton createActionUtilityButton(String text) {
        // Bottom-right Hold/Resume buttons: larger size plus thick rounded border settings live here.
        JButton button = new RoundedFillButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 15));
        button.setFocusPainted(false);
        button.setForeground(new Color(30, 41, 59));
        button.setBackground(new Color(248, 250, 252));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI());
        button.setBorder(new OutsideRoundedBorder(Color.BLACK, 4, 12, new Insets(12, 22, 12, 22)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(150, 56));
        return button;
    }

    private JButton createProductDropdownButton() {
        // Green arrow section on the product search field: width, height, fill, text, and border live here.
        JButton button = new JButton("▼");
        button.setFont(new Font("SansSerif", Font.BOLD, 11));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(22, 163, 74));
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createLineBorder(new Color(21, 128, 61)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        Dimension size = new Dimension(38, SEARCH_CONTROL_HEIGHT);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setToolTipText("Show product list");
        return button;
    }

    private JLabel createMetaLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        label.setForeground(new Color(71, 85, 105));
        return label;
    }

    private JLabel createTotalLabel(String text, boolean prominent) {
        // Totals row label sizes and colors are controlled here.
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", prominent ? Font.BOLD : Font.PLAIN, prominent ? 18 : 14));
        label.setForeground(prominent ? new Color(15, 23, 42) : new Color(71, 85, 105));
        return label;
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
        title.setForeground(new Color(100, 116, 139));
        panel.add(title, BorderLayout.NORTH);
        panel.add(control, BorderLayout.CENTER);
        return panel;
    }

    private void loadCompanyBranding() {
        if (companyNameLabel == null || companyLogoLabel == null) {
            return;
        }

        companyNameLabel.setText("SmartStock");
        companyLogoLabel.setText("Logo");
        companyLogoLabel.setIcon(null);

        new SwingWorker<CompanyBranding, Void>() {
            @Override
            protected CompanyBranding doInBackground() {
                CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
                BufferedImage logo = CompanyCustomizationManager.loadCompanyLogo(settings);
                return new CompanyBranding(settings.companyName(), logo);
            }

            @Override
            protected void done() {
                try {
                    CompanyBranding branding = get();
                    companyNameLabel.setText(branding.companyName());
                    if (branding.logo() != null) {
                        setCompanyLogo(branding.logo());
                    } else {
                        setFallbackCompanyLogo();
                    }
                } catch (Exception ex) {
                    setFallbackCompanyLogo();
                }
            }
        }.execute();
    }

    private void setCompanyLogo(BufferedImage logo) {
        Image scaled = scaleToFit(logo, 280, 84);
        companyLogoLabel.setText("");
        companyLogoLabel.setIcon(new ImageIcon(scaled));
    }

    private void setFallbackCompanyLogo() {
        ImageIcon centerLogoIcon = loadCenterLogoIcon();
        if (centerLogoIcon != null && centerLogoIcon.getIconWidth() > 0) {
            Image scaled = scaleToFit(centerLogoIcon.getImage(), 280, 84);
            companyLogoLabel.setText("");
            companyLogoLabel.setIcon(new ImageIcon(scaled));
            return;
        }

        companyLogoLabel.setIcon(null);
        String name = companyNameLabel == null ? "S" : companyNameLabel.getText().trim();
        companyLogoLabel.setText(name.isBlank() ? "S" : name.substring(0, 1).toUpperCase());
        companyLogoLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
    }

    private void setSmartStockAppLogo() {
        if (appLogoLabel == null) {
            return;
        }
        ImageIcon centerLogoIcon = loadCenterLogoIcon();
        if (centerLogoIcon != null && centerLogoIcon.getIconWidth() > 0) {
            Image scaled = scaleToFit(centerLogoIcon.getImage(), 132, 58);
            appLogoLabel.setText("");
            appLogoLabel.setIcon(new ImageIcon(scaled));
            return;
        }

        appLogoLabel.setIcon(null);
        appLogoLabel.setText("SmartStock");
    }

    private Image scaleToFit(Image image, int maxWidth, int maxHeight) {
        int width = Math.max(image.getWidth(null), 1);
        int height = Math.max(image.getHeight(null), 1);
        double scale = Math.min((double) maxWidth / width, (double) maxHeight / height);
        scale = Math.min(scale, 1.0);
        int targetWidth = Math.max((int) Math.round(width * scale), 1);
        int targetHeight = Math.max((int) Math.round(height * scale), 1);
        return image.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
    }

    private record CompanyBranding(String companyName, BufferedImage logo) {
    }


    private ImageIcon loadCenterLogoIcon() {
        String[] resourcePaths = {
                "/Images/CenterLogo.png",
                "Images/CenterLogo.png",
                "/CenterLogo.png",
                "CenterLogo.png"
        };

        for (String path : resourcePaths) {
            URL url = getClass().getResource(path);
            if (url != null) {
                return new ImageIcon(url);
            }
        }

        String[] filePaths = {
                "src/main/Images/CenterLogo.png",
                "src/main/resources/Images/CenterLogo.png",
                "src/Images/CenterLogo.png",
                "Images/CenterLogo.png",
                "CenterLogo.png"
        };

        for (String path : filePaths) {
            ImageIcon icon = new ImageIcon(path);
            if (icon.getIconWidth() > 0) {
                return icon;
            }
        }

        System.out.println("Center logo not found. Checked classpath and common file locations.");
        return null;
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
	                discountPercentField.setText("0");
	            }
	        }
	        if (cartModel != null && !canChangeSaleItemPrice()) {
	            restoreUnauthorizedCartPrices();
	        }
	    }

    private boolean canApplySaleDiscount() {
        return PermissionManager.hasPermission(APPLY_SALE_DISCOUNT_PERMISSION);
    }

    private boolean canChangeSaleItemPrice() {
        return PermissionManager.hasPermission(CHANGE_SALE_ITEM_PRICE_PERMISSION);
    }

    private void configureCartTableColumns() {
        if (cartTable == null || cartTable.getColumnModel().getColumnCount() < 10) {
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
        String searchText = searchField.getText().trim();

        if (SessionManager.getCurrentLocationId() == null) {
            JOptionPane.showMessageDialog(this, "No store is selected for this session.");
            return;
        }

        String sql = """
            SELECT p.product_id, p.name, p.description, p.sku, p.price,
                   COALESCE(p.product_type, 'INVENTORY') AS product_type,
                   COALESCE(i.quantity_on_hand, 0) AS quantity_on_hand
            FROM products p
            LEFT JOIN inventory i
                ON p.product_id = i.product_id
               AND i.location_id = ?
            WHERE (? = '' OR p.name ILIKE ? OR p.sku ILIKE ?)
            ORDER BY p.name
            LIMIT 250
            """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, SessionManager.getCurrentLocationId());
            ps.setString(2, searchText);
            ps.setString(3, "%" + searchText + "%");
            ps.setString(4, "%" + searchText + "%");

            ResultSet rs = ps.executeQuery();

            java.util.List<Object[]> rows = new java.util.ArrayList<>();

            while (rs.next()) {
                rows.add(new Object[]{
                        rs.getInt("product_id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("sku"),
                        rs.getDouble("price"),
                        rs.getString("product_type"),
                        rs.getInt("quantity_on_hand")
                });
            }

            if (rows.isEmpty()) {
                closeSearchPopup();
                if (showMessages) {
                    JOptionPane.showMessageDialog(this, searchText.isEmpty() ? "No products found for this store." : "No matching products found.");
                }
                return;
            }

            showSearchResultsPopup(rows);

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Database error: " + e.getMessage());
        }
    }

    private void showSearchResultsPopup(java.util.List<Object[]> rows) {
        if (searchPopup == null) {
            searchPopup = new JPopupMenu();
            searchPopup.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            searchPopup.setFocusable(false);

            String[] columns = {"ID", "Name", "Description", "SKU", "Price", "Type", "Stock"};
            DefaultTableModel resultsModel = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

            searchResultsTable = new JTable(resultsModel);
            searchResultsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            searchResultsTable.setAutoCreateRowSorter(true);
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

        if (searchPopup.isVisible()) {
            searchPopup.setVisible(false);
        }

        searchPopup.show(searchField, 0, searchField.getHeight());
        searchField.requestFocusInWindow();
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
        String description = String.valueOf(searchResultsTable.getModel().getValueAt(selectedRow, 2));
        String sku = String.valueOf(searchResultsTable.getModel().getValueAt(selectedRow, 3));
        double price = ((Number) searchResultsTable.getModel().getValueAt(selectedRow, 4)).doubleValue();
        String productType = normalizeProductType(String.valueOf(searchResultsTable.getModel().getValueAt(selectedRow, 5)));

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

        addToCart(productId, name, description, sku, price, qty, productType);
        closeSearchPopup();
        searchField.requestFocusInWindow();
        searchField.selectAll();
    }

    private void closeSearchPopup() {
        if (searchPopup != null) {
            searchPopup.setVisible(false);
        }
    }
    private void addToCart(int productId, String name, String description, String sku, double price, int qty, String productType) {
        for (int i = 0; i < cartModel.getRowCount(); i++) {
            int existingProductId = Integer.parseInt(cartModel.getValueAt(i, CART_COL_ID).toString());

            if (existingProductId == productId) {
                int existingQty = Integer.parseInt(cartModel.getValueAt(i, CART_COL_QTY).toString());
                int newQty = existingQty + qty;

                cartModel.setValueAt(newQty, i, CART_COL_QTY);
                updateLineTotals();
                configureCartTableColumns();
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
                normalizeProductType(productType)
        });
        updateLineTotals();
        configureCartTableColumns();
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

    private String getCartProductType(int row) {
        if (cartModel.getColumnCount() <= CART_COL_PRODUCT_TYPE) {
            return "INVENTORY";
        }
        return normalizeProductType(String.valueOf(cartModel.getValueAt(row, CART_COL_PRODUCT_TYPE)));
    }

    private void restoreUnauthorizedCartPrices() {
        if (cartModel == null || updatingCart) {
            return;
        }

        updatingCart = true;
        try {
            for (int i = 0; i < cartModel.getRowCount(); i++) {
                Object originalPrice = cartModel.getValueAt(i, CART_COL_ORIGINAL_PRICE);
                if (originalPrice != null) {
                    cartModel.setValueAt(parseMoneyOrZero(originalPrice), i, CART_COL_PRICE);
                }
            }
        } finally {
            updatingCart = false;
        }
        updateLineTotals();
    }
    private void updateLineTotals() {
        updatingCart = true;
        try {
            for (int i = 0; i < cartModel.getRowCount(); i++) {
                Object priceValue = canChangeSaleItemPrice()
                        ? cartModel.getValueAt(i, CART_COL_PRICE)
                        : cartModel.getValueAt(i, CART_COL_ORIGINAL_PRICE);
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
                if (!canApplySaleDiscount()) {
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
            if (!canChangeSaleItemPrice()) {
                price = parseMoneyOrZero(cartModel.getValueAt(i, CART_COL_ORIGINAL_PRICE));
            }
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
        BigDecimal subtotal = BigDecimal.valueOf(getCartSubtotal()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = getDiscountAmount(subtotal);
        return subtotal.subtract(discountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
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

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
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
        editorField.setForeground(new Color(15, 23, 42));
        editorField.setCaretColor(new Color(15, 23, 42));
        editorField.setBackground(Color.WHITE);
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

        String paymentMethod = selectedPaymentMethod;
        if (paymentMethod == null || paymentMethod.isBlank()) {
            JOptionPane.showMessageDialog(this, "Select a payment method before checkout.");
            updateCheckoutAvailability();
            return;
        }
        CustomerAccountOption selectedCustomer = getSelectedCustomerAccount();

        boolean chargeCustomerAccount = "ACCOUNT".equals(paymentMethod);
        boolean cashPayment = "CASH".equals(paymentMethod);

        if (chargeCustomerAccount && selectedCustomer == null) {
            JOptionPane.showMessageDialog(this, "Select a customer account for account payment.");
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
                BigDecimal subtotalAmount = getCartGrossSubtotal();
                BigDecimal itemDiscountTotal = getItemDiscountTotal();
                BigDecimal lineSubtotalAfterItemDiscounts = BigDecimal.valueOf(getCartSubtotal()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal saleLevelDiscountAmount = lineSubtotalAfterItemDiscounts.multiply(discountPercent)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal saleTotal = lineSubtotalAfterItemDiscounts.subtract(saleLevelDiscountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
                BigDecimal discountAmount = itemDiscountTotal.add(saleLevelDiscountAmount).setScale(2, RoundingMode.HALF_UP);
                BigDecimal saleDiscountMultiplier = BigDecimal.ONE.subtract(
                        discountPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
                );
                if (chargeCustomerAccount) {
                    validateAndChargeCustomerAccount(conn, selectedCustomer.customerId, saleTotal);
                }

                ReceiptNumberManager.ReceiptNumber receipt = ReceiptNumberManager.nextReceipt(locationId);
                String paymentStatus = chargeCustomerAccount ? "UNPAID" : "PAID";
                BigDecimal amountPaid = chargeCustomerAccount ? BigDecimal.ZERO : saleTotal;
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
	                            discount_amount
	                        )
	                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
	                    saleStmt.executeUpdate();

                    try (ResultSet generatedKeys = saleStmt.getGeneratedKeys()) {
                        if (!generatedKeys.next()) {
                            throw new SQLException("Failed to create sale.");
                        }
                        saleId = generatedKeys.getInt(1);
                    }
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

                String insertItemSql = """
                        INSERT INTO sale_items (
                            sale_id,
                            product_id,
                            quantity,
                            unit_price,
                            original_unit_price,
                            discount_percent,
                            discount_amount,
                            product_type
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """;
	                String insertMovementSql = "INSERT INTO inventory_movements (product_id, location_id, change_qty, reason, note, user_name) VALUES (?, ?, ?, ?, ?, ?)";
                String ensureInventorySql = "INSERT INTO inventory (product_id, location_id, quantity_on_hand, reorder_level) VALUES (?, ?, 0, 0) ON CONFLICT (product_id, location_id) DO NOTHING";
                String updateInventorySql = "UPDATE inventory SET quantity_on_hand = quantity_on_hand - ? WHERE product_id = ? AND location_id = ? AND quantity_on_hand >= ?";

                try (PreparedStatement itemStmt = conn.prepareStatement(insertItemSql);
                     PreparedStatement movementStmt = conn.prepareStatement(insertMovementSql);
                     PreparedStatement ensureInventoryStmt = conn.prepareStatement(ensureInventorySql);
                     PreparedStatement updateInventoryStmt = conn.prepareStatement(updateInventorySql)) {

	                    for (int i = 0; i < cartModel.getRowCount(); i++) {
		                        int productId = Integer.parseInt(cartModel.getValueAt(i, CART_COL_ID).toString());
		                        int qty = Integer.parseInt(cartModel.getValueAt(i, CART_COL_QTY).toString());
	                        String productType = getCartProductType(i);
	                        BigDecimal originalPrice = canChangeSaleItemPrice()
	                                ? parseMoneyOrZero(cartModel.getValueAt(i, CART_COL_PRICE))
	                                : parseMoneyOrZero(cartModel.getValueAt(i, CART_COL_ORIGINAL_PRICE));
		                        BigDecimal itemDiscountPercent = parsePercentOrZero(cartModel.getValueAt(i, CART_COL_ITEM_DISCOUNT));
		                        BigDecimal itemDiscountMultiplier = BigDecimal.ONE.subtract(
		                                itemDiscountPercent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)
		                        );
		                        BigDecimal itemDiscountedPrice = originalPrice.multiply(itemDiscountMultiplier).setScale(2, RoundingMode.HALF_UP);
		                        BigDecimal chargedPrice = itemDiscountedPrice.multiply(saleDiscountMultiplier).setScale(2, RoundingMode.HALF_UP);
		                        BigDecimal itemDiscountAmount = originalPrice
		                                .multiply(BigDecimal.valueOf(qty))
		                                .multiply(itemDiscountPercent)
		                                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

		                        itemStmt.setInt(1, saleId);
		                        itemStmt.setInt(2, productId);
		                        itemStmt.setInt(3, qty);
		                        itemStmt.setBigDecimal(4, chargedPrice);
		                        itemStmt.setBigDecimal(5, originalPrice);
		                        itemStmt.setBigDecimal(6, itemDiscountPercent);
		                        itemStmt.setBigDecimal(7, itemDiscountAmount);
		                        itemStmt.setString(8, productType);
		                        itemStmt.addBatch();

                        if (isInventoryProduct(productType)) {
                            ensureInventoryStmt.setInt(1, productId);
                            ensureInventoryStmt.setInt(2, locationId);
                            ensureInventoryStmt.executeUpdate();

                            updateInventoryStmt.setInt(1, qty);
                            updateInventoryStmt.setInt(2, productId);
                            updateInventoryStmt.setInt(3, locationId);
                            updateInventoryStmt.setInt(4, qty);
                            if (updateInventoryStmt.executeUpdate() == 0) {
                                throw new SQLException("Not enough inventory for " + cartModel.getValueAt(i, CART_COL_NAME) + ".");
                            }

                            movementStmt.setInt(1, productId);
                            movementStmt.setInt(2, locationId);
                            movementStmt.setInt(3, -qty);
                            movementStmt.setString(4, "SALE");
                            movementStmt.setString(5, "sale_id=" + saleId);
                            movementStmt.setString(6, SessionManager.getCurrentUserDisplayName());
                            movementStmt.executeUpdate();
                        }
                    }

                    itemStmt.executeBatch();
                }

                conn.commit();
	                String successMessage = "Sale completed successfully.\nReceipt #: " + receipt.receiptNumber() + "\nSale ID: " + saleId;
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
	                discountPercentField.setText("0");
	                clearHeldCartSelection();
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
	                    holdStmt.setString(5, holdName.trim());
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

                conn.commit();
	                JOptionPane.showMessageDialog(this, "Cart held successfully. Hold ID: " + heldCartId);
	                cartModel.setRowCount(0);
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
                SELECT product_id,
                       product_name,
                       description,
                       sku,
                       unit_price,
                       quantity,
                       COALESCE(discount_percent, 0) AS discount_percent,
                       COALESCE(product_type, 'INVENTORY') AS product_type
                FROM held_cart_items
                WHERE held_cart_id = ?
                ORDER BY held_cart_item_id
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
	                            normalizeProductType(rs.getString("product_type"))
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
        String sql = "DELETE FROM held_carts WHERE held_cart_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, heldCartId);
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
        applyCustomerAccountFilter("", false);
        customerAccountBox.setSelectedItem("");
        if (customerAccountBox.getEditor() != null) {
            customerAccountBox.getEditor().setItem("");
        }
        selectedPaymentMethod = null;
        if (paymentMethodGroup != null) {
            paymentMethodGroup.clearSelection();
        }
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

    private void validateAndChargeCustomerAccount(Connection conn, int customerId, BigDecimal saleTotal) throws SQLException {
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
	                INSERT INTO customer_account_transactions (customer_id, sale_id, amount, transaction_type, note, user_name)
	                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            ps.setInt(2, saleId);
	            ps.setBigDecimal(3, amount);
	            ps.setString(4, type);
	            ps.setString(5, note);
	            ps.setString(6, SessionManager.getCurrentUserDisplayName());
	            ps.executeUpdate();
        }
    }

    private void updateOverallTotal() {
        BigDecimal subtotal = getCartGrossSubtotal();
        BigDecimal afterItemDiscounts = BigDecimal.valueOf(getCartSubtotal()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal itemDiscountAmount = getItemDiscountTotal();
        BigDecimal saleDiscountAmount = getDiscountAmount(afterItemDiscounts);
        BigDecimal discountAmount = itemDiscountAmount.add(saleDiscountAmount).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.subtract(discountAmount).max(BigDecimal.ZERO);

        if (subtotalLabel != null) {
            subtotalLabel.setText(String.format("Subtotal: $%.2f", subtotal.doubleValue()));
        }
        if (discountAmountLabel != null) {
            discountAmountLabel.setText(String.format("Discount: $%.2f", discountAmount.doubleValue()));
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
            g2.setColor(new Color(100, 116, 139));
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
