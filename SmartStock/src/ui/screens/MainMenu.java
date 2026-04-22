package ui.screens;
import managers.NavigationManager;
import managers.PermissionManager;
import managers.SupabaseSessionManager;
import managers.SessionManager;
import services.DeviceService;
import ui.components.AppMenuBar;
import ui.helpers.WindowHelper;
import ui.helpers.ThemeManager;
import data.DB;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.sql.Connection;


public class MainMenu extends JFrame {

    private final JButton makeSaleButton;
    private final JButton returnSaleButton;
    private final JButton endOfDayButton;
    private final JButton enterInventoryButton;
    private final JButton receivingHistoryButton;
    private final JButton storeTransferButton;
    private final JButton departmentListButton;
    private final JButton vendorListButton;
    private final JButton viewSalesButton;
    private final JButton customerAccountsButton;
    private final JButton viewInventoryButton;
    private final JButton addItemButton;
    private final JButton editItemsButton;
    private final JButton timeClockButton;
    private final JButton payrollDashboardButton;
    private final JButton employeeManagementButton;
    private final JButton rolesPermissionsButton;
    private final JButton locationManagementButton;
    private final JButton companyCustomizationButton;
    private final JButton localDeviceSettingsButton;
    private final JButton hardwareSetupButton;
    private final JButton logoutButton;

    public MainMenu() {
        setTitle("SmartStock - Main Menu");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1100, 720);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        setJMenuBar(AppMenuBar.create(this, "MainMenu"));

        JPanel mainPanel = new JPanel(new BorderLayout(20, 20));
        mainPanel.setBorder(new EmptyBorder(25, 25, 25, 25));
        mainPanel.setBackground(new Color(245, 247, 250));

