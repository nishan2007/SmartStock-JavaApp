package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

/** Tiny self-authored ONNX graph exercises the 1024-square mask contract without model weights. */
class StudioRemovalProtocolTest {
    @TempDir Path folder;
    private byte[] integer(int field,long n){var out=new ByteArrayOutputStream();varint(out,field*8L);varint(out,n);return out.toByteArray();}
    private void varint(ByteArrayOutputStream out,long value){while(value>127){out.write((int)(value&127)|128);value>>>=7;}out.write((int)value);}
    private byte[] message(int field,byte[] bytes){var out=new ByteArrayOutputStream();varint(out,field*8L+2);varint(out,bytes.length);out.writeBytes(bytes);return out.toByteArray();}
    private byte[] text(int field,String value){return message(field,value.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private byte[] join(byte[]...chunks){var out=new ByteArrayOutputStream();for(var chunk:chunks)out.writeBytes(chunk);return out.toByteArray();}
    private byte[] info(String name,int channels){byte[] dims=join(message(1,integer(1,1)),message(1,integer(1,channels)),message(1,integer(1,1024)),message(1,integer(1,1024)));return join(text(1,name),message(2,message(1,join(integer(1,1),message(2,dims)))));}
    private Path model()throws Exception{
        byte[] axes=join(text(1,"axes"),integer(8,1),integer(20,7)),keep=join(text(1,"keepdims"),integer(3,1),integer(20,2));
        byte[] node=join(text(1,"input"),text(2,"mask"),text(4,"ReduceMean"),message(5,axes),message(5,keep));
        byte[] graph=join(message(1,node),text(2,"test-mean-mask"),message(11,info("input",3)),message(12,info("mask",1)));
        byte[] data=join(integer(1,8),message(7,graph),message(8,integer(2,13)));
        String hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(data));Path path=folder.resolve(hash+".onnx");Files.write(path,data);return path;
    }
    private byte[] image()throws Exception{
        var image=new BufferedImage(64,96,BufferedImage.TYPE_INT_RGB);for(int y=0;y<96;y++)for(int x=0;x<64;x++)image.setRGB(x,y,((x*255/63)<<16)|((y*255/95)<<8)|100);
        var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
    @Test void cpuMaskProtocolPreservesResolutionColoursAndReusesSession()throws Exception{
        byte[] source=image();try(var processor=new CatalogStudioImageProcessor(model(),StudioQuality.BEST,true,StudioImageEnhancer.Mode.CPU)){
            byte[] first=processor.removeBackgroundPng(source,false),second=processor.removeBackgroundPng(source,false);assertArrayEquals(first,second);
            var original=ImageIO.read(new ByteArrayInputStream(source));var output=ImageIO.read(new ByteArrayInputStream(first));assertEquals(64,output.getWidth());assertEquals(96,output.getHeight());
            for(int y=0;y<96;y++)for(int x=0;x<64;x++)assertEquals(original.getRGB(x,y)&0xffffff,output.getRGB(x,y)&0xffffff);
        }
    }
    @Test void gpuMaskProtocolMatchesCpuWithActualDirectMlWhenRequested()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("smartstock.test.removalGpu")&&System.getProperty("os.name").startsWith("Windows"));
        Path path=model();byte[] source=image(),cpu,gpu;boolean[] used={false};
        try(var processor=new CatalogStudioImageProcessor(path,StudioQuality.BEST,true,StudioImageEnhancer.Mode.CPU)){cpu=processor.removeBackgroundPng(source,false);}
        try(var processor=new CatalogStudioImageProcessor(path,StudioQuality.BEST,true,StudioImageEnhancer.Mode.GPU)){
            processor.progress((device,done,total,note)->{if(device.equals("GPU (DirectML)")&&done==1&&note.equals("Background removed on GPU"))used[0]=true;});gpu=processor.removeBackgroundPng(source,false);
        }
        assertTrue(used[0],"DirectML must actually execute; a fallback is not GPU verification");
        var a=ImageIO.read(new ByteArrayInputStream(cpu));var b=ImageIO.read(new ByteArrayInputStream(gpu));for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)assertTrue(Math.abs((a.getRGB(x,y)>>>24)-(b.getRGB(x,y)>>>24))<=1);
    }
    @Test void failedGpuFallsBackToCpuWithoutChangingTheMask()throws Exception{
        Path path=model();byte[] source=image(),reference;boolean[] closed={false};
        try(var processor=new CatalogStudioImageProcessor(path,StudioQuality.BEST,true,StudioImageEnhancer.Mode.CPU)){reference=processor.removeBackgroundPng(source,false);}
        var backend=new StudioImageEnhancer.GpuBackend(){
            public java.nio.FloatBuffer run(float[] rgb,int w,int h,java.util.function.BooleanSupplier cancelled)throws Exception{throw new EOFException("simulated GPU crash");}
            public void close(){closed[0]=true;}
        };
        try(var processor=new CatalogStudioImageProcessor(path,StudioQuality.BEST,true,StudioImageEnhancer.Mode.GPU,(p,c)->backend)){
            assertArrayEquals(reference,processor.removeBackgroundPng(source,false));assertArrayEquals(reference,processor.removeBackgroundPng(source,false));
        }
        if(System.getProperty("os.name").startsWith("Windows"))assertTrue(closed[0]);
    }
    @Test void pinnedBestModelRunsOnActualDirectMlWhenRequested()throws Exception{
        String installed=System.getProperty("smartstock.test.bestRemovalModel");
        org.junit.jupiter.api.Assumptions.assumeTrue(Boolean.getBoolean("smartstock.test.removalGpu")
                &&installed!=null&&System.getProperty("os.name").startsWith("Windows"));
        Path path=Path.of(installed);var expected=StudioModelCatalogue.bundled().entry(StudioQuality.BEST);
        assertEquals(expected.sizeBytes(),Files.size(path));
        try(var input=Files.newInputStream(path)){
            var digest=java.security.MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)digest.update(buffer,0,count);
            assertEquals(expected.sha256(),java.util.HexFormat.of().formatHex(digest.digest()));
        }
        // A tiny graph cannot detect BiRefNet's runtime-loading regression or
        // integrated-GPU allocation failures. This opt-in test never accepts fallback.
        try(var gpu=new StudioGpuProcess(path,()->false,true)){
            var mask=gpu.run(new float[1024*1024*3],1024,1024,()->false);
            assertEquals(1024*1024,mask.remaining());
            while(mask.hasRemaining())assertTrue(Float.isFinite(mask.get()));
        }
    }
    @Test void cancellationDoesNotFallBackIntoAnotherInference()throws Exception{
        Path path=model();boolean[] closed={false};
        var backend=new StudioImageEnhancer.GpuBackend(){
            public java.nio.FloatBuffer run(float[] rgb,int w,int h,java.util.function.BooleanSupplier cancelled){throw new java.util.concurrent.CancellationException("cancelled");}
            public void close(){closed[0]=true;}
        };
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        try(var processor=new CatalogStudioImageProcessor(path,StudioQuality.BEST,true,StudioImageEnhancer.Mode.GPU,(p,c)->backend)){
            assertThrows(java.util.concurrent.CancellationException.class,()->processor.removeBackgroundPng(image(),false));assertTrue(closed[0]);
        }
    }
}
