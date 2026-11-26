package ui.screens;

import com.google.gson.*;
import ui.components.SearchableComboBox;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Guided, testable presentation of a task. Persistence remains in SalesTasks/the LAN service. */
final class SalesTaskWorkspace extends JPanel {
    interface Handler {
        void select(long id);
        void refresh(String query);
        void save(String nextAction);
        void action(String action);
        boolean discardChanges();
    }
    private final Handler handler;
    private final DefaultListModel<JsonObject> jobs=new DefaultListModel<>();
    final JList<JsonObject> jobList=new JList<>(jobs){@Override public Color getBackground(){return DeckersPalette.surface();}};
    final JTextField search=new JTextField(){@Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);if(getText().isEmpty()&&!hasFocus()){Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);SalesTaskVisuals.icon("search",DeckersPalette.muted(),17).paintIcon(this,g,12,(getHeight()-17)/2);g.setFont(font(Font.PLAIN,14));g.setColor(DeckersPalette.muted());g.drawString("Search by name, phone or task #",38,(getHeight()-g.getFontMetrics().getHeight())/2+g.getFontMetrics().getAscent());g.dispose();}}};
    final JComboBox<String> filter=new JComboBox<>(new String[]{"Open work","All tasks","Received","In progress","Completed","Collected","Cancelled"});
    final JTextField customerName=new JTextField(),phone=new JTextField();
    final JTextArea instructions=new JTextArea(5,30);
    final DefaultTableModel items=new DefaultTableModel(new String[]{"Item / service","Description","Qty","Unit price","Discount %","Line total","#","Actions"},0){
        @Override public boolean isCellEditable(int row,int col){return editable&&(col==1||col==2||col==3||col==4);}
        @Override public Object getValueAt(int row,int col){return col==6?row+1:col==7?"Actions":super.getValueAt(row,col);}
    };
    final JTable itemTable=new JTable(items);
    final SearchableComboBox product=new SearchableComboBox();
    final JSpinner quantity=new JSpinner(new SpinnerNumberModel(1,1,100000,1));
    final JLabel cataloguePrice=new JLabel("Select an item"){@Override public Color getBackground(){return DeckersPalette.background();}},estimate=new JLabel("—"){@Override public Color getBackground(){return DeckersPalette.tileFill(DeckersPalette.ORANGE);}},balance=new JLabel("Not finalized"),paid=new JLabel("—");
    private final JLabel pricingAmount=new JLabel(),pickupAmount=new JLabel();
    final JButton primary=SalesTaskVisuals.button("Next: work & items","check",true),save=SalesTaskVisuals.button("Save progress","save",false);
    private final JLabel taskTitle=new JLabel("New Sales Task"),customerSubtitle=new JLabel("Start with the customer and deadline"),status=new JLabel("New task"),pricing=new JLabel("Estimate");
    private final SalesTaskVisuals.Step[] steps=new SalesTaskVisuals.Step[4];
    private final SalesTaskVisuals.Badge workBadge=new SalesTaskVisuals.Badge("In progress",new Color(40,110,230)),priceBadge=new SalesTaskVisuals.Badge("Estimate",DeckersPalette.ORANGE),asapBadge=new SalesTaskVisuals.Badge("ASAP",DeckersPalette.ORANGE);
    private final JPanel pages=new ScrollablePages(),customerPickerHost=new JPanel(new BorderLayout()),dueHost=new JPanel(new BorderLayout());
    private final JComboBox<Employee> employee=new JComboBox<>();
    private final DefaultListModel<String> fileNames=new DefaultListModel<>();
    private final JTextArea history=new JTextArea();
    private final JList<String> files=new JList<>(fileNames){@Override public Color getBackground(){return DeckersPalette.surface();}};
    private final JButton attach=button("Add file…"),download=button("Save file…"),add=button("Add to task"),misc=button("Service charge…"),remove=button("Remove line");
    private final JTextField saleDiscount=new JTextField("0",4),advance=new JTextField("0",8),advanceReference=new JTextField();
    private final JCheckBox customerDiscount=new JCheckBox("Apply customer sale discount");
    private final JComboBox<String> advanceMethod=new JComboBox<>(new String[]{"CASH","CARD","MMG","CHEQUE","BANK_TRANSFER"});
    private final JPanel initialAdvance=new JPanel(new GridLayout(0,2,8,8));
    private final JLabel summaryCustomer=new JLabel(),summaryCustomerDetail=new JLabel(),summaryDue=new JLabel(),summaryAssigned=new JLabel(),summaryNote=new JLabel("Final price is confirmed after work is completed.");
    private final List<JsonObject> chargeLines=new ArrayList<>();
    private final Map<String,JsonObject> catalogue=new LinkedHashMap<>();
    private final List<JsonObject> allJobs=new ArrayList<>();
    private JsonObject details,checkout=new JsonObject();
    private SalesTaskCustomerPicker customerPicker;
    private SalesTaskDuePicker duePicker;
    private ZoneId zone=ZoneId.systemDefault();
    private boolean loading,dirty,editable=true,busy,selecting;
    private int step;
    private Long selectedId;
    private Consumer<Boolean> dirtyListener=ignored->{};
    private record Employee(Integer id,String name){@Override public String toString(){return name;}}

    SalesTaskWorkspace(Handler handler) {
        super(new BorderLayout());this.handler=handler;setBackground(DeckersPalette.background());
        JPanel rail=new JPanel(new BorderLayout(8,10));rail.setBackground(DeckersPalette.background());rail.setBorder(new EmptyBorder(14,16,14,14));rail.setPreferredSize(new Dimension(338,500));
        JPanel tools=new JPanel(new BorderLayout(0,8));tools.setOpaque(false);JButton create=SalesTaskVisuals.button("New Task","plus",false);create.setFont(font(Font.BOLD,18));create.setPreferredSize(new Dimension(200,48));create.addActionListener(e->{if(!busy&&canLeave())newTask();});
        create.setIcon(SalesTaskVisuals.icon("plus",new Color(20,153,68),20));tools.add(create,BorderLayout.NORTH);
        JPanel find=new JPanel(new BorderLayout(4,6));find.setOpaque(false);search.setPreferredSize(new Dimension(250,40));search.setToolTipText("Search customer, phone, instructions or task number");DeckersSwing.styleField(search);
        JButton refresh=SalesTaskVisuals.button("", "more",false);refresh.setToolTipText("Filter or refresh tasks");refresh.setBorder(new EmptyBorder(4,7,4,7));refresh.addActionListener(e->{JPopupMenu menu=new JPopupMenu();for(int i=0;i<filter.getItemCount();i++){String choice=filter.getItemAt(i);JRadioButtonMenuItem entry=new JRadioButtonMenuItem(choice,choice.equals(filter.getSelectedItem()));entry.addActionListener(a->filter.setSelectedItem(choice));menu.add(entry);}menu.addSeparator();JMenuItem reload=new JMenuItem("Refresh tasks");reload.addActionListener(a->{if(canLeave())handler.refresh(search.getText().trim());});menu.add(reload);menu.show(refresh,0,refresh.getHeight());});
        find.add(search,BorderLayout.CENTER);find.add(refresh,BorderLayout.EAST);tools.add(find,BorderLayout.CENTER);rail.add(tools,BorderLayout.NORTH);
        jobList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);jobList.setCellRenderer(new JobRenderer());jobList.setBackground(DeckersPalette.surface());
        JScrollPane jobScroll=new JScrollPane(jobList);jobScroll.setBorder(null);rail.add(jobScroll,BorderLayout.CENTER);
        jobList.addListSelectionListener(e->{if(e.getValueIsAdjusting()||selecting||busy)return;JsonObject row=jobList.getSelectedValue();if(row==null)return;long id=row.get("task_id").getAsLong();if(selectedId!=null&&id==selectedId)return;if(!canLeave()){selectJob(selectedId);return;}handler.select(id);});
        search.addActionListener(e->{if(canLeave())handler.refresh(search.getText().trim());});filter.addActionListener(e->{if(canLeave())renderJobs();});
        JPanel main=new JPanel(new BorderLayout(10,12)){@Override public Color getBackground(){return DeckersPalette.surface();}};main.setBorder(new EmptyBorder(18,16,10,18));main.setBackground(DeckersPalette.surface());main.putClientProperty("SmartStock.preserveBackground",true);
        JPanel heading=new JPanel(new BorderLayout(10,8)){@Override public void doLayout(){boolean compact=getWidth()<900;taskTitle.setFont(font(Font.BOLD,compact?20:25));asapBadge.setVisible(!compact&&bool(task(),"due_asap"));super.doLayout();}};heading.setOpaque(false);taskTitle.setFont(font(Font.BOLD,25));customerSubtitle.setForeground(DeckersPalette.muted());
        JPanel titles=vertical();JPanel titleLine=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));titleLine.setOpaque(false);titleLine.add(taskTitle);titleLine.add(Box.createHorizontalStrut(18));titleLine.add(asapBadge);titles.add(titleLine);titles.add(Box.createVerticalStrut(6));titles.add(customerSubtitle);heading.add(titles,BorderLayout.CENTER);
        JPanel statuses=new JPanel(new FlowLayout(FlowLayout.RIGHT,10,12));statuses.setOpaque(false);statuses.add(new JLabel("Work status:"));statuses.add(workBadge);statuses.add(new JLabel("Pricing:"));statuses.add(priceBadge);heading.add(statuses,BorderLayout.EAST);heading.setPreferredSize(new Dimension(900,57));
        JPanel navigation=new JPanel(new GridLayout(1,4,0,0));navigation.setOpaque(false);
        String[] names={"Customer & deadline","Work & items","Final pricing","Pickup & payment"};String[] hints={"Find customer and set due date","Add items and instructions","Confirm total when complete","Collect payment and close"};
        for(int i=0;i<4;i++){final int index=i;steps[i]=new SalesTaskVisuals.Step(i+1,names[i],hints[i]);steps[i].addActionListener(e->goTo(index));navigation.add(steps[i]);}
        JPanel top=new JPanel(new BorderLayout(0,14));top.setOpaque(false);top.add(heading,BorderLayout.NORTH);top.add(navigation,BorderLayout.SOUTH);main.add(top,BorderLayout.NORTH);
        pages.setOpaque(true);pages.setBackground(DeckersPalette.surface());pages.putClientProperty("SmartStock.preserveBackground",true);pages.add(customerPage(),"0");pages.add(workPage(),"1");pages.add(pricingPage(),"2");pages.add(pickupPage(),"3");
        JPanel center=new JPanel(new BorderLayout(0,12));center.setOpaque(false);JScrollPane scroll=new JScrollPane(pages);scroll.setBorder(null);scroll.getViewport().setBackground(DeckersPalette.surface());scroll.getVerticalScrollBar().setUnitIncrement(22);center.add(scroll,BorderLayout.CENTER);center.add(summary(),BorderLayout.SOUTH);main.add(center,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(8,8));footer.setBackground(DeckersPalette.background());footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,DeckersPalette.border()),new EmptyBorder(7,8,7,8)));JPanel left=flow();JButton back=SalesTaskVisuals.button("Back","back",false);back.addActionListener(e->goTo(Math.max(0,step-1)));save.addActionListener(e->handler.save("SAVE"));left.add(back);left.add(save);footer.add(left,BorderLayout.WEST);
        JPanel right=flow();JButton print=SalesTaskVisuals.button("Print claim slip","print",false),more=SalesTaskVisuals.button("More","more",false);more.putClientProperty("taskDropdown",true);more.setBorder(new EmptyBorder(10,14,10,30));print.addActionListener(e->handler.action("PRINT"));more.addActionListener(e->more(more));primary.addActionListener(e->primaryAction());primary.setPreferredSize(new Dimension(250,48));
        right.add(print);right.add(more);right.add(primary);footer.add(right,BorderLayout.EAST);add(footer,BorderLayout.SOUTH);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,rail,main){private Boolean compact;@Override public void doLayout(){boolean small=getWidth()<1200;if(compact==null||compact!=small){compact=small;setDividerLocation(small?270:338);}super.doLayout();}};split.setDividerLocation(338);split.setDividerSize(1);split.setResizeWeight(0);split.setBorder(null);rail.setMinimumSize(new Dimension(230,0));main.setMinimumSize(new Dimension(610,0));add(split,BorderLayout.CENTER);
        watch(customerName);watch(phone);watch(instructions);watch(saleDiscount);watch(advance);watch(advanceReference);employee.addActionListener(e->changed());advanceMethod.addActionListener(e->changed());customerDiscount.addActionListener(e->changed());
        items.addTableModelListener(e->{if(loading)return;changed();updateTotals();});
        product.addActionListener(e->updateCataloguePrice());
        files.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mousePressed(java.awt.event.MouseEvent e){int row=files.locationToIndex(e.getPoint());if(row<0||!files.getCellBounds(row,row).contains(e.getPoint()))return;files.setSelectedIndex(row);if(SwingUtilities.isRightMouseButton(e)||e.getX()>files.getWidth()-32)fileMenu().show(files,e.getX(),e.getY());else if(e.getClickCount()==2)download.doClick();}});
        files.getInputMap().put(KeyStroke.getKeyStroke("ENTER"),"save-file");files.getActionMap().put("save-file",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){if(files.getSelectedIndex()>=0)download.doClick();}});files.getInputMap().put(KeyStroke.getKeyStroke("shift F10"),"file-actions");files.getActionMap().put("file-actions",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){if(files.getSelectedIndex()>=0)fileMenu().show(files,0,files.getCellBounds(files.getSelectedIndex(),files.getSelectedIndex()).y);}});
        add.putClientProperty("taskOutline",true);styleInputs(this);newTask();
    }
    private JPanel customerPage(){
        JPanel body=vertical();body.add(label("Customer",18));body.add(Box.createVerticalStrut(10));
        JPanel form=new JPanel(new GridBagLayout()){@Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}};form.setOpaque(false);field(form,"Find customer",customerPickerHost);field(form,"Customer name *",customerName);field(form,"Phone",phone);field(form,"Due date / time",dueHost);field(form,"Assigned employee",employee);
        body.add(form);body.add(Box.createVerticalStrut(18));initialAdvance.setOpaque(false);initialAdvance.setBorder(BorderFactory.createTitledBorder("Optional advance at drop-off"));initialAdvance.add(new JLabel("Amount"));initialAdvance.add(advance);initialAdvance.add(new JLabel("Method"));initialAdvance.add(advanceMethod);initialAdvance.add(new JLabel("Reference (non-cash)"));initialAdvance.add(advanceReference);body.add(initialAdvance);
        body.add(Box.createVerticalStrut(14));body.add(muted("A walk-in customer is fine. Instructions are entered in the next step."));body.add(Box.createVerticalGlue());return body;
    }
    private JPanel workPage(){
        JPanel body=new JPanel(new BorderLayout(0,10));body.setOpaque(false);body.setPreferredSize(new Dimension(900,520));JPanel work=SalesTaskVisuals.card(new GridBagLayout());work.setPreferredSize(new Dimension(800,180));
        instructions.setLineWrap(true);instructions.setWrapStyleWord(true);instructions.setFont(font(Font.PLAIN,16));instructions.setMargin(new Insets(10,10,10,10));instructions.setToolTipText("Describe what to do, such as 50 copies, then laminate 10 sheets.");
        JPanel request=new JPanel(new BorderLayout(0,10));request.setOpaque(false);request.add(label("Work instructions",17),BorderLayout.NORTH);JScrollPane notes=new JScrollPane(instructions);notes.setBorder(BorderFactory.createLineBorder(DeckersPalette.border()));request.add(notes);
        JPanel media=new JPanel(new BorderLayout(0,10));media.setOpaque(false);JPanel mediaHeader=new JPanel(new BorderLayout());mediaHeader.setOpaque(false);mediaHeader.add(label("Attachments",17),BorderLayout.WEST);attach.setIcon(SalesTaskVisuals.icon("clip",DeckersPalette.muted(),18));attach.setMargin(new Insets(6,8,6,8));mediaHeader.add(attach,BorderLayout.EAST);media.add(mediaHeader,BorderLayout.NORTH);files.setVisibleRowCount(2);files.setCellRenderer(new FileRenderer());JScrollPane fileScroll=new JScrollPane(files);fileScroll.setBorder(BorderFactory.createLineBorder(DeckersPalette.border()));media.add(fileScroll);JPanel fileBottom=new JPanel(new BorderLayout());fileBottom.setOpaque(false);JLabel fileHint=muted("Scans, PDFs and reference files");fileHint.setOpaque(true);fileHint.setBackground(DeckersPalette.tileFill(new Color(48,118,193)));fileHint.setBorder(new EmptyBorder(8,10,8,10));fileHint.setIcon(SalesTaskVisuals.icon("info",new Color(48,118,193),18));fileHint.putClientProperty("SmartStock.preserveThemeColors",true);fileBottom.add(fileHint,BorderLayout.CENTER);media.add(fileBottom,BorderLayout.SOUTH);attach.addActionListener(e->handler.action("ATTACH"));download.addActionListener(e->handler.action("DOWNLOAD"));
        request.setMinimumSize(new Dimension(160,80));media.setMinimumSize(new Dimension(170,80));request.setPreferredSize(new Dimension(600,150));media.setPreferredSize(new Dimension(350,150));GridBagConstraints c=new GridBagConstraints();c.fill=GridBagConstraints.BOTH;c.weighty=1;c.weightx=.62;c.gridx=0;c.insets=new Insets(0,0,0,24);work.add(request,c);c.gridx=1;c.weightx=.38;c.insets=new Insets(0,0,0,0);work.add(media,c);body.add(work,BorderLayout.NORTH);
        JPanel itemsCard=SalesTaskVisuals.card(new BorderLayout(0,10));JPanel controls=vertical();controls.add(label("Add item from inventory",17));controls.add(Box.createVerticalStrut(8));
        JPanel entry=new JPanel(new GridBagLayout());entry.setOpaque(false);product.setPreferredSize(new Dimension(490,38));product.setMinimumSize(new Dimension(90,38));quantity.setPreferredSize(new Dimension(124,38));cataloguePrice.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DeckersPalette.border()),new EmptyBorder(8,12,8,12)));cataloguePrice.setOpaque(true);cataloguePrice.setBackground(DeckersPalette.background());cataloguePrice.setPreferredSize(new Dimension(160,38));
        String[] labels={"Item / service","Unit price","Quantity",""};JComponent[] inputs={product,cataloguePrice,quantity,add};for(int col=0;col<4;col++){c=new GridBagConstraints();c.gridx=col;c.gridy=0;c.weightx=col==0?1:0;c.fill=GridBagConstraints.HORIZONTAL;c.insets=new Insets(0,0,6,col==3?0:20);entry.add(muted(labels[col]),c);c.gridy=1;c.insets=new Insets(0,0,0,col==3?0:20);entry.add(inputs[col],c);}add.setPreferredSize(new Dimension(232,40));add.addActionListener(e->addCatalogueItem());controls.add(entry);controls.add(Box.createVerticalStrut(14));controls.add(label("Task items (estimate)",17));itemsCard.add(controls,BorderLayout.NORTH);
        DeckersSwing.styleTable(itemTable);itemTable.putClientProperty("SmartStock.preserveBackground",false);itemTable.setFont(font(Font.PLAIN,15));itemTable.setForeground(DeckersPalette.text());itemTable.setFillsViewportHeight(true);itemTable.setRowHeight(36);itemTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);itemTable.setGridColor(DeckersPalette.border());itemTable.getTableHeader().setFont(font(Font.BOLD,14));itemTable.getTableHeader().setPreferredSize(new Dimension(500,36));
        var columns=itemTable.getColumnModel();columns.removeColumn(columns.getColumn(4));columns.moveColumn(5,0);int[] widths={45,210,230,65,100,110,70};for(int i=0;i<widths.length;i++)columns.getColumn(i).setPreferredWidth(widths[i]);columns.getColumn(0).setMaxWidth(50);columns.getColumn(6).setMaxWidth(85);
        itemTable.getTableHeader().setDefaultRenderer(new javax.swing.table.DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int col){super.getTableCellRendererComponent(table,value,false,false,row,col);setFont(font(Font.PLAIN,14));setBackground(DeckersPalette.background());setForeground(DeckersPalette.text());setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,1,DeckersPalette.border()),new EmptyBorder(8,10,8,10)));return this;}});
        var priceRenderer=new javax.swing.table.DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int col){super.getTableCellRendererComponent(table,value,selected,focus,row,col);if(value!=null&&!value.toString().isBlank())try{setText(money(new BigDecimal(value.toString())));}catch(NumberFormatException ignored){}setBorder(new EmptyBorder(0,10,0,10));return this;}};columns.getColumn(4).setCellRenderer(priceRenderer);
        var actionRenderer=new javax.swing.table.DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable table,Object value,boolean selected,boolean focus,int row,int col){super.getTableCellRendererComponent(table,"",selected,focus,row,col);setHorizontalAlignment(CENTER);setIcon(SalesTaskVisuals.icon("more",DeckersPalette.muted(),18));return this;}};columns.getColumn(6).setCellRenderer(actionRenderer);
        itemTable.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mousePressed(java.awt.event.MouseEvent e){int row=itemTable.rowAtPoint(e.getPoint()),col=itemTable.columnAtPoint(e.getPoint());if(row>=0&&col>=0&&itemTable.convertColumnIndexToModel(col)==7){itemTable.setRowSelectionInterval(row,row);lineMenu(row).show(itemTable,e.getX(),e.getY());}}});
        itemTable.getInputMap().put(KeyStroke.getKeyStroke("shift F10"),"task-line-actions");itemTable.getActionMap().put("task-line-actions",new AbstractAction(){public void actionPerformed(java.awt.event.ActionEvent e){int row=itemTable.getSelectedRow();if(row>=0){Rectangle cell=itemTable.getCellRect(row,itemTable.getColumnCount()-1,true);lineMenu(row).show(itemTable,cell.x,cell.y+cell.height);}}});
        JScrollPane tableScroll=new JScrollPane(itemTable);tableScroll.setPreferredSize(new Dimension(700,110));tableScroll.setMinimumSize(new Dimension(250,110));tableScroll.setBorder(BorderFactory.createLineBorder(DeckersPalette.border()));itemsCard.add(tableScroll);
        JPanel lower=new JPanel(new BorderLayout(8,6));lower.setOpaque(false);misc.addActionListener(e->addService());remove.addActionListener(e->{int row=itemTable.getSelectedRow();if(row>=0){chargeLines.remove(row);items.removeRow(row);}});
        JLabel info=muted("Prices come from inventory. Quantities can change until final pricing.");info.setIcon(SalesTaskVisuals.icon("info",new Color(48,118,193),18));lower.add(info,BorderLayout.CENTER);estimate.setFont(font(Font.BOLD,19));estimate.setOpaque(true);estimate.setBackground(DeckersPalette.tileFill(DeckersPalette.ORANGE));estimate.setBorder(new EmptyBorder(8,14,8,14));estimate.setPreferredSize(new Dimension(336,42));lower.add(estimate,BorderLayout.EAST);itemsCard.add(lower,BorderLayout.SOUTH);body.add(itemsCard);return body;
    }
    private JPopupMenu lineMenu(int row){JPopupMenu menu=new JPopupMenu();JMenuItem discount=new JMenuItem("Line discount…"),delete=new JMenuItem("Remove line");discount.setEnabled(editable&&!busy);delete.setEnabled(editable&&!busy);discount.addActionListener(e->{String input=JOptionPane.showInputDialog(this,"Line discount %",value(row,4));if(input==null)return;try{BigDecimal value=new BigDecimal(input.trim());if(value.signum()<0||value.compareTo(new BigDecimal("100"))>0)throw new NumberFormatException();items.setValueAt(value,row,4);}catch(NumberFormatException ex){message("Enter a discount from 0 to 100.");}});delete.addActionListener(e->remove.doClick());menu.add(discount);menu.add(delete);return menu;}
    private JPopupMenu fileMenu(){JPopupMenu menu=new JPopupMenu();JMenuItem saveFile=new JMenuItem("Save attachment…");saveFile.setEnabled(download.isEnabled());saveFile.addActionListener(e->download.doClick());menu.add(saveFile);return menu;}
    int selectedAttachmentIndex(){return files.getSelectedIndex();}
    private JPanel pricingPage(){
        JPanel body=vertical();body.add(label("Confirm the final bill",21));body.add(Box.createVerticalStrut(10));body.add(muted("Check the actual quantities and prices in Work & items, then finalize the bill."));body.add(Box.createVerticalStrut(18));
        JPanel options=flow();options.add(new JLabel("Sale discount %"));options.add(saleDiscount);options.add(customerDiscount);body.add(options);body.add(Box.createVerticalStrut(14));
        pricingAmount.setFont(font(Font.BOLD,24));body.add(pricingAmount);body.add(Box.createVerticalStrut(14));body.add(muted("Final amounts use the store's sale tax, rounding and price permissions."));body.add(Box.createVerticalStrut(8));body.add(muted("Advances are credited at pickup. Final pricing does not deduct stock."));body.add(Box.createVerticalGlue());return body;
    }
    private JPanel pickupPage(){
        JPanel body=vertical();body.add(label("Pickup & payment",21));body.add(Box.createVerticalStrut(10));body.add(muted("Take the remaining payment or use an eligible customer account, then hand over the work."));body.add(Box.createVerticalStrut(14));
        pickupAmount.setFont(font(Font.BOLD,24));body.add(pickupAmount);body.add(Box.createVerticalStrut(14));JPanel buttons=flow();JButton payment=button("Add advance / payment"),refund=button("Refund advance"),receipt=button("Final receipt");buttons.add(payment);buttons.add(refund);buttons.add(receipt);payment.addActionListener(e->handler.action("PAYMENT"));refund.addActionListener(e->handler.action("REFUND"));receipt.addActionListener(e->handler.action("RECEIPT"));body.add(buttons);
        body.add(Box.createVerticalStrut(14));history.setEditable(false);history.setLineWrap(true);history.setWrapStyleWord(true);history.setFont(font(Font.PLAIN,14));history.setMargin(new Insets(10,10,10,10));JScrollPane scroll=new JScrollPane(history);scroll.setPreferredSize(new Dimension(600,270));body.add(scroll);return body;
    }
    private JPanel summary(){
        JPanel panel=SalesTaskVisuals.card(new GridLayout(1,5,12,8));panel.setBorder(new EmptyBorder(10,16,10,16));panel.putClientProperty("taskSummary",true);panel.addComponentListener(new java.awt.event.ComponentAdapter(){@Override public void componentResized(java.awt.event.ComponentEvent e){panel.revalidate();}});panel.setPreferredSize(new Dimension(800,102));
        panel.add(summaryCell("Customer",summaryCustomer,"person"));panel.add(summaryCell("Due date",summaryDue,"calendar"));panel.add(summaryCell("Assigned to",summaryAssigned,"person"));panel.add(summaryCell("Advance received",paid,"payment"));panel.add(summaryCell("Balance",balance,"info"));
        return panel;
    }
    private JPanel summaryCell(String title,JLabel value,String symbol){JPanel p=vertical();p.add(muted(title));p.add(Box.createVerticalStrut(6));value.setFont(font(Font.BOLD,15));value.setIcon(SalesTaskVisuals.icon(symbol,DeckersPalette.muted(),20));value.setIconTextGap(9);p.add(value);p.add(Box.createVerticalStrut(4));if(title.equals("Customer")){summaryCustomerDetail.setFont(font(Font.PLAIN,11));summaryCustomerDetail.setForeground(DeckersPalette.muted());p.add(summaryCustomerDetail);}if(title.equals("Due date")){JButton edit=button("<html><u>Edit customer / deadline</u></html>");edit.putClientProperty("taskLink",true);edit.setFont(font(Font.PLAIN,12));edit.setBorder(new EmptyBorder(0,0,0,0));edit.addActionListener(e->goTo(0));p.add(edit);}if(title.equals("Balance")){JLabel note=muted("<html><i>Final price is confirmed<br>after work is completed.</i></html>");note.setFont(font(Font.PLAIN,11));p.add(note);}return p;}
    void setOptions(JsonObject options,ZoneId zone){
        this.zone=zone;loading=true;employee.removeAllItems();employee.addItem(new Employee(null,"Unassigned"));
        if(options.has("employees"))for(JsonElement el:options.getAsJsonArray("employees")){JsonObject e=el.getAsJsonObject();employee.addItem(new Employee(e.get("user_id").getAsInt(),text(e,"name")));}
        catalogue.clear();if(options.has("products")){Map<String,Integer> counts=new LinkedHashMap<>();for(JsonElement el:options.getAsJsonArray("products")){JsonObject p=el.getAsJsonObject();if(!"SMARTSTOCK-MISC".equals(text(p,"sku")))counts.merge(text(p,"name"),1,Integer::sum);}for(JsonElement el:options.getAsJsonArray("products")){JsonObject p=el.getAsJsonObject();if("SMARTSTOCK-MISC".equals(text(p,"sku")))continue;String name=text(p,"name");String label=counts.get(name)>1?name+" ["+text(p,"sku")+"]":name;if(catalogue.containsKey(label))label+=" #"+text(p,"product_id");catalogue.put(label,p);}}
        product.setOptions(new ArrayList<>(catalogue.keySet()));loading=false;if(details==null)newTask();
    }
    void setJobs(JsonArray rows,Long select){allJobs.clear();for(JsonElement e:rows)allJobs.add(e.getAsJsonObject());renderJobs();selectJob(select);}
    private void renderJobs(){selecting=true;try{jobs.clear();String f=(String)filter.getSelectedItem();for(JsonObject t:allJobs){String state=text(t,"status");if("Open work".equals(f)&&List.of("COLLECTED","CANCELLED").contains(state))continue;if(!List.of("Open work","All tasks").contains(f)&&!f.toUpperCase().replace(' ','_').equals(state))continue;jobs.addElement(t);}}finally{selecting=false;}}
    void selectJob(Long id){selecting=true;try{jobList.clearSelection();if(id!=null)for(int i=0;i<jobs.size();i++)if(jobs.get(i).get("task_id").getAsLong()==id){jobList.setSelectedIndex(i);jobList.ensureIndexIsVisible(i);break;}}finally{selecting=false;}}
    void showTask(JsonObject detail){
        Long id=detail.getAsJsonObject("task").get("task_id").getAsLong();boolean same=id.equals(selectedId);details=detail;selectedId=id;loading=true;JsonObject t=detail.getAsJsonObject("task");
        customerName.setText(text(t,"customer_name"));phone.setText(text(t,"phone"));instructions.setText(text(t,"instructions"));
        installCustomer(t);for(int i=0;i<employee.getItemCount();i++)if(java.util.Objects.equals(employee.getItemAt(i).id,nullableInt(t,"assigned_user_id")))employee.setSelectedIndex(i);
        checkout=JsonParser.parseString(text(t,"checkout_request")).getAsJsonObject().deepCopy();saleDiscount.setText(checkout.has("saleDiscountPercent")?text(checkout,"saleDiscountPercent"):"0");customerDiscount.setSelected(checkout.has("applyCustomerDiscount")&&checkout.get("applyCustomerDiscount").getAsBoolean());
        chargeLines.clear();items.setRowCount(0);if(checkout.has("lines"))for(JsonElement e:checkout.getAsJsonArray("lines")){JsonObject l=e.getAsJsonObject().deepCopy();chargeLines.add(l);items.addRow(new Object[]{itemName(l),text(l,"miscItemName"),number(l,"quantity"),number(l,"unitPrice"),l.has("discountPercent")?number(l,"discountPercent"):BigDecimal.ZERO,lineTotal(l)});}
        fileNames.clear();if(detail.has("files"))for(JsonElement e:detail.getAsJsonArray("files")){JsonObject file=e.getAsJsonObject();fileNames.addElement(text(file,"filename")+(file.has("size")?"    "+Math.max(1,(number(file,"size").longValue()+1023)/1024)+" KB":""));}history.setText(history(detail));
        loading=false;dirty=false;editable=!closed()&&!finalized();initialAdvance.setVisible(false);if(!same)step=closed()?3:finalized()?3:"COMPLETED".equals(state())?2:1;
        if(!closed()&&!finalized()&&step==3)step="COMPLETED".equals(state())?2:1;if(!closed()&&!"COMPLETED".equals(state())&&step==2)step=1;
        updateView();selectJob(id);dirtyListener.accept(false);
    }
    void newTask(){
        loading=true;details=null;selectedId=null;step=0;checkout=new JsonObject();customerName.setText("");phone.setText("");instructions.setText("");chargeLines.clear();items.setRowCount(0);saleDiscount.setText("0");customerDiscount.setSelected(false);advance.setText("0");advanceReference.setText("");employee.setSelectedIndex(employee.getItemCount()>0?0:-1);installCustomer(null);fileNames.clear();history.setText("");initialAdvance.setVisible(true);editable=true;loading=false;dirty=false;selectJob(null);updateView();
    }
    private void installCustomer(JsonObject task){
        customerPickerHost.removeAll();SalesTaskCustomerPicker.Customer c=task==null||nullableInt(task,"customer_id")==null?null:new SalesTaskCustomerPicker.Customer(nullableInt(task,"customer_id"),text(task,"customer_name"),text(task,"phone"),details==null?"":text(details,"customerAccountNumber"),"");
        customerPicker=new SalesTaskCustomerPicker(c,chosen->{if(chosen!=null){customerName.setText(chosen.name());phone.setText(chosen.phone());}changed();});customerPickerHost.add(customerPicker);
        dueHost.removeAll();String due=task==null?"":text(task,"due_at");duePicker=new SalesTaskDuePicker(zone,due.isBlank()?null:parseDate(due));if(task!=null)duePicker.setAsap(bool(task,"due_asap"));
        dueHost.add(duePicker);watch(duePicker.date);duePicker.enabled.addActionListener(e->changed());duePicker.asap.addActionListener(e->changed());duePicker.hour.addActionListener(e->changed());duePicker.minute.addActionListener(e->changed());duePicker.period.addActionListener(e->changed());
        customerPickerHost.revalidate();dueHost.revalidate();
    }
    private void updateView(){
        JsonObject t=task();taskTitle.setText(details==null?"New Sales Task":"ST-"+selectedId+"   |   "+text(t,"customer_name"));String account=details==null?"":text(details,"customerAccountNumber");customerSubtitle.setText(details==null?"Save customer instructions now; confirm the final bill after the work is completed.":(account.isBlank()?"":"Account: "+account+"    |    ")+"Phone: "+text(t,"phone")+(bool(t,"due_asap")?"":"    Due: "+deadline(t)));
        workBadge.setText(stageName(state()));workBadge.tone(statusColor(state()));priceBadge.setText(finalized()?"Finalized":"Estimate");asapBadge.setVisible(bool(t,"due_asap"));
        steps[0].hint=details==null?"Find customer and set due date":text(t,"customer_name")+" · "+deadline(t);for(int i=0;i<steps.length;i++){steps[i].active=i==step;steps[i].done=i<step;steps[i].setEnabled(!busy&&(i<2||details!=null&&("COMPLETED".equals(state())||closed()))&&(i!=3||finalized()||closed()));steps[i].repaint();}
        ((CardLayout)pages.getLayout()).show(pages,String.valueOf(step));setTreeEnabled(customerPickerHost,editable&&!busy);setTreeEnabled(dueHost,editable&&!busy);if(editable&&!busy)duePicker.setAsap(duePicker.dueAsap());customerName.setEditable(editable&&!busy);phone.setEditable(editable&&!busy);itemTable.setEnabled(!busy);instructions.setEditable(editable&&!busy);employee.setEnabled(editable&&!busy);
        product.setEnabled(editable&&!busy);quantity.setEnabled(editable&&!busy);add.setEnabled(editable&&!busy);misc.setEnabled(editable&&!busy);remove.setEnabled(editable&&!busy);attach.setEnabled(details!=null&&!closed()&&!busy);download.setEnabled(details!=null&&!fileNames.isEmpty()&&!busy);
        saleDiscount.setEditable(editable&&!busy);customerDiscount.setEnabled(editable&&!busy);save.setEnabled(!busy&&editable);primary.setEnabled(!busy&&!closed());
        primary.setText(step==0?"Next: work & items":step==1?(details==null?"Create task":"RECEIVED".equals(state())?"Start work":"COMPLETED".equals(state())?"Next: final pricing":"Mark work completed"):step==2?(finalized()?"Next: pickup & payment":"Finalize bill"):closed()?"Task closed":"Collect & take payment");
        updateTotals();
    }
    void goTo(int target){if(target<0||target>3)return;if(target>=2&&(details==null||!"COMPLETED".equals(state()))&&!closed())return;if(target==3&&!finalized()&&!closed())return;step=target;updateView();}
    private void primaryAction(){
        if(step==0){if(customerName.getText().isBlank()){message("Enter the customer name or use Lookup.");return;}goTo(1);return;}
        if(step==1){if(details==null)handler.save("SAVE");else if("RECEIVED".equals(state()))handler.save("START");else if("COMPLETED".equals(state()))goTo(2);else handler.save("COMPLETE");return;}
        if(step==2){if(finalized())goTo(3);else handler.save("FINALIZE");return;}handler.action("COLLECT");
    }
    private void more(JButton owner){JPopupMenu menu=new JPopupMenu();String[][] actions={{"Add service charge","ADD_SERVICE"},{"Add advance / payment","PAYMENT"},{"Refund advance","REFUND"},{"Edit prices / discounts","PRICING"},{"View payment & activity history","HISTORY"},{"Reopen work","REOPEN"},{"Reopen pricing","REOPEN_PRICING"},{"Cancel task","CANCEL"},{"Final receipt","RECEIPT"}};for(String[] entry:actions){JMenuItem item=new JMenuItem(entry[0]);item.setEnabled(!busy&&("ADD_SERVICE".equals(entry[1])?editable:details!=null));item.addActionListener(e->{handler.action(entry[1]);});menu.add(item);}menu.show(owner,0,owner.getHeight());}
    JsonObject draft(){
        commitEditor();JsonObject b=new JsonObject();b.addProperty("customerName",customerName.getText().trim());b.addProperty("phone",phone.getText().trim());b.addProperty("instructions",instructions.getText().trim());
        if(customerName.getText().isBlank()||instructions.getText().isBlank())throw new IllegalArgumentException("Enter a customer name and work instructions.");
        var c=customerPicker.selected();b.add("customerId",c==null?JsonNull.INSTANCE:new JsonPrimitive(c.id()));Employee person=(Employee)employee.getSelectedItem();b.add("assignedUserId",person==null||person.id==null?JsonNull.INSTANCE:new JsonPrimitive(person.id));b.addProperty("dueAt",duePicker.dueAt());b.addProperty("dueAsap",duePicker.dueAsap());
        JsonObject bill=checkout.deepCopy();JsonArray lines=new JsonArray();for(int i=0;i<items.getRowCount();i++){JsonObject line=chargeLines.get(i).deepCopy();line.addProperty("miscItemName",value(i,1));setDecimal(line,"quantity",value(i,2),true);setDecimal(line,"unitPrice",value(i,3),false);setDecimal(line,"discountPercent",value(i,4).isBlank()?"0":value(i,4),false);lines.add(line);}bill.add("lines",lines);bill.addProperty("saleDiscountPercent",new BigDecimal(saleDiscount.getText().trim()));bill.addProperty("applyCustomerDiscount",customerDiscount.isSelected());b.add("checkout",bill);
        if(details==null){b.addProperty("advance",new BigDecimal(advance.getText().trim()));b.addProperty("method",(String)advanceMethod.getSelectedItem());b.addProperty("reference",advanceReference.getText());}return b;
    }
    private static void setDecimal(JsonObject line,String key,String value,boolean integer){if(value.isBlank()){line.remove(key);return;}BigDecimal n=new BigDecimal(value);if(n.signum()<0||integer&&(n.scale()>0||n.signum()==0))throw new IllegalArgumentException("Enter a positive whole quantity and non-negative prices.");line.addProperty(key,n);}
    private void commitEditor(){if(itemTable.isEditing()&&!itemTable.getCellEditor().stopCellEditing())throw new IllegalArgumentException("Finish editing the charge line first.");}
    private String value(int row,int col){Object v=items.getValueAt(row,col);return v==null?"":v.toString().trim();}
    void addCatalogueItem(){if(!editable)return;try{quantity.commitEdit();}catch(java.text.ParseException e){message("Enter a positive whole quantity.");return;}JsonObject p=catalogue.get(String.valueOf(product.getSelectedItem()));if(p==null){message("Search and select an inventory item first.");return;}JsonObject line=new JsonObject();line.add("productId",p.get("product_id"));line.addProperty("miscItemName",text(p,"name"));line.addProperty("miscItem",false);line.addProperty("quantity",((Number)quantity.getValue()).intValue());line.add("unitPrice",p.get("price"));line.addProperty("discountPercent",0);chargeLines.add(line);items.addRow(new Object[]{text(p,"name"),text(p,"name"),line.get("quantity").getAsInt(),number(p,"price"),BigDecimal.ZERO,lineTotal(line)});}
    void addServiceLine(int productId,String description,BigDecimal price,int qty){JsonObject line=new JsonObject();line.addProperty("productId",productId);line.addProperty("miscItem",true);line.addProperty("miscItemName",description);line.addProperty("quantity",qty);line.addProperty("unitPrice",price);line.addProperty("discountPercent",0);chargeLines.add(line);items.addRow(new Object[]{description,description,qty,price,BigDecimal.ZERO,lineTotal(line)});}
    private void addService(){handler.action("ADD_SERVICE");}
    private void updateCataloguePrice(){JsonObject p=catalogue.get(String.valueOf(product.getSelectedItem()));cataloguePrice.setText(p==null?"Select an item":money(number(p,"price")));}
    private void updateTotals(){
        BigDecimal total=BigDecimal.ZERO;boolean incomplete=false;for(int i=0;i<items.getRowCount();i++){try{if(value(i,2).isBlank()||value(i,3).isBlank()){incomplete=true;continue;}BigDecimal amount=new BigDecimal(value(i,2)).multiply(new BigDecimal(value(i,3))).multiply(BigDecimal.ONE.subtract(new BigDecimal(value(i,4).isBlank()?"0":value(i,4)).movePointLeft(2)));total=total.add(amount);loading=true;items.setValueAt(money(amount),i,5);loading=false;}catch(Exception e){incomplete=true;loading=false;}}
        estimate.setText("Estimated total   "+(incomplete?"Incomplete":money(total)));JsonObject t=task();BigDecimal advancePaid=details==null?BigDecimal.ZERO:number(details,"paid");paid.setText(money(advancePaid)+paymentMethodSummary());
        balance.setText(closed()?"Settled":finalized()?money(number(t,"final_total").subtract(advancePaid)):"Not finalized");
        pricingAmount.setText(finalized()?"Final total: "+money(number(t,"final_total")):"Item estimate: "+(incomplete?"Incomplete":money(total)));
        pickupAmount.setText(finalized()?"Final total: "+money(number(t,"final_total"))+"    Advance: "+money(advancePaid):"Final bill not confirmed");
        summaryCustomer.setText(customerName.getText().isBlank()?"Walk-in":customerName.getText());String account=details==null?"":text(details,"customerAccountNumber");summaryCustomerDetail.setText((account.isBlank()?"":"Account: "+account+" | ")+phone.getText());summaryCustomerDetail.setToolTipText(summaryCustomerDetail.getText());summaryDue.setText(duePicker==null?"No deadline":duePicker.dueAsap()?"ASAP":duePicker.enabled.isSelected()?duePicker.date.getText()+" "+duePicker.hour.getSelectedItem()+":"+duePicker.minute.getSelectedItem()+" "+duePicker.period.getSelectedItem():"No deadline");Employee who=(Employee)employee.getSelectedItem();summaryAssigned.setText(who==null?"Unassigned":who.name);for(JLabel value:new JLabel[]{summaryCustomer,summaryDue,summaryAssigned,paid,balance})value.setToolTipText(value.getText());
        save.setText(dirty?"Save progress *":"Save progress");
    }
    private void changed(){if(loading)return;dirty=true;dirtyListener.accept(true);updateTotals();}
    private String paymentMethodSummary(){if(details==null||!details.has("payments"))return "";java.util.Set<String> methods=new java.util.LinkedHashSet<>();for(JsonElement entry:details.getAsJsonArray("payments"))methods.add(text(entry.getAsJsonObject(),"payment_method"));if(methods.isEmpty())return "";if(methods.size()>1)return " (Mixed)";return " ("+switch(methods.iterator().next()){case "CASH"->"Cash";case "CARD"->"Card";case "BANK_TRANSFER"->"Bank";case "CHEQUE"->"Cheque";default->methods.iterator().next();}+")";}
    private void watch(javax.swing.text.JTextComponent field){field.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){changed();}public void removeUpdate(DocumentEvent e){changed();}public void changedUpdate(DocumentEvent e){changed();}});}
    boolean isDirty(){commitEditor();return dirty;}
    boolean isNew(){return details==null;}
    boolean canLeave(){return !isDirty()||handler.discardChanges();}
    void setBusy(boolean value){busy=value;jobList.setEnabled(!value);search.setEnabled(!value);filter.setEnabled(!value);updateView();}
    void onDirty(Consumer<Boolean> listener){dirtyListener=listener;}
    int currentStep(){return step;}
    void showStepAfterSave(String action){if("FINALIZE".equals(action))step=3;else if("COMPLETE".equals(action))step=2;updateView();}
    private JsonObject task(){return details==null?new JsonObject():details.getAsJsonObject("task");}
    private String state(){return text(task(),"status");}
    private boolean finalized(){return "FINALIZED".equals(text(task(),"billing_status"));}
    private boolean closed(){return List.of("COLLECTED","CANCELLED").contains(state());}
    private String itemName(JsonObject line){for(JsonObject p:catalogue.values())if(p.get("product_id").getAsInt()==line.get("productId").getAsInt())return text(p,"name");return text(line,"miscItemName");}
    private String deadline(JsonObject t){if(bool(t,"due_asap"))return "ASAP";String due=text(t,"due_at");try{return due.isBlank()?"No deadline":parseDate(due).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("dd MMM, h:mm a"));}catch(Exception e){return due;}}
    private static OffsetDateTime parseDate(String value){return OffsetDateTime.parse(value.replace(' ','T').replaceAll("([+-]\\d\\d)$","$1:00"));}
    private static String stageName(String state){return switch(state){case "RECEIVED"->"Received";case "IN_PROGRESS"->"In progress";case "COMPLETED"->"Completed";case "COLLECTED"->"Collected";case "CANCELLED"->"Cancelled";default->"New task";};}
    private static String history(JsonObject detail){StringBuilder text=new StringBuilder("PAYMENTS\n");if(detail.has("payments"))for(JsonElement e:detail.getAsJsonArray("payments")){JsonObject p=e.getAsJsonObject();text.append(text(p,"created_at")).append("  ").append(text(p,"payment_action")).append("  ").append(money(number(p,"amount"))).append("  ").append(text(p,"payment_method")).append("  ").append(text(p,"user_name")).append('\n');}text.append("\nACTIVITY\n");if(detail.has("events"))for(JsonElement e:detail.getAsJsonArray("events")){JsonObject p=e.getAsJsonObject();text.append(text(p,"created_at")).append("  ").append(text(p,"action")).append("  ").append(text(p,"user_name")).append("  ").append(text(p,"reason")).append('\n');}return text.toString();}
    private static BigDecimal lineTotal(JsonObject l){return number(l,"quantity").multiply(number(l,"unitPrice"));}
    static String text(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    private static Integer nullableInt(JsonObject o,String key){return text(o,key).isBlank()?null:o.get(key).getAsInt();}
    private static boolean bool(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()&&o.get(key).getAsBoolean();}
    private static BigDecimal number(JsonObject o,String key){return text(o,key).isBlank()?BigDecimal.ZERO:o.get(key).getAsBigDecimal();}
    private static String money(BigDecimal value){return utils.CurrencyFormatter.format(value);}
    private static Font font(int style,int size){return SalesTaskVisuals.font(style,size);}
    private static JPanel vertical(){JPanel p=new JPanel(){@Override protected void addImpl(Component c,Object constraints,int index){if(c instanceof JComponent child)child.setAlignmentX(Component.LEFT_ALIGNMENT);super.addImpl(c,constraints,index);}};p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setOpaque(false);return p;}
    private static JPanel flow(){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,8,4)){@Override public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}};p.setOpaque(false);return p;}
    private static JLabel label(String text,int size){JLabel l=new JLabel(text);l.setFont(font(Font.BOLD,size));l.setForeground(DeckersPalette.text());return l;}
    private static JLabel muted(String text){JLabel l=new JLabel(text);l.setFont(font(Font.PLAIN,13));l.setForeground(DeckersPalette.muted());return l;}
    private static JButton button(String text){return SalesTaskVisuals.button(text,null,false);}
    private static Color statusColor(String state){return switch(state){case "RECEIVED"->DeckersPalette.PURPLE;case "COMPLETED","COLLECTED"->new Color(20,153,68);case "CANCELLED"->DeckersPalette.CORAL;default->new Color(40,110,230);};}
    private static void styleInputs(Component component){if(component instanceof JComponent jc){if(jc.getFont() instanceof javax.swing.plaf.UIResource)jc.setFont(font(Font.PLAIN,14));if(component instanceof JTextField||component instanceof JTextArea||component instanceof JComboBox<?>||component instanceof JSpinner){jc.setBackground(DeckersPalette.fieldBackground());jc.setForeground(DeckersPalette.text());}}if(component instanceof Container parent)for(Component child:parent.getComponents())if(!(component instanceof SalesTaskVisuals.TaskButton)&&!(component instanceof SalesTaskVisuals.Step)&&!(component instanceof SalesTaskVisuals.Badge))styleInputs(child);}
    private static final class FileRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel name=new JLabel(),size=new JLabel(),actions=new JLabel();
        FileRenderer(){super(new BorderLayout(10,0));setBorder(new EmptyBorder(10,10,10,10));name.setFont(font(Font.PLAIN,14));name.setIconTextGap(10);size.setFont(font(Font.PLAIN,13));JPanel right=new JPanel(new FlowLayout(FlowLayout.RIGHT,12,0));right.setOpaque(false);right.add(size);actions.setIcon(SalesTaskVisuals.icon("more",DeckersPalette.muted(),18));right.add(actions);add(name);add(right,BorderLayout.EAST);}
        @Override public Component getListCellRendererComponent(JList<? extends String> list,String value,int index,boolean selected,boolean focus){int split=value.lastIndexOf("    ");name.setText(split<0?value:value.substring(0,split));size.setText(split<0?"":value.substring(split+4));name.setIcon(SalesTaskVisuals.icon(value.toLowerCase().contains(".pdf")?"pdf":"clip",DeckersPalette.CORAL,24));name.setForeground(DeckersPalette.text());size.setForeground(DeckersPalette.muted());setBackground(selected?DeckersPalette.background():DeckersPalette.surface());return this;}
    }
    private static void field(JPanel panel,String title,JComponent input){GridBagConstraints c=new GridBagConstraints();c.gridy=panel.getComponentCount()/2;c.gridx=0;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(7,0,7,14);panel.add(new JLabel(title),c);c.gridx=1;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;panel.add(input,c);}
    private static void setTreeEnabled(Component component,boolean value){component.setEnabled(value);if(component instanceof Container parent)for(Component child:parent.getComponents())setTreeEnabled(child,value);}
    private void message(String text){JOptionPane.showMessageDialog(this,text,"Sales Tasks",JOptionPane.INFORMATION_MESSAGE);}
    private final class JobRenderer extends JPanel implements ListCellRenderer<JsonObject>{
        private final JLabel number=label("",17),name=label("",17),request=new JLabel(),due=new JLabel();
        private final SalesTaskVisuals.Badge badge=new SalesTaskVisuals.Badge("",DeckersPalette.ORANGE);
        JobRenderer(){setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));JPanel top=new JPanel(new BorderLayout(5,0));top.setAlignmentX(LEFT_ALIGNMENT);top.setOpaque(false);top.add(number,BorderLayout.WEST);top.add(badge,BorderLayout.EAST);add(top);add(Box.createVerticalStrut(4));for(JLabel label:new JLabel[]{name,request,due})label.setAlignmentX(LEFT_ALIGNMENT);add(name);add(Box.createVerticalStrut(5));add(request);add(Box.createVerticalStrut(5));add(due);request.setFont(font(Font.PLAIN,14));due.setFont(font(Font.PLAIN,14));}
        public Component getListCellRendererComponent(JList<? extends JsonObject> list,JsonObject t,int index,boolean selected,boolean focus){
            setBackground(selected?DeckersPalette.sectionFill(DeckersPalette.ORANGE):DeckersPalette.surface());setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,selected?4:1,1,0,selected?DeckersPalette.ORANGE:DeckersPalette.border()),new EmptyBorder(8,12,8,10)));
            String state=stageName(text(t,"status"));if("COMPLETED".equals(text(t,"status")))state="FINALIZED".equals(text(t,"billing_status"))?"Ready for pickup":"Awaiting pricing";number.setText("ST-"+text(t,"task_id"));badge.setText(state);badge.tone(selected?DeckersPalette.ORANGE:statusColor(text(t,"status")));name.setText(text(t,"customer_name"));String work=text(t,"instructions");request.setText(work.length()>37?work.substring(0,35)+"…":work);request.setForeground(DeckersPalette.muted());due.setText((bool(t,"overdue")?"Overdue · ":"")+deadline(t));due.setForeground(bool(t,"overdue")?DeckersPalette.CORAL:bool(t,"due_asap")?DeckersPalette.ORANGE:DeckersPalette.text());setToolTipText("ST-"+text(t,"task_id")+" — "+state+" — "+text(t,"customer_name")+" — "+work+" — "+due.getText());setPreferredSize(new Dimension(280,124));setSize(Math.max(180,list.getWidth()),124);doLayout();for(Component child:getComponents())if(child instanceof Container container)container.doLayout();return this;
        }
    }
    private static final class ScrollablePages extends JPanel implements Scrollable {
        ScrollablePages(){super(new CardLayout());}
        @Override public Color getBackground(){return DeckersPalette.surface();}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 22;}
        public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(22,visible.height-30);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return getParent()!=null&&getPreferredSize().height<getParent().getHeight();}
    }
}
