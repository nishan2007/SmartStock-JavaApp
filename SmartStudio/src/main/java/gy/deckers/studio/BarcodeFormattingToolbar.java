package gy.deckers.studio;

import publishing.*;
import ui.design.*;
import javax.swing.*;
import java.awt.*;
import java.util.function.*;

/** Selection-bound barcode controls; all edits keep the encoded value and artwork separate. */
final class BarcodeFormattingToolbar extends JPanel {
    record Settings(String value,String format,boolean caption,double width,double height,double rotation){}
    final JTextField value=new JTextField(18);
    final JComboBox<String> format=new JComboBox<>(PublicationBarcode.FORMATS.toArray(String[]::new));
    final JCheckBox caption=new JCheckBox("Show text/numbers");
    final JSpinner width=new JSpinner(new SpinnerNumberModel(50.,.1,2000.,.5)),height=new JSpinner(new SpinnerNumberModel(25.,.1,2000.,.5)),rotation=new JSpinner(new SpinnerNumberModel(0.,-3600.,3600.,90.));
    private final Supplier<Publication.Element> selection;private final Consumer<Settings> apply;private boolean refreshing;
    BarcodeFormattingToolbar(Supplier<Publication.Element> selection,Consumer<Settings> apply){
        super(new FlowLayout(FlowLayout.LEFT,5,2));setOpaque(false);this.selection=selection;this.apply=apply;
        add(new JLabel("Barcode / QR"));add(format);add(new JLabel("Value"));add(value);
        JButton update=StudioWorkspaceShell.button("Apply value",this::save);add(update);add(caption);caption.setOpaque(false);
        caption.setToolTipText("Show or hide the text below the bars without changing the encoded value.");
        for(var pair:new Object[][]{{"Width mm",width},{"Height mm",height},{"Rotation °",rotation}}){add(new JLabel((String)pair[0]));var spinner=(JSpinner)pair[1];spinner.setPreferredSize(new Dimension(76,28));add(spinner);spinner.addChangeListener(e->save());}
        format.addActionListener(e->save());caption.addActionListener(e->save());value.addActionListener(e->save());refresh();
    }
    void refresh(){var e=selection.get();refreshing=true;try{
        for(Component c:getComponents())c.setEnabled(e!=null);if(e==null)return;
        value.setText(e.text);format.setSelectedItem(e.type.equals("QR")?"QR_CODE":e.barcodeFormat);caption.setSelected(e.barcodeText);caption.setEnabled(!e.type.equals("QR"));
        width.setValue(e.width);height.setValue(e.height);rotation.setValue(e.rotation);
    }finally{refreshing=false;}}
    void save(){if(refreshing||selection.get()==null)return;try{
        width.commitEdit();height.commitEdit();rotation.commitEdit();String data=value.getText().strip(),kind=String.valueOf(format.getSelectedItem());
        if(!data.contains("{{"))PublicationBarcode.encode(data,kind);else if(!data.matches(".*\\{\\{[A-Za-z][A-Za-z0-9_]*}}.*"))throw new IllegalArgumentException("Use a valid merge field name.");
        apply.accept(new Settings(data,kind,caption.isSelected(),((Number)width.getValue()).doubleValue(),((Number)height.getValue()).doubleValue(),((Number)rotation.getValue()).doubleValue()));
    }catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Barcode settings",JOptionPane.ERROR_MESSAGE);}refresh();}
}
