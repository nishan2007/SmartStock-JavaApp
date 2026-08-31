package services;

import data.EnvironmentProfile;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerWebAuthConfigTest {
    @Test void bridgeCannotUseProductionOrRemoteDatabase() {
        assertThrows(IllegalStateException.class,()->SchedulerWebAuthConfig.requireIsolatedDevelopment(EnvironmentProfile.PRODUCTION,"jdbc:postgresql://127.0.0.1:5432/smartstock_dev_v1"));
        for(String url:new String[]{"jdbc:postgresql://127.0.0.1:5432/smartstock","jdbc:postgresql://remote:5432/smartstock_dev","jdbc:postgresql://127.0.0.1:5432/smartstock_dev?host=remote",""})
            assertThrows(IllegalStateException.class,()->SchedulerWebAuthConfig.requireIsolatedDevelopment(EnvironmentProfile.DEVELOPMENT,url));
        SchedulerWebAuthConfig.requireIsolatedDevelopment(EnvironmentProfile.DEVELOPMENT,"jdbc:postgresql://127.0.0.1:5432/smartstock_dev_v1");
    }
    @Test void bridgeAcceptsOnlyExplicitlyMappedOwner() {
        UUID production=UUID.randomUUID(),development=UUID.randomUUID();
        var config=new SchedulerWebAuthConfig(null,production,development);
        assertEquals(development,config.localSubject(production));
        assertNull(config.localSubject(development));
        assertNull(config.localSubject(UUID.randomUUID()));
    }
    @Test void normalAuthenticationPreservesSubject() {
        UUID subject=UUID.randomUUID();
        assertEquals(subject,new SchedulerWebAuthConfig(null,null,null).localSubject(subject));
    }
}
