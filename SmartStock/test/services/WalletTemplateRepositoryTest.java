package services;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Tests transaction ownership and parameter binding, not a substitute for deployed PostgreSQL tests. */
class WalletTemplateRepositoryTest {
    @Test void saveAndReadAreStoreScopedAndPreserveTemplate() throws Exception {
        Map<Integer,Object> bound=new HashMap<>();List<String> sql=new ArrayList<>();String[] persisted={""};
        Connection c=(Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Connection.class},(proxy,method,args)->{
            if(method.getName().equals("prepareStatement")){
                sql.add((String)args[0]);bound.clear();
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{PreparedStatement.class},(p,m,a)->{
                    if(m.getName().startsWith("set")){bound.put((Integer)a[0],a[1]);return null;}
                    if(m.getName().equals("executeUpdate")){assertEquals(7,bound.get(1));persisted[0]=(String)bound.get(2);return 1;}
                    if(m.getName().equals("executeQuery")){
                        assertEquals(7,bound.get(1));boolean[] first={true};
                        return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{ResultSet.class},(r,rm,ra)->{
                            if(rm.getName().equals("next")){boolean result=first[0];first[0]=false;return result;}
                            if(rm.getName().equals("getString"))return persisted[0];
                            if(rm.getName().equals("close"))return null;
                            throw new UnsupportedOperationException(rm.getName());
                        });
                    }
                    if(m.getName().equals("close"))return null;
                    throw new UnsupportedOperationException(m.getName());
                });
            }
            throw new AssertionError("Repository must not own transactions or close its caller's connection: "+method.getName());
        });
        WalletTemplateRepository.save(c,7,WalletBadgeTemplate.defaults().json());
        assertEquals(WalletBadgeTemplate.defaults(),WalletTemplateRepository.load(c,7));
        assertTrue(sql.get(0).contains("ON CONFLICT(location_id)"));
        assertTrue(sql.get(1).contains("WHERE location_id=?"));
        assertThrows(RuntimeException.class,()->WalletTemplateRepository.save(c,7,"null"));
        assertEquals(2,sql.size(),"Invalid input must fail before preparing any SQL");
    }
}
