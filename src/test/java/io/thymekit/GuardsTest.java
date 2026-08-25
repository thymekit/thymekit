/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * What a value must be before a page carries it.
 *
 * <p>Six refusals about text and about html, and not one of them knows what an element is — which is
 * why they are here and not in the currency. Public, because an element of yours mints values exactly
 * as the kit's own do: a guard only the kit can call would make "your element is an element like ours"
 * a half-truth, and a consumer re-implementing one is where two spellings of a rule begin.
 *
 * <p>Written as the spec of what they ought to be rather than of what they do, and asking about
 * <b>inputs</b>: what is absent, what is empty in every way a string can be empty, what a browser
 * unpicks on its way to reading a scheme, what looks like an address and is not one.
 */
class GuardsTest {

    /** A non-breaking space: what an editor leaves behind when somebody empties a field. */
    private static final String NBSP = "\u00A0";

    /** The rest of the family, none of which a reader sees and none of which is a space to java. */
    private static final List<String> INVISIBLE = List.of(
        "\u200B",   // zero width space — pasted out of a spreadsheet, out of a CMS, out of anywhere
        "\uFEFF",   // byte order mark, at the head of a file somebody copied whole
        "\u2060",   // word joiner
        "\u00AD",   // soft hyphen
        "\u2002",   // en space, which java's \s does not match
        "\u3000",   // ideographic space
        "\u2007",   // figure space, non-breaking
        "\u202F",   // narrow no-break space
        "\u0000");  // and a control character, which shows nothing at all

    // ——— what had to be given ————————————————————————————————————————————————————————————

    /**
     * The one refusal every other guard begins with, and the reason none of them borrows a
     * {@code NullPointerException}: a consumer cannot tell somebody else's from their own code failing.
     */
    @Test
    void aValueThatHadToBeGiven() {
        assertThatThrownBy(() -> Guards.required(null, "Card.of(image)"))
            .isInstanceOf(MisuseException.class).hasMessage("Card.of(image): was not given");
    }

    /** What was given comes back as it was — the same object, not a copy of it. */
    @Test
    void whatWasGivenComesBackItself() {
        var image = Map.of("src", "/x.png");

        assertThat(Guards.required(image, "Card.of(image)")).isSameAs(image);
        assertThat(Guards.required(7, "Card.of(n)")).isEqualTo(7);
    }

    // ——— text a page will show ———————————————————————————————————————————————————————————

    /**
     * Text a page will show is given, and is not nothing — in every way a string is nothing. The last
     * of them is the one that matters: a field somebody emptied in a rich editor comes back holding a
     * non-breaking space, and a heading of one renders as a box with nothing in it.
     */
    @Test
    void textAPageWillShowIsNotNothing() {
        assertThat(Guards.text("Baobab", "Card.title(text)")).isEqualTo("Baobab");

        assertThatThrownBy(() -> Guards.text(null, "Card.title(text)"))
            .isInstanceOf(MisuseException.class).hasMessage("Card.title(text): was not given");
        List<String> nothings = new java.util.ArrayList<>(
            List.of("", " ", "\t", "\n", "\r\n", "\t\n  ", NBSP, " " + NBSP + " "));
        INVISIBLE.forEach(one -> { nothings.add(one); nothings.add(" " + one + NBSP); });
        for (String nothing : nothings) {
            assertThatThrownBy(() -> Guards.text(nothing, "Card.title(text)"))
                .as("a text of %s is an empty box on a page", nothing.codePoints().boxed().toList())
                .isInstanceOf(MisuseException.class)
                .hasMessage("Card.title(text): is blank — a page shows what it was given,"
                    + " and this is nothing");
        }
    }

    /**
     * And what is given is kept exactly. A space inside a line belongs to whoever wrote the line, and
     * so does one at the end of it: this guard refuses nothing, it does not tidy.
     */
    @Test
    void textIsKeptExactly() {
        for (String kept : List.of("  spaced  ", "two  spaces", "trailing ", "a b", "ends with\n")) {
            assertThat(Guards.text(kept, "Card.title(text)")).isEqualTo(kept);
        }
        assertThat(Guards.text("a" + NBSP + "b", "Card.title(text)"))
            .as("and a non-breaking space between two words is a space somebody meant")
            .isEqualTo("a" + NBSP + "b");
        for (String invisible : INVISIBLE) {
            assertThat(Guards.text("a" + invisible + "b", "Card.title(text)"))
                .as("as is anything invisible between two visible things")
                .isEqualTo("a" + invisible + "b");
        }
    }

