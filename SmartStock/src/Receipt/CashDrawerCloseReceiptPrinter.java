package Receipt;

import managers.CompanyCustomizationManager;
import managers.HardwareSettingsManager;
import models.CashDrawerSession;
import utils.CurrencyFormatter;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class CashDrawerCloseReceiptPrinter {
    private static final NumberFormat CURRENCY = CurrencyFormatter.create();
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private CashDrawerCloseReceiptPrinter() {
    }

    public static void print(CashDrawerSession session, BigDecimal cashInHand,
                             BigDecimal floatCash, List<BreakdownLine> breakdown,
                             List<String> cashHandlers,
                             HardwareSettingsManager.PosPrinter printer)
            throws PrintException {
        PrintService service = printer == null
                ? PrintServiceLookup.lookupDefaultPrintService()
                : HardwareSettingsManager.findPrintService(printer.systemName());
        if (service == null) {
            throw new PrintException(printer == null
                    ? "No default printer is configured."
                    : "Configured printer was not found: " + printer.systemName());
        }
        HardwareSettingsManager.PrintFormat format = printer == null
                ? HardwareSettingsManager.PrintFormat.RECEIPT_40
                : printer.printFormat();
        String text = formatText(session, cashInHand, floatCash, breakdown, cashHandlers);
        if (format == HardwareSettingsManager.PrintFormat.LETTER) {
            printLetter(service, text);
            return;
        }
        byte[] content = ("\u001b@" + text + "\n\n\n\u001dVB\0")
                .getBytes(StandardCharsets.US_ASCII);
        DocPrintJob job = service.createPrintJob();
        job.print(new SimpleDoc(content, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
    }

    public static String formatText(CashDrawerSession session, BigDecimal cashInHand,
                                    BigDecimal floatCash, List<BreakdownLine> breakdown,
                                    List<String> cashHandlers) {
        String companyName = "SmartStock";
        String footerLine = "";
        try {
            CompanyCustomizationManager.ReceiptSettings settings =
                    CompanyCustomizationManager.loadReceiptSettings();
            companyName = settings.companyName();
            footerLine = settings.footerLine();
        } catch (RuntimeException ignored) {
            // Closing figures must remain printable if branding settings are unavailable.
        }
        StringBuilder receipt = new StringBuilder();
        center(receipt, companyName, 40);
        center(receipt, "DRAW CLOSE RECEIPT", 40);
        receipt.append("----------------------------------------\n");
        pair(receipt, "Drawer", session.drawerName());
        pair(receipt, "Session", String.valueOf(session.sessionId()));
        pair(receipt, "Closed", session.closedAt() == null ? "" :
                DATE_TIME.format(session.closedAt().toLocalDateTime()));
        List<String> handlers = cashHandlers == null || cashHandlers.isEmpty()
                ? List.of(session.mainCashierName() == null ? "" : session.mainCashierName())
                : cashHandlers;
        for (int index = 0; index < handlers.size(); index++) {
            pair(receipt, "Cashier " + (index + 1), handlers.get(index));
        }
        pair(receipt, "Balanced By", session.balancedByName());
        receipt.append("----------------------------------------\n");
        receipt.append(String.format("%-7s %10s %10s %10s%n", "$$", "QTY", "FLOAT", "CIH"));
        for (BreakdownLine line : breakdown == null ? List.<BreakdownLine>of() : breakdown) {
            receipt.append(String.format("%-7s %10d %10d %10d%n",
                    money(BigDecimal.valueOf(line.denomination())),
                    line.quantity(), line.floatQuantity(), line.cihQuantity()));
        }
        receipt.append(String.format("%-7s %10s %10s %10s%n", "TOTAL",
                money(session.countedCash()), money(floatCash), money(cashInHand)));
        receipt.append("----------------------------------------\n");
        pair(receipt, "Set Cash", money(session.openingCash()));
        pair(receipt, "Expected Cash", money(session.expectedCash()));
        pair(receipt, "Counted Cash", money(session.countedCash()));
        pair(receipt, "Variance", money(session.variance()));
        pair(receipt, "CIH", money(cashInHand));
        pair(receipt, "Float", money(floatCash));
        pair(receipt, "Cash to Remove", money(session.cashToRemove()));
        receipt.append("----------------------------------------\n");
        center(receipt, footerLine, 40);
        return receipt.toString();
    }

    private static void printLetter(PrintService service, String text) throws PrintException {
        PrinterJob job = PrinterJob.getPrinterJob();
        try {
            job.setPrintService(service);
            String[] lines = text.split("\\R", -1);
            job.setPrintable((Graphics graphics, PageFormat page, int pageIndex) -> {
                if (pageIndex != 0) return Printable.NO_SUCH_PAGE;
                graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
                int lineHeight = graphics.getFontMetrics().getHeight();
                int x = (int) page.getImageableX();
                int y = (int) page.getImageableY() + lineHeight;
                for (String line : lines) {
                    graphics.drawString(line, x, y);
                    y += lineHeight;
                }
                return Printable.PAGE_EXISTS;
            });
            job.setJobName("SmartStock Draw Close Receipt");
            job.print();
        } catch (PrinterException ex) {
            throw new PrintException(ex);
        }
    }

    private static String money(BigDecimal amount) {
        return CURRENCY.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private static void center(StringBuilder text, String value, int width) {
        String clean = trim(value, width);
        text.append(" ".repeat(Math.max((width - clean.length()) / 2, 0)))
                .append(clean).append('\n');
    }

    private static void pair(StringBuilder text, String label, String value) {
        String cleanValue = value == null ? "" : value;
        int available = Math.max(40 - label.length() - 1, 1);
        cleanValue = trim(cleanValue, available);
        text.append(label)
                .append(" ".repeat(Math.max(40 - label.length() - cleanValue.length(), 1)))
                .append(cleanValue).append('\n');
    }

    private static String trim(String value, int width) {
        String clean = value == null ? "" : value.trim();
        return clean.length() <= width ? clean : clean.substring(0, width);
    }

    public record BreakdownLine(int denomination, int quantity,
                                int floatQuantity, int cihQuantity) { }
}
