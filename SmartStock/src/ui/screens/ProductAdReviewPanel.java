package ui.screens;

import services.LanApiClient;
import ui.design.DeckersPalette;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;

/** Sequential shared review, using the same list/preview pattern as Product Photo Studio. */
final class ProductAdReviewPanel extends JPanel {
    interface Gateway {
        List<LanApiClient.AdReviewItem> list(String decision) throws Exception;
        LanApiClient.AdDraft load(LanApiClient.AdReviewItem item) throws Exception;
        default byte[] photo(LanApiClient.AdReviewItem item,String kind) throws Exception {return null;}
        void decide(LanApiClient.AdReviewItem item,boolean approve,String reason) throws Exception;
    }
    static final Gateway SERVER=new Gateway() {
        public List<LanApiClient.AdReviewItem> list(String decision) throws Exception {return LanApiClient.adReviewQueue(decision);}
        public LanApiClient.AdDraft load(LanApiClient.AdReviewItem item) throws Exception {return LanApiClient.adDraft(item.productId(),item.kind(),"load",0,null);}
        public byte[] photo(LanApiClient.AdReviewItem item,String kind) throws Exception {
            if("original".equals(kind)) {
                try{return LanApiClient.studioReviewPhoto(item.productId(),kind);}
                catch(Exception unavailable){return LanApiClient.studioSource(item.productId()).bytes();}
            }
            return LanApiClient.studioReviewPhoto(item.productId(),kind);
        }
        public void decide(LanApiClient.AdReviewItem item,boolean approve,String reason) throws Exception {
            if(approve)LanApiClient.approveSharedAdDraft(item.productId(),item.kind(),item.revision());
            else LanApiClient.adDraft(item.productId(),item.kind(),"reject",item.revision(),null,reason);
        }
    };
    final DefaultListModel<LanApiClient.AdReviewItem> model=new DefaultListModel<>();
    final JList<LanApiClient.AdReviewItem> list=new JList<>(model);
    final JButton approve=new JButton("Approve ad and next"),reject=new JButton("Reject ad and next"),refresh=new JButton("Refresh shared reviews");
    final JComboBox<String> filter=new JComboBox<>(new String[]{"Pending review","Rejected","Approved"});
    final ui.components.AdSizePreviewLabel preview=new ui.components.AdSizePreviewLabel("Loading shared ad reviews…");
    final JLabel status=new JLabel(" "),count=new JLabel(" ");
    final JLabel originalPhoto=new ui.components.FitImageLabel("Select an ad to compare photos"),aiPhoto=new ui.components.FitImageLabel("Select an ad to compare photos");
    final JTextArea rejectionReason=new JTextArea(3,40);
    private final Gateway api;
    private final Runnable changed;
    private final JButton edit=new JButton("Import / replace selected ad…");
    private LanApiClient.AdReviewItem loaded;
    private long previewRequest;
    private boolean working;

