package models;

public class DeviceInfo {
    private String installationId;
    private String fingerprint;
    private String deviceName;
    private String hostname;
    private String osName;
    private String osVersion;
    private String osArch;
    private String javaVersion;
    private String appVersion;
    private String localUsername;
    private String macAddresses;

    public String getInstallationId() { return installationId; }
    public void setInstallationId(String installationId) { this.installationId = installationId; }

    public String getFingerprint() { return fingerprint; }
    public void setFingerprint(String fingerprint) { this.fingerprint = fingerprint; }

    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }

    public String getHostname() { return hostname; }
    public void setHostname(String hostname) { this.hostname = hostname; }

    public String getOsName() { return osName; }
    public void setOsName(String osName) { this.osName = osName; }

    public String getOsVersion() { return osVersion; }
    public void setOsVersion(String osVersion) { this.osVersion = osVersion; }

    public String getOsArch() { return osArch; }
    public void setOsArch(String osArch) { this.osArch = osArch; }

    public String getJavaVersion() { return javaVersion; }
    public void setJavaVersion(String javaVersion) { this.javaVersion = javaVersion; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }

    public String getLocalUsername() { return localUsername; }
    public void setLocalUsername(String localUsername) { this.localUsername = localUsername; }

    public String getMacAddresses() { return macAddresses; }
    public void setMacAddresses(String macAddresses) { this.macAddresses = macAddresses; }
}