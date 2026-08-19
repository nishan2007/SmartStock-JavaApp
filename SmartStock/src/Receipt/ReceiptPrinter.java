package Receipt;

import managers.CompanyCustomizationManager;
import managers.HardwareSettingsManager;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.swing.SwingUtilities;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.awt.image.BufferedImage;

public class ReceiptPrinter {
    private ReceiptPrinter() {
    }

    public static void printToDefaultPrinter(ReceiptData receipt) throws PrintException {
        PrintService service = PrintServiceLookup.lookupDefaultPrintService();
        if (service == null) {
            throw new PrintException("No default printer is configured.");
        }

        printToService(receipt, service, HardwareSettingsManager.PrintFormat.RECEIPT_40);
    }

    public static void printToPosPrinter(ReceiptData receipt, HardwareSettingsManager.PosPrinter printer) throws PrintException {
        printToPosPrinter(receipt, printer, false, false);
    }

    public static EpsonReceiptPrintService.PrintResult printToPosPrinter(ReceiptData receipt,
            HardwareSettingsManager.PosPrinter printer, boolean openDrawer, boolean reprint) throws PrintException {
        if (printer != null && printer.printFormat() == HardwareSettingsManager.PrintFormat.RECEIPT_40) {
            EpsonReceiptPrintService.PrintResult result = EpsonReceiptPrintService.print(receipt, printer, openDrawer, reprint);
            if (!result.successful()) {
                try {
                    if (HardwareSettingsManager.getEpsonSettings().printDialogFallback()) {
                        printWithSystemDialog(receipt, reprint);
                        return EpsonReceiptPrintService.PrintResult.queued("Selected in system print dialog");
                    }
                } catch (Exception fallbackFailure) {
                    throw new PrintException(result.message() + " Print dialog fallback failed: " + fallbackFailure.getMessage());
                }
                throw new PrintException(result.message());
            }
            return result;
        }
        printToPosPrinter(receipt, printer, printer == null ? HardwareSettingsManager.PrintFormat.RECEIPT_40 : printer.printFormat());
        return EpsonReceiptPrintService.PrintResult.queued(printer == null ? "" : printer.systemName());
    }

    public static void printToPosPrinter(ReceiptData receipt, HardwareSettingsManager.PosPrinter printer, HardwareSettingsManager.PrintFormat printFormat) throws PrintException {
        if (printer == null) {
            PrintService service = PrintServiceLookup.lookupDefaultPrintService();
            if (service == null) {
                throw new PrintException("No default printer is configured.");
            }
            printToService(receipt, service, printFormat);
            return;
        }

        PrintService service = HardwareSettingsManager.findPrintService(printer.systemName());
        if (service == null) {
            throw new PrintException("Configured printer was not found: " + printer.systemName());
        }

        printToService(receipt, service, printFormat == null ? printer.printFormat() : printFormat);
    }

    private static void printToService(ReceiptData receipt, PrintService service, HardwareSettingsManager.PrintFormat printFormat) throws PrintException {
        CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
        if (printFormat == HardwareSettingsManager.PrintFormat.LETTER) {
            printLetterToService(receipt, service, settings);
            return;
        }

        byte[] bytes = EpsonReceiptPrintService.composeJob(ReceiptFormatter.formatEscPos(receipt, settings),
                loadEpsonSettings(), false);
        DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        Doc doc = new SimpleDoc(bytes, flavor, null);
        DocPrintJob job = service.createPrintJob();
        job.print(doc, null);
    }

    private static HardwareSettingsManager.EpsonSettings loadEpsonSettings() throws PrintException {
        try {
            return HardwareSettingsManager.getEpsonSettings();
        } catch (java.io.IOException ex) {
            throw new PrintException(ex);
        }
    }

    private static void printWithSystemDialog(ReceiptData receipt, boolean reprint) throws Exception {
        final Exception[] failure = new Exception[1];
        Runnable dialogTask = () -> {
            PrinterJob job = PrinterJob.getPrinterJob();
            String prefix = reprint ? "DUPLICATE / REPRINT\n" : "";
            String[] lines = (prefix + ReceiptFormatter.formatText(receipt)).split("\\R", -1);
            Font font = new Font(Font.MONOSPACED, Font.PLAIN, 9);
            Paper paper = new Paper();
            double width = 80.0 / 25.4 * 72.0;
            double height = Math.max((lines.length + 4) * 12.0, 144.0);
            paper.setSize(width, height);
            paper.setImageableArea(6, 6, width - 12, height - 12);
            PageFormat format = new PageFormat();
            format.setPaper(paper);
            job.setPrintable((graphics, pageFormat, pageIndex) -> {
                if (pageIndex > 0) return Printable.NO_SUCH_PAGE;
                graphics.setFont(font);
                int y = (int) pageFormat.getImageableY() + graphics.getFontMetrics().getAscent();
                for (String line : lines) {
                    graphics.drawString(line, (int) pageFormat.getImageableX(), y);
                    y += graphics.getFontMetrics().getHeight();
                }
                return Printable.PAGE_EXISTS;
            }, format);
            if (!job.printDialog()) {
                failure[0] = new PrinterException("Printing was cancelled.");
                return;
            }
            try { job.print(); } catch (PrinterException ex) { failure[0] = ex; }
        };
        if (SwingUtilities.isEventDispatchThread()) dialogTask.run();
        else SwingUtilities.invokeAndWait(dialogTask);
        if (failure[0] != null) throw failure[0];
    }

