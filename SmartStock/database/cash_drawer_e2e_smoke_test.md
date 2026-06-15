# Cash Drawer / Balance Draw E2E Smoke Test

Run this in a non-production store with two real approved devices and at least two users.

## Preconditions
- `cash_drawers` has at least one active drawer for the test store.
- Both test devices are present in `devices` and approved for the same store.
- User A and User B both have login access and `BALANCE_DRAWER` permission.
- At least one product exists for quick cash sale + refund verification.

## 1) Device Assignment
1. Open `Company Preferences > Cash Drawer Manager`.
2. Assign Device 1 to Drawer X in Store S.
3. Verify row appears in assignments list.

Expected DB checks:
```sql
select cash_drawer_id, location_id, device_id, is_active, assigned_at
from cash_drawer_device_assignments
where location_id = <store_id>
  and device_id = '<device_1_uuid>'::uuid
order by assigned_at desc
limit 1;
```
- `is_active = true`
- `cash_drawer_id` matches Drawer X.

## 2) Open Drawer Session
1. On Device 1, sign in as User A.
2. Open `Operations > Balance Draw`.
3. Start draw/session.

Expected DB checks:
```sql
select cash_drawer_session_id, cash_drawer_id, location_id, device_id,
       status, opened_by_user_id, main_cashier_user_id, current_cashier_user_id,
       opening_cash, opened_at
from cash_drawer_sessions
where location_id = <store_id>
  and device_id = '<device_1_uuid>'::uuid
order by opened_at desc
limit 1;
```
- `status = 'OPEN'`
- `opened_by_user_id = User A`
- `main_cashier_user_id = User A`
- `current_cashier_user_id = User A`

## 3) Cash Sale Hook
1. On Device 1, complete one normal cash sale.
2. Keep the receipt or sale id.

Expected DB checks:
```sql
select sale_id, total_amount, payment_method, cash_drawer_id, cash_drawer_name, cash_drawer_session_id
from sales
where payment_method = 'CASH'
  and location_id = <store_id>
order by created_at desc, sale_id desc
limit 1;
```
- `cash_drawer_id` populated.
- `cash_drawer_session_id` equals active session id.

## 4) Cash Refund Hook
1. Refund the sale as cash in `Return Sale`.

Expected DB checks:
```sql
select return_id, refund_method, cash_drawer_id, cash_drawer_name, cash_drawer_session_id
from sale_returns
where location_id = <store_id>
order by created_at desc, return_id desc
limit 1;
```
- Refund row has drawer + session ids populated.

## 5) Custom Order Cash Payment Hook
1. Create or pick a custom order with balance due.
2. Post a cash payment.

Expected DB checks:
```sql
select custom_order_payment_id, custom_order_id, payment_amount, payment_method,
       cash_drawer_id, cash_drawer_name, cash_drawer_session_id
from custom_order_payments
where payment_method = 'CASH'
order by created_at desc, custom_order_payment_id desc
limit 1;
```
- Cash payment row has drawer + session ids.

Optional customer account check:
```sql
select customer_account_transaction_id, transaction_type, amount, payment_method,
       cash_drawer_id, cash_drawer_name, cash_drawer_session_id
from customer_account_transactions
where payment_method = 'CASH'
order by transaction_date desc, customer_account_transaction_id desc
limit 5;
```

## 6) Handover
1. While session stays OPEN, log in as User B on Device 1.
2. In `Balance Draw`, perform `Confirm Handover` with a counted amount.

Expected DB checks:
```sql
select cash_drawer_handover_id, cash_drawer_session_id, from_user_id, to_user_id,
       expected_cash, counted_cash, variance, handed_over_at
from cash_drawer_handovers
where cash_drawer_session_id = <active_session_id>
order by handed_over_at desc
limit 1;
```
- `from_user_id = User A`
- `to_user_id = User B`

And session ownership updates:
```sql
select cash_drawer_session_id, current_cashier_user_id, current_cashier_name, status
from cash_drawer_sessions
where cash_drawer_session_id = <active_session_id>;
```
- `current_cashier_user_id = User B`
- `status = 'OPEN'`

## 7) Close Session
1. On Device 1, close draw from `Balance Draw` with counted cash.

Expected DB checks:
```sql
select cash_drawer_session_id, status, expected_cash, counted_cash,
       cash_to_remove, variance, closed_by_user_id, closed_at, balanced_by_user_id
from cash_drawer_sessions
where cash_drawer_session_id = <active_session_id>;
```
- `status = 'CLOSED'`
- `closed_at` populated.
- `counted_cash` + `variance` populated.

## 8) Guardrail Checks (Must Fail)
- Try cash sale on a device with no active drawer assignment -> should block.
- Try cash sale with assignment but no open session -> should block.
- Try opening second OPEN session for same drawer/device/store -> should fail via unique index.

## 9) RLS Policy Verification
Run:
```sql
select schemaname, tablename, policyname, roles, cmd
from pg_policies
where tablename in ('cash_drawer_sessions', 'cash_drawer_handovers')
order by tablename, policyname;
```

Expected policies:
- `cash_drawer_sessions_service_role_all`
- `cash_drawer_sessions_authenticated_all`
- `cash_drawer_handovers_service_role_all`
- `cash_drawer_handovers_authenticated_all`
