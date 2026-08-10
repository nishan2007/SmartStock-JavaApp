package ui.helpers;

import services.LanApiClient;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;

/** Shared Generate Barcode controls for catalog editors. */
public final class BarcodeGenerationHelper {
    private BarcodeGenerationHelper() {
    }

    public static JPanel field(Component owner, JTextField target) {
        JPanel panel = panel();
        panel.add(target, BorderLayout.CENTER);
        panel.add(button(owner, target, barcode -> setField(owner, target, barcode)), BorderLayout.EAST);
        return panel;
    }

    public static JPanel area(Component owner, JTextArea target, JScrollPane scrollPane) {
        JPanel panel = panel();
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(button(owner, target, barcode -> appendArea(owner, target, barcode)), BorderLayout.EAST);
        return panel;
    }

    private static JPanel panel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        return panel;
    }

    private static JButton button(Component owner, Component target, BarcodeConsumer consumer) {
        JButton button = new JButton("Generate");
        DeckersSwing.styleUtilityButton(button, DeckersPalette.PURPLE);
        button.setToolTipText("Generate an unused internal EAN-13 barcode.");
        button.setEnabled(target.isEnabled());
        target.addPropertyChangeListener("enabled", event -> button.setEnabled(target.isEnabled()));
        button.addActionListener(event -> {
            if (!target.isEnabled()) return;
            Window window = owner instanceof Window direct ? direct : javax.swing.SwingUtilities.getWindowAncestor(owner);
            if (window == null) return;
            button.setEnabled(false);
            UiTaskRunner.submit(window, "catalog.generate-barcode", LanApiClient::generateCatalogBarcode,
                    barcode -> {
                        button.setEnabled(target.isEnabled());
                        consumer.accept(barcode);
                    }, failure -> {
                        button.setEnabled(target.isEnabled());
                        JOptionPane.showMessageDialog(owner,
                                "Unable to generate a barcode: " + failure.getMessage(),
                                "Barcode Generation", JOptionPane.ERROR_MESSAGE);
                    });
        });
        return button;
    }

    private static void setField(Component owner, JTextField target, String barcode) {
        if (!target.getText().isBlank()) {
            int choice = JOptionPane.showConfirmDialog(owner,
                    "Replace the current barcode with " + barcode + "?",
                    "Replace Barcode", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) return;
        }
        target.setText(barcode);
        copy(owner, barcode);
    }

    private static void appendArea(Component owner, JTextArea target, String barcode) {
        String existing = target.getText();
        target.setText(existing.isBlank() ? barcode : existing.stripTrailing() + System.lineSeparator() + barcode);
        copy(owner, barcode);
    }

    private static void copy(Component owner, String barcode) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(barcode), null);
            JOptionPane.showMessageDialog(owner, "Generated and copied barcode: " + barcode,
                    "Barcode Generated", JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(owner, "Generated barcode: " + barcode
                            + "\nIt was added to the form, but could not be copied automatically.",
                    "Barcode Generated", JOptionPane.WARNING_MESSAGE);
        }
    }

    @FunctionalInterface
    private interface BarcodeConsumer {
        void accept(String barcode);
    }
}
