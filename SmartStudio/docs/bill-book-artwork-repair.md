# Bill book artwork repair — 21 October 2026

The Publisher object exporter produced near-black table PNGs even where its
reference PDF showed white paper. Some earlier imports also discarded uniform
black table images, exposing the converter's default green cell fill and fitted
text. This was separate from missing source fonts.

`PublicationTableArtworkRepair` recovers affected table artwork from the retained
reference PDF using the original table geometry. Separate text cells remain
editable. It retains genuinely dark reference artwork and checks page dimensions.
New editable imports use the same repair. Numbering/NUM variants are excluded
from Bill Books discovery, migration choices, and transfer enumeration.

`BillBookLibraryCleanup` checks transfer hashes before changing existing designs,
backs up changed archives, archives numbering variants outside the active library,
and writes a TSV manifest. It can resolve duplicate-source reference PDFs through
the transfer checkpoint's source hashes. Original Publisher files are retained.

The local cleanup repaired 295 distinct templates (957 object exports across
successive passes), archived 137 numbering variants, and left 324 active templates
with no NUMBERING/NUM variants. Backups and manifests are in the user's Documents
folder under `SmartStudio/Bill Book Transfer/library-cleanup-*`.

Validation: 207 Studio tests passed; repository security check and diff whitespace
check passed. The reported black-block example and additional repaired previews
were visually checked. This is not a complete comparison against Microsoft
Publisher rendering: font substitutions and source layout differences can remain.
Physical printing and other computers' shared-library contents were not verified.
These archive repairs are local. The new importer/filter code is included in
published SmartStudio 0.1.62; publishing the application does not upload customer
templates or confirm shared-server migration.
