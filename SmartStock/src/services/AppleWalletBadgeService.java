package services;

import com.google.gson.Gson;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Revocable Apple Wallet badge issuance and authentication. */
public final class AppleWalletBadgeService {
    public static final String CREDENTIAL_PREFIX = "SSW1";
    private static final Pattern SCANNED_CREDENTIAL = Pattern.compile(
            "(?i)SSW1[0-9A-HJKMNP-TV-Z]{32}");
    private static final Duration ENROLLMENT_LIFETIME = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Gson GSON = LanJson.create();

    private AppleWalletBadgeService() { }

    public static Enrollment createEnrollment(Connection c, int userId, int locationId, int actorId) throws Exception {
        String origin=AppleWalletConfig.load().publicOrigin();
        if(!AppleWalletConfig.validPublicOrigin(origin)) throw new IllegalStateException("Configure a valid HTTPS Apple Wallet public origin.");
        requireEmployee(c, userId, locationId);
        byte[] raw = random(32); String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant expires = Instant.now().plus(ENROLLMENT_LIFETIME);
        try (PreparedStatement revoke = c.prepareStatement("UPDATE employee_wallet_enrollments SET consumed_at=CURRENT_TIMESTAMP WHERE user_id=? AND consumed_at IS NULL")) {
            revoke.setInt(1, userId); revoke.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO employee_wallet_enrollments(user_id,token_hash,expires_at,created_by_user_id,location_id) VALUES(?,?,?,?,?)")) {
            ps.setInt(1,userId); ps.setString(2,hash(token)); ps.setTimestamp(3,java.sql.Timestamp.from(expires)); ps.setInt(4,actorId); ps.setInt(5,locationId); ps.executeUpdate();
        }
        return new Enrollment(origin.replaceAll("/+$","")+"/wallet/enroll/"+token, expires);
    }

    public static Status status(Connection c, int userId, int locationId) throws Exception {
        requireEmployee(c,userId,locationId);
        try(PreparedStatement ps=c.prepareStatement("SELECT status,issued_at,last_used_at FROM employee_wallet_credentials WHERE user_id=? ORDER BY issued_at DESC LIMIT 1")){
            ps.setInt(1,userId);try(ResultSet r=ps.executeQuery()){return r.next()?new Status(r.getString(1),instant(r,2),instant(r,3)):new Status("NOT_ISSUED",null,null);}
        }
    }

    public static void revoke(Connection c,int userId,int locationId,int actorId)throws Exception{
        requireEmployee(c,userId,locationId);
        try(PreparedStatement ps=c.prepareStatement("UPDATE employee_wallet_credentials SET status='REVOKED',revoked_at=CURRENT_TIMESTAMP,revoked_by_user_id=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND status='ACTIVE'")){ps.setInt(1,actorId);ps.setInt(2,userId);ps.executeUpdate();}
        try(PreparedStatement ps=c.prepareStatement("UPDATE employee_wallet_enrollments SET consumed_at=CURRENT_TIMESTAMP WHERE user_id=? AND consumed_at IS NULL")){ps.setInt(1,userId);ps.executeUpdate();}
    }

    public static byte[] consumeEnrollment(Connection c,String token)throws Exception{
        AppleWalletConfig config=AppleWalletConfig.load();
        if(!config.barcodeReady())throw new IllegalStateException("Apple Wallet pass signing is not configured.");
        c.setAutoCommit(false);
        try {
            int userId;
            int locationId;
            String name;
            String username;
            try(PreparedStatement ps=c.prepareStatement("SELECT e.user_id,u.full_name,u.username,e.location_id FROM employee_wallet_enrollments e JOIN users u ON u.user_id=e.user_id WHERE e.token_hash=? AND e.consumed_at IS NULL AND e.expires_at>CURRENT_TIMESTAMP AND u.is_active=TRUE FOR UPDATE")){
                ps.setString(1,hash(token));
                try(ResultSet r=ps.executeQuery()){
                    if(!r.next())throw new IllegalArgumentException("This enrollment link is invalid, expired, or already used.");
                    userId=r.getInt(1);name=r.getString(2);username=r.getString(3);
                    locationId=r.getObject(4)==null?employeeLocation(c,userId):r.getInt(4);
                }
            }
                revoke(c,userId,locationId,userId);
                String credential=CREDENTIAL_PREFIX+base32(random(20)),serial=UUID.randomUUID().toString();
                try(PreparedStatement insert=c.prepareStatement("INSERT INTO employee_wallet_credentials(user_id,credential_hash,serial_number,issued_by_user_id) SELECT user_id,?,?,created_by_user_id FROM employee_wallet_enrollments WHERE token_hash=?")){insert.setString(1,hash(credential));insert.setString(2,serial);insert.setString(3,hash(token));insert.executeUpdate();}
                try(PreparedStatement used=c.prepareStatement("UPDATE employee_wallet_enrollments SET consumed_at=CURRENT_TIMESTAMP WHERE token_hash=?")){used.setString(1,hash(token));used.executeUpdate();}
            WalletBadgeTemplate template=WalletTemplateRepository.load(c,locationId);
            var employee=BadgePrintService.loadEmployeeBadgeData(c,userId,locationId);
            Map<String,String> values=Map.of("NAME",employee.displayName(),"USERNAME",employee.username(),
                    "FIRST_NAME",employee.firstName(),"LAST_NAME",employee.lastName(),"EMAIL",employee.email(),"PHONE",employee.phone(),
                    "ROLE",employee.roleName(),"LOCATION",employee.locationName(),"ISSUED",java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString());
            BufferedImage photo=template.employeePhoto()?utils.ImageCacheManager.loadImage(employee.photoPath()):null;
            byte[] pass=buildPass(config,serial,credential,template,values,photo);c.commit();return pass;
        }catch(Exception ex){c.rollback();throw ex;}finally{c.setAutoCommit(true);}
    }

