package managers;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public class HardwareSettingsManager {
    private static final Path CONFIG_PATH = Path.of(System.getProperty("user.home"), ".smartstock", "hardware.properties");

    private HardwareSettingsManager() {
    }

    public static Path getConfigPath() {
        return CONFIG_PATH;
    }

    public static List<PosPrinter> getConfiguredPrinters() throws IOException {
        Properties properties = loadProperties();
        int count = parseInt(properties.getProperty("printer.count"), 0);
        List<PosPrinter> printers = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            String displayName = properties.getProperty("printer." + i + ".display_name", "").trim();
            String systemName = properties.getProperty("printer." + i + ".system_name", "").trim();
            boolean defaultReceiptPrinter = Boolean.parseBoolean(properties.getProperty("printer." + i + ".default_receipt", "false"));
            boolean defaultOrderLabelPrinter = Boolean.parseBoolean(properties.getProperty("printer." + i + ".default_order_label", "false"));
            PrintFormat printFormat = PrintFormat.fromConfigValue(properties.getProperty("printer." + i + ".print_format"));
            if (!displayName.isBlank() && !systemName.isBlank()) {
                printers.add(new PosPrinter(displayName, systemName, defaultReceiptPrinter, defaultOrderLabelPrinter, printFormat));
            }
        }

        if (!printers.isEmpty() && printers.stream().noneMatch(PosPrinter::defaultReceiptPrinter)) {
            PosPrinter first = printers.get(0);
            printers.set(0, new PosPrinter(first.displayName(), first.systemName(), true, first.defaultOrderLabelPrinter(), first.printFormat()));
        }

        return printers;
    }

    public static void saveConfiguredPrinters(List<PosPrinter> printers) throws IOException {
        Properties properties = loadProperties();
        List<PosPrinter> cleanPrinters = normalizePrinters(printers);

        properties.setProperty("printer.count", String.valueOf(cleanPrinters.size()));
        for (int i = 0; i < cleanPrinters.size(); i++) {
            PosPrinter printer = cleanPrinters.get(i);
            properties.setProperty("printer." + i + ".display_name", printer.displayName());
            properties.setProperty("printer." + i + ".system_name", printer.systemName());
            properties.setProperty("printer." + i + ".default_receipt", String.valueOf(printer.defaultReceiptPrinter()));
            properties.setProperty("printer." + i + ".default_order_label", String.valueOf(printer.defaultOrderLabelPrinter()));
            properties.setProperty("printer." + i + ".print_format", printer.printFormat().configValue());
        }

        Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "SmartStock local hardware settings");
        }
    }

    public static EpsonSettings getEpsonSettings() throws IOException {
        Properties p = loadProperties();
        return new EpsonSettings(
                Boolean.parseBoolean(p.getProperty("epson.enabled", "false")),
                Boolean.parseBoolean(p.getProperty("epson.automatic_cut", "true")),
                Boolean.parseBoolean(p.getProperty("epson.cash_drawer_enabled", "false")),
                parseInt(p.getProperty("epson.drawer_pin"), 0),
                parseInt(p.getProperty("epson.drawer_on_ms"), 120),
                parseInt(p.getProperty("epson.drawer_off_ms"), 240),
                Boolean.parseBoolean(p.getProperty("epson.print_dialog_fallback", "true")));
    }

    public static void saveEpsonSettings(EpsonSettings settings) throws IOException {
        EpsonSettings clean = settings == null ? EpsonSettings.defaults() : settings;
        Properties p = loadProperties();
        p.setProperty("epson.enabled", String.valueOf(clean.enabled()));
        p.setProperty("epson.automatic_cut", String.valueOf(clean.automaticCut()));
        p.setProperty("epson.cash_drawer_enabled", String.valueOf(clean.cashDrawerEnabled()));
        p.setProperty("epson.drawer_pin", String.valueOf(clean.drawerPin()));
        p.setProperty("epson.drawer_on_ms", String.valueOf(clean.drawerOnMillis()));
        p.setProperty("epson.drawer_off_ms", String.valueOf(clean.drawerOffMillis()));
        p.setProperty("epson.print_dialog_fallback", String.valueOf(clean.printDialogFallback()));
        Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            p.store(outputStream, "SmartStock local hardware settings");
        }
    }

    public static NativeEthernetPrinterSettings getNativeEthernetPrinterSettings() throws IOException {
        Properties p = loadProperties();
        return new NativeEthernetPrinterSettings(
                Boolean.parseBoolean(p.getProperty("escpos.ethernet.enabled", "false")),
                p.getProperty("escpos.ethernet.host", "10.1.1.23"),
                parseInt(p.getProperty("escpos.ethernet.port"), 9100),
                parseInt(p.getProperty("escpos.ethernet.connect_timeout_ms"), 3000));
    }

    public static void saveNativeEthernetPrinterSettings(NativeEthernetPrinterSettings settings) throws IOException {
        NativeEthernetPrinterSettings clean = settings == null ? NativeEthernetPrinterSettings.defaults() : settings;
        Properties p = loadProperties();
        p.setProperty("escpos.ethernet.enabled", String.valueOf(clean.enabled()));
        p.setProperty("escpos.ethernet.host", clean.host());
        p.setProperty("escpos.ethernet.port", String.valueOf(clean.port()));
        p.setProperty("escpos.ethernet.connect_timeout_ms", String.valueOf(clean.connectTimeoutMillis()));
        Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            p.store(outputStream, "SmartStock local hardware settings");
        }
    }

    public static ReceiptPrinterDestination getDefaultReceiptPrinterDestination() throws IOException {
        Properties properties = loadProperties();
        String configured = properties.getProperty("receipt.default_destination", "").trim();
        if (!configured.isBlank()) return ReceiptPrinterDestination.fromConfigValue(configured);
        // Preserve the behavior of registers configured before the destination selector existed.
        return Boolean.parseBoolean(properties.getProperty("escpos.ethernet.enabled", "false"))
                ? ReceiptPrinterDestination.ETHERNET
                : ReceiptPrinterDestination.WINDOWS_QUEUE;
    }

    public static void saveDefaultReceiptPrinterDestination(ReceiptPrinterDestination destination) throws IOException {
        ReceiptPrinterDestination clean = destination == null
                ? ReceiptPrinterDestination.WINDOWS_QUEUE : destination;
        Properties properties = loadProperties();
        properties.setProperty("receipt.default_destination", clean.configValue());
        Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "SmartStock local hardware settings");
        }
    }

    public static BadgePrinterSettings getBadgePrinterSettings() throws IOException {
        return readBadgePrinterSettings(loadProperties());
    }

    static BadgePrinterSettings readBadgePrinterSettings(Properties p) {
        return new BadgePrinterSettings(
                Boolean.parseBoolean(p.getProperty("badge_printer.enabled", "false")),
                p.getProperty("badge_printer.system_name", ""),
                p.getProperty("badge_printer.model", BadgePrinterSettings.MAGICARD_600),
                Boolean.parseBoolean(p.getProperty("badge_printer.duplex", "true")),
                Boolean.parseBoolean(p.getProperty("badge_printer.show_print_dialog", "true")));
    }

    public static void saveBadgePrinterSettings(BadgePrinterSettings settings) throws IOException {
        BadgePrinterSettings clean = settings == null ? BadgePrinterSettings.defaults() : settings;
        Properties p = loadProperties();
        writeBadgePrinterSettings(p, clean);
        Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            p.store(outputStream, "SmartStock local hardware settings");
        }
    }

    static void writeBadgePrinterSettings(Properties p, BadgePrinterSettings clean) {
        p.setProperty("badge_printer.enabled", String.valueOf(clean.enabled()));
        p.setProperty("badge_printer.system_name", clean.systemName());
        p.setProperty("badge_printer.model", clean.model());
        p.setProperty("badge_printer.duplex", String.valueOf(clean.duplex()));
        p.setProperty("badge_printer.show_print_dialog", String.valueOf(clean.showPrintDialog()));
    }

    public static List<String> getAvailablePrinterNames() {
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        List<String> printerNames = new ArrayList<>();
        for (PrintService service : services) {
            printerNames.add(service.getName());
        }
        printerNames.sort(String.CASE_INSENSITIVE_ORDER);
        return printerNames;
    }

    public static PosPrinter getDefaultReceiptPrinter() throws IOException {
        return getConfiguredPrinters().stream()
                .filter(PosPrinter::defaultReceiptPrinter)
                .findFirst()
                .orElse(null);
    }

    public static PosPrinter getDefaultOrderLabelPrinter() throws IOException {
        return getConfiguredPrinters().stream()
                .filter(PosPrinter::defaultOrderLabelPrinter)
                .findFirst()
                .orElse(null);
    }

    public static PrintService findPrintService(String systemName) {
        return findPrintService(systemName, PrintServiceLookup.lookupPrintServices(null, null));
    }

    static PrintService findPrintService(String systemName, PrintService[] services) {
        if (systemName == null || systemName.isBlank()) {
            return null;
        }
        for (PrintService service : services) {
            if (systemName.equals(service.getName())) {
                return service;
            }
        }
        return null;
    }

    public static boolean isBadgePrinterAvailable(BadgePrinterSettings settings) {
        return settings != null && settings.enabled() && findPrintService(settings.systemName()) != null;
    }

    private static List<PosPrinter> normalizePrinters(List<PosPrinter> printers) {
        List<PosPrinter> cleanPrinters = new ArrayList<>();
        if (printers == null) {
            return cleanPrinters;
        }

        boolean hasDefault = printers.stream().anyMatch(PosPrinter::defaultReceiptPrinter);
        for (int i = 0; i < printers.size(); i++) {
            PosPrinter printer = printers.get(i);
            if (printer == null || printer.displayName().isBlank() || printer.systemName().isBlank()) {
                continue;
            }
            boolean defaultPrinter = printer.defaultReceiptPrinter() || (!hasDefault && cleanPrinters.isEmpty());
            cleanPrinters.add(new PosPrinter(printer.displayName().trim(), printer.systemName().trim(), defaultPrinter,
                    printer.defaultOrderLabelPrinter(), printer.printFormat()));
        }

        boolean defaultAlreadySet = false;
        boolean orderLabelDefaultAlreadySet = false;
        List<PosPrinter> normalized = new ArrayList<>();
        for (PosPrinter printer : cleanPrinters) {
            boolean defaultPrinter = printer.defaultReceiptPrinter() && !defaultAlreadySet;
            defaultAlreadySet = defaultAlreadySet || defaultPrinter;
            boolean orderLabelPrinter = printer.defaultOrderLabelPrinter() && !orderLabelDefaultAlreadySet;
            orderLabelDefaultAlreadySet = orderLabelDefaultAlreadySet || orderLabelPrinter;
            normalized.add(new PosPrinter(printer.displayName(), printer.systemName(), defaultPrinter,
                    orderLabelPrinter, printer.printFormat()));
        }
        return normalized;
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

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    public enum PrintFormat {
        RECEIPT_40("RECEIPT_40", "40 Column Receipt"),
        LETTER("LETTER", "Letter Size");

        private final String configValue;
        private final String label;

        PrintFormat(String configValue, String label) {
            this.configValue = configValue;
            this.label = label;
        }

        public String configValue() {
            return configValue;
        }

        public static PrintFormat fromConfigValue(String value) {
            if (value == null || value.isBlank()) {
                return RECEIPT_40;
            }
            for (PrintFormat format : values()) {
                if (format.configValue.equalsIgnoreCase(value.trim()) || format.name().equalsIgnoreCase(value.trim())) {
                    return format;
                }
            }
            return RECEIPT_40;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum ReceiptPrinterDestination {
        ETHERNET("ETHERNET", "Ethernet receipt printer"),
        WINDOWS_QUEUE("WINDOWS_QUEUE", "Windows / USB printer queue");

        private final String configValue;
        private final String label;

        ReceiptPrinterDestination(String configValue, String label) {
            this.configValue = configValue;
            this.label = label;
        }

        public String configValue() { return configValue; }

        public static ReceiptPrinterDestination fromConfigValue(String value) {
            if (value != null) {
                for (ReceiptPrinterDestination destination : values()) {
                    if (destination.configValue.equalsIgnoreCase(value.trim())
                            || destination.name().equalsIgnoreCase(value.trim())) return destination;
                }
            }
            return WINDOWS_QUEUE;
        }

        @Override public String toString() { return label; }
    }

    public record EpsonSettings(boolean enabled, boolean automaticCut, boolean cashDrawerEnabled,
                                int drawerPin, int drawerOnMillis, int drawerOffMillis,
                                boolean printDialogFallback) {
        public EpsonSettings {
            drawerPin = drawerPin == 1 ? 1 : 0;
            drawerOnMillis = clampPulse(drawerOnMillis, 120);
            drawerOffMillis = clampPulse(drawerOffMillis, 240);
        }

        public static EpsonSettings defaults() {
            return new EpsonSettings(false, true, false, 0, 120, 240, true);
        }

        private static int clampPulse(int value, int fallback) {
            return value < 2 || value > 510 ? fallback : value;
        }
    }

    public record NativeEthernetPrinterSettings(boolean enabled, String host, int port,
                                                 int connectTimeoutMillis) {
        public NativeEthernetPrinterSettings {
            host = Objects.requireNonNullElse(host, "").trim();
            if (enabled && host.isBlank()) {
                throw new IllegalArgumentException("Ethernet printer host is required.");
            }
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Ethernet printer port must be from 1 to 65535.");
            }
            if (connectTimeoutMillis < 250 || connectTimeoutMillis > 30000) {
                throw new IllegalArgumentException("Ethernet printer timeout must be from 250 to 30000 ms.");
            }
        }

        public static NativeEthernetPrinterSettings defaults() {
            return new NativeEthernetPrinterSettings(false, "10.1.1.23", 9100, 3000);
        }

        public String endpoint() {
            return host + ":" + port;
        }
    }

    public record BadgePrinterSettings(boolean enabled, String systemName, String model,
                                       boolean duplex, boolean showPrintDialog) {
        public static final String MAGICARD_600 = "Magicard 600";

        public BadgePrinterSettings {
            systemName = Objects.requireNonNullElse(systemName, "").trim();
            model = Objects.requireNonNullElse(model, MAGICARD_600).trim();
            if (model.isBlank()) model = MAGICARD_600;
            if (enabled && systemName.isBlank()) {
                throw new IllegalArgumentException("Choose an installed Windows queue for the badge printer.");
            }
        }

        public static BadgePrinterSettings defaults() {
            return new BadgePrinterSettings(false, "", MAGICARD_600, true, true);
        }
    }

    public record PosPrinter(String displayName, String systemName, boolean defaultReceiptPrinter,
                             boolean defaultOrderLabelPrinter, PrintFormat printFormat) {
        public PosPrinter(String displayName, String systemName, boolean defaultReceiptPrinter) {
            this(displayName, systemName, defaultReceiptPrinter, false, PrintFormat.RECEIPT_40);
        }

        public PosPrinter(String displayName, String systemName, boolean defaultReceiptPrinter, PrintFormat printFormat) {
            this(displayName, systemName, defaultReceiptPrinter, false, printFormat);
        }

        public PosPrinter {
            displayName = Objects.requireNonNullElse(displayName, "");
            systemName = Objects.requireNonNullElse(systemName, "");
            printFormat = printFormat == null ? PrintFormat.RECEIPT_40 : printFormat;
        }

        @Override
        public String toString() {
            if (defaultReceiptPrinter && defaultOrderLabelPrinter) return displayName + " (Receipt + Order Label Default)";
            if (defaultReceiptPrinter) return displayName + " (Receipt Default)";
            if (defaultOrderLabelPrinter) return displayName + " (Order Label Default)";
            return displayName;
        }
    }
}
