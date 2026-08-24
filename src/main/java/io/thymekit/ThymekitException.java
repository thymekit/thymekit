/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import org.jspecify.annotations.Nullable;

/**
 * What the kit refuses, in a shape somebody else's error handler can route.
 *
 * <p>A consumer of this library may treat a five hundred as a programming error and something to be
 * woken up for. That rule works only when every refusal the kit makes <b>on purpose</b> can be told
 * apart from their own code failing — then what is left uncaught really does mean nobody foresaw it,
 * which is worth waking up for. So the kit throws its own and never borrows: not
 * {@code IllegalArgumentException}, not {@code NullPointerException}, not the one an
 * {@code orElseThrow} hands out when it was given nothing to throw.
 *
 * <p>Three kinds, and the difference between them is not what went wrong but <b>what to do about
 * it</b>:
 *
 * <ul>
 *   <li>{@link MisuseException} — a call written wrong. It should never reach production, and an alert
 *       is the right answer;</li>
 *   <li>{@link UnsoundPageException} — the page does not add up. This one can arrive from data, so the
 *       answer is the consumer's to choose;</li>
 *   <li>{@link ContractBrokenException} — the walk over a triple did not agree, which happens in
 *       tests.</li>
 * </ul>
 *
 * <p>Every refusal names the place it was made at, and the place is in front of the message as well as
 * in {@link #where()} — a handler that logs nothing but the message is the common case, and it would
 * otherwise lose the one thing that says where to look.
 *
 * <p>Abstract, because which of the three it is decides what a consumer does, and refusing without
 * saying which is refusing to answer the only question that matters.
 *
 * <p>Three and no more: the constructors of this class are not visible outside the package, so the
 * family cannot be extended from a consumer's code. That is not a fence around the vocabulary but the
 * point of it — a fourth kind would be a fourth answer to "what do I do about this", and there are only
 * three. An element of yours throws one of the three; a guard of yours refusing a bad argument is a
 * {@link MisuseException} exactly as ours is.
 */
public abstract class ThymekitException extends RuntimeException {

    /**
     * What stands in for a place that was left unnamed. Checking the place would mean throwing while
     * throwing, and the second failure would bury the first — the opposite of what any of this is for.
     */
    private static final String UNNAMED = "somewhere in the kit";

    private final String where;

    ThymekitException(@Nullable String where, String detail) {
        this(where, detail, null);
    }

    ThymekitException(@Nullable String where, String detail, @Nullable Throwable cause) {
        super(named(where) + ": " + detail, cause);
        this.where = named(where);
    }

    private static String named(@Nullable String where) {
        return where == null || where.isBlank() ? UNNAMED : where;
    }

    /**
     * Where the refusal was made: a short, stable name meant for searching a log, and always a
     * <b>call</b> — {@code Heading.href(href)}, {@code Breadcrumbs.site(origin)},
     * {@code Rel.of(values) — one of them}, {@code Descriptor.describes.itemListElement[0].name}. A
     * noun would say what was wrong, which the message says anyway; a call says which line to open.
     * Not a sentence for a person to read — that is the message.
     */
    public String where() {
        return where;
    }
}
