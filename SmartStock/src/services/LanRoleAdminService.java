package services;

import com.google.gson.JsonObject;
import java.sql.*;
import java.util.*;

final class LanRoleAdminService {
    private LanRoleAdminService() { }

    static Map<String,Object> state(Connection c,int actor)throws Exception {
        require(c,actor);
        List<Map<String,Object>>roles=new ArrayList<>(),permissions=new ArrayList<>(),mobile=new ArrayList<>();
        try(PreparedStatement p=c.prepareStatement("SELECT role_id,role_name FROM roles ORDER BY role_name");ResultSet r=p.executeQuery()){while(r.next())roles.add(map("roleId",r.getInt(1),"name",r.getString(2)));}
        try(PreparedStatement p=c.prepareStatement("SELECT permission_key,COALESCE(NULLIF(permission_name,''),permission_key),COALESCE(permission_group,'General'),COALESCE(permission_subgroup,''),COALESCE(description,'') FROM permissions WHERE NULLIF(TRIM(permission_key),'') IS NOT NULL ORDER BY permission_group,permission_subgroup,permission_name,permission_key");ResultSet r=p.executeQuery()){while(r.next())permissions.add(def(r));}
        boolean mobileAvailable=table(c,"mobile_permissions")&&table(c,"role_mobile_permissions");
        if(mobileAvailable)try(PreparedStatement p=c.prepareStatement("SELECT permission_key,COALESCE(NULLIF(permission_name,''),permission_key),COALESCE(permission_group,'General'),COALESCE(permission_subgroup,''),COALESCE(description,'') FROM mobile_permissions WHERE NULLIF(TRIM(permission_key),'') IS NOT NULL ORDER BY permission_group,permission_subgroup,permission_name,permission_key");ResultSet r=p.executeQuery()){while(r.next())mobile.add(def(r));}
        return map("roles",roles,"permissions",permissions,"mobilePermissions",mobile,"mobileAvailable",mobileAvailable);
    }

    static Map<String,Object> selected(Connection c,JsonObject b,int actor)throws Exception {
        require(c,actor);int role=b.get("roleId").getAsInt();List<String>desktop=keys(c,"SELECT p.permission_key FROM role_permissions rp JOIN permissions p ON p.permission_id=rp.permission_id WHERE rp.role_id=?",role);List<String>mobile=table(c,"role_mobile_permissions")?keys(c,"SELECT permission_key FROM role_mobile_permissions WHERE role_id=?",role):List.of();return map("permissionKeys",desktop,"mobilePermissionKeys",mobile);
    }

    static Map<String,Object> save(Connection c,JsonObject b,int actor,int locationId,UUID deviceId)throws Exception {
        require(c,actor);int role=b.get("roleId").getAsInt();Set<String>desktop=jsonSet(b,"permissionKeys"),mobile=jsonSet(b,"mobilePermissionKeys");
        ensureRole(c,role);replace(c,role,desktop,"role_permissions","permissions","permission_id");
        if(table(c,"role_mobile_permissions"))replaceMobile(c,role,mobile);
        SyncOutboxService.recordEvent(c,"ROLE_PERMISSIONS_UPDATED",Map.of("role_id",role,"permission_count",desktop.size(),"mobile_permission_count",mobile.size(),"actor_user_id",actor),locationId,deviceId.toString(),actor);
        return map("saved",true);
    }

    static Map<String,Object> add(Connection c,JsonObject b,int actor,int locationId,UUID deviceId)throws Exception {
        require(c,actor);String name=b.has("name")?b.get("name").getAsString().trim().toUpperCase(Locale.ROOT):"";if(name.isBlank()||name.length()>100)throw new RuleViolation(400,"VALIDATION_ERROR","Enter a valid role name.");
        try(PreparedStatement p=c.prepareStatement("INSERT INTO roles(role_name,description) VALUES(?,'Custom role') RETURNING role_id")){p.setString(1,name);try(ResultSet r=p.executeQuery()){r.next();int roleId=r.getInt(1);SyncOutboxService.recordEvent(c,"ROLE_CREATED",Map.of("role_id",roleId,"role_name",name,"actor_user_id",actor),locationId,deviceId.toString(),actor);return map("roleId",roleId,"name",name);}}
    }

