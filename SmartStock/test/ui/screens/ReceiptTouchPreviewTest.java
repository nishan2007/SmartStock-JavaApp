package ui.screens;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ReceiptTouchPreviewTest {
    @Test void renderTouchControls() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JPanel root = new JPanel(new BorderLayout(12,12));
            root.setBorder(BorderFactory.createEmptyBorder(14,14,14,14));
            root.setBackground(new Color(245,247,250));
            JPanel header = new JPanel(new BorderLayout(12,8));
            header.setOpaque(false);
            JLabel title = new JLabel("Receipt Preview");
            title.setFont(new Font("SansSerif",Font.BOLD,24));
            header.add(title,BorderLayout.NORTH);
            JPanel selectors = new JPanel(new GridLayout(1,2,16,0));
            selectors.setOpaque(false);
            selectors.add(ReceiptPreview.touchSelector("Printer",new JComboBox<>(new String[]{"Receipt printer"})));
            selectors.add(ReceiptPreview.touchSelector("Format",new JComboBox<>(new String[]{"Receipt (40 columns)"})));
            header.add(selectors,BorderLayout.CENTER);
            root.add(header,BorderLayout.NORTH);
            ReceiptPreview.ReceiptPaperPanel paper = new ReceiptPreview.ReceiptPaperPanel();
            paper.setReceiptText("           SMARTSTOCK\n          SAMPLE RECEIPT\n\nReceipt: SAMPLE-001\n--------------------------------\nBlue ballpoint pen       2 x $100\nNotebook                1 x $500\n--------------------------------\nTOTAL                       $700\nCASH                        $700\nCHANGE                        $0\n\n       Thank you for shopping!",false,"SAMPLE-001");
            root.add(new JScrollPane(paper),BorderLayout.CENTER);
            JPanel buttons = new JPanel(new GridLayout(2,2,12,12));
            for(String label:new String[]{"Email Receipt","Send WhatsApp","Print Receipt","Close"}) {
                JButton button=new JButton(label);
                ReceiptPreview.styleTouchButton(button);
                if(label.equals("Print Receipt")) {button.setBackground(ui.design.DeckersPalette.PURPLE);button.setForeground(Color.WHITE);button.setOpaque(true);}
                buttons.add(button);
            }
            root.add(buttons,BorderLayout.SOUTH);
            root.setSize(900,800);
            layout(root);
            for(Component button:buttons.getComponents()) assertTrue(button.getHeight()>=58);
            BufferedImage image=new BufferedImage(900,800,BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics=image.createGraphics();root.printAll(graphics);graphics.dispose();
            try {Files.createDirectories(Path.of("target"));ImageIO.write(image,"png",Path.of("target/receipt-touch-preview.png").toFile());}
            catch(Exception e){throw new RuntimeException(e);}
        });
    }
    private static void layout(Container parent){parent.doLayout();for(Component c:parent.getComponents())if(c instanceof Container child)layout(child);}
}
