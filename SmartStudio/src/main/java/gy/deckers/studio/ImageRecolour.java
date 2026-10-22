package gy.deckers.studio;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** Pixel edits create a new asset; geometry, transparency and the input remain unchanged. */
final class ImageRecolour {
    enum Mode { TINT, REPLACE, GRAYSCALE }
    record Settings(Mode mode,Color from,Color to,int tolerance,boolean preserveShades){
        Settings {if(mode==null||from==null||to==null||tolerance<0||tolerance>100)throw new IllegalArgumentException("Invalid recolour settings.");}
    }
    static BufferedImage apply(BufferedImage source,Settings settings){
        BufferedImage result=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
        double limit=settings.tolerance()*Math.sqrt(3)*255/100.;
        for(int y=0;y<source.getHeight();y++){if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();for(int x=0;x<source.getWidth();x++){
            int pixel=source.getRGB(x,y),a=pixel>>>24,r=(pixel>>16)&255,g=(pixel>>8)&255,b=pixel&255;
            if(a==0){result.setRGB(x,y,pixel);continue;}
            double gray=.2126*r+.7152*g+.0722*b;
            if(settings.mode()==Mode.GRAYSCALE)r=g=b=(int)Math.round(gray);
            else if(settings.mode()==Mode.TINT){double tone=settings.preserveShades()?gray/255.:0;r=shade(settings.to().getRed(),tone);g=shade(settings.to().getGreen(),tone);b=shade(settings.to().getBlue(),tone);}
            else {double dr=r-settings.from().getRed(),dg=g-settings.from().getGreen(),db=b-settings.from().getBlue();
                if(Math.sqrt(dr*dr+dg*dg+db*db)<=limit){r=clamp(settings.to().getRed()+(settings.preserveShades()?(int)dr:0));g=clamp(settings.to().getGreen()+(settings.preserveShades()?(int)dg:0));b=clamp(settings.to().getBlue()+(settings.preserveShades()?(int)db:0));}}
            result.setRGB(x,y,(a<<24)|(r<<16)|(g<<8)|b);
        }}
        return result;
    }
    private static int shade(int ink,double tone){return clamp((int)Math.round(ink+(255-ink)*tone));}
    private static int clamp(int channel){return Math.max(0,Math.min(255,channel));}
    static byte[] png(BufferedImage image)throws IOException{var bytes=new ByteArrayOutputStream();if(!ImageIO.write(image,"png",bytes))throw new IOException("PNG encoding is unavailable.");return bytes.toByteArray();}
}
