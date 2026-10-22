package services;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import javax.imageio.ImageIO;

/** Shared local studio processing; both clients use the same bounded worker. */
public final class StudioBackgroundService {
    private static final Semaphore WORKER = new Semaphore(1);
    private static volatile boolean prewarmed;
    private StudioBackgroundService() { }

    public static void validate(byte[] bytes) throws Exception {
        if (bytes.length == 0 || bytes.length > 6 * 1024 * 1024)
            throw new IllegalArgumentException("Choose a PNG or JPEG image of at most 6 MB.");
        try (var input = new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalArgumentException("Choose a valid PNG or JPEG image.");
            var reader = readers.next();
            try {
                reader.setInput(input);
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("png") && !format.equalsIgnoreCase("jpeg"))
                    throw new IllegalArgumentException("Choose a PNG or JPEG image.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 32 || height < 32 || (long) width * height > 12_000_000)
                    throw new IllegalArgumentException("Choose an image at least 32 pixels wide and high, up to 12 megapixels.");
            } finally { reader.dispose(); }
        }
    }

    public static byte[] remove(byte[] bytes) throws Exception {
        return remove(bytes, StudioQuality.FAST, false);
    }

    public static byte[] remove(byte[] bytes, StudioQuality quality, boolean cleanEdges) throws Exception {
        return process(bytes, quality, cleanEdges, false);
    }

    public static byte[] reviewJpeg(byte[] bytes, StudioQuality quality) throws Exception {
        return process(bytes, quality, false, true);
    }

    /** One worker covers the complete remove-then-enhance pipeline. Scale 1 preserves legacy removal. */
    public static byte[] transform(byte[] bytes, StudioQuality quality, boolean cleanEdges,
                                   boolean removeBackground, int scale) throws Exception {
        return transform(bytes, quality, cleanEdges, removeBackground, scale, (java.nio.file.Path)null, (java.nio.file.Path)null);
    }

    static byte[] transform(byte[] bytes, StudioQuality quality, boolean cleanEdges,
                            boolean removeBackground, int scale, java.nio.file.Path removalModel,
                            java.nio.file.Path enhancementModel) throws Exception {
        return transform(bytes,quality,cleanEdges,removeBackground,scale,removalModel,enhancementModel,StudioImageEnhancer.Mode.AUTO,null);
    }
    public static byte[] transform(byte[] bytes,StudioQuality quality,boolean cleanEdges,boolean removeBackground,int scale,
                                  StudioImageEnhancer.Mode mode,StudioImageEnhancer.Progress progress)throws Exception {
        return transform(bytes,quality,cleanEdges,removeBackground,scale,null,null,mode,progress);
    }
    private static byte[] transform(byte[] bytes,StudioQuality quality,boolean cleanEdges,boolean removeBackground,int scale,
                                   java.nio.file.Path removalModel,java.nio.file.Path enhancementModel,
                                   StudioImageEnhancer.Mode mode,StudioImageEnhancer.Progress progress)throws Exception {
        if (scale == 1 && removeBackground) return process(bytes,quality,cleanEdges,false,mode,progress,removalModel);
        StudioImageEnhancer.validateScale(scale);
        validate(bytes);
        if (!WORKER.tryAcquire()) throw new BusyException();
        try {
            var original = ImageIO.read(new ByteArrayInputStream(bytes));
            StudioImageEnhancer.validateDimensions(original.getWidth(), original.getHeight(), scale);
            // Check enhancement availability before spending time removing a background.
            var model = enhancementModel == null ? StudioEnhancementModel.current().resolve() : enhancementModel;
            StudioEnhancementModel.verify(model);
            if (removeBackground) {
                if(progress!=null){if(progress.cancelled())throw new java.util.concurrent.CancellationException("Image processing cancelled");progress.update("CPU",0,0,"Removing background");}
                byte[] cutout=StudioRemovalRuntime.use(quality,mode,removalModel,progress,remover->remover.removeBackgroundPng(bytes,cleanEdges));
                original=ImageIO.read(new ByteArrayInputStream(cutout));
            }
            try (var enhancer = new StudioImageEnhancer(model)) {
                var output = new java.io.ByteArrayOutputStream();
                var enhanced=enhancer.enhance(original,scale,mode,progress==null?(device,done,total,note)->{}:progress);
                if(progress!=null){if(progress.cancelled())throw new java.util.concurrent.CancellationException("Image processing cancelled");progress.update("",0,0,"Saving PNG");}
                ImageIO.write(enhanced, "png", output);
                return output.toByteArray();
            }
        } finally { WORKER.release(); }
    }

