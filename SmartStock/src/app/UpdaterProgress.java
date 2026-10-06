package app;

import java.awt.*;
import java.io.*;
import java.nio.file.Path;
import javax.swing.*;
import ui.design.DeckersPalette;

/** Standalone installer UI; no dependency on the running desktop's lifetime. */
final class UpdaterProgress {
    interface Listener { void changed(String step, String file, int percent); }
    private static volatile Listener listener = (step, file, percent) -> {};
    private static volatile String step = "Preparing update";
    private static JFrame window;
    private static JLabel status;
    private static JLabel detail;
    private static JProgressBar bar;

    static void listen(Listener next) { listener = next; }
    static int percentage(long done, long total) {
        return total <= 0 ? -1 : (int) Math.min(100, Math.max(0, 100.0 * done / total));
    }
    static void stage(String value) {
        step = value;
        SmartStockUpdater.log(value);
        listener.changed(step, "", -1);
    }
    static void copy(InputStream input, OutputStream output, long total, String name) throws IOException {
        byte[] buffer = new byte[128 * 1024];
        long done = 0;
        int previous = -2;
        listener.changed(step, name, percentage(0, total));
        for (int count; (count = input.read(buffer)) != -1;) {
            output.write(buffer, 0, count);
            done += count;
            int percent = percentage(done, total);
            if (percent != previous) {
                listener.changed(step, name, percent);
                previous = percent;
            }
        }
    }
    static void open(String version) throws Exception {
        if (GraphicsEnvironment.isHeadless()) return;
        SwingUtilities.invokeAndWait(() -> {
            window = new JFrame("Updating SmartStock");
            window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
            content.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
            content.setBackground(DeckersPalette.surface());
            JLabel title = new JLabel("Updating SmartStock " + version);
            title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
            status = new JLabel(step);
            detail = new JLabel(" ");
            bar = new JProgressBar(0, 100);
            bar.setForeground(DeckersPalette.ORANGE);
            bar.setIndeterminate(true);
            JLabel reminder = new JLabel("Please keep this computer on.");
            for (JLabel label : new JLabel[]{title, status, detail, reminder})
                label.setForeground(DeckersPalette.text());
            content.add(title);
            content.add(Box.createVerticalStrut(18));
            content.add(status);
            content.add(Box.createVerticalStrut(10));
            content.add(bar);
            content.add(detail);
            content.add(Box.createVerticalStrut(18));
            content.add(reminder);
            window.setContentPane(content);
            window.setSize(560, 240);
            window.setLocationRelativeTo(null);
            listen((label, file, percent) -> SwingUtilities.invokeLater(() -> {
                status.setText(label);
                detail.setText(file.isEmpty() ? " " : file);
                bar.setIndeterminate(percent < 0);
                bar.setStringPainted(percent >= 0);
                bar.setValue(Math.max(0, percent));
                bar.setString(percent + "% of this file");
            }));
            window.setVisible(true);
        });
    }
    static void close() {
        if (window != null) SwingUtilities.invokeLater(() -> window.dispose());
    }
    static boolean failure(String error, String recovery, Path log) {
        if (window == null) return false;
        SwingUtilities.invokeLater(() -> {
            bar.setIndeterminate(false);
            bar.setStringPainted(false);
            status.setText("Update failed");
            JTextArea message = new JTextArea(failureText(error, recovery, log), 10, 48);
            message.setEditable(false);
            message.setLineWrap(true);
            message.setWrapStyleWord(true);
            JOptionPane.showMessageDialog(window, new JScrollPane(message),
                    "SmartStock update failed", JOptionPane.ERROR_MESSAGE);
            window.dispose();
            System.exit(1);
        });
        return true;
    }
    static String failureText(String error, String recovery, Path log) {
        return error + "\n\n" + recovery + "\n\nLog: " + log;
    }
}
