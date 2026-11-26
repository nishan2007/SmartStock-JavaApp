# SmartStudio 0.1.119 Windows quick update

Windows installations already labeled 0.1.118 can now receive the cumulative SmartStudio update through **Check for updates**.

- PDF Editor uses SmartStock store-server OCR for selected pages and scanned-region correction. **OCR → Check server OCR…** verifies the server connection and configured language.
- This build ships the SmartStock 1.1.153 client. On the store server, an administrator enables Tesseract from **Status → AI Models → Install PDF OCR**.
- Includes editable badge barcodes, QR codes, tables and WordArt, editable PDF barcode inserts, replaceable photo frames, Image Studio design handoff, and sortable Orealla / Siparuta records. See the [cumulative 0.1.116 notes](release-0.1.116.md).

## Verification

- The full SmartStudio Maven run reported 408 tests with zero failures or errors and one skip.
- The Windows updater passed an isolated upgrade from the exact 0.1.118 installation, preserved the Java runtime, and launched natively. The installed application contained server OCR and the exact verified SmartStock 1.1.153 client.
- The hosted update is 175467297 bytes with SHA-256 f4a5718e4fa2293a69fe0edf259058f1b055740e1f3473b1c527d863c385360b.
- The full hosted artifact was downloaded and verified before publication. Independent metadata and notes readback matched.
