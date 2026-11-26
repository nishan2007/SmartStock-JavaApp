ALTER TABLE public.phone_minutes_sales
    DROP CONSTRAINT IF EXISTS phone_minutes_sales_destination_phone_check,
    DROP CONSTRAINT IF EXISTS phone_minutes_destination_phone_by_customer,
    ADD CONSTRAINT phone_minutes_destination_phone_by_customer
        CHECK (destination_phone ~ '^\+?[0-9]{7,15}$' OR (customer_id IS NOT NULL AND destination_phone = ''));
