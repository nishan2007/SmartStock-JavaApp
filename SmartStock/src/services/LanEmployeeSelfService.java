package services;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Server-only self-service employee credential operations. */
final class LanEmployeeSelfService {
    private LanEmployeeSelfService(){}
    static void changePin(Connection c,int userId,int locationId,char[]pin)throws Exception{
        if(!EmployeePinService.validPin(pin))throw new RuleViolation(400,"VALIDATION_ERROR","Use exactly 4–8 digits for the employee PIN.");
        try(PreparedStatement ps=c.prepareStatement("""
                SELECT u.user_id,u.username,u.full_name,u.email,u.badge_id,COALESCE(r.role_name,'USER'),
                  l.location_id,l.name,COALESCE(l.timezone,'')
                FROM users u LEFT JOIN roles r ON r.role_id=u.role_id JOIN user_locations ul ON ul.user_id=u.user_id
                JOIN locations l ON l.location_id=ul.location_id WHERE u.user_id=? AND l.location_id=? AND u.is_active=TRUE
                """)){ps.setInt(1,userId);ps.setInt(2,locationId);try(ResultSet rs=ps.executeQuery()){
            if(!rs.next())throw new RuleViolation(404,"EMPLOYEE_NOT_FOUND","The signed-in employee could not be loaded.");
            LocalAuthCacheService.saveEmployeePin(c,new LocalAuthCacheService.CachedUser(rs.getInt(1),rs.getString(2),rs.getString(3),
                    rs.getString(4),rs.getString(5),rs.getString(6),rs.getInt(7),rs.getString(8),rs.getString(9)),pin);}}
    }
    static final class RuleViolation extends Exception{private final int status;private final String code;private final String safeMessage;
        RuleViolation(int s,String c,String m){super(m);status=s;code=c;safeMessage=m;}int status(){return status;}String code(){return code;}String safeMessage(){return safeMessage;}}
}
