/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * The bar above a page: a way back, and where you are.
 *
 * <p>An element rather than something the consumer assembles, and the whole justification is in what it
 * guarantees. Assembled by hand, the way back lands inside the trail's landmark — and then the
 * navigation a screen reader announces as "breadcrumb" contains a step that is in neither the visible
 * trail nor the graph. Those guarantees are about rendered markup, so they are checked against rendered
 * markup here rather than inferred from a descriptor.
 */
class TopbarTest {

    private static final SpringTemplateEngine ENGINE = engine();

    private static SpringTemplateEngine engine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        return engine;
    }

    private static String render(Element<Topbar> bar) {
        var context = new Context();
        context.setVariable("e", bar.asMap());
        return ENGINE.process("thymekit/topbar", Set.of("topbarEl"), context);
    }

    private static Breadcrumbs.Builder trail() {
        return Breadcrumbs.named("Breadcrumb").add("/ingredients", "Ingredients");
    }

    private static Element<Topbar> bar() {
        return Topbar.of(trail().current("Aloe")).back("/ingredients", "All ingredients").build();
    }

    // what it carries

    /** The trail is the reason the bar exists, so it is what the bar is made of. */
    @Test
    void carriesTheTrail() {
        assertThat(bar().asMap()).containsEntry("crumbs", trail().current("Aloe").asMap());
    }

    /** The way back, when there is one: where it goes and what it says. */
    @Test
    void carriesTheWayBack() {
        assertThat(bar().asMap())
            .containsEntry("backHref", "/ingredients")
            .containsEntry("backLabel", "All ingredients");
    }

    /**
     * And when there is none, there is none: no key, rather than a key holding nothing. Asserted
     * alongside what the bar does carry — an assertion made only of absences is one an empty answer
     * would satisfy just as well.
     */
    @Test
    void carriesNoWayBackWhenNoneWasGiven() {
        assertThat(Topbar.of(trail().current("Aloe")).build().asMap())
            .containsKey("crumbs")
            .doesNotContainKey("backHref").doesNotContainKey("backLabel");
    }

    /** A maker is taken as readily as what it makes, and settled at once, as everywhere in the kit. */
    @Test
    void takesAMakerOrWhatItMade() {
        Element<Breadcrumbs> made = trail().current("Aloe");

        assertThat(Topbar.of(made).build()).isEqualTo(Topbar.of(() -> made).build());
    }

    /** Two bars of the same parts are the same element. */
    @Test
    void isAValue() {
        assertThat(bar()).isEqualTo(bar()).hasSameHashCodeAs(bar());
    }

    /**
     * The bar says nothing about itself for machines, and does not stand between the trail and the page:
     * what the trail contributes is found through the bar exactly as it would be found without it.
     */
    @Test
    void doesNotComeBetweenTheTrailAndTheGraph() {
        assertThat(Tree.describedBy(List.of(bar())))
            .isEqualTo(Tree.describedBy(List.of(trail().current("Aloe"))));
    }

    // what it guarantees in the markup

    /**
     * The one thing a hand-assembled bar gets wrong. Inside the trail's landmark, the way back would be
     * a step of a navigation it is not part of — announced as one, and absent from the graph.
     */
    @Test
    void theWayBackStandsOutsideTheTrail() {
        String html = render(bar());

        assertThat(html.indexOf("All ingredients")).as("the way back is printed")
            .isGreaterThan(0).isLessThan(html.indexOf("<nav"));
    }

    /** One landmark, not two: the bar is a wrapper, and a wrapper is not a place to jump to. */
    @Test
    void addsNoSecondLandmark() {
        assertThat(render(bar()).split("<nav", -1)).hasSize(2);
    }

    /** The mark beside the words is decoration, and says so, or a screen reader reads it out. */
    @Test
    void theMarkIsHiddenFromAssistiveTechnology() {
        assertThat(render(bar())).contains("aria-hidden=\"true\"");
    }

    /**
     * Without a way back the bar prints the trail and nothing else. Asserted on the parts of the bar
     * rather than on links in general — the trail inside it is made of links, and an assertion that
     * forgot as much would pass for the wrong reason.
     */
    @Test
    void printsNoShellWhereThereIsNoWayBack() {
        String html = render(Topbar.of(trail().current("Aloe")).build());

        assertThat(html).doesNotContain("tk-topbar-back").doesNotContain("aria-hidden");
        assertThat(html).as("the trail itself is still there").contains("Ingredients");
    }

    // what is refused

    /** A bar with no trail is a bar with nothing in it. */
    @Test
    void refusesABarWithNoTrail() {
        assertThatExceptionOfType(MisuseException.class).isThrownBy(() -> Topbar.of(null))
            .withMessage("Topbar.of(crumbs): was not given");
    }

    /** A way back with nothing written on it is a link nobody can read. */
    @Test
    void refusesAWayBackWithNoWords() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Topbar.of(trail().current("Aloe")).back("/x", " "))
            .withMessage("Topbar.back(label): is blank — a page shows what it was given, and this is nothing");
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Topbar.of(trail().current("Aloe")).back("/x", null))
            .withMessage("Topbar.back(label): was not given");
    }

    /** And one with nowhere to go is not a way back. */
    @Test
    void refusesAWayBackWithNoAddress() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Topbar.of(trail().current("Aloe")).back(" ", "Back"))
            .withMessage("Topbar.back(href): is blank — a page shows what it was given, and this is nothing");
    }

    /**
     * The address of a way back is often taken from where the visitor came from, which makes it the
     * least trusted string on the page. A scheme that executes instead of navigating is refused here,
     * however it is spelled.
     */
    @Test
    void refusesAWayBackThatExecutesInsteadOfNavigating() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Topbar.of(trail().current("Aloe")).back("java\tscript:alert(1)", "Back"))
            .withMessageContaining("script");
    }
}
