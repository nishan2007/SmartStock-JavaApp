package ui.screens;

import utils.CurrencyFormatter;
import managers.NavigationManager;
import managers.PermissionManager;
import managers.SessionManager;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.WindowHelper;
import ui.helpers.UiTaskRunner;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class ChangeBasket extends JFrame {
    private static final int[] DENOMINATIONS = {5000, 2000, 1000, 500, 100, 50, 20};
    private static final NumberFormat CURRENCY = CurrencyFormatter.create(Locale.US);

    private final JLabel statusLabel = new JLabel("Loading change basket...");
    private final JLabel storeLabel = new JLabel();
    private final JLabel targetLabel = new JLabel();
    private final JLabel countedLabel = new JLabel();
    private final JLabel varianceLabel = new JLabel();
    private final DefaultTableModel denominationModel;
    private final JTable denominationTable;
    private final JButton updateButton = new JButton("Update Change");
    private final JButton clearButton = new JButton("Clear Qty");
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton backButton = new JButton("Main Menu");

    private BigDecimal targetAmount = BigDecimal.ZERO;
    private BigDecimal countedAmount = BigDecimal.ZERO;
    private boolean updatingTable;
    private String pendingUpdateKey;
    private String pendingUpdateFingerprint;
    private final LoadingStatePanel loadingState = new LoadingStatePanel();

    public ChangeBasket() {
        setTitle("Change Basket");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(760, 600);
        setLayout(new BorderLayout(14, 14));
        setJMenuBar(AppMenuBar.create(this, "ChangeBasket"));

        denominationModel = new DefaultTableModel(new Object[]{"$$", "QTY", "AMOUNT"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1 && row < DENOMINATIONS.length && PermissionManager.hasPermission("BALANCE_DRAWER");
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 1 ? Integer.class : String.class;
            }
        };
        denominationTable = new JTable(denominationModel);
        denominationTable.setRowHeight(30);
        denominationTable.getTableHeader().setReorderingAllowed(false);
        denominationTable.setDefaultRenderer(Object.class, new BasketCountRenderer());
        denominationTable.setDefaultRenderer(Integer.class, new BasketCountRenderer());
        denominationTable.getModel().addTableModelListener(e -> {
            if (!updatingTable && e.getType() == TableModelEvent.UPDATE && e.getColumn() == 1) {
                recalculate();
            }
        });

        JPanel root = new JPanel(new BorderLayout(14, 14));
        root.setBorder(new EmptyBorder(20, 20, 20, 20));
        root.setBackground(new Color(245, 247, 250));
        root.add(buildHeaderPanel(), BorderLayout.NORTH);
        root.add(buildCenterPanel(), BorderLayout.CENTER);
        root.add(buildFooterPanel(), BorderLayout.SOUTH);
        add(root, BorderLayout.CENTER);

        clearButton.addActionListener(e -> clearQuantities());
        updateButton.addActionListener(e -> updateChange());
        refreshButton.addActionListener(e -> loadState());
        backButton.addActionListener(e -> NavigationManager.showMainMenu(this));

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowOpened(java.awt.event.WindowEvent event) {
                WindowHelper.configurePosWindow(ChangeBasket.this);
                loadState();
            }
        });
    }

    private JPanel buildHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        panel.setOpaque(false);
        JLabel titleLabel = new JLabel("Change Basket");
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
        tableScroll.setBorder(BorderFactory.createTitledBorder("Basket Count"));
        panel.add(tableScroll, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildSummaryPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 10, 10));
        panel.setOpaque(false);
        panel.add(metric("Store", storeLabel));
        panel.add(metric("Target", targetLabel));
        panel.add(metric("Counted", countedLabel));
        panel.add(metric("Short / Extra", varianceLabel));
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
        valueLabel.setForeground(new Color(31, 41, 55));
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(valueLabel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildFooterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        panel.setOpaque(false);
        panel.add(updateButton);
        panel.add(clearButton);
        panel.add(loadingState);
        return panel;
    }

    private void loadState() {
        if (!PermissionManager.hasPermission("BALANCE_DRAWER")) {
            statusLabel.setText("You do not have permission to count the change basket.");
            updateButton.setEnabled(false);
            clearButton.setEnabled(false);
            resetTable();
            return;
        }

        CachedUiLoader.load(this, "change-basket:state", LanApiClient.ChangeBasketState.class,
                SessionDataCache.SCREEN_TTL, loadingState,
                LanApiClient::loadChangeBasketState, this::applyState);
    }

    private void applyState(LanApiClient.ChangeBasketState state) {
        targetAmount=state.targetAmount()==null?BigDecimal.ZERO:state.targetAmount();
            storeLabel.setText(state.storeName());targetLabel.setText(CURRENCY.format(targetAmount));statusLabel.setText("Count every bill in the change basket. The total should match the configured target.");
        updateButton.setEnabled(true);clearButton.setEnabled(true);resetTable();
    }

    private void resetTable() {
        updatingTable = true;
        denominationModel.setRowCount(0);
        for (int denomination : DENOMINATIONS) {
            denominationModel.addRow(new Object[]{
                    CURRENCY.format(denomination),
                    0,
                    CURRENCY.format(BigDecimal.ZERO)
            });
        }
        denominationModel.addRow(new Object[]{"TOTAL", CURRENCY.format(BigDecimal.ZERO), CURRENCY.format(BigDecimal.ZERO)});
        updatingTable = false;
        recalculate();
    }

    private void recalculate() {
        updatingTable = true;
        BigDecimal total = BigDecimal.ZERO;
        for (int row = 0; row < DENOMINATIONS.length; row++) {
            int denomination = DENOMINATIONS[row];
            int quantity = quantityAt(row);
            BigDecimal rowAmount = BigDecimal.valueOf((long) denomination * quantity);
            denominationModel.setValueAt(CURRENCY.format(rowAmount), row, 2);
            total = total.add(rowAmount);
        }

        countedAmount = total;
        BigDecimal variance = countedAmount.subtract(targetAmount);
        countedLabel.setText(CURRENCY.format(countedAmount));
        varianceLabel.setText(formatVariance(variance));
        varianceLabel.setForeground(varianceColor(variance));

        int totalRow = DENOMINATIONS.length;
        if (denominationModel.getRowCount() > totalRow) {
            denominationModel.setValueAt("TOTAL", totalRow, 0);
            denominationModel.setValueAt(CURRENCY.format(countedAmount), totalRow, 1);
            denominationModel.setValueAt(formatVariance(variance), totalRow, 2);
        }
        updatingTable = false;
    }

    private void clearQuantities() {
        updatingTable = true;
        for (int row = 0; row < DENOMINATIONS.length; row++) {
            denominationModel.setValueAt(0, row, 1);
            denominationModel.setValueAt(CURRENCY.format(BigDecimal.ZERO), row, 2);
        }
        updatingTable = false;
        recalculate();
    }

    private void updateChange() {
        Integer locationId = SessionManager.getCurrentLocationId();
        if (locationId == null) {
            JOptionPane.showMessageDialog(this, "Select a store before saving a change basket update.", "Change Basket", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!allQuantityCellsReady()) {
            JOptionPane.showMessageDialog(this, "Enter 0 or more for every bill quantity before updating change.", "Change Basket", JOptionPane.WARNING_MESSAGE);
            return;
        }
        recalculate();
        BigDecimal variance = countedAmount.subtract(targetAmount);
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Save this change basket update?\n\nTarget: " + CURRENCY.format(targetAmount)
                        + "\nCounted: " + CURRENCY.format(countedAmount)
                        + "\nShort / Extra: " + formatVariance(variance),
                "Update Change",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            Map<Integer,Integer>counts=denominationCounts();String fingerprint=counts.toString()+"|"+targetAmount;
            if(pendingUpdateKey==null||!fingerprint.equals(pendingUpdateFingerprint)){pendingUpdateFingerprint=fingerprint;pendingUpdateKey=UUID.randomUUID().toString();}
            String mutationKey=pendingUpdateKey;
            UiTaskRunner.submit(this,"change-basket.update",()->LanApiClient.updateChangeBasket(counts,mutationKey),updateId->{pendingUpdateKey=null;pendingUpdateFingerprint=null;
            SessionDataCache.invalidate("change-basket:state");
            JOptionPane.showMessageDialog(
                    this,
                    "Change basket update saved.\nUpdate ID: " + updateId
                            + "\nCounted: " + CURRENCY.format(countedAmount)
                            + "\nShort / Extra: " + formatVariance(variance),
                    "Update Change",
                    JOptionPane.INFORMATION_MESSAGE
            );
            },ex->JOptionPane.showMessageDialog(this,"Failed to save change basket update: "+ex.getMessage(),"Change Basket",JOptionPane.ERROR_MESSAGE));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to save change basket update: " + ex.getMessage(), "Change Basket", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Map<Integer, Integer> denominationCounts() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int row = 0; row < DENOMINATIONS.length; row++) {
            counts.put(DENOMINATIONS[row], quantityAt(row));
        }
        return counts;
    }

    private int quantityAt(int row) {
        Object value = denominationModel.getValueAt(row, 1);
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
            Object value = denominationModel.getValueAt(row, 1);
            if (value instanceof Number number) {
                if (number.intValue() < 0) {
                    return false;
                }
                continue;
            }
            String text = String.valueOf(value == null ? "" : value).trim();
            if (text.isEmpty()) {
                return false;
            }
            try {
                if (Integer.parseInt(text) < 0) {
                    return false;
                }
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }

    private String formatVariance(BigDecimal variance) {
        if (variance.compareTo(BigDecimal.ZERO) == 0) {
            return CURRENCY.format(BigDecimal.ZERO);
        }
        String label = variance.compareTo(BigDecimal.ZERO) < 0 ? "Short " : "Extra ";
        return label + CURRENCY.format(variance.abs());
    }

    private Color varianceColor(BigDecimal variance) {
        if (variance.compareTo(BigDecimal.ZERO) == 0) {
            return new Color(22, 101, 52);
        }
        return new Color(190, 38, 20);
    }

    private String displayStore() {
        String locationName = SessionManager.getCurrentLocationName();
        return locationName == null || locationName.isBlank() ? "Current Store" : locationName.trim();
    }

    private class BasketCountRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            int modelRow = table.convertRowIndexToModel(row);
            setHorizontalAlignment(column == 0 ? SwingConstants.LEFT : SwingConstants.RIGHT);
            if (modelRow == DENOMINATIONS.length) {
                component.setFont(component.getFont().deriveFont(Font.BOLD));
                if (column == 2) {
                    component.setBackground(varianceColor(countedAmount.subtract(targetAmount)));
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
