package gy.deckers.studio;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Isolated login renders: no claimed live connection, credentials, or NFC hardware. */
public final class PreviewLogin {
    static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container n)layout(n);}
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args[0]).toAbsolutePath();Files.createDirectories(output);
        System.setProperty("user.home",output.resolve("profile").toString());System.setProperty("smartstudio.badge.folder",output.resolve("profile/badges").toString());System.setProperty("smartstock.environment","development");System.setProperty("smartstock.db.mode","CLIENT");
        SwingUtilities.invokeAndWait(()->{
            SmartStudio studio=new SmartStudio(false);
            try{
                var show=SmartStudio.class.getDeclaredMethod("showPage",String.class);show.setAccessible(true);show.invoke(studio,"login");
                for(int[] size:new int[][]{{1366,768},{1920,1080},{910,512}}){
                    studio.setSize(size[0],size[1]);studio.addNotify();studio.validate();layout(studio.getContentPane());
                    BufferedImage image=new BufferedImage(studio.getContentPane().getWidth(),studio.getContentPane().getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();studio.getContentPane().printAll(g);g.dispose();ImageIO.write(image,"png",output.resolve("login-"+size[0]+".png").toFile());
                }
            }catch(Exception ex){throw new RuntimeException(ex);}finally{studio.dispose();}
        });
    }
}
