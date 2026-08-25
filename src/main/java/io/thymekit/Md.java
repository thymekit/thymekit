/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.LinkedHashSet;
import org.jspecify.annotations.Nullable;

/**
 * The text of a page: markdown rendered by the {@code #md} dialect and sanitised on the way out, or an
 * empty state when nothing has been written yet. It is content — a heading and a section around it
 * belong to {@link Section}, which this goes inside.
 *
 * <p>Its content is data, and that decides the shape of everything here. A caption refuses a blank
 * text, because a blank caption is a programmer writing nothing on purpose; this element treats blank
 * as an absence, because an empty column is not a mistake anybody made and a page falling over for it
 * would be the kit punishing a consumer for their data. What the consumer writes — the hint, the
 * affordance, the link policy — is code again, and held to the stricter rule.
 *
 * <p>The text belongs to whoever wrote it, and its headings keep their shape: {@link MarkdownRenderer}
 * lowers the topmost authored level to a ceiling (h2 by default) and moves the rest by the same amount,
 * so a {@code #} in the source becomes an h2 under the page rather than a second H1. What the author
 * nested stays nested; what the author skipped stays skipped, and {@link Outline} never
 * sees any of it.
 */
public final class Md {

    private Md() {}

    /**
     * The text may be absent — nothing written yet — and blank counts as absent for the same reason.
     * A column in a database is empty because of something nobody chose, and a page has no way to tell
     * "never filled" from "filled with nothing". Then the block shows its {@link Builder#emptyHint},
     * and without a hint it is not rendered at all.
     */
    public static Builder of(@Nullable String markdown) {
        return new Builder(markdown);
    }

    public static final class Builder implements Composable<Md> {

        private final Element.Descriptor<Md> b;
        private final LinkedHashSet<Rel> linkRel = new LinkedHashSet<>();
        private final boolean hasText;
        private boolean hasHint;
        private boolean hasAction;

        private Builder(@Nullable String markdown) {
            this.b = Element.Descriptor.<Md>of("thymekit/md", "mdEl");
            this.hasText = !Guards.isNothing(markdown);
            if (hasText) {
                b.with("markdown", markdown);
            }
        }

        /**
         * Empty-state text, shown instead of the block when there is no markdown. Written by whoever
         * composes the page rather than taken from data, so it is held to the rule every written text
         * is: a hint with nothing in it is an empty box where an explanation was meant to be.
         */
        public Builder emptyHint(String hint) {
            b.with("emptyHint", Guards.text(hint, "Md.emptyHint(hint)"));
            hasHint = true;
            return this;
        }

        /**
         * An affordance shown next to the empty state — any element, rendered through the dispatcher.
         * The block does not know what it is, so the wording and the shape stay with the consumer.
         */
        public Builder addAction(Composable<?> action) {
            b.with("addAction", Element.requireRenderableElement(
                Element.settle(action, "Md.addAction(action)"), "Md.addAction(action)").asMap());
            hasAction = true;
            return this;
        }

        /**
         * What the links of this text say about themselves: {@code linkRel(UGC, NOFOLLOW)} for text a
         * visitor wrote, nothing for text your own editors wrote. The kit has no opinion about whose
         * text this is — only the consumer knows, and both defaults would be wrong for the other case.
         *
         * <p>Marked are the links that leave the site — anything carrying a scheme ({@code https://…})
         * or an authority ({@code //host/…}). A path of your own ({@code /ingredients/baobab},
         * {@code #composition}, {@code ../sibling}) keeps its weight, since holding that back would be
         * a wound self-inflicted.
         */
        public Builder linkRel(Rel... values) {
            linkRel.addAll(Rel.of(values));
            return this;
        }

        @Override
        public Element<Md> build() {
            if (hasAction && (hasText || !hasHint)) {
                throw new MisuseException("Md.build", "an affordance with nowhere to show it: it stands beside the "
                    + "empty state, which needs text that is absent and a hint that is not");
            }
            if (!linkRel.isEmpty()) {
                if (!hasText) {
                    throw new MisuseException("Md.build", "linkRel on a block with no text: the policy would apply to "
                        + "nothing — give the block its markdown, or drop the policy");
                }
                b.with("linkRel", Rel.tokens(linkRel));
            }
            return b.build();
        }
    }
}
