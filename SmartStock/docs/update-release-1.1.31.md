# SmartStock 1.1.31 Windows

Build 101031 includes all current application changes: publishing workspace,
shirt designer and quote review, custom-order size sets and rounded totals,
receipt changes, storefront/mobile improvements, studio acceleration and Windows
network maintenance. SmartStudio 0.1.15 uses this client version.

Validation: 1,005 SmartStock Maven tests, 59 skipped, zero failures; repository
security check; whitespace check; 38 website tests and production frontend build;
39 storefront gateway tests; mobile save transport and network maintenance checks.
Synthetic shirt-designer browser acceptance passed on desktop/mobile, including
editing, uploads, quantity groups, login draft restoration and submission.
The update ZIP passed model-free verification and the hosted model catalogue
readiness check. Exact installer extraction, update/installer JAR identity and
an isolated register-profile launcher startup passed.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| Update ZIP | 161776179 | 076b2f779a9122aaddcd4b88ddc88740e1436755740322c488834f3f71160c2e |
| All-in-one installer | 575140048 | 510477b218490773348d5ecb3ea1e2adad0997ebf58be8d0a5ace88696c86bdf |

The update ZIP and installer uploads were fully downloaded and verified before
release metadata publication and public installer selection. Update metadata
readback matched version, build, object path, byte size and SHA-256.
The complete public installer download returned HTTP 200 with the correct
filename, exact size and SHA-256.
The model-migration helper SHA-256 is
e9017577b34070badac9acc7b47aa91ba76e9d85c1558521e3c125b1905755a7.

The storefront gateway was deployed as version
346ba900-cf7d-47f4-8a2d-816b403e43a0, preserving the deployed origin and
browse-only mode. Public home and health endpoints returned HTTP 200.

Live store installation, database migration acceptance, Windows task/service
and firewall repair acceptance, update/rollback and physical printer, drawer and
NFC checks remain outstanding. macOS packaging requires a Mac. No running store
application was installed or restarted.
