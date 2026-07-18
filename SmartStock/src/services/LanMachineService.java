package services;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Server-owned machine and machine-part repository. */
public final class LanMachineService {
    private LanMachineService() { }

    public static State state(Connection c,String search)throws SQLException {
        String value=search==null?"":search.trim(),pattern="%"+value+"%";List<MachineRow>machines=new ArrayList<>();
        String sql="""
                SELECT machine_id,machine_name,COALESCE(asset_tag,''),COALESCE(machine_type,''),status,
                       COALESCE(l.name,mm.location_name,''),next_service_date
                FROM maintenance_machines mm LEFT JOIN locations l ON l.location_id=mm.location_id
                WHERE (?='' OR machine_name ILIKE ? OR COALESCE(asset_tag,'') ILIKE ? OR COALESCE(serial_number,'') ILIKE ? OR COALESCE(machine_type,'') ILIKE ? OR COALESCE(l.name,mm.location_name,'') ILIKE ?)
                ORDER BY machine_name
                """;
        try(PreparedStatement ps=c.prepareStatement(sql)){ps.setString(1,value);for(int i=2;i<=6;i++)ps.setString(i,pattern);try(ResultSet rs=ps.executeQuery()){while(rs.next()){Date d=rs.getDate(7);machines.add(new MachineRow(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),d==null?null:d.toLocalDate()));}}}
        List<PartOption>parts=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT part_id,part_name,COALESCE(part_number,'') FROM maintenance_parts WHERE is_active=TRUE ORDER BY part_name");ResultSet rs=ps.executeQuery()){while(rs.next())parts.add(new PartOption(rs.getInt(1),rs.getString(2),rs.getString(3)));}
        List<LocationOption>locations=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT location_id,name FROM locations ORDER BY name");ResultSet rs=ps.executeQuery()){while(rs.next())locations.add(new LocationOption(rs.getInt(1),rs.getString(2)));}
        return new State(machines,parts,locations);
    }
    public static Detail detail(Connection c,int id)throws SQLException {
        Machine machine;try(PreparedStatement ps=c.prepareStatement("SELECT mm.machine_id,mm.machine_name,mm.asset_tag,mm.serial_number,mm.manufacturer,mm.model,mm.machine_type,mm.location_id,COALESCE(l.name,mm.location_name,''),mm.status,mm.purchase_date,mm.warranty_expiration_date,mm.last_service_date,mm.next_service_date,mm.notes FROM maintenance_machines mm LEFT JOIN locations l ON l.location_id=mm.location_id WHERE mm.machine_id=?")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){if(!rs.next())throw new SQLException("Machine not found.");machine=new Machine(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),nullableInt(rs,8),rs.getString(9),rs.getString(10),date(rs,11),date(rs,12),date(rs,13),date(rs,14),rs.getString(15));}}
        List<PartLink>links=new ArrayList<>();try(PreparedStatement ps=c.prepareStatement("SELECT mp.machine_part_id,p.part_name,COALESCE(p.part_number,''),COALESCE(mp.notes,'') FROM maintenance_machine_parts mp JOIN maintenance_parts p ON p.part_id=mp.part_id WHERE mp.machine_id=? ORDER BY p.part_name")){ps.setInt(1,id);try(ResultSet rs=ps.executeQuery()){while(rs.next())links.add(new PartLink(rs.getLong(1),rs.getString(2),rs.getString(3),rs.getString(4)));}}
        return new Detail(machine,links);
    }
    public static int save(Connection c,Machine m)throws SQLException {
        if(m==null||m.name()==null||m.name().trim().isBlank())throw new SQLException("Machine name is required.");
        if(!List.of("ACTIVE","NEEDS_SERVICE","DOWN","RETIRED").contains(m.status()))throw new SQLException("Machine status is invalid.");
        String sql=m.id()==null?"INSERT INTO maintenance_machines(machine_name,asset_tag,serial_number,manufacturer,model,machine_type,location_id,location_name,status,purchase_date,warranty_expiration_date,last_service_date,next_service_date,notes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)":"UPDATE maintenance_machines SET machine_name=?,asset_tag=?,serial_number=?,manufacturer=?,model=?,machine_type=?,location_id=?,location_name=?,status=?,purchase_date=?,warranty_expiration_date=?,last_service_date=?,next_service_date=?,notes=?,updated_at=CURRENT_TIMESTAMP WHERE machine_id=?";
        try(PreparedStatement ps=c.prepareStatement(sql,m.id()==null?Statement.RETURN_GENERATED_KEYS:Statement.NO_GENERATED_KEYS)){ps.setString(1,m.name().trim());ps.setString(2,blank(m.assetTag()));ps.setString(3,blank(m.serialNumber()));ps.setString(4,blank(m.manufacturer()));ps.setString(5,blank(m.model()));ps.setString(6,blank(m.type()));setInt(ps,7,m.locationId());String locationName=blank(m.locationName());if(m.locationId()!=null){try(PreparedStatement lp=c.prepareStatement("SELECT name FROM locations WHERE location_id=?")){lp.setInt(1,m.locationId());try(ResultSet rs=lp.executeQuery()){if(!rs.next())throw new SQLException("Selected store does not exist.");locationName=rs.getString(1);}}}ps.setString(8,locationName);ps.setString(9,m.status());setDate(ps,10,m.purchaseDate());setDate(ps,11,m.warrantyDate());setDate(ps,12,m.lastServiceDate());setDate(ps,13,m.nextServiceDate());ps.setString(14,blank(m.notes()));if(m.id()!=null)ps.setInt(15,m.id());ps.executeUpdate();if(m.id()!=null)return m.id();try(ResultSet rs=ps.getGeneratedKeys()){if(!rs.next())throw new SQLException("Machine ID was not returned.");return rs.getInt(1);}}
    }
    public static void delete(Connection c,int id)throws SQLException{try(PreparedStatement ps=c.prepareStatement("DELETE FROM maintenance_machines WHERE machine_id=?")){ps.setInt(1,id);if(ps.executeUpdate()==0)throw new SQLException("Machine not found.");}}
    public static void link(Connection c,int machineId,int partId,String notes)throws SQLException{try(PreparedStatement ps=c.prepareStatement("INSERT INTO maintenance_machine_parts(machine_id,part_id,notes) SELECT ?,p.part_id,? FROM maintenance_parts p WHERE p.part_id=? AND p.is_active=TRUE ON CONFLICT(machine_id,part_id) DO UPDATE SET notes=EXCLUDED.notes,updated_at=CURRENT_TIMESTAMP")){ps.setInt(1,machineId);ps.setString(2,blank(notes));ps.setInt(3,partId);if(ps.executeUpdate()==0)throw new SQLException("The selected part is not active.");}}
    public static void unlink(Connection c,long linkId)throws SQLException{try(PreparedStatement ps=c.prepareStatement("DELETE FROM maintenance_machine_parts WHERE machine_part_id=?")){ps.setLong(1,linkId);if(ps.executeUpdate()==0)throw new SQLException("Machine-part link not found.");}}
    private static Integer nullableInt(ResultSet rs,int i)throws SQLException{int v=rs.getInt(i);return rs.wasNull()?null:v;}private static LocalDate date(ResultSet rs,int i)throws SQLException{Date d=rs.getDate(i);return d==null?null:d.toLocalDate();}private static void setDate(PreparedStatement ps,int i,LocalDate v)throws SQLException{if(v==null)ps.setNull(i,Types.DATE);else ps.setDate(i,Date.valueOf(v));}private static void setInt(PreparedStatement ps,int i,Integer v)throws SQLException{if(v==null)ps.setNull(i,Types.INTEGER);else ps.setInt(i,v);}private static String blank(String v){return v==null||v.isBlank()?null:v.trim();}
    public record State(List<MachineRow>machines,List<PartOption>parts,List<LocationOption>locations){}
    public record MachineRow(int id,String name,String assetTag,String type,String status,String location,LocalDate nextServiceDate){}
    public record Machine(Integer id,String name,String assetTag,String serialNumber,String manufacturer,String model,String type,Integer locationId,String locationName,String status,LocalDate purchaseDate,LocalDate warrantyDate,LocalDate lastServiceDate,LocalDate nextServiceDate,String notes){}
    public record Detail(Machine machine,List<PartLink>parts){}
    public record PartOption(int id,String name,String partNumber){}
    public record LocationOption(int id,String name){}
    public record PartLink(long linkId,String name,String partNumber,String notes){}
}
