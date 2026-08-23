/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The header of a page: a heading group, and under it what the page says about itself before it says
 * anything else.
 *
 * <p>This is the kit's one host — the element made of other elements, where each place has an opinion
 * about what may stand in it. An eyebrow is a caption in the eyebrow role and nothing else; the title
 * is the H1 of the page and not any heading; the badge and the action row are the consumer's own
 * elements, and the kit names the address it will accept rather than the class. So the interesting
 * part of this element is not what it holds but what it refuses, and every refusal names the place it
 * fired in, because it fires on a line the consumer wrote.
 *
 * <p>The order the parts appear in belongs to the adapter and the walk over triples; what this file
 * describes is the descriptor and the guards.
 */
class HeroTest {

    private static final Element<Element.Raw> BADGE =
        Element.raw("fragments/my/badge", "statusBadgeEl").with("text", "in stock").build();
    private static final Element<Element.Raw> ACTIONS =
        Element.raw("fragments/my/actions", "actionsEl").with("text", "Buy").build();

    /** The core is the H1 alone; everything else is a place a page may leave empty. */
    @Test
    void theCoreIsTheTitleAlone() {
        Element<Hero> bare = Hero.of(Heading.h1("Baobab")).build();

        assertThat(bare.asMap())
            .containsEntry("template", "thymekit/hero").containsEntry("fragment", "heroEl")
            .containsEntry("heading", Heading.h1("Baobab").build().asMap())
            .doesNotContainKey("eyebrow").doesNotContainKey("subtitle")
            .doesNotContainKey("metas").doesNotContainKey("badge").doesNotContainKey("actions");
    }

    /** And the whole of it, each part in the place the page gave it. */
    @Test
    void andEveryPlaceAPageMayFill() {
        Element<Hero> full = Hero.of(Heading.h1("Baobab"))
            .eyebrow(Caption.eyebrow("Catalogue"))
            .subtitle(Caption.subtitle("Adansonia digitata"))
            .meta(Caption.meta("/baobab"), Caption.meta("12 entries"))
            .badge(BADGE)
            .actions(ACTIONS)
            .build();

        assertThat(full.asMap())
            .containsEntry("eyebrow", Caption.eyebrow("Catalogue").build().asMap())
            .containsEntry("subtitle", Caption.subtitle("Adansonia digitata").build().asMap())
            .containsEntry("metas", List.of(Caption.meta("/baobab").build().asMap(),
                Caption.meta("12 entries").build().asMap()))
            .containsEntry("badge", BADGE.asMap())
            .containsEntry("actions", ACTIONS.asMap());
    }

