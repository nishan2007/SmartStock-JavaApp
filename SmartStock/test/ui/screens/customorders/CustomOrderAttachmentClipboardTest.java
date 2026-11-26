package ui.screens.customorders;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomOrderAttachmentClipboardTest {
    @Test void clipboardContainsActualFilesAndKeepsItsOwnSelectionSnapshot()throws Exception {
        var original=new ArrayList<>(List.of(new File("logo.pdf"),new File("design.png")));
        Transferable copy=CustomOrderAttachmentClipboard.files(original);
        original.clear();
        assertTrue(copy.isDataFlavorSupported(DataFlavor.javaFileListFlavor));
        assertEquals(List.of(new File("logo.pdf"),new File("design.png")),copy.getTransferData(DataFlavor.javaFileListFlavor));
        assertFalse(copy.isDataFlavorSupported(DataFlavor.stringFlavor));
        assertThrows(UnsupportedFlavorException.class,()->copy.getTransferData(DataFlavor.stringFlavor));
    }
    @Test void preservesNormalNamesAndPreventsUnsafePathsAndWindowsDeviceNames(){
        assertEquals("chest-logo.pdf",CustomOrderAttachmentClipboard.safeFilename("chest-logo.pdf"));
        assertEquals(".._.._design.png",CustomOrderAttachmentClipboard.safeFilename("../../design.png"));
        assertEquals("_CON.pdf",CustomOrderAttachmentClipboard.safeFilename("CON.pdf"));
        assertEquals("attachment",CustomOrderAttachmentClipboard.safeFilename(".."));
        assertEquals("art_work_.pdf",CustomOrderAttachmentClipboard.safeFilename("art:work?.pdf"));
    }
}
