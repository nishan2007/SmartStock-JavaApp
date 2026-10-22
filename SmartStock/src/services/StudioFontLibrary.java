package services;

import com.google.gson.*;
import data.EnvironmentProfile;
import java.awt.Font;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Server-owned immutable font files shared by authenticated Studio clients. */
public final class StudioFontLibrary {
    public static final int MAX_BYTES=16*1024*1024,CHUNK=524288;
    private static final class Production {static final StudioFontLibrary INSTANCE=new StudioFontLibrary(EnvironmentProfile.active().file("studio-fonts"));}
    public static StudioFontLibrary current(){return Production.INSTANCE;}
    private final Path root;
    private record Upload(int user,long expires,int size,String name,String license,Path path){}
    private final Map<String,Upload> uploads=new HashMap<>();
    StudioFontLibrary(Path root){this.root=root.toAbsolutePath().normalize();}
    public synchronized JsonObject execute(int user,boolean administrator,JsonObject request)throws Exception{
        if(user<=0)throw new SecurityException("Sign in before accessing shared fonts.");
        String action=text(request,"action");
        if(!Set.of("LIST","READ").contains(action)&&!administrator)throw new SecurityException("Company preferences permission is required to add shared fonts.");
        Files.createDirectories(root);long now=System.currentTimeMillis();
        for(var entry:new ArrayList<>(uploads.entrySet()))if(entry.getValue().expires<now){Files.deleteIfExists(entry.getValue().path);uploads.remove(entry.getKey());}
        JsonObject catalog=catalog();var result=new JsonObject();
        if(action.equals("LIST"))return catalog;
        if(action.equals("READ")){
            String sha=hashName(request);JsonObject font=find(catalog,sha);if(font==null)throw new IOException("Shared font not found.");int offset=request.get("offset").getAsInt(),size=font.get("size").getAsInt();
            if(offset<0||offset>=size)throw new IllegalArgumentException("Invalid font offset.");
            try(var file=new RandomAccessFile(root.resolve(sha+".font").toFile(),"r")){if(file.length()!=size)throw new IOException("Shared font size changed.");file.seek(offset);byte[] bytes=new byte[Math.min(CHUNK,size-offset)];file.readFully(bytes);result.addProperty("bytes",Base64.getEncoder().encodeToString(bytes));result.addProperty("size",size);result.addProperty("sha256",sha);return result;}
        }
        if(action.equals("BEGIN")){
            if(uploads.values().stream().filter(u->u.user==user).count()>=4||uploads.size()>=16)throw new IOException("Too many active font uploads.");
            int size=request.get("size").getAsInt();String name=text(request,"name");if(size<1||size>MAX_BYTES||name.length()>160||!name.toLowerCase(Locale.ROOT).matches(".*\\.(ttf|otf|ttc)$"))throw new IllegalArgumentException("Choose a TTF, OTF or TTC font up to 16 MiB.");
            String license=request.has("license")?text(request,"license"):"";if(license.length()>65536)throw new IllegalArgumentException("Font notice exceeds 64 KiB.");
            String id=UUID.randomUUID().toString();Path temp=Files.createTempFile(root,".font-upload-",".tmp");uploads.put(id,new Upload(user,now+600000,size,name,license,temp));result.addProperty("uploadId",id);return result;
        }
        String id=text(request,"uploadId");Upload upload=uploads.get(id);if(upload==null||upload.user!=user)throw new SecurityException("Font upload is unavailable for this session.");
        if(action.equals("ABORT")){Files.deleteIfExists(upload.path);uploads.remove(id);result.addProperty("aborted",true);return result;}
        if(action.equals("CHUNK")){
            String encoded=text(request,"bytes");if(encoded.length()>700000)throw new IllegalArgumentException("Font chunk too large.");byte[] bytes=Base64.getDecoder().decode(encoded);
            if(bytes.length<1||bytes.length>CHUNK||request.get("offset").getAsInt()!=Files.size(upload.path)||Files.size(upload.path)+bytes.length>upload.size)throw new IllegalArgumentException("Invalid font chunk.");Files.write(upload.path,bytes,StandardOpenOption.APPEND);result.addProperty("received",Files.size(upload.path));return result;
        }
        if(!action.equals("FINISH"))throw new IllegalArgumentException("Unknown font action.");
        if(Files.size(upload.path)!=upload.size)throw new IOException("Incomplete font upload.");
        byte[] bytes=Files.readAllBytes(upload.path);String sha=sha256(bytes);Font[] faces=validate(bytes);JsonObject existing=find(catalog,sha);if(existing!=null){Files.deleteIfExists(upload.path);uploads.remove(id);return existing;}
        if(catalog.getAsJsonArray("fonts").size()>=2000)throw new IOException("Shared font library exceeds 2000 files.");
        long total=0;for(var value:catalog.getAsJsonArray("fonts")){var row=value.getAsJsonObject();total+=row.get("size").getAsLong();for(var name:row.getAsJsonArray("faces"))for(Font face:faces)if(name.getAsString().equalsIgnoreCase(face.getFontName(Locale.ROOT)))throw new IOException("A different version of "+face.getFontName(Locale.ROOT)+" is already shared; retain the existing version.");}
        if(total+bytes.length>512L*1024*1024)throw new IOException("Shared font library exceeds 512 MiB.");
        JsonObject row=new JsonObject();row.addProperty("sha256",sha);row.addProperty("size",bytes.length);row.addProperty("filename",upload.name);row.addProperty("license",upload.license);row.addProperty("addedBy",user);var names=new JsonArray();for(Font face:faces)names.add(face.getFontName(Locale.ROOT));row.add("faces",names);
        catalog.getAsJsonArray("fonts").add(row);if(catalog.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4*1024*1024)throw new IOException("Font catalogue too large.");
        Path target=root.resolve(sha+".font");Files.move(upload.path,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);write(catalog);uploads.remove(id);return row;
    }
    private JsonObject catalog()throws Exception{Path file=root.resolve("catalog.json");if(Files.isRegularFile(file)){if(Files.size(file)>4*1024*1024)throw new IOException("Font catalogue too large.");return JsonParser.parseString(Files.readString(file)).getAsJsonObject();}var result=new JsonObject();result.addProperty("version",1);result.add("fonts",new JsonArray());return result;}
    private void write(JsonObject catalog)throws Exception{Path temp=Files.createTempFile(root,".font-catalog-",".tmp");try{Files.writeString(temp,catalog.toString());Files.move(temp,root.resolve("catalog.json"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
    private static JsonObject find(JsonObject catalog,String hash){for(var value:catalog.getAsJsonArray("fonts"))if(value.getAsJsonObject().get("sha256").getAsString().equals(hash))return value.getAsJsonObject();return null;}
    private static String hashName(JsonObject r){String hash=text(r,"sha256");if(!hash.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid font hash.");return hash;}
    private static String text(JsonObject r,String key){if(!r.has(key)||!r.get(key).isJsonPrimitive()||!r.get(key).getAsJsonPrimitive().isString())throw new IllegalArgumentException("Missing "+key);return r.get(key).getAsString();}
    public static Font[] validate(byte[] bytes)throws Exception{if(bytes.length<1||bytes.length>MAX_BYTES)throw new IOException("Font must be between 1 byte and 16 MiB.");Font[] fonts=Font.createFonts(new ByteArrayInputStream(bytes));if(fonts.length<1||fonts.length>128)throw new IOException("Font collection has too many faces.");return fonts;}
    public static String sha256(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
