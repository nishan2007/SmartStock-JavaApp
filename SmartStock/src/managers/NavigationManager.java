package managers;

import ui.screens.BalanceDraw;
import ui.screens.BalanceSheet;
import ui.screens.ChangeBasket;
import ui.screens.EditItem;
import ui.screens.EnterInventory;
import ui.screens.CustomerAccounts;
import ui.screens.CompanyCustomization;
import ui.screens.customorders.CustomOrderItems;
import ui.screens.customorders.CustomOrders;
import ui.screens.DeviceManagement;
import ui.screens.DepartmentList;
import ui.screens.EmployeeManagement;
import ui.screens.MainMenu;
import ui.screens.MakeASale;
import ui.screens.OrdersManagerDashboard;
import ui.screens.MachineManagement;
import ui.screens.NewItem;
import ui.screens.Orders;
import ui.screens.MaintenanceManagement;
import ui.screens.PartsManagement;
import ui.screens.ReceivingHistory;
import ui.screens.Reports;
import ui.screens.Roles_Permission;
import ui.screens.ReturnSale;
import ui.screens.Invoices;
import ui.screens.StoreTransfer;
import ui.screens.VendorList;
import ui.screens.ViewInventory;
import ui.screens.PriceTagPrinting;
import ui.screens.ViewSales;
import ui.screens.Login;
import ui.screens.WelcomeFrame;
import ui.screens.PayrollDashboard;
import ui.screens.TimeClock;
import ui.screens.WeeklySchedule;
import ui.screens.WorkstationPreferences;
import ui.helpers.WindowHelper;
import ui.helpers.PerformanceDiagnostics;
import ui.helpers.UiTaskRunner;

import javax.swing.*;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Supplier;

public final class NavigationManager {

    private static boolean transitionInProgress = false;
    private static JFrame activeMainMenu;

    private NavigationManager() {
    }

    public enum ScreenType {
        MAIN_MENU,
        MAKE_SALE,
        RETURN_SALE,
        BALANCE_DRAW,
        CHANGE_BASKET,
        BALANCE_SHEET,
        ORDERS_MANAGER_DASHBOARD,
        REPORTS,
        RECEIVING_INVENTORY,
        RECEIVING_HISTORY,
        STORE_TRANSFER,
        CUSTOM_ORDER_ITEMS,
        DEPARTMENT_LIST,
        VENDOR_LIST,
        NEW_ITEM,
        EDIT_ITEM,
        VIEW_SALES,
        VIEW_INVENTORY,
        PRICE_TAG_PRINTING,
        CUSTOMER_ACCOUNTS,
        QUOTATIONS,
        INVOICES,
        CUSTOM_ORDERS,
        ORDERS,
        TIME_CLOCK,
        PAYROLL_DASHBOARD,
        WEEKLY_SCHEDULE,
        EMPLOYEE_MANAGEMENT,
        ROLES_PERMISSION,
        DEVICE_MANAGEMENT,
        MACHINE_MANAGEMENT,
        PARTS_MANAGEMENT,
        MAINTENANCE_MANAGEMENT,
        COMPANY_CUSTOMIZATION,
        WORKSTATION_PREFERENCES
    }

    private static void openScreen(JFrame parent, ScreenType screenType) {
        if (transitionInProgress || screenType == null) return;
        transitionInProgress = true;
        MainMenu sourceMenu = parent instanceof MainMenu mainMenu ? mainMenu : null;
        if (sourceMenu != null) sourceMenu.setNavigationInProgress(true);
        long started = System.nanoTime();
        try {
            JFrame screen = createScreen(screenType);
            if (screen == null) {
                transitionInProgress = false;
                if (sourceMenu != null) sourceMenu.setNavigationInProgress(false);
                return;
            }
            if (parent instanceof MainMenu mainMenu) {
                openFromMainMenuPrepared(mainMenu, screen);
            } else {
                switchChildScreenPrepared(parent, screen);
            }
            PerformanceDiagnostics.record("navigation", screenType.name(), started, true, -1);
        } catch (RuntimeException | Error ex) {
            transitionInProgress = false;
            if (sourceMenu != null) sourceMenu.setNavigationInProgress(false);
            PerformanceDiagnostics.record("navigation", screenType.name(), started, false, -1);
            throw ex;
        }
    }

