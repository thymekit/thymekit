/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

/**
 * The page does not add up: more than one title, a gap in the heading levels, two things answering to
 * one name. Unlike a call written wrong, this one can arrive from <b>data</b> — two children with the
 * same slug is a state of a database, not a mistake in code — so what to do about it is the consumer's
 * decision rather than an alert.

 */
class UnsoundPageExceptionTest {

    /** Final, so nobody widens the meaning a handler routes by. */
    @Test
    void isFinal() {
        assertThat(java.lang.reflect.Modifier.isFinal(UnsoundPageException.class.getModifiers())).isTrue();
    }

    /** One of the family, and carrying what the family carries. */
    @Test
    void isOneOfTheFamily() {
        var refusal = new UnsoundPageException("somewhere", "the page does not add up");

        assertThat(refusal).isInstanceOf(ThymekitException.class);
        assertThat(refusal.where()).isEqualTo("somewhere");
        assertThat(refusal.getMessage()).isEqualTo("somewhere: the page does not add up");
    }
    /**
     * What it looks like when it happens for real: a page with two titles. This is the kind that can
     * arrive from data rather than from a mistake in code, which is why it is a kind of its own.
     */
    @Test
    void isWhatAPageWithTwoTitlesThrows() {
        var model = new org.springframework.ui.ConcurrentModel();

        assertThatExceptionOfType(UnsoundPageException.class)
            .isThrownBy(() -> PageModel.of(model).title("Aloe")
                .add(Heading.h1("Aloe")).add(Heading.h1("Also Aloe")).render())
            .satisfies(refusal -> {
                assertThat(refusal.where()).isEqualTo("Outline.requireSound");
                assertThat(refusal.getMessage()).contains("more than one H1");
            });
    }

    /** And it takes what went wrong underneath, for a page check of somebody else's. */
    @Test
    void carriesWhatWentWrongUnderneath() {
        var underneath = new IllegalStateException("counted wrong");

        assertThat(new UnsoundPageException("MyChecks.headings", "does not add up", underneath))
            .hasCause(underneath)
            .hasMessageStartingWith("MyChecks.headings: ");
    }
}