    // ——— a language tag ——————————————————————————————————————————————————————————————————

    /**
     * A language tag goes into the attribute that tells a screen reader how to pronounce a phrase, so
     * a sentence there is worse than nothing: a page that claims a language it does not speak is read
     * aloud wrongly and confidently.
     */
    @Test
    void aLanguageTagIsATagAndNotASentence() {
        for (String tag : List.of("la", "pt-BR", "zh-Hant-HK", "en", "EN-us", "de-CH-1901")) {
            assertThat(Guards.tag(tag, "Card.lang(languageTag)")).isEqualTo(tag);
        }
        for (String notATag : List.of("", " ", "по-русски", "la la", "la_LA", "-la", "la-", "la--LA")) {
            assertThatThrownBy(() -> Guards.tag(notATag, "Card.lang(languageTag)"))
                .isInstanceOf(MisuseException.class)
                .hasMessageStartingWith("Card.lang(languageTag): is not a language tag:");
        }
        assertThatThrownBy(() -> Guards.tag(null, "Card.lang(languageTag)"))
            .isInstanceOf(MisuseException.class).hasMessage("Card.lang(languageTag): was not given");
    }

    /**
     * What it does not do is check a registry: {@code zz-ZZ} is a tag in shape and nonsense in fact,
     * and telling those apart needs a list this kit has no business carrying.
     */
    @Test
    void aTagIsCheckedForShapeAndNotForTruth() {
        assertThat(Guards.tag("zz-ZZ", "Card.lang(languageTag)")).isEqualTo("zz-ZZ");
    }

    // ——— an address that leaves the page —————————————————————————————————————————————————

    /**
     * An address that leaves the page is absolute, and it names a host. Whoever reads it is not looking
     * at the document: a path has nothing to resolve against, and a scheme with nothing after it
     * resolves to nowhere at all.
     */
    @Test
    void anAddressThatLeavesThePageIsAbsolute() {
        assertThat(Guards.absolute("https://shop/x", "Canvas.canonical(url)")).isEqualTo("https://shop/x");
        assertThat(Guards.absolute("HTTP://SHOP/X", "Canvas.canonical(url)")).isEqualTo("HTTP://SHOP/X");

        for (String notAbsolute : List.of("/x", "#x", "../x", "//shop/x", "ftp://shop/x", "https:/x",
                "https://", "http://", "https:///x", "shop/x")) {
            assertThatThrownBy(() -> Guards.absolute(notAbsolute, "Canvas.canonical(url)"))
                .as("%s does not name a place a stranger can reach", notAbsolute)
                .isInstanceOf(MisuseException.class)
                .hasMessageStartingWith("Canvas.canonical(url): is not an absolute address:");
        }
        assertThatThrownBy(() -> Guards.absolute("  ", "Canvas.canonical(url)"))
            .isInstanceOf(MisuseException.class).hasMessageStartingWith("Canvas.canonical(url): is blank");
    }

    /**
     * And an address that leaves the page carries no space inside it either. A stranger's parser is
     * not a browser: it will not guess where the address ended, and half of one in an {@code og:url}
     * is worse than none, because the page looks exactly like a page that had one.
     */
    @Test
    void anAddressThatLeavesThePageHasNoSpaceInIt() {
        for (String spaced : List.of("https://shop/a b", "https://shop/a" + NBSP + "b",
                "https://shop /x", "https://shop/a\u200Bb")) {
            assertThatThrownBy(() -> Guards.absolute(spaced, "Canvas.canonical(url)"))
                .isInstanceOf(MisuseException.class)
                .hasMessageStartingWith("Canvas.canonical(url): is not an address anybody can follow:");
        }
    }

    /**
     * An address that stays on the page keeps its spaces. A browser encodes what it must, this is the
     * one reader the kit knows will be looking, and a link of somebody's that has worked for years is
     * not the kit's to refuse.
     */
    @Test
    void anAddressThatStaysKeepsItsSpaces() {
        assertThat(Guards.navigable("/my page", "Heading.href(href)")).isEqualTo("/my page");
    }

