/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What an element says about itself for machines, and how the page finds it.
 *
 * <p>What a page does with the contributions it finds is asked of the walk that finds them; here the
 * question is only what a descriptor accepts and keeps.
 *
 * <p>The contribution is <b>data</b>, never finished text: a descriptor that carried a ready-made
 * script would be a seam through which markup reaches a page, and the whole argument for storing a
 * page as data rests on there being no such seam. Turning it into text is somebody else's job, and it
 * happens once, where the whole tree is visible.
 */
class ElementDescribesTest {

    private static Element<Object> describing(String name, Map<String, ?> node) {
        return Element.Descriptor.of("test/thing", "thingEl").with("name", name).describes(node).build();
    }

    private static Map<String, Object> node(String type) {
        var n = new LinkedHashMap<String, Object>();
        n.put("@type", type);
        return n;
    }

    // what the descriptor does with a contribution

    /** It is kept, under a key of the descriptor's own, and it is kept as the map it was. */
    @Test
    void keepsTheContribution() {
        Element<Object> element = describing("one", node("BreadcrumbList"));

        assertThat(element.asMap()).containsEntry("describes", Map.of("@type", "BreadcrumbList"));
    }

    /**
     * And it is copied on the way in, however deep. An element is its descriptor — that is what makes
     * two of them equal — so a map the caller still holds must not be able to change an element that
     * was already built.
     */
    @Test
    void copiesTheContributionSoTheCallerCannotChangeItLater() {
        var items = new ArrayList<Object>();
        items.add(Map.of("position", 1));
        var mine = node("BreadcrumbList");
        mine.put("itemListElement", items);

        Element<Object> element = describing("one", mine);
        items.add(Map.of("position", 2));
        mine.put("@type", "SomethingElse");

        assertThat(element).isEqualTo(describing("one", nodeWithOneItem()));
    }

    private static Map<String, Object> nodeWithOneItem() {
        var n = node("BreadcrumbList");
        n.put("itemListElement", List.of(Map.of("position", 1)));
        return n;
    }

    /**
     * The key belongs to the descriptor, so an element cannot set it as data. Without this the guards
     * below could be walked around by writing the same key the ordinary way.
     */
    @Test
    void theKeyIsReserved() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Element.Descriptor.of("test/thing", "thingEl").with("describes", "{}"))
            .withMessageContaining("reserved");
    }

    // the guards

    /**
     * A contribution carrying the key that marks a descriptor would be walked as if it were an element:
     * the walk recognises one by exactly that key, and would then look for an adapter that does not
     * exist. Refused where it is written.
     */
    @Test
    void refusesAContributionThatLooksLikeADescriptor() {
        var pretender = node("Thing");
        pretender.put("fragment", "thingEl");

        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> describing("one", pretender))
            .withMessageContaining("fragment");
    }

    /** The other half of the same address, for the same reason. */
    @Test
    void refusesAContributionCarryingATemplate() {
        var pretender = node("Thing");
        pretender.put("template", "test/thing");

        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> describing("one", pretender))
            .withMessageContaining("template");
    }

    /**
     * At any depth, not only at the top: the walk descends into every map it meets, so a node hidden
     * inside a list of nodes would be taken for an element just the same.
     */
    @Test
    void refusesADescriptorKeyHiddenDeepInside() {
        var deep = node("BreadcrumbList");
        deep.put("itemListElement", List.of(Map.of("fragment", "thingEl")));

        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> describing("one", deep))
            .withMessageContaining("fragment");
    }

    /** And under a key as well as inside a list: a node can be nested either way. */
    @Test
    void refusesADescriptorKeyNestedUnderAKey() {
        var deep = node("BreadcrumbList");
        deep.put("about", Map.of("template", "test/thing"));

        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> describing("one", deep))
            .withMessageContaining("template");
    }

    /**
     * A value the kit cannot write is refused here, in the factory that wrote it, rather than later
     * when a page is rendered. The stack then points at whoever put it there, and the place names the
     * path that reaches it — a page rendering several contributions could not have said whose it was.
     */
    @Test
    void refusesAValueItCouldNotWrite() {
        var withADate = node("BreadcrumbList");
        withADate.put("itemListElement", List.of(Map.of("published", java.time.LocalDate.EPOCH)));

        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> describing("one", withADate))
            .satisfies(refusal -> {
                assertThat(refusal.where()).isEqualTo("Descriptor.describes.itemListElement[0].published");
                assertThat(refusal.getMessage()).contains("LocalDate");
            });
    }

    /** An empty contribution says nothing and would print an empty node into the page. */
    @Test
    void refusesAnEmptyContribution() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> describing("one", Map.of()))
            .withMessageContaining("empty");
    }

    /** Absence is not a contribution either. */
    @Test
    void refusesNothing() {
        assertThatExceptionOfType(MisuseException.class).isThrownBy(() -> describing("one", null));
    }

    /**
     * One element describes itself once. A second call is a mistake either way — accumulating would
     * guess, replacing would lose the first quietly — so it is refused instead.
     */
    @Test
    void refusesASecondContribution() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Element.Descriptor.of("test/thing", "thingEl")
                .describes(node("One")).describes(node("Two")))
            .withMessageContaining("already");
    }
}
