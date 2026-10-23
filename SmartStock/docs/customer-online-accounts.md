# Store-hosted customer accounts

Customer passwords are verified by store servers with salted Argon2id hashes.
They are never saved as plaintext, included in register reference data, or sent
to the browser. A private `customer_auth` PostgreSQL schema caches identity
revisions on each serving store. Storefront startup installs its idempotent SQL
resource on a dedicated connection; existing storefront/public schema contracts
remain unchanged. The packaged Maven database resources include this schema.

The existing authenticated HTTPS gateway has one durable identity coordinator.
It serializes revisions and phone claims, retaining encrypted identity envelopes
and opaque keyed contact identifiers. Only configured store servers can access
this internal API. The shared storefront session key encrypts the envelopes;
preserve it securely across stores and recovery. Changing it without migrating
identity envelopes makes existing identities unreadable. No Supabase customer
authentication call is required. Employee authentication is unchanged.

Every login and authenticated request checks current coordinator state. A store
never authorizes from its cached identity when the coordinator is unavailable.
Account/password changes use compare-and-set revisions; a conflict requires a
retry. Password reset invalidates earlier sessions, and logout revocations apply
across serving stores. Account routes choose the lowest measured latency among
healthy servers supporting customer authentication and the selected store data.

## Activation and linking

Activation needs a name, phone and email. All configured stores must confirm
their live customer directory during activation and verification. An unavailable
store pauses activation, while existing customers can still log in through an
available capable server. This conservative check avoids creating a second
customer when a record exists at an offline location.

An existing phone must identify one canonical active customer with the same
recorded email. Verification sends a short-lived one-use code to that email.
Contact mismatches and ambiguous legacy duplicates require staff assistance.
A genuinely new phone/email can create a customer after verification.
Guyana seven-digit numbers normalize to +592; international numbers require their
country code. Verified phone claims are globally serialized, and retired claims
remain reserved. Signup never silently replaces a previous verified claim.

During launch, a customer with a blank recorded email and no existing online
link may instead verify a new email and claim the existing phone-matched account.
The account works immediately, including its existing purchase history, and is
flagged PENDING for employee review. This does not prove ownership of the phone.
Accounts with an existing different email or online link cannot use this path.
The proposed email stays on the online identity/link and does not overwrite the
store customer contact record. No SMS or receipt proof is required.

In Company Preferences > Online Store > Account reviews, employees with Customer
Accounts permission can approve or reject these claims with an audit reason.
Approval clears the flag. Rejection revokes existing sessions on every store and
blocks login, recovery and reset; it preserves the customer record, purchases,
balances, orders already placed and reserved phone claim. Staff must investigate
and correct a rejected claim; rejection does not reverse transactions.

The central launch setting initially allows this path. An administrator with
Company Preferences permission can close it in Account reviews. Closing fences
unfinished verifications atomically and prevents future phone-only linking;
already verified pending accounts continue working unless rejected. Reviews,
decision actor/reason/time and epochs persist in encrypted identity revisions.
The existing private identity JSON schema needs no new SQL columns or register
reference fields. The encrypted review index is shared by all store servers.

Existing Supabase identities must activate and choose a new password; their
existing linked identity/customer IDs are retained. Passwords cannot be copied
from Supabase. Linking keeps the customer's existing purchases and account
history. Activation adds no retroactive loyalty awards.

Staff changes use the existing Customer Accounts permission, enforced on the
server, and require an audit reason. Reassignment advances identity epochs and
revokes the displaced identity. A current identity can repair an interrupted
local link on subsequent enrollment; stale snapshots cannot expose another
customer's history. Existing customer records must synchronize to the serving
store rather than being recreated with another ID.

## Rollout and verification

Keep customer features disabled with the existing browse-only controls until
every store and the gateway run the compatible code.
Customer authentication routes require capability version 2 so an older server
cannot bypass review rejection through its password-reset handler. Configure each origin's
individual key/instance authorization and the same securely managed session key.
Configure and test the store Gmail sender for verification and reset messages.
Register clients receive neither private identity state nor server credentials.

Automated tests cover hashing, envelope integrity, verification, reset/logout,
stale revisions, contact conflicts, phone claims, rate limits and origin routing.
The optional database test uses only an explicit disposable loopback cluster:

```sh
mvn -q -f SmartStock/pom.xml -Dtest=CustomerWebDatabaseTest -Dstorefront.test.admin=jdbc:postgresql://127.0.0.1:55439/postgres test
```

Before enabling, manually verify actual email delivery, signup against an existing
customer, first-time signup, staff reassignment, customer history, password reset,
and failover between real store tunnels. Check unreachable-directory activation
and unavailable-coordinator refusal separately. This change does not deploy the
gateway, migrate a live store, or enable customer features.
