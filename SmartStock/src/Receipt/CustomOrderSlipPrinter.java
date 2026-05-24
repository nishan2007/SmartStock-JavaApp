package Receipt;

import managers.CompanyCustomizationManager;
import managers.HardwareSettingsManager;

import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.SimpleDoc;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.awt.image.BufferedImage;

public class CustomOrderSlipPrinter {
    private CustomOrderSlipPrinter() {
    }

    public static void print(String orderNumber) throws PrintException {
        try {
            print(CustomOrderSlipBuilder.buildFromOrderNumber(orderNumber));
        } catch (PrintException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PrintException(ex);
        }
    }

    public static void print(CustomOrderSlipData data) throws PrintException {
        CompanyCustomizationManager.CustomOrderSlipSettings slipSettings = CompanyCustomizationManager.loadCustomOrderSlipSettings();
        if (!slipSettings.enabled()) {
            return;
        }

        PrinterSelection printerSelection = resolvePrinterSelection();
        PrintService service = printerSelection.service();
        if (service == null) {
            throw new PrintException("No printer is configured for custom order slips.");
        }

        CompanyCustomizationManager.ReceiptSettings receiptSettings = CompanyCustomizationManager.loadReceiptSettings();
        if (printerSelection.printFormat() != HardwareSettingsManager.PrintFormat.LETTER) {
            printReceipt40ToService(data, service, receiptSettings, slipSettings);
            return;
        }

        BufferedImage logo = CompanyCustomizationManager.loadCompanyLogo(receiptSettings);
        PrinterJob job = PrinterJob.getPrinterJob();
        try {
            job.setPrintService(service);
        } catch (PrinterException ex) {
            throw new PrintException(ex);
        }
        PageFormat pageFormat = createLetterPageFormat(job);
        job.setPrintable((graphics, format, pageIndex) -> printPage(graphics, format, pageIndex, data, receiptSettings, slipSettings, logo), pageFormat);
        try {
            job.print();
        } catch (PrinterException ex) {
            throw new PrintException(ex);
        }
    }

    public static void printToPosPrinter(CustomOrderSlipData data,
                                         HardwareSettingsManager.PosPrinter printer,
                                         HardwareSettingsManager.PrintFormat printFormat) throws PrintException {
        CompanyCustomizationManager.CustomOrderSlipSettings slipSettings = CompanyCustomizationManager.loadCustomOrderSlipSettings();
        if (!slipSettings.enabled()) {
            return;
        }

        PrintService service = printer == null ? PrintServiceLookup.lookupDefaultPrintService() : HardwareSettingsManager.findPrintService(printer.systemName());
        if (service == null) {
            throw new PrintException("No printer is configured for custom order slips.");
        }

        CompanyCustomizationManager.ReceiptSettings receiptSettings = CompanyCustomizationManager.loadReceiptSettings();
        HardwareSettingsManager.PrintFormat resolvedFormat = printFormat == null ? HardwareSettingsManager.PrintFormat.RECEIPT_40 : printFormat;
        if (resolvedFormat != HardwareSettingsManager.PrintFormat.LETTER) {
            printReceipt40ToService(data, service, receiptSettings, slipSettings);
            return;
        }

        BufferedImage logo = CompanyCustomizationManager.loadCompanyLogo(receiptSettings);
        PrinterJob job = PrinterJob.getPrinterJob();
        try {
            job.setPrintService(service);
            PageFormat pageFormat = createLetterPageFormat(job);
            job.setPrintable((graphics, format, pageIndex) -> printPage(graphics, format, pageIndex, data, receiptSettings, slipSettings, logo), pageFormat);
            job.print();
        } catch (PrinterException ex) {
            throw new PrintException(ex);
        }
    }

    public static String format40ColumnPreview(CustomOrderSlipData data,
                                               CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                               CompanyCustomizationManager.CustomOrderSlipSettings slipSettings) {
        return CustomOrderSlipFormatter.format40Column(data, receiptSettings, slipSettings);
    }

    private static void printReceipt40ToService(CustomOrderSlipData data,
                                                PrintService service,
                                                CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                                CompanyCustomizationManager.CustomOrderSlipSettings slipSettings) throws PrintException {
        byte[] bytes = CustomOrderSlipFormatter.formatEscPos40Column(data, receiptSettings, slipSettings);
        DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        Doc doc = new SimpleDoc(bytes, flavor, null);
        service.createPrintJob().print(doc, null);
    }

    private static PrinterSelection resolvePrinterSelection() throws PrintException {
        try {
            HardwareSettingsManager.PosPrinter printer = HardwareSettingsManager.getDefaultReceiptPrinter();
            if (printer != null) {
                PrintService service = HardwareSettingsManager.findPrintService(printer.systemName());
                if (service != null) {
                    return new PrinterSelection(service, printer.printFormat());
                }
            }
        } catch (Exception ex) {
            throw new PrintException(ex);
        }
        return new PrinterSelection(PrintServiceLookup.lookupDefaultPrintService(), HardwareSettingsManager.PrintFormat.RECEIPT_40);
    }

    private static int printPage(Graphics graphics,
                                 PageFormat pageFormat,
                                 int pageIndex,
                                 CustomOrderSlipData data,
                                 CompanyCustomizationManager.ReceiptSettings receiptSettings,
                                 CompanyCustomizationManager.CustomOrderSlipSettings slipSettings,
                                 BufferedImage logo) {
        if (pageIndex > 0) {
            return Printable.NO_SUCH_PAGE;
        }
        int x = (int) pageFormat.getImageableX();
        int y = (int) pageFormat.getImageableY();
        int width = (int) pageFormat.getImageableWidth();
        int height = Math.min((int) pageFormat.getImageableHeight(), 360);
        CustomOrderSlipRenderer.paintSlip((Graphics2D) graphics, x, y, width, height, data, receiptSettings, slipSettings, logo);
        return Printable.PAGE_EXISTS;
    }

    private static PageFormat createLetterPageFormat(PrinterJob job) {
        PageFormat pageFormat = job.defaultPage();
        Paper paper = new Paper();
        double width = 8.5 * 72.0;
        double height = 11.0 * 72.0;
        double margin = 0.35 * 72.0;
        paper.setSize(width, height);
        paper.setImageableArea(margin, margin, width - (margin * 2), height - (margin * 2));
        pageFormat.setPaper(paper);
        pageFormat.setOrientation(PageFormat.PORTRAIT);
        return pageFormat;
    }

    private record PrinterSelection(PrintService service, HardwareSettingsManager.PrintFormat printFormat) {
    }
}
