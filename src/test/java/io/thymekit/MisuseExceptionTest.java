/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

/**
 * A call written wrong: a value the kit will not take, a state it will not go into, an argument that
 * was not there. It should never reach production, so a consumer is right to treat it as a programming
 * error and be woken up for it.

 */
class MisuseExceptionTest {

    /** Final, so nobody widens the meaning a handler routes by. */
    @Test
    void isFinal() {
        assertThat(java.lang.reflect.Modifier.isFinal(MisuseException.class.getModifiers())).isTrue();
    }

    /** One of the family, and carrying what the family carries. */
    @Test
    void isOneOfTheFamily() {
        var refusal = new MisuseException("somewhere", "the call was written wrong");

        assertThat(refusal).isInstanceOf(ThymekitException.class);
        assertThat(refusal.where()).isEqualTo("somewhere");
        assertThat(refusal.getMessage()).isEqualTo("somewhere: the call was written wrong");
    }
    /**
     * What it looks like when it happens for real: a heading given nothing to say. The place names the
     * value that was refused, which is what a log is searched by; the message says why.
     */
    @Test
    void isWhatAHeadingWithNothingToSayThrows() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Heading.h2("   "))
            .satisfies(refusal -> {
                assertThat(refusal.where()).isEqualTo("Heading(text)");
                assertThat(refusal.getMessage()).contains("is blank");
            });
    }

    /**
     * And it takes what went wrong underneath, for an element of somebody else's: a guard of theirs may
     * be wrapping a failure from further down, and losing it would leave a handler with half a story.
     */
    @Test
    void carriesWhatWentWrongUnderneath() {
        var underneath = new java.io.IOException("the disk said no");

        assertThat(new MisuseException("MyCard.of(image)", "cannot be read", underneath))
            .hasCause(underneath)
            .hasMessageStartingWith("MyCard.of(image): ");
    }
}
