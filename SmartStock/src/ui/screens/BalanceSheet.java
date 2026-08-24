package ui.screens;

import utils.CurrencyFormatter;
import services.BalanceSheetService;
import services.BalanceSheetService.ChequeDepositOption;
import services.BalanceSheetService.BankTransactionLine;
import services.BalanceSheetService.DrawSessionRange;
import services.BalanceSheetService.ExpenseEntry;
import services.BalanceSheetService.ExpenseOption;
import services.BalanceSheetService.OtherIncomeEntry;
import services.BalanceSheetService.OtherIncomeOption;
import services.BalanceSheetService.EditContext;
import services.BalanceSheetService.EditRequest;
import services.BalanceSheetService.EditResult;
import services.BalanceSheetService.EditableExpense;
import services.BalanceSheetService.EditableOtherIncome;
import services.BalanceSheetService.SheetLine;
import services.BalanceSheetService.SubmissionOption;
import services.BalanceSheetExportService;
import managers.PermissionManager;
import ui.components.AppMenuBar;
import ui.components.VendorSelector;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.ThemeManager;
import ui.helpers.ResponsiveTask;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class BalanceSheet extends JFrame {
    private static final int[] CF_DENOMINATIONS = {5000, 2000, 1000, 500, 100, 50, 20};
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm a");
    private static final NumberFormat CURRENCY = CurrencyFormatter.create(Locale.US);
    private static final Color LIGHT_BACKGROUND = new Color(245, 247, 250);
    private static final Color LIGHT_SURFACE = Color.WHITE;
    private static final Color LIGHT_TEXT = new Color(17, 24, 39);
    private static final Color LIGHT_BORDER = new Color(148, 163, 184);
    private static final Color DARK_BACKGROUND = new Color(18, 18, 18);
    private static final Color DARK_SURFACE = new Color(30, 30, 30);
    private static final Color DARK_TEXT = new Color(235, 235, 235);
    private static final Color DARK_BORDER = new Color(75, 85, 99);

    private final JTextField fromField = new JTextField(10);
    private final JTextField toField = new JTextField(10);
    private final JComboBox<SubmissionOption> savedSheetBox = new JComboBox<>();
    private final JLabel statusLabel = new JLabel("Current draft");
    private final JLabel balanceBfLabel = metricLabel("Balance BF: $0");
    private final JLabel cashInHandLabel = metricLabel("Cash In Hand: $0");
    private final JLabel balanceCfLabel = metricLabel("Balance CF: $0");
    private final JLabel netLabel = metricLabel("Surplus: $0");
    private final DefaultTableModel incomeModel = incomeTableModel();
    private final DefaultTableModel receivableModel = tableModel();
    private final DefaultTableModel expenseModel = tableModel();
    private final DefaultTableModel payableModel = tableModel();
    private final DefaultTableModel drawerCashModel = tableModel();
    private final DefaultTableModel deviceActivityModel = deviceActivityTableModel();
    private final DefaultTableModel pendingChequeModel = pendingChequeTableModel();
    private final DefaultTableModel bankTransactionModel = bankTransactionTableModel();
    private final DefaultTableModel drawerChecksModel = tableModel();
    private final JTable drawerChecksTable = new JTable(drawerChecksModel);
    private final JLabel drawerChecksStatusLabel = new JLabel("Checking drawer activity...");
    private final CardLayout drawerChecksLayout = new CardLayout();
    private final JPanel drawerChecksContent = new JPanel(drawerChecksLayout);
    private final DefaultTableModel cfCheckerModel = cfCheckerTableModel();
    private final JTable cfCheckerTable = new JTable(cfCheckerModel);
    private final JLabel cfExpectedLabel = metricLabel("Expected CF: $0");
    private final JLabel cfCountedLabel = metricLabel("Counted Cash: $0");
    private final JLabel cfVarianceLabel = metricLabel("Missing: $0");
    private services.BalanceSheetService.BalanceSheet currentSheet;
    private SwingWorker<services.BalanceSheetService.BalanceSheet, Void> sheetWorker;
    private SwingWorker<List<SubmissionOption>, Void> historyWorker;
    private SwingWorker<services.BalanceSheetService.BalanceSheet, Void> savedSheetWorker;
    private SwingWorker<List<DrawSessionRange>, Void> drawRangeWorker;
    private List<Long> matchedDrawerSessionIds = List.of();
    private BigDecimal cfExpectedTotal = BigDecimal.ZERO;
    private boolean updatingCfChecker;

    public BalanceSheet() {
        setTitle("Balance Sheet");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "BalanceSheet"));

        JPanel mainPanel = new JPanel(new BorderLayout(14, 14));
        mainPanel.setBorder(new EmptyBorder(18, 18, 18, 18));
        mainPanel.setBackground(backgroundColor());

        JLabel titleLabel = new JLabel("Balance Sheet");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(textColor());
        titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        LocalDate today = StoreTimeZoneHelper.today();
        fromField.setText(today.toString());
        toField.setText(today.toString());

        JButton refreshButton = new JButton("Refresh");
        JButton editBalanceBfButton = new JButton("Set Balance B/F");
        JButton exportButton = new JButton("Print / Export");
        JButton addExpenseButton = new JButton("Log Expense");
        JButton submitButton = new JButton("Submit Balance Sheet");
        JButton matchDrawButton = new JButton("Match Draw Session");
        JButton recoverLegacyCashButton = new JButton("Recover Legacy Cash");
        JButton openSavedButton = new JButton("Open Saved");
        JButton editSavedButton = new JButton("Edit Latest Submission");
        JButton auditButton = new JButton("Revision History");
        JButton addPayableHeaderButton = headerAddButton("Log account payable");
        JButton payPayableHeaderButton = headerActionButton("$", "Pay account payable");
        JButton deletePayableHeaderButton = headerActionButton("x", "Delete account payable row");
        JButton addExpenseHeaderButton = headerAddButton("Log expense");
        JButton deleteExpenseHeaderButton = headerActionButton("x", "Delete expense row");
        JButton addIncomeHeaderButton = headerAddButton("Log Other cash income");
        JButton deleteIncomeHeaderButton = headerActionButton("x", "Delete Other income row");
        JButton depositChequeHeaderButton = headerActionButton("$", "Mark cheque deposited in bank");
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filters.setOpaque(false);
        filters.add(label("From:"));
        filters.add(fromField);
        filters.add(label("To:"));
        filters.add(toField);
        if ("ADMIN".equalsIgnoreCase(PermissionManager.getCurrentRole())) {
            filters.add(editBalanceBfButton);
        }
        filters.add(matchDrawButton);
        if("ADMIN".equalsIgnoreCase(PermissionManager.getCurrentRole())&&PermissionManager.hasPermission("BALANCE_SHEET")&&PermissionManager.hasPermission("BALANCE_DRAWER"))filters.add(recoverLegacyCashButton);
        filters.add(submitButton);
        filters.add(addExpenseButton);
        filters.add(exportButton);
        filters.add(refreshButton);

        savedSheetBox.setPreferredSize(new Dimension(310, 30));
        JPanel savedPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        savedPanel.setOpaque(false);
        savedPanel.add(label("Previous:"));
        savedPanel.add(savedSheetBox);
        savedPanel.add(openSavedButton);
        if (PermissionManager.hasPermission("EDIT_BALANCE_SHEET")) savedPanel.add(editSavedButton);
        savedPanel.add(auditButton);

        JPanel header = new JPanel(new BorderLayout(12, 12));
        header.setOpaque(false);
        header.add(titleLabel, BorderLayout.WEST);
        header.add(filters, BorderLayout.EAST);
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        statusLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        statusLabel.setForeground(textColor());

        JPanel headerStack = new JPanel(new BorderLayout(0, 8));
        headerStack.setOpaque(false);
        headerStack.add(header, BorderLayout.NORTH);
        headerStack.add(statusLabel, BorderLayout.WEST);
        headerStack.add(savedPanel, BorderLayout.EAST);

        JPanel metrics = new JPanel(new GridLayout(1, 4, 10, 0));
        metrics.setOpaque(false);
        metrics.add(balanceBfLabel);
        metrics.add(cashInHandLabel);
        metrics.add(balanceCfLabel);
        metrics.add(netLabel);

        JPanel top = new JPanel(new BorderLayout(0, 12));
        top.setOpaque(false);
        top.add(headerStack, BorderLayout.NORTH);
        top.add(metrics, BorderLayout.SOUTH);

        JPanel grid = new JPanel(new GridLayout(1, 4, 12, 0));
        grid.setOpaque(false);
        grid.add(column(
                section("Income", incomeTable(), new Color(219, 234, 254), addIncomeHeaderButton, deleteIncomeHeaderButton),
                section("Drawer Cash In Hand", new JTable(drawerCashModel), new Color(226, 232, 240)),
                section("Device Sales", new JTable(deviceActivityModel), new Color(224, 231, 255))
        ));
        grid.add(column(
                section("Accounts Receivable", new JTable(receivableModel), new Color(220, 252, 231)),
                section("Accounts Payable", new JTable(payableModel), new Color(255, 237, 213), addPayableHeaderButton, payPayableHeaderButton, deletePayableHeaderButton),
                section("Expenses", new JTable(expenseModel), new Color(254, 226, 226), addExpenseHeaderButton, deleteExpenseHeaderButton)
        ));
        grid.add(reconciliationColumn(
                buildDrawerChecksSection(),
                buildCfCheckerSection()
        ));
        grid.add(column(
                section("Bank Transactions", new JTable(bankTransactionModel), new Color(224, 242, 254)),
                section("Cheques To Deposit", new JTable(pendingChequeModel), new Color(219, 234, 254), depositChequeHeaderButton)
        ));

        mainPanel.add(top, BorderLayout.NORTH);
        mainPanel.add(grid, BorderLayout.CENTER);
        add(mainPanel, BorderLayout.CENTER);

        refreshButton.addActionListener(e -> loadSheet());
        editBalanceBfButton.addActionListener(e -> showBalanceBfDialog());
        exportButton.addActionListener(e -> printOrExportSheet());
        submitButton.addActionListener(e -> submitSheet());
        matchDrawButton.addActionListener(e -> matchDrawSessionRange(true));
        recoverLegacyCashButton.addActionListener(e -> showLegacyCashRecoveryDialog());
        openSavedButton.addActionListener(e -> loadSelectedSubmission());
        editSavedButton.addActionListener(e -> editSelectedSubmission());
        auditButton.addActionListener(e -> showSelectedRevisionHistory());
        addExpenseButton.addActionListener(e -> showExpenseDialog());
        addPayableHeaderButton.addActionListener(e -> showPayableDialog());
        payPayableHeaderButton.addActionListener(e -> showPayablePaymentDialog());
        deletePayableHeaderButton.addActionListener(e -> showDeleteExpenseDialog("UNPAID", "Delete Account Payable"));
        addExpenseHeaderButton.addActionListener(e -> showExpenseDialog());
        deleteExpenseHeaderButton.addActionListener(e -> showDeleteExpenseDialog("PAID", "Delete Expense"));
        addIncomeHeaderButton.addActionListener(e -> showOtherIncomeDialog());
        deleteIncomeHeaderButton.addActionListener(e -> showDeleteOtherIncomeDialog());
        depositChequeHeaderButton.addActionListener(e -> showDepositChequeDialog());
        cfCheckerModel.addTableModelListener(e -> {
            if (!updatingCfChecker && e.getType() == TableModelEvent.UPDATE && e.getColumn() == 1) {
                recalculateCfChecker();
            }
        });

        WindowHelper.configurePosWindow(this);
        ThemeManager.applyToWindow(this);
        showLoadingState("Loading balance sheet...");
        SwingUtilities.invokeLater(() -> {
            applyContinuingDrawSessionRange();
            loadSheet();
            loadSubmissionHistory();
        });
    }

    private void showLegacyCashRecoveryDialog(){
        JComboBox<String> source=new JComboBox<>(new String[]{"SALES","CUSTOM_ORDER_PAYMENT","INVOICE_PAYMENT","CUSTOMER_ACCOUNT_TRANSACTION"});
        JTextField id=new JTextField(14),reason=new JTextField(30);JPanel form=new JPanel(new GridLayout(0,2,8,8));
        form.add(new JLabel("Source table:"));form.add(source);form.add(new JLabel("Primary ID:"));form.add(id);
        form.add(new JLabel("Audit reason:"));form.add(reason);
        int answer=JOptionPane.showConfirmDialog(this,form,"Attach NULL-session legacy cash to this register's open drawer",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE);
        if(answer!=JOptionPane.OK_OPTION)return;
        long sourceId;try{sourceId=Long.parseLong(id.getText().trim());}catch(Exception ex){JOptionPane.showMessageDialog(this,"Enter a valid primary ID.","Recovery",JOptionPane.ERROR_MESSAGE);return;}
        String why=reason.getText().trim();if(why.length()<10){JOptionPane.showMessageDialog(this,"Enter an audit reason of at least 10 characters.","Recovery",JOptionPane.ERROR_MESSAGE);return;}
        String key=UUID.randomUUID().toString(),sourceType=(String)source.getSelectedItem();
        new SwingWorker<services.BalanceSheetService.LegacyCashRecovery,Void>(){
            protected services.BalanceSheetService.LegacyCashRecovery doInBackground()throws Exception{return BalanceSheetService.recoverLegacyCash(sourceType,sourceId,why,key);}
            protected void done(){try{var r=get();JOptionPane.showMessageDialog(BalanceSheet.this,"Attached "+r.sourceType()+" #"+r.sourceId()+" to open session "+r.afterSessionId()+" ("+r.drawerName()+"). The session remains open.","Recovery complete",JOptionPane.INFORMATION_MESSAGE);loadSheet();}catch(Exception ex){JOptionPane.showMessageDialog(BalanceSheet.this,"Recovery was not applied: "+rootMessage(ex),"Recovery",JOptionPane.ERROR_MESSAGE);}}
        }.execute();
    }

    private static String rootMessage(Throwable value){Throwable current=value;while(current.getCause()!=null)current=current.getCause();return current.getMessage()==null?current.toString():current.getMessage();}

    private void printOrExportSheet() {
        if (currentSheet == null) {
            JOptionPane.showMessageDialog(this, "Load a balance sheet before printing or exporting it.", "Balance Sheet", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Object[] formats = {"PDF", "PNG Image", "Print", "Cancel"};
        int choice = JOptionPane.showOptionDialog(this, "Print or export the complete balance sheet.", "Print / Export Balance Sheet",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, formats, formats[0]);
        if (choice < 0 || choice == 3) return;
        if (choice == 2) {
            try { BalanceSheetExportService.print(this, currentSheet); }
            catch (Exception ex) { JOptionPane.showMessageDialog(this, "Failed to print balance sheet: " + ex.getMessage(), "Print Error", JOptionPane.ERROR_MESSAGE); }
            return;
        }
        boolean pdf = choice == 0; String extension = pdf ? "pdf" : "png";
        LocalDate from = currentSheet.periodStart() == null ? parseDate(fromField.getText().trim(), "From") : currentSheet.periodStart();
        LocalDate to = currentSheet.periodEnd() == null ? parseDate(toField.getText().trim(), "To") : currentSheet.periodEnd();
        if (from == null || to == null) return;
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("Save Balance Sheet " + (pdf ? "PDF" : "Image"));
        chooser.setSelectedFile(new File("smartstock-balance-sheet-" + from + "-to-" + to + "." + extension));
        chooser.setFileFilter(new FileNameExtensionFilter(pdf ? "PDF documents (*.pdf)" : "PNG images (*.png)", extension));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File output = chooser.getSelectedFile();
        if (!output.getName().toLowerCase(Locale.ROOT).endsWith("." + extension)) output = new File(output.getParentFile(), output.getName() + "." + extension);
        if (output.exists() && JOptionPane.showConfirmDialog(this, "Replace the existing file?\n" + output.getAbsolutePath(), "Replace Export", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
        File exportOutput = output; services.BalanceSheetService.BalanceSheet sheet = currentSheet;
        try {
            ResponsiveTask.await(this, "Exporting balance sheet...", () -> { if (pdf) BalanceSheetExportService.writePdf(exportOutput, sheet); else BalanceSheetExportService.writePng(exportOutput, sheet); return exportOutput; });
            JOptionPane.showMessageDialog(this, "Balance sheet exported successfully.\n" + output.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Failed to export balance sheet: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE); }
    }

    private JPanel section(String title, JTable table, Color headerColor) {
        return section(title, table, headerColor, new JButton[0]);
    }

    private JPanel section(String title, JTable table, Color headerColor, JButton... actionButtons) {
        table.setRowHeight(26);
        table.setFillsViewportHeight(true);
        if (table.getColumnModel().getColumnCount() == 2) {
            table.getColumnModel().getColumn(0).setPreferredWidth(240);
            table.getColumnModel().getColumn(1).setPreferredWidth(90);
        } else {
            table.getColumnModel().getColumn(0).setPreferredWidth(80);
            for (int column = 1; column < table.getColumnModel().getColumnCount(); column++) {
                table.getColumnModel().getColumn(column).setPreferredWidth(95);
            }
        }
        configureTable(table);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleLabel.setOpaque(true);
        titleLabel.setBackground(headerColor);
        titleLabel.setForeground(new Color(17, 24, 39));
        titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        titleLabel.setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(true);
        header.setBackground(headerColor);
        header.add(titleLabel, BorderLayout.WEST);
        if (actionButtons != null && actionButtons.length > 0) {
            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
            actions.setOpaque(false);
            for (JButton button : actionButtons) {
                if (button != null) {
                    actions.add(button);
                }
            }
            header.add(actions, BorderLayout.EAST);
        }

        JPanel panel = new JPanel(new BorderLayout());
        panel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        panel.setBackground(surfaceColor());
        panel.setBorder(BorderFactory.createLineBorder(borderColor()));
        panel.add(header, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JButton headerAddButton(String tooltip) {
        return headerActionButton("+", tooltip);
    }

    private JButton headerActionButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setMargin(new Insets(0, 8, 0, 8));
        button.setFocusable(false);
        button.setToolTipText(tooltip);
        return button;
    }

    private JPanel column(Component... sections) {
        JPanel panel = new JPanel(new GridLayout(sections.length, 1, 0, 12));
        panel.setOpaque(false);
        for (Component section : sections) {
            panel.add(section);
        }
        return panel;
    }

    private JPanel reconciliationColumn(Component drawerChecks, Component cfChecker) {
        JPanel panel = new JPanel(new BorderLayout(0, 12));
        panel.setOpaque(false);
        panel.add(drawerChecks, BorderLayout.NORTH);
        panel.add(cfChecker, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildDrawerChecksSection() {
        drawerChecksTable.setRowHeight(26);
        drawerChecksTable.setFillsViewportHeight(true);
        drawerChecksTable.getColumnModel().getColumn(0).setPreferredWidth(240);
        drawerChecksTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        configureTable(drawerChecksTable);

        drawerChecksStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        drawerChecksStatusLabel.setBorder(new EmptyBorder(14, 14, 14, 14));
        drawerChecksStatusLabel.setForeground(textColor());
        drawerChecksStatusLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);

        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        statusPanel.setBackground(surfaceColor());
        statusPanel.add(drawerChecksStatusLabel, BorderLayout.NORTH);

        drawerChecksContent.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        drawerChecksContent.setBackground(surfaceColor());
        drawerChecksContent.add(statusPanel, "status");
        drawerChecksContent.add(new JScrollPane(drawerChecksTable), "details");

        JLabel titleLabel = new JLabel("Drawer Match Checks");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleLabel.setOpaque(true);
        titleLabel.setBackground(new Color(254, 249, 195));
        titleLabel.setForeground(new Color(17, 24, 39));
        titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        titleLabel.setBorder(new EmptyBorder(8, 10, 8, 10));

        JPanel panel = new JPanel(new BorderLayout());
        panel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        panel.setBackground(surfaceColor());
        panel.setBorder(BorderFactory.createLineBorder(borderColor()));
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(drawerChecksContent, BorderLayout.CENTER);
        drawerChecksContent.setPreferredSize(new Dimension(1, 52));
        drawerChecksLayout.show(drawerChecksContent, "status");
        return panel;
    }

    private JPanel buildCfCheckerSection() {
        resetCfChecker();
        cfCheckerTable.setRowHeight(28);
        cfCheckerTable.setFillsViewportHeight(false);
        cfCheckerTable.getTableHeader().setReorderingAllowed(false);
        cfCheckerTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        cfCheckerTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        cfCheckerTable.getColumnModel().getColumn(2).setPreferredWidth(95);
        cfCheckerTable.setDefaultRenderer(Object.class, new CfCheckerRenderer());
        cfCheckerTable.setDefaultRenderer(Integer.class, new CfCheckerRenderer());
        configureTable(cfCheckerTable);
        int tableHeight = cfCheckerTable.getTableHeader().getPreferredSize().height
                + cfCheckerTable.getRowHeight() * cfCheckerModel.getRowCount();
        cfCheckerTable.setPreferredSize(new Dimension(1, cfCheckerTable.getRowHeight() * cfCheckerModel.getRowCount()));

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        tablePanel.setBackground(surfaceColor());
        tablePanel.add(cfCheckerTable.getTableHeader(), BorderLayout.NORTH);
        tablePanel.add(cfCheckerTable, BorderLayout.CENTER);

        JPanel summary = new JPanel(new GridLayout(3, 1, 0, 8));
        summary.setOpaque(false);
        summary.setBorder(new EmptyBorder(10, 10, 10, 10));
        summary.add(cfExpectedLabel);
        summary.add(cfCountedLabel);
        summary.add(cfVarianceLabel);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        panel.setBackground(surfaceColor());
        panel.setBorder(BorderFactory.createLineBorder(borderColor()));

        JLabel titleLabel = new JLabel("CF Checker");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        titleLabel.setOpaque(true);
        titleLabel.setBackground(new Color(187, 247, 208));
        titleLabel.setForeground(new Color(17, 24, 39));
        titleLabel.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        titleLabel.setBorder(new EmptyBorder(8, 10, 8, 10));

        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(tablePanel, BorderLayout.CENTER);
        panel.add(summary, BorderLayout.SOUTH);
        int fixedHeight = titleLabel.getPreferredSize().height
                + tableHeight
                + summary.getPreferredSize().height
                + 22;
        Dimension fixedSize = new Dimension(280, fixedHeight);
        panel.setPreferredSize(fixedSize);
        panel.setMinimumSize(fixedSize);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, fixedHeight));
        return panel;
    }

    private JTable incomeTable() {
        JTable table = new JTable(incomeModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(80);
        for (int column = 1; column < table.getColumnModel().getColumnCount(); column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(95);
        }
        return table;
    }

    private void loadSheet() {
        LocalDate from = parseDate(fromField.getText().trim(), "From");
        LocalDate to = parseDate(toField.getText().trim(), "To");
        if (from == null || to == null) {
            return;
        }
        if (to.isBefore(from)) {
            JOptionPane.showMessageDialog(this, "To date must be on or after From date.", "Balance Sheet", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (sheetWorker != null && !sheetWorker.isDone()) {
            sheetWorker.cancel(true);
        }
        String storeZoneId = StoreTimeZoneHelper.getStoreZoneId();
        List<Long> cashDrawerSessionIds = List.copyOf(matchedDrawerSessionIds);
        showLoadingState("Loading balance sheet...");
        sheetWorker = new SwingWorker<>() {
            @Override
            protected services.BalanceSheetService.BalanceSheet doInBackground() throws Exception {
                return BalanceSheetService.loadBalanceSheet(from, to, storeZoneId, cashDrawerSessionIds);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    currentSheet = get();
                    renderSheet(currentSheet);
                    String sessionText = cashDrawerSessionIds.isEmpty() ? "" : " filtered to " + cashDrawerSessionIds.size() + " draw session(s)";
                    statusLabel.setText("Current draft for " + from + " to " + to + sessionText);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(BalanceSheet.this, "Failed to load balance sheet: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        sheetWorker.execute();
    }

    private void renderSheet(services.BalanceSheetService.BalanceSheet sheet) {
        fillIncome(sheet.income(), sheet.totalIncome());
        fill(receivableModel, sheet.receivables(), sheet.totalReceivables());
        fill(expenseModel, sheet.expenses(), sheet.totalExpenses());
        fill(payableModel, sheet.payables(), sheet.totalPayables());
        fill(drawerCashModel, sheet.drawerCash(), sheet.cashInHand());
        fillDeviceActivity(sheet.deviceSales(), sheet.deviceOrders(), sheet.devicePayments());
        fillBankTransactions(sheet.bankTransactions());
        fillPendingCheques(sheet.pendingCheques());
        renderDrawerChecks(sheet.drawerChecks());
        balanceBfLabel.setText("Balance BF: " + money(sheet.balanceBf()));
        cashInHandLabel.setText("Cash In Hand: " + money(sheet.cashInHand()));
        balanceCfLabel.setText("Balance CF: " + money(sheet.balanceCf()));
        BigDecimal net = sheet.totalIncome().subtract(sheet.totalExpenses()).subtract(sheet.totalPayables());
        netLabel.setText((net.compareTo(BigDecimal.ZERO) < 0 ? "Deficit: " : "Surplus: ") + money(net.abs()));
        cfExpectedTotal = defaultZero(sheet.balanceCf());
        recalculateCfChecker();
    }

    private void showBalanceBfDialog() {
        if (!"ADMIN".equalsIgnoreCase(PermissionManager.getCurrentRole())) {
            JOptionPane.showMessageDialog(this, "Only an administrator can set or edit Balance B/F.",
                    "Access Denied", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (currentSheet != null && currentSheet.submissionId() != null) {
            JOptionPane.showMessageDialog(this,
                    "Saved balance sheets are read-only. Refresh the screen to edit the current period's Balance B/F.",
                    "Balance B/F", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        LocalDate periodStart = parseDate(fromField.getText().trim(), "From");
        if (periodStart == null) return;

        JTextField amountField = new JTextField(
                currentSheet == null ? "0.00" : defaultZero(currentSheet.balanceBf()).toPlainString(), 14);
        JPanel panel = new JPanel(new GridLayout(0, 1, 0, 6));
        panel.add(new JLabel("Balance B/F for the period starting " + periodStart + ":"));
        panel.add(amountField);
        panel.add(new JLabel("This overrides the automatically carried-forward balance for this start date."));
        int result = JOptionPane.showConfirmDialog(this, panel, "Set Balance B/F",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        BigDecimal amount;
        try {
            amount = new BigDecimal(amountField.getText().trim()).setScale(2);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Enter a valid amount with no more than 2 decimal places.",
                    "Balance B/F", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            BigDecimal savedAmount = amount;
            Boolean updated = ResponsiveTask.await(this, "Saving Balance B/F...", () -> {
                BalanceSheetService.setBalanceBf(periodStart, savedAmount);
                return Boolean.TRUE;
            });
            if (updated == null) return;
            loadSheet();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save Balance B/F: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void submitSheet() {
        LocalDate from = parseDate(fromField.getText().trim(), "From");
        LocalDate to = parseDate(toField.getText().trim(), "To");
        if (from == null || to == null || to.isBefore(from)) {
            return;
        }

        JTextArea notesArea = new JTextArea(3, 28);
        int confirm = JOptionPane.showConfirmDialog(
                this,
                new JScrollPane(notesArea),
                "Submit Balance Sheet",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        );
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            String storeZoneId = StoreTimeZoneHelper.getStoreZoneId();
            String notes = notesArea.getText();
            List<Long> drawerSessionIds = List.copyOf(matchedDrawerSessionIds);
            SubmissionResult submitted = ResponsiveTask.await(this, "Submitting balance sheet...", () -> {
                long submissionId = BalanceSheetService.submitBalanceSheet(
                        from, to, storeZoneId, notes, drawerSessionIds);
                services.EmailOutboxService.QueueResult emailResult;
                try {
                    emailResult = services.EmailOutboxService.queueBalanceSheetSubmission(submissionId);
                } catch (Exception emailEx) {
                    emailResult = services.EmailOutboxService.QueueResult.skipped(
                            "The email copy could not be queued: " + emailEx.getMessage());
                }
                return new SubmissionResult(submissionId, emailResult);
            });
            if (submitted == null) return;
            loadSubmissionHistory();
            startNextDraft(to);
            services.EmailOutboxService.QueueResult emailResult = submitted.emailResult();
            String message = emailResult.queued()
                    ? "Balance sheet submitted and the email copy was queued.\n\nA new draft has been started with the submitted CF as its Balance BF."
                    : "Balance sheet submitted. A new draft has been started with the submitted CF as its Balance BF.\n\nEmail copy not sent: " + emailResult.message();
            JOptionPane.showMessageDialog(this, message, "Balance Sheet", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to submit balance sheet: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private record SubmissionResult(long submissionId,
                                    services.EmailOutboxService.QueueResult emailResult) { }

    private void startNextDraft(LocalDate submittedPeriodEnd) {
        LocalDate nextStart = submittedPeriodEnd.plusDays(1);
        LocalDate nextEnd = StoreTimeZoneHelper.today();
        if (nextEnd.isBefore(nextStart)) nextEnd = nextStart;
        currentSheet = null;
        matchedDrawerSessionIds = List.of();
        fromField.setText(nextStart.toString());
        toField.setText(nextEnd.toString());
        loadSheet();
    }

    private void loadSubmissionHistory() {
        Object selected = savedSheetBox.getSelectedItem();
        Long selectedId = selected instanceof SubmissionOption option ? option.submissionId() : null;
        savedSheetBox.removeAllItems();
        savedSheetBox.setEnabled(false);
        if (historyWorker != null && !historyWorker.isDone()) {
            historyWorker.cancel(true);
        }
        historyWorker = new SwingWorker<>() {
            @Override
            protected List<SubmissionOption> doInBackground() throws Exception {
                return BalanceSheetService.listSubmissions();
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    for (SubmissionOption option : get()) {
                        savedSheetBox.addItem(option);
                        if (selectedId != null && option.submissionId() == selectedId) {
                            savedSheetBox.setSelectedItem(option);
                        }
                    }
                    savedSheetBox.setEnabled(true);
                } catch (Exception ex) {
                    savedSheetBox.setEnabled(true);
                    JOptionPane.showMessageDialog(BalanceSheet.this, "Failed to load saved balance sheets: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        historyWorker.execute();
    }

    private void loadSubmissionById(long submissionId) {
        if (savedSheetWorker != null && !savedSheetWorker.isDone()) {
            savedSheetWorker.cancel(true);
        }
        statusLabel.setText("Opening saved balance sheet...");
        savedSheetWorker = new SwingWorker<>() {
            @Override
            protected services.BalanceSheetService.BalanceSheet doInBackground() throws Exception {
                return BalanceSheetService.loadSubmission(submissionId);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    currentSheet = get();
                    matchedDrawerSessionIds = List.of();
                    fromField.setText(currentSheet.periodStart().toString());
                    toField.setText(currentSheet.periodEnd().toString());
                    renderSheet(currentSheet);
                    statusLabel.setText("Submitted by " + safeText(currentSheet.submittedByName()) + " at " + currentSheet.submittedAt().format(DATE_TIME_FORMAT));
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(BalanceSheet.this, "Failed to open saved balance sheet: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        savedSheetWorker.execute();
    }

    private void loadSelectedSubmission() {
        SubmissionOption selected = (SubmissionOption) savedSheetBox.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "No saved balance sheet is selected.", "Balance Sheet", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        loadSubmissionById(selected.submissionId());
    }

    private void editSelectedSubmission() {
        SubmissionOption selected=(SubmissionOption)savedSheetBox.getSelectedItem();
        if(selected==null){JOptionPane.showMessageDialog(this,"Select a submitted Balance Sheet first.","Edit Balance Sheet",JOptionPane.INFORMATION_MESSAGE);return;}
        try{
            EditContext context=ResponsiveTask.await(this,"Checking Balance Sheet edit window...",()->BalanceSheetService.loadEditContext(selected.submissionId()));
            if(context==null)return;
            if(!context.eligibility().editable()){JOptionPane.showMessageDialog(this,context.eligibility().lockReason(),"Balance Sheet Locked",JOptionPane.WARNING_MESSAGE);return;}
            showSubmissionEditor(context);
        }catch(Exception ex){JOptionPane.showMessageDialog(this,"Unable to edit this Balance Sheet: "+ex.getMessage(),"Edit Balance Sheet",JOptionPane.ERROR_MESSAGE);}
    }

    private void showSelectedRevisionHistory(){SubmissionOption selected=(SubmissionOption)savedSheetBox.getSelectedItem();if(selected==null){JOptionPane.showMessageDialog(this,"Select a submitted Balance Sheet first.","Revision History",JOptionPane.INFORMATION_MESSAGE);return;}try{List<services.BalanceSheetService.RevisionAudit> rows=ResponsiveTask.await(this,"Loading revision history...",()->BalanceSheetService.loadRevisionHistory(selected.submissionId()));if(rows==null)return;EditContext display=new EditContext(selected.submissionId(),selected.periodStart(),selected.periodEnd(),"",new services.BalanceSheetService.EditEligibility(false,selected.editExpiresAt(),selected.revisionNo(),""),List.of(),List.of(),rows);JTextArea area=auditText(display);JScrollPane scroll=new JScrollPane(area);scroll.setPreferredSize(new Dimension(700,420));JOptionPane.showMessageDialog(this,scroll,"Balance Sheet Revision History",JOptionPane.INFORMATION_MESSAGE);}catch(Exception ex){JOptionPane.showMessageDialog(this,"Unable to load revision history: "+ex.getMessage(),"Revision History",JOptionPane.ERROR_MESSAGE);}}

    private void showSubmissionEditor(EditContext context) {
        DefaultTableModel expenses=new DefaultTableModel(new Object[]{"ID","Date","Category","Payee","Description","Amount","Method","Reference","Status"},0);
        for(EditableExpense e:context.expenses())expenses.addRow(new Object[]{e.expenseId(),e.expenseDate(),e.category(),e.payee(),e.description(),e.amount(),e.paymentMethod(),e.paymentReference(),e.status()});
        DefaultTableModel income=new DefaultTableModel(new Object[]{"ID","Date","Source","Description","Amount","Reference"},0);
        for(EditableOtherIncome i:context.otherIncome())income.addRow(new Object[]{i.otherIncomeId(),i.incomeDate(),i.sourceName(),i.description(),i.amount(),i.paymentReference()});
        JTable expenseTable=new JTable(expenses),incomeTable=new JTable(income);hideIdColumn(expenseTable);hideIdColumn(incomeTable);
        JTextArea notes=new JTextArea(context.notes()==null?"":context.notes(),3,60),reason=new JTextArea(2,60);
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("Expenses / Payables",editableTablePanel(expenseTable,()->expenses.addRow(new Object[]{null,context.periodStart(),"General","","",BigDecimal.ZERO,"CASH","","PAID"})));tabs.addTab("Other Income",editableTablePanel(incomeTable,()->income.addRow(new Object[]{null,context.periodStart(),"","",BigDecimal.ONE,""})));tabs.addTab("Audit History",new JScrollPane(auditText(context)));
        JPanel panel=new JPanel(new BorderLayout(0,8));panel.setPreferredSize(new Dimension(980,560));
        JLabel window=new JLabel("Editable until "+context.eligibility().expiresAt().format(DATE_TIME_FORMAT)+"  |  Revision "+context.eligibility().currentRevision());
        panel.add(window,BorderLayout.NORTH);panel.add(tabs,BorderLayout.CENTER);
        JPanel fields=new JPanel(new GridLayout(0,1,0,4));fields.add(new JLabel("Submission notes:"));fields.add(new JScrollPane(notes));fields.add(new JLabel("Reason for changes (required):"));fields.add(new JScrollPane(reason));panel.add(fields,BorderLayout.SOUTH);
        int choice=JOptionPane.showConfirmDialog(this,panel,"Edit Latest Submitted Balance Sheet",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
        if(choice!=JOptionPane.OK_OPTION)return;
        try{
            String why=reason.getText().trim();if(why.isEmpty())throw new IllegalArgumentException("A reason for the changes is required.");
            List<EditableExpense> expenseRows=readExpenseRows(expenses);List<EditableOtherIncome> incomeRows=readIncomeRows(income);
            EditRequest request=new EditRequest(context.submissionId(),context.eligibility().currentRevision(),notes.getText(),why,expenseRows,incomeRows);
            RevisionSave saved=ResponsiveTask.await(this,"Saving Balance Sheet revision...",()->{
                EditResult edit=BalanceSheetService.reviseSubmission(request);services.EmailOutboxService.QueueResult email;
                try{email=services.EmailOutboxService.queueRevisedBalanceSheet(edit.submissionId(),edit.revisionNo());}
                catch(Exception emailEx){email=services.EmailOutboxService.QueueResult.skipped("The revised email could not be queued: "+emailEx.getMessage());}
                return new RevisionSave(edit,email);
            });
            if(saved==null)return;currentSheet=saved.edit().sheet();renderSheet(currentSheet);loadSubmissionHistory();
            String message="Balance Sheet revision "+saved.edit().revisionNo()+" was saved."+(saved.email().queued()?"\nThe revised email was queued.":"\nEmail copy not sent: "+saved.email().message());
            statusLabel.setText("Revision "+saved.edit().revisionNo()+" saved by "+safeText(PermissionManager.getCurrentRole()));
            JOptionPane.showMessageDialog(this,message,"Balance Sheet Revised",JOptionPane.INFORMATION_MESSAGE);
        }catch(Exception ex){JOptionPane.showMessageDialog(this,"Failed to save the revision: "+ex.getMessage(),"Edit Balance Sheet",JOptionPane.ERROR_MESSAGE);}
    }

    private JPanel editableTablePanel(JTable table,Runnable add){
        JPanel p=new JPanel(new BorderLayout(0,6));p.add(new JScrollPane(table),BorderLayout.CENTER);JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));JButton addButton=new JButton("Add Row"),removeButton=new JButton("Remove Selected");actions.add(addButton);actions.add(removeButton);addButton.addActionListener(e->add.run());removeButton.addActionListener(e->{int row=table.getSelectedRow();if(row>=0)((DefaultTableModel)table.getModel()).removeRow(table.convertRowIndexToModel(row));});p.add(actions,BorderLayout.SOUTH);return p;
    }
    private static void hideIdColumn(JTable table){table.removeColumn(table.getColumnModel().getColumn(0));}
    private JTextArea auditText(EditContext context){StringBuilder b=new StringBuilder();if(context.auditHistory().isEmpty())b.append("No revisions have been made.");else for(var a:context.auditHistory())b.append("Revision ").append(a.revisionNo()).append(" - ").append(a.changedAt().format(DATE_TIME_FORMAT)).append("\nBy: ").append(safeText(a.changedByName())).append(a.deviceName()==null||a.deviceName().isBlank()?"":" on "+a.deviceName()).append("\nReason: ").append(a.reason()).append("\nChanges: ").append(a.changeSummary()).append("\n\n");JTextArea area=new JTextArea(b.toString());area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);return area;}
    private List<EditableExpense> readExpenseRows(DefaultTableModel m){List<EditableExpense> out=new java.util.ArrayList<>();for(int r=0;r<m.getRowCount();r++)out.add(new EditableExpense(longOrNull(m.getValueAt(r,0)),dateValue(m.getValueAt(r,1)),textValue(m.getValueAt(r,2)),textValue(m.getValueAt(r,3)),textValue(m.getValueAt(r,4)),decimalValue(m.getValueAt(r,5)),textValue(m.getValueAt(r,6)),textValue(m.getValueAt(r,7)),textValue(m.getValueAt(r,8))));return out;}
    private List<EditableOtherIncome> readIncomeRows(DefaultTableModel m){List<EditableOtherIncome> out=new java.util.ArrayList<>();for(int r=0;r<m.getRowCount();r++)out.add(new EditableOtherIncome(longOrNull(m.getValueAt(r,0)),dateValue(m.getValueAt(r,1)),textValue(m.getValueAt(r,2)),textValue(m.getValueAt(r,3)),decimalValue(m.getValueAt(r,4)),textValue(m.getValueAt(r,5))));return out;}
    private static Long longOrNull(Object v){return v==null||v.toString().isBlank()?null:Long.valueOf(v.toString());}
    private static LocalDate dateValue(Object v){return v instanceof LocalDate d?d:LocalDate.parse(textValue(v));}
    private static BigDecimal decimalValue(Object v){return v instanceof BigDecimal b?b:new BigDecimal(textValue(v).replace(",","").replace("$",""));}
    private static String textValue(Object v){return v==null?"":v.toString().trim();}
    private record RevisionSave(EditResult edit,services.EmailOutboxService.QueueResult email){}

    private void applyContinuingDrawSessionRange() {
        LocalDate selectedDate = parseDate(fromField.getText().trim(), "From");
        if (selectedDate == null) {
            return;
        }
        String storeZoneId = StoreTimeZoneHelper.getStoreZoneId();
        if (drawRangeWorker != null && !drawRangeWorker.isDone()) {
            drawRangeWorker.cancel(true);
        }
        drawRangeWorker = new SwingWorker<>() {
            @Override
            protected List<DrawSessionRange> doInBackground() throws Exception {
                return BalanceSheetService.findDrawSessionRanges(storeZoneId, selectedDate, selectedDate);
            }

            @Override
            protected void done() {
                if (isCancelled()) {
                    return;
                }
                try {
                    List<DrawSessionRange> ranges = get();
                    DrawSessionSelection selection = summarizeRanges(ranges);
                    if (selection != null && !selection.from().equals(selection.to())) {
                        matchedDrawerSessionIds = selection.sessionIds();
                        fromField.setText(selection.from().toString());
                        toField.setText(selection.to().toString());
                        statusLabel.setText("Matched continuing draw sessions: " + selection.label());
                        loadSheet();
                    }
                } catch (Exception ex) {
                    System.err.println("Failed to check continuing draw session: " + ex.getMessage());
                }
            }
        };
        drawRangeWorker.execute();
    }

    private void matchDrawSessionRange(boolean showMessage) {
        LocalDate selectedDate = parseDate(fromField.getText().trim(), "From");
        if (selectedDate == null) {
            return;
        }
        LocalDate toDate = parseDate(toField.getText().trim(), "To");
        if (toDate == null) {
            toDate = selectedDate;
        }
        LocalDate rangeEnd = toDate;
        String storeZoneId = StoreTimeZoneHelper.getStoreZoneId();
        statusLabel.setText("Finding matching draw session...");
        new SwingWorker<List<DrawSessionRange>, Void>() {
            @Override
            protected List<DrawSessionRange> doInBackground() throws Exception {
                return BalanceSheetService.findDrawSessionRanges(storeZoneId, selectedDate, rangeEnd);
            }

            @Override
            protected void done() {
                try {
                    List<DrawSessionRange> ranges = get();
                    DrawSessionSelection selection = summarizeRanges(ranges);
                    if (selection == null) {
                        matchedDrawerSessionIds = List.of();
                        statusLabel.setText("No draw session matched " + selectedDate);
                        if (showMessage) {
                            JOptionPane.showMessageDialog(BalanceSheet.this, "No draw session overlaps the selected date.", "Draw Session", JOptionPane.INFORMATION_MESSAGE);
                        }
                        return;
                    }
                    matchedDrawerSessionIds = selection.sessionIds();
                    fromField.setText(selection.from().toString());
                    toField.setText(selection.to().toString());
                    loadSheet();
                    statusLabel.setText("Matched draw sessions: " + selection.label());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(BalanceSheet.this, "Failed to match draw session: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static DrawSessionSelection summarizeRanges(List<DrawSessionRange> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            return null;
        }
        LocalDate from = null;
        LocalDate to = null;
        List<Long> sessionIds = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (DrawSessionRange range : ranges) {
            sessionIds.add(range.sessionId());
            if (from == null || range.openedDate().isBefore(from)) {
                from = range.openedDate();
            }
            if (to == null || range.closedDate().isAfter(to)) {
                to = range.closedDate();
            }
            labels.add(range.label());
        }
        String label = labels.size() == 1 ? labels.get(0) : labels.size() + " drawers";
        return new DrawSessionSelection(from, to, sessionIds, label);
    }

    private void showExpenseDialog() {
        JTextField dateField = new JTextField(StoreTimeZoneHelper.today().toString(), 12);
        JTextField categoryField = new JTextField("General", 18);
        JTextField payeeField = new JTextField(18);
        JTextField amountField = new JTextField(12);
        JComboBox<String> statusBox = new JComboBox<>(new String[]{"PAID", "UNPAID"});
        JComboBox<String> methodBox = new JComboBox<>(new String[]{"CASH", "CARD", "CHEQUE", "MMG", "BANK", "OTHER"});
        JTextField referenceField = new JTextField(18);
        JTextArea descriptionArea = new JTextArea(3, 24);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        addRow(panel, gbc, 0, "Date:", dateField);
        addRow(panel, gbc, 1, "Category:", categoryField);
        addRow(panel, gbc, 2, "Payee:", payeeField);
        addRow(panel, gbc, 3, "Amount:", amountField);
        addRow(panel, gbc, 4, "Status:", statusBox);
        addRow(panel, gbc, 5, "Method:", methodBox);
        addRow(panel, gbc, 6, "Reference:", referenceField);
        addRow(panel, gbc, 7, "Description:", new JScrollPane(descriptionArea));

        int result = JOptionPane.showConfirmDialog(this, panel, "Log Expense", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        LocalDate date = parseDate(dateField.getText().trim(), "Expense date");
        BigDecimal amount = parseMoney(amountField.getText().trim());
        String category = categoryField.getText().trim();
        if (date == null || amount == null || category.isBlank()) {
            if (category.isBlank()) {
                JOptionPane.showMessageDialog(this, "Category is required.", "Log Expense", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        try {
            ExpenseEntry expense = new ExpenseEntry(
                    date,
                    category,
                    blankToNull(payeeField.getText()),
                    blankToNull(descriptionArea.getText()),
                    amount,
                    String.valueOf(methodBox.getSelectedItem()),
                    blankToNull(referenceField.getText()),
                    String.valueOf(statusBox.getSelectedItem())
            );
            Boolean saved = ResponsiveTask.await(this, "Saving expense...", () -> {
                BalanceSheetService.addManualExpense(expense);
                return Boolean.TRUE;
            });
            if (saved == null) return;
            loadSheet();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to log expense: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showOtherIncomeDialog() {
        if (currentSheet != null && currentSheet.submissionId() != null) {
            JOptionPane.showMessageDialog(this,
                    "Saved balance sheets are read-only. Refresh the screen to work on the current draft.",
                    "Other Income", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JTextField dateField = new JTextField(StoreTimeZoneHelper.today().toString(), 12);
        JTextField sourceField = new JTextField(18);
        JTextField amountField = new JTextField(12);
        JTextField referenceField = new JTextField(18);
        JTextArea descriptionArea = new JTextArea(3, 24);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        addRow(panel, gbc, 0, "Date:", dateField);
        addRow(panel, gbc, 1, "Source:", sourceField);
        addRow(panel, gbc, 2, "Amount:", amountField);
        addRow(panel, gbc, 3, "Reference:", referenceField);
        addRow(panel, gbc, 4, "Description:", new JScrollPane(descriptionArea));
        addRow(panel, gbc, 5, "Payment method:", new JLabel("Cash"));

        int result = JOptionPane.showConfirmDialog(this, panel, "Log Other Cash Income",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        LocalDate date = parseDate(dateField.getText().trim(), "Income date");
        BigDecimal amount = null;
        try {
            amount = parseWholeGydAmount(amountField.getText());
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Other Income", JOptionPane.WARNING_MESSAGE);
        }
        String source = sourceField.getText().trim();
        if (date == null || amount == null || source.isBlank()) {
            if (source.isBlank()) JOptionPane.showMessageDialog(this, "Income source is required.",
                    "Other Income", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            OtherIncomeEntry entry = new OtherIncomeEntry(date, source, blankToNull(descriptionArea.getText()),
                    amount, blankToNull(referenceField.getText()));
            Boolean saved = ResponsiveTask.await(this, "Saving Other income...", () -> {
                BalanceSheetService.addOtherIncome(entry);
                return Boolean.TRUE;
            });
            if (saved != null) loadSheet();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to log Other income: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showDeleteOtherIncomeDialog() {
        if (currentSheet != null && currentSheet.submissionId() != null) {
            JOptionPane.showMessageDialog(this,
                    "Saved balance sheets are read-only. Refresh the screen to work on the current draft.",
                    "Delete Other Income", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        LocalDate from = parseDate(fromField.getText().trim(), "From");
        LocalDate to = parseDate(toField.getText().trim(), "To");
        if (from == null || to == null || to.isBefore(from)) return;
        try {
            List<OtherIncomeOption> rows = ResponsiveTask.await(this, "Loading Other income...",
                    () -> BalanceSheetService.listDeletableOtherIncome(from, to));
            if (rows == null || rows.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No manual Other income was found for this balance sheet.",
                        "Delete Other Income", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            JComboBox<OtherIncomeOption> incomeBox = new JComboBox<>(rows.toArray(new OtherIncomeOption[0]));
            int result = JOptionPane.showConfirmDialog(this, incomeBox, "Delete Other Income",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return;
            OtherIncomeOption selected = (OtherIncomeOption) incomeBox.getSelectedItem();
            if (selected == null) return;
            int confirm = JOptionPane.showConfirmDialog(this, "Delete this Other income entry?\n\n" + selected,
                    "Delete Other Income", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
            Boolean deleted = ResponsiveTask.await(this, "Deleting Other income...", () -> {
                BalanceSheetService.deleteOtherIncome(selected.otherIncomeId(), from, to);
                return Boolean.TRUE;
            });
            if (deleted != null) loadSheet();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to delete Other income: " + ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showPayableDialog() {
        JTextField dateField = new JTextField(StoreTimeZoneHelper.today().toString(), 12);
        VendorSelector vendorSelector = new VendorSelector();
        JTextField amountField = new JTextField(12);
        JTextArea descriptionArea = new JTextArea(3, 24);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        addRow(panel, gbc, 0, "Date:", dateField);
        addRow(panel, gbc, 1, "Account/Vendor:", vendorSelector);
        addRow(panel, gbc, 2, "Amount:", amountField);
        addRow(panel, gbc, 3, "Description:", new JScrollPane(descriptionArea));

        int result = JOptionPane.showConfirmDialog(this, panel, "Log Account Payable", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        LocalDate date = parseDate(dateField.getText().trim(), "Payable date");
        BigDecimal amount = parseMoney(amountField.getText().trim());
        String accountName = vendorSelector.getSelectedVendorName();
        if (date == null || amount == null || accountName.isBlank()) {
            if (accountName.isBlank()) {
                JOptionPane.showMessageDialog(this, "Account or vendor name is required.", "Log Account Payable", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        try {
            ExpenseEntry payable = new ExpenseEntry(
                    date,
                    "Accounts Payable",
                    accountName,
                    blankToNull(descriptionArea.getText()),
                    amount,
                    "OTHER",
                    null,
                    "UNPAID"
            );
            Boolean saved = ResponsiveTask.await(this, "Saving account payable...", () -> {
                BalanceSheetService.addManualExpense(payable);
                return Boolean.TRUE;
            });
            if (saved == null) return;
            loadSheet();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to log account payable: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showPayablePaymentDialog() {
        LocalDate from = parseDate(fromField.getText().trim(), "From");
        LocalDate to = parseDate(toField.getText().trim(), "To");
        if (from == null || to == null || to.isBefore(from)) {
            return;
        }
        try {
            List<BalanceSheetService.PayableOption> payables = ResponsiveTask.await(this,
                    "Loading account payables...", () -> BalanceSheetService.listUnpaidPayables(from, to));
            if (payables == null) return;
            if (payables.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No unpaid account payables were found for this date range.", "Pay Account Payable", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            JComboBox<BalanceSheetService.PayableOption> payableBox = new JComboBox<>(payables.toArray(new BalanceSheetService.PayableOption[0]));
            JTextField dateField = new JTextField(StoreTimeZoneHelper.today().toString(), 12);
            JTextField amountField = new JTextField(12);
            JComboBox<String> methodBox = new JComboBox<>(new String[]{"CASH", "CARD", "CHEQUE", "MMG", "BANK", "OTHER"});
            JTextField referenceField = new JTextField(18);
            payableBox.addActionListener(e -> populatePayablePaymentAmount(payableBox, amountField));
            populatePayablePaymentAmount(payableBox, amountField);

            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.anchor = GridBagConstraints.WEST;
            addRow(panel, gbc, 0, "Account payable:", payableBox);
            addRow(panel, gbc, 1, "Payment date:", dateField);
            addRow(panel, gbc, 2, "Amount:", amountField);
            addRow(panel, gbc, 3, "Method:", methodBox);
            addRow(panel, gbc, 4, "Reference:", referenceField);

            int result = JOptionPane.showConfirmDialog(this, panel, "Pay Account Payable", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }

            BalanceSheetService.PayableOption selected = (BalanceSheetService.PayableOption) payableBox.getSelectedItem();
            LocalDate paymentDate = parseDate(dateField.getText().trim(), "Payment date");
            BigDecimal amount = parseMoney(amountField.getText().trim());
            if (selected == null || paymentDate == null || amount == null) {
                return;
            }
            if (amount.compareTo(selected.amount()) > 0) {
                JOptionPane.showMessageDialog(this, "Payment cannot exceed the payable balance of " + money(selected.amount()) + ".", "Pay Account Payable", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String method = String.valueOf(methodBox.getSelectedItem());
            String reference = blankToNull(referenceField.getText());
            Boolean paid = ResponsiveTask.await(this, "Recording payable payment...", () -> {
                BalanceSheetService.recordPayablePayment(
                        selected.expenseId(), paymentDate, amount, method, reference);
                return Boolean.TRUE;
            });
            if (paid == null) return;
            loadSheet();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to pay account payable: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void populatePayablePaymentAmount(JComboBox<BalanceSheetService.PayableOption> payableBox, JTextField amountField) {
        BalanceSheetService.PayableOption selected = (BalanceSheetService.PayableOption) payableBox.getSelectedItem();
        if (selected != null) {
            amountField.setText(selected.amount().toPlainString());
        }
    }

    private void showDeleteExpenseDialog(String status, String title) {
        if (currentSheet != null && currentSheet.submissionId() != null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Submitted balance sheets cannot be edited. Refresh the screen to work on the current draft.",
                    title,
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        LocalDate from = parseDate(fromField.getText().trim(), "From");
        LocalDate to = parseDate(toField.getText().trim(), "To");
        if (from == null || to == null || to.isBefore(from)) {
            return;
        }

        try {
            List<ExpenseOption> rows = ResponsiveTask.await(this, "Loading balance-sheet rows...",
                    () -> BalanceSheetService.listDeletableExpenses(from, to, status));
            if (rows == null) return;
            if (rows.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No manually entered rows were found for this balance sheet.", title, JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            JComboBox<ExpenseOption> rowBox = new JComboBox<>(rows.toArray(new ExpenseOption[0]));
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.anchor = GridBagConstraints.WEST;
            addRow(panel, gbc, 0, "Row:", rowBox);

            int result = JOptionPane.showConfirmDialog(this, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }

            ExpenseOption selected = (ExpenseOption) rowBox.getSelectedItem();
            if (selected == null) {
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Delete this row from the current balance sheet?\n\n" + selected,
                    title,
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            Boolean deleted = ResponsiveTask.await(this, "Deleting balance-sheet row...", () -> {
                BalanceSheetService.deleteManualExpense(selected.expenseId(), from, to, status);
                return Boolean.TRUE;
            });
            if (deleted == null) return;
            loadSheet();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to delete row: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showDepositChequeDialog() {
        if (currentSheet != null && currentSheet.submissionId() != null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Saved balance sheets are read-only. Refresh the screen to work on the current draft.",
                    "Deposit Cheque",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        try {
            List<ChequeDepositOption> cheques = ResponsiveTask.await(this,
                    "Loading pending cheques...", BalanceSheetService::listPendingChequeDeposits);
            if (cheques == null) return;
            if (cheques.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No pending cheques are available to deposit.", "Deposit Cheque", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            JComboBox<ChequeDepositOption> chequeBox = new JComboBox<>(cheques.toArray(new ChequeDepositOption[0]));
            JTextArea notesArea = new JTextArea(3, 24);
            JPanel panel = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.anchor = GridBagConstraints.WEST;
            addRow(panel, gbc, 0, "Cheque:", chequeBox);
            addRow(panel, gbc, 1, "Notes:", new JScrollPane(notesArea));

            int result = JOptionPane.showConfirmDialog(this, panel, "Deposit Cheque", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }

            ChequeDepositOption selected = (ChequeDepositOption) chequeBox.getSelectedItem();
            if (selected == null) {
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Mark this cheque as deposited in bank?\n\n" + selected,
                    "Deposit Cheque",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }

            String notes = notesArea.getText();
            Boolean deposited = ResponsiveTask.await(this, "Recording cheque deposit...", () -> {
                BalanceSheetService.markChequeDeposited(selected, notes);
                return Boolean.TRUE;
            });
            if (deposited == null) return;
            loadSheet();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to mark cheque deposited: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        panel.add(field, gbc);
    }

    private static void fill(DefaultTableModel model, List<SheetLine> lines, BigDecimal total) {
        model.setRowCount(0);
        for (SheetLine line : lines) {
            model.addRow(new Object[]{line.label(), money(line.amount())});
        }
        model.addRow(new Object[]{"TOTAL", money(total)});
    }

    private void showLoadingState(String message) {
        fillIncomeLoading();
        fillLoading(receivableModel);
        fillLoading(expenseModel);
        fillLoading(payableModel);
        fillLoading(drawerCashModel);
        fillDeviceActivityLoading();
        fillBankTransactionsLoading();
        fillPendingChequesLoading();
        drawerChecksStatusLabel.setText("Checking drawer activity...");
        drawerChecksContent.setPreferredSize(new Dimension(1, 52));
        drawerChecksLayout.show(drawerChecksContent, "status");
        statusLabel.setText(message);
    }

    private void renderDrawerChecks(List<SheetLine> checks) {
        if (checks == null || checks.isEmpty()) {
            drawerChecksStatusLabel.setText("No drawer match results available");
            drawerChecksContent.setPreferredSize(new Dimension(1, 52));
            drawerChecksLayout.show(drawerChecksContent, "status");
            return;
        }

        if (checks.size() == 1) {
            String label = checks.get(0).label();
            if (label != null && label.startsWith("All cash sales and orders match")) {
                drawerChecksStatusLabel.setText("\u2713 Drawer activity matched");
                drawerChecksContent.setPreferredSize(new Dimension(1, 52));
                drawerChecksLayout.show(drawerChecksContent, "status");
                return;
            }
            if (label != null && (label.startsWith("Match a drawer session") || label.startsWith("Match a draw session"))) {
                drawerChecksStatusLabel.setText("Match a drawer session to check cash activity");
                drawerChecksContent.setPreferredSize(new Dimension(1, 52));
                drawerChecksLayout.show(drawerChecksContent, "status");
                return;
            }
        }

        fill(drawerChecksModel, checks, totalLines(checks));
        drawerChecksContent.setPreferredSize(new Dimension(1, 180));
        drawerChecksLayout.show(drawerChecksContent, "details");
    }

    private void resetCfChecker() {
        updatingCfChecker = true;
        cfCheckerModel.setRowCount(0);
        for (int denomination : CF_DENOMINATIONS) {
            cfCheckerModel.addRow(new Object[]{CURRENCY.format(denomination), 0, money(BigDecimal.ZERO)});
        }
        cfCheckerModel.addRow(new Object[]{"TOTAL", "", money(BigDecimal.ZERO)});
        updatingCfChecker = false;
        recalculateCfChecker();
    }

    private void recalculateCfChecker() {
        updatingCfChecker = true;
        BigDecimal countedTotal = BigDecimal.ZERO;
        for (int row = 0; row < CF_DENOMINATIONS.length; row++) {
            int quantity = quantityAt(cfCheckerModel, row, 1);
            BigDecimal lineTotal = BigDecimal.valueOf((long) CF_DENOMINATIONS[row] * quantity);
            countedTotal = countedTotal.add(lineTotal);
            cfCheckerModel.setValueAt(money(lineTotal), row, 2);
        }
        int totalRow = CF_DENOMINATIONS.length;
        if (cfCheckerModel.getRowCount() > totalRow) {
            cfCheckerModel.setValueAt("TOTAL", totalRow, 0);
            cfCheckerModel.setValueAt("", totalRow, 1);
            cfCheckerModel.setValueAt(money(countedTotal), totalRow, 2);
        }
        BigDecimal variance = countedTotal.subtract(defaultZero(cfExpectedTotal));
        cfExpectedLabel.setText("Expected CF: " + money(cfExpectedTotal));
        cfCountedLabel.setText("Counted Cash: " + money(countedTotal));
        cfVarianceLabel.setText((variance.compareTo(BigDecimal.ZERO) < 0 ? "Missing: " : "Over: ") + money(variance.abs()));
        updatingCfChecker = false;
    }

    private static void fillLoading(DefaultTableModel model) {
        model.setRowCount(0);
        model.addRow(new Object[]{"Loading...", ""});
    }

    private void fillIncomeLoading() {
        incomeModel.setRowCount(0);
        incomeModel.addRow(new Object[]{"Loading...", "", "", "", "", "", ""});
    }

    private void fillIncome(List<SheetLine> income, BigDecimal totalIncome) {
        incomeModel.setRowCount(0);
        IncomeBreakdown sales = new IncomeBreakdown(
                incomeAmount(income, "ACCOUNT CHARGES"),
                incomeAmount(income, "POS CASH", "CASH"),
                incomeAmount(income, "MMG"),
                incomeAmount(income, "POS CARD", "CARD", "CREDIT"),
                incomeAmount(income, "CHEQUES", "CHEQUE"));
        IncomeBreakdown orders = new IncomeBreakdown(
                incomeAmount(income, "ORDER ACCOUNT"),
                incomeAmount(income, "ORDER CASH"),
                incomeAmount(income, "ORDER MMG"),
                incomeAmount(income, "ORDER CARD", "ORDER CREDIT"),
                incomeAmount(income, "ORDER CHEQUE"));
        IncomeBreakdown invoices = new IncomeBreakdown(
                incomeAmount(income, "INVOICE ACCOUNT"),
                incomeAmount(income, "INVOICE CASH"),
                incomeAmount(income, "INVOICE MMG"),
                incomeAmount(income, "INVOICE CARD", "INVOICE CREDIT"),
                incomeAmount(income, "INVOICE CHEQUE"));
        IncomeBreakdown other = new IncomeBreakdown(BigDecimal.ZERO,
                incomeAmount(income, "OTHER CASH"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        addIncomeRow("Sales", sales);
        addIncomeRow("Orders", orders);
        addIncomeRow("Invoices", invoices);
        addIncomeRow("Other", other);
        addIncomeRow("TOTAL", sales.add(orders).add(invoices).add(other));
    }

    private void addIncomeRow(String type, IncomeBreakdown breakdown) {
        incomeModel.addRow(new Object[]{
                type,
                money(breakdown.charge()),
                money(breakdown.cash()),
                money(breakdown.mmg()),
                money(breakdown.credit()),
                money(breakdown.cheque()),
                money(breakdown.total())
        });
    }

    private static BigDecimal incomeAmount(List<SheetLine> lines, String... labels) {
        BigDecimal total = BigDecimal.ZERO;
        for (SheetLine line : lines) {
            for (String label : labels) {
                if (label.equalsIgnoreCase(line.label())) {
                    total = total.add(defaultZero(line.amount()));
                    break;
                }
            }
        }
        return total;
    }

    private void fillDeviceActivityLoading() {
        deviceActivityModel.setRowCount(0);
        deviceActivityModel.addRow(new Object[]{"Loading...", "", "", ""});
    }

    private void fillDeviceActivity(List<SheetLine> sales, List<SheetLine> orders, List<SheetLine> payments) {
        deviceActivityModel.setRowCount(0);
        Set<String> devices = new LinkedHashSet<>();
        collectDeviceLabels(devices, sales);
        collectDeviceLabels(devices, orders);
        collectDeviceLabels(devices, payments);
        if (devices.isEmpty()) {
            deviceActivityModel.addRow(new Object[]{"No device activity logged", money(BigDecimal.ZERO), money(BigDecimal.ZERO), money(BigDecimal.ZERO)});
        } else {
            for (String device : devices) {
                deviceActivityModel.addRow(new Object[]{
                        device,
                        money(amountForDevice(sales, device)),
                        money(amountForDevice(orders, device)),
                        money(amountForDevice(payments, device))
                });
            }
        }
        deviceActivityModel.addRow(new Object[]{
                "TOTAL",
                money(totalDeviceLines(sales)),
                money(totalDeviceLines(orders)),
                money(totalDeviceLines(payments))
        });
    }

    private static void collectDeviceLabels(Set<String> devices, List<SheetLine> lines) {
        for (SheetLine line : lines) {
            String label = line.label();
            if (label != null && !label.startsWith("No device ")) {
                devices.add(label);
            }
        }
    }

    private static BigDecimal amountForDevice(List<SheetLine> lines, String device) {
        BigDecimal total = BigDecimal.ZERO;
        for (SheetLine line : lines) {
            if (device.equals(line.label())) {
                total = total.add(defaultZero(line.amount()));
            }
        }
        return total;
    }

    private static BigDecimal totalDeviceLines(List<SheetLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (SheetLine line : lines) {
            if (line.label() != null && !line.label().startsWith("No device ")) {
                total = total.add(defaultZero(line.amount()));
            }
        }
        return total;
    }

    private void fillPendingChequesLoading() {
        pendingChequeModel.setRowCount(0);
        pendingChequeModel.addRow(new Object[]{"Loading...", "", "", ""});
    }

    private void fillBankTransactionsLoading() {
        bankTransactionModel.setRowCount(0);
        bankTransactionModel.addRow(new Object[]{"Loading...", "", ""});
    }

    private void fillBankTransactions(List<BankTransactionLine> transactions) {
        bankTransactionModel.setRowCount(0);
        if (transactions == null || transactions.isEmpty()) {
            bankTransactionModel.addRow(new Object[]{"No bank transactions", money(BigDecimal.ZERO), ""});
            return;
        }
        for (BankTransactionLine transaction : transactions) {
            bankTransactionModel.addRow(new Object[]{
                    transaction.transaction(),
                    money(transaction.amount()),
                    transaction.direction()
            });
        }
    }

    private void fillPendingCheques(List<ChequeDepositOption> cheques) {
        pendingChequeModel.setRowCount(0);
        if (cheques == null || cheques.isEmpty()) {
            pendingChequeModel.addRow(new Object[]{"No pending cheques", "", "", money(BigDecimal.ZERO)});
            return;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ChequeDepositOption cheque : cheques) {
            total = total.add(defaultZero(cheque.amount()));
            pendingChequeModel.addRow(new Object[]{
                    cheque.sourceLabel(),
                    cheque.chequeAt() == null ? "" : cheque.chequeAt().toLocalDate().toString(),
                    cheque.reference() == null || cheque.reference().isBlank() ? safeText(cheque.payer()) : safeText(cheque.payer()) + " / " + cheque.reference(),
                    money(cheque.amount())
            });
        }
        pendingChequeModel.addRow(new Object[]{"TOTAL", "", "", money(total)});
    }

    private static BigDecimal totalLines(List<SheetLine> lines) {
        BigDecimal total = BigDecimal.ZERO;
        for (SheetLine line : lines) {
            total = total.add(line.amount() == null ? BigDecimal.ZERO : line.amount());
        }
        return total;
    }

    private static DefaultTableModel tableModel() {
        return new DefaultTableModel(new Object[]{"Description", "Amount"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static DefaultTableModel incomeTableModel() {
        return new DefaultTableModel(new Object[]{"Type", "Charge Amount", "Cash", "MMG", "Credit", "Cheque", "Total"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static DefaultTableModel deviceActivityTableModel() {
        return new DefaultTableModel(new Object[]{"Device", "Sales", "Orders", "Payments"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static DefaultTableModel pendingChequeTableModel() {
        return new DefaultTableModel(new Object[]{"Source", "Date", "Payer / Ref", "Amount"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static DefaultTableModel bankTransactionTableModel() {
        return new DefaultTableModel(new Object[]{"Transaction", "Amount", "Direction"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static DefaultTableModel cfCheckerTableModel() {
        return new DefaultTableModel(new Object[]{"Bill", "Qty", "Total"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1 && row < CF_DENOMINATIONS.length;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 1 ? Integer.class : String.class;
            }
        };
    }

    private static JLabel metricLabel(String text) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setBackground(surfaceColor());
        label.setForeground(textColor());
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        label.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        label.setFont(new Font("SansSerif", Font.BOLD, 15));
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor()),
                new EmptyBorder(12, 12, 12, 12)
        ));
        return label;
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(textColor());
        label.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        return label;
    }

    private static void configureTable(JTable table) {
        table.setBackground(surfaceColor());
        table.setForeground(textColor());
        table.setGridColor(borderColor());
        table.setSelectionBackground(ThemeManager.isDarkModeEnabled() ? new Color(37, 99, 235) : new Color(219, 234, 254));
        table.setSelectionForeground(ThemeManager.isDarkModeEnabled() ? Color.WHITE : LIGHT_TEXT);
        table.getTableHeader().setBackground(ThemeManager.isDarkModeEnabled() ? new Color(38, 38, 38) : new Color(241, 245, 249));
        table.getTableHeader().setForeground(textColor());
    }

    private static Color backgroundColor() {
        return ThemeManager.isDarkModeEnabled() ? DARK_BACKGROUND : LIGHT_BACKGROUND;
    }

    private static Color surfaceColor() {
        return ThemeManager.isDarkModeEnabled() ? DARK_SURFACE : LIGHT_SURFACE;
    }

    private static Color textColor() {
        return ThemeManager.isDarkModeEnabled() ? DARK_TEXT : LIGHT_TEXT;
    }

    private static Color borderColor() {
        return ThemeManager.isDarkModeEnabled() ? DARK_BORDER : LIGHT_BORDER;
    }

    private LocalDate parseDate(String value, String label) {
        try {
            return LocalDate.parse(value, DATE_FORMAT);
        } catch (DateTimeParseException ex) {
            JOptionPane.showMessageDialog(this, label + " must use YYYY-MM-DD.", "Balance Sheet", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private BigDecimal parseMoney(String value) {
        return parseMoney(value, "Log Expense");
    }

    private BigDecimal parseMoney(String value, String title) {
        if (value == null || value.isBlank()) {
            JOptionPane.showMessageDialog(this, "Amount is required.", title, JOptionPane.WARNING_MESSAGE);
            return null;
        }
        try {
            BigDecimal amount = new BigDecimal(value.replace(",", "").replace("$", "").trim());
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, "Amount cannot be negative.", title, JOptionPane.WARNING_MESSAGE);
                return null;
            }
            return amount;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Amount must be a valid number.", title, JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    static BigDecimal parseWholeGydAmount(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Amount is required.");
        }
        final BigDecimal amount;
        try {
            amount = new BigDecimal(value.replace(",", "").replace("$", "").trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Amount must be a valid number.");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Income amount must be greater than zero.");
        }
        if (amount.remainder(BigDecimal.ONE).signum() != 0) {
            throw new IllegalArgumentException("Enter the income amount in whole GYD; cents are not used.");
        }
        if (amount.precision() - amount.scale() > 10) {
            throw new IllegalArgumentException("Income amount cannot exceed 10 whole digits.");
        }
        return amount.setScale(0);
    }

    private static int quantityAt(DefaultTableModel model, int row, int column) {
        Object value = model.getValueAt(row, column);
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }
        try {
            return Math.max(Integer.parseInt(String.valueOf(value == null ? "" : value).trim()), 0);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String money(BigDecimal value) {
        return CURRENCY.format(value == null ? BigDecimal.ZERO : value);
    }

    private static String blankToNull(String value) {
        if (value == null || value.trim().isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String safeText(String value) {
        return value == null || value.isBlank() ? "Unknown" : value;
    }

    private record DrawSessionSelection(LocalDate from, LocalDate to, List<Long> sessionIds, String label) {
    }

    private record IncomeBreakdown(BigDecimal charge, BigDecimal cash, BigDecimal mmg, BigDecimal credit, BigDecimal cheque) {
        private IncomeBreakdown add(IncomeBreakdown other) {
            return new IncomeBreakdown(
                    defaultZero(charge).add(defaultZero(other.charge())),
                    defaultZero(cash).add(defaultZero(other.cash())),
                    defaultZero(mmg).add(defaultZero(other.mmg())),
                    defaultZero(credit).add(defaultZero(other.credit())),
                    defaultZero(cheque).add(defaultZero(other.cheque()))
            );
        }

        private BigDecimal total() {
            return defaultZero(charge)
                    .add(defaultZero(cash))
                    .add(defaultZero(mmg))
                    .add(defaultZero(credit))
                    .add(defaultZero(cheque));
        }
    }

    private class CfCheckerRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
            if (modelRow == CF_DENOMINATIONS.length) {
                component.setFont(component.getFont().deriveFont(Font.BOLD));
                if (!isSelected) {
                    component.setBackground(new Color(22, 101, 52));
                    component.setForeground(Color.WHITE);
                }
            } else if (!isSelected) {
                component.setBackground(surfaceColor());
                component.setForeground(textColor());
            }
            return component;
        }
    }
}
