package gy.deckers.studio;

import javax.swing.*;
import ui.design.DeckersPalette;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.function.*;

/** Shared pasteboard: model coordinates remain relative to the printable page. */
final class TemplateWorkspace extends JPanel {
    interface Design {
        double width(); double height(); int size();
        Rectangle2D bounds(int index);
        void move(int index, double x, double y);
        void paint(Graphics2D graphics, double scale) throws Exception;
    }
    private Design design;
    private IntConsumer selection;
    private Runnable changed;
    private int selected=-1;
    private double scale=1, originX, originY, startX, startY;
    private Point pressed;

    TemplateWorkspace() {
        MouseAdapter mouse=new MouseAdapter() {
            public void mousePressed(MouseEvent event) {
                if(design==null || !SwingUtilities.isLeftMouseButton(event))return;
                double x=(event.getX()-originX)/scale,y=(event.getY()-originY)/scale;
                selected=-1;
                for(int i=design.size()-1;i>=0;i--)if(design.bounds(i).contains(x,y)){selected=i;break;}
                if(selected>=0){selection.accept(selected);Rectangle2D b=design.bounds(selected);startX=b.getX();startY=b.getY();pressed=event.getPoint();}
                else pressed=null;
                repaint();
            }
            public void mouseDragged(MouseEvent event) {
                if(pressed==null || selected<0)return;
                design.move(selected,Math.max(-1000,Math.min(1000,startX+(event.getX()-pressed.x)/scale)),
                        Math.max(-1000,Math.min(1000,startY+(event.getY()-pressed.y)/scale)));
                changed.run();repaint();
            }
            public void mouseReleased(MouseEvent event){pressed=null;revalidate();repaint();}
        };
        addMouseListener(mouse);addMouseMotionListener(mouse);
        setToolTipText("White area prints. Drag fields into the surrounding workspace to park them.");
    }
    void configure(Design model, IntConsumer selection, Runnable changed) {
        this.design=model;this.selection=selection;this.changed=changed;
    }
    void configure(Supplier<BadgeTemplate> template, Supplier<Map<String,String>> values,
                   Supplier<BufferedImage> photo, IntConsumer selection, Runnable changed) {
        configure(new Design() {
            public double width(){return template.get().widthMm;}
            public double height(){return template.get().heightMm;}
            public int size(){return template.get().elements.size();}
            public Rectangle2D bounds(int i){var e=template.get().elements.get(i);return new Rectangle2D.Double(e.x,e.y,e.width,e.height);}
            public void move(int i,double x,double y){var e=template.get().elements.get(i);e.x=x;e.y=y;}
            public void paint(Graphics2D g,double scale)throws Exception{template.get().paintElements(g,values.get(),photo.get(),scale);}
        },selection,changed);
    }
    void select(int index){selected=index;repaint();}
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);if(design==null)return;
        double minX=-design.width()/2,minY=-design.height()/2,maxX=design.width()*1.5,maxY=design.height()*1.5;
        // Freeze the view during dragging so pointer movement keeps a stable coordinate system.
        if(pressed==null){
            for(int i=0;i<design.size();i++){Rectangle2D b=design.bounds(i);minX=Math.min(minX,b.getMinX()-10);minY=Math.min(minY,b.getMinY()-10);maxX=Math.max(maxX,b.getMaxX()+10);maxY=Math.max(maxY,b.getMaxY()+10);}
            scale=Math.min((getWidth()-24)/(maxX-minX),(getHeight()-24)/(maxY-minY));
            scale=Math.max(.01,scale);originX=12-minX*scale;originY=12-minY*scale;
        }
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setColor(new Color(225,228,233));g.fillRect(0,0,getWidth(),getHeight());
            g.translate(originX,originY);
            Rectangle2D page=new Rectangle2D.Double(0,0,design.width()*scale,design.height()*scale);
            g.setColor(Color.WHITE);g.fill(page);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            design.paint(g,scale);
            g.setColor(new Color(90,95,105));g.setStroke(new BasicStroke(1.5f));g.draw(page);
            if(selected>=0 && selected<design.size()){
                Rectangle2D b=design.bounds(selected);g.setColor(DeckersPalette.PURPLE);
                g.draw(new Rectangle2D.Double(b.getX()*scale,b.getY()*scale,b.getWidth()*scale,b.getHeight()*scale));
            }
        }catch(Exception ex){g.setColor(Color.RED);g.drawString("Unable to preview template",0,15);}
        finally{g.dispose();}
    }
}
