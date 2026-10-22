package ui.screens.customorders;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.datatransfer.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderFileDropHandlerTest {
    @TempDir Path directory;

    @Test void browserClipboardImageBecomesReadablePngAttachment() throws Exception {
        var image = new java.awt.image.BufferedImage(3, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1, 1, 0xff123456);
        Transferable clipboard = new Transferable() {
            public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
            public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                return image;
            }
        };
        var model = new DefaultListModel<Path>();
        var handler = new CustomOrderFileDropHandler(model, text -> fail(text));
        assertTrue(handler.importData(new TransferHandler.TransferSupport(new JList<>(), clipboard)));
        assertEquals(1, model.size());
        Path path = model.get(0);
        try {
            assertTrue(path.toString().endsWith(".png"));
            var restored = javax.imageio.ImageIO.read(path.toFile());
            assertEquals(3, restored.getWidth());
            assertEquals(0xff123456, restored.getRGB(1, 1));
        } finally { Files.deleteIfExists(path); }
    }

    @Test void explorerDropAddsFilesOnceAndRejectsFoldersAtomically() throws Exception {
        Path image=Files.writeString(directory.resolve("image.png"),"image");
        var model=new DefaultListModel<Path>();
        var errors=new ArrayList<String>();
        var handler=new CustomOrderFileDropHandler(model, errors::add);
        assertTrue(handler.importData(drop(List.of(image),new JLabel())));
        assertTrue(handler.importData(drop(List.of(image),new JLabel())));
        assertEquals(1,model.size());
        Path second=Files.writeString(directory.resolve("second.png"),"image");
        assertFalse(handler.importData(drop(List.of(second,directory),new JLabel())));
        assertEquals(1,model.size());
        assertEquals(1,errors.size());
    }

    @Test void savedOrderDropsUseUploadCallbackAndEveryChildAcceptsFiles() throws Exception {
        Path image=Files.writeString(directory.resolve("image.png"),"image");
        var uploads=new ArrayList<List<Path>>();
        var handler=new CustomOrderFileDropHandler(uploads::add, text -> fail(text));
        JPanel panel=new JPanel();
        JLabel hint=new JLabel("Drop here");
        JButton add=new JButton("Add files");
        JList<Path> list=new JList<>();
        JScrollPane scroll=new JScrollPane(list);
        panel.add(hint);panel.add(scroll);panel.add(add);
        CustomOrderFileDropHandler.install(panel,handler);
        for(JComponent target:List.of(panel,hint,add,scroll,scroll.getViewport(),list)) {
            assertSame(handler,target.getTransferHandler());
            assertTrue(handler.importData(drop(List.of(image),target)));
        }
        assertEquals(6,uploads.size());
        assertEquals(List.of(image.toAbsolutePath().normalize()),uploads.get(0));
    }

    @Test void tooManyFilesNeverReachUploadCallback() throws Exception {
        var uploads=new ArrayList<List<Path>>();
        var errors=new ArrayList<String>();
        var handler=new CustomOrderFileDropHandler(uploads::add,errors::add);
        var paths=new ArrayList<Path>();
        for(int i=0;i<16;i++)paths.add(Files.writeString(directory.resolve("file"+i),"data"));
        assertFalse(handler.importData(drop(paths,new JLabel())));
        assertTrue(uploads.isEmpty());
        assertEquals(1,errors.size());
    }

    @Test void droppingIntoPickerSelectsFilesAndApprovesOnce() throws Exception {
        Path image=Files.writeString(directory.resolve("image.png"), "image");
        Path second=Files.writeString(directory.resolve("second.png"), "image");
        SwingUtilities.invokeAndWait(() -> {
            JFileChooser picker=new JFileChooser();picker.setMultiSelectionEnabled(true);
            var approvals=new ArrayList<String>();
            picker.addActionListener(event -> approvals.add(event.getActionCommand()));
            CustomOrderFileDropHandler.installPicker(picker,text -> fail(text));
            assertTrue(picker.getTransferHandler().importData(drop(List.of(image,second),picker)));
            assertArrayEquals(new java.io.File[]{image.toFile(),second.toFile()},picker.getSelectedFiles());
            assertEquals(List.of(JFileChooser.APPROVE_SELECTION),approvals);
        });
    }

    @Test void nativeDropCompletesBeforeAttachmentCallbacksRun() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeFalse(java.awt.GraphicsEnvironment.isHeadless());
        Path image=Files.writeString(directory.resolve("native.png"), "image");
        var sequence=new ArrayList<String>();
        var added=new ArrayList<List<Path>>();
        SwingUtilities.invokeAndWait(() -> {
            JPanel target=new JPanel();
            var handler=new CustomOrderFileDropHandler(paths -> {
                sequence.add("add"); added.add(paths);
            }, text -> fail(text));
            CustomOrderFileDropHandler.install(target,handler);
            assertTrue(target.getDropTarget().isActive());
            Transferable transfer=drop(List.of(image),target).getTransferable();
            var event=new java.awt.dnd.DropTargetDropEvent(target.getDropTarget().getDropTargetContext(),
                    new java.awt.Point(5,5),java.awt.dnd.DnDConstants.ACTION_COPY,
                    java.awt.dnd.DnDConstants.ACTION_COPY) {
                @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return transfer.isDataFlavorSupported(flavor);
                }
                @Override public Transferable getTransferable() { return transfer; }
                @Override public void acceptDrop(int action) { sequence.add("accept"); }
                @Override public void dropComplete(boolean success) {
                    assertTrue(success); sequence.add("complete");
                }
            };
            CustomOrderFileDropHandler.nativeListener(handler).drop(event);
            assertEquals(List.of("accept","complete"),sequence);
            assertTrue(added.isEmpty(),"Attachment callbacks must wait until native drop completes");
        });
        SwingUtilities.invokeAndWait(() -> {
            assertEquals(List.of("accept","complete","add"),sequence);
            assertEquals(List.of(image.toAbsolutePath().normalize()),added.get(0));
        });
    }

    private TransferHandler.TransferSupport drop(List<Path> paths,JComponent target) {
        Transferable transfer=new Transferable() {
            public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{DataFlavor.javaFileListFlavor};}
            public boolean isDataFlavorSupported(DataFlavor flavor){return DataFlavor.javaFileListFlavor.equals(flavor);}
            public Object getTransferData(DataFlavor flavor)throws UnsupportedFlavorException {
                if(!isDataFlavorSupported(flavor))throw new UnsupportedFlavorException(flavor);
                return paths.stream().map(Path::toFile).toList();
            }
        };
        return new TransferHandler.TransferSupport(target,transfer);
    }
}
