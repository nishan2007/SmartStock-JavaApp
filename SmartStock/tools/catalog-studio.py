"""Local, review-first studio photo workflow. Run --help for commands."""
import argparse
import base64
import csv
import getpass
import hashlib
import io
import json
import ssl
import sys
from http.cookiejar import CookieJar
from pathlib import Path
from urllib.request import HTTPCookieProcessor, HTTPSHandler, Request, build_opener


def save_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")


def api_client(args):
    if not args.url.startswith("https://"):
        raise ValueError("The SmartStock API URL must use HTTPS.")
    if not args.ca:
        raise ValueError("Provide --ca with the store server's trusted TLS certificate.")
    opener = build_opener(HTTPCookieProcessor(CookieJar()), HTTPSHandler(context=ssl.create_default_context(cafile=args.ca)))
    root = args.url.rstrip("/") + "/api/v1"

    def call(route, body, csrf=None, key=None):
        headers = {"Content-Type": "application/json"}
        if csrf:
            headers["X-CSRF-Token"] = csrf
        if key:
            headers["Idempotency-Key"] = key
        request = Request(root + route, json.dumps(body).encode(), headers)
        with opener.open(request, timeout=120) as response:
            result = json.load(response)
        if not result["ok"]:
            raise RuntimeError(result["error"])
        return result["data"]

    token = getpass.getpass("Current mobile activation token: ")
    call("/activate", {"token": token})
    username = input("SmartStock employee login: ")
    password = getpass.getpass("Password or PIN: ")
    session = call("/login", {"identifier": username, "secret": password})
    return call, session["csrfToken"]


def export(args):
    call, _ = api_client(args)
    root = Path(args.output)
    originals = root / "originals"
    originals.mkdir(parents=True, exist_ok=True)
    manifest = []
    after = 0
    while True:
        page = call("/studio/catalog", {"afterId": after})["products"]
        if not page:
            break
        for row in page:
            after = row["productId"]
            entry = {"productId": after, "name": row["name"], "imageUrl": row["imageUrl"], "gallery": row["additionalImageUrls"], "status": "pending"}
            try:
                image = call("/images/fetch", {"reference": row["imageUrl"]})
                data = base64.b64decode(image["bytesBase64"], validate=True)
                entry["sourceSha256"] = hashlib.sha256(data).hexdigest()
                target = originals / f"{after}.img"
                target.write_bytes(data)
            except Exception as exc:
                entry["status"] = "export_failed"
                entry["error"] = str(exc)
            manifest.append(entry)
        save_json(root / "manifest.json", manifest)
        print(f"Exported or recorded {len(manifest)} products", flush=True)
    print(f"Export finished: {sum(x['status']=='pending' for x in manifest)} ready, {sum(x['status']=='export_failed' for x in manifest)} failed")


def process(args):
    from PIL import Image, ImageFilter, ImageOps
    from rembg import new_session, remove
    root = Path(args.output)
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    ids = None
    if args.ids:
        ids = {int(x.strip()) for x in Path(args.ids).read_text().splitlines() if x.strip() and not x.startswith("#")}
    session = new_session("u2net")
    done = 0
    for row in manifest:
        if row["status"] == "export_failed" or (ids is not None and row["productId"] not in ids):
            continue
        dest = root / "previews" / f"{row['productId']}.jpg"
        if dest.exists() and not args.force:
            continue
        try:
            with Image.open(root / "originals" / f"{row['productId']}.img") as image:
                source = ImageOps.exif_transpose(image).convert("RGB")
            cutout = remove(source, session=session).convert("RGBA")
            bounds = cutout.getchannel("A").getbbox()
            if not bounds:
                raise ValueError("No product was detected")
            cutout = cutout.crop(bounds)
            cutout.thumbnail((940, 940), Image.Resampling.LANCZOS)
            canvas = Image.new("RGB", (1200, 1200), "white")
            x, y = (1200-cutout.width)//2, (1100-cutout.height)//2
            alpha = cutout.getchannel("A")
            shadow = Image.new("RGBA", (1200, 1200), (0, 0, 0, 0))
            shadow.paste((0, 0, 0, 55), (x+14, y+20, x+14+cutout.width, y+20+cutout.height), alpha)
            shadow = shadow.filter(ImageFilter.GaussianBlur(22))
            canvas.paste(shadow, (0, 0), shadow)
            canvas.paste(cutout, (x, y), alpha)
            dest.parent.mkdir(parents=True, exist_ok=True)
            canvas.save(dest, "JPEG", quality=86, optimize=True)
            row["status"] = "review"
            row.pop("error", None)
            done += 1
        except Exception as exc:
            row["status"] = "process_failed"
            row["error"] = str(exc)
        save_json(root / "manifest.json", manifest)
    print(f"Created {done} previews")


