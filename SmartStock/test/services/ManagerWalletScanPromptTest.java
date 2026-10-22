package services;

import org.junit.jupiter.api.Test;
import ui.helpers.NfcBadgePromptController;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class ManagerWalletScanPromptTest {
    @Test void walletScanUsesPinPromptAndClearsPreviousPassword() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField identifier = new JTextField();
            JPasswordField secret = new JPasswordField("old-password");
            JLabel label = new JLabel("Manager Password:");
            JLabel status = new JLabel();
            var nfc = new NfcBadgePromptController(identifier, secret, label, status);
            ManagerApprovalService.installWalletScanPrompt(identifier, secret, label, status, nfc);
            identifier.setText("]Q3SSW1" + "A".repeat(32));
            assertEquals(0, secret.getPassword().length);
            assertTrue(label.getText().contains("PIN"));
            assertTrue(status.getText().contains("Wallet badge scanned"));
            secret.setText("1234");
            identifier.setText("manager@example.com");
            assertEquals(0, secret.getPassword().length);
            assertEquals("Manager Password:", label.getText());
            assertFalse(status.getText().contains("Wallet badge scanned"));
        });
    }

    @Test void ordinaryCredentialsArePreserved() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTextField identifier = new JTextField();
            JPasswordField secret = new JPasswordField("manager-password");
            JLabel label = new JLabel();
            JLabel status = new JLabel();
            var nfc = new NfcBadgePromptController(identifier, secret, label, status);
            ManagerApprovalService.installWalletScanPrompt(identifier, secret, label, status, nfc);
            identifier.setText("manager");
            assertArrayEquals("manager-password".toCharArray(), secret.getPassword());
            assertEquals("Manager Password:", label.getText());
        });
    }
}
