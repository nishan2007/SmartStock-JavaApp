ALTER TABLE public.company_info ADD COLUMN IF NOT EXISTS individual_customer_template_rename_version integer DEFAULT 0 NOT NULL;
UPDATE public.company_customization
SET customer_card_template_layout_data=REPLACE(customer_card_template_layout_data,'"name":"Personal / Regular"','"name":"Individual"')
WHERE customer_card_template_layout_data LIKE '%"name":"Personal / Regular"%';
ALTER TABLE public.company_customization ALTER COLUMN customer_card_template_layout_data SET DEFAULT '{"version":1,"templates":[{"name":"Teachers","configured":true,"background":{"red":239,"green":246,"blue":255}},{"name":"Business","configured":true,"background":{"red":255,"green":247,"blue":237}},{"name":"School","configured":true,"background":{"red":240,"green":253,"blue":244}},{"name":"Individual","configured":true,"background":{"red":250,"green":245,"blue":255}},{"name":"Template 5","configured":false,"background":{"red":255,"green":255,"blue":255}}]}';
WITH candidate AS (
    SELECT customer_type_id FROM public.customer_types
    WHERE LOWER(BTRIM(name)) IN ('personal / regular','personal','regular','general')
      AND NOT EXISTS (SELECT 1 FROM public.customer_types WHERE LOWER(BTRIM(name))='individual')
    ORDER BY CASE LOWER(BTRIM(name)) WHEN 'personal / regular' THEN 1 WHEN 'personal' THEN 2 WHEN 'regular' THEN 3 ELSE 4 END,customer_type_id
    LIMIT 1
)
UPDATE public.customer_types target SET name='Individual',description='Individual customer accounts',customer_card_template_slot=4
FROM candidate WHERE target.customer_type_id=candidate.customer_type_id;
UPDATE public.customer_types SET customer_card_template_slot=4 WHERE LOWER(BTRIM(name)) IN ('individual','general','personal / regular','personal','regular');
UPDATE public.company_info SET individual_customer_template_rename_version=1 WHERE company_info_id=1;
