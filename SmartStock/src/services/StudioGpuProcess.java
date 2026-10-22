package services;

import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

final class StudioGpuProcess implements StudioImageEnhancer.GpuBackend {
    private final Path directory;
    private Process process;
    private DataInputStream input;
    private DataOutputStream output;
    private final ExecutorService reader=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"studio-gpu-io");t.setDaemon(true);return t;});
    private final boolean removal;
    StudioGpuProcess(Path model,BooleanSupplier cancelled)throws Exception {this(model,cancelled,false);}
    StudioGpuProcess(Path model,BooleanSupplier cancelled,boolean removal)throws Exception {
        this.removal=removal;
        directory=Files.createTempDirectory("smartstock-gpu-");
        try {
            Properties hashes=new Properties();try(var in=StudioGpuProcess.class.getResourceAsStream("/studio-directml/sha256.properties")){
                if(in==null)throw new IOException("GPU runtime is not packaged on this platform");hashes.load(in);
            }
            for(String name:List.of("DirectML.dll","onnxruntime.dll")){
                Path file=directory.resolve(name);
                try(var in=StudioGpuProcess.class.getResourceAsStream("/studio-directml/"+name)){
                    if(in==null)throw new IOException("GPU runtime missing");Files.copy(in,file);
                }
                String sha;try(var in=Files.newInputStream(file)){
                    var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];int n;
                    while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);sha=HexFormat.of().formatHex(digest.digest());
                }
                if(!sha.equals(hashes.getProperty(name)))throw new IOException("GPU runtime checksum mismatch");
            }
            Path java=Path.of(System.getProperty("java.home"),"bin","java.exe");
            if(!Files.isRegularFile(java))throw new IOException("Bundled Java launcher missing");
            ProcessBuilder builder=new ProcessBuilder(java.toString(),"-Xmx256m",
                "-Donnxruntime.native.onnxruntime.path="+directory.resolve("onnxruntime.dll"),
                "-cp",System.getProperty("java.class.path"),StudioGpuWorker.class.getName(),model.toAbsolutePath().toString(),
                directory.resolve("DirectML.dll").toString(),directory.resolve("onnxruntime.dll").toString(),removal?"REMOVAL":"ENHANCEMENT");
            Map<String,String> inherited=new HashMap<>(builder.environment());builder.environment().clear();
            for(String key:List.of("SystemRoot","WINDIR","SystemDrive","TEMP","TMP")){
                for(var entry:inherited.entrySet())if(entry.getKey().equalsIgnoreCase(key))builder.environment().put(entry.getKey(),entry.getValue());
            }
            builder.redirectError(directory.resolve("worker-error.log").toFile());
            process=builder.start();input=new DataInputStream(new BufferedInputStream(process.getInputStream()));
            output=new DataOutputStream(new BufferedOutputStream(process.getOutputStream()));
            if(await(()->input.readInt(),cancelled,120)!=0x53534731)throw new IOException("GPU protocol mismatch");
        }catch(Exception|LinkageError ex){
            try{Path log=directory.resolve("worker-error.log");if(Files.exists(log)){
                try(var in=Files.newInputStream(log)){String detail=new String(in.readNBytes(8192),java.nio.charset.StandardCharsets.UTF_8);
                    if(!detail.isBlank())ex.addSuppressed(new IOException(detail));}
            }}catch(IOException ignored){}
            close();throw ex;
        }
    }
    public FloatBuffer run(float[] rgb,int w,int h,BooleanSupplier cancelled)throws Exception {
        return await(()->{
            output.writeInt(w);output.writeInt(h);for(float v:rgb)output.writeFloat(v);output.flush();
            int count=input.readInt();if(count!=w*h*(removal?1:48))throw new IOException("GPU output size mismatch");
            float[] values=new float[count];for(int i=0;i<count;i++)values[i]=input.readFloat();
            return FloatBuffer.wrap(values);
        },cancelled,180);
    }
    private <T>T await(Callable<T> action,BooleanSupplier cancelled,int seconds)throws Exception {
        Future<T> future=reader.submit(action);long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(seconds);
        try{while(true){
            if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new CancellationException("Image processing cancelled");
            if(System.nanoTime()>end)throw new IOException("GPU processing timed out");
            try{return future.get(200,TimeUnit.MILLISECONDS);}catch(TimeoutException ignored){}
        }}catch(Exception ex){if(process!=null)process.destroyForcibly();throw ex;}
    }
    public void close(){
        if(process!=null){process.destroyForcibly();try{process.waitFor(5,TimeUnit.SECONDS);}catch(InterruptedException ex){Thread.currentThread().interrupt();}}
        reader.shutdownNow();
        try{if(input!=null)input.close();}catch(IOException ignored){}
        try{if(output!=null)output.close();}catch(IOException ignored){}
        try(var paths=Files.walk(directory)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())try{Files.deleteIfExists(path);}catch(IOException ignored){}}
        catch(IOException ignored){}
    }
}
