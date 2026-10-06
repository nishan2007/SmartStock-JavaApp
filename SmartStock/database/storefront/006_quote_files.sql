-- Private reference files for customer quote requests.
CREATE TABLE storefront.quote_files (
 file_id uuid PRIMARY KEY,
 request_id uuid NOT NULL REFERENCES storefront.quote_requests(request_id),
 asset_reference text NOT NULL,
 original_filename text NOT NULL CHECK(char_length(original_filename) BETWEEN 1 AND 180),
 content_type text NOT NULL,
 byte_size integer NOT NULL CHECK(byte_size BETWEEN 1 AND 4194304),
 sha256 text NOT NULL CHECK(sha256 ~ '^[a-f0-9]{64}$'),
 created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(request_id,asset_reference)
);
CREATE INDEX storefront_quote_files_request ON storefront.quote_files(request_id,created_at);
INSERT INTO storefront.schema_version(version) VALUES(6) ON CONFLICT DO NOTHING;
