package services;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;

/** Imports an ACME chain into the browser identity without touching register TLS. */
public final class MobileWebCertificateImporter {
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected source PFX, destination P12, DNS hostname.");
        String host = args[2];
        if (!host.matches("(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+deckers\\.gy"))
            throw new IllegalArgumentException("Expected a Deckers DNS hostname.");
        String line = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine();
        if (line == null || line.isBlank()) throw new IllegalArgumentException("Missing PFX password on standard input.");
        char[] password = line.toCharArray();
        Path destination = Path.of(args[1]).toAbsolutePath();
        if (!destination.getFileName().toString().equals("mobile-web-" + host + ".p12"))
            throw new IllegalArgumentException("Destination must be the separate browser keystore.");
        Path temporary = null;
        try {
            KeyStore source = KeyStore.getInstance("PKCS12");
            try (var input = Files.newInputStream(Path.of(args[0]))) { source.load(input, password); }
            var aliases = Collections.list(source.aliases()).stream().filter(a -> {
                try { return source.isKeyEntry(a); } catch (Exception e) { throw new IllegalStateException(e); }
            }).toList();
            if (aliases.size() != 1) throw new IllegalArgumentException("PFX must contain exactly one private key.");
            String alias = aliases.get(0);
            var chain = source.getCertificateChain(alias);
            if (chain == null || chain.length < 2) throw new IllegalArgumentException("A complete CA-issued chain is required.");
            var leaf = (X509Certificate) chain[0];
            leaf.checkValidity();
            if (leaf.getBasicConstraints() >= 0) throw new IllegalArgumentException("Expected a server certificate.");
            var names = leaf.getSubjectAlternativeNames();
            if (names == null || names.stream().noneMatch(n -> Integer.valueOf(2).equals(n.get(0)) && host.equalsIgnoreCase(String.valueOf(n.get(1)))))
                throw new IllegalArgumentException("Certificate does not cover the configured hostname.");
            var usage = leaf.getExtendedKeyUsage();
            if (usage == null || !usage.contains("1.3.6.1.5.5.7.3.1"))
                throw new IllegalArgumentException("Certificate must permit TLS server authentication.");
            for (int i = 1; i < chain.length; i++) {
                var issuer = (X509Certificate) chain[i];
                issuer.checkValidity();
                if (issuer.getBasicConstraints() < 0) throw new IllegalArgumentException("Invalid issuer certificate.");
                chain[i - 1].verify(issuer.getPublicKey());
            }
            KeyStore target = KeyStore.getInstance("PKCS12");
            target.load(null, password);
            target.setKeyEntry("smartstock-lan", source.getKey(alias, password), password, chain);
            temporary = Files.createTempFile(destination.getParent(), "mobile-web-import-", ".p12");
            try (var output = Files.newOutputStream(temporary)) { target.store(output, password); }
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            System.out.println("Imported browser certificate for " + host + "; expires " + leaf.getNotAfter().toInstant());
        } finally {
            Arrays.fill(password, '\0');
            if (temporary != null) Files.deleteIfExists(temporary);
        }
    }
}

