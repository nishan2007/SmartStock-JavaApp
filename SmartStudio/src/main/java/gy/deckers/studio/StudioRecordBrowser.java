package gy.deckers.studio;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import static gy.deckers.studio.StudioVisuals.*;

/** Read-only view of a template's records. Opening a row uses its original record identity. */
final class StudioRecordBrowser<K> extends JPanel {
    record Row<K>(K id, Map<String,String> values) {}
    record Details(BufferedImage preview, Runnable load) {}
    @FunctionalInterface interface ReadDetails<K> { Details read(Row<K> row) throws Exception; }

    private static final Color LAVENDER=new Color(0xF4EDF8), HEADER=new Color(0xEFE5F4);
    private static final Color SELECTED=new Color(0xE9D8F3), MUTED=new Color(0x625967);
    private static final Set<String> DATE_KEYS=Set.of("dateOfBirth","issueDate","expiryDate");
    private static final DateTimeFormatter PRINTED_DATE=new DateTimeFormatterBuilder()
            .parseCaseInsensitive().appendPattern("d MMM uuuu").toFormatter(Locale.ENGLISH);

    private final Records<K> model;
    private final TableRowSorter<Records<K>> sorter;
    final JTable table;
    final JTextField search=new JTextField(){
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g);
            if(getText().isEmpty()){
                g.setFont(getFont());
                g.setColor(MUTED);
                FontMetrics metrics=g.getFontMetrics();
                g.drawString("Search any field",getInsets().left,(getHeight()-metrics.getHeight())/2+metrics.getAscent());
            }
        }
    };
    private final JLabel status=label("Loading records…",Font.PLAIN,17,MUTED);
    private final JLabel recordName=label("",Font.BOLD,32,PLUM);
    private final JLabel recordNumber=label("",Font.PLAIN,21,MUTED);
    private final JLabel notice=label("",Font.PLAIN,14,MUTED);
    private final BorderLayout listLayout=new BorderLayout(0,30);
    private final JPanel fields=new JPanel(new GridBagLayout());
    private final Preview preview=new Preview();
    final JButton open=new JButton("Load record");
    private final ReadDetails<K> readDetails;
    private SwingWorker<List<Row<K>>,Void> listWorker;
    private SwingWorker<Details,Void> detailWorker;
    private Row<K> inspected;
    private Details ready;
    private boolean loaded, closed;

    StudioRecordBrowser(String title, Map<String,String> columns, ReadDetails<K> readDetails, Runnable close){
        super(new BorderLayout(0,20));
        this.readDetails=readDetails;
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(14,6,14,6));

        JPanel heading=new JPanel(new BorderLayout(0,2));
        heading.setOpaque(false);
        heading.setBorder(BorderFactory.createEmptyBorder(0,14,0,14));
        heading.add(label(title,Font.BOLD,36,PLUM),BorderLayout.NORTH);
        heading.add(label("Saved records",Font.PLAIN,21,MUTED),BorderLayout.SOUTH);
        add(heading,BorderLayout.NORTH);

        model=new Records<>(columns);
        table=new JTable(model){
            @Override public String getToolTipText(MouseEvent event){
                int row=rowAtPoint(event.getPoint()), column=columnAtPoint(event.getPoint());
                return row>=0&&column>=0?plainTooltip(String.valueOf(getValueAt(row,column))):null;
            }
        };
        sorter=new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(51);
        table.setFont(font(Font.PLAIN,19));
        table.setForeground(INK);
        table.setBackground(SURFACE);
        table.setSelectionForeground(INK);
        table.setSelectionBackground(SELECTED);
        table.setGridColor(BORDER);
        table.setShowHorizontalLines(true);
        table.setFillsViewportHeight(true);
        table.getAccessibleContext().setAccessibleName("Template records");
        table.setDefaultRenderer(String.class,new DefaultTableCellRenderer(){
            {putClientProperty("html.disable",Boolean.TRUE);}
            @Override public Component getTableCellRendererComponent(JTable source,Object value,boolean selected,
                    boolean focused,int row,int column){
                super.getTableCellRendererComponent(source,value,selected,focused,row,column);
                if(!focused)setBorder(BorderFactory.createEmptyBorder(0,14,0,8));
                return this;
            }
        });
        var header=table.getTableHeader();
        header.setDefaultRenderer(new DefaultTableCellRenderer(){
            {putClientProperty("html.disable",Boolean.TRUE);}
            @Override public Component getTableCellRendererComponent(JTable source,Object value,boolean selected,
                    boolean focused,int row,int column){
                super.getTableCellRendererComponent(source,value,selected,focused,row,column);
                setFont(font(Font.BOLD,18));
                setBackground(HEADER);
                setForeground(INK);
                setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,1,BORDER),
                        BorderFactory.createEmptyBorder(0,14,0,8)));
                int modelColumn=table.convertColumnIndexToModel(column);
                var sortKeys=sorter.getSortKeys();
                setIcon(sortKeys.isEmpty()||sortKeys.get(0).getColumn()!=modelColumn?null:
                        icon(sortKeys.get(0).getSortOrder()==SortOrder.DESCENDING?"sort down":"sort up",12));
                setHorizontalTextPosition(SwingConstants.LEFT);
                setIconTextGap(8);
                setPreferredSize(new Dimension(0,48));
                return this;
            }
        });
        header.setReorderingAllowed(false);

        JPanel list=new JPanel(listLayout);
        list.setBackground(SURFACE);
        list.setBorder(BorderFactory.createEmptyBorder(21,12,19,12));
        JLabel searchLabel=label("Search records",Font.BOLD,20,INK);
        searchLabel.setLabelFor(search);
        search.setFont(font(Font.PLAIN,18));
        search.setForeground(INK);
        search.setBorder(BorderFactory.createEmptyBorder(0,2,0,8));
        search.setBackground(Color.WHITE);
        search.getAccessibleContext().setAccessibleName("Search records");
        JPanel input=new JPanel(new BorderLayout(8,0));
        input.setBackground(Color.WHITE);
        input.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0xB7A8C6),1,true),
                BorderFactory.createEmptyBorder(0,12,0,0)));
        JLabel magnifier=new JLabel(icon("search",16));
        magnifier.setForeground(PLUM);
        input.add(magnifier,BorderLayout.WEST);
        input.add(search);
        input.setPreferredSize(new Dimension(100,44));
        JPanel filterBar=new JPanel(new BorderLayout(18,0));
        filterBar.setOpaque(false);
        filterBar.setBorder(BorderFactory.createEmptyBorder(0,8,0,8));
        filterBar.add(searchLabel,BorderLayout.WEST);
        filterBar.add(input);
        list.add(filterBar,BorderLayout.NORTH);

        JScrollPane grid=new JScrollPane(table);
        grid.setColumnHeaderView(header);
        grid.setBorder(null);
        grid.getViewport().setBackground(SURFACE);
        grid.getHorizontalScrollBar().setUnitIncrement(24);
        grid.getAccessibleContext().setAccessibleName("Saved records table");
        JPanel tableFrame=new JPanel(new BorderLayout());
        tableFrame.setBorder(BorderFactory.createLineBorder(BORDER,1,true));
        tableFrame.add(grid);
        status.setOpaque(true);
        status.setBackground(new Color(0xF8F4F8));
        status.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,BORDER),
                BorderFactory.createEmptyBorder(9,12,9,12)));
        tableFrame.add(status,BorderLayout.SOUTH);
        list.add(tableFrame);

        JPanel inspector=new JPanel(new BorderLayout(0,12));
        inspector.setBackground(LAVENDER);
        inspector.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,1,0,0,BORDER),
                BorderFactory.createEmptyBorder(12,21,26,20)));
        JPanel identity=new JPanel(new BorderLayout(0,3));
        identity.setOpaque(false);
        identity.add(recordName,BorderLayout.NORTH);
        identity.add(recordNumber,BorderLayout.SOUTH);
        inspector.add(identity,BorderLayout.NORTH);

        JPanel details=new JPanel(new BorderLayout(0,10));
        details.setOpaque(false);
        details.add(preview,BorderLayout.NORTH);
        fields.setOpaque(false);
        details.add(fields,BorderLayout.CENTER);
        JPanel body=new DetailBody();
        body.setOpaque(false);
        body.add(details,BorderLayout.NORTH);
        JScrollPane detailScroll=new JScrollPane(body,JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        detailScroll.setBorder(null);
        detailScroll.getViewport().setBackground(LAVENDER);
        detailScroll.getVerticalScrollBar().setUnitIncrement(20);
        inspector.add(detailScroll);
        JPanel footer=new JPanel(new BorderLayout(0,5));
        footer.setOpaque(false);
        footer.add(notice,BorderLayout.NORTH);
        notice.setVisible(false);
        primaryButton(open);
        open.setIcon(null);
        open.setDisabledIcon(null);
        open.setFont(font(Font.BOLD,20));
        open.setPreferredSize(new Dimension(100,52));
        open.setEnabled(false);
        footer.add(open);
        inspector.add(footer,BorderLayout.SOUTH);

        // A fixed proportion keeps the details visible while the wide table scrolls independently.
        JPanel panes=new JPanel(new GridBagLayout());
        panes.setBorder(BorderFactory.createLineBorder(BORDER,1,true));
        list.setPreferredSize(new Dimension(0,0));
        inspector.setPreferredSize(new Dimension(0,0));
        list.setMinimumSize(new Dimension(0,0));
        inspector.setMinimumSize(new Dimension(0,0));
        var constraints=new GridBagConstraints();
        constraints.gridy=0;
        constraints.weighty=1;
        constraints.fill=GridBagConstraints.BOTH;
        constraints.weightx=.65;
        constraints.gridx=0;
        panes.add(list,constraints);
        constraints.weightx=.35;
        constraints.gridx=1;
        panes.add(inspector,constraints);
        add(panes);
        updateSelection();

        open.addActionListener(e->{
            if(ready!=null)ready.load().run();
            else if(inspected!=null)readSelection();
        });
        table.getSelectionModel().addListSelectionListener(e->{if(!e.getValueIsAdjusting())updateSelection();});
        table.addMouseListener(new MouseAdapter(){
            @Override public void mouseClicked(MouseEvent e){
                if(e.getClickCount()==2&&SwingUtilities.isLeftMouseButton(e)&&table.rowAtPoint(e.getPoint())>=0)open.doClick();
            }
        });
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"loadRecord");
        table.getActionMap().put("loadRecord",new AbstractAction(){
            public void actionPerformed(ActionEvent e){open.doClick();}
        });
        registerKeyboardAction(e->close.run(),KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        search.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){filter();}
            public void removeUpdate(DocumentEvent e){filter();}
            public void changedUpdate(DocumentEvent e){filter();}
        });
    }

    void start(Callable<List<Row<K>>> fetch){
        listWorker=new SwingWorker<>(){
            protected List<Row<K>> doInBackground()throws Exception{return fetch.call();}
            protected void done(){
                if(closed||isCancelled())return;
                try{load(get());}
                catch(ExecutionException ex){status.setText(message(ex,"Could not load records."));}
                catch(InterruptedException ex){Thread.currentThread().interrupt();}
            }
        };
        listWorker.execute();
        search.requestFocusInWindow();
    }

    void close(){
        closed=true;
        if(listWorker!=null)listWorker.cancel(true);
        if(detailWorker!=null)detailWorker.cancel(true);
    }

    @Override public void doLayout(){
        boolean compact=getHeight()<560;
        int rowHeight=compact?34:51;
        if(table.getRowHeight()!=rowHeight)table.setRowHeight(rowHeight);
        listLayout.setVgap(compact?14:30);
        int previewHeight=compact?112:219;
        if(preview.getPreferredSize().height!=previewHeight)preview.setPreferredSize(new Dimension(1,previewHeight));
        super.doLayout();
    }

    @Override public void removeNotify(){
        close();
        super.removeNotify();
    }

    void load(List<Row<K>> rows){
        model.load(rows);
        for(int column=0;column<model.getColumnCount();column++){
            String key=model.keys.get(column);
            Comparator<String> comparator=DATE_KEYS.contains(key)?StudioRecordBrowser::compareDates:
                    key.equals("permitNumber")?StudioRecordBrowser::comparePermitNumbers:String.CASE_INSENSITIVE_ORDER;
            sorter.setComparator(column,comparator);
            int width=switch(key){
                case "permitNumber"->209;
                case "name"->201;
                case "village"->164;
                case "issueDate","expiryDate","placeOfIssue","_savedAt"->174;
                case "sex"->66;
                default->145;
            };
            int headingWidth=table.getFontMetrics(font(Font.BOLD,18)).stringWidth(model.getColumnName(column))+44;
            table.getColumnModel().getColumn(column).setPreferredWidth(Math.max(width,headingWidth));
        }
        int permitColumn=model.keys.indexOf("permitNumber");
        if(permitColumn>=0)sorter.setSortKeys(List.of(new RowSorter.SortKey(permitColumn,SortOrder.DESCENDING)));
        loaded=true;
        filter();
        if(table.getRowCount()>0)table.setRowSelectionInterval(0,0);
    }

    private Row<K> selected(){
        int row=table.getSelectedRow();
        return row<0?null:model.rows.get(table.convertRowIndexToModel(row));
    }

    private void updateSelection(){
        Row<K> row=selected();
        if(closed||(row==inspected&&row!=null))return;
        if(detailWorker!=null)detailWorker.cancel(true);
        inspected=row;
        ready=null;
        open.setText("Load record");
        open.setEnabled(false);
        notice.setVisible(false);
        preview.image=null;
        preview.setVisible(row!=null);
        fields.removeAll();
        if(row==null){
            recordName.setText("Select a record");
            recordName.setToolTipText(null);
            recordNumber.setText("");
        }else{
            String nameKey=model.nameKey();
            String name=row.values().getOrDefault(nameKey,"");
            String number=row.values().getOrDefault("permitNumber","");
            recordName.setText(name.isBlank()?"Record "+(table.convertRowIndexToModel(table.getSelectedRow())+1):name);
            recordName.setToolTipText(plainTooltip(recordName.getText()));
            recordNumber.setText(number);
            var detailKeys=new LinkedHashSet<>(model.keys);
            for(String key:List.of("village","issueDate","expiryDate","_savedAt"))
                if(detailKeys.remove(key))detailKeys.add(key);
            detailKeys.remove(nameKey);
            detailKeys.remove("permitNumber");
            for(String key:detailKeys)addDetail(model.columns.get(key),row.values().getOrDefault(key,""));
            readSelection();
        }
        preview.repaint();
        fields.revalidate();
        fields.repaint();
    }

    private void addDetail(String key,String value){
        int row=fields.getComponentCount()/2;
        var c=new GridBagConstraints();
        c.gridy=row;
        c.gridx=0;
        c.weightx=.46;
        c.fill=GridBagConstraints.HORIZONTAL;
        JLabel label=label(key,Font.PLAIN,18,PLUM);
        label.setToolTipText(plainTooltip(key));
        label.setBorder(detailBorder(10));
        fields.add(label,c);
        c.gridx=1;
        c.weightx=.54;
        JLabel text=label(value,Font.PLAIN,18,INK);
        text.setToolTipText(plainTooltip(value));
        text.setBorder(detailBorder(0));
        fields.add(text,c);
    }

    private static javax.swing.border.Border detailBorder(int padding){
        return BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,BORDER),
                BorderFactory.createEmptyBorder(7,2,7,padding));
    }

    private void readSelection(){
        Row<K> row=inspected;
        open.setText("Load record");
        open.setEnabled(false);
        notice.setVisible(false);
        preview.saying="Loading preview…";
        preview.repaint();
        detailWorker=new SwingWorker<>(){
            protected Details doInBackground()throws Exception{return readDetails.read(row);}
            protected void done(){
                if(closed||isCancelled()||this!=detailWorker||inspected!=row)return;
                try{
                    ready=get();
                    preview.image=ready.preview();
                    preview.saying="Preview unavailable";
                }catch(ExecutionException ex){
                    preview.saying="Preview unavailable";
                    notice.setText(message(ex,"Could not load the record."));
                    notice.setToolTipText(plainTooltip(notice.getText()));
                    notice.setVisible(true);
                    open.setText("Retry");
                }catch(InterruptedException ex){Thread.currentThread().interrupt();return;}
                preview.repaint();
                open.setEnabled(true);
            }
        };
        detailWorker.execute();
    }

    private void filter(){
        if(!loaded)return;
        String query=search.getText().strip().toLowerCase(Locale.ROOT);
        String[] terms=query.isEmpty()?new String[0]:query.split("\\s+");
        sorter.setRowFilter(terms.length==0?null:new RowFilter<>(){
            @Override public boolean include(Entry<? extends Records<K>,? extends Integer> entry){
                String searchable=model.searchable.get(entry.getIdentifier());
                return Arrays.stream(terms).allMatch(searchable::contains);
            }
        });
        int visible=table.getRowCount(),total=model.getRowCount();
        status.setText(visible==total?visible+(visible==1?" record":" records"):visible+" of "+total+" records");
        updateSelection();
    }

    private static String message(ExecutionException failure,String fallback){
        return Objects.toString(failure.getCause().getMessage(),fallback);
    }

    private static JLabel label(String text,int style,int size,Color color){
        JLabel label=new JLabel();
        label.putClientProperty("html.disable",Boolean.TRUE);
        label.setText(text);
        label.setFont(font(style,size));
        label.setForeground(color);
        return label;
    }

    private static String plainTooltip(String text){
        return javax.swing.plaf.basic.BasicHTML.isHTMLString(text)?"\u200B"+text:text;
    }

    private static int compareDates(String a,String b){
        LocalDate first=date(a),second=date(b);
        if(first==null&&second==null)return String.CASE_INSENSITIVE_ORDER.compare(a,b);
        return Comparator.nullsLast(Comparator.<LocalDate>naturalOrder()).compare(first,second);
    }

    private static int comparePermitNumbers(String a,String b){
        int order=Long.compare(permitSequence(a),permitSequence(b));
        return order==0?String.CASE_INSENSITIVE_ORDER.compare(a,b):order;
    }

    private static long permitSequence(String value){
        if(!value.startsWith("OSV-"))return Long.MIN_VALUE;
        try{return Long.parseLong(value.substring(4));}
        catch(NumberFormatException ex){return Long.MIN_VALUE;}
    }

    private static LocalDate date(String value){
        try{return LocalDate.parse(value);}
        catch(DateTimeParseException ex){
            try{return LocalDate.parse(value,PRINTED_DATE);}
            catch(DateTimeParseException ignored){return null;}
        }
    }

    private static final class DetailBody extends JPanel implements Scrollable {
        DetailBody(){super(new BorderLayout());}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 20;}
        public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(20,visible.height-20);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }

    private static final class Preview extends JComponent {
        private BufferedImage image;
        private String saying="Loading preview…";
        Preview(){
            setPreferredSize(new Dimension(1,219));
            getAccessibleContext().setAccessibleName("Record preview");
        }
        @Override public javax.accessibility.AccessibleContext getAccessibleContext(){
            if(accessibleContext==null)accessibleContext=new AccessibleJComponent(){};
            return accessibleContext;
        }
        @Override protected void paintComponent(Graphics original){
            Graphics2D g=(Graphics2D)original.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            int width=Math.min(400,Math.max(1,getWidth()-30)),height=getHeight()-10;
            int x=(getWidth()-width)/2,y=5;
            g.setColor(BACKGROUND);
            g.fillRoundRect(x,y,width,height,14,14);
            if(image==null){
                g.setFont(font(Font.PLAIN,14));
                g.setColor(MUTED);
                g.drawString(saying,Math.max(x+4,(getWidth()-g.getFontMetrics().stringWidth(saying))/2),getHeight()/2+5);
            }else{
                double scale=Math.min((width-8)/(double)image.getWidth(),(height-8)/(double)image.getHeight());
                int w=Math.max(1,(int)(image.getWidth()*scale)),h=Math.max(1,(int)(image.getHeight()*scale));
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(image,(getWidth()-w)/2,(getHeight()-h)/2,w,h,null);
            }
            g.setColor(BORDER);
            g.drawRoundRect(x,y,width-1,height-1,14,14);
            g.dispose();
        }
    }

    private static final class Records<K> extends AbstractTableModel {
        private final Map<String,String> columns;
        private List<String> keys,searchable=List.of();
        private List<Row<K>> rows=List.of();
        Records(Map<String,String> columns){this.columns=new LinkedHashMap<>(columns);keys=List.copyOf(columns.keySet());}
        void load(List<Row<K>> rows){
            this.rows=List.copyOf(rows);
            for(var row:rows)for(String key:row.values().keySet())columns.putIfAbsent(key,key);
            keys=List.copyOf(columns.keySet());
            searchable=rows.stream().map(row->String.join("\n",row.values().values()).toLowerCase(Locale.ROOT)).toList();
            fireTableStructureChanged();
        }
        String nameKey(){
            if(columns.containsKey("name"))return "name";
            for(String key:keys)if("Name".equalsIgnoreCase(columns.get(key)))return key;
            return "";
        }
        public int getRowCount(){return rows.size();}
        public int getColumnCount(){return keys.size();}
        public String getColumnName(int column){return columns.get(keys.get(column));}
        public Class<?> getColumnClass(int column){return String.class;}
        public Object getValueAt(int row,int column){return rows.get(row).values().getOrDefault(keys.get(column),"");}
    }
}
