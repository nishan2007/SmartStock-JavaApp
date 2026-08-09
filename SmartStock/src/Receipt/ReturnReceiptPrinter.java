package Receipt;

import managers.CompanyCustomizationManager;
import managers.HardwareSettingsManager;

import javax.print.*;
import java.awt.Font;
import java.awt.print.*;
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
        byte[] body = ("\u001b@" + ReturnReceiptFormatter.formatText(receipt, settings) + "\n\n\n\u001dVB\0")
                .getBytes(StandardCharsets.US_ASCII);
        service.createPrintJob().print(new SimpleDoc(body, DocFlavor.BYTE_ARRAY.AUTOSENSE, null), null);
    }

    private static void printLetter(ReturnReceiptData receipt, PrintService service,
                                    CompanyCustomizationManager.ReceiptSettings settings) throws PrintException {
        PrinterJob job=PrinterJob.getPrinterJob();
        try { job.setPrintService(service); } catch (PrinterException ex) { throw new PrintException(ex); }
        String[] lines=ReturnReceiptFormatter.formatLetterText(receipt,settings).split("\\R",-1);
        job.setPrintable((graphics,page,pageIndex)->{if(pageIndex>0)return Printable.NO_SUCH_PAGE;graphics.setFont(new Font(Font.MONOSPACED,Font.PLAIN,10));int x=(int)page.getImageableX(),y=(int)page.getImageableY()+14;for(String line:lines){graphics.drawString(line,x,y);y+=14;}return Printable.PAGE_EXISTS;});
        try { job.print(); } catch (PrinterException ex) { throw new PrintException(ex); }
    }
}
