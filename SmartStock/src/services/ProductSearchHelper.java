package services;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class ProductSearchHelper {
    private ProductSearchHelper() {
    }

    public static String predicate(String productAlias, Integer locationId, String searchText) {
        return predicate(productAlias, locationId, searchText, true);
    }

    public static String predicate(String productAlias, Integer locationId, String searchText, boolean includeVendor) {
        List<String> tokens = tokens(searchText);
        if (tokens.isEmpty()) {
            return "TRUE";
        }
        String expression = searchableTextExpression(productAlias, locationId, includeVendor);
        return String.join(" AND ", java.util.Collections.nCopies(tokens.size(), expression + " ILIKE ?"));
    }

    public static int bindTokens(PreparedStatement ps, int startIndex, String searchText) throws SQLException {
        int index = startIndex;
        for (String token : tokens(searchText)) {
            ps.setString(index++, "%" + token + "%");
        }
        return index;
    }

    public static boolean textMatches(String searchableText, String searchText) {
        String haystack = normalize(searchableText);
        for (String token : tokens(searchText)) {
            if (!haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    public static String searchableTextExpression(String productAlias, Integer locationId) {
        return searchableTextExpression(productAlias, locationId, true);
    }

    public static String searchableTextExpression(String productAlias, Integer locationId, boolean includeVendor) {
        String p = safeAlias(productAlias);
        String locationPredicate = locationId == null ? "" : " AND psa.location_id = " + locationId;
        String vendorExpression = includeVendor
                ? "COALESCE((SELECT v.name FROM vendors v WHERE v.vendor_id = " + p + ".vendor_id), ''),"
                : "";
        return """
                UPPER(CONCAT_WS(' ',
                    CAST(%1$s.product_id AS TEXT),
                    COALESCE(%1$s.name, ''),
                    COALESCE(%1$s.size, ''),
                    COALESCE(%1$s.description, ''),
                    COALESCE(%1$s.sku, ''),
                    COALESCE(%1$s.barcode, ''),
                    COALESCE(%1$s.product_type, ''),
                    COALESCE((SELECT c.name FROM categories c WHERE c.category_id = %1$s.category_id), ''),
                    COALESCE((SELECT it.name FROM item_types it WHERE it.item_type_id = %1$s.item_type_id), ''),
                    COALESCE((SELECT ib.name FROM item_brands ib WHERE ib.brand_id = %1$s.brand_id), ''),
                    %3$s
                    COALESCE((SELECT STRING_AGG(pb.barcode, ' ') FROM product_barcodes pb WHERE pb.product_id = %1$s.product_id), ''),
                    COALESCE((
                        SELECT STRING_AGG(CONCAT_WS(' ', sl.name, ssl.name), ' ')
                        FROM product_shelf_assignments psa
                        LEFT JOIN shelf_locations sl ON sl.shelf_location_id = psa.shelf_location_id
                        LEFT JOIN shelf_locations ssl ON ssl.shelf_location_id = psa.storage_shelf_location_id
                        WHERE psa.product_id = %1$s.product_id%2$s
                    ), '')
                ))
                """.formatted(p, locationPredicate, vendorExpression).trim();
    }

    public static String customItemPredicate(String customItemAlias, String searchText) {
        List<String> tokens = tokens(searchText);
        if (tokens.isEmpty()) {
            return "TRUE";
        }
        String expression = customItemSearchableTextExpression(customItemAlias);
        return String.join(" AND ", java.util.Collections.nCopies(tokens.size(), expression + " ILIKE ?"));
    }

    public static String customVariantPredicate(String customItemAlias, String variantAlias, String searchText) {
        List<String> tokens = tokens(searchText);
        if (tokens.isEmpty()) {
            return "TRUE";
        }
        String coi = safeAlias(customItemAlias);
        String coiv = safeAlias(variantAlias);
        String expression = """
                UPPER(CONCAT_WS(' ',
                    %1$s,
                    COALESCE(%2$s.variant_name, ''),
                    COALESCE(%2$s.sku, ''),
                    COALESCE(%2$s.barcode, '')
                ))
                """.formatted(customItemSearchableTextExpression(coi), coiv).trim();
        return String.join(" AND ", java.util.Collections.nCopies(tokens.size(), expression + " ILIKE ?"));
    }

    public static String customItemSearchableTextExpression(String customItemAlias) {
        String coi = safeAlias(customItemAlias);
        return """
                UPPER(CONCAT_WS(' ',
                    CAST(%1$s.custom_item_id AS TEXT),
                    COALESCE(%1$s.item_name, ''),
                    COALESCE(%1$s.description, ''),
                    COALESCE(%1$s.sku, ''),
                    COALESCE(%1$s.barcode, ''),
                    COALESCE(%1$s.product_type, ''),
                    COALESCE((SELECT c.name FROM categories c WHERE c.category_id = %1$s.category_id), ''),
                    COALESCE((SELECT it.name FROM item_types it WHERE it.item_type_id = %1$s.item_type_id), ''),
                    COALESCE((SELECT ib.name FROM item_brands ib WHERE ib.brand_id = %1$s.brand_id), ''),
                    COALESCE((SELECT STRING_AGG(coib.barcode, ' ')
                              FROM custom_order_item_barcodes coib
                              WHERE coib.custom_item_id = %1$s.custom_item_id), '')
                ))
                """.formatted(coi).trim();
    }

    public static List<String> tokens(String searchText) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String part : normalize(searchText).split("\\s+")) {
            if (!part.isBlank()) {
                unique.add(part);
            }
        }
        return new ArrayList<>(unique);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String safeAlias(String alias) {
        if (alias == null || !alias.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("Invalid SQL alias.");
        }
        return alias;
    }
}
