/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The trail of a page: where it sits in the site, said twice — once for a person and once for a
 * crawler — out of <b>one</b> list of steps.
 *
 * <p>That the two halves come from one list is the whole point rather than an implementation detail.
 * The rules a search engine publishes require the markup to be a true representation of what a visitor
 * sees, and a trail assembled twice is a trail that drifts: two sources, two chances to be edited, one
 * of them silently. Here there is nothing to drift.
 *
 * <p>The visible links stay exactly as they were written, relative and all. The machine-readable half
 * is where absolute addresses are wanted, and it gets them from an origin the consumer names once —
 * because absolute links in the page itself break the day the site moves to another host, to https, or
 * onto a staging machine.
 */
class BreadcrumbsTest {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Element<Breadcrumbs> trail) {
        return (List<Map<String, Object>>) trail.asMap().get("items");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listItems(Element<Breadcrumbs> trail) {
        Map<String, Object> node = (Map<String, Object>) trail.asMap().get("describes");
        return (List<Map<String, Object>>) node.get("itemListElement");
    }

    private static Element<Breadcrumbs> trail() {
        return Breadcrumbs.named("Breadcrumb").add("/ingredients", "Ingredients").current("Aloe");
    }

    // the visible half

    /** The name of the landmark comes from the consumer: a screen reader says it, in their language. */
    @Test
    void carriesTheNameOfTheLandmark() {
        assertThat(trail().asMap()).containsEntry("label", "Breadcrumb");
    }

    /** The steps, in order, each as what it is: a link goes somewhere, the last one does not. */
    @Test
    void carriesTheStepsInOrder() {
        assertThat(items(trail())).containsExactly(
            Map.of("url", "/ingredients", "label", "Ingredients"),
            Map.of("label", "Aloe"));
    }

    /** Addresses in the page are written as they were given: relative stays relative. */
    @Test
    void leavesTheVisibleAddressesAlone() {
        Element<Breadcrumbs> trail = Breadcrumbs.named("Breadcrumb").site("https://shop")
            .add("/ingredients", "Ingredients").current("Aloe");

        assertThat(items(trail).get(0)).containsEntry("url", "/ingredients");
    }

    /** A trail need not end where you are: ancestors alone are a trail too. */
    @Test
    void endsWithoutACurrentPageIfThatIsWhatWasWritten() {
        Element<Breadcrumbs> trail = Breadcrumbs.named("Breadcrumb").add("/ingredients", "Ingredients").build();

        assertThat(items(trail)).containsExactly(Map.of("url", "/ingredients", "label", "Ingredients"));
    }

    /** Two trails of the same steps are the same element — it is a value, like every element. */
    @Test
    void isAValue() {
        assertThat(trail()).isEqualTo(trail()).hasSameHashCodeAs(trail());
    }

    // the machine-readable half

    /** The node is a breadcrumb list, and the positions are counted from one, as the schema says. */
    @Test
    void describesItselfAsATrail() {
        Map<String, Object> node = (Map<String, Object>) trail().asMap().get("describes");

        assertThat(node).containsEntry("@type", "BreadcrumbList");
        assertThat(listItems(trail())).extracting(item -> item.get("position")).containsExactly(1, 2);
    }

    /**
     * A step that goes somewhere says where; the page you are on does not. A crawler takes the address
     * of the last step from the page it is reading, which is the one address it can be sure of.
     */
    @Test
    void onlyTheStepsThatGoSomewhereCarryAnAddress() {
        assertThat(listItems(trail())).containsExactly(
            Map.of("@type", "ListItem", "position", 1, "name", "Ingredients", "item", "/ingredients"),
            Map.of("@type", "ListItem", "position", 2, "name", "Aloe"));
    }

    /**
     * The rule the whole element is built to keep: as many steps in the graph as a visitor can see. A
     * search engine's own guidelines call markup that describes more or less than the page a
     * misrepresentation, and it is the kind that happens when the two halves are assembled apart.
     */
    @Test
    void saysAsMuchAsItShows() {
        Element<Breadcrumbs> long_ = Breadcrumbs.named("Breadcrumb")
            .add("/a", "A").add("/b", "B").add("/c", "C").current("D");

        assertThat(listItems(long_)).hasSameSizeAs(items(long_));
    }

    /** The vocabulary is the canvas's to name, so a trail does not carry one of its own. */
    @Test
    void leavesTheVocabularyToTheCanvas() {
        assertThat((Map<String, Object>) trail().asMap().get("describes")).doesNotContainKey("@context");
    }

    // the origin, and what it is for

    /** Given an origin, the addresses a crawler reads become absolute — and only those. */
    @Test
    void makesTheAddressesACrawlerReadsAbsolute() {
        Element<Breadcrumbs> trail = Breadcrumbs.named("Breadcrumb").site("https://shop")
            .add("/ingredients", "Ingredients").current("Aloe");

        assertThat(listItems(trail).get(0)).containsEntry("item", "https://shop/ingredients");
    }

    /** An address that is already absolute is left as it is: the origin fills a gap, it does not rewrite. */
    @Test
    void leavesAnAddressThatIsAlreadyAbsolute() {
        Element<Breadcrumbs> trail = Breadcrumbs.named("Breadcrumb").site("https://shop")
            .add("https://other/x", "Elsewhere").current("Aloe");

        assertThat(listItems(trail).get(0)).containsEntry("item", "https://other/x");
    }

