/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/**
 * The canvas: a page as a declaration, and the last place anything can be said about it before it is
 * served.
 *
 * <p>Two things make it different from every other element. It knows no list of bricks — whatever a
 * factory produced goes on it, which is why adding an element to the kit never touches this file. And
 * it is where the page stops being a composition and becomes a document: what a browser tab says, what
 * a search engine is told, what a messenger shows in a preview, and the two things no single element
 * can check — the outline of the whole page and its anchors.
 *
 * <p>What the head and the canvas print is checked where every adapter is, in the walk over triples;
 * here they are checked as what they are, two elements the canvas builds.
 */
class PageModelTest {

    private final Model model = new ConcurrentModel();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> part(Model model, String key) {
        return (Map<String, Object>) model.asMap().get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> flow(Model model) {
        return (List<Map<String, Object>>) part(model, "page").get("elements");
    }

    /**
     * What a document finds in the model: four keys, and two of them are elements. The page is one —
     * an address like any other, rendered by the same dispatcher — so composition is closed at the top
     * as well as at the bottom.
     */
    @Test
    void aRenderedPageIsFourKeysAndTwoOfThemAreElements() {
        String view = PageModel.of(model).title("Baobab")
            .add(Heading.h1("Baobab")).add(Caption.meta("12 entries")).render();

        assertThat(view).isEqualTo("page");
        assertThat(model.asMap()).containsOnlyKeys("pageTitle", "head", "page", "assets");
        assertThat(model.asMap().get("pageTitle")).isEqualTo("Baobab");
        assertThat(part(model, "page"))
            .containsEntry("template", "thymekit/canvas").containsEntry("fragment", "canvasEl")
            .containsEntry("pageClass", "page-canvas");
        assertThat(part(model, "head"))
            .containsEntry("template", "thymekit/head").containsEntry("fragment", "headEl");
        assertThat(flow(model)).extracting(e -> e.get("fragment")).containsExactly("headingEl", "captionEl");
    }

    /** A document of your own gets the same model and the name you gave it. */
    @Test
    void aDocumentOfYourOwnGetsTheSameModel() {
        assertThat(PageModel.of(model).title("T").render("ingredient-page")).isEqualTo("ingredient-page");
        assertThat(model.asMap()).containsKeys("pageTitle", "head", "page", "assets");
        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("T").render(null))
            .isInstanceOf(MisuseException.class).hasMessage("PageModel.render(view): was not given");
    }

    /** An unnamed browser tab is always a mistake, so a page without a title does not render. */
    @Test
    void aPageWithoutATitleDoesNotRender() {
        assertThatThrownBy(() -> PageModel.of(model).add(Heading.h1("Baobab")).render())
            .isInstanceOf(MisuseException.class).hasMessageContaining("title");
        assertThatThrownBy(() -> PageModel.of(model).title(null))
            .isInstanceOf(MisuseException.class).hasMessage("Canvas.title(title): was not given");
        assertThatThrownBy(() -> PageModel.of(model).title("   "))
            .isInstanceOf(MisuseException.class)
                .hasMessage("Canvas.title(title): is blank — a page shows what it was given, and this is nothing");
        assertThatThrownBy(() -> PageModel.of(null))
            .isInstanceOf(MisuseException.class).hasMessage("PageModel.of(model): was not given");
    }

    /**
     * Every text a page says about itself is trimmed and never blank. A space at the end of a title is
     * never meant, and it travels into the tab, the search result and the link preview alike — which is
     * the one place the kit does trim what it was given, since none of this is content.
     */
    @Test
    void whatAPageSaysAboutItselfIsTrimmed() {
        PageModel.of(model).title("  Baobab  ").description("  An oil from the tree of life  ").render();

        assertThat(part(model, "head"))
            .containsEntry("title", "Baobab").containsEntry("description", "An oil from the tree of life");
        assertThat(model.asMap().get("pageTitle")).isEqualTo("Baobab");

        PageModel.Canvas canvas = PageModel.of(new ConcurrentModel()).title("t");
        assertThatThrownBy(() -> canvas.description(" "))
            .isInstanceOf(MisuseException.class).hasMessageContaining("description");
        assertThatThrownBy(() -> canvas.description(null))
            .isInstanceOf(MisuseException.class).hasMessageContaining("description");
    }

