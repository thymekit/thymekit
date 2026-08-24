/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What an {@link Element} is: the currency of composition, and a value.
 *
 * <p>Everything else in the kit rests on the two words in that sentence. Currency — one type goes onto
 * a canvas, into a slot and inside a larger element, so a page needs no vocabulary of containers. Value
 * — an element is its descriptor and nothing besides, which is what makes two of them equal, what lets
 * a set deduplicate them, and what stops a list a caller still holds from changing a page after it was
 * composed.
 *
 * <p>The maker is specified in {@code ElementDescriptorTest}, the guards it hands to hosts in
 * {@code ElementGuardsTest}, the outline in {@code ElementOutlineTest} and the scripts in
 * {@code ElementAssetsTest}. This file is about what an element already is.
 */
class ElementTest {

    /** An element carries the address of its adapter, and reads it back the way the dispatcher will. */
    @Test
    void anElementIsAnAddressAndData() {
        Element<Element.Raw> card = Element.raw("fragments/my/card", "myCardEl").with("title", "Baobab").build();

        assertThat(card.template()).isEqualTo("fragments/my/card");
        assertThat(card.fragment()).isEqualTo("myCardEl");
        assertThat(card.bare()).isFalse();
        assertThat(card.asMap()).containsEntry("title", "Baobab");
    }

    /** It has already become one, so building it again is the same value, not a copy of it. */
    @Test
    void anElementHasAlreadyBecomeOne() {
        Element<Element.Raw> card = Element.raw("t", "cardEl").build();

        assertThat(card.build()).isSameAs(card);
    }

    /**
     * An element is its descriptor: two built from the same description are the same element, a set
     * holds one of them, and the marker plays no part — it is a phantom, worn to help the compiler and
     * absent from the value.
     */
    @Test
    void anElementIsItsDescriptor() {
        Element<Element.Raw> one = Element.raw("t", "cardEl").with("title", "x").build();
        Element<Element.Raw> other = Element.raw("t", "cardEl").with("title", "x").build();

        assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
        assertThat(new HashSet<>(List.of(one, other))).hasSize(1);
        // the hash is the descriptor's: one constant would be a correct hash and a useless one, and a
        // page of a hundred elements deduplicates through it
        assertThat(one.hashCode()).isEqualTo(one.asMap().hashCode())
            .isNotEqualTo(Element.raw("t", "cardEl").with("title", "y").build().hashCode());
        assertThat(one).isNotEqualTo(Element.raw("t", "cardEl").with("title", "y").build());
        assertThat(one).isNotEqualTo("not an element").isNotEqualTo(null);

        Element<Caption> asACaption = Caption.label("x").build();
        Element<Heading> asAHeading = Heading.h2("x").build();
        assertThat(asACaption).isNotEqualTo(asAHeading);          // different descriptors, not different markers
    }

    /**
     * And nothing can change it afterwards, however deep the data goes. A caller keeps the list it
     * handed over and goes on using it; the page it composed is already decided.
     */
    @Test
    void nothingChangesAnElementAfterwards() {
        var rows = new ArrayList<>(List.of("first"));
        var nested = new java.util.HashMap<String, Object>();
        nested.put("inner", rows);
        Element<Element.Raw> table = Element.raw("t", "tableEl").with("rows", rows).with("nested", nested).build();

        rows.add("second");
        nested.put("late", "x");

        assertThat(table.asMap()).extracting("rows").isEqualTo(List.of("first"));
        assertThat(table.asMap()).extracting("nested").isEqualTo(Map.of("inner", List.of("first")));
        assertThatThrownBy(() -> table.asMap().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((List<?>) table.asMap().get("rows")).clear())
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> ((Map<?, ?>) table.asMap().get("nested")).clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }

    /** Slots are read by name: what was put in, in the order the adapter will render it. */
    @Test
    void slotsAreReadByName() {
        Element<Element.Raw> first = Element.raw("t", "aEl").build();
        Element<Element.Raw> second = Element.raw("t", "bEl").build();
        Element<Element.Raw> section = Element.raw("t", "sectionEl")
            .slot("items", List.of(first, second))
            .slot("footer", List.of())
            .build();

        assertThat(section.slot("items")).containsExactly(first.asMap(), second.asMap());
        assertThat(section.slotNames()).containsExactly("items", "footer");
        assertThat(section.slot("footer")).isEmpty();
        assertThat(section.slot("nowhere")).isEmpty();
        assertThatThrownBy(() -> section.slot(null)).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("name");
    }

    /** An element that fills no slot says so, rather than making one up. */
    @Test
    void anElementWithoutSlotsHasNone() {
        Element<Element.Raw> plain = Element.raw("t", "plainEl").build();

        assertThat(plain.slotNames()).isEmpty();
        assertThat(plain.slot("items")).isEmpty();
        assertThat(plain.asMap()).doesNotContainKey("slots");
    }

    /** A script element is rendered without an argument, and says which it is. */
    @Test
    void aScriptElementSaysItIsOne() {
        Element<Element.Script> script = Element.script("fragments/my/card", "myCardJs");

        assertThat(script.bare()).isTrue();
        assertThat(script.template()).isEqualTo("fragments/my/card");
        assertThat(script.fragment()).isEqualTo("myCardJs");
        assertThat(Element.raw("t", "cardEl").build().bare()).isFalse();
    }

    /**
     * An element framed for display says so, and says it to whoever walks the page: what is inside a
     * sample is a demonstration, not the structure of the page it stands on. The question is asked
     * here because the key is this class's own; who asks it is somebody else's business.
     */
    @Test
    void anElementFramedForDisplaySaysSo() {
        assertThat(Element.isIllustration(Element.raw("t", "frameEl").illustration().build().asMap())).isTrue();
        assertThat(Element.isIllustration(Element.raw("t", "frameEl").build().asMap())).isFalse();
        assertThat(Element.isIllustration(Map.of())).isFalse();
    }

    /** Read by a person, an element shows what it is and what it carries: it is a value, so it prints as one. */
    @Test
    void anElementPrintsAsWhatItIs() {
        assertThat(Element.raw("t", "cardEl").with("title", "Baobab").build().toString())
            .startsWith("Element{").contains("template=t").contains("title=Baobab");
    }

    /** The markers are names for the compiler and nothing else: there is nothing to make one of. */
    @Test
    void theMarkersAreNamesAndNotThings() {
        for (Class<?> marker : List.of(Element.Raw.class, Element.Script.class)) {
            assertThat(marker.getDeclaredConstructors()).allSatisfy(constructor ->
                assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()))
                    .as("%s has nothing public to construct", marker.getSimpleName()).isTrue());
        }
    }
}
