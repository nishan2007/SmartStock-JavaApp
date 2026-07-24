package ui.screens;

import services.ServerFirstAdministratorService;
import ui.helpers.ThemeManager;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.List;

/** Guided first-administrator transfer or creation after Supabase initialization. */
final class FirstAdministratorSetupDialog extends JDialog {
    private final JTabbedPane choices = new JTabbedPane();
    private final JComboBox<String> sourceType =
            new JComboBox<>(new String[]{"Development profile", "Other source database"});
    private final JTextField sourceUrl =
            new JTextField("jdbc:postgresql://127.0.0.1:5432/smartstock_dev");
    private final JTextField sourceUser = new JTextField();
    private final JPasswordField sourcePassword = new JPasswordField();
    private final JComboBox<ServerFirstAdministratorService.Identity> sourceAdmins = new JComboBox<>();
    private final JButton loadAdminsButton = new JButton("Load Active Administrators");
    private final JTextField username = new JTextField();
    private final JTextField email = new JTextField();
    private final JTextField displayName = new JTextField();
    private final JPasswordField password = new JPasswordField();
    private final JPasswordField confirmPassword = new JPasswordField();
    private final JPasswordField newPassword = new JPasswordField();
    private final JPasswordField newConfirmPassword = new JPasswordField();
    private final JButton finishButton = new JButton("Create First Administrator");
    private final JLabel status = new JLabel("Choose how to create the first production administrator.");
    private final Runnable onComplete;

    FirstAdministratorSetupDialog(Window owner, Runnable onComplete) {
        super(owner, "Create First Administrator", ModalityType.APPLICATION_MODAL);
        this.onComplete = onComplete == null ? () -> { } : onComplete;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(780, 610);
        setLocationRelativeTo(owner);

        choices.addTab("Transfer Existing Administrator", transferPanel());
        choices.addTab("Create New Administrator", newUserPanel());
        choices.addChangeListener(event -> finishButton.setText(choices.getSelectedIndex() == 0
                ? "Transfer First Administrator" : "Create First Administrator"));
        sourceType.addActionListener(event -> refreshSourceFields());
        loadAdminsButton.addActionListener(event -> loadAdministrators());
        finishButton.addActionListener(event -> finish());

        JButton cancel = new JButton("Close");
        cancel.addActionListener(event -> dispose());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(cancel);
        actions.add(finishButton);

        JPanel footer = new JPanel(new BorderLayout());
        footer.add(status, BorderLayout.NORTH);
        footer.add(actions, BorderLayout.SOUTH);
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        root.add(choices, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        refreshSourceFields();
        ThemeManager.applyToWindow(this);
    }

    private JPanel transferPanel() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = constraints();
        int row = 0;
        row = add(panel, gbc, row, "Source:", sourceType);
        row = add(panel, gbc, row, "Source JDBC URL:", sourceUrl);
        row = add(panel, gbc, row, "Source database user:", sourceUser);
        row = add(panel, gbc, row, "Source database password:", sourcePassword);
        row = add(panel, gbc, row, "", loadAdminsButton);
        row = add(panel, gbc, row, "Existing administrator:", sourceAdmins);
        row = add(panel, gbc, row, "Current password:", password);
        row = add(panel, gbc, row, "Confirm password:", confirmPassword);
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        JTextArea note = note("""
                Only active ADMIN users are listed. SmartStock preserves the username,
                email, display name, role, badge ID, and badge verifier exactly. It does
                not read the old password, Auth UUID, sessions, offline cache, PIN, or
                employee history. The entered password creates a separate production
                Auth identity.
                """);
        panel.add(note, gbc);
        return panel;
    }

    private JPanel newUserPanel() {
        JPanel panel = formPanel();
        GridBagConstraints gbc = constraints();
        int row = 0;
        row = add(panel, gbc, row, "Username:", username);
        row = add(panel, gbc, row, "Email:", email);
        row = add(panel, gbc, row, "Display name:", displayName);
        row = add(panel, gbc, row, "Password:", newPassword);
        row = add(panel, gbc, row, "Confirm password:", newConfirmPassword);
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 2;
        panel.add(note("""
                This creates an additional production administrator. It does not count
                as one of the three planned existing-user transfers; migrating all three
                existing users later will result in four production users. Badge
                enrollment happens after the first successful online login.
                """), gbc);
        return panel;
    }

    private void refreshSourceFields() {
        boolean other = sourceType.getSelectedIndex() == 1;
        sourceUrl.setEnabled(other);
        sourceUser.setEnabled(other);
        sourcePassword.setEnabled(other);
        sourceAdmins.removeAllItems();
    }

