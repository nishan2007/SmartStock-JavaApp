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
import java.util.zip.CRC32;

/** Direct PC/SC support for ACR122U-compatible readers and Type 2 NFC tags. */
public final class PcscNfcService {
    private static final Duration CARD_TIMEOUT = Duration.ofSeconds(20);
    private static final byte[] MIME_TYPE = "application/vnd.smartstock.badge".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CLASSIC_MAGIC = "SSC1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] CLASSIC_FACTORY_KEY = {
            (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff
    };
    private static final int CLASSIC_FIRST_DATA_BLOCK = 4;
    private static final int CLASSIC_DATA_BLOCK_COUNT = 3;
    private static final int CLASSIC_BLOCK_SIZE = 16;
    private static final int CLASSIC_RECORD_SIZE = CLASSIC_DATA_BLOCK_COUNT * CLASSIC_BLOCK_SIZE;
    private static final int CLASSIC_CRC_OFFSET = CLASSIC_RECORD_SIZE - Integer.BYTES;
    private static final int CLASSIC_MAX_PAYLOAD_BYTES = CLASSIC_CRC_OFFSET - CLASSIC_MAGIC.length - 1;

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
            CardFamily family = cardFamily(channel.getCard().getATR().getBytes());
            byte[] actual;
            if (family == CardFamily.MIFARE_CLASSIC) {
                writeClassicRecord(channel, expected);
                actual = readClassicRecord(channel);
            } else {
                byte[] ndef = encodeMimeNdef(expected);
                byte[] tlv = wrapNdefTlv(ndef);
                ensureType2Tag(channel);
                writePages(channel, tlv);
                actual = readSmartStockPayload(channel);
            }
            if (!Arrays.equals(expected, actual)) {
                throw new IllegalStateException("The tag was written, but its read-back value did not match.");
            }
            return "NFC badge written and read back successfully.\nReader: ACR122U / PC/SC\nCard type: "
                    + family.label + "\nCard UID: "
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
            CardFamily family = cardFamily(channel.getCard().getATR().getBytes());
            byte[] payload;
            if (family == CardFamily.MIFARE_CLASSIC) {
                payload = readClassicRecord(channel);
            } else {
                ensureType2Tag(channel);
                payload = readSmartStockPayload(channel);
            }
            String actual = new String(payload, StandardCharsets.UTF_8);
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

