/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * What {@link MarkdownDialect} owes a template: {@code #md}, reachable from an expression and answering
 * the way the renderer answers.
 *
 * <p>This is the first place the whole chain can be seen at once — a dialect that hands out a factory
 * that hands out a bridge that asks a renderer, four objects because Thymeleaf's interfaces ask for
 * them in that order. Each was checked on its own; here they are checked as the one thing a page
 * actually uses, which is an expression in a template.
 */
class MarkdownDialectTest {

    private static SpringTemplateEngine engine() {
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new StringTemplateResolver());
        engine.addDialect(new MarkdownDialect(new MarkdownRenderer()));
        return engine;
    }

    private static String render(String template, String... variables) {
        var context = new Context();
        for (int i = 0; i < variables.length; i += 2) {
            context.setVariable(variables[i], variables[i + 1]);
        }
        return engine().process(template, context);
    }

    /** A template asks, and gets html: this is the whole point of the class. */
    @Test
    void aTemplateCanAskForMarkdown() {
        assertThat(render("<div th:utext=\"${#md.toHtmlSafe(text)}\"></div>", "text", "**bold**"))
            .isEqualTo("<div><p><strong>bold</strong></p></div>");
    }

    /**
     * And asks with a policy for the links of somebody else's text. The kit's own markdown adapter makes
     * exactly this call, with values that are null whenever a page has nothing written or no policy set
     * — and a null argument is where an overloaded call in an expression language goes wrong quietly, by
     * finding the other method. It finds this one.
     */
    @Test
    void andWithAPolicyForItsLinksEvenWhenThereIsNone() {
        String template = "<div th:utext=\"${#md.toHtmlSafe(text, rel)}\"></div>";

        assertThat(render(template, "text", "[out](https://example.com/x)", "rel", "ugc nofollow"))
            .contains("rel=\"ugc nofollow\"");
        assertThat(render(template, "text", "[out](https://example.com/x)", "rel", null))
            .contains("<a href=\"https://example.com/x\">out</a>").doesNotContain("rel=");
        assertThat(render(template, "text", null, "rel", null)).isEqualTo("<div></div>");
    }

    /**
     * What comes back may be printed unescaped, which is the reason {@code th:utext} appears next to
     * {@code #md} everywhere in this kit: markup an author wrote is text by the time it arrives.
     */
    @Test
    void whatComesBackIsSafeToPrintUnescaped() {
        assertThat(render("<div th:utext=\"${#md.toHtmlSafe(text)}\"></div>",
                "text", "<script>alert(1)</script> and [x](javascript:alert(1))"))
            .doesNotContain("<script").doesNotContain("javascript:")
            .contains("&lt;script");
    }

    /** One factory, handed out as it is, under a name that says whose dialect this is. */
    @Test
    void itHandsOutOneFactoryUnderItsOwnName() {
        var dialect = new MarkdownDialect(new MarkdownRenderer());

        assertThat(dialect.getName()).isEqualTo("thymekit-markdown");
        assertThat(dialect.getExpressionObjectFactory())
            .isInstanceOf(MarkdownExpressionObjectFactory.class)
            .isSameAs(dialect.getExpressionObjectFactory());
        assertThat(dialect.getExpressionObjectFactory().getAllExpressionObjectNames()).containsExactly("md");
    }

    /** A dialect without a renderer is refused where it is built, not where a page is rendered. */
    @Test
    void aRendererIsRequired() {
        assertThatThrownBy(() -> new MarkdownDialect(null))
            .isInstanceOf(MisuseException.class).hasMessageContaining("markdownRenderer");
    }
}
