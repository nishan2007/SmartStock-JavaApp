package gy.deckers.studio;

import javax.swing.*;
import java.awt.*;
import java.io.InputStream;
import java.util.Locale;
import ui.design.DeckersPalette;

/** Studio-scoped tokens and Font Awesome Free 6.7.2 icons (bundled OFL font). */
final class StudioVisuals {
    static final Color BACKGROUND=new Color(0xFBF8F3),SURFACE=new Color(0xFDFCFA),INK=new Color(0x302936);
    static final Color PURPLE=DeckersPalette.PURPLE,PLUM=new Color(0x4D235B),BORDER=new Color(0xDED7E4);
    private static final Font ICONS=load("fa-solid-900.ttf"),OUTLINE=load("fa-regular-400.ttf");
    private static Font load(String file){
        try(InputStream in=StudioVisuals.class.getResourceAsStream("icons/ribbon/"+file)){
            if(in==null)throw new IllegalStateException("Missing ribbon icon font");
            return Font.createFont(Font.TRUETYPE_FONT,in);
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    static Font font(int style,int size){return new Font("Segoe UI",style,size);}
    static Icon icon(String label,int size){return new Glyph(glyph(label),size);}
    static Icon commandIcon(AbstractButton button,int size){
        return button.getClientProperty(StudioCommand.class) instanceof StudioCommand command
                ? new Glyph(command.glyph,size) : icon(button.getText(),size);
    }
    static Icon workspaceIcon(String name,int size){return new Glyph(switch(name){case "Photo Queue"->0xf302;case "Badge Cards"->0xf2c2;case "Business Cards"->0xf2bb;case "Labels"->0xf02c;case "Vinyl Cut"->0xf0c4;case "Banners"->0xf024;case "Bill Books"->0xf543;case "Passport Pictures"->0xf5ab;case "Image Studio"->0xf0d0;case "PDF Editor"->0xf1c1;default->0xf009;},size);}
    static Icon layerIcon(String label,int size){
        int code=switch(label){case "TEXT"->0xf031;case "template"->0xf07b;case "IMAGE"->0xf03e;case "visible"->0xf06e;case "hidden"->0xf070;case "delete"->0xf2ed;default->0xf0c8;};
        Font face=label.equals("TEXT")?new Font("Georgia",Font.PLAIN,size):OUTLINE;
        return new Icon(){
            public int getIconWidth(){return size;}public int getIconHeight(){return size;}
            public void paintIcon(Component c,Graphics original,int x,int y){
                Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(face.deriveFont((float)size));g.setColor(c.getForeground());
                String text=label.equals("TEXT")?"T":new String(Character.toChars(code));FontMetrics m=g.getFontMetrics();
                g.drawString(text,x+(size-m.stringWidth(text))/2,y+(size-m.getHeight())/2+m.getAscent());g.dispose();
            }
        };
    }
    private static int glyph(String label){
        String s=label.toLowerCase(Locale.ROOT);
        if(s.equals("sign in"))return 0xf061;if(s.contains("nfc staff badge"))return 0xf2c2;
        if(s.equals("db"))return 0xf1c0;
        if(s.equals("edit records"))return 0xf303;
        if(s.equals("search"))return 0xf002;
        if(s.equals("sort up"))return 0xf0d8;
        if(s.equals("sort down"))return 0xf0d7;
        if(s.equals("collapse ribbon"))return 0xf106;if(s.equals("expand ribbon"))return 0xf107;
        if(s.equals("visible layer"))return 0xf06e;if(s.equals("hidden layer"))return 0xf070;
        if(s.equals("delete layer"))return 0xf2ed;if(s.equals("close layers"))return 0xf00d;
        if(s.equals("move layer up"))return 0xf062;if(s.equals("move layer down"))return 0xf063;
        if(s.equals("shape layer")||s.equals("rectangle"))return 0xf0c8;
        if(s.startsWith("shapes"))return 0xf61f;
        if(s.startsWith("history"))return 0xf1da;
        if(s.startsWith("clipboard"))return 0xf328;
        if(s.contains("cutout"))return 0xf0c4;
        if(s.startsWith("fit"))return 0xf065;
        if(s.startsWith("display"))return 0xf108;
        if(s.startsWith("add files"))return 0xf0c5;
        if(s.contains("highlight"))return 0xf591;
        if(s.equals("front")||s.equals("back"))return 0xf2c2;
        if(s.contains("table"))return 0xf0ce;
        if(s.contains("ocr"))return 0xf002;
        if(s.contains("phone"))return 0xf3cd;
        if(s.contains("refresh"))return 0xf021;
        if(s.contains("crop")||s.contains("frame"))return 0xf125;
        if(s.contains("signature"))return 0xf5b7;
        if(s.contains("recognition"))return 0xf002;
        if(s.contains("process"))return 0xf0d0;
        if(s.contains("filter"))return 0xf0b0;
        if(s.equals("rename")||s.startsWith("rename"))return 0xf303;if(s.startsWith("recent"))return 0xf017;if(s.equals("new"))return 0xf15b;if(s.startsWith("file info"))return 0xf15c;if(s.startsWith("import"))return 0xf56f;if(s.startsWith("save as"))return 0xf0c5;
        if(s.equals("delete page"))return 0xf2ed;
        if(s.startsWith("left 90"))return 0xf0e2;if(s.startsWith("right 90")||s.startsWith("rotate 180"))return 0xf01e;
        if(s.equals("bold"))return 0xf032;if(s.equals("italic"))return 0xf033;if(s.equals("underline"))return 0xf0cd;if(s.equals("strikethrough"))return 0xf0cc;
        if(s.equals("apply size"))return 0xf00c;if(s.equals("preview"))return 0xf06e;
        if(s.contains("undo"))return 0xf0e2;if(s.contains("redo"))return 0xf01e;
        if(s.contains("wordart"))return 0xf031;if(s.contains("qr"))return 0xf029;
        if(s.contains("barcode")||s.contains("code format"))return 0xf02a;
        if(s.contains("image")||s.contains("picture")||s.contains("photo"))return 0xf03e;
        if(s.contains("text")||s.contains("font"))return 0xf031;
        if(s.contains("print"))return 0xf02f;if(s.contains("save"))return 0xf0c7;
        if(s.contains("forward")||s.contains("front"))return 0xf5fd;
        if(s.contains("backward")||s.contains("to back"))return 0xf063;
        if(s.contains("align"))return 0xf036;if(s.contains("distribute"))return 0xf07d;
        if(s.contains("merge")||s.contains("field"))return 0xf15c;
        if(s.contains("number"))return 0xf292;if(s.contains("page"))return 0xf15b;
        if(s.contains("template")||s.contains("open"))return 0xf07c;
        if(s.contains("layer")||s.contains("arrange"))return 0xf5fd;
        if(s.contains("workspace"))return 0xf009;if(s.contains("copy"))return 0xf0c5;
        if(s.contains("paste"))return 0xf0ea;if(s.contains("delete")||s.contains("remove"))return 0xf2ed;
        if(s.contains("colour"))return 0xf53f;if(s.contains("zoom")||s.equals("+"))return 0xf00e;
        if(s.contains("fit"))return 0xf065;if(s.contains("export"))return 0xf56e;
        if(s.contains("close")||s.contains("cancel"))return 0xf00d;
        return 0xf013;
    }
    private record Glyph(int code,int size) implements Icon {
        public int getIconWidth(){return size;}public int getIconHeight(){return size;}
        public void paintIcon(Component c,Graphics original,int x,int y){
            Graphics2D g=(Graphics2D)original.create();
            try{
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                if(size==44){g.setColor(new Color(0xF6F1F8));g.fillRoundRect(x,y,44,44,7,7);g.setColor(BORDER);g.drawRoundRect(x,y,43,43,7,7);}
                g.setFont(ICONS.deriveFont(size==44?26f:(float)size));g.setColor(c.isEnabled()?(size<24?c.getForeground():PURPLE):new Color(0xACA6B1));
                String text=new String(Character.toChars(code));FontMetrics metrics=g.getFontMetrics();
                g.drawString(text,x+(size-metrics.stringWidth(text))/2,y+(size-metrics.getHeight())/2+metrics.getAscent());
            }finally{g.dispose();}
        }
    }
    static void headerButton(AbstractButton button){
        headerButton(button,button.getText().startsWith("Print")||button.getText().equals("Workspaces"));
    }
    static void primaryButton(AbstractButton button){headerButton(button,true);}
    static void recordsButton(AbstractButton button){
        headerButton(button,false);
        button.setFont(font(Font.BOLD,18));
        button.setIcon(icon(button.getText(),25));
        button.setDisabledIcon(button.getIcon());
        button.setPreferredSize(null);
        button.setPreferredSize(new Dimension(Math.max(110,button.getPreferredSize().width),50));
        if(button.getText().equals("DB")){
            button.setToolTipText("Browse saved records");
            button.getAccessibleContext().setAccessibleDescription("Open or close saved records");
        }
    }
    private static void headerButton(AbstractButton button,boolean primary){
        button.putClientProperty("studio.visual.role","header");
        Color accent=new Color(0x8050CB);
        button.setIcon(icon(button.getText(),20));button.setDisabledIcon(icon(button.getText(),20));
        button.setFont(font(Font.BOLD,15));button.setForeground(primary?Color.WHITE:accent);button.setBackground(SURFACE);
        button.setHorizontalTextPosition(SwingConstants.RIGHT);button.setVerticalTextPosition(SwingConstants.CENTER);
        button.setIconTextGap(9);button.setContentAreaFilled(false);button.setOpaque(false);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI(){
            @Override public void paint(Graphics original,JComponent component){
                Graphics2D g=(Graphics2D)original.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                boolean pressed=button.getModel().isPressed(),hover=button.getModel().isRollover();
                Color top=primary?new Color(0x8958D3):button.isSelected()?new Color(0xE8DCF8):new Color(0xFBF9FE);
                Color bottom=primary?new Color(0x7546BF):button.isSelected()?new Color(0xE8DCF8):new Color(0xF0EAF8);
                if(pressed){top=primary?new Color(0x6940AD):new Color(0xE7DCF5);bottom=top;}
                else if(hover){top=primary?new Color(0x9667DD):Color.WHITE;}
                g.setPaint(new GradientPaint(0,0,top,0,component.getHeight(),bottom));
                g.fillRoundRect(0,0,component.getWidth()-1,component.getHeight()-1,10,10);g.dispose();
                super.paint(original,component);
            }
        });
        button.setBorder(new javax.swing.border.AbstractBorder(){
            @Override public Insets getBorderInsets(Component c){return new Insets(8,18,8,18);}
            @Override public Insets getBorderInsets(Component c,Insets insets){insets.set(8,18,8,18);return insets;}
            @Override public void paintBorder(Component c,Graphics original,int x,int y,int width,int height){
                Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(button.hasFocus()?PLUM:new Color(0xAF8BE4));
                g.setStroke(new BasicStroke(button.hasFocus()?2f:1f));g.drawRoundRect(x+1,y+1,width-3,height-3,10,10);g.dispose();
            }
        });
        button.setPreferredSize(null);Dimension size=button.getPreferredSize();button.setPreferredSize(new Dimension(Math.max(108,size.width),44));
        button.setFocusPainted(true);button.setRolloverEnabled(true);button.setToolTipText(button.getText());
    }
    static void utilityButton(AbstractButton button){
        String title=button.getText();
        headerButton(button);button.putClientProperty("studio.visual.role","utility");
        button.setFont(font(Font.PLAIN,15));
        if(!"Workspaces".equals(title)){button.setIcon(null);button.setForeground(title!=null&&title.startsWith("Print")?Color.WHITE:INK);}
        button.setPreferredSize(null);Dimension size=button.getPreferredSize();button.setPreferredSize(new Dimension(size.width,44));
    }
    private static Icon toolIcon(AbstractButton button){
        String title=button.getText();
        String key=title.toLowerCase(Locale.ROOT);
        Icon glyph=key.equals("text box")?layerIcon("TEXT",25):key.equals("image")?layerIcon("IMAGE",25):key.contains("template")?layerIcon("template",25):commandIcon(button,25);
        boolean tile=title.toLowerCase(Locale.ROOT).matches(".*(text|image|picture|template|setting|code|page|layer).* ".trim());
        return new Icon(){
            public int getIconWidth(){return 64;}public int getIconHeight(){return 44;}
            public void paintIcon(Component c,Graphics original,int x,int y){
                Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                if(tile){g.setColor(new Color(0xF7F4FA));g.fillRoundRect(x,y,63,43,9,9);g.setColor(BORDER);g.drawRoundRect(x,y,63,43,9,9);}
                glyph.paintIcon(c,g,x+19,y+9);g.dispose();
            }
        };
    }
    static javax.swing.border.Border fieldBorder(){
        return new javax.swing.border.AbstractBorder(){
            public Insets getBorderInsets(Component c){return new Insets(6,9,6,9);}
            public Insets getBorderInsets(Component c,Insets insets){insets.set(6,9,6,9);return insets;}
            public void paintBorder(Component c,Graphics original,int x,int y,int w,int h){
                Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                boolean focused=c.hasFocus()||(c instanceof Container parent&&KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()!=null&&SwingUtilities.isDescendingFrom(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner(),parent));
                g.setColor(focused?PURPLE:new Color(0xCBC5D0));g.setStroke(new BasicStroke(focused?2:1));g.drawRoundRect(x+1,y+1,w-3,h-3,9,9);g.dispose();
            }
        };
    }
    static void styleTree(Component component){
        if(component instanceof JComponent jc&&Boolean.TRUE.equals(jc.getClientProperty("studio.visual.field")))return;
        if(component instanceof JComboBox<?> combo){
            combo.putClientProperty("studio.visual.field",true);combo.setFont(font(Font.PLAIN,15));combo.setForeground(INK);combo.setBackground(Color.WHITE);combo.setOpaque(false);
            combo.setUI(new javax.swing.plaf.basic.BasicComboBoxUI(){
                protected JButton createArrowButton(){JButton b=new JButton(icon("expand ribbon",12));b.setForeground(INK);b.setContentAreaFilled(false);b.setBorder(BorderFactory.createEmptyBorder());return b;}
                public void paint(Graphics original,JComponent c){Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(c.isEnabled()?Color.WHITE:new Color(0xF2F0F4));g.fillRoundRect(1,1,c.getWidth()-2,c.getHeight()-2,9,9);g.dispose();super.paint(original,c);}
                public void paintCurrentValueBackground(Graphics g,Rectangle bounds,boolean focus){}
            });combo.setBorder(fieldBorder());
            combo.setRenderer(new DefaultListCellRenderer(){
                public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){
                    super.getListCellRendererComponent(list,value,index,selected,focus);setFont(font(Font.PLAIN,15));setForeground(combo.isEnabled()?INK:new Color(0xA5A0AB));setBackground(selected?new Color(0xEEE6F8):Color.WHITE);return this;
                }
            });
            if(combo.isEditable()&&combo.getEditor().getEditorComponent() instanceof JComponent editor){editor.setBorder(BorderFactory.createEmptyBorder());editor.setFont(font(Font.PLAIN,15));editor.setOpaque(false);}
            return;
        }
        if(component instanceof JSpinner spinner){
            spinner.putClientProperty("studio.visual.field",true);spinner.setFont(font(Font.PLAIN,15));spinner.setBackground(Color.WHITE);spinner.setOpaque(false);spinner.setBorder(fieldBorder());
            spinner.setUI(new javax.swing.plaf.basic.BasicSpinnerUI(){
                public void paint(Graphics original,JComponent c){Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(Color.WHITE);g.fillRoundRect(1,1,c.getWidth()-2,c.getHeight()-2,9,9);g.dispose();super.paint(original,c);}
                protected Component createNextButton(){JButton b=arrow(SwingConstants.NORTH);installNextButtonListeners(b);return b;}
                protected Component createPreviousButton(){JButton b=arrow(SwingConstants.SOUTH);installPreviousButtonListeners(b);return b;}
                private JButton arrow(int direction){var b=new javax.swing.plaf.basic.BasicArrowButton(direction,Color.WHITE,Color.WHITE,INK,Color.WHITE);b.setBorder(BorderFactory.createEmptyBorder());return b;}
            });
            if(spinner.getEditor() instanceof JSpinner.DefaultEditor editor){editor.setOpaque(false);editor.getTextField().setBorder(BorderFactory.createEmptyBorder());editor.getTextField().setFont(font(Font.PLAIN,15));editor.getTextField().setForeground(INK);editor.getTextField().setBackground(Color.WHITE);editor.getTextField().setOpaque(false);}
            return;
        }
        if(component instanceof javax.swing.text.JTextComponent field){field.setFont(font(Font.PLAIN,15));field.setForeground(INK);field.setBackground(Color.WHITE);field.setBorder(fieldBorder());}
        else if(component instanceof AbstractButton button){if(button instanceof JCheckBox||button instanceof JRadioButton){button.setFont(font(Font.PLAIN,14));button.setForeground(INK);button.setOpaque(false);}else if(button.getClientProperty("studio.visual.role")==null)utilityButton(button);}
        else if(component instanceof JLabel label){if(!(label.getParent() instanceof JTable)){label.setFont(font(label.getFont().getStyle(),Math.max(12,Math.min(32,label.getFont().getSize()))));}}
        if(component instanceof JPanel panel&&panel.getBackground().equals(DeckersPalette.background()))panel.setBackground(BACKGROUND);
        if(component instanceof JScrollPane scroll&&scroll.getViewport().getView() instanceof TemplateWorkspace)scroll.setBorder(BorderFactory.createEmptyBorder());
        if(component instanceof JTable table){table.setFont(font(Font.PLAIN,14));if(table.getTableHeader()!=null)table.getTableHeader().setFont(font(Font.BOLD,13));}
        if(component instanceof Container parent)for(Component child:parent.getComponents())styleTree(child);
    }
    static void ribbonButton(AbstractButton button){
        button.putClientProperty("studio.visual.role","ribbon");
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI(){
            @Override public void paint(Graphics original,JComponent c){
                if(button.getModel().isRollover()||button.hasFocus()){
                    Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(new Color(0xEFE7F8));g.fillRoundRect(1,1,c.getWidth()-2,c.getHeight()-2,10,10);
                    if(button.hasFocus()){g.setColor(PURPLE);g.drawRoundRect(1,1,c.getWidth()-3,c.getHeight()-3,10,10);}g.dispose();
                }
                super.paint(original,c);
            }
        });
        String title=button.getText();
        button.setIcon(toolIcon(button));button.setDisabledIcon(toolIcon(button));
        button.setHorizontalTextPosition(SwingConstants.CENTER);button.setVerticalTextPosition(SwingConstants.BOTTOM);
        button.setHorizontalAlignment(SwingConstants.CENTER);button.setVerticalAlignment(SwingConstants.CENTER);
        button.setIconTextGap(9);button.setFont(font(Font.PLAIN,12));button.setForeground(INK);
        button.setBackground(SURFACE);button.setContentAreaFilled(false);button.setOpaque(false);
        button.setBorder(BorderFactory.createEmptyBorder(6,7,6,7));
        int width=Math.max(78,button.getFontMetrics(button.getFont()).stringWidth(title)+24);
        button.setPreferredSize(new Dimension(width,78));
        button.setToolTipText(button.getToolTipText()==null?title:button.getToolTipText());
        button.setFocusPainted(true);button.setRolloverEnabled(true);
    }
}
