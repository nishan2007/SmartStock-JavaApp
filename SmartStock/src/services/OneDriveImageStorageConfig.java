package services;

import utils.SecureCredentialStore;

import java.io.IOException;

/** Server-only OneDrive settings. Public identifiers are properties; key material stays in secure storage. */
public final class OneDriveImageStorageConfig {
    static final String TENANT_KEY="onedrive-image-tenant-id",CLIENT_KEY="onedrive-image-client-id",
            DRIVE_KEY="onedrive-image-drive-id",CERT_KEY="onedrive-image-certificate-pem",
            PRIVATE_KEY="onedrive-image-private-key-pem";

    private OneDriveImageStorageConfig() { }

    public static Settings load() {
        return new Settings(value("smartstock.onedrive.tenant",TENANT_KEY),
                value("smartstock.onedrive.client",CLIENT_KEY),
                value("smartstock.onedrive.drive",DRIVE_KEY),
                secret(CERT_KEY),secret(PRIVATE_KEY));
    }

    public static void save(String tenantId,String clientId,String driveId,String certificatePem,String privateKeyPem)
            throws IOException {
        require(tenantId,"Microsoft tenant ID"); require(clientId,"Entra client ID");
        require(driveId,"OneDrive drive ID"); require(certificatePem,"public certificate");
        require(privateKeyPem,"certificate private key");
        SecureCredentialStore.write(TENANT_KEY,tenantId.trim());
        SecureCredentialStore.write(CLIENT_KEY,clientId.trim());
        SecureCredentialStore.write(DRIVE_KEY,driveId.trim());
        SecureCredentialStore.write(CERT_KEY,certificatePem.trim());
        SecureCredentialStore.write(PRIVATE_KEY,privateKeyPem.trim());
    }

    public static void clear() {
        for(String key:new String[]{TENANT_KEY,CLIENT_KEY,DRIVE_KEY,CERT_KEY,PRIVATE_KEY}) SecureCredentialStore.delete(key);
    }

    private static String value(String property,String secretKey) {
        String value=System.getProperty(property);
        if(value==null||value.isBlank()) value=SecureCredentialStore.read(secretKey);
        return value==null?"":value.trim();
    }
    private static String secret(String key){String value=SecureCredentialStore.read(key);return value==null?"":value.trim();}
    private static void require(String value,String label) {
        if(value==null||value.isBlank()) throw new IllegalArgumentException(label+" is required.");
    }

    public record Settings(String tenantId,String clientId,String driveId,String certificatePem,String privateKeyPem) {
        public boolean configured(){return !tenantId.isBlank()&&!clientId.isBlank()&&!driveId.isBlank()
                &&!certificatePem.isBlank()&&!privateKeyPem.isBlank();}
    }
}
