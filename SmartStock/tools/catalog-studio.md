# Catalogue studio photos

This workflow creates consistent white-background images from real product photos. It keeps the original primary image and appends approved output to the product gallery. No image is imported until its `review.csv` decision is `approve`.

## Setup

The Windows updater places this guide and `catalog-studio.py` in the installed application's `app\dependency\catalog-studio` folder. Use a private folder outside the installation for output. It contains product photos and an employee-specific work record; do not commit or sync it as source code. Install Python 3.11+ and the local packages `Pillow`, `rembg`, and `onnxruntime` in a virtual environment. Select rembg's `u2net` model; do not substitute BRIA RMBG 2.0 for commercial catalogue work.

Use the SmartStock Mobile Item Web App on the store LAN. The employee needs `EDIT_ITEM` permission, a current mobile activation token, and their normal login. Pass the server's trusted TLS certificate as `--ca`. The tool prompts for the token and password and does not save them. A new activation token is needed for each online command. The store server must be updated to include the `/studio/catalog` and `/studio/import` routes.

## Run

Replace the example URL, certificate, and folder below with your store values.

```powershell
python catalog-studio.py export --output C:\catalog-studio --url https://store.example:8445 --ca C:\store-cert.pem
```

Review the manifest and create `pilot-ids.txt` with 20 product IDs, one per line. Include reflective, transparent, dark, and detailed packages, plus several ordinary products. Then run:

```powershell
python catalog-studio.py process --output C:\catalog-studio --ids C:\catalog-studio\pilot-ids.txt
python catalog-studio.py sheet --output C:\catalog-studio
```

Inspect the full-size files in `previews` alongside `originals`, and use the contact sheet to find mismatched crops quickly. In `review.csv`, enter `approve` only for accurate images. Leave the others blank or enter `reject` with notes. Fix poor source photos or processing issues before approving them. Save the CSV, then import:

```powershell
python catalog-studio.py import --output C:\catalog-studio --url https://store.example:8445 --ca C:\store-cert.pem
```

Check the imported pilot images in SmartStock and the storefront. Confirm the original remains the primary photo and the studio result is a secondary gallery photo. Once satisfied, process the rest by omitting `--ids`, regenerate sheets, approve in `review.csv`, and import again.

The manifest records export, processing, and import failures and can be reused after interruption. Import uses a stable key for each product and source photo, and the server checks the current primary photo and existing gallery before appending an image. A changed primary photo or a conflicting prior upload requires review.
