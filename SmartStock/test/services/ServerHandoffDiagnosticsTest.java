package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerHandoffDiagnosticsTest {
    @TempDir Path directory;
    @Test void tokenMethodPathAndRevocationAreEnforced() {
        var share=new ServerHandoffDiagnostics.Sharing(new JsonObject());
        assertEquals(403,share.authorize("GET","/report",null));
        assertEquals(403,share.authorize("GET","/report","Bearer wrong"));
        assertEquals(405,share.authorize("POST","/report","Bearer "+share.token));
        assertEquals(404,share.authorize("GET","/commands","Bearer "+share.token));
        assertEquals(200,share.authorize("GET","/report","Bearer "+share.token));
        share.close();assertEquals(410,share.authorize("GET","/report","Bearer "+share.token));
    }
    @Test void reportReadsAreBounded() {
        var share=new ServerHandoffDiagnostics.Sharing(new JsonObject());
        for(int n=0;n<60;n++)assertEquals(200,share.authorize("GET","/report","Bearer "+share.token));
        assertEquals(429,share.authorize("GET","/report","Bearer "+share.token));
    }
    @Test void expiredTokenCannotReadReport() {
        var share=new ServerHandoffDiagnostics.Sharing(new JsonObject(),java.time.Instant.now().minusSeconds(1));
        assertEquals(410,share.authorize("GET","/report","Bearer "+share.token));
    }
    @Test void httpReportRequiresBearerAndDoesNotEmbedTokenInPage() throws Exception {
        var share=new ServerHandoffDiagnostics.Sharing(new JsonObject());
        var server=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),4);
        server.createContext("/",share::handle);server.start();
        try {
            var client=java.net.http.HttpClient.newHttpClient();
            String origin="http://127.0.0.1:"+server.getAddress().getPort();
            var page=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(origin+"/")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,page.statusCode());assertFalse(page.body().contains(share.token));
            var denied=client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(origin+"/report")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(403,denied.statusCode());
            var request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(origin+"/report")).header("Authorization","Bearer "+share.token).build();
            var allowed=client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(200,allowed.statusCode());assertEquals("no-store",allowed.headers().firstValue("Cache-Control").orElse(""));
            assertTrue(JsonParser.parseString(allowed.body()).getAsJsonObject().has("diagnosticsExpireAt"));
            share.close();assertEquals(410,client.send(request,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        }finally{server.stop(0);share.close();}
    }
    @Test void filesDetectContentRenameAndMissingDirectory() throws Exception {
        assertFalse(ServerHandoffDiagnostics.digestTree(directory.resolve("missing")).get("present").getAsBoolean());
        Files.writeString(directory.resolve("a"),"one");
        JsonObject first=ServerHandoffDiagnostics.digestTree(directory);
        assertEquals(first,ServerHandoffDiagnostics.digestTree(directory));
        Files.writeString(directory.resolve("a"),"two");
        assertNotEquals(first,ServerHandoffDiagnostics.digestTree(directory));
        Files.writeString(directory.resolve("a"),"one");Files.move(directory.resolve("a"),directory.resolve("b"));
        assertNotEquals(first,ServerHandoffDiagnostics.digestTree(directory));
    }
    @Test void matchingEvidenceNeverClaimsCompleteVerification() {
        JsonObject first=new JsonObject();first.addProperty("format",1);first.addProperty("locationId",3);
        var same=ServerHandoffDiagnostics.compare(first,first.deepCopy());
        assertFalse(same.get("automatedSectionsMatch").getAsBoolean()); // Legacy baseline lacks continuity evidence.
        assertFalse(same.get("verifiedComplete").getAsBoolean());
        JsonObject other=first.deepCopy();other.addProperty("locationId",4);
        assertFalse(ServerHandoffDiagnostics.compare(first,other).get("automatedSectionsMatch").getAsBoolean());
        assertThrows(IllegalArgumentException.class,()->ServerHandoffDiagnostics.compare(new JsonObject(),other));
    }
}