def sheet(args):
    from PIL import Image, ImageDraw, ImageOps
    root = Path(args.output)
    rows = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    rows = [r for r in rows if (root / "previews" / f"{r['productId']}.jpg").exists()]
    for page in range(0, len(rows), 40):
        chunk = rows[page:page+40]
        contact = Image.new("RGB", (1200, 1500), "white")
        draw = ImageDraw.Draw(contact)
        for n, row in enumerate(chunk):
            with Image.open(root / "previews" / f"{row['productId']}.jpg") as image:
                thumb = ImageOps.contain(image, (220, 220))
                x, y = (n % 5)*240+10, (n//5)*187+5
                thumb.thumbnail((175, 155))
                contact.paste(thumb, (x, y))
                draw.text((x, y+157), f"{row['productId']} {row['name'][:22]}", fill="black")
        target = root / "contact-sheets" / f"sheet-{page//40+1:03}.jpg"
        target.parent.mkdir(parents=True, exist_ok=True)
        contact.save(target, quality=85)
    review = root / "review.csv"
    if not review.exists():
        with review.open("w", newline="", encoding="utf-8") as file:
            writer = csv.writer(file)
            writer.writerow(["productId", "name", "decision", "notes"])
            writer.writerows([r["productId"], r["name"], "", ""] for r in rows)
    else:
        with review.open(newline="", encoding="utf-8") as file:
            existing = {int(r["productId"]) for r in csv.DictReader(file)}
        with review.open("a", newline="", encoding="utf-8") as file:
            writer = csv.writer(file)
            writer.writerows([r["productId"], r["name"], "", ""] for r in rows if r["productId"] not in existing)
    print(f"Created {(len(rows)+39)//40} contact sheets and {review}")


def import_approved(args):
    root = Path(args.output)
    manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    decisions = {}
    with (root / "review.csv").open(newline="", encoding="utf-8") as file:
        for row in csv.DictReader(file):
            decisions[int(row["productId"])] = row["decision"].strip().lower()
    approved = [r for r in manifest if decisions.get(r["productId"]) == "approve" and r["status"] != "imported"]
    if not approved:
        print("No new approved images to import")
        return
    call, csrf = api_client(args)
    for row in approved:
        try:
            image = (root / "previews" / f"{row['productId']}.jpg").read_bytes()
            key = "studio-" + str(row["productId"]) + "-" + row["sourceSha256"][:32]
            result = call("/studio/import", {"productId": row["productId"], "sourceImageUrl": row["imageUrl"], "sourceSha256": row["sourceSha256"], "bytesBase64": base64.b64encode(image).decode()}, csrf, key)
            row["status"] = result["status"]
            row["importReference"] = result["reference"]
            row.pop("error", None)
        except Exception as exc:
            row["status"] = "import_failed"
            row["error"] = str(exc)
        save_json(root / "manifest.json", manifest)
        print(f"{row['productId']}: {row['status']}", flush=True)
    for status in ("imported", "skipped", "import_failed", "review", "export_failed", "process_failed"):
        print(f"{status}: {sum(r['status']==status for r in manifest)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["export", "process", "sheet", "import"])
    parser.add_argument("--output", required=True, help="Private working folder outside the repository")
    parser.add_argument("--url", help="Mobile item server URL, for example https://store.example:8445")
    parser.add_argument("--ca", help="Trusted server certificate PEM")
    parser.add_argument("--ids", help="Text file with one product ID per line for a pilot")
    parser.add_argument("--force", action="store_true", help="Rebuild existing previews")
    args = parser.parse_args()
    {"export": export, "process": process, "sheet": sheet, "import": import_approved}[args.command](args)


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
