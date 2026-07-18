package services;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Register-safe employee-schedule facade. */
public final class EmployeeScheduleService {
    public static final int LUNCH_DURATION_MINUTES = 45;
    private EmployeeScheduleService() { }

    public static List<StoreLocation> loadAccessibleLocations() throws SQLException { return call(LanApiClient::loadScheduleLocations); }
    public static List<Employee> loadActiveEmployees(int locationId) throws SQLException { return call(()->LanApiClient.loadScheduleEmployees(locationId)); }
    public static List<Shift> loadShifts(int locationId,boolean includeInactive)throws SQLException{return call(()->LanApiClient.loadScheduleShifts(locationId,includeInactive));}
    public static Map<LocalDate,List<Assignment>> loadWeek(int locationId,LocalDate start)throws SQLException{return loadRange(locationId,start,start.plusDays(6));}
    public static Map<LocalDate,List<Assignment>> loadRange(int locationId,LocalDate start,LocalDate end)throws SQLException{return call(()->LanApiClient.loadScheduleRange(locationId,start,end));}
    public static Map<LocalDate,Holiday> loadHolidays(LocalDate start,LocalDate end)throws SQLException{return call(()->LanApiClient.loadScheduleHolidays(start,end,false));}
    public static PeriodSnapshot loadPeriod(int locationId,LocalDate start,LocalDate end)throws SQLException{return call(()->LanApiClient.loadScheduleSnapshot(locationId,start,end));}
    public static Map<LocalDate,Holiday> loadCurrentStoreHolidaysForTimeClock(LocalDate start,LocalDate end)throws SQLException{return call(()->LanApiClient.loadScheduleHolidays(start,end,true));}
    public static void saveHoliday(LocalDate date,String name)throws SQLException{run(()->LanApiClient.updateSchedule("SAVE_HOLIDAY",new LanApiClient.ScheduleMutation(null,date,null,null,null,null,name,null,null),key()));}
    public static void removeHoliday(LocalDate date)throws SQLException{run(()->LanApiClient.updateSchedule("REMOVE_HOLIDAY",new LanApiClient.ScheduleMutation(null,date,null,null,null,null,null,null,null),key()));}
    public static void addEmployees(int locationId,LocalDate date,List<Employee>employees,UUID shiftId,LocalTime lunch)throws SQLException{run(()->LanApiClient.addScheduleEmployees(locationId,date,employees,shiftId,lunch,key()));}
    public static void updateAssignment(int locationId,int userId,LocalDate date,UUID shiftId,LocalTime lunch)throws SQLException{run(()->LanApiClient.updateSchedule("UPDATE_ASSIGNMENT",new LanApiClient.ScheduleMutation(locationId,date,userId,shiftId,lunch,null,null,null,null),key()));}
    public static Shift saveShift(int locationId,UUID shiftId,String name,LocalTime start,LocalTime end,boolean active)throws SQLException{return saveShift(locationId,shiftId,name,start,end,active,0,true);}
    public static Shift saveShift(int locationId,UUID shiftId,String name,LocalTime start,LocalTime end,boolean active,int displayOrder,boolean propagate)throws SQLException{return call(()->LanApiClient.saveScheduleShift(locationId,shiftId,name,start,end,active,displayOrder,propagate,key()));}
    public static void updateShiftOrder(int locationId,List<UUID>ids)throws SQLException{run(()->LanApiClient.updateSchedule("SHIFT_ORDER",new LanApiClient.ScheduleMutation(locationId,null,null,null,null,null,null,ids,null),key()));}
    public static void removeEmployee(int locationId,int userId,LocalDate date)throws SQLException{run(()->LanApiClient.updateSchedule("REMOVE_EMPLOYEE",new LanApiClient.ScheduleMutation(locationId,date,userId,null,null,null,null,null,null),key()));}
    public static int clearSchedule(int locationId,LocalDate start,LocalDate end)throws SQLException{return call(()->LanApiClient.clearSchedule(locationId,start,end,key()));}
    private static String key(){return UUID.randomUUID().toString();}
    private static <T>T call(ThrowingSupplier<T>s)throws SQLException{try{return s.get();}catch(Exception e){throw new SQLException("The employee schedule request could not be completed by the SmartStock server.",e);}}
    private static void run(ThrowingRunnable r)throws SQLException{try{r.run();}catch(Exception e){throw new SQLException("The employee schedule change could not be completed by the SmartStock server.",e);}}
    private interface ThrowingSupplier<T>{T get()throws Exception;}private interface ThrowingRunnable{void run()throws Exception;}

    public record StoreLocation(int locationId,String name,String timezone){@Override public String toString(){return name;}}
    public record Employee(int userId,String displayName,String username){@Override public String toString(){return displayName;}}
    public record Shift(UUID shiftId,int locationId,String name,LocalTime startTime,LocalTime endTime,boolean active,int displayOrder){@Override public String toString(){return name;}}
    public record Holiday(UUID holidayId,LocalDate holidayDate,String name){}
    public record Assignment(int userId,String displayName,String username,LocalDate workDate,LocalTime lunchStartTime,UUID shiftId,String shiftName,LocalTime shiftStartTime,LocalTime shiftEndTime){public LocalTime lunchEndTime(){return lunchStartTime==null?null:lunchStartTime.plusMinutes(LUNCH_DURATION_MINUTES);}}
    public record PeriodSnapshot(Map<LocalDate,List<Assignment>> assignments,Map<LocalDate,Holiday> holidays){}
}
