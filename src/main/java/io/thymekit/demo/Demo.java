/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit.demo;

import io.thymekit.Caption;
import io.thymekit.Element;
import io.thymekit.Heading;
import io.thymekit.Hero;
import io.thymekit.Md;
import io.thymekit.PageModel;
import io.thymekit.Rel;
import io.thymekit.Section;
import java.time.LocalDate;
import java.util.List;
import org.springframework.ui.Model;

/**
 * The kit's showcase, shipped with the library. Mount it with one controller method and see the
 * elements live, rendered by your engine and served by your static handling; the library knows neither
 * the address you mounted it at nor your theme.
 *
 * <p>The showcase has a document of its own ({@code templates/thymekit/demo.html}, from the jar): no
 * consumer layout decorates it, so the page shows exactly what the kit produced. Its two stylesheets
 * are linked in the template itself — the kit ({@code /thymekit/ui.css}) and the showcase theme
 * ({@code /thymekit/demo.css}). The theme only hands values to element handles, which is what makes
 * the page a demonstration of theming rather than a page with a look of its own.
 *
 * <h2>Mounting</h2>
 * <pre>{@code
 * @GetMapping("/thymekit-demo")
 * String demo(Model model) {
 *     return Demo.page(model);
 * }
 * }</pre>
 */
public final class Demo {

    private static final String VIEW = "thymekit/demo";

    /**
     * How many elements the kit has, said on the page. A number the showcase states about itself is a
     * claim like any other, so the snapshot test counts the adapters in the jar and holds this to it.
     */
    private static final int ELEMENTS = 5;

    private Demo() {}

    /**
     * Address of the showcase's own furniture: frames, stand-ins for the consumer's elements, one
     * script. It lives one level down, in {@code thymekit/demo/}, because the kit's own adapters live
     * in {@code thymekit/} and every one of them owes the contract test a sample. Furniture owes
     * nothing: it stands in for what a consumer writes.
     */
    private static final String PARTS = "thymekit/demo/parts";

    /**
     * A frame in the stock scope, holding the same elements the page shows dressed. Marked as an
     * illustration: what is inside is a sample, not the outline of this page, so the H1 in it does not
     * count as a second one.
     */
    private static Element<Demo> stockSample() {
        return Element.Descriptor.<Demo>of(PARTS, "stockFrame").illustration()
            .slot("items", List.of(
                Caption.eyebrow("showcase"),
                Heading.h2("thymekit, undressed"),
                Caption.subtitle("the same elements, with nothing said about them")))
            .build();
    }

    /**
     * Everything a caption and a heading can carry, side by side: the four roles, a date a machine can
     * read, a phrase in another language, a heading that is a link and one that only a screen reader
     * meets. An illustration as well — the headings in it belong to the sample, not to the page.
     */
    private static Element<Demo> textSample() {
        return Element.Descriptor.<Demo>of(PARTS, "sampleFrame").illustration()
            .slot("items", List.of(
                Caption.eyebrow("eyebrow"),
                Heading.h3("A heading that is a link").href("https://github.com/thymekit/thymekit")
                    .rel(Rel.NOFOLLOW).newTab(),
                Heading.h4("Only a screen reader meets this one").srOnly(),
                Caption.subtitle("subtitle — the line under a title"),
                Caption.label("label"),
                Caption.meta("meta · 21 August 2026").time(LocalDate.of(2026, 8, 21)),
                Caption.subtitle("Adansonia digitata").lang("la"),
                Heading.h5("The fifth level, and the sixth below it"),
                Heading.h6("Six is where html stops, and so does the scale")))
            .build();
    }

