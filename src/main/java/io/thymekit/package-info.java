/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
/**
 * thymekit — domain-agnostic interface elements.
 *
 * <p>Every element is a triple: a Java API (this package), a Thymeleaf fragment served from the jar,
 * and a CSS file whose appearance is tuned by the consumer through {@code --tk-*} custom properties.
 *
 * <h2>The canon</h2>
 * <ul>
 *   <li><b>A element owns its concept.</b> If a concept has an element — heading, caption, button —
 *       everything that needs it composes that element instead of writing its own markup. A new
 *       element is therefore always followed by a pass over the existing ones.</li>
 *   <li><b>Shape:</b> {@code factory(core) → options → build()} yields {@link io.thymekit.Element}, the
 *       single currency of composition. Wide points accept {@code Element<?>}, narrow points name the
 *       marker; where the marker is erased, {@link io.thymekit.Element#requireAdapter} guards at
 *       runtime.</li>
 *   <li><b>One dispatcher.</b> Everything renders through {@code thymekit/element}:
 *       {@code render(e)} for one element, {@code renderAll(items)} for a flow, {@code slot(e, name)}
 *       for a slot, {@code scripts(items)} for behaviour scripts. Containers know no list of bricks.</li>
 *   <li><b>The adapter name is the contract version.</b> Changing the meaning of a descriptor key means
 *       a new adapter ({@code <name>ElV2}), not a new key in the old one.</li>
 *   <li><b>Guards live in the builder:</b> null with the argument's name, invalid state on build.</li>
 * </ul>
 *
 * <h2>Elements of the core</h2>
 *
 * <p>The core holds domain-empty bricks a page rests on. The rest of the kit's elements live one layer
 * below for now and come back one at a time: an element stays in the core not because it happens to be
 * here, but after it has been reviewed on the showcase.
 *
 * <ul>
 *   <li>{@link io.thymekit.Heading} — owner of headings: {@code h1(text)…h6(text)} with an anchor id, a
 *       link ({@code href} plus {@link io.thymekit.Rel} values and {@code newTab}), the language of the
 *       text and screen-reader-only. No default level: it decides the outline, so the author states it.
 *       Outline guard — {@link io.thymekit.Element#assertOutline}.</li>
 *   <li>{@link io.thymekit.Caption} — owner of captions in four roles (eyebrow, subtitle, label, meta):
 *       short text attached to something, never a heading and never a form label. A caption may carry a
 *       machine-readable {@code time} and a {@code lang} of its own.</li>
 *   <li>{@link io.thymekit.Hero} — the header of the page: a heading group, an optional badge and an
 *       optional action row; its core is the H1 alone, and the rule under the group is drawn by CSS.</li>
 *   <li>{@link io.thymekit.Md} — the text of a page: markdown rendered by the {@code #md} dialect and
 *       sanitised, or an empty state with an affordance beside it. {@code linkRel} says what the links
 *       of somebody else's text are. Content, not structure — a heading around it belongs to
 *       {@link io.thymekit.Section}.</li>
 *   <li>{@link io.thymekit.Section} — a titled part of a page: a heading and a slot for whatever goes
 *       under it. Anything needing a titled area composes this instead of writing its own
 *       {@code <section>}.</li>
 *   <li>{@link io.thymekit.PageModel} — the page canvas. It builds two elements: the page itself, which
 *       draws the {@code <main>} landmark with the flow inside it, and the head of the document (title,
 *       description, canonical, image, {@link io.thymekit.PageModel.Robots}). Both go through the same
 *       dispatcher as everything else, so composition is closed at the top as well as at the bottom.
 *       {@code render()} uses the default document, {@code render(view)} one of your own.</li>
 *   <li>{@link io.thymekit.Rel} — what a link says about itself, shared by every element that links.</li>
 *   <li>{@link io.thymekit.ElementContract} — the walk over a triple: the kit checks its own elements
 *       with it, and hands the same walk to whoever writes one.</li>
 *   <li>{@link io.thymekit.Composable} — whatever becomes an element: every builder here, and
 *       {@link io.thymekit.Element} itself. A place that takes an element takes this, so nothing has to
 *       be written between one element and the next.</li>
 *   <li>{@code Element.raw(template, fragment)} wraps a consumer fragment as an element,
 *       {@code Element.script(...)} a behaviour script, and {@code Element.Descriptor.of(...)} is how a
 *       consumer mints an element of its own.</li>
 * </ul>
 */
@org.jspecify.annotations.NullMarked
package io.thymekit;
