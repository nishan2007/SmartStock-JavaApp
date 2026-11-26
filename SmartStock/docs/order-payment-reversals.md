# Order payment reversals

In Orders, open an order's **Payments** tab, select a received payment, and choose
**Reverse selected payment**. Enter a reason and confirm the full refund.

The `REVERSE_CUSTOM_ORDER_PAYMENTS` permission is granted only to ADMIN by default.
Administrators can assign **Reverse Order Payments** to another role in Roles &
Permissions. The LAN server enforces this permission independently of the UI.

Reversals preserve the original payment and add a refund entry using its payment
method, with the operator, device, reason and payment identity in the audit trail.
The paid amount decreases and the balance due increases. The order total and
items stay unchanged. Pending-price deposits retain their pending-price status.
Each payment can be reversed only once, and the refund cannot exceed the order's
remaining paid amount. Cash refunds require an active assigned drawer session.

Card, cheque, MMG and bank refunds must be completed through the actual payment
provider; SmartStock records the refund and its transaction reference. Account
credit entries are not cash receipts and cannot be refunded through this action.
Invoice-billed orders must be handled through their linked invoice.
