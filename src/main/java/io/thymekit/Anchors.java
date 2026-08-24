/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The anchors of a page: the addresses inside it, and whether any two things answer to the same one.
 *
 * <p>A section takes its accessible name from the id of its heading, and a table of contents links to
 * that same id. Two headings sharing one turn a name into a guess — a screen reader announces the wrong
 * section, a link lands on whichever the browser met first, and the html is invalid besides. Checked by
 * the canvas before a page is rendered, like {@link Outline}.
 *
 * <p>Unlike the outline, an illustration hides nothing here. A sample framed for display is not the
 * structure of a page, so its headings stay out of the outline — but an id inside it is a second node
 * in the document with the same address, and a link cannot tell a demonstration from the real thing.
 *
 * <p>What is <b>not</b> checked is as deliberate as what is. Any element may carry a key called
 * {@code id}, and the kit does not own what that word means in somebody else's element: a card
 * carrying the id of a product in a database is carrying data, not an address, and a guard that fired
 * on two products sharing a number would be the kit inventing a rule nobody agreed to.
 *
 * <p>So what is counted is what an element <b>said</b> is an address — {@code anchor(key, value)} on
 * the descriptor, read back by {@link Roles#anchorIn}. Yours as much as the kit's own: this asks what
 * a key is, never whose adapter carries it, and no address appears anywhere in here.
 *
 * <p>What the kit cannot see is whether the adapter prints that value as an {@code id} at all. The
 * walk over a triple checks it where a fragment can be rendered; without one, a declaration is taken
 * at its word.
 */
public final class Anchors {

    private Anchors() {}

    /** Refuses a page where two things answer to one name, and says which name and what they are. */
    public static void requireDistinct(Collection<?> roots) {
        Map<String, String> byAnchor = new LinkedHashMap<>();
        Tree.walk(roots, descriptor -> {
            String anchor = Roles.anchorIn(descriptor);
            if (anchor != null) {
                String first = byAnchor.putIfAbsent(anchor, Roles.nameOf(descriptor));
                if (first != null) {
                    throw new UnsoundPageException("Anchors.requireDistinct",
                        "two things on the page answer to the anchor \""
                        + anchor + "\": \"" + first + "\" and \"" + Roles.nameOf(descriptor)
                        + "\" — a name a page uses twice is a name it cannot use");
                }
            }
            return true;                            // an illustration hides nothing from a document
        });
    }

}
