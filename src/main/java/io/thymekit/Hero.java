/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The page hero: a heading group ({@code <hgroup>} with eyebrow, H1, subtitle and meta lines), then a
 * divider, an optional status badge and an optional action row. The order is fixed by the element; a
 * page only supplies the parts it has. The core is the H1 alone, everything else is a guarded narrow
 * point. The hero knows no domain words — those belong to whoever composes it.
 */
public final class Hero {

    private Hero() {}

    /**
     * The hero of a page; the heading must be the H1. The level is read by the element that owns
     * headings rather than off the descriptor here — a heading minted by hand may write its level as
     * text, and a guard that understood only a number would let a second title onto the page.
     */
    public static Builder of(Composable<Heading> h1) {
        Element<Heading> heading = Element.settle(h1, "Hero.of(h1)");
        Element.requireAdapter(heading, "headingEl", "Hero.of(h1)");
        Integer level = Roles.headingLevelIn(heading.asMap());
        if (level == null || level != 1) {
            throw new MisuseException("Hero.of(h1)", "accepts an H1 only, and got level " + level);
        }
        return new Builder(heading);
    }

    public static final class Builder implements Composable<Hero> {

        private final Element.Descriptor<Hero> b;
        private final List<Map<String, Object>> metas = new ArrayList<>();

        private Builder(Element<Heading> h1) {
            this.b = Element.Descriptor.<Hero>of("thymekit/hero", "heroEl")
                .with("heading", h1.asMap());
        }

        /** Caption in the eyebrow role, above the H1. */
        public Builder eyebrow(Composable<Caption> eyebrow) {
            b.with("eyebrow", Caption.inRole(eyebrow, Caption.EYEBROW, "Hero.eyebrow(eyebrow)").asMap());
            return this;
        }

        /** Caption in the subtitle role, below the H1. */
        public Builder subtitle(Composable<Caption> subtitle) {
            b.with("subtitle", Caption.inRole(subtitle, Caption.SUBTITLE, "Hero.subtitle(subtitle)").asMap());
            return this;
        }

        /**
         * Meta lines of the heading group, in call order — a slug, a counter, a date. Called with
         * nothing to say it is refused: a line that meant something and lost it is worth an exception
         * rather than a page quietly missing it.
         */
        @SafeVarargs
        public final Builder meta(Composable<Caption>... metaLines) {
            Guards.required(metaLines, "Hero.meta(metaLines)");
            if (metaLines.length == 0) {
                throw new MisuseException("Hero.meta(metaLines)", "no caption was named — name at least one, "
                    + "or do not call meta(...) at all");
            }
            for (Composable<Caption> line : metaLines) {
                metas.add(Caption.inRole(line, Caption.META, "Hero.meta(line)").asMap());
            }
            return this;
        }

        /**
         * A status line under the hero — "in stock", "draft", "archived". The element is the consumer's,
         * and so is the name its adapter carries: the guard asks for {@code statusBadgeEl}, which no
         * fragment of the kit defines. What the kit fixes is the place and the shape of the slot, not
         * what goes in it.
         */
        public Builder badge(Composable<?> badge) {
            Element<?> settled = Element.settle(badge, "Hero.badge(badge)");
            Element.requireAdapter(settled, "statusBadgeEl", "Hero.badge(badge)");
            b.with("badge", settled.asMap());
            return this;
        }

        /**
         * The row of actions under the hero. Same arrangement as {@link #badge}: the adapter is named
         * {@code actionsEl} and lives in consumer code.
         */
        public Builder actions(Composable<?> actions) {
            Element<?> settled = Element.settle(actions, "Hero.actions(actions)");
            Element.requireAdapter(settled, "actionsEl", "Hero.actions(actions)");
            b.with("actions", settled.asMap());
            return this;
        }

        @Override
        public Element<Hero> build() {
            if (!metas.isEmpty()) {
                b.with("metas", List.copyOf(metas));
            }
            return b.build();
        }
    }
}
