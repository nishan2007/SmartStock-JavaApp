package services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

record StorefrontConfig(String origin,String edgeKey,byte[] sessionKey,int port,String instanceId,boolean browseOnly) {
    static boolean browseOnly(String value){return !"false".equals(value);}
    static StorefrontConfig load(){
        String origin=System.getenv("SMARTSTOCK_STOREFRONT_ORIGIN");
        String key=System.getenv("SMARTSTOCK_STOREFRONT_EDGE_KEY");
        String sessionValue=System.getenv("SMARTSTOCK_STOREFRONT_SESSION_KEY");
        if(origin==null||origin.isBlank()){
            Path protectedFile=Path.of(System.getProperty("user.home"),".smartstock","storefront-tunnel","website-config.dpapi");
            if(!Files.isRegularFile(protectedFile))return null;
            JsonObject saved=readProtectedSettings(protectedFile);
            origin=saved.get("origin").getAsString();key=saved.get("edgeKey").getAsString();sessionValue=saved.get("sessionKey").getAsString();
        }
        URI u=URI.create(origin);if(!"https".equals(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!(u.getPath().isEmpty()||u.getPath().equals("/")))throw new IllegalArgumentException("Storefront origin must be an HTTPS origin.");
        if(key==null||key.length()<32)throw new IllegalArgumentException("Configure the storefront gateway secret.");
        if(sessionValue==null)throw new IllegalArgumentException("Configure the storefront session key.");
        byte[] session=Base64.getDecoder().decode(sessionValue);if(session.length!=32)throw new IllegalArgumentException("Storefront session key must contain 32 random bytes encoded as Base64.");
        String instance=CloudServerRegistryService.currentInstanceId();
        if(instance==null)throw new IllegalArgumentException("Register this store server before enabling the storefront.");
        return new StorefrontConfig(origin.replaceAll("/$",""),key,session,8449,java.util.UUID.fromString(instance).toString(),
                browseOnly(System.getenv("SMARTSTOCK_STOREFRONT_BROWSE_ONLY")));
    }

    static JsonObject readProtectedSettings(Path file){
        if(!System.getProperty("os.name","").toLowerCase(java.util.Locale.ROOT).contains("windows"))throw new IllegalStateException("Protected website settings require Windows.");
        String script="$ErrorActionPreference='Stop';Add-Type -AssemblyName System.Security;"
                +"$bytes=[Security.Cryptography.ProtectedData]::Unprotect([Convert]::FromBase64String((Get-Content -Raw -LiteralPath $env:SMARTSTOCK_WEBSITE_CONFIG_FILE).Trim()),$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);"
                +"try{$settings=[Text.Encoding]::UTF8.GetString($bytes)|ConvertFrom-Json;"
                +"[Console]::Out.Write((@{origin=$settings.origin;edgeKey=$settings.edgeKey;sessionKey=$settings.sessionKey}|ConvertTo-Json -Compress))}"
                +"finally{[Array]::Clear($bytes,0,$bytes.Length)}";
        String systemRoot=System.getenv("SystemRoot");
        if(systemRoot==null||systemRoot.isBlank())throw new IllegalStateException("Windows PowerShell is unavailable.");
        Path powershell=Path.of(systemRoot,"System32","WindowsPowerShell","v1.0","powershell.exe");
        if(!Files.isRegularFile(powershell))throw new IllegalStateException("Windows PowerShell is unavailable.");
        Process process=null;
        try{
            ProcessBuilder command=new ProcessBuilder(powershell.toString(),"-NoProfile","-NonInteractive","-ExecutionPolicy","Bypass","-Command",script);
            command.environment().put("SMARTSTOCK_WEBSITE_CONFIG_FILE",file.toAbsolutePath().toString());
            command.redirectError(ProcessBuilder.Redirect.DISCARD);
            process=command.start();
            if(!process.waitFor(15,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("Protected website settings timed out.");}
            byte[] output=process.getInputStream().readNBytes(4097);
            try{
                if(process.exitValue()!=0||output.length>4096)throw new IllegalStateException("Protected website settings could not be loaded.");
                JsonObject saved=JsonParser.parseString(new String(output,StandardCharsets.UTF_8)).getAsJsonObject();
                if(!saved.has("origin")||!saved.has("edgeKey")||!saved.has("sessionKey"))throw new IllegalStateException("Protected website settings are incomplete.");
                return saved;
            }finally{java.util.Arrays.fill(output,(byte)0);}
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Protected website settings were interrupted.",e);}
        catch(java.io.IOException e){throw new IllegalStateException("Protected website settings could not be loaded.",e);}
        finally{if(process!=null&&process.isAlive())process.destroyForcibly();}
    }
}
