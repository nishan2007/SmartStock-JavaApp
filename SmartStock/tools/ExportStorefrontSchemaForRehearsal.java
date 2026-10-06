// Read-only production schema export for an isolated storefront upgrade rehearsal.
// Run with -Dsmartstock.environment=production from the repository root.
import data.DatabaseConfig;
import data.DatabaseMode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

class ExportStorefrontSchemaForRehearsal {
    public static void main(String[] args) throws Exception {
        if (!"production".equals(System.getProperty("smartstock.environment")))
            throw new IllegalStateException("Explicit production profile required");
        DatabaseConfig config = DatabaseConfig.load();
        if (config.mode() != DatabaseMode.SERVER || !config.jdbcUrl().matches(
                "jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/[A-Za-z0-9_]+"))
            throw new IllegalStateException("Expected local server database");
        URI url = new URI(config.jdbcUrl().substring(5));
        Path output = Path.of("SmartStock", "target", "storefront-live-schema-rehearsal.sql")
                .toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        ProcessBuilder builder = new ProcessBuilder(
                "C:\\Program Files\\PostgreSQL\\17\\bin\\pg_dump.exe",
                "--schema-only", "--no-owner", "--no-privileges", "--no-password",
                "--host", url.getHost(), "--port", Integer.toString(url.getPort()),
                "--username", config.dbUser(), "--dbname", url.getPath().substring(1),
                "--file", output.toString());
        builder.environment().put("PGPASSWORD", config.dbPassword());
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Schema-only export timed out");
        }
        if (process.exitValue() != 0)
            throw new IllegalStateException("Schema-only export failed with exit " + process.exitValue());
        if (!Files.isRegularFile(output) || Files.size(output) == 0)
            throw new IllegalStateException("Schema-only export is empty");
        System.out.println("Schema-only copy saved to ignored local build output.");
    }
}
