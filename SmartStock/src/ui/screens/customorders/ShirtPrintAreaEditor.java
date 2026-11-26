package ui.screens.customorders;

import com.google.gson.JsonObject;
import services.ShirtDesignerService;
import ui.design.DeckersPalette;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.LinkedHashMap;
import java.util.Map;

/** Edits the existing, server-validated shirt configuration; no separate store. */
final class ShirtPrintAreaEditor extends JPanel {
    private static final String[] KEYS={"x","y","w","h","widthIn","heightIn"};
    private static final String[] LABELS={"Left (%)","Top (%)","Width (%)","Height (%)","Print width (inches)","Print height (inches)"};
    private final JsonObject config;
    private final JComboBox<String> areaChoice=new JComboBox<>(new String[]{"Large front","Left chest logo","Large back"});
    private final Map<String,JSpinner> controls=new LinkedHashMap<>();
    private final Preview preview=new Preview();
    private boolean updating;

    ShirtPrintAreaEditor(JsonObject supplied){
        super(new BorderLayout(12,12));config=initialConfiguration(supplied);
        setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        JPanel settings=new JPanel(new GridBagLayout());int row=0;
        row=addRow(settings,row,"Print area",areaChoice);
        for(int i=0;i<4;i++){
            String key=KEYS[i];boolean inches=key.endsWith("In");
            JSpinner spinner=new JSpinner(new SpinnerNumberModel(1.0,inches?.1:0.0,inches?60.0:100.0,inches?.1:.5));
            spinner.setEditor(new JSpinner.NumberEditor(spinner,"0.##"));spinner.setName(key);spinner.getAccessibleContext().setAccessibleName(LABELS[i]);
            controls.put(key,spinner);row=addRow(settings,row,LABELS[i],spinner);
            spinner.addChangeListener(e->{if(!updating){double value=((Number)spinner.getValue()).doubleValue();setValue(key,key.endsWith("In")?value:value/100);sync();}});
        }
        JButton reset=new JButton("Reset this area");reset.addActionListener(e->{config.getAsJsonObject("areas").add(area(),defaultArea(area(),boundary()));sync();});
        row=addRow(settings,row,"",reset);
        JLabel instructions=new JLabel("<html>Drag the box to move it.<br>Pull a corner to resize it.<br>Smaller Top values move the area higher.<br><br>Guide sizes are set in Print Materials presets.<br>Positions are saved for this shirt and its variants.<br>Save the item to publish your changes.</html>");
        addRow(settings,row,"",instructions);
        areaChoice.addActionListener(e->sync());
        add(preview,BorderLayout.CENTER);add(settings,BorderLayout.EAST);sync();
    }
    JsonObject configuration(){
        try{for(JSpinner spinner:controls.values())spinner.commitEdit();}
        catch(java.text.ParseException failure){throw new IllegalArgumentException("Enter valid positions and print dimensions.",failure);}
        for(String name:ShirtDesignerService.AREAS){JsonObject b=config.getAsJsonObject("areas").getAsJsonObject(name);
            for(String key:KEYS)if(!Double.isFinite(number(b,key)))throw new IllegalArgumentException("Print-area values must be finite numbers.");
            if(number(b,"x")<0||number(b,"y")<0||number(b,"w")<=0||number(b,"h")<=0||number(b,"x")+number(b,"w")>1.000000001||number(b,"y")+number(b,"h")>1.000000001)throw new IllegalArgumentException("Keep each print area inside the shirt preview.");
        }return config.deepCopy();
    }
    String area(){return ShirtDesignerService.AREAS.get(areaChoice.getSelectedIndex());}
    JsonObject boundary(){return config.getAsJsonObject("areas").getAsJsonObject(area());}
    static JsonObject initialConfiguration(JsonObject supplied){
        JsonObject copy=supplied.deepCopy();
        for(String name:ShirtDesignerService.AREAS){JsonObject b=copy.getAsJsonObject("areas").getAsJsonObject(name);
            if(number(b,"widthIn")<=0||number(b,"heightIn")<=0)copy.getAsJsonObject("areas").add(name,defaultArea(name,b));
        }return copy;
    }
    private static JsonObject defaultArea(String name,JsonObject original){
        JsonObject result=original.deepCopy();boolean chest=name.equals("CHEST");
        result.addProperty("x",chest?.54:.30);result.addProperty("y",chest?.27:.31);
        result.addProperty("w",chest?.12:.40);result.addProperty("h",chest?.12:.44);
        result.addProperty("widthIn",chest?3:10);result.addProperty("heightIn",chest?3:11);return result;
    }
    static double number(JsonObject b,String key){return b.get(key).getAsDouble();}
    void setValue(String key,double value){
        JsonObject b=boundary();
        if(key.equals("x"))value=Math.max(0,Math.min(1-number(b,"w"),value));
        if(key.equals("y"))value=Math.max(0,Math.min(1-number(b,"h"),value));
        if(key.equals("w"))value=Math.max(.01,Math.min(1-number(b,"x"),value));
        if(key.equals("h"))value=Math.max(.01,Math.min(1-number(b,"y"),value));
        b.addProperty(key,value);
    }
    private void sync(){
        updating=true;try{for(var entry:controls.entrySet()){double n=number(boundary(),entry.getKey());entry.getValue().setValue(entry.getKey().endsWith("In")?n:n*100);}}finally{updating=false;}
        preview.repaint();
    }
    private static int addRow(JPanel panel,int row,String label,JComponent component){
        GridBagConstraints c=new GridBagConstraints();c.gridy=row;c.gridx=0;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(6,6,6,6);panel.add(new JLabel(label),c);
        c.gridx=1;c.fill=GridBagConstraints.HORIZONTAL;c.weightx=1;panel.add(component,c);return row+1;
    }
    private final class Preview extends JPanel {
        private final Map<String,BufferedImage> images=new LinkedHashMap<>();
        private Rectangle2D.Double before;
        private Point start;
        private int corner=-1;
        Preview(){
            setPreferredSize(new Dimension(470,470));setMinimumSize(new Dimension(280,280));
            getAccessibleContext().setAccessibleName("Shirt print area position preview");
            MouseAdapter mouse=new MouseAdapter(){
                @Override public void mousePressed(MouseEvent e){
                    Rectangle2D.Double box=box(boundary());corner=cornerAt(e.getPoint(),box);
                    if(corner<0&&!box.contains(e.getPoint()))return;
                    start=e.getPoint();before=box;
                }
                @Override public void mouseDragged(MouseEvent e){
                    if(start==null)return;double scale=side(),dx=(e.getX()-start.x)/scale,dy=(e.getY()-start.y)/scale;
                    double x=(before.x-offsetX())/scale,y=(before.y-offsetY())/scale,w=before.width/scale,h=before.height/scale;
                    if(corner<0){x=Math.max(0,Math.min(1-w,x+dx));y=Math.max(0,Math.min(1-h,y+dy));}
                    else{
                        boolean left=corner==0||corner==3,top=corner==0||corner==1;
                        double right=x+w,bottom=y+h;
                        if(left){x=Math.max(0,Math.min(right-.01,x+dx));w=right-x;}else w=Math.max(.01,Math.min(1-x,w+dx));
                        if(top){y=Math.max(0,Math.min(bottom-.01,y+dy));h=bottom-y;}else h=Math.max(.01,Math.min(1-y,h+dy));
                    }
                    JsonObject b=boundary();b.addProperty("x",x);b.addProperty("y",y);b.addProperty("w",w);b.addProperty("h",h);sync();
                }
                @Override public void mouseReleased(MouseEvent e){start=null;before=null;}
                @Override public void mouseMoved(MouseEvent e){int handle=cornerAt(e.getPoint(),box(boundary()));setCursor(Cursor.getPredefinedCursor(handle>=0?(handle%2==0?Cursor.NW_RESIZE_CURSOR:Cursor.NE_RESIZE_CURSOR):box(boundary()).contains(e.getPoint())?Cursor.MOVE_CURSOR:Cursor.DEFAULT_CURSOR));}
            };addMouseListener(mouse);addMouseMotionListener(mouse);
        }
        double side(){return Math.min(getWidth(),getHeight());}
        double offsetX(){return (getWidth()-side())/2;}
        double offsetY(){return (getHeight()-side())/2;}
        Rectangle2D.Double box(JsonObject b){return new Rectangle2D.Double(offsetX()+number(b,"x")*side(),offsetY()+number(b,"y")*side(),number(b,"w")*side(),number(b,"h")*side());}
        Point[] corners(Rectangle2D.Double b){return new Point[]{new Point((int)b.x,(int)b.y),new Point((int)b.getMaxX(),(int)b.y),new Point((int)b.getMaxX(),(int)b.getMaxY()),new Point((int)b.x,(int)b.getMaxY())};}
        int cornerAt(Point p,Rectangle2D.Double b){Point[] points=corners(b);for(int i=0;i<points.length;i++)if(points[i].distance(p)<=12)return i;return -1;}
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
            try{
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                String path=config.get(area().equals("BACK")?"backMockup":"frontMockup").getAsString();
                BufferedImage image=images.get(path);
                if(image==null)try(var input=getClass().getClassLoader().getResourceAsStream("storefront-web/"+path.substring("/shop/".length()))){if(input!=null){image=ImageIO.read(input);if(image!=null)images.put(path,image);}}catch(Exception ignored){}
                if(image!=null)g.drawImage(image,(int)offsetX(),(int)offsetY(),(int)side(),(int)side(),null);
                else{g.setColor(UIManager.getColor("Label.foreground"));g.drawString("Shirt mockup unavailable; use the position controls.",12,24);}
                Color accent=DeckersPalette.ORANGE;
                for(String name:ShirtDesignerService.AREAS){if(area().equals("BACK")?!name.equals("BACK"):name.equals("BACK"))continue;
                    Rectangle2D.Double b=box(config.getAsJsonObject("areas").getAsJsonObject(name));
                    g.setColor(name.equals(area())?accent:Color.GRAY);g.setStroke(new BasicStroke(name.equals(area())?2f:1f));g.draw(b);
                    g.drawString(name.equals("CHEST")?"Left chest":name.equals("FRONT")?"Large front":"Large back",(int)b.x,(int)b.y-6);
                    if(name.equals(area()))for(Point p:corners(b)){g.setColor(Color.WHITE);g.fillRect(p.x-5,p.y-5,10,10);g.setColor(accent);g.drawRect(p.x-5,p.y-5,10,10);}
                }
            }finally{g.dispose();}
        }
    }
}
