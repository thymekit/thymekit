/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.LinkedHashSet;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The text of a page: markdown rendered by the {@code #md} dialect and sanitised on the way out, or an
 * empty state when nothing has been written yet. It is content — a heading and a section around it
 * belong to {@link Section}, which this goes inside.
 *
 * <p>The text belongs to whoever wrote it, and its headings keep their shape: {@link MarkdownRenderer}
 * lowers the topmost authored level to a ceiling (h2 by default) and moves the rest by the same amount,
 * so a {@code #} in the source becomes an h2 under the page rather than a second H1. What the author
 * nested stays nested; what the author skipped stays skipped, and {@link Element#assertOutline} never
 * sees any of it.
 */
public final class Md {

    private Md() {}

    /**
     * The text may be absent ({@code null} — nothing written yet): then the block shows its
     * {@link Builder#emptyHint}, and without a hint it is not rendered at all.
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
            this.hasText = markdown != null;
            if (hasText) {
                b.with("markdown", markdown);
            }
        }

        /** Empty-state text, shown instead of the block when there is no markdown. */
        public Builder emptyHint(String hint) {
            b.with("emptyHint", Objects.requireNonNull(hint, "emptyHint"));
            hasHint = true;
            return this;
        }

        /**
         * An affordance shown next to the empty state — any element, rendered through the dispatcher.
         * The block does not know what it is, so the wording and the shape stay with the consumer.
         */
        public Builder addAction(Composable<?> action) {
            b.with("addAction", Element.requireRenderableElement(Element.settle(action, "action"), "Md.addAction").asMap());
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
                throw new IllegalStateException("an affordance with nowhere to show it: it stands beside the "
                    + "empty state, which needs text that is absent and a hint that is not");
            }
            if (!linkRel.isEmpty()) {
                if (!hasText) {
                    throw new IllegalStateException("linkRel on a block with no text: the policy would apply to "
                        + "nothing — give the block its markdown, or drop the policy");
                }
                b.with("linkRel", Rel.tokens(linkRel));
            }
            return b.build();
        }
    }
}
