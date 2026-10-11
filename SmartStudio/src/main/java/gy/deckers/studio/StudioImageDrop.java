package gy.deckers.studio;

import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.util.concurrent.Callable;
import java.util.function.*;
import javax.swing.*;

/** Own the native lifecycle explicitly: accept, detach data, complete, then touch Swing. */
final class StudioImageDrop {
    interface Drop {
        int actions();
        boolean supports(DataFlavor flavor);
        void accept();
        Transferable data();
        void complete(boolean success);
        void reject();
    }
    static void receive(Drop drop, boolean busy, Consumer<Callable<StudioImageFile.Loaded>> load,
                        Consumer<Throwable> error, Consumer<Runnable> later) {
        if (busy || (drop.actions() & DnDConstants.ACTION_COPY) == 0
                || !(drop.supports(DataFlavor.javaFileListFlavor) || drop.supports(DataFlavor.imageFlavor)
                || drop.supports(StudioImageTransfer.PNG))) {
            drop.reject(); return;
        }
        drop.accept();
        Callable<StudioImageFile.Loaded> captured;
        try { captured = StudioImageTransfer.capture(drop.data()); }
        catch (Exception ex) {
            java.util.logging.Logger.getLogger(StudioImageDrop.class.getName()).log(
                    java.util.logging.Level.WARNING, "Native image drop failed", ex);
            drop.complete(false);
            later.accept(() -> error.accept(new java.io.IOException(
                    "The source app could not provide the dropped image.\nIn WhatsApp Web, right-click the opened photo, choose Copy image, then click Paste image in SmartStudio.\nYou can also download the photo and use Choose image.", ex)));
            return;
        }
        drop.complete(true);
        later.accept(() -> load.accept(captured));
    }
    static void install(JComponent target, BooleanSupplier busy,
                        Consumer<Callable<StudioImageFile.Loaded>> load, Consumer<Throwable> error) {
        StudioImageTransfer.registerNativeImages();
        target.setDropTarget(new DropTarget(target, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            private void drag(DropTargetDragEvent event) {
                if (!busy.getAsBoolean() && (event.getSourceActions() & DnDConstants.ACTION_COPY) != 0
                        && (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || event.isDataFlavorSupported(DataFlavor.imageFlavor)
                        || event.isDataFlavorSupported(StudioImageTransfer.PNG))) event.acceptDrag(DnDConstants.ACTION_COPY);
                else event.rejectDrag();
            }
            @Override public void dragEnter(DropTargetDragEvent event) { drag(event); }
            @Override public void dragOver(DropTargetDragEvent event) { drag(event); }
            @Override public void dropActionChanged(DropTargetDragEvent event) { drag(event); }
            @Override public void drop(DropTargetDropEvent event) {
                receive(new Drop() {
                    public int actions() { return event.getSourceActions(); }
                    public boolean supports(DataFlavor flavor) { return event.isDataFlavorSupported(flavor); }
                    public void accept() { event.acceptDrop(DnDConstants.ACTION_COPY); }
                    public Transferable data() { return event.getTransferable(); }
                    public void complete(boolean success) { event.dropComplete(success); }
                    public void reject() { event.rejectDrop(); }
                }, busy.getAsBoolean(), load, error, SwingUtilities::invokeLater);
            }
        }, true));
    }
}
