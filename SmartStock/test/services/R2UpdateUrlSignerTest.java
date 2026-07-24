package services;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class R2UpdateUrlSignerTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void signsAStableShortLivedWorkerUrl() throws Exception {
        String url = R2UpdateUrlSigner.createDownloadUrl(
                R2UpdateUrlSigner.R2_BUCKET_REFERENCE,
                "mac/1.0.27/SmartStock Update.zip",
                Instant.ofEpochSecond(1_000),
                "https://smartstock-update-download.example.workers.dev/",
                SECRET);

        assertEquals("https://smartstock-update-download.example.workers.dev/"
                + "mac/1.0.27/SmartStock%20Update.zip"
                + "?expires=1600&signature=bb273053a23ef7523d35c73b9e7f865538134c7cb600cdc198f9b2fafe86a06a",
                url);
    }

    @Test
    void rejectsUnknownBucketsTraversalAndShortSecrets() {
        assertThrows(IllegalArgumentException.class, () -> R2UpdateUrlSigner.createDownloadUrl(
                "r2:another-bucket", "mac/release.zip", Instant.EPOCH,
                "https://updates.example.com", SECRET));
        assertThrows(IllegalArgumentException.class, () -> R2UpdateUrlSigner.createDownloadUrl(
                R2UpdateUrlSigner.R2_BUCKET_REFERENCE, "mac/../release.zip", Instant.EPOCH,
                "https://updates.example.com", SECRET));
        assertThrows(IllegalStateException.class, () -> R2UpdateUrlSigner.createDownloadUrl(
                R2UpdateUrlSigner.R2_BUCKET_REFERENCE, "mac/release.zip", Instant.EPOCH,
                "https://updates.example.com", "too-short"));
    }

    @Test
    void recognizesOnlyR2ReferencesAndRequiresHttps() {
        assertTrue(R2UpdateUrlSigner.handles("r2:smartstock-updates"));
        assertThrows(IllegalStateException.class, () -> R2UpdateUrlSigner.createDownloadUrl(
                R2UpdateUrlSigner.R2_BUCKET_REFERENCE, "mac/release.zip", Instant.EPOCH,
                "http://updates.example.com", SECRET));
    }
}
