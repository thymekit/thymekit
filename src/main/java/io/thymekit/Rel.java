/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a link says about itself, and what every element that links does with it.
 *
 * <p>Five values, because these are the ones a search engine acts on and a browser protects with;
 * anything rarer is added when something actually needs it, not in advance.
 *
 * <p>The vocabulary comes with its policy, and both are public. An element of yours links exactly as the
 * kit's own does:
 *
 * <pre>{@code
 * public Builder rel(Rel... values) {          // guarded, ordered, said once
 *     this.rel.addAll(Rel.of(values));
 *     return this;
 * }
 *
 * public Element<Card> build() {
 *     Set<Rel> values = newTab ? Rel.forNewTab(rel) : rel;      // noopener cannot be forgotten
 *     return b.with("rel", Rel.tokens(values)).build();
 * }
 * }</pre>
 *
 * <p>That the policy is public is the point. A kit that handed out the five words and kept the guards,
 * the order and the one safety rule for its own two elements would be telling every consumer to write
 * the rule again — and the second spelling of a rule is where the two of them start to differ.
 *
 * <p>{@link #of} takes what an option was given, {@link #forNewTab} is asked at build time by whoever
 * opens a tab, and {@link #tokens} writes the attribute. What a link may not say — {@code rel} on
 * something that is not a link at all — belongs to the element, which alone knows whether it made one.
 */
public enum Rel {

    /** Do not pass any ranking to this address — a link the page does not vouch for. */
    NOFOLLOW("nofollow"),

    /** A paid or sponsored placement. */
    SPONSORED("sponsored"),

    /** Written by a visitor: a comment, a review, anything user-generated. */
    UGC("ugc"),

    /** The opened page gets no handle on the one that opened it. Always present with a new tab. */
    NOOPENER("noopener"),

    /** The opened page is not told where the visitor came from. */
    NOREFERRER("noreferrer");

    private final String token;

    Rel(String token) {
        this.token = token;
    }

    /** The token as it is written in the attribute. */
    public String token() {
        return token;
    }

    /**
     * What an element's {@code rel(Rel...)} option was given, made into a value: the order the author
     * wrote, each thing said once, and nothing that can be changed afterwards. Asking for no values is
     * refused — an option called with nothing to say is a line that meant something and lost it.
     */
    public static Set<Rel> of(Rel... values) {
        Objects.requireNonNull(values, "values");
        if (values.length == 0) {
            throw new IllegalArgumentException("rel without a value: name at least one, or do not ask for one at all");
        }
        Set<Rel> unique = new LinkedHashSet<>();
        for (Rel value : values) {
            unique.add(Objects.requireNonNull(value, "a null among the rel values"));
        }
        return Collections.unmodifiableSet(unique);
    }

    /**
     * The values a link opening a new tab says about itself: whatever it already said, plus
     * {@code noopener}. Without it the opened page can reach back into this one through
     * {@code window.opener}, and remembering that by hand at every link is exactly the kind of vigilance
     * the kit exists to remove — from a consumer's element as much as from its own.
     *
     * <p>Asked at build time rather than as the options are said, so the order of the calls cannot
     * change the result, and asking twice says the same thing as asking once.
     */
    public static Set<Rel> forNewTab(Collection<Rel> values) {
        Objects.requireNonNull(values, "values");
        Set<Rel> withSafety = new LinkedHashSet<>();
        for (Rel value : values) {
            withSafety.add(Objects.requireNonNull(value, "a null among the rel values"));
        }
        withSafety.add(NOOPENER);
        return Collections.unmodifiableSet(withSafety);
    }

    /**
     * The attribute value: the tokens in the order given, one space between them. Nothing to say gives
     * nothing at all, so an element can ask without checking first — and then print the attribute only
     * when what comes back is not empty.
     */
    public static String tokens(Collection<Rel> values) {
        Objects.requireNonNull(values, "values");
        return values.stream().map(Rel::token).collect(Collectors.joining(" "));
    }
}
