/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * What the writer of structured data owes its caller. Written before the writer exists, so it starts
 * red: the red is the list of things that have to become true, arrived at from the specification
 * rather than from reading an implementation.
 *
 * <p>The questions asked here are about the <b>input space</b>, not the code paths: what an empty
 * thing does, what a nested thing does, what the ends of the accepted numbers do, and — the half that
 * matters — what happens to text a person typed, since every string in a contribution came out of
 * somebody's database in the end.
 */
class JsonTest {

    // the shape it exists for

    /**
     * The thing itself: a breadcrumb graph, written the way it will be written in production. Not a
     * synthetic case — if this one is wrong, nothing else matters.
     */
    @Test
    void writesTheGraphItExistsFor() {
        var first = new LinkedHashMap<String, Object>();
        first.put("@type", "ListItem");
        first.put("position", 1);
        first.put("name", "Ingredients");
        first.put("item", "https://shop/ingredients");

        var last = new LinkedHashMap<String, Object>();
        last.put("@type", "ListItem");
        last.put("position", 2);
        last.put("name", "Aloe");

        var graph = new LinkedHashMap<String, Object>();
        graph.put("@context", "https://schema.org");
        graph.put("@type", "BreadcrumbList");
        graph.put("itemListElement", List.of(first, last));

        assertThat(Json.write(graph)).isEqualTo(
            "{\"@context\":\"https://schema.org\",\"@type\":\"BreadcrumbList\",\"itemListElement\":["
                + "{\"@type\":\"ListItem\",\"position\":1,\"name\":\"Ingredients\","
                + "\"item\":\"https://shop/ingredients\"},"
                + "{\"@type\":\"ListItem\",\"position\":2,\"name\":\"Aloe\"}]}");
    }

    // structure

    /** An empty map is a document that says nothing, not a document that is missing. */
    @Test
    void writesAnEmptyMap() {
        assertThat(Json.write(Map.of())).isEqualTo("{}");
    }

    /** And an empty list is an empty list. */
    @Test
    void writesAnEmptyList() {
        assertThat(Json.write(List.of())).isEqualTo("[]");
    }

    /**
     * Key order is the caller's, not the writer's. In a graph the order carries meaning to a reader —
     * the type first, then what the node is about — and a writer that sorted would take that away.
     */
    @Test
    void keepsTheOrderTheCallerChose() {
        var ordered = new LinkedHashMap<String, Object>();
        ordered.put("zulu", 1);
        ordered.put("alpha", 2);
        ordered.put("mike", 3);

        assertThat(Json.write(ordered)).isEqualTo("{\"zulu\":1,\"alpha\":2,\"mike\":3}");
    }

    /** Nesting has no special case: a list in a map in a list is written like anything else. */
    @Test
    void writesNestedStructures() {
        assertThat(Json.write(List.of(Map.of("inner", List.of("deep")))))
            .isEqualTo("[{\"inner\":[\"deep\"]}]");
    }

    /**
     * Output is compact: a page carries these bytes, and nobody reads them for pleasure. Asserted as
     * the whole string rather than by the absence of spaces — an assertion about what is missing is
     * one an empty answer would also satisfy.
     */
    @Test
    void writesWithoutWhitespace() {
        var two = new LinkedHashMap<String, Object>();
        two.put("a", 1);
        two.put("b", List.of(2, 3));

        assertThat(Json.write(two)).isEqualTo("{\"a\":1,\"b\":[2,3]}");
    }

    // the accepted values

    /** The two integral types a contribution can hold, at both ends of what they carry. */
    @Test
    void writesIntegersAndLongs() {
        var numbers = new LinkedHashMap<String, Object>();
        numbers.put("int", Integer.MIN_VALUE);
        numbers.put("long", Long.MAX_VALUE);
        numbers.put("zero", 0);

        assertThat(Json.write(numbers)).isEqualTo(
            "{\"int\":-2147483648,\"long\":9223372036854775807,\"zero\":0}");
    }

    /** Booleans are written as JSON has them, not as text. */
    @Test
    void writesBooleansUnquoted() {
        var flags = new LinkedHashMap<String, Object>();
        flags.put("yes", true);
        flags.put("no", false);

        assertThat(Json.write(flags)).isEqualTo("{\"yes\":true,\"no\":false}");
    }

    /** A map need not be a LinkedHashMap: any map with string keys is accepted, in its own order. */
    @Test
    void acceptsAnyMapWithStringKeys() {
        var sorted = new TreeMap<String, Object>();
        sorted.put("b", "second");
        sorted.put("a", "first");

        assertThat(Json.write(sorted)).isEqualTo("{\"a\":\"first\",\"b\":\"second\"}");
    }

