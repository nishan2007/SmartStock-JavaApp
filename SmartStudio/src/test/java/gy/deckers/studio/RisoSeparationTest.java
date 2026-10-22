package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RisoSeparationTest {
    final List<RisoSeparation.Ink> inks=List.of(new RisoSeparation.Ink("Black",Color.BLACK,true),new RisoSeparation.Ink("Green",new Color(0,128,0),true));
    BufferedImage strip(){var image=new BufferedImage(4,1,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,Color.WHITE.getRGB());image.setRGB(1,0,Color.BLACK.getRGB());image.setRGB(2,0,new Color(0,128,0).getRGB());image.setRGB(3,0,new Color(128,192,128).getRGB());return image;}
    @Test void inksAreIsolatedAndPaperStaysWhite(){
        var source=strip();var black=RisoSeparation.render(source,inks,0);var green=RisoSeparation.render(source,inks,1);
        assertEquals(BufferedImage.TYPE_BYTE_GRAY,black.getType());
        assertEquals(255,black.getRaster().getSample(0,0,0));assertEquals(0,black.getRaster().getSample(1,0,0));assertEquals(255,black.getRaster().getSample(2,0,0));
        assertEquals(255,green.getRaster().getSample(1,0,0));assertEquals(0,green.getRaster().getSample(2,0,0));assertEquals(128,green.getRaster().getSample(3,0,0),1);
        assertEquals(Color.BLACK.getRGB(),source.getRGB(1,0));
    }
    @Test void combinedPreviewMatchesChosenSolidInks(){var image=RisoSeparation.render(strip(),inks,-1);assertEquals(Color.WHITE.getRGB(),image.getRGB(0,0));assertEquals(Color.BLACK.getRGB(),image.getRGB(1,0));assertEquals(new Color(0,128,0).getRGB(),image.getRGB(2,0));}
    @Test void disablingAnInkMergesIntoRemainingPalette(){var palette=List.of(inks.get(0),new RisoSeparation.Ink("Green",inks.get(1).color(),false));var green=RisoSeparation.render(strip(),palette,1);for(int x=0;x<4;x++)assertEquals(255,green.getRaster().getSample(x,0,0));}
    @Test void detectionIsDeterministicAcrossSheetsAndOmitsWhite(){var a=new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB);var b=new BufferedImage(10,10,BufferedImage.TYPE_INT_RGB);Graphics2D g=b.createGraphics();g.setColor(Color.GREEN);g.fillRect(0,0,10,10);g.dispose();var detected=RisoSeparation.detect(List.of(a,b),2);assertEquals(2,detected.size());assertEquals(detected,RisoSeparation.detect(List.of(a,b),2));}
    @Test void invalidPaletteIsRejected(){assertThrows(IllegalArgumentException.class,()->RisoSeparation.detect(List.of(strip()),9));assertThrows(IllegalArgumentException.class,()->RisoSeparation.render(strip(),List.of(new RisoSeparation.Ink("Off",Color.BLACK,false)),0));}
}
