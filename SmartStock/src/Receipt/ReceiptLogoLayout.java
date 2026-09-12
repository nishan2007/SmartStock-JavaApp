package Receipt;

import java.awt.image.BufferedImage;

/** Removes unused white/transparent borders without modifying the saved logo. */
public final class ReceiptLogoLayout {
    private ReceiptLogoLayout() { }
    public static BufferedImage trim(BufferedImage image) {
        if(image==null)return null;
        int left=image.getWidth(),right=-1,top=image.getHeight(),bottom=-1;
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) {
            int pixel=image.getRGB(x,y),alpha=(pixel>>>24)&255;
            if(alpha>24&&(((pixel>>16)&255)<245||((pixel>>8)&255)<245||(pixel&255)<245)) {
                left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);
            }
        }
        if(right<left)return null;
        left=Math.max(0,left-2);top=Math.max(0,top-2);right=Math.min(image.getWidth()-1,right+2);bottom=Math.min(image.getHeight()-1,bottom+2);
        return image.getSubimage(left,top,right-left+1,bottom-top+1);
    }
}
