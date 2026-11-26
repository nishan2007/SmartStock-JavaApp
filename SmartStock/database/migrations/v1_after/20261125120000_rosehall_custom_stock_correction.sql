-- Evidence and completion travel with the existing store-owned recovery marker.
ALTER TABLE public.custom_inventory_legacy_migration
 ADD COLUMN IF NOT EXISTS rosehall_stock_correction_before jsonb,
 ADD COLUMN IF NOT EXISTS rosehall_stock_corrected_at timestamptz;
