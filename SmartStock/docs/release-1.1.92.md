# SmartStock 1.1.92 combined Windows quick update

Build 101092, prepared 27 October 2026 from the frozen current shared workspace
at `D:/SmartStock-combined-1.1.92/source`. Source manifest and comparison against
the previous combined release are retained outside Git in that release folder.

Includes drawer shortage/overage attribution across accepted handovers and the
audited takeover flow for a cashier who cannot obtain outgoing confirmation.
All prior shared application changes remain included. SmartStudio 0.1.88 uses
the exact JAR produced by this build.

- Update ZIP: `smartstock-windows-1.1.92.zip`, 168,571,853 bytes.
- SHA-256: `38c94a6ec823a8785d2690e96f19a88f6963dcc0fb7e4ea309c7cc52d70f0390`.
- Maven: 1,390 tests, zero failures/errors, 151 skipped.
- Website: 70 passed; Workers: 125 passed; current web tests: 18 passed.
- Security and whitespace checks passed.
- Model-free archive verification, isolated update/rollback/reapply, persistent
  profile preservation, installed JAR/resources/runtime and native startup passed.

Update the server first, then registers. Live database/service/cash-drawer and
store workflow acceptance remains outstanding; database-dependent tests were
among the skips. This release does not install into the live store, deploy
separately hosted Workers/websites, change first-install selections, or produce
a macOS artifact. Models remain separate from the application update.

Published to the Windows application update feed on 27 October 2026 after full
hosted download, size and SHA-256 verification. Independent metadata verification
passed for version/build, publication status, path, size and hash.
