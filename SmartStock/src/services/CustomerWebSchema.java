package services;

import java.sql.*;

/** Separate private schema; never included in public register snapshots. */
final class CustomerWebSchema {
    private CustomerWebSchema() { }
    static void ensure(Connection c)throws SQLException {
        try(var p=c.prepareStatement("SELECT to_regclass('customer_auth.identities')");var r=p.executeQuery()){r.next();if(r.getObject(1)!=null)return;}
        if(!c.getAutoCommit())throw new SQLException("Customer authentication installation requires its own transaction.");
        c.setAutoCommit(false);try{try(var s=c.createStatement()){s.execute("SELECT pg_advisory_xact_lock(734891232)");}SqlScriptRunner.runResource(c,"database/customer-auth/001_schema.sql");c.commit();}catch(Exception e){c.rollback();throw new SQLException("Customer authentication schema installation failed.",e);}finally{c.setAutoCommit(true);}
    }
}
