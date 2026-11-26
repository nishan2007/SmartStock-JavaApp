package services;

/** Product search terms inferred from existing catalog fields. */
final class ProductSearchAliases {
    private ProductSearchAliases() { }

    static String sql(String alias, String description) {
        String text = "lower(concat_ws(' '," + alias + ".name," + alias + ".size," + description + "))";
        String paper = text + " ~ '[[:<:]](paper|cardstock|card stock|folders?|binders?|notebooks?|document sleeves?|sheet protectors?|laminating pouches?|copy|print)[[:>:]]'"
                + " AND " + text + " !~ '[[:<:]](frames?|envelopes?)[[:>:]]'";
        return "concat_ws(' ',"
                + terms(text, "flash[ -]drive|thumb[ -]drive|pen[ -]drive|usb[ -]stick", "flash drive thumb drive pen drive usb stick") + ","
                + terms(text, "erasers?|rubbers?", "eraser rubber") + ","
                + terms(text, "card[ -]?stock", "cardstock card stock") + ","
                + terms(text, "dry[ -]erase|whiteboard markers?", "dry erase dry wipe whiteboard marker whiteboard pen") + ","
                + terms(text, "usb[ -]?c|type[ -]?c", "usb-c usb c type-c type c") + ","
                + terms(text, "colou?rs?", "color colour colors colours") + ","
                + millimeters(text) + ","
                + format(text,paper,"letter","8[.]5","11","letter size us letter") + ","
                + format(text,paper,"legal","8[.]5","14","legal size us legal") + ","
                + format(text,paper,"tabloid|ledger","11","17","tabloid size ledger size") + ","
                + format(text,paper,"a[ -]?3","297","420","a3 a-3") + ","
                + format(text,paper,"a[ -]?4","210","297","a4 a-4") + ","
                + format(text,paper,"a[ -]?5","148","210","a5 a-5") + ","
                + format(text,paper,"a[ -]?6","105","148","a6 a-6") + ")";
    }

    private static String millimeters(String text) {
        return "(SELECT string_agg(m[1] || 'mm ' || m[1] || ' mm', ' ') FROM regexp_matches("
                + text + ", '[[:<:]]([0-9]+([.][0-9]+)?) *mm[[:>:]]', 'g') AS matches(m))";
    }

    private static String terms(String text, String pattern, String synonyms) {
        return "CASE WHEN " + text + " ~ '[[:<:]](" + pattern + ")[[:>:]]' THEN '" + synonyms + "' END";
    }

    private static String format(String text, String scope, String names, String width, String height, String aliases) {
        String separator = "[[:space:]]*(x|×|by)[[:space:]]*";
        String dimensions = width + separator + height + "|" + height + separator + width;
        String w = width.replace("[.]", ".");
        String unit = names.startsWith("a[") ? " mm" : "";
        return "CASE WHEN (" + scope + ") AND " + text + " ~ '[[:<:]](" + names + "|" + dimensions
                + ")[[:>:]]' THEN '" + aliases + " " + w + " x " + height + " " + w + " by " + height
                + " " + w + "x" + height + " " + w + " × " + height + unit + "' END";
    }
}
