package gy.deckers.studio;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

/** Shows the entire imposed sheet, independent of printer dimensions or window size. */
final class SheetPreview extends JPanel {
    private BufferedImage image;
    SheetPreview(){setBackground(new Color(225,228,233));setMinimumSize(new Dimension(120,120));}
    void setImage(BufferedImage value){image=value;repaint();}
    static Rectangle fit(int width,int height,int imageWidth,int imageHeight){
        double scale=Math.min(Math.max(1,width-32)/(double)imageWidth,Math.max(1,height-32)/(double)imageHeight);
        int w=Math.max(1,(int)Math.floor(imageWidth*scale)),h=Math.max(1,(int)Math.floor(imageHeight*scale));
        return new Rectangle((width-w)/2,(height-h)/2,w,h);
    }
    @Override protected void paintComponent(Graphics graphics){
        super.paintComponent(graphics);if(image==null)return;
        Rectangle page=fit(getWidth(),getHeight(),image.getWidth(),image.getHeight());Graphics2D g=(Graphics2D)graphics.create();
        try{g.setColor(new Color(150,155,165));g.fillRect(page.x+4,page.y+4,page.width,page.height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(image,page.x,page.y,page.width,page.height,null);g.setColor(Color.DARK_GRAY);g.drawRect(page.x,page.y,page.width,page.height);
        }finally{g.dispose();}
    }
}
