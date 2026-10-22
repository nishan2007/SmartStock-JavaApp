package services;

import com.google.gson.JsonObject;
import java.sql.*;

/** Shares print configuration using names rather than store-local sequence IDs. */
final class PrintReferenceSync {
    private PrintReferenceSync() { }
    static void apply(Connection c, String table, JsonObject source) throws SQLException {
        JsonObject row=source.deepCopy();
        String fields, conflict;
        switch(table) {
            case "custom_order_print_materials" -> {
                fields="material_name,description,is_active,pricing_mode,available_colors,created_at,updated_at";
                conflict="material_name";
                if(!row.has("available_colors"))row.add("available_colors",new JsonObject());
            }
            case "custom_order_design_placements" -> {
                fields="placement_name,sort_order,is_active,created_at,updated_at";
                conflict="placement_name";
            }
            case "custom_order_print_size_presets" -> {
                try(var p=c.prepareStatement("SELECT print_material_id FROM custom_order_print_materials WHERE material_name=?")) {
                    p.setString(1,row.get("material_name").getAsString());
                    try(var r=p.executeQuery()) {
                        if(!r.next())throw new SQLException("Print preset is waiting for its material; sync will retry.");
                        row.addProperty("print_material_id",r.getLong(1));
                    }
                }
                fields="print_material_id,preset_name,pricing_mode,fixed_price,is_active,created_at,updated_at";
                conflict="print_material_id,preset_name";
            }
            default -> throw new SQLException("Unsupported print reference table.");
        }
        String updates=java.util.Arrays.stream(fields.split(","))
                .filter(f->!f.equals("created_at") && !java.util.List.of(conflict.split(",")).contains(f))
                .map(f->f+"=EXCLUDED."+f).collect(java.util.stream.Collectors.joining(","));
        try(var p=c.prepareStatement("INSERT INTO "+table+" ("+fields+") SELECT "+fields
                +" FROM jsonb_populate_record(NULL::"+table+",?::jsonb) ON CONFLICT ("+conflict
                +") DO UPDATE SET "+updates+" WHERE "+table+".updated_at<EXCLUDED.updated_at")) {
            p.setString(1,row.toString());p.executeUpdate();
        }
    }
}
