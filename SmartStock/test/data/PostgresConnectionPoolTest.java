package data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresConnectionPoolTest {
    private final AtomicInteger physicalConnections = new AtomicInteger();
    private Driver driver;

    @BeforeEach
    void registerDriver() throws Exception {
        driver = new Driver() {
            @Override public Connection connect(String url, Properties info) {
                if (!acceptsURL(url)) return null;
                physicalConnections.incrementAndGet();
                return fakeConnection();
            }
            @Override public boolean acceptsURL(String url) { return url != null && url.startsWith("jdbc:smartstock-test:"); }
            @Override public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) { return new DriverPropertyInfo[0]; }
            @Override public int getMajorVersion() { return 1; }
            @Override public int getMinorVersion() { return 0; }
            @Override public boolean jdbcCompliant() { return false; }
            @Override public Logger getParentLogger() { return Logger.getGlobal(); }
        };
        DriverManager.registerDriver(driver);
    }

    @AfterEach
    void deregisterDriver() throws Exception {
        DriverManager.deregisterDriver(driver);
    }

    @Test
    void reusesPhysicalConnectionAndTreatsCloseAsReturnToPool() throws Exception {
        try (PostgresConnectionPool pool = new PostgresConnectionPool("jdbc:smartstock-test:pool", "user", "password")) {
            Connection first = pool.borrow();
            assertTrue(first.isValid(1));
            first.close();
            assertTrue(first.isClosed());
            assertThrows(java.sql.SQLException.class, first::getCatalog);

            Connection second = pool.borrow();
            assertTrue(second.isValid(1));
            second.close();

            assertEquals(1, physicalConnections.get());
        }
    }

    private static Connection fakeConnection() {
        AtomicBoolean closed = new AtomicBoolean();
        AtomicBoolean autoCommit = new AtomicBoolean(true);
        AtomicBoolean readOnly = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "close" -> { closed.set(true); yield null; }
                    case "isClosed" -> closed.get();
                    case "isValid" -> !closed.get();
                    case "getAutoCommit" -> autoCommit.get();
                    case "setAutoCommit" -> { autoCommit.set((Boolean) args[0]); yield null; }
                    case "isReadOnly" -> readOnly.get();
                    case "setReadOnly" -> { readOnly.set((Boolean) args[0]); yield null; }
                    case "rollback", "clearWarnings" -> null;
                    case "toString" -> "fake physical connection";
                    default -> {
                        if (method.getReturnType() == boolean.class) yield false;
                        if (method.getReturnType() == int.class) yield 0;
                        if (method.getReturnType() == long.class) yield 0L;
                        yield null;
                    }
                });
    }
}
