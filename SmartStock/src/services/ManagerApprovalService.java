package services;

import javax.swing.*;
import java.awt.*;

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

        if (LanApiClient.isPaired()) {
            try {
                String approvalResource = resourceIdentity == null || resourceIdentity.isBlank()
                        ? actionLabel + "|" + reason
                        : RefundApprovalIdentity.withReason(resourceIdentity, reason);
                LanApiClient.ApprovalResult approval = LanApiClient.requestManagerApproval(
                        loginIdentifier, password, requiredPermission,
                        actionLabel, approvalResource);
                return new ApprovalResult(approval.approverUserId(), approval.approverName(), reason,
                        approval.approvalToken());
            } catch (Exception ex) {
                throw new IllegalStateException("Manager approval could not be verified by the SmartStock server: "
                        + ex.getMessage(), ex);
            }
        }
        throw new IllegalStateException("This installation must be paired before manager approval can be used.");
    }

    public record ApprovalResult(int approvedByUserId, String approvedByName, String reason,
                                 String lanApprovalToken) {
    }
}
