package services;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class OverseasCashVerificationTest {
    private final OverseasCashService.FundReport fund=new OverseasCashService.FundReport(BigDecimal.ZERO,new BigDecimal("900.00"),BigDecimal.ZERO,new BigDecimal("900.00"),List.of());
    private JsonObject count(){
        JsonObject b=new JsonObject(),counts=new JsonObject();for(int d:OverseasCashService.COUNT_DENOMINATIONS)counts.addProperty(Integer.toString(d),0);
        counts.addProperty("500",1);counts.addProperty("100",4);b.add("denominations",counts);b.addProperty("expected","900");return b;
    }
    @Test void serverCalculatesTotalsAndDoesNotChangeFundBalance()throws Exception {
        var input=count();input.addProperty("counted","99999");input.addProperty("variance","99999");
        var saved=OverseasCashService.verify(fund,input,"Cash manager","2026-10-22T12:00:00Z");
        assertEquals(0,new BigDecimal("900").compareTo(saved.verification().counted()));assertEquals(0,saved.verification().variance().signum());
        assertEquals(fund.carriedForward(),saved.carriedForward());assertEquals("Cash manager",saved.verification().verifiedBy());assertNull(fund.verification());
    }
    @Test void shortageAndOverageNeedReasonAndAreSigned()throws Exception {
        var b=count();b.getAsJsonObject("denominations").addProperty("100",3);
        assertThrows(SQLException.class,()->OverseasCashService.verify(fund,b,"Manager","now"));b.addProperty("reason","Counted twice");
        assertEquals(0,new BigDecimal("-100").compareTo(OverseasCashService.verify(fund,b,"Manager","now").verification().variance()));
        b.getAsJsonObject("denominations").addProperty("100",5);
        assertEquals(0,new BigDecimal("100").compareTo(OverseasCashService.verify(fund,b,"Manager","now").verification().variance()));
    }
    @Test void rejectsStaleMissingFractionalNegativeAndUnsupportedCounts() {
        var stale=count();stale.addProperty("expected",899);assertThrows(SQLException.class,()->OverseasCashService.verify(fund,stale,"Manager","now"));
        for(String value:List.of("-1","1.5","2147483648")){var b=count();b.getAsJsonObject("denominations").addProperty("100",value);assertThrows(SQLException.class,()->OverseasCashService.verify(fund,b,"Manager","now"));}
        var missing=count();missing.getAsJsonObject("denominations").remove("20");assertThrows(SQLException.class,()->OverseasCashService.verify(fund,missing,"Manager","now"));
        var unsupported=count();unsupported.getAsJsonObject("denominations").addProperty("25",1);assertThrows(SQLException.class,()->OverseasCashService.verify(fund,unsupported,"Manager","now"));
    }
    @Test void historicalSnapshotsWithoutVerificationRemainUnverified() {
        var old=new Gson().fromJson("{\"broughtForward\":0,\"received\":900,\"expenses\":0,\"carriedForward\":900,\"lines\":[]}",OverseasCashService.FundReport.class);
        assertNull(old.verification());
    }
}
