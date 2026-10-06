package architecture;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Maven needs no Node runtime, but must refuse stale checked-in website assets. */
class StorefrontBundleTest {
    @Test void detectsStaleContentAndNormalizesCheckoutLineEndings(@TempDir Path directory)throws Exception {
        Path source=directory.resolve("source.ts");Files.writeString(source,"first\nsecond\n");
        JsonObject hashes=new JsonObject();hashes.addProperty("source.ts",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source))));
        Files.writeString(source,"first\r\nsecond\r\n");assertDoesNotThrow(()->verify(directory,hashes,true));
        Files.writeString(source,"changed\n");assertThrows(AssertionError.class,()->verify(directory,hashes,true));
        Path image=directory.resolve("hero.jpg");Files.write(image,new byte[]{(byte)0xff,0,1});
        JsonObject imageHash=new JsonObject();imageHash.addProperty("hero.jpg",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(image))));
        Files.write(image,new byte[]{(byte)0xfe,0,1});assertThrows(AssertionError.class,()->verify(directory,imageHash,true));
    }
    @Test void compiledWebsiteMatchesItsSourcesAndHasNoMissingOrExtraAssets()throws Exception {
        Path root=Path.of("website"),output=Path.of("src/storefront-web");
        JsonObject manifest=JsonParser.parseString(Files.readString(output.resolve("bundle-manifest.json"))).getAsJsonObject();
        assertEquals(1,manifest.get("version").getAsInt());
        Set<String> inputs=new TreeSet<>(List.of("package.json","pnpm-lock.yaml","pnpm-workspace.yaml","tsconfig.json","vite.config.ts","index.html","tools/bundle-manifest.mjs"));
        for(String directory:List.of("src","public"))for(String name:files(root.resolve(directory)))inputs.add(directory+"/"+name);
        assertEquals(inputs,manifest.getAsJsonObject("inputs").keySet(),"Source file list changed. Run pnpm build in website.");
        Set<String> assets=files(output);assets.remove("bundle-manifest.json");
        assertEquals(assets,manifest.getAsJsonObject("assets").keySet(),"Bundle asset list differs from its manifest.");
        verify(root,manifest.getAsJsonObject("inputs"),true);
        verify(output,manifest.getAsJsonObject("assets"),false);
        Path packaged=Path.of("target/classes/storefront-web");
        assertEquals(files(output),files(packaged),"Packaged website contains missing or obsolete files.");
        verify(packaged,manifest.getAsJsonObject("assets"),false);
        assertTrue(assets.contains("index.html"));
        assertTrue(assets.stream().anyMatch(n->n.endsWith(".js")));
        assertTrue(assets.stream().anyMatch(n->n.endsWith(".css")));
        assertTrue(assets.contains("hero-3d.jpg"));
        assertTrue(assets.contains("services-editorial.jpg"));
        assertTrue(assets.contains("new-3d.jpg"));
        assertTrue(assets.contains("new-apparel.jpg"));
    }
    private static Set<String> files(Path directory)throws Exception {
        Set<String> result=new TreeSet<>();
        if(!Files.exists(directory))return result;
        try(var paths=Files.walk(directory)){for(Path p:paths.filter(Files::isRegularFile).toList())result.add(directory.relativize(p).toString().replace('\\','/'));}
        return result;
    }
    private static void verify(Path directory,JsonObject hashes,boolean normalize)throws Exception {
        for(var entry:hashes.entrySet()){
            Path file=directory.resolve(entry.getKey()).normalize();assertTrue(file.startsWith(directory));
            byte[] bytes=Files.readAllBytes(file);
            if(normalize&&!entry.getKey().matches("(?i).*\\.(png|jpe?g|webp|gif|ico|woff2?|ttf|otf|mp4|webm|pdf)"))
                bytes=new String(bytes,StandardCharsets.UTF_8).replace("\r\n","\n").getBytes(StandardCharsets.UTF_8);
            assertEquals(entry.getValue().getAsString(),HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),"Stale storefront file "+file+". Run pnpm build in website.");
        }
    }
}
