package ui.screens;
import managers.CompanyCustomizationManager;
import managers.NavigationManager;
import managers.PermissionManager;
import managers.SupabaseSessionManager;
import managers.SessionManager;
import models.AppNotification;
import services.LanApiClient;
import services.NotificationService;
import ui.components.AppMenuBar;
import ui.design.DeckersLogoManager;
import ui.design.DeckersPalette;
import ui.helpers.WindowHelper;
import ui.helpers.ThemeManager;
import ui.helpers.UiTaskRunner;
import ui.helpers.SessionDataCache;
import ui.helpers.UiDebouncer;
import ui.helpers.WelcomeGreetingHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class MainMenu extends JFrame {
    private static final Map<String, ImageIcon> MENU_ICON_CACHE = new ConcurrentHashMap<>();
    private static boolean drawStartPromptShownThisAppSession;
    private static final int MENU_ICON_SIZE = 74;
    private static final int MENU_TILE_WIDTH = 315;
    private static final int MENU_TILE_MIN_WIDTH = 248;
    private static final int MENU_TILE_MAX_WIDTH = 345;
    private static final int OPERATION_MENU_TILE_WIDTH = 240;
    private static final int MENU_TILE_HEIGHT = 126;
    private static final int VERTICAL_MENU_TILE_HEIGHT = 182;
    private static final int MENU_TILE_GAP = 14;
    private static final int SECTION_SIDE_PADDING = 18;
    private static final int LEFT_SECTION_COLUMNS = 4;
    private static final int RIGHT_SECTION_COLUMNS = 1;
    private static final int COLUMN_GAP = 18;
    private final JButton makeSaleButton;
    private final JButton returnSaleButton;
    private final JButton balanceDrawButton;
    private final JButton changeBasketButton;
    private final JButton balanceSheetButton;
    private final JButton ordersManagerDashboardButton;
    private final JButton reportsButton;
    private final JButton enterInventoryButton;
    private final JButton receivingHistoryButton;
    private final JButton storeTransferButton;
    private final JButton customOrderItemsButton;
    private final JButton departmentListButton;
    private final JButton vendorListButton;
    private final JButton viewSalesButton;
    private final JButton customerAccountsButton;
    private final JButton customerTransactionHistoryButton;
    private final JButton invoicesButton;
    private final JButton customOrdersButton;
    private final JButton ordersButton;
    private final JButton viewInventoryButton;
    private final JButton priceTagPrintingButton;
    private final JButton addItemButton;
    private final JButton editItemsButton;
    private final JButton timeClockButton;
    private final JButton payrollDashboardButton;
    private final JButton weeklyScheduleButton;
    private final JButton employeeManagementButton;
    private final JButton rolesPermissionsButton;
    private final JButton deviceManagementButton;
    private final JButton machineManagementButton;
    private final JButton partsManagementButton;
    private final JButton maintenanceManagementButton;
    private final JButton companyCustomizationButton;
    private final JButton workstationPreferencesButton;
    private final JButton logoutButton;
    private final Set<String> urgentPopupKeysShown = new HashSet<>();
    private Timer notificationRefreshTimer;

    public MainMenu() {
        setTitle("SmartStock - Main Menu");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1800, 850);
        setMinimumSize(new Dimension(900, 650));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        setJMenuBar(AppMenuBar.create(this, "MainMenu"));

        boolean dark = ThemeManager.isDarkModeEnabled();
        Color backgroundColor = backgroundColor();
        Color surfaceColor = surfaceColor();
        Color textColor = textColor();
        Color mutedColor = mutedColor();

        JPanel mainPanel = new JPanel(new BorderLayout(0, 0));
        mainPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        mainPanel.setBorder(new EmptyBorder(0, 0, 24, 0));
        mainPanel.setBackground(backgroundColor);

        JLabel titleLabel = new JLabel(WelcomeGreetingHelper.currentGreeting().title());
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        titleLabel.setForeground(textColor);
        titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        JLabel subtitleLabel = new JLabel("Choose a section to continue");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        subtitleLabel.setForeground(mutedColor);
        subtitleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        JPanel headerPanel = createHeaderPanel(titleLabel, subtitleLabel);

        makeSaleButton = createMenuButton("Make a Sale", "Create a new sale transaction", loadIcon("src/ICONS/MainMenuMakeSale.png"));
        returnSaleButton = createMenuButton("Returns", "Return items from a completed sale", loadIcon("src/ICONS/MainMenuReturns.png"));
        balanceDrawButton = createMenuButton("Balance Draw", "Start, count, and close the cash drawer", loadIcon("src/ICONS/MainMenuBalanceDraw.png"));
        changeBasketButton = createMenuButton("Change Basket", "Count the store change basket against its target", loadIcon("src/ICONS/MainMenuBalanceDraw.png"));
        balanceSheetButton = createMenuButton("Balance Sheet", "Review income, expenses, assets, and liabilities", loadIcon("src/ICONS/MainMenuBalanceSheet.png"));
        ordersManagerDashboardButton = createMenuButton("Orders Manager Dashboard", "Review order risk, refunds, balances, and audit activity", loadIcon("src/ICONS/MainMenuOrdersDashboard.png"));
        reportsButton = createMenuButton("Reports", "Analyze sales, products, employees, cash flow, and expenses", loadIcon("src/ICONS/MainMenuEndOfDay.png"));
        enterInventoryButton = createMenuButton("Receiving Inventory", "Add received stock to inventory", loadIcon("src/ICONS/MainMenuReceivingInventory.png"));
        receivingHistoryButton = createMenuButton("Receiving History", "Review received inventory", loadIcon("src/ICONS/MainMenuReceivingHistory.png"));
        storeTransferButton = createMenuButton("Store Transfer", "Move stock between stores", loadIcon("src/ICONS/MainMenuStoreTransfer.png"));
        customOrderItemsButton = createMenuButton("Custom Order Items", "Manage printable items and stock levels", loadIcon("src/ICONS/MainMenuCustomOrderItems.png"));
        departmentListButton = createMenuButton("Departments", "Manage item departments", loadIcon("src/ICONS/MainMenuDepartments.png"));
        vendorListButton = createMenuButton("Vendors", "Manage product vendors", loadIcon("src/ICONS/MainMenuVendors.png"));
        viewSalesButton = createMenuButton("View Sales", "Review previous transactions", loadIcon("src/ICONS/MainMenuViewSales.png"));
        customerAccountsButton = createMenuButton("Customers", "Manage customer credit accounts", loadIcon("src/ICONS/MainMenuCustomers.png"));
        customerTransactionHistoryButton = createMenuButton("Customer History", "Open full transaction history for a customer", loadIcon("src/ICONS/MainMenuCustomerHistory.png"));
        invoicesButton = createMenuButton("Quotations & Invoices", "Create quotes, take payments, and post deliveries", loadIcon("src/ICONS/MainMenuInvoices.png"));
        customOrdersButton = createMenuButton("Customer Orders", "Take a new customized customer order", loadIcon("src/ICONS/MainMenuCustomOrders.png"));
        ordersButton = createMenuButton("Orders", "Lookup, assign, and deliver custom orders", loadIcon("src/ICONS/MainMenuOrders.png"));
        viewInventoryButton = createMenuButton("View Inventory", "View current inventory levels", loadIcon("src/ICONS/MainMenuViewInventory.png"));
        priceTagPrintingButton = createMenuButton("Price Tag Printing", "Select normal or custom items and print sticker tags", loadIcon("src/ICONS/MainMenuViewInventory.png"));
        addItemButton = createMenuButton("Add Item", "Add a new product to inventory", loadIcon("src/ICONS/MainMenuAddItem.png"));
        editItemsButton = createMenuButton("Edit Items", "Update product information", loadIcon("src/ICONS/MainMenuEditItems.png"));
        timeClockButton = createMenuButton("Time Clock", "Clock employees in and out", loadIcon("src/ICONS/MainMenuTimeClock.png"));
        payrollDashboardButton = createMenuButton("Payroll", "Review pay periods and time records", loadIcon("src/ICONS/MainMenuPayroll.png"));
        weeklyScheduleButton = createMenuButton("Weekly Schedule", "See who is working each day", loadIcon("src/ICONS/MainMenuEmployees.png"));
        employeeManagementButton = createMenuButton("Employees", "Manage employee accounts", loadIcon("src/ICONS/MainMenuEmployees.png"));
        rolesPermissionsButton = createMenuButton("Roles & Permissions", "Configure user access", loadIcon("src/ICONS/MainMenuRolesPermissions.png"));
        deviceManagementButton = createMenuButton("Device Management", "Review devices and approve or block sign-ins", loadIcon("src/ICONS/MainMenuDeviceManagement.png"));
        machineManagementButton = createMenuButton("Machines", "Create, update, and delete machine records", loadIcon("src/ICONS/MainMenuMachines.png"));
        partsManagementButton = createMenuButton("Parts", "Create, update, and delete maintenance parts", loadIcon("src/ICONS/MainMenuParts.png"));
        maintenanceManagementButton = createMenuButton("Maintenance", "Manage machines, parts, service logs, and problem tickets", loadIcon("src/ICONS/MainMenuMaintenance.png"));
        companyCustomizationButton = createMenuButton("Company Preferences", "Company identity and receipts", loadIcon("src/ICONS/MainMenuCompanyPreferences.png"));
        workstationPreferencesButton = createMenuButton("Workstation Preferences", "Device-level workstation and printing behavior", loadIcon("src/ICONS/MainMenuWorkstationPreferences.png"));

        JPanel leftSectionStackPanel = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        leftSectionStackPanel.setLayout(new BoxLayout(leftSectionStackPanel, BoxLayout.Y_AXIS));
        leftSectionStackPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        leftSectionStackPanel.setBackground(backgroundColor);
        leftSectionStackPanel.add(createSectionPanel(
                "Point of Sale",
                DeckersPalette.ORANGE,
                makeSaleButton,
                returnSaleButton,
                invoicesButton,
                viewSalesButton
        ));
        leftSectionStackPanel.add(Box.createVerticalStrut(18));
        leftSectionStackPanel.add(createSectionPanel(
                "Orders",
                DeckersPalette.MAGENTA,
                ordersManagerDashboardButton,
                customOrdersButton,
                ordersButton
        ));
        leftSectionStackPanel.add(Box.createVerticalStrut(18));
        leftSectionStackPanel.add(createSectionPanel(
                "Inventory",
                DeckersPalette.LIME,
                enterInventoryButton,
                receivingHistoryButton,
                storeTransferButton,
                customOrderItemsButton,
                viewInventoryButton,
                priceTagPrintingButton,
                addItemButton,
                editItemsButton
        ));
        leftSectionStackPanel.add(Box.createVerticalStrut(18));
        leftSectionStackPanel.add(createSectionPanel(
                "Employee",
                DeckersPalette.YELLOW,
                timeClockButton,
                payrollDashboardButton,
                weeklyScheduleButton,
                employeeManagementButton
        ));
        leftSectionStackPanel.add(Box.createVerticalStrut(18));
        leftSectionStackPanel.add(createSectionPanel(
                "Admin",
                DeckersPalette.PURPLE,
                departmentListButton,
                vendorListButton,
                rolesPermissionsButton,
                deviceManagementButton,
                machineManagementButton,
                partsManagementButton,
                companyCustomizationButton,
                workstationPreferencesButton
        ));

        JPanel operationsColumnPanel = new JPanel(new BorderLayout());
        operationsColumnPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        operationsColumnPanel.setBackground(backgroundColor);
        operationsColumnPanel.add(createSectionPanel(
                "Operations",
                DeckersPalette.CORAL,
                RIGHT_SECTION_COLUMNS,
                balanceDrawButton,
                changeBasketButton,
                balanceSheetButton,
                reportsButton,
                customerAccountsButton,
                customerTransactionHistoryButton,
                maintenanceManagementButton
        ), BorderLayout.NORTH);

        JPanel menuColumnsPanel = new MenuColumnsPanel(leftSectionStackPanel, operationsColumnPanel);
        menuColumnsPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        menuColumnsPanel.setBackground(backgroundColor);

        JPanel scrollContentPanel = new ViewportWidthPanel(new BorderLayout());
        scrollContentPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        scrollContentPanel.setBackground(backgroundColor);
        scrollContentPanel.add(menuColumnsPanel, BorderLayout.NORTH);

        JScrollPane sectionScrollPane = new JScrollPane(scrollContentPanel);
        sectionScrollPane.setBorder(BorderFactory.createEmptyBorder());
        sectionScrollPane.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        sectionScrollPane.setBackground(backgroundColor);
        sectionScrollPane.getViewport().putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        sectionScrollPane.getViewport().setBackground(backgroundColor);
        sectionScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        sectionScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sectionScrollPane.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                leftSectionStackPanel.revalidate();
                operationsColumnPanel.revalidate();
            }
        });

        logoutButton = new JButton("Logout");
        logoutButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        logoutButton.setFocusPainted(false);
        logoutButton.setPreferredSize(new Dimension(130, 34));
        logoutButton.setBackground(dark ? new Color(45, 45, 45) : surfaceColor);
        logoutButton.setForeground(textColor);

        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        footerPanel.setBackground(backgroundColor);

        JPanel footerActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footerActionPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        footerActionPanel.setBackground(backgroundColor);
        footerActionPanel.add(logoutButton);
        footerPanel.add(footerActionPanel, BorderLayout.EAST);

        JPanel contentPanel = new JPanel(new BorderLayout(20, 20));
        contentPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        contentPanel.setBackground(backgroundColor);
        contentPanel.setBorder(new EmptyBorder(0, 28, 0, 28));
        contentPanel.add(sectionScrollPane, BorderLayout.CENTER);
        contentPanel.add(footerPanel, BorderLayout.SOUTH);

        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        applyPermissions();
        add(mainPanel, BorderLayout.CENTER);
        wireActions();
        wireWindowSessionHandling();
        WindowHelper.configurePosWindow(this);
        SwingUtilities.invokeLater(() -> {
            refreshNotifications(true);
            startNotificationRefreshTimer();
            promptToStartDrawIfNeeded();
        });
    }
    private ImageIcon loadIcon(String path) {
        String fileName = new File(path).getName();
        ImageIcon cached = MENU_ICON_CACHE.get(fileName);
        if (cached != null) return cached;
        ImageIcon icon = null;
        java.net.URL resource = getClass().getResource("/ICONS/" + fileName);
        if (resource != null) {
            icon = new ImageIcon(resource);
        }

        if (icon == null || icon.getIconWidth() <= 0) {
            icon = new ImageIcon(path);
        }

        if (icon.getIconWidth() <= 0) {
            icon = new ImageIcon("SmartStock/" + path);
        }

        if (icon.getIconWidth() <= 0) {
            ImageIcon fallback = createFallbackIcon();
            MENU_ICON_CACHE.putIfAbsent(fileName, fallback);
            return fallback;
        }

        Image img = icon.getImage().getScaledInstance(MENU_ICON_SIZE, MENU_ICON_SIZE, Image.SCALE_SMOOTH);
        ImageIcon scaled = new ImageIcon(img);
        MENU_ICON_CACHE.putIfAbsent(fileName, scaled);
        return scaled;
    }

    private ImageIcon createFallbackIcon() {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(MENU_ICON_SIZE, MENU_ICON_SIZE, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(226, 232, 240));
        g.fillRoundRect(5, 5, MENU_ICON_SIZE - 10, MENU_ICON_SIZE - 10, 14, 14);
        g.setColor(new Color(100, 116, 139));
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(5, 5, MENU_ICON_SIZE - 10, MENU_ICON_SIZE - 10, 14, 14);
        g.dispose();
        return new ImageIcon(image);
    }

    private JPanel createHeaderPanel(JLabel titleLabel, JLabel subtitleLabel) {
        JPanel headerPanel = new RibbonHeaderPanel();
        headerPanel.setLayout(new BorderLayout(24, 0));
        headerPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        headerPanel.setBackground(backgroundColor());

        JLabel companyLogoLabel = createLogoLabel("Company");

        JPanel companyLogoPanel = createLogoPanel(companyLogoLabel, "Company Logo");

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        titlePanel.setOpaque(false);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titlePanel.add(Box.createVerticalGlue());
        titlePanel.add(titleLabel);
        titlePanel.add(Box.createVerticalStrut(8));
        titlePanel.add(subtitleLabel);
        titlePanel.add(Box.createVerticalGlue());

        JPanel centerGroup = new JPanel(new FlowLayout(FlowLayout.CENTER, 24, 0));
        centerGroup.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        centerGroup.setOpaque(false);
        centerGroup.add(companyLogoPanel);
        centerGroup.add(titlePanel);
        headerPanel.add(centerGroup, BorderLayout.CENTER);

        setDeckersLogo(companyLogoLabel);
        loadCompanyLogo(companyLogoLabel);
        return headerPanel;
    }

    private JLabel createLogoLabel(String fallbackText) {
        JLabel label = new JLabel(fallbackText, SwingConstants.CENTER);
        label.setFont(new Font("SansSerif", Font.BOLD, 14));
        label.setForeground(mutedColor());
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        return label;
    }

    private JPanel createLogoPanel(JLabel logoLabel, String accessibleName) {
        return createLogoPanel(logoLabel, accessibleName, 300, 112);
    }

    private JPanel createLogoPanel(JLabel logoLabel, String accessibleName, int width, int height) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        panel.setOpaque(false);
        panel.setBorder(new EmptyBorder(4, 4, 4, 4));
        Dimension size = new Dimension(width, height);
        panel.setPreferredSize(size);
        panel.setMinimumSize(size);
        panel.setMaximumSize(size);
        panel.getAccessibleContext().setAccessibleName(accessibleName);
        panel.add(logoLabel, BorderLayout.CENTER);
        return panel;
    }

    private void setDeckersLogo(JLabel companyLogoLabel) {
        ImageIcon deckersLogoIcon = DeckersLogoManager.loadDeckersLogoIcon(getClass());
        if (deckersLogoIcon != null && deckersLogoIcon.getIconWidth() > 0) {
            setLogoImage(companyLogoLabel, deckersLogoIcon.getImage(), 280, 100);
            return;
        }
        companyLogoLabel.setText("Deckers");
    }

    private void loadCompanyLogo(JLabel companyLogoLabel) {
        SessionDataCache.get("main-menu.company-logo",BufferedImage.class,SessionDataCache.REFERENCE_TTL)
                .ifPresent(cached->setLogoImage(companyLogoLabel,cached.value(),280,100));
        UiTaskRunner.submit(this,"main-menu.company-logo",()->{
                CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
                return CompanyCustomizationManager.loadCompanyLogo(settings);
            },logo->{
                    if (logo != null) {
                        SessionDataCache.put("main-menu.company-logo",logo);
                        setLogoImage(companyLogoLabel, logo, 280, 100);
                    }
            },ignored->{ });
    }

    private void setLogoImage(JLabel logoLabel, Image image, int maxWidth, int maxHeight) {
        Image scaled = scaleToFit(image, maxWidth, maxHeight);
        logoLabel.setText("");
        logoLabel.setIcon(new ImageIcon(scaled));
    }

    private Image scaleToFit(Image image, int maxWidth, int maxHeight) {
        return DeckersLogoManager.scaleToFit(image, maxWidth, maxHeight);
    }

    private JPanel createSectionPanel(String title, Color accentColor, JButton... buttons) {
        return createSectionPanel(title, accentColor, LEFT_SECTION_COLUMNS, false, buttons);
    }

    private JPanel createSectionPanel(String title, Color accentColor, int columns, JButton... buttons) {
        return createSectionPanel(title, accentColor, columns, true, buttons);
    }

    private JPanel createSectionPanel(String title, Color accentColor, int columns, boolean fixedColumns, JButton... buttons) {
        JPanel sectionPanel = new JPanel(new BorderLayout(0, 14)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(fixedColumns ? preferred.width : Integer.MAX_VALUE, preferred.height);
            }
        };
        boolean dark = ThemeManager.isDarkModeEnabled();
        sectionPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        sectionPanel.setBackground(blend(surfaceColor(), accentColor, dark ? 0.10 : 0.05));
        sectionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(blend(borderColor(), accentColor, dark ? 0.35 : 0.22), 1),
                new EmptyBorder(18, 18, 18, 18)
        ));
        sectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
        headerPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        headerPanel.setBackground(sectionPanel.getBackground());

        JPanel accentBar = new JPanel();
        accentBar.setBackground(accentColor);
        accentBar.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        accentBar.setPreferredSize(new Dimension(6, 30));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        titleLabel.setForeground(textColor());
        titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        headerPanel.add(accentBar, BorderLayout.WEST);
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new WrappingButtonPanel(MENU_TILE_GAP, MENU_TILE_GAP, columns, fixedColumns);
        buttonPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        buttonPanel.setBackground(sectionPanel.getBackground());

        for (JButton button : buttons) {
            applyMenuButtonTheme(button, accentColor);
            if (fixedColumns && columns == RIGHT_SECTION_COLUMNS) {
                applyVerticalMenuButtonLayout(button);
            }
            buttonPanel.add(button);
        }

        sectionPanel.add(headerPanel, BorderLayout.NORTH);
        sectionPanel.add(buttonPanel, BorderLayout.CENTER);
        int sectionWidth = fixedColumns && columns == RIGHT_SECTION_COLUMNS
                ? operationSectionWidth()
                : sectionWidth(columns);
        if (fixedColumns) {
            sectionPanel.setPreferredSize(new Dimension(sectionWidth, sectionPanel.getPreferredSize().height));
        }
        sectionPanel.setMinimumSize(new Dimension(fixedColumns ? sectionWidth : sectionWidth(1), 0));
        sectionPanel.setMaximumSize(new Dimension(fixedColumns ? sectionWidth : Integer.MAX_VALUE, Integer.MAX_VALUE));
        return sectionPanel;
    }

    private void applyMenuButtonTheme(JButton button, Color accentColor) {
        if (button instanceof MenuTileButton tileButton) {
            tileButton.setAccentColor(accentColor);
        }
        button.putClientProperty("SmartStock.customPaintedButton", Boolean.TRUE);
        button.setBackground(surfaceColor());
        button.setForeground(textColor());
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setBorderPainted(false);
        updateMenuButtonText(button);
    }

    private void applyVerticalMenuButtonLayout(JButton button) {
        if (Boolean.TRUE.equals(button.getClientProperty("SmartStock.verticalMenuButton"))) {
            return;
        }
        JLabel iconLabel = findNamedLabel(button, "menuButtonIcon");
        JPanel textPanel = findNamedPanel(button, "menuButtonTextPanel");
        JLabel descriptionLabel = findNamedLabel(button, "menuButtonDescription");
        if (iconLabel == null || textPanel == null || descriptionLabel == null) {
            return;
        }

        button.remove(iconLabel);
        button.remove(textPanel);
        button.setLayout(new BorderLayout(0, 10));
        button.setPreferredSize(new Dimension(OPERATION_MENU_TILE_WIDTH, VERTICAL_MENU_TILE_HEIGHT));
        button.setMinimumSize(new Dimension(OPERATION_MENU_TILE_WIDTH, VERTICAL_MENU_TILE_HEIGHT));
        button.setMaximumSize(new Dimension(OPERATION_MENU_TILE_WIDTH, VERTICAL_MENU_TILE_HEIGHT));

        iconLabel.setPreferredSize(new Dimension(MENU_ICON_SIZE + 10, MENU_ICON_SIZE + 10));
        iconLabel.setMinimumSize(new Dimension(MENU_ICON_SIZE + 10, MENU_ICON_SIZE + 10));
        iconLabel.setMaximumSize(new Dimension(MENU_ICON_SIZE + 10, MENU_ICON_SIZE + 10));

        textPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (Component component : textPanel.getComponents()) {
            if (component instanceof JLabel label) {
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }
        descriptionLabel.setText("<html><div style='width:198px; text-align:center;'>" + getMenuDescription(button) + "</div></html>");

        JPanel iconWrap = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        iconWrap.setName("menuButtonIconWrap");
        iconWrap.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        iconWrap.setOpaque(false);
        iconWrap.add(iconLabel);

        button.add(iconWrap, BorderLayout.NORTH);
        button.add(textPanel, BorderLayout.CENTER);
        button.putClientProperty("SmartStock.verticalMenuButton", Boolean.TRUE);
        button.revalidate();
        button.repaint();
    }

    private String getMenuDescription(JButton button) {
        Object description = button.getClientProperty("SmartStock.menuDescription");
        return description == null ? "" : description.toString();
    }

    private JLabel findNamedLabel(Container container, String name) {
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel label && name.equals(label.getName())) {
                return label;
            }
            if (component instanceof Container childContainer) {
                JLabel match = findNamedLabel(childContainer, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private JPanel findNamedPanel(Container container, String name) {
        for (Component component : container.getComponents()) {
            if (component instanceof JPanel panel && name.equals(panel.getName())) {
                return panel;
            }
            if (component instanceof Container childContainer) {
                JPanel match = findNamedPanel(childContainer, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private void updateMenuButtonText(JButton button) {
        Color titleColor = button.isEnabled() ? textColor() : mutedColor();
        Color descriptionColor = button.isEnabled() ? mutedColor() : blend(mutedColor(), backgroundColor(), 0.38);
        for (Component component : button.getComponents()) {
            updateMenuButtonText(component, titleColor, descriptionColor);
        }
    }

    private void updateMenuButtonText(Component component, Color titleColor, Color descriptionColor) {
        if (component instanceof JLabel label) {
            label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
            if ("menuButtonTitle".equals(label.getName())) {
                label.setForeground(titleColor);
            } else if ("menuButtonDescription".equals(label.getName())) {
                label.setForeground(descriptionColor);
            }
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                updateMenuButtonText(child, titleColor, descriptionColor);
            }
        }
    }

    private static Color backgroundColor() {
        return DeckersPalette.background();
    }

    private static Color surfaceColor() {
        return DeckersPalette.surface();
    }

    private static Color textColor() {
        return DeckersPalette.text();
    }

    private static Color mutedColor() {
        return DeckersPalette.muted();
    }

    private static Color borderColor() {
        return DeckersPalette.border();
    }

    private static Color blend(Color base, Color overlay, double overlayRatio) {
        return DeckersPalette.blend(base, overlay, overlayRatio);
    }

    private static Color withAlpha(Color color, int alpha) {
        return DeckersPalette.withAlpha(color, alpha);
    }

    private static int clamp(int value) {
        return DeckersPalette.clamp(value);
    }

    private static int sectionWidth(int columns) {
        return sectionWidth(columns, MENU_TILE_WIDTH);
    }

    private static int operationSectionWidth() {
        return sectionWidth(RIGHT_SECTION_COLUMNS, OPERATION_MENU_TILE_WIDTH);
    }

    private static int sectionWidth(int columns, int tileWidth) {
        int safeColumns = Math.max(1, columns);
        return safeColumns * tileWidth
                + (safeColumns - 1) * MENU_TILE_GAP
                + (SECTION_SIDE_PADDING * 2);
    }

    private static int tileColumnsForWidth(int width, int maxColumns) {
        int available = Math.max(MENU_TILE_MIN_WIDTH, width);
        int fit = (available + MENU_TILE_GAP) / (MENU_TILE_MIN_WIDTH + MENU_TILE_GAP);
        return Math.max(1, Math.min(Math.max(1, maxColumns), fit));
    }

    private static class RibbonHeaderPanel extends JPanel {
        private final BufferedImage ribbonImage;
        private BufferedImage scaledRibbonImage;
        private int scaledRibbonWidth;
        private int scaledRibbonHeight;

        private RibbonHeaderPanel() {
            ribbonImage = loadRibbonImage();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setColor(new Color(12, 12, 12));
            g.fillRect(0, 0, getWidth(), getHeight());
            if (ribbonImage == null) {
                g.dispose();
                return;
            }
            int width = getWidth();
            int height = getHeight();
            BufferedImage scaled = scaledRibbon(width, height);
            if (scaled != null) {
                g.drawImage(scaled, 0, 0, this);
            }
            g.dispose();
        }

        private BufferedImage scaledRibbon(int width, int height) {
            if (width <= 0 || height <= 0) {
                return null;
            }
            if (scaledRibbonImage != null && scaledRibbonWidth == width && scaledRibbonHeight == height) {
                return scaledRibbonImage;
            }

            double scale = Math.max(
                    width / (double) ribbonImage.getWidth(),
                    height / (double) ribbonImage.getHeight()
            );
            int drawWidth = Math.max(width, (int) Math.ceil(ribbonImage.getWidth() * scale));
            int drawHeight = Math.max(height, (int) Math.ceil(ribbonImage.getHeight() * scale));
            int drawX = (width - drawWidth) / 2;
            int drawY = (height - drawHeight) / 2;

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(ribbonImage, drawX, drawY, drawWidth, drawHeight, null);
            g.dispose();

            scaledRibbonImage = image;
            scaledRibbonWidth = width;
            scaledRibbonHeight = height;
            return scaledRibbonImage;
        }

        private BufferedImage loadRibbonImage() {
            java.net.URL resource = getClass().getResource("/Images/MainMenuRibbonFlow.png");
            if (resource != null) {
                try {
                    return ImageIO.read(resource);
                } catch (IOException ignored) {
                }
            }
            BufferedImage localImage = readRibbonImage(new File("src/Images/MainMenuRibbonFlow.png"));
            if (localImage != null) {
                return localImage;
            }
            return readRibbonImage(new File("SmartStock/src/Images/MainMenuRibbonFlow.png"));
        }

        private BufferedImage readRibbonImage(File imageFile) {
            if (!imageFile.isFile()) {
                return null;
            }
            try {
                return ImageIO.read(imageFile);
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    private static class MenuTileButton extends JButton {
        private Color accentColor;

        private MenuTileButton(Color accentColor) {
            this.accentColor = accentColor;
            setRolloverEnabled(true);
        }

        private void setAccentColor(Color accentColor) {
            this.accentColor = accentColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean dark = ThemeManager.isDarkModeEnabled();
            boolean pressed = getModel().isPressed();
            boolean rollover = getModel().isRollover();
            Color base = surfaceColor();
            Color tint = blend(base, accentColor, dark ? 0.16 : 0.08);
            Color hover = blend(base, accentColor, dark ? 0.24 : 0.14);
            Color fill = rollover ? hover : tint;
            if (pressed) {
                fill = blend(base, accentColor, dark ? 0.30 : 0.20);
            }

            int arc = 14;
            g.setColor(fill);
            g.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);

            g.setColor(withAlpha(accentColor, dark ? 76 : 48));
            g.fillRoundRect(1, 1, 9, getHeight() - 3, arc, arc);
            g.fillRect(7, 1, 5, getHeight() - 3);

            g.setColor(withAlpha(Color.WHITE, dark ? 18 : 90));
            g.fillOval(getWidth() - 96, -48, 144, 96);
            g.setColor(withAlpha(accentColor, dark ? 40 : 22));
            g.fillOval(getWidth() - 82, getHeight() - 58, 112, 86);

            g.setColor(blend(borderColor(), accentColor, dark ? 0.34 : 0.22));
            g.setStroke(new BasicStroke(1.4f));
            g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);

            if (!isEnabled()) {
                g.setColor(withAlpha(backgroundColor(), dark ? 112 : 140));
                g.fillRoundRect(1, 1, getWidth() - 3, getHeight() - 3, arc, arc);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static class MenuColumnsPanel extends JPanel {
        private final JComponent leftColumn;
        private final JComponent rightColumn;

        private MenuColumnsPanel(JComponent leftColumn, JComponent rightColumn) {
            super(null);
            this.leftColumn = leftColumn;
            this.rightColumn = rightColumn;
            add(leftColumn);
            add(rightColumn);
        }

        @Override
        public void doLayout() {
            int rightWidth = operationSectionWidth();
            int leftWidth = Math.max(sectionWidth(1), getWidth() - rightWidth - COLUMN_GAP);
            Dimension leftPreferred = leftColumn.getPreferredSize();
            Dimension rightPreferred = rightColumn.getPreferredSize();
            leftColumn.setBounds(0, 0, leftWidth, leftPreferred.height);
            rightColumn.setBounds(leftWidth + COLUMN_GAP, 0, rightWidth, rightPreferred.height);
        }

        @Override
        public Dimension getPreferredSize() {
            int rightWidth = operationSectionWidth();
            int availableWidth = getWidth();
            int leftWidth = availableWidth > 0
                    ? Math.max(sectionWidth(1), availableWidth - rightWidth - COLUMN_GAP)
                    : sectionWidth(LEFT_SECTION_COLUMNS);
            Dimension leftPreferred = preferredForWidth(leftColumn, leftWidth);
            Dimension rightPreferred = preferredForWidth(rightColumn, rightWidth);
            return new Dimension(leftWidth + COLUMN_GAP + rightWidth, Math.max(leftPreferred.height, rightPreferred.height));
        }

        private Dimension preferredForWidth(JComponent component, int width) {
            Dimension oldSize = component.getSize();
            component.setSize(width, Short.MAX_VALUE);
            Dimension preferred = component.getPreferredSize();
            component.setSize(oldSize);
            return preferred;
        }
    }

    private static class WrappingButtonPanel extends JPanel {
        private final int hGap;
        private final int vGap;
        private final int columns;
        private final boolean fixedColumns;

        private WrappingButtonPanel(int hGap, int vGap) {
            this(hGap, vGap, 0, false);
        }

        private WrappingButtonPanel(int hGap, int vGap, int columns, boolean fixedColumns) {
            super(null);
            this.hGap = hGap;
            this.vGap = vGap;
            this.columns = Math.max(0, columns);
            this.fixedColumns = fixedColumns;
        }

        @Override
        public void doLayout() {
            int width = layoutWidth();
            if (columns > 0) {
                List<Component> visibleComponents = visibleComponents();
                int layoutColumns = fixedColumns ? Math.max(1, columns) : tileColumnsForWidth(width, columns);
                int tileWidth = tileWidthFor(width, layoutColumns);
                int rowHeight = preferredRowHeight(visibleComponents);
                for (int i = 0; i < visibleComponents.size(); i++) {
                    Component component = visibleComponents.get(i);
                    int column = i % layoutColumns;
                    int row = i / layoutColumns;
                    component.setBounds(column * (tileWidth + hGap), row * (rowHeight + vGap), tileWidth, rowHeight);
                }
                return;
            }

            int x = 0;
            int y = 0;
            int rowHeight = 0;

            for (Component component : getComponents()) {
                if (!component.isVisible()) {
                    continue;
                }
                Dimension size = component.getPreferredSize();
                if (x > 0 && x + size.width > width) {
                    x = 0;
                    y += rowHeight + vGap;
                    rowHeight = 0;
                }
                component.setBounds(x, y, size.width, size.height);
                x += size.width + hGap;
                rowHeight = Math.max(rowHeight, size.height);
            }
        }

        @Override
        public Dimension getPreferredSize() {
            if (columns > 0) {
                List<Component> visibleComponents = visibleComponents();
                int visibleCount = visibleComponents.size();
                int rowHeight = preferredRowHeight(visibleComponents);
                int layoutColumns = fixedColumns ? columns : tileColumnsForWidth(layoutWidth(), columns);
                int rows = visibleCount == 0 ? 0 : (int) Math.ceil((double) visibleCount / layoutColumns);
                int width = fixedColumns
                        ? fixedColumnsWidth(layoutColumns)
                        : layoutWidth();
                int height = rows == 0 ? 0 : rows * rowHeight + (rows - 1) * vGap;
                return new Dimension(width, height);
            }
            int width = getWidth();
            if (width <= 0 && getParent() != null) {
                width = getParent().getWidth() - 34;
            }
            if (width <= 0) {
                Window window = SwingUtilities.getWindowAncestor(this);
                width = window == null ? 1000 : window.getWidth() - 90;
            }
            width = Math.max(width, 320);
            int x = 0;
            int y = 0;
            int rowHeight = 0;
            int maxWidth = 0;

            for (Component component : getComponents()) {
                if (!component.isVisible()) {
                    continue;
                }
                Dimension size = component.getPreferredSize();
                if (x > 0 && x + size.width > width) {
                    maxWidth = Math.max(maxWidth, x - hGap);
                    x = 0;
                    y += rowHeight + vGap;
                    rowHeight = 0;
                }
                x += size.width + hGap;
                rowHeight = Math.max(rowHeight, size.height);
            }

            maxWidth = Math.max(maxWidth, Math.max(0, x - hGap));
            return new Dimension(Math.max(Math.min(maxWidth, width), 320), y + rowHeight);
        }

        private int layoutWidth() {
            if (columns > 0 && fixedColumns) {
                return fixedColumnsWidth(columns);
            }
            int width = getWidth();
            if (width <= 0 && getParent() != null) {
                width = getParent().getWidth() - (SECTION_SIDE_PADDING * 2);
            }
            if (width <= 0) {
                width = columns > 0
                        ? columns * MENU_TILE_MIN_WIDTH + (columns - 1) * hGap
                        : MENU_TILE_MIN_WIDTH;
            }
            if (columns > 0) {
                int layoutColumns = tileColumnsForWidth(width, columns);
                return Math.max(width, layoutColumns * MENU_TILE_MIN_WIDTH + (layoutColumns - 1) * hGap);
            }
            return Math.max(width, MENU_TILE_MIN_WIDTH);
        }

        private List<Component> visibleComponents() {
            List<Component> visibleComponents = new java.util.ArrayList<>();
            for (Component component : getComponents()) {
                if (component.isVisible()) {
                    visibleComponents.add(component);
                }
            }
            return visibleComponents;
        }

        private int preferredRowHeight(List<Component> components) {
            int rowHeight = 0;
            for (Component component : components) {
                rowHeight = Math.max(rowHeight, component.getPreferredSize().height);
            }
            return rowHeight;
        }

        private int tileWidthFor(int width, int layoutColumns) {
            if (fixedColumns) {
                return fixedTileWidth();
            }
            int availableForTiles = width - ((layoutColumns - 1) * hGap);
            int flexibleWidth = availableForTiles / Math.max(1, layoutColumns);
            return Math.max(MENU_TILE_MIN_WIDTH, Math.min(MENU_TILE_MAX_WIDTH, flexibleWidth));
        }

        private int fixedColumnsWidth(int layoutColumns) {
            return layoutColumns * fixedTileWidth() + (layoutColumns - 1) * hGap;
        }

        private int fixedTileWidth() {
            int fixedTileWidth = 0;
            for (Component component : getComponents()) {
                if (component.isVisible()) {
                    fixedTileWidth = Math.max(fixedTileWidth, component.getPreferredSize().width);
                }
            }
            return fixedTileWidth > 0 ? fixedTileWidth : OPERATION_MENU_TILE_WIDTH;
        }
    }

    private static class ViewportWidthPanel extends JPanel implements Scrollable {
        private ViewportWidthPanel(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(visibleRect.height - 32, 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    public void applyPermissions() {
        boolean canMakeSale = PermissionManager.hasPermission("MAKE_SALE");
        boolean canProcessReturns = PermissionManager.hasPermission("PROCESS_RETURNS");
        boolean canBalanceDrawer = PermissionManager.hasPermission("BALANCE_DRAWER");
        boolean canBalanceSheet = PermissionManager.hasPermission("BALANCE_SHEET")
                || PermissionManager.hasPermission("END_OF_DAY")
                || PermissionManager.hasPermission("PAYROLL_DASHBOARD");
        boolean canOrdersManagerDashboard = PermissionManager.hasPermission("ORDERS_MANAGER_DASHBOARD")
                || PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS");
        boolean canReports = PermissionManager.hasReportsPermission();
        boolean canEnterInventory = PermissionManager.hasPermission("RECEIVING_INVENTORY");
        boolean canReceivingHistory = PermissionManager.hasPermission("VIEW_RECEIVING_HISTORY");
        boolean canStoreTransfer = PermissionManager.hasPermission("STORE_TRANSFER");
        boolean canCustomOrderItems = PermissionManager.hasPermission("MANUAL_ADJUSTMENT");
        boolean canDepartmentManagement = PermissionManager.hasPermission("DEPARTMENT_MANAGEMENT");
        boolean canVendorManagement = PermissionManager.hasPermission("VENDOR_MANAGEMENT");
        boolean canViewSales = PermissionManager.hasPermission("VIEW_SALES");
        boolean canCustomerAccounts = PermissionManager.hasPermission("CUSTOMER_ACCOUNTS");
        boolean canQuotationsInvoices = PermissionManager.hasPermission("QUOTATIONS_ORDERS")
                || PermissionManager.hasPermission("CREATE_QUOTATION")
                || PermissionManager.hasPermission("MANAGE_INVOICES")
                || PermissionManager.hasPermission("POST_INVOICE_DELIVERY");
        boolean canCustomOrders = PermissionManager.hasPermission("CREATE_CUSTOM_ORDER");
        boolean canOrders = PermissionManager.hasPermission("CREATE_CUSTOM_ORDER")
                || PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS")
                || PermissionManager.hasPermission("VIEW_ASSIGNED_CUSTOM_ORDERS");
        boolean canViewInventory = PermissionManager.hasPermission("VIEW_INVENTORY");
        boolean canAddItem = PermissionManager.hasPermission("NEW_ITEM");
        boolean canEditItem = PermissionManager.hasPermission("EDIT_ITEM");
        boolean canTimeClock = PermissionManager.hasPermission("TIME_CLOCK");
        boolean canPayrollDashboard = PermissionManager.hasPermission("PAYROLL_DASHBOARD");
        boolean canViewEmployeeSchedule = PermissionManager.hasPermission("VIEW_EMPLOYEE_SCHEDULE");
        boolean canEmployeeManagement = PermissionManager.hasPermission("EMPLOYEE_MANAGEMENT");
        boolean canRolesPermissions = PermissionManager.hasPermission("ROLE_MANAGEMENT");
        boolean canDeviceManagement = PermissionManager.hasPermission("DEVICE_MANAGEMENT");
        boolean canMachineManagement = PermissionManager.hasPermission("MACHINE_MANAGEMENT");
        boolean canPartsManagement = PermissionManager.hasPermission("PARTS_MANAGEMENT");
        boolean canMaintenanceManagement = PermissionManager.hasPermission("MAINTENANCE_MANAGEMENT")
                || PermissionManager.hasPermission("MAINTENANCE_TECHNICIAN");
        boolean canCompanyCustomization = hasCompanyPreferencesPermission();
        boolean canWorkstationPreferences = hasWorkstationPreferencesPermission();

        makeSaleButton.setEnabled(canMakeSale);
        returnSaleButton.setEnabled(canProcessReturns);
        balanceDrawButton.setEnabled(canBalanceDrawer);
        changeBasketButton.setEnabled(canBalanceDrawer);
        balanceSheetButton.setEnabled(canBalanceSheet);
        ordersManagerDashboardButton.setEnabled(canOrdersManagerDashboard);
        reportsButton.setEnabled(canReports);
        enterInventoryButton.setEnabled(canEnterInventory);
        receivingHistoryButton.setEnabled(canReceivingHistory);
        storeTransferButton.setEnabled(canStoreTransfer);
        customOrderItemsButton.setEnabled(canCustomOrderItems);
        departmentListButton.setEnabled(canDepartmentManagement);
        vendorListButton.setEnabled(canVendorManagement);
        viewSalesButton.setEnabled(canViewSales);
        customerAccountsButton.setEnabled(canCustomerAccounts);
        customerTransactionHistoryButton.setEnabled(canCustomerAccounts);
        invoicesButton.setEnabled(canQuotationsInvoices);
        customOrdersButton.setEnabled(canCustomOrders);
        ordersButton.setEnabled(canOrders);
        viewInventoryButton.setEnabled(canViewInventory);
        priceTagPrintingButton.setEnabled(canViewInventory);
        addItemButton.setEnabled(canAddItem);
        editItemsButton.setEnabled(canEditItem);
        timeClockButton.setEnabled(canTimeClock);
        payrollDashboardButton.setEnabled(canPayrollDashboard);
        weeklyScheduleButton.setEnabled(canViewEmployeeSchedule);
        employeeManagementButton.setEnabled(canEmployeeManagement);
        rolesPermissionsButton.setEnabled(canRolesPermissions);
        deviceManagementButton.setEnabled(canDeviceManagement);
        machineManagementButton.setEnabled(canMachineManagement);
        partsManagementButton.setEnabled(canPartsManagement);
        maintenanceManagementButton.setEnabled(canMaintenanceManagement);
        companyCustomizationButton.setEnabled(canCompanyCustomization);
        workstationPreferencesButton.setEnabled(canWorkstationPreferences);

        refreshMenuButtonThemes();
    }

    private void refreshMenuButtonThemes() {
        JButton[] buttons = {
                makeSaleButton,
                returnSaleButton,
                balanceDrawButton,
                changeBasketButton,
                balanceSheetButton,
                ordersManagerDashboardButton,
                reportsButton,
                enterInventoryButton,
                receivingHistoryButton,
                storeTransferButton,
                customOrderItemsButton,
                departmentListButton,
                vendorListButton,
                viewSalesButton,
                customerAccountsButton,
                customerTransactionHistoryButton,
                invoicesButton,
                customOrdersButton,
                ordersButton,
                viewInventoryButton,
                priceTagPrintingButton,
                addItemButton,
                editItemsButton,
                timeClockButton,
                payrollDashboardButton,
                weeklyScheduleButton,
                employeeManagementButton,
                rolesPermissionsButton,
                deviceManagementButton,
                machineManagementButton,
                partsManagementButton,
                maintenanceManagementButton,
                companyCustomizationButton,
                workstationPreferencesButton
        };
        for (JButton button : buttons) {
            updateMenuButtonText(button);
            button.repaint();
        }
    }



    private void wireActions() {
        makeSaleButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("MAKE_SALE", this, "Make a Sale")) {
                return;
            }
            NavigationManager.openMakeSale(this);
        });
        returnSaleButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("PROCESS_RETURNS", this, "Returns")) {
                return;
            }
            NavigationManager.openReturnSale(this);
        });
        balanceDrawButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("BALANCE_DRAWER", this, "Balance Draw")) {
                return;
            }
            NavigationManager.openBalanceDraw(this);
        });
        changeBasketButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("BALANCE_DRAWER", this, "Change Basket")) {
                return;
            }
            NavigationManager.openChangeBasket(this);
        });
        balanceSheetButton.addActionListener(e -> {
            boolean canBalanceSheet = PermissionManager.hasPermission("BALANCE_SHEET")
                    || PermissionManager.hasPermission("END_OF_DAY")
                    || PermissionManager.hasPermission("PAYROLL_DASHBOARD");
            if (!canBalanceSheet) {
                JOptionPane.showMessageDialog(this, "You do not have permission to access Balance Sheet.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                return;
            }
            NavigationManager.openBalanceSheet(this);
        });
        ordersManagerDashboardButton.addActionListener(e -> {
            if (!PermissionManager.hasPermission("ORDERS_MANAGER_DASHBOARD") && !PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS")) {
                JOptionPane.showMessageDialog(this, "You do not have permission to access Orders Manager Dashboard.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                return;
            }
            NavigationManager.openOrdersManagerDashboard(this);
        });
        reportsButton.addActionListener(e -> {
            if (!PermissionManager.hasReportsPermission()) {
                JOptionPane.showMessageDialog(this, "You do not have permission to access Reports.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                return;
            }
            NavigationManager.openReports(this);
        });
        enterInventoryButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("RECEIVING_INVENTORY", this, "Receiving Inventory")) {
                return;
            }
            NavigationManager.openEnterInventory(this);
        });
        receivingHistoryButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("VIEW_RECEIVING_HISTORY", this, "Receiving History")) {
                return;
            }
            NavigationManager.openReceivingHistory(this);
        });
        storeTransferButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("STORE_TRANSFER", this, "Store Transfer")) {
                return;
            }
            NavigationManager.openStoreTransfer(this);
        });
        customOrderItemsButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("MANUAL_ADJUSTMENT", this, "Custom Order Items")) {
                return;
            }
            NavigationManager.openCustomOrderItems(this);
        });
        departmentListButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("DEPARTMENT_MANAGEMENT", this, "Department Management")) {
                return;
            }
            NavigationManager.openDepartmentList(this);
        });
        vendorListButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("VENDOR_MANAGEMENT", this, "Vendor Management")) {
                return;
            }
            NavigationManager.openVendorList(this);
        });
        viewSalesButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("VIEW_SALES", this, "View Sales")) {
                return;
            }
            NavigationManager.openViewSales(this);
        });
        customerAccountsButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("CUSTOMER_ACCOUNTS", this, "Customer Accounts")) {
                return;
            }
            NavigationManager.openCustomerAccounts(this);
        });
        customerTransactionHistoryButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("CUSTOMER_ACCOUNTS", this, "Customer Transaction History")) {
                return;
            }
            openCustomerTransactionHistory();
        });
        invoicesButton.addActionListener(e -> {
            boolean canQuotationsInvoices = PermissionManager.hasPermission("QUOTATIONS_ORDERS")
                    || PermissionManager.hasPermission("CREATE_QUOTATION")
                    || PermissionManager.hasPermission("MANAGE_INVOICES")
                    || PermissionManager.hasPermission("POST_INVOICE_DELIVERY");
            if (!canQuotationsInvoices) {
                JOptionPane.showMessageDialog(this, "You do not have permission to access Quotations & Invoices.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                return;
            }
            NavigationManager.openInvoices(this);
        });
        customOrdersButton.addActionListener(e -> {
            if (!PermissionManager.hasPermission("CREATE_CUSTOM_ORDER")) {
                JOptionPane.showMessageDialog(this, "You do not have permission to access Customer Orders.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                return;
            }
            NavigationManager.openCustomOrders(this);
        });
        ordersButton.addActionListener(e -> {
            boolean canOrders = PermissionManager.hasPermission("CREATE_CUSTOM_ORDER")
                    || PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS")
                    || PermissionManager.hasPermission("VIEW_ASSIGNED_CUSTOM_ORDERS");
            if (!canOrders) {
                JOptionPane.showMessageDialog(this, "You do not have permission to access Orders.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                return;
            }
            NavigationManager.openOrders(this);
        });
        viewInventoryButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("VIEW_INVENTORY", this, "View Inventory")) {
                return;
            }
            NavigationManager.openViewInventory(this);
        });
        priceTagPrintingButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("VIEW_INVENTORY", this, "Price Tag Printing")) return;
            NavigationManager.openPriceTagPrinting(this);
        });
        addItemButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("NEW_ITEM", this, "Add Item")) {
                return;
            }
            NavigationManager.openNewItem(this);
        });
        editItemsButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("EDIT_ITEM", this, "Edit Items")) {
                return;
            }
            NavigationManager.openEditItem(this);
        });
        timeClockButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("TIME_CLOCK", this, "Time Clock")) {
                return;
            }
            NavigationManager.openTimeClock(this);
        });
        payrollDashboardButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("PAYROLL_DASHBOARD", this, "Payroll Dashboard")) {
                return;
            }
            NavigationManager.openPayrollDashboard(this);
        });
        weeklyScheduleButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("VIEW_EMPLOYEE_SCHEDULE", this, "Weekly Schedule")) {
                return;
            }
            NavigationManager.openWeeklySchedule(this);
        });
        employeeManagementButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("EMPLOYEE_MANAGEMENT", this, "Employee Management")) {
                return;
            }
            NavigationManager.openEmployeeManagement(this);
        });
        rolesPermissionsButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("ROLE_MANAGEMENT", this, "Roles & Permissions")) {
                return;
            }
            NavigationManager.openRolesPermission(this);
        });
        deviceManagementButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("DEVICE_MANAGEMENT", this, "Device Management")) {
                return;
            }
            NavigationManager.openDeviceManagement(this);
        });
        machineManagementButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("MACHINE_MANAGEMENT", this, "Machine List")) {
                return;
            }
            NavigationManager.openMachineManagement(this);
        });
        partsManagementButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("PARTS_MANAGEMENT", this, "Parts List")) {
                return;
            }
            NavigationManager.openPartsManagement(this);
        });
        maintenanceManagementButton.addActionListener(e -> {
            if (!PermissionManager.hasPermission("MAINTENANCE_MANAGEMENT") && !PermissionManager.hasPermission("MAINTENANCE_TECHNICIAN")) {
                JOptionPane.showMessageDialog(this, "You do not have permission to access Maintenance Management.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                return;
            }
            NavigationManager.openMaintenanceManagement(this);
        });
        companyCustomizationButton.addActionListener(e -> {
            if (!requireCompanyPreferencesPermission()) {
                return;
            }
            NavigationManager.openCompanyCustomization(this);
        });
        workstationPreferencesButton.addActionListener(e -> {
            if (!requireWorkstationPreferencesPermission()) {
                return;
            }
            NavigationManager.openWorkstationPreferences(this);
        });

        logoutButton.addActionListener(e -> {
            endSessionSafely();
            SessionManager.clearSessionState();
            SupabaseSessionManager.clearSession();
            SupabaseSessionManager.clearPersistedSession();
            NavigationManager.logoutToLogin(this);
        });
    }

    private void wireWindowSessionHandling() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopNotificationRefreshTimer();
                endSessionSafely();
            }
        });
    }

    private void startNotificationRefreshTimer() {
        if (notificationRefreshTimer != null && notificationRefreshTimer.isRunning()) {
            return;
        }
        notificationRefreshTimer = new Timer(60_000, e -> refreshNotifications(true));
        notificationRefreshTimer.start();
    }

    private void stopNotificationRefreshTimer() {
        if (notificationRefreshTimer != null) {
            notificationRefreshTimer.stop();
            notificationRefreshTimer = null;
        }
    }

    private void refreshNotifications(boolean allowPopup) {
        UiTaskRunner.submit(this,"main-menu.notifications",NotificationService::loadNotifications,
                notifications->{
                    int unread = 0;
                    int urgent = 0;
                    for (AppNotification notification : notifications) {
                        if (notification.isUnreadVisible()) {
                            unread++;
                        }
                        if (notification.isUrgentVisible()) {
                            urgent++;
                        }
                    }
                    AppMenuBar.updateNotificationMenuLabel(getJMenuBar(), unread, urgent);
                    if (allowPopup) {
                        showUrgentNotificationPopup(notifications);
                    }
                },ex->AppMenuBar.updateNotificationMenuLabel(getJMenuBar(),0,0));
    }

    public void refreshNotificationMenu() {
        refreshNotifications(false);
    }

    private void showUrgentNotificationPopup(List<AppNotification> notifications) {
        for (AppNotification notification : notifications) {
            if (!notification.isUrgentVisible() || urgentPopupKeysShown.contains(notification.notificationKey())) {
                continue;
            }
            urgentPopupKeysShown.add(notification.notificationKey());
            int choice = showUrgentNotificationDialog(notification);
            try {
                if (choice == 0) {
                    NotificationService.markSeen(notification);
                    NotificationsDialog.navigate(this, notification.actionTarget());
                } else if (choice == 1) {
                    NotificationService.snooze(notification.notificationKey(), 60);
                    refreshNotifications(false);
                } else if (choice == 2) {
                    NotificationService.markRead(notification.notificationKey());
                    refreshNotifications(false);
                }
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Notifications", JOptionPane.ERROR_MESSAGE);
            }
            return;
        }
    }

    private int showUrgentNotificationDialog(AppNotification notification) {
        final int[] choice = {-1};
        JDialog dialog = new JDialog(this, "Urgent Notification", true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(18, 16));
        root.setBorder(new EmptyBorder(22, 24, 20, 24));
        root.setBackground(surfaceColor());

        JLabel iconLabel = new JLabel(UIManager.getIcon("OptionPane.warningIcon"));
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        root.add(iconLabel, BorderLayout.WEST);

        JPanel content = new JPanel(new BorderLayout(0, 18));
        content.setOpaque(false);

        JLabel titleLabel = new JLabel(notification.title());
        titleLabel.setForeground(textColor());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 18f));
        content.add(titleLabel, BorderLayout.NORTH);

        JLabel messageLabel = new JLabel("<html><div style='width:460px;'>"
                + escapeHtml(notification.message()) + "</div></html>");
        messageLabel.setForeground(textColor());
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.PLAIN, 16f));
        content.add(messageLabel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(1, 3, 10, 0));
        buttons.setOpaque(false);
        JButton markReadButton = createNotificationActionButton("Mark Read", new Color(75, 85, 99), Color.WHITE);
        JButton snoozeButton = createNotificationActionButton("Snooze", new Color(75, 85, 99), Color.WHITE);
        JButton openButton = createNotificationActionButton("Open", new Color(37, 99, 235), Color.WHITE);

        markReadButton.addActionListener(e -> {
            choice[0] = 2;
            dialog.dispose();
        });
        snoozeButton.addActionListener(e -> {
            choice[0] = 1;
            dialog.dispose();
        });
        openButton.addActionListener(e -> {
            choice[0] = 0;
            dialog.dispose();
        });

        buttons.add(markReadButton);
        buttons.add(snoozeButton);
        buttons.add(openButton);
        content.add(buttons, BorderLayout.SOUTH);
        root.add(content, BorderLayout.CENTER);

        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(openButton);
        dialog.pack();
        dialog.setMinimumSize(new Dimension(640, dialog.getHeight()));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return choice[0];
    }

    private static JButton createNotificationActionButton(String text, Color background, Color foreground) {
        JButton button = new JButton(text);
        button.setUI(new BasicButtonUI());
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        button.setForeground(foreground);
        button.setBackground(background);
        button.setOpaque(true);
        button.setContentAreaFilled(true);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setPreferredSize(new Dimension(142, 40));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void promptToStartDrawIfNeeded() {
        if (drawStartPromptShownThisAppSession || !PermissionManager.hasPermission("BALANCE_DRAWER")) {
            return;
        }
        UiTaskRunner.submit(this,"main-menu.drawer-prompt",LanApiClient::currentCashDrawer,
                this::showStartDrawPromptIfNeeded,
                ex->System.err.println("Failed to check cash draw startup status: "+ex.getMessage()));
    }

    private void showStartDrawPromptIfNeeded(LanApiClient.CashDrawerStatus drawer) {
        if (!drawer.assigned() || drawer.activeSession()) {
            return;
        }

        drawStartPromptShownThisAppSession = true;
        Object[] options = {"Start Draw", "Skip"};
        int choice = JOptionPane.showOptionDialog(
                this,
                "No draw is open for " + drawer.drawerName() + ".\n\nStart it now before taking cash?",
                "Start Draw",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == JOptionPane.YES_OPTION) {
            NavigationManager.openBalanceDraw(this);
        }
    }

    private void endSessionSafely() {
        LanApiClient.logoutWithoutWaiting();
    }

    private void openCustomerTransactionHistory() {
        DefaultListModel<CustomerHistoryOption> model = new DefaultListModel<>();
        JList<CustomerHistoryOption> customerList = new JList<>(model);
        customerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        customerList.setVisibleRowCount(12);
        JTextField searchField = new JTextField();
        JButton openButton = new JButton("Open History");
        JButton cancelButton = new JButton("Cancel");

        Runnable loadCustomers = () -> loadCustomerHistoryOptions(model, searchField.getText().trim());
        UiDebouncer.bind(searchField, 300, loadCustomers);

        JPanel panel = new JPanel(new BorderLayout(8, 8));
        JPanel searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.add(new JLabel("Customer:"), BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);
        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(customerList), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancelButton);
        buttons.add(openButton);
        panel.add(buttons, BorderLayout.SOUTH);

        JDialog dialog = new JDialog(this, "Customer Transaction History", true);
        dialog.setSize(560, 420);
        dialog.setLocationRelativeTo(this);
        dialog.add(panel);

        Runnable openSelected = () -> {
            CustomerHistoryOption selected = customerList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(dialog, "Select a customer first.");
                return;
            }
            dialog.dispose();
            CustomerTransactionHistory history = new CustomerTransactionHistory(selected.customerId(), selected.label());
            WindowHelper.showPosWindow(history, this);
        };
        openButton.addActionListener(e -> openSelected.run());
        cancelButton.addActionListener(e -> dialog.dispose());
        customerList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected.run();
                }
            }
        });
        searchField.addActionListener(e -> openSelected.run());

        loadCustomers.run();
        dialog.setVisible(true);
    }

    private void loadCustomerHistoryOptions(DefaultListModel<CustomerHistoryOption> model, String search) {
        String token = search == null ? "" : search.trim().toLowerCase();
        UiTaskRunner.submit(this, "main-menu.customer-history",
                LanApiClient::loadCustomerAccounts, accounts -> {
            model.clear();
            int count = 0;
            for (LanApiClient.CustomerAccount account : accounts) {
                if (!token.isEmpty() && !(safe(account.accountNumber()).toLowerCase().contains(token)
                        || safe(account.customerName()).toLowerCase().contains(token)
                        || safe(account.phone()).toLowerCase().contains(token))) continue;
                model.addElement(new CustomerHistoryOption(account.customerId(), account.accountNumber(),
                        account.customerName(), account.phone()));
                if (++count >= 100) break;
            }
        }, failure -> model.clear());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private JButton createMenuButton(String title, String description, Icon icon) {
        JButton button = new MenuTileButton(DeckersPalette.ORANGE);
        button.setLayout(new BorderLayout(14, 10));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setBorder(new EmptyBorder(18, 18, 18, 18));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.putClientProperty("SmartStock.customPaintedButton", Boolean.TRUE);
        button.putClientProperty("SmartStock.menuDescription", description);
        button.setPreferredSize(new Dimension(315, 126));
        button.setMinimumSize(new Dimension(315, 126));
        button.setMaximumSize(new Dimension(345, 134));

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setName("menuButtonIcon");
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(82, 82));
        iconLabel.setMinimumSize(new Dimension(82, 82));
        iconLabel.setMaximumSize(new Dimension(82, 82));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setName("menuButtonTitle");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        JLabel descriptionLabel = new JLabel("<html><div style='width:172px;'>" + description + "</div></html>");
        descriptionLabel.setName("menuButtonDescription");
        descriptionLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        descriptionLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        JPanel textPanel = new JPanel();
        textPanel.setName("menuButtonTextPanel");
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        textPanel.setOpaque(false);
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(8));
        textPanel.add(descriptionLabel);

        button.add(iconLabel, BorderLayout.WEST);
        button.add(textPanel, BorderLayout.CENTER);
        updateMenuButtonText(button);

        return button;
    }

    private boolean hasCompanyPreferencesPermission() {
        return PermissionManager.hasPermission("COMPANY_PREFERENCES")
                || PermissionManager.hasPermission("COMPANY_CUSTOMIZATION")
                || PermissionManager.hasPermission("LOCATION_MANAGEMENT")
                || PermissionManager.hasPermission("CASH_DRAWER_MANAGEMENT");
    }

    private boolean requireCompanyPreferencesPermission() {
        if (hasCompanyPreferencesPermission()) {
            return true;
        }
        JOptionPane.showMessageDialog(
                this,
                "You do not have permission to access Company Preferences.",
                "Access Denied",
                JOptionPane.WARNING_MESSAGE
        );
        return false;
    }

    private boolean hasWorkstationPreferencesPermission() {
        return PermissionManager.hasPermission("COMPANY_PREFERENCES")
                || PermissionManager.hasPermission("COMPANY_CUSTOMIZATION")
                || PermissionManager.hasPermission("LOCAL_DEVICE_SETTINGS")
                || PermissionManager.hasPermission("HARDWARE_SETUP");
    }

    private boolean requireWorkstationPreferencesPermission() {
        if (hasWorkstationPreferencesPermission()) {
            return true;
        }
        JOptionPane.showMessageDialog(
                this,
                "You do not have permission to access Workstation Preferences.",
                "Access Denied",
                JOptionPane.WARNING_MESSAGE
        );
        return false;
    }

    public JButton getMakeSaleButton() {
        return makeSaleButton;
    }

    public JButton getBalanceDrawButton() {
        return balanceDrawButton;
    }

    public JButton getEnterInventoryButton() {
        return enterInventoryButton;
    }

    public JButton getReceivingHistoryButton() {
        return receivingHistoryButton;
    }

    public JButton getViewSalesButton() {
        return viewSalesButton;
    }

    public JButton getCustomerAccountsButton() {
        return customerAccountsButton;
    }

    private record CustomerHistoryOption(int customerId, String accountNumber, String name, String phone) {
        private String label() {
            String account = accountNumber == null || accountNumber.isBlank() ? "" : accountNumber + " - ";
            String phoneText = phone == null || phone.isBlank() ? "" : " (" + phone + ")";
            return account + name + phoneText;
        }

        @Override
        public String toString() {
            return label();
        }
    }

    public JButton getViewInventoryButton() {
        return viewInventoryButton;
    }

    public JButton getAddItemButton() {
        return addItemButton;
    }

    public JButton getEditItemsButton() {
        return editItemsButton;
    }

    public JButton getTimeClockButton() {
        return timeClockButton;
    }

    public JButton getPayrollDashboardButton() {
        return payrollDashboardButton;
    }

    public JButton getEmployeeManagementButton() {
        return employeeManagementButton;
    }

    public JButton getRolesPermissionsButton() {
        return rolesPermissionsButton;
    }

    public JButton getDeviceManagementButton() {
        return deviceManagementButton;
    }

    public JButton getMachineManagementButton() {
        return machineManagementButton;
    }

    public JButton getPartsManagementButton() {
        return partsManagementButton;
    }

    public JButton getCompanyCustomizationButton() {
        return companyCustomizationButton;
    }

    public JButton getLogoutButton() {
        return logoutButton;
    }
}
