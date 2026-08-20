/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Render pin of the single dispatcher, without a Spring context but with the consumer's expression
 * engine (SpEL): a bare script fragment and an adapter taking one argument, collection order, nested
 * containers — everything through one address.
 */
class ElementDispatchRenderTest {

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

    /** Collapses whitespace: template formatting is not what these pins are about. */
    private static String compact(String html) {
        return html.replaceAll(">\\s+<", "><").replaceAll("\\s*\\n\\s*", " ").strip();
    }

    private static String renderAll(List<? extends Element<?>> items) {
        var ctx = new Context();
        if (items == null) {                                    // th:each over null iterates zero times
            ctx.setVariable("items", null);
            return compact(ENGINE.process("test/harness", Set.of("all"), ctx));
        }
        ctx.setVariable("businessZone", java.time.ZoneId.of("UTC"));   // consumer contract of the date fragment
        ctx.setVariable("items", items.stream().map(e -> e == null ? null : e.asMap()).toList());
        return compact(ENGINE.process("test/harness", Set.of("all"), ctx));
    }

    @Test
    void render_descriptorsInCollectionOrder_nullSafe() {
        Element<?> echo = Element.raw("test/pieces", "echo").with("text", "42").build();
        assertThat(renderAll(List.of(echo, echo))).isEqualTo("<i class=\"echo\">42</i><i class=\"echo\">42</i>");
        assertThat(renderAll(java.util.Arrays.asList(echo, null, echo)))                    // null renders nothing, so adapters need no th:if
            .isEqualTo("<i class=\"echo\">42</i><i class=\"echo\">42</i>");
        assertThat(renderAll(null)).isEmpty();                                              // th:each over null iterates zero times
        var ctx = new Context();
        ctx.setVariable("e", null);
        assertThat(compact(ENGINE.process("test/harness", Set.of("one"), ctx))).isEmpty();
    }

    /** Script elements render through the scripts fragment, called without arguments. */
    @Test
    void scripts_bareFragments_renderedWithoutArgs() {
        var ctx = new Context();
        ctx.setVariable("items", List.of(Element.script("test/pieces", "hello").asMap()));
        assertThat(compact(ENGINE.process("test/harness", Set.of("scripts"), ctx))).isEqualTo("<b class=\"hello\">HELLO</b>");
        // and the wide-point guard: a script element is not part of the flow
        assertThatThrownBy(() -> Element.requireRenderable(Element.script("t", "f"), "row")).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("script element").hasMessageContaining("requires");

        assertThatThrownBy(() -> PageModel.of(new org.springframework.ui.ConcurrentModel()).title("T").add(Element.script("t", "f")))
            .isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    void render_singleElement() {
        var ctx = new Context();
        ctx.setVariable("e", Caption.label("solo").build().asMap());
        String html = compact(ENGINE.process("test/harness", Set.of("one"), ctx));
        assertThat(html).isEqualTo("<p class=\"tk-caption tk-caption--label\">solo</p>");
    }




    /** Slots: the dispatcher renders their contents, the adapter knows only the names. */
    @Test
    void slots_renderedByAdapterThroughDispatcher() {
        Element<?> slotted = Element.raw("test/pieces", "slotted").with("title", "Title")
            .slot("body", List.of(Heading.h3("In the body").build(), Caption.meta("caption").build()))
            .slot("tail", List.of(Caption.label("tail").build()))
            .build();
        String html = renderAll(List.of(slotted));
        assertThat(html).contains("<div class=\"slotted\">Title</div>")
            .contains("<div class=\"body\"><h3 class=\"tk-heading tk-heading--3\">In the body</h3>")
            .contains("tk-caption--meta")
            .contains("<div class=\"tail\"><p class=\"tk-caption tk-caption--label\">tail</p>");
        assertThat(html.indexOf("In the body")).isLessThan(html.indexOf("--label"));    // slot order follows the adapter
        // an empty slot renders nothing and does not fail
        assertThat(renderAll(List.of(Element.raw("test/pieces", "slotted").with("title", "T").build())))
            .contains("<div class=\"body\"></div>").contains("<div class=\"tail\"></div>");
    }

    /** Hero: the heading group in fixed order — eyebrow, h1, subtitle, meta lines. */
    @Test
    void hero_rendersHgroup_inOrder() {
        Element<?> hero = Hero.of(Heading.h1("Baobab").build()).eyebrow(Caption.eyebrow("Product").build())
            .subtitle(Caption.subtitle("RA-101 · Cream").build()).meta(Caption.meta("/baobab").build(), Caption.meta("12 entries").build())
            .build();
        String html = renderAll(List.of(hero));
        assertThat(html).contains("<div class=\"page-hero\"><hgroup class=\"page-hero-group\">"
            + "<p class=\"tk-caption tk-caption--eyebrow\">Product</p>"
            + "<h1 class=\"tk-heading tk-heading--1\">Baobab</h1>"
            + "<p class=\"tk-caption tk-caption--subtitle\">RA-101 · Cream</p>"
            + "<p class=\"tk-caption tk-caption--meta\">/baobab</p>"
            + "<p class=\"tk-caption tk-caption--meta\">12 entries</p>"
            + "</hgroup><div class=\"page-hero-divider\"></div>");
        String bare = renderAll(List.of(Hero.of(Heading.h1("H1 only").build()).build()));
        assertThat(bare).contains("<hgroup class=\"page-hero-group\"><h1 class=\"tk-heading tk-heading--1\">H1 only</h1></hgroup>");
    }

    /** TidyDialect: indentation nodes collapse, pre stays untouched, inline whitespace survives. */
    @Test
    void tidyDialect_collapsesFormattingWhitespace_keepsPreAndInlineSpace() {
        var ctx = new Context();
        ctx.setVariable("businessZone", java.time.ZoneId.of("UTC"));
        ctx.setVariable("e", Md.of("# Heading\n\n```java\nclass A {\n    void b() {}\n}\n```")
            .title(Heading.h2("Code").build()).build().asMap());
        String md = ENGINE.process("test/harness", Set.of("one"), ctx);      // no compaction here: the raw output is the point
        assertThat(md).contains("<pre><code class=\"language-java\">class A {\n    void b() {}\n}\n</code></pre>");   // pre untouched
        assertThat(md.lines().filter(String::isBlank).count()).isZero();                                                // no leftovers

        ctx.setVariable("e", Element.raw("test/pieces", "inline").with("text", "x").build().asMap());
        assertThat(ENGINE.process("test/harness", Set.of("one"), ctx).strip())
            .isEqualTo("<span>a</span>\n<span>b</span>");                                                              // the inline newline survives
    }
}
