/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.LinkedHashSet;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A markdown block as a page element: optional section heading plus text rendered by the {@code #md}
 * dialect, sanitised on the way out.
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
     * The text may be absent ({@code null} — nothing written yet): then the section shows its
     * {@link Builder#emptyHint}, and without a hint it is not rendered at all.
     */
    public static Builder of(@Nullable String markdown) {
        return new Builder(markdown);
    }

    public static final class Builder implements Composable<Md> {

        private final Element.Descriptor<Md> b;
        private final LinkedHashSet<Rel> linkRel = new LinkedHashSet<>();
        private final boolean hasText;

        private Builder(@Nullable String markdown) {
            this.b = Element.Descriptor.<Md>of("thymekit/md-section", "mdSectionEl");
            this.hasText = markdown != null;
            if (hasText) {
                b.with("markdown", markdown);
            }
        }

        /** Section heading; the author names the level according to the outline. */
        public Builder title(Composable<Heading> heading) {
            Element<Heading> settled = Element.settle(heading, "heading");
            Element.requireAdapter(settled, "headingEl", "Md.title accepts a heading only");
            b.with("heading", settled.asMap());
            return this;
        }

        /** Empty-state text, shown instead of the block when there is no markdown. */
        public Builder emptyHint(String hint) {
            b.with("emptyHint", Objects.requireNonNull(hint, "emptyHint"));
            return this;
        }

        /**
         * An affordance shown next to the empty state — any element, rendered through the dispatcher.
         * The section does not know what it is, so the wording and the shape stay with the consumer.
         */
        public Builder addAction(Composable<?> action) {
            b.with("addAction", Element.requireRenderableElement(Element.settle(action, "action"), "Md.addAction").asMap());
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
            linkRel.addAll(Rel.required(values, "linkRel"));
            return this;
        }

        @Override
        public Element<Md> build() {
            if (!linkRel.isEmpty()) {
                if (!hasText) {
                    throw new IllegalStateException("linkRel on a section with no text: the policy would apply to "
                        + "nothing — give the section its markdown, or drop the policy");
                }
                b.with("linkRel", Rel.tokens(linkRel));
            }
            return b.build();
        }
    }
}
