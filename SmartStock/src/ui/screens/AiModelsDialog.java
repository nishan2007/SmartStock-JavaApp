package ui.screens;

import com.google.gson.JsonObject;
import services.LanApiClient;
import services.StudioQuality;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.Map;

/** All work is performed by the server; closing this screen does not cancel a download. */
public final class AiModelsDialog extends JDialog {
    private final Map<StudioQuality, Row> rows = new EnumMap<>(StudioQuality.class);
    private final JLabel message = DeckersSwing.metaLabel("Loading store server models…");
    private final JButton refresh = new JButton("Check model updates");
    private final Timer poll = new Timer(1500, e -> load(false));
    private boolean loading;

    public AiModelsDialog(Component parent) {
        super(parent instanceof Window window ? window : SwingUtilities.getWindowAncestor(parent), "AI Models", ModalityType.MODELESS);
        JPanel content = DeckersSwing.panel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        content.add(DeckersSwing.totalLabel("AI Models", true));
        content.add(Box.createVerticalStrut(10));
        content.add(DeckersSwing.metaLabel("Downloaded once to the store server and reused across SmartStock updates."));
        for (StudioQuality quality : StudioQuality.values()) {
            Row row = new Row(quality); rows.put(quality, row);
            content.add(Box.createVerticalStrut(16)); content.add(row.panel);
        }
        content.add(Box.createVerticalStrut(16)); content.add(message);
        content.add(Box.createVerticalStrut(12)); content.add(refresh);
        DeckersSwing.styleUtilityButton(refresh, DeckersPalette.PURPLE);
        refresh.addActionListener(e -> load(true));
        setContentPane(content); pack(); setMinimumSize(new Dimension(650, 420));
        setLocationRelativeTo(parent); setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { poll.stop(); }
        });
        load(false); poll.start();
    }

    private void load(boolean check) {
        if (loading || !isDisplayable()) return;
        loading = true; refresh.setEnabled(false);
        if (check) message.setText("Checking Deckers model updates…");
        new SwingWorker<JsonObject, Void>() {
            @Override protected JsonObject doInBackground() throws Exception { return LanApiClient.studioModels(check); }
            @Override protected void done() {
                loading = false; refresh.setEnabled(true);
                if (!isDisplayable()) return;
                try {
                    for (var value : get().getAsJsonArray("models")) {
                        JsonObject model = value.getAsJsonObject();
                        rows.get(StudioQuality.parse(model.get("quality").getAsString())).update(model);
                    }
                    message.setText("Models work offline after installation. Downloads continue if you close this screen.");
                    poll.start();
                } catch (Exception e) {
                    Throwable cause = e; while (cause.getCause() != null) cause = cause.getCause();
                    message.setText("Unable to load models. " + cause.getMessage());
                    poll.stop();
                }
            }
        }.execute();
    }

    private final class Row {
        final JPanel panel = DeckersSwing.panel();
        final JLabel info = DeckersSwing.metaLabel("Loading…");
        final JLabel status = DeckersSwing.metaLabel(" ");
        final JButton action = new JButton("Install");
        final JProgressBar progress = new JProgressBar(0, 100);
        boolean submitting;
        Row(StudioQuality quality) {
            panel.setLayout(new BorderLayout(12, 8));
            JPanel labels = DeckersSwing.panel(); labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
            labels.add(DeckersSwing.totalLabel(quality.toString(), true)); labels.add(info); labels.add(status);
            panel.add(labels, BorderLayout.CENTER); panel.add(action, BorderLayout.EAST); panel.add(progress, BorderLayout.SOUTH);
            progress.setStringPainted(true); progress.setString("Not installed"); action.setEnabled(false);
            DeckersSwing.styleUtilityButton(action, DeckersPalette.PURPLE);
            action.addActionListener(e -> {
                submitting = true; action.setEnabled(false);
                new SwingWorker<Void, Void>() {
                    @Override protected Void doInBackground() throws Exception { LanApiClient.installStudioModel(quality); return null; }
                    @Override protected void done() {
                        submitting = false;
                        try { get(); poll.start(); load(false); }
                        catch (Exception ex) {
                            action.setEnabled(true);
                            Throwable cause = ex; while (cause.getCause() != null) cause = cause.getCause();
                            JOptionPane.showMessageDialog(AiModelsDialog.this, cause.getMessage(), "AI Models", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                }.execute();
            });
        }
        void update(JsonObject model) {
            long size = model.get("sizeBytes").getAsLong();
            info.setText(model.get("version").getAsString() + " • " + formatBytes(size)
                    + " • " + (model.get("available").getAsBoolean() ? "Ready to use" : "Unavailable"));
            JsonObject job = model.getAsJsonObject("progress");
            String state = job.get("state").getAsString();
            boolean busy = state.equals("CHECKING") || state.equals("DOWNLOADING");
            action.setText(model.get("action").getAsString());
            action.setEnabled(!busy && !submitting && !action.getText().equals("Installed"));
            status.setText(job.get("message").getAsString());
            progress.setIndeterminate(state.equals("CHECKING"));
            long downloaded = job.get("downloadedBytes").getAsLong(), total = job.get("totalBytes").getAsLong();
            progress.setValue(total == 0 ? 0 : (int) (downloaded * 100 / total));
            progress.setString(busy ? formatBytes(downloaded) + " / " + formatBytes(total)
                    : model.get("available").getAsBoolean() ? "Installed: " + model.get("installedVersion").getAsString() : state.equals("FAILED") ? "Download failed" : "Not installed");
        }
    }

    private static String formatBytes(long bytes) { return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_000_000.0); }
}
