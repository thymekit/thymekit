/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import org.jspecify.annotations.Nullable;

/**
 * A step in a trail: a link to somewhere above, or the page the trail ends at.
 *
 * <p>Not an element. It appears only inside a trail, never on its own, and nobody would put something
 * else in its place — so it is a value the caller writes rather than a triple with a fragment and a
 * stylesheet of its own.
 *
 * <p>Two states, two factories, and the point of writing it that way is what cannot be written: there
 * is no call that produces the page you are on with a link in it. The page you are on has no address to
 * go to — its address is the address of the page it is printed on, which is why the trail does not
 * repeat it and the graph leaves it out.
 *
 * <p>A record rather than a pair of types, because a crumb becomes data the moment it enters a
 * descriptor: a distinction held by the type system would vanish at that boundary anyway, and the two
 * factories carry it where it is read — at the call site.
 *
 * <p>The guards sit in the canonical constructor rather than in front of it. A record hands its
 * constructor out whether the factories exist or not, so a check that lived only in a factory would be
 * a check with a door beside it. The address goes through the same guard as any other href the kit
 * prints: a step is a link on the page, and an href is the last place a scheme that executes instead of
 * navigating can be stopped.
 *
 * <p>Not public. Nothing in the kit's public surface takes one or hands one back — a trail is written
 * as strings — and a type nobody outside can use is a promise nobody asked for. It becomes public the
 * day something public accepts it.
 */
record Crumb(@Nullable String url, String label) {

    Crumb {
        Element.requireText(label, "Crumb(label)");
        if (url != null) {
            Element.requireNavigable(url, "Crumb(url)");
        }
    }

    /** A step above this page: where it goes, and what it is called. */
    static Crumb link(String url, String label) {
        return new Crumb(Element.requireNavigable(url, "Crumb.link(url)"), label);
    }

    /** The page the trail ends at: named, and going nowhere. */
    static Crumb current(String label) {
        return new Crumb(null, label);
    }
}