    /**
     * The title of a page is an H1, and this element is where that is decided. A level is read the way
     * the element that owns headings reads it — a descriptor minted by hand may write it as text, and a
     * guard that understood only a number would let a second title onto the page.
     */
    @Test
    void theTitleOfAPageIsAnH1() {
        assertThatThrownBy(() -> Hero.of(Heading.h2("Baobab")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("H1 only");

        @SuppressWarnings("unchecked")
        Composable<Heading> byHand = (Composable<Heading>) (Composable<?>) Element.raw("thymekit/heading", "headingEl")
            .with("level", "1").with("text", "written as text");
        assertThat(Hero.of(byHand).build().asMap()).as("a level counts however it was written")
            .containsKey("heading");

        @SuppressWarnings("unchecked")
        Composable<Heading> noLevelAtAll = (Composable<Heading>) (Composable<?>) Element.raw("thymekit/heading", "headingEl")
            .with("text", "a heading of no level");
        assertThatThrownBy(() -> Hero.of(noLevelAtAll))
            .as("and one that reads as no level at all is not the title of anything")
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("H1 only");

        @SuppressWarnings("unchecked")
        Composable<Heading> notAHeading = (Composable<Heading>) (Composable<?>) Caption.label("Baobab");
        assertThatThrownBy(() -> Hero.of(notAHeading))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("heading only").hasMessageContaining("captionEl");
        assertThatThrownBy(() -> Hero.of(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("heading");
    }

    /** Each place takes a caption in its own role, and says which role it wanted. */
    @Test
    void eachPlaceTakesACaptionInItsOwnRole() {
        assertThatThrownBy(() -> Hero.of(Heading.h1("x")).eyebrow(Caption.meta("wrong role")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Hero.eyebrow").hasMessageContaining("eyebrow").hasMessageContaining("meta");
        assertThatThrownBy(() -> Hero.of(Heading.h1("x")).subtitle(Caption.label("wrong role")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Hero.subtitle");
        assertThatThrownBy(() -> Hero.of(Heading.h1("x")).meta(Caption.eyebrow("wrong role")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Hero.meta");
        assertThatThrownBy(() -> Hero.of(Heading.h1("x")).eyebrow(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("caption");
    }

    /** Meta lines accumulate in call order, whether they come one at a time or several at once. */
    @Test
    void metaLinesAccumulateInCallOrder() {
        Element<Hero> hero = Hero.of(Heading.h1("x"))
            .meta(Caption.meta("first"))
            .meta(Caption.meta("second"), Caption.meta("third"))
            .build();

        assertThat((List<?>) hero.asMap().get("metas")).hasSize(3);
        assertThat(((List<?>) hero.asMap().get("metas")).getFirst())
            .isEqualTo(Caption.meta("first").build().asMap());
    }

    /** An option called with nothing to say is a line that meant something and lost it. */
    @Test
    void anOptionCalledWithNothingIsRefused() {
        assertThatThrownBy(() -> Hero.of(Heading.h1("x")).meta())
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("without a value");
        assertThatThrownBy(() -> Hero.of(Heading.h1("x")).meta((Composable<Caption>[]) null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("meta");
        assertThatThrownBy(() -> Hero.of(Heading.h1("x")).meta(Caption.meta("first"), null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("caption");
    }

    /**
     * The badge and the action row are the consumer's own elements. The kit fixes the place and the
     * shape of the slot, and names the address it will accept — {@code statusBadgeEl}, {@code
     * actionsEl} — which no fragment of the kit defines. What goes in them is written outside.
     */
    @Test
    void theBadgeAndTheActionsAreTheConsumersOwn() {
        assertThat(Hero.of(Heading.h1("x")).badge(BADGE).build().asMap()).containsEntry("badge", BADGE.asMap());

        assertThatThrownBy(() -> Hero.of(Heading.h1("x")).badge(Caption.label("not a badge")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status badge").hasMessageContaining("captionEl");
        assertThatThrownBy(() -> Hero.of(Heading.h1("x")).actions(Caption.label("not an action row")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("action row");
        assertThatThrownBy(() -> Hero.of(Heading.h1("x")).badge(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("badge");
        assertThatThrownBy(() -> Hero.of(Heading.h1("x")).actions(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("actions");
    }

    /** Said twice, the last one is what the page shows: a place holds one thing. */
    @Test
    void aPlaceHoldsOneThingAndTheLastOneSaidWins() {
        Element<Hero> hero = Hero.of(Heading.h1("x"))
            .subtitle(Caption.subtitle("first"))
            .subtitle(Caption.subtitle("second"))
            .build();

        assertThat((Map<?, ?>) hero.asMap().get("subtitle")).extracting("text").isEqualTo("second");
    }

    /** A hero is a value, and a maker may go on being written after one was built from it. */
    @Test
    void aHeroIsAValue() {
        Hero.Builder maker = Hero.of(Heading.h1("Baobab")).meta(Caption.meta("first"));
        Element<Hero> early = maker.build();

        maker.meta(Caption.meta("second"));

        assertThat((List<?>) early.asMap().get("metas")).hasSize(1);
        assertThat((List<?>) maker.build().asMap().get("metas")).hasSize(2);
        assertThat(Hero.of(Heading.h1("x")).build()).isEqualTo(Hero.of(Heading.h1("x")).build());
    }
}
