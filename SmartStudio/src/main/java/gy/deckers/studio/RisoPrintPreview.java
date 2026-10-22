package gy.deckers.studio;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import ui.design.*;

/** Preview the same imposed, monochrome sheets that will be sent to the printer. */
final class RisoPrintPreview extends JDialog {
    private final LinkedRisoOutput.Job job;
    private final SheetPreview sheet = new SheetPreview();
    private final JLabel caption = new JLabel();
    private final JButton print = new JButton("Continue to printer…");
    private SwingWorker<BufferedImage,Void> worker;
    private boolean confirmed;
    private int revision;

    static boolean confirm(Component owner, LinkedRisoOutput.Job job) {
        var dialog = new RisoPrintPreview(owner, job);
        dialog.setVisible(true);
        return dialog.confirmed;
    }

    private RisoPrintPreview(Component owner, LinkedRisoOutput.Job job) {
        super(SwingUtilities.getWindowAncestor(owner), "Riso sub-pages • Print preview", ModalityType.APPLICATION_MODAL);
        this.job = job;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(8,8));
        root.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JComboBox<String> pages = new JComboBox<>();
        for (int i=0;i<job.count();i++) {
            String id=job.subPages().get(i/job.sheets());
            pages.addItem(LinkedRiso.subPage(job.document(),0,id).name + " • Sheet " + (i%job.sheets()+1));
        }
        JPanel navigation = new JPanel(new BorderLayout(8,8));
        navigation.add(pages,BorderLayout.CENTER);
        navigation.add(caption,BorderLayout.SOUTH);
        root.add(navigation,BorderLayout.NORTH);
        root.add(sheet,BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(DeckersSwing.metaLabel("Single-sided • Actual size / 100%"));
        JButton cancel=new JButton("Cancel");
        cancel.addActionListener(e->dispose());
        print.addActionListener(e->{confirmed=true;dispose();});
        actions.add(cancel);actions.add(print);
        root.add(actions,BorderLayout.SOUTH);
        setContentPane(root);
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(950,screen.width-80),Math.min(850,screen.height-80));
        setLocationRelativeTo(owner);
        pages.addActionListener(e->render(pages.getSelectedIndex()));
        render(0);
    }

    private void render(int index) {
        if(worker!=null)worker.cancel(true);
        int stamp=++revision;
        sheet.setImage(null);print.setEnabled(false);
        caption.setText("Preparing sheet " + (index+1) + " of " + job.count() + "…");
        worker=new SwingWorker<>() {
            protected BufferedImage doInBackground(){return LinkedRisoOutput.raster(job,index,96);}
            protected void done(){
                if(stamp!=revision)return;
                try{sheet.setImage(get());caption.setText("Sheet " + (index+1) + " of " + job.count());print.setEnabled(true);}
                catch(java.util.concurrent.CancellationException ignored){}
                catch(Exception ex){caption.setText("Could not prepare print preview: " + ex.getMessage());}
            }
        };
        worker.execute();
    }

    @Override public void dispose(){revision++;if(worker!=null)worker.cancel(true);super.dispose();}
}
