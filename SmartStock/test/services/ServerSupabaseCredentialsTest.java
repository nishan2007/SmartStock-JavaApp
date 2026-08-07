package services;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerSupabaseCredentialsTest {
    @Test
    void windowsDpapiScriptsLoadSecurityAssemblyAndStopOnErrors() throws Exception {
        String source = Files.readString(Path.of("src/services/ServerSupabaseCredentials.java"));

        assertTrue(source.contains("Add-Type -AssemblyName System.Security"));
        assertTrue(source.contains("$ErrorActionPreference='Stop'"));
        assertTrue(source.contains("Windows DPAPI returned no encrypted data."));
        assertTrue(source.contains("Windows DPAPI returned no decrypted data."));
    }

    @Test
    void acceptsOnlyConfiguredProjectServiceRoleJwt() {
        assertDoesNotThrow(() -> ServerSupabaseCredentials.validate(
                "sb_" + "secret_0123456789012345678901_01234567"));
        assertDoesNotThrow(() -> ServerSupabaseCredentials.validate(jwt("service_role",
                SupabaseProjectConfig.DEVELOPMENT_PROJECT_REF)));
        assertThrows(IllegalArgumentException.class,
                () -> ServerSupabaseCredentials.validate(jwt("anon",
                        SupabaseProjectConfig.DEVELOPMENT_PROJECT_REF)));
        assertThrows(IllegalArgumentException.class,
                () -> ServerSupabaseCredentials.validate(jwt("service_role", "another-project")));
        assertThrows(IllegalArgumentException.class,
                () -> ServerSupabaseCredentials.validate("sb_publishable_not-a-server-key"));
        assertThrows(IllegalArgumentException.class,
                () -> ServerSupabaseCredentials.validate("sb_secret_short"));
    }

    private static String jwt(String role, String ref) {
        return part("{\"alg\":\"HS256\",\"typ\":\"JWT\"}") + "."
                + part("{\"role\":\"" + role + "\",\"ref\":\"" + ref + "\"}") + ".signature";
    }

    private static String part(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
