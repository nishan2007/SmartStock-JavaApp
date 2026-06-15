-- Adds a separate size field for normal products.
-- Examples: 500g, Small, XL, 12 oz.
-- Safe to run more than once.

ALTER TABLE products
ADD COLUMN IF NOT EXISTS size TEXT;

CREATE INDEX IF NOT EXISTS products_size_search_idx
ON products(size);
