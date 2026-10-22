# SmartStudio 0.1.61 shared bill book fix

Windows build 1061, SmartStock 1.1.68 client dependency. Published 21 October 2026.

Corrects the local-only Bill Books implementation in 0.1.60. Bill Books now
uses a dedicated BILL_BOOKS category on the authenticated local store server,
with existing ownership/sharing, permissions, verified caches, conflict handling
and OneDrive backup. Local converted designs migrate after online sign-in;
later converted files are discovered without repeating completed uploads.
Picker updates and library operations use the bill book category, and sign-out
stops its synchronization. Customer originals and merge records are not bundled.

Deployment order: update the SmartStock server to 1.1.68, update Studio on the
importing computer and other clients to 0.1.61, then sign in online on the
importing computer. Its local template-library/bill-books collection must finish
migration before other computers in the same authorized store can download it.
Publishing alone does not upload templates or install updates on live machines.
Cross-store access remains governed by the existing store sharing controls.

Installer: SmartStudio-setup-0.1.61.exe
Size: 190007613 bytes
SHA-256: 77c0ec736b5dd71d91a02e4587603adda1ece619e72250071152b2cc302618af

Validation: all 203 Studio tests passed, including actual bill book workspace
shared-cache initialization, second-client download and discovery of later
conversions. All 1181 core tests completed with zero failures/errors (96 skipped),
including category isolation and editable server archive round-trip. Repository
security and whitespace checks passed. The exact final installer passed native
startup, JAR identity and model/customer-file exclusion. Both apps carry the
identical final shared core JAR. Hosted bytes were fully verified before release
metadata/public selection changed; metadata and the independent complete public
download match version, build, immutable object path, size and SHA-256.

Evidence: target/release-0.1.61-* logs and metadata.json, plus the combined
source snapshot under SmartStock/target/release-windows-1.1.68. An unpublished
Studio installer was rebuilt after the server packaging retry changed its JAR
container bytes; only the final accepted hash above was published.

Live installation, authenticated template migration on the actual server,
other-computer visibility, font/layout review and physical printing still need
verification after updates. No live installation or macOS artifact was changed.
