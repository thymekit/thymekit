/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.expression.IExpressionObjectFactory;

/**
 * The registry entry behind {@code #md}: one name, and one object to hand out under it.
 *
 * <p>The name is the smallest and longest-lived promise the kit makes — every template ever written
 * against it says {@code #md}, and nothing but a page failing to render would notice if that changed.
 * A consumer who wants {@code #md} to mean something else declares their own dialect bean, which wins
 * over the kit's by {@code @ConditionalOnMissingBean}.
 *
 * <p>The object is built once here and handed out on every ask: it holds a renderer and nothing else,
 * so a second instance would have nothing to hold differently. The renderer comes from outside rather
 * than being built here, so whatever a container wrapped it in stays in the call path.
 */
public final class MarkdownExpressionObjectFactory implements IExpressionObjectFactory {

    private static final String MARKDOWN_EXPRESSION_OBJECT_NAME = "md";
    private static final Set<String> ALL_EXPRESSION_OBJECT_NAMES =
            Set.of(MARKDOWN_EXPRESSION_OBJECT_NAME);

    private final MarkdownExpressionObject markdown;

    public MarkdownExpressionObjectFactory(MarkdownRenderer markdownRenderer) {
        this.markdown = new MarkdownExpressionObject(markdownRenderer);
    }

    @Override
    public Set<String> getAllExpressionObjectNames() {
        return ALL_EXPRESSION_OBJECT_NAMES;
    }

    /** The one name this factory answers to; anything else is somebody else's entry, and gets nothing. */
    @Override
    public @Nullable Object buildObject(IExpressionContext context, String expressionObjectName) {
        if (MARKDOWN_EXPRESSION_OBJECT_NAME.equals(expressionObjectName)) {
            return markdown;
        }
        return null;
    }

    /**
     * The flag asks whether a context should keep the object it was handed for the rest of a render. It
     * should not: there is one object for the whole application, and remembering it costs a context more
     * than asking for it again. Nothing here is said about the html — that cache lives in the renderer,
     * keyed by the text, the link policy and the ceiling.
     */
    @Override
    public boolean isCacheable(String expressionObjectName) {
        return false;
    }
}
