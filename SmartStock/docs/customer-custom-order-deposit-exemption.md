# Customer custom-order deposits

In Customer Accounts, select a customer, enable **Allow custom orders without a
deposit**, and save. The same setting is available in Customer Account Details.
Changing it requires `CUSTOMER_ACCOUNTS` and either
`CUSTOM_ORDER_DEPOSIT_OVERRIDE` or `CUSTOM_ORDER_OVERRIDES`; the LAN server enforces
these permissions.

The option defaults to off. When enabled, a new order for that selected account
has a zero minimum deposit. The customer may still pay a deposit, and any unpaid
amount remains due in full. Walk-in customers and accounts without this setting
continue to use the store minimum and existing manager-override workflow.
The payment screen identifies the customer account exemption.

The server reloads the active customer account when saving an order; a client
cannot grant itself an exemption. Finalizing a deferred-price order also checks
the saved account policy. Existing finalized orders are not recalculated when
the customer setting changes. Older account-editing clients that omit the new
field preserve its saved value.

The local baseline and ordered migration add
`customer_accounts.custom_order_deposit_exempt` as a non-null boolean with a
false default. The schema contract installs the upgrade on existing stores and
includes it in provisioning. Customer search and account LAN responses carry
the setting. Cloud row mirroring and recovery use discovered columns, so this
field follows the existing customer row without a separate column list or index.

Validation includes JDBC-fixture tests for permissions, backward compatibility,
forged client flags, order acceptance, and the unpaid balance. Before deployment,
verify the migration against a disposable PostgreSQL database and the installed
Swing account-editing and custom-order payment screens. No release or live-store
installation is part of this change.
