/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * The guards {@link Element} hands to whoever hosts an element.
 *
 * <p>A host is any place that takes an element and has an opinion about which one: a hero that wants an
 * H1, a section that wants a heading, a canvas that wants anything but a script. Java says {@code
 * Element<?>} at every one of those points, because the marker is erased where it would help — so the
 * kit checks at run time, and hands the same checks to a consumer writing a host of their own. A guard
 * only the kit can call would make "your element is an element like ours" a half-truth.
 */
class ElementGuardsTest {

    /** Whatever becomes an element is settled here, and the argument is named when there is nothing. */
    @Test
    void whateverBecomesAnElementIsSettledHere() {
        Element<Heading> settled = Element.settle(Heading.h2("Title"), "heading");
        assertThat(settled.asMap()).containsEntry("text", "Title");
        assertThat(Element.settle(settled, "heading")).isSameAs(settled);

        assertThatThrownBy(() -> Element.settle(null, "heading"))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("heading");
        assertThatThrownBy(() -> Element.settle(() -> null, "heading"))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("heading built nothing");
    }

    /**
     * A script element never belongs in the flow: the dispatcher calls an adapter with the descriptor,
     * and a script fragment takes no argument, so the page would fail at render. The guard says where to
     * put it instead.
     */
    @Test
    void aScriptElementIsNotPartOfTheFlow() {
        Element<Element.Script> script = Element.script("fragments/my/card", "myCardJs");
        Element<Element.Raw> card = Element.raw("t", "cardEl").build();

        assertThat(Element.requireRenderableElement(card, "Row.add")).isSameAs(card);
        Element.requireRenderable(card, "Row.add");

        assertThatThrownBy(() -> Element.requireRenderable(script, "Row.add"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Row.add").hasMessageContaining("requires()");
        assertThatThrownBy(() -> Element.requireRenderableElement(script, "Row.add"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("script element");
        assertThatThrownBy(() -> Element.requireRenderable(null, "Row.add"))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("element");
    }

    /**
     * Where the marker is erased, the address is what a host can still check. It names what it wanted
     * and what it got, since a host guard fires on a line a consumer wrote and has to say why.
     */
    @Test
    void aHostChecksTheAddressWhenTheMarkerIsGone() {
        Element<Heading> heading = Heading.h2("Title").build();

        Element.requireAdapter(heading, "headingEl", "Section.of accepts a heading only");

        assertThatThrownBy(() -> Element.requireAdapter(
                Caption.label("x").build(), "headingEl", "Section.of accepts a heading only"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Section.of accepts a heading only").hasMessageContaining("captionEl");
        assertThatThrownBy(() -> Element.requireAdapter(null, "headingEl", "Section.of"))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("element");
    }

    /**
     * A language tag goes into an attribute that tells a screen reader how to pronounce a phrase, so a
     * sentence or an empty string must not reach it — a page that claims a language it does not speak is
     * worse than one that claims none.
     */
    @Test
    void aLanguageTagIsATagAndNotASentence() {
        assertThat(Element.requireTag("la", "languageTag")).isEqualTo("la");
        assertThat(Element.requireTag("pt-BR", "languageTag")).isEqualTo("pt-BR");
        assertThat(Element.requireTag("zh-Hant-HK", "languageTag")).isEqualTo("zh-Hant-HK");

        for (String notATag : java.util.List.of("", " ", "по-русски", "la la", "la_LA", "-la", "la-")) {
            assertThatThrownBy(() -> Element.requireTag(notATag, "languageTag"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("language tag");
        }
        assertThatThrownBy(() -> Element.requireTag(null, "languageTag"))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("languageTag");
    }

    /**
     * And every one of them is reachable from outside. Two elements of the kit already share the tag
     * check, which by the canon makes it policy rather than detail — and a consumer writing a caption of
     * their own needs the same guards the kit's own hosts use, or the promise that their element is an
     * element like ours holds only as far as the compiler.
     */
    @Test
    void theGuardsAreAsPublicAsTheHostsThatNeedThem() {
        for (String guard : java.util.List.of("settle", "requireRenderable", "requireRenderableElement",
                "requireAdapter", "requireTag")) {
            assertThat(java.util.Arrays.stream(Element.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(guard))
                    .allMatch(m -> Modifier.isPublic(m.getModifiers())))
                .as("Element.%s is a guard a host of yours can call", guard).isTrue();
        }
    }
}
