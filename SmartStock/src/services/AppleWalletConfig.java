package services;

import java.nio.file.Path;

/** Machine-local Apple Wallet configuration. Secrets are never persisted by SmartStock. */
public record AppleWalletConfig(String passTypeIdentifier, String teamIdentifier,
                                Path signingPkcs12, char[] signingPassword, Path wwdrCertificate,
                                String publicOrigin, boolean nfcEnabled, String nfcProvider,
                                String nfcEncryptionPublicKey, String trustedReaderIds) {
    private static final java.util.Set<String> IMPLEMENTED_NFC_PROVIDERS = java.util.Set.of();
    public static AppleWalletConfig load() {
        return new AppleWalletConfig(value("SMARTSTOCK_WALLET_PASS_TYPE", "smartstock.wallet.passType", ""),
                value("SMARTSTOCK_WALLET_TEAM_ID", "smartstock.wallet.teamId", ""),
                path("SMARTSTOCK_WALLET_SIGNING_P12", "smartstock.wallet.signingP12"),
                value("SMARTSTOCK_WALLET_SIGNING_PASSWORD", "smartstock.wallet.signingPassword", "").toCharArray(),
                path("SMARTSTOCK_WALLET_WWDR_CERT", "smartstock.wallet.wwdrCert"),
                value("SMARTSTOCK_WALLET_PUBLIC_ORIGIN", "smartstock.wallet.publicOrigin", ""),
                Boolean.parseBoolean(value("SMARTSTOCK_WALLET_NFC_ENABLED", "smartstock.wallet.nfcEnabled", "false")),
                value("SMARTSTOCK_WALLET_NFC_PROVIDER", "smartstock.wallet.nfcProvider", "DISABLED"),
                value("SMARTSTOCK_WALLET_NFC_PUBLIC_KEY", "smartstock.wallet.nfcPublicKey", ""),
                value("SMARTSTOCK_WALLET_TRUSTED_READERS", "smartstock.wallet.trustedReaders", ""));
    }

    public boolean barcodeReady() {
        return !passTypeIdentifier.isBlank() && !teamIdentifier.isBlank() && signingPkcs12 != null
                && wwdrCertificate != null && validPublicOrigin(publicOrigin);
    }

    static boolean validPublicOrigin(String value) {
        if (value == null) return false;
        try {
            java.net.URI uri = java.net.URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPort() == -1 || (uri.getPort() > 0 && uri.getPort() <= 65535))
                    && (uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath()));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public boolean nfcReady() {
        return barcodeReady() && nfcEnabled && !"DISABLED".equalsIgnoreCase(nfcProvider)
                && IMPLEMENTED_NFC_PROVIDERS.contains(nfcProvider.toUpperCase(java.util.Locale.ROOT))
                && !nfcEncryptionPublicKey.isBlank() && !trustedReaderIds.isBlank();
    }

    private static Path path(String env, String property) {
        String value = value(env, property, "");
        return value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
    }

    private static String value(String env, String property, String fallback) {
        String value = System.getenv(env);
        if (value == null || value.isBlank()) value = System.getProperty(property);
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
