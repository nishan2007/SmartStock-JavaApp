"""Validate design budgets and local report assets; does not test the application."""
from pathlib import Path
import json
import math
import re
from PIL import Image

ROOT = Path(__file__).resolve().parent

def contrast(a, b):
    def luminance(h):
        rgb = [int(h[i:i+2], 16)/255 for i in (1, 3, 5)]
        values = [v/12.92 if v <= .04045 else ((v+.055)/1.055)**2.4 for v in rgb]
        return sum(v*w for v, w in zip(values, (.2126, .7152, .0722)))
    x, y = sorted((luminance(a), luminance(b)))
    return round((y+.05)/(x+.05), 2)

cases = []
for pw, ph in ((1366, 768), (1920, 1080)):
    for scale in (1, 1.25, 1.5):
        w, h = math.floor(pw/scale), math.floor((ph-64)/scale)
        compact = w < 1200 or h < 620
        canvas_w = w-32 if compact else w-184-280-32
        canvas_h = h-56-52-52-32
        ribbon_canvas_h = h-56-44-(104 if compact else 128)-32
        dialog_w, dialog_h = min(840, w-82), min(600, h-53)
        assert canvas_w >= 600 and canvas_h >= 260
        assert dialog_w >= 800 and dialog_h >= 416
        assert w-32 >= 640, "Primary command row would overflow"
        assert ribbon_canvas_h >= 230, "Selected tabbed ribbon leaves insufficient canvas height"
        cases.append(dict(display=f"{pw}x{ph}", scale=scale, usable=[w,h],
                          compact=compact, canvas=[canvas_w,canvas_h], dialog=[dialog_w,dialog_h],
                          selected_ribbon_canvas_height=ribbon_canvas_h))

pairs = {"primary_white": ("#7022A8", "#FFFFFF"),
         "deckers_body": ("#292433", "#F7F6FA"),
         "ribbon_body": ("#302936", "#FBF8F3"),
         "focus_body": ("#252A35", "#EEF0F4"),
         "slate_header": ("#FFFFFF", "#222631"),
         "plum_header": ("#FFFFFF", "#4D235B")}
ratios = {k: contrast(*v) for k,v in pairs.items()}
assert all(x >= 4.5 for x in ratios.values())
assets = {}
for source in ("report.md", "build_report.py", "validate_report.py"):
    content = (ROOT/source).read_text(encoding="utf-8")
    assert all(line == line.rstrip() for line in content.splitlines()), f"Trailing whitespace: {source}"
rendered = (ROOT/"index.html").read_text(encoding="utf-8")
for anchor in re.findall(r'href="#([^"]+)"', rendered):
    assert f'id="{anchor}"' in rendered, f"Broken section link: {anchor}"
for ref in re.findall(r"!\[[^\]]*\]\(([^)]+)\)", (ROOT/"report.md").read_text(encoding="utf-8")):
    assert f'src="{ref}"' in rendered, f"Image missing from HTML: {ref}"
    with Image.open(ROOT/ref) as im:
        im.verify()
    with Image.open(ROOT/ref) as im:
        assets[ref] = list(im.size)
result = dict(scope="Specification arithmetic and report assets only; not native UI verification",
              layout_cases=cases, contrast=ratios, images=assets)
(ROOT/"validation.json").write_text(json.dumps(result, indent=2)+"\n", encoding="utf-8")
print(json.dumps(result, indent=2))
