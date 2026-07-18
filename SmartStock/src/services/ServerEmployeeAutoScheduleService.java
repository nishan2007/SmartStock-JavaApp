package services;

import data.DB;
import services.ServerEmployeeScheduleService.Employee;
import services.ServerEmployeeScheduleService.Shift;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;

public final class ServerEmployeeAutoScheduleService {
    private static final int TARGET_WORK_DAYS = 6;
    private static final int MIN_DAILY_STAFF = 2;
    private static final int HISTORY_WEEKS = 4;
    private static final LocalTime PREFERRED_LUNCH_START = LocalTime.of(11, 30);
    private static final LocalTime PREFERRED_LUNCH_END = LocalTime.of(14, 0);
    private static final LocalTime EXTENDED_LUNCH_START = LocalTime.of(11, 0);
    private static final LocalTime EXTENDED_LUNCH_END = LocalTime.of(14, 30);
    private static final ThreadLocal<RequestContext> REQUEST_CONTEXT = new ThreadLocal<>();

    public enum WarningLevel { INFO, WARNING }

    public record ScheduleWarning(WarningLevel level, LocalDate workDate, Integer userId, String message) {
    }

    public record ScheduleEntry(int userId, String displayName, String username, LocalDate workDate,
                                UUID shiftId, String shiftName, LocalTime shiftStartTime,
                                LocalTime shiftEndTime, LocalTime lunchStartTime, boolean proposed) {
    }

    public record DailyCoverage(LocalDate workDate, int existingCount, int proposedCount,
                                int totalCount, Map<String, Integer> shiftCounts) {
    }

    public record AutoScheduleProposal(UUID proposalId, int locationId,
                                       LocalDate periodStart, LocalDate periodEnd,
                                       LocalDate fingerprintStart, LocalDate fingerprintEnd,
                                       Instant generatedAt, String assignmentFingerprint,
                                       List<ScheduleEntry> entries, List<DailyCoverage> dailyCoverage,
                                       List<ScheduleWarning> warnings) {
        public List<ScheduleEntry> proposedEntries() {
            return entries.stream().filter(ScheduleEntry::proposed).toList();
        }
    }

    private record AssignmentRow(int locationId, int userId, String displayName, String username,
                                 LocalDate workDate, UUID shiftId, String shiftName,
                                 LocalTime shiftStart, LocalTime shiftEnd, LocalTime lunchStart,
                                 Timestamp updatedAt) {
    }

    private record AssignmentKey(int userId, LocalDate workDate) {
    }

    private record LunchInterval(LocalTime start, LocalTime end) {
        boolean overlaps(LunchInterval other) {
            return start.isBefore(other.end) && other.start.isBefore(end);
        }
    }

    private static final class EmployeeState {
        final Employee employee;
        final Set<LocalDate> assignedDates = new HashSet<>();
        final Map<UUID, Integer> shiftCounts = new HashMap<>();
        final EnumMap<DayOfWeek, Integer> historicalOffDays = new EnumMap<>(DayOfWeek.class);
        int historicalLunchMinutes;
        int historicalLunchCount;

        EmployeeState(Employee employee) {
            this.employee = employee;
            for (DayOfWeek day : DayOfWeek.values()) historicalOffDays.put(day, 0);
        }

        int workDays() {
            return assignedDates.size();
        }

        int averageLunchMinutes() {
            return historicalLunchCount == 0 ? 12 * 60 + 30 : historicalLunchMinutes / historicalLunchCount;
        }
    }

    private ServerEmployeeAutoScheduleService() {
    }

    public static void bindRequest(int userId,int locationId,String displayName,Set<String>permissions){REQUEST_CONTEXT.set(new RequestContext(userId,locationId,displayName,permissions==null?Set.of():Set.copyOf(permissions)));}
    public static void clearRequest(){REQUEST_CONTEXT.remove();}
    private static RequestContext request(){RequestContext c=REQUEST_CONTEXT.get();if(c==null)throw new IllegalStateException("Server automatic schedule request context is missing.");return c;}

    public static AutoScheduleProposal generate(int locationId, LocalDate requestedWeekStart) throws SQLException {
        return generate(locationId, requestedWeekStart, new SplittableRandom());
    }

    public static AutoScheduleProposal generate(int locationId, LocalDate requestedWeekStart,
                                                RandomGenerator random) throws SQLException {
        LocalDate weekStart = requestedWeekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return generateRange(locationId, weekStart, weekStart.plusDays(6), random);
    }

    public static AutoScheduleProposal generateRange(int locationId, LocalDate periodStart,
                                                     LocalDate periodEnd) throws SQLException {
        return generateRange(locationId, periodStart, periodEnd, new SplittableRandom());
    }

    public static AutoScheduleProposal generateRange(int locationId, LocalDate periodStart,
                                                     LocalDate periodEnd,
                                                     Set<Integer> includedEmployeeIds) throws SQLException {
        return generateRange(locationId, periodStart, periodEnd, includedEmployeeIds, new SplittableRandom());
    }

    public static AutoScheduleProposal generateRange(int locationId, LocalDate requestedStart,
                                                     LocalDate requestedEnd,
                                                     RandomGenerator random) throws SQLException {
        return generateRange(locationId, requestedStart, requestedEnd, null, random);
    }

