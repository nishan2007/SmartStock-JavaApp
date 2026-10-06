package gy.deckers.studio;

import services.AppUpdateService.AppRelease;
import services.LanApiClient;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

/** SmartStudio-only release checks and verified, per-user Windows installer handoff. */
final class StudioUpdates {
    static String currentVersion() throws IOException {
        var properties=new java.util.Properties();
        try(var stream=StudioUpdates.class.getResourceAsStream("version.properties")) {
            if(stream==null)throw new IOException("SmartStudio version information is missing.");
            properties.load(stream);
        }
        return properties.getProperty("version");
    }
    static boolean newer(String next,String current) {
        if(next==null||current==null||!next.matches("\\d+\\.\\d+\\.\\d+")||!current.matches("\\d+\\.\\d+\\.\\d+"))
            throw new IllegalArgumentException("Invalid SmartStudio version.");
        String[] a=next.split("\\."),b=current.split("\\.");
        for(int i=0;i<3;i++){int diff=new java.math.BigInteger(a[i]).compareTo(new java.math.BigInteger(b[i]));if(diff!=0)return diff>0;}
        return false;
    }
    static void validate(AppRelease release) throws IOException {
        if(release==null||!"windows".equals(release.platform())||release.artifactPath()==null
                ||!release.artifactPath().startsWith("smartstudio/windows/")||!release.artifactPath().endsWith(".exe")
                ||release.artifactPath().contains("..")||release.artifactPath().contains("\\")
                ||release.sha256()==null||!release.sha256().matches("[a-fA-F0-9]{64}")
                ||release.fileSizeBytes()<1||release.fileSizeBytes()>500L*1024*1024)
            throw new IOException("This release is not a valid SmartStudio Windows update.");
        newer(release.version(),"0.0.0");
    }
    static AppRelease check() throws Exception {
        if(!System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("windows"))
            throw new IOException("SmartStudio's in-app installer currently supports Windows.");
        AppRelease release=LanApiClient.loadLatestStudioRelease();
        if(release==null)return null;
        validate(release);return newer(release.version(),currentVersion())?release:null;
    }
    static Path download(AppRelease release) throws Exception {
        validate(release);
        URI url=URI.create(LanApiClient.createUpdateDownloadUrl(release.artifactBucket(),release.artifactPath()));
        if(!"https".equalsIgnoreCase(url.getScheme())||url.getHost()==null||url.getUserInfo()!=null)
            throw new IOException("The update download must use HTTPS.");
        Path directory=Path.of(System.getProperty("user.home"),"updates");Files.createDirectories(directory);
        Path partial=Files.createTempFile(directory,"smartstudio-", ".partial");
        try {
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).followRedirects(HttpClient.Redirect.NEVER).build();
            var reply=client.send(HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(10)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            try(var input=reply.body();var output=Files.newOutputStream(partial)) {
                if(reply.statusCode()!=200)throw new IOException("The update could not be downloaded. Try again later.");
                var hash=MessageDigest.getInstance("SHA-256");long total=0;byte[] buffer=new byte[65536];
                for(int count;(count=input.read(buffer))!=-1;) {
                    total+=count;if(total>release.fileSizeBytes())throw new IOException("The update download is larger than expected.");
                    hash.update(buffer,0,count);output.write(buffer,0,count);
                }
                if(total!=release.fileSizeBytes()||!HexFormat.of().formatHex(hash.digest()).equalsIgnoreCase(release.sha256()))
                    throw new IOException("The update failed its size or integrity check. It was not installed.");
            }
            Path installer=directory.resolve("SmartStudio-setup-"+release.version()+".exe");
            Files.move(partial,installer,StandardCopyOption.REPLACE_EXISTING);return installer;
        } finally {Files.deleteIfExists(partial);}
    }
    static void verifyInstaller(Path installer, AppRelease release) throws Exception {
        validate(release);
        if(Files.size(installer)!=release.fileSizeBytes())throw new IOException("The downloaded installer size changed.");
        var digest=MessageDigest.getInstance("SHA-256");
        try(var input=Files.newInputStream(installer)){byte[] buffer=new byte[65536];for(int n;(n=input.read(buffer))!=-1;)digest.update(buffer,0,n);}
        if(!HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(release.sha256()))
            throw new IOException("The downloaded installer failed its integrity check.");
    }
    static void install(Path installer, AppRelease release) throws Exception {
        verifyInstaller(installer,release);
        Path jar=Path.of(StudioUpdates.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path root=jar.getParent()==null?null:jar.getParent().getParent();
        if(!Files.isRegularFile(jar)||root==null||!Files.isRegularFile(root.resolve("SmartStudio.exe")))
            throw new IOException("Install the desktop version of SmartStudio before using in-app installation.");
        // Inno Setup handles replacement, existing uninstall registration and shortcut preservation.
        new ProcessBuilder(installer.toString(),"/SILENT","/CLOSEAPPLICATIONS","/RESTARTAPPLICATIONS","/REOPENSTUDIO","/DIR="+root).start();
    }
}