    /** Said nothing, printed nothing: the head carries what it was told and no empty tags. */
    @Test
    void theHeadCarriesWhatItWasToldAndNothingElse() {
        PageModel.of(model).title("Draft").render();

        assertThat(part(model, "head")).containsEntry("title", "Draft")
            .doesNotContainKeys("description", "canonical", "image", "robots");
    }

    /**
     * An address that leaves the page is absolute or it is broken. A canonical link a browser could
     * resolve goes out as {@code og:url} as well, and a messenger scraping the page has no document to
     * resolve it against — so the failure is silent: no preview, or a canonical pointing at a stranger.
     */
    @Test
    void anAddressThatLeavesThePageIsAbsolute() {
        PageModel.of(model).title("Baobab")
            .canonical("https://shop/ingredients/baobab").image("https://shop/img/baobab.jpg").render();

        assertThat(part(model, "head"))
            .containsEntry("canonical", "https://shop/ingredients/baobab")
            .containsEntry("image", "https://shop/img/baobab.jpg");

        PageModel.Canvas canvas = PageModel.of(new ConcurrentModel()).title("t");
        for (String relative : List.of("/ingredients/baobab", "ingredients/baobab", "//shop/img.jpg",
                "ftp://shop/img.jpg")) {
            assertThatThrownBy(() -> canvas.canonical(relative))
                .isInstanceOf(MisuseException.class)
                .hasMessageContaining("canonical").hasMessageContaining(relative);
            assertThatThrownBy(() -> canvas.image(relative)).isInstanceOf(MisuseException.class);
        }
        assertThatCode(() -> canvas.canonical("HTTP://shop/x").image("HTTPS://shop/i.jpg"))
            .as("the scheme is not case-sensitive").doesNotThrowAnyException();
        assertThatThrownBy(() -> canvas.canonical(null)).isInstanceOf(MisuseException.class);
    }

    /** What a crawler may do: said as it was said, in the order it was said, without repetitions. */
    @Test
    void whatACrawlerMayDoIsSaidAsItWasSaid() {
        PageModel.of(model).title("t")
            .robots(PageModel.Robots.NOFOLLOW, PageModel.Robots.NOARCHIVE, PageModel.Robots.NOFOLLOW).render();
        assertThat(part(model, "head")).containsEntry("robots", "nofollow, noarchive");

        var twice = new ConcurrentModel();
        PageModel.of(twice).title("t").robots(PageModel.Robots.NOARCHIVE).robots(PageModel.Robots.NOFOLLOW).render();
        assertThat(part(twice, "head")).as("the last call is the whole line").containsEntry("robots", "nofollow");

        PageModel.Canvas canvas = PageModel.of(new ConcurrentModel()).title("t");
        assertThatThrownBy(canvas::robots)
            .isInstanceOf(MisuseException.class).hasMessageContaining("without a directive");
        assertThatThrownBy(() -> canvas.robots((PageModel.Robots[]) null))
            .isInstanceOf(MisuseException.class);
        assertThat(PageModel.Robots.MAX_IMAGE_PREVIEW_LARGE.directive()).isEqualTo("max-image-preview:large");
    }

