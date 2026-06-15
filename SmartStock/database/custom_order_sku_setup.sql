-- Adds generated SKUs for custom order items and variants.
-- Safe to run more than once.

ALTER TABLE custom_order_items
ADD COLUMN IF NOT EXISTS sku TEXT;

ALTER TABLE custom_order_item_variants
ADD COLUMN IF NOT EXISTS sku TEXT;

CREATE OR REPLACE FUNCTION custom_order_words(input_name TEXT)
RETURNS TEXT[] AS $$
DECLARE
    result TEXT[];
BEGIN
    SELECT ARRAY_AGG(word ORDER BY ord)
    INTO result
    FROM REGEXP_SPLIT_TO_TABLE(UPPER(COALESCE(input_name, '')), '[^A-Z0-9]+') WITH ORDINALITY AS parts(word, ord)
    WHERE word <> ''
      AND word NOT IN ('A', 'AN', 'AND', 'FOR', 'IN', 'OF', 'THE', 'TO', 'WITH');

    RETURN COALESCE(result, ARRAY[]::TEXT[]);
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_abbreviate_word(input_word TEXT)
RETURNS TEXT AS $$
DECLARE
    word TEXT := UPPER(COALESCE(input_word, ''));
    result TEXT;
BEGIN
    IF word = '' THEN
        RETURN '';
    END IF;

    result := CASE word
        WHEN 'ADHESIVE' THEN 'ADH'
        WHEN 'BANNER' THEN 'BNR'
        WHEN 'BOTTLE' THEN 'BTL'
        WHEN 'CANVAS' THEN 'CNV'
        WHEN 'GLOSSY' THEN 'GLSY'
        WHEN 'MARKER' THEN 'MRKR'
        WHEN 'MATTE' THEN 'MAT'
        WHEN 'MEDIUM' THEN 'MED'
        WHEN 'PAPER' THEN 'PPR'
        WHEN 'PEN' THEN 'PEN'
        WHEN 'PENCIL' THEN 'PNCL'
        WHEN 'PURPLE' THEN 'PRPL'
        WHEN 'SHIRT' THEN 'SHRT'
        WHEN 'SMALL' THEN 'SML'
        WHEN 'STICKER' THEN 'STKR'
        WHEN 'VINYL' THEN 'VNL'
        ELSE NULL
    END;

    IF result IS NOT NULL THEN
        RETURN result;
    END IF;
    IF LENGTH(word) <= 4 THEN
        RETURN word;
    END IF;

    result := SUBSTRING(word FROM 1 FOR 1)
        || SUBSTRING(REGEXP_REPLACE(SUBSTRING(word FROM 2), '[AEIOU]', '', 'g') FROM 1 FOR 3);
    IF LENGTH(result) < 4 THEN
        result := result || SUBSTRING(REGEXP_REPLACE(SUBSTRING(word FROM 2), '[^AEIOU]', '', 'g') FROM 1 FOR 4 - LENGTH(result));
    END IF;
    RETURN SUBSTRING(result FROM 1 FOR 4);
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_right_size(input_value TEXT, input_words TEXT[])
RETURNS TEXT AS $$
DECLARE
    sized TEXT := COALESCE(input_value, '');
    word TEXT;
    i INTEGER;
    ch TEXT;
BEGIN
    FOREACH word IN ARRAY COALESCE(input_words, ARRAY[]::TEXT[]) LOOP
        i := 1;
        WHILE i <= LENGTH(word) AND LENGTH(sized) < 3 LOOP
            ch := SUBSTRING(word FROM i FOR 1);
            IF POSITION(ch IN sized) = 0 THEN
                sized := sized || ch;
            END IF;
            i := i + 1;
        END LOOP;
    END LOOP;

    i := 1;
    WHILE i <= LENGTH('ITEM') AND LENGTH(sized) < 3 LOOP
        sized := sized || SUBSTRING('ITEM' FROM i FOR 1);
        i := i + 1;
    END LOOP;

    RETURN SUBSTRING(sized FROM 1 FOR LEAST(4, LENGTH(sized)));
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_sku_prefix(input_name TEXT)
RETURNS TEXT AS $$
DECLARE
    words TEXT[] := custom_order_words(input_name);
    word_count INTEGER := COALESCE(ARRAY_LENGTH(words, 1), 0);
    initials TEXT;
    first_part TEXT;
    second_part TEXT;
BEGIN
    IF word_count = 0 THEN
        RETURN 'ITEM';
    END IF;
    IF word_count = 1 THEN
        RETURN custom_order_abbreviate_word(words[1]);
    END IF;

    SELECT STRING_AGG(SUBSTRING(word FROM 1 FOR 1), '' ORDER BY ord)
    INTO initials
    FROM UNNEST(words) WITH ORDINALITY AS parts(word, ord);
    IF LENGTH(initials) >= 3 THEN
        RETURN SUBSTRING(initials FROM 1 FOR LEAST(4, LENGTH(initials)));
    END IF;

    first_part := custom_order_abbreviate_word(words[1]);
    second_part := custom_order_abbreviate_word(words[2]);
    RETURN custom_order_right_size(
        SUBSTRING(first_part FROM 1 FOR LEAST(2, LENGTH(first_part)))
        || SUBSTRING(second_part FROM 1 FOR LEAST(2, LENGTH(second_part))),
        words
    );
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_variant_sku_prefix(input_item_name TEXT, input_variant_name TEXT)
RETURNS TEXT AS $$
DECLARE
    item_words TEXT[] := custom_order_words(input_item_name);
    variant_words TEXT[] := custom_order_words(input_variant_name);
    item_count INTEGER := COALESCE(ARRAY_LENGTH(item_words, 1), 0);
    variant_count INTEGER := COALESCE(ARRAY_LENGTH(variant_words, 1), 0);
    first_part TEXT;
    second_part TEXT;