    /** And any list, including one that was built rather than declared. */
    @Test
    void acceptsAnyList() {
        var built = new ArrayList<Object>();
        built.add("one");
        built.add(2);

        assertThat(Json.write(built)).isEqualTo("[\"one\",2]");
    }

    // text a person typed

    /** The two escapes JSON itself demands. */
    @Test
    void escapesQuoteAndBackslash() {
        assertThat(Json.write(Map.of("k", "say \"hi\" \\ bye")))
            .isEqualTo("{\"k\":\"say \\\"hi\\\" \\\\ bye\"}");
    }

    /**
     * The escape this writer exists for. A label carrying a closing script tag must not be able to end
     * the element it is printed inside: the page would break, and worse, whatever followed the label
     * would arrive as markup.
     */
    @Test
    void aClosingScriptTagCannotSurvive() {
        String written = Json.write(Map.of("name", "</script><img src=x onerror=alert(1)>"));

        assertThat(written).doesNotContain("</script").doesNotContain("<");
        assertThat(written).contains("\\u003C");
    }

    /**
     * And the ampersand, which HTML leaves alone inside a script but XML does not: the same page served
     * as XML decodes the entity and hands the parser something else.
     */
    @Test
    void escapesTheAmpersand() {
        assertThat(Json.write(Map.of("k", "Tables & Networking")))
            .isEqualTo("{\"k\":\"Tables \\u0026 Networking\"}");
    }

    /** Control characters are written by their code, every one of them, with no short forms. */
    @Test
    void escapesControlCharacters() {
        String text = "a\nb\tc" + (char) 0x01;

        assertThat(Json.write(Map.of("k", text)))
            .isEqualTo("{\"k\":\"a\\u000Ab\\u0009c\\u0001\"}");
    }

    /**
     * The two separators that are legal in JSON and illegal in a javascript string literal. The block
     * is read as JSON, so this is not required — it is done so that a consumer who re-embeds the output
     * somewhere stricter does not discover the difference on their own data.
     */
    @Test
    void escapesTheLineAndParagraphSeparators() {
        String text = "a" + (char) 0x2028 + "b" + (char) 0x2029 + "c";

        assertThat(Json.write(Map.of("k", text)))
            .isEqualTo("{\"k\":\"a\\u2028b\\u2029c\"}");
    }

    /** Everything else printable travels as itself, including what is not in the basic plane. */
    @Test
    void leavesOrdinaryTextAlone() {
        assertThat(Json.write(Map.of("k", "Aloe vera 🌿 — 100%")))
            .isEqualTo("{\"k\":\"Aloe vera 🌿 — 100%\"}");
    }

    /** An empty string is a value, not an absence: the writer does not decide to drop it. */
    @Test
    void writesAnEmptyString() {
        assertThat(Json.write(Map.of("k", ""))).isEqualTo("{\"k\":\"\"}");
    }

    // what is refused, and how it says so

