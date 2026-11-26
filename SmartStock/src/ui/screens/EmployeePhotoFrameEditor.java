package ui.screens;

import services.EmployeePhotoFraming;
import services.EmployeePhotoFraming.Frame;
import ui.design.DeckersPalette;
import ui.design.DeckersSwing;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/** One composition, with the actual badge and avatar crops visible together. */
public final class EmployeePhotoFrameEditor extends JPanel {
    private final BufferedImage source;
    private Frame frame;
    private final Canvas canvas=new Canvas();
    private final JLabel avatar=new JLabel(),badge=new JLabel();
    private final JSlider zoom=new JSlider(10,600,100);

    public EmployeePhotoFrameEditor(BufferedImage source,Frame initial) {
        super(new BorderLayout(18,12));
        this.source=java.util.Objects.requireNonNull(source);this.frame=initial;
        setBackground(DeckersPalette.surface());setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        JLabel instructions=new JLabel("Drag to position. Keep the whole head inside the circle; the rectangle is the badge photo.");
        instructions.setForeground(DeckersPalette.text());add(instructions,BorderLayout.NORTH);
        canvas.setPreferredSize(new Dimension(330,380));add(canvas,BorderLayout.CENTER);
        JPanel previews=new JPanel();previews.setOpaque(false);previews.setLayout(new BoxLayout(previews,BoxLayout.Y_AXIS));
        previews.add(new JLabel("Web profile / avatar"));previews.add(Box.createVerticalStrut(8));previews.add(avatar);
        previews.add(Box.createVerticalStrut(20));previews.add(new JLabel("Badge photo"));previews.add(Box.createVerticalStrut(8));previews.add(badge);
        add(previews,BorderLayout.EAST);
        JPanel controls=new JPanel(new BorderLayout(8,8));controls.setOpaque(false);
        controls.add(new JLabel("Zoom"),BorderLayout.WEST);zoom.setValue((int)Math.round(initial.zoom()*100));controls.add(zoom);
        JButton reset=new JButton("Fit original");DeckersSwing.styleUtilityButton(reset,DeckersPalette.PURPLE);controls.add(reset,BorderLayout.EAST);
        JLabel note=new JLabel("The original is kept. Use Reframe later to adjust both views.");note.setForeground(DeckersPalette.muted());controls.add(note,BorderLayout.SOUTH);
        add(controls,BorderLayout.SOUTH);
        zoom.addChangeListener(e->{frame=new Frame(zoom.getValue()/100.0,frame.x(),frame.y());refresh();});
        reset.addActionListener(e->{frame=EmployeePhotoFraming.fit();zoom.setValue((int)Math.round(frame.zoom()*100));refresh();});
        refresh();
    }
    public Frame frame(){return frame;}
    public BufferedImage rendered(){return EmployeePhotoFraming.render(source,frame);}
    private void refresh(){
        BufferedImage image=rendered();
        avatar.setIcon(new ImageIcon(EmployeePhotoFraming.avatar(image,120,true,true)));
        badge.setIcon(new ImageIcon(image.getScaledInstance(132,152,Image.SCALE_SMOOTH)));canvas.repaint();
    }
    public static Frame show(Component parent,BufferedImage source,Frame initial){
        EmployeePhotoFrameEditor editor=new EmployeePhotoFrameEditor(source,initial);
        return JOptionPane.showConfirmDialog(parent,editor,"Frame employee photo",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE)==JOptionPane.OK_OPTION?editor.frame():null;
    }
    private final class Canvas extends JPanel {
        private Point previous;
        Canvas(){
            MouseAdapter mouse=new MouseAdapter(){
                public void mousePressed(MouseEvent e){previous=e.getPoint();}
                public void mouseReleased(MouseEvent e){previous=null;}
                public void mouseDragged(MouseEvent e){
                    if(previous==null)return;Rectangle r=boundsForPhoto();
                    double x=frame.x()+(e.getX()-previous.x)/(double)r.width,y=frame.y()+(e.getY()-previous.y)/(double)r.height;
                    frame=new Frame(frame.zoom(),Math.max(-3,Math.min(3,x)),Math.max(-3,Math.min(3,y)));previous=e.getPoint();refresh();
                }
            };addMouseListener(mouse);addMouseMotionListener(mouse);setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        }
        private Rectangle boundsForPhoto(){double scale=Math.min(getWidth()/660.0,getHeight()/760.0);int w=Math.max(1,(int)(660*scale)),h=Math.max(1,(int)(760*scale));return new Rectangle((getWidth()-w)/2,(getHeight()-h)/2,w,h);}
        protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
            try{Rectangle r=boundsForPhoto();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                g.drawImage(rendered(),r.x,r.y,r.width,r.height,null);g.setStroke(new BasicStroke(2));
                g.setColor(DeckersPalette.PURPLE);g.drawRect(r.x+1,r.y+1,r.width-2,r.height-2);
                g.setColor(Color.WHITE);g.setStroke(new BasicStroke(4));int cy=r.y+(r.height-r.width)/2;g.drawOval(r.x+2,cy+2,r.width-4,r.width-4);
                g.setColor(DeckersPalette.PURPLE);g.setStroke(new BasicStroke(2));g.drawOval(r.x+2,cy+2,r.width-4,r.width-4);
            }finally{g.dispose();}
        }
    }
}
