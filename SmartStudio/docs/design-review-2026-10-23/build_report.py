"""Render this report's small Markdown subset into a self-contained local HTML index."""
from pathlib import Path
import html
import re

ROOT = Path(__file__).resolve().parent

def inline(s):
    s = html.escape(s)
    return re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", s)

lines = (ROOT/"report.md").read_text(encoding="utf-8").splitlines()
parts, toc = [], []
i = 0
while i < len(lines):
    line = lines[i]
    if not line.strip():
        i += 1
        continue
    if line.startswith("#"):
        level = len(line)-len(line.lstrip("#"))
        title = line[level:].strip()
        anchor = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")
        parts.append(f'<h{level} id="{anchor}">{inline(title)}</h{level}>')
        if level == 2:
            toc.append(f'<a href="#{anchor}">{html.escape(title)}</a>')
    elif line.startswith("!["):
        match = re.fullmatch(r"!\[(.*?)\]\((.*?)\)", line)
        alt, src = match.groups()
        parts.append(f'<figure><a href="{html.escape(src)}"><img src="{html.escape(src)}" alt="{html.escape(alt)}" loading="lazy"></a><figcaption>{html.escape(alt)} · Open image for full detail</figcaption></figure>')
    elif line.startswith("|"):
        rows = []
        while i < len(lines) and lines[i].startswith("|"):
            row = lines[i]
            if not re.fullmatch(r"[| :\-]+", row):
                cells = row.strip("|").split("|")
                tag = "th" if not rows else "td"
                rows.append("<tr>"+"".join(f'<{tag}>{inline(x.strip())}</{tag}>' for x in cells)+"</tr>")
            i += 1
        parts.append('<div class="table"><table>'+"".join(rows)+"</table></div>")
        continue
    else:
        parts.append("<p>"+inline(line)+"</p>")
    i += 1

style = """
:root{color-scheme:light;--purple:#7022a8;--ink:#292433}*{box-sizing:border-box}
body{margin:0;background:#f7f6fa;color:var(--ink);font:17px/1.65 'Segoe UI',Arial,sans-serif}
header{background:#292433;color:white;padding:44px max(24px,calc((100vw - 1180px)/2));border-top:8px solid #7022a8}
header p{color:#e5d6ef;margin:0}header strong{font-size:38px;line-height:1.2;display:block;margin:12px 0}
main{max-width:1180px;margin:auto;padding:36px 24px 80px}h1{font-size:36px;line-height:1.2}
h2{font-size:28px;line-height:1.3;margin:72px 0 20px;padding-top:24px;border-top:1px solid #dcd7e3;scroll-margin-top:24px}
h3{font-size:23px;margin-top:40px}p{max-width:90ch;margin:18px 0}strong{font-weight:650}
nav{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:48px}a{color:var(--purple);text-underline-offset:3px}
nav a{padding:9px 13px;border:1px solid #d6ccdf;background:white;border-radius:6px;font-size:14px;text-decoration:none;min-height:40px}
a:focus-visible{outline:3px solid #7022a8;outline-offset:4px}
figure{margin:28px 0 40px}img{display:block;width:100%;height:auto;border:1px solid #dfdbe5;border-radius:8px;background:white}
figcaption{font-size:14px;color:#625c6c;margin-top:9px}.table{overflow-x:auto;margin:24px 0}
table{border-collapse:collapse;width:100%;background:white;font-size:15px;line-height:1.5}th,td{text-align:left;vertical-align:top;padding:14px 16px;border-bottom:1px solid #e4dfe8}th{background:#eee4f6;color:#45205d}td:first-child{font-weight:600}
@media(max-width:700px){header strong{font-size:30px}h1{font-size:28px}h2{font-size:24px}body{font-size:16px}main{padding:24px 16px}table{min-width:680px}}
@media print{body{background:white;font-size:11pt}header{padding:18px;color:#292433;background:white}header p{color:#625c6c}main{max-width:none;padding:0}nav{display:none}h2{margin-top:28px;break-after:avoid}h3{break-after:avoid}figure{break-inside:avoid}img{max-height:230mm;object-fit:contain}table{font-size:9pt}.table{overflow:visible}tr{break-inside:avoid}a{color:inherit}}
"""
output = '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>SmartStudio — Design report</title><style>'+style+'</style><header><p>SMARTSTUDIO / DESIGN EXPLORATION / 23 OCT 2026</p><strong>Make room to create.</strong><p>Three directions. Clearer controls. A calmer workspace.</p></header><main><nav aria-label="Report sections">'+"".join(toc)+'</nav>'+"\n".join(parts)+'</main></html>'
(ROOT/"index.html").write_text(output, encoding="utf-8")
print("Wrote", ROOT/"index.html")
