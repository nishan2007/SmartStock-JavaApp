package app;

import ui.screens.WelcomeFrame;
import ui.screens.InitialSetupWizard;
import ui.helpers.ThemeManager;
import ui.helpers.AppIconManager;
import data.DatabaseConfig;
import data.DatabaseMode;
import services.PostgresRuntimeService;
import services.CompanyBackupScheduler;
import services.SyncWorker;
import services.LanApiClient;
import managers.NavigationManager;

import javax.swing.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

public class Main {
    private static final Path STARTUP_LOG = Path.of(System.getProperty("user.home"), ".smartstock", "app-startup.log");

    public static void main(String[] args) {
        installStartupDiagnostics();
        logStartup("Starting SmartStock " + System.getProperty("java.version", "unknown-java")
                + " on " + System.getProperty("os.name", "unknown-os")
                + " " + System.getProperty("os.version", "")
                + " (" + System.getProperty("os.arch", "unknown-arch") + ")");
        if (hasArg(args, "--sync-service")) {
            applyModeArgument(new String[]{"--server"});
            SyncServiceMain.main(args);
            return;
        }
        try {
            applyModeArgument(args);
            startSilentLanCredentialMaintenance();
            ensureBackgroundSyncForServerMode();
            CompanyBackupScheduler.start();
            SyncWorker.startIfServerMode();
        } catch (Throwable ex) {
            logStartupException("Startup preparation failed", ex);
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                ex.printStackTrace();
                logStartupException("Unable to apply system look and feel", ex);
            }
            try {
                ThemeManager.applyLookAndFeelDefaults();
                AppIconManager.install();
                LanApiClient.setConnectionLossHandler(NavigationManager::returnToWelcomeForConnectionLoss);
                WelcomeFrame frame = new WelcomeFrame();
                frame.setVisible(true);
                frame.toFront();
                frame.requestFocus();
                if (hasArg(args, "--setup-wizard")) {
                    InitialSetupWizard wizard = new InitialSetupWizard(frame);
                    wizard.setVisible(true);
                    wizard.toFront();
                    wizard.requestFocus();
                }
                logStartup("WelcomeFrame visible=" + frame.isVisible()
                        + " bounds=" + frame.getBounds()
                        + " state=" + frame.getState());
            } catch (Throwable ex) {
                logStartupException("Unable to show SmartStock welcome window", ex);
                JOptionPane.showMessageDialog(
                        null,
                        "SmartStock could not finish starting.\n\n"
                                + rootCauseMessage(ex)
                                + "\n\nDetails were written to:\n" + STARTUP_LOG,
                        "SmartStock Startup Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        });
    }

    private static void startSilentLanCredentialMaintenance() {
        DatabaseMode mode = DatabaseConfig.load().mode();
        if (mode != DatabaseMode.CLIENT && mode != DatabaseMode.SERVER && mode != DatabaseMode.REMOTE_ADMIN) return;
        Thread maintenance = new Thread(() -> {
            try {
                if (mode == DatabaseMode.SERVER && !LanApiClient.isPaired()) {
                    for (int attempt = 0; attempt < 10 && !LanApiClient.isPaired(); attempt++) {
                        try {
                            LanApiClient.ensureLocalServerCredential();
                        } catch (Exception ex) {
                            Thread.sleep(1_000L);
                        }
                    }
                } else if (!LanApiClient.isPaired()) {
                    LanApiClient.claimApprovedCredential();
                } else {
                    LanApiClient.renewDeviceCredentialIfDue();
                }
                if (LanApiClient.isPaired()) LanApiClient.syncDeviceMetadata();
            } catch (Exception ex) {
                // Pairing state is deliberately preserved during outages. Employees
                // receive the normal server-unavailable message from their workflow.
                System.err.println("LAN credential maintenance will retry later: " + ex.getMessage());
            }
        }, "smartstock-lan-credential-maintenance");
        maintenance.setDaemon(true);
        maintenance.start();
    }

    private static void installStartupDiagnostics() {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) ->
                logStartupException("Uncaught exception on " + thread.getName(), throwable));
        System.setProperty("sun.awt.exception.handler", Main.class.getName());
    }

    public void handle(Throwable throwable) {
        logStartupException("AWT exception", throwable);
    }

    private static void logStartup(String message) {
        try {
            Files.createDirectories(STARTUP_LOG.getParent());
            Files.writeString(
                    STARTUP_LOG,
                    LocalDateTime.now() + " " + message + System.lineSeparator(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException ignored) {
            // Best-effort diagnostic logging only.
        }
    }

    private static void logStartupException(String message, Throwable throwable) {
        StringWriter buffer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(buffer));
        logStartup(message + System.lineSeparator() + buffer);
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
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
                case "--remote-admin" -> DatabaseMode.REMOTE_ADMIN;
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
        if (config.mode() != DatabaseMode.SERVER || config.locationId() == null) {
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
