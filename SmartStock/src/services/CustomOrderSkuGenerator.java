package services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CustomOrderSkuGenerator {
    private static final Map<String, String> KNOWN_ABBREVIATIONS = Map.ofEntries(
            Map.entry("ADHESIVE", "ADH"),
            Map.entry("BANNER", "BNR"),
            Map.entry("CANVAS", "CNV"),
            Map.entry("GLOSSY", "GLSY"),
            Map.entry("MATTE", "MAT"),
            Map.entry("MEDIUM", "MED"),
            Map.entry("PURPLE", "PRPL"),
            Map.entry("SHIRT", "SHRT"),
            Map.entry("SMALL", "SML"),
            Map.entry("VINYL", "VNL")
    );

    private CustomOrderSkuGenerator() {
    }

    public static String itemSku(String itemName) {
        String itemPart = abbreviateName(itemName);
        return itemPart.isBlank() ? "" : "CO-" + itemPart;
    }

    public static String variantSku(String itemName, String variantName) {
        String itemPart = abbreviateName(itemName);
        String variantPart = abbreviateName(variantName);
        if (itemPart.isBlank()) {
            return variantPart.isBlank() ? "" : "CO-" + variantPart;
        }
        return variantPart.isBlank() ? "CO-" + itemPart : "CO-" + itemPart + "-" + variantPart;
    }

    private static String abbreviateName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (String rawWord : value.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+")) {
            if (!rawWord.isBlank()) {
                parts.add(abbreviateWord(rawWord));
            }
        }
        return String.join("-", parts);
    }

    private static String abbreviateWord(String word) {
        String known = KNOWN_ABBREVIATIONS.get(word);
        if (known != null) {
            return known;
        }
        if (word.length() <= 4) {
            return word;
        }
        StringBuilder abbreviated = new StringBuilder();
        abbreviated.append(word.charAt(0));
        for (int i = 1; i < word.length() && abbreviated.length() < 4; i++) {
            char ch = word.charAt(i);
            if (!isVowel(ch)) {
                abbreviated.append(ch);
            }
        }
        return abbreviated.toString();
    }

    private static boolean isVowel(char ch) {
        return ch == 'A' || ch == 'E' || ch == 'I' || ch == 'O' || ch == 'U';
    }
}
