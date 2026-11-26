package gy.deckers.studio;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BadgePortableCompatibilityTest {
    @Test void defaultSettingsAreImplicitAndNonDefaultSettingsSurvive() throws Exception {
        var template=BadgeTemplate.builtin();
        var layer=template.elements.get(0);
        layer.rotation=0;layer.hidden=false;layer.transparency=0;
        var defaults=JsonParser.parseString(BadgeTemplate.portableJson(template)).getAsJsonObject().getAsJsonArray("elements").get(0).getAsJsonObject();
        for(String field:new String[]{"rotation","hidden","transparency"})assertFalse(defaults.has(field));
        layer.rotation=90;layer.hidden=true;layer.transparency=25;
        var restored=BadgeTemplate.read(new java.io.ByteArrayInputStream(BadgeTemplate.portableJson(template).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals(90,restored.elements.get(0).rotation);
        assertTrue(restored.elements.get(0).hidden);
        assertEquals(25,restored.elements.get(0).transparency);
    }
}
