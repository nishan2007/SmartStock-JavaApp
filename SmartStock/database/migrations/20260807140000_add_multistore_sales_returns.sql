-- Local store-server cache and durable workflow for multistore sales and refunds.
CREATE TABLE IF NOT EXISTS sync_cross_store_sales_cache (
    source_location_id INTEGER NOT NULL,
    sale_id INTEGER NOT NULL,
    store_name TEXT NOT NULL,
    receipt_number TEXT,
    customer_id INTEGER,
    user_name TEXT,
    payment_method TEXT,
    payment_status TEXT,
    subtotal_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_percent NUMERIC(8,4) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    amount_paid NUMERIC(14,2) NOT NULL DEFAULT 0,
    returned_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    source_created_at TIMESTAMPTZ,
    source_updated_at TIMESTAMPTZ,
    cache_refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cache_status TEXT NOT NULL DEFAULT 'CURRENT',
    PRIMARY KEY(source_location_id,sale_id)
);
CREATE INDEX IF NOT EXISTS sync_cross_store_sales_search_idx
ON sync_cross_store_sales_cache(source_location_id,source_created_at DESC,receipt_number);

CREATE TABLE IF NOT EXISTS sync_cross_store_sale_items_cache (
    source_location_id INTEGER NOT NULL,
    sale_id INTEGER NOT NULL,
    sale_item_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    sku TEXT,
    product_name TEXT NOT NULL,
    product_type TEXT NOT NULL DEFAULT 'INVENTORY',
    quantity INTEGER NOT NULL DEFAULT 0,
    unit_price NUMERIC(14,2) NOT NULL DEFAULT 0,
    original_unit_price NUMERIC(14,2) NOT NULL DEFAULT 0,
    discount_percent NUMERIC(8,4) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    PRIMARY KEY(source_location_id,sale_item_id)
);
CREATE INDEX IF NOT EXISTS sync_cross_store_sale_items_sale_idx
ON sync_cross_store_sale_items_cache(source_location_id,sale_id);

CREATE TABLE IF NOT EXISTS sync_cross_store_returns_cache (
    source_location_id INTEGER NOT NULL,
    return_id BIGINT NOT NULL,
    sale_id INTEGER NOT NULL,
    user_name TEXT,
    refund_method TEXT,
    refund_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    reason TEXT,
    source_created_at TIMESTAMPTZ,
    PRIMARY KEY(source_location_id,return_id)
);
CREATE INDEX IF NOT EXISTS sync_cross_store_returns_sale_idx
ON sync_cross_store_returns_cache(source_location_id,sale_id);

CREATE TABLE IF NOT EXISTS sync_cross_store_return_items_cache (
    source_location_id INTEGER NOT NULL,
    return_item_id BIGINT NOT NULL,
    return_id BIGINT NOT NULL,
    sale_item_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    unit_price NUMERIC(14,2) NOT NULL DEFAULT 0,
    PRIMARY KEY(source_location_id,return_item_id)
);
CREATE INDEX IF NOT EXISTS sync_cross_store_return_items_sale_item_idx
ON sync_cross_store_return_items_cache(source_location_id,sale_item_id);

