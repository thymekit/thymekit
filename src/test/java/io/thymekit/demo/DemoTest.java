/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit.demo;

import static org.assertj.core.api.Assertions.assertThat;

import io.thymekit.MarkdownDialect;
import io.thymekit.MarkdownRenderer;
import io.thymekit.TidyDialect;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.ui.ExtendedModelMap;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.web.servlet.IServletWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * The showcase, which is the kit's only consumer and is written the way a consumer writes.
 *
 * <p>Everything else in this project is specified as what it produces; this one is specified as what a
 * page made of it comes out like, because that is the whole of what it is for. It composes in java,
 * renders through the engine a consumer would use, and the result is written to a file so that the
 * project's own pages can show it without anybody running a server.
 *
 * <p>And it is held to what it says about itself. A page demonstrating a kit whose claim is markup you
 * can stand behind cannot state a number that is wrong, so the number of elements on it is checked
 * against the adapters the jar ships rather than against a memory of them.
 */
class DemoTest {

    private static final Path OUT = Path.of("build/showcase/index.html");

    private static String render() {
        var engine = new SpringTemplateEngine();
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);
        engine.addDialect(new TidyDialect());
        engine.addDialect(new MarkdownDialect(new MarkdownRenderer()));

        var model = new ExtendedModelMap();
        String view = Demo.page(model);

        // the document links its stylesheets with @{...}, which needs a web exchange; servlet doubles
        // give one without a server
        var application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        IServletWebExchange exchange = application.buildExchange(
            new MockHttpServletRequest(), new MockHttpServletResponse());
        var context = new org.thymeleaf.context.WebContext(exchange);
        model.asMap().forEach(context::setVariable);

        return engine.process(view, context);
    }

    /**
     * The page renders, and is written where the project publishes its pages from. Written before
     * anything is asserted: a page that failed a claim is a page somebody will want to open.
     */
    @Test
    void theShowcaseRendersToAFileAnybodyCanOpen() throws IOException {
        String html = render();

        Files.createDirectories(OUT.getParent());
        Files.writeString(OUT, html);

        assertThat(html).contains("<main class=\"page-showcase page-canvas\">")
            .contains("<h1 class=\"tk-heading tk-heading--1\">thymekit</h1>")
            .contains("/thymekit/ui.css").contains("/thymekit/demo.css");
        assertThat(OUT).exists();
    }

    /** One page, one title — samples included, since a frame full of headings is still one document. */
    @Test
    void thePageHasOneTitle() {
        assertThat(render()).containsOnlyOnce("<h1 ");
    }

    /**
     * Every instrument the kit has, on one page. That is the claim the showcase exists to make, so it
     * is the claim held here: each of these is something a consumer can only see by looking at a page.
     */
    @Test
    void everyInstrumentTheKitHasIsOnIt() {
        String html = render();

        assertThat(html)
            .contains("<time datetime=\"2026-08-21\">")
            .contains("lang=\"la\"")
            .contains("rel=\"nofollow noopener\"").contains("target=\"_blank\"")
            .contains("tk-sr-only")
            .contains("tk-caption--eyebrow").contains("tk-caption--subtitle")
            .contains("tk-caption--label").contains("tk-caption--meta")
            .contains("<h5 ").contains("<h6 ")
            .contains("page-hero-status").contains("page-hero-actions")
            .contains("<section class=\"tk-section\" aria-labelledby=\"who-dresses-whom\">")
            .contains("rich-content")
            .contains("rel=\"ugc nofollow\"")
            .contains("tk-md-empty")
            .contains("thymekitDemo");
    }

    /**
     * And the theme can be taken away. The page claims its look is one stylesheet handing values to
     * handles, and the frame in the stock scope is where that claim is shown rather than made.
     */
    @Test
    void theThemeCanBeTakenAway() {
        assertThat(render()).contains("<div class=\"tk-demo-stock tk-defaults\">")
            .containsPattern("tk-demo-stock[\\s\\S]*<h2 class=\"tk-heading tk-heading--2\">thymekit, undressed</h2>");
    }

    /**
     * The number the page states about itself is the number of elements the jar ships. A showcase that
     * miscounted its own subject would be a poor argument for a kit that refuses to let a page say
     * anything it cannot keep.
     */
    @Test
    void theNumberThePageStatesAboutItselfIsTrue() throws Exception {
        var templates = Path.of(DemoTest.class.getResource("/templates/thymekit").toURI());
        long elements;
        try (var files = Files.list(templates)) {
            elements = files.filter(Files::isRegularFile)
                .flatMap(file -> {
                    try {
                        return java.util.regex.Pattern.compile("th:fragment=\"([a-zA-Z0-9]+El)\\(")
                            .matcher(Files.readString(file)).results();
                    } catch (IOException unreadable) {
                        throw new java.io.UncheckedIOException(unreadable);
                    }
                })
                .map(match -> match.group(1)).distinct()
                // the canvas and the head are what a page is made of, not elements it is made from
                .filter(adapter -> !adapter.equals("headEl") && !adapter.equals("canvasEl"))
                .count();
        }

        assertThat(render()).contains(elements + " elements, a canvas and a head");
    }

    /**
     * A page mounted inside somebody else's application says nothing about where it lives: no canonical,
     * no picture, no robots. An address this page cannot know, and a directive that would be a false
     * statement about a page that wants to be read.
     */
    @Test
    void aPageThatCannotKnowItsAddressClaimsNone() {
        String html = render();

        assertThat(html).doesNotContain("rel=\"canonical\"")
            .doesNotContain("og:image").doesNotContain("name=\"robots\"");
        assertThat(html).as("what it can say about itself, it does")
            .contains("<meta name=\"description\"").contains("<meta property=\"og:title\"");
    }
}
