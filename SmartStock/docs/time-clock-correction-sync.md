# Corrections made from another store

A store publishes its own time-clock records. Other stores keep copies for
payroll reporting. Changing a copied shift previously changed only that copy:
the owning store correctly refused ordinary foreign snapshots, and the edited
copy was excluded from normal outbound time-clock replication.

`TimeClockCorrectionSyncService` delivers explicit correction audits through the
existing `REFERENCE_ROW_CHANGED` relay, using `employee_time_clock_adjustments`
and operation `CORRECT_OWNER`. No cloud function, schema, or installer migration
is required. The owning store remains responsible for calculating and saving
the corrected shift and distributing the resulting authoritative record.

The sender scans locally authored corrections for foreign-owned shifts,
including corrections saved before this fix. Audits received through the inbox
are excluded to prevent history from becoming new commands. Outbox hashes
prevent repeated announcement. All six original punch values accompany each
correction; only the owning store applies it, after checking the employee/session
identity, the author's current active account and time-clock management
permission, and the original punches. Conflicting changes remain failed inbox
events for management review rather than overwriting newer work. The original
adjustment UUID is retained so retries apply exactly once.

Both the sending and owning store server applications must include this fix.
An older receiving server leaves the unfamiliar operation failed in its inbox;
the updated server retries it. Upload acknowledgement alone does not mean the
owning store has applied the correction. Check its inbox status and the actual
employee dashboard before considering delivery complete. Normal foreign payroll
snapshots remain unable to overwrite a store's own shifts.

Validation: `TimeClockCorrectionSyncTest` starts a disposable loopback PostgreSQL
cluster, installs the packaged schema, and exercises historical announcement,
delivery, permission rejection, conflicting punches, replay, and protection
against foreign snapshots. It never connects to an installed store database.

Live verification after deployment requires both store servers online: let the
sending server upload, the owning server apply, and its next sync publish the
result. Verify corrected punches and hours on the owning server and in the
employee's Time Clock. Release preparation does not authorize live installation.
