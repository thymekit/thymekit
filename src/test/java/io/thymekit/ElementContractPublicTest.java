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
                Md.of("**text**"),
                Section.of(Heading.h3("Description")).add(Md.of("text under it")),
                Hero.of(Heading.h1("Title")).subtitle(Caption.subtitle("RA-101")))
            .renderedBy(ENGINE)
            .styledBy(CssCanonTest.stylesheets())
            .check();
    }

    @Test
    void anAddressThatPointsNowhereIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("fragments/my/absent", "priceEl").build()).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no template on the classpath");

        assertThatThrownBy(() -> ElementContract.of(Element.raw("thymekit/heading", "absentEl").build()).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("declares no fragment absentEl");
    }

    /** The keys an adapter says it reads, against the keys an element carries — in both directions. */
    @Test
    void keysDeclaredAndKeysCarried() {
        Element<?> stranger = Element.raw("thymekit/heading", "headingEl")
            .with("level", 2).with("text", "x").with("colour", "gold").build();
        assertThatThrownBy(() -> ElementContract.of(stranger).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("carries the key \"colour\" that its adapter does not read");

        // one element of an adapter is not a claim about the whole adapter: silence unless asked
        ElementContract.of(Heading.h2("plain")).check();
        assertThatThrownBy(() -> ElementContract.of(Heading.h2("plain")).coveringEveryKey().check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("its adapter reads \"href\"").hasMessageContaining("nothing given here puts it in");

        // and an adapter that declares nothing is simply not checked for keys
        ElementContract.of(Element.raw("test/pieces", "echoEl").with("text", "x").with("anything", 1).build())
            .coveringEveryKey().check();
    }

    /**
     * A declaration stands above one fragment and speaks for it alone. The fragment after it declares
     * nothing of its own, so nothing about it is checked — rather than the keys of its neighbour.
     */
    @Test
    void aDeclarationSpeaksForOneFragmentOnly() {
        ElementContract.of(Element.raw("test/pieces", "undeclaredEl").with("anything", 1).build()).check();

        assertThatThrownBy(() -> ElementContract.of(
                Element.raw("test/pieces", "declaredEl").with("anything", 1).build()).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("carries the key \"anything\"");
    }

    /** A fragment named in a comment is prose: the walk reads the template without its comments. */
    @Test
    void aFragmentNamedOnlyInACommentIsNotAFragment() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/pieces", "ghostEl").build()).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("declares no fragment ghostEl");
    }

    /** A slot is declared like a key, and an element that fills one nobody renders is named. */
    @Test
    void slotsAreDeclaredAndMatched() {
        Element<?> strangeSlot = Element.raw("thymekit/section", "sectionEl")
            .with("heading", Heading.h2("Title").build().asMap())
            .slot("extra", java.util.List.of(Md.of("text")))
            .build();
        assertThatThrownBy(() -> ElementContract.of(strangeSlot).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("fills the slot \"extra\" that its adapter does not render");

        // and the other direction, when the samples claim to cover the adapter
        assertThatThrownBy(() -> ElementContract.of(Element.raw("thymekit/section", "sectionEl")
                .with("heading", Heading.h2("Title").build().asMap()).build())
                .coveringEveryKey().check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("slot items");
    }

    /**
     * Data that travels for nothing: the element carries a key, and the page is byte for byte the same
     * without it. Built by hand, since a builder that guards against it cannot produce one.
     */
    @Test
    void aKeyThatChangesNothingIsNamed() {
        Element<?> dead = Element.raw("thymekit/md", "mdEl")
            .with("markdown", "text a visitor wrote")
            .with("addAction", Caption.label("Add").build().asMap())
            .build();
        assertThatThrownBy(() -> ElementContract.of(dead).renderedBy(ENGINE).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("addAction").hasMessageContaining("renders exactly the same without it");
    }

    /**
     * An adapter that does not render is said to once. What it carries is not looked at afterwards:
     * against a page that never appeared every key looks unread, and a list of consequences buries the
     * one thing wrong.
     */
    @Test
    void anAdapterThatDoesNotRenderIsNamedOnce() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/broken", "emptyEl")
                .with("text", "x").build()).renderedBy(ENGINE).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("broken in 1 place(s)")
            .hasMessageContaining("renders nothing a browser would show");

        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/broken", "explodingEl")
                .with("text", "x").build()).renderedBy(ENGINE).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("broken in 1 place(s)")
            .hasMessageContaining("does not render");
    }

    @Test
    void anAdapterNamedLikeSomethingElseIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("thymekit/heading", "heading").build()).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("named like myCardEl");
    }

    @Test
    void aScriptAmongElementsIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Element.script("thymekit/heading", "headingEl")).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("does not belong among elements");
    }

    @Test
    void aClassWithoutARuleIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Heading.h2("Section"))
                .renderedBy(ENGINE).styledBy("static/thymekit/caption.css").check())
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
        Element<?> withScript = Element.raw("thymekit/heading", "headingEl")
            .with("level", 2).with("text", "x")
            .requires(Element.script("fragments/my/absent", "priceJs")).build();
        assertThatThrownBy(() -> ElementContract.of(withScript).check())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("its script fragments/my/absent :: priceJs")
            .hasMessageContaining("no template on the classpath");

        Element<?> soundScript = Element.raw("thymekit/heading", "headingEl")
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
