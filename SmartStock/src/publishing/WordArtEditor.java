package publishing;

import javax.swing.*;
import java.awt.*;

/** Returns a detached editable text layer; cancelling leaves the document untouched. */
public final class WordArtEditor {
  private WordArtEditor() {}
  public static Publication.Element choose(Component parent, Publication.Element original) {
    Publication.Element e=original==null?new Publication.Element():Publication.JSON.fromJson(Publication.JSON.toJson(original),Publication.Element.class);
    JTextField text=new JTextField(e.text),font=new JTextField(e.font),color=new JTextField(e.color),end=new JTextField(e.wordArtEndColor),outline=new JTextField(e.wordArtOutline),shadow=new JTextField(e.wordArtShadow);
    JComboBox<String> shape=new JComboBox<>(new String[]{"PLAIN","ARCH_UP","ARCH_DOWN","WAVE","NONE"});shape.setSelectedItem(e.wordArt.equals("NONE")?"PLAIN":e.wordArt);
    JSpinner bend=new JSpinner(new SpinnerNumberModel(e.wordArtBend,-1,1,.05)),width=new JSpinner(new SpinnerNumberModel(e.wordArtOutlineWidth,0,5,.05));
    JCheckBox gradient=new JCheckBox("Gradient",e.wordArtGradient),hasShadow=new JCheckBox("Shadow",e.wordArtHasShadow),bold=new JCheckBox("Bold",e.bold),italic=new JCheckBox("Italic",e.italic);
    JPanel form=new JPanel(new GridLayout(0,2,6,6));
    Object[][] rows={{"Text / {{field}}",text},{"Font family",font},{"Shape",shape},{"Bend (-1 to 1)",bend},{"Text colour (#RRGGBB)",color},{"Gradient end",end},{"Outline colour",outline},{"Outline width mm",width},{"Shadow colour",shadow},{"",gradient},{"",hasShadow},{"",bold},{"",italic}};
    for(var row:rows){form.add(new JLabel(row[0].toString()));form.add((Component)row[1]);}
    while(JOptionPane.showConfirmDialog(parent,form,"WordArt",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){
      try{for(var field:new JTextField[]{color,end,outline,shadow}){if(!field.getText().matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Use #RRGGBB colours.");}
        e.type="TEXT";e.text=text.getText();e.font=font.getText();e.wordArt=shape.getSelectedItem().toString();e.wordArtBend=((Number)bend.getValue()).doubleValue();e.color=color.getText();e.wordArtEndColor=end.getText();e.wordArtOutline=outline.getText();e.wordArtShadow=shadow.getText();e.wordArtOutlineWidth=((Number)width.getValue()).doubleValue();e.wordArtGradient=gradient.isSelected();e.wordArtHasShadow=hasShadow.isSelected();e.bold=bold.isSelected();e.italic=italic.isSelected();return e;
      }catch(Exception ex){JOptionPane.showMessageDialog(parent,ex.getMessage());}
    }return null;
  }
}
