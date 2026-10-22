-- Additional early production steps; existing line statuses remain unchanged.
ALTER TABLE public.custom_order_lines
    DROP CONSTRAINT IF EXISTS custom_order_lines_production_status_chk;
ALTER TABLE public.custom_order_lines
    ADD CONSTRAINT custom_order_lines_production_status_chk CHECK (
        production_status IN ('NOT_STARTED', 'STARTED', 'WAITING_FOR_APPROVAL',
                              'DESIGN_APPROVED', 'PRINTED', 'FINISHED', 'QUALITY_CHECKED', 'READY')
    );
