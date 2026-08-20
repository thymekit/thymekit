/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

/**
 * The object behind {@code #md} in templates. Methods are instance methods because Thymeleaf calls them
 * reflectively, and the renderer is injected rather than created so that a Spring proxy (and with it
 * {@code @Cacheable}) stays in the call path.
 */
public class MarkdownExpressionObject {

    private final MarkdownRenderer markdownRenderer;

    public MarkdownExpressionObject(MarkdownRenderer markdownRenderer) {
        this.markdownRenderer = markdownRenderer;
    }

    /**
     * Converts markdown source into safe HTML.
     *
     * @param source markdown text; {@code null} or blank yields an empty string
     * @return sanitised HTML
     * @see MarkdownRenderer#toHtmlSafe(String)
     */
    public String toHtmlSafe(String source) {
        return markdownRenderer.toHtmlSafe(source);
    }
}
