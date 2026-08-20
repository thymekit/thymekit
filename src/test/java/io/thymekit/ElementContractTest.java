/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * The element author's contract, checked on one live sample per factory:
 * <ol>
 *   <li>the adapter name is versionable ({@code <name>El} or {@code <name>ElV<n>});</li>
 *   <li>its address resolves and renders under the consumer's expression engine;</li>
 *   <li>the element has a CSS file, listed in the manifest and carrying the {@code .tk-defaults} scope;</li>
 *   <li>interactive elements carry an accessible name;</li>
 *   <li>declared scripts resolve and render;</li>
 *   <li>an element is a value: building the same sample twice yields equal elements.</li>
 * </ol>
 * A new element is added to {@link #samples()} and is checked from then on.
 */
class ElementContractTest {

    private static final Pattern ADAPTER_NAME = Pattern.compile("^[a-z][A-Za-z0-9]*El(V\\d+)?$");

    /** Template name to CSS file name, when they differ. */
    private static final Map<String, String> CSS_BY_TEMPLATE = Map.of("drawer", "toolbar-drawer");

    /** One live element per element; URLs are absolute because there is no web context here. */
    static List<Element<?>> samples() {
        return List.of(
            Heading.h3("Section").build(),
            Caption.eyebrow("Product").build(), Caption.subtitle("RA-101").build(),
            Caption.label("label").build(), Caption.meta("meta").build(),
            Md.of("**text**").title(Heading.h2("Description").build()).build(),
            Hero.of(Heading.h1("Title").build()).eyebrow(Caption.eyebrow("Label").build())
                .subtitle(Caption.subtitle("RA-101").build()).meta(Caption.meta("/slug").build()).build());
    }

    private static final SpringTemplateEngine ENGINE = engine();

    private static SpringTemplateEngine engine() {
        var r = new ClassLoaderTemplateResolver();
        r.setPrefix("templates/");
        r.setSuffix(".html");
        r.setTemplateMode("HTML");
        r.setCharacterEncoding("UTF-8");
        var e = new SpringTemplateEngine();
        e.setTemplateResolver(r);
        e.addDialect(new TidyDialect());       // tidy rendering is part of the contract
        e.addDialect(new MarkdownDialect(new MarkdownRenderer()));   // #md, as configured for a consumer
        return e;
    }

    /** Script elements render through their own fragment, without arguments. */
    private static String scripts(Element<Element.Script> js) {
        var ctx = new Context();
        ctx.setVariable("items", java.util.List.of(js.asMap()));
        return ENGINE.process("test/harness", Set.of("scripts"), ctx);
    }

    private static String render(Element<?> e) {
        var ctx = new Context();
        ctx.setVariable("businessZone", java.time.ZoneId.of("UTC"));
        ctx.setVariable("e", e.asMap());
        return ENGINE.process("test/harness", Set.of("one"), ctx).replaceAll("\\s+", " ").strip();
    }

    private static String resource(String path) throws IOException {
        try (InputStream in = ElementContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("resource %s", path).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Outside preserved zones: their contents and comments are content, not formatting. */
    private static String outsidePreserved(String html) {
        return html.replaceAll("(?s)<(pre|textarea|script|style)\\b.*?</\\1>", "[preserved zone]")
            .replaceAll("(?s)<!--.*?-->", "[comment]");
    }

    @Test
    void adapterNames_versionable() {
        for (Element<?> e : samples()) {
            assertThat(e.fragment()).as("adapter %s :: %s", e.template(), e.fragment()).matches(ADAPTER_NAME);
            assertThat(e.template()).startsWith("fragments/thymekit/");
        }
    }

    /** An adapter renders without a single blank line of template formatting. */
    @Test
    void adapters_renderTidy_noBlankLines() {
        for (Element<?> e : samples()) {
            var ctx = new Context();
            ctx.setVariable("businessZone", java.time.ZoneId.of("UTC"));
            ctx.setVariable("e", e.asMap());
            String html = ENGINE.process("test/harness", Set.of("one"), ctx).strip();
            assertThat(outsidePreserved(html).lines().filter(String::isBlank).count())
                .as("blank lines rendering %s :: %s%n%s", e.template(), e.fragment(), html).isZero();
        }
    }

    @Test
    void adapters_resolveAndRender_notBlank() {
        for (Element<?> e : samples()) {
            String html = render(e);
            assertThat(html).as("render %s :: %s", e.template(), e.fragment()).isNotBlank().contains("<");
            assertThat(e).isEqualTo(rebuild(e));   // an element is a value: rebuilding the sample yields an equal one
        }
    }

    /** Rebuilds the same sample by its adapter address; the registry is deterministic. */
    private static Element<?> rebuild(Element<?> e) {
        return samples().stream().filter(s -> s.template().equals(e.template()) && s.fragment().equals(e.fragment())
            && s.asMap().equals(e.asMap())).findFirst().orElseThrow();
    }

    @Test
    void css_perElement_inManifest_withStockScope() throws IOException {
        String manifest = resource("static/thymekit/ui.css");
        Set<String> seen = new java.util.HashSet<>();
        for (Element<?> e : samples()) {
            String name = e.template().substring("fragments/thymekit/".length());
            String css = CSS_BY_TEMPLATE.getOrDefault(name, name);
            if (!seen.add(css)) {
                continue;
            }
            String file = resource("static/thymekit/elements/" + css + ".css");
            assertThat(manifest).as("manifest ui.css imports %s", css).contains("@import url(\"elements/" + css + ".css\")");
            assertThat(file).as("stock scope .tk-defaults in %s.css", css).contains(".tk-defaults");
        }
    }

    /** A heading may be a link, and a screen-reader-only heading stays in the outline. */
    @Test
    void heading_linkAndScreenReaderOnly() {
        assertThat(render(Heading.h3("Section").href("https://x/s").build())).contains("<a href=\"https://x/s\"");
        assertThat(render(Heading.h2("Hidden").srOnly().build())).contains("tk-sr-only");
    }

    @Test
    void assets_resolveAndRender() {
        for (Element<?> e : samples()) {
            for (Element<Element.Script> js : e.assets()) {
                assertThat(js.bare()).isTrue();
                assertThat(scripts(js)).as("script %s :: %s", js.template(), js.fragment()).contains("<script");
            }
        }
        // no behavioural elements in the core right now: their scripts left with them, and the first
        // one to come back brings a sample with a dependency here
        assertThat(Element.assetsOf(samples())).isEmpty();
    }
}
