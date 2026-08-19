package Receipt;

import managers.HardwareSettingsManager;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintException;
import javax.print.PrintService;
import javax.print.SimpleDoc;
import java.io.ByteArrayOutputStream;

/** Owns Epson ESC/POS transport and keeps device commands out of receipt formatting. */
public final class EpsonReceiptPrintService {
    private EpsonReceiptPrintService() { }

    public static PrintResult print(ReceiptData receipt, HardwareSettingsManager.PosPrinter printer,
                                    boolean openDrawer, boolean reprint) {
        if (receipt == null) return PrintResult.failure(PrintStatus.INVALID_CONFIGURATION, "", "Receipt data is required.");
        if (printer == null) return PrintResult.failure(PrintStatus.INVALID_CONFIGURATION, "", "No receipt printer is configured.");
        PrintService service = HardwareSettingsManager.findPrintService(printer.systemName());
        if (service == null) {
            return PrintResult.failure(PrintStatus.QUEUE_MISSING, printer.systemName(),
                    "Configured printer is unavailable: " + printer.systemName());
        }
        try {
            HardwareSettingsManager.EpsonSettings settings = HardwareSettingsManager.getEpsonSettings();
            byte[] receiptBytes = ReceiptFormatter.formatEscPos(receipt,
                    managers.CompanyCustomizationManager.loadReceiptSettings(), reprint);
            byte[] jobBytes = composeJob(receiptBytes, settings, openDrawer && !reprint);
            submit(service, jobBytes, "SmartStock receipt " + receipt.getReceiptNumber());
            return PrintResult.queued(service.getName());
        } catch (PrintException ex) {
            return PrintResult.failure(PrintStatus.JOB_REJECTED, service.getName(), safeMessage(ex, "Windows rejected the print job."));
        } catch (Exception ex) {
            return PrintResult.failure(PrintStatus.TRANSPORT_FAILURE, service.getName(), safeMessage(ex, "Printer transport failed."));
        }
    }

    public static PrintResult testControl(HardwareSettingsManager.PosPrinter printer, ControlAction action) {
        if (printer == null) return PrintResult.failure(PrintStatus.INVALID_CONFIGURATION, "", "Select a configured printer first.");
        PrintService service = HardwareSettingsManager.findPrintService(printer.systemName());
        if (service == null) return PrintResult.failure(PrintStatus.QUEUE_MISSING, printer.systemName(), "Configured printer is unavailable.");
        try {
            HardwareSettingsManager.EpsonSettings settings = HardwareSettingsManager.getEpsonSettings();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.writeBytes(new byte[]{0x1B, 0x40});
            if (action == ControlAction.DRAWER) appendDrawer(out, settings);
            if (action == ControlAction.CUT) appendCut(out);
            submit(service, out.toByteArray(), "SmartStock Epson " + action.name().toLowerCase() + " test");
            return PrintResult.queued(service.getName());
        } catch (Exception ex) {
            return PrintResult.failure(ex instanceof PrintException ? PrintStatus.JOB_REJECTED : PrintStatus.TRANSPORT_FAILURE,
                    service.getName(), safeMessage(ex, "Epson hardware test failed."));
        }
    }

    static byte[] composeJob(byte[] receiptBytes, HardwareSettingsManager.EpsonSettings settings, boolean openDrawer) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(receiptBytes == null ? new byte[0] : receiptBytes);
        if (settings.enabled() && openDrawer && settings.cashDrawerEnabled()) appendDrawer(out, settings);
        // Preserve the legacy receipt behavior (automatic cut) until Epson controls are explicitly enabled.
        if (!settings.enabled() || settings.automaticCut()) appendCut(out);
        return out.toByteArray();
    }

    private static void appendDrawer(ByteArrayOutputStream out, HardwareSettingsManager.EpsonSettings settings) {
        int on = Math.max(1, Math.min(255, settings.drawerOnMillis() / 2));
        int off = Math.max(1, Math.min(255, settings.drawerOffMillis() / 2));
        out.writeBytes(new byte[]{0x1B, 0x70, (byte) settings.drawerPin(), (byte) on, (byte) off});
    }

    private static void appendCut(ByteArrayOutputStream out) {
        out.writeBytes(new byte[]{0x1B, 0x64, 0x03, 0x1D, 0x56, 0x42, 0x00});
    }

    private static void submit(PrintService service, byte[] bytes, String jobName) throws PrintException {
        Doc doc = new SimpleDoc(bytes, DocFlavor.BYTE_ARRAY.AUTOSENSE, null);
        DocPrintJob job = service.createPrintJob();
        javax.print.attribute.HashPrintRequestAttributeSet attributes = new javax.print.attribute.HashPrintRequestAttributeSet();
        attributes.add(new javax.print.attribute.standard.JobName(jobName, null));
        job.print(doc, attributes);
    }

    private static String safeMessage(Exception ex, String fallback) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? fallback : message;
    }

    public enum ControlAction { CUT, DRAWER }
    public enum PrintStatus { QUEUED, QUEUE_MISSING, JOB_REJECTED, TRANSPORT_FAILURE, INVALID_CONFIGURATION }

    public record PrintResult(PrintStatus status, String printerName, String message) {
        public boolean successful() { return status == PrintStatus.QUEUED; }
        public static PrintResult queued(String printer) {
            return new PrintResult(PrintStatus.QUEUED, printer, "Receipt submitted to the Windows print queue.");
        }
        public static PrintResult failure(PrintStatus status, String printer, String message) {
            return new PrintResult(status, printer == null ? "" : printer, message);
        }
    }
}
