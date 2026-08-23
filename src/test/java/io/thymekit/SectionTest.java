/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Owner of the section concept: a part of a page with a heading and whatever belongs under it.
 *
 * <p>It exists so that nothing else has to write a {@code <section>}. A markdown block, a row of cards,
 * a table of a consumer's own — each of them is content, and a titled area around content is one idea
 * that ought to be written once. The heading is not an option here but the reason the element exists:
 * a section without a title is a div, and a div is not this element's business.
 *
 * <p>What the adapter prints — the {@code <section>}, and the accessible name it takes from the
 * heading's anchor — belongs to the walk over triples, as everywhere.
 */
class SectionTest {

    /** A section is a heading and a flow, in call order. */
    @Test
    void aSectionIsAHeadingAndWhatFollowsIt() {
        Element<Md> text = Md.of("**bold**").build();
        Element<Caption> note = Caption.meta("12 entries").build();

        Element<Section> section = Section.of(Heading.h2("Composition").id("composition"))
            .add(text).add(note).build();

        assertThat(section.asMap())
            .containsEntry("template", "thymekit/section").containsEntry("fragment", "sectionEl")
            .containsEntry("heading", Heading.h2("Composition").id("composition").build().asMap());
        assertThat(section.slot("items")).containsExactly(text.asMap(), note.asMap());
    }

    /** The heading is the reason the element exists, so there is no section without one. */
    @Test
    void thereIsNoSectionWithoutAHeading() {
        assertThatThrownBy(() -> Section.of(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("heading");
        assertThatThrownBy(() -> Section.of(() -> null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("built nothing");

        @SuppressWarnings("unchecked")
        Composable<Heading> notAHeading = (Composable<Heading>) (Composable<?>) Caption.label("Composition");
        assertThatThrownBy(() -> Section.of(notAHeading))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("heading only").hasMessageContaining("captionEl");
    }

    /** A heading alone is a section: whether there is anything worth showing is the consumer's to know. */
    @Test
    void aHeadingAloneIsASection() {
        Element<Section> empty = Section.of(Heading.h2("Nothing here yet")).build();

        assertThat(empty.slot("items")).isEmpty();
        assertThat(empty.asMap()).containsKey("heading");
    }

    /** Whatever becomes an element goes under the heading — including another section. */
    @Test
    void whateverBecomesAnElementGoesUnderTheHeading() {
        Element<Section> nested = Section.of(Heading.h2("Composition"))
            .add(Section.of(Heading.h3("What is in it")).add(Md.of("text")))
            .add(Caption.meta("a maker, settled where it was taken"))
            .build();

        assertThat(nested.slot("items")).hasSize(2);
        assertThat(nested.slot("items").getFirst()).containsEntry("fragment", "sectionEl");
        assertThat(nested.slot("items").get(1)).containsEntry("fragment", "captionEl");
    }

    /** A script is not content: it is declared as a dependency, and the dispatcher would fail on it here. */
    @Test
    void aScriptIsNotWhatGoesUnderAHeading() {
        assertThatThrownBy(() -> Section.of(Heading.h2("Composition")).add(Element.script("t", "myJs")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Section.add").hasMessageContaining("requires()");
        assertThatThrownBy(() -> Section.of(Heading.h2("Composition")).add(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("element");
    }

    /** A maker may go on being written, and the section that was built does not follow it. */
    @Test
    void whatWasBuiltDoesNotFollowTheMaker() {
        Section.Builder maker = Section.of(Heading.h2("Composition")).add(Md.of("first"));
        Element<Section> early = maker.build();

        maker.add(Caption.meta("added later"));

        assertThat(early.slot("items")).hasSize(1);
        assertThat(maker.build().slot("items")).hasSize(2);
        assertThat(maker.build().slot("items")).as("build is not a step").hasSize(2);
    }

    /** A section is a value: two written the same way are the same section. */
    @Test
    void aSectionIsAValue() {
        assertThat(Section.of(Heading.h2("Composition")).add(Md.of("text")).build())
            .isEqualTo(Section.of(Heading.h2("Composition")).add(Md.of("text")).build())
            .isNotEqualTo(Section.of(Heading.h3("Composition")).add(Md.of("text")).build());
        assertThat(new HashSet<>(List.of(
            Section.of(Heading.h2("x")).build(), Section.of(Heading.h2("x")).build()))).hasSize(1);
    }
}
