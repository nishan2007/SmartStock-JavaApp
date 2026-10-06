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

    CustomOrderFileDropHandler(DefaultListModel<Path> files, Consumer<String> error) {
        this.files = files;
        this.error = error;
    }

    @Override public boolean canImport(TransferSupport support) {
        return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    @Override public boolean importData(TransferSupport support) {
        if (!canImport(support)) return false;
        try {
            Object value = support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            if (!(value instanceof List<?> dropped)) return false;
            List<Path> paths = new ArrayList<>();
            for (Object entry : dropped) {
                if (!(entry instanceof File file)) return false;
                paths.add(file.toPath());
            }
            return addFiles(paths);
        } catch (Exception ex) {
            error.accept("The dropped files could not be added. Use Add Files to select them.");
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
        chosen.forEach(files::addElement);
        return true;
    }
}
