package ui.screens.workstationprefs;

import com.google.gson.*;
import managers.HardwareSettingsManager;
import services.LanApiClient;
import services.SharedLabelPrinting;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.Callable;

/** Setup and diagnostics for the store's USB label host. */
final class SharedLabelPrinterDialog extends JDialog {
    private final JComboBox<String> queues = new JComboBox<>();
    private final JTextField name = new JTextField("Store label printer", 20);
    private final JCheckBox use = new JCheckBox("Use the store's shared CT221B on this computer");
    private final JTextArea status = new JTextArea(12, 65);
    private final JPanel actions = new JPanel(new GridLayout(0, 3, 6, 6));
    private boolean busy;
    private final Timer timer;

    static void show(Component parent) {
        new SharedLabelPrinterDialog(SwingUtilities.getWindowAncestor(parent)).setVisible(true);
    }
    private SharedLabelPrinterDialog(Window owner) {
        super(owner, "Shared CT221B Label Printer", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JPanel setup = new JPanel(); setup.setLayout(new BoxLayout(setup, BoxLayout.Y_AXIS));
        setup.add(new JLabel("Connect the CT221B by USB to its host and install its driver on that computer."));
        setup.add(new JLabel("Keep SmartStock open on the host. Other computers need only their normal store connection."));
        JPanel host = new JPanel(new FlowLayout(FlowLayout.LEFT));
        host.add(new JLabel("Host name:")); host.add(name); host.add(new JLabel("Local CT221B:")); host.add(queues);
        setup.add(host); setup.add(use);
        setup.add(new JLabel("Sharing changes require Company Preferences permission. Paper size is set in the host driver."));
        panel.add(setup, BorderLayout.NORTH);
        status.setEditable(false); status.setLineWrap(true); status.setWrapStyleWord(true);
        panel.add(new JScrollPane(status), BorderLayout.CENTER);
        button("Share from this computer", () -> {
            String queue = (String) queues.getSelectedItem();
            if (queue == null) { showError("No local CT221B found. Install its USB driver, then reopen this window."); return; }
            JsonObject r = SharedLabelPrinting.request("CONFIGURE"); r.addProperty("enabled", true);
            r.addProperty("queue", queue); r.addProperty("name", name.getText().trim());
            run(() -> { JsonObject result = LanApiClient.sharedLabelPrinter(r, true);
                HardwareSettingsManager.saveHostsSharedLabelPrinter(true);
                HardwareSettingsManager.saveUseSharedLabelPrinter(true); return result;
            }, result -> { use.setSelected(true); display(result); });
        });
        button("Stop sharing", () -> {
            JsonObject r = SharedLabelPrinting.request("CONFIGURE"); r.addProperty("enabled", false);
            run(() -> { JsonObject result = LanApiClient.sharedLabelPrinter(r, true);
                HardwareSettingsManager.saveHostsSharedLabelPrinter(false); return result; }, this::display);
        });
        button("Save this computer", () -> {
            boolean selected = use.isSelected();
            run(() -> { HardwareSettingsManager.saveUseSharedLabelPrinter(selected); return null; },
                    ignored -> status.setText("Saved. " + (selected ? "Order labels and price tags will use the shared CT221B." : "This computer will use its local label settings.")));
        });
        button("Test shared label", () -> run(() -> {
            BufferedImage image = new BufferedImage(600, 300, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            try { g.setColor(Color.WHITE); g.fillRect(0, 0, 600, 300); g.setColor(Color.BLACK);
                g.setFont(new Font("SansSerif", Font.BOLD, 38)); g.drawString("SmartStock CT221B", 30, 100);
                g.setFont(new Font("SansSerif", Font.PLAIN, 25)); g.drawString("Shared label printer test", 30, 160);
            } finally { g.dispose(); }
            SharedLabelPrinting.submitOrder(image, 1); return null;
        }, ignored -> refresh()));
        button("Refresh status", this::refresh);
        panel.add(actions, BorderLayout.SOUTH); setContentPane(panel); pack();
        setMinimumSize(new Dimension(800, 450)); setLocationRelativeTo(owner);
        SharedLabelPrinting.start();
        run(() -> new Setup(HardwareSettingsManager.getAvailablePrinterNames(), HardwareSettingsManager.useSharedLabelPrinter()),
                settings -> { for (String q : settings.queues()) if (Receipt.CustomOrderLabelPrinter.isCt221b(q)) queues.addItem(q);
                    use.setSelected(settings.use()); refresh(); });
        timer = new Timer(5000, e -> refresh()); timer.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { timer.stop(); }
        });
    }
    private record Setup(java.util.List<String> queues, boolean use) { }
    private void button(String label, Runnable action) { JButton b = new JButton(label); b.addActionListener(e -> action.run()); actions.add(b); }
    private void refresh() { if (!busy) run(() -> LanApiClient.sharedLabelPrinter(SharedLabelPrinting.request("STATUS"), true), this::display); }
    private void display(JsonObject result) {
        StringBuilder text = new StringBuilder();
        if (result.has("name")) text.append(result.get("name").getAsString()).append(" — ")
                .append(result.get("online").getAsBoolean() ? "Online" : "Offline; open SmartStock on the host")
                .append("\nUSB queue: ").append(result.get("queue").getAsString());
        else text.append("No shared label printer configured. On the USB computer, choose its CT221B and select Share from this computer.");
        text.append("\n\nRecent jobs:\n");
        if (result.has("jobs")) for (JsonElement entry : result.getAsJsonArray("jobs")) {
            JsonObject job = entry.getAsJsonObject();
            text.append(job.get("id").getAsString(), 0, 8).append("  ").append(job.get("state").getAsString());
            if (job.has("message")) text.append(" — ").append(job.get("message").getAsString());
            text.append('\n');
        }
        text.append("\nSPOOLED means accepted by the printer driver. If SENDING remains after a disconnect, check physical output before submitting again.");
        status.setText(text.toString()); status.setCaretPosition(0);
    }
    private <T> void run(Callable<T> task, java.util.function.Consumer<T> success) {
        if (busy) return; busy = true; setButtons(false);
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return task.call(); }
            @Override protected void done() {
                busy = false; setButtons(true);
                try { success.accept(get()); } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause(); showError(cause.getMessage());
                }
            }
        }.execute();
    }
    private void setButtons(boolean enabled) { for (Component c : actions.getComponents()) c.setEnabled(enabled); }
    private void showError(String message) { status.setText(message == null ? "Unable to contact the shared printer." : message); }
}
