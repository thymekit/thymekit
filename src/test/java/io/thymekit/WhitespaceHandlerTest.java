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
 * What {@link WhitespaceHandler} owes a page: html that carries no trace of how the templates were
 * formatted, and not one character of change anywhere else.
 *
 * <p>Checked by rendering, which is the only honest way — the class is a handler in Thymeleaf's event
 * stream, and driving that stream by hand would test a model built in the test rather than the one an
 * engine builds. Its own javadoc says as much.
 */
class WhitespaceHandlerTest {

    private static SpringTemplateEngine engine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.addDialect(new TidyDialect());
        return engine;
    }

    private final SpringTemplateEngine engine = engine();

    private String render(String fragment) {
        return engine.process("tidy/page", Set.of(fragment), new Context());
    }

    /** Formatting whitespace becomes one newline, and the newline is indented by the nesting. */
    @Test
    void formattingWhitespaceBecomesOneIndentedNewline() {
        assertThat(render("nested")).isEqualTo("""
            <div class="a">
              <div class="b">
                <span>deep</span>
              </div>
            </div>""");
    }

    /** Not one blank line anywhere: that is the whole reason the dialect exists. */
    @Test
    void nothingIsLeftOfHowTheTemplateWasWritten() {
        assertThat(render("nested").lines().filter(String::isBlank)).isEmpty();
        assertThat(render("commentAndStandalone").lines().filter(String::isBlank)).isEmpty();
    }

    /** Where whitespace is content and not formatting, nothing is touched. */
    @Test
    void insideAPreservedZoneNothingIsTouched() {
        String html = render("preserved");
        assertThat(html).contains("<pre>  two  spaces\n    and an indented line</pre>")
            .contains("<textarea>  raw\n  text</textarea>")
            .contains("<script>var a = 1;\nvar b = 2;</script>")
            .contains("<style>.x { color: red;\n}</style>");
    }

    /**
     * And a zone whose content arrives from another template is still that zone: one render is counted
     * by one handler, or the file boundary would quietly end the preservation.
     */
    @Test
    void aPreservedZoneFilledFromAnotherTemplateIsStillPreserved() {
        assertThat(render("preservedFromElsewhere"))
            .contains("<pre><span><b>a</b>\n        <b>b</b></span></pre>");
    }

    /**
     * An indent held, and then words rather than a tag: the newline that was waiting is written before
     * them, or the words would be pulled up against the tag above.
     */
    @Test
    void anIndentHeldBeforeWordsIsStillWritten() {
        assertThat(render("indentThenWords")).isEqualTo("""
            <div>
              words from an expression
            </div>""");
    }

    /** A template saved on a machine that ends lines with two characters tidies like any other. */
    @Test
    void aTemplateWrittenWithCarriageReturnsTidiesAsWell() {
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(new org.thymeleaf.templateresolver.StringTemplateResolver());
        engine.addDialect(new TidyDialect());

        String html = engine.process("<div>\r\n    <span>a</span>\r\n</div>", new Context());
        assertThat(html).isEqualTo("<div>\n  <span>a</span>\n</div>");
    }

    /**
     * An attribute a processor made carries no place in any file, and a tag whose attributes are all of
     * that kind cannot be shown to span lines — so it is left exactly as it stands, line break and all.
     * The rebuild is an answer to something seen in a template; where nothing can be seen, there is
     * nothing to answer.
     */
    @Test
    void aTagWhoseAttributesHaveNoPlaceInAFileIsLeftAlone() {
        assertThat(render("attributesFromAProcessor"))
            .isEqualTo("<div><a href=\"/x\" title=\"made\"\n       >link</a></div>");
    }

    /** The zone ends where its tag ends: what follows a {@code </pre>} is tidied like anything else. */
    @Test
    void tidyingResumesWhereThePreservedZoneEnds() {
        assertThat(render("afterPre")).isEqualTo("""
            <div>
              <pre>raw  text</pre>
              <span>tidied</span>
            </div>""");
    }

    /**
     * A node that carries words is handed on exactly as it was written, indentation and all. Only
     * whitespace-only nodes are formatting; a space beside a word may be the space between two words,
     * and a handler that trimmed it would be changing what the page says to make the source look tidy.
     * So prose keeps the indentation of its template, and that is the price of never being wrong.
     */
    @Test
    void aNodeThatCarriesWordsIsNotTouched() {
        assertThat(render("prose")).isEqualTo("""
            <div>
                Words an author wrote.
                <span>and a tag after them</span>
            </div>""");
    }

    /** A standalone tag spans lines the same way, and comes back the same way. */
    @Test
    void aStandaloneTagWrittenAcrossLinesComesBackOnOne() {
        assertThat(render("standaloneAcrossLines"))
            .isEqualTo("<div><img src=\"/x.png\" alt=\"on two lines\"></div>");
    }

    /**
     * And a tag that was merely spaced oddly on one line is not touched at all: the rebuild exists to
     * undo a line break, not to tidy an author's spacing inside a tag they can see.
     */
    @Test
    void aTagSpacedOddlyOnOneLineIsLeftAsWritten() {
        assertThat(render("messyOneLine")).isEqualTo("<div><i  class=\"messy\"   data-z=\"1\" >as written</i></div>");
    }

    /** A space between two inline elements is what an author meant, and it stays exactly one space. */
    @Test
    void spaceThatMeansSomethingIsKept() {
        assertThat(render("inline")).isEqualTo("<div><span>a</span> <span>b</span></div>");
    }

    /** A tag whose attributes were written across lines comes back on one: between them space says nothing. */
    @Test
    void aTagWrittenAcrossLinesComesBackOnOne() {
        assertThat(render("acrossLines"))
            .isEqualTo("<div><a class=\"multi\" href=\"/x\" title=\"on three lines\">link</a></div>");
    }

    /**
     * Rebuilt only where rebuilding provably changes nothing. Single quotes are a choice the author made
     * and a minimised attribute has no value to put back, so both are left as they stand — newlines and
     * all, because a tag that renders wrong is worse than a tag that renders wide.
     */
    @Test
    void aTagThatCannotBeRebuiltSafelyIsLeftAsItStands() {
        String html = render("notRebuildable");
        assertThat(html).contains("data-x='quotes'").contains("checked");
        assertThat(html).contains("\n");                     // its own lines survive, untouched
    }

    /** A comment and a standalone tag are nodes of the flow like any other, and are indented like any other. */
    @Test
    void aCommentAndAStandaloneTagTakeTheirPlace() {
        assertThat(render("commentAndStandalone")).isEqualTo("""
            <div>
              <!-- a comment of the page -->
              <br>
              <img src="/x.png" alt="x">
            </div>""");
    }

    /**
     * An indent that would run away stops growing. A page nested two dozen levels deep is a page whose
     * margin has stopped saying anything about its structure, and a line half of which is spaces is
     * worse to read than a line that is not indented at all.
     */
    @Test
    void anIndentThatWouldRunAwayStopsGrowing() {
        var deep = new SpringTemplateEngine();
        deep.setTemplateResolver(new org.thymeleaf.templateresolver.StringTemplateResolver());
        deep.addDialect(new TidyDialect());

        String html = deep.process("<div>\n".repeat(30) + "<span>deep</span>\n" + "</div>\n".repeat(30),
            new Context());
        assertThat(html).contains("\n" + " ".repeat(24 * 2) + "<span>deep</span>");
        assertThat(html).doesNotContain(" ".repeat(25 * 2) + "<");
    }

    /**
     * A render that dies leaves nothing behind for the next one. It died inside a preserved zone, so the
     * count of those zones is what it could have left — and the page rendered after it would then come
     * out exactly as the template was written, with every indent of every adapter in it.
     */
    @Test
    void aRenderThatDiedLeavesNothingBehind() {
        var context = new Context();
        context.setVariable("text", "a string with no such method");
        assertThatThrownBy(() -> engine.process("tidy/page", Set.of("diesInsidePre"), context))
            .isInstanceOf(RuntimeException.class);

        assertThat(render("nested")).isEqualTo("""
            <div class="a">
              <div class="b">
                <span>deep</span>
              </div>
            </div>""");
    }
}