    public static AutoScheduleProposal generateRange(int locationId, LocalDate requestedStart,
                                                     LocalDate requestedEnd,
                                                     Set<Integer> includedEmployeeIds,
                                                     RandomGenerator random) throws SQLException {
        requireLocationEditAccess(locationId);
        Objects.requireNonNull(requestedStart, "periodStart");
        Objects.requireNonNull(requestedEnd, "periodEnd");
        Objects.requireNonNull(random, "random");
        if (requestedEnd.isBefore(requestedStart)) throw new SQLException("Schedule period end cannot be before its start.");
        Set<Integer> selectedEmployeeIds = includedEmployeeIds == null
                ? null : Set.copyOf(includedEmployeeIds);
        LocalDate fingerprintStart = requestedStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fingerprintEnd = requestedEnd.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        String before;
        try (Connection conn = DB.getConnection()) {
            ServerEmployeeScheduleService.ensureSchema(conn);
            before = assignmentFingerprint(conn, fingerprintStart, fingerprintEnd);
        }
        List<ScheduleEntry> entries = new ArrayList<>();
        List<DailyCoverage> coverage = new ArrayList<>();
        List<ScheduleWarning> warnings = new ArrayList<>();
        for (LocalDate week = fingerprintStart; !week.isAfter(fingerprintEnd); week = week.plusWeeks(1)) {
            AutoScheduleProposal weekly = generateSingleWeek(locationId, week, selectedEmployeeIds, random);
            weekly.entries().stream()
                    .filter(entry -> !entry.workDate().isBefore(requestedStart) && !entry.workDate().isAfter(requestedEnd))
                    .forEach(entries::add);
            weekly.dailyCoverage().stream()
                    .filter(day -> !day.workDate().isBefore(requestedStart) && !day.workDate().isAfter(requestedEnd))
                    .forEach(coverage::add);
            weekly.warnings().stream()
                    .filter(warning -> warning.workDate() == null
                            || (!warning.workDate().isBefore(requestedStart) && !warning.workDate().isAfter(requestedEnd)))
                    .forEach(warnings::add);
        }
        String after;
        try (Connection conn = DB.getConnection()) {
            after = assignmentFingerprint(conn, fingerprintStart, fingerprintEnd);
        }
        if (!before.equals(after)) {
            throw new SQLException("The schedule changed while the automatic proposal was being generated. Regenerate it.");
        }
        entries.sort(Comparator.comparing(ScheduleEntry::workDate)
                .thenComparing(entry -> entry.shiftStartTime() == null ? LocalTime.MAX : entry.shiftStartTime())
                .thenComparing(ScheduleEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        coverage.sort(Comparator.comparing(DailyCoverage::workDate));
        return new AutoScheduleProposal(UUID.randomUUID(), locationId, requestedStart, requestedEnd,
                fingerprintStart, fingerprintEnd, Instant.now(), after, List.copyOf(entries),
                List.copyOf(coverage), deduplicateWarnings(warnings));
    }

    private static AutoScheduleProposal generateSingleWeek(int locationId, LocalDate requestedWeekStart,
                                                           Set<Integer> includedEmployeeIds,
                                                           RandomGenerator random) throws SQLException {
        requireLocationEditAccess(locationId);
        Objects.requireNonNull(random, "random");
        LocalDate weekStart = requestedWeekStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        List<Employee> employees = ServerEmployeeScheduleService.loadActiveEmployees(locationId);
        if (includedEmployeeIds != null) {
            employees = employees.stream()
                    .filter(employee -> includedEmployeeIds.contains(employee.userId()))
                    .toList();
        }
        List<Shift> shifts = ServerEmployeeScheduleService.loadShifts(locationId, false);
        List<ScheduleWarning> warnings = new ArrayList<>();
        if (employees.isEmpty()) {
            warnings.add(new ScheduleWarning(WarningLevel.WARNING, null, null,
                    includedEmployeeIds == null
                            ? "This store has no active employees available for automatic scheduling."
                            : "No active employees were selected for automatic scheduling."));
        }
        if (shifts.isEmpty()) {
            warnings.add(new ScheduleWarning(WarningLevel.WARNING, null, null,
                    "This store has no active shifts. Add or reactivate a shift before applying an automatic schedule."));
        }

        try (Connection conn = DB.getConnection()) {
            ServerEmployeeScheduleService.ensureSchema(conn);
            LocalDate today = currentDateForLocation(conn, locationId);
            LocalDate historyStart = weekStart.minusWeeks(HISTORY_WEEKS);
            List<AssignmentRow> historyAndWeek = loadAssignments(conn, historyStart, weekEnd);
            Set<LocalDate> holidayDates = loadHolidayDates(conn, weekStart, weekEnd);
            String fingerprint = assignmentFingerprint(conn, weekStart, weekEnd);

            Map<Integer, EmployeeState> states = new LinkedHashMap<>();
            for (Employee employee : employees) states.put(employee.userId(), new EmployeeState(employee));
            buildHistory(states, historyAndWeek, historyStart, weekStart, shifts);

            List<AssignmentRow> selectedWeekRows = historyAndWeek.stream()
                    .filter(row -> row.locationId() == locationId
                            && !row.workDate().isBefore(weekStart) && !row.workDate().isAfter(weekEnd))
                    .toList();
            Map<LocalDate, List<AssignmentRow>> existingByDay = groupByDay(selectedWeekRows);
            Map<AssignmentKey, AssignmentRow> anyStoreWeek = new HashMap<>();
            for (AssignmentRow row : historyAndWeek) {
                if (row.workDate().isBefore(weekStart) || row.workDate().isAfter(weekEnd)) continue;
                anyStoreWeek.putIfAbsent(new AssignmentKey(row.userId(), row.workDate()), row);
                EmployeeState state = states.get(row.userId());
                if (state != null) {
                    state.assignedDates.add(row.workDate());
                    matchShift(row, shifts).ifPresent(shift -> state.shiftCounts.merge(shift.shiftId(), 1, Integer::sum));
                }
            }

            Map<LocalDate, List<ScheduleEntry>> proposedByDay = new LinkedHashMap<>();
            for (int day = 0; day < 7; day++) proposedByDay.put(weekStart.plusDays(day), new ArrayList<>());
            Set<AssignmentKey> proposedKeys = new HashSet<>();
            List<LocalDate> generatableDays = new ArrayList<>();
            for (int day = 0; day < 7; day++) {
                LocalDate date = weekStart.plusDays(day);
                if (!date.isBefore(today) && date.getDayOfWeek() != DayOfWeek.SUNDAY
                        && !holidayDates.contains(date)) {
                    generatableDays.add(date);
                }
            }
            if (shifts.isEmpty()) generatableDays.clear();
            if (generatableDays.size() < 7) {
                if (weekStart.isBefore(today)) {
                    warnings.add(new ScheduleWarning(WarningLevel.INFO, null, null,
                            "Past dates in the selected week were preserved and skipped."));
                }
            }
            warnings.add(new ScheduleWarning(WarningLevel.INFO, weekStart.plusDays(6), null,
                    "Sunday is reserved for manual scheduling and was left unchanged."));
            for (LocalDate holidayDate : holidayDates) {
                warnings.add(new ScheduleWarning(WarningLevel.INFO, holidayDate, null,
                        "This store is marked closed for a holiday. Automatic scheduling left the day unchanged."));
            }

            // Coverage is the hard priority. Fill each future day to two people before distributing remaining workdays.
            List<LocalDate> coverageOrder = new ArrayList<>(generatableDays);
            shuffleEqual(coverageOrder, random);
            coverageOrder.sort(Comparator.comparingInt(date -> existingByDay.getOrDefault(date, List.of()).size()));
            for (LocalDate date : coverageOrder) {
                while (dailyCount(date, existingByDay, proposedByDay) < MIN_DAILY_STAFF) {
                    EmployeeState candidate = bestCoverageCandidate(date, states, anyStoreWeek, proposedKeys, random);
                    if (candidate == null) break;
                    addPlaceholder(candidate, date, proposedByDay, proposedKeys);
                }
            }

            // Give every employee up to six total working days across all stores when dates remain available.
            List<EmployeeState> employeeOrder = new ArrayList<>(states.values());
            shuffleEqual(employeeOrder, random);
            employeeOrder.sort(Comparator.comparingInt(EmployeeState::workDays));
            for (EmployeeState state : employeeOrder) {
                while (state.workDays() < TARGET_WORK_DAYS) {
                    LocalDate date = bestFairnessDate(state, generatableDays, existingByDay, proposedByDay,
                            anyStoreWeek, proposedKeys, random);
                    if (date == null) break;
                    addPlaceholder(state, date, proposedByDay, proposedKeys);
                }
            }

            assignShifts(states, shifts, existingByDay, proposedByDay, random);
            assignLunches(states, existingByDay, proposedByDay, warnings, random);

            List<ScheduleEntry> entries = new ArrayList<>();
            for (AssignmentRow row : selectedWeekRows) {
                entries.add(new ScheduleEntry(row.userId(), row.displayName(), row.username(), row.workDate(),
                        row.shiftId(), row.shiftName(), row.shiftStart(), row.shiftEnd(), row.lunchStart(), false));
                if (row.shiftId() == null || row.shiftStart() == null || row.shiftEnd() == null) {
                    warnings.add(new ScheduleWarning(WarningLevel.WARNING, row.workDate(), row.userId(),
                            row.displayName() + " has an existing assignment with no shift. It was preserved."));
                }
                if (row.lunchStart() == null) {
                    warnings.add(new ScheduleWarning(WarningLevel.WARNING, row.workDate(), row.userId(),
                            row.displayName() + " has an existing assignment with no lunch time. It was preserved."));
                }
            }
            proposedByDay.values().forEach(entries::addAll);
            entries.sort(Comparator.comparing(ScheduleEntry::workDate)
                    .thenComparing(entry -> entry.shiftStartTime() == null ? LocalTime.MAX : entry.shiftStartTime())
                    .thenComparing(ScheduleEntry::displayName, String.CASE_INSENSITIVE_ORDER));

            List<DailyCoverage> coverage = buildCoverage(weekStart, today, entries, shifts, holidayDates, warnings);
            addEmployeeWarnings(states, weekStart, weekEnd, warnings);
            addCrossStoreAndCoverageWarnings(locationId, weekStart, weekEnd, states, anyStoreWeek,
                    existingByDay, proposedByDay, warnings);

            return new AutoScheduleProposal(UUID.randomUUID(), locationId, weekStart, weekEnd,
                    weekStart, weekEnd, Instant.now(), fingerprint, List.copyOf(entries),
                    List.copyOf(coverage), deduplicateWarnings(warnings));
        }
    }

    public static int apply(Connection conn, AutoScheduleProposal proposal) throws SQLException {
        if (proposal == null) throw new SQLException("Generate an automatic schedule before applying it.");
        requireLocationEditAccess(proposal.locationId());
        List<ScheduleEntry> proposed = proposal.proposedEntries();
        if (proposed.isEmpty()) return 0;
            ServerEmployeeScheduleService.ensureSchema(conn);
                // Wait for any in-flight manual schedule or shift edit, then prevent a new one from
                // racing the fingerprint check and exact proposal insert below.
                try (java.sql.Statement statement = conn.createStatement()) {
                    statement.execute("LOCK TABLE employee_schedule_assignments, employee_schedule_shifts, employee_schedule_holidays IN SHARE MODE");
                }
                String currentFingerprint = assignmentFingerprint(conn, proposal.fingerprintStart(), proposal.fingerprintEnd());
                if (!proposal.assignmentFingerprint().equals(currentFingerprint)) {
                    throw new SQLException("The schedule changed after this preview was generated. Regenerate before applying.");
                }
                Set<LocalDate> holidayDates = loadHolidayDates(conn,
                        proposal.periodStart(), proposal.periodEnd());
                if (proposed.stream().anyMatch(entry -> holidayDates.contains(entry.workDate()))) {
                    throw new SQLException("A day in this proposal was marked as a holiday. Regenerate before applying.");
                }

                Map<UUID, Shift> activeShifts = new HashMap<>();
                try (PreparedStatement ps = conn.prepareStatement("""
                        SELECT shift_id, location_id, shift_name, start_time, end_time, is_active, display_order
                        FROM employee_schedule_shifts WHERE location_id = ? AND is_active
                        """)) {
                    ps.setInt(1, proposal.locationId());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Shift shift = new Shift(rs.getObject("shift_id", UUID.class), rs.getInt("location_id"),
                                    rs.getString("shift_name"), rs.getTime("start_time").toLocalTime(),
                                    rs.getTime("end_time").toLocalTime(), true, rs.getInt("display_order"));
                            activeShifts.put(shift.shiftId(), shift);
                        }
                    }
                }
                for (ScheduleEntry entry : proposed) {
                    Shift shift = activeShifts.get(entry.shiftId());
                    if (shift == null || !shift.name().equals(entry.shiftName())
                            || !shift.startTime().equals(entry.shiftStartTime())
                            || !shift.endTime().equals(entry.shiftEndTime())) {
                        throw new SQLException("A shift changed after this preview was generated. Regenerate before applying.");
                    }
                }

                int inserted = 0;
                try (PreparedStatement ps = conn.prepareStatement("""
                        INSERT INTO employee_schedule_assignments (
                            location_id, user_id, work_date, lunch_start_time, shift_id,
                            shift_name_snapshot, shift_start_time, shift_end_time,
                            created_by_user_id, created_by_name
                        )
                        SELECT ?, u.user_id, ?, ?, ?, ?, ?, ?, ?, ?
                        FROM users u
                        WHERE u.user_id = ? AND COALESCE(u.is_active, TRUE)
                          AND (EXISTS (SELECT 1 FROM user_locations ul WHERE ul.user_id = u.user_id AND ul.location_id = ?)
                               OR NOT EXISTS (SELECT 1 FROM user_locations any_ul WHERE any_ul.user_id = u.user_id))
                          AND NOT EXISTS (
                              SELECT 1 FROM employee_schedule_assignments existing
                              WHERE existing.user_id = u.user_id AND existing.work_date = ?
                          )
                        """)) {
                    for (ScheduleEntry entry : proposed) {
                        ps.setInt(1, proposal.locationId());
                        ps.setDate(2, Date.valueOf(entry.workDate()));
                        ps.setTime(3, java.sql.Time.valueOf(entry.lunchStartTime()));
                        ps.setObject(4, entry.shiftId());
                        ps.setString(5, entry.shiftName());
                        ps.setTime(6, java.sql.Time.valueOf(entry.shiftStartTime()));
                        ps.setTime(7, java.sql.Time.valueOf(entry.shiftEndTime()));
                        setCurrentUser(ps, 8, 9);
                        ps.setInt(10, entry.userId());
                        ps.setInt(11, proposal.locationId());
                        ps.setDate(12, Date.valueOf(entry.workDate()));
                        int changed = ps.executeUpdate();
                        if (changed != 1) {
                            throw new SQLException("An employee or assignment changed after preview. Regenerate before applying.");
                        }
                        inserted += changed;
                    }
                }
                return inserted;
    }

