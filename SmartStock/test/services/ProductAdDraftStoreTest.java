package services;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ProductAdDraftStoreTest {
    @Test void rejectionReasonIsRequiredBoundedAndTrimmed() {
        assertEquals("Wrong logo",ProductAdDraftStore.rejectionReason("  Wrong logo  "));
        assertThrows(IllegalArgumentException.class,()->ProductAdDraftStore.rejectionReason(null));
        assertThrows(IllegalArgumentException.class,()->ProductAdDraftStore.rejectionReason(" \n "));
        assertThrows(IllegalArgumentException.class,()->ProductAdDraftStore.rejectionReason("x".repeat(2001)));
    }
    @Test void validatesAndCanonicalizesImportsWithoutApprovingThem() throws Exception {
        var bytes=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(100,125,BufferedImage.TYPE_INT_RGB),"jpg",bytes);
        byte[] png=ProductAdDraftStore.canonical("ad_portrait",bytes.toByteArray());
        assertEquals(137,png[0]&255);assertEquals('P',png[1]);
        assertThrows(IllegalArgumentException.class,()->ProductAdDraftStore.canonical("ad_story",bytes.toByteArray()));
        assertThrows(IllegalArgumentException.class,()->ProductAdDraftStore.canonical("ad_square",new byte[]{1,2,3}));
    }
    @Test void onlyTheExactPendingRevisionCanBeReviewed() {
        var pending=new ProductAdDraftStore.Draft(new byte[]{1},"pending",3);
        assertDoesNotThrow(()->ProductAdDraftStore.check(pending,3,true));
        assertThrows(IllegalStateException.class,()->ProductAdDraftStore.check(pending,2,true));
        assertThrows(IllegalStateException.class,()->ProductAdDraftStore.check(null,0,true));
        assertThrows(IllegalStateException.class,()->ProductAdDraftStore.check(new ProductAdDraftStore.Draft(new byte[]{1},"rejected",4),4,true));
        assertDoesNotThrow(()->ProductAdDraftStore.check(null,0,false));
    }
}
