/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IPostProcessorDialect;
import org.thymeleaf.engine.AbstractTemplateHandler;
import org.thymeleaf.model.IText;
import org.thymeleaf.postprocessor.IPostProcessor;
import org.thymeleaf.postprocessor.PostProcessor;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * What {@link TidyDialect} owes an engine: one post-processor, put in the right place, for the one
 * template mode where whitespace is formatting rather than content.
 *
 * <p>Where its handler is checked by {@code WhitespaceHandlerTest}, this file checks the wiring — the
 * mode it applies to, the mode it keeps out of, and the place it takes among other dialects. That last
 * one is a promise to a consumer: a post-processor of theirs sees either the template or the page,
 * depending on which side of this dialect it sits, and which side that is has to be knowable.
 */
class TidyDialectTest {

    private static SpringTemplateEngine engine(TemplateMode mode, org.thymeleaf.dialect.IDialect... also) {
        var resolver = new StringTemplateResolver();
        resolver.setTemplateMode(mode);
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.addDialect(new TidyDialect());
        for (var dialect : also) {
            engine.addDialect(dialect);
        }
        return engine;
    }

    /** In HTML, whitespace between tags is how a template was formatted, and it is tidied. */
    @Test
    void inHtmlTheFormattingIsTidiedAway() {
        assertThat(engine(TemplateMode.HTML).process("<div>\n    <span>a</span>\n</div>", new Context()))
            .isEqualTo("<div>\n  <span>a</span>\n</div>");
    }

    /**
     * And nowhere else. In a text template or a script, whitespace is not formatting but content — a
     * letter, a csv, a block of javascript — so those pass through with every character they had.
     */
    @Test
    void everywhereElseTheTextIsContentAndIsLeftAlone() {
        String letter = "line one\n    line two\n";
        assertThat(engine(TemplateMode.TEXT).process(letter, new Context())).isEqualTo(letter);

        String script = "function f() {\n    return 1;\n}\n";
        assertThat(engine(TemplateMode.JAVASCRIPT).process(script, new Context())).isEqualTo(script);
    }

    /**
     * One post-processor, for HTML, the kit's own handler — and the two places where a precedence is
     * declared say the same thing. The chain is ordered by the processor's number, the dialect's orders
     * dialects, and one decision written twice is a decision that can drift.
     */
    @Test
    void itRegistersOneHandlerForHtmlAtOnePlaceInTheChain() {
        TidyDialect dialect = new TidyDialect();
        Set<IPostProcessor> processors = dialect.getPostProcessors();
        assertThat(processors).singleElement().satisfies(processor -> {
            assertThat(processor.getTemplateMode()).isEqualTo(TemplateMode.HTML);
            assertThat(processor.getHandlerClass()).isEqualTo(WhitespaceHandler.class);
            assertThat(processor.getPrecedence()).isEqualTo(TidyDialect.PRECEDENCE);
        });
        assertThat(dialect.getDialectPostProcessorPrecedence()).isEqualTo(TidyDialect.PRECEDENCE);
    }

    /** A dialect is known by its name — in an engine's configuration and in the error it prints. */
    @Test
    void theDialectSaysWhatItIs() {
        assertThat(new TidyDialect().getName()).isEqualTo("thymekit-tidy");
    }

    /**
     * The place it takes: a post-processor with a lower precedence sees the template as it was written,
     * one with a higher precedence sees the page as it will be served. Both are useful and the choice is
     * the consumer's, so the number this dialect declares has to mean something — and it does.
     */
    @Test
    void whatComesBeforeSeesTheTemplateAndWhatComesAfterSeesThePage() {
        assertThat(whatASpyAtPrecedenceSees(TidyDialect.PRECEDENCE - 500))
            .as("before the tidying: the indentation of the template")
            .contains("[\\n    ]");
        assertThat(whatASpyAtPrecedenceSees(TidyDialect.PRECEDENCE + 1000))
            .as("after the tidying: the indentation of the page")
            .contains("[\\n  ]");
    }

    private static String whatASpyAtPrecedenceSees(int precedence) {
        SEEN.set(new StringBuilder());
        engine(TemplateMode.HTML, new SpyDialect(precedence))
            .process("<div>\n    <span>a</span>\n</div>", new Context());
        return SEEN.get().toString();
    }

    private static final AtomicReference<StringBuilder> SEEN = new AtomicReference<>(new StringBuilder());

    /** A consumer's post-processor, writing down every text node it is handed. */
    public static final class Spy extends AbstractTemplateHandler {
        @Override
        public void handleText(IText text) {
            SEEN.get().append("[").append(text.getText().replace("\n", "\\n")).append("]");
            super.handleText(text);
        }
    }

    private static final class SpyDialect extends AbstractDialect implements IPostProcessorDialect {
        private final int precedence;

        private SpyDialect(int precedence) {
            super("spy");
            this.precedence = precedence;
        }

        @Override
        public int getDialectPostProcessorPrecedence() {
            return precedence;
        }

        @Override
        public Set<IPostProcessor> getPostProcessors() {
            return Set.of(new PostProcessor(TemplateMode.HTML, Spy.class, precedence));
        }
    }
}
