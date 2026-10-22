package gy.deckers.studio;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.function.*;

/** Physical picture dimensions; ratio locking is on by default for each selected picture. */
final class ImageFormattingToolbar extends JPanel {
    record Picture(Object key,double width,double height,int transparency){Picture(Object key,double width,double height){this(key,width,height,0);}}
    record Size(double width,double height){}
    final JSpinner width=new JSpinner(new SpinnerNumberModel(25.,.01,2000.,.5)),height=new JSpinner(new SpinnerNumberModel(25.,.01,2000.,.5));
    final JSpinner transparency=new JSpinner(new SpinnerNumberModel(0,0,100,5));
    void setTransparencyEditor(IntConsumer action){add(new JLabel("Transparency %"));add(transparency);transparency.setToolTipText("0% is solid; 100% is invisible.");transparency.addChangeListener(e->{if(!updating&&selection.get()!=null){action.accept((Integer)transparency.getValue());refresh();}});refresh();}
    final JCheckBox locked=new JCheckBox("Lock aspect ratio",true);
    final JComboBox<String> units=new JComboBox<>(new String[]{"mm","inches"});
    private final Supplier<Picture> selection;private final Consumer<Size> apply;private Object key;private boolean updating;
    private double maximumMm=2000;
    void setMaximumSizeMm(double value){maximumMm=value;refresh();}
    ImageFormattingToolbar(Supplier<Picture> selection,Consumer<Size> apply){
        this(selection,apply,null);
    }
    ImageFormattingToolbar(Supplier<Picture> selection,Consumer<Size> apply,Runnable recolour){
        super(new FlowLayout(FlowLayout.LEFT,6,2));setOpaque(false);this.selection=selection;this.apply=apply;
        add(new JLabel("Picture size"));add(new JLabel("Width"));add(width);add(new JLabel("Height"));add(height);add(units);add(locked);locked.setOpaque(false);
        for(var spinner:new JSpinner[]{width,height}){spinner.setPreferredSize(new Dimension(100,28));spinner.setEditor(new JSpinner.NumberEditor(spinner,"0.###"));}
        locked.setToolTipText("Locked: changing either size adjusts the other to preserve this picture's current proportions.");
        width.addChangeListener(e->resize(true));height.addChangeListener(e->resize(false));units.addActionListener(e->refresh());refresh();
        if(recolour!=null){JButton button=StudioWorkspaceShell.button("Recolour picture…",recolour);add(button);refresh();}
    }
    void refresh(){Picture picture=selection.get();updating=true;try{
        for(Component c:getComponents())c.setEnabled(picture!=null);if(picture==null)return;
        if(!Objects.equals(key,picture.key())){key=picture.key();locked.setSelected(true);}
        double factor=units.getSelectedIndex()==1?25.4:1;
        for(var spinner:new JSpinner[]{width,height}){var model=(SpinnerNumberModel)spinner.getModel();model.setMinimum(.01/factor);model.setMaximum(maximumMm/factor);model.setStepSize(factor==1?.5:.01);}
        transparency.setValue(picture.transparency());
        width.setValue(picture.width()/factor);height.setValue(picture.height()/factor);
    }finally{updating=false;}}
    static Size resized(Picture picture,boolean changeWidth,double value,boolean lock){
        return resized(picture,changeWidth,value,lock,2000);
    }
    static Size resized(Picture picture,boolean changeWidth,double value,boolean lock,double maximumMm){
        if(picture==null||!Double.isFinite(value)||value<=0||picture.width()<=0||picture.height()<=0)throw new IllegalArgumentException("Enter a positive picture size.");
        double w=changeWidth?value:lock?value*picture.width()/picture.height():picture.width();
        double h=!changeWidth?value:lock?value*picture.height()/picture.width():picture.height();
        if(!Double.isFinite(w+h)||Math.min(w,h)<.01||Math.max(w,h)>maximumMm)throw new IllegalArgumentException("Picture dimensions must be between 0.01 and "+maximumMm+" mm.");
        return new Size(w,h);
    }
    private void resize(boolean changeWidth){if(updating)return;var picture=selection.get();if(picture==null)return;
        try{double factor=units.getSelectedIndex()==1?25.4:1;apply.accept(resized(picture,changeWidth,((Number)(changeWidth?width:height).getValue()).doubleValue()*factor,locked.isSelected(),maximumMm));}
        catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Picture size",JOptionPane.ERROR_MESSAGE);}refresh();
    }
}
