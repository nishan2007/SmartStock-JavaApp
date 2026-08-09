--
-- PostgreSQL database dump
--


-- Dumped from database version 18.3 (Homebrew)
-- Dumped by pg_dump version 18.3 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Data for Name: categories; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.categories (category_id, name, description, vat_rate_percent, created_at) VALUES (1, 'Custom', 'Default department for custom items', 0.00, '2026-08-09 13:36:57.19437-04');


--
-- Data for Name: company_info; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.company_info (company_info_id, company_name, company_motto_line1, company_motto_line2, company_logo_url, updated_at) VALUES (1, 'SmartStock', '', '', '', '2026-08-09 13:36:57.048811-04');


--
-- Data for Name: custom_order_design_placements; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (1, 'Line 1', 10, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (2, 'Line 2', 20, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (3, 'Line 3', 30, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (4, 'Top', 40, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (5, 'Middle', 50, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (6, 'Bottom', 60, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (7, 'Pocket', 70, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (8, 'Chest', 80, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (9, 'Left Chest', 90, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (10, 'Right Chest', 100, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (11, 'Front', 110, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (12, 'Back', 120, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (13, 'Left Sleeve', 130, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');
INSERT INTO public.custom_order_design_placements (design_placement_id, placement_name, sort_order, is_active, created_at, updated_at) VALUES (14, 'Right Sleeve', 140, true, '2026-08-09 13:36:58.102904-04', '2026-08-09 13:36:58.102904-04');


--
-- Data for Name: customer_types; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.customer_types (customer_type_id, name, description, is_active, created_at) VALUES (1, 'General', 'Default customer category', true, '2026-08-09 13:36:57.843435-04');


--
-- Data for Name: permissions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (52, 'MAINTENANCE_MANAGEMENT', 'Maintenance Management', 'Allows Maintenance Management.', 'Inventory', 'Maintenance', '2026-08-09 13:36:58.412553-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (53, 'MACHINE_MANAGEMENT', 'Machine List', 'Allows Machine Management.', 'Inventory', 'Maintenance', '2026-08-09 13:36:58.412553-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (23, 'CHANGE_SALE_ITEM_PRICE', 'Change Sale Item Price', 'Allows editing item unit prices during a sale without override.', 'Sales', 'Discounts', '2026-08-09 13:36:57.960095-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (64, 'QUOTATIONS_ORDERS', 'Quotations / Invoices', 'Allows Quotations Orders.', 'Quotations & Invoices', 'General', '2026-08-09 13:36:58.527729-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (65, 'CREATE_QUOTATION', 'Create Quotation', 'Allows Create Quotation.', 'Quotations & Invoices', 'General', '2026-08-09 13:36:58.527729-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (66, 'MANAGE_INVOICES', 'Manage Invoices', 'Allows Manage Invoices.', 'Quotations & Invoices', 'General', '2026-08-09 13:36:58.527729-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (67, 'POST_INVOICE_DELIVERY', 'Post Invoice Delivery', 'Allows Post Invoice Delivery.', 'Quotations & Invoices', 'General', '2026-08-09 13:36:58.527729-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (43, 'BALANCE_DRAWER', 'Balance Draw', 'Allows balancing drawer sessions, submitting counted cash totals, and receiving drawer-start notifications.', 'Operations', 'Cash Drawer', '2026-08-09 13:36:58.252859-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (33, 'CUSTOM_ORDER_OVERRIDES', 'Custom Order Overrides', 'Allows Custom Order Overrides.', 'Custom Orders', 'Approvals', '2026-08-09 13:36:58.181682-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (15, 'SERVER_RECOVERY', 'Server Recovery', 'Allows Server Recovery.', 'Administration', 'Devices', '2026-08-09 13:36:57.900122-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (72, 'APP_UPDATES', 'App Updates', 'Allows App Updates.', 'Operations', 'App Updates', '2026-08-09 13:36:58.638322-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (24, 'VIEW_SALE_AUDIT', 'View Sale Audit', 'Allows View Sale Audit.', 'General', NULL, '2026-08-09 13:36:57.980077-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (25, 'EXPORT_SALE_AUDIT', 'Export Sale Audit', 'Allows Export Sale Audit.', 'General', NULL, '2026-08-09 13:36:57.980899-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (22, 'APPLY_SALE_DISCOUNT', 'Apply Sale Discount', 'Allows applying line and sale-level discounts without manager override.', 'Sales', 'Discounts', '2026-08-09 13:36:57.959764-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (26, 'SALE_DISCOUNT_OVERRIDE', 'Sale Discount Override', 'Allows approving discount overrides above configured limits.', 'Sales', 'Overrides', '2026-08-09 13:36:57.9927-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (27, 'RETURN_OVERRIDE', 'Return Override', 'Allows approving return amount overrides above configured limits.', 'Sales', 'Overrides', '2026-08-09 13:36:57.99313-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (28, 'SALE_DISCOUNT_LIMIT_SETTINGS', 'Sale Discount Limit Settings', 'Allows changing sale discount approval thresholds in company settings.', 'Sales', 'Settings', '2026-08-09 13:36:57.993569-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (29, 'SALE_RETURN_APPROVAL_SETTINGS', 'Sale Return Approval Settings', 'Allows changing return approval thresholds in company settings.', 'Sales', 'Settings', '2026-08-09 13:36:57.993878-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (21, 'PROCESS_RETURNS', 'Process Returns', 'Allows creating and completing return transactions.', 'Sales', 'Returns', '2026-08-09 13:36:57.951578-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (51, 'END_OF_DAY', 'Sales Reports', 'Allows access to sales reporting totals.', 'Operations', 'Closeout', '2026-08-09 13:36:58.377175-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (46, 'TIME_CLOCK', 'Time Clock', 'Allows clock-in/clock-out actions.', 'People', 'Time Clock', '2026-08-09 13:36:58.361335-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (44, 'BALANCE_SHEET', 'Balance Sheet', 'Allows viewing balance sheet totals and logging business expenses or Other income.', 'Operations', 'Cash Drawer', '2026-08-09 13:36:58.301651-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (4, 'VIEW_MULTI_STORE_STOCK', 'View Multistore Stock', 'Allows viewing synchronized stock quantities from other stores.', 'Inventory', 'Item Visibility', '2026-08-09 13:36:57.533055-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (5, 'VIEW_MULTI_STORE_SALES', 'View Multistore Sales', 'Allows viewing synchronized sales and returns from other stores.', 'Point of Sale', 'Sales History', '2026-08-09 13:36:57.533055-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (6, 'PROCESS_MULTI_STORE_RETURNS', 'Process Multistore Returns', 'Allows paying and queuing returns for sales from another store.', 'Point of Sale', 'Returns', '2026-08-09 13:36:57.533055-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (68, 'INVENTORY_STOCK_NOTIFICATIONS', 'Inventory Stock Notifications', 'Allows receiving low-stock and out-of-stock notifications for inventory and custom-order items.', 'Inventory', 'Notifications', '2026-08-09 13:36:58.541103-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (69, 'CUSTOM_ORDER_WORK_NOTIFICATIONS', 'Custom Order Work Notifications', 'Allows receiving operational notifications for due, overdue, ready, unassigned, and balance-due custom orders.', 'Custom Orders', 'Notifications', '2026-08-09 13:36:58.541103-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (70, 'CUSTOM_ORDER_EXCEPTION_NOTIFICATIONS', 'Custom Order Exception Notifications', 'Allows receiving custom-order exception notifications such as recent refunds.', 'Custom Orders', 'Notifications', '2026-08-09 13:36:58.541103-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (71, 'SYNC_NOTIFICATIONS', 'Sync Notifications', 'Allows receiving sync health notifications for offline cloud, failed events, conflicts, and backlogs.', 'Operations', 'Sync', '2026-08-09 13:36:58.541103-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (54, 'PARTS_MANAGEMENT', 'Parts List', 'Allows managing maintenance parts and receiving maintenance part reorder notifications.', 'Inventory', 'Maintenance', '2026-08-09 13:36:58.412553-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (55, 'MAINTENANCE_TECHNICIAN', 'Maintenance Technician', 'Allows receiving open maintenance ticket notifications and working maintenance tickets.', 'Inventory', 'Maintenance', '2026-08-09 13:36:58.412877-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (14, 'DEVICE_MANAGEMENT', 'Device Management', 'Allows managing approved, pending, and blocked devices.', 'Administration', 'Devices', '2026-08-09 13:36:57.899351-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (60, 'RECEIVING_STOCK_OVERRIDE', 'Receiving Stock Override', 'Allows correcting counted shelf/storage stock during receiving with an audit trail.', 'Inventory', 'Receiving', '2026-08-09 13:36:58.416268-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (50, 'STORE_TRANSFER', 'Store Transfer', 'Allows sending and receiving inventory store transfers.', 'Inventory', 'Transfers', '2026-08-09 13:36:58.376027-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (1, 'VIEW_EMPLOYEE_SCHEDULE', 'View Employee Schedule', 'Allows viewing who is scheduled to work each day.', 'People', 'Scheduling', '2026-08-09 13:36:57.533055-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (7, 'EDIT_BALANCE_SHEET', 'Edit Submitted Balance Sheet', 'Allows revising the latest submitted Balance Sheet during its 48-hour edit window.', 'Operations', 'Cash Drawer', '2026-08-09 13:36:57.533055-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (34, 'ORDERS_MANAGER_DASHBOARD', 'Orders Manager Dashboard', 'Allows access to manager-level custom order dashboard tools.', 'Custom Orders', 'Management', '2026-08-09 13:36:58.182143-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (56, 'VIEW_COST_PRICE', 'View Cost Price', 'Allows viewing internal item cost prices.', 'Inventory', 'Sensitive Fields', '2026-08-09 13:36:58.41515-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (57, 'VIEW_VENDOR', 'View Vendor', 'Allows viewing vendor assignments on items.', 'Inventory', 'Sensitive Fields', '2026-08-09 13:36:58.415561-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (58, 'VIEW_CREATED_BY', 'View Created By', 'Allows viewing item creation and ownership metadata.', 'Inventory', 'Sensitive Fields', '2026-08-09 13:36:58.415839-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (59, 'MANUAL_ADJUSTMENT', 'Manual Adjustment', 'Allows manual quantity adjustments outside normal receiving/transfer flows.', 'Inventory', 'Adjustments', '2026-08-09 13:36:58.416058-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (12, 'DEPARTMENT_MANAGEMENT', 'Department Management', 'Allows creating and managing item departments.', 'Inventory', 'Setup', '2026-08-09 13:36:57.803978-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (13, 'VENDOR_MANAGEMENT', 'Vendor Management', 'Allows creating and managing vendors.', 'Inventory', 'Setup', '2026-08-09 13:36:57.816297-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (30, 'CREATE_CUSTOM_ORDER', 'Create Custom Order', 'Allows creating new custom orders.', 'Custom Orders', 'Order Access', '2026-08-09 13:36:58.138151-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (31, 'MANAGE_CUSTOM_ORDERS', 'Manage Custom Orders', 'Allows full management access across all custom orders.', 'Custom Orders', 'Order Access', '2026-08-09 13:36:58.138482-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (32, 'VIEW_ASSIGNED_CUSTOM_ORDERS', 'View Assigned Custom Orders', 'Allows viewing custom orders assigned to the logged-in user.', 'Custom Orders', 'Order Access', '2026-08-09 13:36:58.138733-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (35, 'ORDERS_END_OF_DAY', 'Order Reports', 'Allows access to custom-order reporting totals.', 'Custom Orders', 'Reports', '2026-08-09 13:36:58.183037-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (36, 'CUSTOM_ORDER_REFUNDS', 'Custom Order Refunds', 'Allows issuing refunds on custom orders.', 'Custom Orders', 'Refunds & Returns', '2026-08-09 13:36:58.183293-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (37, 'CUSTOM_ORDER_LINE_RETURNS', 'Custom Order Line Returns', 'Allows returning individual custom-order lines.', 'Custom Orders', 'Refunds & Returns', '2026-08-09 13:36:58.183543-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (38, 'CUSTOM_ORDER_LINE_DELIVERY', 'Custom Order Line Delivery', 'Allows marking custom-order lines as delivered.', 'Custom Orders', 'Workflow', '2026-08-09 13:36:58.183815-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (39, 'CUSTOM_ORDER_LINE_DISCOUNT', 'Custom Order Line Discount', 'Allows discounting custom-order lines without override.', 'Custom Orders', 'Pricing & Deposits', '2026-08-09 13:36:58.184063-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (40, 'CUSTOM_ORDER_DEPOSIT_OVERRIDE', 'Custom Order Deposit Override', 'Allows overriding required custom-order deposit amounts.', 'Custom Orders', 'Pricing & Deposits', '2026-08-09 13:36:58.184328-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (18, 'CUSTOM_ORDER_DEPOSIT_SETTINGS', 'Custom Order Deposit Settings', 'Allows editing custom-order minimum deposit settings.', 'Custom Orders', 'Settings', '2026-08-09 13:36:57.93883-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (61, 'CUSTOM_ORDER_REFUND_APPROVAL', 'Custom Order Refund Approval', 'Allows approving high-value custom-order refunds.', 'Custom Orders', 'Approvals', '2026-08-09 13:36:58.441216-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (41, 'CUSTOM_ORDER_CANCEL', 'Cancel Custom Orders', 'Allows canceling custom orders.', 'Custom Orders', 'Refunds & Returns', '2026-08-09 13:36:58.184713-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (19, 'CUSTOM_ORDER_REFUND_APPROVAL_SETTINGS', 'Custom Order Refund Approval Settings', 'Allows editing custom-order refund approval limits.', 'Custom Orders', 'Settings', '2026-08-09 13:36:57.939045-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (62, 'CUSTOM_ORDER_PRODUCTION_STEPS', 'Custom Order Production Steps', 'Allows updating production workflow states for custom-order lines.', 'Custom Orders', 'Workflow', '2026-08-09 13:36:58.441993-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (47, 'TIME_CLOCK_MANAGEMENT', 'Time Clock Management', 'Allows viewing and correcting staff time clock records.', 'People', 'Time Clock', '2026-08-09 13:36:58.361753-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (48, 'TIME_CLOCK_OVERRIDE', 'Time Clock Override', 'Allows approving additional employee time clock sessions after a completed session on the same day.', 'General', 'Time Clock', '2026-08-09 13:36:58.361994-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (49, 'PAYROLL_DASHBOARD', 'Payroll Dashboard', 'Allows viewing payroll and labor summary dashboards.', 'People', 'Payroll', '2026-08-09 13:36:58.362236-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (11, 'LOCATION_MANAGEMENT', 'Location Management', 'Allows creating and editing store locations.', 'Administration', 'Locations', '2026-08-09 13:36:57.798919-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (42, 'CASH_DRAWER_MANAGEMENT', 'Cash Drawer Management', 'Allows configuring cash drawer workflows and sessions.', 'Operations', 'Cash Drawer', '2026-08-09 13:36:58.252476-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (16, 'HARDWARE_SETUP', 'Hardware Setup', 'Allows configuring scanner, printer, and hardware integration settings.', 'Operations', 'Device & Hardware', '2026-08-09 13:36:57.901177-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (2, 'EDIT_EMPLOYEE_SCHEDULE', 'Edit Employee Schedule', 'Allows adding and removing employees from the weekly schedule.', 'People', 'Scheduling', '2026-08-09 13:36:57.533055-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (3, 'SCHEDULE_OTHER_STORES', 'Schedule Other Stores', 'Allows viewing and scheduling employees at stores other than the selected login store.', 'People', 'Scheduling', '2026-08-09 13:36:57.533055-04');
INSERT INTO public.permissions (permission_id, permission_key, permission_name, description, permission_group, permission_subgroup, created_at) VALUES (17, 'COMPANY_PREFERENCES', 'Company Preferences', 'Allows editing company-wide operational preferences.', 'Administration', 'Company Setup', '2026-08-09 13:36:57.938604-04');


--
-- Data for Name: roles; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.roles (role_id, role_name, description, created_at, updated_at) VALUES (1, 'ADMIN', 'Administrator', '2026-08-09 13:36:57.530285-04', '2026-08-09 13:36:57.530285-04');
INSERT INTO public.roles (role_id, role_name, description, created_at, updated_at) VALUES (2, 'MANAGER', 'Manager', '2026-08-09 13:36:57.530285-04', '2026-08-09 13:36:57.530285-04');
INSERT INTO public.roles (role_id, role_name, description, created_at, updated_at) VALUES (3, 'USER', 'User', '2026-08-09 13:36:57.530285-04', '2026-08-09 13:36:57.530285-04');


--
-- Data for Name: role_permissions; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 4, '2026-08-09 13:36:57.537097-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 5, '2026-08-09 13:36:57.537097-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 6, '2026-08-09 13:36:57.537097-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 1, '2026-08-09 13:36:57.542608-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 1, '2026-08-09 13:36:57.542608-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (3, 1, '2026-08-09 13:36:57.542608-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 7, '2026-08-09 13:36:57.543806-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 7, '2026-08-09 13:36:57.543806-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 3, '2026-08-09 13:36:57.544607-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 2, '2026-08-09 13:36:57.545091-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 2, '2026-08-09 13:36:57.545091-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 11, '2026-08-09 13:36:57.799363-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 11, '2026-08-09 13:36:57.799363-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 12, '2026-08-09 13:36:57.804321-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 12, '2026-08-09 13:36:57.804321-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 13, '2026-08-09 13:36:57.816703-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 13, '2026-08-09 13:36:57.816703-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 15, '2026-08-09 13:36:57.900478-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 16, '2026-08-09 13:36:57.901499-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 17, '2026-08-09 13:36:57.939258-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 18, '2026-08-09 13:36:57.939258-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 19, '2026-08-09 13:36:57.939258-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 17, '2026-08-09 13:36:57.941247-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 21, '2026-08-09 13:36:57.95206-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 21, '2026-08-09 13:36:57.95206-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 23, '2026-08-09 13:36:57.960439-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 22, '2026-08-09 13:36:57.960439-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 22, '2026-08-09 13:36:57.960439-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 23, '2026-08-09 13:36:57.960439-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 24, '2026-08-09 13:36:57.981489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 25, '2026-08-09 13:36:57.981489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 25, '2026-08-09 13:36:57.981489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 24, '2026-08-09 13:36:57.981489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 26, '2026-08-09 13:36:57.994235-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 26, '2026-08-09 13:36:57.994235-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 27, '2026-08-09 13:36:57.994235-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 27, '2026-08-09 13:36:57.994235-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 28, '2026-08-09 13:36:57.994235-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 28, '2026-08-09 13:36:57.994235-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 29, '2026-08-09 13:36:57.994235-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 29, '2026-08-09 13:36:57.994235-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 30, '2026-08-09 13:36:58.138966-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 31, '2026-08-09 13:36:58.138966-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 32, '2026-08-09 13:36:58.138966-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 30, '2026-08-09 13:36:58.138966-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 31, '2026-08-09 13:36:58.138966-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 32, '2026-08-09 13:36:58.138966-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (3, 30, '2026-08-09 13:36:58.139455-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (3, 32, '2026-08-09 13:36:58.139455-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 38, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 37, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 41, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 40, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 18, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 34, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 35, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 33, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 39, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 36, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 39, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 33, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 34, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 35, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 36, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 41, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 37, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 38, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 40, '2026-08-09 13:36:58.184993-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 42, '2026-08-09 13:36:58.253128-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 42, '2026-08-09 13:36:58.253128-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (3, 43, '2026-08-09 13:36:58.253611-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 43, '2026-08-09 13:36:58.253611-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 43, '2026-08-09 13:36:58.253611-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 44, '2026-08-09 13:36:58.303034-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 44, '2026-08-09 13:36:58.303034-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 46, '2026-08-09 13:36:58.362461-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 46, '2026-08-09 13:36:58.362461-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 47, '2026-08-09 13:36:58.362461-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 47, '2026-08-09 13:36:58.362461-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 48, '2026-08-09 13:36:58.362461-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 48, '2026-08-09 13:36:58.362461-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 49, '2026-08-09 13:36:58.362461-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 49, '2026-08-09 13:36:58.362461-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 50, '2026-08-09 13:36:58.376419-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 50, '2026-08-09 13:36:58.376419-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 51, '2026-08-09 13:36:58.377504-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 51, '2026-08-09 13:36:58.377504-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 52, '2026-08-09 13:36:58.413451-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 54, '2026-08-09 13:36:58.414006-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 53, '2026-08-09 13:36:58.414006-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 56, '2026-08-09 13:36:58.416489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 56, '2026-08-09 13:36:58.416489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 57, '2026-08-09 13:36:58.416489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 57, '2026-08-09 13:36:58.416489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 58, '2026-08-09 13:36:58.416489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 58, '2026-08-09 13:36:58.416489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 59, '2026-08-09 13:36:58.416489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 59, '2026-08-09 13:36:58.416489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 60, '2026-08-09 13:36:58.416489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 60, '2026-08-09 13:36:58.416489-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 19, '2026-08-09 13:36:58.442497-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 61, '2026-08-09 13:36:58.442497-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 61, '2026-08-09 13:36:58.442497-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 62, '2026-08-09 13:36:58.442497-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 62, '2026-08-09 13:36:58.442497-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 64, '2026-08-09 13:36:58.527971-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 65, '2026-08-09 13:36:58.527971-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 66, '2026-08-09 13:36:58.527971-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 67, '2026-08-09 13:36:58.527971-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 64, '2026-08-09 13:36:58.527971-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 65, '2026-08-09 13:36:58.527971-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 66, '2026-08-09 13:36:58.527971-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (2, 67, '2026-08-09 13:36:58.527971-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 69, '2026-08-09 13:36:58.542407-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 70, '2026-08-09 13:36:58.542407-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 71, '2026-08-09 13:36:58.542407-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 68, '2026-08-09 13:36:58.542407-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 72, '2026-08-09 13:36:58.638628-04');
INSERT INTO public.role_permissions (role_id, permission_id, updated_at) VALUES (1, 14, '2026-08-09 13:37:00.172485-04');


--
-- Data for Name: time_clock_auto_close_settings; Type: TABLE DATA; Schema: public; Owner: -
--

INSERT INTO public.time_clock_auto_close_settings (settings_id, auto_close_enabled, scheduled_detection_delay_hours, unscheduled_detection_hours, max_auto_work_hours, updated_by_user_id, updated_by_name, created_at, updated_at) VALUES ('8e56e4a5-742e-4f69-b819-2e853b850001', true, 4, 12, 8, NULL, 'System default', '2026-08-09 13:36:58.31549-04', '2026-08-09 13:36:58.31549-04');


--
-- Name: categories_category_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.categories_category_id_seq', 1, true);


--
-- Name: custom_order_design_placements_design_placement_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.custom_order_design_placements_design_placement_id_seq', 14, true);


--
-- Name: customer_types_customer_type_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.customer_types_customer_type_id_seq', 1, true);


--
-- Name: permissions_permission_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.permissions_permission_id_seq', 72, true);


--
-- Name: roles_role_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.roles_role_id_seq', 3, true);


--
-- PostgreSQL database dump complete
--
