package ui.components;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Reusable non-modal loading, refreshing, error, and retry status strip. */
public final class LoadingStatePanel extends JPanel {
    private static final Color INFO_TEXT = new Color(30, 64, 175);
    private static final Color READY_TEXT = new Color(22, 101, 52);
    private static final Color ERROR_TEXT = new Color(153, 27, 27);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm:ss a")
            .withZone(ZoneId.systemDefault());
    private final JLabel message = new JLabel(" ");
    private final JProgressBar progress = new JProgressBar();
    private final JButton retry = new JButton("Retry");
    private Runnable retryAction = () -> { };

    public LoadingStatePanel() {
        super(new BorderLayout(8, 0));
        setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        setOpaque(true);
        message.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        progress.setIndeterminate(true);
        progress.setVisible(false);
        retry.setVisible(false);
        retry.addActionListener(event -> retryAction.run());
        retry.setBackground(new Color(71, 85, 105));
        retry.setForeground(Color.WHITE);
        retry.putClientProperty("SmartStock.preserveBackground", Boolean.TRUE);
        retry.putClientProperty("SmartStock.preserveForeground", Boolean.TRUE);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        actions.setOpaque(false);
        actions.add(retry);
        add(progress, BorderLayout.WEST);
        add(message, BorderLayout.CENTER);
        add(actions, BorderLayout.EAST);
    }

    public void loading(boolean cached, Instant loadedAt) {
        progress.setVisible(true);
        retry.setVisible(false);
        setBackground(new Color(239, 246, 255));
        message.setForeground(INFO_TEXT);
        message.setText(cached
                ? "Refreshing… Showing data from " + TIME.format(loadedAt) + "."
                : "Loading…");
    }

    public void ready(Instant loadedAt) {
        progress.setVisible(false);
        retry.setVisible(false);
        setBackground(new Color(240, 253, 244));
        message.setForeground(READY_TEXT);
        message.setText("Updated " + TIME.format(loadedAt));
    }

    public void failed(String detail, boolean cached, Runnable retryAction) {
        progress.setVisible(false);
        retry.setVisible(true);
        setBackground(new Color(254, 242, 242));
        message.setForeground(ERROR_TEXT);
        message.setText((cached ? "Refresh failed; existing data is still shown. " : "Could not load data. ")
                + (detail == null ? "" : detail));
        this.retryAction = retryAction == null ? () -> { } : retryAction;
    }

    public void actionFailed(String action, String detail, Runnable retryAction) {
        progress.setVisible(false);
        retry.setVisible(true);
        setBackground(new Color(254, 242, 242));
        message.setForeground(ERROR_TEXT);
        String label = action == null || action.isBlank() ? "Operation" : action.trim();
        message.setText(label + " was not completed. " + (detail == null ? "" : detail));
        this.retryAction = retryAction == null ? () -> { } : retryAction;
    }
}
