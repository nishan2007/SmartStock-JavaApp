package ui.components;
import services.EmployeePinService;
import services.NotificationService;
import services.AppUpdateService;
import services.LanApiClient;
import managers.NavigationManager;
import managers.PermissionManager;
import managers.SessionManager;
import managers.SessionLogoutManager;
import managers.SupabaseSessionManager;
import data.DatabaseConfig;
import data.DatabaseMode;
import data.EnvironmentProfile;
import ui.screens.CompanyCustomization;
import ui.screens.customorders.CustomOrderItems;
import ui.screens.BalanceDraw;
import ui.screens.BalanceSheet;
import ui.screens.CustomerAccounts;
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
import ui.screens.StoreTransfer;
import ui.screens.SyncStatus;
import ui.screens.TimeClock;
import ui.screens.WeeklySchedule;
import ui.screens.VendorList;
import ui.screens.ViewInventory;
import ui.screens.ViewSales;
import ui.screens.WorkstationPreferences;
import ui.helpers.WindowHelper;

import javax.swing.*;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.HierarchyEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class AppMenuBar {
    private static final String NOTIFICATIONS_MENU_PROPERTY = "SmartStock.notificationsMenuItem";
    private static final String STATUS_COMPONENT_PROPERTY = "SmartStock.menuBarStatusComponent";
    private static final Color NOTIFICATION_URGENT_COLOR = new Color(185, 28, 28);
    private static final DateTimeFormatter SESSION_CLOCK_FORMATTER =
            DateTimeFormatter.ofPattern("h:mm EEE, MMM d", Locale.US);

    public static JMenuBar create(JFrame parent, String currentScreen) {
        JMenuBar menuBar = new JMenuBar();

        Object savedStatus = parent.getRootPane().getClientProperty(STATUS_COMPONENT_PROPERTY);
        LoadingStatePanel menuStatus;
        if (savedStatus instanceof LoadingStatePanel existingStatus) {
            menuStatus = existingStatus;
        } else {
            menuStatus = LoadingStatePanel.forMenuBar();
            parent.getRootPane().putClientProperty(STATUS_COMPONENT_PROPERTY, menuStatus);
        }

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
        JMenuItem ordersManagerDashboardItem = new JMenuItem("Orders Dashboard");
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
        JMenuItem sessionTimeClockItem = new JMenuItem("Time Clock");
        JMenuItem payrollDashboardItem = new JMenuItem("Payroll Dashboard");
        JMenuItem weeklyScheduleItem = new JMenuItem("Employee Schedule");
        JMenuItem rolesPermissionItem = new JMenuItem("Roles & Permission");
        JMenuItem deviceManagementItem = new JMenuItem("Device Management");
        JMenuItem machineManagementItem = new JMenuItem("Machines");
        JMenuItem partsManagementItem = new JMenuItem("Parts");
        JMenuItem companyCustomizationItem = new JMenuItem("Company Preferences");
        JMenuItem workstationPreferencesItem = new JMenuItem("Workstation Preferences");
        JMenuItem customerAccountsItem = new JMenuItem("Customer Accounts");
        JMenuItem invoicesItem = new JMenuItem("Quotations & Invoices");
        JMenuItem customOrdersItem = new JMenuItem("Customer Orders");
        JMenuItem ordersItem = new JMenuItem("Orders");
        JMenuItem ViewSalesItem = new JMenuItem("View Sales");
        JMenuItem viewInventoryItem = new JMenuItem("View Inventory");
        JMenuItem notificationsItem = new JMenuItem(NotificationService.cachedSummary().label());
        notificationsItem.putClientProperty(NOTIFICATIONS_MENU_PROPERTY, Boolean.TRUE);

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
        boolean canQuotationsInvoices = PermissionManager.hasPermission("QUOTATIONS_ORDERS")
                || PermissionManager.hasPermission("CREATE_QUOTATION")
                || PermissionManager.hasPermission("MANAGE_INVOICES")
                || PermissionManager.hasPermission("POST_INVOICE_DELIVERY");
        boolean canCustomOrders = PermissionManager.hasPermission("CREATE_CUSTOM_ORDER");
        boolean canOrders = PermissionManager.hasPermission("CREATE_CUSTOM_ORDER")
                || PermissionManager.hasPermission("MANAGE_CUSTOM_ORDERS")
                || PermissionManager.hasPermission("VIEW_ASSIGNED_CUSTOM_ORDERS");

        boolean canEmployeeMgmt = PermissionManager.hasPermission("EMPLOYEE_MANAGEMENT");
        boolean canTimeClock = PermissionManager.hasPermission("TIME_CLOCK");
        boolean canPayrollDashboard = PermissionManager.hasPermission("PAYROLL_DASHBOARD");
        boolean canViewEmployeeSchedule = PermissionManager.hasPermission("VIEW_EMPLOYEE_SCHEDULE");
        boolean canRoleManagement = PermissionManager.hasPermission("ROLE_MANAGEMENT");
        boolean canDeviceManagement = PermissionManager.hasPermission("DEVICE_MANAGEMENT");
        boolean canMachineManagement = PermissionManager.hasPermission("MACHINE_MANAGEMENT");
        boolean canPartsManagement = PermissionManager.hasPermission("PARTS_MANAGEMENT");
        boolean canCompanyCustomization = hasCompanyPreferencesPermission();
        boolean canWorkstationPreferences = hasWorkstationPreferencesPermission();
        boolean canChangeStore = DatabaseConfig.load().mode() == DatabaseMode.REMOTE_ADMIN
                || (PermissionManager.hasPermission("CHANGE_STORE") && !isStoreLockedToConfiguredLocation());
        boolean canOpenMainMenu = canMakeSale || canProcessReturns || canBalanceDrawer || canBalanceSheet || canReports || canOrdersManagerDashboard || canNewItem || canEditItem || canEnterInventory || canReceivingHistory || canStoreTransfer || canCustomOrderItems || canDepartmentManagement || canVendorManagement || canMaintenanceManagement || canViewSales || canViewInventory || canCustomerAccounts || canQuotationsInvoices || canCustomOrders || canOrders || canEmployeeMgmt || canTimeClock || canPayrollDashboard || canViewEmployeeSchedule || canRoleManagement || canDeviceManagement || canMachineManagement || canPartsManagement || canCompanyCustomization || canWorkstationPreferences;
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
        if (!canQuotationsInvoices || "Invoices".equalsIgnoreCase(screenKey) || "Quotations".equalsIgnoreCase(screenKey)) {
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
            sessionTimeClockItem.setEnabled(false);
        }
        if (!canPayrollDashboard || "PayrollDashboard".equalsIgnoreCase(screenKey)) {
            payrollDashboardItem.setEnabled(false);
        }
        if (!canViewEmployeeSchedule || "WeeklySchedule".equalsIgnoreCase(screenKey)) {
            weeklyScheduleItem.setEnabled(false);
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
                    JOptionPane.showMessageDialog(parent, "You do not have permission to access Orders Dashboard.", "Access Denied", JOptionPane.WARNING_MESSAGE);
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

        invoicesItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!canQuotationsInvoices) {
                    JOptionPane.showMessageDialog(parent, "You do not have permission to access Quotations & Invoices.", "Access Denied", JOptionPane.WARNING_MESSAGE);
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
                    JOptionPane.showMessageDialog(parent, "You do not have permission to access Customer Orders.", "Access Denied", JOptionPane.WARNING_MESSAGE);
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

        ActionListener timeClockAction = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("TIME_CLOCK", parent, "Time Clock")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(TimeClock.class)) {
                    return;
                }
                NavigationManager.openTimeClock(parent);
            }
        };
        timeClockItem.addActionListener(timeClockAction);
        sessionTimeClockItem.addActionListener(timeClockAction);

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

        weeklyScheduleItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (!PermissionManager.requirePermission("VIEW_EMPLOYEE_SCHEDULE", parent, "Employee Schedule")) {
                    return;
                }
                if (WindowHelper.focusIfAlreadyOpen(WeeklySchedule.class)) {
                    return;
                }
                NavigationManager.openWeeklySchedule(parent);
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
        operationsMenu.add(maintenanceManagementItem);

        ordersMenu.add(ordersManagerDashboardItem);
        ordersMenu.add(invoicesItem);
        ordersMenu.add(customOrdersItem);
        ordersMenu.add(ordersItem);

        inventoryMenu.add(enterInventoryItem);
        inventoryMenu.add(receivingHistoryItem);
        inventoryMenu.add(storeTransferItem);
        inventoryMenu.add(customOrderItemsItem);
        inventoryMenu.add(viewInventoryItem);
        inventoryMenu.add(newItemItem);
        inventoryMenu.add(editItemItem);

        employeeMenu.add(employeeMgmtItem);
        employeeMenu.add(timeClockItem);
        employeeMenu.add(payrollDashboardItem);
        employeeMenu.add(weeklyScheduleItem);

        adminMenu.add(departmentListItem);
        adminMenu.add(vendorListItem);
        adminMenu.add(rolesPermissionItem);
        adminMenu.add(deviceManagementItem);
        adminMenu.add(machineManagementItem);
        adminMenu.add(partsManagementItem);
        adminMenu.add(companyCustomizationItem);
        adminMenu.add(workstationPreferencesItem);

        JMenu statusMenu = new JMenu("Status");
        JMenu sessionMenu = new JMenu(currentSessionMenuTitle());
        JLabel sessionDateLabel = createSessionDateLabel();
        JMenuItem changeStoreItem = new JMenuItem("Change Store");
        JMenuItem changeEmployeePinItem = new JMenuItem("Change Employee PIN");
        JMenuItem syncNowItem = new JMenuItem("Sync Now");
        JMenuItem syncStatusItem = new JMenuItem("Sync Status");
        JMenuItem remoteQueueItem = new JMenuItem("Remote Change Status");
        JMenuItem checkUpdatesItem = new JMenuItem("Check for Updates");
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
        changeEmployeePinItem.addActionListener(e -> EmployeePinService.changeCurrentEmployeePin(parent));
        notificationsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                NotificationsDialog dialog = new NotificationsDialog(parent);
                if (parent instanceof MainMenu mainMenu) {
                    dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                        @Override
                        public void windowClosed(java.awt.event.WindowEvent e) {
                            mainMenu.refreshNotificationMenu();
                        }
                    });
                }
                dialog.setVisible(true);
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
        remoteQueueItem.setVisible(DatabaseConfig.load().mode() == DatabaseMode.REMOTE_ADMIN);
        remoteQueueItem.addActionListener(e -> ui.helpers.UiTaskRunner.submit(parent, "remote-admin.commands",
                LanApiClient::loadRemoteCommands, commands -> {
                    StringBuilder message = new StringBuilder("Recent changes for ")
                            .append(SessionManager.getCurrentLocationName()).append(":\n\n");
                    if (commands.isEmpty()) message.append("No remote changes recorded.");
                    for (LanApiClient.RemoteCommand command : commands) {
                        message.append(command.status()).append("  ").append(command.operation()).append('\n');
                    }
                    showWrappedMessageDialog(parent, message.toString(), "Remote Change Status", JOptionPane.INFORMATION_MESSAGE);
                }, failure -> JOptionPane.showMessageDialog(parent, failure.getMessage(),
                        "Remote Change Status", JOptionPane.ERROR_MESSAGE)));
        checkUpdatesItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                AppUpdateService.checkForUpdatesAsync(parent, true);
            }
        });
        closeItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                NavigationManager.closeApplication(parent);
            }
        });

        logoutItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                SessionLogoutManager.logout(parent);
            }
        });




        statusMenu.add(syncNowItem);
        statusMenu.add(syncStatusItem);
        statusMenu.add(remoteQueueItem);
        statusMenu.add(checkUpdatesItem);

        sessionMenu.add(changeStoreItem);
        sessionMenu.add(changeEmployeePinItem);
        sessionMenu.add(sessionTimeClockItem);
        sessionMenu.addSeparator();
        sessionMenu.add(closeItem);
        sessionMenu.add(logoutItem);


        menuBar.add(pointOfSaleMenu);
        menuBar.add(operationsMenu);
        menuBar.add(ordersMenu);
        menuBar.add(inventoryMenu);
        menuBar.add(employeeMenu);
        menuBar.add(adminMenu);
        menuBar.add(statusMenu);
        menuBar.add(notificationsItem);
        menuBar.add(Box.createHorizontalGlue());
        if (EnvironmentProfile.active() == EnvironmentProfile.DEVELOPMENT) {
            JLabel developmentLabel = new JLabel("DEVELOPER / TEST");
            developmentLabel.setForeground(Color.WHITE);
            developmentLabel.setFont(developmentLabel.getFont().deriveFont(java.awt.Font.BOLD));
            developmentLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
            menuBar.add(developmentLabel);
            menuBar.add(Box.createHorizontalStrut(12));
        }
        menuBar.add(menuStatus);
        menuBar.add(Box.createHorizontalStrut(8));
        menuBar.add(sessionDateLabel);
        menuBar.add(sessionMenu);
        menuBar.add(Box.createHorizontalStrut(14));

        return menuBar;
    }

    /**
     * Creates the standard application menu bar with a screen-specific status
     * component. The component is retained if permissions later rebuild the menu.
     */
    public static JMenuBar create(JFrame parent, String currentScreen, JComponent statusComponent) {
        JMenuBar menuBar = create(parent, currentScreen);
        if (statusComponent instanceof LoadingStatePanel loadingStatePanel) {
            loadingStatePanel.attachToMenu(parent.getRootPane());
        }
        return menuBar;
    }

    static LoadingStatePanel loadingStatusFor(Component component) {
        JRootPane rootPane = SwingUtilities.getRootPane(component);
        if (rootPane == null) {
            return null;
        }
        Object status = rootPane.getClientProperty(STATUS_COMPONENT_PROPERTY);
        return status instanceof LoadingStatePanel loadingStatePanel ? loadingStatePanel : null;
    }

    public static void updateNotificationMenuLabel(JMenuBar menuBar, int unreadCount, int urgentCount) {
        JMenuItem item = findNotificationMenuItem(menuBar);
        if (item == null) {
            return;
        }
        item.setText(new NotificationService.NotificationSummary(unreadCount, urgentCount).label());
        item.setForeground(urgentCount > 0 ? NOTIFICATION_URGENT_COLOR : UIManager.getColor("MenuItem.foreground"));
    }

    private static JMenuItem findNotificationMenuItem(MenuElement element) {
        if (element instanceof JMenuItem item
                && Boolean.TRUE.equals(item.getClientProperty(NOTIFICATIONS_MENU_PROPERTY))) {
            return item;
        }
        for (MenuElement child : element.getSubElements()) {
            JMenuItem match = findNotificationMenuItem(child);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static String currentSessionMenuTitle() {
        String username = SessionManager.getCurrentUsername();
        String base = username == null || username.isBlank() ? "Session" : username.trim();
        if (DatabaseConfig.load().mode() == DatabaseMode.REMOTE_ADMIN
                && SessionManager.getCurrentLocationName() != null) {
            return base + " · " + SessionManager.getCurrentLocationName();
        }
        return base;
    }

    private static JLabel createSessionDateLabel() {
        JLabel label = new JLabel();
        label.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
        updateSessionDateLabel(label);

        Timer timer = new Timer(60_000, e -> updateSessionDateLabel(label));
        timer.setInitialDelay(60_000 - (int) (System.currentTimeMillis() % 60_000));
        label.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) {
                return;
            }
            if (label.isDisplayable()) {
                updateSessionDateLabel(label);
                timer.restart();
            } else {
                timer.stop();
            }
        });
        return label;
    }

    private static void updateSessionDateLabel(JLabel label) {
        label.setText(ZonedDateTime.now(currentSessionZone()).format(SESSION_CLOCK_FORMATTER));
    }

    private static ZoneId currentSessionZone() {
        String timezone = SessionManager.getCurrentLocationTimezone();
        if (timezone != null && !timezone.isBlank()) {
            try {
                return ZoneId.of(timezone.trim());
            } catch (Exception ignored) {
                // Fall back to the workstation zone if a stored location timezone is invalid.
            }
        }
        return ZoneId.systemDefault();
    }

    private static void runManualSync(Component parent, JMenuItem syncNowItem) {
        syncNowItem.setEnabled(false);
        syncNowItem.setText("Syncing...");
        SwingWorker<LanApiClient.SyncStatusSnapshot, Void> worker = new SwingWorker<>() {
            @Override
            protected LanApiClient.SyncStatusSnapshot doInBackground() throws Exception {
                return LanApiClient.runSyncNow();
            }

            @Override
            protected void done() {
                syncNowItem.setEnabled(true);
                syncNowItem.setText("Sync Now");
                try {
                    LanApiClient.SyncStatusSnapshot status = get();
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

    private static String formatSyncStatus(LanApiClient.SyncStatusSnapshot status) {
        String currentSync = status.lockRunning()
                ? "Running by " + status.lockOwner() + " since " + formatEpoch(status.lockAcquiredEpochMillis())
                : "Idle";
        String backgroundSync = nullToDash(status.serviceStatus())
                + " (" + nullToDash(status.serviceMessage()) + ")";
        return "Message: " + nullToDash(status.message())
                + "\nCloud reachable: " + (status.cloudReachable() ? "yes" : "no")
                + "\nBackground service: " + backgroundSync
                + "\nServer sync: " + (status.serverWorkerStarted() ? "running" : "not running")
                + "\nCurrent sync: " + currentSync
                + "\nLast pushed: " + status.lastPushed()
                + "\nPending events: " + status.pendingCount()
                + "\nFailed events: " + status.failedCount()
                + "\nOpen conflicts: " + status.conflictCount()
                + "\nLast success: " + formatEpoch(status.lastSuccessEpochMillis())
                + "\nLast error: " + nullToDash(status.lastError());
    }

    private static String formatInstant(Instant instant) {
        return instant == null ? "Never" : instant.toString();
    }

    private static String formatEpoch(long epochMillis) {
        return epochMillis <= 0 ? "Never" : Instant.ofEpochMilli(epochMillis).toString();
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
        if (DatabaseConfig.load().mode() == DatabaseMode.REMOTE_ADMIN) {
            ui.helpers.UiTaskRunner.submit(parent, "remote-admin.stores", LanApiClient::loadRemoteStores, stores -> {
                LanApiClient.RemoteStore selected = (LanApiClient.RemoteStore) JOptionPane.showInputDialog(parent,
                        "Select the store to manage. The current screen will reload in that store's scope.",
                        "Remote Admin Store", JOptionPane.PLAIN_MESSAGE, null, stores.toArray(),
                        stores.stream().filter(s -> s.locationId() == SessionManager.getCurrentLocationId()).findFirst().orElse(null));
                if (selected == null || selected.locationId() == SessionManager.getCurrentLocationId()) return;
                ui.helpers.UiTaskRunner.submit(parent, "remote-admin.switch-store",
                        () -> LanApiClient.switchRemoteStore(selected.locationId()), result -> {
                            LanApiClient.User user = result.user();
                            SessionManager.setCurrentLocationId(user.locationId());
                            SessionManager.setCurrentLocationName(user.locationName());
                            SessionManager.setCurrentLocationTimezone(user.locationTimezone());
                            SessionManager.setCurrentPermissions(result.permissions());
                            ui.helpers.SessionDataCache.clear();
                            refreshCurrentScreen(parent, currentScreen);
                        }, failure -> JOptionPane.showMessageDialog(parent,
                                "Store switch failed: " + failure.getMessage(), "Remote Admin", JOptionPane.ERROR_MESSAGE));
            }, failure -> JOptionPane.showMessageDialog(parent,
                    "Stores could not be loaded: " + failure.getMessage(), "Remote Admin", JOptionPane.ERROR_MESSAGE));
            return;
        }
        JOptionPane.showMessageDialog(parent,
                "This installation is assigned to " + SessionManager.getCurrentLocationName()
                        + ". An administrator can change the assignment from Device Management.",
                "Store Assignment", JOptionPane.INFORMATION_MESSAGE);
    }

    private static boolean isStoreLockedToConfiguredLocation() {
        DatabaseMode mode = DatabaseConfig.load().mode();
        return mode == DatabaseMode.SERVER || mode == DatabaseMode.CLIENT;
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

}
