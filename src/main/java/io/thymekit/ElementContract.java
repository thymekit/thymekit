/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */
package io.thymekit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import io.thymekit.Element.Script;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.thymeleaf.ITemplateEngine;
import org.thymeleaf.context.Context;

/**
 * The walk over a triple, handed to whoever writes an element. The kit checks its own elements with
 * it; an element of yours is checked the same way, in your own test:
 *
 * <pre>{@code
 * @Test
 * void myElementsKeepTheContract() {
 *     ElementContract.of(Price.of("12.00", "EUR"), Badge.of("in stock"))
 *         .renderedBy(templateEngine)
 *         .styledBy("static/my/ui.css")
 *         .check();
 * }
 * }</pre>
 *
 * <p>What it looks at is what no compiler can: that the address points at a fragment that exists and
 * declares itself, that the adapter is named the way an adapter is named, that a script has not been
 * put where an element belongs, and — when an engine and stylesheets are given — that the element
 * renders something, and that every class it prints has a rule somewhere in the CSS you name.
 *
 * <p>Nothing here is required to write an element. It is the same walk the kit takes over its own, and
 * it fails with every problem at once rather than with the first.
 */
public final class ElementContract {

    /** An adapter is named for what it renders and ends in El; a second contract gets a version suffix. */
    private static final Pattern ADAPTER = Pattern.compile("^[a-z][A-Za-z0-9]*El(V\\d+)?$");

    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile("class=\"([^\"]+)\"");

    private final List<Element<?>> elements;
    private final @Nullable ITemplateEngine engine;
    private final List<String> stylesheets;
    private final List<String> templateRoots;

    private ElementContract(List<Element<?>> elements, @Nullable ITemplateEngine engine,
                            List<String> stylesheets, List<String> templateRoots) {
        this.elements = elements;
        this.engine = engine;
        this.stylesheets = stylesheets;
        this.templateRoots = templateRoots;
    }

    /** The elements to walk: one live sample of each, as a page would build them. */
    public static ElementContract of(Composable<?>... elements) {
        Objects.requireNonNull(elements, "elements");
        List<Element<?>> settled = new ArrayList<>();
        for (Composable<?> element : elements) {
            settled.add(Element.settle(element, "element"));
        }
        if (settled.isEmpty()) {
            throw new IllegalArgumentException("no elements to check: give the contract at least one");
        }
        return new ElementContract(settled, null, List.of(), List.of("templates/", ""));
    }

    /** Renders each element through the dispatcher, with the engine your application uses. */
    public ElementContract renderedBy(ITemplateEngine engine) {
        return new ElementContract(elements, Objects.requireNonNull(engine, "engine"), stylesheets, templateRoots);
    }

    /**
     * Where your templates live on the classpath, when they are not under {@code templates/} — the
     * prefix your template resolver was given, {@code "views/"} or whatever it is. Without this the
     * contract looks under {@code templates/} and then at the address as written, which covers the
     * default and nothing else.
     */
    public ElementContract templatesUnder(String... classpathPrefixes) {
        List<String> roots = new ArrayList<>(List.of(Objects.requireNonNull(classpathPrefixes, "classpathPrefixes")));
        roots.addAll(templateRoots);
        return new ElementContract(elements, engine, stylesheets, List.copyOf(roots));
    }

    /**
     * Where the looks live, as classpath resources ({@code "static/my/ui.css"}). Given these, every
     * class an element prints must have a rule in one of them; without them the classes are not looked
     * at, since the contract has no way to guess where a consumer keeps its CSS.
     */
    public ElementContract styledBy(String... cssResources) {
        List<String> all = new ArrayList<>(stylesheets);            // said twice means both, never the last one only
        all.addAll(List.of(Objects.requireNonNull(cssResources, "cssResources")));
        return new ElementContract(elements, engine, List.copyOf(all), templateRoots);
    }

