package services;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Disposable replica images only; never scans the authoritative image mirror. */
final class StorefrontImageCache {
    static final long LIMIT = 512L * 1024 * 1024;
    private StorefrontImageCache() { }

    static synchronized byte[] read(Path root,String digest)throws IOException{
        Path target=target(root,digest);
        if(!Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS))return null;
        byte[] bytes;
        try(var input=Files.newInputStream(target)){
            bytes=input.readNBytes(BoundedImageBody.STOREFRONT_LIMIT+1);
        }
        if(bytes.length>BoundedImageBody.STOREFRONT_LIMIT||!digest.equals(hash(bytes))){Files.delete(target);return null;}
        Files.setLastModifiedTime(target,FileTime.fromMillis(System.currentTimeMillis()));
        return bytes;
    }

    static synchronized void write(Path root,String digest,byte[] bytes,long limit)throws IOException{
        Path target=target(root,digest);
        if(limit<1||bytes.length>limit||bytes.length>BoundedImageBody.STOREFRONT_LIMIT||!digest.equals(hash(bytes)))
            throw new IOException("Invalid replica image size or checksum.");
        Files.createDirectories(root);
        List<Entry> entries=new ArrayList<>();long total=0;
        try(var files=Files.list(root)){
            for(Path file:files.toList()){
                if(file.equals(target)||!file.getFileName().toString().matches("[a-f0-9]{64}")
                        ||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))continue;
                long size=Files.size(file);total+=size;entries.add(new Entry(file,size,Files.getLastModifiedTime(file).toMillis()));
            }
        }
        entries.sort(Comparator.comparingLong(Entry::used).thenComparing(e->e.path().toString()));
        for(Entry entry:entries){if(total+bytes.length<=limit)break;Files.delete(entry.path());total-=entry.size();}
        Path temporary=Files.createTempFile(root,".storefront-",".tmp");
        try{
            Files.write(temporary,bytes);
            try{Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
    }

    private static Path target(Path root,String digest)throws IOException{
        if(digest==null||!digest.matches("[a-f0-9]{64}")||Files.isSymbolicLink(root))throw new IOException("Invalid replica image cache path.");
        Path target=root.resolve(digest);
        if(Files.isSymbolicLink(target))throw new IOException("Replica image cache links are not supported.");
        return target;
    }
    static String hash(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private record Entry(Path path,long size,long used) { }
}
