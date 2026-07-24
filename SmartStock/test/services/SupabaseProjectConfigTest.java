package services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SupabaseProjectConfigTest {
    @Test
    void developmentRetainsExplicitlyScopedLegacyDefault() {
        SupabaseProjectConfig config = SupabaseProjectConfig.resolve(null, null, null);
        assertEquals(SupabaseProjectConfig.Environment.DEVELOPMENT, config.environment());
        assertEquals(SupabaseProjectConfig.DEVELOPMENT_PROJECT_REF, config.projectRef());
    }

    @Test
    void productionRequiresExplicitNonDevelopmentProject() {
        assertThrows(IllegalStateException.class,
                () -> SupabaseProjectConfig.resolve("production", null, null));
        assertThrows(IllegalStateException.class,
                () -> SupabaseProjectConfig.resolve("production",
                        SupabaseProjectConfig.DEVELOPMENT_URL, "production-key"));
    }

}
