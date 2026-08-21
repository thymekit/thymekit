/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Owner of the heading concept: anything with a heading composes this element instead of writing its
 * own {@code <h*>}. The level lives in the factory name ({@code h1…h6}) and has no default — it decides
 * the place in the document outline, so the author states it. Options carry meaning only: an anchor id,
 * a heading-link with what it says about itself ({@code rel}) and where it opens ({@code newTab}), the
 * language of the text when it is not the page's, and screen-reader-only. Appearance comes from
 * {@code --tk-heading-*} handles.
 */
public final class Heading {

    /**
     * Everything a browser throws away before it reads the scheme of an address: spaces, tabs, newlines
     * and control characters. {@code java\tscript:} is {@code javascript:} by the time it is followed,
     * so the guard has to look at the address the same way.
     */
    private static final Pattern IGNORED_IN_SCHEME = Pattern.compile("[\\s\\p{Cntrl}]");

    /**
     * Schemes that execute instead of navigating. {@code blob:} and {@code file:} are not here: they
     * navigate, and a heading is not where either becomes dangerous.
     */
    private static final Set<String> EXECUTING_SCHEMES = Set.of("javascript:", "data:", "vbscript:");

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
        private final Set<Rel> rel = new LinkedHashSet<>();
        private boolean newTab;
        private boolean linked;

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

        /**
         * Heading as a link ({@code hN > a}). The address is the consumer's, and it is kept trimmed. A
         * scheme that executes rather than navigates is refused here — {@code javascript:}, {@code data:},
         * {@code vbscript:}, however they are spelled: a browser drops spaces, tabs and control characters
         * before it reads a scheme, so {@code java\tscript:} is refused with the plain spelling. An href
         * is the last place any of them can be stopped before the page.
         */
        public Builder href(String href) {
            String value = Objects.requireNonNull(href, "href").strip();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("href is blank");
            }
            String asFollowed = IGNORED_IN_SCHEME.matcher(value).replaceAll("").toLowerCase(Locale.ROOT);
            for (String executing : EXECUTING_SCHEMES) {
                if (asFollowed.startsWith(executing)) {
                    throw new IllegalArgumentException("href is not a link but a script: \"" + href + "\"");
                }
            }
            b.with("href", value);
            linked = true;
            return this;
        }

        /**
         * What the link says about itself: {@code rel(NOFOLLOW)} for an address the page does not vouch
         * for, {@code rel(UGC)} for one a visitor wrote. Repetitions collapse, order is kept.
         */
        public Builder rel(Rel... values) {
            rel.addAll(Rel.required(values, "rel"));
            return this;
        }

        /**
         * Open in a new tab. {@code noopener} comes with it and cannot be forgotten: without it the
         * opened page can reach back into this one through {@code window.opener}, and remembering that
         * by hand at every link is exactly the kind of vigilance the kit exists to remove.
         */
        public Builder newTab() {
            newTab = true;
            return this;
        }

        /**
         * Language of the heading when it differs from the page — a Latin name, a title kept in the
         * language it was published in.
         */
        public Builder lang(String languageTag) {
            b.with("lang", Element.requireTag(languageTag, "languageTag"));
            return this;
        }

        /** Present in the outline for screen readers, visually clipped (not {@code display:none}). */
        public Builder srOnly() {
            b.with("srOnly", true);
            return this;
        }

        /**
         * Terminal. The link is assembled here rather than as it is said, so the order of the calls
         * cannot change the result: {@code noopener} joins a new tab whether the tab was asked for
         * before the rel values or after them.
         */
        public Element<Heading> build() {
            if (!linked && (newTab || !rel.isEmpty())) {
                throw new IllegalStateException("rel or newTab on a heading that is not a link: "
                    + "call href(...) as well, or drop them — an attribute with no <a> to sit on is printed nowhere");
            }
            Set<Rel> values = new LinkedHashSet<>(rel);      // the builder is left as it was: build() is not a step
            if (newTab) {
                b.with("target", "_blank");
                values.add(Rel.NOOPENER);
            }
            if (!values.isEmpty()) {
                b.with("rel", Rel.tokens(values));
            }
            return b.build();
        }
    }
}
