package app;

import ui.screens.WelcomeFrame;
import ui.helpers.ThemeManager;
import data.DatabaseConfig;
import data.DatabaseMode;
import services.PostgresRuntimeService;
import services.SyncWorker;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        if (hasArg(args, "--sync-service")) {
            applyModeArgument(new String[]{"--server"});
            SyncServiceMain.main(args);
            return;
        }
        applyModeArgument(args);
        ensureBackgroundSyncForServerMode();
        SyncWorker.startIfServerMode();
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            ThemeManager.applyLookAndFeelDefaults();
            new WelcomeFrame().setVisible(true);
        });
    }

    private static boolean hasArg(String[] args, String expected) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (expected.equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static void applyModeArgument(String[] args) {
        if (args == null || args.length == 0) {
            return;
        }
        for (String arg : args) {
            DatabaseMode mode = switch (arg) {
                case "--server" -> DatabaseMode.SERVER;
                case "--client" -> DatabaseMode.CLIENT;
                case "--cloud-direct" -> DatabaseMode.CLOUD_DIRECT;
                default -> null;
            };
            if (mode == null) {
                continue;
            }
            try {
                DatabaseConfig config = DatabaseConfig.load();
                if (config.hasUnresolvedCredentialPlaceholders()) {
                    System.err.println(config.missingPrimaryConnectionMessage());
                }
                config.withMode(mode).save();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private static void ensureBackgroundSyncForServerMode() {
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() != DatabaseMode.SERVER) {
            return;
        }
        Thread installer = new Thread(() -> {
            try {
                PostgresRuntimeService.CommandResult result = PostgresRuntimeService.ensureSyncServiceInstalled();
                if (!result.success()) {
                    System.err.println("Background sync service install failed:\n" + result.output());
                }
            } catch (Exception ex) {
                System.err.println("Background sync service install check failed: " + ex.getMessage());
            }
        }, "smartstock-sync-service-auto-install");
        installer.setDaemon(true);
        installer.start();
    }
}
