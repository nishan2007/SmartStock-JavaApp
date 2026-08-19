package utils;
import models.DeviceInfo;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.prefs.Preferences;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class DeviceUtils {

    private static final String PREF_NODE = "smartstock";
    private static final String PREF_INSTALLATION_ID = "installation_id";

    public static DeviceInfo collectDeviceInfo() {
        DeviceInfo info = new DeviceInfo();

        String hostname = getHostName();
        String osName = System.getProperty("os.name", "");
        String osVersion = System.getProperty("os.version", "");
        String osArch = System.getProperty("os.arch", "");
        String javaVersion = System.getProperty("java.version", "");
        String localUsername = System.getProperty("user.name", "");
        String macAddresses = getMacAddresses();

        String installationId = getOrCreateInstallationId();
        String fingerprintSource = hostname + "|" + osName + "|" + osVersion + "|" + osArch + "|" + macAddresses;
        String fingerprint = sha256(fingerprintSource);

        info.setInstallationId(installationId);
        info.setFingerprint(fingerprint);
        info.setHostname(hostname);
        info.setDeviceName(getDeviceName(hostname));
        info.setOsName(osName);
        info.setOsVersion(osVersion);
        info.setOsArch(osArch);
        info.setJavaVersion(javaVersion);
        info.setAppVersion(getAppVersion());
        info.setLocalUsername(localUsername);
        info.setMacAddresses(macAddresses);

        return info;
    }

    private static String getDeviceName(String hostname) {
        if (isMac()) {
            String computerName = macSystemName("ComputerName");
            if (!computerName.isBlank()) return computerName;
        }
        if (hostname != null && !hostname.isBlank()) {
            return hostname.trim();
        }
        return "Unknown";
    }

    private static String getOrCreateInstallationId() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        String installationId = prefs.get(PREF_INSTALLATION_ID, null);

        if (installationId == null || installationId.isBlank()) {
            installationId = UUID.randomUUID().toString();
            prefs.put(PREF_INSTALLATION_ID, installationId);
        }

        return installationId;
    }

    private static String getHostName() {
        if (isWindows()) {
            String computerName = System.getenv("COMPUTERNAME");
            if (computerName != null && !computerName.isBlank()) return computerName.trim();
        }
        if (isMac()) {
            String configuredHostName = macSystemName("HostName");
            if (!configuredHostName.isBlank()) return configuredHostName;
            String localHostName = macSystemName("LocalHostName");
            if (!localHostName.isBlank()) return localHostName + ".local";
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "Unknown";
        }
    }

    private static boolean isMac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String macSystemName(String key) {
        Process process = null;
        try {
            process = new ProcessBuilder("/usr/sbin/scutil", "--get", key)
                    .redirectErrorStream(true).start();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            if (process.exitValue() != 0) return "";
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return "";
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static String getMacAddresses() {
        try {
            List<String> macs = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac != null && mac.length > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (byte b : mac) {
                        if (sb.length() > 0) sb.append("-");
                        sb.append(String.format("%02X", b));
                    }
                    macs.add(sb.toString());
                }
            }

            Collections.sort(macs);
            return String.join(", ", macs);
        } catch (Exception e) {
            return "";
        }
    }

    public static String getAppVersion() {
        Package pkg = DeviceUtils.class.getPackage();
        if (pkg != null) {
            String implementationVersion = pkg.getImplementationVersion();
            if (implementationVersion != null && !implementationVersion.isBlank()) {
                return implementationVersion;
            }
        }

        try (InputStream inputStream = DeviceUtils.class.getResourceAsStream("/META-INF/MANIFEST.MF")) {
            if (inputStream != null) {
                Manifest manifest = new Manifest(inputStream);
                Attributes attributes = manifest.getMainAttributes();
                String version = attributes.getValue("Implementation-Version");
                if (version != null && !version.isBlank()) {
                    return version;
                }
            }
        } catch (Exception ignored) {
        }

        return "dev";
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }
}
