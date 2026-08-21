/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What a link says about itself. Five values, because these are the ones a search engine acts on and a
 * browser protects with; anything rarer is added when something actually needs it.
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

    /** The attribute value for a set of values, in the order they were given. Shared by every element that links. */
    static String tokens(Collection<Rel> values) {
        return values.stream().map(Rel::token).collect(Collectors.joining(" "));
    }

    /** What a link element does with its own varargs: refuse an empty call, collapse repetitions, keep order. */
    static Set<Rel> required(Rel[] values, String what) {
        Objects.requireNonNull(values, "values");
        if (values.length == 0) {
            throw new IllegalArgumentException(what + " without a value: name at least one, or do not call "
                + what + "(...) at all");
        }
        Set<Rel> unique = new LinkedHashSet<>();
        for (Rel value : values) {
            unique.add(Objects.requireNonNull(value, () -> "a null among the values of " + what + "(...)"));
        }
        return unique;
    }
}
