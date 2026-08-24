/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * An element says what a key of it is, and the checks a page gets ask that.
 *
 * <p>They used to ask a reader that answered for the kit's own heading and refused every other
 * adapter, so a heading of somebody else's was not a heading as far as a page was concerned: their
 * page could carry two titles where ours could not. Three verbs close that — and the shape of them is
 * the argument. The key is named once, in the call that puts the value, so a role for a key that was
 * never given a value cannot be written; and the value's type is the signature's, so a role that
 * cannot be met cannot be claimed.
 */
class RolesTest {

    /** Said and put in one call, which is what makes the mistakes unwritable rather than checked. */
    @Test
    void aRoleIsSaidInTheCallThatPutsTheValue() {
        Map<String, Object> chapter = Element.raw("fragments/my/chapter", "chapterEl")
            .headingLevel("depth", 3)
            .anchor("slug", "in-the-south")
            .name("title", "In the south")
            .with("body", "…")
            .build().asMap();

        assertThat(chapter).containsEntry("depth", 3).containsEntry("slug", "in-the-south");
        assertThat(Roles.headingLevelIn(chapter)).as("their key is not called level").isEqualTo(3);
        assertThat(Roles.anchorIn(chapter)).as("nor is it called id").isEqualTo("in-the-south");
        assertThat(Roles.nameOf(chapter)).isEqualTo("In the south");
    }

    /** The kit's own heading says the same three things, and holds no other privilege. */
    @Test
    void theKitsOwnHeadingSaysTheSameThings() {
        Map<String, Object> h2 = Heading.h2("Composition").id("composition").build().asMap();

        assertThat(Roles.headingLevelIn(h2)).isEqualTo(2);
        assertThat(Roles.anchorIn(h2)).isEqualTo("composition");
        assertThat(Roles.nameOf(h2)).isEqualTo("Composition");
    }

    /** What was never said is not there, and a descriptor that never spoke of roles answers nothing. */
    @Test
    void whatWasNotSaidIsNotThere() {
        Map<String, Object> card = Element.raw("fragments/my/card", "cardEl")
            .with("id", 4210).with("level", 2).build().asMap();

        assertThat(Roles.anchorIn(card)).as("an id of a product is a number, not an address").isNull();
        assertThat(Roles.headingLevelIn(card)).as("a key called level was never said to be one").isNull();
        assertThat(Roles.nameOf(Map.of("fragment", "cardEl")))
            .as("what says nothing is called by its address").isEqualTo("cardEl");
    }

    /**
     * A level is taken as it is given and judged with the page, not at the call. Whether six or nine is
     * a question about html, and the page check has to ask it anyway for a page that arrived as stored
     * data — asking it twice would leave that judgement with nothing able to reach it.
     */
    @Test
    void aLevelIsTakenAsItIsGivenAndJudgedWithThePage() {
        for (int any : java.util.List.of(1, 6, 7, 0)) {
            assertThatCode(() -> Element.raw("t", "chapterEl").headingLevel("depth", any))
                .doesNotThrowAnyException();
        }
        assertThat(Roles.headingLevelIn(
            Element.raw("t", "chapterEl").headingLevel("depth", 9).build().asMap())).isEqualTo(9);
    }

    /** An anchor is one word, wherever it is declared: an attribute keeps only the first. */
    @Test
    void anAnchorIsOneWord() {
        for (String notOne : java.util.List.of("two words", "with\ttab", "line\nbreak")) {
            assertThatThrownBy(() -> Element.raw("t", "chapterEl").anchor("slug", notOne))
                .isInstanceOf(MisuseException.class)
                .hasMessageStartingWith("Descriptor.anchor(value): is not one word:");
        }
        assertThatThrownBy(() -> Element.raw("t", "chapterEl").anchor("slug", " "))
            .isInstanceOf(MisuseException.class)
            .hasMessage("Descriptor.anchor(value): is blank — a page shows what it was given,"
                + " and this is nothing");
        assertThatThrownBy(() -> Element.raw("t", "chapterEl").anchor("slug", null))
            .isInstanceOf(MisuseException.class).hasMessage("Descriptor.anchor(value): was not given");
    }

    /** The words an element is called by are words: blank is nothing to call it. */
    @Test
    void aNameIsSomethingToCallIt() {
        assertThatThrownBy(() -> Element.raw("t", "chapterEl").name("title", "  "))
            .isInstanceOf(MisuseException.class).hasMessageStartingWith("Descriptor.name(value): is blank");
        assertThatThrownBy(() -> Element.raw("t", "chapterEl").name(null, "x"))
            .isInstanceOf(MisuseException.class).hasMessage("Descriptor.name(key): was not given");
        assertThatThrownBy(() -> Element.raw("t", "chapterEl").headingLevel(null, 2))
            .isInstanceOf(MisuseException.class).hasMessage("Descriptor.headingLevel(key): was not given");
    }

    /**
     * One role, one key. Two keys claiming to be the anchor is a question with two answers, and a
     * check would have to pick one — which is a check inventing a rule nobody agreed to.
     */
    @Test
    void oneRoleBelongsToOneKey() {
        assertThatThrownBy(() -> Element.raw("t", "chapterEl").anchor("id", "a").anchor("slug", "b"))
            .isInstanceOf(MisuseException.class)
            .hasMessage("Descriptor.anchor: this element already says that \"id\" is what it carries"
                + " there: one role, one key");

        assertThatCode(() -> Element.raw("t", "chapterEl").anchor("id", "a").anchor("id", "b"))
            .as("the same key said twice is the same thing said twice").doesNotThrowAnyException();
        assertThat(Roles.anchorIn(Element.raw("t", "chapterEl").anchor("id", "a").anchor("id", "b")
            .build().asMap())).as("and the last value written is the one it carries").isEqualTo("b");
    }

    /** What was said travels with the element, and a built one does not change afterwards. */
    @Test
    void whatWasSaidIsPartOfTheValue() {
        Element<Element.Raw> chapter = Element.raw("t", "chapterEl").headingLevel("depth", 2).build();

        assertThat(chapter.asMap()).containsEntry("roles", Map.of("HEADING_LEVEL", "depth"));
        assertThat(chapter).as("two elements that say the same are the same")
            .isEqualTo(Element.raw("t", "chapterEl").headingLevel("depth", 2).build());
        @SuppressWarnings("unchecked")
        Map<String, String> roles = (Map<String, String>) chapter.asMap().get("roles");
        assertThatThrownBy(() -> roles.put("ANCHOR", "x")).isInstanceOf(UnsupportedOperationException.class);
    }

    /** The word is reserved: data cannot be called what the descriptor calls its own. */
    @Test
    void theWordIsReserved() {
        assertThatThrownBy(() -> Element.raw("t", "chapterEl").with("roles", "something"))
            .isInstanceOf(MisuseException.class).hasMessageContaining("reserved");
    }

    /** And a reader is asked about something. */
    @Test
    void aReaderIsAskedAboutSomething() {
        assertThatThrownBy(() -> Roles.anchorIn(null))
            .isInstanceOf(MisuseException.class).hasMessage("Roles.roleIn(descriptor): was not given");
    }
}
