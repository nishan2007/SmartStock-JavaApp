package ui.screens;

import ui.design.DeckersPalette;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.function.Supplier;

/** Shared-palette native painting for the transfer desk. Icons use bundled Font Awesome Free. */
final class TransferDeskVisuals {
    private TransferDeskVisuals() {}
    static Font font(int style,int size){return SalesTaskVisuals.font(style,size);}
    static JPanel panel(LayoutManager layout,boolean surface){return new JPanel(layout){
        {putClientProperty("SmartStock.preserveBackground",true);}
        @Override public Color getBackground(){return surface?DeckersPalette.surface():DeckersPalette.background();}
    };}
    static JPanel clear(LayoutManager layout){JPanel p=new JPanel(layout);p.setOpaque(false);return p;}
    static JLabel label(String text,int style,int size){return label(text,style,size,DeckersPalette::text);}
    static JLabel label(String text,int style,int size,Supplier<Color> color){return new JLabel(text){
        {setFont(font(style,size));putClientProperty("SmartStock.preserveForeground",true);}
        @Override public Color getForeground(){return color.get();}
    };}
    static JPanel card(LayoutManager layout){JPanel p=SalesTaskVisuals.card(layout);p.setBorder(new EmptyBorder(14,14,14,14));return p;}
    static JButton button(String text,String icon,boolean primary){return new DeskButton(text,icon,primary,false);}
    static JButton danger(String text){return new DeskButton(text,"trash",false,true);}
    static final class DeskButton extends JButton {
        final boolean primary,danger;
        DeskButton(String text,String icon,boolean primary,boolean danger){
            super(text);this.primary=primary;this.danger=danger;
            setFont(font(Font.BOLD,15));setBorder(new EmptyBorder(12,16,12,16));
            setContentAreaFilled(false);setOpaque(false);setFocusPainted(false);setIconTextGap(10);
            if(icon!=null)setIcon(SalesTaskVisuals.icon(icon,danger?new Color(222,47,31):primary?Color.WHITE:DeckersPalette.ORANGE,18));
            putClientProperty("SmartStock.customPaintedButton",true);putClientProperty("SmartStock.preserveThemeColors",true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }
        private boolean filled(){return Boolean.TRUE.equals(getClientProperty("segment"))?Boolean.TRUE.equals(getClientProperty("segmentSelected")):primary;}
        @Override public Color getForeground(){return !isEnabled()?DeckersPalette.muted():filled()?Color.WHITE:danger?new Color(222,47,31):Boolean.TRUE.equals(getClientProperty("neutral"))||Boolean.TRUE.equals(getClientProperty("segment"))?DeckersPalette.text():DeckersPalette.ORANGE;}
        @Override public void setOpaque(boolean b){super.setOpaque(false);}
        @Override public void setContentAreaFilled(boolean b){super.setContentAreaFilled(false);}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);
            Color accent=danger?new Color(235,48,27):Boolean.TRUE.equals(getClientProperty("neutral"))||Boolean.TRUE.equals(getClientProperty("segment"))&&!filled()?DeckersPalette.border():DeckersPalette.ORANGE;
            g.setColor(!isEnabled()?DeckersPalette.background():filled()?accent:DeckersPalette.surface());
            if(isEnabled()&&getModel().isRollover())g.setColor(filled()?accent.darker():DeckersPalette.tileFill(accent));
            if(getModel().isPressed())g.setColor(DeckersPalette.tilePressed(accent));
            g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,5,5);g.setColor(isEnabled()?accent:DeckersPalette.border());
            g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,5,5);
            if(hasFocus())g.drawRoundRect(3,3,getWidth()-7,getHeight()-7,4,4);
            g.dispose();super.paintComponent(graphics);
        }
    }
    static javax.swing.border.Border edge(int top,int left,int bottom,int right){return new javax.swing.border.AbstractBorder(){
        @Override public Insets getBorderInsets(Component c){return new Insets(top,left,bottom,right);}
        @Override public Insets getBorderInsets(Component c,Insets insets){insets.set(top,left,bottom,right);return insets;}
        @Override public void paintBorder(Component c,Graphics g,int x,int y,int w,int h){g.setColor(DeckersPalette.border());g.fillRect(x,y,w,top);g.fillRect(x,y,left,h);g.fillRect(x,y+h-bottom,w,bottom);g.fillRect(x+w-right,y,right,h);}
    };}
    static String html(String s){return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    static final class SearchField extends JTextField {
        final String hint;
        SearchField(String hint){this.hint=hint;setFont(font(Font.PLAIN,15));setBorder(BorderFactory.createCompoundBorder(edge(1,1,1,1),new EmptyBorder(8,36,8,10)));setToolTipText(hint);getAccessibleContext().setAccessibleName(hint);}
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);
            SalesTaskVisuals.icon("search",DeckersPalette.muted(),17).paintIcon(this,g,10,(getHeight()-17)/2);
            if(getText().isEmpty()&&!hasFocus()){g.setFont(getFont());g.setColor(DeckersPalette.muted());g.drawString(hint,36,(getHeight()-g.getFontMetrics().getHeight())/2+g.getFontMetrics().getAscent());}g.dispose();
        }
    }
    static final class Lifecycle extends JComponent {
        boolean draft;
        Lifecycle(){setPreferredSize(new Dimension(550,92));getAccessibleContext();}
        @Override protected void paintComponent(Graphics graphics){
            Graphics2D g=(Graphics2D)graphics.create();SalesTaskVisuals.antialias(g);
            String[] titles=draft?new String[]{"Prepare","Send","Receive"}:new String[]{"Sent","Awaiting receipt","Received"};
            String[] hints=draft?new String[]{"Choose store & items","Deduct source stock","Add stock after verification"}:new String[]{"Transfer sent to store","Receiving store to verify","Stock added after verification"};
            int unit=getWidth()/3;
            if(getWidth()<360){
                for(int i=0;i<3;i++){
                    int center=i*unit+unit/2;g.setColor(i==0?DeckersPalette.ORANGE:DeckersPalette.border());g.fillOval(center-16,5,32,32);g.setColor(Color.WHITE);g.setFont(font(Font.BOLD,17));g.drawString(""+(i+1),center-5,27);
                    g.setFont(font(Font.BOLD,12));g.setColor(DeckersPalette.text());String[] words=titles[i].split(" ");int y=55;for(String word:words){g.drawString(word,center-g.getFontMetrics().stringWidth(word)/2,y);y+=17;}
                }g.dispose();return;
            }
            for(int i=0;i<3;i++){
                int x=i*unit+8;Color accent=i==0?DeckersPalette.ORANGE:DeckersPalette.blend(DeckersPalette.border(),DeckersPalette.muted(),.15);
                g.setColor(accent);g.fillOval(x,5,32,32);g.setFont(font(Font.BOLD,17));g.setColor(Color.WHITE);g.drawString(""+(i+1),x+11,27);
                g.setColor(DeckersPalette.border());g.fillRect(x+36,18,Math.max(0,unit-48),4);
                int textX=x+38;g.setClip(textX,32,Math.max(1,unit-40),60);
                g.setColor(DeckersPalette.text());g.setFont(font(Font.BOLD,14));g.drawString(titles[i],textX,46);
                g.setFont(font(Font.PLAIN,13));g.setColor(DeckersPalette.muted());
                String[] words=hints[i].split(" ");String line="";int y=66;
                for(String word:words){String next=line.isEmpty()?word:line+" "+word;if(g.getFontMetrics().stringWidth(next)>unit-44&&!line.isEmpty()){g.drawString(line,textX,y);y+=16;line=word;}else line=next;}g.drawString(line,textX,y);g.setClip(null);
            }g.dispose();
        }
    }
}
