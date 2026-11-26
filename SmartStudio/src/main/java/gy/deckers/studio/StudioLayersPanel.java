package gy.deckers.studio;

import publishing.Publication;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;
import java.util.function.*;

/** Floating selection list: topmost artwork first, with persistent visibility. */
final class StudioLayersPanel extends JPanel {
    private final Supplier<List<Publication.Element>> elements;
    private final IntSupplier selection;
    private final IntConsumer select,toggle;
    private boolean updating;
    final JTable table;
    private final AbstractTableModel model=new AbstractTableModel(){
        public int getRowCount(){return elements.get().size();}
        public int getColumnCount(){return 3;}
        public String getColumnName(int c){return new String[]{"Visibility","Type","Name"}[c];}
        public Object getValueAt(int row,int c){var element=elements.get().get(index(row));return c==0?!element.hidden:c==1?element.type:name(element);}
    };
    private int index(int row){return elements.get().size()-1-row;}
    private static String name(Publication.Element e){String text=e.text==null?"":e.text.strip().replaceAll("\\s+"," ");return text.isBlank()?switch(e.type){case "IMAGE"->"Picture";case "RECT"->"Background";default->e.type;}:text;}

    StudioLayersPanel(Supplier<List<Publication.Element>> elements,IntSupplier selection,IntConsumer select,IntConsumer toggle,Consumer<StudioLayerOrder.Move> move,Runnable delete,Runnable close){
        super(new BorderLayout(0,8));this.elements=elements;this.selection=selection;this.select=select;this.toggle=toggle;
        setOpaque(false);setBorder(BorderFactory.createEmptyBorder(14,14,12,14));
        JPanel heading=new JPanel(new BorderLayout());heading.setOpaque(false);JLabel title=new JLabel("Layers");title.setFont(StudioVisuals.font(Font.BOLD,15));heading.add(title);heading.add(command("Close layers","×",close),BorderLayout.EAST);add(heading,BorderLayout.NORTH);
        MouseAdapter drag=new MouseAdapter(){Point start,origin;public void mousePressed(MouseEvent e){Window window=SwingUtilities.getWindowAncestor(StudioLayersPanel.this);if(window!=null){start=e.getLocationOnScreen();origin=window.getLocation();}}public void mouseDragged(MouseEvent e){Window window=SwingUtilities.getWindowAncestor(StudioLayersPanel.this);if(window!=null&&start!=null){Point now=e.getLocationOnScreen();window.setLocation(origin.x+now.x-start.x,origin.y+now.y-start.y);}}};
        heading.addMouseListener(drag);heading.addMouseMotionListener(drag);title.addMouseListener(drag);title.addMouseMotionListener(drag);
        table=new JTable(model){
            @Override protected void paintComponent(Graphics original){
                Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                int row=getSelectedRow();if(row>=0){g.setColor(new Color(0xEEE6F8));g.fillRoundRect(0,row*getRowHeight()+2,getWidth(),getRowHeight()-4,9,9);}
                g.dispose();super.paintComponent(original);
                g=(Graphics2D)original.create();g.setColor(new Color(0xEFEDF1));
                for(int r=0;r<getRowCount();r++)if(r!=row&&r+1!=row){int y=(r+1)*getRowHeight();g.drawLine(12,y,getWidth()-12,y);}
                g.dispose();
            }
        };table.setOpaque(false);table.setRowHeight(44);table.setTableHeader(null);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setShowGrid(false);table.setIntercellSpacing(new Dimension(0,0));table.setBackground(Color.WHITE);table.setSelectionBackground(new Color(0xEEE5FA));table.setSelectionForeground(StudioVisuals.INK);
        table.getColumnModel().getColumn(0).setMinWidth(36);table.getColumnModel().getColumn(0).setMaxWidth(36);table.getColumnModel().getColumn(1).setMinWidth(32);table.getColumnModel().getColumn(1).setMaxWidth(32);
        table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){
            public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int column){
                super.getTableCellRendererComponent(t,value,selected,focus,row,column);var element=elements.get().get(index(row));
                setOpaque(false);setFont(StudioVisuals.font(Font.PLAIN,13));setBorder(BorderFactory.createEmptyBorder(0,8,0,8));setForeground(element.hidden?new Color(0x96919A):new Color(0x514C56));setIcon(null);setText("");
                if(column==0){setIcon(StudioVisuals.layerIcon(element.hidden?"hidden":"visible",16));setToolTipText(element.hidden?"Show layer (Space)":"Hide layer (Space)");}
                else if(column==1){setIcon(StudioVisuals.layerIcon(element.type,19));setToolTipText(element.type);}
                else{setText(name(element));setToolTipText(name(element)+(element.hidden?" · Hidden":""));}
                return this;
            }
        });
        table.getSelectionModel().addListSelectionListener(e->{if(!updating&&!e.getValueIsAdjusting()&&table.getSelectedRow()>=0)select.accept(index(table.getSelectedRow()));});
        table.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){int row=table.rowAtPoint(e.getPoint());if(row>=0&&table.columnAtPoint(e.getPoint())==0&&SwingUtilities.isLeftMouseButton(e))toggle.accept(index(row));}});
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0),"toggleLayer");table.getActionMap().put("toggleLayer",new AbstractAction(){public void actionPerformed(ActionEvent e){if(table.getSelectedRow()>=0)toggle.accept(index(table.getSelectedRow()));}});
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE,0),"deleteLayer");table.getActionMap().put("deleteLayer",new AbstractAction(){public void actionPerformed(ActionEvent e){if(table.getSelectedRow()>=0)delete.run();}});
        JScrollPane scroll=new JScrollPane(table);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.setViewportBorder(null);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8,0));scroll.getVerticalScrollBar().setUI(new javax.swing.plaf.basic.BasicScrollBarUI(){
            protected void configureScrollBarColors(){thumbColor=new Color(0xCEC5D6);trackColor=new Color(0xFAF9FC);}
            protected JButton createDecreaseButton(int orientation){return zeroButton();}
            protected JButton createIncreaseButton(int orientation){return zeroButton();}
            private JButton zeroButton(){JButton button=new JButton();button.setPreferredSize(new Dimension(0,0));return button;}
        });scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);scroll.getVerticalScrollBar().setUnitIncrement(44);add(scroll);
        JPanel footer=new JPanel(new BorderLayout());footer.setOpaque(false);footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,new Color(0xEFEDF1)),BorderFactory.createEmptyBorder(4,0,0,0)));JPanel order=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));order.setOpaque(false);
        order.add(command("Move layer up",null,()->{if(table.getSelectedRow()>=0)move.accept(StudioLayerOrder.Move.FORWARD);}));order.add(command("Move layer down",null,()->{if(table.getSelectedRow()>=0)move.accept(StudioLayerOrder.Move.BACKWARD);}));footer.add(order);footer.add(command("Delete layer",null,()->{if(table.getSelectedRow()>=0)delete.run();}),BorderLayout.EAST);add(footer,BorderLayout.SOUTH);
        setPreferredSize(new Dimension(280,370));refresh();
    }
    private static JButton command(String name,String label,Runnable action){JButton b=new JButton();b.setIcon(name.equals("Delete layer")?StudioVisuals.layerIcon("delete",17):StudioVisuals.icon(name,16));b.setForeground(new Color(0x6E6974));b.setMargin(new Insets(0,0,0,0));b.setToolTipText(name);b.getAccessibleContext().setAccessibleName(name);b.setPreferredSize(new Dimension(40,40));b.setBackground(Color.WHITE);b.setContentAreaFilled(false);b.setOpaque(false);b.setFocusPainted(true);b.setBorder(name.equals("Delete layer")?BorderFactory.createMatteBorder(0,1,0,0,new Color(0xEFEDF1)):BorderFactory.createEmptyBorder());b.addActionListener(e->action.run());return b;}
    @Override protected void paintComponent(Graphics original){
        Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        for(int i=6;i>0;i--){g.setColor(new Color(40,30,50,4));g.fillRoundRect(6-i,6-i,getWidth()-12+2*i,getHeight()-12+2*i,16,16);}
        g.setPaint(new GradientPaint(0,0,Color.WHITE,0,getHeight(),new Color(0xFAF9FC)));g.fillRoundRect(5,5,getWidth()-11,getHeight()-11,14,14);
        g.setColor(new Color(0xE4DFE8));g.drawRoundRect(5,5,getWidth()-11,getHeight()-11,14,14);g.dispose();
    }
    void refresh(){updating=true;try{model.fireTableDataChanged();int selected=selection.getAsInt();if(selected>=0&&selected<elements.get().size()){int row=index(selected);table.setRowSelectionInterval(row,row);table.scrollRectToVisible(table.getCellRect(row,0,true));}else table.clearSelection();}finally{updating=false;}}
}
