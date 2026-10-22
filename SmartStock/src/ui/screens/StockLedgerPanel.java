package ui.screens;

import com.google.gson.JsonObject;
import managers.PermissionManager;
import managers.SessionManager;
import services.LanApiClient;
import ui.helpers.CustomerChargeAuthorizationDialog;
import ui.helpers.UiTaskRunner;
import ui.helpers.SessionDataCache;
import ui.helpers.ThemeManager;
import ui.design.DeckersSwing;
import ui.design.DeckersPalette;
import utils.CurrencyFormatter;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Noncash stock rows; monetary expenses stay in the existing expense report. */
final class StockLedgerPanel extends JPanel {
    private final String kind;
    private final Runnable changed;
    private final DefaultTableModel model=new DefaultTableModel(new String[]{"Date","Product","Employee","Reason / Category","Quantity","Selling Value","Status","Receiving Reference","Valuation"},0){
        @Override public boolean isCellEditable(int row,int column){return false;}
    };
    private final JTable table=new JTable(model);
    private final TableRowSorter<DefaultTableModel> sorter=new TableRowSorter<>(model);
    private final List<JsonObject> rows=new ArrayList<>();
    private final JTextField product=new JTextField(13),employee=new JTextField(12),reason=new JTextField(13);
    private final JLabel totals=new JLabel(" ");
    private final java.text.NumberFormat currency=CurrencyFormatter.create(Locale.US);
    private BigDecimal monetary=BigDecimal.ZERO;
    private long from,to;
    private int generation;

    StockLedgerPanel(String kind,Runnable changed){
        super(new BorderLayout(8,8));this.kind=kind;this.changed=changed;setOpaque(false);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.add(new JLabel("Product"));actions.add(product);actions.add(new JLabel("Employee"));actions.add(employee);
        actions.add(new JLabel("Reason / Category"));actions.add(reason);
        JButton refresh=new JButton("Refresh"),details=new JButton("Details"),reverse=new JButton("Reverse Entry");
        refresh.addActionListener(e->reload());details.addActionListener(e->details());reverse.addActionListener(e->reverse());
        buttons.add(refresh);buttons.add(details);
        if(kind.equals("LOSS") && PermissionManager.hasPermission("VIEW_RECEIVING_HISTORY")){
            JButton receiving=new JButton("Receiving Record");
            receiving.addActionListener(e->{JsonObject row=selected();if(row!=null)new ReceivingHistory(text(row,"receive_id")).setVisible(true);});
            buttons.add(receiving);
        }
        if(PermissionManager.hasPermission("REVERSE_STOCK_LEDGER"))buttons.add(reverse);
        if(kind.equals("EXPENSE") && PermissionManager.hasPermission("RECORD_STOCK_EXPENSE")){
            JButton record=new JButton("Record Stock Expense");record.addActionListener(e->record());buttons.add(record);
        }
        for(Component component:buttons.getComponents())if(component instanceof JButton b)DeckersSwing.styleUtilityButton(b,DeckersPalette.ORANGE);
        JPanel header=new JPanel(new BorderLayout());header.add(buttons,BorderLayout.NORTH);header.add(actions,BorderLayout.CENTER);header.add(totals,BorderLayout.SOUTH);
        add(header,BorderLayout.NORTH);add(new JScrollPane(table),BorderLayout.CENTER);
        table.setRowSorter(sorter);table.setRowHeight(27);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for(int i=0;i<table.getColumnCount();i++)table.getColumnModel().getColumn(i).setPreferredWidth(i==1?230:160);
        javax.swing.event.DocumentListener listener=new javax.swing.event.DocumentListener(){
            public void insertUpdate(javax.swing.event.DocumentEvent e){filter();}
            public void removeUpdate(javax.swing.event.DocumentEvent e){filter();}
            public void changedUpdate(javax.swing.event.DocumentEvent e){filter();}
        };
        product.getDocument().addDocumentListener(listener);employee.getDocument().addDocumentListener(listener);reason.getDocument().addDocumentListener(listener);
        table.addMouseListener(new java.awt.event.MouseAdapter(){@Override public void mouseClicked(java.awt.event.MouseEvent e){if(e.getClickCount()==2)details();}});
    }

    void setRange(ZonedDateTime start,ZonedDateTime end){from=start.toInstant().toEpochMilli();to=end.toInstant().toEpochMilli();reload();}
    void setMonetaryTotal(BigDecimal amount){monetary=amount;updateTotals();}

