package services;

import com.google.gson.*;
import data.DatabaseConfig;
import data.DatabaseMode;
import data.EnvironmentProfile;
import utils.SecureCredentialStore;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** Outbound, certificate-authenticated provisioning. Never distributes cloud DNS credentials. */
public final class StoreNetworkProvisioning {
    private static final Gson JSON=new Gson();
    private static final Path ROOT=Path.of(System.getProperty("user.home"),".smartstock","network-provisioning");
    private static final String ORIGIN="https://networking.deckers.gy";
    private static ScheduledExecutorService worker;
    private static Runnable refreshed=()->{};
    private static volatile Process provisioningProcess;
    private StoreNetworkProvisioning() { }
    static String remoteAdministratorInstaller()throws Exception{
        try(var input=StoreNetworkProvisioning.class.getResourceAsStream("/server-network/install-remote-admin.ps1")){
            if(input==null)throw new IllegalStateException("Missing remote administrator installer.");
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }

    public static synchronized void start(Runnable onRefresh) {
        if(worker!=null || !System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win"))return;
        refreshed=onRefresh;
        worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"smartstock-store-network");t.setDaemon(true);return t;});
        worker.scheduleWithFixedDelay(StoreNetworkProvisioning::reconcile,30,900,TimeUnit.SECONDS);
    }
    public static synchronized void close(){
        if(worker!=null){worker.shutdownNow();worker=null;}
        Process process=provisioningProcess;if(process!=null)process.destroyForcibly();
        fenceRemote();
    }
    static String assignedWebsite(){
        try {
            Integer location=DatabaseConfig.load().locationId();
            if(location==null)return null;
            Path file=ROOT.resolve("endpoints.json");
            if(!Files.isRegularFile(file)||Files.size(file)>8192)return null;
            JsonObject saved=JsonParser.parseString(Files.readString(file,StandardCharsets.UTF_8).replace("\uFEFF","")).getAsJsonObject();
            String expected="store-"+location+"-web.deckers.gy";
            return saved.has("remoteReady")&&saved.get("remoteReady").getAsBoolean()&&expected.equals(saved.get("websiteHost").getAsString())?"https://"+expected:null;
        }catch(Exception ignored){return null;}
    }
    public static void fenceRemote(){
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win"))return;
        try {
            Path code=Path.of(StoreNetworkProvisioning.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if(!Files.isRegularFile(code)||EnvironmentProfile.active()!=EnvironmentProfile.PRODUCTION||DatabaseConfig.load().mode()!=DatabaseMode.SERVER)return;
            var builder=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",
                "$ErrorActionPreference='Stop'; $t=Get-ScheduledTask -TaskName SmartStockManagedNetworkTunnel -ErrorAction SilentlyContinue; if($t){Stop-ScheduledTask -TaskName SmartStockManagedNetworkTunnel}; Get-CimInstance Win32_Process -Filter \"Name='cloudflared.exe'\" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains($env:SMARTSTOCK_NETWORK_DIRECTORY)} | ForEach-Object {Stop-Process -Id $_.ProcessId -Force}")
                .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.environment().put("SMARTSTOCK_NETWORK_DIRECTORY",ROOT.toString());
            Process p=builder.start();if(!p.waitFor(10,TimeUnit.SECONDS))p.destroyForcibly();
        }catch(InterruptedException e){Thread.currentThread().interrupt();}catch(Exception ignored){}
    }
    private static void reconcile(){
        try {
            if(EnvironmentProfile.active()!=EnvironmentProfile.PRODUCTION||DatabaseConfig.load().mode()!=DatabaseMode.SERVER||ServerRoleGuard.state()!=ServerRoleGuard.State.PRIMARY)return;
            Path jar=Path.of(StoreNetworkProvisioning.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if(!Files.isRegularFile(jar))return; // IDE/tests cannot publish DNS or install machine tasks.
            // Probe authorization before installing dependencies or changing any live settings.
            String address=selectAddress();
            verifyStaticAddress(address);
            var payload=new JsonObject();payload.addProperty("lanAddress",address);
            JsonObject manifest=call("/v1/provision",payload);
            Files.createDirectories(ROOT);
            protectDirectory();
            writeResource("SmartStock.ps1");writeResource("setup.ps1");
            var bootstrap=new JsonObject();bootstrap.addProperty("java",Path.of(System.getProperty("java.home"),"bin","java.exe").toString());
            bootstrap.addProperty("jar",jar.toString());bootstrap.addProperty("environment",EnvironmentProfile.active().id());
            bootstrap.addProperty("lanAddress",address);bootstrap.addProperty("lanHost",manifest.get("lanHost").getAsString());
            Files.writeString(ROOT.resolve("bootstrap.json"),JSON.toJson(bootstrap),StandardCharsets.UTF_8);
            Process process=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass",
                "-File",ROOT.resolve("setup.ps1").toString()).redirectErrorStream(true)
                .redirectOutput(ROOT.resolve("last-run.log").toFile()).start();
            provisioningProcess=process;
            if(!process.waitFor(8,TimeUnit.MINUTES)){process.destroyForcibly();throw new IllegalStateException("Network setup timed out.");}
            if(process.exitValue()!=0)throw new IllegalStateException("Network setup needs attention; see the server network setup log.");
            provisioningProcess=null;
            refreshed.run();
            boolean live=verifyBrowser(manifest.get("lanHost").getAsString());
            if(live)retireLegacyRenewal(address);
            Files.writeString(ROOT.resolve("status.txt"),(live?"Browser HTTPS verified at ":"Certificate prepared; browser HTTPS verification pending at ")+java.time.Instant.now());
        }catch(InterruptedException e){Process p=provisioningProcess;if(p!=null)p.destroyForcibly();Thread.currentThread().interrupt();}
        catch(Exception e){
            // A failed cloud operation cannot interrupt local sales or change server role.
            System.err.println("Store network provisioning will retry: "+e.getClass().getSimpleName());
        }
    }
    private static boolean verifyBrowser(String host){
        try {
            var request=HttpRequest.newBuilder(URI.create("https://"+host+":8444/mobile-management/")).timeout(Duration.ofSeconds(10)).GET().build();
            return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build()
                .send(request,HttpResponse.BodyHandlers.discarding()).statusCode()==200;
        }catch(InterruptedException e){Thread.currentThread().interrupt();return false;}catch(Exception e){return false;}
    }
    private static void retireLegacyRenewal(String address)throws Exception{
        String script="$t=Get-ScheduledTask -TaskName SmartStockLanCertificateRenewal -ErrorAction SilentlyContinue;"
            +"$needle=[string][char]34+$env:SMARTSTOCK_LEGACY_RENEWAL+[char]34;"
            +"if($t -and $t.Actions.Count -eq 1 -and (Split-Path $t.Actions[0].Execute -Leaf) -eq 'powershell.exe' "
            +"-and $t.Actions[0].Arguments.IndexOf($needle,[StringComparison]::OrdinalIgnoreCase) -ge 0 "
            +"-and $t.Actions[0].Arguments.Contains('-ServerAddress '+$env:SMARTSTOCK_SELECTED_LAN)){"
            +"$ErrorActionPreference='Stop';Disable-ScheduledTask -TaskName SmartStockLanCertificateRenewal | Out-Null;Stop-ScheduledTask -TaskName SmartStockLanCertificateRenewal}";
        var builder=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",script)
            .redirectError(ProcessBuilder.Redirect.DISCARD).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("SMARTSTOCK_SELECTED_LAN",address);
        builder.environment().put("SMARTSTOCK_LEGACY_RENEWAL",ROOT.getParent().resolve("acme/renew-lan-public-certificate.ps1").toString());
        Process process=builder.start();
        if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Legacy renewal migration timed out.");}
        if(process.exitValue()!=0)throw new IllegalStateException("Could not migrate the previous certificate renewal task.");
    }
    private static void verifyStaticAddress(String address)throws Exception{
        var builder=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-Command",
            "$a=Get-NetIPAddress -AddressFamily IPv4 | Where-Object IPAddress -eq $env:SMARTSTOCK_SELECTED_LAN; if(-not $a -or $a.PrefixOrigin -ne 'Manual'){exit 2}")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().put("SMARTSTOCK_SELECTED_LAN",address);
        Process process=builder.start();
        if(!process.waitFor(10,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Network address validation timed out.");}
        if(process.exitValue()!=0)throw new IllegalStateException("Configure a reserved static server address before publication.");
    }
    private static void writeResource(String name)throws Exception{
        try(var in=StoreNetworkProvisioning.class.getResourceAsStream("/server-network/"+name)){
            if(in==null)throw new IllegalStateException("Missing packaged network setup resource.");
            Files.copy(in,ROOT.resolve(name),StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private static void protectDirectory()throws Exception{
        String user=System.getenv("USERDOMAIN")+"\\"+System.getenv("USERNAME");
        Process p=new ProcessBuilder("icacls.exe",ROOT.toString(),"/inheritance:r","/grant:r",user+":(OI)(CI)F","*S-1-5-18:(OI)(CI)F")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        if(!p.waitFor(10,TimeUnit.SECONDS)){p.destroyForcibly();throw new IllegalStateException("Network directory protection timed out.");}
        if(p.exitValue()!=0)throw new IllegalStateException("Cannot protect the network setup directory.");
    }
    public record NetworkChoice(String address,String label){@Override public String toString(){return label;}}
    public static List<NetworkChoice> networkChoices()throws Exception{
        List<NetworkChoice> choices=new ArrayList<>();
        for(var iface:Collections.list(NetworkInterface.getNetworkInterfaces())){
            if(!iface.isUp()||iface.isLoopback()||iface.isVirtual())continue;
            for(var address:Collections.list(iface.getInetAddresses()))
                if(address instanceof Inet4Address&&address.isSiteLocalAddress())choices.add(new NetworkChoice(address.getHostAddress(),iface.getDisplayName()+" · "+address.getHostAddress()));
        }
        return List.copyOf(choices);
    }
    public static String savedAddress()throws Exception{
        Path settings=ROOT.resolve("network.properties");
        Properties properties=new Properties();
        if(Files.isRegularFile(settings))try(var in=Files.newInputStream(settings)){properties.load(in);}
        return properties.getProperty("lan.address","").trim();
    }
    public static void saveAddress(String address)throws Exception{
        if(networkChoices().stream().noneMatch(c->c.address().equals(address)))throw new IllegalArgumentException("Choose an address currently assigned to this server.");
        Files.createDirectories(ROOT);protectDirectory();
        Properties properties=new Properties();Path settings=ROOT.resolve("network.properties");
        if(Files.isRegularFile(settings))try(var in=Files.newInputStream(settings)){properties.load(in);}
        properties.setProperty("lan.address",address);
        try(var out=Files.newOutputStream(settings)){properties.store(out,"Selected store network");}
    }
    static String selectAddress()throws Exception{
        String selected=savedAddress();
        Set<String> candidates=new TreeSet<>();
        for(var choice:networkChoices())candidates.add(choice.address());
        if(!selected.isBlank()){
            if(!candidates.contains(selected))throw new IllegalStateException("Configured store network address is not assigned to this server.");
            return selected;
        }
        if(candidates.size()!=1)throw new IllegalStateException("Choose the store network address in Server Setup.");
        return candidates.iterator().next();
    }
    static String canonical(String path,String time,String nonce,String body)throws Exception{
        return "POST\n"+path+"\n"+time+"\n"+nonce+"\n"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
    }
    public static JsonObject call(String path,JsonObject fields)throws Exception{
        if(!Set.of("/v1/provision","/v1/dns/present","/v1/dns/cleanup").contains(path))throw new IllegalArgumentException("Unknown network provisioning operation.");
        DatabaseConfig config=DatabaseConfig.load();
        if(EnvironmentProfile.active()!=EnvironmentProfile.PRODUCTION)throw new IllegalStateException("Production network provisioning requires the production profile.");
        if(config.mode()!=DatabaseMode.SERVER||config.locationId()==null)throw new IllegalStateException("An assigned store server is required.");
        String instance=CloudServerRegistryService.currentInstanceId();
        if(instance==null)throw new IllegalStateException("The store server must be registered first.");
        fields.addProperty("locationId",config.locationId());fields.addProperty("serverInstanceId",instance);
        boolean managedRouting;
        try{LanApiServer.class.getMethod("refreshMobileWebIdentity");managedRouting=true;}
        catch(NoSuchMethodException oldServer){managedRouting=false;}
        fields.addProperty("managedRouting",managedRouting);
        String password=SecureCredentialStore.read("lan-api-keystore-password");
        if(password==null)throw new IllegalStateException("An existing server identity is required.");
        KeyStore store=KeyStore.getInstance("PKCS12");
        char[] chars=password.toCharArray();
        try {
            try(var in=Files.newInputStream(ROOT.getParent().resolve("lan-api.p12"))){store.load(in,chars);}
            var certificate=store.getCertificate("smartstock-lan");
            String body=JSON.toJson(fields),time=Long.toString(System.currentTimeMillis()),nonce=UUID.randomUUID().toString();
            Signature signer=Signature.getInstance("SHA256withRSA");
            signer.initSign((PrivateKey)store.getKey("smartstock-lan",chars));
            signer.update(canonical(path,time,nonce,body).getBytes(StandardCharsets.UTF_8));
            HttpRequest request=HttpRequest.newBuilder(URI.create(ORIGIN+path)).timeout(Duration.ofSeconds(90))
                .header("Content-Type","application/json").header("X-SmartStock-Time",time).header("X-SmartStock-Nonce",nonce)
                .header("X-SmartStock-Certificate",Base64.getEncoder().encodeToString(certificate.getEncoded()))
                .header("X-SmartStock-Signature",Base64.getEncoder().encodeToString(signer.sign()))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER)
                .build().send(request,HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IllegalStateException("Network provisioning returned HTTP "+response.statusCode()+".");
            if(response.body().length()>32768)throw new IllegalStateException("Invalid network provisioning response.");
            JsonObject result=JsonParser.parseString(response.body()).getAsJsonObject();
            if(path.equals("/v1/provision")){
                for(String kind:List.of("lan","web","admin")){
                    String field=kind.equals("web")?"websiteHost":kind+"Host";
                    String expected="store-"+config.locationId()+"-"+kind+".deckers.gy";
                    if(!result.has(field)||!expected.equals(result.get(field).getAsString()))throw new IllegalStateException("Unexpected assigned store hostname.");
                }
            }
            return result;
        }finally{Arrays.fill(chars,'\0');}
    }
    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("Expected a provisioning operation.");
        byte[] input=System.in.readNBytes(16385);
        if(input.length>16384)throw new IllegalArgumentException("Request too large.");
        JsonObject fields=JsonParser.parseString(new String(input,StandardCharsets.UTF_8)).getAsJsonObject();
        System.out.println(JSON.toJson(call(args[0],fields)));
    }
}