    public static void refreshCurrentScreen(JFrame parent, String currentScreenName) {
        ScreenType screenType = parseScreenType(currentScreenName);
        if (screenType == null) {
            return;
        }

        openScreen(parent, screenType);
    }

    public static void openMakeSale(JFrame parent) {
        openScreen(parent, ScreenType.MAKE_SALE);
    }

    public static void openReturnSale(JFrame parent) {
        openScreen(parent, ScreenType.RETURN_SALE);
    }

    public static void openBalanceDraw(JFrame parent) {
        openScreen(parent, ScreenType.BALANCE_DRAW);
    }

    public static void openChangeBasket(JFrame parent) {
        openScreen(parent, ScreenType.CHANGE_BASKET);
    }

    public static void openBalanceSheet(JFrame parent) {
        openScreen(parent, ScreenType.BALANCE_SHEET);
    }

    public static void openOrdersManagerDashboard(JFrame parent) {
        openScreen(parent, ScreenType.ORDERS_MANAGER_DASHBOARD);
    }

    public static void openReports(JFrame parent) {
        openScreen(parent, ScreenType.REPORTS);
    }

    public static void openEnterInventory(JFrame parent) {
        openScreen(parent, ScreenType.RECEIVING_INVENTORY);
    }

    public static void openReceivingHistory(JFrame parent) {
        openScreen(parent, ScreenType.RECEIVING_HISTORY);
    }

    public static void openStoreTransfer(JFrame parent) {
        openScreen(parent, ScreenType.STORE_TRANSFER);
    }

    public static void openCustomOrderItems(JFrame parent) {
        openScreen(parent, ScreenType.CUSTOM_ORDER_ITEMS);
    }

    public static void openDepartmentList(JFrame parent) {
        openScreen(parent, ScreenType.DEPARTMENT_LIST);
    }

    public static void openVendorList(JFrame parent) {
        openScreen(parent, ScreenType.VENDOR_LIST);
    }

    public static void openNewItem(JFrame parent) {
        openScreen(parent, ScreenType.NEW_ITEM);
    }

    public static void openEditItem(JFrame parent) {
        openScreen(parent, ScreenType.EDIT_ITEM);
    }

    public static void openViewSales(JFrame parent) {
        openScreen(parent, ScreenType.VIEW_SALES);
    }

    public static void openViewInventory(JFrame parent) {
        openScreen(parent, ScreenType.VIEW_INVENTORY);
    }

    public static void openPriceTagPrinting(JFrame parent) { openScreen(parent, ScreenType.PRICE_TAG_PRINTING); }

    public static void openCustomerAccounts(JFrame parent) {
        openScreen(parent, ScreenType.CUSTOMER_ACCOUNTS);
    }

    public static void openQuotations(JFrame parent) {
        openScreen(parent, ScreenType.QUOTATIONS);
    }

    public static void openInvoices(JFrame parent) {
        openScreen(parent, ScreenType.INVOICES);
    }

    public static void openCustomOrders(JFrame parent) {
        openScreen(parent, ScreenType.CUSTOM_ORDERS);
    }

    public static void openOrders(JFrame parent) {
        openScreen(parent, ScreenType.ORDERS);
    }

    public static void openTimeClock(JFrame parent) {
        openScreen(parent, ScreenType.TIME_CLOCK);
    }

    public static void openPayrollDashboard(JFrame parent) {
        openScreen(parent, ScreenType.PAYROLL_DASHBOARD);
    }

    public static void openWeeklySchedule(JFrame parent) {
        openScreen(parent, ScreenType.WEEKLY_SCHEDULE);
    }

