/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Walking the tree of a page.
 *
 * <p>Kept apart from the currency for the reason the outline and the anchors are: a question about a
 * whole page is not a property of an element. What is asked here is the traversal itself — the shapes
 * a tree can be made of, and the visitor's right to refuse to go deeper.
 */
class TreeTest {

    /**
     * A page is a tree of descriptors, and this is how it is walked: elements, the maps they are, and
     * collections of either, at any depth. The visitor answers whether to go deeper, so a check of your
     * own may stop where the page stops being its structure — which is what the kit's own two checks do,
     * one of them stopping at an illustration and the other not.
     */
    @Test
    void theTreeOfAPageIsWalkedFromHere() {
        Element<Element.Raw> deep = Element.raw("t", "aEl").with("title", "deep").build();
        Element<Element.Raw> frame = Element.raw("t", "frameEl").illustration()
            .slot("items", List.of(deep)).build();
        Element<Element.Raw> page = Element.raw("t", "pageEl").slot("items", List.of(frame)).build();

        var seen = new ArrayList<String>();
        Tree.walk(List.of(page), descriptor -> {
            seen.add(String.valueOf(descriptor.get("fragment")));
            return true;
        });
        assertThat(seen).containsExactly("pageEl", "frameEl", "aEl");

        var stopped = new ArrayList<String>();
        Tree.walk(List.of(page), descriptor -> {
            stopped.add(String.valueOf(descriptor.get("fragment")));
            return !Element.isIllustration(descriptor);
        });
        assertThat(stopped).as("what the visitor refuses to enter stays unvisited")
            .containsExactly("pageEl", "frameEl");

        assertThatCode(() -> Tree.walk(java.util.Arrays.asList(page, null, "not a descriptor", 42),
            descriptor -> true)).as("a hole inside a page is what the page carries, not a mistake at this call")
            .doesNotThrowAnyException();
    }

    /**
     * What was handed to the walk is another matter, and this expectation used to say the opposite: a
     * null page walked to nothing and said nothing. A check written over it then checks nothing and
     * passes, which is the exact failure this class exists to warn about, one line into its own comment.
     * A hole <i>inside</i> a page stays what it was — the page's business — and the case above keeps it.
     */
    @Test
    void whatIsHandedToTheWalkIsRequired() {
        assertThatThrownBy(() -> Tree.walk(null, descriptor -> true))
            .isInstanceOf(MisuseException.class).hasMessage("Tree.walk(node): was not given");
        assertThatThrownBy(() -> Tree.walk(List.of(), null))
            .isInstanceOf(MisuseException.class).hasMessage("Tree.walk(visit): was not given");
        assertThatThrownBy(() -> Tree.assetsOf(null))
            .isInstanceOf(MisuseException.class).hasMessage("Tree.assetsOf(roots): was not given");
        assertThatThrownBy(() -> Tree.describedBy(null))
            .isInstanceOf(MisuseException.class).hasMessage("Tree.describedBy(roots): was not given");
    }

    private static Element<Object> describing(String name, Map<String, ?> node) {
        return Element.Descriptor.of("test/thing", "thingEl").with("name", name).describes(node).build();
    }

    private static Map<String, Object> node(String type) {
        var n = new LinkedHashMap<String, Object>();
        n.put("@type", type);
        return n;
    }

    // how the page finds them

    /** A tree with nothing to say produces nothing to print. */
    @Test
    void findsNothingWhereNothingWasSaid() {
        assertThat(Tree.describedBy(List.of(Heading.h2("plain").build()))).isEmpty();
    }

    /** The contributions of a flow come back in the order the page carries them. */
    @Test
    void findsThemInTheOrderOfTheTree() {
        var first = describing("one", node("First"));
        var second = describing("two", node("Second"));

        assertThat(Tree.describedBy(List.of(first, second)))
            .containsExactly(Map.of("@type", "First"), Map.of("@type", "Second"));
    }

    /**
     * And it goes as deep as the page does: an element inside a slot of another element is still on the
     * page, so what it says about itself is still said.
     */
    @Test
    void findsThemInsideSlots() {
        Element<Object> inner = describing("inner", node("Inner"));
        Element<Object> outer = Element.Descriptor.of("test/box", "boxEl")
            .slot("items", List.of(inner)).build();

        assertThat(Tree.describedBy(List.of(outer))).containsExactly(Map.of("@type", "Inner"));
    }

    /**
     * Two elements saying the same thing say it once. A page that carries the same trail twice does not
     * describe it twice, and a reader of the graph has no way to tell which copy was meant.
     */
    @Test
    void saysTheSameThingOnlyOnce() {
        var twice = List.of(describing("one", node("Same")), describing("two", node("Same")));

        assertThat(Tree.describedBy(twice)).containsExactly(Map.of("@type", "Same"));
    }
}
