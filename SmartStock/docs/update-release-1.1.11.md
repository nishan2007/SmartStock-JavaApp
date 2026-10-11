# SmartStock 1.1.11 — Shirts labels and default tag printer

Adds a Shirts template for 2 x 1-inch stock, with approximately 0.1-inch blank margins on every edge. Shows only the main item name, large size and colour, price, barcode and SKU. Preserves the other five templates and adds Shirts when decoding older saved settings.

Adds a per-computer default price-tag printer, selectable from Price Tag Printing. The print dialog preselects the saved queue and still permits a different queue for each job. If the saved queue is unavailable, a warning directs the user to choose another queue.

Validation: Maven packaging suite passed (878 tests, zero failures/errors, 47 skipped). Price-tag tests cover stock dimensions, blank edge margins, template serialization and main-name handling. Repository security and whitespace checks passed. Model-free update archive verification passed.

Physical label printing and barcode scanning, installed-app upgrade, live database/services, receipt printer, cash drawer and NFC remain separate checks. Update the server before registers. Publication does not update running installations.

The exact installer was extracted and its native launcher/runtime reached the welcome screen with an isolated development profile. The installer JAR SHA-256 matched the tested Maven JAR. The smoke process was stopped; no production installation was upgraded.

Artifacts: update ZIP 133,313,743 bytes, SHA-256 `ad75a3a5ce4f42c15110eee5181b33b3f21a47907edca654d197113f322a2723`; full Windows installer 546,662,598 bytes, SHA-256 `43186dd87491cdbf5c4c25e2a800531388fe60963ed2f4056c8fbd73b62dd084`. Build 101011.

Publication completed: the complete hosted update ZIP and installer download matched their local byte sizes and SHA-256 before release metadata/latest selection changes. Windows update 1.1.11 build 101011 was published. Public installer HEAD returned HTTP 200, the 1.1.11 installer filename and expected size. Download: https://downloads.deckers.gy/download/windows
