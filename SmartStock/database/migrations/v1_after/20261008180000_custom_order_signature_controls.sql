-- Nullable overrides preserve the existing combined preference until saved.
ALTER TABLE public.company_customization
    ADD COLUMN IF NOT EXISTS custom_order_slip_show_team_member_signature boolean,
    ADD COLUMN IF NOT EXISTS custom_order_slip_show_customer_signature boolean;
