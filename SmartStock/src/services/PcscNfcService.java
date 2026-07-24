package services;

import javax.smartcardio.Card;
import javax.smartcardio.CardChannel;
import javax.smartcardio.CardException;
import javax.smartcardio.CardTerminal;
import javax.smartcardio.CommandAPDU;
import javax.smartcardio.ResponseAPDU;
import javax.smartcardio.TerminalFactory;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/** Direct PC/SC support for ACR122U-compatible readers and Type 2 NFC tags. */
public final class PcscNfcService {
    private static final Duration CARD_TIMEOUT = Duration.ofSeconds(20);
    private static final byte[] MIME_TYPE = "application/vnd.smartstock.badge".getBytes(StandardCharsets.US_ASCII);

    private PcscNfcService() {
    }

    public static boolean hasReader() {
        try {
            return !matchingTerminals().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static String writeAndVerify(String payload) throws Exception {
        byte[] expected = payload.getBytes(StandardCharsets.UTF_8);
        return withCard(channel -> {
            byte[] uid = getUid(channel);
            byte[] ndef = encodeMimeNdef(expected);
            byte[] tlv = wrapNdefTlv(ndef);
            ensureType2Tag(channel);
            writePages(channel, tlv);
            byte[] actual = readSmartStockPayload(channel);
            if (!Arrays.equals(expected, actual)) {
                throw new IllegalStateException("The tag was written, but its read-back value did not match.");
            }
            return "NFC badge written and read back successfully.\nReader: ACR122U / PC/SC\nCard UID: "
                    + HexFormat.of().withUpperCase().formatHex(uid);
        });
    }

    public static TestResult test(String expectedPayload) throws Exception {
        ReadResult read = read(CARD_TIMEOUT);
        return new TestResult(read.payload(), read.payload().equals(expectedPayload), read.cardUid());
    }

    public static ReadResult read(Duration timeout) throws Exception {
        return withCard(timeout, channel -> {
            byte[] uid = getUid(channel);
            ensureType2Tag(channel);
            String actual = new String(readSmartStockPayload(channel), StandardCharsets.UTF_8);
            return new ReadResult(actual, HexFormat.of().withUpperCase().formatHex(uid));
        });
    }

    private static <T> T withCard(CardAction<T> action) throws Exception {
        return withCard(CARD_TIMEOUT, action);
    }

    private static <T> T withCard(Duration timeout, CardAction<T> action) throws Exception {
        List<CardTerminal> terminals = matchingTerminals();
        if (terminals.isEmpty()) {
            throw new IllegalStateException("No PC/SC NFC reader is available. The ACR122U may be connected at USB level, "
                    + "but macOS must also list it as a Smart Card reader.");
        }
        CardTerminal terminal = terminals.get(0);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!terminal.isCardPresent() && System.nanoTime() < deadline) {
            terminal.waitForCardPresent(500);
        }
        if (!terminal.isCardPresent()) {
            throw new NoCardPresentException("No NFC card was detected. Place one card flat on the reader and try again.");
        }
        Card card = null;
        try {
            card = terminal.connect("*");
            return action.run(card.getBasicChannel());
        } finally {
            if (card != null) {
                try { card.disconnect(false); } catch (CardException ignored) { }
            }
        }
    }

    private static List<CardTerminal> matchingTerminals() throws CardException {
        List<CardTerminal> all = TerminalFactory.getDefault().terminals().list();
        List<CardTerminal> acr = all.stream()
                .filter(t -> t.getName().toUpperCase().contains("ACR122"))
                .toList();
        return acr.isEmpty() ? all : acr;
    }

    private static byte[] getUid(CardChannel channel) throws CardException {
        return transmit(channel, new byte[]{(byte) 0xff, (byte) 0xca, 0x00, 0x00, 0x00}, "read card UID");
    }

    private static void ensureType2Tag(CardChannel channel) throws CardException {
        byte[] header = readPages(channel, 0);
        if (header.length < 16 || header[12] != (byte) 0xe1) {
            throw new IllegalStateException("This card is not an NFC Forum Type 2 tag (NTAG/MIFARE Ultralight). "
                    + "SmartStock did not write it. Use an NTAG213/215/216 or MIFARE Ultralight card.");
        }
    }

    private static void writePages(CardChannel channel, byte[] data) throws CardException {
        int pages = (data.length + 3) / 4;
        int capacityPages = (readPages(channel, 0)[14] & 0xff) * 2;
        if (pages > capacityPages) {
            throw new IllegalStateException("The NFC payload is too large for this tag.");
        }
        for (int i = 0; i < pages; i++) {
            byte[] page = new byte[4];
            int offset = i * 4;
            System.arraycopy(data, offset, page, 0, Math.min(4, data.length - offset));
            byte[] command = new byte[]{(byte) 0xff, (byte) 0xd6, 0x00, (byte) (4 + i), 0x04,
                    page[0], page[1], page[2], page[3]};
            transmit(channel, command, "write NFC page " + (4 + i));
        }
    }

    private static byte[] readSmartStockPayload(CardChannel channel) throws CardException {
        ByteArrayOutputStream memory = new ByteArrayOutputStream();
        int capacityBytes = (readPages(channel, 0)[14] & 0xff) * 8;
        for (int page = 4; memory.size() < capacityBytes; page += 4) {
            memory.writeBytes(readPages(channel, page));
        }
        return decodeMimeNdef(memory.toByteArray());
    }

    private static byte[] readPages(CardChannel channel, int firstPage) throws CardException {
        return transmit(channel, new byte[]{(byte) 0xff, (byte) 0xb0, 0x00, (byte) firstPage, 0x10},
                "read NFC page " + firstPage);
    }

    private static byte[] transmit(CardChannel channel, byte[] command, String operation) throws CardException {
        ResponseAPDU response = channel.transmit(new CommandAPDU(command));
        if (response.getSW() != 0x9000) {
            throw new IllegalStateException("Could not " + operation + " (status "
                    + Integer.toHexString(response.getSW()).toUpperCase() + ").");
        }
        return response.getData();
    }

    static byte[] encodeMimeNdef(byte[] payload) {
        if (payload.length > 255) throw new IllegalArgumentException("NFC payload must be 255 bytes or less.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xd2); // MB, ME, SR, MIME media record
        out.write(MIME_TYPE.length);
        out.write(payload.length);
        out.writeBytes(MIME_TYPE);
        out.writeBytes(payload);
        return out.toByteArray();
    }

    static byte[] wrapNdefTlv(byte[] ndef) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x03);
        if (ndef.length < 255) {
            out.write(ndef.length);
        } else {
            out.write(0xff); out.write(ndef.length >>> 8); out.write(ndef.length);
        }
        out.writeBytes(ndef);
        out.write(0xfe);
        return out.toByteArray();
    }

