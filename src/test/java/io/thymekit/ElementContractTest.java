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

    /**
     * Every file of the triple shares one suffix path — {@code io/thymekit/Heading.java},
     * {@code templates/thymekit/heading.html}, {@code static/thymekit/heading.css} — so an element's
     * stylesheet is its name, and the manifest is asked whether it is there. One element is exempt: the
     * head prints tags a browser never shows and has no look at all. A list of the rest is not kept
     * here on purpose; a list is a thing to forget, and forgetting it is how a file escapes the walk.
     */
    private static final Set<String> WITHOUT_A_LOOK = Set.of("head");

    /**
     * Classes the kit prints that do not carry its prefix. Two of them are decisions — {@code
     * rich-content} is the open surface a theme styles directly, and the page-level names came with the
     * canvas — and {@code detail-empty-hint} is a name left over from the application the kit was cut
     * out of. Pinned rather than allowed: nothing new joins this list without somebody saying so.
     */
    private static final Set<String> OUTSIDE_THE_PREFIX = Set.of(
        "rich-content", "detail-empty-hint",
        "page-canvas", "page-hero", "page-hero-group", "page-hero-status", "page-hero-actions");

    /**
     * One live element per adapter — including the two a page is made of, since the page is an element
     * too. {@link #everyAdapterHasASample()} checks that this list left nothing out.
     */
    static List<Element<?>> samples() {
        var model = new org.springframework.ui.ConcurrentModel();
        PageModel.of(model).title("Page").description("What this page is")
            .canonical("https://shop/page").image("https://shop/page.jpg")
            .robots(PageModel.Robots.NOARCHIVE)
            .add(Heading.h1("Title").build()).render();
        return List.of(
            Heading.h3("Section").build(),
            // every option of the heading, so no branch of its adapter goes unrendered by the suite
            Heading.h2("Linked").id("linked").href("https://x/y").rel(Rel.NOFOLLOW).newTab()
                .lang("en").srOnly().build(),
            Caption.eyebrow("Product").build(), Caption.subtitle("RA-101").build(),
            Caption.label("label").build(), Caption.meta("meta").build(),
            Caption.meta("12 March 2026").time(java.time.LocalDate.of(2026, 3, 12)).lang("en-GB").build(),
            Md.of("**text**").build(),
            Section.of(Heading.h2("Description")).add(Md.of("under a heading")).build(),
            Md.of("[out](https://spam.example/x)").linkRel(Rel.UGC).build(),
            Md.of(null).emptyHint("No description yet")
                .addAction(Caption.label("Add")).build(),
            Hero.of(Heading.h1("Title").build()).eyebrow(Caption.eyebrow("Label").build())
                .subtitle(Caption.subtitle("RA-101").build()).meta(Caption.meta("/slug").build())
                .badge(Element.raw("test/pieces", "statusBadgeEl").with("text", "in stock").build())
                .actions(Element.raw("test/pieces", "actionsEl").with("text", "Buy").build()).build(),
            fromModel(model, "head"), fromModel(model, "page"));
    }

    /**
     * An element the canvas built, taken back out of the model it was written into. Data is copied key
     * by key, so a page part that grew a slot or a script dependency would be rebuilt without it —
     * which would make this sample quietly unlike the real thing, and is refused instead.
     */
    @SuppressWarnings("unchecked")
    private static Element<?> fromModel(org.springframework.ui.Model model, String key) {
        Map<String, Object> descriptor = (Map<String, Object>) model.asMap().get(key);
        assertThat(descriptor).as("a page part with slots or assets needs a sample of its own, not this rebuild")
            .doesNotContainKeys("slots", "assets");
        Element.Descriptor<Element.Raw> rebuilt =
            Element.raw((String) descriptor.get("template"), (String) descriptor.get("fragment"));
        descriptor.forEach((k, v) -> {
            if (!Element.RESERVED.contains(k)) {
                rebuilt.with(k, v);
            }
        });
        return rebuilt.build();
    }

    /**
     * The claim in the readme — that a contract test walks every element — held only as long as somebody
     * remembered to add one to {@link #samples()}. Now the templates are asked instead: every adapter
     * declared in the kit's fragments has a sample, or this fails with its name.
     */
    @Test
    void everyAdapterHasASample() throws Exception {
        var dir = java.nio.file.Path.of(getClass().getResource("/templates/thymekit").toURI());
        Set<String> declared = new java.util.TreeSet<>();
        try (var files = java.nio.file.Files.list(dir)) {
            // the kit's own adapters live here; what is one level down (thymekit/demo/) is showcase
            // furniture standing in for a consumer's elements, and owes this test nothing
            for (java.nio.file.Path file : files.filter(java.nio.file.Files::isRegularFile).toList()) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("th:fragment=\"([a-zA-Z0-9]+El)\\(")
                    .matcher(java.nio.file.Files.readString(file));
                while (m.find()) {
                    declared.add(m.group(1));
                }
            }
        }
        assertThat(declared).contains("headingEl", "captionEl", "heroEl", "mdEl", "headEl", "canvasEl");
        assertThat(samples()).extracting(Element::fragment).containsAll(declared);
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

    /**
     * The kit takes the walk it hands to a consumer, over its own elements: the address points at a
     * fragment that declares itself, the adapter is named like an adapter, nothing renders empty, and
     * every class printed has a rule. What stays below is what only the kit can check about itself.
     */
    @Test
    void theKitKeepsTheContractItPublishes() {
        ElementContract.of(samples().toArray(Composable<?>[]::new))
            .coveringEveryKey()
            .renderedBy(ENGINE)
            .styledBy(CssCanonTest.stylesheets())
            .check();
    }

    @Test
    void adapterNames_versionable() {
        for (Element<?> e : samples()) {
            assertThat(e.fragment()).as("adapter %s :: %s", e.template(), e.fragment()).matches(ADAPTER_NAME);
            assertThat(e.template()).startsWith("thymekit/");
        }
    }

    /** An adapter renders without a single blank line of template formatting. */
    @Test
    void adapters_renderTidy_noBlankLines() {
        for (Element<?> e : samples()) {
            var ctx = new Context();
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
        for (Element<?> e : samples()) {
            String element = e.template().substring("thymekit/".length());
            if (WITHOUT_A_LOOK.contains(element)) {
                continue;                                  // the head prints tags, not looks
            }
            String css = element + ".css";
            assertThat(CssCanonTest.manifest()).as("the manifest imports %s", css).contains(css);
            assertThat(resource("static/thymekit/" + css)).as("stock scope .tk-defaults in %s", css).contains(".tk-defaults");
        }
    }

    /** Whatever the prefix is for, the classes outside it are the few that were decided, and no more. */
    @Test
    void css_classesOutsideThePrefix_areTheOnesDecided() {
        Set<String> strangers = new java.util.LinkedHashSet<>();
        for (Element<?> e : samples()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("class=\"([^\"]+)\"").matcher(render(e));
            while (m.find()) {
                for (String name : m.group(1).trim().split("\\s+")) {
                    if (!name.startsWith("tk-")) {
                        strangers.add(name);
                    }
                }
            }
        }
        assertThat(strangers).containsExactlyInAnyOrderElementsOf(OUTSIDE_THE_PREFIX);
    }

    /**
     * The narrowest joint of the triple: a class an element prints has a rule in the kit's CSS. The
     * names are assembled at render time — {@code 'tk-caption tk-caption--' + role} — so nothing else
     * ties the stylesheet to the code that can produce it, and a class renamed in one of the two would
     * otherwise travel unnoticed.
     */
    @Test
    void css_everyClassAnElementPrints_hasARule() throws IOException {
        StringBuilder kitCss = new StringBuilder();
        for (String resource : CssCanonTest.stylesheets()) {
            // comments name classes too, and a class documented but not styled is exactly what this looks for
            kitCss.append(resource(resource).replaceAll("(?s)/\\*.*?\\*/", " "));
        }
        Set<String> printed = new java.util.LinkedHashSet<>();
        for (Element<?> e : samples()) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("class=\"([^\"]+)\"").matcher(render(e));
            while (m.find()) {
                printed.addAll(List.of(m.group(1).trim().split("\\s+")));
            }
        }
        assertThat(printed).isNotEmpty();
        for (String className : printed) {
            assertThat(kitCss.toString()).as("class %s printed by an element has no rule in the kit's css", className)
                .containsPattern("\\." + java.util.regex.Pattern.quote(className) + "(?![\\w-])");
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
