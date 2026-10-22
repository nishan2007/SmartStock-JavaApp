package services;

import javax.smartcardio.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

/** Experimental discovery only. Never yields an employee credential or authenticates a user. */
public final class ExperimentalWalletVasProbe {
    private ExperimentalWalletVasProbe() { }

    static byte[] selectCommand() {
        return new CommandAPDU(0, 0xa4, 4, 0,
                "OSE.VAS.01".getBytes(StandardCharsets.US_ASCII), 256).getBytes();
    }

    static byte[] rawCommand(byte[] apdu) {
        if (apdu.length == 0 || apdu.length > 252) throw new IllegalArgumentException("Invalid APDU length.");
        byte[] command = new byte[8 + apdu.length];
        command[0] = (byte) 0xff;
        command[4] = (byte) (3 + apdu.length);
        command[5] = (byte) 0xd4;
        command[6] = 0x40; // PN532 InDataExchange, target 1 established by PC/SC polling.
        command[7] = 1;
        System.arraycopy(apdu, 0, command, 8, apdu.length);
        return command;
    }

    static byte[] unwrapRaw(byte[] response) {
        if (response.length < 7 || response[response.length - 2] != (byte) 0x90
                || response[response.length - 1] != 0 || response[0] != (byte) 0xd5
                || response[1] != 0x41 || response[2] != 0) {
            throw new IllegalArgumentException("Reader did not return a successful PN532 exchange.");
        }
        return Arrays.copyOfRange(response, 3, response.length - 2);
    }

    public record Discovery(int statusWord, boolean appleWalletSelected) { }

    static Discovery parse(byte[] response) {
        if (response.length < 2 || response.length > 4096) {
            throw new IllegalArgumentException("Invalid SELECT response length.");
        }
        ResponseAPDU apdu = new ResponseAPDU(response);
        if (apdu.getSW() != 0x9000) return new Discovery(apdu.getSW(), false);
        byte[] data = apdu.getData();
        boolean apple = hasAppleLabel(data, 0, data.length, 0);
        return new Discovery(apdu.getSW(), apple);
    }

    // Strict bounded BER-TLV parsing; a raw ApplePay substring is not evidence of discovery.
    private static boolean hasAppleLabel(byte[] data, int start, int end, int depth) {
        if (depth > 8) throw new IllegalArgumentException("SELECT response nesting is too deep.");
        boolean apple = false;
        for (int offset = start; offset < end;) {
            int first = data[offset++] & 0xff;
            if (first == 0 || first == 0xff) throw new IllegalArgumentException("Invalid BER tag.");
            int tag = first;
            if ((first & 0x1f) == 0x1f) {
                int count = 0;
                int next;
                do {
                    if (offset >= end || ++count > 2) throw new IllegalArgumentException("Invalid BER tag.");
                    next = data[offset++] & 0xff;
                    tag = (tag << 8) | next;
                } while ((next & 0x80) != 0);
            }
            if (offset >= end) throw new IllegalArgumentException("Missing BER length.");
            int length = data[offset++] & 0xff;
            if ((length & 0x80) != 0) {
                int count = length & 0x7f;
                if (count == 0 || count > 2 || offset + count > end) {
                    throw new IllegalArgumentException("Invalid BER length.");
                }
                length = 0;
                for (int i = 0; i < count; i++) length = (length << 8) | (data[offset++] & 0xff);
            }
            if (length > end - offset) throw new IllegalArgumentException("Truncated BER value.");
            if (tag == 0x50 && Arrays.equals(Arrays.copyOfRange(data, offset, offset + length),
                    "ApplePay".getBytes(StandardCharsets.US_ASCII))) apple = true;
            if ((first & 0x20) != 0) apple |= hasAppleLabel(data, offset, offset + length, depth + 1);
            offset += length;
        }
        return apple;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 2) throw new IllegalArgumentException("Usage: ExperimentalWalletVasProbe [1..120 seconds] [--raw]");
        int seconds = args.length == 0 ? 60 : Integer.parseInt(args[0]);
        boolean raw = args.length == 2 && "--raw".equals(args[1]);
        if (seconds < 1 || seconds > 120 || (args.length == 2 && !raw)) {
            throw new IllegalArgumentException("Usage: ExperimentalWalletVasProbe [1..120 seconds] [--raw]");
        }
        var readers = TerminalFactory.getDefault().terminals().list().stream()
                .filter(t -> t.getName().toUpperCase(java.util.Locale.ROOT).contains("ACR122")).toList();
        if (readers.size() != 1) throw new IllegalStateException("Connect exactly one ACR122 reader for this experiment.");
        CardTerminal terminal = readers.get(0);
        System.out.println("Reader: " + terminal.getName() + "; transport: " + (raw ? "ACS raw PN532" : "PC/SC ISO7816"));
        System.out.println("Present an unlocked iPhone. SELECT VAS only; no payment commands, pass reads or login.");
        long deadline = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
        boolean handled = false;
        int attempts = 0;
        while (System.nanoTime() < deadline) {
            if (!terminal.isCardPresent()) { handled = false; Thread.sleep(200); continue; }
            if (handled) { Thread.sleep(200); continue; }
            handled = true;
            attempts++;
            Card card = null;
            try {
                card = terminal.connect("*");
                byte[] response;
                if (raw) {
                    int escape = System.getProperty("os.name").startsWith("Windows")
                            ? (0x31 << 16) | (3500 << 2) : 0x42000000 + 3500;
                    response = unwrapRaw(card.transmitControlCommand(escape, rawCommand(selectCommand())));
                } else {
                    response = card.getBasicChannel().transmit(new CommandAPDU(selectCommand())).getBytes();
                }
                Discovery result = parse(response);
                System.out.printf("VAS SELECT status: %04X; Apple Wallet label: %s%n",
                        result.statusWord(), result.appleWalletSelected());
            } catch (CardException | IllegalArgumentException ex) {
                // Do not dump phone response bytes or card exception messages into logs.
                System.out.println("SELECT exchange failed or response was malformed; no credential accepted.");
            } finally {
                if (card != null) card.disconnect(false);
            }
        }
        System.out.println("Probe complete; attempts: " + attempts + ". Discovery is not badge authentication.");
    }
}
