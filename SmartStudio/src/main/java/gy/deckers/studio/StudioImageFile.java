package gy.deckers.studio;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;

/** Streams network files into bounded local memory; does not require remote size/attribute access. */
final class StudioImageFile {
    record Loaded(byte[] bytes,BufferedImage image,String name,int sourceWidth,int sourceHeight) {
        Loaded(byte[] bytes,BufferedImage image,String name){this(bytes,image,name,image.getWidth(),image.getHeight());}
    }
    static final int PHOTO_MAX_BYTES=20*1024*1024;
    static final int ARTWORK_MAX_BYTES=100*1024*1024;
    static Loaded loadArtwork(Path path)throws Exception {
        byte[] bytes;
        try(InputStream stream=Files.newInputStream(path)){bytes=stream.readNBytes(ARTWORK_MAX_BYTES+1);}
        catch(IOException ex){throw new IOException("Cannot open this artwork. Check the network drive and file read access.\n"+path,ex);}
        return artwork(bytes,path.getFileName().toString());
    }
    static Loaded artwork(byte[] bytes,String name)throws IOException {
        if(bytes.length==0||bytes.length>ARTWORK_MAX_BYTES)throw new IOException("Banner artwork supports PNG/JPEG files up to 100 MiB. The complete local banner template can contain up to 5 GiB of assets and design data.");
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Choose a valid PNG or JPEG image.");
            var reader=readers.next();try{
                reader.setInput(input);String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("jpeg"))throw new IOException("Choose a PNG or JPEG image.");
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||(long)w*h>100_000_000)throw new IOException("Banner artwork supports up to 100 megapixels.");
                var param=reader.getDefaultReadParam();int sample=Math.max(1,(int)Math.ceil(Math.max(w,h)/2048.));param.setSourceSubsampling(sample,sample,0,0);
                return new Loaded(bytes,reader.read(0,param),name,w,h);
            }finally{reader.dispose();}
        }
    }
    static void validatePhoto(byte[] bytes)throws IOException {validatePhoto(bytes,false);}
    private static void validatePhoto(byte[] bytes,boolean largePhoto)throws IOException {
        if(bytes.length==0||bytes.length>PHOTO_MAX_BYTES)
            throw new IOException("Choose a PNG or JPG/JPEG image of at most 20 MB.");
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("Choose a valid PNG or JPG/JPEG image.");
            var reader=readers.next();try{
                reader.setInput(input);String format=reader.getFormatName();
                if(!format.equalsIgnoreCase("png")&&!format.equalsIgnoreCase("jpeg"))
                    throw new IOException("Choose a PNG or JPG/JPEG image.");
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<32||height<32||(long)width*height>(largePhoto?100_000_000:12_000_000))
                    throw new IOException(largePhoto?"Choose an image at least 32 pixels wide and high, up to 100 megapixels.":"Choose an image at least 32 pixels wide and high, up to 12 megapixels.");
            }finally{reader.dispose();}
        }
    }
    static Loaded load(Path path)throws Exception {return load(path,false);}
    /** Decode large camera photos with bounded sampling before opening the framing editor. */
    static Loaded loadPhoto(Path path)throws Exception {return load(path,true);}
    private static Loaded load(Path path,boolean largePhoto)throws Exception {
        byte[] bytes;
        try(InputStream stream=Files.newInputStream(path)) {bytes=stream.readNBytes(PHOTO_MAX_BYTES+1);}
        catch(IOException ex) {throw new IOException("Cannot open this image from its location. Check that the network drive is connected and you have read access, or copy the image to this computer and try again.\n"+path,ex);}
        validatePhoto(bytes,largePhoto);
        try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("This file cannot be decoded as a PNG or JPEG image.");
            var reader=readers.next();
            try {
                reader.setInput(input);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                var param=reader.getDefaultReadParam();
                int sample=1;
                if(largePhoto)while(((width+sample-1L)/sample)*((height+sample-1L)/sample)>12_000_000)sample++;
                param.setSourceSubsampling(sample,sample,0,0);
                BufferedImage image=reader.read(0,param);
                return new Loaded(bytes,image,path.getFileName().toString(),width,height);
            } finally {reader.dispose();}
        }
    }
}
