/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Owner of the caption concept: short text attached to something.
 *
 * <p>Two things make it worth an element of its own rather than a paragraph somebody writes. It stays
 * out of the outline — a line above a title is not a heading, however much it looks like one — and it
 * carries what a machine needs beside what a person reads: the day as a date, the language of a phrase
 * that is not the page's.
 *
 * <p>What is checked here is the descriptor a caption produces. That its adapter renders the descriptor
 * as {@code <p class="tk-caption tk-caption--role">} is the triple's business and belongs to the walk
 * over triples; the two are checked apart on purpose, since a caption is composed long before anything
 * is rendered.
 */
class CaptionTest {

    /** The role is the factory you called, and there is no default: a caption without one is not a thing. */
    @Test
    void theRoleLivesInTheFactoryName() {
        assertThat(Caption.eyebrow("Catalogue").build().asMap())
            .containsEntry("role", Caption.EYEBROW).containsEntry("text", "Catalogue")
            .containsEntry("template", "thymekit/caption").containsEntry("fragment", "captionEl");
        assertThat(Caption.subtitle("RA-101").build().asMap()).containsEntry("role", Caption.SUBTITLE);
        assertThat(Caption.label("Composition").build().asMap()).containsEntry("role", Caption.LABEL);
        assertThat(Caption.meta("12 entries").build().asMap()).containsEntry("role", Caption.META);

        assertThat(List.of(Caption.EYEBROW, Caption.SUBTITLE, Caption.LABEL, Caption.META))
            .as("four roles, and the names are the kit's own vocabulary")
            .containsExactly("eyebrow", "subtitle", "label", "meta");
    }

    /**
     * A caption with nothing to say is an empty box on a page: a paragraph a reader meets and gets
     * nothing from, and a line a screen reader announces as silence. Refused where it is written.
     */
    @Test
    void aCaptionWithNothingToSayIsRefused() {
        for (String nothing : List.of("", " ", "\t\n  ")) {
            assertThatThrownBy(() -> Caption.meta(nothing))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("text");
        }
        assertThatThrownBy(() -> Caption.label(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("text");
        assertThatThrownBy(() -> Caption.subtitle(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Caption.eyebrow(null)).isInstanceOf(NullPointerException.class);
    }

    /**
     * And what is written is kept as written. A caption is content, not a document property: the canvas
     * trims a title, because a space at the end of one travels into a browser tab and a search result,
     * but a space an author put inside a line of text is theirs. Blank is refused; anything else is
     * carried exactly.
     */
    @Test
    void whatIsWrittenIsKeptAsWritten() {
        assertThat(Caption.meta("  12 entries  ").build().asMap()).containsEntry("text", "  12 entries  ");
        assertThat(Caption.label("a\u00a0non-breaking space").build().asMap())
            .containsEntry("text", "a\u00a0non-breaking space");
    }

    /**
     * The day written for machines beside the words written for people. The wording, the language and
     * the format of the text stay the author's; the attribute is what a search engine and a screen
     * reader understand — and it takes a date rather than a string, so "yesterday" cannot get in.
     */
    @Test
    void aDayIsWrittenForMachinesBesideTheWordsForPeople() {
        assertThat(Caption.meta("12 March 2026").time(LocalDate.of(2026, 3, 12)).build().asMap())
            .containsEntry("datetime", "2026-03-12").containsEntry("text", "12 March 2026");
        assertThatThrownBy(() -> Caption.meta("x").time((LocalDate) null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("day");
    }

    /** A moment is kept as precisely as it was given, and the last thing said about the time wins. */
    @Test
    void aMomentIsKeptAsPreciselyAsItWasGiven() {
        assertThat(Caption.meta("noon, sharp").time(Instant.parse("2026-03-12T12:00:00Z")).build().asMap())
            .containsEntry("datetime", "2026-03-12T12:00:00Z");
        assertThat(Caption.meta("x").time(Instant.parse("2026-03-12T12:00:00.123456Z")).build().asMap())
            .containsEntry("datetime", "2026-03-12T12:00:00.123456Z");
        assertThat(Caption.meta("x").time(LocalDate.of(2026, 3, 12))
                .time(Instant.parse("2026-03-12T12:00:00Z")).build().asMap())
            .containsEntry("datetime", "2026-03-12T12:00:00Z");
        assertThatThrownBy(() -> Caption.meta("x").time((Instant) null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("moment");
    }

    /** A phrase in another language says which one, or a screen reader reads it as broken page language. */
    @Test
    void aPhraseInAnotherLanguageSaysWhichOne() {
        assertThat(Caption.subtitle("Adansonia digitata").lang("la").build().asMap()).containsEntry("lang", "la");
        assertThat(Caption.subtitle("São Paulo").lang("pt-BR").build().asMap()).containsEntry("lang", "pt-BR");
        assertThat(Caption.meta("plain").build().asMap()).as("nothing said, nothing carried")
            .doesNotContainKey("lang").doesNotContainKey("datetime");

        for (String notATag : List.of("", " ", "по-русски", "la la", "la_LA")) {
            assertThatThrownBy(() -> Caption.subtitle("x").lang(notATag))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("language tag");
        }
        assertThatThrownBy(() -> Caption.subtitle("x").lang(null)).isInstanceOf(NullPointerException.class);
    }

    /** A caption is a value: two written the same way are the same caption. */
    @Test
    void aCaptionIsAValue() {
        assertThat(Caption.meta("12 entries").build())
            .isEqualTo(Caption.meta("12 entries").build())
            .isNotEqualTo(Caption.label("12 entries").build());
        assertThat(new HashSet<>(List.of(Caption.meta("x").build(), Caption.meta("x").build()))).hasSize(1);
    }

    /**
     * The guard a host uses. A hero wants an eyebrow above its title and a subtitle below it, and Java
     * says {@code Element<?>} at both points because the marker is erased there — so the role is checked
     * at run time, and the check says what it wanted and what it got.
     *
     * <p>Public, because a host of yours has the same problem the kit's hero has. Handing out the role
     * as a string to compare by hand would be handing out the weaker half of the instrument.
     */
    @Test
    void theGuardAHostUsesIsHandedOut() {
        assertThat(Caption.inRole(Caption.eyebrow("Catalogue"), Caption.EYEBROW, "Hero.eyebrow accepts a caption")
            .asMap()).as("a maker is settled by the guard").containsEntry("text", "Catalogue");

        Element<Caption> settled = Caption.subtitle("RA-101").build();
        assertThat(Caption.inRole(settled, Caption.SUBTITLE, "Hero.subtitle accepts a caption")).isSameAs(settled);

        assertThatThrownBy(() -> Caption.inRole(Caption.meta("x"), Caption.EYEBROW, "Hero.eyebrow accepts a caption"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Hero.eyebrow").hasMessageContaining("eyebrow").hasMessageContaining("meta");

        @SuppressWarnings("unchecked")
        Element<Caption> notACaption = (Element<Caption>) (Element<?>) Element.raw("t", "myCardEl").build();
        assertThatThrownBy(() -> Caption.inRole(notACaption, Caption.LABEL, "Frame.label accepts a caption"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("myCardEl");

        assertThatThrownBy(() -> Caption.inRole(null, Caption.LABEL, "Frame.label"))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("caption");
        assertThatThrownBy(() -> Caption.inRole(() -> null, Caption.LABEL, "Frame.label"))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("built nothing");

        assertThat(java.util.Arrays.stream(Caption.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("inRole"))
                .allMatch(m -> Modifier.isPublic(m.getModifiers())))
            .as("the guard a host needs is reachable by a host of yours").isTrue();
    }
}
