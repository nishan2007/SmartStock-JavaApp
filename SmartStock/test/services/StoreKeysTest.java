package services;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class StoreKeysTest {
    @Test void keyCardsCannotBeEmployeeBadgesOrArbitraryPayloads()throws Exception {
        ServerStoreKeyService.validateCardToken("SSK1"+UUID.randomUUID().toString().replace("-",""));
        for(String invalid:new String[]{"SSB1EMPLOYEE","","SSK1arbitrary","SSK1"+"a".repeat(33)})assertThrows(ServerStoreKeyService.Rule.class,()->ServerStoreKeyService.validateCardToken(invalid));
    }
    @Test void keyPayloadFitsBothExistingCardFormats()throws Exception {
        byte[] payload=("SSK1"+UUID.randomUUID().toString().replace("-","")).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertArrayEquals(payload,PcscNfcService.decodeClassicRecord(PcscNfcService.encodeClassicRecord(payload)));
        assertArrayEquals(payload,PcscNfcService.decodeMimeNdef(PcscNfcService.wrapNdefTlv(PcscNfcService.encodeMimeNdef(payload))));
        assertThrows(ServerStoreKeyService.Rule.class,()->PcscNfcService.prepareKeyCard("SSB1EMPLOYEE"));
    }
    @Test void blankCardDetectionAcceptsEmptyNdefButProtectsExistingData() {
        assertTrue(PcscNfcService.isBlankKeyCard(new byte[]{0,0,0},true));
        assertTrue(PcscNfcService.isBlankKeyCard(new byte[]{3,0,(byte)0xfe,0},false));
        assertFalse(PcscNfcService.isBlankKeyCard(new byte[]{3,0,(byte)0xfe,0},true));
        assertFalse(PcscNfcService.isBlankKeyCard(new byte[]{3,2,65,66,(byte)0xfe},false));
        assertFalse(PcscNfcService.isBlankKeyCard("SSB1EMPLOYEE".getBytes(java.nio.charset.StandardCharsets.UTF_8),true));
    }
    @Test void staleVersionRequiresRefresh(){assertDoesNotThrow(()->ServerStoreKeyService.validateVersion(2,2));assertThrows(ServerStoreKeyService.Rule.class,()->ServerStoreKeyService.validateVersion(2,1));}
    @Test void remoteAdministrationCannotPerformKeyCustody(){assertTrue(RemoteAdminPolicy.isPhysicalOperation("/v1/store-keys/checkout"));assertTrue(RemoteAdminPolicy.isPhysicalOperation("/v1/store-keys/force-return"));}
}
