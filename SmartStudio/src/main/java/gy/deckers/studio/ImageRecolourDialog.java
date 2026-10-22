package gy.deckers.studio;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
import ui.design.DeckersSwing;
import ui.design.DeckersPalette;

final class ImageRecolourDialog extends JPanel {
    private final JComboBox<String> mode=new JComboBox<>(new String[]{"Tint whole picture","Replace one colour","Grayscale"});
    private final JSpinner tolerance=new JSpinner(new SpinnerNumberModel(15,0,100,1));
    private final JCheckBox shades=new JCheckBox("Preserve shading / white",true);
    private final JButton from=new JButton("Source colour"),to=new JButton("New colour");
    private final JLabel result=new JLabel();
    private Color sourceColor=Color.BLACK,targetColor=new Color(0,128,0);
    private final BufferedImage sample;
    private ImageRecolourDialog(BufferedImage source){
        super(new BorderLayout(8,8));setBackground(DeckersPalette.surface());
        double scale=Math.min(1,Math.min(420./source.getWidth(),400./source.getHeight()));
        sample=new BufferedImage(Math.max(1,(int)Math.round(source.getWidth()*scale)),Math.max(1,(int)Math.round(source.getHeight()*scale)),BufferedImage.TYPE_INT_ARGB);Graphics2D g=sample.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(source,0,0,sample.getWidth(),sample.getHeight(),null);g.dispose();
        JPanel options=DeckersSwing.panel();options.add(mode);options.add(from);options.add(to);options.add(new JLabel("Tolerance %"));options.add(tolerance);options.add(shades);add(options,BorderLayout.NORTH);
        JPanel pictures=DeckersSwing.panel();pictures.setLayout(new GridLayout(1,2,12,0));JLabel original=new JLabel(new ImageIcon(sample));original.setToolTipText("Click the original picture to pick a source colour.");pictures.add(original);pictures.add(result);add(pictures,BorderLayout.CENTER);
        original.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){int x=e.getX()-(original.getWidth()-sample.getWidth())/2,y=e.getY()-(original.getHeight()-sample.getHeight())/2;if(x>=0&&x<sample.getWidth()&&y>=0&&y<sample.getHeight()){int sx=Math.min(source.getWidth()-1,(int)(x/(double)sample.getWidth()*source.getWidth())),sy=Math.min(source.getHeight()-1,(int)(y/(double)sample.getHeight()*source.getHeight()));sourceColor=new Color(source.getRGB(sx,sy));mode.setSelectedIndex(1);refresh();}}});
        from.addActionListener(e->{Color color=JColorChooser.showDialog(this,"Colour to replace",sourceColor);if(color!=null){sourceColor=color;refresh();}});
        to.addActionListener(e->{Color color=JColorChooser.showDialog(this,"New colour",targetColor);if(color!=null){targetColor=color;refresh();}});
        mode.addActionListener(e->refresh());tolerance.addChangeListener(e->refresh());shades.addActionListener(e->refresh());
        add(new JLabel("Original (click to pick a colour)                         Preview • Apply creates new artwork; Undo restores the previous image."),BorderLayout.SOUTH);refresh();
    }
    private ImageRecolour.Settings settings(){return new ImageRecolour.Settings(ImageRecolour.Mode.values()[mode.getSelectedIndex()],sourceColor,targetColor,(Integer)tolerance.getValue(),shades.isSelected());}
    private void refresh(){boolean replace=mode.getSelectedIndex()==1;from.setEnabled(replace);tolerance.setEnabled(replace);to.setEnabled(mode.getSelectedIndex()!=2);shades.setEnabled(mode.getSelectedIndex()!=2);from.setBackground(sourceColor);to.setBackground(targetColor);result.setIcon(new ImageIcon(ImageRecolour.apply(sample,settings())));}
    static ImageRecolour.Settings choose(Component parent,BufferedImage source){var dialog=new ImageRecolourDialog(source);return JOptionPane.showConfirmDialog(parent,dialog,"Recolour picture",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION?dialog.settings():null;}
    static BufferedImage edit(Component parent,BufferedImage source)throws Exception{
        var settings=choose(parent,source);if(settings==null)return null;
        JDialog progress=new JDialog(SwingUtilities.getWindowAncestor(parent),"Recolouring picture…",Dialog.ModalityType.APPLICATION_MODAL);progress.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JProgressBar bar=new JProgressBar();bar.setIndeterminate(true);progress.add(bar);progress.setSize(320,90);progress.setLocationRelativeTo(parent);
        SwingWorker<BufferedImage,Void> worker=new SwingWorker<>(){protected BufferedImage doInBackground(){return ImageRecolour.apply(source,settings);}protected void done(){progress.dispose();}};
        JButton cancel=new JButton("Cancel");cancel.addActionListener(e->worker.cancel(true));progress.add(cancel,BorderLayout.SOUTH);worker.execute();progress.setVisible(true);return worker.isCancelled()?null:worker.get();
    }
}
