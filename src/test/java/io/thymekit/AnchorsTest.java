/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What {@link Anchors} owes a page: that two things on it never answer to the same name.
 *
 * <p>An anchor is an address inside a page. A section takes its accessible name from the id of its
 * heading, and a table of contents links to the same id — so two headings sharing one turn a name into
 * a guess: a screen reader announces the wrong section, and a link lands on whichever came first.
 *
 * <p>What is not checked here is as deliberate as what is. Any element may carry a key called
 * {@code id}, and the kit does not own what that word means in somebody else's element — a card
 * carrying the id of a product in a database is carrying data, not an address. Only the anchors the kit
 * itself puts on a page are counted, which today means the ones a heading was given.
 */
class AnchorsTest {

    private static Element<Element.Raw> row(Composable<?>... items) {
        return Element.raw("t", "rowEl").slot("items", List.of(items)).build();
    }

    /** Two headings answering to one name is refused, and the message says which name. */
    @Test
    void twoThingsMayNotAnswerToOneName() {
        assertThatThrownBy(() -> Anchors.requireDistinct(List.of(
                Heading.h2("Composition").id("composition").build(),
                Heading.h2("How it is made").id("composition").build())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("composition")
            .hasMessageContaining("How it is made");
    }

    /** Names that differ, or no names at all, are a page like any other. */
    @Test
    void namesThatDifferAreFine() {
        assertThatCode(() -> Anchors.requireDistinct(List.of(
                Heading.h2("Composition").id("composition").build(),
                Heading.h2("How it is made").id("how-it-is-made").build(),
                Heading.h2("Nameless").build())))
            .doesNotThrowAnyException();
        assertThatCode(() -> Anchors.requireDistinct(List.of())).doesNotThrowAnyException();
    }

    /** Where a heading stands changes nothing: a page is one document however deep its parts sit. */
    @Test
    void nestingHidesNothing() {
        assertThatThrownBy(() -> Anchors.requireDistinct(List.of(
                Section.of(Heading.h2("Composition").id("composition")).build(),
                row(row(Heading.h3("Also composition").id("composition").build())))))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("composition");
    }

    /**
     * And an illustration hides nothing either — here the two checks of a page part ways. The outline
     * skips a sample framed for display, because what is inside it is a demonstration and not the
     * structure of the page. An id inside that same frame is no demonstration at all: it is a second
     * node in the document with the same address, and a link to it lands wherever it lands.
     */
    @Test
    void anIllustrationHidesNothingEither() {
        Element<Element.Raw> sample = Element.raw("t", "frameEl").illustration()
            .slot("items", List.of(Heading.h2("A sample").id("composition").build()))
            .build();

        assertThatThrownBy(() -> Anchors.requireDistinct(List.of(
                Heading.h2("Composition").id("composition").build(), sample)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("composition");
    }

    /** The same heading twice is two nodes in the document, whatever it was in java. */
    @Test
    void theSameHeadingTwiceIsTwice() {
        Element<Heading> once = Heading.h2("Composition").id("composition").build();

        assertThatThrownBy(() -> Anchors.requireDistinct(List.of(once, once)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("composition");
    }

    /**
     * A key called id on somebody else's element is theirs. The kit does not own that word: a card
     * carrying the id of a product is carrying data, and a page that fell over because two products
     * share a number would be the kit inventing a rule nobody agreed to.
     */
    @Test
    void anIdThatIsNotAnAnchorIsNotOurBusiness() {
        assertThatCode(() -> Anchors.requireDistinct(List.of(
                Heading.h2("Composition").id("composition").build(),
                Element.raw("fragments/my/card", "myCardEl").with("id", "composition").build(),
                Element.raw("fragments/my/card", "myCardEl").with("id", "composition").build())))
            .doesNotThrowAnyException();
    }
}
