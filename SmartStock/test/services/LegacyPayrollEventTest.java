package services;

import com.google.gson.JsonParser;
import java.lang.reflect.Proxy;
import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LegacyPayrollEventTest {
    @Test void supersededIncompleteEventDoesNotWritePayroll() throws Exception {
        CrossStoreReferenceSyncService.upsertPayrollSetting(connection(true), payload().getAsJsonObject("row_data"));
    }
    @Test void unresolvedIncompleteEventRemainsFailed() {
        assertThrows(SQLException.class, () ->
                CrossStoreReferenceSyncService.upsertPayrollSetting(connection(false), payload().getAsJsonObject("row_data")));
    }
    private com.google.gson.JsonObject payload() {
        return JsonParser.parseString("""
                {"table_name":"employee_payroll_settings","operation":"UPSERT",
                 "row_data":{"user_id":42,"effective_from":"2026-09-01",
                             "updated_at":"2026-09-02T00:00:00Z"}}
                """).getAsJsonObject();
    }
    private Connection connection(boolean matched) {
        ResultSet result = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class}, (p,m,a) -> switch(m.getName()) {
                    case "next" -> matched;
                    case "close" -> null;
                    default -> throw new AssertionError(m.getName());
                });
        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class}, (p,m,a) -> switch(m.getName()) {
                    case "setInt","setDate","setTimestamp","close" -> null;
                    case "executeQuery" -> result;
                    default -> throw new AssertionError("Unexpected payroll write: " + m.getName());
                });
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Connection.class}, (p,m,a) -> {
                    assertEquals("prepareStatement",m.getName());
                    String sql=(String)a[0];
                    assertTrue(sql.stripLeading().startsWith("SELECT"));
                    assertTrue(sql.contains("user_id=? AND effective_from=? AND updated_at>=?"));
                    assertTrue(sql.contains("compensation_type IS NOT NULL AND pay_rate IS NOT NULL"));
                    return statement;
                });
    }
}
