package gy.deckers.studio;
import javax.swing.*;
import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/** Flat control ownership with semantic visual groups; listeners and selection remain intact. */
final class StudioRibbonBands implements LayoutManager {
 private final String tab;private final List<Band> painted=new ArrayList<>();
 private record Item(Component control,JLabel label,String caption,String group){}
 private record Band(String title,Rectangle bounds){}
 private StudioRibbonBands(String tab){this.tab=tab;}
 static void install(JComponent panel,String tab){
  var layout=new StudioRibbonBands(tab);panel.setLayout(layout);
  for(Component c:panel.getComponents())if(c instanceof JLabel l&&(l.getText().equals("Picture size")||l.getText().equals("Text")))panel.remove(l);
  for(Component c:panel.getComponents())if(c instanceof JButton b){String text=b.getText();if(b.getIcon()==null&&!text.equals("A−")&&!text.equals("A+")&&!text.contains("colour"))b.setIcon(StudioVisuals.icon(text,18));b.setIconTextGap(6);b.setForeground(StudioVisuals.PURPLE);b.setPreferredSize(null);Dimension d=b.getPreferredSize();b.setPreferredSize(new Dimension(d.width+4,40));}
  if(panel instanceof TextFormattingToolbar text){JToggleButton[] buttons={text.bold,text.italic,text.underline,text.strike};String[] names={"Bold","Italic","Underline","Strikethrough"};for(int i=0;i<buttons.length;i++){var b=buttons[i];b.setText("");b.setIcon(StudioVisuals.icon(names[i],18));b.setPreferredSize(new Dimension(40,40));b.setToolTipText(names[i]);b.getAccessibleContext().setAccessibleName(names[i]);}}
  panel.setBorder(new AbstractBorder(){public void paintBorder(Component c,Graphics original,int x,int y,int width,int height){Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);for(Band band:layout.painted){Rectangle r=band.bounds();g.setColor(new Color(0xF9F6FC));g.fillRoundRect(r.x,r.y,r.width,r.height,10,10);g.setColor(new Color(0xE7DFEE));g.drawRoundRect(r.x,r.y,r.width,r.height,10,10);g.setColor(new Color(0x796486));g.setFont(StudioVisuals.font(Font.BOLD,11));g.drawString(band.title().toUpperCase(java.util.Locale.ROOT),r.x+10,r.y+16);}for(Item item:layout.items(panel)){Component control=item.control();if(item.label()==null&&!(control instanceof AbstractButton)&&!(control instanceof JPanel)&&!(control instanceof JLabel)&&!item.caption().isBlank()){g.setFont(StudioVisuals.font(Font.PLAIN,12));g.setColor(StudioVisuals.INK);g.drawString(item.caption(),control.getX(),control.getY()-5);}}g.dispose();}});
 }
 private String caption(Component c){
  if(c instanceof JPanel p)for(Component child:p.getComponents())if(child instanceof JLabel l)return l.getText();
  if(c instanceof AbstractButton b)return b.getText();
  if(c.getAccessibleContext()!=null&&c.getAccessibleContext().getAccessibleName()!=null)return c.getAccessibleContext().getAccessibleName();return "";
 }
 private String group(String label,Component c,Container parent){
  String s=label.toLowerCase(Locale.ROOT);
  if(c instanceof JComponent tool&&Boolean.TRUE.equals(tool.getClientProperty("studio.layerOrder")))return "Layer order";
  if(parent instanceof ImageFormattingToolbar t){if(c==t.angle||s.contains("90")||s.contains("180")||s.contains("rotation"))return "Rotation";if(c==t.transparency||s.contains("recolour")||s.contains("transparency"))return "Appearance";return "Picture size";}
  if(parent instanceof TextFormattingToolbar t){if(c==t.fonts||c==t.styles)return "Font";if(c==t.size||s.startsWith("a"))return "Font size";if(c==t.bold||c==t.italic||c==t.underline||c==t.strike)return "Emphasis";if(c==t.outline||c==t.width||s.contains("outline"))return "Text outline";return "Colour";}
  if(tab.equals("Layout")){if(s.startsWith("paper"))return "Paper";if(s.contains("page layout"))return "Page layout";if(s.equals("layers")||s.equals("align…"))return "Selection";if(s.contains("template")||s.contains("page"))return "Pages & templates";return "Template size";}
  if(s.contains("left")||s.contains("right")||s.contains("top")||s.contains("bottom"))return "Margins";
  if(s.contains("gap")||s.contains("rotation"))return "Spacing";
  if(s.contains("copies")||s.equals("sheet"))return "Output";
  if(s.contains("preview")||s.contains("save")||s.contains("print")||s.contains("pdf"))return "Review & output";
  return "Finishing";
 }
 private List<Item> items(Container parent){List<Item> items=new ArrayList<>();JLabel pending=null;
  for(Component c:parent.getComponents())if(c.isVisible()){
   if(c instanceof JLabel l){int at=java.util.Arrays.asList(parent.getComponents()).indexOf(c);if(at+1>=parent.getComponentCount()||parent.getComponent(at+1) instanceof AbstractButton){items.add(new Item(c,null,l.getText(),group(l.getText(),c,parent)));}else pending=l;continue;}
   String label=pending!=null?pending.getText():caption(c);
   if(parent instanceof TextFormattingToolbar t){if(c==t.fonts)label="Font";if(c==t.styles)label="Style";if(c==t.size)label="Size (pt)";}
   if(parent instanceof ImageFormattingToolbar t&&c==t.units)label="Units";
   items.add(new Item(c,pending,label,group(label,c,parent)));pending=null;
  }return items;
 }
 private int width(Item item){return Math.max(item.control().getPreferredSize().width,item.label()!=null?item.label().getPreferredSize().width:0);}
 private Dimension arrange(Container parent,boolean apply){
  int available=Math.max(200,parent.getWidth()-12);if(parent.getWidth()==0)available=1280;
  LinkedHashMap<String,List<Item>> groups=new LinkedHashMap<>();for(Item item:items(parent))groups.computeIfAbsent(item.group(),k->new ArrayList<>()).add(item);
  int x=6,y=4,max=0;if(apply)painted.clear();
  for(var entry:groups.entrySet()){
   int wanted=20;for(Item item:entry.getValue())wanted+=width(item)+8;wanted-=8;
   if(x>6&&x+wanted>available){x=6;y+=92;}
   int bandX=x,bandY=y,cx=x+10;
   for(Item item:entry.getValue()){
    int w=Math.min(width(item),available-32);if(cx>x+10&&cx+w>x+Math.min(wanted,available-6)-10){cx=x+10;y+=92;}
    if(apply){Component c=item.control();boolean wrapped=c instanceof JPanel;int controlY=y+38,controlH=40;
     if(wrapped){controlY=y+24;controlH=Math.max(56,c.getPreferredSize().height);}
     else if(item.label()!=null){item.label().setBounds(cx,y+22,w,16);}
     else if(!(c instanceof AbstractButton)&&!item.caption().isBlank()){ /* explicit captions painted below */ }
     if(!wrapped&&item.label()==null&&!(c instanceof AbstractButton))controlY=y+38;
     c.setBounds(cx,controlY,w,controlH);
    }
    cx+=w+8;
   }
   int bw=Math.min(wanted,available-6);if(apply)painted.add(new Band(entry.getKey(),new Rectangle(bandX,bandY,bw,y-bandY+84)));
   x=bandX+bw+10;max=Math.max(max,x);
  }
  if(apply)parent.repaint();return new Dimension(Math.max(200,max),y+92);
 }
 public int height(Container parent){return arrange(parent,false).height;}
 public void addLayoutComponent(String name,Component c){}public void removeLayoutComponent(Component c){}
 public Dimension preferredLayoutSize(Container parent){return arrange(parent,false);}public Dimension minimumLayoutSize(Container parent){return new Dimension(240,92);}
 public void layoutContainer(Container parent){arrange(parent,true);}
}