    private static void replace(Connection c,int role,Set<String>keys,String join,String defs,String id)throws Exception {
        Set<Integer>wanted=new LinkedHashSet<>();try(PreparedStatement p=c.prepareStatement("SELECT "+id+" FROM "+defs+" WHERE UPPER(permission_key)=UPPER(?)")){for(String key:keys){p.setString(1,key);try(ResultSet r=p.executeQuery()){if(!r.next())throw new RuleViolation(400,"UNKNOWN_PERMISSION","A selected permission no longer exists: "+key);wanted.add(r.getInt(1));}}}
        Set<Integer>existing=new LinkedHashSet<>();try(PreparedStatement p=c.prepareStatement("SELECT "+id+" FROM "+join+" WHERE role_id=?")){p.setInt(1,role);try(ResultSet r=p.executeQuery()){while(r.next())existing.add(r.getInt(1));}}
        try(PreparedStatement p=c.prepareStatement("DELETE FROM "+join+" WHERE role_id=? AND "+id+"=?")){for(int value:existing)if(!wanted.contains(value)){ReferenceDataSyncService.recordTombstone(c,join,Map.of("role_id",role,id,value));p.setInt(1,role);p.setInt(2,value);p.addBatch();}p.executeBatch();}
        try(PreparedStatement p=c.prepareStatement("INSERT INTO "+join+"(role_id,"+id+") VALUES(?,?) ON CONFLICT DO NOTHING")){for(int value:wanted){p.setInt(1,role);p.setInt(2,value);p.addBatch();}p.executeBatch();}
    }
    private static void replaceMobile(Connection c,int role,Set<String>keys)throws Exception{try(PreparedStatement d=c.prepareStatement("DELETE FROM role_mobile_permissions WHERE role_id=?")){d.setInt(1,role);d.executeUpdate();}try(PreparedStatement p=c.prepareStatement("INSERT INTO role_mobile_permissions(role_id,permission_key) SELECT ?,permission_key FROM mobile_permissions WHERE permission_key=? ON CONFLICT DO NOTHING")){for(String key:keys){p.setInt(1,role);p.setString(2,key);p.addBatch();}p.executeBatch();}}
    private static List<String>keys(Connection c,String sql,int role)throws SQLException{List<String>x=new ArrayList<>();try(PreparedStatement p=c.prepareStatement(sql)){p.setInt(1,role);try(ResultSet r=p.executeQuery()){while(r.next())x.add(r.getString(1));}}return x;}
    private static Map<String,Object>def(ResultSet r)throws SQLException{return map("key",r.getString(1).toUpperCase(Locale.ROOT),"label",r.getString(2),"group",r.getString(3),"subgroup",r.getString(4),"description",r.getString(5));}
    private static Set<String>jsonSet(JsonObject b,String key){Set<String>x=new LinkedHashSet<>();if(b.has(key)&&b.get(key).isJsonArray())b.getAsJsonArray(key).forEach(v->x.add(v.getAsString().toUpperCase(Locale.ROOT)));return x;}
    private static boolean table(Connection c,String name)throws SQLException{try(PreparedStatement p=c.prepareStatement("SELECT to_regclass(?) IS NOT NULL")){p.setString(1,"public."+name);try(ResultSet r=p.executeQuery()){r.next();return r.getBoolean(1);}}}
    private static void ensureRole(Connection c,int id)throws Exception{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM roles WHERE role_id=? FOR UPDATE")){p.setInt(1,id);try(ResultSet r=p.executeQuery()){if(!r.next())throw new RuleViolation(404,"ROLE_NOT_FOUND","The selected role no longer exists.");}}}
    private static void require(Connection c,int user)throws Exception{try(PreparedStatement p=c.prepareStatement("SELECT 1 FROM users u JOIN role_permissions rp ON rp.role_id=u.role_id JOIN permissions x ON x.permission_id=rp.permission_id WHERE u.user_id=? AND UPPER(x.permission_key)='ROLE_MANAGEMENT' LIMIT 1")){p.setInt(1,user);try(ResultSet r=p.executeQuery()){if(r.next())return;}}throw new RuleViolation(403,"PERMISSION_DENIED","You do not have permission to manage roles.");}
    private static Map<String,Object>map(Object...v){Map<String,Object>m=new LinkedHashMap<>();for(int i=0;i<v.length;i+=2)m.put((String)v[i],v[i+1]);return m;}
    static final class RuleViolation extends Exception{final int status;final String code;RuleViolation(int s,String c,String m){super(m);status=s;code=c;}}
}
