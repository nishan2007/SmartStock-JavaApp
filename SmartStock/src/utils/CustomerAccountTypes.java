package utils;

/** Customer type is the sole authority for account classification. */
public final class CustomerAccountTypes {
    private CustomerAccountTypes() { }
    public static boolean isBusiness(String name) {
        return name != null && ("Business".equalsIgnoreCase(name.trim()) || "Businesses".equalsIgnoreCase(name.trim()));
    }
}
