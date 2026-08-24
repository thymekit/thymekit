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
 * carrying the id of a product in a database is carrying data, not an address. Only the anchors the kit
 * itself puts on a page are counted — today, the ones a heading was given — because a guard that fired
 * on two products sharing a number would be the kit inventing a rule nobody agreed to.
 *
 * <p>Today that means <b>headings and nothing else</b>, including yours: the reader this asks refuses
 * to answer for any adapter but its own, so an element of yours carrying an anchor is not counted here
 * however it carries it, and two of them may quietly answer to one name. There is no way for it to
 * join from outside, and saying otherwise would be a promise this code does not keep. Closing it means
 * letting an element declare what a key of it <i>means</i>, which is the direction the readme names in
 * its last chapter.
 */
public final class Anchors {

    private Anchors() {}

    /** Refuses a page where two things answer to one name, and says which name and what they are. */
    public static void requireDistinct(Collection<?> roots) {
        Map<String, String> byAnchor = new LinkedHashMap<>();
        Tree.walk(roots, descriptor -> {
            String anchor = Element.anchorIn(descriptor);
            if (anchor != null) {
                String first = byAnchor.putIfAbsent(anchor, Heading.textIn(descriptor));
                if (first != null) {
                    throw new UnsoundPageException("Anchors.requireDistinct",
                        "two things on the page answer to the anchor \""
                        + anchor + "\": \"" + first + "\" and \"" + Heading.textIn(descriptor)
                        + "\" — a name a page uses twice is a name it cannot use");
                }
            }
            return true;                            // an illustration hides nothing from a document
        });
    }

}
