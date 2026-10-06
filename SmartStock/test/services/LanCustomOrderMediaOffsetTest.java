package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import static org.junit.jupiter.api.Assertions.*;

class LanCustomOrderMediaOffsetTest {
    private Object parse(String value, String parser) throws Exception {
        JsonObject body = new JsonObject();
        if (value != null) body.add("offset", com.google.gson.JsonParser.parseString(value));
        Method method = LanApiServer.class.getDeclaredMethod(parser, JsonObject.class, String.class);
        method.setAccessible(true);
        return method.invoke(null, body, "offset");
    }

    @Test void firstAndSubsequentChunksHaveValidOffsets() throws Exception {
        assertEquals(0L, parse("0", "requiredNonNegativeLong"));
        assertEquals(786432L, parse("786432", "requiredNonNegativeLong"));
    }

    @Test void malformedOffsetsAreRejected() {
        for (String value : new String[]{null, "null", "-1", "0.5", "9223372036854775808", "\"bad\""}) {
            assertThrows(InvocationTargetException.class, () -> parse(value, "requiredNonNegativeLong"));
        }
    }

    @Test void identifiersStillRequirePositiveValues() {
        assertThrows(InvocationTargetException.class, () -> parse("0", "requiredLong"));
    }
}