    private void loadAdministrators() {
        loadAdminsButton.setEnabled(false);
        status.setText("Reading active administrators from the source database...");
        char[] sourceSecret = sourcePassword.getPassword();
        SwingWorker<List<ServerFirstAdministratorService.Identity>, Void> worker =
                new SwingWorker<>() {
                    @Override
                    protected List<ServerFirstAdministratorService.Identity> doInBackground()
                            throws Exception {
                        return sourceType.getSelectedIndex() == 0
                                ? ServerFirstAdministratorService.listDevelopmentAdministrators()
                                : ServerFirstAdministratorService.listTemporaryAdministrators(
                                sourceUrl.getText().trim(), sourceUser.getText().trim(),
                                sourceSecret);
                    }

                    @Override
                    protected void done() {
                        Arrays.fill(sourceSecret, '\0');
                        sourcePassword.setText("");
                        loadAdminsButton.setEnabled(true);
                        try {
                            List<ServerFirstAdministratorService.Identity> identities = get();
                            sourceAdmins.removeAllItems();
                            identities.forEach(sourceAdmins::addItem);
                            status.setText(identities.isEmpty()
                                    ? "No active ADMIN users were found."
                                    : "Select the administrator to transfer.");
                        } catch (Exception ex) {
                            status.setText("The source administrators could not be loaded.");
                            JOptionPane.showMessageDialog(
                                    FirstAdministratorSetupDialog.this,
                                    rootCauseMessage(ex), "Load Administrators",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
        worker.execute();
    }

    private void finish() {
        ServerFirstAdministratorService.Identity identity;
        char[] entered;
        char[] confirmation;
        try {
            if (choices.getSelectedIndex() == 0) {
                identity = (ServerFirstAdministratorService.Identity) sourceAdmins.getSelectedItem();
                if (identity == null) throw new IllegalArgumentException(
                        "Load and select an active administrator.");
                entered = password.getPassword();
                confirmation = confirmPassword.getPassword();
            } else {
                int answer = JOptionPane.showConfirmDialog(this,
                        "This creates an additional administrator that is not one of the "
                                + "three existing-user transfers.\n\nContinue?",
                        "Additional Production User", JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (answer != JOptionPane.YES_OPTION) return;
                identity = ServerFirstAdministratorService.newAdministrator(
                        username.getText(), email.getText(), displayName.getText());
                entered = newPassword.getPassword();
                confirmation = newConfirmPassword.getPassword();
            }
            if (!Arrays.equals(entered, confirmation)) {
                throw new IllegalArgumentException("The passwords do not match.");
            }
            if (entered.length < 8) {
                throw new IllegalArgumentException("Password must contain at least 8 characters.");
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, rootCauseMessage(ex),
                    "First Administrator", JOptionPane.WARNING_MESSAGE);
            return;
        }

        finishButton.setEnabled(false);
        status.setText("Creating and linking the production administrator...");
        ServerFirstAdministratorService.Identity selected = identity;
        SwingWorker<ServerFirstAdministratorService.BootstrapResult, Void> worker =
                new SwingWorker<>() {
                    @Override
                    protected ServerFirstAdministratorService.BootstrapResult doInBackground()
                            throws Exception {
                        return ServerFirstAdministratorService.bootstrap(selected, entered);
                    }

                    @Override
                    protected void done() {
                        Arrays.fill(entered, '\0');
                        Arrays.fill(confirmation, '\0');
                        clearPasswordFields();
                        try {
                            var result = get();
                            JOptionPane.showMessageDialog(
                                    FirstAdministratorSetupDialog.this,
                                    result.message() + "\n\nProduction user ID: "
                                            + result.userId(),
                                    "First Administrator Ready",
                                    JOptionPane.INFORMATION_MESSAGE);
                            dispose();
                            onComplete.run();
                        } catch (Exception ex) {
                            finishButton.setEnabled(true);
                            status.setText("Administrator setup stopped; it can be retried safely.");
                            JOptionPane.showMessageDialog(
                                    FirstAdministratorSetupDialog.this,
                                    rootCauseMessage(ex), "First Administrator",
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
        worker.execute();
    }

    private void clearPasswordFields() {
        password.setText("");
        confirmPassword.setText("");
        newPassword.setText("");
        newConfirmPassword.setText("");
    }

    private static JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        return panel;
    }

    private static GridBagConstraints constraints() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        return gbc;
    }

    private static int add(JPanel panel, GridBagConstraints gbc, int row,
                           String label, JComponent component) {
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(new JLabel(label), gbc);
        gbc.gridx = 1;
        gbc.weightx = 1;
        panel.add(component, gbc);
        return row + 1;
    }

    private static JTextArea note(String text) {
        JTextArea area = new JTextArea(text.strip());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        return area;
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
