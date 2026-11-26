package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class EmployeeAccountServiceTest {
    @Test void nicknamesSupportRemovalAndRejectControlCharacters() {
        assertEquals("Jamie",EmployeeAccountService.nickname(" Jamie "));
        assertEquals("",EmployeeAccountService.nickname("   "));
        assertThrows(IllegalArgumentException.class,()->EmployeeAccountService.nickname("a\nb"));
        assertThrows(IllegalArgumentException.class,()->EmployeeAccountService.nickname("a".repeat(101)));
        assertEquals("a".repeat(100),EmployeeAccountService.nickname("a".repeat(100)));
    }

    @Test void passwordWhitespaceIsPartOfTheSecret()throws Exception {
        JsonObject body=new JsonObject();body.addProperty("currentPassword","  secret  ");
        assertEquals("  secret  ",EmployeeWebServer.accountPassword(body,"currentPassword"));
        assertThrows(EmployeeWebServer.WebError.class,()->EmployeeWebServer.accountPassword(body,"newPassword"));
        body.addProperty("newPassword",false);
        assertThrows(EmployeeWebServer.WebError.class,()->EmployeeWebServer.accountPassword(body,"newPassword"));
    }

    @Test void selfServiceDoesNotRequireUnrelatedAppPermissions() {
        for(String route:Set.of("account/profile","account/nickname","account/password")) {
            assertDoesNotThrow(()->EmployeeWebServer.requireRouteAccess(route,Set.of("ACCESS_SCHEDULER")));
            assertFalse(MobileDeviceService.routeApps("employee",route).isEmpty());
        }
        assertThrows(SecurityException.class,()->EmployeeWebServer.requireRouteAccess("employees",Set.of()));
    }

    @Test void profileWhitelistsFieldsAndNicknameUpdateCannotTargetAnotherEmployee()throws Exception {
        String url=System.getProperty("smartstock.account.test.jdbc","");assumeTrue(!url.isBlank());
        if(!Set.of("jdbc:postgresql://127.0.0.1:56561/postgres","jdbc:postgresql://127.0.0.1:56563/postgres").contains(url))throw new IllegalArgumentException("Use disposable account cluster.");
        try(Connection c=DriverManager.getConnection(url,"postgres","");Statement st=c.createStatement()) {
            st.execute("CREATE TEMP TABLE users(user_id int PRIMARY KEY,username text,full_name text,nickname text,email text,phone text,employee_photo_url text,is_active boolean,updated_at timestamptz,salary numeric)");
            st.execute("INSERT INTO users VALUES(1,'one','Employee One','First','one@example.com','123',NULL,TRUE,now(),99999),(2,'two','Employee Two','Second',NULL,NULL,NULL,TRUE,now(),12345)");
            st.execute("ALTER TABLE users ADD COLUMN employee_photo_frame text, ADD COLUMN employee_photo_source_url text");
            var profile=EmployeeAccountService.profile(c,1);
            assertEquals(Set.of("name","nickname","email","phone","photo"),profile.keySet());
            assertEquals("",profile.get("photo"));
            var photoFile=java.nio.file.Files.createTempFile("account-photo-",".png");
            try {
                var image=new java.awt.image.BufferedImage(640,480,java.awt.image.BufferedImage.TYPE_INT_RGB);
                javax.imageio.ImageIO.write(image,"png",photoFile.toFile());
                try(var p=c.prepareStatement("UPDATE users SET employee_photo_url=? WHERE user_id=1")){p.setString(1,photoFile.toString());p.executeUpdate();}
                String photo=EmployeeAccountService.profile(c,1).get("photo").toString();
                assertTrue(photo.startsWith("data:image/jpeg;base64,"));
                var thumbnail=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(photo.substring(photo.indexOf(',')+1))));
                assertEquals(320,thumbnail.getWidth());assertEquals(320,thumbnail.getHeight());
            }finally{java.nio.file.Files.deleteIfExists(photoFile);}
            EmployeeAccountService.saveNickname(c,1,"New nickname");
            assertEquals("New nickname",EmployeeAccountService.profile(c,1).get("nickname"));
            assertEquals("Second",EmployeeAccountService.profile(c,2).get("nickname"));
            EmployeeAccountService.saveNickname(c,1,"");
            assertEquals("",EmployeeAccountService.profile(c,1).get("nickname"));
            st.execute("UPDATE users SET is_active=FALSE WHERE user_id=1");
            assertThrows(SecurityException.class,()->EmployeeAccountService.profile(c,1));
            assertThrows(SecurityException.class,()->EmployeeAccountService.saveNickname(c,1,"Denied"));
        }
    }
}
