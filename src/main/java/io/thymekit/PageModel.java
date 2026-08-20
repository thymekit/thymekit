/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.ui.Model;

/**
 * The canvas of an arbitrary page: document properties (a title, and classes of your own if your theme
 * needs them) plus an ordered collection of elements — the order of {@code add} is the order of
 * rendering.
 *
 * <p>The canvas knows no list of bricks: an element is whatever a factory produced, so adding one to
 * the kit never touches this API.
 *
 * <p>The builder only accumulates; the model is filled by {@code render()} under the keys the consumer
 * document reads — {@code pageTitle}, {@code pageClass}, {@code elements}, {@code assets}.
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

        /** Document title; required before {@code render}. */
        Canvas title(String title);

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
         * the kit's showcase). All such a document has to do is render {@code elements} and
         * {@code assets} through the dispatcher.
         */
        String render(String view);
    }

    private static final class Builder implements Canvas {

        private final Model model;
        private String pageClass = CANVAS;
        private @Nullable String title;
        private final List<Element<?>> elements = new ArrayList<>();

        private Builder(Model model) {
            this.model = model;
        }

        @Override
        public Canvas pageClass(String classes) {
            this.pageClass = Objects.requireNonNull(classes, "classes") + " " + CANVAS;
            return this;
        }

        @Override
        public Canvas title(String title) {
            this.title = Objects.requireNonNull(title, "title");
            return this;
        }

        @Override
        public Canvas add(Element<?> element) {
            Element.requireRenderable(element, "PageModel.add");
            elements.add(element);
            return this;
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
            Element.assertSingleH1(elements);
            model.addAttribute("pageTitle", title);
            model.addAttribute("pageClass", pageClass);
            model.addAttribute("elements", elements.stream().map(Element::asMap).toList());
            model.addAttribute("assets", Element.assetsOf(elements).stream().map(Element::asMap).toList());
            return view;
        }
    }
}
