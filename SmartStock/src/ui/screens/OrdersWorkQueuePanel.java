package ui.screens;

import services.LanApiClient;
import services.OrdersWorkQueueService.Row;
import ui.design.DeckersPalette;
import ui.design.DeckersLogoManager;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.*;
import java.awt.*;
import java.awt.event.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;

/** Live, store-scoped production board; cards open the existing authoritative editors. */
public final class OrdersWorkQueuePanel extends JPanel {
    private static final Color NAVY=new Color(10,19,77), BLUE=new Color(179,224,255), ORANGE=new Color(255,207,160), GREEN=new Color(166,239,197);
    private static final Font FACE=loadFont();
    final JTextField search=new JTextField(){
        protected void paintComponent(Graphics graphics){super.paintComponent(graphics);if(getText().isEmpty()&&!hasFocus()){Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);g.setColor(DeckersPalette.dark()?DeckersPalette.muted():new Color(65,87,130));g.setFont(getFont());g.drawString("Search job #, customer or phone...",0,(getHeight()-g.getFontMetrics().getHeight())/2+g.getFontMetrics().getAscent());g.dispose();}}
    };
    final JComboBox<String> type=new QueueComboBox(new String[]{"All work types","Custom orders","Sales tasks"});
    final JComboBox<String> staff=new QueueComboBox(new String[]{"Assigned to: All staff"});
    final JToggleButton mine=new JToggleButton("My work");
    final JComboBox<String> deadline=new QueueComboBox(new String[]{"All deadlines","Due today","Overdue","ASAP","No deadline"});
    final JComboBox<String> sort=new QueueComboBox(new String[]{"Sort: Due soonest","Sort: Newest first","Sort: Customer"});
    private final JLabel summary=label("Loading active jobs…",14,false);
    private final JPanel board=transparent(new BoardLayout());
    private final JFrame owner;
    private final Consumer<Row> open;
    private final boolean display;
    private List<Row> rows=List.of();
    private ZoneId zone=ZoneId.systemDefault();
    private boolean updatingStaff,loading;
    private Row selected;
    private final Map<String,ImageIcon> images=new HashMap<>();
    private final Set<String> requestedImages=new HashSet<>();

    public OrdersWorkQueuePanel(JFrame owner,Consumer<Row> open) { this(owner,open,false); }
    public OrdersWorkQueuePanel(JFrame owner,Consumer<Row> open,boolean display) {
        super(new BorderLayout());
        this.display=display;this.owner=owner;this.open=Objects.requireNonNull(open);
        setBackground(background());preserve(this);
        JPanel controls=new ResponsiveControls();controls.setOpaque(false);controls.setBorder(new EmptyBorder(10,16,14,16));
        JPanel searchBox=transparent(new BorderLayout(12,0));searchBox.setBackground(DeckersPalette.surface());searchBox.setOpaque(true);
        searchBox.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(line(BLUE)),new EmptyBorder(0,14,0,10)));
        searchBox.add(new JLabel(SalesTaskVisuals.icon("search",ink(),19)),BorderLayout.WEST);
        search.setBorder(new EmptyBorder(0,0,0,0));search.setFont(font(Font.PLAIN,16));search.setForeground(ink());search.setOpaque(false);
        search.putClientProperty("JTextField.placeholderText","Search job #, customer or phone...");
        search.setToolTipText("Search job #, customer or phone");search.getAccessibleContext().setAccessibleName("Search job number, customer or phone");
        searchBox.add(search);searchBox.setPreferredSize(new Dimension(350,37));
        JButton refresh=SalesTaskVisuals.button("Refresh","refresh",true);refresh.setPreferredSize(new Dimension(112,37));refresh.addActionListener(e->refresh());
        JComponent[] fields={searchBox,type,staff,mine,deadline,refresh,sort};double[] weights={2.1,1,1.25,.65,1,0,.65};
        for(int i=0;i<fields.length;i++){
            JComponent field=fields[i];preserve(field);field.setFont(font(Font.PLAIN,15));field.setForeground(i==5?Color.WHITE:ink());
            if(field instanceof JComboBox<?>){field.setBackground(DeckersPalette.surface());field.setPreferredSize(new Dimension(i==6?150:190,37));}
            GridBagConstraints c=new GridBagConstraints();c.gridx=i;c.weightx=weights[i];c.fill=GridBagConstraints.HORIZONTAL;c.insets=new Insets(0,i==0?0:12,0,0);controls.add(field,c);
        }
        sort.setBorder(new EmptyBorder(0,0,0,0));sort.setFont(font(Font.PLAIN,13));
        mine.setIcon(new ToggleIcon(false));mine.setSelectedIcon(new ToggleIcon(true));mine.setContentAreaFilled(false);mine.setBorder(new EmptyBorder(0,0,0,0));mine.setPreferredSize(new Dimension(125,37));
        type.getAccessibleContext().setAccessibleName("Work type");staff.getAccessibleContext().setAccessibleName("Assigned staff");deadline.getAccessibleContext().setAccessibleName("Deadline");sort.getAccessibleContext().setAccessibleName("Sort jobs");
        add(controls,BorderLayout.NORTH);board.setBorder(new EmptyBorder(0,16,8,16));add(board,BorderLayout.CENTER);
        summary.setIcon(SalesTaskVisuals.icon("info",new Color(95,122,158),17));summary.setIconTextGap(10);
        summary.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1,0,0,0,line(BLUE)),new EmptyBorder(11,22,12,16)));add(summary,BorderLayout.SOUTH);
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){render();}public void removeUpdate(DocumentEvent e){render();}public void changedUpdate(DocumentEvent e){render();}});
        for(JComboBox<String> box:List.of(type,staff,deadline,sort))box.addActionListener(e->{if(!updatingStaff)render();});mine.addActionListener(e->render());
        if(display) { mine.setVisible(false); summary.setText("Read-only display"); }
        if(owner!=null && !display)owner.addWindowListener(new WindowAdapter(){@Override public void windowActivated(WindowEvent e){if(isShowing())refresh();}});
        render();
    }
    public void refresh(){
        if(display)return;
        if(loading)return;loading=true;summary.setText("Refreshing active jobs…");
        UiTaskRunner.submit(owner,"orders.work-queue",LanApiClient::loadOrdersWorkQueue,
            result->{loading=false;setSnapshot(result.rows(),ZoneId.of(result.timezone()));},
            error->{loading=false;summary.setText("Unable to refresh work: "+error.getMessage()+" — use Refresh to try again.");});
    }
    void setSnapshot(List<Row> values,ZoneId timezone){
        rows=List.copyOf(values);if(display && rows.isEmpty()){images.clear();requestedImages.clear();}zone=timezone;Object previous=staff.getSelectedItem();updatingStaff=true;
        try{staff.removeAllItems();staff.addItem("Assigned to: All staff");staff.addItem("Unassigned");rows.stream().map(Row::assigned).filter(s->s!=null&&!s.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).forEach(staff::addItem);
            for(int i=0;i<staff.getItemCount();i++)if(Objects.equals(previous,staff.getItemAt(i)))staff.setSelectedIndex(i);
        }finally{updatingStaff=false;}render();loadImages();
    }
    private void loadImages(){
        if(owner==null)return;
        for(Row row:rows){String reference=row.imageReference();if(reference==null||reference.isBlank()||!requestedImages.add(reference))continue;
            UiTaskRunner.submit(owner,"queue-image-"+reference,()->{byte[] bytes=display?LanApiClient.downloadDisplayImage(reference):LanApiClient.downloadImageAsset(reference);var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));return image==null?null:new ImageIcon(DeckersLogoManager.scaleToFit(image,100,100));},icon->{if(icon!=null && (!display || rows.stream().anyMatch(r->reference.equals(r.imageReference())))){images.put(reference,icon);render();}},error->{});
        }
    }
    private void render(){
        if(updatingStaff)return;
        List<Row> filtered=rows.stream().filter(r->matches(r,search.getText(),(String)type.getSelectedItem(),"All pending"))
            .filter(r->matchesAssignment(r,(String)staff.getSelectedItem(),mine.isSelected(),managers.SessionManager.getCurrentUserDisplayName()))
            .filter(r->matchesDeadline(r,(String)deadline.getSelectedItem(),LocalDate.now(zone),zone)).sorted(comparator((String)sort.getSelectedItem())).toList();
        java.util.List<Integer> positions=new ArrayList<>();
        if(display)for(Component lane:board.getComponents())if(lane instanceof Container container)for(Component child:container.getComponents())if(child instanceof JScrollPane pane)positions.add(pane.getVerticalScrollBar().getValue());
        board.removeAll();board.add(lane("New orders",BLUE,"orders",filtered,0));board.add(lane("In progress",ORANGE,"gear",filtered,1));board.add(lane("Ready",GREEN,"check-circle",filtered,2));
        summary.setText(filtered.size()+ (display ? " active jobs  •  Read-only display" : " active jobs  •  Open a card to view its order or sales task."));board.revalidate();board.repaint();
        if(display)SwingUtilities.invokeLater(()->{int i=0;for(Component lane:board.getComponents())if(lane instanceof Container container)for(Component child:container.getComponents())if(child instanceof JScrollPane pane && i<positions.size())pane.getVerticalScrollBar().setValue(positions.get(i++));});
    }
    private JPanel lane(String title,Color tone,String symbol,List<Row> filtered,int stage){
        List<Row> jobs=filtered.stream().filter(r->lane(r)==stage).toList();
        JPanel lane=new RoundedPanel(new BorderLayout(),tone,.10);lane.setBorder(new EmptyBorder(0,7,7,7));lane.putClientProperty("queueJobCount",jobs.size());
        JPanel heading=transparent(new FlowLayout(FlowLayout.LEFT,12,8));heading.setPreferredSize(new Dimension(200,49));
        heading.add(new JLabel(SalesTaskVisuals.icon(symbol,stage==1?DeckersPalette.ORANGE:stage==2?new Color(0,133,87):ink(),26)));
        JLabel name=label(title+" ("+jobs.size()+")",21,true);if(stage==1)name.setForeground(DeckersPalette.ORANGE);heading.add(name);lane.add(heading,BorderLayout.NORTH);
        JPanel cards=new CardsPanel(stage==2?1:Math.max(1,jobs.size()));for(Row row:jobs)cards.add(card(row,tone));
        if(jobs.isEmpty()){JLabel empty=label("No "+title.toLowerCase(Locale.ROOT),16,false);empty.setHorizontalAlignment(SwingConstants.CENTER);empty.setBorder(new EmptyBorder(30,8,30,8));cards.add(empty);}
        JScrollPane scroll=new JScrollPane(cards);scroll.setBorder(null);scroll.setOpaque(false);scroll.getViewport().setOpaque(false);scroll.getVerticalScrollBar().setUnitIncrement(24);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);lane.add(scroll);return lane;
    }
    private JPanel card(Row row,Color tone){
        JPanel card=new RoundedPanel(new BorderLayout(8,0),tone,0);card.setBorder(new EmptyBorder(12,16,2,12));card.putClientProperty("row",row);
        card.setFocusable(true);card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.getAccessibleContext().setAccessibleName(row.number()+", "+row.customer()+", "+row.stage());
        card.setToolTipText("<html>"+escape(row.number()+" • "+row.customer())+"<br>"+escape(safe(row.instructions())).replace("\n","<br>")+"</html>");
        JPanel body=transparent(new GridBagLayout());GridBagConstraints c=new GridBagConstraints();c.gridx=0;c.gridy=0;c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;c.anchor=GridBagConstraints.WEST;
        JPanel top=transparent(new BorderLayout(8,3));JLabel number=label(row.number(),row.number().length()>18?19:22,true);number.setToolTipText(row.number());top.add(number,BorderLayout.CENTER);JPanel kind=transparent(new FlowLayout(FlowLayout.LEFT,0,0));kind.add(badge("SALES_TASK".equals(row.kind())?"Sales task":"Custom order","SALES_TASK".equals(row.kind())?new Color(55,155,255):new Color(157,101,255)));top.add(kind,row.number().length()>15?BorderLayout.SOUTH:BorderLayout.EAST);body.add(top,c);
        c.gridy++;body.add(imageSafeLabel(safe(row.customer()).isBlank()?"Walk-in customer":row.customer(),18,true,row),c);
        c.gridy++;JLabel instructions=imageSafeLabel(firstLine(row.instructions()),16,false,row);instructions.setPreferredSize(new Dimension(80,24));body.add(instructions,c);
        c.gridy++;c.insets=new Insets(4,0,0,0);boolean ready=lane(row)==2;String due=ready?"Ready for pickup":due(row);
        JComponent dueLabel=ready?badge(due,GREEN):row.overdue()?badge(due,new Color(255,128,128)):label(due,16,false);
        if(!ready&&(row.asap()||row.dueEpochMillis()!=null&&Instant.ofEpochMilli(row.dueEpochMillis()).atZone(zone).toLocalDate().equals(LocalDate.now(zone))))dueLabel.setForeground(new Color(255,55,0));
        body.add(detail("calendar",dueLabel),c);
        c.gridy++;c.insets=new Insets(4,0,0,0);
        JComponent assigned=safe(row.assigned()).isBlank()?badge("Unassigned",new Color(255,216,119)):label(row.assigned(),16,false);
        String money=row.pricePending()?"Price pending":row.balance()==null?"Balance unavailable":row.balance().signum()<=0?"Paid":"Balance G$"+NumberFormat.getNumberInstance(Locale.US).format(row.balance());
        JLabel payment=badge(money,row.pricePending()?new Color(255,216,119):row.balance()!=null&&row.balance().signum()<=0?GREEN:ORANGE);
        if(lane(row)==1&&!row.partPaid()&&!row.designApproved()){JPanel bottom=transparent(new FlowLayout(FlowLayout.LEFT,12,0));bottom.add(assigned);bottom.add(payment);body.add(detail("person",bottom),c);}
        else{JPanel assignment=transparent(new FlowLayout(FlowLayout.LEFT,8,0));assignment.add(assigned);if(row.designApproved())assignment.add(badge("Design approved",BLUE));body.add(detail("person",assignment),c);c.gridy++;JPanel payments=transparent(new FlowLayout(FlowLayout.LEFT,8,0));if(row.partPaid()){JLabel partial=badge("Part paid",ORANGE);partial.setForeground(new Color(255,63,0));payments.add(partial);}payments.add(payment);body.add(detail("coins",payments),c);}
        GridBagConstraints glue=new GridBagConstraints();glue.gridx=0;glue.gridy=c.gridy+1;glue.weighty=1;glue.fill=GridBagConstraints.VERTICAL;body.add(Box.createVerticalGlue(),glue);
        card.add(body);
        
        if(display){card.setCursor(Cursor.getDefaultCursor());card.setFocusable(false);return card;}
        Runnable activate=()->{selected=row;board.repaint();open.accept(row);};
        MouseAdapter mouse=new MouseAdapter(){public void mouseClicked(MouseEvent e){if(SwingUtilities.isLeftMouseButton(e))activate.run();}public void mouseEntered(MouseEvent e){card.putClientProperty("hover",true);card.repaint();}public void mouseExited(MouseEvent e){card.putClientProperty("hover",false);card.repaint();}};installMouse(card,mouse);
        card.addFocusListener(new FocusAdapter(){public void focusGained(FocusEvent e){card.putClientProperty("hover",true);card.repaint();}public void focusLost(FocusEvent e){card.putClientProperty("hover",false);card.repaint();}});
        card.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"open");card.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0),"open");card.getActionMap().put("open",new AbstractAction(){public void actionPerformed(ActionEvent e){activate.run();}});return card;
    }
    private static void installMouse(Component component,MouseListener listener){component.addMouseListener(listener);if(component instanceof Container container)for(Component child:container.getComponents())installMouse(child,listener);}
    private JLabel imageSafeLabel(String text,int size,boolean bold,Row row){JLabel label=new JLabel(text){protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();if(images.containsKey(row.imageReference()))g.clipRect(0,0,Math.max(0,getWidth()-108),getHeight());super.paintComponent(g);g.dispose();}};label.setFont(font(bold?Font.BOLD:Font.PLAIN,size));label.setForeground(ink());preserve(label);return label;}
    private static JPanel detail(String icon,JComponent content){JPanel p=transparent(new BorderLayout(12,0));JLabel glyph=new JLabel(SalesTaskVisuals.icon(icon,ink(),19));glyph.setPreferredSize(new Dimension(22,24));p.add(glyph,BorderLayout.WEST);JPanel text=transparent(new FlowLayout(FlowLayout.LEFT,0,0));text.add(content);p.add(text);return p;}
    private static JLabel badge(String text,Color tone){JLabel b=new JLabel(text){protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);g.setColor(DeckersPalette.blend(DeckersPalette.surface(),tone,.30));g.fillRoundRect(0,0,getWidth(),getHeight(),7,7);g.dispose();super.paintComponent(graphics);}};b.setFont(font(Font.BOLD,15));b.setForeground(ink());preserve(b);b.setBorder(new EmptyBorder(3,12,3,12));return b;}
    private String due(Row row){if(row.asap())return "ASAP";if(row.dueEpochMillis()==null)return "No deadline";var date=Instant.ofEpochMilli(row.dueEpochMillis()).atZone(zone);String value=date.format(DateTimeFormatter.ofPattern("d MMM yyyy",Locale.ENGLISH));if(row.overdue())return "OVERDUE - "+value;if(date.toLocalDate().equals(LocalDate.now(zone)))return row.dateOnly()?"Due today":"Due today "+date.format(DateTimeFormatter.ofPattern("h:mm a",Locale.ENGLISH));return "Due "+value;}
    static int lane(Row row){return "Ready for pickup".equals(row.stage())?2:Set.of("NEW","RECEIVED").contains(row.status())?0:1;}
    static Comparator<Row> comparator(String sort){if("Sort: Newest first".equals(sort))return Comparator.comparingLong(Row::createdEpochMillis).reversed().thenComparing(Row::number);if("Sort: Customer".equals(sort))return Comparator.comparing(r->safe(r.customer()),String.CASE_INSENSITIVE_ORDER);return Comparator.comparing(Row::asap).reversed().thenComparing(Row::dueEpochMillis,Comparator.nullsLast(Long::compareTo)).thenComparingLong(Row::createdEpochMillis).thenComparing(Row::number);}
    static boolean matchesAssignment(Row row,String staff,boolean mine,String user){String assigned=safe(row.assigned());if(mine&&(safe(user).isBlank()||!assigned.equalsIgnoreCase(user)))return false;return staff==null||staff.startsWith("Assigned to:")||("Unassigned".equals(staff)?assigned.isBlank():assigned.equals(staff));}
    static boolean matchesDeadline(Row row,String choice,LocalDate today,ZoneId zone){return switch(safe(choice)){case "ASAP"->row.asap();case "Overdue"->row.overdue();case "No deadline"->!row.asap()&&row.dueEpochMillis()==null;case "Due today"->row.asap()||row.dueEpochMillis()!=null&&Instant.ofEpochMilli(row.dueEpochMillis()).atZone(zone).toLocalDate().equals(today);default->true;};}
    static boolean sameJob(Row a,Row b){return a.id()==b.id()&&a.kind().equals(b.kind());}
    static boolean matches(Row row,String query,String type,String stage){
        if(("Custom Orders".equals(type)||"Custom orders".equals(type))&&!"CUSTOM_ORDER".equals(row.kind()))return false;
        if(("Sales Tasks".equals(type)||"Sales tasks".equals(type))&&!"SALES_TASK".equals(row.kind()))return false;
        if("Overdue".equals(stage)&&!row.overdue())return false;
        if(List.of("Ready for pickup","Awaiting pricing").contains(safe(stage))&&!row.stage().equals(stage))return false;
        if("To do".equals(stage)&&List.of("Ready for pickup","Awaiting pricing").contains(row.stage()))return false;
        return (row.type()+" "+row.number()+" "+safe(row.customer())+" "+safe(row.phone())+" "+safe(row.assigned())+" "+safe(row.instructions())).toLowerCase(Locale.ROOT).contains(safe(query).trim().toLowerCase(Locale.ROOT));
    }
    private static String safe(String value){return value==null?"":value;}
    private static String firstLine(String value){return safe(value).lines().filter(s->!s.isBlank()).findFirst().orElse("No work instructions");}
    private static String escape(String value){return safe(value).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    static Font font(int style,int size){return FACE.deriveFont(style,(float)size);}
    private static Font loadFont(){try(var in=OrdersWorkQueuePanel.class.getResourceAsStream("/Images/orders-roboto-condensed.ttf")){return Font.createFont(Font.TRUETYPE_FONT,in);}catch(Exception e){return new Font("Segoe UI",Font.PLAIN,16);}}
    static Color ink(){return DeckersPalette.dark()?DeckersPalette.text():NAVY;}
    private static Color background(){return DeckersPalette.dark()?DeckersPalette.background():new Color(247,252,255);}
    private static Color line(Color tone){return DeckersPalette.blend(DeckersPalette.surface(),tone,.6);}
    static JLabel label(String value,int size,boolean bold){JLabel label=new JLabel(value);label.setForeground(ink());label.setFont(font(bold?Font.BOLD:Font.PLAIN,size));preserve(label);return label;}
    private static JPanel transparent(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);preserve(p);return p;}
    private static void preserve(JComponent c){c.putClientProperty("SmartStock.preserveThemeColors",true);}
    private final class RoundedPanel extends JPanel {
        final Color tone;final double tint;
        RoundedPanel(LayoutManager layout,Color tone,double tint){super(layout);this.tone=tone;this.tint=tint;setOpaque(false);preserve(this);}
        protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);g.setPaint(tint>0?new GradientPaint(0,0,DeckersPalette.blend(DeckersPalette.surface(),tone,.40f),0,Math.min(180,getHeight()),DeckersPalette.blend(DeckersPalette.surface(),tone,tint)):DeckersPalette.surface());g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,8,8);Object r=getClientProperty("row");boolean active=Boolean.TRUE.equals(getClientProperty("hover"))||r instanceof Row row&&selected!=null&&sameJob(row,selected);if(r instanceof Row job){ImageIcon picture=images.get(job.imageReference());if(picture!=null)picture.paintIcon(this,g,getWidth()-picture.getIconWidth()-12,28);}g.setColor(active?DeckersPalette.ORANGE:line(tone));g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,8,8);g.dispose();super.paintComponent(graphics);}
    }
    private static final class ToggleIcon implements Icon {
        final boolean on;ToggleIcon(boolean on){this.on=on;}public int getIconWidth(){return 44;}public int getIconHeight(){return 25;}
        public void paintIcon(Component c,Graphics graphics,int x,int y){Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);g.setColor(on?DeckersPalette.ORANGE:new Color(135,158,186));g.fillRoundRect(x,y,43,25,25,25);g.setColor(Color.WHITE);g.fillOval(x+(on?22:3),y+3,19,19);g.dispose();}
    }
    private static final class BoardLayout implements LayoutManager {
        public void addLayoutComponent(String n,Component c){}public void removeLayoutComponent(Component c){}
        public Dimension preferredLayoutSize(Container c){return new Dimension(1600,710);}public Dimension minimumLayoutSize(Container c){return new Dimension(900,300);}
        public void layoutContainer(Container c){
            Insets in=c.getInsets();int count=c.getComponentCount();
            int width=Math.max(0,c.getWidth()-in.left-in.right-Math.max(0,count-1)*8);
            int[] jobs=new int[count];for(int i=0;i<count;i++)jobs[i]=(Integer)((JComponent)c.getComponent(i)).getClientProperty("queueJobCount");
            int[] widths=laneWidths(width,jobs);int x=in.left;
            for(int i=0;i<count;i++){c.getComponent(i).setBounds(x,in.top,widths[i],Math.max(0,c.getHeight()-in.top-in.bottom));x+=widths[i]+8;}
        }
    }
    /** Reserve one compact pickup column; share production space by filtered workload. */
    static int[] laneWidths(int available,int... jobs){
        int[] result=new int[jobs.length];if(jobs.length==0)return result;
        if(jobs.length==3){int ready=Math.min(310,Math.max(0,available)/3);int[] production=laneWidths(Math.max(0,available)-ready,jobs[0],jobs[1]);return new int[]{production[0],production[1],ready};}
        int base=Math.min(270,Math.max(0,available)/jobs.length),spare=Math.max(0,available)-base*jobs.length;
        long total=0;for(int count:jobs)total+=Math.max(0,count);
        double[] fractions=new double[jobs.length];int used=0;
        for(int i=0;i<jobs.length;i++){double share=total==0?(double)spare/jobs.length:(double)spare*Math.max(0,jobs[i])/total;int extra=(int)share;result[i]=base+extra;fractions[i]=share-extra;used+=result[i];}
        for(int left=Math.max(0,available)-used;left>0;left--){int best=0;for(int i=1;i<jobs.length;i++)if(fractions[i]>fractions[best])best=i;result[best]++;fractions[best]=-1;}
        return result;
    }
    private static final class ResponsiveControls extends JPanel {
        ResponsiveControls(){super(null);}
        public Dimension getPreferredSize(){return new Dimension(1000,getWidth()>0&&getWidth()<1350?106:61);}
        public void doLayout(){
            Insets in=getInsets();int width=getWidth()-in.left-in.right;
            if(getWidth()>=1350){int[] x={0,471,706,1007,1153,1388,1507},w={459,223,281,134,221,112,132};double scale=width/1640.0;for(int i=0;i<getComponentCount();i++)getComponent(i).setBounds(in.left+(int)Math.round(x[i]*scale),in.top,(int)Math.round(w[i]*scale),37);}
            else{int searchWidth=width-261;getComponent(0).setBounds(in.left,in.top,searchWidth,37);getComponent(3).setBounds(in.left+searchWidth+12,in.top,125,37);getComponent(5).setBounds(in.left+width-112,in.top,112,37);int space=width-36;int[] indices={1,2,4,6};double[] ratios={.22,.30,.25,.23};int x=in.left;for(int i=0;i<indices.length;i++){int w=(int)(space*ratios[i]);getComponent(indices[i]).setBounds(x,in.top+45,w,37);x+=w+12;}}
        }
    }
    private static final class QueueComboBox extends JComboBox<String> {
        QueueComboBox(String[] items){super(items);setOpaque(false);setUI(new javax.swing.plaf.basic.BasicComboBoxUI(){
            {squareButton=false;}
            protected JButton createArrowButton(){JButton b=new JButton(SalesTaskVisuals.icon("down",ink(),12));b.setBorder(new EmptyBorder(0,4,0,8));b.setContentAreaFilled(false);return b;}
            public void paintCurrentValueBackground(Graphics g,Rectangle bounds,boolean focus){}
        });setBorder(new javax.swing.border.AbstractBorder(){public Insets getBorderInsets(Component c){return new Insets(1,10,1,6);}public Insets getBorderInsets(Component c,Insets in){in.set(1,10,1,6);return in;}public void paintBorder(Component c,Graphics graphics,int x,int y,int w,int h){Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);g.setColor(line(BLUE));g.drawRoundRect(x,y,w-1,h-1,7,7);g.dispose();}});
        setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){JLabel label=(JLabel)super.getListCellRendererComponent(list,value,index,selected,focus);label.setFont(font(Font.PLAIN,value!=null&&value.toString().startsWith("Sort:")?13:15));label.setForeground(ink());label.setBackground(selected?DeckersPalette.blend(DeckersPalette.surface(),BLUE,.35):DeckersPalette.surface());return label;}});}
        protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);g.setColor(DeckersPalette.surface());g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,7,7);g.dispose();super.paintComponent(graphics);}
    }
    private static final class CardsPanel extends JPanel implements Scrollable {
        CardsPanel(int columns){super(new CardGrid(columns));setOpaque(false);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 24;}
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(24,r.height-24);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }
    private static final class CardGrid implements LayoutManager {
        final int maxColumns;CardGrid(int columns){maxColumns=columns;}
        public void addLayoutComponent(String n,Component c){}public void removeLayoutComponent(Component c){}
        int columns(Container c){return Math.max(1,Math.min(maxColumns,(c.getWidth()+8)/258));}
        public Dimension preferredLayoutSize(Container c){int cols=columns(c),rows=(c.getComponentCount()+cols-1)/cols,height=0;for(int r=0;r<rows;r++)height+=cardHeight(c,r)+8;return new Dimension(280,Math.max(0,height-8));}
        int cardHeight(Container c,int row){int height=193,cols=columns(c);for(int i=row*cols;i<Math.min(c.getComponentCount(),(row+1)*cols);i++)height=Math.max(height,c.getComponent(i).getPreferredSize().height);return height;}
        public Dimension minimumLayoutSize(Container c){return new Dimension(260,160);}
        public void layoutContainer(Container c){int cols=columns(c),width=(c.getWidth()-(cols-1)*8)/cols,y=0;for(int row=0;row*cols<c.getComponentCount();row++){int height=cardHeight(c,row);for(int col=0;col<cols&&row*cols+col<c.getComponentCount();col++)c.getComponent(row*cols+col).setBounds(col*(width+8),y,width,height);y+=height+8;}}
    }
}
