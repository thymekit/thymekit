#!/usr/bin/env python3
"""Assembles the gh-pages tree: the reports site plus per-commit snapshots of the showcase.

Layout of the published branch:

    /                    the reports site, from main
    /showcase/           the list of snapshots
    /showcase/main/      the snapshot of the latest main
    /showcase/<sha>/     snapshots of other commits, the last ten of them

A snapshot carries its own stylesheets rather than sharing them: an old page has to keep looking the
way it looked, not the way the current CSS would render it.
"""
import json, shutil, sys, pathlib, datetime

KEEP = 10   # snapshots outside main; main keeps the latest one and is never pruned

root = pathlib.Path(__file__).resolve().parents[2]
tree = pathlib.Path(sys.argv[1])              # working copy of the gh-pages branch
sha, branch, subject = sys.argv[2][:8], sys.argv[3], sys.argv[4]
is_main = branch == "main"

shots = tree / "showcase"
shots.mkdir(parents=True, exist_ok=True)
manifest_file = shots / "snapshots.json"
manifest = json.loads(manifest_file.read_text(encoding="utf-8")) if manifest_file.exists() else []

# the site itself is published from main only: a branch has no business overwriting the canonical page
if is_main and (root / "build/site").exists():
    for item in (root / "build/site").iterdir():
        if item.name == "showcase":
            continue                          # snapshots live under their own scheme, see below
        target = tree / item.name
        shutil.rmtree(target, ignore_errors=True) if item.is_dir() else target.unlink(missing_ok=True)
        (shutil.copytree if item.is_dir() else shutil.copy2)(item, target)

source = root / "build/site/showcase"
if not source.exists():
    sys.exit("no showcase to publish: build/site/showcase is missing")

slot = "main" if is_main else sha
shutil.rmtree(shots / slot, ignore_errors=True)
shutil.copytree(source, shots / slot)

entry = {"slot": slot, "sha": sha, "branch": branch, "subject": subject,
         "date": datetime.datetime.now(datetime.UTC).strftime("%Y-%m-%d %H:%M")}
manifest = [m for m in manifest if m["slot"] != slot]
manifest.insert(0, entry)

# retention: main always stays, the rest keeps the last ten by publication order
kept, others = [], 0
for m in manifest:
    if m["slot"] == "main":
        kept.append(m)
        continue
    if others < KEEP:
        kept.append(m)
        others += 1
    else:
        shutil.rmtree(shots / m["slot"], ignore_errors=True)
manifest = kept
manifest_file.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

rows = "\n".join(
    f'''      <a class="row" href="{m['slot']}/">
        <span class="slot">{'main' if m['slot'] == 'main' else m['sha']}</span>
        <span class="subject">{m['subject']}</span>
        <span class="meta">{m['branch']} · {m['date']}</span>
      </a>''' for m in manifest)

(shots / "index.html").write_text(f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>thymekit — showcase snapshots</title>
<style>
  :root {{ --ink:#1d241b; --muted:#6b7a66; --line:#dfe6db; --accent:#3d5c3a; --paper:#f5f7f2; }}
  body {{ margin:0; padding:48px 24px; background:var(--paper); color:var(--ink);
          font:16px/1.6 ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }}
  main {{ max-width:880px; margin:0 auto; }}
  h1 {{ font-size:28px; margin:0 0 4px; }}
  p.lede {{ margin:0 0 32px; color:var(--muted); }}
  a.row {{ display:grid; grid-template-columns:90px 1fr auto; gap:16px; align-items:baseline;
           padding:14px 16px; background:#fff; border:1px solid var(--line); border-radius:10px;
           margin-bottom:8px; text-decoration:none; color:inherit; }}
  a.row:hover {{ border-color:var(--accent); }}
  .slot {{ font-family:ui-monospace, SFMono-Regular, Menlo, monospace; color:var(--accent); font-weight:600; }}
  .meta {{ font-size:13px; color:var(--muted); white-space:nowrap; }}
  footer {{ margin-top:32px; font-size:14px; color:var(--muted); }}
  footer a {{ color:var(--accent); }}
</style>
</head>
<body>
<main>
  <h1>Showcase snapshots</h1>
  <p class="lede">The showcase as it was rendered at each commit — main plus the last {KEEP} elsewhere.</p>
{rows}
  <footer><a href="../">Build reports</a> · <a href="https://github.com/thymekit/thymekit">Source</a></footer>
</main>
</body>
</html>""", encoding="utf-8")

print(f"published {slot} ({branch}); snapshots kept: {len(manifest)}")