    public static void openEmployeeManagement(JFrame parent) {
        openScreen(parent, ScreenType.EMPLOYEE_MANAGEMENT);
    }

    public static void openRolesPermission(JFrame parent) {
        openScreen(parent, ScreenType.ROLES_PERMISSION);
    }

    public static void openDeviceManagement(JFrame parent) {
        openScreen(parent, ScreenType.DEVICE_MANAGEMENT);
    }

    public static void openMachineManagement(JFrame parent) {
        openScreen(parent, ScreenType.MACHINE_MANAGEMENT);
    }

    public static void openPartsManagement(JFrame parent) {
        openScreen(parent, ScreenType.PARTS_MANAGEMENT);
    }

    public static void openMaintenanceManagement(JFrame parent) {
        openScreen(parent, ScreenType.MAINTENANCE_MANAGEMENT);
    }

    public static void openCompanyCustomization(JFrame parent) {
        openScreen(parent, ScreenType.COMPANY_CUSTOMIZATION);
    }

    public static void openWorkstationPreferences(JFrame parent) {
        openScreen(parent, ScreenType.WORKSTATION_PREFERENCES);
    }

    private static JFrame createScreen(ScreenType screenType) {
        return switch (screenType) {
            case MAIN_MENU -> new DeferredScreenFrame(
                    "SmartStock", "Preparing main menu...", MainMenu::new, true);
            case MAKE_SALE -> deferred("Point of Sale", "Preparing point of sale...", MakeASale::new);
            case RETURN_SALE -> deferred("Returns", "Preparing returns...", ReturnSale::new);
            case BALANCE_DRAW -> new DeferredScreenFrame(
                    "Balance Draw", "Preparing cash drawer...", BalanceDraw::new);
            case CHANGE_BASKET -> new DeferredScreenFrame(
                    "Change Basket", "Preparing change basket...", ChangeBasket::new);
            case BALANCE_SHEET -> deferred("Balance Sheet", "Preparing balance sheet...", BalanceSheet::new);
            case ORDERS_MANAGER_DASHBOARD -> deferred("Orders Dashboard", "Preparing orders dashboard...", OrdersManagerDashboard::new);
            case REPORTS -> deferred("Reports", "Preparing reports...", Reports::new);
            case RECEIVING_INVENTORY -> deferred("Receiving Inventory", "Preparing receiving...", EnterInventory::new);
            case RECEIVING_HISTORY -> deferred("Receiving History", "Preparing receiving history...", ReceivingHistory::new);
            case STORE_TRANSFER -> deferred("Store Transfer", "Preparing store transfer...", StoreTransfer::new);
            case CUSTOM_ORDER_ITEMS -> new DeferredScreenFrame(
                    "Custom Order Items", "Preparing custom order items...", CustomOrderItems::new);
            case DEPARTMENT_LIST -> deferred("Departments", "Preparing departments...", DepartmentList::new);
            case VENDOR_LIST -> deferred("Vendors", "Preparing vendors...", VendorList::new);
            case NEW_ITEM -> new DeferredScreenFrame(
                    "Add New Item", "Preparing item form...", NewItem::new);
            case EDIT_ITEM -> deferred("Edit Items", "Preparing item editor...", EditItem::new);
            case VIEW_SALES -> deferred("View Sales", "Preparing sales history...", ViewSales::new);
            case VIEW_INVENTORY -> deferred("View Inventory", "Preparing inventory...", ViewInventory::new);
            case PRICE_TAG_PRINTING -> deferred("Price Tag Printing", "Preparing price tags...", PriceTagPrinting::new);
            case CUSTOMER_ACCOUNTS -> deferred("Customers", "Preparing customer accounts...", CustomerAccounts::new);
            case QUOTATIONS -> deferred("Quotations", "Preparing quotations...", () -> new Invoices(Invoices.InitialTab.QUOTATIONS));
            case INVOICES -> deferred("Invoices", "Preparing invoices...", Invoices::new);
            case CUSTOM_ORDERS -> deferred("Customer Orders", "Preparing customer orders...", CustomOrders::new);
            case ORDERS -> deferred("Orders", "Preparing orders...", Orders::new);
            case TIME_CLOCK -> deferred("Time Clock", "Preparing time clock...", TimeClock::new);
            case PAYROLL_DASHBOARD -> deferred("Payroll", "Preparing payroll...", PayrollDashboard::new);
            case WEEKLY_SCHEDULE -> deferred("Employee Schedule", "Preparing employee schedule...", WeeklySchedule::new);
            case EMPLOYEE_MANAGEMENT -> deferred("Employees", "Preparing employee management...", EmployeeManagement::new);
            case ROLES_PERMISSION -> deferred("Roles & Permissions", "Preparing roles and permissions...", Roles_Permission::new);
            case DEVICE_MANAGEMENT -> deferred("Device Management", "Preparing devices...", DeviceManagement::new);
            case MACHINE_MANAGEMENT -> deferred("Machines", "Preparing machines...", MachineManagement::new);
            case PARTS_MANAGEMENT -> deferred("Parts", "Preparing parts...", PartsManagement::new);
            case MAINTENANCE_MANAGEMENT -> deferred("Maintenance", "Preparing maintenance...", MaintenanceManagement::new);
            case COMPANY_CUSTOMIZATION -> new DeferredScreenFrame(
                    "Company Preferences", "Preparing company preferences...", CompanyCustomization::new);
            case WORKSTATION_PREFERENCES -> new DeferredScreenFrame(
                    "Workstation Preferences", "Preparing workstation preferences...",
                    WorkstationPreferences::new);
        };
    }

