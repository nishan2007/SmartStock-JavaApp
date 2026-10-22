package gy.deckers.studio;

import ui.design.DeckersSwing;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;

final class PassportPhotosPanel extends JPanel {
    private final JComboBox<PassportPhotos.Template> template=new JComboBox<>(PassportPhotos.Template.values());
    private final JCheckBox bleed=new JCheckBox("2 mm bleed + trim marks",true);
    private final SheetPreview preview=new SheetPreview();
    private final JLabel status=new JLabel("Choose a photo, then frame it before printing.");
    private PhotoFraming photo;
    private boolean loading;
    private String queuePhotoId;
    private long deleteAfter;
    private int revision;
    private final javax.swing.Timer expiry=new javax.swing.Timer(60000,e->{if(deleteAfter>0&&System.currentTimeMillis()>=deleteAfter){reset();status.setText("Printed photo expired and was cleared. Choose the next photo.");}});
    private final StudioPhoneCapture phone=new StudioPhoneCapture(this,image->{revision++;expiry.stop();queuePhotoId=null;deleteAfter=0;photo=PhotoFraming.initial(image);refresh();framePhoto();},status::setText);

    PassportPhotosPanel() {
        super(new BorderLayout(8,8));setOpaque(false);
        JPanel tools=DeckersSwing.panel();tools.setLayout(new BoxLayout(tools,BoxLayout.X_AXIS));
        template.getAccessibleContext().setAccessibleName("Passport photo template");
        tools.add(template);
        bleed.setOpaque(false);bleed.setToolTipText("Extra image/background outside the finished photo for trimming; photo size and framing stay unchanged.");
        bleed.addActionListener(e->refresh());tools.add(bleed);
        tools.add(StudioWorkspaceShell.button("Choose photo…",this::choosePhoto));
        tools.add(StudioWorkspaceShell.button("Take photo on phone…",()->{if(!loading)phone.start();}));
        tools.add(StudioWorkspaceShell.button("Frame photo…",this::framePhoto));
        tools.add(StudioWorkspaceShell.button("Print 4 × 6…",this::printPhotos));
        tools.add(StudioWorkspaceShell.button("Mark printed",()->markPrinted(queuePhotoId)));
        JScrollPane toolbar=new JScrollPane(tools,JScrollPane.VERTICAL_SCROLLBAR_NEVER,JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        toolbar.setBorder(null);toolbar.setPreferredSize(new Dimension(300,58));
        add(toolbar,BorderLayout.NORTH);add(preview);
        JPanel footer=DeckersSwing.panel();footer.setLayout(new GridLayout(0,1));
        footer.add(new JLabel("4 × 6 inch photo paper · Actual size · Check face position and background before printing."));
        footer.add(status);add(footer,BorderLayout.SOUTH);
        template.addActionListener(e->{phone.cancel();refresh();});refresh();
    }
    private PassportPhotos.Template selected(){return (PassportPhotos.Template)template.getSelectedItem();}
    void reset(){revision++;loading=false;phone.cancel();expiry.stop();queuePhotoId=null;deleteAfter=0;photo=null;refresh();status.setText("Choose a photo, then frame it before printing.");}
    void useQueued(StudioPhotoQueuePanel.Photo selected){
        reset();queuePhotoId=selected.id();deleteAfter=selected.deleteAfter();photo=PhotoFraming.initial(selected.image());expiry.start();refresh();status.setText("Queued photo: "+(selected.name().isBlank()?selected.id().substring(0,8):selected.name()));framePhoto();
    }
    private void refresh(){preview.setImage(PassportPhotos.render(selected(),photo,bleed.isSelected()?PassportPhotos.BLEED:0));}
    private void choosePhoto(){
        if(loading)return;
        phone.cancel();
        JFileChooser chooser=new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Photos (JPEG, PNG)","jpg","jpeg","png"));
        if(chooser.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        var file=chooser.getSelectedFile();int version=++revision;loading=true;status.setText("Loading photo…");
        new SwingWorker<java.awt.image.BufferedImage,Void>(){
            protected java.awt.image.BufferedImage doInBackground()throws Exception{
                var image=ImageIO.read(file);if(image==null)throw new java.io.IOException("Choose a supported photo file.");return image;
            }
            protected void done(){if(version!=revision)return;loading=false;try{queuePhotoId=null;deleteAfter=0;photo=PhotoFraming.initial(get());refresh();status.setText(file.getName());framePhoto();}
                catch(Exception ex){error(ex.getCause()==null?ex:ex.getCause());}}
        }.execute();
    }
    private void framePhoto(){
        if(loading){status.setText("Wait for the photo operation to finish.");return;}
        phone.cancel();
        if(photo==null){status.setText("Choose a photo first.");return;}
        var size=selected();var framed=PhotoFramingDialog.edit(this,photo,size.width*25.4/72,size.height*25.4/72);
        if(framed!=null){photo=framed;refresh();}
    }
    void printPhotos(){
        if(deleteAfter>0&&System.currentTimeMillis()>=deleteAfter){reset();status.setText("This printed photo expired. Choose the next photo.");return;}
        if(loading){status.setText("Wait for the photo to finish loading.");return;}
        if(photo==null){status.setText("Choose a photo first.");return;}
        if(queuePhotoId!=null){
            String id=queuePhotoId;int version=revision;loading=true;status.setText("Checking queued photo…");
            new SwingWorker<com.google.gson.JsonObject,Void>(){
                protected com.google.gson.JsonObject doInBackground()throws Exception{return StudioPhotoQueuePanel.request("STATUS",id);}
                protected void done(){if(version!=revision)return;loading=false;try{deleteAfter=get().get("deleteAfter").getAsLong();sendPrint();}catch(Exception ex){photo=null;queuePhotoId=null;refresh();error(ex);}}
            }.execute();return;
        }
        sendPrint();
    }
    private void sendPrint(){
        String printingId=queuePhotoId;
        try{StudioPrinting.printPassport(this,PassportPhotos.printable(selected(),photo,bleed.isSelected()?PassportPhotos.BLEED:0),status::setText,()->{if(printingId!=null&&printingId.equals(queuePhotoId)&&isShowing())markPrinted(printingId);});}
        catch(Exception ex){error(ex);}
    }
    private void markPrinted(String id){
        if(id==null){status.setText("This photo is not from the shared queue.");return;}
        if(JOptionPane.showConfirmDialog(this,"Has this person's sheet printed successfully?\nThe queued photo will be deleted 24 hours after marking it printed.","Mark printed",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        int version=revision;new SwingWorker<com.google.gson.JsonObject,Void>(){
            protected com.google.gson.JsonObject doInBackground()throws Exception{return StudioPhotoQueuePanel.request("PRINTED",id);}
            protected void done(){if(version!=revision)return;try{deleteAfter=get().get("deleteAfter").getAsLong();expiry.start();status.setText("Marked printed. This photo will be removed 24 hours after its first confirmed print.");}catch(Exception ex){error(ex);}}
        }.execute();
    }
    private void error(Throwable ex){status.setText("Photo operation failed.");JOptionPane.showMessageDialog(this,ex.getMessage(),"Passport photos",JOptionPane.ERROR_MESSAGE);}
}