CREATE TABLE IF NOT EXISTS sync_cross_store_sales_status (
    source_location_id INTEGER PRIMARY KEY,
    store_name TEXT NOT NULL,
    row_count INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    last_error TEXT,
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cross_store_refund_requests (
    request_id UUID PRIMARY KEY,
    source_location_id INTEGER NOT NULL,
    receiving_location_id INTEGER NOT NULL,
    source_sale_id INTEGER NOT NULL,
    cloud_request_sequence BIGINT,
    refund_method TEXT NOT NULL,
    refund_amount NUMERIC(14,2) NOT NULL DEFAULT 0,
    reason TEXT NOT NULL,
    status TEXT NOT NULL,
    user_id INTEGER,
    user_name TEXT,
    device_id UUID,
    cash_drawer_id BIGINT,
    cash_drawer_session_id BIGINT,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(source_location_id,source_sale_id,request_id)
);
CREATE INDEX IF NOT EXISTS cross_store_refund_requests_status_idx
ON cross_store_refund_requests(status,created_at);

CREATE TABLE IF NOT EXISTS cross_store_refund_lines (
    request_id UUID NOT NULL REFERENCES cross_store_refund_requests(request_id) ON DELETE CASCADE,
    source_sale_item_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL CHECK(quantity > 0),
    unit_price NUMERIC(14,2) NOT NULL DEFAULT 0,
    disposition TEXT NOT NULL CHECK(disposition IN ('RESTOCK','DISCARD')),
    destination_location_id INTEGER,
    disposition_reason TEXT,
    confirmed_quantity INTEGER NOT NULL DEFAULT 0,
    conflict_quantity INTEGER NOT NULL DEFAULT 0,
    destination_status TEXT NOT NULL DEFAULT 'PENDING',
    PRIMARY KEY(request_id,source_sale_item_id)
);

CREATE TABLE IF NOT EXISTS cross_store_refund_reconciliation (
    reconciliation_id BIGSERIAL PRIMARY KEY,
    request_id UUID NOT NULL REFERENCES cross_store_refund_requests(request_id),
    source_sale_item_id INTEGER,
    source_location_id INTEGER NOT NULL,
    receiving_location_id INTEGER NOT NULL,
    product_id INTEGER,
    conflict_quantity INTEGER NOT NULL DEFAULT 0,
    financial_loss NUMERIC(14,2) NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'OPEN',
    detail TEXT NOT NULL,
    resolved_by_user_id INTEGER,
    resolution_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS cross_store_refund_reconciliation_open_idx
ON cross_store_refund_reconciliation(status,created_at DESC);

ALTER TABLE sale_returns ADD COLUMN IF NOT EXISTS cross_store_request_id UUID;
ALTER TABLE sale_returns ADD COLUMN IF NOT EXISTS receiving_location_id INTEGER;
CREATE UNIQUE INDEX IF NOT EXISTS sale_returns_cross_store_request_uidx
ON sale_returns(cross_store_request_id) WHERE cross_store_request_id IS NOT NULL;

INSERT INTO permissions(permission_key,permission_name,description,permission_group,permission_subgroup)
VALUES
 ('VIEW_MULTI_STORE_STOCK','View Multistore Stock','Allows viewing synchronized stock quantities from other stores.','Inventory','Item Visibility'),
 ('VIEW_MULTI_STORE_SALES','View Multistore Sales','Allows viewing synchronized sales and returns from other stores.','Point of Sale','Sales History'),
 ('PROCESS_MULTI_STORE_RETURNS','Process Multistore Returns','Allows paying and queuing returns for sales from another store.','Point of Sale','Returns')
ON CONFLICT(permission_key) DO UPDATE SET permission_name=EXCLUDED.permission_name,
 description=EXCLUDED.description,permission_group=EXCLUDED.permission_group,
 permission_subgroup=EXCLUDED.permission_subgroup;

INSERT INTO role_permissions(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM roles r CROSS JOIN permissions p
WHERE UPPER(r.role_name) IN ('ADMIN','OWNER','CEO')
  AND p.permission_key IN ('VIEW_MULTI_STORE_STOCK','VIEW_MULTI_STORE_SALES','PROCESS_MULTI_STORE_RETURNS')
ON CONFLICT(role_id,permission_id) DO NOTHING;

INSERT INTO role_permissions(role_id,permission_id)
SELECT legacy.role_id,target.permission_id FROM role_permissions legacy
JOIN permissions oldp ON oldp.permission_id=legacy.permission_id AND oldp.permission_key='VIEW_ALL_STORES_INVENTORY'
JOIN permissions target ON target.permission_key='VIEW_MULTI_STORE_STOCK'
ON CONFLICT(role_id,permission_id) DO NOTHING;
