package ui.components;

import services.AdDraftRenderer;
import javax.swing.*;
import java.awt.*;

/** Review-only size guide. Always fits the complete source without cropping or stretching. */
public final class AdSizePreviewLabel extends JLabel {
    private Dimension target=AdDraftRenderer.size("ad_square");
    private String format="Square (1:1)";
    private ImageIcon source;

    public AdSizePreviewLabel(String text) {
        super(text,SwingConstants.CENTER);
        setMinimumSize(new Dimension(160,160));setPreferredSize(new Dimension(400,400));
    }
    public void setFormat(String kind) {
        target=AdDraftRenderer.size(kind);
        format=switch(kind){case "ad_square"->"Square (1:1)";case "ad_portrait"->"Portrait (4:5)";case "ad_story"->"Story (9:16)";case "ad_landscape"->"Landscape (16:9)";default->throw new IllegalArgumentException("Unsupported ad format");};
        setToolTipText(format+" · Target "+target.width+" × "+target.height+" px · Red frame is a preview guide only");repaint();
    }
    @Override public void setIcon(Icon icon) {
        source=icon instanceof ImageIcon image?image:null;
        if(source==null){super.setIcon(icon);return;}
        super.setIcon(new Icon(){
            public int getIconWidth(){return 0;}
            public int getIconHeight(){return 0;}
            public void paintIcon(Component component,Graphics graphics,int x,int y) {
                if(source==null||source.getIconWidth()<=0||source.getIconHeight()<=0)return;
                Rectangle frame=frameBounds();
                double scale=Math.min((double)frame.width/source.getIconWidth(),(double)frame.height/source.getIconHeight());
                int width=Math.max(1,(int)(source.getIconWidth()*scale)),height=Math.max(1,(int)(source.getIconHeight()*scale));
                Graphics2D g=(Graphics2D)graphics.create();
                try{g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);g.drawImage(source.getImage(),frame.x+(frame.width-width)/2,frame.y+(frame.height-height)/2,width,height,component);}finally{g.dispose();}
            }
        });
    }
    Rectangle frameBounds() {
        Insets insets=getInsets();int width=Math.max(1,getWidth()-insets.left-insets.right-32),height=Math.max(1,getHeight()-insets.top-insets.bottom-76);
        double scale=Math.min((double)width/target.width,(double)height/target.height);
        int w=Math.max(1,(int)Math.round(target.width*scale)),h=Math.max(1,(int)Math.round(target.height*scale));
        return new Rectangle(insets.left+16+(width-w)/2,insets.top+38+(height-h)/2,w,h);
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);if(target==null)return;
        Rectangle frame=frameBounds();Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setColor(Color.RED);g.setStroke(new BasicStroke(2));g.drawRect(frame.x,frame.y,frame.width,frame.height);
            g.setFont(getFont().deriveFont(Font.BOLD,12f));
            Insets insets=getInsets();g.drawString(format+" · "+target.width+" × "+target.height+" px",insets.left+16,insets.top+20);
            if(source!=null){g.setColor(getForeground());g.setFont(getFont().deriveFont(Font.PLAIN,12f));g.drawString("Actual: "+source.getIconWidth()+" × "+source.getIconHeight()+" px",insets.left+16,getHeight()-insets.bottom-12);}
        }finally{g.dispose();}
    }
}
