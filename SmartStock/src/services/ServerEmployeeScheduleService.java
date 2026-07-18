package services;

import data.DB;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

public final class ServerEmployeeScheduleService {
    public static final int LUNCH_DURATION_MINUTES = 45;
    private static final ThreadLocal<RequestContext> REQUEST_CONTEXT = new ThreadLocal<>();
    private static final Object SCHEMA_LOCK = new Object();
    private static volatile boolean schemaReady;

    public record StoreLocation(int locationId, String name, String timezone) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record Employee(int userId, String displayName, String username) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    public record Shift(UUID shiftId, int locationId, String name, LocalTime startTime,
                        LocalTime endTime, boolean active, int displayOrder) {
        @Override
        public String toString() {
            return name;
        }
    }

    public record Holiday(UUID holidayId, LocalDate holidayDate, String name) {
    }

    public record Assignment(int userId, String displayName, String username, LocalDate workDate,
                             LocalTime lunchStartTime, UUID shiftId, String shiftName,
                             LocalTime shiftStartTime, LocalTime shiftEndTime) {
        public LocalTime lunchEndTime() {
            return lunchStartTime == null ? null : lunchStartTime.plusMinutes(LUNCH_DURATION_MINUTES);
        }
    }

    private ServerEmployeeScheduleService() {
    }

    public static void bindRequest(int userId, int locationId, String displayName,
                                   Set<String> permissions) {
        REQUEST_CONTEXT.set(new RequestContext(userId, locationId, displayName,
                permissions == null ? Set.of() : Set.copyOf(permissions)));
    }
    public static void clearRequest() { REQUEST_CONTEXT.remove(); }
    private static RequestContext request() { RequestContext c=REQUEST_CONTEXT.get();if(c==null)throw new IllegalStateException("Server schedule request context is missing.");return c; }

    public static void ensureSchema(Connection conn) throws SQLException {
        if (schemaReady) return;
        synchronized (SCHEMA_LOCK) {
            if (schemaReady) return;
            ensureSchemaInternal(conn);
            schemaReady = true;
        }
    }