BEGIN
    IF variant_count = 0 THEN
        RETURN custom_order_sku_prefix(input_item_name);
    END IF;
    IF item_count = 0 THEN
        RETURN custom_order_sku_prefix(input_variant_name);
    END IF;

    first_part := custom_order_abbreviate_word(item_words[1]);
    second_part := custom_order_abbreviate_word(variant_words[1]);
    RETURN custom_order_right_size(
        SUBSTRING(first_part FROM 1 FOR LEAST(2, LENGTH(first_part)))
        || SUBSTRING(second_part FROM 1 FOR LEAST(2, LENGTH(second_part))),
        item_words || variant_words
    );
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_item_sku(input_name TEXT)
RETURNS TEXT AS $$
BEGIN
    RETURN custom_order_sku_prefix(input_name) || '-0001';
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_variant_sku(input_item_name TEXT, input_variant_name TEXT)
RETURNS TEXT AS $$
BEGIN
    RETURN custom_order_variant_sku_prefix(input_item_name, input_variant_name) || '-0001';
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_next_item_sku(input_prefix TEXT, current_item_id BIGINT)
RETURNS TEXT AS $$
DECLARE
    existing_sku TEXT;
    matches TEXT[];
    max_number INTEGER := 0;
BEGIN
    FOR existing_sku IN
        SELECT sku
        FROM custom_order_items
        WHERE UPPER(sku) LIKE UPPER(input_prefix) || '-%'
          AND custom_item_id <> COALESCE(current_item_id, -1)
    LOOP
        matches := REGEXP_MATCH(existing_sku, '^' || input_prefix || '-([0-9]+)$', 'i');
        IF matches IS NOT NULL THEN
            max_number := GREATEST(max_number, matches[1]::INTEGER);
        END IF;
    END LOOP;

    RETURN input_prefix || '-' || LPAD((max_number + 1)::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION custom_order_next_variant_sku(input_prefix TEXT, current_variant_id BIGINT)
RETURNS TEXT AS $$
DECLARE
    existing_sku TEXT;
    matches TEXT[];
    max_number INTEGER := 0;
BEGIN
    FOR existing_sku IN
        SELECT sku
        FROM custom_order_item_variants
        WHERE UPPER(sku) LIKE UPPER(input_prefix) || '-%'
          AND custom_variant_id <> COALESCE(current_variant_id, -1)
    LOOP
        matches := REGEXP_MATCH(existing_sku, '^' || input_prefix || '-([0-9]+)$', 'i');
        IF matches IS NOT NULL THEN
            max_number := GREATEST(max_number, matches[1]::INTEGER);
        END IF;
    END LOOP;

    RETURN input_prefix || '-' || LPAD((max_number + 1)::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION set_custom_order_item_sku()
RETURNS TRIGGER AS $$
BEGIN
    NEW.sku := custom_order_next_item_sku(custom_order_sku_prefix(NEW.item_name), NEW.custom_item_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION set_custom_order_variant_sku()
RETURNS TRIGGER AS $$
DECLARE
    parent_name TEXT;
BEGIN
    SELECT item_name
    INTO parent_name
    FROM custom_order_items
    WHERE custom_item_id = NEW.custom_item_id;

    NEW.sku := custom_order_next_variant_sku(custom_order_variant_sku_prefix(parent_name, NEW.variant_name), NEW.custom_variant_id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION refresh_custom_order_variant_skus_for_item()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.item_name IS DISTINCT FROM OLD.item_name THEN
        UPDATE custom_order_item_variants
        SET variant_name = variant_name,
            updated_at = CURRENT_TIMESTAMP
        WHERE custom_item_id = NEW.custom_item_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS custom_order_items_set_sku ON custom_order_items;
CREATE TRIGGER custom_order_items_set_sku
BEFORE INSERT OR UPDATE OF item_name ON custom_order_items
FOR EACH ROW
EXECUTE FUNCTION set_custom_order_item_sku();

DROP TRIGGER IF EXISTS custom_order_item_variants_set_sku ON custom_order_item_variants;
CREATE TRIGGER custom_order_item_variants_set_sku
BEFORE INSERT OR UPDATE OF variant_name, custom_item_id ON custom_order_item_variants
FOR EACH ROW
EXECUTE FUNCTION set_custom_order_variant_sku();

DROP TRIGGER IF EXISTS custom_order_items_refresh_variant_skus ON custom_order_items;
CREATE TRIGGER custom_order_items_refresh_variant_skus
AFTER INSERT OR UPDATE OF item_name ON custom_order_items
FOR EACH ROW
EXECUTE FUNCTION refresh_custom_order_variant_skus_for_item();

UPDATE custom_order_items
SET item_name = item_name;

UPDATE custom_order_item_variants
SET variant_name = variant_name;

CREATE UNIQUE INDEX IF NOT EXISTS custom_order_items_sku_uidx
ON custom_order_items(UPPER(sku))
WHERE sku IS NOT NULL AND sku <> '';

CREATE UNIQUE INDEX IF NOT EXISTS custom_order_item_variants_sku_uidx
ON custom_order_item_variants(UPPER(sku))
WHERE sku IS NOT NULL AND sku <> '';

CREATE INDEX IF NOT EXISTS custom_order_items_sku_search_idx
ON custom_order_items(sku);

CREATE INDEX IF NOT EXISTS custom_order_item_variants_sku_search_idx
ON custom_order_item_variants(sku);
