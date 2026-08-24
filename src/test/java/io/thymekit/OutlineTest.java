/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What a page's headings must add up to before it is rendered.
 *
 * <p>These are not opinions about style. A second H1 tells a search engine the page is about two things;
 * a level skipped is a hole a screen reader walks straight through; a level html does not have renders
 * as nothing at all, which is the kind of silence a page should never ship with. All three are found on
 * the finished page by somebody who cannot fix them, so they are found here instead.
 *
 * <p>Where a heading stands is not the guard's business — nesting never hides one and never trips it.
 *
 * <p>And what a heading is, this class does not know: it asks {@link Heading}, which owns the adapter
 * and therefore owns the name of it. That is why the check lives here and not on the currency, where it
 * had taught {@code Element} the name of one element's fragment.
 */
class OutlineTest {

    private static Element<Element.Raw> row(Composable<?>... items) {
        return Element.raw("t", "rowEl").slot("items", List.of(items)).build();
    }

    /** The title of a page is one thing. */
    @Test
    void aPageHasOneTitleAtMost() {
        assertThatCode(() -> Outline.requireSound(List.of(Heading.h1("The page").build()))).doesNotThrowAnyException();

        assertThatThrownBy(() -> Outline.requireSound(
                List.of(Heading.h1("The page").build(), Heading.h1("And another").build())))
            .isInstanceOf(UnsoundPageException.class)
            .hasMessageStartingWith("Outline.requireSound:")
            .hasMessageContaining("more than one H1").hasMessageContaining("And another");
    }

    /** And a heading is found wherever it stands: inside a container, inside a container of containers. */
    @Test
    void nestingHidesNothing() {
        assertThatThrownBy(() -> Outline.requireSound(
                List.of(Heading.h1("The page").build(), row(row(Heading.h1("Buried").build())))))
            .isInstanceOf(UnsoundPageException.class).hasMessageContaining("Buried");
    }

    /** The levels a page uses run without a gap, whatever order they appear in. */
    @Test
    void theLevelsRunWithoutAGap() {
        assertThatCode(() -> Outline.requireSound(List.of(
                Heading.h1("Page").build(), Heading.h2("Section").build(), Heading.h3("Under it").build())))
            .doesNotThrowAnyException();
        assertThatCode(() -> Outline.requireSound(List.of(
                Heading.h2("A").build(), Heading.h3("B").build())))
            .as("a page may start below h1").doesNotThrowAnyException();

        assertThatThrownBy(() -> Outline.requireSound(
                List.of(Heading.h1("Page").build(), Heading.h3("Deep").build())))
            .isInstanceOf(UnsoundPageException.class).hasMessageContaining("h2").hasMessageContaining("[1, 3]");
        assertThatThrownBy(() -> Outline.requireSound(
                List.of(row(Heading.h4("Deep").build()), Heading.h1("Page").build(), Heading.h2("Section").build())))
            .as("order in the flow is not the guard's business")
            .isInstanceOf(UnsoundPageException.class).hasMessageContaining("h3");
    }

    /** A page with no headings at all is a page, and one level alone is contiguous. */
    @Test
    void aPageWithoutHeadingsIsLegal() {
        assertThatCode(() -> Outline.requireSound(List.of())).doesNotThrowAnyException();
        assertThatCode(() -> Outline.requireSound(List.of(Caption.meta("12 entries").build())))
            .doesNotThrowAnyException();
        assertThatCode(() -> Outline.requireSound(List.of(Heading.h4("Alone").build())))
            .doesNotThrowAnyException();
    }

    /** Html has six levels, and an element carrying anything else renders nothing at all. */
    @Test
    void htmlHasSixLevels() {
        for (Object impossible : List.of(7, "7", 0L)) {
            assertThatThrownBy(() -> Outline.requireSound(List.of(Element.raw("thymekit/heading", "headingEl")
                    .with("level", impossible).with("text", "x")
                    .means("level", Element.Role.HEADING_LEVEL).build())))
                .isInstanceOf(UnsoundPageException.class).hasMessageContaining("outside h1..h6");
        }
        assertThatCode(() -> Outline.requireSound(List.of(
                Heading.h1("a").build(), Heading.h2("b").build(), Heading.h3("c").build(),
                Heading.h4("d").build(), Heading.h5("e").build(), Heading.h6("f").build())))
            .doesNotThrowAnyException();
    }

    /**
     * A level counts however it was written. The kit's factories always write a number, but an element
     * minted by hand may carry text — and a guard that understood only one of the two would have let a
     * second H1 through while the adapter rendered it happily.
     */
    @Test
    void aLevelCountsHoweverItWasWritten() {
        assertThatThrownBy(() -> Outline.requireSound(List.of(Heading.h1("Page").build(),
                Element.raw("thymekit/heading", "headingEl").with("level", "1").with("text", "sneaky")
                    .means("level", Element.Role.HEADING_LEVEL).build())))
            .isInstanceOf(UnsoundPageException.class).hasMessageContaining("sneaky");
        assertThatCode(() -> Outline.requireSound(List.of(Heading.h1("Page").build(),
                Element.raw("thymekit/heading", "headingEl").with("level", " 2 ").with("text", "spaced")
                    .means("level", Element.Role.HEADING_LEVEL).build())))
            .as("text with spaces still reads as a level").doesNotThrowAnyException();
        assertThatCode(() -> Outline.requireSound(List.of(
                Element.raw("thymekit/heading", "headingEl").with("level", "two").with("text", "x")
                    .means("level", Element.Role.HEADING_LEVEL).build(),
                Element.raw("thymekit/heading", "headingEl").with("text", "no level at all").build())))
            .as("what does not read as a level is not the guard's business").doesNotThrowAnyException();
    }

    /**
     * An illustration is a sample framed for display, not the structure of the page: a showcase shows a
     * heading of every level side by side, and the outline of the page it stands on is untouched by it.
     */
    /**
     * And a heading of somebody else's counts towards the outline exactly as ours does. A page of theirs
     * may not skip from two to four either — which is the point: the outline asks what a key <b>is</b>,
     * so an element that says its key is a heading level joins the check without asking anybody.
     */
    @Test
    void aHeadingOfSomebodyElsesIsCountedToo() {
        assertThatThrownBy(() -> Outline.requireSound(List.of(chapter(2), chapter(4))))
            .isInstanceOf(UnsoundPageException.class).hasMessageContaining("h3");

        assertThatCode(() -> Outline.requireSound(List.of(chapter(2), chapter(3))))
            .as("and a page of theirs that adds up is a page like any other").doesNotThrowAnyException();

        assertThatCode(() -> Outline.requireSound(List.of(
                Element.raw("fragments/my/chapter", "chapterEl").with("level", 2).build(),
                Element.raw("fragments/my/chapter", "chapterEl").with("level", 4).build())))
            .as("a key called level that was never said to be one is data, and data is theirs")
            .doesNotThrowAnyException();
    }

    /** A chapter of somebody else's, saying which of its keys is the level. */
    private static Element<Element.Raw> chapter(int level) {
        return Element.raw("fragments/my/chapter", "chapterEl")
            .with("level", level).with("title", "Part " + level)
            .means("level", Element.Role.HEADING_LEVEL)
            .build();
    }

    @Test
    void anIllustrationIsNotStructure() {
        Element<Element.Raw> sample = Element.raw("t", "frameEl").illustration()
            .slot("items", List.of(Heading.h1("A sample H1").build(), Heading.h4("And an h4").build()))
            .build();

        assertThatCode(() -> Outline.requireSound(List.of(Heading.h1("The page").build(), sample)))
            .doesNotThrowAnyException();
    }
}
