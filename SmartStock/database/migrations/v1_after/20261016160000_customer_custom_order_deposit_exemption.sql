-- Customer-specific custom-order deposit policy. Existing accounts retain the normal minimum.
ALTER TABLE public.customer_accounts ADD COLUMN IF NOT EXISTS custom_order_deposit_exempt boolean NOT NULL DEFAULT false;
