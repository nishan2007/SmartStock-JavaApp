package services;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public final class CompanyBackupScheduler {
    public static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "backup.properties");
    public static final Path DEFAULT_BACKUP_DIRECTORY = Path.of(System.getProperty("user.home"), ".smartstock", "backups");
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "smartstock-company-backup-scheduler");
        thread.setDaemon(true);
        return thread;
    });
    private static final Object LOCK = new Object();
    private static ScheduledFuture<?> scheduledTask;
    private static volatile boolean runningBackup;

    private CompanyBackupScheduler() {
    }

    public static BackupScheduleSettings loadSettings() {
        Properties props = new Properties();
        if (Files.isRegularFile(CONFIG_PATH)) {
            try (InputStream input = Files.newInputStream(CONFIG_PATH)) {
                props.load(input);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return new BackupScheduleSettings(
                Boolean.parseBoolean(props.getProperty("enabled", "false")),
                Path.of(props.getProperty("directory", DEFAULT_BACKUP_DIRECTORY.toString())),
                parseLong(props.getProperty("interval.minutes"), 1440, 15, 525600),
                parseInt(props.getProperty("retention.count"), 10, 1, 1000),
                parseLong(props.getProperty("last.success.epoch.millis"), 0, 0, Long.MAX_VALUE),
                props.getProperty("last.status", "Not run yet"),
                props.getProperty("last.error", "")
        );
    }

    public static void saveSettings(BackupScheduleSettings settings) throws IOException {
        writeSettings(settings, true);
    }

    private static void writeSettings(BackupScheduleSettings settings, boolean applySchedule) throws IOException {
        BackupScheduleSettings clean = cleanSettings(settings);
        Properties props = new Properties();
        props.setProperty("enabled", String.valueOf(clean.enabled()));
        props.setProperty("directory", clean.directory().toString());
        props.setProperty("interval.minutes", String.valueOf(clean.intervalMinutes()));
        props.setProperty("retention.count", String.valueOf(clean.retentionCount()));
        props.setProperty("last.success.epoch.millis", String.valueOf(clean.lastSuccessEpochMillis()));
        props.setProperty("last.status", clean.lastStatus());
        props.setProperty("last.error", clean.lastError());
        Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream output = Files.newOutputStream(CONFIG_PATH)) {
            props.store(output, "SmartStock company backup scheduler");
        }
        if (applySchedule) {
            reschedule();
        }
    }

    public static void start() {
        reschedule();
    }

    public static BackupRunResult runNow() throws Exception {
        BackupScheduleSettings settings = loadSettings();
        BackupRunResult result = runBackup(settings);
        saveRunResult(settings, result, null);
        return result;
    }

    public static void pruneOldBackups(Path directory, int keepCount) throws IOException {
        int cleanKeepCount = Math.max(1, keepCount);
        if (directory == null || !Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.list(directory)) {
            Path[] backups = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".ssbackup"))
                    .sorted(Comparator.comparingLong(CompanyBackupScheduler::lastModifiedMillis).reversed())
                    .toArray(Path[]::new);
            for (int i = cleanKeepCount; i < backups.length; i++) {
                Files.deleteIfExists(backups[i]);
            }
        }
    }

    private static void reschedule() {
        synchronized (LOCK) {
            if (scheduledTask != null) {
                scheduledTask.cancel(false);
                scheduledTask = null;
            }
            BackupScheduleSettings settings = loadSettings();
            if (!settings.enabled()) {
                return;
            }
            long intervalMinutes = Math.max(15, settings.intervalMinutes());
            long initialDelayMinutes = initialDelayMinutes(settings, intervalMinutes);
            scheduledTask = EXECUTOR.scheduleWithFixedDelay(
                    CompanyBackupScheduler::runScheduledSafely,
                    initialDelayMinutes,
                    intervalMinutes,
                    TimeUnit.MINUTES
            );
        }
    }

    private static long initialDelayMinutes(BackupScheduleSettings settings, long intervalMinutes) {
        if (settings.lastSuccessEpochMillis() <= 0) {
            return 1;
        }
        long elapsedMinutes = Duration.between(Instant.ofEpochMilli(settings.lastSuccessEpochMillis()), Instant.now()).toMinutes();
        return Math.max(1, intervalMinutes - elapsedMinutes);
    }

    private static void runScheduledSafely() {
        BackupScheduleSettings settings = loadSettings();
        if (!settings.enabled()) {
            reschedule();
            return;
        }
        try {
            BackupRunResult result = runBackup(settings);
            saveRunResult(settings, result, null);
        } catch (Exception ex) {
            try {
                saveRunResult(settings, null, ex);
            } catch (IOException ioEx) {
                ioEx.printStackTrace();
            }
        }
    }

    private static BackupRunResult runBackup(BackupScheduleSettings settings) throws Exception {
        if (runningBackup) {
            throw new IllegalStateException("A company backup is already running.");
        }
        runningBackup = true;
        try {
            Files.createDirectories(settings.directory());
            Path backupFile = settings.directory().resolve("smartstock-company-backup-" + LocalDateTime.now().format(FILE_TIMESTAMP) + ".ssbackup");
            CompanyBackupService.BackupSummary summary = CompanyBackupService.exportBackup(backupFile);
            pruneOldBackups(settings.directory(), settings.retentionCount());
            return new BackupRunResult(backupFile, summary);
        } finally {
            runningBackup = false;
        }
    }

    private static void saveRunResult(BackupScheduleSettings settings, BackupRunResult result, Exception error) throws IOException {
        BackupScheduleSettings clean = cleanSettings(settings);
        String status;
        String lastError;
        long lastSuccess = clean.lastSuccessEpochMillis();
        if (error == null && result != null) {
            lastSuccess = System.currentTimeMillis();
            status = "Last backup: " + result.backupFile() + " (" + result.summary().rowCount() + " rows, " + result.summary().assetCount() + " files)";
            lastError = "";
        } else {
            status = "Last backup failed: " + (error == null ? "Unknown error" : error.getMessage());
            lastError = error == null ? "" : String.valueOf(error.getMessage());
        }
        writeSettings(new BackupScheduleSettings(
                clean.enabled(),
                clean.directory(),
                clean.intervalMinutes(),
                clean.retentionCount(),
                lastSuccess,
                status,
                lastError
        ), false);
    }

    private static BackupScheduleSettings cleanSettings(BackupScheduleSettings settings) {
        Path directory = settings.directory() == null ? DEFAULT_BACKUP_DIRECTORY : settings.directory();
        return new BackupScheduleSettings(
                settings.enabled(),
                directory,
                Math.max(15, settings.intervalMinutes()),
                Math.max(1, settings.retentionCount()),
                Math.max(0, settings.lastSuccessEpochMillis()),
                settings.lastStatus() == null ? "" : settings.lastStatus(),
                settings.lastError() == null ? "" : settings.lastError()
        );
    }

    private static long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ex) {
            return 0;
        }
    }

    private static long parseLong(String value, long fallback, long min, long max) {
        try {
            long parsed = Long.parseLong(value == null ? "" : value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback, int min, int max) {
        try {
            int parsed = Integer.parseInt(value == null ? "" : value.trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ex) {
            return fallback;
        }
    }

    public record BackupScheduleSettings(
            boolean enabled,
            Path directory,
            long intervalMinutes,
            int retentionCount,
            long lastSuccessEpochMillis,
            String lastStatus,
            String lastError
    ) {
    }

    public record BackupRunResult(Path backupFile, CompanyBackupService.BackupSummary summary) {
    }
}
