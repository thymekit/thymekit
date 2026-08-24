/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Owner of the heading concept: anything with a heading composes this instead of writing its own
 * {@code <h*>}.
 *
 * <p>A heading is the one element whose data other things are built from. The outline of a page is made
 * of levels; a section takes its accessible name from an anchor; a table of contents links to that same
 * anchor. So it owes more than markup: what it carries has to be true, and what it refuses has to be
 * refused where it was written rather than found on the page.
 *
 * <p>What the adapter prints from the descriptor belongs to the walk over triples, as everywhere.
 */
class HeadingTest {

    /** The level is the factory you called; there is no default, because a level decides a place. */
    @Test
    void theLevelLivesInTheFactoryName() {
        assertThat(Heading.h1("Baobab").build().asMap())
            .containsEntry("level", 1).containsEntry("text", "Baobab")
            .containsEntry("template", "thymekit/heading").containsEntry("fragment", "headingEl");
        assertThat(List.of(Heading.h2("x"), Heading.h3("x"), Heading.h4("x"), Heading.h5("x"), Heading.h6("x")))
            .extracting(maker -> maker.build().asMap().get("level"))
            .containsExactly(2, 3, 4, 5, 6);
        assertThat(Heading.h2("x").build().asMap())
            .as("nothing is carried that was not asked for")
            .doesNotContainKey("id").doesNotContainKey("href").doesNotContainKey("srOnly")
            .doesNotContainKey("lang").doesNotContainKey("rel").doesNotContainKey("target");
    }

    /**
     * A heading with nothing to say is worse than no heading: it takes a place in the outline, a screen
     * reader walking headings stops at it, and there is nothing there.
     */
    @Test
    void aHeadingWithNothingToSayIsRefused() {
        for (String nothing : List.of("", " ", "\t\n  ")) {
            assertThatThrownBy(() -> Heading.h2(nothing))
                .isInstanceOf(MisuseException.class)
                    .hasMessage("Heading(text): is blank — a page shows what it was given, and this is nothing");
        }
        assertThatThrownBy(() -> Heading.h1(null))
            .isInstanceOf(MisuseException.class).hasMessage("Heading(text): was not given");
        assertThat(Heading.h2("  Baobab  ").build().asMap())
            .as("what is written is kept as written").containsEntry("text", "  Baobab  ");
    }

    /**
     * An anchor is an address inside the page: a section is named by it, a link lands on it. An address
     * with a space in it is no address at all — the attribute takes the first word, and the rest becomes
     * something nobody meant.
     */
    @Test
    void anAnchorIsAnAddressAndNotASentence() {
        assertThat(Heading.h2("Composition").id("composition").build().asMap())
            .containsEntry("id", "composition");
        assertThat(Heading.h2("x").id("section-2.1_a").build().asMap()).containsEntry("id", "section-2.1_a");

        for (String blank : List.of("", "  ")) {
            assertThatThrownBy(() -> Heading.h2("x").id(blank)).isInstanceOf(MisuseException.class)
                .hasMessage("Heading.id(id): is blank — a page shows what it was given, and this is nothing");
        }
        for (String twoWords : List.of("two words", "with\ttab", "line\nbreak")) {
            assertThatThrownBy(() -> Heading.h2("x").id(twoWords)).isInstanceOf(MisuseException.class)
                .hasMessageStartingWith("Heading.id(id): is not one word:")
                .hasMessageContaining("keeps only what comes before the first space");
        }
        assertThatThrownBy(() -> Heading.h2("x").id(null))
            .isInstanceOf(MisuseException.class).hasMessage("Heading.id(id): was not given");
    }

    /**
     * A heading may be a link, and an address that executes instead of navigating is refused where it is
     * written — however it is spelled, since a browser drops spaces, tabs and control characters before
     * it reads a scheme.
     */
    @Test
    void aHeadingMayBeALinkButNotAScript() {
        assertThat(Heading.h3("Source").href("  https://example.com/x  ").build().asMap())
            .as("kept trimmed").containsEntry("href", "https://example.com/x");
        assertThat(Heading.h3("Ours").href("/ingredients/baobab").build().asMap())
            .containsEntry("href", "/ingredients/baobab");

        for (String script : List.of("javascript:alert(1)", "JavaScript:alert(1)", " data:text/html,x",
                "vbscript:x", "java\tscript:alert(1)", "java\nscript:alert(1)", "jav ascript:alert(1)")) {
            assertThatThrownBy(() -> Heading.h2("x").href(script))
                .isInstanceOf(MisuseException.class).hasMessageContaining("not a link but a script");
        }
        assertThatThrownBy(() -> Heading.h2("x").href("  "))
            .isInstanceOf(MisuseException.class).hasMessageContaining("blank");
        assertThatThrownBy(() -> Heading.h2("x").href(null))
            .isInstanceOf(MisuseException.class).hasMessage("Heading.href(href): was not given");
    }

