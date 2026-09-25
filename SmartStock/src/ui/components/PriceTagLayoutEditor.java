package ui.components;

import managers.CompanyCustomizationManager;
import services.PriceTagPrintService;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.math.BigDecimal;
import java.util.LinkedHashMap;

/** Edits normalized boxes on a canvas with the label's actual aspect ratio. */
public class PriceTagLayoutEditor extends JPanel {
    private final CompanyCustomizationManager.PriceTagTemplateSettings original;
    private final LinkedHashMap<String, Rectangle> boxes;
    private final LinkedHashMap<String, Integer> rotations = new LinkedHashMap<>();
    private final JComboBox<String> elements = new JComboBox<>();
    private final JComboBox<Integer> rotation = new JComboBox<>(new Integer[]{0,90,180,270});
    private final Canvas canvas = new Canvas();
    private boolean loading;

    public PriceTagLayoutEditor(CompanyCustomizationManager.PriceTagTemplateSettings settings) {
        super(new BorderLayout(8,8)); original=settings;
        boxes=PriceTagPrintService.layoutRects(settings.layoutData());
        boxes.keySet().forEach(id -> {
            rotations.put(id, PriceTagPrintService.elementRotation(settings.layoutData(), id));
            if(PriceTagPrintService.elementVisible(settings,id)) elements.addItem(id);
        });
        JPanel controls=new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("Element:")); controls.add(elements);
        controls.add(new JLabel("Rotation (degrees):")); controls.add(rotation);
        controls.add(new JLabel(settings.widthInches()<settings.heightInches()?"Portrait":"Landscape"));
        add(controls,BorderLayout.NORTH); add(canvas,BorderLayout.CENTER);
        elements.addActionListener(e -> select());
        rotation.addActionListener(e -> {if(!loading && elements.getSelectedItem()!=null) {
            rotations.put((String)elements.getSelectedItem(),(Integer)rotation.getSelectedItem());canvas.repaint();
        }});
        select();
    }
    private void select(){loading=true;rotation.setSelectedItem(rotations.getOrDefault((String)elements.getSelectedItem(),0));loading=false;canvas.repaint();}
    public void reset(){boxes.clear();boxes.putAll(PriceTagPrintService.defaultLayout());rotations.replaceAll((k,v)->0);select();}
    public CompanyCustomizationManager.PriceTagTemplateSettings settings(){return new CompanyCustomizationManager.PriceTagTemplateSettings(original.name(),original.showCompany(),
            original.showName(),original.showPrice(),original.showSku(),original.showBarcode(),original.showSize(),
            original.showDescription(),original.widthInches(),original.heightInches(),PriceTagPrintService.encodeLayout(boxes,rotations),
            original.labelsAcross(),original.columnGapInches(),original.rowGapInches());}
    private class Canvas extends JPanel {
        private Point press; private boolean resize;
        Canvas(){setPreferredSize(new Dimension(650,610));setBackground(new Color(242,244,248));
            addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){
                Point p=logical(e.getPoint()); press=null;
                for(var entry:boxes.entrySet()) if(PriceTagPrintService.elementVisible(original,entry.getKey()) && entry.getValue().contains(p)) {
                    elements.setSelectedItem(entry.getKey());Rectangle r=entry.getValue();press=p;
                    resize=p.x>r.x+r.width-40 && p.y>r.y+r.height-20;break;
                }
            }});
            addMouseMotionListener(new MouseMotionAdapter(){public void mouseDragged(MouseEvent e){
                if(press==null)return;Rectangle r=boxes.get(elements.getSelectedItem());if(r==null)return;
                Point p=logical(e.getPoint());int dx=p.x-press.x,dy=p.y-press.y;
                if(resize){r.width=Math.max(20,Math.min(1000-r.x,r.width+dx));r.height=Math.max(10,Math.min(500-r.y,r.height+dy));}
                else {r.x=Math.max(0,Math.min(1000-r.width,r.x+dx));r.y=Math.max(0,Math.min(500-r.height,r.y+dy));}
                press=p;repaint();
            }});
        }
        private Rectangle paper(){double scale=Math.min((getWidth()-60)/original.widthInches(),(getHeight()-55)/original.heightInches());
            int w=(int)(scale*original.widthInches()),h=(int)(scale*original.heightInches());return new Rectangle((getWidth()-w)/2,15,w,h);}
        private Point logical(Point p){Rectangle r=paper();return new Point((p.x-r.x)*1000/Math.max(1,r.width),(p.y-r.y)*500/Math.max(1,r.height));}
        protected void paintComponent(Graphics graphics){super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
            try {Rectangle p=paper();g.setColor(Color.WHITE);g.fill(p);
                try {var image=PriceTagPrintService.render(new PriceTagPrintService.PriceTagItem("Sample Item","","","061-0001","061-0001",BigDecimal.valueOf(100)),settings());
                    g.drawImage(image,p.x,p.y,p.width,p.height,null);
                } catch(IllegalArgumentException ex){g.setColor(Color.RED);g.drawString("Barcode does not fit: rotate or enlarge its box.",10,getHeight()-25);}
                for(var entry:boxes.entrySet())if(PriceTagPrintService.elementVisible(original,entry.getKey())) {
                    Rectangle r=entry.getValue();int x=p.x+r.x*p.width/1000,y=p.y+r.y*p.height/500,w=r.width*p.width/1000,h=r.height*p.height/500;
                    boolean selected=entry.getKey().equals(elements.getSelectedItem());g.setColor(selected?new Color(220,100,0):new Color(0,85,145,100));
                    g.drawRect(x,y,w,h);if(selected)g.fillRect(x+w-8,y+h-8,8,8);
                }
                g.setColor(Color.DARK_GRAY);g.draw(p);g.drawString("Drag to move; drag the bottom-right handle to resize.",15,getHeight()-5);
            } finally {g.dispose();}
        }
    }
}
