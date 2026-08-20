/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit.demo;

import io.thymekit.Caption;
import io.thymekit.Heading;
import io.thymekit.Hero;
import io.thymekit.Md;
import io.thymekit.PageModel;
import org.springframework.ui.Model;

/**
 * The kit's showcase, shipped with the library. Mount it with one controller method and see the
 * elements live, rendered by your engine and served by your static handling; the library knows neither
 * the address you mounted it at nor your theme.
 *
 * <p>The showcase has a document of its own ({@code templates/thymekit/demo.html}, from the jar): no
 * consumer layout decorates it, so the page shows exactly what the kit produced. Its two stylesheets
 * are linked in the template itself — the kit ({@code /thymekit/ui.css}) and the showcase theme
 * ({@code /thymekit/demo.css}, deliberately empty).
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

    private Demo() {}

    /** Builds the showcase and returns the name of its view. */
    public static String page(Model model) {
        return PageModel.of(model)
            .title("thymekit — element showcase")
            .add(Hero.of(Heading.h1("thymekit").build())
                .eyebrow(Caption.eyebrow("showcase").build())
                .subtitle(Caption.subtitle("living documentation: every element exactly as the kit renders it").build())
                .build())
            .add(Md.of("""
                This page is composed **declaratively in Java** and rendered by the kit. The document is
                the library's own, so no application theme takes part: what you see below is stock —
                plain HTML with correct markup and element handles at their default values.
                """).build())
            .render(VIEW);
    }
}
