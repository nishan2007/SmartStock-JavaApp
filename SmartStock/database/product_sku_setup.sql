-- Adds generated SKUs for normal products.
-- Safe to run more than once.

CREATE OR REPLACE FUNCTION product_sku_words(input_name TEXT)
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

CREATE OR REPLACE FUNCTION product_abbreviate_sku_word(input_word TEXT)
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

CREATE OR REPLACE FUNCTION product_sku_right_size(input_value TEXT, input_words TEXT[])
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

CREATE OR REPLACE FUNCTION product_sku_prefix(input_name TEXT)
RETURNS TEXT AS $$
DECLARE
    words TEXT[] := product_sku_words(input_name);
    word_count INTEGER := COALESCE(ARRAY_LENGTH(words, 1), 0);
    initials TEXT;
    first_part TEXT;
    second_part TEXT;
BEGIN
    IF word_count = 0 THEN
        RETURN 'ITEM';
    END IF;
    IF word_count = 1 THEN
        RETURN product_abbreviate_sku_word(words[1]);
    END IF;

    SELECT STRING_AGG(SUBSTRING(word FROM 1 FOR 1), '' ORDER BY ord)
    INTO initials
    FROM UNNEST(words) WITH ORDINALITY AS parts(word, ord);
    IF LENGTH(initials) >= 3 THEN
        RETURN SUBSTRING(initials FROM 1 FOR LEAST(4, LENGTH(initials)));
    END IF;

    first_part := product_abbreviate_sku_word(words[1]);
    second_part := product_abbreviate_sku_word(words[2]);
    RETURN product_sku_right_size(
        SUBSTRING(first_part FROM 1 FOR LEAST(2, LENGTH(first_part)))
        || SUBSTRING(second_part FROM 1 FOR LEAST(2, LENGTH(second_part))),
        words
    );
END;
$$ LANGUAGE plpgsql IMMUTABLE SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION product_next_sku(input_prefix TEXT, current_product_id INTEGER)
RETURNS TEXT AS $$
DECLARE
    existing_sku TEXT;
    matches TEXT[];
    max_number INTEGER := 0;
BEGIN
    FOR existing_sku IN
        SELECT sku
        FROM products
        WHERE UPPER(sku) LIKE UPPER(input_prefix) || '-%'
          AND product_id <> COALESCE(current_product_id, -1)
    LOOP
        matches := REGEXP_MATCH(existing_sku, '^' || input_prefix || '-([0-9]+)$', 'i');
        IF matches IS NOT NULL THEN
            max_number := GREATEST(max_number, matches[1]::INTEGER);
        END IF;
    END LOOP;

    RETURN input_prefix || '-' || LPAD((max_number + 1)::TEXT, 4, '0');
END;
$$ LANGUAGE plpgsql SET search_path = public, pg_temp;

CREATE OR REPLACE FUNCTION set_product_sku()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.sku IS NULL OR BTRIM(NEW.sku) = '' THEN
        NEW.sku := product_next_sku(product_sku_prefix(NEW.name), NEW.product_id);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SET search_path = public, pg_temp;

DROP TRIGGER IF EXISTS products_set_sku ON products;
CREATE TRIGGER products_set_sku
BEFORE INSERT ON products
FOR EACH ROW
EXECUTE FUNCTION set_product_sku();
