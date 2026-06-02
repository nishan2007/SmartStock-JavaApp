package ui.screens;

import data.DB;
import services.SyncLockService;
import services.SyncSchemaInstaller;
import services.SyncServiceStatusService;
import services.SyncWorker;
import ui.helpers.ThemeManager;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

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
            SwingWorker<SyncWorker.SyncStatus, Void> worker = new SwingWorker<>() {
                @Override
                protected SyncWorker.SyncStatus doInBackground() {
                    return SyncWorker.runOnceNow();
                }

                @Override
                protected void done() {
                    runNowButton.setEnabled(true);
                    runNowButton.setText("Run Sync Now");
                    try {
                        SyncWorker.SyncStatus status = get();
                        if (status.currentSync() != null && status.currentSync().running()) {
                            JOptionPane.showMessageDialog(
                                    SyncStatus.this,
                                    "Sync already running by " + status.currentSync().ownerLabel()
                                            + "\nStarted: " + status.currentSync().acquiredAt(),
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
        SyncWorker.SyncStatus status = SyncWorker.latestStatus();
        SyncLockService.LockInfo lock = status.currentSync();
        SyncServiceStatusService.ServiceInfo service = status.serviceInfo();
        String backgroundStatus = service == null ? "Unknown" : service.status();
        String appSyncStatus = SyncWorker.isStarted() ? "Running while UI is open" : "Not running";
        statusLabel.setText("Cloud: " + (status.cloudReachable() ? "Online" : "Offline")
                + " | Background Service: " + backgroundStatus
                + " | In-App Sync: " + appSyncStatus
                + " | Current Sync: " + (lock != null && lock.running() ? "Running by " + lock.ownerLabel() : "Idle")
                + " | Pending: " + status.pendingCount()
                + " | Failed: " + status.failedCount()
                + " | Conflicts: " + status.conflictCount()
                + " | Last success: " + (status.lastSuccess() == null ? "Never" : status.lastSuccess())
                + (status.lastError() == null ? "" : " | Error: " + status.lastError()));
        conflictModel.setRowCount(0);
        auditModel.setRowCount(0);
        try (Connection conn = DB.getConnection()) {
            SyncSchemaInstaller.ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                     SELECT conflict_id, event_type, conflict_type, status, created_at
                     FROM sync_conflicts
                     WHERE status = 'OPEN'
                     ORDER BY created_at DESC
                     LIMIT 200
                     """);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    conflictModel.addRow(new Object[]{
                            rs.getLong("conflict_id"),
                            rs.getString("event_type"),
                            rs.getString("conflict_type"),
                            rs.getString("status"),
                            rs.getTimestamp("created_at")
                    });
                }
            }
            loadAuditRows(conn);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Sync Status", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadAuditRows(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("""
                 SELECT created_at, action_type, table_name, local_id_before, local_id_after,
                        cloud_id, match_key, status, details
                 FROM sync_audit_log
                 ORDER BY created_at DESC, sync_audit_id DESC
                 LIMIT 300
                 """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                auditModel.addRow(new Object[]{
                        rs.getTimestamp("created_at"),
                        rs.getString("action_type"),
                        rs.getString("table_name"),
                        rs.getString("local_id_before"),
                        rs.getString("local_id_after"),
                        rs.getString("cloud_id"),
                        rs.getString("match_key"),
                        rs.getString("status"),
                        rs.getString("details")
                });
            }
        }
    }

    private void resolveSelected(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        long conflictId = Long.parseLong(String.valueOf(conflictModel.getValueAt(table.convertRowIndexToModel(row), 0)));
        try (Connection conn = DB.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                     UPDATE sync_conflicts
                     SET status = 'RESOLVED',
                         resolved_at = CURRENT_TIMESTAMP,
                         resolution_notes = 'Resolved from SmartStock sync status screen'
                     WHERE conflict_id = ?
                     """)) {
            ps.setLong(1, conflictId);
            ps.executeUpdate();
            refresh();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Resolve Conflict", JOptionPane.ERROR_MESSAGE);
        }
    }
}