    private static byte[] process(byte[] bytes, StudioQuality quality, boolean cleanEdges, boolean review) throws Exception {
        return process(bytes,quality,cleanEdges,review,StudioImageEnhancer.Mode.AUTO,null);
    }
    private static byte[] process(byte[] bytes,StudioQuality quality,boolean cleanEdges,boolean review,StudioImageEnhancer.Mode mode,StudioImageEnhancer.Progress progress)throws Exception{
        return process(bytes,quality,cleanEdges,review,mode,progress,null);
    }
    private static byte[] process(byte[] bytes,StudioQuality quality,boolean cleanEdges,boolean review,StudioImageEnhancer.Mode mode,StudioImageEnhancer.Progress progress,java.nio.file.Path model)throws Exception{
        validate(bytes);
        if (!WORKER.tryAcquire()) throw new BusyException();
        try {
            byte[] result=StudioRemovalRuntime.use(quality,mode,model,progress,processor->review?processor.makeStudioJpeg(bytes):processor.removeBackgroundPng(bytes,cleanEdges));
            return result;
        } finally { WORKER.release(); }
    }

    static byte[] prepareQueuedPhoto(byte[] bytes)throws Exception{
        // No waiting behind an interactive operation, and no cold model loads under RAM pressure.
        if(!StudioRemovalRuntime.canPrepare())throw new BusyException();
        return process(bytes,StudioQuality.BEST,false,false,StudioImageEnhancer.Mode.AUTO,null);
    }
    static void prewarmBest()throws Exception{
        if(prewarmed)return;
        if(StudioRemovalRuntime.hasSession()){prewarmed=true;return;}
        if(!StudioRemovalRuntime.canPrepare()||!StudioModelService.current().available(StudioQuality.BEST)||!WORKER.tryAcquire())return;
        try{StudioRemovalRuntime.use(StudioQuality.BEST,StudioImageEnhancer.Mode.AUTO,null,null,processor->{processor.warmUp();return null;});prewarmed=true;}
        finally{WORKER.release();}
    }

    public static final class BusyException extends Exception {
        public BusyException() { super("Another photo is being processed. Try again shortly."); }
    }

    public static Map<String,Object> branding(int locationId) throws Exception {
        var info = managers.ServerCompanyCustomizationRepository.loadReceiptSettingsForLocation(locationId);
        Map<String,Object> brand = new LinkedHashMap<>();
        brand.put("name", info.companyName()); brand.put("motto", info.mottoLine1());
        brand.put("phone", info.phoneLine1()); brand.put("email", info.emailLine1());
        brand.put("studioBestQualityAvailable", StudioModelService.current().available(StudioQuality.BEST));
        brand.put("studioFastQualityAvailable", StudioModelService.current().available(StudioQuality.FAST));
        brand.put("studioEnhancementAvailable", StudioEnhancementModel.current().available());
        brand.put("studioProgressAvailable",true);
        if (!info.logoPath().isBlank()) {
            try {
                var logo = ServerImageAssetService.load(info.logoPath());
                if (logo.bytes().length <= 2*1024*1024 && (logo.contentType().equals("image/png") || logo.contentType().equals("image/jpeg")))
                    brand.put("logo", "data:" + logo.contentType() + ";base64," + Base64.getEncoder().encodeToString(logo.bytes()));
            } catch (Exception ignored) { /* Text remains available if the logo cannot load. */ }
        }
        return brand;
    }
}
