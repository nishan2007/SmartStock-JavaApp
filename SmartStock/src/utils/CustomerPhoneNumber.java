package utils;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;

/** Canonical customer phone storage: international country code and digits. */
public final class CustomerPhoneNumber {
    private CustomerPhoneNumber() { }

    public static String normalize(String input) {
        if (input == null || input.isBlank()) return "";
        String compact = input.strip().replaceAll("[\\s\\p{Z}().-]", "");
        if (!compact.matches("\\+?[0-9]+")) throw invalid();
        boolean international = compact.startsWith("+") || compact.startsWith("00");
        if (compact.startsWith("00")) compact = "+" + compact.substring(2);
        if (!international) {
            // Seven digits always mean a local Guyana number. Longer input must
            // already include a country code; never guess a foreign area code.
            compact = compact.length() == 7 ? "+592" + compact : "+" + compact;
        }
        try {
            PhoneNumberUtil util = PhoneNumberUtil.getInstance();
            var number = util.parse(compact, "GY");
            if (number.hasExtension() || !util.isPossibleNumber(number)) throw invalid();
            String formatted = util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
            if (!formatted.matches("\\+[1-9][0-9]{7,14}")) throw invalid();
            return formatted;
        } catch (NumberParseException ex) {
            throw invalid();
        }
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Enter a seven-digit Guyana phone number, or a complete international number with its country code (for example +5927058194). Leave the field blank if no phone is available.");
    }
}
