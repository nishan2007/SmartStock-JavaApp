package ui.screens;

import data.DatabaseConfig;
import services.LanApiClient;
import ui.helpers.ThemeManager;
import ui.components.LoadingStatePanel;
import ui.helpers.CachedUiLoader;
import ui.helpers.SessionDataCache;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.UiTaskRunner;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class SyncStatus extends JFrame {
    private final JLabel statusLabel = new JLabel();
    private final JLabel syncTimingLabel = new JLabel();
    private static final DateTimeFormatter SYNC_TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy h:mm:ss a");
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
    private final LoadingStatePanel loadingState=new LoadingStatePanel();

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
            UiTaskRunner.submit(this,"sync-status.run",LanApiClient::runSyncNow,status->{
                    runNowButton.setEnabled(true);
                    runNowButton.setText("Run Sync Now");
                        if (status.lockRunning()) {
                            JOptionPane.showMessageDialog(
                                    SyncStatus.this,
                                    "Sync already running by " + status.lockOwner()
                                            + "\nStarted: " + instant(status.lockAcquiredEpochMillis()),
                                    "Manual Sync",
                                    JOptionPane.INFORMATION_MESSAGE
                            );
                        }
                    refresh();
                },ex->{runNowButton.setEnabled(true);runNowButton.setText("Run Sync Now");JOptionPane.showMessageDialog(SyncStatus.this,ex.getMessage(),"Manual Sync",JOptionPane.ERROR_MESSAGE);});
        });
        resolveButton.addActionListener(e -> resolveSelected(conflictsTable));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(runNowButton);
        buttons.add(refreshButton);
        buttons.add(resolveButton);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel statusHeader = new JPanel();
        statusHeader.setLayout(new BoxLayout(statusHeader, BoxLayout.Y_AXIS));
        statusHeader.add(statusLabel);
        statusHeader.add(Box.createVerticalStrut(5));
        statusHeader.add(syncTimingLabel);
        root.add(statusHeader, BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Conflicts", new JScrollPane(conflictsTable));
        tabs.addTab("Audit History", new JScrollPane(auditTable));
        root.add(tabs, BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.add(loadingState,BorderLayout.NORTH);footer.add(buttons,BorderLayout.SOUTH);root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        ThemeManager.applyToWindow(this);
        refresh();
    }

    private void refresh() {
        CachedUiLoader.load(this,"sync-status:snapshot",LanApiClient.SyncStatusSnapshot.class,
                SessionDataCache.SCREEN_TTL,loadingState,LanApiClient::loadSyncStatus,this::applyStatus);
    }

    private void applyStatus(LanApiClient.SyncStatusSnapshot status) {
            conflictModel.setRowCount(0);auditModel.setRowCount(0);
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
                    + (status.lastError() == null ? "" : " | Error: " + status.lastError()));
            long intervalMillis = Math.max(15, DatabaseConfig.load().syncIntervalSeconds()) * 1_000L;
            long nextSync = nextSyncEpochMillis(status.lastSuccessEpochMillis(), intervalMillis);
            syncTimingLabel.setText("Last Sync: " + displayTimeOrNever(status.lastSuccessEpochMillis())
                    + "   |   Next Sync: " + displayNextSync(nextSync));
    }

    private void resolveSelected(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        long conflictId = Long.parseLong(String.valueOf(conflictModel.getValueAt(table.convertRowIndexToModel(row), 0)));
        UiTaskRunner.submit(this,"sync-status.resolve",()->{LanApiClient.resolveSyncConflict(conflictId);return Boolean.TRUE;},ignored->{SessionDataCache.invalidate("sync-status:");refresh();},ex->JOptionPane.showMessageDialog(this,ex.getMessage(),"Resolve Conflict",JOptionPane.ERROR_MESSAGE));
    }

    private static Object instant(long epochMillis) {
        return epochMillis <= 0 ? "" : Instant.ofEpochMilli(epochMillis);
    }

    static long nextSyncEpochMillis(long lastSuccessEpochMillis, long intervalMillis) {
        if (lastSuccessEpochMillis <= 0 || intervalMillis <= 0) return 0;
        return lastSuccessEpochMillis + intervalMillis;
    }

    private static String displayTimeOrNever(long epochMillis) {
        return epochMillis <= 0 ? "Never" : displayTime(epochMillis);
    }

    private static String displayNextSync(long epochMillis) {
        if (epochMillis <= 0) return "Waiting for first sync";
        return epochMillis <= System.currentTimeMillis()
                ? "Due now (scheduled " + displayTime(epochMillis) + ")"
                : displayTime(epochMillis);
    }

    private static String displayTime(long epochMillis) {
        return SYNC_TIME_FORMAT.format(Instant.ofEpochMilli(epochMillis)
                .atZone(StoreTimeZoneHelper.getStoreZone()));
    }
}
