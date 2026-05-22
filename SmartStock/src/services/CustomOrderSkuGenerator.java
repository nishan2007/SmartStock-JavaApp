package services;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class CustomOrderSkuGenerator {
    private static final Map<String, String> KNOWN_ABBREVIATIONS = Map.ofEntries(
            Map.entry("ADHESIVE", "ADH"),
            Map.entry("BANNER", "BNR"),
            Map.entry("CANVAS", "CNV"),
            Map.entry("GLOSSY", "GLSY"),
            Map.entry("MARKER", "MRKR"),
            Map.entry("MATTE", "MAT"),
            Map.entry("MEDIUM", "MED"),
            Map.entry("PAPER", "PPR"),
            Map.entry("PEN", "PEN"),
            Map.entry("PENCIL", "PNCL"),
            Map.entry("PURPLE", "PRPL"),
            Map.entry("SHIRT", "SHRT"),
            Map.entry("SMALL", "SML"),
            Map.entry("STICKER", "STKR"),
            Map.entry("VINYL", "VNL")
    );
    private static final Set<String> STOP_WORDS = Set.of(
            "A", "AN", "AND", "FOR", "IN", "OF", "THE", "TO", "WITH"
    );

    private CustomOrderSkuGenerator() {
    }

    public static String itemSku(String itemName) {
        return sampleSku(itemPrefix(itemName));
    }

    public static String variantSku(String itemName, String variantName) {
        return sampleSku(variantPrefix(itemName, variantName));
    }

    private static String sampleSku(String prefix) {
        return prefix.isBlank() ? "" : prefix + "-0001";
    }

    private static String itemPrefix(String itemName) {
        return prefixFromWords(words(itemName));
    }

    private static String variantPrefix(String itemName, String variantName) {
        List<String> variantWords = words(variantName);
        if (variantWords.isEmpty()) {
            return itemPrefix(itemName);
        }
        List<String> itemWords = words(itemName);
        if (itemWords.isEmpty()) {
            return prefixFromWords(variantWords);
        }

        String itemPart = abbreviateWord(itemWords.get(0));
        String variantPart = abbreviateWord(variantWords.get(0));
        String combined = itemPart.substring(0, Math.min(2, itemPart.length()))
                + variantPart.substring(0, Math.min(2, variantPart.length()));
        return rightSize(combined, itemWords, variantWords);
    }

    private static String prefixFromWords(List<String> words) {
        if (words.isEmpty()) {
            return "";
        }
        if (words.size() == 1) {
            return abbreviateWord(words.get(0));
        }

        String initials = initials(words);
        if (initials.length() >= 3) {
            return initials.substring(0, Math.min(4, initials.length()));
        }

        String firstPart = abbreviateWord(words.get(0));
        String secondPart = abbreviateWord(words.get(1));
        String combined = firstPart.substring(0, Math.min(2, firstPart.length()))
                + secondPart.substring(0, Math.min(2, secondPart.length()));
        return rightSize(combined, words);
    }

    private static List<String> words(String value) {
        List<String> words = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return words;
        }
        for (String rawWord : value.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+")) {
            if (!rawWord.isBlank() && !STOP_WORDS.contains(rawWord)) {
                words.add(rawWord);
            }
        }
        return words;
    }

    private static String initials(List<String> words) {
        StringBuilder initials = new StringBuilder();
        for (String word : words) {
            initials.append(word.charAt(0));
        }
        return initials.toString();
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
        appendMatchingChars(abbreviated, word, false);
        appendMatchingChars(abbreviated, word, true);
        return abbreviated.substring(0, Math.min(4, abbreviated.length()));
    }

    private static void appendMatchingChars(StringBuilder abbreviated, String word, boolean vowels) {
        for (int i = 1; i < word.length() && abbreviated.length() < 4; i++) {
            char ch = word.charAt(i);
            if (isVowel(ch) == vowels) {
                abbreviated.append(ch);
            }
        }
    }

    @SafeVarargs
    private static String rightSize(String value, List<String>... wordLists) {
        StringBuilder sized = new StringBuilder(value);
        for (List<String> wordList : wordLists) {
            for (String word : wordList) {
                for (int i = 0; i < word.length() && sized.length() < 3; i++) {
                    char ch = word.charAt(i);
                    if (sized.indexOf(String.valueOf(ch)) < 0) {
                        sized.append(ch);
                    }
                }
            }
        }
        for (int i = 0; i < "ITEM".length() && sized.length() < 3; i++) {
            sized.append("ITEM".charAt(i));
        }
        return sized.substring(0, Math.min(4, sized.length()));
    }

    private static boolean isVowel(char ch) {
        return ch == 'A' || ch == 'E' || ch == 'I' || ch == 'O' || ch == 'U';
    }
}
