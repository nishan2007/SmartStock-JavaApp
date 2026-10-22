package publishing;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Local ZIP64 templates. Asset bytes never accumulate in a project-sized byte array. */
public final class PublicationLargeFiles {
    public static final long MAX_BYTES=5L*1024*1024*1024;
    public static final int MAX_IMAGE_BYTES=100*1024*1024;
    public static final int MAX_JSON_BYTES=16*1024*1024;
    public static Path cache(){return Path.of(System.getProperty("user.home"),"banner-asset-cache");}
    public static Path store(InputStream input,Path cache)throws IOException {
        return store(input,cache,MAX_IMAGE_BYTES);
    }
    private static Path store(InputStream input,Path cache,long limit)throws IOException {
        Files.createDirectories(cache);Path partial=Files.createTempFile(cache,"asset-",".partial");
        MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException ex){throw new IOException(ex);}
        try {
            long size=0;byte[] buffer=new byte[1024*1024];
            try(OutputStream output=Files.newOutputStream(partial)){
                int count;while((count=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Template operation cancelled.");size+=count;if(size>limit)throw new IOException("Image exceeds the 100 MiB or remaining 5 GiB template budget.");digest.update(buffer,0,count);output.write(buffer,0,count);}
            }
            Path target=cache.resolve(HexFormat.of().formatHex(digest.digest()));
            if(!Files.exists(target))try{Files.move(partial,target,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(partial,target,StandardCopyOption.REPLACE_EXISTING);}
            else if(Files.size(target)!=size)throw new IOException("Cached artwork failed its size check.");
            return target;
        }finally{Files.deleteIfExists(partial);}
    }
    public static void write(Path target,Publication publication)throws IOException {
        publication.validate();byte[] json=Publication.JSON.toJson(publication).getBytes(StandardCharsets.UTF_8);
        if(json.length>MAX_JSON_BYTES)throw new IOException("Banner design metadata exceeds 16 MiB.");
        long expected=json.length;
        for(String id:publication.assetIds()){long size=publication.assetSize(id);if(size<1||size>MAX_IMAGE_BYTES)throw new IOException("Each banner image must be at most 100 MiB.");expected+=size;if(expected>MAX_BYTES)throw new IOException("Template exceeds 5 GiB expanded size.");}
        Path parent=target.toAbsolutePath().getParent();Files.createDirectories(parent);
        Path partial=Files.createTempFile(parent,"banner-",".partial");
        try {
            // Level zero keeps save CPU bounded and avoids pointless recompression of JPEG/PNG.
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(partial)))) {
                zip.setLevel(0);zip.putNextEntry(new ZipEntry("publication.json"));zip.write(json);zip.closeEntry();
                byte[] buffer=new byte[1024*1024];long total=json.length;
                for(String id:publication.assetIds()) {
                    zip.putNextEntry(new ZipEntry("assets/"+id));long count=0;
                    try(InputStream input=publication.openAsset(id)){int n;while((n=input.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new IOException("Template save cancelled.");count+=n;total+=n;if(count>MAX_IMAGE_BYTES||total>MAX_BYTES)throw new IOException("Artwork changed or exceeds template limits.");zip.write(buffer,0,n);}}
                    if(count!=publication.assetSize(id))throw new IOException("Artwork changed while saving.");zip.closeEntry();
                }
            }
            if(Files.size(partial)>MAX_BYTES)throw new IOException("Template including archive overhead exceeds 5 GiB.");
            try{Files.move(partial,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException ex){Files.move(partial,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(partial);}
    }
    public static Publication read(Path archive,Path cache)throws IOException {
        if(Files.size(archive)>MAX_BYTES)throw new IOException("Template exceeds 5 GiB.");
        Set<String> seen=new HashSet<>();Map<String,Path> files=new LinkedHashMap<>();byte[] json=null;long total=0;
        try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            ZipEntry entry;while((entry=zip.getNextEntry())!=null) {
                String name=entry.getName();
                if(entry.isDirectory()||(!name.equals("publication.json")&&!name.matches("assets/[A-Za-z0-9_-]{1,100}"))||!seen.add(name)||seen.size()>10001)throw new IOException("Unsafe, duplicate or excessive template entries.");
                if(name.equals("publication.json")){json=zip.readNBytes(MAX_JSON_BYTES+1);if(json.length>MAX_JSON_BYTES)throw new IOException("Banner metadata exceeds 16 MiB.");total+=json.length;}
                else {
                    Path file=store(zip,cache,Math.min(MAX_IMAGE_BYTES,MAX_BYTES-total));long size=Files.size(file);if(size==0)throw new IOException("Empty artwork asset.");total+=size;files.put(name.substring(7),file);
                }
                if(total>MAX_BYTES)throw new IOException("Expanded template exceeds 5 GiB.");zip.closeEntry();
            }
        }
        if(json==null)throw new IOException("Template document is missing.");
        try{Publication p=Publication.JSON.fromJson(new String(json,StandardCharsets.UTF_8),Publication.class);if(p==null)throw new IOException("Empty template.");p.assets.clear();p.assetFiles=files;p.validate();return p;}catch(RuntimeException ex){throw new IOException("Invalid banner template.",ex);}
    }
    private PublicationLargeFiles(){}
}
