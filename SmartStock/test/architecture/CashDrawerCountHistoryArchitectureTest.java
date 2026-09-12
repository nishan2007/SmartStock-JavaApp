package architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CashDrawerCountHistoryArchitectureTest {
    private static String read(String path)throws Exception{return Files.readString(Path.of(path));}

    @Test void historyIsAppendOnlyServerAuthorizedAndRecoverable()throws Exception{
        String migration=read("database/migrations/v1_after/20260904150000_cash_drawer_count_history.sql");
        String server=read("src/services/LanApiServer.java");
        String sync=read("src/services/ReferenceDataSyncService.java");
        assertTrue(migration.contains("cash_drawer_count_events_immutable"));
        assertTrue(migration.contains("VIEW_DRAWER_HISTORY"));
        assertTrue(migration.contains("balance_sheet_drawer_sessions"));
        assertTrue(server.contains("requireAnyPermission(connection,session.userId(),\"VIEW_DRAWER_HISTORY\")"));
        assertTrue(server.contains("/v1/cash/drawer/draft/save"));
        assertTrue(sync.contains("\"cash_drawer_count_events\""));
        assertTrue(sync.contains("\"balance_sheet_drawer_sessions\""));
    }

    @Test void balanceDrawAutosavesAndSavedSheetsDrillIntoHistory()throws Exception{
        String draw=read("src/ui/screens/BalanceDraw.java");
        String sheet=read("src/ui/screens/BalanceSheet.java");
        assertTrue(draw.contains("new Timer(700"));
        assertTrue(draw.contains("loadCashDrawerDraft"));
        assertTrue(draw.contains("Draft saved"));
        assertTrue(sheet.contains("getClickCount()==2"));
        assertTrue(sheet.contains("loadCashDrawerHistory"));
        assertTrue(sheet.contains("Legacy balance sheet: session match inferred"));
        assertTrue(sheet.contains("Float breakdown:"));
        assertTrue(sheet.contains("CIH to remove breakdown:"));
    }
}
