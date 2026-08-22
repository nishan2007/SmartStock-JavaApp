package Receipt;

import managers.HardwareSettingsManager;

import javax.print.PrintException;
import javax.print.PrintService;
import javax.swing.JOptionPane;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/** Prints a compact adhesive label that identifies a custom order. */
public final class CustomOrderLabelPrinter {
    public static final double LABEL_WIDTH_INCHES = 2.0;
    public static final double LABEL_HEIGHT_INCHES = 1.0;
    public static final int MAX_LABEL_COUNT = 100;
    private static final int RENDER_WIDTH = 600;
    private static final int RENDER_HEIGHT = 300;
    private static final DateTimeFormatter DUE_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, uuuu");

    private CustomOrderLabelPrinter() { }

    public static Integer promptLabelCount(Component parent) {
        String value = "1";
        while (true) {
            value = JOptionPane.showInputDialog(parent, "Number of order labels to print:", value);
            if (value == null) return null;
            try {
                return parseLabelCount(value);
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(parent, ex.getMessage(), "Order Labels", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    public static int parseLabelCount(String value) {
        try {
            int count = Integer.parseInt(value == null ? "" : value.trim());
            if (count < 1 || count > MAX_LABEL_COUNT) throw new NumberFormatException();
            return count;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Enter a whole number from 1 to " + MAX_LABEL_COUNT + ".");
        }
    }

    public static void print(CustomOrderSlipData data, int count) throws PrintException {
        if (data == null) throw new PrintException("Custom order label data is unavailable.");
        parseLabelCount(String.valueOf(count));
        HardwareSettingsManager.PosPrinter printer;
        try {
            printer = HardwareSettingsManager.getDefaultOrderLabelPrinter();
        } catch (Exception ex) {
            throw new PrintException(ex);
        }
        boolean receiptPrinterFallback = printer == null;
        if (receiptPrinterFallback) {
            try {
                printer = HardwareSettingsManager.getDefaultReceiptPrinter();
            } catch (Exception ex) {
                throw new PrintException(ex);
            }
        }
        if (printer == null) throw new PrintException("No order label or receipt printer is configured.");
        PrintService service = HardwareSettingsManager.findPrintService(printer.systemName());
        if (service == null) {
            throw new PrintException("The configured " + (receiptPrinterFallback ? "receipt" : "custom order label")
                    + " printer is unavailable: " + printer.displayName());
        }
        if (receiptPrinterFallback
                && printer.printFormat() == HardwareSettingsManager.PrintFormat.RECEIPT_40) {
            printToReceiptService(data, count, service);
        } else {
            printToService(data, count, service);
        }
    }

    static void printToReceiptService(CustomOrderSlipData data, int count, PrintService service) throws PrintException {
        byte[] content = formatEscPosReceiptLabels(data, count);
        service.createPrintJob().print(new javax.print.SimpleDoc(content, javax.print.DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
    }

    static byte[] formatEscPosReceiptLabels(CustomOrderSlipData data, int count) {
        parseLabelCount(String.valueOf(count));
        BufferedImage label = scaleForReceipt(render(data), 384);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int copy = 0; copy < count; copy++) {
            out.writeBytes(new byte[]{0x1B, 0x40, 0x1B, 0x61, 0x01});
            appendRaster(out, label);
            out.writeBytes(new byte[]{0x0A, 0x1B, 0x64, 0x03, 0x1D, 0x56, 0x42, 0x00});
        }
        return out.toByteArray();
    }

    private static BufferedImage scaleForReceipt(BufferedImage source, int width) {
        int height = Math.max(1, (int) Math.round(source.getHeight() * ((double) width / source.getWidth())));
        BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private static void appendRaster(ByteArrayOutputStream out, BufferedImage image) {
        int bytesPerRow = (image.getWidth() + 7) / 8;
        out.writeBytes(new byte[]{0x1D, 0x76, 0x30, 0x00,
                (byte) (bytesPerRow & 0xFF), (byte) ((bytesPerRow >> 8) & 0xFF),
                (byte) (image.getHeight() & 0xFF), (byte) ((image.getHeight() >> 8) & 0xFF)});
        for (int y = 0; y < image.getHeight(); y++) {
            for (int xByte = 0; xByte < bytesPerRow; xByte++) {
                int value = 0;
                for (int bit = 0; bit < 8; bit++) {
                    int x = xByte * 8 + bit;
                    if (x < image.getWidth()) {
                        Color color = new Color(image.getRGB(x, y));
                        double luminance = color.getRed() * 0.299 + color.getGreen() * 0.587 + color.getBlue() * 0.114;
                        if (luminance < 160) value |= 0x80 >> bit;
                    }
                }
                out.write(value);
            }
        }
    }

    static void printToService(CustomOrderSlipData data, int count, PrintService service) throws PrintException {
        BufferedImage label = render(data);
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("SmartStock Order Labels " + data.orderNumber());
        try {
            job.setPrintService(service);
            PageFormat format = createPageFormat(job);
            job.setPrintable((graphics, pageFormat, pageIndex) -> printPage(graphics, pageFormat, pageIndex, label, count), format);
            job.print();
        } catch (PrinterException ex) {
            throw new PrintException(ex);
        }
    }

    public static BufferedImage render(CustomOrderSlipData data) {
        if (data == null || data.orderNumber() == null || data.orderNumber().trim().isBlank()) {
            throw new IllegalArgumentException("Order number is required for an order label.");
        }
        BufferedImage image = new BufferedImage(RENDER_WIDTH, RENDER_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.setColor(Color.BLACK);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            drawFitted(g, "ORDER " + data.orderNumber().trim(), 24, 12, 552, 48, Font.BOLD, 40);
            BufferedImage barcode = ReceiptBarcodeRenderer.renderCode128(data.orderNumber().trim(), 552, 112);
            ReceiptBarcodeRenderer.drawBarcodeFit(g, barcode, 24, 66, 552, 112);
            drawFitted(g, clean(data.customerName(), "Customer not provided"), 24, 190, 552, 42, Font.BOLD, 31);
            String due = data.dueDate() == null ? "Due: No due date" : "Due: " + DUE_DATE_FORMAT.format(data.dueDate());
            drawFitted(g, due, 24, 238, 552, 36, Font.PLAIN, 27);
        } finally {
            g.dispose();
        }
        return image;
    }

    private static int printPage(Graphics graphics, PageFormat format, int pageIndex, BufferedImage label, int count) {
        if (pageIndex < 0 || pageIndex >= count) return Printable.NO_SUCH_PAGE;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(format.getImageableX(), format.getImageableY());
            g.drawImage(label, 0, 0, (int) format.getImageableWidth(), (int) format.getImageableHeight(), null);
        } finally {
            g.dispose();
        }
        return Printable.PAGE_EXISTS;
    }

    static PageFormat createPageFormat(PrinterJob job) {
        double width = LABEL_WIDTH_INCHES * 72.0;
        double height = LABEL_HEIGHT_INCHES * 72.0;
        double margin = 0.04 * 72.0;
        Paper paper = new Paper();
        paper.setSize(width, height);
        paper.setImageableArea(margin, margin, width - margin * 2, height - margin * 2);
        PageFormat format = job.defaultPage();
        format.setOrientation(PageFormat.PORTRAIT);
        format.setPaper(paper);
        return format;
    }

    private static void drawFitted(Graphics2D g, String text, int x, int y, int width, int height, int style, int maxSize) {
        String value = clean(text, "");
        int size = maxSize;
        Font font = new Font("SansSerif", style, size);
        FontMetrics metrics = g.getFontMetrics(font);
        while (size > 14 && metrics.stringWidth(value) > width) {
            font = new Font("SansSerif", style, --size);
            metrics = g.getFontMetrics(font);
        }
        if (metrics.stringWidth(value) > width) {
            while (value.length() > 1 && metrics.stringWidth(value + "...") > width) value = value.substring(0, value.length() - 1);
            value += "...";
        }
        g.setFont(font);
        g.drawString(value, x, y + Math.min(height, metrics.getAscent()));
    }

    private static String clean(String value, String fallback) {
        String clean = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return clean.isBlank() ? fallback : clean;
    }
}