    /**
     * And one thing is added without being asked: a page that declared a picture and is not kept out of
     * the index says the preview may be large. The consumer has already decided the picture is worth
     * showing, and a search engine's own default is a thumbnail — so silence there would be a decision
     * nobody made.
     */
    @Test
    void aPictureWorthShowingIsWorthShowingLarge() {
        PageModel.of(model).title("t").image("https://shop/img.jpg").render();
        assertThat(part(model, "head")).containsEntry("robots", "max-image-preview:large");

        var alongside = new ConcurrentModel();
        PageModel.of(alongside).title("t").image("https://shop/img.jpg")
            .robots(PageModel.Robots.NOFOLLOW).render();
        assertThat(part(alongside, "head")).containsEntry("robots", "nofollow, max-image-preview:large");

        var hidden = new ConcurrentModel();
        PageModel.of(hidden).title("t").image("https://shop/img.jpg")
            .robots(PageModel.Robots.NOINDEX).render();
        assertThat(part(hidden, "head")).as("not indexed: the size of a preview is moot")
            .containsEntry("robots", "noindex");
    }

    /** Classes of your own for the page element, beside the one marker the kit puts there itself. */
    @Test
    void classesOfYourOwnStandBesideTheKitsMarker() {
        PageModel.of(model).pageClass("  page-ingredient  ").title("t").render();
        assertThat(part(model, "page")).containsEntry("pageClass", "page-ingredient page-canvas");

        var replaced = new ConcurrentModel();
        PageModel.of(replaced).pageClass("first").pageClass("second").title("t").render();
        assertThat(part(replaced, "page")).as("the last call is the whole list")
            .containsEntry("pageClass", "second page-canvas");

        PageModel.Canvas canvas = PageModel.of(new ConcurrentModel()).title("t");
        assertThatThrownBy(() -> canvas.pageClass(" ")).isInstanceOf(MisuseException.class)
            .hasMessage("Canvas.pageClass(classes): is blank — a page shows what it was given, "
                + "and this is nothing");
        assertThatThrownBy(() -> canvas.pageClass(null)).isInstanceOf(MisuseException.class);
    }

    /** The canvas knows no list of bricks: whatever becomes an element goes on it, in the order added. */
    @Test
    void theCanvasKnowsNoListOfBricks() {
        PageModel.of(model).title("t")
            .add(Heading.h1("Title"))
            .add(Element.raw("fragments/my/card", "myCardEl").with("title", "one of yours"))
            .render();

        assertThat(flow(model)).extracting(e -> e.get("fragment")).containsExactly("headingEl", "myCardEl");
        assertThatThrownBy(() -> flow(model).add(Map.of()))
            .as("and what a document is handed cannot be changed by it")
            .isInstanceOf(UnsupportedOperationException.class);

        var empty = new ConcurrentModel();
        PageModel.of(empty).title("t").render();
        assertThat(flow(empty)).as("a page with nothing on it is a page").isEmpty();
    }

    /** A script is not part of the flow; it is declared by an element and collected from the tree. */
    @Test
    void scriptsAreCollectedRatherThanAdded() {
        var script = Element.script("fragments/my/card", "myCardJs");
        PageModel.of(model).title("t")
            .add(Element.raw("fragments/my/card", "myCardEl").with("title", "x").requires(script))
            .render();

        assertThat(model.asMap().get("assets")).isEqualTo(List.of(script.asMap()));

        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("t").add(Element.script("t", "js")))
            .isInstanceOf(MisuseException.class).hasMessageContaining("requires()");
        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("t").add(null))
            .isInstanceOf(MisuseException.class).hasMessage("PageModel.add(element): was not given");
    }

    /**
     * And the two things no single element can see are asked before the page is handed over: the
     * outline of the whole page, and its anchors. A page that fails either does not render.
     */
    @Test
    void thePageIsAskedTheTwoQuestionsNoElementCanAnswer() {
        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("t")
                .add(Heading.h1("The page")).add(Heading.h1("And another")).render())
            .isInstanceOf(UnsoundPageException.class).hasMessageContaining("more than one H1");

        assertThatThrownBy(() -> PageModel.of(new ConcurrentModel()).title("t")
                .add(Heading.h2("Composition").id("composition"))
                .add(Heading.h3("Also composition").id("composition")).render())
            .isInstanceOf(UnsoundPageException.class).hasMessageContaining("answer to the anchor");
    }
}
