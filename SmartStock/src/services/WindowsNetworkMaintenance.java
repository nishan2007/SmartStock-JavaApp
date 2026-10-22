package services;

import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/** Shared installer/updater payload. Execution requires the existing elevated setup. */
public final class WindowsNetworkMaintenance {
    private WindowsNetworkMaintenance() { }

    static String installationScriptUnchecked(String scope) {
        try { return installationScript(scope); }
        catch (IOException ex) { throw new IllegalStateException(ex); }
    }

    public static String installationScript(String scope) throws IOException {
        String payload;
        try (InputStream input = WindowsNetworkMaintenance.class.getResourceAsStream("/windows/network-maintenance.ps1")) {
            if (input == null) throw new IOException("Windows network maintenance resource is missing.");
            payload = Base64.getEncoder().encodeToString(input.readAllBytes());
        }
        String argument = scope == null || scope.isBlank() ? "" : " -LanSubnet '" + scope.replace("'", "''") + "'";
        return "$maintenanceFile=Join-Path $env:TEMP ([guid]::NewGuid().ToString()+'.ps1');"
                + "try {[IO.File]::WriteAllBytes($maintenanceFile,[Convert]::FromBase64String('" + payload + "'));"
                + "& $maintenanceFile -Install" + argument + ";} finally {Remove-Item -LiteralPath $maintenanceFile -Force -ErrorAction SilentlyContinue;}";
    }
}
