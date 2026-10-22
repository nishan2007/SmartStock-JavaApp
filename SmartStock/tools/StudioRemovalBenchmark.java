package services;

import java.nio.file.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

/** Use a representative local photo; output stays in an operator-selected local folder. */
public final class StudioRemovalBenchmark {
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("Usage: StudioRemovalBenchmark <verified-best.onnx> <photo.png-or-jpg> <output-directory>");
        if(!StudioRemovalRuntime.enoughMemory(StudioRemovalRuntime.physicalMemory(),Runtime.getRuntime().maxMemory()-Runtime.getRuntime().totalMemory()+Runtime.getRuntime().freeMemory(),false))throw new IllegalStateException("Free at least 3 GB of RAM before benchmarking the full Best model. Do not benchmark under store memory pressure.");
        Path model=Path.of(args[0]),out=Path.of(args[2]);Files.createDirectories(out);byte[] source=Files.readAllBytes(Path.of(args[1]));StudioBackgroundService.validate(source);
        byte[] cpu=null,gpu=null;boolean[] gpuUsed={false};
        for(var mode:new StudioImageEnhancer.Mode[]{StudioImageEnhancer.Mode.CPU,StudioImageEnhancer.Mode.GPU}){
            for(int run=0;run<3;run++){
                long start=System.nanoTime();
                var progress=new StudioImageEnhancer.Progress(){public void update(String device,int done,int total,String note){if(device.equals("GPU (DirectML)")&&done==1&&note.equals("Background removed on GPU"))gpuUsed[0]=true;}};
                byte[] result=StudioRemovalRuntime.use(StudioQuality.BEST,mode,model,progress,p->p.removeBackgroundPng(source,false));
                System.out.printf(java.util.Locale.ROOT,"%s %s %.3f seconds%n",mode,run==0?"cold":"warm",(System.nanoTime()-start)/1e9);
                if(run==2){Files.write(out.resolve(mode.name().toLowerCase()+".png"),result);if(mode==StudioImageEnhancer.Mode.CPU)cpu=result;else gpu=result;}
            }
        }
        if(!gpuUsed[0])throw new IllegalStateException("GPU fell back to CPU. These are not GPU benchmark timings.");
        var a=ImageIO.read(new ByteArrayInputStream(cpu));var b=ImageIO.read(new ByteArrayInputStream(gpu));long sum=0;int max=0;
        for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++){int difference=Math.abs((a.getRGB(x,y)>>>24)-(b.getRGB(x,y)>>>24));sum+=difference;max=Math.max(max,difference);}
        System.out.printf(java.util.Locale.ROOT,"Alpha comparison: max=%d mean=%.5f (0–255 scale). Review fine hair and edges in both outputs.%n",max,sum/(double)(a.getWidth()*a.getHeight()));
    }
}