    static byte[] decodeMimeNdef(byte[] memory) {
        int offset = 0;
        while (offset < memory.length && (memory[offset] & 0xff) != 0x03) {
            if ((memory[offset] & 0xff) == 0xfe) break;
            offset++;
        }
        if (offset >= memory.length || (memory[offset++] & 0xff) != 0x03) {
            throw new IllegalStateException("No NDEF message was found on this NFC card.");
        }
        int length = memory[offset++] & 0xff;
        if (length == 0xff) length = ((memory[offset++] & 0xff) << 8) | (memory[offset++] & 0xff);
        int end = offset + length;
        if (end > memory.length || length < 3) throw new IllegalStateException("The NFC card contains an invalid NDEF message.");
        int flags = memory[offset++] & 0xff;
        int typeLength = memory[offset++] & 0xff;
        boolean shortRecord = (flags & 0x10) != 0;
        int payloadLength;
        if (shortRecord) payloadLength = memory[offset++] & 0xff;
        else payloadLength = ((memory[offset++] & 0xff) << 24) | ((memory[offset++] & 0xff) << 16)
                | ((memory[offset++] & 0xff) << 8) | (memory[offset++] & 0xff);
        if ((flags & 0x08) != 0) offset += 1 + (memory[offset] & 0xff);
        byte[] type = Arrays.copyOfRange(memory, offset, offset + typeLength);
        offset += typeLength;
        if (!Arrays.equals(type, MIME_TYPE) || offset + payloadLength > end) {
            throw new IllegalStateException("This NFC card does not contain a SmartStock employee badge record.");
        }
        return Arrays.copyOfRange(memory, offset, offset + payloadLength);
    }

    public record TestResult(String payload, boolean matchesExpectedEmployee, String cardUid) { }
    public record ReadResult(String payload, String cardUid) { }

    public static final class NoCardPresentException extends IllegalStateException {
        public NoCardPresentException(String message) { super(message); }
    }

    @FunctionalInterface
    private interface CardAction<T> { T run(CardChannel channel) throws Exception; }
}
