# SmartStock 1.1.152 Windows quick update

## Checkout, expenses and business credit

- **Make a Sale** supports the selected store’s internal expense account. Expense checkout records the representative and employee signature, deducts the cart stock once, and posts a noncash entry to that store’s Expense Ledger.
- **Customer Accounts → Transfer Company Expenses** supports a signed administrator transfer of eligible historical entries after confirming the source account. The application update does not itself execute that transfer.
- Finalizing eligible custom orders can post only their unpaid amount to business credit. Reaching or exceeding the limit requires a manager approval. The ledger and cross-store calculations account for deposits once.
- Customer account and custom-order detail endpoints return the information needed for itemized receipts and order sheets. The server checks account edit, account number, credit limit, artwork and order-access permissions.

## Store apps and operations

- The Deckers customer account source includes a credit summary, account editing, the most recent ten transactions and searchable history. Historical receipts and order sheets can show items, customization, permitted artwork, payments, fulfillment, returns and audit history.
- Updated customer account request and history migrations, expense checkout schema and ledger entries, and cross-store refresh support. The package includes the current quote and invoice workflows, custom inventory list and variant handling, register session behavior, and the Orealla / Siparuta permit integration.
- Revised image-review limits handle larger review images within the server’s supported dimensions.

## SmartStudio PDF OCR

- **Status → AI Models → Install PDF OCR** installs a pinned Tesseract 5.5.4 Windows x64 runtime and English language data into the active store profile. SmartStock verifies both downloads and tests recognition before activation. The managed runtime survives application updates and keeps its previous active copy if repair fails.
- Paired SmartStudio devices with an employee session can use the authenticated store connection to recognize page images. SmartStudio retains the PDF and applies the result locally.

## Rollout

Update the Windows store server before registers and SmartStudio. The quick update is for an existing Windows installation. Public web gateway deployment and live workflow or hardware acceptance are separate.

## Release verification

- Windows build 101152. The update feed artifact is 170812383 bytes.
- SHA-256: ae378dcae222a448d1483054070bb86092ab7b5e7f06b3b99add2b49107cf320.
- The full hosted artifact was downloaded and verified before publication. Independent readback matched the artifact and release notes.
- Maven reported 1,680 tests with zero failures or errors and 220 skips. Custom-order pricing and customer history passed against disposable PostgreSQL. The managed OCR download and recognition, hosted model catalogue, and isolated 1.1.151 upgrade, backup, and native launch were verified.