    /** Space around an address is nobody's meaning, so it comes back without any. */
    @Test
    void anAddressComesBackWithoutSpaceAroundIt() {
        assertThat(Guards.absolute("  https://shop/x\n", "Canvas.canonical(url)")).isEqualTo("https://shop/x");
        assertThat(Guards.navigable("  /x  ", "Heading.href(href)")).isEqualTo("/x");
    }

    // ——— an address that navigates ———————————————————————————————————————————————————————

    /**
     * An href is the last place a scheme that executes instead of navigating can be stopped, and it is
     * stopped in every spelling a browser unpicks on its way to reading one.
     */
    @Test
    void anAddressNavigatesRatherThanExecutes() {
        for (String own : List.of("/x", "#x", "../x", "https://shop/x", "mailto:a@b", "tel:+1")) {
            assertThat(Guards.navigable(own, "Heading.href(href)")).isEqualTo(own);
        }
        for (String script : List.of("javascript:alert(1)", "JavaScript:alert(1)", "jAvAsCrIpT:x",
                "java\tscript:alert(1)", "java\nscript:x", " javascript:x",
                "data:text/html,<script>", "vbscript:x")) {
            assertThatThrownBy(() -> Guards.navigable(script, "Heading.href(href)"))
                .as("a browser unpicks that before it reads a scheme")
                .isInstanceOf(MisuseException.class)
                .hasMessageStartingWith("Heading.href(href): is not a link but a script:");
        }
        assertThatThrownBy(() -> Guards.navigable(null, "Heading.href(href)"))
            .isInstanceOf(MisuseException.class).hasMessage("Heading.href(href): was not given");
    }

    // ——— an address inside the page ——————————————————————————————————————————————————————

    /**
     * An anchor is one word, because an attribute keeps only what comes before the first space: an
     * anchor with a space in it is an address that silently truncates, and a link to it lands nowhere.
     */
    @Test
    void anAnchorIsOneWord() {
        for (String one : List.of("in-the-south", "section-2.1_a", "1", "été")) {
            assertThat(Guards.anchor(one, "Heading.id(id)")).isEqualTo(one);
        }
        List<String> notOne = new java.util.ArrayList<>(
            List.of("two words", "with\ttab", "line\nbreak", "trailing "));
        INVISIBLE.forEach(one -> notOne.add("in" + one + "south"));
        notOne.add("in" + NBSP + "south");
        for (String notOne0 : notOne) {
            assertThatThrownBy(() -> Guards.anchor(notOne0, "Heading.id(id)"))
                .as("an anchor nobody can type is an anchor nobody can follow")
                .isInstanceOf(MisuseException.class)
                .hasMessageStartingWith("Heading.id(id): is not one word:");
        }
        assertThatThrownBy(() -> Guards.anchor(" ", "Heading.id(id)"))
            .isInstanceOf(MisuseException.class).hasMessageStartingWith("Heading.id(id): is blank");
        assertThatThrownBy(() -> Guards.anchor(null, "Heading.id(id)"))
            .isInstanceOf(MisuseException.class).hasMessage("Heading.id(id): was not given");
    }

    // ——— what the class itself is ————————————————————————————————————————————————————————

    /**
     * Every one of them is reachable from outside, because an element of yours mints the same values
     * the kit's own do — and a guard only the kit can call would make "your element is an element like
     * ours" a half-truth.
     */
    @Test
    void theGuardsArePublic() {
        for (String guard : List.of("required", "text", "tag", "absolute", "navigable", "anchor")) {
            assertThat(java.util.Arrays.stream(Guards.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals(guard))
                    .allMatch(m -> Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers())))
                .as("Guards.%s is a guard an element of yours can call", guard).isTrue();
        }
    }

    /** A namespace and not a thing: final, and with nothing to instantiate. */
    @Test
    void itIsANamespace() throws Exception {
        assertThat(Modifier.isFinal(Guards.class.getModifiers())).isTrue();

        Constructor<Guards> alone = Guards.class.getDeclaredConstructor();
        assertThat(Modifier.isPrivate(alone.getModifiers())).isTrue();
        alone.setAccessible(true);
        assertThatCode(alone::newInstance).doesNotThrowAnyException();
    }
}
