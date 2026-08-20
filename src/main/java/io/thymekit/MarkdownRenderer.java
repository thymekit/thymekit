/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import java.util.List;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.annotation.Cacheable;

/**
 * Markdown to safe HTML, rendered into templates through {@code th:utext}.
 *
 * <p>Safety is layered. flexmark parses with HTML blocks disabled and inline HTML escaped, so nothing
 * authored as markup survives the parser; jsoup then cleans the output against a relaxed safelist. Two
 * independent layers, because one of them may be misconfigured some day.
 *
 * <p>{@code toHtmlSafe} is annotated {@code @Cacheable} keyed by the source text: with Spring Cache
 * enabled the result is cached and editing the text is a natural cache miss; without a cache manager
 * the annotation is a no-op. If a consumer post-processes the HTML with data outside the text (say,
 * resolving image ids), invalidating that cache is the consumer's business.
 *
 * @see MarkdownDialect for the {@code #md} template integration
 */
public class MarkdownRenderer {

    /**
     * Lines that consist only of whitespace are normalised to truly empty ones.
     *
     * <p>WYSIWYG editors keep "empty" lines as a space or a non-breaking space. CommonMark requires a
     * blank line to contain zero non-whitespace characters, and U+00A0 does not count as whitespace
     * there — so without this, {@code ---} after such a line turns the previous paragraph into a setext
     * heading instead of a rule, tables stay plain text, and separate paragraphs glue into one.
     */
    private static final Pattern WHITESPACE_ONLY_LINE =
        Pattern.compile("(?m)^[\\s\\u00A0]+$");

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final Safelist safelist;
    private final int maxHeadingLevel;

    /** By default authored headings start at h2: the page H1 belongs to the hero, not to content. */
    public MarkdownRenderer() {
        this(2);
    }

    /**
     * @param maxHeadingLevel ceiling for content headings (1..6): the topmost authored level is demoted
     *        to it and the rest shift by the same amount, so the structure of the text survives. 1 keeps
     *        the text as authored (pages written entirely in markdown).
     */
    public MarkdownRenderer(int maxHeadingLevel) {
        if (maxHeadingLevel < 1 || maxHeadingLevel > 6) {
            throw new IllegalArgumentException("maxHeadingLevel " + maxHeadingLevel + ": allowed range is 1..6");
        }
        this.maxHeadingLevel = maxHeadingLevel;
        // Parser and HtmlRenderer are immutable and thread-safe, so they are built once.
        //
        // HTML_BLOCK_PARSER=false keeps authored HTML blocks as text, ESCAPE_HTML=true escapes what is
        // left inline; soft breaks stay CommonMark default, since pasted markdown wraps lines by accident.
        MutableDataSet options = new MutableDataSet()
            .set(Parser.HTML_BLOCK_PARSER, false)
            .set(HtmlRenderer.ESCAPE_HTML, true)
            .set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                AutolinkExtension.create()));
        this.parser = Parser.builder(options).build();
        this.renderer = HtmlRenderer.builder(options).build();
        this.safelist = createSafelist();
    }

    /**
     * Converts markdown source into safe HTML.
     *
     * <p>Pipeline: flexmark Parser → HtmlRenderer → jsoup Safelist.relaxed().
     *
     * @param source markdown text; {@code null} or blank yields an empty string
     * @return sanitised HTML ready for {@code th:utext}
     */
    @Cacheable(value = "markdown.htmlSafe", key = "#source")
    public String toHtmlSafe(@Nullable String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String normalized = WHITESPACE_ONLY_LINE.matcher(source).replaceAll("");
        Node document = parser.parse(normalized);
        demoteHeadings(document);
        String unsafeHtml = renderer.render(document);
        String safeHtml = Jsoup.clean(unsafeHtml, safelist);
        return safeHtml;
    }

    /**
     * Demotes content headings: the topmost authored level becomes {@code maxHeadingLevel} and the rest
     * shift by the same amount, never past h6. Content does not declare the page H1 — the hero does.
     */
    private void demoteHeadings(Node document) {
        List<com.vladsch.flexmark.ast.Heading> headings = new java.util.ArrayList<>();
        Node n = document.getFirstChild();
        java.util.ArrayDeque<Node> stack = new java.util.ArrayDeque<>();
        if (n != null) {
            stack.push(n);
        }
        while (!stack.isEmpty()) {
            Node cur = stack.pop();
            if (cur instanceof com.vladsch.flexmark.ast.Heading h) {
                headings.add(h);
            }
            if (cur.getNext() != null) {
                stack.push(cur.getNext());
            }
            if (cur.getFirstChild() != null) {
                stack.push(cur.getFirstChild());
            }
        }
        if (headings.isEmpty()) {
            return;
        }
        int min = headings.stream().mapToInt(com.vladsch.flexmark.ast.Heading::getLevel).min().orElse(6);
        int shift = Math.max(0, maxHeadingLevel - min);
        if (shift == 0) {
            return;
        }
        for (com.vladsch.flexmark.ast.Heading h : headings) {
            h.setLevel(Math.min(6, h.getLevel() + shift));
        }
    }

    /**
     * Output-side safelist: {@code Safelist.relaxed()} plus {@code rel}/{@code target} on links,
     * loading hints on images, and {@code class} on {@code <code>} — flexmark puts the fenced-block
     * language there, and without it code blocks lose their highlighting.
     */
    private static Safelist createSafelist() {
        return Safelist.relaxed()
            .addAttributes("a", "rel", "target")
            .addAttributes("img", "loading", "decoding")
            .addAttributes("code", "class");
    }
}
