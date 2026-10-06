package gy.deckers.studio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class StudioImageFileTest {
    @TempDir Path directory;
    @Test void readsPngWithSpacesAndUnicodeAndRetainsItsPreviewAndAlpha()throws Exception {
        Path imagePath=directory.resolve("Product photo – sample.png");
        var image=new BufferedImage(128,96,BufferedImage.TYPE_INT_ARGB);image.setRGB(45,30,0x7fff5b00);
        ImageIO.write(image,"png",imagePath.toFile());
        var loaded=StudioImageFile.load(imagePath);
        assertEquals(128,loaded.image().getWidth());assertEquals(96,loaded.image().getHeight());
        assertEquals(0x7fff5b00,loaded.image().getRGB(45,30));assertArrayEquals(Files.readAllBytes(imagePath),loaded.bytes());
    }
    @Test void inaccessibleFileReportsActionableMessageInsteadOfOnlyItsPath() {
        IOException ex=assertThrows(IOException.class,()->StudioImageFile.load(directory.resolve("missing.png")));
        assertTrue(ex.getMessage().contains("network drive"));assertTrue(ex.getMessage().contains("read access"));
    }
    @Test void rejectsOversizedAndInvalidFilesWithoutUsingFileSizeMetadata()throws Exception {
        Path file=directory.resolve("large.png");Files.write(file,new byte[6*1024*1024+1]);
        assertThrows(IllegalArgumentException.class,()->StudioImageFile.load(file));
        Files.writeString(file,"not a PNG");assertThrows(IllegalArgumentException.class,()->StudioImageFile.load(file));
    }
}