    /** The kit's showcase, built the way a consumer builds a page. */
    public static String page(Model model) {
        return PageModel.of(model)
            .title("thymekit — element showcase")
            .description("Every element of the kit, rendered by the kit itself: the page you are "
                + "looking at is composed in Java and dressed by one stylesheet of handle values.")
            .pageClass("page-showcase")
            .add(Hero.of(Heading.h1("thymekit"))
                .eyebrow(Caption.eyebrow("showcase"))
                .subtitle(Caption.subtitle("the elements on this page are composed in Java and rendered by the kit itself"))
                .meta(Caption.meta(ELEMENTS + " elements, a canvas and a head"))
                .badge(Element.Descriptor.of(PARTS, "statusBadgeEl").with("text", "0.1.0"))
                .actions(Element.Descriptor.of(PARTS, "actionsEl")
                    .with("text", "the source, and every number on this page")
                    .with("href", "https://github.com/thymekit/thymekit")))
            .add(Section.of(Heading.h2("What this page is"))
                .add(Md.of("""
                    This page is composed **declaratively in Java** and rendered by the kit. Its look
                    comes from one stylesheet, `thymekit/demo.css`, which hands values to element
                    handles and touches nothing else — no override of the kit's markup, no rule the kit
                    knows about. Take that file away and the very same page renders as plain HTML.
                    """)))
            .add(Section.of(Heading.h2("Who dresses whom").id("who-dresses-whom"))
                .add(Md.of("""
                Three headings on this page wear three different faces, and no stylesheet rule mentions
                where any of them sits.

                ### The title of the page
                A serif display face, dressed by the hero — the element that hosts it. A page has exactly
                one H1 and the canvas keeps it that way, so its own face is a decision about the hero
                rather than about every heading on the site.

                ### The titles of the sections
                The heading above wears the site's own voice, set once in `:root`: small, tracked,
                upper-case, gold. Every section title on every page follows it.

                ### The headings of the text
                These two lines are written by an author inside markdown, not by the page. The markdown
                section hosts them, so it dresses them — quieter, in its own scale, through
                `--tk-md-heading-*`. Where the theme says nothing, the site's values come through
                untouched.
                    """)))
            .add(Section.of(Heading.h2("What text can carry"))
                .add(Md.of("""
                    A caption comes in four roles and carries what a machine needs beside what a person
                    reads: a date as `<time datetime="…">`, a phrase marked with the language it is in.
                    A heading may be a link — this one says `rel="nofollow"` and opens in a new tab,
                    which is why it also says `noopener`, whichever order the two were written in. A
                    heading may be there only for the outline: the frame below holds one no eye finds.
                    And the scale has six levels, the last two of them here.
                    """))
                .add(textSample()))
            .add(Section.of(Heading.h2("Whose text this is"))
                .add(Md.of("""
                    Text written by visitors says so. The link below carries `rel="ugc nofollow"`
                    because this block was told `linkRel(UGC, NOFOLLOW)`; a link of the site's own would
                    keep its weight, since only what leaves the site is marked: [a stranger's
                    page](https://example.com/somewhere) beside [a page of ours](/ingredients/baobab).
                    """).linkRel(Rel.UGC, Rel.NOFOLLOW)))
            .add(Section.of(Heading.h2("When there is nothing to show"))
                .add(Md.of(null)
                    .emptyHint("Nothing written here yet")
                    .addAction(Element.Descriptor.of(PARTS, "actionsEl")
                        .with("text", "write it")
                        .with("href", "https://github.com/thymekit/thymekit")
                        .requires(Element.script(PARTS, "demoJs")))))
            .add(Section.of(Heading.h2("What this page cannot show"))
                .add(Md.of("""
                    Three things the canvas can say are missing here on purpose. `canonical(...)` and
                    `image(...)` need an address this page cannot know — and this page is mounted inside
                    somebody else's application, where declaring *our* address would send *their*
                    crawler to *our* site; the copy published on the project's own pages gets its
                    canonical from the script that hands the addresses out. `robots(...)` is missing for
                    a simpler reason: every directive it can carry would be a false statement about a
                    page that wants to be read. A showcase that lied in its own markup would be a poor
                    argument for a kit whose whole claim is markup you can stand behind.
                    """))
            )
            .add(Section.of(Heading.h2("The theme, taken away"))
                .add(Md.of("""
                    Every element file resets its handles inside `.tk-defaults`, so a frame carrying
                    that class is a place the theme cannot reach. The same elements are below, in it:
                    this is the page with `demo.css` taken away, and the claim above stops being a
                    promise. The heading in the frame is an h2 and not the page's H1, because a page has
                    one of those and a sample of the stock is not it.
                    """))
                .add(stockSample()))
            .render(VIEW);
    }
}
