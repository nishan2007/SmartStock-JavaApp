package ui.screens.customorders;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomOrderCallHistoryTest {
    @Test void activityShowsWhoCalledAndTheOutcome() {
        JsonObject event=new JsonObject();
        event.addProperty("action_type","CUSTOMER_CALL");
        event.addProperty("user_name","Clerk");
        event.addProperty("new_value","CALLED_NO_ANSWER");
        assertEquals("Clerk recorded Called no answer",CustomOrderDetailsPanel.eventTitle(event));
        event.addProperty("new_value","CALLED");
        assertEquals("Clerk recorded Called",CustomOrderDetailsPanel.eventTitle(event));
    }
}
