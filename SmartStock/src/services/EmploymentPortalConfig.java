package services;

import data.EnvironmentProfile;
import java.net.URI;
import java.nio.file.Files;
import java.util.Properties;

/** Machine-local public origin. The listener is loopback-only behind a trusted proxy. */
public final class EmploymentPortalConfig {
    private EmploymentPortalConfig() { }
    public static String origin() {
        String value=System.getenv("SMARTSTOCK_APPLICATION_PUBLIC_ORIGIN");
        if(value==null||value.isBlank()) {
            var path=EnvironmentProfile.active().file("applications.properties");
            Properties p=new Properties();
            try { if(Files.exists(path))try(var in=Files.newInputStream(path)){p.load(in);} }
            catch(Exception e){throw new IllegalStateException("Could not read applications.properties.");}
            value=p.getProperty("public.origin");
        }
        return validateOrigin(value);
    }
    static String validateOrigin(String value) {
        try {
            URI u=URI.create(value==null?"":value.trim());
            if(!"https".equals(u.getScheme())||u.getHost()==null||u.getRawUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null
                    ||!(u.getPath().isEmpty()||"/".equals(u.getPath())))throw new IllegalArgumentException();
            return "https://"+u.getRawAuthority();
        }catch(Exception e){throw new IllegalStateException("Set public.origin to a permanent HTTPS origin in applications.properties before starting the application portal.");}
    }
}
