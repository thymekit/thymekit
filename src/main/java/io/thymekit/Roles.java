/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * What a page asks of an element: which of its keys is a heading level, which is an address inside the
 * page, and what to call it in a message.
 *
 * <p>Its own class for the reason {@link Outline}, {@link Anchors} and {@link Tree} are theirs. These
 * are questions about a <b>page</b> — they exist because two checks and one message need them — and a
 * question about a page is not a property of an element. The currency holds what a descriptor is made
 * of; this holds what a page wants to know about one, and the two grow for different reasons.
 *
 * <p>An element answers by saying so where it puts the value — {@code headingLevel("depth", 2)},
 * {@code anchor("slug", …)}, {@code name("title", …)} on the descriptor. Nothing here knows an adapter
 * address, and nothing here may: an element of somebody else's answers exactly as the kit's own does,
 * which is the whole point of asking what a key <b>is</b> rather than whose it is.
 *
 * <p>Two suffixes, and the difference is the point: {@code …In} answers what an element said, or
 * {@code null} when it said nothing; {@code …Of} always answers, because a message has to print
 * something. A reader that can return nothing and one that cannot should not read alike.
 *
 * <p>Public for the reason the guards are: a check of your own over a page of yours asks the same
 * three questions the kit's two checks ask, and asks them the same way.
 */
public final class Roles {

    private Roles() {}

    /**
     * The heading level a descriptor declares, or {@code null} when it declares none.
     *
     * <p>Whether that level is one HTML has is not asked here — {@link Outline} asks it of the whole
     * page, because a page assembled from stored data has the same right to be judged as one composed
     * by a call, and a level of nine is nonsense in both.
     */
    public static @Nullable Integer headingLevelIn(Map<?, ?> descriptor) {
        return under(descriptor, Element.Role.HEADING_LEVEL) instanceof Number level
            ? level.intValue()
            : null;
    }

    /** The address inside the page a descriptor declares, or {@code null} when it declares none. */
    public static @Nullable String anchorIn(Map<?, ?> descriptor) {
        return under(descriptor, Element.Role.ANCHOR) instanceof String anchor ? anchor : null;
    }

    /**
     * What to call this element in a message a person will read: the words it said it is called, and
     * failing that the address of its adapter, which every descriptor carries. A check that cannot
     * name what it refused sends somebody looking through a page for two of something.
     */
    public static String nameOf(Map<?, ?> descriptor) {
        return under(descriptor, Element.Role.NAME) instanceof String name
            ? name
            : String.valueOf(Guards.required(descriptor, "Roles.nameOf(descriptor)").get("fragment"));
    }

    /** What a descriptor put under a role. The vocabulary is the descriptor's; the questions are ours. */
    private static @Nullable Object under(Map<?, ?> descriptor, Element.Role role) {
        Guards.required(descriptor, "Roles.roleIn(descriptor)");
        return descriptor.get("roles") instanceof Map<?, ?> roles
            ? descriptor.get(roles.get(role.name()))
            : null;
    }
}
