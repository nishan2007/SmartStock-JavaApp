package services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Reads the current Windows Location Services position without storing it. */
public final class ComputerLocationService {
    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    private ComputerLocationService() { }

    public static Position current() throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            throw new UnsupportedOperationException("Current-location lookup is currently available on Windows only.");
        }
        String script = "Add-Type -AssemblyName System.Runtime.WindowsRuntime;"
                + "[void][Windows.Devices.Geolocation.Geolocator,Windows.Devices.Geolocation,ContentType=WindowsRuntime];"
                + "[void][Windows.Devices.Geolocation.Geoposition,Windows.Devices.Geolocation,ContentType=WindowsRuntime];"
                + "$g=[Windows.Devices.Geolocation.Geolocator]::new();"
                + "$g.DesiredAccuracy=[Windows.Devices.Geolocation.PositionAccuracy]::High;"
                + "$op=$g.GetGeopositionAsync();"
                + "$m=([System.WindowsRuntimeSystemExtensions].GetMethods()|Where-Object{$_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1})[0];"
                + "$t=$m.MakeGenericMethod([Windows.Devices.Geolocation.Geoposition]).Invoke($null,@($op));"
                + "if(-not $t.Wait(15000)){throw 'Location request timed out.'};"
                + "$c=$t.Result.Coordinate;$p=$c.Point.Position;"
                + "[Console]::Write(('{0:R},{1:R},{2:R}' -f $p.Latitude,$p.Longitude,$c.Accuracy))";
        Process process = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", script).redirectErrorStream(true).start();
        try {
            if (!process.waitFor(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new IOException("Windows Location Services timed out.");
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.exitValue() != 0) throw new IOException(locationError(output));
            return parse(output);
        } finally {
            process.destroy();
        }
    }

    static Position parse(String value) throws IOException {
        try {
            String[] parts=value.trim().split(",");
            if(parts.length!=3)throw new IllegalArgumentException();
            double latitude=Double.parseDouble(parts[0]);
            double longitude=Double.parseDouble(parts[1]);
            double accuracy=Double.parseDouble(parts[2]);
            if(!Double.isFinite(latitude)||latitude < -90||latitude > 90
                    ||!Double.isFinite(longitude)||longitude < -180||longitude > 180
                    ||!Double.isFinite(accuracy)||accuracy < 0)throw new IllegalArgumentException();
            return new Position(latitude,longitude,accuracy);
        } catch(Exception ex) {
            throw new IOException("Windows returned an invalid current location.",ex);
        }
    }

    private static String locationError(String output) {
        String detail=output==null?"":output.trim();
        if(detail.length()>300)detail=detail.substring(0,300);
        return "Could not get this computer's location. Turn on Windows Settings > Privacy & security > Location, allow desktop apps, and try again."
                +(detail.isBlank()?"":"\n\nWindows: "+detail);
    }

    public record Position(double latitude,double longitude,double accuracyMeters) { }
}
