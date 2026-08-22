/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
 * <p>Both {@code toHtmlSafe} overloads are annotated {@code @Cacheable} and share one key: the source
 * text, the link policy and the ceiling of this renderer — everything a consumer is given to decide
 * with. The same text under two policies cannot come back with the wrong attributes, and two renderers
 * (a page written entirely in markdown, a section inside one) cannot hand each other their headings.
 * Both carry the annotation on purpose: a method calling its neighbour inside the same object would go
 * past the Spring proxy and lose the cache silently.
 *
 * <p>The ceiling stands for the renderer because it is the only thing that varies between two of them.
 * A subclass that changed what rendering means would break that, which is one more reason the class
 * says it is not meant to be overridden: two renderers that disagree about more than a ceiling need a
 * cache of their own, not this one.
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
 * <p>Open rather than final, and for one reason: Spring proxies it to cache, and a proxy needs
 * something to extend. Nothing here is meant to be overridden — the one thing a consumer changes is the
 * ceiling, and that is an argument to the constructor.
 *
 * @see MarkdownDialect for the {@code #md} template integration
 */
public class MarkdownRenderer {

    /** A line with nothing on it but space, in the wide sense that includes the non-breaking kind. */
    private static final Pattern WHITESPACE_ONLY_LINE = Pattern.compile("^[\\s\\u00A0]+$");

    /** The opening or closing line of a fenced code block, indented by up to three spaces. */
    private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");

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

    /** The ceiling this renderer places authored headings under; part of what its cache is keyed by. */
    public int maxHeadingLevel() {
        return maxHeadingLevel;
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
    @Cacheable(value = "markdown.htmlSafe", key = "{#p0, null, #root.target.maxHeadingLevel()}")
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
    @Cacheable(value = "markdown.htmlSafe", key = "{#p0, #p1, #root.target.maxHeadingLevel()}")
    public String toHtmlSafe(@Nullable String source, @Nullable String linkRel) {
        return render(source, linkRel);
    }

    /** The pipeline itself; both public methods are cached entry points into it. */
    private String render(@Nullable String source, @Nullable String linkRel) {
        if (source == null || source.isBlank()) {
            return "";
        }
        String normalized = blankTheLinesAnEditorLeft(source);
        Node document = parser.parse(normalized);
        demoteHeadings(document);
        String safeHtml = Jsoup.clean(renderer.render(document), safelist);
        if (linkRel == null) {
            return safeHtml;
        }
        // A second parse, and deliberately so: Jsoup.clean keeps a relative href alive, while a Cleaner
        // driven by hand over a parsed document drops it. The cost is paid only by text that carries a
        // link policy, and jsoup's own output settings are left alone so both paths serialise alike.
        Document marked = Jsoup.parseBodyFragment(safeHtml);
        markOutgoing(marked, linkRel);
        return marked.body().html();
    }

    /**
     * Lines that consist only of whitespace are made truly empty — outside a fenced code block, where
     * the spaces are the author's text and not a trace of their editor.
     *
     * <p>WYSIWYG editors keep an "empty" line as a space or a non-breaking space. CommonMark requires a
     * blank line to contain zero non-whitespace characters, and U+00A0 does not count as whitespace
     * there — so without this, {@code ---} after such a line turns the paragraph above it into a setext
     * heading instead of a rule, tables stay rows of pipes, and separate paragraphs glue into one.
     *
     * <p>Inside a fence nothing is touched: three spaces on a line of a code sample are three
     * characters somebody wrote. The one place this pass still changes what was written is an indented
     * code block — telling one from ordinary formatting needs the document parsed, and a correct parse
     * is what this pass exists to make possible.
     */
    private static String blankTheLinesAnEditorLeft(String source) {
        StringBuilder normalized = new StringBuilder(source.length());
        char fenceChar = 0;
        int fenceLength = 0;
        int at = 0;
        while (true) {
            int newline = source.indexOf('\n', at);
            int lineEnd = newline < 0 ? source.length() : newline;
            String line = source.substring(at, lineEnd);
            String bare = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;

            Matcher fence = FENCE.matcher(bare);
            if (fence.matches()) {
                String marker = fence.group(1);
                if (fenceChar == 0) {
                    fenceChar = marker.charAt(0);
                    fenceLength = marker.length();
                } else if (marker.charAt(0) == fenceChar && marker.length() >= fenceLength
                    && fence.group(2).isBlank()) {          // a closing fence carries no info string
                    fenceChar = 0;
                }
            }
            boolean theirs = fenceChar != 0 || !WHITESPACE_ONLY_LINE.matcher(bare).matches();
            normalized.append(theirs ? line : "");

            if (newline < 0) {
                break;
            }
            normalized.append('\n');
            at = newline + 1;
        }
        return normalized.toString();
    }

    /**
     * Puts the given rel on every link that leaves the site. Leaving means carrying a scheme
     * ({@code https://…}, and any other) or an authority ({@code //host/…}); a path of the site's own
     * ({@code /x}, {@code #x}, {@code ../x}) is left alone, because holding back the weight of your own
     * links is a wound self-inflicted. A protocol-relative address counts as outgoing: it is somebody
     * else's host written without a scheme, and in text a visitor wrote it is exactly the shape spam
     * takes to slip past a check for {@code http}.
     *
     * <p>The address is read as the parser left it. Flexmark hands over a link destination already
     * trimmed, so there is nothing here to strip — and a trim that no text can reach is a line nobody
     * has ever run.
     */
    private static void markOutgoing(Document doc, String linkRel) {
        // jsoup's Element, spelled in full: the kit has one of its own, and this file is not about it
        for (org.jsoup.nodes.Element a : doc.select("a[href]")) {
            String href = a.attr("href");
            if (href.startsWith("//") || SCHEME.matcher(href).find()) {
                a.attr("rel", linkRel);
            }
        }
    }

    /**
     * Demotes content headings: the topmost authored level becomes {@code maxHeadingLevel} and the rest
     * shift by the same amount. Content does not declare the page H1 — the hero does.
     *
     * <p>What the shift may not do is flatten the text. Two levels an author kept apart mean two depths,
     * and html stops at six — so the move is whatever fits under the deepest heading in the document, and
     * a text already using all six levels stays where it was written. The relative shape of the text is
     * what a level means in markdown, and it is what survives here; the ceiling is reached when there is
     * room for it.
     */
    private void demoteHeadings(Node document) {
        List<Heading> headings = new ArrayList<>();
        Node n = document.getFirstChild();
        ArrayDeque<Node> stack = new ArrayDeque<>();
        if (n != null) {
            stack.push(n);
        }
        while (!stack.isEmpty()) {
            Node cur = stack.pop();
            if (cur instanceof Heading h) {
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
        int topmost = headings.stream().mapToInt(Heading::getLevel).min().orElseThrow();
        int deepest = headings.stream().mapToInt(Heading::getLevel).max().orElseThrow();
        int shift = Math.max(0, Math.min(maxHeadingLevel - topmost, 6 - deepest));
        if (shift == 0) {
            return;
        }
        for (Heading h : headings) {
            h.setLevel(h.getLevel() + shift);
        }
    }

    /**
     * Output-side safelist: {@code Safelist.relaxed()} plus the two things markdown produces that the
     * list does not have — {@code class} on {@code <code>}, where flexmark puts the language of a fenced
     * block, and the {@code <hr>} of a thematic break. The clean is here to keep out what a browser must
     * not be shown, not to thin out what an author may write.
     *
     * <p>Nothing else is added, and two allowances that used to be here are gone: {@code rel} and
     * {@code target} on links, and the loading hints on images. Neither can arrive — an author writes no
     * markup, since it is escaped, and the {@code rel} this class puts on outgoing links is put on after
     * the clean, not before it. A permission that cannot be reached is not caution, it is a wider
     * surface for nothing; the day something here produces one of them, it comes back with the thing
     * that produces it.
     */
    private static Safelist createSafelist() {
        return Safelist.relaxed()
            .addTags("hr")                 // a rule between two parts of a text; relaxed has no such tag
            .preserveRelativeLinks(true)   // without it a link to your own site loses its href: jsoup
                                           // resolves relative addresses against a base document, and
                                           // here there is none. The protocol list still applies, so
                                           // javascript: and data: are dropped as before.
            .addAttributes("code", "class");
    }
}
