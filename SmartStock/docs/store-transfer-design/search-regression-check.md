# Transfer search regression check — 2 November 2026

The affected register is another computer and the user reports it is on the
latest release. The locally installed 1.1.99 process and its login dialog are
not evidence of the cause on that register. No physical scanner or frozen thread
dump from the affected register has been obtained, so remote resolution remains
unverified.

Changes beyond published 1.1.102:

- Scanner Enter can wait for the current asynchronous lookup and add exactly
  one unambiguous match. Stale and duplicate responses cannot add items.
- Every query edit and Escape invalidate pending callbacks immediately, including
  edits that return to the original query before debounce expires.
- Results remain inline and do not select a global Swing menu path.
- The 300-result list reuses one plain renderer, with fixed row measurements,
  instead of creating HTML label layouts.

Coverage includes repeated additions and continued searching, delayed and
superseded responses, Escape during a scan lookup, 300 search results, simulated
network failure and recovery, and a visible two-window test through the actual
UiTaskRunner. During the pending request, a separate window's button remains
responsive; all transfer search controls remain enabled. Existing tests also
cover create, receive, edit, cancel, stock deltas, permissions, idempotency,
loading/failure states, and read-only transfers awaiting confirmation.

The disposable full-schema database check exercises name/size, primary barcode,
alternate barcode, empty results, store isolation and permission denial.

Validation passed: full isolated native Maven suite (1,450 tests, zero failures,
zero errors, 148 platform/opt-in skips), all 12 transfer UI/controller cases,
the expanded disposable PostgreSQL case, repository security check and
`git diff --check`. The final focused tests were repeated after adding the
database search assertions and passed without skips.

These are source-level and isolated runtime checks. They do not certify the
affected register's installed bytes, physical scanner suffix/settings, local
Windows input state, or live network/server behavior. No store installation is
changed by these tests.
