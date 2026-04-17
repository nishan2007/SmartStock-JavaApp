package managers;

import ui.screens.EditItem;
import ui.screens.EnterInventory;
import ui.screens.CustomerAccounts;
import ui.screens.EmployeeManagement;
import ui.screens.MainMenu;
import ui.screens.MakeASale;
import ui.screens.NewItem;
import ui.screens.Roles_Permission;
import ui.screens.ViewInventory;
import ui.screens.ViewSales;
import ui.screens.Login;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public final class NavigationManager {

    private static boolean transitionInProgress = false;
    private static MainMenu activeMainMenu;

    private NavigationManager() {
    }

    public enum ScreenType {
        MAIN_MENU,
        MAKE_SALE,
        ENTER_INVENTORY,
        NEW_ITEM,
        EDIT_ITEM,
        VIEW_SALES,
        VIEW_INVENTORY,
        CUSTOMER_ACCOUNTS,
        EMPLOYEE_MANAGEMENT,
        ROLES_PERMISSION
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

    public static void openEnterInventory(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.ENTER_INVENTORY));
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

    public static void openEmployeeManagement(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.EMPLOYEE_MANAGEMENT));
    }

    public static void openRolesPermission(JFrame parent) {
        openScreen(parent, createScreen(ScreenType.ROLES_PERMISSION));
    }

    private static JFrame createScreen(ScreenType screenType) {
        return switch (screenType) {
            case MAIN_MENU -> new MainMenu();
            case MAKE_SALE -> new MakeASale();
            case ENTER_INVENTORY -> new EnterInventory();
            case NEW_ITEM -> new NewItem();
            case EDIT_ITEM -> new EditItem();
            case VIEW_SALES -> new ViewSales();
            case VIEW_INVENTORY -> new ViewInventory();
            case CUSTOMER_ACCOUNTS -> new CustomerAccounts();
            case EMPLOYEE_MANAGEMENT -> new EmployeeManagement();
            case ROLES_PERMISSION -> new Roles_Permission();
        };
    }

    private static ScreenType parseScreenType(String currentScreenName) {
        if (currentScreenName == null || currentScreenName.isBlank()) {
            return null;
        }

        return switch (currentScreenName) {
            case "MainMenu" -> ScreenType.MAIN_MENU;
            case "MakeASale" -> ScreenType.MAKE_SALE;
            case "EnterInventory" -> ScreenType.ENTER_INVENTORY;
            case "NewItem" -> ScreenType.NEW_ITEM;
            case "EditItem" -> ScreenType.EDIT_ITEM;
            case "ViewSales" -> ScreenType.VIEW_SALES;
            case "ViewInventory" -> ScreenType.VIEW_INVENTORY;
            case "CustomerAccounts" -> ScreenType.CUSTOMER_ACCOUNTS;
            case "EmployeeManagement" -> ScreenType.EMPLOYEE_MANAGEMENT;
            case "Roles_Permission" -> ScreenType.ROLES_PERMISSION;
            default -> null;
        };
    }

    private static void showExistingMainMenu(JFrame relativeTo) {
        if (activeMainMenu != null) {
            activeMainMenu.applyPermissions();
            if (relativeTo != null) {
                activeMainMenu.setLocationRelativeTo(relativeTo);
            }
            activeMainMenu.setVisible(true);
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
        });

        childScreen.setLocationRelativeTo(mainMenu);
        childScreen.setVisible(true);
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

        newScreen.setLocationRelativeTo(currentScreen);
        newScreen.setVisible(true);
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
            menu.setLocationRelativeTo(currentScreen);
            menu.setVisible(true);
        }

        if (currentScreen != null) {
            currentScreen.dispose();
        }
        transitionInProgress = false;
    }
}
