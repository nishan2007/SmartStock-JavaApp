package services;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

/** Offline product cutout using pinned IS-Net or BiRefNet models. Keep this off the EDT. */
public final class CatalogStudioImageProcessor implements AutoCloseable {
    public static final String MODEL_SHA256 = "60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a";
    public static final String BEST_MODEL_SHA256 = "58f621f00f5d756097615970a88a791584600dcf7c45b18a0a6267535a1ebd3c";
    private static final int MODEL_SIZE = 1024;
    private final OrtEnvironment environment;
    private OrtSession session;
    private final StudioQuality quality;
    private final Path model;
    private final StudioImageEnhancer.Mode mode;
    private final StudioImageEnhancer.GpuFactory gpuFactory;
    private StudioImageEnhancer.GpuBackend gpu;
    private boolean gpuAttempted;
    private String device="CPU";
    private StudioImageEnhancer.Progress progress=(d,c,t,n)->{};
    void progress(StudioImageEnhancer.Progress value){progress=value==null?(d,c,t,n)->{}:value;}

    public static Path packagedModel() throws Exception {
        return packagedModel(StudioQuality.FAST);
    }

    public static Path packagedModel(StudioQuality quality) throws Exception {
        return StudioModelService.current().resolve(quality);
    }

    public CatalogStudioImageProcessor() throws Exception {
        this(StudioQuality.FAST);
    }

    public CatalogStudioImageProcessor(Path model) throws Exception {
        this(model, StudioQuality.FAST);
    }

    public CatalogStudioImageProcessor(StudioQuality quality) throws Exception {
        this(packagedModel(quality), quality, true);
    }

    public CatalogStudioImageProcessor(Path model, StudioQuality quality) throws Exception {
        this(model, quality, false);
    }

