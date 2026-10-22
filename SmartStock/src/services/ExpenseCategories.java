package services;

import java.util.List;
import java.util.Locale;

/** Shared suggested expense categories; custom names remain supported. */
public final class ExpenseCategories {
    public static final List<String> OPTIONS = List.of(
            "Salary", "Marketing", "Electrical", "Construction", "Maintenance",
            "Inventory / Supplies", "Rent", "Utilities", "Internet / Telephone",
            "Transport / Delivery", "Fuel", "Cleaning", "Security", "Professional Fees",
            "Bank Charges", "Licences / Permits", "Taxes / Government Fees", "Insurance",
            "Equipment / Furniture", "Donations", "Staff Welfare", "Other");
    private ExpenseCategories() { }

    public static String normalize(String category) {
        String clean = category == null ? "" : category.trim();
        String key = clean.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        String alias = switch (key) {
            case "payroll", "salary", "salaries", "wages", "salaries / wages", "salary and wages" -> "Salary";
            case "maintainence", "maintenance", "repairs", "repairs / maintenance" -> "Maintenance";
            case "inventory/supplies", "inventory / supplies", "supplies" -> "Inventory / Supplies";
            case "marketing / advertising", "advertising" -> "Marketing";
            default -> clean;
        };
        return OPTIONS.stream().filter(option -> option.equalsIgnoreCase(alias)).findFirst().orElse(alias);
    }

    public static String forSource(String category, String source) {
        return "PAYROLL".equalsIgnoreCase(source) ? "Salary" : normalize(category);
    }
}
