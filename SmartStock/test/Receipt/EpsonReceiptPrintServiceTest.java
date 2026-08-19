package Receipt;

import managers.HardwareSettingsManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpsonReceiptPrintServiceTest {
    @Test
    void ordersReceiptDrawerFeedAndCutForCashSale() {
        byte[] receipt = {0x1B, 0x40, 82};
        HardwareSettingsManager.EpsonSettings settings =
                new HardwareSettingsManager.EpsonSettings(true, true, true, 0, 120, 240, true);
        byte[] job = EpsonReceiptPrintService.composeJob(receipt, settings, true);
        assertArrayEquals(receipt, java.util.Arrays.copyOf(job, receipt.length));
        assertTrue(indexOf(job, new byte[]{0x1B, 0x70, 0x00, 60, 120}) >= receipt.length);
        assertTrue(indexOf(job, new byte[]{0x1B, 0x64, 0x03, 0x1D, 0x56, 0x42, 0x00})
                > indexOf(job, new byte[]{0x1B, 0x70}));
    }

    @Test
    void suppressesDrawerForReprintOrNonCashRequest() {
        HardwareSettingsManager.EpsonSettings settings =
                new HardwareSettingsManager.EpsonSettings(true, true, true, 1, 120, 240, true);
        byte[] job = EpsonReceiptPrintService.composeJob(new byte[]{82}, settings, false);
        assertFalse(indexOf(job, new byte[]{0x1B, 0x70}) >= 0);
        assertTrue(indexOf(job, new byte[]{0x1D, 0x56}) >= 0);
    }

    @Test
    void disabledEpsonSettingsPreserveLegacyAutomaticCut() {
        byte[] job = EpsonReceiptPrintService.composeJob(new byte[]{82},
                HardwareSettingsManager.EpsonSettings.defaults(), false);
        assertTrue(indexOf(job, new byte[]{0x1D, 0x56}) >= 0);
        assertFalse(indexOf(job, new byte[]{0x1B, 0x70}) >= 0);
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        outer:
        for (int i = 0; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
