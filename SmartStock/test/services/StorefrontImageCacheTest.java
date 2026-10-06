package services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import static org.junit.jupiter.api.Assertions.*;

class StorefrontImageCacheTest {
    @TempDir Path root;
    @Test void evictsLeastRecentlyUsedReplicaAndPreservesOtherFiles()throws Exception{
        byte[] first={1,2,3,4},second={5,6,7,8},third={9,10,11,12};
        String a=StorefrontImageCache.hash(first),b=StorefrontImageCache.hash(second),c=StorefrontImageCache.hash(third);
        Files.writeString(root.resolve("unrelated.txt"),"leave this alone");
        Files.createDirectory(root.resolve("a".repeat(64)));
        StorefrontImageCache.write(root,a,first,8);StorefrontImageCache.write(root,b,second,8);
        Files.setLastModifiedTime(root.resolve(a),FileTime.fromMillis(1000));
        Files.setLastModifiedTime(root.resolve(b),FileTime.fromMillis(2000));
        assertArrayEquals(first,StorefrontImageCache.read(root,a));
        StorefrontImageCache.write(root,c,third,8);
        assertTrue(Files.exists(root.resolve(a)));assertFalse(Files.exists(root.resolve(b)));
        assertArrayEquals(third,StorefrontImageCache.read(root,c));
        assertEquals("leave this alone",Files.readString(root.resolve("unrelated.txt")));
        assertTrue(Files.isDirectory(root.resolve("a".repeat(64))));
        // The evicted image can be downloaded and cached again within the same budget.
        StorefrontImageCache.write(root,b,second,8);assertArrayEquals(second,StorefrontImageCache.read(root,b));
    }
    @Test void corruptionAndOversizedCacheFilesBecomeMisses()throws Exception{
        byte[] bytes={1,2};String digest=StorefrontImageCache.hash(bytes);
        Files.write(root.resolve(digest),new byte[]{9});assertNull(StorefrontImageCache.read(root,digest));
        assertFalse(Files.exists(root.resolve(digest)));
        try(var output=Files.newOutputStream(root.resolve(digest))){output.write(new byte[BoundedImageBody.STOREFRONT_LIMIT+1]);}
        assertNull(StorefrontImageCache.read(root,digest));assertFalse(Files.exists(root.resolve(digest)));
    }
    @Test void invalidWritesCannotEvictValidImages()throws Exception{
        byte[] bytes={1,2};String digest=StorefrontImageCache.hash(bytes);
        StorefrontImageCache.write(root,digest,bytes,2);
        assertThrows(java.io.IOException.class,()->StorefrontImageCache.write(root,"b".repeat(64),bytes,2));
        assertThrows(java.io.IOException.class,()->StorefrontImageCache.write(root,digest,bytes,1));
        assertThrows(java.io.IOException.class,()->StorefrontImageCache.read(root,"../escape"));
        assertArrayEquals(bytes,StorefrontImageCache.read(root,digest));
    }
}
