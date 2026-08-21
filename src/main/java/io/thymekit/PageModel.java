/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.ui.Model;

/**
 * The canvas of an arbitrary page: document properties (a title, what search engines and messengers
 * are told about the page, and classes of your own if your theme needs them) plus an ordered
 * collection of elements — the order of {@code add} is the order of rendering.
 *
 * <p>The canvas knows no list of bricks: an element is whatever a factory produced, so adding one to
 * the kit never touches this API.
 *
 * <p>The builder only accumulates; the model is filled by {@code render()} under four keys, and two of
 * them are elements: {@code page} — the canvas itself, with its classes and its flow inside — and
 * {@code head}, what the page says about itself. The other two are {@code assets}, the scripts of the
 * tree, and {@code pageTitle}, the bare string for a document that wants to show the title somewhere
 * of its own; the tag is printed by the head element alone, so the two cannot disagree.
 *
 * <p>That the page is an element is not a turn of phrase. It carries an adapter address like any other,
 * the same dispatcher renders it, and a document says {@code render(${page})} exactly as it would for a
 * heading. Composition is closed at the top as well as at the bottom.
 *
 * <p>The canvas renders itself: the element under {@code page} draws the {@code <main>} landmark with
 * the flow inside it. A page owes a reader and a crawler that landmark, so the kit puts it down rather
 * than trusting every document to remember.
 *
 * <p>The head of the document is filled the same way: {@code head} is an element like any other, so a
 * document renders it through the dispatcher and gets the title, the description, the canonical link
 * and the Open Graph tags from one source — the builder below. Say a thing once and every place that
 * repeats it stays in step.
 *
 * <p>Guards: a title is required (an unnamed browser tab is always a mistake), an empty collection is
 * legal (a placeholder page knows what it is doing).
 */
public final class PageModel {

    private PageModel() {}

    /** Default document: the view name returned by the no-argument {@code render()}. */
    private static final String DEFAULT_VIEW = "page";

    /** Marker of a canvas-built page; the only class the kit puts there by itself. */
    private static final String CANVAS = "page-canvas";

    /** A page canvas. */
    public static Canvas of(Model model) {
        return new Builder(Objects.requireNonNull(model, "model"));
    }

    public interface Canvas {

        /*
         * Every text below is refused when it is blank and kept trimmed when it is not: a space at the
         * end of a title is never meant, and it travels into the tab, the search result and the preview.
         */

        /** Document title; required before {@code render}. Also the Open Graph title. */
        Canvas title(String title);

        /**
         * What this page is, in a sentence — the description a search engine may show under the link
         * and a messenger shows in a preview. One source for both.
         */
        Canvas description(String description);

        /**
         * The address this page is to be found at, when more than one leads to it (a filter, a sort,
         * a session parameter, a copy under another path). Becomes the canonical link and the Open
         * Graph url. Only the consumer knows where the page lives, so the kit never guesses it.
         *
         * <p>Absolute, with a scheme: {@code https://shop/ingredients/baobab}. A relative canonical
         * link a browser would resolve, but the same value goes out as {@code og:url}, and a messenger
         * scraping the page has no document to resolve it against.
         */
        Canvas canonical(String url);

        /**
         * The picture a messenger shows in the preview of this page. Absolute, with a scheme — a
         * relative {@code og:image} is not a smaller picture, it is no preview at all.
         */
        Canvas image(String url);

        /**
         * What a crawler may do with this page. Say nothing and nothing is printed, which is the same
         * as full permission; say {@code robots(NOINDEX)} for a draft, a filtered listing, a page
         * meant for one visitor.
         *
         * <p>One thing is added without being asked: a page that was given an {@link #image(String)}
         * and is not kept out of the index also says {@code max-image-preview:large}. The consumer has
         * already declared a picture worth showing, and the default of a search engine is a thumbnail —
         * so silence there would be a decision nobody made. Nothing else is ever added, and whatever is
         * written here is printed exactly as written.
         */
        Canvas robots(Robots... directives);

        /**
         * Classes of your own for the page element — an audience, a section of the site, whatever your
         * theme hooks onto. The kit adds only its own {@code page-canvas} marker and has no opinion
         * about what kinds of pages you have.
         */
        Canvas pageClass(String classes);

        /** Appends an element to the flow. */
        Canvas add(Element<?> element);

        /** Terminal: fills the model and returns the default view name. */
        String render();

        /**
         * Terminal with a document of your own: the view name is yours (own layout, print version,
         * the kit's showcase). All such a document has to do is include the canvas fragment and the
         * scripts of the element tree.
         */
        String render(String view);
    }

    /**
     * What a crawler may do with a page. Four directives, because these are the four that change what a
     * visitor sees in a search result; the rest of the vocabulary is added when something actually needs
     * it, not in advance.
     */
    public enum Robots {

        /** Keep the page out of the index. Links on it are still followed. */
        NOINDEX("noindex"),

        /** Do not follow the links of this page. */
        NOFOLLOW("nofollow"),

        /** Do not keep a cached copy. */
        NOARCHIVE("noarchive"),

