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
 * <p>The guards sit in the canonical constructor and nowhere else. A record hands its constructor out
 * whether the factories exist or not, so a check in front of one is a check with a door beside it —
 * and the constructor is also where what the guard gives back is <b>kept</b>, which is the half that
 * matters: an address comes out of it without the space a copied link brought with it, whichever door
 * it came through.
 *
 * <p>The address goes through the same guard as any other href the kit prints: a step is a link on the
 * page, and an href is the last place a scheme that executes instead of navigating can be stopped.
 *
 * <p>Not public. Nothing in the kit's public surface takes one or hands one back — a trail is written
 * as strings — and a type nobody outside can use is a promise nobody asked for. It becomes public the
 * day something public accepts it.
 */
record Crumb(@Nullable String url, String label) {

    Crumb {
        label = Guards.text(label, "Crumb(label)");
        url = url == null ? null : Guards.navigable(url, "Crumb(url)");
    }

    /**
     * A step above this page: where it goes, and what it is called.
     *
     * <p>What it checks is the one thing the constructor cannot: that an address was given at all.
     * Absent is a legal state of a crumb — it is what the page you are on looks like — so the
     * constructor has to accept it, and only the caller of this factory knows that a step which goes
     * nowhere is not what was meant here.
     *
     * <p>Whether the address is <b>an address</b> it does not ask, and that is on purpose. A record
     * hands its constructor out whether the factories exist or not, so a check in front of one is a
     * check with a door beside it — and the same check in both places is worse than either, because
     * the two can disagree about what the value became. They did: this factory kept what the guard
     * gave back and the constructor threw it away, so a step made here and a step made by hand printed
     * two different addresses from one input.
     */
    static Crumb link(String url, String label) {
        return new Crumb(Guards.required(url, "Crumb.link(url)"), label);
    }

    /** The page the trail ends at: named, and going nowhere. */
    static Crumb current(String label) {
        return new Crumb(null, label);
    }
}
