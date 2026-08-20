/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The kit works right after the dependency is added; a consumer bean always wins; tidy is switchable. */
class ThymekitAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ThymekitAutoConfiguration.class));

    @Test
    void defaults_dialectsRegistered_withoutAnyConsumerConfiguration() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(MarkdownRenderer.class)
                .hasSingleBean(MarkdownDialect.class)
                .hasSingleBean(TidyDialect.class);
            assertThat(ctx.getBean(MarkdownDialect.class).getExpressionObjectFactory()
                .getAllExpressionObjectNames()).contains("md");
            assertThat(ctx.getBean(TidyDialect.class).getPostProcessors()).isNotEmpty();
        });
    }

    @Test
    void consumerBeanWins_autoConfigurationBacksOff() {
        runner.withUserConfiguration(OwnRenderer.class).run(ctx ->
            assertThat(ctx).hasSingleBean(MarkdownRenderer.class)
                .getBean(MarkdownRenderer.class).isSameAs(OwnRenderer.INSTANCE));
    }

    @Test
    void tidy_disabledByProperty_markdownStays() {
        runner.withPropertyValues("thymekit.tidy.enabled=false").run(ctx ->
            assertThat(ctx).doesNotHaveBean(TidyDialect.class).hasSingleBean(MarkdownDialect.class));
    }

    @Configuration
    static class OwnRenderer {
        static final MarkdownRenderer INSTANCE = new MarkdownRenderer(3);

        @Bean
        MarkdownRenderer markdownRenderer() {
            return INSTANCE;
        }
    }
}
