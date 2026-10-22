package gy.deckers.studio;

import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/** Shows the exact fixed-aspect photo box used by canvas, exports and printing. */
final class PhotoFramingDialog {
    static PhotoFraming edit(Component owner,PhotoFraming original,double widthMm,double heightMm){
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(owner),"Frame photo",Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        FramePreview preview=new FramePreview(original,widthMm/heightMm);
        JPanel root=DeckersSwing.panel();root.setLayout(new BorderLayout(12,12));root.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        root.add(new JLabel(String.format(java.util.Locale.ROOT,"Photo box: %.1f × %.1f mm · Drag to position · Scroll to zoom",widthMm,heightMm)),BorderLayout.NORTH);
        root.add(preview);
        JPanel controls=DeckersSwing.panel();controls.setLayout(new BoxLayout(controls,BoxLayout.Y_AXIS));
        JPanel actions=DeckersSwing.panel();actions.setLayout(new FlowLayout(FlowLayout.LEFT));
        JSlider zoom=new JSlider(10,400,(int)Math.round(original.zoom()*100));JLabel zoomLabel=new JLabel();
        zoom.setToolTipText("Photo zoom; 100% fills the box");zoom.getAccessibleContext().setAccessibleName("Photo zoom");
        Runnable sync=()->{zoom.setValue((int)Math.round(preview.frame.zoom()*100));zoomLabel.setText(zoom.getValue()+"%");preview.repaint();};
        zoom.addChangeListener(e->{preview.frame=preview.frame.positioned(zoom.getValue()/100.,preview.frame.offsetX(),preview.frame.offsetY(),preview.frame.turns());zoomLabel.setText(zoom.getValue()+"%");preview.repaint();});
        installWheelZoom(preview,zoom);
        actions.add(StudioWorkspaceShell.button("Fit whole photo",()->{preview.frame=preview.frame.fit(widthMm,heightMm);sync.run();}));
        actions.add(StudioWorkspaceShell.button("Fill box",()->{preview.frame=preview.frame.positioned(1,0,0,preview.frame.turns());sync.run();}));
        actions.add(StudioWorkspaceShell.button("Rotate 90°",()->{preview.frame=preview.frame.positioned(preview.frame.zoom(),0,0,preview.frame.turns()+1);sync.run();}));
        actions.add(StudioWorkspaceShell.button("Reset",()->{preview.frame=preview.frame.positioned(1,0,0,0);sync.run();}));
        JPanel zoomRow=DeckersSwing.panel();zoomRow.setLayout(new BorderLayout(8,0));zoomRow.add(new JLabel("Zoom"),BorderLayout.WEST);zoomRow.add(zoom);zoomRow.add(zoomLabel,BorderLayout.EAST);
        controls.add(actions);controls.add(zoomRow);
        JPanel appearance=DeckersSwing.panel();appearance.setLayout(new FlowLayout(FlowLayout.LEFT));
        JCheckBox background=new JCheckBox("Background",original.background()!=null),outline=new JCheckBox("Outline",original.outline()!=null);
        background.setOpaque(false);outline.setOpaque(false);
        Color[] colors={original.background()==null?Color.WHITE:original.background(),original.outline()==null?Color.BLACK:original.outline()};
        JButton bgColor=StudioWorkspaceShell.button("Background color…",()->{}),lineColor=StudioWorkspaceShell.button("Outline color…",()->{});
        Runnable style=()->{bgColor.setEnabled(background.isSelected());lineColor.setEnabled(outline.isSelected());preview.frame=preview.frame.styled(background.isSelected()?colors[0]:null,outline.isSelected()?colors[1]:null);preview.repaint();};
        background.addActionListener(e->style.run());outline.addActionListener(e->style.run());
        bgColor.addActionListener(e->{Color chosen=JColorChooser.showDialog(dialog,"Photo background",colors[0]);if(chosen!=null){colors[0]=chosen;style.run();}});
        lineColor.addActionListener(e->{Color chosen=JColorChooser.showDialog(dialog,"Photo outline",colors[1]);if(chosen!=null){colors[1]=chosen;style.run();}});
        appearance.add(background);appearance.add(bgColor);appearance.add(outline);appearance.add(lineColor);controls.add(appearance);style.run();
        controls.add(new JLabel("Background off leaves uncovered areas transparent. It does not remove the photo's background."));
        PhotoFraming[] result={null};JPanel buttons=DeckersSwing.panel();buttons.setLayout(new FlowLayout(FlowLayout.RIGHT));
        JPanel corrections=DeckersSwing.panel();corrections.setLayout(new GridLayout(0,1,0,2));
        PhotoAdjustments initial=original.adjustments();
        String[] names={"Brightness","Contrast","Saturation","Warmth"};
        int[] values={initial.brightness(),initial.contrast(),initial.saturation(),initial.warmth()};
        JSlider[] sliders=new JSlider[4];
        for(int i=0;i<sliders.length;i++){
            JSlider slider=new JSlider(-100,100,values[i]);sliders[i]=slider;
            slider.getAccessibleContext().setAccessibleName(names[i]);
            slider.setToolTipText(names[i]+": -100 to +100; 0 keeps the original");
            JLabel value=new JLabel(Integer.toString(values[i]));value.setPreferredSize(new Dimension(38,20));
            JPanel row=DeckersSwing.panel();row.setLayout(new BorderLayout(8,0));
            JLabel label=new JLabel(names[i]);label.setPreferredSize(new Dimension(85,20));label.setLabelFor(slider);
            row.add(label,BorderLayout.WEST);row.add(slider);row.add(value,BorderLayout.EAST);corrections.add(row);
            slider.addChangeListener(e->value.setText(Integer.toString(slider.getValue())));
        }
        controls.add(corrections);
        buttons.add(StudioWorkspaceShell.button("Cancel",dialog::dispose));JButton apply=StudioWorkspaceShell.button("Apply photo",()->{result[0]=preview.frame;dialog.dispose();});buttons.add(apply);controls.add(buttons);root.add(controls,BorderLayout.SOUTH);
        int[] revision={0};
        Runnable adjust=()->{
            int version=++revision[0];apply.setEnabled(false);
            PhotoAdjustments settings=new PhotoAdjustments(sliders[0].getValue(),sliders[1].getValue(),sliders[2].getValue(),sliders[3].getValue());
            var source=preview.frame.source();
            new SwingWorker<java.awt.image.BufferedImage,Void>(){
                protected java.awt.image.BufferedImage doInBackground(){return settings.apply(source);}
                protected void done(){
                    if(version!=revision[0]||!dialog.isDisplayable())return;
                    try{PhotoFraming f=preview.frame;preview.frame=new PhotoFraming(f.source(),f.zoom(),f.offsetX(),f.offsetY(),f.turns(),f.background(),f.outline(),settings,get());preview.repaint();apply.setEnabled(true);}
                    catch(Exception ex){JOptionPane.showMessageDialog(dialog,"Could not adjust the photo. Try resetting the adjustments.","Photo adjustments",JOptionPane.ERROR_MESSAGE);}
                }
            }.execute();
        };
        Timer adjustmentDelay=new Timer(150,e->adjust.run());adjustmentDelay.setRepeats(false);
        for(JSlider slider:sliders)slider.addChangeListener(e->{++revision[0];apply.setEnabled(false);if(!slider.getValueIsAdjusting())adjustmentDelay.restart();});
        JButton removeBackground=new JButton("Remove background"),restorePhoto=new JButton("Restore photo background");
        JLabel processing=new JLabel(" ");
        JPanel removal=DeckersSwing.panel();removal.setLayout(new FlowLayout(FlowLayout.LEFT));
        removal.add(removeBackground);removal.add(restorePhoto);removal.add(processing);controls.add(removal,0);
        restorePhoto.setEnabled(false);
        removeBackground.setToolTipText("Remove the photo background using the store server's installed background-removal model.");
        removeBackground.addActionListener(e->{
            adjustmentDelay.stop();++revision[0];
            var source=preview.frame.source();
            setControlsEnabled(controls,false);preview.setEnabled(false);processing.setText("Removing background…");
            new SwingWorker<java.awt.image.BufferedImage,Void>(){
                protected java.awt.image.BufferedImage doInBackground()throws Exception{return BadgePhotoBackground.remove(source);}
                protected void done(){
                    if(!dialog.isDisplayable())return;
                    setControlsEnabled(controls,true);preview.setEnabled(true);style.run();
                    try{preview.frame=preview.frame.withSource(get());processing.setText("Background removed");}
                    catch(Exception ex){processing.setText("Photo unchanged");Throwable cause=ex.getCause()==null?ex:ex.getCause();JOptionPane.showMessageDialog(dialog,cause.getMessage(),"Background removal",JOptionPane.ERROR_MESSAGE);}
                    restorePhoto.setEnabled(preview.frame.source()!=original.source());adjust.run();
                }
            }.execute();
        });
        restorePhoto.addActionListener(e->{preview.frame=preview.frame.withSource(original.source());restorePhoto.setEnabled(false);processing.setText("Original restored");adjust.run();});
        dialog.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){adjustmentDelay.stop();++revision[0];}});
        buttons.add(StudioWorkspaceShell.button("Reset adjustments",()->{for(JSlider slider:sliders)slider.setValue(0);} ),0);
        dialog.setContentPane(root);dialog.getRootPane().setDefaultButton(apply);
        dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        sync.run();dialog.pack();dialog.setLocationRelativeTo(owner);dialog.setVisible(true);return result[0];
    }
    private static void setControlsEnabled(Container parent,boolean enabled){
        for(Component child:parent.getComponents()){child.setEnabled(enabled);if(child instanceof Container nested)setControlsEnabled(nested,enabled);}
    }
    static void installWheelZoom(JComponent preview,JSlider zoom) {
        final double[] remainder={0};
        MouseWheelListener wheel=e->{
            if(!preview.isEnabled())return;
            // Keep fractional wheel motion so smooth trackpads can make small adjustments.
            double change=remainder[0]-e.getPreciseWheelRotation()*5;
            int steps=(int)change;
            int value=Math.max(zoom.getMinimum(),Math.min(zoom.getMaximum(),zoom.getValue()+steps));
            remainder[0]=(value==zoom.getMinimum()||value==zoom.getMaximum())?0:change-steps;
            zoom.setValue(value);
            e.consume();
        };
        preview.addMouseWheelListener(wheel);
        zoom.addMouseWheelListener(wheel);
        preview.setToolTipText("Drag to position the photo. Scroll up to zoom in; scroll down to zoom out.");
    }

    static final class FramePreview extends JPanel {
        PhotoFraming frame;final double aspect;Rectangle box=new Rectangle();Point pressed;PhotoFraming start;
        FramePreview(PhotoFraming frame,double aspect){
            this.frame=frame;this.aspect=aspect;setPreferredSize(new Dimension(580,390));setBackground(DeckersPalette.background());
            MouseAdapter drag=new MouseAdapter(){
                public void mousePressed(MouseEvent e){if(isEnabled()&&SwingUtilities.isLeftMouseButton(e)&&box.contains(e.getPoint())){pressed=e.getPoint();start=FramePreview.this.frame;}}
                public void mouseDragged(MouseEvent e){if(pressed==null||!isEnabled())return;FramePreview.this.frame=start.positioned(start.zoom(),Math.max(-2,Math.min(2,start.offsetX()+(e.getX()-pressed.x)/(double)box.width)),Math.max(-2,Math.min(2,start.offsetY()+(e.getY()-pressed.y)/(double)box.height)),start.turns());repaint();}
                public void mouseReleased(MouseEvent e){pressed=null;}
            };addMouseListener(drag);addMouseMotionListener(drag);
        }
        protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);int width=Math.max(1,getWidth()-60),height=Math.max(1,getHeight()-40);
            if(width/(double)height>aspect)width=(int)(height*aspect);else height=(int)(width/aspect);
            box.setBounds((getWidth()-width)/2,(getHeight()-height)/2,width,height);
            Graphics2D g=(Graphics2D)graphics.create();try{frame.paint(g,box.x,box.y,box.width,box.height);g.setColor(DeckersPalette.PURPLE);g.setStroke(new BasicStroke(2));g.draw(box);
                g.setColor(new Color(128,128,128,130));for(int i=1;i<3;i++){int x=box.x+box.width*i/3,y=box.y+box.height*i/3;g.drawLine(x,box.y,x,box.y+box.height);g.drawLine(box.x,y,box.x+box.width,y);}
            }finally{g.dispose();}
        }
    }
}
