package services;

import org.junit.jupiter.api.Test;

import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.standard.MediaPrintableArea;
import javax.print.attribute.standard.OrientationRequested;
import javax.print.attribute.standard.Sides;

import static org.junit.jupiter.api.Assertions.*;

class BadgePrintServicePrinterAttributesTest {
    @Test
    void duplexBadgeUsesCr80PortraitAndTwoSidedPrinting() {
        PrintRequestAttributeSet attributes = BadgePrintService.createPrintAttributes(
                BadgePrintService.BadgePrintSide.BOTH, true);

        assertEquals(OrientationRequested.PORTRAIT, attributes.get(OrientationRequested.class));
        assertEquals(Sides.TWO_SIDED_LONG_EDGE, attributes.get(Sides.class));
        float[] area = ((MediaPrintableArea) attributes.get(MediaPrintableArea.class))
                .getPrintableArea(MediaPrintableArea.INCH);
        assertArrayEquals(new float[]{0f, 0f, 2.125f, 3.375f}, area, 0.001f);
    }

    @Test
    void oneSidedBadgeAlwaysUsesSimplex() {
        for (BadgePrintService.BadgePrintSide side : new BadgePrintService.BadgePrintSide[]{
                BadgePrintService.BadgePrintSide.FRONT, BadgePrintService.BadgePrintSide.BACK}) {
            PrintRequestAttributeSet attributes = BadgePrintService.createPrintAttributes(side, true);
            assertEquals(Sides.ONE_SIDED, attributes.get(Sides.class));
        }
    }
}
