package ui.screens.customorders;

import com.google.gson.*;
import ui.helpers.LoyaltyDialog;
import ui.design.DeckersPalette;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Comparator;

/** Order summary and structured content; no parsing of legacy formatted text. */
final class CustomOrderDetailsPanel extends JPanel {
    final JTabbedPane tabs=new JTabbedPane();
    final JPanel itemActions=new JPanel(new OrderDetailsWrapLayout());
    final JPanel paymentActions=new JPanel(new OrderDetailsWrapLayout());
    private final JPanel summary=new JPanel();
    private final JPanel items=column(),activity=column();
    private final DefaultTableModel payments=new DefaultTableModel(new String[]{"Date","Action","Amount","Method","Reference","Cashier","Reason"},0){public boolean isCellEditable(int r,int c){return false;}};
    private final JLabel paymentNotice=new JLabel();
    private long generation;
    private boolean canPay;
    boolean canPay(){return canPay;}
    private final java.util.function.Consumer<Boolean> paymentEnabled;
    interface ProductionUpdater {
        void update(long orderId,long lineId,String status,java.util.function.Consumer<Boolean> completion);
    }
    private ProductionUpdater productionUpdater;
    void setProductionUpdater(ProductionUpdater updater){productionUpdater=updater;}
    CustomOrderDetailsPanel(java.util.function.Consumer<Boolean> paymentEnabled){
        super(new BorderLayout(8,12));this.paymentEnabled=paymentEnabled;setBorder(new EmptyBorder(12,12,12,12));
        setBackground(DeckersPalette.background());summary.setLayout(new BoxLayout(summary,BoxLayout.Y_AXIS));summary.setOpaque(false);add(summary,BorderLayout.NORTH);
        JPanel itemTab=new JPanel(new BorderLayout());itemTab.add(new JScrollPane(items),BorderLayout.CENTER);itemTab.add(itemActions,BorderLayout.SOUTH);
        JTable table=new JTable(payments);table.setRowHeight(30);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i==0?175:125);
        JPanel paymentTab=new JPanel(new BorderLayout(8,8));paymentTab.add(new JScrollPane(table),BorderLayout.CENTER);JPanel foot=new JPanel(new BorderLayout());foot.add(paymentNotice,BorderLayout.NORTH);foot.add(paymentActions,BorderLayout.CENTER);paymentTab.add(foot,BorderLayout.SOUTH);
        tabs.addTab("Items & Notes",itemTab);tabs.addTab("Payments",paymentTab);tabs.addTab("Activity",new JScrollPane(activity));add(tabs,BorderLayout.CENTER);empty();
    }
    long loading(){long token=++generation;summary.removeAll();summary.add(label("Loading order details…",16,true));items.removeAll();activity.removeAll();payments.setRowCount(0);canPay=false;paymentEnabled.accept(false);revalidate();repaint();return token;}
    void empty(){loading();summary.removeAll();summary.add(label("Select an order to view its details",16,false));revalidate();repaint();}
    void failed(long token,String message){if(token!=generation)return;summary.removeAll();summary.add(label("Unable to load order details",16,true));summary.add(label(message,13,false));revalidate();repaint();}
    void showDetails(long token,JsonObject data){if(token!=generation)return;JsonObject s=data.getAsJsonObject("summary");summary.removeAll();
        boolean pending="PENDING".equals(text(s,"pricing_state"));String number=text(s,"order_number");JPanel heading=new JPanel(new BorderLayout(8,0));heading.setOpaque(false);heading.add(label("Order …"+number.substring(Math.max(0,number.length()-4)),22,true),BorderLayout.CENTER);
        JLabel badge=label(human(text(s,"status")),14,true);badge.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DeckersPalette.border()),new EmptyBorder(5,10,5,10)));heading.add(badge,BorderLayout.EAST);heading.setAlignmentX(Component.LEFT_ALIGNMENT);summary.add(heading);
        JButton copy=new JButton("Copy full order number");copy.setToolTipText(number);copy.addActionListener(e->Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(number),null));copy.setAlignmentX(Component.LEFT_ALIGNMENT);summary.add(copy);
        if(!pending && decimal(s,"amount_paid").signum()==0 && decimal(s,"loyalty_discount_amount").signum()==0 && !"CANCELLED".equals(text(s,"status")) && !Boolean.parseBoolean(text(s,"invoice_billed"))) {
            JButton loyalty=new JButton("Redeem loyalty points before first payment");
            loyalty.addActionListener(e->LoyaltyDialog.open(this,Integer.parseInt(text(s,"customer_id")),points->{
                if(points==0||token!=generation)return;JsonObject request=new JsonObject();request.addProperty("action","PREVIEW_ORDER");request.addProperty("orderId",Long.parseLong(text(s,"custom_order_id")));request.addProperty("customerId",Integer.parseInt(text(s,"customer_id")));request.addProperty("points",points);request.addProperty("eligible",decimal(s,"total_amount"));
                new SwingWorker<JsonObject,Void>(){protected JsonObject doInBackground()throws Exception{return services.LanApiClient.loyaltyRead(request);}protected void done(){try{var quote=get().getAsJsonObject("redemption");if(token!=generation)return;if(JOptionPane.showConfirmDialog(CustomOrderDetailsPanel.this,"Use "+points+" points for G$"+quote.get("discount").getAsBigDecimal().toPlainString()+" off order "+number+"?","Redeem points",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
                    String key=java.util.UUID.randomUUID().toString();new SwingWorker<JsonObject,Void>(){protected JsonObject doInBackground()throws Exception{JsonObject mutation=new JsonObject();mutation.addProperty("action","CUSTOM_ORDER_REDEEM");mutation.addProperty("orderId",Long.parseLong(text(s,"custom_order_id")));mutation.addProperty("points",points);services.LanApiClient.loyaltyUpdate(mutation,key);return services.LanApiClient.customOrderWorkflowRead("DETAILS_V2",Long.parseLong(text(s,"custom_order_id")),null).getAsJsonObject("orderDetails");}protected void done(){try{var updated=get();if(token==generation)showDetails(token,updated);}catch(Exception ex){LoyaltyDialog.error(CustomOrderDetailsPanel.this,ex);}}}.execute();
                }catch(Exception ex){LoyaltyDialog.error(CustomOrderDetailsPanel.this,ex);}}}.execute();
            }));summary.add(loyalty);
        }
        summary.add(label(join(text(s,"customer_name"),text(s,"customer_phone")),16,false));summary.add(label("Due: "+fallback(text(s,"due_date"),"Not set")+"     Assigned: "+fallback(text(s,"assigned_to_name"),"Unassigned"),13,false));
        JPanel totals=new JPanel(new GridLayout(1,3,12,0));totals.setOpaque(false);totals.add(metric("Order Total",pending?"Price pending":money(s,"total_amount")));totals.add(metric(pending?"Deposits":"Paid",money(s,"amount_paid")));totals.add(metric("Balance Due",pending?"Price pending":money(s,"balance_due")+(decimal(s,"balance_due").signum()==0?" · Paid":"")));summary.add(Box.createVerticalStrut(10));totals.setAlignmentX(Component.LEFT_ALIGNMENT);summary.add(totals);
        summary.add(label(join(prefix("Payment: ",human(text(s,"payment_method"))),human(text(s,"payment_status")),prefix("Reference: ",text(s,"payment_reference"))),13,false));
        boolean billed=Boolean.parseBoolean(text(s,"invoice_billed"));boolean due=decimal(s,"balance_due").signum()>0;paymentNotice.setText(pending?"Price pending — optional deposits accepted. Finalize pricing to calculate the balance.":billed?"Billed through the linked invoice — record payments in Quotations & Invoices.":due?"Balance due: "+money(s,"balance_due"):"Paid in full");canPay=(pending||due)&&!billed&&!"CANCELLED".equals(text(s,"status"));paymentEnabled.accept(canPay);
        items.removeAll();for(var group:CustomOrderQuantityGroups.group(data)){
            JPanel destination=items;
            if(group.size()>1){
                JsonObject first=group.get(0);JPanel wrapper=column();wrapper.setBorder(new EmptyBorder(8,8,8,8));
                JPanel children=column();children.setVisible(false);
                JButton expand=new JButton(group.size()+" × "+text(first,"item_name")+" — "+(pending?"Price pending":moneyTotal(first,group.size()))+"   ▸");
                expand.setAlignmentX(Component.LEFT_ALIGNMENT);expand.addActionListener(e->{children.setVisible(!children.isVisible());expand.setText(group.size()+" × "+text(first,"item_name")+" — "+(pending?"Price pending":moneyTotal(first,group.size()))+(children.isVisible()?"   ▾":"   ▸"));revalidate();repaint();});
                wrapper.add(expand);addText(wrapper,join(text(first,"variant_name"),prefix("Size: ",text(first,"item_size")),prefix("Colour: ",text(first,"item_color"))));
                addText(wrapper,text(first,"customization_details"));addText(wrapper,prefix("Instructions: ",text(first,"order_instructions")));
                addText(wrapper,"Production: "+human(text(first,"production_status"))+" · Delivery: "+human(text(first,"delivery_status")));
                for(JsonElement element:data.getAsJsonArray("addons")){JsonObject addon=element.getAsJsonObject();if(text(addon,"custom_order_line_id").equals(text(first,"custom_order_line_id")))addText(wrapper,"Add-on: "+join(text(addon,"print_material_name"),text(addon,"print_size_name"),text(addon,"print_description")));}
                wrapper.add(children);items.add(wrapper);items.add(Box.createVerticalStrut(10));destination=children;
            }
            for(JsonObject line:group){JPanel card=column();card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(DeckersPalette.border()),new EmptyBorder(12,12,12,12)));
            JPanel title=new JPanel(new BorderLayout());title.setOpaque(false);title.setAlignmentX(Component.LEFT_ALIGNMENT);title.add(label(text(line,"item_name"),17,true),BorderLayout.CENTER);title.add(label(pending?"Price pending":money(line,"line_total"),17,true),BorderLayout.EAST);card.add(title);
            addText(card,join(text(line,"variant_name"),prefix("Size: ",text(line,"item_size")),prefix("Colour: ",text(line,"item_color"))));
            addProductionControl(card,s,line);
            if(!text(line,"return_status").isBlank()&&!text(line,"return_status").equals("NONE"))addText(card,"Return: "+human(text(line,"return_status"))+" · "+money(line,"returned_amount"));
            addText(card,text(line,"customization_details"));addText(card,prefix("Instructions: ",text(line,"order_instructions")));
            for(JsonElement a:data.getAsJsonArray("addons")){JsonObject addon=a.getAsJsonObject();if(text(addon,"custom_order_line_id").equals(text(line,"custom_order_line_id")))addText(card,"Add-on: "+join(text(addon,"print_material_name"),text(addon,"print_size_name"),text(addon,"print_description"),pending?"Price pending":money(addon,"print_charge")));}destination.add(card);destination.add(Box.createVerticalStrut(10));}}
        if(!text(s,"order_notes").isBlank()){items.add(label("Order Notes",16,true));addText(items,text(s,"order_notes"));}
        for(JsonElement entry:data.getAsJsonArray("authorization")){JsonObject a=entry.getAsJsonObject();items.add(label("Account Authorization",16,true));addText(items,join(text(a,"representative_name"),"true".equals(text(a,"has_signature"))?"Signature captured":!text(a,"signature_purged_at").isBlank()?"Signature image expired":prefix("Unsigned: ",text(a,"unsigned_reason")),date(text(a,"captured_at"))));}
        payments.setRowCount(0);for(JsonElement entry:data.getAsJsonArray("payments")){JsonObject p=entry.getAsJsonObject();payments.addRow(new Object[]{date(text(p,"created_at")),human(text(p,"payment_action"))+(!text(p,"voided_at").isBlank()?" (voided)":""),money(p,"payment_amount"),human(text(p,"payment_method")),text(p,"payment_reference"),text(p,"taken_by_name"),text(p,"void_reason")});}
        activity.removeAll();ArrayList<JsonObject> events=new ArrayList<>();for(JsonElement entry:data.getAsJsonArray("activity"))events.add(entry.getAsJsonObject());for(String group:new String[]{"deliveries","production","returns"})for(JsonElement entry:data.getAsJsonArray(group)){JsonObject source=entry.getAsJsonObject();JsonObject event=source.deepCopy();event.addProperty("created_at",text(source,group.equals("deliveries")?"delivered_at":"created_at"));event.addProperty("action_type",group);event.addProperty("user_name",text(source,group.equals("deliveries")?"delivered_by_name":group.equals("production")?"updated_by_name":"created_by_name"));event.addProperty("reason",text(source,group.equals("deliveries")?"delivery_notes":group.equals("production")?"notes":"reason"));events.add(event);}events.sort(Comparator.comparing((JsonObject e)->text(e,"created_at")).reversed());
        for(JsonObject e:events){JPanel card=column();card.setBorder(new EmptyBorder(8,4,8,4));addText(card,date(text(e,"created_at"))+" · "+eventTitle(e));JToggleButton expand=new JToggleButton("Show details");JTextArea raw=readable(join(prefix("Field: ",text(e,"field_name")),prefix("Before: ",text(e,"old_value")),prefix("After: ",text(e,"new_value")),prefix("Reason: ",text(e,"reason")),prefix("Item: ",text(e,"item_name")),prefix("Device: ",text(e,"device_name")),prefix("Return: ",text(e,"return_type")),prefix("Restock: ",text(e,"restock_action"))));raw.setVisible(false);expand.addActionListener(a->{raw.setVisible(expand.isSelected());expand.setText(expand.isSelected()?"Hide details":"Show details");card.revalidate();});card.add(expand);card.add(raw);activity.add(card);}
        if(events.isEmpty())activity.add(label("No activity recorded.",14,false));revalidate();repaint();
    }
    static String eventTitle(JsonObject e){String actor=fallback(text(e,"user_name"),"System"),action=text(e,"action_type");return switch(action){case "deliveries"->actor+" delivered "+text(e,"item_name")+prefix(" from ",text(e,"device_name"));case "production"->actor+" updated "+text(e,"item_name")+" to "+human(text(e,"new_status"));case "returns"->actor+" returned "+text(e,"item_name")+" · "+money(e,"refund_amount");case "CREATE"->actor+" created the order";case "STATUS_CHANGE"->actor+" changed order status to "+human(text(e,"new_value"));case "CUSTOMER_CALL"->actor+" recorded "+human(text(e,"new_value"));case "LINE_DELIVERY"->actor+" delivered order lines";case "PAYMENT"->actor+" applied a payment";default->actor+" · "+human(action);};}
    static String date(String value){try{return DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a",Locale.ENGLISH).withZone(ZoneId.systemDefault()).format(Instant.parse(value));}catch(Exception e){return value;}}
    private static final class ContentColumn extends JPanel implements Scrollable {
        public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}
        public Dimension getPreferredScrollableViewportSize(){return new Dimension(500,400);}
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 24;}
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(24,r.height-24);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }
    private static JPanel column(){JPanel p=new ContentColumn();p.setAlignmentX(Component.LEFT_ALIGNMENT);p.setLayout(new BoxLayout(p,BoxLayout.Y_AXIS));p.setBackground(DeckersPalette.surface());return p;}
    private void addProductionControl(JPanel card,JsonObject order,JsonObject line) {
        String current=fallback(text(line,"production_status"),"NOT_STARTED");
        if(productionUpdater==null||java.util.List.of("CANCELLED","DELIVERED").contains(text(order,"status"))
                ||"DELIVERED".equals(text(line,"delivery_status"))||"FULL".equals(text(line,"return_status"))){
            addText(card,"Production: "+human(current)+"     Delivery: "+fallback(human(text(line,"delivery_status")),"Pending"));return;
        }
        JPanel row=new JPanel(new OrderDetailsWrapLayout());row.setOpaque(false);row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.add(label("Production:",14,false));
        JComboBox<String> selector=new JComboBox<>(new String[]{"NOT_STARTED","STARTED","WAITING_FOR_APPROVAL","DESIGN_APPROVED","PRINTED","FINISHED","QUALITY_CHECKED","READY"});
        selector.setRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){return super.getListCellRendererComponent(list,human(String.valueOf(value)),index,selected,focus);}});
        if(!"NOT_STARTED".equals(current))selector.removeItem("NOT_STARTED");
        selector.setSelectedItem(current);selector.setPreferredSize(new Dimension(170,32));
        row.add(selector);row.add(label("Delivery: "+fallback(human(text(line,"delivery_status")),"Pending"),14,false));card.add(row);
        long token=generation;
        selector.addActionListener(e->{
            String status=(String)selector.getSelectedItem();
            if(current.equals(status))return;
            if("NOT_STARTED".equals(status)){selector.setSelectedItem(current);return;}
            selector.setEnabled(false);
            productionUpdater.update(Long.parseLong(text(order,"custom_order_id")),Long.parseLong(text(line,"custom_order_line_id")),status,success->{
                if(token!=generation)return;
                if(!success){selector.setSelectedItem(current);selector.setEnabled(true);}
            });
        });
    }
    private static JLabel label(String value,int size,boolean bold){JLabel l=new JLabel(value);l.setAlignmentX(Component.LEFT_ALIGNMENT);l.setFont(new Font("SansSerif",bold?Font.BOLD:Font.PLAIN,size));l.setForeground(DeckersPalette.text());return l;}
    private static JPanel metric(String title,String value){JPanel p=column();p.setBorder(new EmptyBorder(8,10,8,10));p.add(label(title,12,false));p.add(label(value,22,true));return p;}
    private static JTextArea readable(String value){JTextArea a=new JTextArea(value){public Dimension getPreferredSize(){FontMetrics fm=getFontMetrics(getFont());int width=getParent()!=null&&getParent().getWidth()>40?getParent().getWidth()-24:500;int rows=0;for(String line:getText().split("\\n",-1))rows+=Math.max(1,(int)Math.ceil(fm.stringWidth(line)/(double)Math.max(1,width)));return new Dimension(width,rows*fm.getHeight()+8);}public Dimension getMaximumSize(){return new Dimension(Integer.MAX_VALUE,getPreferredSize().height);}};a.setAlignmentX(Component.LEFT_ALIGNMENT);a.setEditable(false);a.setLineWrap(true);a.setWrapStyleWord(true);a.setFont(new Font("SansSerif",Font.PLAIN,14));a.setBackground(DeckersPalette.surface());a.setForeground(DeckersPalette.text());a.setBorder(new EmptyBorder(4,0,4,0));return a;}
    private static void addText(JPanel panel,String value){if(!value.isBlank())panel.add(readable(value));}
    static String text(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    private static String prefix(String prefix,String value){return value.isBlank()?"":prefix+value;}
    private static String fallback(String value,String fallback){return value.isBlank()?fallback:value;}
    private static String join(String...parts){return String.join(" · ",java.util.Arrays.stream(parts).filter(p->!p.isBlank()).toList());}
    private static String human(String value){String lower=value.toLowerCase(Locale.ROOT).replace('_',' ');return lower.isBlank()?"":Character.toUpperCase(lower.charAt(0))+lower.substring(1);}
    private static BigDecimal decimal(JsonObject o,String key){try{return new BigDecimal(text(o,key));}catch(Exception e){return BigDecimal.ZERO;}}
    private static String moneyTotal(JsonObject line,int count){return "$"+new java.text.DecimalFormat("#,##0.##").format(decimal(line,"line_total").multiply(BigDecimal.valueOf(count)));}
    private static String money(JsonObject o,String key){return "$"+new java.text.DecimalFormat("#,##0.##").format(decimal(o,key));}
}
