/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A markdown block as a page element: optional section heading plus text rendered by the {@code #md}
 * dialect, sanitised on the way out.
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

    public static final class Builder {

        private final Element.Descriptor<Md> b;

        private Builder(@Nullable String markdown) {
            this.b = Element.Descriptor.<Md>of("fragments/thymekit/md-section", "mdSectionEl");
            if (markdown != null) {
                b.with("markdown", markdown);
            }
        }

        /** Section heading; the author names the level according to the outline. */
        public Builder title(Element<Heading> heading) {
            Element.requireAdapter(Objects.requireNonNull(heading, "heading"), "headingEl", "Md.title accepts a heading only");
            b.with("heading", heading.asMap());
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
        public Builder addAction(Element<?> action) {
            b.with("addAction", Element.requireRenderableElement(action, "Md.addAction").asMap());
            return this;
        }

        public Element<Md> build() {
            return b.build();
        }
    }
}
