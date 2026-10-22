package gy.deckers.studio;
import publishing.*;
import javax.swing.*;
import java.awt.*;

final class BarcodeEditor {
 private BarcodeEditor(){}
 static Publication.Element choose(Component owner,Publication.Element original){
  return choose(owner,original,"CODE_128");
 }
 static Publication.Element chooseQr(Component owner){return choose(owner,null,"QR_CODE");}
 private static Publication.Element choose(Component owner,Publication.Element original,String initialFormat){
  JComboBox<String> formats=new JComboBox<>(PublicationBarcode.FORMATS.toArray(String[]::new));formats.setSelectedItem(original==null?initialFormat:original.type.equals("QR")?"QR_CODE":original.barcodeFormat);
  JTextField data=new JTextField(original==null?"":original.text,28);JCheckBox caption=new JCheckBox("Show text/numbers below barcode",original==null||original.barcodeText);
  Runnable updateCaption=()->caption.setEnabled(!"QR_CODE".equals(formats.getSelectedItem()));
  formats.addActionListener(e->updateCaption.run());updateCaption.run();
  caption.setToolTipText("Uncheck to print only the bars. The encoded barcode value is unchanged.");
  JPanel form=new JPanel(new GridLayout(0,1,4,4));form.add(new JLabel("Barcode type"));form.add(formats);form.add(new JLabel("Data (or {{merge_field}})"));form.add(data);form.add(caption);form.add(new JLabel("EAN / UPC require valid numeric lengths and check digits."));
  while(JOptionPane.showConfirmDialog(owner,form,original==null?(initialFormat.equals("QR_CODE")?"Generate QR code":"Generate barcode / QR"):"Edit barcode / QR",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){
   try{String text=data.getText().strip(),format=formats.getSelectedItem().toString();if(!text.contains("{{"))PublicationBarcode.encode(text,format);else if(!text.matches(".*\\{\\{[A-Za-z][A-Za-z0-9_]*}}.*"))throw new IllegalArgumentException("Use a valid merge field name.");
    var result=original==null?new Publication.Element():Publication.JSON.fromJson(Publication.JSON.toJson(original),Publication.Element.class);result.type=format.equals("QR_CODE")?"QR":"BARCODE";result.text=text;result.barcodeFormat=format;result.barcodeText=caption.isSelected();result.asset="";if(original==null){result.width=format.equals("QR_CODE")?25:50;result.height=25;}return result;
   }catch(Exception ex){JOptionPane.showMessageDialog(owner,ex.getMessage(),"Barcode data",JOptionPane.ERROR_MESSAGE);}
  }return null;
 }
}
