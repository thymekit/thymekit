/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * What {@link MarkdownExpressionObject} owes a template: {@code #md}, and nothing else.
 *
 * <p>It is a bridge, and a bridge is judged by what crosses it and what does not. Two methods cross —
 * the text, and the text with a link policy. The renderer does not: a template that could reach the
 * renderer itself could reach everything it will ever grow, and the surface a page is written against
 * would then be whatever the internals happen to be. (What every object carries — {@code getClass} and
 * the rest — a template reaches on anything, and this class neither adds to that nor could take from
 * it.)
 */
class MarkdownExpressionObjectTest {

    /** A renderer that writes down how it was called; a consumer's is a Spring proxy, which is the point. */
    private static final class Spy extends MarkdownRenderer {
        private int calls;
        private String source = "never called";
        private String linkRel = "never called";

        @Override
        public String toHtmlSafe(String source) {
            calls++;
            this.source = source;
            this.linkRel = "not given";
            return "one argument";
        }

        @Override
        public String toHtmlSafe(String source, String linkRel) {
            calls++;
            this.source = source;
            this.linkRel = linkRel;
            return "two arguments";
        }
    }

    /**
     * The renderer it was handed is the renderer it asks. Injected rather than built here so that
     * whatever a consumer's container wrapped it in — a cache proxy, above all — stays in the call path.
     */
    @Test
    void itAsksTheRendererItWasGiven() {
        var spy = new Spy();
        var md = new MarkdownExpressionObject(spy);

        assertThat(md.toHtmlSafe("text")).isEqualTo("one argument");
        assertThat(spy.source).isEqualTo("text");
        assertThat(spy.linkRel).isEqualTo("not given");

        assertThat(md.toHtmlSafe("text", "ugc nofollow")).isEqualTo("two arguments");
        assertThat(spy.linkRel).isEqualTo("ugc nofollow");
        assertThat(spy.calls).isEqualTo(2);
    }

    /**
     * A template asks with whatever the page carries, and a page carries absences: {@code e['markdown']}
     * is null where nothing was written and {@code e['linkRel']} is null where no policy was set. Both
     * cross the bridge as they are — deciding what an absence means is the renderer's business.
     */
    @Test
    void anAbsenceCrossesTheBridgeAsAnAbsence() {
        var spy = new Spy();
        var md = new MarkdownExpressionObject(spy);

        md.toHtmlSafe(null);
        assertThat(spy.source).isNull();

        md.toHtmlSafe(null, null);
        assertThat(spy.linkRel).isNull();

        assertThat(new MarkdownExpressionObject(new MarkdownRenderer()).toHtmlSafe(null)).isEmpty();
        assertThat(new MarkdownExpressionObject(new MarkdownRenderer()).toHtmlSafe(null, "ugc")).isEmpty();
    }

    /** A bridge to nowhere is refused where it is built, not where a page is rendered. */
    @Test
    void aRendererIsRequired() {
        assertThatThrownBy(() -> new MarkdownExpressionObject(null))
            .isInstanceOf(MisuseException.class).hasMessageContaining("markdownRenderer");
    }

    /**
     * And what this class adds to the surface stays two methods. A template can also reach what every
     * object has — {@code getClass}, {@code toString} — and no arrangement here changes that; what it
     * cannot reach is the renderer and whatever the renderer grows later, a ceiling to read today and
     * something else tomorrow. Widening this is a decision somebody writes down, here.
     */
    @Test
    void whatThisClassAddsIsTwoMethods() {
        assertThat(Arrays.stream(MarkdownExpressionObject.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName).distinct())
            .containsExactly("toHtmlSafe");
        assertThat(Arrays.stream(MarkdownExpressionObject.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers())).count())
            .isEqualTo(2);
    }

    /**
     * And it is safe to be the only one. The factory builds this once and hands the same object to
     * every template of every request, so anything it held that changed would be two requests writing
     * over each other — a race nobody would reproduce and everybody would blame on the renderer.
     *
     * <p>It holds a renderer and nothing else, and holds it finally. That is the whole reason one
     * instance is enough, and it is worth a spec rather than a sentence, because the day somebody adds
     * a field here the sentence stays true-looking and this does not.
     */
    @Test
    void itHoldsNothingThatCouldChange() {
        assertThat(Arrays.stream(MarkdownExpressionObject.class.getDeclaredFields())
                // what coverage and mutation add to a class while they watch it is theirs, not ours
                .filter(field -> !field.isSynthetic()).toList())
            .as("one field, and it is the renderer")
            .singleElement()
            .satisfies(field -> {
                assertThat(Modifier.isFinal(field.getModifiers())).as("final").isTrue();
                assertThat(field.getType()).isEqualTo(MarkdownRenderer.class);
            });
    }
}