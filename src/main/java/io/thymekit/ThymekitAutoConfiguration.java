/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.thymeleaf.ITemplateEngine;

/**
 * Makes the kit work as promised right after the dependency is added, with no configuration on the
 * consumer's side: the markdown renderer, its {@code #md} dialect and the tidy-render dialect are
 * registered here, and Spring Boot feeds dialect beans into its template engine by itself.
 *
 * <p>Every bean is {@code @ConditionalOnMissingBean} — declare your own and yours wins. Tidy rendering
 * can be switched off with {@code thymekit.tidy.enabled=false}, after which the output carries the
 * formatting of the templates, like plain Thymeleaf.
 */
@AutoConfiguration
@ConditionalOnClass(ITemplateEngine.class)
public class ThymekitAutoConfiguration {

    /** Markdown to safe HTML; cached when the consumer has Spring Cache enabled. */
    @Bean
    @ConditionalOnMissingBean
    public MarkdownRenderer thymekitMarkdownRenderer() {
        return new MarkdownRenderer();
    }

    /** The {@code #md} expression object for templates. */
    @Bean
    @ConditionalOnMissingBean
    public MarkdownDialect thymekitMarkdownDialect(MarkdownRenderer markdownRenderer) {
        return new MarkdownDialect(markdownRenderer);
    }

    /** Tidy rendering: output without traces of template formatting. */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "thymekit", name = "tidy.enabled", matchIfMissing = true)
    public TidyDialect thymekitTidyDialect() {
        return new TidyDialect();
    }
}
