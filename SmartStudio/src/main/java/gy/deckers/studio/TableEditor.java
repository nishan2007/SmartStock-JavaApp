package gy.deckers.studio;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.*;
import ui.design.DeckersSwing;
import ui.design.DeckersPalette;

/** Works on a detached table draft; Cancel never changes the publication. */
final class TableEditor extends JPanel {
    final StudioTables.Grid grid;
    final JTable table;
    private final AbstractTableModel model;
    private final JSpinner x,y,width,height,rowHeight,columnWidth,stroke;
    private final JComboBox<String> borders=new JComboBox<>(new String[]{"All","Outside","None"});
    private final JLabel message=new JLabel("Select cells to edit text, resize rows / columns, or merge and split.");
    private boolean updating;
    TableEditor(StudioTables.Grid grid){
        super(new BorderLayout(6,6));this.grid=grid;setBackground(DeckersPalette.background());
        model=new AbstractTableModel(){
            public int getRowCount(){return grid.rows();}public int getColumnCount(){return grid.columns();}
            public String getColumnName(int c){return "Column "+(c+1);}
            public Object getValueAt(int r,int c){var e=grid.cell(r,c);return e==null?"":e.sourceRow==r&&e.sourceColumn==c?e.text:"↖ merged";}
            public boolean isCellEditable(int r,int c){var e=grid.cell(r,c);return e!=null&&e.sourceRow==r&&e.sourceColumn==c;}
            public void setValueAt(Object value,int r,int c){grid.text(r,c,value==null?"":value.toString());fireTableCellUpdated(r,c);}
        };
        table=new JTable(model);table.setCellSelectionEnabled(true);table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);table.setRowHeight(27);table.getTableHeader().setReorderingAllowed(false);
        sizeColumns();
        JScrollPane scroll=new JScrollPane(table);scroll.setPreferredSize(new Dimension(720,280));add(scroll);
        JPanel controls=DeckersSwing.panel();controls.setLayout(new BoxLayout(controls,BoxLayout.Y_AXIS));
        JPanel counts=row();var rowCount=new JSpinner(new SpinnerNumberModel(grid.rows(),1,StudioTables.MAX,1));var columnCount=new JSpinner(new SpinnerNumberModel(grid.columns(),1,StudioTables.MAX,1));field(counts,"Rows",rowCount);field(counts,"Columns",columnCount);button(counts,"Set counts",()->{
            int rs=(int)value(rowCount),cs=(int)value(columnCount);if((long)rs*cs>StudioTables.MAX_CELLS)throw new IllegalArgumentException("Table limit: 2000 cells.");
            if((rs<grid.rows()||cs<grid.columns())&&!GraphicsEnvironment.isHeadless()&&JOptionPane.showConfirmDialog(this,"Remove the trailing rows / columns and their contents?","Table size",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            double w=grid.width(),h=grid.height();while(grid.rows()>rs)grid.delete(true,grid.rows()-1);while(grid.columns()>cs)grid.delete(false,grid.columns()-1);while(grid.rows()<rs)grid.insert(true,grid.rows());while(grid.columns()<cs)grid.insert(false,grid.columns());grid.resize(w,h);refresh(false);
        });controls.add(counts);
        model.addTableModelListener(e->{rowCount.setValue(grid.rows());columnCount.setValue(grid.columns());});
        JPanel structure=row();button(structure,"Row above",()->insert(true,false));button(structure,"Row below",()->insert(true,true));button(structure,"Delete row",()->remove(true));button(structure,"Column left",()->insert(false,false));button(structure,"Column right",()->insert(false,true));button(structure,"Delete column",()->remove(false));controls.add(structure);
        JPanel cells=row();button(cells,"Merge cells",()->{int[] rs=table.getSelectedRows(),cs=table.getSelectedColumns();requireSelection();grid.merge(rs[0],cs[0],rs[rs.length-1],cs[cs.length-1]);model.fireTableDataChanged();});button(cells,"Split cell",()->{requireSelection();grid.split(table.getSelectedRow(),table.getSelectedColumn());model.fireTableDataChanged();});controls.add(cells);
        x=number(grid.x,-1000000,1000000);y=number(grid.y,-1000000,1000000);width=number(grid.width(),.1,1000000);height=number(grid.height(),.1,1000000);rowHeight=number(grid.heights.get(0),.1,1000000);columnWidth=number(grid.widths.get(0),.1,1000000);stroke=number(grid.stroke,.01,10);
        JPanel geometry=row();field(geometry,"X (mm)",x);field(geometry,"Y (mm)",y);field(geometry,"Width (mm)",width);field(geometry,"Height (mm)",height);controls.add(geometry);
        JPanel dimensions=row();field(dimensions,"Row height (mm)",rowHeight);field(dimensions,"Column width (mm)",columnWidth);button(dimensions,"Size selected",()->{requireSelection();for(int r:table.getSelectedRows())grid.heights.set(r,value(rowHeight));for(int c:table.getSelectedColumns())grid.widths.set(c,value(columnWidth));syncSize();});button(dimensions,"Distribute rows",()->{double h=grid.height()/grid.rows();grid.heights.replaceAll(v->h);syncSize();});button(dimensions,"Distribute columns",()->{double w=grid.width()/grid.columns();grid.widths.replaceAll(v->w);syncSize();});controls.add(dimensions);
        JPanel appearance=row();borders.setSelectedItem(grid.border);field(appearance,"Borders",borders);field(appearance,"Line width (mm)",stroke);button(appearance,"Border colour…",()->{Color color=JColorChooser.showDialog(this,"Table border colour",Color.decode(grid.color));if(color!=null)grid.color=String.format("#%06x",color.getRGB()&0xffffff);});controls.add(appearance);
        table.getSelectionModel().addListSelectionListener(e->selection());table.getColumnModel().getSelectionModel().addListSelectionListener(e->selection());
        JScrollPane controlScroll=new JScrollPane(controls);controlScroll.setBorder(null);controlScroll.setPreferredSize(new Dimension(740,350));add(controlScroll,BorderLayout.NORTH);add(message,BorderLayout.SOUTH);
    }
    private JPanel row(){var p=DeckersSwing.panel();p.setLayout(new FlowLayout(FlowLayout.LEFT,5,3));return p;}
    private void field(JPanel p,String name,JComponent c){p.add(new JLabel(name));p.add(c);c.getAccessibleContext().setAccessibleName(name);}
    private JSpinner number(double v,double min,double max){var s=new JSpinner(new SpinnerNumberModel(v,min,max,.1));s.setPreferredSize(new Dimension(90,28));return s;}
    private double value(JSpinner spinner){try{spinner.commitEdit();}catch(java.text.ParseException e){throw new IllegalArgumentException("Enter a valid measurement.");}return ((Number)spinner.getValue()).doubleValue();}
    private void button(JPanel p,String label,Runnable action){p.add(StudioWorkspaceShell.button(label,()->{try{commit();action.run();message.setText("Table draft updated. Apply to keep changes; Ctrl+Z can undo after applying.");}catch(Exception e){message.setText(e.getMessage());}}));}
    private void commit(){if(table.isEditing()&&!table.getCellEditor().stopCellEditing())throw new IllegalArgumentException("Finish editing the cell.");if(width!=null){double w=value(width),h=value(height);if(Math.abs(w-grid.width())>.000001||Math.abs(h-grid.height())>.000001)grid.resize(w,h);}}
    private void requireSelection(){if(table.getSelectedRow()<0||table.getSelectedColumn()<0)throw new IllegalArgumentException("Select a cell first.");}
    private void selection(){if(updating)return;int r=table.getSelectedRow(),c=table.getSelectedColumn();if(r>=0&&r<grid.rows())rowHeight.setValue(grid.heights.get(r));if(c>=0&&c<grid.columns())columnWidth.setValue(grid.widths.get(c));}
    private void syncSize(){width.setValue(grid.width());height.setValue(grid.height());}
    private void insert(boolean row,boolean after){int at=row?table.getSelectedRow():table.getSelectedColumn();if(at<0)at=after?(row?grid.rows():grid.columns()):0;else if(after)at++;grid.insert(row,at);refresh(row);}
    private void remove(boolean row){requireSelection();grid.delete(row,row?table.getSelectedRow():table.getSelectedColumn());refresh(row);}
    private void sizeColumns(){for(int c=0;c<grid.columns();c++)table.getColumnModel().getColumn(c).setPreferredWidth(Math.max(90,Math.min(450,(int)(700*grid.widths.get(c)/grid.width()))));}
    private void refresh(boolean rows){updating=true;try{if(rows)model.fireTableDataChanged();else{model.fireTableStructureChanged();sizeColumns();}syncSize();}finally{updating=false;}}
    StudioTables.Grid finish(){commit();grid.x=value(x);grid.y=value(y);grid.stroke=value(stroke);grid.border=(String)borders.getSelectedItem();grid.elements();return grid;}
    static StudioTables.Grid choose(Component owner,StudioTables.Grid grid,boolean creating){
        var editor=new TableEditor(grid);var dialog=new JDialog(SwingUtilities.getWindowAncestor(owner),creating?"Insert table":"Edit table",Dialog.ModalityType.APPLICATION_MODAL);
        boolean[] applied={false};JPanel content=new JPanel(new BorderLayout(8,8));content.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));content.add(editor);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));var apply=StudioWorkspaceShell.button("Apply",()->{try{editor.finish();applied[0]=true;dialog.dispose();}catch(Exception ex){editor.message.setText(ex.getMessage());}});
        buttons.add(apply);buttons.add(StudioWorkspaceShell.button("Cancel",dialog::dispose));content.add(buttons,BorderLayout.SOUTH);dialog.setContentPane(content);dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.getRootPane().setDefaultButton(apply);dialog.getRootPane().registerKeyboardAction(e->dialog.dispose(),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.pack();Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();dialog.setSize(Math.min(dialog.getWidth(),usable.width-32),Math.min(dialog.getHeight(),usable.height-48));dialog.setLocationRelativeTo(owner);dialog.setVisible(true);return applied[0]?grid:null;
    }
}
