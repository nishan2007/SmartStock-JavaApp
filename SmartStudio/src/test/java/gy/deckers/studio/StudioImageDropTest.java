package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import java.awt.datatransfer.*;
import java.awt.dnd.DnDConstants;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.Callable;
import static org.junit.jupiter.api.Assertions.*;

class StudioImageDropTest {
    private static final class NativeDrop implements StudioImageDrop.Drop {
        final List<String> events = new ArrayList<>();
        final BufferedImage image = new BufferedImage(40, 48, BufferedImage.TYPE_INT_ARGB);
        boolean accepted, completed, broken;
        int actions = DnDConstants.ACTION_COPY;
        public int actions() { return actions; }
        public boolean supports(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
        public void accept() { assertFalse(completed); accepted = true; events.add("accept"); }
        public Transferable data() {
            assertTrue(accepted); assertFalse(completed); events.add("data");
            if (broken) throw new java.awt.dnd.InvalidDnDOperationException("Native transfer unavailable");
            return new Transferable() {
                public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[]{DataFlavor.imageFlavor}; }
                public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
                public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                    assertTrue(accepted); assertFalse(completed);
                    if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                    return image;
                }
            };
        }
        public void complete(boolean success) { assertTrue(accepted); assertFalse(completed); completed = true; events.add("complete:" + success); }
        public void reject() { assertFalse(accepted); events.add("reject"); }
    }
    @Test void detachesImageBeforeCompletingAndQueuesLoadingAfterCompletion() throws Exception {
        var drop = new NativeDrop(); drop.image.setRGB(5, 5, 0x8044aaee);
        var queued = new ArrayList<Runnable>(); var loads = new ArrayList<Callable<StudioImageFile.Loaded>>();
        StudioImageDrop.receive(drop, false, loader -> {
            assertTrue(drop.completed); loads.add(loader);
        }, error -> fail(error), queued::add);
        assertEquals(List.of("accept", "data", "complete:true"), drop.events);
        assertTrue(loads.isEmpty()); assertEquals(1, queued.size());
        drop.image.setRGB(5, 5, 0); queued.get(0).run();
        assertEquals(0x8044aaee, loads.get(0).call().image().getRGB(5, 5));
    }
    @Test void completesFailedTransferBeforeDisplayingErrorAndDoesNotStartWorker() {
        var drop = new NativeDrop(); drop.broken = true;
        var queued = new ArrayList<Runnable>(); var errors = new ArrayList<Throwable>();
        StudioImageDrop.receive(drop, false, loader -> fail("No worker on failure"), errors::add, queued::add);
        assertEquals(List.of("accept", "data", "complete:false"), drop.events); assertTrue(errors.isEmpty());
        queued.get(0).run(); assertEquals(1, errors.size());
        assertInstanceOf(java.awt.dnd.InvalidDnDOperationException.class, errors.get(0).getCause());
    }
    @Test void busyAndMoveOnlyDropsAreRejectedWithoutReadingNativeData() {
        var busy = new NativeDrop();
        StudioImageDrop.receive(busy, true, loader -> fail(), error -> fail(), task -> fail());
        assertEquals(List.of("reject"), busy.events);
        var move = new NativeDrop(); move.actions = DnDConstants.ACTION_MOVE;
        StudioImageDrop.receive(move, false, loader -> fail(), error -> fail(), task -> fail());
        assertEquals(List.of("reject"), move.events);
    }
}
