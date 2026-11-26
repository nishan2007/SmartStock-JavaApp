# Work Queue

Open **Orders → Work Queue**, or the **Work Queue** tile on the main menu.

The native Swing board combines active custom orders and sales tasks from the
authenticated store LAN API. New/received jobs appear in New orders; assigned,
in-progress, and completed jobs awaiting pricing appear in In progress. Ready
jobs with final pricing appear in Ready. Delivered, collected, and cancelled
jobs are excluded by the server.

Search matches job number, customer, phone, staff, and instructions. Work type,
assigned staff, My work, and deadline filters combine. Deadlines use the store
timezone. Due soonest puts ASAP work first and jobs without deadlines last.
Cards support mouse activation and Enter/Space and open the existing editor.
Work Queue and both job editors use the shared full-screen navigation flow.
The server continues to enforce store and user access independently of filters.

Catalogue images, design approval, partial payments, and balances come from
the saved job data. Jobs without catalogue images remain text cards. Pending
prices are labelled explicitly rather than presented as final balances.
No example jobs from the design are inserted into the store.

The desktop reference is 1672 × 941 including window chrome. Render tests write
the 1672-wide content view and a compact 1120-wide view to
`target/work-queue-design/`. Compact controls wrap to two rows and each lane
scrolls independently. No application release or live installation is required
to run these tests.

Ready stays a compact single column. New orders and In progress share the
remaining width by their visible job counts after filtering, with a readable
minimum for each section and multiple card columns as space allows. Empty
sections keep their heading and empty state. Resizing, refreshing and filtering
recalculate the production allocation.

Validation commands:

```powershell
mvn -q -f SmartStock/pom.xml test
& 'C:/Program Files/Git/bin/bash.exe' SmartStock/tools/security-check.sh
git diff --check
```

Live database/image fetching and installed-app navigation require a configured
store session. Rendering and unit tests do not establish those deployed results.
