package services;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;

public final class ManagerApprovalService {
    private ManagerApprovalService() {
    }

    public static ApprovalResult requestApproval(Component parent, String requiredPermission, String actionLabel, String reasonPrompt) {
        return requestApproval(parent, requiredPermission, actionLabel, reasonPrompt, null, null);
    }

    public static ApprovalResult requestApproval(Component parent, String requiredPermission,
                                                  String actionLabel, String reasonPrompt,
                                                  String resourceLabel, String resourceIdentity) {
        JTextField loginField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JTextArea reasonArea = new JTextArea(3, 28);
        reasonArea.setLineWrap(true);
        reasonArea.setWrapStyleWord(true);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;

        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Manager Username / Email / Badge ID:"), gbc);
        gbc.gridy = 1;
        panel.add(loginField, gbc);
        gbc.gridy = 2;
        panel.add(new JLabel("Manager Password:"), gbc);
        gbc.gridy = 3;
        panel.add(passwordField, gbc);
        gbc.gridy = 4;
        if (resourceLabel != null && !resourceLabel.isBlank()) {
            panel.add(new JLabel(resourceLabel), gbc);
            gbc.gridy = 5;
        }
        panel.add(new JLabel(reasonPrompt), gbc);
        gbc.gridy++;
        panel.add(new JScrollPane(reasonArea), gbc);

        int result = JOptionPane.showConfirmDialog(
                parent,
                panel,
                "Manager Approval Required - " + actionLabel,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String loginIdentifier = loginField.getText() == null ? "" : loginField.getText().trim();
        char[] password = passwordField.getPassword();
        String reason = reasonArea.getText() == null ? "" : reasonArea.getText().trim();
        if (loginIdentifier.isBlank()) {
            throw new IllegalStateException("Manager login is required.");
        }
        if (password.length == 0) {
            throw new IllegalStateException("Manager password is required.");
        }
        if (reason.isBlank()) {
            throw new IllegalStateException("Override reason is required.");
        }

        if (!LanApiClient.isPaired()) {
            Arrays.fill(password, '\0');
            throw new IllegalStateException("This installation must be paired before manager approval can be used.");
        }
        String approvalResource = resourceIdentity == null || resourceIdentity.isBlank()
                ? actionLabel + "|" + reason
                : RefundApprovalIdentity.withReason(resourceIdentity, reason);
        return verifyAwayFromEdt(parent, loginIdentifier, password, requiredPermission,
                actionLabel, approvalResource, reason);
    }

    private static ApprovalResult verifyAwayFromEdt(Component parent, String loginIdentifier,
                                                     char[] password, String requiredPermission,
                                                     String actionLabel, String approvalResource,
                                                     String reason) {
        if (!SwingUtilities.isEventDispatchThread()) {
            return verify(loginIdentifier, password, requiredPermission, actionLabel, approvalResource, reason);
        }

        Window owner = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog progress = new JDialog(owner, "Verifying Manager Approval",
                Dialog.ModalityType.APPLICATION_MODAL);
        progress.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JLabel status = new JLabel("Verifying approval with the SmartStock server...", SwingConstants.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(18, 24, 12, 24));
        JButton cancel = new JButton("Cancel");
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(cancel);
        progress.add(status, BorderLayout.CENTER);
        progress.add(actions, BorderLayout.SOUTH);
        progress.pack();
        progress.setLocationRelativeTo(parent);

        AtomicReference<ApprovalResult> approval = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingWorker<ApprovalResult, Void> worker = new SwingWorker<>() {
            @Override protected ApprovalResult doInBackground() {
                return verify(loginIdentifier, password, requiredPermission,
                        actionLabel, approvalResource, reason);
            }

            @Override protected void done() {
                try {
                    approval.set(get());
                } catch (CancellationException cancelled) {
                    failure.set(cancelled);
                } catch (Exception ex) {
                    failure.set(ex.getCause() == null ? ex : ex.getCause());
                } finally {
                    progress.dispose();
                }
            }
        };
        cancel.addActionListener(event -> {
            worker.cancel(true);
            progress.dispose();
        });
        worker.execute();
        progress.setVisible(true);

        Throwable problem = failure.get();
        if (problem instanceof CancellationException) return null;
        if (problem != null) {
            if (problem instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException(problem.getMessage(), problem);
        }
        return approval.get();
    }

    private static ApprovalResult verify(String loginIdentifier, char[] password,
                                         String requiredPermission, String actionLabel,
                                         String approvalResource, String reason) {
        try {
            LanApiClient.ApprovalResult approval = LanApiClient.requestManagerApproval(
                    loginIdentifier, password, requiredPermission, actionLabel, approvalResource);
            return new ApprovalResult(approval.approverUserId(), approval.approverName(), reason,
                    approval.approvalToken());
        } catch (Exception ex) {
            throw new IllegalStateException("Manager approval could not be verified by the SmartStock server: "
                    + ex.getMessage(), ex);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public record ApprovalResult(int approvedByUserId, String approvedByName, String reason,
                                 String lanApprovalToken) {
    }
}
