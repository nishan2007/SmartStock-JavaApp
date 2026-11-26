package services;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmartDriveLibraryGatewayTest {
    @Test void verifiesTemplateIdentityAndExactContentHash()throws Exception {
        var id=java.util.UUID.fromString("11111111-1111-4111-8111-111111111111");
        var head=new com.google.gson.JsonObject();head.addProperty("id",id.toString());head.addProperty("storageStoreId",1);
        head.addProperty("ownerStoreId",2);head.add("sharedStoreIds",new com.google.gson.JsonArray());
        head.addProperty("content","{\"savePrintRecords\":true,\"elements\":[]}");
        head.addProperty("sha256",LanSecurity.sha256(head.get("content").getAsString()));
        SmartDriveLibraryGateway.verifyTemplate(head,1,id);
        assertThrows(java.io.IOException.class,()->SmartDriveLibraryGateway.verifyTemplate(head,2,id));
        head.addProperty("content","changed");
        assertThrows(java.io.IOException.class,()->SmartDriveLibraryGateway.verifyTemplate(head,1,id));
    }
    @Test void acceptsOnlyHttpsOriginsWithoutEmbeddedCredentialsOrPaths() {
        assertEquals("https://drive.example.com/internal/studio/catalog",
                SmartDriveLibraryGateway.endpoint("https://drive.example.com/").toString());
        for(String value:new String[]{"http://drive.example.com","https://user:secret@drive.example.com",
                "https://drive.example.com/other","https://drive.example.com?token=secret","https://drive.example.com/#fragment"}) {
            assertThrows(IllegalArgumentException.class,()->SmartDriveLibraryGateway.endpoint(value));
        }
    }
}
