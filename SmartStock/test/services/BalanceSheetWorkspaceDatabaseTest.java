package services;

import com.google.gson.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Dedicated disposable cluster only. Does not use configured store connections. */
@EnabledIfSystemProperty(named="smartstock.test.balanceSheetJdbc",matches="jdbc:postgresql://127\\.0\\.0\\.1:55449/postgres")
class BalanceSheetWorkspaceDatabaseTest {
    private Connection c;
    private static Connection connect()throws SQLException{return DriverManager.getConnection(System.getProperty("smartstock.test.balanceSheetJdbc"),"balance_sheet_test","");}
    @BeforeAll static void install()throws Exception {
        try(var c=connect();var s=c.createStatement()) {
            s.execute("DROP SCHEMA IF EXISTS storefront CASCADE; DROP SCHEMA public CASCADE; CREATE SCHEMA public");
            SchemaContractService.installLocalBaseline(c);
            s.execute("INSERT INTO locations(location_id,name,timezone,receipt_store_code) VALUES(994,'Balance test','America/Guyana','0994'),(995,'Other balance store','America/Guyana','0995')");
            s.execute("INSERT INTO roles(role_id,role_name) VALUES(994,'Balance no permissions')");
            s.execute("INSERT INTO users(user_id,username,full_name,role_id,is_active) VALUES(994,'balance-test','Balance Tester',1,true),(995,'balance-no-access','No access',994,true)");
            s.execute("INSERT INTO user_locations(user_id,location_id) VALUES(994,994)");
            SchemaContractService.requireLocalReady(c);
        }
    }
    @BeforeEach void begin()throws Exception {c=connect();c.setAutoCommit(false);bind(994,994,"test-browser");}
    @AfterEach void end()throws Exception {c.rollback();c.close();ServerRequestIdentity.clear();}
    private void bind(int user,int location,String device){ServerRequestIdentity.bind(user,location,"Balance test","Balance Tester",device,"Test browser");}
    private JsonObject json(String value){return JsonParser.parseString(value).getAsJsonObject();}
    private JsonObject execute(String action,JsonObject b)throws Exception{return LanJson.create().toJsonTree(BalanceSheetWorkspaceService.execute(c,action,b)).getAsJsonObject();}
    private JsonObject open()throws Exception{return execute("OPEN",json("{from:'2026-10-25',to:'2026-10-25'}"));}
    private JsonObject request(JsonObject workspace){JsonObject b=new JsonObject();b.add("workspaceId",workspace.get("workspaceId"));return b;}
    private JsonObject capture(JsonObject w,String form,long revision,String type,String value)throws Exception {
        JsonObject b=request(w);b.addProperty("eventKey",UUID.randomUUID().toString());b.addProperty("formId",form);b.addProperty("formRevision",revision);b.addProperty("eventType",type);b.addProperty("capturedAt","2026-10-25T12:00:00Z");b.add("content",json("{kind:'ADD_EXPENSE',status:'editing',values:{amount:'"+value+"'}}"));return b;
    }
    private long scalar(String query)throws SQLException {try(var s=c.createStatement();var r=s.executeQuery(query)){r.next();return r.getLong(1);}}
    @Test void capturesChangedRemovedAndCancelledInputWithoutPostingMoney()throws Exception {
        JsonObject w=open();String form=UUID.randomUUID().toString();
        execute("CAPTURE",capture(w,form,0,"ADD","12x"));execute("CAPTURE",capture(w,form,1,"INPUT","120"));execute("CAPTURE",capture(w,form,2,"REMOVE",""));execute("CAPTURE",capture(w,form,3,"CANCEL",""));
        assertEquals(0,scalar("SELECT count(*) FROM expenses WHERE location_id=994"));
        var events=execute("HISTORY",request(w)).getAsJsonArray("events");assertEquals(5,events.size());
        JsonObject select=request(w);select.add("eventId",events.get(3).getAsJsonObject().get("eventId"));var snapshot=execute("EVENT",select);
        assertEquals("12x",snapshot.getAsJsonObject("after").getAsJsonArray("drafts").get(0).getAsJsonObject().getAsJsonObject("content").getAsJsonObject("values").get("amount").getAsString());
        assertEquals("Balance Tester",events.get(0).getAsJsonObject().get("employee").getAsString());
    }
    @Test void retriesAreIdempotentAndConflictingRetryDoesNotOverwrite()throws Exception {
        JsonObject w=open(),b=capture(w,UUID.randomUUID().toString(),0,"INPUT","500");
        assertEquals(execute("CAPTURE",b),execute("CAPTURE",b));assertEquals(2,scalar("SELECT count(*) FROM balance_sheet_workspace_events"));
        b.getAsJsonObject("content").addProperty("amount","900");assertThrows(BalanceSheetWorkspaceService.Conflict.class,()->execute("CAPTURE",b));
    }
    @Test void draftOwnershipAndStaleRevisionAreEnforced()throws Exception {
        JsonObject w=open();String form=UUID.randomUUID().toString();execute("CAPTURE",capture(w,form,0,"INPUT","500"));
        assertThrows(BalanceSheetWorkspaceService.Conflict.class,()->execute("CAPTURE",capture(w,form,0,"INPUT","700")));
        bind(994,994,"other-browser");assertThrows(SecurityException.class,()->execute("CAPTURE",capture(w,form,1,"INPUT","700")));
        bind(994,995,"test-browser");assertThrows(SecurityException.class,()->execute("POLL",request(w)));
        bind(995,994,"test-browser");assertThrows(SecurityException.class,()->execute("OPEN",json("{from:'2026-10-25',to:'2026-10-25'}")));
    }
    private JsonObject mutation(JsonObject w,String change,JsonObject payload){JsonObject b=request(w);b.add("expectedRevision",w.get("revision"));b.addProperty("eventKey",UUID.randomUUID().toString());b.addProperty("change",change);b.add("payload",payload);return b;}
    private JsonObject expense(){return json("{expense:{expenseDate:'2026-10-25',category:'Other',payee:'Test',description:'Test row',amount:500,paymentMethod:'CASH',status:'PAID'}}");}
    @Test void mutationAndAuditCommitTogetherAndRetryDoesNotDuplicateMoney()throws Exception {
        JsonObject w=open(),b=mutation(w,"ADD_EXPENSE",expense());var result=execute("MUTATE",b);assertEquals(result,execute("MUTATE",b));
        assertEquals(1,scalar("SELECT count(*) FROM expenses WHERE location_id=994"));assertEquals(1,scalar("SELECT count(*) FROM balance_sheet_workspace_events WHERE event_type='ADD_EXPENSE'"));
        assertThrows(BalanceSheetWorkspaceService.Conflict.class,()->execute("MUTATE",mutation(w,"ADD_EXPENSE",expense())));
        var history=execute("HISTORY",request(w)).getAsJsonArray("events");var event=request(w);event.add("eventId",history.get(0).getAsJsonObject().get("eventId"));
        assertEquals(1,execute("EVENT",event).getAsJsonObject("after").getAsJsonArray("manualExpenses").size());
    }
    @Test void rejectedAuditInsertRollsBackAccountingChange()throws Exception {
        JsonObject w=open();Savepoint point=c.setSavepoint();
        try(var s=c.createStatement()) {s.execute("CREATE FUNCTION test_reject_balance_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END; $$; CREATE TRIGGER test_reject_balance_event BEFORE INSERT ON balance_sheet_workspace_events FOR EACH ROW EXECUTE FUNCTION test_reject_balance_event()");}
        assertThrows(SQLException.class,()->execute("MUTATE",mutation(w,"ADD_EXPENSE",expense())));c.rollback(point);
        assertEquals(0,scalar("SELECT count(*) FROM expenses WHERE location_id=994"));assertEquals(1,scalar("SELECT count(*) FROM balance_sheet_workspace_events"));
    }
    @Test void historyCannotBeUpdatedDeletedOrTruncated()throws Exception {
        open();for(String sql:List.of("UPDATE balance_sheet_workspace_events SET event_type='HIDDEN'","DELETE FROM balance_sheet_workspace_events","TRUNCATE balance_sheet_workspace_events","UPDATE balance_sheet_workspace_snapshots SET content='{}'","DELETE FROM balance_sheet_workspace_snapshots")) {
            Savepoint point=c.setSavepoint();assertThrows(SQLException.class,()->{try(var s=c.createStatement()){s.execute(sql);}});c.rollback(point);
        }
    }
    @Test void pollRecordsAutomaticChangesAndStablePollDoesNotGrowHistory()throws Exception {
        JsonObject w=open();execute("POLL",request(w));assertEquals(1,scalar("SELECT count(*) FROM balance_sheet_workspace_events"));
        ServerBalanceSheetService.addManualExpense(c,LanJson.create().fromJson(expense().get("expense"),ServerBalanceSheetService.ExpenseEntry.class));
        execute("POLL",request(w));assertEquals(1,scalar("SELECT count(*) FROM balance_sheet_workspace_events WHERE event_type='SYSTEM_REFRESH' AND changed_by_user_id IS NULL"));
    }
    @Test void historyRequiresItsOwnPermissionAndDurableTablesHaveRecoveryOwnership()throws Exception {
        JsonObject w=open();
        try(var s=c.createStatement()){s.execute("DELETE FROM role_permissions WHERE role_id=1 AND permission_id=(SELECT permission_id FROM permissions WHERE permission_key='VIEW_BALANCE_SHEET_HISTORY')");}
        execute("POLL",request(w));assertThrows(SecurityException.class,()->execute("HISTORY",request(w)));
        for(String table:List.of("balance_sheet_workspaces","balance_sheet_workspace_snapshots","balance_sheet_workspace_events")) {
            assertTrue(ReferenceDataSyncService.cloudPullOrderForApi().contains(table));
            assertTrue(CloudRowMirrorService.countScopedRows(c,994,table)>0);
            assertEquals(0,CloudRowMirrorService.countScopedRows(c,995,table));
        }
        assertFalse(ReferenceDataSyncService.cloudMirrorTablesForApi().contains("balance_sheet_workspace_drafts"));
    }
    @Test void timelinePaginationDoesNotSkipEvents()throws Exception {
        JsonObject w=open();String form=UUID.randomUUID().toString();for(int i=0;i<53;i++)execute("CAPTURE",capture(w,form,i,"INPUT",Integer.toString(i)));
        JsonObject first=execute("HISTORY",request(w));assertEquals(50,first.getAsJsonArray("events").size());assertTrue(first.get("hasMore").getAsBoolean());
        JsonObject next=request(w);next.add("before",first.getAsJsonArray("events").get(49).getAsJsonObject().get("eventId"));
        JsonObject second=execute("HISTORY",next);assertEquals(4,second.getAsJsonArray("events").size());assertFalse(second.get("hasMore").getAsBoolean());
    }
    @Test void submittedSnapshotSurvivesRevisionAndExpiredEditWindowIsEnforced()throws Exception {
        JsonObject w=open(),submitted=execute("MUTATE",mutation(w,"SUBMIT",json("{notes:'Original notes'}")));
        long id=submitted.get("submissionId").getAsLong();
        JsonObject originalEvent=execute("HISTORY",request(w)).getAsJsonArray("events").get(0).getAsJsonObject();
        w=execute("POLL",request(w));var context=ServerBalanceSheetService.loadEditContext(c,id);assertTrue(context.eligibility().editable());
        JsonObject payload=json("{edit:{submissionId:"+id+",expectedRevision:0,notes:'Revised notes',reason:'Corrected notes',expenses:[],otherIncome:[]}}");
        execute("MUTATE",mutation(w,"REVISE",payload));assertEquals("Revised notes",ServerBalanceSheetService.loadSubmission(c,id).notes());
        JsonObject select=request(w);select.add("eventId",originalEvent.get("eventId"));
        assertEquals("Original notes",execute("EVENT",select).getAsJsonObject("after").getAsJsonObject("submittedSheet").get("notes").getAsString());
        try(var p=c.prepareStatement("UPDATE balance_sheet_submissions SET submitted_at=now()-interval '49 hours' WHERE balance_sheet_submission_id=?")){p.setLong(1,id);p.executeUpdate();}
        assertFalse(ServerBalanceSheetService.loadEditContext(c,id).eligibility().editable());
        JsonObject latest=execute("POLL",request(w));payload.getAsJsonObject("edit").addProperty("expectedRevision",1);
        assertThrows(BalanceSheetWorkspaceService.Validation.class,()->execute("MUTATE",mutation(latest,"REVISE",payload)));
    }
    @Test void fullBackupRestoresJournalAndReenablesHistoryProtection()throws Exception {
        open();var backup=CompanyBackupService.buildBackupData(c);String sql=backup.sql();
        assertTrue(sql.contains("DISABLE TRIGGER USER"));assertTrue(sql.contains("ENABLE TRIGGER USER"));
        // Keep this restore inside the disposable test transaction, rather than committing fixtures.
        SqlScriptRunner.runSql(c,sql.replace("BEGIN;\n","").replace("COMMIT;\n",""));
        assertEquals(1,scalar("SELECT count(*) FROM balance_sheet_workspace_events"));
        Savepoint point=c.setSavepoint();assertThrows(SQLException.class,()->{try(var s=c.createStatement()){s.execute("DELETE FROM balance_sheet_workspace_events");}});c.rollback(point);
    }
}
