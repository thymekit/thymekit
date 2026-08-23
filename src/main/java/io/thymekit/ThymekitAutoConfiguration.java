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
 * consumer's side: the markdown renderer, the dialect that puts {@code #md} into templates and the
 * tidy-render dialect are registered here, and Spring Boot feeds dialect beans into its template engine
 * by itself.
 *
 * <p>Nothing at all is registered where there is no template engine to register it for
 * ({@code @ConditionalOnClass}): a project that took the kit for its java and renders no pages gets no
 * beans and no Thymeleaf.
 *
 * <p>Every bean is {@code @ConditionalOnMissingBean} — declare your own and yours wins, which is how a
 * consumer changes the heading ceiling of the renderer or the meaning of {@code #md}. Tidy rendering can
 * be switched off with {@code thymekit.tidy.enabled=false}, after which the output carries the
 * formatting of the templates, like plain Thymeleaf. That switch is the only one the kit has, and it is
 * described in {@code META-INF/additional-spring-configuration-metadata.json} so that it is spelled out
 * where a consumer types it rather than only here.
 *
 * <p>Final: {@code @AutoConfiguration} carries {@code proxyBeanMethods = false}, so no container
 * subclasses this — and a bean method calling its neighbour would get a plain call rather than the
 * container's, which is why they do not call each other.
 */
@AutoConfiguration
@ConditionalOnClass(ITemplateEngine.class)
public final class ThymekitAutoConfiguration {

    /** Markdown to safe HTML; cached when the consumer has Spring Cache enabled. */
    @Bean
    @ConditionalOnMissingBean
    public MarkdownRenderer thymekitMarkdownRenderer() {
        return new MarkdownRenderer();
    }

    /** The dialect that puts {@code #md} into templates. */
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
