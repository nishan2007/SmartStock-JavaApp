package gy.deckers.studio;

import javax.swing.*;
import java.awt.*;

/** Presentation only. Authentication, NFC capture and offline policy remain in SmartStudio. */
final class StudioLoginPanel extends JPanel {
    static JPanel card(){
        return new JPanel(){
            {setOpaque(false);}
            @Override protected void paintComponent(Graphics original){
                Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                for(int i=8;i>0;i--){g.setColor(new Color(40,30,50,3));g.fillRoundRect(10-i,10-i,getWidth()-20+i*2,getHeight()-20+i*2,22,22);}
                g.setColor(Color.WHITE);g.fillRoundRect(8,8,getWidth()-17,getHeight()-17,20,20);g.setColor(StudioVisuals.BORDER);g.drawRoundRect(8,8,getWidth()-17,getHeight()-17,20,20);g.dispose();
            }
        };
    }
    StudioLoginPanel(JPanel form,JTextField identifier,JPasswordField password,JButton login,JButton tap,JButton manual,JButton offline,JButton settings,JLabel nfcState){
        super(new BorderLayout());setBackground(StudioVisuals.BACKGROUND);
        form.removeAll();form.setLayout(new BoxLayout(form,BoxLayout.Y_AXIS));form.setBorder(BorderFactory.createEmptyBorder(20,34,20,34));
        var source=StudioLoginPanel.class.getResource("icons/SmartStudio-128.png");
        JLabel icon=new JLabel(source==null?null:new ImageIcon(new ImageIcon(source).getImage().getScaledInstance(60,60,Image.SCALE_SMOOTH)));icon.setHorizontalAlignment(SwingConstants.CENTER);icon.getAccessibleContext().setAccessibleName("SmartStudio Creative Brush");
        addRow(form,icon,60);gap(form,8);
        JLabel title=new JLabel("Welcome to SmartStudio",SwingConstants.CENTER);title.setFont(StudioVisuals.font(Font.BOLD,26));title.setForeground(StudioVisuals.INK);addRow(form,title,34);
        JLabel subtitle=new JLabel("Sign in with your SmartStock staff account",SwingConstants.CENTER);subtitle.setFont(StudioVisuals.font(Font.PLAIN,15));subtitle.setForeground(new Color(0x71687F));addRow(form,subtitle,22);gap(form,12);
        identifier.getAccessibleContext().setAccessibleName("Username, email or badge ID");password.getAccessibleContext().setAccessibleName("Password or employee PIN");
        label(form,"Username, email or badge ID",identifier);addRow(form,identifier,44);gap(form,12);
        label(form,"Password or employee PIN",password);
        JPanel passwordRow=new JPanel(new BorderLayout());passwordRow.setOpaque(false);passwordRow.setBorder(StudioVisuals.fieldBorder());passwordRow.add(password);
        char echo=password.getEchoChar();JButton reveal=new JButton(StudioVisuals.layerIcon("visible",18));quiet(reveal);reveal.setPreferredSize(new Dimension(38,40));reveal.getAccessibleContext().setAccessibleName("Show password");reveal.setToolTipText("Show password");
        reveal.addActionListener(e->{boolean show=password.getEchoChar()!=0;password.setEchoChar(show?(char)0:echo);reveal.setIcon(StudioVisuals.layerIcon(show?"hidden":"visible",18));reveal.getAccessibleContext().setAccessibleName(show?"Hide password":"Show password");reveal.setToolTipText(show?"Hide password":"Show password");});
        password.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){
            private void resetWhenCleared(){if(password.getDocument().getLength()==0){password.setEchoChar(echo);reveal.setIcon(StudioVisuals.layerIcon("visible",18));reveal.getAccessibleContext().setAccessibleName("Show password");reveal.setToolTipText("Show password");}}
            public void insertUpdate(javax.swing.event.DocumentEvent e){resetWhenCleared();}public void removeUpdate(javax.swing.event.DocumentEvent e){resetWhenCleared();}public void changedUpdate(javax.swing.event.DocumentEvent e){resetWhenCleared();}
        });
        password.addFocusListener(new java.awt.event.FocusAdapter(){public void focusGained(java.awt.event.FocusEvent e){passwordRow.scrollRectToVisible(new Rectangle(0,0,passwordRow.getWidth(),passwordRow.getHeight()));passwordRow.repaint();}public void focusLost(java.awt.event.FocusEvent e){passwordRow.repaint();}});
        passwordRow.add(reveal,BorderLayout.EAST);addRow(form,passwordRow,44);gap(form,16);
        StudioVisuals.primaryButton(login);login.setFont(StudioVisuals.font(Font.BOLD,17));login.setHorizontalTextPosition(SwingConstants.LEFT);addRow(form,login,46);gap(form,10);
        JSeparator divider=new JSeparator();divider.setForeground(StudioVisuals.BORDER);addRow(form,divider,1);gap(form,12);
        tap.setText("Tap your staff badge");StudioVisuals.utilityButton(tap);tap.setSelected(true);tap.setIcon(StudioVisuals.icon("NFC staff badge",24));tap.setIconTextGap(14);tap.setForeground(StudioVisuals.PURPLE);addRow(form,tap,44);
        nfcState.setHorizontalAlignment(SwingConstants.CENTER);nfcState.setFont(StudioVisuals.font(Font.PLAIN,12));nfcState.setForeground(new Color(0x71687F));addRow(form,nfcState,20);
        quiet(manual);addRow(form,manual,26);gap(form,8);
        StudioVisuals.headerButton(offline);offline.setFont(StudioVisuals.font(Font.BOLD,14));addRow(form,offline,44);gap(form,3);
        JLabel warning=new JLabel("Offline edits are temporary and cannot be saved.",SwingConstants.CENTER);warning.setFont(StudioVisuals.font(Font.PLAIN,12));warning.setForeground(new Color(0x71687F));addRow(form,warning,20);gap(form,6);
        quiet(settings);settings.setIcon(StudioVisuals.icon("Settings",16));addRow(form,settings,30);
        StudioVisuals.styleTree(form);password.putClientProperty("studio.visual.field",true);password.setBorder(BorderFactory.createEmptyBorder(6,9,6,4));password.setOpaque(false);
        JPanel centered=new JPanel(new GridBagLayout()){
            @Override public Dimension getPreferredSize(){return new Dimension(600,form.getPreferredSize().height+4);}
        };centered.setBackground(StudioVisuals.BACKGROUND);GridBagConstraints c=new GridBagConstraints();c.insets=new Insets(2,2,2,2);centered.add(form,c);
        JPanel view=new JPanel(new BorderLayout());view.setBackground(StudioVisuals.BACKGROUND);view.add(centered);
        JScrollPane scroll=new JScrollPane(view);scroll.setBorder(BorderFactory.createEmptyBorder());scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);scroll.getVerticalScrollBar().setUnitIncrement(32);add(scroll);
        // Center at roomy sizes; let the card scroll rather than clipping actions on small displays.
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter(){public void componentResized(java.awt.event.ComponentEvent e){
            Dimension extent=scroll.getViewport().getExtentSize();view.setPreferredSize(new Dimension(extent.width,Math.max(extent.height,centered.getPreferredSize().height)));view.revalidate();
        }});
        form.setPreferredSize(new Dimension(560,form.getPreferredSize().height));
    }
    private static void quiet(JButton button){button.putClientProperty("studio.visual.role","link");button.setUI(new javax.swing.plaf.basic.BasicButtonUI(){
        @Override public void paint(Graphics original,JComponent c){super.paint(original,c);if(button.hasFocus()){Graphics2D g=(Graphics2D)original.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(StudioVisuals.PURPLE);g.drawRoundRect(1,1,c.getWidth()-3,c.getHeight()-3,8,8);g.dispose();}}
    });button.setFont(StudioVisuals.font(Font.PLAIN,13));button.setForeground(StudioVisuals.PURPLE);button.setContentAreaFilled(false);button.setOpaque(false);button.setBorder(BorderFactory.createEmptyBorder(2,4,2,4));button.setFocusPainted(true);}
    private static void label(JPanel parent,String text,JComponent field){JLabel label=new JLabel(text);label.setLabelFor(field);label.setFont(StudioVisuals.font(Font.BOLD,14));label.setForeground(StudioVisuals.INK);addRow(parent,label,20);gap(parent,4);}
    private static void gap(JPanel parent,int height){parent.add(Box.createVerticalStrut(height));}
    private static void addRow(JPanel parent,JComponent component,int height){component.setAlignmentX(Component.CENTER_ALIGNMENT);component.setPreferredSize(new Dimension(492,height));component.setMaximumSize(new Dimension(Integer.MAX_VALUE,height));component.setMinimumSize(new Dimension(180,height));component.addFocusListener(new java.awt.event.FocusAdapter(){public void focusGained(java.awt.event.FocusEvent e){component.scrollRectToVisible(new Rectangle(0,0,component.getWidth(),component.getHeight()));}});parent.add(component);}
}
