package services;

import data.EnvironmentProfile;
import data.DB;
import managers.SupabaseSessionManager;
import utils.SecureFilePermissions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipFile;

/** Store-owned custom-order media. Only opaque, server-generated keys become filesystem paths. */
public final class CustomOrderMediaService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final String BUCKET = "custom-order-private";
    private static final OneDriveImageCloudProvider ONEDRIVE = new OneDriveImageCloudProvider();
    private static final int CHUNK_MAX = 1024 * 1024;
    private static final Map<String,String> TYPES = Map.ofEntries(
        Map.entry("jpg","image/jpeg"), Map.entry("jpeg","image/jpeg"), Map.entry("png","image/png"),
        Map.entry("webp","image/webp"), Map.entry("gif","image/gif"), Map.entry("pdf","application/pdf"),
        Map.entry("doc","application/msword"),
        Map.entry("docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
        Map.entry("xls","application/vnd.ms-excel"),
        Map.entry("xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        Map.entry("ppt","application/vnd.ms-powerpoint"),
        Map.entry("pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation"));

    private CustomOrderMediaService() { }

    public record Begin(UUID uploadId, long limitBytes) { }
    public record Completed(UUID fileId, String proofToken, int revision) { }
    public record Media(UUID id,String filename,String contentType,long bytes,String storageKey) { }
    public record AccessLink(UUID id,String token) { }
    public record ProofDecision(UUID proofId,String status) { }

    public static String newToken() { byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public static String tokenHash(String token) throws Exception { return sha256(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private static String sha256(byte[] bytes)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
    private static String sha256(Path file)throws Exception{MessageDigest d=MessageDigest.getInstance("SHA-256");try(InputStream in=Files.newInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))>=0)if(n>0)d.update(b,0,n);}return HexFormat.of().formatHex(d.digest());}
    private static Path root()throws IOException{Path p=EnvironmentProfile.active().directory().resolve("custom-order-media").toAbsolutePath().normalize();Files.createDirectories(p);SecureFilePermissions.restrictDirectoryToOwner(p);return p;}
    private static Path stage(UUID id)throws IOException{Path d=root().resolve("staging");Files.createDirectories(d);SecureFilePermissions.restrictDirectoryToOwner(d);return d.resolve(id.toString()+".part");}
    private static Path asset(String key)throws IOException{if(!key.matches("[0-9a-f-]{36}"))throw new IOException("Invalid media key.");Path d=root().resolve("assets");Files.createDirectories(d);SecureFilePermissions.restrictDirectoryToOwner(d);return d.resolve(key);}
    static Path stagingPath(UUID id)throws IOException{return stage(id);}
    static Path assetPath(String key)throws IOException{return asset(key);}
    private static String cleanName(String value){String name=value==null?"":value.replace('\\','/');name=name.substring(name.lastIndexOf('/')+1).trim();if(name.isEmpty()||name.length()>180||name.contains("..")||name.chars().anyMatch(c->c<32))throw new IllegalArgumentException("Choose a filename of at most 180 characters.");return name;}
    private static String type(String filename,boolean proof){int dot=filename.lastIndexOf('.');String ext=dot<0?"":filename.substring(dot+1).toLowerCase(Locale.ROOT);String result=TYPES.get(ext);if(result==null||(proof&&!Set.of("jpg","jpeg","png","pdf").contains(ext)))throw new IllegalArgumentException(proof?"Use a JPEG, PNG, or PDF preview.":"This file type is not supported.");return result;}
    private static void requireLine(Connection c,long orderId,long lineId,int locationId)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM custom_order_lines l JOIN custom_orders o ON o.custom_order_id=l.custom_order_id WHERE l.custom_order_id=? AND l.custom_order_line_id=? AND o.location_id=? AND o.status NOT IN ('DELIVERED','CANCELLED') FOR UPDATE OF l")){p.setLong(1,orderId);p.setLong(2,lineId);p.setInt(3,locationId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("This order line is unavailable for uploads.");}}}
    public static long limit(Connection c,int locationId)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT custom_order_file_limit_bytes FROM company_customization WHERE location_id=?")){p.setInt(1,locationId);try(ResultSet r=p.executeQuery()){return r.next()?r.getLong(1):104857600L;}}}

    public static Begin begin(Connection c,long orderId,long lineId,int locationId,String kind,String filename,long size,String hash,Integer staff,UUID customerLink,Integer overrideBy,String overrideReason)throws Exception{
        return begin(c,orderId,lineId,locationId,kind,filename,size,hash,staff,customerLink,overrideBy,overrideReason,null);
    }
    public static Begin begin(Connection c,long orderId,long lineId,int locationId,String kind,String filename,long size,String hash,Integer staff,UUID customerLink,Integer overrideBy,String overrideReason,UUID requestedId)throws Exception{
        boolean proof="PROOF".equals(kind);if(!proof&&!"ATTACHMENT".equals(kind))throw new IllegalArgumentException("Invalid file kind.");
        filename=cleanName(filename);String mime=type(filename,proof);
        if(size<=0||size>Long.MAX_VALUE-CHUNK_MAX||hash==null||!hash.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Invalid file size or checksum.");
        if(staff!=null&&hash.equals("0".repeat(64)))throw new IllegalArgumentException("A staff upload requires a checksum.");
        requireLine(c,orderId,lineId,locationId);
        long configured=limit(c,locationId);
        if(size>configured&&(overrideBy==null||overrideReason==null||overrideReason.isBlank()))throw new IllegalArgumentException("This file exceeds the company size limit.");
        if(customerLink!=null&&overrideBy!=null)throw new IllegalArgumentException("Customers cannot override the file size limit.");
        if(requestedId!=null)try(PreparedStatement p=c.prepareStatement("SELECT custom_order_id,custom_order_line_id,kind,filename,expected_bytes,sha256,staff_user_id,customer_link_id FROM custom_order_file_uploads WHERE upload_id=? AND expires_at>now()")){p.setObject(1,requestedId);try(ResultSet r=p.executeQuery()){if(r.next()){if(r.getLong(1)!=orderId||r.getLong(2)!=lineId||!kind.equals(r.getString(3))||!filename.equals(r.getString(4))||r.getLong(5)!=size||!hash.equals(r.getString(6))||!java.util.Objects.equals(staff,r.getObject(7))||!java.util.Objects.equals(customerLink,r.getObject(8,UUID.class)))throw new IllegalArgumentException("Upload identifier belongs to another file.");return new Begin(requestedId,configured);}}}
        if(!proof){try(PreparedStatement p=c.prepareStatement("SELECT (SELECT count(*) FROM custom_order_files WHERE custom_order_line_id=? AND removed_at IS NULL AND deleted_at IS NULL)+(SELECT count(*) FROM custom_order_file_uploads WHERE custom_order_line_id=? AND kind='ATTACHMENT' AND expires_at>now())")){p.setLong(1,lineId);p.setLong(2,lineId);try(ResultSet r=p.executeQuery()){r.next();if(r.getLong(1)>=15)throw new IllegalArgumentException("This line already has 15 attachments.");}}}
        UUID id=requestedId==null?UUID.randomUUID():requestedId;try(PreparedStatement p=c.prepareStatement("INSERT INTO custom_order_file_uploads(upload_id,custom_order_id,custom_order_line_id,kind,filename,content_type,expected_bytes,sha256,customer_link_id,staff_user_id,override_by_user_id,override_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")){p.setObject(1,id);p.setLong(2,orderId);p.setLong(3,lineId);p.setString(4,kind);p.setString(5,filename);p.setString(6,mime);p.setLong(7,size);p.setString(8,hash);p.setObject(9,customerLink);if(staff==null)p.setNull(10,java.sql.Types.INTEGER);else p.setInt(10,staff);if(overrideBy==null)p.setNull(11,java.sql.Types.INTEGER);else p.setInt(11,overrideBy);p.setString(12,overrideReason);p.executeUpdate();}
        return new Begin(id,configured);
    }

    public static long chunk(Connection c,UUID id,long offset,byte[] bytes,Integer staff,UUID customerLink)throws Exception{
        if(bytes==null||bytes.length==0||bytes.length>CHUNK_MAX)throw new IllegalArgumentException("Send a file chunk of at most 1 MB.");
        if(offset<0||offset>Long.MAX_VALUE-CHUNK_MAX)throw new IllegalArgumentException("Invalid file offset.");
        try(PreparedStatement p=c.prepareStatement("SELECT received_bytes,expected_bytes,staff_user_id,customer_link_id FROM custom_order_file_uploads WHERE upload_id=? AND expires_at>now() FOR UPDATE")){p.setObject(1,id);try(ResultSet r=p.executeQuery()){if(!r.next()||!java.util.Objects.equals(staff,r.getObject(3))||!java.util.Objects.equals(customerLink,r.getObject(4,UUID.class)))throw new IllegalArgumentException("Upload session expired or unavailable.");long received=r.getLong(1);Path path=stage(id);if(offset<received&&offset+bytes.length<=received&&Files.isRegularFile(path)){byte[] old=new byte[bytes.length];try(FileChannel in=FileChannel.open(path,StandardOpenOption.READ)){ByteBuffer b=ByteBuffer.wrap(old);while(b.hasRemaining()&&in.read(b,offset+b.position())>0){}if(!b.hasRemaining()&&java.util.Arrays.equals(old,bytes))return received;}}if(offset!=received||offset+bytes.length>r.getLong(2))throw new IllegalArgumentException("The file chunk is out of sequence.");try(FileChannel out=FileChannel.open(path,StandardOpenOption.CREATE,StandardOpenOption.WRITE)){if(out.size()!=received)throw new IOException("The incomplete upload needs to be restarted.");out.position(received);ByteBuffer b=ByteBuffer.wrap(bytes);while(b.hasRemaining())out.write(b);out.force(true);}SecureFilePermissions.restrictFileToOwner(path);try(PreparedStatement update=c.prepareStatement("UPDATE custom_order_file_uploads SET received_bytes=? WHERE upload_id=?")){update.setLong(1,offset+bytes.length);update.setObject(2,id);update.executeUpdate();}return offset+bytes.length;}}
    }

    public static Completed finish(Connection c,UUID id,int locationId,Integer staff,UUID customerLink)throws Exception{
        try(PreparedStatement p=c.prepareStatement("SELECT custom_order_id,custom_order_line_id,kind,filename,content_type,expected_bytes,received_bytes,sha256,staff_user_id,customer_link_id,override_by_user_id,override_reason FROM custom_order_file_uploads WHERE upload_id=? AND expires_at>now() FOR UPDATE")){p.setObject(1,id);try(ResultSet r=p.executeQuery()){
            if(!r.next()){
                if(customerLink!=null)try(PreparedStatement existing=c.prepareStatement("SELECT 1 FROM custom_order_files f JOIN custom_order_access_links l ON l.custom_order_id=f.custom_order_id WHERE f.file_id=? AND l.link_id=? AND f.uploaded_by_customer=TRUE")){existing.setObject(1,id);existing.setObject(2,customerLink);try(ResultSet done=existing.executeQuery()){if(done.next())return new Completed(id,null,0);}}
                throw new IllegalArgumentException("Upload session expired or unavailable.");
            }
            if(!java.util.Objects.equals(staff,r.getObject(9))||!java.util.Objects.equals(customerLink,r.getObject(10,UUID.class)))throw new IllegalArgumentException("Upload session expired or unavailable.");
            long order=r.getLong(1),line=r.getLong(2),expected=r.getLong(6);String kind=r.getString(3),name=r.getString(4),mime=r.getString(5),hash=r.getString(8);requireLine(c,order,line,locationId);
            Path staged=stage(id);if(expected!=r.getLong(7)||!Files.isRegularFile(staged)||Files.size(staged)!=expected)throw new IllegalArgumentException("The upload is incomplete or its checksum did not match.");
            String actualHash=sha256(staged);if(!hash.equals("0".repeat(64))&&!hash.equals(actualHash))throw new IllegalArgumentException("The upload checksum did not match.");hash=actualHash;verifySignature(staged,mime);
            UUID file=id;String key=file.toString();Path target=asset(key);Files.copy(staged,target,StandardCopyOption.REPLACE_EXISTING);SecureFilePermissions.restrictFileToOwner(target);
            String token=null;int revision=0;
            if("ATTACHMENT".equals(kind)){
                try(PreparedStatement insert=c.prepareStatement("INSERT INTO custom_order_files(file_id,custom_order_id,custom_order_line_id,filename,content_type,byte_size,sha256,storage_key,uploaded_by_user_id,uploaded_by_customer,override_by_user_id,override_reason) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")){insert.setObject(1,file);insert.setLong(2,order);insert.setLong(3,line);insert.setString(4,name);insert.setString(5,mime);insert.setLong(6,expected);insert.setString(7,hash);insert.setString(8,key);if(staff==null)insert.setNull(9,java.sql.Types.INTEGER);else insert.setInt(9,staff);insert.setBoolean(10,customerLink!=null);Object approver=r.getObject(11);if(approver==null)insert.setNull(11,java.sql.Types.INTEGER);else insert.setInt(11,(Integer)approver);insert.setString(12,r.getString(12));insert.executeUpdate();}
            }else{
                if(staff==null)throw new IllegalArgumentException("Only staff may submit a preview.");
                try(PreparedStatement update=c.prepareStatement("UPDATE custom_order_design_proofs SET status='SUPERSEDED',decided_at=now() WHERE custom_order_line_id=? AND status='AWAITING_APPROVAL'")){update.setLong(1,line);update.executeUpdate();}
                try(PreparedStatement next=c.prepareStatement("SELECT COALESCE(MAX(revision),0)+1 FROM custom_order_design_proofs WHERE custom_order_line_id=?")){next.setLong(1,line);try(ResultSet n=next.executeQuery()){n.next();revision=n.getInt(1);}}
                token=newToken();try(PreparedStatement insert=c.prepareStatement("INSERT INTO custom_order_design_proofs(proof_id,custom_order_id,custom_order_line_id,revision,filename,content_type,byte_size,sha256,storage_key,token_sha256,created_by_user_id) VALUES(?,?,?,?,?,?,?,?,?,?,?)")){insert.setObject(1,file);insert.setLong(2,order);insert.setLong(3,line);insert.setInt(4,revision);insert.setString(5,name);insert.setString(6,mime);insert.setLong(7,expected);insert.setString(8,hash);insert.setString(9,key);insert.setString(10,tokenHash(token));insert.setInt(11,staff);insert.executeUpdate();}
            }
            audit(c,order,"ATTACHMENT".equals(kind)?"ORDER_FILE_UPLOADED":"ORDER_PROOF_PUBLISHED",file.toString(),r.getString(12),staff,staff==null?"Customer private link":"Staff");
            try(PreparedStatement delete=c.prepareStatement("DELETE FROM custom_order_file_uploads WHERE upload_id=?")){delete.setObject(1,id);delete.executeUpdate();}
            return new Completed(file,token,revision);
        }}
    }

    private static void verifySignature(Path path,String mime)throws Exception{
        byte[] b;try(InputStream in=Files.newInputStream(path)){b=in.readNBytes(12);}boolean valid=switch(mime){
            case "image/jpeg"->b.length>=3&&(b[0]&255)==255&&(b[1]&255)==216&&(b[2]&255)==255;
            case "image/png"->b.length>=8&&(b[0]&255)==137&&b[1]==80&&b[2]==78&&b[3]==71;
            case "image/gif"->b.length>=6&&b[0]=='G'&&b[1]=='I'&&b[2]=='F';
            case "image/webp"->b.length>=12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P';
            case "application/pdf"->b.length>=5&&b[0]=='%'&&b[1]=='P'&&b[2]=='D'&&b[3]=='F'&&b[4]=='-';
            case "application/msword","application/vnd.ms-excel","application/vnd.ms-powerpoint"->b.length>=8&&(b[0]&255)==208&&(b[1]&255)==207&&(b[2]&255)==17&&(b[3]&255)==224;
            default->b.length>=4&&b[0]=='P'&&b[1]=='K';};
        if(!valid)throw new IllegalArgumentException("The file contents do not match its extension.");
        if(mime.contains("openxmlformats"))try(ZipFile zip=new ZipFile(path.toFile())){if(zip.getEntry("[Content_Types].xml")==null)throw new IllegalArgumentException("The Office file is invalid.");}
    }
    static void audit(Connection c,long orderId,String action,String value,String reason,Integer userId,String userName)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("INSERT INTO custom_order_audit_log(custom_order_id,action_type,field_name,new_value,reason,user_id,user_name) VALUES(?,?,'custom_order_media',?,?,?,?)")){
            p.setLong(1,orderId);p.setString(2,action);p.setString(3,value);p.setString(4,reason);if(userId==null)p.setNull(5,java.sql.Types.INTEGER);else p.setInt(5,userId);p.setString(6,userName);p.executeUpdate();
        }
    }

    public static boolean approvedOrNoProof(Connection c,long line)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT COUNT(*),COUNT(*) FILTER(WHERE status='APPROVED' AND deleted_at IS NULL AND decided_at>now()-interval '3 months') FROM custom_order_design_proofs WHERE custom_order_line_id=?")){p.setLong(1,line);try(ResultSet r=p.executeQuery()){r.next();return r.getLong(1)==0||r.getLong(2)>0;}}
    }
    public static AccessLink issueAccessLink(Connection c,long orderId,int locationId,int userId)throws Exception{
        try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM custom_orders WHERE custom_order_id=? AND location_id=?")){p.setLong(1,orderId);p.setInt(2,locationId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Order not found.");}}
        UUID id=UUID.randomUUID();String token=newToken();try(PreparedStatement p=c.prepareStatement("INSERT INTO custom_order_access_links(link_id,custom_order_id,token_sha256,created_by_user_id) VALUES(?,?,?,?)")){p.setObject(1,id);p.setLong(2,orderId);p.setString(3,tokenHash(token));p.setInt(4,userId);p.executeUpdate();}return new AccessLink(id,token);
    }
    public static long orderForAccessLink(Connection c,String token,int locationId)throws Exception{
        if(token==null||!token.matches("[A-Za-z0-9_-]{43}"))throw new IllegalArgumentException("Invalid order link.");
        try(PreparedStatement p=c.prepareStatement("SELECT l.custom_order_id FROM custom_order_access_links l JOIN custom_orders o ON o.custom_order_id=l.custom_order_id WHERE l.token_sha256=? AND l.revoked_at IS NULL AND o.location_id=?")){p.setString(1,tokenHash(token));p.setInt(2,locationId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Order link is no longer available.");return r.getLong(1);}}
    }
    public static UUID accessLinkId(Connection c,String token,int locationId)throws Exception{
        try(PreparedStatement p=c.prepareStatement("SELECT l.link_id FROM custom_order_access_links l JOIN custom_orders o ON o.custom_order_id=l.custom_order_id WHERE l.token_sha256=? AND l.revoked_at IS NULL AND o.location_id=?")){p.setString(1,tokenHash(token));p.setInt(2,locationId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("Order link is no longer available.");return r.getObject(1,UUID.class);}}
    }
    public static void revokeAccessLink(Connection c,UUID id,long orderId,int locationId)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("UPDATE custom_order_access_links SET revoked_at=now() WHERE link_id=? AND custom_order_id=? AND revoked_at IS NULL AND EXISTS(SELECT 1 FROM custom_orders WHERE custom_order_id=? AND location_id=?)")){p.setObject(1,id);p.setLong(2,orderId);p.setLong(3,orderId);p.setInt(4,locationId);if(p.executeUpdate()!=1)throw new IllegalArgumentException("Order link not found.");}
    }
    public static ProofDecision decide(Connection c,String token,int locationId,String action,String reason,String changes)throws Exception{
        if(!"APPROVE".equals(action)&&!"REJECT".equals(action))throw new IllegalArgumentException("Choose approve or reject.");
        reason=reason==null?"":reason.trim();changes=changes==null?"":changes.trim();
        if(reason.length()>1000||changes.length()>1000||("REJECT".equals(action)&&(reason.isBlank()||changes.isBlank())))throw new IllegalArgumentException("Enter both a reason and requested changes, each within 1,000 characters.");
        String feedback="REJECT".equals(action)?"Reason: "+reason+"\nRequested changes: "+changes:"";
        try(PreparedStatement p=c.prepareStatement("SELECT p.proof_id,p.status,p.custom_order_id FROM custom_order_design_proofs p JOIN custom_orders o ON o.custom_order_id=p.custom_order_id WHERE p.token_sha256=? AND p.revoked_at IS NULL AND o.location_id=? AND o.status NOT IN ('DELIVERED','CANCELLED') FOR UPDATE OF p")){p.setString(1,tokenHash(token));p.setInt(2,locationId);try(ResultSet r=p.executeQuery()){if(!r.next()||!"AWAITING_APPROVAL".equals(r.getString(2)))throw new IllegalArgumentException("This preview is no longer awaiting approval.");UUID id=r.getObject(1,UUID.class);long orderId=r.getLong(3);String status="APPROVE".equals(action)?"APPROVED":"CHANGES_REQUESTED";try(PreparedStatement update=c.prepareStatement("UPDATE custom_order_design_proofs SET status=?,feedback=?,decided_at=now() WHERE proof_id=?")){update.setString(1,status);update.setString(2,feedback);update.setObject(3,id);update.executeUpdate();}audit(c,orderId,"APPROVE".equals(action)?"ORDER_PROOF_APPROVED":"ORDER_PROOF_CHANGES_REQUESTED",id.toString(),feedback,null,"Customer private link");return new ProofDecision(id,status);}}
    }
    public static Media approvedPreview(Connection c,long orderId,long lineId,int locationId)throws SQLException{
        try(PreparedStatement p=c.prepareStatement("SELECT p.proof_id,p.filename,p.content_type,p.byte_size,p.storage_key FROM custom_order_design_proofs p JOIN custom_orders o ON o.custom_order_id=p.custom_order_id WHERE p.custom_order_id=? AND p.custom_order_line_id=? AND o.location_id=? AND p.status='APPROVED' AND p.deleted_at IS NULL AND p.decided_at>now()-interval '3 months' ORDER BY p.revision DESC LIMIT 1")){p.setLong(1,orderId);p.setLong(2,lineId);p.setInt(3,locationId);try(ResultSet r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("No approved design is available for this line.");return new Media(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getLong(4),r.getString(5));}}
    }
    public static Path localFile(String key)throws IOException{Path file=asset(key);if(Files.isRegularFile(file))return file;
        String expected=null;long size=0;
        try(Connection c=DB.getConnection();PreparedStatement p=c.prepareStatement("SELECT sha256,byte_size FROM custom_order_files WHERE storage_key=? AND deleted_at IS NULL UNION ALL SELECT sha256,byte_size FROM custom_order_design_proofs WHERE storage_key=? AND deleted_at IS NULL LIMIT 1")){p.setString(1,key);p.setString(2,key);try(ResultSet r=p.executeQuery()){if(r.next()){expected=r.getString(1);size=r.getLong(2);}}}catch(SQLException e){throw new IOException("Media metadata could not be read.",e);}
        if(expected==null)throw new IOException("The file is not available.");
        Path temp=Files.createTempFile(file.getParent(),".restore-",".part");try{
            if(ONEDRIVE.configured()&&ONEDRIVE.downloadPrivateFile(key,temp)){
                if(Files.size(temp)!=size||!expected.equals(sha256(temp)))
                    throw new IOException("The OneDrive recovery copy failed verification.");
                try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE);}catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(temp,file);}catch(java.nio.file.FileAlreadyExistsException ignored){}
                SecureFilePermissions.restrictFileToOwner(file);return file;
            }
            HttpRequest request=ServerSupabaseCredentials.applyTo(HttpRequest.newBuilder(URI.create(cloudUri(key).toString().replace("/storage/v1/object/","/storage/v1/object/authenticated/"))).timeout(Duration.ofMinutes(15)).GET()).build();
            HttpResponse<Path> response=HTTP.send(request,HttpResponse.BodyHandlers.ofFile(temp));
            if(response.statusCode()!=200||Files.size(temp)!=size||!expected.equals(sha256(temp)))throw new IOException("The recovery copy is unavailable or failed verification.");
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE);}catch(java.nio.file.AtomicMoveNotSupportedException e){Files.move(temp,file);}catch(java.nio.file.FileAlreadyExistsException ignored){}
            SecureFilePermissions.restrictFileToOwner(file);return file;
        }catch(Exception e){throw e instanceof IOException io?io:new IOException("The recovery copy could not be downloaded.",e);}finally{Files.deleteIfExists(temp);}
    }
    public static String cloudPath(String key){return "orders/"+key;}
    private static URI cloudUri(String key)throws IOException{return URI.create(SupabaseSessionManager.getSupabaseUrl()+"/storage/v1/object/"+BUCKET+"/"+URLEncoder.encode(cloudPath(key),java.nio.charset.StandardCharsets.UTF_8).replace("%2F","/"));}
    public static void mirror(String key,String mime)throws Exception{
        if(!ONEDRIVE.configured())throw new IOException("OneDrive private file storage is not configured on this server.");
        ONEDRIVE.uploadPrivateFile(key,localFile(key),mime);
    }
    public static void deleteCloud(String key)throws Exception{
        if(ONEDRIVE.configured())ONEDRIVE.deletePrivateFile(key);
        // Remove any legacy Supabase copy left by an earlier server version.
        HttpRequest request=ServerSupabaseCredentials.applyTo(HttpRequest.newBuilder(cloudUri(key)).timeout(Duration.ofMinutes(2)).DELETE()).build();HttpResponse<String> response=HTTP.send(request,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=404&&(response.statusCode()<200||response.statusCode()>=300))throw new IOException("Private recovery deletion failed with HTTP "+response.statusCode());
    }
}
