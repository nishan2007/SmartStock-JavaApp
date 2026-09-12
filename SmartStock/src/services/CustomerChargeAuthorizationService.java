package services;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

final class CustomerChargeAuthorizationService {
    private static final int MAX_BYTES=300_000;
    private CustomerChargeAuthorizationService(){}

    static boolean required(Connection c,int customerId)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT COALESCE(require_charge_authorization,FALSE) FROM customer_accounts WHERE customer_id=?")){
            p.setInt(1,customerId);try(ResultSet r=p.executeQuery()){return r.next()&&r.getBoolean(1);}
        }
    }

    static void validateRequired(Connection c,int customerId,Authorization authorization)throws SQLException{
        if(!required(c,customerId))return;
        validate(authorization);
    }

    static void insert(Connection c,long transactionId,int customerId,int locationId,String documentType,long documentId,
                       Authorization authorization,int userId,String userName,UUID deviceId,String deviceName)throws SQLException{
        if(authorization==null)return;
        purgeExpired(c);
        Evidence evidence=validate(authorization);
        int years=3;
        try(PreparedStatement p=c.prepareStatement("SELECT COALESCE(account_signature_retention_years,3) FROM company_customization WHERE location_id=?")){
            p.setInt(1,locationId);
            try(ResultSet r=p.executeQuery()){if(r.next())years=Math.max(1,Math.min(25,r.getInt(1)));}
        }
        try(PreparedStatement p=c.prepareStatement("""
                INSERT INTO customer_charge_authorizations(transaction_id,customer_id,location_id,document_type,document_id,
                  representative_name,signature_png,signature_sha256,unsigned_reason,captured_by_user_id,captured_by_name,
                  device_id,device_name,signature_expires_at)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(transaction_id) DO NOTHING
                """)){
            int i=1;p.setLong(i++,transactionId);p.setInt(i++,customerId);p.setInt(i++,locationId);p.setString(i++,documentType);p.setLong(i++,documentId);
            p.setString(i++,evidence.name);p.setBytes(i++,evidence.png);p.setString(i++,evidence.sha);p.setString(i++,evidence.reason);
            p.setInt(i++,userId);p.setString(i++,userName);p.setObject(i++,deviceId);p.setString(i++,deviceName);
            p.setObject(i,OffsetDateTime.now().plusYears(years));p.executeUpdate();
        }
    }

    static void purgeExpired(Connection c)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("UPDATE customer_charge_authorizations SET signature_png=NULL,signature_purged_at=CURRENT_TIMESTAMP WHERE signature_png IS NOT NULL AND signature_expires_at<=CURRENT_TIMESTAMP")){p.executeUpdate();}
    }

    private static Evidence validate(Authorization a)throws SQLException{
        if(a==null)throw new IllegalArgumentException("Representative authorization is required for this customer account.");
        String name=clean(a.representativeName(),200);if(name.isBlank())throw new IllegalArgumentException("Enter the representative's name.");
        String reason=clean(a.unsignedReason(),1000);String encoded=a.signaturePngBase64()==null?"":a.signaturePngBase64().trim();
        if(encoded.isBlank()){
            if(reason.isBlank())throw new IllegalArgumentException("Capture a signature or enter the reason it could not be captured.");
            return new Evidence(name,null,null,reason);
        }
        if(!reason.isBlank())throw new IllegalArgumentException("Clear the unsigned reason when a signature is captured.");
        byte[] png;try{png=Base64.getDecoder().decode(encoded);}catch(Exception ex){throw new IllegalArgumentException("The signature image is invalid.");}
        if(png.length==0||png.length>MAX_BYTES)throw new IllegalArgumentException("The signature image is too large.");
        BufferedImage image;
        try{image=ImageIO.read(new ByteArrayInputStream(png));}catch(Exception ex){throw new IllegalArgumentException("The signature image is invalid.");}
        if(image==null||image.getWidth()<100||image.getHeight()<40||image.getWidth()>1200||image.getHeight()>600)throw new IllegalArgumentException("The signature image dimensions are invalid.");
        int ink=0;for(int y=0;y<image.getHeight();y+=2)for(int x=0;x<image.getWidth();x+=2){int rgb=image.getRGB(x,y);if(((rgb>>16)&255)<220||((rgb>>8)&255)<220||(rgb&255)<220)ink++;}
        if(ink<20)throw new IllegalArgumentException("Please provide a complete signature, not only a tap or dot.");
        String sha;
        try{sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(png));}
        catch(Exception ex){throw new SQLException("Unable to secure the signature image.",ex);}
        return new Evidence(name,png,sha,null);
    }
    private static String clean(String value,int max){String v=value==null?"":value.trim();if(v.length()>max)throw new IllegalArgumentException("Authorization text is too long.");return v;}
    record Authorization(String representativeName,String signaturePngBase64,String unsignedReason){}
    private record Evidence(String name,byte[]png,String sha,String reason){}
}
