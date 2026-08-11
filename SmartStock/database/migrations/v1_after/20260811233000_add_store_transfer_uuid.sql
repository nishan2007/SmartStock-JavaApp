ALTER TABLE public.store_transfers
    ADD COLUMN IF NOT EXISTS transfer_uuid uuid;

UPDATE public.store_transfers
SET transfer_uuid = gen_random_uuid()
WHERE transfer_uuid IS NULL;

ALTER TABLE public.store_transfers
    ALTER COLUMN transfer_uuid SET DEFAULT gen_random_uuid(),
    ALTER COLUMN transfer_uuid SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS store_transfers_transfer_uuid_uidx
    ON public.store_transfers(transfer_uuid);
