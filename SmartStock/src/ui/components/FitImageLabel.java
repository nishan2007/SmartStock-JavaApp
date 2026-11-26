package ui.components;

import javax.swing.*;
import java.awt.*;

/** Fits the entire preview into the current component bounds, including after resize. */
public final class FitImageLabel extends JLabel {
    public FitImageLabel(String text) {
        super(text,SwingConstants.CENTER);
        setMinimumSize(new Dimension(80,80));
        setPreferredSize(new Dimension(400,400));
    }

    @Override public void setIcon(Icon icon) {
        if(icon instanceof ImageIcon image)super.setIcon(new Icon() {
            public int getIconWidth(){return 0;}
            public int getIconHeight(){return 0;}
            public void paintIcon(Component component,Graphics graphics,int x,int y) {
                Insets insets=getInsets();
                int availableWidth=getWidth()-insets.left-insets.right-12;
                int availableHeight=getHeight()-insets.top-insets.bottom-12;
                if(availableWidth<=0||availableHeight<=0||image.getIconWidth()<=0||image.getIconHeight()<=0)return;
                double scale=Math.min((double)availableWidth/image.getIconWidth(),(double)availableHeight/image.getIconHeight());
                int width=Math.max(1,(int)(image.getIconWidth()*scale)),height=Math.max(1,(int)(image.getIconHeight()*scale));
                Graphics2D g=(Graphics2D)graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(image.getImage(),insets.left+6+(availableWidth-width)/2,insets.top+6+(availableHeight-height)/2,width,height,component);
                } finally {g.dispose();}
            }
        });
        else super.setIcon(icon);
    }
}
