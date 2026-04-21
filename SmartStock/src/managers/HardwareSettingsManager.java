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
            PrintFormat printFormat = PrintFormat.fromConfigValue(properties.getProperty("printer." + i + ".print_format"));
            if (!displayName.isBlank() && !systemName.isBlank()) {
                printers.add(new PosPrinter(displayName, systemName, defaultReceiptPrinter, printFormat));
            }
        }

        if (!printers.isEmpty() && printers.stream().noneMatch(PosPrinter::defaultReceiptPrinter)) {
            PosPrinter first = printers.get(0);
            printers.set(0, new PosPrinter(first.displayName(), first.systemName(), true, first.printFormat()));
        }

        return printers;
    }

    public static void saveConfiguredPrinters(List<PosPrinter> printers) throws IOException {
        Properties properties = new Properties();
        List<PosPrinter> cleanPrinters = normalizePrinters(printers);

        properties.setProperty("printer.count", String.valueOf(cleanPrinters.size()));
        for (int i = 0; i < cleanPrinters.size(); i++) {
            PosPrinter printer = cleanPrinters.get(i);
            properties.setProperty("printer." + i + ".display_name", printer.displayName());
            properties.setProperty("printer." + i + ".system_name", printer.systemName());
            properties.setProperty("printer." + i + ".default_receipt", String.valueOf(printer.defaultReceiptPrinter()));
            properties.setProperty("printer." + i + ".print_format", printer.printFormat().configValue());
        }

        Files.createDirectories(CONFIG_PATH.getParent());
        try (OutputStream outputStream = Files.newOutputStream(CONFIG_PATH)) {
            properties.store(outputStream, "SmartStock local hardware settings");
        }
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

    public static PrintService findPrintService(String systemName) {
        if (systemName == null || systemName.isBlank()) {
            return null;
        }

        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        for (PrintService service : services) {
            if (systemName.equals(service.getName())) {
                return service;
            }
        }
        return null;
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
            cleanPrinters.add(new PosPrinter(printer.displayName().trim(), printer.systemName().trim(), defaultPrinter, printer.printFormat()));
        }

        boolean defaultAlreadySet = false;
        List<PosPrinter> normalized = new ArrayList<>();
        for (PosPrinter printer : cleanPrinters) {
            boolean defaultPrinter = printer.defaultReceiptPrinter() && !defaultAlreadySet;
            defaultAlreadySet = defaultAlreadySet || defaultPrinter;
            normalized.add(new PosPrinter(printer.displayName(), printer.systemName(), defaultPrinter, printer.printFormat()));
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

    public record PosPrinter(String displayName, String systemName, boolean defaultReceiptPrinter, PrintFormat printFormat) {
        public PosPrinter(String displayName, String systemName, boolean defaultReceiptPrinter) {
            this(displayName, systemName, defaultReceiptPrinter, PrintFormat.RECEIPT_40);
        }

        public PosPrinter {
            displayName = Objects.requireNonNullElse(displayName, "");
            systemName = Objects.requireNonNullElse(systemName, "");
            printFormat = printFormat == null ? PrintFormat.RECEIPT_40 : printFormat;
        }

        @Override
        public String toString() {
            return defaultReceiptPrinter ? displayName + " (Default)" : displayName;
        }
    }
}
