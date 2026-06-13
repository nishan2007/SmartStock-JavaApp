package ui.components;
import services.DeviceService;
import services.NotificationService;
import services.StoreHydrationService;
import services.SyncWorker;
import managers.NavigationManager;
import managers.PermissionManager;
import managers.SessionManager;
import managers.SupabaseSessionManager;
import data.DB;
import data.DatabaseConfig;
import data.DatabaseMode;
import ui.screens.CompanyCustomization;
import ui.screens.customorders.CustomOrderItems;
import ui.screens.BalanceDraw;
import ui.screens.BalanceSheet;
import ui.screens.CustomerAccounts;
import ui.screens.DatabaseSetup;
import ui.screens.DeviceManagement;
import ui.screens.DepartmentList;
import ui.screens.EditItem;
import ui.screens.EnterInventory;
import ui.screens.EmployeeManagement;
import ui.screens.MaintenanceManagement;
import ui.screens.MainMenu;
import ui.screens.MakeASale;
import ui.screens.OrdersManagerDashboard;
import ui.screens.MachineManagement;
import ui.screens.NewItem;
import ui.screens.NotificationsDialog;
import ui.screens.Orders;
import ui.screens.PartsManagement;
import ui.screens.PayrollDashboard;
import ui.screens.ReceivingHistory;
import ui.screens.Reports;
import ui.screens.Roles_Permission;
import ui.screens.ReturnSale;
import ui.screens.Invoices;
import ui.screens.Quotations;
import ui.screens.StoreTransfer;
import ui.screens.SyncStatus;
import ui.screens.TimeClock;
import ui.screens.VendorList;
import ui.screens.ViewInventory;
import ui.screens.ViewSales;
import ui.screens.WorkstationPreferences;
import ui.helpers.WindowHelper;

