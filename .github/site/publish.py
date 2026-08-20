#!/usr/bin/env python3
"""Assembles the gh-pages tree: a full site per commit.

    /            the list of published sites
    /main/       the trunk: tests, coverage, mutation, the showcase and the API docs
    /<sha>/      any other commit: the same, minus the API docs

Every site is self-contained — its own reports, its own showcase with its own stylesheets — so an old
page keeps telling the truth about the commit it came from instead of borrowing today's.
"""
import json, shutil, sys, pathlib, datetime

KEEP = 3   # sites outside main; main is kept forever

root = pathlib.Path(__file__).resolve().parents[2]
tree = pathlib.Path(sys.argv[1])                 # working copy of the gh-pages branch
sha, branch, subject = sys.argv[2][:8], sys.argv[3], sys.argv[4]
slot = "main" if branch == "main" else sha

site = root / "build/site"
if not site.exists():
    sys.exit("nothing to publish: build/site is missing")

tree.mkdir(parents=True, exist_ok=True)
shutil.rmtree(tree / slot, ignore_errors=True)
shutil.copytree(site, tree / slot)

manifest_file = tree / "snapshots.json"
manifest = json.loads(manifest_file.read_text(encoding="utf-8")) if manifest_file.exists() else []
entry = {"slot": slot, "sha": sha, "branch": branch, "subject": subject,
         "date": datetime.datetime.now(datetime.UTC).strftime("%Y-%m-%d %H:%M")}
manifest = [entry] + [m for m in manifest if m["slot"] != slot]

kept, others = [], 0
for m in manifest:
    if m["slot"] == "main":
        kept.append(m)
    elif others < KEEP:
        kept.append(m)
        others += 1
    else:
        shutil.rmtree(tree / m["slot"], ignore_errors=True)
manifest = kept
manifest_file.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

rows = "\n".join(
    f'''      <a class="row" href="{m['slot']}/">
        <span class="slot">{m['slot']}</span>
        <span class="subject">{m['subject']}</span>
        <span class="meta">{m['branch']} · {m['date']}</span>
      </a>''' for m in manifest)

(tree / "index.html").write_text(f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>thymekit — builds</title>
<style>
  :root {{ --ink:#1d241b; --muted:#6b7a66; --line:#dfe6db; --accent:#3d5c3a; --paper:#f5f7f2; }}
  body {{ margin:0; padding:48px 24px; background:var(--paper); color:var(--ink);
          font:16px/1.6 ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }}
  main {{ max-width:880px; margin:0 auto; }}
  h1 {{ font-size:28px; margin:0 0 4px; }}
  p.lede {{ margin:0 0 32px; color:var(--muted); }}
  a.row {{ display:grid; grid-template-columns:96px 1fr auto; gap:16px; align-items:baseline;
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
  <h1>Builds</h1>
  <p class="lede">Each one carries its own tests, coverage, mutation report and showcase — main, plus
  the last {KEEP} commits from anywhere else.</p>
{rows}
  <footer><a href="https://github.com/thymekit/thymekit">Source</a></footer>
</main>
</body>
</html>""", encoding="utf-8")

print(f"published /{slot}/ from {branch}; sites kept: {len(manifest)}")
