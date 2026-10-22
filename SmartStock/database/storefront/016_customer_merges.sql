CREATE OR REPLACE FUNCTION storefront.redirect_merged_customer() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.customer_uuid IS NOT NULL AND to_regprocedure('public.canonical_customer_uuid(uuid)') IS NOT NULL THEN
    NEW.customer_uuid := public.canonical_customer_uuid(NEW.customer_uuid);
  END IF;
  RETURN NEW;
END
$$;
DROP TRIGGER IF EXISTS customer_merge_uuid ON storefront.customer_links;
CREATE TRIGGER customer_merge_uuid BEFORE INSERT OR UPDATE ON storefront.customer_links
FOR EACH ROW EXECUTE FUNCTION storefront.redirect_merged_customer();
DROP TRIGGER IF EXISTS customer_merge_uuid ON storefront.enrollments;
CREATE TRIGGER customer_merge_uuid BEFORE INSERT OR UPDATE ON storefront.enrollments
FOR EACH ROW EXECUTE FUNCTION storefront.redirect_merged_customer();
DROP TRIGGER IF EXISTS customer_merge_uuid ON storefront.orders;
CREATE TRIGGER customer_merge_uuid BEFORE INSERT OR UPDATE ON storefront.orders
FOR EACH ROW EXECUTE FUNCTION storefront.redirect_merged_customer();
INSERT INTO storefront.schema_version(version) VALUES(16) ON CONFLICT DO NOTHING;
