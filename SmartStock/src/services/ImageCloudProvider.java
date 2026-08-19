package services;

import java.util.UUID;

/** Server-only binary storage boundary for managed SmartStock images. */
interface ImageCloudProvider {
    String name();
    boolean configured();
    RemoteObject upload(UUID assetId, String category, String sourcePath, String contentType, byte[] bytes)
            throws Exception;
    byte[] download(UUID assetId, String category, String sourcePath, String remoteItemId, String remotePath)
            throws Exception;
    void delete(UUID assetId, String category, String sourcePath, String remoteItemId, String remotePath)
            throws Exception;
    ProbeResult probe() throws Exception;

    record RemoteObject(String driveId, String itemId, String path, String eTag, long byteSize) { }
    record ProbeResult(boolean ready, String message) { }
}
