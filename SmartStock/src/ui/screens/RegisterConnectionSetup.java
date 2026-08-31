package ui.screens;

import data.DatabaseConfig;
import data.DatabaseMode;
import services.LanApiClient;
import ui.helpers.ResponsiveTask;

import javax.swing.*;
import java.awt.*;

/** Minimal recovery-safe setup for a register's API-only server connection. */
final class RegisterConnectionSetup {
    private RegisterConnectionSetup() { }

    static boolean open(Component owner) {
        JTextField host = new JTextField("POS-SERVER", 24);
        JSpinner port = new JSpinner(new SpinnerNumberModel(8443, 1, 65535, 1));
        JSpinner location = new JSpinner(new SpinnerNumberModel(1, 1, Integer.MAX_VALUE, 1));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("SmartStock server:"));
        form.add(host);
        form.add(new JLabel("HTTPS port:"));
        form.add(port);
        form.add(new JLabel("Store location ID:"));
        form.add(location);

        int choice = JOptionPane.showConfirmDialog(owner, form,
                "Configure Register Connection", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return false;
        String selectedHost = host.getText() == null ? "" : host.getText().trim();
        int selectedPort = (Integer) port.getValue();
        int selectedLocation = (Integer) location.getValue();
        if (selectedHost.isBlank()) {
            JOptionPane.showMessageDialog(owner, "Enter POS-SERVER or the server's LAN address.",
                    "Configure Register Connection", JOptionPane.WARNING_MESSAGE);
            return false;
        }
        try {
            Boolean saved = ResponsiveTask.await(owner, "Saving the register connection...", () -> {
                LanApiClient.configureEndpoint(selectedHost, selectedPort);
                DatabaseConfig.fromForm(DatabaseMode.CLIENT, "", "", "",
                        selectedHost, selectedPort, selectedLocation, 60).save();
                return Boolean.TRUE;
            });
            if (saved == null) return false;
            JOptionPane.showMessageDialog(owner,
                    "Register mode saved.\nServer: " + selectedHost + ":" + selectedPort
                            + "\nStore location: " + selectedLocation
                            + "\n\nYou can now pair this register.",
                    "Register Connection Saved", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, "The register connection could not be saved.\n\n"
                            + rootMessage(ex), "Configure Register Connection", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