    ProductAdReviewPanel(Gateway api,Runnable changed) {
        super(new BorderLayout(12,12));this.api=api;this.changed=changed;
        setBorder(BorderFactory.createEmptyBorder(14,14,14,14));setBackground(DeckersPalette.background());
        JPanel header=new JPanel(new BorderLayout(0,8));
        header.add(new JLabel("Review shared ad previews. Approve adds the image to the gallery; Reject keeps existing approved coverage."),BorderLayout.NORTH);
        JPanel tools=new JPanel(new FlowLayout(FlowLayout.LEFT));tools.add(filter);tools.add(refresh);tools.add(count);header.add(tools,BorderLayout.SOUTH);
        add(header,BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane rows=new JScrollPane(list);rows.setBorder(BorderFactory.createTitledBorder("Ads to review · Product and format"));rows.setMinimumSize(new Dimension(250,150));
        JPanel image=new JPanel(new BorderLayout());image.add(preview,BorderLayout.CENTER);image.setBorder(BorderFactory.createTitledBorder("Shared ad preview"));
        JTabbedPane sourceTabs=new JTabbedPane();sourceTabs.addTab("Original photo",originalPhoto);sourceTabs.addTab("AI photo",aiPhoto);
        JPanel sources=new JPanel(new BorderLayout());sources.setBorder(BorderFactory.createTitledBorder("Product photos · Compare with the ad"));sources.add(sourceTabs);
        sources.setMinimumSize(new Dimension(220,150));image.setMinimumSize(new Dimension(300,150));
        JSplitPane comparison=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,sources,image);comparison.setResizeWeight(.40);comparison.setDividerLocation(380);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,rows,comparison);split.setResizeWeight(.23);split.setDividerLocation(280);rejectionReason.setLineWrap(true);rejectionReason.setWrapStyleWord(true);
        JScrollPane reasonPane=new JScrollPane(rejectionReason);reasonPane.setBorder(BorderFactory.createTitledBorder("Rejection reason / corrections needed (required to reject)"));
        JPanel content=new JPanel(new BorderLayout(0,8));content.add(split,BorderLayout.CENTER);content.add(reasonPane,BorderLayout.SOUTH);add(content,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,9,0));actions.add(approve);actions.add(reject);actions.add(edit);
        approve.setBackground(DeckersPalette.ORANGE);
        JPanel footer=new JPanel(new BorderLayout(0,9));footer.add(actions,BorderLayout.NORTH);footer.add(status,BorderLayout.SOUTH);add(footer,BorderLayout.SOUTH);
        list.addListSelectionListener(e->{if(!e.getValueIsAdjusting())showSelected();});
        refresh.addActionListener(e->reload(list.getSelectedValue(),list.getSelectedIndex()));
        filter.addActionListener(e->reload(null,0));
        approve.addActionListener(e->decide(true));reject.addActionListener(e->decide(false));
        edit.addActionListener(e->{
            var item=list.getSelectedValue();if(item==null)return;
            new ProductAdDialog(SwingUtilities.getWindowAncestor(this),
                    new LanApiClient.StudioProduct(item.productId(),item.name(),null,List.of()),
                    ()->{changed.run();reload(item,list.getSelectedIndex());},item.kind()).setVisible(true);
        });
        reload(null,0);
    }
    boolean isWorking(){return working;}
    private String decision(){return switch(filter.getSelectedIndex()){case 1->"rejected";case 2->"approved";default->"pending";};}
    private void controls() {
        rejectionReason.setEditable(!working&&loaded!=null&&"pending".equals(loaded.decision()));
        list.setEnabled(!working);refresh.setEnabled(!working);filter.setEnabled(!working);
        boolean ready=!working&&loaded!=null&&loaded.equals(list.getSelectedValue())&&"pending".equals(loaded.decision());
        approve.setEnabled(ready);reject.setEnabled(ready);edit.setEnabled(!working&&list.getSelectedValue()!=null);
        count.setText(model.size()+" "+filter.getSelectedItem().toString().toLowerCase());
    }
    private void reload(LanApiClient.AdReviewItem keep,int index) {
        if(working)return;
        working=true;loaded=null;previewRequest++;clearPhotos("Loading product photos…");controls();status.setText("Loading shared ad reviews…");String decision=decision();
        new SwingWorker<List<LanApiClient.AdReviewItem>,Void>() {
            protected List<LanApiClient.AdReviewItem> doInBackground() throws Exception {return api.list(decision);}
            protected void done() {
                try {
                    var items=get();model.clear();for(var item:items)model.addElement(item);
                    working=false;
                    int select=-1;
                    if(keep!=null)for(int i=0;i<model.size();i++)if(sameSlot(keep,model.get(i)))select=i;
                    if(!model.isEmpty())list.setSelectedIndex(select>=0?select:Math.min(Math.max(0,index),model.size()-1));
                    else {clearPhotos("No ad selected");preview.setIcon(null);preview.setText("pending".equals(decision)?"All shared ads reviewed. No pending ads.":"No "+decision+" ads.");status.setText(preview.getText());}
                }catch(Exception e){status.setText("Could not load reviews: "+message(e));}
                finally{working=false;controls();}
            }
        }.execute();
    }
    private static boolean sameSlot(LanApiClient.AdReviewItem a,LanApiClient.AdReviewItem b){return a.productId()==b.productId()&&a.kind().equals(b.kind());}
    private void showSelected() {
        long request=++previewRequest;loaded=null;rejectionReason.setText("");preview.setIcon(null);controls();
        clearPhotos("Loading product photos…");
        var item=list.getSelectedValue();if(item==null){clearPhotos("Select an ad to compare photos");preview.setText("Select an ad");return;}
        preview.setFormat(item.kind());
        loadPhoto(item,"original",originalPhoto,request);
        loadPhoto(item,"preview",aiPhoto,request);
        preview.setText("Loading shared preview…");status.setText(item.toString());
        new SwingWorker<ImageIcon,Void>() {
            private String reason;
            protected ImageIcon doInBackground() throws Exception {
                var draft=api.load(item);reason=draft.rejectionReason();
                if(draft.revision()!=item.revision()||!item.decision().equals(draft.decision()))
                    throw new IllegalStateException("This ad changed on another register. Refresh shared reviews.");
                var source=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(draft.bytesBase64())));
                if(source==null)throw new IllegalArgumentException("The preview is unreadable.");
                return new ImageIcon(source);
            }
            protected void done() {
                if(request!=previewRequest||!item.equals(list.getSelectedValue()))return;
                try {preview.setIcon(get());preview.setText("");loaded=item;rejectionReason.setText(reason==null?"":reason);rejectionReason.setCaretPosition(0);status.setText(item+" · "+item.decision()+" · Revision "+item.revision());}
                catch(Exception e){preview.setText("Preview unavailable");status.setText(message(e));}
                controls();
            }
        }.execute();
    }
    private void clearPhotos(String message) {
        for(JLabel photo:List.of(originalPhoto,aiPhoto)){photo.setIcon(null);photo.setText(message);}
    }
    private void loadPhoto(LanApiClient.AdReviewItem item,String kind,JLabel target,long request) {
        new SwingWorker<ImageIcon,Void>() {
            protected ImageIcon doInBackground() throws Exception {
                byte[] bytes=api.photo(item,kind);
                if(bytes==null||bytes.length==0)return null;
                BufferedImage image=ImageIO.read(new ByteArrayInputStream(bytes));
                if(image==null)throw new IllegalArgumentException("Unreadable product photo");
                return new ImageIcon(image);
            }
            protected void done() {
                if(request!=previewRequest||!item.equals(list.getSelectedValue()))return;
                try{ImageIcon image=get();target.setIcon(image);target.setText(image==null?("original".equals(kind)?"Original photo unavailable":"AI photo unavailable"):"");}
                catch(Exception unavailable){target.setIcon(null);target.setText("original".equals(kind)?"Original photo unavailable":"AI photo unavailable");}
            }
        }.execute();
    }
    private void decide(boolean approving) {
        var item=list.getSelectedValue();
        if(working||loaded==null||!loaded.equals(item)||!"pending".equals(item.decision()))return;
        String reason=approving?null:rejectionReason.getText().trim();
        if(!approving&&(reason.isEmpty()||reason.length()>2000)){status.setText("Enter a rejection reason (1–2000 characters) before rejecting.");rejectionReason.requestFocusInWindow();return;}
        int index=list.getSelectedIndex();working=true;controls();status.setText(approving?"Approving ad…":"Rejecting ad…");
        new SwingWorker<Void,Void>() {
            protected Void doInBackground() throws Exception {api.decide(item,approving,reason);return null;}
            protected void done() {
                try {
                    get();loaded=null;rejectionReason.setText("");previewRequest++;model.remove(index);working=false;changed.run();
                    if(model.isEmpty()){clearPhotos("No ad selected");preview.setIcon(null);preview.setText("All shared ads reviewed. No pending ads.");status.setText("All ads in this queue reviewed. Refresh to check for newly imported ads.");}
                    else {list.setSelectedIndex(Math.min(index,model.size()-1));list.ensureIndexIsVisible(list.getSelectedIndex());}
                }catch(Exception e){loaded=null;status.setText("Review not confirmed: "+message(e)+" · Refresh before reviewing again.");}
                finally{working=false;controls();}
            }
        }.execute();
    }
    private static String message(Exception e){return (e.getCause()==null?e:e.getCause()).getMessage();}
}
