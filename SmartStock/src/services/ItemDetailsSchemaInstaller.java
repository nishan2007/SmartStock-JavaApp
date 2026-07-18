package services;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class ItemDetailsSchemaInstaller {
    private ItemDetailsSchemaInstaller() {
    }

    public static void ensureSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS item_types (
                        item_type_id SERIAL PRIMARY KEY,
                        category_id INTEGER NOT NULL REFERENCES categories(category_id) ON DELETE CASCADE,
                        name TEXT NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS item_types_category_name_unique_idx ON item_types(category_id, LOWER(name))");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS item_brands (
                        brand_id SERIAL PRIMARY KEY,
                        name TEXT NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS item_brands_name_unique_idx ON item_brands(LOWER(name))");
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS shelf_locations (
                        shelf_location_id SERIAL PRIMARY KEY,
                        location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
                        name TEXT NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE (shelf_location_id, location_id)
                    )
                    """);
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS shelf_locations_location_name_unique_idx ON shelf_locations(location_id, LOWER(name))");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS shelf_locations_id_location_unique_idx ON shelf_locations(shelf_location_id, location_id)");
            stmt.executeUpdate("UPDATE item_types SET name = UPPER(REGEXP_REPLACE(BTRIM(name), '\\s+', ' ', 'g')) WHERE name IS DISTINCT FROM UPPER(REGEXP_REPLACE(BTRIM(name), '\\s+', ' ', 'g'))");
            stmt.executeUpdate("UPDATE item_brands SET name = UPPER(REGEXP_REPLACE(BTRIM(name), '\\s+', ' ', 'g')) WHERE name IS DISTINCT FROM UPPER(REGEXP_REPLACE(BTRIM(name), '\\s+', ' ', 'g'))");
            stmt.executeUpdate("UPDATE shelf_locations SET name = UPPER(REGEXP_REPLACE(BTRIM(name), '\\s+', ' ', 'g')) WHERE name IS DISTINCT FROM UPPER(REGEXP_REPLACE(BTRIM(name), '\\s+', ' ', 'g'))");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS item_types_normalized_name_unique_idx ON item_types(category_id, UPPER(REGEXP_REPLACE(BTRIM(name), '\\s+', ' ', 'g')))");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS item_brands_normalized_name_unique_idx ON item_brands(UPPER(REGEXP_REPLACE(BTRIM(name), '\\s+', ' ', 'g')))");
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS shelf_locations_normalized_name_unique_idx ON shelf_locations(location_id, UPPER(REGEXP_REPLACE(BTRIM(name), '\\s+', ' ', 'g')))");
            stmt.executeUpdate("ALTER TABLE products ADD COLUMN IF NOT EXISTS item_type_id INTEGER REFERENCES item_types(item_type_id)");
            stmt.executeUpdate("ALTER TABLE products ADD COLUMN IF NOT EXISTS brand_id INTEGER REFERENCES item_brands(brand_id)");
            stmt.executeUpdate("""
                    INSERT INTO categories (name, description)
                    SELECT 'Custom', 'Default department for custom items'
                    WHERE NOT EXISTS (
                        SELECT 1 FROM categories WHERE UPPER(BTRIM(name)) = 'CUSTOM'
                    )
                    """);
            stmt.executeUpdate("""
                    DO $$
                    BEGIN
                        IF to_regclass('public.custom_order_items') IS NOT NULL THEN
                            ALTER TABLE custom_order_items
                                ADD COLUMN IF NOT EXISTS category_id INTEGER REFERENCES categories(category_id);
                            ALTER TABLE custom_order_items
                                ADD COLUMN IF NOT EXISTS item_type_id INTEGER REFERENCES item_types(item_type_id);
                            ALTER TABLE custom_order_items
                                ADD COLUMN IF NOT EXISTS brand_id INTEGER REFERENCES item_brands(brand_id);
                            CREATE INDEX IF NOT EXISTS custom_order_items_category_idx
                                ON custom_order_items(category_id);
                            CREATE INDEX IF NOT EXISTS custom_order_items_item_type_idx
                                ON custom_order_items(item_type_id);
                            CREATE INDEX IF NOT EXISTS custom_order_items_brand_idx
                                ON custom_order_items(brand_id);
                        END IF;
                    END
                    $$
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS product_shelf_assignments (
                        product_id INTEGER NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
                        location_id INTEGER NOT NULL REFERENCES locations(location_id) ON DELETE CASCADE,
                        shelf_location_id INTEGER NOT NULL,
                        storage_shelf_location_id INTEGER,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (product_id, location_id),
                        FOREIGN KEY (shelf_location_id, location_id) REFERENCES shelf_locations(shelf_location_id, location_id),
                        FOREIGN KEY (storage_shelf_location_id, location_id) REFERENCES shelf_locations(shelf_location_id, location_id)
                    )
                    """);
            stmt.executeUpdate("ALTER TABLE product_shelf_assignments ALTER COLUMN storage_shelf_location_id DROP NOT NULL");
            stmt.executeUpdate("ALTER TABLE item_types ENABLE ROW LEVEL SECURITY");
            stmt.executeUpdate("ALTER TABLE item_brands ENABLE ROW LEVEL SECURITY");
            stmt.executeUpdate("ALTER TABLE shelf_locations ENABLE ROW LEVEL SECURITY");
            stmt.executeUpdate("ALTER TABLE product_shelf_assignments ENABLE ROW LEVEL SECURITY");
        }
    }
}
