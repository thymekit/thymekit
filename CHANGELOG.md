# Changelog

## 0.2.0 — 2026-08-23

Every class of the kit was taken through the same walk: a spec of what it ought to be, written before
the fix so that it started red, and nine questions answered with numbers before it was committed. What
follows is what changed for somebody who already depends on 0.1.0.

**Breaking.**

- `.detail-empty-hint` is `.tk-md-empty`: a class of the kit now carries the kit's prefix, as the
  others do. A theme that styled the old name styles nothing.
- The handles of the markdown empty state are `--tk-md-empty-fg` and `--tk-md-link-fg`; they used to
  be named after the section, which was not the element that read them.
- `Element.assertOutline(...)` is `Outline.requireSound(...)`: the outline of a page is a property of
  the page, and holding it on the currency had taught it the name of one element's adapter.
- `Caption.roleOf(...)` is gone. `Caption.inRole(...)`, the guard the kit's own hero uses, is public
  instead — the useful half of the instrument rather than the readable one.
- The markdown dialect is named `thymekit-markdown` rather than `markdown`; a name in an engine's
  registry now says whose it is.
- A blank text is refused where a person writes one — `Caption.eyebrow("")`, `Heading.h2(" ")`, an
  empty-state hint — and treated as an absence where data provides one: `Md.of("")` shows the empty
  state rather than an empty box.
- An anchor is checked: `Heading.id("two words")` is refused, because an attribute keeps what comes
  before the first space.
- `ElementContract` now fails an element whose adapter declares no keys, and an element carrying a key
  the adapter never reads. An element that passed in 0.1.0 may not pass here until its fragment says
  what it reads.

**New.**

- **Declared keys and slots.** An adapter says which keys it reads and which slots it renders, in a
  comment above the fragment that Thymeleaf strips before rendering. `ElementContract` checks the
  declaration in both directions, and a key the page comes out the same without is a failure rather
  than a shrug. The opposite direction — a key nothing fills, a branch nothing reaches — is a claim
  about your samples and is asked for with `coveringEveryKey()`.
- **`Section`** — the element for a part of a page that has a title: a heading and a slot for whatever
  belongs under it, taking its accessible name from the heading's anchor when you gave it one.
- **The showcase**, mountable in one controller method with `Demo.page(model)`: every element live,
  each beside a frame in the stock scope showing what it looks like when a theme says nothing.
- `Anchors` — no two things on a page answer to one name. Checked by the canvas beside `Outline`.
- `Element.walk(...)`, `Element.requireText(...)`, `Heading.levelIn/idIn/textIn(...)` — the instruments
  the kit's own page checks are written with, published so a check of yours is written the same way.
- `ElementContract.templatesUnder(...)` for templates that do not live under `templates/`.
- `TidyDialect.PRECEDENCE` — the place tidying takes in the chain, so a post-processor of yours can
  choose which side of it to sit on.
- `thymekit.tidy.enabled` is described in the metadata an ide reads.
- `./gradlew verify` — the run that judges a commit: it deletes what the last one left, then builds,
  runs the specs and takes the mutation gate.

**Fixed.**

- The jar shipped without its configuration metadata on any clean build. The annotation processor reads
  its input off the compile classpath, and compilation was running before resources were copied there.
- The markdown cache is keyed by the heading ceiling as well, so two renderers cannot hand each other
  their headings.
- A thematic break (`---`) survives the clean; it was being dropped silently.
- A line of spaces inside a fenced code block is left alone; it was being emptied before the parse.
- Whitespace tidying no longer touches the inside of a fenced code block.
- `Md` refuses an action it has nowhere to show, instead of rendering a control that cannot be reached.
- The section adapter declares the slot it renders, so the contract walk can see it.

**Inside.** The canon that keeps this shape grew from eight rules to twenty-three, each one standing
behind a defect that actually happened and each made to fail before it was kept. The mutation gate is
at 100% over 379 mutants.

## 0.1.0

First public shape of the kit: `Element<K>` as the single currency of composition, a page canvas, one
dispatcher, tidy rendering, markdown with the `#md` dialect, auto-configuration, a showcase you can
mount in one controller method, and four elements — heading, caption, hero, markdown block.
