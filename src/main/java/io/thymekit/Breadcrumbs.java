/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The trail of a page: where it sits in the site, said twice — once for a person and once for a
 * crawler — out of <b>one</b> list of steps.
 *
 * <p>That the two halves come from one list is the point rather than a convenience. The rules a search
 * engine publishes require markup to be a true representation of what a visitor sees, and a trail
 * assembled twice is a trail that drifts: two sources, two chances to be edited, one of them silently.
 * Here there is nothing to drift.
 *
 * <p>The name of the landmark is yours, because a screen reader says it out loud and it has to be in
 * the language of your visitors. The kit will not invent it.
 *
 * <p>The visible links are written exactly as they were given, relative and all: absolute links in the
 * page break the day the site moves to another host, to https, or onto a staging machine. Absolute
 * addresses are wanted in the other half, where a crawler reads them, and {@link Builder#site} is where
 * the origin is named — once, by whoever knows where the site lives.
 *
 * <pre>{@code
 * Breadcrumbs.named("Breadcrumb")
 *     .site("https://shop")
 *     .add("/ingredients", "Ingredients")
 *     .current("Aloe")
 * }</pre>
 *
 * <p>{@code current(...)} both adds the last step and finishes, so nothing can follow the page you are
 * on. A trail of ancestors alone is a trail too, and ends with {@code build()}.
 */
public final class Breadcrumbs {

    private static final String TEMPLATE = "thymekit/breadcrumbs";

    private Breadcrumbs() {}

    /**
     * The trail, named as a screen reader will announce it.
     *
     * <p>Not {@code of(...)}: in this kit that name takes the thing an element is made of, and takes
     * it as something that may be absent. What breadcrumbs are made of is steps, and their name is
     * never absent.
     */
    public static Builder named(String label) {
        return new Builder(label);
    }

    /** Steps in the order they are walked, from the top of the site towards this page. */
    public static final class Builder implements Composable<Breadcrumbs> {

        private final String label;
        private final List<Crumb> steps = new ArrayList<>();
        private @Nullable String origin;

        private Builder(String label) {
            this.label = Element.requireText(label, "label");
        }

        /**
         * Where the site lives, used for the machine-readable half only. Given one, a step written as a
         * path becomes an absolute address in the graph; the link on the page is untouched.
         *
         * <p>Only a step that starts at the root is joined to it. One that is already absolute is left
         * alone, and one written relative to the current directory is left alone too — resolving that
         * would need the address of the page, which is the one thing this element does not have.
         */
        public Builder site(String origin) {
            String value = Element.requireAbsolute(origin, "origin");
            // != -1 rather than >= 0: the search starts past the scheme, so a zero is not a boundary
            // this code can reach, and asking about one invites a question that has no answer
            if (value.indexOf('/', value.indexOf("//") + 2) != -1) {
                throw new IllegalArgumentException("origin carries more than a site: \"" + value
                    + "\" — scheme and host only, with no path and no trailing slash. A step written from"
                    + " the root is joined to this as it is, so a path here would end up inside it twice"
                    + " and a slash would double");
            }
            this.origin = value;
            return this;
        }

        /** A step above this page. */
        public Builder add(String url, String label) {
            return step(Crumb.link(url, label));
        }

        /** The page you are on: the last step, and the end of the trail. */
        public Element<Breadcrumbs> current(String label) {
            return step(Crumb.current(label)).build();
        }

        private Builder step(Crumb crumb) {
            if (!steps.isEmpty() && steps.get(steps.size() - 1).url() == null) {
                throw new IllegalStateException("the trail already ends at the page you are on");
            }
            steps.add(crumb);
            return this;
        }

        @Override
        public Element<Breadcrumbs> build() {
            if (steps.isEmpty()) {
                throw new IllegalStateException("a trail with no steps is empty, and would print a landmark "
                    + "with nothing in it");
            }
            return Element.Descriptor.<Breadcrumbs>of(TEMPLATE, "breadcrumbsEl")
                .with("label", label)
                .with("items", steps.stream().map(Builder::visible).toList())
                .describes(trail())
                .build();
        }

        /** What the page shows: the address as written, and the name. */
        private static Map<String, Object> visible(Crumb crumb) {
            Map<String, Object> step = new LinkedHashMap<>();
            if (crumb.url() != null) {
                step.put("url", crumb.url());
            }
            step.put("label", crumb.label());
            return step;
        }

        /**
         * What a crawler reads. Positions count from one, as the schema says, and a step carries an
         * address only if it goes somewhere: the address of the page you are on is the address of the
         * page being read, which is the one a crawler can be sure of. No vocabulary is named here — the
         * canvas names it once for the whole page.
         */
        private Map<String, Object> trail() {
            List<Map<String, Object>> listed = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                Crumb crumb = steps.get(i);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("@type", "ListItem");
                item.put("position", i + 1);
                item.put("name", crumb.label());
                if (crumb.url() != null) {
                    item.put("item", forACrawler(crumb.url()));
                }
                listed.add(item);
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("@type", "BreadcrumbList");
            node.put("itemListElement", listed);
            return node;
        }

        /**
         * Only a step written from the root of the site is joined to the origin. One that already names
         * a host — with a scheme or without, since {@code //host/path} names one too — is left as it
         * is, and so is one written relative to the current directory, because resolving that needs the
         * address of the page and this element does not have it.
         */
        private String forACrawler(String url) {
            boolean fromTheRoot = url.startsWith("/") && !url.startsWith("//");
            return origin != null && fromTheRoot ? origin + url : url;
        }
    }
}
