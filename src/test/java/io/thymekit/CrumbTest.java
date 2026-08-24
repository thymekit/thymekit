/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

/**
 * A link in a trail, or the page the trail ends at.
 *
 * <p>Not an element: it appears only inside a trail, never on its own, and nobody would put something
 * else in its place — so it is a value the caller writes, not a triple with a fragment and a stylesheet
 * of its own.
 *
 * <p>Two states, two factories. The page you are on has no address to go to, and the point of writing
 * it this way is that you cannot give it one by accident: there is no call that produces a current
 * crumb with a link in it.
 */
class CrumbTest {

    /** A link in the trail: where it goes, and what it is called. */
    @Test
    void aLinkKnowsWhereItGoes() {
        Crumb crumb = Crumb.link("/ingredients", "Ingredients");

        assertThat(crumb.url()).isEqualTo("/ingredients");
        assertThat(crumb.label()).isEqualTo("Ingredients");
    }

    /**
     * The page you are on is named and goes nowhere. Its address is the address of the page it is
     * printed on, which is why the trail does not repeat it and the graph leaves it out.
     */
    @Test
    void theCurrentPageGoesNowhere() {
        Crumb crumb = Crumb.current("Aloe");

        assertThat(crumb.url()).isNull();
        assertThat(crumb.label()).isEqualTo("Aloe");
    }

    /** Written exactly as given: a space inside a name belongs to whoever wrote the name. */
    @Test
    void keepsWhatItWasGiven() {
        assertThat(Crumb.link("/a b", " Aloe  vera ").label()).isEqualTo(" Aloe  vera ");
    }

    /** Two crumbs describing the same step are the same crumb: it is a value, and behaves as one. */
    @Test
    void isAValue() {
        assertThat(Crumb.link("/x", "X")).isEqualTo(Crumb.link("/x", "X"))
            .hasSameHashCodeAs(Crumb.link("/x", "X"));
        assertThat(Crumb.current("X")).isNotEqualTo(Crumb.link("/x", "X"));
    }

    // what is refused

    /** A step with nothing written on it would print an empty gap between two separators. */
    @Test
    void refusesALinkWithNoName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> Crumb.link("/x", "  "))
            .withMessageContaining("label");
    }

    /** The same for the page you are on, and for the same reason. */
    @Test
    void refusesACurrentPageWithNoName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> Crumb.current(""))
            .withMessageContaining("label");
    }

    /** A name that was never given is refused where it was not given. */
    @Test
    void refusesANameThatIsNotThere() {
        assertThatNullPointerException().isThrownBy(() -> Crumb.current(null)).withMessageContaining("label");
        assertThatNullPointerException().isThrownBy(() -> Crumb.link("/x", null)).withMessageContaining("label");
    }

    /**
     * A link with no address is not a link. It would print an anchor that goes nowhere — visible,
     * clickable to the eye, and doing nothing.
     */
    @Test
    void refusesALinkWithNoAddress() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> Crumb.link(" ", "X"))
            .withMessageContaining("url");
        assertThatNullPointerException().isThrownBy(() -> Crumb.link(null, "X")).withMessageContaining("url");
    }

    /**
     * A step is a link on the page, so its address goes through the same guard as any other the kit
     * prints. An href is the last place a scheme that executes instead of navigating can be stopped,
     * and three places in this kit print one — they cannot each decide separately.
     */
    @Test
    void refusesAnAddressThatExecutesInsteadOfNavigating() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> Crumb.link("java\tscript:alert(1)", "X"))
            .withMessageContaining("script");
    }

    /**
     * The guards hold whichever door is used. A record hands out its canonical constructor whether the
     * factories are there or not, so the checks live in it rather than in front of it.
     */
    @Test
    void theGuardsAreInTheValueAndNotOnlyInTheFactories() {
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new Crumb("/x", " "))
            .withMessageContaining("label");
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(() -> new Crumb("", "X"))
            .withMessageContaining("url");
    }

    /** And the constructor still allows the one shape that has no address, because that is a crumb too. */
    @Test
    void theCanonicalConstructorAllowsTheCurrentPage() {
        assertThat(new Crumb(null, "Aloe")).isEqualTo(Crumb.current("Aloe"));
    }
}
