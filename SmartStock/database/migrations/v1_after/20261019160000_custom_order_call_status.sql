-- Contact attempts remain undelivered until staff record actual delivery.
ALTER TABLE public.custom_order_lines
    DROP CONSTRAINT IF EXISTS custom_order_lines_delivery_status_chk;
ALTER TABLE public.custom_order_lines
    ADD CONSTRAINT custom_order_lines_delivery_status_chk CHECK (
        delivery_status IN ('PENDING', 'CALLED', 'CALLED_NO_ANSWER', 'DELIVERED')
    );