    private static JFrame deferred(String title, String loadingText, Supplier<JFrame> factory) {
        return new DeferredScreenFrame(title, loadingText, factory);
    }

    private static ScreenType parseScreenType(String currentScreenName) {
        if (currentScreenName == null || currentScreenName.isBlank()) {
            return null;
        }

        return switch (currentScreenName) {
            case "MainMenu" -> ScreenType.MAIN_MENU;
            case "MakeASale" -> ScreenType.MAKE_SALE;
            case "ReturnSale" -> ScreenType.RETURN_SALE;
            case "BalanceDraw" -> ScreenType.BALANCE_DRAW;
            case "ChangeBasket" -> ScreenType.CHANGE_BASKET;
            case "BalanceSheet" -> ScreenType.BALANCE_SHEET;
            case "OrdersManagerDashboard" -> ScreenType.ORDERS_MANAGER_DASHBOARD;
            case "Reports", "EndOfDay", "OrdersEndOfDay" -> ScreenType.REPORTS;
            case "EnterInventory" -> ScreenType.RECEIVING_INVENTORY;
            case "ReceivingHistory" -> ScreenType.RECEIVING_HISTORY;
            case "StoreTransfer" -> ScreenType.STORE_TRANSFER;
            case "CustomOrderItems" -> ScreenType.CUSTOM_ORDER_ITEMS;
            case "DepartmentList" -> ScreenType.DEPARTMENT_LIST;
            case "VendorList" -> ScreenType.VENDOR_LIST;
            case "NewItem" -> ScreenType.NEW_ITEM;
            case "EditItem" -> ScreenType.EDIT_ITEM;
            case "ViewSales" -> ScreenType.VIEW_SALES;
            case "ViewInventory" -> ScreenType.VIEW_INVENTORY;
            case "PriceTagPrinting" -> ScreenType.PRICE_TAG_PRINTING;
            case "CustomerAccounts" -> ScreenType.CUSTOMER_ACCOUNTS;
            case "Quotations", "QuotationsOrders" -> ScreenType.QUOTATIONS;
            case "Invoices" -> ScreenType.INVOICES;
            case "CustomOrders" -> ScreenType.CUSTOM_ORDERS;
            case "Orders" -> ScreenType.ORDERS;
            case "TimeClock" -> ScreenType.TIME_CLOCK;
            case "PayrollDashboard" -> ScreenType.PAYROLL_DASHBOARD;
            case "WeeklySchedule" -> ScreenType.WEEKLY_SCHEDULE;
            case "EmployeeManagement" -> ScreenType.EMPLOYEE_MANAGEMENT;
            case "Roles_Permission" -> ScreenType.ROLES_PERMISSION;
            case "DeviceManagement" -> ScreenType.DEVICE_MANAGEMENT;
            case "MachineManagement" -> ScreenType.MACHINE_MANAGEMENT;
            case "PartsManagement" -> ScreenType.PARTS_MANAGEMENT;
            case "MaintenanceManagement" -> ScreenType.MAINTENANCE_MANAGEMENT;
            case "CompanyCustomization" -> ScreenType.COMPANY_CUSTOMIZATION;
            case "WorkstationPreferences" -> ScreenType.WORKSTATION_PREFERENCES;
            default -> null;
        };
    }

