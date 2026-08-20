-- First-administrator bootstrap needs the built-in role identities before the
-- local reference-data mirror has started. Keep the stable v1 IDs aligned with
-- the local seed; later reference synchronization supplies permissions.
INSERT INTO public.roles (role_id, role_name, description, created_at, updated_at)
VALUES
    (1, 'ADMIN', 'Administrator', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'MANAGER', 'Manager', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'USER', 'User', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (role_id) DO UPDATE SET
    role_name = EXCLUDED.role_name,
    description = EXCLUDED.description,
    updated_at = GREATEST(public.roles.updated_at, EXCLUDED.updated_at);

SELECT pg_catalog.setval(
    pg_get_serial_sequence('public.roles', 'role_id'),
    GREATEST((SELECT MAX(role_id) FROM public.roles), 1),
    TRUE
);