    static CardFamily cardFamily(byte[] atr) {
        byte[] pcscStorageCardPrefix = {
                (byte) 0xa0, 0x00, 0x00, 0x03, 0x06, 0x03, 0x00
        };
        for (int i = 0; i <= atr.length - pcscStorageCardPrefix.length - 1; i++) {
            boolean matches = true;
            for (int j = 0; j < pcscStorageCardPrefix.length; j++) {
                if (atr[i + j] != pcscStorageCardPrefix[j]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                int cardCode = atr[i + pcscStorageCardPrefix.length] & 0xff;
                if (cardCode == 0x01 || cardCode == 0x02) return CardFamily.MIFARE_CLASSIC;
                if (cardCode == 0x03) return CardFamily.TYPE_2;
            }
        }
        return CardFamily.UNKNOWN;
    }

    private static void writeClassicRecord(CardChannel channel, byte[] payload) throws CardException {
        byte[] record = encodeClassicRecord(payload);
        authenticateClassicSector(channel);
        for (int i = 0; i < CLASSIC_DATA_BLOCK_COUNT; i++) {
            int block = CLASSIC_FIRST_DATA_BLOCK + i;
            byte[] data = Arrays.copyOfRange(record, i * CLASSIC_BLOCK_SIZE, (i + 1) * CLASSIC_BLOCK_SIZE);
            byte[] command = new byte[5 + CLASSIC_BLOCK_SIZE];
            command[0] = (byte) 0xff;
            command[1] = (byte) 0xd6;
            command[3] = (byte) block;
            command[4] = CLASSIC_BLOCK_SIZE;
            System.arraycopy(data, 0, command, 5, data.length);
            transmit(channel, command, "write MIFARE Classic block " + block);
        }
    }

    private static byte[] readClassicRecord(CardChannel channel) throws CardException {
        authenticateClassicSector(channel);
        ByteArrayOutputStream record = new ByteArrayOutputStream(CLASSIC_RECORD_SIZE);
        for (int i = 0; i < CLASSIC_DATA_BLOCK_COUNT; i++) {
            int block = CLASSIC_FIRST_DATA_BLOCK + i;
            record.writeBytes(transmit(channel,
                    new byte[]{(byte) 0xff, (byte) 0xb0, 0x00, (byte) block, CLASSIC_BLOCK_SIZE},
                    "read MIFARE Classic block " + block));
        }
        return decodeClassicRecord(record.toByteArray());
    }

    private static void authenticateClassicSector(CardChannel channel) throws CardException {
        byte[] loadKey = new byte[5 + CLASSIC_FACTORY_KEY.length];
        loadKey[0] = (byte) 0xff;
        loadKey[1] = (byte) 0x82;
        loadKey[4] = (byte) CLASSIC_FACTORY_KEY.length;
        System.arraycopy(CLASSIC_FACTORY_KEY, 0, loadKey, 5, CLASSIC_FACTORY_KEY.length);
        transmit(channel, loadKey, "load the MIFARE Classic authentication key");
        try {
            transmit(channel, new byte[]{(byte) 0xff, (byte) 0x86, 0x00, 0x00, 0x05,
                    0x01, 0x00, (byte) CLASSIC_FIRST_DATA_BLOCK, 0x60, 0x00},
                    "authenticate MIFARE Classic sector 1");
        } catch (IllegalStateException ex) {
            throw new IllegalStateException("MIFARE Classic sector 1 could not be authenticated with its factory key. "
                    + "This card may already be personalized or locked; SmartStock did not write it.", ex);
        }
    }

    static byte[] encodeClassicRecord(byte[] payload) {
        if (payload.length > CLASSIC_MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("MIFARE Classic badge payload must be "
                    + CLASSIC_MAX_PAYLOAD_BYTES + " bytes or less.");
        }
        byte[] record = new byte[CLASSIC_RECORD_SIZE];
        System.arraycopy(CLASSIC_MAGIC, 0, record, 0, CLASSIC_MAGIC.length);
        record[CLASSIC_MAGIC.length] = (byte) payload.length;
        System.arraycopy(payload, 0, record, CLASSIC_MAGIC.length + 1, payload.length);
        int checksum = checksum(record, 0, CLASSIC_CRC_OFFSET);
        writeInt(record, CLASSIC_CRC_OFFSET, checksum);
        return record;
    }

    static byte[] decodeClassicRecord(byte[] record) {
        if (record.length != CLASSIC_RECORD_SIZE
                || !Arrays.equals(CLASSIC_MAGIC, Arrays.copyOf(record, CLASSIC_MAGIC.length))) {
            throw new IllegalStateException("This MIFARE Classic card does not contain a SmartStock employee badge record.");
        }
        int length = record[CLASSIC_MAGIC.length] & 0xff;
        if (length > CLASSIC_MAX_PAYLOAD_BYTES) {
            throw new IllegalStateException("The MIFARE Classic SmartStock badge record has an invalid length.");
        }
        int expectedChecksum = readInt(record, CLASSIC_CRC_OFFSET);
        int actualChecksum = checksum(record, 0, CLASSIC_CRC_OFFSET);
        if (expectedChecksum != actualChecksum) {
            throw new IllegalStateException("The MIFARE Classic SmartStock badge record failed its integrity check.");
        }
        return Arrays.copyOfRange(record, CLASSIC_MAGIC.length + 1, CLASSIC_MAGIC.length + 1 + length);
    }

    private static int checksum(byte[] data, int offset, int length) {
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        return (int) crc.getValue();
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static int readInt(byte[] source, int offset) {
        return ((source[offset] & 0xff) << 24)
                | ((source[offset + 1] & 0xff) << 16)
                | ((source[offset + 2] & 0xff) << 8)
                | (source[offset + 3] & 0xff);
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

    enum CardFamily {
        TYPE_2("NTAG / MIFARE Ultralight"),
        MIFARE_CLASSIC("MIFARE Classic"),
        UNKNOWN("NFC Type 2 compatible");

        private final String label;

        CardFamily(String label) {
            this.label = label;
        }
    }
}