    /** Runs the walk. Throws with everything that is wrong, not with the first thing. */
    public void check() {
        List<String> failures = new ArrayList<>();
        Set<String> printedClasses = new LinkedHashSet<>();
        for (Element<?> element : elements) {
            String address = element.template() + " :: " + element.fragment();
            if (!ADAPTER.matcher(element.fragment()).matches()) {
                failures.add(address + " — an adapter is named like myCardEl, or myCardElV2 for a second contract");
            }
            if (element.bare()) {
                failures.add(address + " — a script element does not belong among elements; declare it with requires()");
            }
            declaresItself(element, address, failures);
            for (Element<Script> asset : element.assets()) {   // requires() already refuses anything but a script
                declaresItself(asset, address + " — its script " + asset.template() + " :: " + asset.fragment(), failures);
            }
            if (engine != null) {
                printedClasses.addAll(renderAndCollect(element, address, failures));
            }
        }
        if (!stylesheets.isEmpty()) {
            checkClasses(printedClasses, failures);
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("the element contract is broken in " + failures.size()
                + " place(s):\n  - " + String.join("\n  - ", failures));
        }
    }

    /** The address points at a template that exists, and the template declares the fragment it names. */
    private void declaresItself(Element<?> element, String address, List<String> failures) {
        String template = read(element.template() + ".html");
        if (template == null) {
            failures.add(address + " — no template on the classpath at " + element.template() + ".html"
                + " (looked under " + String.join(", ", templateRoots.stream().map(r -> r.isEmpty() ? "the address itself" : r).toList()) + ")");
        } else if (!template.contains("th:fragment=\"" + element.fragment() + "(")) {
            failures.add(address + " — the template declares no fragment " + element.fragment()
                + "(e); the dispatcher calls an adapter with the descriptor, so it takes one argument");
        }
    }

    /** Renders one element through the single dispatcher, exactly as a page would. */
    private Set<String> renderAndCollect(Element<?> element, String address, List<String> failures) {
        Context context = new Context();
        context.setVariable("e", element.asMap());
        String html;
        try {
            html = Objects.requireNonNull(engine).process("fragments/thymekit/element", Set.of("render"), context);
        } catch (RuntimeException notRendered) {
            failures.add(address + " — does not render: " + notRendered.getMessage());
            return Set.of();
        }
        if (html.isBlank() || !html.contains("<")) {
            failures.add(address + " — renders nothing a browser would show");
            return Set.of();
        }
        Set<String> classes = new LinkedHashSet<>();
        Matcher m = CLASS_ATTRIBUTE.matcher(html);
        while (m.find()) {
            classes.addAll(List.of(m.group(1).trim().split("\\s+")));
        }
        return classes;
    }

    /** Every class an element printed has a rule; comments are stripped, since a name in a header styles nothing. */
    private void checkClasses(Set<String> printed, List<String> failures) {
        StringBuilder css = new StringBuilder();
        for (String resource : stylesheets) {
            String text = read(resource);
            if (text == null) {
                failures.add("no stylesheet on the classpath at " + resource);
            } else {
                css.append(text.replaceAll("(?s)/\\*.*?\\*/", " "));
            }
        }
        for (String className : printed) {
            if (!Pattern.compile("\\." + Pattern.quote(className) + "(?![\\w-])").matcher(css).find()) {
                failures.add("class " + className + " is printed by an element and styled by none of the stylesheets given");
            }
        }
    }

    /** A classpath resource, tried under every root the caller named and then as given. */
    private @Nullable String read(String resource) {
        List<String> candidates = new ArrayList<>();
        for (String root : templateRoots) {
            candidates.add(root + resource);
        }
        candidates.add(resource);
        for (String candidate : candidates) {
            try (InputStream in = ElementContract.class.getClassLoader().getResourceAsStream(candidate)) {
                if (in != null) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException unreadable) {   // a resource that exists and cannot be read is not a verdict to swallow
                throw new java.io.UncheckedIOException("cannot read " + candidate, unreadable);
            }
        }
        return null;
    }
}