    private static void showExistingMainMenu(JFrame relativeTo) {
        if (activeMainMenu != null) {
            if (activeMainMenu instanceof MainMenu mainMenu) mainMenu.setNavigationInProgress(false);
            WindowHelper.showPosWindow(activeMainMenu, relativeTo);
            activeMainMenu.toFront();
            activeMainMenu.requestFocus();
        }
    }

    private static void openFromMainMenuPrepared(MainMenu mainMenu, JFrame childScreen) {
        activeMainMenu = mainMenu;
        if (childScreen.getRootPane() != null) {
            childScreen.getRootPane().putClientProperty("returnToMainMenu", Boolean.TRUE);
        }
        childScreen.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        childScreen.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                transitionInProgress = false;
            }

            @Override
            public void windowClosed(WindowEvent e) {
                UiTaskRunner.cancelAll(childScreen);
                transitionInProgress = false;
                Object returnToMainMenu = null;
                if (childScreen.getRootPane() != null) {
                    returnToMainMenu = childScreen.getRootPane().getClientProperty("returnToMainMenu");
                }
                if (!Boolean.FALSE.equals(returnToMainMenu)) {
                    showExistingMainMenu(childScreen);
                }
            }

            @Override
            public void windowClosing(WindowEvent e) {
                if (transitionInProgress) {
                    return;
                }
                closeApplication(childScreen);
            }
        });

        WindowHelper.showPosWindow(childScreen, mainMenu);
        mainMenu.setVisible(false);
        transitionInProgress = false;
    }

    private static void switchChildScreenPrepared(JFrame currentScreen, JFrame newScreen) {
        if (currentScreen != null && currentScreen.getRootPane() != null) {
            currentScreen.getRootPane().putClientProperty("returnToMainMenu", Boolean.FALSE);
        }
        if (newScreen.getRootPane() != null) {
            newScreen.getRootPane().putClientProperty("returnToMainMenu", Boolean.TRUE);
        }
        newScreen.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        newScreen.addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                transitionInProgress = false;
            }

            @Override
            public void windowClosed(WindowEvent e) {
                UiTaskRunner.cancelAll(newScreen);
                transitionInProgress = false;
                Object returnToMainMenu = null;
                if (newScreen.getRootPane() != null) {
                    returnToMainMenu = newScreen.getRootPane().getClientProperty("returnToMainMenu");
                }
                if (!Boolean.FALSE.equals(returnToMainMenu)) {
                    showExistingMainMenu(newScreen);
                }
            }
        });

        WindowHelper.showPosWindow(newScreen, currentScreen);
        transitionInProgress = false;
        UiTaskRunner.cancelAll(currentScreen);
        currentScreen.dispose();
    }

    public static void logoutToLogin(JFrame currentScreen) {
        if (transitionInProgress) {
            return;
        }

        transitionInProgress = true;

        if (currentScreen != null && currentScreen.getRootPane() != null) {
            currentScreen.getRootPane().putClientProperty("returnToMainMenu", Boolean.FALSE);
        }

        if (activeMainMenu != null) {
            if (activeMainMenu != currentScreen) {
                activeMainMenu.dispose();
            }
            activeMainMenu = null;
        }

        Point loginLocation = currentScreen == null || !currentScreen.isShowing()
                ? null : currentScreen.getLocationOnScreen();
        if (currentScreen != null) {
            UiTaskRunner.cancelAll(currentScreen);
            currentScreen.dispose();
        }
        // Let the disposed screen and any card-reader workers finish their window-close
        // callbacks before the new Login claims the PC/SC reader.
        SwingUtilities.invokeLater(() -> {
            try {
                Login login = new Login();
                if (loginLocation != null) login.setLocation(loginLocation);
                login.toFront();
                login.requestFocus();
            } finally {
                transitionInProgress = false;
            }
        });
    }

    /** Returns from the login prompt to the normal Welcome screen without clearing a saved session. */
    public static void returnToWelcomeFromLogin(JFrame login) {
        if (transitionInProgress) return;
        transitionInProgress = true;
        try {
            if (login != null && login.getRootPane() != null) {
                login.getRootPane().putClientProperty("returnToMainMenu", Boolean.FALSE);
            }

            WelcomeFrame welcome = new WelcomeFrame();
            welcome.setLocationRelativeTo(login);
            welcome.setVisible(true);
            welcome.toFront();
            welcome.requestFocus();

            if (login != null) {
                UiTaskRunner.cancelAll(login);
                login.dispose();
            }
        } finally {
            transitionInProgress = false;
        }
    }

    /** Locks a disconnected register at Welcome without deleting its saved employee session. */
    public static void returnToWelcomeForConnectionLoss() {
        Runnable transition = () -> {
            for (Window window : Window.getWindows()) {
                if (window instanceof WelcomeFrame && window.isVisible()) return;
            }

            transitionInProgress = true;
            for (Window window : Window.getWindows()) {
                if (window instanceof JFrame frame && frame.getRootPane() != null) {
                    frame.getRootPane().putClientProperty("returnToMainMenu", Boolean.FALSE);
                }
                if (window.isDisplayable()) UiTaskRunner.cancelAll(window);
            }

            activeMainMenu = null;
            SupabaseSessionManager.clearSession();
            SessionManager.clearSessionState();

            WelcomeFrame welcome = new WelcomeFrame(true);
            welcome.setVisible(true);
            welcome.toFront();
            welcome.requestFocus();

            for (Window window : Window.getWindows()) {
                if (window != welcome && window.isDisplayable()) window.dispose();
            }
            transitionInProgress = false;
        };
        if (SwingUtilities.isEventDispatchThread()) transition.run();
        else SwingUtilities.invokeLater(transition);
    }

    public static void showMainMenu(JFrame currentScreen) {
        if (transitionInProgress) {
            return;
        }

        transitionInProgress = true;

        if (currentScreen != null && currentScreen.getRootPane() != null) {
            currentScreen.getRootPane().putClientProperty("returnToMainMenu", Boolean.FALSE);
        }

        if (activeMainMenu != null) {
            showExistingMainMenu(currentScreen);
        } else {
            JFrame menu = createScreen(ScreenType.MAIN_MENU);
            activeMainMenu = menu;
            WindowHelper.showPosWindow(menu, currentScreen);
        }

        if (currentScreen != null) {
            currentScreen.dispose();
        }
        transitionInProgress = false;
    }

    public static void showMainMenuAfterLogin(JFrame login) {
        if (transitionInProgress) return;
        transitionInProgress = true;
        try {
            if (activeMainMenu != null && activeMainMenu.isDisplayable()) {
                showExistingMainMenu(login);
            } else {
                JFrame menu = createScreen(ScreenType.MAIN_MENU);
                activeMainMenu = menu;
                WindowHelper.showPosWindow(menu, login);
            }
            if (login != null) login.dispose();
        } finally {
            transitionInProgress = false;
        }
    }

    public static void closeApplication(JFrame currentScreen) {
        transitionInProgress = true;

        services.LanApiClient.logoutWithoutWaiting();

        for (Window window : Window.getWindows()) {
            if (!(window instanceof JFrame frame)) {
                continue;
            }
            if (frame.getRootPane() != null) {
                frame.getRootPane().putClientProperty("returnToMainMenu", Boolean.FALSE);
            }
        }

        activeMainMenu = null;

        for (Window window : Window.getWindows()) {
            if (window.isDisplayable()) {
                window.dispose();
            }
        }

        transitionInProgress = false;
    }

    private static void promoteDeferredScreen(DeferredScreenFrame shell, JFrame screen) {
        if (!shell.isDisplayable()) {
            screen.dispose();
            return;
        }
        if (shell.getRootPane() != null) {
            shell.getRootPane().putClientProperty("returnToMainMenu", Boolean.FALSE);
        }
        if (shell.mainMenuDestination) {
            activeMainMenu = screen;
            WindowHelper.showPosWindow(screen, shell);
            shell.dispose();
            services.AppUpdateService.checkForUpdatesAsync(screen, false);
            return;
        }
        if (screen.getRootPane() != null) {
            screen.getRootPane().putClientProperty("returnToMainMenu", Boolean.TRUE);
        }
        screen.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        screen.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                UiTaskRunner.cancelAll(screen);
                Object returnToMainMenu = screen.getRootPane() == null ? null
                        : screen.getRootPane().getClientProperty("returnToMainMenu");
                if (!Boolean.FALSE.equals(returnToMainMenu)) {
                    showExistingMainMenu(screen);
                }
            }

            @Override
            public void windowClosing(WindowEvent event) {
                if (!transitionInProgress) closeApplication(screen);
            }
        });
        WindowHelper.showPosWindow(screen, shell);
        shell.dispose();
    }

    private static final class DeferredScreenFrame extends JFrame {
        private final String taskKey;
        private final String loadingText;
        private final Supplier<JFrame> screenFactory;
        private final boolean mainMenuDestination;
        private final JLabel statusLabel = new JLabel("", SwingConstants.CENTER);
        private final JButton retryButton = new JButton("Retry");
        private boolean started;

        private DeferredScreenFrame(String title, String loadingText, Supplier<JFrame> screenFactory) {
            this(title, loadingText, screenFactory, false);
        }

        private DeferredScreenFrame(String title, String loadingText, Supplier<JFrame> screenFactory,
                                    boolean mainMenuDestination) {
            super(title);
            this.taskKey = "deferred-screen." + title.toLowerCase().replace(' ', '-');
            this.loadingText = loadingText;
            this.screenFactory = screenFactory;
            this.mainMenuDestination = mainMenuDestination;
            setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            setSize(900, 640);
            setLocationRelativeTo(null);
            JPanel shell = new JPanel(new java.awt.BorderLayout(12, 12));
            shell.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
            statusLabel.setText(loadingText);
            statusLabel.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 16));
            shell.add(statusLabel, java.awt.BorderLayout.CENTER);
            retryButton.setVisible(false);
            retryButton.addActionListener(event -> startPreparing());
            JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER));
            actions.add(retryButton);
            shell.add(actions, java.awt.BorderLayout.SOUTH);
            add(shell);
            WindowHelper.configurePosWindow(this);
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent event) {
                    startPreparing();
                }
            });
        }

        private void startPreparing() {
            if (started || !isDisplayable()) return;
            started = true;
            retryButton.setVisible(false);
            statusLabel.setText(loadingText);
            UiTaskRunner.submit(this, taskKey, screenFactory::get,
                    screen -> promoteDeferredScreen(this, screen), failure -> {
                        started = false;
                        statusLabel.setText("Could not prepare this screen: " + safeMessage(failure));
                        retryButton.setVisible(true);
                    });
        }

        private static String safeMessage(Throwable failure) {
            String message = failure == null ? null : failure.getMessage();
            return message == null || message.isBlank() ? "Unknown error" : message;
        }
    }
}
