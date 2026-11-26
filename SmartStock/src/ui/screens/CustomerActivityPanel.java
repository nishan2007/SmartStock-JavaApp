package ui.screens;

import services.LanApiClient;
import ui.design.DeckersPalette;
import ui.helpers.StoreTimeZoneHelper;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/** Compact chronological view of the same ledger used by account details. */
final class CustomerActivityPanel extends JPanel {
    private final DefaultListModel<LanApiClient.CustomerTransactionRecord> model=new DefaultListModel<>();
    private final JLabel status=CustomerAccountsDesign.label("Select a customer to view activity.",14,false);
    private final JList<LanApiClient.CustomerTransactionRecord> list=new JList<>(model);
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("dd MMM yyyy · h:mm a");

    CustomerActivityPanel(Runnable refresh,Runnable details,Runnable payments) {
        super(new BorderLayout(0,10));setOpaque(false);
        JPanel toolbar=CustomerAccountsDesign.panel(new BorderLayout(8,6));
        toolbar.add(status,BorderLayout.NORTH);
        JPanel actions=CustomerAccountsDesign.panel(new FlowLayout(FlowLayout.LEFT,6,0));
        JButton reload=CustomerAccountsDesign.button("Refresh","refresh",false);
        JButton full=CustomerAccountsDesign.button("Details",null,false);
        JButton paid=CustomerAccountsDesign.button("Payments",null,false);
        reload.addActionListener(e->refresh.run());full.addActionListener(e->details.run());paid.addActionListener(e->payments.run());
        actions.add(reload);actions.add(full);actions.add(paid);toolbar.add(actions);
        add(toolbar,BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer((owner,row,index,selected,focus)->render(row,selected));
        add(CustomerAccountsDesign.scroll(list));
    }
    void message(String text) {model.clear();status.setText(text);}
    void showHistory(LanApiClient.CustomerTransactionResult result) {
        model.clear();
        if(result!=null&&result.transactions()!=null)result.transactions().stream()
                .sorted(Comparator.comparingLong(LanApiClient.CustomerTransactionRecord::createdAtEpochMillis).reversed())
                .forEach(model::addElement);
        status.setText(model.isEmpty()?"No transactions or payments yet.":model.size()+" entries · Newest first");
    }
    private Component render(LanApiClient.CustomerTransactionRecord row,boolean selected) {
        JPanel item=CustomerAccountsDesign.panel(new BorderLayout(8,6));item.setOpaque(true);
        item.setBackground(selected?DeckersPalette.tileFill(DeckersPalette.PURPLE):DeckersPalette.surface());
        item.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0,0,1,0,DeckersPalette.border()),new EmptyBorder(12,10,12,10)));
        String type=clean(row.transactionType()).replace('_',' ');
        boolean payment=type.toUpperCase(java.util.Locale.ROOT).contains("PAYMENT");
        JLabel title=CustomerAccountsDesign.label(type.isBlank()?"Transaction":type,15,true);
        JLabel amount=CustomerAccountsDesign.label("G$"+java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(row.amount()==null?0:row.amount()),16,true);
        JPanel heading=CustomerAccountsDesign.panel(new BorderLayout(8,0));heading.add(title);heading.add(amount,BorderLayout.EAST);item.add(heading,BorderLayout.NORTH);
        String document=join(clean(row.documentType()).replace('_',' '),row.documentNumber());
        String reference=payment?join(row.paymentMethod(),row.paymentReference()):clean(row.paymentStatus()).replace('_',' ');
        String meta=join(DATE.format(Instant.ofEpochMilli(row.createdAtEpochMillis()).atZone(StoreTimeZoneHelper.getStoreZone())),row.storeName());
        String note=clean(row.note());
        if(note.length()>160)note=note.substring(0,157)+"…";
        int width=Math.max(180,list.getWidth()-26);
        JLabel description=CustomerAccountsDesign.label("<html><div style='width:"+width+"px'>"+escape(meta)+"<br>"+escape(join(document,reference))
                +(note.isBlank()?"":"<br>"+escape(note))+"</div></html>",13,false);
        item.setToolTipText(clean(row.note()));
        item.add(description);return item;
    }
    private static String clean(String value){return value==null?"":value.trim();}
    private static String join(String a,String b){return clean(a).isBlank()?clean(b):clean(b).isBlank()?clean(a):clean(a)+" · "+clean(b);}
    private static String escape(String value){return clean(value).replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\n","<br>");}
}
