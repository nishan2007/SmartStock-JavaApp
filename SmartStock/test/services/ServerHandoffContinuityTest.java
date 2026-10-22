package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServerHandoffContinuityTest {
    private JsonObject evidence() {
        JsonObject result=new JsonObject();result.addProperty("version",1);result.addProperty("requiredTablesPresent",true);
        result.addProperty("openDrawerSessions",1);result.addProperty("approvedRegisters",2);result.addProperty("registersNeedingCredentialRecovery",0);
        JsonObject content=new JsonObject();
        for(String table:ServerHandoffContinuity.TABLES) {
            JsonObject fingerprint=new JsonObject();fingerprint.addProperty("present",true);fingerprint.addProperty("rows",1);fingerprint.addProperty("sha256","a".repeat(64));content.add(table,fingerprint);
        }
        result.add("content",content);return result;
    }
    @Test void preservedOpenDrawerAndPermissionsPassButDoNotClaimReconnection() {
        JsonObject before=evidence(),result=ServerHandoffContinuity.compare(before,before.deepCopy(),true);
        assertTrue(result.get("preservationChecksPassed").getAsBoolean());
        assertFalse(result.get("automaticReconnectionVerified").getAsBoolean());
    }
    @Test void sameCountChangedCashOrCredentialOrPermissionBlocksAcceptance() {
        for(String table:List.of("cash_drawer_sessions","sales","devices","role_permissions","user_locations")) {
            JsonObject before=evidence(),after=before.deepCopy();
            after.getAsJsonObject("content").getAsJsonObject(table).addProperty("sha256","b".repeat(64));
            assertFalse(ServerHandoffContinuity.compare(before,after,true).get("preservationChecksPassed").getAsBoolean(),table);
        }
    }
    @Test void missingEvidenceClosedDrawerExpiredCredentialsAndIdentityAreBlocked() {
        JsonObject before=evidence();
        assertFalse(ServerHandoffContinuity.compare(null,before,true).get("preservationChecksPassed").getAsBoolean());
        assertFalse(ServerHandoffContinuity.compare(before,before,false).get("preservationChecksPassed").getAsBoolean());
        JsonObject closed=before.deepCopy();closed.addProperty("openDrawerSessions",0);
        assertFalse(ServerHandoffContinuity.compare(before,closed,true).get("preservationChecksPassed").getAsBoolean());
        JsonObject expired=before.deepCopy();expired.addProperty("registersNeedingCredentialRecovery",1);
        assertFalse(ServerHandoffContinuity.compare(before,expired,true).get("preservationChecksPassed").getAsBoolean());
        JsonObject incomplete=before.deepCopy();incomplete.getAsJsonObject("content").remove("devices");
        assertFalse(ServerHandoffContinuity.compare(incomplete,incomplete,true).get("preservationChecksPassed").getAsBoolean());
    }
    @Test void contentFingerprintsAreOrderIndependentButDetectChangesAndDuplicates() throws Exception {
        byte[] drawer=ServerHandoffContinuity.hash("{\"session\":1,\"cash\":20000}"),device=ServerHandoffContinuity.hash("paired-register");
        String original=ServerHandoffContinuity.aggregate(new ArrayList<>(List.of(drawer,device)));
        assertEquals(original,ServerHandoffContinuity.aggregate(new ArrayList<>(List.of(device,drawer))));
        assertNotEquals(original,ServerHandoffContinuity.aggregate(new ArrayList<>(List.of(ServerHandoffContinuity.hash("{\"session\":1,\"cash\":20001}"),device))));
        assertNotEquals(original,ServerHandoffContinuity.aggregate(new ArrayList<>(List.of(drawer,device,device))));
    }
    @Test void topLevelComparisonRequiresContinuityAndStableIdentity() {
        JsonObject before=new JsonObject();before.addProperty("format",1);before.addProperty("environment","production");before.addProperty("locationId",1);before.addProperty("lanCertificate","same-certificate");before.add("drawerAndRegisterContinuity",evidence());
        assertTrue(ServerHandoffDiagnostics.compare(before,before.deepCopy()).get("automatedSectionsMatch").getAsBoolean());
        JsonObject changed=before.deepCopy();changed.addProperty("lanCertificate","different-certificate");
        assertFalse(ServerHandoffDiagnostics.compare(before,changed).get("automatedSectionsMatch").getAsBoolean());
    }
}
