package gy.deckers.studio;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import services.LanApiClient;
import services.StudioQuality;

/** Prepare a bounded processing copy; the editor keeps its existing crop and style. */
final class BadgePhotoBackground {
    private BadgePhotoBackground(){}
    static byte[] processingCopy(BufferedImage source)throws IOException {
        double scale=Math.min(1,2048./Math.max(source.getWidth(),source.getHeight()));
        var image=new BufferedImage(Math.max(32,(int)Math.round(source.getWidth()*scale)),Math.max(32,(int)Math.round(source.getHeight()*scale)),BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();try{
            g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(source,0,0,image.getWidth(),image.getHeight(),null);
        }finally{g.dispose();}
        var bytes=new ByteArrayOutputStream();
        if(!ImageIO.write(image,"jpeg",bytes))throw new IOException("Could not prepare photo for background removal.");
        if(bytes.size()>6*1024*1024)throw new IOException("Photo is too large for background removal.");
        return bytes.toByteArray();
    }
    static BufferedImage remove(BufferedImage source)throws Exception {
        byte[] result=LanApiClient.transformStudioPhoto(processingCopy(source),StudioQuality.BEST,false,true,1);
        var image=ImageIO.read(new ByteArrayInputStream(result));
        if(image==null)throw new IOException("The server returned an unreadable photo.");
        return image;
    }
}
