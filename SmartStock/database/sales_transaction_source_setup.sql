ALTER TABLE sales
ADD COLUMN IF NOT EXISTS transaction_source TEXT;

UPDATE sales
SET transaction_source = 'Java_app'
WHERE transaction_source IS NULL OR BTRIM(transaction_source) = '';
