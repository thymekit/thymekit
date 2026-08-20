/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/** Contract of {@link PageModel} and {@link Element}: document properties, element flow, guards. */
class PageModelTest {

    private final Model model = new ConcurrentModel();

    @Test @SuppressWarnings("unchecked")
    void render_titlePageClassAndElementsInAddOrder() {
        String view = PageModel.of(model).pageClass("page-public").title("Showcase")
            .add(Element.raw("uikit/sections", "intro").build())
            .add(Element.raw("uikit/sections", "outro").build())
            .render();
        assertThat(view).isEqualTo("page");
        assertThat(model.asMap().get("pageTitle")).isEqualTo("Showcase");
        assertThat(model.asMap().get("pageClass")).isEqualTo("page-public page-canvas");
        List<Map<String, Object>> elements = (List<Map<String, Object>>) model.asMap().get("elements");   
        assertThat(elements).extracting(e -> e.get("fragment")).containsExactly("intro", "outro");
        // a script element does not belong in the flow: its place is assets, declared via requires
        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("T").add(Element.script("t", "js")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("script element").hasMessageContaining("requires");
        assertThatThrownBy(() -> elements.add(Map.of())).isInstanceOf(UnsupportedOperationException.class);
    }

    /** Own document: the model is filled the same way, the given view name is returned. */
    @Test
    void render_ownView_sameModel_returnsGivenView() {
        String view = PageModel.of(model).pageClass("page-public").title("Showcase")
            .add(Element.raw("uikit/sections", "intro").build())
            .render("thymekit/demo");
        assertThat(view).isEqualTo("thymekit/demo");
        assertThat(model.asMap()).containsKeys("pageTitle", "pageClass", "elements", "assets");
        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("T").render(null))
            .isInstanceOf(NullPointerException.class).hasMessageContaining("view");
    }

    @Test
    void admin_pageClass_emptyCollectionLegal() {
        assertThat(PageModel.of(model).pageClass("page-admin").title("t").render()).isEqualTo("page");
        assertThat(model.asMap().get("pageClass")).isEqualTo("page-admin page-canvas");
        assertThat((List<?>) model.asMap().get("elements")).isEmpty();
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
        // без своих классов канвас ставит только свой маркер
        var bare = new ConcurrentModel();
        PageModel.of(bare).title("T").render();
        assertThat(bare.getAttribute("pageClass")).isEqualTo("page-canvas");
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

    /** Value semantics: equal descriptors mean equal elements, which is what deduplication relies on. */
    @Test
    void element_valueSemantics_equalsHashToString() {
        Element<Caption> a = Caption.label("x").build();
        Element<Caption> b = Caption.label("x").build();
        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b).isNotEqualTo(Caption.label("y").build()).isNotEqualTo("x");
        assertThat(a.hashCode()).isEqualTo(a.asMap().hashCode()).isNotZero();   // hash comes from the descriptor
        assertThat(new java.util.HashSet<>(List.of(a, b))).hasSize(1);
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
    void assertSingleH1_guard_skipsIllustrations() {
        Element<?> hero = Hero.of(Heading.h1("Page").build()).eyebrow(Caption.eyebrow("l").build()).build();
        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("T").add(hero).add(Heading.h1("Second").build()).render())
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("more than one H1").hasMessageContaining("Second");
        // an H1 nested in a container is seen by the guard too
        assertThatThrownBy(() -> Element.assertSingleH1(List.of(hero,
            Element.raw("t", "row").slot("items", List.of(Heading.h1("x").build())).build())))
            .isInstanceOf(IllegalStateException.class);
        // an illustration is not structure: a hero inside a sample does not count
        Element<?> sample = Element.raw("t", "sample").illustration()
            .slot("live", List.of(Hero.of(Heading.h1("sample").build()).eyebrow(Caption.eyebrow("l").build()).build())).build();
        Element.assertSingleH1(List.of(hero, sample));
        Element.assertSingleH1(List.of());                                                                 // no H1 is legal
        assertThatThrownBy(() -> Element.raw("t", "f").with("illustration", true)).isInstanceOf(IllegalArgumentException.class);   // reserved key
        assertThat(Element.raw("t", "f").illustration().build().asMap()).containsEntry("illustration", true);
        assertThat(PageModel.of(new ConcurrentModel()).title("T").add(hero).add(sample).render()).isEqualTo("page");
    }

    @Test
    void elementFactories_selfRegisteringDescriptors() {
        assertThat(Md.of("**text**").title(Heading.h2("Description").build()).build().asMap())
            .containsEntry("template", "fragments/thymekit/md-section").containsEntry("fragment", "mdSectionEl")
            .containsEntry("markdown", "**text**")
            .containsEntry("heading", Heading.h2("Description").build().asMap());   
        assertThat(Md.of("x").build().asMap()).doesNotContainKey("heading");
        // the text may be absent: then the section lives by its empty state
        assertThat(Md.of(null).emptyHint("No description yet").build().asMap())
            .doesNotContainKey("markdown").containsEntry("emptyHint", "No description yet");
        Element<?> action = Element.raw("t", "add").build();
        assertThat(Md.of("x").addAction(action).build().asMap())
            .containsEntry("addAction", action.asMap());   // the affordance is an element, not a URL
        assertThatThrownBy(() -> Md.of("x").addAction(Element.script("t", "js")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("script element");
        assertThatThrownBy(() -> Md.of("x").title(null)).isInstanceOf(NullPointerException.class).hasMessageContaining("heading");
        @SuppressWarnings("unchecked")
        Element<Heading> notTitle = (Element<Heading>) (Element<?>) Element.raw("t", "f").build();
        assertThatThrownBy(() -> Md.of("x").title(notTitle))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("heading only");

        Map<String, Object> hero = Hero.of(Heading.h1("Showcase").build()).eyebrow(Caption.eyebrow("thymekit").build())
            .meta(Caption.meta("meta").build()).build().asMap();
        assertThat(hero).containsEntry("template", "fragments/thymekit/hero").containsEntry("fragment", "heroEl")
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
            .containsEntry("template", "fragments/thymekit/heading").containsEntry("fragment", "headingEl")
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


}
