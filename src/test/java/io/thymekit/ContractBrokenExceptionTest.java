/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.Test;

/**
 * The walk over a triple did not agree: an address that resolves to nothing, a key declared and never
 * filled, a class printed and never styled. This happens where a consumer runs the walk, which is
 * their tests, so it is neither a page failing nor a call in production going wrong.

 */
class ContractBrokenExceptionTest {

    /** Final, so nobody widens the meaning a handler routes by. */
    @Test
    void isFinal() {
        assertThat(java.lang.reflect.Modifier.isFinal(ContractBrokenException.class.getModifiers())).isTrue();
    }

    /** One of the family, and carrying what the family carries. */
    @Test
    void isOneOfTheFamily() {
        var refusal = new ContractBrokenException("somewhere", "the walk over a triple did not agree");

        assertThat(refusal).isInstanceOf(ThymekitException.class);
        assertThat(refusal.where()).isEqualTo("somewhere");
        assertThat(refusal.getMessage()).isEqualTo("somewhere: the walk over a triple did not agree");
    }
    /**
     * What it looks like when it happens for real: an element whose adapter is not there. The place is
     * the walk rather than the element, because the walk reports everything wrong at once and the
     * message is where the elements are named.
     */
    @Test
    void isWhatAnAddressLeadingNowhereThrows() {
        assertThatExceptionOfType(ContractBrokenException.class)
            .isThrownBy(() -> ElementContract.of(Element.raw("test/pieces", "absentEl")).check())
            .satisfies(refusal -> {
                assertThat(refusal.where()).isEqualTo("ElementContract.check");
                assertThat(refusal.getMessage()).contains("absentEl");
            });
    }
}
