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
 * <p>Both {@code toHtmlSafe} overloads are annotated {@code @Cacheable} and share one key — the source
 * text together with the link policy, so the same text under two policies cannot come back with the
 * wrong attributes. Both carry the annotation on purpose: a method calling its neighbour inside the
 * same object would go past the Spring proxy and lose the cache silently.
 *
 * <p>The key names its arguments by position ({@code #p0}, {@code #p1}) rather than by name. A library
 * jar carries parameter names only if it was compiled with {@code -parameters}; where it was not, a key
 * written as {@code #source} evaluates to {@code null} for every call, every text lands on one entry,
 * and the second page rendered shows the text of the first. By position that cannot happen, whoever
 * compiles the consumer and however. With Spring Cache enabled
 * editing the text is a natural miss; without a cache manager the annotation is a no-op. If a consumer
 * post-processes the HTML with data outside the text (say, resolving image ids), invalidating that
 * cache is the consumer's business.
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

    /** An address that names its own scheme — {@code https:}, {@code mailto:}, anything — leaves this site. */
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");

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
     * <p>Pipeline: flexmark Parser → HtmlRenderer → jsoup Safelist.relaxed(), plus a pass that marks
     * outgoing links when a policy was given.
     *
     * @param source markdown text; {@code null} or blank yields an empty string
     * @return sanitised HTML ready for {@code th:utext}
     */
    @Cacheable(value = "markdown.htmlSafe", key = "{#p0, null}")
    public String toHtmlSafe(@Nullable String source) {
        return render(source, null);
    }

    /**
     * The same, with what the links of this text say about themselves — {@code "ugc nofollow"} for a
     * review, nothing for text your own editors wrote.
     *
     * <p>Marked are the links that leave the site: an address carrying a scheme ({@code https://…}) or
     * an authority ({@code //host/…}). A path of the site's own ({@code /x}, {@code #x}, {@code ../x})
     * is left alone, since holding back the weight of your own links is a wound self-inflicted. The one
     * case this cannot tell apart is a link written absolutely to your own site: it is marked with the
     * rest.
     *
     * @param source markdown text; {@code null} or blank yields an empty string
     * @param linkRel value for the {@code rel} attribute of outgoing links; {@code null} marks nothing
     */
    @Cacheable(value = "markdown.htmlSafe", key = "{#p0, #p1}")
    public String toHtmlSafe(@Nullable String source, @Nullable String linkRel) {
        return render(source, linkRel);
    }

    /** The pipeline itself; both public methods are cached entry points into it. */
    private String render(@Nullable String source, @Nullable String linkRel) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String normalized = WHITESPACE_ONLY_LINE.matcher(source).replaceAll("");
        Node document = parser.parse(normalized);
        demoteHeadings(document);
        String safeHtml = Jsoup.clean(renderer.render(document), safelist);
        if (linkRel == null) {
            return safeHtml;
        }
        // A second parse, and deliberately so: Jsoup.clean keeps a relative href alive, while a Cleaner
        // driven by hand over a parsed document drops it. The cost is paid only by text that carries a
        // link policy, and jsoup's own output settings are left alone so both paths serialise alike.
        org.jsoup.nodes.Document marked = Jsoup.parseBodyFragment(safeHtml);
        markOutgoing(marked, linkRel);
        return marked.body().html();
    }

    /**
     * Puts the given rel on every link that leaves the site. Leaving means carrying a scheme
     * ({@code https://…}, and any other) or an authority ({@code //host/…}); a path of the site's own
     * ({@code /x}, {@code #x}, {@code ../x}) is left alone, because holding back the weight of your own
     * links is a wound self-inflicted. A protocol-relative address counts as outgoing: it is somebody
     * else's host written without a scheme, and in text a visitor wrote it is exactly the shape spam
     * takes to slip past a check for {@code http}.
     */
    private static void markOutgoing(org.jsoup.nodes.Document doc, String linkRel) {
        for (org.jsoup.nodes.Element a : doc.select("a[href]")) {
            String href = a.attr("href").strip();
            if (href.startsWith("//") || SCHEME.matcher(href).find()) {
                a.attr("rel", linkRel);
            }
        }
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
            .preserveRelativeLinks(true)   // without it a link to your own site loses its href: jsoup
                                           // resolves relative addresses against a base document, and
                                           // here there is none. The protocol list still applies, so
                                           // javascript: and data: are dropped as before.
            .addAttributes("a", "rel", "target")
            .addAttributes("img", "loading", "decoding")
            .addAttributes("code", "class");
    }
}
