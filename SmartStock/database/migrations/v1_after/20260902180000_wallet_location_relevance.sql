ALTER TABLE public.locations
    ADD COLUMN IF NOT EXISTS wallet_relevance_latitude double precision,
    ADD COLUMN IF NOT EXISTS wallet_relevance_longitude double precision;

ALTER TABLE public.locations
    DROP CONSTRAINT IF EXISTS locations_wallet_relevance_coordinates_check;

ALTER TABLE public.locations
    ADD CONSTRAINT locations_wallet_relevance_coordinates_check CHECK (
        (wallet_relevance_latitude IS NULL AND wallet_relevance_longitude IS NULL)
        OR
        (wallet_relevance_latitude BETWEEN -90 AND 90
         AND wallet_relevance_longitude BETWEEN -180 AND 180)
    );
