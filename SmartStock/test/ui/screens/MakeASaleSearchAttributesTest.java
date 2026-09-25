package ui.screens;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MakeASaleSearchAttributesTest {
    @Test void separatesAllPresentAttributesWithSlashes() {
        assertEquals("Large / Blue / Vanilla",
                MakeASale.searchItemAttributes("Large", "Blue", "Vanilla"));
    }

    @Test void omitsMissingAttributesWithoutExtraSlashes() {
        assertEquals("Blue / Vanilla", MakeASale.searchItemAttributes(null, "Blue", "Vanilla"));
        assertEquals("Large / Vanilla", MakeASale.searchItemAttributes("Large", " ", "Vanilla"));
        assertEquals("Blue", MakeASale.searchItemAttributes("", " Blue ", null));
        assertEquals("", MakeASale.searchItemAttributes(null, "", " "));
        assertEquals("Large", MakeASale.searchItemAttributes("Large", null, null));
    }
}
