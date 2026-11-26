ALTER TABLE public.product_ad_drafts ADD COLUMN IF NOT EXISTS rejection_reason text CHECK (char_length(rejection_reason) <= 2000);
