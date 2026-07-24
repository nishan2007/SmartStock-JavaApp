package services;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Creates complete, scroll-independent Balance Sheet exports. */
public final class BalanceSheetExportService {
    private static final int WIDTH = 1600, MARGIN = 60, GAP = 24, ROW_HEIGHT = 34;
    private static final Color PAGE = new Color(246, 248, 251), CARD = Color.WHITE;
    private static final Color TEXT = new Color(17, 24, 39), MUTED = new Color(91, 101, 115);
    private static final Color BORDER = new Color(203, 213, 225), ACCENT = new Color(30, 64, 175);
    private static final NumberFormat MONEY = NumberFormat.getCurrencyInstance(Locale.US);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

    private BalanceSheetExportService() { }

    public static void writePng(File output, BalanceSheetService.BalanceSheet sheet) throws IOException {
        if (!ImageIO.write(render(sheet), "png", output)) throw new IOException("PNG export support is unavailable.");
    }

    public static void writePdf(File output, BalanceSheetService.BalanceSheet sheet) throws IOException {
        BufferedImage report = render(sheet);
        PDRectangle pageSize = new PDRectangle(PDRectangle.LETTER.getHeight(), PDRectangle.LETTER.getWidth());
        int sliceHeight = Math.max(1, (int) (report.getWidth() * (pageSize.getHeight() - 48f) / (pageSize.getWidth() - 48f)));
        try (PDDocument document = new PDDocument()) {
            for (int y = 0; y < report.getHeight(); y += sliceHeight) {
                int height = Math.min(sliceHeight, report.getHeight() - y);
                BufferedImage slice = report.getSubimage(0, y, report.getWidth(), height);
                PDPage page = new PDPage(pageSize); document.addPage(page);
                PDImageXObject image = LosslessFactory.createFromImage(document, slice);
                float scale = Math.min((pageSize.getWidth() - 48f) / slice.getWidth(), (pageSize.getHeight() - 48f) / slice.getHeight());
                float w = slice.getWidth() * scale, h = slice.getHeight() * scale;
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.drawImage(image, (pageSize.getWidth() - w) / 2f, (pageSize.getHeight() - h) / 2f, w, h);
                }
            }
            document.save(output);
        }
    }

    public static void print(Component parent, BalanceSheetService.BalanceSheet sheet) throws Exception {
        BufferedImage report = render(sheet);
        PrinterJob job = PrinterJob.getPrinterJob(); job.setJobName("SmartStock Balance Sheet");
        job.setPrintable((graphics, format, pageIndex) -> printPage(graphics, format, pageIndex, report));
        if (job.printDialog()) job.print();
    }

    private static int printPage(Graphics graphics, PageFormat format, int pageIndex, BufferedImage report) {
        double scale = format.getImageableWidth() / report.getWidth();
        int sourceHeight = Math.max(1, (int) Math.floor(format.getImageableHeight() / scale));
        int y = pageIndex * sourceHeight;
        if (y >= report.getHeight()) return Printable.NO_SUCH_PAGE;
        int height = Math.min(sourceHeight, report.getHeight() - y);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.translate(format.getImageableX(), format.getImageableY()); g.scale(scale, scale);
            g.drawImage(report, 0, 0, report.getWidth(), height, 0, y, report.getWidth(), y + height, null);
        } finally { g.dispose(); }
        return Printable.PAGE_EXISTS;
    }

    public static BufferedImage render(BalanceSheetService.BalanceSheet sheet) {
        List<Section> sections = sections(sheet);
        int columnWidth = (WIDTH - MARGIN * 2 - GAP) / 2;
        int leftHeight = 0, rightHeight = 0;
        for (Section section : sections) {
            int h = sectionHeight(section);
            if (leftHeight <= rightHeight) leftHeight += h + GAP; else rightHeight += h + GAP;
        }
        int height = 300 + Math.max(leftHeight, rightHeight) - GAP + 30;
        BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(PAGE); g.fillRect(0, 0, WIDTH, height); drawHeader(g, sheet);
            int[] y = {300, 300};
            for (Section section : sections) {
                int column = y[0] <= y[1] ? 0 : 1;
                drawSection(g, MARGIN + column * (columnWidth + GAP), y[column], columnWidth, section);
                y[column] += sectionHeight(section) + GAP;
            }
        } finally { g.dispose(); }
        return image;
    }

    private static void drawHeader(Graphics2D g, BalanceSheetService.BalanceSheet s) {
        g.setColor(TEXT); g.setFont(new Font("SansSerif", Font.BOLD, 42)); g.drawString("Balance Sheet", MARGIN, 80);
        String period = s.periodStart() == null ? "Current report" : s.periodStart() + " to " + s.periodEnd();
        g.setFont(new Font("SansSerif", Font.PLAIN, 20)); g.setColor(MUTED); g.drawString(period, MARGIN, 116);
        if (s.submittedAt() != null) g.drawString("Submitted by " + text(s.submittedByName()) + " on " + DATE_TIME.format(s.submittedAt()), MARGIN, 148);
        int metricWidth = (WIDTH - MARGIN * 2 - GAP * 3) / 4;
        metric(g, MARGIN, 180, metricWidth, "Balance BF", s.balanceBf());
        metric(g, MARGIN + (metricWidth + GAP), 180, metricWidth, "Cash In Hand", s.cashInHand());
        metric(g, MARGIN + 2 * (metricWidth + GAP), 180, metricWidth, "Balance CF", s.balanceCf());
        BigDecimal net = zero(s.totalIncome()).subtract(zero(s.totalExpenses())).subtract(zero(s.totalPayables()));
        metric(g, MARGIN + 3 * (metricWidth + GAP), 180, metricWidth, net.signum() < 0 ? "Deficit" : "Surplus", net.abs());
    }

    private static void metric(Graphics2D g, int x, int y, int width, String label, BigDecimal value) {
        g.setColor(CARD); g.fillRoundRect(x, y, width, 86, 14, 14); g.setColor(BORDER); g.drawRoundRect(x, y, width, 86, 14, 14);
        g.setColor(MUTED); g.setFont(new Font("SansSerif", Font.BOLD, 15)); g.drawString(label.toUpperCase(Locale.ROOT), x + 18, y + 28);
        g.setColor(TEXT); g.setFont(new Font("SansSerif", Font.BOLD, 24)); g.drawString(money(value), x + 18, y + 62);
    }

    private static void drawSection(Graphics2D g, int x, int y, int width, Section section) {
        int height = sectionHeight(section); g.setColor(CARD); g.fillRoundRect(x, y, width, height, 14, 14); g.setColor(BORDER); g.drawRoundRect(x, y, width, height, 14, 14);
        g.setColor(ACCENT); g.fillRoundRect(x, y, width, 48, 14, 14); g.fillRect(x, y + 25, width, 23);
        g.setColor(Color.WHITE); g.setFont(new Font("SansSerif", Font.BOLD, 19)); g.drawString(section.title(), x + 18, y + 31);
        int rowY = y + 48; g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        for (Row row : section.rows()) {
            rowY += ROW_HEIGHT; g.setColor(MUTED); g.drawString(clip(g, row.label(), width - 190), x + 18, rowY - 10);
            g.setColor(TEXT); String amount = row.amount(); int amountWidth = g.getFontMetrics().stringWidth(amount); g.drawString(amount, x + width - 18 - amountWidth, rowY - 10);
            g.setColor(BORDER); g.drawLine(x + 18, rowY, x + width - 18, rowY);
        }
        if (section.total() != null) {
            rowY += ROW_HEIGHT; g.setFont(new Font("SansSerif", Font.BOLD, 16)); g.setColor(TEXT); g.drawString("Total", x + 18, rowY - 9);
            String total = money(section.total()); g.drawString(total, x + width - 18 - g.getFontMetrics().stringWidth(total), rowY - 9);
        }
    }

    private static int sectionHeight(Section section) { return 48 + ROW_HEIGHT * (section.rows().size() + (section.total() == null ? 0 : 1)) + 10; }
    private static List<Section> sections(BalanceSheetService.BalanceSheet s) {
        List<Section> result = new ArrayList<>();
        result.add(lines("Income", s.income(), s.totalIncome())); result.add(lines("Accounts Receivable", s.receivables(), s.totalReceivables()));
        result.add(lines("Expenses", s.expenses(), s.totalExpenses())); result.add(lines("Accounts Payable", s.payables(), s.totalPayables()));
        result.add(lines("Drawer Cash In Hand", s.drawerCash(), s.cashInHand())); result.add(lines("Device Sales", s.deviceSales(), null));
        result.add(lines("Device Orders", s.deviceOrders(), null)); result.add(lines("Device Payments", s.devicePayments(), null));
        result.add(lines("Account Payments", s.accountPayments(), null));
        List<Row> bank = safe(s.bankTransactions()).stream().map(v -> new Row(v.transaction() + " (" + text(v.direction()) + ")", money(v.amount()))).toList();
        result.add(new Section("Bank Transactions", bank, null));
        List<Row> cheques = safe(s.pendingCheques()).stream().map(v -> new Row(text(v.sourceLabel()) + detail(v.payer(), v.reference()), money(v.amount()))).toList();
        result.add(new Section("Cheques To Deposit", cheques, null)); result.add(lines("Drawer Match Checks", s.drawerChecks(), null));
        if (s.notes() != null && !s.notes().isBlank()) result.add(new Section("Notes", List.of(new Row(s.notes().replace('\n', ' '), "")), null));
        return result;
    }

    private static Section lines(String title, List<BalanceSheetService.SheetLine> lines, BigDecimal total) {
        return new Section(title, safe(lines).stream().map(v -> new Row(text(v.label()), money(v.amount()))).toList(), total);
    }
    private static String detail(String payer, String reference) { String value = payer == null || payer.isBlank() ? "" : " - " + payer; return value + (reference == null || reference.isBlank() ? "" : " / " + reference); }
    private static String clip(Graphics2D g, String value, int width) { if (g.getFontMetrics().stringWidth(value) <= width) return value; String s = value; while (s.length() > 1 && g.getFontMetrics().stringWidth(s + "...") > width) s = s.substring(0, s.length() - 1); return s + "..."; }
    private static String money(BigDecimal value) { return MONEY.format(zero(value)); }
    private static BigDecimal zero(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private static String text(String value) { return value == null || value.isBlank() ? "-" : value.trim(); }
    private static <T> List<T> safe(List<T> value) { return value == null ? List.of() : value; }
    private record Row(String label, String amount) { }
    private record Section(String title, List<Row> rows, BigDecimal total) { }
}
