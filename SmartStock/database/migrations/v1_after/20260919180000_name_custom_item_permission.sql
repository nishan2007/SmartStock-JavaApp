-- Use the existing catalog permission for both the desktop entry point and
-- server mutations. Keep its key and role assignments intact.
UPDATE public.permissions
SET permission_name = 'Add/Edit Custom Items',
    description = 'Allows opening the custom item screen and adding or editing custom items and variants.',
    permission_group = 'Custom Orders',
    permission_subgroup = 'Order Items'
WHERE permission_key = 'MANAGE_CUSTOM_ORDER_ITEMS';
