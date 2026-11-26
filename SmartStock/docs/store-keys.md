# Store keys

Store Keys tracks physical key custody; it does not control a door lock. PostgreSQL on the store server is the authority. Transactions require a live authenticated LAN connection and an enrolled computer at that store. Remote Admin and studio devices cannot record custody.

## Setup and operation

1. In **Store Locations**, select the current location and choose **Store Keys**. The same screen is available under **Employee > Store Keys** and on the main menu.
2. Choose **Set key count** to create numbered slots, then rename each key as needed.
3. Choose **Pair NFC card**. Present a blank supported card or an existing SmartStock key card. Blank cards are written and read back before the pairing is saved. Employee badges are rejected without being overwritten. Attach the card to its physical key.
4. Sign into SmartStock using the employee's normal badge/login. Choose a key from the list or **Tap key card**, then **Sign out key** and confirm. One employee may hold several keys.
5. To return it, select/tap the key and choose **Return key**. The employee can return only their own keys.
6. Authorized managers can select **Record for employee** for ordinary checkout/return. **Recover key** is a separate forced return requiring a reason and confirmation that management physically recovered the key. It does not require employee participation.

Pairing replacement immediately invalidates the old card. A key must be returned before re-pairing or retirement. Reduce the count by selecting the specific returned keys and choosing **Retire key**; history remains intact and later additions receive new numbers.

If a save is unconfirmed, use **Retry last save** to resubmit the same request ID, or **Refresh** to inspect server state before another action. No offline transactions are queued. A card written before a failed pairing save can be presented again; its existing key identifier is reused.

## Permissions

All five permissions are initially granted to ADMIN. Grant other roles explicitly in Roles & Permissions:

| Permission | Capability |
| --- | --- |
| `STORE_KEYS_SELF` | Check out keys for yourself, return your keys, view your custody history |
| `STORE_KEYS_ASSIST` | Record checkout and return for employees assigned to this store |
| `STORE_KEYS_CONFIGURE` | Create numbered keys, rename, pair cards and retire returned keys |
| `STORE_KEYS_FORCE_RETURN` | Record physical recovery without the outgoing holder present |
| `STORE_KEYS_HISTORY` | Search all store custody history |

The server checks permissions, active employee status, store assignment, current key version and custody identity. Ordinary staff see availability and their own holder details; authorized management can see all holders. Only history permission exposes the full store history. Audit events retain employee and recording actor names, server timestamps, method and recovery reason, and reject UPDATE/DELETE.

## Storage and recovery

`store_keys`, `store_key_custody` and `store_key_events` are included in company backups and store-scoped recovery mirrors, in dependency order. They are excluded from live cross-store reference replication: a different store must never become a custody authority. NFC key payloads use a separate `SSK1` namespace and cannot serve as employee credentials. Pairings retain only the payload hash on the server; NFC identifiers are not employee authentication secrets.

Fresh schema provisioning and the ordered local migration install the same constraints and indexes. Existing stores use the guarded schema-contract upgrade; cloud migration only extends the permission catalog, with custody preserved in generic recovery snapshots.

## Validation

Run `mvn -q -f SmartStock/pom.xml test`, the repository security script through Bash, and `git diff --check`.

The optional `StoreKeysDatabaseTest` and `StoreKeysSchemaTest` require a disposable PostgreSQL cluster on loopback port 55447, user `store_keys_test`, and empty databases `smartstock_store_keys_test` and `smartstock_store_keys_schema_test`. Run them with `-Dsmartstock.keys.test.jdbc=jdbc:postgresql://127.0.0.1:55447/smartstock_store_keys_test`. Never point these tests at a store database. The schema test installs the full baseline and exercises an upgrade; the custody tests cover concurrency, permissions, audit preservation, replacement cards, retirement and replay handling.

Physical reader/card operation and installed-app workflows require separate Windows verification. No release publication, installation, or live-store migration is authorized by source implementation alone.
