package services;

import com.google.gson.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.util.*;
import java.time.Instant;
import java.nio.charset.StandardCharsets;

/** Authenticated encryption permits failover without exposing tokens to JavaScript. */
final class StorefrontSession {
    private StorefrontSession() { }
    static String seal(JsonObject value,byte[] key)throws Exception{
        byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        c.updateAAD("deckers-storefront-v1".getBytes(StandardCharsets.UTF_8));byte[] encrypted=c.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
        byte[] result=new byte[nonce.length+encrypted.length];System.arraycopy(nonce,0,result,0,12);System.arraycopy(encrypted,0,result,12,encrypted.length);return Base64.getUrlEncoder().withoutPadding().encodeToString(result);
    }
    static JsonObject open(String cookie,byte[] key)throws Exception{
        if(cookie.length()>16000)throw new IllegalArgumentException("Invalid session.");byte[] data=Base64.getUrlDecoder().decode(cookie);if(data.length<29)throw new IllegalArgumentException("Invalid session.");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOf(data,12)));c.updateAAD("deckers-storefront-v1".getBytes(StandardCharsets.UTF_8));
        var s=JsonParser.parseString(new String(c.doFinal(data,12,data.length-12),StandardCharsets.UTF_8)).getAsJsonObject();
        if(s.get("expires").getAsLong()<=Instant.now().getEpochSecond())throw new IllegalArgumentException("Session expired.");return s;
    }
}