    private static void buildHistory(Map<Integer, EmployeeState> states, List<AssignmentRow> rows,
                                     LocalDate historyStart, LocalDate weekStart, List<Shift> shifts) {
        Map<Integer, Set<LocalDate>> historicalDates = new HashMap<>();
        for (AssignmentRow row : rows) {
            if (!row.workDate().isBefore(weekStart)) continue;
            EmployeeState state = states.get(row.userId());
            if (state == null) continue;
            historicalDates.computeIfAbsent(row.userId(), ignored -> new HashSet<>()).add(row.workDate());
            matchShift(row, shifts).ifPresent(shift -> state.shiftCounts.merge(shift.shiftId(), 1, Integer::sum));
            if (row.lunchStart() != null) {
                state.historicalLunchMinutes += row.lunchStart().getHour() * 60 + row.lunchStart().getMinute();
                state.historicalLunchCount++;
            }
        }
        for (EmployeeState state : states.values()) {
            Set<LocalDate> worked = historicalDates.getOrDefault(state.employee.userId(), Set.of());
            for (LocalDate date = historyStart; date.isBefore(weekStart); date = date.plusDays(1)) {
                if (!worked.contains(date)) state.historicalOffDays.merge(date.getDayOfWeek(), 1, Integer::sum);
            }
        }
    }

