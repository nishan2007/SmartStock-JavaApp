package gy.deckers.studio;

import java.awt.image.BufferedImage;

/** Basic color corrections, always calculated from the original pixels. */
record PhotoAdjustments(int brightness,int contrast,int saturation,int warmth) {
    static final PhotoAdjustments NONE=new PhotoAdjustments(0,0,0,0);
    PhotoAdjustments {
        if(Math.abs((long)brightness)>100||Math.abs((long)contrast)>100||Math.abs((long)saturation)>100||Math.abs((long)warmth)>100)
            throw new IllegalArgumentException("Photo adjustments must be between -100 and 100");
    }
    BufferedImage apply(BufferedImage source){
        if(equals(NONE))return source;
        BufferedImage result=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
        double c=Math.pow(2,contrast/100.),s=1+saturation/100.,b=brightness*1.275,w=warmth*.51;
        int[] row=new int[source.getWidth()];
        for(int y=0;y<source.getHeight();y++){
            source.getRGB(0,y,row.length,1,row,0,row.length);
            for(int x=0;x<row.length;x++){
                int pixel=row[x];double r=(pixel>>>16)&255,g=(pixel>>>8)&255,blue=pixel&255;
                double luminance=.2126*r+.7152*g+.0722*blue;
                r=((luminance+(r-luminance)*s)-127.5)*c+127.5+b+w;
                g=((luminance+(g-luminance)*s)-127.5)*c+127.5+b;
                blue=((luminance+(blue-luminance)*s)-127.5)*c+127.5+b-w;
                row[x]=(pixel&0xff000000)|(channel(r)<<16)|(channel(g)<<8)|channel(blue);
            }
            result.setRGB(0,y,row.length,1,row,0,row.length);
        }
        return result;
    }
    private static int channel(double value){return (int)Math.max(0,Math.min(255,Math.round(value)));}
}
