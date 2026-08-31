package services;

import data.DatabaseConfig;
import data.EnvironmentProfile;
import java.util.Properties;
import java.util.UUID;
import java.nio.file.Files;

/** Opt-in, single-owner authentication bridge for isolated scheduler acceptance testing. */
record SchedulerWebAuthConfig(SupabaseProjectConfig project,UUID productionSubject,UUID developmentSubject) {
    static SchedulerWebAuthConfig load() {
        if(!"true".equalsIgnoreCase(System.getenv("SMARTSTOCK_SCHEDULER_TEST_PRODUCTION_AUTH")))
            return new SchedulerWebAuthConfig(SupabaseProjectConfig.load(),null,null);
        requireIsolatedDevelopment(EnvironmentProfile.active(),DatabaseConfig.load().jdbcUrl());
        try {
            UUID production=UUID.fromString(System.getenv("SMARTSTOCK_SCHEDULER_TEST_PRODUCTION_SUBJECT"));
            UUID development=UUID.fromString(System.getenv("SMARTSTOCK_SCHEDULER_TEST_DEVELOPMENT_SUBJECT"));
            Properties saved=new Properties();
            try(var in=Files.newInputStream(EnvironmentProfile.PRODUCTION.file("supabase.properties"))){saved.load(in);}
            SupabaseProjectConfig project=SupabaseProjectConfig.resolveProduction(saved.getProperty("url"),saved.getProperty("publishable.key"));
            return new SchedulerWebAuthConfig(project,production,development);
        } catch(Exception e) {
            throw new IllegalStateException("Scheduler test authentication needs the explicit owner mapping and saved production public configuration.");
        }
    }
    static void requireIsolatedDevelopment(EnvironmentProfile profile,String jdbc) {
        if(profile!=EnvironmentProfile.DEVELOPMENT||jdbc==null||
                !jdbc.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/smartstock_dev(?:_[a-z0-9]+)?"))
            throw new IllegalStateException("Production-auth testing is allowed only with a loopback development database.");
    }
    UUID localSubject(UUID authenticatedSubject) {
        if(productionSubject==null)return authenticatedSubject;
        return productionSubject.equals(authenticatedSubject)?developmentSubject:null;
    }
}
