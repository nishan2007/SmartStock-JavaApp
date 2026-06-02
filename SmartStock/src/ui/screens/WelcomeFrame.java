package ui.screens;

import data.DB;
import data.DatabaseConfig;
import managers.SupabaseSessionManager;
import ui.helpers.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.sql.Connection;

public class WelcomeFrame extends JFrame {

    private final JLabel statusLabel = new JLabel("Status: Not checked");
    private final JLabel modeLabel = new JLabel();
    private final JButton testBtn = new JButton("Test Database Connection");
    private final JButton setupBtn = new JButton("Database Setup");
    private final JButton syncStatusBtn = new JButton("Sync Status");
    private final JButton continueBtn = new JButton("Continue");

    public WelcomeFrame() {
        super("SmartStock");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(520, 260);
        setLocationRelativeTo(null);

        JLabel title = new JLabel("Welcome to SmartStock");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22f));

        JLabel subtitle = new JLabel("Inventory + Sales Management System");
        subtitle.setFont(subtitle.getFont().deriveFont(14f));

        continueBtn.setEnabled(false);

        refreshModeLabel();
        testBtn.addActionListener(e -> testConnection());
        setupBtn.addActionListener(e -> {
            new DatabaseSetup(this).setVisible(true);
            SwingUtilities.invokeLater(this::refreshModeLabel);
        });
        syncStatusBtn.addActionListener(e -> new SyncStatus().setVisible(true));
        continueBtn.addActionListener(e -> {
            new Login().setVisible(true);
            dispose();

        });

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(title);
        top.add(Box.createVerticalStrut(6));
        top.add(subtitle);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        modeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        testBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        setupBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        syncStatusBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        continueBtn.setAlignmentX(Component.LEFT_ALIGNMENT);

        center.add(modeLabel);
        center.add(Box.createVerticalStrut(8));
        center.add(statusLabel);
        center.add(Box.createVerticalStrut(10));
        center.add(testBtn);
        center.add(Box.createVerticalStrut(10));
        center.add(setupBtn);
        center.add(Box.createVerticalStrut(10));
        center.add(syncStatusBtn);
        center.add(Box.createVerticalStrut(10));
        center.add(continueBtn);

        JPanel root = new JPanel(new BorderLayout(16, 16));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.add(top, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);

        setContentPane(root);
        ThemeManager.applyToWindow(this);
        SwingUtilities.invokeLater(this::continueIfStoredSessionExists);
    }

    private void refreshModeLabel() {
        DatabaseConfig config = DatabaseConfig.load();
        String dbText = config.jdbcUrl() == null || config.jdbcUrl().isBlank() ? "Not configured" : config.jdbcUrl();
        modeLabel.setText("Mode: " + config.mode() + " | DB: " + dbText);
    }

    private void continueIfStoredSessionExists() {
        if (!SupabaseSessionManager.hasPersistedSession()) {
            return;
        }
        try (Connection ignored = DB.getConnection()) {
            // Continue only after the configured database is reachable.
        } catch (Exception ex) {
            statusLabel.setText("Status: Database setup required");
            testBtn.setEnabled(true);
            continueBtn.setEnabled(false);
            JOptionPane.showMessageDialog(
                    this,
                    "Saved sign-in was found, but the database is not ready yet.\n\n"
                            + getRootCauseMessage(ex)
                            + "\n\nOpen Database Setup, save the connection, then test it.",
                    "Database Setup Required",
                    JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        statusLabel.setText("Status: Restoring saved sign-in...");
        testBtn.setEnabled(false);
        continueBtn.setEnabled(false);
        new Login();
        dispose();
    }

    private String getRootCauseMessage(Exception ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName()
                : cause.getMessage();
    }

    private void testConnection() {
        statusLabel.setText("Status: Checking...");
        testBtn.setEnabled(false);

        // Run DB work off the UI thread
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                try (Connection conn = DB.getConnection()) {
                    // If we got here, connection worked
                } catch (Exception ex) {
                    // Re-throw to handle in done()
                    throw new RuntimeException(ex);
                }
                return null;
            }

            @Override
            protected void done() {
                testBtn.setEnabled(true);
                try {
                    get(); // will throw if connection failed
                    statusLabel.setText("Status: Connected");
                    continueBtn.setEnabled(true);
                } catch (Exception ex) {
                    statusLabel.setText("Status: Failed");
                    continueBtn.setEnabled(false);
                    JOptionPane.showMessageDialog(WelcomeFrame.this,
                            "Database connection failed:\n" + ex.getCause().getMessage(),
                            "Connection Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.execute();
    }
}
