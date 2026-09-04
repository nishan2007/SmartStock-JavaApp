package ui.helpers;

import services.WhatsAppService;

import javax.swing.JOptionPane;
import java.awt.Component;

/** Shared one-click WhatsApp document action for Swing previews. */
public final class WhatsAppActions {
    private WhatsAppActions() { }

    public static void send(Component parent, String type, long id) {
        int confirm = JOptionPane.showConfirmDialog(parent,
                "Send this document to the opted-in WhatsApp number on the customer account?\n\nMeta messaging charges may apply.",
                "Send WhatsApp", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (confirm != JOptionPane.OK_OPTION) return;
        try {
            WhatsAppService.SendResult result = ResponsiveTask.await(parent,
                    "Sending WhatsApp message...", () -> WhatsAppService.send(type, id));
            if (result == null) return;
            JOptionPane.showMessageDialog(parent, result.message(), "WhatsApp",
                    !result.accepted() ? JOptionPane.ERROR_MESSAGE : result.budgetWarning() ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "WhatsApp message was not sent.\n\n" + ex.getMessage(),
                    "WhatsApp", JOptionPane.ERROR_MESSAGE);
        }
    }
}
