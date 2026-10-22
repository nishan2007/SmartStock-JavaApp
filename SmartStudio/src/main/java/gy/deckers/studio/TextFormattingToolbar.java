package gy.deckers.studio;

import javax.swing.*;
import java.awt.*;
import java.util.function.*;
import ui.design.*;

/** Selection-bound text controls shared by every template editor. Sizes are points. */
final class TextFormattingToolbar extends JPanel {
    record Style(String font,double size,boolean bold,boolean italic,boolean underline,boolean strike,String color,boolean outline,String outlineColor,double outlineWidth){}
    final JComboBox<String> fonts=new JComboBox<>(publishing.PublicationFonts.names());
    final JSpinner size=new JSpinner(new SpinnerNumberModel(12.,1.5,140.,.5));
    private double maximumFontSize=140;
    void setMaximumFontSize(double value){maximumFontSize=value;refresh();}
    final JToggleButton bold=new JToggleButton("B"),italic=new JToggleButton("I"),underline=new JToggleButton("U"),strike=new JToggleButton("Strike");
    final JCheckBox outline=new JCheckBox("Outline");
    final JSpinner width=new JSpinner(new SpinnerNumberModel(.2,0.,5.,.1));
    private final JButton color=new JButton("Text colour"),outlineColor=new JButton("Outline colour");
    private String textColor="#000000",edgeColor="#000000";
    private final Supplier<Style> selection;private final Consumer<Style> apply;private boolean updating;
    TextFormattingToolbar(Supplier<Style> selection,Consumer<Style> apply){
        super(new FlowLayout(FlowLayout.LEFT,5,2));setOpaque(false);this.selection=selection;this.apply=apply;
        fonts.addPopupMenuListener(new javax.swing.event.PopupMenuListener(){public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e){Object selected=fonts.getSelectedItem();updating=true;try{fonts.setModel(new DefaultComboBoxModel<>(publishing.PublicationFonts.names()));fonts.setSelectedItem(selected);}finally{updating=false;}}public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e){}public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e){}});fonts.setEditable(true);fonts.setPreferredSize(new Dimension(170,28));size.setPreferredSize(new Dimension(75,28));width.setPreferredSize(new Dimension(70,28));add(new JLabel("Text"));add(fonts);add(size);
        JButton minus=StudioWorkspaceShell.button("A−",()->step(-1)),plus=StudioWorkspaceShell.button("A+",()->step(1));add(minus);add(plus);
        for(JToggleButton button:new JToggleButton[]{bold,italic,underline,strike}){button.setForeground(DeckersPalette.PURPLE);button.setFocusPainted(false);add(button);button.addActionListener(e->save());}
        for(JButton button:new JButton[]{color,outlineColor})DeckersSwing.styleUtilityButton(button,DeckersPalette.PURPLE);
        add(color);add(outline);add(outlineColor);add(new JLabel("Width mm"));add(width);
        fonts.addActionListener(e->save());size.addChangeListener(e->save());outline.addActionListener(e->save());width.addChangeListener(e->save());
        color.addActionListener(e->choose(false));outlineColor.addActionListener(e->choose(true));refresh();
    }
    private void step(int delta){size.setValue(Math.max(1.5,Math.min(((Number)((SpinnerNumberModel)size.getModel()).getMaximum()).doubleValue(),((Number)size.getValue()).doubleValue()+delta)));}
    private void choose(boolean edge){Color chosen=JColorChooser.showDialog(this,edge?"Text outline colour":"Text colour",Color.decode(edge?edgeColor:textColor));if(chosen==null)return;String hex=String.format("#%06x",chosen.getRGB()&0xffffff);if(edge)edgeColor=hex;else textColor=hex;save();}
    void refresh(){Style s=selection.get();updating=true;try{for(Component c:getComponents())c.setEnabled(s!=null);if(s==null)return;fonts.setSelectedItem(s.font());((SpinnerNumberModel)size.getModel()).setMaximum(Math.max(maximumFontSize,s.size()));size.setValue(Math.max(1.5,s.size()));bold.setSelected(s.bold());italic.setSelected(s.italic());underline.setSelected(s.underline());strike.setSelected(s.strike());outline.setSelected(s.outline());width.setValue(s.outlineWidth());textColor=s.color();edgeColor=s.outlineColor();color.setForeground(Color.decode(textColor));outlineColor.setForeground(Color.decode(edgeColor));}finally{updating=false;}}
    private void save(){if(updating||selection.get()==null)return;try{size.commitEdit();width.commitEdit();String font=String.valueOf(fonts.getSelectedItem()).strip();if(font.isEmpty()||font.length()>100)throw new IllegalArgumentException("Choose a font name up to 100 characters.");apply.accept(new Style(font,((Number)size.getValue()).doubleValue(),bold.isSelected(),italic.isSelected(),underline.isSelected(),strike.isSelected(),textColor,outline.isSelected(),edgeColor,((Number)width.getValue()).doubleValue()));}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Text formatting",JOptionPane.ERROR_MESSAGE);}refresh();}
}

