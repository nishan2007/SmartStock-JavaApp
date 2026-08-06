ALTER TABLE public.other_income_entries
DROP CONSTRAINT IF EXISTS other_income_whole_gyd_chk;

ALTER TABLE public.other_income_entries
ADD CONSTRAINT other_income_whole_gyd_chk
CHECK (amount = TRUNC(amount));
