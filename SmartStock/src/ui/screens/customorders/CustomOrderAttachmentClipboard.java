package ui.screens.customorders;

import com.google.gson.JsonObject;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.util.List;
import java.util.UUID;
import javax.swing.JOptionPane;
import ui.helpers.UiTaskRunner;

/** Copies downloaded attachment files, rather than their names or private URLs. */
final class CustomOrderAttachmentClipboard {
    static Transferable files(List<File> files) {
        List<File> snapshot=List.copyOf(files);
        return new Transferable(){
            public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{DataFlavor.javaFileListFlavor};}
            public boolean isDataFlavorSupported(DataFlavor flavor){return DataFlavor.javaFileListFlavor.equals(flavor);}
            public Object getTransferData(DataFlavor flavor)throws UnsupportedFlavorException{
                if(!isDataFlavorSupported(flavor))throw new UnsupportedFlavorException(flavor);
                return snapshot;
            }
        };
    }
    static void copy(Component parent,long orderId,List<JsonObject> attachments) {
        if(attachments.isEmpty())return;
        Window owner=parent instanceof Window window?window:javax.swing.SwingUtilities.getWindowAncestor(parent);
        UiTaskRunner.submit(owner,"orders.copy-attachments",()->{
            java.util.ArrayList<File> copies=new java.util.ArrayList<>();
            for(JsonObject file:attachments)copies.add(CustomOrderMediaDialog.download(orderId,
                UUID.fromString(file.get("file_id").getAsString()),false,file.get("filename").getAsString()).toFile());
            return files(copies);
        },transfer->{
            try{Toolkit.getDefaultToolkit().getSystemClipboard().setContents(transfer,null);}
            catch(IllegalStateException busy){JOptionPane.showMessageDialog(parent,"The clipboard is busy. Try copying again.");}
        },failure->JOptionPane.showMessageDialog(parent,"Could not copy attachment: "+failure.getMessage()));
    }
    static String safeFilename(String filename) {
        String safe=filename.replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]","_").replaceAll("[. ]+$","");
        if(safe.isBlank())safe="attachment";
        if(safe.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?"))safe="_"+safe;
        return safe;
    }
}