        JLabel titleLabel = new JLabel("SmartStock Main Menu");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel subtitleLabel = new JLabel("Choose a section to continue");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 16));
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        subtitleLabel.setForeground(new Color(90, 90, 90));

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(8));
        headerPanel.add(subtitleLabel);

        makeSaleButton = createMenuButton("Make a Sale", "Create a new sale transaction", loadIcon("src/ICONS/MakeASale.png"));
        returnSaleButton = createMenuButton("Returns", "Return items from a completed sale", loadIcon("src/ICONS/ViewSales.png"));
        endOfDayButton = createMenuButton("End of Day", "Review store daily totals", loadIcon("src/ICONS/ViewSales.png"));
        enterInventoryButton = createMenuButton("Receiving Inventory", "Add received stock to inventory", loadIcon("src/ICONS/ViewInventory.png"));
        receivingHistoryButton = createMenuButton("Receiving History", "Review received inventory", loadIcon("src/ICONS/ViewSales.png"));
        storeTransferButton = createMenuButton("Store Transfer", "Move stock between stores", loadIcon("src/ICONS/ViewInventory.png"));
        departmentListButton = createMenuButton("Departments", "Manage item departments", loadIcon("src/ICONS/ViewInventory.png"));
        vendorListButton = createMenuButton("Vendors", "Manage product vendors", loadIcon("src/ICONS/Employee.png"));
        viewSalesButton = createMenuButton("View Sales", "Review previous transactions", loadIcon("src/ICONS/ViewSales.png"));
        customerAccountsButton = createMenuButton("Customers", "Manage customer credit accounts", loadIcon("src/ICONS/Employee.png"));
        viewInventoryButton = createMenuButton("View Inventory", "View current inventory levels", loadIcon("src/ICONS/ViewInventory.png"));
        addItemButton = createMenuButton("Add Item", "Add a new product to inventory", loadIcon("src/ICONS/NewItem.png"));
        editItemsButton = createMenuButton("Edit Items", "Update product information", loadIcon("src/ICONS/EditItem.png"));
        timeClockButton = createMenuButton("Time Clock", "Clock employees in and out", loadIcon("src/ICONS/Employee.png"));
        payrollDashboardButton = createMenuButton("Payroll", "Review pay periods and time records", loadIcon("src/ICONS/ViewSales.png"));
        employeeManagementButton = createMenuButton("Employees", "Manage employee accounts", loadIcon("src/ICONS/Employee.png"));
        rolesPermissionsButton = createMenuButton("Roles & Permissions", "Configure user access", loadIcon("src/ICONS/Security.png"));
        locationManagementButton = createMenuButton("Locations", "Manage store locations", loadIcon("src/ICONS/Security.png"));
        companyCustomizationButton = createMenuButton("Company Preferences", "Company identity and receipts", loadIcon("src/ICONS/Security.png"));
        localDeviceSettingsButton = createMenuButton("Local Device", "Edit register receipt settings", loadIcon("src/ICONS/Security.png"));
        hardwareSetupButton = createMenuButton("Hardware Setup", "Configure POS printers", loadIcon("src/ICONS/Security.png"));
        applyPermissions();

        JPanel sectionStackPanel = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        sectionStackPanel.setLayout(new BoxLayout(sectionStackPanel, BoxLayout.Y_AXIS));
        sectionStackPanel.setOpaque(false);
        sectionStackPanel.add(createSectionPanel(
                "Point of Sale",
                new Color(37, 99, 235),
                makeSaleButton,
                returnSaleButton,
                endOfDayButton,
                viewSalesButton,
                customerAccountsButton
        ));
        sectionStackPanel.add(Box.createVerticalStrut(18));
        sectionStackPanel.add(createSectionPanel(
                "Inventory",
                new Color(5, 150, 105),
                enterInventoryButton,
                receivingHistoryButton,
                storeTransferButton,
                departmentListButton,
                vendorListButton,
                viewInventoryButton,
                addItemButton,
                editItemsButton
        ));
        sectionStackPanel.add(Box.createVerticalStrut(18));
        sectionStackPanel.add(createSectionPanel(
                "Employee",
                new Color(217, 119, 6),
                timeClockButton,
                payrollDashboardButton,
                employeeManagementButton
        ));
        sectionStackPanel.add(Box.createVerticalStrut(18));
        sectionStackPanel.add(createSectionPanel(
                "Admin",
                new Color(124, 58, 237),
                rolesPermissionsButton,
                locationManagementButton,
                companyCustomizationButton,
                localDeviceSettingsButton,
                hardwareSetupButton
        ));

        JPanel scrollContentPanel = new ViewportWidthPanel(new BorderLayout());
        scrollContentPanel.setOpaque(false);
        scrollContentPanel.add(sectionStackPanel, BorderLayout.NORTH);

        JScrollPane sectionScrollPane = new JScrollPane(scrollContentPanel);
        sectionScrollPane.setBorder(BorderFactory.createEmptyBorder());
        sectionScrollPane.setOpaque(false);
        sectionScrollPane.getViewport().setOpaque(false);
        sectionScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        sectionScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sectionScrollPane.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                sectionStackPanel.revalidate();
            }
        });

        logoutButton = new JButton("Logout");
        logoutButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        logoutButton.setFocusPainted(false);
        logoutButton.setPreferredSize(new Dimension(130, 42));

        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footerPanel.setOpaque(false);
        footerPanel.add(logoutButton);

        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(sectionScrollPane, BorderLayout.CENTER);
        mainPanel.add(footerPanel, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
        wireActions();
        wireWindowSessionHandling();
        WindowHelper.configurePosWindow(this);
    }
    private ImageIcon loadIcon(String path) {
        ImageIcon icon = null;
        String fileName = new File(path).getName();
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
            return createFallbackIcon();
        }

        Image img = icon.getImage().getScaledInstance(48, 48, Image.SCALE_SMOOTH);
        return new ImageIcon(img);
    }

    private ImageIcon createFallbackIcon() {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(48, 48, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(226, 232, 240));
        g.fillRoundRect(4, 4, 40, 40, 8, 8);
        g.setColor(new Color(100, 116, 139));
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(4, 4, 40, 40, 8, 8);
        g.dispose();
        return new ImageIcon(image);
    }

    private JPanel createSectionPanel(String title, Color accentColor, JButton... buttons) {
        JPanel sectionPanel = new JPanel(new BorderLayout(0, 14)) {
            @Override
            public Dimension getMaximumSize() {
                Dimension preferred = getPreferredSize();
                return new Dimension(Integer.MAX_VALUE, preferred.height);
            }
        };
        sectionPanel.setBackground(Color.WHITE);
        sectionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(16, 16, 16, 16)
        ));
        sectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
        headerPanel.setOpaque(false);

        JPanel accentBar = new JPanel();
        accentBar.setBackground(accentColor);
        accentBar.setPreferredSize(new Dimension(5, 28));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        titleLabel.setForeground(new Color(32, 41, 57));

        headerPanel.add(accentBar, BorderLayout.WEST);
        headerPanel.add(titleLabel, BorderLayout.CENTER);

        JPanel buttonPanel = new WrappingButtonPanel(12, 12);
        buttonPanel.setOpaque(false);

        for (JButton button : buttons) {
            buttonPanel.add(button);
        }

        sectionPanel.add(headerPanel, BorderLayout.NORTH);
        sectionPanel.add(buttonPanel, BorderLayout.CENTER);
        return sectionPanel;
    }

    private static class WrappingButtonPanel extends JPanel {
        private final int hGap;
        private final int vGap;

        private WrappingButtonPanel(int hGap, int vGap) {
            super(null);
            this.hGap = hGap;
            this.vGap = vGap;
        }

        @Override
        public void doLayout() {
            int width = Math.max(getWidth(), getPreferredSize().width);
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
        boolean canEndOfDay = PermissionManager.hasPermission("END_OF_DAY");
        boolean canEnterInventory = PermissionManager.hasPermission("RECEIVING_INVENTORY");
        boolean canReceivingHistory = PermissionManager.hasPermission("VIEW_RECEIVING_HISTORY");
        boolean canStoreTransfer = PermissionManager.hasPermission("STORE_TRANSFER");
        boolean canDepartmentManagement = PermissionManager.hasPermission("DEPARTMENT_MANAGEMENT");
        boolean canVendorManagement = PermissionManager.hasPermission("VENDOR_MANAGEMENT");
        boolean canViewSales = PermissionManager.hasPermission("VIEW_SALES");
        boolean canCustomerAccounts = PermissionManager.hasPermission("CUSTOMER_ACCOUNTS");
        boolean canViewInventory = PermissionManager.hasPermission("VIEW_INVENTORY");
        boolean canAddItem = PermissionManager.hasPermission("NEW_ITEM");
        boolean canEditItem = PermissionManager.hasPermission("EDIT_ITEM");
        boolean canTimeClock = PermissionManager.hasPermission("TIME_CLOCK");
        boolean canPayrollDashboard = PermissionManager.hasPermission("PAYROLL_DASHBOARD");
        boolean canEmployeeManagement = PermissionManager.hasPermission("EMPLOYEE_MANAGEMENT");
        boolean canRolesPermissions = PermissionManager.hasPermission("ROLE_MANAGEMENT");
        boolean canLocationManagement = PermissionManager.hasPermission("LOCATION_MANAGEMENT");
        boolean canCompanyCustomization = hasCompanyPreferencesPermission();
        boolean canLocalDeviceSettings = PermissionManager.hasPermission("LOCAL_DEVICE_SETTINGS");
        boolean canHardwareSetup = PermissionManager.hasPermission("HARDWARE_SETUP");

        makeSaleButton.setEnabled(canMakeSale);
        returnSaleButton.setEnabled(canProcessReturns);
        endOfDayButton.setEnabled(canEndOfDay);
        enterInventoryButton.setEnabled(canEnterInventory);
        receivingHistoryButton.setEnabled(canReceivingHistory);
        storeTransferButton.setEnabled(canStoreTransfer);
        departmentListButton.setEnabled(canDepartmentManagement);
        vendorListButton.setEnabled(canVendorManagement);
        viewSalesButton.setEnabled(canViewSales);
        customerAccountsButton.setEnabled(canCustomerAccounts);
        viewInventoryButton.setEnabled(canViewInventory);
        addItemButton.setEnabled(canAddItem);
        editItemsButton.setEnabled(canEditItem);
        timeClockButton.setEnabled(canTimeClock);
        payrollDashboardButton.setEnabled(canPayrollDashboard);
        employeeManagementButton.setEnabled(canEmployeeManagement);
        rolesPermissionsButton.setEnabled(canRolesPermissions);
        locationManagementButton.setEnabled(canLocationManagement);
        companyCustomizationButton.setEnabled(canCompanyCustomization);
        localDeviceSettingsButton.setEnabled(canLocalDeviceSettings);
        hardwareSetupButton.setEnabled(canHardwareSetup);
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
        endOfDayButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("END_OF_DAY", this, "End of Day")) {
                return;
            }
            NavigationManager.openEndOfDay(this);
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
        viewInventoryButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("VIEW_INVENTORY", this, "View Inventory")) {
                return;
            }
            NavigationManager.openViewInventory(this);
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
        locationManagementButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("LOCATION_MANAGEMENT", this, "Location Management")) {
                return;
            }
            NavigationManager.openLocationManagement(this);
        });
        companyCustomizationButton.addActionListener(e -> {
            if (!requireCompanyPreferencesPermission()) {
                return;
            }
            NavigationManager.openCompanyCustomization(this);
        });
        localDeviceSettingsButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("LOCAL_DEVICE_SETTINGS", this, "Local Device Settings")) {
                return;
            }
            NavigationManager.openLocalDeviceSettings(this);
        });
        hardwareSetupButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("HARDWARE_SETUP", this, "Hardware Setup")) {
                return;
            }
            NavigationManager.openHardwareSetup(this);
        });

        logoutButton.addActionListener(e -> {
            endSessionSafely();
            SessionManager.clearSessionState();
            SupabaseSessionManager.clearSession();
            NavigationManager.logoutToLogin(this);
        });
    }

    private void wireWindowSessionHandling() {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                endSessionSafely();
            }
        });
    }

    private void endSessionSafely() {
        try (Connection conn = DB.getConnection()) {
            DeviceService.endCurrentSession(conn);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private JButton createMenuButton(String title, String description, Icon icon) {
        JButton button = new JButton();
        button.setLayout(new BorderLayout(10, 10));
        button.setFocusPainted(false);
        button.setBackground(Color.WHITE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1, true),
                new EmptyBorder(18, 18, 18, 18)
        ));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setPreferredSize(new Dimension(285, 96));
        button.setMinimumSize(new Dimension(285, 96));
        button.setMaximumSize(new Dimension(320, 104));

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(54, 54));
        iconLabel.setMinimumSize(new Dimension(54, 54));
        iconLabel.setMaximumSize(new Dimension(54, 54));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setName("menuButtonTitle");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

        JLabel descriptionLabel = new JLabel("<html><div style='width:170px;'>" + description + "</div></html>");
        descriptionLabel.setName("menuButtonDescription");
        descriptionLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        descriptionLabel.setForeground(ThemeManager.isDarkModeEnabled() ? Color.WHITE : new Color(90, 90, 90));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setOpaque(false);
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(8));
        textPanel.add(descriptionLabel);

        button.add(iconLabel, BorderLayout.WEST);
        button.add(textPanel, BorderLayout.CENTER);

        return button;
    }

    private boolean hasCompanyPreferencesPermission() {
        return PermissionManager.hasPermission("COMPANY_PREFERENCES")
                || PermissionManager.hasPermission("COMPANY_CUSTOMIZATION");
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

    public JButton getMakeSaleButton() {
        return makeSaleButton;
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

    public JButton getCompanyCustomizationButton() {
        return companyCustomizationButton;
    }

    public JButton getLocalDeviceSettingsButton() {
        return localDeviceSettingsButton;
    }

    public JButton getHardwareSetupButton() {
        return hardwareSetupButton;
    }

    public JButton getLogoutButton() {
        return logoutButton;
    }
}
