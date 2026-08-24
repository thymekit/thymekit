/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * What the kit refuses, in a shape somebody else's error handler can route.
 *
 * <p>The consumer this was written for treats a five hundred as a programming error and something to
 * be woken up for. That works only if every refusal the kit makes on purpose can be told apart from
 * their own code failing — then what is left uncaught means nobody foresaw it, which is worth waking
 * up for. So the kit throws its own types and nothing else, and this is the shape they share.
 */
class ThymekitExceptionTest {

    /** The place is not decoration: it is what a log line is searched by afterwards. */
    @Test
    void carriesThePlaceItRefusedAt() {
        var refusal = new MisuseException("Heading.href(href)", "is not a link but a script");

        assertThat(refusal.where()).isEqualTo("Heading.href(href)");
    }

    /**
     * And the message carries it too, in front. A handler that logs nothing but the message would
     * otherwise lose the one thing that says where to look, and most handlers log nothing but the
     * message.
     */
    @Test
    void putsThePlaceInFrontOfTheMessage() {
        var refusal = new MisuseException("Heading.href(href)", "is not a link but a script");

        assertThat(refusal.getMessage()).isEqualTo("Heading.href(href): is not a link but a script");
    }

    /** What went wrong underneath is kept, so the chain reads from the top to the disk. */
    @Test
    void keepsWhatWentWrongUnderneath() {
        var underneath = new java.io.IOException("the disk said no");
        var refusal = new ContractBrokenException("ElementContract.check",
            "cannot read templates/thymekit/heading.html", underneath);

        assertThat(refusal).hasCause(underneath);
        assertThat(refusal.getMessage())
            .isEqualTo("ElementContract.check: cannot read templates/thymekit/heading.html");
    }

    /**
     * Nobody throws "a thymekit exception". Which of the three it is decides what a consumer does about
     * it, so refusing without saying which is refusing to answer the only question that matters.
     */
    @Test
    void theFamilyItselfIsNotThrowable() {
        assertThat(java.lang.reflect.Modifier.isAbstract(ThymekitException.class.getModifiers())).isTrue();

        assertThat(ThymekitException.class.getDeclaredConstructors())
            .as("nor is it a family a consumer can add a fourth kind to: a fourth kind would be a fourth"
                + " answer to what do I do about this, and there are three")
            .allSatisfy(made -> assertThat(java.lang.reflect.Modifier.isPublic(made.getModifiers())
                || java.lang.reflect.Modifier.isProtected(made.getModifiers())).isFalse());
    }

    /**
     * A refusal whose place was left unnamed still reports one. Validating the place would mean
     * throwing while throwing, and the second failure would bury the first — which is the opposite of
     * what any of this is for.
     */
    @Test
    void reportsSomethingEvenWhenThePlaceWasNotNamed() {
        assertThat(new MisuseException(null, "something").where()).isEqualTo("somewhere in the kit");
        assertThat(new MisuseException("  ", "something").getMessage())
            .isEqualTo("somewhere in the kit: something");
    }

    /** All three are caught as one, which is what lets a handler say "the kit refused" in one clause. */
    @Test
    void allThreeAreCaughtAsOne() {
        assertThat(new MisuseException("a", "b")).isInstanceOf(ThymekitException.class);
        assertThat(new UnsoundPageException("a", "b")).isInstanceOf(ThymekitException.class);
        assertThat(new ContractBrokenException("a", "b")).isInstanceOf(ThymekitException.class);
    }
}
