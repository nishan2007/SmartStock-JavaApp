package services;

import java.io.*;
import java.sql.*;
import javax.imageio.ImageIO;

/** Store-local durable review evidence. Callers enforce permissions and own transactions. */
final class ProductAdDraftStore {
    static final int MAX_BYTES=12*1024*1024;
    record Draft(byte[] png,String decision,long revision,String rejectionReason) {
        Draft(byte[] png,String decision,long revision){this(png,decision,revision,null);}
    }

    static java.util.List<LanApiClient.AdReviewItem> queue(Connection c,String decision) throws SQLException {
        if(!java.util.Set.of("pending","approved","rejected").contains(decision))throw new IllegalArgumentException("Choose a review status.");
        var rows=new java.util.ArrayList<LanApiClient.AdReviewItem>();
        try(var p=c.prepareStatement("""
                SELECT d.product_id,p.name,d.image_kind,d.decision,d.revision
                FROM product_ad_drafts d JOIN products p ON p.product_id=d.product_id
                WHERE d.decision=? AND p.is_active=TRUE AND p.sku<>'SMARTSTOCK-MISC'
                ORDER BY d.product_id,CASE d.image_kind WHEN 'ad_square' THEN 0
                  WHEN 'ad_portrait' THEN 1 WHEN 'ad_story' THEN 2 ELSE 3 END
                """)) {
            p.setString(1,decision);try(var r=p.executeQuery()) {
                while(r.next())rows.add(new LanApiClient.AdReviewItem(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getLong(5)));
            }
        }
        return java.util.List.copyOf(rows);
    }

    static byte[] canonical(String kind,byte[] bytes) throws IOException {
        if(bytes==null || bytes.length==0 || bytes.length>MAX_BYTES)
            throw new IllegalArgumentException("Choose an image under 12 MB.");
        // Inspect dimensions before allocating a decoded raster.
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IllegalArgumentException("Choose a readable image.");
            var reader=readers.next();
            try {
                reader.setInput(input);
                int width=reader.getWidth(0),height=reader.getHeight(0);
                if(width<=0 || height<=0 || width>8000 || height>8000 || (long)width*height>20_000_000)
                    throw new IllegalArgumentException("The image is too large (maximum 20 megapixels and 8000 pixels per side).");
                var image=reader.read(0);AdDraftRenderer.validate(kind,image);
                var output=new ByteArrayOutputStream();ImageIO.write(image,"png",output);
                if(output.size()>MAX_BYTES)throw new IllegalArgumentException("The PNG draft exceeds 12 MB.");
                return output.toByteArray();
            } finally {reader.dispose();}
        }
    }

    static void lockProduct(Connection c,long id) throws SQLException {
        try(var p=c.prepareStatement("SELECT product_id FROM products WHERE product_id=? AND is_active=TRUE AND sku<>'SMARTSTOCK-MISC' FOR UPDATE")) {
            p.setLong(1,id);try(var r=p.executeQuery()){if(!r.next())throw new IllegalArgumentException("The product is unavailable.");}
        }
    }
    static Draft load(Connection c,long id,String kind) throws SQLException {
        AdDraftRenderer.size(kind);
        try(var p=c.prepareStatement("SELECT preview_png,decision,revision,rejection_reason FROM product_ad_drafts WHERE product_id=? AND image_kind=?")) {
            p.setLong(1,id);p.setString(2,kind);
            try(var r=p.executeQuery()){return r.next()?new Draft(r.getBytes(1),r.getString(2),r.getLong(3),r.getString(4)):null;}
        }
    }
    static void check(Draft draft,long expected,boolean pending) {
        if((draft==null?0:draft.revision())!=expected || (pending&&(draft==null||!"pending".equals(draft.decision()))))
            throw new IllegalStateException("This draft changed on another register. Refresh and review it again.");
    }
    static long save(Connection c,long id,String kind,long expected,int user,byte[] png) throws SQLException {
        lockProduct(c,id);Draft current=load(c,id,kind);check(current,expected,false);
        try(var p=c.prepareStatement("""
                INSERT INTO product_ad_drafts(product_id,image_kind,preview_png,uploaded_by_user_id)
                VALUES(?,?,?,?) ON CONFLICT(product_id,image_kind) DO UPDATE SET
                preview_png=EXCLUDED.preview_png,uploaded_by_user_id=EXCLUDED.uploaded_by_user_id,
                decision='pending',rejection_reason=NULL,reviewed_by_user_id=NULL,revision=product_ad_drafts.revision+1,updated_at=now()
                RETURNING revision
                """)) {
            p.setLong(1,id);p.setString(2,kind);p.setBytes(3,png);p.setInt(4,user);
            try(var r=p.executeQuery()){r.next();return r.getLong(1);}
        }
    }
    static void decide(Connection c,long id,String kind,long revision,int user,String decision) throws SQLException {
        decide(c,id,kind,revision,user,decision,null);
    }
    static String rejectionReason(String reason) {
        String value=reason==null?"":reason.trim();
        if(value.isEmpty()||value.length()>2000)throw new IllegalArgumentException("Enter a rejection reason (1–2000 characters) so the ad can be corrected.");
        return value;
    }
    static void decide(Connection c,long id,String kind,long revision,int user,String decision,String reason) throws SQLException {
        if(!"approved".equals(decision)&&!"rejected".equals(decision))throw new IllegalArgumentException("Invalid decision.");
        String savedReason="rejected".equals(decision)?rejectionReason(reason):null;
        check(load(c,id,kind),revision,true);
        try(var p=c.prepareStatement("UPDATE product_ad_drafts SET decision=?,reviewed_by_user_id=?,revision=revision+1,updated_at=now(),rejection_reason=? WHERE product_id=? AND image_kind=? AND revision=? AND decision='pending'")) {
            p.setString(1,decision);p.setInt(2,user);p.setString(3,savedReason);p.setLong(4,id);p.setString(5,kind);p.setLong(6,revision);
            if(p.executeUpdate()!=1)throw new IllegalStateException("This draft changed. Refresh and review it again.");
        }
    }
}
