package gy.deckers.studio;

import javax.swing.*;
import ui.design.DeckersPalette;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.*;
import java.util.function.*;

/** Shared pasteboard: model coordinates remain relative to the printable page. */
final class TemplateWorkspace extends JPanel implements Scrollable {
    interface Design {
        double width(); double height(); int size();
        Rectangle2D bounds(int index);
        void move(int index, double x, double y);
        default boolean canResize(){return false;}
        default double maximumSize(){return 1000;}
        default boolean constrainSizeToPage(){return true;}
        default void resize(int index,Rectangle2D bounds){}
        default void remove(int index){}
        default boolean locked(int index){return false;}
        default String text(int index){return null;}
        default boolean setText(int index,String value){return false;}
        void paint(Graphics2D graphics, double scale) throws Exception;
    }
    private Design design;
    private IntFunction<Boolean> aspectRatioPolicy=i->null;
    void setAspectRatioPolicy(IntFunction<Boolean> policy){aspectRatioPolicy=policy;}
    private IntConsumer selection;
    private Runnable changed;
    private int selected=-1, dragged=-1;
    private final SortedSet<Integer> selectedItems=new TreeSet<>();
    private final Map<Integer,Rectangle2D> dragBounds=new LinkedHashMap<>();
    private boolean notifyingSelection;
    private Point marqueeEnd;
    private boolean marqueeAdd;
    private final Set<Integer> marqueeStart=new TreeSet<>();
    int[] selectedIndices(){return selectedItems.stream().mapToInt(Integer::intValue).toArray();}
    private void notifySelection(){notifyingSelection=true;try{selection.accept(selected);}finally{notifyingSelection=false;}}
    private double scale=1, originX, originY;
    private Point pressed;
    private Point panScreen,panStart;
    boolean gestureActive(){return pressed!=null;}
    private double zoom=1;
    private boolean pageFocus;
    private int resizeHandle=-1;
    private Rectangle2D startBounds;
    private static final int HANDLE=8;
    private JTextArea textEditor;
    private int editing=-1;
    private IntPredicate layerActivation=i->false;
    void setLayerActivation(IntPredicate action){layerActivation=action;}
    private BiConsumer<String,String> fieldEditor;
    void setFieldEditor(BiConsumer<String,String> editor){fieldEditor=editor;}

    void setPageFocus(boolean value){pageFocus=value;revalidate();repaint();}

    double zoom(){return zoom;}
    void zoomBy(double factor){setZoom(zoom*factor);}
    void setZoom(double value){
        if(pressed!=null || !Double.isFinite(value))return;
        double old=zoom;zoom=Math.max(.25,Math.min(8,value));
        firePropertyChange("zoom",old,zoom);revalidate();repaint();
    }
    @Override public Dimension getPreferredSize(){
        Dimension base=getParent() instanceof JViewport viewport?viewport.getExtentSize():super.getPreferredSize();
        return new Dimension((int)Math.ceil(base.width*Math.max(1,zoom)),(int)Math.ceil(base.height*Math.max(1,zoom)));
    }

    public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
    public int getScrollableUnitIncrement(Rectangle visible,int orientation,int direction){return 16;}
    public int getScrollableBlockIncrement(Rectangle visible,int orientation,int direction){return orientation==SwingConstants.HORIZONTAL?visible.width:visible.height;}
    public boolean getScrollableTracksViewportWidth(){return zoom<=1;}
    public boolean getScrollableTracksViewportHeight(){return zoom<=1;}

