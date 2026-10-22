package publishing;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicationWordArtTest {
  @TempDir Path temp;
  @Test void curvedEditableLetteringSurvivesArchiveAndRendersDistinctShapes() throws Exception {
    Publication p=new Publication();var e=new Publication.Element();p.pages.get(0).objects.add(e);
    e.text="Deckers";e.wordArtGradient=true;e.wordArtHasShadow=true;
    int[] previous=null;
    for(String shape:new String[]{"PLAIN","ARCH_UP","ARCH_DOWN","WAVE"}){
      e.wordArt=shape;p.validate();
      BufferedImage image=new BufferedImage(600,150,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
      g.setColor(Color.WHITE);g.fillRect(0,0,600,150);g.scale(10,10);PublicationWordArt.draw(g,e,e.text);g.dispose();
      int[] pixels=image.getRGB(0,0,600,150,null,0,600);
      assertTrue(Arrays.stream(pixels).anyMatch(x->x!=Color.WHITE.getRGB()));
      if(previous!=null)assertFalse(Arrays.equals(previous,pixels));previous=pixels;
    }
    Path file=temp.resolve("wordart.sspub");java.nio.file.Files.write(file,PublicationFiles.archive(p));
    Publication loaded=PublicationFiles.read(java.nio.file.Files.readAllBytes(file));var field=loaded.pages.get(0).objects.get(0);
    assertEquals("WAVE",field.wordArt);assertTrue(field.wordArtGradient);assertTrue(field.wordArtHasShadow);
    field.text="Changed";field.x=20;assertEquals("Deckers",e.text);assertNotEquals(e.x,field.x);
  }
  @Test void rejectsUnboundedAndNonfiniteEffects(){
    Publication p=new Publication();var e=new Publication.Element();p.pages.get(0).objects.add(e);
    e.wordArtBend=Double.NaN;assertThrows(IllegalArgumentException.class,p::validate);
    e.wordArtBend=.3;e.wordArtOutlineWidth=100;assertThrows(IllegalArgumentException.class,p::validate);
  }
}