    /**
     * Without an origin the graph carries what it was given. The standard resolves a relative address
     * against the page it is printed on, so this is defensible; it is not the default because the
     * search engines that read it do not say so anywhere.
     */
    @Test
    void withoutAnOriginTheGraphCarriesWhatItWasGiven() {
        assertThat(listItems(trail()).get(0)).containsEntry("item", "/ingredients");
    }

    /**
     * An address that names a host without naming a scheme starts with a slash too, and joining an
     * origin to it would produce a host inside a host. This project has been caught by that spelling
     * before, in another element and another guard.
     */
    @Test
    void leavesAnAddressThatAlreadyNamesAHost() {
        Element<Breadcrumbs> trail = Breadcrumbs.named("Breadcrumb").site("https://shop")
            .add("//cdn.example/x", "Elsewhere").current("Aloe");

        assertThat(listItems(trail).get(0)).containsEntry("item", "//cdn.example/x");
    }

    /**
     * An origin is where a site lives and nothing more. Given a path here, every step written from the
     * root would be joined behind that path — an address that resolves to somewhere else, silently.
     */
    @Test
    void refusesAnOriginThatCarriesAPath() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Breadcrumbs.named("Breadcrumb").site("https://shop/app"))
            .withMessageContaining("more than a site");
    }

    /** An origin is an origin: it has to be absolute, and it is refused where it is written. */
    @Test
    void refusesAnOriginThatIsNotAbsolute() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Breadcrumbs.named("Breadcrumb").site("/shop"))
            .withMessageContaining("absolute");
    }

    /** And it must not end in a slash, or every address under it would carry two. */
    @Test
    void refusesAnOriginWithATrailingSlash() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Breadcrumbs.named("Breadcrumb").site("https://shop/"))
            .withMessageContaining("slash");
    }

    // what is refused

    /** A landmark with no name is what the practices ask us to avoid, so it cannot be built. */
    @Test
    void refusesATrailWithNoName() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Breadcrumbs.named(" "))
            .withMessage("Breadcrumbs.named(label): is blank — a page shows what it was given, and this is nothing");
        assertThatExceptionOfType(MisuseException.class).isThrownBy(() -> Breadcrumbs.named(null))
            .withMessage("Breadcrumbs.named(label): was not given");
    }

    /** A trail with no steps is not a trail, and would print an empty landmark. */
    @Test
    void refusesATrailWithNoSteps() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Breadcrumbs.named("Breadcrumb").build())
            .withMessageContaining("empty");
    }

    /**
     * Nothing follows the page you are on. In a chain that is impossible to write — {@code current}
     * ends it — but a builder held in a variable could be asked twice, and the invariant is the trail's
     * rather than the chain's.
     */
    @Test
    void refusesAStepAfterThePageYouAreOn() {
        Breadcrumbs.Builder held = Breadcrumbs.named("Breadcrumb").add("/x", "X");
        held.current("Here");

        assertThatExceptionOfType(MisuseException.class).isThrownBy(() -> held.add("/y", "Y"))
            .withMessage("Breadcrumbs.add: the trail already ends at the page you are on");
        assertThatExceptionOfType(MisuseException.class).isThrownBy(() -> held.current("Again"))
            .as("the place is the call that was made, and current is not add")
            .withMessage("Breadcrumbs.current: the trail already ends at the page you are on");
    }

    /** A step is a step wherever it is written: the same guards as the value it becomes. */
    @Test
    void refusesAStepThatIsNotOne() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Breadcrumbs.named("Breadcrumb").add("/x", " "))
            .as("a step is refused at the call the caller wrote, not inside the value it becomes")
            .withMessage("Breadcrumbs.add(label): is blank — a page shows what it was given, and this is nothing");
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Breadcrumbs.named("Breadcrumb").add(" ", "X"))
            .withMessage("Breadcrumbs.add(url): is blank — a page shows what it was given, and this is nothing");
    }

    /**
     * A trail belongs to one site. Saying so twice with two answers is a mistake nobody would ever
     * catch reading the page — the second quietly wins, the graph a crawler reads names a host the
     * page never mentions, and everything looks exactly as it should.
     *
     * <p>Saying the same thing twice is not a mistake and is left alone: it costs nothing and refusing
     * it would make a builder held in a variable harder to write than one written in a chain.
     */
    @Test
    void aTrailBelongsToOneSite() {
        assertThatExceptionOfType(MisuseException.class)
            .isThrownBy(() -> Breadcrumbs.named("Where you are")
                .site("https://shop").site("https://other"))
            .withMessage("Breadcrumbs.site(origin): this trail already belongs to \"https://shop\":"
                + " one trail, one site");

        assertThatCode(() -> Breadcrumbs.named("Where you are").site("https://shop").site("https://shop"))
            .as("the same site said twice says the same thing").doesNotThrowAnyException();
    }

    /**
     * And where it is said does not matter. The graph is built when the trail is, so an origin named
     * before the steps and one named after them describe the same trail — which is the rule this kit
     * holds every option to: the order of the calls cannot change the result.
     */
    @Test
    void whereTheSiteIsSaidDoesNotMatter() {
        var before = Breadcrumbs.named("Where you are").site("https://shop")
            .add("/catalogue", "Catalogue").current("Aloe");
        var after = Breadcrumbs.named("Where you are")
            .add("/catalogue", "Catalogue").site("https://shop").current("Aloe");

        assertThat(after).isEqualTo(before);
    }
}