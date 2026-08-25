# Changelog

## Unreleased

**Breaking.** The two checks a page gets ask what a key of an element *is*, and no longer which adapter
carries it.

- an element says what a key is in the call that puts the value: `headingLevel(key, int)`,
  `anchor(key, String)`, `name(key, String)`. An element of yours that says so takes part in the
  outline and in the anchor check exactly as the kit's own do — their page may not carry two titles,
  skip a level, or answer twice to one name either, which is what
  [#20](https://github.com/thymekit/thymekit/issues/20) asked for.
- `Roles` is where those questions are read back: `Roles.headingLevelIn`, `Roles.anchorIn`,
  `Roles.nameOf`. Its own class, for the reason `Outline`, `Anchors` and `Tree` are theirs — a
  question about a page is not a property of an element, and the currency holds what a descriptor is
  made of rather than what a page wants to know about one.
- `Heading.levelIn`, `Heading.idIn` and `Heading.textIn` are gone. Each began by asking whether the
  adapter was the kit's own, which is the privilege being removed. `Element.headingLevelIn`,
  `Element.anchorIn` and `Element.nameIn` answer for everything that says what it is.
- a level is taken as it is given and judged with the page: `Outline` refuses one outside h1..h6, so
  a page assembled from stored data is judged as one composed by a call. A descriptor minted by hand
  takes part in the outline only by saying that a key of it is a level.
- an anchor is one word wherever it is declared — the rule moved out of the heading, so an anchor of
  yours goes through the check the kit's own does.

- the six guards over a **value** — `required`, `text`, `tag`, `absolute`, `navigable`, `anchor` —
  are `Guards.…` now, not `Element.require…`. None of them knows what an element is, and while they
  lived in the currency, everything that guards anything depended on it: two classes had come to hold
  each other through `Element.required`. Guards over an *element* stay where an element is —
  `Element.settle`, `Element.requireAdapter`, `Element.requireRenderable` — and that line is what
  keeps the dependency one-way.
- the reserved key that carries what an element said is `roles`, not `means`: the word a stored page
  shows is now a word the documentation uses.

**Breaking.** A key of a descriptor holds data, and an element put under one is refused where it is
written. It never rendered: the dispatcher asks a descriptor for its address and an element is not
one, so that part of a page was simply absent, and nothing said why. An element goes in a slot, or in
as `element.asMap()` where an adapter renders it in place — which is what every element the kit ships
already did.

**Breaking.** `Rel.forNewTab` and `Rel.tokens` take a `Set<Rel>` rather than any collection. A link
saying `nofollow` twice is not wrong to a browser, which is exactly why nobody would ever notice it —
and what cannot be written needs no guard. `Rel.of(...)` hands back a set, which is what both of them
are given everywhere in the kit.

**Fixed.** A contribution that held itself walked until the stack ended. A map may hold itself,
nothing stops a caller building one, and the writer meets a contribution before it is copied — so a
`StackOverflowError` was reachable from `describes`, which is the failure that kills a thread, names
no element and is the one kind the family of refusals exists to abolish. A description has a bottom
now, thirty-two deep, and the refusal names the way down to what has none.

**Fixed.** Nothing at all is decided in one place and asked of every code point. Java offers two
answers and neither is right: `String.isBlank` counts an en space and misses the non-breaking one,
and a regex of `\s` in java is seven ASCII characters, so an ideographic space passes it as text. A
heading of a zero width space, of a byte order mark, of a soft hyphen renders a box with nothing in
it, and all of them are what a paste out of a spreadsheet or a CMS leaves behind. The same question
now decides what an anchor may hold, since an anchor with a space of any width is one nobody can type
into a link.

**Fixed.** A non-breaking space counted as text where a programmer wrote it and as emptiness where
somebody else's markdown carried it — the rule of this project upside down, since what is written by
hand is held to the stricter standard. `Guards.isNothing(text)` is the one answer now, and a markdown
block made of a field somebody emptied in a rich editor shows its empty state instead of an empty box.

**Fixed.** An address that leaves the page carried spaces. `og:url` and a canonical are read by
something that is not a browser and will not guess where the address ended, so a space of any width
inside one is refused; an address that stays on the page keeps its spaces, because there the reader
is a browser and a link of yours that has worked for years is not the kit's to refuse.

**Fixed.** An address of `https://` with nothing after it passed as absolute. A scheme is not an
address: a page would have handed a crawler a link to nowhere while looking exactly like a page that
had one.

**Fixed.** Three sentences of the readme described things that exist and described them wrongly, and
two of its lists had not followed the kit as it grew
([#22](https://github.com/thymekit/thymekit/issues/22),
[#23](https://github.com/thymekit/thymekit/issues/23)): what a factory returns, the type the hero
takes, what forms the outline, the elements named in prose, and the number of requests `ui.css`
costs — the last of which no longer carries a number to go stale.

**Inside.** The walk over a triple gained a question: an element that says one of its keys is an
address inside the page must print it as an `id`, where there is an engine to render with. The anchor
check holds every page to that value, and until now nothing could tell whether a browser would ever
find it.

**Inside.** The readme was rebuilt from its own sentences: every one of them was read, put with the
sentences that say the same kind of thing, and the chapters were whatever came out of that — fifteen
of them, and the topics inside them are the ones the text actually had rather than the ones its old
headings claimed. Seven pairs of sentences said the same thing twice and are now one each.

Two things it said about itself were wrong, and both are now held by a rule: the core was announced as
seven things and is eight, and the canon was announced as thirty rules with twenty-seven listed. Four more rules, thirty-five now: this list is as long as the number in front of it; the version the
readme hands a consumer to copy is the version the build publishes, which was a release behind; the map
at the top of the readme names every chapter of it, in order; a class that publishes a reader over a
descriptor names no adapter; and what counts as nothing at all is decided in one place — which is the rule that would have stopped the outline growing blind to
everybody else's elements, and could not have been written before it happened.

## 0.4.0 — 2026-08-24

**Breaking.** The kit throws its own failures and no longer borrows anyone's.

- `catch (IllegalArgumentException)`, `catch (NullPointerException)` and `catch (IllegalStateException)`
  no longer catch a refusal of this library. Catch `ThymekitException`, or one of the three it covers.
- the walk over a triple no longer throws `UncheckedIOException` for a template it cannot read; it
  throws `ContractBrokenException` with the original failure as the cause.
- `Tree.walk(node, visit)` refuses a null tree and a null visitor. It used to walk a null to nothing,
  which made a check written over it pass while checking nothing. A hole *inside* a tree is still
  walked past: a page may carry a list with a gap in it.
- `Descriptor.describes(node)` checks the values of a contribution where it is written rather than
  when a page is rendered. A contribution carrying something the kit cannot write — a date, a double —
  used to build quietly and fail later, on a page that could not say whose contribution it was.
- the last argument of `Element.requireAdapter(element, fragment, where)` and of
  `Caption.inRole(caption, role, where)` is a place and no longer a sentence: the kit writes the
  sentence itself, and says which adapter was wanted as well as which one came.

**New.**

- `ThymekitException` and the three kinds under it — `MisuseException` for a call written wrong,
  `UnsoundPageException` for a page that does not add up, `ContractBrokenException` for the walk over a
  triple. What separates them is not what went wrong but what a consumer should do about it: the first
  is an alert, the second can arrive from data and is theirs to decide, the third happens in tests.
- every refusal names the place it was made at, in `where()` and in front of the message, because a
  handler that logs only the message is the common case. The place is a call — `Heading.href(href)`,
  `Rel.of(values) — one of them` — so it says which line to open.
- `Element.required(value, where)` — the guard that replaces `Objects.requireNonNull` throughout, and
  is there for an element of yours as much as for ours.

**Fixed.**

- a collection of link relations was guarded for being absent but not for holding a null, so a null
  inside it reached the caller as the machine's own failure rather than as a refusal.
- `Element.settle` built its message on every successful call, and folded the place into it: the one
  time it fired it said `heading built nothing: was not given`.
- a step of a trail was refused by the value it becomes, so the refusal named a type of this package
  that a caller cannot look up. `Breadcrumbs.add` and `Breadcrumbs.current` refuse it themselves now,
  and the second no longer reports itself as the first.
- the walk over a triple checked its template engine for null under a caller that had already asked.

**Inside.** Five more rules, thirty now: the kit throws only its own; every one of those types is named
in the readme; every place names a call rather than a noun or a sentence; nothing is imported that a
file has stopped using; and this entry says a number of rules that is the number of rules. The first
two make one sentence true — what the kit does not name, it did not foresee — which is what lets a
consumer treat an uncaught failure from here as a defect of ours.

## 0.3.0 — 2026-08-24

**Breaking.**

- `Element.walk(...)` and `Element.assetsOf(...)` are now `Tree.walk(...)` and `Tree.assetsOf(...)`.
  Walking the tree of a page is a question about a page, not a property of an element — the same
  reason `Outline` and `Anchors` left the currency in 0.2.0, applied to the traversal they were
  written on. `element.assets()` goes with them — it asked about a subtree, which is the same species
  of question — and `Tree.assetsOf(List.of(element))` says the same thing.

**New.**

- **Structured data.** An element declares a JSON-LD node as data with
  `Element.Descriptor.describes(...)`; the canvas gathers the contributions of the page, names the
  vocabulary, turns them into text once and prints a single `<script type="application/ld+json">` in
  the head. An element never prints its own block and never carries finished text, so a descriptor
  stays data all the way down. `Tree.describedBy(...)` is the gathering.
- **`Breadcrumbs`** — the trail of a page, visible and machine-readable from one list of steps, so the
  two halves cannot come to describe different pages. `named(label)` because a screen reader says that
  name aloud; `site(origin)` makes the addresses a crawler reads absolute while the links on the page
  stay as they were written.
- **`Topbar`** — the bar above a page: the way back, then the trail. An element rather than a thing to
  assemble, because assembled by hand the way back lands inside the trail's landmark.
- `Element.requireAbsolute(...)` and `Element.requireNavigable(...)` — the guards for an address that
  leaves the page and for one that navigates rather than executes, public now that more than one
  element needs each.
- Handles for the two new elements, including direction and a stripe for the bar, so a theme can dress
  it without a single selector over a kit class.

**Fixed.**

- A step of a trail is guarded like any other link the kit prints: an address whose scheme executes
  instead of navigating is refused where it is written.
- An origin is scheme and host only. Given a path or a trailing slash it would be joined to every step
  written from the root, producing an address that resolves somewhere else with nothing to notice it
  by. An address that already names a host — including `//host/path` — is left alone.
- Two guards that could never fire and two constants nobody read, all of them left over from moving
  code rather than from writing it.

**Inside.** Two more rules of the canon, twenty-five now: structured data is printed by the head and by
nothing else, and nothing hidden is kept for a reader that does not exist. The second exists because no
gate can see a dead field — a constant is initialised when its class loads, so it reads as covered.

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