    private static EmployeeState bestCoverageCandidate(LocalDate date, Map<Integer, EmployeeState> states,
                                                        Map<AssignmentKey, AssignmentRow> anyStoreWeek,
                                                        Set<AssignmentKey> proposedKeys, RandomGenerator random) {
        List<EmployeeState> candidates = states.values().stream()
                .filter(state -> isAvailable(state.employee.userId(), date, anyStoreWeek, proposedKeys))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        shuffleEqual(candidates, random);
        candidates.sort(Comparator.comparingInt(EmployeeState::workDays)
                .thenComparingInt(state -> -state.historicalOffDays.getOrDefault(date.getDayOfWeek(), 0)));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static LocalDate bestFairnessDate(EmployeeState state, List<LocalDate> days,
                                              Map<LocalDate, List<AssignmentRow>> existingByDay,
                                              Map<LocalDate, List<ScheduleEntry>> proposedByDay,
                                              Map<AssignmentKey, AssignmentRow> anyStoreWeek,
                                              Set<AssignmentKey> proposedKeys, RandomGenerator random) {
        List<LocalDate> candidates = days.stream()
                .filter(date -> isAvailable(state.employee.userId(), date, anyStoreWeek, proposedKeys))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        shuffleEqual(candidates, random);
        candidates.sort(Comparator
                .comparingInt((LocalDate date) -> dailyCount(date, existingByDay, proposedByDay))
                .thenComparingInt(date -> -state.historicalOffDays.getOrDefault(date.getDayOfWeek(), 0)));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private static void addPlaceholder(EmployeeState state, LocalDate date,
                                       Map<LocalDate, List<ScheduleEntry>> proposedByDay,
                                       Set<AssignmentKey> proposedKeys) {
        AssignmentKey key = new AssignmentKey(state.employee.userId(), date);
        proposedKeys.add(key);
        state.assignedDates.add(date);
        proposedByDay.get(date).add(new ScheduleEntry(state.employee.userId(), state.employee.displayName(),
                state.employee.username(), date, null, null, null, null, null, true));
    }

    private static void assignShifts(Map<Integer, EmployeeState> states, List<Shift> shifts,
                                     Map<LocalDate, List<AssignmentRow>> existingByDay,
                                     Map<LocalDate, List<ScheduleEntry>> proposedByDay,
                                     RandomGenerator random) {
        if (shifts.isEmpty()) return;
        for (Map.Entry<LocalDate, List<ScheduleEntry>> dayEntry : proposedByDay.entrySet()) {
            LocalDate date = dayEntry.getKey();
            Map<UUID, Integer> dailyCounts = new HashMap<>();
            for (Shift shift : shifts) dailyCounts.put(shift.shiftId(), 0);
            for (AssignmentRow row : existingByDay.getOrDefault(date, List.of())) {
                if (row.shiftId() != null && dailyCounts.containsKey(row.shiftId())) {
                    dailyCounts.merge(row.shiftId(), 1, Integer::sum);
                }
            }
            List<ScheduleEntry> assigned = new ArrayList<>();
            List<ScheduleEntry> pending = new ArrayList<>(dayEntry.getValue());
            shuffleEqual(pending, random);
            pending.sort(Comparator.comparingInt(entry -> states.get(entry.userId()).shiftCounts.values().stream()
                    .mapToInt(Integer::intValue).sum()));
            for (ScheduleEntry entry : pending) {
                EmployeeState state = states.get(entry.userId());
                List<Shift> options = new ArrayList<>(shifts);
                shuffleEqual(options, random);
                options.sort(Comparator.comparingInt((Shift shift) -> dailyCounts.getOrDefault(shift.shiftId(), 0))
                        .thenComparingInt(shift -> state.shiftCounts.getOrDefault(shift.shiftId(), 0))
                        .thenComparingInt(Shift::displayOrder));
                Shift shift = options.get(0);
                dailyCounts.merge(shift.shiftId(), 1, Integer::sum);
                state.shiftCounts.merge(shift.shiftId(), 1, Integer::sum);
                assigned.add(new ScheduleEntry(entry.userId(), entry.displayName(), entry.username(), date,
                        shift.shiftId(), shift.name(), shift.startTime(), shift.endTime(), null, true));
            }
            dayEntry.setValue(assigned);
        }
    }

    private static void assignLunches(Map<Integer, EmployeeState> states,
                                      Map<LocalDate, List<AssignmentRow>> existingByDay,
                                      Map<LocalDate, List<ScheduleEntry>> proposedByDay,
                                      List<ScheduleWarning> warnings, RandomGenerator random) {
        for (Map.Entry<LocalDate, List<ScheduleEntry>> dayEntry : proposedByDay.entrySet()) {
            LocalDate date = dayEntry.getKey();
            List<AssignmentRow> existing = existingByDay.getOrDefault(date, List.of());
            int totalStaff = existing.size() + dayEntry.getValue().size();
            int spacingMinutes = totalStaff >= 3 ? 30 : ServerEmployeeScheduleService.LUNCH_DURATION_MINUTES;
            List<LunchInterval> occupied = new ArrayList<>();
            for (AssignmentRow row : existing) {
                if (row.lunchStart() != null) occupied.add(interval(row.lunchStart()));
            }

            List<ScheduleEntry> pending = new ArrayList<>(dayEntry.getValue());
            shuffleEqual(pending, random);
            pending.sort(Comparator.comparingInt((ScheduleEntry entry) ->
                    states.get(entry.userId()).averageLunchMinutes()).reversed());
            List<LocalTime> targets = centeredTargets(totalStaff, spacingMinutes);
            removeTargetsForExisting(targets, existing);
            List<ScheduleEntry> withLunch = new ArrayList<>();
            for (int index = 0; index < pending.size(); index++) {
                ScheduleEntry entry = pending.get(index);
                LocalTime target = index < targets.size() ? targets.get(index) : LocalTime.of(12, 30).plusMinutes((long) index * spacingMinutes);
                LocalTime lunch = findLunchTime(entry, target, occupied, totalStaff, PREFERRED_LUNCH_START, PREFERRED_LUNCH_END);
                if (lunch == null) {
                    lunch = findLunchTime(entry, target, occupied, totalStaff, EXTENDED_LUNCH_START, EXTENDED_LUNCH_END);
                }
                if (lunch == null) {
                    lunch = clampLunchToShift(entry, target);
                    warnings.add(new ScheduleWarning(WarningLevel.WARNING, date, entry.userId(),
                            "A fully covered lunch slot could not be found for " + entry.displayName() + ". Review this lunch manually."));
                }
                occupied.add(interval(lunch));
                withLunch.add(new ScheduleEntry(entry.userId(), entry.displayName(), entry.username(), date,
                        entry.shiftId(), entry.shiftName(), entry.shiftStartTime(), entry.shiftEndTime(), lunch, true));
            }
            dayEntry.setValue(withLunch);
        }
    }

    private static LocalTime findLunchTime(ScheduleEntry entry, LocalTime target, List<LunchInterval> occupied,
                                           int totalStaff, LocalTime windowStart, LocalTime windowEnd) {
        List<LocalTime> candidates = quarterHourCandidates(windowStart, windowEnd);
        candidates.sort(Comparator.comparingLong(time -> Math.abs(minutes(time) - minutes(target))));
        for (LocalTime candidate : candidates) {
            if (candidate.isBefore(entry.shiftStartTime())
                    || candidate.plusMinutes(ServerEmployeeScheduleService.LUNCH_DURATION_MINUTES).isAfter(entry.shiftEndTime())) continue;
            LunchInterval proposed = interval(candidate);
            int maxConcurrent = maxConcurrentLunches(occupied, proposed);
            if (maxConcurrent >= totalStaff) continue;
            if (totalStaff >= 3 && maxConcurrent > 2) continue;
            if (totalStaff == 2 && occupied.stream().anyMatch(proposed::overlaps)) continue;
            return candidate;
        }
        return null;
    }

    private static int maxConcurrentLunches(List<LunchInterval> occupied, LunchInterval proposed) {
        List<LunchInterval> all = new ArrayList<>(occupied);
        all.add(proposed);
        int max = 0;
        for (int minute = 11 * 60; minute <= 15 * 60; minute += 15) {
            LocalTime point = LocalTime.of(minute / 60, minute % 60);
            int concurrent = 0;
            for (LunchInterval interval : all) {
                if (!point.isBefore(interval.start) && point.isBefore(interval.end)) concurrent++;
            }
            max = Math.max(max, concurrent);
        }
        return max;
    }

    private static List<DailyCoverage> buildCoverage(LocalDate weekStart, LocalDate today, List<ScheduleEntry> entries,
                                                     List<Shift> shifts, Set<LocalDate> holidayDates,
                                                     List<ScheduleWarning> warnings) {
        List<DailyCoverage> result = new ArrayList<>();
        for (int day = 0; day < 7; day++) {
            LocalDate date = weekStart.plusDays(day);
            List<ScheduleEntry> dayEntries = entries.stream().filter(entry -> entry.workDate().equals(date)).toList();
            int existing = (int) dayEntries.stream().filter(entry -> !entry.proposed()).count();
            int proposed = dayEntries.size() - existing;
            Map<String, Integer> shiftCounts = new LinkedHashMap<>();
            for (Shift shift : shifts) shiftCounts.put(shift.name(), 0);
            for (ScheduleEntry entry : dayEntries) {
                if (entry.shiftId() != null) shiftCounts.computeIfPresent(entry.shiftName(), (ignored, count) -> count + 1);
            }
            result.add(new DailyCoverage(date, existing, proposed, dayEntries.size(), Map.copyOf(shiftCounts)));
            if (date.isBefore(today) || date.getDayOfWeek() == DayOfWeek.SUNDAY
                    || holidayDates.contains(date)) continue;
            if (dayEntries.size() < MIN_DAILY_STAFF) {
                warnings.add(new ScheduleWarning(WarningLevel.WARNING, date, null,
                        "Only " + dayEntries.size() + " employee" + (dayEntries.size() == 1 ? " is" : "s are")
                                + " scheduled; the two-person coverage target is not met."));
            }
            for (Shift shift : shifts) {
                if (dayEntries.stream().noneMatch(entry -> shift.shiftId().equals(entry.shiftId()))) {
                    warnings.add(new ScheduleWarning(WarningLevel.WARNING, date, null,
                            shift.name() + " has no scheduled employee."));
                }
            }
            if (!shiftCounts.isEmpty()) {
                int minimum = shiftCounts.values().stream().mapToInt(Integer::intValue).min().orElse(0);
                int maximum = shiftCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
                if (maximum - minimum > 1) {
                    warnings.add(new ScheduleWarning(WarningLevel.WARNING, date, null,
                            "Shift staffing differs by more than one employee. Existing assignments were preserved."));
                }
            }
            validateLunchCoverage(date, dayEntries, warnings);
        }
        return result;
    }

    private static void validateLunchCoverage(LocalDate date, List<ScheduleEntry> entries,
                                              List<ScheduleWarning> warnings) {
        if (entries.isEmpty()) return;
        for (int minute = 11 * 60; minute <= 15 * 60; minute += 15) {
            LocalTime point = LocalTime.of(minute / 60, minute % 60);
            int atLunch = 0;
            for (ScheduleEntry entry : entries) {
                if (entry.lunchStartTime() == null) continue;
                LocalTime end = entry.lunchStartTime().plusMinutes(ServerEmployeeScheduleService.LUNCH_DURATION_MINUTES);
                if (!point.isBefore(entry.lunchStartTime()) && point.isBefore(end)) atLunch++;
            }
            if (atLunch >= entries.size()) {
                warnings.add(new ScheduleWarning(WarningLevel.WARNING, date, null,
                        "Lunch coverage reaches zero around " + point + ". Review this day manually."));
                return;
            }
        }
    }

    private static void addEmployeeWarnings(Map<Integer, EmployeeState> states, LocalDate weekStart,
                                            LocalDate weekEnd, List<ScheduleWarning> warnings) {
        for (EmployeeState state : states.values()) {
            long workDays = state.assignedDates.stream()
                    .filter(date -> !date.isBefore(weekStart) && !date.isAfter(weekEnd)).count();
            if (workDays >= 7) {
                warnings.add(new ScheduleWarning(WarningLevel.WARNING, null, state.employee.userId(),
                        state.employee.displayName() + " is scheduled all seven days to maintain coverage."));
            } else if (workDays < TARGET_WORK_DAYS) {
                warnings.add(new ScheduleWarning(WarningLevel.WARNING, null, state.employee.userId(),
                        state.employee.displayName() + " has only " + workDays
                                + " working day" + (workDays == 1 ? "" : "s") + " because available dates were limited."));
            }
        }
    }

    private static void addCrossStoreAndCoverageWarnings(int locationId, LocalDate weekStart, LocalDate weekEnd,
                                                         Map<Integer, EmployeeState> states,
                                                         Map<AssignmentKey, AssignmentRow> anyStoreWeek,
                                                         Map<LocalDate, List<AssignmentRow>> existingByDay,
                                                         Map<LocalDate, List<ScheduleEntry>> proposedByDay,
                                                         List<ScheduleWarning> warnings) {
        for (EmployeeState state : states.values()) {
            for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
                AssignmentRow row = anyStoreWeek.get(new AssignmentKey(state.employee.userId(), date));
                if (row != null && row.locationId() != locationId) {
                    warnings.add(new ScheduleWarning(WarningLevel.INFO, date, state.employee.userId(),
                            state.employee.displayName() + " was unavailable because of another-store assignment."));
                }
            }
        }
        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            if (dailyCount(date, existingByDay, proposedByDay) < MIN_DAILY_STAFF) {
                // The detailed coverage warning is added by buildCoverage; this keeps the constraint explicit here.
            }
        }
    }

