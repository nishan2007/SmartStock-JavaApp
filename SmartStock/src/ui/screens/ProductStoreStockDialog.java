package ui.screens;

import services.*;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.UUID;

/** Administrators stay on the current server while the owning store applies the edit. */
final class ProductStoreStockDialog extends JDialog {
    private final int product;
    private final DefaultTableModel model=new DefaultTableModel(new Object[]{"Store","Quantity","Source","Updated"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
    private final JTable table=new JTable(model);private final JLabel status=new JLabel("Loading…");
    private final JButton edit=new JButton("Edit selected store stock");
    private List<ProductMergeService.StoreStock> stores=List.of();
    ProductStoreStockDialog(Window owner,int product) {
        super(owner,"Stock by store — item #"+product,ModalityType.APPLICATION_MODAL);this.product=product;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(850,420);setLocationRelativeTo(owner);
        JPanel body=new JPanel(new BorderLayout(10,10));body.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));setContentPane(body);
        body.add(new JLabel("Stock edits are applied by the selected store. Offline requests remain queued until confirmed."),BorderLayout.NORTH);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setRowHeight(28);body.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.add(status,BorderLayout.NORTH);JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refresh=new JButton("Refresh"),commands=new JButton("Check requests");buttons.add(commands);buttons.add(refresh);buttons.add(edit);footer.add(buttons,BorderLayout.SOUTH);body.add(footer,BorderLayout.SOUTH);
        refresh.addActionListener(e->load());edit.addActionListener(e->edit());commands.addActionListener(e->UiTaskRunner.submit(this,"stock.status",LanApiClient::productMergeStatus,result->{
            DefaultTableModel requests=new DefaultTableModel(new Object[]{"Store","Requested quantity","State","Actual before request"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
            for(var entry:result.getAsJsonArray("stockCommands")){var q=entry.getAsJsonObject();String name=stores.stream().filter(s->s.locationId()==q.get("location_id").getAsInt()).map(ProductMergeService.StoreStock::name).findFirst().orElse("Store "+q.get("location_id"));requests.addRow(new Object[]{name,q.get("new_quantity").getAsInt(),q.get("state").getAsString(),q.get("actualQuantity").isJsonNull()?"Awaiting confirmation":q.get("actualQuantity").getAsInt()});}
            JScrollPane pane=new JScrollPane(new JTable(requests));pane.setPreferredSize(new Dimension(680,300));JOptionPane.showMessageDialog(this,pane,"Stock change requests",JOptionPane.INFORMATION_MESSAGE);
        },error->status.setText(error.getMessage())));
        SwingUtilities.invokeLater(this::load);
    }
    private void load(){edit.setEnabled(false);UiTaskRunner.submit(this,"stock.load",()->LanApiClient.productStoreStock(product),result->{stores=result;model.setRowCount(0);for(var s:stores)model.addRow(new Object[]{s.name(),s.keepQuantity()==null?"Not listed":s.keepQuantity(),s.live()?"Live":s.status()+" (synced)",s.capturedAt()});status.setText("Missing rows are not a verified zero. Requests reject stock changed since your review.");edit.setEnabled(true);},error->status.setText(error.getMessage()));}
    private void edit(){int row=table.getSelectedRow();if(row<0)return;var store=stores.get(row);
        if(store.keepQuantity()==null){JOptionPane.showMessageDialog(this,"No verified quantity is available for this store. Refresh its stock before editing.");return;}
        String value=JOptionPane.showInputDialog(this,"New quantity at "+store.name()+" (currently "+store.keepQuantity()+")",store.keepQuantity());if(value==null)return;
        try{int qty=Integer.parseInt(value.trim());if(qty<0)throw new NumberFormatException();send(store,qty,UUID.randomUUID().toString());}catch(NumberFormatException ex){JOptionPane.showMessageDialog(this,"Enter a whole number of zero or more.");}
    }
    private void send(ProductMergeService.StoreStock store,int qty,String key){edit.setEnabled(false);UiTaskRunner.submit(this,"stock.save",()->LanApiClient.setProductStoreStock(product,store.locationId(),store.keepQuantity(),qty,key),result->{status.setText("APPLIED".equals(result.get("state").getAsString())?"Stock updated.":"Request queued. Check requests for the owning store's confirmation.");edit.setEnabled(true);},error->{edit.setEnabled(true);if(JOptionPane.showConfirmDialog(this,error.getMessage()+"\nRetry this same request?","Stock edit",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)send(store,qty,key);});}
}
