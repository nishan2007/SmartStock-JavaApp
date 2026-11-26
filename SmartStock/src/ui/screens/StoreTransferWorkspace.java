package ui.screens;

import services.LanApiClient;
import ui.design.DeckersPalette;
import ui.helpers.UiDebouncer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import static ui.screens.TransferDeskVisuals.*;

/** Option-one transfer desk, shared by the real window and isolated native UI verification. */
final class StoreTransferWorkspace extends JPanel {
    interface Handler {
        boolean leave();
        void select(Transfer row);
        void newTransfer();
        void refresh();
        void search(String query);
        void submitSearch(String query);
        void dismissSearch();
        void saveDraft();
        void openDraft();
        void deleteDraft();
        void submit();
        void cancel();
        void discard();
        void back();
    }
    record Transfer(long id,boolean incoming,String store,int destinationId,long created,String sender,
                    String note,int itemCount,int units,String status) {
        boolean pending(){return "PENDING".equalsIgnoreCase(status);}
        String badge(){return pending()?"Pending":"Awaiting store confirmation";}
    }
    record Destination(int id,String name){@Override public String toString(){return name;}}
    private Handler handler;
    final JPanel brandHeader;
    final TransferDeskVisuals.SearchField queueSearch=new SearchField("Search transfer #, store or note…"),productSearch=new SearchField("Search by product name or SKU…");
    final DefaultListModel<Transfer> queueModel=new DefaultListModel<>();
    final JList<Transfer> queue=new JList<>(queueModel){@Override public Color getBackground(){return DeckersPalette.surface();}@Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);if(getModel().getSize()==0){graphics.setColor(DeckersPalette.muted());graphics.setFont(font(Font.PLAIN,14));graphics.drawString(queueSearch.getText().isBlank()?"No transfers in this queue.":"No matching transfers.",14,32);}}};
    final JButton create=button("New transfer","plus",false),incoming=button("Incoming (0)",null,false),outgoing=button("Outgoing (0)",null,true);
    final JButton more=button("","more",false),add=button("Add item","plus",false),primary=button("Send transfer","check",true),cancel=danger("Cancel transfer"),discard=button("Clear draft","close",false),back=button("Back","back",false);
    final JButton saveDraft=button("Save draft",null,false),openDraft=button("Open",null,false),deleteDraft=button("Delete",null,false);
    final JComboBox<TransferDraftStore.Draft> savedDrafts=new JComboBox<>();
    final JComboBox<Destination> destination=new JComboBox<>();
    final JTextField source=new JTextField();
    final JSpinner quantity=new JSpinner(new SpinnerNumberModel(1,1,1_000_000,1));
    final JTextArea note=new JTextArea(3,30);
    final JLabel title=label("New transfer",Font.BOLD,29),subtitle=label("Choose a destination and add items to send.",Font.PLAIN,16,DeckersPalette::muted);
    final SalesTaskVisuals.Badge badge=new SalesTaskVisuals.Badge("Draft",DeckersPalette.ORANGE),mode=new SalesTaskVisuals.Badge("New transfer",new Color(101,122,150));
    final JLabel itemHeading=label("Transfer items (0)",Font.BOLD,18),totals=label("0 items • 0 units",Font.BOLD,20);
    final JLabel infoTitle=label("Stock is added only after the receiving store verifies and receives this transfer.",Font.BOLD,16,()->DeckersPalette.dark()?new Color(145,186,255):new Color(32,83,165));
    final JLabel infoText=label("Sending deducts source stock. Receiving adds stock to the destination store.",Font.PLAIN,15,DeckersPalette::muted);
    final JTextArea stockHint=new JTextArea(){@Override public Color getForeground(){return DeckersPalette.muted();}};
    final JLabel cancelHint=label("<html>Stock returns after the receiving store<br>confirms cancellation.</html>",Font.PLAIN,12,DeckersPalette::muted);
    final JLabel localStatus=label("Connecting to local server…",Font.PLAIN,13,DeckersPalette::muted),syncStatus=label("Store sync status not checked",Font.PLAIN,13,DeckersPalette::muted),message=new JLabel(""){ {setFont(font(Font.PLAIN,14));putClientProperty("SmartStock.preserveForeground",true);setVisible(false);} @Override public Color getForeground(){return DeckersPalette.muted();} @Override public void setText(String text){super.setText(text);setVisible(text!=null&&!text.isBlank());if(getParent()!=null)getParent().revalidate();}};
    final DefaultTableModel items=new DefaultTableModel(new String[]{"Product ID","SKU","Item","Available at store","Transfer qty","Actions"},0){
        @Override public boolean isCellEditable(int r,int c){return editable()&&(c==4||c==5);}
        @Override public Class<?> getColumnClass(int c){return c==0||c==4?Integer.class:Object.class;}
    };
    final JTable table=new JTable(items){@Override public void doLayout(){int mode=getParent()!=null&&getParent().getWidth()<780?AUTO_RESIZE_OFF:AUTO_RESIZE_ALL_COLUMNS;if(getAutoResizeMode()!=mode)setAutoResizeMode(mode);super.doLayout();}@Override public Color getBackground(){return DeckersPalette.surface();}@Override public Color getForeground(){return DeckersPalette.text();}@Override public Color getGridColor(){return DeckersPalette.border();}};
    final DefaultListModel<LanApiClient.TransferProduct> productModel=new DefaultListModel<>();
    final JList<LanApiClient.TransferProduct> products=new JList<>(productModel);
    // Inline results deliberately avoid JPopupMenu input grabs across SmartStock windows.
    final JPanel productPopup=panel(new BorderLayout(),true);
    private JPanel inventoryFinder;
    private final JPanel rail=panel(new BorderLayout(0,16),false),main=panel(new BorderLayout(),true),searchRow=clear(new BorderLayout(26,0));
    private final JPanel footer=new Footer(),sourceCard=card(new GridBagLayout()),editor=card(new BorderLayout(0,10));
    private JPanel footerLeft,footerRight;
    private final Lifecycle lifecycle=new Lifecycle();
    private final Body body=new Body();
    private final JLabel queueHint=label("Select Incoming to verify and receive.",Font.PLAIN,12,DeckersPalette::muted);
    private final JScrollPane tableScroll=new JScrollPane(table);
    private final List<Transfer> transfers=new ArrayList<>();
    final String store;final ZoneId zone;
    private Transfer selected;
    private boolean searchPending;
    private boolean incomingMode,loading,dirty,busy,detailReady=true,selecting;

    StoreTransferWorkspace(String company,Icon logo,String store,String staff,ZoneId zone,LocalDateTime now){
        super(new BorderLayout());this.store=store;this.zone=zone;
        brandHeader=SalesTasks.createBrandHeader(company,logo,store,staff,now.toLocalDate());
        brandHeader.setPreferredSize(new Dimension(1000,84));replaceSubtitle(brandHeader,now);mode.setFont(font(Font.BOLD,12));add(brandHeader,BorderLayout.NORTH);
        setFont(font(Font.PLAIN,14));setBackground(DeckersPalette.background());
        rail.setBorder(BorderFactory.createCompoundBorder(edge(0,0,0,1),new EmptyBorder(17,20,14,14)));
        rail.setPreferredSize(new Dimension(375,600));
        JPanel tools=clear(new BorderLayout(0,10));create.setPreferredSize(new Dimension(300,48));tools.add(create,BorderLayout.NORTH);
        JPanel finding=clear(new BorderLayout(8,10));queueSearch.setPreferredSize(new Dimension(250,40));more.setPreferredSize(new Dimension(40,40));more.setBorder(new EmptyBorder(8,8,8,8));more.setToolTipText("Refresh transfers");
        JPanel find=clear(new BorderLayout(8,0));find.add(queueSearch);find.add(more,BorderLayout.EAST);finding.add(find,BorderLayout.NORTH);
        incoming.putClientProperty("segment",true);outgoing.putClientProperty("segment",true);
        for(JButton b:new JButton[]{back,discard,more})b.putClientProperty("neutral",true);
        back.setIcon(SalesTaskVisuals.icon("back",DeckersPalette.muted(),18));discard.setIcon(SalesTaskVisuals.icon("close",DeckersPalette.muted(),18));
        JPanel segments=clear(new GridLayout(1,2,2,0));segments.add(incoming);segments.add(outgoing);incoming.setBorder(new EmptyBorder(10,6,10,6));outgoing.setBorder(new EmptyBorder(10,6,10,6));
        finding.add(segments,BorderLayout.CENTER);queueHint.setIcon(SalesTaskVisuals.icon("info",new Color(40,100,190),16));finding.add(queueHint,BorderLayout.SOUTH);tools.add(finding,BorderLayout.CENTER);
        JPanel drafts=clear(new BorderLayout(0,6));drafts.add(label("Saved drafts · this computer",Font.BOLD,13),BorderLayout.NORTH);
        savedDrafts.setPrototypeDisplayValue(new TransferDraftStore.Draft("00000000-0000-0000-0000-000000000000",0,"Choose saved draft","",List.of()));
        drafts.add(savedDrafts);JPanel draftActions=clear(new FlowLayout(FlowLayout.LEFT,6,0));draftActions.add(openDraft);draftActions.add(deleteDraft);drafts.add(draftActions,BorderLayout.SOUTH);tools.add(drafts,BorderLayout.SOUTH);rail.add(tools,BorderLayout.NORTH);
        saveDraft.addActionListener(e->handler.saveDraft());openDraft.addActionListener(e->handler.openDraft());deleteDraft.addActionListener(e->handler.deleteDraft());
        queue.getAccessibleContext().setAccessibleName("Pending store transfers");
        queue.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);queue.setCellRenderer(new QueueRenderer());queue.setFixedCellHeight(182);queue.setBackground(DeckersPalette.surface());
        JScrollPane queueScroll=new JScrollPane(queue);queueScroll.setBorder(BorderFactory.createEmptyBorder());queueScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);rail.add(queueScroll);
        JPanel center=panel(new BorderLayout(),true);center.add(rail,BorderLayout.WEST);center.add(main);add(center);
        buildEditor();JScrollPane workScroll=new JScrollPane(body);workScroll.setBorder(BorderFactory.createEmptyBorder());workScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);workScroll.getViewport().setBackground(DeckersPalette.surface());workScroll.getVerticalScrollBar().setUnitIncrement(24);main.add(workScroll);
        buildFooter();JPanel actionArea=panel(new BorderLayout(),true);message.setBorder(new EmptyBorder(6,18,6,18));actionArea.add(message,BorderLayout.NORTH);actionArea.add(footer);main.add(actionArea,BorderLayout.SOUTH);
        JPanel status=panel(new FlowLayout(FlowLayout.LEFT,20,10),false);status.setBorder(edge(1,0,0,0));status.add(localStatus);status.add(syncStatus);JLabel syncHint=label("Transfers sync automatically between stores.",Font.PLAIN,12,DeckersPalette::muted);syncHint.setIcon(SalesTaskVisuals.icon("info",DeckersPalette.muted(),14));status.add(syncHint);status.setPreferredSize(new Dimension(1200,44));add(status,BorderLayout.SOUTH);
        create.addActionListener(e->{if(canLeave())handler.newTransfer();});incoming.addActionListener(e->switchQueue(true));outgoing.addActionListener(e->switchQueue(false));
        more.addActionListener(e->{if(!busy&&canLeave())handler.refresh();});primary.addActionListener(e->{if(!busy)handler.submit();});cancel.addActionListener(e->{if(!busy)handler.cancel();});discard.addActionListener(e->{if(!busy)handler.discard();});back.addActionListener(e->handler.back());
        queue.addListSelectionListener(e->{if(e.getValueIsAdjusting()||selecting||busy)return;Transfer row=queue.getSelectedValue();if(row==null||Objects.equals(row,selected))return;if(!canLeave()){selectInQueue();return;}handler.select(row);});
        listen(queueSearch,()->{if(!busy)renderQueue();});
        listen(productSearch,()->{searchPending=!productSearch.getText().isBlank();if(handler!=null)handler.dismissSearch();productModel.clear();productPopup.setVisible(false);});
        UiDebouncer.bind(productSearch,220,()->{if(searchPending&&handler!=null&&editable())handler.search(productSearch.getText().trim());});
        productSearch.addActionListener(e->{if(editable()){if(products.getSelectedValue()!=null)addScannedProduct();else handler.submitSearch(productSearch.getText().trim());}});
        add.addActionListener(e->addProduct());
        destination.addActionListener(e->{if(!loading){dirty=true;updateSummary();}});
        listen(note,()->{if(!loading)dirty=true;});items.addTableModelListener(e->{if(!loading){dirty=true;updateSummary();}});
        productSearch.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,0),"products");productSearch.getActionMap().put("products",new AbstractAction(){public void actionPerformed(ActionEvent e){if(!productModel.isEmpty()){showProducts();products.requestFocusInWindow();if(products.getSelectedIndex()<0)products.setSelectedIndex(0);}}});
        products.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)addProduct();}});
        products.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"add");products.getActionMap().put("add",new AbstractAction(){public void actionPerformed(ActionEvent e){addProduct();}});
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_S,KeyEvent.CTRL_DOWN_MASK),"save");getActionMap().put("save",new AbstractAction(){public void actionPerformed(ActionEvent e){if(primary.isEnabled())primary.doClick();}});
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE,0),"remove-line");table.getActionMap().put("remove-line",new AbstractAction(){public void actionPerformed(ActionEvent e){removeRow(table.getSelectedRow());}});
        newDraft();
    }
    void handler(Handler value){handler=value;}
    private static void replaceSubtitle(Container c,LocalDateTime now){for(Component child:c.getComponents()){if(child instanceof JLabel l){if("Sales Tasks".equals(l.getText()))l.setText("Transfer Desk");else if(l.getText()!=null&&l.getText().contains(now.format(DateTimeFormatter.ofPattern("d MMM yyyy"))))l.setText(l.getText().replace("</html>","<br>"+now.format(DateTimeFormatter.ofPattern("hh:mm a"))+"</html>"));}if(child instanceof Container nested)replaceSubtitle(nested,now);}}
    private void buildEditor(){
        body.setBorder(new EmptyBorder(24,18,14,20));JPanel heading=new JPanel(new BorderLayout(12,0)){ {setOpaque(false);} @Override public void doLayout(){lifecycle.setPreferredSize(new Dimension(getWidth()>1000?550:getWidth()>800?420:285,92));title.setFont(font(Font.BOLD,getWidth()>800?29:23));mode.setVisible(getWidth()>700);super.doLayout();}};heading.setPreferredSize(new Dimension(900,86));
        JPanel titles=clear(null);titles.setLayout(new BoxLayout(titles,BoxLayout.Y_AXIS));JPanel titleLine=clear(new FlowLayout(FlowLayout.LEFT,0,0));titleLine.add(title);titleLine.add(Box.createHorizontalStrut(18));titleLine.add(badge);titleLine.add(Box.createHorizontalStrut(14));titleLine.add(mode);titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);titleLine.setMaximumSize(new Dimension(Integer.MAX_VALUE,36));subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);titles.add(titleLine);titles.add(Box.createVerticalStrut(10));titles.add(subtitle);heading.add(titles);heading.add(lifecycle,BorderLayout.EAST);
        JPanel top=clear(new BorderLayout(0,16));top.add(heading,BorderLayout.NORTH);
        JPanel bands=clear(new BorderLayout(0,8));sourceCard.setPreferredSize(new Dimension(900,102));source.setEditable(false);source.setText(store);source.setFont(font(Font.PLAIN,16));source.setBorder(new EmptyBorder(8,10,8,10));
        destination.setBorder(edge(1,1,1,1));destination.setFont(font(Font.PLAIN,16));destination.setRenderer(new DefaultListCellRenderer(){@Override public Color getForeground(){return destination.isEnabled()?DeckersPalette.text():DeckersPalette.muted();}});GridBagConstraints g=new GridBagConstraints();g.fill=GridBagConstraints.HORIZONTAL;g.weightx=1;g.insets=new Insets(0,10,0,10);g.gridx=0;g.gridy=0;sourceCard.add(field("From (source store)",source,"store"),g);g.gridx=1;g.weightx=0;sourceCard.add(new JLabel(SalesTaskVisuals.icon("arrow-right",DeckersPalette.muted(),26)),g);g.gridx=2;g.weightx=1;sourceCard.add(field("To (destination store)",destination,"store"),g);bands.add(sourceCard,BorderLayout.NORTH);
        JPanel info=new JPanel(new BorderLayout(16,5)){ {putClientProperty("SmartStock.preserveBackground",true);} @Override public Color getBackground(){return DeckersPalette.blend(DeckersPalette.surface(),new Color(42,118,236),.12);} };info.setBorder(new EmptyBorder(10,16,10,16));info.setPreferredSize(new Dimension(900,66));info.add(new JLabel(SalesTaskVisuals.icon("info",new Color(40,100,190),28)),BorderLayout.WEST);JPanel copy=clear(new GridLayout(2,1,0,4));copy.add(infoTitle);copy.add(infoText);info.add(copy);bands.add(info);top.add(bands);body.add(top,BorderLayout.NORTH);
        JPanel finder=clear(new BorderLayout(0,12));inventoryFinder=finder;JPanel finderHeading=clear(new BorderLayout());finderHeading.add(label("Add item from inventory",Font.BOLD,17));JLabel quantityLabel=label("Quantity",Font.PLAIN,12);quantityLabel.setPreferredSize(new Dimension(290,20));finderHeading.add(quantityLabel,BorderLayout.EAST);finder.add(finderHeading,BorderLayout.NORTH);
        productSearch.setPreferredSize(new Dimension(400,46));searchRow.add(productSearch);JPanel addTools=clear(new BorderLayout(18,0));JPanel qty=clear(new BorderLayout(0,2));quantity.setFont(font(Font.PLAIN,16));quantity.setPreferredSize(new Dimension(112,40));qty.add(quantity);addTools.add(qty);add.setPreferredSize(new Dimension(160,44));JPanel addHolder=clear(new BorderLayout());addHolder.setBorder(new EmptyBorder(0,0,0,0));addHolder.add(add);addTools.add(addHolder,BorderLayout.EAST);searchRow.add(addTools,BorderLayout.EAST);finder.add(searchRow);
        JPanel itemBody=clear(new BorderLayout(0,8));itemBody.add(itemHeading,BorderLayout.NORTH);configureTable();tableScroll.setColumnHeaderView(table.getTableHeader());tableScroll.setBorder(edge(1,1,1,1));tableScroll.setPreferredSize(new Dimension(900,194));itemBody.add(tableScroll);
        JPanel editorTop=clear(new BorderLayout(0,18));editorTop.add(finder,BorderLayout.NORTH);editorTop.add(itemBody);editor.add(editorTop);
        JPanel lower=new JPanel(new GridBagLayout()){ {setOpaque(false);} @Override public Dimension getPreferredSize(){Dimension d=super.getPreferredSize();d.height=getWidth()>0&&getWidth()<800?154:114;return d;}};GridBagConstraints n=new GridBagConstraints();n.gridy=0;n.gridx=0;n.weighty=1;n.weightx=.64;n.fill=GridBagConstraints.BOTH;n.insets=new Insets(0,0,0,16);JPanel notes=clear(new BorderLayout(0,6));notes.add(label("Transfer note",Font.BOLD,14),BorderLayout.NORTH);note.setLineWrap(true);note.setWrapStyleWord(true);note.setFont(font(Font.PLAIN,15));note.setMargin(new Insets(8,10,8,10));JScrollPane noteScroll=new JScrollPane(note);noteScroll.setBorder(edge(1,1,1,1));noteScroll.setPreferredSize(new Dimension(300,100));notes.add(noteScroll);notes.setPreferredSize(new Dimension(0,114));notes.setMinimumSize(new Dimension(0,114));lower.add(notes,n);
        JPanel summary=new JPanel(new BorderLayout(14,8)){{setOpaque(false);setBorder(new EmptyBorder(20,20,14,18));}@Override protected void paintComponent(Graphics gr){gr.setColor(DeckersPalette.tileFill(DeckersPalette.ORANGE));gr.fillRect(0,0,getWidth(),getHeight());super.paintComponent(gr);}};
        stockHint.setEditable(false);stockHint.setFocusable(false);stockHint.setLineWrap(true);stockHint.setWrapStyleWord(true);stockHint.setOpaque(false);stockHint.setFont(font(Font.PLAIN,14));stockHint.setBorder(new EmptyBorder(0,0,0,0));summary.add(new JLabel(SalesTaskVisuals.icon("box",DeckersPalette.dark()?new Color(255,170,115):new Color(116,55,22),32)),BorderLayout.WEST);JPanel sumText=clear(new BorderLayout(0,8));sumText.add(totals,BorderLayout.NORTH);sumText.add(stockHint);summary.add(sumText);summary.setPreferredSize(new Dimension(0,114));summary.setMinimumSize(new Dimension(0,114));n.gridx=1;n.weightx=.36;n.insets=new Insets(0,0,0,0);lower.add(summary,n);editor.add(lower,BorderLayout.SOUTH);body.add(editor);
        products.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);products.setFixedCellHeight(78);products.setFixedCellWidth(540);products.setCellRenderer(new ProductRenderer());
        JScrollPane matches=new JScrollPane(products);matches.setPreferredSize(new Dimension(540,162));productPopup.add(matches);JButton hide=button("Hide results","close",false);hide.addActionListener(e->dismissProducts());productPopup.add(hide,BorderLayout.NORTH);productPopup.setVisible(false);finder.add(productPopup,BorderLayout.SOUTH);
        products.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"dismiss-results");products.getActionMap().put("dismiss-results",new AbstractAction(){public void actionPerformed(ActionEvent e){dismissProducts();productSearch.requestFocusInWindow();}});
        productSearch.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE,0),"dismiss-results");productSearch.getActionMap().put("dismiss-results",new AbstractAction(){public void actionPerformed(ActionEvent e){dismissProducts();}});
    }
    private JPanel field(String name,JComponent input,String icon){JPanel p=clear(new BorderLayout(16,0));p.add(new JLabel(SalesTaskVisuals.icon(icon,DeckersPalette.muted(),30)),BorderLayout.WEST);JPanel content=clear(new BorderLayout(0,6));JLabel caption=label(name,Font.PLAIN,14,DeckersPalette::muted);caption.setLabelFor(input);content.add(caption,BorderLayout.NORTH);content.add(input);input.setPreferredSize(new Dimension(250,40));p.add(content);return p;}
    private void configureTable(){
        table.getAccessibleContext().setAccessibleName("Transfer items and quantities");
        table.setFont(font(Font.PLAIN,16));table.setRowHeight(52);table.setShowGrid(true);table.setIntercellSpacing(new Dimension(1,1));table.setGridColor(DeckersPalette.border());table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);table.putClientProperty("SmartStock.preserveBackground",true);
        table.getTableHeader().setFont(font(Font.PLAIN,13));table.getTableHeader().setPreferredSize(new Dimension(900,36));table.getTableHeader().setReorderingAllowed(false);table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col){super.getTableCellRendererComponent(t,value,selected,focus,row,col);setFont(font(Font.PLAIN,13));setForeground(DeckersPalette.text());setBackground(DeckersPalette.tableHeader(new Color(55,100,170)));setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DeckersPalette.border()),new EmptyBorder(6,12,6,6)));return this;}});
        int[] widths={120,145,280,175,190,112},minimums={80,110,180,130,150,70};for(int i=0;i<widths.length;i++){table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);table.getColumnModel().getColumn(i).setMinWidth(minimums[i]);}
        table.setDefaultRenderer(Object.class,new DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int col){super.getTableCellRendererComponent(t,value,selected,focus,row,col);setFont(font(Font.PLAIN,16));setBorder(new EmptyBorder(8,12,8,8));setForeground(DeckersPalette.text());setBackground(selected?DeckersPalette.tileFill(DeckersPalette.ORANGE):DeckersPalette.surface());return this;}});
        table.setDefaultRenderer(Integer.class,table.getDefaultRenderer(Object.class));
        table.getColumnModel().getColumn(4).setCellRenderer((t,value,selected,focus,row,col)->{
            if(!editable()){JLabel l=label(String.valueOf(value),Font.PLAIN,15);l.setHorizontalAlignment(SwingConstants.CENTER);return l;}
            JSpinner s=new JSpinner(new SpinnerNumberModel(number(value),1,1_000_000,1));s.setFont(font(Font.PLAIN,16));styleSpinner(s);JPanel p=new JPanel(new BorderLayout()){ {putClientProperty("SmartStock.preserveBackground",true);} @Override public Color getBackground(){return DeckersPalette.surface();} @Override public void paint(Graphics gr){layoutRenderer(this);super.paint(gr);}};p.setBorder(new EmptyBorder(10,20,10,20));p.add(s);return p;});
        table.getColumnModel().getColumn(4).setCellEditor(new QuantityEditor());
        table.getColumnModel().getColumn(5).setCellRenderer((t,value,selected,focus,row,col)->{JLabel l=new JLabel(editable()?SalesTaskVisuals.icon("trash",DeckersPalette.muted(),20):null,SwingConstants.CENTER);l.setToolTipText(editable()?"Remove "+items.getValueAt(row,2):null);return l;});
        table.getColumnModel().getColumn(5).setCellEditor(new RemoveEditor());
    }
    private void buildFooter(){
        footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,DeckersPalette.border()),new EmptyBorder(12,18,10,20)));footer.setPreferredSize(new Dimension(900,112));
        JPanel left=clear(new FlowLayout(FlowLayout.LEFT,0,0));back.setForeground(DeckersPalette.muted());left.add(back);left.add(Box.createHorizontalStrut(10));left.add(discard);left.add(Box.createHorizontalStrut(10));left.add(saveDraft);footerLeft=left;footer.add(left,BorderLayout.WEST);
        JPanel right=clear(new BorderLayout(0,8));JPanel actions=clear(new BorderLayout(5,0));cancel.setPreferredSize(new Dimension(218,46));primary.setPreferredSize(new Dimension(190,46));back.setPreferredSize(new Dimension(108,46));discard.setPreferredSize(new Dimension(161,46));actions.add(cancel,BorderLayout.WEST);actions.add(primary,BorderLayout.EAST);right.add(actions,BorderLayout.NORTH);right.add(cancelHint,BorderLayout.CENTER);footerRight=right;footer.add(right,BorderLayout.EAST);
    }
    private boolean canLeave(){return !busy&&(!dirty||handler==null||handler.leave());}
    boolean dirty(){return dirty;}
    boolean busy(){return busy;}
    boolean editable(){return !busy&&detailReady&&(selected==null||(!selected.incoming&&selected.pending()));}
    Transfer selected(){return selected;}
    boolean incomingMode(){return incomingMode;}
    void newDraft(){
        loading=true;selected=null;for(int i=destination.getItemCount()-1;i>=0;i--)if(destination.getItemAt(i).id<=0)destination.removeItemAt(i);detailReady=true;items.setRowCount(0);note.setText("");source.setText(store);productSearch.setText("");productModel.clear();productPopup.setVisible(false);title.setText("New transfer");subtitle.setText("Choose a destination and add items to send.");badge.setText("Draft");mode.setText("New transfer");lifecycle.draft=true;dirty=false;loading=false;selectInQueue();updateState();updateSummary();
    }
    TransferDraftStore.Draft draft(String id){
        if(!commitEditor())throw new IllegalArgumentException("Enter a valid whole transfer quantity.");
        if(note.getText().length()>1000)throw new IllegalArgumentException("The transfer note can contain up to 1,000 characters.");
        Destination target=(Destination)destination.getSelectedItem();List<TransferDraftStore.Line> lines=new ArrayList<>();
        for(int i=0;i<items.getRowCount();i++)lines.add(new TransferDraftStore.Line(number(items.getValueAt(i,0)),Objects.toString(items.getValueAt(i,1),""),Objects.toString(items.getValueAt(i,2),""),number(items.getValueAt(i,4))));
        return new TransferDraftStore.Draft(id,target==null?0:target.id(),target==null?"":target.name(),note.getText(),List.copyOf(lines));
    }
    void savedDrafts(List<TransferDraftStore.Draft> drafts){savedDrafts.removeAllItems();for(var draft:drafts)savedDrafts.addItem(draft);updateState();}
    void restoreDraft(TransferDraftStore.Draft draft){
        newDraft();loading=true;destination.setSelectedIndex(-1);
        for(int i=0;i<destination.getItemCount();i++)if(destination.getItemAt(i).id()==draft.destinationId())destination.setSelectedIndex(i);
        note.setText(draft.note());for(var line:draft.lines())items.addRow(new Object[]{line.productId(),line.sku(),line.name(),"Check when sent",line.quantity(),""});
        loading=false;dirty=false;title.setText("Saved transfer draft");mode.setText("Saved locally");updateSummary();updateState();
        message.setText("Draft reopened. Stock availability is checked by the server when sent.");
    }
    void locations(List<LanApiClient.TransferLocation> values){
        loading=true;Object prior=destination.getSelectedItem();destination.removeAllItems();for(var v:values)destination.addItem(new Destination(v.locationId(),v.name()));if(prior!=null)destination.setSelectedItem(prior);if(selected!=null)selectDestination(selected);loading=false;updateState();
    }
    void transfers(List<Transfer> values){transfers.clear();transfers.addAll(values);incoming.setText("Incoming ("+values.stream().filter(Transfer::incoming).count()+")");outgoing.setText("Outgoing ("+values.stream().filter(t->!t.incoming).count()+")");renderQueue();}
    List<Transfer> transfers(){return List.copyOf(transfers);}
    private void switchQueue(boolean next){if(next==incomingMode||!canLeave())return;incomingMode=next;incoming.putClientProperty("selected",next);outgoing.putClientProperty("selected",!next);renderQueue();Transfer first=queueModel.isEmpty()?null:queueModel.get(0);if(first!=null)handler.select(first);else handler.newTransfer();updateState();}
    private void renderQueue(){
        selecting=true;queueModel.clear();String query=queueSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
        for(Transfer t:transfers)if(t.incoming==incomingMode&&(query.isEmpty()||("#"+t.id+" "+t.store+" "+t.sender+" "+t.note+" "+t.badge()).toLowerCase(java.util.Locale.ROOT).contains(query)))queueModel.addElement(t);
        selecting=false;selectInQueue();queue.repaint();
    }
    private void selectInQueue(){selecting=true;queue.clearSelection();if(selected!=null)for(int i=0;i<queueModel.size();i++)if(queueModel.get(i).id==selected.id&&queueModel.get(i).incoming==selected.incoming){queue.setSelectedIndex(i);break;}selecting=false;}
    void showTransfer(Transfer value){
        loading=true;selected=value;incomingMode=value.incoming;detailReady=false;items.setRowCount(0);note.setText(value.note);source.setText(value.incoming?value.store:store);selectDestination(value);
        title.setText("Transfer #"+value.id);badge.setText(value.badge());badge.tone(value.pending()?DeckersPalette.ORANGE:new Color(40,100,190));mode.setText(value.incoming?"Verify items":value.pending()?"Edit mode":"Read only");
        subtitle.setText("Sent by: "+value.sender+"   |   "+DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a").format(Instant.ofEpochMilli(value.created).atZone(zone)));lifecycle.draft=false;
        dirty=false;loading=false;renderQueue();updateState();updateSummary();message.setText("Loading transfer items…");
    }
    private void selectDestination(Transfer t){
        if(t.incoming){Destination current=new Destination(-1,store);boolean found=false;for(int i=0;i<destination.getItemCount();i++)if(destination.getItemAt(i).id==-1)found=true;if(!found)destination.addItem(current);destination.setSelectedItem(current);return;}
        for(int i=0;i<destination.getItemCount();i++)if(destination.getItemAt(i).id==t.destinationId){destination.setSelectedIndex(i);return;}
        // Never choose a different store merely because display names match.
        Destination missing=new Destination(t.destinationId,t.store);destination.addItem(missing);destination.setSelectedItem(missing);
    }
    void detail(List<LanApiClient.TransferDetailItem> lines){
        loading=true;items.setRowCount(0);for(var line:lines)items.addRow(new Object[]{line.productId(),line.sku(),line.name(),line.availableQuantity()==null?"—":line.availableQuantity(),line.quantity(),""});loading=false;detailReady=!lines.isEmpty();dirty=false;message.setText(lines.isEmpty()?"No transfer items found. Refresh to retry.":"");updateState();updateSummary();
    }
    void detailFailed(String text){detailReady=false;loading=true;items.setRowCount(0);loading=false;updateSummary();updateState();message.setText(text);}
    void products(List<LanApiClient.TransferProduct> matches){message.setText(matches.isEmpty()?"No matching inventory items.":"");productModel.clear();for(var p:matches)productModel.addElement(p);if(!matches.isEmpty())products.setSelectedIndex(0);showProducts();}
    private void dismissProducts(){searchPending=false;if(handler!=null)handler.dismissSearch();productPopup.setVisible(false);inventoryFinder.revalidate();body.revalidate();}
    private void showProducts(){boolean visible=editable()&&!productModel.isEmpty();productPopup.setPreferredSize(new Dimension(400,Math.min(286,products.getFixedCellHeight()*productModel.size()+46)));productPopup.setVisible(visible);productPopup.revalidate();body.revalidate();}
    void addScannedProduct(){
        int before=0;for(int i=0;i<items.getRowCount();i++)before+=number(items.getValueAt(i,4));
        addProduct();
        int after=0;for(int i=0;i<items.getRowCount();i++)after+=number(items.getValueAt(i,4));
        if(after>before){productSearch.setText("");productSearch.requestFocusInWindow();}
    }
    void addProduct(){
        if(!editable())return;var p=products.getSelectedValue();if(p==null){message.setText("Search and select an inventory item first.");if(handler!=null)handler.search(productSearch.getText().trim());return;}
        if(!commitEditor())return;try{quantity.commitEdit();}catch(java.text.ParseException e){message.setText("Enter a whole quantity between 1 and 1,000,000.");return;}
        int qty=((Number)quantity.getValue()).intValue();if(qty<1||qty>1_000_000){message.setText("Enter a valid quantity.");return;}
        for(int i=0;i<items.getRowCount();i++)if(number(items.getValueAt(i,0))==p.productId()){long next=(long)number(items.getValueAt(i,4))+qty;if(next>1_000_000){message.setText("Transfer quantity cannot exceed 1,000,000 per item.");return;}items.setValueAt((int)next,i,4);productSearch.setText("");dismissProducts();productSearch.requestFocusInWindow();return;}
        if(items.getRowCount()>=300){message.setText("A transfer can contain up to 300 products.");return;}items.addRow(new Object[]{p.productId(),p.sku(),p.name(),p.availableQuantity(),qty,""});productSearch.setText("");dismissProducts();productSearch.requestFocusInWindow();
    }
    void removeRow(int row){if(editable()&&row>=0&&row<items.getRowCount()){if(table.isEditing())table.getCellEditor().cancelCellEditing();items.removeRow(row);}}
    boolean commitEditor(){return !table.isEditing()||table.getCellEditor().stopCellEditing();}
    LanApiClient.CreateTransferRequest request(){
        if(!commitEditor())throw new IllegalArgumentException("Enter a valid whole transfer quantity.");
        if(selected!=null&&(!selected.pending()||selected.incoming))throw new IllegalArgumentException("Select a pending outgoing transfer.");
        Destination target=(Destination)destination.getSelectedItem();int id=selected==null?(target==null?0:target.id):selected.destinationId;
        if(id<=0)throw new IllegalArgumentException("Choose a destination store. Refresh if it is unavailable.");
        if(items.getRowCount()==0)throw new IllegalArgumentException("Add at least one item to transfer.");
        if(note.getText().trim().length()>1000)throw new IllegalArgumentException("The transfer note can contain up to 1,000 characters.");
        List<LanApiClient.TransferLine> lines=new ArrayList<>();for(int i=0;i<items.getRowCount();i++){int qty=number(items.getValueAt(i,4));if(qty<1||qty>1_000_000)throw new IllegalArgumentException("Every transfer quantity must be between 1 and 1,000,000.");lines.add(new LanApiClient.TransferLine(number(items.getValueAt(i,0)),qty));}
        return new LanApiClient.CreateTransferRequest(id,note.getText().trim(),List.copyOf(lines));
    }
    void busy(boolean value){busy=value;updateState();}
    void clean(){dirty=false;}
    void unsaved(){dirty=true;}
    void status(boolean connected,String sync,boolean healthy){localStatus.setText(connected?"Local server connected":"Local server unavailable");localStatus.setIcon(SalesTaskVisuals.icon("check-circle",connected?new Color(20,153,68):new Color(222,77,48),12));syncStatus.setText(sync);syncStatus.setIcon(SalesTaskVisuals.icon("check-circle",healthy?new Color(20,153,68):new Color(185,125,28),12));}
    private void updateState(){
        boolean edit=editable(),isIncoming=selected!=null&&selected.incoming;queueHint.setText(incomingMode?"Verify items before receiving.":"Select Incoming to verify and receive.");source.setEditable(false);destination.setEnabled(edit&&selected==null);note.setEditable(edit);productSearch.setEnabled(edit);quantity.setEnabled(edit);add.setEnabled(edit);searchRow.setVisible(!isIncoming);inventoryFinder.setVisible(!isIncoming);primary.setText(selected==null?"Send transfer":isIncoming?"Receive transfer":"Save changes");
        primary.setEnabled(!busy&&detailReady&&(selected==null?destination.getItemCount()>0:selected.pending()));cancel.setVisible(selected!=null&&!isIncoming);cancel.setEnabled(!busy&&detailReady&&selected!=null&&selected.pending()&&!isIncoming);discard.setText(selected==null?"Clear draft":"Discard edits");discard.setEnabled(!busy&&(selected==null||!isIncoming&&selected.pending()));discard.setVisible(!isIncoming);cancelHint.setVisible(selected!=null&&!isIncoming);
        saveDraft.setVisible(selected==null);saveDraft.setEnabled(edit&&selected==null);openDraft.setEnabled(!busy&&savedDrafts.getItemCount()>0);deleteDraft.setEnabled(!busy&&savedDrafts.getItemCount()>0);savedDrafts.setEnabled(!busy);
        create.setEnabled(!busy);incoming.setEnabled(!busy);outgoing.setEnabled(!busy);queue.setEnabled(!busy);more.setEnabled(!busy);back.setEnabled(!busy);queueSearch.setEnabled(!busy);
        incoming.putClientProperty("segmentSelected",incomingMode);outgoing.putClientProperty("segmentSelected",!incomingMode);
        incoming.setBackground(incomingMode?DeckersPalette.ORANGE:DeckersPalette.background());outgoing.setBackground(!incomingMode?DeckersPalette.ORANGE:DeckersPalette.background());
        infoTitle.setText(selected!=null&&!selected.pending()?"This transfer is awaiting automatic confirmation from the receiving store.":isIncoming?"Verify every item and quantity before receiving this transfer.":"Stock is added only after the receiving store verifies and receives this transfer.");
        infoText.setText(selected!=null&&!selected.pending()?"Editing and cancellation are unavailable until both stores have synchronized.":isIncoming?"Receive only after checking the physical stock. This adds the listed quantities to this store.":"Source stock is deducted when sent. Destination stock is added after receipt.");
        table.getColumnModel().getColumn(3).setHeaderValue(isIncoming?"Source stock":"Available at "+store);table.getTableHeader().repaint();table.repaint();lifecycle.repaint();revalidate();repaint();
    }
    private void updateSummary(){int units=0;for(int i=0;i<items.getRowCount();i++)units+=number(items.getValueAt(i,4));totals.setText(count(items.getRowCount(),"item")+" • "+count(units,"unit"));itemHeading.setText("Transfer items ("+items.getRowCount()+")");String hint=selected==null?"Sending deducts source stock; receipt adds destination stock.":selected.incoming?"Receiving adds these quantities to "+store+".":"Stock deducted when sent; edits apply after confirmation.";stockHint.setText(hint);}
    private static void styleSpinner(JSpinner spinner){if(spinner.getEditor() instanceof JSpinner.DefaultEditor editor){editor.getTextField().setBackground(DeckersPalette.fieldBackground());editor.getTextField().setForeground(DeckersPalette.text());editor.getTextField().setCaretColor(DeckersPalette.text());}}
    private static void layoutRenderer(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container nested)layoutRenderer(nested);}
    private static String count(int value,String noun){return value+" "+noun+(value==1?"":"s");}
    private static int number(Object value){try{return Integer.parseInt(String.valueOf(value));}catch(Exception e){return 0;}}
    private static void listen(javax.swing.text.JTextComponent text,Runnable action){text.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){action.run();}public void removeUpdate(DocumentEvent e){action.run();}public void changedUpdate(DocumentEvent e){action.run();}});}
    @Override public void doLayout(){rail.setPreferredSize(new Dimension(getWidth()<1250?300:375,600));super.doLayout();}
    private final class Footer extends JPanel {
        Footer(){super(null);putClientProperty("SmartStock.preserveBackground",true);}
        @Override public Color getBackground(){return DeckersPalette.surface();}
        @Override public Dimension getPreferredSize(){return new Dimension(900,getWidth()>0&&getWidth()<800?166:112);}
        @Override public void doLayout(){
            if(footerLeft==null||footerRight==null)return;
            Insets in=getInsets();boolean compact=getWidth()<800;
            Dimension left=footerLeft.getPreferredSize(),right=footerRight.getPreferredSize();
            footerLeft.setBounds(in.left,in.top,Math.min(left.width,getWidth()-in.left-in.right),46);
            int rightWidth=Math.min(right.width,getWidth()-in.left-in.right);
            footerRight.setBounds(getWidth()-in.right-rightWidth,in.top+(compact?58:0),rightWidth,Math.max(0,getHeight()-in.top-in.bottom-(compact?58:0)));
        }
    }
    private final class Body extends JPanel implements Scrollable {
        Body(){super(new BorderLayout(0,12));setOpaque(false);}
        @Override public Dimension getPreferredSize(){return new Dimension(950,800);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 24;}
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return 160;}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return getParent()!=null&&getParent().getHeight()>=getPreferredSize().height;}
    }
    private final class QueueRenderer implements ListCellRenderer<Transfer> {
        public Component getListCellRendererComponent(JList<? extends Transfer> list,Transfer t,int index,boolean selected,boolean focus){
            return new JLabel(){
                {setToolTipText(t.note);getAccessibleContext().setAccessibleName("Transfer "+t.id+", "+(t.incoming?"from ":"to ")+t.store+", "+t.badge()+", "+t.itemCount+" items, "+t.units+" units");}
                @Override protected void paintComponent(Graphics graphics){
                    Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);
                    g.setColor(selected?DeckersPalette.tileFill(DeckersPalette.ORANGE):DeckersPalette.surface());g.fillRect(0,0,getWidth(),getHeight());g.setColor(DeckersPalette.border());g.drawLine(0,getHeight()-1,getWidth(),getHeight()-1);
                    if(selected){g.setColor(DeckersPalette.ORANGE);g.fillRect(0,0,4,getHeight());}
                    g.setColor(DeckersPalette.text());g.setFont(font(Font.BOLD,22));g.drawString("#"+t.id,14,34);
                    Color tone=t.pending()?DeckersPalette.ORANGE:new Color(40,100,190);boolean wrap=!t.pending()&&getWidth()<300;String status=wrap?"Awaiting store":t.badge();g.setFont(font(Font.BOLD,t.pending()?13:11));int width=g.getFontMetrics().stringWidth(status)+18;int bx=getWidth()-width-12;
                    g.setColor(DeckersPalette.blend(DeckersPalette.surface(),tone,.16));g.fillRoundRect(bx,14,width,wrap?36:29,7,7);g.setColor(DeckersPalette.dark()?DeckersPalette.blend(Color.WHITE,tone,.5):tone.darker());g.drawString(status,bx+9,wrap?28:33);if(wrap)g.drawString("confirmation",bx+9,43);
                    g.setFont(font(Font.PLAIN,16));g.setColor(DeckersPalette.text());g.drawString((t.incoming?"From: ":"To: ")+t.store,14,65);
                    g.setFont(font(Font.PLAIN,15));g.setColor(DeckersPalette.muted());g.drawString("Sent: "+DateTimeFormatter.ofPattern("d MMM yyyy, hh:mm a").format(Instant.ofEpochMilli(t.created).atZone(zone)),14,92);g.drawString("By: "+t.sender,14,116);
                    g.setFont(font(Font.BOLD,16));g.setColor(DeckersPalette.text());g.drawString(count(t.itemCount,"item")+" / "+count(t.units,"unit"),14,141);
                    g.setFont(font(Font.PLAIN,15));g.setColor(DeckersPalette.muted());g.clipRect(14,146,getWidth()-26,30);g.drawString(t.note,14,164);g.dispose();
                }
            };
        }
    }
    /** Reuse one plain renderer; measuring 300 HTML labels on the shared EDT is unnecessary. */
    private static final class ProductRenderer extends JLabel implements ListCellRenderer<LanApiClient.TransferProduct> {
        private LanApiClient.TransferProduct product;private boolean selected;
        ProductRenderer(){putClientProperty("html.disable",true);setOpaque(true);}
        public Component getListCellRendererComponent(JList<? extends LanApiClient.TransferProduct> list,LanApiClient.TransferProduct value,int index,boolean selected,boolean focus){this.product=value;this.selected=selected;setToolTipText(value.name()+" · "+value.details());getAccessibleContext().setAccessibleName(value.name()+", "+value.details()+", "+value.sku()+", available "+value.availableQuantity());return this;}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);g.setColor(selected?DeckersPalette.tileFill(DeckersPalette.ORANGE):DeckersPalette.surface());g.fillRect(0,0,getWidth(),getHeight());
            if(product!=null){g.clipRect(12,0,Math.max(0,getWidth()-24),getHeight());g.setFont(font(Font.BOLD,14));g.setColor(DeckersPalette.text());g.drawString(product.name(),12,21);g.setFont(font(Font.PLAIN,14));g.setColor(DeckersPalette.muted());g.drawString(product.sku()+" · Available: "+product.availableQuantity(),12,43);g.drawString(product.details(),12,65);}g.dispose();
        }
    }
    private final class QuantityEditor extends AbstractCellEditor implements TableCellEditor {
        final JSpinner spinner=new JSpinner(new SpinnerNumberModel(1,1,1_000_000,1));
        public Object getCellEditorValue(){return ((Number)spinner.getValue()).intValue();}
        public Component getTableCellEditorComponent(JTable t,Object value,boolean selected,int row,int col){spinner.setValue(number(value));spinner.setFont(font(Font.PLAIN,16));styleSpinner(spinner);return spinner;}
        @Override public boolean stopCellEditing(){try{spinner.commitEdit();int value=((Number)spinner.getValue()).intValue();if(value<1||value>1_000_000)return false;return super.stopCellEditing();}catch(java.text.ParseException e){return false;}}
    }
    private final class RemoveEditor extends AbstractCellEditor implements TableCellEditor {
        final JButton remove=button("","trash",false);int row;
        RemoveEditor(){remove.addActionListener(e->{int r=row;fireEditingCanceled();removeRow(r);});}
        public Object getCellEditorValue(){return "";}
        public Component getTableCellEditorComponent(JTable t,Object value,boolean selected,int row,int col){this.row=row;remove.getAccessibleContext().setAccessibleName("Remove "+items.getValueAt(row,2));return remove;}
    }
}