import javax.swing.*;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AppMenuBar {

    public static JMenuBar create(JFrame parent, String currentScreen) {
        JMenuBar menuBar = new JMenuBar();

        JMenu pointOfSaleMenu = new JMenu("Point of Sale");
        JMenu operationsMenu = new JMenu("Operations");
        JMenu ordersMenu = new JMenu("Orders");
        JMenu inventoryMenu = new JMenu("Inventory");
        JMenu employeeMenu = new JMenu("Employee");
        JMenu adminMenu = new JMenu("Admin");

        JMenuItem mainMenuItem = new JMenuItem("Main Menu");
        JMenuItem makeSaleItem = new JMenuItem("Make a Sale");
        JMenuItem returnSaleItem = new JMenuItem("Returns");
        JMenuItem balanceDrawItem = new JMenuItem("Balance Draw");
        JMenuItem balanceSheetItem = new JMenuItem("Balance Sheet");
        JMenuItem ordersManagerDashboardItem = new JMenuItem("Orders Manager Dashboard");
        JMenuItem reportsItem = new JMenuItem("Reports");
        JMenuItem enterInventoryItem = new JMenuItem("Receiving Inventory");
        JMenuItem receivingHistoryItem = new JMenuItem("Receiving History");
        JMenuItem storeTransferItem = new JMenuItem("Store Transfer");
        JMenuItem customOrderItemsItem = new JMenuItem("Custom Order Items");
        JMenuItem departmentListItem = new JMenuItem("Departments");
        JMenuItem vendorListItem = new JMenuItem("Vendors");
        JMenuItem maintenanceManagementItem = new JMenuItem("Maintenance");
        JMenuItem newItemItem = new JMenuItem("New Item");
        JMenuItem editItemItem = new JMenuItem("Edit Item");
        JMenuItem employeeMgmtItem = new JMenuItem("Employee Management");
        JMenuItem timeClockItem = new JMenuItem("Time Clock");
        JMenuItem payrollDashboardItem = new JMenuItem("Payroll Dashboard");
        JMenuItem rolesPermissionItem = new JMenuItem("Roles & Permission");
        JMenuItem deviceManagementItem = new JMenuItem("Device Management");
        JMenuItem machineManagementItem = new JMenuItem("Machines");
        JMenuItem partsManagementItem = new JMenuItem("Parts");
        JMenuItem companyCustomizationItem = new JMenuItem("Company Preferences");
        JMenuItem workstationPreferencesItem = new JMenuItem("Workstation Preferences");
        JMenuItem customerAccountsItem = new JMenuItem("Customer Accounts");
        JMenuItem quotationsItem = new JMenuItem("Quotations");
        JMenuItem invoicesItem = new JMenuItem("Invoices");
        JMenuItem customOrdersItem = new JMenuItem("Custom Orders");
        JMenuItem ordersItem = new JMenuItem("Orders");
        JMenuItem ViewSalesItem = new JMenuItem("View Sales");
        JMenuItem viewInventoryItem = new JMenuItem("View Inventory");

        boolean canMakeSale = PermissionManager.hasPermission("MAKE_SALE");
        boolean canProcessReturns = PermissionManager.hasPermission("PROCESS_RETURNS");
        boolean canBalanceDrawer = PermissionManager.hasPermission("BALANCE_DRAWER");
        boolean canBalanceSheet = PermissionManager.hasPermission("BALANCE_SHEET")
                || PermissionManager.hasPermission("END_OF_DAY")
                || PermissionManager.hasPermission("PAYROLL_DASHBOARD");
        boolean canOrdersManagerDashboard = PermissionManager.hasPermission("ORDERS_MANAGER_DASHBOARD")
                || PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS");
        boolean canReports = PermissionManager.hasReportsPermission();
        boolean canNewItem = PermissionManager.hasPermission("NEW_ITEM");
        boolean canEditItem = PermissionManager.hasPermission("EDIT_ITEM");
        boolean canEnterInventory = PermissionManager.hasPermission("RECEIVING_INVENTORY");
        boolean canReceivingHistory = PermissionManager.hasPermission("VIEW_RECEIVING_HISTORY");
        boolean canStoreTransfer = PermissionManager.hasPermission("STORE_TRANSFER");
        boolean canCustomOrderItems = PermissionManager.hasPermission("MANUAL_ADJUSTMENT");
        boolean canDepartmentManagement = PermissionManager.hasPermission("DEPARTMENT_MANAGEMENT");
        boolean canVendorManagement = PermissionManager.hasPermission("VENDOR_MANAGEMENT");
        boolean canMaintenanceManagement = PermissionManager.hasPermission("MAINTENANCE_MANAGEMENT")
                || PermissionManager.hasPermission("MAINTENANCE_TECHNICIAN");
        boolean canViewSales = PermissionManager.hasPermission("VIEW_SALES");
        boolean canViewInventory = PermissionManager.hasPermission("VIEW_INVENTORY");
        boolean canCustomerAccounts = PermissionManager.hasPermission("CUSTOMER_ACCOUNTS");
        boolean canQuotations = PermissionManager.hasPermission("QUOTATIONS_ORDERS")
                || PermissionManager.hasPermission("CREATE_QUOTATION");
        boolean canInvoices = PermissionManager.hasPermission("QUOTATIONS_ORDERS")
                || PermissionManager.hasPermission("MANAGE_INVOICES")
                || PermissionManager.hasPermission("POST_INVOICE_DELIVERY");
        boolean canCustomOrders = PermissionManager.hasPermission("CREATE_CUSTOM_ORDER");
        boolean canOrders = PermissionManager.hasPermission("CREATE_CUSTOM_ORDER")
                || PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS")
                || PermissionManager.hasPermission("VIEW_ASSIGNED_CUSTOM_ORDERS");

        boolean canEmployeeMgmt = PermissionManager.hasPermission("EMPLOYEE_MANAGEMENT");
        boolean canTimeClock = PermissionManager.hasPermission("TIME_CLOCK");
        boolean canPayrollDashboard = PermissionManager.hasPermission("PAYROLL_DASHBOARD");
        boolean canRoleManagement = PermissionManager.hasPermission("ROLE_MANAGEMENT");
        boolean canDeviceManagement = PermissionManager.hasPermission("DEVICE_MANAGEMENT");
        boolean canMachineManagement = PermissionManager.hasPermission("MACHINE_MANAGEMENT");
        boolean canPartsManagement = PermissionManager.hasPermission("PARTS_MANAGEMENT");
        boolean canCompanyCustomization = hasCompanyPreferencesPermission();
        boolean canWorkstationPreferences = hasWorkstationPreferencesPermission();
        boolean canChangeStore = PermissionManager.hasPermission("CHANGE_STORE") && !isStoreLockedToConfiguredLocation();
        boolean canOpenMainMenu = canMakeSale || canProcessReturns || canBalanceDrawer || canBalanceSheet || canReports || canOrdersManagerDashboard || canNewItem || canEditItem || canEnterInventory || canReceivingHistory || canStoreTransfer || canCustomOrderItems || canDepartmentManagement || canVendorManagement || canMaintenanceManagement || canViewSales || canViewInventory || canCustomerAccounts || canQuotations || canInvoices || canCustomOrders || canOrders || canEmployeeMgmt || canTimeClock || canPayrollDashboard || canRoleManagement || canDeviceManagement || canMachineManagement || canPartsManagement || canCompanyCustomization || canWorkstationPreferences;
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
        if (!canBalanceDrawer || "BalanceDraw".equalsIgnoreCase(screenKey)) {
            balanceDrawItem.setEnabled(false);
        }
        if (!canBalanceSheet || "BalanceSheet".equalsIgnoreCase(screenKey)) {
            balanceSheetItem.setEnabled(false);
        }
        if (!canOrdersManagerDashboard || "OrdersManagerDashboard".equalsIgnoreCase(screenKey)) {
            ordersManagerDashboardItem.setEnabled(false);
        }
        if (!canReports || "Reports".equalsIgnoreCase(screenKey)) {
            reportsItem.setEnabled(false);
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
        if (!canCustomOrderItems || "CustomOrderItems".equalsIgnoreCase(screenKey)) {
            customOrderItemsItem.setEnabled(false);
        }
        if (!canDepartmentManagement || "DepartmentList".equalsIgnoreCase(screenKey)) {
            departmentListItem.setEnabled(false);
        }
        if (!canVendorManagement || "VendorList".equalsIgnoreCase(screenKey)) {
            vendorListItem.setEnabled(false);
        }
        if (!canMaintenanceManagement || "MaintenanceManagement".equalsIgnoreCase(screenKey)) {
            maintenanceManagementItem.setEnabled(false);
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
        if (!canQuotations || "Quotations".equalsIgnoreCase(screenKey)) {
            quotationsItem.setEnabled(false);
        }
        if (!canInvoices || "Invoices".equalsIgnoreCase(screenKey)) {
            invoicesItem.setEnabled(false);
        }
        if (!canCustomOrders || "CustomOrders".equalsIgnoreCase(screenKey)) {
            customOrdersItem.setEnabled(false);
        }
        if (!canOrders || "Orders".equalsIgnoreCase(screenKey)) {
            ordersItem.setEnabled(false);
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
        if (!canDeviceManagement || "DeviceManagement".equalsIgnoreCase(screenKey)) {
            deviceManagementItem.setEnabled(false);
        }
        if (!canMachineManagement || "MachineManagement".equalsIgnoreCase(screenKey)) {
            machineManagementItem.setEnabled(false);
        }
        if (!canPartsManagement || "PartsManagement".equalsIgnoreCase(screenKey)) {
            partsManagementItem.setEnabled(false);
        }
        if (!canCompanyCustomization || "CompanyCustomization".equalsIgnoreCase(screenKey)) {
            companyCustomizationItem.setEnabled(false);
        }
        if (!canWorkstationPreferences || "WorkstationPreferences".equalsIgnoreCase(screenKey)) {
            workstationPreferencesItem.setEnabled(false);
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

        balanceDrawItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("BALANCE_DRAWER", parent, "Balance Draw")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(BalanceDraw.class)) {
                    return;
                }
                NavigationManager.openBalanceDraw(parent);
            }
        });

        ordersManagerDashboardItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.hasPermission("ORDERS_MANAGER_DASHBOARD") && !PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS")) {
                    JOptionPane.showMessageDialog(parent, "You do not have permission to access Orders Manager Dashboard.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(OrdersManagerDashboard.class)) {
                    return;
                }
                NavigationManager.openOrdersManagerDashboard(parent);
            }
        });

        reportsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.hasReportsPermission()) {
                    JOptionPane.showMessageDialog(parent, "You do not have permission to access Reports.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(Reports.class)) {
                    return;
                }
                NavigationManager.openReports(parent);
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

        customOrderItemsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("MANUAL_ADJUSTMENT", parent, "Custom Order Items")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(CustomOrderItems.class)) {
                    return;
                }
                NavigationManager.openCustomOrderItems(parent);
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

        maintenanceManagementItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.hasPermission("MAINTENANCE_MANAGEMENT") && !PermissionManager.hasPermission("MAINTENANCE_TECHNICIAN")) {
                    JOptionPane.showMessageDialog(parent, "You do not have permission to access Maintenance Management.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(MaintenanceManagement.class)) {
                    return;
                }
                NavigationManager.openMaintenanceManagement(parent);
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

        quotationsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!canQuotations) {
                    JOptionPane.showMessageDialog(parent, "You do not have permission to access Quotations.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(Quotations.class)) {
                    return;
                }
                NavigationManager.openQuotations(parent);
            }
        });

        invoicesItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!canInvoices) {
                    JOptionPane.showMessageDialog(parent, "You do not have permission to access Invoices.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(Invoices.class)) {
                    return;
                }
                NavigationManager.openInvoices(parent);
            }
        });

        customOrdersItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!canCustomOrders) {
                    JOptionPane.showMessageDialog(parent, "You do not have permission to access Custom Orders.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                NavigationManager.openCustomOrders(parent);
            }
        });

        ordersItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!canOrders) {
                    JOptionPane.showMessageDialog(parent, "You do not have permission to access Orders.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(Orders.class)) {
                    return;
                }
                NavigationManager.openOrders(parent);
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

        balanceSheetItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!canBalanceSheet) {
                    JOptionPane.showMessageDialog(parent, "You do not have permission to access Balance Sheet.", "Access Denied", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(BalanceSheet.class)) {
                    return;
                }
                NavigationManager.openBalanceSheet(parent);
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

        deviceManagementItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("DEVICE_MANAGEMENT", parent, "Device Management")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(DeviceManagement.class)) {
                    return;
                }
                NavigationManager.openDeviceManagement(parent);
            }
        });


        machineManagementItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("MACHINE_MANAGEMENT", parent, "Machine List")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(MachineManagement.class)) {
                    return;
                }
                NavigationManager.openMachineManagement(parent);
            }
        });

        partsManagementItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("PARTS_MANAGEMENT", parent, "Parts List")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(PartsManagement.class)) {
                    return;
                }
                NavigationManager.openPartsManagement(parent);
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

        workstationPreferencesItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!requireWorkstationPreferencesPermission(parent)) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(WorkstationPreferences.class)) {
                    return;
                }
                NavigationManager.openWorkstationPreferences(parent);
            }
        });

        pointOfSaleMenu.add(mainMenuItem);
        pointOfSaleMenu.addSeparator();
        pointOfSaleMenu.add(makeSaleItem);
        pointOfSaleMenu.add(returnSaleItem);
        pointOfSaleMenu.add(ViewSalesItem);
        pointOfSaleMenu.add(customerAccountsItem);

        operationsMenu.add(balanceDrawItem);
        operationsMenu.add(balanceSheetItem);
        operationsMenu.add(reportsItem);

        ordersMenu.add(ordersManagerDashboardItem);
        ordersMenu.add(quotationsItem);
        ordersMenu.add(invoicesItem);
        ordersMenu.add(customOrdersItem);
        ordersMenu.add(ordersItem);
        ordersMenu.add(customOrderItemsItem);

        inventoryMenu.add(enterInventoryItem);
        inventoryMenu.add(receivingHistoryItem);
        inventoryMenu.add(storeTransferItem);
        inventoryMenu.add(departmentListItem);
        inventoryMenu.add(vendorListItem);
        inventoryMenu.add(maintenanceManagementItem);
        inventoryMenu.add(viewInventoryItem);
        inventoryMenu.add(newItemItem);
        inventoryMenu.add(editItemItem);

        employeeMenu.add(employeeMgmtItem);
        employeeMenu.add(timeClockItem);
        employeeMenu.add(payrollDashboardItem);

        adminMenu.add(rolesPermissionItem);
        adminMenu.add(deviceManagementItem);
        adminMenu.add(machineManagementItem);
        adminMenu.add(partsManagementItem);
        adminMenu.add(companyCustomizationItem);
        adminMenu.add(workstationPreferencesItem);

        JMenu sessionMenu = new JMenu("Session");
        JMenuItem changeStoreItem = new JMenuItem("Change Store");
        JMenuItem notificationsItem = new JMenuItem(NotificationService.loadSummary().label());
        JMenuItem syncNowItem = new JMenuItem("Sync Now");
        JMenuItem syncStatusItem = new JMenuItem("Sync Status");
        JMenuItem databaseSetupItem = new JMenuItem("Database Setup");
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
        notificationsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new NotificationsDialog(parent).setVisible(true);
            }
        });
        syncNowItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                runManualSync(parent, syncNowItem);
            }
        });
        syncStatusItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new SyncStatus().setVisible(true);
            }
        });
        databaseSetupItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                new DatabaseSetup(parent).setVisible(true);
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
                SupabaseSessionManager.clearPersistedSession();
                NavigationManager.logoutToLogin(parent);
            }
        });




        sessionMenu.add(changeStoreItem);
        sessionMenu.add(notificationsItem);
        sessionMenu.add(syncNowItem);
        sessionMenu.add(syncStatusItem);
        sessionMenu.add(databaseSetupItem);
        sessionMenu.addSeparator();
        sessionMenu.add(closeItem);
        sessionMenu.add(logoutItem);


        menuBar.add(pointOfSaleMenu);
        menuBar.add(operationsMenu);
        menuBar.add(ordersMenu);
        menuBar.add(inventoryMenu);
        menuBar.add(employeeMenu);
        menuBar.add(adminMenu);
        menuBar.add(sessionMenu);

        return menuBar;
    }

    private static void runManualSync(Component parent, JMenuItem syncNowItem) {
        syncNowItem.setEnabled(false);
        syncNowItem.setText("Syncing...");
        SwingWorker<SyncWorker.SyncStatus, Void> worker = new SwingWorker<>() {
            @Override
            protected SyncWorker.SyncStatus doInBackground() {
                return SyncWorker.runOnceNow();
            }

            @Override
            protected void done() {
                syncNowItem.setEnabled(true);
                syncNowItem.setText("Sync Now");
                try {
                    SyncWorker.SyncStatus status = get();
                    JOptionPane.showMessageDialog(
                            parent,
                            formatSyncStatus(status),
                            "Manual Sync",
                            status.lastError() == null ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE
                    );
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(
                            parent,
                            rootCauseMessage(ex),
                            "Manual Sync",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        };
        worker.execute();
    }

    private static String formatSyncStatus(SyncWorker.SyncStatus status) {
        String currentSync = status.currentSync() != null && status.currentSync().running()
                ? "Running by " + status.currentSync().ownerLabel() + " since " + formatInstant(status.currentSync().acquiredAt())
                : "Idle";
        String backgroundSync = status.serviceInfo() == null
                ? "Unknown"
                : status.serviceInfo().status() + " (" + nullToDash(status.serviceInfo().message()) + ")";
        return "Message: " + nullToDash(status.message())
                + "\nCloud reachable: " + (status.cloudReachable() ? "yes" : "no")
                + "\nBackground service: " + backgroundSync
                + "\nIn-app sync: " + (SyncWorker.isStarted() ? "running while UI is open" : "not running")
                + "\nCurrent sync: " + currentSync
                + "\nLast pushed: " + status.lastPushed()
                + "\nPending events: " + status.pendingCount()
                + "\nFailed events: " + status.failedCount()
                + "\nOpen conflicts: " + status.conflictCount()
                + "\nLast success: " + formatInstant(status.lastSuccess())
                + "\nLast error: " + nullToDash(status.lastError());
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "Never" : instant.toString();
    }

    private static String nullToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.toString() : message;
    }

    private static boolean hasCompanyPreferencesPermission() {
        return PermissionManager.hasPermission("COMPANY_PREFERENCES")
                || PermissionManager.hasPermission("COMPANY_CUSTOMIZATION")
                || PermissionManager.hasPermission("LOCATION_MANAGEMENT")
                || PermissionManager.hasPermission("CASH_DRAWER_MANAGEMENT");
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

    private static boolean hasWorkstationPreferencesPermission() {
        return PermissionManager.hasPermission("COMPANY_PREFERENCES")
                || PermissionManager.hasPermission("COMPANY_CUSTOMIZATION")
                || PermissionManager.hasPermission("LOCAL_DEVICE_SETTINGS")
                || PermissionManager.hasPermission("HARDWARE_SETUP");
    }

    private static boolean requireWorkstationPreferencesPermission(Component parent) {
        if (hasWorkstationPreferencesPermission()) {
            return true;
        }
        JOptionPane.showMessageDialog(
                parent,
                "You do not have permission to access Workstation Preferences.",
                "Access Denied",
                JOptionPane.WARNING_MESSAGE
        );
        return false;
    }


    private static void showChangeLocationDialog(JFrame parent, String currentScreen) {
        if (isStoreLockedToConfiguredLocation()) {
            JOptionPane.showMessageDialog(
                    parent,
                    "This workstation is locked to its configured store in server/client mode.",
                    "Change Store",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        if (!PermissionManager.hasPermission("CHANGE_STORE")) {
            JOptionPane.showMessageDialog(
                    parent,
                    "You do not have permission to change stores.",
                    "Access Denied",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        refreshAssignedStoresForPicker(parent);
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
                if (!hydrateStoreOrConfirmLocal(parent, selected.id)) {
                    return;
                }
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

    private static boolean isStoreLockedToConfiguredLocation() {
        DatabaseMode mode = DatabaseConfig.load().mode();
        return mode == DatabaseMode.SERVER || mode == DatabaseMode.CLIENT;
    }

    private static void refreshAssignedStoresForPicker(Component parent) {
        try (Connection conn = DB.getConnection()) {
            StoreHydrationService.refreshAssignedStores(conn, SessionManager.getCurrentUserId());
        } catch (SQLException ex) {
            showWrappedMessageDialog(
                    parent,
                    "Could not refresh assigned stores from cloud. Showing stores currently available on this local server.\n\n" + ex.getMessage(),
                    "Store Data Refresh",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    private static boolean hydrateStoreOrConfirmLocal(Component parent, int storeId) {
        try (Connection conn = DB.getConnection()) {
            StoreHydrationService.hydrateSelectedStore(conn, storeId);
            return true;
        } catch (SQLException ex) {
            int choice = showWrappedConfirmDialog(
                    parent,
                    "Cloud store refresh failed for the selected store.\n\n"
                            + ex.getMessage()
                            + "\n\nContinue with only the data currently available on this local server?",
                    "Store Data Refresh",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            return choice == JOptionPane.YES_OPTION;
        }
    }

    private static void showWrappedMessageDialog(Component parent, String message, String title, int messageType) {
        JOptionPane.showMessageDialog(parent, wrappedMessage(message), title, messageType);
    }

    private static int showWrappedConfirmDialog(Component parent, String message, String title, int optionType, int messageType) {
        return JOptionPane.showConfirmDialog(parent, wrappedMessage(message), title, optionType, messageType);
    }

    private static JComponent wrappedMessage(String message) {
        JTextArea area = new JTextArea(message);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setColumns(72);
        area.setRows(Math.min(14, Math.max(4, message.length() / 80)));
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new java.awt.Dimension(640, Math.min(260, Math.max(120, area.getRows() * 22))));
        return scrollPane;
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