    private CatalogStudioImageProcessor(Path model, StudioQuality quality, boolean managed) throws Exception {
        this(model,quality,managed,StudioImageEnhancer.Mode.CPU);
    }
    CatalogStudioImageProcessor(Path model,StudioQuality quality,boolean managed,StudioImageEnhancer.Mode mode)throws Exception{
        this(model,quality,managed,mode,(path,cancelled)->new StudioGpuProcess(path,cancelled,true));
    }
    CatalogStudioImageProcessor(Path model,StudioQuality quality,boolean managed,StudioImageEnhancer.Mode mode,StudioImageEnhancer.GpuFactory gpuFactory)throws Exception{
        this.quality = java.util.Objects.requireNonNull(quality);
        this.model=model;this.mode=mode;this.gpuFactory=gpuFactory;
        if (!Files.isRegularFile(model)) throw new ModelUnavailableException(quality == StudioQuality.BEST
                ? "Best quality is not installed. Ask an administrator to open Status > AI Models."
                : "The photo model is missing. Ask an administrator to open Status > AI Models.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var stream = Files.newInputStream(model)) {
            byte[] chunk = new byte[65536];
            for (int count; (count = stream.read(chunk)) != -1; ) digest.update(chunk, 0, count);
        }
        String hash = HexFormat.of().formatHex(digest.digest());
        String expectedHash = managed ? model.getFileName().toString().replace(".onnx", "")
                : quality == StudioQuality.BEST ? BEST_MODEL_SHA256 : MODEL_SHA256;
        if (!expectedHash.equals(hash))
            throw new ModelUnavailableException("The photo model failed its integrity check. Ask an administrator to repair it in Status > AI Models.");
        environment = OrtEnvironment.getEnvironment();
    }
    private void cpu()throws Exception{
        if(session!=null)return;
        long start=System.nanoTime();
        try (OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(StudioImageEnhancer.cpuThreads(StudioImageEnhancer.availableMemory(),Runtime.getRuntime().availableProcessors()));
            options.setInterOpNumThreads(1);
            session = environment.createSession(model.toString(), options);
        }
        System.out.printf(java.util.Locale.ROOT,"SmartStudio removal device=CPU sessionInitMs=%d%n",(System.nanoTime()-start)/1_000_000);
    }
    private void checkCancelled(){if(progress.cancelled()||Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("Image processing cancelled");}
    void warmUp()throws Exception{infer(new float[MODEL_SIZE*MODEL_SIZE*3]);}
    private float[] infer(float[] pixels)throws Exception{
        checkCancelled();
        if(mode!=StudioImageEnhancer.Mode.CPU&&!gpuAttempted&&System.getProperty("os.name","").startsWith("Windows")){
            gpuAttempted=true;
            try{long start=System.nanoTime();progress.update("GPU (DirectML)",0,1,"Loading background removal model");gpu=gpuFactory.open(model,progress::cancelled);System.out.printf(java.util.Locale.ROOT,"SmartStudio removal device=DirectML sessionInitMs=%d%n",(System.nanoTime()-start)/1_000_000);}
            catch(java.util.concurrent.CancellationException ex){gpuAttempted=false;throw ex;}
            catch(Exception|LinkageError ex){System.err.println("Background removal GPU unavailable; using CPU ("+ex.getClass().getSimpleName()+")");}
        }
        float[] mask=new float[MODEL_SIZE*MODEL_SIZE];
        if(gpu!=null){
            try{device="GPU (DirectML)";progress.update(device,0,1,"Removing background");gpu.run(pixels,MODEL_SIZE,MODEL_SIZE,progress::cancelled).get(mask);checkCancelled();progress.update(device,1,1,"Background removed on GPU");return mask;}
            catch(java.util.concurrent.CancellationException ex){gpu.close();gpu=null;gpuAttempted=false;throw ex;}
            catch(Exception|LinkageError ex){gpu.close();gpu=null;System.err.println("Background removal GPU failed; continuing on CPU ("+ex.getClass().getSimpleName()+")");}
        }
        checkCancelled();cpu();device="CPU";progress.update(device,0,1,"Removing background on CPU");
        String inputName=quality==StudioQuality.BEST?session.getInputNames().iterator().next():"input_image";
        String outputName=quality==StudioQuality.BEST?session.getOutputNames().iterator().next():"output_image";
        try(OnnxTensor tensor=OnnxTensor.createTensor(environment,FloatBuffer.wrap(pixels),new long[]{1,3,MODEL_SIZE,MODEL_SIZE});
            OrtSession.Result result=session.run(Map.of(inputName,tensor),Set.of(outputName))){((OnnxTensor)result.get(outputName).orElseThrow()).getFloatBuffer().get(mask);}
        checkCancelled();progress.update(device,1,1,"Background removed on CPU");return mask;
    }

    private BufferedImage cutout(byte[] input, boolean cleanEdges) throws Exception {
        StudioBackgroundService.validate(input);
        BufferedImage original = ImageIO.read(new ByteArrayInputStream(input));
        if (original == null || original.getWidth() < 32 || original.getHeight() < 32)
            throw new IllegalArgumentException("The original product photo is invalid or too small.");
        BufferedImage scaled = new BufferedImage(MODEL_SIZE, MODEL_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(original, 0, 0, MODEL_SIZE, MODEL_SIZE, null);
        } finally { g.dispose(); }
        int plane = MODEL_SIZE * MODEL_SIZE;
        float[] pixels = new float[plane * 3];
        int max = 1;
        for (int y = 0; y < MODEL_SIZE; y++) for (int x = 0; x < MODEL_SIZE; x++) {
            int color = scaled.getRGB(x, y);
            max = Math.max(max, Math.max((color >> 16) & 255, Math.max((color >> 8) & 255, color & 255)));
        }
        for (int y = 0; y < MODEL_SIZE; y++) for (int x = 0; x < MODEL_SIZE; x++) {
            int color = scaled.getRGB(x, y), index = y * MODEL_SIZE + x;
            pixels[index] = ((color >> 16) & 255) / (float) max - .5f;
            pixels[plane + index] = ((color >> 8) & 255) / (float) max - .5f;
            pixels[2 * plane + index] = (color & 255) / (float) max - .5f;
            if (quality == StudioQuality.BEST) {
                // BiRefNet's exported model requires ImageNet channel normalization.
                pixels[index] = (((color >> 16) & 255) / 255f - .485f) / .229f;
                pixels[plane + index] = (((color >> 8) & 255) / 255f - .456f) / .224f;
                pixels[2 * plane + index] = ((color & 255) / 255f - .406f) / .225f;
            }
        }
        long inferenceStart=System.nanoTime();float[] mask=infer(pixels);
        System.out.printf(java.util.Locale.ROOT,"SmartStudio removal quality=%s device=%s maskMs=%d%n",quality,device,(System.nanoTime()-inferenceStart)/1_000_000);
        if (quality == StudioQuality.BEST)
            for (int i = 0; i < mask.length; i++) mask[i] = (float) (1.0 / (1 + Math.exp(-mask[i])));
        float min = Float.POSITIVE_INFINITY, maxValue = Float.NEGATIVE_INFINITY;
        for (float value : mask) { min = Math.min(min, value); maxValue = Math.max(maxValue, value); }
        if (maxValue - min < 1e-5f) throw new IllegalStateException("The model found no usable product outline.");
        BufferedImage alpha = new BufferedImage(MODEL_SIZE, MODEL_SIZE, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < MODEL_SIZE; y++) for (int x = 0; x < MODEL_SIZE; x++) {
            float value = (mask[y * MODEL_SIZE + x] - min) / (maxValue - min);
            int gray = Math.min(255, Math.max(0, Math.round(value * 255)));
            alpha.getRaster().setSample(x, y, 0, gray);
        }
        BufferedImage resizedMask = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        g = resizedMask.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(alpha, 0, 0, original.getWidth(), original.getHeight(), null);
        } finally { g.dispose(); }
        return StudioEdgeRefiner.apply(original, resizedMask, cleanEdges);
    }