        /** Allow a large image preview instead of the thumbnail a search engine shows by default. */
        MAX_IMAGE_PREVIEW_LARGE("max-image-preview:large");

        private final String directive;

        Robots(String directive) {
            this.directive = directive;
        }

        /** The directive as it is written in the tag. */
        public String directive() {
            return directive;
        }
    }

    /** Addresses of the two elements a page is made of; the canvas builds them, the document renders them. */
    private static final String HEAD = "fragments/thymekit/head";
    private static final String CANVAS_TEMPLATE = "fragments/thymekit/canvas";

    private static final class Builder implements Canvas {

        private final Model model;
        private String pageClass = CANVAS;
        private @Nullable String title;
        private @Nullable String description;
        private @Nullable String canonical;
        private @Nullable String image;
        private @Nullable Set<Robots> robots;
        private final List<Element<?>> elements = new ArrayList<>();

        private Builder(Model model) {
            this.model = model;
        }

        @Override
        public Canvas pageClass(String classes) {
            this.pageClass = require(classes, "classes") + " " + CANVAS;
            return this;
        }

        @Override
        public Canvas title(String title) {
            this.title = require(title, "title");
            return this;
        }

        @Override
        public Canvas description(String description) {
            this.description = require(description, "description");
            return this;
        }

        @Override
        public Canvas canonical(String url) {
            this.canonical = requireAbsolute(url, "canonical");
            return this;
        }

        @Override
        public Canvas image(String url) {
            this.image = requireAbsolute(url, "image");
            return this;
        }

        @Override
        public Canvas robots(Robots... directives) {
            Objects.requireNonNull(directives, "directives");
            if (directives.length == 0) {
                throw new IllegalArgumentException("robots without a directive: say what a crawler may not do, "
                    + "or do not call robots at all");
            }
            this.robots = new LinkedHashSet<>(List.of(directives));
            return this;
        }

        /**
         * A blank value is a mistake that shows up as an empty tag, so it is refused here; what is kept
         * is trimmed, because a space at the end of a title is never meant and travels into the tab, the
         * search result and the link preview alike.
         */
        private static String require(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " is blank");
            }
            return value.strip();
        }

        /**
         * An address that leaves the page — into a canonical link, into Open Graph — is absolute or it
         * is broken. Whoever reads it is not looking at the document and has nothing to resolve a path
         * against, and the failure is silent: no preview, or a canonical pointing at a stranger.
         */
        private static String requireAbsolute(String url, String name) {
            String value = require(url, name);
            if (!value.regionMatches(true, 0, "https://", 0, 8) && !value.regionMatches(true, 0, "http://", 0, 7)) {
                throw new IllegalArgumentException(name + " is not an absolute address: \"" + value
                    + "\" — this value leaves the page, and whoever reads it has no document to resolve it against");
            }
            return value;
        }

        @Override
        public Canvas add(Element<?> element) {
            Element.requireRenderable(element, "PageModel.add");
            elements.add(element);
            return this;
        }

        /**
         * The robots line: what was said, plus the size of the image preview when a picture was given
         * and the page is not kept out of the index. Nothing at all when there is nothing to say.
         */
        private @Nullable String crawler() {
            Set<Robots> directives = new LinkedHashSet<>(robots == null ? Set.of() : robots);
            if (image != null && !directives.contains(Robots.NOINDEX)) {
                directives.add(Robots.MAX_IMAGE_PREVIEW_LARGE);
            }
            return directives.isEmpty() ? null
                : directives.stream().map(Robots::directive).collect(Collectors.joining(", "));
        }

        /** The page as an element: its classes and its flow, rendered by the same dispatcher as everything else. */
        private Map<String, Object> page() {
            return Element.Descriptor.<PageModel>of(CANVAS_TEMPLATE, "canvasEl")
                .with("pageClass", pageClass)
                .with("elements", elements.stream().map(Element::asMap).toList())
                .build().asMap();
        }

        /** The head as an element: only what was said is put in, so the adapter prints no empty tags. */
        private Map<String, Object> head() {
            Element.Descriptor<PageModel> head = Element.Descriptor.<PageModel>of(HEAD, "headEl").with("title", title);
            if (description != null) {
                head.with("description", description);
            }
            if (canonical != null) {
                head.with("canonical", canonical);
            }
            if (image != null) {
                head.with("image", image);
            }
            String crawler = crawler();
            if (crawler != null) {
                head.with("robots", crawler);
            }
            return head.build().asMap();
        }

        @Override
        public String render() {
            return render(DEFAULT_VIEW);
        }

        @Override
        public String render(String view) {
            Objects.requireNonNull(view, "view");
            if (title == null) {
                throw new IllegalStateException("page without a title: call title(...) before render()");
            }
            Element.assertOutline(elements);
            model.addAttribute("pageTitle", title);
            model.addAttribute("head", head());
            model.addAttribute("page", page());
            model.addAttribute("assets", Element.assetsOf(elements).stream().map(Element::asMap).toList());
            return view;
        }
    }
}
