package services;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Generates and validates barcodes across the standard and custom-item catalogs. */
public final class CatalogBarcodeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int GENERATION_ATTEMPTS = 100;

    private CatalogBarcodeService() {
    }

    public static String generateAvailable(Connection connection) throws SQLException {
        for (int attempt = 0; attempt < GENERATION_ATTEMPTS; attempt++) {
            String barcode = generateCandidate();
            if (!conflicts(connection, barcode, null, null, null)) return barcode;
        }
        throw new SQLException("An unused barcode could not be generated. Please try again.");
    }

    public static void requireAvailable(Connection connection, Collection<String> barcodes,
                                        Integer productId, Long customItemId, Long customVariantId)
            throws SQLException {
        Set<String> normalized = new LinkedHashSet<>();
        for (String barcode : barcodes == null ? List.<String>of() : barcodes) {
            String value = BarcodeNormalizer.normalize(barcode);
            if (value.isEmpty()) continue;
            for (String candidate : BarcodeNormalizer.lookupCandidates(value)) {
                if (!normalized.add(candidate)) {
                    throw new ConflictException("The same barcode was entered more than once.");
                }
            }
        }
        for (String candidate : normalized) {
            lock(connection, candidate);
            if (conflicts(connection, candidate, productId, customItemId, customVariantId)) {
                throw new ConflictException("Another product or custom item already uses barcode " + candidate + ".");
            }
        }
    }

    static String generateCandidate() {
        StringBuilder data = new StringBuilder(12);
        data.append('2');
        for (int i = 1; i < 12; i++) data.append(RANDOM.nextInt(10));
        return data.toString() + BarcodeNormalizer.checkDigit(data.toString());
    }

    static boolean conflicts(Connection connection, String barcode, Integer productId,
                             Long customItemId, Long customVariantId) throws SQLException {
        List<String> candidates = BarcodeNormalizer.lookupCandidates(barcode);
        if (candidates.isEmpty()) return false;
        String placeholders = String.join(",", java.util.Collections.nCopies(candidates.size(), "?"));
        String normalized = "UPPER(REGEXP_REPLACE(COALESCE(barcode,''), '[\\s-]+', '', 'g'))";
        String sql = "SELECT EXISTS(" +
                "SELECT 1 FROM products WHERE " + normalized + " IN (" + placeholders + ") AND (? IS NULL OR product_id<>?) " +
                "UNION ALL SELECT 1 FROM product_barcodes WHERE " + normalized + " IN (" + placeholders + ") AND (? IS NULL OR product_id<>?) " +
                "UNION ALL SELECT 1 FROM custom_order_items WHERE " + normalized + " IN (" + placeholders + ") AND (? IS NULL OR custom_item_id<>?) " +
                "UNION ALL SELECT 1 FROM custom_order_item_barcodes WHERE " + normalized + " IN (" + placeholders + ") AND (? IS NULL OR custom_item_id<>?) " +
                "UNION ALL SELECT 1 FROM custom_order_item_variants WHERE " + normalized + " IN (" + placeholders + ") AND (? IS NULL OR custom_variant_id<>?) " +
                "UNION ALL SELECT 1 FROM custom_order_item_variant_barcodes WHERE " + normalized + " IN (" + placeholders + ") AND (? IS NULL OR custom_variant_id<>?)" +
                ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            index = bindSection(ps, index, candidates, productId);
            index = bindSection(ps, index, candidates, productId);
            index = bindSection(ps, index, candidates, customItemId);
            index = bindSection(ps, index, candidates, customItemId);
            index = bindSection(ps, index, candidates, customVariantId);
            bindSection(ps, index, candidates, customVariantId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private static int bindSection(PreparedStatement ps, int index, List<String> candidates,
                                   Number excludedId) throws SQLException {
        for (String candidate : candidates) ps.setString(index++, candidate);
        if (excludedId == null) {
            ps.setNull(index++, Types.BIGINT);
            ps.setNull(index++, Types.BIGINT);
        } else {
            ps.setLong(index++, excludedId.longValue());
            ps.setLong(index++, excludedId.longValue());
        }
        return index;
    }

    private static void lock(Connection connection, String barcode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
            ps.setString(1, barcode);
            ps.executeQuery();
        }
    }

    public static final class ConflictException extends SQLException {
        ConflictException(String message) {
            super(message);
        }
    }
}
