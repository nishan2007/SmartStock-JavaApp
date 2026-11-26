package ui.screens;

import managers.PermissionManager;
import services.LanApiClient;
import ui.components.AppMenuBar;
import ui.components.SearchableComboBox;
import ui.helpers.SessionDataCache;
import ui.helpers.StoreTimeZoneHelper;
import ui.helpers.UiTaskRunner;
import ui.helpers.WindowHelper;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import ui.design.DeckersPalette;
import javax.swing.border.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Records minutes already sent. No receipt and no telecom network integration. */
public final class PhoneMinutes extends JFrame {
    private final SearchableComboBox customer=new SearchableComboBox(){@Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);SalesTaskVisuals.icon("search",DeckersPalette.muted(),20).paintIcon(this,graphics,14,(getHeight()-20)/2);}};
    private final java.util.Map<String,String> customerPhones=new java.util.HashMap<>();
    private final java.util.Map<String,Integer> customerIds=new java.util.LinkedHashMap<>();
    private static final String WALK_IN="Walk-in (cash only)";
    private final JTextField phone=new JTextField(18),amount=new JTextField(10),search=new JTextField(22){@Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);SalesTaskVisuals.icon("search",DeckersPalette.muted(),19).paintIcon(this,graphics,14,(getHeight()-19)/2);if(getText().isEmpty()&&!hasFocus()){graphics.setColor(DeckersPalette.muted());graphics.setFont(getFont());graphics.drawString("Search customer, phone or reference",44,(getHeight()-graphics.getFontMetrics().getHeight())/2+graphics.getFontMetrics().getAscent());}}};
    private final JLabel destinationLabel = label("Destination phone (required for walk-ins)", 17, true);
    private final JTextField from=new JTextField(10),to=new JTextField(10);
    private final JComboBox<String> historyMethod=new JComboBox<>(new String[]{"All","CASH","CREDIT"});
    private String loadedFilter;
    private final JComboBox<String> method=new JComboBox<>(new String[]{"CREDIT","CASH"});
    private final JButton save=new PrimaryButton("Record Minutes"),refresh=new JButton("Search / Refresh"),more=new JButton("Load Older"),reverse=new JButton("Reverse Selected");
    private final JLabel summary=new JLabel(" "),accountSummary=new JLabel("Select a customer, or choose Cash for a walk-in."),transactionDetails=new JLabel(" ");
    private final java.util.Map<Integer,LanApiClient.CustomerAccountRecord> customerRecords=new java.util.HashMap<>();
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"Reference","Date / Time","Customer","Destination phone","Amount (GYD)","Due (GYD)","Method"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final JTable table=new JTable(model);
    private final List<LanApiClient.PhoneMinutesRecord> rows=new ArrayList<>();
    private final Integer fixedCustomer;
    private String pendingKey,pendingFingerprint,reverseKey,reverseFingerprint;
    private boolean busy;
    private final Gateway gateway;
    public PhoneMinutes(Integer customerId) { this(customerId,new Gateway() {
        public List<LanApiClient.CustomerAccountRecord> customers()throws Exception{return LanApiClient.loadCustomerAccountRecords();}
        public List<LanApiClient.PhoneMinutesRecord> history(Integer customer,String search,int offset,String from,String to,String method)throws Exception{return LanApiClient.loadPhoneMinutes(customer,search,offset,from,to,method);}
        public void save(Integer customer,String phone,BigDecimal amount,String method,String key)throws Exception{LanApiClient.savePhoneMinutes(customer,phone,amount,method,key);}
        public void reverse(long id,String reason,String key)throws Exception{LanApiClient.reversePhoneMinutes(id,reason,key);}
    }); }
    interface Gateway {
        List<LanApiClient.CustomerAccountRecord> customers()throws Exception;
        List<LanApiClient.PhoneMinutesRecord> history(Integer customer,String search,int offset,String from,String to,String method)throws Exception;
        void save(Integer customer,String phone,BigDecimal amount,String method,String key)throws Exception;
        void reverse(long id,String reason,String key)throws Exception;
    }
    PhoneMinutes(Integer customerId,Gateway gateway) {
        this.gateway=gateway;
        this.fixedCustomer=customerId;
        setTitle("Phone Minutes");setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(1200,700);
        setJMenuBar(AppMenuBar.create(this,"PhoneMinutes"));
        buildWorkspace();
        save.setEnabled(false);more.setEnabled(false);save.addActionListener(e->save());refresh.addActionListener(e->load(false));search.addActionListener(e->load(false));more.addActionListener(e->load(true));reverse.addActionListener(e->reverse());
        WindowHelper.configurePosWindow(this);
        customer.setBorder(new CompoundBorder(new LineBorder(DeckersPalette.border(),1,true),new EmptyBorder(0,42,0,8)));if(customer.getEditor().getEditorComponent() instanceof JTextField editor){editor.setBorder(new EmptyBorder(0,0,0,0));editor.setFont(font(Font.PLAIN,19));}
        getJMenuBar().setPreferredSize(new Dimension(100,52));for(Component menu:getJMenuBar().getComponents())if(menu instanceof JMenu)menu.setFont(font(Font.PLAIN,16));
        UiTaskRunner.submit(this,"phone-minutes.customers",gateway::customers,accounts->{
            List<String> options=new ArrayList<>();options.add(WALK_IN);String selected=WALK_IN;
            for(var a:accounts)if(a.active()){
                String account=a.accountNumber()==null||a.accountNumber().isBlank()?"#"+a.customerId():a.accountNumber();
                String label=a.name()+" ("+account+")";
                if(customerIds.containsKey(label))label+=" (#"+a.customerId()+")";
                customerIds.put(label,a.customerId());customerPhones.put(label,a.phone()==null?"":a.phone());customerRecords.put(a.customerId(),a);options.add(label);if(java.util.Objects.equals(fixedCustomer,a.customerId()))selected=label;
            }
            customer.setOptions(options);customer.setSelectedItem(selected);customer.setEnabled(fixedCustomer==null);updateAccountSummary();
            save.setEnabled(true);load(false);},ex->error(ex));
    }
    private static Font font(int style,int size){return new Font("Segoe UI",style,size);}
    private static JPanel panel(LayoutManager layout){JPanel p=new JPanel(layout);p.setBackground(DeckersPalette.surface());p.putClientProperty("SmartStock.preserveBackground",true);return p;}
    private static JLabel label(String text,int size,boolean bold){JLabel label=new JLabel(text);label.setFont(font(bold?Font.BOLD:Font.PLAIN,size));label.setForeground(DeckersPalette.text());return label;}
    private static void field(JComponent field){field.setFont(font(Font.PLAIN,19));field.setPreferredSize(new Dimension(200,46));field.putClientProperty("SmartStock.preserveThemeColors",true);field.setBackground(DeckersPalette.fieldBackground());field.setForeground(DeckersPalette.text());field.setBorder(new CompoundBorder(new LineBorder(DeckersPalette.border(),1,true),new EmptyBorder(8,12,8,12)));}
    private static void button(AbstractButton b,boolean primary){b.setFont(font(Font.BOLD,17));b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));b.putClientProperty("SmartStock.preserveThemeColors",true);b.setForeground(primary?Color.WHITE:DeckersPalette.text());b.setBackground(primary?DeckersPalette.CORAL:DeckersPalette.surface());b.setOpaque(true);b.setBorder(new CompoundBorder(new LineBorder(primary?DeckersPalette.CORAL:DeckersPalette.border(),1,true),new EmptyBorder(12,20,12,20)));b.setFocusPainted(true);}
    private JPanel heading(String title){JPanel p=panel(new BorderLayout(0,6));p.add(label(title,28,true),BorderLayout.NORTH);JPanel line=panel(new FlowLayout(FlowLayout.LEFT,0,0));JSeparator accent=new JSeparator();accent.setForeground(DeckersPalette.PURPLE);accent.setBackground(DeckersPalette.PURPLE);accent.setPreferredSize(new Dimension(72,3));line.add(accent);p.add(line,BorderLayout.SOUTH);return p;}
    private void buildWorkspace(){
        setTitle("SmartStock Phone Minutes");phone.setName("minutesPhone");amount.setName("minutesAmount");
        JPanel root=panel(new BorderLayout());root.putClientProperty("SmartStock.preserveThemeColors",true);setContentPane(root);
        JPanel banner=panel(new BorderLayout(18,0));banner.setBackground(DeckersPalette.background());banner.setBorder(new EmptyBorder(20,20,20,20));banner.setPreferredSize(new Dimension(100,120));
        JLabel icon=new JLabel(SalesTaskVisuals.icon("phone",Color.WHITE,30),SwingConstants.CENTER);icon.setOpaque(true);icon.setBackground(DeckersPalette.PURPLE);icon.setPreferredSize(new Dimension(50,50));icon.putClientProperty("SmartStock.preserveThemeColors",true);JPanel iconBox=panel(new GridBagLayout());iconBox.setOpaque(false);iconBox.add(icon);banner.add(iconBox,BorderLayout.WEST);
        JPanel titles=panel(new GridLayout(2,1,0,2));titles.setOpaque(false);titles.add(label("Phone Minutes",34,true));JLabel subtitle=label("Record minutes already sent.",19,false);subtitle.setForeground(DeckersPalette.muted());titles.add(subtitle);banner.add(titles);if(PermissionManager.hasPermission("DEVICE_MANAGEMENT")){JButton web=new JButton("Phone web app");button(web,false);web.setFont(font(Font.PLAIN,13));web.addActionListener(e->new EmployeeWebDialog(this,"minutes").setVisible(true));JPanel webBox=panel(new GridBagLayout());webBox.setOpaque(false);webBox.add(web);banner.add(webBox,BorderLayout.EAST);}root.add(banner,BorderLayout.NORTH);
        JPanel workspace=panel(new GridBagLayout());GridBagConstraints layout=new GridBagConstraints();layout.fill=GridBagConstraints.BOTH;layout.weighty=1;layout.weightx=.35;layout.gridx=0;
        JPanel left=panel(new BorderLayout(0,24));left.setBorder(new CompoundBorder(new MatteBorder(0,0,0,1,DeckersPalette.border()),new EmptyBorder(8,20,30,24)));left.add(heading("New entry"),BorderLayout.NORTH);left.addComponentListener(new java.awt.event.ComponentAdapter(){@Override public void componentResized(java.awt.event.ComponentEvent e){updateAccountSummary();}});
        JPanel form=new EntryForm();GridBagConstraints g=new GridBagConstraints();g.gridx=0;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;g.anchor=GridBagConstraints.NORTHWEST;int row=0;
        customer.setSearchTerms(option->option+" "+customerPhones.getOrDefault(option,""));customer.setToolTipText("Type a name, account or phone; select a matching customer.");field(customer);customer.setMaximumRowCount(6);
        customer.addActionListener(e->SwingUtilities.invokeLater(this::updateAccountSummary));
        g.gridy=row++;g.insets=new Insets(0,0,12,0);form.add(label("Find customer — name, account or phone *",16,true),g);g.gridy=row++;g.insets=new Insets(0,0,22,0);form.add(customer,g);
        accountSummary.setFont(font(Font.PLAIN,13));accountSummary.setOpaque(true);accountSummary.setBackground(DeckersPalette.sectionFill(DeckersPalette.PURPLE));accountSummary.setForeground(DeckersPalette.text());accountSummary.setBorder(new EmptyBorder(16,10,16,10));accountSummary.putClientProperty("SmartStock.preserveThemeColors",true);g.gridy=row++;g.insets=new Insets(0,0,30,0);form.add(accountSummary,g);
        for(Object[] item:new Object[][]{{destinationLabel,phone},{label("Amount (GYD) *",17,true),amount}}){g.gridy=row++;g.insets=new Insets(0,0,12,0);form.add((JLabel)item[0],g);field((JComponent)item[1]);g.gridy=row++;g.insets=new Insets(0,0,25,0);form.add((Component)item[1],g);}
        g.gridy=row++;g.insets=new Insets(0,0,14,0);form.add(label("Payment *",17,true),g);
        JPanel payments=panel(new GridLayout(1,2,0,0));JToggleButton cash=new JToggleButton("Cash",SalesTaskVisuals.icon("coins",DeckersPalette.muted(),24)),credit=new JToggleButton("Credit",SalesTaskVisuals.icon("payment",DeckersPalette.PURPLE,24));ButtonGroup group=new ButtonGroup();group.add(cash);group.add(credit);credit.setSelected(true);for(var toggle:new JToggleButton[]{cash,credit}){button(toggle,false);toggle.setIconTextGap(12);payments.add(toggle);toggle.addActionListener(e->{method.setSelectedItem(toggle==cash?"CASH":"CREDIT");stylePayment(cash,cash.isSelected());stylePayment(credit,credit.isSelected());});}stylePayment(cash,false);stylePayment(credit,true);g.gridy=row++;g.insets=new Insets(0,0,28,0);form.add(payments,g);
        JLabel info=label("<html>Minutes credit has no limit. Merchandise credit is separate.</html>",14,false);info.setIcon(SalesTaskVisuals.icon("info",DeckersPalette.PURPLE,20));info.setIconTextGap(10);g.gridy=row++;g.insets=new Insets(0,0,46,0);form.add(info,g);
        button(save,true);save.setFont(font(Font.BOLD,22));save.setBorder(new EmptyBorder(12,20,12,20));save.setPreferredSize(new Dimension(200,54));g.gridy=row++;g.insets=new Insets(0,0,0,0);form.add(save,g);g.gridy=row;g.weighty=1;form.add(panel(new BorderLayout()),g);JScrollPane entryScroll=new JScrollPane(form);entryScroll.setBorder(new EmptyBorder(0,0,0,0));entryScroll.setViewportBorder(new EmptyBorder(0,0,0,0));entryScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);entryScroll.getVerticalScrollBar().setUnitIncrement(18);left.add(entryScroll,BorderLayout.CENTER);left.setMinimumSize(new Dimension(370,100));workspace.add(left,layout);
        layout.gridx=1;layout.weightx=.65;JPanel history=panel(new BorderLayout(0,28));history.setBorder(new EmptyBorder(8,22,30,22));JPanel top=panel(new BorderLayout(0,20));top.add(heading("Transaction history"),BorderLayout.NORTH);
        JPanel filters=panel(new BorderLayout(0,10));field(search);search.setToolTipText("Search customer, phone or reference");search.putClientProperty("JTextField.placeholderText","Search customer, phone or reference");search.setBorder(new CompoundBorder(new LineBorder(DeckersPalette.border(),1,true),new EmptyBorder(9,42,9,14)));filters.add(search,BorderLayout.NORTH);
        JPanel range=panel(new FlowLayout(FlowLayout.LEFT,10,0));range.add(label("Date range:",15,false));
        JButton dateRange=new JButton("All dates",SalesTaskVisuals.icon("calendar",DeckersPalette.muted(),18));button(dateRange,false);dateRange.setFont(font(Font.PLAIN,15));dateRange.setIconTextGap(12);dateRange.setPreferredSize(new Dimension(220,40));dateRange.addActionListener(e->{String oldFrom=from.getText(),oldTo=to.getText();JPanel dates=panel(new GridLayout(2,2,8,10));dates.add(label("From (YYYY-MM-DD)",14,false));dates.add(from);dates.add(label("To (blank for all)",14,false));dates.add(to);if(JOptionPane.showConfirmDialog(this,dates,"History date range",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION){dateRange.setText(from.getText().isBlank()&&to.getText().isBlank()?"All dates":from.getText().equals(to.getText())?from.getText():"Custom range");load(false);}else{from.setText(oldFrom);to.setText(oldTo);}});range.add(dateRange);
        ButtonGroup filterGroup=new ButtonGroup();List<JToggleButton> filterButtons=new ArrayList<>();for(String type:new String[]{"All","Credit","Cash"}){JToggleButton b=new JToggleButton(type);button(b,false);b.setFont(font(Font.PLAIN,15));b.setPreferredSize(new Dimension(96,40));filterGroup.add(b);filterButtons.add(b);b.setSelected(type.equals("All"));stylePayment(b,b.isSelected());b.addActionListener(e->{historyMethod.setSelectedItem(type.equals("All")?"All":type.toUpperCase(java.util.Locale.ROOT));for(var filterButton:filterButtons)stylePayment(filterButton,filterButton.isSelected());load(false);});range.add(b);}button(refresh,false);refresh.setText("Refresh");refresh.setFont(font(Font.PLAIN,12));refresh.setBorder(new EmptyBorder(8,4,8,4));range.add(refresh);filters.add(range,BorderLayout.SOUTH);top.add(filters);history.add(top,BorderLayout.NORTH);
        table.setAutoCreateRowSorter(true);table.setRowHeight(46);table.setFont(font(Font.PLAIN,15));table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setSelectionBackground(DeckersPalette.sectionFill(DeckersPalette.PURPLE));table.setSelectionForeground(DeckersPalette.text());table.setGridColor(DeckersPalette.border());table.setShowVerticalLines(false);table.setShowHorizontalLines(true);table.setIntercellSpacing(new Dimension(0,1));table.getTableHeader().setFont(font(Font.BOLD,14));table.getTableHeader().setPreferredSize(new Dimension(600,52));table.getTableHeader().setBackground(DeckersPalette.background());table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);int[] widths={100,170,170,160,95,95,90};for(int i=0;i<widths.length;i++)table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.setDefaultRenderer(Object.class,new javax.swing.table.DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int column){super.getTableCellRendererComponent(t,value,selected,focus,row,column);selected=t.isRowSelected(row);setBorder(selected&&column==0?new CompoundBorder(new MatteBorder(0,3,0,0,DeckersPalette.PURPLE),new EmptyBorder(0,7,0,8)):new EmptyBorder(0,10,0,8));setBackground(selected?DeckersPalette.blend(DeckersPalette.surface(),DeckersPalette.PURPLE,.10):row%2==0?DeckersPalette.surface():DeckersPalette.tableStripe());setForeground(column==6?("CASH".equals(String.valueOf(value))?new Color(0,128,64):DeckersPalette.PURPLE):DeckersPalette.text());setFont(font(column==6?Font.BOLD:Font.PLAIN,16));setHorizontalAlignment(column==4||column==5?SwingConstants.RIGHT:SwingConstants.LEFT);if(value instanceof BigDecimal decimal)setText(String.format(java.util.Locale.US,"%,.0f",decimal));var entry=rows.get(t.convertRowIndexToModel(row));setToolTipText(entry.reference()+" · "+entry.method()+(entry.phone().isBlank()?"":" · "+entry.phone())+(entry.reversed()?" · Reversed: "+entry.reversalReason():""));if(column==6&&entry.reversed()){setText("Reversed");setForeground(DeckersPalette.muted());}return this;}});
        table.getSelectionModel().addListSelectionListener(e->{int selected=table.getSelectedRow();if(selected<0)return;var r=rows.get(table.convertRowIndexToModel(selected));transactionDetails.setText(r.reference()+" · "+r.method()+" · "+r.userName()+" · "+r.storeName()+" · "+(r.reversed()?"Reversed: "+r.reversalReason():"Paid GYD"+r.paid()));});
        table.getTableHeader().setDefaultRenderer(new javax.swing.table.DefaultTableCellRenderer(){@Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int column){super.getTableCellRendererComponent(t,value,selected,focus,row,column);setBackground(DeckersPalette.background());setForeground(DeckersPalette.text());setFont(font(Font.BOLD,15));setBorder(new EmptyBorder(8,10,8,8));setHorizontalAlignment(column>=4?SwingConstants.CENTER:SwingConstants.LEFT);if(column==4)setText("<html>Amount<br>(GYD)</html>");if(column==5)setText("<html>Due<br>(GYD)</html>");return this;}});
        JScrollPane scroll=new JScrollPane(table);scroll.getViewport().setBackground(DeckersPalette.surface());scroll.setBorder(new LineBorder(DeckersPalette.border(),1,true));scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter(){@Override public void componentResized(java.awt.event.ComponentEvent e){int mode=scroll.getViewport().getWidth()<830?JTable.AUTO_RESIZE_OFF:JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS;if(table.getAutoResizeMode()!=mode){table.setAutoResizeMode(mode);for(int i=0;i<widths.length;i++)table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);}}});history.add(scroll,BorderLayout.CENTER);
        JPanel footer=panel(new BorderLayout(0,12));JPanel actions=panel(new BorderLayout());button(more,false);more.setText("Load older transactions");more.setIcon(SalesTaskVisuals.icon("receive",DeckersPalette.PURPLE,16));more.setForeground(DeckersPalette.PURPLE);more.setBorder(new EmptyBorder(10,0,10,0));actions.add(more,BorderLayout.WEST);button(reverse,false);reverse.setText("Reverse selected");reverse.setIcon(SalesTaskVisuals.icon("refresh",DeckersPalette.muted(),19));actions.add(reverse,BorderLayout.EAST);footer.add(actions,BorderLayout.NORTH);JPanel notes=panel(new GridLayout(2,1,0,6));summary.setFont(font(Font.PLAIN,11));summary.setForeground(DeckersPalette.muted());transactionDetails.setFont(font(Font.PLAIN,11));transactionDetails.setForeground(DeckersPalette.muted());notes.add(transactionDetails);notes.add(summary);footer.add(notes,BorderLayout.SOUTH);history.add(footer,BorderLayout.SOUTH);history.setMinimumSize(new Dimension(600,100));JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,history);split.setBorder(null);split.setDividerSize(1);split.setResizeWeight(.365);split.addComponentListener(new java.awt.event.ComponentAdapter(){@Override public void componentResized(java.awt.event.ComponentEvent e){split.setDividerLocation((int)(split.getWidth()*.365));}});root.add(split,BorderLayout.CENTER);
    }
    private static void stylePayment(JToggleButton b,boolean selected){b.setBackground(selected?DeckersPalette.sectionFill(DeckersPalette.PURPLE):DeckersPalette.surface());b.setForeground(selected?DeckersPalette.PURPLE:DeckersPalette.muted());b.setBorder(new CompoundBorder(new LineBorder(selected?DeckersPalette.PURPLE:DeckersPalette.border(),1,true),new EmptyBorder(12,20,12,20)));}
    private void updateAccountSummary(){Integer id=customerIds.get(String.valueOf(customer.getEditor().getItem()).trim());var a=customerRecords.get(id);destinationLabel.setText(a==null?"Destination phone (required for walk-ins)":"Destination phone (optional)");if(a==null){accountSummary.setText("Select a customer, or choose Cash for a walk-in.");return;}accountSummary.setText("<html>Account: <b>"+html(a.accountNumber())+"</b> &nbsp; | &nbsp; Total owed: <b>"+gyd(a.totalDue())+"</b>"+(accountSummary.getWidth()>0&&accountSummary.getWidth()<440?"<br>":" &nbsp; | &nbsp; ")+"Minutes due: <b>"+gyd(a.minutesDue())+"</b></html>");}
    private static String html(String value){return value==null?"":value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    private static String gyd(BigDecimal value){return "GYD"+String.format(java.util.Locale.US,"%,.0f",value==null?BigDecimal.ZERO:value);}

    /** Large bold action text meets the large-text contrast threshold on the shared coral. */
    private static final class EntryForm extends JPanel implements Scrollable {
        EntryForm(){super(new GridBagLayout());setBackground(DeckersPalette.surface());putClientProperty("SmartStock.preserveBackground",true);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 18;}
        public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return Math.max(18,visible.height-18);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }
    private static final class PrimaryButton extends JButton {
        PrimaryButton(String text){super(text);putClientProperty("SmartStock.customPaintedButton",true);}
        @Override public void setOpaque(boolean value){super.setOpaque(false);}
        @Override public void setContentAreaFilled(boolean value){super.setContentAreaFilled(false);}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);g.setColor(getModel().isPressed()?DeckersPalette.CORAL.darker():DeckersPalette.CORAL);g.fillRoundRect(0,0,getWidth(),getHeight(),12,12);g.dispose();super.paintComponent(graphics);}
    }
    private void save() {
        if(busy)return;
        String selected=String.valueOf(customer.getEditor().getItem()).trim();Integer id=customerIds.get(selected);
        if(!selected.isBlank()&&!WALK_IN.equals(selected)&&id==null){JOptionPane.showMessageDialog(this,"Select a matching customer from the search results.");return;}
        if(fixedCustomer!=null&&!fixedCustomer.equals(id)){JOptionPane.showMessageDialog(this,"This customer account is unavailable. Refresh the account before recording minutes.");return;}
        String enteredPhone=phone.getText().trim();
        String number=enteredPhone.replaceAll("[ ()-]", ""),type=(String)method.getSelectedItem();BigDecimal value;
        try{value=new BigDecimal(amount.getText().trim().replace(",",""));if(value.signum()<=0||value.stripTrailingZeros().scale()>0)throw new Exception();}catch(Exception e){JOptionPane.showMessageDialog(this,"Enter a positive whole-GYD amount.");return;}
        if("CREDIT".equals(type)&&id==null){JOptionPane.showMessageDialog(this,"Select a customer for credit minutes.");return;}
        if ((id == null || !enteredPhone.isBlank()) && !number.matches("\\+?[0-9]{7,15}")) {
            JOptionPane.showMessageDialog(this, "Enter a destination phone number with 7 to 15 digits.");
            phone.requestFocusInWindow();
            return;
        }
        String fingerprint=id+"|"+number+"|"+value+"|"+type;if(!fingerprint.equals(pendingFingerprint)){pendingKey=UUID.randomUUID().toString();pendingFingerprint=fingerprint;}String key=pendingKey;
        busy=true;save.setEnabled(false);
        UiTaskRunner.submit(this,"phone-minutes.save",()->{gateway.save(id,number,value,type,key);return true;},done->{busy=false;save.setEnabled(true);pendingKey=null;pendingFingerprint=null;phone.setText("");amount.setText("");changed();JOptionPane.showMessageDialog(this,"Minutes recorded.");load(false);},ex->{busy=false;save.setEnabled(true);error(ex);});
    }
    private void load(boolean append) {
        if(busy)return;busy=true;refresh.setEnabled(false);more.setEnabled(false);String query=search.getText().trim(),start=from.getText().trim(),end=to.getText().trim(),filter=historyMethod.getSelectedIndex()==0?"":(String)historyMethod.getSelectedItem();String filterKey=query+"|"+start+"|"+end+"|"+filter;if(append&&!filterKey.equals(loadedFilter)){busy=false;refresh.setEnabled(true);JOptionPane.showMessageDialog(this,"Refresh after changing search filters.");return;}loadedFilter=filterKey;int offset=append?rows.size():0;
        UiTaskRunner.submit(this,"phone-minutes.history",()->gateway.history(fixedCustomer,query,offset,start,end,filter),result->{if(!append){rows.clear();model.setRowCount(0);}rows.addAll(result);var format=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(StoreTimeZoneHelper.getStoreZone());for(var r:result)model.addRow(new Object[]{r.reference(),format.format(Instant.ofEpochMilli(r.createdAtEpochMillis())),r.customerName(),r.phone(),r.amount(),r.balanceDue(),r.method()});BigDecimal cash=BigDecimal.ZERO,credit=BigDecimal.ZERO,due=BigDecimal.ZERO;for(var item:rows)if(!item.reversed()){if("CASH".equals(item.method()))cash=cash.add(item.amount());else credit=credit.add(item.amount());due=due.add(item.balanceDue());}summary.setText(rows.size()+" entries · Cash "+cash+" · Credit "+credit+" · Due "+due+" GYD");busy=false;refresh.setEnabled(true);more.setEnabled(result.size()==1000);},ex->{busy=false;refresh.setEnabled(true);error(ex);});
    }
    private void reverse() {
        if(busy)return;int index=table.getSelectedRow();if(index<0){JOptionPane.showMessageDialog(this,"Select a minutes sale to reverse.");return;}var row=rows.get(table.convertRowIndexToModel(index));if(row.reversed()){JOptionPane.showMessageDialog(this,"This sale is already reversed.");return;}
        String reason=JOptionPane.showInputDialog(this,"Reason for reversing "+row.reference()+(row.phone().isBlank()?"":" to "+row.phone())+" ("+row.amount()+" GYD):");if(reason==null||reason.isBlank())return;
        String fp=row.minutesId()+"|"+reason;if(!fp.equals(reverseFingerprint)){reverseFingerprint=fp;reverseKey=UUID.randomUUID().toString();}String key=reverseKey;busy=true;reverse.setEnabled(false);
        UiTaskRunner.submit(this,"phone-minutes.reverse",()->{gateway.reverse(row.minutesId(),reason,key);return true;},done->{busy=false;reverse.setEnabled(true);reverseKey=null;reverseFingerprint=null;changed();JOptionPane.showMessageDialog(this,"Minutes reversed."+("CREDIT".equals(row.method())&&row.paid().signum()>0?" Paid amounts are available through APPLY_CREDIT in customer payments.":""));load(false);},ex->{busy=false;reverse.setEnabled(true);error(ex);});
    }
    private void changed(){UiTaskRunner.submit(this,"phone-minutes.balance-refresh",gateway::customers,accounts->{for(var a:accounts)customerRecords.put(a.customerId(),a);updateAccountSummary();},this::error);SessionDataCache.invalidate("customer-accounts:");SessionDataCache.invalidate("customer-account:");}
    private void error(Throwable ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Phone Minutes",JOptionPane.ERROR_MESSAGE);}
}
