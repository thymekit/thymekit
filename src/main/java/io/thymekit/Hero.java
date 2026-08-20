/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The page hero: a heading group ({@code <hgroup>} with eyebrow, H1, subtitle and meta lines), then a
 * divider, an optional status badge and an optional action row. The order is fixed by the element; a
 * page only supplies the parts it has. The core is the H1 alone, everything else is a guarded narrow
 * point. The hero knows no domain words — those belong to whoever composes it.
 */
public final class Hero {

    private Hero() {}

    /** The hero of a page; the heading must be level 1. */
    public static Builder of(Element<Heading> h1) {
        Element.requireAdapter(Objects.requireNonNull(h1, "heading"), "headingEl", "Hero.of accepts a heading only");
        if (!Integer.valueOf(1).equals(h1.asMap().get("level"))) {
            throw new IllegalArgumentException("Hero.of accepts an H1 only (got level " + h1.asMap().get("level") + ")");
        }
        return new Builder(h1);
    }

    public static final class Builder {

        private final Element.Descriptor<Hero> b;
        private final List<Map<String, Object>> metas = new ArrayList<>();

        private Builder(Element<Heading> h1) {
            this.b = Element.Descriptor.<Hero>of("fragments/thymekit/hero", "heroEl")
                .with("heading", h1.asMap());
        }

        /** Caption in the eyebrow role, above the H1. */
        public Builder eyebrow(Element<Caption> eyebrow) {
            Caption.requireRole(eyebrow, Caption.EYEBROW, "Hero.eyebrow accepts a caption");
            b.with("eyebrow", eyebrow.asMap());
            return this;
        }

        /** Caption in the subtitle role, below the H1. */
        public Builder subtitle(Element<Caption> subtitle) {
            Caption.requireRole(subtitle, Caption.SUBTITLE, "Hero.subtitle accepts a caption");
            b.with("subtitle", subtitle.asMap());
            return this;
        }

        /** Meta lines of the heading group, in call order. */
        @SafeVarargs
        public final Builder meta(Element<Caption>... metaLines) {
            for (Element<Caption> m : Objects.requireNonNull(metaLines, "meta")) {
                Caption.requireRole(m, Caption.META, "Hero.meta accepts a caption");
                metas.add(m.asMap());
            }
            return this;
        }

        /** Status line below the divider. Guarded by adapter address while the badge element lives outside the core. */
        public Builder badge(Element<?> badge) {
            Element.requireAdapter(Objects.requireNonNull(badge, "badge"), "statusBadgeEl", "Hero.badge accepts a status badge only");
            b.with("badge", badge.asMap());
            return this;
        }

        /** Action row of the hero; guarded by adapter address, see {@link #badge}. */
        public Builder actions(Element<?> actions) {
            Element.requireAdapter(Objects.requireNonNull(actions, "actions"), "actionsEl", "Hero.actions accepts an action row");
            b.with("actions", actions.asMap());
            return this;
        }

        public Element<Hero> build() {
            if (!metas.isEmpty()) {
                b.with("metas", List.copyOf(metas));
            }
            return b.build();
        }
    }
}
