package services;

import java.awt.*;
import java.awt.image.BufferedImage;

/** Local template composition only; this is not an AI image generator. */
public final class AdDraftRenderer {
    private AdDraftRenderer() { }

    public static Dimension size(String kind) {
        return switch (kind) {
            case "ad_square" -> new Dimension(1080,1080);
            case "ad_portrait" -> new Dimension(1080,1350);
            case "ad_story" -> new Dimension(1080,1920);
            case "ad_landscape" -> new Dimension(1920,1080);
            default -> throw new IllegalArgumentException("Choose a supported ad format.");
        };
    }

    public static void validate(String kind, BufferedImage image) {
        Dimension target=size(kind);
        if(image==null || image.getWidth()>8000 || image.getHeight()>8000 ||
                Math.abs((double)image.getWidth()/image.getHeight()-(double)target.width/target.height)>0.03)
            throw new IllegalArgumentException("Choose an image matching the selected ad format (maximum 8000 pixels per side).");
    }

    public static BufferedImage render(String kind, BufferedImage source, String headline) {
        if(source==null) throw new IllegalArgumentException("A product photo is required.");
        Dimension size=size(kind);
        BufferedImage result=new BufferedImage(size.width,size.height,BufferedImage.TYPE_INT_RGB);
        Graphics2D g=result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);g.fillRect(0,0,size.width,size.height);
            int margin=64, footer=200;
            double scale=Math.min((double)(size.width-margin*2)/source.getWidth(),(double)(size.height-footer-margin*2)/source.getHeight());
            int w=(int)(source.getWidth()*scale),h=(int)(source.getHeight()*scale);
            g.drawImage(source,(size.width-w)/2,margin+(size.height-footer-margin*2-h)/2,w,h,null);
            g.setColor(Color.DARK_GRAY);
            int fontSize=52;
            do { g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,fontSize--)); }
            while(fontSize>16 && g.getFontMetrics().stringWidth(headline)>size.width-margin*2);
            g.drawString(headline,margin,size.height-100);
        } finally { g.dispose(); }
        return result;
    }
}
