package services;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
}
