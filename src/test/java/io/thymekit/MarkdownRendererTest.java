/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What {@link MarkdownRenderer} owes a page: an author's text turned into markup a browser may show,
 * placed under the page rather than beside it, with the links of somebody else's text marked as such.
 *
 * <p>Three promises are the readme's own words and are pinned here as they are written there: nothing
 * authored as markup survives the parser (chapter "Safety"), the topmost level found in the text is
 * lowered to a ceiling and the rest move by the same amount so that "the relative shape of the text is
 * what survives", and the ceiling belongs to the renderer, which a consumer may replace.
 */
class MarkdownRendererTest {

    private final MarkdownRenderer md = new MarkdownRenderer();

    // ——— the text itself ————————————————————————————————————————————————————————————————

    /** Nothing written is nothing shown — not an empty paragraph, not a stray tag. */
    @Test
    void nothingToRenderIsNothing() {
        assertThat(md.toHtmlSafe(null)).isEmpty();
        assertThat(md.toHtmlSafe("")).isEmpty();
        assertThat(md.toHtmlSafe("   \n\t \n")).isEmpty();
        assertThat(md.toHtmlSafe(null, "ugc")).isEmpty();
        // an editor's "empty" page: a single non-breaking space, which java does not count as blank
        assertThat(md.toHtmlSafe("\u00a0")).isEmpty();
    }

    /** What markdown is for: a paragraph, emphasis, a list, a table, a fenced block that keeps its language. */
    @Test
    void whatAnAuthorWroteIsWhatComesOut() {
        assertThat(md.toHtmlSafe("Plain **bold** and *thin*."))
            .contains("<p>Plain <strong>bold</strong> and <em>thin</em>.</p>");
        assertThat(md.toHtmlSafe("- one\n- two")).contains("<ul>").contains("<li>one</li>");
        assertThat(md.toHtmlSafe("| a | b |\n|---|---|\n| 1 | 2 |")).contains("<table>").contains("<td>1</td>");
        assertThat(md.toHtmlSafe("```java\nclass A {}\n```"))
            .contains("<pre><code class=\"language-java\">class A {}");
        assertThat(md.toHtmlSafe("Write to https://example.com/x for more"))
            .contains("<a href=\"https://example.com/x\">https://example.com/x</a>");
    }

    /**
     * Markup an author wrote is text, not markup — the first of the two layers the readme promises. A
     * block of html stays a block of characters, and a tag inside a line is escaped where it stands.
     */
    @Test
    void markupAnAuthorWroteIsTextNotMarkup() {
        assertThat(md.toHtmlSafe("<div class=\"mine\">block</div>"))
            .doesNotContain("<div").contains("&lt;div");
        assertThat(md.toHtmlSafe("a <b>bold</b> word")).doesNotContain("<b>").contains("&lt;b&gt;");
        assertThat(md.toHtmlSafe("<script>alert(1)</script>")).doesNotContain("<script").contains("&lt;script");
    }

    /** And the second layer: what a link may be, and what an attribute may say. */
    @Test
    void whatComesOutIsWhatABrowserMayBeShown() {
        assertThat(md.toHtmlSafe("[go](javascript:alert(1))")).doesNotContain("javascript:");
        assertThat(md.toHtmlSafe("[go](data:text/html,x)")).doesNotContain("data:text/html");
        assertThat(md.toHtmlSafe("![pic](/img/x.png)")).contains("<img").contains("src=\"/img/x.png\"");
    }

    /** A link of the site's own keeps its address: there is no document here to resolve it against. */
    @Test
    void linksOfYourOwnKeepTheirAddress() {
        assertThat(md.toHtmlSafe("[here](/ingredients/baobab)")).contains("href=\"/ingredients/baobab\"");
        assertThat(md.toHtmlSafe("[here](#composition)")).contains("href=\"#composition\"");
        assertThat(md.toHtmlSafe("[here](../sibling)")).contains("href=\"../sibling\"");
    }

    /**
     * A line an editor left "empty" holds a space or a non-breaking space, and CommonMark counts neither
     * as blank. Without normalising them a rule turns the paragraph above it into a heading, and a table
     * stays a row of pipes.
     */
    @Test
    void aLineAnEditorLeftEmptyIsEmpty() {
        assertThat(md.toHtmlSafe("Paragraph\n \n---\nAfter")).doesNotContain("<h2");   // not a setext heading
        assertThat(md.toHtmlSafe("Paragraph\n\u00a0\n---\nAfter")).doesNotContain("<h2");
        assertThat(md.toHtmlSafe("Paragraph\n \n| a |\n|---|\n| 1 |")).contains("<table>");
    }

    /**
     * A rule between two parts of a text is a thing markdown has, so it is a thing the page gets. The
     * clean is there to drop what a browser must not be shown, not to thin out what an author may write.
     */
    @Test
    void aRuleBetweenTwoPartsSurvivesTheClean() {
        assertThat(md.toHtmlSafe("Paragraph\n\n---\n\nAfter")).contains("<hr").contains("<p>After</p>");
        assertThat(md.toHtmlSafe("***")).contains("<hr");
    }

    // ——— where the text sits under the page ——————————————————————————————————————————————

    /** Content does not declare the page title: the topmost authored level is lowered to the ceiling. */
    @Test
    void theTextIsPlacedUnderThePageAndNotBesideIt() {
        assertThat(md.toHtmlSafe("# Title\n\n## Under it")).contains("<h2>Title</h2>").contains("<h3>Under it</h3>");
        assertThat(md.toHtmlSafe("### Already deep")).contains("<h3>Already deep</h3>");   // never raised
        assertThat(new MarkdownRenderer(4).toHtmlSafe("# a\n\n## b")).contains("<h4>a</h4>").contains("<h5>b</h5>");
        assertThat(new MarkdownRenderer(1).toHtmlSafe("# a\n\n## b")).contains("<h1>a</h1>").contains("<h2>b</h2>");
    }

