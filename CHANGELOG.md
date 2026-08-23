# Changelog

## Unreleased

Every class of the kit is being taken through the same walk — a spec of what it ought to be, written
before the fix, and eight questions answered with numbers before it is committed. What follows is what
changed for somebody who already depends on 0.1.0.

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

**New.**

- `Anchors` — no two things on a page answer to one name. Checked by the canvas beside `Outline`.
- `Element.walk(...)`, `Element.requireText(...)`, `Heading.levelIn/idIn/textIn(...)` — the instruments
  the kit's own page checks are written with, published so a check of yours is written the same way.
- `TidyDialect.PRECEDENCE` — the place tidying takes in the chain, so a post-processor of yours can
  choose which side of it to sit on.
- `thymekit.tidy.enabled` is described in the metadata an ide reads.

**Fixed.**

- The markdown cache is keyed by the heading ceiling as well, so two renderers cannot hand each other
  their headings.
- A thematic break (`---`) survives the clean; it was being dropped silently.
- A line of spaces inside a fenced code block is left alone; it was being emptied before the parse.

## 0.1.0

First public shape of the kit: `Element<K>` as the single currency of composition, a page canvas, one
dispatcher, tidy rendering, markdown with the `#md` dialect, auto-configuration, a showcase you can
mount in one controller method, and four elements — heading, caption, hero, markdown block.
