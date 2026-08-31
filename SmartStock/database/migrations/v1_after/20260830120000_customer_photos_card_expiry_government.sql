ALTER TABLE public.customer_accounts ADD COLUMN IF NOT EXISTS customer_photo_url text;
ALTER TABLE public.customer_accounts ADD COLUMN IF NOT EXISTS customer_card_issued_on date;
ALTER TABLE public.customer_accounts ADD COLUMN IF NOT EXISTS customer_card_expires_on date;

ALTER TABLE public.customer_accounts DROP CONSTRAINT IF EXISTS customer_accounts_card_dates_chk;
ALTER TABLE public.customer_accounts ADD CONSTRAINT customer_accounts_card_dates_chk CHECK (
  customer_card_expires_on IS NULL OR
  (customer_card_issued_on IS NOT NULL AND customer_card_expires_on >= customer_card_issued_on)
);

INSERT INTO public.customer_types(name,description,is_active,auto_print_sale_receipt,customer_card_template_slot)
SELECT 'Government','Government customer accounts',true,true,5
WHERE NOT EXISTS (SELECT 1 FROM public.customer_types WHERE LOWER(BTRIM(name)) IN ('government','governmental','govt','public sector'));

UPDATE public.customer_types SET customer_card_template_slot=3
WHERE LOWER(BTRIM(name)) IN ('school','schools');
UPDATE public.customer_types SET customer_card_template_slot=5
WHERE LOWER(BTRIM(name)) IN ('government','governmental','govt','public sector');

UPDATE public.company_customization
SET customer_card_template_layout_data=jsonb_set(
      customer_card_template_layout_data::jsonb,
      '{templates,4}',
      '{"name":"Government","configured":true,"background":{"red":241,"green":245,"blue":249},"header":{"red":15,"green":76,"blue":92},"backgroundImage":"","layoutData":""}'::jsonb
    )::text,
    updated_at=NOW()
WHERE jsonb_typeof(customer_card_template_layout_data::jsonb)='object'
  AND jsonb_array_length(customer_card_template_layout_data::jsonb->'templates')=5
  AND (
    COALESCE((customer_card_template_layout_data::jsonb->'templates'->4->>'configured')::boolean,false)=false
    OR LOWER(COALESCE(customer_card_template_layout_data::jsonb->'templates'->4->>'name',''))='template 5'
  );

ALTER TABLE public.company_customization ALTER COLUMN customer_card_template_layout_data SET DEFAULT
'{"version":1,"templates":[{"name":"Teachers","configured":true,"background":{"red":239,"green":246,"blue":255}},{"name":"Business","configured":true,"background":{"red":255,"green":247,"blue":237}},{"name":"School","configured":true,"background":{"red":240,"green":253,"blue":244}},{"name":"Individual","configured":true,"background":{"red":250,"green":245,"blue":255}},{"name":"Government","configured":true,"background":{"red":241,"green":245,"blue":249},"header":{"red":15,"green":76,"blue":92}}]}';
