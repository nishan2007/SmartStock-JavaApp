package services;

import com.google.gson.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Transactional local-only workspace journal shared by browser and register clients. */
public final class BalanceSheetWorkspaceService {
    public static final String PUBLIC_URL="https://balancesheet.deckers.gy/employees/balance-sheet.html";
    private static final Gson JSON = LanJson.create();
    private static final Set<String> INPUT_EVENTS = Set.of("INPUT", "ADD", "REMOVE", "CLEAR", "CANCEL", "LEAVE", "COMMIT");
    private BalanceSheetWorkspaceService() { }

    public static Object execute(Connection c, String action, JsonObject b) throws Exception {
        require(c,"BALANCE_SHEET");
        try(var p=c.prepareStatement("SELECT 1 FROM user_locations WHERE user_id=? AND location_id=?")) {
            p.setInt(1,ServerRequestIdentity.userId());p.setInt(2,ServerRequestIdentity.locationId());try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("This store is not assigned to your account.");}
        }
        if (c.getAutoCommit()) throw new SQLException("Workspace requests require a transaction.");
        if (ServerRoleGuard.blocksMutations()) throw new IllegalArgumentException(ServerRoleGuard.safeMessage());
        return switch(action) {
            case "OPEN" -> open(c,b);
            case "POLL" -> poll(c,b);
            case "CAPTURE" -> capture(c,b);
            case "HISTORY" -> history(c,b);
            case "EVENT" -> event(c,b);
            case "READ" -> read(c,b);
            case "MUTATE" -> mutate(c,b);
            default -> throw new IllegalArgumentException("Unknown balance sheet workspace action.");
        };
    }

    public static void require(Connection c,String permission)throws SQLException {
        try(var p=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions x ON x.permission_id=rp.permission_id WHERE u.user_id=? AND u.is_active=true AND x.permission_key=?")) {
            p.setInt(1,ServerRequestIdentity.userId());p.setString(2,permission);
            try(var r=p.executeQuery()){if(r.next())return;}
        }
        throw new SecurityException("You do not have permission for this balance sheet operation.");
    }
    static String zone(Connection c)throws SQLException {
        try(var p=c.prepareStatement("SELECT timezone FROM locations WHERE location_id=?")) {
            p.setInt(1,ServerRequestIdentity.locationId());try(var r=p.executeQuery()) {
                if(!r.next())throw new SecurityException("Store was not found.");
                String z=r.getString(1);return z==null||z.isBlank()?"America/Guyana":ZoneId.of(z).getId();
            }
        }
    }
    private static Object open(Connection c,JsonObject b)throws Exception {
        LocalDate today=LocalDate.now(ZoneId.of(zone(c)));
        LocalDate from=b.has("from")?LocalDate.parse(text(b,"from")):today,to=b.has("to")?LocalDate.parse(text(b,"to")):from;
        if(to.isBefore(from))throw new IllegalArgumentException("To date must be on or after From date.");
        List<Long> ids=sessions(b);
        for(long id:ids)try(var p=c.prepareStatement("SELECT 1 FROM cash_drawer_sessions WHERE cash_drawer_session_id=? AND location_id=?")) {
            p.setLong(1,id);p.setInt(2,ServerRequestIdentity.locationId());try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("Drawer session was not found at this store.");}
        }
        UUID id=UUID.randomUUID();
        try(var p=c.prepareStatement("INSERT INTO balance_sheet_workspaces(workspace_id,location_id,period_start,period_end,drawer_sessions) VALUES(?,?,?,?,?::jsonb) ON CONFLICT(location_id,period_start,period_end,drawer_sessions) DO NOTHING")) {
            p.setObject(1,id);p.setInt(2,ServerRequestIdentity.locationId());p.setObject(3,from);p.setObject(4,to);p.setString(5,JSON.toJson(ids));p.executeUpdate();
        }
        try(var p=c.prepareStatement("SELECT workspace_id FROM balance_sheet_workspaces WHERE location_id=? AND period_start=? AND period_end=? AND drawer_sessions=?::jsonb")) {
            p.setInt(1,ServerRequestIdentity.locationId());p.setObject(2,from);p.setObject(3,to);p.setString(4,JSON.toJson(ids));try(var r=p.executeQuery()){r.next();id=(UUID)r.getObject(1);}
        }
        JsonObject request=new JsonObject();request.addProperty("workspaceId",id.toString());return poll(c,request);
    }
    private record Workspace(UUID id,LocalDate from,LocalDate to,List<Long> sessions,long revision,String hash) { }
    private static Workspace lock(Connection c,JsonObject b)throws SQLException {
        UUID id=UUID.fromString(text(b,"workspaceId"));
        try(var p=c.prepareStatement("SELECT period_start,period_end,drawer_sessions,revision,snapshot_hash FROM balance_sheet_workspaces WHERE workspace_id=? AND location_id=? FOR UPDATE")) {
            p.setObject(1,id);p.setInt(2,ServerRequestIdentity.locationId());try(var r=p.executeQuery()) {
                if(!r.next())throw new SecurityException("Balance sheet was not found at this store.");
                JsonObject drawers=new JsonObject();drawers.add("cashDrawerSessionIds",JsonParser.parseString(r.getString(3)));
                return new Workspace(id,r.getObject(1,LocalDate.class),r.getObject(2,LocalDate.class),sessions(drawers),r.getLong(4),r.getString(5));
            }
        }
    }
    private static JsonObject snapshot(Connection c,Workspace w)throws SQLException {
        JsonObject out=new JsonObject();out.addProperty("from",w.from.toString());out.addProperty("to",w.to.toString());out.addProperty("storeZoneId",zone(c));
        out.addProperty("locationId",ServerRequestIdentity.locationId());
        out.add("cashDrawerSessionIds",JSON.toJsonTree(w.sessions));
        out.add("sheet",JSON.toJsonTree(ServerBalanceSheetService.loadBalanceSheet(c,w.from,w.to,zone(c),w.sessions)));
        // Keep source row IDs as well as presentation totals, so edits/deletions remain identifiable.
        out.add("manualExpenses",JSON.toJsonTree(ServerBalanceSheetService.listDeletableExpenses(c,w.from,w.to,"PAID")));
        out.add("manualPayables",JSON.toJsonTree(ServerBalanceSheetService.listDeletableExpenses(c,w.from,w.to,"UNPAID")));
        out.add("otherIncome",JSON.toJsonTree(ServerBalanceSheetService.listDeletableOtherIncome(c,w.from,w.to)));
        JsonArray drafts=new JsonArray();
        try(var p=c.prepareStatement("SELECT form_id,user_id,device_id,revision,content FROM balance_sheet_workspace_drafts WHERE workspace_id=? ORDER BY form_id")) {
            p.setObject(1,w.id);try(var r=p.executeQuery()){while(r.next()) {
                JsonObject d=new JsonObject();d.addProperty("formId",r.getString(1));d.addProperty("userId",r.getInt(2));d.addProperty("deviceId",r.getString(3));d.addProperty("revision",r.getLong(4));d.add("content",JsonParser.parseString(r.getString(5)));drafts.add(d);
            }}
        }
        out.add("drafts",drafts);return out;
    }
    static String hash(JsonElement content) { return LanSecurity.sha256(canonical(content).toString()); }
    private static JsonElement canonical(JsonElement value) {
        if(value.isJsonObject()){JsonObject sorted=new JsonObject();new TreeSet<>(value.getAsJsonObject().keySet()).forEach(k->sorted.add(k,canonical(value.getAsJsonObject().get(k))));return sorted;}
        if(value.isJsonArray()){JsonArray a=new JsonArray();value.getAsJsonArray().forEach(v->a.add(canonical(v)));return a;}return value;
    }
    private static JsonObject poll(Connection c,JsonObject b)throws Exception {
        Workspace w=lock(c,b);JsonObject full=snapshot(c,w);
        String hash=hash(full);long revision=w.revision;
        if(!hash.equals(w.hash)) {
            revision=append(c,w,UUID.randomUUID(),"",w.hash==null?"OPEN":"SYSTEM_REFRESH",null,full,new JsonObject(),new JsonObject(),null,false);
        }
        JsonObject out=new JsonObject();out.addProperty("workspaceId",w.id.toString());out.addProperty("revision",revision);out.addProperty("storeZoneId",zone(c));
        out.addProperty("isAdmin",isAdmin(c));
        out.add("sheet",full.get("sheet"));out.addProperty("from",w.from.toString());out.addProperty("to",w.to.toString());
        JsonArray own=new JsonArray();for(JsonElement d:full.getAsJsonArray("drafts"))if(d.getAsJsonObject().get("userId").getAsInt()==ServerRequestIdentity.userId()&&text(d.getAsJsonObject(),"deviceId").equals(ServerRequestIdentity.deviceId()))own.add(d);
        out.add("drafts",own);return out;
    }
    private static Object capture(Connection c,JsonObject b)throws Exception {
        Workspace w=lock(c,b);UUID key=UUID.fromString(text(b,"eventKey"));String requestHash=hash(b);
        JsonObject prior=prior(c,w,key,requestHash);if(prior!=null)return prior;
        String type=text(b,"eventType");if(!INPUT_EVENTS.contains(type))throw new IllegalArgumentException("Invalid input event.");
        UUID form=UUID.fromString(text(b,"formId"));JsonObject content=b.getAsJsonObject("content");
        if(content==null||content.toString().length()>65536)throw new IllegalArgumentException("Draft is too large.");
        long expected=b.get("formRevision").getAsLong(),actual=0;
        try(var p=c.prepareStatement("SELECT user_id,device_id,revision FROM balance_sheet_workspace_drafts WHERE workspace_id=? AND form_id=?")) {
            p.setObject(1,w.id);p.setObject(2,form);try(var r=p.executeQuery()){if(r.next()) {
                if(r.getInt(1)!=ServerRequestIdentity.userId()||!r.getString(2).equals(ServerRequestIdentity.deviceId()))throw new SecurityException("This entry belongs to another employee or device.");actual=r.getLong(3);
            }}
        }
        if(actual!=expected)throw new Conflict("This entry changed in another window. Your unsaved input has been preserved.");
        try(var p=c.prepareStatement("INSERT INTO balance_sheet_workspace_drafts(workspace_id,form_id,user_id,device_id,revision,content) VALUES(?,?,?,?,?,?::jsonb) ON CONFLICT(workspace_id,form_id) DO UPDATE SET revision=EXCLUDED.revision,content=EXCLUDED.content")) {
            p.setObject(1,w.id);p.setObject(2,form);p.setInt(3,ServerRequestIdentity.userId());p.setString(4,ServerRequestIdentity.deviceId());p.setLong(5,actual+1);p.setString(6,content.toString());p.executeUpdate();
        }
        JsonObject result=new JsonObject();result.addProperty("revision",w.revision+1);result.addProperty("formRevision",actual+1);
        JsonObject detail=new JsonObject();detail.addProperty("formId",form.toString());detail.add("content",content);
        append(c,w,key,requestHash,type,optional(b,"capturedAt"),snapshot(c,w),detail,result,null,true);return result;
    }
    private static JsonObject prior(Connection c,Workspace w,UUID key,String hash)throws SQLException {
        try(var p=c.prepareStatement("SELECT request_hash,result,changed_by_user_id,device_id FROM balance_sheet_workspace_events WHERE workspace_id=? AND event_key=?")) {
            p.setObject(1,w.id);p.setObject(2,key);try(var r=p.executeQuery()){if(!r.next())return null;
                if(!hash.equals(r.getString(1))||r.getInt(3)!=ServerRequestIdentity.userId()||!Objects.equals(r.getString(4),ServerRequestIdentity.deviceId()))throw new Conflict("This retry key was already used for different input.");
                return JsonParser.parseString(r.getString(2)).getAsJsonObject();}
        }
    }
    private static long append(Connection c,Workspace w,UUID key,String requestHash,String type,String capturedAt,JsonObject snapshot,JsonObject details,JsonObject result,Long submission,boolean actor)throws SQLException {
        String after=hash(snapshot);
        storeSnapshot(c,after,snapshot);
        try(var p=c.prepareStatement("INSERT INTO balance_sheet_workspace_events(workspace_id,revision,event_key,request_hash,event_type,changed_by_user_id,changed_by_name,device_id,device_name,captured_at,before_hash,after_hash,details,result,submission_id) VALUES(?,?,?,?,?,?,?,?,?,?,?, ?,?::jsonb,?::jsonb,?)")) {
            p.setObject(1,w.id);p.setLong(2,w.revision+1);p.setObject(3,key);p.setString(4,requestHash);p.setString(5,type);
            p.setObject(6,actor?ServerRequestIdentity.userId():null);p.setString(7,actor?ServerRequestIdentity.userName():"Store accounting update");p.setString(8,actor?ServerRequestIdentity.deviceId():null);p.setString(9,actor?ServerRequestIdentity.deviceName():null);
            p.setTimestamp(10,capturedAt==null?null:Timestamp.from(Instant.parse(capturedAt)));p.setString(11,w.hash);p.setString(12,after);p.setString(13,details.toString());p.setString(14,result.toString());p.setObject(15,submission);p.executeUpdate();
        }
        try(var p=c.prepareStatement("UPDATE balance_sheet_workspaces SET revision=?,snapshot_hash=? WHERE workspace_id=?")){p.setLong(1,w.revision+1);p.setString(2,after);p.setObject(3,w.id);p.executeUpdate();}
        return w.revision+1;
    }
    /** Content-address large sections and individual drafts; unchanged sections are stored once. */
    private static void storeSnapshot(Connection c,String hash,JsonObject full)throws SQLException {
        JsonObject descriptor=new JsonObject(),blocks=new JsonObject(),values=new JsonObject();JsonArray drafts=new JsonArray();
        descriptor.addProperty("journalSnapshot",1);
        try(var p=c.prepareStatement("INSERT INTO balance_sheet_workspace_snapshots(snapshot_hash,location_id,content) VALUES(?,?,?::jsonb) ON CONFLICT DO NOTHING")) {
            for(var entry:full.entrySet()) {
                if(entry.getKey().equals("drafts")){for(var draft:entry.getValue().getAsJsonArray())drafts.add(storeBlock(p,draft));}
                else if(entry.getValue().isJsonObject()||entry.getValue().isJsonArray())blocks.addProperty(entry.getKey(),storeBlock(p,entry.getValue()));
                else values.add(entry.getKey(),entry.getValue());
            }
            descriptor.add("values",values);descriptor.add("blocks",blocks);descriptor.add("draftBlocks",drafts);
            p.setString(1,hash);p.setInt(2,ServerRequestIdentity.locationId());p.setString(3,descriptor.toString());p.addBatch();p.executeBatch();
        }
    }
    private static String storeBlock(PreparedStatement p,JsonElement content)throws SQLException {
        String hash=LanSecurity.sha256(ServerRequestIdentity.locationId()+":block:"+canonical(content));
        p.setString(1,hash);p.setInt(2,ServerRequestIdentity.locationId());p.setString(3,content.toString());p.addBatch();return hash;
    }
    private static JsonElement expandSnapshot(Connection c,String json)throws SQLException {
        if(json==null)return JsonNull.INSTANCE;
        JsonObject descriptor=JsonParser.parseString(json).getAsJsonObject();
        // Also accepts full snapshots from older journal implementations.
        if(!descriptor.has("journalSnapshot"))return descriptor;
        JsonObject values=descriptor.getAsJsonObject("values").deepCopy();Map<String,JsonElement> loaded=new HashMap<>();
        List<String> hashes=new ArrayList<>();descriptor.getAsJsonObject("blocks").entrySet().forEach(e->hashes.add(e.getValue().getAsString()));descriptor.getAsJsonArray("draftBlocks").forEach(v->hashes.add(v.getAsString()));
        try(var p=c.prepareStatement("SELECT snapshot_hash,content FROM balance_sheet_workspace_snapshots WHERE location_id=? AND snapshot_hash=ANY(?)")) {
            p.setInt(1,ServerRequestIdentity.locationId());java.sql.Array ids=c.createArrayOf("text",hashes.toArray());
            try{p.setArray(2,ids);try(var r=p.executeQuery()){while(r.next())loaded.put(r.getString(1),JsonParser.parseString(r.getString(2)));}}finally{ids.free();}
        }
        for(var e:descriptor.getAsJsonObject("blocks").entrySet())values.add(e.getKey(),requiredBlock(loaded,e.getValue().getAsString()));
        JsonArray drafts=new JsonArray();for(var id:descriptor.getAsJsonArray("draftBlocks"))drafts.add(requiredBlock(loaded,id.getAsString()));values.add("drafts",drafts);return values;
    }
    private static JsonElement requiredBlock(Map<String,JsonElement> loaded,String hash)throws SQLException {
        JsonElement block=loaded.get(hash);if(block==null)throw new SQLException("A balance sheet snapshot block is missing. Restore a complete store backup.");return block;
    }
    private static Object history(Connection c,JsonObject b)throws SQLException {
        require(c,"VIEW_BALANCE_SHEET_HISTORY");Workspace w=lock(c,b);JsonArray rows=new JsonArray();
        long before=b.has("before")?b.get("before").getAsLong():Long.MAX_VALUE;
        String employee=optional(b,"employee"),type=optional(b,"eventType"),date=optional(b,"date");
        try(var p=c.prepareStatement("SELECT event_id,revision,event_type,changed_by_user_id,changed_by_name,device_name,captured_at,received_at,details,submission_id FROM balance_sheet_workspace_events WHERE workspace_id=? AND event_id<? AND (? IS NULL OR changed_by_name ILIKE '%'||?||'%') AND (? IS NULL OR event_type=?) AND (? IS NULL OR (received_at AT TIME ZONE ?)::date=?::date) ORDER BY event_id DESC LIMIT 51")) {
            p.setObject(1,w.id);p.setLong(2,before);p.setString(3,employee);p.setString(4,employee);p.setString(5,type);p.setString(6,type);p.setString(7,date);p.setString(8,zone(c));p.setString(9,date);
            try(var r=p.executeQuery()){while(r.next()){JsonObject e=new JsonObject();e.addProperty("eventId",r.getLong(1));e.addProperty("revision",r.getLong(2));e.addProperty("eventType",r.getString(3));e.addProperty("employee",r.getString(5));e.addProperty("device",r.getString(6));e.addProperty("capturedAt",r.getTimestamp(7)==null?null:r.getTimestamp(7).toInstant().toString());e.addProperty("receivedAt",r.getTimestamp(8).toInstant().toString());e.add("details",JsonParser.parseString(r.getString(9)));e.addProperty("submissionId",(Long)r.getObject(10));rows.add(e);}}
        }
        boolean more=rows.size()>50;if(more)rows.remove(50);JsonObject out=new JsonObject();out.add("events",rows);out.addProperty("hasMore",more);return out;
    }
    private static Object event(Connection c,JsonObject b)throws SQLException {
        require(c,"VIEW_BALANCE_SHEET_HISTORY");Workspace w=lock(c,b);
        try(var p=c.prepareStatement("SELECT a.content,z.content FROM balance_sheet_workspace_events e JOIN balance_sheet_workspace_snapshots a ON a.snapshot_hash=e.after_hash LEFT JOIN balance_sheet_workspace_snapshots z ON z.snapshot_hash=e.before_hash WHERE e.workspace_id=? AND e.event_id=?")) {
            p.setObject(1,w.id);p.setLong(2,b.get("eventId").getAsLong());try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("History event was not found.");JsonObject out=new JsonObject();out.add("after",expandSnapshot(c,r.getString(1)));out.add("before",expandSnapshot(c,r.getString(2)));return out;}
        }
    }
    private static Object read(Connection c,JsonObject b)throws Exception {
        String query=text(b,"query");
        if(query.equals("WORKSPACES")) {
            require(c,"VIEW_BALANCE_SHEET_HISTORY");JsonArray rows=new JsonArray();
            try(var p=c.prepareStatement("SELECT w.workspace_id,w.period_start,w.period_end,w.drawer_sessions,w.revision,max(e.received_at) last_change FROM balance_sheet_workspaces w LEFT JOIN balance_sheet_workspace_events e USING(workspace_id) WHERE w.location_id=? GROUP BY w.workspace_id ORDER BY last_change DESC")) {
                p.setInt(1,ServerRequestIdentity.locationId());try(var r=p.executeQuery()){while(r.next()){JsonObject row=new JsonObject();row.addProperty("workspaceId",r.getString(1));row.addProperty("from",r.getString(2));row.addProperty("to",r.getString(3));row.add("cashDrawerSessionIds",JsonParser.parseString(r.getString(4)));row.addProperty("revision",r.getLong(5));rows.add(row);}}
            }return Map.of("rows",rows);
        }
        if(query.equals("SUBMISSIONS"))return Map.of("rows",ServerBalanceSheetService.listSubmissions(c));
        if(query.equals("SUBMISSION"))return Map.of("sheet",ServerBalanceSheetService.loadSubmission(c,b.get("submissionId").getAsLong()));
        if(query.equals("EDIT_CONTEXT")){require(c,"EDIT_BALANCE_SHEET");return Map.of("context",ServerBalanceSheetService.loadEditContext(c,b.get("submissionId").getAsLong()));}
        if(query.equals("REVISION_HISTORY")){require(c,"VIEW_BALANCE_SHEET_HISTORY");return Map.of("rows",ServerBalanceSheetService.loadRevisionHistory(c,b.get("submissionId").getAsLong()));}
        Workspace w=lock(c,b);
        return switch(query) {
            case "EXPENSES" -> Map.of("rows",ServerBalanceSheetService.listDeletableExpenses(c,w.from,w.to,"PAID"));
            case "MANUAL_PAYABLES" -> Map.of("rows",ServerBalanceSheetService.listDeletableExpenses(c,w.from,w.to,"UNPAID"));
            case "PAYABLES" -> Map.of("rows",ServerBalanceSheetService.listUnpaidPayables(c,w.from,w.to));
            case "OTHER_INCOME" -> Map.of("rows",ServerBalanceSheetService.listDeletableOtherIncome(c,w.from,w.to));
            case "CHEQUES" -> Map.of("rows",ServerBalanceSheetService.listPendingChequeDeposits(c));
            case "DRAW_RANGES" -> Map.of("rows",ServerBalanceSheetService.findDrawSessionRanges(c,zone(c),w.from,w.to));
            case "DRAWER_HISTORY" -> {require(c,"VIEW_DRAWER_HISTORY");yield CashDrawerCountHistoryService.history(c,b.get("sessionId").getAsLong(),ServerRequestIdentity.locationId(),ServerRequestIdentity.userId());}
            default -> throw new IllegalArgumentException("Unknown balance sheet query.");
        };
    }
    private static Object mutate(Connection c,JsonObject b)throws Exception {
        Workspace w=lock(c,b);UUID key=UUID.fromString(text(b,"eventKey"));String requestHash=hash(b);JsonObject prior=prior(c,w,key,requestHash);if(prior!=null)return prior;
        if(b.get("expectedRevision").getAsLong()!=w.revision)throw new Conflict("The sheet changed. Review the latest values before saving. Your draft is preserved.");
        // Persist an accounting baseline in the same transaction before any destructive action.
        JsonObject before=snapshot(c,w);
        if(!hash(before).equals(w.hash)) {
            append(c,w,UUID.randomUUID(),"","SYSTEM_REFRESH",null,before,new JsonObject(),new JsonObject(),null,false);
            throw new Conflict("Accounting totals changed. Refresh and review before saving.");
        }
        String change=text(b,"change");JsonObject payload=b.getAsJsonObject("payload");if(payload==null)payload=new JsonObject();
        if(b.has("formId")) {
            try(var p=c.prepareStatement("SELECT 1 FROM balance_sheet_workspace_drafts WHERE workspace_id=? AND form_id=? AND user_id=? AND device_id=?")) {
                p.setObject(1,w.id);p.setObject(2,UUID.fromString(text(b,"formId")));p.setInt(3,ServerRequestIdentity.userId());p.setString(4,ServerRequestIdentity.deviceId());try(var r=p.executeQuery()){if(!r.next())throw new SecurityException("Capture this entry before posting it.");}
            }
        }
        JsonObject result;
        try{result=apply(c,change,payload,w.from,w.to,w.sessions);}
        catch(SQLException ex){if(ex.getSQLState()==null)throw new Validation(ex.getMessage());throw ex;}
        if(b.has("queueEmail")&&b.get("queueEmail").getAsBoolean()&&(change.equals("SUBMIT")||change.equals("REVISE"))) {
            Savepoint email=c.setSavepoint();
            try{result.add("email",JSON.toJsonTree(ServerEmailOutboxService.queueBalanceSheetSubmission(c,result.get("submissionId").getAsLong(),result.has("revisionNo")?result.get("revisionNo").getAsInt():0)));}
            catch(Exception ex){c.rollback(email);result.addProperty("emailWarning","The accounting change is saved; its email copy could not be queued.");}
            finally{c.releaseSavepoint(email);}
        }
        JsonObject details=new JsonObject();details.addProperty("change",change);details.add("payload",payload);
        if(b.has("formId"))details.add("formId",b.get("formId"));
        result.addProperty("revision",w.revision+1);
        if(b.has("formId"))try(var p=c.prepareStatement("UPDATE balance_sheet_workspace_drafts SET content=content||?::jsonb WHERE workspace_id=? AND form_id=?")) {
            JsonObject posted=new JsonObject();posted.addProperty("status","posted");posted.add("result",result);p.setString(1,posted.toString());p.setObject(2,w.id);p.setObject(3,UUID.fromString(text(b,"formId")));p.executeUpdate();
        }
        JsonObject after=snapshot(c,w);
        if(result.has("submissionId"))after.add("submittedSheet",JSON.toJsonTree(ServerBalanceSheetService.loadSubmission(c,result.get("submissionId").getAsLong())));
        if(result.has("sheet"))after.add("submittedSheet",result.get("sheet"));
        append(c,w,key,requestHash,change,null,after,details,result,result.has("submissionId")?result.get("submissionId").getAsLong():null,true);return result;
    }
    /** Journal older register protocol calls as well; no financial operation can skip its audit. */
    static JsonObject beforeRegisterChange(Connection c,JsonObject b)throws Exception {
        JsonObject range=new JsonObject();
        LocalDate today=LocalDate.now(ZoneId.of(zone(c)));
        String date=b.has("expense")?optional(b.getAsJsonObject("expense"),"expenseDate"):
                b.has("income")?optional(b.getAsJsonObject("income"),"incomeDate"):optional(b,"paymentDate");
        range.addProperty("from",b.has("from")?text(b,"from"):date==null?today.toString():date);
        range.addProperty("to",b.has("to")?text(b,"to"):range.get("from").getAsString());
        if(b.has("edit")) {
            var context=ServerBalanceSheetService.loadEditContext(c,b.getAsJsonObject("edit").get("submissionId").getAsLong());
            range.addProperty("from",context.periodStart().toString());range.addProperty("to",context.periodEnd().toString());
        }
        if(b.has("cashDrawerSessionIds"))range.add("cashDrawerSessionIds",b.get("cashDrawerSessionIds"));
        return (JsonObject)open(c,range);
    }
    static void afterRegisterChange(Connection c,JsonObject workspace,String action,JsonObject body,Map<String,Object> result)throws Exception {
        Workspace w=lock(c,workspace);
        JsonObject detail=new JsonObject();detail.add("payload",body);detail.addProperty("legacyProtocol",true);
        append(c,w,UUID.randomUUID(),"",action,null,snapshot(c,w),detail,JSON.toJsonTree(result).getAsJsonObject(),result.containsKey("submissionId")?((Number)result.get("submissionId")).longValue():null,true);
    }
    /** All financial changes use existing accounting rules and the caller's transaction. */
    static JsonObject apply(Connection c,String change,JsonObject p,LocalDate from,LocalDate to,List<Long> sessions)throws Exception {
        JsonObject result=new JsonObject();
        switch(change) {
            case "ADD_EXPENSE" -> result.addProperty("expenseId",ServerBalanceSheetService.addManualExpense(c,JSON.fromJson(p.get("expense"),ServerBalanceSheetService.ExpenseEntry.class)));
            case "ADD_OTHER_INCOME" -> result.addProperty("otherIncomeId",ServerBalanceSheetService.addOtherIncome(c,JSON.fromJson(p.get("income"),ServerBalanceSheetService.OtherIncomeEntry.class)));
            case "DELETE_EXPENSE" -> ServerBalanceSheetService.deleteManualExpense(c,p.get("expenseId").getAsLong(),from,to,text(p,"status"));
            case "DELETE_OTHER_INCOME" -> ServerBalanceSheetService.deleteOtherIncome(c,p.get("otherIncomeId").getAsLong(),from,to);
            case "PAY_PAYABLE" -> ServerBalanceSheetService.recordPayablePayment(c,p.get("expenseId").getAsLong(),LocalDate.parse(text(p,"paymentDate")),p.get("paymentAmount").getAsBigDecimal(),text(p,"paymentMethod"),optional(p,"paymentReference"));
            case "DEPOSIT_CHEQUE" -> {
                var candidate=JSON.fromJson(p.get("cheque"),ServerBalanceSheetService.ChequeDepositOption.class);
                var actual=ServerBalanceSheetService.listPendingChequeDeposits(c).stream().filter(q->q.sourceType().equals(candidate.sourceType())&&q.sourceId().equals(candidate.sourceId())).findFirst().orElseThrow(()->new SecurityException("Pending cheque was not found at this store."));
                ServerBalanceSheetService.markChequeDeposited(c,actual,optional(p,"notes"));
            }
            case "OVERSEAS_WITHDRAW" -> OverseasCashService.withdraw(c,p.get("amount").getAsBigDecimal(),text(p,"reason"));
            case "SET_BALANCE_BF" -> {admin(c);ServerBalanceSheetService.setBalanceBf(c,from,p.get("amount").getAsBigDecimal());}
            case "RECOVER_LEGACY_CASH" -> {admin(c);require(c,"BALANCE_DRAWER");result.add("recovery",JSON.toJsonTree(ServerBalanceSheetService.recoverLegacyCash(c,text(p,"sourceType"),p.get("sourceId").getAsLong(),text(p,"reason"))));}
            case "SUBMIT" -> result.addProperty("submissionId",ServerBalanceSheetService.submitBalanceSheet(c,from,to,zone(c),optional(p,"notes"),sessions,p.has("overseasCashCheck")?p.getAsJsonObject("overseasCashCheck"):null));
            case "REVISE" -> {
                require(c,"EDIT_BALANCE_SHEET");var request=JSON.fromJson(p.get("edit"),ServerBalanceSheetService.EditRequest.class);
                var context=ServerBalanceSheetService.loadEditContext(c,request.submissionId());
                if(!context.periodStart().equals(from)||!context.periodEnd().equals(to))throw new IllegalArgumentException("Open the submitted sheet's date range before revising it.");
                var revised=ServerBalanceSheetService.reviseSubmission(c,request);result.addProperty("submissionId",revised.submissionId());result.addProperty("revisionNo",revised.revisionNo());result.add("sheet",JSON.toJsonTree(revised.sheet()));
            }
            default -> throw new IllegalArgumentException("Unknown accounting change.");
        }
        result.addProperty("updated",true);return result;
    }
    private static boolean isAdmin(Connection c)throws SQLException {
        try(var p=c.prepareStatement("SELECT 1 FROM users u JOIN roles r ON r.role_id=u.role_id WHERE u.user_id=? AND upper(r.role_name)='ADMIN'")){p.setInt(1,ServerRequestIdentity.userId());try(var r=p.executeQuery()){return r.next();}}
    }
    private static void admin(Connection c)throws SQLException {
        if(!isAdmin(c))throw new SecurityException("Only an administrator can perform this operation.");
    }
    static List<Long> sessions(JsonObject b) {
        TreeSet<Long> ids=new TreeSet<>();if(b.has("cashDrawerSessionIds"))for(var v:b.getAsJsonArray("cashDrawerSessionIds")){long id=v.getAsLong();if(id<=0)throw new IllegalArgumentException("Invalid drawer session.");ids.add(id);}return List.copyOf(ids);
    }
    static String text(JsonObject b,String key) {String value=optional(b,key);if(value==null)throw new IllegalArgumentException(key+" is required.");return value;}
    static String optional(JsonObject b,String key) {return b.has(key)&&!b.get(key).isJsonNull()&&!b.get(key).getAsString().isBlank()?b.get(key).getAsString():null;}
    public static final class Conflict extends IllegalArgumentException { public Conflict(String message){super(message);} }
    public static final class Validation extends IllegalArgumentException { public Validation(String message){super(message);} }
}
