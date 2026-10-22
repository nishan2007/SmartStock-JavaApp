# Shared template deletion

Included in the published Windows releases SmartStock 1.1.53 and SmartStudio 0.1.48 (19 October 2026). Update the SmartStock server before using Delete selected in the Badges, Labels or Business Cards shared template picker. Publishing does not install the update into a running store.

DELETE requires COMPANY_PREFERENCES or MANAGE_CUSTOM_ORDER_ITEMS on the authenticated server, plus access to the owner store. Shared visibility alone does not authorize deletion. The request carries the selected SHA-256 and assignment version; concurrent changes reject deletion. Immutable revisions and deletion metadata remain on the server for recovery, while the active catalogue and refreshed caches omit the deleted entry. Recovery catalogue publication is queued.

The chooser asks for confirmation and explains that deletion affects every store where the design is shared. Already-open documents are not modified. Local templates are not deleted by this shared-library action.

After rollout, remove duplicate MASALA MAN new entry 6fb89bdb-dece-35be-b989-031d5f7eada9, retaining 0b99ce03-7075-3a4b-8c39-5203478225df, only after checking that both current revisions still match. This implementation has not removed either live entry.

Badge print records are retained when a template is deleted; this action does not purge personal history. No live template was deleted while preparing or publishing this release.
