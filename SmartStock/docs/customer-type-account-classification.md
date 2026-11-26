# Customer type is the account classification

Customer accounts store `customer_type_id` only. Business classification is derived from the selected type name (`Business` or the existing `Businesses` alias, case-insensitive and trimmed). Other types are personal classifications. There is no separate business checkbox or persisted business flag.

The desktop Customers, account-details, quick-create and employee customer web forms all save the selected type. POS, customer filters and quotation/invoice selection derive business classification from that type. Existing API read responses retain a computed `business` value for older clients; it is not an independent field or accepted write setting. Old write payloads containing `business` are ignored.

## Upgrade

`20261116160000_customer_type_account_classification.sql` moves accounts previously marked business onto an existing Business/Businesses type, or creates Business if necessary. Account numbers, limits, balances and transaction links remain unchanged. A legacy business flag takes precedence over an inconsistent old non-business type during this one-time conversion. Accounts already on a Business type retain it. The migration replaces account-number generation and drops `customer_accounts.is_business`.

New accounts receive BA numbers for Business types and CA numbers otherwise. Changing a type does not renumber an existing account. The migration is idempotent and runs through the shared local provisioning and LAN schema upgrade paths. The existing `customer_accounts_customer_type_id_idx` and customer-types primary key support the joins and lookups; no redundant index is needed.

Customer types are restored before accounts in the shared reference/recovery ordering. Legacy recovery rows translate their removed flag into the Business type before insertion and discard the obsolete property. New mirrors contain only the actual target columns. The cloud baseline contains recovery JSON rather than a separate customer account table, so no cloud relational column change is needed.

Deploy the updated store server before updated clients, because the current save request no longer sends an independent business flag. This source change does not install or migrate a live store. Pre-upgrade backups should be retained for application rollback; old binaries that require the dropped column cannot run against the upgraded schema.

Validation includes disposable PostgreSQL tests for fresh provisioning, repeat migrations, preserved numbers/balances, type-based numbering, old recovery rows, and actual account save/read/type-switch behavior. Hardware, installed-app, and live database verification are separate steps.