    private static List<ScheduleWarning> deduplicateWarnings(List<ScheduleWarning> warnings) {
        LinkedHashMap<String, ScheduleWarning> unique = new LinkedHashMap<>();
        for (ScheduleWarning warning : warnings) {
            String key = warning.level() + "|" + warning.workDate() + "|" + warning.userId() + "|" + warning.message();
            unique.putIfAbsent(key, warning);
        }
        return List.copyOf(unique.values());
    }

    private static List<AssignmentRow> loadAssignments(Connection conn, LocalDate from, LocalDate to) throws SQLException {
        String sql = """
                SELECT s.location_id, s.user_id, s.work_date, s.shift_id, s.shift_name_snapshot,
                       s.shift_start_time, s.shift_end_time, s.lunch_start_time, s.updated_at,
                       COALESCE(NULLIF(TRIM(u.full_name), ''),
                                NULLIF(TRIM(CONCAT_WS(' ', u.first_name, u.last_name)), ''), u.username) AS display_name,
                       u.username
                FROM employee_schedule_assignments s
                JOIN users u ON u.user_id = s.user_id
                WHERE s.work_date BETWEEN ? AND ?
                ORDER BY s.work_date, s.location_id, s.user_id
                """;
        List<AssignmentRow> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(new AssignmentRow(rs.getInt("location_id"), rs.getInt("user_id"),
                            rs.getString("display_name"), rs.getString("username"),
                            rs.getDate("work_date").toLocalDate(), rs.getObject("shift_id", UUID.class),
                            rs.getString("shift_name_snapshot"), toTime(rs, "shift_start_time"),
                            toTime(rs, "shift_end_time"), toTime(rs, "lunch_start_time"), rs.getTimestamp("updated_at")));
                }
            }
        }
        return rows;
    }

    private static Set<LocalDate> loadHolidayDates(Connection conn,
                                                   LocalDate from, LocalDate to) throws SQLException {
        Set<LocalDate> dates = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT holiday_date
                FROM employee_schedule_holidays
                WHERE holiday_date BETWEEN ? AND ?
                """)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) dates.add(rs.getDate(1).toLocalDate());
            }
        }
        return dates;
    }

    private static String assignmentFingerprint(Connection conn, LocalDate from, LocalDate to) throws SQLException {
        List<AssignmentRow> rows = loadAssignments(conn, from, to);
        StringBuilder canonical = new StringBuilder();
        for (AssignmentRow row : rows) {
            canonical.append(row.locationId()).append('|').append(row.userId()).append('|').append(row.workDate())
                    .append('|').append(row.shiftId()).append('|').append(row.shiftName()).append('|')
                    .append(row.shiftStart()).append('|').append(row.shiftEnd()).append('|').append(row.lunchStart())
                    .append('|').append(row.updatedAt() == null ? null : row.updatedAt().toInstant()).append('\n');
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT holiday_date, holiday_id, holiday_name, updated_at
                FROM employee_schedule_holidays
                WHERE holiday_date BETWEEN ? AND ?
                ORDER BY holiday_date
                """)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    canonical.append("HOLIDAY|").append(rs.getDate("holiday_date")).append('|')
                            .append(rs.getObject("holiday_id")).append('|')
                            .append(rs.getString("holiday_name")).append('|')
                            .append(rs.getTimestamp("updated_at").toInstant()).append('\n');
                }
            }
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new SQLException("Could not fingerprint the current schedule.", ex);
        }
    }

    private static Map<LocalDate, List<AssignmentRow>> groupByDay(List<AssignmentRow> rows) {
        Map<LocalDate, List<AssignmentRow>> result = new HashMap<>();
        for (AssignmentRow row : rows) result.computeIfAbsent(row.workDate(), ignored -> new ArrayList<>()).add(row);
        return result;
    }

    private static java.util.Optional<Shift> matchShift(AssignmentRow row, List<Shift> shifts) {
        if (row.shiftId() != null) {
            java.util.Optional<Shift> exact = shifts.stream().filter(shift -> shift.shiftId().equals(row.shiftId())).findFirst();
            if (exact.isPresent()) return exact;
        }
        if (row.shiftStart() == null || row.shiftEnd() == null) return java.util.Optional.empty();
        return shifts.stream().filter(shift -> shift.startTime().equals(row.shiftStart())
                && shift.endTime().equals(row.shiftEnd())).findFirst();
    }

    private static boolean isAvailable(int userId, LocalDate date,
                                       Map<AssignmentKey, AssignmentRow> anyStoreWeek,
                                       Set<AssignmentKey> proposedKeys) {
        AssignmentKey key = new AssignmentKey(userId, date);
        return !anyStoreWeek.containsKey(key) && !proposedKeys.contains(key);
    }

    private static int dailyCount(LocalDate date, Map<LocalDate, List<AssignmentRow>> existingByDay,
                                  Map<LocalDate, List<ScheduleEntry>> proposedByDay) {
        return existingByDay.getOrDefault(date, List.of()).size()
                + proposedByDay.getOrDefault(date, List.of()).size();
    }

    private static List<LocalTime> centeredTargets(int totalStaff, int spacingMinutes) {
        List<LocalTime> targets = new ArrayList<>();
        if (totalStaff <= 0) return targets;
        int centerMinutes = 12 * 60 + 30;
        int first = centerMinutes - ((totalStaff - 1) * spacingMinutes) / 2;
        first = Math.round(first / 15f) * 15;
        for (int index = 0; index < totalStaff; index++) targets.add(timeFromMinutes(first + index * spacingMinutes));
        return targets;
    }

    private static void removeTargetsForExisting(List<LocalTime> targets, List<AssignmentRow> existing) {
        for (AssignmentRow row : existing) {
            if (row.lunchStart() == null || targets.isEmpty()) continue;
            targets.stream().min(Comparator.comparingLong(time -> Math.abs(minutes(time) - minutes(row.lunchStart()))))
                    .ifPresent(targets::remove);
        }
    }

    private static List<LocalTime> quarterHourCandidates(LocalTime start, LocalTime end) {
        List<LocalTime> result = new ArrayList<>();
        for (LocalTime value = start; !value.isAfter(end); value = value.plusMinutes(15)) result.add(value);
        return result;
    }

    private static LocalTime clampLunchToShift(ScheduleEntry entry, LocalTime target) {
        LocalTime latest = entry.shiftEndTime().minusMinutes(ServerEmployeeScheduleService.LUNCH_DURATION_MINUTES);
        if (target.isBefore(entry.shiftStartTime())) return entry.shiftStartTime();
        if (target.isAfter(latest)) return latest;
        return target;
    }

    private static LunchInterval interval(LocalTime start) {
        return new LunchInterval(start, start.plusMinutes(ServerEmployeeScheduleService.LUNCH_DURATION_MINUTES));
    }

    private static int minutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static LocalTime timeFromMinutes(int minutes) {
        int dayMinutes = Math.floorMod(minutes, 24 * 60);
        return LocalTime.of(dayMinutes / 60, dayMinutes % 60);
    }

    private static LocalTime toTime(ResultSet rs, String column) throws SQLException {
        java.sql.Time value = rs.getTime(column);
        return value == null ? null : value.toLocalTime();
    }

    private static LocalDate currentDateForLocation(Connection conn, int locationId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(NULLIF(timezone, ''), 'America/New_York') FROM locations WHERE location_id = ?")) {
            ps.setInt(1, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new SQLException("The selected store no longer exists.");
                try {
                    return LocalDate.now(ZoneId.of(rs.getString(1)));
                } catch (Exception ignored) {
                    return LocalDate.now(ZoneId.of("America/New_York"));
                }
            }
        }
    }

    private static void requireLocationEditAccess(int locationId) throws SQLException {
        if (!PermissionManager.hasPermission("EDIT_EMPLOYEE_SCHEDULE")) {
            throw new SQLException("You do not have permission to edit employee schedules.");
        }
        Integer current = SessionManager.getCurrentLocationId();
        if (current == null) throw new SQLException("Select a store before scheduling employees.");
        if (current != locationId && !PermissionManager.hasPermission("SCHEDULE_OTHER_STORES")) {
            throw new SQLException("You do not have permission to schedule employees at another store.");
        }
    }

    private static void setCurrentUser(PreparedStatement ps, int idIndex, int nameIndex) throws SQLException {
        if (SessionManager.getCurrentUserId() == null) ps.setNull(idIndex, Types.INTEGER);
        else ps.setInt(idIndex, SessionManager.getCurrentUserId());
        ps.setString(nameIndex, SessionManager.getCurrentUserDisplayName());
    }

    private static <T> void shuffleEqual(List<T> values, RandomGenerator random) {
        for (int index = values.size() - 1; index > 0; index--) {
            int target = random.nextInt(index + 1);
            T value = values.get(index);
            values.set(index, values.get(target));
            values.set(target, value);
        }
    }
    private static final class PermissionManager{private static boolean hasPermission(String k){return request().permissions().contains(k.toUpperCase(java.util.Locale.ROOT));}}
    private static final class SessionManager{private static Integer getCurrentUserId(){return request().userId();}private static Integer getCurrentLocationId(){return request().locationId();}private static String getCurrentUserDisplayName(){return request().displayName();}}
    private record RequestContext(int userId,int locationId,String displayName,Set<String>permissions){}
}
