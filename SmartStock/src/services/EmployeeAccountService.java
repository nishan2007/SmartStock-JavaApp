package services;

import java.sql.*;
import java.util.*;

/** Self-service projection: deliberately excludes all compensation and personnel documents. */
final class EmployeeAccountService {
    private EmployeeAccountService() {}

    static Map<String,Object> profile(Connection c,int userId)throws Exception {
        try(var p=c.prepareStatement("SELECT COALESCE(NULLIF(full_name,''),username),nickname,email,phone,employee_photo_url,employee_photo_frame FROM users WHERE user_id=? AND is_active=TRUE")) {
            p.setInt(1,userId);
            try(var r=p.executeQuery()) {
                if(!r.next())throw new SecurityException("Your employee account is unavailable.");
                Map<String,Object> out=new LinkedHashMap<>();
                out.put("name",Objects.toString(r.getString(1),""));
                out.put("nickname",Objects.toString(r.getString(2),""));
                out.put("email",Objects.toString(r.getString(3),""));
                out.put("phone",Objects.toString(r.getString(4),""));
                out.put("photo",thumbnail(r.getString(5),r.getString(6)!=null&&!r.getString(6).isBlank()));
                return out;
            }
        }
    }

    static String nickname(String value) {
        String clean=Objects.requireNonNull(value,"Nickname is required.").strip();
        if(clean.codePointCount(0,clean.length())>100||clean.codePoints().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Use up to 100 characters without control characters.");
        return clean;
    }

    static void saveNickname(Connection c,int userId,String value)throws SQLException {
        String clean=nickname(value);
        try(var p=c.prepareStatement("UPDATE users SET nickname=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND is_active=TRUE")) {
            p.setString(1,clean);p.setInt(2,userId);
            if(p.executeUpdate()!=1)throw new SecurityException("Your employee account is unavailable.");
        }
    }

    static String thumbnail(String reference,boolean framed) {
        if(reference==null||reference.isBlank())return "";
        try {
            var original=utils.ImageCacheManager.loadImage(reference);
            if(original==null)return "";
            var image=EmployeePhotoFraming.avatar(original,320,framed,false);
            var bytes=new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image,"jpg",bytes);
            return "data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(bytes.toByteArray());
        }catch(Exception ignored){return "";}
    }
}
