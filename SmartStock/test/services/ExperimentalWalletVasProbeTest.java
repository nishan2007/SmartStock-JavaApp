package services;

import org.junit.jupiter.api.Test;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentalWalletVasProbeTest {
    private static byte[] hex(String value) { return HexFormat.of().parseHex(value); }

    @Test void selectsOnlyVasApplet() {
        assertArrayEquals(hex("00a404000a4f53452e5641532e303100"), ExperimentalWalletVasProbe.selectCommand());
        assertArrayEquals(hex("ff00000013d4400100a404000a4f53452e5641532e303100"),
                ExperimentalWalletVasProbe.rawCommand(ExperimentalWalletVasProbe.selectCommand()));
    }

    @Test void acceptsDocumentedSelectResponse() {
        var result = ExperimentalWalletVasProbe.parse(hex("6f1d50084170706c655061799f210201009f2404c05d48d09f23040000001e9000"));
        assertEquals(0x9000, result.statusWord());
        assertTrue(result.appleWalletSelected());
    }

    @Test void rejectsSubstringSpoofingAndMalformedResponses() {
        assertFalse(ExperimentalWalletVasProbe.parse(hex("53084170706c655061799000")).appleWalletSelected());
        assertFalse(ExperimentalWalletVasProbe.parse(hex("6a82")).appleWalletSelected());
        for (String value : new String[]{"", "90", "50094170706c655061799000", "50809000", "6f035008419000"}) {
            assertThrows(IllegalArgumentException.class, () -> ExperimentalWalletVasProbe.parse(hex(value)));
        }
    }

    @Test void rejectsReaderAndPn532Errors() {
        assertArrayEquals(hex("6a82"), ExperimentalWalletVasProbe.unwrapRaw(hex("d541006a829000")));
        for (String value : new String[]{"", "d541016a829000", "d541006a826300", "d54b006a829000"}) {
            assertThrows(IllegalArgumentException.class, () -> ExperimentalWalletVasProbe.unwrapRaw(hex(value)));
        }
    }
}
