package ui.screens;

import Receipt.CashDrawerCloseReceiptPrinter;
import utils.CurrencyFormatter;
import managers.HardwareSettingsManager;
import managers.NavigationManager;
import managers.PermissionManager;
import managers.SessionManager;
import models.CashDrawerContext;
import models.CashDrawerHandover;
import models.CashDrawerSession;
import services.CashDrawerService;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.UiTaskRunner;
import ui.helpers.WindowHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class BalanceDraw extends JFrame {
    private static final int[] DENOMINATIONS = {5000, 2000, 1000, 500, 100, 50, 20};
    private static final int[] PREFERRED_FLOAT_DENOMINATIONS = {20, 100, 500, 1000};
    private static final int[] FLOAT_FILL_DENOMINATIONS = {1000, 500, 100, 20};
    private static final NumberFormat CURRENCY = CurrencyFormatter.create(Locale.US);
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
    private static final Color MATCH_COLOR = new Color(34, 139, 34);
    private static final Color VARIANCE_COLOR = new Color(190, 38, 20);
    private static final Color METRIC_TEXT_COLOR = new Color(31, 41, 55);

    private final JLabel statusLabel = new JLabel("Loading draw status...");
    private final JLabel drawerLabel = new JLabel();
    private final JLabel mainCashierLabel = new JLabel();
    private final JLabel currentCashierLabel = new JLabel();
    private final JLabel openedLabel = new JLabel();
    private final JLabel expectedLabel = new JLabel();
    private final JLabel countedLabel = new JLabel();
    private final JLabel floatLabel = new JLabel();
    private String pendingMutationKey;
    private String pendingMutationFingerprint;
    private final JLabel cihLabel = new JLabel();
    private final JLabel varianceLabel = new JLabel();
    private final DefaultTableModel denominationModel;
    private final JTable denominationTable;
    private final JButton startButton = new JButton("Start Draw");
    private final JButton calculateFloatButton = new JButton("Calculate Float");
    private final JButton clearQtyButton = new JButton("Clear Qty");
    private final JButton handoverButton = new JButton("Confirm Handover");
    private final JButton closeButton = new JButton("Close Draw");
    private final JButton editClosedButton = new JButton("Edit Closed Draw");
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton backButton = new JButton("Main Menu");

    private CashDrawerSession activeSession;
    private BigDecimal expectedCash = BigDecimal.ZERO;
    private BigDecimal expectedFloatCash = BigDecimal.ZERO;
    private BigDecimal currentFloatTotal = BigDecimal.ZERO;
    private BigDecimal currentCihTotal = BigDecimal.ZERO;
    private Map<Integer, Integer> currentFloatCounts = new HashMap<>();
    private Map<Integer, Integer> configuredFloatMix = CashDrawerService.DEFAULT_FLOAT_MIX;
    private boolean floatCalculated;
    private boolean updatingTable;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public BalanceDraw() {
        setTitle("Balance Draw");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(820, 660);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "BalanceDraw"));

        denominationModel = new DefaultTableModel(new Object[]{"$$", "QTY", "FLOAT", "CIH"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1 && activeSession != null && row < DENOMINATIONS.length;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 1 || columnIndex == 2 || columnIndex == 3 ? Integer.class : String.class;
            }
        };
        denominationTable = new JTable(denominationModel);
        denominationTable.setRowHeight(30);
        denominationTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        denominationTable.getTableHeader().setReorderingAllowed(false);
        denominationTable.setDefaultRenderer(Object.class, new CashCountRenderer());
        denominationTable.setDefaultRenderer(Integer.class, new CashCountRenderer());
        denominationTable.getModel().addTableModelListener(e -> {
            if (!updatingTable && e.getType() == TableModelEvent.UPDATE && e.getColumn() == 1) {
                if (allQuantityCellsReady()) {
                    currentFloatCounts = calculateFloatCountsFromDrawer();
                    floatCalculated = true;
                } else {
                    currentFloatCounts = new HashMap<>();
                    floatCalculated = false;
                }
                recalculateDenominations();
                saveQuantityDraft();
            }
        });

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(20, 20, 20, 20));
        root.setBackground(new Color(245, 247, 250));
        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildCenterPanel(), BorderLayout.CENTER);
        root.add(buildFooterPanel(), BorderLayout.SOUTH);
        add(root, BorderLayout.CENTER);

        startButton.addActionListener(e -> startDraw());
        calculateFloatButton.addActionListener(e -> calculateFloat());
        clearQtyButton.addActionListener(e -> clearQuantities());
        handoverButton.addActionListener(e -> confirmHandover());
        closeButton.addActionListener(e -> closeDraw());
        editClosedButton.addActionListener(e -> editClosedDraw());
        refreshButton.addActionListener(e -> loadState());
        backButton.addActionListener(e -> NavigationManager.showMainMenu(this));

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent event) {
                WindowHelper.configurePosWindow(BalanceDraw.this);
                loadState();
            }
        });
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        panel.setOpaque(false);
        JLabel titleLabel = new JLabel("Balance Draw");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 28));
        titleLabel.setForeground(new Color(31, 41, 55));
        statusLabel.setForeground(new Color(75, 85, 99));

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.add(titleLabel);
        titleStack.add(Box.createVerticalStrut(4));
        titleStack.add(statusLabel);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(refreshButton);
        actions.add(backButton);

        panel.add(titleStack, BorderLayout.WEST);
        panel.add(actions, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildCenterPanel() {
        JPanel panel = new JPanel(new BorderLayout(14, 14));
        panel.setOpaque(false);
        panel.add(buildSummaryPanel(), BorderLayout.NORTH);

        JScrollPane tableScroll = new JScrollPane(denominationTable);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Cash Count"));
        panel.add(tableScroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildSummaryPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 3, 10, 10));
        panel.setOpaque(false);
        panel.add(metric("Drawer", drawerLabel));
        panel.add(metric("Main Cashier", mainCashierLabel));
        panel.add(metric("Current Cashier", currentCashierLabel));
        panel.add(metric("Opened", openedLabel));
        panel.add(metric("Expected", expectedLabel));
        panel.add(metric("Counted", countedLabel));
        panel.add(metric("Float", floatLabel));
        panel.add(metric("CIH", cihLabel));
        panel.add(metric("Variance", varianceLabel));
        return panel;
    }

    private JPanel metric(String title, JLabel valueLabel) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 224, 230)),
                new EmptyBorder(10, 10, 10, 10)
        ));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setForeground(new Color(75, 85, 99));
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        valueLabel.setForeground(METRIC_TEXT_COLOR);
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFooterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panel.setOpaque(false);
        panel.add(startButton);
        panel.add(calculateFloatButton);
        panel.add(clearQtyButton);
        panel.add(handoverButton);
        panel.add(closeButton);
        panel.add(editClosedButton);
        panel.add(loadingState);
        return panel;
    }

    private void loadState() {
        if (!PermissionManager.hasPermission("BALANCE_DRAWER")) {
                statusLabel.setText("You do not have permission to balance draws.");
            startButton.setEnabled(false);
            calculateFloatButton.setEnabled(false);
            clearQtyButton.setEnabled(false);
            handoverButton.setEnabled(false);
            closeButton.setEnabled(false);
            editClosedButton.setEnabled(false);
            return;
        }

        CachedUiLoader.load(this, "cash-drawer:state", LanApiClient.CashDrawerRegisterState.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                LanApiClient::loadCashDrawerRegisterState, this::applyState);
    }

    private void applyState(LanApiClient.CashDrawerRegisterState state) {
            activeSession=state.session();drawerLabel.setText(state.drawerName()==null?"Unassigned":state.drawerName());
            if (state.drawerId()==null) {
                statusLabel.setText("This register is not assigned to an active cash drawer.");
                mainCashierLabel.setText("-");
                currentCashierLabel.setText("-");
                startButton.setEnabled(false);
                calculateFloatButton.setEnabled(false);
                clearQtyButton.setEnabled(false);
                handoverButton.setEnabled(false);
                closeButton.setEnabled(false);
                editClosedButton.setEnabled(true);
                resetTable(BigDecimal.ZERO);
                return;
            }

            if (activeSession == null) {
                statusLabel.setText("No draw is open. Start the draw before taking cash.");
                mainCashierLabel.setText("-");
                currentCashierLabel.setText("-");
                openedLabel.setText("-");
                expectedCash = BigDecimal.ZERO;
                expectedLabel.setText("-");
                startButton.setEnabled(true);
                calculateFloatButton.setEnabled(false);
                clearQtyButton.setEnabled(false);
                handoverButton.setEnabled(false);
                closeButton.setEnabled(false);
                editClosedButton.setEnabled(true);
                resetTable(BigDecimal.ZERO);
                return;
            }

            expectedCash = state.expectedCash();
            configuredFloatMix = state.floatMix();
            statusLabel.setText("Draw is open. Count cash for handover or final close.");
            mainCashierLabel.setText(displayName(activeSession.mainCashierName()));
            currentCashierLabel.setText(displayName(activeSession.currentCashierName()));
            openedLabel.setText(activeSession.openedAt() == null ? "" : DISPLAY_FORMAT.format(activeSession.openedAt().toLocalDateTime()));
            expectedLabel.setText(CURRENCY.format(expectedCash));
            startButton.setEnabled(false);
            calculateFloatButton.setEnabled(true);
            clearQtyButton.setEnabled(true);
            handoverButton.setEnabled(true);
            closeButton.setEnabled(true);
            editClosedButton.setEnabled(false);
            resetTable(activeSession.openingCash());
            restoreQuantityDraft(activeSession.sessionId());
    }

    private void startDraw() {
        String key=mutationKey("open");
        UiTaskRunner.submit(this,"cash-drawer.open",()->LanApiClient.openCashDrawer(key),session->{activeSession=session;clearMutationKey();SessionDataCache.invalidate("cash-drawer:");loadState();},ex->JOptionPane.showMessageDialog(this,"Failed to start draw: "+ex.getMessage(),"Balance Draw",JOptionPane.ERROR_MESSAGE));
    }

    private void confirmHandover() {
        if (activeSession == null) {
            return;
        }
        if (!allQuantityCellsReady()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Enter 0 or more for every bill quantity before confirming a handover.",
                    "Count Required",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        BigDecimal countedCash = countedTotal();
        BigDecimal variance = countedCash.subtract(expectedCash);
        if (variance.compareTo(BigDecimal.ZERO) != 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "Handover count must match expected cash.\nExpected: " + CURRENCY.format(expectedCash)
                            + "\nCounted: " + CURRENCY.format(countedCash)
                            + "\nVariance: " + CURRENCY.format(variance),
                    "Handover Count Mismatch",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Confirm handover from " + displayName(activeSession.currentCashierName())
                        + " to " + displayName(SessionManager.getCurrentUserDisplayName())
                        + " with " + CURRENCY.format(countedCash) + " in the draw?",
                "Confirm Handover",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        long sessionId=activeSession.sessionId();String key=mutationKey("handover|"+sessionId+"|"+countedCash);
        UiTaskRunner.submit(this,"cash-drawer.handover",()->LanApiClient.handoverCashDrawer(sessionId,countedCash,null,key),handover->{clearMutationKey();SessionDataCache.invalidate("cash-drawer:");
            clearQuantityDraft(sessionId);
            JOptionPane.showMessageDialog(
                    this,
                    "Handover confirmed.\nFrom: " + displayName(handover.fromUserName())
                            + "\nTo: " + displayName(handover.toUserName())
                            + "\nCounted: " + CURRENCY.format(handover.countedCash())
            );
            loadState();
        },ex->JOptionPane.showMessageDialog(this,"Failed to confirm handover: "+ex.getMessage(),"Balance Draw",JOptionPane.ERROR_MESSAGE));
    }

    private void closeDraw() {
        if (activeSession == null) {
            return;
        }
        BigDecimal countedCash = countedTotal();
        if (!floatCalculated) {
            JOptionPane.showMessageDialog(
                    this,
                    "Enter all bill quantities, then click Calculate Float before closing the draw.",
                    "Calculate Float",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        if (countedCash.compareTo(activeSession.openingCash()) < 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "Counted cash is below the required float of " + CURRENCY.format(activeSession.openingCash()) + ".",
                    "Balance Draw",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        if (currentFloatTotal.compareTo(activeSession.openingCash()) != 0) {
            JOptionPane.showMessageDialog(
                    this,
                    "The calculated float is " + CURRENCY.format(currentFloatTotal)
                            + ", but the required float is " + CURRENCY.format(activeSession.openingCash()) + ".\n"
                            + "Add enough counted cash to preserve the required float before closing.",
                    "Float Cash Required",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Close this draw with counted cash of " + CURRENCY.format(countedCash) + "?",
                "Close Draw",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        long sessionId=activeSession.sessionId();String key=mutationKey("close|"+sessionId+"|"+countedCash);
        BigDecimal closingCih=currentCihTotal;BigDecimal closingFloat=currentFloatTotal;
        List<CashDrawerCloseReceiptPrinter.BreakdownLine> closingBreakdown=closingCashBreakdown();
        UiTaskRunner.submit(this,"cash-drawer.close",()->{
            LanApiClient.CashDrawerCloseResult result=LanApiClient.closeCashDrawer(sessionId,countedCash,null,key);
            String printError=null;
            try {
                CashDrawerCloseReceiptPrinter.print(result.session(),closingCih,closingFloat,closingBreakdown,
                        result.handlers(),result.returnedAmount(),
                        HardwareSettingsManager.getDefaultReceiptPrinter());
            } catch (Exception ex) {
                printError=ex.getMessage();
            }
            return new CloseDrawSnapshot(result,printError);
        },snapshot->{clearMutationKey();SessionDataCache.invalidate("cash-drawer:");
            clearQuantityDraft(sessionId);
            LanApiClient.CashDrawerCloseResult result=snapshot.result();
            CashDrawerSession closed=result.session();String handlers=String.join(", ",result.handlers());
            JOptionPane.showMessageDialog(
                    this,
                    "Draw closed.\nExpected: " + CURRENCY.format(closed.expectedCash())
                            + "\nCounted: " + CURRENCY.format(closed.countedCash())
                            + "\nCash to remove: " + CURRENCY.format(closed.cashToRemove())
                            + "\nVariance: " + CURRENCY.format(closed.variance())
                            + "\nMain Cashier: " + displayName(closed.mainCashierName())
                            + "\nBalanced By: " + displayName(closed.balancedByName())
                            + "\nCash Handlers: " + (handlers.isBlank() ? "None" : handlers)
                            + (snapshot.printError()==null ? "\n\nClose receipt printed."
                            : "\n\nThe draw was closed, but the receipt did not print:\n"+snapshot.printError())
            );
            loadState();
        },ex->JOptionPane.showMessageDialog(this,"Failed to close draw: "+ex.getMessage(),"Balance Draw",JOptionPane.ERROR_MESSAGE));
    }

    private record CloseDrawSnapshot(LanApiClient.CashDrawerCloseResult result,String printError) { }

    private List<CashDrawerCloseReceiptPrinter.BreakdownLine> closingCashBreakdown() {
        List<CashDrawerCloseReceiptPrinter.BreakdownLine> lines=new ArrayList<>();
        for (int row=0;row<DENOMINATIONS.length;row++) {
            int denomination=DENOMINATIONS[row];
            int quantity=quantityAt(row,1);
            int floatQuantity=currentFloatCounts.getOrDefault(denomination,0);
            lines.add(new CashDrawerCloseReceiptPrinter.BreakdownLine(
                    denomination,quantity,floatQuantity,Math.max(quantity-floatQuantity,0)));
        }
        return List.copyOf(lines);
    }

    private void editClosedDraw() {
        if (!PermissionManager.hasPermission("BALANCE_DRAWER")) {
            JOptionPane.showMessageDialog(this, "You do not have permission to correct closed draws.", "Balance Draw", JOptionPane.WARNING_MESSAGE);
            return;
        }
        UiTaskRunner.submit(this,"cash-drawer.closed-list",()->LanApiClient.loadRecentCashDrawers()
                    .stream()
                    .filter(session -> !session.isOpen())
                    .map(ClosedDrawOption::new)
                    .toList(),this::showClosedDrawEditor,ex->JOptionPane.showMessageDialog(this,"Failed to load closed draws: "+ex.getMessage(),"Balance Draw",JOptionPane.ERROR_MESSAGE));
    }

    private void showClosedDrawEditor(List<ClosedDrawOption> options) {
            if (options.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No closed draw sessions were found for this store.", "Edit Closed Draw", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            JComboBox<ClosedDrawOption> sessionBox = new JComboBox<>(options.toArray(new ClosedDrawOption[0]));
            JTextField countedField = new JTextField(12);
            JTextArea notesArea = new JTextArea(3, 28);
            sessionBox.addActionListener(e -> populateClosedDrawCount(sessionBox, countedField));
            populateClosedDrawCount(sessionBox, countedField);

            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.anchor = GridBagConstraints.WEST;
            addDialogRow(form, gbc, 0, "Closed draw:", sessionBox);
            addDialogRow(form, gbc, 1, "Corrected count:", countedField);
            addDialogRow(form, gbc, 2, "Reason:", new JScrollPane(notesArea));

            int result = JOptionPane.showConfirmDialog(this, form, "Edit Closed Draw", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) {
                return;
            }
            ClosedDrawOption selected = (ClosedDrawOption) sessionBox.getSelectedItem();
            BigDecimal correctedCount = parseMoney(countedField.getText().trim());
            if (selected == null || correctedCount == null) {
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Correct " + selected + " to counted cash of " + CURRENCY.format(correctedCount) + "?",
                    "Confirm Closed Draw Correction",
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.OK_OPTION) {
                return;
            }

            long sessionId=selected.session().sessionId();String notes=notesArea.getText();String key=mutationKey("revise|"+sessionId+"|"+correctedCount+"|"+notes);
            UiTaskRunner.submit(this,"cash-drawer.revise",()->LanApiClient.reviseCashDrawer(sessionId,correctedCount,notes,key),revised->{clearMutationKey();SessionDataCache.invalidate("cash-drawer:");
            JOptionPane.showMessageDialog(
                    this,
                    "Closed draw corrected.\nExpected: " + CURRENCY.format(revised.expectedCash())
                            + "\nCounted: " + CURRENCY.format(revised.countedCash())
                            + "\nCash to remove: " + CURRENCY.format(revised.cashToRemove())
                            + "\nVariance: " + CURRENCY.format(revised.variance())
                            + "\nBalanced By: " + displayName(revised.balancedByName()),
                    "Edit Closed Draw",
                    JOptionPane.INFORMATION_MESSAGE
            );
            loadState();
            },ex->JOptionPane.showMessageDialog(this,"Failed to correct closed draw: "+ex.getMessage(),"Balance Draw",JOptionPane.ERROR_MESSAGE));
    }

    private String mutationKey(String fingerprint){if(pendingMutationKey==null||!fingerprint.equals(pendingMutationFingerprint)){
        pendingMutationKey=UUID.randomUUID().toString();pendingMutationFingerprint=fingerprint;}return pendingMutationKey;}
    private void clearMutationKey(){pendingMutationKey=null;pendingMutationFingerprint=null;}

    private void populateClosedDrawCount(JComboBox<ClosedDrawOption> sessionBox, JTextField countedField) {
        ClosedDrawOption selected = (ClosedDrawOption) sessionBox.getSelectedItem();
        if (selected != null) {
            countedField.setText(selected.session().countedCash().toPlainString());
        }
    }

    private static void addDialogRow(JPanel panel, GridBagConstraints gbc, int row, String label, Component field) {
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

    private void resetTable(BigDecimal openingCash) {
        updatingTable = true;
        expectedFloatCash = openingCash == null ? BigDecimal.ZERO : openingCash;
        currentFloatCounts = new HashMap<>();
        floatCalculated = false;
        denominationModel.setRowCount(0);
        for (int denomination : DENOMINATIONS) {
            denominationModel.addRow(new Object[]{
                    CURRENCY.format(denomination),
                    0,
                    0,
                    0
            });
        }
        denominationModel.addRow(new Object[]{"TOTAL", CURRENCY.format(BigDecimal.ZERO), CURRENCY.format(BigDecimal.ZERO), CURRENCY.format(BigDecimal.ZERO)});
        updatingTable = false;
        recalculateDenominations();
    }

    private void recalculateDenominations() {
        updatingTable = true;
        BigDecimal counted = BigDecimal.ZERO;
        BigDecimal floatTotal = BigDecimal.ZERO;
        BigDecimal cihTotal = BigDecimal.ZERO;
        for (int row = 0; row < DENOMINATIONS.length; row++) {
            int denomination = DENOMINATIONS[row];
            int quantity = quantityAt(row, 1);
            int floatQty = currentFloatCounts.getOrDefault(denomination, 0);
            int cihQty = Math.max(quantity - floatQty, 0);
            denominationModel.setValueAt(floatQty, row, 2);
            denominationModel.setValueAt(cihQty, row, 3);
            counted = counted.add(BigDecimal.valueOf((long) denomination * quantity));
            floatTotal = floatTotal.add(BigDecimal.valueOf((long) denomination * floatQty));
            cihTotal = cihTotal.add(BigDecimal.valueOf((long) denomination * cihQty));
        }
        countedLabel.setText(CURRENCY.format(counted));
        floatLabel.setText(CURRENCY.format(floatTotal));
        BigDecimal expectedCih = expectedCash.subtract(expectedFloatCash);
        cihLabel.setText(activeSession == null ? "-" : CURRENCY.format(expectedCih));
        BigDecimal variance = counted.subtract(expectedCash);
        varianceLabel.setText(activeSession == null ? "-" : CURRENCY.format(variance));
        varianceLabel.setForeground(activeSession == null
                ? METRIC_TEXT_COLOR
                : variance.compareTo(BigDecimal.ZERO) == 0 ? MATCH_COLOR : VARIANCE_COLOR);
        currentFloatTotal = floatTotal;
        currentCihTotal = cihTotal;
        int totalRow = DENOMINATIONS.length;
        if (denominationModel.getRowCount() > totalRow) {
            denominationModel.setValueAt("TOTAL", totalRow, 0);
            denominationModel.setValueAt(CURRENCY.format(counted), totalRow, 1);
            denominationModel.setValueAt(CURRENCY.format(floatTotal), totalRow, 2);
            denominationModel.setValueAt(CURRENCY.format(cihTotal), totalRow, 3);
        }
        updatingTable = false;
    }

    private void calculateFloat() {
        if (activeSession == null) {
            return;
        }
        if (!allQuantityCellsReady()) {
            JOptionPane.showMessageDialog(
                    this,
                    "Enter 0 or more for every bill quantity before calculating the float.",
                    "Calculate Float",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        currentFloatCounts = calculateFloatCountsFromDrawer();
        floatCalculated = true;
        recalculateDenominations();
    }

    private void clearQuantities() {
        if (activeSession != null) {
            clearQuantityDraft(activeSession.sessionId());
        }
        updatingTable = true;
        for (int row = 0; row < DENOMINATIONS.length; row++) {
            denominationModel.setValueAt(0, row, 1);
            denominationModel.setValueAt(0, row, 2);
            denominationModel.setValueAt(0, row, 3);
        }
        currentFloatCounts = new HashMap<>();
        floatCalculated = false;
        updatingTable = false;
        recalculateDenominations();
    }

    private void saveQuantityDraft() {
        if (activeSession == null || updatingTable) return;
        List<Integer> quantities = new ArrayList<>(DENOMINATIONS.length);
        for (int row = 0; row < DENOMINATIONS.length; row++) {
            quantities.add(quantityAt(row, 1));
        }
        SessionDataCache.put(quantityDraftKey(activeSession.sessionId()),
                new QuantityDraft(List.copyOf(quantities)));
    }

    private void restoreQuantityDraft(long sessionId) {
        SessionDataCache.get(quantityDraftKey(sessionId), QuantityDraft.class, java.time.Duration.ofDays(1))
                .ifPresent(cached -> {
                    List<Integer> quantities = cached.value().quantities();
                    if (quantities.size() != DENOMINATIONS.length) return;
                    updatingTable = true;
                    for (int row = 0; row < DENOMINATIONS.length; row++) {
                        denominationModel.setValueAt(Math.max(quantities.get(row), 0), row, 1);
                    }
                    updatingTable = false;
                    currentFloatCounts = calculateFloatCountsFromDrawer();
                    floatCalculated = true;
                    recalculateDenominations();
                });
    }

    private static void clearQuantityDraft(long sessionId) {
        SessionDataCache.invalidate(quantityDraftKey(sessionId));
    }

    private static String quantityDraftKey(long sessionId) {
        return "balance-draw-draft:" + sessionId;
    }

    private record QuantityDraft(List<Integer> quantities) {
    }

    private BigDecimal countedTotal() {
        BigDecimal total = BigDecimal.ZERO;
        for (int row = 0; row < DENOMINATIONS.length; row++) {
            total = total.add(BigDecimal.valueOf((long) DENOMINATIONS[row] * quantityAt(row, 1)));
        }
        return total;
    }

    private Map<Integer, Integer> calculateFloatCountsFromDrawer() {
        Map<Integer, Integer> configuredCounts = configuredFloatCountsIfAvailable();
        if (!configuredCounts.isEmpty()) {
            return configuredCounts;
        }

        Map<Integer, Integer> exactCounts = calculateExactFloatCounts();
        if (!exactCounts.isEmpty()) {
            return exactCounts;
        }

        Map<Integer, Integer> counts = new HashMap<>();
        int targetAmount = expectedFloatCash
                .setScale(0, RoundingMode.DOWN)
                .max(BigDecimal.ZERO)
                .intValue();
        int remaining = targetAmount;

        for (int denomination : PREFERRED_FLOAT_DENOMINATIONS) {
            int available = quantityForDenomination(denomination);
            int targetQty = Math.min(available, targetFloatQuantity(denomination, targetAmount));
            int qty = Math.min(targetQty, remaining / denomination);
            if (qty > 0) {
                counts.put(denomination, qty);
                remaining -= denomination * qty;
            }
        }

        for (int denomination : FLOAT_FILL_DENOMINATIONS) {
            int available = Math.max(quantityForDenomination(denomination) - counts.getOrDefault(denomination, 0), 0);
            int qty = Math.min(available, remaining / denomination);
            if (qty > 0) {
                counts.merge(denomination, qty, Integer::sum);
                remaining -= denomination * qty;
            }
        }

        return counts;
    }

    private Map<Integer, Integer> configuredFloatCountsIfAvailable() {
        if (CashDrawerService.floatMixTotal(configuredFloatMix).compareTo(expectedFloatCash) != 0) {
            return Map.of();
        }
        Map<Integer, Integer> counts = new HashMap<>();
        for (int denomination : DENOMINATIONS) {
            int targetQty = configuredFloatMix.getOrDefault(denomination, 0);
            if (targetQty <= 0) {
                continue;
            }
            if (quantityForDenomination(denomination) < targetQty) {
                return Map.of();
            }
            counts.put(denomination, targetQty);
        }
        return counts;
    }

    private Map<Integer, Integer> calculateExactFloatCounts() {
        Map<Integer, Integer> targetMix = adjustedTargetFloatMix();
        int targetAmount = expectedFloatCash
                .setScale(0, RoundingMode.DOWN)
                .max(BigDecimal.ZERO)
                .intValue();
        if (targetAmount <= 0 || targetAmount % 20 != 0) {
            return Map.of();
        }

        Map<Integer, Integer> exactCounts = calculateExactFloatCountsForDenominations(
                targetAmount,
                targetMix,
                new int[]{1000, 500, 100, 20}
        );
        if (!exactCounts.isEmpty()) {
            return exactCounts;
        }

        return calculateExactFloatCountsForDenominations(
                targetAmount,
                targetMix,
                new int[]{5000, 2000, 1000, 500, 100, 50, 20}
        );
    }

    private Map<Integer, Integer> calculateExactFloatCountsForDenominations(int targetAmount,
                                                                             Map<Integer, Integer> targetMix,
                                                                             int[] denominations) {
        return searchFloatMix(denominations, 0, targetAmount, targetMix, new HashMap<>(), null);
    }

    private Map<Integer, Integer> searchFloatMix(int[] denominations,
                                                 int index,
                                                 int remaining,
                                                 Map<Integer, Integer> targetMix,
                                                 Map<Integer, Integer> current,
                                                 FloatSearchBest best) {
        if (index >= denominations.length) {
            if (remaining != 0) {
                return best == null ? Map.of() : best.counts();
            }
            FloatSearchBest candidate = new FloatSearchBest(new HashMap<>(current), scoreFloatMix(current, targetMix));
            if (best == null || candidate.score() < best.score()) {
                return candidate.counts();
            }
            return best.counts();
        }

        int denomination = denominations[index];
        int maxQty = Math.min(quantityForDenomination(denomination), remaining / denomination);
        Map<Integer, Integer> bestCounts = best == null ? Map.of() : best.counts();
        long bestScore = best == null ? Long.MAX_VALUE : best.score();
        for (int qty = 0; qty <= maxQty; qty++) {
            if (qty > 0) {
                current.put(denomination, qty);
            } else {
                current.remove(denomination);
            }
            Map<Integer, Integer> result = searchFloatMix(
                    denominations,
                    index + 1,
                    remaining - denomination * qty,
                    targetMix,
                    current,
                    bestScore == Long.MAX_VALUE ? null : new FloatSearchBest(bestCounts, bestScore)
            );
            if (!result.isEmpty()) {
                long resultScore = scoreFloatMix(result, targetMix);
                if (resultScore < bestScore) {
                    bestCounts = result;
                    bestScore = resultScore;
                }
            }
        }
        current.remove(denomination);
        return bestCounts;
    }

    private long scoreFloatMix(Map<Integer, Integer> counts, Map<Integer, Integer> targetMix) {
        long score = 0;
        for (int denomination : DENOMINATIONS) {
            int actual = counts.getOrDefault(denomination, 0);
            int target = targetMix.getOrDefault(denomination, 0);
            score += (long) Math.abs(actual - target) * scoreWeight(denomination);
        }
        return score;
    }

    private int scoreWeight(int denomination) {
        return switch (denomination) {
            case 20 -> 1_000_000;
            case 100 -> 10_000;
            case 500 -> 100;
            case 1000 -> 1;
            case 50 -> 50_000_000;
            case 2000 -> 75_000_000;
            case 5000 -> 100_000_000;
            default -> 1;
        };
    }

    private Map<Integer, Integer> adjustedTargetFloatMix() {
        Map<Integer, Integer> target = new HashMap<>();
        int count20 = roundedAvailableTarget(20, 5);
        putPositive(target, 20, count20);

        int count100For20 = fillLowerShortfallWithDenomination(targetValueThrough(20), valueOf(target), 100);
        int count100 = count100For20 + roundedAvailableTargetAfter(100, 10, count100For20);
        putPositive(target, 100, count100);

        int count500ForLower = fillLowerShortfallWithDenomination(targetValueThrough(100), valueOf(target), 500);
        int count500 = count500ForLower + roundedAvailableTargetAfter(500, 2, count500ForLower);
        putPositive(target, 500, count500);

        int count1000ForLower = fillLowerShortfallWithDenomination(targetValueThrough(500), valueOf(target), 1000);
        int count1000 = count1000ForLower + availableTargetAfter(1000, count1000ForLower);
        putPositive(target, 1000, count1000);

        return target;
    }

    private int roundedAvailableTarget(int denomination, int groupSize) {
        int targetQty = configuredFloatMix.getOrDefault(denomination, 0);
        int availableQty = quantityForDenomination(denomination);
        return roundDown(Math.min(targetQty, availableQty), groupSize);
    }

    private int roundedAvailableTargetAfter(int denomination, int groupSize, int alreadyUsed) {
        int targetQty = Math.max(configuredFloatMix.getOrDefault(denomination, 0) - alreadyUsed, 0);
        int availableQty = Math.max(quantityForDenomination(denomination) - alreadyUsed, 0);
        return roundDown(Math.min(targetQty, availableQty), groupSize);
    }

    private int availableTargetAfter(int denomination, int alreadyUsed) {
        int targetQty = Math.max(configuredFloatMix.getOrDefault(denomination, 0) - alreadyUsed, 0);
        int availableQty = Math.max(quantityForDenomination(denomination) - alreadyUsed, 0);
        return Math.min(targetQty, availableQty);
    }

    private int fillLowerShortfallWithDenomination(int targetValue, int currentValue, int denomination) {
        int shortfall = Math.max(targetValue - currentValue, 0);
        if (shortfall < denomination) {
            return 0;
        }
        int availableQty = quantityForDenomination(denomination);
        return Math.min(availableQty, shortfall / denomination);
    }

    private int targetValueThrough(int denomination) {
        int total = 0;
        for (int current : new int[]{20, 100, 500}) {
            total += configuredFloatMix.getOrDefault(current, 0) * current;
            if (current == denomination) {
                break;
            }
        }
        return total;
    }

    private int valueOf(Map<Integer, Integer> counts) {
        int total = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            total += entry.getKey() * entry.getValue();
        }
        return total;
    }

    private int roundDown(int value, int groupSize) {
        if (groupSize <= 1) {
            return Math.max(value, 0);
        }
        return Math.max(value, 0) / groupSize * groupSize;
    }

    private int targetFloatQuantity(int denomination, int targetAmount) {
        if (targetAmount <= 0) {
            return 0;
        }
        return switch (denomination) {
            case 20, 100, 500, 1000 -> configuredFloatMix.getOrDefault(denomination, targetAmount / denomination);
            default -> 0;
        };
    }

    private void putPositive(Map<Integer, Integer> counts, int denomination, int quantity) {
        if (quantity > 0) {
            counts.put(denomination, quantity);
        }
    }

    private int quantityForDenomination(int denomination) {
        for (int row = 0; row < DENOMINATIONS.length; row++) {
            if (DENOMINATIONS[row] == denomination) {
                return quantityAt(row, 1);
            }
        }
        return 0;
    }

    private int quantityAt(int row, int column) {
        Object value = denominationModel.getValueAt(row, column);
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }
        try {
            return Math.max(Integer.parseInt(String.valueOf(value).trim()), 0);
        } catch (Exception ex) {
            return 0;
        }
    }

    private boolean allQuantityCellsReady() {
        for (int row = 0; row < DENOMINATIONS.length; row++) {
            if (!isQuantityCellReady(row)) {
                return false;
            }
        }
        return true;
    }

    private boolean isQuantityCellReady(int row) {
        Object value = denominationModel.getValueAt(row, 1);
        if (value instanceof Number number) {
            return number.intValue() >= 0;
        }
        String text = String.valueOf(value == null ? "" : value).trim();
        if (text.isEmpty()) {
            return false;
        }
        try {
            return Integer.parseInt(text) >= 0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            JOptionPane.showMessageDialog(this, "Corrected count is required.", "Edit Closed Draw", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        try {
            BigDecimal amount = utils.CurrencyFormatter.normalize(new BigDecimal(value.replace(",", "").replace("$", "").trim()));
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                JOptionPane.showMessageDialog(this, "Corrected count cannot be negative.", "Edit Closed Draw", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            return amount;
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Corrected count must be a valid number.", "Edit Closed Draw", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private String displayName(String value) {
        return value == null || value.isBlank() ? "Unknown" : value.trim();
    }

    private record ClosedDrawOption(CashDrawerSession session) {
        @Override
        public String toString() {
            String opened = session.openedAt() == null ? "Unknown time" : DISPLAY_FORMAT.format(session.openedAt().toLocalDateTime());
            String closed = session.closedAt() == null ? "not closed" : DISPLAY_FORMAT.format(session.closedAt().toLocalDateTime());
            return "#" + session.sessionId()
                    + " / " + (session.drawerName() == null || session.drawerName().isBlank() ? "Drawer" : session.drawerName())
                    + " / " + opened + " - " + closed
                    + " / counted " + CURRENCY.format(session.countedCash())
                    + " / variance " + CURRENCY.format(session.variance());
        }
    }

    private record FloatSearchBest(Map<Integer, Integer> counts, long score) {
    }

    private class CashCountRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
            if (modelRow == DENOMINATIONS.length) {
                component.setFont(component.getFont().deriveFont(Font.BOLD));
                if (column == 2) {
                    boolean matchesFloat = currentFloatTotal.compareTo(expectedFloatCash) == 0;
                    component.setBackground(matchesFloat ? MATCH_COLOR : VARIANCE_COLOR);
                    component.setForeground(Color.WHITE);
                } else if (column == 3) {
                    BigDecimal expectedCih = expectedCash.subtract(expectedFloatCash);
                    boolean matchesCih = currentCihTotal.compareTo(expectedCih) == 0;
                    component.setBackground(matchesCih ? MATCH_COLOR : VARIANCE_COLOR);
                    component.setForeground(Color.WHITE);
                } else if (!isSelected) {
                    component.setBackground(new Color(232, 240, 254));
                    component.setForeground(new Color(37, 99, 235));
                }
            } else if (!isSelected) {
                component.setBackground(Color.WHITE);
                component.setForeground(new Color(31, 41, 55));
            }
            return component;
        }
    }
}
