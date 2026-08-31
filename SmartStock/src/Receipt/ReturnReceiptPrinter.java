package Receipt;

import managers.CompanyCustomizationManager;
import managers.HardwareSettingsManager;

import javax.print.*;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class ReturnReceiptPrinter {
    private ReturnReceiptPrinter() { }

    public static void printToPosPrinter(ReturnReceiptData receipt, HardwareSettingsManager.PosPrinter printer) throws PrintException {
        printToPosPrinter(receipt, printer, false);
    }

    public static void printToPosPrinter(ReturnReceiptData receipt, HardwareSettingsManager.PosPrinter printer,
                                         boolean reprint) throws PrintException {
        HardwareSettingsManager.PrintFormat format = printer == null ? HardwareSettingsManager.PrintFormat.RECEIPT_40 : printer.printFormat();
        CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
        if (format == HardwareSettingsManager.PrintFormat.RECEIPT_40) {
            byte[] content = formatEscPos(receipt, settings, reprint);
            if (NativeEscPosTransport.sendIfEnabled(content) != null) return;
        }
        PrintService service = printer == null ? PrintServiceLookup.lookupDefaultPrintService()
                : HardwareSettingsManager.findPrintService(printer.systemName());
        if (service == null) throw new PrintException(printer == null ? "No default printer is configured."
                : "Configured printer was not found: " + printer.systemName());
        if (format == HardwareSettingsManager.PrintFormat.LETTER) {
            printLetter(receipt, service, settings, reprint);
            return;
        }
        service.createPrintJob().print(new SimpleDoc(formatEscPos(receipt, settings, reprint), DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
    }

    private static byte[] formatEscPos(ReturnReceiptData receipt, CompanyCustomizationManager.ReceiptSettings settings,
                                       boolean reprint) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("\u001b@".getBytes(StandardCharsets.US_ASCII));
        body.writeBytes(ReceiptLogoSupport.escPosLogo(
                CompanyCustomizationManager.loadReceiptLogo(settings)));
        body.writeBytes(ReturnReceiptFormatter.formatText(receipt, settings, reprint).getBytes(StandardCharsets.US_ASCII));
        ReceiptFormatter.appendEscPosBarcode(body, receipt.returnReceiptNumber());
        body.writeBytes("\n\n\n\u001dVB\0".getBytes(StandardCharsets.US_ASCII));
        return body.toByteArray();
    }

    private static void printLetter(ReturnReceiptData receipt, PrintService service,
                                    CompanyCustomizationManager.ReceiptSettings settings,
                                    boolean reprint) throws PrintException {
        PrinterJob job=PrinterJob.getPrinterJob();
        try { job.setPrintService(service); } catch (PrinterException ex) { throw new PrintException(ex); }
        String[] lines=ReturnReceiptFormatter.formatLetterText(receipt,settings,reprint).split("\\R",-1);
        BufferedImage logo=CompanyCustomizationManager.loadReceiptLogo(settings);
        job.setPrintable((graphics,page,pageIndex)->{if(pageIndex>0)return Printable.NO_SUCH_PAGE;Graphics2D g=(Graphics2D)graphics;g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,10));int x=(int)page.getImageableX(),y=(int)page.getImageableY()+14;y+=ReceiptLogoSupport.drawLetterLogo(g,page,logo);for(String line:lines){g.drawString(line,x,y);y+=14;}if(ReceiptBarcodeRenderer.hasScannableText(receipt.returnReceiptNumber())){int width=260,height=58,barcodeX=(int)(page.getImageableX()+(page.getImageableWidth()-width)/2);BufferedImage barcode=ReceiptBarcodeRenderer.renderCode128(receipt.returnReceiptNumber(),width,height);ReceiptBarcodeRenderer.drawBarcodeFit(g,barcode,barcodeX,y+8,width,height);g.setFont(new Font(Font.MONOSPACED,Font.PLAIN,10));String value=receipt.returnReceiptNumber().trim();int textX=(int)(page.getImageableX()+(page.getImageableWidth()-g.getFontMetrics().stringWidth(value))/2);g.drawString(value,textX,y+height+24);}return Printable.PAGE_EXISTS;});
        try { job.print(); } catch (PrinterException ex) { throw new PrintException(ex); }
    }
}
