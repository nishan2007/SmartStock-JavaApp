# Restoring a revoked mobile device

In **Device Management → Mobile Management**, select the revoked phone. Its
**Revoke phone** button changes to **Restore device**. Review the confirmation
and restore it. The selected device remains selected when the refreshed state
shows it approved again.

Restoration keeps the same device record, app grants and previously linked
browser credentials. It does not add apps, change the name or bypass employee
role permissions. Previously approved browsers regain access, subject to their
normal employee session checks. **Re-pair QR** is a separate action for repairing
the browser connection after the device is approved.

The authenticated LAN endpoint requires Device Management permission, a current
device revision and an idempotency key. It restores only a currently revoked
record. Concurrent/stale changes and already-approved records are rejected, and
successful restoration writes an audit event with the acting employee.

This update is packaged and published without installation or a live service
restart. Install it on the store server and the workstation running Mobile
Management to use the action. Physical hardware behavior is unchanged and is
not verified by these software tests.