    public byte[] removeBackgroundPng(byte[] input) throws Exception {
        return removeBackgroundPng(input, false);
    }

    public byte[] removeBackgroundPng(byte[] input, boolean cleanEdges) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BufferedImage image=cutout(input,cleanEdges);checkCancelled();progress.update(device,0,0,"Saving PNG");
        ImageIO.write(image, "png", output);checkCancelled();progress.update(device,1,1,"Background removed");
        return output.toByteArray();
    }

    public byte[] makeStudioJpeg(byte[] input) throws Exception {
        BufferedImage original = cutout(input, false);
        int left = original.getWidth(), top = original.getHeight(), right = -1, bottom = -1;
        for (int y = 0; y < original.getHeight(); y++) for (int x = 0; x < original.getWidth(); x++) {
            if ((original.getRGB(x, y) >>> 24) > 20) {
                left = Math.min(left, x); top = Math.min(top, y);
                right = Math.max(right, x); bottom = Math.max(bottom, y);
            }
        }
        if (right < left || bottom < top) throw new IllegalStateException("The model found no product.");
        double scale = Math.min(920.0 / (right - left + 1), 920.0 / (bottom - top + 1));
        int width = Math.max(1, (int) Math.round((right - left + 1) * scale));
        int height = Math.max(1, (int) Math.round((bottom - top + 1) * scale));
        int startX = (1200 - width) / 2, startY = (1110 - height) / 2;
        BufferedImage canvas = new BufferedImage(1200, 1200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(Color.WHITE);g.fillRect(0, 0, 1200, 1200);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setColor(new Color(232, 232, 232));
            g.fillOval(startX + width / 10, startY + height - 5, Math.max(1, width * 8 / 10), 28);
            BufferedImage cutout = new BufferedImage(right - left + 1, bottom - top + 1, BufferedImage.TYPE_INT_ARGB);
            for (int y = top; y <= bottom; y++) for (int x = left; x <= right; x++) {
                int a = original.getRGB(x, y) >>> 24;
                cutout.setRGB(x - left, y - top, (a << 24) | (original.getRGB(x, y) & 0xffffff));
            }
            g.drawImage(cutout, startX, startY, width, height, null);
        } finally { g.dispose(); }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam settings = writer.getDefaultWriteParam();
            settings.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            settings.setCompressionQuality(.84f);
            writer.write(null, new IIOImage(canvas, null, null), settings);
        } finally { writer.dispose(); }
        return output.toByteArray();
    }

    @Override public void close() throws Exception {if(gpu!=null){gpu.close();gpu=null;}if(session!=null){session.close();session=null;}}

    public static final class ModelUnavailableException extends Exception {
        public ModelUnavailableException(String message) { super(message); }
    }
}