    public static AuthenticatedWallet authenticate(Connection c,String credential,int locationId,char[] pin)throws Exception{
        String normalized=normalizeScannedCredential(credential);
        if(normalized.isEmpty())return null;
        try(PreparedStatement ps=c.prepareStatement("SELECT w.user_id,u.username,u.full_name FROM employee_wallet_credentials w JOIN users u ON u.user_id=w.user_id JOIN user_locations ul ON ul.user_id=u.user_id AND ul.location_id=? WHERE w.credential_hash=? AND w.status='ACTIVE' AND u.is_active=TRUE")){
            ps.setInt(1,locationId);ps.setString(2,hash(normalized));try(ResultSet r=ps.executeQuery()){if(!r.next())return null;int id=r.getInt(1);boolean required=managers.ServerCompanyCustomizationRepository.isBadgePinRequired(c,locationId);if(required&&!LocalAuthCacheService.verifyEmployeePin(c,id,pin))return null;try(PreparedStatement used=c.prepareStatement("UPDATE employee_wallet_credentials SET last_used_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND credential_hash=?")){used.setInt(1,id);used.setString(2,hash(normalized));used.executeUpdate();}return new AuthenticatedWallet(id,r.getString(2),r.getString(3),required);}}
    }

    /**
     * Keyboard-wedge scanners can surround QR data with an AIM symbology identifier,
     * framing characters, or a configured prefix/suffix. Extract only the fixed-size
     * opaque Wallet credential so those scanner settings do not change its hash.
     */
    public static String normalizeScannedCredential(String scanned) {
        if (scanned == null || scanned.isBlank()) return "";
        Matcher matcher = SCANNED_CREDENTIAL.matcher(scanned.toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group() : "";
    }

    static byte[] buildPass(AppleWalletConfig config,String serial,String name,String username,String credential)throws Exception{
        return buildPass(config,serial,credential,WalletBadgeTemplate.defaults(),Map.of("NAME",name,"USERNAME",username),null);
    }

    static byte[] buildPass(AppleWalletConfig config,String serial,String credential,WalletBadgeTemplate template,
                            Map<String,String> employee,BufferedImage employeePhoto)throws Exception{
        Map<String,byte[]> files=new LinkedHashMap<>();Map<String,Object> pass=new LinkedHashMap<>();
        pass.put("formatVersion",1);pass.put("passTypeIdentifier",config.passTypeIdentifier());pass.put("serialNumber",serial);pass.put("teamIdentifier",config.teamIdentifier());pass.put("organizationName","SmartStock");pass.put("description","SmartStock Employee Badge");pass.put("logoText","SmartStock");pass.put("foregroundColor","rgb(255,255,255)");pass.put("backgroundColor","rgb(0,55,96)");
        pass.put("organizationName",template.company());pass.put("description",template.title());pass.put("logoText",template.company());
        pass.put("foregroundColor",WalletBadgeTemplate.rgb(template.foreground()));pass.put("backgroundColor",WalletBadgeTemplate.rgb(template.background()));pass.put("labelColor",WalletBadgeTemplate.rgb(template.labelColor()));
        pass.put("generic",template.passFields(employee));
        if(template.poster().enabled())pass.put("posterGeneric",WalletPosterArtwork.fields(template,employee));
        pass.put("barcodes",List.of(Map.of("format","PKBarcodeFormatQR","message",credential,"messageEncoding","iso-8859-1","altText","SmartStock Wallet Badge")));
        pass.put("sharingProhibited",true);
        if(config.nfcReady())pass.put("nfc",Map.of("message",credential,"encryptionPublicKey",config.nfcEncryptionPublicKey(),"requiresAuthentication",false));
        files.put("pass.json",GSON.toJson(pass).getBytes(StandardCharsets.UTF_8));files.put("icon.png",icon(29));files.put("icon@2x.png",icon(58));files.put("icon@3x.png",icon(87));
        if(template.poster().enabled())for(int scale=1;scale<=3;scale++){
            ByteArrayOutputStream artwork=new ByteArrayOutputStream();
            ImageIO.write(WalletPosterArtwork.render(template,scale),"png",artwork);
            files.put("artwork"+(scale==1?"":"@"+scale+"x")+".png",artwork.toByteArray());
        }
        BufferedImage logo=WalletBadgeTemplate.decodeImage(template.logoPng());
        if(template.poster().enabled()&&logo!=null)for(int scale=1;scale<=3;scale++)files.put("primaryLogo"+(scale==1?"":"@"+scale+"x")+".png",WalletBadgeTemplate.image(logo,126*scale,30*scale,template.logoScale()));
        if(logo!=null)for(int scale=1;scale<=3;scale++)files.put("logo"+(scale==1?"":"@"+scale+"x")+".png",WalletBadgeTemplate.image(logo,160*scale,50*scale,template.logoScale()));
        BufferedImage thumbnail=template.employeePhoto()?employeePhoto:WalletBadgeTemplate.decodeImage(template.thumbnailPng());
        if(thumbnail!=null)for(int scale=1;scale<=3;scale++)files.put("thumbnail"+(scale==1?"":"@"+scale+"x")+".png",WalletBadgeTemplate.image(thumbnail,90*scale,90*scale,template.thumbnailScale()));
        Map<String,String> manifest=new LinkedHashMap<>();for(var e:files.entrySet())manifest.put(e.getKey(),hex(digest(e.getValue())));byte[] manifestBytes=GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8);files.put("manifest.json",manifestBytes);files.put("signature",sign(config,manifestBytes));
        ByteArrayOutputStream out=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(out)){for(var e:files.entrySet()){zip.putNextEntry(new ZipEntry(e.getKey()));zip.write(e.getValue());zip.closeEntry();}}return out.toByteArray();
    }

    private static byte[] sign(AppleWalletConfig cfg,byte[] manifest)throws Exception{KeyStore store=KeyStore.getInstance("PKCS12");try(InputStream in=Files.newInputStream(cfg.signingPkcs12())){store.load(in,cfg.signingPassword());}String alias=Collections.list(store.aliases()).stream().filter(a->{try{return store.isKeyEntry(a);}catch(Exception e){return false;}}).findFirst().orElseThrow();PrivateKey key=(PrivateKey)store.getKey(alias,cfg.signingPassword());X509Certificate signer=(X509Certificate)store.getCertificate(alias);X509Certificate wwdr;try(InputStream in=Files.newInputStream(cfg.wwdrCertificate())){wwdr=(X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(in);}CMSSignedDataGenerator gen=new CMSSignedDataGenerator();gen.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(new JcaDigestCalculatorProviderBuilder().build()).build(new JcaContentSignerBuilder("SHA256withRSA").build(key),signer));gen.addCertificates(new JcaCertStore(List.of(signer,wwdr)));return gen.generate(new CMSProcessableByteArray(manifest),false).getEncoded();}
    private static byte[] icon(int size)throws IOException{BufferedImage i=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);Graphics2D g=i.createGraphics();g.setColor(new Color(0,55,96));g.fillRoundRect(0,0,size,size,size/4,size/4);g.setColor(new Color(255,112,0));g.setFont(new Font("SansSerif",Font.BOLD,Math.max(10,size/2)));g.drawString("S",size/3,size*2/3);g.dispose();ByteArrayOutputStream o=new ByteArrayOutputStream();ImageIO.write(i,"png",o);return o.toByteArray();}
    private static void requireEmployee(Connection c,int userId,int location)throws Exception{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM users u JOIN user_locations ul ON ul.user_id=u.user_id WHERE u.user_id=? AND ul.location_id=?")){p.setInt(1,userId);p.setInt(2,location);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Employee was not found at this store.");}}}
    private static int employeeLocation(Connection c,int id)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT MIN(location_id) FROM user_locations WHERE user_id=?")){p.setInt(1,id);try(ResultSet r=p.executeQuery()){if(!r.next()||r.getObject(1)==null)throw new SQLException("Employee has no assigned store.");return r.getInt(1);}}}
    private static Instant instant(ResultSet r,int i)throws SQLException{java.sql.Timestamp t=r.getTimestamp(i);return t==null?null:t.toInstant();}
    private static byte[] random(int n){byte[] b=new byte[n];RANDOM.nextBytes(b);return b;}
    private static String hash(String s){return LanSecurity.sha256(s);}
    private static byte[] digest(byte[] b)throws Exception{return MessageDigest.getInstance("SHA-1").digest(b);}
    private static String hex(byte[] b){return HexFormat.of().formatHex(b);}
    private static String base32(byte[] b){String alphabet="0123456789ABCDEFGHJKMNPQRSTVWXYZ";StringBuilder s=new StringBuilder();int buffer=0,bits=0;for(byte x:b){buffer=(buffer<<8)|(x&255);bits+=8;while(bits>=5){s.append(alphabet.charAt((buffer>>(bits-=5))&31));}}if(bits>0)s.append(alphabet.charAt((buffer<<(5-bits))&31));return s.toString();}
    public record Enrollment(String url,Instant expiresAt){}
    public record Status(String status,Instant issuedAt,Instant lastUsedAt){}
    public record AuthenticatedWallet(int userId,String username,String fullName,boolean pinRequired){}
}
