package ui.helpers;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.event.MouseEvent;
import static org.junit.jupiter.api.Assertions.*;

class CustomerChargeAuthorizationDialogTest {
    private void stroke(CustomerChargeAuthorizationDialog.Pad pad,int end,int button) {
        pad.dispatchEvent(new MouseEvent(pad,MouseEvent.MOUSE_PRESSED,0,0,10,30,1,false,button));
        pad.dispatchEvent(new MouseEvent(pad,MouseEvent.MOUSE_DRAGGED,0,MouseEvent.BUTTON1_DOWN_MASK,end,30,0,false,MouseEvent.NOBUTTON));
        pad.dispatchEvent(new MouseEvent(pad,MouseEvent.MOUSE_RELEASED,0,0,end,30,1,false,button));
    }
    @Test void penStrokesExportAndClearRedoPreservesValidation() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var pad=new CustomerChargeAuthorizationDialog.Pad();pad.setSize(620,240);
            stroke(pad,25,MouseEvent.BUTTON1);assertFalse(pad.meaningful());
            pad.clear();pad.redo();assertFalse(pad.meaningful());
            stroke(pad,200,MouseEvent.BUTTON1);assertTrue(pad.meaningful());
            pad.clear();assertFalse(pad.meaningful());pad.redo();assertTrue(pad.meaningful());
            try {
                var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(pad.base64())));
                assertEquals(620,image.getWidth());assertEquals(240,image.getHeight());
                assertEquals(java.awt.Color.BLACK.getRGB(),image.getRGB(100,30));
            }catch(Exception ex){throw new AssertionError(ex);}
        });
    }
    @Test void disabledPadAndPenSideButtonCannotSign() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var pad=new CustomerChargeAuthorizationDialog.Pad();pad.setSize(620,240);
            pad.setEnabled(false);stroke(pad,200,MouseEvent.BUTTON1);assertFalse(pad.meaningful());
            pad.setEnabled(true);stroke(pad,200,MouseEvent.BUTTON3);assertFalse(pad.meaningful());
        });
    }
    @Test void largeScreenSignatureExportsWithinServerLimits() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var pad=new CustomerChargeAuthorizationDialog.Pad();pad.setSize(2400,1200);
            stroke(pad,1000,MouseEvent.BUTTON1);assertTrue(pad.meaningful());
            try {
                byte[] png=java.util.Base64.getDecoder().decode(pad.base64());
                var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
                assertEquals(1200,image.getWidth());assertEquals(600,image.getHeight());
                assertNotEquals(java.awt.Color.WHITE.getRGB(),image.getRGB(100,15));
                // Resizing and clearing must still recover the original signature with Redo.
                pad.setSize(1600,800);pad.clear();pad.redo();
                assertTrue(pad.meaningful());
                image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(pad.base64())));
                assertEquals(1200,image.getWidth());assertEquals(600,image.getHeight());
            }catch(Exception ex){throw new AssertionError(ex);}
        });
    }
}
