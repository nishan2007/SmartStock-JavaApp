# SmartStudio 0.1.26: shared templates in every category

Badges, business cards and labels now use the store server as their shared file authority. See [Shared template library](shared-template-library.md) for migration, permissions, offline caches and verified OneDrive backup behavior.

## Validation

- Full SmartStock Maven package: 1,041 tests, 61 skipped, no failures/errors.
- Full SmartStudio Maven package: 82 tests, no skips or failures/errors.
- Git Bash repository security check and git diff --check passed.
- SmartStock 1.1.37 model-free Windows update ZIP passed the update verifier and isolated native startup using the existing launcher and rewritten version configuration. No new server installer was built.
- SmartStudio 0.1.26 Windows installer and portable ZIP prepared; exact installer extraction and native launch acceptance recorded separately alongside local artifacts.
- Production server, live database, installed clients, real OneDrive template revisions and two-computer synchronization have not been deployed or accepted by this change. No running SmartStock application was restarted. No release was published.

Server update ZIP SHA-256: 645d909392fa495ec586446b336beeef8383004a20b48fcfee0b9b3f0b2dc4eb.
SmartStudio portable ZIP SHA-256: f64a4f7d5e9c56d77fe521fefe0d574dbc5eaf9c29361f843517d579626cbb8c.

Update SmartStock server 1.1.37 first, then SmartStudio clients 0.1.26. The current live server cannot serve the new card/label library until updated. Original local files are retained. Font availability and physical printing/scanning remain separate checks.

Exact final SmartStudio installer extraction, model-free inventory, dependency contents and native launcher check passed. Installer SHA-256: b1d6f3505aea97aa29c374653066bc4b73941bb62701eb00306787fd908488c7.
