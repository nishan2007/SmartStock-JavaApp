--
-- PostgreSQL database dump
--

-- SmartStock local PostgreSQL v1 canonical baseline.
-- This script is intentionally fresh-install only. Existing databases must be
-- migrated through a side-by-side candidate and must never be upgraded in place.


-- Dumped from database version 18.3 (Homebrew)
-- Dumped by pg_dump version 18.3 (Homebrew)

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: public; Type: SCHEMA; Schema: -; Owner: -
--

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_catalog.pg_class c
        JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
    ) THEN
        RAISE EXCEPTION 'SmartStock v1 baseline requires an empty candidate database';
    END IF;
END
$$;

DROP SCHEMA public CASCADE;
CREATE SCHEMA public;


--
-- Name: SCHEMA public; Type: COMMENT; Schema: -; Owner: -
--

COMMENT ON SCHEMA public IS 'standard public schema';


--
-- Name: compensation_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.compensation_type_enum AS ENUM (
    'HOURLY',
    'SALARY',
    'DAILY'
);


--
-- Name: pay_period_type_enum; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.pay_period_type_enum AS ENUM (
    'SEMI_MONTHLY',
    'WEEKLY'
);


--
-- Name: assign_customer_account_number(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.assign_customer_account_number() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public', 'pg_temp'
    AS $$
BEGIN
    IF NULLIF(TRIM(NEW.account_number), '') IS NULL THEN
        IF COALESCE(NEW.is_business, FALSE) THEN
            NEW.account_number := 'BA-' || LPAD(NEXTVAL('customer_account_ba_number_seq')::TEXT, 6, '0');
        ELSE
            NEW.account_number := 'CA-' || LPAD(NEXTVAL('customer_account_ca_number_seq')::TEXT, 6, '0');
        END IF;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: auto_close_resolved_maintenance_tickets(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.auto_close_resolved_maintenance_tickets() RETURNS integer
    LANGUAGE plpgsql
    AS $$
DECLARE
    closed_count INTEGER;
BEGIN
    UPDATE maintenance_tickets
    SET status = 'CLOSED',
        closed_at = CURRENT_TIMESTAMP,
        updated_at = CURRENT_TIMESTAMP
    WHERE status = 'RESOLVED'
      AND resolved_at IS NOT NULL
      AND resolved_at <= CURRENT_TIMESTAMP - INTERVAL '24 hours'
      AND closed_at IS NULL;

    GET DIAGNOSTICS closed_count = ROW_COUNT;
    RETURN closed_count;
END;
$$;


--
-- Name: custom_order_abbreviate_word(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.custom_order_abbreviate_word(input_word text) RETURNS text
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public', 'pg_temp'
    AS $$
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
$$;


--
-- Name: custom_order_item_sku(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.custom_order_item_sku(input_name text) RETURNS text
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public', 'pg_temp'
    AS $$
BEGIN
    RETURN custom_order_sku_prefix(input_name) || '-0001';
END;
$$;


--
-- Name: custom_order_next_item_sku(text, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.custom_order_next_item_sku(input_prefix text, current_item_id bigint) RETURNS text
    LANGUAGE plpgsql
    SET search_path TO 'public', 'pg_temp'
    AS $_$
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
$_$;


--
-- Name: custom_order_next_variant_sku(text, bigint); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.custom_order_next_variant_sku(input_prefix text, current_variant_id bigint) RETURNS text
    LANGUAGE plpgsql
    SET search_path TO 'public', 'pg_temp'
    AS $_$
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
$_$;


--
-- Name: custom_order_right_size(text, text[]); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.custom_order_right_size(input_value text, input_words text[]) RETURNS text
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public', 'pg_temp'
    AS $$
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
$$;


--
-- Name: custom_order_sku_prefix(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.custom_order_sku_prefix(input_name text) RETURNS text
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public', 'pg_temp'
    AS $$
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
$$;


--
-- Name: custom_order_variant_sku(text, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.custom_order_variant_sku(input_item_name text, input_variant_name text) RETURNS text
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public', 'pg_temp'
    AS $$
BEGIN
    RETURN custom_order_variant_sku_prefix(input_item_name, input_variant_name) || '-0001';
END;
$$;


--
-- Name: custom_order_variant_sku_prefix(text, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.custom_order_variant_sku_prefix(input_item_name text, input_variant_name text) RETURNS text
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public', 'pg_temp'
    AS $$
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
$$;


--
-- Name: custom_order_words(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.custom_order_words(input_name text) RETURNS text[]
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public', 'pg_temp'
    AS $$
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
$$;


--
-- Name: prevent_balance_sheet_revision_changes(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_balance_sheet_revision_changes() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog', 'public'
    AS $$
BEGIN
    RAISE EXCEPTION 'Balance sheet revision history is immutable';
END;
$$;


--
-- Name: prevent_employee_time_clock_adjustment_changes(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.prevent_employee_time_clock_adjustment_changes() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE EXCEPTION 'Time-clock adjustment history is append-only';
END;
$$;


--
-- Name: product_abbreviate_sku_word(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.product_abbreviate_sku_word(input_word text) RETURNS text
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public', 'pg_temp'
    AS $$
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
$$;


--
-- Name: product_next_sku(text, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.product_next_sku(input_prefix text, current_product_id integer) RETURNS text
    LANGUAGE plpgsql
    SET search_path TO 'public', 'pg_temp'
    AS $_$
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
$_$;


--
-- Name: product_sku_prefix(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.product_sku_prefix(input_name text) RETURNS text
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public', 'pg_temp'
    AS $$
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
$$;


--
-- Name: product_sku_right_size(text, text[]); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.product_sku_right_size(input_value text, input_words text[]) RETURNS text
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public', 'pg_temp'
    AS $$
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
$$;


--
-- Name: product_sku_words(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.product_sku_words(input_name text) RETURNS text[]
    LANGUAGE plpgsql IMMUTABLE
    SET search_path TO 'public', 'pg_temp'
    AS $$
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
$$;


--
-- Name: record_sale_table_audit(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.record_sale_table_audit() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    row_sale_id INTEGER;
    row_sale_item_id INTEGER;
    row_return_id BIGINT;
    row_return_item_id BIGINT;
    row_customer_id INTEGER;
    row_product_id INTEGER;
    row_location_id INTEGER;
    action_scope_value TEXT;
    old_value TEXT;
    new_value TEXT;
BEGIN
    IF TG_OP = 'UPDATE' AND NEW IS NOT DISTINCT FROM OLD THEN
        RETURN NEW;
    END IF;

    IF TG_OP = 'UPDATE' AND TG_TABLE_NAME IN ('sales', 'sale_items') THEN
        RETURN NEW;
    END IF;

    row_sale_item_id := NULL;
    row_return_id := NULL;
    row_return_item_id := NULL;
    row_customer_id := NULL;
    row_product_id := NULL;
    row_location_id := NULL;
    action_scope_value := TG_TABLE_NAME;

    IF TG_TABLE_NAME = 'sales' THEN
        row_sale_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.sale_id ELSE NEW.sale_id END;
        row_customer_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.customer_id ELSE NEW.customer_id END;
        row_location_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.location_id ELSE NEW.location_id END;
        old_value := CASE WHEN TG_OP = 'DELETE' THEN ROW_TO_JSON(OLD)::TEXT ELSE NULL END;
        new_value := CASE WHEN TG_OP <> 'DELETE' THEN ROW_TO_JSON(NEW)::TEXT ELSE NULL END;
    ELSIF TG_TABLE_NAME = 'sale_items' THEN
        row_sale_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.sale_id ELSE NEW.sale_id END;
        row_sale_item_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.sale_item_id ELSE NEW.sale_item_id END;
        row_product_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.product_id ELSE NEW.product_id END;
        old_value := CASE WHEN TG_OP = 'DELETE' THEN ROW_TO_JSON(OLD)::TEXT ELSE NULL END;
        new_value := CASE WHEN TG_OP <> 'DELETE' THEN ROW_TO_JSON(NEW)::TEXT ELSE NULL END;
    ELSIF TG_TABLE_NAME = 'sale_returns' THEN
        row_sale_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.sale_id ELSE NEW.sale_id END;
        row_return_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.return_id ELSE NEW.return_id END;
        row_location_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.location_id ELSE NEW.location_id END;
        old_value := CASE WHEN TG_OP = 'DELETE' THEN ROW_TO_JSON(OLD)::TEXT ELSE NULL END;
        new_value := CASE WHEN TG_OP <> 'DELETE' THEN ROW_TO_JSON(NEW)::TEXT ELSE NULL END;
    ELSIF TG_TABLE_NAME = 'sale_return_items' THEN
        row_return_item_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.return_item_id ELSE NEW.return_item_id END;
        row_return_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.return_id ELSE NEW.return_id END;
        row_sale_item_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.sale_item_id ELSE NEW.sale_item_id END;
        row_product_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.product_id ELSE NEW.product_id END;
        SELECT sr.sale_id, sr.location_id
        INTO row_sale_id, row_location_id
        FROM sale_returns sr
        WHERE sr.return_id = row_return_id;
        old_value := CASE WHEN TG_OP = 'DELETE' THEN ROW_TO_JSON(OLD)::TEXT ELSE NULL END;
        new_value := CASE WHEN TG_OP <> 'DELETE' THEN ROW_TO_JSON(NEW)::TEXT ELSE NULL END;
    END IF;

    INSERT INTO sale_audit_log (
        sale_id, sale_item_id, return_id, return_item_id,
        customer_id, product_id, location_id,
        action_type, action_scope, old_value, new_value, note
    )
    VALUES (
        row_sale_id, row_sale_item_id, row_return_id, row_return_item_id,
        row_customer_id, row_product_id, row_location_id,
        'DB_' || TG_OP, action_scope_value, old_value, new_value,
        'Automatic database safety audit for ' || TG_TABLE_NAME
    );

    RETURN COALESCE(NEW, OLD);
END;
$$;


--
-- Name: refresh_custom_order_item_variant_totals(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.refresh_custom_order_item_variant_totals() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
DECLARE
    affected_item_id BIGINT;
BEGIN
    affected_item_id := COALESCE(NEW.custom_item_id, OLD.custom_item_id);

    UPDATE custom_order_items coi
    SET quantity_on_hand = COALESCE((
            SELECT SUM(coiv.quantity_on_hand)
            FROM custom_order_item_variants coiv
            WHERE coiv.custom_item_id = affected_item_id
              AND coiv.is_active = TRUE
        ), 0),
        reorder_level = COALESCE((
            SELECT SUM(coiv.reorder_level)
            FROM custom_order_item_variants coiv
            WHERE coiv.custom_item_id = affected_item_id
              AND coiv.is_active = TRUE
        ), 0),
        updated_at = CURRENT_TIMESTAMP
    WHERE coi.custom_item_id = affected_item_id
      AND coi.has_variants = TRUE;

    RETURN COALESCE(NEW, OLD);
END;
$$;


--
-- Name: refresh_custom_order_variant_skus_for_item(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.refresh_custom_order_variant_skus_for_item() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public', 'pg_temp'
    AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.item_name IS DISTINCT FROM OLD.item_name THEN
        UPDATE custom_order_item_variants
        SET variant_name = variant_name,
            updated_at = CURRENT_TIMESTAMP
        WHERE custom_item_id = NEW.custom_item_id;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: refresh_device_session_count(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.refresh_device_session_count() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
            BEGIN
                IF TG_OP IN ('INSERT', 'UPDATE') THEN
                    UPDATE public.devices
                    SET session_count = (
                        SELECT COUNT(*)::BIGINT
                        FROM public.device_sessions
                        WHERE device_id = NEW.device_id
                    )
                    WHERE device_id = NEW.device_id;
                END IF;

                IF TG_OP IN ('DELETE', 'UPDATE')
                   AND (TG_OP = 'DELETE' OR OLD.device_id IS DISTINCT FROM NEW.device_id) THEN
                    UPDATE public.devices
                    SET session_count = (
                        SELECT COUNT(*)::BIGINT
                        FROM public.device_sessions
                        WHERE device_id = OLD.device_id
                    )
                    WHERE device_id = OLD.device_id;
                END IF;

                RETURN COALESCE(NEW, OLD);
            END;
            $$;


--
-- Name: refresh_machine_last_service_date(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.refresh_machine_last_service_date() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE maintenance_machines
    SET last_service_date = (
            SELECT MAX(service_date)
            FROM maintenance_logs
            WHERE machine_id = NEW.machine_id
        ),
        updated_at = CURRENT_TIMESTAMP
    WHERE machine_id = NEW.machine_id;

    RETURN NEW;
END;
$$;


--
-- Name: reject_lan_api_audit_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reject_lan_api_audit_mutation() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO ''
    AS $$
BEGIN
    RAISE EXCEPTION 'SmartStock LAN API audit events are immutable';
END;
$$;


--
-- Name: reject_security_audit_mutation(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.reject_security_audit_mutation() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO ''
    AS $$
BEGIN
    RAISE EXCEPTION 'SmartStock security audit events are immutable';
END;
$$;


--
-- Name: set_cash_drawer_device_assignments_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_cash_drawer_device_assignments_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_cash_drawers_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_cash_drawers_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_custom_order_item_sku(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_custom_order_item_sku() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public', 'pg_temp'
    AS $$
BEGIN
    NEW.sku := custom_order_next_item_sku(custom_order_sku_prefix(NEW.item_name), NEW.custom_item_id);
    RETURN NEW;
END;
$$;


--
-- Name: set_custom_order_print_materials_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_custom_order_print_materials_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_custom_order_print_size_presets_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_custom_order_print_size_presets_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_custom_order_variant_sku(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_custom_order_variant_sku() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public', 'pg_temp'
    AS $$
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
$$;


--
-- Name: set_customer_account_payment_allocations_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_customer_account_payment_allocations_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_customer_account_transactions_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_customer_account_transactions_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_customer_accounts_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_customer_accounts_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_devices_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_devices_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_email_outbox_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_email_outbox_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public'
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_employee_payroll_settings_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_employee_payroll_settings_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_employee_schedule_assignments_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_employee_schedule_assignments_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO ''
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_employee_schedule_holidays_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_employee_schedule_holidays_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO ''
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_employee_schedule_shifts_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_employee_schedule_shifts_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO ''
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_employee_time_clock_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_employee_time_clock_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_held_cart_items_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_held_cart_items_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_held_carts_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_held_carts_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_image_asset_references_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_image_asset_references_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog'
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: set_image_assets_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_image_assets_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'pg_catalog'
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END $$;


--
-- Name: set_inventory_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_inventory_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_item_brands_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_item_brands_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_item_types_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_item_types_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_locations_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_locations_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_product_barcodes_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_product_barcodes_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_product_shelf_assignments_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_product_shelf_assignments_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_product_sku(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_product_sku() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path TO 'public', 'pg_temp'
    AS $$
BEGIN
    IF NEW.sku IS NULL OR BTRIM(NEW.sku) = '' THEN
        NEW.sku := product_next_sku(product_sku_prefix(NEW.name), NEW.product_id);
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_products_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_products_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_quotation_invoice_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_quotation_invoice_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_role_mobile_permissions_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_role_mobile_permissions_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_role_permissions_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_role_permissions_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_roles_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_roles_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_shelf_locations_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_shelf_locations_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_time_clock_auto_close_settings_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_time_clock_auto_close_settings_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_user_locations_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_user_locations_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: set_users_updated_at(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.set_users_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.updated_at = COALESCE(NEW.updated_at, CURRENT_TIMESTAMP);
    ELSIF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at = CURRENT_TIMESTAMP;
    END IF;
    RETURN NEW;
END;
$$;


--
-- Name: suppress_duplicate_sale_db_update_audit(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.suppress_duplicate_sale_db_update_audit() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF NEW.action_type = 'DB_UPDATE'
       AND NEW.action_scope IN ('sales', 'sale_items')
       AND EXISTS (
           SELECT 1
           FROM sale_audit_log existing
           WHERE existing.action_type = NEW.action_type
             AND existing.action_scope = NEW.action_scope
             AND existing.sale_id IS NOT DISTINCT FROM NEW.sale_id
             AND existing.sale_item_id IS NOT DISTINCT FROM NEW.sale_item_id
           LIMIT 1
       ) THEN
        RETURN NULL;
    END IF;

    RETURN NEW;
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_releases; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_releases (
    release_id bigint NOT NULL,
    version text NOT NULL,
    build_number integer NOT NULL,
    platform text DEFAULT 'windows'::text NOT NULL,
    artifact_bucket text DEFAULT 'smartstock-releases'::text NOT NULL,
    artifact_path text NOT NULL,
    sha256 text NOT NULL,
    file_size_bytes bigint,
    release_notes text,
    required boolean DEFAULT false NOT NULL,
    minimum_supported_version text,
    published boolean DEFAULT false NOT NULL,
    published_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by_user_id integer,
    CONSTRAINT app_releases_platform_check CHECK ((platform = ANY (ARRAY['windows'::text, 'mac'::text, 'linux'::text, 'all'::text]))),
    CONSTRAINT app_releases_published_at_check CHECK (((published = false) OR (published_at IS NOT NULL))),
    CONSTRAINT app_releases_sha256_check CHECK ((sha256 ~* '^[a-f0-9]{64}$'::text))
);


--
-- Name: app_releases_release_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.app_releases_release_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: app_releases_release_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.app_releases_release_id_seq OWNED BY public.app_releases.release_id;


--
-- Name: balance_sheet_bf_overrides; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.balance_sheet_bf_overrides (
    balance_sheet_bf_override_id bigint CONSTRAINT balance_sheet_bf_overrides_balance_sheet_bf_override_i_not_null NOT NULL,
    location_id integer NOT NULL,
    period_start date NOT NULL,
    amount numeric(12,2) NOT NULL,
    updated_by_user_id integer,
    updated_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: balance_sheet_bf_overrides_balance_sheet_bf_override_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.balance_sheet_bf_overrides_balance_sheet_bf_override_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: balance_sheet_bf_overrides_balance_sheet_bf_override_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.balance_sheet_bf_overrides_balance_sheet_bf_override_id_seq OWNED BY public.balance_sheet_bf_overrides.balance_sheet_bf_override_id;


--
-- Name: balance_sheet_submission_revisions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.balance_sheet_submission_revisions (
    balance_sheet_revision_id bigint CONSTRAINT balance_sheet_submission_rev_balance_sheet_revision_id_not_null NOT NULL,
    balance_sheet_submission_id bigint CONSTRAINT balance_sheet_submission_re_balance_sheet_submission_i_not_null NOT NULL,
    location_id integer NOT NULL,
    revision_no integer NOT NULL,
    action_type text DEFAULT 'EDIT'::text NOT NULL,
    reason text NOT NULL,
    change_summary text NOT NULL,
    before_snapshot jsonb NOT NULL,
    after_snapshot jsonb NOT NULL,
    changed_by_user_id integer,
    changed_by_name text,
    device_id text,
    device_name text,
    changed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT balance_sheet_revision_reason_chk CHECK ((length(TRIM(BOTH FROM reason)) > 0))
);


--
-- Name: balance_sheet_submission_revision_balance_sheet_revision_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.balance_sheet_submission_revision_balance_sheet_revision_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: balance_sheet_submission_revision_balance_sheet_revision_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.balance_sheet_submission_revision_balance_sheet_revision_id_seq OWNED BY public.balance_sheet_submission_revisions.balance_sheet_revision_id;


--
-- Name: balance_sheet_submissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.balance_sheet_submissions (
    balance_sheet_submission_id bigint NOT NULL,
    location_id integer,
    location_name text,
    period_start date NOT NULL,
    period_end date NOT NULL,
    store_timezone text,
    balance_bf numeric(12,2) DEFAULT 0 NOT NULL,
    cash_in_hand numeric(12,2) DEFAULT 0 NOT NULL,
    total_income numeric(12,2) DEFAULT 0 NOT NULL,
    total_receivables numeric(12,2) DEFAULT 0 NOT NULL,
    total_expenses numeric(12,2) DEFAULT 0 NOT NULL,
    total_payables numeric(12,2) DEFAULT 0 NOT NULL,
    balance_cf numeric(12,2) DEFAULT 0 NOT NULL,
    income_lines text,
    receivable_lines text,
    expense_lines text,
    payable_lines text,
    drawer_cash_lines text,
    device_sales_lines text,
    device_order_lines text,
    device_payment_lines text,
    account_payment_lines text,
    bank_transaction_lines text,
    pending_cheque_lines text,
    drawer_check_lines text,
    submitted_by_user_id integer,
    submitted_by_name text,
    submitted_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    notes text,
    revision_no integer DEFAULT 0 NOT NULL,
    last_edited_at timestamp with time zone,
    last_edited_by_user_id integer,
    last_edited_by_name text
);


--
-- Name: balance_sheet_submissions_balance_sheet_submission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.balance_sheet_submissions_balance_sheet_submission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: balance_sheet_submissions_balance_sheet_submission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.balance_sheet_submissions_balance_sheet_submission_id_seq OWNED BY public.balance_sheet_submissions.balance_sheet_submission_id;


--
-- Name: bank_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.bank_transactions (
    bank_transaction_id bigint NOT NULL,
    location_id integer,
    transaction_date date DEFAULT CURRENT_DATE NOT NULL,
    transaction_name text NOT NULL,
    transaction_direction text NOT NULL,
    amount numeric(12,2) NOT NULL,
    payment_reference text,
    source_type text,
    source_id text,
    created_by_user_id integer,
    created_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT bank_transactions_amount_chk CHECK ((amount >= (0)::numeric)),
    CONSTRAINT bank_transactions_direction_chk CHECK ((transaction_direction = ANY (ARRAY['PAID'::text, 'RECEIVED'::text])))
);


--
-- Name: bank_transactions_bank_transaction_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.bank_transactions_bank_transaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bank_transactions_bank_transaction_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bank_transactions_bank_transaction_id_seq OWNED BY public.bank_transactions.bank_transaction_id;


--
-- Name: cash_drawer_device_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cash_drawer_device_assignments (
    assignment_id bigint NOT NULL,
    cash_drawer_id bigint NOT NULL,
    location_id integer NOT NULL,
    device_id uuid NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    assigned_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    assigned_by_user_id integer,
    unassigned_at timestamp with time zone,
    unassigned_by_user_id integer,
    notes text,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: cash_drawer_device_assignments_assignment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cash_drawer_device_assignments_assignment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cash_drawer_device_assignments_assignment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cash_drawer_device_assignments_assignment_id_seq OWNED BY public.cash_drawer_device_assignments.assignment_id;


--
-- Name: cash_drawer_handovers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cash_drawer_handovers (
    cash_drawer_handover_id bigint NOT NULL,
    cash_drawer_session_id bigint NOT NULL,
    cash_drawer_id bigint NOT NULL,
    location_id integer NOT NULL,
    device_id uuid NOT NULL,
    from_user_id integer,
    from_user_name text,
    to_user_id integer,
    to_user_name text,
    expected_cash numeric(12,2) DEFAULT 0 NOT NULL,
    counted_cash numeric(12,2) DEFAULT 0 NOT NULL,
    variance numeric(12,2) DEFAULT 0 NOT NULL,
    handed_over_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    notes text
);


--
-- Name: cash_drawer_handovers_cash_drawer_handover_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cash_drawer_handovers_cash_drawer_handover_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cash_drawer_handovers_cash_drawer_handover_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cash_drawer_handovers_cash_drawer_handover_id_seq OWNED BY public.cash_drawer_handovers.cash_drawer_handover_id;


--
-- Name: cash_drawer_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cash_drawer_sessions (
    cash_drawer_session_id bigint NOT NULL,
    cash_drawer_id bigint NOT NULL,
    location_id integer NOT NULL,
    device_id uuid NOT NULL,
    drawer_name text NOT NULL,
    device_name text,
    opening_cash numeric(12,2) DEFAULT 0 NOT NULL,
    expected_cash numeric(12,2),
    counted_cash numeric(12,2),
    cash_to_remove numeric(12,2),
    variance numeric(12,2),
    status text DEFAULT 'OPEN'::text NOT NULL,
    opened_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    opened_by_user_id integer,
    opened_by_name text,
    main_cashier_user_id integer,
    main_cashier_name text,
    current_cashier_user_id integer,
    current_cashier_name text,
    closed_at timestamp with time zone,
    closed_by_user_id integer,
    closed_by_name text,
    balanced_by_user_id integer,
    balanced_by_name text,
    opening_notes text,
    closing_notes text,
    closing_report text,
    CONSTRAINT cash_drawer_sessions_status_chk CHECK ((status = ANY (ARRAY['OPEN'::text, 'CLOSED'::text])))
);


--
-- Name: cash_drawer_sessions_cash_drawer_session_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cash_drawer_sessions_cash_drawer_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cash_drawer_sessions_cash_drawer_session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cash_drawer_sessions_cash_drawer_session_id_seq OWNED BY public.cash_drawer_sessions.cash_drawer_session_id;


--
-- Name: cash_drawers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cash_drawers (
    cash_drawer_id bigint NOT NULL,
    location_id integer NOT NULL,
    drawer_name text NOT NULL,
    description text,
    starting_cash_amount numeric(12,2) DEFAULT 20000.00 NOT NULL,
    float_mix jsonb DEFAULT '{"20": 100, "100": 50, "500": 10, "1000": 8}'::jsonb NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by_user_id integer,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by_user_id integer
);


--
-- Name: cash_drawers_cash_drawer_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cash_drawers_cash_drawer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cash_drawers_cash_drawer_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cash_drawers_cash_drawer_id_seq OWNED BY public.cash_drawers.cash_drawer_id;


--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.categories (
    category_id integer NOT NULL,
    name text NOT NULL,
    description text,
    vat_rate_percent numeric(6,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT categories_vat_rate_percent_chk CHECK (((vat_rate_percent >= (0)::numeric) AND (vat_rate_percent <= (100)::numeric)))
);


--
-- Name: categories_category_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.categories_category_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categories_category_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.categories_category_id_seq OWNED BY public.categories.category_id;


--
-- Name: change_basket_updates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.change_basket_updates (
    change_basket_update_id bigint NOT NULL,
    location_id integer NOT NULL,
    store_name text,
    target_amount numeric(12,2) DEFAULT 0 NOT NULL,
    counted_amount numeric(12,2) DEFAULT 0 NOT NULL,
    variance numeric(12,2) DEFAULT 0 NOT NULL,
    denomination_counts jsonb DEFAULT '{}'::jsonb NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by_user_id integer,
    updated_by_name text,
    device_id uuid,
    device_name text,
    notes text
);


--
-- Name: change_basket_updates_change_basket_update_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.change_basket_updates_change_basket_update_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: change_basket_updates_change_basket_update_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.change_basket_updates_change_basket_update_id_seq OWNED BY public.change_basket_updates.change_basket_update_id;


--
-- Name: cheque_bank_deposits; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cheque_bank_deposits (
    cheque_bank_deposit_id bigint NOT NULL,
    location_id integer,
    source_type text NOT NULL,
    source_id text NOT NULL,
    amount numeric(12,2) DEFAULT 0 NOT NULL,
    payment_reference text,
    deposited_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deposited_by_user_id integer,
    deposited_by_name text,
    notes text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT cheque_bank_deposits_amount_chk CHECK ((amount >= (0)::numeric))
);


--
-- Name: cheque_bank_deposits_cheque_bank_deposit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cheque_bank_deposits_cheque_bank_deposit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cheque_bank_deposits_cheque_bank_deposit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cheque_bank_deposits_cheque_bank_deposit_id_seq OWNED BY public.cheque_bank_deposits.cheque_bank_deposit_id;


--
-- Name: company_customization; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.company_customization (
    customization_id integer NOT NULL,
    location_id integer NOT NULL,
    receipt_header_line text DEFAULT ''::text NOT NULL,
    receipt_footer_line text DEFAULT 'Thank you'::text NOT NULL,
    show_logo boolean DEFAULT false NOT NULL,
    show_sale_id boolean DEFAULT true NOT NULL,
    show_device boolean DEFAULT true NOT NULL,
    show_customer boolean DEFAULT true NOT NULL,
    show_sku boolean DEFAULT true NOT NULL,
    show_item_discount boolean DEFAULT true NOT NULL,
    show_payment_status boolean DEFAULT true NOT NULL,
    vat_enabled boolean DEFAULT false NOT NULL,
    vat_use_department_rates boolean DEFAULT false NOT NULL,
    vat_fixed_rate_percent numeric(6,2) DEFAULT 0 NOT NULL,
    next_receipt_counter integer DEFAULT 1 NOT NULL,
    change_basket_target_amount numeric(12,2) DEFAULT 60000 NOT NULL,
    always_print_sale_receipt boolean DEFAULT false NOT NULL,
    account_payment_receipt_title text DEFAULT 'CUSTOMER ACCOUNT PAYMENT'::text NOT NULL,
    account_payment_receipt_show_user boolean DEFAULT true CONSTRAINT company_customization_account_payment_receipt_show_use_not_null NOT NULL,
    account_payment_receipt_show_customer boolean DEFAULT true CONSTRAINT company_customization_account_payment_receipt_show_cus_not_null NOT NULL,
    account_payment_receipt_show_account_number boolean DEFAULT true CONSTRAINT company_customization_account_payment_receipt_show_acc_not_null NOT NULL,
    account_payment_receipt_show_method boolean DEFAULT true CONSTRAINT company_customization_account_payment_receipt_show_met_not_null NOT NULL,
    account_payment_receipt_show_reference boolean DEFAULT true CONSTRAINT company_customization_account_payment_receipt_show_ref_not_null NOT NULL,
    account_payment_receipt_show_device boolean DEFAULT true CONSTRAINT company_customization_account_payment_receipt_show_dev_not_null NOT NULL,
    account_payment_receipt_show_drawer boolean DEFAULT true CONSTRAINT company_customization_account_payment_receipt_show_dra_not_null NOT NULL,
    account_payment_receipt_show_allocations boolean DEFAULT true CONSTRAINT company_customization_account_payment_receipt_show_all_not_null NOT NULL,
    account_payment_receipt_show_balance boolean DEFAULT true CONSTRAINT company_customization_account_payment_receipt_show_bal_not_null NOT NULL,
    account_payment_receipt_show_barcode boolean DEFAULT true CONSTRAINT company_customization_account_payment_receipt_show_bar_not_null NOT NULL,
    custom_order_minimum_deposit_percent numeric(7,4) DEFAULT 0 CONSTRAINT company_customization_custom_order_minimum_deposit_per_not_null NOT NULL,
    custom_order_refund_approval_limit numeric(12,2) DEFAULT 0 CONSTRAINT company_customization_custom_order_refund_approval_lim_not_null NOT NULL,
    custom_order_slip_enabled boolean DEFAULT true NOT NULL,
    custom_order_slip_auto_print boolean DEFAULT true NOT NULL,
    custom_order_slip_title text DEFAULT 'CUSTOMER''S ORDER SLIP'::text NOT NULL,
    custom_order_slip_contact_line text DEFAULT ''::text NOT NULL,
    custom_order_slip_email_line text DEFAULT ''::text NOT NULL,
    custom_order_slip_footer_note text DEFAULT 'NB: The management is NOT responsible for any LOSS or DAMAGE to your personal property.'::text NOT NULL,
    custom_order_slip_blank_detail_lines integer DEFAULT 8 CONSTRAINT company_customization_custom_order_slip_blank_detail_l_not_null NOT NULL,
    custom_order_slip_show_logo boolean DEFAULT true NOT NULL,
    custom_order_slip_show_order_number boolean DEFAULT true CONSTRAINT company_customization_custom_order_slip_show_order_num_not_null NOT NULL,
    custom_order_slip_show_due_date boolean DEFAULT true NOT NULL,
    custom_order_slip_show_customer_phone boolean DEFAULT true CONSTRAINT company_customization_custom_order_slip_show_customer__not_null NOT NULL,
    custom_order_slip_show_customer_account boolean DEFAULT true CONSTRAINT company_customization_custom_order_slip_show_customer_not_null1 NOT NULL,
    custom_order_slip_show_store boolean DEFAULT true NOT NULL,
    custom_order_slip_show_device boolean DEFAULT true NOT NULL,
    custom_order_slip_show_cashier boolean DEFAULT true NOT NULL,
    custom_order_slip_show_line_items boolean DEFAULT true CONSTRAINT company_customization_custom_order_slip_show_line_item_not_null NOT NULL,
    custom_order_slip_show_pricing boolean DEFAULT true NOT NULL,
    custom_order_slip_show_payment_summary boolean DEFAULT true CONSTRAINT company_customization_custom_order_slip_show_payment_s_not_null NOT NULL,
    custom_order_slip_show_payment_reference boolean DEFAULT true CONSTRAINT company_customization_custom_order_slip_show_payment_r_not_null NOT NULL,
    custom_order_slip_show_taken_by boolean DEFAULT true NOT NULL,
    custom_order_slip_show_signatures boolean DEFAULT true CONSTRAINT company_customization_custom_order_slip_show_signature_not_null NOT NULL,
    badge_template_company_name text DEFAULT 'SmartStock'::text NOT NULL,
    badge_template_logo_url text DEFAULT ''::text NOT NULL,
    badge_template_quote text DEFAULT '"Sales goes up and down, Service is Forever"'::text NOT NULL,
    badge_template_signatory_name text DEFAULT 'Authorized Signature'::text NOT NULL,
    badge_template_signatory_title text DEFAULT 'Management'::text NOT NULL,
    badge_template_back_instructions text DEFAULT 'Scan, swipe, or tap this badge for SmartStock access.'::text NOT NULL,
    badge_template_show_quote boolean DEFAULT true NOT NULL,
    badge_template_show_employee_id boolean DEFAULT true NOT NULL,
    badge_template_show_issue_date boolean DEFAULT true NOT NULL,
    badge_template_show_barcode boolean DEFAULT true NOT NULL,
    badge_template_show_badge_text boolean DEFAULT false NOT NULL,
    badge_template_magstripe_enabled boolean DEFAULT false NOT NULL,
    badge_template_magstripe_track1 text DEFAULT '{badge_id}'::text NOT NULL,
    badge_template_magstripe_track2 text DEFAULT '{badge_id}'::text NOT NULL,
    badge_template_magstripe_track3 text DEFAULT ''::text NOT NULL,
    badge_template_magstripe_command text DEFAULT ''::text NOT NULL,
    badge_template_nfc_enabled boolean DEFAULT false NOT NULL,
    badge_template_nfc_payload text DEFAULT '{badge_id}'::text NOT NULL,
    badge_template_nfc_writer_command text DEFAULT ''::text CONSTRAINT company_customization_badge_template_nfc_writer_comman_not_null NOT NULL,
    badge_template_nfc_verify_command text DEFAULT ''::text CONSTRAINT company_customization_badge_template_nfc_verify_comman_not_null NOT NULL,
    require_badge_pin_login boolean DEFAULT true NOT NULL,
    badge_template_layout_data text DEFAULT ''::text NOT NULL,
    price_tag_show_company boolean DEFAULT true NOT NULL,
    price_tag_show_sku boolean DEFAULT true NOT NULL,
    price_tag_show_barcode boolean DEFAULT true NOT NULL,
    price_tag_width_inches numeric(5,2) DEFAULT 2.25 NOT NULL,
    price_tag_height_inches numeric(5,2) DEFAULT 1.25 NOT NULL,
    price_tag_templates text DEFAULT ''::text NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    sale_discount_limit_percent numeric(7,4) DEFAULT 5 NOT NULL,
    sale_return_approval_limit numeric(12,2) DEFAULT 0 NOT NULL,
    next_quotation_counter integer DEFAULT 1 NOT NULL,
    next_invoice_counter integer DEFAULT 1 NOT NULL,
    next_invoice_delivery_counter integer DEFAULT 1 NOT NULL,
    quotation_default_valid_days integer DEFAULT 30 NOT NULL,
    quotation_print_title text DEFAULT 'QUOTE / NOT FINAL SALE'::text NOT NULL,
    quotation_print_validity_note text DEFAULT 'This is a quote only and is not a final sale. Prices are valid until the valid-until date shown above unless superseded or cancelled.'::text NOT NULL,
    invoice_print_title text DEFAULT 'SALES ORDER CONFIRMATION'::text NOT NULL,
    invoice_delivery_print_title text DEFAULT 'DELIVERY BILL'::text NOT NULL,
    quotation_invoice_print_footer_note text DEFAULT ''::text CONSTRAINT company_customization_quotation_invoice_print_footer_n_not_null NOT NULL,
    quotation_invoice_print_show_signatures boolean DEFAULT true CONSTRAINT company_customization_quotation_invoice_print_show_sig_not_null NOT NULL,
    CONSTRAINT company_customization_custom_order_deposit_percent_chk CHECK (((custom_order_minimum_deposit_percent >= (0)::numeric) AND (custom_order_minimum_deposit_percent <= (100)::numeric))),
    CONSTRAINT company_customization_quotation_days_chk CHECK (((quotation_default_valid_days >= 1) AND (quotation_default_valid_days <= 365))),
    CONSTRAINT company_customization_refund_approval_limit_chk CHECK ((custom_order_refund_approval_limit >= (0)::numeric)),
    CONSTRAINT company_customization_sale_discount_limit_chk CHECK (((sale_discount_limit_percent >= (0)::numeric) AND (sale_discount_limit_percent <= (100)::numeric))),
    CONSTRAINT company_customization_sale_return_approval_limit_chk CHECK ((sale_return_approval_limit >= (0)::numeric)),
    CONSTRAINT company_customization_slip_blank_detail_lines_chk CHECK (((custom_order_slip_blank_detail_lines >= 0) AND (custom_order_slip_blank_detail_lines <= 20))),
    CONSTRAINT company_customization_vat_fixed_rate_percent_chk CHECK (((vat_fixed_rate_percent >= (0)::numeric) AND (vat_fixed_rate_percent <= (100)::numeric)))
);


--
-- Name: company_customization_customization_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.company_customization_customization_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: company_customization_customization_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.company_customization_customization_id_seq OWNED BY public.company_customization.customization_id;


--
-- Name: company_info; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.company_info (
    company_info_id integer DEFAULT 1 NOT NULL,
    company_name text DEFAULT 'SmartStock'::text NOT NULL,
    company_motto_line1 text DEFAULT ''::text NOT NULL,
    company_motto_line2 text DEFAULT ''::text NOT NULL,
    company_logo_url text DEFAULT ''::text NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT company_info_singleton_chk CHECK ((company_info_id = 1))
);


--
-- Name: cross_store_refund_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cross_store_refund_lines (
    request_id uuid NOT NULL,
    source_sale_item_id integer NOT NULL,
    product_id integer NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(14,2) DEFAULT 0 NOT NULL,
    disposition text NOT NULL,
    destination_location_id integer,
    disposition_reason text,
    confirmed_quantity integer DEFAULT 0 NOT NULL,
    conflict_quantity integer DEFAULT 0 NOT NULL,
    destination_status text DEFAULT 'PENDING'::text NOT NULL,
    CONSTRAINT cross_store_refund_lines_disposition_check CHECK ((disposition = ANY (ARRAY['RESTOCK'::text, 'DISCARD'::text]))),
    CONSTRAINT cross_store_refund_lines_quantity_check CHECK ((quantity > 0))
);


--
-- Name: cross_store_refund_reconciliation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cross_store_refund_reconciliation (
    reconciliation_id bigint NOT NULL,
    request_id uuid NOT NULL,
    source_sale_item_id integer,
    source_location_id integer NOT NULL,
    receiving_location_id integer CONSTRAINT cross_store_refund_reconciliatio_receiving_location_id_not_null NOT NULL,
    product_id integer,
    conflict_quantity integer DEFAULT 0 NOT NULL,
    financial_loss numeric(14,2) DEFAULT 0 NOT NULL,
    status text DEFAULT 'OPEN'::text NOT NULL,
    detail text NOT NULL,
    resolved_by_user_id integer,
    resolution_note text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at timestamp with time zone
);


--
-- Name: cross_store_refund_reconciliation_reconciliation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.cross_store_refund_reconciliation_reconciliation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cross_store_refund_reconciliation_reconciliation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cross_store_refund_reconciliation_reconciliation_id_seq OWNED BY public.cross_store_refund_reconciliation.reconciliation_id;


--
-- Name: cross_store_refund_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.cross_store_refund_requests (
    request_id uuid NOT NULL,
    source_location_id integer NOT NULL,
    receiving_location_id integer NOT NULL,
    source_sale_id integer NOT NULL,
    cloud_request_sequence bigint,
    refund_method text NOT NULL,
    refund_amount numeric(14,2) DEFAULT 0 NOT NULL,
    reason text NOT NULL,
    status text NOT NULL,
    user_id integer,
    user_name text,
    device_id uuid,
    cash_drawer_id bigint,
    cash_drawer_session_id bigint,
    last_error text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: custom_order_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_audit_log (
    custom_order_audit_id bigint NOT NULL,
    custom_order_id bigint NOT NULL,
    action_type text NOT NULL,
    field_name text,
    old_value text,
    new_value text,
    reason text,
    user_id integer,
    user_name text,
    device_id text,
    device_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: custom_order_audit_log_custom_order_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_audit_log_custom_order_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_audit_log_custom_order_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_audit_log_custom_order_audit_id_seq OWNED BY public.custom_order_audit_log.custom_order_audit_id;


--
-- Name: custom_order_design_placements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_design_placements (
    design_placement_id bigint NOT NULL,
    placement_name text NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: custom_order_design_placements_design_placement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_design_placements_design_placement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_design_placements_design_placement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_design_placements_design_placement_id_seq OWNED BY public.custom_order_design_placements.design_placement_id;


--
-- Name: custom_order_inventory_reservations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_inventory_reservations (
    custom_order_inventory_reservation_id bigint CONSTRAINT custom_order_inventory_rese_custom_order_inventory_res_not_null NOT NULL,
    custom_order_id bigint NOT NULL,
    custom_order_line_id bigint,
    custom_item_id bigint NOT NULL,
    custom_variant_id bigint,
    item_name text NOT NULL,
    variant_name text,
    reserved_qty numeric(12,2) DEFAULT 1 NOT NULL,
    released_qty numeric(12,2) DEFAULT 0 NOT NULL,
    status text DEFAULT 'RESERVED'::text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    released_at timestamp with time zone,
    release_reason text,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT custom_order_inventory_reservations_status_chk CHECK ((status = ANY (ARRAY['RESERVED'::text, 'RELEASED'::text, 'CONSUMED'::text])))
);


--
-- Name: custom_order_inventory_reserv_custom_order_inventory_reserv_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_inventory_reserv_custom_order_inventory_reserv_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_inventory_reserv_custom_order_inventory_reserv_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_inventory_reserv_custom_order_inventory_reserv_seq OWNED BY public.custom_order_inventory_reservations.custom_order_inventory_reservation_id;


--
-- Name: custom_order_item_barcodes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_item_barcodes (
    custom_item_barcode_id bigint NOT NULL,
    custom_item_id bigint NOT NULL,
    barcode text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: custom_order_item_barcodes_custom_item_barcode_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_item_barcodes_custom_item_barcode_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_item_barcodes_custom_item_barcode_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_item_barcodes_custom_item_barcode_id_seq OWNED BY public.custom_order_item_barcodes.custom_item_barcode_id;


--
-- Name: custom_order_item_movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_item_movements (
    movement_id bigint NOT NULL,
    custom_item_id bigint NOT NULL,
    custom_variant_id bigint,
    variant_name text,
    location_id integer,
    change_qty numeric(12,2) NOT NULL,
    reason text NOT NULL,
    note text,
    user_name text,
    user_id integer,
    device_id text,
    device_name text,
    receive_id text,
    receive_device_id text,
    receive_sequence integer,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    custom_order_id bigint,
    custom_order_line_id bigint,
    custom_order_line_return_id bigint,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: custom_order_item_movements_movement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_item_movements_movement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_item_movements_movement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_item_movements_movement_id_seq OWNED BY public.custom_order_item_movements.movement_id;


--
-- Name: custom_order_item_variants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_item_variants (
    custom_variant_id bigint NOT NULL,
    custom_item_id bigint NOT NULL,
    variant_name text NOT NULL,
    barcode text,
    image_url text,
    fixed_price numeric(12,2),
    quantity_on_hand numeric(12,2) DEFAULT 0 NOT NULL,
    sold_quantity numeric(12,2) DEFAULT 0 NOT NULL,
    reorder_level numeric(12,2) DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sku text
);


--
-- Name: custom_order_item_variants_custom_variant_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_item_variants_custom_variant_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_item_variants_custom_variant_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_item_variants_custom_variant_id_seq OWNED BY public.custom_order_item_variants.custom_variant_id;


--
-- Name: custom_order_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_items (
    custom_item_id bigint NOT NULL,
    item_name text NOT NULL,
    barcode text,
    description text,
    image_url text,
    category_id integer,
    item_type_id integer,
    brand_id integer,
    product_type text DEFAULT 'INVENTORY'::text NOT NULL,
    pricing_type text DEFAULT 'VARIABLE'::text NOT NULL,
    fixed_price numeric(12,2),
    area_price numeric(12,2),
    area_price_unit text,
    dimension_unit text,
    max_width numeric(12,2),
    max_length numeric(12,2),
    has_variants boolean DEFAULT false NOT NULL,
    quantity_on_hand numeric(12,2) DEFAULT 0 NOT NULL,
    sold_quantity numeric(12,2) DEFAULT 0 NOT NULL,
    reorder_level numeric(12,2) DEFAULT 0 NOT NULL,
    minimum_deposit_percent numeric(7,4) DEFAULT 0 NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sku text,
    CONSTRAINT custom_order_items_area_price_chk CHECK (((pricing_type <> 'AREA'::text) OR (has_variants = true) OR (fixed_price IS NOT NULL))),
    CONSTRAINT custom_order_items_deposit_percent_chk CHECK (((minimum_deposit_percent >= (0)::numeric) AND (minimum_deposit_percent <= (100)::numeric))),
    CONSTRAINT custom_order_items_fixed_price_chk CHECK (((pricing_type <> ALL (ARRAY['FIXED'::text, 'AREA'::text])) OR (has_variants = true) OR (fixed_price IS NOT NULL))),
    CONSTRAINT custom_order_items_pricing_type_chk CHECK ((pricing_type = ANY (ARRAY['FIXED'::text, 'VARIABLE'::text, 'AREA'::text]))),
    CONSTRAINT custom_order_items_product_type_chk CHECK ((product_type = ANY (ARRAY['INVENTORY'::text, 'SERVICE'::text, 'NON_INVENTORY'::text])))
);


--
-- Name: custom_order_items_custom_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_items_custom_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_items_custom_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_items_custom_item_id_seq OWNED BY public.custom_order_items.custom_item_id;


--
-- Name: custom_order_line_deliveries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_line_deliveries (
    custom_order_line_delivery_id bigint CONSTRAINT custom_order_line_deliverie_custom_order_line_delivery_not_null NOT NULL,
    custom_order_id bigint NOT NULL,
    custom_order_line_id bigint NOT NULL,
    custom_item_id bigint,
    custom_variant_id bigint,
    item_name text NOT NULL,
    variant_name text,
    delivered_by_user_id integer,
    delivered_by_name text,
    delivery_notes text,
    device_id text,
    device_name text,
    delivered_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: custom_order_line_deliveries_custom_order_line_delivery_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_line_deliveries_custom_order_line_delivery_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_line_deliveries_custom_order_line_delivery_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_line_deliveries_custom_order_line_delivery_id_seq OWNED BY public.custom_order_line_deliveries.custom_order_line_delivery_id;


--
-- Name: custom_order_line_print_addons; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_line_print_addons (
    custom_order_line_print_addon_id bigint CONSTRAINT custom_order_line_print_add_custom_order_line_print_ad_not_null NOT NULL,
    custom_order_line_id bigint NOT NULL,
    print_material_id bigint,
    print_material_name text NOT NULL,
    print_size_preset_id bigint,
    print_size_name text,
    pricing_mode text DEFAULT 'FIXED_PRESET'::text NOT NULL,
    print_description text,
    print_charge numeric(12,2) DEFAULT 0 NOT NULL,
    print_line_count integer DEFAULT 1 NOT NULL,
    sort_order integer DEFAULT 1 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT custom_order_line_print_addons_charge_chk CHECK ((print_charge >= (0)::numeric)),
    CONSTRAINT custom_order_line_print_addons_line_count_chk CHECK ((print_line_count > 0)),
    CONSTRAINT custom_order_line_print_addons_pricing_mode_chk CHECK ((pricing_mode = ANY (ARRAY['FIXED_PRESET'::text, 'PER_LINE'::text])))
);


--
-- Name: custom_order_line_print_addon_custom_order_line_print_addon_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_line_print_addon_custom_order_line_print_addon_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_line_print_addon_custom_order_line_print_addon_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_line_print_addon_custom_order_line_print_addon_seq OWNED BY public.custom_order_line_print_addons.custom_order_line_print_addon_id;


--
-- Name: custom_order_line_production_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_line_production_history (
    custom_order_line_production_history_id bigint CONSTRAINT custom_order_line_productio_custom_order_line_producti_not_null NOT NULL,
    custom_order_id bigint NOT NULL,
    custom_order_line_id bigint CONSTRAINT custom_order_line_production_hist_custom_order_line_id_not_null NOT NULL,
    custom_item_id bigint,
    custom_variant_id bigint,
    item_name text NOT NULL,
    variant_name text,
    old_status text,
    new_status text NOT NULL,
    notes text,
    updated_by_user_id integer,
    updated_by_name text,
    device_id text,
    device_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: custom_order_line_production__custom_order_line_production__seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_line_production__custom_order_line_production__seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_line_production__custom_order_line_production__seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_line_production__custom_order_line_production__seq OWNED BY public.custom_order_line_production_history.custom_order_line_production_history_id;


--
-- Name: custom_order_line_returns; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_line_returns (
    custom_order_line_return_id bigint NOT NULL,
    custom_order_id bigint NOT NULL,
    custom_order_line_id bigint NOT NULL,
    custom_item_id bigint,
    custom_variant_id bigint,
    item_name text NOT NULL,
    variant_name text,
    return_type text DEFAULT 'FULL'::text NOT NULL,
    restock_action text DEFAULT 'NO_RESTOCK'::text NOT NULL,
    refund_amount numeric(12,2) DEFAULT 0 NOT NULL,
    balance_reduction numeric(12,2) DEFAULT 0 NOT NULL,
    payout_amount numeric(12,2) DEFAULT 0 NOT NULL,
    reason text NOT NULL,
    notes text,
    created_by_user_id integer,
    created_by_name text,
    device_id text,
    device_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT custom_order_line_returns_amount_chk CHECK (((refund_amount >= (0)::numeric) AND (balance_reduction >= (0)::numeric) AND (payout_amount >= (0)::numeric))),
    CONSTRAINT custom_order_line_returns_restock_chk CHECK ((restock_action = ANY (ARRAY['RESTOCK'::text, 'DAMAGED'::text, 'CUSTOMER_KEPT'::text, 'WASTE'::text, 'NO_RESTOCK'::text]))),
    CONSTRAINT custom_order_line_returns_type_chk CHECK ((return_type = ANY (ARRAY['FULL'::text, 'PARTIAL'::text])))
);


--
-- Name: custom_order_line_returns_custom_order_line_return_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_line_returns_custom_order_line_return_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_line_returns_custom_order_line_return_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_line_returns_custom_order_line_return_id_seq OWNED BY public.custom_order_line_returns.custom_order_line_return_id;


--
-- Name: custom_order_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_lines (
    custom_order_line_id bigint NOT NULL,
    custom_order_id bigint NOT NULL,
    custom_item_id bigint,
    item_name text NOT NULL,
    pricing_type text NOT NULL,
    unit_price numeric(12,2) NOT NULL,
    line_total numeric(12,2) NOT NULL,
    width_inches numeric(12,2),
    length_inches numeric(12,2),
    square_feet numeric(12,4),
    width_value numeric(12,2),
    length_value numeric(12,2),
    dimension_unit text,
    area_value numeric(12,4),
    area_unit text,
    area_price numeric(12,2),
    base_item_price numeric(12,2),
    print_material_id bigint,
    print_material_name text,
    print_size_preset_id bigint,
    print_size_name text,
    print_charge numeric(12,2) DEFAULT 0 NOT NULL,
    print_line_count integer DEFAULT 1 NOT NULL,
    customization_details text NOT NULL,
    order_instructions text,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    returned_amount numeric(12,2) DEFAULT 0 NOT NULL,
    return_status text DEFAULT 'NONE'::text NOT NULL,
    delivery_status text DEFAULT 'PENDING'::text NOT NULL,
    delivered_at timestamp with time zone,
    delivered_by_user_id integer,
    delivered_by_name text,
    custom_variant_id bigint,
    variant_name text,
    original_line_total numeric(12,2),
    line_discount_percent numeric(7,4) DEFAULT 0 NOT NULL,
    line_discount_amount numeric(12,2) DEFAULT 0 NOT NULL,
    line_discount_by_user_id integer,
    line_discount_by_name text,
    line_discount_reason text,
    minimum_deposit_percent numeric(7,4) DEFAULT 0 NOT NULL,
    original_base_price numeric(12,2),
    price_override_price numeric(12,2),
    price_override_reason text,
    price_override_by_user_id integer,
    price_override_by_name text,
    production_status text DEFAULT 'NOT_STARTED'::text NOT NULL,
    production_updated_at timestamp with time zone,
    production_updated_by_user_id integer,
    production_updated_by_name text,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT custom_order_lines_delivery_status_chk CHECK ((delivery_status = ANY (ARRAY['PENDING'::text, 'DELIVERED'::text]))),
    CONSTRAINT custom_order_lines_discount_amount_chk CHECK ((line_discount_amount >= (0)::numeric)),
    CONSTRAINT custom_order_lines_discount_percent_chk CHECK (((line_discount_percent >= (0)::numeric) AND (line_discount_percent <= (100)::numeric))),
    CONSTRAINT custom_order_lines_min_deposit_percent_chk CHECK (((minimum_deposit_percent >= (0)::numeric) AND (minimum_deposit_percent <= (100)::numeric))),
    CONSTRAINT custom_order_lines_pricing_type_chk CHECK ((pricing_type = ANY (ARRAY['FIXED'::text, 'VARIABLE'::text, 'AREA'::text]))),
    CONSTRAINT custom_order_lines_production_status_chk CHECK ((production_status = ANY (ARRAY['NOT_STARTED'::text, 'DESIGN_APPROVED'::text, 'PRINTED'::text, 'FINISHED'::text, 'QUALITY_CHECKED'::text, 'READY'::text]))),
    CONSTRAINT custom_order_lines_return_status_chk CHECK ((return_status = ANY (ARRAY['NONE'::text, 'PARTIAL'::text, 'FULL'::text])))
);


--
-- Name: custom_order_lines_custom_order_line_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_lines_custom_order_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_lines_custom_order_line_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_lines_custom_order_line_id_seq OWNED BY public.custom_order_lines.custom_order_line_id;


--
-- Name: custom_order_payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_payments (
    custom_order_payment_id bigint NOT NULL,
    custom_order_id bigint NOT NULL,
    payment_amount numeric(12,2) NOT NULL,
    payment_method text NOT NULL,
    payment_reference text,
    taken_by_user_id integer,
    taken_by_name text,
    payment_action text DEFAULT 'PAYMENT'::text NOT NULL,
    voided_at timestamp with time zone,
    voided_by_user_id integer,
    voided_by_name text,
    void_reason text,
    device_id text,
    device_name text,
    cash_drawer_id bigint,
    cash_drawer_name text,
    cash_drawer_session_id bigint,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT custom_order_payments_action_chk CHECK ((payment_action = ANY (ARRAY['PAYMENT'::text, 'REFUND'::text, 'REVERSAL'::text]))),
    CONSTRAINT custom_order_payments_amount_chk CHECK ((payment_amount > (0)::numeric)),
    CONSTRAINT custom_order_payments_method_chk CHECK ((payment_method = ANY (ARRAY['CASH'::text, 'CARD'::text, 'CHEQUE'::text, 'MMG'::text, 'ACCOUNT'::text])))
);


--
-- Name: custom_order_payments_custom_order_payment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_payments_custom_order_payment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_payments_custom_order_payment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_payments_custom_order_payment_id_seq OWNED BY public.custom_order_payments.custom_order_payment_id;


--
-- Name: custom_order_print_materials; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_print_materials (
    print_material_id bigint NOT NULL,
    material_name text NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    pricing_mode text DEFAULT 'FIXED_PRESET'::text NOT NULL
);


--
-- Name: custom_order_print_materials_print_material_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_print_materials_print_material_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_print_materials_print_material_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_print_materials_print_material_id_seq OWNED BY public.custom_order_print_materials.print_material_id;


--
-- Name: custom_order_print_size_presets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_print_size_presets (
    print_size_preset_id bigint NOT NULL,
    print_material_id bigint NOT NULL,
    preset_name text NOT NULL,
    pricing_mode text DEFAULT 'FIXED_PRESET'::text NOT NULL,
    fixed_price numeric(12,2) NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT custom_order_print_size_presets_price_chk CHECK ((fixed_price >= (0)::numeric)),
    CONSTRAINT custom_order_print_size_presets_pricing_mode_chk CHECK ((pricing_mode = ANY (ARRAY['FIXED_PRESET'::text, 'PER_LINE'::text])))
);


--
-- Name: custom_order_print_size_presets_print_size_preset_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_print_size_presets_print_size_preset_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_print_size_presets_print_size_preset_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_print_size_presets_print_size_preset_id_seq OWNED BY public.custom_order_print_size_presets.print_size_preset_id;


--
-- Name: custom_order_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_order_status_history (
    custom_order_status_history_id bigint CONSTRAINT custom_order_status_history_custom_order_status_histor_not_null NOT NULL,
    custom_order_id bigint NOT NULL,
    old_status text,
    new_status text NOT NULL,
    reason text,
    user_id integer,
    user_name text,
    device_id text,
    device_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: custom_order_status_history_custom_order_status_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_order_status_history_custom_order_status_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_order_status_history_custom_order_status_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_order_status_history_custom_order_status_history_id_seq OWNED BY public.custom_order_status_history.custom_order_status_history_id;


--
-- Name: custom_orders; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.custom_orders (
    custom_order_id bigint NOT NULL,
    order_number text NOT NULL,
    customer_id integer NOT NULL,
    customer_name text NOT NULL,
    customer_phone text NOT NULL,
    status text DEFAULT 'NEW'::text NOT NULL,
    due_date date,
    order_notes text,
    total_amount numeric(12,2) DEFAULT 0 NOT NULL,
    amount_paid numeric(12,2) DEFAULT 0 NOT NULL,
    balance_due numeric(12,2) DEFAULT 0 NOT NULL,
    payment_method text,
    payment_reference text,
    payment_status text DEFAULT 'UNPAID'::text NOT NULL,
    taken_by_user_id integer,
    taken_by_name text,
    location_id integer,
    location_name text,
    device_id text,
    device_name text,
    cash_drawer_id bigint,
    cash_drawer_name text,
    cash_drawer_session_id bigint,
    minimum_deposit_required numeric(12,2) DEFAULT 0 NOT NULL,
    deposit_override_reason text,
    deposit_override_by_user_id integer,
    deposit_override_by_name text,
    assigned_to_user_id integer,
    assigned_to_name text,
    assigned_by_user_id integer,
    assigned_by_name text,
    assigned_at timestamp with time zone,
    completed_at timestamp with time zone,
    delivered_at timestamp with time zone,
    cancellation_reason text,
    cancelled_at timestamp with time zone,
    cancelled_by_user_id integer,
    cancelled_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT custom_orders_amount_paid_chk CHECK (((amount_paid >= (0)::numeric) AND (balance_due >= (0)::numeric))),
    CONSTRAINT custom_orders_payment_method_chk CHECK (((payment_method IS NULL) OR (payment_method = ANY (ARRAY['CASH'::text, 'CARD'::text, 'CHEQUE'::text, 'MMG'::text, 'ACCOUNT'::text])))),
    CONSTRAINT custom_orders_payment_status_chk CHECK ((payment_status = ANY (ARRAY['PAID'::text, 'PARTIAL'::text, 'UNPAID'::text]))),
    CONSTRAINT custom_orders_status_chk CHECK ((status = ANY (ARRAY['NEW'::text, 'ASSIGNED'::text, 'IN_PROGRESS'::text, 'READY'::text, 'COMPLETED'::text, 'DELIVERED'::text, 'CANCELLED'::text])))
);


--
-- Name: custom_orders_custom_order_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.custom_orders_custom_order_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: custom_orders_custom_order_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.custom_orders_custom_order_id_seq OWNED BY public.custom_orders.custom_order_id;


--
-- Name: customer_account_ba_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_account_ba_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_account_ca_number_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_account_ca_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_account_payment_allocations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_account_payment_allocations (
    allocation_id bigint NOT NULL,
    payment_transaction_id bigint CONSTRAINT customer_account_payment_alloca_payment_transaction_id_not_null NOT NULL,
    customer_id integer NOT NULL,
    sale_id integer,
    custom_order_id bigint,
    sales_order_id bigint,
    amount numeric(12,2) DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    invoice_id bigint,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: customer_account_payment_allocations_allocation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_account_payment_allocations_allocation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_account_payment_allocations_allocation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_account_payment_allocations_allocation_id_seq OWNED BY public.customer_account_payment_allocations.allocation_id;


--
-- Name: customer_account_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_account_transactions (
    transaction_id bigint NOT NULL,
    customer_id integer NOT NULL,
    sale_id integer,
    custom_order_id bigint,
    sales_order_id bigint,
    payment_id text,
    location_id integer,
    amount numeric(12,2) DEFAULT 0 NOT NULL,
    transaction_type text NOT NULL,
    note text,
    user_name text,
    device_id text,
    device_name text,
    payment_method text,
    payment_reference text,
    cash_drawer_id bigint,
    cash_drawer_name text,
    cash_drawer_session_id bigint,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    invoice_id bigint,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: customer_account_transactions_transaction_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_account_transactions_transaction_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_account_transactions_transaction_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_account_transactions_transaction_id_seq OWNED BY public.customer_account_transactions.transaction_id;


--
-- Name: customer_accounts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_accounts (
    customer_id integer NOT NULL,
    account_number text,
    name text NOT NULL,
    customer_type_id integer,
    phone text,
    email text,
    credit_limit numeric(12,2) DEFAULT 0 NOT NULL,
    current_balance numeric(12,2) DEFAULT 0 NOT NULL,
    is_business boolean DEFAULT false NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    account_notes text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: customer_accounts_customer_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_accounts_customer_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_accounts_customer_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_accounts_customer_id_seq OWNED BY public.customer_accounts.customer_id;


--
-- Name: customer_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.customer_types (
    customer_type_id integer NOT NULL,
    name text NOT NULL,
    description text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: customer_types_customer_type_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.customer_types_customer_type_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: customer_types_customer_type_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.customer_types_customer_type_id_seq OWNED BY public.customer_types.customer_type_id;


--
-- Name: device_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.device_sessions (
    session_id bigint NOT NULL,
    device_id uuid NOT NULL,
    user_id integer,
    store_id integer,
    login_time timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    logout_time timestamp with time zone,
    session_status text DEFAULT 'ACTIVE'::text NOT NULL
);


--
-- Name: device_sessions_session_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.device_sessions_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: device_sessions_session_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.device_sessions_session_id_seq OWNED BY public.device_sessions.session_id;


--
-- Name: devices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.devices (
    device_id uuid DEFAULT gen_random_uuid() NOT NULL,
    installation_id text NOT NULL,
    device_fingerprint text,
    device_name text,
    hostname text,
    os_name text,
    os_version text,
    os_arch text,
    java_version text,
    app_version text,
    local_username text,
    mac_addresses text,
    first_seen timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_seen timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    last_login_user_id integer,
    last_store_id integer,
    is_approved boolean DEFAULT false NOT NULL,
    allow_persistent_login boolean DEFAULT false NOT NULL,
    auto_logout_enabled boolean DEFAULT false NOT NULL,
    auto_logout_minutes integer DEFAULT 15 NOT NULL,
    is_blocked boolean DEFAULT false NOT NULL,
    approved_at timestamp with time zone,
    approved_by_user_id integer,
    blocked_at timestamp with time zone,
    blocked_by_user_id integer,
    status_notes text,
    receipt_device_code text DEFAULT '0001'::text NOT NULL,
    allow_sales boolean DEFAULT true NOT NULL,
    allow_orders boolean DEFAULT true NOT NULL,
    access_mode text DEFAULT 'CLIENT'::text NOT NULL,
    session_count bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    pairing_public_key text,
    credential_status text DEFAULT 'PENDING'::text NOT NULL,
    credential_issued_at timestamp with time zone,
    credential_claimed_at timestamp with time zone,
    api_credential_hash text,
    api_previous_credential_hash text,
    api_credential_issued_at timestamp with time zone,
    api_credential_expires_at timestamp with time zone,
    api_previous_expires_at timestamp with time zone,
    api_credential_last_used_at timestamp with time zone,
    api_server_fingerprint text,
    api_pairing_challenge_hash text,
    api_pairing_challenge_expires_at timestamp with time zone,
    CONSTRAINT devices_access_mode_check CHECK ((access_mode = ANY (ARRAY['CLIENT'::text, 'SERVER'::text, 'REMOTE_ADMIN'::text]))),
    CONSTRAINT devices_auto_logout_minutes_check CHECK (((auto_logout_minutes >= 1) AND (auto_logout_minutes <= 480))),
    CONSTRAINT devices_credential_status_check CHECK ((credential_status = ANY (ARRAY['PENDING'::text, 'ISSUED'::text, 'CLAIMED'::text, 'ROTATION_PENDING'::text, 'REVOKED'::text])))
);


--
-- Name: email_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_outbox (
    email_outbox_id bigint NOT NULL,
    location_id integer,
    sender_email text NOT NULL,
    sender_name text DEFAULT ''::text NOT NULL,
    recipient_email text NOT NULL,
    bcc_email text,
    subject text NOT NULL,
    body_text text DEFAULT ''::text NOT NULL,
    body_html text DEFAULT ''::text NOT NULL,
    attachment_name text,
    attachment_content_type text,
    attachment_body text,
    document_type text NOT NULL,
    document_id text NOT NULL,
    status text DEFAULT 'QUEUED'::text NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    max_attempts integer DEFAULT 3 NOT NULL,
    last_error text,
    sent_at timestamp with time zone,
    queued_by_user_id integer,
    queued_by_name text,
    device_id text,
    device_name text,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT email_outbox_attempts_chk CHECK (((attempts >= 0) AND (max_attempts > 0))),
    CONSTRAINT email_outbox_status_chk CHECK ((status = ANY (ARRAY['QUEUED'::text, 'SENDING'::text, 'SENT'::text, 'FAILED'::text, 'CANCELLED'::text])))
);


--
-- Name: email_outbox_email_outbox_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_outbox_email_outbox_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_outbox_email_outbox_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_outbox_email_outbox_id_seq OWNED BY public.email_outbox.email_outbox_id;


--
-- Name: email_outbox_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.email_outbox_events (
    email_outbox_event_id bigint NOT NULL,
    email_outbox_id bigint NOT NULL,
    event_type text NOT NULL,
    message text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    user_id integer,
    user_name text,
    device_id text,
    device_name text,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: email_outbox_events_email_outbox_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.email_outbox_events_email_outbox_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: email_outbox_events_email_outbox_event_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.email_outbox_events_email_outbox_event_id_seq OWNED BY public.email_outbox_events.email_outbox_event_id;


--
-- Name: employee_payroll_bonuses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_payroll_bonuses (
    employee_payroll_bonus_id bigint NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id integer NOT NULL,
    location_id integer,
    employee_name text,
    pay_period_start date NOT NULL,
    pay_period_end date NOT NULL,
    amount numeric(12,2) NOT NULL,
    reason text,
    created_by_user_id integer,
    created_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT employee_payroll_bonuses_amount_chk CHECK ((amount > (0)::numeric))
);


--
-- Name: employee_payroll_bonuses_employee_payroll_bonus_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.employee_payroll_bonuses_employee_payroll_bonus_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: employee_payroll_bonuses_employee_payroll_bonus_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.employee_payroll_bonuses_employee_payroll_bonus_id_seq OWNED BY public.employee_payroll_bonuses.employee_payroll_bonus_id;


--
-- Name: employee_payroll_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_payroll_settings (
    setting_id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id integer NOT NULL,
    period_type text DEFAULT 'SEMI_MONTHLY'::text NOT NULL,
    work_hour_limit numeric(8,2) DEFAULT 80.00 NOT NULL,
    effective_from date NOT NULL,
    created_by_user_id integer,
    created_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT employee_payroll_settings_hour_limit_chk CHECK ((work_hour_limit > (0)::numeric)),
    CONSTRAINT employee_payroll_settings_period_type_chk CHECK ((period_type = ANY (ARRAY['SEMI_MONTHLY'::text, 'WEEKLY'::text, 'FOUR_MONTH_BLOCKS'::text])))
);


--
-- Name: employee_schedule_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_schedule_assignments (
    location_id integer NOT NULL,
    user_id integer NOT NULL,
    work_date date NOT NULL,
    lunch_start_time time without time zone,
    shift_id uuid,
    shift_name_snapshot text,
    shift_start_time time without time zone,
    shift_end_time time without time zone,
    created_by_user_id integer,
    created_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: employee_schedule_holidays; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_schedule_holidays (
    holiday_id uuid DEFAULT gen_random_uuid() NOT NULL,
    holiday_date date NOT NULL,
    holiday_name text DEFAULT 'Holiday'::text NOT NULL,
    created_by_user_id integer,
    created_by_name text,
    updated_by_user_id integer,
    updated_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT employee_schedule_holidays_name_chk CHECK ((length(TRIM(BOTH FROM holiday_name)) > 0))
);


--
-- Name: employee_schedule_shifts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_schedule_shifts (
    shift_id uuid DEFAULT gen_random_uuid() NOT NULL,
    location_id integer NOT NULL,
    shift_name text NOT NULL,
    start_time time without time zone NOT NULL,
    end_time time without time zone NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_by_user_id integer,
    created_by_name text,
    updated_by_user_id integer,
    updated_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT employee_schedule_shifts_daytime_check CHECK ((end_time > start_time))
);


--
-- Name: employee_time_clock; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_time_clock (
    clock_id bigint NOT NULL,
    user_id integer NOT NULL,
    user_name text,
    location_id integer,
    location_name text,
    work_date date DEFAULT CURRENT_DATE NOT NULL,
    clock_in timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    lunch_start timestamp with time zone,
    lunch_end timestamp with time zone,
    break_start timestamp with time zone,
    break_end timestamp with time zone,
    clock_out timestamp with time zone,
    total_hours_worked numeric(10,2),
    total_earned numeric(12,2),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    auto_break_end boolean DEFAULT false NOT NULL,
    auto_break_end_detected_at timestamp with time zone,
    auto_break_end_review_status text,
    multiple_session_override_required boolean DEFAULT false NOT NULL,
    multiple_session_override_reason text,
    multiple_session_override_by_user_id integer,
    multiple_session_override_by_name text,
    auto_close_enabled_snapshot boolean DEFAULT true NOT NULL,
    auto_close_rule_snapshot text,
    auto_close_detection_at timestamp with time zone,
    auto_close_max_work_hours integer DEFAULT 8 NOT NULL,
    scheduled_shift_id_snapshot uuid,
    scheduled_shift_name_snapshot text,
    scheduled_shift_end_at_snapshot timestamp with time zone,
    auto_clock_out boolean DEFAULT false NOT NULL,
    auto_clock_out_detected_at timestamp with time zone,
    auto_clock_out_review_status text,
    auto_clock_out_reviewed_at timestamp with time zone,
    auto_clock_out_reviewed_by_user_id integer,
    auto_clock_out_reviewed_by_name text,
    auto_clock_out_review_reason text,
    CONSTRAINT employee_time_clock_auto_review_chk CHECK (((auto_clock_out_review_status IS NULL) OR (auto_clock_out_review_status = ANY (ARRAY['PENDING'::text, 'CONFIRMED'::text, 'CORRECTED'::text])))),
    CONSTRAINT employee_time_clock_auto_rule_chk CHECK (((auto_close_rule_snapshot IS NULL) OR (auto_close_rule_snapshot = ANY (ARRAY['SCHEDULED'::text, 'UNSCHEDULED'::text])))),
    CONSTRAINT employee_time_clock_break_order CHECK (((break_start IS NULL) OR (break_end IS NULL) OR (break_end >= break_start))),
    CONSTRAINT employee_time_clock_lunch_order CHECK (((lunch_start IS NULL) OR (lunch_end IS NULL) OR (lunch_end >= lunch_start))),
    CONSTRAINT employee_time_clock_out_order CHECK (((clock_out IS NULL) OR (clock_out >= clock_in)))
);


--
-- Name: employee_time_clock_adjustments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.employee_time_clock_adjustments (
    adjustment_id uuid DEFAULT gen_random_uuid() NOT NULL,
    clock_id bigint NOT NULL,
    user_id integer NOT NULL,
    action_type text NOT NULL,
    before_clock_in timestamp with time zone,
    before_lunch_start timestamp with time zone,
    before_lunch_end timestamp with time zone,
    before_break_start timestamp with time zone,
    before_break_end timestamp with time zone,
    before_clock_out timestamp with time zone,
    before_hours numeric(10,2),
    after_clock_in timestamp with time zone,
    after_lunch_start timestamp with time zone,
    after_lunch_end timestamp with time zone,
    after_break_start timestamp with time zone,
    after_break_end timestamp with time zone,
    after_clock_out timestamp with time zone,
    after_hours numeric(10,2),
    reason text NOT NULL,
    actor_user_id integer,
    actor_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT employee_time_clock_adjustments_action_chk CHECK ((action_type = ANY (ARRAY['AUTO_CLOSE'::text, 'BREAK_AUTO_END'::text, 'CONFIRM'::text, 'CORRECT'::text])))
);


--
-- Name: employee_time_clock_clock_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.employee_time_clock_clock_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: employee_time_clock_clock_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.employee_time_clock_clock_id_seq OWNED BY public.employee_time_clock.clock_id;


--
-- Name: expenses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.expenses (
    expense_id bigint NOT NULL,
    location_id integer,
    expense_date date DEFAULT CURRENT_DATE NOT NULL,
    category text NOT NULL,
    payee text,
    description text,
    amount numeric(12,2) NOT NULL,
    payment_method text,
    payment_reference text,
    status text DEFAULT 'PAID'::text NOT NULL,
    source_type text,
    source_id text,
    created_by_user_id integer,
    created_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT expenses_amount_chk CHECK ((amount >= (0)::numeric)),
    CONSTRAINT expenses_status_chk CHECK ((status = ANY (ARRAY['PAID'::text, 'UNPAID'::text])))
);


--
-- Name: expenses_expense_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.expenses_expense_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: expenses_expense_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.expenses_expense_id_seq OWNED BY public.expenses.expense_id;


--
-- Name: held_cart_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.held_cart_items (
    held_cart_item_id bigint NOT NULL,
    held_cart_id bigint NOT NULL,
    product_id integer NOT NULL,
    product_name text,
    description text,
    sku text,
    unit_price numeric(12,2) DEFAULT 0 NOT NULL,
    quantity integer NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    product_type text DEFAULT 'INVENTORY'::text NOT NULL,
    discount_percent numeric(5,2) DEFAULT 0 NOT NULL,
    CONSTRAINT held_cart_items_product_type_check CHECK ((product_type = ANY (ARRAY['INVENTORY'::text, 'SERVICE'::text, 'NON_INVENTORY'::text]))),
    CONSTRAINT held_cart_items_quantity_check CHECK ((quantity > 0))
);


--
-- Name: held_cart_items_held_cart_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.held_cart_items_held_cart_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: held_cart_items_held_cart_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.held_cart_items_held_cart_item_id_seq OWNED BY public.held_cart_items.held_cart_item_id;


--
-- Name: held_carts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.held_carts (
    held_cart_id bigint NOT NULL,
    location_id integer NOT NULL,
    user_id integer,
    user_name text,
    customer_id integer,
    hold_name text,
    payment_method text,
    total_amount numeric(12,2) DEFAULT 0 NOT NULL,
    status text DEFAULT 'OPEN'::text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resumed_at timestamp with time zone,
    resumed_by_user_id integer,
    resumed_by_name text,
    subtotal_amount numeric(12,2) DEFAULT 0 NOT NULL,
    discount_percent numeric(5,2) DEFAULT 0 NOT NULL,
    discount_amount numeric(12,2) DEFAULT 0 NOT NULL
);


--
-- Name: held_carts_held_cart_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.held_carts_held_cart_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: held_carts_held_cart_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.held_carts_held_cart_id_seq OWNED BY public.held_carts.held_cart_id;


--
-- Name: image_asset_references; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.image_asset_references (
    reference_id uuid DEFAULT gen_random_uuid() NOT NULL,
    asset_id uuid NOT NULL,
    source_table text NOT NULL,
    source_key text NOT NULL,
    source_column text NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: image_assets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.image_assets (
    asset_id uuid DEFAULT gen_random_uuid() NOT NULL,
    category text NOT NULL,
    bucket_name text NOT NULL,
    object_path text NOT NULL,
    access_level text DEFAULT 'PUBLIC'::text NOT NULL,
    original_filename text DEFAULT ''::text NOT NULL,
    content_type text DEFAULT 'application/octet-stream'::text NOT NULL,
    byte_size bigint DEFAULT 0 NOT NULL,
    sha256 text DEFAULT ''::text NOT NULL,
    lifecycle_status text DEFAULT 'ACTIVE'::text NOT NULL,
    local_status text DEFAULT 'MISSING'::text NOT NULL,
    cloud_status text DEFAULT 'PENDING'::text NOT NULL,
    retained boolean DEFAULT false NOT NULL,
    unused_since timestamp with time zone,
    last_verified_at timestamp with time zone,
    last_error text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by_user_id integer,
    deleted_by_name text,
    CONSTRAINT image_assets_access_level_check CHECK ((access_level = ANY (ARRAY['PUBLIC'::text, 'AUTHENTICATED'::text]))),
    CONSTRAINT image_assets_byte_size_check CHECK ((byte_size >= 0)),
    CONSTRAINT image_assets_cloud_status_check CHECK ((cloud_status = ANY (ARRAY['PENDING'::text, 'PRESENT'::text, 'MISSING'::text, 'FAILED'::text, 'DELETED'::text]))),
    CONSTRAINT image_assets_lifecycle_status_check CHECK ((lifecycle_status = ANY (ARRAY['ACTIVE'::text, 'UNUSED'::text, 'DELETE_PENDING'::text, 'DELETED'::text]))),
    CONSTRAINT image_assets_local_status_check CHECK ((local_status = ANY (ARRAY['PRESENT'::text, 'MISSING'::text, 'CORRUPT'::text])))
);


--
-- Name: image_sync_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.image_sync_state (
    state_key text NOT NULL,
    state_value text NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: inventory; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory (
    product_id integer NOT NULL,
    location_id integer NOT NULL,
    quantity_on_hand integer DEFAULT 0 NOT NULL,
    reorder_level integer DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: inventory_movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.inventory_movements (
    movement_id bigint NOT NULL,
    product_id integer NOT NULL,
    location_id integer NOT NULL,
    change_qty integer DEFAULT 0 NOT NULL,
    reason text DEFAULT 'ADJUSTMENT'::text NOT NULL,
    note text,
    user_name text,
    sale_id integer,
    sale_item_id integer,
    device_id text,
    device_name text,
    user_id integer,
    receive_id text,
    receive_device_id text,
    receive_sequence integer,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sale_return_id bigint,
    invoice_id bigint,
    invoice_line_id bigint,
    invoice_delivery_event_id bigint,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: inventory_movements_movement_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.inventory_movements_movement_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_movements_movement_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_movements_movement_id_seq OWNED BY public.inventory_movements.movement_id;


--
-- Name: invoice_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_audit_log (
    invoice_audit_id bigint NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    invoice_id bigint NOT NULL,
    action_type text NOT NULL,
    field_name text,
    old_value text,
    new_value text,
    reason text,
    user_id integer,
    user_name text,
    device_id text,
    device_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: invoice_audit_log_invoice_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.invoice_audit_log_invoice_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: invoice_audit_log_invoice_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.invoice_audit_log_invoice_audit_id_seq OWNED BY public.invoice_audit_log.invoice_audit_id;


--
-- Name: invoice_delivery_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_delivery_events (
    invoice_delivery_event_id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    delivery_number text NOT NULL,
    delivery_method text DEFAULT 'PICKUP'::text NOT NULL,
    receiver_name text,
    delivery_notes text,
    remaining_balance numeric(12,2) DEFAULT 0 NOT NULL,
    delivered_by_user_id integer,
    delivered_by_name text,
    location_id integer,
    device_id text,
    device_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT invoice_delivery_events_method_chk CHECK ((delivery_method = ANY (ARRAY['PICKUP'::text, 'LOCAL_DELIVERY'::text, 'SHIP'::text, 'INSTALLATION'::text])))
);


--
-- Name: invoice_delivery_events_invoice_delivery_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.invoice_delivery_events_invoice_delivery_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: invoice_delivery_events_invoice_delivery_event_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.invoice_delivery_events_invoice_delivery_event_id_seq OWNED BY public.invoice_delivery_events.invoice_delivery_event_id;


--
-- Name: invoice_delivery_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_delivery_lines (
    invoice_delivery_line_id bigint NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    invoice_delivery_event_id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    invoice_line_id bigint NOT NULL,
    product_id integer,
    item_name text NOT NULL,
    quantity_delivered integer NOT NULL,
    quantity_remaining integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT invoice_delivery_lines_qty_chk CHECK (((quantity_delivered > 0) AND (quantity_remaining >= 0)))
);


--
-- Name: invoice_delivery_lines_invoice_delivery_line_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.invoice_delivery_lines_invoice_delivery_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: invoice_delivery_lines_invoice_delivery_line_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.invoice_delivery_lines_invoice_delivery_line_id_seq OWNED BY public.invoice_delivery_lines.invoice_delivery_line_id;


--
-- Name: invoice_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_lines (
    invoice_line_id bigint NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    invoice_id bigint NOT NULL,
    quotation_line_id bigint,
    product_id integer,
    item_name text NOT NULL,
    sku text,
    quantity_invoiced integer DEFAULT 1 NOT NULL,
    quantity_delivered integer DEFAULT 0 NOT NULL,
    unit_price numeric(12,2) DEFAULT 0 NOT NULL,
    original_unit_price numeric(12,2),
    price_override_reason text,
    price_override_by_user_id integer,
    price_override_by_name text,
    category_id integer,
    discount_percent numeric(7,4) DEFAULT 0 NOT NULL,
    discount_amount numeric(12,2) DEFAULT 0 NOT NULL,
    vat_rate_percent numeric(6,2) DEFAULT 0 NOT NULL,
    vat_amount numeric(12,2) DEFAULT 0 NOT NULL,
    line_total numeric(12,2) DEFAULT 0 NOT NULL,
    delivery_method text DEFAULT 'PICKUP'::text NOT NULL,
    delivery_status text DEFAULT 'PENDING'::text NOT NULL,
    line_notes text,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT invoice_lines_amounts_chk CHECK (((unit_price >= (0)::numeric) AND (discount_amount >= (0)::numeric) AND (line_total >= (0)::numeric))),
    CONSTRAINT invoice_lines_delivery_method_chk CHECK ((delivery_method = ANY (ARRAY['PICKUP'::text, 'LOCAL_DELIVERY'::text, 'SHIP'::text, 'INSTALLATION'::text]))),
    CONSTRAINT invoice_lines_delivery_status_chk CHECK ((delivery_status = ANY (ARRAY['PENDING'::text, 'PARTIAL'::text, 'DELIVERED'::text]))),
    CONSTRAINT invoice_lines_discount_percent_chk CHECK (((discount_percent >= (0)::numeric) AND (discount_percent <= (100)::numeric))),
    CONSTRAINT invoice_lines_qty_chk CHECK (((quantity_invoiced > 0) AND (quantity_delivered >= 0) AND (quantity_delivered <= quantity_invoiced))),
    CONSTRAINT invoice_lines_vat_chk CHECK (((vat_rate_percent >= (0)::numeric) AND (vat_rate_percent <= (100)::numeric) AND (vat_amount >= (0)::numeric)))
);


--
-- Name: invoice_lines_invoice_line_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.invoice_lines_invoice_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: invoice_lines_invoice_line_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.invoice_lines_invoice_line_id_seq OWNED BY public.invoice_lines.invoice_line_id;


--
-- Name: invoice_payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_payments (
    invoice_payment_id bigint NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    invoice_id bigint NOT NULL,
    customer_id integer,
    payment_amount numeric(12,2) NOT NULL,
    payment_method text NOT NULL,
    payment_reference text,
    payment_action text DEFAULT 'PAYMENT'::text NOT NULL,
    voided_at timestamp with time zone,
    voided_by_user_id integer,
    voided_by_name text,
    void_reason text,
    taken_by_user_id integer,
    taken_by_name text,
    location_id integer,
    device_id text,
    device_name text,
    cash_drawer_id bigint,
    cash_drawer_name text,
    cash_drawer_session_id bigint,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT invoice_payments_action_chk CHECK ((payment_action = ANY (ARRAY['PAYMENT'::text, 'REFUND'::text, 'REVERSAL'::text]))),
    CONSTRAINT invoice_payments_amount_chk CHECK ((payment_amount > (0)::numeric)),
    CONSTRAINT invoice_payments_method_chk CHECK ((payment_method = ANY (ARRAY['CASH'::text, 'CARD'::text, 'CHEQUE'::text, 'MMG'::text, 'ACCOUNT'::text])))
);


--
-- Name: invoice_payments_invoice_payment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.invoice_payments_invoice_payment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: invoice_payments_invoice_payment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.invoice_payments_invoice_payment_id_seq OWNED BY public.invoice_payments.invoice_payment_id;


--
-- Name: invoice_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoice_status_history (
    invoice_status_history_id bigint NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    invoice_id bigint NOT NULL,
    old_status text,
    new_status text NOT NULL,
    reason text,
    user_id integer,
    user_name text,
    device_id text,
    device_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: invoice_status_history_invoice_status_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.invoice_status_history_invoice_status_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: invoice_status_history_invoice_status_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.invoice_status_history_invoice_status_history_id_seq OWNED BY public.invoice_status_history.invoice_status_history_id;


--
-- Name: invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.invoices (
    invoice_id bigint NOT NULL,
    invoice_number text NOT NULL,
    quotation_id bigint,
    quotation_number text,
    customer_id integer NOT NULL,
    customer_name text NOT NULL,
    customer_phone text,
    customer_email text,
    status text DEFAULT 'OPEN'::text NOT NULL,
    invoice_date date DEFAULT CURRENT_DATE NOT NULL,
    invoice_notes text,
    subtotal_amount numeric(12,2) DEFAULT 0 NOT NULL,
    discount_amount numeric(12,2) DEFAULT 0 NOT NULL,
    vat_amount numeric(12,2) DEFAULT 0 NOT NULL,
    vat_rate_percent numeric(6,2) DEFAULT 0 NOT NULL,
    vat_mode text DEFAULT ''::text NOT NULL,
    total_amount numeric(12,2) DEFAULT 0 NOT NULL,
    amount_paid numeric(12,2) DEFAULT 0 NOT NULL,
    balance_due numeric(12,2) DEFAULT 0 NOT NULL,
    payment_status text DEFAULT 'UNPAID'::text NOT NULL,
    payment_method text,
    payment_reference text,
    delivered_at timestamp with time zone,
    location_id integer,
    location_name text,
    device_id text,
    device_name text,
    cash_drawer_id bigint,
    cash_drawer_name text,
    cash_drawer_session_id bigint,
    created_by_user_id integer,
    created_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT invoices_amounts_chk CHECK (((subtotal_amount >= (0)::numeric) AND (discount_amount >= (0)::numeric) AND (vat_amount >= (0)::numeric) AND (total_amount >= (0)::numeric) AND (amount_paid >= (0)::numeric) AND (balance_due >= (0)::numeric))),
    CONSTRAINT invoices_payment_method_chk CHECK (((payment_method IS NULL) OR (payment_method = ANY (ARRAY['CASH'::text, 'CARD'::text, 'CHEQUE'::text, 'MMG'::text, 'ACCOUNT'::text])))),
    CONSTRAINT invoices_payment_status_chk CHECK ((payment_status = ANY (ARRAY['UNPAID'::text, 'PARTIAL'::text, 'PAID'::text]))),
    CONSTRAINT invoices_status_chk CHECK ((status = ANY (ARRAY['OPEN'::text, 'PARTIALLY_DELIVERED'::text, 'DELIVERED'::text, 'CANCELLED'::text]))),
    CONSTRAINT invoices_vat_rate_chk CHECK (((vat_rate_percent >= (0)::numeric) AND (vat_rate_percent <= (100)::numeric)))
);


--
-- Name: invoices_invoice_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.invoices_invoice_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: invoices_invoice_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.invoices_invoice_id_seq OWNED BY public.invoices.invoice_id;


--
-- Name: item_brands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.item_brands (
    brand_id integer NOT NULL,
    name text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: item_brands_brand_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.item_brands_brand_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: item_brands_brand_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.item_brands_brand_id_seq OWNED BY public.item_brands.brand_id;


--
-- Name: item_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.item_types (
    item_type_id integer NOT NULL,
    category_id integer NOT NULL,
    name text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: item_types_item_type_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.item_types_item_type_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: item_types_item_type_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.item_types_item_type_id_seq OWNED BY public.item_types.item_type_id;


--
-- Name: lan_api_approvals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lan_api_approvals (
    approval_id uuid DEFAULT gen_random_uuid() NOT NULL,
    approval_hash text NOT NULL,
    device_id uuid NOT NULL,
    requester_user_id integer NOT NULL,
    approver_user_id integer NOT NULL,
    location_id integer NOT NULL,
    permission_key text NOT NULL,
    action_key text NOT NULL,
    resource_hash text NOT NULL,
    issued_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    consumed_at timestamp with time zone
);


--
-- Name: lan_api_idempotency; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lan_api_idempotency (
    device_id uuid NOT NULL,
    idempotency_key text NOT NULL,
    operation_key text NOT NULL,
    request_hash text NOT NULL,
    response_status integer,
    response_body text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at timestamp with time zone
);


--
-- Name: lan_api_request_audit; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lan_api_request_audit (
    request_id uuid NOT NULL,
    device_id uuid,
    user_id integer,
    location_id integer,
    method text NOT NULL,
    route text NOT NULL,
    operation_key text,
    outcome text NOT NULL,
    status_code integer NOT NULL,
    source_address inet,
    details text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: lan_api_schedule_proposals; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lan_api_schedule_proposals (
    proposal_id uuid NOT NULL,
    device_id uuid NOT NULL,
    user_id integer NOT NULL,
    location_id integer NOT NULL,
    proposal_hash text NOT NULL,
    proposal_json text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp with time zone DEFAULT (CURRENT_TIMESTAMP + '00:30:00'::interval) NOT NULL,
    consumed_at timestamp with time zone
);


--
-- Name: lan_api_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.lan_api_sessions (
    session_id uuid DEFAULT gen_random_uuid() NOT NULL,
    session_hash text NOT NULL,
    device_id uuid NOT NULL,
    user_id integer NOT NULL,
    location_id integer NOT NULL,
    issued_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    absolute_expires_at timestamp with time zone NOT NULL,
    last_seen_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    revoked_at timestamp with time zone,
    auth_source text NOT NULL,
    CONSTRAINT lan_api_sessions_auth_source_check CHECK ((auth_source = ANY (ARRAY['SUPABASE'::text, 'SUPABASE_PASSWORD'::text, 'LOCAL_CACHE'::text, 'LOCAL_PASSWORD_CACHE'::text, 'BADGE_PIN'::text, 'EMPLOYEE_PIN'::text, 'BADGE_ONLY'::text, 'BADGE_PIN_SETUP'::text])))
);


--
-- Name: locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.locations (
    location_id integer NOT NULL,
    name text NOT NULL,
    address text,
    company_address_line1 text DEFAULT ''::text NOT NULL,
    company_address_line2 text DEFAULT ''::text NOT NULL,
    company_address_line3 text DEFAULT ''::text NOT NULL,
    company_phone_line1 text DEFAULT ''::text NOT NULL,
    company_phone_line2 text DEFAULT ''::text NOT NULL,
    company_email_line1 text DEFAULT ''::text NOT NULL,
    company_email_line2 text DEFAULT ''::text NOT NULL,
    receipt_store_code text DEFAULT '0001'::text NOT NULL,
    timezone text DEFAULT 'America/New_York'::text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    balance_sheet_recipient_email text DEFAULT ''::text NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    email_sender_address text DEFAULT ''::text NOT NULL,
    email_sender_name text DEFAULT ''::text NOT NULL,
    email_bcc_address text DEFAULT ''::text NOT NULL,
    email_receipts_enabled boolean DEFAULT false NOT NULL,
    email_order_confirmations_enabled boolean DEFAULT false NOT NULL,
    email_quotes_enabled boolean DEFAULT false NOT NULL,
    email_invoices_enabled boolean DEFAULT false NOT NULL,
    email_delivery_bills_enabled boolean DEFAULT false NOT NULL,
    email_connected_at timestamp with time zone,
    email_last_tested_at timestamp with time zone
);


--
-- Name: locations_location_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.locations_location_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: locations_location_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.locations_location_id_seq OWNED BY public.locations.location_id;


--
-- Name: login_security_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.login_security_state (
    identifier_hash text NOT NULL,
    failed_count integer DEFAULT 0 NOT NULL,
    window_started_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    locked_until timestamp with time zone,
    last_failed_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: maintenance_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maintenance_logs (
    log_id bigint NOT NULL,
    machine_id bigint NOT NULL,
    service_date date DEFAULT CURRENT_DATE NOT NULL,
    service_type text DEFAULT 'PREVENTIVE'::text NOT NULL,
    technician_name text,
    labor_hours numeric(10,2) DEFAULT 0 NOT NULL,
    total_cost numeric(12,2) DEFAULT 0 NOT NULL,
    summary text,
    details text,
    parts_used text,
    created_by_user_id integer,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT maintenance_logs_service_type_chk CHECK ((service_type = ANY (ARRAY['PREVENTIVE'::text, 'REPAIR'::text, 'INSPECTION'::text, 'CLEANING'::text, 'CALIBRATION'::text, 'OTHER'::text])))
);


--
-- Name: maintenance_logs_log_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.maintenance_logs_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: maintenance_logs_log_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.maintenance_logs_log_id_seq OWNED BY public.maintenance_logs.log_id;


--
-- Name: maintenance_machine_parts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maintenance_machine_parts (
    machine_part_id bigint NOT NULL,
    machine_id bigint NOT NULL,
    part_id bigint NOT NULL,
    notes text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: maintenance_machines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maintenance_machines (
    machine_id bigint NOT NULL,
    machine_name text NOT NULL,
    asset_tag text,
    serial_number text,
    manufacturer text,
    model text,
    machine_type text,
    location_id integer,
    location_name text,
    status text DEFAULT 'ACTIVE'::text NOT NULL,
    purchase_date date,
    warranty_expiration_date date,
    last_service_date date,
    next_service_date date,
    notes text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT maintenance_machines_status_chk CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'NEEDS_SERVICE'::text, 'DOWN'::text, 'RETIRED'::text])))
);


--
-- Name: maintenance_parts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maintenance_parts (
    part_id bigint NOT NULL,
    part_name text NOT NULL,
    part_number text,
    category text,
    quantity_on_hand numeric(12,2) DEFAULT 0 NOT NULL,
    reorder_point numeric(12,2) DEFAULT 0 NOT NULL,
    reorder_quantity numeric(12,2) DEFAULT 0 NOT NULL,
    unit_cost numeric(12,2) DEFAULT 0 NOT NULL,
    vendor_name text,
    bin_location text,
    is_active boolean DEFAULT true NOT NULL,
    notes text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: maintenance_machine_part_list; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.maintenance_machine_part_list WITH (security_invoker='true') AS
 SELECT m.machine_id,
    m.machine_name,
    m.asset_tag,
    COALESCE(l.name, m.location_name) AS location_name,
    p.part_id,
    p.part_name,
    p.part_number,
    mp.notes
   FROM (((public.maintenance_machine_parts mp
     JOIN public.maintenance_machines m ON ((m.machine_id = mp.machine_id)))
     LEFT JOIN public.locations l ON ((l.location_id = m.location_id)))
     JOIN public.maintenance_parts p ON ((p.part_id = mp.part_id)));


--
-- Name: maintenance_machine_parts_machine_part_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.maintenance_machine_parts_machine_part_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: maintenance_machine_parts_machine_part_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.maintenance_machine_parts_machine_part_id_seq OWNED BY public.maintenance_machine_parts.machine_part_id;


--
-- Name: maintenance_machines_machine_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.maintenance_machines_machine_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: maintenance_machines_machine_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.maintenance_machines_machine_id_seq OWNED BY public.maintenance_machines.machine_id;


--
-- Name: maintenance_tickets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maintenance_tickets (
    ticket_id bigint NOT NULL,
    machine_id bigint,
    opened_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    opened_by_user_id integer,
    priority text DEFAULT 'NORMAL'::text NOT NULL,
    status text DEFAULT 'OPEN'::text NOT NULL,
    assigned_to_name text,
    due_date date,
    problem_summary text NOT NULL,
    resolution_summary text,
    notes text,
    resolved_at timestamp with time zone,
    closed_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT maintenance_tickets_priority_chk CHECK ((priority = ANY (ARRAY['LOW'::text, 'NORMAL'::text, 'HIGH'::text, 'URGENT'::text]))),
    CONSTRAINT maintenance_tickets_status_chk CHECK ((status = ANY (ARRAY['OPEN'::text, 'IN_PROGRESS'::text, 'WAITING_PARTS'::text, 'RESOLVED'::text, 'CLOSED'::text, 'CANCELED'::text])))
);


--
-- Name: maintenance_open_ticket_summary; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.maintenance_open_ticket_summary WITH (security_invoker='true') AS
 SELECT t.ticket_id,
    t.opened_at,
    t.priority,
    t.status,
    t.due_date,
    t.problem_summary,
    m.machine_name,
    m.asset_tag,
    COALESCE(l.name, m.location_name) AS location_name,
    t.assigned_to_name
   FROM ((public.maintenance_tickets t
     LEFT JOIN public.maintenance_machines m ON ((m.machine_id = t.machine_id)))
     LEFT JOIN public.locations l ON ((l.location_id = m.location_id)))
  WHERE (t.status = ANY (ARRAY['OPEN'::text, 'IN_PROGRESS'::text, 'WAITING_PARTS'::text]));


--
-- Name: maintenance_parts_part_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.maintenance_parts_part_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: maintenance_parts_part_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.maintenance_parts_part_id_seq OWNED BY public.maintenance_parts.part_id;


--
-- Name: maintenance_parts_to_reorder; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.maintenance_parts_to_reorder WITH (security_invoker='true') AS
 SELECT part_id,
    part_name,
    part_number,
    quantity_on_hand,
    reorder_point,
    reorder_quantity,
    vendor_name,
    bin_location
   FROM public.maintenance_parts
  WHERE ((is_active = true) AND (quantity_on_hand <= reorder_point));


--
-- Name: maintenance_ticket_notes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maintenance_ticket_notes (
    note_id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    note_text text NOT NULL,
    created_by_user_id integer,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: maintenance_ticket_notes_note_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.maintenance_ticket_notes_note_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: maintenance_ticket_notes_note_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.maintenance_ticket_notes_note_id_seq OWNED BY public.maintenance_ticket_notes.note_id;


--
-- Name: maintenance_ticket_parts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.maintenance_ticket_parts (
    ticket_part_id bigint NOT NULL,
    ticket_id bigint NOT NULL,
    part_id bigint NOT NULL,
    quantity_used numeric(12,2) DEFAULT 1 NOT NULL,
    unit_cost numeric(12,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: maintenance_ticket_parts_ticket_part_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.maintenance_ticket_parts_ticket_part_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: maintenance_ticket_parts_ticket_part_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.maintenance_ticket_parts_ticket_part_id_seq OWNED BY public.maintenance_ticket_parts.ticket_part_id;


--
-- Name: maintenance_tickets_ticket_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.maintenance_tickets_ticket_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: maintenance_tickets_ticket_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.maintenance_tickets_ticket_id_seq OWNED BY public.maintenance_tickets.ticket_id;


--
-- Name: mobile_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.mobile_permissions (
    permission_key text NOT NULL,
    permission_name text,
    description text,
    permission_group text,
    permission_subgroup text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: notification_user_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.notification_user_state (
    user_id integer NOT NULL,
    notification_key text NOT NULL,
    read_at timestamp with time zone,
    snoozed_until timestamp with time zone,
    dismissed_at timestamp with time zone,
    dismissed_until timestamp with time zone,
    last_seen_at timestamp with time zone,
    last_seen_severity text,
    last_seen_source text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: other_income_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.other_income_entries (
    other_income_id bigint NOT NULL,
    location_id integer,
    income_date date DEFAULT CURRENT_DATE NOT NULL,
    source_name text NOT NULL,
    description text,
    amount numeric(12,2) NOT NULL,
    payment_method text DEFAULT 'CASH'::text NOT NULL,
    payment_reference text,
    created_by_user_id integer,
    created_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT other_income_amount_chk CHECK ((amount > (0)::numeric)),
    CONSTRAINT other_income_payment_method_chk CHECK ((payment_method = 'CASH'::text)),
    CONSTRAINT other_income_whole_gyd_chk CHECK ((amount = trunc(amount)))
);


--
-- Name: other_income_entries_other_income_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.other_income_entries_other_income_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: other_income_entries_other_income_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.other_income_entries_other_income_id_seq OWNED BY public.other_income_entries.other_income_id;


--
-- Name: payroll_payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.payroll_payments (
    payroll_payment_id bigint NOT NULL,
    user_id integer NOT NULL,
    employee_name text,
    employee_role text,
    location_id integer,
    pay_period_start date NOT NULL,
    pay_period_end date NOT NULL,
    payment_number integer DEFAULT 1 NOT NULL,
    pay_date date,
    days_worked integer DEFAULT 0 NOT NULL,
    total_hours numeric(10,2) DEFAULT 0 NOT NULL,
    pay_period_type text DEFAULT 'SEMI_MONTHLY'::text NOT NULL,
    work_hour_limit numeric(8,2) DEFAULT 80.00 NOT NULL,
    regular_hours numeric(10,2) DEFAULT 0 NOT NULL,
    overtime_hours numeric(10,2) DEFAULT 0 NOT NULL,
    regular_pay numeric(12,2) DEFAULT 0 NOT NULL,
    overtime_pay numeric(12,2) DEFAULT 0 NOT NULL,
    total_pay numeric(12,2) DEFAULT 0 NOT NULL,
    record_count integer DEFAULT 0 NOT NULL,
    compensation_type text,
    location_name text,
    payment_method text DEFAULT 'CASH'::text NOT NULL,
    payment_reference text,
    paid_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    paid_by_user_id integer,
    paid_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: payroll_payments_payroll_payment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.payroll_payments_payroll_payment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: payroll_payments_payroll_payment_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.payroll_payments_payroll_payment_id_seq OWNED BY public.payroll_payments.payroll_payment_id;


--
-- Name: permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.permissions (
    permission_id integer NOT NULL,
    permission_key text NOT NULL,
    permission_name text,
    description text,
    permission_group text,
    permission_subgroup text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: permissions_permission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.permissions_permission_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: permissions_permission_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.permissions_permission_id_seq OWNED BY public.permissions.permission_id;


--
-- Name: product_barcodes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_barcodes (
    product_barcode_id integer NOT NULL,
    product_id integer NOT NULL,
    barcode text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: product_barcodes_product_barcode_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.product_barcodes ALTER COLUMN product_barcode_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.product_barcodes_product_barcode_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: product_shelf_assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.product_shelf_assignments (
    product_id integer NOT NULL,
    location_id integer NOT NULL,
    shelf_location_id integer NOT NULL,
    storage_shelf_location_id integer,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: products; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.products (
    product_id integer NOT NULL,
    name text NOT NULL,
    size text,
    sku text NOT NULL,
    barcode text,
    description text,
    cost_price numeric(12,2) DEFAULT 0 NOT NULL,
    price numeric(12,2) DEFAULT 0 NOT NULL,
    product_type text DEFAULT 'INVENTORY'::text NOT NULL,
    category_id integer,
    vendor_id integer,
    image_url text,
    created_by_user_id integer,
    created_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    item_type_id integer,
    brand_id integer,
    CONSTRAINT products_product_type_check CHECK ((product_type = ANY (ARRAY['INVENTORY'::text, 'SERVICE'::text, 'NON_INVENTORY'::text])))
);


--
-- Name: products_product_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.products_product_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: products_product_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.products_product_id_seq OWNED BY public.products.product_id;


--
-- Name: quotation_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quotation_audit_log (
    quotation_audit_id bigint NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    quotation_id bigint NOT NULL,
    action_type text NOT NULL,
    field_name text,
    old_value text,
    new_value text,
    reason text,
    user_id integer,
    user_name text,
    device_id text,
    device_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: quotation_audit_log_quotation_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.quotation_audit_log_quotation_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: quotation_audit_log_quotation_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.quotation_audit_log_quotation_audit_id_seq OWNED BY public.quotation_audit_log.quotation_audit_id;


--
-- Name: quotation_lines; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quotation_lines (
    quotation_line_id bigint NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    quotation_id bigint NOT NULL,
    product_id integer,
    item_name text NOT NULL,
    sku text,
    quantity integer DEFAULT 1 NOT NULL,
    unit_price numeric(12,2) DEFAULT 0 NOT NULL,
    original_unit_price numeric(12,2),
    price_override_reason text,
    price_override_by_user_id integer,
    price_override_by_name text,
    category_id integer,
    discount_percent numeric(7,4) DEFAULT 0 NOT NULL,
    discount_amount numeric(12,2) DEFAULT 0 NOT NULL,
    vat_rate_percent numeric(6,2) DEFAULT 0 NOT NULL,
    vat_amount numeric(12,2) DEFAULT 0 NOT NULL,
    line_total numeric(12,2) DEFAULT 0 NOT NULL,
    delivery_method text DEFAULT 'PICKUP'::text NOT NULL,
    line_notes text,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT quotation_lines_amounts_chk CHECK (((unit_price >= (0)::numeric) AND (discount_amount >= (0)::numeric) AND (line_total >= (0)::numeric))),
    CONSTRAINT quotation_lines_delivery_method_chk CHECK ((delivery_method = ANY (ARRAY['PICKUP'::text, 'LOCAL_DELIVERY'::text, 'SHIP'::text, 'INSTALLATION'::text]))),
    CONSTRAINT quotation_lines_discount_percent_chk CHECK (((discount_percent >= (0)::numeric) AND (discount_percent <= (100)::numeric))),
    CONSTRAINT quotation_lines_qty_chk CHECK ((quantity > 0)),
    CONSTRAINT quotation_lines_vat_chk CHECK (((vat_rate_percent >= (0)::numeric) AND (vat_rate_percent <= (100)::numeric) AND (vat_amount >= (0)::numeric)))
);


--
-- Name: quotation_lines_quotation_line_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.quotation_lines_quotation_line_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: quotation_lines_quotation_line_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.quotation_lines_quotation_line_id_seq OWNED BY public.quotation_lines.quotation_line_id;


--
-- Name: quotation_status_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quotation_status_history (
    quotation_status_history_id bigint NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    quotation_id bigint NOT NULL,
    old_status text,
    new_status text NOT NULL,
    reason text,
    user_id integer,
    user_name text,
    device_id text,
    device_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: quotation_status_history_quotation_status_history_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.quotation_status_history_quotation_status_history_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: quotation_status_history_quotation_status_history_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.quotation_status_history_quotation_status_history_id_seq OWNED BY public.quotation_status_history.quotation_status_history_id;


--
-- Name: quotations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.quotations (
    quotation_id bigint NOT NULL,
    quotation_number text NOT NULL,
    customer_id integer NOT NULL,
    customer_name text NOT NULL,
    customer_phone text,
    customer_email text,
    status text DEFAULT 'DRAFT'::text NOT NULL,
    issue_date date DEFAULT CURRENT_DATE NOT NULL,
    valid_until date DEFAULT (CURRENT_DATE + 30) NOT NULL,
    quotation_notes text,
    subtotal_amount numeric(12,2) DEFAULT 0 NOT NULL,
    discount_amount numeric(12,2) DEFAULT 0 NOT NULL,
    vat_amount numeric(12,2) DEFAULT 0 NOT NULL,
    vat_rate_percent numeric(6,2) DEFAULT 0 NOT NULL,
    vat_mode text DEFAULT ''::text NOT NULL,
    total_amount numeric(12,2) DEFAULT 0 NOT NULL,
    accepted_at timestamp with time zone,
    accepted_by_user_id integer,
    accepted_by_name text,
    superseded_by_quotation_id bigint,
    location_id integer,
    location_name text,
    device_id text,
    device_name text,
    created_by_user_id integer,
    created_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT quotations_amounts_chk CHECK (((subtotal_amount >= (0)::numeric) AND (discount_amount >= (0)::numeric) AND (vat_amount >= (0)::numeric) AND (total_amount >= (0)::numeric))),
    CONSTRAINT quotations_status_chk CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ISSUED'::text, 'ACCEPTED'::text, 'EXPIRED'::text, 'CANCELLED'::text, 'SUPERSEDED'::text]))),
    CONSTRAINT quotations_vat_rate_chk CHECK (((vat_rate_percent >= (0)::numeric) AND (vat_rate_percent <= (100)::numeric)))
);


--
-- Name: quotations_quotation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.quotations_quotation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: quotations_quotation_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.quotations_quotation_id_seq OWNED BY public.quotations.quotation_id;


--
-- Name: receiving_batches; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.receiving_batches (
    receive_id text NOT NULL,
    location_id integer,
    user_id integer,
    user_name text,
    receive_device_id text,
    receive_sequence integer,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: remote_admin_commands; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.remote_admin_commands (
    command_id uuid NOT NULL,
    location_id integer NOT NULL,
    device_id uuid,
    user_id integer,
    operation text NOT NULL,
    status text NOT NULL,
    details text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    applied_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT remote_admin_commands_status_check CHECK ((status = ANY (ARRAY['APPLIED_CLOUD'::text, 'PENDING_STORE'::text, 'APPLIED_STORE'::text, 'REJECTED'::text, 'CONFLICT'::text])))
);


--
-- Name: role_mobile_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_mobile_permissions (
    role_id integer NOT NULL,
    permission_key text NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: role_permissions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.role_permissions (
    role_id integer NOT NULL,
    permission_id integer NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.roles (
    role_id integer NOT NULL,
    role_name text NOT NULL,
    description text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: roles_role_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.roles_role_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: roles_role_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.roles_role_id_seq OWNED BY public.roles.role_id;


--
-- Name: sale_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sale_audit_log (
    sale_audit_id bigint NOT NULL,
    sale_id integer,
    sale_item_id integer,
    return_id bigint,
    return_item_id bigint,
    customer_id integer,
    product_id integer,
    location_id integer,
    action_type text NOT NULL,
    action_scope text DEFAULT 'SALE'::text NOT NULL,
    field_name text,
    old_value text,
    new_value text,
    amount numeric(12,2),
    quantity integer,
    reason text,
    note text,
    user_id integer,
    user_name text,
    device_id text,
    device_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: sale_audit_log_sale_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sale_audit_log_sale_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sale_audit_log_sale_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sale_audit_log_sale_audit_id_seq OWNED BY public.sale_audit_log.sale_audit_id;


--
-- Name: sale_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sale_items (
    sale_item_id integer NOT NULL,
    sale_id integer NOT NULL,
    product_id integer NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    unit_price numeric(12,2) DEFAULT 0 NOT NULL,
    original_unit_price numeric(12,2) DEFAULT 0 NOT NULL,
    discount_percent numeric(6,2) DEFAULT 0 NOT NULL,
    discount_amount numeric(12,2) DEFAULT 0 NOT NULL,
    price_override_reason text,
    price_override_by_user_id integer,
    price_override_by_name text,
    product_type text DEFAULT 'INVENTORY'::text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT sale_items_product_type_check CHECK ((product_type = ANY (ARRAY['INVENTORY'::text, 'SERVICE'::text, 'NON_INVENTORY'::text])))
);


--
-- Name: sale_items_sale_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sale_items_sale_item_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sale_items_sale_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sale_items_sale_item_id_seq OWNED BY public.sale_items.sale_item_id;


--
-- Name: sale_return_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sale_return_items (
    return_item_id bigint NOT NULL,
    return_id bigint NOT NULL,
    sale_item_id integer NOT NULL,
    product_id integer NOT NULL,
    quantity integer NOT NULL,
    unit_price numeric(12,2) DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT sale_return_items_quantity_check CHECK ((quantity > 0))
);


--
-- Name: sale_return_items_return_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sale_return_items_return_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sale_return_items_return_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sale_return_items_return_item_id_seq OWNED BY public.sale_return_items.return_item_id;


--
-- Name: sale_returns; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sale_returns (
    return_id bigint NOT NULL,
    sale_id integer NOT NULL,
    location_id integer NOT NULL,
    user_id integer,
    user_name text,
    refund_method text,
    refund_amount numeric(12,2) DEFAULT 0 NOT NULL,
    reason text,
    device_id text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    cross_store_request_id uuid,
    receiving_location_id integer,
    device_name text,
    override_reason text,
    override_by_user_id integer,
    override_by_name text,
    cash_drawer_id bigint,
    cash_drawer_name text,
    cash_drawer_session_id bigint,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: sale_returns_return_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sale_returns_return_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sale_returns_return_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sale_returns_return_id_seq OWNED BY public.sale_returns.return_id;


--
-- Name: sales; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sales (
    sale_id integer NOT NULL,
    location_id integer NOT NULL,
    user_id integer,
    customer_id integer,
    total_amount numeric(12,2) DEFAULT 0 NOT NULL,
    status text DEFAULT 'COMPLETED'::text NOT NULL,
    payment_method text DEFAULT 'CASH'::text NOT NULL,
    payment_status text DEFAULT 'PAID'::text NOT NULL,
    amount_paid numeric(12,2) DEFAULT 0 NOT NULL,
    user_name text,
    receipt_number text,
    receipt_device_id text,
    receipt_sequence integer,
    subtotal_amount numeric(12,2) DEFAULT 0 NOT NULL,
    discount_percent numeric(6,2) DEFAULT 0 NOT NULL,
    discount_amount numeric(12,2) DEFAULT 0 NOT NULL,
    vat_amount numeric(12,2) DEFAULT 0 NOT NULL,
    vat_rate_percent numeric(6,2) DEFAULT 0 NOT NULL,
    vat_mode text DEFAULT ''::text NOT NULL,
    payment_reference text,
    transaction_source text,
    device_id text,
    completed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    returned_amount numeric(12,2) DEFAULT 0 NOT NULL,
    device_name text,
    discount_override_reason text,
    discount_override_by_user_id integer,
    discount_override_by_name text,
    cash_drawer_id bigint,
    cash_drawer_name text,
    cash_drawer_session_id bigint,
    sync_uuid uuid DEFAULT gen_random_uuid() NOT NULL,
    CONSTRAINT sales_vat_amount_chk CHECK ((vat_amount >= (0)::numeric)),
    CONSTRAINT sales_vat_rate_percent_chk CHECK (((vat_rate_percent >= (0)::numeric) AND (vat_rate_percent <= (100)::numeric)))
);


--
-- Name: sales_sale_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sales_sale_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sales_sale_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sales_sale_id_seq OWNED BY public.sales.sale_id;


--
-- Name: security_audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.security_audit_events (
    event_id bigint NOT NULL,
    event_type text NOT NULL,
    device_id uuid,
    actor_user_id integer,
    details text,
    source_address inet DEFAULT inet_client_addr(),
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: security_audit_events_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.security_audit_events_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: security_audit_events_event_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.security_audit_events_event_id_seq OWNED BY public.security_audit_events.event_id;


--
-- Name: shelf_locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.shelf_locations (
    shelf_location_id integer NOT NULL,
    location_id integer NOT NULL,
    name text NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: shelf_locations_shelf_location_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.shelf_locations_shelf_location_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: shelf_locations_shelf_location_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.shelf_locations_shelf_location_id_seq OWNED BY public.shelf_locations.shelf_location_id;


--
-- Name: store_sync_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_sync_status (
    location_id integer NOT NULL,
    status text NOT NULL,
    message text,
    last_success_at timestamp with time zone,
    last_seen_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: store_transfer_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_transfer_items (
    transfer_item_id bigint NOT NULL,
    transfer_id bigint NOT NULL,
    product_id integer NOT NULL,
    quantity integer NOT NULL,
    CONSTRAINT store_transfer_items_quantity_check CHECK ((quantity > 0))
);


--
-- Name: store_transfer_items_transfer_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.store_transfer_items_transfer_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: store_transfer_items_transfer_item_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.store_transfer_items_transfer_item_id_seq OWNED BY public.store_transfer_items.transfer_item_id;


--
-- Name: store_transfers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.store_transfers (
    transfer_id bigint NOT NULL,
    from_location_id integer NOT NULL,
    to_location_id integer NOT NULL,
    user_id integer,
    user_name text,
    status text DEFAULT 'PENDING'::text NOT NULL,
    note text,
    received_at timestamp with time zone,
    received_by_user_id integer,
    received_by_name text,
    receive_id text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT store_transfers_different_locations CHECK ((from_location_id <> to_location_id))
);


--
-- Name: store_transfers_transfer_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.store_transfers_transfer_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: store_transfers_transfer_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.store_transfers_transfer_id_seq OWNED BY public.store_transfers.transfer_id;


--
-- Name: sync_applied_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_applied_events (
    origin_event_id uuid NOT NULL,
    event_type text NOT NULL,
    origin_location_id integer,
    origin_device_id text,
    applied_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    cloud_reference text
);


--
-- Name: sync_audit_log; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_audit_log (
    sync_audit_id bigint NOT NULL,
    action_type text NOT NULL,
    table_name text,
    local_id_before text,
    local_id_after text,
    cloud_id text,
    match_key text,
    status text DEFAULT 'INFO'::text NOT NULL,
    details jsonb DEFAULT '{}'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_audit_log_sync_audit_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sync_audit_log_sync_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sync_audit_log_sync_audit_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sync_audit_log_sync_audit_id_seq OWNED BY public.sync_audit_log.sync_audit_id;


--
-- Name: sync_cloud_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_cloud_state (
    state_id text NOT NULL,
    cursor_value bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_conflicts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_conflicts (
    conflict_id bigint NOT NULL,
    origin_event_id uuid,
    event_type text,
    table_name text,
    local_id text,
    conflict_type text NOT NULL,
    local_payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    cloud_payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    status text DEFAULT 'OPEN'::text NOT NULL,
    resolution_notes text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at timestamp with time zone,
    resolved_by_user_id integer,
    resolved_by_name text
);


--
-- Name: sync_conflicts_conflict_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sync_conflicts_conflict_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sync_conflicts_conflict_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sync_conflicts_conflict_id_seq OWNED BY public.sync_conflicts.conflict_id;


--
-- Name: sync_cross_store_inventory_cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_cross_store_inventory_cache (
    source_location_id integer NOT NULL,
    product_id integer NOT NULL,
    store_name text NOT NULL,
    sku text,
    barcode text,
    additional_barcodes text,
    product_name text NOT NULL,
    size text,
    description text,
    quantity_on_hand integer DEFAULT 0 NOT NULL,
    reorder_level integer DEFAULT 0 NOT NULL,
    source_updated_at timestamp with time zone,
    cache_refreshed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_cross_store_inventory_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_cross_store_inventory_status (
    source_location_id integer NOT NULL,
    store_name text NOT NULL,
    row_count integer DEFAULT 0 NOT NULL,
    status text NOT NULL,
    last_error text,
    refreshed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_cross_store_return_items_cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_cross_store_return_items_cache (
    source_location_id integer NOT NULL,
    return_item_id bigint NOT NULL,
    return_id bigint NOT NULL,
    sale_item_id integer NOT NULL,
    product_id integer NOT NULL,
    quantity integer DEFAULT 0 NOT NULL,
    unit_price numeric(14,2) DEFAULT 0 NOT NULL
);


--
-- Name: sync_cross_store_returns_cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_cross_store_returns_cache (
    source_location_id integer NOT NULL,
    return_id bigint NOT NULL,
    sale_id integer NOT NULL,
    user_name text,
    refund_method text,
    refund_amount numeric(14,2) DEFAULT 0 NOT NULL,
    reason text,
    source_created_at timestamp with time zone
);


--
-- Name: sync_cross_store_sale_items_cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_cross_store_sale_items_cache (
    source_location_id integer NOT NULL,
    sale_id integer NOT NULL,
    sale_item_id integer NOT NULL,
    product_id integer NOT NULL,
    sku text,
    product_name text NOT NULL,
    product_type text DEFAULT 'INVENTORY'::text NOT NULL,
    quantity integer DEFAULT 0 NOT NULL,
    unit_price numeric(14,2) DEFAULT 0 NOT NULL,
    original_unit_price numeric(14,2) DEFAULT 0 NOT NULL,
    discount_percent numeric(8,4) DEFAULT 0 NOT NULL,
    discount_amount numeric(14,2) DEFAULT 0 NOT NULL
);


--
-- Name: sync_cross_store_sales_cache; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_cross_store_sales_cache (
    source_location_id integer NOT NULL,
    sale_id integer NOT NULL,
    store_name text NOT NULL,
    receipt_number text,
    customer_id integer,
    user_name text,
    payment_method text,
    payment_status text,
    subtotal_amount numeric(14,2) DEFAULT 0 NOT NULL,
    discount_percent numeric(8,4) DEFAULT 0 NOT NULL,
    discount_amount numeric(14,2) DEFAULT 0 NOT NULL,
    total_amount numeric(14,2) DEFAULT 0 NOT NULL,
    amount_paid numeric(14,2) DEFAULT 0 NOT NULL,
    returned_amount numeric(14,2) DEFAULT 0 NOT NULL,
    source_created_at timestamp with time zone,
    source_updated_at timestamp with time zone,
    cache_refreshed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    cache_status text DEFAULT 'CURRENT'::text NOT NULL
);


--
-- Name: sync_cross_store_sales_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_cross_store_sales_status (
    source_location_id integer NOT NULL,
    store_name text NOT NULL,
    row_count integer DEFAULT 0 NOT NULL,
    status text NOT NULL,
    last_error text,
    refreshed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_id_map; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_id_map (
    id_map_id bigint NOT NULL,
    origin_event_id uuid,
    table_name text NOT NULL,
    local_id text NOT NULL,
    cloud_id text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_id_map_id_map_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sync_id_map_id_map_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sync_id_map_id_map_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sync_id_map_id_map_id_seq OWNED BY public.sync_id_map.id_map_id;


--
-- Name: sync_inbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_inbox (
    cloud_sequence bigint NOT NULL,
    event_id uuid NOT NULL,
    event_type text NOT NULL,
    location_id integer,
    device_id text,
    user_id integer,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    origin_location_id integer,
    origin_device_id text,
    origin_created_at timestamp with time zone,
    status text DEFAULT 'RECEIVED'::text NOT NULL,
    received_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    applied_at timestamp with time zone,
    last_error text
);


--
-- Name: sync_locks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_locks (
    lock_name text NOT NULL,
    owner_id text NOT NULL,
    owner_label text NOT NULL,
    acquired_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    heartbeat_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_outbox; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_outbox (
    event_id uuid DEFAULT gen_random_uuid() NOT NULL,
    event_type text NOT NULL,
    location_id integer,
    device_id text,
    user_id integer,
    payload jsonb DEFAULT '{}'::jsonb NOT NULL,
    status text DEFAULT 'PENDING'::text NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    last_error text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    synced_at timestamp with time zone,
    origin_event_id uuid,
    origin_location_id integer,
    origin_device_id text,
    origin_created_at timestamp with time zone
);


--
-- Name: sync_row_mirror_completion; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_row_mirror_completion (
    location_id integer NOT NULL,
    table_counts jsonb NOT NULL,
    active_row_count bigint NOT NULL,
    generation_id uuid,
    completed_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_row_mirror_state; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_row_mirror_state (
    location_id integer NOT NULL,
    table_name text NOT NULL,
    row_key text NOT NULL,
    row_hash text NOT NULL,
    materialized_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_service_status; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_service_status (
    service_id text NOT NULL,
    status text NOT NULL,
    message text,
    started_at timestamp with time zone,
    last_seen_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_tombstones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_tombstones (
    tombstone_id uuid DEFAULT gen_random_uuid() NOT NULL,
    table_name text NOT NULL,
    key_data jsonb DEFAULT '{}'::jsonb NOT NULL,
    deleted_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    origin_device_id text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_transfer_metrics; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.sync_transfer_metrics (
    metric_id bigint NOT NULL,
    operation text NOT NULL,
    request_bytes bigint DEFAULT 0 NOT NULL,
    response_bytes bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: sync_transfer_metrics_metric_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.sync_transfer_metrics_metric_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: sync_transfer_metrics_metric_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.sync_transfer_metrics_metric_id_seq OWNED BY public.sync_transfer_metrics.metric_id;


--
-- Name: time_clock_auto_close_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.time_clock_auto_close_settings (
    settings_id uuid NOT NULL,
    auto_close_enabled boolean DEFAULT true NOT NULL,
    scheduled_detection_delay_hours integer DEFAULT 4 CONSTRAINT time_clock_auto_close_setti_scheduled_detection_delay__not_null NOT NULL,
    unscheduled_detection_hours integer DEFAULT 12 CONSTRAINT time_clock_auto_close_setti_unscheduled_detection_hour_not_null NOT NULL,
    max_auto_work_hours integer DEFAULT 8 NOT NULL,
    updated_by_user_id integer,
    updated_by_name text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT time_clock_auto_close_max_work_chk CHECK (((max_auto_work_hours >= 1) AND (max_auto_work_hours <= 24))),
    CONSTRAINT time_clock_auto_close_scheduled_delay_chk CHECK (((scheduled_detection_delay_hours >= 0) AND (scheduled_detection_delay_hours <= 24))),
    CONSTRAINT time_clock_auto_close_threshold_order_chk CHECK ((unscheduled_detection_hours >= max_auto_work_hours)),
    CONSTRAINT time_clock_auto_close_unscheduled_chk CHECK (((unscheduled_detection_hours >= 1) AND (unscheduled_detection_hours <= 48)))
);


--
-- Name: user_locations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_locations (
    user_id integer NOT NULL,
    location_id integer NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    user_id integer NOT NULL,
    username text NOT NULL,
    password_hash text,
    first_name text,
    middle_name text,
    last_name text,
    full_name text NOT NULL,
    nickname text,
    email text,
    phone text,
    employee_photo_url text,
    employee_id_card_document_url text,
    date_of_birth date,
    hire_date date DEFAULT CURRENT_DATE NOT NULL,
    badge_id text,
    badge_secret_salt text,
    badge_secret_hash text,
    badge_generated_at timestamp with time zone,
    badge_print_count integer DEFAULT 0 NOT NULL,
    badge_rotated_at timestamp with time zone,
    badge_rotated_by_user_id integer,
    badge_rotated_by_name text,
    compensation_type public.compensation_type_enum DEFAULT 'HOURLY'::public.compensation_type_enum NOT NULL,
    salary numeric(12,2) DEFAULT 0 NOT NULL,
    role_id integer,
    auth_user_id uuid,
    is_active boolean DEFAULT true NOT NULL,
    deactivated_at timestamp with time zone,
    deactivated_by_user_id integer,
    deactivated_by_name text,
    password_cache_invalidated_at timestamp with time zone,
    employee_pin_salt text,
    employee_pin_hash text,
    employee_pin_updated_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: users_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_user_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_user_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_user_id_seq OWNED BY public.users.user_id;


--
-- Name: vendors; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.vendors (
    vendor_id integer NOT NULL,
    name text NOT NULL,
    contact_name text,
    phone text,
    email text,
    address text,
    notes text,
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: vendors_vendor_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.vendors_vendor_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: vendors_vendor_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.vendors_vendor_id_seq OWNED BY public.vendors.vendor_id;


--
-- Name: app_releases release_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_releases ALTER COLUMN release_id SET DEFAULT nextval('public.app_releases_release_id_seq'::regclass);


--
-- Name: balance_sheet_bf_overrides balance_sheet_bf_override_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_bf_overrides ALTER COLUMN balance_sheet_bf_override_id SET DEFAULT nextval('public.balance_sheet_bf_overrides_balance_sheet_bf_override_id_seq'::regclass);


--
-- Name: balance_sheet_submission_revisions balance_sheet_revision_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_submission_revisions ALTER COLUMN balance_sheet_revision_id SET DEFAULT nextval('public.balance_sheet_submission_revision_balance_sheet_revision_id_seq'::regclass);


--
-- Name: balance_sheet_submissions balance_sheet_submission_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_submissions ALTER COLUMN balance_sheet_submission_id SET DEFAULT nextval('public.balance_sheet_submissions_balance_sheet_submission_id_seq'::regclass);


--
-- Name: bank_transactions bank_transaction_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_transactions ALTER COLUMN bank_transaction_id SET DEFAULT nextval('public.bank_transactions_bank_transaction_id_seq'::regclass);


--
-- Name: cash_drawer_device_assignments assignment_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_device_assignments ALTER COLUMN assignment_id SET DEFAULT nextval('public.cash_drawer_device_assignments_assignment_id_seq'::regclass);


--
-- Name: cash_drawer_handovers cash_drawer_handover_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_handovers ALTER COLUMN cash_drawer_handover_id SET DEFAULT nextval('public.cash_drawer_handovers_cash_drawer_handover_id_seq'::regclass);


--
-- Name: cash_drawer_sessions cash_drawer_session_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_sessions ALTER COLUMN cash_drawer_session_id SET DEFAULT nextval('public.cash_drawer_sessions_cash_drawer_session_id_seq'::regclass);


--
-- Name: cash_drawers cash_drawer_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawers ALTER COLUMN cash_drawer_id SET DEFAULT nextval('public.cash_drawers_cash_drawer_id_seq'::regclass);


--
-- Name: categories category_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories ALTER COLUMN category_id SET DEFAULT nextval('public.categories_category_id_seq'::regclass);


--
-- Name: change_basket_updates change_basket_update_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.change_basket_updates ALTER COLUMN change_basket_update_id SET DEFAULT nextval('public.change_basket_updates_change_basket_update_id_seq'::regclass);


--
-- Name: cheque_bank_deposits cheque_bank_deposit_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cheque_bank_deposits ALTER COLUMN cheque_bank_deposit_id SET DEFAULT nextval('public.cheque_bank_deposits_cheque_bank_deposit_id_seq'::regclass);


--
-- Name: company_customization customization_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_customization ALTER COLUMN customization_id SET DEFAULT nextval('public.company_customization_customization_id_seq'::regclass);


--
-- Name: cross_store_refund_reconciliation reconciliation_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cross_store_refund_reconciliation ALTER COLUMN reconciliation_id SET DEFAULT nextval('public.cross_store_refund_reconciliation_reconciliation_id_seq'::regclass);


--
-- Name: custom_order_audit_log custom_order_audit_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_audit_log ALTER COLUMN custom_order_audit_id SET DEFAULT nextval('public.custom_order_audit_log_custom_order_audit_id_seq'::regclass);


--
-- Name: custom_order_design_placements design_placement_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_design_placements ALTER COLUMN design_placement_id SET DEFAULT nextval('public.custom_order_design_placements_design_placement_id_seq'::regclass);


--
-- Name: custom_order_inventory_reservations custom_order_inventory_reservation_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_inventory_reservations ALTER COLUMN custom_order_inventory_reservation_id SET DEFAULT nextval('public.custom_order_inventory_reserv_custom_order_inventory_reserv_seq'::regclass);


--
-- Name: custom_order_item_barcodes custom_item_barcode_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_barcodes ALTER COLUMN custom_item_barcode_id SET DEFAULT nextval('public.custom_order_item_barcodes_custom_item_barcode_id_seq'::regclass);


--
-- Name: custom_order_item_movements movement_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_movements ALTER COLUMN movement_id SET DEFAULT nextval('public.custom_order_item_movements_movement_id_seq'::regclass);


--
-- Name: custom_order_item_variants custom_variant_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_variants ALTER COLUMN custom_variant_id SET DEFAULT nextval('public.custom_order_item_variants_custom_variant_id_seq'::regclass);


--
-- Name: custom_order_items custom_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_items ALTER COLUMN custom_item_id SET DEFAULT nextval('public.custom_order_items_custom_item_id_seq'::regclass);


--
-- Name: custom_order_line_deliveries custom_order_line_delivery_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_deliveries ALTER COLUMN custom_order_line_delivery_id SET DEFAULT nextval('public.custom_order_line_deliveries_custom_order_line_delivery_id_seq'::regclass);


--
-- Name: custom_order_line_print_addons custom_order_line_print_addon_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_print_addons ALTER COLUMN custom_order_line_print_addon_id SET DEFAULT nextval('public.custom_order_line_print_addon_custom_order_line_print_addon_seq'::regclass);


--
-- Name: custom_order_line_production_history custom_order_line_production_history_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_production_history ALTER COLUMN custom_order_line_production_history_id SET DEFAULT nextval('public.custom_order_line_production__custom_order_line_production__seq'::regclass);


--
-- Name: custom_order_line_returns custom_order_line_return_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_returns ALTER COLUMN custom_order_line_return_id SET DEFAULT nextval('public.custom_order_line_returns_custom_order_line_return_id_seq'::regclass);


--
-- Name: custom_order_lines custom_order_line_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_lines ALTER COLUMN custom_order_line_id SET DEFAULT nextval('public.custom_order_lines_custom_order_line_id_seq'::regclass);


--
-- Name: custom_order_payments custom_order_payment_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_payments ALTER COLUMN custom_order_payment_id SET DEFAULT nextval('public.custom_order_payments_custom_order_payment_id_seq'::regclass);


--
-- Name: custom_order_print_materials print_material_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_print_materials ALTER COLUMN print_material_id SET DEFAULT nextval('public.custom_order_print_materials_print_material_id_seq'::regclass);


--
-- Name: custom_order_print_size_presets print_size_preset_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_print_size_presets ALTER COLUMN print_size_preset_id SET DEFAULT nextval('public.custom_order_print_size_presets_print_size_preset_id_seq'::regclass);


--
-- Name: custom_order_status_history custom_order_status_history_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_status_history ALTER COLUMN custom_order_status_history_id SET DEFAULT nextval('public.custom_order_status_history_custom_order_status_history_id_seq'::regclass);


--
-- Name: custom_orders custom_order_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_orders ALTER COLUMN custom_order_id SET DEFAULT nextval('public.custom_orders_custom_order_id_seq'::regclass);


--
-- Name: customer_account_payment_allocations allocation_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_account_payment_allocations ALTER COLUMN allocation_id SET DEFAULT nextval('public.customer_account_payment_allocations_allocation_id_seq'::regclass);


--
-- Name: customer_account_transactions transaction_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_account_transactions ALTER COLUMN transaction_id SET DEFAULT nextval('public.customer_account_transactions_transaction_id_seq'::regclass);


--
-- Name: customer_accounts customer_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_accounts ALTER COLUMN customer_id SET DEFAULT nextval('public.customer_accounts_customer_id_seq'::regclass);


--
-- Name: customer_types customer_type_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_types ALTER COLUMN customer_type_id SET DEFAULT nextval('public.customer_types_customer_type_id_seq'::regclass);


--
-- Name: device_sessions session_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_sessions ALTER COLUMN session_id SET DEFAULT nextval('public.device_sessions_session_id_seq'::regclass);


--
-- Name: email_outbox email_outbox_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox ALTER COLUMN email_outbox_id SET DEFAULT nextval('public.email_outbox_email_outbox_id_seq'::regclass);


--
-- Name: email_outbox_events email_outbox_event_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox_events ALTER COLUMN email_outbox_event_id SET DEFAULT nextval('public.email_outbox_events_email_outbox_event_id_seq'::regclass);


--
-- Name: employee_payroll_bonuses employee_payroll_bonus_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_payroll_bonuses ALTER COLUMN employee_payroll_bonus_id SET DEFAULT nextval('public.employee_payroll_bonuses_employee_payroll_bonus_id_seq'::regclass);


--
-- Name: employee_time_clock clock_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_time_clock ALTER COLUMN clock_id SET DEFAULT nextval('public.employee_time_clock_clock_id_seq'::regclass);


--
-- Name: expenses expense_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expenses ALTER COLUMN expense_id SET DEFAULT nextval('public.expenses_expense_id_seq'::regclass);


--
-- Name: held_cart_items held_cart_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.held_cart_items ALTER COLUMN held_cart_item_id SET DEFAULT nextval('public.held_cart_items_held_cart_item_id_seq'::regclass);


--
-- Name: held_carts held_cart_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.held_carts ALTER COLUMN held_cart_id SET DEFAULT nextval('public.held_carts_held_cart_id_seq'::regclass);


--
-- Name: inventory_movements movement_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_movements ALTER COLUMN movement_id SET DEFAULT nextval('public.inventory_movements_movement_id_seq'::regclass);


--
-- Name: invoice_audit_log invoice_audit_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_audit_log ALTER COLUMN invoice_audit_id SET DEFAULT nextval('public.invoice_audit_log_invoice_audit_id_seq'::regclass);


--
-- Name: invoice_delivery_events invoice_delivery_event_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_delivery_events ALTER COLUMN invoice_delivery_event_id SET DEFAULT nextval('public.invoice_delivery_events_invoice_delivery_event_id_seq'::regclass);


--
-- Name: invoice_delivery_lines invoice_delivery_line_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_delivery_lines ALTER COLUMN invoice_delivery_line_id SET DEFAULT nextval('public.invoice_delivery_lines_invoice_delivery_line_id_seq'::regclass);


--
-- Name: invoice_lines invoice_line_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_lines ALTER COLUMN invoice_line_id SET DEFAULT nextval('public.invoice_lines_invoice_line_id_seq'::regclass);


--
-- Name: invoice_payments invoice_payment_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_payments ALTER COLUMN invoice_payment_id SET DEFAULT nextval('public.invoice_payments_invoice_payment_id_seq'::regclass);


--
-- Name: invoice_status_history invoice_status_history_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_status_history ALTER COLUMN invoice_status_history_id SET DEFAULT nextval('public.invoice_status_history_invoice_status_history_id_seq'::regclass);


--
-- Name: invoices invoice_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices ALTER COLUMN invoice_id SET DEFAULT nextval('public.invoices_invoice_id_seq'::regclass);


--
-- Name: item_brands brand_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_brands ALTER COLUMN brand_id SET DEFAULT nextval('public.item_brands_brand_id_seq'::regclass);


--
-- Name: item_types item_type_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_types ALTER COLUMN item_type_id SET DEFAULT nextval('public.item_types_item_type_id_seq'::regclass);


--
-- Name: locations location_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations ALTER COLUMN location_id SET DEFAULT nextval('public.locations_location_id_seq'::regclass);


--
-- Name: maintenance_logs log_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_logs ALTER COLUMN log_id SET DEFAULT nextval('public.maintenance_logs_log_id_seq'::regclass);


--
-- Name: maintenance_machine_parts machine_part_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_machine_parts ALTER COLUMN machine_part_id SET DEFAULT nextval('public.maintenance_machine_parts_machine_part_id_seq'::regclass);


--
-- Name: maintenance_machines machine_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_machines ALTER COLUMN machine_id SET DEFAULT nextval('public.maintenance_machines_machine_id_seq'::regclass);


--
-- Name: maintenance_parts part_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_parts ALTER COLUMN part_id SET DEFAULT nextval('public.maintenance_parts_part_id_seq'::regclass);


--
-- Name: maintenance_ticket_notes note_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_ticket_notes ALTER COLUMN note_id SET DEFAULT nextval('public.maintenance_ticket_notes_note_id_seq'::regclass);


--
-- Name: maintenance_ticket_parts ticket_part_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_ticket_parts ALTER COLUMN ticket_part_id SET DEFAULT nextval('public.maintenance_ticket_parts_ticket_part_id_seq'::regclass);


--
-- Name: maintenance_tickets ticket_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_tickets ALTER COLUMN ticket_id SET DEFAULT nextval('public.maintenance_tickets_ticket_id_seq'::regclass);


--
-- Name: other_income_entries other_income_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.other_income_entries ALTER COLUMN other_income_id SET DEFAULT nextval('public.other_income_entries_other_income_id_seq'::regclass);


--
-- Name: payroll_payments payroll_payment_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payroll_payments ALTER COLUMN payroll_payment_id SET DEFAULT nextval('public.payroll_payments_payroll_payment_id_seq'::regclass);


--
-- Name: permissions permission_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions ALTER COLUMN permission_id SET DEFAULT nextval('public.permissions_permission_id_seq'::regclass);


--
-- Name: products product_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products ALTER COLUMN product_id SET DEFAULT nextval('public.products_product_id_seq'::regclass);


--
-- Name: quotation_audit_log quotation_audit_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_audit_log ALTER COLUMN quotation_audit_id SET DEFAULT nextval('public.quotation_audit_log_quotation_audit_id_seq'::regclass);


--
-- Name: quotation_lines quotation_line_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_lines ALTER COLUMN quotation_line_id SET DEFAULT nextval('public.quotation_lines_quotation_line_id_seq'::regclass);


--
-- Name: quotation_status_history quotation_status_history_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_status_history ALTER COLUMN quotation_status_history_id SET DEFAULT nextval('public.quotation_status_history_quotation_status_history_id_seq'::regclass);


--
-- Name: quotations quotation_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotations ALTER COLUMN quotation_id SET DEFAULT nextval('public.quotations_quotation_id_seq'::regclass);


--
-- Name: roles role_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles ALTER COLUMN role_id SET DEFAULT nextval('public.roles_role_id_seq'::regclass);


--
-- Name: sale_audit_log sale_audit_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_audit_log ALTER COLUMN sale_audit_id SET DEFAULT nextval('public.sale_audit_log_sale_audit_id_seq'::regclass);


--
-- Name: sale_items sale_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_items ALTER COLUMN sale_item_id SET DEFAULT nextval('public.sale_items_sale_item_id_seq'::regclass);


--
-- Name: sale_return_items return_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_return_items ALTER COLUMN return_item_id SET DEFAULT nextval('public.sale_return_items_return_item_id_seq'::regclass);


--
-- Name: sale_returns return_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_returns ALTER COLUMN return_id SET DEFAULT nextval('public.sale_returns_return_id_seq'::regclass);


--
-- Name: sales sale_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales ALTER COLUMN sale_id SET DEFAULT nextval('public.sales_sale_id_seq'::regclass);


--
-- Name: security_audit_events event_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_audit_events ALTER COLUMN event_id SET DEFAULT nextval('public.security_audit_events_event_id_seq'::regclass);


--
-- Name: shelf_locations shelf_location_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shelf_locations ALTER COLUMN shelf_location_id SET DEFAULT nextval('public.shelf_locations_shelf_location_id_seq'::regclass);


--
-- Name: store_transfer_items transfer_item_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_transfer_items ALTER COLUMN transfer_item_id SET DEFAULT nextval('public.store_transfer_items_transfer_item_id_seq'::regclass);


--
-- Name: store_transfers transfer_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_transfers ALTER COLUMN transfer_id SET DEFAULT nextval('public.store_transfers_transfer_id_seq'::regclass);


--
-- Name: sync_audit_log sync_audit_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_audit_log ALTER COLUMN sync_audit_id SET DEFAULT nextval('public.sync_audit_log_sync_audit_id_seq'::regclass);


--
-- Name: sync_conflicts conflict_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_conflicts ALTER COLUMN conflict_id SET DEFAULT nextval('public.sync_conflicts_conflict_id_seq'::regclass);


--
-- Name: sync_id_map id_map_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_id_map ALTER COLUMN id_map_id SET DEFAULT nextval('public.sync_id_map_id_map_id_seq'::regclass);


--
-- Name: sync_transfer_metrics metric_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_transfer_metrics ALTER COLUMN metric_id SET DEFAULT nextval('public.sync_transfer_metrics_metric_id_seq'::regclass);


--
-- Name: users user_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN user_id SET DEFAULT nextval('public.users_user_id_seq'::regclass);


--
-- Name: vendors vendor_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendors ALTER COLUMN vendor_id SET DEFAULT nextval('public.vendors_vendor_id_seq'::regclass);


--
-- Name: app_releases app_releases_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_releases
    ADD CONSTRAINT app_releases_pkey PRIMARY KEY (release_id);


--
-- Name: balance_sheet_bf_overrides balance_sheet_bf_overrides_location_period_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_bf_overrides
    ADD CONSTRAINT balance_sheet_bf_overrides_location_period_unique UNIQUE (location_id, period_start);


--
-- Name: balance_sheet_bf_overrides balance_sheet_bf_overrides_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_bf_overrides
    ADD CONSTRAINT balance_sheet_bf_overrides_pkey PRIMARY KEY (balance_sheet_bf_override_id);


--
-- Name: balance_sheet_submission_revisions balance_sheet_revision_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_submission_revisions
    ADD CONSTRAINT balance_sheet_revision_unique UNIQUE (balance_sheet_submission_id, revision_no);


--
-- Name: balance_sheet_submission_revisions balance_sheet_submission_revisions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_submission_revisions
    ADD CONSTRAINT balance_sheet_submission_revisions_pkey PRIMARY KEY (balance_sheet_revision_id);


--
-- Name: balance_sheet_submissions balance_sheet_submissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_submissions
    ADD CONSTRAINT balance_sheet_submissions_pkey PRIMARY KEY (balance_sheet_submission_id);


--
-- Name: bank_transactions bank_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_transactions
    ADD CONSTRAINT bank_transactions_pkey PRIMARY KEY (bank_transaction_id);


--
-- Name: cash_drawer_device_assignments cash_drawer_device_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_device_assignments
    ADD CONSTRAINT cash_drawer_device_assignments_pkey PRIMARY KEY (assignment_id);


--
-- Name: cash_drawer_handovers cash_drawer_handovers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_handovers
    ADD CONSTRAINT cash_drawer_handovers_pkey PRIMARY KEY (cash_drawer_handover_id);


--
-- Name: cash_drawer_sessions cash_drawer_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_sessions
    ADD CONSTRAINT cash_drawer_sessions_pkey PRIMARY KEY (cash_drawer_session_id);


--
-- Name: cash_drawers cash_drawers_location_id_drawer_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawers
    ADD CONSTRAINT cash_drawers_location_id_drawer_name_key UNIQUE (location_id, drawer_name);


--
-- Name: cash_drawers cash_drawers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawers
    ADD CONSTRAINT cash_drawers_pkey PRIMARY KEY (cash_drawer_id);


--
-- Name: categories categories_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_name_key UNIQUE (name);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (category_id);


--
-- Name: change_basket_updates change_basket_updates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.change_basket_updates
    ADD CONSTRAINT change_basket_updates_pkey PRIMARY KEY (change_basket_update_id);


--
-- Name: cheque_bank_deposits cheque_bank_deposits_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cheque_bank_deposits
    ADD CONSTRAINT cheque_bank_deposits_pkey PRIMARY KEY (cheque_bank_deposit_id);


--
-- Name: cheque_bank_deposits cheque_bank_deposits_source_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cheque_bank_deposits
    ADD CONSTRAINT cheque_bank_deposits_source_unique UNIQUE (source_type, source_id);


--
-- Name: company_customization company_customization_location_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_customization
    ADD CONSTRAINT company_customization_location_id_key UNIQUE (location_id);


--
-- Name: company_customization company_customization_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_customization
    ADD CONSTRAINT company_customization_pkey PRIMARY KEY (customization_id);


--
-- Name: company_info company_info_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_info
    ADD CONSTRAINT company_info_pkey PRIMARY KEY (company_info_id);


--
-- Name: cross_store_refund_lines cross_store_refund_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cross_store_refund_lines
    ADD CONSTRAINT cross_store_refund_lines_pkey PRIMARY KEY (request_id, source_sale_item_id);


--
-- Name: cross_store_refund_reconciliation cross_store_refund_reconciliation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cross_store_refund_reconciliation
    ADD CONSTRAINT cross_store_refund_reconciliation_pkey PRIMARY KEY (reconciliation_id);


--
-- Name: cross_store_refund_requests cross_store_refund_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cross_store_refund_requests
    ADD CONSTRAINT cross_store_refund_requests_pkey PRIMARY KEY (request_id);


--
-- Name: cross_store_refund_requests cross_store_refund_requests_source_location_id_source_sale__key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cross_store_refund_requests
    ADD CONSTRAINT cross_store_refund_requests_source_location_id_source_sale__key UNIQUE (source_location_id, source_sale_id, request_id);


--
-- Name: custom_order_audit_log custom_order_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_audit_log
    ADD CONSTRAINT custom_order_audit_log_pkey PRIMARY KEY (custom_order_audit_id);


--
-- Name: custom_order_design_placements custom_order_design_placements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_design_placements
    ADD CONSTRAINT custom_order_design_placements_pkey PRIMARY KEY (design_placement_id);


--
-- Name: custom_order_design_placements custom_order_design_placements_placement_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_design_placements
    ADD CONSTRAINT custom_order_design_placements_placement_name_key UNIQUE (placement_name);


--
-- Name: custom_order_inventory_reservations custom_order_inventory_reservations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_inventory_reservations
    ADD CONSTRAINT custom_order_inventory_reservations_pkey PRIMARY KEY (custom_order_inventory_reservation_id);


--
-- Name: custom_order_item_barcodes custom_order_item_barcodes_barcode_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_barcodes
    ADD CONSTRAINT custom_order_item_barcodes_barcode_key UNIQUE (barcode);


--
-- Name: custom_order_item_barcodes custom_order_item_barcodes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_barcodes
    ADD CONSTRAINT custom_order_item_barcodes_pkey PRIMARY KEY (custom_item_barcode_id);


--
-- Name: custom_order_item_movements custom_order_item_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_movements
    ADD CONSTRAINT custom_order_item_movements_pkey PRIMARY KEY (movement_id);


--
-- Name: custom_order_item_variants custom_order_item_variants_barcode_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_variants
    ADD CONSTRAINT custom_order_item_variants_barcode_key UNIQUE (barcode);


--
-- Name: custom_order_item_variants custom_order_item_variants_item_name_uidx; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_variants
    ADD CONSTRAINT custom_order_item_variants_item_name_uidx UNIQUE (custom_item_id, variant_name);


--
-- Name: custom_order_item_variants custom_order_item_variants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_variants
    ADD CONSTRAINT custom_order_item_variants_pkey PRIMARY KEY (custom_variant_id);


--
-- Name: custom_order_items custom_order_items_barcode_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_items
    ADD CONSTRAINT custom_order_items_barcode_key UNIQUE (barcode);


--
-- Name: custom_order_items custom_order_items_item_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_items
    ADD CONSTRAINT custom_order_items_item_name_key UNIQUE (item_name);


--
-- Name: custom_order_items custom_order_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_items
    ADD CONSTRAINT custom_order_items_pkey PRIMARY KEY (custom_item_id);


--
-- Name: custom_order_line_deliveries custom_order_line_deliveries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_deliveries
    ADD CONSTRAINT custom_order_line_deliveries_pkey PRIMARY KEY (custom_order_line_delivery_id);


--
-- Name: custom_order_line_print_addons custom_order_line_print_addons_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_print_addons
    ADD CONSTRAINT custom_order_line_print_addons_pkey PRIMARY KEY (custom_order_line_print_addon_id);


--
-- Name: custom_order_line_production_history custom_order_line_production_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_production_history
    ADD CONSTRAINT custom_order_line_production_history_pkey PRIMARY KEY (custom_order_line_production_history_id);


--
-- Name: custom_order_line_returns custom_order_line_returns_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_returns
    ADD CONSTRAINT custom_order_line_returns_pkey PRIMARY KEY (custom_order_line_return_id);


--
-- Name: custom_order_lines custom_order_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_lines
    ADD CONSTRAINT custom_order_lines_pkey PRIMARY KEY (custom_order_line_id);


--
-- Name: custom_order_payments custom_order_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_payments
    ADD CONSTRAINT custom_order_payments_pkey PRIMARY KEY (custom_order_payment_id);


--
-- Name: custom_order_print_materials custom_order_print_materials_material_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_print_materials
    ADD CONSTRAINT custom_order_print_materials_material_name_key UNIQUE (material_name);


--
-- Name: custom_order_print_materials custom_order_print_materials_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_print_materials
    ADD CONSTRAINT custom_order_print_materials_pkey PRIMARY KEY (print_material_id);


--
-- Name: custom_order_print_size_presets custom_order_print_size_presets_name_uidx; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_print_size_presets
    ADD CONSTRAINT custom_order_print_size_presets_name_uidx UNIQUE (print_material_id, preset_name);


--
-- Name: custom_order_print_size_presets custom_order_print_size_presets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_print_size_presets
    ADD CONSTRAINT custom_order_print_size_presets_pkey PRIMARY KEY (print_size_preset_id);


--
-- Name: custom_order_status_history custom_order_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_status_history
    ADD CONSTRAINT custom_order_status_history_pkey PRIMARY KEY (custom_order_status_history_id);


--
-- Name: custom_orders custom_orders_order_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_orders
    ADD CONSTRAINT custom_orders_order_number_key UNIQUE (order_number);


--
-- Name: custom_orders custom_orders_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_orders
    ADD CONSTRAINT custom_orders_pkey PRIMARY KEY (custom_order_id);


--
-- Name: customer_account_payment_allocations customer_account_payment_allocations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_account_payment_allocations
    ADD CONSTRAINT customer_account_payment_allocations_pkey PRIMARY KEY (allocation_id);


--
-- Name: customer_account_transactions customer_account_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_account_transactions
    ADD CONSTRAINT customer_account_transactions_pkey PRIMARY KEY (transaction_id);


--
-- Name: customer_accounts customer_accounts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_accounts
    ADD CONSTRAINT customer_accounts_pkey PRIMARY KEY (customer_id);


--
-- Name: customer_types customer_types_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_types
    ADD CONSTRAINT customer_types_name_key UNIQUE (name);


--
-- Name: customer_types customer_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_types
    ADD CONSTRAINT customer_types_pkey PRIMARY KEY (customer_type_id);


--
-- Name: device_sessions device_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_sessions
    ADD CONSTRAINT device_sessions_pkey PRIMARY KEY (session_id);


--
-- Name: devices devices_installation_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_installation_id_key UNIQUE (installation_id);


--
-- Name: devices devices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_pkey PRIMARY KEY (device_id);


--
-- Name: email_outbox_events email_outbox_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox_events
    ADD CONSTRAINT email_outbox_events_pkey PRIMARY KEY (email_outbox_event_id);


--
-- Name: email_outbox email_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox
    ADD CONSTRAINT email_outbox_pkey PRIMARY KEY (email_outbox_id);


--
-- Name: employee_payroll_bonuses employee_payroll_bonuses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_payroll_bonuses
    ADD CONSTRAINT employee_payroll_bonuses_pkey PRIMARY KEY (employee_payroll_bonus_id);


--
-- Name: employee_payroll_bonuses employee_payroll_bonuses_sync_uuid_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_payroll_bonuses
    ADD CONSTRAINT employee_payroll_bonuses_sync_uuid_key UNIQUE (sync_uuid);


--
-- Name: employee_payroll_settings employee_payroll_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_payroll_settings
    ADD CONSTRAINT employee_payroll_settings_pkey PRIMARY KEY (setting_id);


--
-- Name: employee_payroll_settings employee_payroll_settings_user_effective_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_payroll_settings
    ADD CONSTRAINT employee_payroll_settings_user_effective_key UNIQUE (user_id, effective_from);


--
-- Name: employee_schedule_assignments employee_schedule_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_assignments
    ADD CONSTRAINT employee_schedule_assignments_pkey PRIMARY KEY (location_id, user_id, work_date);


--
-- Name: employee_schedule_holidays employee_schedule_holidays_holiday_date_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_holidays
    ADD CONSTRAINT employee_schedule_holidays_holiday_date_key UNIQUE (holiday_date);


--
-- Name: employee_schedule_holidays employee_schedule_holidays_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_holidays
    ADD CONSTRAINT employee_schedule_holidays_pkey PRIMARY KEY (holiday_id);


--
-- Name: employee_schedule_shifts employee_schedule_shifts_location_identity; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_shifts
    ADD CONSTRAINT employee_schedule_shifts_location_identity UNIQUE (location_id, shift_id);


--
-- Name: employee_schedule_shifts employee_schedule_shifts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_shifts
    ADD CONSTRAINT employee_schedule_shifts_pkey PRIMARY KEY (shift_id);


--
-- Name: employee_time_clock_adjustments employee_time_clock_adjustments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_time_clock_adjustments
    ADD CONSTRAINT employee_time_clock_adjustments_pkey PRIMARY KEY (adjustment_id);


--
-- Name: employee_time_clock employee_time_clock_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_time_clock
    ADD CONSTRAINT employee_time_clock_pkey PRIMARY KEY (clock_id);


--
-- Name: expenses expenses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_pkey PRIMARY KEY (expense_id);


--
-- Name: held_cart_items held_cart_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.held_cart_items
    ADD CONSTRAINT held_cart_items_pkey PRIMARY KEY (held_cart_item_id);


--
-- Name: held_carts held_carts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.held_carts
    ADD CONSTRAINT held_carts_pkey PRIMARY KEY (held_cart_id);


--
-- Name: image_asset_references image_asset_references_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_asset_references
    ADD CONSTRAINT image_asset_references_pkey PRIMARY KEY (reference_id);


--
-- Name: image_asset_references image_asset_references_source_table_source_key_source_colum_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_asset_references
    ADD CONSTRAINT image_asset_references_source_table_source_key_source_colum_key UNIQUE (source_table, source_key, source_column);


--
-- Name: image_assets image_assets_bucket_name_object_path_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_assets
    ADD CONSTRAINT image_assets_bucket_name_object_path_key UNIQUE (bucket_name, object_path);


--
-- Name: image_assets image_assets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_assets
    ADD CONSTRAINT image_assets_pkey PRIMARY KEY (asset_id);


--
-- Name: image_sync_state image_sync_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_sync_state
    ADD CONSTRAINT image_sync_state_pkey PRIMARY KEY (state_key);


--
-- Name: inventory_movements inventory_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_movements
    ADD CONSTRAINT inventory_movements_pkey PRIMARY KEY (movement_id);


--
-- Name: inventory inventory_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_pkey PRIMARY KEY (product_id, location_id);


--
-- Name: invoice_audit_log invoice_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_audit_log
    ADD CONSTRAINT invoice_audit_log_pkey PRIMARY KEY (invoice_audit_id);


--
-- Name: invoice_delivery_events invoice_delivery_events_delivery_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_delivery_events
    ADD CONSTRAINT invoice_delivery_events_delivery_number_key UNIQUE (delivery_number);


--
-- Name: invoice_delivery_events invoice_delivery_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_delivery_events
    ADD CONSTRAINT invoice_delivery_events_pkey PRIMARY KEY (invoice_delivery_event_id);


--
-- Name: invoice_delivery_lines invoice_delivery_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_delivery_lines
    ADD CONSTRAINT invoice_delivery_lines_pkey PRIMARY KEY (invoice_delivery_line_id);


--
-- Name: invoice_lines invoice_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_lines
    ADD CONSTRAINT invoice_lines_pkey PRIMARY KEY (invoice_line_id);


--
-- Name: invoice_payments invoice_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_payments
    ADD CONSTRAINT invoice_payments_pkey PRIMARY KEY (invoice_payment_id);


--
-- Name: invoice_status_history invoice_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_status_history
    ADD CONSTRAINT invoice_status_history_pkey PRIMARY KEY (invoice_status_history_id);


--
-- Name: invoices invoices_invoice_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_invoice_number_key UNIQUE (invoice_number);


--
-- Name: invoices invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_pkey PRIMARY KEY (invoice_id);


--
-- Name: item_brands item_brands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_brands
    ADD CONSTRAINT item_brands_pkey PRIMARY KEY (brand_id);


--
-- Name: item_types item_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_types
    ADD CONSTRAINT item_types_pkey PRIMARY KEY (item_type_id);


--
-- Name: lan_api_approvals lan_api_approvals_approval_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_approvals
    ADD CONSTRAINT lan_api_approvals_approval_hash_key UNIQUE (approval_hash);


--
-- Name: lan_api_approvals lan_api_approvals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_approvals
    ADD CONSTRAINT lan_api_approvals_pkey PRIMARY KEY (approval_id);


--
-- Name: lan_api_idempotency lan_api_idempotency_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_idempotency
    ADD CONSTRAINT lan_api_idempotency_pkey PRIMARY KEY (device_id, idempotency_key);


--
-- Name: lan_api_request_audit lan_api_request_audit_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_request_audit
    ADD CONSTRAINT lan_api_request_audit_pkey PRIMARY KEY (request_id);


--
-- Name: lan_api_schedule_proposals lan_api_schedule_proposals_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_schedule_proposals
    ADD CONSTRAINT lan_api_schedule_proposals_pkey PRIMARY KEY (proposal_id);


--
-- Name: lan_api_sessions lan_api_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_sessions
    ADD CONSTRAINT lan_api_sessions_pkey PRIMARY KEY (session_id);


--
-- Name: lan_api_sessions lan_api_sessions_session_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_sessions
    ADD CONSTRAINT lan_api_sessions_session_hash_key UNIQUE (session_hash);


--
-- Name: locations locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.locations
    ADD CONSTRAINT locations_pkey PRIMARY KEY (location_id);


--
-- Name: login_security_state login_security_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.login_security_state
    ADD CONSTRAINT login_security_state_pkey PRIMARY KEY (identifier_hash);


--
-- Name: maintenance_logs maintenance_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_logs
    ADD CONSTRAINT maintenance_logs_pkey PRIMARY KEY (log_id);


--
-- Name: maintenance_machine_parts maintenance_machine_parts_machine_part_uidx; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_machine_parts
    ADD CONSTRAINT maintenance_machine_parts_machine_part_uidx UNIQUE (machine_id, part_id);


--
-- Name: maintenance_machine_parts maintenance_machine_parts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_machine_parts
    ADD CONSTRAINT maintenance_machine_parts_pkey PRIMARY KEY (machine_part_id);


--
-- Name: maintenance_machines maintenance_machines_asset_tag_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_machines
    ADD CONSTRAINT maintenance_machines_asset_tag_key UNIQUE (asset_tag);


--
-- Name: maintenance_machines maintenance_machines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_machines
    ADD CONSTRAINT maintenance_machines_pkey PRIMARY KEY (machine_id);


--
-- Name: maintenance_parts maintenance_parts_part_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_parts
    ADD CONSTRAINT maintenance_parts_part_number_key UNIQUE (part_number);


--
-- Name: maintenance_parts maintenance_parts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_parts
    ADD CONSTRAINT maintenance_parts_pkey PRIMARY KEY (part_id);


--
-- Name: maintenance_ticket_notes maintenance_ticket_notes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_ticket_notes
    ADD CONSTRAINT maintenance_ticket_notes_pkey PRIMARY KEY (note_id);


--
-- Name: maintenance_ticket_parts maintenance_ticket_parts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_ticket_parts
    ADD CONSTRAINT maintenance_ticket_parts_pkey PRIMARY KEY (ticket_part_id);


--
-- Name: maintenance_tickets maintenance_tickets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_tickets
    ADD CONSTRAINT maintenance_tickets_pkey PRIMARY KEY (ticket_id);


--
-- Name: mobile_permissions mobile_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.mobile_permissions
    ADD CONSTRAINT mobile_permissions_pkey PRIMARY KEY (permission_key);


--
-- Name: notification_user_state notification_user_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_user_state
    ADD CONSTRAINT notification_user_state_pkey PRIMARY KEY (user_id, notification_key);


--
-- Name: other_income_entries other_income_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.other_income_entries
    ADD CONSTRAINT other_income_entries_pkey PRIMARY KEY (other_income_id);


--
-- Name: payroll_payments payroll_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payroll_payments
    ADD CONSTRAINT payroll_payments_pkey PRIMARY KEY (payroll_payment_id);


--
-- Name: permissions permissions_permission_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_permission_key_key UNIQUE (permission_key);


--
-- Name: permissions permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.permissions
    ADD CONSTRAINT permissions_pkey PRIMARY KEY (permission_id);


--
-- Name: product_barcodes product_barcodes_barcode_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_barcodes
    ADD CONSTRAINT product_barcodes_barcode_key UNIQUE (barcode);


--
-- Name: product_barcodes product_barcodes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_barcodes
    ADD CONSTRAINT product_barcodes_pkey PRIMARY KEY (product_barcode_id);


--
-- Name: product_shelf_assignments product_shelf_assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_shelf_assignments
    ADD CONSTRAINT product_shelf_assignments_pkey PRIMARY KEY (product_id, location_id);


--
-- Name: products products_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_pkey PRIMARY KEY (product_id);


--
-- Name: quotation_audit_log quotation_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_audit_log
    ADD CONSTRAINT quotation_audit_log_pkey PRIMARY KEY (quotation_audit_id);


--
-- Name: quotation_lines quotation_lines_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_lines
    ADD CONSTRAINT quotation_lines_pkey PRIMARY KEY (quotation_line_id);


--
-- Name: quotation_status_history quotation_status_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_status_history
    ADD CONSTRAINT quotation_status_history_pkey PRIMARY KEY (quotation_status_history_id);


--
-- Name: quotations quotations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotations
    ADD CONSTRAINT quotations_pkey PRIMARY KEY (quotation_id);


--
-- Name: quotations quotations_quotation_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotations
    ADD CONSTRAINT quotations_quotation_number_key UNIQUE (quotation_number);


--
-- Name: receiving_batches receiving_batches_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_batches
    ADD CONSTRAINT receiving_batches_pkey PRIMARY KEY (receive_id);


--
-- Name: remote_admin_commands remote_admin_commands_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.remote_admin_commands
    ADD CONSTRAINT remote_admin_commands_pkey PRIMARY KEY (command_id);


--
-- Name: role_mobile_permissions role_mobile_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_mobile_permissions
    ADD CONSTRAINT role_mobile_permissions_pkey PRIMARY KEY (role_id, permission_key);


--
-- Name: role_permissions role_permissions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (role_id, permission_id);


--
-- Name: roles roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_pkey PRIMARY KEY (role_id);


--
-- Name: roles roles_role_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.roles
    ADD CONSTRAINT roles_role_name_key UNIQUE (role_name);


--
-- Name: sale_audit_log sale_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_audit_log
    ADD CONSTRAINT sale_audit_log_pkey PRIMARY KEY (sale_audit_id);


--
-- Name: sale_items sale_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_items
    ADD CONSTRAINT sale_items_pkey PRIMARY KEY (sale_item_id);


--
-- Name: sale_return_items sale_return_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_return_items
    ADD CONSTRAINT sale_return_items_pkey PRIMARY KEY (return_item_id);


--
-- Name: sale_returns sale_returns_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_returns
    ADD CONSTRAINT sale_returns_pkey PRIMARY KEY (return_id);


--
-- Name: sales sales_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT sales_pkey PRIMARY KEY (sale_id);


--
-- Name: security_audit_events security_audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_audit_events
    ADD CONSTRAINT security_audit_events_pkey PRIMARY KEY (event_id);


--
-- Name: shelf_locations shelf_locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shelf_locations
    ADD CONSTRAINT shelf_locations_pkey PRIMARY KEY (shelf_location_id);


--
-- Name: shelf_locations shelf_locations_shelf_location_id_location_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shelf_locations
    ADD CONSTRAINT shelf_locations_shelf_location_id_location_id_key UNIQUE (shelf_location_id, location_id);


--
-- Name: store_sync_status store_sync_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_sync_status
    ADD CONSTRAINT store_sync_status_pkey PRIMARY KEY (location_id);


--
-- Name: store_transfer_items store_transfer_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_transfer_items
    ADD CONSTRAINT store_transfer_items_pkey PRIMARY KEY (transfer_item_id);


--
-- Name: store_transfers store_transfers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_transfers
    ADD CONSTRAINT store_transfers_pkey PRIMARY KEY (transfer_id);


--
-- Name: sync_applied_events sync_applied_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_applied_events
    ADD CONSTRAINT sync_applied_events_pkey PRIMARY KEY (origin_event_id);


--
-- Name: sync_audit_log sync_audit_log_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_audit_log
    ADD CONSTRAINT sync_audit_log_pkey PRIMARY KEY (sync_audit_id);


--
-- Name: sync_cloud_state sync_cloud_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_cloud_state
    ADD CONSTRAINT sync_cloud_state_pkey PRIMARY KEY (state_id);


--
-- Name: sync_conflicts sync_conflicts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_conflicts
    ADD CONSTRAINT sync_conflicts_pkey PRIMARY KEY (conflict_id);


--
-- Name: sync_cross_store_inventory_cache sync_cross_store_inventory_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_cross_store_inventory_cache
    ADD CONSTRAINT sync_cross_store_inventory_cache_pkey PRIMARY KEY (source_location_id, product_id);


--
-- Name: sync_cross_store_inventory_status sync_cross_store_inventory_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_cross_store_inventory_status
    ADD CONSTRAINT sync_cross_store_inventory_status_pkey PRIMARY KEY (source_location_id);


--
-- Name: sync_cross_store_return_items_cache sync_cross_store_return_items_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_cross_store_return_items_cache
    ADD CONSTRAINT sync_cross_store_return_items_cache_pkey PRIMARY KEY (source_location_id, return_item_id);


--
-- Name: sync_cross_store_returns_cache sync_cross_store_returns_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_cross_store_returns_cache
    ADD CONSTRAINT sync_cross_store_returns_cache_pkey PRIMARY KEY (source_location_id, return_id);


--
-- Name: sync_cross_store_sale_items_cache sync_cross_store_sale_items_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_cross_store_sale_items_cache
    ADD CONSTRAINT sync_cross_store_sale_items_cache_pkey PRIMARY KEY (source_location_id, sale_item_id);


--
-- Name: sync_cross_store_sales_cache sync_cross_store_sales_cache_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_cross_store_sales_cache
    ADD CONSTRAINT sync_cross_store_sales_cache_pkey PRIMARY KEY (source_location_id, sale_id);


--
-- Name: sync_cross_store_sales_status sync_cross_store_sales_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_cross_store_sales_status
    ADD CONSTRAINT sync_cross_store_sales_status_pkey PRIMARY KEY (source_location_id);


--
-- Name: sync_id_map sync_id_map_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_id_map
    ADD CONSTRAINT sync_id_map_pkey PRIMARY KEY (id_map_id);


--
-- Name: sync_id_map sync_id_map_table_name_local_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_id_map
    ADD CONSTRAINT sync_id_map_table_name_local_id_key UNIQUE (table_name, local_id);


--
-- Name: sync_inbox sync_inbox_event_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_inbox
    ADD CONSTRAINT sync_inbox_event_id_key UNIQUE (event_id);


--
-- Name: sync_inbox sync_inbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_inbox
    ADD CONSTRAINT sync_inbox_pkey PRIMARY KEY (cloud_sequence);


--
-- Name: sync_locks sync_locks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_locks
    ADD CONSTRAINT sync_locks_pkey PRIMARY KEY (lock_name);


--
-- Name: sync_outbox sync_outbox_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_outbox
    ADD CONSTRAINT sync_outbox_pkey PRIMARY KEY (event_id);


--
-- Name: sync_row_mirror_completion sync_row_mirror_completion_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_row_mirror_completion
    ADD CONSTRAINT sync_row_mirror_completion_pkey PRIMARY KEY (location_id);


--
-- Name: sync_row_mirror_state sync_row_mirror_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_row_mirror_state
    ADD CONSTRAINT sync_row_mirror_state_pkey PRIMARY KEY (location_id, table_name, row_key);


--
-- Name: sync_service_status sync_service_status_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_service_status
    ADD CONSTRAINT sync_service_status_pkey PRIMARY KEY (service_id);


--
-- Name: sync_tombstones sync_tombstones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_tombstones
    ADD CONSTRAINT sync_tombstones_pkey PRIMARY KEY (tombstone_id);


--
-- Name: sync_tombstones sync_tombstones_table_name_key_data_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_tombstones
    ADD CONSTRAINT sync_tombstones_table_name_key_data_key UNIQUE (table_name, key_data);


--
-- Name: sync_transfer_metrics sync_transfer_metrics_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sync_transfer_metrics
    ADD CONSTRAINT sync_transfer_metrics_pkey PRIMARY KEY (metric_id);


--
-- Name: time_clock_auto_close_settings time_clock_auto_close_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.time_clock_auto_close_settings
    ADD CONSTRAINT time_clock_auto_close_settings_pkey PRIMARY KEY (settings_id);


--
-- Name: user_locations user_locations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_locations
    ADD CONSTRAINT user_locations_pkey PRIMARY KEY (user_id, location_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: vendors vendors_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendors
    ADD CONSTRAINT vendors_name_key UNIQUE (name);


--
-- Name: vendors vendors_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.vendors
    ADD CONSTRAINT vendors_pkey PRIMARY KEY (vendor_id);


--
-- Name: app_releases_latest_published_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX app_releases_latest_published_idx ON public.app_releases USING btree (platform, build_number DESC) WHERE (published = true);


--
-- Name: app_releases_platform_build_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX app_releases_platform_build_idx ON public.app_releases USING btree (platform, build_number);


--
-- Name: balance_sheet_bf_overrides_location_period_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX balance_sheet_bf_overrides_location_period_idx ON public.balance_sheet_bf_overrides USING btree (location_id, period_start DESC);


--
-- Name: balance_sheet_revision_changed_by_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX balance_sheet_revision_changed_by_idx ON public.balance_sheet_submission_revisions USING btree (changed_by_user_id);


--
-- Name: balance_sheet_revision_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX balance_sheet_revision_location_idx ON public.balance_sheet_submission_revisions USING btree (location_id);


--
-- Name: balance_sheet_revision_submission_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX balance_sheet_revision_submission_idx ON public.balance_sheet_submission_revisions USING btree (balance_sheet_submission_id, revision_no DESC);


--
-- Name: balance_sheet_submission_last_editor_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX balance_sheet_submission_last_editor_idx ON public.balance_sheet_submissions USING btree (last_edited_by_user_id);


--
-- Name: balance_sheet_submissions_location_period_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX balance_sheet_submissions_location_period_idx ON public.balance_sheet_submissions USING btree (location_id, period_start DESC, period_end DESC);


--
-- Name: balance_sheet_submissions_submitted_by_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX balance_sheet_submissions_submitted_by_user_idx ON public.balance_sheet_submissions USING btree (submitted_by_user_id);


--
-- Name: bank_transactions_location_date_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX bank_transactions_location_date_idx ON public.bank_transactions USING btree (location_id, transaction_date DESC);


--
-- Name: bank_transactions_source_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX bank_transactions_source_unique_idx ON public.bank_transactions USING btree (source_type, source_id) WHERE ((source_type IS NOT NULL) AND (source_id IS NOT NULL));


--
-- Name: cash_drawer_assignments_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_assignments_device_idx ON public.cash_drawer_device_assignments USING btree (device_id, location_id, is_active);


--
-- Name: cash_drawer_assignments_drawer_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_assignments_drawer_idx ON public.cash_drawer_device_assignments USING btree (cash_drawer_id, is_active);


--
-- Name: cash_drawer_device_assignments_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_device_assignments_updated_at_idx ON public.cash_drawer_device_assignments USING btree (updated_at DESC);


--
-- Name: cash_drawer_handovers_device_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_handovers_device_fk_idx ON public.cash_drawer_handovers USING btree (device_id);


--
-- Name: cash_drawer_handovers_drawer_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_handovers_drawer_fk_idx ON public.cash_drawer_handovers USING btree (cash_drawer_id);


--
-- Name: cash_drawer_handovers_from_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_handovers_from_user_fk_idx ON public.cash_drawer_handovers USING btree (from_user_id);


--
-- Name: cash_drawer_handovers_location_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_handovers_location_fk_idx ON public.cash_drawer_handovers USING btree (location_id);


--
-- Name: cash_drawer_handovers_session_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_handovers_session_idx ON public.cash_drawer_handovers USING btree (cash_drawer_session_id, handed_over_at DESC);


--
-- Name: cash_drawer_handovers_to_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_handovers_to_user_fk_idx ON public.cash_drawer_handovers USING btree (to_user_id);


--
-- Name: cash_drawer_one_active_device_assignment_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX cash_drawer_one_active_device_assignment_idx ON public.cash_drawer_device_assignments USING btree (location_id, device_id) WHERE (is_active = true);


--
-- Name: cash_drawer_one_open_session_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX cash_drawer_one_open_session_idx ON public.cash_drawer_sessions USING btree (cash_drawer_id, location_id, device_id) WHERE (status = 'OPEN'::text);


--
-- Name: cash_drawer_sessions_balanced_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_sessions_balanced_by_user_fk_idx ON public.cash_drawer_sessions USING btree (balanced_by_user_id);


--
-- Name: cash_drawer_sessions_closed_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_sessions_closed_by_user_fk_idx ON public.cash_drawer_sessions USING btree (closed_by_user_id);


--
-- Name: cash_drawer_sessions_current_cashier_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_sessions_current_cashier_user_fk_idx ON public.cash_drawer_sessions USING btree (current_cashier_user_id);


--
-- Name: cash_drawer_sessions_device_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_sessions_device_fk_idx ON public.cash_drawer_sessions USING btree (device_id);


--
-- Name: cash_drawer_sessions_drawer_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_sessions_drawer_idx ON public.cash_drawer_sessions USING btree (cash_drawer_id, opened_at DESC);


--
-- Name: cash_drawer_sessions_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_sessions_location_idx ON public.cash_drawer_sessions USING btree (location_id, status, opened_at DESC);


--
-- Name: cash_drawer_sessions_main_cashier_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_sessions_main_cashier_user_fk_idx ON public.cash_drawer_sessions USING btree (main_cashier_user_id);


--
-- Name: cash_drawer_sessions_opened_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawer_sessions_opened_by_user_fk_idx ON public.cash_drawer_sessions USING btree (opened_by_user_id);


--
-- Name: cash_drawers_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawers_location_idx ON public.cash_drawers USING btree (location_id, is_active, drawer_name);


--
-- Name: cash_drawers_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cash_drawers_updated_at_idx ON public.cash_drawers USING btree (updated_at DESC);


--
-- Name: categories_name_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX categories_name_unique_idx ON public.categories USING btree (lower(name));


--
-- Name: change_basket_updates_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX change_basket_updates_device_idx ON public.change_basket_updates USING btree (device_id) WHERE (device_id IS NOT NULL);


--
-- Name: change_basket_updates_location_updated_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX change_basket_updates_location_updated_idx ON public.change_basket_updates USING btree (location_id, updated_at DESC);


--
-- Name: change_basket_updates_updated_by_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX change_basket_updates_updated_by_user_idx ON public.change_basket_updates USING btree (updated_by_user_id) WHERE (updated_by_user_id IS NOT NULL);


--
-- Name: cheque_bank_deposits_location_deposited_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cheque_bank_deposits_location_deposited_idx ON public.cheque_bank_deposits USING btree (location_id, deposited_at DESC);


--
-- Name: cross_store_refund_requests_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX cross_store_refund_requests_status_idx ON public.cross_store_refund_requests USING btree (status, created_at);


--
-- Name: custom_order_audit_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_audit_device_idx ON public.custom_order_audit_log USING btree (device_id, created_at DESC);


--
-- Name: custom_order_audit_device_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_audit_device_name_idx ON public.custom_order_audit_log USING btree (device_name, created_at DESC);


--
-- Name: custom_order_audit_log_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_audit_log_sync_uuid_key ON public.custom_order_audit_log USING btree (sync_uuid);


--
-- Name: custom_order_audit_log_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_audit_log_user_fk_idx ON public.custom_order_audit_log USING btree (user_id) WHERE (user_id IS NOT NULL);


--
-- Name: custom_order_audit_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_audit_order_idx ON public.custom_order_audit_log USING btree (custom_order_id, created_at DESC);


--
-- Name: custom_order_design_placements_active_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_design_placements_active_idx ON public.custom_order_design_placements USING btree (is_active, sort_order, placement_name);


--
-- Name: custom_order_inventory_reservations_item_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_inventory_reservations_item_idx ON public.custom_order_inventory_reservations USING btree (custom_item_id, custom_variant_id, status);


--
-- Name: custom_order_inventory_reservations_line_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_inventory_reservations_line_fk_idx ON public.custom_order_inventory_reservations USING btree (custom_order_line_id) WHERE (custom_order_line_id IS NOT NULL);


--
-- Name: custom_order_inventory_reservations_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_inventory_reservations_order_idx ON public.custom_order_inventory_reservations USING btree (custom_order_id, status);


--
-- Name: custom_order_inventory_reservations_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_inventory_reservations_sync_uuid_key ON public.custom_order_inventory_reservations USING btree (sync_uuid);


--
-- Name: custom_order_inventory_reservations_variant_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_inventory_reservations_variant_fk_idx ON public.custom_order_inventory_reservations USING btree (custom_variant_id) WHERE (custom_variant_id IS NOT NULL);


--
-- Name: custom_order_item_barcodes_item_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_barcodes_item_idx ON public.custom_order_item_barcodes USING btree (custom_item_id);


--
-- Name: custom_order_item_movements_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_movements_device_idx ON public.custom_order_item_movements USING btree (device_id, created_at DESC);


--
-- Name: custom_order_item_movements_item_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_movements_item_idx ON public.custom_order_item_movements USING btree (custom_item_id, created_at DESC);


--
-- Name: custom_order_item_movements_line_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_movements_line_idx ON public.custom_order_item_movements USING btree (custom_order_line_id, created_at DESC);


--
-- Name: custom_order_item_movements_line_return_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_movements_line_return_idx ON public.custom_order_item_movements USING btree (custom_order_line_return_id, created_at DESC);


--
-- Name: custom_order_item_movements_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_movements_location_idx ON public.custom_order_item_movements USING btree (location_id, created_at DESC);


--
-- Name: custom_order_item_movements_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_movements_order_idx ON public.custom_order_item_movements USING btree (custom_order_id, created_at DESC);


--
-- Name: custom_order_item_movements_receive_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_movements_receive_idx ON public.custom_order_item_movements USING btree (receive_id, created_at DESC);


--
-- Name: custom_order_item_movements_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_item_movements_sync_uuid_key ON public.custom_order_item_movements USING btree (sync_uuid);


--
-- Name: custom_order_item_movements_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_movements_user_idx ON public.custom_order_item_movements USING btree (user_id, created_at DESC);


--
-- Name: custom_order_item_movements_variant_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_movements_variant_idx ON public.custom_order_item_movements USING btree (custom_variant_id, created_at DESC);


--
-- Name: custom_order_item_variants_barcode_uidx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_item_variants_barcode_uidx ON public.custom_order_item_variants USING btree (barcode) WHERE ((barcode IS NOT NULL) AND (barcode <> ''::text));


--
-- Name: custom_order_item_variants_item_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_variants_item_idx ON public.custom_order_item_variants USING btree (custom_item_id, is_active, variant_name);


--
-- Name: custom_order_item_variants_low_stock_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_variants_low_stock_idx ON public.custom_order_item_variants USING btree (is_active, quantity_on_hand, reorder_level);


--
-- Name: custom_order_item_variants_sku_search_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_item_variants_sku_search_idx ON public.custom_order_item_variants USING btree (sku);


--
-- Name: custom_order_item_variants_sku_uidx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_item_variants_sku_uidx ON public.custom_order_item_variants USING btree (upper(sku)) WHERE ((sku IS NOT NULL) AND (sku <> ''::text));


--
-- Name: custom_order_items_active_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_items_active_idx ON public.custom_order_items USING btree (is_active, item_name);


--
-- Name: custom_order_items_barcode_uidx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_items_barcode_uidx ON public.custom_order_items USING btree (barcode) WHERE ((barcode IS NOT NULL) AND (barcode <> ''::text));


--
-- Name: custom_order_items_brand_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_items_brand_idx ON public.custom_order_items USING btree (brand_id);


--
-- Name: custom_order_items_category_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_items_category_idx ON public.custom_order_items USING btree (category_id);


--
-- Name: custom_order_items_item_type_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_items_item_type_idx ON public.custom_order_items USING btree (item_type_id);


--
-- Name: custom_order_items_low_stock_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_items_low_stock_idx ON public.custom_order_items USING btree (is_active, quantity_on_hand, reorder_level);


--
-- Name: custom_order_items_sku_search_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_items_sku_search_idx ON public.custom_order_items USING btree (sku);


--
-- Name: custom_order_items_sku_uidx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_items_sku_uidx ON public.custom_order_items USING btree (upper(sku)) WHERE ((sku IS NOT NULL) AND (sku <> ''::text));


--
-- Name: custom_order_line_deliveries_custom_item_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_deliveries_custom_item_fk_idx ON public.custom_order_line_deliveries USING btree (custom_item_id) WHERE (custom_item_id IS NOT NULL);


--
-- Name: custom_order_line_deliveries_custom_variant_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_deliveries_custom_variant_fk_idx ON public.custom_order_line_deliveries USING btree (custom_variant_id) WHERE (custom_variant_id IS NOT NULL);


--
-- Name: custom_order_line_deliveries_delivered_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_deliveries_delivered_by_user_fk_idx ON public.custom_order_line_deliveries USING btree (delivered_by_user_id) WHERE (delivered_by_user_id IS NOT NULL);


--
-- Name: custom_order_line_deliveries_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_deliveries_device_idx ON public.custom_order_line_deliveries USING btree (device_id, delivered_at DESC);


--
-- Name: custom_order_line_deliveries_device_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_deliveries_device_name_idx ON public.custom_order_line_deliveries USING btree (device_name, delivered_at DESC);


--
-- Name: custom_order_line_deliveries_line_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_deliveries_line_idx ON public.custom_order_line_deliveries USING btree (custom_order_line_id, delivered_at DESC);


--
-- Name: custom_order_line_deliveries_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_deliveries_order_idx ON public.custom_order_line_deliveries USING btree (custom_order_id, delivered_at DESC);


--
-- Name: custom_order_line_deliveries_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_line_deliveries_sync_uuid_key ON public.custom_order_line_deliveries USING btree (sync_uuid);


--
-- Name: custom_order_line_print_addons_line_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_print_addons_line_idx ON public.custom_order_line_print_addons USING btree (custom_order_line_id, sort_order);


--
-- Name: custom_order_line_print_addons_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_line_print_addons_sync_uuid_key ON public.custom_order_line_print_addons USING btree (sync_uuid);


--
-- Name: custom_order_line_production_history_line_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_production_history_line_idx ON public.custom_order_line_production_history USING btree (custom_order_line_id, created_at DESC);


--
-- Name: custom_order_line_production_history_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_production_history_order_idx ON public.custom_order_line_production_history USING btree (custom_order_id, created_at DESC);


--
-- Name: custom_order_line_production_history_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_line_production_history_sync_uuid_key ON public.custom_order_line_production_history USING btree (sync_uuid);


--
-- Name: custom_order_line_returns_created_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_returns_created_by_user_fk_idx ON public.custom_order_line_returns USING btree (created_by_user_id) WHERE (created_by_user_id IS NOT NULL);


--
-- Name: custom_order_line_returns_custom_item_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_returns_custom_item_fk_idx ON public.custom_order_line_returns USING btree (custom_item_id) WHERE (custom_item_id IS NOT NULL);


--
-- Name: custom_order_line_returns_custom_variant_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_returns_custom_variant_fk_idx ON public.custom_order_line_returns USING btree (custom_variant_id) WHERE (custom_variant_id IS NOT NULL);


--
-- Name: custom_order_line_returns_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_returns_device_idx ON public.custom_order_line_returns USING btree (device_id, created_at DESC);


--
-- Name: custom_order_line_returns_device_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_returns_device_name_idx ON public.custom_order_line_returns USING btree (device_name, created_at DESC);


--
-- Name: custom_order_line_returns_line_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_returns_line_idx ON public.custom_order_line_returns USING btree (custom_order_line_id, created_at DESC);


--
-- Name: custom_order_line_returns_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_line_returns_order_idx ON public.custom_order_line_returns USING btree (custom_order_id, created_at DESC);


--
-- Name: custom_order_line_returns_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_line_returns_sync_uuid_key ON public.custom_order_line_returns USING btree (sync_uuid);


--
-- Name: custom_order_lines_custom_item_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_lines_custom_item_fk_idx ON public.custom_order_lines USING btree (custom_item_id) WHERE (custom_item_id IS NOT NULL);


--
-- Name: custom_order_lines_custom_variant_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_lines_custom_variant_fk_idx ON public.custom_order_lines USING btree (custom_variant_id) WHERE (custom_variant_id IS NOT NULL);


--
-- Name: custom_order_lines_delivered_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_lines_delivered_by_user_fk_idx ON public.custom_order_lines USING btree (delivered_by_user_id) WHERE (delivered_by_user_id IS NOT NULL);


--
-- Name: custom_order_lines_line_discount_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_lines_line_discount_by_user_fk_idx ON public.custom_order_lines USING btree (line_discount_by_user_id) WHERE (line_discount_by_user_id IS NOT NULL);


--
-- Name: custom_order_lines_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_lines_order_idx ON public.custom_order_lines USING btree (custom_order_id, sort_order);


--
-- Name: custom_order_lines_price_override_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_lines_price_override_by_user_fk_idx ON public.custom_order_lines USING btree (price_override_by_user_id) WHERE (price_override_by_user_id IS NOT NULL);


--
-- Name: custom_order_lines_print_material_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_lines_print_material_fk_idx ON public.custom_order_lines USING btree (print_material_id) WHERE (print_material_id IS NOT NULL);


--
-- Name: custom_order_lines_print_size_preset_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_lines_print_size_preset_fk_idx ON public.custom_order_lines USING btree (print_size_preset_id) WHERE (print_size_preset_id IS NOT NULL);


--
-- Name: custom_order_lines_production_updated_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_lines_production_updated_by_user_fk_idx ON public.custom_order_lines USING btree (production_updated_by_user_id) WHERE (production_updated_by_user_id IS NOT NULL);


--
-- Name: custom_order_lines_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_lines_sync_uuid_key ON public.custom_order_lines USING btree (sync_uuid);


--
-- Name: custom_order_payments_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_payments_device_idx ON public.custom_order_payments USING btree (device_id, created_at DESC);


--
-- Name: custom_order_payments_device_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_payments_device_name_idx ON public.custom_order_payments USING btree (device_name, created_at DESC);


--
-- Name: custom_order_payments_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_payments_order_idx ON public.custom_order_payments USING btree (custom_order_id, created_at);


--
-- Name: custom_order_payments_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_payments_sync_uuid_key ON public.custom_order_payments USING btree (sync_uuid);


--
-- Name: custom_order_payments_taken_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_payments_taken_by_user_fk_idx ON public.custom_order_payments USING btree (taken_by_user_id) WHERE (taken_by_user_id IS NOT NULL);


--
-- Name: custom_order_payments_voided_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_payments_voided_by_user_fk_idx ON public.custom_order_payments USING btree (voided_by_user_id) WHERE (voided_by_user_id IS NOT NULL);


--
-- Name: custom_order_print_materials_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_print_materials_updated_at_idx ON public.custom_order_print_materials USING btree (updated_at DESC);


--
-- Name: custom_order_print_size_presets_material_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_print_size_presets_material_idx ON public.custom_order_print_size_presets USING btree (print_material_id, is_active, preset_name);


--
-- Name: custom_order_print_size_presets_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_print_size_presets_updated_at_idx ON public.custom_order_print_size_presets USING btree (updated_at DESC);


--
-- Name: custom_order_status_history_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_status_history_device_idx ON public.custom_order_status_history USING btree (device_id, created_at DESC);


--
-- Name: custom_order_status_history_device_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_status_history_device_name_idx ON public.custom_order_status_history USING btree (device_name, created_at DESC);


--
-- Name: custom_order_status_history_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_status_history_order_idx ON public.custom_order_status_history USING btree (custom_order_id, created_at DESC);


--
-- Name: custom_order_status_history_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_order_status_history_sync_uuid_key ON public.custom_order_status_history USING btree (sync_uuid);


--
-- Name: custom_order_status_history_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_order_status_history_user_fk_idx ON public.custom_order_status_history USING btree (user_id) WHERE (user_id IS NOT NULL);


--
-- Name: custom_orders_assigned_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_orders_assigned_by_user_fk_idx ON public.custom_orders USING btree (assigned_by_user_id) WHERE (assigned_by_user_id IS NOT NULL);


--
-- Name: custom_orders_cancelled_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_orders_cancelled_by_user_fk_idx ON public.custom_orders USING btree (cancelled_by_user_id) WHERE (cancelled_by_user_id IS NOT NULL);


--
-- Name: custom_orders_customer_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_orders_customer_idx ON public.custom_orders USING btree (customer_id, created_at DESC);


--
-- Name: custom_orders_deposit_override_by_user_fk_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_orders_deposit_override_by_user_fk_idx ON public.custom_orders USING btree (deposit_override_by_user_id) WHERE (deposit_override_by_user_id IS NOT NULL);


--
-- Name: custom_orders_device_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_orders_device_name_idx ON public.custom_orders USING btree (device_name, created_at DESC);


--
-- Name: custom_orders_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_orders_location_idx ON public.custom_orders USING btree (location_id, created_at DESC);


--
-- Name: custom_orders_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX custom_orders_status_idx ON public.custom_orders USING btree (status, due_date);


--
-- Name: custom_orders_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX custom_orders_sync_uuid_key ON public.custom_orders USING btree (sync_uuid);


--
-- Name: customer_account_payment_allocations_custom_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_payment_allocations_custom_order_idx ON public.customer_account_payment_allocations USING btree (custom_order_id);


--
-- Name: customer_account_payment_allocations_invoice_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_payment_allocations_invoice_idx ON public.customer_account_payment_allocations USING btree (invoice_id);


--
-- Name: customer_account_payment_allocations_payment_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_payment_allocations_payment_idx ON public.customer_account_payment_allocations USING btree (payment_transaction_id);


--
-- Name: customer_account_payment_allocations_sale_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_payment_allocations_sale_idx ON public.customer_account_payment_allocations USING btree (sale_id);


--
-- Name: customer_account_payment_allocations_sales_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_payment_allocations_sales_order_idx ON public.customer_account_payment_allocations USING btree (sales_order_id);


--
-- Name: customer_account_payment_allocations_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX customer_account_payment_allocations_sync_uuid_key ON public.customer_account_payment_allocations USING btree (sync_uuid);


--
-- Name: customer_account_payment_allocations_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_payment_allocations_updated_at_idx ON public.customer_account_payment_allocations USING btree (updated_at DESC);


--
-- Name: customer_account_transactions_custom_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_transactions_custom_order_idx ON public.customer_account_transactions USING btree (custom_order_id);


--
-- Name: customer_account_transactions_customer_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_transactions_customer_created_idx ON public.customer_account_transactions USING btree (customer_id, created_at DESC);


--
-- Name: customer_account_transactions_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_transactions_device_idx ON public.customer_account_transactions USING btree (device_id, created_at DESC);


--
-- Name: customer_account_transactions_device_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_transactions_device_name_idx ON public.customer_account_transactions USING btree (device_name, created_at DESC);


--
-- Name: customer_account_transactions_invoice_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_transactions_invoice_idx ON public.customer_account_transactions USING btree (invoice_id);


--
-- Name: customer_account_transactions_location_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_transactions_location_created_idx ON public.customer_account_transactions USING btree (location_id, created_at DESC);


--
-- Name: customer_account_transactions_sales_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_transactions_sales_order_idx ON public.customer_account_transactions USING btree (sales_order_id);


--
-- Name: customer_account_transactions_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX customer_account_transactions_sync_uuid_key ON public.customer_account_transactions USING btree (sync_uuid);


--
-- Name: customer_account_transactions_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_account_transactions_updated_at_idx ON public.customer_account_transactions USING btree (updated_at DESC);


--
-- Name: customer_accounts_customer_type_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_accounts_customer_type_id_idx ON public.customer_accounts USING btree (customer_type_id);


--
-- Name: customer_accounts_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX customer_accounts_sync_uuid_key ON public.customer_accounts USING btree (sync_uuid);


--
-- Name: customer_accounts_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX customer_accounts_updated_at_idx ON public.customer_accounts USING btree (updated_at DESC);


--
-- Name: device_sessions_active_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX device_sessions_active_idx ON public.device_sessions USING btree (device_id, session_status, logout_time);


--
-- Name: device_sessions_device_login_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX device_sessions_device_login_idx ON public.device_sessions USING btree (device_id, login_time DESC);


--
-- Name: device_sessions_login_time_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX device_sessions_login_time_idx ON public.device_sessions USING btree (login_time DESC);


--
-- Name: devices_api_credential_hash_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX devices_api_credential_hash_idx ON public.devices USING btree (api_credential_hash) WHERE (api_credential_hash IS NOT NULL);


--
-- Name: devices_installation_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX devices_installation_id_idx ON public.devices USING btree (installation_id);


--
-- Name: devices_last_seen_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX devices_last_seen_idx ON public.devices USING btree (last_seen DESC);


--
-- Name: devices_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX devices_updated_at_idx ON public.devices USING btree (updated_at DESC);


--
-- Name: email_outbox_document_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email_outbox_document_idx ON public.email_outbox USING btree (document_type, document_id, created_at DESC);


--
-- Name: email_outbox_events_outbox_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email_outbox_events_outbox_idx ON public.email_outbox_events USING btree (email_outbox_id, created_at DESC);


--
-- Name: email_outbox_events_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX email_outbox_events_sync_uuid_key ON public.email_outbox_events USING btree (sync_uuid);


--
-- Name: email_outbox_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email_outbox_location_idx ON public.email_outbox USING btree (location_id, created_at DESC);


--
-- Name: email_outbox_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX email_outbox_status_idx ON public.email_outbox USING btree (status, created_at);


--
-- Name: email_outbox_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX email_outbox_sync_uuid_key ON public.email_outbox USING btree (sync_uuid);


--
-- Name: employee_payroll_bonuses_location_period_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_payroll_bonuses_location_period_idx ON public.employee_payroll_bonuses USING btree (location_id, pay_period_start, pay_period_end);


--
-- Name: employee_payroll_bonuses_period_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_payroll_bonuses_period_idx ON public.employee_payroll_bonuses USING btree (pay_period_start, pay_period_end, user_id);


--
-- Name: employee_payroll_settings_user_effective_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_payroll_settings_user_effective_idx ON public.employee_payroll_settings USING btree (user_id, effective_from DESC);


--
-- Name: employee_schedule_holidays_date_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_schedule_holidays_date_idx ON public.employee_schedule_holidays USING btree (holiday_date);


--
-- Name: employee_schedule_location_date_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_schedule_location_date_idx ON public.employee_schedule_assignments USING btree (location_id, work_date);


--
-- Name: employee_schedule_shifts_location_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX employee_schedule_shifts_location_name_idx ON public.employee_schedule_shifts USING btree (location_id, lower(TRIM(BOTH FROM shift_name)));


--
-- Name: employee_schedule_shifts_location_order_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_schedule_shifts_location_order_idx ON public.employee_schedule_shifts USING btree (location_id, is_active DESC, display_order, start_time);


--
-- Name: employee_schedule_user_date_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_schedule_user_date_idx ON public.employee_schedule_assignments USING btree (user_id, work_date);


--
-- Name: employee_time_clock_adjustments_clock_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_time_clock_adjustments_clock_idx ON public.employee_time_clock_adjustments USING btree (clock_id, created_at DESC);


--
-- Name: employee_time_clock_auto_due_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_time_clock_auto_due_idx ON public.employee_time_clock USING btree (auto_close_detection_at) WHERE ((clock_out IS NULL) AND auto_close_enabled_snapshot);


--
-- Name: employee_time_clock_auto_review_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_time_clock_auto_review_idx ON public.employee_time_clock USING btree (auto_clock_out_review_status, auto_clock_out_detected_at DESC) WHERE auto_clock_out;


--
-- Name: employee_time_clock_one_open_shift_per_user; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX employee_time_clock_one_open_shift_per_user ON public.employee_time_clock USING btree (user_id) WHERE (clock_out IS NULL);


--
-- Name: employee_time_clock_open_break_due_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_time_clock_open_break_due_idx ON public.employee_time_clock USING btree (break_start) WHERE ((clock_out IS NULL) AND (break_start IS NOT NULL) AND (break_end IS NULL));


--
-- Name: employee_time_clock_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_time_clock_updated_at_idx ON public.employee_time_clock USING btree (updated_at DESC);


--
-- Name: employee_time_clock_user_date_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX employee_time_clock_user_date_idx ON public.employee_time_clock USING btree (user_id, work_date DESC);


--
-- Name: expenses_created_by_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX expenses_created_by_user_idx ON public.expenses USING btree (created_by_user_id);


--
-- Name: expenses_location_date_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX expenses_location_date_idx ON public.expenses USING btree (location_id, expense_date DESC);


--
-- Name: expenses_source_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX expenses_source_unique_idx ON public.expenses USING btree (source_type, source_id) WHERE ((source_type IS NOT NULL) AND (source_id IS NOT NULL));


--
-- Name: held_cart_items_hold_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX held_cart_items_hold_idx ON public.held_cart_items USING btree (held_cart_id);


--
-- Name: held_cart_items_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX held_cart_items_updated_at_idx ON public.held_cart_items USING btree (updated_at DESC);


--
-- Name: held_carts_location_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX held_carts_location_status_idx ON public.held_carts USING btree (location_id, status, created_at DESC);


--
-- Name: held_carts_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX held_carts_updated_at_idx ON public.held_carts USING btree (updated_at DESC);


--
-- Name: idx_cash_drawer_assignments_assigned_by_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cash_drawer_assignments_assigned_by_user ON public.cash_drawer_device_assignments USING btree (assigned_by_user_id) WHERE (assigned_by_user_id IS NOT NULL);


--
-- Name: idx_cash_drawer_assignments_location_drawer; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cash_drawer_assignments_location_drawer ON public.cash_drawer_device_assignments USING btree (location_id, cash_drawer_id);


--
-- Name: idx_cash_drawer_assignments_unassigned_by_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cash_drawer_assignments_unassigned_by_user ON public.cash_drawer_device_assignments USING btree (unassigned_by_user_id) WHERE (unassigned_by_user_id IS NOT NULL);


--
-- Name: idx_cash_drawers_created_by_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cash_drawers_created_by_user ON public.cash_drawers USING btree (created_by_user_id) WHERE (created_by_user_id IS NOT NULL);


--
-- Name: idx_cash_drawers_updated_by_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cash_drawers_updated_by_user ON public.cash_drawers USING btree (updated_by_user_id) WHERE (updated_by_user_id IS NOT NULL);


--
-- Name: idx_custom_order_payments_cash_drawer_session_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_custom_order_payments_cash_drawer_session_created ON public.custom_order_payments USING btree (cash_drawer_session_id, created_at DESC) WHERE (cash_drawer_session_id IS NOT NULL);


--
-- Name: idx_custom_orders_cash_drawer_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_custom_orders_cash_drawer_created ON public.custom_orders USING btree (cash_drawer_id, created_at DESC) WHERE (cash_drawer_id IS NOT NULL);


--
-- Name: idx_custom_orders_cash_drawer_session_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_custom_orders_cash_drawer_session_created ON public.custom_orders USING btree (cash_drawer_session_id, created_at DESC) WHERE (cash_drawer_session_id IS NOT NULL);


--
-- Name: idx_customer_account_transactions_payment_id; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_customer_account_transactions_payment_id ON public.customer_account_transactions USING btree (payment_id) WHERE (payment_id IS NOT NULL);


--
-- Name: idx_customer_transactions_cash_drawer_session_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_customer_transactions_cash_drawer_session_created ON public.customer_account_transactions USING btree (cash_drawer_session_id, created_at DESC) WHERE (cash_drawer_session_id IS NOT NULL);


--
-- Name: idx_product_barcodes_product_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_product_barcodes_product_id ON public.product_barcodes USING btree (product_id);


--
-- Name: idx_sale_returns_cash_drawer_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sale_returns_cash_drawer_created ON public.sale_returns USING btree (cash_drawer_id, created_at DESC) WHERE (cash_drawer_id IS NOT NULL);


--
-- Name: idx_sale_returns_cash_drawer_session_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sale_returns_cash_drawer_session_created ON public.sale_returns USING btree (cash_drawer_session_id, created_at DESC) WHERE (cash_drawer_session_id IS NOT NULL);


--
-- Name: idx_sales_cash_drawer_session_created; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_sales_cash_drawer_session_created ON public.sales USING btree (cash_drawer_session_id, created_at DESC) WHERE (cash_drawer_session_id IS NOT NULL);


--
-- Name: image_asset_refs_asset_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX image_asset_refs_asset_idx ON public.image_asset_references USING btree (asset_id, active);


--
-- Name: image_assets_cloud_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX image_assets_cloud_idx ON public.image_assets USING btree (cloud_status, updated_at);


--
-- Name: image_assets_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX image_assets_status_idx ON public.image_assets USING btree (lifecycle_status, updated_at DESC);


--
-- Name: inventory_movements_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_movements_device_idx ON public.inventory_movements USING btree (device_id, created_at DESC);


--
-- Name: inventory_movements_invoice_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_movements_invoice_idx ON public.inventory_movements USING btree (invoice_id, created_at DESC);


--
-- Name: inventory_movements_sale_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_movements_sale_idx ON public.inventory_movements USING btree (sale_id, created_at DESC);


--
-- Name: inventory_movements_sale_return_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_movements_sale_return_idx ON public.inventory_movements USING btree (sale_return_id, created_at DESC);


--
-- Name: inventory_movements_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX inventory_movements_sync_uuid_key ON public.inventory_movements USING btree (sync_uuid);


--
-- Name: inventory_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX inventory_updated_at_idx ON public.inventory USING btree (updated_at DESC);


--
-- Name: invoice_audit_invoice_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_audit_invoice_idx ON public.invoice_audit_log USING btree (invoice_id, created_at DESC);


--
-- Name: invoice_audit_log_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX invoice_audit_log_sync_uuid_key ON public.invoice_audit_log USING btree (sync_uuid);


--
-- Name: invoice_delivery_events_invoice_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_delivery_events_invoice_idx ON public.invoice_delivery_events USING btree (invoice_id, created_at DESC);


--
-- Name: invoice_delivery_lines_event_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_delivery_lines_event_idx ON public.invoice_delivery_lines USING btree (invoice_delivery_event_id);


--
-- Name: invoice_delivery_lines_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX invoice_delivery_lines_sync_uuid_key ON public.invoice_delivery_lines USING btree (sync_uuid);


--
-- Name: invoice_lines_delivery_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_lines_delivery_idx ON public.invoice_lines USING btree (delivery_status, delivery_method);


--
-- Name: invoice_lines_invoice_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_lines_invoice_idx ON public.invoice_lines USING btree (invoice_id, sort_order);


--
-- Name: invoice_lines_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX invoice_lines_sync_uuid_key ON public.invoice_lines USING btree (sync_uuid);


--
-- Name: invoice_payments_invoice_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_payments_invoice_idx ON public.invoice_payments USING btree (invoice_id, created_at DESC);


--
-- Name: invoice_payments_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX invoice_payments_sync_uuid_key ON public.invoice_payments USING btree (sync_uuid);


--
-- Name: invoice_status_history_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX invoice_status_history_sync_uuid_key ON public.invoice_status_history USING btree (sync_uuid);


--
-- Name: invoice_status_invoice_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoice_status_invoice_idx ON public.invoice_status_history USING btree (invoice_id, created_at DESC);


--
-- Name: invoices_customer_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoices_customer_idx ON public.invoices USING btree (customer_id, created_at DESC);


--
-- Name: invoices_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoices_location_idx ON public.invoices USING btree (location_id, created_at DESC);


--
-- Name: invoices_quotation_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoices_quotation_idx ON public.invoices USING btree (quotation_id);


--
-- Name: invoices_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX invoices_status_idx ON public.invoices USING btree (status, invoice_date DESC);


--
-- Name: item_brands_name_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX item_brands_name_unique_idx ON public.item_brands USING btree (lower(name));


--
-- Name: item_brands_normalized_name_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX item_brands_normalized_name_unique_idx ON public.item_brands USING btree (upper(regexp_replace(btrim(name), '\s+'::text, ' '::text, 'g'::text)));


--
-- Name: item_brands_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX item_brands_updated_at_idx ON public.item_brands USING btree (updated_at DESC);


--
-- Name: item_types_category_name_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX item_types_category_name_unique_idx ON public.item_types USING btree (category_id, lower(name));


--
-- Name: item_types_normalized_name_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX item_types_normalized_name_unique_idx ON public.item_types USING btree (category_id, upper(regexp_replace(btrim(name), '\s+'::text, ' '::text, 'g'::text)));


--
-- Name: item_types_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX item_types_updated_at_idx ON public.item_types USING btree (updated_at DESC);


--
-- Name: lan_api_approvals_active_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lan_api_approvals_active_idx ON public.lan_api_approvals USING btree (device_id, expires_at) WHERE (consumed_at IS NULL);


--
-- Name: lan_api_idempotency_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lan_api_idempotency_created_idx ON public.lan_api_idempotency USING btree (created_at);


--
-- Name: lan_api_request_audit_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lan_api_request_audit_created_idx ON public.lan_api_request_audit USING btree (created_at DESC);


--
-- Name: lan_api_schedule_proposals_active_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lan_api_schedule_proposals_active_idx ON public.lan_api_schedule_proposals USING btree (device_id, user_id, location_id, expires_at) WHERE (consumed_at IS NULL);


--
-- Name: lan_api_sessions_device_active_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lan_api_sessions_device_active_idx ON public.lan_api_sessions USING btree (device_id, expires_at) WHERE (revoked_at IS NULL);


--
-- Name: lan_api_sessions_user_active_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX lan_api_sessions_user_active_idx ON public.lan_api_sessions USING btree (user_id, expires_at) WHERE (revoked_at IS NULL);


--
-- Name: locations_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX locations_updated_at_idx ON public.locations USING btree (updated_at DESC);


--
-- Name: maintenance_logs_machine_date_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX maintenance_logs_machine_date_idx ON public.maintenance_logs USING btree (machine_id, service_date DESC);


--
-- Name: maintenance_machine_parts_machine_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX maintenance_machine_parts_machine_idx ON public.maintenance_machine_parts USING btree (machine_id);


--
-- Name: maintenance_machine_parts_part_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX maintenance_machine_parts_part_idx ON public.maintenance_machine_parts USING btree (part_id);


--
-- Name: maintenance_machines_asset_tag_uidx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX maintenance_machines_asset_tag_uidx ON public.maintenance_machines USING btree (asset_tag) WHERE ((asset_tag IS NOT NULL) AND (asset_tag <> ''::text));


--
-- Name: maintenance_machines_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX maintenance_machines_location_idx ON public.maintenance_machines USING btree (location_id);


--
-- Name: maintenance_machines_next_service_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX maintenance_machines_next_service_idx ON public.maintenance_machines USING btree (next_service_date);


--
-- Name: maintenance_machines_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX maintenance_machines_status_idx ON public.maintenance_machines USING btree (status);


--
-- Name: maintenance_parts_part_number_uidx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX maintenance_parts_part_number_uidx ON public.maintenance_parts USING btree (part_number) WHERE ((part_number IS NOT NULL) AND (part_number <> ''::text));


--
-- Name: maintenance_parts_reorder_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX maintenance_parts_reorder_idx ON public.maintenance_parts USING btree (is_active, quantity_on_hand, reorder_point);


--
-- Name: maintenance_ticket_notes_ticket_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX maintenance_ticket_notes_ticket_idx ON public.maintenance_ticket_notes USING btree (ticket_id, created_at DESC);


--
-- Name: maintenance_ticket_parts_ticket_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX maintenance_ticket_parts_ticket_idx ON public.maintenance_ticket_parts USING btree (ticket_id);


--
-- Name: maintenance_tickets_machine_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX maintenance_tickets_machine_idx ON public.maintenance_tickets USING btree (machine_id, opened_at DESC);


--
-- Name: maintenance_tickets_resolved_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX maintenance_tickets_resolved_idx ON public.maintenance_tickets USING btree (status, resolved_at) WHERE (status = 'RESOLVED'::text);


--
-- Name: maintenance_tickets_status_priority_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX maintenance_tickets_status_priority_idx ON public.maintenance_tickets USING btree (status, priority, opened_at DESC);


--
-- Name: notification_user_state_dismissed_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notification_user_state_dismissed_idx ON public.notification_user_state USING btree (user_id, dismissed_until);


--
-- Name: notification_user_state_snoozed_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notification_user_state_snoozed_idx ON public.notification_user_state USING btree (user_id, snoozed_until);


--
-- Name: notification_user_state_updated_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX notification_user_state_updated_idx ON public.notification_user_state USING btree (updated_at DESC);


--
-- Name: other_income_created_by_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX other_income_created_by_user_idx ON public.other_income_entries USING btree (created_by_user_id);


--
-- Name: other_income_location_date_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX other_income_location_date_idx ON public.other_income_entries USING btree (location_id, income_date DESC);


--
-- Name: payroll_payments_employee_period_payment_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX payroll_payments_employee_period_payment_idx ON public.payroll_payments USING btree (user_id, pay_period_start, pay_period_end, payment_number);


--
-- Name: payroll_payments_location_paid_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX payroll_payments_location_paid_idx ON public.payroll_payments USING btree (location_id, paid_at DESC);


--
-- Name: product_barcodes_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX product_barcodes_updated_at_idx ON public.product_barcodes USING btree (updated_at DESC);


--
-- Name: product_shelf_assignments_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX product_shelf_assignments_updated_at_idx ON public.product_shelf_assignments USING btree (updated_at DESC);


--
-- Name: products_size_search_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX products_size_search_idx ON public.products USING btree (size);


--
-- Name: products_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX products_updated_at_idx ON public.products USING btree (updated_at DESC);


--
-- Name: products_vendor_id_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX products_vendor_id_idx ON public.products USING btree (vendor_id);


--
-- Name: quotation_audit_log_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX quotation_audit_log_sync_uuid_key ON public.quotation_audit_log USING btree (sync_uuid);


--
-- Name: quotation_audit_quotation_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX quotation_audit_quotation_idx ON public.quotation_audit_log USING btree (quotation_id, created_at DESC);


--
-- Name: quotation_lines_quotation_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX quotation_lines_quotation_idx ON public.quotation_lines USING btree (quotation_id, sort_order);


--
-- Name: quotation_lines_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX quotation_lines_sync_uuid_key ON public.quotation_lines USING btree (sync_uuid);


--
-- Name: quotation_status_history_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX quotation_status_history_sync_uuid_key ON public.quotation_status_history USING btree (sync_uuid);


--
-- Name: quotation_status_quotation_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX quotation_status_quotation_idx ON public.quotation_status_history USING btree (quotation_id, created_at DESC);


--
-- Name: quotations_customer_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX quotations_customer_idx ON public.quotations USING btree (customer_id, created_at DESC);


--
-- Name: quotations_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX quotations_location_idx ON public.quotations USING btree (location_id, created_at DESC);


--
-- Name: quotations_status_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX quotations_status_idx ON public.quotations USING btree (status, valid_until);


--
-- Name: role_mobile_permissions_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX role_mobile_permissions_updated_at_idx ON public.role_mobile_permissions USING btree (updated_at DESC);


--
-- Name: role_permissions_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX role_permissions_updated_at_idx ON public.role_permissions USING btree (updated_at DESC);


--
-- Name: roles_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX roles_updated_at_idx ON public.roles USING btree (updated_at DESC);


--
-- Name: sale_audit_log_action_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_audit_log_action_idx ON public.sale_audit_log USING btree (action_type, created_at DESC);


--
-- Name: sale_audit_log_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_audit_log_device_idx ON public.sale_audit_log USING btree (device_id, created_at DESC);


--
-- Name: sale_audit_log_item_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_audit_log_item_idx ON public.sale_audit_log USING btree (sale_item_id, created_at DESC);


--
-- Name: sale_audit_log_location_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_audit_log_location_idx ON public.sale_audit_log USING btree (location_id, created_at DESC);


--
-- Name: sale_audit_log_product_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_audit_log_product_idx ON public.sale_audit_log USING btree (product_id, created_at DESC);


--
-- Name: sale_audit_log_return_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_audit_log_return_idx ON public.sale_audit_log USING btree (return_id, created_at DESC);


--
-- Name: sale_audit_log_sale_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_audit_log_sale_idx ON public.sale_audit_log USING btree (sale_id, created_at DESC);


--
-- Name: sale_audit_log_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX sale_audit_log_sync_uuid_key ON public.sale_audit_log USING btree (sync_uuid);


--
-- Name: sale_audit_log_user_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_audit_log_user_idx ON public.sale_audit_log USING btree (user_id, created_at DESC);


--
-- Name: sale_items_product_sale_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_items_product_sale_idx ON public.sale_items USING btree (product_id, sale_id);


--
-- Name: sale_items_sale_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_items_sale_idx ON public.sale_items USING btree (sale_id);


--
-- Name: sale_items_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX sale_items_sync_uuid_key ON public.sale_items USING btree (sync_uuid);


--
-- Name: sale_return_items_sale_item_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_return_items_sale_item_idx ON public.sale_return_items USING btree (sale_item_id);


--
-- Name: sale_return_items_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX sale_return_items_sync_uuid_key ON public.sale_return_items USING btree (sync_uuid);


--
-- Name: sale_returns_cross_store_request_uidx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX sale_returns_cross_store_request_uidx ON public.sale_returns USING btree (cross_store_request_id) WHERE (cross_store_request_id IS NOT NULL);


--
-- Name: sale_returns_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_returns_device_idx ON public.sale_returns USING btree (device_id, created_at DESC);


--
-- Name: sale_returns_device_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_returns_device_name_idx ON public.sale_returns USING btree (device_name, created_at DESC);


--
-- Name: sale_returns_location_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_returns_location_created_idx ON public.sale_returns USING btree (location_id, created_at DESC);


--
-- Name: sale_returns_sale_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sale_returns_sale_idx ON public.sale_returns USING btree (sale_id);


--
-- Name: sale_returns_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX sale_returns_sync_uuid_key ON public.sale_returns USING btree (sync_uuid);


--
-- Name: sales_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sales_device_idx ON public.sales USING btree (device_id, created_at DESC);


--
-- Name: sales_device_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sales_device_name_idx ON public.sales USING btree (device_name, created_at DESC);


--
-- Name: sales_receipt_number_uidx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX sales_receipt_number_uidx ON public.sales USING btree (receipt_number) WHERE (COALESCE(receipt_number, ''::text) <> ''::text);


--
-- Name: sales_sync_uuid_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX sales_sync_uuid_key ON public.sales USING btree (sync_uuid);


--
-- Name: security_audit_events_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX security_audit_events_created_idx ON public.security_audit_events USING btree (created_at DESC);


--
-- Name: security_audit_events_device_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX security_audit_events_device_idx ON public.security_audit_events USING btree (device_id, created_at DESC);


--
-- Name: shelf_locations_id_location_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX shelf_locations_id_location_unique_idx ON public.shelf_locations USING btree (shelf_location_id, location_id);


--
-- Name: shelf_locations_location_name_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX shelf_locations_location_name_unique_idx ON public.shelf_locations USING btree (location_id, lower(name));


--
-- Name: shelf_locations_normalized_name_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX shelf_locations_normalized_name_unique_idx ON public.shelf_locations USING btree (location_id, upper(regexp_replace(btrim(name), '\s+'::text, ' '::text, 'g'::text)));


--
-- Name: shelf_locations_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX shelf_locations_updated_at_idx ON public.shelf_locations USING btree (updated_at DESC);


--
-- Name: store_transfer_items_transfer_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX store_transfer_items_transfer_idx ON public.store_transfer_items USING btree (transfer_id);


--
-- Name: store_transfers_created_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX store_transfers_created_at_idx ON public.store_transfers USING btree (created_at DESC);


--
-- Name: sync_audit_log_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_audit_log_created_idx ON public.sync_audit_log USING btree (created_at DESC);


--
-- Name: sync_audit_log_table_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_audit_log_table_created_idx ON public.sync_audit_log USING btree (table_name, created_at DESC);


--
-- Name: sync_conflicts_status_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_conflicts_status_created_idx ON public.sync_conflicts USING btree (status, created_at);


--
-- Name: sync_cross_store_inventory_store_name_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_cross_store_inventory_store_name_idx ON public.sync_cross_store_inventory_cache USING btree (source_location_id, product_name);


--
-- Name: sync_cross_store_return_items_sale_item_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_cross_store_return_items_sale_item_idx ON public.sync_cross_store_return_items_cache USING btree (source_location_id, sale_item_id);


--
-- Name: sync_cross_store_returns_sale_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_cross_store_returns_sale_idx ON public.sync_cross_store_returns_cache USING btree (source_location_id, sale_id);


--
-- Name: sync_cross_store_sale_items_sale_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_cross_store_sale_items_sale_idx ON public.sync_cross_store_sale_items_cache USING btree (source_location_id, sale_id);


--
-- Name: sync_cross_store_sales_search_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_cross_store_sales_search_idx ON public.sync_cross_store_sales_cache USING btree (source_location_id, source_created_at DESC, receipt_number);


--
-- Name: sync_inbox_status_sequence_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_inbox_status_sequence_idx ON public.sync_inbox USING btree (status, cloud_sequence);


--
-- Name: sync_outbox_status_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_outbox_status_created_idx ON public.sync_outbox USING btree (status, created_at);


--
-- Name: sync_tombstones_deleted_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_tombstones_deleted_idx ON public.sync_tombstones USING btree (deleted_at DESC);


--
-- Name: sync_tombstones_table_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_tombstones_table_idx ON public.sync_tombstones USING btree (table_name);


--
-- Name: sync_transfer_metrics_created_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX sync_transfer_metrics_created_idx ON public.sync_transfer_metrics USING btree (created_at DESC);


--
-- Name: user_locations_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_locations_updated_at_idx ON public.user_locations USING btree (updated_at DESC);


--
-- Name: users_badge_normalized_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX users_badge_normalized_idx ON public.users USING btree (upper(regexp_replace(COALESCE(badge_id, ''::text), '[^a-zA-Z0-9]'::text, ''::text, 'g'::text)));


--
-- Name: users_updated_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX users_updated_at_idx ON public.users USING btree (updated_at DESC);


--
-- Name: vendors_name_unique_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX vendors_name_unique_idx ON public.vendors USING btree (lower(name));


--
-- Name: customer_accounts assign_customer_account_number_before_insert; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER assign_customer_account_number_before_insert BEFORE INSERT ON public.customer_accounts FOR EACH ROW EXECUTE FUNCTION public.assign_customer_account_number();


--
-- Name: balance_sheet_submission_revisions balance_sheet_revisions_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER balance_sheet_revisions_immutable BEFORE DELETE OR UPDATE ON public.balance_sheet_submission_revisions FOR EACH ROW EXECUTE FUNCTION public.prevent_balance_sheet_revision_changes();


--
-- Name: cash_drawer_device_assignments cash_drawer_device_assignments_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER cash_drawer_device_assignments_set_updated_at BEFORE INSERT OR UPDATE ON public.cash_drawer_device_assignments FOR EACH ROW EXECUTE FUNCTION public.set_cash_drawer_device_assignments_updated_at();


--
-- Name: cash_drawers cash_drawers_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER cash_drawers_set_updated_at BEFORE INSERT OR UPDATE ON public.cash_drawers FOR EACH ROW EXECUTE FUNCTION public.set_cash_drawers_updated_at();


--
-- Name: custom_order_item_variants custom_order_item_variants_refresh_totals; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER custom_order_item_variants_refresh_totals AFTER INSERT OR DELETE OR UPDATE ON public.custom_order_item_variants FOR EACH ROW EXECUTE FUNCTION public.refresh_custom_order_item_variant_totals();


--
-- Name: custom_order_item_variants custom_order_item_variants_set_sku; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER custom_order_item_variants_set_sku BEFORE INSERT OR UPDATE OF variant_name, custom_item_id ON public.custom_order_item_variants FOR EACH ROW EXECUTE FUNCTION public.set_custom_order_variant_sku();


--
-- Name: custom_order_items custom_order_items_refresh_variant_skus; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER custom_order_items_refresh_variant_skus AFTER INSERT OR UPDATE OF item_name ON public.custom_order_items FOR EACH ROW EXECUTE FUNCTION public.refresh_custom_order_variant_skus_for_item();


--
-- Name: custom_order_items custom_order_items_set_sku; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER custom_order_items_set_sku BEFORE INSERT OR UPDATE OF item_name ON public.custom_order_items FOR EACH ROW EXECUTE FUNCTION public.set_custom_order_item_sku();


--
-- Name: custom_order_print_materials custom_order_print_materials_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER custom_order_print_materials_set_updated_at BEFORE INSERT OR UPDATE ON public.custom_order_print_materials FOR EACH ROW EXECUTE FUNCTION public.set_custom_order_print_materials_updated_at();


--
-- Name: custom_order_print_size_presets custom_order_print_size_presets_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER custom_order_print_size_presets_set_updated_at BEFORE INSERT OR UPDATE ON public.custom_order_print_size_presets FOR EACH ROW EXECUTE FUNCTION public.set_custom_order_print_size_presets_updated_at();


--
-- Name: customer_account_payment_allocations customer_account_payment_allocations_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER customer_account_payment_allocations_set_updated_at BEFORE INSERT OR UPDATE ON public.customer_account_payment_allocations FOR EACH ROW EXECUTE FUNCTION public.set_customer_account_payment_allocations_updated_at();


--
-- Name: customer_account_transactions customer_account_transactions_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER customer_account_transactions_set_updated_at BEFORE INSERT OR UPDATE ON public.customer_account_transactions FOR EACH ROW EXECUTE FUNCTION public.set_customer_account_transactions_updated_at();


--
-- Name: customer_accounts customer_accounts_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER customer_accounts_set_updated_at BEFORE INSERT OR UPDATE ON public.customer_accounts FOR EACH ROW EXECUTE FUNCTION public.set_customer_accounts_updated_at();


--
-- Name: device_sessions device_sessions_refresh_count; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER device_sessions_refresh_count AFTER INSERT OR DELETE OR UPDATE ON public.device_sessions FOR EACH ROW EXECUTE FUNCTION public.refresh_device_session_count();


--
-- Name: devices devices_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER devices_set_updated_at BEFORE INSERT OR UPDATE ON public.devices FOR EACH ROW EXECUTE FUNCTION public.set_devices_updated_at();


--
-- Name: email_outbox email_outbox_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER email_outbox_set_updated_at BEFORE INSERT OR UPDATE ON public.email_outbox FOR EACH ROW EXECUTE FUNCTION public.set_email_outbox_updated_at();


--
-- Name: employee_payroll_settings employee_payroll_settings_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER employee_payroll_settings_set_updated_at BEFORE UPDATE ON public.employee_payroll_settings FOR EACH ROW EXECUTE FUNCTION public.set_employee_payroll_settings_updated_at();


--
-- Name: employee_schedule_assignments employee_schedule_assignments_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER employee_schedule_assignments_set_updated_at BEFORE INSERT OR UPDATE ON public.employee_schedule_assignments FOR EACH ROW EXECUTE FUNCTION public.set_employee_schedule_assignments_updated_at();


--
-- Name: employee_schedule_holidays employee_schedule_holidays_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER employee_schedule_holidays_set_updated_at BEFORE INSERT OR UPDATE ON public.employee_schedule_holidays FOR EACH ROW EXECUTE FUNCTION public.set_employee_schedule_holidays_updated_at();


--
-- Name: employee_schedule_shifts employee_schedule_shifts_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER employee_schedule_shifts_set_updated_at BEFORE INSERT OR UPDATE ON public.employee_schedule_shifts FOR EACH ROW EXECUTE FUNCTION public.set_employee_schedule_shifts_updated_at();


--
-- Name: employee_time_clock_adjustments employee_time_clock_adjustments_append_only; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER employee_time_clock_adjustments_append_only BEFORE DELETE OR UPDATE ON public.employee_time_clock_adjustments FOR EACH ROW EXECUTE FUNCTION public.prevent_employee_time_clock_adjustment_changes();


--
-- Name: employee_time_clock employee_time_clock_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER employee_time_clock_set_updated_at BEFORE INSERT OR UPDATE ON public.employee_time_clock FOR EACH ROW EXECUTE FUNCTION public.set_employee_time_clock_updated_at();


--
-- Name: held_cart_items held_cart_items_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER held_cart_items_set_updated_at BEFORE INSERT OR UPDATE ON public.held_cart_items FOR EACH ROW EXECUTE FUNCTION public.set_held_cart_items_updated_at();


--
-- Name: held_carts held_carts_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER held_carts_set_updated_at BEFORE INSERT OR UPDATE ON public.held_carts FOR EACH ROW EXECUTE FUNCTION public.set_held_carts_updated_at();


--
-- Name: image_asset_references image_asset_references_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER image_asset_references_set_updated_at BEFORE INSERT OR UPDATE ON public.image_asset_references FOR EACH ROW EXECUTE FUNCTION public.set_image_asset_references_updated_at();


--
-- Name: image_assets image_assets_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER image_assets_set_updated_at BEFORE INSERT OR UPDATE ON public.image_assets FOR EACH ROW EXECUTE FUNCTION public.set_image_assets_updated_at();


--
-- Name: inventory inventory_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER inventory_set_updated_at BEFORE INSERT OR UPDATE ON public.inventory FOR EACH ROW EXECUTE FUNCTION public.set_inventory_updated_at();


--
-- Name: invoice_delivery_events invoice_delivery_events_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER invoice_delivery_events_set_updated_at BEFORE INSERT OR UPDATE ON public.invoice_delivery_events FOR EACH ROW EXECUTE FUNCTION public.set_quotation_invoice_updated_at();


--
-- Name: invoice_lines invoice_lines_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER invoice_lines_set_updated_at BEFORE INSERT OR UPDATE ON public.invoice_lines FOR EACH ROW EXECUTE FUNCTION public.set_quotation_invoice_updated_at();


--
-- Name: invoice_payments invoice_payments_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER invoice_payments_set_updated_at BEFORE INSERT OR UPDATE ON public.invoice_payments FOR EACH ROW EXECUTE FUNCTION public.set_quotation_invoice_updated_at();


--
-- Name: invoices invoices_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER invoices_set_updated_at BEFORE INSERT OR UPDATE ON public.invoices FOR EACH ROW EXECUTE FUNCTION public.set_quotation_invoice_updated_at();


--
-- Name: item_brands item_brands_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER item_brands_set_updated_at BEFORE INSERT OR UPDATE ON public.item_brands FOR EACH ROW EXECUTE FUNCTION public.set_item_brands_updated_at();


--
-- Name: item_types item_types_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER item_types_set_updated_at BEFORE INSERT OR UPDATE ON public.item_types FOR EACH ROW EXECUTE FUNCTION public.set_item_types_updated_at();


--
-- Name: lan_api_request_audit lan_api_request_audit_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER lan_api_request_audit_immutable BEFORE DELETE OR UPDATE ON public.lan_api_request_audit FOR EACH ROW EXECUTE FUNCTION public.reject_lan_api_audit_mutation();


--
-- Name: locations locations_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER locations_set_updated_at BEFORE INSERT OR UPDATE ON public.locations FOR EACH ROW EXECUTE FUNCTION public.set_locations_updated_at();


--
-- Name: maintenance_logs maintenance_logs_refresh_machine_last_service_date; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER maintenance_logs_refresh_machine_last_service_date AFTER INSERT OR UPDATE OF machine_id, service_date ON public.maintenance_logs FOR EACH ROW EXECUTE FUNCTION public.refresh_machine_last_service_date();


--
-- Name: product_barcodes product_barcodes_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER product_barcodes_set_updated_at BEFORE INSERT OR UPDATE ON public.product_barcodes FOR EACH ROW EXECUTE FUNCTION public.set_product_barcodes_updated_at();


--
-- Name: product_shelf_assignments product_shelf_assignments_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER product_shelf_assignments_set_updated_at BEFORE INSERT OR UPDATE ON public.product_shelf_assignments FOR EACH ROW EXECUTE FUNCTION public.set_product_shelf_assignments_updated_at();


--
-- Name: products products_set_sku; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER products_set_sku BEFORE INSERT ON public.products FOR EACH ROW EXECUTE FUNCTION public.set_product_sku();


--
-- Name: products products_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER products_set_updated_at BEFORE INSERT OR UPDATE ON public.products FOR EACH ROW EXECUTE FUNCTION public.set_products_updated_at();


--
-- Name: quotation_lines quotation_lines_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER quotation_lines_set_updated_at BEFORE INSERT OR UPDATE ON public.quotation_lines FOR EACH ROW EXECUTE FUNCTION public.set_quotation_invoice_updated_at();


--
-- Name: quotations quotations_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER quotations_set_updated_at BEFORE INSERT OR UPDATE ON public.quotations FOR EACH ROW EXECUTE FUNCTION public.set_quotation_invoice_updated_at();


--
-- Name: role_mobile_permissions role_mobile_permissions_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER role_mobile_permissions_set_updated_at BEFORE INSERT OR UPDATE ON public.role_mobile_permissions FOR EACH ROW EXECUTE FUNCTION public.set_role_mobile_permissions_updated_at();


--
-- Name: role_permissions role_permissions_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER role_permissions_set_updated_at BEFORE INSERT OR UPDATE ON public.role_permissions FOR EACH ROW EXECUTE FUNCTION public.set_role_permissions_updated_at();


--
-- Name: roles roles_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER roles_set_updated_at BEFORE INSERT OR UPDATE ON public.roles FOR EACH ROW EXECUTE FUNCTION public.set_roles_updated_at();


--
-- Name: sale_items sale_items_update_delete_audit; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER sale_items_update_delete_audit AFTER DELETE OR UPDATE ON public.sale_items FOR EACH ROW EXECUTE FUNCTION public.record_sale_table_audit();


--
-- Name: sale_return_items sale_return_items_update_delete_audit; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER sale_return_items_update_delete_audit AFTER DELETE OR UPDATE ON public.sale_return_items FOR EACH ROW EXECUTE FUNCTION public.record_sale_table_audit();


--
-- Name: sale_returns sale_returns_update_delete_audit; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER sale_returns_update_delete_audit AFTER DELETE OR UPDATE ON public.sale_returns FOR EACH ROW EXECUTE FUNCTION public.record_sale_table_audit();


--
-- Name: sales sales_update_delete_audit; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER sales_update_delete_audit AFTER DELETE OR UPDATE ON public.sales FOR EACH ROW EXECUTE FUNCTION public.record_sale_table_audit();


--
-- Name: security_audit_events security_audit_events_immutable; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER security_audit_events_immutable BEFORE DELETE OR UPDATE ON public.security_audit_events FOR EACH ROW EXECUTE FUNCTION public.reject_security_audit_mutation();


--
-- Name: shelf_locations shelf_locations_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER shelf_locations_set_updated_at BEFORE INSERT OR UPDATE ON public.shelf_locations FOR EACH ROW EXECUTE FUNCTION public.set_shelf_locations_updated_at();


--
-- Name: sale_audit_log suppress_duplicate_sale_db_update_audit; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER suppress_duplicate_sale_db_update_audit BEFORE INSERT ON public.sale_audit_log FOR EACH ROW EXECUTE FUNCTION public.suppress_duplicate_sale_db_update_audit();


--
-- Name: time_clock_auto_close_settings time_clock_auto_close_settings_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER time_clock_auto_close_settings_updated_at BEFORE UPDATE ON public.time_clock_auto_close_settings FOR EACH ROW EXECUTE FUNCTION public.set_time_clock_auto_close_settings_updated_at();


--
-- Name: user_locations user_locations_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER user_locations_set_updated_at BEFORE INSERT OR UPDATE ON public.user_locations FOR EACH ROW EXECUTE FUNCTION public.set_user_locations_updated_at();


--
-- Name: users users_set_updated_at; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER users_set_updated_at BEFORE INSERT OR UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION public.set_users_updated_at();


--
-- Name: app_releases app_releases_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_releases
    ADD CONSTRAINT app_releases_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: balance_sheet_bf_overrides balance_sheet_bf_overrides_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_bf_overrides
    ADD CONSTRAINT balance_sheet_bf_overrides_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: balance_sheet_bf_overrides balance_sheet_bf_overrides_updated_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_bf_overrides
    ADD CONSTRAINT balance_sheet_bf_overrides_updated_by_user_id_fkey FOREIGN KEY (updated_by_user_id) REFERENCES public.users(user_id);


--
-- Name: balance_sheet_submission_revisions balance_sheet_submission_revis_balance_sheet_submission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_submission_revisions
    ADD CONSTRAINT balance_sheet_submission_revis_balance_sheet_submission_id_fkey FOREIGN KEY (balance_sheet_submission_id) REFERENCES public.balance_sheet_submissions(balance_sheet_submission_id);


--
-- Name: balance_sheet_submission_revisions balance_sheet_submission_revisions_changed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_submission_revisions
    ADD CONSTRAINT balance_sheet_submission_revisions_changed_by_user_id_fkey FOREIGN KEY (changed_by_user_id) REFERENCES public.users(user_id);


--
-- Name: balance_sheet_submission_revisions balance_sheet_submission_revisions_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_submission_revisions
    ADD CONSTRAINT balance_sheet_submission_revisions_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: balance_sheet_submissions balance_sheet_submissions_last_edited_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_submissions
    ADD CONSTRAINT balance_sheet_submissions_last_edited_by_user_id_fkey FOREIGN KEY (last_edited_by_user_id) REFERENCES public.users(user_id);


--
-- Name: balance_sheet_submissions balance_sheet_submissions_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_submissions
    ADD CONSTRAINT balance_sheet_submissions_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: balance_sheet_submissions balance_sheet_submissions_submitted_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.balance_sheet_submissions
    ADD CONSTRAINT balance_sheet_submissions_submitted_by_user_id_fkey FOREIGN KEY (submitted_by_user_id) REFERENCES public.users(user_id);


--
-- Name: bank_transactions bank_transactions_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_transactions
    ADD CONSTRAINT bank_transactions_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: bank_transactions bank_transactions_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bank_transactions
    ADD CONSTRAINT bank_transactions_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: cash_drawer_device_assignments cash_drawer_device_assignments_assigned_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_device_assignments
    ADD CONSTRAINT cash_drawer_device_assignments_assigned_by_user_id_fkey FOREIGN KEY (assigned_by_user_id) REFERENCES public.users(user_id);


--
-- Name: cash_drawer_device_assignments cash_drawer_device_assignments_cash_drawer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_device_assignments
    ADD CONSTRAINT cash_drawer_device_assignments_cash_drawer_id_fkey FOREIGN KEY (cash_drawer_id) REFERENCES public.cash_drawers(cash_drawer_id) ON DELETE CASCADE;


--
-- Name: cash_drawer_device_assignments cash_drawer_device_assignments_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_device_assignments
    ADD CONSTRAINT cash_drawer_device_assignments_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id) ON DELETE CASCADE;


--
-- Name: cash_drawer_device_assignments cash_drawer_device_assignments_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_device_assignments
    ADD CONSTRAINT cash_drawer_device_assignments_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: cash_drawer_device_assignments cash_drawer_device_assignments_unassigned_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_device_assignments
    ADD CONSTRAINT cash_drawer_device_assignments_unassigned_by_user_id_fkey FOREIGN KEY (unassigned_by_user_id) REFERENCES public.users(user_id);


--
-- Name: cash_drawer_handovers cash_drawer_handovers_cash_drawer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_handovers
    ADD CONSTRAINT cash_drawer_handovers_cash_drawer_id_fkey FOREIGN KEY (cash_drawer_id) REFERENCES public.cash_drawers(cash_drawer_id);


--
-- Name: cash_drawer_handovers cash_drawer_handovers_cash_drawer_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_handovers
    ADD CONSTRAINT cash_drawer_handovers_cash_drawer_session_id_fkey FOREIGN KEY (cash_drawer_session_id) REFERENCES public.cash_drawer_sessions(cash_drawer_session_id) ON DELETE CASCADE;


--
-- Name: cash_drawer_handovers cash_drawer_handovers_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_handovers
    ADD CONSTRAINT cash_drawer_handovers_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id);


--
-- Name: cash_drawer_handovers cash_drawer_handovers_from_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_handovers
    ADD CONSTRAINT cash_drawer_handovers_from_user_id_fkey FOREIGN KEY (from_user_id) REFERENCES public.users(user_id);


--
-- Name: cash_drawer_handovers cash_drawer_handovers_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_handovers
    ADD CONSTRAINT cash_drawer_handovers_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: cash_drawer_handovers cash_drawer_handovers_to_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_handovers
    ADD CONSTRAINT cash_drawer_handovers_to_user_id_fkey FOREIGN KEY (to_user_id) REFERENCES public.users(user_id);


--
-- Name: cash_drawer_sessions cash_drawer_sessions_balanced_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_sessions
    ADD CONSTRAINT cash_drawer_sessions_balanced_by_user_id_fkey FOREIGN KEY (balanced_by_user_id) REFERENCES public.users(user_id);


--
-- Name: cash_drawer_sessions cash_drawer_sessions_cash_drawer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_sessions
    ADD CONSTRAINT cash_drawer_sessions_cash_drawer_id_fkey FOREIGN KEY (cash_drawer_id) REFERENCES public.cash_drawers(cash_drawer_id);


--
-- Name: cash_drawer_sessions cash_drawer_sessions_closed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_sessions
    ADD CONSTRAINT cash_drawer_sessions_closed_by_user_id_fkey FOREIGN KEY (closed_by_user_id) REFERENCES public.users(user_id);


--
-- Name: cash_drawer_sessions cash_drawer_sessions_current_cashier_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_sessions
    ADD CONSTRAINT cash_drawer_sessions_current_cashier_user_id_fkey FOREIGN KEY (current_cashier_user_id) REFERENCES public.users(user_id);


--
-- Name: cash_drawer_sessions cash_drawer_sessions_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_sessions
    ADD CONSTRAINT cash_drawer_sessions_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id);


--
-- Name: cash_drawer_sessions cash_drawer_sessions_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_sessions
    ADD CONSTRAINT cash_drawer_sessions_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: cash_drawer_sessions cash_drawer_sessions_main_cashier_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_sessions
    ADD CONSTRAINT cash_drawer_sessions_main_cashier_user_id_fkey FOREIGN KEY (main_cashier_user_id) REFERENCES public.users(user_id);


--
-- Name: cash_drawer_sessions cash_drawer_sessions_opened_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawer_sessions
    ADD CONSTRAINT cash_drawer_sessions_opened_by_user_id_fkey FOREIGN KEY (opened_by_user_id) REFERENCES public.users(user_id);


--
-- Name: cash_drawers cash_drawers_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawers
    ADD CONSTRAINT cash_drawers_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: cash_drawers cash_drawers_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawers
    ADD CONSTRAINT cash_drawers_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: cash_drawers cash_drawers_updated_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_drawers
    ADD CONSTRAINT cash_drawers_updated_by_user_id_fkey FOREIGN KEY (updated_by_user_id) REFERENCES public.users(user_id);


--
-- Name: change_basket_updates change_basket_updates_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.change_basket_updates
    ADD CONSTRAINT change_basket_updates_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id);


--
-- Name: change_basket_updates change_basket_updates_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.change_basket_updates
    ADD CONSTRAINT change_basket_updates_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: change_basket_updates change_basket_updates_updated_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.change_basket_updates
    ADD CONSTRAINT change_basket_updates_updated_by_user_id_fkey FOREIGN KEY (updated_by_user_id) REFERENCES public.users(user_id);


--
-- Name: cheque_bank_deposits cheque_bank_deposits_deposited_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cheque_bank_deposits
    ADD CONSTRAINT cheque_bank_deposits_deposited_by_user_id_fkey FOREIGN KEY (deposited_by_user_id) REFERENCES public.users(user_id);


--
-- Name: cheque_bank_deposits cheque_bank_deposits_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cheque_bank_deposits
    ADD CONSTRAINT cheque_bank_deposits_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: company_customization company_customization_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.company_customization
    ADD CONSTRAINT company_customization_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: cross_store_refund_lines cross_store_refund_lines_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cross_store_refund_lines
    ADD CONSTRAINT cross_store_refund_lines_request_id_fkey FOREIGN KEY (request_id) REFERENCES public.cross_store_refund_requests(request_id) ON DELETE CASCADE;


--
-- Name: cross_store_refund_reconciliation cross_store_refund_reconciliation_request_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cross_store_refund_reconciliation
    ADD CONSTRAINT cross_store_refund_reconciliation_request_id_fkey FOREIGN KEY (request_id) REFERENCES public.cross_store_refund_requests(request_id);


--
-- Name: custom_order_audit_log custom_order_audit_log_custom_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_audit_log
    ADD CONSTRAINT custom_order_audit_log_custom_order_id_fkey FOREIGN KEY (custom_order_id) REFERENCES public.custom_orders(custom_order_id) ON DELETE CASCADE;


--
-- Name: custom_order_audit_log custom_order_audit_log_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_audit_log
    ADD CONSTRAINT custom_order_audit_log_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: custom_order_inventory_reservations custom_order_inventory_reservations_custom_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_inventory_reservations
    ADD CONSTRAINT custom_order_inventory_reservations_custom_item_id_fkey FOREIGN KEY (custom_item_id) REFERENCES public.custom_order_items(custom_item_id);


--
-- Name: custom_order_inventory_reservations custom_order_inventory_reservations_custom_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_inventory_reservations
    ADD CONSTRAINT custom_order_inventory_reservations_custom_order_id_fkey FOREIGN KEY (custom_order_id) REFERENCES public.custom_orders(custom_order_id) ON DELETE CASCADE;


--
-- Name: custom_order_inventory_reservations custom_order_inventory_reservations_custom_order_line_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_inventory_reservations
    ADD CONSTRAINT custom_order_inventory_reservations_custom_order_line_id_fkey FOREIGN KEY (custom_order_line_id) REFERENCES public.custom_order_lines(custom_order_line_id) ON DELETE CASCADE;


--
-- Name: custom_order_inventory_reservations custom_order_inventory_reservations_custom_variant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_inventory_reservations
    ADD CONSTRAINT custom_order_inventory_reservations_custom_variant_id_fkey FOREIGN KEY (custom_variant_id) REFERENCES public.custom_order_item_variants(custom_variant_id);


--
-- Name: custom_order_item_barcodes custom_order_item_barcodes_custom_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_barcodes
    ADD CONSTRAINT custom_order_item_barcodes_custom_item_id_fkey FOREIGN KEY (custom_item_id) REFERENCES public.custom_order_items(custom_item_id) ON DELETE CASCADE;


--
-- Name: custom_order_item_movements custom_order_item_movements_custom_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_movements
    ADD CONSTRAINT custom_order_item_movements_custom_item_id_fkey FOREIGN KEY (custom_item_id) REFERENCES public.custom_order_items(custom_item_id);


--
-- Name: custom_order_item_movements custom_order_item_movements_custom_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_movements
    ADD CONSTRAINT custom_order_item_movements_custom_order_id_fkey FOREIGN KEY (custom_order_id) REFERENCES public.custom_orders(custom_order_id);


--
-- Name: custom_order_item_movements custom_order_item_movements_custom_order_line_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_movements
    ADD CONSTRAINT custom_order_item_movements_custom_order_line_id_fkey FOREIGN KEY (custom_order_line_id) REFERENCES public.custom_order_lines(custom_order_line_id);


--
-- Name: custom_order_item_movements custom_order_item_movements_custom_order_line_return_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_movements
    ADD CONSTRAINT custom_order_item_movements_custom_order_line_return_id_fkey FOREIGN KEY (custom_order_line_return_id) REFERENCES public.custom_order_line_returns(custom_order_line_return_id);


--
-- Name: custom_order_item_movements custom_order_item_movements_custom_variant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_movements
    ADD CONSTRAINT custom_order_item_movements_custom_variant_id_fkey FOREIGN KEY (custom_variant_id) REFERENCES public.custom_order_item_variants(custom_variant_id);


--
-- Name: custom_order_item_movements custom_order_item_movements_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_movements
    ADD CONSTRAINT custom_order_item_movements_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: custom_order_item_movements custom_order_item_movements_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_movements
    ADD CONSTRAINT custom_order_item_movements_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: custom_order_item_variants custom_order_item_variants_custom_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_item_variants
    ADD CONSTRAINT custom_order_item_variants_custom_item_id_fkey FOREIGN KEY (custom_item_id) REFERENCES public.custom_order_items(custom_item_id) ON DELETE CASCADE;


--
-- Name: custom_order_items custom_order_items_brand_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_items
    ADD CONSTRAINT custom_order_items_brand_id_fkey FOREIGN KEY (brand_id) REFERENCES public.item_brands(brand_id);


--
-- Name: custom_order_items custom_order_items_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_items
    ADD CONSTRAINT custom_order_items_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(category_id);


--
-- Name: custom_order_items custom_order_items_item_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_items
    ADD CONSTRAINT custom_order_items_item_type_id_fkey FOREIGN KEY (item_type_id) REFERENCES public.item_types(item_type_id);


--
-- Name: custom_order_line_deliveries custom_order_line_deliveries_custom_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_deliveries
    ADD CONSTRAINT custom_order_line_deliveries_custom_item_id_fkey FOREIGN KEY (custom_item_id) REFERENCES public.custom_order_items(custom_item_id);


--
-- Name: custom_order_line_deliveries custom_order_line_deliveries_custom_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_deliveries
    ADD CONSTRAINT custom_order_line_deliveries_custom_order_id_fkey FOREIGN KEY (custom_order_id) REFERENCES public.custom_orders(custom_order_id) ON DELETE CASCADE;


--
-- Name: custom_order_line_deliveries custom_order_line_deliveries_custom_order_line_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_deliveries
    ADD CONSTRAINT custom_order_line_deliveries_custom_order_line_id_fkey FOREIGN KEY (custom_order_line_id) REFERENCES public.custom_order_lines(custom_order_line_id) ON DELETE CASCADE;


--
-- Name: custom_order_line_deliveries custom_order_line_deliveries_custom_variant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_deliveries
    ADD CONSTRAINT custom_order_line_deliveries_custom_variant_id_fkey FOREIGN KEY (custom_variant_id) REFERENCES public.custom_order_item_variants(custom_variant_id);


--
-- Name: custom_order_line_deliveries custom_order_line_deliveries_delivered_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_deliveries
    ADD CONSTRAINT custom_order_line_deliveries_delivered_by_user_id_fkey FOREIGN KEY (delivered_by_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_order_line_print_addons custom_order_line_print_addons_custom_order_line_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_print_addons
    ADD CONSTRAINT custom_order_line_print_addons_custom_order_line_id_fkey FOREIGN KEY (custom_order_line_id) REFERENCES public.custom_order_lines(custom_order_line_id) ON DELETE CASCADE;


--
-- Name: custom_order_line_print_addons custom_order_line_print_addons_print_material_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_print_addons
    ADD CONSTRAINT custom_order_line_print_addons_print_material_id_fkey FOREIGN KEY (print_material_id) REFERENCES public.custom_order_print_materials(print_material_id);


--
-- Name: custom_order_line_print_addons custom_order_line_print_addons_print_size_preset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_print_addons
    ADD CONSTRAINT custom_order_line_print_addons_print_size_preset_id_fkey FOREIGN KEY (print_size_preset_id) REFERENCES public.custom_order_print_size_presets(print_size_preset_id);


--
-- Name: custom_order_line_production_history custom_order_line_production_history_custom_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_production_history
    ADD CONSTRAINT custom_order_line_production_history_custom_item_id_fkey FOREIGN KEY (custom_item_id) REFERENCES public.custom_order_items(custom_item_id);


--
-- Name: custom_order_line_production_history custom_order_line_production_history_custom_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_production_history
    ADD CONSTRAINT custom_order_line_production_history_custom_order_id_fkey FOREIGN KEY (custom_order_id) REFERENCES public.custom_orders(custom_order_id) ON DELETE CASCADE;


--
-- Name: custom_order_line_production_history custom_order_line_production_history_custom_order_line_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_production_history
    ADD CONSTRAINT custom_order_line_production_history_custom_order_line_id_fkey FOREIGN KEY (custom_order_line_id) REFERENCES public.custom_order_lines(custom_order_line_id) ON DELETE CASCADE;


--
-- Name: custom_order_line_production_history custom_order_line_production_history_custom_variant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_production_history
    ADD CONSTRAINT custom_order_line_production_history_custom_variant_id_fkey FOREIGN KEY (custom_variant_id) REFERENCES public.custom_order_item_variants(custom_variant_id);


--
-- Name: custom_order_line_production_history custom_order_line_production_history_updated_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_production_history
    ADD CONSTRAINT custom_order_line_production_history_updated_by_user_id_fkey FOREIGN KEY (updated_by_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_order_line_returns custom_order_line_returns_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_returns
    ADD CONSTRAINT custom_order_line_returns_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_order_line_returns custom_order_line_returns_custom_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_returns
    ADD CONSTRAINT custom_order_line_returns_custom_item_id_fkey FOREIGN KEY (custom_item_id) REFERENCES public.custom_order_items(custom_item_id);


--
-- Name: custom_order_line_returns custom_order_line_returns_custom_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_returns
    ADD CONSTRAINT custom_order_line_returns_custom_order_id_fkey FOREIGN KEY (custom_order_id) REFERENCES public.custom_orders(custom_order_id) ON DELETE CASCADE;


--
-- Name: custom_order_line_returns custom_order_line_returns_custom_order_line_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_returns
    ADD CONSTRAINT custom_order_line_returns_custom_order_line_id_fkey FOREIGN KEY (custom_order_line_id) REFERENCES public.custom_order_lines(custom_order_line_id) ON DELETE CASCADE;


--
-- Name: custom_order_line_returns custom_order_line_returns_custom_variant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_line_returns
    ADD CONSTRAINT custom_order_line_returns_custom_variant_id_fkey FOREIGN KEY (custom_variant_id) REFERENCES public.custom_order_item_variants(custom_variant_id);


--
-- Name: custom_order_lines custom_order_lines_custom_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_lines
    ADD CONSTRAINT custom_order_lines_custom_item_id_fkey FOREIGN KEY (custom_item_id) REFERENCES public.custom_order_items(custom_item_id);


--
-- Name: custom_order_lines custom_order_lines_custom_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_lines
    ADD CONSTRAINT custom_order_lines_custom_order_id_fkey FOREIGN KEY (custom_order_id) REFERENCES public.custom_orders(custom_order_id) ON DELETE CASCADE;


--
-- Name: custom_order_lines custom_order_lines_custom_variant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_lines
    ADD CONSTRAINT custom_order_lines_custom_variant_id_fkey FOREIGN KEY (custom_variant_id) REFERENCES public.custom_order_item_variants(custom_variant_id);


--
-- Name: custom_order_lines custom_order_lines_delivered_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_lines
    ADD CONSTRAINT custom_order_lines_delivered_by_user_id_fkey FOREIGN KEY (delivered_by_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_order_lines custom_order_lines_line_discount_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_lines
    ADD CONSTRAINT custom_order_lines_line_discount_by_user_id_fkey FOREIGN KEY (line_discount_by_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_order_lines custom_order_lines_price_override_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_lines
    ADD CONSTRAINT custom_order_lines_price_override_by_user_id_fkey FOREIGN KEY (price_override_by_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_order_lines custom_order_lines_print_material_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_lines
    ADD CONSTRAINT custom_order_lines_print_material_id_fkey FOREIGN KEY (print_material_id) REFERENCES public.custom_order_print_materials(print_material_id);


--
-- Name: custom_order_lines custom_order_lines_print_size_preset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_lines
    ADD CONSTRAINT custom_order_lines_print_size_preset_id_fkey FOREIGN KEY (print_size_preset_id) REFERENCES public.custom_order_print_size_presets(print_size_preset_id);


--
-- Name: custom_order_lines custom_order_lines_production_updated_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_lines
    ADD CONSTRAINT custom_order_lines_production_updated_by_user_id_fkey FOREIGN KEY (production_updated_by_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_order_payments custom_order_payments_custom_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_payments
    ADD CONSTRAINT custom_order_payments_custom_order_id_fkey FOREIGN KEY (custom_order_id) REFERENCES public.custom_orders(custom_order_id) ON DELETE CASCADE;


--
-- Name: custom_order_payments custom_order_payments_taken_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_payments
    ADD CONSTRAINT custom_order_payments_taken_by_user_id_fkey FOREIGN KEY (taken_by_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_order_payments custom_order_payments_voided_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_payments
    ADD CONSTRAINT custom_order_payments_voided_by_user_id_fkey FOREIGN KEY (voided_by_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_order_print_size_presets custom_order_print_size_presets_print_material_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_print_size_presets
    ADD CONSTRAINT custom_order_print_size_presets_print_material_id_fkey FOREIGN KEY (print_material_id) REFERENCES public.custom_order_print_materials(print_material_id) ON DELETE CASCADE;


--
-- Name: custom_order_status_history custom_order_status_history_custom_order_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_status_history
    ADD CONSTRAINT custom_order_status_history_custom_order_id_fkey FOREIGN KEY (custom_order_id) REFERENCES public.custom_orders(custom_order_id) ON DELETE CASCADE;


--
-- Name: custom_order_status_history custom_order_status_history_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_order_status_history
    ADD CONSTRAINT custom_order_status_history_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: custom_orders custom_orders_assigned_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_orders
    ADD CONSTRAINT custom_orders_assigned_by_user_id_fkey FOREIGN KEY (assigned_by_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_orders custom_orders_assigned_to_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_orders
    ADD CONSTRAINT custom_orders_assigned_to_user_id_fkey FOREIGN KEY (assigned_to_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_orders custom_orders_cancelled_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_orders
    ADD CONSTRAINT custom_orders_cancelled_by_user_id_fkey FOREIGN KEY (cancelled_by_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_orders custom_orders_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_orders
    ADD CONSTRAINT custom_orders_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customer_accounts(customer_id);


--
-- Name: custom_orders custom_orders_deposit_override_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_orders
    ADD CONSTRAINT custom_orders_deposit_override_by_user_id_fkey FOREIGN KEY (deposit_override_by_user_id) REFERENCES public.users(user_id);


--
-- Name: custom_orders custom_orders_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_orders
    ADD CONSTRAINT custom_orders_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: custom_orders custom_orders_taken_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.custom_orders
    ADD CONSTRAINT custom_orders_taken_by_user_id_fkey FOREIGN KEY (taken_by_user_id) REFERENCES public.users(user_id);


--
-- Name: customer_account_payment_allocations customer_account_payment_allocation_payment_transaction_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_account_payment_allocations
    ADD CONSTRAINT customer_account_payment_allocation_payment_transaction_id_fkey FOREIGN KEY (payment_transaction_id) REFERENCES public.customer_account_transactions(transaction_id) ON DELETE CASCADE;


--
-- Name: customer_account_payment_allocations customer_account_payment_allocations_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_account_payment_allocations
    ADD CONSTRAINT customer_account_payment_allocations_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customer_accounts(customer_id);


--
-- Name: customer_account_payment_allocations customer_account_payment_allocations_sale_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_account_payment_allocations
    ADD CONSTRAINT customer_account_payment_allocations_sale_id_fkey FOREIGN KEY (sale_id) REFERENCES public.sales(sale_id);


--
-- Name: customer_account_transactions customer_account_transactions_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_account_transactions
    ADD CONSTRAINT customer_account_transactions_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customer_accounts(customer_id);


--
-- Name: customer_account_transactions customer_account_transactions_sale_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.customer_account_transactions
    ADD CONSTRAINT customer_account_transactions_sale_id_fkey FOREIGN KEY (sale_id) REFERENCES public.sales(sale_id);


--
-- Name: device_sessions device_sessions_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_sessions
    ADD CONSTRAINT device_sessions_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id) ON DELETE CASCADE;


--
-- Name: device_sessions device_sessions_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_sessions
    ADD CONSTRAINT device_sessions_store_id_fkey FOREIGN KEY (store_id) REFERENCES public.locations(location_id);


--
-- Name: device_sessions device_sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.device_sessions
    ADD CONSTRAINT device_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: devices devices_approved_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_approved_by_user_id_fkey FOREIGN KEY (approved_by_user_id) REFERENCES public.users(user_id);


--
-- Name: devices devices_blocked_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_blocked_by_user_id_fkey FOREIGN KEY (blocked_by_user_id) REFERENCES public.users(user_id);


--
-- Name: devices devices_last_login_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_last_login_user_id_fkey FOREIGN KEY (last_login_user_id) REFERENCES public.users(user_id);


--
-- Name: devices devices_last_store_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_last_store_id_fkey FOREIGN KEY (last_store_id) REFERENCES public.locations(location_id);


--
-- Name: email_outbox_events email_outbox_events_email_outbox_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox_events
    ADD CONSTRAINT email_outbox_events_email_outbox_id_fkey FOREIGN KEY (email_outbox_id) REFERENCES public.email_outbox(email_outbox_id) ON DELETE CASCADE;


--
-- Name: email_outbox_events email_outbox_events_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox_events
    ADD CONSTRAINT email_outbox_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: email_outbox email_outbox_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox
    ADD CONSTRAINT email_outbox_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: email_outbox email_outbox_queued_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.email_outbox
    ADD CONSTRAINT email_outbox_queued_by_user_id_fkey FOREIGN KEY (queued_by_user_id) REFERENCES public.users(user_id);


--
-- Name: employee_payroll_bonuses employee_payroll_bonuses_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_payroll_bonuses
    ADD CONSTRAINT employee_payroll_bonuses_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: employee_payroll_bonuses employee_payroll_bonuses_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_payroll_bonuses
    ADD CONSTRAINT employee_payroll_bonuses_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: employee_payroll_bonuses employee_payroll_bonuses_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_payroll_bonuses
    ADD CONSTRAINT employee_payroll_bonuses_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: employee_payroll_settings employee_payroll_settings_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_payroll_settings
    ADD CONSTRAINT employee_payroll_settings_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: employee_payroll_settings employee_payroll_settings_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_payroll_settings
    ADD CONSTRAINT employee_payroll_settings_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: employee_schedule_assignments employee_schedule_assignments_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_assignments
    ADD CONSTRAINT employee_schedule_assignments_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: employee_schedule_assignments employee_schedule_assignments_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_assignments
    ADD CONSTRAINT employee_schedule_assignments_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: employee_schedule_assignments employee_schedule_assignments_location_shift_fk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_assignments
    ADD CONSTRAINT employee_schedule_assignments_location_shift_fk FOREIGN KEY (location_id, shift_id) REFERENCES public.employee_schedule_shifts(location_id, shift_id);


--
-- Name: employee_schedule_assignments employee_schedule_assignments_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_assignments
    ADD CONSTRAINT employee_schedule_assignments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: employee_schedule_holidays employee_schedule_holidays_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_holidays
    ADD CONSTRAINT employee_schedule_holidays_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: employee_schedule_holidays employee_schedule_holidays_updated_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_holidays
    ADD CONSTRAINT employee_schedule_holidays_updated_by_user_id_fkey FOREIGN KEY (updated_by_user_id) REFERENCES public.users(user_id);


--
-- Name: employee_schedule_shifts employee_schedule_shifts_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_shifts
    ADD CONSTRAINT employee_schedule_shifts_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: employee_schedule_shifts employee_schedule_shifts_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_shifts
    ADD CONSTRAINT employee_schedule_shifts_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: employee_schedule_shifts employee_schedule_shifts_updated_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_schedule_shifts
    ADD CONSTRAINT employee_schedule_shifts_updated_by_user_id_fkey FOREIGN KEY (updated_by_user_id) REFERENCES public.users(user_id);


--
-- Name: employee_time_clock_adjustments employee_time_clock_adjustments_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_time_clock_adjustments
    ADD CONSTRAINT employee_time_clock_adjustments_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.users(user_id);


--
-- Name: employee_time_clock_adjustments employee_time_clock_adjustments_clock_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_time_clock_adjustments
    ADD CONSTRAINT employee_time_clock_adjustments_clock_id_fkey FOREIGN KEY (clock_id) REFERENCES public.employee_time_clock(clock_id) ON DELETE CASCADE;


--
-- Name: employee_time_clock_adjustments employee_time_clock_adjustments_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_time_clock_adjustments
    ADD CONSTRAINT employee_time_clock_adjustments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: employee_time_clock employee_time_clock_auto_clock_out_reviewed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_time_clock
    ADD CONSTRAINT employee_time_clock_auto_clock_out_reviewed_by_user_id_fkey FOREIGN KEY (auto_clock_out_reviewed_by_user_id) REFERENCES public.users(user_id);


--
-- Name: employee_time_clock employee_time_clock_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_time_clock
    ADD CONSTRAINT employee_time_clock_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: employee_time_clock employee_time_clock_multiple_session_override_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_time_clock
    ADD CONSTRAINT employee_time_clock_multiple_session_override_by_user_id_fkey FOREIGN KEY (multiple_session_override_by_user_id) REFERENCES public.users(user_id);


--
-- Name: employee_time_clock employee_time_clock_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employee_time_clock
    ADD CONSTRAINT employee_time_clock_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: expenses expenses_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: expenses expenses_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: held_cart_items held_cart_items_held_cart_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.held_cart_items
    ADD CONSTRAINT held_cart_items_held_cart_id_fkey FOREIGN KEY (held_cart_id) REFERENCES public.held_carts(held_cart_id) ON DELETE CASCADE;


--
-- Name: held_cart_items held_cart_items_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.held_cart_items
    ADD CONSTRAINT held_cart_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id);


--
-- Name: held_carts held_carts_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.held_carts
    ADD CONSTRAINT held_carts_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customer_accounts(customer_id);


--
-- Name: held_carts held_carts_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.held_carts
    ADD CONSTRAINT held_carts_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: held_carts held_carts_resumed_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.held_carts
    ADD CONSTRAINT held_carts_resumed_by_user_id_fkey FOREIGN KEY (resumed_by_user_id) REFERENCES public.users(user_id);


--
-- Name: held_carts held_carts_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.held_carts
    ADD CONSTRAINT held_carts_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: image_asset_references image_asset_references_asset_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.image_asset_references
    ADD CONSTRAINT image_asset_references_asset_id_fkey FOREIGN KEY (asset_id) REFERENCES public.image_assets(asset_id);


--
-- Name: inventory inventory_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: inventory_movements inventory_movements_invoice_delivery_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_movements
    ADD CONSTRAINT inventory_movements_invoice_delivery_event_id_fkey FOREIGN KEY (invoice_delivery_event_id) REFERENCES public.invoice_delivery_events(invoice_delivery_event_id);


--
-- Name: inventory_movements inventory_movements_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_movements
    ADD CONSTRAINT inventory_movements_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(invoice_id);


--
-- Name: inventory_movements inventory_movements_invoice_line_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_movements
    ADD CONSTRAINT inventory_movements_invoice_line_id_fkey FOREIGN KEY (invoice_line_id) REFERENCES public.invoice_lines(invoice_line_id);


--
-- Name: inventory_movements inventory_movements_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_movements
    ADD CONSTRAINT inventory_movements_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: inventory_movements inventory_movements_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_movements
    ADD CONSTRAINT inventory_movements_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id);


--
-- Name: inventory_movements inventory_movements_sale_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_movements
    ADD CONSTRAINT inventory_movements_sale_id_fkey FOREIGN KEY (sale_id) REFERENCES public.sales(sale_id);


--
-- Name: inventory_movements inventory_movements_sale_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_movements
    ADD CONSTRAINT inventory_movements_sale_item_id_fkey FOREIGN KEY (sale_item_id) REFERENCES public.sale_items(sale_item_id);


--
-- Name: inventory_movements inventory_movements_sale_return_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_movements
    ADD CONSTRAINT inventory_movements_sale_return_id_fkey FOREIGN KEY (sale_return_id) REFERENCES public.sale_returns(return_id);


--
-- Name: inventory_movements inventory_movements_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_movements
    ADD CONSTRAINT inventory_movements_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: inventory inventory_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory
    ADD CONSTRAINT inventory_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id) ON DELETE CASCADE;


--
-- Name: invoice_audit_log invoice_audit_log_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_audit_log
    ADD CONSTRAINT invoice_audit_log_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(invoice_id) ON DELETE CASCADE;


--
-- Name: invoice_audit_log invoice_audit_log_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_audit_log
    ADD CONSTRAINT invoice_audit_log_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: invoice_delivery_events invoice_delivery_events_delivered_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_delivery_events
    ADD CONSTRAINT invoice_delivery_events_delivered_by_user_id_fkey FOREIGN KEY (delivered_by_user_id) REFERENCES public.users(user_id);


--
-- Name: invoice_delivery_events invoice_delivery_events_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_delivery_events
    ADD CONSTRAINT invoice_delivery_events_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(invoice_id) ON DELETE CASCADE;


--
-- Name: invoice_delivery_events invoice_delivery_events_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_delivery_events
    ADD CONSTRAINT invoice_delivery_events_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: invoice_delivery_lines invoice_delivery_lines_invoice_delivery_event_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_delivery_lines
    ADD CONSTRAINT invoice_delivery_lines_invoice_delivery_event_id_fkey FOREIGN KEY (invoice_delivery_event_id) REFERENCES public.invoice_delivery_events(invoice_delivery_event_id) ON DELETE CASCADE;


--
-- Name: invoice_delivery_lines invoice_delivery_lines_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_delivery_lines
    ADD CONSTRAINT invoice_delivery_lines_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(invoice_id) ON DELETE CASCADE;


--
-- Name: invoice_delivery_lines invoice_delivery_lines_invoice_line_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_delivery_lines
    ADD CONSTRAINT invoice_delivery_lines_invoice_line_id_fkey FOREIGN KEY (invoice_line_id) REFERENCES public.invoice_lines(invoice_line_id) ON DELETE CASCADE;


--
-- Name: invoice_delivery_lines invoice_delivery_lines_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_delivery_lines
    ADD CONSTRAINT invoice_delivery_lines_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id);


--
-- Name: invoice_lines invoice_lines_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_lines
    ADD CONSTRAINT invoice_lines_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(category_id);


--
-- Name: invoice_lines invoice_lines_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_lines
    ADD CONSTRAINT invoice_lines_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(invoice_id) ON DELETE CASCADE;


--
-- Name: invoice_lines invoice_lines_price_override_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_lines
    ADD CONSTRAINT invoice_lines_price_override_by_user_id_fkey FOREIGN KEY (price_override_by_user_id) REFERENCES public.users(user_id);


--
-- Name: invoice_lines invoice_lines_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_lines
    ADD CONSTRAINT invoice_lines_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id);


--
-- Name: invoice_lines invoice_lines_quotation_line_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_lines
    ADD CONSTRAINT invoice_lines_quotation_line_id_fkey FOREIGN KEY (quotation_line_id) REFERENCES public.quotation_lines(quotation_line_id);


--
-- Name: invoice_payments invoice_payments_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_payments
    ADD CONSTRAINT invoice_payments_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customer_accounts(customer_id);


--
-- Name: invoice_payments invoice_payments_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_payments
    ADD CONSTRAINT invoice_payments_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(invoice_id) ON DELETE CASCADE;


--
-- Name: invoice_payments invoice_payments_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_payments
    ADD CONSTRAINT invoice_payments_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: invoice_payments invoice_payments_taken_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_payments
    ADD CONSTRAINT invoice_payments_taken_by_user_id_fkey FOREIGN KEY (taken_by_user_id) REFERENCES public.users(user_id);


--
-- Name: invoice_payments invoice_payments_voided_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_payments
    ADD CONSTRAINT invoice_payments_voided_by_user_id_fkey FOREIGN KEY (voided_by_user_id) REFERENCES public.users(user_id);


--
-- Name: invoice_status_history invoice_status_history_invoice_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_status_history
    ADD CONSTRAINT invoice_status_history_invoice_id_fkey FOREIGN KEY (invoice_id) REFERENCES public.invoices(invoice_id) ON DELETE CASCADE;


--
-- Name: invoice_status_history invoice_status_history_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoice_status_history
    ADD CONSTRAINT invoice_status_history_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: invoices invoices_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: invoices invoices_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customer_accounts(customer_id);


--
-- Name: invoices invoices_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: invoices invoices_quotation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.invoices
    ADD CONSTRAINT invoices_quotation_id_fkey FOREIGN KEY (quotation_id) REFERENCES public.quotations(quotation_id);


--
-- Name: item_types item_types_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.item_types
    ADD CONSTRAINT item_types_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(category_id) ON DELETE CASCADE;


--
-- Name: lan_api_approvals lan_api_approvals_approver_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_approvals
    ADD CONSTRAINT lan_api_approvals_approver_user_id_fkey FOREIGN KEY (approver_user_id) REFERENCES public.users(user_id);


--
-- Name: lan_api_approvals lan_api_approvals_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_approvals
    ADD CONSTRAINT lan_api_approvals_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id) ON DELETE CASCADE;


--
-- Name: lan_api_approvals lan_api_approvals_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_approvals
    ADD CONSTRAINT lan_api_approvals_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: lan_api_approvals lan_api_approvals_requester_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_approvals
    ADD CONSTRAINT lan_api_approvals_requester_user_id_fkey FOREIGN KEY (requester_user_id) REFERENCES public.users(user_id);


--
-- Name: lan_api_idempotency lan_api_idempotency_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_idempotency
    ADD CONSTRAINT lan_api_idempotency_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id) ON DELETE CASCADE;


--
-- Name: lan_api_request_audit lan_api_request_audit_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_request_audit
    ADD CONSTRAINT lan_api_request_audit_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id) ON DELETE SET NULL;


--
-- Name: lan_api_request_audit lan_api_request_audit_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_request_audit
    ADD CONSTRAINT lan_api_request_audit_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE SET NULL;


--
-- Name: lan_api_request_audit lan_api_request_audit_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_request_audit
    ADD CONSTRAINT lan_api_request_audit_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: lan_api_schedule_proposals lan_api_schedule_proposals_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_schedule_proposals
    ADD CONSTRAINT lan_api_schedule_proposals_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id) ON DELETE CASCADE;


--
-- Name: lan_api_schedule_proposals lan_api_schedule_proposals_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_schedule_proposals
    ADD CONSTRAINT lan_api_schedule_proposals_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: lan_api_schedule_proposals lan_api_schedule_proposals_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_schedule_proposals
    ADD CONSTRAINT lan_api_schedule_proposals_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: lan_api_sessions lan_api_sessions_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_sessions
    ADD CONSTRAINT lan_api_sessions_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id) ON DELETE CASCADE;


--
-- Name: lan_api_sessions lan_api_sessions_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_sessions
    ADD CONSTRAINT lan_api_sessions_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: lan_api_sessions lan_api_sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.lan_api_sessions
    ADD CONSTRAINT lan_api_sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: maintenance_logs maintenance_logs_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_logs
    ADD CONSTRAINT maintenance_logs_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: maintenance_logs maintenance_logs_machine_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_logs
    ADD CONSTRAINT maintenance_logs_machine_id_fkey FOREIGN KEY (machine_id) REFERENCES public.maintenance_machines(machine_id) ON DELETE CASCADE;


--
-- Name: maintenance_machine_parts maintenance_machine_parts_machine_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_machine_parts
    ADD CONSTRAINT maintenance_machine_parts_machine_id_fkey FOREIGN KEY (machine_id) REFERENCES public.maintenance_machines(machine_id) ON DELETE CASCADE;


--
-- Name: maintenance_machine_parts maintenance_machine_parts_part_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_machine_parts
    ADD CONSTRAINT maintenance_machine_parts_part_id_fkey FOREIGN KEY (part_id) REFERENCES public.maintenance_parts(part_id) ON DELETE RESTRICT;


--
-- Name: maintenance_machines maintenance_machines_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_machines
    ADD CONSTRAINT maintenance_machines_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: maintenance_ticket_notes maintenance_ticket_notes_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_ticket_notes
    ADD CONSTRAINT maintenance_ticket_notes_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: maintenance_ticket_notes maintenance_ticket_notes_ticket_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_ticket_notes
    ADD CONSTRAINT maintenance_ticket_notes_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES public.maintenance_tickets(ticket_id) ON DELETE CASCADE;


--
-- Name: maintenance_ticket_parts maintenance_ticket_parts_part_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_ticket_parts
    ADD CONSTRAINT maintenance_ticket_parts_part_id_fkey FOREIGN KEY (part_id) REFERENCES public.maintenance_parts(part_id) ON DELETE RESTRICT;


--
-- Name: maintenance_ticket_parts maintenance_ticket_parts_ticket_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_ticket_parts
    ADD CONSTRAINT maintenance_ticket_parts_ticket_id_fkey FOREIGN KEY (ticket_id) REFERENCES public.maintenance_tickets(ticket_id) ON DELETE CASCADE;


--
-- Name: maintenance_tickets maintenance_tickets_machine_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_tickets
    ADD CONSTRAINT maintenance_tickets_machine_id_fkey FOREIGN KEY (machine_id) REFERENCES public.maintenance_machines(machine_id) ON DELETE SET NULL;


--
-- Name: maintenance_tickets maintenance_tickets_opened_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.maintenance_tickets
    ADD CONSTRAINT maintenance_tickets_opened_by_user_id_fkey FOREIGN KEY (opened_by_user_id) REFERENCES public.users(user_id);


--
-- Name: notification_user_state notification_user_state_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.notification_user_state
    ADD CONSTRAINT notification_user_state_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: other_income_entries other_income_entries_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.other_income_entries
    ADD CONSTRAINT other_income_entries_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: other_income_entries other_income_entries_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.other_income_entries
    ADD CONSTRAINT other_income_entries_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: payroll_payments payroll_payments_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payroll_payments
    ADD CONSTRAINT payroll_payments_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: payroll_payments payroll_payments_paid_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payroll_payments
    ADD CONSTRAINT payroll_payments_paid_by_user_id_fkey FOREIGN KEY (paid_by_user_id) REFERENCES public.users(user_id);


--
-- Name: payroll_payments payroll_payments_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.payroll_payments
    ADD CONSTRAINT payroll_payments_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: product_barcodes product_barcodes_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_barcodes
    ADD CONSTRAINT product_barcodes_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id) ON DELETE CASCADE;


--
-- Name: product_shelf_assignments product_shelf_assignments_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_shelf_assignments
    ADD CONSTRAINT product_shelf_assignments_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: product_shelf_assignments product_shelf_assignments_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_shelf_assignments
    ADD CONSTRAINT product_shelf_assignments_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id) ON DELETE CASCADE;


--
-- Name: product_shelf_assignments product_shelf_assignments_shelf_location_id_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_shelf_assignments
    ADD CONSTRAINT product_shelf_assignments_shelf_location_id_location_id_fkey FOREIGN KEY (shelf_location_id, location_id) REFERENCES public.shelf_locations(shelf_location_id, location_id);


--
-- Name: product_shelf_assignments product_shelf_assignments_storage_shelf_location_id_locati_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.product_shelf_assignments
    ADD CONSTRAINT product_shelf_assignments_storage_shelf_location_id_locati_fkey FOREIGN KEY (storage_shelf_location_id, location_id) REFERENCES public.shelf_locations(shelf_location_id, location_id);


--
-- Name: products products_brand_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_brand_id_fkey FOREIGN KEY (brand_id) REFERENCES public.item_brands(brand_id);


--
-- Name: products products_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(category_id);


--
-- Name: products products_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: products products_item_type_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_item_type_id_fkey FOREIGN KEY (item_type_id) REFERENCES public.item_types(item_type_id);


--
-- Name: products products_vendor_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.products
    ADD CONSTRAINT products_vendor_id_fkey FOREIGN KEY (vendor_id) REFERENCES public.vendors(vendor_id);


--
-- Name: quotation_audit_log quotation_audit_log_quotation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_audit_log
    ADD CONSTRAINT quotation_audit_log_quotation_id_fkey FOREIGN KEY (quotation_id) REFERENCES public.quotations(quotation_id) ON DELETE CASCADE;


--
-- Name: quotation_audit_log quotation_audit_log_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_audit_log
    ADD CONSTRAINT quotation_audit_log_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: quotation_lines quotation_lines_category_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_lines
    ADD CONSTRAINT quotation_lines_category_id_fkey FOREIGN KEY (category_id) REFERENCES public.categories(category_id);


--
-- Name: quotation_lines quotation_lines_price_override_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_lines
    ADD CONSTRAINT quotation_lines_price_override_by_user_id_fkey FOREIGN KEY (price_override_by_user_id) REFERENCES public.users(user_id);


--
-- Name: quotation_lines quotation_lines_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_lines
    ADD CONSTRAINT quotation_lines_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id);


--
-- Name: quotation_lines quotation_lines_quotation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_lines
    ADD CONSTRAINT quotation_lines_quotation_id_fkey FOREIGN KEY (quotation_id) REFERENCES public.quotations(quotation_id) ON DELETE CASCADE;


--
-- Name: quotation_status_history quotation_status_history_quotation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_status_history
    ADD CONSTRAINT quotation_status_history_quotation_id_fkey FOREIGN KEY (quotation_id) REFERENCES public.quotations(quotation_id) ON DELETE CASCADE;


--
-- Name: quotation_status_history quotation_status_history_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotation_status_history
    ADD CONSTRAINT quotation_status_history_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: quotations quotations_accepted_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotations
    ADD CONSTRAINT quotations_accepted_by_user_id_fkey FOREIGN KEY (accepted_by_user_id) REFERENCES public.users(user_id);


--
-- Name: quotations quotations_created_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotations
    ADD CONSTRAINT quotations_created_by_user_id_fkey FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);


--
-- Name: quotations quotations_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotations
    ADD CONSTRAINT quotations_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customer_accounts(customer_id);


--
-- Name: quotations quotations_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotations
    ADD CONSTRAINT quotations_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: quotations quotations_superseded_by_quotation_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.quotations
    ADD CONSTRAINT quotations_superseded_by_quotation_id_fkey FOREIGN KEY (superseded_by_quotation_id) REFERENCES public.quotations(quotation_id);


--
-- Name: receiving_batches receiving_batches_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_batches
    ADD CONSTRAINT receiving_batches_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: receiving_batches receiving_batches_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.receiving_batches
    ADD CONSTRAINT receiving_batches_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: remote_admin_commands remote_admin_commands_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.remote_admin_commands
    ADD CONSTRAINT remote_admin_commands_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id) ON DELETE SET NULL;


--
-- Name: remote_admin_commands remote_admin_commands_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.remote_admin_commands
    ADD CONSTRAINT remote_admin_commands_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: remote_admin_commands remote_admin_commands_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.remote_admin_commands
    ADD CONSTRAINT remote_admin_commands_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: role_mobile_permissions role_mobile_permissions_permission_key_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_mobile_permissions
    ADD CONSTRAINT role_mobile_permissions_permission_key_fkey FOREIGN KEY (permission_key) REFERENCES public.mobile_permissions(permission_key) ON DELETE CASCADE;


--
-- Name: role_mobile_permissions role_mobile_permissions_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_mobile_permissions
    ADD CONSTRAINT role_mobile_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(role_id) ON DELETE CASCADE;


--
-- Name: role_permissions role_permissions_permission_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_permission_id_fkey FOREIGN KEY (permission_id) REFERENCES public.permissions(permission_id) ON DELETE CASCADE;


--
-- Name: role_permissions role_permissions_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.role_permissions
    ADD CONSTRAINT role_permissions_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(role_id) ON DELETE CASCADE;


--
-- Name: sale_audit_log sale_audit_log_customer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_audit_log
    ADD CONSTRAINT sale_audit_log_customer_id_fkey FOREIGN KEY (customer_id) REFERENCES public.customer_accounts(customer_id);


--
-- Name: sale_audit_log sale_audit_log_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_audit_log
    ADD CONSTRAINT sale_audit_log_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: sale_audit_log sale_audit_log_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_audit_log
    ADD CONSTRAINT sale_audit_log_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id);


--
-- Name: sale_audit_log sale_audit_log_return_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_audit_log
    ADD CONSTRAINT sale_audit_log_return_id_fkey FOREIGN KEY (return_id) REFERENCES public.sale_returns(return_id) ON DELETE SET NULL;


--
-- Name: sale_audit_log sale_audit_log_return_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_audit_log
    ADD CONSTRAINT sale_audit_log_return_item_id_fkey FOREIGN KEY (return_item_id) REFERENCES public.sale_return_items(return_item_id) ON DELETE SET NULL;


--
-- Name: sale_audit_log sale_audit_log_sale_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_audit_log
    ADD CONSTRAINT sale_audit_log_sale_id_fkey FOREIGN KEY (sale_id) REFERENCES public.sales(sale_id) ON DELETE SET NULL;


--
-- Name: sale_audit_log sale_audit_log_sale_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_audit_log
    ADD CONSTRAINT sale_audit_log_sale_item_id_fkey FOREIGN KEY (sale_item_id) REFERENCES public.sale_items(sale_item_id) ON DELETE SET NULL;


--
-- Name: sale_audit_log sale_audit_log_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_audit_log
    ADD CONSTRAINT sale_audit_log_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: sale_items sale_items_price_override_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_items
    ADD CONSTRAINT sale_items_price_override_by_user_id_fkey FOREIGN KEY (price_override_by_user_id) REFERENCES public.users(user_id);


--
-- Name: sale_items sale_items_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_items
    ADD CONSTRAINT sale_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id);


--
-- Name: sale_items sale_items_sale_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_items
    ADD CONSTRAINT sale_items_sale_id_fkey FOREIGN KEY (sale_id) REFERENCES public.sales(sale_id) ON DELETE CASCADE;


--
-- Name: sale_return_items sale_return_items_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_return_items
    ADD CONSTRAINT sale_return_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id);


--
-- Name: sale_return_items sale_return_items_return_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_return_items
    ADD CONSTRAINT sale_return_items_return_id_fkey FOREIGN KEY (return_id) REFERENCES public.sale_returns(return_id) ON DELETE CASCADE;


--
-- Name: sale_return_items sale_return_items_sale_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_return_items
    ADD CONSTRAINT sale_return_items_sale_item_id_fkey FOREIGN KEY (sale_item_id) REFERENCES public.sale_items(sale_item_id);


--
-- Name: sale_returns sale_returns_cash_drawer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_returns
    ADD CONSTRAINT sale_returns_cash_drawer_id_fkey FOREIGN KEY (cash_drawer_id) REFERENCES public.cash_drawers(cash_drawer_id);


--
-- Name: sale_returns sale_returns_cash_drawer_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_returns
    ADD CONSTRAINT sale_returns_cash_drawer_session_id_fkey FOREIGN KEY (cash_drawer_session_id) REFERENCES public.cash_drawer_sessions(cash_drawer_session_id);


--
-- Name: sale_returns sale_returns_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_returns
    ADD CONSTRAINT sale_returns_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: sale_returns sale_returns_override_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_returns
    ADD CONSTRAINT sale_returns_override_by_user_id_fkey FOREIGN KEY (override_by_user_id) REFERENCES public.users(user_id);


--
-- Name: sale_returns sale_returns_sale_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_returns
    ADD CONSTRAINT sale_returns_sale_id_fkey FOREIGN KEY (sale_id) REFERENCES public.sales(sale_id);


--
-- Name: sale_returns sale_returns_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sale_returns
    ADD CONSTRAINT sale_returns_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: sales sales_cash_drawer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT sales_cash_drawer_id_fkey FOREIGN KEY (cash_drawer_id) REFERENCES public.cash_drawers(cash_drawer_id);


--
-- Name: sales sales_cash_drawer_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT sales_cash_drawer_session_id_fkey FOREIGN KEY (cash_drawer_session_id) REFERENCES public.cash_drawer_sessions(cash_drawer_session_id);


--
-- Name: sales sales_discount_override_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT sales_discount_override_by_user_id_fkey FOREIGN KEY (discount_override_by_user_id) REFERENCES public.users(user_id);


--
-- Name: sales sales_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT sales_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id);


--
-- Name: sales sales_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.sales
    ADD CONSTRAINT sales_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: security_audit_events security_audit_events_actor_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_audit_events
    ADD CONSTRAINT security_audit_events_actor_user_id_fkey FOREIGN KEY (actor_user_id) REFERENCES public.users(user_id) ON DELETE SET NULL;


--
-- Name: security_audit_events security_audit_events_device_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.security_audit_events
    ADD CONSTRAINT security_audit_events_device_id_fkey FOREIGN KEY (device_id) REFERENCES public.devices(device_id) ON DELETE SET NULL;


--
-- Name: shelf_locations shelf_locations_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shelf_locations
    ADD CONSTRAINT shelf_locations_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: store_sync_status store_sync_status_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_sync_status
    ADD CONSTRAINT store_sync_status_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: store_transfer_items store_transfer_items_product_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_transfer_items
    ADD CONSTRAINT store_transfer_items_product_id_fkey FOREIGN KEY (product_id) REFERENCES public.products(product_id);


--
-- Name: store_transfer_items store_transfer_items_transfer_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_transfer_items
    ADD CONSTRAINT store_transfer_items_transfer_id_fkey FOREIGN KEY (transfer_id) REFERENCES public.store_transfers(transfer_id) ON DELETE CASCADE;


--
-- Name: store_transfers store_transfers_from_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_transfers
    ADD CONSTRAINT store_transfers_from_location_id_fkey FOREIGN KEY (from_location_id) REFERENCES public.locations(location_id);


--
-- Name: store_transfers store_transfers_receive_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_transfers
    ADD CONSTRAINT store_transfers_receive_id_fkey FOREIGN KEY (receive_id) REFERENCES public.receiving_batches(receive_id);


--
-- Name: store_transfers store_transfers_received_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_transfers
    ADD CONSTRAINT store_transfers_received_by_user_id_fkey FOREIGN KEY (received_by_user_id) REFERENCES public.users(user_id);


--
-- Name: store_transfers store_transfers_to_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_transfers
    ADD CONSTRAINT store_transfers_to_location_id_fkey FOREIGN KEY (to_location_id) REFERENCES public.locations(location_id);


--
-- Name: store_transfers store_transfers_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.store_transfers
    ADD CONSTRAINT store_transfers_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: time_clock_auto_close_settings time_clock_auto_close_settings_updated_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.time_clock_auto_close_settings
    ADD CONSTRAINT time_clock_auto_close_settings_updated_by_user_id_fkey FOREIGN KEY (updated_by_user_id) REFERENCES public.users(user_id);


--
-- Name: user_locations user_locations_location_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_locations
    ADD CONSTRAINT user_locations_location_id_fkey FOREIGN KEY (location_id) REFERENCES public.locations(location_id) ON DELETE CASCADE;


--
-- Name: user_locations user_locations_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_locations
    ADD CONSTRAINT user_locations_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;


--
-- Name: users users_role_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_role_id_fkey FOREIGN KEY (role_id) REFERENCES public.roles(role_id);


--
-- Name: app_releases; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: app_releases app_releases_authenticated_published_read; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: app_releases app_releases_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: balance_sheet_bf_overrides; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: balance_sheet_bf_overrides balance_sheet_bf_overrides_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: balance_sheet_submission_revisions; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: balance_sheet_submission_revisions balance_sheet_submission_revisions_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: balance_sheet_submissions; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: balance_sheet_submissions balance_sheet_submissions_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: bank_transactions; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: bank_transactions bank_transactions_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cash_drawer_handovers; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: cash_drawer_handovers cash_drawer_handovers_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cash_drawer_handovers cash_drawer_handovers_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cash_drawer_sessions; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: cash_drawer_sessions cash_drawer_sessions_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cash_drawer_sessions cash_drawer_sessions_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: change_basket_updates; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: change_basket_updates change_basket_updates_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: change_basket_updates change_basket_updates_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cheque_bank_deposits; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: cheque_bank_deposits cheque_bank_deposits_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cross_store_refund_lines; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: cross_store_refund_lines cross_store_refund_lines_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cross_store_refund_reconciliation; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: cross_store_refund_reconciliation cross_store_refund_reconciliation_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: cross_store_refund_requests; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: cross_store_refund_requests cross_store_refund_requests_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: email_outbox; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: email_outbox_events; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: email_outbox_events email_outbox_events_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: email_outbox email_outbox_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: employee_payroll_bonuses; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: employee_payroll_bonuses employee_payroll_bonuses_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: employee_payroll_settings; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: employee_payroll_settings employee_payroll_settings_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: employee_schedule_assignments; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: employee_schedule_holidays; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: employee_schedule_holidays employee_schedule_holidays_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: employee_schedule_shifts; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: employee_time_clock_adjustments; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: employee_time_clock_adjustments employee_time_clock_adjustments_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: expenses; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: expenses expenses_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: image_asset_references; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: image_assets; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: invoice_audit_log; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: invoice_audit_log invoice_audit_log_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoice_audit_log invoice_audit_log_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoice_delivery_events; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: invoice_delivery_events invoice_delivery_events_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoice_delivery_events invoice_delivery_events_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoice_delivery_lines; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: invoice_delivery_lines invoice_delivery_lines_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoice_delivery_lines invoice_delivery_lines_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoice_lines; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: invoice_lines invoice_lines_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoice_lines invoice_lines_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoice_payments; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: invoice_payments invoice_payments_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoice_payments invoice_payments_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoice_status_history; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: invoice_status_history invoice_status_history_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoice_status_history invoice_status_history_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoices; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: invoices invoices_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: invoices invoices_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: item_brands; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: item_types; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: lan_api_approvals; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: lan_api_idempotency; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: lan_api_request_audit; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: lan_api_schedule_proposals; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: lan_api_sessions; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: login_security_state; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: login_security_state login_security_state_local_app_access; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: other_income_entries; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: other_income_entries other_income_entries_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: product_shelf_assignments; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: quotation_audit_log; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: quotation_audit_log quotation_audit_log_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: quotation_audit_log quotation_audit_log_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: quotation_lines; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: quotation_lines quotation_lines_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: quotation_lines quotation_lines_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: quotation_status_history; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: quotation_status_history quotation_status_history_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: quotation_status_history quotation_status_history_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: quotations; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: quotations quotations_authenticated_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: quotations quotations_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: remote_admin_commands; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: remote_admin_commands remote_admin_commands_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: security_audit_events; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: security_audit_events security_audit_events_local_app_access; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: security_audit_events security_audit_events_local_app_insert; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: security_audit_events security_audit_events_service_role_access; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: shelf_locations; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: store_sync_status; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: store_sync_status store_sync_status_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_applied_events; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_applied_events sync_applied_events_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_audit_log; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_audit_log sync_audit_log_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_cloud_state; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_cloud_state sync_cloud_state_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_conflicts; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_conflicts sync_conflicts_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_cross_store_inventory_cache; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_cross_store_inventory_cache sync_cross_store_inventory_cache_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_cross_store_inventory_status; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_cross_store_inventory_status sync_cross_store_inventory_status_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_cross_store_return_items_cache; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_cross_store_return_items_cache sync_cross_store_return_items_cache_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_cross_store_returns_cache; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_cross_store_returns_cache sync_cross_store_returns_cache_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_cross_store_sale_items_cache; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_cross_store_sale_items_cache sync_cross_store_sale_items_cache_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_cross_store_sales_cache; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_cross_store_sales_cache sync_cross_store_sales_cache_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_cross_store_sales_status; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_cross_store_sales_status sync_cross_store_sales_status_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_id_map; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_id_map sync_id_map_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_inbox; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_inbox sync_inbox_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_outbox; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_outbox sync_outbox_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_row_mirror_completion; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_row_mirror_completion sync_row_mirror_completion_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_row_mirror_state; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_row_mirror_state sync_row_mirror_state_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_tombstones; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_tombstones sync_tombstones_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: sync_transfer_metrics; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: sync_transfer_metrics sync_transfer_metrics_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- Name: time_clock_auto_close_settings; Type: ROW SECURITY; Schema: public; Owner: -
--


--
-- Name: time_clock_auto_close_settings time_clock_auto_close_settings_service_role_all; Type: POLICY; Schema: public; Owner: -
--



--
-- PostgreSQL database dump complete
--

-- Local-only protected login cache. This object is deliberately absent from
-- Supabase; encrypted password, PIN, and badge verifiers remain on the store
-- server and in service-only recovery snapshots.
CREATE TABLE public.local_auth_cache (
    user_id integer PRIMARY KEY,
    username text NOT NULL,
    full_name text,
    email text,
    badge_id text,
    role_name text,
    location_id integer,
    location_name text,
    location_timezone text,
    pin_salt text,
    pin_hash text,
    is_active boolean DEFAULT true NOT NULL,
    cached_at timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL,
    password_salt text,
    password_hash text,
    pin_cached_at timestamptz,
    password_cached_at timestamptz,
    employee_pin_salt text,
    employee_pin_hash text,
    employee_pin_cached_at timestamptz
);

CREATE INDEX local_auth_cache_badge_idx
    ON public.local_auth_cache (lower(badge_id));
CREATE INDEX local_auth_cache_badge_normalized_idx
    ON public.local_auth_cache (
        upper(regexp_replace(COALESCE(badge_id, ''), '[^a-zA-Z0-9]', '', 'g'))
    );
CREATE INDEX local_auth_cache_email_idx
    ON public.local_auth_cache (lower(email));
CREATE INDEX local_auth_cache_username_idx
    ON public.local_auth_cache (lower(username));
