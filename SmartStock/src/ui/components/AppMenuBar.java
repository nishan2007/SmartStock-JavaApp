package ui.components;
import services.DeviceService;
import managers.NavigationManager;
import managers.PermissionManager;
import managers.SessionManager;
import managers.SupabaseSessionManager;
import data.DB;
import ui.screens.CompanyCustomization;
import ui.screens.CustomerAccounts;
import ui.screens.DepartmentList;
import ui.screens.EditItem;
import ui.screens.EndOfDay;
import ui.screens.EnterInventory;
import ui.screens.EmployeeManagement;
import ui.screens.HardwareSetup;
import ui.screens.LocalDeviceSettings;
import ui.screens.LocationManagement;
import ui.screens.MainMenu;
import ui.screens.MakeASale;
import ui.screens.NewItem;
import ui.screens.PayrollDashboard;
import ui.screens.ReceivingHistory;
import ui.screens.Roles_Permission;
import ui.screens.ReturnSale;
import ui.screens.StoreTransfer;
import ui.screens.TimeClock;
import ui.screens.VendorList;
import ui.screens.ViewInventory;
import ui.screens.ViewSales;
import ui.helpers.WindowHelper;

import javax.swing.*;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AppMenuBar {

    public static JMenuBar create(JFrame parent, String currentScreen) {
        JMenuBar menuBar = new JMenuBar();

        JMenu pointOfSaleMenu = new JMenu("Point of Sale");
        JMenu inventoryMenu = new JMenu("Inventory");
        JMenu employeeMenu = new JMenu("Employee");
        JMenu adminMenu = new JMenu("Admin");

        JMenuItem mainMenuItem = new JMenuItem("Main Menu");
        JMenuItem makeSaleItem = new JMenuItem("Make a Sale");
        JMenuItem returnSaleItem = new JMenuItem("Returns");
        JMenuItem endOfDayItem = new JMenuItem("End of Day");
        JMenuItem enterInventoryItem = new JMenuItem("Receiving Inventory");
        JMenuItem receivingHistoryItem = new JMenuItem("Receiving History");
        JMenuItem storeTransferItem = new JMenuItem("Store Transfer");
        JMenuItem departmentListItem = new JMenuItem("Departments");
        JMenuItem vendorListItem = new JMenuItem("Vendors");
        JMenuItem newItemItem = new JMenuItem("New Item");
        JMenuItem editItemItem = new JMenuItem("Edit Item");
        JMenuItem employeeMgmtItem = new JMenuItem("Employee Management");
        JMenuItem timeClockItem = new JMenuItem("Time Clock");
        JMenuItem payrollDashboardItem = new JMenuItem("Payroll Dashboard");
        JMenuItem rolesPermissionItem = new JMenuItem("Roles & Permission");
        JMenuItem locationManagementItem = new JMenuItem("Locations");
        JMenuItem companyCustomizationItem = new JMenuItem("Company Preferences");
        JMenuItem customerAccountsItem = new JMenuItem("Customer Accounts");
        JMenuItem ViewSalesItem = new JMenuItem("View Sales");
        JMenuItem viewInventoryItem = new JMenuItem("View Inventory");
        JMenuItem localDeviceSettingsItem = new JMenuItem("Local Device Settings");
        JMenuItem hardwareSetupItem = new JMenuItem("Hardware Setup");

        boolean canMakeSale = PermissionManager.hasPermission("MAKE_SALE");
        boolean canProcessReturns = PermissionManager.hasPermission("PROCESS_RETURNS");
        boolean canEndOfDay = PermissionManager.hasPermission("END_OF_DAY");
        boolean canNewItem = PermissionManager.hasPermission("NEW_ITEM");
        boolean canEditItem = PermissionManager.hasPermission("EDIT_ITEM");
        boolean canEnterInventory = PermissionManager.hasPermission("RECEIVING_INVENTORY");
        boolean canReceivingHistory = PermissionManager.hasPermission("VIEW_RECEIVING_HISTORY");
        boolean canStoreTransfer = PermissionManager.hasPermission("STORE_TRANSFER");
        boolean canDepartmentManagement = PermissionManager.hasPermission("DEPARTMENT_MANAGEMENT");
        boolean canVendorManagement = PermissionManager.hasPermission("VENDOR_MANAGEMENT");
        boolean canViewSales = PermissionManager.hasPermission("VIEW_SALES");
        boolean canViewInventory = PermissionManager.hasPermission("VIEW_INVENTORY");
        boolean canCustomerAccounts = PermissionManager.hasPermission("CUSTOMER_ACCOUNTS");

        boolean canEmployeeMgmt = PermissionManager.hasPermission("EMPLOYEE_MANAGEMENT");
        boolean canTimeClock = PermissionManager.hasPermission("TIME_CLOCK");
        boolean canPayrollDashboard = PermissionManager.hasPermission("PAYROLL_DASHBOARD");
        boolean canRoleManagement = PermissionManager.hasPermission("ROLE_MANAGEMENT");
        boolean canLocationManagement = PermissionManager.hasPermission("LOCATION_MANAGEMENT");
        boolean canCompanyCustomization = hasCompanyPreferencesPermission();
        boolean canChangeStore = PermissionManager.hasPermission("CHANGE_STORE");
        boolean canLocalDeviceSettings = PermissionManager.hasPermission("LOCAL_DEVICE_SETTINGS");
        boolean canHardwareSetup = PermissionManager.hasPermission("HARDWARE_SETUP");
        boolean canOpenMainMenu = canMakeSale || canProcessReturns || canEndOfDay || canNewItem || canEditItem || canEnterInventory || canReceivingHistory || canStoreTransfer || canDepartmentManagement || canVendorManagement || canViewSales || canViewInventory || canCustomerAccounts || canEmployeeMgmt || canTimeClock || canPayrollDashboard || canRoleManagement || canLocationManagement || canCompanyCustomization || canLocalDeviceSettings || canHardwareSetup;
        String screenKey = currentScreen == null ? "" : currentScreen.trim();
        if (!canOpenMainMenu || "MainMenu".equalsIgnoreCase(screenKey)) {
            mainMenuItem.setEnabled(false);
        }
        if (!canMakeSale || "MakeASale".equalsIgnoreCase(screenKey)) {
            makeSaleItem.setEnabled(false);
        }
        if (!canProcessReturns || "ReturnSale".equalsIgnoreCase(screenKey)) {
            returnSaleItem.setEnabled(false);
        }
        if (!canEndOfDay || "EndOfDay".equalsIgnoreCase(screenKey)) {
            endOfDayItem.setEnabled(false);
        }
        if (!canEnterInventory || "EnterInventory".equalsIgnoreCase(screenKey)) {
            enterInventoryItem.setEnabled(false);
        }
        if (!canReceivingHistory || "ReceivingHistory".equalsIgnoreCase(screenKey)) {
            receivingHistoryItem.setEnabled(false);
        }
        if (!canStoreTransfer || "StoreTransfer".equalsIgnoreCase(screenKey)) {
            storeTransferItem.setEnabled(false);
        }
        if (!canDepartmentManagement || "DepartmentList".equalsIgnoreCase(screenKey)) {
            departmentListItem.setEnabled(false);
        }
        if (!canVendorManagement || "VendorList".equalsIgnoreCase(screenKey)) {
            vendorListItem.setEnabled(false);
        }
        mainMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (WindowHelper.focusIfAlreadyOpen(MainMenu.class)) {
                    parent.dispose();
                    return;
                }
                NavigationManager.showMainMenu(parent);
            }
        });
        if (!canNewItem || "NewItem".equalsIgnoreCase(screenKey)) {
            newItemItem.setEnabled(false);
        }
        if (!canEditItem || "EditItem".equalsIgnoreCase(screenKey)) {
            editItemItem.setEnabled(false);
        }
        if (!canViewSales || "ViewSales".equalsIgnoreCase(screenKey)) {
            ViewSalesItem.setEnabled(false);
        }
        if (!canViewInventory || "ViewInventory".equalsIgnoreCase(screenKey)) {
            viewInventoryItem.setEnabled(false);
        }
        if (!canCustomerAccounts || "CustomerAccounts".equalsIgnoreCase(screenKey)) {
            customerAccountsItem.setEnabled(false);
        }
        if (!canEmployeeMgmt || "EmployeeManagement".equalsIgnoreCase(screenKey)) {
            employeeMgmtItem.setEnabled(false);
        }
        if (!canTimeClock || "TimeClock".equalsIgnoreCase(screenKey)) {
            timeClockItem.setEnabled(false);
        }
        if (!canPayrollDashboard || "PayrollDashboard".equalsIgnoreCase(screenKey)) {
            payrollDashboardItem.setEnabled(false);
        }
        if (!canRoleManagement || "Roles_Permission".equalsIgnoreCase(screenKey)) {
            rolesPermissionItem.setEnabled(false);
        }
        if (!canLocationManagement || "LocationManagement".equalsIgnoreCase(screenKey)) {
            locationManagementItem.setEnabled(false);
        }
        if (!canCompanyCustomization || "CompanyCustomization".equalsIgnoreCase(screenKey)) {
            companyCustomizationItem.setEnabled(false);
        }
        if (!canLocalDeviceSettings || "LocalDeviceSettings".equalsIgnoreCase(screenKey)) {
            localDeviceSettingsItem.setEnabled(false);
        }
        if (!canHardwareSetup || "HardwareSetup".equalsIgnoreCase(screenKey)) {
            hardwareSetupItem.setEnabled(false);
        }

        makeSaleItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("MAKE_SALE", parent, "Make a Sale")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(MakeASale.class)) {
                    parent.dispose();
                    return;
                }
                NavigationManager.openMakeSale(parent);
            }
        });

        returnSaleItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("PROCESS_RETURNS", parent, "Returns")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(ReturnSale.class)) {
                    return;
                }
                NavigationManager.openReturnSale(parent);
            }
        });

        endOfDayItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("END_OF_DAY", parent, "End of Day")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(EndOfDay.class)) {
                    return;
                }
                NavigationManager.openEndOfDay(parent);
            }
        });

        enterInventoryItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("RECEIVING_INVENTORY", parent, "Receiving Inventory")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(EnterInventory.class)) {
                    return;
                }
                NavigationManager.openEnterInventory(parent);
            }
        });

        receivingHistoryItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("VIEW_RECEIVING_HISTORY", parent, "Receiving History")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(ReceivingHistory.class)) {
                    return;
                }
                NavigationManager.openReceivingHistory(parent);
            }
        });

        storeTransferItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("STORE_TRANSFER", parent, "Store Transfer")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(StoreTransfer.class)) {
                    return;
                }
                NavigationManager.openStoreTransfer(parent);
            }
        });

        departmentListItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("DEPARTMENT_MANAGEMENT", parent, "Department Management")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(DepartmentList.class)) {
                    return;
                }
                NavigationManager.openDepartmentList(parent);
            }
        });

        vendorListItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("VENDOR_MANAGEMENT", parent, "Vendor Management")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(VendorList.class)) {
                    return;
                }
                NavigationManager.openVendorList(parent);
            }
        });

        newItemItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("NEW_ITEM", parent, "New Item")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(NewItem.class)) {
                    return;
                }
                NavigationManager.openNewItem(parent);
            }
        });

        editItemItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("EDIT_ITEM", parent, "Edit Item")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(EditItem.class)) {
                    return;
                }
                NavigationManager.openEditItem(parent);
            }
        });

        ViewSalesItem.addActionListener(new ActionListener() {
             public void actionPerformed(ActionEvent e) {
                  if (!PermissionManager.requirePermission("VIEW_SALES", parent, "View Sales")) {
                      return;
                  }
                  if (WindowHelper.focusIfAlreadyOpen(ViewSales.class)) {
                       return;
                  }
                  NavigationManager.openViewSales(parent);
             }
        });


        viewInventoryItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("VIEW_INVENTORY",parent,"View Inventory")){
                return;
                }
                if (WindowHelper.focusIfAlreadyOpen(ViewInventory.class)) {
                return;
                }
                NavigationManager.openViewInventory(parent);
            }
        });

        customerAccountsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("CUSTOMER_ACCOUNTS", parent, "Customer Accounts")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(CustomerAccounts.class)) {
                    return;
                }
                NavigationManager.openCustomerAccounts(parent);
            }
        });



        employeeMgmtItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("EMPLOYEE_MANAGEMENT", parent, "Employee Management")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(EmployeeManagement.class)) {
                    return;
                }
                NavigationManager.openEmployeeManagement(parent);
            }
        });

        timeClockItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("TIME_CLOCK", parent, "Time Clock")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(TimeClock.class)) {
                    return;
                }
                NavigationManager.openTimeClock(parent);
            }
        });

        payrollDashboardItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("PAYROLL_DASHBOARD", parent, "Payroll Dashboard")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(PayrollDashboard.class)) {
                    return;
                }
                NavigationManager.openPayrollDashboard(parent);
            }
        });

        rolesPermissionItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("ROLE_MANAGEMENT", parent, "Roles & Permission")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(Roles_Permission.class)) {
                    return;
                }
                NavigationManager.openRolesPermission(parent);
            }
        });

        locationManagementItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("LOCATION_MANAGEMENT", parent, "Location Management")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(LocationManagement.class)) {
                    return;
                }
                NavigationManager.openLocationManagement(parent);
            }
        });

        companyCustomizationItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!requireCompanyPreferencesPermission(parent)) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(CompanyCustomization.class)) {
                    return;
                }
                NavigationManager.openCompanyCustomization(parent);
            }
        });

        localDeviceSettingsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("LOCAL_DEVICE_SETTINGS", parent, "Local Device Settings")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(LocalDeviceSettings.class)) {
                    return;
                }
                NavigationManager.openLocalDeviceSettings(parent);
            }
        });

        hardwareSetupItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("HARDWARE_SETUP", parent, "Hardware Setup")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(HardwareSetup.class)) {
                    return;
                }
                NavigationManager.openHardwareSetup(parent);
            }
        });

        pointOfSaleMenu.add(mainMenuItem);
        pointOfSaleMenu.addSeparator();
        pointOfSaleMenu.add(makeSaleItem);
        pointOfSaleMenu.add(returnSaleItem);
        pointOfSaleMenu.add(endOfDayItem);
        pointOfSaleMenu.add(ViewSalesItem);
        pointOfSaleMenu.add(customerAccountsItem);

        inventoryMenu.add(enterInventoryItem);
        inventoryMenu.add(receivingHistoryItem);
        inventoryMenu.add(storeTransferItem);
        inventoryMenu.add(departmentListItem);
        inventoryMenu.add(vendorListItem);
        inventoryMenu.add(viewInventoryItem);
        inventoryMenu.add(newItemItem);
        inventoryMenu.add(editItemItem);

        employeeMenu.add(employeeMgmtItem);
        employeeMenu.add(timeClockItem);
        employeeMenu.add(payrollDashboardItem);

        adminMenu.add(rolesPermissionItem);
        adminMenu.add(locationManagementItem);
        adminMenu.add(companyCustomizationItem);
        adminMenu.add(localDeviceSettingsItem);
        adminMenu.add(hardwareSetupItem);

        JMenu sessionMenu = new JMenu("Session");
        JMenuItem changeStoreItem = new JMenuItem("Change Store");
        JMenuItem closeItem = new JMenuItem("Close");
        JMenuItem logoutItem = new JMenuItem("Logout");

        if (!canChangeStore) {
            changeStoreItem.setEnabled(false);
        }

        changeStoreItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showChangeLocationDialog(parent, currentScreen);
            }
        });
        closeItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                NavigationManager.closeApplication(parent);
            }
        });

        logoutItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try (Connection conn = DB.getConnection()) {
                    DeviceService.endCurrentSession(conn);
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }

                SessionManager.clearSessionState();
                SupabaseSessionManager.clearSession();
                NavigationManager.logoutToLogin(parent);
            }
        });




        sessionMenu.add(changeStoreItem);
        sessionMenu.addSeparator();
        sessionMenu.add(closeItem);
        sessionMenu.add(logoutItem);


        menuBar.add(pointOfSaleMenu);
        menuBar.add(inventoryMenu);
        menuBar.add(employeeMenu);
        menuBar.add(adminMenu);
        menuBar.add(sessionMenu);

        return menuBar;
    }

    private static boolean hasCompanyPreferencesPermission() {
        return PermissionManager.hasPermission("COMPANY_PREFERENCES")
                || PermissionManager.hasPermission("COMPANY_CUSTOMIZATION");
    }

    private static boolean requireCompanyPreferencesPermission(Component parent) {
        if (hasCompanyPreferencesPermission()) {
            return true;
        }
        JOptionPane.showMessageDialog(
                parent,
                "You do not have permission to access Company Preferences.",
                "Access Denied",
                JOptionPane.WARNING_MESSAGE
        );
        return false;
    }


    private static void showChangeLocationDialog(JFrame parent, String currentScreen) {
        if (!PermissionManager.hasPermission("CHANGE_STORE")) {
            JOptionPane.showMessageDialog(
                    parent,
                    "You do not have permission to change stores.",
                    "Access Denied",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        List<StoreOption> allowedStores = getAllowedStoresFromSession();

        if (allowedStores.isEmpty()) {
            JOptionPane.showMessageDialog(
                    parent,
                    "No allowed store locations were found for this user.",
                    "Change Store",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        JComboBox<StoreOption> storeCombo = new JComboBox<>(allowedStores.toArray(new StoreOption[0]));
        storeCombo.setSelectedItem(findCurrentStoreOption(allowedStores));

        JPanel panel = new JPanel();
        panel.add(new JLabel("Select store:"));
        panel.add(storeCombo);

        int result = JOptionPane.showConfirmDialog(
                parent,
                panel,
                "Change Store",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            StoreOption selected = (StoreOption) storeCombo.getSelectedItem();
            if (selected != null) {
                boolean updated = setCurrentStoreInSession(selected.id);

                if (updated) {
                    JOptionPane.showMessageDialog(
                            parent,
                            "Current store changed to: " + SessionManager.getCurrentLocationName(),
                            "Store Updated",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                    refreshCurrentScreen(parent, currentScreen);
                } else {
                    JOptionPane.showMessageDialog(
                            parent,
                            "Could not change the current store.",
                            "Store Update Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }
    }

    private static void refreshCurrentScreen(JFrame parent, String currentScreen) {
        if (currentScreen == null || parent == null) {
            return;
        }

        SwingUtilities.invokeLater(() -> NavigationManager.refreshCurrentScreen(parent, currentScreen));
    }

    private static StoreOption findCurrentStoreOption(List<StoreOption> allowedStores) {
        Integer currentId = getCurrentStoreIdFromSession();
        if (currentId == null) {
            return allowedStores.get(0);
        }

        for (StoreOption option : allowedStores) {
            if (option.id == currentId) {
                return option;
            }
        }

        return allowedStores.get(0);
    }

    private static Integer getCurrentStoreIdFromSession() {
        return SessionManager.getCurrentLocationId();
    }

    private static boolean setCurrentStoreInSession(int storeId) {
        List<StoreOption> stores = getAllowedStoresFromSession();

        for (StoreOption store : stores) {
            if (store.id == storeId) {
                SessionManager.setCurrentLocationId(store.id);
                SessionManager.setCurrentLocationName(store.label);
                SessionManager.setCurrentLocationTimezone(store.timezone);
                return true;
            }
        }

        return false;
    }

    private static List<StoreOption> getAllowedStoresFromSession() {
        List<StoreOption> stores = new ArrayList<>();

        if (SessionManager.getCurrentUserId() == null) {
            return stores;
        }

        String storesSql = """
                SELECT l.location_id,
                       l.name,
                       COALESCE(l.timezone, '') AS timezone
                FROM user_locations ul
                JOIN locations l ON ul.location_id = l.location_id
                WHERE ul.user_id = ?
                ORDER BY l.name
                """;

        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement(storesSql)) {

            ps.setInt(1, SessionManager.getCurrentUserId());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    stores.add(new StoreOption(
                            rs.getInt("location_id"),
                            rs.getString("name"),
                            rs.getString("timezone")
                    ));
                }
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(
                    null,
                    "Could not load allowed stores: " + ex.getMessage(),
                    "Store Load Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }

        return stores;
    }


    private static class StoreOption {
        private final int id;
        private final String label;
        private final String timezone;

        private StoreOption(int id, String label, String timezone) {
            this.id = id;
            this.label = label;
            this.timezone = timezone;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
