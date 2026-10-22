package ui.screens.companyprefs;

import ui.helpers.CustomerChargeAuthorizationDialog;
import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;

/** Register-local hardware test; never submits an authorization or sale. */
public final class SignaturePadPanel extends JPanel {
    public SignaturePadPanel() {
        super(new BorderLayout(0,16));
        JTextArea instructions=new JTextArea("XP-Pen Star G430S setup on this register:\n\n"
                + "1. Connect the pad by USB and install the official XP-Pen G430S driver.\n"
                + "2. Set the pen tip to left click. With a compatible Windows Wintab driver, "
                + "SmartStock temporarily maps the pad to the signature box while the signing dialog is active. "
                + "Use the mouse for the name, Clear, Redo, and confirmation controls. "
                + "Normal mapping returns when the dialog closes or loses focus.\n"
                + "3. Open the test below. Enter a test name, then touch the pen tip to the pad "
                + "and sign while watching the screen. Lift the pen between strokes.\n"
                + "4. Check Clear, Redo, and the saved-image preview. If strokes do not appear, "
                + "check the mapping status shown in the dialog and the driver's Windows Ink setting, then retry. "
                + "If box mapping is unavailable, normal pointer signing still works.\n\n"
                + "This uses the same signing dialog and PNG capture as charging a customer account. "
                + "Test signatures are not saved or sent to the server. Mouse and touchscreen input also work; "
                + "SmartStock cannot identify which pointer device made a stroke.");
        instructions.setEditable(false);instructions.setLineWrap(true);instructions.setWrapStyleWord(true);
        instructions.setOpaque(false);instructions.setFont(UIManager.getFont("Label.font"));
        add(instructions,BorderLayout.CENTER);
        JButton test=new JButton("Test customer signature pad");
        test.addActionListener(e->{
            var result=CustomerChargeAuthorizationDialog.capture(this,"TEST ONLY — no account charge");
            if(result==null)return;
            if(result.signaturePngBase64()==null){
                JOptionPane.showMessageDialog(this,"Test completed without a signature. Repeat and sign to verify the pad.");return;
            }
            try {
                var image=ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(result.signaturePngBase64())));
                JOptionPane.showMessageDialog(this,new JLabel(new ImageIcon(image)),"Test signature preview — not saved",JOptionPane.PLAIN_MESSAGE);
            } catch(Exception ex){
                JOptionPane.showMessageDialog(this,"The test signature could not be previewed.","Signature Pad",JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));actions.add(test);add(actions,BorderLayout.SOUTH);
    }
}