    /**
     * What the link says about itself is assembled at build, so the order of the calls cannot change it:
     * a new tab brings {@code noopener} whether the tab was asked for before the values or after them.
     */
    @Test
    void whatALinkSaysIsAssembledAtTheEnd() {
        assertThat(Heading.h2("x").href("https://o/x").rel(Rel.NOFOLLOW, Rel.UGC, Rel.NOFOLLOW).build().asMap())
            .as("repetitions collapse, order is kept").containsEntry("rel", "nofollow ugc");
        assertThat(Heading.h2("x").href("https://o/x").rel(Rel.NOFOLLOW).rel(Rel.UGC).build().asMap())
            .as("two calls accumulate, they do not replace").containsEntry("rel", "nofollow ugc");
        assertThat(Heading.h2("x").href("https://o/x").newTab().rel(Rel.NOFOLLOW).build().asMap())
            .containsEntry("rel", "nofollow noopener").containsEntry("target", "_blank");
        assertThat(Heading.h2("x").href("https://o/x").rel(Rel.NOFOLLOW).newTab().build().asMap())
            .as("and the other order says the same thing").containsEntry("rel", "nofollow noopener");

        var reused = Heading.h2("x").href("https://o/x").rel(Rel.UGC).newTab();
        assertThat(reused.build().asMap()).containsEntry("rel", "ugc noopener");
        assertThat(reused.build().asMap()).as("build is not a step").containsEntry("rel", "ugc noopener");
    }

    /** An attribute with no {@code <a>} to sit on is printed nowhere, so it is refused rather than lost. */
    @Test
    void whatOnlyALinkCanSayIsRefusedWithoutOne() {
        assertThatThrownBy(() -> Heading.h2("x").rel(Rel.UGC).build())
            .isInstanceOf(MisuseException.class).hasMessageContaining("not a link");
        assertThatThrownBy(() -> Heading.h2("x").newTab().build())
            .isInstanceOf(MisuseException.class).hasMessageContaining("not a link");
        assertThatThrownBy(() -> Heading.h2("x").rel())
            .isInstanceOf(MisuseException.class).hasMessageContaining("name at least one");
    }

    /** The language of the text when it is not the page's, and a heading only a screen reader meets. */
    @Test
    void aHeadingMayNameItsLanguageAndMayBeForScreenReadersOnly() {
        assertThat(Heading.h3("Adansonia digitata").lang("la").build().asMap()).containsEntry("lang", "la");
        assertThatThrownBy(() -> Heading.h2("x").lang("по-русски"))
            .isInstanceOf(MisuseException.class).hasMessageContaining("language tag");

        assertThat(Heading.h2("Navigation").srOnly().build().asMap()).containsEntry("srOnly", true);
    }

    /**
     * And what a heading is, read back out of a descriptor: it says which of its keys is the level,
     * which is the anchor and which holds the words — so the checks a page gets find them without
     * asking whose adapter this is, and a check of yours walking a page asks the same three readers.
     */
    @Test
    void aHeadingSaysWhatItsKeysAre() {
        Map<String, Object> heading = Heading.h2("Composition").id("composition").build().asMap();

        assertThat(Roles.headingLevelIn(heading)).isEqualTo(2);
        assertThat(Roles.anchorIn(heading)).isEqualTo("composition");
        assertThat(Roles.nameOf(heading)).isEqualTo("Composition");

        Map<String, Object> caption = Caption.meta("12 entries").build().asMap();
        assertThat(Roles.headingLevelIn(caption)).as("something else is not a heading").isNull();
        assertThat(Roles.anchorIn(caption)).isNull();

        assertThat(Roles.anchorIn(Heading.h2("Nameless").build().asMap()))
            .as("a heading with no address has no anchor").isNull();
    }

    /** A heading is a value: two written the same way are the same heading. */
    @Test
    void aHeadingIsAValue() {
        assertThat(Heading.h2("Composition").build())
            .isEqualTo(Heading.h2("Composition").build())
            .isNotEqualTo(Heading.h3("Composition").build());
        assertThat(new HashSet<>(List.of(Heading.h2("x").build(), Heading.h2("x").build()))).hasSize(1);
    }
}
