package ui.screens.customorders;

import java.awt.*;

/** Flow controls wrap and report their actual height at the available width. */
final class OrderDetailsWrapLayout extends FlowLayout {
    OrderDetailsWrapLayout(){super(FlowLayout.LEFT,8,6);}
    @Override public Dimension preferredLayoutSize(Container target){synchronized(target.getTreeLock()){
        int width=target.getWidth();Container parent=target.getParent();while(width<=0&&parent!=null){width=parent.getWidth();parent=parent.getParent();}if(width<=0)width=600;
        Insets in=target.getInsets();int available=Math.max(1,width-in.left-in.right-2*getHgap()),rowWidth=0,rowHeight=0,height=getVgap()*2;
        for(Component c:target.getComponents())if(c.isVisible()){Dimension d=c.getPreferredSize();if(rowWidth>0&&rowWidth+getHgap()+d.width>available){height+=rowHeight+getVgap();rowWidth=0;rowHeight=0;}rowWidth+= (rowWidth>0?getHgap():0)+d.width;rowHeight=Math.max(rowHeight,d.height);}return new Dimension(width,height+rowHeight+in.top+in.bottom);
    }}
    @Override public Dimension minimumLayoutSize(Container target){return preferredLayoutSize(target);}
}
