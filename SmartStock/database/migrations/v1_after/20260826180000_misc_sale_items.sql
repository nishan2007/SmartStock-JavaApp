ALTER TABLE public.sale_items
    ADD COLUMN IF NOT EXISTS item_name text,
    ADD COLUMN IF NOT EXISTS is_misc_item boolean DEFAULT false NOT NULL;

ALTER TABLE public.held_cart_items
    ADD COLUMN IF NOT EXISTS is_misc_item boolean DEFAULT false NOT NULL;

ALTER TABLE public.sync_cross_store_sale_items_cache
    ADD COLUMN IF NOT EXISTS is_misc_item boolean DEFAULT false NOT NULL;

INSERT INTO public.permissions(permission_key,permission_name,description,permission_group,permission_subgroup,created_at)
VALUES ('ADD_MISC_SALE_ITEM','Add Misc Sale Item','Allows adding an arbitrary named and priced non-inventory item to a sale.','Point of Sale','Checkout',CURRENT_TIMESTAMP)
ON CONFLICT (permission_key) DO UPDATE SET permission_name=EXCLUDED.permission_name,
 description=EXCLUDED.description,permission_group=EXCLUDED.permission_group,
 permission_subgroup=EXCLUDED.permission_subgroup;

INSERT INTO public.role_permissions(role_id,permission_id,updated_at)
SELECT r.role_id,p.permission_id,CURRENT_TIMESTAMP FROM public.roles r CROSS JOIN public.permissions p
WHERE UPPER(r.role_name)='ADMIN' AND p.permission_key='ADD_MISC_SALE_ITEM'
ON CONFLICT (role_id,permission_id) DO NOTHING;

UPDATE public.products SET name='Misc Item',description='System anchor for miscellaneous sale items.',
 cost_price=0,price=0,product_type='NON_INVENTORY',is_active=false
WHERE sku='SMARTSTOCK-MISC';

INSERT INTO public.products(name,size,sku,description,cost_price,price,product_type,is_active)
SELECT 'Misc Item',NULL,'SMARTSTOCK-MISC','System anchor for miscellaneous sale items.',0,0,'NON_INVENTORY',false
WHERE NOT EXISTS (SELECT 1 FROM public.products WHERE sku='SMARTSTOCK-MISC');
