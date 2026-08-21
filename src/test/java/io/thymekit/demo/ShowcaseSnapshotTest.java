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
 * Renders the showcase to a file, so the page can be published next to the build reports and read by
 * anyone without running a server. The showcase carries no data of its own, which is what makes a
 * snapshot of it honest: what lands in the file is what the engine produces at run time.
 *
 * <p>It doubles as a pin: if the showcase stops rendering, or renders empty, the build says so.
 */
class ShowcaseSnapshotTest {

    private static final Path OUT = Path.of("build/showcase/index.html");

    /** Adapters the kit ships, less the two a page is made of: the canvas and the head are not elements of it. */
    private static int elementsInTheKit() throws Exception {
        var dir = Path.of(ShowcaseSnapshotTest.class.getResource("/templates/thymekit").toURI());
        try (var files = Files.list(dir)) {
            long adapters = files.filter(Files::isRegularFile)
                .flatMap(file -> {
                    try {
                        return java.util.regex.Pattern.compile("th:fragment=\"([a-zA-Z0-9]+El)\\(")
                            .matcher(Files.readString(file)).results();
                    } catch (IOException unreadable) {
                        throw new java.io.UncheckedIOException(unreadable);
                    }
                })
                .map(m -> m.group(1)).distinct()
                .filter(name -> !name.equals("headEl") && !name.equals("canvasEl"))
                .count();
            return (int) adapters;
        }
    }

    @Test
    void showcase_rendersToAFileAndCarriesItsElements() throws Exception {
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

        // The document links its stylesheets with @{...}, which needs a web exchange; servlet doubles
        // give one without a server.
        var application = JakartaServletWebApplication.buildApplication(new MockServletContext());
        IServletWebExchange exchange = application.buildExchange(
            new MockHttpServletRequest(), new MockHttpServletResponse());
        var context = new org.thymeleaf.context.WebContext(exchange);
        model.asMap().forEach(context::setVariable);

        String html = engine.process(view, context);

        assertThat(html).contains("<main class=\"page-showcase page-canvas\">")   // the canvas puts down the landmark, the page its own class
            .contains("<h1 class=\"tk-heading tk-heading--1\">thymekit</h1>")
            .contains("tk-caption--eyebrow")
            .contains("rich-content")
            .contains("/thymekit/ui.css")
            .contains("/thymekit/demo.css")
            .contains("<meta name=\"description\"")            // the head of the page is the head element's work
            .contains("<meta property=\"og:title\"")
            // the page claims the theme can be taken away; the frame in the stock scope shows it
            .contains("<div class=\"tk-demo-stock tk-defaults\">")
            .containsPattern("tk-demo-stock[\\s\\S]*<h2 class=\"tk-heading tk-heading--2\">thymekit, undressed</h2>")
            .containsOnlyOnce("<h1 ")                                          // a page has one H1, samples included
            // every instrument the kit has, on one page
            .contains("<time datetime=\"2026-08-21\">")                       // a date a machine reads
            .contains("lang=\"la\"")                                          // a phrase in another language
            .contains("rel=\"nofollow noopener\"").contains("target=\"_blank\"")   // a heading that is a link
            .contains("tk-sr-only")                                            // one only a screen reader meets
            .contains("tk-caption--label").contains("tk-caption--meta")        // all four caption roles
            .contains("page-hero-status").contains("page-hero-actions")        // badge and action row
            .contains("rel=\"ugc nofollow\"")                                 // links of somebody else's text
            .contains("detail-empty-hint")                                     // the empty state
            .contains("thymekitDemo");                                         // the script, collected once

        assertThat(html).as("the number the page states about itself, against the adapters in the jar")
            .contains(elementsInTheKit() + " elements, a canvas and a head");

        Files.createDirectories(OUT.getParent());
        Files.writeString(OUT, html);
    }
}
