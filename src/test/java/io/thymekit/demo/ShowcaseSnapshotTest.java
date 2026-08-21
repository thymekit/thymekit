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

    @Test
    void showcase_rendersToAFileAndCarriesItsElements() throws IOException {
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

        assertThat(html).contains("<main class=\"page-canvas\">")   // the canvas puts down the landmark itself
            .contains("<h1 class=\"tk-heading tk-heading--1\">thymekit</h1>")
            .contains("tk-caption--eyebrow")
            .contains("rich-content")
            .contains("/thymekit/ui.css")
            .contains("/thymekit/demo.css")
            .contains("<meta name=\"description\"")            // the head of the page is the head element's work
            .contains("<meta property=\"og:title\"")
            // the page claims the theme can be taken away; the frame in the stock scope shows it
            .contains("<div class=\"tk-demo-stock tk-defaults\">")
            .containsPattern("tk-demo-stock[\\s\\S]*<h1 class=\"tk-heading tk-heading--1\">thymekit</h1>");

        Files.createDirectories(OUT.getParent());
        Files.writeString(OUT, html);
    }
}
