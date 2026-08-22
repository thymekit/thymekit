/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What {@link Composable} is for: one signature that means "whatever becomes an element", so that a
 * page reads the way the concept does — with nothing written between one element and the next.
 *
 * <p>The interface and the currency name each other, and neither can be described alone: an element is
 * composable because it has already become one, and the interface exists so that a place taking an
 * element can take a maker as well. That pairing is what this file is about; what an element is once it
 * exists belongs to {@code ElementTest}.
 */
class ComposableTest {

    /** A type of a consumer's own, which knows how to describe itself and nothing more. */
    private record Price(String amount) {
        Element<Price> asElement() {
            return Element.Descriptor.<Price>of("fragments/my/price", "priceEl").with("amount", amount).build();
        }
    }

    /** An element is what has already become one, so it answers the interface with itself. */
    @Test
    void anElementIsAlreadyComposable() {
        Element<Caption> caption = Caption.label("Composition").build();
        Composable<Caption> asAMaker = caption;

        assertThat(asAMaker.build()).isSameAs(caption);
    }

    /**
     * And that is why one signature is enough. A slot takes whatever becomes an element: a maker still
     * being configured, something already settled, and a lambda over a type the kit has never heard of.
     * All three arrive as the same thing, which is what lets a page be written without {@code .build()}
     * between the lines.
     */
    @Test
    void onePlaceTakesAMakerAnElementAndALambda() {
        Composable<Heading> maker = Heading.h2("Title");
        Element<Caption> settled = Caption.meta("12 entries").build();
        Composable<Price> ofTheirOwn = () -> new Price("12.00").asElement();

        Element<Element.Raw> section = Element.raw("t", "sectionEl")
            .slot("items", List.of(maker, settled, ofTheirOwn))
            .build();

        assertThat(section.slot("items")).containsExactly(
            maker.build().asMap(), settled.asMap(), ofTheirOwn.build().asMap());
    }

    /**
     * What a place takes, it settles there and then. A maker handed over goes on being a maker — its
     * owner may keep configuring it — and none of that reaches the page that already took it. This is
     * the behaviour behind the rule the canon keeps: accept what becomes an element, hold only what has
     * become one.
     */
    @Test
    void whatIsTakenIsSettledWhereItIsTaken() {
        Caption.Builder stillBeingWritten = Caption.meta("12 March 2026");

        Element<Element.Raw> row = Element.raw("t", "rowEl")
            .slot("items", List.of(stillBeingWritten))
            .build();

        stillBeingWritten.time(java.time.LocalDate.of(2026, 3, 12));

        assertThat(row.slot("items")).singleElement()
            .satisfies(item -> assertThat(item).doesNotContainKey("datetime"));
        assertThat(stillBeingWritten.build().asMap()).containsKey("datetime");   // the maker did change
    }

    /** One method, so a lambda is one — and a second would take that away from every consumer at once. */
    @Test
    void itStaysOneMethodWide() {
        assertThat(Composable.class.isAnnotationPresent(FunctionalInterface.class)).isTrue();
        assertThat(Composable.class.getDeclaredMethods()).hasSize(1);
    }
}
