package ui.screens;

import com.google.gson.Gson;
import services.LanApiClient;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Review shared product previews; approved photos become primary through the store server. */
public final class CatalogPhotoStudioDialog extends JDialog {
    private static final Gson GSON = new Gson();
    private static final Path HOME = Path.of(System.getProperty("user.home"), ".smartstock", "catalog-studio");
    private final DefaultListModel<Review> model = new DefaultListModel<>();
    private final JList<Review> list = new JList<>(model);
    private final JLabel original = new JLabel("Select a product", JLabel.CENTER);
    private final JLabel studio = new JLabel("", JLabel.CENTER);
    private final JLabel status = new JLabel(" ");
    private final JButton generate = new JButton("Create next 20 previews");
    private final JButton retry = new JButton("Retry failed previews");
    private final JButton approve = new JButton("Approve and make main photo");
    private final JButton reject = new JButton("Reject photo");
    private final JButton importPhotos = new JButton("Retry approved uploads");
    private final JButton reload = new JButton("Refresh shared reviews");
    private final JCheckBox showCompleted = new JCheckBox("Show completed photos");
    private State state;
    private boolean busy;
    private final Map<Long,Long> loadedPhotos = new HashMap<>();

    public CatalogPhotoStudioDialog(Window owner) {
        super(owner,"Product Photo Studio",ModalityType.MODELESS);
        state = load();
        setSize(1100,740);setMinimumSize(new Dimension(780,550));setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent event){if(!busy)dispose();}});
        JPanel root=new JPanel(new BorderLayout(12,12));root.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
        JLabel heading=new JLabel("Review shared previews from any connected computer. Approval makes the preview the main photo and keeps the original as a secondary photo.");
        JPanel header=new JPanel(new BorderLayout());header.add(heading,BorderLayout.NORTH);
        header.add(showCompleted,BorderLayout.SOUTH);root.add(header,BorderLayout.NORTH);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(e->{if(!e.getValueIsAdjusting())showSelected();});
        JScrollPane products=new JScrollPane(list);products.setPreferredSize(new Dimension(310,500));
        JPanel comparison=new JPanel();comparison.setLayout(new BoxLayout(comparison,BoxLayout.X_AXIS));
        comparison.add(imagePane("Original",original));comparison.add(Box.createHorizontalStrut(8));
        comparison.add(imagePane("Studio preview",studio));
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,products,comparison);
        split.setResizeWeight(.28);root.add(split,BorderLayout.CENTER);
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT,9,0));
        controls.add(generate);controls.add(retry);controls.add(approve);controls.add(reject);controls.add(importPhotos);controls.add(reload);
        JPanel footer=new JPanel(new BorderLayout(0,9));footer.add(controls,BorderLayout.NORTH);footer.add(status,BorderLayout.SOUTH);
        root.add(footer,BorderLayout.SOUTH);setContentPane(root);
        generate.addActionListener(e->generate());retry.addActionListener(e->retryFailed());approve.addActionListener(e->approveSelected());
        reject.addActionListener(e->decide("rejected"));importPhotos.addActionListener(e->importApproved(null));
        showCompleted.addActionListener(e->refresh());reload.addActionListener(e->loadSharedReviews());
        refresh();
        loadSharedReviews();
    }

    private static JPanel imagePane(String title,JLabel image){
        JPanel panel=new JPanel(new BorderLayout());panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.setPreferredSize(new Dimension(340,440));panel.add(image,BorderLayout.CENTER);return panel;
    }

    private void setBusy(boolean value){
        busy=value;generate.setEnabled(!value);retry.setEnabled(!value);approve.setEnabled(!value);reject.setEnabled(!value);
        importPhotos.setEnabled(!value);reload.setEnabled(!value);
    }

    private void refresh(){
        Review selected=list.getSelectedValue();model.clear();
        int selectedIndex=-1;
        for(Review review:state.items){
            if(!showCompleted.isSelected()&&("approved".equals(review.decision)||"imported".equals(review.decision)))continue;
            if(review==selected)selectedIndex=model.size();
            model.addElement(review);
        }
        if(selectedIndex>=0)list.setSelectedIndex(selectedIndex);
        else if(!model.isEmpty())list.setSelectedIndex(0);
        else showSelected();
        int approved=0,imported=0,rejected=0,failed=0;
        for(Review row:state.items){if("approved".equals(row.decision))approved++;if("imported".equals(row.decision))imported++;if("rejected".equals(row.decision))rejected++;if("failed".equals(row.decision)||"import_failed".equals(row.decision))failed++;}
        status.setText(state.items.size()+" previews  ·  "+approved+" approved  ·  "+imported+" added  ·  "+rejected+" rejected  ·  "+failed+" failed");
    }

    private void showSelected(){
        Review row=list.getSelectedValue();
        if(row==null){showImage(original,null);showImage(studio,null);return;}
        if(loadedPhotos.getOrDefault(row.productId,-1L)==row.revision){
            showImage(original,HOME.resolve("originals/"+row.productId+".img"));
            showImage(studio,HOME.resolve("previews/"+row.productId+".jpg"));
            return;
        }
        original.setIcon(null);original.setText("Loading original…");
        studio.setIcon(null);studio.setText("Loading preview…");
        long requestedId=row.productId,requestedRevision=row.revision;
        new SwingWorker<Void,Void>(){
            @Override protected Void doInBackground() throws Exception {
                Path originals=HOME.resolve("originals"),previews=HOME.resolve("previews");
                Files.createDirectories(originals);Files.createDirectories(previews);
                try{Files.write(originals.resolve(requestedId+".img"),LanApiClient.studioReviewPhoto(requestedId,"original"));}
                catch(Exception error){if(!Files.isRegularFile(originals.resolve(requestedId+".img")))throw error;}
                Files.write(previews.resolve(requestedId+".jpg"),LanApiClient.studioReviewPhoto(requestedId,"preview"));
                return null;
            }
            @Override protected void done(){
                if(list.getSelectedValue()==null||list.getSelectedValue().productId!=requestedId)return;
                try{get();loadedPhotos.put(requestedId,requestedRevision);
                    showImage(original,HOME.resolve("originals/"+requestedId+".img"));
                    showImage(studio,HOME.resolve("previews/"+requestedId+".jpg"));
                }catch(Exception error){original.setText("Photo unavailable");studio.setText("Photo unavailable");
                    status.setText("Could not load shared photos: "+message(error));}
            }
        }.execute();
    }

    private static void showImage(JLabel label,Path file){
        if(file==null||!Files.isRegularFile(file)){label.setIcon(null);label.setText("No image");return;}
        try{
            var source=ImageIO.read(file.toFile());if(source==null)throw new IllegalArgumentException("Invalid photo");
            double scale=Math.min(350.0/source.getWidth(),450.0/source.getHeight());
            int width=Math.max(1,(int)(source.getWidth()*scale)),height=Math.max(1,(int)(source.getHeight()*scale));
            label.setIcon(new javax.swing.ImageIcon(source.getScaledInstance(width,height,Image.SCALE_SMOOTH)));
            label.setText("");
        }catch(Exception e){label.setIcon(null);label.setText("Preview unavailable");}
    }

    private void loadSharedReviews(){
        if(busy)return;
        setBusy(true);status.setText("Loading shared photo reviews…");
        new SwingWorker<List<LanApiClient.StudioReview>,String>(){
            @Override protected List<LanApiClient.StudioReview> doInBackground() throws Exception {
                // An older installation's local queue is transferred once. Existing server rows win.
                List<LanApiClient.StudioReview> existing=LanApiClient.studioReviews();
                java.util.Set<Long> sharedIds=new java.util.HashSet<>();
                for(var item:existing)sharedIds.add(item.productId());
                boolean transferred=false;
                for(Review row:new ArrayList<>(state.items)){
                    if(row.revision>0||sharedIds.contains(row.productId))continue;
                    publish("Sharing preview for "+row.name+"…");
                    Path preview=HOME.resolve("previews/"+row.productId+".jpg");
                    byte[] image=Files.isRegularFile(preview)?Files.readAllBytes(preview):new byte[0];
                    LanApiClient.uploadStudioReview(row.asStudioReview(),new byte[0],image);
                    transferred=true;
                }
                return transferred?LanApiClient.studioReviews():existing;
            }
            @Override protected void process(List<String> updates){status.setText(updates.get(updates.size()-1));}
            @Override protected void done(){
                setBusy(false);
                try{
                    List<LanApiClient.StudioReview> remote=get();
                    Review selected=list.getSelectedValue();
                    State shared=new State();shared.lastId=state.lastId;
                    for(var item:remote){
                        Review row=Review.from(item);shared.items.add(row);
                        shared.lastId=Math.max(shared.lastId,row.productId);
                    }
                    state=shared;save();refresh();
                    if(selected!=null)for(int i=0;i<model.size();i++)if(model.get(i).productId==selected.productId){list.setSelectedIndex(i);break;}
                }catch(Exception error){status.setText("Could not load shared reviews: "+message(error));}
            }
        }.execute();
    }

    private void uploadIfMissing(Review row) throws Exception {
        if(row.revision>0)return;
        Path preview=HOME.resolve("previews/"+row.productId+".jpg");
        LanApiClient.uploadStudioReview(row.asStudioReview(),
                new byte[0],
                Files.isRegularFile(preview)?Files.readAllBytes(preview):new byte[0]);
        for(var remote:LanApiClient.studioReviews())if(remote.productId()==row.productId){
            row.revision=remote.revision();return;
        }
        throw new IllegalStateException("The shared review could not be found.");
    }

    private void decide(String decision){
        Review row=list.getSelectedValue();if(row==null||"imported".equals(row.decision))return;
        setBusy(true);status.setText("Saving review for "+row.name+"…");
        new SwingWorker<Void,Void>(){
            @Override protected Void doInBackground() throws Exception {
                uploadIfMissing(row);
                row.revision=LanApiClient.decideStudioReview(row.productId,row.revision,decision,null);
                row.decision=decision;row.error=null;save();return null;
            }
            @Override protected void done(){setBusy(false);try{get();refresh();}catch(Exception error){
                status.setText("Review was not saved: "+message(error));loadSharedReviews();}}
        }.execute();
    }

    private void approveSelected(){
        Review row=list.getSelectedValue();
        if(row==null||"imported".equals(row.decision))return;
        if(loadedPhotos.getOrDefault(row.productId,-1L)!=row.revision
                ||!Files.isRegularFile(HOME.resolve("previews/"+row.productId+".jpg"))){
            JOptionPane.showMessageDialog(this,"Wait for the shared preview to load before approving it.");return;
        }
        int answer=JOptionPane.showConfirmDialog(this,
                "Make this reviewed image the main photo for "+row.name+"? The current main photo will become a secondary photo.",
                "Approve product photo",JOptionPane.OK_CANCEL_OPTION);
        if(answer!=JOptionPane.OK_OPTION)return;
        row.decision="approved";row.error=null;save();refresh();
        importApproved(row);
    }

    private void generate(){
        setBusy(true);status.setText("Preparing store server photo processing…");
        new SwingWorker<Void,String>(){
            @Override protected Void doInBackground() throws Exception {
                {
                    int made=0;
                    while(made<20){
                        List<LanApiClient.StudioProduct> page=LanApiClient.studioProducts(state.lastId);
                        if(page.isEmpty())break;
                        for(var product:page){
                            state.lastId=product.productId();
                            if(find(product.productId())!=null)continue;
                            Review row=new Review(product.productId(),product.name());
                            state.items.add(row);
                            publish("Creating "+(made+1)+" of 20: "+product.name());
                            try{createPreview(row);uploadIfMissing(row);made++;}
                            catch(Exception error){row.decision="failed";row.error=message(error);}
                            save();
                            if(made>=20)break;
                        }
                    }
                }
                return null;
            }
            @Override protected void process(List<String> updates){status.setText(updates.get(updates.size()-1));}
            @Override protected void done(){
                setBusy(false);try{get();refresh();if(!model.isEmpty())list.setSelectedIndex(model.size()-1);}
                catch(Exception error){status.setText("Photo creation stopped: "+message(error));}
            }
        }.execute();
    }

    private void retryFailed(){
        setBusy(true);status.setText("Retrying failed previews…");
        new SwingWorker<Void,String>(){
            @Override protected Void doInBackground() throws Exception {
                {
                    for(Review row:state.items){
                        if(!"failed".equals(row.decision))continue;
                        publish("Retrying "+row.name+"…");
                        try{createPreview(row);row.revision=0;uploadIfMissing(row);}
                        catch(Exception error){row.error=message(error);}
                        save();
                    }
                }
                return null;
            }
            @Override protected void process(List<String> updates){status.setText(updates.get(updates.size()-1));}
            @Override protected void done(){
                setBusy(false);try{get();refresh();}catch(Exception error){status.setText("Retry stopped: "+message(error));}
            }
        }.execute();
    }

    private static void createPreview(Review row) throws Exception {
        LanApiClient.StudioSource source=LanApiClient.studioSource(row.productId);
        row.imageUrl=source.imageUrl();row.sha256=source.sha256();
        Path originals=HOME.resolve("originals"),previews=HOME.resolve("previews");
        Files.createDirectories(originals);Files.createDirectories(previews);
        Files.write(originals.resolve(row.productId+".img"),source.bytes());
        Files.write(previews.resolve(row.productId+".jpg"),LanApiClient.createStudioReviewJpeg(source.bytes()));
        row.decision="review";row.error=null;
    }

    private void importApproved(Review selected){
        long count=state.items.stream().filter(r->(selected==null||r==selected)&&("approved".equals(r.decision)||"import_failed".equals(r.decision))).count();
        if(count==0){JOptionPane.showMessageDialog(this,"Approve a preview first.");return;}
        setBusy(true);
        new SwingWorker<Void,String>(){
            @Override protected Void doInBackground(){
                for(Review row:state.items){
                    if(selected!=null&&row!=selected)continue;
                    if(!"approved".equals(row.decision)&&!"import_failed".equals(row.decision))continue;
                    publish("Adding photo for "+row.name+"…");
                    try{
                        uploadIfMissing(row);
                        row.revision=LanApiClient.decideStudioReview(row.productId,row.revision,"approved",null);
                        byte[] image=LanApiClient.studioReviewPhoto(row.productId,"preview");
                        var result=LanApiClient.importStudioPhoto(row.productId,row.imageUrl,row.sha256,image);
                        row.decision="imported";row.reference=result.reference();row.error=null;
                    }catch(Exception error){row.decision="import_failed";row.error=message(error);
                        try{row.revision=LanApiClient.decideStudioReview(row.productId,row.revision,"import_failed",row.error);}
                        catch(Exception ignored){} }
                    save();
                }
                return null;
            }
            @Override protected void process(List<String> updates){status.setText(updates.get(updates.size()-1));}
            @Override protected void done(){setBusy(false);loadSharedReviews();}
        }.execute();
    }

    private Review find(long id){for(Review row:state.items)if(row.productId==id)return row;return null;}
    private static String message(Throwable error){Throwable cause=error;while(cause.getCause()!=null)cause=cause.getCause();return cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage();}
    private static State load(){
        try{Path file=HOME.resolve("review.json");if(Files.isRegularFile(file)){
            State found=GSON.fromJson(Files.readString(file),State.class);
            if(found!=null&&found.items!=null)return found;
        }}catch(Exception ignored){}
        return new State();
    }
    private void save(){
        try{
            Files.createDirectories(HOME);Path temp=HOME.resolve("review.json.tmp");
            Files.writeString(temp,GSON.toJson(state));
            Files.move(temp,HOME.resolve("review.json"),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception error){SwingUtilities.invokeLater(()->status.setText("Could not save photo review: "+message(error)));}
    }
    private static final class State {long lastId;List<Review> items=new ArrayList<>();}
    private static final class Review {
        long productId;String name,imageUrl,sha256,decision="review",reference,error,generator;
        boolean generatedForReview;
        long revision;
        Review(long id,String name){this.productId=id;this.name=name;}
        LanApiClient.StudioReview asStudioReview(){return new LanApiClient.StudioReview(productId,name,imageUrl,sha256,
                decision,reference,error,generator,generatedForReview,revision);}
        static Review from(LanApiClient.StudioReview source){
            Review row=new Review(source.productId(),source.name());row.imageUrl=source.imageUrl();
            row.sha256=source.sha256();row.decision=source.decision();row.reference=source.reference();
            row.error=source.error();row.generator=source.generator();row.generatedForReview=source.generatedForReview();
            row.revision=source.revision();return row;
        }
        @Override public String toString(){return productId+"  "+name+(generatedForReview?"  [AI-created]":"")+"  ["+decision+(error==null?"":": "+error)+"]";}
    }
}
