/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

/**
 * Thymeleaf dialect exposing {@link MarkdownExpressionObject} as the {@code #md} expression object:
 * {@code <div th:utext="${#md.toHtmlSafe(text)}">}. The renderer already sanitises its output, which
 * is what makes {@code th:utext} safe here.
 */
public class MarkdownDialect extends AbstractDialect implements IExpressionObjectDialect {

    private final MarkdownExpressionObjectFactory factory;

    public MarkdownDialect(MarkdownRenderer markdownRenderer) {
        super("markdown");
        this.factory = new MarkdownExpressionObjectFactory(markdownRenderer);
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return factory;
    }
}
