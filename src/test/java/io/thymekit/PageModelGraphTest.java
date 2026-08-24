/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/**
 * Where the contributions of a page become one block of text.
 *
 * <p>The canvas does it rather than the element, and the reason is not tidiness. An element is a value
 * settled once, so nobody reaches into it afterwards; but a contribution has to become text at some
 * point, and the only place that sees the whole tree is here. Doing it here also leaves exactly one
 * place in the library where a value is printed without escaping, and the text for it is minted by the
 * kit rather than by whoever wrote an element.
 */
class PageModelGraphTest {

    private final Model model = new ConcurrentModel();

    private static Map<String, Object> head(Model model) {
        @SuppressWarnings("unchecked")
        Map<String, Object> head = (Map<String, Object>) model.asMap().get("head");
        return head;
    }

    private static Element<Object> describing(String name, String type) {
        var node = new LinkedHashMap<String, Object>();
        node.put("@type", type);
        return Element.Descriptor.of("test/thing", "thingEl").with("name", name).describes(node).build();
    }

    /** A page where nothing describes itself prints no block at all — as with every absent part. */
    @Test
    void aPageThatSaysNothingCarriesNoBlock() {
        PageModel.of(model).title("Aloe").add(Heading.h1("Aloe")).render();

        assertThat(head(model)).doesNotContainKey("graph");
    }

    /**
     * One contribution becomes one node, and the context is put in front of it by the canvas — so no
     * element has to carry that literal, and two of them cannot come to disagree about it.
     */
    @Test
    void oneContributionBecomesOneNode() {
        PageModel.of(model).title("Aloe").add(describing("one", "BreadcrumbList")).render();

        assertThat(head(model)).containsEntry("graph",
            "{\"@context\":\"https://schema.org\",\"@type\":\"BreadcrumbList\"}");
    }

    /**
     * Several become an array, and each node carries its own context. An array of self-contained nodes
     * is understood everywhere; the wrapper that would let the context be written once is not.
     */
    @Test
    void severalBecomeAnArrayOfSelfContainedNodes() {
        PageModel.of(model).title("Aloe")
            .add(describing("one", "BreadcrumbList"))
            .add(describing("two", "ItemList"))
            .render();

        assertThat(head(model)).containsEntry("graph",
            "[{\"@context\":\"https://schema.org\",\"@type\":\"BreadcrumbList\"},"
                + "{\"@context\":\"https://schema.org\",\"@type\":\"ItemList\"}]");
    }

    /** What an element deep in the page says is said by the page: the walk goes all the way down. */
    @Test
    void aContributionFromInsideTheTreeStillReachesTheHead() {
        Element<Object> boxed = Element.Descriptor.of("test/box", "boxEl")
            .slot("items", List.of(describing("inner", "BreadcrumbList"))).build();

        PageModel.of(model).title("Aloe").add(boxed).render();

        assertThat(head(model)).containsEntry("graph",
            "{\"@context\":\"https://schema.org\",\"@type\":\"BreadcrumbList\"}");
    }

    /**
     * A contribution that already names its context is left with the one it has: the canvas fills a
     * gap, it does not overrule what an element decided.
     */
    @Test
    void doesNotOverruleAContextAnElementChose() {
        var node = new LinkedHashMap<String, Object>();
        node.put("@context", "https://example.org/other");
        node.put("@type", "Thing");
        Element<Object> own = Element.Descriptor.of("test/thing", "thingEl").describes(node).build();

        PageModel.of(model).title("Aloe").add(own).render();

        assertThat(head(model)).containsEntry("graph",
            "{\"@context\":\"https://example.org/other\",\"@type\":\"Thing\"}");
    }
}
