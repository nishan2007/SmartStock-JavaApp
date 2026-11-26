package gy.deckers.studio;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Shared photo source choice for template editors. */
final class StudioPhotoPicker {
    static int source(Component owner) {
        return JOptionPane.showOptionDialog(owner,"Choose a photo source","Choose photo",
            JOptionPane.DEFAULT_OPTION,JOptionPane.PLAIN_MESSAGE,null,
            new String[]{"Use from local PC","Use from Photo Queue","Cancel"},"Use from local PC");
    }
    static StudioPhotoQueuePanel.Photo queued(Component owner) {
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(owner),"Choose from Photo Queue",Dialog.ModalityType.APPLICATION_MODAL);
        StudioPhotoQueuePanel.Photo[] selected={null};
        StudioPhotoQueuePanel queue=new StudioPhotoQueuePanel(photo->{selected[0]=photo;dialog.dispose();},true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.add(queue);
        JPanel footer=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.add(StudioWorkspaceShell.button("Cancel",dialog::dispose));dialog.add(footer,BorderLayout.SOUTH);
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setSize(Math.min(1050,screen.width-80),Math.min(700,screen.height-80));
        dialog.setLocationRelativeTo(owner);
        try{dialog.setVisible(true);return selected[0];}finally{queue.reset();dialog.dispose();}
    }
    static BufferedImage photo(Component owner,java.util.concurrent.Callable<BufferedImage> local)throws Exception {
        int source=source(owner);
        if(source==0)return local.call();
        if(source==1){var photo=queued(owner);return photo==null?null:photo.image();}
        return null;
    }
}
