package services;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class CashDrawerHandoverServiceTest {
    @Test void onlyOutgoingCashierInitiatesAndDifferentCashierAccepts() throws Exception {
        assertDoesNotThrow(()->CashDrawerHandoverService.validateCashier(1,1,false));
        assertDoesNotThrow(()->CashDrawerHandoverService.validateCashier(1,2,true));
        assertThrows(SQLException.class,()->CashDrawerHandoverService.validateCashier(1,2,false));
        assertThrows(SQLException.class,()->CashDrawerHandoverService.validateCashier(1,1,true));
        assertThrows(SQLException.class,()->CashDrawerHandoverService.validateCashier(null,2,true));
    }
    @Test void serverRequiresCompleteValidDenominationsMatchingTotal() throws Exception {
        JsonObject b=count();
        assertDoesNotThrow(()->CashDrawerHandoverService.validateCount(b,new BigDecimal("100")));
        assertThrows(SQLException.class,()->CashDrawerHandoverService.validateCount(b,new BigDecimal("200")));
        b.getAsJsonObject("denominationCounts").addProperty("100",-1);
        assertThrows(SQLException.class,()->CashDrawerHandoverService.validateCount(b,new BigDecimal("-100")));
        b.getAsJsonObject("denominationCounts").addProperty("100",1.5);
        assertThrows(SQLException.class,()->CashDrawerHandoverService.validateCount(b,new BigDecimal("150")));
        b.getAsJsonObject("denominationCounts").remove("20");
        assertThrows(SQLException.class,()->CashDrawerHandoverService.validateCount(b,new BigDecimal("100")));
        JsonObject incomplete=count();incomplete.addProperty("complete",false);
        assertThrows(SQLException.class,()->CashDrawerHandoverService.validateCount(incomplete,new BigDecimal("100")));
    }
    static JsonObject count() {
        JsonObject b=new JsonObject(),d=new JsonObject();
        for(String v:new String[]{"5000","2000","1000","500","100","50","20"})d.addProperty(v,v.equals("100")?1:0);
        b.add("denominationCounts",d);b.addProperty("complete",true);b.addProperty("countedCash",100);
        return b;
    }
}
