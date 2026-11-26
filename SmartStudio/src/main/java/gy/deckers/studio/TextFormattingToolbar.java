package gy.deckers.studio;

import javax.swing.*;
import java.awt.*;
import java.util.function.*;
import publishing.TextAlignment;
import ui.design.*;

/** Selection-bound text controls shared by every template editor. Sizes are points. */
final class TextFormattingToolbar extends JPanel {
    record Style(String font,double size,boolean bold,boolean italic,boolean underline,boolean strike,String color,boolean outline,String outlineColor,double outlineWidth,TextAlignment alignment){}
    final JComboBox<String> fonts=new JComboBox<>(publishing.PublicationFonts.families());
    final JComboBox<publishing.PublicationFonts.Face> styles=new JComboBox<>();
    final JSpinner size=new JSpinner(new SpinnerNumberModel(12.,1.5,140.,.5));
    private double maximumFontSize=140;
    void setMaximumFontSize(double value){maximumFontSize=value;refresh();}
    final JToggleButton bold=new JToggleButton("B"),italic=new JToggleButton("I"),underline=new JToggleButton("U"),strike=new JToggleButton("Strike");
    final JCheckBox outline=new JCheckBox("Outline");
    final JComboBox<TextAlignment> alignment=new JComboBox<>(TextAlignment.values());
    final JSpinner width=new JSpinner(new SpinnerNumberModel(.2/25.4,0.,5/25.4,.001));
    private final JButton color=new JButton("Text colour"),outlineColor=new JButton("Outline colour");
    private String textColor="#000000",edgeColor="#000000";
    private final Supplier<Style> selection;private final Consumer<Style> apply;
    private final Supplier<TextAlignment> defaultAlignment;
    private boolean updating;
    private JComboBox<String> quickFont;
    private JSpinner quickSize;
    private JComboBox<publishing.PublicationFonts.Face> quickStyles;
    private JButton quickColor;
    private JComboBox<TextAlignment> quickAlignment;
    JComponent quickControls(){
        JPanel panel=DeckersSwing.panel();panel.setLayout(new FlowLayout(FlowLayout.LEFT,6,0));
        quickFont=new JComboBox<>(publishing.PublicationFonts.families());quickFont.setPreferredSize(new Dimension(124,44));
        quickFont.getAccessibleContext().setAccessibleName("Text font");
        quickStyles=new JComboBox<>();configureFontDropdown(quickStyles);quickStyles.setPreferredSize(new Dimension(108,44));quickStyles.getAccessibleContext().setAccessibleName("Font style");quickStyles.addActionListener(e->{if(!updating&&quickStyles.getSelectedItem()!=null)styles.setSelectedItem(quickStyles.getSelectedItem());});
        quickSize=new JSpinner(size.getModel());quickSize.setPreferredSize(new Dimension(68,44));
        quickSize.getAccessibleContext().setAccessibleName("Text size in points");
        quickColor=StudioWorkspaceShell.button("Colour…",()->choose(false));
        quickFont.addActionListener(e->{if(!updating)fonts.setSelectedItem(quickFont.getSelectedItem());});
        configureFontDropdown(quickFont);
        quickAlignment=new JComboBox<>(TextAlignment.values());
        configureAlignment(quickAlignment,44);
        panel.add(captioned("Font",quickFont));panel.add(captioned("Style",quickStyles));panel.add(captioned("Size",quickSize));panel.add(captioned("Colour",quickColor));panel.add(captioned("Align",quickAlignment));refresh();return panel;
    }
    private void configureFontDropdown(JComboBox<?> combo){
        combo.setMaximumRowCount(16);
        combo.addPopupMenuListener(new javax.swing.event.PopupMenuListener(){
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e){}
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e){SwingUtilities.invokeLater(()->refresh());}
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e){SwingUtilities.invokeLater(()->refresh());}
        });
    }
    private boolean browsingFonts(){return fonts.isPopupVisible()||styles.isPopupVisible()||(quickFont!=null&&quickFont.isPopupVisible())||(quickStyles!=null&&quickStyles.isPopupVisible());}
    private static void refreshFamilies(JComboBox<String> combo){
        String[] families=publishing.PublicationFonts.families();
        boolean same=combo.getItemCount()==families.length;
        for(int i=0;same&&i<families.length;i++)same=java.util.Objects.equals(combo.getItemAt(i),families[i]);
        if(same)return;
        Object selected=combo.getSelectedItem();combo.setModel(new DefaultComboBoxModel<>(families));combo.setSelectedItem(selected);
    }
    private static JPanel captioned(String title,JComponent field){
        JPanel panel=new JPanel(new BorderLayout(0,6));panel.setOpaque(false);panel.add(field);
        JLabel label=new JLabel(title,SwingConstants.CENTER);label.putClientProperty("studio.ribbon.caption",true);label.setFont(StudioVisuals.font(Font.PLAIN,12));label.setForeground(StudioVisuals.INK);panel.add(label,BorderLayout.SOUTH);return panel;
    }
    TextFormattingToolbar(Supplier<Style> selection,Consumer<Style> apply,Supplier<TextAlignment> defaultAlignment){
        super(new FlowLayout(FlowLayout.LEFT,5,2));setOpaque(false);this.selection=selection;this.apply=apply;this.defaultAlignment=defaultAlignment;
        configureFontDropdown(fonts);configureFontDropdown(styles);fonts.setEditable(true);fonts.setPreferredSize(new Dimension(170,28));size.setPreferredSize(new Dimension(75,28));width.setPreferredSize(new Dimension(70,28));add(new JLabel("Text"));add(fonts);styles.setPreferredSize(new Dimension(140,40));styles.getAccessibleContext().setAccessibleName("Font style");add(styles);add(size);
        JButton minus=StudioWorkspaceShell.button("A−",()->step(-1)),plus=StudioWorkspaceShell.button("A+",()->step(1));add(minus);add(plus);
        for(JToggleButton button:new JToggleButton[]{bold,italic,underline,strike}){button.setForeground(DeckersPalette.PURPLE);button.setFocusPainted(false);add(button);button.addActionListener(e->{if(button==bold||button==italic)matchToggleStyle();save();});}
        for(JButton button:new JButton[]{color,outlineColor})DeckersSwing.styleUtilityButton(button,DeckersPalette.PURPLE);
        configureAlignment(alignment,40);add(alignment);add(color);add(outline);add(outlineColor);add(new JLabel("Width in"));add(width);
        fonts.addActionListener(e->{if(!updating){updateFaces(String.valueOf(fonts.getSelectedItem()),null,bold.isSelected(),italic.isSelected());save();}});styles.addActionListener(e->{if(!updating&&styles.getSelectedItem() instanceof publishing.PublicationFonts.Face face){updating=true;try{bold.setSelected(face.bold());italic.setSelected(face.italic());if(quickStyles!=null)quickStyles.setSelectedItem(face);}finally{updating=false;}save();}});size.addChangeListener(e->save());outline.addActionListener(e->save());width.addChangeListener(e->save());
        color.addActionListener(e->choose(false));outlineColor.addActionListener(e->choose(true));refresh();
    }
    private void configureAlignment(JComboBox<TextAlignment> field,int height){
        field.setPreferredSize(new Dimension(88,height));
        field.setToolTipText("Align text within its box");
        field.getAccessibleContext().setAccessibleName("Text alignment");
        field.addActionListener(e->changeAlignment((TextAlignment)field.getSelectedItem()));
    }
    private void changeAlignment(TextAlignment placement){
        if(updating||placement==null)return;
        Style current=selection.get();
        if(current==null||current.alignment()==placement)return;
        apply.accept(new Style(current.font(),current.size(),current.bold(),current.italic(),current.underline(),
                current.strike(),current.color(),current.outline(),current.outlineColor(),current.outlineWidth(),placement));
        refresh();
    }
    private void updateFaces(String family,String exact,boolean isBold,boolean isItalic){
        boolean previous=updating;updating=true;
        try{
            var options=publishing.PublicationFonts.faces(family);publishing.PublicationFonts.Face selected=null;
            for(var face:options)if(face.name().equalsIgnoreCase(exact==null?"":exact)&&face.bold()==isBold&&face.italic()==isItalic){selected=face;break;}
            if(selected==null)for(var face:options)if(face.bold()==isBold&&face.italic()==isItalic){selected=face;break;}
            if(selected==null&&!options.isEmpty())selected=options.get(0);
            setFaces(styles,options);styles.setSelectedItem(selected);styles.setEnabled(selection.get()!=null&&!options.isEmpty());
            if(quickStyles!=null){setFaces(quickStyles,options);quickStyles.setSelectedItem(selected);quickStyles.setEnabled(styles.isEnabled());}
        }finally{updating=previous;}
    }
    private static void setFaces(JComboBox<publishing.PublicationFonts.Face> combo,java.util.List<publishing.PublicationFonts.Face> faces){
        boolean same=combo.getItemCount()==faces.size();
        for(int i=0;same&&i<faces.size();i++)same=java.util.Objects.equals(combo.getItemAt(i),faces.get(i));
        if(!same)combo.setModel(new DefaultComboBoxModel<>(faces.toArray(publishing.PublicationFonts.Face[]::new)));
    }
    private void matchToggleStyle(){
        if(updating)return;updateFaces(String.valueOf(fonts.getSelectedItem()),null,bold.isSelected(),italic.isSelected());
    }
    private void step(int delta){size.setValue(Math.max(1.5,Math.min(((Number)((SpinnerNumberModel)size.getModel()).getMaximum()).doubleValue(),((Number)size.getValue()).doubleValue()+delta)));}
    private static Icon swatch(String hex){
        Color colour=Color.decode(hex);
        return new Icon(){
            public int getIconWidth(){return 16;}public int getIconHeight(){return 16;}
            public void paintIcon(Component c,Graphics g,int x,int y){g.setColor(colour);g.fillRect(x,y,15,15);g.setColor(DeckersPalette.border());g.drawRect(x,y,15,15);}
        };
    }
    private void choose(boolean edge){Color chosen=JColorChooser.showDialog(this,edge?"Text outline colour":"Text colour",Color.decode(edge?edgeColor:textColor));if(chosen==null)return;String hex=String.format("#%06x",chosen.getRGB()&0xffffff);if(edge)edgeColor=hex;else textColor=hex;save();}
    void refresh(){Style s=selection.get();if(s!=null&&browsingFonts())return;putClientProperty("studio.selection.available",s!=null);updating=true;try{refreshFamilies(fonts);if(quickFont!=null)refreshFamilies(quickFont);for(Component c:getComponents())c.setEnabled(s!=null);if(quickFont!=null){quickFont.setEnabled(s!=null);quickStyles.setEnabled(s!=null);quickSize.setEnabled(s!=null);quickColor.setEnabled(s!=null);quickAlignment.setEnabled(s!=null);}if(s==null)return;TextAlignment placement=s.alignment()==null?defaultAlignment.get():s.alignment();alignment.setSelectedItem(placement);if(quickAlignment!=null)quickAlignment.setSelectedItem(placement);String family=publishing.PublicationFonts.fontFamily(s.font());fonts.setSelectedItem(family);if(quickFont!=null)quickFont.setSelectedItem(family);updateFaces(family,s.font(),s.bold(),s.italic());((SpinnerNumberModel)size.getModel()).setMaximum(Math.max(maximumFontSize,s.size()));size.setValue(Math.max(1.5,s.size()));bold.setSelected(s.bold());italic.setSelected(s.italic());underline.setSelected(s.underline());strike.setSelected(s.strike());outline.setSelected(s.outline());width.setValue(s.outlineWidth()/25.4);textColor=s.color();edgeColor=s.outlineColor();color.setForeground(DeckersPalette.text());outlineColor.setForeground(DeckersPalette.text());color.setIcon(swatch(textColor));outlineColor.setIcon(swatch(edgeColor));for(JButton button:new JButton[]{color,outlineColor}){button.setPreferredSize(null);Dimension d=button.getPreferredSize();button.setPreferredSize(new Dimension(d.width,Math.max(40,d.height)));}if(quickColor!=null){quickColor.setIcon(swatch(textColor));quickColor.setPreferredSize(null);Dimension d=quickColor.getPreferredSize();quickColor.setPreferredSize(new Dimension(d.width,Math.max(44,d.height)));}}finally{updating=false;}}
    private void save(){Style current=selection.get();if(current!=null)save(current.alignment());}
    private void save(TextAlignment placement){if(updating||selection.get()==null)return;try{String font=String.valueOf(fonts.getSelectedItem()).strip();if(font.isEmpty()||font.length()>100)throw new IllegalArgumentException("Choose a font name up to 100 characters.");if(styles.getSelectedItem() instanceof publishing.PublicationFonts.Face face)font=face.name();apply.accept(new Style(font,((Number)size.getValue()).doubleValue(),bold.isSelected(),italic.isSelected(),underline.isSelected(),strike.isSelected(),textColor,outline.isSelected(),edgeColor,((Number)width.getValue()).doubleValue()*25.4,placement));}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Text formatting",JOptionPane.ERROR_MESSAGE);}refresh();}
}

