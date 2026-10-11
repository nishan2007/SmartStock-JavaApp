package services;

import ai.onnxruntime.*;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** CPU Real-ESRGAN: overlapping source tiles, native 4x RGB, independently resampled alpha. */
public final class StudioImageEnhancer implements AutoCloseable {
    private static final int PADDING = 24;
    public static final long MAX_OUTPUT_PIXELS = 24_000_000;
    private OrtEnvironment environment;
    private OrtSession session;
    private final Path model;
    interface GpuBackend extends AutoCloseable {
        FloatBuffer run(float[] rgb,int w,int h,java.util.function.BooleanSupplier cancelled)throws Exception;
        void close();
    }
    private GpuBackend gpu;
    interface GpuFactory {GpuBackend open(Path model,java.util.function.BooleanSupplier cancelled)throws Exception;}
    private final GpuFactory gpuFactory;
    public enum Mode { AUTO, CPU, GPU }
    public interface Progress {
        void update(String device,int completed,int total,String note);
        default boolean cancelled(){return false;}
    }
    private static final Progress SILENT=(device,completed,total,note)->{};
    private String device="CPU",note="";

    public StudioImageEnhancer() throws Exception { this(StudioEnhancementModel.current().resolve()); }
    public StudioImageEnhancer(Path model) throws Exception {
        this(model,StudioGpuProcess::new);
    }
    StudioImageEnhancer(Path model,GpuFactory factory)throws Exception {
        StudioEnhancementModel.verify(model);
        this.model=model;
        this.gpuFactory=factory;
    }
    private void cpu()throws OrtException {
        environment=OrtEnvironment.getEnvironment();
        try (var options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(cpuThreads(availableMemory(),Runtime.getRuntime().availableProcessors()));
            options.setInterOpNumThreads(1);
            if(availableMemory()<512L*1024*1024)options.setCPUArenaAllocator(false);
            session = environment.createSession(model.toString(), options);
        }
    }
    static long availableMemory(){
        var os=java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        long physical=os instanceof com.sun.management.OperatingSystemMXBean m?m.getFreeMemorySize():Long.MAX_VALUE;
        Runtime r=Runtime.getRuntime();return Math.min(physical,r.maxMemory()-r.totalMemory()+r.freeMemory());
    }
    static int cpuThreads(long bytes,int processors){return Math.max(1,Math.min(bytes<1024L*1024*1024?2:4,processors-2));}
    static int tileSize(long bytes){return bytes<512L*1024*1024?48:bytes<2L*1024*1024*1024?96:160;}
    public static void validateScale(int scale) {
        if (scale != 2 && scale != 4) throw new IllegalArgumentException("Choose 2× or 4× enhancement.");
    }
    public static void validateDimensions(int width, int height, int scale) {
        validateScale(scale);
        long sourcePixels = (long) width * height;
        if (width <= 0 || height <= 0 || sourcePixels > MAX_OUTPUT_PIXELS / (scale * scale))
            throw new IllegalArgumentException("The enhanced image would exceed 24 megapixels. Choose 2× or crop the original.");
        long pixels = sourcePixels * scale * scale;
        if (pixels * 24 > Runtime.getRuntime().maxMemory())
            throw new IllegalArgumentException("The server needs more memory for this output size. Choose 2× or crop the original.");
    }
    public BufferedImage enhance(BufferedImage source, int scale) throws Exception {
        return enhance(source,scale,Mode.AUTO,SILENT);
    }
    public BufferedImage enhance(BufferedImage source,int scale,Mode mode,Progress progress)throws Exception {
        try{return enhanceAttempt(source,scale,mode,progress,Integer.MAX_VALUE);}
        catch(OrtException ex){
            if(!memoryFailure(ex))throw ex;
            close();progress.update("CPU",0,0,"Reducing tile size after memory allocation failed");
            return enhanceAttempt(source,scale,Mode.CPU,progress,48);
        }
    }
    static boolean memoryFailure(Throwable error){
        for(Throwable cause=error;cause!=null;cause=cause.getCause()){
            String message=String.valueOf(cause.getMessage()).toLowerCase(java.util.Locale.ROOT);
            if(message.contains("bad_alloc")||message.contains("out of memory")||message.contains("failed to allocate")||message.contains("not enough memory"))return true;
        }
        return false;
    }
    private BufferedImage enhanceAttempt(BufferedImage source,int scale,Mode mode,Progress progress,int cap)throws Exception {
        validateDimensions(source.getWidth(), source.getHeight(), scale);
        if(progress.cancelled())throw new java.util.concurrent.CancellationException("Image processing cancelled");
        if(gpu!=null){gpu.close();gpu=null;}if(session!=null){session.close();session=null;}
        device="CPU";note="";
        if(mode==Mode.GPU&&!System.getProperty("os.name","").startsWith("Windows"))note="GPU acceleration is unavailable on this platform; using CPU";
        long memory=availableMemory();
        long outputBytes=(long)source.getWidth()*source.getHeight()*scale*scale*4;
        if(memory<outputBytes+64L*1024*1024)throw new IllegalArgumentException("Not enough available memory. Close unused apps or choose a smaller image.");
        int tile=Math.min(cap,tileSize(memory));
        if(mode!=Mode.CPU && System.getProperty("os.name","").startsWith("Windows")){
            progress.update("GPU",0,0,"Preparing GPU");
            try{gpu=gpuFactory.open(model,progress::cancelled);device="GPU (DirectML)";}
            catch(java.util.concurrent.CancellationException ex){throw ex;}
            catch(Exception|LinkageError ex){java.util.logging.Logger.getLogger(StudioImageEnhancer.class.getName()).log(java.util.logging.Level.WARNING,"GPU initialization failed; using CPU",ex);note="GPU unavailable; using CPU";}
        }
        if(gpu==null)cpu();
        int total=((source.getWidth()+tile-1)/tile)*((source.getHeight()+tile-1)/tile),completed=0;
        progress.update(device,0,total,note);
        BufferedImage output = new BufferedImage(source.getWidth() * scale, source.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
        long deadline = System.nanoTime() + Duration.ofMinutes(25).toNanos();
        for (int y = 0; y < source.getHeight(); y += tile) for (int x = 0; x < source.getWidth(); x += tile) {
            if(progress.cancelled())throw new java.util.concurrent.CancellationException("Image processing cancelled");
            if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline)
                throw new IllegalArgumentException("Enhancement took too long. Try a smaller image or crop.");
            int coreW = Math.min(tile, source.getWidth() - x), coreH = Math.min(tile, source.getHeight() - y);
            int left = Math.max(0, x - PADDING), top = Math.max(0, y - PADDING);
            int right = Math.min(source.getWidth(), x + coreW + PADDING), bottom = Math.min(source.getHeight(), y + coreH + PADDING);
            int w = right - left, h = bottom - top, plane = w * h;
            float[] rgb = new float[plane * 3];
            for (int ty = 0; ty < h; ty++) for (int tx = 0; tx < w; tx++) {
                int pixel = edgeColour(source, left + tx, top + ty), p = ty * w + tx;
                rgb[p] = ((pixel >>> 16) & 255) / 255f;
                rgb[p + plane] = ((pixel >>> 8) & 255) / 255f;
                rgb[p + 2 * plane] = (pixel & 255) / 255f;
            }
            {
                FloatBuffer values;
                if(gpu!=null){
                    try{values=gpu.run(rgb,w,h,progress::cancelled);}
                    catch(java.util.concurrent.CancellationException ex){throw ex;}
                    catch(Exception ex){java.util.logging.Logger.getLogger(StudioImageEnhancer.class.getName()).log(java.util.logging.Level.WARNING,"GPU tile failed; using CPU",ex);gpu.close();gpu=null;device="CPU";note="GPU failed; continued on CPU";cpu();progress.update(device,completed,total,note);values=runCpu(rgb,w,h);}
                }else values=runCpu(rgb,w,h);
                int ow = w * 4, op = ow * h * 4;
                int step = 4 / scale;
                for (int dy = 0; dy < coreH * scale; dy++) for (int dx = 0; dx < coreW * scale; dx++) {
                    int px = (x - left) * 4 + dx * step, py = (y - top) * 4 + dy * step;
                    int colour = 0;
                    for (int c = 0; c < 3; c++) {
                        float v = 0;
                        for (int sy = 0; sy < step; sy++) for (int sx = 0; sx < step; sx++) v += values.get(c * op + (py + sy) * ow + px + sx);
                        if (!Float.isFinite(v)) throw new IllegalStateException("The enhancement model returned invalid pixels.");
                        colour |= Math.max(0, Math.min(255, Math.round(v / (step * step) * 255))) << (16 - c * 8);
                    }
                    int ox = x * scale + dx, oy = y * scale + dy;
                    int alpha = alphaAt(source, (ox + .5) / scale - .5, (oy + .5) / scale - .5);
                    output.setRGB(ox, oy, alpha == 0 ? 0 : (alpha << 24) | colour);
                }
            }
            progress.update(device,++completed,total,note);
        }
        return output;
    }
    private FloatBuffer runCpu(float[] rgb,int w,int h)throws Exception {
        try(var input=OnnxTensor.createTensor(environment,FloatBuffer.wrap(rgb),new long[]{1,3,h,w});
            var result=session.run(Map.of("input",input))){
            var tensor=(OnnxTensor)result.get("output").orElseThrow();
            if(!java.util.Arrays.equals(tensor.getInfo().getShape(),new long[]{1,3,h*4L,w*4L}))throw new IllegalStateException("Invalid model output");
            FloatBuffer nativeValues=tensor.getFloatBuffer();float[] copy=new float[nativeValues.remaining()];nativeValues.get(copy);return FloatBuffer.wrap(copy);
        }
    }
    // Extend nearby visible RGB into transparent pixels without changing the transparency mask.
    static int edgeColour(BufferedImage image, int x, int y) {
        int pixel = image.getRGB(x, y);
        if ((pixel >>> 24) != 0) return pixel;
        for (int r = 1; r <= 8; r++) {
            for (int d = -r; d <= r; d++) {
                for (int side = 0; side < 4; side++) {
                    int px = side < 2 ? x + d : side == 2 ? x - r : x + r;
                    int py = side >= 2 ? y + d : side == 0 ? y - r : y + r;
                    if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()) {
                        int candidate = image.getRGB(px, py); if ((candidate >>> 24) != 0) return candidate;
                    }
                }
            }
        }
        return pixel;
    }
    static int alphaAt(BufferedImage image, double x, double y) {
        x = Math.max(0, Math.min(image.getWidth() - 1, x)); y = Math.max(0, Math.min(image.getHeight() - 1, y));
        int x0 = (int) x, y0 = (int) y, x1 = Math.min(x0 + 1, image.getWidth() - 1), y1 = Math.min(y0 + 1, image.getHeight() - 1);
        double fx = x - x0, fy = y - y0;
        double a = (image.getRGB(x0, y0) >>> 24) * (1 - fx) + (image.getRGB(x1, y0) >>> 24) * fx;
        double b = (image.getRGB(x0, y1) >>> 24) * (1 - fx) + (image.getRGB(x1, y1) >>> 24) * fx;
        return (int) Math.round(a * (1 - fy) + b * fy);
    }
    @Override public void close() throws OrtException { if(gpu!=null)gpu.close();if(session!=null)session.close(); }
}
