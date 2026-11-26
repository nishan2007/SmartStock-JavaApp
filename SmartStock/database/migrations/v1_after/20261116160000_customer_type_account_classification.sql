-- Customer type replaces the independent business-account flag.
-- Preserve existing account numbers and balances. Only the type of legacy
-- business accounts is reconciled before the redundant column is removed.
DO $$
DECLARE business_type_id integer;
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public'
               AND table_name='customer_accounts' AND column_name='is_business') THEN
        SELECT customer_type_id INTO business_type_id FROM public.customer_types
        WHERE LOWER(BTRIM(name)) IN ('business','businesses')
        ORDER BY is_active DESC, customer_type_id LIMIT 1;
        IF business_type_id IS NULL THEN
            INSERT INTO public.customer_types(name,description,is_active,auto_print_sale_receipt,customer_card_template_slot)
            VALUES ('Business','Business customer accounts',true,true,2)
            RETURNING customer_type_id INTO business_type_id;
        END IF;
        UPDATE public.customer_accounts ca
        SET customer_type_id=business_type_id,updated_at=CURRENT_TIMESTAMP
        WHERE ca.is_business AND NOT EXISTS (SELECT 1 FROM public.customer_types ct
            WHERE ct.customer_type_id=ca.customer_type_id AND LOWER(BTRIM(ct.name)) IN ('business','businesses'));
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.assign_customer_account_number() RETURNS trigger
LANGUAGE plpgsql SET search_path TO 'public','pg_temp' AS $$
BEGIN
    IF NULLIF(TRIM(NEW.account_number),'') IS NULL THEN
        IF EXISTS (SELECT 1 FROM public.customer_types ct WHERE ct.customer_type_id=NEW.customer_type_id
                   AND LOWER(BTRIM(ct.name)) IN ('business','businesses')) THEN
            NEW.account_number := 'BA-' || LPAD(NEXTVAL('customer_account_ba_number_seq')::TEXT,6,'0');
        ELSE
            NEW.account_number := 'CA-' || LPAD(NEXTVAL('customer_account_ca_number_seq')::TEXT,6,'0');
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

ALTER TABLE public.customer_accounts DROP COLUMN IF EXISTS is_business;
