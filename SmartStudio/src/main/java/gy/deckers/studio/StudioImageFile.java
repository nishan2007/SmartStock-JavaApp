package gy.deckers.studio;

import services.StudioBackgroundService;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;

/** Streams network files into bounded local memory; does not require remote size/attribute access. */
final class StudioImageFile {
    record Loaded(byte[] bytes,BufferedImage image,String name) { }
    static Loaded load(Path path)throws Exception {
        byte[] bytes;
        try(InputStream stream=Files.newInputStream(path)) {bytes=stream.readNBytes(6*1024*1024+1);}
        catch(IOException ex) {throw new IOException("Cannot open this image from its location. Check that the network drive is connected and you have read access, or copy the image to this computer and try again.\n"+path,ex);}
        StudioBackgroundService.validate(bytes);
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("This file cannot be decoded as a PNG or JPEG image.");
            var reader=readers.next();
            try {
                reader.setInput(input);BufferedImage image=reader.read(0);
                return new Loaded(bytes,image,path.getFileName().toString());
            } finally {reader.dispose();}
        }
    }
}
