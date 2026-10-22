import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.Card;
import javax.smartcardio.TerminalFactory;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only PC/SC presence and ACR122 firmware checks. Never reads card data. */
public final class NfcReaderDiagnostics {
    public static void main(String[] args) throws Exception {
        int seconds = args.length == 0 ? 0 : Integer.parseInt(args[0]);
        if (args.length > 1 || seconds < 0 || seconds > 120) {
            throw new IllegalArgumentException("Usage: NfcReaderDiagnostics [watch-seconds: 0..120]");
        }
        var terminals = TerminalFactory.getDefault().terminals().list();
        System.out.println("PC/SC readers: " + terminals.size());
        if (terminals.isEmpty()) {
            throw new IllegalStateException("No PC/SC reader found. Check USB, driver and Smart Card service.");
        }
        Map<CardTerminal, Boolean> previous = new LinkedHashMap<>();
        for (var terminal : terminals) {
            System.out.println("Reader: " + terminal.getName());
            if (terminal.getName().toUpperCase(java.util.Locale.ROOT).contains("ACR122")) {
                System.out.println("ACR122 detected: physical SmartStock cards are supported; "
                        + "Apple Wallet VAS support is not established by reader detection.");
                inspectAcr122(terminal);
            }
        }
        System.out.println("Presence only: no UID, badge data or payment data is read. "
                + "Presence does not prove Wallet badge compatibility.");
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(seconds).toNanos();
        do {
            for (var terminal : terminals) {
                try {
                    boolean present = terminal.isCardPresent();
                    Boolean old = previous.put(terminal, present);
                    if (old == null || old != present) {
                        System.out.println(Instant.now() + " " + terminal.getName()
                                + ": " + (present ? "contactless device present" : "no contactless device"));
                    }
                } catch (CardException ex) {
                    throw new IllegalStateException("Reader became unavailable: " + terminal.getName(), ex);
                }
            }
            if (System.nanoTime() >= deadline) break;
            Thread.sleep(250);
        } while (true);
        System.out.println("Presence check complete. Wallet credential exchange was not tested.");
    }

    private static void inspectAcr122(CardTerminal terminal) {
        // ACS ACR122U API, sections 6.3/6.4 and Appendix A.
        // These commands address the reader, not the phone or a payment applet.
        Card reader = null;
        try {
            reader = terminal.connect("direct");
            int escape = System.getProperty("os.name").startsWith("Windows")
                    ? (0x31 << 16) | (3500 << 2) : 0x42000000 + 3500;
            byte[] version = reader.transmitControlCommand(escape,
                    new byte[]{(byte) 0xff, 0, 0x48, 0, 0});
            String firmware = new String(version, StandardCharsets.US_ASCII);
            if (!firmware.matches("ACR122[A-Za-z0-9._-]{1,32}")) {
                System.out.println("Firmware: reader returned an unrecognized response (not logged).");
                return;
            }
            System.out.println("Firmware: " + firmware);
            byte[] polling = reader.transmitControlCommand(escape,
                    new byte[]{(byte) 0xff, 0, 0x50, 0, 0});
            if (polling.length == 1 || (polling.length == 3
                    && polling[1] == (byte) 0x90 && polling[2] == 0)) {
                int flags = polling[0] & 0xff;
                System.out.println("Automatic polling: " + ((flags & 0x80) != 0));
                System.out.println("ISO 14443 Type A polling: " + ((flags & 1) != 0));
                System.out.println("ISO 14443 Type B polling: " + ((flags & 2) != 0));
            } else {
                System.out.println("Polling configuration: unrecognized response (not logged).");
            }
        } catch (CardException ex) {
            System.out.println("Reader firmware/polling query unavailable; "
                    + "the driver may block PC/SC escape commands. Settings were not changed.");
        } finally {
            if (reader != null) {
                try { reader.disconnect(false); }
                catch (CardException ex) { System.out.println("Direct reader connection cleanup failed."); }
            }
        }
    }
}
