/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IExpressionObjectDialect;
import org.thymeleaf.expression.IExpressionObjectFactory;

/**
 * What puts {@code #md} into your templates: {@code <div th:utext="${#md.toHtmlSafe(text)}">}.
 *
 * <p>A dialect is Thymeleaf's only way to add an expression object, and it adds one by handing over a
 * factory, which hands over the object itself. Four objects for two methods, and each layer is there
 * because an interface asks for it rather than because the kit wanted one — {@link MarkdownDialect}
 * for the engine, {@link MarkdownExpressionObjectFactory} for the name, {@link MarkdownExpressionObject}
 * for the template, {@link MarkdownRenderer} for the work.
 *
 * <p>{@code th:utext} rather than {@code th:text}, because the renderer sanitises what it returns: by
 * the time a template has it, markup an author wrote is text. That is what makes printing it unescaped
 * the right thing here and nowhere else.
 *
 * <p>Named for the kit, like the tidy dialect: a dialect's name is what an engine's configuration and
 * its errors call it, and "markdown" alone would be a claim on a word that belongs to nobody.
 *
 * <p>To make {@code #md} mean something else, declare a dialect bean of your own — the kit's is
 * {@code @ConditionalOnMissingBean} and steps aside.
 */
public final class MarkdownDialect extends AbstractDialect implements IExpressionObjectDialect {

    private final MarkdownExpressionObjectFactory factory;

    public MarkdownDialect(MarkdownRenderer markdownRenderer) {
        super("thymekit-markdown");
        this.factory = new MarkdownExpressionObjectFactory(markdownRenderer);
    }

    @Override
    public IExpressionObjectFactory getExpressionObjectFactory() {
        return factory;
    }
}
