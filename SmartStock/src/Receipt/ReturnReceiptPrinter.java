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
        PrintService service = printer == null ? PrintServiceLookup.lookupDefaultPrintService()
                : HardwareSettingsManager.findPrintService(printer.systemName());
        if (service == null) throw new PrintException(printer == null ? "No default printer is configured."
                : "Configured printer was not found: " + printer.systemName());
        HardwareSettingsManager.PrintFormat format = printer == null ? HardwareSettingsManager.PrintFormat.RECEIPT_40 : printer.printFormat();
        CompanyCustomizationManager.ReceiptSettings settings = CompanyCustomizationManager.loadReceiptSettings();
        if (format == HardwareSettingsManager.PrintFormat.LETTER) {
            printLetter(receipt, service, settings);
            return;
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("\u001b@".getBytes(StandardCharsets.US_ASCII));
        body.writeBytes(ReceiptLogoSupport.escPosLogo(
                CompanyCustomizationManager.loadReceiptLogo(settings)));
        body.writeBytes((ReturnReceiptFormatter.formatText(receipt, settings) + "\n\n\n\u001dVB\0")
                .getBytes(StandardCharsets.US_ASCII));
        service.createPrintJob().print(new SimpleDoc(body.toByteArray(), DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
    }

    private static void printLetter(ReturnReceiptData receipt, PrintService service,
                                    CompanyCustomizationManager.ReceiptSettings settings) throws PrintException {
        PrinterJob job=PrinterJob.getPrinterJob();
        try { job.setPrintService(service); } catch (PrinterException ex) { throw new PrintException(ex); }
        String[] lines=ReturnReceiptFormatter.formatLetterText(receipt,settings).split("\\R",-1);
        BufferedImage logo=CompanyCustomizationManager.loadReceiptLogo(settings);
        job.setPrintable((graphics,page,pageIndex)->{if(pageIndex>0)return Printable.NO_SUCH_PAGE;graphics.setFont(new Font(Font.MONOSPACED,Font.PLAIN,10));int x=(int)page.getImageableX(),y=(int)page.getImageableY()+14;y+=ReceiptLogoSupport.drawLetterLogo((Graphics2D)graphics,page,logo);for(String line:lines){graphics.drawString(line,x,y);y+=14;}return Printable.PAGE_EXISTS;});
        try { job.print(); } catch (PrinterException ex) { throw new PrintException(ex); }
    }
}
