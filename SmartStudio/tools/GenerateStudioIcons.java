import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Package the exact SVG renders from render-studio-icon.cjs; never redraw the approved artwork. */
public final class GenerateStudioIcons {
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args.length>0?args[0]:"src/main/resources/gy/deckers/studio/icons");
        int[] sizes={16,24,32,48,64,128,256};List<byte[]> images=new ArrayList<>();
        for(int size:sizes){
            Path file=output.resolve("SmartStudio-"+size+".png");
            BufferedImage image=ImageIO.read(file.toFile());
            if(image==null||image.getWidth()!=size||image.getHeight()!=size||!image.getColorModel().hasAlpha())throw new IOException("Render the approved SVG first: "+file);
            images.add(Files.readAllBytes(file));
        }
        int offset=6+16*sizes.length;ByteBuffer directory=ByteBuffer.allocate(offset).order(ByteOrder.LITTLE_ENDIAN);
        directory.putShort((short)0).putShort((short)1).putShort((short)sizes.length);
        for(int i=0;i<sizes.length;i++){
            directory.put((byte)sizes[i]).put((byte)sizes[i]).put((byte)0).put((byte)0);
            directory.putShort((short)1).putShort((short)32).putInt(images.get(i).length).putInt(offset);offset+=images.get(i).length;
        }
        try(OutputStream stream=Files.newOutputStream(output.resolve("SmartStudio.ico"))){stream.write(directory.array());for(byte[] image:images)stream.write(image);}
        String[] types={"icp4","icp5","icp6","ic07","ic08","ic09","ic10"};int[] macSizes={16,32,64,128,256,512,1024};
        ByteArrayOutputStream chunks=new ByteArrayOutputStream();
        try(DataOutputStream stream=new DataOutputStream(chunks)){
            for(int i=0;i<types.length;i++){byte[] png=Files.readAllBytes(output.resolve("SmartStudio-"+macSizes[i]+".png"));stream.writeBytes(types[i]);stream.writeInt(png.length+8);stream.write(png);}
        }
        try(DataOutputStream stream=new DataOutputStream(Files.newOutputStream(output.resolve("SmartStudio.icns")))){stream.writeBytes("icns");stream.writeInt(chunks.size()+8);chunks.writeTo(stream);}
        System.out.println("Generated seven-size Windows ICO and macOS ICNS from approved SVG renders.");
    }
}
