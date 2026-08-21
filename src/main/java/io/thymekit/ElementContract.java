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
 * put where an element belongs, that the keys the adapter says it reads are the keys the factory puts
 * in, and — when an engine and stylesheets are given — that the element renders something, and that
 * every class it prints has a rule somewhere in the CSS you name.
 *
 * <p>The keys are declared in the template, above the fragment, in a comment Thymeleaf strips before
 * anything is rendered:
 *
 * <pre>{@code
 * <!--/* keys: level, text, id, href, rel, target, lang, srOnly *\/-->
 * <th:block th:fragment="headingEl(e)" ...>
 * }</pre>
 *
 * <p>Declare nothing and nothing is checked — the walk says so once rather than failing. Declare, and
 * a key carried by an element that its adapter never reads becomes a failure: data travelling for
 * nothing.
 *
 * <p>The other direction — a key an adapter reads that nothing here puts in — is only a defect if the
 * elements given were meant to cover the whole adapter. Say {@link #coveringEveryKey()} and it is
 * checked; that is a claim about the samples, not about the element, so it is asked for rather than
 * assumed. The kit makes that claim about its own.
 *
 * <p>Nothing here is required to write an element. It is the same walk the kit takes over its own, and
 * it fails with every problem at once rather than with the first.
 */
public final class ElementContract {

    /** An adapter is named for what it renders and ends in El; a second contract gets a version suffix. */
    private static final Pattern ADAPTER = Pattern.compile("^[a-z][A-Za-z0-9]*El(V\\d+)?$");

    private static final Pattern CLASS_ATTRIBUTE = Pattern.compile("class=\"([^\"]+)\"");

    /** What an adapter says it reads: a comment above the fragment, stripped before rendering. */
    private static final Pattern DECLARED_KEYS = Pattern.compile("keys:\\s*([A-Za-z0-9_,\\s]+?)\\s*\\*/");

    private final List<Element<?>> elements;
    private final @Nullable ITemplateEngine engine;
    private final List<String> stylesheets;
    private final List<String> templateRoots;
    private final boolean everyKey;


    private ElementContract(List<Element<?>> elements, @Nullable ITemplateEngine engine,
                            List<String> stylesheets, List<String> templateRoots, boolean everyKey) {
        this.elements = elements;
        this.engine = engine;
        this.stylesheets = stylesheets;
        this.templateRoots = templateRoots;
        this.everyKey = everyKey;
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
        return new ElementContract(settled, null, List.of(), List.of("templates/", ""), false);
    }

    /** Renders each element through the dispatcher, with the engine your application uses. */
    public ElementContract renderedBy(ITemplateEngine engine) {
        return new ElementContract(elements, Objects.requireNonNull(engine, "engine"), stylesheets, templateRoots, everyKey);
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
        return new ElementContract(elements, engine, stylesheets, List.copyOf(roots), everyKey);
    }

    /**
     * Where the looks live, as classpath resources ({@code "static/my/ui.css"}). Given these, every
     * class an element prints must have a rule in one of them; without them the classes are not looked
     * at, since the contract has no way to guess where a consumer keeps its CSS.
     */
    public ElementContract styledBy(String... cssResources) {
        List<String> all = new ArrayList<>(stylesheets);            // said twice means both, never the last one only
        all.addAll(List.of(Objects.requireNonNull(cssResources, "cssResources")));
        return new ElementContract(elements, engine, List.copyOf(all), templateRoots, everyKey);
    }

    /**
     * Also require that every key the adapters declare is put in by something here — a claim about the
     * elements given, not about the elements themselves: it says "these cover their adapters". Without
     * it a template branch nothing reaches goes unmentioned, which is right for a consumer checking one
     * element and wrong for a suite that means to check them all.
     */
    public ElementContract coveringEveryKey() {
        return new ElementContract(elements, engine, stylesheets, templateRoots, true);
    }

    /** Runs the walk. Throws with everything that is wrong, not with the first thing. */
    public void check() {
        List<String> failures = new ArrayList<>();
        Set<String> printedClasses = new LinkedHashSet<>();
        // what each adapter declares and what the elements given carried, gathered as the walk goes:
        // local, because a walk is one run and holds nothing between two of them
        java.util.Map<String, Set<String>> declaredByAdapter = new java.util.LinkedHashMap<>();
        java.util.Map<String, Set<String>> carriedByAdapter = new java.util.LinkedHashMap<>();
        for (Element<?> element : elements) {
            String address = element.template() + " :: " + element.fragment();
            if (!ADAPTER.matcher(element.fragment()).matches()) {
                failures.add(address + " — an adapter is named like myCardEl, or myCardElV2 for a second contract");
            }
            if (element.bare()) {
                failures.add(address + " — a script element does not belong among elements; declare it with requires()");
            }
            declaresItself(element, address, failures);
            declaredKeysMatch(element, address, failures, declaredByAdapter, carriedByAdapter);
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
        if (everyKey) {
            declaredByAdapter.forEach((address, declared) -> {
                Set<String> carried = carriedByAdapter.getOrDefault(address, Set.of());
                declared.stream().filter(key -> !carried.contains(key)).forEach(key ->
                    failures.add(address + " — its adapter reads \"" + key + "\", and nothing given here puts"
                        + " it in: a branch of the template nothing reaches, or samples too poor to reach it"));
            });
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

    /**
     * The keys an adapter says it reads against the keys the element actually carries. Both directions
     * are silent failures otherwise: data nobody reads, and a template branch nothing can reach.
     */
    private void declaredKeysMatch(Element<?> element, String address, List<String> failures,
                                   java.util.Map<String, Set<String>> declaredByAdapter,
                                   java.util.Map<String, Set<String>> carriedByAdapter) {
        String template = read(element.template() + ".html");
        if (template == null) {
            return;                                   // already reported by declaresItself
        }
        int fragmentAt = template.indexOf("th:fragment=\"" + element.fragment() + "(");
        if (fragmentAt == -1) {
            return;                                   // same
        }
        Matcher declaration = DECLARED_KEYS.matcher(template.substring(0, fragmentAt));
        String last = null;
        while (declaration.find()) {
            last = declaration.group(1);              // the one nearest the fragment
        }
        if (last == null) {
            return;                                   // nothing declared, nothing checked
        }
        Set<String> declared = new LinkedHashSet<>(List.of(last.trim().split("\\s*,\\s*")));
        Set<String> carried = new LinkedHashSet<>(element.asMap().keySet());
        carried.removeAll(Element.RESERVED);
        for (String key : carried) {
            if (!declared.contains(key)) {
                failures.add(address + " — carries the key \"" + key + "\" that its adapter does not read;"
                    + " declare it above the fragment or stop putting it in");
            }
        }
        declaredByAdapter.computeIfAbsent(address, a -> new LinkedHashSet<>()).addAll(declared);
        carriedByAdapter.computeIfAbsent(address, a -> new LinkedHashSet<>()).addAll(carried);
    }

    /** Renders one element through the single dispatcher, exactly as a page would. */
    private Set<String> renderAndCollect(Element<?> element, String address, List<String> failures) {
        Context context = new Context();
        context.setVariable("e", element.asMap());
        String html;
        try {
            html = Objects.requireNonNull(engine).process("thymekit/element", Set.of("render"), context);
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
