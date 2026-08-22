/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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
     * And a line inside a code sample is not an editor's leftover but three characters somebody wrote.
     * The pass that empties "blank" lines stops at a fence, whichever of the two markers opened it, and
     * a fence nobody closed runs to the end of the text — which is what a parser makes of it too.
     */
    @Test
    void insideAFenceTheSpacesBelongToTheAuthor() {
        assertThat(md.toHtmlSafe("```\nline one\n   \nline three\n```"))
            .contains("<code>line one\n   \nline three\n</code>");
        assertThat(md.toHtmlSafe("~~~\nline one\n  \nline three\n~~~"))
            .contains("<code>line one\n  \nline three\n</code>");
        assertThat(md.toHtmlSafe("```\nopen and never closed\n   \nstill code"))
            .contains("<code>open and never closed\n   \nstill code</code>");
        // and a marker of the other kind does not close what it did not open
        assertThat(md.toHtmlSafe("```\ncode\n~~~\n   \nmore code\n```"))
            .contains("~~~\n   \nmore code");
    }

    /**
     * A fence closes only on its own terms: the same marker, at least as long, and nothing written after
     * it. Anything else is a line of the code sample, spaces and all.
     */
    @Test
    void aFenceClosesOnlyOnItsOwnTerms() {
        assertThat(md.toHtmlSafe("````\ncode\n```\n   \nstill code\n````"))
            .as("three backticks do not close four").contains("```\n   \nstill code");
        assertThat(md.toHtmlSafe("```\ncode\n``` java\n   \nstill code\n```"))
            .as("a closing fence carries no info string").contains("``` java\n   \nstill code");
        // once it has closed, the text after it is text again — shown with the line an editor really
        // leaves, a non-breaking space, since a line of ordinary spaces is blank to a parser anyway
        assertThat(md.toHtmlSafe("```\ncode\n```\nParagraph\n\u00a0\n---\nAfter"))
            .contains("<code>code\n</code>").contains("<hr").doesNotContain("<h2");
    }

    /**
     * Text arrives as it was stored, which is not always as it was typed: a line ending kept by a machine
     * that ends lines with two characters, or a first line that is empty because a form put it there.
     */
    @Test
    void textArrivesAsItWasStored() {
        assertThat(md.toHtmlSafe("Paragraph\r\n \r\n---\r\nAfter"))
            .as("carriage returns and all").doesNotContain("<h2").contains("<hr");
        assertThat(md.toHtmlSafe("\nAlpha"))
            .as("a text that begins with an empty line is that text, once").isEqualTo("<p>Alpha</p>");
    }

    /**
     * The one place the pass still changes what was written: a code block made by indentation. Telling
     * one from ordinary formatting needs the document parsed, and a correct parse is the thing the pass
     * exists to make possible. Pinned rather than hidden — the day it matters, this is where it says so.
     */
    @Test
    void insideAnIndentedCodeBlockTheSpacesAreStillLost() {
        assertThat(md.toHtmlSafe("text\n\n    code one\n       \n    code three\n"))
            .contains("<code>code one\n\ncode three\n</code>");
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

    /**
     * A policy changes attributes, not formatting. Text with a policy goes out through a second parse —
     * the only way jsoup keeps a relative address alive — and the class claims both ways write the same
     * page. Claimed, and now held: a text with nothing to mark comes out identical either way.
     */
    @Test
    void bothWaysOutOfThePipelineWriteTheSamePage() {
        String rich = """
            | a | b |
            |---|---|
            | 1 | 2 |

            > a quotation

            ```java
            class A {}
            ```

            - one
            - two

            ![picture](/i.png)
            """;
        assertThat(md.toHtmlSafe(rich, "ugc nofollow")).isEqualTo(md.toHtmlSafe(rich));
    }

    // ——— the cache ———————————————————————————————————————————————————————————————————————

    /**
     * The answer is filed under the whole question: the text, the policy, and the ceiling of the
     * renderer that was asked. Written against a real cache manager and against the entry itself,
     * because a test that only calls twice and compares proves that rendering is a function — which it
     * would be with no cache at all. This project has already met the other kind of defect once, when a
     * key written by parameter name evaluated to null and every page showed the text of the first.
     */
    @Test
    void theAnswerIsFiledUnderTheWholeQuestion() {
        try (var context = new AnnotationConfigApplicationContext(Cached.class)) {
            MarkdownRenderer underAPage = context.getBean("plain", MarkdownRenderer.class);
            Cache entries = context.getBean(CacheManager.class).getCache("markdown.htmlSafe");

            String withoutAPolicy = underAPage.toHtmlSafe("[out](https://example.com/x)");
            String withOne = underAPage.toHtmlSafe("[out](https://example.com/x)", "ugc");

            assertThat(entries.get(Arrays.asList("[out](https://example.com/x)", null, 2)))
                .as("filed under the text, no policy, and a ceiling of two")
                .isNotNull().extracting(Cache.ValueWrapper::get).isEqualTo(withoutAPolicy);
            assertThat(entries.get(Arrays.asList("[out](https://example.com/x)", "ugc", 2)))
                .as("and the same text under a policy is another question")
                .isNotNull().extracting(Cache.ValueWrapper::get).isEqualTo(withOne);
            assertThat(withoutAPolicy).doesNotContain("rel=");
            assertThat(withOne).contains("rel=\"ugc\"");
        }
    }

    /**
     * And a second ask is answered from memory rather than done again. Shown by answering it wrongly on
     * purpose: an entry nobody could have rendered is put in the cache, and the renderer hands it back.
     * Nothing else can produce that string, so nothing else can make this pass.
     */
    @Test
    void aSecondAskIsAnsweredFromMemory() {
        try (var context = new AnnotationConfigApplicationContext(Cached.class)) {
            MarkdownRenderer underAPage = context.getBean("plain", MarkdownRenderer.class);
            Cache entries = context.getBean(CacheManager.class).getCache("markdown.htmlSafe");

            underAPage.toHtmlSafe("**bold**");
            entries.put(Arrays.asList("**bold**", null, 2), "<p>remembered, not rendered</p>");

            assertThat(underAPage.toHtmlSafe("**bold**")).isEqualTo("<p>remembered, not rendered</p>");
        }
    }

    /**
     * Two renderers are two answers to the same text — a page written entirely in markdown and a section
     * inside one — and a cache that cannot tell them apart hands the second the headings of the first.
     * Their questions differ in the ceiling, and so do their entries.
     */
    @Test
    void theCacheTellsOneRendererFromAnother() {
        try (var context = new AnnotationConfigApplicationContext(Cached.class)) {
            MarkdownRenderer underAPage = context.getBean("plain", MarkdownRenderer.class);
            MarkdownRenderer wholePage = context.getBean("asAuthored", MarkdownRenderer.class);
            Cache entries = context.getBean(CacheManager.class).getCache("markdown.htmlSafe");

            assertThat(underAPage.toHtmlSafe("# Title")).contains("<h2>Title</h2>");
            assertThat(wholePage.toHtmlSafe("# Title")).contains("<h1>Title</h1>");

            assertThat(entries.get(Arrays.asList("# Title", null, 2))).isNotNull()
                .extracting(Cache.ValueWrapper::get).asString().contains("<h2>");
            assertThat(entries.get(Arrays.asList("# Title", null, 1))).isNotNull()
                .extracting(Cache.ValueWrapper::get).asString().contains("<h1>");
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
