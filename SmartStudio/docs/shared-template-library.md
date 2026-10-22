# Shared templates: badges, business cards and labels

SmartStock 1.1.37 and SmartStudio 0.1.26 extend the authenticated LAN library to all three template categories. Update the server first, then clients. Building these updates does not install them or restart the live server.

The server profile owns the files under studio-templates. Categories and store locations are isolated. Computers use the authenticated HTTPS LAN API and keep a local cache for opening templates offline. Shared saves require an online session and server-enforced management permission. Conflicting saves are rejected.

The first online session automatically imports the existing badge folder and existing business-card and label .sspub libraries. Migration checkpoints each file, retries failures, deduplicates identical designs, and leaves originals unchanged. It snapshots the existing library once; later files must be imported and saved through SmartStudio. Raw Publisher documents elsewhere on disk are not automatically converted. Reusable publication templates exclude customer merge records and order links. Pages, separate objects, assets and editable properties remain in the archive; sharing does not flatten them.

The server queues immutable revisions and recovery catalogs for the configured OneDrive provider. It verifies uploaded bytes by downloading and hashing them. Pending or failed backup status is displayed and retried. OneDrive provides off-site recovery; the server remains the live editing authority. Fonts still need to be available on each editing computer and are not redistributed in these packages.

Business cards and Labels show shared-library status and a searchable template picker. Export template file remains available for an explicit local copy. Offline changes can be exported locally; reconnect before saving to the shared library.

Automated checks cover chunked archives, category/store isolation, upload ownership, validation, verified backup retry, conflicts, cached reads, interrupted migration, deduplication, old-server detection and sign-out cancellation. Deployment acceptance still requires two actual clients, the production LAN, and a real OneDrive revision and recovery catalog.

## Store folders (SmartStock 1.1.38 / SmartStudio 0.1.27)

Every template category defaults to the signed-in store folder. The picker has a
named store selector and All accessible stores view. Store ownership and sharing
can be changed with Store ownership / sharing. New designs belong to the signed-in
store regardless of the browsing folder. The creation store is retained for history.
A shared design appears in every selected store folder without duplicating its
artwork. Content edits preserve its current associations. Staff can browse stores
assigned to their account and designs explicitly shared to those stores. Changing
ownership or sharing requires template management permission and access to the
owner and destination stores; the server enforces this on every operation.

New physical partitions include the configured store name, such as store-1-Skeldon.
Existing partitions retain their paths. Renaming a store or changing a template's
owner changes its displayed store folder; the original physical revisions remain
in the creation partition so backup identities and history stay stable. Category
and stable store IDs keep identically named stores and templates separate. The
verified recovery catalog carries the current ownership/sharing metadata; use it
together with the referenced revision envelope during recovery.

This is organization within one server library. Automatic live synchronization
between independent store servers is not added by the store selector. Clients
must connect to the server holding the shared library. OneDrive remains recovery
storage, not a live cross-server editing service.
