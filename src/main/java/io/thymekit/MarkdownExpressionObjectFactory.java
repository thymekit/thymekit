/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Set;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.expression.IExpressionObjectFactory;

/**
 * Registers {@code #md} as an expression object name. Not cacheable at the Thymeleaf level: caching
 * happens inside the renderer, keyed by the source text.
 */
public class MarkdownExpressionObjectFactory implements IExpressionObjectFactory {

    private static final String MARKDOWN_EXPRESSION_OBJECT_NAME = "md";
    private static final Set<String> ALL_EXPRESSION_OBJECT_NAMES =
            Set.of(MARKDOWN_EXPRESSION_OBJECT_NAME);

    private final MarkdownRenderer markdownRenderer;

    public MarkdownExpressionObjectFactory(MarkdownRenderer markdownRenderer) {
        this.markdownRenderer = markdownRenderer;
    }

    @Override
    public Set<String> getAllExpressionObjectNames() {
        return ALL_EXPRESSION_OBJECT_NAMES;
    }

    @Override
    public Object buildObject(IExpressionContext context, String expressionObjectName) {
        if (MARKDOWN_EXPRESSION_OBJECT_NAME.equals(expressionObjectName)) {
            return new MarkdownExpressionObject(markdownRenderer);
        }
        return null;
    }

    @Override
    public boolean isCacheable(String expressionObjectName) {
        return false;
    }
}
