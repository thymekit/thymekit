/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import org.jspecify.annotations.Nullable;

/**
 * The object behind {@code #md} in templates: {@code <div th:utext="${#md.toHtmlSafe(text)}">}.
 *
 * <p>Two methods wide, and deliberately. A template could have been handed the renderer itself, and
 * then the surface a page is written against would be whatever the renderer happens to hold — today a
 * ceiling to read, tomorrow whatever else it grows. What crosses this bridge is the text and what the
 * links of that text say about themselves; the rest stays on the other side.
 *
 * <p>Methods are instance methods because Thymeleaf calls them reflectively, and the renderer is
 * injected rather than created so that a Spring proxy — and with it {@code @Cacheable} — stays in the
 * call path. Final, unlike the renderer: nothing proxies this one, so nothing needs to extend it.
 *
 * <p>Both arguments may be absent, because a page carries absences: nothing written yet, no policy set.
 * They cross as they are — what an absence means is the renderer's to decide, and it decides the same
 * way for a template as for java.
 *
 * <p>One of these is built and handed to every template of every request, so it holds a renderer and
 * nothing else, and holds it finally. A field here that changed would be two requests writing over
 * each other — the kind of race nobody reproduces and everybody blames on the renderer.
 */
public final class MarkdownExpressionObject {

    private final MarkdownRenderer markdownRenderer;

    public MarkdownExpressionObject(MarkdownRenderer markdownRenderer) {
        this.markdownRenderer = Guards.required(markdownRenderer, "MarkdownExpressionObject(markdownRenderer)");
    }

    /**
     * Converts markdown source into safe HTML.
     *
     * @param source markdown text; {@code null} or blank yields an empty string
     * @return sanitised HTML
     * @see MarkdownRenderer#toHtmlSafe(String)
     */
    public String toHtmlSafe(@Nullable String source) {
        return markdownRenderer.toHtmlSafe(source);
    }

    /**
     * Converts markdown source into safe HTML, marking the links that leave the site.
     *
     * @param source markdown text; {@code null} or blank yields an empty string
     * @param linkRel value for the {@code rel} attribute of outgoing links, written as it is given;
     *        {@code null} marks nothing. {@code Rel.tokens(Rel.of(...))} is the same string with a
     *        misspelling made impossible, for a page composed in java rather than in a template
     * @return sanitised HTML
     * @see MarkdownRenderer#toHtmlSafe(String, String)
     */
    public String toHtmlSafe(@Nullable String source, @Nullable String linkRel) {
        return markdownRenderer.toHtmlSafe(source, linkRel);
    }
}