    private static void printLetterToService(ReceiptData receipt, PrintService service, CompanyCustomizationManager.ReceiptSettings settings) throws PrintException {
        PrinterJob job = PrinterJob.getPrinterJob();
        try {
            job.setPrintService(service);
        } catch (PrinterException ex) {
            throw new PrintException(ex);
        }

        String[] lines = ReceiptFormatter.formatLetterText(receipt, settings).split("\\R", -1);
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 10);
        BufferedImage logo = CompanyCustomizationManager.loadReceiptLogo(settings);
        job.setPrintable(
                (graphics, pageFormat, pageIndex) -> printLetterPage(graphics, pageFormat, pageIndex, lines, font, logo, receipt),
                createLetterPageFormat(job)
        );

        try {
            job.print();
        } catch (PrinterException ex) {
            throw new PrintException(ex);
        }
    }

    private static int printLetterPage(Graphics graphics, PageFormat pageFormat, int pageIndex, String[] lines, Font font, BufferedImage logo, ReceiptData receipt) {
        Graphics2D g2 = (Graphics2D) graphics;
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setFont(font);

        int lineHeight = g2.getFontMetrics().getHeight();
        int x = (int) pageFormat.getImageableX();
        int y = (int) pageFormat.getImageableY() + lineHeight;
        int logoSpace = pageIndex == 0 ? drawLetterLogo(g2, pageFormat, logo) : 0;
        int barcodeSpace = ReceiptBarcodeRenderer.hasScannableReceiptNumber(receipt) ? 96 : 0;
        y += logoSpace;
        int standardLinesPerPage = Math.max((int) pageFormat.getImageableHeight() / lineHeight, 1);
        int lastPageLines = Math.max(((int) pageFormat.getImageableHeight() - barcodeSpace) / lineHeight, 1);
        int firstPageLines = Math.max(((int) pageFormat.getImageableHeight() - logoSpace) / lineHeight, 1);
        int firstPageCapacity = lines.length <= firstPageLines ? Math.max(((int) pageFormat.getImageableHeight() - logoSpace - barcodeSpace) / lineHeight, 1) : firstPageLines;
        int firstPageCount = Math.min(lines.length, firstPageCapacity);
        int remainingLines = Math.max(lines.length - firstPageCount, 0);
        int pagesAfterFirst = remainingLines == 0 ? 0 : (int) Math.ceil(remainingLines / (double) lastPageLines);
        int totalPages = 1 + pagesAfterFirst;

        if (pageIndex >= totalPages) {
            return Printable.NO_SUCH_PAGE;
        }

        int linesPerPage;
        int startLine;
        if (pageIndex == 0) {
            linesPerPage = totalPages == 1 ? firstPageCapacity : firstPageCount;
            startLine = 0;
        } else {
            linesPerPage = lastPageLines;
            startLine = firstPageCount + ((pageIndex - 1) * lastPageLines);
        }
        int endLine = Math.min(startLine + linesPerPage, lines.length);
        for (int i = startLine; i < endLine; i++) {
            g2.drawString(lines[i], x, y);
            y += lineHeight;
        }
        if (pageIndex == totalPages - 1) {
            drawLetterBarcode(g2, pageFormat, receipt, y + 10);
        }

        return Printable.PAGE_EXISTS;
    }

    private static int drawLetterLogo(Graphics2D graphics, PageFormat pageFormat, BufferedImage logo) {
        if (logo == null) {
            return 0;
        }

        int maxWidth = 220;
        int maxHeight = 90;
        double scale = Math.min((double) maxWidth / logo.getWidth(), (double) maxHeight / logo.getHeight());
        scale = Math.min(scale, 1.0);
        int width = Math.max((int) Math.round(logo.getWidth() * scale), 1);
        int height = Math.max((int) Math.round(logo.getHeight() * scale), 1);
        int x = (int) (pageFormat.getImageableX() + ((pageFormat.getImageableWidth() - width) / 2));
        int y = (int) pageFormat.getImageableY();
        graphics.drawImage(logo, x, y, width, height, null);
        return height + 16;
    }

    private static void drawLetterBarcode(Graphics2D graphics, PageFormat pageFormat, ReceiptData receipt, int y) {
        if (!ReceiptBarcodeRenderer.hasScannableReceiptNumber(receipt)) {
            return;
        }
        int width = 260;
        int height = 58;
        BufferedImage barcode = ReceiptBarcodeRenderer.renderCode128(receipt.getReceiptNumber(), width, height);
        int x = (int) (pageFormat.getImageableX() + ((pageFormat.getImageableWidth() - width) / 2));
        ReceiptBarcodeRenderer.drawBarcodeFit(graphics, barcode, x, y, width, height);
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        int textWidth = graphics.getFontMetrics().stringWidth(receipt.getReceiptNumber());
        int textX = (int) (pageFormat.getImageableX() + ((pageFormat.getImageableWidth() - textWidth) / 2));
        graphics.drawString(receipt.getReceiptNumber(), textX, y + height + graphics.getFontMetrics().getAscent() + 4);
    }

    private static PageFormat createLetterPageFormat(PrinterJob job) {
        PageFormat pageFormat = job.defaultPage();
        Paper paper = new Paper();
        double width = 8.5 * 72.0;
        double height = 11.0 * 72.0;
        double margin = 0.5 * 72.0;

        paper.setSize(width, height);
        paper.setImageableArea(
                margin,
                margin,
                width - (margin * 2),
                height - (margin * 2)
        );

        pageFormat.setPaper(paper);
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        return pageFormat;
    }
}
