/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.ExpressionContext;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * What {@link ThymekitAutoConfiguration} owes an application: everything the readme promises happens
 * without anybody typing anything.
 *
 * <p>Three promises, and two of them rest on Spring's behaviour rather than on the kit's code — that a
 * bean of the consumer's wins, and that a property removes one of ours. Reading the annotations is not
 * the same as knowing they hold, so a context is started here and asked.
 */
class ThymekitAutoConfigurationTest {

    private final ApplicationContextRunner application = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ThymekitAutoConfiguration.class));

    /** Add the dependency, and the kit is there: nothing to configure is the first thing it says. */
    @Test
    void addingTheDependencyIsAllThatIsNeeded() {
        application.run(context -> assertThat(context)
            .hasSingleBean(MarkdownRenderer.class)
            .hasSingleBean(MarkdownDialect.class)
            .hasSingleBean(TidyDialect.class));
    }

    /**
     * And the beans are the working things they claim to be, in the hands of an engine built the way
     * Boot builds one — from whatever dialects the context holds. This is the promise underneath the
     * other three: a page written by a consumer asks for markdown and gets it, tidied, having configured
     * nothing.
     */
    @Test
    void anEngineBuiltFromThoseBeansRendersAPage() {
        application.run(context -> {
            var engine = new SpringTemplateEngine();
            engine.setTemplateResolver(new org.thymeleaf.templateresolver.StringTemplateResolver());
            context.getBeansOfType(org.thymeleaf.dialect.IDialect.class).values().forEach(engine::addDialect);

            var variables = new org.thymeleaf.context.Context();
            variables.setVariable("text", "**bold**");
            String page = engine.process(
                "<section>\n    <div th:utext=\"${#md.toHtmlSafe(text)}\"></div>\n</section>", variables);

            assertThat(page).isEqualTo("<section>\n  <div><p><strong>bold</strong></p></div>\n</section>");
        });
    }

    /** And a bean of your own wins — each of the three, one at a time. */
    @Test
    void aBeanOfYourOwnWins() {
        application.withUserConfiguration(OwnRenderer.class).run(context -> {
            assertThat(context).hasSingleBean(MarkdownRenderer.class);
            assertThat(context.getBean(MarkdownRenderer.class).maxHeadingLevel()).isEqualTo(1);
        });
        application.withUserConfiguration(OwnMarkdownDialect.class).run(context -> {
            assertThat(context).hasSingleBean(MarkdownDialect.class);
            assertThat(context.getBean(MarkdownDialect.class)).isSameAs(OwnMarkdownDialect.MINE);
        });
        application.withUserConfiguration(OwnTidyDialect.class).run(context -> {
            assertThat(context).hasSingleBean(TidyDialect.class);
            assertThat(context.getBean(TidyDialect.class)).isSameAs(OwnTidyDialect.MINE);
        });
    }

    /** The one switch the kit has removes the tidying, and nothing else. */
    @Test
    void tidyingCanBeSwitchedOffAndOnlyThat() {
        application.withPropertyValues("thymekit.tidy.enabled=false").run(context -> assertThat(context)
            .doesNotHaveBean(TidyDialect.class)
            .hasSingleBean(MarkdownRenderer.class)
            .hasSingleBean(MarkdownDialect.class));

        application.withPropertyValues("thymekit.tidy.enabled=true")
            .run(context -> assertThat(context).hasSingleBean(TidyDialect.class));
    }

    /**
     * And where there is no template engine there is nothing to configure for: a project that took the
     * kit for its java and renders nothing gets no beans, no dialects and no Thymeleaf.
     */
    @Test
    void withoutATemplateEngineNothingIsRegistered() {
        application.withClassLoader(new FilteredClassLoader(ITemplateEngine.class))
            .run(context -> assertThat(context)
                .doesNotHaveBean(MarkdownRenderer.class)
                .doesNotHaveBean(MarkdownDialect.class)
                .doesNotHaveBean(TidyDialect.class));
    }

    /** The dialect the kit registers is wired to the renderer the kit registered, not to one of its own. */
    @Test
    void theDialectSpeaksToTheRendererOfTheApplication() {
        application.withUserConfiguration(OwnRenderer.class).run(context -> {
            var dialect = context.getBean(MarkdownDialect.class);
            var bridge = (MarkdownExpressionObject) dialect.getExpressionObjectFactory()
                .buildObject(new ExpressionContext(new SpringTemplateEngine().getConfiguration()), "md");
            assertThat(bridge.toHtmlSafe("# Title")).contains("<h1>Title</h1>");   // the ceiling of 1, theirs
        });
    }

    /**
     * The switch is described where a consumer types it. Boot's processor turns the file the kit writes
     * into the metadata an ide reads, and a kit that lost that step would still work while its only
     * knob went silent — no name completion, no description, no default.
     *
     * <p>The kit's copy is found by reading every copy on the classpath and keeping the one that
     * describes the kit's property. Asking for the resource by name answers with whichever jar comes
     * first, and Boot's own autoconfigure jar publishes a file of exactly this name: for a while this
     * spec was passing against Spring's metadata while the kit published none of its own.
     */
    @Test
    void theOneSwitchIsDescribedWhereItIsTyped() throws Exception {
        var ours = java.util.Collections
            .list(getClass().getClassLoader().getResources("META-INF/spring-configuration-metadata.json"))
            .stream()
            .map(ThymekitAutoConfigurationTest::read)
            .filter(published -> published.contains("\"thymekit.tidy.enabled\""))
            .toList();
        assertThat(ours).as("the kit's own metadata, published for an ide to read").hasSize(1);
        assertThat(ours.get(0)).contains("\"defaultValue\": true").contains("formatting whitespace");
    }

    private static String read(java.net.URL url) {
        try (var bytes = url.openStream()) {
            return new String(bytes.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException cannotRead) {
            throw new java.io.UncheckedIOException(cannotRead);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnRenderer {
        @Bean
        MarkdownRenderer markdownRenderer() {
            return new MarkdownRenderer(1);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnMarkdownDialect {
        static final MarkdownDialect MINE = new MarkdownDialect(new MarkdownRenderer());

        @Bean
        MarkdownDialect markdownDialect() {
            return MINE;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OwnTidyDialect {
        static final TidyDialect MINE = new TidyDialect();

        @Bean
        TidyDialect tidyDialect() {
            return MINE;
        }
    }
}
