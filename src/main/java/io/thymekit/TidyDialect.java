/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.Set;
import org.thymeleaf.dialect.AbstractDialect;
import org.thymeleaf.dialect.IPostProcessorDialect;
import org.thymeleaf.postprocessor.IPostProcessor;
import org.thymeleaf.postprocessor.PostProcessor;
import org.thymeleaf.templatemode.TemplateMode;

/**
 * Tidy rendering: the HTML of a page should carry no trace of how the templates were formatted. This
 * post-processing dialect turns formatting whitespace between tags into a single newline indented by
 * nesting depth, and leaves {@code pre/textarea/script/style} alone.
 *
 * <p>Why it is needed: adapters are written to be read — indented, one condition per line — and
 * Thymeleaf, after removing its own tags, leaves their indentation behind as text nodes. On a page of a
 * hundred elements that is thousands of blank lines.
 *
 * <p>Why it is safe: whitespace is only ever collapsed where it already exists, never inserted, and a
 * run of whitespace in HTML is equivalent to one. Between inline elements the newline is kept precisely
 * because there the whitespace is significant.
 */
public final class TidyDialect extends AbstractDialect implements IPostProcessorDialect {

    /**
     * Two spaces per level. Not configurable: Thymeleaf instantiates the post-processor by class, so
     * there is nowhere to pass a setting through.
     */
    static final int INDENT = 2;

    public TidyDialect() {
        super("thymekit-tidy");
    }

    @Override
    public int getDialectPostProcessorPrecedence() {
        return 1000;
    }

    @Override
    public Set<IPostProcessor> getPostProcessors() {
        return Set.of(new PostProcessor(TemplateMode.HTML, WhitespaceHandler.class, 1000));
    }
}
