# SmartStock 1.1.153 Windows quick update

This release makes the cumulative Windows update available to stores that already ran an earlier build labeled 1.1.152. It includes the cumulative changes from the published 1.1.152 release and a Windows OCR installer fix.

- Update the SmartStock store server to 1.1.153. Sign in as an administrator and open **Status → AI Models → Install PDF OCR** to download and verify Tesseract and its English language data.
- SmartStudio 0.1.116 already supports server OCR. In the PDF Editor, open **OCR → Check server OCR…** after the server installation completes.
- This package also carries the store expense checkout, business credit approvals, customer account history and other changes in the [cumulative 1.1.152 notes](release-1.1.152.md).

- The installer tests Tesseract in its final location before activation. This avoids renaming a folder that Windows may still have open.

## Verification

- The isolated Windows copy upgraded from the exact earlier JAR captured from POS-SERVER. The updater preserved its backup and model directory, installed the verified new JAR, and opened the native welcome screen.
- Focused Maven tests covered OCR installation, recognition, model management, and the LAN API client.
- The hosted Windows build 101153 is 170812290 bytes. SHA-256 01eea46894ac1a36516f437e04b8104e442571d7631f6135f77d908878333096.
- The full hosted download was checked before publishing, and independent readback matched the artifact and release notes.
