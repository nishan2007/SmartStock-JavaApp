package ui.screens;

import services.LanApiClient;
import ui.helpers.ThemeManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;

public class SyncStatus extends JFrame {
    private final JLabel statusLabel = new JLabel();
    private final DefaultTableModel conflictModel = new DefaultTableModel(
            new Object[]{"ID", "Event", "Type", "Status", "Created"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final DefaultTableModel auditModel = new DefaultTableModel(
            new Object[]{"Time", "Action", "Table", "Before", "After", "Cloud", "Match", "Status", "Details"}, 0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    public SyncStatus() {
        super("Local Sync Status");
        setSize(760, 460);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JTable conflictsTable = new JTable(conflictModel);
        JTable auditTable = new JTable(auditModel);
        JButton refreshButton = new JButton("Refresh");
        JButton runNowButton = new JButton("Run Sync Now");
        JButton resolveButton = new JButton("Mark Selected Resolved");
        refreshButton.addActionListener(e -> refresh());
        runNowButton.addActionListener(e -> {
            runNowButton.setEnabled(false);
            runNowButton.setText("Syncing...");
            SwingWorker<LanApiClient.SyncStatusSnapshot, Void> worker = new SwingWorker<>() {
                @Override
                protected LanApiClient.SyncStatusSnapshot doInBackground() throws Exception {
                    return LanApiClient.runSyncNow();
                }

                @Override
                protected void done() {
                    runNowButton.setEnabled(true);
                    runNowButton.setText("Run Sync Now");
                    try {
                        LanApiClient.SyncStatusSnapshot status = get();
                        if (status.lockRunning()) {
                            JOptionPane.showMessageDialog(
                                    SyncStatus.this,
                                    "Sync already running by " + status.lockOwner()
                                            + "\nStarted: " + instant(status.lockAcquiredEpochMillis()),
                                    "Manual Sync",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        }
                    } catch (Exception ex) {
                        JOptionPane.showMessageDialog(SyncStatus.this, ex.getMessage(), "Manual Sync", JOptionPane.ERROR_MESSAGE);
                    }
                    refresh();
                }
            };
            worker.execute();
        });
        resolveButton.addActionListener(e -> resolveSelected(conflictsTable));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(runNowButton);
        buttons.add(refreshButton);
        buttons.add(resolveButton);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(statusLabel, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Conflicts", new JScrollPane(conflictsTable));
        tabs.addTab("Audit History", new JScrollPane(auditTable));
        root.add(tabs, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        setContentPane(root);
        ThemeManager.applyToWindow(this);
        refresh();
    }

    private void refresh() {
        conflictModel.setRowCount(0);
        auditModel.setRowCount(0);
        try {
            LanApiClient.SyncStatusSnapshot status = LanApiClient.loadSyncStatus();
            if (status.conflicts() != null) for (LanApiClient.SyncConflict conflict : status.conflicts()) {
                conflictModel.addRow(new Object[]{conflict.conflictId(), conflict.eventType(),
                        conflict.conflictType(), conflict.status(), instant(conflict.createdAtEpochMillis())});
            }
            if (status.audits() != null) for (LanApiClient.SyncAudit audit : status.audits()) {
                auditModel.addRow(new Object[]{instant(audit.createdAtEpochMillis()), audit.actionType(),
                        audit.tableName(), audit.localIdBefore(), audit.localIdAfter(), audit.cloudId(),
                        audit.matchKey(), audit.status(), audit.details()});
            }
            statusLabel.setText("Cloud: " + (status.cloudReachable() ? "Online" : "Offline")
                    + " | Background Service: " + status.serviceStatus()
                    + " | Server Sync: " + (status.serverWorkerStarted() ? "Running" : "Not running")
                    + " | Current Sync: " + (status.lockRunning() ? "Running by " + status.lockOwner() : "Idle")
                    + " | Pending: " + status.pendingCount()
                    + " | Failed: " + status.failedCount()
                    + " | Conflicts: " + status.conflictCount()
                    + " | Last success: " + instantOrNever(status.lastSuccessEpochMillis())
                    + (status.lastError() == null ? "" : " | Error: " + status.lastError()));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Sync Status", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resolveSelected(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        long conflictId = Long.parseLong(String.valueOf(conflictModel.getValueAt(table.convertRowIndexToModel(row), 0)));
        try {
            LanApiClient.resolveSyncConflict(conflictId);
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Resolve Conflict", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static Object instant(long epochMillis) {
        return epochMillis <= 0 ? "" : Instant.ofEpochMilli(epochMillis);
    }

    private static Object instantOrNever(long epochMillis) {
        return epochMillis <= 0 ? "Never" : Instant.ofEpochMilli(epochMillis);
    }
}
