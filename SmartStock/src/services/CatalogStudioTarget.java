package services;

/** Uses the same signed IDs as the POS catalog; SQL identifiers are server constants. */
record CatalogStudioTarget(String table, String key, long id, String category, String folder) {
    static CatalogStudioTarget from(long catalogId) {
        if (catalogId == 0 || catalogId == Long.MIN_VALUE || catalogId == -1_000_000_000L)
            throw new IllegalArgumentException("Invalid catalog photo owner.");
        if (catalogId > 0)
            return new CatalogStudioTarget("products", "product_id", catalogId, "PRODUCT", "products");
        if (catalogId < -1_000_000_000L)
            return new CatalogStudioTarget("custom_order_item_variants", "custom_variant_id",
                    -catalogId - 1_000_000_000L, "CUSTOM_VARIANT", "custom-variants");
        return new CatalogStudioTarget("custom_order_items", "custom_item_id", -catalogId,
                "CUSTOM_ITEM", "custom-items");
    }

    String activeCondition() {
        if (category.equals("CUSTOM_ITEM")) return "is_active=TRUE AND is_archived=FALSE";
        if (category.equals("CUSTOM_VARIANT"))
            return "is_active=TRUE AND EXISTS (SELECT 1 FROM custom_order_items parent "
                    + "WHERE parent.custom_item_id=custom_order_item_variants.custom_item_id "
                    + "AND parent.is_active=TRUE AND parent.is_archived=FALSE)";
        return "is_active=TRUE";
    }

    String photoSql(boolean lock) {
        return "SELECT image_url,additional_image_urls::text FROM " + table + " WHERE " + key
                + "=? AND " + activeCondition() + (lock ? " FOR UPDATE" : "");
    }
}