    private void reload(){
        if(to<=from)return;
        int request=++generation;JsonObject body=new JsonObject();body.addProperty("fromEpochMillis",from);body.addProperty("toEpochMillis",to);
        UiTaskRunner.submit(SwingUtilities.getWindowAncestor(this),"stock-ledger."+kind,()->LanApiClient.stockLedger("load",body,null),data->{
            if(request!=generation)return;rows.clear();model.setRowCount(0);
            for(var element:data.getAsJsonArray("entries")){
                JsonObject r=element.getAsJsonObject();if(!text(r,"ledger_type").equals(kind))continue;rows.add(r);
                boolean reversal=!text(r,"reversal_of").isEmpty();BigDecimal value=amount(r).multiply(BigDecimal.valueOf(reversal?-1:1));
                model.addRow(new Object[]{Instant.ofEpochMilli(r.get("created_at").getAsLong()).atZone(zone()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        text(r,"item_name"),text(r,"user_name"),text(r,"category"),r.get("quantity").getAsInt()*(reversal?-1:1),text(r,"selling_price").isEmpty()?"Unavailable":value,
                        reversal?"REVERSAL":r.get("reversed").getAsBoolean()?"REVERSED":"POSTED",text(r,"receive_id"),text(r,"valuation_source")});
            }
            filter();
        },e->{if(request==generation){rows.clear();model.setRowCount(0);totals.setText("Ledger unavailable — "+e.getMessage());}});
    }

    private void filter(){
        List<RowFilter<Object,Object>> filters=new ArrayList<>();
        String[] values={product.getText(),employee.getText(),reason.getText()};int[] columns={1,2,3};
        for(int i=0;i<values.length;i++)if(!values[i].isBlank())filters.add(RowFilter.regexFilter("(?i)"+java.util.regex.Pattern.quote(values[i].trim()),columns[i]));
        sorter.setRowFilter(filters.isEmpty()?null:RowFilter.andFilter(filters));updateTotals();
    }

    private void updateTotals(){
        BigDecimal total=BigDecimal.ZERO;
        for(int view=0;view<table.getRowCount();view++){Object value=model.getValueAt(table.convertRowIndexToModel(view),5);if(value instanceof BigDecimal amount)total=total.add(amount);}
        totals.setText(kind.equals("LOSS")?"Loss selling value (noncash; historical prices estimated where marked; unavailable values excluded): "+currency.format(total):
                "Monetary expenses: "+currency.format(monetary)+"   Stock expenses (selling value, noncash): "+currency.format(total)+"   Combined: "+currency.format(monetary.add(total)));
    }

    private JsonObject selected(){int view=table.getSelectedRow();if(view<0){message("Select a ledger entry first.");return null;}return rows.get(table.convertRowIndexToModel(view));}

    private void details(){
        JsonObject row=selected();if(row==null)return;
        JsonObject request=new JsonObject();request.addProperty("entryId",text(row,"entry_id"));
        UiTaskRunner.submit(SwingUtilities.getWindowAncestor(this),"stock-ledger.detail",()->LanApiClient.stockLedger("detail",request,null),data->{
            JPanel panel=new JPanel(new BorderLayout(8,8));
            String summary="Entry: "+text(row,"entry_id")+"\nProduct: "+text(row,"item_name")+"\nEmployee: "+text(row,"user_name")+
                    "\nSystem quantity: "+text(row,"system_quantity")+"\nCounted / resulting quantity: "+text(row,"counted_quantity")+
                    "\nQuantity: "+text(row,"quantity")+"\nSelling price: "+text(row,"selling_price")+"\nSelling value: "+currency.format(amount(row))+
                    "\nUnit cost: "+(text(row,"unit_cost").isEmpty()?"Unavailable or restricted":text(row,"unit_cost"))+
                    "\nValuation: "+text(row,"valuation_source")+"\nSource movement: "+text(row,"source_movement_type")+" #"+text(row,"source_movement_id")+"\nCategory: "+text(row,"category")+"\nDescription / recipient: "+text(row,"description")+"\nReason: "+text(row,"reason")+
                    "\nReceiving reference: "+text(row,"receive_id")+"\nReversal of: "+text(row,"reversal_of");
            JTextArea text=new JTextArea(summary,16,65);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);panel.add(new JScrollPane(text),BorderLayout.CENTER);
            String signature=text(data,"signaturePngBase64");if(!signature.isEmpty()){
                ImageIcon original=new ImageIcon(java.util.Base64.getDecoder().decode(signature));
                panel.add(new JLabel(new ImageIcon(original.getImage().getScaledInstance(600,250,Image.SCALE_SMOOTH))),BorderLayout.SOUTH);
            }
            JOptionPane.showMessageDialog(this,panel,"Ledger Entry",JOptionPane.PLAIN_MESSAGE);
        },e->message(e.getMessage()));
    }

    private void reverse(){
        JsonObject row=selected();if(row==null)return;
        if(!text(row,"reversal_of").isEmpty() || row.get("reversed").getAsBoolean()){message("Choose a posted entry that has not been reversed.");return;}
        String why=JOptionPane.showInputDialog(this,"Reason for reversal (stock will be restored):");if(why==null)return;
        if(why.isBlank() || why.length()>2000){message("Enter a reason up to 2,000 characters.");return;}
        JsonObject body=new JsonObject();body.addProperty("entryId",text(row,"entry_id"));body.addProperty("reason",why.trim());
        submitMutation("reverse",body);
    }

