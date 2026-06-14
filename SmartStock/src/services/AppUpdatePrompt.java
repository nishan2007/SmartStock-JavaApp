package services;

import utils.DeviceUtils;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;

public final class AppUpdatePrompt {
    private AppUpdatePrompt() {
    }

    public static void show(Component parent, AppUpdateService.AppRelease release, boolean required) {
        JTextArea notesArea = new JTextArea(notesText(release, required));
        notesArea.setEditable(false);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setOpaque(false);
        JScrollPane scrollPane = new JScrollPane(notesArea);
        scrollPane.setPreferredSize(new Dimension(460, 220));

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("SmartStock " + release.version() + " is available."), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        Object[] options = required
                ? new Object[]{"Update and Restart"}
                : new Object[]{"Update and Restart", "Later"};
        int choice = JOptionPane.showOptionDialog(
                parent,
                panel,
                required ? "Required SmartStock Update" : "SmartStock Update",
                JOptionPane.DEFAULT_OPTION,
                required ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE,
                null,
                options,
                options[0]
        );

        if (choice == 0) {
            AppUpdateService.downloadAndStageAsync(parent, release);
        }
    }

    private static String notesText(AppUpdateService.AppRelease release, boolean required) {
        StringBuilder text = new StringBuilder();
        text.append("Current version: ").append(DeviceUtils.getAppVersion()).append('\n');
        text.append("New version: ").append(release.version()).append('\n');
        if (required) {
            text.append("This update is required before continuing on this workstation.").append('\n');
        }
        if (release.fileSizeBytes() > 0) {
            text.append("Download size: ").append(formatBytes(release.fileSizeBytes())).append('\n');
        }
        if (release.releaseNotes() != null && !release.releaseNotes().isBlank()) {
            text.append('\n').append(release.releaseNotes().trim());
        }
        return text.toString();
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024L) {
            return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
        }
        if (bytes >= 1024L) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return bytes + " bytes";
    }
}