    TemplateWorkspace() {
        setFocusable(true);
        setLayout(null);
        addMouseWheelListener(event->{
            if(event.isControlDown()){
                zoomBy(Math.pow(1.15,-event.getPreciseWheelRotation()));event.consume();
            }else{
                // A wheel listener intercepts ordinary scrolling too; pass it to the page.
                for(Container parent=getParent();parent!=null;parent=parent.getParent()){
                    if(parent instanceof JScrollPane pane && pane.isWheelScrollingEnabled()){
                        Point point=SwingUtilities.convertPoint(this,event.getPoint(),pane);
                        pane.dispatchEvent(new MouseWheelEvent(pane,event.getID(),event.getWhen(),event.getModifiersEx(),
                                point.x,point.y,event.getXOnScreen(),event.getYOnScreen(),event.getClickCount(),event.isPopupTrigger(),
                                event.getScrollType(),event.getScrollAmount(),event.getWheelRotation(),event.getPreciseWheelRotation()));
                        event.consume();break;
                    }
                }
            }
        });
        bindMove("LEFT", -.1, 0);bindMove("RIGHT", .1, 0);
        bindMove("UP", 0, -.1);bindMove("DOWN", 0, .1);
        bindMove("shift LEFT", -1, 0);bindMove("shift RIGHT", 1, 0);
        bindMove("shift UP", 0, -1);bindMove("shift DOWN", 0, 1);
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("DELETE"),"deleteLayer");
        getActionMap().put("deleteLayer",new AbstractAction(){public void actionPerformed(ActionEvent e){deleteSelection();}});
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke("control A"),"selectAllLayers");
        getActionMap().put("selectAllLayers",new AbstractAction(){public void actionPerformed(ActionEvent e){if(design!=null&&textEditor==null){selectIndices(java.util.stream.IntStream.range(0,design.size()).toArray());notifySelection();}}});
        MouseAdapter mouse=new MouseAdapter() {
            public void mousePressed(MouseEvent event) {
                if(SwingUtilities.isMiddleMouseButton(event)&&getParent() instanceof JViewport viewport){panScreen=new Point(event.getXOnScreen(),event.getYOnScreen());panStart=viewport.getViewPosition();return;}
                if(design==null || !SwingUtilities.isLeftMouseButton(event))return;
                if(!finishText(true))return;
                if(event.getClickCount()==2){
                    double x=(event.getX()-originX)/scale,y=(event.getY()-originY)/scale;
                    for(int i=design.size()-1;i>=0;i--)if(design.bounds(i).contains(x,y)){select(i);notifySelection();if(!layerActivation.test(i))beginText();return;}
                }
                resizeHandle=handleAt(event.getPoint());
                if(resizeHandle>=0){dragged=selected;startBounds=(Rectangle2D)design.bounds(selected).clone();pressed=event.getPoint();requestFocusInWindow();return;}
                double x=(event.getX()-originX)/scale,y=(event.getY()-originY)/scale;
                int hit=-1;
                for(int i=design.size()-1;i>=0;i--)if(design.bounds(i).contains(x,y)){hit=i;break;}
                boolean add=event.isControlDown()||event.isShiftDown();
                if(hit>=0){
                    if(add&&selectedItems.contains(hit)){selectedItems.remove(hit);selected=selectedItems.isEmpty()?-1:selectedItems.last();dragged=-1;pressed=null;notifySelection();repaint();return;}
                    if(!add&&!selectedItems.contains(hit))selectedItems.clear();selectedItems.add(hit);selected=hit;dragged=hit;
                    dragBounds.clear();for(int i:selectedItems)if(!design.locked(i))dragBounds.put(i,(Rectangle2D)design.bounds(i).clone());
                    pressed=event.getPoint();notifySelection();
                }else{
                    marqueeAdd=add;marqueeStart.clear();if(add)marqueeStart.addAll(selectedItems);else selectedItems.clear();
                    selected=selectedItems.isEmpty()?-1:selectedItems.last();pressed=event.getPoint();marqueeEnd=pressed;dragged=-1;notifySelection();
                }
                requestFocusInWindow();
                repaint();
            }
            public void mouseDragged(MouseEvent event) {
                if(panScreen!=null&&getParent() instanceof JViewport viewport){Point now=new Point(event.getXOnScreen(),event.getYOnScreen());Dimension extent=viewport.getExtentSize();viewport.setViewPosition(new Point(Math.max(0,Math.min(getWidth()-extent.width,panStart.x+panScreen.x-now.x)),Math.max(0,Math.min(getHeight()-extent.height,panStart.y+panScreen.y-now.y))));return;}
                if(pressed==null)return;
                if(marqueeEnd!=null){marqueeEnd=event.getPoint();Rectangle2D box=marquee();selectedItems.clear();if(marqueeAdd)selectedItems.addAll(marqueeStart);for(int i=0;i<design.size();i++)if(box.intersects(design.bounds(i)))selectedItems.add(i);selected=selectedItems.isEmpty()?-1:selectedItems.last();notifySelection();repaint();return;}
                if(dragged<0||dragged>=design.size())return;
                selected=dragged;
                if(resizeHandle>=0){Rectangle2D next=resized(startBounds,resizeHandle,(event.getX()-pressed.x)/scale,(event.getY()-pressed.y)/scale,aspectRatioPolicy.apply(dragged),design.maximumSize());double limit=design.constrainSizeToPage()?Math.min(1,Math.min(design.width()/next.getWidth(),design.height()/next.getHeight())):1;if(limit<1){double w=next.getWidth()*limit,h=next.getHeight()*limit;next=new Rectangle2D.Double(resizeHandle==0||resizeHandle==6||resizeHandle==7?startBounds.getMaxX()-w:next.getX(),resizeHandle<=2?startBounds.getMaxY()-h:next.getY(),w,h);}design.resize(dragged,next);changed.run();selected=dragged;repaint();return;}
                moveItems(dragBounds,(event.getX()-pressed.x)/scale,(event.getY()-pressed.y)/scale);
                changed.run();selected=dragged;repaint();
            }
            public void mouseReleased(MouseEvent event){panScreen=null;panStart=null;boolean moved=pressed!=null&&marqueeEnd==null;pressed=null;marqueeEnd=null;dragBounds.clear();dragged=-1;resizeHandle=-1;revalidate();repaint();if(moved)firePropertyChange("editCompleted",false,true);}
            public void mouseMoved(MouseEvent event){int handle=handleAt(event.getPoint());int[] cursors={Cursor.NW_RESIZE_CURSOR,Cursor.N_RESIZE_CURSOR,Cursor.NE_RESIZE_CURSOR,Cursor.E_RESIZE_CURSOR,Cursor.SE_RESIZE_CURSOR,Cursor.S_RESIZE_CURSOR,Cursor.SW_RESIZE_CURSOR,Cursor.W_RESIZE_CURSOR};setCursor(Cursor.getPredefinedCursor(handle<0?Cursor.DEFAULT_CURSOR:cursors[handle]));}
        };
        addMouseListener(mouse);addMouseMotionListener(mouse);
        setToolTipText("Ctrl/Shift-click adds or removes objects. Drag empty space to select several. Ctrl+A selects all. Drag or arrow keys move the selection; Delete removes it. Ctrl+wheel zooms.");
    }
    void configure(Design model, IntConsumer selection, Runnable changed) {
        finishText(false);
        pressed=null;marqueeEnd=null;dragBounds.clear();selectedItems.clear();dragged=-1;selected=-1;resizeHandle=-1;
        this.design=model;this.selection=selection;this.changed=changed;
    }
    private void bindMove(String key,double dx,double dy) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key),key);
        getActionMap().put(key,new AbstractAction(){public void actionPerformed(ActionEvent event){
            if(design==null || pressed!=null || selected<0 || selected>=design.size())return;
            Map<Integer,Rectangle2D> bounds=new LinkedHashMap<>();for(int i:selectedItems)if(!design.locked(i))bounds.put(i,(Rectangle2D)design.bounds(i).clone());
            if(bounds.isEmpty())return;moveItems(bounds,dx,dy);
            changed.run();revalidate();repaint();
            firePropertyChange("editCompleted",false,true);
        }});
    }
    void configure(Supplier<BadgeTemplate> template, Supplier<Map<String,String>> values,
                   Supplier<BufferedImage> photo, IntConsumer selection, Runnable changed) {
        configure(new Design() {
            public double width(){return template.get().widthMm;}
            public double height(){return template.get().heightMm;}
            public int size(){return template.get().elements.size();}
            public Rectangle2D bounds(int i){var e=template.get().elements.get(i);return new Rectangle2D.Double(e.x,e.y,e.width,e.height);}
            public void move(int i,double x,double y){var e=template.get().elements.get(i);e.x=x;e.y=y;}
            public boolean canResize(){return true;}
            public void resize(int i,Rectangle2D b){var e=template.get().elements.get(i);e.x=b.getX();e.y=b.getY();e.width=b.getWidth();e.height=b.getHeight();}
            public void remove(int i){template.get().elements.remove(i);}
            public String text(int i){var e=template.get().elements.get(i);return "TEXT".equals(e.type)?(e.field.isEmpty()?e.text:values.get().getOrDefault(e.field,e.text)):null;}
            public boolean setText(int i,String value){if(value.length()>1000)return false;var e=template.get().elements.get(i);if(!e.field.isEmpty()&&fieldEditor!=null)fieldEditor.accept(e.field,value);else e.text=value;return true;}
            public void paint(Graphics2D g,double scale)throws Exception{template.get().paintElements(g,values.get(),photo.get(),scale);}
        },selection,changed);
    }
    void select(int index){selectIndices(index<0?new int[0]:new int[]{index});}
    void selectIndices(int[] indices){if(notifyingSelection||pressed!=null)return;finishText(true);selectedItems.clear();if(design!=null)for(int i:indices)if(i>=0&&i<design.size())selectedItems.add(i);selected=selectedItems.isEmpty()?-1:selectedItems.last();repaint();}
    private Rectangle2D marquee(){double x=(Math.min(pressed.x,marqueeEnd.x)-originX)/scale,y=(Math.min(pressed.y,marqueeEnd.y)-originY)/scale;return new Rectangle2D.Double(x,y,Math.abs(pressed.x-marqueeEnd.x)/scale,Math.abs(pressed.y-marqueeEnd.y)/scale);}
    private void moveItems(Map<Integer,Rectangle2D> bounds,double dx,double dy){
        double maximum=design.maximumSize();
        for(var b:bounds.values()){dx=Math.max(-maximum-b.getX(),Math.min(maximum-b.getX(),dx));dy=Math.max(-maximum-b.getY(),Math.min(maximum-b.getY(),dy));}
        for(var e:bounds.entrySet())design.move(e.getKey(),e.getValue().getX()+dx,e.getValue().getY()+dy);
    }
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);if(design==null)return;
        double padding=pageFocus?.08:.5;
        double minX=-design.width()*padding,minY=-design.height()*padding,maxX=design.width()*(1+padding),maxY=design.height()*(1+padding);
        // Freeze the view during dragging so pointer movement keeps a stable coordinate system.
        if(pressed==null && textEditor==null){
            for(int i=0;i<design.size();i++){Rectangle2D b=design.bounds(i);minX=Math.min(minX,b.getMinX()-10);minY=Math.min(minY,b.getMinY()-10);maxX=Math.max(maxX,b.getMaxX()+10);maxY=Math.max(maxY,b.getMaxY()+10);}
            Dimension view=getParent() instanceof JViewport viewport?viewport.getExtentSize():getSize();
            if(view.width<=24 || view.height<=24)view=getSize();
            scale=zoom*Math.min((view.width-24)/(maxX-minX),(view.height-24)/(maxY-minY));
            scale=Math.max(1e-8,scale);originX=12-minX*scale;originY=12-minY*scale;
            if(pageFocus){originX+=(view.width-24-(maxX-minX)*scale)/2;originY+=(view.height-24-(maxY-minY)*scale)/2;}
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
            for(int item:selectedItems)if(item>=0 && item<design.size()){
                Rectangle2D b=design.bounds(item);g.setColor(DeckersPalette.PURPLE);
                g.draw(new Rectangle2D.Double(b.getX()*scale,b.getY()*scale,b.getWidth()*scale,b.getHeight()*scale));
                if(design.canResize()&&selectedItems.size()==1&&!design.locked(item))for(int i=0;i<8;i++){Point.Double point=handlePoint(b,i);Rectangle2D box=new Rectangle2D.Double(point.x*scale-HANDLE/2.,point.y*scale-HANDLE/2.,HANDLE,HANDLE);g.setColor(DeckersPalette.surface());g.fill(box);g.setColor(DeckersPalette.PURPLE);g.draw(box);}
            }
            if(marqueeEnd!=null){var b=marquee();g.setColor(new Color(120,70,180,35));var box=new Rectangle2D.Double(b.getX()*scale,b.getY()*scale,b.getWidth()*scale,b.getHeight()*scale);g.fill(box);g.setColor(DeckersPalette.PURPLE);g.draw(box);}
        }catch(Exception ex){g.setColor(Color.RED);g.drawString("Unable to preview template",0,15);}
        finally{g.dispose();}
    }

    void deleteSelection(){
        if(textEditor!=null)return;
        if(design==null || !design.canResize() || pressed!=null || selected<0 || selected>=design.size())return;
        int[] items=selectedIndices();if(Arrays.stream(items).allMatch(design::locked))return;
        selected=-1;selectedItems.clear();notifySelection();for(int i=items.length-1;i>=0;i--)if(!design.locked(items[i]))design.remove(items[i]);changed.run();
        firePropertyChange("layerDeleted",false,true);firePropertyChange("editCompleted",false,true);revalidate();repaint();
    }
    void beginText(){
        if(design==null||selectedItems.size()!=1||selected<0||selected>=design.size()||design.locked(selected)||textEditor!=null)return;
        String value=design.text(selected);if(value==null)return;
        editing=selected;textEditor=new JTextArea(value);textEditor.setLineWrap(true);textEditor.setWrapStyleWord(true);
        Rectangle2D b=design.bounds(selected);
        textEditor.setBounds((int)(originX+b.getX()*scale),(int)(originY+b.getY()*scale),Math.max(80,(int)(b.getWidth()*scale)),Math.max(30,(int)(b.getHeight()*scale)));
        textEditor.setBorder(BorderFactory.createLineBorder(DeckersPalette.PURPLE,2));
        textEditor.getInputMap().put(KeyStroke.getKeyStroke("ESCAPE"),"cancelInline");
        textEditor.getActionMap().put("cancelInline",new AbstractAction(){public void actionPerformed(ActionEvent e){finishText(false);requestFocusInWindow();}});
        textEditor.getInputMap().put(KeyStroke.getKeyStroke("control ENTER"),"commitInline");
        textEditor.getActionMap().put("commitInline",new AbstractAction(){public void actionPerformed(ActionEvent e){if(finishText(true))requestFocusInWindow();}});
        textEditor.addFocusListener(new FocusAdapter(){public void focusLost(FocusEvent e){finishText(true);}});
        add(textEditor);textEditor.requestFocusInWindow();textEditor.selectAll();repaint();
    }
    boolean finishText(boolean commit){
        if(textEditor==null)return true;
        JTextArea editor=textEditor;int index=editing;
        boolean modified=commit&&design!=null&&index>=0&&index<design.size()&&!editor.getText().equals(design.text(index));
        if(modified&&!design.setText(index,editor.getText())){editor.setToolTipText("Text is too long or invalid.");editor.requestFocusInWindow();return false;}
        textEditor=null;editing=-1;remove(editor);
        if(modified){changed.run();firePropertyChange("editCompleted",false,true);}
        revalidate();repaint();return true;
    }
    private static Point.Double handlePoint(Rectangle2D b,int handle){
        double[] xs={b.getMinX(),b.getCenterX(),b.getMaxX(),b.getMaxX(),b.getMaxX(),b.getCenterX(),b.getMinX(),b.getMinX()};
        double[] ys={b.getMinY(),b.getMinY(),b.getMinY(),b.getCenterY(),b.getMaxY(),b.getMaxY(),b.getMaxY(),b.getCenterY()};
        return new Point.Double(xs[handle],ys[handle]);
    }
    private int handleAt(Point point){
        if(design==null || selectedItems.size()!=1 || !design.canResize() || selected<0 || selected>=design.size()||design.locked(selected))return -1;
        Rectangle2D bounds=design.bounds(selected);
        // Corners take precedence when a very small object has overlapping handles.
        for(int i:new int[]{0,2,4,6,1,3,5,7}){Point.Double h=handlePoint(bounds,i);if(Math.abs(point.x-originX-h.x*scale)<=HANDLE/2.+2&&Math.abs(point.y-originY-h.y*scale)<=HANDLE/2.+2)return i;}
        return -1;
    }
    static Rectangle2D resized(Rectangle2D b,int handle,double dx,double dy){return resized(b,handle,dx,dy,null);}
    static Rectangle2D resized(Rectangle2D b,int handle,double dx,double dy,Boolean locked){return resized(b,handle,dx,dy,locked,1000);}
    static Rectangle2D resized(Rectangle2D b,int handle,double dx,double dy,Boolean locked,double maximum){
        boolean left=handle==0||handle==6||handle==7,right=handle==2||handle==3||handle==4;
        boolean top=handle==0||handle==1||handle==2,bottom=handle==4||handle==5||handle==6;
        double width=b.getWidth(),height=b.getHeight();
        double w=width+(left?-dx:right?dx:0),h=height+(top?-dy:bottom?dy:0);
        if(Boolean.TRUE.equals(locked)||(locked==null&&handle%2==0)){
            double factor=handle%2==0?(w*width+h*height)/(width*width+height*height):(left||right)?w/width:h/height;
            factor=Math.max(Math.max(.1/width,.1/height),Math.min(Math.min(maximum/width,maximum/height),factor));
            w=width*factor;h=height*factor;
        }else{w=Math.max(.1,Math.min(maximum,w));h=Math.max(.1,Math.min(maximum,h));}
        double x=left?b.getMaxX()-w:b.getX(),y=top?b.getMaxY()-h:b.getY();
        if(Boolean.TRUE.equals(locked)){if(!left&&!right)x=b.getCenterX()-w/2;if(!top&&!bottom)y=b.getCenterY()-h/2;}
        return new Rectangle2D.Double(x,y,w,h);
    }
}
