package gy.deckers.studio;
import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** Icon-led workspace cards, using the same theme and bundled icons as the ribbon. */
final class StudioWorkspaceChooser extends JPanel implements Scrollable {
 static final String[] NAMES={"Photo Queue","Badge Cards","Business Cards","Labels","Banners","Bill Books","Passport Pictures","Image Studio","PDF Editor","Vinyl Cut"};
 private static final String[][] DETAILS={{"Import and prepare","photos for printing"},{"Create ID and","membership cards"},{"Design and print","business cards"},{"Design and print","labels and stickers"},{"Create large format","banners and signs"},{"Design and print","invoices and bill books"},{"Create passport","and ID photos"},{"Edit images and","remove backgrounds"},{"View, edit and","organise PDF files"},{"Design vector artwork","and cut vinyl"}};
 private final JPanel content=new JPanel(new BorderLayout(0,20)),grid=new JPanel(new GridLayout(3,3,12,12));
 StudioWorkspaceChooser(Consumer<String> open){
  setLayout(null);setBackground(StudioVisuals.BACKGROUND);content.setOpaque(false);grid.setOpaque(false);
  JPanel heading=new JPanel(new BorderLayout(0,6));heading.setOpaque(false);
  JLabel title=new JLabel("What would you like to create?");title.setFont(StudioVisuals.font(Font.BOLD,24));title.setForeground(StudioVisuals.INK);heading.add(title,BorderLayout.NORTH);
  JLabel subtitle=new JLabel("Choose a workspace to get started");subtitle.setFont(StudioVisuals.font(Font.PLAIN,15));subtitle.setForeground(new Color(0x756B7F));heading.add(subtitle);
  content.add(heading,BorderLayout.NORTH);content.add(grid);add(content);
  for(int i=0;i<NAMES.length;i++){String name=NAMES[i];grid.add(new Tile(name,DETAILS[i],()->open.accept(name)));}
 }
 @Override public void doLayout(){int width=Math.max(240,Math.min(1060,getWidth()-48));int columns=width>=720?3:width>=480?2:1;int rows=(NAMES.length+columns-1)/columns;grid.setLayout(new GridLayout(rows,columns,12,12));int wanted=84+rows*112+(rows-1)*12;int height=Math.min(wanted,Math.max(84+rows*90+(rows-1)*12,getHeight()-40));content.setBounds(Math.max(16,(getWidth()-width)/2),Math.max(20,(getHeight()-height)/2),width,height);content.doLayout();grid.doLayout();}
 @Override public Dimension getPreferredSize(){int width=getParent() instanceof JViewport v?v.getWidth():1108;int columns=width>=768?3:width>=528?2:1;int rows=(NAMES.length+columns-1)/columns;return new Dimension(760,116+rows*112+(rows-1)*12);}
 public Dimension getPreferredScrollableViewportSize(){return new Dimension(1108,500);}
 public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 32;}
 public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(32,r.height-32);}
 public boolean getScrollableTracksViewportWidth(){return true;}
 public boolean getScrollableTracksViewportHeight(){return getParent() instanceof JViewport v&&v.getHeight()>=410&&v.getWidth()>=768;}
 static final class Tile extends JButton {
  private final String[] detail;
  Tile(String name,String[] detail,Runnable open){super(name);putClientProperty("studio.visual.role","workspace");this.detail=detail;setIcon(StudioVisuals.workspaceIcon(name,34));setForeground(StudioVisuals.PURPLE);setOpaque(false);setContentAreaFilled(false);setBorderPainted(false);setFocusPainted(false);setRolloverEnabled(true);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));getAccessibleContext().setAccessibleName(name);getAccessibleContext().setAccessibleDescription(String.join(" ",detail));setToolTipText(name+" — "+String.join(" ",detail));addActionListener(e->open.run());}
  @Override protected void paintComponent(Graphics original){
   Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
   boolean active=hasFocus()||getModel().isRollover()||getModel().isPressed();
   g.setColor(new Color(0xEDE7F1));g.fillRoundRect(2,4,getWidth()-4,getHeight()-5,14,14);
   g.setColor(active?new Color(0xF5EFFC):Color.WHITE);g.fillRoundRect(1,1,getWidth()-3,getHeight()-5,12,12);
   g.setColor(active?StudioVisuals.PURPLE:StudioVisuals.BORDER);g.setStroke(new BasicStroke(active?2:1));g.drawRoundRect(1,1,getWidth()-3,getHeight()-5,12,12);
   int cy=(getHeight()-4)/2;g.setColor(new Color(0xF1E8FB));g.fillRoundRect(16,cy-29,58,58,14,14);getIcon().paintIcon(this,g,28,cy-17);
   int x=88;g.setFont(StudioVisuals.font(Font.BOLD,16));g.setColor(StudioVisuals.INK);g.drawString(getText(),x,cy-14);
   g.setFont(StudioVisuals.font(Font.PLAIN,14));g.setColor(new Color(0x756B7F));g.drawString(detail[0],x,cy+9);g.drawString(detail[1],x,cy+28);
   g.dispose();
  }
 }
}
