package ui.screens;

import services.ServerSupabaseMigrationRunner;
import services.SupabaseProjectConfig;
import ui.helpers.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

/** Collects the one-time Supabase database credential without persisting it. */
final class SupabaseProjectInitializerDialog extends JDialog {
    private final JTextField connectionField = new JTextField();
    private final JPasswordField passwordField = new JPasswordField();
    private final JLabel statusLabel = new JLabel("Paste the Direct or Session Pooler connection from Supabase.");
    private final JButton runButton = new JButton("Initialize / Update Project");
    private final Runnable onComplete;

    SupabaseProjectInitializerDialog(Window owner, Runnable onComplete) {
        super(owner, "Initialize Supabase Project", ModalityType.APPLICATION_MODAL);
        this.onComplete = onComplete == null ? () -> { } : onComplete;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(720, 330);
        setLocationRelativeTo(owner);

        connectionField.setToolTipText(
                "Use a Supabase Direct or Session Pooler connection on port 5432.");
        passwordField.setToolTipText("Used only for this initialization and immediately cleared.");

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(7, 7, 7, 7);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 0;
        gbc.gridx = 0;
        gbc.weightx = 0;
        form.add(new JLabel("Connection string:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(connectionField, gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weightx = 0;
        form.add(new JLabel("Database password:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        form.add(passwordField, gbc);

        JTextArea explanation = new JTextArea(
                "SmartStock will install only packaged, checksum-verified migrations over TLS. "
                        + "The connection and password are not saved. Normal operation continues "
                        + "through the Supabase HTTPS API.");
        explanation.setEditable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setOpaque(false);

        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        runButton.addActionListener(event -> runMigrations());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(cancel);
        actions.add(runButton);

        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        root.add(explanation, BorderLayout.NORTH);
        root.add(form, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout());
        footer.add(statusLabel, BorderLayout.CENTER);
        footer.add(actions, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        ThemeManager.applyToWindow(this);
    }

    private void runMigrations() {
        char[] password = passwordField.getPassword();
        String connection = connectionField.getText().trim();
        if (password.length == 0) {
            Arrays.fill(password, '\0');
            JOptionPane.showMessageDialog(this, "Enter the Supabase database password.",
                    "Initialize Supabase", JOptionPane.WARNING_MESSAGE);
            return;
        }
        runButton.setEnabled(false);
        connectionField.setEnabled(false);
        passwordField.setEnabled(false);
        statusLabel.setText("Installing packaged Supabase migrations...");
        SwingWorker<ServerSupabaseMigrationRunner.Result, Void> worker = new SwingWorker<>() {
            @Override
            protected ServerSupabaseMigrationRunner.Result doInBackground() throws Exception {
                return ServerSupabaseMigrationRunner.migrate(
                        connection, password, SupabaseProjectConfig.load());
            }

            @Override
            protected void done() {
                Arrays.fill(password, '\0');
                passwordField.setText("");
                connectionField.setText("");
                try {
                    ServerSupabaseMigrationRunner.Result result = get();
                    statusLabel.setText(result.message());
                    JOptionPane.showMessageDialog(SupabaseProjectInitializerDialog.this,
                            result.message()
                                    + "\n\nSchema, Storage support, secured RPCs, and migration history were verified.",
                            "Supabase Project Ready", JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                    onComplete.run();
                } catch (Exception ex) {
                    statusLabel.setText("Initialization stopped without saving the database password.");
                    runButton.setEnabled(true);
                    connectionField.setEnabled(true);
                    passwordField.setEnabled(true);
                    JOptionPane.showMessageDialog(SupabaseProjectInitializerDialog.this,
                            rootCauseMessage(ex), "Initialize Supabase",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
