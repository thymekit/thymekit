/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
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
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("adapter address");
            assertThatThrownBy(() -> Element.raw("t", notAnAddress))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("adapter address");
        }
        assertThatThrownBy(() -> Element.raw("t", "card-el")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Element.raw(null, "cardEl")).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("template");
        assertThatThrownBy(() -> Element.script("t", null)).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("fragment");
    }

    /** Data is whatever the adapter reads; the keys the engine uses for itself are not on offer. */
    @Test
    void theKeysTheEngineUsesAreNotOnOffer() {
        Element.Descriptor<Element.Raw> maker = Element.raw("t", "cardEl");

        for (String reserved : List.of("template", "fragment", "bare", "slots", "assets", "illustration")) {
            assertThatThrownBy(() -> maker.with(reserved, "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(reserved);
        }
        assertThatThrownBy(() -> maker.with(null, "x")).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("key");
        assertThatThrownBy(() -> maker.with("title", null)).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("title");
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

        assertThatThrownBy(() -> maker.slot(null, List.of())).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("name");
        assertThatThrownBy(() -> maker.slot("items", null)).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("items");
        assertThatThrownBy(() -> maker.slot("items", Arrays.asList(Heading.h2("x"), null)))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("slot item");
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
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("script element");
        assertThatThrownBy(() -> Element.raw("t", "cardEl").requires((Element<Element.Script>[]) null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("scripts");
        assertThatThrownBy(() -> Element.raw("t", "cardEl").requires(js, null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("script");
    }

    /** An illustration is a sample framed for display, and says so in the descriptor. */
    @Test
    void anIllustrationSaysSo() {
        assertThat(Element.raw("t", "frameEl").illustration().build().asMap())
            .containsEntry("illustration", true);
        assertThat(Element.raw("t", "frameEl").build().asMap()).doesNotContainKey("illustration");
    }
}
