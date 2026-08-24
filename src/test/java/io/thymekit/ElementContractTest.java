/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * The walk over a triple, and the promise that whoever writes an element gets the same one the kit
 * takes over its own.
 *
 * <p>A triple is held together by nothing a compiler can see: a java factory names an address, a
 * template declares a fragment at that address, a stylesheet has rules for the classes it prints. Every
 * one of those joints is a string, and every one of them can be renamed on one side alone. This is the
 * walk that refuses to let that pass — and each statement below is written against a fixture that is
 * wrong in exactly one way, so what the walk says can be read off the fixture rather than guessed.
 */
class ElementContractTest {

    private static final SpringTemplateEngine ENGINE = engine();

    private static SpringTemplateEngine engine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.addDialect(new TidyDialect());
        engine.addDialect(new MarkdownDialect(new MarkdownRenderer()));
        return engine;
    }

    // ——— the joints of a triple ——————————————————————————————————————————————————————————

    /** An address that points at no template is named, and the walk says where it looked. */
    @Test
    void anAddressThatPointsNowhereIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("fragments/my/absent", "priceEl")).check())
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageStartingWith("ElementContract.check:")
            .hasMessageContaining("no template on the classpath")
            .hasMessageContaining("looked under templates/, the address itself");
    }

    /** A template that declares no such fragment is named, and told what the dispatcher will call. */
    @Test
    void aTemplateThatDeclaresNoSuchFragmentIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/pieces", "absentEl")).check())
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("declares no fragment absentEl").hasMessageContaining("one argument");
    }

    /** A fragment named in a comment is prose: the walk reads a template without its comments. */
    @Test
    void aFragmentNamedOnlyInACommentIsNotAFragment() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/pieces", "ghostEl")).check())
            .isInstanceOf(ContractBrokenException.class).hasMessageContaining("declares no fragment ghostEl");
    }

    /** An adapter is named for what it renders and ends in El; a second contract gets a version suffix. */
    @Test
    void anAdapterIsNamedLikeAnAdapter() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/pieces", "notAnAdapter")).check())
            .isInstanceOf(ContractBrokenException.class).hasMessageContaining("named like myCardEl");
        assertThatCode(() -> ElementContract.of(Element.raw("test/pieces", "echoEl").with("text", "x")).check())
            .doesNotThrowAnyException();
    }

    /** A script element is not part of the flow, and a script an element depends on has to resolve too. */
    @Test
    void aScriptIsCheckedAsADependencyAndRefusedAsAnElement() {
        assertThatThrownBy(() -> ElementContract.of(Element.script("test/pieces", "echoEl")).check())
            .isInstanceOf(ContractBrokenException.class).hasMessageContaining("does not belong among elements");

        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/pieces", "echoEl").with("text", "x")
                .requires(Element.script("fragments/my/absent", "priceJs"))).check())
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("its script fragments/my/absent :: priceJs")
            .hasMessageContaining("no template on the classpath");
    }

    // ——— what an adapter says it reads ————————————————————————————————————————————————————

    /** A key an element carries that its adapter never reads is data travelling for nothing. */
    @Test
    void aKeyTheAdapterDoesNotReadIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(
                Element.raw("test/pieces", "echoEl").with("text", "x").with("colour", "gold")).check())
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("carries the key \"colour\" that its adapter does not read");
    }

    /** And a slot it fills that its adapter never renders is the same mistake, one level up. */
    @Test
    void aSlotTheAdapterDoesNotRenderIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/pieces", "echoEl").with("text", "x")
                .slot("items", List.of(Caption.label("inside")))).check())
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("fills the slot \"items\" that its adapter does not render");
    }

    /** Declare nothing and nothing is checked: the walk says so once rather than inventing a rule. */
    @Test
    void anAdapterThatDeclaresNothingIsNotAskedAboutKeys() {
        assertThatCode(() -> ElementContract.of(Element.raw("test/pieces", "undeclaredEl")
                .with("text", "x").with("anything", 1)
                .slot("whatever", List.of(Caption.label("inside")))).check())
            .doesNotThrowAnyException();
    }

    /**
     * A declaration speaks for the fragment beneath it and no other — and a fragment named in a comment
     * inside that window does not move the boundary, since a comment declares nothing anywhere.
     */
    @Test
    void aDeclarationSpeaksForOneFragmentOnly() {
        assertThatThrownBy(() -> ElementContract.of(
                Element.raw("test/broken", "maskedEl").with("text", "x").with("colour", "gold")).check())
            .as("the declaration read is the one nearest the fragment, and prose is not a fragment")
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("carries the key \"colour\"");
        assertThatCode(() -> ElementContract.of(
                Element.raw("test/broken", "maskedEl").with("text", "x")).check())
            .doesNotThrowAnyException();
    }

    /**
     * The other direction is a claim about the samples rather than about the element, so it is asked
     * for: say {@link ElementContract#coveringEveryKey()} and a branch nothing reaches is named.
     */
    @Test
    void whatNothingReachesIsNamedWhenTheSamplesClaimToCoverIt() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/pieces", "deadKeyEl").with("text", "x"))
                .coveringEveryKey().check())
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("reads \"colour\"").hasMessageContaining("nothing given here puts it in");

        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/pieces", "slottedEl").with("title", "x"))
                .coveringEveryKey().check())
            .as("a slot nothing fills is said as a slot, not as a key with a strange name")
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("renders the slot \"items\"").hasMessageContaining("nothing given here fills it");
    }

    // ——— what a page would see ————————————————————————————————————————————————————————————

    /** Given an engine, every element has to render something a browser would show. */
    @Test
    void anAdapterThatShowsNothingIsNamedOnce() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/broken", "emptyEl").with("text", "x"))
                .renderedBy(ENGINE).check())
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("broken in 1 place(s)").hasMessageContaining("renders nothing");

        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/broken", "explodingEl").with("text", "x"))
                .renderedBy(ENGINE).check())
            .as("and one that never rendered is worth one line, not a list about its keys")
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("broken in 1 place(s)").hasMessageContaining("does not render");
    }

    /** Data that travels for nothing: a key the page comes out the same without. */
    @Test
    void aKeyThatChangesNothingIsNamed() {
        Element<?> dead = Element.raw("thymekit/md", "mdEl")
            .with("markdown", "text a visitor wrote")
            .with("addAction", Caption.label("Add").build().asMap()).build();

        assertThatThrownBy(() -> ElementContract.of(dead).renderedBy(ENGINE).check())
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("addAction").hasMessageContaining("renders exactly the same without it");
    }

    /**
     * And a slot the page comes out the same without. A slot declared and never rendered is the quieter
     * half of the same defect: the declaration says it is read, the page says otherwise, and what a
     * consumer put in it is nowhere.
     */
    @Test
    void aSlotThatChangesNothingIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/pieces", "slotIgnoredEl")
                .with("title", "x").slot("items", List.of(Caption.label("inside"))))
                .renderedBy(ENGINE).check())
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("fills the slot \"items\"").hasMessageContaining("renders exactly the same");
    }

    /**
     * An empty slot is not a complaint. It renders nothing whether it is there or not, which says
     * something about the sample and nothing about the adapter — and a section with a heading and no
     * contents yet is a page a consumer legitimately builds.
     */
    @Test
    void anEmptySlotIsNotAComplaint() {
        assertThatCode(() -> ElementContract.of(Element.raw("test/pieces", "slottedEl")
                .with("title", "x").slot("items", List.of()))
                .renderedBy(ENGINE).check())
            .doesNotThrowAnyException();
    }

    /**
     * And where taking a key away breaks the adapter rather than changing the page, the key is read —
     * loudly. The walk is looking for data that travels for nothing, not for an adapter that would
     * rather fail than do without.
     */
    @Test
    void aKeyWhoseAbsenceBreaksTheAdapterIsRead() {
        assertThatCode(() -> ElementContract.of(Element.raw("test/pieces", "needsItEl").with("text", "words"))
                .renderedBy(ENGINE).check())
            .doesNotThrowAnyException();
    }

    /** An adapter may declare slots and no keys, or print words and no tag of its own. */
    @Test
    void anAdapterMayBeMadeOfSlotsAloneOrOfWordsAlone() {
        assertThatCode(() -> ElementContract.of(Element.raw("test/pieces", "slotsOnlyEl")
                .slot("items", List.of(Caption.label("inside")))).renderedBy(ENGINE).check())
            .doesNotThrowAnyException();

        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/pieces", "wordsOnlyEl").with("text", "words"))
                .renderedBy(ENGINE).check())
            .as("words with no tag around them are not something a browser shows as an element")
            .isInstanceOf(ContractBrokenException.class).hasMessageContaining("renders nothing");
    }

    /** Every class an element prints has a rule in the stylesheets named, and a missing one is said so. */
    @Test
    void aClassWithNoRuleIsNamed() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("test/pieces", "strangerEl").with("text", "x"))
                .renderedBy(ENGINE).styledBy("static/thymekit/ui.css").check())
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("not-a-kit-class").hasMessageContaining("styled by none");

        assertThatThrownBy(() -> ElementContract.of(Caption.label("x"))
                .renderedBy(ENGINE).styledBy("static/nowhere.css").check())
            .isInstanceOf(ContractBrokenException.class).hasMessageContaining("no stylesheet on the classpath");
    }

    // ——— the walk itself ——————————————————————————————————————————————————————————————————

    /** Templates elsewhere: a consumer says where they are, and their element walks like any other. */
    @Test
    void templatesUnderARootOfYourOwn() {
        var price = Element.raw("fragments/my/price", "priceEl").with("amount", "12.00")
            .requires(Element.script("fragments/my/price", "priceJs")).build();

        assertThatCode(() -> ElementContract.of(price).templatesUnder("views/").check())
            .doesNotThrowAnyException();
        assertThatThrownBy(() -> ElementContract.of(price).check())
            .isInstanceOf(ContractBrokenException.class).hasMessageContaining("no template on the classpath");
    }

    /** Everything wrong at once, because a walk that stopped at the first would be walked many times. */
    @Test
    void everythingWrongIsSaidAtOnce() {
        assertThatThrownBy(() -> ElementContract.of(Element.raw("fragments/my/absent", "price")).check())
            .isInstanceOf(ContractBrokenException.class).hasMessageContaining("broken in 2 place(s)");

        assertThatThrownBy(ElementContract::of)
            .isInstanceOf(MisuseException.class).hasMessageContaining("at least one");
        assertThatThrownBy(() -> ElementContract.of((Composable<?>[]) null))
            .isInstanceOf(MisuseException.class);
        assertThat(ElementContract.of(Caption.label("x"))).as("structure alone is a legal walk").isNotNull();
    }

    // ——— and the kit takes it over its own ————————————————————————————————————————————————

    /**
     * One live sample of every element the kit ships, walked the way a consumer walks theirs. This is
     * the only place in the project where an adapter is asked to render at all: every element's own
     * spec describes the descriptor it builds, and this describes what a page does with it.
     */
    static List<Composable<?>> samples() {
        var model = new org.springframework.ui.ConcurrentModel();
        PageModel.of(model).title("Page").description("What this page is")
            .canonical("https://shop/page").image("https://shop/page.jpg")
            .robots(PageModel.Robots.NOARCHIVE)
            .add(Heading.h1("Title"))
            // an element that says something about itself for machines, so the head carries a graph:
            // a key an adapter declares and no sample fills is a key this very walk refuses
            .add(Breadcrumbs.named("Breadcrumb").site("https://shop").add("/ingredients", "Ingredients")
                .current("Aloe"))
            .render();

        return List.of(
            Heading.h3("Section"),
            Heading.h2("Linked").id("linked").href("https://x/y").rel(Rel.NOFOLLOW).newTab().lang("en").srOnly(),
            Caption.eyebrow("Product"), Caption.subtitle("RA-101"), Caption.label("label"),
            Caption.meta("12 March 2026").time(java.time.LocalDate.of(2026, 3, 12)).lang("en-GB"),
            Md.of("**text**"),
            Md.of("[out](https://spam.example/x)").linkRel(Rel.UGC),
            Md.of(null).emptyHint("No description yet").addAction(Caption.label("Add")),
            Section.of(Heading.h2("Description")).add(Md.of("under a heading")),
            Hero.of(Heading.h1("Title")).eyebrow(Caption.eyebrow("Label")).subtitle(Caption.subtitle("RA-101"))
                .meta(Caption.meta("/slug"))
                .badge(Element.raw("test/pieces", "statusBadgeEl").with("text", "in stock").build())
                .actions(Element.raw("test/pieces", "actionsEl").with("text", "Buy").build()),
            Breadcrumbs.named("Breadcrumb").add("/ingredients", "Ingredients").current("Aloe"),
            Topbar.of(Breadcrumbs.named("Breadcrumb").current("Aloe")).back("/ingredients", "All ingredients"),
            fromModel(model, "head"), fromModel(model, "page"));
    }

    /** A page part the canvas built, taken back out of the model it was written into. */
    @SuppressWarnings("unchecked")
    private static Composable<?> fromModel(org.springframework.ui.Model model, String key) {
        var descriptor = (java.util.Map<String, Object>) model.asMap().get(key);
        assertThat(descriptor).as("a page part with slots or scripts needs a sample of its own, not this rebuild")
            .doesNotContainKeys("slots", "assets");
        var rebuilt = Element.raw((String) descriptor.get("template"), (String) descriptor.get("fragment"));
        descriptor.forEach((k, v) -> {
            if (!Element.RESERVED.contains(k)) {
                rebuilt.with(k, v);
            }
        });
        return rebuilt;
    }

    @Test
    void theKitKeepsTheWalkItHandsOut() {
        ElementContract.of(samples().toArray(Composable<?>[]::new))
            .coveringEveryKey()
            .renderedBy(ENGINE)
            .styledBy(kitStylesheets())
            .check();
    }

    /**
     * And no adapter of the kit is left out of that. The claim above is only as good as the list it
     * walks, so the templates are asked instead of a person remembering: every adapter the kit declares
     * has a sample, or this says which one does not.
     */
    @Test
    void everyAdapterOfTheKitHasASample() throws Exception {
        var templates = java.nio.file.Path.of(getClass().getResource("/templates/thymekit").toURI());
        var declared = new java.util.TreeSet<String>();
        try (var files = java.nio.file.Files.list(templates)) {
            // what is one level down (thymekit/demo/) is showcase furniture standing in for a
            // consumer's elements, and owes this nothing
            for (var file : files.filter(java.nio.file.Files::isRegularFile).toList()) {
                var found = java.util.regex.Pattern.compile("th:fragment=\"([a-zA-Z0-9]+El)\\(")
                    .matcher(java.nio.file.Files.readString(file));
                while (found.find()) {
                    declared.add(found.group(1));
                }
            }
        }
        assertThat(declared).contains("headingEl", "captionEl", "heroEl", "mdEl", "sectionEl", "headEl", "canvasEl");
        assertThat(samples()).extracting(sample -> sample.build().fragment()).containsAll(declared);
    }

    /**
     * A resource that exists and cannot be read is not swallowed. Unreachable through a classpath, so
     * it is asked of the seam that handles it: a walk that could not read a template would otherwise
     * report the element as declaring nothing, which is a verdict about the disk dressed as a verdict
     * about the element.
     */
    @Test
    void aTemplateThatCannotBeReadIsNotMistakenForAnEmptyOne() {
        var unreadable = new java.io.InputStream() {
            @Override
            public int read() throws java.io.IOException {
                throw new java.io.IOException("the disk said no");
            }
        };

        assertThatThrownBy(() -> ElementContract.textOf(unreadable, "templates/thymekit/heading.html"))
            .isInstanceOf(ContractBrokenException.class)
            .hasMessage("ElementContract.check: cannot read templates/thymekit/heading.html");
    }

    /**
     * An anchor an element declares is an anchor a browser can find. The page check holds every page to
     * that value; if the adapter never prints it as an id, the check guards a name that is not in the
     * document — and nothing else in the kit can notice, because only the walk sees what came out.
     */
    @Test
    void anAnchorDeclaredIsAnAnchorPrinted() {
        var saysButDoesNotPrint = Element.raw("fragments/my/silent", "silentEl")
            .anchor("slug", "in-the-south");

        assertThatThrownBy(() -> ElementContract.of(saysButDoesNotPrint).renderedBy(ENGINE).check())
            .isInstanceOf(ContractBrokenException.class)
            .hasMessageContaining("prints no id with it");

        assertThatCode(() -> ElementContract.of(Heading.h2("Composition").id("composition"))
                .renderedBy(ENGINE).styledBy(kitStylesheets()).check())
            .as("and the kit's own heading prints the one it declares").doesNotThrowAnyException();
    }

    /** The kit's own stylesheets, from the manifest rather than from a list somebody keeps. */
    private static String[] kitStylesheets() {
        var manifest = new java.util.ArrayList<>(List.of("static/thymekit/ui.css"));
        var imported = java.util.regex.Pattern.compile("@import url\\(\"([a-z0-9-]+\\.css)\"\\)")
            .matcher(resource("static/thymekit/ui.css"));
        while (imported.find()) {
            manifest.add("static/thymekit/" + imported.group(1));
        }
        return manifest.stream().distinct().toArray(String[]::new);
    }

    private static String resource(String path) {
        try (var in = ElementContractTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(in).as("resource %s", path).isNotNull();
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException unreadable) {
            throw new java.io.UncheckedIOException(unreadable);
        }
    }
}
