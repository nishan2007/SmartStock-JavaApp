package ui.helpers;

import services.LanApiClient;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/** Touch, stylus, and mouse friendly authorization capture for account charges. */
public final class CustomerChargeAuthorizationDialog {
    private CustomerChargeAuthorizationDialog(){}

    public static LanApiClient.ChargeAuthorization capture(Component parent,String accountName){
        JTextField name=new JTextField(32);JCheckBox unsigned=new JCheckBox("Unable to capture signature");
        JTextArea reason=new JTextArea(3,32);reason.setLineWrap(true);reason.setWrapStyleWord(true);reason.setEnabled(false);
        Pad pad=new Pad();JButton clear=new JButton("Clear"),redo=new JButton("Redo");
        Dimension touch=new Dimension(120,48);clear.setPreferredSize(touch);redo.setPreferredSize(touch);
        clear.addActionListener(e->pad.clear());redo.addActionListener(e->pad.redo());
        unsigned.addActionListener(e->{boolean on=unsigned.isSelected();pad.setEnabled(!on);reason.setEnabled(on);});
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.add(clear);buttons.add(redo);
        JPanel panel=new JPanel();panel.setBorder(new EmptyBorder(12,12,12,12));panel.setLayout(new BoxLayout(panel,BoxLayout.Y_AXIS));
        panel.add(new JLabel("Account: "+accountName));panel.add(Box.createVerticalStrut(8));panel.add(new JLabel("Representative name:"));panel.add(name);
        panel.add(Box.createVerticalStrut(8));panel.add(new JLabel("Sign inside the box:"));panel.add(pad);panel.add(buttons);panel.add(unsigned);
        panel.add(new JLabel("Reason signature was not captured:"));panel.add(new JScrollPane(reason));
        while(true){int choice=JOptionPane.showConfirmDialog(parent,panel,"Authorize Customer Account Charge",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE);
            if(choice!=JOptionPane.OK_OPTION)return null;
            String person=name.getText().trim();if(person.isBlank()){message(parent,"Enter the representative's name.");continue;}
            if(unsigned.isSelected()){String why=reason.getText().trim();if(why.isBlank()){message(parent,"Enter why the signature could not be captured.");continue;}return new LanApiClient.ChargeAuthorization(person,null,why);}
            if(!pad.meaningful()){message(parent,"Please provide a complete signature, not only a tap or dot.");continue;}
            try{return new LanApiClient.ChargeAuthorization(person,pad.base64(),null);}catch(Exception ex){message(parent,"The signature could not be prepared.");}
        }
    }
    private static void message(Component p,String text){JOptionPane.showMessageDialog(p,text,"Account Authorization",JOptionPane.WARNING_MESSAGE);}

    static final class Pad extends JComponent {
        private BufferedImage image,redo;private Point last;private int length;
        Pad(){setPreferredSize(new Dimension(620,240));setMinimumSize(new Dimension(420,180));setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY,2));
            MouseAdapter input=new MouseAdapter(){@Override public void mousePressed(MouseEvent e){ensure();last=e.getPoint();redo=null;}
                @Override public void mouseDragged(MouseEvent e){if(last==null)return;Graphics2D g=image.createGraphics();g.setColor(Color.BLACK);g.setStroke(new BasicStroke(4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.drawLine(last.x,last.y,e.getX(),e.getY());g.dispose();length+=(int)last.distance(e.getPoint());last=e.getPoint();repaint();}
                @Override public void mouseReleased(MouseEvent e){last=null;}};addMouseListener(input);addMouseMotionListener(input);}
        private void ensure(){if(image!=null&&image.getWidth()==getWidth()&&image.getHeight()==getHeight())return;BufferedImage next=new BufferedImage(Math.max(1,getWidth()),Math.max(1,getHeight()),BufferedImage.TYPE_INT_RGB);Graphics2D g=next.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,next.getWidth(),next.getHeight());if(image!=null)g.drawImage(image,0,0,next.getWidth(),next.getHeight(),null);g.dispose();image=next;}
        void clear(){ensure();redo=image;image=new BufferedImage(getWidth(),getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,getWidth(),getHeight());g.dispose();length=0;repaint();}
        void redo(){if(redo!=null){image=redo;redo=null;length=100;repaint();}}
        boolean meaningful(){return length>=80;}
        String base64()throws Exception{ensure();ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return Base64.getEncoder().encodeToString(out.toByteArray());}
        @Override protected void paintComponent(Graphics g){super.paintComponent(g);ensure();g.drawImage(image,0,0,getWidth(),getHeight(),null);}
    }
}
