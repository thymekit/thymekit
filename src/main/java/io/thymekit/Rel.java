/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
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
        Guards.required(values, "Rel.of(values)");
        if (values.length == 0) {
            throw new MisuseException("Rel.of(values)",
                "no value was named — name at least one, or do not ask for one at all");
        }
        return Collections.unmodifiableSet(guarded(Arrays.asList(values), "Rel.of(values) — one of them"));
    }

    /**
     * The values a link opening a new tab says about itself: whatever it already said, plus
     * {@code noopener}. Without it the opened page can reach back into this one through
     * {@code window.opener}, and remembering that by hand at every link is exactly the kind of vigilance
     * the kit exists to remove — from a consumer's element as much as from its own.
     *
     * <p>Asked at build time rather than as the options are said, so the order of the calls cannot
     * change the result, and asking twice says the same thing as asking once.
     *
     * <p>A set, and not any collection: a link saying {@code nofollow} twice is not wrong to a browser,
     * which is exactly why nobody would ever notice it. What cannot be written needs no guard.
     */
    public static Set<Rel> forNewTab(Set<Rel> values) {
        Guards.required(values, "Rel.forNewTab(values)");
        Set<Rel> withSafety = guarded(values, "Rel.forNewTab(values) — one of them");
        withSafety.add(NOOPENER);
        return Collections.unmodifiableSet(withSafety);
    }

    /**
     * The attribute value: the tokens in the order the set iterates, one space between them. Nothing to
     * say gives nothing at all, so an element can ask without checking first — and then print the
     * attribute only when what comes back is not empty.
     *
     * <p>The order is the set's, which is worth saying plainly rather than promising the author's: hand
     * over the one {@code Set.of} builds and the tokens come out in whatever order it keeps them. No
     * part of a page depends on that order, and a promise that was true only for some sets would be a
     * promise this could not keep.
     */
    public static String tokens(Set<Rel> values) {
        Guards.required(values, "Rel.tokens(values)");
        return values.stream()
            .map(value -> Guards.required(value, "Rel.tokens(values) — one of them").token())
            .collect(Collectors.joining(" "));
    }

    /**
     * The values as a set, in the order given, each said once — and each of them refused if it is not
     * there. Guarding the members and not only the collection is the point: a hole among them would be
     * dereferenced later, and what reaches the caller then is the machine's failure rather than a
     * refusal of ours. Both entrances need that, and one spelling of it is how the two stay agreed.
     */
    private static Set<Rel> guarded(Iterable<Rel> values, String each) {
        Set<Rel> unique = new LinkedHashSet<>();
        for (Rel value : values) {
            unique.add(Guards.required(value, each));
        }
        return unique;
    }
}