    private static void ensureSchemaInternal(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS employee_schedule_shifts (
                        shift_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
                        shift_name TEXT NOT NULL,
                        start_time TIME NOT NULL,
                        end_time TIME NOT NULL,
                        is_active BOOLEAN NOT NULL DEFAULT TRUE,
                        display_order INTEGER NOT NULL DEFAULT 0,
                        created_by_user_id INTEGER REFERENCES users(user_id),
                        created_by_name TEXT,
                        updated_by_user_id INTEGER REFERENCES users(user_id),
                        updated_by_name TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT employee_schedule_shifts_daytime_check CHECK (end_time > start_time),
                        CONSTRAINT employee_schedule_shifts_location_identity UNIQUE (location_id, shift_id)
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE employee_schedule_shifts ADD COLUMN IF NOT EXISTS updated_by_user_id INTEGER REFERENCES users(user_id)");
            stmt.executeUpdate("ALTER TABLE employee_schedule_shifts ADD COLUMN IF NOT EXISTS updated_by_name TEXT");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS employee_schedule_shifts_location_name_idx ON employee_schedule_shifts(location_id, LOWER(TRIM(shift_name)))");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS employee_schedule_shifts_location_order_idx ON employee_schedule_shifts(location_id, is_active DESC, display_order, start_time)");
            stmt.executeUpdate("ALTER TABLE employee_schedule_shifts ENABLE ROW LEVEL SECURITY");
            stmt.executeUpdate("""
                    DO $$
                    BEGIN
                        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
                            REVOKE ALL ON employee_schedule_shifts FROM anon;
                        END IF;
                        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
                            REVOKE ALL ON employee_schedule_shifts FROM authenticated;
                        END IF;
                        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
                            GRANT ALL ON employee_schedule_shifts TO service_role;
                        END IF;
                    END;
                    $$
                    """);
            stmt.executeUpdate("""
                    CREATE OR REPLACE FUNCTION set_employee_schedule_shifts_updated_at()
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
            stmt.executeUpdate("DROP TRIGGER IF EXISTS employee_schedule_shifts_set_updated_at ON employee_schedule_shifts");
            stmt.executeUpdate("""
                    CREATE TRIGGER employee_schedule_shifts_set_updated_at
                    BEFORE INSERT OR UPDATE ON employee_schedule_shifts
                    FOR EACH ROW EXECUTE FUNCTION set_employee_schedule_shifts_updated_at()
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS employee_schedule_holidays (
                        holiday_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        holiday_date DATE NOT NULL UNIQUE,
                        holiday_name TEXT NOT NULL DEFAULT 'Holiday',
                        created_by_user_id INTEGER REFERENCES users(user_id),
                        created_by_name TEXT,
                        updated_by_user_id INTEGER REFERENCES users(user_id),
                        updated_by_name TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT employee_schedule_holidays_name_chk CHECK (LENGTH(TRIM(holiday_name)) > 0)
                    )
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS employee_schedule_holidays_date_idx ON employee_schedule_holidays(holiday_date)");
            stmt.executeUpdate("ALTER TABLE employee_schedule_holidays ENABLE ROW LEVEL SECURITY");
            stmt.executeUpdate("""
                    DO $$
                    BEGIN
                        REVOKE ALL ON employee_schedule_holidays FROM PUBLIC;
                        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
                            REVOKE ALL ON employee_schedule_holidays FROM anon;
                        END IF;
                        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
                            REVOKE ALL ON employee_schedule_holidays FROM authenticated;
                        END IF;
                        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'service_role') THEN
                            GRANT ALL ON employee_schedule_holidays TO service_role;
                            DROP POLICY IF EXISTS employee_schedule_holidays_service_role_all ON employee_schedule_holidays;
                            CREATE POLICY employee_schedule_holidays_service_role_all
                                ON employee_schedule_holidays FOR ALL TO service_role
                                USING (true) WITH CHECK (true);
                        END IF;
                    END;
                    $$
                    """);
            stmt.executeUpdate("""
                    CREATE OR REPLACE FUNCTION set_employee_schedule_holidays_updated_at()
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
            stmt.executeUpdate("DROP TRIGGER IF EXISTS employee_schedule_holidays_set_updated_at ON employee_schedule_holidays");
            stmt.executeUpdate("""
                    CREATE TRIGGER employee_schedule_holidays_set_updated_at
                    BEFORE INSERT OR UPDATE ON employee_schedule_holidays
                    FOR EACH ROW EXECUTE FUNCTION set_employee_schedule_holidays_updated_at()
                    """);

            stmt.executeUpdate("""
                    INSERT INTO employee_schedule_shifts (
                        shift_id, location_id, shift_name, start_time, end_time, display_order
                    )
                    SELECT (md5('employee-schedule-shift:' || l.location_id || ':0700-1600'))::uuid,
                           l.location_id, '7 AM–4 PM', TIME '07:00', TIME '16:00', 10
                    FROM locations l
                    ON CONFLICT (shift_id) DO NOTHING
                    """);
            stmt.executeUpdate("""
                    INSERT INTO employee_schedule_shifts (
                        shift_id, location_id, shift_name, start_time, end_time, display_order
                    )
                    SELECT (md5('employee-schedule-shift:' || l.location_id || ':0900-1800'))::uuid,
                           l.location_id, '9 AM–6 PM', TIME '09:00', TIME '18:00', 20
                    FROM locations l
                    ON CONFLICT (shift_id) DO NOTHING
                    """);

            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS employee_schedule_assignments (
                        location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
                        user_id INTEGER NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
                        work_date DATE NOT NULL,
                        lunch_start_time TIME,
                        shift_id UUID,
                        shift_name_snapshot TEXT,
                        shift_start_time TIME,
                        shift_end_time TIME,
                        created_by_user_id INTEGER REFERENCES users(user_id),
                        created_by_name TEXT,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (location_id, user_id, work_date),
                        CONSTRAINT employee_schedule_assignments_location_shift_fk FOREIGN KEY (location_id, shift_id)
                            REFERENCES employee_schedule_shifts(location_id, shift_id)
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS lunch_start_time TIME");
            stmt.executeUpdate("ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS shift_id UUID");
            stmt.executeUpdate("ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS shift_name_snapshot TEXT");
            stmt.executeUpdate("ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS shift_start_time TIME");
            stmt.executeUpdate("ALTER TABLE employee_schedule_assignments ADD COLUMN IF NOT EXISTS shift_end_time TIME");
            stmt.executeUpdate("""
                    DO $$
                    BEGIN
                        IF NOT EXISTS (
                            SELECT 1 FROM pg_constraint
                            WHERE conname = 'employee_schedule_assignments_location_shift_fk'
                        ) THEN
                            ALTER TABLE employee_schedule_assignments
                            ADD CONSTRAINT employee_schedule_assignments_location_shift_fk
                            FOREIGN KEY (location_id, shift_id)
                            REFERENCES employee_schedule_shifts(location_id, shift_id);
                        END IF;
                    END;
                    $$
                    """);
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS employee_schedule_location_date_idx ON employee_schedule_assignments(location_id, work_date)");
            stmt.executeUpdate("CREATE INDEX IF NOT EXISTS employee_schedule_user_date_idx ON employee_schedule_assignments(user_id, work_date)");
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

    public static List<StoreLocation> loadAccessibleLocations() throws SQLException {
        requirePermission("VIEW_EMPLOYEE_SCHEDULE", "view employee schedules");
        Integer currentLocationId = SessionManager.getCurrentLocationId();
        if (currentLocationId == null) {
            throw new SQLException("Select a store before opening the employee schedule.");
        }
        boolean allStores = PermissionManager.hasPermission("SCHEDULE_OTHER_STORES");
        String sql = allStores
                ? "SELECT location_id, name, COALESCE(NULLIF(timezone, ''), 'America/New_York') AS timezone FROM locations ORDER BY LOWER(name), location_id"
                : "SELECT location_id, name, COALESCE(NULLIF(timezone, ''), 'America/New_York') AS timezone FROM locations WHERE location_id = ?";
        List<StoreLocation> locations = new ArrayList<>();
        try (Connection conn = DB.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ensureSchema(conn);
            if (!allStores) {
                ps.setInt(1, currentLocationId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    locations.add(new StoreLocation(rs.getInt("location_id"), rs.getString("name"), rs.getString("timezone")));
                }
            }
        }
        if (locations.isEmpty()) {
            throw new SQLException("The selected store no longer exists.");
        }
        return locations;
    }

    public static List<Employee> loadActiveEmployees(int locationId) throws SQLException {
        requireLocationAccess(locationId, false);
        String sql = """
                SELECT u.user_id,
                       COALESCE(NULLIF(TRIM(u.full_name), ''),
                                NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''),
                                u.username) AS display_name,
                       u.username
                FROM users u
                WHERE COALESCE(u.is_active, TRUE) = TRUE
                  AND (
                      EXISTS (SELECT 1 FROM user_locations ul WHERE ul.user_id = u.user_id AND ul.location_id = ?)
                      OR NOT EXISTS (SELECT 1 FROM user_locations any_ul WHERE any_ul.user_id = u.user_id)
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
                        employees.add(new Employee(rs.getInt("user_id"), rs.getString("display_name"), rs.getString("username")));
                    }
                }
            }
        }
        return employees;
    }

    public static List<Shift> loadShifts(int locationId, boolean includeInactive) throws SQLException {
        requireLocationAccess(locationId, false);
        String sql = """
                SELECT shift_id, location_id, shift_name, start_time, end_time, is_active, display_order
                FROM employee_schedule_shifts
                WHERE location_id = ? AND (? OR is_active)
                ORDER BY is_active DESC, display_order, start_time, LOWER(shift_name)
                """;
        List<Shift> shifts = new ArrayList<>();
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, locationId);
                ps.setBoolean(2, includeInactive);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        shifts.add(readShift(rs));
                    }
                }
            }
        }
        return shifts;
    }

    public static Map<LocalDate, List<Assignment>> loadWeek(int locationId, LocalDate weekStart) throws SQLException {
        return loadRange(locationId, weekStart, weekStart.plusDays(6));
    }

    public static Map<LocalDate, List<Assignment>> loadRange(int locationId, LocalDate periodStart,
                                                            LocalDate periodEnd) throws SQLException {
        requireLocationAccess(locationId, false);
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new SQLException("A valid schedule date range is required.");
        }
        String sql = """
                SELECT s.user_id, s.work_date, s.lunch_start_time, s.shift_id,
                       s.shift_name_snapshot, s.shift_start_time, s.shift_end_time,
                       COALESCE(NULLIF(TRIM(u.full_name), ''),
                                NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''),
                                u.username) AS display_name,
                       u.username
                FROM employee_schedule_assignments s
                JOIN users u ON u.user_id = s.user_id
                WHERE s.location_id = ? AND s.work_date BETWEEN ? AND ?
                ORDER BY s.work_date, s.shift_start_time NULLS LAST,
                         LOWER(COALESCE(NULLIF(TRIM(u.full_name), ''),
                                        NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), u.username))
                """;
        Map<LocalDate, List<Assignment>> assignments = new LinkedHashMap<>();
        for (LocalDate date = periodStart; !date.isAfter(periodEnd); date = date.plusDays(1)) {
            assignments.put(date, new ArrayList<>());
        }
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, locationId);
                ps.setDate(2, Date.valueOf(periodStart));
                ps.setDate(3, Date.valueOf(periodEnd));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate date = rs.getDate("work_date").toLocalDate();
                        assignments.computeIfAbsent(date, ignored -> new ArrayList<>()).add(new Assignment(
                                rs.getInt("user_id"), rs.getString("display_name"), rs.getString("username"), date,
                                toLocalTime(rs, "lunch_start_time"),
                                rs.getObject("shift_id", UUID.class), rs.getString("shift_name_snapshot"),
                                toLocalTime(rs, "shift_start_time"), toLocalTime(rs, "shift_end_time")
                        ));
                    }
                }
            }
        }
        return assignments;
    }

    public static Map<LocalDate, Holiday> loadHolidays(LocalDate periodStart,
                                                       LocalDate periodEnd) throws SQLException {
        requirePermission("VIEW_EMPLOYEE_SCHEDULE", "view employee schedules");
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new SQLException("A valid holiday date range is required.");
        }
        Map<LocalDate, Holiday> holidays = new LinkedHashMap<>();
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT holiday_id, holiday_date, holiday_name
                    FROM employee_schedule_holidays
                    WHERE holiday_date BETWEEN ? AND ?
                    ORDER BY holiday_date
                    """)) {
                ps.setDate(1, Date.valueOf(periodStart));
                ps.setDate(2, Date.valueOf(periodEnd));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate date = rs.getDate("holiday_date").toLocalDate();
                        holidays.put(date, new Holiday(rs.getObject("holiday_id", UUID.class),
                                date, rs.getString("holiday_name")));
                    }
                }
            }
        }
        return holidays;
    }

    public static Map<LocalDate, Holiday> loadCurrentStoreHolidaysForTimeClock(LocalDate periodStart,
                                                                                LocalDate periodEnd) throws SQLException {
        requirePermission("TIME_CLOCK", "view store holidays on the time clock");
        return loadHolidaysDirect(periodStart, periodEnd);
    }

    private static Map<LocalDate, Holiday> loadHolidaysDirect(LocalDate periodStart,
                                                              LocalDate periodEnd) throws SQLException {
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new SQLException("A valid holiday date range is required.");
        }
        Map<LocalDate, Holiday> holidays = new LinkedHashMap<>();
        try (Connection conn = DB.getConnection()) {
            ensureSchema(conn);
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT holiday_id, holiday_date, holiday_name
                    FROM employee_schedule_holidays
                    WHERE holiday_date BETWEEN ? AND ?
                    ORDER BY holiday_date
                    """)) {
                ps.setDate(1, Date.valueOf(periodStart));
                ps.setDate(2, Date.valueOf(periodEnd));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate date = rs.getDate("holiday_date").toLocalDate();
                        holidays.put(date, new Holiday(rs.getObject("holiday_id", UUID.class),
                                date, rs.getString("holiday_name")));
                    }
                }
            }
        }
        return holidays;
    }

    public static void saveHoliday(LocalDate holidayDate, String holidayName) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            saveHoliday(conn, holidayDate, holidayName);
        }
    }

    public static void saveHoliday(Connection conn, LocalDate holidayDate, String holidayName) throws SQLException {
        requirePermission("EDIT_EMPLOYEE_SCHEDULE", "edit employee schedules");
        if (holidayDate == null) throw new SQLException("Select a holiday date.");
        String cleanName = holidayName == null ? "" : holidayName.trim();
        if (cleanName.isBlank()) cleanName = "Holiday";
        ensureSchema(conn);
        try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO employee_schedule_holidays (
                        holiday_id, holiday_date, holiday_name,
                        created_by_user_id, created_by_name, updated_by_user_id, updated_by_name
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (holiday_date)
                    DO UPDATE SET holiday_name = EXCLUDED.holiday_name,
                                  updated_by_user_id = EXCLUDED.updated_by_user_id,
                                  updated_by_name = EXCLUDED.updated_by_name,
                                  updated_at = CURRENT_TIMESTAMP
                    """)) {
                ps.setObject(1, UUID.nameUUIDFromBytes(
                        ("employee-schedule-holiday:" + holidayDate).getBytes(StandardCharsets.UTF_8)));
                ps.setDate(2, Date.valueOf(holidayDate));
                ps.setString(3, cleanName);
                setCurrentUser(ps, 4, 5);
                setCurrentUser(ps, 6, 7);
            ps.executeUpdate();
        }
    }

    public static void removeHoliday(LocalDate holidayDate) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            removeHoliday(conn, holidayDate);
        }
    }

    public static void removeHoliday(Connection conn, LocalDate holidayDate) throws SQLException {
        requirePermission("EDIT_EMPLOYEE_SCHEDULE", "edit employee schedules");
        ensureSchema(conn);
        ReferenceDataSyncService.recordTombstone(conn, "employee_schedule_holidays", Map.of(
                "holiday_date", holidayDate.toString()
        ));
        try (PreparedStatement ps = conn.prepareStatement("""
                    DELETE FROM employee_schedule_holidays
                    WHERE holiday_date = ?
                    """)) {
            ps.setDate(1, Date.valueOf(holidayDate));
            ps.executeUpdate();
        }
    }

    public static void addEmployees(int locationId, LocalDate workDate, List<Employee> employees,
                                    UUID shiftId, LocalTime lunchStartTime) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            addEmployees(conn, locationId, workDate, employees, shiftId, lunchStartTime);
        }
    }

    public static void addEmployees(Connection conn, int locationId, LocalDate workDate, List<Employee> employees,
                                    UUID shiftId, LocalTime lunchStartTime) throws SQLException {
        requireLocationAccess(locationId, true);
        if (employees == null || employees.isEmpty()) {
            return;
        }
        if (lunchStartTime == null) {
            throw new SQLException("A lunch start time is required for scheduled employees.");
        }
        ensureSchema(conn);
        Shift shift = requireShift(conn, locationId, shiftId, true);
        try (PreparedStatement ps = conn.prepareStatement("""
                    INSERT INTO employee_schedule_assignments (
                        location_id, user_id, work_date, lunch_start_time, shift_id,
                        shift_name_snapshot, shift_start_time, shift_end_time,
                        created_by_user_id, created_by_name
                    )
                    SELECT ?, u.user_id, ?, ?, ?, ?, ?, ?, ?, ?
                    FROM users u
                    WHERE u.user_id = ? AND COALESCE(u.is_active, TRUE) = TRUE
                      AND (EXISTS (SELECT 1 FROM user_locations ul WHERE ul.user_id = u.user_id AND ul.location_id = ?)
                           OR NOT EXISTS (SELECT 1 FROM user_locations any_ul WHERE any_ul.user_id = u.user_id))
                    ON CONFLICT (location_id, user_id, work_date)
                    DO UPDATE SET lunch_start_time = EXCLUDED.lunch_start_time,
                                  shift_id = EXCLUDED.shift_id,
                                  shift_name_snapshot = EXCLUDED.shift_name_snapshot,
                                  shift_start_time = EXCLUDED.shift_start_time,
                                  shift_end_time = EXCLUDED.shift_end_time,
                                  created_by_user_id = EXCLUDED.created_by_user_id,
                                  created_by_name = EXCLUDED.created_by_name,
                                  updated_at = CURRENT_TIMESTAMP
                    """)) {
                for (Employee employee : employees) {
                    ps.setInt(1, locationId);
                    ps.setDate(2, Date.valueOf(workDate));
                    ps.setTime(3, java.sql.Time.valueOf(lunchStartTime));
                    ps.setObject(4, shift.shiftId());
                    ps.setString(5, shift.name());
                    ps.setTime(6, java.sql.Time.valueOf(shift.startTime()));
                    ps.setTime(7, java.sql.Time.valueOf(shift.endTime()));
                    setCurrentUser(ps, 8, 9);
                    ps.setInt(10, employee.userId());
                    ps.setInt(11, locationId);
                    ps.addBatch();
                }
            ps.executeBatch();
        }
    }

    public static void updateAssignment(int locationId, int userId, LocalDate workDate,
                                        UUID shiftId, LocalTime lunchStartTime) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            updateAssignment(conn, locationId, userId, workDate, shiftId, lunchStartTime);
        }
    }

    public static void updateAssignment(Connection conn, int locationId, int userId, LocalDate workDate,
                                        UUID shiftId, LocalTime lunchStartTime) throws SQLException {
        requireLocationAccess(locationId, true);
        if (lunchStartTime == null) {
            throw new SQLException("A lunch start time is required.");
        }
        ensureSchema(conn);
        Shift shift = requireShift(conn, locationId, shiftId, true);
        try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE employee_schedule_assignments
                    SET lunch_start_time = ?, shift_id = ?, shift_name_snapshot = ?,
                        shift_start_time = ?, shift_end_time = ?
                    WHERE location_id = ? AND user_id = ? AND work_date = ?
                    """)) {
                ps.setTime(1, java.sql.Time.valueOf(lunchStartTime));
                ps.setObject(2, shift.shiftId());
                ps.setString(3, shift.name());
                ps.setTime(4, java.sql.Time.valueOf(shift.startTime()));
                ps.setTime(5, java.sql.Time.valueOf(shift.endTime()));
                ps.setInt(6, locationId);
                ps.setInt(7, userId);
                ps.setDate(8, Date.valueOf(workDate));
                if (ps.executeUpdate() == 0) {
                    throw new SQLException("This schedule assignment no longer exists.");
                }
        }
    }

    public static Shift saveShift(int locationId, UUID shiftId, String name, LocalTime startTime,
                                  LocalTime endTime, boolean active) throws SQLException {
        int order = 0;
        try (Connection conn = DB.getConnection(); PreparedStatement ps = conn.prepareStatement(
                shiftId == null
                        ? "SELECT COALESCE(MAX(display_order), -1) + 1 FROM employee_schedule_shifts WHERE location_id = ?"
                        : "SELECT display_order FROM employee_schedule_shifts WHERE location_id = ? AND shift_id = ?")) {
            ps.setInt(1, locationId);
            if (shiftId != null) ps.setObject(2, shiftId);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) order = rs.getInt(1); }
        }
        return saveShift(locationId, shiftId, name, startTime, endTime, active, order, true);
    }

    public static Shift saveShift(int locationId, UUID shiftId, String name, LocalTime startTime,
                                  LocalTime endTime, boolean active, int displayOrder,
                                  boolean propagateToCurrentAndFuture) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Shift result = saveShift(conn, locationId, shiftId, name, startTime, endTime, active,
                        displayOrder, propagateToCurrentAndFuture);
                conn.commit();
                return result;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static Shift saveShift(Connection conn, int locationId, UUID shiftId, String name, LocalTime startTime,
                                  LocalTime endTime, boolean active, int displayOrder,
                                  boolean propagateToCurrentAndFuture) throws SQLException {
        requireLocationAccess(locationId, true);
        String cleanName = name == null ? "" : name.trim();
        if (cleanName.isBlank()) {
            throw new SQLException("Enter a shift name.");
        }
        if (startTime == null || endTime == null || !endTime.isAfter(startTime)) {
            throw new SQLException("Shift end time must be later than its start time.");
        }
        UUID savedId = shiftId == null ? UUID.randomUUID() : shiftId;
        ensureSchema(conn);
        try {
                if (shiftId == null) {
                    try (PreparedStatement ps = conn.prepareStatement("""
                            INSERT INTO employee_schedule_shifts (
                                shift_id, location_id, shift_name, start_time, end_time,
                                is_active, display_order, created_by_user_id, created_by_name,
                                updated_by_user_id, updated_by_name
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """)) {
                        ps.setObject(1, savedId);
                        ps.setInt(2, locationId);
                        ps.setString(3, cleanName);
                        ps.setTime(4, java.sql.Time.valueOf(startTime));
                        ps.setTime(5, java.sql.Time.valueOf(endTime));
                        ps.setBoolean(6, active);
                        ps.setInt(7, displayOrder);
                        setCurrentUser(ps, 8, 9);
                        setCurrentUser(ps, 10, 11);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement("""
                            UPDATE employee_schedule_shifts
                            SET shift_name = ?, start_time = ?, end_time = ?, is_active = ?, display_order = ?,
                                updated_by_user_id = ?, updated_by_name = ?
                            WHERE location_id = ? AND shift_id = ?
                            """)) {
                        ps.setString(1, cleanName);
                        ps.setTime(2, java.sql.Time.valueOf(startTime));
                        ps.setTime(3, java.sql.Time.valueOf(endTime));
                        ps.setBoolean(4, active);
                        ps.setInt(5, displayOrder);
                        setCurrentUser(ps, 6, 7);
                        ps.setInt(8, locationId);
                        ps.setObject(9, savedId);
                        if (ps.executeUpdate() == 0) {
                            throw new SQLException("This shift no longer exists.");
                        }
                    }
                }
                if (shiftId != null && propagateToCurrentAndFuture) {
                    LocalDate today = currentDateForLocation(conn, locationId);
                    try (PreparedStatement ps = conn.prepareStatement("""
                            UPDATE employee_schedule_assignments
                            SET shift_name_snapshot = ?, shift_start_time = ?, shift_end_time = ?
                            WHERE location_id = ? AND shift_id = ? AND work_date >= ?
                            """)) {
                        ps.setString(1, cleanName);
                        ps.setTime(2, java.sql.Time.valueOf(startTime));
                        ps.setTime(3, java.sql.Time.valueOf(endTime));
                        ps.setInt(4, locationId);
                        ps.setObject(5, savedId);
                        ps.setDate(6, Date.valueOf(today));
                        ps.executeUpdate();
                    }
                }
            return new Shift(savedId, locationId, cleanName, startTime, endTime, active, displayOrder);
        } catch (SQLException ex) {
            if ("23505".equals(ex.getSQLState())) {
                throw new SQLException("A shift with that name already exists for this store.", ex);
            }
            throw ex;
        }
    }

    public static void updateShiftOrder(int locationId, List<UUID> shiftIds) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            updateShiftOrder(conn, locationId, shiftIds);
        }
    }

    public static void updateShiftOrder(Connection conn, int locationId, List<UUID> shiftIds) throws SQLException {
        requireLocationAccess(locationId, true);
        if (shiftIds == null || shiftIds.isEmpty()) {
            return;
        }
        ensureSchema(conn);
        try (PreparedStatement ps = conn.prepareStatement("""
                    UPDATE employee_schedule_shifts
                    SET display_order = ?, updated_by_user_id = ?, updated_by_name = ?
                    WHERE location_id = ? AND shift_id = ?
                    """)) {
                int order = 10;
                for (UUID shiftId : shiftIds) {
                    ps.setInt(1, order);
                    setCurrentUser(ps, 2, 3);
                    ps.setInt(4, locationId);
                    ps.setObject(5, shiftId);
                    ps.addBatch();
                    order += 10;
                }
            ps.executeBatch();
        }
    }

    public static void removeEmployee(int locationId, int userId, LocalDate workDate) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            removeEmployee(conn, locationId, userId, workDate);
        }
    }

    public static void removeEmployee(Connection conn, int locationId, int userId, LocalDate workDate) throws SQLException {
        requireLocationAccess(locationId, true);
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

    public static int clearSchedule(int locationId, LocalDate periodStart, LocalDate periodEnd) throws SQLException {
        try (Connection conn = DB.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int result = clearSchedule(conn, locationId, periodStart, periodEnd);
                conn.commit();
                return result;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    public static int clearSchedule(Connection conn, int locationId, LocalDate periodStart, LocalDate periodEnd) throws SQLException {
        requireLocationAccess(locationId, true);
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new SQLException("Select a valid schedule period to clear.");
        }
        ensureSchema(conn);
        try {
                List<Map<String, Object>> assignmentKeys = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement("""
                        SELECT user_id, work_date
                        FROM employee_schedule_assignments
                        WHERE location_id = ? AND work_date BETWEEN ? AND ?
                        FOR UPDATE
                        """)) {
                    ps.setInt(1, locationId);
                    ps.setDate(2, Date.valueOf(periodStart));
                    ps.setDate(3, Date.valueOf(periodEnd));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            assignmentKeys.add(Map.of(
                                    "location_id", locationId,
                                    "user_id", rs.getInt("user_id"),
                                    "work_date", rs.getDate("work_date").toLocalDate().toString()
                            ));
                        }
                    }
                }
                for (Map<String, Object> key : assignmentKeys) {
                    ReferenceDataSyncService.recordTombstone(conn, "employee_schedule_assignments", key);
                }
                try (PreparedStatement ps = conn.prepareStatement("""
                        DELETE FROM employee_schedule_assignments
                        WHERE location_id = ? AND work_date BETWEEN ? AND ?
                        """)) {
                    ps.setInt(1, locationId);
                    ps.setDate(2, Date.valueOf(periodStart));
                    ps.setDate(3, Date.valueOf(periodEnd));
                return ps.executeUpdate();
                }
        } catch (SQLException ex) {
            throw ex;
        }
    }

    private static void requireLocationAccess(int locationId, boolean edit) throws SQLException {
        requirePermission(edit ? "EDIT_EMPLOYEE_SCHEDULE" : "VIEW_EMPLOYEE_SCHEDULE",
                edit ? "edit employee schedules" : "view employee schedules");
        Integer current = SessionManager.getCurrentLocationId();
        if (current == null) {
            throw new SQLException("Select a store before opening the employee schedule.");
        }
        if (current != locationId && !PermissionManager.hasPermission("SCHEDULE_OTHER_STORES")) {
            throw new SQLException("You do not have permission to schedule employees at another store.");
        }
    }

    private static void requirePermission(String permission, String action) throws SQLException {
        if (!PermissionManager.hasPermission(permission)) {
            throw new SQLException("You do not have permission to " + action + ".");
        }
    }

    private static Shift requireShift(Connection conn, int locationId, UUID shiftId, boolean activeOnly) throws SQLException {
        if (shiftId == null) {
            throw new SQLException("Select a shift.");
        }
        String sql = """
                SELECT shift_id, location_id, shift_name, start_time, end_time, is_active, display_order
                FROM employee_schedule_shifts
                WHERE location_id = ? AND shift_id = ? AND (? = FALSE OR is_active)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, locationId);
            ps.setObject(2, shiftId);
            ps.setBoolean(3, activeOnly);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readShift(rs);
                }
            }
        }
        throw new SQLException("The selected shift is unavailable for this store.");
    }

    private static Shift readShift(ResultSet rs) throws SQLException {
        return new Shift(rs.getObject("shift_id", UUID.class), rs.getInt("location_id"),
                rs.getString("shift_name"), rs.getTime("start_time").toLocalTime(),
                rs.getTime("end_time").toLocalTime(), rs.getBoolean("is_active"), rs.getInt("display_order"));
    }

    private static LocalDate currentDateForLocation(Connection conn, int locationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(NULLIF(timezone, ''), 'America/New_York') FROM locations WHERE location_id = ?")) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("The selected store no longer exists.");
                }
                try {
                    return LocalDate.now(ZoneId.of(rs.getString(1)));
                } catch (Exception ignored) {
                    return LocalDate.now(ZoneId.of("America/New_York"));
                }
            }
        }
    }

    private static LocalTime toLocalTime(ResultSet rs, String column) throws SQLException {
        java.sql.Time value = rs.getTime(column);
        return value == null ? null : value.toLocalTime();
    }

    private static void setCurrentUser(PreparedStatement ps, int idIndex, int nameIndex) throws SQLException {
        if (SessionManager.getCurrentUserId() == null) {
            ps.setNull(idIndex, Types.INTEGER);
        } else {
            ps.setInt(idIndex, SessionManager.getCurrentUserId());
        }
        ps.setString(nameIndex, SessionManager.getCurrentUserDisplayName());
    }

    private static final class PermissionManager { private static boolean hasPermission(String k){return request().permissions().contains(k.toUpperCase(java.util.Locale.ROOT));} }
    private static final class SessionManager { private static Integer getCurrentUserId(){return request().userId();}private static Integer getCurrentLocationId(){return request().locationId();}private static String getCurrentUserDisplayName(){return request().displayName();} }
    private record RequestContext(int userId,int locationId,String displayName,Set<String>permissions){}
}
