package services;

import data.DatabaseConfig;
import data.EnvironmentProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Opt-in: reads development only; all restore/migration writes use a newly created scratch database. */
class SchedulerMigrationRecoveryIntegrationTest {
    @Test void developmentBackupRestoresAndSchedulerMigrationPreservesBusinessData(@TempDir Path temp) throws Exception {
        assumeTrue(Boolean.getBoolean("smartstock.test.schedulerRecovery"));
        assertEquals(EnvironmentProfile.DEVELOPMENT,EnvironmentProfile.active());
        DatabaseConfig cfg=DatabaseConfig.load();
        assertEquals("jdbc:postgresql://127.0.0.1:5432/smartstock_dev_v1",cfg.jdbcUrl(),"Never run against production or a remote host");
        Path bin=Path.of(System.getProperty("smartstock.test.pgBin","C:/Program Files/PostgreSQL/17/bin"));
        Path dump=temp.resolve("development.dump");
        utils.SecureFilePermissions.restrictDirectoryToOwner(temp);
        pg(bin.resolve("pg_dump.exe"),cfg,temp.resolve("dump.log"),"--format=custom","--no-owner","--no-acl","--file="+dump,"--dbname=smartstock_dev_v1");
        assertTrue(Files.size(dump)>0);
        String scratch="smartstock_scheduler_test_"+UUID.randomUUID().toString().replace("-","");
        assertTrue(scratch.matches("smartstock_scheduler_test_[a-f0-9]{32}"));
        try(Connection admin=DriverManager.getConnection(cfg.jdbcUrl(),cfg.dbUser(),cfg.dbPassword())) {
            try(Statement s=admin.createStatement()){s.executeUpdate("CREATE DATABASE "+scratch);}
            try {
                pg(bin.resolve("pg_restore.exe"),cfg,temp.resolve("restore.log"),"--exit-on-error","--no-owner","--no-acl","--dbname="+scratch,dump.toString());
                try(Connection c=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/"+scratch,cfg.dbUser(),cfg.dbPassword())) {
                    Map<String,String> before=businessFingerprints(c);
                    assertFalse(before.isEmpty());
                    String migration=Files.readString(Path.of("database/migrations/v1_after/20260828190000_scheduler_web_app.sql"));
                    c.setAutoCommit(false);
                    SqlScriptRunner.runSql(c,migration);c.rollback();
                    assertEquals(before,businessFingerprints(c),"Rollback must preserve every business row");
                    SqlScriptRunner.runSql(c,migration);c.commit();
                    SqlScriptRunner.runSql(c,migration);c.commit();
                    assertEquals(before,businessFingerprints(c),"Migration and replay must preserve every business row");
                    System.out.println("Scheduler recovery rehearsal: development backup restored; "+before.size()+" business tables unchanged after migration, rollback and replay.");
                }
            } finally {
                // Only the exact random database created by this test is ever dropped.
                try(Statement s=admin.createStatement()){s.executeUpdate("DROP DATABASE "+scratch);}
            }
        }
    }
    private static Map<String,String> businessFingerprints(Connection c)throws Exception {
        Map<String,String> result=new TreeMap<>();List<String> tables=new ArrayList<>();
        try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")) {
            while(r.next())tables.add(r.getString(1));
        }
        for(String table:tables) {
            if(table.startsWith("scheduler_web_")||Set.of("mobile_permissions","role_mobile_permissions").contains(table))continue;
            String quoted="\""+table.replace("\"","\"\"")+"\"";
            try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT count(*)::text||':'||md5(COALESCE(string_agg(v,E'\\n' ORDER BY v),'')) FROM (SELECT row_to_json(t)::text AS v FROM public."+quoted+" t) q")) {
                r.next();result.put(table,r.getString(1));
            }
        }
        return result;
    }
    private static void pg(Path exe,DatabaseConfig cfg,Path log,String... args)throws Exception {
        List<String> command=new ArrayList<>(List.of(exe.toString(),"--host=127.0.0.1","--port=5432","--username="+cfg.dbUser(),"--no-password"));command.addAll(List.of(args));
        ProcessBuilder builder=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("PGPASSWORD",cfg.dbPassword());
        Process process=builder.start();
        if(!process.waitFor(60,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("PostgreSQL backup/restore timed out");}
        assertEquals(0,process.exitValue(),"PostgreSQL backup/restore failed; inspect private temporary log");
    }
}
