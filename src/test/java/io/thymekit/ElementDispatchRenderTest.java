/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
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
        ctx.setVariable("businessZone", ZoneId.of("UTC"));   // consumer contract of the date fragment
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

        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("T").add(Element.script("t", "f")))
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
        assertThat(html).contains("<header class=\"page-hero\"><hgroup class=\"page-hero-group\">"
            + "<p class=\"tk-caption tk-caption--eyebrow\">Product</p>"
            + "<h1 class=\"tk-heading tk-heading--1\">Baobab</h1>"
            + "<p class=\"tk-caption tk-caption--subtitle\">RA-101 · Cream</p>"
            + "<p class=\"tk-caption tk-caption--meta\">/baobab</p>"
            + "<p class=\"tk-caption tk-caption--meta\">12 entries</p>"
            + "</hgroup>");                                  // the rule under the group is CSS, not markup
        // the badge and the action row hold whatever element the consumer passed — a block one included,
        // so neither container may be a <p>: that would be markup the kit itself made invalid
        String withParts = renderAll(List.of(Hero.of(Heading.h1("B").build())
            .badge(Element.raw("test/pieces", "statusBadgeEl").with("text", "in stock").build())
            .actions(Element.raw("test/pieces", "actionsEl").with("text", "Buy").build()).build()));
        assertThat(withParts).contains("<div class=\"page-hero-status\"><span class=\"badge\">in stock</span></div>")
            .contains("<div class=\"page-hero-actions\"><div class=\"actions\">Buy</div></div>");

        String bare = renderAll(List.of(Hero.of(Heading.h1("H1 only").build()).build()));
        assertThat(bare).contains("<hgroup class=\"page-hero-group\"><h1 class=\"tk-heading tk-heading--1\">H1 only</h1></hgroup>")
            .doesNotContain("page-hero-status").doesNotContain("page-hero-actions");   // absent parts render nothing
    }

    /** TidyDialect: indentation nodes collapse, pre stays untouched, inline whitespace survives. */
    @Test
    void tidyDialect_collapsesFormattingWhitespace_keepsPreAndInlineSpace() {
        var ctx = new Context();
        ctx.setVariable("businessZone", ZoneId.of("UTC"));
        ctx.setVariable("e", Md.of("# Heading\n\n```java\nclass A {\n    void b() {}\n}\n```")
            .title(Heading.h2("Code").build()).build().asMap());
        String md = ENGINE.process("test/harness", Set.of("one"), ctx);      // no compaction here: the raw output is the point
        assertThat(md).contains("<pre><code class=\"language-java\">class A {\n    void b() {}\n}\n</code></pre>");   // pre untouched
        assertThat(md.lines().filter(String::isBlank).count()).isZero();                                                // no leftovers

        ctx.setVariable("e", Element.raw("test/pieces", "inline").with("text", "x").build().asMap());
        assertThat(ENGINE.process("test/harness", Set.of("one"), ctx).strip())
            .isEqualTo("<span>a</span>\n<span>b</span>");                                                              // the inline newline survives
    }

    /** The head prints what the canvas said and nothing more: no empty tags, one title for all of them. */
    @Test
    void head_printsOnlyWhatWasSaid() {
        var model = new ConcurrentModel();
        PageModel.of(model).title("Baobab").description("An oil").canonical("https://shop/baobab")
            .image("https://shop/baobab.jpg").robots(PageModel.Robots.NOINDEX).render();
        String full = renderHead(model);
        assertThat(full)
            .contains("<title>Baobab</title>")
            .contains("<meta name=\"description\" content=\"An oil\">")
            .contains("<link rel=\"canonical\" href=\"https://shop/baobab\">")
            .contains("<meta name=\"robots\" content=\"noindex\">")
            .contains("<meta property=\"og:type\" content=\"website\">")
            .contains("<meta property=\"og:title\" content=\"Baobab\">")
            .contains("<meta property=\"og:description\" content=\"An oil\">")
            .contains("<meta property=\"og:url\" content=\"https://shop/baobab\">")
            .contains("<meta property=\"og:image\" content=\"https://shop/baobab.jpg\">")
            .contains("<meta name=\"twitter:card\" content=\"summary_large_image\">");

        var bare = new ConcurrentModel();
        PageModel.of(bare).title("Plain & simple").render();
        String plain = renderHead(bare);
        assertThat(plain).contains("<title>Plain &amp; simple</title>")           // the title is escaped, like any text
            .contains("<meta property=\"og:type\" content=\"website\">")        // what the page is, always said
            .doesNotContain("twitter:card")                                      // nothing to preview, nothing to shape
            .doesNotContain("name=\"description\"").doesNotContain("rel=\"canonical\"")
            .doesNotContain("name=\"robots\"").doesNotContain("og:image");

        var described = new ConcurrentModel();
        PageModel.of(described).title("T").description("A sentence").render();
        assertThat(renderHead(described)).contains("<meta name=\"twitter:card\" content=\"summary\">");
    }

    @SuppressWarnings("unchecked")
    private static String renderHead(Model model) {
        var ctx = new Context();
        ctx.setVariable("items", List.of((Map<String, Object>) model.asMap().get("head")));
        return compact(ENGINE.process("test/harness", Set.of("all"), ctx));
    }

    /** A section is named when its heading was given an id, and stays an ordinary box when it was not. */
    @Test
    void mdSection_isNamedByTheHeadingThatHasAnAddress() {
        String named = renderAll(List.of(Md.of("Text").title(Heading.h2("Composition").id("composition").build()).build()));
        assertThat(named).contains("<section class=\"tk-section\" aria-labelledby=\"composition\">")
            .contains("<h2 class=\"tk-heading tk-heading--2\" id=\"composition\">Composition</h2>");

        String plain = renderAll(List.of(Md.of("Text").title(Heading.h2("Composition").build()).build()));
        assertThat(plain).contains("<section class=\"tk-section\">").doesNotContain("aria-labelledby");

        String headless = renderAll(List.of(Md.of("Text").build()));
        assertThat(headless).contains("<section class=\"tk-section\">").doesNotContain("aria-labelledby");
    }

    /** Text for people, attributes for machines: a date that is readable both ways, a language that is declared. */
    @Test
    void caption_printsTimeAndLang() {
        assertThat(renderAll(List.of(Caption.meta("12 March 2026").time(LocalDate.of(2026, 3, 12)).build())))
            .isEqualTo("<p class=\"tk-caption tk-caption--meta\"><time datetime=\"2026-03-12\">12 March 2026</time></p>");
        assertThat(renderAll(List.of(Caption.subtitle("Adansonia digitata").lang("la").build())))
            .isEqualTo("<p class=\"tk-caption tk-caption--subtitle\" lang=\"la\">Adansonia digitata</p>");
        assertThat(renderAll(List.of(Caption.meta("plain").build())))
            .isEqualTo("<p class=\"tk-caption tk-caption--meta\">plain</p>");           // neither given, neither printed
    }

    /** A heading link says what it is, and a new tab can never be opened without noopener. */
    @Test
    void heading_linkCarriesRelAndTarget() {
        assertThat(renderAll(List.of(Heading.h2("Source").href("https://x/s").rel(Rel.NOFOLLOW, Rel.UGC, Rel.NOFOLLOW).build())))
            .contains("<a href=\"https://x/s\" rel=\"nofollow ugc\">Source</a>");
        assertThat(renderAll(List.of(Heading.h2("Source").href("https://x/s").newTab().build())))
            .contains("rel=\"noopener\"").contains("target=\"_blank\"");
        assertThat(renderAll(List.of(Heading.h2("Source").href("https://x/s").rel(Rel.NOFOLLOW).newTab().build())))
            .contains("rel=\"nofollow noopener\"");                       // what was said, plus what must not be forgotten
        assertThat(renderAll(List.of(Heading.h2("Source").href("https://x/s").newTab().rel(Rel.NOFOLLOW).build())))
            .contains("rel=\"nofollow noopener\"");                       // and the other order says the same thing
        assertThat(renderAll(List.of(Heading.h2("Source").href("https://x/s").rel(Rel.NOFOLLOW).rel(Rel.UGC).build())))
            .contains("rel=\"nofollow ugc\"");                            // two calls accumulate, they do not replace
        assertThat(renderAll(List.of(Heading.h2("Plain").href("https://x/s").build())))
            .contains("<a href=\"https://x/s\">Plain</a>");
        assertThat(renderAll(List.of(Heading.h2("Latin").lang("la").build())))
            .isEqualTo("<h2 class=\"tk-heading tk-heading--2\" lang=\"la\">Latin</h2>");
        assertThatThrownBy(() -> Heading.h2("x").rel()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("without a value");
        assertThatThrownBy(() -> Heading.h2("x").rel(Rel.UGC).build())      // an attribute with no <a> to sit on
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("not a link");
        assertThatThrownBy(() -> Heading.h2("x").newTab().build()).isInstanceOf(IllegalStateException.class);
        for (String script : List.of("javascript:alert(1)", "JavaScript:alert(1)", " data:text/html,x", "vbscript:x",
                "java\tscript:alert(1)", "java\nscript:alert(1)", "jav\u0000ascript:alert(1)")) {   // a browser ignores these too
            assertThatThrownBy(() -> Heading.h2("x").href(script))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not a link but a script");
        }
        assertThatThrownBy(() -> Heading.h2("x").href(" ")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("blank");
        assertThat(Heading.h2("x").href("  https://o/x  ").build().asMap()).containsEntry("href", "https://o/x");   // trimmed
        var reused = Heading.h2("x").href("https://o/x").rel(Rel.UGC);
        assertThat(reused.newTab().build().asMap()).containsEntry("rel", "ugc noopener");
        assertThat(reused.build().asMap()).containsEntry("rel", "ugc noopener");            // build() is not a step: same twice
        assertThat(Rel.SPONSORED.token()).isEqualTo("sponsored");
        assertThat(Rel.NOREFERRER.token()).isEqualTo("noreferrer");
    }

    /** The section hands the policy of its links down to the renderer. */
    @Test
    void mdSection_marksOutgoingLinksWhenTold() {
        String review = renderAll(List.of(Md.of("Try [this](https://spam.example/x)").linkRel(Rel.UGC, Rel.NOFOLLOW).build()));
        assertThat(review).contains("rel=\"ugc nofollow\"");

        String editorial = renderAll(List.of(Md.of("Try [this](https://spam.example/x)").build()));
        assertThat(editorial).doesNotContain("rel=");
        assertThatThrownBy(() -> Md.of("t").linkRel()).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("without a value");
        assertThatThrownBy(() -> Md.of("t").linkRel(Rel.UGC, null)).isInstanceOf(NullPointerException.class)
            .hasMessageContaining("linkRel");
        assertThatThrownBy(() -> Md.of(null).emptyHint("nothing yet").linkRel(Rel.UGC).build())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("no text");
    }
}
