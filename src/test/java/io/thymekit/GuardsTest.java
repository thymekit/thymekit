/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

/**
 * What a value must be before a page carries it.
 *
 * <p>Six refusals about text and about html, and not one of them knows what an element is — which is
 * why they are here and not in the currency. Public, because an element of yours mints values exactly
 * as the kit's own do: a guard only the kit can call would make "your element is an element like ours"
 * a half-truth, and a consumer re-implementing one is where two spellings of a rule begin.
 */
class GuardsTest {

    /**
     * Text a page will show is given and is not empty. Two elements refuse the same thing for the same
     * reason, which by the canon makes it one rule rather than two spellings — and what is given is kept
     * exactly, since a space inside a line belongs to whoever wrote the line.
     */
    @Test
    void textAPageWillShowIsNotNothing() {
        assertThat(Guards.text("Baobab", "Card.title(text)")).isEqualTo("Baobab");
        assertThat(Guards.text("  kept  ", "Card.title(text)")).isEqualTo("  kept  ");

        for (String nothing : java.util.List.of("", " ", "\t\n ")) {
            assertThatThrownBy(() -> Guards.text(nothing, "Card.title(text)"))
                .isInstanceOf(MisuseException.class)
                .hasMessage("Card.title(text): is blank — a page shows what it was given, and this is nothing");
        }
        assertThatThrownBy(() -> Guards.text(null, "Card.title(text)"))
            .isInstanceOf(MisuseException.class).hasMessage("Card.title(text): was not given");
    }

    /**
     * A language tag goes into an attribute that tells a screen reader how to pronounce a phrase, so a
     * sentence or an empty string must not reach it — a page that claims a language it does not speak is
     * worse than one that claims none.
     */
    @Test
    void aLanguageTagIsATagAndNotASentence() {
        assertThat(Guards.tag("la", "Card.lang(languageTag)")).isEqualTo("la");
        assertThat(Guards.tag("pt-BR", "Card.lang(languageTag)")).isEqualTo("pt-BR");
        assertThat(Guards.tag("zh-Hant-HK", "Card.lang(languageTag)")).isEqualTo("zh-Hant-HK");

        for (String notATag : java.util.List.of("", " ", "по-русски", "la la", "la_LA", "-la", "la-")) {
            assertThatThrownBy(() -> Guards.tag(notATag, "Card.lang(languageTag)"))
                .isInstanceOf(MisuseException.class).hasMessageStartingWith("Card.lang(languageTag):")
                .hasMessageContaining("is not a language tag");
        }
        assertThatThrownBy(() -> Guards.tag(null, "Card.lang(languageTag)"))
            .isInstanceOf(MisuseException.class).hasMessage("Card.lang(languageTag): was not given");
    }

    /**
     * An address that leaves the page is absolute, and one that stays navigates rather than executes.
     * The second is the last place a scheme that runs instead of going anywhere can be stopped.
     */
    @Test
    void anAddressIsWhatItClaimsToBe() {
        assertThat(Guards.absolute("https://shop/x", "Canvas.canonical(url)")).isEqualTo("https://shop/x");
        assertThat(Guards.absolute("  http://shop/x  ", "Canvas.canonical(url)")).isEqualTo("http://shop/x");
        assertThatThrownBy(() -> Guards.absolute("/x", "Canvas.canonical(url)"))
            .isInstanceOf(MisuseException.class)
            .hasMessageStartingWith("Canvas.canonical(url): is not an absolute address:");

        assertThat(Guards.navigable("/x", "Heading.href(href)")).isEqualTo("/x");
        for (String script : java.util.List.of("javascript:alert(1)", "JavaScript:alert(1)",
                "java\tscript:alert(1)", "data:text/html,x", "vbscript:x")) {
            assertThatThrownBy(() -> Guards.navigable(script, "Heading.href(href)"))
                .as("a browser unpicks that before it reads a scheme")
                .isInstanceOf(MisuseException.class)
                .hasMessageStartingWith("Heading.href(href): is not a link but a script:");
        }
    }

    /** An anchor is one word, because an attribute keeps only what comes before the first space. */
    @Test
    void anAnchorIsOneWord() {
        assertThat(Guards.anchor("in-the-south", "Heading.id(id)")).isEqualTo("in-the-south");
        for (String notOne : java.util.List.of("two words", "with\ttab", "line\nbreak")) {
            assertThatThrownBy(() -> Guards.anchor(notOne, "Heading.id(id)"))
                .isInstanceOf(MisuseException.class)
                .hasMessageStartingWith("Heading.id(id): is not one word:");
        }
        assertThatThrownBy(() -> Guards.anchor(" ", "Heading.id(id)"))
            .isInstanceOf(MisuseException.class).hasMessageStartingWith("Heading.id(id): is blank");
    }

    /** A value that had to be given, and the one refusal every other guard begins with. */
    @Test
    void aValueThatHadToBeGiven() {
        assertThat(Guards.required("x", "Card.of(x)")).isEqualTo("x");
        assertThatThrownBy(() -> Guards.required(null, "Card.of(x)"))
            .isInstanceOf(MisuseException.class).hasMessage("Card.of(x): was not given");
    }

    /**
     * And every one of them is reachable from outside, because an element of yours mints the same
     * values the kit's own do.
     */
    @Test
    void theGuardsArePublic() {
        for (String guard : java.util.List.of("required", "text", "tag", "absolute", "navigable", "anchor")) {
            assertThat(java.util.Arrays.stream(Guards.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(guard))
                    .allMatch(m -> Modifier.isPublic(m.getModifiers())))
                .as("Guards.%s is a guard an element of yours can call", guard).isTrue();
        }
    }
}
