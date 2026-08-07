-- Local store-server cache populated from other stores' Supabase mirrors.
-- These tables are read-only application caches and must never be mirrored
-- back into Supabase or used to mutate another store's inventory.
CREATE TABLE IF NOT EXISTS sync_cross_store_inventory_cache (
    source_location_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    store_name TEXT NOT NULL,
    sku TEXT,
    barcode TEXT,
    additional_barcodes TEXT,
    product_name TEXT NOT NULL,
    size TEXT,
    description TEXT,
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    reorder_level INTEGER NOT NULL DEFAULT 0,
    source_updated_at TIMESTAMPTZ,
    cache_refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (source_location_id, product_id)
);

CREATE INDEX IF NOT EXISTS sync_cross_store_inventory_store_name_idx
ON sync_cross_store_inventory_cache(source_location_id, product_name);

CREATE TABLE IF NOT EXISTS sync_cross_store_inventory_status (
    source_location_id INTEGER PRIMARY KEY,
    store_name TEXT NOT NULL,
    row_count INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    last_error TEXT,
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
