package ui.helpers;

import managers.SessionManager;
import services.BadgeCredentialService;
import services.LanApiClient;
import services.PcscNfcService;

import javax.swing.JLabel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.time.Duration;

/** Adds private ACR122U badge capture to a modal credential prompt. */
public final class NfcBadgePromptController implements AutoCloseable {
    private final JTextField identifierField;
    private final JPasswordField secretField;
    private final JLabel secretLabel;
    private final JLabel statusLabel;
    private volatile boolean listening;
    private volatile String badgeIdentifier;
    private volatile Boolean badgePinRequired;

    public NfcBadgePromptController(JTextField identifierField, JPasswordField secretField,
                                    JLabel secretLabel, JLabel statusLabel) {
        this.identifierField = identifierField;
        this.secretField = secretField;
        this.secretLabel = secretLabel;
        this.statusLabel = statusLabel;
    }

    public void start() {
        if (listening || !PcscNfcService.hasReader()) {
            statusLabel.setText("Enter manager credentials.");
            return;
        }
        listening = true;
        statusLabel.setText("Tap a manager NFC badge, or enter manager credentials.");
        Thread monitor = new Thread(() -> {
            while (listening) {
                try {
                    PcscNfcService.ReadResult card = PcscNfcService.read(Duration.ofSeconds(2));
                    String normalized = BadgeCredentialService.normalizeBadge(card.payload());
                    if (!BadgeCredentialService.looksLikeGeneratedBadge(normalized)) {
                        SwingUtilities.invokeLater(() -> statusLabel.setText(
                                "That card is not a SmartStock employee badge."));
                        waitBeforeRetry();
                        continue;
                    }
                    badgeIdentifier = normalized;
                    listening = false;
                    SwingUtilities.invokeLater(() -> {
                        identifierField.setText("NFC badge detected");
                        identifierField.setEditable(false);
                        secretField.setText("");
                        showCheckingBadgePolicy();
                    });
                    loadBadgePolicy(normalized);
                } catch (PcscNfcService.NoCardPresentException ignored) {
                    // Continue listening until the modal prompt closes.
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> statusLabel.setText(
                            "NFC reader unavailable; enter manager credentials."));
                    waitBeforeRetry();
                }
            }
        }, "smartstock-nfc-approval");
        monitor.setDaemon(true);
        monitor.start();
    }

    public String identifier() {
        if (badgeIdentifier != null) return badgeIdentifier;
        return identifierField.getText() == null ? "" : identifierField.getText().trim();
    }

    public boolean badgeCaptured() {
        return badgeIdentifier != null;
    }

    public Boolean badgePinRequired() {
        return badgePinRequired;
    }

    private void loadBadgePolicy(String badgeId) {
        try {
            Integer locationId = SessionManager.getCurrentLocationId();
            if (locationId == null || !LanApiClient.isPaired()) {
                throw new IllegalStateException("No active paired store");
            }
            LanApiClient.BadgeStatus badgeStatus = LanApiClient.badgeStatus(badgeId, locationId);
            badgePinRequired = badgeStatus.pinRequired();
            SwingUtilities.invokeLater(() -> showBadgePolicy(badgeStatus.pinRequired()));
        } catch (Exception ex) {
            badgePinRequired = null;
            SwingUtilities.invokeLater(this::showBadgePolicyUnavailable);
        }
    }

    private void showCheckingBadgePolicy() {
        secretLabel.setText("Checking manager badge security...");
        secretLabel.setVisible(true);
        secretField.setVisible(false);
        secretField.setEnabled(false);
        statusLabel.setText("Manager badge detected. Checking whether an employee PIN is required...");
        refreshLayout();
    }

    private void showBadgePolicy(boolean pinRequired) {
        secretLabel.setText("Manager Employee PIN:");
        secretLabel.setVisible(pinRequired);
        secretField.setVisible(pinRequired);
        secretField.setEnabled(pinRequired);
        if (pinRequired) {
            statusLabel.setText("Manager badge detected. Enter the employee PIN.");
            secretField.requestFocusInWindow();
        } else {
            secretField.setText("");
            statusLabel.setText("Manager badge detected. No PIN is required; enter the override reason.");
        }
        refreshLayout();
    }

    private void showBadgePolicyUnavailable() {
        secretLabel.setText("Manager Employee PIN:");
        secretLabel.setVisible(true);
        secretField.setVisible(true);
        secretField.setEnabled(true);
        statusLabel.setText("Could not check the badge PIN setting. Enter the manager employee PIN.");
        secretField.requestFocusInWindow();
        refreshLayout();
    }

    private void refreshLayout() {
        if (secretField.getParent() != null) {
            secretField.getParent().revalidate();
            secretField.getParent().repaint();
        }
    }

    private void waitBeforeRetry() {
        try {
            Thread.sleep(1_000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            listening = false;
        }
    }

    @Override
    public void close() {
        listening = false;
    }
}
