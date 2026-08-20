/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The #md chain end to end: renderer, expression object, factory, dialect. */
class MarkdownTest {

    private final MarkdownRenderer renderer = new MarkdownRenderer();

    @Test void toHtmlSafe_nullBlank_empty() {
        assertThat(renderer.toHtmlSafe(null)).isEmpty();
        assertThat(renderer.toHtmlSafe("   ")).isEmpty();
    }
    @Test void toHtmlSafe_fencedBlock_keepsLanguageClass_dropsOtherClasses() {   // documentation: java and css blocks stay distinguishable
        String html = renderer.toHtmlSafe("```java\nint x = 1;\n```\n\n```css\n:root { --a: 1; }\n```");
        assertThat(html).contains("<code class=\"language-java\">").contains("<code class=\"language-css\">");
        assertThat(renderer.toHtmlSafe("<p class=\"evil\">x</p>")).doesNotContain("<p class=");         // raw HTML is escaped, so the class never becomes a tag attribute
    }

    @Test void toHtmlSafe_headingTableAutolink_sanitizesScript() {
        String html = renderer.toHtmlSafe(
            "# Heading\n\n| a | b |\n|---|---|\n| 1 | 2 |\n\nhttps://example.com\n\n<script>bad</script>");
        assertThat(html).contains("<h2>").doesNotContain("<h1>").contains("<table>").contains("href");   // an authored # becomes h2 under the default ceiling
        assertThat(html).doesNotContain("<script>");
    }

    /** Content headings never claim the page H1: the topmost level meets the ceiling, the rest shift along. */
    @Test void toHtmlSafe_headingsDemotedToMaxLevel_structureKept() {
        assertThat(renderer.toHtmlSafe("# A\n\n## B\n\n### C")).contains("<h2>A</h2>").contains("<h3>B</h3>").contains("<h4>C</h4>");
        assertThat(renderer.toHtmlSafe("## B\n\n### C")).contains("<h2>B</h2>").contains("<h3>C</h3>");           // already below the ceiling, left as authored
        assertThat(renderer.toHtmlSafe("# A\n\n###### F")).contains("<h2>A</h2>").contains("<h6>F</h6>");          // never deeper than h6
        assertThat(new MarkdownRenderer(1).toHtmlSafe("# A")).contains("<h1>A</h1>");                              // ceiling of 1 demotes nothing
        assertThat(new MarkdownRenderer(3).toHtmlSafe("# A\n\n## B")).contains("<h3>A</h3>").contains("<h4>B</h4>");
        assertThat(renderer.toHtmlSafe("text without headings")).doesNotContain("<h");
        assertThat(new MarkdownRenderer(6).toHtmlSafe("# A\n\n## B")).contains("<h6>A</h6>").contains("<h6>B</h6>");   // a ceiling of 6 is legal
        assertThat(renderer.toHtmlSafe("> # In a quote")).contains("<blockquote>").contains("<h2>In a quote</h2>");         // nested headings are demoted too
        assertThatThrownBy(() -> new MarkdownRenderer(0)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1..6");
        assertThatThrownBy(() -> new MarkdownRenderer(7)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void expressionObject_delegates() {
        assertThat(new MarkdownExpressionObject(renderer).toHtmlSafe("**b**")).contains("<strong>");
    }
    @Test void factory_names_build_cacheable() {
        MarkdownExpressionObjectFactory f = new MarkdownExpressionObjectFactory(renderer);
        assertThat(f.getAllExpressionObjectNames()).contains("md");
        assertThat(f.buildObject(null, "md")).isInstanceOf(MarkdownExpressionObject.class);
        assertThat(f.buildObject(null, "unknown")).isNull();
        assertThat(f.isCacheable("md")).isFalse();
    }
    @Test void dialect_factory_and_name() {
        MarkdownDialect d = new MarkdownDialect(renderer);
        assertThat(d.getExpressionObjectFactory()).isInstanceOf(MarkdownExpressionObjectFactory.class);
        assertThat(d.getName()).isNotBlank();
    }
}
