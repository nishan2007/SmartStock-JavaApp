package ui.screens;

import data.DatabaseConfig;
import data.DatabaseMode;
import services.LanApiClient;
import ui.helpers.ResponsiveTask;

import javax.swing.*;
import java.awt.*;
import java.net.URI;
import java.util.List;

/** Minimal recovery-safe setup for a register's API-only server connection. */
final class RegisterConnectionSetup {
    private RegisterConnectionSetup() { }

    static boolean open(Component owner) {
        Settings settings;
        try {
            settings = ResponsiveTask.await(owner, "Loading the register connection...",
                    () -> initialSettings(DatabaseConfig.load(), LanApiClient.baseUri()));
            if (settings == null) return false;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(owner, "The saved register connection could not be loaded.\n\n"
                    + rootMessage(ex), "Configure Register Connection", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        JTextField host = new JTextField(settings.host(), 24);
        JSpinner port = new JSpinner(new SpinnerNumberModel(settings.port(), 1, 65535, 1));
        JSpinner location = new JSpinner(new SpinnerNumberModel(settings.location(), 1, Integer.MAX_VALUE, 1));
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("SmartStock server:"));
        form.add(host);
        form.add(new JLabel("HTTPS port:"));
        form.add(port);
        form.add(new JLabel("Store location ID:"));
        form.add(location);

        JButton find = new JButton("Find Network Server");
        JLabel discoveryStatus = new JLabel("Saved connection loaded.");
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.add(form, BorderLayout.CENTER);
        JPanel discovery = new JPanel(new BorderLayout(8, 8));
        discovery.add(find, BorderLayout.WEST);
        discovery.add(discoveryStatus, BorderLayout.CENTER);
        content.add(discovery, BorderLayout.SOUTH);
        find.addActionListener(event -> discover(content, host, port, location, find, discoveryStatus, false));
        // Automatic discovery helps first-time setup without replacing a saved address.
        if ("POS-SERVER".equalsIgnoreCase(settings.host())) {
            discover(content, host, port, location, find, discoveryStatus, true);
        }

        int choice = JOptionPane.showConfirmDialog(owner, content,
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
                        selectedHost, selectedPort, selectedLocation, settings.interval()).save();
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

    static Settings initialSettings(DatabaseConfig config, URI endpoint) {
        return new Settings(DatabaseSetup.registerSetupHost(DatabaseMode.CLIENT, endpoint.getHost()),
                endpoint.getPort() > 0 ? endpoint.getPort() : 8443,
                config.locationId() != null && config.locationId() > 0 ? config.locationId() : 1,
                config.syncIntervalSeconds());
    }

    record Settings(String host, int port, int location, int interval) { }

    private static void discover(JComponent owner, JTextField host, JSpinner port, JSpinner location,
                                 JButton find, JLabel status, boolean automatic) {
        String originalHost = host.getText();
        Object originalPort = port.getValue();
        Object originalLocation = location.getValue();
        find.setEnabled(false);
        status.setText("Looking for SmartStock servers...");
        new SwingWorker<List<LanApiClient.DiscoveredServer>, Void>() {
            @Override protected List<LanApiClient.DiscoveredServer> doInBackground() throws Exception {
                return LanApiClient.discoverServers();
            }
            @Override protected void done() {
                find.setEnabled(true);
                if (!owner.isShowing()) return;
                try {
                    List<LanApiClient.DiscoveredServer> servers = get();
                    if (servers.isEmpty()) {
                        status.setText("No server found. You can enter its address manually.");
                        return;
                    }
                    if (automatic && (!host.getText().equals(originalHost)
                            || !port.getValue().equals(originalPort)
                            || !location.getValue().equals(originalLocation))) return;
                    if (automatic && servers.size() != 1) {
                        status.setText("Multiple servers found. Use Find Network Server to choose.");
                        return;
                    }
                    LanApiClient.DiscoveredServer selected = servers.size() == 1 ? servers.get(0)
                            : (LanApiClient.DiscoveredServer) JOptionPane.showInputDialog(owner,
                            "Choose the SmartStock server:", "Find Network Server",
                            JOptionPane.PLAIN_MESSAGE, null, servers.toArray(), servers.get(0));
                    if (selected == null) return;
                    host.setText(selected.host());
                    port.setValue(selected.port());
                    if (selected.locationId() != null && selected.locationId() > 0) {
                        location.setValue(selected.locationId());
                    }
                    status.setText("Server found. Select OK to save; pairing is still required.");
                } catch (Exception ex) {
                    status.setText("Discovery failed. You can enter the server address manually.");
                }
            }
        }.execute();
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
