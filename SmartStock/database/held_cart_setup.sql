-- Held Cart setup for SmartStock.
-- Held carts park a sale cart without creating a sale or moving inventory.

CREATE TABLE IF NOT EXISTS held_carts (
    held_cart_id BIGSERIAL PRIMARY KEY,
    location_id INTEGER NOT NULL REFERENCES locations(location_id),
    user_id INTEGER REFERENCES users(user_id),
    user_name TEXT,
    customer_id INTEGER REFERENCES customer_accounts(customer_id),
    hold_name TEXT,
    payment_method TEXT,
    total_amount NUMERIC(12, 2) NOT NULL DEFAULT 0,
    status TEXT NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resumed_at TIMESTAMPTZ,
    resumed_by_user_id INTEGER REFERENCES users(user_id),
    resumed_by_name TEXT
);

CREATE TABLE IF NOT EXISTS held_cart_items (
    held_cart_item_id BIGSERIAL PRIMARY KEY,
    held_cart_id BIGINT NOT NULL REFERENCES held_carts(held_cart_id) ON DELETE CASCADE,
    product_id INTEGER NOT NULL REFERENCES products(product_id),
    product_name TEXT,
    description TEXT,
    sku TEXT,
    unit_price NUMERIC(12, 2) NOT NULL DEFAULT 0,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE held_carts
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE held_cart_items
ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE OR REPLACE FUNCTION set_held_carts_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS held_carts_set_updated_at ON held_carts;
CREATE TRIGGER held_carts_set_updated_at
BEFORE INSERT OR UPDATE ON held_carts
FOR EACH ROW
EXECUTE FUNCTION set_held_carts_updated_at();

CREATE OR REPLACE FUNCTION set_held_cart_items_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS held_cart_items_set_updated_at ON held_cart_items;
CREATE TRIGGER held_cart_items_set_updated_at
BEFORE INSERT OR UPDATE ON held_cart_items
FOR EACH ROW
EXECUTE FUNCTION set_held_cart_items_updated_at();

CREATE INDEX IF NOT EXISTS held_carts_updated_at_idx
ON held_carts(updated_at DESC);

CREATE INDEX IF NOT EXISTS held_cart_items_updated_at_idx
ON held_cart_items(updated_at DESC);

CREATE INDEX IF NOT EXISTS held_carts_location_status_idx
ON held_carts(location_id, status, created_at DESC);

CREATE INDEX IF NOT EXISTS held_cart_items_hold_idx
ON held_cart_items(held_cart_id);
