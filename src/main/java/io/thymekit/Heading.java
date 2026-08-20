/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Objects;

/**
 * Owner of the heading concept: anything with a heading composes this element instead of writing its
 * own {@code <h*>}. The level lives in the factory name ({@code h1…h6}) and has no default — it decides
 * the place in the document outline, so the author states it. Options carry meaning only: an anchor id,
 * a heading-link, and screen-reader-only. Appearance comes from {@code --tk-heading-*} handles.
 */
public final class Heading {

    private Heading() {}

    public static Builder h1(String text) { return new Builder(1, text); }
    public static Builder h2(String text) { return new Builder(2, text); }
    public static Builder h3(String text) { return new Builder(3, text); }
    public static Builder h4(String text) { return new Builder(4, text); }
    public static Builder h5(String text) { return new Builder(5, text); }
    public static Builder h6(String text) { return new Builder(6, text); }

    /** Level as a number, for hosts that compute it; 1..6. */
    static Builder of(int level, String text) {
        if (level < 1 || level > 6) {
            throw new IllegalArgumentException("level " + level + ": allowed range is 1..6");
        }
        return new Builder(level, text);
    }

    public static final class Builder {

        private final Element.Descriptor<Heading> b;

        private Builder(int level, String text) {
            this.b = Element.Descriptor.<Heading>of("fragments/thymekit/heading", "headingEl")
                .with("level", level)
                .with("text", Objects.requireNonNull(text, "text"));
        }

        /** Anchor id — for table-of-contents links and {@code aria-labelledby}. */
        public Builder id(String id) {
            b.with("id", Objects.requireNonNull(id, "id"));
            return this;
        }

        /** Heading as a link ({@code hN > a}). */
        public Builder href(String href) {
            b.with("href", Objects.requireNonNull(href, "href"));
            return this;
        }

        /** Present in the outline for screen readers, visually clipped (not {@code display:none}). */
        public Builder srOnly() {
            b.with("srOnly", true);
            return this;
        }

        public Element<Heading> build() {
            return b.build();
        }
    }
}
