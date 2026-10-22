# Customer account phone ownership

Customer account creation previously had no database phone uniqueness rule.
In particular, `resolveOrderCustomerId` created a new general customer every
time a custom order supplied a typed name/phone without a selected account.
Repeated submissions with distinct request keys could therefore create the
same person again. This explains a code path capable of producing the reported
duplicates; the live Larex accounts have not been inspected.

The server now rejects a typed phone already assigned to an account and tells
staff to select that account. It does not silently select a financial account
based on a typed phone. A PostgreSQL trigger and primary-key-backed ownership
registry enforce the rule for inserts, phone edits, reference synchronization,
and concurrent requests. An expression index supports normalized phone lookup.
Seven-digit Guyana numbers, country-prefixed numbers, punctuation, and `00`
international prefixes share a phone key. Blank phones remain allowed.

Reservations survive deactivation, deletion, and phone changes. The same owner
may keep or restore its number; a different account cannot take it. The base
local schema, ordered migration, and startup schema upgrade include this rule.
No cloud-facing POS database dependency is introduced.

Existing duplicate accounts are preserved until an administrator previews and
merges them in **Customer Accounts → Merge Duplicates**. The preview groups
accounts with matching names (ignoring case and repeated spaces) and normalized
nonblank phone numbers. Different names sharing a phone are not merged. The
lowest customer ID survives; duplicate identities remain inactive and hidden
from the customer-account management list.

The entire batch runs in one transaction on the authoritative LAN server.
Declared customer foreign keys, legacy `customer_id` columns, phone reservations,
other-store history caches, and storefront UUID references move to the survivor.
Transaction amounts and document balances remain unchanged. Stored account
balances must match the ledger before merging, and the combined ledger balance
must match their sum afterward. A conflict, stale preview, or unreconciled balance
rejects the whole batch. The survivor keeps its account settings, credit limit,
and discounts; the survivor stays active if any matching account was active,
and charge authorization remains required if any merged account
required it. Existing website links with different identities require manual
review if their uniqueness rules conflict.

Permanent old-to-new mappings retain the original account UUIDs, record the
administrator, propagate through authenticated cross-store reference sync, and
belong to recovery snapshots. Database reference guards resolve later writes
using retired IDs, including history-cache rebuilds from older snapshots.
Storefront schema version 16 provides equivalent UUID guards. Retired identities
cannot be reactivated, assigned a balance, deleted, or given a new identity.
The shared phone is kept on the survivor and cleared from retired account rows,
so restoring accounts before their mappings cannot collide with phone ownership.
Each merged group is recorded in security audit history. No merge runs merely
because an application/schema upgrade is installed.

Before running against a live store, verify its backup and preview the actual
groups on its server. Production on this development machine is configured as
a register client; these changes do not modify the live Larex accounts.

After the upgrade, this read-only query identifies existing duplicates:

```sql
SELECT public.customer_phone_key(phone) AS phone_key,
       array_agg(customer_id ORDER BY customer_id) AS customer_ids,
       array_agg(name ORDER BY customer_id) AS names
FROM public.customer_accounts ca
WHERE public.customer_phone_key(phone) IS NOT NULL
  AND NOT EXISTS(SELECT 1 FROM public.customer_account_merges m WHERE m.source_customer_id=ca.customer_id)
GROUP BY public.customer_phone_key(phone)
HAVING count(*) > 1;
```

Do not commit query results containing customer data. Reference sync rejects
conflicting phone ownership rather than creating another duplicate; resolve
those conflicts through account review. Live store verification and duplicate
cleanup are separate from the disposable PostgreSQL regression tests.
