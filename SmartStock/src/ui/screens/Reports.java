package ui.screens;

import utils.CurrencyFormatter;
import managers.SessionManager;
import managers.PermissionManager;
import services.ReportDataService;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.ThemeManager;
import ui.helpers.UiTaskRunner;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.knowm.xchart.*;
import org.knowm.xchart.internal.chartpart.Chart;
import org.knowm.xchart.CategorySeries.CategorySeriesRenderStyle;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.AxesChartStyler;

public class Reports extends JFrame {
    private static final DateTimeFormatter INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm a");
    private static final NumberFormat CURRENCY = CurrencyFormatter.create(Locale.US);

    private final JTextField fromField = new JTextField();
    private final JTextField toField = new JTextField();
    private final JLabel storeLabel = new JLabel();
    private final LoadingStatePanel loadingState = new LoadingStatePanel();
    private final JCheckBox allRevenueToggle = new JCheckBox("All Revenue");
    private final JPanel filterChips = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
    private final Set<Integer> selectedProducts = new LinkedHashSet<>();
    private final Set<Integer> selectedBrands = new LinkedHashSet<>();
    private final Set<Integer> selectedDepartments = new LinkedHashSet<>();
    private final Set<Integer> selectedItemTypes = new LinkedHashSet<>();
    private final Set<Integer> selectedEmployees = new LinkedHashSet<>();
    private final Set<String> selectedPayments = new LinkedHashSet<>();
    private ReportDataService.FilterOptions filterOptions;

    private final JPanel overviewRevenueChart = chartHolder();
    private final JPanel overviewCashChart = chartHolder();
    private final JPanel overviewDepartmentChart = chartHolder();
    private final JPanel overviewEmployeeChart = chartHolder();
    private final JPanel productChart = chartHolder();
    private final JPanel employeeChart = chartHolder();
    private final JPanel cashChart = chartHolder();
    private final JPanel expenseChart = chartHolder();
    private final JPanel balanceChart = chartHolder();
    private final JPanel overviewMetrics = new JPanel(new GridLayout(0, 4, 10, 10));

    private final DefaultTableModel productModel = readOnlyModel("Product", "Brand", "Department", "Item Type", "Units", "Returned", "Net Revenue", "Return Value", "Avg Price", "Est. Cost", "Est. Profit");
    private final DefaultTableModel employeeModel = readOnlyModel("Employee", "Transactions", "Items", "Gross", "Discounts", "Returns", "Net", "Average", "Share");
    private final DefaultTableModel cashModel = readOnlyModel("Time", "Direction", "Source", "Method", "Reference", "Amount");
    private final DefaultTableModel expenseModel = readOnlyModel("ID", "Date", "Category", "Payee", "Description", "Method", "Status", "Source", "Created By", "Amount");
    private final DefaultTableModel balanceModel = readOnlyModel("ID", "Period Start", "Period End", "Submitted", "Submitted By", "Balance B/F", "Income", "Expenses", "Payables", "Balance C/F");

    private final DefaultTableModel salesModel = readOnlyModel(
            "Sale ID", "Receipt", "Time", "Employee", "Device", "Drawer", "Payment", "Status", "Paid", "Total"
    );
    private final DefaultTableModel orderModel = readOnlyModel(
            "Payment ID", "Order #", "Time", "Customer", "Employee", "Device", "Drawer", "Method", "Amount", "Order Total", "Balance", "Status"
    );
    private final DefaultTableModel invoiceModel = readOnlyModel(
            "Order ID", "Order #", "Date", "Customer", "Status", "Payment", "Total", "Paid", "Balance", "Created By", "Device", "Drawer"
    );

    private final JLabel salesTransactionsLabel = metricLabel();
    private final JLabel salesGrossLabel = metricLabel();
    private final JLabel salesReturnsLabel = metricLabel();
    private final JLabel salesNetLabel = metricLabel();
    private final JLabel salesPaidLabel = metricLabel();
    private final JLabel salesUnpaidLabel = metricLabel();
    private final JLabel salesCashLabel = metricLabel();
    private final JLabel salesCardLabel = metricLabel();
    private final JLabel salesMmgLabel = metricLabel();
    private final JLabel salesAccountLabel = metricLabel();

    private final JLabel orderPaymentsLabel = metricLabel();
    private final JLabel orderTotalLabel = metricLabel();
    private final JLabel orderCollectedLabel = metricLabel();
    private final JLabel orderBalanceLabel = metricLabel();
    private final JLabel orderCashLabel = metricLabel();
    private final JLabel orderCardLabel = metricLabel();
    private final JLabel orderChequeLabel = metricLabel();
    private final JLabel orderMmgLabel = metricLabel();
    private final JLabel orderAccountLabel = metricLabel();
    private final JLabel orderReturnsLabel = metricLabel();

