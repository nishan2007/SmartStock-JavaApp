package services;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import java.security.SecureRandom;
import java.util.*;

/** Versioned Argon2id hashes. No plaintext password or recoverable password is persisted. */
final class CustomerWebPassword {
    private CustomerWebPassword() { }
    static String hash(String password){
        validate(password);byte[] salt=new byte[16];new SecureRandom().nextBytes(salt);byte[] hash=derive(password,salt);
        try{return "argon2id$v=19$m=19456,t=2,p=1$"+Base64.getEncoder().withoutPadding().encodeToString(salt)+"$"+Base64.getEncoder().withoutPadding().encodeToString(hash);}finally{Arrays.fill(hash,(byte)0);}
    }
    static boolean verify(String password,String encoded){
        if(password==null||password.length()>128)return false;
        try{String[] parts=encoded.split("\\$");if(parts.length!=5||!parts[0].equals("argon2id")||!parts[1].equals("v=19")||!parts[2].equals("m=19456,t=2,p=1"))return false;byte[] salt=Base64.getDecoder().decode(parts[3]),expected=Base64.getDecoder().decode(parts[4]);if(salt.length!=16||expected.length!=32)return false;byte[] actual=derive(password,salt);try{return java.security.MessageDigest.isEqual(actual,expected);}finally{Arrays.fill(actual,(byte)0);Arrays.fill(expected,(byte)0);}}catch(RuntimeException e){return false;}
    }
    static void validate(String password){if(password==null||password.length()<12||password.length()>128)throw new IllegalArgumentException("Use a password with 12 to 128 characters.");}
    private static byte[] derive(String password,byte[] salt){
        var params=new Argon2Parameters.Builder(Argon2Parameters.ARGON2_id).withVersion(Argon2Parameters.ARGON2_VERSION_13).withMemoryAsKB(19456).withIterations(2).withParallelism(1).withSalt(salt).build();
        var generator=new Argon2BytesGenerator();generator.init(params);byte[] output=new byte[32];char[] chars=password.toCharArray();try{generator.generateBytes(chars,output);return output;}finally{Arrays.fill(chars,'\0');params.clear();}
    }
}
