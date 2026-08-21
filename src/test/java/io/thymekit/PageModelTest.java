/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/** Contract of {@link PageModel} and {@link Element}: document properties, element flow, guards. */
class PageModelTest {

    private final Model model = new ConcurrentModel();

    /** The page under the key "page" is an element like any other: an address, and its flow as data. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> page(Model model) {
        return (Map<String, Object>) model.asMap().get("page");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> flowOf(Model model) {
        return (List<Map<String, Object>>) page(model).get("elements");
    }

    @Test @SuppressWarnings("unchecked")
    void render_titlePageClassAndElementsInAddOrder() {
        String view = PageModel.of(model).pageClass("page-public").title("Showcase")
            .add(Element.raw("uikit/sections", "intro").build())
            .add(Element.raw("uikit/sections", "outro").build())
            .render();
        assertThat(view).isEqualTo("page");
        assertThat(model.asMap().get("pageTitle")).isEqualTo("Showcase");
        assertThat(page(model)).containsEntry("template", "thymekit/canvas")
            .containsEntry("fragment", "canvasEl").containsEntry("pageClass", "page-public page-canvas");
        var padded = new ConcurrentModel();                                  // the consumer's own classes are trimmed
        PageModel.of(padded).pageClass(" page-public ").title("T").render();
        assertThat(page(padded)).containsEntry("pageClass", "page-public page-canvas");
        assertThat(flowOf(model)).extracting(e -> e.get("fragment")).containsExactly("intro", "outro");
        // a script element does not belong in the flow: its place is assets, declared via requires
        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("T").add(Element.script("t", "js")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("script element").hasMessageContaining("requires");
        assertThatThrownBy(() -> flowOf(model).add(Map.of())).isInstanceOf(UnsupportedOperationException.class);
    }

    /** Own document: the model is filled the same way, the given view name is returned. */
    @Test
    void render_ownView_sameModel_returnsGivenView() {
        String view = PageModel.of(model).pageClass("page-public").title("Showcase")
            .add(Element.raw("uikit/sections", "intro").build())
            .render("thymekit/demo");
        assertThat(view).isEqualTo("thymekit/demo");
        assertThat(model.asMap()).containsKeys("pageTitle", "head", "page", "assets");
        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("T").render(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("view");
    }

    @Test
    void admin_pageClass_emptyCollectionLegal() {
        assertThat(PageModel.of(model).pageClass("page-admin").title("t").render()).isEqualTo("page");
        assertThat(page(model)).containsEntry("pageClass", "page-admin page-canvas");
        assertThat(flowOf(model)).isEmpty();                                   // a page with nothing on it is legal
    }

    @Test
    void guards_titleRequired_nullsRejected() {
        assertThatThrownBy(() -> PageModel.of(model).pageClass("page-public").render())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> PageModel.of(model).pageClass("page-public").title(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> PageModel.of(model).pageClass("page-public").add(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("element");
        assertThatThrownBy(() -> PageModel.of(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("model");
        assertThatThrownBy(() -> PageModel.of(model).pageClass(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("classes");
        // with no classes of its own the canvas puts down only its marker
        var bare = new ConcurrentModel();
        PageModel.of(bare).title("T").render();
        assertThat(page(bare)).containsEntry("pageClass", "page-canvas");
    }

    /** What the page says about itself: one source for the tab, the search result and the preview. */
    @Test @SuppressWarnings("unchecked")
    void head_carriesWhatWasSaid_andNothingElse() {
        PageModel.of(model).title("Baobab").description("An oil from the tree of life")
            .canonical("https://shop/ingredients/baobab").image("https://shop/img/baobab.jpg").render();
        Map<String, Object> head = (Map<String, Object>) model.asMap().get("head");
        assertThat(head).containsEntry("template", "thymekit/head").containsEntry("fragment", "headEl")
            .containsEntry("title", "Baobab").containsEntry("description", "An oil from the tree of life")
            .containsEntry("canonical", "https://shop/ingredients/baobab")
            .containsEntry("image", "https://shop/img/baobab.jpg")
            .containsEntry("robots", "max-image-preview:large");   // a picture was declared; see below

        var bare = new ConcurrentModel();
        PageModel.of(bare).title("Draft").robots(PageModel.Robots.NOINDEX).render();
        Map<String, Object> quiet = (Map<String, Object>) bare.asMap().get("head");
        assertThat(quiet).containsEntry("title", "Draft").containsEntry("robots", "noindex")
            .doesNotContainKeys("description", "canonical", "image");   // said nothing, so nothing is printed
    }

    @Test @SuppressWarnings("unchecked")
    void head_refusesNullAndBlank() {
        PageModel.Canvas canvas = PageModel.of(model).title("t");
        assertThatThrownBy(() -> canvas.title("   ")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> canvas.pageClass("  ")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("classes");
        var padded = new ConcurrentModel();                                  // a space at the end of a title is never meant
        PageModel.of(padded).title(" Baobab ").description(" An oil ").render();
        assertThat((Map<String, Object>) padded.asMap().get("head"))
            .containsEntry("title", "Baobab").containsEntry("description", "An oil");
        assertThatThrownBy(() -> canvas.description(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("description");
        assertThatThrownBy(() -> canvas.canonical(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("canonical");
        assertThatThrownBy(() -> canvas.image(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("image");
        assertThatThrownBy(() -> canvas.description(" ")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("description");
        assertThatThrownBy(() -> canvas.canonical("")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("canonical");
        assertThatThrownBy(() -> canvas.image("  ")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("image");
    }

    /** The robots line: what was said, in the order it was said, without repetitions. */
    @Test @SuppressWarnings("unchecked")
    void robots_saysWhatWasSaid() {
        PageModel.of(model).title("t")
            .robots(PageModel.Robots.NOFOLLOW, PageModel.Robots.NOARCHIVE, PageModel.Robots.NOFOLLOW).render();
        assertThat((Map<String, Object>) model.asMap().get("head")).containsEntry("robots", "nofollow, noarchive");
        assertThat(PageModel.Robots.MAX_IMAGE_PREVIEW_LARGE.directive()).isEqualTo("max-image-preview:large");
        assertThat(PageModel.Robots.NOINDEX.directive()).isEqualTo("noindex");

        PageModel.Canvas canvas = PageModel.of(new ConcurrentModel()).title("t");
        assertThatThrownBy(canvas::robots).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("without a directive");
        assertThatThrownBy(() -> canvas.robots((PageModel.Robots[]) null)).isInstanceOf(NullPointerException.class);

        var twice = new ConcurrentModel();                                   // the last call is the whole line
        PageModel.of(twice).title("t").robots(PageModel.Robots.NOARCHIVE).robots(PageModel.Robots.NOFOLLOW).render();
        assertThat((Map<String, Object>) twice.asMap().get("head")).containsEntry("robots", "nofollow");
    }

    /** A picture the consumer already declared is worth showing large, unless the page is not indexed at all. */
    @Test @SuppressWarnings("unchecked")
    void robots_largePreviewFollowsThePicture() {
        PageModel.of(model).title("t").image("https://shop/img.jpg").render();
        assertThat((Map<String, Object>) model.asMap().get("head")).containsEntry("robots", "max-image-preview:large");

        var withDirectives = new ConcurrentModel();
        PageModel.of(withDirectives).title("t").image("https://shop/img.jpg").robots(PageModel.Robots.NOFOLLOW).render();
        assertThat((Map<String, Object>) withDirectives.asMap().get("head"))
            .containsEntry("robots", "nofollow, max-image-preview:large");

        var hidden = new ConcurrentModel();                       // not indexed: the size of a preview is moot
        PageModel.of(hidden).title("t").image("https://shop/img.jpg").robots(PageModel.Robots.NOINDEX).render();
        assertThat((Map<String, Object>) hidden.asMap().get("head")).containsEntry("robots", "noindex");

        var plain = new ConcurrentModel();                        // no picture, nothing said: no tag at all
        PageModel.of(plain).title("t").render();
        assertThat((Map<String, Object>) plain.asMap().get("head")).doesNotContainKey("robots");
    }

    /** An address that leaves the page is absolute or it is broken: no document, nothing to resolve against. */
    @Test
    void head_refusesARelativeAddressWhereItWouldNotBeResolved() {
        PageModel.Canvas canvas = PageModel.of(model).title("t");
        for (String relative : List.of("/ingredients/baobab", "ingredients/baobab", "//shop/img.jpg", "ftp://shop/img.jpg")) {
            assertThatThrownBy(() -> canvas.canonical(relative))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("canonical").hasMessageContaining(relative);
            assertThatThrownBy(() -> canvas.image(relative))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("image");
        }
        canvas.canonical("https://shop/baobab").image("HTTP://shop/img.jpg");     // the scheme is not case-sensitive
        canvas.canonical("http://shop/baobab").image("HTTPS://shop/img.jpg");
    }

    @Test
    void element_rawAndScript_descriptorShape() {
        Element<Element.Raw> e = Element.raw("uikit/sections", "chips")
            .with("plain", List.of("a")).with("removable", List.of("b")).build();
        assertThat(e.template()).isEqualTo("uikit/sections");
        assertThat(e.fragment()).isEqualTo("chips");
        assertThat(e.bare()).isFalse();
        assertThat(e.asMap()).containsEntry("template", "uikit/sections").containsEntry("fragment", "chips")
            .containsEntry("bare", false).containsEntry("plain", List.of("a")).containsEntry("removable", List.of("b"))
            .doesNotContainKey("type");                                                       
        assertThatThrownBy(() -> e.asMap().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
        Element<Element.Script> js = Element.script("fragments/ui/toggle", "toggleJs");
        assertThat(js.bare()).isTrue();
        assertThat(js.asMap()).containsEntry("bare", true);
        assertThatThrownBy(() -> Element.raw(null, "x")).isInstanceOf(NullPointerException.class).hasMessageContaining("template");
        assertThatThrownBy(() -> Element.script("x", null)).isInstanceOf(NullPointerException.class).hasMessageContaining("fragment");
        // the dispatcher turns the address into an expression, so an address is a path and a name and nothing else
        for (String bad : List.of("", " ", "t :: f", "t' + ${T(java.lang.Runtime)} + '", "-t", "t\n")) {
            assertThatThrownBy(() -> Element.raw(bad, "f")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adapter address");
            assertThatThrownBy(() -> Element.raw("t", bad)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("adapter address");
        }
        assertThatThrownBy(() -> Element.raw("t", "f-g")).isInstanceOf(IllegalArgumentException.class);   // a fragment is a java-ish name
        assertThat(Element.raw("fragments/my/card-v2.inner", "cardEl").build().fragment()).isEqualTo("cardEl");
    }

    @Test
    void element_with_reservedAndNullGuards() {
        Element.Descriptor<Element.Raw> b = Element.raw("t", "f");
        for (String reserved : List.of("template", "fragment", "bare", "slots")) {
            assertThatThrownBy(() -> b.with(reserved, "x"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(reserved);
        }
        assertThatThrownBy(() -> b.with(null, "x")).isInstanceOf(NullPointerException.class).hasMessageContaining("key");
        assertThatThrownBy(() -> b.with("data", null)).isInstanceOf(NullPointerException.class).hasMessageContaining("data");
    }

    /** Slots: filled by the descriptor, read back by name, snapshotted on build. */
    @Test
    void element_slots_standard() {
        Element<Element.Raw> a = Element.raw("t", "a").build();
        Element<Element.Raw> b = Element.raw("t", "b").build();
        Element.Descriptor<Element.Raw> d = Element.raw("t", "f").slot("header", List.of(a, b)).slot("footer", List.of());
        Element<Element.Raw> e = d.build();
        assertThat(e.slot("header")).containsExactly(a.asMap(), b.asMap());
        assertThat(e.slot("footer")).isEmpty();
        assertThat(e.slot("nope")).isEmpty();
        assertThat(Element.raw("t", "f").build().slot("header")).isEmpty();          // no slots at all
        @SuppressWarnings("unchecked")
        Map<String, Object> slots = (Map<String, Object>) e.asMap().get("slots");
        assertThat(slots).containsOnlyKeys("header", "footer");
        d.slot("header", List.of(a));                                              // the builder changes, the snapshot does not
        assertThat(e.slot("header")).hasSize(2);
        assertThatThrownBy(() -> slots.put("x", List.of())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> d.slot(null, List.of())).isInstanceOf(NullPointerException.class).hasMessageContaining("name");
        assertThatThrownBy(() -> d.slot("s", null)).isInstanceOf(NullPointerException.class).hasMessageContaining("items");
        assertThatThrownBy(() -> e.slot(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("name");
    }

    /** Whatever becomes an element is taken; what has become one is taken too, and nothing half-made is kept. */
    @Test @SuppressWarnings("unchecked")
    void composable_isAcceptedAndSettledAtOnce() {
        var builder = Caption.meta("first");
        PageModel.of(model).title("T").add(builder).add(Caption.meta("second").build()).render();
        assertThat(flowOf(model)).extracting(e -> e.get("text")).containsExactly("first", "second");

        builder.time(java.time.LocalDate.of(2026, 3, 12));                 // the maker goes on; the page does not
        assertThat(flowOf(model).get(0)).doesNotContainKey("datetime");

        Element<Caption> settled = Caption.label("x").build();
        assertThat(settled.build()).isSameAs(settled);                     // an element has already become one
        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("T").add(() -> null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("built nothing");
        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("T").add((Composable<?>) null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("element");
    }

    /** Value semantics: equal descriptors mean equal elements, which is what deduplication relies on. */
    @Test
    void element_valueSemantics_equalsHashToString() {
        Element<Caption> a = Caption.label("x").build();
        Element<Caption> b = Caption.label("x").build();
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(Caption.label("y").build()).isNotEqualTo("x");
        assertThat(a.hashCode()).isEqualTo(a.asMap().hashCode()).isNotZero();   // hash comes from the descriptor
        assertThat(new HashSet<>(List.of(a, b))).hasSize(1);
        assertThat(a.toString()).startsWith("Element{").contains("text=x");
    }

    /** Assets: elements declare them, the tree collects them deduplicated in traversal order. */
    @Test
    void element_assets_declaredCollectedDeduped() {
        Element<Element.Script> js = Element.script("t", "js");
        Element<Element.Script> js2 = Element.script("t", "js2");
        Element<Element.Raw> leaf = Element.raw("t", "leaf").requires(js).build();
        assertThat(leaf.assets()).containsExactly(js);
        assertThat(Element.raw("t", "x").build().assets()).isEmpty();
        // nested: a container without dependencies of its own still carries those of its children
        Element<?> row = Element.raw("t", "row").slot("items", List.of(leaf,
            Element.raw("t", "s").slot("body", List.of(Element.raw("t", "l2").requires(js2, js).build())).build(), leaf)).build();
        assertThat(row.assets()).containsExactly(js, js2);
        assertThat(Element.assetsOf(List.of(row, leaf, java.util.Collections.singletonList(null)))).containsExactly(js, js2);
        // an element without declared dependencies carries none
        assertThat(Heading.h2("t").build().assets()).isEmpty();
    }

    /** Outline guard: at most one H1 in the tree; illustrations do not count; no H1 is legal. */
    @Test
    void assertOutline_singleH1_skipsIllustrations() {
        Element<?> hero = Hero.of(Heading.h1("Page").build()).eyebrow(Caption.eyebrow("l").build()).build();
        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("T").add(hero).add(Heading.h1("Second").build()).render())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("more than one H1").hasMessageContaining("Second");
        // an H1 nested in a container is seen by the guard too
        assertThatThrownBy(() -> Element.assertOutline(List.of(hero,
            Element.raw("t", "row").slot("items", List.of(Heading.h1("x").build())).build())))
            .isInstanceOf(IllegalStateException.class);
        // an illustration is not structure: a hero inside a sample does not count
        Element<?> sample = Element.raw("t", "sample").illustration()
            .slot("live", List.of(Hero.of(Heading.h1("sample").build()).eyebrow(Caption.eyebrow("l").build()).build())).build();
        Element.assertOutline(List.of(hero, sample));
        Element.assertOutline(List.of());                                                                 // no H1 is legal
        assertThatThrownBy(() -> Element.raw("t", "f").with("illustration", true)).isInstanceOf(IllegalArgumentException.class);   // reserved key
        assertThat(Element.raw("t", "f").illustration().build().asMap()).containsEntry("illustration", true);
        assertThat(PageModel.of(new ConcurrentModel()).title("T").add(hero).add(sample).render()).isEqualTo("page");
    }

    /** A level counts however it was written, and a level html does not have is refused. */
    @Test
    void assertOutline_readsLevelsHoweverWritten_andRefusesOnesHtmlHasNot() {
        Element<?> hero = Hero.of(Heading.h1("Page").build()).build();
        Element<?> textLevel = Element.raw("thymekit/heading", "headingEl")
            .with("level", "1").with("text", "sneaky").build();          // renders <h1>, written as text
        assertThatThrownBy(() -> Element.assertOutline(List.of(hero, textLevel)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("more than one H1").hasMessageContaining("sneaky");

        Element.assertOutline(List.of(hero, Element.raw("thymekit/heading", "headingEl")
            .with("level", " 2 ").with("text", "spaced").build()));       // text with spaces still reads as a level

        for (Object impossible : List.of(7, "7", 0L)) {
            assertThatThrownBy(() -> Element.assertOutline(List.of(Element.raw("thymekit/heading", "headingEl")
                .with("level", impossible).with("text", "x").build())))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("outside h1..h6");
        }
        Element.assertOutline(List.of(Heading.h1("a").build(), Heading.h2("b").build(), Heading.h3("c").build(),
            Heading.h4("d").build(), Heading.h5("e").build(), Heading.h6("f").build()));   // all six are legal
        Element.assertOutline(List.of(Element.raw("thymekit/heading", "headingEl")
            .with("level", "two").with("text", "x").build()));            // not a level at all: not the guard's business
        Element.assertOutline(List.of(Element.raw("thymekit/heading", "headingEl")
            .with("text", "x").build()));                                 // no level key either
    }

    /** The other half of the outline: the levels a page uses have to be contiguous, wherever they stand. */
    @Test
    void assertOutline_refusesASkippedLevel() {
        Element<?> hero = Hero.of(Heading.h1("Page").build()).build();
        Element.assertOutline(List.of(hero, Heading.h2("Section").build(), Heading.h3("Under it").build()));
        Element.assertOutline(List.of(Heading.h2("A").build(), Heading.h3("B").build()));   // a page may start at h2
        Element.assertOutline(List.of(hero));                                          // one level alone is contiguous

        assertThatThrownBy(() -> Element.assertOutline(List.of(hero, Heading.h3("Deep").build())))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("h2").hasMessageContaining("[1, 3]");
        assertThatThrownBy(() -> Element.assertOutline(List.of(hero, Heading.h2("S").build(), Heading.h4("Deep").build())))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("h3");
        // the hole is found wherever the heading sits: nesting does not hide it, and order does not matter
        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("T")
            .add(Element.raw("t", "row").slot("items", List.of(Heading.h4("Deep").build())).build())
            .add(hero).render())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("h2");
    }

    @Test
    void elementFactories_selfRegisteringDescriptors() {
        assertThat(Md.of("**text**").build().asMap())
            .containsEntry("template", "thymekit/md").containsEntry("fragment", "mdEl")
            .containsEntry("markdown", "**text**")
            .doesNotContainKey("heading");                 // a heading belongs to the section around it
        // the text may be absent: then the section lives by its empty state
        assertThat(Md.of(null).emptyHint("No description yet").build().asMap())
            .doesNotContainKey("markdown").containsEntry("emptyHint", "No description yet");
        Element<?> action = Element.raw("t", "add").build();
        assertThat(Md.of("x").addAction(action).build().asMap())
            .containsEntry("addAction", action.asMap());   // the affordance is an element, not a URL
        assertThatThrownBy(() -> Md.of("x").addAction(Element.script("t", "js")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("script element");
        // the section owns the heading now, and takes nothing else in its place
        assertThatThrownBy(() -> Section.of(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("heading");
        @SuppressWarnings("unchecked")
        Element<Heading> notTitle = (Element<Heading>) (Element<?>) Element.raw("t", "f").build();
        assertThatThrownBy(() -> Section.of(notTitle))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("heading only");
        assertThatThrownBy(() -> Section.of(Heading.h2("t")).add(Element.script("t", "js")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("script element");
        assertThat(Section.of(Heading.h2("Composition").id("composition")).add(Md.of("text")).build().asMap())
            .containsEntry("template", "thymekit/section").containsEntry("fragment", "sectionEl");

        Map<String, Object> hero = Hero.of(Heading.h1("Showcase").build()).eyebrow(Caption.eyebrow("thymekit").build())
            .meta(Caption.meta("meta").build()).build().asMap();
        assertThat(hero).containsEntry("template", "thymekit/hero").containsEntry("fragment", "heroEl")
            .containsEntry("heading", Heading.h1("Showcase").build().asMap())
            .containsEntry("eyebrow", Caption.eyebrow("thymekit").build().asMap())
            .containsEntry("metas", List.of(Caption.meta("meta").build().asMap()));
        assertThat(Hero.of(Heading.h1("t").build()).build().asMap())                                          // the core is the H1 alone
            .doesNotContainKey("eyebrow").doesNotContainKey("subtitle").doesNotContainKey("metas").doesNotContainKey("badge");
        assertThat(Hero.of(Heading.h1("t").build()).subtitle(Caption.subtitle("RA-101 · Cream").build())
            .meta(Caption.meta("/x").build(), Caption.meta("12 entries").build()).build().asMap())
            .containsEntry("subtitle", Caption.subtitle("RA-101 · Cream").build().asMap())
            .containsEntry("metas", List.of(Caption.meta("/x").build().asMap(), Caption.meta("12 entries").build().asMap()));
        // guards: H1 only, caption roles, nulls; badge and actions are checked by adapter address
        assertThatThrownBy(() -> Hero.of(Heading.h2("t").build())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("H1 only");
        @SuppressWarnings("unchecked")
        Element<Heading> notHeading = (Element<Heading>) (Element<?>) Element.raw("t", "f").build();
        assertThatThrownBy(() -> Hero.of(notHeading)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("heading only");
        assertThatThrownBy(() -> Hero.of(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("heading");
        assertThatThrownBy(() -> Hero.of(Heading.h1("t").build()).eyebrow(Caption.meta("x").build()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("eyebrow").hasMessageContaining("meta");
        assertThatThrownBy(() -> Hero.of(Heading.h1("t").build()).subtitle(Caption.label("x").build())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Hero.of(Heading.h1("t").build()).meta(Caption.eyebrow("x").build())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Hero.of(Heading.h1("t").build()).eyebrow(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("caption");
        assertThatThrownBy(() -> Hero.of(Heading.h1("t").build()).badge(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("badge");
        // the adapter guard lets through an element with the right address, wherever the element lives
        assertThat(Hero.of(Heading.h1("t").build())
            .badge(Element.raw("fragments/ui/status-badge", "statusBadgeEl").build())
            .actions(Element.raw("fragments/ui/actions", "actionsEl").build()).build().asMap())
            .containsKey("badge").containsKey("actions");
        assertThatThrownBy(() -> Hero.of(Heading.h1("t").build()).badge(Element.raw("t", "f").build()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("status badge");
        assertThatThrownBy(() -> Hero.of(Heading.h1("t").build()).actions(Element.raw("t", "f").build()))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("action row");
    }

    @Test
    void heading_factoriesH1toH6_idHrefSrOnly_guards() {
        assertThat(Heading.h2("Topbar and hero").build().asMap())
            .containsEntry("template", "thymekit/heading").containsEntry("fragment", "headingEl")
            .containsEntry("text", "Topbar and hero").containsEntry("level", 2)
            .doesNotContainKey("id").doesNotContainKey("href").doesNotContainKey("srOnly");
        assertThat(Heading.h3("Subsection").id("badges").build().asMap()).containsEntry("level", 3).containsEntry("id", "badges");
        assertThat(Heading.h1("x").build().asMap()).containsEntry("level", 1);      // H1 — hero
        assertThat(Heading.h4("x").build().asMap()).containsEntry("level", 4);
        assertThat(Heading.h5("x").build().asMap()).containsEntry("level", 5);
        assertThat(Heading.h6("x").build().asMap()).containsEntry("level", 6);
        assertThat(Heading.h3("Name").href("/p/7").build().asMap()).containsEntry("href", "/p/7");   // heading as a link
        assertThat(Heading.h2("hidden").srOnly().build().asMap()).containsEntry("srOnly", true);   // present in the outline, invisible on screen
        assertThat(Heading.of(2, "x").build()).isEqualTo(Heading.h2("x").build());                 // numeric level, for hosts that compute it
        assertThatThrownBy(() -> Heading.of(0, "x")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1..6");
        assertThatThrownBy(() -> Heading.of(7, "x")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("1..6");
        assertThat(Heading.of(1, "x").build().asMap()).containsEntry("level", 1);   // 1 and 6 are legal bounds
        assertThat(Heading.of(6, "x").build().asMap()).containsEntry("level", 6);
        assertThatThrownBy(() -> Heading.h2(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("text");
        assertThatThrownBy(() -> Heading.h2("x").id(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("id");
        assertThatThrownBy(() -> Heading.h2("x").href(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("href");
    }

    /** An element is its descriptor: a collection handed to the builder cannot change it afterwards. */
    @Test @SuppressWarnings("unchecked")
    void element_valuesAreSnapshots_howeverDeep() {
        var items = new java.util.ArrayList<>(List.of("a"));
        var nested = new java.util.HashMap<String, Object>();
        nested.put("inner", items);
        Element<Element.Raw> e = Element.raw("t", "f").with("items", items).with("nested", nested).build();

        items.add("b");                                                   // the caller keeps mutating what it passed
        nested.put("late", "x");
        assertThat((List<String>) e.asMap().get("items")).containsExactly("a");
        assertThat((Map<String, Object>) e.asMap().get("nested")).containsOnlyKeys("inner");
        assertThat((List<String>) ((Map<String, Object>) e.asMap().get("nested")).get("inner")).containsExactly("a");
        assertThat(e).isEqualTo(Element.raw("t", "f").with("items", List.of("a"))
            .with("nested", Map.of("inner", List.of("a"))).build());      // still equal to its twin

        assertThatThrownBy(() -> ((List<String>) e.asMap().get("items")).add("c"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThat(Element.raw("t", "f").with("holes", java.util.Arrays.asList("a", null)).build().asMap())
            .containsEntry("holes", java.util.Arrays.asList("a", null));   // a list with a hole is a page's business
        assertThat(Element.raw("t", "f").with("n", 42).build().asMap()).containsEntry("n", 42);
    }
}
