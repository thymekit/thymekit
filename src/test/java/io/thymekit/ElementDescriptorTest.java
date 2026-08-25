/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What {@link Element.Descriptor} is: the one way an element is made, inside the kit and outside it.
 *
 * <p>A factory of a consumer's own writes {@code Element.Descriptor.<Price>of(address).with(…).build()}
 * and has an element the kit will take anywhere it takes its own. So the maker owes two things: that
 * what it produces is a value nobody can reach into afterwards, and that what it refuses, it refuses
 * where the mistake was made rather than on a page.
 */
class ElementDescriptorTest {

    /** A factory names its adapter and its marker, and gets a maker. */
    @Test
    void aFactoryNamesItsAdapterAndGetsAMaker() {
        Element<Element.Raw> price = Element.Descriptor.<Element.Raw>of("fragments/my/price", "priceEl")
            .with("amount", "12.00").build();

        assertThat(price.template()).isEqualTo("fragments/my/price");
        assertThat(price.fragment()).isEqualTo("priceEl");
        assertThat(price.asMap()).containsEntry("amount", "12.00").containsEntry("bare", false);
    }

    /**
     * The address is checked for shape, and not out of tidiness: the dispatcher turns it into a fragment
     * expression, so anything assembled from data would be evaluated instead of read. A path and a name,
     * and nothing that could be an expression.
     */
    @Test
    void anAddressIsAPathAndANameAndNothingElse() {
        assertThat(Element.raw("fragments/my/card-v2.inner", "cardElV2").build().fragment()).isEqualTo("cardElV2");

        for (String notAnAddress : List.of("", " ", "t :: f", "t' + ${T(java.lang.Runtime)} + '", "-t", "t\n")) {
            assertThatThrownBy(() -> Element.raw(notAnAddress, "cardEl"))
                .isInstanceOf(MisuseException.class).hasMessageContaining("adapter address");
            assertThatThrownBy(() -> Element.raw("t", notAnAddress))
                .isInstanceOf(MisuseException.class).hasMessageContaining("adapter address");
        }
        assertThatThrownBy(() -> Element.raw("t", "card-el")).isInstanceOf(MisuseException.class);
        assertThatThrownBy(() -> Element.raw(null, "cardEl")).isInstanceOf(MisuseException.class)
            .hasMessage("Descriptor(template): was not given");
        assertThatThrownBy(() -> Element.script("t", null)).isInstanceOf(MisuseException.class)
            .hasMessage("Descriptor(fragment): was not given");
    }

    /** Data is whatever the adapter reads; the keys the engine uses for itself are not on offer. */
    @Test
    void theKeysTheEngineUsesAreNotOnOffer() {
        Element.Descriptor<Element.Raw> maker = Element.raw("t", "cardEl");

        for (String reserved : List.of("template", "fragment", "bare", "slots", "assets", "illustration")) {
            assertThatThrownBy(() -> maker.with(reserved, "x"))
                .isInstanceOf(MisuseException.class).hasMessageContaining(reserved);
        }
        assertThatThrownBy(() -> maker.with(null, "x")).isInstanceOf(MisuseException.class)
            .hasMessage("Descriptor.with(key): was not given");
        assertThatThrownBy(() -> maker.with("title", null)).isInstanceOf(MisuseException.class)
            .as("the key is in the message; the place stays the call, or nothing can route on it")
            .hasMessage("Descriptor.with(value): the value of key \"title\" was not given");
    }

    /**
     * What is handed to the maker is copied on the way in, however deep it goes, and a list with a hole
     * in it is a page's business rather than a mistake — refusing it here would turn a rendering
     * decision into an exception.
     */
    @Test
    void whatIsHandedOverIsCopiedOnTheWayIn() {
        var rows = new ArrayList<>(List.of("first"));
        Element<Element.Raw> table = Element.raw("t", "tableEl").with("rows", rows).build();
        rows.add("second");

        assertThat(table.asMap()).extracting("rows").isEqualTo(List.of("first"));
        assertThat(Element.raw("t", "tableEl").with("holes", Arrays.asList("a", null)).build().asMap())
            .extracting("holes").isEqualTo(Arrays.asList("a", null));
        assertThat(Element.raw("t", "tableEl").with("count", 42).build().asMap()).containsEntry("count", 42);
    }

    /** A maker may go on after it has been built from, and what was built does not follow it. */
    @Test
    void aMakerMayGoOnAndWhatItBuiltDoesNot() {
        Element.Descriptor<Element.Raw> maker = Element.raw("t", "cardEl").with("title", "first");
        Element<Element.Raw> early = maker.build();

        maker.with("title", "second").slot("items", List.of(Element.raw("t", "aEl")));
        Element<Element.Raw> late = maker.build();

        assertThat(early.asMap()).containsEntry("title", "first").doesNotContainKey("slots");
        assertThat(late.asMap()).containsEntry("title", "second");
        assertThat(late.slot("items")).hasSize(1);
    }

