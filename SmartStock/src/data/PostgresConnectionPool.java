package data;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small bounded pool for the store server's loopback PostgreSQL connections. */
final class PostgresConnectionPool implements AutoCloseable {
    private static final int DEFAULT_MAX_SIZE = 16;
    private static final long DEFAULT_ACQUIRE_TIMEOUT_MILLIS = 10_000L;

    private final String jdbcUrl;
    private final String user;
    private final String password;
    private final int maxSize;
    private final long acquireTimeoutMillis;
    private final Deque<Connection> idle = new ArrayDeque<>();
    private int created;
    private boolean closed;

    PostgresConnectionPool(String jdbcUrl, String user, String password) {
        this.jdbcUrl = jdbcUrl;
        this.user = user;
        this.password = password;
        this.maxSize = Math.max(2, Integer.getInteger("smartstock.db.pool.maxSize", DEFAULT_MAX_SIZE));
        this.acquireTimeoutMillis = Math.max(1_000L,
                Long.getLong("smartstock.db.pool.acquireTimeoutMillis", DEFAULT_ACQUIRE_TIMEOUT_MILLIS));
    }

    Connection borrow() throws SQLException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(acquireTimeoutMillis);
        while (true) {
            Connection physical;
            boolean create = false;
            synchronized (this) {
                ensureOpen();
                physical = idle.pollFirst();
                if (physical == null && created < maxSize) {
                    created++;
                    create = true;
                } else if (physical == null) {
                    long remaining = deadline - System.nanoTime();
                    if (remaining <= 0L) {
                        throw new SQLException("Timed out waiting for a SmartStock database connection.", "08001");
                    }
                    try {
                        TimeUnit.NANOSECONDS.timedWait(this, remaining);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Interrupted while waiting for a SmartStock database connection.", "08001", ex);
                    }
                    continue;
                }
            }

            if (create) {
                try {
                    physical = DriverManager.getConnection(jdbcUrl, user, password);
                } catch (SQLException ex) {
                    synchronized (this) {
                        created--;
                        notifyAll();
                    }
                    throw ex;
                }
            }

            if (!isUsable(physical)) {
                discard(physical);
                continue;
            }
            return logicalConnection(physical);
        }
    }

    private Connection logicalConnection(Connection physical) {
        AtomicBoolean returned = new AtomicBoolean();
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("close".equals(name)) {
                        if (returned.compareAndSet(false, true)) recycle(physical);
                        return null;
                    }
                    if ("isClosed".equals(name)) {
                        return returned.get() || physical.isClosed();
                    }
                    if ("unwrap".equals(name)) {
                        Class<?> type = (Class<?>) args[0];
                        if (type.isInstance(proxy)) return proxy;
                        if (type.isInstance(physical)) return physical;
                    }
                    if ("isWrapperFor".equals(name)) {
                        Class<?> type = (Class<?>) args[0];
                        return type.isInstance(proxy) || type.isInstance(physical) || physical.isWrapperFor(type);
                    }
                    if ("toString".equals(name)) return "SmartStock pooled connection";
                    if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                    if ("equals".equals(name)) return proxy == args[0];
                    if (returned.get()) throw new SQLException("Connection is closed.");
                    try {
                        return method.invoke(physical, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                });
    }

    private void recycle(Connection physical) {
        boolean reusable = reset(physical);
        synchronized (this) {
            if (!closed && reusable) {
                idle.addLast(physical);
            } else {
                closePhysical(physical);
                created--;
            }
            notifyAll();
        }
    }

    private boolean reset(Connection connection) {
        try {
            if (connection.isClosed()) return false;
            if (!connection.getAutoCommit()) {
                connection.rollback();
                connection.setAutoCommit(true);
            }
            if (connection.isReadOnly()) connection.setReadOnly(false);
            connection.clearWarnings();
            return connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }

    private static boolean isUsable(Connection connection) {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException ex) {
            return false;
        }
    }

    private void discard(Connection physical) {
        closePhysical(physical);
        synchronized (this) {
            created--;
            notifyAll();
        }
    }

    private void ensureOpen() throws SQLException {
        if (closed) throw new SQLException("SmartStock database pool is closed.", "08003");
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        while (!idle.isEmpty()) {
            closePhysical(idle.removeFirst());
            created--;
        }
        notifyAll();
    }

    private static void closePhysical(Connection connection) {
        if (connection == null) return;
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }
}
