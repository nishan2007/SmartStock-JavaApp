package ui.screens;
import managers.NavigationManager;
import managers.PermissionManager;
import managers.SupabaseSessionManager;
import managers.SessionManager;
import services.DeviceService;
import ui.components.AppMenuBar;
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
    private final JButton enterInventoryButton;
    private final JButton viewSalesButton;
    private final JButton customerAccountsButton;
    private final JButton viewInventoryButton;
    private final JButton addItemButton;
    private final JButton editItemsButton;
    private final JButton employeeManagementButton;
    private final JButton rolesPermissionsButton;
    private final JButton localDeviceSettingsButton;
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
        enterInventoryButton = createMenuButton("Enter Inventory", "Add received stock to inventory", loadIcon("src/ICONS/ViewInventory.png"));
        viewSalesButton = createMenuButton("View Sales", "Review previous transactions", loadIcon("src/ICONS/ViewSales.png"));
        customerAccountsButton = createMenuButton("Customers", "Manage customer credit accounts", loadIcon("src/ICONS/Employee.png"));
        viewInventoryButton = createMenuButton("View Inventory", "View current inventory levels", loadIcon("src/ICONS/ViewInventory.png"));
        addItemButton = createMenuButton("Add Item", "Add a new product to inventory", loadIcon("src/ICONS/NewItem.png"));
        editItemsButton = createMenuButton("Edit Items", "Update product information", loadIcon("src/ICONS/EditItem.png"));
        employeeManagementButton = createMenuButton("Employees", "Manage employee accounts", loadIcon("src/ICONS/Employee.png"));
        rolesPermissionsButton = createMenuButton("Roles & Permissions", "Configure user access", loadIcon("src/ICONS/Security.png"));
        localDeviceSettingsButton = createMenuButton("Local Device", "Edit register receipt settings", loadIcon("src/ICONS/Security.png"));
        applyPermissions();

        JPanel sectionStackPanel = new JPanel();
        sectionStackPanel.setLayout(new BoxLayout(sectionStackPanel, BoxLayout.Y_AXIS));
        sectionStackPanel.setOpaque(false);
        sectionStackPanel.add(createSectionPanel(
                "Point of Sale",
                new Color(37, 99, 235),
                makeSaleButton,
                viewSalesButton,
                customerAccountsButton
        ));
        sectionStackPanel.add(Box.createVerticalStrut(18));
        sectionStackPanel.add(createSectionPanel(
                "Inventory",
                new Color(5, 150, 105),
                enterInventoryButton,
                viewInventoryButton,
                addItemButton,
                editItemsButton
        ));
        sectionStackPanel.add(Box.createVerticalStrut(18));
        sectionStackPanel.add(createSectionPanel(
                "Employee",
                new Color(217, 119, 6),
                employeeManagementButton
        ));
        sectionStackPanel.add(Box.createVerticalStrut(18));
        sectionStackPanel.add(createSectionPanel(
                "Admin",
                new Color(124, 58, 237),
                rolesPermissionsButton,
                localDeviceSettingsButton
        ));

        JScrollPane sectionScrollPane = new JScrollPane(sectionStackPanel);
        sectionScrollPane.setBorder(BorderFactory.createEmptyBorder());
        sectionScrollPane.setOpaque(false);
        sectionScrollPane.getViewport().setOpaque(false);
        sectionScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        sectionScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

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
        JPanel sectionPanel = new JPanel(new BorderLayout(0, 14));
        sectionPanel.setBackground(Color.WHITE);
        sectionPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(16, 16, 16, 16)
        ));
        sectionPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sectionPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 170));
        sectionPanel.setPreferredSize(new Dimension(1000, 170));

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

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        buttonPanel.setOpaque(false);

        for (JButton button : buttons) {
            buttonPanel.add(button);
        }

        sectionPanel.add(headerPanel, BorderLayout.NORTH);
        sectionPanel.add(buttonPanel, BorderLayout.CENTER);
        return sectionPanel;
    }

    public void applyPermissions() {
        boolean canMakeSale = PermissionManager.hasPermission("MAKE_SALE");
        boolean canEnterInventory = PermissionManager.hasPermission("ENTER_INVENTORY");
        boolean canViewSales = PermissionManager.hasPermission("VIEW_SALES");
        boolean canCustomerAccounts = PermissionManager.hasPermission("CUSTOMER_ACCOUNTS");
        boolean canViewInventory = PermissionManager.hasPermission("VIEW_INVENTORY");
        boolean canAddItem = PermissionManager.hasPermission("NEW_ITEM");
        boolean canEditItem = PermissionManager.hasPermission("EDIT_ITEM");
        boolean canEmployeeManagement = PermissionManager.hasPermission("EMPLOYEE_MANAGEMENT");
        boolean canRolesPermissions = PermissionManager.hasPermission("ROLE_MANAGEMENT");
        boolean canLocalDeviceSettings = PermissionManager.hasPermission("LOCAL_DEVICE_SETTINGS");

        makeSaleButton.setEnabled(canMakeSale);
        enterInventoryButton.setEnabled(canEnterInventory);
        viewSalesButton.setEnabled(canViewSales);
        customerAccountsButton.setEnabled(canCustomerAccounts);
        viewInventoryButton.setEnabled(canViewInventory);
        addItemButton.setEnabled(canAddItem);
        editItemsButton.setEnabled(canEditItem);
        employeeManagementButton.setEnabled(canEmployeeManagement);
        rolesPermissionsButton.setEnabled(canRolesPermissions);
        localDeviceSettingsButton.setEnabled(canLocalDeviceSettings);
    }



    private void wireActions() {
        makeSaleButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("MAKE_SALE", this, "Make a Sale")) {
                return;
            }
            NavigationManager.openMakeSale(this);
        });
        enterInventoryButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("ENTER_INVENTORY", this, "Enter Inventory")) {
                return;
            }
            NavigationManager.openEnterInventory(this);
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
        localDeviceSettingsButton.addActionListener(e -> {
            if (!PermissionManager.requirePermission("LOCAL_DEVICE_SETTINGS", this, "Local Device Settings")) {
                return;
            }
            NavigationManager.openLocalDeviceSettings(this);
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
        button.setPreferredSize(new Dimension(245, 88));
        button.setMinimumSize(new Dimension(245, 88));
        button.setMaximumSize(new Dimension(245, 88));

        JLabel iconLabel = new JLabel(icon);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(54, 54));
        iconLabel.setMinimumSize(new Dimension(54, 54));
        iconLabel.setMaximumSize(new Dimension(54, 54));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

        JLabel descriptionLabel = new JLabel("<html><div style='width:170px; color:#666666;'>" + description + "</div></html>");
        descriptionLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));

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

    public JButton getMakeSaleButton() {
        return makeSaleButton;
    }

    public JButton getEnterInventoryButton() {
        return enterInventoryButton;
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

    public JButton getEmployeeManagementButton() {
        return employeeManagementButton;
    }

    public JButton getRolesPermissionsButton() {
        return rolesPermissionsButton;
    }

    public JButton getLocalDeviceSettingsButton() {
        return localDeviceSettingsButton;
    }

    public JButton getLogoutButton() {
        return logoutButton;
    }
}
