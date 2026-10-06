package utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomerPhoneNumberTest {
    @Test void normalizesLocalAndCountryPrefixedNumbers() {
        for (String input : new String[]{"7058194", "705 8194", "705-8194", "5927058194", "+592 705 8194", "00592 (705) 8194", "  +5927058194  "}) {
            assertEquals("+5927058194", CustomerPhoneNumber.normalize(input));
        }
        assertEquals("+5923393200", CustomerPhoneNumber.normalize("3393200"));
        assertEquals("+5926500991", CustomerPhoneNumber.normalize("5926500991"));
    }
    @Test void preservesForeignCountryCodes() {
        for (String input : new String[]{"+1 (202) 555-0123", "12025550123", "0012025550123"}) {
            assertEquals("+12025550123", CustomerPhoneNumber.normalize(input));
        }
        assertEquals("+442079460018", CustomerPhoneNumber.normalize("+44 20 7946 0018"));
        assertEquals("+18685551234", CustomerPhoneNumber.normalize("+1 868 555 1234"));
    }
    @Test void allowsMissingPhonesAndIsIdempotent() {
        assertEquals("", CustomerPhoneNumber.normalize(null));
        assertEquals("", CustomerPhoneNumber.normalize("  "));
        String saved = CustomerPhoneNumber.normalize("675 0302");
        assertEquals(saved, CustomerPhoneNumber.normalize(saved));
    }
    @Test void rejectsIncompleteOrNonPhoneContent() {
        for (String input : new String[]{"123", "+592705819", "7058194 ext 2", "7058194/6145030", "++5927058194", "phone7058194", "+999123456789", "+59270581940000000000"}) {
            assertThrows(IllegalArgumentException.class, () -> CustomerPhoneNumber.normalize(input), input);
        }
    }
}
