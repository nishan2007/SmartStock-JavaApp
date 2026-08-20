package services;

import utils.SecureCredentialStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

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
                pemSecret(CERT_KEY),pemSecret(PRIVATE_KEY));
    }

    public static void save(String tenantId,String clientId,String driveId,String certificatePem,String privateKeyPem)
            throws IOException {
        require(tenantId,"Microsoft tenant ID"); require(clientId,"Entra client ID");
        require(driveId,"OneDrive drive ID"); require(certificatePem,"public certificate");
        require(privateKeyPem,"certificate private key");
        SecureCredentialStore.write(TENANT_KEY,tenantId.trim());
        SecureCredentialStore.write(CLIENT_KEY,clientId.trim());
        SecureCredentialStore.write(DRIVE_KEY,driveId.trim());
        SecureCredentialStore.write(CERT_KEY,encodePem(certificatePem));
        SecureCredentialStore.write(PRIVATE_KEY,encodePem(privateKeyPem));
        Settings saved=load();
        if(!saved.tenantId().equals(tenantId.trim())||!saved.clientId().equals(clientId.trim())
                ||!saved.driveId().equals(driveId.trim())||!saved.certificatePem().equals(certificatePem.trim())
                ||!saved.privateKeyPem().equals(privateKeyPem.trim()))
            throw new IOException("OneDrive credentials could not be verified in "+SecureCredentialStore.backendLabel()+".");
    }

    public static void saveCertificate(String certificatePem,String privateKeyPem)throws IOException{
        require(certificatePem,"public certificate");require(privateKeyPem,"certificate private key");
        SecureCredentialStore.write(CERT_KEY,encodePem(certificatePem));
        SecureCredentialStore.write(PRIVATE_KEY,encodePem(privateKeyPem));
        Settings saved=load();
        if(!saved.certificatePem().equals(certificatePem.trim())||!saved.privateKeyPem().equals(privateKeyPem.trim()))
            throw new IOException("OneDrive certificate could not be verified in "+SecureCredentialStore.backendLabel()+".");
    }

    public static void savePublicIdentifiers(String tenantId,String clientId,String driveId)throws IOException{
        require(tenantId,"Microsoft tenant ID");require(clientId,"Entra client ID");require(driveId,"OneDrive drive ID");
        SecureCredentialStore.write(TENANT_KEY,tenantId.trim());
        SecureCredentialStore.write(CLIENT_KEY,clientId.trim());
        SecureCredentialStore.write(DRIVE_KEY,driveId.trim());
        Settings saved=load();
        if(!saved.tenantId().equals(tenantId.trim())||!saved.clientId().equals(clientId.trim())
                ||!saved.driveId().equals(driveId.trim()))
            throw new IOException("OneDrive public identifiers could not be verified in "+SecureCredentialStore.backendLabel()+".");
    }

    public static boolean hasCertificate(){Settings s=load();return !s.certificatePem().isBlank()&&!s.privateKeyPem().isBlank();}

    public static void clear() {
        for(String key:new String[]{TENANT_KEY,CLIENT_KEY,DRIVE_KEY,CERT_KEY,PRIVATE_KEY}) SecureCredentialStore.delete(key);
    }

    private static String value(String property,String secretKey) {
        String value=System.getProperty(property);
        if(value==null||value.isBlank()) value=SecureCredentialStore.read(secretKey);
        return value==null?"":value.trim();
    }
    private static String secret(String key){String value=SecureCredentialStore.read(key);return value==null?"":value.trim();}
    private static String pemSecret(String key){
        String value=secret(key);if(!value.startsWith("pem-v1:"))return value;
        try{return new String(Base64.getDecoder().decode(value.substring(7)),StandardCharsets.UTF_8).trim();}
        catch(IllegalArgumentException ex){return "";}
    }
    private static String encodePem(String value){return "pem-v1:"+Base64.getEncoder().encodeToString(value.trim().getBytes(StandardCharsets.UTF_8));}
    private static void require(String value,String label) {
        if(value==null||value.isBlank()) throw new IllegalArgumentException(label+" is required.");
    }

    public record Settings(String tenantId,String clientId,String driveId,String certificatePem,String privateKeyPem) {
        public boolean configured(){return !tenantId.isBlank()&&!clientId.isBlank()&&!driveId.isBlank()
                &&!certificatePem.isBlank()&&!privateKeyPem.isBlank();}
    }
}
