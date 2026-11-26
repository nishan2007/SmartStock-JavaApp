package ui.screens;

import javax.swing.*;
import java.awt.*;

/** Shared sequential ad review from any authorized register. */
public final class ProductAdReviewDialog extends JDialog {
    public ProductAdReviewDialog(Window owner) {this(owner,()->{});}
    public ProductAdReviewDialog(Window owner,Runnable changed) {
        super(owner,"Product Ad Reviews",ModalityType.MODELESS);
        var panel=new ProductAdReviewPanel(ProductAdReviewPanel.SERVER,changed);
        setContentPane(panel);Rectangle screen=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(Math.min(1400,screen.width-32),Math.min(950,screen.height-32));setMinimumSize(new Dimension(780,550));setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){if(!panel.isWorking())dispose();}});
    }
}
