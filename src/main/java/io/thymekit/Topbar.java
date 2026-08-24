/* This Source Code Form is subject to the terms of the Mozilla Public
   License, v. 2.0. If a copy of the MPL was not distributed with this
   file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import org.jspecify.annotations.Nullable;

/**
 * The bar above a page: a way back, and where you are.
 *
 * <p>An element rather than something a consumer assembles, and the justification is what it
 * guarantees rather than what it saves. Assembled by hand, the way back lands inside the trail's
 * landmark — and then a navigation announced as "breadcrumb" contains a step that is in neither the
 * visible trail nor the graph. Here it cannot: the way back is printed before the trail is rendered,
 * the bar itself is a plain wrapper rather than a second landmark, and the mark beside the words is
 * marked as decoration so it is not read out.
 *
 * <p>The way back is keys rather than a slot. It has one host and one shape; widening keys into a slot
 * later takes nothing away from anybody, while narrowing a slot into keys would.
 *
 * <p>The bar says nothing about itself for machines and does not stand between the trail and the page:
 * what the trail contributes is collected through the bar exactly as it would be without it.
 */
public final class Topbar {

    private static final String TEMPLATE = "thymekit/topbar";

    private Topbar() {}

    /** The bar around a trail. The trail is what the bar is for, so there is no bar without one. */
    public static Builder of(Composable<Breadcrumbs> crumbs) {
        return new Builder(Element.settle(crumbs, "Topbar.of(crumbs)"));
    }

    /**
     * Where to go back to, and what to call it — one value rather than two fields. They are written
     * together or not at all, and holding them apart made that a thing to remember: a branch asking
     * whether the second arrived, which nothing could reach, and then an assertion in its place.
     */
    private record Back(String href, String label) {}

    /** The trail, and optionally the way back. */
    public static final class Builder implements Composable<Topbar> {

        private final Element<Breadcrumbs> crumbs;
        private @Nullable Back back;

        private Builder(Element<Breadcrumbs> crumbs) {
            this.crumbs = crumbs;
        }

        /**
         * Where to go back to, and what to call it. The address is often taken from where the visitor
         * came from, which makes it the least trusted string on the page — so it goes through the guard
         * that refuses a scheme which executes instead of navigating.
         *
         * <p>The words matter as much: "Back" says nothing about where, and a name of the place —
         * "All ingredients" — is what somebody hears who cannot see the trail beside it.
         */
        public Builder back(String href, String label) {
            this.back = new Back(Guards.navigable(href, "Topbar.back(href)"),
                Guards.text(label, "Topbar.back(label)"));
            return this;
        }

        @Override
        public Element<Topbar> build() {
            Element.Descriptor<Topbar> bar = Element.Descriptor.<Topbar>of(TEMPLATE, "topbarEl")
                .with("crumbs", crumbs.asMap());
            if (back != null) {
                bar.with("backHref", back.href()).with("backLabel", back.label());
            }
            return bar.build();
        }
    }
}
