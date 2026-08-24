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
 * What a key of an element <b>is</b>, and why anything of the sort exists.
 *
 * <p>The two checks a page gets used to ask a reader that answered for one adapter and refused every
 * other, so a heading of somebody else's was not a heading as far as the page was concerned: their
 * page could carry two titles and pass where the kit's own could not. A role is how that closes — the
 * checks ask what a key is, an element says so, and no address is special to anybody.
 */
class ElementRolesTest {

    /** The kit's own heading says it, which is the whole of its former privilege. */
    @Test
    void theKitsOwnHeadingSaysWhatItsKeysAre() {
        Map<String, Object> h2 = Heading.h2("Composition").id("composition").build().asMap();

        assertThat(Element.headingLevelIn(h2)).isEqualTo(2);
        assertThat(Element.anchorIn(h2)).isEqualTo("composition");
        assertThat(Element.roleIn(h2, Element.Role.HEADING_LEVEL)).isEqualTo(2);
    }

    /** A heading without an address carries no anchor, and says nothing rather than saying null. */
    @Test
    void whatWasNotSaidIsNotThere() {
        Map<String, Object> plain = Heading.h2("Composition").build().asMap();

        assertThat(Element.anchorIn(plain)).isNull();
        assertThat(Element.roleIn(plain, Element.Role.ANCHOR)).isNull();
        assertThat(Element.roleIn(Map.of("fragment", "cardEl"), Element.Role.ANCHOR))
            .as("a descriptor that never spoke of roles at all").isNull();
    }

    /** An element of somebody else's says it the same way, and is read the same way. */
    @Test
    void anElementOfSomebodyElsesSaysItTheSameWay() {
        Map<String, Object> chapter = Element.raw("fragments/my/chapter", "chapterEl")
            .with("depth", 3).with("slug", "in-the-south")
            .means("depth", Element.Role.HEADING_LEVEL)
            .means("slug", Element.Role.ANCHOR)
            .build().asMap();

        assertThat(Element.headingLevelIn(chapter)).as("their key is not called level").isEqualTo(3);
        assertThat(Element.anchorIn(chapter)).as("nor is it called id").isEqualTo("in-the-south");
    }

    /**
     * A level counts however it was written, because a descriptor may arrive as data — read out of a
     * column, parsed from json — where a number is easily a string.
     */
    @Test
    void aLevelCountsHoweverItWasWritten() {
        assertThat(Element.headingLevelIn(chapterWithLevel(2))).isEqualTo(2);
        assertThat(Element.headingLevelIn(chapterWithLevel("2"))).isEqualTo(2);
        assertThat(Element.headingLevelIn(chapterWithLevel(" 2 "))).as("space around it").isEqualTo(2);
        assertThat(Element.headingLevelIn(chapterWithLevel(2L))).as("a long is a number too").isEqualTo(2);
        assertThat(Element.headingLevelIn(chapterWithLevel("two"))).as("prose is not a level").isNull();
        assertThat(Element.headingLevelIn(chapterWithLevel(true))).as("nor is a flag").isNull();
    }

    /** An anchor is text, and a key that says it is one while carrying something else says nothing. */
    @Test
    void anAnchorIsText() {
        Map<String, Object> numbered = Element.raw("fragments/my/chapter", "chapterEl")
            .with("slug", 12).means("slug", Element.Role.ANCHOR).build().asMap();

        assertThat(Element.anchorIn(numbered)).isNull();
    }

    /**
     * One role, one key. Two keys claiming to be the anchor of one element is a question with two
     * answers, and a check would have to pick one — which is a check inventing a rule nobody agreed to.
     */
    @Test
    void oneRoleBelongsToOneKey() {
        assertThatThrownBy(() -> Element.raw("t", "chapterEl")
                .with("id", "a").with("slug", "b")
                .means("id", Element.Role.ANCHOR)
                .means("slug", Element.Role.ANCHOR))
            .isInstanceOf(MisuseException.class)
            .hasMessage("Descriptor.means(role): this element already says that \"id\" is its ANCHOR:"
                + " one role, one key");

        assertThatCode(() -> Element.raw("t", "chapterEl").with("id", "a")
                .means("id", Element.Role.ANCHOR)
                .means("id", Element.Role.ANCHOR))
            .as("saying the same thing twice says the same thing").doesNotThrowAnyException();
    }

    /**
     * A role given to a key that was never given a value is a promise the element does not keep, and it
     * is refused where the element is built rather than where a page fails to find anything.
     */
    @Test
    void aRoleWithoutAValueIsRefused() {
        assertThatThrownBy(() -> Element.raw("t", "chapterEl")
                .means("depth", Element.Role.HEADING_LEVEL).build())
            .isInstanceOf(MisuseException.class)
            .hasMessage("Descriptor.means(key): \"depth\" was given a role and never given a value");

        assertThatCode(() -> Element.raw("t", "chapterEl")
                .means("depth", Element.Role.HEADING_LEVEL).with("depth", 2).build())
            .as("the two calls may be written in either order").doesNotThrowAnyException();
    }

    /** What was said is data like the rest of it, and a built element does not change afterwards. */
    @Test
    void whatWasSaidIsPartOfTheValue() {
        Element<Element.Raw> chapter = Element.raw("t", "chapterEl")
            .with("depth", 2).means("depth", Element.Role.HEADING_LEVEL).build();

        assertThat(chapter.asMap()).containsEntry("means", Map.of("depth", "HEADING_LEVEL"));
        assertThat(chapter).as("two elements that say the same are the same")
            .isEqualTo(Element.raw("t", "chapterEl").with("depth", 2)
                .means("depth", Element.Role.HEADING_LEVEL).build());
        assertThatThrownBy(() -> ((Map<String, String>) chapter.asMap().get("means")).put("x", "ANCHOR"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    /** The word is reserved: data cannot be called what the kit calls its own. */
    @Test
    void theWordIsReserved() {
        assertThatThrownBy(() -> Element.raw("t", "chapterEl").with("means", "something"))
            .isInstanceOf(MisuseException.class).hasMessageContaining("reserved");
    }

    /** And the readers refuse to be asked about nothing. */
    @Test
    void theReadersAreAskedAboutSomething() {
        assertThatThrownBy(() -> Element.roleIn(null, Element.Role.ANCHOR))
            .isInstanceOf(MisuseException.class).hasMessage("Element.roleIn(descriptor): was not given");
        assertThatThrownBy(() -> Element.roleIn(Map.of(), null))
            .isInstanceOf(MisuseException.class).hasMessage("Element.roleIn(role): was not given");
    }

    private static Map<String, Object> chapterWithLevel(Object level) {
        return Element.raw("fragments/my/chapter", "chapterEl")
            .with("depth", level).means("depth", Element.Role.HEADING_LEVEL).build().asMap();
    }
}
