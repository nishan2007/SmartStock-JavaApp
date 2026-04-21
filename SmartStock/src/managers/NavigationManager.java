package managers;

import data.DB;
import services.DeviceService;
import ui.screens.EditItem;
import ui.screens.EnterInventory;
import ui.screens.CustomerAccounts;
import ui.screens.DepartmentList;
import ui.screens.EmployeeManagement;
import ui.screens.EndOfDay;
import ui.screens.MainMenu;
import ui.screens.MakeASale;
import ui.screens.NewItem;
import ui.screens.ReceivingHistory;
import ui.screens.Roles_Permission;
import ui.screens.ReturnSale;
import ui.screens.StoreTransfer;
import ui.screens.VendorList;
import ui.screens.ViewInventory;
import ui.screens.ViewSales;
import ui.screens.Login;
import ui.screens.LocalDeviceSettings;
import ui.screens.PayrollDashboard;
import ui.screens.TimeClock;
import ui.helpers.WindowHelper;

import javax.swing.*;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.sql.Connection;
import java.sql.SQLException;

public final class NavigationManager {

    private static boolean transitionInProgress = false;
    private static MainMenu activeMainMenu;

    private NavigationManager() {
    }

    public enum ScreenType {
        MAIN_MENU,
        MAKE_SALE,
        RETURN_SALE,
        END_OF_DAY,
        RECEIVING_INVENTORY,
        RECEIVING_HISTORY,
        STORE_TRANSFER,
        DEPARTMENT_LIST,
        VENDOR_LIST,
        NEW_ITEM,
        EDIT_ITEM,
        VIEW_SALES,
        VIEW_INVENTORY,
        CUSTOMER_ACCOUNTS,
        TIME_CLOCK,
        PAYROLL_DASHBOARD,
        EMPLOYEE_MANAGEMENT,
        ROLES_PERMISSION,
        LOCAL_DEVICE_SETTINGS
    }

    private static void openScreen(JFrame parent, JFrame screen) {
        if (screen == null) {
            return;
        }

        if (parent instanceof MainMenu mainMenu) {
            openFromMainMenu(mainMenu, screen);
        } else {
            switchChildScreen(parent, screen);
        }
    }

    public static void refreshCurrentScreen(JFrame parent, String currentScreenName) {
        ScreenType screenType = parseScreenType(currentScreenName);
        if (screenType == null) {
            return;
        }

        openScreen(parent, createScreen(screenType));
    }

