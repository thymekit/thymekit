/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.ExpressionContext;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * What {@link MarkdownExpressionObjectFactory} owes an engine: one name, and the same object behind it
 * every time it is asked.
 *
 * <p>The name is the smallest and longest-lived promise the kit makes. Every template ever written
 * against this kit says {@code #md}; a factory that answered to something else would break all of them
 * at once, and no compiler would notice. So it is written down here rather than only in the templates
 * that depend on it.
 *
 * <p>What the object does once a template has it belongs to {@code MarkdownExpressionObjectTest}, and
 * what an engine does with the factory belongs to the dialect that registers it. This file is about the
 * registry entry itself.
 */
class MarkdownExpressionObjectFactoryTest {

    private final MarkdownExpressionObjectFactory factory =
        new MarkdownExpressionObjectFactory(new MarkdownRenderer());

    /** A context of the kind Thymeleaf hands in — real, because the package promises it will not be null. */
    private static IExpressionContext context() {
        return new ExpressionContext(new SpringTemplateEngine().getConfiguration());
    }

    /** One name, and it is the one every template says. */
    @Test
    void itRegistersTheNameEveryTemplateSays() {
        assertThat(factory.getAllExpressionObjectNames()).containsExactly("md");
    }

    /**
     * Asked for that name it hands out the bridge, and the same one however it is asked: one object for
     * the whole application, and the render it is asked from is no part of the answer.
     */
    @Test
    void askedForThatNameItHandsOutTheBridge() {
        Object first = factory.buildObject(context(), "md");
        Object fromAnotherRender = factory.buildObject(context(), "md");

        assertThat(first).isInstanceOf(MarkdownExpressionObject.class).isSameAs(fromAnotherRender);
    }

    /** Asked for anything else, it hands out nothing: the registry is not its to guess at. */
    @Test
    void askedForAnythingElseItHandsOutNothing() {
        assertThat(factory.buildObject(context(), "markdown")).isNull();
        assertThat(factory.buildObject(context(), "")).isNull();
    }

    /** The bridge it hands out speaks to the renderer the factory was given, proxy and all. */
    @Test
    void theBridgeItHandsOutSpeaksToTheRendererItWasGiven() {
        var renderer = new MarkdownRenderer(1);      // a ceiling of its own, so the answer is recognisable
        var withOwnRenderer = new MarkdownExpressionObjectFactory(renderer);

        var bridge = (MarkdownExpressionObject) withOwnRenderer.buildObject(context(), "md");
        assertThat(bridge.toHtmlSafe("# Title")).contains("<h1>Title</h1>");
    }

    /** A factory without a renderer is refused where it is built, not where a page is rendered. */
    @Test
    void aRendererIsRequired() {
        assertThatThrownBy(() -> new MarkdownExpressionObjectFactory(null))
            .isInstanceOf(MisuseException.class).hasMessageContaining("markdownRenderer");
    }

    /**
     * It asks an engine not to keep the object. Thymeleaf's flag is about holding the expression object
     * in a context for the rest of a render, not about the html — and since the same instance is handed
     * out on every ask, holding it would cost a context more than asking again. The answer is a
     * decision, so it is written down here; what the flag means is above, since the name alone has
     * misled this project once already.
     */
    @Test
    void itAsksAnEngineNotToKeepTheObject() {
        assertThat(factory.isCacheable("md")).isFalse();
        assertThat(factory.isCacheable("anything else")).isFalse();
    }
}
