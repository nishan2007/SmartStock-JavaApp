package services;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PcscNfcServiceTest {
    @Test
    void smartStockMimeRecordRoundTripsThroughType2Tlv() {
        byte[] payload = "SSB1-EMPLOYEE-000123".getBytes(StandardCharsets.UTF_8);
        byte[] tlv = PcscNfcService.wrapNdefTlv(PcscNfcService.encodeMimeNdef(payload));

        assertArrayEquals(payload, PcscNfcService.decodeMimeNdef(tlv));
    }

    @Test
    void rejectsCardsWithoutSmartStockNdefRecord() {
        assertThrows(IllegalStateException.class,
                () -> PcscNfcService.decodeMimeNdef(new byte[]{0x00, 0x00, (byte) 0xfe}));
    }

    @Test
    void smartStockRecordRoundTripsThroughMifareClassicDataBlocks() {
        byte[] payload = "SSB1-EMPLOYEE-000123".getBytes(StandardCharsets.UTF_8);

        byte[] record = PcscNfcService.encodeClassicRecord(payload);

        assertEquals(48, record.length);
        assertArrayEquals(payload, PcscNfcService.decodeClassicRecord(record));
    }

    @Test
    void rejectsCorruptedMifareClassicRecord() {
        byte[] record = PcscNfcService.encodeClassicRecord("SSB1-EMPLOYEE-000123".getBytes(StandardCharsets.UTF_8));
        record[8] ^= 0x01;

        assertThrows(IllegalStateException.class, () -> PcscNfcService.decodeClassicRecord(record));
    }

    @Test
    void detectsMifareClassicAndUltralightFromPcscStorageCardAtr() {
        assertEquals(PcscNfcService.CardFamily.MIFARE_CLASSIC, PcscNfcService.cardFamily(hex(
                "3B8F8001804F0CA000000306030001000000006A")));
        assertEquals(PcscNfcService.CardFamily.TYPE_2, PcscNfcService.cardFamily(hex(
                "3B8F8001804F0CA0000003060300030000000068")));
    }

    private static byte[] hex(String value) {
        return java.util.HexFormat.of().parseHex(value);
    }
}
