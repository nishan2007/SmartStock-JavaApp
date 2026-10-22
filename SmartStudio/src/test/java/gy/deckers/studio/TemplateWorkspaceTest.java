package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TemplateWorkspaceTest {
    @Test void doubleClickActivatesTheHitPhotoAfterSelectingIt()throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var workspace=new TemplateWorkspace();workspace.setSize(424,424);
            var template=new BadgeTemplate();template.widthMm=template.heightMm=100;
            var photo=new BadgeTemplate.Element();photo.type="PHOTO";photo.x=photo.y=10;photo.width=photo.height=20;template.elements.add(photo);
            var selected=new java.util.concurrent.atomic.AtomicInteger(-1);
            var activated=new java.util.concurrent.atomic.AtomicInteger(-1);
            workspace.configure(()->template,Map::of,()->null,selected::set,()->{});
            workspace.setLayerActivation(row->{assertEquals(row,selected.get());activated.set(row);return true;});
            var g=new BufferedImage(424,424,BufferedImage.TYPE_INT_RGB).createGraphics();workspace.paint(g);g.dispose();
            workspace.dispatchEvent(new MouseEvent(workspace,MouseEvent.MOUSE_PRESSED,0,0,152,152,1,false,MouseEvent.BUTTON1));
            workspace.dispatchEvent(new MouseEvent(workspace,MouseEvent.MOUSE_RELEASED,0,0,152,152,1,false,MouseEvent.BUTTON1));
            assertEquals(-1,activated.get());
            workspace.dispatchEvent(new MouseEvent(workspace,MouseEvent.MOUSE_PRESSED,0,0,152,152,2,false,MouseEvent.BUTTON1));
            assertEquals(0,activated.get());assertEquals(0,workspace.getComponentCount());
        });
    }
    @Test void bannerHandlesStretchPastOneMetreAndBeyondPageWithoutShrinking() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            double[] bounds={0,0,1000,1000};
            TemplateWorkspace workspace=new TemplateWorkspace();workspace.setSize(424,424);
            workspace.configure(new TemplateWorkspace.Design(){
                public double width(){return 10000;}public double height(){return 10000;}
                public double maximumSize(){return BannerDocuments.MAX_MM;}
                public boolean constrainSizeToPage(){return false;}
                public int size(){return 1;}
                public boolean canResize(){return true;}
                public java.awt.geom.Rectangle2D bounds(int i){return new java.awt.geom.Rectangle2D.Double(bounds[0],bounds[1],bounds[2],bounds[3]);}
                public void move(int i,double x,double y){bounds[0]=x;bounds[1]=y;}
                public void resize(int i,java.awt.geom.Rectangle2D b){bounds[0]=b.getX();bounds[1]=b.getY();bounds[2]=b.getWidth();bounds[3]=b.getHeight();}
                public void paint(java.awt.Graphics2D g,double scale){}
            },row->{},()->{});
            var graphics=new BufferedImage(424,424,BufferedImage.TYPE_INT_RGB).createGraphics();workspace.paint(graphics);graphics.dispose();
            workspace.select(0);
            drag(workspace,132,132,412,412);
            assertEquals(15000,bounds[2],.01);assertEquals(15000,bounds[3],.01);
            // A second drag from the object's interior must not snap it back to 1 metre.
            drag(workspace,212,212,252,252);
            assertEquals(2000,bounds[0],.01);assertEquals(2000,bounds[1],.01);
        });
    }
    @Test void bannerResizeSupportsHundredAndThreeHundredFootArtwork() {
        for(double width:new double[]{30480,91440}){
            var original=new java.awt.geom.Rectangle2D.Double(10,20,100,10);
            var locked=TemplateWorkspace.resized(original,3,width-100,0,true,BannerDocuments.MAX_MM);
            assertEquals(width,locked.getWidth(),.001);assertEquals(10,locked.getWidth()/locked.getHeight(),.001);
            assertEquals(original.getCenterY(),locked.getCenterY(),.001);
            var stretched=TemplateWorkspace.resized(original,3,width-100,0,false,BannerDocuments.MAX_MM);
            assertEquals(width,stretched.getWidth(),.001);assertEquals(10,stretched.getHeight(),.001);
        }
    }
    @Test void controlWheelZoomsAndFitRestoresViewportTracking() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            TemplateWorkspace workspace=new TemplateWorkspace();workspace.setSize(424,424);
            BadgeTemplate template=new BadgeTemplate();template.widthMm=template.heightMm=100;
            BadgeTemplate.Element element=new BadgeTemplate.Element();element.label="Field";element.x=element.y=10;element.width=element.height=10;template.elements.add(element);
            workspace.configure(()->template,Map::of,()->null,row->{},()->{});
            var wheel=new java.awt.event.MouseWheelEvent(workspace,MouseEvent.MOUSE_WHEEL,0,
                    MouseEvent.CTRL_DOWN_MASK,200,200,0,false,java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,3,-1);
            workspace.dispatchEvent(wheel);
            assertTrue(wheel.isConsumed());assertEquals(1.15,workspace.zoom(),.001);
            assertFalse(workspace.getScrollableTracksViewportWidth());
            workspace.setZoom(2);
            var graphics=new BufferedImage(424,424,BufferedImage.TYPE_INT_RGB).createGraphics();workspace.paint(graphics);graphics.dispose();
            drag(workspace,272,272,312,292);
            assertEquals(20,element.x,.01);assertEquals(15,element.y,.01);
            workspace.setZoom(100);assertEquals(8,workspace.zoom());
            workspace.setZoom(0);assertEquals(.25,workspace.zoom());
            workspace.setZoom(1);assertTrue(workspace.getScrollableTracksViewportWidth());assertTrue(workspace.getScrollableTracksViewportHeight());
        });
    }
    @Test void pasteboardFitsItsViewportWithoutHidingTheCardBehindScrollbars() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            TemplateWorkspace workspace=new TemplateWorkspace();workspace.setPreferredSize(new java.awt.Dimension(700,450));
            JScrollPane pane=new JScrollPane(workspace);pane.setSize(500,250);pane.doLayout();pane.getViewport().doLayout();
            assertEquals(pane.getViewport().getExtentSize(),workspace.getSize());
            assertFalse(pane.getVerticalScrollBar().isVisible());assertFalse(pane.getHorizontalScrollBar().isVisible());
        });
    }
    @Test void dragOffPageAndBackKeepsLayerEditable() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BadgeTemplate t=new BadgeTemplate();t.widthMm=100;t.heightMm=100;
            BadgeTemplate.Element e=new BadgeTemplate.Element();e.label="Field";e.x=10;e.y=10;e.width=10;e.height=10;t.elements.add(e);
            TemplateWorkspace workspace=new TemplateWorkspace();workspace.setSize(424,424);
            workspace.configure(()->t,Map::of,()->null,row->{},()->{});
            BufferedImage image=new BufferedImage(424,424,BufferedImage.TYPE_INT_RGB);
            var g=image.createGraphics();workspace.paint(g);g.dispose();
            drag(workspace,142,142,72,142);
            assertEquals(-25,e.x,.01);
            g=image.createGraphics();workspace.paint(g);g.dispose();
            drag(workspace,72,142,142,142);
            assertEquals(10,e.x,.01);assertEquals(1,t.elements.size());t.validate();
        });
    }
    private void drag(JComponent c,int x,int y,int endX,int endY) {
        c.dispatchEvent(new MouseEvent(c,MouseEvent.MOUSE_PRESSED,0,0,x,y,1,false,MouseEvent.BUTTON1));
        c.dispatchEvent(new MouseEvent(c,MouseEvent.MOUSE_DRAGGED,0,MouseEvent.BUTTON1_DOWN_MASK,endX,endY,0,false,MouseEvent.NOBUTTON));
        c.dispatchEvent(new MouseEvent(c,MouseEvent.MOUSE_RELEASED,0,0,endX,endY,1,false,MouseEvent.BUTTON1));
    }
}