    private void record(){
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),"Record Stock Expense",Dialog.ModalityType.APPLICATION_MODAL);
        JTextField search=new JTextField(30);JButton find=new JButton("Search");
        DefaultListModel<LanApiClient.LookupItem> items=new DefaultListModel<>();JList<LanApiClient.LookupItem> list=new JList<>(items);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> l,Object value,int i,boolean selected,boolean focus){
            var item=(LanApiClient.LookupItem)value;return super.getListCellRendererComponent(l,item.name()+" | "+item.code()+" | Stock: "+item.quantityOnHand()+" | Price: "+currency.format(item.price()),i,selected,focus);
        }});
        find.addActionListener(e->UiTaskRunner.submit(dialog,"stock-expense.search",()->LanApiClient.searchReceivingItems(search.getText()),found->{items.clear();items.addAll(found);},ex->message(ex.getMessage())));
        search.addActionListener(e->find.doClick());
        JSpinner qty=new JSpinner(new SpinnerNumberModel(1,1,1_000_000,1));
        JComboBox<String> category=new JComboBox<>(new String[]{"Company Use","Donation"});
        JTextField description=new JTextField(30);JTextArea why=new JTextArea(3,30);why.setLineWrap(true);why.setWrapStyleWord(true);
        JPanel fields=new JPanel(new GridLayout(0,2,8,8));fields.add(new JLabel("Quantity"));fields.add(qty);fields.add(new JLabel("Category"));fields.add(category);
        fields.add(new JLabel("Recipient / description"));fields.add(description);fields.add(new JLabel("Reason (required)"));fields.add(new JScrollPane(why));
        fields.add(new JLabel("Employee"));fields.add(new JLabel(SessionManager.getCurrentUserDisplayName()));
        JButton post=new JButton("Sign and Record"),cancel=new JButton("Cancel");cancel.addActionListener(e->dialog.dispose());
        post.addActionListener(e->{
            var item=list.getSelectedValue();if(item==null){message("Select a product.");return;}
            int quantity=(Integer)qty.getValue();if(quantity>item.quantityOnHand()){message("There is not enough stock.");return;}
            if(description.getText().isBlank() || why.getText().isBlank() || description.getText().length()>2000 || why.getText().length()>2000){message("Enter a description and reason, each up to 2,000 characters.");return;}
            String signature=CustomerChargeAuthorizationDialog.captureEmployeeSignature(dialog,SessionManager.getCurrentUserDisplayName());if(signature==null)return;
            JsonObject body=new JsonObject();body.addProperty("itemType",item.itemType().toUpperCase(Locale.ROOT).replace(' ','_'));body.addProperty("itemId",item.itemId());
            body.addProperty("quantity",quantity);body.addProperty("category",(String)category.getSelectedItem());body.addProperty("description",description.getText().trim());
            body.addProperty("reason",why.getText().trim());body.addProperty("signaturePngBase64",signature);
            dialog.dispose();submitMutation("post",body);
        });
        JPanel top=new JPanel(new BorderLayout(8,8));top.add(search,BorderLayout.CENTER);top.add(find,BorderLayout.EAST);
        JPanel bottom=new JPanel(new BorderLayout(8,8));bottom.add(fields,BorderLayout.CENTER);JPanel buttons=new JPanel();buttons.add(cancel);buttons.add(post);bottom.add(buttons,BorderLayout.SOUTH);
        JPanel content=new JPanel(new BorderLayout(8,8));content.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));content.add(top,BorderLayout.NORTH);content.add(new JScrollPane(list),BorderLayout.CENTER);content.add(bottom,BorderLayout.SOUTH);
        for(JButton b:List.of(find,post,cancel))DeckersSwing.styleUtilityButton(b,DeckersPalette.ORANGE);
        dialog.setContentPane(content);dialog.setSize(760,600);dialog.setLocationRelativeTo(this);ThemeManager.applyToWindow(dialog);dialog.setVisible(true);
    }

    private void submitMutation(String action,JsonObject body){submitMutation(action,body,UUID.randomUUID().toString());}
    private void submitMutation(String action,JsonObject body,String key){
        UiTaskRunner.submit(SwingUtilities.getWindowAncestor(this),"stock-ledger.mutate",()->LanApiClient.stockLedger(action,body,key),result->{
            SessionDataCache.invalidate("reports:");SessionDataCache.invalidate("inventory-");
            services.InventoryCatalogCache.refreshAfterMutation().exceptionally(e->null);reload();changed.run();
        },e->{
            if(JOptionPane.showConfirmDialog(this,"Could not confirm the result: "+e.getMessage()+"\nRetry this same entry?","Ledger",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)submitMutation(action,body,key);
        });
    }

    private void message(String s){JOptionPane.showMessageDialog(this,s,"Stock Ledger",JOptionPane.WARNING_MESSAGE);}
    private static String text(JsonObject o,String key){return o.has(key)&&!o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    private static BigDecimal amount(JsonObject o){return text(o,"selling_price").isEmpty()?BigDecimal.ZERO:o.get("selling_price").getAsBigDecimal().multiply(o.get("quantity").getAsBigDecimal());}
    private static ZoneId zone(){try{return ZoneId.of(SessionManager.getCurrentLocationTimezone());}catch(Exception e){return ZoneId.systemDefault();}}
}
