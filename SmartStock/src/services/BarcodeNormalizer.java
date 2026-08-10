package services;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Normalizes scanner input without discarding meaningful identifier characters. */
public final class BarcodeNormalizer {
    private BarcodeNormalizer() {
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("[\\s-]+", "").toUpperCase(Locale.ROOT);
    }

    public static List<String> lookupCandidates(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);
        if (!normalized.chars().allMatch(Character::isDigit)) {
            return List.copyOf(candidates);
        }

        if (normalized.length() == 11) {
            candidates.add(normalized + checkDigit(normalized));
        } else if (normalized.length() == 12) {
            String data = normalized.substring(0, 11);
            if (normalized.charAt(11) == checkDigit(data)) {
                candidates.add(data);
            }
            candidates.add(normalized + checkDigit(normalized));
        } else if (normalized.length() == 13) {
            String data = normalized.substring(0, 12);
            if (normalized.charAt(12) == checkDigit(data)) {
                candidates.add(data);
            }
        }
        return List.copyOf(candidates);
    }

    static char checkDigit(String digits) {
        int sum = 0;
        boolean weightThree = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = digits.charAt(i) - '0';
            sum += digit * (weightThree ? 3 : 1);
            weightThree = !weightThree;
        }
        return (char) ('0' + ((10 - (sum % 10)) % 10));
    }
}
