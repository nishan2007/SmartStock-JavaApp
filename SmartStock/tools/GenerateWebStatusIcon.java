import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Regenerate the Deckers web-monitor tile icon; run from the repository root. */
class GenerateWebStatusIcon {
    public static void main(String[] args)throws Exception{
        BufferedImage image=new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        Color purple=new Color(111,42,179),orange=new Color(255,102,0),lime=new Color(161,219,33);
        g.setColor(purple);g.setStroke(new BasicStroke(13,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.drawRoundRect(29,38,198,142,20,20);g.drawLine(128,185,128,213);g.drawLine(87,219,169,219);
        g.setColor(orange);g.fillRoundRect(63,113,22,35,7,7);g.fillRoundRect(101,93,22,55,7,7);g.fillRoundRect(139,70,22,78,7,7);
        g.setColor(lime);g.fillOval(173,146,64,64);g.setColor(Color.WHITE);g.setStroke(new BasicStroke(9,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.drawLine(188,178,199,188);g.drawLine(199,188,221,166);g.dispose();
        Path output=Path.of("SmartStock/src/ICONS/MainMenuWebStatus.png");ImageIO.write(image,"png",output.toFile());
    }
}
