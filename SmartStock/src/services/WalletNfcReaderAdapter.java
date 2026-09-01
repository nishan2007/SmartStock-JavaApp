package services;

import java.time.Instant;

/** Provider boundary for future Apple VAS-certified readers. */
public interface WalletNfcReaderAdapter {
    Presentation verify(byte[] providerPayload) throws Exception;

    record Presentation(String walletCredential, String readerId, String provider,
                        String replayToken, Instant presentedAt) { }

    static WalletNfcReaderAdapter disabled() {
        return payload -> { throw new IllegalStateException("Apple Wallet NFC is not enabled on this server."); };
    }
}
