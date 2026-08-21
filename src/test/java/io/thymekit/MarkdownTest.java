/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    /** Links that leave the site say what they are; the site's own links keep their weight. */
    @Test
    void linkRel_marksOnlyWhatLeavesTheSite() {
        String md = "[out](https://spam.example/x) [up](HTTPS://other/y) [in](/ingredients/baobab) [here](#composition)";
        String html = renderer.toHtmlSafe(md, "ugc nofollow");
        assertThat(html).contains("<a href=\"https://spam.example/x\" rel=\"ugc nofollow\">out</a>")
            .contains("<a href=\"HTTPS://other/y\" rel=\"ugc nofollow\">up</a>")   // any case of the scheme
            .contains("<a href=\"/ingredients/baobab\">in</a>")
            .contains("<a href=\"#composition\">here</a>");

        // an authority without a scheme is somebody else's host too, and it is the shape spam takes
        assertThat(renderer.toHtmlSafe("[x](//evil.example/y)", "ugc nofollow"))
            .contains("<a href=\"//evil.example/y\" rel=\"ugc nofollow\">x</a>");
        assertThat(renderer.toHtmlSafe("[x](mailto:a@b)", "ugc nofollow")).contains("rel=\"ugc nofollow\"");
        assertThat(renderer.toHtmlSafe("[x](../sibling)", "ugc nofollow")).doesNotContain("rel=");   // still ours

        assertThat(renderer.toHtmlSafe(md, null)).doesNotContain("rel=");      // nothing said, nothing marked
        assertThat(renderer.toHtmlSafe(md)).doesNotContain("rel=");
        assertThat(renderer.toHtmlSafe(null, "ugc")).isEmpty();
        assertThat(renderer.toHtmlSafe("  ", "ugc")).isEmpty();
    }

    /**
     * The cache, driven by a real cache manager rather than by reading annotations: two texts must not
     * share an entry. A key written by parameter name would do exactly that in a jar compiled without
     * -parameters — every call landing on [null, null] and the second page showing the first text.
     */
    @Test
    void cache_keepsTextsApart_andBothEntryPointsShareOneEntry() {
        try (var ctx = new AnnotationConfigApplicationContext(CachedRenderer.class)) {
            MarkdownRenderer cached = ctx.getBean(MarkdownRenderer.class);
            assertThat(cached.toHtmlSafe("**first**")).contains("first");
            assertThat(cached.toHtmlSafe("**second**")).contains("second").doesNotContain("first");
            assertThat(cached.toHtmlSafe("[x](https://o/y)", "ugc")).contains("rel=\"ugc\"");
            assertThat(cached.toHtmlSafe("[x](https://o/y)")).doesNotContain("rel=");   // the policy is part of the key

            // both entry points reach the same entry: a hit hands back the very object that was stored
            assertThat(cached.toHtmlSafe("**first**")).isSameAs(cached.toHtmlSafe("**first**", null));
        }
    }

    @Configuration
    @EnableCaching
    static class CachedRenderer {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("markdown.htmlSafe");
        }

        @Bean
        MarkdownRenderer renderer() {
            return new MarkdownRenderer();
        }
    }

    /** A link to the site's own page keeps its address, and a dangerous scheme still loses it. */
    @Test
    void relativeLinks_survive_dangerousSchemesDoNot() {
        assertThat(renderer.toHtmlSafe("[in](/ingredients/baobab)")).contains("<a href=\"/ingredients/baobab\">in</a>");
        assertThat(renderer.toHtmlSafe("[here](#composition)")).contains("<a href=\"#composition\">here</a>");
        assertThat(renderer.toHtmlSafe("[up](../sibling)")).contains("<a href=\"../sibling\">up</a>");
        assertThat(renderer.toHtmlSafe("[mail](mailto:hi@shop)")).contains("href=\"mailto:hi@shop\"");
        assertThat(renderer.toHtmlSafe("[bad](javascript:alert(1))")).doesNotContain("javascript");
        assertThat(renderer.toHtmlSafe("[bad](data:text/html;base64,PHNjcmlwdD4=)")).doesNotContain("data:");
        assertThat(renderer.toHtmlSafe("![photo](/img/baobab.jpg)")).contains("<img src=\"/img/baobab.jpg\"");   // pictures too
    }
}
