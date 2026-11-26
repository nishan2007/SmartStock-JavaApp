package services;

import java.util.List;
import java.util.Locale;

/** Shared production stages; STARTED remains the persisted code for In progress. */
public final class CustomOrderProductionStages {
    private CustomOrderProductionStages() {}
    public static final List<String> CODES = List.of("NOT_STARTED", "STARTED", "WAITING_FOR_APPROVAL", "DESIGN_APPROVED", "PRINTED", "FINISHED", "QUALITY_CHECKED", "READY");
    public static String[] codes() { return CODES.toArray(String[]::new); }
    public static String label(String code) {
        return switch (code) {
            case "NOT_STARTED" -> "Not started";
            case "STARTED" -> "In progress";
            case "WAITING_FOR_APPROVAL" -> "Waiting for approval";
            case "DESIGN_APPROVED" -> "Design approved";
            case "PRINTED" -> "Printed";
            case "FINISHED" -> "Finished";
            case "QUALITY_CHECKED" -> "Quality checked";
            case "READY" -> "Ready";
            case "MIXED" -> "Mixed — choose a stage";
            default -> code;
        };
    }
    public static String code(String label) {
        String value = label == null ? "" : label.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return "IN_PROGRESS".equals(value) ? "STARTED" : value;
    }
    public static String summarize(String states) {
        if(states==null)return "NOT_STARTED";
        var values=java.util.Arrays.stream(states.split(",")).filter(CODES::contains).distinct().toList();
        return values.isEmpty()?"NOT_STARTED":values.size()==1?values.get(0):"MIXED";
    }
}
