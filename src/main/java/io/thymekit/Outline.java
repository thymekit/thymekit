/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;

/**
 * The outline of a page: the headings on it, and whether they add up to something a reader can walk.
 *
 * <p>Checked by the canvas before a page is rendered. Three things, and all three are defects somebody
 * meets on the finished page rather than opinions about style:
 *
 * <ul>
 *   <li>at most one first-level heading — the title of a page is one thing, not several;</li>
 *   <li>no level skipped — a page that uses h4 while nothing on it is an h3 has a hole in its outline,
 *       and a screen reader walking headings falls straight through it;</li>
 *   <li>no level outside h1..h6 — html has six, and the adapter renders nothing at all for a seventh,
 *       which is the kind of silence a page should never ship with.</li>
 * </ul>
 *
 * <p>Where in the flow a heading stands is not this class's business, so nesting one deeper never trips
 * it. An illustration is skipped: a sample framed for display is not the structure of the page it sits
 * on. A page with no headings is legal.
 *
 * <p>This class knows how to walk a tree of elements and what an outline must be, and it knows no
 * descriptor keys at all: whether something is a heading, and at what level, is {@link Heading}'s to
 * say, and what counts as an illustration is {@link Element}'s. That is the whole reason it lives on
 * its own — the currency of composition should not know the name of one element's adapter.
 *
 * <p>The guarantee stops where the kit stops. A heading an author wrote inside markdown is not seen
 * here — that text is data and arrives as HTML long after this runs. Such headings are placed under the
 * page by {@link MarkdownRenderer} instead; a gap the author left inside the text travels with it, and
 * neither this nor the renderer closes it.
 */
public final class Outline {

    private Outline() {}

    /** Refuses a page whose headings do not add up; says which of the three it is, and on what. */
    public static void requireSound(Collection<?> roots) {
        List<String> h1 = new ArrayList<>();
        TreeSet<Integer> levels = new TreeSet<>();
        collect(roots, h1, levels);

        if (h1.size() > 1) {
            throw new IllegalStateException("more than one H1 on the page: " + h1
                + " — the title of a page is one thing; sections start at h2");
        }
        if (levels.isEmpty()) {
            return;
        }
        if (levels.first() < 1 || levels.last() > 6) {
            throw new IllegalStateException("heading level outside h1..h6 on the page: " + levels
                + " — html has six, and the adapter renders nothing at all for anything else");
        }
        for (int level = levels.first(); level < levels.last(); level++) {
            if (!levels.contains(level + 1)) {
                throw new IllegalStateException("heading level h" + (level + 1) + " is missing on a page that uses "
                    + levels + " — an outline with a hole in it is a page a screen reader falls through");
            }
        }
    }

    private static void collect(@Nullable Object node, List<String> h1, Set<Integer> levels) {
        if (node instanceof Element<?> element) {
            collect(element.asMap(), h1, levels);
        } else if (node instanceof Map<?, ?> descriptor) {
            if (Element.isIllustration(descriptor)) {
                return;
            }
            Integer level = Heading.levelIn(descriptor);
            if (level != null) {
                levels.add(level);
                if (level == 1) {
                    h1.add(Heading.textIn(descriptor));
                }
            }
            for (Object value : descriptor.values()) {
                collect(value, h1, levels);
            }
        } else if (node instanceof Collection<?> items) {
            for (Object item : items) {
                collect(item, h1, levels);
            }
        }
    }
}
