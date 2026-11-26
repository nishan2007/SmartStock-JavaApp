package ui.screens;

import ui.design.DeckersPalette;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Path2D;

/** Native controls for the guided task workspace, using the application's shared palette. */
final class SalesTaskVisuals {
    private SalesTaskVisuals() {}
    static Font font(int style,int size){return new Font("Segoe UI",style,size);}
    static void antialias(Graphics2D g){g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);}
    static JPanel card(LayoutManager layout){return new JPanel(layout){
        {setOpaque(false);setBorder(new EmptyBorder(14,16,14,16));putClientProperty("SmartStock.preserveBackground",true);}
        @Override public void doLayout(){if(Boolean.TRUE.equals(getClientProperty("taskSummary"))&&getLayout() instanceof GridLayout grid){boolean compact=getWidth()<850;grid.setRows(compact?2:1);grid.setColumns(compact?3:5);}super.doLayout();}
        @Override public Dimension getPreferredSize(){Dimension size=super.getPreferredSize();if(Boolean.TRUE.equals(getClientProperty("taskSummary")))size.height=getWidth()>0&&getWidth()<850?190:102;return size;}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();antialias(g);g.setColor(DeckersPalette.surface());g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,8,8);g.setColor(DeckersPalette.border());g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,8,8);g.dispose();}
    };}
    static JButton button(String text,String icon,boolean primary){return new TaskButton(text,icon,primary);}
    static final class TaskButton extends JButton {
        final String symbol;final boolean primary;
        TaskButton(String text,String symbol,boolean primary){super(text);this.symbol=symbol;this.primary=primary;setFont(font(primary?Font.BOLD:Font.PLAIN,15));setForeground(primary?Color.WHITE:DeckersPalette.text());setContentAreaFilled(false);setOpaque(false);setBorder(new EmptyBorder(10,14,10,14));setFocusPainted(false);putClientProperty("SmartStock.preserveThemeColors",true);if(symbol!=null){setIcon(icon(symbol,primary?Color.WHITE:DeckersPalette.muted(),18));setIconTextGap(9);}setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));}
        @Override public void setContentAreaFilled(boolean value){super.setContentAreaFilled(false);}
        @Override public void setOpaque(boolean value){super.setOpaque(false);}
        @Override public Color getForeground(){return primary?Color.WHITE:Boolean.TRUE.equals(getClientProperty("taskLink"))?new Color(32,91,180):Boolean.TRUE.equals(getClientProperty("taskOutline"))?DeckersPalette.ORANGE:DeckersPalette.text();}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();antialias(g);Color accent=getClientProperty("taskAccent") instanceof Color customAccent?customAccent:DeckersPalette.ORANGE;if(!Boolean.TRUE.equals(getClientProperty("taskLink"))){g.setColor(primary?accent:DeckersPalette.surface());if(getModel().isPressed())g.setColor(primary?accent.darker():DeckersPalette.background());else if(getModel().isRollover())g.setColor(primary?accent.brighter():DeckersPalette.background());g.fillRoundRect(0,0,getWidth()-1,getHeight()-1,6,6);g.setColor(primary||Boolean.TRUE.equals(getClientProperty("taskOutline"))?accent:DeckersPalette.border());g.drawRoundRect(0,0,getWidth()-1,getHeight()-1,6,6);}if(hasFocus()){g.setColor(accent);g.drawRoundRect(2,2,getWidth()-5,getHeight()-5,5,5);}if(Boolean.TRUE.equals(getClientProperty("taskDropdown")))icon("down",DeckersPalette.muted(),10).paintIcon(this,g,getWidth()-20,(getHeight()-10)/2);g.dispose();super.paintComponent(graphics);}
    }
    static final class Badge extends JLabel {
        private Color accent;
        Badge(String text,Color accent){super(text);this.accent=accent;setFont(font(Font.BOLD,14));setBorder(new EmptyBorder(4,12,4,12));putClientProperty("SmartStock.preserveThemeColors",true);setForeground(accent.darker());}
        void tone(Color color){accent=color;setForeground(DeckersPalette.dark()?DeckersPalette.blend(Color.WHITE,color,.55):color.darker());repaint();}
        @Override public Color getForeground(){return accent==null?DeckersPalette.text():DeckersPalette.dark()?DeckersPalette.blend(Color.WHITE,accent,.55):accent.darker();}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();antialias(g);g.setColor(DeckersPalette.blend(DeckersPalette.surface(),accent,.16));g.fillRoundRect(0,0,getWidth(),getHeight(),9,9);g.dispose();super.paintComponent(graphics);}
    }
    static final class Step extends JButton {
        @Override public void setOpaque(boolean value){super.setOpaque(false);}
        @Override public void setContentAreaFilled(boolean value){super.setContentAreaFilled(false);}
        @Override protected void paintBorder(Graphics graphics){}
        final int number;final String title;String hint;boolean active,done;
        Step(int number,String title,String hint){this.number=number;this.title=title;this.hint=hint;setPreferredSize(new Dimension(200,70));setOpaque(false);setContentAreaFilled(false);setBorder(new EmptyBorder(0,0,0,0));setFocusPainted(false);setToolTipText(title+": "+hint);getAccessibleContext().setAccessibleName(title);putClientProperty("SmartStock.preserveThemeColors",true);setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();antialias(g);int w=getWidth(),h=getHeight();Color green=new Color(20,153,68),accent=active?DeckersPalette.ORANGE:done?green:DeckersPalette.muted();Color fill=DeckersPalette.blend(DeckersPalette.surface(),accent,active?.08:done?.10:.05);Path2D p=new Path2D.Double();p.moveTo(.5,.5);p.lineTo(w-20.5,.5);p.lineTo(w-1.0,h/2d);p.lineTo(w-20.5,h-1.0);p.lineTo(.5,h-1.0);if(number>1)p.lineTo(20,h/2d);p.closePath();g.setColor(fill);g.fill(p);if(active){g.setColor(DeckersPalette.ORANGE);g.draw(p);}if(hasFocus()){g.setColor(accent);g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT,BasicStroke.JOIN_ROUND,1,new float[]{2,2},0));g.drawRoundRect(7,5,w-33,h-11,6,6);g.setStroke(new BasicStroke(1));}int cx=number==1?16:26;int cy=(h-34)/2;g.setColor(active?DeckersPalette.ORANGE:done?green:DeckersPalette.blend(DeckersPalette.border(),DeckersPalette.muted(),.18));g.fillOval(cx,cy,34,34);g.setFont(font(Font.BOLD,19));g.setColor(Color.WHITE);String n=String.valueOf(number);if(done)icon("check",Color.WHITE,18).paintIcon(this,g,cx+8,cy+8);else g.drawString(n,cx+(34-g.getFontMetrics().stringWidth(n))/2,cy+24);int tx=cx+44;g.clipRect(tx,0,w-tx-15,h);g.setColor(DeckersPalette.text());g.setFont(font(Font.BOLD,w<240?13:15));if(w<215){String[] parts=title.contains(" & ")?title.split(" & "):title.split(" ",2);g.drawString(parts[0],tx,h/2-3);g.drawString(parts.length>1?(title.contains(" & ")?"& ":"")+parts[1]:"",tx,h/2+15);}else{g.drawString(title,tx,h/2-3);g.setColor(DeckersPalette.muted());g.setFont(font(Font.PLAIN,w<240?11:13));g.drawString(hint,tx,h/2+18);}g.dispose();}
    }
    private static final Font ICON_FONT=loadIconFont();
    private static Font loadIconFont(){try(var stream=SalesTaskVisuals.class.getResourceAsStream("/Images/sales-task-icons.ttf")){if(stream==null)throw new IllegalStateException("Task icon font is missing");return Font.createFont(Font.TRUETYPE_FONT,stream);}catch(Exception e){throw new IllegalStateException("Cannot load task icons",e);}}
    static Icon icon(String type,Color color,int size){int code=switch(type){case "circle"->0xf111;case "image"->0xf03e;case "edit"->0xf044;case "external-link"->0xf35d;case "phone"->0xf095;case "trash"->0xf2ed;case "close"->0xf00d;case "box"->0xf1b2;case "store"->0xf54e;case "arrow-right"->0xf061;case "receive"->0xf019;case "send"->0xf093;case "refresh"->0xf021;case "orders"->0xf573;case "gear"->0xf013;case "check-circle"->0xf058;case "coins"->0xf51e;case "plus"->0xf055;case "search"->0xf002;case "back"->0xf053;case "check"->0xf00c;case "save"->0xf0c7;case "print"->0xf02f;case "more"->0xf141;case "calendar"->0xf073;case "person"->0xf007;case "pin"->0xf3c5;case "payment"->0xf09d;case "clip"->0xf0c6;case "pdf"->0xf1c1;case "down"->0xf078;default->0xf05a;};return new Icon(){public int getIconWidth(){return size;}public int getIconHeight(){return size;}public void paintIcon(Component c,Graphics graphics,int x,int y){Graphics2D g=(Graphics2D)graphics.create();antialias(g);g.setFont(ICON_FONT.deriveFont((float)size));g.setColor(color);String glyph=String.valueOf((char)code);FontMetrics fm=g.getFontMetrics();g.drawString(glyph,x+(size-fm.stringWidth(glyph))/2,y+(size-fm.getHeight())/2+fm.getAscent());g.dispose();}};}
}
