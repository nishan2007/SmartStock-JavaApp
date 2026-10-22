# Shared SmartStudio fonts

Account → Shared fonts → Add font files accepts TTF, OTF and TTC files.
An administrator with Company preferences permission adds the files from the
computer that has the original fonts. Use fonts licensed for those computers.
The authenticated store server retains immutable files. Studio clients on that
server check for additions at sign-in and every minute; Sync now checks immediately.
Each client verifies the size and SHA-256 before using a complete snapshot and
keeps that snapshot for offline editing. Shared faces take precedence over local
copies in template rendering; font files are not installed into Windows.

Different versions with the same face name are rejected to prevent an accidental
appearance change. Identical uploads are harmless. The current library supports
adding fonts, not replacing or deleting them. Computers using different store
servers have separate libraries. Back up the server profile's studio-fonts folder
with the server's other data. Font files are not included in application packages.

Both the store server and Studio require an update containing this feature.
Original fonts have not yet been imported or verified on the user's other PCs.

Validation on 21 October 2026: the isolated SmartStock build ran 1,206 tests
with zero failures/errors and 97 environment-dependent skips. SmartStudio ran
227 tests with zero failures/errors/skips; focused font and toolbar tests also
passed. Repository security and whitespace checks passed. A simultaneous build
in another chat invalidated the first shared-output core run; the clean isolated
run is the usable full-suite evidence. Live server/client, installed-app and
physical printing checks remain outstanding. This feature is unpublished and
should be included in the combined release after the other active chats finish.

The native-word audit of all 318 Publisher archives now reports zero flags.
This does not certify all graphics, font metrics or page layouts as exact.
Original-font import from the other computer and visual acceptance are pending.

Release handoff: bump both application versions and Studio's core dependency.
Do not reuse published SmartStock 1.1.70 or Studio 0.1.62 for these new bytes.
The shared-font endpoint and client must ship together. Repaired source customer
templates remain profile data outside packages; application publication alone
does not upload repaired revisions to the template server. Existing migration
checkpoints skip completed entries, so transferring repaired shared revisions
still needs a separate conflict-safe revision step and verification.

Downloaded batch follow-up (21 October): the user confirmed personal use and
requested free personal-use releases too. The private pending batch now contains
44 font files, all parsed successfully by Java. Exact family/face names cover
13 of the 56 missing font names: Amita, Ampera, Aptos, Aptos Black, Aptos
ExtraBold, Aptos Narrow, Dancing Script, Dubai Medium, GROBOLD, Kleader,
Lobster 1.4, Malvie Trial and carbono. Exact names do not certify page layout.

Files came from Google Fonts, Microsoft, Dubai Land Department and the creators'
DaFont/FontSpace pages. Defender's custom scan reported no detections. Preserve
all notices and source records. Multiple unrelated Valentine fonts were downloaded
for identification but are deliberately outside the import queue; installing a
random namesake would change the original design. The other unavailable names
still need original files or an appropriate vendor download.

Private files: C:/Users/decke/.smartstudio/font-import-pending.
Evidence, manifests, remaining-fonts.tsv and the private v2 ZIP are in
C:/Users/decke/Documents/SmartStudio/Shared font import.

After the matching server/client update, online administrator sign-in imports
the batch through the permission-enforced API; Shared fonts also has Import
downloaded batch. Per-server SHA checkpoints resume interrupted batches and
suppress duplicate uploads; original files stay intact. Server acknowledgements
must match uploaded SHA-256 and size. Notices travel with catalogue entries and
are viewable in Shared fonts. This is queued data; live server upload and
cross-computer delivery have not yet been verified.

Never include this private batch in the public application installer. The batch
contains personal-use font releases and Aptos's own-device licence. Client/server
code supports user-supplied fonts, while font files remain profile data. Actual
shared bold/italic faces take precedence over synthetic styling where available.
Focused core and batch tests pass; re-run the combined release gate after ongoing
changes in the other chat are complete.