    public static void openMakeSale(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.MAKE_SALE));
    }

    public static void openReturnSale(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.RETURN_SALE));
    }

    public static void openEndOfDay(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.END_OF_DAY));
    }

    public static void openEnterInventory(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.RECEIVING_INVENTORY));
    }

    public static void openReceivingHistory(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.RECEIVING_HISTORY));
    }

    public static void openStoreTransfer(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.STORE_TRANSFER));
    }

    public static void openDepartmentList(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.DEPARTMENT_LIST));
    }

    public static void openVendorList(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.VENDOR_LIST));
    }

    public static void openNewItem(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.NEW_ITEM));
    }

    public static void openEditItem(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.EDIT_ITEM));
    }

    public static void openViewSales(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.VIEW_SALES));
    }

    public static void openViewInventory(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.VIEW_INVENTORY));
    }

    public static void openCustomerAccounts(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.CUSTOMER_ACCOUNTS));
    }

    public static void openTimeClock(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.TIME_CLOCK));
    }

    public static void openPayrollDashboard(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.PAYROLL_DASHBOARD));
    }

    public static void openEmployeeManagement(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.EMPLOYEE_MANAGEMENT));
    }

    public static void openRolesPermission(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.ROLES_PERMISSION));
    }

    public static void openLocalDeviceSettings(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.LOCAL_DEVICE_SETTINGS));
    }

    private static JFrame createScreen(ScreenType screenType) {
        return switch (screenType) {
            case MAIN_MENU -> new MainMenu();
            case MAKE_SALE -> new MakeASale();
            case RETURN_SALE -> new ReturnSale();
            case END_OF_DAY -> new EndOfDay();
            case RECEIVING_INVENTORY -> new EnterInventory();
            case RECEIVING_HISTORY -> new ReceivingHistory();
            case STORE_TRANSFER -> new StoreTransfer();
            case DEPARTMENT_LIST -> new DepartmentList();
            case VENDOR_LIST -> new VendorList();
            case NEW_ITEM -> new NewItem();
            case EDIT_ITEM -> new EditItem();
            case VIEW_SALES -> new ViewSales();
            case VIEW_INVENTORY -> new ViewInventory();
            case CUSTOMER_ACCOUNTS -> new CustomerAccounts();
            case TIME_CLOCK -> new TimeClock();
            case PAYROLL_DASHBOARD -> new PayrollDashboard();
            case EMPLOYEE_MANAGEMENT -> new EmployeeManagement();
            case ROLES_PERMISSION -> new Roles_Permission();
            case LOCAL_DEVICE_SETTINGS -> new LocalDeviceSettings();
        };
    }

    private static ScreenType parseScreenType(String currentScreenName) {
        if (currentScreenName == null || currentScreenName.isBlank()) {
            return null;
        }

        return switch (currentScreenName) {
            case "MainMenu" -> ScreenType.MAIN_MENU;
            case "MakeASale" -> ScreenType.MAKE_SALE;
            case "ReturnSale" -> ScreenType.RETURN_SALE;
            case "EndOfDay" -> ScreenType.END_OF_DAY;
            case "EnterInventory" -> ScreenType.RECEIVING_INVENTORY;
            case "ReceivingHistory" -> ScreenType.RECEIVING_HISTORY;
            case "StoreTransfer" -> ScreenType.STORE_TRANSFER;
            case "DepartmentList" -> ScreenType.DEPARTMENT_LIST;
            case "VendorList" -> ScreenType.VENDOR_LIST;
            case "NewItem" -> ScreenType.NEW_ITEM;
            case "EditItem" -> ScreenType.EDIT_ITEM;
            case "ViewSales" -> ScreenType.VIEW_SALES;
            case "ViewInventory" -> ScreenType.VIEW_INVENTORY;
            case "CustomerAccounts" -> ScreenType.CUSTOMER_ACCOUNTS;
            case "TimeClock" -> ScreenType.TIME_CLOCK;
            case "PayrollDashboard" -> ScreenType.PAYROLL_DASHBOARD;
            case "EmployeeManagement" -> ScreenType.EMPLOYEE_MANAGEMENT;
            case "Roles_Permission" -> ScreenType.ROLES_PERMISSION;
            case "LocalDeviceSettings" -> ScreenType.LOCAL_DEVICE_SETTINGS;
            default -> null;
        };
    }

    private static void showExistingMainMenu(JFrame relativeTo) {
        if (activeMainMenu != null) {
            activeMainMenu.applyPermissions();
            WindowHelper.showPosWindow(activeMainMenu, relativeTo);
            activeMainMenu.toFront();
            activeMainMenu.requestFocus();
        }
    }

    public static void openFromMainMenu(MainMenu mainMenu, JFrame childScreen) {
        if (transitionInProgress) {
            return;
        }

        transitionInProgress = true;
        activeMainMenu = mainMenu;

        mainMenu.setVisible(false);
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
        transitionInProgress = false;
    }

    public static void switchChildScreen(JFrame currentScreen, JFrame newScreen) {
        if (transitionInProgress) {
            return;
        }

        transitionInProgress = true;
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

        Login login = new Login();
        login.setLocationRelativeTo(currentScreen);
        login.setVisible(true);

        if (currentScreen != null) {
            currentScreen.dispose();
        }

        transitionInProgress = false;
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
            MainMenu menu = new MainMenu();
            activeMainMenu = menu;
            WindowHelper.showPosWindow(menu, currentScreen);
        }

        if (currentScreen != null) {
            currentScreen.dispose();
        }
        transitionInProgress = false;
    }

    public static void closeApplication(JFrame currentScreen) {
        transitionInProgress = true;

        try (Connection conn = DB.getConnection()) {
            DeviceService.endCurrentSession(conn);
        } catch (SQLException ex) {
            ex.printStackTrace();
        }

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
}