    /** A slot holds whatever becomes an element, in the order given; an empty one is a slot with nothing in it. */
    @Test
    void aSlotHoldsWhateverBecomesAnElement() {
        Element.Descriptor<Element.Raw> maker = Element.raw("t", "sectionEl")
            .slot("items", List.of(Heading.h2("Title"), Caption.meta("12 entries").build()))
            .slot("footer", List.of());

        Element<Element.Raw> section = maker.build();
        assertThat(section.slot("items")).containsExactly(
            Heading.h2("Title").build().asMap(), Caption.meta("12 entries").build().asMap());
        assertThat(section.slotNames()).containsExactly("items", "footer");

        assertThatThrownBy(() -> maker.slot(null, List.of())).isInstanceOf(MisuseException.class)
            .hasMessage("Descriptor.slot(name): was not given");
        assertThatThrownBy(() -> maker.slot("items", null)).isInstanceOf(MisuseException.class)
            .hasMessage("Descriptor.slot(items): was not given");
        assertThatThrownBy(() -> maker.slot("items", Arrays.asList(Heading.h2("x"), null)))
            .isInstanceOf(MisuseException.class).hasMessageContaining("Descriptor.slot(items) — one of them");
    }

    /** A dependency is a script element and nothing else: anything else would be rendered as an adapter. */
    @Test
    void aDependencyIsAScriptElement() {
        Element<Element.Script> js = Element.script("fragments/my/card", "myCardJs");
        Element.Descriptor<Element.Raw> maker = Element.raw("t", "cardEl");

        assertThat(Tree.assetsOf(List.of(maker.requires(js).build()))).containsExactly(js);
        // the marker alone is not the shape: a descriptor can wear it without being a script
        Element<Element.Script> wearsTheMarker =
            Element.Descriptor.<Element.Script>of("fragments/my/card", "notAScriptEl").build();
        assertThatThrownBy(() -> Element.raw("t", "cardEl").requires(wearsTheMarker))
            .isInstanceOf(MisuseException.class).hasMessageContaining("script element");
        assertThatThrownBy(() -> Element.raw("t", "cardEl").requires((Element<Element.Script>[]) null))
            .isInstanceOf(MisuseException.class).hasMessage("Descriptor.requires(scripts): was not given");
        assertThatThrownBy(() -> Element.raw("t", "cardEl").requires(js, null))
            .isInstanceOf(MisuseException.class)
            .hasMessage("Descriptor.requires(scripts) — one of them: was not given");
    }

    /** An illustration is a sample framed for display, and says so in the descriptor. */
    @Test
    void anIllustrationSaysSo() {
        assertThat(Element.raw("t", "frameEl").illustration().build().asMap())
            .containsEntry("illustration", true);
        assertThat(Element.raw("t", "frameEl").build().asMap()).doesNotContainKey("illustration");
    }

    /**
     * An element is not the value of a key. It goes in a slot, which is the place made for it, or it
     * goes in as its descriptor — which is what every element the kit ships does, and what the
     * dispatcher can actually render.
     *
     * <p>Put the element itself and nothing tells you: the page renders without that part, because the
     * dispatcher asks a descriptor for its address and an element is not one; the walk cannot compare
     * it; and a page stored as data has an object in it that no reader can spell. A key holds data,
     * and "an address and data" is the sentence this whole kit rests on.
     */
    @Test
    void anElementIsNotTheValueOfAKey() {
        var heading = Heading.h2("Composition").build();

        assertThatThrownBy(() -> Element.raw("t", "cardEl").with("title", heading))
            .isInstanceOf(MisuseException.class)
            .hasMessage("Descriptor.with(value): an element is not the value of a key — put it in a slot,"
                + " or put its descriptor with element.asMap() where the adapter renders it in place");
        assertThatThrownBy(() -> Element.raw("t", "cardEl").with("title", Heading.h2("Composition")))
            .as("nor is a maker of one")
            .isInstanceOf(MisuseException.class)
            .hasMessageStartingWith("Descriptor.with(value): an element is not the value of a key");
        assertThatThrownBy(() -> Element.raw("t", "cardEl").with("rows", List.of(heading)))
            .as("and hiding one inside a list does not make it data")
            .isInstanceOf(MisuseException.class)
            .hasMessageStartingWith("Descriptor.with(value): an element is not the value of a key");

        assertThatCode(() -> Element.raw("t", "cardEl").with("title", heading.asMap()))
            .as("its descriptor is data, and that is what goes in").doesNotThrowAnyException();
    }
}