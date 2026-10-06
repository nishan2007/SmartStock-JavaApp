# Windows update 1.0.220

Published build 100220 through the existing R2/Supabase updater workflow at the
user's request. The publisher downloaded the uploaded ZIP and verified its size
and SHA-256 before inserting the published release metadata; the returned row
matched the artifact path and hash. This is an application update, not website
activation or production storefront acceptance.

Artifacts in `target/release-windows-1.0.220`:

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| smartstock-windows-1.0.220.zip | 57889813 | f00602293e03e84112432ad8365aaa361c9712eff3091f8b31cad8d17fe5a1f9 |
| smartstock-windows-setup-1.0.220.exe | 70242538 | d6c865f4c6bed487725672b036c177d0b89acfff0016b46e422e90f49c3d5d3c |

The updater ZIP is published; the installer is retained locally. Maven packaging
tests, Bash security check and whitespace checks passed. ZIP layout, Website
Status class, compiled website and storefront SQL were verified. The package
includes all current shared-workspace changes, including catalog galleries and
price-tag work. The running installation, service and live database were not
changed. No installed-app or hardware verification was performed.

Keep online ordering disabled pending server credential setup, Cloudflare
activation, real account/email tests, installed restart and failover acceptance.
The outstanding source/operational limits in storefront-readiness.md still apply.
