package ui.screens;

import services.LanApiClient;
import services.ProductMergeService;
import managers.SessionManager;
import ui.helpers.UiTaskRunner;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.UUID;

/** One pair at a time: nothing is selected or merged automatically. */
public final class ProductMergeReviewDialog extends JDialog {
    private final JTextField search=new JTextField(24);
    private final JButton scan=new JButton("Find duplicates");
    private final JButton inspect=new JButton("Review selected pair");
    private final JLabel status=new JLabel(" ");
    private final DefaultTableModel model=new DefaultTableModel(new Object[]{"Reason","First ID","First item / SKU","Qty","Second ID","Second item / SKU","Qty"},0) {
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    private final JTable table=new JTable(model);
    private List<ProductMergeService.Candidate> pairs=List.of();
    private final Runnable changed;
    private boolean busy;
    private record Presentation(ProductMergeService.Review review,ImageIcon[] photos) { }

    public ProductMergeReviewDialog(JFrame owner,Runnable changed) {
        super(owner,"Review duplicate inventory items",true);this.changed=changed;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);setSize(1120,650);setLocationRelativeTo(owner);
        JPanel body=new JPanel(new BorderLayout(10,10));body.setBorder(new EmptyBorder(15,15,15,15));setContentPane(body);
        JPanel header=new JPanel(new GridLayout(0,1,0,6));
        header.add(new JLabel("Store: "+SessionManager.getCurrentLocationName()+" — review each pair before merging"));
        header.add(new JLabel("Matches use names and variants, or shared barcodes. Similar items may be different products."));
        JPanel filters=new JPanel(new FlowLayout(FlowLayout.LEFT));filters.add(new JLabel("Name or SKU:"));filters.add(search);filters.add(scan);header.add(filters);
        body.add(header,BorderLayout.NORTH);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.setAutoCreateRowSorter(true);
        table.setRowHeight(26);body.add(new JScrollPane(table),BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout());footer.add(status,BorderLayout.CENTER);
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));JButton progress=new JButton("Merge progress");buttons.add(progress);buttons.add(inspect);JButton close=new JButton("Close");buttons.add(close);footer.add(buttons,BorderLayout.SOUTH);body.add(footer,BorderLayout.SOUTH);
        progress.addActionListener(e->UiTaskRunner.submit(this,"product-merge.progress",LanApiClient::productMergeStatus,result->{
            DefaultTableModel states=new DefaultTableModel(new Object[]{"Merge","Store","State"},0){@Override public boolean isCellEditable(int r,int c){return false;}};
            for(var entry:result.getAsJsonArray("merges")){var m=entry.getAsJsonObject();for(var store:m.getAsJsonArray("stores")){var s=store.getAsJsonObject();String label=m.get("sourceProductId").isJsonNull()?"Awaiting catalogue":"#"+m.get("sourceProductId").getAsInt()+" into #"+m.get("targetProductId").getAsInt();states.addRow(new Object[]{label,s.get("name").getAsString(),s.get("state").getAsString()});}}
            JScrollPane pane=new JScrollPane(new JTable(states));pane.setPreferredSize(new Dimension(700,300));JOptionPane.showMessageDialog(this,pane,"Merge progress",JOptionPane.INFORMATION_MESSAGE);
        },error->status.setText(error.getMessage())));
        scan.addActionListener(e->scan());search.addActionListener(e->scan());inspect.addActionListener(e->choose());close.addActionListener(e->dispose());
        inspect.setEnabled(false);SwingUtilities.invokeLater(this::scan);
    }
    private void busy(boolean value,String message) {
        busy=value;scan.setEnabled(!value);search.setEnabled(!value);inspect.setEnabled(!value&&!pairs.isEmpty());status.setText(message);
    }
    private void scan() {
        if(busy)return;String term=search.getText().trim();busy(true,"Finding duplicate candidates…");
        UiTaskRunner.submit(this,"product-merge.scan",()->LanApiClient.productMergeCandidates(term),result->{
            pairs=result.pairs();model.setRowCount(0);
            for(var p:pairs)model.addRow(new Object[]{p.reason(),p.first().id(),label(p.first()),p.first().quantity(),p.second().id(),label(p.second()),p.second().quantity()});
            busy(false,result.truncated()?"Results limited — narrow the name or SKU search to find more pairs.":pairs.size()+" candidate pairs. No items have been merged.");
        },error->busy(false,"Could not find duplicates: "+error.getMessage()));
    }
    private static String label(ProductMergeService.Item item) {
        return item.name()+" / "+item.sku()+" / "+item.size()+" / "+item.color()+" / "+item.flavor();
    }
    private void choose() {
        if(busy)return;int row=table.getSelectedRow();if(row<0){status.setText("Select a candidate pair first.");return;}
        var pair=pairs.get(table.convertRowIndexToModel(row));
        Object[] choices={"Keep #"+pair.first().id(),"Keep #"+pair.second().id(),"Cancel"};
        int choice=JOptionPane.showOptionDialog(this,"Choose the item whose name, SKU, price, cost, images and shelf settings will be kept.\n"+
                label(pair.first())+"\n"+label(pair.second()),"Choose the item to keep",JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,choices,choices[0]);
        if(choice<0||choice>1)return;
        int keep=choice==0?pair.first().id():pair.second().id(),source=choice==0?pair.second().id():pair.first().id();
        busy(true,"Checking stock and linked records…");
        UiTaskRunner.submit(this,"product-merge.review",()->{
            var review=LanApiClient.reviewProductMerge(keep,source);ImageIcon[] photos=new ImageIcon[2];
            var items=List.of(review.keep(),review.source());
            for(int n=0;n<items.size();n++)try {
                var p=com.google.gson.JsonParser.parseString(items.get(n).snapshot()).getAsJsonObject().getAsJsonObject("product");
                if(p.has("image_url")&&!p.get("image_url").isJsonNull()&&!p.get("image_url").getAsString().isBlank()) {
                    var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(LanApiClient.downloadImageAsset(p.get("image_url").getAsString())));
                    if(image!=null){double scale=Math.min(330.0/image.getWidth(),180.0/image.getHeight());photos[n]=new ImageIcon(image.getScaledInstance(Math.max(1,(int)(image.getWidth()*scale)),Math.max(1,(int)(image.getHeight()*scale)),Image.SCALE_SMOOTH));}
                }
            }catch(Exception ignored){ /* A missing image must not hide stock or history. */ }
            return new Presentation(review,photos);
        },presentation->{
            busy(false,"Review ready.");showReview(presentation.review(),presentation.photos());
        },error->busy(false,"Could not review this pair: "+error.getMessage()));
    }
    private void showReview(ProductMergeService.Review review,ImageIcon[] photos) {
        var a=review.keep();var b=review.source();
        DefaultTableModel details=new DefaultTableModel(new Object[]{"Field","Keep #"+a.id(),"Archive #"+b.id()},0) {
            @Override public boolean isCellEditable(int r,int c){return false;}
        };
        details.addRow(new Object[]{"Name",a.name(),b.name()});details.addRow(new Object[]{"SKU",a.sku(),b.sku()});
        details.addRow(new Object[]{"Size",a.size(),b.size()});details.addRow(new Object[]{"Color",a.color(),b.color()});
        details.addRow(new Object[]{"Flavor",a.flavor(),b.flavor()});details.addRow(new Object[]{"Brand",a.brand(),b.brand()});
        details.addRow(new Object[]{"Department",a.department(),b.department()});details.addRow(new Object[]{"Description",a.description(),b.description()});
        details.addRow(new Object[]{"Price",a.price(),b.price()});details.addRow(new Object[]{"Cost",a.cost(),b.cost()});
        details.addRow(new Object[]{"Store stock",a.quantity(),b.quantity()});details.addRow(new Object[]{"Barcodes",a.barcodes(),b.barcodes()});
        details.addRow(new Object[]{"Added by",a.createdBy(),b.createdBy()});details.addRow(new Object[]{"Added at",a.createdAt(),b.createdAt()});
        if(review.stores()!=null)for(var store:review.stores()) {
            details.addRow(new Object[]{store.name()+" stock — "+(store.live()?"live":store.status()+"; "+store.capturedAt()),store.keepQuantity()==null?"Not listed":store.keepQuantity(),store.sourceQuantity()==null?"Not listed":store.sourceQuantity()});
        }
        if(review.references()!=null)for(var ref:review.references().entrySet())details.addRow(new Object[]{"Linked history: "+ref.getKey(),ref.getValue()+" records","Amounts preserved"});
        JPanel panel=new JPanel(new BorderLayout(8,8));JTable grid=new JTable(details);grid.setRowHeight(24);
        JPanel pictures=new JPanel(new GridLayout(1,2,12,0));
        for(int n=0;n<2;n++){JLabel photo=new JLabel(photos[n]==null?"Photo unavailable":"",photos[n],JLabel.CENTER);photo.setPreferredSize(new Dimension(350,185));photo.setBorder(BorderFactory.createTitledBorder(n==0?"Keep this item and its photos":"Archive duplicate; retain its barcodes"));pictures.add(photo);}panel.add(pictures,BorderLayout.NORTH);
        JScrollPane scroll=new JScrollPane(grid);scroll.setPreferredSize(new Dimension(880,260));panel.add(scroll,BorderLayout.CENTER);
        JTextArea explanation=new JTextArea();explanation.setEditable(false);explanation.setLineWrap(true);explanation.setWrapStyleWord(true);explanation.setOpaque(false);
        boolean allowed=review.blockers().isEmpty();
        explanation.setText(allowed?"Keep #"+a.id()+" and archive #"+b.id()+". Keep the original photos and price, combine descriptions, and retain all barcodes under Additional barcodes. Each store combines its own quantities; sales and return amounts stay unchanged. Other stores remain awaiting confirmation until their servers apply the merge. Confirm these quantities represent separate physical stock.":"Merge blocked:\n"+String.join("\n",review.blockers()));
        explanation.setRows(5);panel.add(new JScrollPane(explanation),BorderLayout.SOUTH);
        if(!allowed){JOptionPane.showMessageDialog(this,panel,"Merge review — needs attention",JOptionPane.WARNING_MESSAGE);return;}
        if(JOptionPane.showConfirmDialog(this,panel,"Merge #"+b.id()+" into #"+a.id()+"?",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
        sendMerge(review,UUID.randomUUID().toString());
    }
    private void sendMerge(ProductMergeService.Review review,String key) {
        busy(true,"Merging reviewed pair…");
        UiTaskRunner.submit(this,"product-merge.save",()->LanApiClient.mergeProducts(review,key),result->{
            busy(false,"Merged.");changed.run();JOptionPane.showMessageDialog(this,"Merged #"+review.source().id()+" into #"+review.keep().id()+" at this store. The duplicate is archived.\n"+(result.has("state")&&"AWAITING_STORES".equals(result.get("state").getAsString())?"Other stores are awaiting confirmation; their stock is applied independently.":"Merge applied."));scan();
        },error->{
            busy(false,"Merge was not confirmed: "+error.getMessage());
            // Preserve the exact request and key across uncertain network outcomes.
            if(JOptionPane.showConfirmDialog(this,"Merge was not confirmed: "+error.getMessage()+"\nRetry the same request safely?", "Merge result",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)sendMerge(review,key);
        });
    }
}
