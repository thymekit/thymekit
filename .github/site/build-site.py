#!/usr/bin/env python3
"""Assembles the Pages site out of the reports the build has just produced."""
import shutil, json, pathlib, xml.etree.ElementTree as ET

root = pathlib.Path(__file__).resolve().parents[2]
site = root / "build" / "site"
shutil.rmtree(site, ignore_errors=True)
(site / "badges").mkdir(parents=True)


def copy(src: str, dst: str) -> bool:
    s = root / src
    if not s.exists():
        return False
    shutil.copytree(s, site / dst)
    return True


# A branch publishes only its snapshot, so the reports may legitimately be absent: whatever is
# missing simply does not get a card or a badge.
def coverage():
    f = root / "build/reports/jacoco/test/jacocoTestReport.xml"
    if not f.exists():
        return None
    out = {}
    for c in ET.parse(f).getroot().findall("counter"):
        missed, covered = int(c.get("missed")), int(c.get("covered"))
        out[c.get("type")] = 100.0 * covered / (covered + missed)
    return out["INSTRUCTION"], out["BRANCH"]


def mutation():
    f = root / "build/reports/pitest/mutations.xml"
    if not f.exists():
        return None
    x = f.read_text(encoding="utf-8")
    return x.count("detected='true'"), x.count("<mutation ")


def tests():
    results = list((root / "build/test-results/test").glob("TEST-*.xml"))
    if not results:
        return None
    total = failed = 0
    seconds = 0.0
    for f in results:
        s = ET.parse(f).getroot()
        total += int(s.get("tests", 0))
        failed += int(s.get("failures", 0)) + int(s.get("errors", 0))
        seconds += float(s.get("time", 0))
    return total, failed, seconds


def badge(name: str, label: str, message: str, colour: str) -> None:
    (site / "badges" / f"{name}.json").write_text(json.dumps(
        {"schemaVersion": 1, "label": label, "message": message, "color": colour}), encoding="utf-8")


cov, mut, tst = coverage(), mutation(), tests()
def showcase() -> bool:
    """The showcase rendered at build time: real output of the engine, served as a static page.

    Its stylesheets are linked absolutely (that is how a consumer would serve them), so the assets go
    next to the page and the two links are pointed at them.
    """
    page = root / "build/showcase/index.html"
    if not page.exists():
        return False
    (site / "showcase").mkdir(parents=True)
    shutil.copytree(root / "src/main/resources/static/thymekit", site / "showcase" / "assets")
    html = page.read_text(encoding="utf-8")
    for name in ("ui.css", "demo.css"):
        html = html.replace(f'href="/thymekit/{name}"', f'href="assets/{name}"')
    (site / "showcase" / "index.html").write_text(html, encoding="utf-8")
    return True


have = {
    "showcase": showcase(),
    "tests": copy("build/reports/tests/test", "tests"),
    "coverage": copy("build/reports/jacoco/test/html", "coverage"),
    "mutation": copy("build/reports/pitest", "mutation"),
    "api": copy("build/docs/javadoc", "api"),
}

cards = [("Showcase", "live", "the kit as it renders itself", "showcase/index.html", have["showcase"])]

if tst:
    total, failed, seconds = tst
    badge("tests", "tests", f"{total} passing" if not failed else f"{failed} failing", "3d5c3a" if not failed else "a33")
    cards.append(("Tests", f"{total}", f"all passing in {seconds:.1f}s" if not failed else f"{failed} failing",
                  "tests/index.html", have["tests"]))
if cov:
    instr, branch = cov
    badge("coverage", "coverage", f"{instr:.1f}%", "3d5c3a" if instr >= 90 else "b08c54")
    cards.append(("Coverage", f"{instr:.1f}%", f"instructions · {branch:.1f}% branches",
                  "coverage/index.html", have["coverage"]))
if mut:
    killed, mutants = mut
    badge("mutation", "mutation", f"{100 * killed // mutants}%", "3d5c3a" if killed == mutants else "b08c54")
    cards.append(("Mutation", f"{100 * killed // mutants}%", f"{killed} of {mutants} mutants killed",
                  "mutation/index.html", have["mutation"]))
cards.append(("API", "javadoc", "every public type", "api/index.html", have["api"]))

html = ["""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>thymekit — build reports</title>
<style>
  :root { --ink:#1d241b; --muted:#6b7a66; --line:#dfe6db; --accent:#3d5c3a; --paper:#f5f7f2; }
  * { box-sizing: border-box; }
  body { margin:0; padding:48px 24px; background:var(--paper); color:var(--ink);
         font:16px/1.6 ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif; }
  main { max-width: 880px; margin: 0 auto; }
  h1 { font-size:28px; margin:0 0 4px; letter-spacing:-.01em; }
  p.lede { margin:0 0 36px; color:var(--muted); }
  .cards { display:grid; grid-template-columns:repeat(auto-fit,minmax(200px,1fr)); gap:16px; }
  a.card { display:block; padding:20px; background:#fff; border:1px solid var(--line); border-radius:12px;
           text-decoration:none; color:inherit; transition:border-color .15s, transform .15s; }
  a.card:hover { border-color:var(--accent); transform:translateY(-2px); }
  .label { font-size:13px; text-transform:uppercase; letter-spacing:.08em; color:var(--muted); }
  .value { font-size:30px; font-weight:600; margin:6px 0 2px; color:var(--accent); }
  .note { font-size:14px; color:var(--muted); }
  footer { margin-top:40px; padding-top:20px; border-top:1px solid var(--line); font-size:14px; color:var(--muted); }
  footer a { color:var(--accent); }
</style>
</head>
<body>
<main>
  <h1>thymekit — build reports</h1>
  <p class="lede">Published by the build itself on every push to <code>main</code>.</p>
  <div class="cards">"""]
for label, value, note, href, present in cards:
    if present:
        html.append(f'''    <a class="card" href="{href}">
      <div class="label">{label}</div><div class="value">{value}</div><div class="note">{note}</div>
    </a>''')
html.append("""  </div>
  <footer>
    Source: <a href="https://github.com/thymekit/thymekit">github.com/thymekit/thymekit</a>
  </footer>
</main>
</body>
</html>""")
(site / "index.html").write_text("\n".join(html), encoding="utf-8")
print("site:", ", ".join(name.lower() for name, *_ , present in cards if present))
