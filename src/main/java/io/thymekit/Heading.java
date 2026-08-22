/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

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

    /** An anchor is an address inside a page: whatever it is made of, it holds together as one word. */
    private static final Pattern BREAKS_AN_ANCHOR = Pattern.compile("\\s");

    private Heading() {}

    /**
     * The level of a heading in a descriptor, or {@code null} where the descriptor is not one.
     *
     * <p>Recognising a heading is this element's business and nobody else's: it owns the adapter, so it
     * owns the name of it. The outline of a page asks here rather than knowing the answer, and so may a
     * check of yours walking a page with {@link Element#walk} — the three readers below are published
     * together, since a check that can read the text of a heading but not its level is half a gift.
     *
     * <p>A level counts however it was written — as a number or as text. The factories above always
     * write a number, but an element minted by hand may not, and a guard that understood only one of
     * the two would let a second H1 through while the adapter rendered it happily.
     */
    public static @Nullable Integer levelIn(Map<?, ?> descriptor) {
        if (!"headingEl".equals(descriptor.get("fragment"))) {
            return null;
        }
        Object level = descriptor.get("level");
        if (level instanceof Number number) {
            return number.intValue();
        }
        if (level instanceof String text) {
            try {
                return Integer.valueOf(text.strip());
            } catch (NumberFormatException notALevel) {
                return null;
            }
        }
        return null;
    }

    /**
     * The anchor of a heading in a descriptor, or {@code null} where there is none — the heading was
     * given no id, or the descriptor is not a heading at all. Recognising one is this element's business
     * for the same reason its level is: it owns the adapter, so it owns what the keys mean.
     */
    public static @Nullable String idIn(Map<?, ?> descriptor) {
        return levelIn(descriptor) == null ? null : (String) descriptor.get("id");
    }

    /** What such a heading says, for a message a person will read. */
    public static String textIn(Map<?, ?> descriptor) {
        return String.valueOf(descriptor.get("text"));
    }

    public static Builder h1(String text) { return new Builder(1, text); }
    public static Builder h2(String text) { return new Builder(2, text); }
    public static Builder h3(String text) { return new Builder(3, text); }
    public static Builder h4(String text) { return new Builder(4, text); }
    public static Builder h5(String text) { return new Builder(5, text); }
    public static Builder h6(String text) { return new Builder(6, text); }

    public static final class Builder implements Composable<Heading> {

        private final Element.Descriptor<Heading> b;
        private final Set<Rel> rel = new LinkedHashSet<>();
        private boolean newTab;
        private boolean linked;

        private Builder(int level, String text) {
            this.b = Element.Descriptor.<Heading>of("thymekit/heading", "headingEl")
                .with("level", level)
                .with("text", Element.requireText(text, "text"));
        }

        /**
         * The anchor of this heading: what a table of contents links to, and what a section around it
         * is named by. An address, so no whitespace may be in it — an attribute takes the first word
         * and what follows becomes something nobody meant. What it is made of otherwise is the
         * author's: a slug, a number, a word in their own language.
         */
        public Builder id(String id) {
            b.with("id", anchor(id));
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
            rel.addAll(Rel.of(values));
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
        /** An anchor is one word: given, not empty, and with nothing in it that ends an attribute. */
        private static String anchor(String id) {
            Objects.requireNonNull(id, "id");
            if (id.isBlank() || BREAKS_AN_ANCHOR.matcher(id).find()) {
                throw new IllegalArgumentException("not an anchor: \"" + id + "\" — an address inside a "
                    + "page holds together as one word, and an attribute keeps only what comes before "
                    + "the first space");
            }
            return id;
        }

        @Override
        public Element<Heading> build() {
            if (!linked && (newTab || !rel.isEmpty())) {
                throw new IllegalStateException("rel or newTab on a heading that is not a link: "
                    + "call href(...) as well, or drop them — an attribute with no <a> to sit on is printed nowhere");
            }
            Set<Rel> values = newTab ? Rel.forNewTab(rel) : rel;   // a copy: the builder is left as it was
            if (newTab) {
                b.with("target", "_blank");
            }
            if (!values.isEmpty()) {
                b.with("rel", Rel.tokens(values));
            }
            return b.build();
        }
    }
}
