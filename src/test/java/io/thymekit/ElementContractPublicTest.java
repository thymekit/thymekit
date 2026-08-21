/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * The walk the kit hands to a consumer, taken over the kit's own elements — and over elements broken
 * on purpose, since a check that cannot fail says nothing.
 */
class ElementContractPublicTest {

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
        e.addDialect(new MarkdownDialect(new MarkdownRenderer()));
        return e;
    }

    @Test
    void theKitsOwnElementsKeepTheContractItHandsOut() {
        ElementContract.of(
                Heading.h2("Section"),
                Caption.meta("12 entries"),
                Md.of("**text**").title(Heading.h3("Description")),
                Hero.of(Heading.h1("Title")).subtitle(Caption.subtitle("RA-101")))
            .renderedBy(ENGINE)
            .styledBy("static/thymekit/ui.css", "static/thymekit/base/canvas.css",
                "static/thymekit/base/section.css", "static/thymekit/elements/hero.css",
                "static/thymekit/elements/heading.css", "static/thymekit/elements/caption.css",
                "static/thymekit/elements/md-section.css")
            .check();
    }

    @Test
    void anAddressThatPointsNowhereIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("fragments/my/absent", "priceEl").build()).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no template on the classpath");

        assertThatThrownBy(() -> ElementContract.of(Element.raw("fragments/thymekit/heading", "absentEl").build()).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("declares no fragment absentEl");
    }

    @Test
    void anAdapterNamedLikeSomethingElseIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("fragments/thymekit/heading", "heading").build()).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("named like myCardEl");
    }

    @Test
    void aScriptAmongElementsIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Element.script("fragments/thymekit/heading", "headingEl")).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not belong among elements");
    }

    @Test
    void aClassWithoutARuleIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Heading.h2("Section"))
                .renderedBy(ENGINE).styledBy("static/thymekit/elements/caption.css").check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tk-heading").hasMessageContaining("styled by none");

        assertThatThrownBy(() -> ElementContract.of(Heading.h2("Section"))
                .renderedBy(ENGINE).styledBy("static/nowhere.css").check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no stylesheet on the classpath");
    }

    /** Templates elsewhere: a consumer says where they are, and the message says where it looked. */
    @Test
    void templatesUnderAnotherRoot() {
        Element<?> price = Element.raw("fragments/my/price", "priceEl").with("amount", "12.00").build();
        ElementContract.of(price).templatesUnder("views/").check();

        assertThatThrownBy(() -> ElementContract.of(price).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no template on the classpath")
            .hasMessageContaining("looked under templates/, the address itself");
    }

    /** A script an element depends on is walked as well: its fragment has to exist too. */
    @Test
    void aDependencyThatPointsNowhereIsNamed() {
        Element<?> withScript = Element.raw("fragments/thymekit/heading", "headingEl")
            .with("level", 2).with("text", "x")
            .requires(Element.script("fragments/my/absent", "priceJs")).build();
        assertThatThrownBy(() -> ElementContract.of(withScript).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("its script fragments/my/absent :: priceJs")
            .hasMessageContaining("no template on the classpath");

        Element<?> soundScript = Element.raw("fragments/thymekit/heading", "headingEl")
            .with("level", 2).with("text", "x")
            .requires(Element.script("fragments/my/price", "priceEl")).build();
        ElementContract.of(soundScript).templatesUnder("views/").check();
    }

    @Test
    void everythingWrongIsReportedAtOnce() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("fragments/my/absent", "price").build()).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("broken in 2 place(s)");

        assertThatThrownBy(ElementContract::of).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least one");
        assertThatThrownBy(() -> ElementContract.of((Composable<?>[]) null)).isInstanceOf(NullPointerException.class);
        assertThat(ElementContract.of(Heading.h2("x"))).isNotNull();     // structure alone is a legal walk
    }
}
