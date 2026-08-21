/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Owner of the section concept: a part of a page with a heading and whatever belongs under it. Anything
 * that needs a titled area composes this instead of writing its own {@code <section>} — a markdown
 * block, a row of cards, a table of yours, several of them together.
 *
 * <pre>{@code
 * Section.of(Heading.h2("Composition").id("composition"))
 *     .add(Md.of(it.description()).emptyHint("Nothing written yet"))
 *     .add(myCardRow)
 * }</pre>
 *
 * <p>The section takes its accessible name from the heading, when the heading was given an id: deciding
 * that a section deserves an address is deciding that it stands on its own, and what stands on its own
 * deserves a name. Where nobody decided that, it stays an ordinary box rather than one more landmark to
 * step over.
 *
 * <p>Whether there is anything worth showing is the consumer's decision — the kit does not know the
 * data. A section renders its heading and its contents; an element inside that has nothing to render
 * renders nothing, and the heading stays.
 */
public final class Section {

    private Section() {}

    /** A section under a heading of the level the outline calls for. */
    public static Builder of(Composable<Heading> heading) {
        Element<Heading> settled = Element.settle(heading, "heading");
        Element.requireAdapter(settled, "headingEl", "Section.of accepts a heading only");
        return new Builder(settled);
    }

    public static final class Builder implements Composable<Section> {

        private final Element.Descriptor<Section> b;
        private final List<Element<?>> items = new ArrayList<>();

        private Builder(Element<Heading> heading) {
            this.b = Element.Descriptor.<Section>of("thymekit/section", "sectionEl")
                .with("heading", heading.asMap());
        }

        /** Appends to the flow of the section, in call order. */
        public Builder add(Composable<?> element) {
            items.add(Element.requireRenderableElement(Element.settle(element, "element"), "Section.add"));
            return this;
        }

        @Override
        public Element<Section> build() {
            return b.slot("items", items).build();
        }
    }
}
