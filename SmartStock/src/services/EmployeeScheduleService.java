package services;

import data.DB;
import managers.SessionManager;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EmployeeScheduleService {
    public static final int LUNCH_DURATION_MINUTES = 45;
    public record Employee(int userId, String displayName, String username) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    public record Assignment(int userId, String displayName, String username, LocalDate workDate, LocalTime lunchStartTime) {
        public LocalTime lunchEndTime() {
            return lunchStartTime == null ? null : lunchStartTime.plusMinutes(LUNCH_DURATION_MINUTES);
        }
    }

    private EmployeeScheduleService() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS employee_schedule_assignments (
                        location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
                        user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                        work_date DATE NOT NULL,
                        lunch_start_time TIME,
                        created_by_user_id INTEGER REFERENCES users(user_id),
                        created_by_name TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (location_id, user_id, work_date)
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS lunch_start_time TIME");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS employee_schedule_location_date_idx ON employee_schedule_assignments(location_id, work_date)");
            stmt.executeUpdate("ALTER TABLE employee_schedule_assignments ENABLE ROW LEVEL SECURITY");
            stmt.executeUpdate("""
                    DO $$
                    BEGIN
                        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
                            REVOKE ALL ON employee_schedule_assignments FROM anon;
                        END IF;
                        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
                            REVOKE ALL ON employee_schedule_assignments FROM authenticated;
                        END IF;
                        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
                            GRANT ALL ON employee_schedule_assignments TO service_role;
                        END IF;
                    END;
                    $$
                    """);
            stmt.executeUpdate("""
                    CREATE OR REPLACE FUNCTION set_employee_schedule_assignments_updated_at()
                    RETURNS TRIGGER
                    LANGUAGE plpgsql
                    SET search_path = ''
                    AS $$
                    BEGIN
                        IF TG_OP = 'INSERT' THEN
                            NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
                        ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
                            NEW.updated_at = CURRENT_TIMESTAMP;
                        END IF;
                        RETURN NEW;
                    END;
                    $$
                    """);
            stmt.executeUpdate("DROP TRIGGER IF EXISTS employee_schedule_assignments_set_updated_at ON employee_schedule_assignments");
            stmt.executeUpdate("""
                    CREATE TRIGGER employee_schedule_assignments_set_updated_at
                    BEFORE INSERT OR UPDATE ON employee_schedule_assignments
                    FOR EACH ROW EXECUTE FUNCTION set_employee_schedule_assignments_updated_at()
                    """);
        }
    }

    public static List<Employee> loadActiveEmployees(int locationId) throws SQLException {
        String sql = """
                SELECT u.user_id,
                       COALESCE(NULLIF(TRIM(u.full_name), ''),
                                NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''),
                                u.username) AS display_name,
                       u.username
                FROM users u
                WHERE COALESCE(u.is_active, TRUE) = TRUE
                  AND (
                      EXISTS (
                          SELECT 1 FROM user_locations ul
                          WHERE ul.user_id = u.user_id AND ul.location_id = ?
                      )
                      OR NOT EXISTS (
                          SELECT 1 FROM user_locations any_ul WHERE any_ul.user_id = u.user_id
                      )
                  )
                ORDER BY LOWER(COALESCE(NULLIF(TRIM(u.full_name), ''),
                                        NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''),
                                        u.username)), u.user_id
                """;
        List<Employee> employees = new ArrayList<>();
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, locationId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        employees.add(new Employee(
                                rs.getInt("user_id"),
                                rs.getString("display_name"),
                                rs.getString("username")
                        ));
                    }
                }
            }
        }
        return employees;
    }

    public static Map<LocalDate, List<Assignment>> loadWeek(int locationId, LocalDate weekStart) throws SQLException {
        String sql = """
                SELECT s.user_id, s.work_date, s.lunch_start_time,
                       COALESCE(NULLIF(TRIM(u.full_name), ''),
                                NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''),
                                u.username) AS display_name,
                       u.username
                FROM employee_schedule_assignments s
                JOIN users u ON u.user_id = s.user_id
                WHERE s.location_id = ?
                  AND s.work_date BETWEEN ? AND ?
                ORDER BY s.work_date, LOWER(COALESCE(NULLIF(TRIM(u.full_name), ''),
                                                    NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''),
                                                    u.username))
                """;
        Map<LocalDate, List<Assignment>> assignments = new LinkedHashMap<>();
        for (int day = 0; day < 7; day++) {
            assignments.put(weekStart.plusDays(day), new ArrayList<>());
        }
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, locationId);
                ps.setDate(2, Date.valueOf(weekStart));
                ps.setDate(3, Date.valueOf(weekStart.plusDays(6)));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate date = rs.getDate("work_date").toLocalDate();
                        assignments.computeIfAbsent(date, ignored -> new ArrayList<>()).add(new Assignment(
                                rs.getInt("user_id"),
                                rs.getString("display_name"),
                                rs.getString("username"),
                                date,
                                rs.getTime("lunch_start_time") == null ? null : rs.getTime("lunch_start_time").toLocalTime()
                        ));
                    }
                }
            }
        }
        return assignments;
    }

    public static void addEmployees(int locationId, LocalDate workDate, List<Employee> employees, LocalTime lunchStartTime) throws SQLException {
        if (employees == null || employees.isEmpty()) {
            return;
        }
        if (lunchStartTime == null) {
            throw new SQLException("A lunch start time is required for scheduled employees.");
        }
        String sql = """
                INSERT INTO employee_schedule_assignments (
                    location_id, user_id, work_date, lunch_start_time, created_by_user_id, created_by_name
                )
                SELECT ?, u.user_id, ?, ?, ?, ?
                FROM users u
                WHERE u.user_id = ? AND COALESCE(u.is_active, TRUE) = TRUE
                ON CONFLICT (location_id, user_id, work_date)
                DO UPDATE SET
                    created_by_user_id = EXCLUDED.created_by_user_id,
                    created_by_name = EXCLUDED.created_by_name,
                    lunch_start_time = EXCLUDED.lunch_start_time,
                    updated_at = CURRENT_TIMESTAMP
                """;
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Employee employee : employees) {
                    ps.setInt(1, locationId);
                    ps.setDate(2, Date.valueOf(workDate));
                    ps.setTime(3, java.sql.Time.valueOf(lunchStartTime));
                    if (SessionManager.getCurrentUserId() == null) {
                        ps.setNull(4, java.sql.Types.INTEGER);
                    } else {
                        ps.setInt(4, SessionManager.getCurrentUserId());
                    }
                    ps.setString(5, SessionManager.getCurrentUserDisplayName());
                    ps.setInt(6, employee.userId());
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    public static void updateLunchStartTime(int locationId, int userId, LocalDate workDate, LocalTime lunchStartTime) throws SQLException {
        if (lunchStartTime == null) {
            throw new SQLException("A lunch start time is required.");
        }
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE employee_schedule_assignments
                    SET lunch_start_time = ?
                    WHERE location_id = ? AND user_id = ? AND work_date = ?
                    """)) {
                ps.setTime(1, java.sql.Time.valueOf(lunchStartTime));
                ps.setInt(2, locationId);
                ps.setInt(3, userId);
                ps.setDate(4, Date.valueOf(workDate));
                if (ps.executeUpdate() == 0) {
                    throw new SQLException("This schedule assignment no longer exists.");
                }
            }
        }
    }

    public static void removeEmployee(int locationId, int userId, LocalDate workDate) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            ReferenceDataSyncService.recordTombstone(conn, "employee_schedule_assignments", Map.of(
                    "location_id", locationId,
                    "user_id", userId,
                    "work_date", workDate.toString()
            ));
            try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE FROM employee_schedule_assignments
                    WHERE location_id = ? AND user_id = ? AND work_date = ?
                    """)) {
                ps.setInt(1, locationId);
                ps.setInt(2, userId);
                ps.setDate(3, Date.valueOf(workDate));
                ps.executeUpdate();
            }
        }
    }
}
