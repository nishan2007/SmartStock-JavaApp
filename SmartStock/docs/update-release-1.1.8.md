# SmartStock 1.1.8 — Price-tag brand and variant names

Adds Show brand to price-tag templates, retaining the preference in the existing stored layout data. Brand can be moved, resized and rotated in the layout editor. Selected catalog brand values are included in the print cart and rendered tags. Existing templates leave brand hidden until selected.

Custom items with variants use an explicit line break between the main item name and variant name. The renderer fits both lines inside the existing name box rather than concatenating both names on one line.

Validation: full Maven packaging suite passed (872 tests, zero failures/errors, 47 skipped), including price-tag layout and receipt-printer tests. Repository security and whitespace checks passed. Model-free archive verification passed. The packaged JAR matches the tested JAR. The extracted update launched successfully using an isolated native app copy and temporary development profile, with startup evidence confirming the welcome window was visible; the test process was stopped.

Windows update artifact: `smartstock-windows-1.1.8.zip`, 133,295,314 bytes; SHA-256 `42eb6349ac560cfb888b70db1e616b76cf312329a6febe201dc0a933a22c4f4f`. Build 101008. The publisher verifies the separately hosted model catalogue and downloads the complete stored application artifact to verify size and SHA-256 before release metadata insertion.

Update-only release; no new installer or macOS artifact was built. Installed-app update application, template save/load and physical price-tag printing, live databases/services, backup/restore, cash drawers and NFC remain separate live checks. Update the store server before registers. Publication does not apply the update to running installations automatically.

Publication completed: the complete remote artifact download matched the size and SHA-256 above before metadata publication. Published response matched Windows update 1.1.8, build 101008 and its verified artifact.
