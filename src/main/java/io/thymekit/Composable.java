/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

/**
 * Something that becomes an element. Every builder in the kit implements it, and so does
 * {@link Element} itself — an element is what has already become one, so {@code build()} hands back
 * the same value.
 *
 * <p>That is why a place that takes an element takes this instead: with the currency implementing it,
 * the signature says "whatever becomes an element" and covers both without an "or" in it. A page reads
 * the way the concept does, with nothing between one element and the next:
 *
 * <pre>{@code
 * PageModel.of(model)
 *     .title(it.name())
 *     .add(Hero.of(Heading.h1(it.name()))
 *         .eyebrow(Caption.eyebrow("Catalogue"))
 *         .subtitle(Caption.subtitle(it.latinName())))
 *     .render("ingredient-page");
 * }</pre>
 *
 * <p>{@code build()} keeps one meaning of its own: settle it, because you want to hold it — in a
 * variable, in a list, in a field. What is settled is a value and cannot change afterwards.
 *
 * <p>The kit accepts a {@code Composable} and never stores one: every place that takes one builds it
 * at once, so a half-made element never lives anywhere. Writing an element of your own does not
 * require implementing this — a factory that returns an {@code Element} is an element factory,
 * because an element is recognised by what it is, not by what it implements.
 *
 * <p>It has one method, so a lambda is one too, and that is deliberate: a type of yours that already
 * knows how to describe itself becomes composable with {@code () -> myThing.asElement()} and goes into
 * a page beside the kit's own. Since everything is settled where it is taken, nothing is deferred by
 * writing it that way.
 */
@FunctionalInterface
public interface Composable<K> {

    /** The element. Settled, immutable, and equal to any other built from the same description. */
    Element<K> build();
}
