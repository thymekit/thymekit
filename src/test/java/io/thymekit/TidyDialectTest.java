/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Tidy rendering: the output carries no trace of template formatting, yet significant whitespace
 * survives — inside preserved zones and between inline elements. State is per render, so repeating a
 * render yields an identical result.
 */
class TidyDialectTest {

    private static final SpringTemplateEngine ENGINE = engine();

    private static SpringTemplateEngine engine() {
        var r = new ClassLoaderTemplateResolver();
        r.setPrefix("templates/");
        r.setSuffix(".html");
        r.setTemplateMode("HTML");
        r.setCharacterEncoding("UTF-8");
        var e = new SpringTemplateEngine();
        e.setTemplateResolver(r);
        e.addDialect(new TidyDialect());
        return e;
    }

    /** Blank lines inside preserved zones and comments are content, not formatting. */
    static String outsidePreserved(String html) {
        return html.replaceAll("(?s)<(pre|textarea|script|style)\\b.*?</\\1>", "[preserved zone]")
            .replaceAll("(?s)<!--.*?-->", "[comment]");
    }

    private static String render() {
        var ctx = new Context();
        ctx.setVariable("e", Element.raw("test/pieces", "tidy").with("x", "1").build().asMap());
        return ENGINE.process("test/harness", Set.of("one"), ctx);
    }

    @Test
    void dialect_registersPostProcessor() {
        TidyDialect d = new TidyDialect();
        assertThat(d.getName()).isEqualTo("thymekit-tidy");
        assertThat(d.getDialectPostProcessorPrecedence()).isEqualTo(1000);
        assertThat(d.getPostProcessors()).hasSize(1)
            .allSatisfy(p -> assertThat(p.getHandlerClass()).isEqualTo(WhitespaceHandler.class));
    }

    @Test
    void render_noBlankLines_preservesMeaningfulWhitespace() {
        String html = render().strip();
        assertThat(outsidePreserved(html).lines().filter(String::isBlank).count()).as(html).isZero();   // no leftovers
        assertThat(html).contains("<pre>  two  spaces\n    and indent</pre>")                  // pre exactly as authored
            .contains("<textarea>  raw\n  text</textarea>")                                // textarea
            .contains("var a = 1;\nvar b = 2;")                                               // script
            .contains(".x { color: red;\n}")                                                  // style
            .contains("<span>a</span> <span>b</span>")                                        // inline space survives
            .contains("<!-- plain comment -->")                                         // comment survives
            .contains("<br/>");                                                               // standalone
        // whitespace inside pre is untouched, otherwise the raw text would break
        assertThat(html).contains("<pre><code>x</code>\n   \n</pre>");
        // indent follows nesting depth; a closing tag returns to the parent level
        assertThat(html.replaceAll("(?s).*(<div class=\"c\">)", "$1").replaceAll("(?s)</div>\n<div class=\"d\">.*", "</div>"))
            .isEqualTo("<div class=\"c\">\n  <pre><code>x</code>\n   \n</pre>\n  <div><span>nested</span></div>"
                + "\n  text node\n</div>");
        // structural nodes do not glue together: the held newline is emitted before each
        assertThat(html.replaceAll("(?s).*(<div class=\"b\">)", "$1").replaceAll("(?s)</div>.*", "</div>"))
            .isEqualTo("<div class=\"b\">\n  <br/>\n  <!-- plain comment -->\n  <span>a</span> <span>b</span>\n</div>");
    }

    @Test
    void state_isPerRender_notLeakingBetweenRenders() {
        assertThat(render()).isEqualTo(render());   // state does not survive a render
    }

    /** A render that fails midway leaves no depth behind: the next one starts at zero. */
    @Test
    void failedRender_leavesNoIndentStateBehind() {
        String good = render();
        assertThatThrownBy(() -> ENGINE.process("test/broken", new Context()))   // fails inside nested tags
            .isInstanceOf(org.thymeleaf.exceptions.TemplateProcessingException.class);
        assertThat(render()).isEqualTo(good);
    }

    /** Multiline attributes never reach the output — the tag is rebuilt on one line when that is safe. */
    @Test
    void render_multilineTags_rebuiltInOneLine_unlessRisky() {
        String html = render().strip();
        assertThat(html).contains("<a class=\"multi\" href=\"/x\" title=\"multiline tag\">link</a>")    // attribute order preserved
            .contains("<img src=\"/x.png\" alt=\"image\" width=\"10\"/>");        // self-closing the same way
        assertThat(html).contains("checked");                                     // minimised attribute untouched
        assertThat(html).contains("single")                                    // single quotes untouched
            .contains("<b class='one-line' data-y='intact'>one-line</b>")         // a one-line tag is never rebuilt...
            .contains("<i  class=\"messy\"   data-z=\"1\" >");                       // ...even when its spacing is messy
        assertThat(html.replaceAll("(?s).*(<div class=\"d\">)", "$1").replaceAll("(?s)</div>\n<div class=\"b\">.*", "</div>").lines()
            .filter(l -> l.strip().startsWith("<input") || l.strip().startsWith("checked")).count()).isEqualTo(2);   // risky tag stays as authored
    }

    /** A whole document, not a fragment: doctype and root tags pass through the handler. */
    @Test
    void render_wholeDocument_docTypeAndRootTags() {
        var ctx = new Context();
        ctx.setVariable("e", Element.raw("test/pieces", "tidy").with("x", "1").build().asMap());
        String html = ENGINE.process("test/pieces", ctx).strip();
        assertThat(html).startsWith("<!DOCTYPE html>").contains("<html").contains("</html>");
        assertThat(outsidePreserved(html).lines().filter(String::isBlank).count()).as(html).isZero();
    }

    /**
     * One handler counts a whole render, nested templates included: a preserved tag in one file whose
     * content comes from another keeps that content exactly, rather than re-indenting it to the depth
     * the outer file happens to be at. Seven spaces in the inner file, four if the depth were applied.
     */
    @Test
    void aPreservedTagKeepsWhatAnotherFilePutInIt() {
        String html = ENGINE.process("test/preserved-outer", Set.of("outer"), new Context());
        assertThat(html).contains("<pre>\n       <span>seven spaces before me</span>\n</pre>");
    }

    /** A render that dies inside a preserved tag leaves no preservation behind for the next one. */
    @Test
    void aRenderThatDiesInsideAPreservedTagLeavesNothingBehind() {
        String good = ENGINE.process("test/preserved-outer", Set.of("outer"), new Context());
        assertThatThrownBy(() -> ENGINE.process("test/preserved-outer", Set.of("dies"), new Context()))
            .isInstanceOf(org.thymeleaf.exceptions.TemplateProcessingException.class);
        assertThat(ENGINE.process("test/preserved-outer", Set.of("outer"), new Context())).isEqualTo(good);
        assertThat(render()).contains("\n  ");                     // and tidying still happens elsewhere
    }
}
