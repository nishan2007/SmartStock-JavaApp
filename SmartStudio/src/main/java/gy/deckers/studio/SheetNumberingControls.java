package gy.deckers.studio;

import publishing.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;
import java.util.function.Supplier;

final class SheetNumberingControls extends JPanel {
    final JComboBox<String> order=new JComboBox<>(new String[]{"Current layout order","Rows: left to right, top first","Rows: right to left, top first","Rows: left to right, bottom first","Rows: right to left, bottom first","Columns: top to bottom, left first","Columns: top to bottom, right first","Columns: bottom to top, left first","Columns: bottom to top, right first","Custom position order"});
    final JTextField custom=new JTextField(20);
    final JCheckBox cutStack=new JCheckBox("Cut and stack (consecutive set at each position)");

    SheetNumberingControls(Supplier<Publication> publication,Runnable changed){
        setOpaque(false);setLayout(new GridLayout(0,1));
        JPanel direction=row();direction.add(new JLabel("Numbering order"));direction.add(order);
        JPanel positions=row();positions.add(new JLabel("Custom positions"));positions.add(custom);
        JButton map=new JButton("Position map…");positions.add(map);
        custom.setEnabled(false);custom.setToolTipText("Position IDs from Position map, separated by commas; include every position once.");
        JPanel stacking=row();stacking.add(cutStack);
        cutStack.setToolTipText("Example: 1,000 records in two positions use 500 sheets: first position 1–500, second 501–1000. Use separate pages, not mixed pages.");
        order.addActionListener(event->{custom.setEnabled(order.getSelectedIndex()==9);changed.run();});
        cutStack.addActionListener(event->changed.run());
        custom.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent event){changed.run();}
            public void removeUpdate(DocumentEvent event){changed.run();}
            public void changedUpdate(DocumentEvent event){changed.run();}
        });
        map.addActionListener(event->{
            try{
                Publication source=publication.get();source.print.positionOrder="ROWS_LEFT_TOP";source.print.customPositionOrder="";
                source.print.cutAndStack=false;
                var layout=SheetLayout.pack(source.width,source.height,source.print);
                if(layout.capacity()==0)throw new IllegalArgumentException("The template does not fit the sheet.");
                JComponent diagram=new JPanel(){
                    protected void paintComponent(Graphics graphics){
                        super.paintComponent(graphics);Graphics2D draw=(Graphics2D)graphics.create();
                        try{
                            double scale=Math.min((getWidth()-30)/source.print.width,(getHeight()-30)/source.print.height);
                            draw.translate(15,15);draw.setColor(Color.WHITE);draw.fillRect(0,0,(int)(source.print.width*scale),(int)(source.print.height*scale));
                            draw.setColor(Color.DARK_GRAY);draw.drawRect(0,0,(int)(source.print.width*scale),(int)(source.print.height*scale));
                            for(int index=0;index<layout.capacity();index++){
                                var position=layout.placements().get(index);
                                int x=(int)(position.x()*scale),y=(int)(position.y()*scale),width=(int)(position.width()*scale),height=(int)(position.height()*scale);
                                draw.drawRect(x,y,width,height);String label=String.valueOf(index+1);
                                draw.drawString(label,x+(width-draw.getFontMetrics().stringWidth(label))/2,y+height/2);
                            }
                        }finally{draw.dispose();}
                    }
                };
                diagram.setPreferredSize(new Dimension(420,430));
                JPanel content=new JPanel(new BorderLayout());content.add(new JLabel("Custom order uses these position IDs, viewed from the front of the sheet."),BorderLayout.NORTH);content.add(diagram);
                content.add(new JLabel("Example for 2 columns × 3 rows: 2,1,4,3,6,5 or 2,4,6,1,3,5"),BorderLayout.SOUTH);
                JOptionPane.showMessageDialog(map,content,"Sheet position map",JOptionPane.PLAIN_MESSAGE);
            }catch(Exception ex){JOptionPane.showMessageDialog(map,ex.getMessage(),"Sheet position map",JOptionPane.ERROR_MESSAGE);}
        });
    }
    private JPanel row(){JPanel panel=new JPanel(new FlowLayout(FlowLayout.LEFT,5,3));panel.setOpaque(false);add(panel);return panel;}
    void apply(SheetLayout.Settings settings){settings.positionOrder=SheetSequence.ORDERS.get(order.getSelectedIndex());settings.customPositionOrder=custom.getText().trim();settings.cutAndStack=cutStack.isSelected();}
    void restore(SheetLayout.Settings settings){order.setSelectedIndex(SheetSequence.ORDERS.indexOf(settings.positionOrder));custom.setText(settings.customPositionOrder);cutStack.setSelected(settings.cutAndStack);}
}
