package gy.deckers.studio;

import com.google.gson.JsonObject;
import services.LanApiClient;
import services.StudioBackgroundService;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.Base64;
import java.util.function.Consumer;

/** One pending phone capture per workspace; stale network completions never replace photos. */
final class StudioPhoneCapture {
    private final JComponent owner;
    private final Consumer<BufferedImage> receive;
    private final Consumer<String> status;
    private final Timer poll;
    private int generation;
    private String id;
    private long expires;
    private boolean polling;
    private JDialog dialog;

    StudioPhoneCapture(JComponent owner,Consumer<BufferedImage> receive,Consumer<String> status){
        this.owner=owner;this.receive=receive;this.status=status;
        poll=new Timer(1800,e->poll());
        owner.addHierarchyListener(e->{if((e.getChangeFlags()&HierarchyEvent.SHOWING_CHANGED)!=0&&!owner.isShowing())cancel();});
    }
    void start(){
        cancel();int version=generation;status.accept("Creating phone photo link…");
        new SwingWorker<JsonObject,Void>(){
            protected JsonObject doInBackground()throws Exception{return request("CREATE",null);}
            protected void done(){try{
                var result=get();String created=result.get("id").getAsString();
                if(version!=generation||!owner.isShowing()){closeRemote(created);return;}
                id=created;expires=result.get("expiresAt").getAsLong();
                showLink(result.get("url").getAsString());poll.start();status.accept("Waiting for a photo from your phone…");
            }catch(Exception ex){if(version==generation)status.accept(message(ex));}}
        }.execute();
    }
    void cancel(){
        generation++;poll.stop();polling=false;
        String old=id;id=null;
        if(dialog!=null){JDialog oldDialog=dialog;dialog=null;oldDialog.dispose();}
        if(old!=null)closeRemote(old);
    }
    private void poll(){
        if(id==null||polling)return;
        if(System.currentTimeMillis()>=expires){cancel();status.accept("Phone photo link expired. Create a new link.");return;}
        int version=generation;String current=id;polling=true;
        new SwingWorker<BufferedImage,Void>(){
            protected BufferedImage doInBackground()throws Exception{
                var result=request("POLL",current);
                if(!result.get("uploaded").getAsBoolean())return null;
                byte[] bytes=Base64.getDecoder().decode(result.get("bytesBase64").getAsString());
                StudioBackgroundService.validate(bytes);
                var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));
                if(image==null)throw new java.io.IOException("The phone photo could not be decoded.");return image;
            }
            protected void done(){
                if(version!=generation)return;polling=false;
                try{var image=get();if(image==null)return;
                    StudioPhoneCapture.this.cancel();status.accept("Phone photo received. Adjust the crop before printing.");receive.accept(image);
                }catch(Exception ex){StudioPhoneCapture.this.cancel();status.accept(message(ex));}
            }
        }.execute();
    }
    private void showLink(String url)throws Exception{
        dialog=new JDialog(SwingUtilities.getWindowAncestor(owner),"Take photo on phone",Dialog.ModalityType.MODELESS);
        JDialog current=dialog;JPanel content=new JPanel(new BorderLayout(8,8));content.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        content.add(new JLabel("Scan with your phone. Works on Wi-Fi or mobile data."),BorderLayout.NORTH);
        var matrix=new com.google.zxing.qrcode.QRCodeWriter().encode(url,com.google.zxing.BarcodeFormat.QR_CODE,280,280);
        BufferedImage qr=new BufferedImage(280,280,BufferedImage.TYPE_INT_RGB);
        for(int y=0;y<280;y++)for(int x=0;x<280;x++)qr.setRGB(x,y,matrix.get(x,y)?Color.BLACK.getRGB():Color.WHITE.getRGB());
        content.add(new JLabel(new ImageIcon(qr)));
        JPanel bottom=new JPanel(new BorderLayout(8,8));JTextField link=new JTextField(url);link.setEditable(false);link.setColumns(32);link.getAccessibleContext().setAccessibleName("Private phone capture link");bottom.add(link);
        bottom.add(new JLabel("Keep this workspace open. Link expires in 15 minutes."),BorderLayout.SOUTH);content.add(bottom,BorderLayout.SOUTH);
        current.setContentPane(content);current.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        current.addWindowListener(new WindowAdapter(){public void windowClosed(WindowEvent e){if(dialog==current){cancel();status.accept("Phone capture cancelled.");}}});
        current.pack();current.setLocationRelativeTo(owner);current.setVisible(true);
    }
    private static JsonObject request(String action,String id)throws Exception{
        JsonObject body=new JsonObject();body.addProperty("action",action);if(id!=null)body.addProperty("id",id);
        return LanApiClient.studioPhoneCapture(body);
    }
    private static void closeRemote(String id){new SwingWorker<Void,Void>(){
        protected Void doInBackground(){try{request("CANCEL",id);}catch(Exception ignored){/* Relay also expires automatically. */}return null;}
    }.execute();}
    private static String message(Exception ex){Throwable cause=ex.getCause()==null?ex:ex.getCause();return "Phone photo: "+cause.getMessage();}
}
