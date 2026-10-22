package gy.deckers.studio;

import publishing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import ui.design.DeckersSwing;

/** Assign one or several pictures without recolouring the saved design. */
final class RisoImageAssignmentsPanel extends JPanel {
    record Choice(int index,String label){public String toString(){return label;}}
    private final List<RisoImageAssignments.Target> targets;
    private final List<Choice> choices=new ArrayList<>();
    private final Map<String,Integer> assigned=new LinkedHashMap<>();
    final JTable table;
    final JComboBox<Choice> ink=new JComboBox<>();
    private final JLabel picture=new JLabel("Select a picture to preview it",SwingConstants.CENTER);
    RisoImageAssignmentsPanel(Publication publication,List<RisoSeparation.Ink> inks,Map<String,Integer> existing){
        super(new BorderLayout(8,8));targets=RisoImageAssignments.targets(publication);assigned.putAll(existing);choices.add(new Choice(-1,"Automatic separation"));
        for(int i=0;i<inks.size();i++)choices.add(new Choice(i,(i+1)+" • "+inks.get(i).name()+(inks.get(i).enabled()?"":" (disabled)")));
        var model=new AbstractTableModel(){public int getRowCount(){return targets.size();}public int getColumnCount(){return 2;}public String getColumnName(int c){return c==0?"Picture":"Print using";}public Class<?> getColumnClass(int c){return c==0?String.class:Choice.class;}public Object getValueAt(int r,int c){return c==0?targets.get(r).label():choice(assigned.getOrDefault(targets.get(r).key(),-1));}public boolean isCellEditable(int r,int c){return c==1;}public void setValueAt(Object value,int row,int col){if(col==1&&value instanceof Choice c){set(row,c.index());fireTableRowsUpdated(row,row);}}};
        table=new JTable(model);table.setRowHeight(28);table.getColumnModel().getColumn(0).setPreferredWidth(340);table.getColumnModel().getColumn(1).setPreferredWidth(210);
        var editor=new JComboBox<Choice>(choices.toArray(Choice[]::new));table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(editor));
        picture.setPreferredSize(new Dimension(200,180));JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,new JScrollPane(table),new JScrollPane(picture));split.setResizeWeight(.75);add(split,BorderLayout.CENTER);
        choices.forEach(ink::addItem);JPanel tools=DeckersSwing.panel();tools.add(new JLabel("Selected pictures:"));tools.add(ink);JButton apply=StudioWorkspaceShell.button("Assign ink",()->{commit();Choice selected=(Choice)ink.getSelectedItem();int[] rows=table.getSelectedRows();for(int row:rows)set(row,selected.index());if(rows.length>0)model.fireTableRowsUpdated(rows[0],rows[rows.length-1]);});tools.add(apply);add(tools,BorderLayout.NORTH);
        JTextArea help=new JTextArea("Choose an ink for each picture, or Ctrl/Shift-select several rows and use Assign ink. Automatic separation keeps the default behaviour. Assigned pictures print as grayscale tones on just the chosen ink; white stays unprinted. These choices apply only to this Riso job and do not change the template.");help.setRows(4);help.setColumns(65);help.setLineWrap(true);help.setWrapStyleWord(true);help.setEditable(false);help.setOpaque(false);help.setBorder(BorderFactory.createEmptyBorder(5,5,5,5));help.setPreferredSize(new Dimension(760,help.getFontMetrics(help.getFont()).getHeight()*5+10));add(help,BorderLayout.SOUTH);
        table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())showPicture(publication);});setPreferredSize(new Dimension(800,440));if(!targets.isEmpty())table.setRowSelectionInterval(0,0);
    }
    private Choice choice(int index){return choices.stream().filter(c->c.index()==index).findFirst().orElse(new Choice(index,"Missing ink — choose another"));}
    private void set(int row,int index){if(index<0)assigned.remove(targets.get(row).key());else assigned.put(targets.get(row).key(),index);}
    private void commit(){if(table.isEditing())table.getCellEditor().stopCellEditing();}
    Map<String,Integer> assignments(){commit();return Map.copyOf(assigned);}
    private void showPicture(Publication p){int row=table.getSelectedRow();picture.setIcon(null);if(row<0){picture.setText("Select a picture to preview it");return;}try{
        var e=targets.get(row).element();BufferedImage source=publishing.PublicationImport.decodeImage(p.openAsset(e.asset),1000);if(source==null)throw new IllegalArgumentException();
        double scale=Math.min(1,Math.min(190./source.getWidth(),250./source.getHeight()));picture.setIcon(new ImageIcon(source.getScaledInstance(Math.max(1,(int)(source.getWidth()*scale)),Math.max(1,(int)(source.getHeight()*scale)),Image.SCALE_SMOOTH)));picture.setText(null);
    }catch(Exception ex){picture.setText("Picture preview unavailable");}}
}
