package services;

import utils.ImageCacheManager;
import utils.ImageOptimizationHelper;
import java.io.File;
import java.util.Locale;

/** Optimizes customer photos/logos and stores them in the protected OneDrive-backed image workflow. */
public final class CustomerPhotoService {
    private static final long MAX_ORIGINAL_BYTES=12L*1024L*1024L,MAX_UPLOAD_BYTES=180L*1024L;
    private CustomerPhotoService(){}
    public static String uploadLocalPhotoIfNeeded(String pathOrUrl,String customerName)throws Exception{
        String value=pathOrUrl==null?"":pathOrUrl.trim();if(value.isBlank()||ImageCacheManager.isRemoteImageUrl(value))return value;
        File file=new File(value);if(!file.isFile())throw new IllegalArgumentException("The selected customer photo file was not found.");
        try(ImageOptimizationHelper.OptimizedImage image=ImageOptimizationHelper.optimizeForUpload(file,"customer-photo",900,900,.82f,MAX_ORIGINAL_BYTES,MAX_UPLOAD_BYTES,false)){
            String slug=sanitize(customerName),filename=StorageObjectNameBuilder.filename(image.filename(),"jpg",Long.toString(System.currentTimeMillis()),customerName,"customer-photo");
            String reference=LanApiClient.uploadCloudFile("customer files","customer photos/"+slug+"/"+filename,image.contentType(),java.nio.file.Files.readAllBytes(image.file().toPath()));
            ImageCacheManager.cacheUploadedImage(reference,image.file().toPath());return reference;
        }
    }
    private static String sanitize(String value){String x=value==null?"customer":value.trim().toLowerCase(Locale.ROOT);x=x.replaceAll("[^a-z0-9._-]","-").replaceAll("-+","-");return x.isBlank()?"customer":x;}
}
