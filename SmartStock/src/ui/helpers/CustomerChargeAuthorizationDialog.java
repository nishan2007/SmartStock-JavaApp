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
        JLabel tabletStatus=new JLabel("Preparing pen pad mapping...");
        tabletStatus.setPreferredSize(new Dimension(620,40));
        tabletStatus.setMaximumSize(new Dimension(Integer.MAX_VALUE,40));
        Dimension touch=new Dimension(120,48);clear.setPreferredSize(touch);redo.setPreferredSize(touch);
        clear.addActionListener(e->pad.clear());redo.addActionListener(e->pad.redo());
        unsigned.addActionListener(e->{boolean on=unsigned.isSelected();pad.setEnabled(!on);reason.setEnabled(on);});
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT));buttons.add(unsigned);buttons.add(clear);buttons.add(redo);
        JPanel panel=new JPanel(new BorderLayout(0,8));panel.setBorder(new EmptyBorder(8,8,8,8));
        JPanel heading=new JPanel(new BorderLayout(12,6));
        JLabel account=new JLabel("Account: "+accountName);
        account.setFont(account.getFont().deriveFont(Font.BOLD,18f));
        heading.add(account,BorderLayout.NORTH);
        JPanel representative=new JPanel(new BorderLayout(8,0));
        representative.add(new JLabel("Representative name:"),BorderLayout.WEST);representative.add(name,BorderLayout.CENTER);
        heading.add(representative,BorderLayout.CENTER);
        heading.add(new JLabel("Sign in the large white area below. Watch the screen while using the pen pad."),BorderLayout.SOUTH);
        panel.add(heading,BorderLayout.NORTH);panel.add(pad,BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(0,4));footer.add(buttons,BorderLayout.NORTH);footer.add(tabletStatus,BorderLayout.CENTER);
        JPanel unsignedReason=new JPanel(new BorderLayout(8,0));
        unsignedReason.add(new JLabel("Reason signature was not captured:"),BorderLayout.WEST);
        unsignedReason.add(new JScrollPane(reason),BorderLayout.CENTER);unsignedReason.setVisible(false);
        footer.add(unsignedReason,BorderLayout.SOUTH);panel.add(footer,BorderLayout.SOUTH);
        unsigned.addActionListener(e->{unsignedReason.setVisible(unsigned.isSelected());panel.revalidate();});
        while(true){int choice=showSigningDialog(parent,panel,pad,tabletStatus);
            if(choice!=JOptionPane.OK_OPTION)return null;
            String person=name.getText().trim();if(person.isBlank()){message(parent,"Enter the representative's name.");continue;}
            if(unsigned.isSelected()){String why=reason.getText().trim();if(why.isBlank()){message(parent,"Enter why the signature could not be captured.");continue;}return new LanApiClient.ChargeAuthorization(person,null,why);}
            if(!pad.meaningful()){message(parent,"Please provide a complete signature, not only a tap or dot.");continue;}
            try{return new LanApiClient.ChargeAuthorization(person,pad.base64(),null);}catch(Exception ex){message(parent,"The signature could not be prepared.");}
        }
    }
    /** Signed stock use always belongs to the authenticated employee; no unsigned bypass. */
    public static String captureEmployeeSignature(Component parent,String employee){
        Pad pad=new Pad();JLabel status=new JLabel("Preparing pen pad mapping...");
        JPanel panel=new JPanel(new BorderLayout(8,8));
        panel.add(new JLabel("Stock expense — employee: "+employee),BorderLayout.NORTH);
        panel.add(pad,BorderLayout.CENTER);
        JButton clear=new JButton("Clear signature"),redo=new JButton("Redo");
        clear.addActionListener(e->pad.clear());redo.addActionListener(e->pad.redo());
        JPanel footer=new JPanel(new BorderLayout());JPanel buttons=new JPanel();buttons.add(clear);buttons.add(redo);
        footer.add(buttons,BorderLayout.NORTH);footer.add(status,BorderLayout.SOUTH);panel.add(footer,BorderLayout.SOUTH);
        while(showSigningDialog(parent,panel,pad,status)==JOptionPane.OK_OPTION){
            if(!pad.meaningful()){message(parent,"Please provide a complete handwritten signature.");continue;}
            try{return pad.base64();}catch(Exception e){message(parent,"The signature could not be prepared.");}
        }
        return null;
    }

    private static int showSigningDialog(Component parent,JPanel panel,Pad pad,JLabel status){
        Window owner=parent instanceof Window w?w:parent==null?null:SwingUtilities.getWindowAncestor(parent);
        JDialog dialog=new JDialog(owner,"Authorize Customer Account Charge",Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        int[] choice={JOptionPane.CANCEL_OPTION};
        JButton accept=new JButton("Accept signature"),cancel=new JButton("Cancel");
        accept.setPreferredSize(new Dimension(180,48));cancel.setPreferredSize(new Dimension(120,48));
        accept.addActionListener(e->{choice[0]=JOptionPane.OK_OPTION;dialog.setVisible(false);});
        cancel.addActionListener(e->dialog.setVisible(false));
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.RIGHT));actions.add(cancel);actions.add(accept);
        JPanel content=new JPanel(new BorderLayout());content.add(panel,BorderLayout.CENTER);content.add(actions,BorderLayout.SOUTH);
        dialog.setContentPane(content);dialog.getRootPane().setDefaultButton(accept);
        dialog.getRootPane().registerKeyboardAction(e->dialog.setVisible(false),KeyStroke.getKeyStroke("ESCAPE"),JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.setResizable(true);
        GraphicsConfiguration screen=dialog.getGraphicsConfiguration();
        Rectangle usable=new Rectangle(screen.getBounds());
        Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(screen);
        usable.x+=insets.left;usable.y+=insets.top;
        usable.width-=insets.left+insets.right;usable.height-=insets.top+insets.bottom;
        // Use nearly all of this monitor without covering the taskbar or other monitors.
        dialog.setBounds(usable.x+8,usable.y+8,Math.max(1,usable.width-16),Math.max(1,usable.height-16));
        ui.design.DeckersSwing.styleUtilityButton(accept,ui.design.DeckersPalette.ORANGE);
        ui.design.DeckersSwing.styleUtilityButton(cancel,ui.design.DeckersPalette.ORANGE);
        ThemeManager.applyToWindow(dialog);
        try(SignatureTabletMapping mapping=new SignatureTabletMapping(pad,status)){
            mapping.start();
            dialog.setVisible(true);
            return choice[0];
        }finally{dialog.dispose();}
    }
    private static void message(Component p,String text){JOptionPane.showMessageDialog(p,text,"Account Authorization",JOptionPane.WARNING_MESSAGE);}

    static final class Pad extends JComponent {
        private BufferedImage image,redo;private Point last;private int length,redoLength;
        Pad(){setPreferredSize(new Dimension(1000,480));setMinimumSize(new Dimension(200,100));setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY,2));
            setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
            MouseAdapter input=new MouseAdapter(){@Override public void mousePressed(MouseEvent e){if(!isEnabled()||!SwingUtilities.isLeftMouseButton(e))return;ensure();last=e.getPoint();redo=null;}
                @Override public void mouseDragged(MouseEvent e){if(!isEnabled()||last==null)return;ensure();Point next=new Point(Math.max(0,Math.min(getWidth()-1,e.getX())),Math.max(0,Math.min(getHeight()-1,e.getY())));Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setColor(Color.BLACK);g.setStroke(new BasicStroke(4f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.drawLine(last.x,last.y,next.x,next.y);g.dispose();length+=(int)last.distance(next);last=next;repaint();}
                @Override public void mouseReleased(MouseEvent e){last=null;}};addMouseListener(input);addMouseMotionListener(input);}
        private void ensure(){if(image!=null&&image.getWidth()==getWidth()&&image.getHeight()==getHeight())return;BufferedImage next=new BufferedImage(Math.max(1,getWidth()),Math.max(1,getHeight()),BufferedImage.TYPE_INT_RGB);Graphics2D g=next.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,next.getWidth(),next.getHeight());if(image!=null)g.drawImage(image,0,0,next.getWidth(),next.getHeight(),null);g.dispose();image=next;}
        @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);last=null;}
        void clear(){if(!isEnabled())return;ensure();last=null;redo=image;redoLength=length;image=new BufferedImage(Math.max(1,getWidth()),Math.max(1,getHeight()),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,image.getWidth(),image.getHeight());g.dispose();length=0;repaint();}
        void redo(){if(isEnabled()&&redo!=null){last=null;image=redo;redo=null;length=redoLength;repaint();}}
        boolean meaningful(){return length>=80;}
        String base64()throws Exception{
            ensure();BufferedImage export=image;
            double scale=Math.min(1.0,Math.min(1200.0/image.getWidth(),600.0/image.getHeight()));
            if(scale<1.0){
                export=new BufferedImage(Math.max(100,(int)Math.round(image.getWidth()*scale)),
                        Math.max(40,(int)Math.round(image.getHeight()*scale)),BufferedImage.TYPE_INT_RGB);
                Graphics2D g=export.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(image,0,0,export.getWidth(),export.getHeight(),null);g.dispose();
            }
            ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(export,"png",out);return Base64.getEncoder().encodeToString(out.toByteArray());
        }
        @Override protected void paintComponent(Graphics g){super.paintComponent(g);ensure();g.drawImage(image,0,0,getWidth(),getHeight(),null);}
    }
}