    private final JLabel invoiceCountLabel = metricLabel();
    private final JLabel invoiceTotalLabel = metricLabel();
    private final JLabel invoicePaidLabel = metricLabel();
    private final JLabel invoiceBalanceLabel = metricLabel();
    private final JLabel invoiceOpenLabel = metricLabel();
    private final JLabel invoiceDeliveredLabel = metricLabel();
    private final JLabel invoiceCashLabel = metricLabel();
    private final JLabel invoiceCardLabel = metricLabel();
    private final JLabel invoiceChequeLabel = metricLabel();
    private final JLabel invoiceMmgLabel = metricLabel();

    private ZoneId storeZone = resolveStoreZone();

    public Reports() {
        setTitle("Reports");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "Reports"));

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(18, 18, 18, 18));
        root.setBackground(new Color(245, 247, 250));

        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildTabs(), BorderLayout.CENTER);
        add(root, BorderLayout.CENTER);

        setDefaultRange();
        loadReports();
        WindowHelper.configurePosWindow(this);
    }

    private JPanel buildHeaderPanel() {
        JPanel headerPanel = new JPanel(new BorderLayout(0, 12));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Reports");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(31, 41, 55));
        updateStoreLabel();

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(titleLabel, BorderLayout.WEST);
        storeLabel.setForeground(new Color(75, 85, 99));
        titleRow.add(storeLabel, BorderLayout.EAST);

        JPanel filterPanel = new JPanel(new GridBagLayout());
        filterPanel.setBackground(Color.WHITE);
        filterPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1),
                new EmptyBorder(12, 12, 12, 12)
        ));

        JButton runButton = new JButton("Run Reports");
        JButton todayButton = new JButton("Today");
        JButton allTimeButton = new JButton("All Time");
        JButton presetsButton = new JButton("Date Preset");
        JButton productButton = new JButton("Products");
        JButton brandButton = new JButton("Brands");
        JButton departmentButton = new JButton("Departments");
        JButton typeButton = new JButton("Item Types");
        JButton employeeButton = new JButton("Employees");
        JButton paymentButton = new JButton("Payments");
        JButton clearButton = new JButton("Clear Filters");
        addFilter(filterPanel, 0, "From", fromField, 190);
        addFilter(filterPanel, 2, "To", toField, 190);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 4;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 10, 0, 0);
        filterPanel.add(runButton, gbc);
        gbc.gridx = 5;
        filterPanel.add(todayButton, gbc);
        gbc.gridx = 6;
        filterPanel.add(allTimeButton, gbc);
        JPanel advanced = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        advanced.setOpaque(false);
        advanced.add(presetsButton); advanced.add(productButton); advanced.add(brandButton);
        advanced.add(departmentButton); advanced.add(typeButton); advanced.add(employeeButton);
        advanced.add(paymentButton); advanced.add(clearButton); advanced.add(allRevenueToggle);
        advanced.add(loadingState);
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 6; gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(10, 0, 0, 0);
        filterPanel.add(advanced, gbc);
        filterChips.setOpaque(false);
        gbc.gridy = 2; filterPanel.add(filterChips, gbc);

        runButton.addActionListener(e -> loadReports());
        todayButton.addActionListener(e -> {
            setDefaultRange();
            loadReports();
        });
        allTimeButton.addActionListener(e -> setAllTimeRangeAndLoad());
        fromField.addActionListener(e -> loadReports());
        toField.addActionListener(e -> loadReports());
        allRevenueToggle.addActionListener(e -> loadReports());
        presetsButton.addActionListener(e -> showDatePresets(presetsButton));
        productButton.addActionListener(e -> selectOptions("Products", filterOptions == null ? List.of() : filterOptions.products(), selectedProducts));
        brandButton.addActionListener(e -> selectOptions("Brands", filterOptions == null ? List.of() : filterOptions.brands(), selectedBrands));
        departmentButton.addActionListener(e -> selectOptions("Departments", filterOptions == null ? List.of() : filterOptions.departments(), selectedDepartments));
        typeButton.addActionListener(e -> selectOptions("Item Types", filterOptions == null ? List.of() : filterOptions.itemTypes(), selectedItemTypes));
        employeeButton.addActionListener(e -> selectOptions("Employees", filterOptions == null ? List.of() : filterOptions.employees(), selectedEmployees));
        paymentButton.addActionListener(e -> selectStrings("Payment Methods", filterOptions == null ? List.of() : filterOptions.paymentMethods(), selectedPayments));
        clearButton.addActionListener(e -> clearAdvancedFilters());

        headerPanel.add(titleRow, BorderLayout.NORTH);
        headerPanel.add(filterPanel, BorderLayout.CENTER);
        return headerPanel;
    }

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Overview", buildOverviewTab());
        tabs.addTab("Sales", reportPanel(
                metricsPanel(salesTransactionsLabel, salesGrossLabel, salesReturnsLabel, salesNetLabel, salesPaidLabel,
                        salesUnpaidLabel, salesCashLabel, salesCardLabel, salesMmgLabel, salesAccountLabel),
                new JTable(salesModel)
        ));
        tabs.addTab("Products", chartTablePanel(productChart, new JTable(productModel)));
        tabs.addTab("Employees", chartTablePanel(employeeChart, new JTable(employeeModel)));
        tabs.addTab("Cash Flow", chartTablePanel(cashChart, new JTable(cashModel)));
        if (PermissionManager.hasPermission("BALANCE_SHEET")) {
            tabs.addTab("Expenses", expenseReportPanel());
            tabs.addTab("Balance C/F", chartTablePanel(balanceChart, new JTable(balanceModel)));
        }
        tabs.addTab("Orders", reportPanel(
                metricsPanel(orderPaymentsLabel, orderTotalLabel, orderCollectedLabel, orderBalanceLabel, orderCashLabel,
                        orderCardLabel, orderChequeLabel, orderMmgLabel, orderAccountLabel, orderReturnsLabel),
                new JTable(orderModel)
        ));
        tabs.addTab("Invoices", reportPanel(
                metricsPanel(invoiceCountLabel, invoiceTotalLabel, invoicePaidLabel, invoiceBalanceLabel,
                        invoiceOpenLabel, invoiceDeliveredLabel, invoiceCashLabel, invoiceCardLabel,
                        invoiceChequeLabel, invoiceMmgLabel),
                new JTable(invoiceModel)
        ));
        return tabs;
    }

    private JPanel buildOverviewTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        overviewMetrics.setOpaque(false);
        panel.add(overviewMetrics, BorderLayout.NORTH);
        JPanel charts = new JPanel(new GridLayout(2, 2, 10, 10));
        charts.setOpaque(false);
        charts.add(overviewRevenueChart); charts.add(overviewCashChart);
        charts.add(overviewDepartmentChart); charts.add(overviewEmployeeChart);
        panel.add(charts, BorderLayout.CENTER);
        return panel;
    }

    private JPanel chartTablePanel(JPanel chart, JTable table) {
        table.setRowHeight(27); table.setAutoCreateRowSorter(true);
        JPanel panel = new JPanel(new BorderLayout(10, 10)); panel.setOpaque(false);
        chart.setPreferredSize(new Dimension(600, 300));
        panel.add(chart, BorderLayout.NORTH); panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel expenseReportPanel() {
        JTable table = new JTable(expenseModel);
        table.setRowHeight(27);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(expenseModel);
        table.setRowSorter(sorter);
        JTextField category = new JTextField(10), payee = new JTextField(10), source = new JTextField(10), creator = new JTextField(10);
        JComboBox<String> status = new JComboBox<>(new String[]{"All Statuses", "PAID", "UNPAID"});
        JComboBox<String> method = new JComboBox<>(new String[]{"All Methods", "CASH", "CARD", "CHEQUE", "MMG", "ACCOUNT", "BANK"});
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 4));
        filters.add(new JLabel("Category")); filters.add(category); filters.add(new JLabel("Payee")); filters.add(payee);
        filters.add(new JLabel("Status")); filters.add(status); filters.add(new JLabel("Method")); filters.add(method);
        filters.add(new JLabel("Source")); filters.add(source); filters.add(new JLabel("Creator")); filters.add(creator);
        Runnable update = () -> {
            List<RowFilter<Object,Object>> fs = new ArrayList<>();
            addTextRowFilter(fs, category.getText(), 2); addTextRowFilter(fs, payee.getText(), 3);
            addTextRowFilter(fs, source.getText(), 7); addTextRowFilter(fs, creator.getText(), 8);
            if (status.getSelectedIndex() > 0) fs.add(RowFilter.regexFilter("^" + java.util.regex.Pattern.quote(status.getSelectedItem().toString()) + "$", 6));
            if (method.getSelectedIndex() > 0) fs.add(RowFilter.regexFilter("^" + java.util.regex.Pattern.quote(method.getSelectedItem().toString()) + "$", 5));
            sorter.setRowFilter(fs.isEmpty() ? null : RowFilter.andFilter(fs));
        };
        javax.swing.event.DocumentListener listener = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e){update.run();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){update.run();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){update.run();}
        };
        category.getDocument().addDocumentListener(listener); payee.getDocument().addDocumentListener(listener);
        source.getDocument().addDocumentListener(listener); creator.getDocument().addDocumentListener(listener);
        status.addActionListener(e -> update.run()); method.addActionListener(e -> update.run());
        JPanel panel = new JPanel(new BorderLayout(10,10)); panel.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false); top.add(expenseChart, BorderLayout.CENTER); top.add(filters, BorderLayout.SOUTH);
        top.setPreferredSize(new Dimension(600,350)); panel.add(top,BorderLayout.NORTH); panel.add(new JScrollPane(table),BorderLayout.CENTER);
        return panel;
    }

    private void addTextRowFilter(List<RowFilter<Object,Object>> filters, String value, int column) {
        if (value != null && !value.isBlank()) filters.add(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(value.trim()), column));
    }

    private JPanel reportPanel(JPanel metricsPanel, JTable table) {
        table.setRowHeight(27);
        table.setAutoCreateRowSorter(true);
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setOpaque(false);
        panel.add(metricsPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel metricsPanel(JLabel... labels) {
        JPanel panel = new JPanel(new GridLayout(2, 5, 10, 10));
        panel.setOpaque(false);
        for (JLabel label : labels) {
            panel.add(label);
        }
        return panel;
    }

    private void addFilter(JPanel panel, int x, String label, JComponent field, int width) {
        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = x;
        labelGbc.gridy = 0;
        labelGbc.insets = new Insets(0, 0, 0, 6);
        panel.add(new JLabel(label + ":"), labelGbc);

        field.setPreferredSize(new Dimension(width, 30));
        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.gridx = x + 1;
        fieldGbc.gridy = 0;
        fieldGbc.insets = new Insets(0, 0, 0, 8);
        panel.add(field, fieldGbc);
    }

    private void setDefaultRange() {
        storeZone = resolveStoreZone();
        LocalDate today = LocalDate.now(storeZone);
        fromField.setText(today.atStartOfDay().format(INPUT_FORMAT));
        toField.setText(today.plusDays(1).atStartOfDay().format(INPUT_FORMAT));
        updateStoreLabel();
    }

    private void loadReports() {
        ZonedDateTime from = parseDateTime(fromField.getText(), "From");
        ZonedDateTime to = parseDateTime(toField.getText(), "To");
        if (from == null || to == null) {
            return;
        }
        if (!to.isAfter(from)) {
            JOptionPane.showMessageDialog(this, "To must be after From.");
            return;
        }
        ReportDataService.Filters filters = new ReportDataService.Filters(
                from, to, storeZone, SessionManager.getCurrentLocationId(),
                Set.copyOf(selectedProducts), Set.copyOf(selectedBrands), Set.copyOf(selectedDepartments),
                Set.copyOf(selectedItemTypes), Set.copyOf(selectedEmployees), Set.copyOf(selectedPayments)
        );
        boolean allRevenue = allRevenueToggle.isSelected();
        boolean includeAccounting = PermissionManager.hasPermission("BALANCE_SHEET");
        String cacheKey = "reports:" + filters + ":allRevenue=" + allRevenue + ":accounting=" + includeAccounting;
        CachedUiLoader.load(this, "reports.load", cacheKey, ReportsScreenSnapshot.class, SessionDataCache.SCREEN_TTL,
                loadingState, () -> loadReportsSnapshot(filters, from, to, allRevenue, includeAccounting),
                this::applyReportsSnapshot);
    }

    private ReportsScreenSnapshot loadReportsSnapshot(ReportDataService.Filters filters,
                                                       ZonedDateTime from, ZonedDateTime to,
                                                       boolean allRevenue, boolean includeAccounting) {
        CompletableFuture<ReportDataService.Snapshot> analytics = async(
                () -> ReportDataService.load(filters, allRevenue, includeAccounting));
        CompletableFuture<LanApiClient.OrderReport> orders = async(() -> LanApiClient.loadOrderReport(from, to));
        CompletableFuture<LanApiClient.InvoiceReport> invoices = async(() -> LanApiClient.loadInvoiceReport(from, to));
        CompletableFuture<ReportDataService.FilterOptions> options = async(
                () -> ReportDataService.loadOptions(SessionManager.getCurrentLocationId()));
        CompletableFuture.allOf(analytics, orders, invoices, options).join();
        return new ReportsScreenSnapshot(analytics.join(), orders.join(), invoices.join(), options.join());
    }

    private void applyReportsSnapshot(ReportsScreenSnapshot snapshot) {
        filterOptions = snapshot.options();
        applySnapshot(snapshot.analytics());
        applyOrdersReport(snapshot.orders());
        applyInvoicesReport(snapshot.invoices());
    }

    private static <T> CompletableFuture<T> async(ThrowingSupplier<T> supplier) {
        return UiTaskRunner.supplyAsync(() -> {
            try { return supplier.get(); }
            catch (Exception ex) { throw new CompletionException(ex); }
        });
    }

    private void applySnapshot(ReportDataService.Snapshot s) {
        overviewMetrics.removeAll();
        s.metrics().forEach((name, value) -> {
            boolean count = name.equals("Transactions") || name.equals("Items Sold");
            JLabel label = metricLabel();
            label.setText("<html><span style='font-size:10px'>" + name + "</span><br><span style='font-size:16px'>" +
                    (count ? value.toBigInteger().toString() : CURRENCY.format(value)) + "</span></html>");
            overviewMetrics.add(label);
        });
        setSeriesChart(overviewRevenueChart, "Revenue Over Time", s.revenueSeries());
        setSeriesChart(overviewCashChart, "Actual Cash In vs Cash Out", s.cashSeries());
        setRankChart(overviewDepartmentChart, "Top Departments", s.departments());
        setRankChart(overviewEmployeeChart, "Top Employees", s.employees());
        setRankChart(productChart, "Top Products", s.products());
        setRankChart(employeeChart, "Employee Net Sales", s.employees());
        setSeriesChart(cashChart, "Cash Movement", s.cashSeries());
        setExpenseChart(s.expenses());
        setBalanceChart(s.balances());

        productModel.setRowCount(0);
        for (ReportDataService.ProductRow r : s.productRows()) productModel.addRow(new Object[]{
                r.product(), r.brand(), r.department(), r.itemType(), r.units(), r.returned(),
                CURRENCY.format(r.revenue()), CURRENCY.format(r.returnValue()), CURRENCY.format(r.averagePrice()),
                CURRENCY.format(r.cost()), CURRENCY.format(r.profit())
        });
        employeeModel.setRowCount(0);
        for (ReportDataService.EmployeeRow r : s.employeeRows()) employeeModel.addRow(new Object[]{
                r.employee(), r.transactions(), r.items(), CURRENCY.format(r.gross()), CURRENCY.format(r.discounts()),
                CURRENCY.format(r.returns()), CURRENCY.format(r.net()), CURRENCY.format(r.average()), r.share() + "%"
        });
        cashModel.setRowCount(0);
        for (ReportDataService.CashRow r : s.cashRows()) cashModel.addRow(new Object[]{
                r.time().format(DISPLAY_FORMAT), r.direction(), r.source(), r.method(), r.reference(), CURRENCY.format(r.amount())
        });
        expenseModel.setRowCount(0);
        for (ReportDataService.ExpenseRow r : s.expenses()) expenseModel.addRow(new Object[]{
                r.id(), r.date(), r.category(), r.payee(), r.description(), r.method(), r.status(), r.source(), r.creator(), CURRENCY.format(r.amount())
        });
        balanceModel.setRowCount(0);
        for (ReportDataService.BalanceRow r : s.balances()) balanceModel.addRow(new Object[]{
                r.id(), r.from(), r.to(), r.submittedAt().format(DISPLAY_FORMAT), r.submitter(),
                CURRENCY.format(r.bf()), CURRENCY.format(r.income()), CURRENCY.format(r.expenses()),
                CURRENCY.format(r.payables()), CURRENCY.format(r.cf())
        });
        salesModel.setRowCount(0);
        BigDecimal paid = BigDecimal.ZERO;
        Map<String, BigDecimal> paymentTotals = new java.util.HashMap<>();
        for (ReportDataService.SaleRow r : s.sales()) {
            paid = paid.add(r.paid());
            paymentTotals.merge(r.payment().toUpperCase(), r.paid(), BigDecimal::add);
            salesModel.addRow(new Object[]{r.id(), r.receipt(), r.time().format(DISPLAY_FORMAT), r.employee(), "", "",
                    r.payment(), r.status(), CURRENCY.format(r.paid()), CURRENCY.format(r.total())});
        }
        BigDecimal gross = s.metrics().getOrDefault("Gross Sales", BigDecimal.ZERO);
        salesTransactionsLabel.setText("Transactions: " + s.sales().size());
        salesGrossLabel.setText("Gross Sales: " + CURRENCY.format(gross));
        salesReturnsLabel.setText("Returns: " + CURRENCY.format(s.metrics().getOrDefault("Returns", BigDecimal.ZERO)));
        salesNetLabel.setText("Net Sales: " + CURRENCY.format(s.metrics().getOrDefault("Net Sales", BigDecimal.ZERO)));
        salesPaidLabel.setText("Paid: " + CURRENCY.format(paid));
        salesUnpaidLabel.setText("Unpaid: " + CURRENCY.format(gross.subtract(paid).max(BigDecimal.ZERO)));
        salesCashLabel.setText("Cash: " + CURRENCY.format(paymentTotals.getOrDefault("CASH", BigDecimal.ZERO)));
        salesCardLabel.setText("Card/Check: " + CURRENCY.format(paymentTotals.getOrDefault("CARD", BigDecimal.ZERO).add(paymentTotals.getOrDefault("CHEQUE", BigDecimal.ZERO))));
        salesMmgLabel.setText("MMG: " + CURRENCY.format(paymentTotals.getOrDefault("MMG", BigDecimal.ZERO)));
        salesAccountLabel.setText("Account: " + CURRENCY.format(paymentTotals.getOrDefault("ACCOUNT", BigDecimal.ZERO)));
        overviewMetrics.revalidate(); overviewMetrics.repaint();
    }

    private void setSeriesChart(JPanel holder, String title, List<ReportDataService.Series> series) {
        String yAxis = title.toLowerCase(Locale.ROOT).contains("cash") ? "Cash Amount" : "Net Sales";
        CategoryChart chart = new CategoryChartBuilder().width(700).height(300).title(title).xAxisTitle("Period").yAxisTitle(yAxis).build();
        chart.getStyler().setDefaultSeriesRenderStyle(CategorySeriesRenderStyle.Line);
        chart.getStyler().setYAxisDecimalPattern("$#,##0.00");
        styleChart(chart);
        for (ReportDataService.Series line : series) {
            List<String> labels = line.points().stream().map(ReportDataService.Point::label).toList();
            List<BigDecimal> values = line.points().stream().map(ReportDataService.Point::value).toList();
            if (!labels.isEmpty()) chart.addSeries(line.name(), labels, values);
        }
        replaceChart(holder, chart);
    }

    private void setRankChart(JPanel holder, String title, List<ReportDataService.Rank> ranks) {
        CategoryChart chart = new CategoryChartBuilder().width(700).height(300).title(title).xAxisTitle("").yAxisTitle("Net Sales").build();
        chart.getStyler().setYAxisDecimalPattern("$#,##0.00");
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setXAxisLabelRotation(ranks.size() > 5 ? 25 : 0);
        chart.getStyler().setAvailableSpaceFill(0.72);
        styleChart(chart);
        if (!ranks.isEmpty()) chart.addSeries("Net", ranks.stream().map(ReportDataService.Rank::label).toList(),
                ranks.stream().map(ReportDataService.Rank::amount).toList());
        replaceChart(holder, chart);
    }

    private void setExpenseChart(List<ReportDataService.ExpenseRow> rows) {
        Map<String, BigDecimal> grouped = new java.util.TreeMap<>();
        for (ReportDataService.ExpenseRow r : rows) grouped.merge(r.category(), r.amount(), BigDecimal::add);
        CategoryChart chart = new CategoryChartBuilder().width(700).height(300).title("Expenses by Category").yAxisTitle("Amount").build();
        chart.getStyler().setYAxisDecimalPattern("$#,##0.00");
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setXAxisLabelRotation(grouped.size() > 5 ? 25 : 0);
        styleChart(chart);
        if (!grouped.isEmpty()) chart.addSeries("Expenses", new ArrayList<>(grouped.keySet()), new ArrayList<>(grouped.values()));
        replaceChart(expenseChart, chart);
    }

    private void setBalanceChart(List<ReportDataService.BalanceRow> rows) {
        CategoryChart chart = new CategoryChartBuilder().width(700).height(300).title("Saved Balance C/F History").xAxisTitle("Period End").yAxisTitle("Balance C/F").build();
        chart.getStyler().setDefaultSeriesRenderStyle(CategorySeriesRenderStyle.Line);
        chart.getStyler().setYAxisDecimalPattern("$#,##0.00");
        chart.getStyler().setLegendVisible(false);
        styleChart(chart);
        if (!rows.isEmpty()) chart.addSeries("Balance C/F", rows.stream().map(r -> r.to().toString()).toList(), rows.stream().map(ReportDataService.BalanceRow::cf).toList());
        replaceChart(balanceChart, chart);
    }

    private void styleChart(Chart<?, ?> chart) {
        boolean dark = ThemeManager.isDarkModeEnabled();
        Color background = dark ? new Color(30,30,30) : Color.WHITE;
        Color plot = dark ? new Color(38,38,38) : new Color(248,250,252);
        Color text = dark ? new Color(238,238,238) : new Color(31,41,55);
        Color grid = dark ? new Color(105,105,105) : new Color(203,213,225);
        Styler styler = chart.getStyler();
        AxesChartStyler axes = (AxesChartStyler) styler;
        styler.setLegendPosition(Styler.LegendPosition.InsideNW);
        styler.setChartBackgroundColor(background);
        styler.setPlotBackgroundColor(plot);
        styler.setLegendBackgroundColor(plot);
        styler.setLegendBorderColor(grid);
        styler.setChartFontColor(text);
        styler.setXAxisTitleColor(text);
        styler.setYAxisTitleColor(text);
        axes.setAxisTickLabelsColor(text);
        axes.setAxisTickMarksColor(text);
        axes.setPlotGridLinesColor(grid);
        styler.setPlotBorderColor(grid);
        styler.setBaseFont(new Font("SansSerif", Font.PLAIN, 12));
        styler.setChartTitleFont(new Font("SansSerif", Font.BOLD, 14));
        axes.setAxisTitleFont(new Font("SansSerif", Font.BOLD, 12));
        axes.setAxisTickLabelsFont(new Font("SansSerif", Font.PLAIN, 11));
        styler.setLegendFont(new Font("SansSerif", Font.BOLD, 11));
        axes.setXAxisMaxLabelCount(12);
        axes.setYAxisTickMarkSpacingHint(45);
    }

    private void replaceChart(JPanel holder, Chart<?, ?> chart) {
        holder.removeAll(); holder.add(new XChartPanel<>(chart), BorderLayout.CENTER);
        holder.revalidate(); holder.repaint();
    }

    private void showDatePresets(Component anchor) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem allTime = new JMenuItem("All Time");
        allTime.addActionListener(e -> setAllTimeRangeAndLoad());
        menu.add(allTime); menu.addSeparator();
        addPreset(menu, "Today", 0); addPreset(menu, "Last 7 Days", 7);
        addPreset(menu, "Last 30 Days", 30); addPreset(menu, "Last 90 Days", 90);
        JMenuItem month = new JMenuItem("This Month");
        month.addActionListener(e -> {
            LocalDate today = LocalDate.now(storeZone);
            fromField.setText(today.withDayOfMonth(1).atStartOfDay().format(INPUT_FORMAT));
            toField.setText(today.plusMonths(1).withDayOfMonth(1).atStartOfDay().format(INPUT_FORMAT)); loadReports();
        });
        menu.add(month); menu.show(anchor, 0, anchor.getHeight());
    }

    private void setAllTimeRangeAndLoad() {
        fromField.setText(LocalDate.of(1970, 1, 1).atStartOfDay().format(INPUT_FORMAT));
        toField.setText(LocalDate.now(storeZone).plusDays(1).atStartOfDay().format(INPUT_FORMAT));
        loadReports();
    }

    private void addPreset(JPopupMenu menu, String name, int days) {
        JMenuItem item = new JMenuItem(name);
        item.addActionListener(e -> {
            LocalDate today = LocalDate.now(storeZone);
            LocalDate start = days == 0 ? today : today.minusDays(days - 1L);
            fromField.setText(start.atStartOfDay().format(INPUT_FORMAT));
            toField.setText(today.plusDays(1).atStartOfDay().format(INPUT_FORMAT)); loadReports();
        });
        menu.add(item);
    }

    private void selectOptions(String title, List<ReportDataService.Option> options, Set<Integer> selected) {
        JList<ReportDataService.Option> list = new JList<>(options.toArray(ReportDataService.Option[]::new));
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        int[] indexes = java.util.stream.IntStream.range(0, options.size()).filter(i -> selected.contains(options.get(i).id())).toArray();
        list.setSelectedIndices(indexes);
        if (JOptionPane.showConfirmDialog(this, new JScrollPane(list), title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            selected.clear(); list.getSelectedValuesList().forEach(x -> selected.add(x.id())); refreshFilterChips(); loadReports();
        }
    }

    private void selectStrings(String title, List<String> options, Set<String> selected) {
        JList<String> list = new JList<>(options.toArray(String[]::new));
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setSelectedIndices(java.util.stream.IntStream.range(0, options.size()).filter(i -> selected.contains(options.get(i))).toArray());
        if (JOptionPane.showConfirmDialog(this, new JScrollPane(list), title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            selected.clear(); selected.addAll(list.getSelectedValuesList()); refreshFilterChips(); loadReports();
        }
    }

    private void refreshFilterChips() {
        filterChips.removeAll();
        addChip("Products", selectedProducts.size()); addChip("Brands", selectedBrands.size());
        addChip("Departments", selectedDepartments.size()); addChip("Item Types", selectedItemTypes.size());
        addChip("Employees", selectedEmployees.size()); addChip("Payments", selectedPayments.size());
        filterChips.revalidate(); filterChips.repaint();
    }

    private void addChip(String name, int count) {
        if (count == 0) return;
        JLabel chip = new JLabel(name + ": " + count);
        chip.setOpaque(true); chip.setBackground(new Color(219,234,254)); chip.setForeground(new Color(30,64,175));
        chip.setBorder(new EmptyBorder(4,8,4,8)); filterChips.add(chip);
    }

    private void clearAdvancedFilters() {
        selectedProducts.clear(); selectedBrands.clear(); selectedDepartments.clear(); selectedItemTypes.clear();
        selectedEmployees.clear(); selectedPayments.clear(); refreshFilterChips(); loadReports();
    }

    private void applyOrdersReport(LanApiClient.OrderReport report) {
        orderModel.setRowCount(0);
        for(LanApiClient.OrderReportRow row:report.rows()){
                    orderModel.addRow(new Object[]{
                            row.paymentId(),row.orderNumber(),row.time().format(DISPLAY_FORMAT),row.customer(),row.employee(),row.device(),row.drawer(),row.method(),
                            CURRENCY.format(row.amount()),CURRENCY.format(row.total()),CURRENCY.format(row.balance()),row.status()
                    });
        }
        orderPaymentsLabel.setText("Payments: "+report.payments());orderTotalLabel.setText("Order Total: "+CURRENCY.format(report.total()));
        orderCollectedLabel.setText("Collected: "+CURRENCY.format(report.collected()));orderBalanceLabel.setText("Balance Due: "+CURRENCY.format(report.balance()));
        orderCashLabel.setText("Cash: "+CURRENCY.format(report.cash()));orderCardLabel.setText("Card: "+CURRENCY.format(report.card()));orderChequeLabel.setText("Cheque: "+CURRENCY.format(report.cheque()));
        orderMmgLabel.setText("MMG: "+CURRENCY.format(report.mmg()));orderAccountLabel.setText("Account: "+CURRENCY.format(report.account()));orderReturnsLabel.setText("Returns: "+CURRENCY.format(report.returns()));
    }

    private void applyInvoicesReport(LanApiClient.InvoiceReport report) {
        invoiceModel.setRowCount(0);
        for(LanApiClient.InvoiceReportRow row:report.rows()){
                    invoiceModel.addRow(new Object[]{
                            row.invoiceId(),row.invoiceNumber(),row.invoiceDate(),row.customer(),row.status(),row.paymentStatus(),CURRENCY.format(row.total()),
                            CURRENCY.format(row.paid()),CURRENCY.format(row.balance()),row.creator(),row.device(),row.drawer()
                    });
        }
        invoiceCountLabel.setText("Invoices: "+report.count());invoiceTotalLabel.setText("Invoice Total: "+CURRENCY.format(report.total()));invoicePaidLabel.setText("Paid: "+CURRENCY.format(report.paid()));
        invoiceBalanceLabel.setText("Balance Due: "+CURRENCY.format(report.balance()));invoiceOpenLabel.setText("Open: "+report.open());invoiceDeliveredLabel.setText("Delivered: "+report.delivered());
        invoiceCashLabel.setText("Cash: "+CURRENCY.format(report.cash()));invoiceCardLabel.setText("Card: "+CURRENCY.format(report.card()));invoiceChequeLabel.setText("Cheque: "+CURRENCY.format(report.cheque()));invoiceMmgLabel.setText("MMG: "+CURRENCY.format(report.mmg()));
    }

    private record ReportsScreenSnapshot(ReportDataService.Snapshot analytics,
                                         LanApiClient.OrderReport orders,
                                         LanApiClient.InvoiceReport invoices,
                                         ReportDataService.FilterOptions options) { }

    @FunctionalInterface
    private interface ThrowingSupplier<T> { T get() throws Exception; }

    private ZonedDateTime parseDateTime(String value, String label) {
        try {
            return LocalDateTime.parse(value, INPUT_FORMAT).atZone(storeZone);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, label + " must use YYYY-MM-DD HH:MM in this store timezone (" + storeZone + ").");
            return null;
        }
    }

    private void updateStoreLabel() {
        String storeName = SessionManager.getCurrentLocationName();
        Integer locationId = SessionManager.getCurrentLocationId();
        String storeText = locationId == null ? "Store: Not selected" : "Store: " + (storeName == null ? locationId : storeName);
        storeLabel.setText(storeText + "    Store Timezone: " + storeZone);
    }

    private ZoneId resolveStoreZone() {
        String timezone = SessionManager.getCurrentLocationTimezone();
        if (timezone != null && !timezone.isBlank()) {
            try {
                return ZoneId.of(timezone.trim());
            } catch (Exception ignored) {
            }
        }
        return ZoneId.systemDefault();
    }

    private void showReportError(String reportName, Exception ex) {
        JOptionPane.showMessageDialog(this, "Failed to load " + reportName + ": " + ex.getMessage(), "Reports", JOptionPane.ERROR_MESSAGE);
    }

    private static JLabel metricLabel() {
        JLabel label = new JLabel();
        label.setOpaque(true);
        boolean dark = ThemeManager.isDarkModeEnabled();
        label.setBackground(dark ? new Color(88, 88, 88) : Color.WHITE);
        label.setForeground(dark ? Color.WHITE : Color.BLACK);
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(dark ? new Color(115, 115, 115) : new Color(220, 224, 230), 1),
                new EmptyBorder(10, 10, 10, 10)
        ));
        label.setFont(new Font("SansSerif", Font.BOLD, 13));
        return label;
    }

    private static JPanel chartHolder() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createLineBorder(new Color(220, 224, 230)));
        return panel;
    }

    private static DefaultTableModel readOnlyModel(Object... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

}
