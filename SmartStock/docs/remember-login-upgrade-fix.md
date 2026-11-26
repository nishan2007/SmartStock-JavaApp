# Existing-store remembered-login migration correction

The 1.1.135 remembered-login migration was registered in the fresh baseline but
was missing from the existing-store upgrade hooks. On an existing store upgraded
to 1.1.136, employee session queries referenced remember_device before the column
existed, producing the generic request error on open/sign-in.

SchemaContractService now has ensureEmployeeWebRememberDeviceUpgrade, called by
both normal local readiness and LanApiSchemaInstaller. The method checks the
recorded catalogue fingerprint, serializes concurrent upgrade attempts, applies
the packaged idempotent migration, and updates schema metadata atomically.
Unrelated schema drift is rejected. Existing sessions default to temporary.

A disposable PostgreSQL regression test covers drift rejection without mutation,
session preservation, false/non-null default, metadata fingerprints and repeated
execution. The upgrade test, schema unit test and employee security tests passed.

The affected live store was diagnosed using read-only metadata queries. It lacked
the column and its catalogue matched the recorded fingerprint. After the isolated
test passed, the exact guarded migration was applied using a maintenance helper,
without replacing application files, installing software or restarting services.
The full local contract then validated and no sessions had null remember flags.
Operator helpers/evidence remain outside Git under D:/SmartStock-release-builds.
The application-side upgrade hook is in source for the next packaged update.

Real iPhone login acceptance remains a user check; no employee password or browser
credential was requested, exposed or changed during diagnosis/repair.
