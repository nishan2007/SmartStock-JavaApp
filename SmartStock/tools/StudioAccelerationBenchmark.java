package services;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Run with the packaged classpath; writes synthetic CPU/GPU comparison images only. */
public final class StudioAccelerationBenchmark {
    public static void main(String[] args)throws Exception {
        Path model=Path.of(args[0]),folder=Path.of(args[1]);java.nio.file.Files.createDirectories(folder);
        int size=args.length>2?Integer.parseInt(args[2]):192;
        BufferedImage source=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++)source.setRGB(x,y,((x<8||y<8)?0:255)<<24|(x*255/size)<<16|(y*255/size)<<8|127);
        BufferedImage cpu,gpu;
        long begin=System.nanoTime();
        try(var enhancer=new StudioImageEnhancer(model)){cpu=enhancer.enhance(source,2,StudioImageEnhancer.Mode.CPU,(d,c,t,n)->{});}
        double cpuSeconds=(System.nanoTime()-begin)/1e9;begin=System.nanoTime();
        boolean[] actualGpu={false};
        try(var enhancer=new StudioImageEnhancer(model)){gpu=enhancer.enhance(source,2,StudioImageEnhancer.Mode.GPU,(d,c,t,n)->{System.out.println(d+" "+c+"/"+t+" "+n);if(t>0&&c==t)actualGpu[0]=d.equals("GPU (DirectML)");});}
        double gpuSeconds=(System.nanoTime()-begin)/1e9;
        int max=0;long sum=0;for(int y=0;y<cpu.getHeight();y++)for(int x=0;x<cpu.getWidth();x++){
            int a=cpu.getRGB(x,y),b=gpu.getRGB(x,y);if((a>>>24)!=(b>>>24))throw new AssertionError("Alpha differs");
            for(int shift:new int[]{0,8,16}){int diff=Math.abs(((a>>>shift)&255)-((b>>>shift)&255));max=Math.max(max,diff);sum+=diff;}
        }
        if(!actualGpu[0])throw new AssertionError("GPU did not finish all tiles; CPU fallback timings are not a GPU benchmark");
        ImageIO.write(cpu,"png",folder.resolve("cpu.png").toFile());ImageIO.write(gpu,"png",folder.resolve("gpu.png").toFile());
        System.out.printf(java.util.Locale.ROOT,"CPU %.3fs GPU %.3fs max channel difference %d mean %.4f%n",cpuSeconds,gpuSeconds,max,sum/(double)(cpu.getWidth()*cpu.getHeight()*3));
        if(max>4)throw new AssertionError("GPU quality differs excessively");
    }
}
