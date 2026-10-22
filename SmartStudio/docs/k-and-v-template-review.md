# K & V review — 22 October 2026

Only K&V was updated. The shared server held a stale 54-object design with no
line elements. The prior local repair had 94 native grid segments but still had
three black converted totals images. The corrected reusable template restores
the native grid and uses three editable white, black-outline totals boxes.
It retains 51 editable text fields; empty table cells now have a usable 9pt
default instead of an oversized imported 18pt setting. Editing a quantity cell
and saving/reopening was verified visually and by archive round-trip.

The existing Skeldon K&V template ID was updated through authenticated HTTPS,
using the previous SHA as a conflict check. Ownership/sharing was preserved.
A complete read-back matched SHA-256
20e06582de60df1c1ba6e8d0b93fcf35b0d435409216109d03d24a73552883fe.
The local shared cache and both source-library K&V copies match those bytes.
Prior server/local archives and previews are retained outside Git in
Documents/SmartStudio/K-and-V-review. No app release or other template was changed.

The user will verify K&V before authorizing the remaining template corrections.
Cross-computer display and physical printing remain user checks. Bulk repair
and shared revision transfer must wait for that verification.

Follow-up: the user accepted K&V on 22 October and explicitly authorized the
remaining template repairs. See remaining-bill-book-table-repairs.md for the
verified server transfer and residual visual/hardware limits.
