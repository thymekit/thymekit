/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * How the scripts of a page are found: an element declares what it needs, and the canvas collects them
 * from the whole tree.
 *
 * <p>The point is that nobody wires a script by hand. An element that needs behaviour says so next to
 * the data it renders with; a page that happens to contain it — three levels down a slot, twice — gets
 * that script once, in the order the page meets it. A consumer who forgot to add a tag is a bug that
 * cannot happen, which is worth more than the collection itself.
 */
class TreeAssetsTest {

    private final Element<Element.Script> drawer = Element.script("fragments/my/drawer", "drawerJs");
    private final Element<Element.Script> toggle = Element.script("fragments/my/toggle", "toggleJs");

    /** An element declares what it needs beside the data it renders with. */
    @Test
    void anElementDeclaresWhatItNeeds() {
        Element<Element.Raw> card = Element.raw("t", "cardEl").requires(drawer).build();

        assertThat(Tree.assetsOf(List.of(card))).containsExactly(drawer);
        assertThat(Tree.assetsOf(List.of(Element.raw("t", "plainEl").build()))).isEmpty();
        assertThat(Tree.assetsOf(List.of(Heading.h2("Title").build()))).isEmpty();
    }

    /** A container carries what its children need, however deep they sit. */
    @Test
    void aContainerCarriesWhatItsChildrenNeed() {
        Element<Element.Raw> card = Element.raw("t", "cardEl").requires(drawer).build();
        Element<Element.Raw> row = Element.raw("t", "rowEl").slot("items", List.of(card)).build();
        Element<Element.Raw> section = Element.raw("t", "sectionEl").slot("items", List.of(row)).build();

        assertThat(Tree.assetsOf(List.of(section))).containsExactly(drawer);
    }

    /** Each script once, in the order the page meets it — a page renders a tag, not a count. */
    @Test
    void eachScriptOnceInTheOrderThePageMeetsIt() {
        Element<Element.Raw> withDrawer = Element.raw("t", "aEl").requires(drawer).build();
        Element<Element.Raw> withBoth = Element.raw("t", "bEl").requires(toggle, drawer).build();

        Element<Element.Raw> page = Element.raw("t", "pageEl")
            .slot("items", List.of(withDrawer, withBoth, withDrawer))
            .build();

        assertThat(Tree.assetsOf(List.of(page))).containsExactly(drawer, toggle);
    }

    /** The same, over whatever a canvas holds: elements, the descriptors they are made of, and holes. */
    @Test
    void theSameOverWhateverACanvasHolds() {
        Element<Element.Raw> card = Element.raw("t", "cardEl").requires(drawer).build();

        assertThat(Tree.assetsOf(List.of(card))).containsExactly(drawer);
        assertThat(Tree.assetsOf(List.of(card.asMap()))).containsExactly(drawer);
        assertThat(Tree.assetsOf(Arrays.asList(card, null, "not an element", 42)))
            .as("what is not an element carries no scripts, and is not an error either")
            .containsExactly(drawer);
        assertThat(Tree.assetsOf(List.of())).isEmpty();
        assertThat(Tree.assetsOf(List.of(List.of(card)))).as("nested collections too").containsExactly(drawer);
    }

    /** What comes back is a value: a page cannot be given scripts by whoever received the list. */
    @Test
    void whatComesBackIsAValue() {
        Element<Element.Raw> card = Element.raw("t", "cardEl").requires(drawer).build();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> Tree.assetsOf(List.of(card)).add(toggle))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    /**
     * A script is identified by its address, so the same script declared through two different elements
     * is one script — and two scripts that differ only in fragment are two.
     */
    @Test
    void aScriptIsItsAddress() {
        Element<Element.Script> sameAsDrawer = Element.script("fragments/my/drawer", "drawerJs");
        Element<Element.Script> neighbour = Element.script("fragments/my/drawer", "closeJs");

        Element<Element.Raw> page = Element.raw("t", "pageEl").slot("items", List.of(
            Element.raw("t", "aEl").requires(drawer).build(),
            Element.raw("t", "bEl").requires(sameAsDrawer).build(),
            Element.raw("t", "cEl").requires(neighbour).build())).build();

        assertThat(Tree.assetsOf(List.of(page))).containsExactly(drawer, neighbour);
    }
}