    /**
     * The relative shape of the text is what survives — the readme's words. A document using six levels
     * cannot be pushed down without two of them becoming one, so it is not pushed down that far: the
     * shift is whatever fits, and levels an author kept apart stay apart.
     */
    @Test
    void theShapeOfTheTextSurvivesTheMove() {
        String sixLevels = "# one\n\n## two\n\n### three\n\n#### four\n\n##### five\n\n###### six";
        String moved = md.toHtmlSafe(sixLevels);
        assertThat(moved).contains("<h1>one</h1>").contains("<h6>six</h6>");   // nothing fits, so nothing moves

        String fiveLevels = "# one\n\n## two\n\n### three\n\n#### four\n\n##### five";
        assertThat(md.toHtmlSafe(fiveLevels))
            .contains("<h2>one</h2>").contains("<h6>five</h6>")                // one step is all there is room for
            .doesNotContain("<h1>");
    }

    /**
     * A heading is a heading wherever it stands — quoted, or inside a list an author is building the
     * page out of. The move finds it there too, or one part of the text ends up at a depth of its own.
     */
    @Test
    void theCeilingFindsHeadingsWhereverTheyStand() {
        assertThat(md.toHtmlSafe("> # Quoted\n>\n> text"))
            .contains("<blockquote>").contains("<h2>Quoted</h2>").doesNotContain("<h1>");
        assertThat(md.toHtmlSafe("- # In a list")).contains("<li>").contains("<h2>In a list</h2>");
    }

    /** The ceiling is a level html has. */
    @Test
    void theCeilingIsALevelHtmlHas() {
        assertThatThrownBy(() -> new MarkdownRenderer(0)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1..6");
        assertThatThrownBy(() -> new MarkdownRenderer(7)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1..6");
        assertThat(new MarkdownRenderer(6).toHtmlSafe("# a")).contains("<h6>a</h6>");
    }

    // ——— whose text this is ——————————————————————————————————————————————————————————————

    /** Marked is what leaves the site, and only that. */
    @Test
    void onlyWhatLeavesTheSiteIsMarked() {
        String policy = "ugc nofollow";
        assertThat(md.toHtmlSafe("[out](https://example.com/x)", policy)).contains("rel=\"ugc nofollow\"");
        assertThat(md.toHtmlSafe("[out](//example.com/x)", policy)).contains("rel=\"ugc nofollow\"");
        assertThat(md.toHtmlSafe("[write](mailto:a@example.com)", policy)).contains("rel=\"ugc nofollow\"");
        assertThat(md.toHtmlSafe("[ours](/ingredients/baobab)", policy)).doesNotContain("rel=");
        assertThat(md.toHtmlSafe("[ours](#composition)", policy)).doesNotContain("rel=");
        assertThat(md.toHtmlSafe("[ours](../sibling)", policy)).doesNotContain("rel=");
    }

    /** No policy, no marks — and a link of your own keeps its address through the second pass as well. */
    @Test
    void withoutAPolicyNothingIsMarked() {
        assertThat(md.toHtmlSafe("[out](https://example.com/x)")).doesNotContain("rel=");
        assertThat(md.toHtmlSafe("[ours](/x)", "nofollow")).contains("href=\"/x\"");
    }

    // ——— the cache ———————————————————————————————————————————————————————————————————————

    /**
     * The cache holds the text together with everything that decides what the text becomes. Asked with
     * the same question it answers from memory; asked with a different one it does the work again.
     * Written against a real cache manager, because an assertion about an annotation is an assertion
     * about a spelling.
     */
    @Test
    void theCacheAnswersTheQuestionItWasAsked() {
        try (var context = new AnnotationConfigApplicationContext(Cached.class)) {
            MarkdownRenderer cached = context.getBean("plain", MarkdownRenderer.class);
            String text = "[out](https://example.com/x)";

            assertThat(cached.toHtmlSafe(text)).isEqualTo(cached.toHtmlSafe(text));
            assertThat(cached.toHtmlSafe(text, "ugc")).contains("rel=\"ugc\"");
            assertThat(cached.toHtmlSafe(text)).doesNotContain("rel=");          // the policy is part of the question
            assertThat(cached.toHtmlSafe(text, null)).doesNotContain("rel=");    // and null is the same question

            assertThat(cached.toHtmlSafe("# Title")).contains("<h2>Title</h2>");
        }
    }

    /**
     * And so is the ceiling. Two renderers are two answers to the same text — a page written entirely in
     * markdown and a section inside one — and a cache that cannot tell them apart hands the second the
     * headings of the first.
     */
    @Test
    void theCacheTellsOneRendererFromAnother() {
        try (var context = new AnnotationConfigApplicationContext(Cached.class)) {
            MarkdownRenderer underAPage = context.getBean("plain", MarkdownRenderer.class);
            MarkdownRenderer wholePage = context.getBean("asAuthored", MarkdownRenderer.class);

            assertThat(underAPage.toHtmlSafe("# Title")).contains("<h2>Title</h2>");
            assertThat(wholePage.toHtmlSafe("# Title")).contains("<h1>Title</h1>");
        }
    }

    @Configuration
    @EnableCaching
    static class Cached {
        @Bean
        ConcurrentMapCacheManager cacheManager() {
            return new ConcurrentMapCacheManager("markdown.htmlSafe");
        }

        @Bean
        MarkdownRenderer plain() {
            return new MarkdownRenderer();
        }

        @Bean
        MarkdownRenderer asAuthored() {
            return new MarkdownRenderer(1);
        }
    }
}
