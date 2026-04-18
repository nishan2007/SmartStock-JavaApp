package managers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class ReceiptNumberManager {
    private static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "device.properties");
    private static final String DEVICE_ID_KEY = "device_id";
    private static final int RECEIPT_SEQUENCE_PADDING = 6;

    private ReceiptNumberManager() {
    }

    public static synchronized ReceiptNumber nextReceipt(int locationId) throws IOException {
        Properties properties = loadProperties();
        String deviceId = getOrCreateDeviceId(properties);
        String storeCode = formatStoreCode(locationId);
        String sequenceKey = "next_receipt_sequence." + storeCode + "." + deviceId;
        int sequence = parsePositiveInt(properties.getProperty(sequenceKey), 1);

        properties.setProperty(sequenceKey, String.valueOf(sequence + 1));
        saveProperties(properties);

        String receiptNumber = formatReceiptNumber(storeCode, deviceId, sequence);
        return new ReceiptNumber(receiptNumber, deviceId, sequence);
    }

    public static synchronized DeviceReceiptSettings getDeviceReceiptSettings(int locationId) throws IOException {
        Properties properties = loadProperties();
        String deviceId = getOrCreateDeviceId(properties);
        String storeCode = formatStoreCode(locationId);
        String sequenceKey = "next_receipt_sequence." + storeCode + "." + deviceId;
        int nextSequence = parsePositiveInt(properties.getProperty(sequenceKey), 1);
        String nextReceiptPreview = formatReceiptNumber(storeCode, deviceId, nextSequence);

        return new DeviceReceiptSettings(CONFIG_PATH, deviceId, storeCode, nextSequence, nextReceiptPreview);
    }

    public static synchronized String updateDeviceId(String deviceId) throws IOException {
        String sanitizedDeviceId = sanitizeDeviceId(deviceId);
        if (sanitizedDeviceId.isBlank()) {
            throw new IllegalArgumentException("Device ID cannot be blank.");
        }

        Properties properties = loadProperties();
        properties.setProperty(DEVICE_ID_KEY, sanitizedDeviceId);
        saveProperties(properties);
        return sanitizedDeviceId;
    }

    public static String previewSanitizedDeviceId(String deviceId) {
        return sanitizeDeviceId(deviceId);
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }

    private static Properties loadProperties() throws IOException {
        Properties properties = new Properties();
        if (Files.exists(CONFIG_PATH)) {
            try (InputStream inputStream = Files.newInputStream(CONFIG_PATH)) {
                properties.load(inputStream);
            }
        }
        return properties;
    }

    private static void saveProperties(Properties properties) throws IOException {
        Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "SmartStock local device receipt settings");
        }
    }

    private static String getOrCreateDeviceId(Properties properties) throws IOException {
        String deviceId = sanitizeDeviceId(properties.getProperty(DEVICE_ID_KEY));
        if (deviceId.isBlank()) {
            deviceId = sanitizeDeviceId("POS-" + getHostName());
            if (deviceId.isBlank()) {
                deviceId = "POS-LOCAL";
            }
            properties.setProperty(DEVICE_ID_KEY, deviceId);
            saveProperties(properties);
        }
        return deviceId;
    }

    private static String formatStoreCode(int locationId) {
        return String.format("S%03d", locationId);
    }

    private static int parsePositiveInt(String value, int fallback) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private static String formatReceiptNumber(String storeCode, String deviceId, int sequence) {
        return "R-" + storeCode + "-" + deviceId + "-" + String.format("%0" + RECEIPT_SEQUENCE_PADDING + "d", sequence);
    }

    private static String sanitizeDeviceId(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim().toUpperCase().replaceAll("[^A-Z0-9-]+", "-");
        cleaned = cleaned.replaceAll("-+", "-");
        cleaned = cleaned.replaceAll("^-|-$", "");
        return cleaned;
    }

    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "LOCAL";
        }
    }

    public record ReceiptNumber(String receiptNumber, String deviceId, int sequence) {
    }

    public record DeviceReceiptSettings(Path configPath, String deviceId, String storeCode, int nextSequence, String nextReceiptPreview) {
    }
}
