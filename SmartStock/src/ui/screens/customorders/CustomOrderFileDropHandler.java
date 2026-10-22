package ui.screens.customorders;

import javax.swing.DefaultListModel;
import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Adds Explorer file drops to the pending attachment list without uploading. */
final class CustomOrderFileDropHandler extends TransferHandler {
    private final DefaultListModel<Path> files;
    private final Consumer<String> error;
    private final Consumer<List<Path>> onFiles;
    private Consumer<String> status = text -> { };

    void setStatus(Consumer<String> status) { this.status = status; }

    void paste(javax.swing.JComponent target) {
        try {
            var contents = java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (contents == null || !canImport(new TransferSupport(target, contents))) {
                error.accept("Copy a photo using Copy image in WhatsApp Web, or copy files, then paste here.");
                return;
            }
            importData(new TransferSupport(target, contents));
        } catch (IllegalStateException ex) {
            error.accept("The clipboard is busy. Try Paste again.");
        }
    }

    static void installPaste(javax.swing.JComponent target, CustomOrderFileDropHandler handler) {
        int mask = java.awt.GraphicsEnvironment.isHeadless() ? java.awt.event.InputEvent.CTRL_DOWN_MASK
                : java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        var key = javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_V, mask);
        target.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(key, "paste-order-files");
        target.getInputMap(javax.swing.JComponent.WHEN_FOCUSED).put(key, "paste-order-files");
        target.getActionMap().put("paste-order-files", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { handler.paste(target); }
        });
    }

    CustomOrderFileDropHandler(DefaultListModel<Path> files, Consumer<String> error) {
        this.files = files;
        this.error = error;
        this.onFiles = null;
    }

    CustomOrderFileDropHandler(Consumer<List<Path>> onFiles, Consumer<String> error) {
        this.files = new DefaultListModel<>();
        this.error = error;
        this.onFiles = onFiles;
    }

    /** Child controls have their own drop targets; install on every visible surface. */
    static void install(java.awt.Component component, CustomOrderFileDropHandler handler) {
        if (component instanceof javax.swing.JComponent swing) {
            swing.setTransferHandler(handler);
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                swing.setDropTarget(new java.awt.dnd.DropTarget(swing,
                        java.awt.dnd.DnDConstants.ACTION_COPY, nativeListener(handler), true));
            }
        }
        if (component instanceof java.awt.Container container)
            for (java.awt.Component child : container.getComponents()) install(child, handler);
    }

    static java.awt.dnd.DropTargetListener nativeListener(CustomOrderFileDropHandler handler) {
        return new java.awt.dnd.DropTargetAdapter() {
            private void accept(java.awt.dnd.DropTargetDragEvent event) {
                if (event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        && (event.getSourceActions() & java.awt.dnd.DnDConstants.ACTION_COPY) != 0) {
                    event.acceptDrag(java.awt.dnd.DnDConstants.ACTION_COPY);
                    handler.status.accept("Release to add the files.");
                } else {
                    event.rejectDrag();
                    handler.status.accept("This drop is not a file. Choose a file from File Explorer.");
                }
            }
            @Override public void dragEnter(java.awt.dnd.DropTargetDragEvent event) { accept(event); }
            @Override public void dragOver(java.awt.dnd.DropTargetDragEvent event) { accept(event); }
            @Override public void dropActionChanged(java.awt.dnd.DropTargetDragEvent event) { accept(event); }
            @Override public void dragExit(java.awt.dnd.DropTargetEvent event) {
                handler.status.accept("Drag files into the list or click Add Files.");
            }
            @Override public void drop(java.awt.dnd.DropTargetDropEvent event) {
                if (!event.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                        || (event.getSourceActions() & java.awt.dnd.DnDConstants.ACTION_COPY) == 0) {
                    event.rejectDrop();
                    return;
                }
                event.acceptDrop(java.awt.dnd.DnDConstants.ACTION_COPY);
                List<Path> paths;
                try {
                    paths = readFiles(event.getTransferable());
                } catch (Exception ex) {
                    event.dropComplete(false);
                    javax.swing.SwingUtilities.invokeLater(() -> handler.error.accept(
                            "Windows could not provide the dropped files. Use Add Files to select them."));
                    return;
                }
                // Finish the native operation before opening errors or closing a picker.
                event.dropComplete(true);
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (handler.addFiles(paths)) handler.status.accept("Files added. Click OK to keep them.");
                    else handler.status.accept("The files were not added. Check the message and try again.");
                });
            }
        };
    }

    private static List<Path> readFiles(java.awt.datatransfer.Transferable transfer) throws Exception {
        Object value = transfer.getTransferData(DataFlavor.javaFileListFlavor);
        if (!(value instanceof List<?> dropped) || dropped.isEmpty())
            throw new IllegalArgumentException("No files were supplied.");
        List<Path> paths = new ArrayList<>();
        for (Object entry : dropped) {
            if (!(entry instanceof File file)) throw new IllegalArgumentException("The drop was not a file.");
            paths.add(file.toPath());
        }
        return paths;
    }

    static void installPicker(javax.swing.JFileChooser picker, Consumer<String> error) {
        CustomOrderFileDropHandler handler = new CustomOrderFileDropHandler(paths -> {
            picker.setSelectedFiles(paths.stream().map(Path::toFile).toArray(File[]::new));
            picker.approveSelection();
        }, error);
        install(picker, handler);
        // File chooser UIs replace their file list when navigating between folders.
        picker.addPropertyChangeListener(javax.swing.JFileChooser.DIRECTORY_CHANGED_PROPERTY,
                event -> javax.swing.SwingUtilities.invokeLater(() -> install(picker, handler)));
    }

    @Override public boolean canImport(TransferSupport support) {
        if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                && !support.isDataFlavorSupported(DataFlavor.imageFlavor)) return false;
        if (support.isDrop() && (support.getSourceDropActions() & COPY) != 0) support.setDropAction(COPY);
        return true;
    }

    @Override public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;
        try {
            var transfer = support.getTransferable();
            if (transfer.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
                return addFiles(readFiles(transfer));
            var image = new javax.swing.ImageIcon((java.awt.Image) transfer.getTransferData(DataFlavor.imageFlavor));
            int width = image.getIconWidth(), height = image.getIconHeight();
            if (width <= 0 || height <= 0 || (long) width * height > 40_000_000L)
                throw new IllegalArgumentException("Invalid image dimensions.");
            var copy = new java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            var graphics = copy.createGraphics();
            try { graphics.drawImage(image.getImage(), 0, 0, null); } finally { graphics.dispose(); }
            Path path = Files.createTempFile("custom-order-photo-", ".png");
            path.toFile().deleteOnExit();
            try {
                if (!javax.imageio.ImageIO.write(copy, "png", path.toFile()))
                    throw new java.io.IOException("Could not save clipboard photo.");
                boolean added = addFiles(List.of(path));
                if (!added) Files.deleteIfExists(path);
                return added;
            } catch (Exception ex) { Files.deleteIfExists(path); throw ex; }
        } catch (Exception ex) {
            error.accept("The photo or files could not be added. Copy again or use Add Files.");
            return false;
        }
    }

    boolean addFiles(List<Path> paths) {
        List<Path> chosen = new ArrayList<>();
        for (Path path : paths) {
            Path normalized = path.toAbsolutePath().normalize();
            if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
                error.accept("Choose readable files; folders cannot be attached.");
                return false;
            }
            boolean duplicate = chosen.contains(normalized);
            for (int i = 0; i < files.size() && !duplicate; i++)
                duplicate = files.get(i).toAbsolutePath().normalize().equals(normalized);
            if (!duplicate) chosen.add(normalized);
        }
        if (files.size() + chosen.size() > 15) {
            error.accept("Choose at most 15 files for this line.");
            return false;
        }
        if (chosen.isEmpty()) return true;
        if (onFiles != null) onFiles.accept(List.copyOf(chosen));
        else chosen.forEach(files::addElement);
        return true;
    }
}