    /** A null argument is a mistake at the call site, and it is named there. */
    @Test
    void refusesANullArgument() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Json.write(null))
            .withMessage("Json.write(value): was not given");
    }

    /**
     * A null inside a contribution is a different thing: it is content, and the person who needs to
     * hear about it is whoever wrote that key. So the message carries the path to it, not just the fact.
     */
    @Test
    void refusesANullInsideAndNamesWhereItIs() {
        var item = new LinkedHashMap<String, Object>();
        item.put("name", null);

        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Json.write(Map.of("itemListElement", List.of(item))))
            .withMessageContaining("itemListElement[0].name");
    }

    /**
     * A type outside the accepted set is refused rather than guessed at. Widening the set later is not
     * a breaking change; having guessed wrong once is a page that means something else.
     */
    @Test
    void refusesATypeItDoesNotAccept() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Json.write(Map.of("date", LocalDate.of(2026, 3, 12))))
            .withMessageContaining("date")
            .withMessageContaining("LocalDate");
    }

    /** Including the near misses: a double looks like a number and is not one of ours. */
    @Test
    void refusesADouble() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Json.write(Map.of("rating", 4.5)))
            .withMessageContaining("rating")
            .withMessageContaining("Double");
    }

    /** A map whose keys are not strings cannot become an object, and is refused as itself. */
    @Test
    void refusesAMapWithNonStringKeys() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Json.write(Map.of(1, "one")))
            .withMessageContaining("Integer");
    }

    /**
     * And the key that is not merely of the wrong type but absent: a hash map takes a null key, so the
     * writer meets one sooner or later, and "a key of type null" is what it has to say about it.
     */
    @Test
    void refusesAMapWithANullKey() {
        var withNullKey = new HashMap<Object, Object>();
        withNullKey.put(null, "orphan");

        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Json.write(withNullKey))
            .withMessageContaining("null");
    }

    /** The path is built through lists as well as maps, so a deep mistake is still findable. */
    @Test
    void namesThePathThroughNestedLists() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Json.write(Map.of("a", List.of(List.of(LocalDate.EPOCH)))))
            .withMessageContaining("a[0][0]");
    }

    // ——— the other door ——————————————————————————————————————————————————————————————————

    /**
     * The writer has two doors and they answer alike. One is asked to write; the other is asked only
     * whether it could, and is what {@code describes} calls when an element says something about
     * itself — so a contribution is refused by the factory that wrote it rather than by a page months
     * later. A door that judged differently from the one beside it would refuse at composition what
     * renders at midnight, or the other way about.
     */
    @Test
    void theDoorThatOnlyChecksJudgesTheSame() {
        assertThatCode(() -> Json.check(Map.of("@type", "Thing", "name", "Baobab"), "Descriptor.describes"))
            .doesNotThrowAnyException();

        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Json.check(Map.of("when", java.time.LocalDate.EPOCH), "Descriptor.describes"))
            .withMessageStartingWith("Descriptor.describes.when: structured data carries a LocalDate");
    }

    /** And it names where it is asked from, so two contributions on a page are told apart. */
    @Test
    void theCheckingDoorNamesWhereItWasAskedFrom() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Json.check(Map.of("x", 1.5), "MyCard.describes"))
            .withMessageStartingWith("MyCard.describes.x:");
    }

    // ——— what a graph may not be —————————————————————————————————————————————————————————

    /**
     * A graph that contains itself is refused, and refused as this kit refuses things.
     *
     * <p>Nothing stops a caller building one: a map is a map, and {@code describes} hands its node
     * here before it is copied, so the writer meets exactly what was passed. Walking it would recur
     * until the stack ends — and a {@code StackOverflowError} is not a refusal. It kills the thread it
     * lands on, says nothing about which element caused it, and is the one kind of failure this kit
     * promises never to hand over unnamed.
     */
    @Test
    void refusesAGraphThatContainsItself() {
        var itself = new java.util.LinkedHashMap<String, Object>();
        itself.put("@type", "Thing");
        itself.put("about", itself);

        assertThatExceptionOfType(MisuseException.class).isThrownBy(() -> Json.write(itself))
            .withMessageContaining("nested too deeply");
    }

    /** A list that holds itself is the same mistake, and gets the same answer. */
    @Test
    void refusesAListThatHoldsItself() {
        var itself = new java.util.ArrayList<Object>();
        itself.add("first");
        itself.add(itself);

        assertThatExceptionOfType(MisuseException.class).isThrownBy(() -> Json.write(itself))
            .withMessageContaining("nested too deeply");
    }

    /**
     * What is deep but finite is written, because a description may be nested and usually is. The
     * boundary is asked about from both sides: a limit nobody has stood on is a limit nobody has
     * measured, and the difference between the last depth that works and the first that does not is
     * the whole of what this number means.
     */
    @Test
    void writesSomethingProperlyNested() {
        assertThat(Json.write(nested(24)))
            .isEqualTo("{\"in\":".repeat(24) + "\"bottom\"" + "}".repeat(24));

        assertThatCode(() -> Json.write(nested(32)))
            .as("the deepest a description may go is written like any other").doesNotThrowAnyException();
        assertThatExceptionOfType(MisuseException.class).isThrownBy(() -> Json.write(nested(33)))
            .as("and one deeper is not")
            .withMessageContaining("nested too deeply")
            .satisfies(refusal -> assertThat(refusal.where())
                .as("named by the way down to it, which is how a graph that has no bottom is found")
                .isEqualTo(String.join(".", java.util.Collections.nCopies(33, "in"))));
    }

    /** A description of a description of … , that many times over, with a word at the bottom. */
    private static Object nested(int depth) {
        Object deep = "bottom";
        for (int i = 0; i < depth; i++) {
            deep = Map.of("in", deep);
        }
        return deep;
    }

    // ——— text beyond the basic plane —————————————————————————————————————————————————————

    /**
     * Text that needs more than one char to hold one character comes out whole. The escaping walks
     * chars rather than code points, which is right — every character it escapes is one char — but a
     * pair split in half would be text nobody can read, in a block a machine parses.
     */
    @Test
    void keepsWhatNeedsTwoCharsToHoldOneCharacter() {
        assertThat(Json.write(Map.of("name", "Баобаб 🌳 растёт")))
            .isEqualTo("{\"name\":\"Баобаб 🌳 растёт\"}");
    }
}