package ui.screens;

import services.AdDraftRenderer;
import services.LanApiClient;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Base64;

/** Shared store-server review; only an explicit confirmation approves the loaded revision. */
final class ProductAdDialog extends JDialog {
    private final String[] kinds={"ad_square","ad_portrait","ad_story","ad_landscape"};
    private final JComboBox<String> format=new JComboBox<>(new String[]{"Square (1:1)","Portrait (4:5)","Story (9:16)","Landscape (16:9)"});
    private final JTextField headline=new JTextField(30);
    private final ui.components.AdSizePreviewLabel preview=new ui.components.AdSizePreviewLabel("Loading shared draft…");
    private final JLabel status=new JLabel("Images can be generated in another chat and imported here.");
    private final JButton create=new JButton("Create template draft"),importFile=new JButton("Import image…"),
            approve=new JButton("Approve ad"),reject=new JButton("Reject ad"),save=new JButton("Save shared draft"),refresh=new JButton("Refresh");
    private final JTextArea rejectionReason=new JTextArea(3,40);
    private BufferedImage draft;
    private long revision;
    private boolean dirty,busy,loaded;
    private String decision="missing";
    private final LanApiClient.StudioProduct product;
    private final Runnable saved;

    ProductAdDialog(Window owner,LanApiClient.StudioProduct product,Runnable saved) {
        this(owner,product,saved,"ad_square");
    }
    ProductAdDialog(Window owner,LanApiClient.StudioProduct product,Runnable saved,String initialKind) {
        super(owner,"Ad review · "+product.name(),ModalityType.APPLICATION_MODAL);
        this.product=product;this.saved=saved;headline.setText(product.name());
        for(int i=0;i<kinds.length;i++)if(kinds[i].equals(initialKind))format.setSelectedIndex(i);
        JPanel controls=new JPanel(new GridLayout(3,1,0,6));
        JPanel fields=new JPanel(new BorderLayout(8,0));fields.add(format,BorderLayout.WEST);fields.add(headline,BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));actions.add(create);actions.add(importFile);actions.add(save);actions.add(refresh);
        JPanel review=new JPanel(new FlowLayout(FlowLayout.LEFT));review.add(approve);review.add(reject);
        controls.add(fields);controls.add(actions);controls.add(review);
        JPanel root=new JPanel(new BorderLayout(8,8));root.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        root.setBackground(ui.design.DeckersPalette.background());preview.setForeground(ui.design.DeckersPalette.text());
        approve.setBackground(ui.design.DeckersPalette.ORANGE);
        rejectionReason.setLineWrap(true);rejectionReason.setWrapStyleWord(true);
        JScrollPane reasons=new JScrollPane(rejectionReason);reasons.setBorder(BorderFactory.createTitledBorder("Rejection reason / corrections needed"));
        JPanel bottom=new JPanel(new BorderLayout(0,6));bottom.add(reasons,BorderLayout.CENTER);bottom.add(status,BorderLayout.SOUTH);
        root.add(controls,BorderLayout.NORTH);root.add(preview,BorderLayout.CENTER);root.add(bottom,BorderLayout.SOUTH);
        setContentPane(root);setSize(1050,740);setMinimumSize(new Dimension(750,450));setLocationRelativeTo(owner);
        create.addActionListener(e -> {
            String kind=kind(),text=headline.getText().trim();
            prepare(() -> AdDraftRenderer.render(kind,ImageIO.read(new ByteArrayInputStream(LanApiClient.studioSource(product.productId()).bytes())),text));
        });
        importFile.addActionListener(e -> {
            JFileChooser chooser=new JFileChooser();
            if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
            File file=chooser.getSelectedFile();
            prepare(() -> {if(file.length()>12_000_000)throw new IOException("Choose a file under 12 MB.");return ImageIO.read(file);});
        });
        format.addActionListener(e -> {dirty=false;draft=null;loaded=false;load();});
        refresh.addActionListener(e -> {
            if(dirty && JOptionPane.showConfirmDialog(this,"Discard the unsaved local draft and reload the store's version?","Reload draft",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            load();
        });
        save.addActionListener(e -> save());approve.addActionListener(e -> decide(true));reject.addActionListener(e -> decide(false));
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter(){public void windowClosing(java.awt.event.WindowEvent e){
            if(busy)return;
            if(!dirty || JOptionPane.showConfirmDialog(ProductAdDialog.this,"Discard the unsaved local draft? Shared drafts remain on the server.","Close review",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)dispose();
        }});
        load();
    }
    private String kind(){return kinds[format.getSelectedIndex()];}
    private void busy(boolean value) {
        rejectionReason.setEditable(!value&&loaded&&!dirty&&"pending".equals(decision));
        busy=value;format.setEnabled(!value&&!dirty);headline.setEnabled(!value);
        create.setEnabled(!value&&loaded);importFile.setEnabled(!value&&loaded);refresh.setEnabled(!value);
        save.setEnabled(!value&&dirty&&draft!=null);
        approve.setEnabled(!value&&loaded&&!dirty&&"pending".equals(decision)&&draft!=null);
        reject.setEnabled(approve.isEnabled());
    }
    private void showPreview() {
        preview.setFormat(kind());
        preview.setIcon(null);
        if(draft==null){preview.setText("No shared draft for this format");return;}
        preview.setIcon(new ImageIcon(draft));preview.setText("");
    }
    private void accept(LanApiClient.AdDraft shared) throws Exception {
        draft=shared.bytesBase64().isEmpty()?null:ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(shared.bytesBase64())));
        rejectionReason.setText(shared.rejectionReason()==null?"":shared.rejectionReason());
        revision=shared.revision();decision=shared.decision();dirty=false;loaded=true;showPreview();
        status.setText("Shared draft: "+decision+" · Revision "+revision+" · Available from any authorized register · Refresh for changes");
    }
    private void load() {
        String kind=kind();preview.setFormat(kind);preview.setIcon(null);busy(true);status.setText("Loading shared draft from the store…");
        new SwingWorker<LanApiClient.AdDraft,Void>() {
            protected LanApiClient.AdDraft doInBackground() throws Exception {return LanApiClient.adDraft(product.productId(),kind,"load",0,null);}
            protected void done(){try{accept(get());}catch(Exception e){loaded=false;status.setText("Could not load: "+message(e));}busy(false);}
        }.execute();
    }
    private void prepare(java.util.concurrent.Callable<BufferedImage> action) {
        String kind=kind();busy(true);status.setText("Preparing local draft…");
        new SwingWorker<BufferedImage,Void>() {
            protected BufferedImage doInBackground() throws Exception {var image=action.call();AdDraftRenderer.validate(kind,image);return image;}
            protected void done(){try{draft=get();dirty=true;showPreview();status.setText("Unsaved local draft · Save shared draft to make it available on every register. Not approved.");}catch(Exception e){status.setText("Draft failed: "+message(e));}busy(false);}
        }.execute();
    }
    private void save() {
        String kind=kind();BufferedImage image=draft;long expected=revision;busy(true);status.setText("Saving shared draft…");
        new SwingWorker<LanApiClient.AdDraft,Void>() {
            protected LanApiClient.AdDraft doInBackground() throws Exception {var bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);return LanApiClient.adDraft(product.productId(),kind,"save",expected,bytes.toByteArray());}
            protected void done(){try{accept(get());saved.run();}catch(Exception e){status.setText("Save failed; local draft retained: "+message(e));}busy(false);}
        }.execute();
    }
    private void decide(boolean approving) {
        String reason=approving?null:rejectionReason.getText().trim();
        if(!approving&&(reason.isEmpty()||reason.length()>2000)){status.setText("Enter a rejection reason (1–2000 characters).");return;}
        String verb=approving?"Approve":"Reject";
        if(JOptionPane.showConfirmDialog(this,verb+" this shared preview for "+format.getSelectedItem()+"?"+(approving?" It will be added to the gallery and replace this slot's approval.":" Existing approved coverage will be preserved."),"Review ad",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        String kind=kind();long expected=revision;busy(true);status.setText("Saving review decision…");
        new SwingWorker<LanApiClient.AdDraft,Void>() {
            protected LanApiClient.AdDraft doInBackground() throws Exception {
                if(approving){LanApiClient.approveSharedAdDraft(product.productId(),kind,expected);return LanApiClient.adDraft(product.productId(),kind,"load",0,null);}
                return LanApiClient.adDraft(product.productId(),kind,"reject",expected,null,reason);
            }
            protected void done(){try{accept(get());saved.run();}catch(Exception e){loaded=false;decision="unknown";status.setText("Review failed: "+message(e)+" · Refresh before reviewing again.");}busy(false);}
        }.execute();
    }
    private static String message(Exception error){Throwable cause=error.getCause();return (cause==null?error:cause).getMessage();}
}
